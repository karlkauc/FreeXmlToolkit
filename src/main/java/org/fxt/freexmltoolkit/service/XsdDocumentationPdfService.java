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

    /**
     * Lists the namespaces the schema declares (prefix, URI and what they are: target namespace,
     * XML Schema, imported, or only declared) and the schema's imports, includes, redefines and
     * overrides with their locations.
     */
    private void writeNamespaces(Document doc, Element overview) {
        org.w3c.dom.Element schema = schemaElement();
        String targetNs = documentationData.getTargetNamespace();
        Map<String, String> importLocations = new java.util.LinkedHashMap<>();
        List<String[]> references = new ArrayList<>();
        if (schema != null) {
            for (Node child = schema.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof org.w3c.dom.Element reference
                        && javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(reference.getNamespaceURI())
                        && List.of("import", "include", "redefine", "override").contains(reference.getLocalName())) {
                    String namespace = reference.getAttribute("namespace");
                    String location = reference.getAttribute("schemaLocation");
                    references.add(new String[]{reference.getLocalName(), namespace, location});
                    if ("import".equals(reference.getLocalName())) {
                        importLocations.put(namespace, location);
                    }
                }
            }
        }

        // The declared prefixes, then namespaces only imported
        Map<String, String> declared = new java.util.LinkedHashMap<>();
        if (schema != null) {
            var attributes = schema.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attribute = attributes.item(i);
                if (javax.xml.XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attribute.getNamespaceURI())) {
                    String prefix = "xmlns".equals(attribute.getNodeName()) ? "(default)" : attribute.getLocalName();
                    declared.put(prefix, attribute.getNodeValue());
                }
            }
        }
        if (declared.isEmpty()) {
            declared.put("xs", javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);
        }
        java.util.Set<String> listed = new java.util.HashSet<>();
        for (Map.Entry<String, String> entry : declared.entrySet()) {
            String uri = entry.getValue();
            namespaceRow(doc, overview, entry.getKey(), uri, namespaceKind(uri, targetNs, importLocations));
            listed.add(uri);
        }
        if (targetNs != null && !targetNs.isEmpty() && !listed.contains(targetNs)) {
            namespaceRow(doc, overview, "-", targetNs, "Target namespace");
            listed.add(targetNs);
        }
        for (String imported : importLocations.keySet()) {
            if (!listed.contains(imported)) {
                namespaceRow(doc, overview, "-", imported.isEmpty() ? "(no namespace)" : imported, "Imported");
            }
        }
        for (String[] reference : references) {
            Element row = doc.createElement("schema-reference");
            overview.appendChild(row);
            addElement(doc, row, "kind", reference[0]);
            addElement(doc, row, "namespace", reference[1].isEmpty() ? "-" : reference[1]);
            addElement(doc, row, "location", breakable(reference[2].isEmpty() ? "-" : reference[2]));
        }
    }

    private static String namespaceKind(String uri, String targetNs, Map<String, String> imports) {
        if (javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(uri)) {
            return "XML Schema";
        }
        if (uri != null && uri.equals(targetNs)) {
            return "Target namespace";
        }
        if ("http://www.w3.org/2001/XMLSchema-instance".equals(uri)) {
            return "XML Schema instance";
        }
        if ("http://www.w3.org/XML/1998/namespace".equals(uri)) {
            return "XML";
        }
        return imports.containsKey(uri) ? "Imported" : "Declared";
    }

    private void namespaceRow(Document doc, Element overview, String prefix, String uri, String kind) {
        Element ns = doc.createElement("namespace");
        overview.appendChild(ns);
        addElement(doc, ns, "prefix", prefix);
        addElement(doc, ns, "uri", breakable(uri));
        addElement(doc, ns, "type", kind);
    }

    /** @return the {@code xs:schema} element of the documented schema, or null */
    private org.w3c.dom.Element schemaElement() {
        List<Node> declarations = new ArrayList<>(documentationData.getGlobalElements());
        declarations.addAll(documentationData.getGlobalComplexTypes());
        declarations.addAll(documentationData.getGlobalSimpleTypes());
        for (Node declaration : declarations) {
            if (declaration.getOwnerDocument() != null && declaration.getOwnerDocument().getDocumentElement() != null) {
                return declaration.getOwnerDocument().getDocumentElement();
            }
        }
        return null;
    }

    /**
     * Lists each named complex or simple type once, at its first occurrence in document order, as
     * the HTML data dictionary does with the same option. Elements of a built-in type or without a
     * type are always kept.
     */
    private List<XsdExtendedElement> deduplicateByType(List<XsdExtendedElement> elements) {
        List<XsdExtendedElement> inOrder = new ArrayList<>(elements);
        inOrder.sort(Comparator.comparingInt(XsdExtendedElement::getCounter));
        Set<String> seenTypes = new java.util.HashSet<>();
        List<XsdExtendedElement> kept = new ArrayList<>();
        for (XsdExtendedElement element : inOrder) {
            String type = element.getElementType();
            if (type == null || type.isBlank() || type.startsWith("xs:") || type.startsWith("xsd:")
                    || seenTypes.add(localName(type))) {
                kept.add(element);
            }
        }
        logger.info("PDF data dictionary deduplicated by type: {} -> {} rows", elements.size(), kept.size());
        return kept;
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
     * Turns the HTML restriction string of an element ({@code Base: xs:string<br />MaxLength: 256})
     * into one line of text ({@code Base: xs:string; MaxLength: 256}).
     */
    static String restrictionText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String text = html.replaceAll("(?i)<br\\s*/?>", "; ")
                .replaceAll("<[^>]+>", "")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&amp;", "&")
                .replaceAll("\\s+", " ")
                .replaceAll("(;\\s*)+", "; ")
                .trim();
        return text.endsWith(";") ? text.substring(0, text.length() - 1).trim() : text;
    }

    /**
     * The global element the schema diagram starts from: the first global element of the schema
     * itself (not a SEQUENCE/CHOICE/ALL container), as on the root page of the HTML documentation.
     */
    private XsdExtendedElement findRootElement() {
        return documentationData.getExtendedXsdElementMap().values().stream()
                .filter(e -> e.getLevel() == 0)
                .filter(e -> !isContainerElement(e))
                .min(Comparator.comparing((XsdExtendedElement e) -> e.getSourceNamespace() != null)
                        .thenComparingInt(XsdExtendedElement::getCounter))
                .orElse(null);
    }

    /**
     * The type an element declares: its {@code type} attribute where it has one. The element map
     * keeps the base type for elements of a simpleContent type (sample data needs it), which is
     * not what a reader looks for.
     */
    String declaredType(XsdExtendedElement element) {
        String declared = declaredTypeAttribute(element);
        if (declared != null) {
            return declared;
        }
        return element.getElementType() != null && !element.getElementType().isEmpty() ? element.getElementType() : "-";
    }

    private static String declaredTypeAttribute(XsdExtendedElement element) {
        Node node = element.getCurrentNode();
        if (node instanceof org.w3c.dom.Element declaration && declaration.hasAttribute("type")) {
            String type = declaration.getAttribute("type").trim();
            return type.isEmpty() ? null : type;
        }
        return null;
    }

    /**
     * Who uses a global type.
     *
     * @param users        the elements and attributes that declare it as their type or whose inline
     *                     type derives from it, by path
     * @param derivedTypes the global types that derive from it ({@code base=})
     */
    record TypeUsage(List<XsdExtendedElement> users, List<String> derivedTypes) {
        static final TypeUsage NONE = new TypeUsage(List.of(), List.of());

        /** @return how often the type is used */
        int count() {
            return users.size() + derivedTypes.size();
        }
    }

    /** The usage of every global type by local name, built once per document. */
    private Map<String, TypeUsage> typeUsages;

    /**
     * @param typeName a type name, with or without prefix
     * @return who uses the global type of that name
     */
    TypeUsage typeUsage(String typeName) {
        return typeName == null ? TypeUsage.NONE : typeUsages().getOrDefault(localName(typeName), TypeUsage.NONE);
    }

    private Map<String, TypeUsage> typeUsages() {
        if (typeUsages != null) {
            return typeUsages;
        }
        Map<String, List<XsdExtendedElement>> users = new java.util.HashMap<>();
        Map<String, List<String>> derived = new java.util.HashMap<>();
        for (XsdExtendedElement element : documentationData.getExtendedXsdElementMap().values()) {
            if (isContainerElement(element)) {
                continue;
            }
            String declared = declaredTypeAttribute(element);
            if (declared != null) {
                users.computeIfAbsent(localName(declared), k -> new ArrayList<>()).add(element);
            } else if (element.getCurrentNode() != null) {
                // An inline type that derives from a named one uses it as well
                for (String base : derivationBases(element.getCurrentNode(), false)) {
                    users.computeIfAbsent(localName(base), k -> new ArrayList<>()).add(element);
                }
            }
            // So do the types of its XSD 1.1 type alternatives
            for (Node child = element.getCurrentNode() == null ? null : element.getCurrentNode().getFirstChild();
                 child != null; child = child.getNextSibling()) {
                if (child instanceof org.w3c.dom.Element alternative && "alternative".equals(alternative.getLocalName())
                        && alternative.hasAttribute("type")) {
                    users.computeIfAbsent(localName(alternative.getAttribute("type")), k -> new ArrayList<>()).add(element);
                }
            }
        }
        List<Node> globalTypes = new ArrayList<>(documentationData.getGlobalComplexTypes());
        globalTypes.addAll(documentationData.getGlobalSimpleTypes());
        for (Node type : globalTypes) {
            String name = type instanceof org.w3c.dom.Element typeElement ? typeElement.getAttribute("name") : "";
            for (String base : derivationBases(type, true)) {
                derived.computeIfAbsent(localName(base), k -> new ArrayList<>()).add(name);
            }
        }
        Map<String, TypeUsage> result = new java.util.HashMap<>();
        java.util.Set<String> names = new java.util.HashSet<>(users.keySet());
        names.addAll(derived.keySet());
        for (String name : names) {
            List<XsdExtendedElement> typeUsers = new ArrayList<>(users.getOrDefault(name, List.of()));
            typeUsers.sort(Comparator.comparing(this::getCleanXPath));
            result.put(name, new TypeUsage(typeUsers, derived.getOrDefault(name, List.of())));
        }
        typeUsages = result;
        return result;
    }

    /**
     * The {@code base} attributes of the extensions and restrictions of a type definition. For an
     * element, only its own inline type is looked at, not nested element declarations.
     */
    private static List<String> derivationBases(Node node, boolean isType) {
        List<String> bases = new ArrayList<>();
        java.util.ArrayDeque<Node> pending = new java.util.ArrayDeque<>();
        pending.push(node);
        while (!pending.isEmpty()) {
            Node current = pending.pop();
            for (Node child = current.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (!(child instanceof org.w3c.dom.Element childElement)) {
                    continue;
                }
                String name = childElement.getLocalName();
                if ("element".equals(name) || "attribute".equals(name)) {
                    continue; // nested declarations count for themselves
                }
                if (("extension".equals(name) || "restriction".equals(name)) && childElement.hasAttribute("base")) {
                    bases.add(childElement.getAttribute("base"));
                }
                if (isType || "complexType".equals(name) || "simpleType".equals(name)
                        || "complexContent".equals(name) || "simpleContent".equals(name)
                        || "extension".equals(name) || "restriction".equals(name)) {
                    pending.push(childElement);
                }
            }
        }
        return bases;
    }

    /** @return a schema component as indented XML text (its XSD source), empty if it cannot be written */
    static String serialize(Node node) {
        try {
            javax.xml.transform.Transformer transformer = javax.xml.transform.TransformerFactory
                    .newDefaultInstance().newTransformer();
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            java.io.StringWriter out = new java.io.StringWriter();
            transformer.transform(new DOMSource(node), new javax.xml.transform.stream.StreamResult(out));
            // Drop the blank lines the identity transform leaves from the source's whitespace
            return out.toString().replaceAll("(?m)^[ \\t]*\\r?\\n", "").strip();
        } catch (javax.xml.transform.TransformerException e) {
            logger.debug("Could not write the source of {}: {}", node.getNodeName(), e.getMessage());
            return "";
        }
    }

    static String localName(String qName) {
        return qName.substring(qName.indexOf(':') + 1);
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

    String getFirstDocumentation(XsdExtendedElement element) {
        Map<String, String> docs = element.getLanguageDocumentation();
        if (docs == null || docs.isEmpty()) {
            return "";
        }

        // Apply language filter if set
        if (includedLanguages != null && !includedLanguages.isEmpty()) {
            for (String lang : includedLanguages) {
                if (docs.containsKey(lang)) {
                    return stripHtml(docs.get(lang));
                }
            }
        }

        // Fallback: default or first available language
        String doc = docs.get("default");
        if (doc == null) {
            doc = docs.values().iterator().next();
        }
        return stripHtml(doc);
    }

    /**
     * Strips HTML tags from a string.
     */
    String stripHtml(String text) {
        return MarkdownSupport.toPlainText(text);
    }

    /**
     * Gets a summary of restrictions for an element (facets, constraints, assertions).
     */
    private String getRestrictionsSummary(XsdExtendedElement element) {
        if (element == null) {
            return "";
        }

        // The restriction string is HTML (lines separated by <br />): make it text
        String restrictionString = restrictionText(element.getXsdRestrictionString());
        if (!restrictionString.isEmpty()) {
            return restrictionString;
        }

        // Check for identity constraints
        var constraints = element.getIdentityConstraints();
        if (constraints != null && !constraints.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (var constraint : constraints) {
                if (!sb.isEmpty()) {
                    sb.append("; ");
                }
                sb.append(constraint.getType()).append(": ").append(constraint.getName());
            }
            return sb.toString();
        }

        // Check for assertions (XSD 1.1)
        var assertions = element.getAssertions();
        if (assertions != null && !assertions.isEmpty()) {
            return assertions.size() + " assertion(s)";
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
    String getCardinality(XsdExtendedElement element) {
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
    boolean isContainerElement(XsdExtendedElement element) {
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
    String getCleanXPath(XsdExtendedElement element) {
        if (element == null) {
            return "";
        }

        // Reuse the cached value if it was already computed (by any exporter) in this run.
        String cached = element.getCleanXPathCache();
        if (cached != null) {
            return cached;
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

        String cleanXPath = "/" + String.join("/", pathParts);
        element.setCleanXPathCache(cleanXPath);
        return cleanXPath;
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
