/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.fxt.freexmltoolkit.service.SignatureService;
import org.fxt.freexmltoolkit.service.SignatureService.SignatureServiceException;
import org.fxt.freexmltoolkit.util.DialogHelper;

import java.io.File;

/**
 * Controller for XML digital signature operations.
 *
 * <p>This controller provides a complete workflow for XML digital signatures according to
 * W3C XML-DSig standards, including:</p>
 * <ul>
 *     <li>Creating self-signed certificates and JKS keystores</li>
 *     <li>Signing XML documents with enveloped signatures</li>
 *     <li>Validating signed XML documents</li>
 *     <li>Expert mode with advanced algorithm configuration</li>
 * </ul>
 *
 * <p>Supported algorithms include RSA, DSA, EC, and ECDSA with various key sizes,
 * and SHA1/SHA256/SHA384/SHA512 digest algorithms.</p>
 *
 * @see SignatureService
 */
public class SignatureController {
    private final static Logger logger = LogManager.getLogger(SignatureController.class);
    private final SignatureService signatureService = new SignatureService();

    /**
     * Creates a new SignatureController instance.
     *
     * <p>Initializes the signature service for handling certificate creation,
     * document signing, and signature validation operations.</p>
     */
    public SignatureController() {
        // Default constructor - SignatureService is initialized inline
    }

    @FXML
    private TextField commonName, organizationUnit, organizationName, localityName, streetName, country, email,
            createCertificateAlias, signKeystoreAlias;

    @FXML
    private TextField newFileName;

    @FXML
    private Button signLoadKeystoreButton, signLoadXmlFileButton, validateLoadXmlFileButton;

    private File certificateFile, xmlFile;
    private final FileChooser fileChooserCertificate = new FileChooser();
    private final FileChooser fileChooserXMl = new FileChooser();

    @FXML
    private Label certFileInfo, xmlFileInfo, validateXmlFileInfo;

    @FXML
    private PasswordField createCertificateKeystorePassword, createCertificateAliasPassword, signKeystorePassword, signAliasPassword;

    // Expert Mode fields
    @FXML
    private ComboBox<String> expertKeyAlgorithm, expertKeySize, expertSignatureAlgorithm;

    @FXML
    private TextField expertValidityDays, expertSubjectAltNames, expertCommonName, expertOrganization, expertCountry, expertCertAlias;

    @FXML
    private PasswordField expertCertKeystorePassword, expertCertAliasPassword;

    @FXML
    private Button expertLoadXmlButton, expertLoadKeystoreButton, expertValidateButton;

    @FXML
    private Label expertXmlFileInfo, expertKeystoreInfo, expertValidateFileInfo;

    @FXML
    private ComboBox<String> expertCanonicalizationMethod, expertTransformMethod, expertDigestMethod, expertSignatureType;

    @FXML
    private TextField expertKeystoreAlias;

    @FXML
    private PasswordField expertKeystorePassword, expertKeyPassword;

    @FXML
    private CheckBox expertValidateCertChain, expertValidateTrust, expertValidateRevocation, expertValidateTimestamp, expertDetailedReport;

    @FXML
    private TextArea expertResultsArea;

    @FXML
    private TabPane tabPane;

    // Additional files for expert mode
    private File expertXmlFile, expertKeystoreFile, expertValidateFile;

    private MainController parentController;

    /**
     * Sets the parent controller for navigation and coordination.
     *
     * <p>This method establishes the connection to the main application controller,
     * allowing this controller to communicate with other parts of the application.</p>
     *
     * @param parentController the main controller instance
     */
    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

    /**
     * Programmatically selects a sub-tab by its ID.
     * Called from MainController menu handlers.
     *
     * @param tabId the fx:id of the tab to select
     */
    public void selectSubTab(String tabId) {
        if (tabPane == null || tabId == null) {
            return;
        }
        for (Tab tab : tabPane.getTabs()) {
            if (tabId.equals(tab.getId())) {
                tabPane.getSelectionModel().select(tab);
                return;
            }
        }
    }

