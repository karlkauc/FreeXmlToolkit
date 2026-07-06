package org.fxt.freexmltoolkit.controls.theme;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

import org.fxt.freexmltoolkit.controls.shell.ThemeManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/**
 * Verifies that {@link SemanticStyle} builds a token-coloured inline style and re-applies
 * it with the new theme's colour on a light/dark switch.
 */
@ExtendWith(ApplicationExtension.class)
class SemanticStyleTest {

    private void runAndWait(Runnable action) {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } finally {
                latch.countDown();
            }
        });
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS), "Action timed out");
        } catch (InterruptedException e) {
            fail("Interrupted while waiting for JavaFX action");
        }
    }

    @Test
    @DisplayName("style() applies the light hex then re-applies the dark hex on a theme switch")
    void reStylesOnThemeSwitch() {
        runAndWait(() -> {
            Scene scene = new Scene(new StackPane(), 10, 10);
            ThemeManager.apply(scene, false);

            Label label = new Label("x");
            SemanticStyle.style(label, DesignTokens.ColorToken.DANGER,
                    c -> "-fx-text-fill: " + c + ";");

            String lightHex = SemanticStyle.hex(DesignTokens.ColorToken.DANGER);
            assertTrue(label.getStyle().contains(lightHex),
                    "style must contain the light danger hex: " + label.getStyle());

            ThemeManager.apply(scene, true);
            // Re-query hex under the dark theme.
            String darkHex = SemanticStyle.hex(DesignTokens.ColorToken.DANGER);
            assertTrue(label.getStyle().contains(darkHex),
                    "style must re-apply the dark danger hex: " + label.getStyle());
            assertTrue(!lightHex.equals(darkHex), "light and dark danger hex differ");

            ThemeManager.apply(scene, false); // restore
        });
    }
}
