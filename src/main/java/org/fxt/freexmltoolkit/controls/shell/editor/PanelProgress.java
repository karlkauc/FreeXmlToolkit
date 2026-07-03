package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;

/**
 * A compact, reusable progress/cancel row for the activity side panels.
 *
 * <p>Long-running panel actions (batch validation, XSLT transform, PDF generation)
 * previously showed only a text status like {@code "Validating…"}, so a slow run
 * looked frozen and could not be stopped. This control adds a visible progress bar
 * and a Cancel button:
 * <ul>
 *   <li>{@link #beginDeterminate(int, Runnable)} — a real 0…N progress bar with a
 *       running count (use when the number of steps is known, e.g. batch files);</li>
 *   <li>{@link #beginIndeterminate(Runnable)} — an animated indeterminate bar for a
 *       single opaque operation (a transform / PDF render).</li>
 * </ul>
 *
 * <p>The control hides itself ({@code managed = false}) when idle, so it takes no
 * space in the panel layout until an action starts. All methods must be called on
 * the FX thread.
 */
public final class PanelProgress extends HBox {

    private final ProgressBar bar = new ProgressBar();
    private final Label countLabel = new Label();
    private final Button cancelButton = new Button("Cancel");
    private Runnable onCancel;
    private int total;

    public PanelProgress() {
        super(8);
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("fxt-panel-progress");

        bar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(bar, Priority.ALWAYS);

        countLabel.getStyleClass().add("fxt-panel-progress-count");

        IconifyIcon cancelIcon = new IconifyIcon("bi-x-circle");
        cancelIcon.setIconSize(13);
        cancelButton.setGraphic(cancelIcon);
        cancelButton.getStyleClass().add("fxt-panel-progress-cancel");
        cancelButton.setOnAction(e -> {
            Runnable r = onCancel;
            if (r != null) {
                r.run();
            }
        });

        getChildren().addAll(bar, countLabel, cancelButton);
        setVisible(false);
        setManaged(false);
    }

    /**
     * Starts an indeterminate run (unknown duration, single operation). The Cancel
     * button invokes {@code onCancel} (may be null to hide it).
     */
    public void beginIndeterminate(Runnable onCancel) {
        this.onCancel = onCancel;
        this.total = 0;
        bar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        countLabel.setVisible(false);
        countLabel.setManaged(false);
        show(onCancel != null);
    }

    /**
     * Starts a determinate run over {@code total} steps, showing a {@code done / total}
     * count. The Cancel button invokes {@code onCancel} (may be null to hide it).
     */
    public void beginDeterminate(int total, Runnable onCancel) {
        this.onCancel = onCancel;
        this.total = Math.max(total, 0);
        bar.setProgress(0);
        countLabel.setVisible(true);
        countLabel.setManaged(true);
        countLabel.setText("0 / " + this.total);
        show(onCancel != null);
    }

    /** Updates the determinate bar and count to {@code done} completed steps. */
    public void setProgress(int done) {
        if (total > 0) {
            int clamped = Math.min(Math.max(done, 0), total);
            bar.setProgress(clamped / (double) total);
            countLabel.setText(clamped + " / " + total);
        }
    }

    /** Ends the run and hides the control. */
    public void finish() {
        onCancel = null;
        setVisible(false);
        setManaged(false);
    }

    /** @return {@code true} while a run is in progress (the control is showing). */
    public boolean isRunning() {
        return isManaged();
    }

    private void show(boolean cancellable) {
        cancelButton.setVisible(cancellable);
        cancelButton.setManaged(cancellable);
        setVisible(true);
        setManaged(true);
    }
}
