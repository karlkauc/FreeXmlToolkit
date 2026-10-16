package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
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

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.service.SignatureService;
import org.fxt.freexmltoolkit.service.telemetry.UsageEvents;

/**
 * The Signature page, a tab in the main editor area; the Signature activity's side panel only
 * picks the {@link Action} ({@link #SECTIONS}). Every action follows the same pattern on one
 * card: a header, the inputs it needs (the DOCUMENT and/or the KEYSTORE with alias and both
 * passwords, each shown once), the action's own form ending in its primary button, and the
 * status line.
 *
 * <p>Signing, validation and certificate creation reuse {@link SignatureService} and
 * {@link SignatureActionRunner} and run off the UI thread; Expert Mode is the PKIX trust
 * validation ({@link SignatureTrustValidator}).
 */
public class SignatureView extends ScrollPane {

    /** The page's actions, in the side panel's order of use. */
    public enum Action {
        SIGN("sign", "Sign XML File", "bi-pencil", "Sign XML Document", "XML Digital Signature · XML-DSig"),
        VALIDATE("validate", "Validate Signature", "bi-check2-circle", "Validate Signature",
                "Checks the document against the certificate embedded in its signature"),
        CREATE("create", "Create Certificate", "bi-patch-plus", "Create Certificate",
                "A self-signed certificate in a new keystore, with the alias and passwords above"),
        EXPERT("expert", "Expert Mode", "bi-gear", "Validate Trust",
                "Checks the signing certificate's chain against a trust store (PKIX)");

        private final String key;
        private final String navLabel;
        private final String icon;
        private final String title;
        private final String subtitle;

        Action(String key, String navLabel, String icon, String title, String subtitle) {
            this.key = key;
            this.navLabel = navLabel;
            this.icon = icon;
            this.title = title;
            this.subtitle = subtitle;
        }

        /** @return the stable key used by the side panel */
        public String key() {
            return key;
        }

        /** @return the action for a side-panel key, {@link #SIGN} for unknown keys */
        public static Action fromKey(String key) {
            for (Action action : values()) {
                if (action.key.equals(key)) {
                    return action;
                }
            }
            return SIGN;
        }
    }

    /** The actions as side-panel rows (ids {@code sig-nav-<key>}). */
    public static final List<PageNavigator.Entry> SECTIONS = java.util.Arrays.stream(Action.values())
            .map(a -> new PageNavigator.Entry(a.key, a.navLabel, a.icon, a.title))
            .toList();

    private final EditorHost editorHost;
    @FXML private IconifyIcon actionIcon;
    @FXML private Label actionTitle;
    @FXML private Label actionSubtitle;
    @FXML private VBox documentSection;
    @FXML private VBox keystoreSection;
    @FXML private VBox signSection;
    @FXML private VBox validateSection;
    @FXML private VBox createSection;
    @FXML private VBox expertSection;
    @FXML private Label docName;
    @FXML private Label docInfo;
    @FXML private SourceRow keystoreRow;
    @FXML private Label keystoreName;
    @FXML private TextField alias;
    @FXML private PasswordField keystorePassword;
    @FXML private PasswordField aliasPassword;
    @FXML private ToggleGroup typeGroup;
    @FXML private ToggleButton enveloped;
    @FXML private ToggleButton enveloping;
    @FXML private ToggleButton detached;
    @FXML private Label detachedHint;
    @FXML private VBox certificateBox;
    @FXML private TextField cnField;
    @FXML private TextField ouField;
    @FXML private TextField orgField;
    @FXML private TextField localityField;
    @FXML private TextField stateField;
    @FXML private TextField countryField;
    @FXML private TextField emailField;
    @FXML private SourceRow trustRow;
    @FXML private Label trustStoreName;
    @FXML private PasswordField trustStorePassword;
    @FXML private CheckBox checkRevocation;
    @FXML private Label status;
    @FXML private javafx.scene.control.Hyperlink validateSignedLink;

