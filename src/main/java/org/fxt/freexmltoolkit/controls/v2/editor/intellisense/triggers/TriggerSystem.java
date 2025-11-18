package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.triggers;

import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * System for triggering IntelliSense based on character input or key combinations.
 */
public class TriggerSystem {

    private static final Logger logger = LogManager.getLogger(TriggerSystem.class);

    private final Map<Character, Runnable> charTriggers = new HashMap<>();
    private final Map<KeyCombination, Runnable> keyTriggers = new HashMap<>();

    /**
     * Adds a character trigger.
     *
     * @param c      the trigger character
     * @param action the action to execute
     */
    public void addCharTrigger(char c, Runnable action) {
        charTriggers.put(c, action);
        logger.debug("Added char trigger for: '{}'", c);
    }

    /**
     * Adds a key combination trigger.
     *
     * @param keyCode  the key code
     * @param ctrl     true if Ctrl must be pressed
     * @param action   the action to execute
     */
    public void addKeyTrigger(KeyCode keyCode, boolean ctrl, Runnable action) {
        KeyCombination combo = new KeyCombination(keyCode, ctrl);
        keyTriggers.put(combo, action);
        logger.debug("Added key trigger for: {}", combo);
    }

    /**
     * Handles a character typed event.
     *
     * @param c the character
     */
    public void handleCharTyped(char c) {
        Runnable action = charTriggers.get(c);
        if (action != null) {
            logger.debug("Triggering action for char: '{}'", c);
            Platform.runLater(action);
        }
    }

    /**
     * Handles a key pressed event.
     *
     * @param event the key event
     * @return true if event was consumed, false otherwise
     */
    public boolean handleKeyPressed(KeyEvent event) {
        KeyCombination combo = new KeyCombination(event.getCode(), event.isControlDown());
        Runnable action = keyTriggers.get(combo);

        if (action != null) {
            logger.debug("Triggering action for key: {}", combo);
            action.run();
            return true;
        }

        return false;
    }

    /**
     * Removes a character trigger.
     *
     * @param c the character
     */
    public void removeCharTrigger(char c) {
        charTriggers.remove(c);
    }

    /**
     * Removes all triggers.
     */
    public void clearTriggers() {
        charTriggers.clear();
        keyTriggers.clear();
        logger.debug("Cleared all triggers");
    }

    /**
     * Key combination for trigger matching.
     */
    private static class KeyCombination {
        private final KeyCode keyCode;
        private final boolean ctrl;
        private final int hashCode;

        KeyCombination(KeyCode keyCode, boolean ctrl) {
            this.keyCode = keyCode;
            this.ctrl = ctrl;
            this.hashCode = Objects.hash(keyCode, ctrl);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            KeyCombination that = (KeyCombination) o;
            return ctrl == that.ctrl && keyCode == that.keyCode;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public String toString() {
            return (ctrl ? "Ctrl+" : "") + keyCode;
        }
    }
}
