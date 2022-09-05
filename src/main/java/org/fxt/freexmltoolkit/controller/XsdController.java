package org.fxt.freexmltoolkit.controller;

import com.google.inject.Inject;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.scene.layout.StackPane;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxt.freexmltoolkit.service.XmlService;
import org.xmlet.xsdparser.core.XsdParser;
import org.xmlet.xsdparser.xsdelements.*;
import org.xmlet.xsdparser.xsdelements.xsdrestrictions.XsdEnumeration;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.fxt.freexmltoolkit.controller.XmlController.computeHighlighting;

public class XsdController {

    @Inject
    XmlService xmlService;

    CodeArea codeArea = new CodeArea();
    VirtualizedScrollPane<CodeArea> virtualizedScrollPane;

    @FXML
    StackPane stackPane;

    @FXML
    TextArea documentation;


    private MainController parentController;

    private final static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

    final static int MAX_ALLOWED_DEPTH = 99;
    private List<XsdComplexType> xsdComplexTypes;
    private List<XsdSimpleType> xsdSimpleTypes;


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
                codeArea.scrollToPixel(1, 1);

                logger.debug("Caret Position: {}", codeArea.getCaretPosition());
                logger.debug("Caret Column: {}", codeArea.getCaretColumn());
            } else {
                logger.warn("FILE IS NULL");
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    @FXML
    private void generateDocumentation() {
        documentation.setText("TEST");

        XsdParser parser;
        List<XsdElement> elements;

        parser = new XsdParser(xmlService.getCurrentXsdFile().getAbsolutePath());
        elements = parser.getResultXsdElements().collect(Collectors.toList());
        List<XsdSchema> xmlSchema = parser.getResultXsdSchemas().collect(Collectors.toList());

        xsdComplexTypes = xmlSchema.get(0).getChildrenComplexTypes().collect(Collectors.toList());
        xsdSimpleTypes = xmlSchema.get(0).getChildrenSimpleTypes().collect(Collectors.toList());

        for (XsdElement xsdElement : elements) {
            getXsdAbstractElementInfo(0, xsdElement, List.of(xsdElement.getName()), List.of());
        }
    }

    void getXsdAbstractElementInfo(int level, XsdAbstractElement xsdAbstractElement, List<String> prevElementTypes, List<String> prevElementPath) {
        logger.debug("prevElementTypes = {}", prevElementTypes);
        if (level > MAX_ALLOWED_DEPTH) {
            logger.error("Too many elements");
            System.err.println("Too many elements");
            System.exit(0);
        }

        if (xsdAbstractElement instanceof XsdElement currentXsdElement) {
            final String currentXpath = "/" + String.join("/", prevElementPath) + "/" + currentXsdElement.getName();
            logger.debug("Current XPath = {}", currentXpath);

            var currentType = currentXsdElement.getType();
            logger.debug("Current Type: " + currentType);
            logger.debug("Current Name: {}", currentXsdElement.getRawName());

            if (currentXsdElement.getAnnotation() != null && currentXsdElement.getAnnotation().getDocumentations() != null) {
                for (XsdAppInfo xsdAppInfo : currentXsdElement.getAnnotation().getAppInfoList()) {
                    logger.debug("App Info: {}", xsdAppInfo.getContent());
                }

                for (XsdDocumentation xsdDocumentation : currentXsdElement.getAnnotation().getDocumentations()) {
                    logger.debug("Documentation: {}", xsdDocumentation.getContent());
                    logger.debug("Documentation Attributest: {}", xsdDocumentation.getAttributesMap());
                }
            }

            if (prevElementTypes.stream().anyMatch(str -> str.trim().equals(currentType))) {
                System.out.println("ELEMENT SCHON BEARBEITET: " + currentType);
                logger.warn("Element {} schon bearbeitet.", currentType);
                return;
            } else {
                logger.debug("noch nicht bearbeitet: {}", currentType);
            }
            System.out.println("LEVEL: " + level + " - RAW NAME: " + currentXsdElement.getRawName());

            if (currentXsdElement.getXsdComplexType() != null) {
                System.out.println("TYPE: " + currentXsdElement.getXsdComplexType().getRawName()); // entweder null oder Type (IdentifiersType)

                if (currentXsdElement.getXsdComplexType() != null && currentXsdElement.getXsdComplexType().getXsdChildElement() != null) {
                    logger.debug("Attributes Complex Type: {}", currentXsdElement.getXsdComplexType().getAttributesMap());

                    XsdAbstractElement element = currentXsdElement.getXsdComplexType().getXsdChildElement();
                    var allElements = element.getXsdElements().toList();
                    for (XsdAbstractElement allElement : allElements) {
                        ArrayList<String> prevElementTypeList = new ArrayList<>(prevElementTypes);
                        if (currentXsdElement.getXsdComplexType().getRawName() != null) {
                            prevElementTypeList.add(currentXsdElement.getXsdComplexType().getRawName());
                        }

                        ArrayList<String> prevElementPathList = new ArrayList<>(prevElementPath);
                        if (currentXsdElement.getRawName() != null) {
                            prevElementPathList.add(currentXsdElement.getRawName());
                        }

                        getXsdAbstractElementInfo(level + 1, allElement, prevElementTypeList, prevElementPathList);
                    }
                }
            }
            if (currentXsdElement.getXsdSimpleType() != null) {
                logger.debug("SIMPLE: " + currentXsdElement.getXsdSimpleType().getAttributesMap());
                var simpleType = currentXsdElement.getXsdSimpleType();

                // xsdHtmlGenerator.generateSimpleType(currentXsdElement, level, currentXpath);

                logger.debug("Attributes Simple Type: {}", currentXsdElement.getXsdSimpleType().getAttributesMap());
                logger.debug("current type: {}", currentType);
                logger.debug("simple Type base: {}", simpleType.getRestriction().getBase());

                if (currentType != null && simpleType.getRestriction().getBase() != null) {
                    // documentation.append("Build in Type: [").append(currentType).append("] ").append(System.lineSeparator());
                }
                if (currentType != null && simpleType.getRestriction().getBase() == null) {
                    // documentation.append("Custom Type: [").append(currentType).append("] ").append(System.lineSeparator());
                }
                if (simpleType.getRestriction().getBase() != null) {
                    // documentation.append("BaseType: ").append(simpleType.getRestriction().getBase()).append(System.lineSeparator());
                }

                if (simpleType.getRestriction().getEnumeration().size() > 0) {
                    /*documentation.append(System.lineSeparator())
                            .append("ENUMERATION: ")
                            .append(System.lineSeparator());
                     */

                    var elementEnum = simpleType.getRestriction().getEnumeration();
                    for (XsdEnumeration xsdEnumeration : elementEnum) {
                        logger.debug("xsdEnumeration.getValue() = " + xsdEnumeration.getValue());
                    }

                    var t = xsdSimpleTypes.stream().filter(x -> x.getRawName().equals(currentXsdElement.getType())).findFirst();
                    if (t.isPresent()) {
                        /*
                        documentation.append(" Restricition: ")
                                .append(t.get().getRestriction().getAttributesMap())
                                .append(System.lineSeparator())
                                .append(System.lineSeparator());

                        var restriction = t.get().getRestriction();
                        documentation.append("| KEY | Value | ")
                                .append(System.lineSeparator())
                                .append("| --- | --- | ")
                                .append(System.lineSeparator());

                        documentation.append("| Max Length | ")
                                .append((restriction.getMaxLength() == null ? "" : restriction.getMaxLength().getValue()))
                                .append(" |")
                                .append(System.lineSeparator());

                        documentation.append("| Min Length | ")
                                .append((restriction.getMinLength() == null ? "" : Integer.valueOf(restriction.getMinLength().getValue())))
                                .append(" |")
                                .append(System.lineSeparator());

                        documentation.append("| Total Digets | ")
                                .append((restriction.getTotalDigits() == null ? "" : restriction.getTotalDigits().getValue()))
                                .append(" |")
                                .append(System.lineSeparator());

                        documentation.append("| Pattern | ")
                                .append((restriction.getPattern() == null ? "" : "`" + restriction.getPattern().getValue() + "`"))
                                .append(" |")
                                .append(System.lineSeparator());
                         */
                    }

                    // documentation.append(System.lineSeparator()).append(System.lineSeparator());
                }
            }
        }

        if (xsdAbstractElement instanceof XsdChoice xsdChoice) {
            System.out.println("XsdChoice = " + xsdAbstractElement.getClass().getName());
            System.out.println("xsdChoice = " + xsdChoice.getAttributesMap());
        }

        if (xsdAbstractElement instanceof XsdSequence xsdSequence) {
            System.out.println("XsdSequence = " + xsdAbstractElement.getClass().getName());

            System.out.println("((XsdSequence) xsdAbstractElement).getId() = " + xsdSequence.getId());
            System.out.println("sequence = " + xsdSequence.getAttributesMap());
        }
    }

}
