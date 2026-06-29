package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;

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

    private final Label docName = new Label("none");
    private final Label docInfo = new Label("no document open");
    private final VBox certificateBox = new VBox(8);
    private ToggleButton enveloped;
    private ToggleButton enveloping;
    private ToggleButton detached;
    private File xmlFile;

    SignDocumentView(SignaturePanel panel, EditorHost editorHost) {
        getStyleClass().add("fxt-sign-view");
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);

        // --- card header -------------------------------------------------------
        IconifyIcon headIcon = new IconifyIcon("bi-pencil");
        headIcon.setIconSize(20);
        StackPane iconTile = new StackPane(headIcon);
        iconTile.getStyleClass().add("fxt-sign-icon-tile");
        Label title = new Label("Sign XML Document");
        title.getStyleClass().add("fxt-sign-card-title");
        Label subtitle = new Label("XML Digital Signature · XML-DSig");
        subtitle.getStyleClass().add("fxt-sign-card-sub");
        HBox head = new HBox(14, iconTile, new VBox(2, title, subtitle));
        head.setAlignment(Pos.CENTER_LEFT);

        // --- DOCUMENT ------------------------------------------------------------
        docName.setId("sign-doc-name");
        docName.getStyleClass().add("fxt-sign-doc-name");
        docInfo.getStyleClass().add("fxt-sign-doc-info");
        Region docSpacer = new Region();
        HBox.setHgrow(docSpacer, Priority.ALWAYS);
        Hyperlink browse = new Hyperlink("Browse");
        browse.getStyleClass().add("fxt-vp-change");
        browse.setOnAction(e -> chooseDocument());
        IconifyIcon docIcon = new IconifyIcon("bi-code-slash");
        docIcon.setIconSize(18);
        HBox docRow = new HBox(12, docIcon, new VBox(2, docName, docInfo), docSpacer, browse);
        docRow.getStyleClass().add("fxt-sign-doc-row");
        docRow.setAlignment(Pos.CENTER_LEFT);

        // --- keystore credentials (shared with the side panel) ---------------------
        TextField alias = new TextField();
        alias.setId("sign-card-alias");
        alias.setPromptText("alias");
        alias.textProperty().bindBidirectional(panel.aliasField().textProperty());
        PasswordField password = new PasswordField();
        password.setId("sign-card-password");
        password.setPromptText("keystore password");
        password.textProperty().bindBidirectional(panel.keystorePasswordField().textProperty());
        VBox aliasBox = fieldBox("Keystore alias", alias);
        VBox passwordBox = fieldBox("Keystore password", password);
        HBox.setHgrow(aliasBox, Priority.ALWAYS);
        HBox.setHgrow(passwordBox, Priority.ALWAYS);
        HBox credentials = new HBox(16, aliasBox, passwordBox);

        // --- SIGNATURE OPTIONS ------------------------------------------------------
        Label optionsLabel = sectionLabel("SIGNATURE OPTIONS");
        Label typeLabel = new Label("Signature type");
        typeLabel.getStyleClass().add("fxt-sig-field-label");
        ToggleGroup typeGroup = new ToggleGroup();
        enveloped = segment("Enveloped", typeGroup);
        enveloped.setId("sign-type-enveloped");
        enveloped.setSelected(true);
        enveloped.setTooltip(new Tooltip("The signature is inserted into the signed document"));
        enveloping = segment("Enveloping", typeGroup);
        enveloping.setId("sign-type-enveloping");
        enveloping.setTooltip(new Tooltip("The signed content is wrapped inside the signature document"));
        detached = segment("Detached", typeGroup);
        detached.setId("sign-type-detached");
        detached.setTooltip(new Tooltip("A standalone .sig.xml referencing the file - keep both together"));
        typeGroup.selectedToggleProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) {
                typeGroup.selectToggle(oldV);
            }
        });
        HBox typeSeg = new HBox(2, enveloped, enveloping, detached);
        typeSeg.getStyleClass().add("fxt-seg-group");

        Label algoLabel = new Label("Algorithm");
        algoLabel.getStyleClass().add("fxt-sig-field-label");
        Region algoSpacer = new Region();
        HBox.setHgrow(algoSpacer, Priority.ALWAYS);
        Label algoValue = new Label("RSA-SHA256 · C14N exclusive");
        algoValue.getStyleClass().add("fxt-sign-algo");
        HBox algoRow = new HBox(algoLabel, algoSpacer, algoValue);
        algoRow.setAlignment(Pos.CENTER_LEFT);

        // --- Sign + status ------------------------------------------------------------
        Button sign = new Button("Sign Document", icon("bi-shield-lock", 15));
        sign.setId("sign-card-run");
        sign.getStyleClass().add("fxt-primary-button");
        sign.setMaxWidth(Double.MAX_VALUE);
        sign.setOnAction(e -> panel.signFile(xmlFile, selectedSignatureType()));
        Label status = new Label();
        status.getStyleClass().add("fxt-vp-status");
        status.setWrapText(true);
        status.textProperty().bind(panel.statusTextProperty());

        // --- CERTIFICATE (the mockup's certificate inspector) -----------------------
        Hyperlink showCertificate = new Hyperlink("Show certificate details");
        showCertificate.setId("sign-cert-load");
        showCertificate.getStyleClass().add("fxt-vp-change");
        showCertificate.setOnAction(e -> loadCertificate(panel, alias.getText(), password.getText()));
        certificateBox.setId("sign-cert-box");
        certificateBox.setVisible(false);
        certificateBox.setManaged(false);

        VBox card = new VBox(16, head,
                sectionLabel("DOCUMENT"), docRow,
                credentials,
                optionsLabel, typeLabel, typeSeg, algoRow,
                sign, status,
                new HBox(sectionLabel("CERTIFICATE"), hSpacer(), showCertificate),
                certificateBox);
        card.getStyleClass().add("fxt-sign-card");
        card.setMaxWidth(560);

        StackPane wrap = new StackPane(card);
        wrap.getStyleClass().add("fxt-sign-view-wrap");
        StackPane.setAlignment(card, Pos.TOP_CENTER);
        setContent(wrap);

        setDocument(editorHost.getActiveDocument()
                .map(OpenDocument::getPath).map(p -> p != null ? p.toFile() : null).orElse(null));
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

    private static Region hSpacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private void chooseDocument() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select XML document");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML", "*.xml"));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            setDocument(file);
        }
    }

    private static VBox fieldBox(String label, Region field) {
        Label fieldLabel = new Label(label);
        fieldLabel.getStyleClass().add("fxt-sig-field-label");
        return new VBox(4, fieldLabel, field);
    }

    private static Label sectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("fxt-sign-section-label");
        return label;
    }

    private static ToggleButton segment(String text, ToggleGroup group) {
        ToggleButton toggle = new ToggleButton(text);
        toggle.setToggleGroup(group);
        toggle.getStyleClass().add("fxt-seg");
        toggle.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(toggle, Priority.ALWAYS);
        return toggle;
    }

    private static IconifyIcon icon(String literal, int size) {
        IconifyIcon icon = new IconifyIcon(literal);
        icon.setIconSize(size);
        return icon;
    }
}
