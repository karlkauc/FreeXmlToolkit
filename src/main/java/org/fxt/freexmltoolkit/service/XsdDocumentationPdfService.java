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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

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

import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.PdfDocumentationConfig;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.fxt.freexmltoolkit.util.MarkdownSupport;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

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

    /** List each named type only once in the data dictionary, as the HTML option of the same name. */
    private boolean deduplicateDataDictionaryByType;

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
        this.support = null; // built for this document on first use

        try {
            // Create the temp directory for images if any diagram is enabled
            if ((config.isIncludeSchemaDiagram() || config.isIncludeElementDiagrams()) && imageService != null) {
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
        DocumentBuilderFactory factory = org.fxt.freexmltoolkit.util.SecureXmlFactory.createSecureDocumentBuilderFactory();
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

        // Namespace Overview section
        Element namespaceOverview = doc.createElement("namespace-overview");
        root.appendChild(namespaceOverview);

        writeNamespaces(doc, namespaceOverview);

        // Schema Diagram section (if configured)
        if (config.isIncludeSchemaDiagram() && imageService != null && tempImageDir != null) {
            Element schemaDiagramSection = doc.createElement("schema-diagram");
            root.appendChild(schemaDiagramSection);

            XsdExtendedElement rootElement = findRootElement();

            if (rootElement != null) {
                try {
                    // SVG (vector) or PNG, as configured
                    Path imagePath = PdfElementReferenceBuilder.drawDiagram(imageService, config, rootElement,
                            tempImageDir.resolve("schema-diagram"));

                    if (imagePath != null) {
                        addElement(doc, schemaDiagramSection, "image-path", imagePath.toUri().toString());
                        PdfElementReferenceBuilder.setImageSize(
                                (Element) schemaDiagramSection.getLastChild(), imagePath, config, 200);
                        addElement(doc, schemaDiagramSection, "root-element", rootElement.getElementName());
                        logger.info("Schema diagram generated for PDF document");
                    }
                } catch (Exception e) {
                    logger.warn("Failed to generate schema diagram: {}", e.getMessage());
                }
            }
        }

        // Element and type reference: decided first, so every table can link to them
        PdfElementReferenceBuilder reference = new PdfElementReferenceBuilder(this, documentationData, config,
                imageService, tempImageDir, includedLanguages, this::reportProgress);
        PdfTypeReferenceBuilder types = new PdfTypeReferenceBuilder(this, documentationData, config,
                imageService, tempImageDir, includedLanguages, reference);
        reference.setTypeSections(types::sectionIdOf);

        writeOverviewDiagram(doc, root);

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
                addElement(doc, type, "usage-count", String.valueOf(typeUsage(typeName).count()));
                types.writeComplexType(doc, type, typeElement);
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
                types.writeSimpleType(doc, type, typeElement);
            }
        }

        // Data Dictionary section
        Element dataDictionary = doc.createElement("data-dictionary");
        root.appendChild(dataDictionary);

        Map<String, XsdExtendedElement> elementMap = documentationData.getExtendedXsdElementMap();
        List<XsdExtendedElement> sortedElements = new ArrayList<>(elementMap.values());
        // Filter out container elements (SEQUENCE, CHOICE, ALL) - they are internal structures
        sortedElements.removeIf(this::isContainerElement);
        if (deduplicateDataDictionaryByType) {
            sortedElements = deduplicateByType(sortedElements);
        }
        sortedElements.sort(Comparator.comparing(this::getCleanXPath));

        int totalElements = sortedElements.size();
        logger.info("Creating PDF data dictionary with {} elements (excluding container elements)", totalElements);

        int progressInterval = Math.max(1, totalElements / 10); // Report progress every 10%
        for (int i = 0; i < totalElements; i++) {
            XsdExtendedElement xsdElement = sortedElements.get(i);
            Element entry = doc.createElement("element");
            dataDictionary.appendChild(entry);
            setRef(entry, reference.sectionIdOf(xsdElement.getCurrentXpath()));

            // Use clean XPath without container elements; nothing is cut, long values wrap
            addElement(doc, entry, "path", breakable(getCleanXPath(xsdElement)));
            addElement(doc, entry, "name", xsdElement.getElementName());
            String declaredType = declaredType(xsdElement);
            addElement(doc, entry, "type", declaredType);
            setRef((Element) entry.getLastChild(), types.sectionIdOf(declaredType));
            addElement(doc, entry, "cardinality", getCardinality(xsdElement));
            addElement(doc, entry, "level", String.valueOf(xsdElement.getLevel()));

            addElement(doc, entry, "restrictions", breakable(getRestrictionsSummary(xsdElement)));
            addElement(doc, entry, "description", breakable(getFirstDocumentation(xsdElement)));

            // Report progress for large documents
            if (i > 0 && i % progressInterval == 0) {
                reportProgress("Processing element " + i + " of " + totalElements);
            }
        }

        logger.info("PDF data dictionary completed with {} elements", totalElements);

        // Element reference: one section per element (declaration) with its diagram and details
        reference.write(doc, root);

        // Index section - alphabetically sorted list of all types and elements
        Element index = doc.createElement("index");
        root.appendChild(index);

        // Use a record to store index entry data
        record IndexEntry(String name, String entryType, String location, String ref) implements Comparable<IndexEntry> {
            @Override
            public int compareTo(IndexEntry other) {
                // Same names at different places are different entries
                int byName = String.CASE_INSENSITIVE_ORDER.compare(this.name, other.name);
                return byName != 0 ? byName : this.location.compareTo(other.location);
            }
        }

        // Collect all index entries with their type and location
        Set<IndexEntry> indexEntries = new TreeSet<>();

        // Add element and attribute names with their location (path without SEQUENCE/CHOICE/ALL)
        for (XsdExtendedElement element : elementMap.values()) {
            if (!isContainerElement(element) && element.getElementName() != null) {
                String kind = element.getElementName().startsWith("@") ? "Attribute" : "Element";
                indexEntries.add(new IndexEntry(element.getElementName(), kind, breakable(getCleanXPath(element)),
                        reference.sectionIdOf(element.getCurrentXpath())));
            }
        }

        // Add complex type names
        for (Node typeNode : documentationData.getGlobalComplexTypes()) {
            if (typeNode instanceof org.w3c.dom.Element typeElement) {
                String name = typeElement.getAttribute("name");
                if (name != null && !name.isEmpty()) {
                    indexEntries.add(new IndexEntry(name, "ComplexType", "Global", types.sectionIdOf(name)));
                }
            }
        }

        // Add simple type names
        for (Node typeNode : documentationData.getGlobalSimpleTypes()) {
            if (typeNode instanceof org.w3c.dom.Element typeElement) {
                String name = typeElement.getAttribute("name");
                if (name != null && !name.isEmpty()) {
                    indexEntries.add(new IndexEntry(name, "SimpleType", "Global", types.sectionIdOf(name)));
                }
            }
        }

        // Create structured index entries
        for (IndexEntry entry : indexEntries) {
            Element entryElement = doc.createElement("entry");
            setRef(entryElement, entry.ref());
            addElement(doc, entryElement, "name", entry.name());
            addElement(doc, entryElement, "entry-type", entry.entryType());
            addElement(doc, entryElement, "location", entry.location());
            index.appendChild(entryElement);
        }

        logger.info("PDF index created with {} entries", indexEntries.size());

        return doc;
    }

    /**
     * Loads the XSL-FO template from resources.
     */
    private Source loadXslFoTemplate() throws IOException {
        // Try loading from classpath
        try (InputStream is = getClass().getResourceAsStream(XSL_FO_TEMPLATE_PATH)) {
            if (is != null) {
                // Read into a string and create a StreamSource
                String xslContent = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
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
        // Large schemas give thousands of pages: let FOP write finished pages out instead of
        // holding them all in memory
        foUserAgent.setConserveMemoryPolicy(true);
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

            TransformerFactory transformerFactory = org.fxt.freexmltoolkit.util.SecureXmlFactory.createSecureTransformerFactory();
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
            transformer.setParameter("includeElementReference",
                    config.getElementReference() != PdfDocumentationConfig.ElementReference.OFF ? "true" : "false");

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

                                    <xsl:if test="xsd-documentation/namespace-overview/namespace">
                                        <fo:bookmark internal-destination="namespace-overview">
                                            <fo:bookmark-title>Namespace Overview</fo:bookmark-title>
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

                                    <xsl:if test="xsd-documentation/index/entry">
                                        <fo:bookmark internal-destination="index">
                                            <fo:bookmark-title>Index</fo:bookmark-title>
                                        </fo:bookmark>
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

                                    <!-- Namespace Overview Section -->
                                    <xsl:if test="xsd-documentation/namespace-overview/namespace">
                                        <fo:block id="namespace-overview" font-size="14pt" font-weight="{$headingBold}" color="{$headingColor}"
                                            text-decoration="{$headingUnderlined}"
                                            space-before="10mm" space-after="5mm">
                                            Namespace Overview
                                        </fo:block>

                                        <fo:table table-layout="fixed" width="100%" border="0.5pt solid #CBD5E1">
                                            <fo:table-column column-width="20%"/>
                                            <fo:table-column column-width="50%"/>
                                            <fo:table-column column-width="30%"/>
                                            <fo:table-header>
                                                <fo:table-row background-color="{$tableHeaderBg}">
                                                    <fo:table-cell padding="3mm" border="0.5pt solid #CBD5E1">
                                                        <fo:block font-weight="bold" font-size="9pt">Prefix</fo:block>
                                                    </fo:table-cell>
                                                    <fo:table-cell padding="3mm" border="0.5pt solid #CBD5E1">
                                                        <fo:block font-weight="bold" font-size="9pt">URI</fo:block>
                                                    </fo:table-cell>
                                                    <fo:table-cell padding="3mm" border="0.5pt solid #CBD5E1">
                                                        <fo:block font-weight="bold" font-size="9pt">Description</fo:block>
                                                    </fo:table-cell>
                                                </fo:table-row>
                                            </fo:table-header>
                                            <fo:table-body>
                                                <xsl:for-each select="xsd-documentation/namespace-overview/namespace">
                                                    <fo:table-row>
                                                        <xsl:if test="$tableStyle = 'ZEBRA_STRIPES' and position() mod 2 = 0">
                                                            <xsl:attribute name="background-color">#F9FAFB</xsl:attribute>
                                                        </xsl:if>
                                                        <fo:table-cell padding="2mm" border="0.5pt solid #CBD5E1">
                                                            <fo:block font-size="9pt"><xsl:value-of select="prefix"/></fo:block>
                                                        </fo:table-cell>
                                                        <fo:table-cell padding="2mm" border="0.5pt solid #CBD5E1">
                                                            <fo:block font-size="8pt" wrap-option="wrap"><xsl:value-of select="uri"/></fo:block>
                                                        </fo:table-cell>
                                                        <fo:table-cell padding="2mm" border="0.5pt solid #CBD5E1">
                                                            <fo:block font-size="9pt"><xsl:value-of select="description"/></fo:block>
                                                        </fo:table-cell>
                                                    </fo:table-row>
                                                </xsl:for-each>
                                            </fo:table-body>
                                        </fo:table>
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
                                                <fo:table-column column-width="25%"/>
                                                <fo:table-column column-width="15%"/>
                                                <fo:table-column column-width="12%"/>
                                                <fo:table-column column-width="20%"/>
                                                <fo:table-column column-width="28%"/>
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
                                                            <fo:block font-weight="bold" font-size="9pt">Restrictions</fo:block>
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
                                                                <fo:block font-size="8pt" wrap-option="wrap"><xsl:value-of select="restrictions"/></fo:block>
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

                                    <!-- Index Section -->
                                    <xsl:if test="xsd-documentation/index/entry">
                                        <fo:block break-before="page"/>
                                        <fo:block id="index" font-size="18pt" font-weight="{$headingBold}" color="{$headingColor}"
                                            text-decoration="{$headingUnderlined}"
                                            space-before="5mm" space-after="10mm">
                                            Index
                                        </fo:block>

                                        <!-- 3-column layout for compact index display -->
                                        <fo:table table-layout="fixed" width="100%">
                                            <fo:table-column column-width="33%"/>
                                            <fo:table-column column-width="33%"/>
                                            <fo:table-column column-width="34%"/>
                                            <fo:table-body>
                                                <xsl:variable name="entries" select="xsd-documentation/index/entry"/>
                                                <xsl:variable name="count" select="count($entries)"/>
                                                <xsl:variable name="perColumn" select="ceiling($count div 3)"/>
                                                <fo:table-row>
                                                    <fo:table-cell padding="2mm">
                                                        <fo:block>
                                                            <xsl:for-each select="$entries[position() &lt;= $perColumn]">
                                                                <fo:block font-size="9pt" space-after="1mm">
                                                                    <xsl:value-of select="."/>
                                                                </fo:block>
                                                            </xsl:for-each>
                                                        </fo:block>
                                                    </fo:table-cell>
                                                    <fo:table-cell padding="2mm">
                                                        <fo:block>
                                                            <xsl:for-each select="$entries[position() &gt; $perColumn and position() &lt;= ($perColumn * 2)]">
                                                                <fo:block font-size="9pt" space-after="1mm">
                                                                    <xsl:value-of select="."/>
                                                                </fo:block>
                                                            </xsl:for-each>
                                                        </fo:block>
                                                    </fo:table-cell>
                                                    <fo:table-cell padding="2mm">
                                                        <fo:block>
                                                            <xsl:for-each select="$entries[position() &gt; ($perColumn * 2)]">
                                                                <fo:block font-size="9pt" space-after="1mm">
                                                                    <xsl:value-of select="."/>
                                                                </fo:block>
                                                            </xsl:for-each>
                                                        </fo:block>
                                                    </fo:table-cell>
                                                </fo:table-row>
                                            </fo:table-body>
                                        </fo:table>
                                    </xsl:if>

                                </fo:flow>
                            </fo:page-sequence>
                        </fo:root>
                    </xsl:template>
                </xsl:stylesheet>
                """;
    }

    // ================= Helper Methods =================

    /** Schemas with more elements than this get no overview diagram: it would not be legible on a page. */
    static final int OVERVIEW_MAX_ELEMENTS = 400;

    /**
     * Adds the overview diagram of the whole schema (as on the HTML overview page) as vector
     * graphics, when the schema diagram is on and the schema is small enough to read on one page.
     */
    private void writeOverviewDiagram(Document doc, Element root) {
        if (!config.isIncludeSchemaDiagram() || tempImageDir == null) {
            return;
        }
        long elements = documentationData.getExtendedXsdElementMap().values().stream()
                .filter(e -> !isContainerElement(e)).count();
        Element overview = doc.createElement("overview-diagram");
        root.appendChild(overview);
        if (elements > OVERVIEW_MAX_ELEMENTS) {
            overview.setAttribute("skipped", String.valueOf(elements));
            return;
        }
        try {
            XsdDocumentationSvgService svgService = new XsdDocumentationSvgService();
            svgService.setDocumentationData(documentationData);
            Path svg = tempImageDir.resolve("overview.svg");
            Files.writeString(svg, svgService.printOverviewSvg(), java.nio.charset.StandardCharsets.UTF_8);
            addElement(doc, overview, "image-path", svg.toUri().toString());
            double pageHeight = PdfElementReferenceBuilder.toMm(config.getPageHeight())
                    - 2 * PdfElementReferenceBuilder.toMm(config.getMarginSize()) - 60;
            PdfElementReferenceBuilder.setImageSize((Element) overview.getLastChild(), svg, config, pageHeight);
        } catch (IOException | RuntimeException e) {
            logger.warn("Failed to draw the overview diagram: {}", e.getMessage());
            root.removeChild(overview);
        }
    }

    /** Marks an entry as linking to the element reference section {@code ref} (if there is one). */
    private static void setRef(Element entry, String ref) {
        if (ref != null) {
            entry.setAttribute("ref", ref);
        }
    }

    private void addElement(Document doc, Element parent, String name, String value) {
        Element element = doc.createElement(name);
        element.setTextContent(value != null ? value : "");
        parent.appendChild(element);
    }

    /** Zero-width space: lets FOP break a long value (an XPath, a pattern) inside a table cell. */
    private static final char BREAK_OPPORTUNITY = '\u200B';

    /** Tokens longer than this get break opportunities every so many characters. */
    private static final int MAX_UNBROKEN = 24;

    private void reportProgress(String message) {
        if (progressListener != null) {
            progressListener.onProgressUpdate(new TaskProgressListener.ProgressUpdate(message,
                    TaskProgressListener.ProgressUpdate.Status.RUNNING, 0));
        }
        logger.debug("PDF generation progress: {}", message);
    }

    /**
     * Returns the text with break opportunities after path and list separators and inside very
     * long tokens, so a cell wraps it instead of letting it run over the column. The text itself
     * is unchanged.
     */
    static String breakable(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length() + 16);
        int run = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            out.append(c);
            if (Character.isWhitespace(c)) {
                run = 0;
            } else if (c == '/' || c == '|' || c == ',' || c == ';') {
                out.append(BREAK_OPPORTUNITY);
                run = 0;
            } else if (++run >= MAX_UNBROKEN) {
                out.append(BREAK_OPPORTUNITY);
                run = 0;
            }
        }
        return out.toString();
    }

    /**
     * Lists the namespaces the schema declares (prefix, URI and what they are: target namespace,
     * XML Schema, imported, or only declared) and the schema's imports, includes, redefines and
     * overrides with their locations.
     */
    private void writeNamespaces(Document doc, Element overview) {
        XsdDocumentationExportSupport.Namespaces namespaces = support().namespaces();
        for (XsdDocumentationExportSupport.NamespaceRow row : namespaces.rows()) {
            namespaceRow(doc, overview, row.prefix(), row.uri(), row.kind());
        }
        for (XsdDocumentationExportSupport.SchemaReference reference : namespaces.references()) {
            Element row = doc.createElement("schema-reference");
            overview.appendChild(row);
            addElement(doc, row, "kind", reference.kind());
            addElement(doc, row, "namespace", reference.namespace());
            addElement(doc, row, "location", breakable(reference.location()));
        }
    }

    private void namespaceRow(Document doc, Element overview, String prefix, String uri, String kind) {
        Element ns = doc.createElement("namespace");
        overview.appendChild(ns);
        addElement(doc, ns, "prefix", prefix);
        addElement(doc, ns, "uri", breakable(uri));
        addElement(doc, ns, "type", kind);
    }

    // ================= Shared with the Word export =================

    private XsdDocumentationExportSupport support;

    /** @return the reading of the schema PDF and Word share, for the current document */
    XsdDocumentationExportSupport support() {
        if (support == null) {
            support = new XsdDocumentationExportSupport(documentationData, includedLanguages);
        }
        return support;
    }

    private XsdExtendedElement findRootElement() {
        return support().findRootElement();
    }

    String declaredType(XsdExtendedElement element) {
        return support().declaredType(element);
    }

    XsdDocumentationExportSupport.TypeUsage typeUsage(String typeName) {
        return support().typeUsage(typeName);
    }

    static String restrictionText(String html) {
        return XsdDocumentationExportSupport.restrictionText(html);
    }

    static String serialize(Node node) {
        return XsdDocumentationExportSupport.serialize(node);
    }

    static String localName(String qName) {
        return XsdDocumentationExportSupport.localName(qName);
    }

    private String extractSchemaName() {
        return support().extractSchemaName();
    }

    private String getBaseType(org.w3c.dom.Element complexTypeElement) {
        return support().getBaseType(complexTypeElement);
    }

    private String getSimpleTypeBase(org.w3c.dom.Element simpleTypeElement) {
        return support().getSimpleTypeBase(simpleTypeElement);
    }

    private String getSimpleTypeFacetsSummary(org.w3c.dom.Element simpleTypeElement) {
        return support().getSimpleTypeFacetsSummary(simpleTypeElement);
    }

    String getFirstDocumentation(XsdExtendedElement element) {
        return support().getFirstDocumentation(element);
    }

    String stripHtml(String text) {
        return support().stripHtml(text);
    }

    private String getRestrictionsSummary(XsdExtendedElement element) {
        return support().getRestrictionsSummary(element);
    }

    String getCardinality(XsdExtendedElement element) {
        return support().getCardinality(element);
    }

    boolean isContainerElement(XsdExtendedElement element) {
        return XsdDocumentationExportSupport.isContainerElement(element);
    }

    String getCleanXPath(XsdExtendedElement element) {
        return support().getCleanXPath(element);
    }

    private List<XsdExtendedElement> deduplicateByType(List<XsdExtendedElement> elements) {
        return support().deduplicateByType(elements);
    }

    // ================= Setters =================

    public void setDocumentationData(XsdDocumentationData documentationData) {
        this.documentationData = documentationData;
    }

    public void setImageService(XsdDocumentationImageService imageService) {
        this.imageService = imageService;
    }

    public void setIncludedLanguages(Set<String> includedLanguages) {
        this.includedLanguages = includedLanguages == null ? new java.util.LinkedHashSet<>() : new java.util.LinkedHashSet<>(includedLanguages);
    }

    public void setProgressListener(TaskProgressListener progressListener) {
        this.progressListener = progressListener;
    }

    /**
     * Sets whether the data dictionary lists each named complex or simple type only once.
     *
     * @param deduplicate true to collapse repeated named types (the HTML option of the same name)
     */
    public void setDeduplicateDataDictionaryByType(boolean deduplicate) {
        this.deduplicateDataDictionaryByType = deduplicate;
    }

    public void setConfig(PdfDocumentationConfig config) {
        this.config = config != null ? config : new PdfDocumentationConfig();
    }

    public PdfDocumentationConfig getConfig() {
        return config;
    }
}
