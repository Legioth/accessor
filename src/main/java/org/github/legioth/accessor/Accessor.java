package org.github.legioth.accessor;

import java.io.Serializable;
import java.util.function.Consumer;
import java.util.function.Function;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.UIDetachedException;
import com.vaadin.flow.function.SerializableConsumer;
import com.vaadin.flow.function.SerializableFunction;
import com.vaadin.flow.server.Command;
import com.vaadin.flow.shared.Registration;

/**
 * Helper for managing concurrent access to a Vaadin UI based on occasional
 * events from a subscription. The helper automatically handles details related
 * to correctly using {@link UI#access(Command)} as well as automatic
 * subscription and unsubscription when the owning component is attached and
 * detached. Instantiated through one of the static methods that return a
 * builder for further configuration. After creation, the accessor is activated
 * by using {@link #bind(Component)} to connect it to the attach and detach
 * events of the provided component instance.
 * 
 * @see #ofConsumer(Consumer)
 * @see #ofRunnable(Runnable)
 * @see #bind(Component)
 */
public abstract class Accessor implements Serializable {
    private Registration subscribeRegistration;
    private boolean boundToComponent = false;

    /**
     * Builder for finalizing an accessor by defining how to subscribe and
     * unsubscribe.
     *
     * @param <T>
     *            the callback type used by the subscription
     */
    public interface AccessorBuilder<T> {
        /**
         * Creates an accessor that subscribes itself through a callback that
         * returns a registration instance for unsubscribing. The subscriber
         * receives a callback that wraps the callback passed to
         * {@link Accessor#ofConsumer(Consumer)} or
         * {@link Accessor#ofRunnable(Runnable)}. It should return a
         * {@link Registration} that will be called by the accessor to
         * unsubscribe.
         * 
         * @param subscriber
         *            the function that sets up a subscription based on a
         *            callback, not <code>null</code>
         * @return a newly configured accessor, not <code>null</code>
         */
        Accessor withSubscriber(Function<T, Registration> subscriber);

        /**
         * Creates an accessor that subscribes itself through a callback that
         * returns a registration instance for unsubscribing. The subscriber
         * receives a callback that wraps the callback passed to
         * {@link Accessor#ofConsumer(Consumer)} or
         * {@link Accessor#ofRunnable(Runnable)}. It should return a an object
         * that the accessor will pass to the probided unsubscriber to
         * unsubscribe.
         * 
         * @param subscriber
         *            the function that sets up a subscription based on a
         *            callback, not <code>null</code>
         * @param unsubscriber
         *            a callback that performs the unsubscription based on the
         *            object returned from the subscriber
         * @return a newly configured accessor, not <code>null</code>
         */
        <U> Accessor withSubscriberAndUnsubscriber(Function<T, U> subscriber,
                Consumer<U> unsubscriber);
    }

    private static class AccessorBuilderImpl<T> implements AccessorBuilder<T> {
        /**
         * Function that receives a callback that triggers UI.access. The
         * function should return an appropriately typed callback that uses the
         * received callback to access the UI.
         */
        private final SerializableFunction<SerializableConsumer<Command>, T> wrapper;

        public AccessorBuilderImpl(
                SerializableFunction<SerializableConsumer<Command>, T> wrapper) {
            assert wrapper != null;
            this.wrapper = wrapper;
        }

        @Override
        public Accessor withSubscriber(Function<T, Registration> subscriber) {
            return withSubscriberAndUnsubscriber(subscriber,
                    (SerializableConsumer<Registration>) Registration::remove);
        }

        @Override
        public <U> Accessor withSubscriberAndUnsubscriber(
                Function<T, U> subscriber, Consumer<U> unsubscriber) {
            if (subscriber == null) {
                throw new IllegalArgumentException("Subscriber cannot be null");
            }
            if (unsubscriber == null) {
                throw new IllegalArgumentException(
                        "Unsubscriber cannot be null");
            }

            /*
             * Accessor.subscribe is abstract so that the type parameters of the
             * builder can be encapsulated in the subclass without
             * parameterizing Accessor itself.
             */
            return new Accessor() {
                @Override
                protected Registration subscribe(
                        SerializableConsumer<Command> uiAccess) {
                    T callbackForSubscribe = wrapper.apply(uiAccess);

                    U unsubscribeHandle = subscriber
                            .apply(callbackForSubscribe);

                    return () -> unsubscriber.accept(unsubscribeHandle);
                }
            };
        }
    }

