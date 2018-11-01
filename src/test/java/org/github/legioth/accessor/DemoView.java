package org.github.legioth.accessor;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;

@Push
@Route("")
public class DemoView extends Div {
    private final MessageDisplay globalMessages = new MessageDisplay();
    private Registration globalRegistration;

    public DemoView() {
        /*
         * Basic case where subscription accepts a Consumer and returns a
         * Registration. The accessor is bound to the attach and detach events
         * of a component.
         */
        MessageDisplay basicMessages = new MessageDisplay();
        Accessor.ofConsumer(basicMessages::addMessage)
                .withSubscriber(DemoSubscription::subscribe)
                .bind(basicMessages);

        /*
         * Basic case where subscription accepts a Runnable and returns a
         * Registration.
         */
        MessageDisplay runnableMessages = new MessageDisplay();
        Accessor.ofRunnable(runnableMessages::generateMessage)
                .withSubscriber(DemoSubscription::subscribe)
                .bind(runnableMessages);

        /*
         * Case where subscription accepts something that isn't a consumer or
         * runnable. In that case, we need to manually define our own listener
         * that passes the appropriate value to the consumer passed to the
         * subscriber.
         */
        MessageDisplay biConsumerMessages = new MessageDisplay();
        Accessor.ofConsumer(biConsumerMessages::addMessage)
                .withSubscriber(consumer -> {
                    BiConsumer<String, String> listener = (message,
                            greeting) -> {
                        consumer.accept(message + " " + greeting);
                    };
                    return DemoSubscription.subscribe(listener);
                }).bind(biConsumerMessages);

        /*
         * Separate subscribe and unsubscribe callbacks when subscriber returns
         * something other than a Registration.
         */
        MessageDisplay unsubscriberMessages = new MessageDisplay();
        Accessor.ofConsumer(unsubscriberMessages::addMessage)
                .withSubscriberAndUnsubscriber(
                        DemoSubscription::subscribeRunnableUnsubscribe,
                        Runnable::run)
                .bind(unsubscriberMessages);

        /*
         * Manually enabling and disabling the accessor instead of connecting it
         * to the attach and detach events of any given component. This is done
         * by binding to UI which stays attached as long as the application is
         * used. Instead, unsubscribing is handled through the returned
         * Registration instance.
         */
        Accessor globalAccessor = Accessor
                .ofConsumer(globalMessages::addMessage)
                .withSubscriber(DemoSubscription::subscribe);
        Checkbox globalToggle = createToggle("Global binding",
                ui -> globalRegistration = globalAccessor.bind(ui),
                ui -> globalRegistration.remove());

        /*
         * Build the demo UI.
         */
        add(createAttachDetachToggle("Basic case", basicMessages),
                createAttachDetachToggle("Runnable accessor", runnableMessages),
                createAttachDetachToggle("BiConsumer accessor",
                        biConsumerMessages),
                createAttachDetachToggle("Unsubscriber accessor",
                        unsubscriberMessages),
                globalToggle, globalMessages);
    }

    private static class MessageDisplay extends Div {
        public void addMessage(String message) {
            add(new Paragraph(message));
        }

        public void generateMessage() {
            addMessage("Generated at " + System.currentTimeMillis());
        }
    }

    private Checkbox createAttachDetachToggle(String label,
            Component component) {
        return createToggle(label, ui -> add(component),
                ui -> remove(component));
    }

    private static Checkbox createToggle(String label,
            Consumer<UI> enableAction, Consumer<UI> disableAction) {
        return new Checkbox(label, valueChange -> {
            UI ui = valueChange.getSource().getUI().get();
            if (valueChange.getValue() == Boolean.TRUE) {
                enableAction.accept(ui);
            } else {
                disableAction.accept(ui);
            }
        });
    }
}
