package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.service.SignatureService;
import org.fxt.freexmltoolkit.service.telemetry.UsageEvents;

/**
 * The Signature activity side panel, laid out after the Figma mockup
 * "Redesign · Unified — Signature" (node 50:2): an action nav (Create
 * Certificate / Sign XML File / Validate Signature / Expert Mode) switches the
 * visible form section below a shared KEYSTORE section (file + alias +
 * passwords). Signing/validation reuse {@link SignatureService} and
 * {@link SignatureActionRunner} and run off the UI thread; Expert Mode hosts
 * the PKIX trust validation ({@link SignatureTrustValidator}).
 */
public class SignaturePanel extends VBox {

    /** The mockup's nav actions; each shows its own form section. */
    enum Action { CREATE, SIGN, VALIDATE, EXPERT }

    private final EditorHost editorHost;
    @FXML private ToggleGroup navGroup;
    @FXML private ToggleButton navCreate;
    @FXML private ToggleButton navSign;
    @FXML private ToggleButton navValidate;
    @FXML private ToggleButton navExpert;
    @FXML private VBox validateSection;
    @FXML private VBox createSection;
    @FXML private VBox expertSection;
    @FXML private SourceRow keystoreRow;
    @FXML private SourceRow trustRow;
    @FXML private TextField alias;
    @FXML private PasswordField keystorePassword;
    @FXML private PasswordField aliasPassword;
    @FXML private Label keystoreName;
    @FXML private Label trustStoreName;
    @FXML private Label status;
    @FXML private CheckBox checkRevocation;
    @FXML private TextField cnField;
    @FXML private TextField ouField;
    @FXML private TextField orgField;
    @FXML private TextField localityField;
    @FXML private TextField stateField;
    @FXML private TextField countryField;
    @FXML private TextField emailField;
    private final Map<Action, ToggleButton> nav = new EnumMap<>(Action.class);
    private final Map<Action, VBox> sections = new EnumMap<>(Action.class);
    private File keystoreFile;
    private File trustStoreFile;
    private final javafx.scene.control.MenuButton keystoreFavoritesMenu;
    private final javafx.scene.control.MenuButton trustStoreFavoritesMenu;
    private javafx.scene.control.Tab signTab;
    private SignDocumentView signView;

    /** Loads the layout from {@code /pages/panels/SignaturePanel.fxml} and wires the data-driven parts. */
    public SignaturePanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        FxmlViews.load(this, "/pages/panels/SignaturePanel.fxml");

