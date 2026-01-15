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

import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.PdfDocumentationConfig;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service for generating PDF documentation from XSD schema data using Apache FOP.
 * Creates an intermediate XML document that is transformed via XSL-FO to produce
 * a professional PDF document.
 */
public class XsdDocumentationPdfService {

    private static final Logger logger = LogManager.getLogger(XsdDocumentationPdfService.class);

    private static final String XSL_FO_TEMPLATE_PATH = "/xsdDocumentation/pdf/xsd-documentation.xsl";

    private XsdDocumentationData documentationData;
    private XsdDocumentationImageService imageService;
    private Set<String> includedLanguages;
    private TaskProgressListener progressListener;
    private PdfDocumentationConfig config = new PdfDocumentationConfig();

    // Cached FopFactory for performance
    private static final FopFactory fopFactory;

    static {
        try {
            fopFactory = FopFactory.newInstance(new File(".").toURI());
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize FopFactory", e);
        }
    }

    /**
     * Generates a PDF document from the XSD documentation data.
     *
     * @param outputFile        the output .pdf file
     * @param documentationData the parsed XSD documentation data
     * @throws FOPServiceException if PDF generation fails
     */
    // Temporary directory for storing generated images
    private Path tempImageDir;

    public void generatePdfDocumentation(File outputFile, XsdDocumentationData documentationData)
            throws FOPServiceException {
        this.documentationData = documentationData;

        try {
            // Create temp directory for images if element diagrams are enabled
            if (config.isIncludeElementDiagrams() && imageService != null) {
                tempImageDir = Files.createTempDirectory("xsd-pdf-images-");
                reportProgress("Generating element diagrams");
            }

            reportProgress("Creating intermediate XML");
            Document intermediateXml = createIntermediateXml();

            reportProgress("Loading XSL-FO template");
            Source xslSource = loadXslFoTemplate();

            reportProgress("Generating PDF");
            transformToPdf(intermediateXml, xslSource, outputFile);

            logger.info("PDF documentation generated successfully: {}", outputFile.getAbsolutePath());

        } catch (Exception e) {
            logger.error("Failed to generate PDF documentation", e);
            throw new FOPServiceException("PDF generation failed: " + e.getMessage(), e);
        } finally {
            // Cleanup temp images
            cleanupTempImages();
        }
    }