    /**
     * Binds this accessor to the attach and detach cycle of the provided
     * component. The component is typically the view that is updated by the
     * subscription. Alternatively, the accessor can be bound to a UI instance
     * and manually unbound using the returned {@link Registration} instance.
     * 
     * @see #isBound()
     * 
     * @param component
     *            the component instance to bind to, not <code>null</code>
     * @return a registration that can be used to unbind the component, not
     *         <code>null</code>
     */
    public Registration bind(Component component) {
        if (isBound()) {
            throw new IllegalStateException("Already bound to a component");
        }
        boundToComponent = true;

        Registration attachRegistration = component.addAttachListener(
                event -> registerSubscription(event.getUI()));
        Registration detachRegistration = component
                .addDetachListener(event -> unsubscribe());

        // Subscribe immediately if already attached
        component.getUI().ifPresent(this::registerSubscription);

        return () -> {
            attachRegistration.remove();
            detachRegistration.remove();

            unsubscribe();

            boundToComponent = false;
        };
    }

    /**
     * Checks whether this accessor is currently bound to a component.
     * 
     * @see #bind(Component)
     * 
     * @return <code>true</code> if this accessor is currently bound to a
     *         component, otherwise <code>false</code>
     */
    public boolean isBound() {
        return boundToComponent;
    }

    /**
     * Checks whether the subscription is currently active. The subscription is
     * automatically active when bound to a component that is attached. The
     * subscription is not active if not bound to a component or if bound to a
     * component that is not attached to a UI.
     * 
     * @return <code>true</code> if subscribed, otherwise <code>false</code>
     */
    public boolean isSubscribed() {
        return subscribeRegistration != null;
    }

    private Registration registerSubscription(UI ui) {
        if (ui == null) {
            throw new IllegalArgumentException(
                    "Cannot subscribe for a null UI");
        }
        if (isSubscribed()) {
            throw new IllegalStateException(
                    "This accessor is already subscribed");
        }

        SerializableConsumer<Command> wrappedUiAccess = accessTask -> {
            if (ui.getSession() == null) {
                unsubscribe();
                return;
            }

            try {
                ui.access(accessTask);
            } catch (UIDetachedException ignore) {
                // Handle the race condition case where the UI was detached in
                // the time between checking the session and running access()
                unsubscribe();
            }
        };

        subscribeRegistration = subscribe(wrappedUiAccess);

        if (subscribeRegistration == null) {
            throw new IllegalStateException(
                    "Subscriber must return a non-null unsubscriber");
        }

        return this::unsubscribe;
    }

    /**
     * Set up the actual subscription based on a callback that does access() on
     * the UI of the bound component.
     * 
     * @param uiAccess
     *            a callback that delegates to UI.access(), not
     *            <code>null</code>
     * @return a registration for unsubscribing, not <code>null</code>
     */
    protected abstract Registration subscribe(
            SerializableConsumer<Command> uiAccess);

    private void unsubscribe() {
        // Ignore double unsubscribe
        if (isSubscribed()) {
            subscribeRegistration.remove();
            subscribeRegistration = null;
        }
    }

    /**
     * Creates a builder for an accessor based on a consumer. When a
     * subscription is active and emits an event, the consumer passed to this
     * method will be run inside {@link UI#access(Command)}. To create an
     * accessor, a callback for setting up a subscription must be passed to one
     * of the methods on the builder returned from this method. The function for
     * setting up a subscription will receive a consumer that wraps the consumer
     * passed to this method.
     * 
     * @param consumer
     *            the consumer that will update the UI based on events from a
     *            subscription, not <code>null</code>
     * @return an accessor builder, not <code>null</code>
     */
    public static <T> AccessorBuilder<Consumer<T>> ofConsumer(
            Consumer<T> consumer) {
        if (consumer == null) {
            throw new IllegalArgumentException("Consumer cannot be null");
        }
        return new AccessorBuilderImpl<>(uiAccess -> {
            return value -> uiAccess.accept(() -> consumer.accept(value));
        });
    }

    /**
     * Creates a builder for an accessor based on a consumer. When a
     * subscription is active and emits an event, the runnable passed to this
     * method will be run inside {@link UI#access(Command)}. To create an
     * accessor, a callback for setting up a subscription must be passed to one
     * of the methods on the builder returned from this method. The function for
     * setting up a subscription will receive a runnable that wraps the runnable
     * passed to this method.
     * 
     * @param runnable
     *            the runnable that will update the UI based on events from a
     *            subscription, not <code>null</code>
     * @return an accessor builder, not <code>null</code>
     */
    public static AccessorBuilder<Runnable> ofRunnable(Runnable runnable) {
        if (runnable == null) {
            throw new IllegalArgumentException("Runnable cannot be null");
        }
        return new AccessorBuilderImpl<>(uiAccess -> {
            return () -> uiAccess.accept(runnable::run);
        });
    }
}
