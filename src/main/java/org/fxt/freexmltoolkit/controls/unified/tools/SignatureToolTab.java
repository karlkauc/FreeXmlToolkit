package org.fxt.freexmltoolkit.controls.unified.tools;

import java.io.File;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.fxt.freexmltoolkit.controls.unified.AbstractToolTab;
import org.fxt.freexmltoolkit.service.SignatureService;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Tool tab for XML digital signatures - create certificates, sign, and validate.
 */
public class SignatureToolTab extends AbstractToolTab {

    private static final Logger logger = LogManager.getLogger(SignatureToolTab.class);

    private final SignatureService signatureService;
    private final TextArea resultArea;

    public SignatureToolTab() {
        super("tool:signature", "Digital Signatures", "bi-shield-lock", "#28a745");

        this.signatureService = new SignatureService();
        this.resultArea = new TextArea();
        resultArea.setEditable(false);
        resultArea.setPromptText("Results will appear here...");
        resultArea.setStyle("-fx-font-family: monospace;");

        TabPane innerTabs = new TabPane();
        innerTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        innerTabs.getTabs().addAll(
                createCertificateTab(),
                createSignTab(),
                createValidateTab()
        );

        VBox mainBox = new VBox(8, innerTabs, new Label("Results:"), resultArea);
        mainBox.setPadding(new Insets(8));
        VBox.setVgrow(innerTabs, Priority.ALWAYS);
        resultArea.setPrefHeight(150);

        setContent(mainBox);
    }

    private Tab createCertificateTab() {
        Tab tab = new Tab("Create Certificate");
        FontIcon icon = new FontIcon("bi-award");
        icon.setIconSize(14);
        tab.setGraphic(icon);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(16));

        TextField cnField = new TextField();
        cnField.setPromptText("Common Name");
        TextField ouField = new TextField();
        ouField.setPromptText("Organizational Unit");
        TextField oField = new TextField();
        oField.setPromptText("Organization");
        TextField lField = new TextField();
        lField.setPromptText("Locality");
        TextField stField = new TextField();
        stField.setPromptText("State/Province");
        TextField cField = new TextField();
        cField.setPromptText("Country (2-letter)");
        TextField emailField = new TextField();
        emailField.setPromptText("Email");

        TextField aliasField = new TextField();
        aliasField.setPromptText("Keystore alias (required)");
        PasswordField ksPwdField = new PasswordField();
        ksPwdField.setPromptText("Keystore password (required)");
        PasswordField aliasPwdField = new PasswordField();
        aliasPwdField.setPromptText("Alias password (required)");

        int row = 0;
        grid.add(new Label("Common Name (CN):"), 0, row);
        grid.add(cnField, 1, row++);
        grid.add(new Label("Organization (O):"), 0, row);
        grid.add(oField, 1, row++);
        grid.add(new Label("Org. Unit (OU):"), 0, row);
        grid.add(ouField, 1, row++);
        grid.add(new Label("Locality (L):"), 0, row);
        grid.add(lField, 1, row++);
        grid.add(new Label("State (ST):"), 0, row);
        grid.add(stField, 1, row++);
        grid.add(new Label("Country (C):"), 0, row);
        grid.add(cField, 1, row++);
        grid.add(new Label("Email:"), 0, row);
        grid.add(emailField, 1, row++);
        grid.add(new javafx.scene.control.Separator(), 0, row++, 2, 1);
        grid.add(new Label("Alias:"), 0, row);
        grid.add(aliasField, 1, row++);
        grid.add(new Label("Keystore Pwd:"), 0, row);
        grid.add(ksPwdField, 1, row++);
        grid.add(new Label("Alias Pwd:"), 0, row);
        grid.add(aliasPwdField, 1, row++);

        Button createBtn = new Button("Create Certificate & Keystore");
        createBtn.setStyle("-fx-font-size: 14px;");
        createBtn.setOnAction(e -> {
            if (aliasField.getText().isEmpty() || ksPwdField.getText().isEmpty()) {
                showAlert("Alias and keystore password are required.");
                return;
            }
            try {
                X500NameBuilder nameBuilder = new X500NameBuilder();
                if (!cnField.getText().isEmpty()) nameBuilder.addRDN(BCStyle.CN, cnField.getText());
                if (!oField.getText().isEmpty()) nameBuilder.addRDN(BCStyle.O, oField.getText());
                if (!ouField.getText().isEmpty()) nameBuilder.addRDN(BCStyle.OU, ouField.getText());
                if (!lField.getText().isEmpty()) nameBuilder.addRDN(BCStyle.L, lField.getText());
                if (!stField.getText().isEmpty()) nameBuilder.addRDN(BCStyle.ST, stField.getText());
                if (!cField.getText().isEmpty()) nameBuilder.addRDN(BCStyle.C, cField.getText());
                if (!emailField.getText().isEmpty()) nameBuilder.addRDN(BCStyle.EmailAddress, emailField.getText());

                File ksFile = signatureService.createNewKeystoreFile(
                        nameBuilder, aliasField.getText(),
                        ksPwdField.getText(), aliasPwdField.getText());
                resultArea.setText("Certificate created successfully!\n\nKeystore: " + ksFile.getAbsolutePath());
            } catch (Exception ex) {
                resultArea.setText("Error: " + ex.getMessage());
                logger.error("Certificate creation failed", ex);
            }
        });

