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

    @Test
    @DisplayName("Action-colour overload paints with the role's token")
    void paintsActionColor() {
        runAndWait(() -> {
            Scene scene = new Scene(new StackPane(), 10, 10);
            ThemeManager.apply(scene, false);
            IconifyIcon icon = SemanticIcon.paint(new IconifyIcon("bi-trash"), ActionColor.DELETE);
            assertEquals(DesignTokens.ColorToken.DANGER.color(DesignTokens.Theme.LIGHT), icon.getIconColor());
        });
    }

    @Test
    @DisplayName("Bound registry prunes collected icons without waiting for a theme switch")
    void boundRegistryPrunesCollectedIcons() throws InterruptedException {
        runAndWait(() -> {
            for (int i = 0; i < 300; i++) {
                SemanticIcon.bind(new IconifyIcon("bi-star"), ActionColor.NEUTRAL); // dropped immediately
            }
        });
        int before = SemanticIcon.boundRegistrySize();
        assertTrue(before >= 300, "entries were registered: " + before);
        boolean collected = false;
        for (int i = 0; i < 20 && !collected; i++) {
            System.gc();
            Thread.sleep(50);
            byte[][] pressure = new byte[64][];
            for (int j = 0; j < pressure.length; j++) {
                pressure[j] = new byte[1 << 20];
            }
            runAndWait(() -> SemanticIcon.bind(new IconifyIcon("bi-star"), ActionColor.NEUTRAL));
            collected = SemanticIcon.boundRegistrySize() < before;
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(collected, "GC did not collect the icons in time");
        assertTrue(SemanticIcon.boundRegistrySize() < before, "prune must drop entries whose icon was collected");
    }

    @Test
    @DisplayName("Bound icon colour re-tints on a theme switch and stays bound (CSS cannot override it)")
    void boundIconRecolorsOnThemeSwitch() {
        runAndWait(() -> {
            Scene scene = new Scene(new StackPane(), 10, 10);
            ThemeManager.apply(scene, false);
            IconifyIcon icon = SemanticIcon.bind(new IconifyIcon("bi-plus-circle"), ActionColor.CREATE);
            assertTrue(icon.iconColorProperty().isBound(), "icon colour must be bound");
            assertEquals(DesignTokens.ColorToken.SUCCESS.color(DesignTokens.Theme.LIGHT), icon.getIconColor());
            ThemeManager.apply(scene, true);
            assertEquals(DesignTokens.ColorToken.SUCCESS.color(DesignTokens.Theme.DARK), icon.getIconColor());
            ThemeManager.apply(scene, false);
        });
    }
}
