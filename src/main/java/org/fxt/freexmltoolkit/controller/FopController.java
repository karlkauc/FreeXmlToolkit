package org.fxt.freexmltoolkit.controller;

import com.google.inject.Inject;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxt.freexmltoolkit.service.XmlService;

import java.io.IOException;
import java.nio.file.Files;

import static org.fxt.freexmltoolkit.controller.XmlController.computeHighlighting;

public class FopController {

    @Inject
    XmlService xmlService;

    @FXML
    GridPane settings;

    @FXML
    Button startConversion;

    @FXML
    AnchorPane xml, xslt, pdf;

    @FXML
    StackPane stackPaneXml, stackPaneXslt;

    private MainController parentController;

    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

    private final static Logger logger = LogManager.getLogger(FopController.class);

    public static final int MAX_SIZE_FOR_FORMATING = 1024 * 1024 * 2;

    CodeArea codeArea = new CodeArea();
    VirtualizedScrollPane<CodeArea> virtualizedScrollPane;


    @FXML
    private void initialize() {
        logger.debug("BIN IM FOP CONTROLLER");
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            if (newText.length() < MAX_SIZE_FOR_FORMATING) {
                logger.debug("Format Text begin!");
                Platform.runLater(() -> {
                    codeArea.setStyleSpans(0, computeHighlighting(newText));
                    logger.debug("FINISH REFORMAT TEXT in XmlController");
                });
            }
        });

        virtualizedScrollPane = new VirtualizedScrollPane<>(codeArea);
        stackPaneXml.getChildren().add(virtualizedScrollPane);

    }

    @FXML
    private void buttonConversion() throws IOException {
        logger.debug("Start Conversion!");

        codeArea.clear();
        codeArea.setBackground(null);

        try {
            if (xmlService.getCurrentXmlFile() != null && xmlService.getCurrentXmlFile().exists()) {
                codeArea.replaceText(0, 0, Files.readString(xmlService.getCurrentXmlFile().toPath()));
                codeArea.scrollToPixel(1, 1);
            } else {
                logger.warn("FILE IS NULL");
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
        }

    }

}