    private final Map<Action, VBox> sections = new EnumMap<>(Action.class);
    private final javafx.scene.control.MenuButton keystoreFavoritesMenu;
    private final javafx.scene.control.MenuButton trustStoreFavoritesMenu;
    private Action currentAction;
    private Consumer<String> onActionShown = key -> { };
    private File xmlFile;
    private File keystoreFile;
    /** The file the last successful signing wrote ("Validate this signature" checks it). */
    private File signedFile;
    private File trustStoreFile;

    /** Loads the layout from {@code /pages/views/SignatureView.fxml}; starts on {@link Action#SIGN}. */
    public SignatureView(EditorHost editorHost) {
        this.editorHost = editorHost;
        FxmlViews.load(this, "/pages/views/SignatureView.fxml");

        sections.put(Action.SIGN, signSection);
        sections.put(Action.VALIDATE, validateSection);
        sections.put(Action.CREATE, createSection);
        sections.put(Action.EXPERT, expertSection);

        typeGroup.selectedToggleProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) {
                typeGroup.selectToggle(oldV);
            }
            boolean isDetached = typeGroup.getSelectedToggle() == detached;
            detachedHint.setVisible(isDetached);
            detachedHint.setManaged(isDetached);
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

        useActiveDocument();
        selectAction(Action.SIGN);
    }

    // ----- action ---------------------------------------------------------------

    /**
     * Shows the form of {@code action}: the inputs it needs and its own section. A status line
     * belongs to the action that produced it and is cleared on a switch.
     */
    public void selectAction(Action action) {
        if (currentAction != action) {
            PanelStatus.info(status, "");
            showValidateSignedLink(false);
        }
        currentAction = action;
        actionIcon.setIconLiteral(action.icon);
        actionTitle.setText(action.title);
        actionSubtitle.setText(action.subtitle);
        show(documentSection, action != Action.CREATE);
        show(keystoreSection, action == Action.SIGN || action == Action.CREATE);
        sections.forEach((a, box) -> show(box, a == action));
        onActionShown.accept(action.key);
    }

    /** @return the action whose form is shown */
    public Action currentAction() {
        return currentAction;
    }

    /** Reports the key of the shown action now and on every change (for the side panel's mark). */
    public void setOnActionShown(Consumer<String> handler) {
        this.onActionShown = handler != null ? handler : key -> { };
        onActionShown.accept(currentAction.key);
    }

    private static void show(VBox box, boolean visible) {
        box.setVisible(visible);
        box.setManaged(visible);
    }

    // ----- document ---------------------------------------------------------------

    /** Points the DOCUMENT row at the active editor document (if it is a saved file). */
    public void useActiveDocument() {
        File active = activeXmlFile();
        if (active != null || xmlFile == null) {
            setDocument(active);
        }
    }

    /** Sets the document the actions work on and refreshes the DOCUMENT row. */
    void setDocument(File file) {
        this.xmlFile = file;
        if (file != null && file.exists()) {
            docName.setText(file.getName());
            docInfo.setText(String.format("%d KB · %s", Math.max(1, file.length() / 1024),
                    file.getParentFile() != null ? file.getParentFile().getName() : ""));
        } else {
            docName.setText("No document");
            docInfo.setText("Open a saved XML file, or browse for one");
        }
    }

    @FXML
    public void onChooseDocument() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select XML document");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML", "*.xml"));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, window());
        if (file != null) {
            setDocument(file);
        }
    }

    // ----- keystore -----------------------------------------------------------------

    @FXML
    public void onChooseKeystore() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Keystore");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Java KeyStore", "*.jks", "*.keystore"));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, window());
        if (file != null) {
            setKeystore(file);
        }
    }

    /** Sets the keystore file (also from the file chooser, a favourite or a drop). */
    public void setKeystore(File file) {
        this.keystoreFile = file;
        if (file != null) {
            FieldMarker.clear(keystoreName);
        }
        keystoreRow.showFile(file);
    }

    /** Sets the alias and passwords (for tests/observers). */
    public void setCredentials(String aliasName, String keystorePw, String aliasPw) {
        alias.setText(aliasName);
        keystorePassword.setText(keystorePw);
        aliasPassword.setText(aliasPw);
    }

    /** @return the favourite-keystore names currently in the quick-select menu (for tests). */
    public List<String> keystoreFavoriteNames() {
        FavoritesMenu.populate(keystoreFavoritesMenu,
                org.fxt.freexmltoolkit.domain.FileFavorite.FileType.KEYSTORE, this::setKeystore);
        return FavoritesMenu.leafNames(keystoreFavoritesMenu);
    }

    // ----- sign -----------------------------------------------------------------------

    @FXML
    public void onSign() {
        signFile(xmlFile, selectedSignatureType());
    }

    /** @return the signature structure selected in SIGNATURE OPTIONS */
    SignatureService.SignatureType selectedSignatureType() {
        if (enveloping.isSelected()) {
            return SignatureService.SignatureType.ENVELOPING;
        }
        if (detached.isSelected()) {
            return SignatureService.SignatureType.DETACHED;
        }
        return SignatureService.SignatureType.ENVELOPED;
    }

    /** Signs {@code xml} (enveloped) to {@code <name>.signed.xml} next to it (async). */
    public void signFile(File xml) {
        signFile(xml, SignatureService.SignatureType.ENVELOPED);
    }

    /**
     * Signs {@code xml} with the given signature structure (async). Enveloped and enveloping
     * signatures are written to {@code <name>.signed.xml}; a detached signature is a standalone
     * document written to {@code <name>.sig.xml}.
     */
    public void signFile(File xml, SignatureService.SignatureType type) {
        if (xml == null) {
            PanelStatus.precondition(status, "No document open.");
            return;
        }
        if (keystoreFile == null) {
            FieldMarker.mark(keystoreName);
            PanelStatus.precondition(status,
                    "Select a keystore first (the highlighted entry in KEYSTORE). "
                            + "\"Create Certificate\" can generate one for you.");
            return;
        }
        FieldMarker.clear(keystoreName);
        if (credentialsMissing()) {
            PanelStatus.precondition(status,
                    "Fill in the highlighted keystore fields — alias and both passwords "
                            + "are required for signing.");
            return;
        }
        // Signing reads the saved file: offer to save editor changes first.
        if (!editorHost.ensureSavedBeforeTool(xml.toPath(), "Signing")) {
            PanelStatus.info(status, "Signing cancelled.");
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
            boolean success = ok;
            String finalResult = result;
            Platform.runLater(() -> {
                if (success) {
                    PanelStatus.success(status, finalResult);
                    if (output.exists()) {
                        // Open the signed file, but keep this page in front: the next step is
                        // usually checking the signature, offered right below the status.
                        editorHost.openFile(output.toPath());
                        editorHost.selectTabShowing(this);
                        signedFile = output;
                        showValidateSignedLink(true);
                    }
                } else {
                    PanelStatus.failure(status, "Signing failed", finalResult);
                }
            });
        });
    }

    /** Switches to Validate with the file just signed as the document and runs the check. */
    @FXML
    public void onValidateSigned() {
        File file = signedFile;
        selectAction(Action.VALIDATE);
        if (file != null) {
            setDocument(file);
        }
        validateActive();
    }

    private void showValidateSignedLink(boolean visible) {
        validateSignedLink.setVisible(visible);
        validateSignedLink.setManaged(visible);
    }

    /** Marks blank alias / password fields; @return whether one was blank */
    private boolean credentialsMissing() {
        boolean missing = FieldMarker.markIf(alias, alias.getText() == null || alias.getText().isBlank());
        missing |= FieldMarker.markIf(keystorePassword, keystorePassword.getText().isBlank());
        missing |= FieldMarker.markIf(aliasPassword, aliasPassword.getText().isBlank());
        return missing;
    }

    @FXML
    public void onShowCertificate() {
        File keystore = keystoreFile;
        String aliasName = alias.getText();
        String password = keystorePassword.getText();
        if (keystore == null || aliasName == null || aliasName.isBlank()) {
            showCertificateMessage("Select a keystore and enter the alias first.");
            return;
        }
        showCertificateMessage("Loading…");
        FxtGui.executorService.submit(() -> {
            try {
                var details = CertificateDetailsRunner.fromKeystore(keystore, password, aliasName);
                Platform.runLater(() -> showCertificate(details));
            } catch (Exception e) {
                Platform.runLater(() -> showCertificateMessage("Could not load the certificate: " + e.getMessage()));
            }
        });
    }

    /** @return the certificate box (lookups on an unskinned ScrollPane cannot reach the content; for tests). */
    VBox certificateBoxForTests() {
        return certificateBox;
    }

    private void showCertificateMessage(String message) {
        Label label = new Label(message);
        label.getStyleClass().add("fxt-vp-status");
        label.setWrapText(true);
        certificateBox.getChildren().setAll(label);
        certificateBox.setVisible(true);
        certificateBox.setManaged(true);
    }

    /** Renders the certificate details (the certificate inspector content). */
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

    // ----- validate -------------------------------------------------------------------

    /** Validates the document's signature (async), reporting a speaking outcome. */
    @FXML
    public void validateActive() {
        File xml = xmlFile;
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
     * Validates the document's signature and opens a detailed report (validity +
     * signing-certificate details) as a new tab (async).
     */
    @FXML
    public void validateDetailsActive() {
        File xml = xmlFile;
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

    // ----- create certificate ---------------------------------------------------------

    /**
     * Creates a self-signed certificate / JKS keystore from the DN fields and the keystore alias
     * and passwords (async). On success the new keystore is selected so the document can be
     * signed with it immediately.
     */
    @FXML
    public void createCertificate() {
        if (credentialsMissing()) {
            PanelStatus.precondition(status,
                    "Fill in the highlighted keystore fields — alias and both passwords "
                            + "are required to create a certificate.");
            return;
        }
        SignatureActionRunner.CertificateInfo info = new SignatureActionRunner.CertificateInfo(
                cnField.getText(), ouField.getText(), orgField.getText(), localityField.getText(),
                stateField.getText(), countryField.getText(), emailField.getText());
        String aliasName = alias.getText();
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

    // ----- expert: trust validation ---------------------------------------------------

    /** Picks a trust store (.jks/.p12) whose trusted certificates are the validation anchors. */
    @FXML
    public void chooseTrustStore() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select trust store");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Key/Trust store", "*.jks", "*.p12", "*.pfx", "*.keystore"));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, window());
        if (file != null) {
            setTrustStore(file);
        }
    }

    /** Sets the trust store file (from the chooser, a favourite or a drop). */
    public void setTrustStore(File file) {
        this.trustStoreFile = file;
        trustStoreName.setText(file != null ? file.getName() : "default (cacerts)");
    }

    /**
     * Validates the signed document's certificate chain against the trust store (PKIX: chain +
     * trust anchor + optional revocation + timestamp presence) and opens the report.
     */
    @FXML
    public void validateTrustActive() {
        File xml = xmlFile;
        if (xml == null) {
            PanelStatus.precondition(status, "No document open.");
            return;
        }
        File store = trustStoreFile;
        boolean revocation = checkRevocation.isSelected();
        char[] storePassword = trustStorePassword.getText().isBlank()
                ? null : trustStorePassword.getText().toCharArray();
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

    // ----- test / observer accessors ----------------------------------------------------

    /** @return the current status text (for tests/observers). */
    public String getStatusText() {
        return status.getText();
    }

    /** @return the alias field (for tests). */
    TextField aliasField() {
        return alias;
    }

    /** @return the keystore password field (for tests). */
    PasswordField keystorePasswordField() {
        return keystorePassword;
    }

    // ----- helpers ------------------------------------------------------------------------

    private File activeXmlFile() {
        var doc = editorHost.getActiveDocument();
        return (doc.isPresent() && doc.get().getPath() != null) ? doc.get().getPath().toFile() : null;
    }

    private static File siblingFile(File xml, String suffix) {
        String name = xml.getName().replaceFirst("\\.xml$", "") + suffix;
        return new File(xml.getParentFile(), name);
    }

    private javafx.stage.Window window() {
        return getScene() != null ? getScene().getWindow() : null;
    }

    private static IconifyIcon icon(String literal, int size) {
        IconifyIcon icon = new IconifyIcon(literal);
        icon.setIconSize(size);
        return icon;
    }
}