        nav.put(Action.CREATE, navCreate);
        nav.put(Action.SIGN, navSign);
        nav.put(Action.VALIDATE, navValidate);
        nav.put(Action.EXPERT, navExpert);
        // SIGN has no panel section: per the mockup the sign form is a card in the
        // main editor area (SignDocumentView), opened by the nav entry.
        sections.put(Action.VALIDATE, validateSection);
        sections.put(Action.CREATE, createSection);
        sections.put(Action.EXPERT, expertSection);
        // A nav always has exactly one active entry.
        navGroup.selectedToggleProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) {
                navGroup.selectToggle(oldV);
            }
        });

        keystoreFavoritesMenu = FavoritesMenu.create(
                org.fxt.freexmltoolkit.domain.FileFavorite.FileType.KEYSTORE,
                "Keystore favorites", this::setKeystore);
        keystoreRow.addExtras(keystoreFavoritesMenu);
        org.fxt.freexmltoolkit.controls.shell.FileDropSupport.install(keystoreRow,
                org.fxt.freexmltoolkit.service.DragDropService.KEYSTORE_EXTENSIONS, this::setKeystore);

        trustStoreFavoritesMenu = FavoritesMenu.create(
                org.fxt.freexmltoolkit.domain.FileFavorite.FileType.KEYSTORE,
                "Trust store favorites", this::setTrustStore);
        trustRow.addExtras(trustStoreFavoritesMenu);
        org.fxt.freexmltoolkit.controls.shell.FileDropSupport.install(trustRow,
                org.fxt.freexmltoolkit.service.DragDropService.KEYSTORE_EXTENSIONS, this::setTrustStore);

        selectAction(Action.SIGN);
    }

    @FXML
    public void onNavCreate() {
        selectAction(Action.CREATE);
    }

    /** "Sign XML File" additionally opens the sign card in the editor area. */
    @FXML
    public void onNavSign() {
        selectAction(Action.SIGN);
        openSignCard();
    }

    @FXML
    public void onNavValidate() {
        selectAction(Action.VALIDATE);
    }

    @FXML
    public void onNavExpert() {
        selectAction(Action.EXPERT);
    }

    /**
     * Selects a nav action and shows only its form section (mockup nav behavior).
     * SIGN has no panel section - its form is the {@link SignDocumentView} card
     * in the editor area (opened on the nav click, not on programmatic selection).
     */
    void selectAction(Action action) {
        nav.get(action).setSelected(true);
        sections.forEach((a, box) -> {
            boolean show = a == action;
            box.setVisible(show);
            box.setManaged(show);
        });
    }

    /** Opens (or focuses) the "Sign XML Document" card in the editor area. */
    void openSignCard() {
        if (signTab != null && editorHost.containsTab(signTab) && signView != null) {
            signView.setDocument(activeXmlFile());
            editorHost.selectTab(signTab);
            return;
        }
        signView = new SignDocumentView(this, editorHost);
        signTab = editorHost.openToolTab("Sign", "bi-pencil", signView);
    }

    // ----- shared state for the SignDocumentView card -------------------------

    /** @return the alias field (the card binds to it bidirectionally). */
    TextField aliasField() {
        return alias;
    }

    /** @return the keystore password field (the card binds to it bidirectionally). */
    PasswordField keystorePasswordField() {
        return keystorePassword;
    }

    /** @return the status text property (the card mirrors it). */
    javafx.beans.property.ReadOnlyStringProperty statusTextProperty() {
        return status.textProperty();
    }

    /** @return the chosen keystore file, or {@code null} (the card's certificate inspector reads it). */
    File currentKeystoreFile() {
        return keystoreFile;
    }

    /**
     * Creates a self-signed certificate / JKS keystore from the DN fields and the
     * keystore alias + passwords (async). On success the new keystore is selected
     * so the document can be signed with it immediately.
     */
    @FXML
    public void createCertificate() {
        String aliasName = alias.getText();
        boolean missing = FieldMarker.markIf(alias, aliasName == null || aliasName.isBlank());
        missing |= FieldMarker.markIf(keystorePassword, keystorePassword.getText().isBlank());
        missing |= FieldMarker.markIf(aliasPassword, aliasPassword.getText().isBlank());
        if (missing) {
            PanelStatus.precondition(status,
                    "Fill in the highlighted keystore fields — alias and both passwords "
                            + "are required to create a certificate.");
            return;
        }
        SignatureActionRunner.CertificateInfo info = new SignatureActionRunner.CertificateInfo(
                cnField.getText(), ouField.getText(), orgField.getText(), localityField.getText(),
                stateField.getText(), countryField.getText(), emailField.getText());
        String ksPw = keystorePassword.getText();
        String aliasPw = aliasPassword.getText();
        PanelStatus.info(status, "Creating certificate…");
        FxtGui.executorService.submit(() -> {
            String result;
            File created = null;
            try {
                created = SignatureActionRunner.createKeystore(info, aliasName, ksPw, aliasPw);
                result = "Created keystore: " + created.getAbsolutePath();
            } catch (Exception e) {
                result = "ERROR: " + e.getMessage();
            }
            UsageEvents.certificateCreated(created != null);
            File ks = created;
            String finalResult = result;
            Platform.runLater(() -> {
                if (ks != null) {
                    PanelStatus.success(status, finalResult);
                    setKeystore(ks); // ready to sign with the new keystore
                } else {
                    PanelStatus.failure(status, "Certificate creation failed", finalResult);
                }
            });
        });
    }

    /** Sets the keystore file (also from the file chooser). */
    public void setKeystore(File file) {
        this.keystoreFile = file;
        if (file != null) {
            FieldMarker.clear(keystoreName);
        }
        setSourceName(keystoreName, file != null ? file.getName() : null);
    }

    /** Sets the alias and passwords (for tests/observers). */
    public void setCredentials(String aliasName, String keystorePw, String aliasPw) {
        alias.setText(aliasName);
        keystorePassword.setText(keystorePw);
        aliasPassword.setText(aliasPw);
    }

    /** Signs the active XML (enveloped) to {@code <name>.signed.xml} next to it (async). */
    public void signActive() {
        signFile(activeXmlFile());
    }

    /** Signs {@code xml} (enveloped) to {@code <name>.signed.xml} next to it (async). */
    public void signFile(File xml) {
        signFile(xml, SignatureService.SignatureType.ENVELOPED);
    }

    /**
     * Signs {@code xml} with the given signature structure (async). Enveloped and
     * enveloping signatures are written to {@code <name>.signed.xml}; a detached
     * signature is a standalone document written to {@code <name>.sig.xml}.
     */
    public void signFile(File xml, SignatureService.SignatureType type) {
        if (xml == null) {
            PanelStatus.precondition(status, "No document open.");
            return;
        }
        if (keystoreFile == null) {
            FieldMarker.mark(keystoreName);
            PanelStatus.precondition(status,
                    "Select a keystore first (the highlighted entry in the KEYSTORE section). "
                            + "\"Create Certificate\" can generate one for you.");
            return;
        }
        FieldMarker.clear(keystoreName);
        boolean missing = FieldMarker.markIf(alias, alias.getText().isBlank());
        missing |= FieldMarker.markIf(keystorePassword, keystorePassword.getText().isBlank());
        missing |= FieldMarker.markIf(aliasPassword, aliasPassword.getText().isBlank());
        if (missing) {
            PanelStatus.precondition(status,
                    "Fill in the highlighted keystore fields — alias and both passwords "
                            + "are required for signing.");
            return;
        }
        File keystore = keystoreFile;
        String aliasName = alias.getText();
        String ksPw = keystorePassword.getText();
        String aliasPw = aliasPassword.getText();
        File output = type == SignatureService.SignatureType.DETACHED
                ? siblingFile(xml, ".sig.xml") : siblingFile(xml, ".signed.xml");
        PanelStatus.info(status, "Signing…");
        FxtGui.executorService.submit(() -> {
            long t0 = System.nanoTime();
            String result;
            boolean ok = false;
            try {
                File signed = new SignatureService()
                        .signDocument(xml, keystore, ksPw, aliasName, aliasPw, output.getAbsolutePath(), type);
                result = "Signed: " + signed.getName();
                ok = true;
            } catch (Exception e) {
                result = "ERROR: " + e.getMessage();
            }
            UsageEvents.signed(t0, ok);
            String finalResult = result;
            Platform.runLater(() -> {
                if (finalResult.startsWith("Signed:")) {
                    PanelStatus.success(status, finalResult);
                    if (output.exists()) {
                        editorHost.openFile(output.toPath());
                    }
                } else {
                    PanelStatus.failure(status, "Signing failed", finalResult);
                }
            });
        });
    }

    /** Validates the active document's signature (async), reporting a speaking outcome. */
    @FXML
    public void validateActive() {
        File xml = activeXmlFile();
        if (xml == null) {
            PanelStatus.precondition(status, "No document open.");
            return;
        }
        PanelStatus.info(status, "Validating…");
        FxtGui.executorService.submit(() -> {
            long t0 = System.nanoTime();
            SignatureService.ValidationOutcome outcome;
            try {
                outcome = new SignatureService().validateSignatureDetailed(xml);
            } catch (Exception e) {
                outcome = new SignatureService.ValidationOutcome(
                        SignatureService.ValidationStatus.ERROR,
                        "Failed to validate the signature: " + e.getMessage(), e);
            }
            SignatureService.ValidationOutcome result = outcome;
            UsageEvents.signatureVerified("basic",
                    result.status().name().toLowerCase(java.util.Locale.ROOT), t0);
            Platform.runLater(() -> reportValidationOutcome(result));
        });
    }

    /** Maps a validation outcome to the status line and, for failures, an explanatory dialog. */
    private void reportValidationOutcome(SignatureService.ValidationOutcome outcome) {
        switch (outcome.status()) {
            case VALID -> PanelStatus.success(status, "Signature valid ✓");
            case NO_SIGNATURE -> PanelStatus.precondition(status,
                    "This document contains no signature. Sign it first via \"Sign XML File\".");
            case INVALID -> PanelStatus.failure(status, "Signature validation failed",
                    outcome.message(),
                    "The document no longer matches its signature — it was probably modified "
                            + "after signing. Re-sign the current content, or validate the original, "
                            + "unmodified file. \"Validate (Details)\" shows the signing certificate.",
                    outcome.cause());
            case UNSUPPORTED_KEY_INFO -> PanelStatus.failure(status, "Signature validation failed",
                    outcome.message(),
                    "Validation uses the certificate embedded in the signature itself. Ask the "
                            + "sender for a signature that embeds its X.509 certificate (RSA key, "
                            + "SHA-256/512), or inspect the signature via \"Validate (Details)\". "
                            + "Files signed by this application always embed their certificate.",
                    outcome.cause());
            case WEAK_ALGORITHM -> PanelStatus.failure(status, "Signature validation failed",
                    outcome.message(),
                    "SHA-1 signatures are rejected for security reasons. "
                            + "Re-sign the document with SHA-256 or SHA-512.",
                    outcome.cause());
            case ERROR -> PanelStatus.failure(status, "Signature validation failed",
                    outcome.message(),
                    "Check that the file is well-formed XML and readable, then try again. "
                            + "The technical details below show the underlying cause.",
                    outcome.cause());
        }
    }

    /**
     * Validates the active document's signature and opens a detailed report
     * (validity + signing-certificate details) as a new tab (async).
     */
    @FXML
    public void validateDetailsActive() {
        File xml = activeXmlFile();
        if (xml == null) {
            PanelStatus.precondition(status, "No document open.");
            return;
        }
        PanelStatus.info(status, "Validating…");
        FxtGui.executorService.submit(() -> {
            long t0 = System.nanoTime();
            String report = SignatureActionRunner.describeSignature(xml);
            UsageEvents.signatureVerified("details", report.startsWith("ERROR") ? "error" : "report", t0);
            Platform.runLater(() -> {
                PanelStatus.success(status, "Validation report opened");
                editorHost.openGeneratedDocument(report, EditorFileType.OTHER, "Signature-Report.txt");
            });
        });
    }

    /** Picks a trust store (.jks/.p12) whose trusted certificates are the validation anchors. */
    @FXML
    public void chooseTrustStore() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select trust store");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Key/Trust store", "*.jks", "*.p12", "*.pfx", "*.keystore"));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            setTrustStore(file);
        }
    }

    /** Sets the trust store file (from the chooser or a favorite). */
    public void setTrustStore(File file) {
        this.trustStoreFile = file;
        trustStoreName.setText(file != null ? file.getName() : "default (cacerts)");
    }

    /** @return the favorite-keystore names currently in the quick-select menu (for tests). */
    public java.util.List<String> keystoreFavoriteNames() {
        FavoritesMenu.populate(keystoreFavoritesMenu,
                org.fxt.freexmltoolkit.domain.FileFavorite.FileType.KEYSTORE, this::setKeystore);
        return FavoritesMenu.leafNames(keystoreFavoritesMenu);
    }

    /**
     * Validates the active signed document's certificate chain against the trust store (PKIX:
     * chain + trust anchor + optional revocation + timestamp presence) and opens the report.
     */
    @FXML
    public void validateTrustActive() {
        File xml = activeXmlFile();
        if (xml == null) {
            PanelStatus.precondition(status, "No document open.");
            return;
        }
        File store = trustStoreFile;
        boolean revocation = checkRevocation.isSelected();
        char[] storePassword = keystorePassword.getText().isBlank() ? null : keystorePassword.getText().toCharArray();
        PanelStatus.info(status, "Validating trust…");
        FxtGui.executorService.submit(() -> {
            long t0 = System.nanoTime();
            String report;
            String outcome;
            try {
                java.security.KeyStore trustStore = loadTrustStore(store, storePassword);
                SignatureTrustValidator.TrustResult result =
                        SignatureTrustValidator.validate(xml, trustStore, revocation);
                report = "Trust validation of " + xml.getName() + "\n\n" + result.report();
                outcome = result.trusted() ? "valid" : "untrusted";
            } catch (Exception e) {
                report = "ERROR: " + e.getMessage();
                outcome = "error";
            }
            UsageEvents.signatureVerified("trust", outcome, t0);
            String finalReport = report;
            Platform.runLater(() -> {
                PanelStatus.success(status, "Trust report opened");
                editorHost.openGeneratedDocument(finalReport, EditorFileType.OTHER, "Signature-Trust-Report.txt");
            });
        });
    }

    /** Loads the chosen trust store, or the JVM default {@code cacerts} when none is selected. */
    private static java.security.KeyStore loadTrustStore(File storeFile, char[] password) throws Exception {
        if (storeFile != null) {
            String type = storeFile.getName().toLowerCase().matches(".*\\.(p12|pfx)$") ? "PKCS12" : "JKS";
            java.security.KeyStore ks = java.security.KeyStore.getInstance(type);
            try (var in = new java.io.FileInputStream(storeFile)) {
                ks.load(in, password);
            }
            return ks;
        }
        File cacerts = new File(System.getProperty("java.home"), "lib/security/cacerts");
        java.security.KeyStore ks = java.security.KeyStore.getInstance("JKS");
        try (var in = new java.io.FileInputStream(cacerts)) {
            ks.load(in, null);
        }
        return ks;
    }

    /** @return the current status text (for tests/observers). */
    public String getStatusText() {
        return status.getText();
    }

    // ----- helpers --------------------------------------------------------------

    private File activeXmlFile() {
        var doc = editorHost.getActiveDocument();
        return (doc.isPresent() && doc.get().getPath() != null) ? doc.get().getPath().toFile() : null;
    }

    private File siblingFile(File xml, String suffix) {
        String name = xml.getName().replaceFirst("\\.xml$", "") + suffix;
        return new File(xml.getParentFile(), name);
    }

    @FXML
    public void onChooseKeystore() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Keystore");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Java KeyStore", "*.jks", "*.keystore"));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            setKeystore(file);
        }
    }

    /** Sets a source-row name, toggling the muted "none" style. */
    private static void setSourceName(Label label, String name) {
        label.setText(name != null ? name : "none");
        label.getStyleClass().remove("fxt-vp-source-none");
        if (name == null) {
            label.getStyleClass().add("fxt-vp-source-none");
        }
    }
}
