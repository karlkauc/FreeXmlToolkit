package org.fxt.freexmltoolkit.controls.v2.editor.core;

import javafx.application.Platform;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Event bus for editor events.
 * Provides publish-subscribe mechanism for loose coupling between editor components.
 *
 * <p>Thread-safe implementation using CopyOnWriteArrayList for listeners.
 * Events can be published from any thread and will be delivered on JavaFX Application Thread.</p>
 */
public class EditorEventBus {

    private static final Logger logger = LogManager.getLogger(EditorEventBus.class);

    /**
     * Listener interface for editor events.
     */
    @FunctionalInterface
    public interface EventListener {
        /**
         * Called when an event is published.
         *
         * @param event the published event
         */
        void onEvent(EditorEvent event);
    }

    // Map of event type to listeners
    private final Map<EditorEvent.Type, List<EventListener>> listeners = new EnumMap<>(EditorEvent.Type.class);

    // Flag to enable/disable event logging
    private boolean loggingEnabled = false;

    /**
     * Creates a new event bus.
     */
    public EditorEventBus() {
        // Initialize listener lists for all event types
        for (EditorEvent.Type type : EditorEvent.Type.values()) {
            listeners.put(type, new CopyOnWriteArrayList<>());
        }
    }

    /**
     * Subscribes a listener for a specific event type.
     *
     * @param type     the event type to listen for
     * @param listener the listener to register
     */
    public void subscribe(EditorEvent.Type type, EventListener listener) {
        Objects.requireNonNull(type, "Event type cannot be null");
        Objects.requireNonNull(listener, "Listener cannot be null");

        listeners.get(type).add(listener);
        logger.debug("Subscribed listener for event type: {}", type);
    }

    /**
     * Unsubscribes a listener from a specific event type.
     *
     * @param type     the event type
     * @param listener the listener to unregister
     */
    public void unsubscribe(EditorEvent.Type type, EventListener listener) {
        Objects.requireNonNull(type, "Event type cannot be null");
        Objects.requireNonNull(listener, "Listener cannot be null");

        listeners.get(type).remove(listener);
        logger.debug("Unsubscribed listener from event type: {}", type);
    }

    /**
     * Publishes an event to all registered listeners.
     * Events are delivered on the JavaFX Application Thread.
     *
     * @param event the event to publish
     */
    public void publish(EditorEvent event) {
        Objects.requireNonNull(event, "Event cannot be null");

        if (loggingEnabled) {
            logger.debug("Publishing event: {}", event);
        }

        List<EventListener> eventListeners = listeners.get(event.getType());
        if (eventListeners.isEmpty()) {
            return;
        }

        // Deliver on JavaFX thread
        Platform.runLater(() -> {
            for (EventListener listener : eventListeners) {
                try {
                    listener.onEvent(event);
                } catch (Exception e) {
                    logger.error("Error delivering event {} to listener: {}", event, e.getMessage(), e);
                }
            }
        });
    }

    /**
     * Publishes an event synchronously on the current thread.
     * Use only when you're already on the JavaFX Application Thread.
     *
     * @param event the event to publish
     */
    public void publishSync(EditorEvent event) {
        Objects.requireNonNull(event, "Event cannot be null");

        if (loggingEnabled) {
            logger.debug("Publishing event (sync): {}", event);
        }

        List<EventListener> eventListeners = listeners.get(event.getType());
        for (EventListener listener : eventListeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                logger.error("Error delivering event {} to listener: {}", event, e.getMessage(), e);
            }
        }
    }

    /**
     * Clears all listeners for a specific event type.
     *
     * @param type the event type
     */
    public void clearListeners(EditorEvent.Type type) {
        Objects.requireNonNull(type, "Event type cannot be null");
        listeners.get(type).clear();
        logger.debug("Cleared all listeners for event type: {}", type);
    }

    /**
     * Clears all listeners for all event types.
     */
    public void clearAllListeners() {
        listeners.values().forEach(List::clear);
        logger.debug("Cleared all listeners");
    }

    /**
     * Gets the number of listeners for a specific event type.
     *
     * @param type the event type
     * @return the number of registered listeners
     */
    public int getListenerCount(EditorEvent.Type type) {
        Objects.requireNonNull(type, "Event type cannot be null");
        return listeners.get(type).size();
    }

    /**
     * Enables or disables debug logging for event publishing.
     *
     * @param enabled true to enable logging, false to disable
     */
    public void setLoggingEnabled(boolean enabled) {
        this.loggingEnabled = enabled;
        logger.debug("Event logging {}", enabled ? "enabled" : "disabled");
    }
}
