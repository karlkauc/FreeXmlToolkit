package org.fxt.freexmltoolkit.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.LinkedFileInfo;
import org.fxt.freexmltoolkit.domain.LinkedFileInfo.LinkType;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service for detecting linked files in XML, XSD, and XSLT documents.
 * Used by the Unified Editor for auto-linking functionality.
 *
 * @since 2.0
 */
public class LinkedFileDetector {

    private static final Logger logger = LogManager.getLogger(LinkedFileDetector.class);

    private static final String XSI_NAMESPACE = "http://www.w3.org/2001/XMLSchema-instance";
    private static final String XSD_NAMESPACE = "http://www.w3.org/2001/XMLSchema";
    private static final String XSLT_NAMESPACE = "http://www.w3.org/1999/XSL/Transform";

    private final DocumentBuilderFactory documentBuilderFactory;
    private final XMLInputFactory xmlInputFactory;

    /**
     * Creates a new LinkedFileDetector.
     */
    public LinkedFileDetector() {
        this.documentBuilderFactory = DocumentBuilderFactory.newInstance();
        this.documentBuilderFactory.setNamespaceAware(true);

        this.xmlInputFactory = XMLInputFactory.newInstance();
        // Security settings
        this.xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        this.xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    }

    /**
     * Detects all linked files from any supported document type.
     *
     * @param file the file to analyze
     * @return list of detected linked files
     */
    public List<LinkedFileInfo> detectLinks(File file) {
        if (file == null || !file.exists()) {
            return Collections.emptyList();
        }

        String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".xsd")) {
            return detectXsdLinks(file);
        } else if (fileName.endsWith(".xsl") || fileName.endsWith(".xslt")) {
            return detectXsltLinks(file);
        } else if (fileName.endsWith(".sch") || fileName.endsWith(".schematron")) {
            return detectSchematronLinks(file);
        } else {
            // Assume XML
            return detectXmlLinks(file);
        }
    }

    /**
     * Detects linked files from an XML document.
     * Finds xsi:schemaLocation, xsi:noNamespaceSchemaLocation, and xml-stylesheet.
     *
     * @param xmlFile the XML file
     * @return list of detected linked files
     */
    public List<LinkedFileInfo> detectXmlLinks(File xmlFile) {
        if (xmlFile == null || !xmlFile.exists()) {
            return Collections.emptyList();
        }

        List<LinkedFileInfo> links = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(xmlFile)) {
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            Document doc = builder.parse(fis);
            Element root = doc.getDocumentElement();

            // Check xsi:schemaLocation
            String schemaLocation = root.getAttributeNS(XSI_NAMESPACE, "schemaLocation");
            if (schemaLocation == null || schemaLocation.isEmpty()) {
                schemaLocation = root.getAttribute("xsi:schemaLocation");
            }

            if (schemaLocation != null && !schemaLocation.isEmpty()) {
                // Format: "namespace1 location1 namespace2 location2 ..."
                String[] parts = schemaLocation.trim().split("\\s+");
                for (int i = 1; i < parts.length; i += 2) {
                    String location = parts[i];
                    String namespace = i > 0 ? parts[i - 1] : null;
                    LinkedFileInfo link = createLink(xmlFile, location, LinkType.XSD_SCHEMA_LOCATION, namespace);
                    links.add(link);
                    logger.debug("Found schemaLocation: {} (namespace: {})", location, namespace);
                }
            }

            // Check xsi:noNamespaceSchemaLocation
            String noNsLocation = root.getAttributeNS(XSI_NAMESPACE, "noNamespaceSchemaLocation");
            if (noNsLocation == null || noNsLocation.isEmpty()) {
                noNsLocation = root.getAttribute("xsi:noNamespaceSchemaLocation");
            }

            if (noNsLocation != null && !noNsLocation.isEmpty()) {
                LinkedFileInfo link = createLink(xmlFile, noNsLocation, LinkType.XSD_NO_NAMESPACE_LOCATION, null);
                links.add(link);
                logger.debug("Found noNamespaceSchemaLocation: {}", noNsLocation);
            }

        } catch (Exception e) {
            logger.warn("Failed to parse XML for link detection: {}", e.getMessage());
        }

        // Check for xml-stylesheet processing instruction using StAX
        links.addAll(detectXmlStylesheets(xmlFile));

        return links;
    }

    /**
     * Detects xml-stylesheet processing instructions.
     */
    private List<LinkedFileInfo> detectXmlStylesheets(File xmlFile) {
        List<LinkedFileInfo> links = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(xmlFile)) {
            XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(fis);

            while (reader.hasNext()) {
                int event = reader.next();

                if (event == XMLStreamConstants.PROCESSING_INSTRUCTION) {
                    String target = reader.getPITarget();
                    if ("xml-stylesheet".equals(target)) {
                        String data = reader.getPIData();
                        String href = extractHref(data);
                        if (href != null) {
                            LinkedFileInfo link = createLink(xmlFile, href, LinkType.XML_STYLESHEET, null);
                            links.add(link);
                            logger.debug("Found xml-stylesheet: {}", href);
                        }
                    }
                }

                // Stop after root element starts
                if (event == XMLStreamConstants.START_ELEMENT) {
                    break;
                }
            }
            reader.close();
        } catch (Exception e) {
            logger.warn("Failed to detect xml-stylesheet: {}", e.getMessage());
        }

        return links;
    }

    /**
     * Detects linked files from an XSD schema.
     * Finds xs:import, xs:include, and xs:redefine.
     *
     * @param xsdFile the XSD file
     * @return list of detected linked files
     */
    public List<LinkedFileInfo> detectXsdLinks(File xsdFile) {
        if (xsdFile == null || !xsdFile.exists()) {
            return Collections.emptyList();
        }

        List<LinkedFileInfo> links = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(xsdFile)) {
            XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(fis);

            while (reader.hasNext()) {
                int event = reader.next();

                if (event == XMLStreamConstants.START_ELEMENT) {
                    String namespaceURI = reader.getNamespaceURI();
                    String localName = reader.getLocalName();

                    if (XSD_NAMESPACE.equals(namespaceURI)) {
                        String schemaLocation = reader.getAttributeValue(null, "schemaLocation");

                        if ("import".equals(localName) && schemaLocation != null) {
                            String namespace = reader.getAttributeValue(null, "namespace");
                            LinkedFileInfo link = createLink(xsdFile, schemaLocation, LinkType.XSD_IMPORT, namespace);
                            links.add(link);
                            logger.debug("Found xs:import: {} (namespace: {})", schemaLocation, namespace);
                        } else if ("include".equals(localName) && schemaLocation != null) {
                            LinkedFileInfo link = createLink(xsdFile, schemaLocation, LinkType.XSD_INCLUDE, null);
                            links.add(link);
                            logger.debug("Found xs:include: {}", schemaLocation);
                        } else if ("redefine".equals(localName) && schemaLocation != null) {
                            LinkedFileInfo link = createLink(xsdFile, schemaLocation, LinkType.XSD_REDEFINE, null);
                            links.add(link);
                            logger.debug("Found xs:redefine: {}", schemaLocation);
                        }
                    }
                }
            }
            reader.close();
        } catch (Exception e) {
            logger.warn("Failed to parse XSD for link detection: {}", e.getMessage());
        }

        return links;
    }

    /**
     * Detects linked files from an XSLT stylesheet.
     * Finds xsl:import and xsl:include.
     *
     * @param xsltFile the XSLT file
     * @return list of detected linked files
     */
    public List<LinkedFileInfo> detectXsltLinks(File xsltFile) {
        if (xsltFile == null || !xsltFile.exists()) {
            return Collections.emptyList();
        }

        List<LinkedFileInfo> links = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(xsltFile)) {
            XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(fis);

            while (reader.hasNext()) {
                int event = reader.next();

                if (event == XMLStreamConstants.START_ELEMENT) {
                    String namespaceURI = reader.getNamespaceURI();
                    String localName = reader.getLocalName();

                    if (XSLT_NAMESPACE.equals(namespaceURI)) {
                        String href = reader.getAttributeValue(null, "href");

                        if ("import".equals(localName) && href != null) {
                            LinkedFileInfo link = createLink(xsltFile, href, LinkType.XSLT_IMPORT, null);
                            links.add(link);
                            logger.debug("Found xsl:import: {}", href);
                        } else if ("include".equals(localName) && href != null) {
                            LinkedFileInfo link = createLink(xsltFile, href, LinkType.XSLT_INCLUDE, null);
                            links.add(link);
                            logger.debug("Found xsl:include: {}", href);
                        }
                    }
                }
            }
            reader.close();
        } catch (Exception e) {
            logger.warn("Failed to parse XSLT for link detection: {}", e.getMessage());
        }

        return links;
    }

    /**
     * Detects linked files from a Schematron file.
     * Currently just parses as XML for any XSD references.
     *
     * @param schFile the Schematron file
     * @return list of detected linked files
     */
    public List<LinkedFileInfo> detectSchematronLinks(File schFile) {
        // Schematron files are XML, so use XML detection
        return detectXmlLinks(schFile);
    }

    /**
     * Creates a LinkedFileInfo, resolving relative paths.
     */
    private LinkedFileInfo createLink(File sourceFile, String referencePath, LinkType linkType, String namespace) {
        if (referencePath == null || referencePath.isEmpty()) {
            return LinkedFileInfo.unresolved(sourceFile, "", linkType);
        }

        // Check if it's a URL
        if (referencePath.startsWith("http://") || referencePath.startsWith("https://")) {
            // Remote URL - mark as unresolved (can't open remote files)
            return LinkedFileInfo.unresolved(sourceFile, referencePath, linkType, namespace);
        }

        // Resolve relative path
        File resolvedFile = resolvePath(sourceFile, referencePath);

        if (resolvedFile != null && resolvedFile.exists()) {
            return LinkedFileInfo.resolved(sourceFile, referencePath, resolvedFile, linkType, namespace);
        } else {
            return LinkedFileInfo.unresolved(sourceFile, referencePath, linkType, namespace);
        }
    }

    /**
     * Resolves a relative path against a base file's directory.
     */
    private File resolvePath(File baseFile, String relativePath) {
        if (baseFile == null || relativePath == null) {
            return null;
        }

        try {
            File parentDir = baseFile.getParentFile();
            if (parentDir == null) {
                return new File(relativePath);
            }

            // Handle various path formats
            String normalizedPath = relativePath.replace('\\', '/');
            File resolved = new File(parentDir, normalizedPath);

            return resolved.getCanonicalFile();
        } catch (Exception e) {
            logger.warn("Failed to resolve path: {} relative to {}", relativePath, baseFile);
            return null;
        }
    }

    /**
     * Extracts href value from xml-stylesheet processing instruction data.
     */
    private String extractHref(String piData) {
        if (piData == null) {
            return null;
        }

        // Parse: type="text/xsl" href="stylesheet.xsl"
        int hrefIndex = piData.indexOf("href=");
        if (hrefIndex == -1) {
            return null;
        }

        int start = hrefIndex + 5;
        if (start >= piData.length()) {
            return null;
        }

        char quote = piData.charAt(start);
        if (quote != '"' && quote != '\'') {
            return null;
        }

        int end = piData.indexOf(quote, start + 1);
        if (end == -1) {
            return null;
        }

        return piData.substring(start + 1, end);
    }
}