        grid.add(createBtn, 1, row);
        GridPane.setHgrow(cnField, Priority.ALWAYS);

        ScrollPane scrollPane = new ScrollPane(grid);
        scrollPane.setFitToWidth(true);
        tab.setContent(scrollPane);
        return tab;
    }

    private Tab createSignTab() {
        Tab tab = new Tab("Sign XML");
        FontIcon icon = new FontIcon("bi-pen");
        icon.setIconSize(14);
        tab.setGraphic(icon);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(16));

        TextField xmlField = new TextField();
        xmlField.setEditable(false);
        Button browseXml = new Button("Browse");
        TextField ksField = new TextField();
        ksField.setEditable(false);
        Button browseKs = new Button("Browse");
        TextField aliasField = new TextField();
        PasswordField ksPwdField = new PasswordField();
        PasswordField aliasPwdField = new PasswordField();
        TextField postfixField = new TextField("_signed");

        final File[] xmlFile = {null};
        final File[] ksFile = {null};

        browseXml.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select XML File");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML Files", "*.xml"));
            File f = fc.showOpenDialog(getContent().getScene().getWindow());
            if (f != null) { xmlFile[0] = f; xmlField.setText(f.getAbsolutePath()); }
        });

        browseKs.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select Keystore");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Keystore Files", "*.jks", "*.p12"));
            File f = fc.showOpenDialog(getContent().getScene().getWindow());
            if (f != null) { ksFile[0] = f; ksField.setText(f.getAbsolutePath()); }
        });

        int row = 0;
        grid.add(new Label("XML File:"), 0, row);
        grid.add(new HBox(4, xmlField, browseXml), 1, row++);
        grid.add(new Label("Keystore:"), 0, row);
        grid.add(new HBox(4, ksField, browseKs), 1, row++);
        grid.add(new Label("Alias:"), 0, row);
        grid.add(aliasField, 1, row++);
        grid.add(new Label("Keystore Pwd:"), 0, row);
        grid.add(ksPwdField, 1, row++);
        grid.add(new Label("Alias Pwd:"), 0, row);
        grid.add(aliasPwdField, 1, row++);
        grid.add(new Label("Output Postfix:"), 0, row);
        grid.add(postfixField, 1, row++);

        Button signBtn = new Button("Sign Document");
        signBtn.setStyle("-fx-font-size: 14px;");
        signBtn.setOnAction(e -> {
            if (xmlFile[0] == null || ksFile[0] == null) {
                showAlert("Please select both XML and keystore files.");
                return;
            }
            try {
                String outputName = xmlFile[0].getName().replaceFirst("\\.xml$", "") + postfixField.getText() + ".xml";
                File outputFile = new File(xmlFile[0].getParentFile(), outputName);
                signatureService.signDocument(xmlFile[0], ksFile[0],
                        ksPwdField.getText(), aliasField.getText(),
                        aliasPwdField.getText(), outputFile.getAbsolutePath());
                resultArea.setText("Document signed successfully!\n\nOutput: " + outputFile.getAbsolutePath());
            } catch (Exception ex) {
                resultArea.setText("Signing failed: " + ex.getMessage());
                logger.error("Signing failed", ex);
            }
        });

        grid.add(signBtn, 1, row);
        GridPane.setHgrow(xmlField, Priority.ALWAYS);

        ScrollPane scrollPane = new ScrollPane(grid);
        scrollPane.setFitToWidth(true);
        tab.setContent(scrollPane);
        return tab;
    }

    private Tab createValidateTab() {
        Tab tab = new Tab("Validate Signature");
        FontIcon icon = new FontIcon("bi-check-circle");
        icon.setIconSize(14);
        tab.setGraphic(icon);

        VBox box = new VBox(12);
        box.setPadding(new Insets(16));

        TextField fileField = new TextField();
        fileField.setEditable(false);
        Button browseBtn = new Button("Browse Signed XML");
        final File[] signedFile = {null};

        browseBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select Signed XML File");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML Files", "*.xml"));
            File f = fc.showOpenDialog(getContent().getScene().getWindow());
            if (f != null) { signedFile[0] = f; fileField.setText(f.getAbsolutePath()); }
        });

        HBox fileRow = new HBox(8, new Label("Signed XML:"), fileField, browseBtn);
        fileRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(fileField, Priority.ALWAYS);

        Button validateBtn = new Button("Validate Signature");
        validateBtn.setStyle("-fx-font-size: 14px;");
        validateBtn.setOnAction(e -> {
            if (signedFile[0] == null) {
                showAlert("Please select a signed XML file.");
                return;
            }
            try {
                boolean valid = signatureService.isSignatureValid(signedFile[0]);
                resultArea.setText(valid
                        ? "Signature is VALID.\n\nFile: " + signedFile[0].getAbsolutePath()
                        : "Signature is INVALID.\n\nFile: " + signedFile[0].getAbsolutePath());
            } catch (Exception ex) {
                resultArea.setText("Validation failed: " + ex.getMessage());
                logger.error("Validation failed", ex);
            }
        });

        box.getChildren().addAll(fileRow, validateBtn);
        tab.setContent(box);
        return tab;
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Digital Signatures");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
