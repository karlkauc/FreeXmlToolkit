package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.scene.control.Label;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.util.DialogHelper;

/**
 * Severity-aware status reporting for the activity side panels.
 *
 * <p>The panels show progress, success and failure of an action in a single inline
 * status {@link Label}. Historically that label was always rendered muted (grey),
 * so a failure was barely distinguishable from a routine progress message. This
 * helper colours the message by severity (and adds a leading icon), and — for a
 * real failure where an action could not be performed — additionally raises an
 * unmistakable modal error dialog via {@link DialogHelper#notifyActionFailure}.
 *
 * <p>The three severity style classes ({@code fxt-status-info},
 * {@code fxt-status-success}, {@code fxt-status-error}) are mutually exclusive;
 * each call replaces whichever was set before. They are defined in
 * {@code unified-shell.css} and override the panels' base label styling.
 */
public final class PanelStatus {

    private static final String INFO = "fxt-status-info";
    private static final String SUCCESS = "fxt-status-success";
    private static final String ERROR = "fxt-status-error";

    private PanelStatus() {
    }

    /** Neutral / progress message (muted, no icon) — e.g. "Generating…". */
    public static void info(Label status, String text) {
        apply(status, text, INFO, null);
    }

    /** Success message (green, check icon) — e.g. "Generated: report.pdf". */
    public static void success(Label status, String text) {
        apply(status, text, SUCCESS, "bi-check-circle");
    }

    /**
     * A precondition that blocks the action (red inline, error icon, <b>no</b> dialog) —
     * e.g. "Select a stylesheet first." / "No document open.". These are expected,
     * mild guards, so they are made visible but do not interrupt with a popup.
     */
    public static void precondition(Label status, String text) {
        apply(status, text, ERROR, "bi-exclamation-triangle");
    }

    /**
     * A real failure: an action could not be performed. The message is shown red
     * inline (error icon) <b>and</b> raised as a modal error dialog so the user is
     * unmistakably informed. A leading {@code "ERROR: "} prefix (as returned by the
     * runners) is stripped for display.
     *
     * @param status      the inline status label
     * @param dialogTitle the error dialog's window title (e.g. "PDF generation failed")
     * @param message     the failure message (may carry an "ERROR: " prefix)
     */
    public static void failure(Label status, String dialogTitle, String message) {
        String clean = strip(message);
        apply(status, clean, ERROR, "bi-x-circle");
        DialogHelper.notifyActionFailure(dialogTitle, clean);
    }

    private static void apply(Label status, String text, String severityClass, String iconLiteral) {
        status.setText(text);
        status.getStyleClass().removeAll(INFO, SUCCESS, ERROR);
        status.getStyleClass().add(severityClass);
        if (iconLiteral != null) {
            IconifyIcon icon = new IconifyIcon(iconLiteral);
            icon.setIconSize(13);
            status.setGraphic(icon);
        } else {
            status.setGraphic(null);
        }
    }

    /** Removes a leading {@code "ERROR:"} / {@code "ERROR "} prefix from a runner message. */
    static String strip(String message) {
        if (message == null) {
            return "";
        }
        String trimmed = message.strip();
        if (trimmed.regionMatches(true, 0, "ERROR", 0, 5)) {
            String rest = trimmed.substring(5).strip();
            if (rest.startsWith(":")) {
                rest = rest.substring(1).strip();
            }
            return rest;
        }
        return message;
    }
}
