package org.fxt.freexmltoolkit.controls.shell;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

import org.fxt.freexmltoolkit.controls.theme.DesignTokens;

/**
 * Gives every dialog window the look of the shell: the shell's stylesheets (design tokens, Inter,
 * {@code unified-shell.css}), the light/dark theme of the main window (and later switches), and
 * the root style class {@value #DIALOG_CLASS} that the dialog rules of {@code unified-shell.css}
 * are scoped by.
 *
 * <p>{@link #installGlobally()} watches {@link Window#getWindows()}: JavaFX adds a window to that
 * list when it starts showing, before the scene's preferred size is computed, so the sheets are
 * in place for the first layout. Every window whose scene root is a {@link DialogPane} (Alert,
 * Dialog, TextInputDialog, ChoiceDialog and their subclasses) is themed, as is any window whose
 * properties hold {@value #DIALOG_MARKER} (a Stage-based popup opting in).</p>
 */
public final class DialogTheming {

    /** Root style class of every themed dialog. */
    public static final String DIALOG_CLASS = "fxt-dialog";
    /** Window property a Stage-based popup sets to be themed like a dialog. */
    public static final String DIALOG_MARKER = "fxt.dialog";

    private static final String INSTALLED = "fxt.dialogTheming";
    private static final String[] SHELL_SHEETS = {
            "/css/design-tokens.css", "/css/app-theme.css", "/css/fxt-theme.css", "/css/unified-shell.css"};
    /** Sheets of the retired dialog styling that may still be attached by old code paths. */
    private static final String[] LEGACY_SHEETS = {
            "app-theme.css", "light-theme.css", "dark-theme.css", "dialog-theme.css",
            "design-tokens.css", "fxt-theme.css", "unified-shell.css"};

    private static boolean globallyInstalled;

    private DialogTheming() {
    }

    /** Themes every dialog window, now and those opened later. Idempotent. */
    public static synchronized void installGlobally() {
        if (globallyInstalled) {
            return;
        }
        globallyInstalled = true;
        Window.getWindows().forEach(DialogTheming::install);
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                change.getAddedSubList().forEach(DialogTheming::install);
            }
        });
    }

    /** The shell stylesheets in cascade order (the theme sheet is inserted by {@link ThemeManager}). */
    public static List<String> shellStylesheets() {
        List<String> sheets = new ArrayList<>(SHELL_SHEETS.length);
        for (String sheet : SHELL_SHEETS) {
            URL url = DialogTheming.class.getResource(sheet);
            if (url != null) {
                sheets.add(url.toExternalForm());
            }
        }
        return sheets;
    }

    /**
     * Themes {@code dialog} right away (for code that measures it before showing, and tests).
     * The global hook does the same when the dialog shows.
     */
    public static void apply(Dialog<?> dialog) {
        if (dialog != null && dialog.getDialogPane() != null) {
            applyTo(dialog.getDialogPane());
        }
    }

    /** Themes a dialog's pane: shell sheets, current light/dark theme, {@value #DIALOG_CLASS}. */
    public static void applyTo(DialogPane pane) {
        applyToRoot(pane, ThemeManager.currentIsDark());
    }

    private static void install(Window window) {
        if (!(window.getScene() != null && isDialog(window))) {
            return;
        }
        Scene scene = window.getScene();
        applyToRoot(scene.getRoot(), ThemeManager.currentIsDark());
        inheritIcons(window);
        if (window.getProperties().putIfAbsent(INSTALLED, Boolean.TRUE) != null) {
            return;
        }
        // Follow later light/dark switches while the window is open; dialogs are reused
        // (showAndWait twice), so the listener is added on every show and removed on hide.
        Consumer<DesignTokens.Theme> listener = theme -> Platform.runLater(() -> {
            Scene current = window.getScene();
            if (current != null) {
                applyToRoot(current.getRoot(), theme == DesignTokens.Theme.DARK);
            }
        });
        subscribe(listener);
        window.addEventHandler(WindowEvent.WINDOW_SHOWN, e -> subscribe(listener));
        window.addEventHandler(WindowEvent.WINDOW_HIDDEN, e -> ThemeManager.removeThemeChangeListener(listener));
    }

    /** A dialog without its own window icons takes the app icon of the window it belongs to. */
    private static void inheritIcons(Window window) {
        if (!(window instanceof Stage stage) || !stage.getIcons().isEmpty()) {
            return;
        }
        for (Window owner = stage.getOwner(); owner != null;
             owner = owner instanceof Stage s ? s.getOwner() : null) {
            if (owner instanceof Stage ownerStage && !ownerStage.getIcons().isEmpty()) {
                stage.getIcons().setAll(ownerStage.getIcons());
                return;
            }
        }
    }

    /** Adds the listener once (the listener list allows duplicates). */
    private static void subscribe(Consumer<DesignTokens.Theme> listener) {
        ThemeManager.removeThemeChangeListener(listener);
        ThemeManager.addThemeChangeListener(listener);
    }

    private static boolean isDialog(Window window) {
        return window.getScene().getRoot() instanceof DialogPane
                || Boolean.TRUE.equals(window.getProperties().get(DIALOG_MARKER));
    }

    private static void applyToRoot(Parent root, boolean dark) {
        if (root == null) {
            return;
        }
        var sheets = root.getStylesheets();
        sheets.removeIf(DialogTheming::isManagedSheet);
        sheets.addAll(0, shellStylesheets());
        if (!root.getStyleClass().contains(DIALOG_CLASS)) {
            root.getStyleClass().add(DIALOG_CLASS);
        }
        insertThemeSheet(root, dark);
        ThemeManager.syncAppearanceSheet(root);
        if (root instanceof DialogPane pane) {
            fitButtonsToLabels(pane);
        }
    }

    /**
     * ButtonBar gives every button the same width, taken before the shell font and padding
     * apply, which cut longer labels ("Don't Save"): each button keeps at least its label width.
     */
    private static void fitButtonsToLabels(DialogPane pane) {
        for (ButtonType type : pane.getButtonTypes()) {
            if (pane.lookupButton(type) instanceof Button button) {
                button.setMinWidth(Region.USE_PREF_SIZE);
            }
        }
    }

    /**
     * Same placement {@link ThemeManager} uses (just before unified-shell.css), but only on the
     * pane: the scene stylesheets of an owned dialog are content-bound to its owner's scene, and
     * changing them directly breaks that binding.
     */
    private static void insertThemeSheet(Parent root, boolean dark) {
        URL url = DialogTheming.class.getResource(dark ? "/css/dark-theme.css" : "/css/light-theme.css");
        var sheets = root.getStylesheets();
        int at = sheets.size();
        for (int i = 0; i < sheets.size(); i++) {
            if (sheets.get(i).endsWith("unified-shell.css")) {
                at = i;
                break;
            }
        }
        if (url != null) {
            sheets.add(at, url.toExternalForm());
        }
        root.getStyleClass().removeAll("fxt-theme-dark", "fxt-theme-light");
        root.getStyleClass().add(dark ? "fxt-theme-dark" : "fxt-theme-light");
    }

    private static boolean isManagedSheet(String sheet) {
        for (String legacy : LEGACY_SHEETS) {
            if (sheet.endsWith("/" + legacy)) {
                return true;
            }
        }
        return false;
    }
}
