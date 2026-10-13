package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;

/**
 * The "Sign XML Document" card shown in the main editor area (Figma mockup
 * "Redesign · Unified — Signature", node 50:2): document row with Browse,
 * keystore alias + password (shared bidirectionally with the Signature side
 * panel's KEYSTORE section), the signature type (only enveloped XML-DSig is
 * implemented) and the fixed algorithm, and a primary "Sign Document" button.
 * Signing itself is delegated to the {@link SignaturePanel}, whose status the
 * card mirrors.
 */
public class SignDocumentView extends ScrollPane {

    private final SignaturePanel panel;
    @FXML private ToggleGroup typeGroup;
    @FXML private Label docName;
    @FXML private Label docInfo;
    @FXML private TextField alias;
    @FXML private PasswordField password;
    @FXML private Label status;
    @FXML private VBox certificateBox;
    @FXML private ToggleButton enveloped;
    @FXML private ToggleButton enveloping;
    @FXML private ToggleButton detached;
    private File xmlFile;

    /** Loads the layout from {@code /pages/views/SignDocumentView.fxml} and binds it to the side panel. */
    SignDocumentView(SignaturePanel panel, EditorHost editorHost) {
        this.panel = panel;
        FxmlViews.load(this, "/pages/views/SignDocumentView.fxml");

        // Keystore credentials and status are shared with the side panel.
        alias.textProperty().bindBidirectional(panel.aliasField().textProperty());
        password.textProperty().bindBidirectional(panel.keystorePasswordField().textProperty());
        status.textProperty().bind(panel.statusTextProperty());
        typeGroup.selectedToggleProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) {
                typeGroup.selectToggle(oldV);
            }
        });

        setDocument(editorHost.getActiveDocument()
                .map(OpenDocument::getPath).map(p -> p != null ? p.toFile() : null).orElse(null));
    }

    @FXML
    public void onSign() {
        panel.signFile(xmlFile, selectedSignatureType());
    }

    @FXML
    public void onShowCertificate() {
        loadCertificate(panel, alias.getText(), password.getText());
    }

    /** @return the certificate box (lookups on an unskinned ScrollPane cannot reach the content; for tests). */
    VBox certificateBoxForTests() {
        return certificateBox;
    }

    /** @return the signature structure selected in the SIGNATURE OPTIONS segmented control. */
    org.fxt.freexmltoolkit.service.SignatureService.SignatureType selectedSignatureType() {
        if (enveloping.isSelected()) {
            return org.fxt.freexmltoolkit.service.SignatureService.SignatureType.ENVELOPING;
        }
        if (detached.isSelected()) {
            return org.fxt.freexmltoolkit.service.SignatureService.SignatureType.DETACHED;
        }
        return org.fxt.freexmltoolkit.service.SignatureService.SignatureType.ENVELOPED;
    }

    /** Sets the document to sign and refreshes the DOCUMENT row. */
    void setDocument(File file) {
        this.xmlFile = file;
        if (file != null && file.exists()) {
            docName.setText(file.getName());
            docInfo.setText(String.format("%d KB · %s", Math.max(1, file.length() / 1024),
                    file.getParentFile() != null ? file.getParentFile().getName() : ""));
        } else {
            docName.setText("none");
            docInfo.setText("no document open");
        }
    }

    /** Loads the keystore's certificate off the UI thread and shows its details. */
    private void loadCertificate(SignaturePanel panel, String alias, String password) {
        File keystore = panel.currentKeystoreFile();
        if (keystore == null || alias == null || alias.isBlank()) {
            showCertificateMessage("Select a keystore (side panel) and enter the alias first.");
            return;
        }
        showCertificateMessage("Loading…");
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            try {
                var details = CertificateDetailsRunner.fromKeystore(keystore, password, alias);
                javafx.application.Platform.runLater(() -> showCertificate(details));
            } catch (Exception e) {
                javafx.application.Platform.runLater(() ->
                        showCertificateMessage("Could not load the certificate: " + e.getMessage()));
            }
        });
    }

    private void showCertificateMessage(String message) {
        Label label = new Label(message);
        label.getStyleClass().add("fxt-vp-status");
        label.setWrapText(true);
        certificateBox.getChildren().setAll(label);
        certificateBox.setVisible(true);
        certificateBox.setManaged(true);
    }

    /** Renders the certificate details (the mockup's CERTIFICATE inspector content). */
    void showCertificate(CertificateDetailsRunner.CertificateDetails details) {
        IconifyIcon shield = icon("bi-shield-check", 16);
        Label name = new Label(details.subjectCn());
        name.setId("sign-cert-name");
        name.getStyleClass().add("fxt-sign-doc-name");
        Label badge = new Label(details.selfSigned() ? "self-signed" : "CA-issued");
        badge.getStyleClass().add("fxt-cert-badge");
        HBox headerRow = new HBox(8, shield, name, badge);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        Label validity = new Label(details.currentlyValid()
                ? "Valid · " + details.daysRemaining() + " days remaining"
                : (details.daysRemaining() < 0
                        ? "Expired " + Math.abs(details.daysRemaining()) + " days ago"
                        : "Not yet valid"));
        validity.setId("sign-cert-validity");
        validity.setMaxWidth(Double.MAX_VALUE);
        validity.getStyleClass().add(details.currentlyValid() ? "fxt-cert-valid" : "fxt-cert-expired");

        java.time.format.DateTimeFormatter day = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
        VBox rows = new VBox(4,
                certRow("Subject", details.subjectCn()
                        + (details.subjectO() != null ? " · " + details.subjectO() : "")
                        + (details.subjectC() != null ? " · " + details.subjectC() : "")),
                certRow("Issued", details.issued().format(day)),
                certRow("Expires", details.expires().format(day)),
                certRow("Serial", details.serialHex()),
                certRow("Signature", details.signatureAlgorithm()),
                certRow("Key usage", details.keyUsage() != null ? details.keyUsage() : "–"));

        Label fingerprint = new Label(details.sha256Fingerprint());
        fingerprint.getStyleClass().add("fxt-cert-fingerprint");
        fingerprint.setWrapText(true);
        Button copy = new Button(null, icon("bi-clipboard", 13));
        copy.getStyleClass().add("fxt-tool-button");
        copy.setTooltip(new Tooltip("Copy fingerprint"));
        copy.setOnAction(e -> {
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(details.sha256Fingerprint());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
        });
        HBox fingerprintRow = new HBox(8, fingerprint, copy);
        fingerprintRow.setAlignment(Pos.CENTER_LEFT);
        Label fingerprintLabel = new Label("SHA-256 fingerprint");
        fingerprintLabel.getStyleClass().add("fxt-sig-field-label");

        certificateBox.getChildren().setAll(headerRow, validity, rows, fingerprintLabel, fingerprintRow);
        certificateBox.setVisible(true);
        certificateBox.setManaged(true);
    }

    private static HBox certRow(String key, String value) {
        Label keyLabel = new Label(key);
        keyLabel.getStyleClass().add("fxt-sig-field-label");
        keyLabel.setMinWidth(90);
        Label valueLabel = new Label(value != null ? value : "–");
        valueLabel.getStyleClass().add("fxt-favmgr-detail");
        valueLabel.setWrapText(true);
        HBox row = new HBox(8, keyLabel, valueLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    @FXML
    public void onChooseDocument() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select XML document");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML", "*.xml"));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            setDocument(file);
        }
    }

    private static IconifyIcon icon(String literal, int size) {
        IconifyIcon icon = new IconifyIcon(literal);
        icon.setIconSize(size);
        return icon;
    }
}
