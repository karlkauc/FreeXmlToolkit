package org.fxt.freexmltoolkit.controller;

import com.google.inject.Inject;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.fxt.freexmltoolkit.service.XmlService;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XmlController {

    public static final int MAX_SIZE_FOR_FORMATING = 1024 * 1024 * 2;
    @Inject
    XmlService xmlService;

    private MainController parentController;

    private final static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

    private static final Pattern XML_TAG = Pattern.compile("(?<ELEMENT>(</?\\h*)(\\w+)([^<>]*)(\\h*/?>))"
            + "|(?<COMMENT><!--[^<>]+-->)");

    private static final Pattern ATTRIBUTES = Pattern.compile("(\\w+\\h*)(=)(\\h*\"[^\"]+\")");

    private static final int GROUP_OPEN_BRACKET = 2;
    private static final int GROUP_ELEMENT_NAME = 3;
    private static final int GROUP_ATTRIBUTES_SECTION = 4;
    private static final int GROUP_CLOSE_BRACKET = 5;
    private static final int GROUP_ATTRIBUTE_NAME = 1;
    private static final int GROUP_EQUAL_SYMBOL = 2;
    private static final int GROUP_ATTRIBUTE_VALUE = 3;

    CodeArea codeArea = new CodeArea();
    VirtualizedScrollPane<CodeArea> virtualizedScrollPane;

    @FXML
    StackPane stackPane;

    @FXML
    Button xpath;

    @FXML
    TextArea xpathText;

    @FXML
    private void evaluateXpath() {
        var newString = this.xmlService.getXmlFromXpath(xpathText.getText());
        logger.debug("New String: {}", newString);
        codeArea.clear();
        codeArea.replaceText(0, 0, newString);
        codeArea.setBackground(new Background(
                new BackgroundFill(
                        new LinearGradient(0, 0, 1, 1, true,
                                CycleMethod.NO_CYCLE,
                                new Stop(0, Color.web("#81c483")),
                                new Stop(1, Color.web("#fcc200"))
                        ), CornerRadii.EMPTY, Insets.EMPTY
                )
        ));
    }

    public void setParentController(MainController parentController) {
        logger.debug("XML Controller - set parent controller");
        this.parentController = parentController;
    }

    @FXML
    public void reloadXmlText() {
        logger.debug("Reload XML Text");
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

    public boolean saveCurrentChanges() {
        if (this.codeArea.getText() != null) {
            if (this.xmlService.getCurrentXmlFile() != null) {
                try {
                    Path path = Paths.get(this.xmlService.getCurrentXmlFile().getPath());
                    byte[] strToBytes = codeArea.getText().getBytes();
                    Files.write(path, strToBytes);

                    this.xmlService.setCurrentXmlFile(path.toFile());
                    return true;
                } catch (Exception e) {
                    logger.error("Exception in writing File: {}", e.getMessage());
                    logger.error("File: {}", this.xmlService.getCurrentXmlFile().getAbsolutePath());
                }
            }
        } else {
            logger.debug("No Text");
        }
        return false;
    }

    @FXML
    private void initialize() {
        logger.debug("Bin im xmlController init");

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
        stackPane.getChildren().add(virtualizedScrollPane);
    }

    public void formatXmlText() {
        var temp = codeArea.getText();
        codeArea.clear();

        final String tempFormat = XmlService.prettyFormat(temp, 20);
        logger.debug("Format String length: {}", tempFormat.length());
        codeArea.replaceText(0, 0, tempFormat);
    }

    static StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = XML_TAG.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while (matcher.find()) {

            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            if (matcher.group("COMMENT") != null) {
                spansBuilder.add(Collections.singleton("comment"), matcher.end() - matcher.start());
            } else {
                if (matcher.group("ELEMENT") != null) {
                    String attributesText = matcher.group(GROUP_ATTRIBUTES_SECTION);

                    spansBuilder.add(Collections.singleton("tagmark"), matcher.end(GROUP_OPEN_BRACKET) - matcher.start(GROUP_OPEN_BRACKET));
                    spansBuilder.add(Collections.singleton("anytag"), matcher.end(GROUP_ELEMENT_NAME) - matcher.end(GROUP_OPEN_BRACKET));

                    if (!attributesText.isEmpty()) {

                        lastKwEnd = 0;

                        Matcher amatcher = ATTRIBUTES.matcher(attributesText);
                        while (amatcher.find()) {
                            spansBuilder.add(Collections.emptyList(), amatcher.start() - lastKwEnd);
                            spansBuilder.add(Collections.singleton("attribute"), amatcher.end(GROUP_ATTRIBUTE_NAME) - amatcher.start(GROUP_ATTRIBUTE_NAME));
                            spansBuilder.add(Collections.singleton("tagmark"), amatcher.end(GROUP_EQUAL_SYMBOL) - amatcher.end(GROUP_ATTRIBUTE_NAME));
                            spansBuilder.add(Collections.singleton("avalue"), amatcher.end(GROUP_ATTRIBUTE_VALUE) - amatcher.end(GROUP_EQUAL_SYMBOL));
                            lastKwEnd = amatcher.end();
                        }
                        if (attributesText.length() > lastKwEnd)
                            spansBuilder.add(Collections.emptyList(), attributesText.length() - lastKwEnd);
                    }

                    lastKwEnd = matcher.end(GROUP_ATTRIBUTES_SECTION);

                    spansBuilder.add(Collections.singleton("tagmark"), matcher.end(GROUP_CLOSE_BRACKET) - lastKwEnd);
                }
            }
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }
}
