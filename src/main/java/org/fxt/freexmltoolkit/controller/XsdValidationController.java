package org.fxt.freexmltoolkit.controller;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import javafx.fxml.FXML;
import javafx.fxml.JavaFXBuilderFactory;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.util.BuilderFactory;
import javafx.util.Callback;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.ModuleBindings;
import org.fxt.freexmltoolkit.service.XmlService;
import org.xml.sax.SAXParseException;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;

public class XsdValidationController {

    final Injector injector = Guice.createInjector(new ModuleBindings());
    BuilderFactory builderFactory = new JavaFXBuilderFactory();
    Callback<Class<?>, Object> guiceControllerFactory = injector::getInstance;

    @Inject
    XmlService xmlService;

    private MainController parentController;

    @FXML
    AnchorPane anchorPane;

    FileChooser xmlFileChooser = new FileChooser(), xsdFileChooser = new FileChooser();

    @FXML
    GridPane auswertung;

    @FXML
    Button xmlLoadButton, xsdLoadButton;

    @FXML
    TextField xmlFileName, xsdFileName, remoteXsdLocation;

    @FXML
    VBox errorListBox;

    @FXML
    CheckBox autodetect;

    @FXML
    ImageView statusImage;

    private final static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

    @FXML
    private void toggleAutoDetection() {
        xsdLoadButton.setDisable(!xsdLoadButton.isDisable());
        xsdFileName.setDisable(!xsdFileName.isDisable());
    }