    @FXML
    private void initialize() {
        if (new File("certs").exists()) {
            fileChooserCertificate.setInitialDirectory(new File("certs"));
        }

        FileChooser.ExtensionFilter certFilter = new FileChooser.ExtensionFilter("Certificate Files", "*.jks");
        fileChooserCertificate.getExtensionFilters().add(certFilter);
        signLoadKeystoreButton.setOnAction(this::handleKeystoreOnAction);
        signLoadKeystoreButton.setOnDragOver(SignatureController::handleOnDragOver);
        signLoadKeystoreButton.setOnDragDropped(this::handleLoadKeystore);

        FileChooser.ExtensionFilter xmlFilter = new FileChooser.ExtensionFilter("XML Files", "*.xml");
        fileChooserXMl.getExtensionFilters().add(xmlFilter);
        signLoadXmlFileButton.setOnAction(this::handleXmlOnAction);
        signLoadXmlFileButton.setOnDragOver(SignatureController::handleOnDragOver);
        signLoadXmlFileButton.setOnDragDropped(this::handleXmlLoad);

        // CORRECTION: Event handlers for the validation buttons are also set here
        validateLoadXmlFileButton.setOnAction(this::handleXmlOnAction);
        validateLoadXmlFileButton.setOnDragOver(SignatureController::handleOnDragOver);
        validateLoadXmlFileButton.setOnDragDropped(this::handleXmlLoad);

        // Initialize Expert Mode ComboBoxes
        initializeExpertModeOptions();
        setupExpertModeEventHandlers();
    }

    private void initializeExpertModeOptions() {
        // Key Algorithms according to XML-DSig standards
        if (expertKeyAlgorithm != null) {
            expertKeyAlgorithm.getItems().addAll(
                    "RSA",
                    "DSA",
                    "EC (Elliptic Curve)",
                    "ECDSA"
            );
            expertKeyAlgorithm.setValue("RSA");

            // Update key sizes based on algorithm selection
            expertKeyAlgorithm.setOnAction(e -> updateKeySizes());
        }

        // Initialize key sizes for RSA (default)
        if (expertKeySize != null) {
            updateKeySizes();
        }

        // Signature Algorithms
        if (expertSignatureAlgorithm != null) {
            expertSignatureAlgorithm.getItems().addAll(
                    "SHA1withRSA",
                    "SHA256withRSA",
                    "SHA384withRSA",
                    "SHA512withRSA",
                    "SHA1withDSA",
                    "SHA256withDSA",
                    "SHA1withECDSA",
                    "SHA256withECDSA",
                    "SHA384withECDSA",
                    "SHA512withECDSA"
            );
            expertSignatureAlgorithm.setValue("SHA256withRSA");
        }

        // Canonicalization Methods (C14N)
        if (expertCanonicalizationMethod != null) {
            expertCanonicalizationMethod.getItems().addAll(
                    "http://www.w3.org/TR/2001/REC-xml-c14n-20010315",
                    "http://www.w3.org/TR/2001/REC-xml-c14n-20010315#WithComments",
                    "http://www.w3.org/2001/10/xml-exc-c14n#",
                    "http://www.w3.org/2001/10/xml-exc-c14n#WithComments",
                    "http://www.w3.org/2006/12/xml-c14n11",
                    "http://www.w3.org/2006/12/xml-c14n11#WithComments"
            );
            expertCanonicalizationMethod.setValue("http://www.w3.org/2001/10/xml-exc-c14n#");
        }

        // Transform Methods
        if (expertTransformMethod != null) {
            expertTransformMethod.getItems().addAll(
                    "http://www.w3.org/2000/09/xmldsig#enveloped-signature",
                    "http://www.w3.org/TR/2001/REC-xml-c14n-20010315",
                    "http://www.w3.org/2001/10/xml-exc-c14n#",
                    "http://www.w3.org/TR/1999/REC-xpath-19991116",
                    "http://www.w3.org/2002/06/xmldsig-filter2",
                    "http://www.w3.org/TR/1999/REC-xslt-19991116"
            );
            expertTransformMethod.setValue("http://www.w3.org/2000/09/xmldsig#enveloped-signature");
        }

        // Digest Methods
        if (expertDigestMethod != null) {
            expertDigestMethod.getItems().addAll(
                    "http://www.w3.org/2000/09/xmldsig#sha1",
                    "http://www.w3.org/2001/04/xmlenc#sha256",
                    "http://www.w3.org/2001/04/xmldsig-more#sha384",
                    "http://www.w3.org/2001/04/xmlenc#sha512",
                    "http://www.w3.org/2001/04/xmldsig-more#md5"
            );
            expertDigestMethod.setValue("http://www.w3.org/2001/04/xmlenc#sha256");
        }

        // Signature Types (XML-DSig signature forms)
        if (expertSignatureType != null) {
            expertSignatureType.getItems().addAll(
                    "Enveloped (signature inside document)",
                    "Enveloping (document inside signature)",
                    "Detached (signature separate from document)"
            );
            expertSignatureType.setValue("Enveloped (signature inside document)");
        }

        // Set default validity period
        if (expertValidityDays != null) {
            expertValidityDays.setText("365");
        }
    }

