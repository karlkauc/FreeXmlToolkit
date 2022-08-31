package org.fxt.freexmltoolkit.controller;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.JavaFXBuilderFactory;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Tab;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.BuilderFactory;
import javafx.util.Callback;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.ModuleBindings;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.XmlService;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
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

    private final static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

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
/*

        Properties p = propertiesService.loadProperties();

        GridPane gridPane = new GridPane();
        gridPane.setPadding(new Insets(10, 10, 10, 10));
        gridPane.setHgap(10);
        gridPane.setVgap(10);

        gridPane.add(new Label("HTTP Proxy Host"), 0, 0);
        TextField httpProxy = new TextField();
        httpProxy.setText(p.getProperty("http.proxy.host"));
        gridPane.add(httpProxy, 1, 0);

        gridPane.add(new Label("HTTP Proxy Port"), 0, 1);
        TextField httpProxyPort = new TextField();
        httpProxyPort.setText(p.getProperty("http.proxy.port"));
        gridPane.add(httpProxyPort, 1, 1);

        settingsDialog.setGraphic(gridPane);


        var result = settingsDialog.showAndWait();
        if (result.isPresent()) {
            var buttonType = result.get();
            if (!buttonType.getButtonData().isCancelButton()) {
                logger.debug("Save Properties: {}", p);
                if (httpProxy.getText() != null) {
                    p.setProperty("http.proxy.host", httpProxy.getText());
                }
                if (httpProxyPort.getText() != null) {
                    p.setProperty("http.proxy.port", httpProxyPort.getText());
                }
                propertiesService.saveProperties(p);
            }
            else {
                logger.debug("Do not save properties: {}", p);
            }
        }

 */
    }

    @FXML
    private void saveFile() {
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

    public XsdController getXsdController() { return this.xsdController; }

    @FXML
    private void openFile(ActionEvent e) {
        Stage stage = (Stage) mainBox.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile != null) {
            logger.debug("Selected File from Menue: {}", selectedFile.getAbsolutePath());
            String fileContent;

            if (selectedFile.exists()) {
                this.xmlService.setCurrentXmlFile(selectedFile);

                try {
                    fileContent = Files.readString(Path.of(selectedFile.getAbsolutePath()));
                    logger.debug("fileContent.length: {}", String.format("%.2f", fileContent.length() / (1024f * 1024f)) + " MB");
                    xmlController.codeArea.clear();
                    xmlController.codeArea.replaceText(0, 0, fileContent);
                } catch (IOException ex) {
                    logger.error(ex.getMessage());
                }
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

        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("XML Files", "*.xml")
                , new FileChooser.ExtensionFilter("XSLT Files", "*.xslt")
        );

        if (SystemUtils.OS_NAME.toUpperCase(Locale.ROOT).startsWith("WINDOWS") && new File(("C:\\Data\\TEMP\\2021-12-14_FundsXMLTestFiles")).exists()) {
            fileChooser.setInitialDirectory(new File("C:\\Data\\TEMP\\2021-12-14_FundsXMLTestFiles"));
        }

        exit.setOnAction(e -> System.exit(0));

        prettyPrint.setOnAction(event -> {
            if (tabPaneXml.isSelected()) {
                if (xmlController != null) {
                    xmlController.formatXmlText();
                } else {
                    logger.error("XMLController is null");
                }
            }
        });
    }


}
