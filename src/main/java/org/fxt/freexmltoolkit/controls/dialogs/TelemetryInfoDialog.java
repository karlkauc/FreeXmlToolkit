package org.fxt.freexmltoolkit.controls.dialogs;

import java.net.URI;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Window;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
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

    private TelemetryInfoDialog() {
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

        Label intro = new Label("FreeXmlToolkit sends anonymous usage statistics (which features are used, "
                + "how long operations take, coarse document type such as XML or XSD, sizes and counts) and "
                + "anonymous error reports (exception type and a stack signature of the application code). "
                + "Your OS family, CPU architecture, Java version, UI language and app version are included.\n\n"
                + "Never sent: file names, paths, document content, exception messages, user names, "
                + "e-mail addresses or your IP address (the server derives only the country and discards the IP).\n\n"
                + "The data goes to a self-hosted server in the EU (Finland). You can switch both kinds of "
                + "data off at any time in Settings ▸ Usage Statistics.");
        intro.setWrapText(true);

        TextArea payload = new TextArea(telemetry.previewPayload());
        payload.setEditable(false);
        payload.setWrapText(false);
        payload.setPrefRowCount(14);
        payload.setStyle("-fx-font-family: 'JetBrains Mono', monospace; -fx-font-size: 11px;");
        VBox.setVgrow(payload, Priority.ALWAYS);

        TextField installId = new TextField(telemetry.getInstallId());
        installId.setEditable(false);
        installId.setId("telemetry-install-id");
        HBox.setHgrow(installId, Priority.ALWAYS);
        IconifyIcon resetIcon = new IconifyIcon("bi-arrow-counterclockwise");
        resetIcon.setIconSize(16);
        Button reset = new Button("Reset ID", resetIcon);
        reset.setOnAction(e -> {
            installId.setText(telemetry.resetInstallId());
            payload.setText(telemetry.previewPayload());
        });
        HBox idRow = new HBox(6, new Label("Anonymous installation ID:"), installId, reset);
        idRow.setAlignment(Pos.CENTER_LEFT);

        Hyperlink docs = new Hyperlink("Read the full privacy documentation");
        docs.setOnAction(e -> openBrowser(DOCS_URL));

        VBox content = new VBox(10, intro, new Label("Example of the data sent (JSON):"), payload, idRow, docs);
        content.setPadding(new Insets(12, 16, 4, 16));
        content.setPrefWidth(640);
        pane.setContent(content);

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
