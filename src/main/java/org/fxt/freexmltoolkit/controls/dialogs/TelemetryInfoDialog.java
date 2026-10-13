package org.fxt.freexmltoolkit.controls.dialogs;

import java.net.URI;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Window;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.theme.SemanticColors;
import org.fxt.freexmltoolkit.service.telemetry.Telemetry;
import org.fxt.freexmltoolkit.service.telemetry.TelemetryService;
import org.fxt.freexmltoolkit.util.DialogHelper;

/**
 * "What is sent?" — explains the anonymous telemetry and shows a sample of the exact payload,
 * the anonymous installation id and a "Reset ID" action.
 */
public final class TelemetryInfoDialog {

    /** Online documentation of the telemetry (docs/telemetry.md). */
    public static final String DOCS_URL = "https://karlkauc.github.io/FreeXmlToolkit/telemetry/";

    private final TelemetryService telemetry;
    @FXML private TextArea payload;
    @FXML private TextField installId;

    /** Controller instance of {@code /pages/dialogs/TelemetryInfoDialog.fxml}; created by {@link #build(Window)}. */
    private TelemetryInfoDialog(TelemetryService telemetry) {
        this.telemetry = telemetry;
    }

    @FXML
    public void onResetId() {
        installId.setText(telemetry.resetInstallId());
        payload.setText(telemetry.previewPayload());
    }

    @FXML
    public void onOpenDocs() {
        openBrowser(DOCS_URL);
    }

    /** Shows the dialog (non-blocking). */
    public static void show(Window owner) {
        build(owner).show();
    }

    /** Builds the dialog without showing it. */
    public static Dialog<ButtonType> build(Window owner) {
        TelemetryService telemetry = Telemetry.get();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Anonymous Usage Statistics");
        if (owner != null) {
            dialog.initOwner(owner);
            dialog.initModality(Modality.WINDOW_MODAL);
        }
        dialog.setResizable(true);

        DialogPane pane = dialog.getDialogPane();
        pane.getStylesheets().addAll(DialogHelper.getThemeStylesheets());
        pane.setHeaderText("What is sent?");
        IconifyIcon headerIcon = new IconifyIcon("bi-shield-check");
        headerIcon.setIconSize(32);
        headerIcon.setIconColor(Color.web(SemanticColors.INFO));
        pane.setGraphic(headerIcon);

        TelemetryInfoDialog view = new TelemetryInfoDialog(telemetry);
        pane.setContent(FxmlViews.loadContent(view, "/pages/dialogs/TelemetryInfoDialog.fxml"));
        TextArea payload = view.payload;
        payload.setText(telemetry.previewPayload());
        view.installId.setText(telemetry.getInstallId());

        ButtonType copyType = new ButtonType("Copy", ButtonBar.ButtonData.LEFT);
        pane.getButtonTypes().addAll(copyType, ButtonType.CLOSE);
        Button copy = (Button) pane.lookupButton(copyType);
        IconifyIcon copyIcon = new IconifyIcon("bi-clipboard");
        copyIcon.setIconSize(16);
        copy.setGraphic(copyIcon);
        copy.addEventFilter(ActionEvent.ACTION, evt -> {
            evt.consume();
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(
                    java.util.Map.of(javafx.scene.input.DataFormat.PLAIN_TEXT, payload.getText()));
        });
        return dialog;
    }

    private static void openBrowser(String url) {
        Thread t = new Thread(() -> {
            try {
                java.awt.Desktop.getDesktop().browse(URI.create(url));
            } catch (Exception ignored) {
                // no desktop browser available
            }
        }, "open-telemetry-docs");
        t.setDaemon(true);
        t.start();
    }
}
