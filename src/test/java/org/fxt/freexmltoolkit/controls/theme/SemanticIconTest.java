package org.fxt.freexmltoolkit.controls.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.ThemeManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/**
 * Verifies that {@link SemanticIcon} colours an icon from the theme-aware design token
 * and re-tints it when {@link ThemeManager} switches theme (the behaviour a static hex
 * or plain CSS cannot provide for a programmatically coloured icon).
 */
@ExtendWith(ApplicationExtension.class)
class SemanticIconTest {

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
    @DisplayName("Icon takes the current theme colour and re-tints on a theme switch")
    void recolorsOnThemeSwitch() {
        runAndWait(() -> {
            Scene scene = new Scene(new StackPane(), 10, 10);

            // Baseline light, then paint — the icon must take the LIGHT token colour.
            ThemeManager.apply(scene, false);
            IconifyIcon icon = SemanticIcon.paint(new IconifyIcon("bi-check-circle"),
                    DesignTokens.ColorToken.SUCCESS);
            assertEquals(DesignTokens.ColorToken.SUCCESS.color(DesignTokens.Theme.LIGHT),
                    icon.getIconColor(), "initial colour must be the light token");

            // Switch to dark — the registered icon must re-tint to the DARK token.
            ThemeManager.apply(scene, true);
            assertEquals(DesignTokens.ColorToken.SUCCESS.color(DesignTokens.Theme.DARK),
                    icon.getIconColor(), "icon must re-tint to the dark token on switch");

            // Switch back to light — and restore the baseline for other tests.
            ThemeManager.apply(scene, false);
            assertEquals(DesignTokens.ColorToken.SUCCESS.color(DesignTokens.Theme.LIGHT),
                    icon.getIconColor(), "icon must re-tint back to the light token");
        });
    }
}
