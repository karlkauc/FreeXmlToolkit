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
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.SignatureService;
import org.fxt.freexmltoolkit.service.crypto.KeyMaterial;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionAlgorithms;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService;
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
        ENCRYPT("encrypt", "Encrypt", "bi-lock", "Encrypt XML",
                "W3C XML Encryption 1.1 · for certificates, keys or a password"),
        DECRYPT("decrypt", "Decrypt", "bi-unlock", "Decrypt XML",
                "With your private key, a shared key or the password"),
        INSPECT("inspect", "Inspect Encryption", "bi-search", "Inspect Encryption",
                "What is encrypted, for whom and with which algorithms - no key needed"),
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

        /** @return the label in the side panel, in the interface language */
        public String navLabel() {
            return org.fxt.freexmltoolkit.i18n.I18n.or("SignatureView.Action." + name() + ".nav", navLabel);
        }

        /** @return the page title of the action */
        public String title() {
            return org.fxt.freexmltoolkit.i18n.I18n.or("SignatureView.Action." + name() + ".title", title);
        }

        /** @return the line under the title */
        public String subtitle() {
            return org.fxt.freexmltoolkit.i18n.I18n.or("SignatureView.Action." + name() + ".subtitle", subtitle);
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
            .map(a -> new PageNavigator.Entry(a.key, a.navLabel(), a.icon, a.title()))
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
    @FXML private javafx.scene.control.ComboBox<String> signMethod;
    @FXML private javafx.scene.control.ComboBox<String> signDigest;
    @FXML private javafx.scene.control.ComboBox<String> signC14n;
    @FXML private javafx.scene.control.ComboBox<SignatureService.KeyType> keyTypeChoice;
    @FXML private VBox encryptSection;
    @FXML private VBox decryptSection;
    @FXML private VBox inspectSection;
    @FXML private ToggleGroup encTargetGroup;
    @FXML private ToggleButton encTargetDocument;
    @FXML private ToggleButton encTargetCaret;
    @FXML private ToggleButton encTargetXPath;
    @FXML private TextField encXPath;
    @FXML private CheckBox encContentOnly;
    @FXML private javafx.scene.control.ComboBox<XmlEncryptionAlgorithms.Algorithm> encDataAlgorithm;
    @FXML private VBox recipientList;
    @FXML private Label recipientsEmpty;
    @FXML private javafx.scene.control.TitledPane encAdvanced;
    @FXML private javafx.scene.control.ComboBox<XmlEncryptionAlgorithms.Algorithm> encKeyTransport;
    @FXML private javafx.scene.control.ComboBox<XmlEncryptionAlgorithms.Algorithm> encOaepDigest;
    @FXML private javafx.scene.control.ComboBox<XmlEncryptionAlgorithms.Algorithm> encMgf;
    @FXML private javafx.scene.control.ComboBox<XmlEncryptionAlgorithms.Algorithm> encKdf;
    @FXML private javafx.scene.control.ComboBox<XmlEncryptionAlgorithms.Algorithm> encKdfDigest;
    @FXML private javafx.scene.control.ComboBox<String> encKeyWrap;
    @FXML private javafx.scene.control.ComboBox<XmlEncryptionService.X509Reference> encX509;
    @FXML private TextField encRecipientName;
    @FXML private CheckBox encKeysOutside;
    @FXML private javafx.scene.control.TextArea encProperties;
    @FXML private Label encLegacyWarning;
    @FXML private ToggleGroup decKeyGroup;
    @FXML private ToggleButton decKeyKeystore;
    @FXML private ToggleButton decKeyPem;
    @FXML private ToggleButton decKeyFile;
    @FXML private ToggleButton decKeyPassword;
    @FXML private VBox decPemBox;
    @FXML private SourceRow decPemRow;
    @FXML private Label decPemName;
    @FXML private PasswordField decPemPassword;
    @FXML private VBox decKeyFileBox;
    @FXML private SourceRow decKeyFileRow;
    @FXML private Label decKeyFileName;
    @FXML private TextField decKeyName;
    @FXML private PasswordField decPassword;
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
    private final List<EncryptionRecipients.Entry> recipients = new java.util.ArrayList<>();
    private File decryptionKeyFile;
    private File decryptionSymmetricKeyFile;
    /** The caret offset of the active editor when the page was last shown (Element at the caret). */
    private String caretElementXPath;

    /** Loads the layout from {@code /pages/views/SignatureView.fxml}; starts on {@link Action#SIGN}. */
    public SignatureView(EditorHost editorHost) {
        this.editorHost = editorHost;
        FxmlViews.load(this, "/pages/views/SignatureView.fxml");

        sections.put(Action.SIGN, signSection);
        sections.put(Action.VALIDATE, validateSection);
        sections.put(Action.CREATE, createSection);
        sections.put(Action.ENCRYPT, encryptSection);
        sections.put(Action.DECRYPT, decryptSection);
        sections.put(Action.INSPECT, inspectSection);
        initAlgorithmChoices();
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
        actionTitle.setText(action.title());
        actionSubtitle.setText(action.subtitle());
        show(documentSection, action != Action.CREATE);
        show(keystoreSection, action == Action.SIGN || action == Action.CREATE || action == Action.ENCRYPT
                || action == Action.DECRYPT && decKeyKeystore.isSelected());
        if (action == Action.ENCRYPT) {
            rememberCaretElement();
        }
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
            docName.setText(I18n.t("SignatureView.no-document"));
            docInfo.setText(I18n.t("SignatureView.open-a-saved-xml-file"));
        }
    }

    @FXML
    public void onChooseDocument() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("SignatureView.select-xml-document"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML", "*.xml"));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, window());
        if (file != null) {
            pickDocument(file);
        }
    }

    /** A document chosen on the page: it becomes the document and is loaded into the editor, behind the page. */
    void pickDocument(File file) {
        setDocument(file);
        editorHost.openFile(file.toPath(), false);
    }

    // ----- keystore -----------------------------------------------------------------

    @FXML
    public void onChooseKeystore() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("SignatureView.select-keystore"));
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
            PanelStatus.precondition(status, I18n.t("SignatureView.no-document-open"));
            return;
        }
        if (keystoreFile == null) {
            FieldMarker.mark(keystoreName);
            PanelStatus.precondition(status,
                    I18n.t("SignatureView.select-a-keystore-first-the"));
            return;
        }
        FieldMarker.clear(keystoreName);
        if (credentialsMissing()) {
            PanelStatus.precondition(status,
                    I18n.t("SignatureView.fill-in-the-highlighted-keystore"));
            return;
        }
        // Signing reads the saved file: offer to save editor changes first.
        if (!editorHost.ensureSavedBeforeTool(xml.toPath(), "Signing")) {
            PanelStatus.info(status, I18n.t("SignatureView.signing-cancelled"));
            return;
        }
        File keystore = keystoreFile;
        String aliasName = alias.getText();
        String ksPw = keystorePassword.getText();
        String aliasPw = aliasPassword.getText();
        SignatureService.SigningOptions options = signingOptions();
        File output = type == SignatureService.SignatureType.DETACHED
                ? siblingFile(xml, ".sig.xml") : siblingFile(xml, ".signed.xml");
        PanelStatus.info(status, I18n.t("SignatureView.signing"));
        FxtGui.executorService.submit(() -> {
            long t0 = System.nanoTime();
            String result;
            boolean ok = false;
            org.fxt.freexmltoolkit.service.telemetry.QueryFailure failure = null;
            try {
                File signed = new SignatureService()
                        .signDocument(xml, keystore, ksPw, aliasName, aliasPw, output.getAbsolutePath(), type, options);
                result = "Signed: " + signed.getName();
                ok = true;
            } catch (Exception e) {
                result = "ERROR: " + e.getMessage();
                failure = org.fxt.freexmltoolkit.service.telemetry.QueryFailure.of(e);
            }
            UsageEvents.signed(t0, failure);
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
                    // the sign event already carries the failure
                    PanelStatus.failure(status, I18n.t("SignatureView.signing-failed"), finalResult, false);
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
        copy.setTooltip(new Tooltip(I18n.t("SignatureView.copy-fingerprint")));
        copy.setOnAction(e -> {
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(details.sha256Fingerprint());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
        });
        HBox fingerprintRow = new HBox(8, fingerprint, copy);
        fingerprintRow.setAlignment(Pos.CENTER_LEFT);
        Label fingerprintLabel = new Label(I18n.t("SignatureView.sha-256-fingerprint"));
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
            PanelStatus.precondition(status, I18n.t("SignatureView.no-document-open"));
            return;
        }
        PanelStatus.info(status, I18n.t("SignatureView.validating"));
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
                    result.status().name().toLowerCase(java.util.Locale.ROOT), t0,
                    result.status() == SignatureService.ValidationStatus.ERROR
                            ? org.fxt.freexmltoolkit.service.telemetry.QueryFailure.of(result.cause()) : null);
            Platform.runLater(() -> reportValidationOutcome(result));
        });
    }

    /** Maps a validation outcome to the status line and, for failures, an explanatory dialog. */
    private void reportValidationOutcome(SignatureService.ValidationOutcome outcome) {
        switch (outcome.status()) {
            case VALID -> PanelStatus.success(status, I18n.t("SignatureView.signature-valid"));
            case NO_SIGNATURE -> PanelStatus.precondition(status,
                    I18n.t("SignatureView.this-document-contains-no-signature"));
            case INVALID -> PanelStatus.failure(status, I18n.t("SignatureView.signature-validation-failed"),
                    outcome.message(),
                    I18n.t("SignatureView.the-document-no-longer-matches"),
                    outcome.cause(), false); // the user's document, counted by verify_signature
            case UNSUPPORTED_KEY_INFO -> PanelStatus.failure(status, I18n.t("SignatureView.signature-validation-failed"),
                    outcome.message(),
                    I18n.t("SignatureView.validation-uses-the-certificate-embedded"),
                    outcome.cause(), false);
            case WEAK_ALGORITHM -> PanelStatus.failure(status, I18n.t("SignatureView.signature-validation-failed"),
                    outcome.message(),
                    I18n.t("SignatureView.sha-1-signatures-are-rejected"),
                    outcome.cause(), false);
            case ERROR -> PanelStatus.failure(status, I18n.t("SignatureView.signature-validation-failed"),
                    outcome.message(),
                    I18n.t("SignatureView.check-that-the-file-is"),
                    outcome.cause(), false); // verify_signature carries the failure
            default -> { }
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
            PanelStatus.precondition(status, I18n.t("SignatureView.no-document-open"));
            return;
        }
        PanelStatus.info(status, I18n.t("SignatureView.validating"));
        FxtGui.executorService.submit(() -> {
            long t0 = System.nanoTime();
            SignatureActionRunner.Report run = SignatureActionRunner.describeSignatureResult(xml);
            String report = run.text();
            UsageEvents.signatureVerified("details", run.failure() != null ? "error" : "report", t0, run.failure());
            Platform.runLater(() -> {
                if (run.failure() != null) {
                    PanelStatus.failure(status, I18n.t("SignatureView.signature-validation-failed"), report, false);
                } else {
                    PanelStatus.success(status, I18n.t("SignatureView.validation-report-opened"));
                }
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
                    I18n.t("SignatureView.fill-in-the-highlighted-keystore-2"));
            return;
        }
        SignatureActionRunner.CertificateInfo info = new SignatureActionRunner.CertificateInfo(
                cnField.getText(), ouField.getText(), orgField.getText(), localityField.getText(),
                stateField.getText(), countryField.getText(), emailField.getText());
        String aliasName = alias.getText();
        String ksPw = keystorePassword.getText();
        String aliasPw = aliasPassword.getText();
        SignatureService.KeyType keyType = keyTypeChoice.getValue();
        PanelStatus.info(status, I18n.t("SignatureView.creating-certificate"));
        FxtGui.executorService.submit(() -> {
            String result;
            File created = null;
            org.fxt.freexmltoolkit.service.telemetry.QueryFailure failure = null;
            try {
                created = SignatureActionRunner.createKeystore(info, aliasName, ksPw, aliasPw, keyType);
                result = "Created keystore: " + created.getAbsolutePath();
            } catch (Exception e) {
                result = "ERROR: " + e.getMessage();
                failure = org.fxt.freexmltoolkit.service.telemetry.QueryFailure.of(e);
            }
            UsageEvents.certificateCreated(failure);
            File ks = created;
            String finalResult = result;
            Platform.runLater(() -> {
                if (ks != null) {
                    PanelStatus.success(status, finalResult);
                    setKeystore(ks); // ready to sign with the new keystore
                } else {
                    // create_certificate already carries the failure
                    PanelStatus.failure(status, I18n.t("SignatureView.certificate-creation-failed"), finalResult, false);
                }
            });
        });
    }

    // ----- expert: trust validation ---------------------------------------------------

    /** Picks a trust store (.jks/.p12) whose trusted certificates are the validation anchors. */
    @FXML
    public void chooseTrustStore() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("SignatureView.select-trust-store"));
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
            PanelStatus.precondition(status, I18n.t("SignatureView.no-document-open"));
            return;
        }
        File store = trustStoreFile;
        boolean revocation = checkRevocation.isSelected();
        char[] storePassword = trustStorePassword.getText().isBlank()
                ? null : trustStorePassword.getText().toCharArray();
        PanelStatus.info(status, I18n.t("SignatureView.validating-trust"));
        FxtGui.executorService.submit(() -> {
            long t0 = System.nanoTime();
            String report;
            String outcome;
            Exception error = null;
            try {
                java.security.KeyStore trustStore = loadTrustStore(store, storePassword);
                SignatureTrustValidator.TrustResult result =
                        SignatureTrustValidator.validate(xml, trustStore, revocation);
                report = "Trust validation of " + xml.getName() + "\n\n" + result.report();
                outcome = result.trusted() ? "valid" : "untrusted";
            } catch (Exception e) {
                report = "ERROR: " + e.getMessage();
                outcome = "error";
                error = e;
            }
            UsageEvents.signatureVerified("trust", outcome, t0,
                    error != null ? org.fxt.freexmltoolkit.service.telemetry.QueryFailure.of(error) : null);
            String finalReport = report;
            Exception failure = error;
            Platform.runLater(() -> {
                if (failure != null) {
                    // verify_signature carries the failure; the dialog explains it
                    PanelStatus.failure(status, I18n.t("SignatureView.trust-validation-failed"), I18n.t("SignatureView.trust-validation-could-not-run"),
                            I18n.t("SignatureView.check-the-trust-store-file"), failure, false);
                } else {
                    PanelStatus.success(status, I18n.t("SignatureView.trust-report-opened"));
                }
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

    // ----- algorithm choices -----------------------------------------------------------

    private static final String AUTO = "Automatic";

    private void initAlgorithmChoices() {
        signMethod.getItems().add(AUTO);
        signMethod.getItems().addAll(new java.util.TreeSet<>(SignatureService.SIGNATURE_METHODS.keySet()));
        signMethod.setValue(AUTO);
        signDigest.getItems().addAll(new java.util.TreeSet<>(SignatureService.DIGEST_METHODS.keySet()));
        signDigest.setValue("sha256");
        signC14n.getItems().addAll("exclusive", "exclusive-comments", "inclusive", "inclusive-comments", "inclusive-11");
        signC14n.setValue("exclusive");
        keyTypeChoice.getItems().addAll(SignatureService.KeyType.values());
        keyTypeChoice.setValue(SignatureService.KeyType.RSA_2048);

        encDataAlgorithm.getItems().addAll(XmlEncryptionAlgorithms.of(XmlEncryptionAlgorithms.Kind.DATA));
        encDataAlgorithm.setValue(XmlEncryptionAlgorithms.DEFAULT_DATA);
        encKeyTransport.getItems().addAll(XmlEncryptionAlgorithms.of(XmlEncryptionAlgorithms.Kind.KEY_TRANSPORT));
        encKeyTransport.setValue(XmlEncryptionAlgorithms.DEFAULT_KEY_TRANSPORT);
        encOaepDigest.getItems().addAll(XmlEncryptionAlgorithms.of(XmlEncryptionAlgorithms.Kind.DIGEST));
        encOaepDigest.setValue(XmlEncryptionAlgorithms.Algorithm.SHA256);
        encMgf.getItems().addAll(XmlEncryptionAlgorithms.of(XmlEncryptionAlgorithms.Kind.MGF));
        encMgf.setValue(XmlEncryptionAlgorithms.Algorithm.MGF1_SHA256);
        encKdf.getItems().addAll(XmlEncryptionAlgorithms.Algorithm.CONCAT_KDF, XmlEncryptionAlgorithms.Algorithm.HKDF);
        encKdf.setValue(XmlEncryptionAlgorithms.Algorithm.CONCAT_KDF);
        encKdfDigest.getItems().addAll(XmlEncryptionAlgorithms.Algorithm.SHA256, XmlEncryptionAlgorithms.Algorithm.SHA384,
                XmlEncryptionAlgorithms.Algorithm.SHA512);
        encKdfDigest.setValue(XmlEncryptionAlgorithms.Algorithm.SHA256);
        encKeyWrap.getItems().add(AUTO);
        XmlEncryptionAlgorithms.of(XmlEncryptionAlgorithms.Kind.KEY_WRAP).forEach(a -> encKeyWrap.getItems().add(a.id()));
        encKeyWrap.getItems().add("none (key file is the data key)");
        encKeyWrap.setValue(AUTO);
        encX509.getItems().addAll(XmlEncryptionService.X509Reference.values());
        encX509.setValue(XmlEncryptionService.X509Reference.ISSUER_SERIAL);
        javafx.beans.InvalidationListener legacy = o -> updateLegacyWarning();
        encDataAlgorithm.valueProperty().addListener(legacy);
        encKeyTransport.valueProperty().addListener(legacy);
        encKeyWrap.valueProperty().addListener(legacy);

        encTargetGroup.selectedToggleProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) {
                encTargetGroup.selectToggle(oldV);
            }
            boolean xpath = encTargetGroup.getSelectedToggle() == encTargetXPath;
            encXPath.setVisible(xpath);
            encXPath.setManaged(xpath);
        });
        decKeyGroup.selectedToggleProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) {
                decKeyGroup.selectToggle(oldV);
            }
            show(decPemBox, decKeyPem.isSelected());
            show(decKeyFileBox, decKeyFile.isSelected());
            decPassword.setVisible(decKeyPassword.isSelected());
            decPassword.setManaged(decKeyPassword.isSelected());
            if (currentAction == Action.DECRYPT) {
                show(keystoreSection, decKeyKeystore.isSelected());
            }
        });
        org.fxt.freexmltoolkit.controls.shell.FileDropSupport.install(decPemRow, KEY_EXTENSIONS, this::setDecryptionKey);
        org.fxt.freexmltoolkit.controls.shell.FileDropSupport.install(decKeyFileRow, KEY_EXTENSIONS,
                this::setDecryptionKeyFile);
        refreshRecipients();
    }

    /** Extensions of certificate and key files (drops, choosers). */
    static final List<String> KEY_EXTENSIONS = List.of(".pem", ".crt", ".cer", ".der", ".key", ".bin");

    SignatureService.SigningOptions signingOptions() {
        String method = AUTO.equals(signMethod.getValue()) ? null : SignatureService.SIGNATURE_METHODS.get(signMethod.getValue());
        return new SignatureService.SigningOptions(method, SignatureService.DIGEST_METHODS.get(signDigest.getValue()),
                SignatureService.C14N_METHODS.get(signC14n.getValue()));
    }

    private void updateLegacyWarning() {
        java.util.List<String> warnings = new java.util.ArrayList<>();
        java.util.stream.Stream.of(encDataAlgorithm.getValue(), encKeyTransport.getValue(), keyWrapChoice())
                .filter(a -> a != null && a.legacy())
                .forEach(a -> warnings.add(XmlEncryptionService.legacyWarning(a)));
        encLegacyWarning.setText(String.join("\n", warnings));
        encLegacyWarning.setVisible(!warnings.isEmpty());
        encLegacyWarning.setManaged(!warnings.isEmpty());
    }

    private XmlEncryptionAlgorithms.Algorithm keyWrapChoice() {
        String value = encKeyWrap.getValue();
        return value == null ? null
                : XmlEncryptionAlgorithms.parse(XmlEncryptionAlgorithms.Kind.KEY_WRAP, value).orElse(null);
    }

    // ----- encrypt ------------------------------------------------------------------------

    /** Remembers the element at the active editor's caret (the target "Element at the caret"). */
    private void rememberCaretElement() {
        var doc = editorHost.getActiveDocument();
        if (doc.isEmpty()) {
            return;
        }
        String text = editorHost.getActiveViewText().orElse("");
        int caret = editorHost.activeCaretProperty().get();
        org.fxt.freexmltoolkit.controls.shared.utilities.XmlElementAtCaret.at(text, caret)
                .ifPresent(r -> caretElementXPath = EncryptionRecipients.localNameXPath(r.xpath()));
    }

    @FXML
    public void onAddCertificate() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("SignatureView.select-recipient-certificate"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                I18n.t("SignatureView.certificates-and-public-keys"), "*.pem", "*.crt", "*.cer", "*.der"));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, window());
        if (file != null) {
            addCertificateRecipient(file);
        }
    }

    /** Adds the certificates (or the public key) of a PEM/DER file as recipients. */
    void addCertificateRecipient(File file) {
        try {
            java.util.List<java.security.cert.X509Certificate> certificates;
            try {
                certificates = KeyMaterial.certificates(file.toPath());
            } catch (Exception notACertificate) {
                java.security.PublicKey key = KeyMaterial.publicKey(file.toPath());
                recipients.add(new EncryptionRecipients.CertificateEntry(key, null, file.getName()));
                refreshRecipients();
                return;
            }
            for (var certificate : certificates) {
                recipients.add(new EncryptionRecipients.CertificateEntry(certificate.getPublicKey(), certificate,
                        commonName(certificate)));
            }
            refreshRecipients();
        } catch (Exception e) {
            PanelStatus.failure(status, I18n.t("SignatureView.cannot-read-certificate"), e.getMessage(), false);
        }
    }

    @FXML
    public void onAddKeystoreRecipient() {
        if (keystoreFile == null) {
            FieldMarker.mark(keystoreName);
            PanelStatus.precondition(status, I18n.t("SignatureView.select-a-keystore-and-alias"));
            return;
        }
        if (FieldMarker.markIf(alias, alias.getText() == null || alias.getText().isBlank())) {
            PanelStatus.precondition(status, I18n.t("SignatureView.select-a-keystore-and-alias"));
            return;
        }
        try {
            var certificate = KeyMaterial.certificate(keystoreFile.toPath(), keystorePassword.getText().toCharArray(),
                    alias.getText().trim());
            recipients.add(new EncryptionRecipients.CertificateEntry(certificate.getPublicKey(), certificate,
                    commonName(certificate)));
            refreshRecipients();
        } catch (Exception e) {
            PanelStatus.failure(status, I18n.t("SignatureView.cannot-read-certificate"), e.getMessage(), false);
        }
    }

    @FXML
    public void onAddKeyFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("SignatureView.select-key-file"));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, window());
        if (file == null) {
            return;
        }
        java.util.Optional<String> name = org.fxt.freexmltoolkit.util.DialogHelper.promptText(
                I18n.t("SignatureView.key-name"), I18n.t("SignatureView.key-name-header"),
                I18n.t("SignatureView.key-name-label"), "");
        addKeyFileRecipient(file, name.orElse(null));
    }

    /** Adds a shared symmetric key file as recipient (with an optional KeyName). */
    void addKeyFileRecipient(File file, String keyName) {
        try {
            javax.crypto.SecretKey key = KeyMaterial.symmetricKey(file.toPath(), "AES");
            recipients.add(new EncryptionRecipients.KeyFileEntry(key, keyName, file.getName()));
            refreshRecipients();
        } catch (Exception e) {
            PanelStatus.failure(status, I18n.t("SignatureView.cannot-read-key-file"), e.getMessage(), false);
        }
    }

    @FXML
    public void onAddPassword() {
        javafx.scene.control.Dialog<char[]> dialog = org.fxt.freexmltoolkit.util.DialogHelper.createDialog(
                I18n.t("SignatureView.password-recipient"), I18n.t("SignatureView.password-recipient-header"),
                "bi-asterisk");
        PasswordField first = new PasswordField();
        first.setId("enc-password-1");
        first.setPromptText(I18n.t("SignatureView.password"));
        PasswordField second = new PasswordField();
        second.setId("enc-password-2");
        second.setPromptText(I18n.t("SignatureView.repeat-password"));
        Label hint = new Label(I18n.t("SignatureView.password-hint"));
        hint.setWrapText(true);
        hint.getStyleClass().add("fxt-placeholder-text");
        VBox content = new VBox(8, first, second, hint);
        content.setPrefWidth(380);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().setAll(javafx.scene.control.ButtonType.OK,
                javafx.scene.control.ButtonType.CANCEL);
        javafx.scene.Node ok = dialog.getDialogPane().lookupButton(javafx.scene.control.ButtonType.OK);
        ok.disableProperty().bind(first.textProperty().isEmpty()
                .or(first.textProperty().isNotEqualTo(second.textProperty())));
        dialog.setResultConverter(b -> b == javafx.scene.control.ButtonType.OK ? first.getText().toCharArray() : null);
        Platform.runLater(first::requestFocus);
        dialog.showAndWait().ifPresent(this::addPasswordRecipient);
    }

    /** Adds a password recipient. */
    void addPasswordRecipient(char[] password) {
        if (password != null && password.length > 0) {
            recipients.add(new EncryptionRecipients.PasswordEntry(password));
            refreshRecipients();
        }
    }

    private void refreshRecipients() {
        recipientList.getChildren().clear();
        for (EncryptionRecipients.Entry entry : recipients) {
            Label title = new Label(entry.title());
            title.getStyleClass().add("fxt-sign-doc-name");
            Label detail = new Label(entry.detail());
            detail.getStyleClass().add("fxt-sign-doc-info");
            VBox text = new VBox(2, title, detail);
            Button remove = new Button(null, icon("bi-x-lg", 13));
            remove.getStyleClass().add("fxt-tool-button");
            remove.setTooltip(new Tooltip(I18n.t("SignatureView.remove-recipient")));
            remove.setAccessibleText(I18n.t("SignatureView.remove-recipient"));
            remove.setOnAction(e -> {
                recipients.remove(entry);
                refreshRecipients();
            });
            javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
            HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
            HBox row = new HBox(12, icon(entry.icon(), 18), text, spacer, remove);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("fxt-sign-doc-row");
            recipientList.getChildren().add(row);
        }
        show(recipientList, !recipients.isEmpty());
        recipientsEmpty.setVisible(recipients.isEmpty());
        recipientsEmpty.setManaged(recipients.isEmpty());
    }

    /** @return the recipients listed (for tests) */
    List<EncryptionRecipients.Entry> recipientsForTests() {
        return List.copyOf(recipients);
    }

    private static String commonName(java.security.cert.X509Certificate certificate) {
        String dn = certificate.getSubjectX500Principal().getName();
        for (String part : dn.split(",")) {
            if (part.trim().startsWith("CN=")) {
                return part.trim().substring(3);
            }
        }
        return dn;
    }

    /** Encrypts the document as the form says; the result is written next to it and opened. */
    @FXML
    public void onEncrypt() {
        File xml = xmlFile;
        if (xml == null) {
            PanelStatus.precondition(status, I18n.t("SignatureView.no-document-open"));
            return;
        }
        if (recipients.isEmpty()) {
            PanelStatus.precondition(status, I18n.t("SignatureView.add-a-recipient"));
            return;
        }
        java.util.List<String> xpaths = new java.util.ArrayList<>();
        String targetKind = "document";
        if (encTargetGroup.getSelectedToggle() == encTargetXPath) {
            if (FieldMarker.markIf(encXPath, encXPath.getText() == null || encXPath.getText().isBlank())) {
                PanelStatus.precondition(status, I18n.t("SignatureView.enter-an-xpath"));
                return;
            }
            xpaths.add(encXPath.getText().trim());
            targetKind = "xpath";
        } else if (encTargetGroup.getSelectedToggle() == encTargetCaret) {
            rememberCaretElement();
            if (caretElementXPath == null) {
                PanelStatus.precondition(status, I18n.t("SignatureView.place-the-caret-on-an-element"));
                return;
            }
            xpaths.add(caretElementXPath);
            targetKind = "element";
        }
        if (encContentOnly.isSelected()) {
            targetKind = "content";
        }
        if (!editorHost.ensureSavedBeforeTool(xml.toPath(), "Encryption")) {
            PanelStatus.info(status, I18n.t("SignatureView.encryption-cancelled"));
            return;
        }
        String wrapValue = encKeyWrap.getValue();
        boolean direct = wrapValue != null && wrapValue.startsWith("none");
        var options = new EncryptionRecipients.Options(encKeyTransport.getValue(), encOaepDigest.getValue(),
                encMgf.getValue(), encKdf.getValue(), encKdfDigest.getValue(), keyWrapChoice(), direct,
                encX509.getValue(), encRecipientName.getText());
        java.util.List<XmlEncryptionService.Recipient> serviceRecipients;
        try {
            serviceRecipients = EncryptionRecipients.toRecipients(recipients, options);
        } catch (RuntimeException e) {
            PanelStatus.failure(status, I18n.t("SignatureView.encryption-failed"), e.getMessage(), false);
            return;
        }
        var request = new XmlEncryptionService.EncryptionRequest(xpaths, java.util.Map.of(),
                encContentOnly.isSelected() ? XmlEncryptionService.TargetType.CONTENT : XmlEncryptionService.TargetType.ELEMENT,
                encDataAlgorithm.getValue(), serviceRecipients, EncryptionRecipients.properties(encProperties.getText()),
                encKeysOutside.isSelected());
        File output = siblingFile(xml, ".encrypted.xml");
        String target = targetKind;
        PanelStatus.info(status, I18n.t("SignatureView.encrypting"));
        FxtGui.executorService.submit(() -> {
            long t0 = System.nanoTime();
            EncryptionActionRunner.Result result = EncryptionActionRunner.encryptFile(xml.toPath(), output.toPath(), request);
            boolean legacy = !result.warnings().isEmpty();
            UsageEvents.encrypted(target, family(request.data()), String.join(",", serviceRecipients.stream()
                            .map(EncryptionRecipients::keyMethodName).distinct().toList()), serviceRecipients.size(),
                    legacy, t0, result.failure());
            Platform.runLater(() -> {
                if (result.ok()) {
                    PanelStatus.success(status, result.message() + " · " + output.getName()
                            + (legacy ? " · " + String.join(" ", result.warnings()) : ""));
                    editorHost.openFile(output.toPath());
                    editorHost.selectTabShowing(this);
                } else {
                    PanelStatus.failure(status, I18n.t("SignatureView.encryption-failed"), result.message(), false);
                }
            });
        });
    }

    /** @return the data algorithm family for telemetry ("aes-gcm", "aes-cbc", "tripledes-cbc", …) */
    private static String family(XmlEncryptionAlgorithms.Algorithm data) {
        return data.id().replaceAll("\\d+", "");
    }

    // ----- decrypt ------------------------------------------------------------------------

    @FXML
    public void onChooseDecryptionKey() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("SignatureView.select-private-key"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PEM", "*.pem", "*.key"));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, window());
        if (file != null) {
            setDecryptionKey(file);
        }
    }

    /** Sets the PEM private key used to decrypt. */
    public void setDecryptionKey(File file) {
        decryptionKeyFile = file;
        decPemRow.showFile(file);
    }

    @FXML
    public void onChooseDecryptionKeyFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("SignatureView.select-key-file"));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, window());
        if (file != null) {
            setDecryptionKeyFile(file);
        }
    }

    /** Sets the shared key file used to decrypt. */
    public void setDecryptionKeyFile(File file) {
        decryptionSymmetricKeyFile = file;
        decKeyFileRow.showFile(file);
    }

    /** Selects the key source of Decrypt (for tests): keystore, pem, file or password. */
    void selectDecryptionSource(String source) {
        decKeyGroup.selectToggle(switch (source) {
            case "pem" -> decKeyPem;
            case "file" -> decKeyFile;
            case "password" -> decKeyPassword;
            default -> decKeyKeystore;
        });
    }

    /** Sets the decryption password (for tests). */
    void setDecryptionPassword(String password) {
        decPassword.setText(password);
    }

    /** Decrypts the document with the chosen key; the result opens as a new, unsaved tab. */
    @FXML
    public void onDecrypt() {
        File xml = xmlFile;
        if (xml == null) {
            PanelStatus.precondition(status, I18n.t("SignatureView.no-document-open"));
            return;
        }
        String source;
        java.util.concurrent.Callable<XmlEncryptionService.DecryptionKeys> keys;
        if (decKeyPem.isSelected()) {
            if (decryptionKeyFile == null) {
                FieldMarker.mark(decPemName);
                PanelStatus.precondition(status, I18n.t("SignatureView.select-a-private-key"));
                return;
            }
            File key = decryptionKeyFile;
            char[] pass = decPemPassword.getText().toCharArray();
            source = "private_key";
            keys = () -> XmlEncryptionService.DecryptionKeys.privateKey(KeyMaterial.privateKey(key.toPath(), pass));
        } else if (decKeyFile.isSelected()) {
            if (decryptionSymmetricKeyFile == null) {
                FieldMarker.mark(decKeyFileName);
                PanelStatus.precondition(status, I18n.t("SignatureView.select-a-key-file"));
                return;
            }
            File key = decryptionSymmetricKeyFile;
            String name = decKeyName.getText();
            source = "key_file";
            keys = () -> XmlEncryptionService.DecryptionKeys.secretKey(name, KeyMaterial.symmetricKey(key.toPath(), "AES"));
        } else if (decKeyPassword.isSelected()) {
            if (FieldMarker.markIf(decPassword, decPassword.getText().isEmpty())) {
                PanelStatus.precondition(status, I18n.t("SignatureView.enter-the-password"));
                return;
            }
            char[] pass = decPassword.getText().toCharArray();
            source = "password";
            keys = () -> XmlEncryptionService.DecryptionKeys.password(pass);
        } else {
            if (keystoreFile == null) {
                FieldMarker.mark(keystoreName);
                PanelStatus.precondition(status, I18n.t("SignatureView.select-a-keystore-first-the"));
                return;
            }
            File keystore = keystoreFile;
            String aliasName = alias.getText();
            char[] storePass = keystorePassword.getText().toCharArray();
            char[] keyPass = aliasPassword.getText().isEmpty() ? null : aliasPassword.getText().toCharArray();
            source = "keystore";
            keys = () -> XmlEncryptionService.DecryptionKeys.privateKey(
                    KeyMaterial.privateKey(keystore.toPath(), storePass, aliasName, keyPass).privateKey());
        }
        if (!editorHost.ensureSavedBeforeTool(xml.toPath(), "Decryption")) {
            PanelStatus.info(status, I18n.t("SignatureView.decryption-cancelled"));
            return;
        }
        PanelStatus.info(status, I18n.t("SignatureView.decrypting"));
        FxtGui.executorService.submit(() -> {
            long t0 = System.nanoTime();
            EncryptionActionRunner.DecryptedText result;
            try {
                String text = java.nio.file.Files.readString(xml.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                result = EncryptionActionRunner.decryptText(text, xml.toPath(), keys.call(), java.util.Set.of());
            } catch (Exception e) {
                result = new EncryptionActionRunner.DecryptedText(null, null, e.getMessage(), java.util.List.of(),
                        org.fxt.freexmltoolkit.service.telemetry.QueryFailure.invalidInput("key_unreadable"));
            }
            EncryptionActionRunner.DecryptedText done = result;
            UsageEvents.decrypted(source, done.ok() ? Math.max(1, countIn(done.message())) : 0, t0, done.failure());
            Platform.runLater(() -> showDecrypted(xml, done));
        });
    }

    private static int countIn(String message) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("Decrypted (\\d+)").matcher(message);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private void showDecrypted(File source, EncryptionActionRunner.DecryptedText result) {
        if (!result.ok()) {
            PanelStatus.failure(status, I18n.t("SignatureView.decryption-failed"), result.message(), false);
            return;
        }
        String warnings = result.warnings().isEmpty() ? "" : " · " + String.join(" ", result.warnings());
        if (result.binary() != null) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(I18n.t("SignatureView.save-decrypted-data"));
            chooser.setInitialFileName(source.getName().replaceFirst("\\.xml$", "") + ".bin");
            File target = org.fxt.freexmltoolkit.util.FileChooserHelper.showSaveDialog(chooser, window());
            if (target != null) {
                try {
                    java.nio.file.Files.write(target.toPath(), result.binary());
                    PanelStatus.success(status, result.message() + " · " + target.getName() + warnings);
                } catch (java.io.IOException e) {
                    PanelStatus.failure(status, I18n.t("SignatureView.decryption-failed"), e.getMessage(), false);
                }
            }
            return;
        }
        // not written anywhere: the user saves the decrypted document only if wanted
        editorHost.openGeneratedDocument(result.xml(), EditorFileType.XML,
                source.getName().replaceFirst("\\.xml$", "") + ".decrypted.xml");
        editorHost.selectTabShowing(this);
        PanelStatus.success(status, result.message() + warnings);
    }

    // ----- inspect ------------------------------------------------------------------------

    /** Opens the report of what the document encrypts (no key). */
    @FXML
    public void onInspect() {
        File xml = xmlFile;
        if (xml == null) {
            PanelStatus.precondition(status, I18n.t("SignatureView.no-document-open"));
            return;
        }
        PanelStatus.info(status, I18n.t("SignatureView.inspecting"));
        FxtGui.executorService.submit(() -> {
            long t0 = System.nanoTime();
            String text;
            int count = 0;
            org.fxt.freexmltoolkit.service.telemetry.QueryFailure failure = null;
            try {
                var report = EncryptionActionRunner.inspectFile(xml.toPath());
                count = report.data().size();
                text = report.toText();
            } catch (Exception e) {
                text = null;
                failure = org.fxt.freexmltoolkit.service.telemetry.QueryFailure.invalidInput("not_xml");
                String message = e.getMessage();
                Platform.runLater(() -> PanelStatus.failure(status, I18n.t("SignatureView.inspection-failed"),
                        message, false));
            }
            UsageEvents.encryptionInspected(count, t0, failure);
            String report = text;
            int found = count;
            if (report != null) {
                Platform.runLater(() -> {
                    editorHost.openGeneratedDocument(report, EditorFileType.OTHER, "Encryption-Report.txt");
                    editorHost.selectTabShowing(this);
                    PanelStatus.success(status, I18n.t("SignatureView.encrypted-data-found", found));
                });
            }
        });
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