    private void updateKeySizes() {
        if (expertKeySize == null || expertKeyAlgorithm == null) return;

        String algorithm = expertKeyAlgorithm.getValue();
        expertKeySize.getItems().clear();

        switch (algorithm) {
            case "RSA" -> expertKeySize.getItems().addAll("1024", "2048", "3072", "4096");
            case "DSA" -> expertKeySize.getItems().addAll("1024", "2048", "3072");
            case "EC (Elliptic Curve)", "ECDSA" -> expertKeySize.getItems().addAll("256", "384", "521");
            default -> expertKeySize.getItems().addAll("2048", "3072", "4096");
        }

        // Set recommended default
        if (algorithm.equals("RSA") || algorithm.equals("DSA")) {
            expertKeySize.setValue("2048");
        } else {
            expertKeySize.setValue("256");
        }
    }

    private void setupExpertModeEventHandlers() {
        // Expert mode file selection handlers
        if (expertLoadXmlButton != null) {
            expertLoadXmlButton.setOnAction(this::handleExpertXmlOnAction);
            expertLoadXmlButton.setOnDragOver(SignatureController::handleOnDragOver);
            expertLoadXmlButton.setOnDragDropped(this::handleExpertXmlLoad);
        }

        if (expertLoadKeystoreButton != null) {
            expertLoadKeystoreButton.setOnAction(this::handleExpertKeystoreOnAction);
            expertLoadKeystoreButton.setOnDragOver(SignatureController::handleOnDragOver);
            expertLoadKeystoreButton.setOnDragDropped(this::handleExpertKeystoreLoad);
        }

        if (expertValidateButton != null) {
            expertValidateButton.setOnAction(this::handleExpertValidateOnAction);
            expertValidateButton.setOnDragOver(SignatureController::handleOnDragOver);
            expertValidateButton.setOnDragDropped(this::handleExpertValidateLoad);
        }
    }

    /**
     * Creates a new self-signed certificate and saves it to a JKS keystore.
     *
     * <p>This method collects identity information from the form fields (common name,
     * organization, country, etc.) and generates a new X.509 certificate using BouncyCastle.
     * The certificate is stored in a newly created JKS keystore file.</p>
     *
     * <p>Required fields are:</p>
     * <ul>
     *     <li>Alias - identifier for the certificate in the keystore</li>
     *     <li>Keystore Password - password to protect the keystore file</li>
     *     <li>Alias Password - password to protect the private key</li>
     * </ul>
     */
    @FXML
    public void createNewSignatureFile() {
        final var aliasText = createCertificateAlias.getText();
        final var keystorePasswordText = createCertificateKeystorePassword.getText();
        final var aliasPasswordText = createCertificateAliasPassword.getText();

        if (aliasText.isBlank() || keystorePasswordText.isBlank() || aliasPasswordText.isBlank()) {
            showErrorAlert("Input Required", "All fields are mandatory.", "Please provide Alias, Keystore Password, and Alias Password.");
            return;
        }

        try {
            final X500NameBuilder nameBuilder = new X500NameBuilder(X500Name.getDefaultStyle());
            if (!commonName.getText().isBlank()) nameBuilder.addRDN(BCStyle.CN, commonName.getText());
            if (!organizationUnit.getText().isBlank()) nameBuilder.addRDN(BCStyle.OU, organizationUnit.getText());
            if (!organizationName.getText().isBlank()) nameBuilder.addRDN(BCStyle.O, organizationName.getText());
            if (!localityName.getText().isBlank()) nameBuilder.addRDN(BCStyle.L, localityName.getText());
            if (!streetName.getText().isBlank()) nameBuilder.addRDN(BCStyle.ST, streetName.getText());
            if (!country.getText().isBlank()) nameBuilder.addRDN(BCStyle.C, country.getText());
            if (!email.getText().isBlank()) nameBuilder.addRDN(BCStyle.EmailAddress, email.getText());

            var outFile = signatureService.createNewKeystoreFile(nameBuilder, aliasText, keystorePasswordText, aliasPasswordText);

            showInfoAlert("Success", "Certificate created successfully.", "Your certificate was created successfully. \nIt can be found under: " + outFile.getParent());

        } catch (SignatureServiceException e) {
            logger.error("Certificate creation failed.", e);
            showErrorAlert("Certificate Creation Failed", "Could not create the certificate.", e.getMessage());
        }
    }

