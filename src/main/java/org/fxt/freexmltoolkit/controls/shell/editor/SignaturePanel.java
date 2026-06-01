package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.service.SignatureService;

/**
 * The Signature activity side panel: signs the active XML with a JKS keystore
 * (enveloped XML-DSig) and validates a signed document. Reuses
 * {@link SignatureService}; signing/validation run off the UI thread.
 * <p>
 * Also offers self-signed certificate / keystore creation and a detailed
 * validation report (validity + signing-certificate details), via
 * {@link SignatureActionRunner}. Certificate-chain/trust/revocation/timestamp
 * checks are out of scope (require a trust store / online checks).
 */
public class SignaturePanel extends VBox {

    private final EditorHost editorHost;
    private final TextField alias = new TextField();
    private final PasswordField keystorePassword = new PasswordField();
    private final PasswordField aliasPassword = new PasswordField();
    private final Label keystoreStatus = new Label("Keystore: none");
    private final Label status = new Label("Not signed/validated");
    private final TextField cnField = new TextField();
    private final TextField ouField = new TextField();
    private final TextField orgField = new TextField();
    private final TextField localityField = new TextField();
    private final TextField stateField = new TextField();
    private final TextField countryField = new TextField();
    private final TextField emailField = new TextField();
    private File keystoreFile;

    public SignaturePanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        getStyleClass().add("fxt-side-panel-content");

        Label title = new Label("SIGNATURE");
        title.getStyleClass().add("fxt-side-panel-title");

        Button chooseKeystore = button("Keystore…", "bi-key", this::chooseKeystore);
        keystoreStatus.getStyleClass().add("fxt-placeholder-text");
        alias.setPromptText("alias");
        keystorePassword.setPromptText("keystore password");
        aliasPassword.setPromptText("alias password");

        Button sign = button("Sign", "bi-shield-lock", this::signActive);
        Button validate = button("Validate", "bi-shield-check", this::validateActive);
        Button validateDetails = button("Validate (Details)", "bi-card-list", this::validateDetailsActive);

        status.getStyleClass().add("fxt-placeholder-text");
        status.setWrapText(true);

        getChildren().addAll(title, SidePanelLayout.fill(chooseKeystore), keystoreStatus,
                alias, keystorePassword, aliasPassword,
                SidePanelLayout.fill(sign), SidePanelLayout.fill(validate),
                SidePanelLayout.fill(validateDetails),
                status, buildCreateCertificateSection());
    }

    /** Expert section: create a self-signed certificate / JKS keystore (collapsed by default). */
    private TitledPane buildCreateCertificateSection() {
        cnField.setPromptText("Common Name (CN)");
        orgField.setPromptText("Organization (O)");
        ouField.setPromptText("Organizational Unit (OU)");
        localityField.setPromptText("Locality (L)");
        stateField.setPromptText("State (ST)");
        countryField.setPromptText("Country (C)");
        emailField.setPromptText("Email");
        Button create = button("Create Certificate", "bi-patch-plus", this::createCertificate);
        VBox box = new VBox(6, new Label("Uses the alias + passwords above."),
                cnField, orgField, ouField, localityField, stateField, countryField, emailField, create);
        TitledPane pane = new TitledPane("Create Self-Signed Certificate", box);
        pane.setExpanded(false);
        return pane;
    }

    /**
     * Creates a self-signed certificate / JKS keystore from the DN fields and the
     * alias + passwords above (async). On success the new keystore is selected so
     * the document can be signed with it immediately.
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
        keystoreStatus.setText(file != null ? "Keystore: " + file.getName() : "Keystore: none");
    }

    /** Sets the alias and passwords (for tests/observers). */
    public void setCredentials(String aliasName, String keystorePw, String aliasPw) {
        alias.setText(aliasName);
        keystorePassword.setText(keystorePw);
        aliasPassword.setText(aliasPw);
    }

    /** Signs the active XML to {@code <name>.signed.xml} next to it (async). */
    public void signActive() {
        File xml = activeXmlFile();
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
        File output = siblingSignedFile(xml);
        status.setText("Signing…");
        FxtGui.executorService.submit(() -> {
            String result;
            try {
                File signed = new SignatureService()
                        .signDocument(xml, keystore, ksPw, aliasName, aliasPw, output.getAbsolutePath());
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

    /** @return the current status text (for tests/observers). */
    public String getStatusText() {
        return status.getText();
    }

    private File activeXmlFile() {
        var doc = editorHost.getActiveDocument();
        return (doc.isPresent() && doc.get().getPath() != null) ? doc.get().getPath().toFile() : null;
    }

    private File siblingSignedFile(File xml) {
        String name = xml.getName().replaceFirst("\\.xml$", "") + ".signed.xml";
        return new File(xml.getParentFile(), name);
    }

    private void chooseKeystore() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Keystore");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Java KeyStore", "*.jks", "*.keystore"));
        File file = chooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            setKeystore(file);
        }
    }

    private Button button(String text, String icon, Runnable action) {
        IconifyIcon graphic = new IconifyIcon(icon);
        graphic.setIconSize(16);
        Button button = new Button(text, graphic);
        button.getStyleClass().add("fxt-tool-button");
        button.setOnAction(e -> action.run());
        return button;
    }
}
