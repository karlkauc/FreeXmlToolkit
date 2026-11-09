package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.util.DialogHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Every dialog looks like the shell: shell stylesheets, current light/dark theme, the
 * {@code fxt-dialog} root class, an owner window and a type icon coloured by a CSS role.
 */
@ExtendWith(ApplicationExtension.class)
class DialogThemingTest {

    private Stage stage;
    private Scene scene;
    private boolean wasDark;
    private Dialog<?> open;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        this.stage = stage;
        scene = new Scene(new StackPane(), 300, 200);
        stage.setScene(scene);
        stage.show();
        wasDark = ThemeManager.currentIsDark();
        ThemeManager.apply(scene, false);
        DialogTheming.installGlobally();
    }

    @AfterEach
    void tearDown() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            if (open != null) {
                open.hide();
            }
            ThemeManager.apply(scene, wasDark);
            return null;
        });
    }

    @Test
    void aPlainDialogIsThemedWhenItShows() {
        // Built without DialogHelper: only the global window hook can style it
        Dialog<?> dialog = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "plain");
            alert.show();
            return alert;
        });
        open = dialog;
        WaitForAsyncUtils.waitForFxEvents();
        assertShellLook(dialog.getDialogPane().getScene().getRoot(), "fxt-theme-light");
    }

    @Test
    void factoryDialogsHaveAnOwnerAndTheShellLookBeforeShowing() {
        TextInputDialog dialog = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            stage.requestFocus();
            return DialogHelper.createTextInputDialog("Rename", "Rename element", "Name:", "order");
        });
        assertSame(stage, dialog.getOwner(), "opens over the application window");
        List<String> sheets = dialog.getDialogPane().getStylesheets();
        assertTrue(sheets.stream().anyMatch(s -> s.endsWith("/unified-shell.css")), "shell sheets: " + sheets);
        assertTrue(dialog.getDialogPane().getStyleClass().contains(DialogTheming.DIALOG_CLASS));
    }

    @Test
    void openDialogsFollowALightDarkSwitch() {
        Dialog<?> dialog = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            TextInputDialog d = DialogHelper.createTextInputDialog("Name", "Enter a name", "Name:", "");
            d.show();
            return d;
        });
        open = dialog;
        WaitForAsyncUtils.waitForFxEvents();
        Parent root = dialog.getDialogPane().getScene().getRoot();
        assertTrue(root.getStyleClass().contains("fxt-theme-light"));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            ThemeManager.apply(scene, true);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertShellLook(root, "fxt-theme-dark");
        assertFalse(root.getStyleClass().contains("fxt-theme-light"));
    }

    @Test
    void alertIconTakesItsColourFromTheTypeRole() {
        Alert alert = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            Alert a = DialogHelper.createAlert(Alert.AlertType.WARNING);
            a.setHeaderText("Careful");
            a.show();
            return a;
        });
        open = alert;
        WaitForAsyncUtils.waitForFxEvents();
        IconifyIcon icon = assertInstanceOf(IconifyIcon.class, alert.getGraphic());
        assertTrue(icon.getStyleClass().contains("fxt-dialog-icon"));
        assertTrue(alert.getDialogPane().getStyleClass().contains("warning"));
        // -fxt-warning (light) from design-tokens.css, not a hard-coded hex in Java
        assertEquals(Color.web("#f08c00"), icon.getIconColor());
    }

    private static void assertShellLook(Parent root, String themeClass) {
        List<String> sheets = root.getStylesheets();
        assertTrue(sheets.stream().anyMatch(s -> s.endsWith("/design-tokens.css")), "tokens: " + sheets);
        assertTrue(sheets.stream().anyMatch(s -> s.endsWith("/unified-shell.css")), "shell rules: " + sheets);
        assertFalse(sheets.stream().anyMatch(s -> s.endsWith("/dialog-theme.css")), "no legacy sheet");
        assertEquals(1, sheets.stream().filter(s -> s.endsWith("-theme.css") && !s.endsWith("/fxt-theme.css")
                && !s.endsWith("/app-theme.css")).count(), "exactly one light/dark sheet: " + sheets);
        assertTrue(root.getStyleClass().contains(DialogTheming.DIALOG_CLASS), "fxt-dialog class");
        assertTrue(root.getStyleClass().contains(themeClass), themeClass + " on " + root.getStyleClass());
    }
}