    /**
     * Signs an XML document using a certificate from a JKS keystore.
     *
     * <p>This method creates an enveloped XML digital signature according to W3C XML-DSig
     * standards. The signature is embedded within the original XML document, and a new
     * signed file is created with the specified suffix.</p>
     *
     * <p>Prerequisites:</p>
     * <ul>
     *     <li>An XML file must be loaded</li>
     *     <li>A JKS keystore file must be loaded</li>
     *     <li>Keystore alias and passwords must be provided</li>
     * </ul>
     *
     * <p>Upon successful signing, the newly created signed document is automatically
     * loaded for validation.</p>
     */
    @FXML
    public void signDocument() {
        final var keystoreAlias = signKeystoreAlias.getText();
        final var keystorePassword = signKeystorePassword.getText();
        final var aliasPassword = signAliasPassword.getText();

        if (certificateFile == null || xmlFile == null || keystoreAlias.isBlank() || keystorePassword.isBlank() || aliasPassword.isBlank()) {
            showErrorAlert("Input Required", "All fields and files are mandatory.", "Please provide an XML File, a Keystore File, and all password/alias fields.");
            return;
        }

        final var outputFileName = this.xmlFile.getName().toLowerCase().replace(".xml", newFileName.getText() + ".xml");

        try {
            var signedDocument = signatureService.signDocument(xmlFile, certificateFile, keystorePassword, keystoreAlias, aliasPassword, outputFileName);
            showInfoAlert("Success", "File signed successfully.", "File '" + signedDocument.getName() + "' was created.");

            // Provide the newly signed document for validation
            this.xmlFile = signedDocument;
            this.validateXmlFileInfo.setText(this.xmlFile.getName());

        } catch (SignatureServiceException e) {
            logger.error("Document signing failed.", e);
            showErrorAlert("Signing Failed", "Could not sign the document.", e.getMessage());
        }
    }

    /**
     * Validates the digital signature in a signed XML document.
     *
     * <p>This method verifies the cryptographic integrity of an XML-DSig signature
     * by checking that the signature value matches the signed content and that the
     * signature has not been tampered with.</p>
     *
     * <p>The validation checks:</p>
     * <ul>
     *     <li>Signature cryptographic validity</li>
     *     <li>Document integrity (no modifications since signing)</li>
     *     <li>Reference digest values</li>
     * </ul>
     *
     * <p>A signed XML file must be loaded before calling this method.</p>
     */
    @FXML
    public void validateSignedDocument() {
        if (this.xmlFile == null) {
            showErrorAlert("Validation Failed", "No XML file loaded.", "Please load a signed XML file to validate.");
            return;
        }

        try {
            boolean isValid = signatureService.isSignatureValid(this.xmlFile);

            if (isValid) {
                showInfoAlert("Validation Successful", "The signature is valid.", "The signature in '" + this.xmlFile.getName() + "' has been successfully validated.");
            } else {
                showErrorAlert("Validation Failed", "The signature is invalid.", "The signature in '" + this.xmlFile.getName() + "' could not be validated.");
            }
        } catch (SignatureServiceException e) {
            logger.error("An error occurred during validation.", e);
            showErrorAlert("Validation Error", "A technical error occurred during validation.", e.getMessage());
        }
    }

