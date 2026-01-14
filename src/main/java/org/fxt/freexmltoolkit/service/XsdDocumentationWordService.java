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

package org.fxt.freexmltoolkit.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.fxt.freexmltoolkit.domain.WordDocumentationConfig;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service for generating Word (.docx) documentation from XSD schema data.
 * Uses Apache POI to create professional Word documents with tables,
 * embedded images, and formatted content.
 */
public class XsdDocumentationWordService {

    private static final Logger logger = LogManager.getLogger(XsdDocumentationWordService.class);

    // Style constants
    private static final String COLOR_PRIMARY = "2563EB";      // Blue
    private static final String COLOR_SECONDARY = "64748B";    // Slate
    private static final String COLOR_HEADER_BG = "F1F5F9";    // Light gray
    private static final String COLOR_TABLE_BORDER = "CBD5E1"; // Border gray

    private static final int TABLE_WIDTH_TWIPS = 9000;

    private XsdDocumentationData documentationData;
    private XsdDocumentationImageService imageService;
    private Set<String> includedLanguages;
    private TaskProgressListener progressListener;
    private WordDocumentationConfig config = new WordDocumentationConfig();

    /**
     * Generates a Word document from the XSD documentation data.
     *
     * @param outputFile        the output .docx file
     * @param documentationData the parsed XSD documentation data
     * @throws IOException if writing the document fails
     */
    public void generateWordDocumentation(File outputFile, XsdDocumentationData documentationData) throws IOException {
        this.documentationData = documentationData;

        try (XWPFDocument document = new XWPFDocument()) {
            // Set document properties
            setDocumentProperties(document);

            // Set page size and orientation based on config
            setPageLayout(document);

            // Create cover page if configured
            if (config.isIncludeCoverPage()) {
                reportProgress("Creating cover page");
                createTitlePage(document);
            }

            // Create table of contents if configured
            if (config.isIncludeToc()) {
                reportProgress("Creating table of contents");
                createTableOfContents(document);
            }

            // Create schema overview
            reportProgress("Creating schema overview");
            createSchemaOverview(document);

            // Embed schema diagram if available and configured
            if (config.isIncludeSchemaDiagram() && imageService != null) {
                reportProgress("Embedding schema diagram");
                embedSchemaDiagram(document);
            }

            // Create ComplexTypes section
            reportProgress("Creating ComplexTypes section");
            createComplexTypesSection(document);

            // Create SimpleTypes section
            reportProgress("Creating SimpleTypes section");
            createSimpleTypesSection(document);

            // Create Data Dictionary if configured
            if (config.isIncludeDataDictionary()) {
                reportProgress("Creating Data Dictionary");
                createDataDictionarySection(document);
            }

            // Create Element Diagrams section if configured
            if (config.isIncludeElementDiagrams() && imageService != null) {
                reportProgress("Creating Element Diagrams");
                createElementDiagramsSection(document);
            }

            // Save the document
            reportProgress("Saving document");
            try (FileOutputStream out = new FileOutputStream(outputFile)) {
                document.write(out);
            }

            logger.info("Word documentation generated successfully: {}", outputFile.getAbsolutePath());
        }
    }

    /**
     * Sets the page layout (size and orientation) based on configuration.
     */
    private void setPageLayout(XWPFDocument document) {
        CTDocument1 ctDoc = document.getDocument();
        CTBody body = ctDoc.getBody();
        if (body == null) {
            body = ctDoc.addNewBody();
        }
        CTSectPr sectPr = body.getSectPr();
        if (sectPr == null) {
            sectPr = body.addNewSectPr();
        }
        CTPageSz pageSize = sectPr.getPgSz();
        if (pageSize == null) {
            pageSize = sectPr.addNewPgSz();
        }

        // Set page dimensions based on config
        // Word uses twips (1 inch = 1440 twips, 1mm = 56.7 twips)
        BigInteger width;
        BigInteger height;

        switch (config.getPageSize()) {
            case LETTER -> {
                width = BigInteger.valueOf(12240); // 8.5 inches
                height = BigInteger.valueOf(15840); // 11 inches
            }
            case LEGAL -> {
                width = BigInteger.valueOf(12240); // 8.5 inches
                height = BigInteger.valueOf(20160); // 14 inches
            }
            default -> { // A4
                width = BigInteger.valueOf(11906); // 210mm
                height = BigInteger.valueOf(16838); // 297mm
            }
        }

        // Swap dimensions for landscape orientation
        if (config.getOrientation() == WordDocumentationConfig.Orientation.LANDSCAPE) {
            BigInteger temp = width;
            width = height;
            height = temp;
            pageSize.setOrient(STPageOrientation.LANDSCAPE);
        } else {
            pageSize.setOrient(STPageOrientation.PORTRAIT);
        }

        pageSize.setW(width);
        pageSize.setH(height);
    }

