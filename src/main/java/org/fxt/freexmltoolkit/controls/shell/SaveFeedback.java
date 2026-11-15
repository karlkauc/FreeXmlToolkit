package org.fxt.freexmltoolkit.controls.shell;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

import javafx.animation.PauseTransition;
import javafx.scene.control.Label;
import javafx.util.Duration;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.theme.DesignTokens;
import org.fxt.freexmltoolkit.controls.theme.SemanticIcon;
import org.fxt.freexmltoolkit.i18n.I18n;

/**
 * Short confirmation in the status bar after a save ("Saved a.xml (12:55:18)"). Before it, the only sign
 * of a save was the dirty dot leaving the tab title, and saving an unchanged file showed nothing at all
 * (telemetry: users pressed Save several times within a second). Shown for {@link #SHOWN}; a new save
 * restarts it with the new time, so repeated saves are visibly confirmed each time. FX thread only.
 */
public final class SaveFeedback {

    /** How long the message stays. */
    public static final Duration SHOWN = Duration.seconds(3);

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Label label;
    private final Supplier<LocalTime> clock;
    private final PauseTransition hide = new PauseTransition(SHOWN);

    /**
     * @param label the status-bar label to fill; hidden (and unmanaged) while there is no message
     */
    public SaveFeedback(Label label) {
        this(label, LocalTime::now);
    }

    SaveFeedback(Label label, Supplier<LocalTime> clock) {
        this.label = label;
        this.clock = clock;
        label.managedProperty().bind(label.visibleProperty());
        label.setVisible(false);
        hide.setOnFinished(e -> clear());
    }

    /** One file was written. */
    public void saved(String fileName) {
        show(I18n.t("SaveFeedback.saved", fileName, TIME.format(clock.get())));
    }

    /** Save All finished with {@code count} files written. */
    public void savedAll(int count) {
        if (count <= 0) {
            show(I18n.t("SaveFeedback.nothing-to-save"));
        } else if (count == 1) {
            show(I18n.t("SaveFeedback.saved-one", TIME.format(clock.get())));
        } else {
            show(I18n.t("SaveFeedback.saved-all", count, TIME.format(clock.get())));
        }
    }

    /** The message currently shown, or {@code null}. */
    public String message() {
        return label.isVisible() ? label.getText() : null;
    }

    private void show(String text) {
        IconifyIcon icon = new IconifyIcon("bi-check-circle");
        icon.setIconSize(11);
        SemanticIcon.bind(icon, DesignTokens.ColorToken.SUCCESS);
        label.setGraphic(icon);
        label.setText(text);
        label.setVisible(true);
        hide.playFromStart();
    }

    private void clear() {
        label.setVisible(false);
        label.setText(null);
        label.setGraphic(null);
    }
}