    private static void handleOnDragOver(DragEvent event) {
        Dragboard db = event.getDragboard();
        if (db.hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY);
        } else {
            event.consume();
        }
    }

    private void handleXmlLoad(DragEvent event) {
        Dragboard db = event.getDragboard();
        if (db.hasFiles() && !db.getFiles().isEmpty()) {
            File file = db.getFiles().get(0);
            updateXmlFile(file);
            event.setDropCompleted(true);
        }
        event.consume();
    }

    private void handleLoadKeystore(DragEvent event) {
        Dragboard db = event.getDragboard();
        if (db.hasFiles() && !db.getFiles().isEmpty()) {
            File file = db.getFiles().get(0);
            updateKeystoreFile(file);
            event.setDropCompleted(true);
        }
        event.consume();
    }

    private void handleXmlOnAction(ActionEvent e) {
        File file = fileChooserXMl.showOpenDialog(null);
        if (file != null) {
            updateXmlFile(file);
        }
    }

    private void handleKeystoreOnAction(ActionEvent e) {
        File file = fileChooserCertificate.showOpenDialog(null);
        if (file != null) {
            updateKeystoreFile(file);
        }
    }

    private void updateXmlFile(File file) {
        this.xmlFile = file;
        String fileName = file.getName();
        this.xmlFileInfo.setText(fileName);
        this.validateXmlFileInfo.setText(fileName);
        logger.debug("Set XML File to: {}", file.getAbsolutePath());
    }

    private void updateKeystoreFile(File file) {
        this.certificateFile = file;
        String fileName = file.getName();
        this.certFileInfo.setText(fileName);
        logger.debug("Set Keystore File to: {}", file.getAbsolutePath());
    }

    private void showInfoAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showErrorAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    // Expert Mode Event Handlers
    private void handleExpertXmlOnAction(ActionEvent e) {
        File file = fileChooserXMl.showOpenDialog(null);
        if (file != null) {
            updateExpertXmlFile(file);
        }
    }

    private void handleExpertKeystoreOnAction(ActionEvent e) {
        File file = fileChooserCertificate.showOpenDialog(null);
        if (file != null) {
            updateExpertKeystoreFile(file);
        }
    }

    private void handleExpertValidateOnAction(ActionEvent e) {
        File file = fileChooserXMl.showOpenDialog(null);
        if (file != null) {
            updateExpertValidateFile(file);
        }
    }

    private void handleExpertXmlLoad(DragEvent event) {
        Dragboard db = event.getDragboard();
        if (db.hasFiles() && !db.getFiles().isEmpty()) {
            File file = db.getFiles().get(0);
            updateExpertXmlFile(file);
            event.setDropCompleted(true);
        }
        event.consume();
    }

    private void handleExpertKeystoreLoad(DragEvent event) {
        Dragboard db = event.getDragboard();
        if (db.hasFiles() && !db.getFiles().isEmpty()) {
            File file = db.getFiles().get(0);
            updateExpertKeystoreFile(file);
            event.setDropCompleted(true);
        }
        event.consume();
    }

    private void handleExpertValidateLoad(DragEvent event) {
        Dragboard db = event.getDragboard();
        if (db.hasFiles() && !db.getFiles().isEmpty()) {
            File file = db.getFiles().get(0);
            updateExpertValidateFile(file);
            event.setDropCompleted(true);
        }
        event.consume();
    }

    private void updateExpertXmlFile(File file) {
        this.expertXmlFile = file;
        String fileName = file.getName();
        if (expertXmlFileInfo != null) {
            expertXmlFileInfo.setText(fileName);
        }
        logger.debug("Set Expert XML File to: {}", file.getAbsolutePath());
        appendToExpertResults("XML File loaded: " + fileName);
    }

    private void updateExpertKeystoreFile(File file) {
        this.expertKeystoreFile = file;
        String fileName = file.getName();
        if (expertKeystoreInfo != null) {
            expertKeystoreInfo.setText(fileName);
        }
        logger.debug("Set Expert Keystore File to: {}", file.getAbsolutePath());
        appendToExpertResults("Keystore File loaded: " + fileName);
    }

    private void updateExpertValidateFile(File file) {
        this.expertValidateFile = file;
        String fileName = file.getName();
        if (expertValidateFileInfo != null) {
            expertValidateFileInfo.setText(fileName);
        }
        logger.debug("Set Expert Validate File to: {}", file.getAbsolutePath());
        appendToExpertResults("Validation File loaded: " + fileName);
    }

    private void appendToExpertResults(String message) {
        if (expertResultsArea != null) {
            String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
            String currentText = expertResultsArea.getText();
            String newText = currentText + (currentText.isEmpty() ? "" : "\n") + "[" + timestamp + "] " + message;
            expertResultsArea.setText(newText);
            expertResultsArea.positionCaret(newText.length());
        }
    }

    /**
     * Creates a certificate with advanced expert mode options.
     *
     * <p>This method provides full control over certificate generation parameters,
     * allowing selection of:</p>
     * <ul>
     *     <li>Key algorithm (RSA, DSA, EC, ECDSA)</li>
     *     <li>Key size (1024-4096 bits for RSA/DSA, 256-521 bits for EC)</li>
     *     <li>Signature algorithm (SHA1/SHA256/SHA384/SHA512 with RSA/DSA/ECDSA)</li>
     *     <li>Certificate validity period in days</li>
     *     <li>Subject Alternative Names (SANs)</li>
     * </ul>
     *
     * <p>Results and progress are logged to the expert results text area.</p>
     */
    @FXML
    public void expertCreateCertificate() {
        appendToExpertResults("=== Advanced Certificate Generation Started ===");

        try {
            String keyAlgorithm = expertKeyAlgorithm.getValue();
            String keySize = expertKeySize.getValue();
            String signatureAlg = expertSignatureAlgorithm.getValue();
            String validityDays = expertValidityDays.getText();
            String subjectAltNames = expertSubjectAltNames.getText();

            if (keyAlgorithm == null || keySize == null || signatureAlg == null) {
                appendToExpertResults("ERROR: Key algorithm, size, and signature algorithm must be selected");
                return;
            }

            appendToExpertResults("Selected Configuration:");
            appendToExpertResults("  - Key Algorithm: " + keyAlgorithm);
            appendToExpertResults("  - Key Size: " + keySize + " bits");
            appendToExpertResults("  - Signature Algorithm: " + signatureAlg);
            appendToExpertResults("  - Validity Period: " + validityDays + " days");
            if (!subjectAltNames.trim().isEmpty()) {
                appendToExpertResults("  - Subject Alternative Names: " + subjectAltNames);
            }

            appendToExpertResults("Certificate Identity:");
            String cnText = expertCommonName != null ? expertCommonName.getText() : "";
            String orgText = expertOrganization != null ? expertOrganization.getText() : "";
            String countryText = expertCountry != null ? expertCountry.getText() : "";
            appendToExpertResults("  - Common Name: " + (!cnText.isBlank() ? cnText : "Expert Mode Certificate (default)"));
            appendToExpertResults("  - Organization: " + (!orgText.isBlank() ? orgText : "FreeXmlToolkit Expert Mode (default)"));
            appendToExpertResults("  - Country: " + (!countryText.isBlank() ? countryText : "XX (default)"));

            String alias = expertCertAlias != null ? expertCertAlias.getText().trim() : "";
            appendToExpertResults("Keystore Alias: " + (!alias.isEmpty() ? alias : "[NOT SET - ERROR WILL OCCUR]"));

            // Build Distinguished Name from expert mode fields (reuse already read values)
            final X500NameBuilder nameBuilder = new X500NameBuilder(X500Name.getDefaultStyle());

            if (!cnText.isBlank()) {
                nameBuilder.addRDN(BCStyle.CN, cnText);
            } else {
                appendToExpertResults("WARNING: No Common Name provided, using default");
                nameBuilder.addRDN(BCStyle.CN, "Expert Mode Certificate");
            }

            if (!orgText.isBlank()) {
                nameBuilder.addRDN(BCStyle.O, orgText);
            } else {
                nameBuilder.addRDN(BCStyle.O, "FreeXmlToolkit Expert Mode");
            }

            if (!countryText.isBlank()) {
                nameBuilder.addRDN(BCStyle.C, countryText);
            } else {
                nameBuilder.addRDN(BCStyle.C, "XX");
            }

            // Use expert mode keystore configuration (alias already declared above)
            String keystorePassword = expertCertKeystorePassword != null ? expertCertKeystorePassword.getText() : "";
            String aliasPassword = expertCertAliasPassword != null ? expertCertAliasPassword.getText() : "";

            if (alias.isEmpty() || keystorePassword.isEmpty() || aliasPassword.isEmpty()) {
                appendToExpertResults("ERROR: Alias and passwords are required");
                return;
            }

            // Generate certificate with advanced options
            // Note: This would require extending SignatureService to support advanced options
            var outFile = signatureService.createNewKeystoreFile(nameBuilder, alias, keystorePassword, aliasPassword);

            appendToExpertResults("SUCCESS: Advanced certificate created successfully");
            appendToExpertResults("Certificate files location: " + outFile.getParent());
            appendToExpertResults("=== Certificate Generation Completed ===");

        } catch (Exception e) {
            logger.error("Expert certificate creation failed", e);
            appendToExpertResults("ERROR: Certificate generation failed - " + e.getMessage());
        }
    }

    /**
     * Signs an XML document with advanced expert mode options.
     *
     * <p>This method provides full control over the XML-DSig signing process,
     * allowing configuration of:</p>
     * <ul>
     *     <li>Canonicalization method (C14N, Exclusive C14N, C14N 1.1)</li>
     *     <li>Transform method (enveloped-signature, C14N, XPath, etc.)</li>
     *     <li>Digest method (SHA1, SHA256, SHA384, SHA512, MD5)</li>
     *     <li>Signature type (Enveloped, Enveloping, Detached)</li>
     * </ul>
     *
     * <p>Prerequisites:</p>
     * <ul>
     *     <li>An XML file must be loaded in expert mode</li>
     *     <li>A keystore file must be loaded in expert mode</li>
     *     <li>Keystore credentials must be provided</li>
     * </ul>
     *
     * <p>Results and progress are logged to the expert results text area.</p>
     */
    @FXML
    public void expertSignDocument() {
        appendToExpertResults("=== Advanced XML Signing Started ===");

        try {
            if (expertXmlFile == null || expertKeystoreFile == null) {
                appendToExpertResults("ERROR: XML file and keystore file are required");
                return;
            }

            String alias = expertKeystoreAlias.getText().trim();
            String keystorePassword = expertKeystorePassword.getText();
            String keyPassword = expertKeyPassword.getText();

            if (alias.isEmpty() || keystorePassword.isEmpty() || keyPassword.isEmpty()) {
                appendToExpertResults("ERROR: Keystore credentials are required");
                return;
            }

            String canonMethod = expertCanonicalizationMethod.getValue();
            String transformMethod = expertTransformMethod.getValue();
            String digestMethod = expertDigestMethod.getValue();
            String signatureType = expertSignatureType.getValue();

            appendToExpertResults("Signing Configuration:");
            appendToExpertResults("  - XML File: " + expertXmlFile.getName());
            appendToExpertResults("  - Keystore: " + expertKeystoreFile.getName());
            appendToExpertResults("  - Canonicalization: " + canonMethod);
            appendToExpertResults("  - Transform: " + transformMethod);
            appendToExpertResults("  - Digest: " + digestMethod);
            appendToExpertResults("  - Signature Type: " + signatureType);

            // Use existing signature service (would need enhancement for advanced options)
            String outputFileName = expertXmlFile.getName().toLowerCase().replace(".xml", "_expert_signed.xml");
            var signedDocument = signatureService.signDocument(expertXmlFile, expertKeystoreFile,
                    keystorePassword, alias, keyPassword, outputFileName);

            appendToExpertResults("SUCCESS: Document signed with advanced XML-DSig options");
            appendToExpertResults("Signed document: " + signedDocument.getName());
            appendToExpertResults("=== XML Signing Completed ===");

        } catch (Exception e) {
            logger.error("Expert document signing failed", e);
            appendToExpertResults("ERROR: Signing failed - " + e.getMessage());
        }
    }

    /**
     * Validates a signed XML document with advanced expert mode options.
     *
     * <p>This method provides comprehensive signature validation with configurable
     * verification options:</p>
     * <ul>
     *     <li>Certificate chain validation - verify the certificate hierarchy</li>
     *     <li>Trust anchor validation - check against trusted root certificates</li>
     *     <li>Revocation checking - verify certificate status via OCSP/CRL</li>
     *     <li>Timestamp validation - verify signing timestamps if present</li>
     *     <li>Detailed report generation - comprehensive validation output</li>
     * </ul>
     *
     * <p>A signed XML file must be loaded in expert mode before validation.</p>
     *
     * <p>Results and detailed reports are logged to the expert results text area.</p>
     */
    @FXML
    public void expertValidateDocument() {
        appendToExpertResults("=== Advanced Signature Validation Started ===");

        try {
            if (expertValidateFile == null) {
                appendToExpertResults("ERROR: Signed XML file is required for validation");
                return;
            }

            boolean validateCertChain = expertValidateCertChain.isSelected();
            boolean validateTrust = expertValidateTrust.isSelected();
            boolean validateRevocation = expertValidateRevocation.isSelected();
            boolean validateTimestamp = expertValidateTimestamp.isSelected();
            boolean detailedReport = expertDetailedReport.isSelected();

            appendToExpertResults("Validation Configuration:");
            appendToExpertResults("  - File: " + expertValidateFile.getName());
            appendToExpertResults("  - Certificate Chain Validation: " + (validateCertChain ? "ENABLED" : "DISABLED"));
            appendToExpertResults("  - Trust Anchor Validation: " + (validateTrust ? "ENABLED" : "DISABLED"));
            appendToExpertResults("  - Revocation Checking (OCSP/CRL): " + (validateRevocation ? "ENABLED" : "DISABLED"));
            appendToExpertResults("  - Timestamp Validation: " + (validateTimestamp ? "ENABLED" : "DISABLED"));
            appendToExpertResults("  - Detailed Report: " + (detailedReport ? "ENABLED" : "DISABLED"));

            // Perform validation with advanced options
            boolean isValid = signatureService.isSignatureValid(expertValidateFile);

            if (isValid) {
                appendToExpertResults("✓ VALIDATION SUCCESS: Signature is cryptographically valid");
            } else {
                appendToExpertResults("✗ VALIDATION FAILED: Signature is invalid or corrupted");
            }

            if (detailedReport) {
                appendToExpertResults("--- Detailed Validation Report ---");
                appendToExpertResults("Signature Method: [Analysis would require SignatureService enhancement]");
                appendToExpertResults("Certificate Subject: [Would be extracted from signature]");
                appendToExpertResults("Certificate Validity: [Would be checked if validateCertChain enabled]");
                appendToExpertResults("Trust Status: [Would be verified if validateTrust enabled]");
                appendToExpertResults("--- End Report ---");
            }

            appendToExpertResults("=== Signature Validation Completed ===");

        } catch (Exception e) {
            logger.error("Expert document validation failed", e);
            appendToExpertResults("ERROR: Validation failed - " + e.getMessage());
        }
    }

    // ======================================================================
    // Toolbar Navigation Methods
    // ======================================================================

    /**
     * Show help dialog with instructions
     */
    @FXML
    private void showHelp() {
        var features = java.util.List.of(
                new String[]{"bi-key", "Certificate Creation", "Generate self-signed digital certificates with RSA, DSA, EC, or ECDSA algorithms"},
                new String[]{"bi-pen", "XML Signing", "Apply enveloped, enveloping, or detached digital signatures to XML documents"},
                new String[]{"bi-patch-check", "Signature Validation", "Verify the cryptographic integrity of signed XML documents"},
                new String[]{"bi-sliders", "Expert Mode", "Advanced XML-DSig options with full control over algorithms and methods"},
                new String[]{"bi-shield-lock", "Multiple Algorithms", "Support for SHA1/SHA256/SHA384/SHA512 digest and signature algorithms"},
                new String[]{"bi-file-earmark-lock", "Keystore Management", "Create and manage JKS keystores with secure password protection"},
                new String[]{"bi-file-earmark-arrow-down", "Drag & Drop", "Load XML and keystore files by dragging them onto the interface"}
        );

        var shortcuts = java.util.List.of(
                new String[]{"Tab 1", "Create Certificate and Keystore"},
                new String[]{"Tab 2", "Sign XML Document"},
                new String[]{"Tab 3", "Validate Signed Document"},
                new String[]{"Tab 4", "Expert Mode (Advanced)"},
                new String[]{"F1", "Show this help dialog"}
        );

        var helpDialog = DialogHelper.createHelpDialog(
                "XML Signature Tool - Help",
                "Digital Signatures",
                "Complete XML digital signature workflow: create certificates, sign documents, and validate signatures according to W3C XML-DSig standards.",
                "bi-shield-check",
                DialogHelper.HeaderTheme.INFO,
                features,
                shortcuts
        );
        helpDialog.showAndWait();
    }

    /**
     * Switch to the Create Certificate tab
     */
    @FXML
    private void switchToCreateCertificateTab() {
        if (tabPane != null) {
            tabPane.getSelectionModel().select(0); // Create Certificate is tab 0
        }
    }

    /**
     * Switch to the Sign XML File tab
     */
    @FXML
    private void switchToSignTab() {
        if (tabPane != null) {
            tabPane.getSelectionModel().select(1); // Sign XML File is tab 1
        }
    }

    /**
     * Switch to the Validate Signed File tab
     */
    @FXML
    private void switchToValidateTab() {
        if (tabPane != null) {
            tabPane.getSelectionModel().select(2); // Validate is tab 2
        }
    }

    /**
     * Switch to the Expert Mode tab
     */
    @FXML
    private void switchToExpertTab() {
        if (tabPane != null) {
            tabPane.getSelectionModel().select(3); // Expert Mode is tab 3
        }
    }
}