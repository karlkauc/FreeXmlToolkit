package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.i18n.I18n;

/**
 * A compact, reusable progress/cancel block for the activity side panels.
 *
 * <p>Long-running panel actions (batch validation, XSLT transform, PDF generation, online lookups, plugin checks)
 * show a progress bar, what runs right now and a Cancel button:
 * <ul>
 *   <li>{@link #beginDeterminate(int, Runnable)} — a real 0…N progress bar with a
 *       running count (use when the number of steps is known, e.g. batch files);</li>
 *   <li>{@link #beginIndeterminate(Runnable)} — an animated indeterminate bar for a
 *       single opaque operation (a transform / PDF render);</li>
 *   <li>{@link #setMessage(String)} / {@link #setSteps(long, long)} / {@link #setElapsed(String)} /
 *       {@link #setHint(String, String, Runnable)} — the detail line, a changing step count, the time so far and an
 *       actionable hint (used by {@link PanelJobs}).</li>
 * </ul>
 *
 * <p>The control hides itself ({@code managed = false}) when idle, so it takes no
 * space in the panel layout until an action starts. All methods must be called on
 * the FX thread.
 */
public final class PanelProgress extends VBox {

    private final ProgressBar bar = new ProgressBar();
    private final Label countLabel = new Label();
    private final Label elapsedLabel = new Label();
    private final Button cancelButton = new Button(I18n.t("PanelProgress.cancel"));
    private final Label messageLabel = new Label();
    private final Label hintLabel = new Label();
    private final Hyperlink hintLink = new Hyperlink();
    private final HBox hintRow = new HBox(4, hintLabel, hintLink);
    private Runnable onCancel;
    private Runnable hintAction;
    private int total;

    public PanelProgress() {
        super(2);
        getStyleClass().add("fxt-panel-progress");

        bar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(bar, Priority.ALWAYS);

        countLabel.getStyleClass().add("fxt-panel-progress-count");
        elapsedLabel.getStyleClass().add("fxt-panel-progress-count");
        elapsedLabel.setId("panel-progress-elapsed");

        IconifyIcon cancelIcon = new IconifyIcon("bi-x-circle");
        cancelIcon.setIconSize(13);
        cancelButton.setGraphic(cancelIcon);
        cancelButton.getStyleClass().add("fxt-panel-progress-cancel");
        cancelButton.setId("panel-progress-cancel");
        cancelButton.setOnAction(e -> {
            Runnable r = onCancel;
            if (r != null) {
                cancelButton.setDisable(true); // one click is enough; the job stops at its next step
                r.run();
            }
        });

        HBox row = new HBox(8, bar, countLabel, elapsedLabel, cancelButton);
        row.setAlignment(Pos.CENTER_LEFT);

        messageLabel.getStyleClass().addAll("fxt-panel-progress-message", "fxt-text-xs");
        messageLabel.setId("panel-progress-message");
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(Double.MAX_VALUE);
        messageLabel.setMaxHeight(32); // two lines; the tooltip holds the whole text
        hintLabel.getStyleClass().addAll("fxt-panel-progress-hint", "fxt-text-xs");
        hintLabel.setWrapText(true);
        hintLink.getStyleClass().add("fxt-text-xs");
        hintLink.setId("panel-progress-hint-link");
        hintLink.setOnAction(e -> {
            Runnable r = hintAction;
            if (r != null) {
                r.run();
            }
        });
        hintRow.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(row, messageLabel, hintRow);
        show(messageLabel, false);
        show(hintRow, false);
        show(elapsedLabel, false);
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
        show(countLabel, false);
        start(onCancel != null);
    }

    /**
     * Starts a determinate run over {@code total} steps, showing a {@code done / total}
     * count. The Cancel button invokes {@code onCancel} (may be null to hide it).
     */
    public void beginDeterminate(int total, Runnable onCancel) {
        this.onCancel = onCancel;
        this.total = Math.max(total, 0);
        bar.setProgress(0);
        show(countLabel, true);
        countLabel.setText("0 / " + this.total);
        start(onCancel != null);
    }

    /** Updates the determinate bar and count to {@code done} completed steps. */
    public void setProgress(int done) {
        if (total > 0) {
            int clamped = Math.min(Math.max(done, 0), total);
            bar.setProgress(clamped / (double) total);
            countLabel.setText(clamped + " / " + total);
        }
    }

    /**
     * Shows {@code done} of {@code total} steps; the total may change while the run goes on (an estimate that becomes
     * known). {@code total <= 0} switches to the indeterminate bar.
     */
    public void setSteps(long done, long total) {
        if (total <= 0) {
            this.total = 0;
            bar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
            show(countLabel, false);
            return;
        }
        long clamped = Math.min(Math.max(done, 0), total);
        this.total = (int) Math.min(Integer.MAX_VALUE, total);
        bar.setProgress(clamped / (double) total);
        show(countLabel, true);
        countLabel.setText(clamped + " / " + total);
    }

    /** Shows what runs right now below the bar ({@code null} or blank hides the line). */
    public void setMessage(String message) {
        boolean on = message != null && !message.isBlank();
        if (on && !message.equals(messageLabel.getText())) {
            messageLabel.setText(message);
            messageLabel.setTooltip(new Tooltip(message));
        }
        show(messageLabel, on);
    }

    /** @return the detail line's text, {@code ""} when hidden */
    public String getMessage() {
        return messageLabel.isManaged() ? messageLabel.getText() : "";
    }

    /** Shows the time the run has taken so far ({@code null} hides it). */
    public void setElapsed(String elapsed) {
        elapsedLabel.setText(elapsed == null ? "" : elapsed);
        show(elapsedLabel, elapsed != null);
    }

    /** Shows a hint with a link (e.g. "Set an OpenFIGI API key"); {@code text == null} hides it. */
    public void setHint(String text, String linkText, Runnable action) {
        hintAction = action;
        hintLabel.setText(text == null ? "" : text);
        hintLink.setText(linkText == null ? "" : linkText);
        show(hintLink, linkText != null && action != null);
        show(hintRow, text != null);
    }

    /** Shows or hides the Cancel button while running (a run whose current step cannot be stopped hides it). */
    public void setCancel(Runnable onCancel) {
        this.onCancel = onCancel;
        cancelButton.setDisable(false);
        show(cancelButton, onCancel != null);
    }

    /** Ends the run and hides the control. */
    public void finish() {
        onCancel = null;
        hintAction = null;
        setMessage(null);
        setElapsed(null);
        setHint(null, null, null);
        setVisible(false);
        setManaged(false);
    }

    /** @return {@code true} while a run is in progress (the control is showing). */
    public boolean isRunning() {
        return isManaged();
    }

    private void start(boolean cancellable) {
        cancelButton.setDisable(false);
        show(cancelButton, cancellable);
        setVisible(true);
        setManaged(true);
    }

    private static void show(javafx.scene.Node node, boolean on) {
        node.setVisible(on);
        node.setManaged(on);
    }
}
