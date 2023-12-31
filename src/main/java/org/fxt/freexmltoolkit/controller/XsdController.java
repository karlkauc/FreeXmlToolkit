/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2023.
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

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxt.freexmltoolkit.controls.XmlEditor;
import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.XmlServiceImpl;
import org.fxt.freexmltoolkit.service.XsdDocumentationService;
import org.xmlet.xsdparser.xsdelements.*;
import org.xmlet.xsdparser.xsdelements.xsdrestrictions.XsdEnumeration;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class XsdController {
    XmlService xmlService = XmlServiceImpl.getInstance();

    CodeArea codeArea;
    VirtualizedScrollPane<CodeArea> virtualizedScrollPane;

    @FXML
    Button newFile, openFile, saveFile, prettyPrint, validateSchema;

    DirectoryChooser documentationOutputDirectory;
    File selectedDocumentationOutputDirectory;


    @FXML
    TextField documentationOutputDirPath, xsdFilePath;

    @FXML
    Label schemaValidText;

    @FXML
    StackPane stackPane;

    @FXML
    TextArea documentation, sampleData;

    @FXML
    CheckBox openFileAfterCreation;

    String documentationString;

    private MainController parentController;

    private final static Logger logger = LogManager.getLogger(XsdController.class);

    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

    final static int MAX_ALLOWED_DEPTH = 99;
    private List<XsdComplexType> xsdComplexTypes;
    private List<XsdSimpleType> xsdSimpleTypes;


    @FXML
    private void initialize() {
        logger.debug("Bin im xsdController init");

        codeArea = new CodeArea();
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            if (newText.length() < 1024 * 1024 * 2) { // MAX 2 MB große Files
                logger.debug("Format Text begin!");
                Platform.runLater(() -> {
                    codeArea.setStyleSpans(0, XmlEditor.computeHighlighting(newText));
                    logger.debug("FINISH 1");
                });
                logger.debug("Format Text fertig!");
            }
        });

        try {
            virtualizedScrollPane = new VirtualizedScrollPane<>(codeArea);
            stackPane.getChildren().add(virtualizedScrollPane);
            reloadXmlText();
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
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
            logger.error("Error in reloadXMLText: ");
            logger.error(e.getMessage());
        }
    }

    @FXML
    private void loadXsdFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("XML Schema Definition", "*.xsd")
        );
        fileChooser.setInitialDirectory(new File("."));
        var xsdFile = fileChooser.showOpenDialog(null);

        if (xsdFile != null && xsdFile.exists()) {
            logger.debug("open File: {}", xsdFile.getAbsolutePath());
            xmlService.setCurrentXsdFile(xsdFile);
            xsdFilePath.setText(xsdFile.getName());
        }
    }

    @FXML
    private void test() {
        final var testFilePath = Paths.get("examples/xsd/FundsXML4.xsd");

        if (Files.exists(testFilePath)) {
            xmlService.setCurrentXsdFile(testFilePath.toFile());
            generateDocumentation();
        } else {
            logger.debug("test file not found: {}", testFilePath.toFile().getAbsolutePath());
        }
    }

    @FXML
    private void openOutputFolderDialog() {
        documentationOutputDirectory = new DirectoryChooser();
        documentationOutputDirectory.setTitle("Output Directory");
        documentationOutputDirectory.setInitialDirectory(new File("."));
        selectedDocumentationOutputDirectory = documentationOutputDirectory.showDialog(null);

        if (selectedDocumentationOutputDirectory != null && selectedDocumentationOutputDirectory.exists()) {
            logger.debug("Directory: {}", selectedDocumentationOutputDirectory.getAbsolutePath());
            documentationOutputDirPath.setText(selectedDocumentationOutputDirectory.getAbsolutePath());
        } else {
            logger.debug("no dir selected");
            documentationOutputDirPath.setText(null);
        }
    }

    @FXML
    private void generateDocumentation() {
        if (selectedDocumentationOutputDirectory != null && selectedDocumentationOutputDirectory.exists()
                && xmlService.getCurrentXsdFile() != null && xmlService.getCurrentXsdFile().exists()) {

            XsdDocumentationService xsdDocumentationService = new XsdDocumentationService();
            try {
                xsdDocumentationService.setXsdFilePath(xmlService.getCurrentXsdFile().getPath());
                xsdDocumentationService.generateXsdDocumentation(selectedDocumentationOutputDirectory);

                if (openFileAfterCreation.isSelected()) {
                    Desktop.getDesktop().open(new File(selectedDocumentationOutputDirectory.getAbsolutePath() + "/index.html"));
                }
            } catch (IOException ioException) {
                logger.error(ioException.getMessage());
            }
        }
    }

    void getXsdAbstractElementInfo(int level, XsdAbstractElement xsdAbstractElement, List<String> prevElementTypes, List<String> prevElementPath) {
        logger.debug("prevElementTypes = {}", prevElementTypes);
        if (level > MAX_ALLOWED_DEPTH) {
            logger.error("Too many elements");
            System.err.println("Too many elements");
        }

        if (xsdAbstractElement instanceof XsdElement currentXsdElement) {
            final String currentXpath = "/" + String.join("/", prevElementPath) + "/" + currentXsdElement.getName();
            logger.debug("Current XPath = {}", currentXpath);

            documentationString += System.lineSeparator() + "XPATH: " + currentXpath;

            var currentType = currentXsdElement.getType();
            logger.debug("Current Type: " + currentType);
            logger.debug("Current Name: {}", currentXsdElement.getRawName());

            documentationString += System.lineSeparator() + "Type: " + currentType;
            documentationString += System.lineSeparator() + "Name: " + currentXsdElement.getRawName();

            if (currentXsdElement.getAnnotation() != null && currentXsdElement.getAnnotation().getDocumentations() != null) {
                for (XsdAppInfo xsdAppInfo : currentXsdElement.getAnnotation().getAppInfoList()) {
                    logger.debug("App Info: {}", xsdAppInfo.getContent());
                }

                for (XsdDocumentation xsdDocumentation : currentXsdElement.getAnnotation().getDocumentations()) {
                    logger.debug("Documentation: {}", xsdDocumentation.getContent());
                    logger.debug("Documentation Attributest: {}", xsdDocumentation.getAttributesMap());

                    documentationString += System.lineSeparator() + "Documentation: " + xsdDocumentation.getContent();
                    documentationString += System.lineSeparator() + "Documentation Attributest: " + xsdDocumentation.getAttributesMap();
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

                if (!simpleType.getRestriction().getEnumeration().isEmpty()) {
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

        documentationString += System.lineSeparator() + "----------------------" + System.lineSeparator();
    }
}
