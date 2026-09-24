package org.fxt.freexmltoolkit.controls.dialogs;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Window;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.theme.SemanticColors;
import org.fxt.freexmltoolkit.service.telemetry.ErrorReportResult;
import org.fxt.freexmltoolkit.service.telemetry.Telemetry;
import org.fxt.freexmltoolkit.service.telemetry.TelemetryService;
import org.fxt.freexmltoolkit.util.DialogHelper;

/**
 * "Send error report…" dialog: the user describes the problem (required), optionally
 * leaves a contact, and chooses whether the anonymous technical details (exception class,
 * stack signature — never messages, file names or content) are attached. A collapsible
 * preview shows exactly the JSON that will be sent. Sending is asynchronous.
 */
public final class ErrorReportDialog {

    private ErrorReportDialog() {
    }

    /**
     * @return true when the "Send error report…" entry points should be offered (telemetry
     *         active for this build and error reports enabled by the user)
     */
    public static boolean isAvailable() {
        try {
            TelemetryService t = Telemetry.get();
            return t.isActive() && t.isErrorReportingEnabled();
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Creates the "Send error report…" button used in error dialogs, or {@code null} when
     * error reporting is unavailable/disabled (so callers can simply skip it).
     *
     * @param ownerSupplier supplies the owner window at click time (may return null)
     * @param error         the error the report refers to (may be null)
     */
    public static Button createReportButton(java.util.function.Supplier<Window> ownerSupplier, Throwable error) {
        if (!isAvailable()) {
            return null;
        }
        IconifyIcon icon = new IconifyIcon("bi-send");
        icon.setIconSize(16);
        Button button = new Button("Send error report…", icon);
        button.setOnAction(e -> show(ownerSupplier == null ? null : ownerSupplier.get(), error));
        return button;
    }

    /**
     * Shows the dialog (non-blocking, window-modal to {@code owner}).
     *
     * @param owner the owner window (may be null)
     * @param error the error the report refers to (may be null)
     */
    public static void show(Window owner, Throwable error) {
        build(owner, error).show();
    }

    /** Builds the dialog without showing it. */
    public static Dialog<ButtonType> build(Window owner, Throwable error) {
        TelemetryService telemetry = Telemetry.get();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Send Error Report");
        if (owner != null) {
            dialog.initOwner(owner);
            dialog.initModality(Modality.WINDOW_MODAL);
        } else {
            dialog.initModality(Modality.NONE);
        }
        dialog.setResizable(true);

        DialogPane pane = dialog.getDialogPane();
        pane.getStylesheets().addAll(DialogHelper.getThemeStylesheets());
        pane.setHeaderText("Help us fix this problem");
        IconifyIcon headerIcon = new IconifyIcon("bi-bug");
        headerIcon.setIconSize(32);
        headerIcon.setIconColor(Color.web(SemanticColors.INFO));
        pane.setGraphic(headerIcon);

        Label intro = new Label("Describe what you were doing when the problem occurred. "
                + "The report is sent anonymously to the FreeXmlToolkit developers. "
                + "File names, paths and document content are never included.");
        intro.setWrapText(true);
        intro.setMinHeight(Region.USE_PREF_SIZE); // never truncate the privacy statement

        TextArea description = new TextArea();
        description.setPromptText("What did you do, what happened, what did you expect? (required)");
        description.setWrapText(true);
        description.setPrefRowCount(6);
        description.setMinHeight(90);
        description.setId("error-report-description");

        TextField contact = new TextField();
        contact.setPromptText("Email (optional — only if you would like a reply)");
        contact.setId("error-report-contact");

        CheckBox technical = new CheckBox(error != null
                ? "Include technical details (exception type and stack signature)"
                : "Include technical details (none available for this report)");
        technical.setSelected(true);
        technical.setDisable(error == null);

        TextArea preview = new TextArea();
        preview.setEditable(false);
        preview.setWrapText(false);
        preview.setPrefRowCount(10);
        preview.setStyle("-fx-font-family: 'JetBrains Mono', monospace; -fx-font-size: 11px;");
        TitledPane previewPane = new TitledPane("Preview of the data that will be sent", preview);
        previewPane.setExpanded(false);
        previewPane.setAnimated(false);

        Runnable refreshPreview = () -> preview.setText(telemetry.previewErrorReport(
                description.getText(), contact.getText(), error, technical.isSelected()));
        description.textProperty().addListener((o, a, b) -> {
            if (previewPane.isExpanded()) {
                refreshPreview.run();
            }
        });
        contact.textProperty().addListener((o, a, b) -> {
            if (previewPane.isExpanded()) {
                refreshPreview.run();
            }
        });
        technical.selectedProperty().addListener((o, a, b) -> refreshPreview.run());
        previewPane.expandedProperty().addListener((o, a, expanded) -> {
            if (expanded) {
                refreshPreview.run();
            }
            fitWindow(pane);
        });
        refreshPreview.run();

        Label status = new Label();
        status.setWrapText(true);
        status.setMinHeight(Region.USE_PREF_SIZE);

        VBox content = new VBox(10, intro, new Label("Description:"), description,
                new Label("Contact (optional):"), contact, technical, previewPane, status);
        content.setPadding(new Insets(12, 16, 4, 16));
        content.setPrefWidth(560);
        VBox.setVgrow(description, Priority.ALWAYS);
        pane.setContent(content);

        ButtonType sendType = new ButtonType("Send", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(sendType, ButtonType.CANCEL);
        Button send = (Button) pane.lookupButton(sendType);
        IconifyIcon sendIcon = new IconifyIcon("bi-send");
        sendIcon.setIconSize(16);
        send.setGraphic(sendIcon);
        send.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                () -> description.getText() == null || description.getText().isBlank(),
                description.textProperty()));

        send.addEventFilter(ActionEvent.ACTION, evt -> {
            evt.consume(); // keep the dialog open until the result is known
            send.disableProperty().unbind();
            send.setDisable(true);
            description.setDisable(true);
            contact.setDisable(true);
            technical.setDisable(true);
            status.setStyle("-fx-text-fill: " + SemanticColors.NEUTRAL + ";");
            status.setText("Sending…");
            telemetry.sendErrorReport(description.getText(), contact.getText(), error, technical.isSelected())
                    .whenComplete((result, ex) -> Platform.runLater(() -> onResult(dialog, pane, sendType,
                            result != null ? result : ErrorReportResult.failure("The report could not be sent."),
                            status, send, description, contact, technical, error)));
        });
        return dialog;
    }

    private static void onResult(Dialog<ButtonType> dialog, DialogPane pane, ButtonType sendType,
                                 ErrorReportResult result, Label status, Button send,
                                 TextArea description, TextField contact, CheckBox technical, Throwable error) {
        if (result.success()) {
            status.setStyle("-fx-text-fill: " + SemanticColors.SUCCESS + ";");
            status.setText(result.message());
            pane.getButtonTypes().setAll(ButtonType.CLOSE);
        } else {
            status.setStyle("-fx-text-fill: " + SemanticColors.DANGER + ";");
            status.setText(result.message());
            send.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                    () -> description.getText() == null || description.getText().isBlank(),
                    description.textProperty()));
            description.setDisable(false);
            contact.setDisable(false);
            technical.setDisable(error == null);
        }
        fitWindow(pane);
    }

    /**
     * Grows (or shrinks) the dialog window to its content, so expanding the preview or showing
     * a status message does not squeeze the description and intro text.
     */
    private static void fitWindow(DialogPane pane) {
        Platform.runLater(() -> {
            pane.requestLayout();
            if (pane.getScene() != null && pane.getScene().getWindow() != null) {
                pane.getScene().getWindow().sizeToScene();
            }
        });
    }
}
