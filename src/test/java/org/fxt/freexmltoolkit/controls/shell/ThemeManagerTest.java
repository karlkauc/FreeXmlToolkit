package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

@ExtendWith(ApplicationExtension.class)
class ThemeManagerTest {

    private Scene scene;
    private StackPane root;
    private StackPane nested;

    private static String sheet(String name) {
        return ThemeManagerTest.class.getResource("/css/" + name).toExternalForm();
    }

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        nested = new StackPane();
        root = new StackPane(nested);
        scene = new Scene(root, 200, 200);
        stage.setScene(scene);
    }

    @Test
    void applyDarkThenLightSwapsStylesheetAndRootClass() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            ThemeManager.apply(scene, true);
            return null;
        });
        assertTrue(scene.getStylesheets().stream().anyMatch(s -> s.contains("dark-theme.css")), "dark css added");
        assertTrue(scene.getRoot().getStyleClass().contains("fxt-theme-dark"), "dark root class");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            ThemeManager.apply(scene, false);
            return null;
        });
        assertTrue(scene.getStylesheets().stream().anyMatch(s -> s.contains("light-theme.css")), "light css added");
        assertFalse(scene.getStylesheets().stream().anyMatch(s -> s.contains("dark-theme.css")), "dark css removed");
        assertTrue(scene.getRoot().getStyleClass().contains("fxt-theme-light"), "light root class");
        assertFalse(scene.getRoot().getStyleClass().contains("fxt-theme-dark"), "dark root class removed");
    }

    /**
     * The shell's base sheets are PARENT stylesheets, which outrank scene stylesheets.
     * The theme sheet must therefore be inserted into every parent list declaring
     * unified-shell.css — immediately before it, so unified-shell.css keeps winning ties.
     */
    @Test
    void insertsThemeSheetBeforeUnifiedShellInEveryDeclaringParent() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            root.getStylesheets().setAll(
                    sheet("design-tokens.css"), sheet("app-theme.css"),
                    sheet("fxt-theme.css"), sheet("unified-shell.css"));
            nested.getStylesheets().setAll(
                    sheet("design-tokens.css"), sheet("fxt-theme.css"), sheet("unified-shell.css"));
            ThemeManager.apply(scene, true);
            return null;
        });

        assertOrdered(root.getStylesheets(), "dark-theme.css");
        assertOrdered(nested.getStylesheets(), "dark-theme.css");
        assertFalse(scene.getStylesheets().stream().anyMatch(s -> s.contains("dark-theme.css")),
                "theme sheet must not land on the scene when a parent declares the shell sheets");

        // Toggling replaces the theme sheet in place (no duplicates, position preserved).
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            ThemeManager.apply(scene, false);
            return null;
        });
        assertOrdered(root.getStylesheets(), "light-theme.css");
        assertOrdered(nested.getStylesheets(), "light-theme.css");
        assertFalse(root.getStylesheets().stream().anyMatch(s -> s.contains("dark-theme.css")),
                "stale dark sheet removed from parent");
    }

    @Test
    void fallsBackToSceneWhenNoParentDeclaresShellSheets() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            ThemeManager.apply(scene, true);
            return null;
        });
        assertTrue(scene.getStylesheets().stream().anyMatch(s -> s.contains("dark-theme.css")),
                "bare scene falls back to scene-level theme sheet");
        assertTrue(root.getStylesheets().isEmpty(), "parent list untouched");
    }

    private static void assertOrdered(List<String> sheets, String themeSheet) {
        int themeIdx = indexOf(sheets, themeSheet);
        int shellIdx = indexOf(sheets, "unified-shell.css");
        assertTrue(themeIdx >= 0, themeSheet + " present in parent list");
        assertTrue(shellIdx >= 0, "unified-shell.css still present");
        assertEquals(shellIdx - 1, themeIdx, themeSheet + " directly before unified-shell.css");
        assertEquals(1, sheets.stream().filter(s -> s.contains(themeSheet)).count(), "no duplicate theme sheet");
    }

    private static int indexOf(List<String> sheets, String suffixName) {
        for (int i = 0; i < sheets.size(); i++) {
            if (sheets.get(i).contains(suffixName)) {
                return i;
            }
        }
        return -1;
    }

    /** Secondary windows follow the theme without persisting it or notifying listeners. */
    @Test
    void applyToSwitchesVisuallyWithoutPersistingOrNotifying() {
        var props = org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            ThemeManager.apply(scene, false);
            return null;
        });
        java.util.concurrent.atomic.AtomicInteger notified = new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.Consumer<org.fxt.freexmltoolkit.controls.theme.DesignTokens.Theme> listener =
                t -> notified.incrementAndGet();
        ThemeManager.addThemeChangeListener(listener);
        try {
            Scene other = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
                Scene s = new Scene(new StackPane(), 100, 100);
                ThemeManager.applyTo(s, true);
                return s;
            });
            assertTrue(other.getRoot().getStyleClass().contains("fxt-theme-dark"), "dark root class");
            assertTrue(other.getStylesheets().stream().anyMatch(s -> s.contains("dark-theme.css")), "dark css");
            assertEquals("light", props.get("ui.theme"), "the preference is untouched");
            assertEquals(0, notified.get(), "no theme-change notification");
        } finally {
            ThemeManager.removeThemeChangeListener(listener);
        }
    }
}
