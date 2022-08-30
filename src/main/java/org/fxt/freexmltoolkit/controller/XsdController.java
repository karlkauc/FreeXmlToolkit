package org.fxt.freexmltoolkit.controller;

import com.google.inject.Inject;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.layout.StackPane;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxt.freexmltoolkit.service.XmlService;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;

import static org.fxt.freexmltoolkit.controller.XmlController.computeHighlighting;

public class XsdController {

    @Inject
    XmlService xmlService;

    CodeArea codeArea = new CodeArea();
    VirtualizedScrollPane<CodeArea> virtualizedScrollPane;

    @FXML
    StackPane stackPane;

    private MainController parentController;

    private final static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }


    @FXML
    private void initialize() {
        logger.debug("Bin im xsdController init");

        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            if (newText.length() < 1024 * 1024 * 2) { // MAX 2 MB große Files
                logger.debug("Format Text begin!");
                Platform.runLater(() -> {
                    codeArea.setStyleSpans(0, computeHighlighting(newText));
                    logger.debug("FINISH 1");
                });
                logger.debug("Format Text fertig!");
            }
        });

        virtualizedScrollPane = new VirtualizedScrollPane<>(codeArea);
        stackPane.getChildren().add(virtualizedScrollPane);
        reloadXmlText();
    }


    @FXML
    public void reloadXmlText() {
        logger.debug("Reload XSD Text");
        codeArea.clear();
        codeArea.setBackground(null);

        try {
            if (xmlService.getCurrentXsdFile() != null && xmlService.getCurrentXsdFile().exists()) {
                codeArea.replaceText(0, 0, Files.readString(xmlService.getCurrentXsdFile().toPath()));

                logger.debug("Caret Position: {}", codeArea.getCaretPosition());
                logger.debug("Caret Column: {}", codeArea.getCaretColumn());
            } else {
                logger.warn("FILE IS NULL");
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

}
