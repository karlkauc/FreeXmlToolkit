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
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.*;
import org.fxt.freexmltoolkit.domain.WordDocumentationConfig;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

    // Style color definitions for different HeaderStyles
    private static final String[][] STYLE_COLORS = {
            // PROFESSIONAL: Blue/Slate theme (index 0)
            {"2563EB", "64748B", "F1F5F9", "CBD5E1"},  // primary, secondary, headerBg, tableBorder
            // MINIMAL: Black/Gray theme (index 1)
            {"374151", "6B7280", "F9FAFB", "E5E7EB"},  // dark gray, medium gray, very light, light border
            // COLORFUL: Purple/Pink theme (index 2)
            {"7C3AED", "EC4899", "FEF3C7", "DDD6FE"}   // purple, pink, light yellow, light purple
    };

    private static final int TABLE_WIDTH_TWIPS = 9000;

    private XsdDocumentationData documentationData;
    private XsdDocumentationImageService imageService;
    private Set<String> includedLanguages;
    private TaskProgressListener progressListener;
    private WordDocumentationConfig config = new WordDocumentationConfig();

    // ================= Style Color Helpers =================

    /**
     * Gets the style color array index based on the configured HeaderStyle.
     */
    private int getStyleIndex() {
        if (config == null || config.getHeaderStyle() == null) {
            return 0; // Default to PROFESSIONAL
        }
        return switch (config.getHeaderStyle()) {
            case PROFESSIONAL -> 0;
            case MINIMAL -> 1;
            case COLORFUL -> 2;
        };
    }

    /**
     * Gets the primary color for headings based on the configured HeaderStyle.
     */
    private String getColorPrimary() {
        return STYLE_COLORS[getStyleIndex()][0];
    }

    /**
     * Gets the secondary color for captions and metadata based on the configured HeaderStyle.
     */
    private String getColorSecondary() {
        return STYLE_COLORS[getStyleIndex()][1];
    }

    /**
     * Gets the header background color for tables based on the configured HeaderStyle.
     */
    private String getColorHeaderBg() {
        return STYLE_COLORS[getStyleIndex()][2];
    }

    /**
     * Gets the table border color based on the configured HeaderStyle.
     */
    private String getColorTableBorder() {
        return STYLE_COLORS[getStyleIndex()][3];
    }

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

            // Create header and footer with page numbers
            createHeaderFooter(document);

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

            // Create namespace overview
            reportProgress("Creating namespace overview");
            createNamespaceOverview(document);

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

            // Create Index section at the end
            reportProgress("Creating Index");
            createIndexSection(document);

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
     * Creates header and footer for the document with page numbers.
     * Header shows the schema name, footer shows page numbers and generation info.
     */
    private void createHeaderFooter(XWPFDocument document) {
        try {
            // Create header/footer policy
            XWPFHeaderFooterPolicy policy = document.createHeaderFooterPolicy();

            // Create header with schema name
            XWPFHeader header = policy.createHeader(XWPFHeaderFooterPolicy.DEFAULT);
            XWPFParagraph headerPara = header.createParagraph();
            headerPara.setAlignment(ParagraphAlignment.RIGHT);

            XWPFRun headerRun = headerPara.createRun();
            String schemaName = extractSchemaName();
            headerRun.setText("XSD Documentation: " + schemaName);
            headerRun.setFontSize(9);
            headerRun.setColor(getColorSecondary());
            headerRun.setItalic(true);

            // Create footer with page numbers
            XWPFFooter footer = policy.createFooter(XWPFHeaderFooterPolicy.DEFAULT);
            XWPFParagraph footerPara = footer.createParagraph();
            footerPara.setAlignment(ParagraphAlignment.CENTER);

            // Add "Page X of Y" with field codes
            XWPFRun footerRun = footerPara.createRun();
            footerRun.setText("Page ");
            footerRun.setFontSize(9);
            footerRun.setColor(getColorSecondary());

            // PAGE field
            footerPara.getCTP().addNewFldSimple().setInstr("PAGE");

            XWPFRun footerRun2 = footerPara.createRun();
            footerRun2.setText(" of ");
            footerRun2.setFontSize(9);
            footerRun2.setColor(getColorSecondary());

            // NUMPAGES field
            footerPara.getCTP().addNewFldSimple().setInstr("NUMPAGES");

            // Add generation info line
            XWPFParagraph footerLine2 = footer.createParagraph();
            footerLine2.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun genRun = footerLine2.createRun();
            genRun.setText("Generated by FreeXmlToolkit");
            genRun.setFontSize(8);
            genRun.setColor(getColorSecondary());

        } catch (Exception e) {
            logger.warn("Failed to create header/footer: {}", e.getMessage());
        }
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
        titleRun.setColor(getColorPrimary());
        titleRun.setFontFamily("Calibri");

        // Schema name
        String schemaName = extractSchemaName();
        XWPFParagraph schemaPara = document.createParagraph();
        schemaPara.setAlignment(ParagraphAlignment.CENTER);
        schemaPara.setSpacingAfter(600);

        XWPFRun schemaRun = schemaPara.createRun();
        schemaRun.setText(schemaName);
        schemaRun.setFontSize(18);
        schemaRun.setColor(getColorSecondary());
        schemaRun.setFontFamily("Calibri");

        // Generated date
        XWPFParagraph datePara = document.createParagraph();
        datePara.setAlignment(ParagraphAlignment.CENTER);
        datePara.setSpacingAfter(200);

        XWPFRun dateRun = datePara.createRun();
        dateRun.setText("Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        dateRun.setFontSize(11);
        dateRun.setItalic(true);
        dateRun.setColor(getColorSecondary());

        // Generator info
        XWPFParagraph genPara = document.createParagraph();
        genPara.setAlignment(ParagraphAlignment.CENTER);

        XWPFRun genRun = genPara.createRun();
        genRun.setText("Generated by FreeXmlToolkit");
        genRun.setFontSize(10);
        genRun.setColor(getColorSecondary());

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
        instrRun.setColor(getColorSecondary());

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
     * Creates the namespace overview section showing all namespaces used in the schema.
     */
    private void createNamespaceOverview(XWPFDocument document) {
        Map<String, String> namespaces = documentationData.getNamespaces();

        // Skip if no namespaces are defined
        if (namespaces == null || namespaces.isEmpty()) {
            return;
        }

        createHeading(document, "Namespace Overview", 2);

        // Create table for namespaces
        int rowCount = namespaces.size() + 1;
        XWPFTable nsTable = document.createTable(rowCount, 2);
        setTableStyle(nsTable);
        setColumnWidths(nsTable, new int[]{2500, 6500});

        // Header row
        setTableCellWithZebra(nsTable.getRow(0), 0, "Prefix", 0);
        setTableCellWithZebra(nsTable.getRow(0), 1, "Namespace URI", 0);

        // Sort namespaces by prefix for consistent output
        List<Map.Entry<String, String>> sortedNamespaces = new ArrayList<>(namespaces.entrySet());
        sortedNamespaces.sort(Map.Entry.comparingByKey());

        int row = 1;
        for (Map.Entry<String, String> entry : sortedNamespaces) {
            String prefix = entry.getKey();
            String uri = entry.getValue();

            // Mark default namespace
            if (prefix == null || prefix.isEmpty()) {
                prefix = "(default)";
            }

            setTableCellWithZebra(nsTable.getRow(row), 0, prefix, row);
            setTableCellWithZebra(nsTable.getRow(row), 1, uri, row);
            row++;
        }

        addParagraphSpacing(document);
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
                    captionRun.setColor(getColorSecondary());
                }
            } catch (IOException | InvalidFormatException e) {
                logger.warn("Failed to embed schema diagram: {}", e.getMessage());

                XWPFParagraph errorPara = document.createParagraph();
                XWPFRun errorRun = errorPara.createRun();
                errorRun.setText("(Schema diagram could not be generated)");
                errorRun.setItalic(true);
                errorRun.setColor(getColorSecondary());
            }
        } else {
            XWPFParagraph noDiagramPara = document.createParagraph();
            XWPFRun noDiagramRun = noDiagramPara.createRun();
            noDiagramRun.setText("(No diagram available)");
            noDiagramRun.setItalic(true);
            noDiagramRun.setColor(getColorSecondary());
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
        setTableCell(summaryTable.getRow(0), 0, "Type Name", true, getColorHeaderBg());
        setTableCell(summaryTable.getRow(0), 1, "Base Type", true, getColorHeaderBg());
        setTableCell(summaryTable.getRow(0), 2, "Usage Count", true, getColorHeaderBg());

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
        setTableCell(summaryTable.getRow(0), 0, "Type Name", true, getColorHeaderBg());
        setTableCell(summaryTable.getRow(0), 1, "Base Type", true, getColorHeaderBg());
        setTableCell(summaryTable.getRow(0), 2, "Facets", true, getColorHeaderBg());

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

        XWPFTable dictTable = document.createTable(totalElements + 1, 5);
        setTableStyle(dictTable);
        setColumnWidths(dictTable, new int[]{2500, 1800, 1200, 1800, 1700});

        // Header row (row index 0)
        setTableCellWithZebra(dictTable.getRow(0), 0, "Element Path", 0);
        setTableCellWithZebra(dictTable.getRow(0), 1, "Type", 0);
        setTableCellWithZebra(dictTable.getRow(0), 2, "Cardinality", 0);
        setTableCellWithZebra(dictTable.getRow(0), 3, "Restrictions", 0);
        setTableCellWithZebra(dictTable.getRow(0), 4, "Description", 0);

        int row = 1;
        int progressInterval = Math.max(1, totalElements / 10); // Report progress every 10%
        for (int i = 0; i < totalElements; i++) {
            XsdExtendedElement element = sortedElements.get(i);

            // Use clean XPath without container elements
            String path = getCleanXPath(element);
            String type = element.getElementType() != null ? element.getElementType() : "-";
            String cardinality = getCardinality(element);
            String restrictions = getRestrictionsSummary(element);
            String description = getFirstDocumentation(element);

            // Truncate long values for table display
            if (path != null && path.length() > 40) {
                path = "..." + path.substring(path.length() - 37);
            }
            if (restrictions != null && restrictions.length() > 35) {
                restrictions = restrictions.substring(0, 32) + "...";
            }
            if (description != null && description.length() > 40) {
                description = description.substring(0, 37) + "...";
            }

            // Use zebra striping for better readability
            setTableCellWithZebra(dictTable.getRow(row), 0, path != null ? path : "-", row);
            setTableCellWithZebra(dictTable.getRow(row), 1, type, row);
            setTableCellWithZebra(dictTable.getRow(row), 2, cardinality != null ? cardinality : "1", row);
            setTableCellWithZebra(dictTable.getRow(row), 3, restrictions != null ? restrictions : "-", row);
            setTableCellWithZebra(dictTable.getRow(row), 4, description != null ? description : "-", row);
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
                infoRun.setColor(getColorSecondary());

                if (element.getElementType() != null) {
                    infoPara = document.createParagraph();
                    infoRun = infoPara.createRun();
                    infoRun.setText("Type: " + element.getElementType());
                    infoRun.setFontSize(10);
                    infoRun.setColor(getColorSecondary());
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
                    captionRun.setColor(getColorSecondary());
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
        String elementXpath = element.getCurrentXpath();
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

    /**
     * Creates an alphabetical index of all types and elements at the end of the document.
     */
    private void createIndexSection(XWPFDocument document) {
        // Page break before index
        XWPFParagraph breakPara = document.createParagraph();
        breakPara.createRun().addBreak(BreakType.PAGE);

        createHeading(document, "Index", 1);

        // Collect all items for the index
        List<String> indexItems = new ArrayList<>();

        // Add global elements
        for (Node node : documentationData.getGlobalElements()) {
            if (node instanceof Element elem) {
                String name = elem.getAttribute("name");
                if (name != null && !name.isEmpty()) {
                    indexItems.add(name + " (Element)");
                }
            }
        }

        // Add complex types
        for (Node node : documentationData.getGlobalComplexTypes()) {
            if (node instanceof Element elem) {
                String name = elem.getAttribute("name");
                if (name != null && !name.isEmpty()) {
                    indexItems.add(name + " (ComplexType)");
                }
            }
        }

        // Add simple types
        for (Node node : documentationData.getGlobalSimpleTypes()) {
            if (node instanceof Element elem) {
                String name = elem.getAttribute("name");
                if (name != null && !name.isEmpty()) {
                    indexItems.add(name + " (SimpleType)");
                }
            }
        }

        // Sort alphabetically (case-insensitive)
        indexItems.sort(String.CASE_INSENSITIVE_ORDER);

        if (indexItems.isEmpty()) {
            XWPFParagraph emptyPara = document.createParagraph();
            XWPFRun emptyRun = emptyPara.createRun();
            emptyRun.setText("No items to index.");
            emptyRun.setItalic(true);
            return;
        }

        // Create index in two columns using a table
        int itemsPerColumn = (indexItems.size() + 1) / 2;
        XWPFTable indexTable = document.createTable(itemsPerColumn, 2);
        setColumnWidths(indexTable, new int[]{4500, 4500});

        // Remove table borders for cleaner look
        CTTblPr tblPr = indexTable.getCTTbl().getTblPr();
        if (tblPr == null) {
            tblPr = indexTable.getCTTbl().addNewTblPr();
        }
        CTTblBorders borders = tblPr.addNewTblBorders();
        borders.addNewTop().setVal(STBorder.NIL);
        borders.addNewBottom().setVal(STBorder.NIL);
        borders.addNewLeft().setVal(STBorder.NIL);
        borders.addNewRight().setVal(STBorder.NIL);
        borders.addNewInsideH().setVal(STBorder.NIL);
        borders.addNewInsideV().setVal(STBorder.NIL);

        for (int i = 0; i < itemsPerColumn; i++) {
            XWPFTableRow row = indexTable.getRow(i);

            // Left column
            if (i < indexItems.size()) {
                setTableCell(row, 0, indexItems.get(i), false);
            }

            // Right column
            int rightIndex = i + itemsPerColumn;
            if (rightIndex < indexItems.size()) {
                setTableCell(row, 1, indexItems.get(rightIndex), false);
            }
        }

        logger.info("Index section created with {} items", indexItems.size());
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
                run.setColor(getColorPrimary());
            }
            case 2 -> {
                run.setFontSize(14);
                run.setColor(getColorPrimary());
            }
            default -> {
                run.setFontSize(12);
                run.setColor(getColorSecondary());
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
        border.setColor(getColorTableBorder());

        border = borders.addNewBottom();
        border.setVal(STBorder.SINGLE);
        border.setSz(BigInteger.valueOf(4));
        border.setColor(getColorTableBorder());

        border = borders.addNewLeft();
        border.setVal(STBorder.SINGLE);
        border.setSz(BigInteger.valueOf(4));
        border.setColor(getColorTableBorder());

        border = borders.addNewRight();
        border.setVal(STBorder.SINGLE);
        border.setSz(BigInteger.valueOf(4));
        border.setColor(getColorTableBorder());

        border = borders.addNewInsideH();
        border.setVal(STBorder.SINGLE);
        border.setSz(BigInteger.valueOf(4));
        border.setColor(getColorTableBorder());

        border = borders.addNewInsideV();
        border.setVal(STBorder.SINGLE);
        border.setSz(BigInteger.valueOf(4));
        border.setColor(getColorTableBorder());
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

    /**
     * Sets a table cell with zebra striping (alternating row colors).
     * Even rows (index 0, 2, 4...) get the primary background, odd rows get a light tint.
     *
     * @param row       the table row
     * @param cellIndex the cell index in the row
     * @param text      the text to set
     * @param rowIndex  the row index (0-based, where 0 is the header row)
     */
    private void setTableCellWithZebra(XWPFTableRow row, int cellIndex, String text, int rowIndex) {
        // Header row (index 0) gets header background
        // Data rows: even indices get very light background, odd get white
        String bgColor = null;
        if (rowIndex == 0) {
            bgColor = getColorHeaderBg();
        } else if (rowIndex % 2 == 0) {
            // Even data rows get a very light tint based on style
            bgColor = getZebraStripeColor();
        }
        setTableCell(row, cellIndex, text, rowIndex == 0, bgColor);
    }

    /**
     * Gets the zebra stripe background color (lighter than header background).
     */
    private String getZebraStripeColor() {
        return switch (config.getHeaderStyle()) {
            case PROFESSIONAL -> "F8FAFC";  // Very light blue-gray
            case MINIMAL -> "FAFAFA";       // Very light gray
            case COLORFUL -> "FEFCE8";      // Very light yellow
        };
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

    /**
     * Gets a summary of restrictions/facets for an element.
     * Uses the restriction string from the element if available.
     */
    private String getRestrictionsSummary(XsdExtendedElement element) {
        if (element == null) {
            return null;
        }

        // Get the restriction string from the element
        String restrictionString = element.getXsdRestrictionString();
        if (restrictionString != null && !restrictionString.isEmpty()) {
            // Clean up and format the restriction string
            return restrictionString.replaceAll("\\s+", " ").trim();
        }

        // Check for identity constraints
        var constraints = element.getIdentityConstraints();
        if (constraints != null && !constraints.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (var constraint : constraints) {
                if (sb.length() > 0) sb.append("; ");
                sb.append(constraint.getType()).append(": ").append(constraint.getName());
            }
            return sb.toString();
        }

        // Check for assertions (XSD 1.1)
        var assertions = element.getAssertions();
        if (assertions != null && !assertions.isEmpty()) {
            return assertions.size() + " assertion(s)";
        }

        return null;
    }

    private String getFirstDocumentation(XsdExtendedElement element) {
        Map<String, String> docs = element.getLanguageDocumentation();
        if (docs == null || docs.isEmpty()) {
            return null;
        }

        String doc = null;

        // If language filter is set, try to find documentation in preferred languages
        if (includedLanguages != null && !includedLanguages.isEmpty()) {
            for (String lang : includedLanguages) {
                if (docs.containsKey(lang)) {
                    doc = docs.get(lang);
                    break;
                }
            }
        }

        // Fallback: prefer "default" language, then any available
        if (doc == null) {
            doc = docs.get("default");
        }
        if (doc == null) {
            doc = docs.values().iterator().next();
        }

        // Strip HTML tags and return
        return doc.replaceAll("<[^>]*>", "").trim();
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
