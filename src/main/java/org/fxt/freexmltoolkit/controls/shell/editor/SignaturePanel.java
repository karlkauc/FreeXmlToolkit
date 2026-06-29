package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.service.SignatureService;

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
    private final TextField alias = new TextField();
    private final PasswordField keystorePassword = new PasswordField();
    private final PasswordField aliasPassword = new PasswordField();
    private final Label keystoreName = new Label("none");
    private final Label trustStoreName = new Label("default (cacerts)");
    private final Label status = new Label("Not signed/validated");
    private final CheckBox checkRevocation = new CheckBox("Check revocation (OCSP/CRL)");
    private final TextField cnField = new TextField();
    private final TextField ouField = new TextField();
    private final TextField orgField = new TextField();
    private final TextField localityField = new TextField();
    private final TextField stateField = new TextField();
    private final TextField countryField = new TextField();
    private final TextField emailField = new TextField();
    private final Map<Action, ToggleButton> nav = new EnumMap<>(Action.class);
    private final Map<Action, VBox> sections = new EnumMap<>(Action.class);
    private File keystoreFile;
    private File trustStoreFile;
    private javafx.scene.control.Tab signTab;
    private SignDocumentView signView;

    public SignaturePanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        getStyleClass().add("fxt-signature-panel");

        // --- header: SIGNATURE ------------------------------------------------
        // Keeps the shared side-panel-title class: the shell convention (and
        // UnifiedShellViewTest) identify the active panel's title by it.
        Label title = new Label("SIGNATURE");
        title.getStyleClass().addAll("fxt-side-panel-title", "fxt-vp-title");
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox header = new HBox(title, headerSpacer);
        header.getStyleClass().add("fxt-vp-header");
        header.setAlignment(Pos.CENTER_LEFT);

        // --- action nav (mockup order; Sign is the default) -------------------
        ToggleGroup navGroup = new ToggleGroup();
        VBox navBox = new VBox(
                navItem(Action.CREATE, "Create Certificate", "bi-patch-plus", navGroup),
                navItem(Action.SIGN, "Sign XML File", "bi-pencil", navGroup),
                navItem(Action.VALIDATE, "Validate Signature", "bi-check2-circle", navGroup),
                navItem(Action.EXPERT, "Expert Mode", "bi-gear", navGroup));
        navBox.getStyleClass().add("fxt-sig-nav-box");
        // A nav always has exactly one active entry.
        navGroup.selectedToggleProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) {
                navGroup.selectToggle(oldV);
            }
        });

        // --- KEYSTORE (shared by sign + create) --------------------------------
        keystoreName.setId("sig-keystore-name");
        keystoreName.getStyleClass().addAll("fxt-vp-source-name", "fxt-vp-source-none");
        HBox keystoreRow = sourceRow("bi-file-earmark-lock", keystoreName, this::chooseKeystore);
        alias.setPromptText("alias");
        keystorePassword.setPromptText("keystore password");
        aliasPassword.setPromptText("alias password");
        VBox keystoreFields = new VBox(4,
                fieldLabel("Keystore alias"), alias,
                fieldLabel("Keystore password"), keystorePassword,
                fieldLabel("Alias password"), aliasPassword);
        keystoreFields.getStyleClass().add("fxt-tp-section-body");
        HBox keystoreHeader = SidePanelLayout.sectionHeader(
                new Label("KEYSTORE"), keystoreRow, keystoreFields);

        // SIGN has no panel section: per the mockup the sign form is a card in the
        // main editor area (SignDocumentView), opened by the nav entry.

        // --- VALIDATE section ---------------------------------------------------
        Button validate = primaryButton("Validate Signature", "bi-shield-check",
                "sig-validate-run", this::validateActive);
        Button validateDetails = toolButton("Validate (Details)", "bi-card-list", this::validateDetailsActive);
        VBox validateSection = section(Action.VALIDATE, "sig-section-validate",
                runBox(validate, SidePanelLayout.fill(validateDetails)));

        // --- CREATE CERTIFICATE section ------------------------------------------
        cnField.setPromptText("Common Name (CN)");
        orgField.setPromptText("Organization (O)");
        ouField.setPromptText("Organizational Unit (OU)");
        localityField.setPromptText("Locality (L)");
        stateField.setPromptText("State (ST)");
        countryField.setPromptText("Country (C)");
        emailField.setPromptText("Email");
        Label createHint = new Label("Creates a self-signed certificate using the keystore alias and passwords.");
        createHint.getStyleClass().add("fxt-sig-hint");
        createHint.setWrapText(true);
        Button create = primaryButton("Create Certificate", "bi-patch-plus",
                "sig-create-run", this::createCertificate);
        VBox createFields = new VBox(4, createHint,
                cnField, orgField, ouField, localityField, stateField, countryField, emailField);
        createFields.getStyleClass().add("fxt-tp-section-body");
        VBox createSection = section(Action.CREATE, "sig-section-create",
                SidePanelLayout.sectionHeader(new Label("CERTIFICATE"), createFields), createFields,
                runBox(create));

        // --- EXPERT section (PKIX trust validation) -------------------------------
        trustStoreName.setId("sig-truststore-name");
        trustStoreName.getStyleClass().add("fxt-vp-source-name");
        HBox trustRow = sourceRow("bi-key", trustStoreName, this::chooseTrustStore);
        VBox trustOptions = new VBox(6, checkRevocation);
        trustOptions.getStyleClass().add("fxt-tp-section-body");
        Button validateTrust = primaryButton("Validate (Trust)", "bi-patch-check",
                "sig-trust-run", this::validateTrustActive);
        VBox expertSection = section(Action.EXPERT, "sig-section-expert",
                SidePanelLayout.sectionHeader(new Label("TRUST STORE"), trustRow, trustOptions), trustRow,
                trustOptions, runBox(validateTrust));

        // --- status + assembly -----------------------------------------------------
        status.getStyleClass().add("fxt-vp-status");
        status.setWrapText(true);

        VBox content = new VBox(navBox,
                keystoreHeader, keystoreRow, keystoreFields,
                validateSection, createSection, expertSection,
                status);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("edge-to-edge");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(header, scroll);
        selectAction(Action.SIGN);
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
    public void createCertificate() {
        String aliasName = alias.getText();
        if (aliasName == null || aliasName.isBlank()
                || keystorePassword.getText().isBlank() || aliasPassword.getText().isBlank()) {
            status.setText("Alias and both passwords are required to create a certificate.");
            return;
        }
        SignatureActionRunner.CertificateInfo info = new SignatureActionRunner.CertificateInfo(
                cnField.getText(), ouField.getText(), orgField.getText(), localityField.getText(),
                stateField.getText(), countryField.getText(), emailField.getText());
        String ksPw = keystorePassword.getText();
        String aliasPw = aliasPassword.getText();
        status.setText("Creating certificate…");
        FxtGui.executorService.submit(() -> {
            String result;
            File created = null;
            try {
                created = SignatureActionRunner.createKeystore(info, aliasName, ksPw, aliasPw);
                result = "Created keystore: " + created.getAbsolutePath();
            } catch (Exception e) {
                result = "ERROR: " + e.getMessage();
            }
            File ks = created;
            String finalResult = result;
            Platform.runLater(() -> {
                status.setText(finalResult);
                if (ks != null) {
                    setKeystore(ks); // ready to sign with the new keystore
                }
            });
        });
    }

    /** Sets the keystore file (also from the file chooser). */
    public void setKeystore(File file) {
        this.keystoreFile = file;
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
            status.setText("No document open.");
            return;
        }
        if (keystoreFile == null) {
            status.setText("Select a keystore first.");
            return;
        }
        File keystore = keystoreFile;
        String aliasName = alias.getText();
        String ksPw = keystorePassword.getText();
        String aliasPw = aliasPassword.getText();
        File output = type == SignatureService.SignatureType.DETACHED
                ? siblingFile(xml, ".sig.xml") : siblingFile(xml, ".signed.xml");
        status.setText("Signing…");
        FxtGui.executorService.submit(() -> {
            String result;
            try {
                File signed = new SignatureService()
                        .signDocument(xml, keystore, ksPw, aliasName, aliasPw, output.getAbsolutePath(), type);
                result = "Signed: " + signed.getName();
            } catch (Exception e) {
                result = "ERROR: " + e.getMessage();
            }
            String finalResult = result;
            Platform.runLater(() -> {
                status.setText(finalResult);
                if (finalResult.startsWith("Signed:") && output.exists()) {
                    editorHost.openFile(output.toPath());
                }
            });
        });
    }

    /** Validates the active document's signature (async). */
    public void validateActive() {
        File xml = activeXmlFile();
        if (xml == null) {
            status.setText("No document open.");
            return;
        }
        status.setText("Validating…");
        FxtGui.executorService.submit(() -> {
            boolean valid;
            try {
                valid = new SignatureService().isSignatureValid(xml);
            } catch (Exception e) {
                valid = false;
            }
            boolean result = valid;
            Platform.runLater(() -> status.setText(result ? "Signature valid ✓" : "Signature invalid / none"));
        });
    }

    /**
     * Validates the active document's signature and opens a detailed report
     * (validity + signing-certificate details) as a new tab (async).
     */
    public void validateDetailsActive() {
        File xml = activeXmlFile();
        if (xml == null) {
            status.setText("No document open.");
            return;
        }
        status.setText("Validating…");
        FxtGui.executorService.submit(() -> {
            String report = SignatureActionRunner.describeSignature(xml);
            Platform.runLater(() -> {
                status.setText("Validation report opened");
                editorHost.openGeneratedDocument(report, EditorFileType.OTHER, "Signature-Report.txt");
            });
        });
    }

    /** Picks a trust store (.jks/.p12) whose trusted certificates are the validation anchors. */
    public void chooseTrustStore() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select trust store");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Key/Trust store", "*.jks", "*.p12", "*.pfx", "*.keystore"));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            trustStoreFile = file;
            trustStoreName.setText(file.getName());
        }
    }

    /**
     * Validates the active signed document's certificate chain against the trust store (PKIX:
     * chain + trust anchor + optional revocation + timestamp presence) and opens the report.
     */
    public void validateTrustActive() {
        File xml = activeXmlFile();
        if (xml == null) {
            status.setText("No document open.");
            return;
        }
        File store = trustStoreFile;
        boolean revocation = checkRevocation.isSelected();
        char[] storePassword = keystorePassword.getText().isBlank() ? null : keystorePassword.getText().toCharArray();
        status.setText("Validating trust…");
        FxtGui.executorService.submit(() -> {
            String report;
            try {
                java.security.KeyStore trustStore = loadTrustStore(store, storePassword);
                SignatureTrustValidator.TrustResult result =
                        SignatureTrustValidator.validate(xml, trustStore, revocation);
                report = "Trust validation of " + xml.getName() + "\n\n" + result.report();
            } catch (Exception e) {
                report = "ERROR: " + e.getMessage();
            }
            String finalReport = report;
            Platform.runLater(() -> {
                status.setText("Trust report opened");
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

    private void chooseKeystore() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Keystore");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Java KeyStore", "*.jks", "*.keystore"));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            setKeystore(file);
        }
    }

    /**
     * A nav row: icon + label, full width; selecting it shows the matching section.
     * Clicking "Sign XML File" additionally opens the sign card in the editor area.
     */
    private ToggleButton navItem(Action action, String text, String iconLiteral, ToggleGroup group) {
        ToggleButton item = new ToggleButton(text, icon(iconLiteral, 15));
        item.setId("sig-nav-" + action.name().toLowerCase());
        item.getStyleClass().add("fxt-sig-nav");
        item.setToggleGroup(group);
        item.setMaxWidth(Double.MAX_VALUE);
        item.setAlignment(Pos.CENTER_LEFT);
        item.setOnAction(e -> {
            selectAction(action);
            if (action == Action.SIGN) {
                openSignCard();
            }
        });
        nav.put(action, item);
        return item;
    }

    /** Wraps an action's form nodes in its toggleable section box. */
    private VBox section(Action action, String id, Node... children) {
        VBox box = new VBox(children);
        box.setId(id);
        sections.put(action, box);
        return box;
    }

    /** A source row: file-type icon · name · "Change" link (shared mockup style). */
    private HBox sourceRow(String iconLiteral, Label nameLabel, Runnable changeAction) {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Hyperlink change = new Hyperlink("Change");
        change.getStyleClass().add("fxt-vp-change");
        change.setOnAction(e -> changeAction.run());
        HBox row = new HBox(8, icon(iconLiteral, 15), nameLabel, spacer, change);
        row.getStyleClass().add("fxt-vp-source-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** Sets a source-row name, toggling the muted "none" style. */
    private static void setSourceName(Label label, String name) {
        label.setText(name != null ? name : "none");
        label.getStyleClass().remove("fxt-vp-source-none");
        if (name == null) {
            label.getStyleClass().add("fxt-vp-source-none");
        }
    }

    private Button primaryButton(String text, String iconLiteral, String id, Runnable action) {
        Button button = new Button(text, icon(iconLiteral, 14));
        button.setId(id);
        button.getStyleClass().add("fxt-primary-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(e -> action.run());
        return button;
    }

    private Button toolButton(String text, String iconLiteral, Runnable action) {
        Button button = new Button(text, icon(iconLiteral, 16));
        button.getStyleClass().add("fxt-tool-button");
        button.setOnAction(e -> action.run());
        return button;
    }

    private static VBox runBox(Node... children) {
        VBox box = new VBox(8, children);
        box.getStyleClass().add("fxt-vp-run-box");
        return box;
    }

    private static Label fieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("fxt-sig-field-label");
        return label;
    }

    private static IconifyIcon icon(String literal, int size) {
        IconifyIcon icon = new IconifyIcon(literal);
        icon.setIconSize(size);
        return icon;
    }
}
