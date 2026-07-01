package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;

/**
 * Toast notification component for the XML Editor V2.
 * Shows temporary messages at the bottom right of the editor.
 */
public class ToastNotification extends HBox {

    /**
     * Toast types with different styling.
     */
    public enum Type {
        /** Informational message (blue). */
        INFO("bi-info-circle", "#3b82f6", "#eff6ff"),
        /** Success message (green). */
        SUCCESS("bi-check-circle", "#22c55e", "#f0fdf4"),
        /** Warning message (orange). */
        WARNING("bi-exclamation-triangle", "#f59e0b", "#fffbeb"),
        /** Error message (red). */
        ERROR("bi-x-circle", "#ef4444", "#fef2f2");

        final String icon;
        final String iconColor;
        final String backgroundColor;

        Type(String icon, String iconColor, String backgroundColor) {
            this.icon = icon;
            this.iconColor = iconColor;
            this.backgroundColor = backgroundColor;
        }
    }

    private static final double FADE_DURATION_MS = 300;
    private static final double DISPLAY_DURATION_MS = 2000;
    private static final double MARGIN = 16;

    private final Label messageLabel;
    private final IconifyIcon icon;

    /**
     * Creates a new toast notification.
     *
     * @param message The message to display
     * @param type    The type of toast (affects styling)
     */
    public ToastNotification(String message, Type type) {
        super(8);  // spacing

        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(10, 16, 10, 12));

        // Icon
        icon = new IconifyIcon(type.icon);
        icon.setIconSize(16);
        icon.setIconColor(Color.web(type.iconColor));

        // Message
        messageLabel = new Label(message);
        messageLabel.setFont(Font.font("System", FontWeight.NORMAL, 12));
        messageLabel.setTextFill(Color.rgb(31, 41, 55));

        getChildren().addAll(icon, messageLabel);

        // Styling
        setStyle(String.format(
            "-fx-background-color: %s; " +
            "-fx-background-radius: 8; " +
            "-fx-border-color: %s; " +
            "-fx-border-radius: 8; " +
            "-fx-border-width: 1; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 10, 0, 0, 4);",
            type.backgroundColor,
            type.iconColor
        ));

        // Initial state
        setOpacity(0);
    }

    /**
     * Shows a toast notification in the given parent pane.
     * The toast will automatically fade out and remove itself.
     *
     * @param parent  The parent pane to show the toast in
     * @param message The message to display
     * @param type    The type of toast (affects styling)
     */
    public static void show(Pane parent, String message, Type type) {
        ToastNotification toast = new ToastNotification(message, type);

        // Wrap in a StackPane for positioning
        StackPane wrapper = new StackPane(toast);
        wrapper.setAlignment(Pos.BOTTOM_RIGHT);
        wrapper.setPadding(new Insets(0, MARGIN, MARGIN, 0));
        wrapper.setMouseTransparent(true);  // Don't block clicks
        wrapper.setPickOnBounds(false);

        // Add to parent
        if (parent instanceof StackPane stackPane) {
            stackPane.getChildren().add(wrapper);
        } else {
            // For non-StackPane parents, add directly
            parent.getChildren().add(wrapper);
        }

        // Animation sequence: fade in -> pause -> fade out -> remove
        FadeTransition fadeIn = new FadeTransition(Duration.millis(FADE_DURATION_MS), toast);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        PauseTransition pause = new PauseTransition(Duration.millis(DISPLAY_DURATION_MS));

        FadeTransition fadeOut = new FadeTransition(Duration.millis(FADE_DURATION_MS), toast);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);

        SequentialTransition sequence = new SequentialTransition(fadeIn, pause, fadeOut);
        sequence.setOnFinished(e -> {
            // Remove from parent
            if (parent instanceof StackPane stackPane) {
                stackPane.getChildren().remove(wrapper);
            } else {
                parent.getChildren().remove(wrapper);
            }
        });

        sequence.play();
    }

    /**
     * Shows a toast anchored to the bottom-right of a window, without touching that
     * window's layout. This is the app-wide entry point for transient success/info
     * feedback: it uses a lightweight {@link javafx.stage.Popup}, so it works over any
     * scene graph (BorderPane shell, dialogs, …) and never blocks the user like a
     * modal alert. No-op when {@code owner} is null or not showing (e.g. headless).
     *
     * @param owner   the window to anchor the toast to
     * @param message the message to display
     * @param type    the type of toast (affects styling)
     */
    public static void show(javafx.stage.Window owner, String message, Type type) {
        if (owner == null || !owner.isShowing()) {
            return;
        }
        ToastNotification toast = new ToastNotification(message, type);

        javafx.stage.Popup popup = new javafx.stage.Popup();
        popup.setAutoFix(false);
        popup.setAutoHide(false);
        popup.getContent().add(toast);
        popup.show(owner);

        // Anchor bottom-right of the owner window (screen coordinates), leaving room
        // for a status bar. Size must be resolved first via a CSS/layout pass.
        toast.applyCss();
        toast.autosize();
        double x = owner.getX() + owner.getWidth() - toast.prefWidth(-1) - MARGIN;
        double y = owner.getY() + owner.getHeight() - toast.prefHeight(-1) - MARGIN - 32;
        popup.setX(x);
        popup.setY(y);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(FADE_DURATION_MS), toast);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        PauseTransition pause = new PauseTransition(Duration.millis(DISPLAY_DURATION_MS));
        FadeTransition fadeOut = new FadeTransition(Duration.millis(FADE_DURATION_MS), toast);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        SequentialTransition sequence = new SequentialTransition(fadeIn, pause, fadeOut);
        sequence.setOnFinished(e -> popup.hide());
        sequence.play();
    }

    /** Convenience: show a success toast anchored to {@code owner}. */
    public static void success(javafx.stage.Window owner, String message) {
        show(owner, message, Type.SUCCESS);
    }

    /** Convenience: show an info toast anchored to {@code owner}. */
    public static void info(javafx.stage.Window owner, String message) {
        show(owner, message, Type.INFO);
    }

    /**
     * Convenience method to show an info toast.
     *
     * @param parent The parent pane
     * @param message The message to display
     */
    public static void showInfo(Pane parent, String message) {
        show(parent, message, Type.INFO);
    }

    /**
     * Convenience method to show a success toast.
     *
     * @param parent The parent pane
     * @param message The message to display
     */
    public static void showSuccess(Pane parent, String message) {
        show(parent, message, Type.SUCCESS);
    }

    /**
     * Convenience method to show a warning toast.
     *
     * @param parent The parent pane
     * @param message The message to display
     */
    public static void showWarning(Pane parent, String message) {
        show(parent, message, Type.WARNING);
    }

    /**
     * Convenience method to show an error toast.
     *
     * @param parent The parent pane
     * @param message The message to display
     */
    public static void showError(Pane parent, String message) {
        show(parent, message, Type.ERROR);
    }
}
