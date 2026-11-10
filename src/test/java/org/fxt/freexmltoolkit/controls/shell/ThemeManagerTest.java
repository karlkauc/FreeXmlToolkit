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

    @Test
    void storedModesAreReadAndSystemStaysStored() {
        assertEquals(ThemeManager.Mode.SYSTEM, ThemeManager.Mode.fromProperty("system"));
        assertEquals(ThemeManager.Mode.DARK, ThemeManager.Mode.fromProperty("Dark"));
        assertEquals(ThemeManager.Mode.LIGHT, ThemeManager.Mode.fromProperty(null));
        assertEquals(ThemeManager.Mode.LIGHT, ThemeManager.Mode.fromProperty("blue"));

        var props = org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class);
        try {
            WaitForAsyncUtils.waitForAsyncFx(2000, () -> ThemeManager.applyMode(scene, ThemeManager.Mode.SYSTEM));
            assertEquals("system", props.get("ui.theme"), "the mode is stored, not its resolution");
            assertEquals(ThemeManager.Mode.SYSTEM, ThemeManager.currentMode());
            boolean dark = ThemeManager.systemPrefersDark();
            assertEquals(dark, ThemeManager.currentIsDark(), "system resolves to the platform's scheme");
            assertTrue(scene.getRoot().getStyleClass().contains(dark ? "fxt-theme-dark" : "fxt-theme-light"));
        } finally {
            WaitForAsyncUtils.waitForAsyncFx(2000, () -> ThemeManager.apply(scene, false));
        }
    }

    @Test
    void anAccentAddsItsSheetRightAfterTheDesignTokensAndTheDefaultRemovesIt() {
        String tokens = sheet("design-tokens.css");
        String shell = sheet("unified-shell.css");
        try {
            WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
                nested.getStylesheets().setAll(tokens, shell);
                ThemeManager.apply(scene, false);
                ThemeManager.setAccent("green");
            });
            List<String> sheets = nested.getStylesheets();
            assertEquals(tokens, sheets.get(0));
            assertTrue(sheets.get(1).startsWith("data:text/css"), "accent sheet after the tokens: " + sheets);
            assertEquals("green", ThemeManager.currentAccent());
            assertTrue(org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken.PRIMARY
                    .color(org.fxt.freexmltoolkit.controls.theme.DesignTokens.Theme.LIGHT).getGreen() > 0.4, "Java tokens follow");
            String css = new String(java.util.Base64.getDecoder().decode(
                    sheets.get(1).substring(sheets.get(1).indexOf(',') + 1)), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(css.contains("-fxt-primary:") && css.contains(".root.fxt-theme-dark"), css);

            WaitForAsyncUtils.waitForAsyncFx(2000, () -> ThemeManager.setAccent("violet"));
            assertEquals(1, nested.getStylesheets().stream().filter(s -> s.startsWith("data:")).count(), "replaced, not added");
        } finally {
            WaitForAsyncUtils.waitForAsyncFx(2000, () -> ThemeManager.setAccent(null));
        }
        assertTrue(nested.getStylesheets().stream().noneMatch(s -> s.startsWith("data:")), "the default needs no sheet");
        assertEquals(javafx.scene.paint.Color.web("#1373d9"), org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken.PRIMARY
                .color(org.fxt.freexmltoolkit.controls.theme.DesignTokens.Theme.LIGHT));
    }

    @Test
    void theAccentReachesTheLookedUpColoursOfTheNodes() {
        try {
            javafx.scene.layout.Region probe = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
                // .root rules apply to the scene root only, as in the shell
                root.getStylesheets().setAll(sheet("design-tokens.css"));
                javafx.scene.layout.Region r = new javafx.scene.layout.Region();
                r.setStyle("-fx-background-color: -fxt-primary;");
                nested.getChildren().setAll(r);
                ThemeManager.apply(scene, false);
                ThemeManager.setAccent("#c2410c");
                scene.getRoot().applyCss();
                return r;
            });
            javafx.scene.paint.Paint fill = probe.getBackground().getFills().getFirst().getFill();
            assertEquals(org.fxt.freexmltoolkit.controls.theme.AccentPalette.light(javafx.scene.paint.Color.web("#c2410c"))
                    .get("-fxt-primary"), fill);
        } finally {
            WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
                ThemeManager.setAccent(null);
                root.getStylesheets().clear();
            });
        }
    }
}