    /**
     * Cleans up temporary image files after PDF generation.
     */
    private void cleanupTempImages() {
        if (tempImageDir != null) {
            try {
                Files.walk(tempImageDir)
                        .sorted(Comparator.reverseOrder()) // reverse order to delete files before dirs
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                logger.debug("Could not delete temp file: {}", path);
                            }
                        });
            } catch (IOException e) {
                logger.warn("Could not cleanup temp image directory: {}", e.getMessage());
            }
        }
    }

    /**
     * Creates an intermediate XML document from the XSD documentation data.
     * This XML structure is designed to be transformed by the XSL-FO template.
     */
    private Document createIntermediateXml() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        // Root element
        Element root = doc.createElement("xsd-documentation");
        doc.appendChild(root);

        // Metadata section
        Element metadata = doc.createElement("metadata");
        root.appendChild(metadata);

        addElement(doc, metadata, "schema-name", extractSchemaName());
        addElement(doc, metadata, "file-path", documentationData.getXsdFilePath());
        addElement(doc, metadata, "target-namespace",
                documentationData.getTargetNamespace() != null ? documentationData.getTargetNamespace() : "");
        addElement(doc, metadata, "version",
                documentationData.getVersion() != null ? documentationData.getVersion() : "");
        addElement(doc, metadata, "element-form-default",
                documentationData.getElementFormDefault() != null ? documentationData.getElementFormDefault() : "unqualified");
        addElement(doc, metadata, "attribute-form-default",
                documentationData.getAttributeFormDefault() != null ? documentationData.getAttributeFormDefault() : "unqualified");
        addElement(doc, metadata, "generation-date",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        addElement(doc, metadata, "generator", "FreeXmlToolkit");

        // Statistics section
        Element statistics = doc.createElement("statistics");
        root.appendChild(statistics);

        addElement(doc, statistics, "global-elements", String.valueOf(documentationData.getGlobalElements().size()));
        addElement(doc, statistics, "global-complex-types", String.valueOf(documentationData.getGlobalComplexTypes().size()));
        addElement(doc, statistics, "global-simple-types", String.valueOf(documentationData.getGlobalSimpleTypes().size()));
        addElement(doc, statistics, "total-elements", String.valueOf(documentationData.getExtendedXsdElementMap().size()));

        // Schema Diagram section (if configured)
        if (config.isIncludeSchemaDiagram() && imageService != null && tempImageDir != null) {
            Element schemaDiagramSection = doc.createElement("schema-diagram");
            root.appendChild(schemaDiagramSection);

            // Find root element (same logic as Word service)
            var elementMap = documentationData.getExtendedXsdElementMap();
            XsdExtendedElement rootElement = null;

            for (var element : elementMap.values()) {
                if (element.getLevel() == 0) {
                    rootElement = element;
                    break;
                }
            }

            if (rootElement == null && !elementMap.isEmpty()) {
                rootElement = elementMap.values().iterator().next();
            }

            if (rootElement != null) {
                try {
                    // Generate PNG image
                    Path imagePath = tempImageDir.resolve("schema-diagram.png");
                    String generatedPath = imageService.generateImage(rootElement, imagePath.toFile());

                    if (generatedPath != null) {
                        addElement(doc, schemaDiagramSection, "image-path", imagePath.toUri().toString());
                        addElement(doc, schemaDiagramSection, "root-element", rootElement.getElementName());
                        logger.info("Schema diagram generated for PDF document");
                    }
                } catch (Exception e) {
                    logger.warn("Failed to generate schema diagram: {}", e.getMessage());
                }
            }
        }

        // ComplexTypes section
        Element complexTypes = doc.createElement("complex-types");
        root.appendChild(complexTypes);

        for (Node typeNode : documentationData.getGlobalComplexTypes()) {
            if (typeNode instanceof org.w3c.dom.Element typeElement) {
                Element type = doc.createElement("complex-type");
                complexTypes.appendChild(type);

                String typeName = typeElement.getAttribute("name");
                addElement(doc, type, "name", typeName);
                addElement(doc, type, "base-type", getBaseType(typeElement));
                addElement(doc, type, "usage-count", String.valueOf(getTypeUsageCount(typeName)));
            }
        }

        // SimpleTypes section
        Element simpleTypes = doc.createElement("simple-types");
        root.appendChild(simpleTypes);

        for (Node typeNode : documentationData.getGlobalSimpleTypes()) {
            if (typeNode instanceof org.w3c.dom.Element typeElement) {
                Element type = doc.createElement("simple-type");
                simpleTypes.appendChild(type);

                String typeName = typeElement.getAttribute("name");
                addElement(doc, type, "name", typeName);
                addElement(doc, type, "base-type", getSimpleTypeBase(typeElement));
                addElement(doc, type, "facets", getSimpleTypeFacetsSummary(typeElement));
            }
        }

        // Data Dictionary section
        Element dataDictionary = doc.createElement("data-dictionary");
        root.appendChild(dataDictionary);

        Map<String, XsdExtendedElement> elementMap = documentationData.getExtendedXsdElementMap();
        List<XsdExtendedElement> sortedElements = new ArrayList<>(elementMap.values());
        // Filter out container elements (SEQUENCE, CHOICE, ALL) - they are internal structures
        sortedElements.removeIf(this::isContainerElement);
        sortedElements.sort(Comparator.comparing(this::getCleanXPath));

        int totalElements = sortedElements.size();
        logger.info("Creating PDF data dictionary with {} elements (excluding container elements)", totalElements);

        int progressInterval = Math.max(1, totalElements / 10); // Report progress every 10%
        for (int i = 0; i < totalElements; i++) {
            XsdExtendedElement xsdElement = sortedElements.get(i);
            Element entry = doc.createElement("element");
            dataDictionary.appendChild(entry);

            // Use clean XPath without container elements
            addElement(doc, entry, "path", truncateString(getCleanXPath(xsdElement), 80));
            addElement(doc, entry, "name", xsdElement.getElementName());
            addElement(doc, entry, "type", xsdElement.getElementType() != null ? xsdElement.getElementType() : "-");
            addElement(doc, entry, "cardinality", getCardinality(xsdElement));
            addElement(doc, entry, "level", String.valueOf(xsdElement.getLevel()));

            String doc1 = getFirstDocumentation(xsdElement);
            addElement(doc, entry, "description", truncateString(doc1, 100));

            // Report progress for large documents
            if (i > 0 && i % progressInterval == 0) {
                reportProgress("Processing element " + i + " of " + totalElements);
            }
        }

        logger.info("PDF data dictionary completed with {} elements", totalElements);

        // Element Diagrams section (if configured)
        if (config.isIncludeElementDiagrams() && imageService != null && tempImageDir != null) {
            Element elementDiagrams = doc.createElement("element-diagrams");
            root.appendChild(elementDiagrams);

            // Filter elements that have children (diagrams make sense for these)
            List<XsdExtendedElement> elementsWithDiagrams = new ArrayList<>();
            for (XsdExtendedElement element : elementMap.values()) {
                if (isContainerElement(element)) {
                    continue;
                }
                // Only include elements at level 0-2 that have children
                if (element.getLevel() <= 2 && hasSignificantContent(element, elementMap)) {
                    elementsWithDiagrams.add(element);
                }
            }

            // Sort by level then by path
            elementsWithDiagrams.sort(Comparator
                    .comparingInt(XsdExtendedElement::getLevel)
                    .thenComparing(this::getCleanXPath));

            logger.info("Generating {} element diagrams for PDF document", elementsWithDiagrams.size());
            int diagramCount = 0;

            for (XsdExtendedElement element : elementsWithDiagrams) {
                try {
                    // Generate PNG image
                    String safeName = element.getElementName().replaceAll("[^a-zA-Z0-9]", "_");
                    Path imagePath = tempImageDir.resolve(safeName + "-" + diagramCount + ".png");
                    String generatedPath = imageService.generateImage(element, imagePath.toFile());

                    if (generatedPath != null) {
                        Element diagramEntry = doc.createElement("diagram");
                        elementDiagrams.appendChild(diagramEntry);

                        addElement(doc, diagramEntry, "element-name", element.getElementName());
                        addElement(doc, diagramEntry, "path", getCleanXPath(element));
                        addElement(doc, diagramEntry, "type", element.getElementType() != null ? element.getElementType() : "-");
                        addElement(doc, diagramEntry, "image-path", imagePath.toUri().toString());

                        diagramCount++;
                    }
                } catch (Exception e) {
                    logger.warn("Failed to generate diagram for element {}: {}", element.getElementName(), e.getMessage());
                }
            }

            logger.info("PDF element diagrams prepared: {} diagrams", diagramCount);
        }

        return doc;
    }

    /**
     * Checks if an element has significant content (children) worth showing in a diagram.
     */
    private boolean hasSignificantContent(XsdExtendedElement element, Map<String, XsdExtendedElement> elementMap) {
        String elementXpath = element.getCurrentXpath();
        if (elementXpath == null) {
            return false;
        }

        for (XsdExtendedElement child : elementMap.values()) {
            if (elementXpath.equals(child.getParentXpath())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Loads the XSL-FO template from resources.
     */
    private Source loadXslFoTemplate() throws IOException {
        // Try loading from classpath
        try (InputStream is = getClass().getResourceAsStream(XSL_FO_TEMPLATE_PATH)) {
            if (is != null) {
                // Read into a string and create a StreamSource
                String xslContent = new String(is.readAllBytes());
                return new StreamSource(new StringReader(xslContent));
            }
        }

        // If not found, use embedded template
        logger.warn("XSL-FO template not found at {}, using embedded template", XSL_FO_TEMPLATE_PATH);
        return new StreamSource(new StringReader(getEmbeddedXslFoTemplate()));
    }

    /**
     * Transforms the intermediate XML to PDF using Apache FOP.
     */
    private void transformToPdf(Document intermediateXml, Source xslSource, File outputFile)
            throws Exception {

        // Ensure output directory exists
        Files.createDirectories(outputFile.toPath().getParent());

        // Configure FOP
        FOUserAgent foUserAgent = fopFactory.newFOUserAgent();
        foUserAgent.setProducer("FreeXmlToolkit");
        foUserAgent.setCreator("FreeXmlToolkit");

        // Apply PDF metadata from config
        String author = config.getAuthor() != null && !config.getAuthor().isEmpty()
                ? config.getAuthor()
                : System.getProperty("user.name", "");
        foUserAgent.setAuthor(author);

        String title = config.getTitle() != null && !config.getTitle().isEmpty()
                ? config.getTitle()
                : "XSD Documentation: " + extractSchemaName();
        foUserAgent.setTitle(title);

        if (config.getSubject() != null && !config.getSubject().isEmpty()) {
            foUserAgent.setSubject(config.getSubject());
        }

        if (config.getKeywords() != null && !config.getKeywords().isEmpty()) {
            foUserAgent.setKeywords(config.getKeywords());
        }

        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(outputFile))) {
            Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, foUserAgent, out);

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer(xslSource);

            // Basic parameters
            transformer.setParameter("currentDate", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            transformer.setParameter("schemaName", extractSchemaName());

            // Page layout parameters
            transformer.setParameter("pageWidth", config.getPageWidth());
            transformer.setParameter("pageHeight", config.getPageHeight());
            transformer.setParameter("marginSize", config.getMarginSize());

            // Typography parameters
            transformer.setParameter("fontSize", config.getFontSizeValue());
            transformer.setParameter("fontFamily", config.getFontFamilyName());
            transformer.setParameter("lineHeight", config.getLineHeightValue());
            transformer.setParameter("headingBold", config.getHeadingStyle().isBold() ? "bold" : "normal");
            transformer.setParameter("headingUnderlined", config.getHeadingStyle().isUnderlined() ? "underline" : "none");
            transformer.setParameter("headingColor", config.getHeadingStyle().getColor());

            // Color scheme parameters
            transformer.setParameter("primaryColor", config.getPrimaryColor());
            transformer.setParameter("lightBackground", config.getColorScheme().getLightBackground());
            transformer.setParameter("tableHeaderBg", config.getColorScheme().getTableHeaderBg());

            // Content section toggles
            transformer.setParameter("includeCoverPage", config.isIncludeCoverPage() ? "true" : "false");
            transformer.setParameter("includeToc", config.isIncludeToc() ? "true" : "false");
            transformer.setParameter("includeSchemaOverview", config.isIncludeSchemaOverview() ? "true" : "false");
            transformer.setParameter("includeSchemaDiagram", config.isIncludeSchemaDiagram() ? "true" : "false");
            transformer.setParameter("includeComplexTypes", config.isIncludeComplexTypes() ? "true" : "false");
            transformer.setParameter("includeSimpleTypes", config.isIncludeSimpleTypes() ? "true" : "false");
            transformer.setParameter("includeDataDictionary", config.isIncludeDataDictionary() ? "true" : "false");
            transformer.setParameter("includeElementDiagrams", config.isIncludeElementDiagrams() ? "true" : "false");

            // Header & Footer parameters
            transformer.setParameter("headerStyle", config.getHeaderStyle().name());
            transformer.setParameter("footerStyle", config.getFooterStyle().name());
            transformer.setParameter("includePageNumbers", config.isIncludePageNumbers() ? "true" : "false");
            transformer.setParameter("pageNumberPosition", config.getPageNumberPosition().getAlignment());

            // Table style parameter
            transformer.setParameter("tableStyle", config.getTableStyle().name());

            // Watermark parameters
            transformer.setParameter("watermarkText", config.getWatermarkText());
            transformer.setParameter("hasWatermark", config.hasWatermark() ? "true" : "false");

            // PDF Bookmarks parameter
            transformer.setParameter("generateBookmarks", config.isGenerateBookmarks() ? "true" : "false");

            Source xmlSource = new DOMSource(intermediateXml);
            Result result = new SAXResult(fop.getDefaultHandler());

            transformer.transform(xmlSource, result);
        }
    }

    /**
     * Returns an embedded XSL-FO template for PDF generation.
     * This is used as a fallback if the external template is not found.
     * The template accepts parameters for customization.
     */
    private String getEmbeddedXslFoTemplate() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <xsl:stylesheet version="1.0"
                    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                    xmlns:fo="http://www.w3.org/1999/XSL/Format">

                    <!-- Basic parameters -->
                    <xsl:param name="currentDate"/>
                    <xsl:param name="schemaName"/>

                    <!-- Page layout parameters -->
                    <xsl:param name="pageWidth">210mm</xsl:param>
                    <xsl:param name="pageHeight">297mm</xsl:param>
                    <xsl:param name="marginSize">20mm</xsl:param>

                    <!-- Typography parameters -->
                    <xsl:param name="fontSize">11pt</xsl:param>
                    <xsl:param name="fontFamily">Helvetica</xsl:param>
                    <xsl:param name="lineHeight">1.2</xsl:param>
                    <xsl:param name="headingBold">bold</xsl:param>
                    <xsl:param name="headingUnderlined">none</xsl:param>
                    <xsl:param name="headingColor">#2563EB</xsl:param>

                    <!-- Color scheme parameters -->
                    <xsl:param name="primaryColor">#2563EB</xsl:param>
                    <xsl:param name="lightBackground">#EFF6FF</xsl:param>
                    <xsl:param name="tableHeaderBg">#DBEAFE</xsl:param>

                    <!-- Content section toggles -->
                    <xsl:param name="includeCoverPage">true</xsl:param>
                    <xsl:param name="includeToc">true</xsl:param>
                    <xsl:param name="includeSchemaOverview">true</xsl:param>
                    <xsl:param name="includeSchemaDiagram">false</xsl:param>
                    <xsl:param name="includeComplexTypes">true</xsl:param>
                    <xsl:param name="includeSimpleTypes">true</xsl:param>
                    <xsl:param name="includeDataDictionary">true</xsl:param>
                    <xsl:param name="includeElementDiagrams">false</xsl:param>
                    <xsl:param name="generateBookmarks">true</xsl:param>

                    <!-- Header & Footer parameters -->
                    <xsl:param name="headerStyle">STANDARD</xsl:param>
                    <xsl:param name="footerStyle">STANDARD</xsl:param>
                    <xsl:param name="includePageNumbers">true</xsl:param>
                    <xsl:param name="pageNumberPosition">center</xsl:param>

                    <!-- Table style parameter -->
                    <xsl:param name="tableStyle">BORDERED</xsl:param>

                    <!-- Watermark parameters -->
                    <xsl:param name="watermarkText"></xsl:param>
                    <xsl:param name="hasWatermark">false</xsl:param>

                    <xsl:template match="/">
                        <fo:root font-family="{$fontFamily}" font-size="{$fontSize}" line-height="{$lineHeight}">
                            <!-- Page layout -->
                            <fo:layout-master-set>
                                <fo:simple-page-master master-name="main"
                                    page-width="{$pageWidth}" page-height="{$pageHeight}"
                                    margin-top="{$marginSize}" margin-bottom="{$marginSize}"
                                    margin-left="{$marginSize}" margin-right="{$marginSize}">
                                    <fo:region-body margin-top="15mm" margin-bottom="15mm"/>
                                    <fo:region-before extent="15mm"/>
                                    <fo:region-after extent="15mm"/>
                                </fo:simple-page-master>
                            </fo:layout-master-set>

                            <!-- PDF Bookmarks / Outline -->
                            <xsl:if test="$generateBookmarks = 'true'">
                                <fo:bookmark-tree>
                                    <xsl:if test="$includeSchemaOverview = 'true'">
                                        <fo:bookmark internal-destination="schema-overview">
                                            <fo:bookmark-title>Schema Overview</fo:bookmark-title>
                                        </fo:bookmark>
                                    </xsl:if>

                                    <xsl:if test="$includeSchemaDiagram = 'true'">
                                        <xsl:if test="xsd-documentation/schema-diagram/image-path">
                                            <fo:bookmark internal-destination="schema-diagram">
                                                <fo:bookmark-title>Schema Diagram</fo:bookmark-title>
                                            </fo:bookmark>
                                        </xsl:if>
                                    </xsl:if>

                                    <xsl:if test="$includeComplexTypes = 'true'">
                                        <xsl:if test="xsd-documentation/complex-types/complex-type">
                                            <fo:bookmark internal-destination="complex-types">
                                                <fo:bookmark-title>Complex Types</fo:bookmark-title>
                                            </fo:bookmark>
                                        </xsl:if>
                                    </xsl:if>

                                    <xsl:if test="$includeSimpleTypes = 'true'">
                                        <xsl:if test="xsd-documentation/simple-types/simple-type">
                                            <fo:bookmark internal-destination="simple-types">
                                                <fo:bookmark-title>Simple Types</fo:bookmark-title>
                                            </fo:bookmark>
                                        </xsl:if>
                                    </xsl:if>

                                    <xsl:if test="$includeDataDictionary = 'true'">
                                        <xsl:if test="xsd-documentation/data-dictionary/element">
                                            <fo:bookmark internal-destination="data-dictionary">
                                                <fo:bookmark-title>Data Dictionary</fo:bookmark-title>
                                            </fo:bookmark>
                                        </xsl:if>
                                    </xsl:if>

                                    <xsl:if test="$includeElementDiagrams = 'true'">
                                        <xsl:if test="xsd-documentation/element-diagrams/diagram">
                                            <fo:bookmark internal-destination="element-diagrams">
                                                <fo:bookmark-title>Element Diagrams</fo:bookmark-title>
                                            </fo:bookmark>
                                        </xsl:if>
                                    </xsl:if>
                                </fo:bookmark-tree>
                            </xsl:if>

                            <!-- Document content -->
                            <fo:page-sequence master-reference="main">
                                <!-- Header -->
                                <xsl:if test="$headerStyle != 'NONE'">
                                    <fo:static-content flow-name="xsl-region-before">
                                        <xsl:choose>
                                            <xsl:when test="$headerStyle = 'STANDARD'">
                                                <fo:block font-size="9pt" color="#64748B"
                                                    border-bottom="0.5pt solid #CBD5E1" padding-bottom="3mm">
                                                    XSD Documentation: <xsl:value-of select="$schemaName"/>
                                                </fo:block>
                                            </xsl:when>
                                            <xsl:when test="$headerStyle = 'MINIMAL'">
                                                <fo:block border-bottom="0.5pt solid #CBD5E1" padding-bottom="3mm"/>
                                            </xsl:when>
                                        </xsl:choose>
                                    </fo:static-content>
                                </xsl:if>

                                <!-- Footer -->
                                <xsl:if test="$footerStyle != 'NONE'">
                                    <fo:static-content flow-name="xsl-region-after">
                                        <xsl:choose>
                                            <xsl:when test="$footerStyle = 'STANDARD'">
                                                <fo:block font-size="9pt" color="#64748B" text-align="{$pageNumberPosition}"
                                                    border-top="0.5pt solid #CBD5E1" padding-top="3mm">
                                                    <xsl:if test="$includePageNumbers = 'true'">Page <fo:page-number/> - </xsl:if>
                                                    Generated by FreeXmlToolkit on <xsl:value-of select="$currentDate"/>
                                                </fo:block>
                                            </xsl:when>
                                            <xsl:when test="$footerStyle = 'MINIMAL'">
                                                <fo:block font-size="9pt" color="#64748B" text-align="{$pageNumberPosition}"
                                                    border-top="0.5pt solid #CBD5E1" padding-top="3mm">
                                                    <xsl:if test="$includePageNumbers = 'true'">Page <fo:page-number/></xsl:if>
                                                </fo:block>
                                            </xsl:when>
                                        </xsl:choose>
                                    </fo:static-content>
                                </xsl:if>

                                <!-- Body -->
                                <fo:flow flow-name="xsl-region-body">

                                    <!-- Watermark (if enabled) -->
                                    <xsl:if test="$hasWatermark = 'true'">
                                        <fo:block-container absolute-position="fixed" top="100mm" left="0mm" width="100%">
                                            <fo:block text-align="center" font-size="60pt" color="#E5E7EB"
                                                font-weight="bold" letter-spacing="10pt">
                                                <xsl:value-of select="$watermarkText"/>
                                            </fo:block>
                                        </fo:block-container>
                                    </xsl:if>

                                    <!-- Cover Page -->
                                    <xsl:if test="$includeCoverPage = 'true'">
                                        <fo:block font-size="28pt" font-weight="{$headingBold}" color="{$headingColor}"
                                            text-decoration="{$headingUnderlined}"
                                            text-align="center" space-after="20mm" margin-top="60mm">
                                            XSD Schema Documentation
                                        </fo:block>

                                        <fo:block font-size="18pt" color="#64748B" text-align="center" space-after="40mm">
                                            <xsl:value-of select="xsd-documentation/metadata/schema-name"/>
                                        </fo:block>

                                        <fo:block font-size="12pt" font-style="italic" color="#64748B" text-align="center" space-after="5mm">
                                            Generated: <xsl:value-of select="xsd-documentation/metadata/generation-date"/>
                                        </fo:block>

                                        <fo:block font-size="10pt" color="#64748B" text-align="center">
                                            Generated by <xsl:value-of select="xsd-documentation/metadata/generator"/>
                                        </fo:block>

                                        <fo:block break-after="page"/>
                                    </xsl:if>

                                    <!-- Schema Overview -->
                                    <xsl:if test="$includeSchemaOverview = 'true'">
                                        <fo:block id="schema-overview" font-size="18pt" font-weight="{$headingBold}" color="{$headingColor}"
                                            text-decoration="{$headingUnderlined}"
                                            space-before="10mm" space-after="5mm">
                                            Schema Overview
                                        </fo:block>

                                        <fo:table table-layout="fixed" width="100%" border="0.5pt solid #CBD5E1">
                                            <fo:table-column column-width="40%"/>
                                            <fo:table-column column-width="60%"/>
                                            <fo:table-body>
                                                <fo:table-row>
                                                    <fo:table-cell padding="3mm" background-color="{$tableHeaderBg}" border="0.5pt solid #CBD5E1">
                                                        <fo:block font-weight="bold">File Path</fo:block>
                                                    </fo:table-cell>
                                                    <fo:table-cell padding="3mm" border="0.5pt solid #CBD5E1">
                                                        <fo:block><xsl:value-of select="xsd-documentation/metadata/file-path"/></fo:block>
                                                    </fo:table-cell>
                                                </fo:table-row>
                                                <fo:table-row>
                                                    <fo:table-cell padding="3mm" background-color="{$tableHeaderBg}" border="0.5pt solid #CBD5E1">
                                                        <fo:block font-weight="bold">Target Namespace</fo:block>
                                                    </fo:table-cell>
                                                    <fo:table-cell padding="3mm" border="0.5pt solid #CBD5E1">
                                                        <fo:block><xsl:value-of select="xsd-documentation/metadata/target-namespace"/></fo:block>
                                                    </fo:table-cell>
                                                </fo:table-row>
                                                <fo:table-row>
                                                    <fo:table-cell padding="3mm" background-color="{$tableHeaderBg}" border="0.5pt solid #CBD5E1">
                                                        <fo:block font-weight="bold">Version</fo:block>
                                                    </fo:table-cell>
                                                    <fo:table-cell padding="3mm" border="0.5pt solid #CBD5E1">
                                                        <fo:block><xsl:value-of select="xsd-documentation/metadata/version"/></fo:block>
                                                    </fo:table-cell>
                                                </fo:table-row>
                                            </fo:table-body>
                                        </fo:table>

                                        <!-- Statistics -->
                                        <fo:block font-size="14pt" font-weight="{$headingBold}" color="{$headingColor}"
                                            text-decoration="{$headingUnderlined}"
                                            space-before="10mm" space-after="5mm">
                                            Statistics
                                        </fo:block>

                                        <fo:table table-layout="fixed" width="100%" border="0.5pt solid #CBD5E1">
                                            <fo:table-column column-width="50%"/>
                                            <fo:table-column column-width="50%"/>
                                            <fo:table-body>
                                                <fo:table-row>
                                                    <fo:table-cell padding="3mm" background-color="{$tableHeaderBg}" border="0.5pt solid #CBD5E1">
                                                        <fo:block font-weight="bold">Global Elements</fo:block>
                                                    </fo:table-cell>
                                                    <fo:table-cell padding="3mm" border="0.5pt solid #CBD5E1">
                                                        <fo:block><xsl:value-of select="xsd-documentation/statistics/global-elements"/></fo:block>
                                                    </fo:table-cell>
                                                </fo:table-row>
                                                <fo:table-row>
                                                    <fo:table-cell padding="3mm" background-color="{$tableHeaderBg}" border="0.5pt solid #CBD5E1">
                                                        <fo:block font-weight="bold">Global ComplexTypes</fo:block>
                                                    </fo:table-cell>
                                                    <fo:table-cell padding="3mm" border="0.5pt solid #CBD5E1">
                                                        <fo:block><xsl:value-of select="xsd-documentation/statistics/global-complex-types"/></fo:block>
                                                    </fo:table-cell>
                                                </fo:table-row>
                                                <fo:table-row>
                                                    <fo:table-cell padding="3mm" background-color="{$tableHeaderBg}" border="0.5pt solid #CBD5E1">
                                                        <fo:block font-weight="bold">Global SimpleTypes</fo:block>
                                                    </fo:table-cell>
                                                    <fo:table-cell padding="3mm" border="0.5pt solid #CBD5E1">
                                                        <fo:block><xsl:value-of select="xsd-documentation/statistics/global-simple-types"/></fo:block>
                                                    </fo:table-cell>
                                                </fo:table-row>
                                                <fo:table-row>
                                                    <fo:table-cell padding="3mm" background-color="{$tableHeaderBg}" border="0.5pt solid #CBD5E1">
                                                        <fo:block font-weight="bold">Total Elements</fo:block>
                                                    </fo:table-cell>
                                                    <fo:table-cell padding="3mm" border="0.5pt solid #CBD5E1">
                                                        <fo:block><xsl:value-of select="xsd-documentation/statistics/total-elements"/></fo:block>
                                                    </fo:table-cell>
                                                </fo:table-row>
                                            </fo:table-body>
                                        </fo:table>

                                        <fo:block break-after="page"/>
                                    </xsl:if>

                                    <!-- Schema Diagram Section -->
                                    <xsl:if test="$includeSchemaDiagram = 'true'">
                                        <xsl:if test="xsd-documentation/schema-diagram/image-path">
                                            <fo:block break-before="page"/>
                                            <fo:block id="schema-diagram" font-size="18pt" font-weight="{$headingBold}"
                                                color="{$headingColor}" text-decoration="{$headingUnderlined}"
                                                space-before="5mm" space-after="5mm">
                                                Schema Diagram
                                            </fo:block>

                                            <fo:block font-size="10pt" color="#6c757d" space-after="4mm">
                                                Visual representation of the complete schema structure starting from root element:
                                                <fo:inline font-weight="bold">
                                                    <xsl:value-of select="xsd-documentation/schema-diagram/root-element"/>
                                                </fo:inline>
                                            </fo:block>

                                            <!-- Diagram image -->
                                            <fo:block text-align="center" space-before="5mm">
                                                <fo:external-graphic
                                                    src="{xsd-documentation/schema-diagram/image-path}"
                                                    content-width="scale-to-fit"
                                                    content-height="scale-to-fit"
                                                    max-width="160mm"
                                                    max-height="200mm"
                                                    scaling="uniform"/>
                                            </fo:block>

                                            <!-- Caption -->
                                            <fo:block font-size="9pt" font-style="italic" color="#6c757d"
                                                text-align="center" space-before="3mm" space-after="8mm">
                                                Figure: Complete Schema Structure
                                            </fo:block>
                                        </xsl:if>
                                    </xsl:if>

                                    <!-- Complex Types -->
                                    <xsl:if test="$includeComplexTypes = 'true' and xsd-documentation/complex-types/complex-type">
                                        <fo:block id="complex-types" font-size="18pt" font-weight="{$headingBold}" color="{$headingColor}"
                                            text-decoration="{$headingUnderlined}"
                                            space-before="5mm" space-after="5mm">
                                            Complex Types
                                        </fo:block>

                                        <fo:table table-layout="fixed" width="100%" border="0.5pt solid #CBD5E1">
                                            <fo:table-column column-width="40%"/>
                                            <fo:table-column column-width="40%"/>
                                            <fo:table-column column-width="20%"/>
                                            <fo:table-header>
                                                <fo:table-row background-color="{$tableHeaderBg}">
                                                    <fo:table-cell padding="3mm" border="0.5pt solid #CBD5E1">
                                                        <fo:block font-weight="bold">Type Name</fo:block>
                                                    </fo:table-cell>
                                                    <fo:table-cell padding="3mm" border="0.5pt solid #CBD5E1">
                                                        <fo:block font-weight="bold">Base Type</fo:block>
                                                    </fo:table-cell>
                                                    <fo:table-cell padding="3mm" border="0.5pt solid #CBD5E1">
                                                        <fo:block font-weight="bold">Usage</fo:block>
                                                    </fo:table-cell>
                                                </fo:table-row>
                                            </fo:table-header>
                                            <fo:table-body>
                                                <xsl:for-each select="xsd-documentation/complex-types/complex-type">
                                                    <fo:table-row>
                                                        <xsl:if test="$tableStyle = 'ZEBRA_STRIPES' and position() mod 2 = 0">
                                                            <xsl:attribute name="background-color">#F9FAFB</xsl:attribute>
                                                        </xsl:if>
                                                        <fo:table-cell padding="2mm" border="0.5pt solid #CBD5E1">
                                                            <fo:block font-size="9pt"><xsl:value-of select="name"/></fo:block>
                                                        </fo:table-cell>
                                                        <fo:table-cell padding="2mm" border="0.5pt solid #CBD5E1">
                                                            <fo:block font-size="9pt"><xsl:value-of select="base-type"/></fo:block>
                                                        </fo:table-cell>
                                                        <fo:table-cell padding="2mm" border="0.5pt solid #CBD5E1">
                                                            <fo:block font-size="9pt"><xsl:value-of select="usage-count"/></fo:block>
                                                        </fo:table-cell>
                                                    </fo:table-row>
                                                </xsl:for-each>
                                            </fo:table-body>
                                        </fo:table>
                                    </xsl:if>

                                    <!-- Simple Types -->
                                    <xsl:if test="$includeSimpleTypes = 'true' and xsd-documentation/simple-types/simple-type">
                                        <fo:block id="simple-types" font-size="18pt" font-weight="{$headingBold}" color="{$headingColor}"
                                            text-decoration="{$headingUnderlined}"
                                            space-before="10mm" space-after="5mm">
                                            Simple Types
                                        </fo:block>

                                        <fo:table table-layout="fixed" width="100%" border="0.5pt solid #CBD5E1">
                                            <fo:table-column column-width="35%"/>
                                            <fo:table-column column-width="35%"/>
                                            <fo:table-column column-width="30%"/>
                                            <fo:table-header>
                                                <fo:table-row background-color="{$tableHeaderBg}">
                                                    <fo:table-cell padding="3mm" border="0.5pt solid #CBD5E1">
                                                        <fo:block font-weight="bold">Type Name</fo:block>
                                                    </fo:table-cell>
                                                    <fo:table-cell padding="3mm" border="0.5pt solid #CBD5E1">
                                                        <fo:block font-weight="bold">Base Type</fo:block>
                                                    </fo:table-cell>
                                                    <fo:table-cell padding="3mm" border="0.5pt solid #CBD5E1">
                                                        <fo:block font-weight="bold">Facets</fo:block>
                                                    </fo:table-cell>
                                                </fo:table-row>
                                            </fo:table-header>
                                            <fo:table-body>
                                                <xsl:for-each select="xsd-documentation/simple-types/simple-type">
                                                    <fo:table-row>
                                                        <xsl:if test="$tableStyle = 'ZEBRA_STRIPES' and position() mod 2 = 0">
                                                            <xsl:attribute name="background-color">#F9FAFB</xsl:attribute>
                                                        </xsl:if>
                                                        <fo:table-cell padding="2mm" border="0.5pt solid #CBD5E1">
                                                            <fo:block font-size="9pt"><xsl:value-of select="name"/></fo:block>
                                                        </fo:table-cell>
                                                        <fo:table-cell padding="2mm" border="0.5pt solid #CBD5E1">
                                                            <fo:block font-size="9pt"><xsl:value-of select="base-type"/></fo:block>
                                                        </fo:table-cell>
                                                        <fo:table-cell padding="2mm" border="0.5pt solid #CBD5E1">
                                                            <fo:block font-size="9pt"><xsl:value-of select="facets"/></fo:block>
                                                        </fo:table-cell>
                                                    </fo:table-row>
                                                </xsl:for-each>
                                            </fo:table-body>
                                        </fo:table>

                                        <fo:block break-after="page"/>
                                    </xsl:if>

                                    <!-- Data Dictionary -->
                                    <xsl:if test="$includeDataDictionary = 'true'">
                                        <fo:block id="data-dictionary" font-size="18pt" font-weight="{$headingBold}" color="{$headingColor}"
                                            text-decoration="{$headingUnderlined}"
                                            space-before="5mm" space-after="5mm">
                                            Data Dictionary
                                        </fo:block>

                                        <xsl:if test="xsd-documentation/data-dictionary/element">
                                            <fo:table table-layout="fixed" width="100%" border="0.5pt solid #CBD5E1">
                                                <fo:table-column column-width="30%"/>
                                                <fo:table-column column-width="20%"/>
                                                <fo:table-column column-width="15%"/>
                                                <fo:table-column column-width="35%"/>
                                                <fo:table-header>
                                                    <fo:table-row background-color="{$tableHeaderBg}">
                                                        <fo:table-cell padding="3mm" border="0.5pt solid #CBD5E1">
                                                            <fo:block font-weight="bold" font-size="9pt">Element Path</fo:block>
                                                        </fo:table-cell>
                                                        <fo:table-cell padding="3mm" border="0.5pt solid #CBD5E1">
                                                            <fo:block font-weight="bold" font-size="9pt">Type</fo:block>
                                                        </fo:table-cell>
                                                        <fo:table-cell padding="3mm" border="0.5pt solid #CBD5E1">
                                                            <fo:block font-weight="bold" font-size="9pt">Cardinality</fo:block>
                                                        </fo:table-cell>
                                                        <fo:table-cell padding="3mm" border="0.5pt solid #CBD5E1">
                                                            <fo:block font-weight="bold" font-size="9pt">Description</fo:block>
                                                        </fo:table-cell>
                                                    </fo:table-row>
                                                </fo:table-header>
                                                <fo:table-body>
                                                    <xsl:for-each select="xsd-documentation/data-dictionary/element">
                                                        <fo:table-row>
                                                            <xsl:if test="$tableStyle = 'ZEBRA_STRIPES' and position() mod 2 = 0">
                                                                <xsl:attribute name="background-color">#F9FAFB</xsl:attribute>
                                                            </xsl:if>
                                                            <fo:table-cell padding="2mm" border="0.5pt solid #CBD5E1">
                                                                <fo:block font-size="8pt" wrap-option="wrap"><xsl:value-of select="path"/></fo:block>
                                                            </fo:table-cell>
                                                            <fo:table-cell padding="2mm" border="0.5pt solid #CBD5E1">
                                                                <fo:block font-size="8pt"><xsl:value-of select="type"/></fo:block>
                                                            </fo:table-cell>
                                                            <fo:table-cell padding="2mm" border="0.5pt solid #CBD5E1">
                                                                <fo:block font-size="8pt"><xsl:value-of select="cardinality"/></fo:block>
                                                            </fo:table-cell>
                                                            <fo:table-cell padding="2mm" border="0.5pt solid #CBD5E1">
                                                                <fo:block font-size="8pt" wrap-option="wrap"><xsl:value-of select="description"/></fo:block>
                                                            </fo:table-cell>
                                                        </fo:table-row>
                                                    </xsl:for-each>
                                                </fo:table-body>
                                            </fo:table>
                                        </xsl:if>
                                    </xsl:if>

                                    <!-- Element Diagrams Section -->
                                    <xsl:if test="$includeElementDiagrams = 'true'">
                                        <xsl:if test="xsd-documentation/element-diagrams/diagram">
                                            <fo:block break-before="page"/>
                                            <fo:block id="element-diagrams" font-size="18pt" font-weight="{$headingBold}" color="{$headingColor}"
                                                text-decoration="{$headingUnderlined}"
                                                space-before="5mm" space-after="5mm">
                                                Element Diagrams
                                            </fo:block>

                                            <xsl:for-each select="xsd-documentation/element-diagrams/diagram">
                                                <fo:block space-before="8mm" space-after="4mm">
                                                    <!-- Element name heading -->
                                                    <fo:block font-size="14pt" font-weight="bold" color="{$primaryColor}"
                                                        space-after="2mm">
                                                        <xsl:value-of select="element-name"/>
                                                    </fo:block>

                                                    <!-- Element info -->
                                                    <fo:block font-size="9pt" color="#6c757d" space-after="1mm">
                                                        Path: <xsl:value-of select="path"/>
                                                    </fo:block>
                                                    <fo:block font-size="9pt" color="#6c757d" space-after="3mm">
                                                        Type: <xsl:value-of select="type"/>
                                                    </fo:block>

                                                    <!-- Diagram image -->
                                                    <fo:block text-align="center">
                                                        <fo:external-graphic
                                                            src="{image-path}"
                                                            content-width="scale-to-fit"
                                                            content-height="scale-to-fit"
                                                            max-width="160mm"
                                                            max-height="100mm"
                                                            scaling="uniform"/>
                                                    </fo:block>

                                                    <!-- Caption -->
                                                    <fo:block font-size="9pt" font-style="italic" color="#6c757d"
                                                        text-align="center" space-before="2mm">
                                                        Figure: Structure of <xsl:value-of select="element-name"/>
                                                    </fo:block>
                                                </fo:block>
                                            </xsl:for-each>
                                        </xsl:if>
                                    </xsl:if>

                                </fo:flow>
                            </fo:page-sequence>
                        </fo:root>
                    </xsl:template>
                </xsl:stylesheet>
                """;
    }

    // ================= Helper Methods =================

    private void addElement(Document doc, Element parent, String name, String value) {
        Element element = doc.createElement(name);
        element.setTextContent(value != null ? value : "");
        parent.appendChild(element);
    }

    private String truncateString(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
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

    private String getBaseType(org.w3c.dom.Element complexTypeElement) {
        var childNodes = complexTypeElement.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            if (child instanceof org.w3c.dom.Element childElement) {
                String localName = childElement.getLocalName();
                if ("complexContent".equals(localName) || "simpleContent".equals(localName)) {
                    var contentChildren = childElement.getChildNodes();
                    for (int j = 0; j < contentChildren.getLength(); j++) {
                        Node contentChild = contentChildren.item(j);
                        if (contentChild instanceof org.w3c.dom.Element contentElement) {
                            String contentLocalName = contentElement.getLocalName();
                            if ("extension".equals(contentLocalName) || "restriction".equals(contentLocalName)) {
                                return contentElement.getAttribute("base");
                            }
                        }
                    }
                }
            }
        }
        return "-";
    }

    private String getSimpleTypeBase(org.w3c.dom.Element simpleTypeElement) {
        var childNodes = simpleTypeElement.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            if (child instanceof org.w3c.dom.Element childElement) {
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
        return "-";
    }

    private String getSimpleTypeFacetsSummary(org.w3c.dom.Element simpleTypeElement) {
        List<String> facets = new ArrayList<>();
        var childNodes = simpleTypeElement.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            if (child instanceof org.w3c.dom.Element childElement && "restriction".equals(childElement.getLocalName())) {
                var facetNodes = childElement.getChildNodes();
                for (int j = 0; j < facetNodes.getLength(); j++) {
                    Node facetNode = facetNodes.item(j);
                    if (facetNode instanceof org.w3c.dom.Element facetElement) {
                        String facetName = facetElement.getLocalName();
                        if (facetName != null && !facetName.equals("annotation")) {
                            String facetValue = facetElement.getAttribute("value");
                            if (!facetValue.isEmpty()) {
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
            String doc = docs.get("default");
            if (doc == null) {
                doc = docs.values().iterator().next();
            }
            // Strip HTML tags
            return doc.replaceAll("<[^>]*>", "").trim();
        }
        return "";
    }

    private void reportProgress(String message) {
        if (progressListener != null) {
            progressListener.onProgressUpdate(new TaskProgressListener.ProgressUpdate(message,
                    TaskProgressListener.ProgressUpdate.Status.RUNNING, 0));
        }
        logger.debug("PDF generation progress: {}", message);
    }

    /**
     * Gets cardinality string from minOccurs/maxOccurs attributes.
     */
    private String getCardinality(XsdExtendedElement element) {
        Node cardNode = element.getCardinalityNode();
        if (cardNode == null) {
            cardNode = element.getCurrentNode();
        }
        if (cardNode == null) {
            return "1";
        }

        String minOccurs = getNodeAttribute(cardNode, "minOccurs", "1");
        String maxOccurs = getNodeAttribute(cardNode, "maxOccurs", "1");

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
     * These container elements are internal structures and should not appear in PDF documentation.
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
                pathParts.addFirst(current.getElementName());
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
            if (!value.isEmpty()) {
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

    public void setConfig(PdfDocumentationConfig config) {
        this.config = config != null ? config : new PdfDocumentationConfig();
    }

    public PdfDocumentationConfig getConfig() {
        return config;
    }
}
