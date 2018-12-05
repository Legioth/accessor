package org.github.legioth.accessor;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.servlet.annotation.WebServlet;

import com.vaadin.annotations.Push;
import com.vaadin.annotations.VaadinServletConfiguration;
import com.vaadin.server.VaadinRequest;
import com.vaadin.server.VaadinServlet;
import com.vaadin.shared.Registration;
import com.vaadin.ui.CheckBox;
import com.vaadin.ui.Component;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;

@Push
public class DemoUI extends UI {
    @WebServlet(value = "/*", asyncSupported = true)
    @VaadinServletConfiguration(ui = DemoUI.class, productionMode = false)
    public static class DemoServlet extends VaadinServlet {

    }

    private final MessageDisplay globalMessages = new MessageDisplay();
    private Registration globalRegistration;
    private VerticalLayout layout = new VerticalLayout();

    @Override
    protected void init(VaadinRequest request) {
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
        CheckBox globalToggle = createToggle("Global binding",
                ui -> globalRegistration = globalAccessor.bind(ui),
                ui -> globalRegistration.remove());

        /*
         * Build the demo UI.
         */
        HorizontalLayout toggles = new HorizontalLayout(
                createAttachDetachToggle("Basic case", basicMessages),
                createAttachDetachToggle("Runnable accessor", runnableMessages),
                createAttachDetachToggle("BiConsumer accessor",
                        biConsumerMessages),
                createAttachDetachToggle("Unsubscriber accessor",
                        unsubscriberMessages),
                globalToggle);

        layout.addComponents(toggles, globalMessages);
        setContent(layout);
    }

    private static class MessageDisplay extends VerticalLayout {
        public MessageDisplay() {
            setMargin(false);
        }

        public void addMessage(String message) {
            addComponent(new Label(message));
        }

        public void generateMessage() {
            addMessage("Generated at " + System.currentTimeMillis());
        }
    }

    private CheckBox createAttachDetachToggle(String label,
            Component component) {
        return createToggle(label, ui -> layout.addComponent(component),
                ui -> layout.removeComponent(component));
    }

    private static CheckBox createToggle(String label,
            Consumer<UI> enableAction, Consumer<UI> disableAction) {
        CheckBox checkBox = new CheckBox(label);
        checkBox.addValueChangeListener(valueChange -> {
            UI ui = checkBox.getUI();
            if (valueChange.getValue() == Boolean.TRUE) {
                enableAction.accept(ui);
            } else {
                disableAction.accept(ui);
            }
        });
        return checkBox;
    }
}