    @FXML
    private void initialize() {
        if (parentController != null && parentController.lastOpenDir != null) {
            xmlFileChooser.setInitialDirectory(new File(parentController.lastOpenDir));
            xsdFileChooser.setInitialDirectory(new File(parentController.lastOpenDir));
        } else {
            final Path path = FileSystems.getDefault().getPath(".");
            xmlFileChooser.setInitialDirectory(path.toFile());
            xsdFileChooser.setInitialDirectory(path.toFile());
        }

        xmlFileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("XML File", "*.xml"));
        xsdFileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("XSD File", "*.xsd"));

        xmlLoadButton.setOnAction(ae -> {
            var tempFile = xmlFileChooser.showOpenDialog(null);
            if (tempFile != null) {
                logger.debug("Loaded XML File: {}", tempFile.getAbsolutePath());
                processXmlFile(tempFile);

                parentController.getXmlController().reloadXmlText();
                parentController.getXsdController().reloadXmlText();
            }
        });

        xmlLoadButton.setOnDragOver(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            } else {
                event.consume();
            }
        });

        xmlLoadButton.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                success = true;
                for (File file : db.getFiles()) {
                    processXmlFile(file);
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });

        xsdLoadButton.setOnAction(ae -> {
            var tempFile = xsdFileChooser.showOpenDialog(null);
            if (tempFile != null) {
                logger.debug("Loaded XSLT File: {}", tempFile.getAbsolutePath());
                xmlService.setCurrentXsdFile(tempFile);
                xsdFileName.setText(xmlService.getCurrentXsdFile().getName());
                reload();
            }
        });
    }

    private void processXmlFile(File file) {
        xmlService.setCurrentXmlFile(file);
        xmlFileName.setText(xmlService.getCurrentXmlFile().getName());

        if (autodetect.isSelected()) {
            var schemaName = xmlService.getSchemaNameFromCurrentXMLFile();
            if (schemaName != null && !schemaName.isEmpty()) {
                xsdFileName.setText(schemaName);
                if (xmlService.loadSchemaFromXMLFile()) {
                    logger.debug("Loading remote schema successfull!");
                } else {
                    logger.debug("Could not load remote schema");
                }
            }
        }
        reload();
        if (parentController != null) {
            parentController.getXmlController().reloadXmlText();
            parentController.getXsdController().reloadXmlText();
        }
    }


    @FXML
    private void reload() {
        if (xmlService.getCurrentXmlFile() != null && xmlService.getCurrentXmlFile().exists() &&
                xmlService.getCurrentXsdFile() != null && xmlService.getCurrentXsdFile().exists()) {
            remoteXsdLocation.setText(xmlService.getRemoteXsdLocation());

            errorListBox.getChildren().clear();

            var exceptionList = xmlService.validate();
            if (exceptionList != null && exceptionList.size() > 0) {
                logger.warn(Arrays.toString(exceptionList.toArray()));
                int i = 0;
                for (SAXParseException saxParseException : exceptionList) {
                    TextFlow textFlowPane = new TextFlow();
                    textFlowPane.setLineSpacing(5.0);

                    Text headerText = new Text("#" + i++ + ": " + saxParseException.getLocalizedMessage() + System.lineSeparator());
                    headerText.setFont(Font.font("Verdana", 20));

                    textFlowPane.getChildren().add(headerText);

                    Text lineText = new Text("Line#: " + saxParseException.getLineNumber() + " Col#: " + saxParseException.getColumnNumber() + System.lineSeparator());
                    textFlowPane.getChildren().add(lineText);

                    try {
                        var lineBevore = Files.readAllLines(xmlService.getCurrentXmlFile().toPath()).get(saxParseException.getLineNumber() - 1).trim();
                        textFlowPane.getChildren().add(new Text(lineBevore + System.lineSeparator()));

                        var line = Files.readAllLines(xmlService.getCurrentXmlFile().toPath()).get(saxParseException.getLineNumber()).trim();
                        textFlowPane.getChildren().add(new Text(line + System.lineSeparator()));

                        var lineAfter = Files.readAllLines(xmlService.getCurrentXmlFile().toPath()).get(saxParseException.getLineNumber() + 1).trim();
                        textFlowPane.getChildren().add(new Text(lineAfter + System.lineSeparator()));
                    } catch (IOException exception) {
                        logger.error("Exception: {}", exception.getMessage());
                    }
                    errorListBox.getChildren().add(textFlowPane);

                    Button goToError = new Button("Go to error");
                    goToError.setOnAction(ae -> {
                        try {
                            var p = (TabPane) anchorPane.getParent().getParent();
                            var t = p.getTabs();
                            t.forEach(e -> {
                                if (Objects.equals(e.getId(), "tabPaneXml")) {
                                    this.parentController.getXmlController().codeArea.scrollToPixel(saxParseException.getLineNumber(), saxParseException.getColumnNumber());
                                    this.parentController.getXmlController().codeArea.requestFocus();

                                    p.getSelectionModel().select(e);
                                }
                            });

                        } catch (Exception e) {
                            logger.error(e.getMessage());
                        }
                    });
                    errorListBox.getChildren().add(goToError);
                    errorListBox.getChildren().add(new Separator());
                }

                Image image = new Image(Objects.requireNonNull(getClass().getResource("/img/icons8-stornieren-48.png")).toString());
                statusImage.setImage(image);

            } else {
                logger.warn("KEINE ERRORS");
                Image image = new Image(Objects.requireNonNull(getClass().getResource("/img/icons8-ok-48.png")).toString());
                statusImage.setImage(image);
            }
        } else {
            logger.debug("war nicht alles ausgewählt!!");
            logger.debug("Current XML File: {}", xmlService.getCurrentXmlFile());
            logger.debug("Current XSD File: {}", xmlService.getCurrentXsdFile());
        }
    }

    @FXML
    private void test() {
        xmlService.setCurrentXmlFile(Paths.get("C:/Data/src/FreeXmlToolkit/output/!FundsXML AMUNDI FLOATING RATE EURO CORP ESG as of 2021-12-30 v2.xml").toFile());
        xmlFileName.setText(xmlService.getCurrentXmlFile().getName());

        xmlService.setCurrentXsdFile(Paths.get("C:/Data/src/schema/FundsXML4.xsd").toFile());
        xsdFileName.setText(xmlService.getCurrentXsdFile().getName());

        reload();

        if (parentController != null) {
            parentController.getXmlController().reloadXmlText();
            parentController.getXsdController().reloadXmlText();
        }
    }

}
