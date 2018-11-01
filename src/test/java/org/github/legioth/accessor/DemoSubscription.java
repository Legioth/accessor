package org.github.legioth.accessor;

import java.util.Timer;
import java.util.TimerTask;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.vaadin.flow.shared.Registration;

/**
 * Simple example subscription manager that fires an event once per second.
 * There are various subscription method variants here to showcase how to use
 * {@link Accessor} in different situations.
 */
public class DemoSubscription {
    private static final Timer timer = new Timer("DemoSubscription timer",
            true);

    public static Registration subscribe(Consumer<String> consumer) {
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                consumer.accept(
                        "Time is currently " + System.currentTimeMillis());
            }
        };
        timer.scheduleAtFixedRate(task, 1000, 1000);

        System.out.println("Subscription added");
        return () -> {
            task.cancel();
            System.out.println("Subscription canceled");
        };
    }

    public static Registration subscribe(Runnable task) {
        return subscribe(ignore -> task.run());
    }

    public static Runnable subscribeRunnableUnsubscribe(
            Consumer<String> consumer) {
        return subscribe(consumer)::remove;
    }

    public static Registration subscribe(
            BiConsumer<String, String> biConsumer) {
        return subscribe(value -> biConsumer.accept(value, "Only for you"));
    }
}