    /**
     * Sets the document properties (metadata).
     */
    private void setDocumentProperties(XWPFDocument document) {
        var props = document.getProperties();
        var coreProps = props.getCoreProperties();

        String schemaName = extractSchemaName();
        coreProps.setTitle("XSD Documentation: " + schemaName);
        coreProps.setCreator("FreeXmlToolkit");
        coreProps.setDescription("Auto-generated XSD schema documentation");
        coreProps.setSubjectProperty("XSD Schema Documentation");

        var extProps = props.getExtendedProperties();
        extProps.getUnderlyingProperties().setApplication("FreeXmlToolkit");
    }

    /**
     * Creates the title page with schema information.
     */
    private void createTitlePage(XWPFDocument document) {
        // Main title
        XWPFParagraph titlePara = document.createParagraph();
        titlePara.setAlignment(ParagraphAlignment.CENTER);
        titlePara.setSpacingAfter(400);

        XWPFRun titleRun = titlePara.createRun();
        titleRun.setText("XSD Schema Documentation");
        titleRun.setBold(true);
        titleRun.setFontSize(28);
        titleRun.setColor(COLOR_PRIMARY);
        titleRun.setFontFamily("Calibri");

        // Schema name
        String schemaName = extractSchemaName();
        XWPFParagraph schemaPara = document.createParagraph();
        schemaPara.setAlignment(ParagraphAlignment.CENTER);
        schemaPara.setSpacingAfter(600);

        XWPFRun schemaRun = schemaPara.createRun();
        schemaRun.setText(schemaName);
        schemaRun.setFontSize(18);
        schemaRun.setColor(COLOR_SECONDARY);
        schemaRun.setFontFamily("Calibri");

        // Generated date
        XWPFParagraph datePara = document.createParagraph();
        datePara.setAlignment(ParagraphAlignment.CENTER);
        datePara.setSpacingAfter(200);

        XWPFRun dateRun = datePara.createRun();
        dateRun.setText("Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        dateRun.setFontSize(11);
        dateRun.setItalic(true);
        dateRun.setColor(COLOR_SECONDARY);

        // Generator info
        XWPFParagraph genPara = document.createParagraph();
        genPara.setAlignment(ParagraphAlignment.CENTER);

        XWPFRun genRun = genPara.createRun();
        genRun.setText("Generated by FreeXmlToolkit");
        genRun.setFontSize(10);
        genRun.setColor(COLOR_SECONDARY);

        // Page break after title page
        XWPFParagraph breakPara = document.createParagraph();
        breakPara.createRun().addBreak(BreakType.PAGE);
    }

    /**
     * Creates a table of contents placeholder.
     * Note: Word will need to update the TOC when the document is opened.
     */
    private void createTableOfContents(XWPFDocument document) {
        XWPFParagraph tocTitle = document.createParagraph();
        tocTitle.setStyle("Heading1");

        XWPFRun tocRun = tocTitle.createRun();
        tocRun.setText("Table of Contents");
        tocRun.setBold(true);
        tocRun.setFontSize(16);

        // Add TOC field - use simple instruction without dirty flag
        // Word will prompt user to update TOC on first open
        XWPFParagraph tocPara = document.createParagraph();
        CTSimpleField toc = tocPara.getCTP().addNewFldSimple();
        toc.setInstr("TOC \\o \"1-3\" \\h \\z \\u");

        XWPFParagraph instrPara = document.createParagraph();
        XWPFRun instrRun = instrPara.createRun();
        instrRun.setText("(Right-click and select 'Update Field' to refresh the Table of Contents)");
        instrRun.setItalic(true);
        instrRun.setFontSize(9);
        instrRun.setColor(COLOR_SECONDARY);

        // Page break
        XWPFParagraph breakPara = document.createParagraph();
        breakPara.createRun().addBreak(BreakType.PAGE);
    }

    /**
     * Creates the schema overview section.
     */
    private void createSchemaOverview(XWPFDocument document) {
        // Section heading
        createHeading(document, "Schema Overview", 1);

        // Overview table
        XWPFTable table = document.createTable(5, 2);
        setTableStyle(table);

        // Set column widths
        setColumnWidths(table, new int[]{3000, 6000});

        // Add rows
        setTableCell(table.getRow(0), 0, "File Path", true);
        setTableCell(table.getRow(0), 1, documentationData.getXsdFilePath(), false);

        setTableCell(table.getRow(1), 0, "Target Namespace", true);
        setTableCell(table.getRow(1), 1,
                documentationData.getTargetNamespace() != null ? documentationData.getTargetNamespace() : "(none)",
                false);

        setTableCell(table.getRow(2), 0, "Version", true);
        setTableCell(table.getRow(2), 1,
                documentationData.getVersion() != null ? documentationData.getVersion() : "(not specified)",
                false);

        setTableCell(table.getRow(3), 0, "Element Form Default", true);
        setTableCell(table.getRow(3), 1,
                documentationData.getElementFormDefault() != null ? documentationData.getElementFormDefault() : "unqualified",
                false);

        setTableCell(table.getRow(4), 0, "Attribute Form Default", true);
        setTableCell(table.getRow(4), 1,
                documentationData.getAttributeFormDefault() != null ? documentationData.getAttributeFormDefault() : "unqualified",
                false);

        // Statistics
        addParagraphSpacing(document);
        createHeading(document, "Statistics", 2);

        XWPFTable statsTable = document.createTable(4, 2);
        setTableStyle(statsTable);
        setColumnWidths(statsTable, new int[]{4500, 4500});

        setTableCell(statsTable.getRow(0), 0, "Global Elements", true);
        setTableCell(statsTable.getRow(0), 1, String.valueOf(documentationData.getGlobalElements().size()), false);

        setTableCell(statsTable.getRow(1), 0, "Global ComplexTypes", true);
        setTableCell(statsTable.getRow(1), 1, String.valueOf(documentationData.getGlobalComplexTypes().size()), false);

        setTableCell(statsTable.getRow(2), 0, "Global SimpleTypes", true);
        setTableCell(statsTable.getRow(2), 1, String.valueOf(documentationData.getGlobalSimpleTypes().size()), false);

        setTableCell(statsTable.getRow(3), 0, "Total Elements Documented", true);
        setTableCell(statsTable.getRow(3), 1, String.valueOf(documentationData.getExtendedXsdElementMap().size()), false);

        // Page break
        XWPFParagraph breakPara = document.createParagraph();
        breakPara.createRun().addBreak(BreakType.PAGE);
    }

    /**
     * Embeds the schema diagram as a PNG image.
     */
    private void embedSchemaDiagram(XWPFDocument document) {
        createHeading(document, "Schema Diagram", 1);

        // Find the first root element to generate diagram from
        var elementMap = documentationData.getExtendedXsdElementMap();
        XsdExtendedElement rootElement = null;

        // Find root element (level 0 or the first global element)
        for (var element : elementMap.values()) {
            if (element.getLevel() == 0) {
                rootElement = element;
                break;
            }
        }

        if (rootElement == null && !elementMap.isEmpty()) {
            rootElement = elementMap.values().iterator().next();
        }

        if (rootElement != null && imageService != null) {
            try {
                // Generate PNG to temporary file
                Path tempPng = Files.createTempFile("xsd-diagram-", ".png");
                String imagePath = imageService.generateImage(rootElement, tempPng.toFile());

                if (imagePath != null) {
                    // Insert image into document
                    XWPFParagraph imgPara = document.createParagraph();
                    imgPara.setAlignment(ParagraphAlignment.CENTER);
                    XWPFRun imgRun = imgPara.createRun();

                    try (InputStream is = Files.newInputStream(tempPng)) {
                        // Calculate image dimensions (max width 500px)
                        imgRun.addPicture(is, XWPFDocument.PICTURE_TYPE_PNG,
                                "schema-diagram.png",
                                Units.toEMU(450), Units.toEMU(300));
                    }

                    // Clean up temp file
                    Files.deleteIfExists(tempPng);

                    // Add caption
                    XWPFParagraph captionPara = document.createParagraph();
                    captionPara.setAlignment(ParagraphAlignment.CENTER);
                    XWPFRun captionRun = captionPara.createRun();
                    captionRun.setText("Figure 1: Schema Structure Overview");
                    captionRun.setItalic(true);
                    captionRun.setFontSize(10);
                    captionRun.setColor(COLOR_SECONDARY);
                }
            } catch (IOException | InvalidFormatException e) {
                logger.warn("Failed to embed schema diagram: {}", e.getMessage());

                XWPFParagraph errorPara = document.createParagraph();
                XWPFRun errorRun = errorPara.createRun();
                errorRun.setText("(Schema diagram could not be generated)");
                errorRun.setItalic(true);
                errorRun.setColor(COLOR_SECONDARY);
            }
        } else {
            XWPFParagraph noDiagramPara = document.createParagraph();
            XWPFRun noDiagramRun = noDiagramPara.createRun();
            noDiagramRun.setText("(No diagram available)");
            noDiagramRun.setItalic(true);
            noDiagramRun.setColor(COLOR_SECONDARY);
        }

        // Page break
        XWPFParagraph breakPara = document.createParagraph();
        breakPara.createRun().addBreak(BreakType.PAGE);
    }

    /**
     * Creates the ComplexTypes section.
     */
    private void createComplexTypesSection(XWPFDocument document) {
        createHeading(document, "Complex Types", 1);

        List<Node> complexTypes = documentationData.getGlobalComplexTypes();

        if (complexTypes.isEmpty()) {
            XWPFParagraph emptyPara = document.createParagraph();
            XWPFRun emptyRun = emptyPara.createRun();
            emptyRun.setText("No global ComplexTypes defined in this schema.");
            emptyRun.setItalic(true);
            return;
        }

        // Summary table
        XWPFTable summaryTable = document.createTable(complexTypes.size() + 1, 3);
        setTableStyle(summaryTable);
        setColumnWidths(summaryTable, new int[]{3500, 3500, 2000});

        // Header row
        setTableCell(summaryTable.getRow(0), 0, "Type Name", true, COLOR_HEADER_BG);
        setTableCell(summaryTable.getRow(0), 1, "Base Type", true, COLOR_HEADER_BG);
        setTableCell(summaryTable.getRow(0), 2, "Usage Count", true, COLOR_HEADER_BG);

        int row = 1;
        for (Node typeNode : complexTypes) {
            if (typeNode instanceof Element element) {
                String typeName = element.getAttribute("name");
                String baseType = getBaseType(element);
                int usageCount = getTypeUsageCount(typeName);

                setTableCell(summaryTable.getRow(row), 0, typeName, false);
                setTableCell(summaryTable.getRow(row), 1, baseType != null ? baseType : "-", false);
                setTableCell(summaryTable.getRow(row), 2, String.valueOf(usageCount), false);
                row++;
            }
        }

        // Page break
        XWPFParagraph breakPara = document.createParagraph();
        breakPara.createRun().addBreak(BreakType.PAGE);
    }

    /**
     * Creates the SimpleTypes section.
     */
    private void createSimpleTypesSection(XWPFDocument document) {
        createHeading(document, "Simple Types", 1);

        List<Node> simpleTypes = documentationData.getGlobalSimpleTypes();

        if (simpleTypes.isEmpty()) {
            XWPFParagraph emptyPara = document.createParagraph();
            XWPFRun emptyRun = emptyPara.createRun();
            emptyRun.setText("No global SimpleTypes defined in this schema.");
            emptyRun.setItalic(true);
            return;
        }

        // Summary table
        XWPFTable summaryTable = document.createTable(simpleTypes.size() + 1, 3);
        setTableStyle(summaryTable);
        setColumnWidths(summaryTable, new int[]{3500, 3500, 2000});

        // Header row
        setTableCell(summaryTable.getRow(0), 0, "Type Name", true, COLOR_HEADER_BG);
        setTableCell(summaryTable.getRow(0), 1, "Base Type", true, COLOR_HEADER_BG);
        setTableCell(summaryTable.getRow(0), 2, "Facets", true, COLOR_HEADER_BG);

        int row = 1;
        for (Node typeNode : simpleTypes) {
            if (typeNode instanceof Element element) {
                String typeName = element.getAttribute("name");
                String baseType = getSimpleTypeBase(element);
                String facets = getSimpleTypeFacetsSummary(element);

                setTableCell(summaryTable.getRow(row), 0, typeName, false);
                setTableCell(summaryTable.getRow(row), 1, baseType != null ? baseType : "-", false);
                setTableCell(summaryTable.getRow(row), 2, facets, false);
                row++;
            }
        }

        // Page break
        XWPFParagraph breakPara = document.createParagraph();
        breakPara.createRun().addBreak(BreakType.PAGE);
    }

    /**
     * Creates the Data Dictionary section.
     */
    private void createDataDictionarySection(XWPFDocument document) {
        createHeading(document, "Data Dictionary", 1);

        Map<String, XsdExtendedElement> elementMap = documentationData.getExtendedXsdElementMap();

        if (elementMap.isEmpty()) {
            XWPFParagraph emptyPara = document.createParagraph();
            XWPFRun emptyRun = emptyPara.createRun();
            emptyRun.setText("No elements documented in this schema.");
            emptyRun.setItalic(true);
            return;
        }

        // Sort elements by clean XPath and filter out container elements
        List<XsdExtendedElement> sortedElements = new ArrayList<>(elementMap.values());
        // Filter out container elements (SEQUENCE, CHOICE, ALL) - they are internal structures
        sortedElements.removeIf(this::isContainerElement);
        sortedElements.sort(Comparator.comparing(this::getCleanXPath));

        int totalElements = sortedElements.size();
        logger.info("Creating Word data dictionary with {} elements (excluding container elements)", totalElements);

        XWPFTable dictTable = document.createTable(totalElements + 1, 4);
        setTableStyle(dictTable);
        setColumnWidths(dictTable, new int[]{3000, 2000, 2000, 2000});

        // Header row
        setTableCell(dictTable.getRow(0), 0, "Element Path", true, COLOR_HEADER_BG);
        setTableCell(dictTable.getRow(0), 1, "Type", true, COLOR_HEADER_BG);
        setTableCell(dictTable.getRow(0), 2, "Cardinality", true, COLOR_HEADER_BG);
        setTableCell(dictTable.getRow(0), 3, "Description", true, COLOR_HEADER_BG);

        int row = 1;
        int progressInterval = Math.max(1, totalElements / 10); // Report progress every 10%
        for (int i = 0; i < totalElements; i++) {
            XsdExtendedElement element = sortedElements.get(i);

            // Use clean XPath without container elements
            String path = getCleanXPath(element);
            String type = element.getElementType() != null ? element.getElementType() : "-";
            String cardinality = getCardinality(element);
            String description = getFirstDocumentation(element);

            // Truncate long values for table display
            if (path != null && path.length() > 50) {
                path = "..." + path.substring(path.length() - 47);
            }
            if (description != null && description.length() > 60) {
                description = description.substring(0, 57) + "...";
            }

            setTableCell(dictTable.getRow(row), 0, path != null ? path : "-", false);
            setTableCell(dictTable.getRow(row), 1, type, false);
            setTableCell(dictTable.getRow(row), 2, cardinality != null ? cardinality : "1", false);
            setTableCell(dictTable.getRow(row), 3, description != null ? description : "-", false);
            row++;

            // Report progress for large documents
            if (i > 0 && i % progressInterval == 0) {
                reportProgress("Processing element " + i + " of " + totalElements);
            }
        }

        logger.info("Word data dictionary completed with {} elements", totalElements);
    }

    /**
     * Creates the Element Diagrams section with SVG diagrams for each significant element.
     * Only includes elements that have child elements (to avoid trivial diagrams).
     */
    private void createElementDiagramsSection(XWPFDocument document) {
        createHeading(document, "Element Diagrams", 1);

        Map<String, XsdExtendedElement> elementMap = documentationData.getExtendedXsdElementMap();

        // Filter elements that have children (diagrams make sense for these)
        List<XsdExtendedElement> elementsWithDiagrams = new ArrayList<>();
        for (XsdExtendedElement element : elementMap.values()) {
            // Skip container elements
            if (isContainerElement(element)) {
                continue;
            }
            // Only include elements that have children or are at level 0-2
            if (element.getLevel() <= 2 && hasSignificantContent(element, elementMap)) {
                elementsWithDiagrams.add(element);
            }
        }

        // Sort by level then by path
        elementsWithDiagrams.sort(Comparator
                .comparingInt(XsdExtendedElement::getLevel)
                .thenComparing(this::getCleanXPath));

        if (elementsWithDiagrams.isEmpty()) {
            XWPFParagraph emptyPara = document.createParagraph();
            XWPFRun emptyRun = emptyPara.createRun();
            emptyRun.setText("No element diagrams available.");
            emptyRun.setItalic(true);
            return;
        }

        logger.info("Generating {} element diagrams for Word document", elementsWithDiagrams.size());

        int diagramCount = 0;
        int progressInterval = Math.max(1, elementsWithDiagrams.size() / 10);

        for (int i = 0; i < elementsWithDiagrams.size(); i++) {
            XsdExtendedElement element = elementsWithDiagrams.get(i);

            try {
                // Create element heading
                createHeading(document, element.getElementName(), 2);

                // Add element info
                XWPFParagraph infoPara = document.createParagraph();
                XWPFRun infoRun = infoPara.createRun();
                infoRun.setText("Path: " + getCleanXPath(element));
                infoRun.setFontSize(10);
                infoRun.setColor(COLOR_SECONDARY);

                if (element.getElementType() != null) {
                    infoPara = document.createParagraph();
                    infoRun = infoPara.createRun();
                    infoRun.setText("Type: " + element.getElementType());
                    infoRun.setFontSize(10);
                    infoRun.setColor(COLOR_SECONDARY);
                }

                // Generate and embed diagram
                Path tempPng = Files.createTempFile("xsd-element-", ".png");
                String imagePath = imageService.generateImage(element, tempPng.toFile());

                if (imagePath != null) {
                    XWPFParagraph imgPara = document.createParagraph();
                    imgPara.setAlignment(ParagraphAlignment.CENTER);
                    XWPFRun imgRun = imgPara.createRun();

                    try (InputStream is = Files.newInputStream(tempPng)) {
                        // Insert the diagram image (max width 450px for page fit)
                        imgRun.addPicture(is, XWPFDocument.PICTURE_TYPE_PNG,
                                element.getElementName() + "-diagram.png",
                                Units.toEMU(450), Units.toEMU(250));
                    }

                    // Clean up temp file
                    Files.deleteIfExists(tempPng);
                    diagramCount++;

                    // Add caption
                    XWPFParagraph captionPara = document.createParagraph();
                    captionPara.setAlignment(ParagraphAlignment.CENTER);
                    XWPFRun captionRun = captionPara.createRun();
                    captionRun.setText("Figure: Structure of " + element.getElementName());
                    captionRun.setItalic(true);
                    captionRun.setFontSize(9);
                    captionRun.setColor(COLOR_SECONDARY);
                }

                // Add some spacing
                document.createParagraph();

            } catch (Exception e) {
                logger.warn("Failed to generate diagram for element {}: {}", element.getElementName(), e.getMessage());
            }

            // Report progress
            if (i > 0 && i % progressInterval == 0) {
                reportProgress("Generating diagram " + i + " of " + elementsWithDiagrams.size());
            }
        }

        logger.info("Word element diagrams completed: {} diagrams generated", diagramCount);
    }

    /**
     * Checks if an element has significant content (children or attributes) worth showing in a diagram.
     */
    private boolean hasSignificantContent(XsdExtendedElement element, Map<String, XsdExtendedElement> elementMap) {
        String elementXpath = element.getXpath();
        if (elementXpath == null) {
            return false;
        }

        // Check if any element has this element as parent
        for (XsdExtendedElement child : elementMap.values()) {
            if (elementXpath.equals(child.getParentXpath())) {
                return true;
            }
        }
        return false;
    }

    // ================= Helper Methods =================

    private void createHeading(XWPFDocument document, String text, int level) {
        XWPFParagraph para = document.createParagraph();
        para.setStyle("Heading" + level);
        para.setSpacingBefore(200);
        para.setSpacingAfter(120);

        XWPFRun run = para.createRun();
        run.setText(text);
        run.setBold(true);

        switch (level) {
            case 1 -> {
                run.setFontSize(18);
                run.setColor(COLOR_PRIMARY);
            }
            case 2 -> {
                run.setFontSize(14);
                run.setColor(COLOR_PRIMARY);
            }
            default -> {
                run.setFontSize(12);
                run.setColor(COLOR_SECONDARY);
            }
        }
    }

    private void setTableStyle(XWPFTable table) {
        table.setWidth("100%");
        CTTblPr tblPr = table.getCTTbl().getTblPr();
        if (tblPr == null) {
            tblPr = table.getCTTbl().addNewTblPr();
        }

        // Set table borders
        CTTblBorders borders = tblPr.addNewTblBorders();

        CTBorder border = borders.addNewTop();
        border.setVal(STBorder.SINGLE);
        border.setSz(BigInteger.valueOf(4));
        border.setColor(COLOR_TABLE_BORDER);

        border = borders.addNewBottom();
        border.setVal(STBorder.SINGLE);
        border.setSz(BigInteger.valueOf(4));
        border.setColor(COLOR_TABLE_BORDER);

        border = borders.addNewLeft();
        border.setVal(STBorder.SINGLE);
        border.setSz(BigInteger.valueOf(4));
        border.setColor(COLOR_TABLE_BORDER);

        border = borders.addNewRight();
        border.setVal(STBorder.SINGLE);
        border.setSz(BigInteger.valueOf(4));
        border.setColor(COLOR_TABLE_BORDER);

        border = borders.addNewInsideH();
        border.setVal(STBorder.SINGLE);
        border.setSz(BigInteger.valueOf(4));
        border.setColor(COLOR_TABLE_BORDER);

        border = borders.addNewInsideV();
        border.setVal(STBorder.SINGLE);
        border.setSz(BigInteger.valueOf(4));
        border.setColor(COLOR_TABLE_BORDER);
    }

    private void setColumnWidths(XWPFTable table, int[] widths) {
        for (XWPFTableRow row : table.getRows()) {
            for (int i = 0; i < widths.length && i < row.getTableCells().size(); i++) {
                XWPFTableCell cell = row.getCell(i);
                CTTcPr tcPr = cell.getCTTc().getTcPr();
                if (tcPr == null) {
                    tcPr = cell.getCTTc().addNewTcPr();
                }
                CTTblWidth width = tcPr.addNewTcW();
                width.setW(BigInteger.valueOf(widths[i]));
                width.setType(STTblWidth.DXA);
            }
        }
    }

    private void setTableCell(XWPFTableRow row, int cellIndex, String text, boolean isHeader) {
        setTableCell(row, cellIndex, text, isHeader, null);
    }

    private void setTableCell(XWPFTableRow row, int cellIndex, String text, boolean isHeader, String bgColor) {
        XWPFTableCell cell = row.getCell(cellIndex);
        cell.removeParagraph(0);
        XWPFParagraph para = cell.addParagraph();
        para.setSpacingBefore(40);
        para.setSpacingAfter(40);

        XWPFRun run = para.createRun();
        run.setText(text != null ? text : "");
        run.setFontSize(10);
        run.setFontFamily("Calibri");

        if (isHeader) {
            run.setBold(true);
        }

        if (bgColor != null) {
            CTTcPr tcPr = cell.getCTTc().getTcPr();
            if (tcPr == null) {
                tcPr = cell.getCTTc().addNewTcPr();
            }
            CTShd shd = tcPr.addNewShd();
            shd.setFill(bgColor);
        }
    }

    private void addParagraphSpacing(XWPFDocument document) {
        XWPFParagraph para = document.createParagraph();
        para.setSpacingAfter(200);
    }

    private String extractSchemaName() {
        String filePath = documentationData.getXsdFilePath();
        if (filePath == null) {
            return "Unknown Schema";
        }
        Path path = Path.of(filePath);
        String fileName = path.getFileName().toString();
        if (fileName.endsWith(".xsd")) {
            return fileName.substring(0, fileName.length() - 4);
        }
        return fileName;
    }

    private String getBaseType(Element complexTypeElement) {
        // Look for xs:complexContent/xs:extension or xs:complexContent/xs:restriction
        var childNodes = complexTypeElement.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            if (child instanceof Element childElement) {
                String localName = childElement.getLocalName();
                if ("complexContent".equals(localName) || "simpleContent".equals(localName)) {
                    var contentChildren = childElement.getChildNodes();
                    for (int j = 0; j < contentChildren.getLength(); j++) {
                        Node contentChild = contentChildren.item(j);
                        if (contentChild instanceof Element contentElement) {
                            String contentLocalName = contentElement.getLocalName();
                            if ("extension".equals(contentLocalName) || "restriction".equals(contentLocalName)) {
                                return contentElement.getAttribute("base");
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private String getSimpleTypeBase(Element simpleTypeElement) {
        var childNodes = simpleTypeElement.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            if (child instanceof Element childElement) {
                String localName = childElement.getLocalName();
                if ("restriction".equals(localName)) {
                    return childElement.getAttribute("base");
                } else if ("list".equals(localName)) {
                    return "list(" + childElement.getAttribute("itemType") + ")";
                } else if ("union".equals(localName)) {
                    return "union(" + childElement.getAttribute("memberTypes") + ")";
                }
            }
        }
        return null;
    }

    private String getSimpleTypeFacetsSummary(Element simpleTypeElement) {
        List<String> facets = new ArrayList<>();
        var childNodes = simpleTypeElement.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            if (child instanceof Element childElement && "restriction".equals(childElement.getLocalName())) {
                var facetNodes = childElement.getChildNodes();
                for (int j = 0; j < facetNodes.getLength(); j++) {
                    Node facetNode = facetNodes.item(j);
                    if (facetNode instanceof Element facetElement) {
                        String facetName = facetElement.getLocalName();
                        if (facetName != null && !facetName.equals("annotation")) {
                            String facetValue = facetElement.getAttribute("value");
                            if (facetValue != null && !facetValue.isEmpty()) {
                                facets.add(facetName + ": " + facetValue);
                            } else {
                                facets.add(facetName);
                            }
                        }
                    }
                }
            }
        }
        return facets.isEmpty() ? "-" : String.join(", ", facets);
    }

    private int getTypeUsageCount(String typeName) {
        var usageMap = documentationData.getTypeUsageMap();
        if (usageMap != null && usageMap.containsKey(typeName)) {
            return usageMap.get(typeName).size();
        }
        return 0;
    }

    private String getFirstDocumentation(XsdExtendedElement element) {
        Map<String, String> docs = element.getLanguageDocumentation();
        if (docs != null && !docs.isEmpty()) {
            // Prefer default or first available
            String doc = docs.get("default");
            if (doc == null) {
                doc = docs.values().iterator().next();
            }
            // Strip HTML tags
            return doc.replaceAll("<[^>]*>", "").trim();
        }
        return null;
    }

    private void reportProgress(String message) {
        if (progressListener != null) {
            progressListener.onProgressUpdate(new TaskProgressListener.ProgressUpdate(message,
                    TaskProgressListener.ProgressUpdate.Status.RUNNING, 0));
        }
        logger.debug("Word generation progress: {}", message);
    }

    /**
     * Gets cardinality string from minOccurs/maxOccurs attributes.
     */
    private String getCardinality(XsdExtendedElement element) {
        Node node = element.getCardinalityNode();
        if (node == null) {
            node = element.getCurrentNode();
        }
        if (node == null) {
            return "1";
        }

        String minOccurs = getNodeAttribute(node, "minOccurs", "1");
        String maxOccurs = getNodeAttribute(node, "maxOccurs", "1");

        if (minOccurs.equals(maxOccurs)) {
            return minOccurs;
        }
        if ("unbounded".equals(maxOccurs)) {
            return minOccurs + "..*";
        }
        return minOccurs + ".." + maxOccurs;
    }

    /**
     * Checks if an element is a compositor container (CHOICE, SEQUENCE, ALL).
     * These container elements are internal structures and should not appear in Word documentation.
     */
    private boolean isContainerElement(XsdExtendedElement element) {
        if (element == null || element.getElementName() == null) {
            return false;
        }
        String elementName = element.getElementName();
        return elementName.startsWith("CHOICE") ||
               elementName.startsWith("SEQUENCE") ||
               elementName.startsWith("ALL");
    }

    /**
     * Returns the XPath of an element without container elements (CHOICE, SEQUENCE, ALL).
     * This produces a clean XPath for display in documentation.
     */
    private String getCleanXPath(XsdExtendedElement element) {
        if (element == null) {
            return "";
        }

        List<String> pathParts = new ArrayList<>();
        XsdExtendedElement current = element;

        // Traverse up the hierarchy and collect only non-container elements
        while (current != null) {
            if (!isContainerElement(current)) {
                pathParts.add(0, current.getElementName());
            }
            String parentXpath = current.getParentXpath();
            current = (parentXpath != null) ?
                    documentationData.getExtendedXsdElementMap().get(parentXpath) : null;
        }

        return "/" + String.join("/", pathParts);
    }

    /**
     * Gets attribute value from a Node with default fallback.
     */
    private String getNodeAttribute(Node node, String attrName, String defaultValue) {
        if (node instanceof Element elem) {
            String value = elem.getAttribute(attrName);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return defaultValue;
    }

    // ================= Setters =================

    public void setDocumentationData(XsdDocumentationData documentationData) {
        this.documentationData = documentationData;
    }

    public void setImageService(XsdDocumentationImageService imageService) {
        this.imageService = imageService;
    }

    public void setIncludedLanguages(Set<String> includedLanguages) {
        this.includedLanguages = includedLanguages;
    }

    public void setProgressListener(TaskProgressListener progressListener) {
        this.progressListener = progressListener;
    }

    public void setConfig(WordDocumentationConfig config) {
        this.config = config != null ? config : new WordDocumentationConfig();
    }

    public WordDocumentationConfig getConfig() {
        return config;
    }
}
