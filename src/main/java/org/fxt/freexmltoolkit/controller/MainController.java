package org.fxt.freexmltoolkit.controller;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.JavaFXBuilderFactory;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.BuilderFactory;
import javafx.util.Callback;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.ModuleBindings;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.XmlService;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;

public class MainController {

    final Injector injector = Guice.createInjector(new ModuleBindings());
    BuilderFactory builderFactory = new JavaFXBuilderFactory();
    Callback<Class<?>, Object> guiceControllerFactory = injector::getInstance;

    @Inject
    PropertiesService propertiesService;

    @Inject
    XmlService xmlService;

    @FXML
    private Parent xml, xslt, xsd, fop, signature, xsdValidation, schematron;

    @FXML
    private XmlController xmlController;

    @FXML
    private SignatureController signatureController;

    @FXML
    private XsdValidationController xsdValidationController;

    @FXML
    private XsltController xsltController;

    @FXML
    private XsdController xsdController;

    @FXML
    private FopController fopController;

    @FXML
    Tab tabPaneXml, tabPaneXslt, tabPaneXsdValidation, tabPaneXsd, tabSignature, tabFop, tabSchematron;

    @FXML
    Button prettyPrint, newFile, saveFile, exit, about;

    FileChooser fileChooser = new FileChooser();

    @FXML
    VBox mainBox;

    @FXML
    TabPane tabPane;

    private final static Logger logger = LogManager.getLogger(MainController.class);

    public String lastOpenDir;

    @FXML
    private void openHelpPage() {
        try {
            Desktop.getDesktop().browse(new URI("https://www.google.at"));
        } catch (IOException | URISyntaxException ex) {
            ex.printStackTrace();
        }
    }

    @FXML
    private void openSetting() {
        logger.debug("Open Settings");
        Alert settingsDialog = new Alert(Alert.AlertType.NONE, null, ButtonType.CANCEL, ButtonType.OK);

        try {
            Parent root = FXMLLoader.load(Objects.requireNonNull(getClass().getResource("/pages/popup_settings.fxml")), null, builderFactory, guiceControllerFactory);
            settingsDialog.setGraphic(root);
        } catch (IOException e) {
            logger.error("Error loading Settings Dialog: {}", e.getMessage());
        }

        settingsDialog.showAndWait();
    }

    @FXML
    public void saveFile() {
        logger.debug("SAVE FILE!!");
        if (this.tabPaneXml.isSelected()) {
            logger.debug("Save XML File");
            var success = xmlController.saveCurrentChanges();
            if (success) {
                Alert successMessage = new Alert(Alert.AlertType.INFORMATION);
                successMessage.setContentText("Writing successful");
                successMessage.showAndWait();
            } else {
                Alert fail = new Alert(Alert.AlertType.ERROR);
                fail.setContentText("Error in writing File!");
                fail.showAndWait();
            }
        }
        if (this.tabPaneXsd.isSelected()) {
            logger.debug("Save XSD File");
        }
    }

    @FXML
    private void pressAboutButton() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information Dialog");
        alert.setHeaderText("Look, an Information Dialog");
        alert.setContentText("I have a great message for you!");

        alert.showAndWait();
    }

    public XmlController getXmlController() {
        return xmlController;
    }

    public XsltController getXsltController() {
        return xsltController;
    }

    public XsdValidationController getXsdValidationController() {
        return xsdValidationController;
    }

    public SignatureController getSignatureController() {
        return signatureController;
    }

    public FopController getFopController() {
        return this.fopController;
    }

    public XsdController getXsdController() {
        return this.xsdController;
    }

    @FXML
    private void openFile() {
        Stage stage = (Stage) mainBox.getScene().getWindow();

        logger.debug("Last open Dir: {}", lastOpenDir);
        if (lastOpenDir == null) {
            lastOpenDir = Path.of(".").toString();
        }
        fileChooser.setInitialDirectory(new File(lastOpenDir));
        File selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile != null && selectedFile.exists()) {
            logger.debug("Selected File from Menue: {}", selectedFile.getAbsolutePath());
            this.lastOpenDir = selectedFile.getParent();

            final var fileExtension = FilenameUtils.getExtension(selectedFile.getName()).toLowerCase();
            switch (fileExtension) {
                case "xml":
                    xmlService.setCurrentXmlFile(selectedFile);
                    xmlController.reloadXmlText();

                    tabPane.getSelectionModel().select(tabPaneXml);
                    break;
                case "xsd":
                    xmlService.setCurrentXsdFile(selectedFile);
                    xsdController.reloadXmlText();

                    tabPane.getSelectionModel().select(tabPaneXsd);
                    break;
                case "fo":
                    break;
            }
        } else {
            logger.debug("No file selected");
        }
    }


    @FXML
    private void initialize() {
        xmlController.setParentController(this);
        xsltController.setParentController(this);
        xsdController.setParentController(this);
        xsdValidationController.setParentController(this);
        fopController.setParentController(this);

        // TODO: auf showOpenMultipleDialog umbauen!!
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("XML Files", "*.xml", "*.xslt", "*.xsd")
        );

        fileChooser.setTitle("Loading XML File");

        exit.setOnAction(e -> System.exit(0));

        prettyPrint.setOnAction(event -> {
            if (tabPaneXml.isSelected()) {
                if (xmlController != null) {
                    xmlController.formatXmlText();
                } else {
                    logger.error("XMLController is null");
                }
            }
            if (tabPaneXsd.isSelected()) {
                if (xsdController != null) {
                    // null;
                }
            }
        });


    }


}
