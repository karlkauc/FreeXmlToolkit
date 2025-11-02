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
import org.fxt.freexmltoolkit.domain.*;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement.DocumentationInfo;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement.RestrictionInfo;
import org.fxt.freexmltoolkit.service.TaskProgressListener.ProgressUpdate;
import org.fxt.freexmltoolkit.service.TaskProgressListener.ProgressUpdate.Status;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

public class XsdDocumentationService {

    static final int MAX_ALLOWED_DEPTH = 99;
    private static final Logger logger = LogManager.getLogger(XsdDocumentationService.class);
    private static final String NS_PREFIX = "xs";
    private static final String NS_URI = "http://www.w3.org/2001/XMLSchema";
    private static final String ALTOVA_NS_URI = "http://www.altova.com/xml-schema-extensions";

    String xsdFilePath;
    public XsdDocumentationData xsdDocumentationData = new XsdDocumentationData();

    int counter;
    boolean parallelProcessing = true;

    public enum ImageOutputMethod {SVG, PNG}

    public ImageOutputMethod imageOutputMethod = ImageOutputMethod.SVG;
    Boolean useMarkdownRenderer = true;
    Boolean includeTypeDefinitionsInSourceCode = false;

    private TaskProgressListener progressListener;
    private final XsdSampleDataGenerator xsdSampleDataGenerator = new XsdSampleDataGenerator();
    private final PropertiesService propertiesService = PropertiesServiceImpl.getInstance();
    private final RandomGenerator random = RandomGenerator.getDefault();
    XsdDocumentationHtmlService xsdDocumentationHtmlService = new XsdDocumentationHtmlService();
    XsdDocumentationSvgService xsdDocumentationSvgService = new XsdDocumentationSvgService();

    // DOM/XPath related fields
    private XPath xpath;
    private Document doc;
    private Map<String, Node> elementMap = new HashMap<>();
    private Map<String, Node> complexTypeMap = new HashMap<>();
    private Map<String, Node> simpleTypeMap = new HashMap<>();
    private Map<String, Node> groupMap = new HashMap<>();
    private Map<String, Node> attributeGroupMap = new HashMap<>();

    // Provides a separate, thread-safe Transformer instance for each thread.
    // This avoids the cost of constant recreation in a parallel environment.
    private static final ThreadLocal<Transformer> transformerThreadLocal = ThreadLocal.withInitial(() -> {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            // Security feature to block external DTDs and entities
            factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", String.valueOf(PropertiesServiceImpl.getInstance().getXmlIndentSpaces()));
            return transformer;
        } catch (Exception e) {
            // If initialization fails, a RuntimeException is thrown.
            // This is appropriate in a ThreadLocal initializer.
            throw new RuntimeException("Failed to initialize thread-local Transformer", e);
        }
    });

    public void setProgressListener(TaskProgressListener progressListener) {
        this.progressListener = progressListener;
    }

    public void setXsdFilePath(String xsdFilePath) {
        this.xsdFilePath = xsdFilePath;
        this.xsdDocumentationData.setXsdFilePath(xsdFilePath);
    }

    public void setMethod(ImageOutputMethod method) {
        this.imageOutputMethod = method;
    }

    public void setParallelProcessing(boolean parallelProcessing) {
        this.parallelProcessing = parallelProcessing;
    }

    public void setUseMarkdownRenderer(Boolean useMarkdownRenderer) {
        this.useMarkdownRenderer = useMarkdownRenderer;
    }

    public void setIncludeTypeDefinitionsInSourceCode(Boolean includeTypeDefinitionsInSourceCode) {
        this.includeTypeDefinitionsInSourceCode = includeTypeDefinitionsInSourceCode;
    }

    public void generateXsdDocumentation(File outputDirectory) throws Exception {
        logger.debug("Starting documentation generation...");
        processXsd(this.useMarkdownRenderer);

        xsdDocumentationHtmlService.setOutputDirectory(outputDirectory);
        xsdDocumentationHtmlService.setDocumentationData(xsdDocumentationData);
        xsdDocumentationHtmlService.setXsdDocumentationService(this);

        xsdDocumentationSvgService.setOutputDirectory(outputDirectory);
        xsdDocumentationSvgService.setDocumentationData(xsdDocumentationData);

        // The ImageService is now centrally initialized here so it's ready for pre-creation.
        xsdDocumentationHtmlService.xsdDocumentationImageService = new XsdDocumentationImageService(xsdDocumentationData.getExtendedXsdElementMap());

        executeAndTrack("Copying resources", xsdDocumentationHtmlService::copyResources);
        executeAndTrack("Generating root page", xsdDocumentationHtmlService::generateRootPage);
        executeAndTrack("Generating SVG page", xsdDocumentationSvgService::generateSvgPage);
        executeAndTrack("Generating list of complex types", xsdDocumentationHtmlService::generateComplexTypesListPage);
        executeAndTrack("Generating list of simple types", xsdDocumentationHtmlService::generateSimpleTypesListPage);
        executeAndTrack("Generating data dictionary", xsdDocumentationHtmlService::generateDataDictionaryPage);
        executeAndTrack("Generating search index", xsdDocumentationHtmlService::generateSearchIndex);

        if (parallelProcessing) {
            executeAndTrack("Generating detail pages for complex types (parallel)", xsdDocumentationHtmlService::generateComplexTypePagesInParallel);
            executeAndTrack("Generating detail pages for simple types (parallel)", xsdDocumentationHtmlService::generateSimpleTypePagesInParallel);
            executeAndTrack("Generating detail pages for elements (parallel)", xsdDocumentationHtmlService::generateDetailsPagesInParallel);
        } else {
            executeAndTrack("Generating detail pages for complex types", xsdDocumentationHtmlService::generateComplexTypePages);
            executeAndTrack("Generating detail pages for simple types", xsdDocumentationHtmlService::generateSimpleTypePages);
            executeAndTrack("Generating detail pages for elements", xsdDocumentationHtmlService::generateDetailPages);
        }
    }

    public void processXsd(Boolean useMarkdownRenderer) throws Exception {
        this.useMarkdownRenderer = useMarkdownRenderer;
        initializeXmlTools();

        // Process all schemas (including xs:include and xs:import)
        processAllSchemas();

        // Parse the main XSD file
        String xsdContent = Files.readString(new File(xsdFilePath).toPath(), StandardCharsets.UTF_8);
        this.doc = parseXsdContent(xsdContent);

        // Initialize caches for global definitions from all processed schemas
        initializeCachesFromAllSchemas();

        // Populate XsdDocumentationData
        populateDocumentationData();

        // Start traversal from global elements
        counter = 0;
        for (Node globalElement : xsdDocumentationData.getGlobalElements()) {
            String rootName = getAttributeValue(globalElement, "name");
            traverseNode(globalElement, "/" + rootName, null, 0, new HashSet<>());
        }

        // Build the type usage map after traversal
        buildTypeUsageMap();
    }

    /**
     * Gets mandatory child elements for a given element name based on XSD schema analysis.
     *
     * @param elementName The name of the parent element
     * @return List of mandatory child element information
     */
    public List<MandatoryChildInfo> getMandatoryChildElements(String elementName) {
        return getMandatoryChildElements(elementName, null);
    }

    /**
     * Gets mandatory child elements for a given element name with XPath context for disambiguation.
     *
     * @param elementName The name of the parent element
     * @param contextPath Optional XPath context to help find the correct element (e.g., "ControlData/DataSupplier")
     * @return List of mandatory child element information
     */
    public List<MandatoryChildInfo> getMandatoryChildElements(String elementName, String contextPath) {
        List<MandatoryChildInfo> mandatoryChildren = new ArrayList<>();

        try {
            // Normalize element name (strip namespace prefix if present)
            String cleanElementName = elementName;
            int colonIdx = cleanElementName.indexOf(':');
            if (colonIdx > -1 && colonIdx < cleanElementName.length() - 1) {
                cleanElementName = cleanElementName.substring(colonIdx + 1);
            }

            // First approach: Use the already processed XsdDocumentationData with XPath context
            if (xsdDocumentationData != null && xsdDocumentationData.getExtendedXsdElementMap() != null) {
                // Look for the element in the processed data, prioritizing specific XPath matches
                XsdExtendedElement targetElement = findElementByNameAndContext(cleanElementName, contextPath);

                if (targetElement != null && targetElement.hasChildren()) {
                    // Found the element, now extract its mandatory children using the correct XPath context
                    for (String childXPath : targetElement.getChildren()) {
                        XsdExtendedElement childElement = xsdDocumentationData.getExtendedXsdElementMap().get(childXPath);
                        if (childElement != null && childElement.isMandatory()) {
                            // Skip attributes (elements starting with @)
                            if (childElement.getElementName().startsWith("@")) {
                                logger.debug("Skipping attribute: {} (xpath={})", childElement.getElementName(), childXPath);
                                continue;
                            }

                            // For mandatory children, only create empty elements without recursive nesting
                            MandatoryChildInfo childInfo = new MandatoryChildInfo(
                                    childElement.getElementName(),
                                    1, // Default minOccurs for mandatory elements
                                    1, // Default maxOccurs
                                    new ArrayList<>() // No nested children - just create empty elements
                            );
                            mandatoryChildren.add(childInfo);
                            logger.debug("Added mandatory child from processed data: {} (xpath={}, isAttribute=false)",
                                    childElement.getElementName(), childXPath);
                        }
                    }
                    if (!mandatoryChildren.isEmpty()) {
                        logger.debug("Found {} mandatory children for element '{}' using processed data with XPath context",
                                mandatoryChildren.size(), cleanElementName);
                        return mandatoryChildren;
                    }
                }
            }

            // Second approach: Try to find in global element map (legacy behavior)
            Node elementNode = elementMap.get(cleanElementName);
            if (elementNode == null) {
                // Fallback: try to find a global complexType with the same name and use its content model
                Node complexTypeNode = complexTypeMap.get(cleanElementName);
                if (complexTypeNode != null) {
                    Node contentModel = findContentModel(complexTypeNode, null);
                    if (contentModel != null) {
                        extractMandatoryChildren(contentModel, mandatoryChildren);
                        logger.debug("Found {} mandatory children for complexType '{}' (no global element)", mandatoryChildren.size(), cleanElementName);
                        return mandatoryChildren;
                    }
                }
                logger.debug("Element '{}' not found in schema (after normalization)", cleanElementName);
                return mandatoryChildren;
            }

            // Get the type definition for the element
            Node typeDefinition = findTypeDefinition(elementNode, getAttributeValue(elementNode, "type"));
            Node contentModel = findContentModel(elementNode, typeDefinition);

            if (contentModel != null) {
                extractMandatoryChildren(contentModel, mandatoryChildren);
            }

            logger.debug("Found {} mandatory children for element '{}'", mandatoryChildren.size(), cleanElementName);

        } catch (Exception e) {
            logger.error("Error getting mandatory children for element '{}': {}", elementName, e.getMessage(), e);
        }

        return mandatoryChildren;
    }

    /**
     * Finds an element by name and optional context path, prioritizing exact XPath matches.
     * This helps distinguish between multiple elements with the same name in different contexts.
     *
     * @param elementName The name of the element to find
     * @param contextPath Optional context path to help disambiguate (e.g. "ControlData/DataSupplier")
     * @return The best matching XsdExtendedElement or null if not found
     */
    private XsdExtendedElement findElementByNameAndContext(String elementName, String contextPath) {
        if (xsdDocumentationData == null || xsdDocumentationData.getExtendedXsdElementMap() == null) {
            return null;
        }

        XsdExtendedElement bestMatch = null;
        int bestScore = -1;

        for (Map.Entry<String, XsdExtendedElement> entry : xsdDocumentationData.getExtendedXsdElementMap().entrySet()) {
            String xpath = entry.getKey();
            XsdExtendedElement element = entry.getValue();

            if (elementName.equals(element.getElementName())) {
                int score = calculateContextScore(xpath, contextPath);
                if (score > bestScore) {
                    bestScore = score;
                    bestMatch = element;
                }
            }
        }

        if (bestMatch != null) {
            logger.debug("Found best match for element '{}': xpath='{}' (score={})",
                    elementName, bestMatch.getCurrentXpath(), bestScore);
        }

        return bestMatch;
    }

    /**
     * Calculates a context score for XPath matching.
     * Higher scores indicate better matches based on context.
     */
    private int calculateContextScore(String xpath, String contextPath) {
        if (xpath == null) return 0;

        // Base score - prefer shorter, more specific paths
        int score = 100 - (xpath.split("/").length * 5);

        // Boost score for root-level elements
        if (xpath.startsWith("/") && xpath.indexOf('/', 1) == -1) {
            score += 50;
        }

        // If context is provided, significantly boost score for exact matches
        if (contextPath != null && !contextPath.isEmpty()) {
            // Exact context match gets highest score
            if (xpath.contains(contextPath)) {
                score += 200; // Strong boost for context match

                // Extra bonus if the context path is at the end (more specific)
                if (xpath.endsWith("/" + contextPath.substring(contextPath.lastIndexOf('/') + 1))) {
                    score += 100;
                }
            }

            // Penalty for paths that don't match the expected context
            // This helps avoid picking wrong elements with same name
            String[] contextParts = contextPath.split("/");
            String[] xpathParts = xpath.split("/");

            boolean hasContextMismatch = false;
            for (String contextPart : contextParts) {
                boolean found = false;
                for (String xpathPart : xpathParts) {
                    if (contextPart.equals(xpathPart)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    hasContextMismatch = true;
                    break;
                }
            }

            if (hasContextMismatch) {
                score -= 150; // Strong penalty for context mismatch
            }
        }

        return Math.max(0, score);
    }

    /**
     * Gets mandatory child elements for a specific XPath, ensuring we use the correct context.
     * This method looks up children directly by their parent's XPath.
     *
     * @param parentXPath The XPath of the parent element
     * @return List of mandatory child element information
     */
    private List<MandatoryChildInfo> getMandatoryChildElementsByXPath(String parentXPath) {
        List<MandatoryChildInfo> mandatoryChildren = new ArrayList<>();

        if (xsdDocumentationData == null || xsdDocumentationData.getExtendedXsdElementMap() == null) {
            return mandatoryChildren;
        }

        XsdExtendedElement parentElement = xsdDocumentationData.getExtendedXsdElementMap().get(parentXPath);
        if (parentElement == null || !parentElement.hasChildren()) {
            return mandatoryChildren;
        }

        // Process children directly using their XPath
        for (String childXPath : parentElement.getChildren()) {
            XsdExtendedElement childElement = xsdDocumentationData.getExtendedXsdElementMap().get(childXPath);
            if (childElement != null && childElement.isMandatory()) {
                // Skip attributes (elements starting with @)
                if (childElement.getElementName().startsWith("@")) {
                    logger.debug("Skipping attribute: {} (xpath={})", childElement.getElementName(), childXPath);
                    continue;
                }

                // For mandatory children, only create empty elements without recursive nesting
                // This prevents complex choice elements from being expanded
                MandatoryChildInfo childInfo = new MandatoryChildInfo(
                        childElement.getElementName(),
                        1, // Default minOccurs for mandatory elements
                        1, // Default maxOccurs
                        new ArrayList<>() // No nested children - just create empty elements
                );
                mandatoryChildren.add(childInfo);
                logger.debug("Added mandatory child by XPath: {} (xpath={}, isAttribute=false)",
                        childElement.getElementName(), childXPath);
            }
        }

        return mandatoryChildren;
    }

    /**
     * Gets all child elements (mandatory and optional) for a given element name.
     * This method is similar to getMandatoryChildElements but returns all children regardless of minOccurs.
     *
     * @param elementName The name of the parent element
     * @return List of all child element information
     */
    public List<MandatoryChildInfo> getAllChildElements(String elementName) {
        List<MandatoryChildInfo> allChildren = new ArrayList<>();

        try {
            // Normalize element name (strip namespace prefix if present)
            String cleanElementName = elementName;
            int colonIdx = cleanElementName.indexOf(':');
            if (colonIdx > -1 && colonIdx < cleanElementName.length() - 1) {
                cleanElementName = cleanElementName.substring(colonIdx + 1);
            }

            // First approach: Use the already processed XsdDocumentationData
            if (xsdDocumentationData != null && xsdDocumentationData.getExtendedXsdElementMap() != null) {
                // Look for the element in the processed data
                for (Map.Entry<String, XsdExtendedElement> entry : xsdDocumentationData.getExtendedXsdElementMap().entrySet()) {
                    XsdExtendedElement element = entry.getValue();
                    if (cleanElementName.equals(element.getElementName()) && element.hasChildren()) {
                        // Found the element, now extract all its children (mandatory and optional)
                        for (String childXPath : element.getChildren()) {
                            XsdExtendedElement childElement = xsdDocumentationData.getExtendedXsdElementMap().get(childXPath);
                            if (childElement != null) { // Include all children, not just mandatory
                                // Check if this child has its own children
                                List<MandatoryChildInfo> nestedChildren = getAllChildElements(childElement.getElementName());

                                // Parse minOccurs from current node if available
                                int minOccurs = 1; // Default
                                if (childElement.getCurrentNode() != null) {
                                    String minOccursStr = getAttributeValue(childElement.getCurrentNode(), "minOccurs", "1");
                                    try {
                                        minOccurs = Integer.parseInt(minOccursStr);
                                    } catch (NumberFormatException e) {
                                        minOccurs = 1;
                                    }
                                }

                                MandatoryChildInfo childInfo = new MandatoryChildInfo(
                                        childElement.getElementName().startsWith("@")
                                                ? childElement.getElementName().substring(1) // Remove @ for attributes
                                                : childElement.getElementName(),
                                        minOccurs,
                                        1, // Default maxOccurs
                                        nestedChildren
                                );
                                allChildren.add(childInfo);
                                logger.debug("Added child from processed data: {} (minOccurs={}, hasChildren={})",
                                        childElement.getElementName(), minOccurs, !nestedChildren.isEmpty());
                            }
                        }
                        if (!allChildren.isEmpty()) {
                            logger.debug("Found {} child elements for element '{}' using processed data",
                                    allChildren.size(), cleanElementName);
                            return allChildren;
                        }
                    }
                }
            }

            // Second approach: Try to find in global element map (legacy behavior)
            Node elementNode = elementMap.get(cleanElementName);
            if (elementNode == null) {
                // Fallback: try to find a global complexType with the same name and use its content model
                Node complexTypeNode = complexTypeMap.get(cleanElementName);
                if (complexTypeNode != null) {
                    Node contentModel = findContentModel(complexTypeNode, null);
                    if (contentModel != null) {
                        extractAllChildren(contentModel, allChildren);
                        logger.debug("Found {} child elements for complexType '{}' (no global element)", allChildren.size(), cleanElementName);
                        return allChildren;
                    }
                }
                logger.debug("Element '{}' not found in schema (after normalization)", cleanElementName);
                return allChildren;
            }

            // Get the type definition for the element
            Node typeDefinition = findTypeDefinition(elementNode, getAttributeValue(elementNode, "type"));
            Node contentModel = findContentModel(elementNode, typeDefinition);

            if (contentModel != null) {
                extractAllChildren(contentModel, allChildren);
            }

            logger.debug("Found {} child elements for element '{}'", allChildren.size(), cleanElementName);

        } catch (Exception e) {
            logger.error("Error getting all child elements for element '{}': {}", elementName, e.getMessage(), e);
        }

        return allChildren;
    }

    /**
     * Recursively extracts mandatory child elements from XSD content model (sequence, choice, etc.).
     */
    private void extractMandatoryChildren(Node contentNode, List<MandatoryChildInfo> mandatoryChildren) {
        if (contentNode == null) return;

        String localName = contentNode.getLocalName();

        if ("sequence".equals(localName) || "all".equals(localName)) {
            // For sequence and all, process all child elements
            for (Node child : getDirectChildElements(contentNode)) {
                if ("element".equals(child.getLocalName())) {
                    processMandatoryElement(child, mandatoryChildren);
                } else if ("sequence".equals(child.getLocalName()) || "choice".equals(child.getLocalName()) || "all".equals(child.getLocalName())) {
                    // Recursively process nested compositors
                    extractMandatoryChildren(child, mandatoryChildren);
                }
            }
        } else if ("choice".equals(localName)) {
            // For choice, we need to handle differently - usually only one child is mandatory
            // For now, we'll add the first mandatory child from the choice
            for (Node child : getDirectChildElements(contentNode)) {
                if ("element".equals(child.getLocalName())) {
                    if (isMandatoryElement(child)) {
                        processMandatoryElement(child, mandatoryChildren);
                        break; // Only take first mandatory element from choice
                    }
                }
            }
        } else if ("extension".equals(localName)) {
            // Handle extension - process base type and then current content
            String baseType = getAttributeValue(contentNode, "base");
            if (baseType != null) {
                Node baseTypeNode = findTypeDefinition(null, baseType);
                if (baseTypeNode != null) {
                    Node baseContentModel = findContentModel(baseTypeNode, null);
                    if (baseContentModel != null) {
                        extractMandatoryChildren(baseContentModel, mandatoryChildren);
                    }
                }
            }

            // Process current extension content
            for (Node child : getDirectChildElements(contentNode)) {
                if ("sequence".equals(child.getLocalName()) || "choice".equals(child.getLocalName()) || "all".equals(child.getLocalName())) {
                    extractMandatoryChildren(child, mandatoryChildren);
                }
            }
        }
    }

    /**
     * Recursively extracts all child elements (mandatory and optional) from XSD content model.
     * This is similar to extractMandatoryChildren but includes all elements regardless of minOccurs.
     */
    private void extractAllChildren(Node contentNode, List<MandatoryChildInfo> allChildren) {
        if (contentNode == null) return;

        String localName = contentNode.getLocalName();

        if ("sequence".equals(localName) || "all".equals(localName)) {
            // For sequence and all, process all child elements
            for (Node child : getDirectChildElements(contentNode)) {
                if ("element".equals(child.getLocalName())) {
                    processAnyElement(child, allChildren);  // Process all elements, not just mandatory
                } else if ("sequence".equals(child.getLocalName()) || "choice".equals(child.getLocalName()) || "all".equals(child.getLocalName())) {
                    // Recursively process nested compositors
                    extractAllChildren(child, allChildren);
                }
            }
        } else if ("choice".equals(localName)) {
            // For choice, include all options (not just first mandatory)
            for (Node child : getDirectChildElements(contentNode)) {
                if ("element".equals(child.getLocalName())) {
                    processAnyElement(child, allChildren);
                }
            }
        } else if ("extension".equals(localName)) {
            // Handle extension - process base type and then current content
            String baseType = getAttributeValue(contentNode, "base");
            if (baseType != null) {
                Node baseTypeNode = findTypeDefinition(null, baseType);
                if (baseTypeNode != null) {
                    Node baseContentModel = findContentModel(baseTypeNode, null);
                    if (baseContentModel != null) {
                        extractAllChildren(baseContentModel, allChildren);
                    }
                }
            }

            // Process current extension content
            for (Node child : getDirectChildElements(contentNode)) {
                if ("sequence".equals(child.getLocalName()) || "choice".equals(child.getLocalName()) || "all".equals(child.getLocalName())) {
                    extractAllChildren(child, allChildren);
                }
            }
        }
    }

    /**
     * Processes a single element to determine if it's mandatory and extract its info.
     */
    private void processMandatoryElement(Node elementNode, List<MandatoryChildInfo> mandatoryChildren) {
        if (!isMandatoryElement(elementNode)) {
            return;
        }

        String childName = getAttributeValue(elementNode, "name");
        if (childName == null) {
            // Handle element reference
            childName = getAttributeValue(elementNode, "ref");
            if (childName != null && childName.contains(":")) {
                // Remove namespace prefix for simplicity
                childName = childName.substring(childName.lastIndexOf(":") + 1);
            }
        }

        if (childName != null) {
            // Get minOccurs and maxOccurs
            String minOccurs = getAttributeValue(elementNode, "minOccurs", "1");
            String maxOccurs = getAttributeValue(elementNode, "maxOccurs", "1");

            // Check if this element has mandatory children recursively
            List<MandatoryChildInfo> nestedChildren = new ArrayList<>();
            Node typeDefinition = findTypeDefinition(elementNode, getAttributeValue(elementNode, "type"));
            Node contentModel = findContentModel(elementNode, typeDefinition);

            if (contentModel != null) {
                extractMandatoryChildren(contentModel, nestedChildren);
            }

            MandatoryChildInfo childInfo = new MandatoryChildInfo(
                    childName,
                    Integer.parseInt(minOccurs),
                    maxOccurs.equals("unbounded") ? Integer.MAX_VALUE : Integer.parseInt(maxOccurs),
                    nestedChildren
            );

            mandatoryChildren.add(childInfo);
            logger.debug("Added mandatory child element: {} (minOccurs={}, maxOccurs={}, hasChildren={})",
                    childName, minOccurs, maxOccurs, !nestedChildren.isEmpty());
        }
    }

    /**
     * Processes a single element (mandatory or optional) and extracts its info.
     * This is similar to processMandatoryElement but processes all elements regardless of minOccurs.
     */
    private void processAnyElement(Node elementNode, List<MandatoryChildInfo> allChildren) {
        String childName = getAttributeValue(elementNode, "name");
        if (childName == null) {
            // Handle element reference
            childName = getAttributeValue(elementNode, "ref");
            if (childName != null && childName.contains(":")) {
                // Remove namespace prefix for simplicity
                childName = childName.substring(childName.lastIndexOf(":") + 1);
            }
        }

        if (childName != null) {
            // Get minOccurs and maxOccurs
            String minOccurs = getAttributeValue(elementNode, "minOccurs", "1");
            String maxOccurs = getAttributeValue(elementNode, "maxOccurs", "1");

            // Check if this element has children recursively (use getAllChildElements for consistency)
            List<MandatoryChildInfo> nestedChildren = new ArrayList<>();
            Node typeDefinition = findTypeDefinition(elementNode, getAttributeValue(elementNode, "type"));
            Node contentModel = findContentModel(elementNode, typeDefinition);

            if (contentModel != null) {
                extractAllChildren(contentModel, nestedChildren);
            }

            MandatoryChildInfo childInfo = new MandatoryChildInfo(
                    childName,
                    Integer.parseInt(minOccurs),
                    maxOccurs.equals("unbounded") ? Integer.MAX_VALUE : Integer.parseInt(maxOccurs),
                    nestedChildren
            );

            allChildren.add(childInfo);
            logger.debug("Added child element: {} (minOccurs={}, maxOccurs={}, hasChildren={})",
                    childName, minOccurs, maxOccurs, !nestedChildren.isEmpty());
        }
    }

    /**
     * Checks if an element is mandatory based on its minOccurs attribute.
     */
    private boolean isMandatoryElement(Node elementNode) {
        String minOccurs = getAttributeValue(elementNode, "minOccurs", "1");
        return Integer.parseInt(minOccurs) >= 1;
    }

    /**
     * Helper record to hold information about mandatory child elements.
     */
    public record MandatoryChildInfo(
            String name,
            int minOccurs,
            int maxOccurs,
            List<MandatoryChildInfo> children
    ) {
        public boolean hasChildren() {
            return children != null && !children.isEmpty();
        }
    }

    /**
     * Generates a sample value for an element based on its XSD type and available example values.
     *
     * @param elementName The name of the element
     * @param type        The XSD type of the element (e.g., "xs:string", "xs:int", etc.)
     * @return A sample value or null if no value can be generated
     */
    public String generateSampleValue(String elementName, String type) {
        try {
            // First, try to get example values from XSD annotations
            if (xsdDocumentationData != null && xsdDocumentationData.getExtendedXsdElementMap() != null) {
                // Look for element by name in the extended element map
                for (XsdExtendedElement element : xsdDocumentationData.getExtendedXsdElementMap().values()) {
                    if (elementName.equals(element.getElementName()) || (element.getCurrentXpath() != null && element.getCurrentXpath().endsWith("/" + elementName))) {
                        List<String> exampleValues = element.getExampleValues();
                        if (exampleValues != null && !exampleValues.isEmpty()) {
                            // Return first available example value
                            return exampleValues.get(0);
                        }
                    }
                }
            }

            // Fallback to type-based sample values
            if (type == null || type.isEmpty()) {
                return "sample text";
            }

            // Normalize type name (remove namespace prefix)
            String normalizedType = type;
            if (type.contains(":")) {
                normalizedType = type.substring(type.lastIndexOf(":") + 1);
            }

            return switch (normalizedType.toLowerCase()) {
                case "string", "normalizedstring", "token" -> "Sample text";
                case "int", "integer", "positiveinteger", "nonpositiveinteger",
                     "negativeinteger", "nonnegativeinteger", "long", "short", "byte" -> "123";
                case "decimal", "float", "double" -> "99.99";
                case "boolean" -> "true";
                case "date" -> java.time.LocalDate.now().toString();
                case "datetime", "timestamp" -> java.time.LocalDateTime.now().toString();
                case "time" -> java.time.LocalTime.now().toString();
                case "anyuri", "uri", "url" -> "https://example.com";
                case "email" -> "example@example.com";
                case "base64binary" -> "U2FtcGxlIGRhdGE=";
                case "hexbinary" -> "48656C6C6F";
                case "duration" -> "P1D";
                case "gday" -> "---15";
                case "gmonth" -> "--12";
                case "gmonthday" -> "--12-15";
                case "gyear" -> String.valueOf(java.time.Year.now().getValue());
                case "gyearmonth" -> java.time.YearMonth.now().toString();
                default -> {
                    // Check if it's a custom type that might have restrictions
                    logger.debug("Unknown type '{}' for element '{}', using default sample", type, elementName);
                    yield "sample value";
                }
            };

        } catch (Exception e) {
            logger.debug("Error generating sample value for element '{}' with type '{}': {}",
                    elementName, type, e.getMessage());
            return "sample value";
        }
    }

    /**
     * Processes all schemas including xs:include and xs:import elements.
     * This method downloads remote schemas and processes local includes.
     */
    private void processAllSchemas() throws Exception {
        Path baseDirectory = new File(this.xsdFilePath).toPath().getParent();
        if (baseDirectory == null) {
            baseDirectory = new File(".").toPath(); // Fallback to current directory
        }

        Queue<Path> filesToProcess = new LinkedList<>();
        filesToProcess.add(new File(this.xsdFilePath).toPath());

        Set<String> processedFiles = new HashSet<>();
        Set<String> processedUrls = new HashSet<>();

        while (!filesToProcess.isEmpty()) {
            Path currentFile = filesToProcess.poll();
            if (!Files.exists(currentFile)) continue;

            String currentFilePath = currentFile.toAbsolutePath().toString();
            if (processedFiles.contains(currentFilePath)) continue;
            processedFiles.add(currentFilePath);

            logger.debug("Processing schema file: {}", currentFile);
            Document document = parseXsdContent(Files.readString(currentFile, StandardCharsets.UTF_8));

            // Process both xs:include and xs:import elements
            NodeList includeNodes = (NodeList) xpath.evaluate("//xs:include[@schemaLocation]", document, XPathConstants.NODESET);
            NodeList importNodes = (NodeList) xpath.evaluate("//xs:import[@schemaLocation]", document, XPathConstants.NODESET);
            
            boolean modified = false;

            // Process xs:include elements (local files)
            for (int i = 0; i < includeNodes.getLength(); i++) {
                Element includeElement = (Element) includeNodes.item(i);
                String location = includeElement.getAttribute("schemaLocation");

                if (location != null && !location.isEmpty()) {
                    Path includedFile = baseDirectory.resolve(location);
                    if (Files.exists(includedFile)) {
                        logger.debug("Found local include: {}", includedFile);
                        filesToProcess.add(includedFile);
                    } else {
                        logger.warn("Included file not found: {}", includedFile);
                    }
                }
            }

            // Process xs:import elements (remote files)
            for (int i = 0; i < importNodes.getLength(); i++) {
                Element importElement = (Element) importNodes.item(i);
                String location = importElement.getAttribute("schemaLocation");

                if (isRemote(location)) {
                    String fileName = getFileNameFromUrl(location);
                    if (fileName.isEmpty()) {
                        logger.warn("Could not determine filename from URL, skipping: {}", location);
                        continue;
                    }

                    // Rewrite path even if already processed, to ensure consistency
                    if (!importElement.getAttribute("schemaLocation").equals(fileName)) {
                        importElement.setAttribute("schemaLocation", fileName);
                        modified = true;
                    }

                    if (processedUrls.contains(location)) continue; // Already downloaded

                    try {
                        Path localPath = baseDirectory.resolve(fileName);

                        if (Files.exists(localPath)) {
                            logger.info("Skipping download, file already exists locally: {}", localPath);
                        } else {
                            logger.info("Downloading remote schema from {} to {}", location, localPath);
                            try (InputStream in = new URI(location).toURL().openStream()) {
                                Files.copy(in, localPath);
                            }
                        }

                        // Add the downloaded file to the processing queue
                        filesToProcess.add(localPath);
                        processedUrls.add(location);
                    } catch (Exception e) {
                        logger.error("Failed to download or process remote schema: {}", location, e);
                    }
                } else if (location != null && !location.isEmpty()) {
                    // Handle local imports
                    Path importedFile = baseDirectory.resolve(location);
                    if (Files.exists(importedFile)) {
                        logger.debug("Found local import: {}", importedFile);
                        filesToProcess.add(importedFile);
                    } else {
                        logger.warn("Imported file not found: {}", importedFile);
                    }
                }
            }

            if (modified) {
                logger.info("Rewriting schema file with updated local paths: {}", currentFile);
                try (Writer writer = Files.newBufferedWriter(currentFile, StandardCharsets.UTF_8)) {
                    TransformerFactory factory = TransformerFactory.newInstance();
                    factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
                    Transformer transformer = factory.newTransformer();
                    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", String.valueOf(PropertiesServiceImpl.getInstance().getXmlIndentSpaces()));
                    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
                    transformer.transform(new DOMSource(document), new StreamResult(writer));
                }
            }
        }
    }

    /**
     * Initialize caches for global definitions from all processed schema files.
     */
    private void initializeCachesFromAllSchemas() throws Exception {
        Path baseDirectory = new File(this.xsdFilePath).toPath().getParent();
        if (baseDirectory == null) {
            baseDirectory = new File(".").toPath();
        }

        // Start with the main schema file
        Queue<Path> filesToProcess = new LinkedList<>();
        filesToProcess.add(new File(this.xsdFilePath).toPath());
        Set<String> processedFiles = new HashSet<>();

        while (!filesToProcess.isEmpty()) {
            Path currentFile = filesToProcess.poll();
            if (!Files.exists(currentFile)) continue;

            String currentFilePath = currentFile.toAbsolutePath().toString();
            if (processedFiles.contains(currentFilePath)) continue;
            processedFiles.add(currentFilePath);

            logger.debug("Initializing caches from: {}", currentFile);
            Document document = parseXsdContent(Files.readString(currentFile, StandardCharsets.UTF_8));

            // Initialize caches for this document
            initializeCaches(document);

            // Find and queue included/imported files
            NodeList includeNodes = (NodeList) xpath.evaluate("//xs:include[@schemaLocation]", document, XPathConstants.NODESET);
            NodeList importNodes = (NodeList) xpath.evaluate("//xs:import[@schemaLocation]", document, XPathConstants.NODESET);

            for (int i = 0; i < includeNodes.getLength(); i++) {
                Element includeElement = (Element) includeNodes.item(i);
                String location = includeElement.getAttribute("schemaLocation");
                if (!location.isEmpty()) {
                    Path includedFile = baseDirectory.resolve(location);
                    if (Files.exists(includedFile)) {
                        filesToProcess.add(includedFile);
                    }
                }
            }

            for (int i = 0; i < importNodes.getLength(); i++) {
                Element importElement = (Element) importNodes.item(i);
                String location = importElement.getAttribute("schemaLocation");
                if (!location.isEmpty() && !isRemote(location)) {
                    Path importedFile = baseDirectory.resolve(location);
                    if (Files.exists(importedFile)) {
                        filesToProcess.add(importedFile);
                    }
                }
            }
        }
    }

    private void traverseNode(Node node, String currentXPath, String parentXPath, int level, Set<Node> visitedOnPath) {
        if (node == null || node.getNodeType() != Node.ELEMENT_NODE || level > MAX_ALLOWED_DEPTH) {
            return;
        }
        if (visitedOnPath.contains(node)) {
            logger.warn("Recursion detected at node '{}' on path '{}'. Aborting this branch.", getAttributeValue(node, "name"), currentXPath);
            return;
        }
        visitedOnPath.add(node);

        try {
            // Handle references (ref="...")
            String ref = getAttributeValue(node, "ref");
            if (ref != null && !ref.isEmpty()) {
                Node referencedNode = findReferencedNode(node.getLocalName(), ref);
                if (referencedNode != null) {
                    traverseNode(referencedNode, currentXPath, parentXPath, level, visitedOnPath);
                } else {
                    logger.warn("Reference '{}' for node '{}' could not be resolved.", ref, node.getLocalName());
                }
                return; // Stop processing the current ref-node
            }

            String localName = node.getLocalName();
            if ("element".equals(localName) || "attribute".equals(localName)) {
                processElementOrAttribute(node, currentXPath, parentXPath, level, visitedOnPath);
            } else if ("sequence".equals(localName) || "choice".equals(localName) || "all".equals(localName)) {
                // Create explicit sequence/choice/all nodes for SVG visualization
                processElementOrAttribute(node, currentXPath, parentXPath, level, visitedOnPath);
            } else if ("group".equals(localName)) {
                // For groups, just traverse their children (groups are references)
                for (Node child : getDirectChildElements(node)) {
                    String childName = getAttributeValue(child, "name", getAttributeValue(child, "ref"));
                    String childXPath = "element".equals(child.getLocalName()) ? currentXPath + "/" + childName : currentXPath;
                    traverseNode(child, childXPath, currentXPath, level, visitedOnPath);
                }
            }
        } finally {
            visitedOnPath.remove(node);
        }
    }

    private void processElementOrAttribute(Node node, String currentXPath, String parentXPath, int level, Set<Node> visitedOnPath) {
        XsdExtendedElement extendedElem = new XsdExtendedElement();
        extendedElem.setUseMarkdownRenderer(this.useMarkdownRenderer);
        extendedElem.setCurrentNode(node);
        extendedElem.setCounter(counter++);
        extendedElem.setLevel(level);
        extendedElem.setCurrentXpath(currentXPath);
        extendedElem.setParentXpath(parentXPath);

        String localName = node.getLocalName();
        boolean isAttribute = "attribute".equals(localName);
        boolean isContainer = "sequence".equals(localName) || "choice".equals(localName) || "all".equals(localName);

        String name;
        if (isContainer) {
            name = localName.toUpperCase();
        } else {
            name = getAttributeValue(node, "name");
        }
        extendedElem.setElementName(isAttribute ? "@" + name : name);

        // NEW: Read the origin of the namespace from the attribute set by the Flattener.
        String sourceNamespace = getAttributeValue(node, "fxt:sourceNamespace");
        if (sourceNamespace != null && !sourceNamespace.isBlank()) {
            extendedElem.setSourceNamespace(sourceNamespace);
            // Find the corresponding prefix for display.
            xsdDocumentationData.getNamespaces().entrySet().stream()
                    .filter(entry -> sourceNamespace.equals(entry.getValue()))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .ifPresent(extendedElem::setSourceNamespacePrefix);
        }

        // Add to parent's children list (only if not already added)
        if (parentXPath != null && xsdDocumentationData.getExtendedXsdElementMap().containsKey(parentXPath)) {
            List<String> parentChildren = xsdDocumentationData.getExtendedXsdElementMap().get(parentXPath).getChildren();
            if (!parentChildren.contains(currentXPath)) {
                parentChildren.add(currentXPath);
            }
        }
        xsdDocumentationData.getExtendedXsdElementMap().put(currentXPath, extendedElem);

        // --- Type, Documentation, and Content Processing ---
        String typeName = getAttributeValue(node, "type");
        Node typeDefinitionNode = findTypeDefinition(node, typeName);

        if (isContainer) {
            extendedElem.setElementType("(container)");
        } else {
            if (typeName != null) {
                // Named type reference
                extendedElem.setElementType(typeName);
            } else if (typeDefinitionNode != null) {
                // Inline type definition - check if it's a simple type with restriction
                Node restrictionNode = findRestriction(node, typeDefinitionNode);
                if (restrictionNode != null) {
                    String baseType = getAttributeValue(restrictionNode, "base");
                    if (baseType != null && !baseType.isEmpty()) {
                        extendedElem.setElementType(baseType);
                    } else {
                        extendedElem.setElementType("(anonymous)");
                    }
                } else {
                    extendedElem.setElementType("(anonymous)");
                }
            }
        }

        // Process annotations from the element itself and its type definition
        processAnnotations(getDirectChildElement(node, "annotation"), extendedElem);
        if (typeDefinitionNode != null) {
            processAnnotations(getDirectChildElement(typeDefinitionNode, "annotation"), extendedElem);
        }

        // Process list and union types (for simpleType definitions)
        if (typeDefinitionNode != null && "simpleType".equals(typeDefinitionNode.getLocalName())) {
            processListAndUnionTypes(typeDefinitionNode, extendedElem);
        }

        // Process content (children for elements, restrictions for attributes/simple types)
        if (!isAttribute) {
            if (isContainer) {
                // For sequence/choice/all containers, process direct children
                for (Node child : getDirectChildElements(node)) {
                    String childName = getAttributeValue(child, "name", getAttributeValue(child, "ref"));
                    String childLocalName = child.getLocalName();
                    String childXPath;

                    if ("element".equals(childLocalName)) {
                        childXPath = currentXPath + "/" + childName;
                        traverseNode(child, childXPath, currentXPath, level + 1, visitedOnPath);
                    } else if ("sequence".equals(childLocalName) || "choice".equals(childLocalName) || "all".equals(childLocalName)) {
                        // Nested containers
                        String containerName = childLocalName.toUpperCase();
                        childXPath = currentXPath + "/" + containerName + "_" + counter;
                        traverseNode(child, childXPath, currentXPath, level + 1, visitedOnPath);
                    }
                }
            } else {
                Node contentModel = findContentModel(node, typeDefinitionNode);
                if (contentModel != null) {
                    // Handle complex content (sequence, choice, extension, etc.)
                    processComplexContent(contentModel, currentXPath, level, visitedOnPath);
                }

                // Additionally process attributes that are defined directly on the complexType
                // (i.e., siblings of <sequence>/<choice>), which are not inside the content model.
                Node attributeContext = (typeDefinitionNode != null) ? typeDefinitionNode : node;
                processAttributes(attributeContext, currentXPath, level, visitedOnPath);
            }
        }

        // Process restrictions
        Node restrictionNode = findRestriction(node, typeDefinitionNode);
        if (restrictionNode != null) {
            extendedElem.setRestrictionInfo(parseRestriction(restrictionNode));
            if (extendedElem.getElementType() == null || extendedElem.getElementType().isEmpty()) {
                extendedElem.setElementType(getAttributeValue(restrictionNode, "base"));
            }
            // Process assertions on simpleType restrictions (xs:assertion)
            processAssertions(restrictionNode, extendedElem);
        }

        // Process XSD 1.0 identity constraints (key, keyref, unique) on elements
        if (!isAttribute && !isContainer) {
            processIdentityConstraints(node, extendedElem);
        }

        // Process XSD 1.1 type alternatives on elements
        if (!isAttribute && !isContainer) {
            processTypeAlternatives(node, extendedElem);
        }

        // Process XSD 1.1 assertions on complexType (xs:assert)
        if (typeDefinitionNode != null && "complexType".equals(typeDefinitionNode.getLocalName())) {
            processAssertions(typeDefinitionNode, extendedElem);
        }

        // Process XSD 1.1 open content on complexType (xs:openContent)
        if (typeDefinitionNode != null && "complexType".equals(typeDefinitionNode.getLocalName())) {
            processOpenContent(typeDefinitionNode, extendedElem);
        }

        // Process wildcards (xs:any, xs:anyAttribute)
        // Check in the element node itself
        processWildcards(node, extendedElem);
        // Check in the type definition (complexType)
        if (typeDefinitionNode != null) {
            processWildcards(typeDefinitionNode, extendedElem);
        }
        // Check in content model
        Node contentModel = findContentModel(node, typeDefinitionNode);
        if (contentModel != null) {
            processWildcards(contentModel, extendedElem);
        }

        // Prefer fixed/default values where present (especially for attributes)
        String fixedValue = getAttributeValue(node, "fixed");
        String defaultValue = getAttributeValue(node, "default");

        // Final steps - generate separate source code snippets
        setSourceCodeSnippets(extendedElem, node, typeName, typeDefinitionNode);

        if (fixedValue != null) {
            extendedElem.setSampleData(fixedValue);
        } else if (defaultValue != null) {
            extendedElem.setSampleData(defaultValue);
        } else {
            extendedElem.setSampleData(xsdSampleDataGenerator.generate(extendedElem));
        }
    }

    /**
     * Processes attributes that are declared directly under a complexType (outside of sequence/choice/all).
     */
    private void processAttributes(Node contextNode, String parentXPath, int level, Set<Node> visitedOnPath) {
        if (contextNode == null) return;

        for (Node child : getDirectChildElements(contextNode)) {
            String localName = child.getLocalName();
            if (!"attribute".equals(localName)) continue;

            String childName = getAttributeValue(child, "name", getAttributeValue(child, "ref"));
            if (childName == null || childName.isBlank()) continue;

            String childXPath = parentXPath + "/@" + childName;

            // Ensure attributes are added as children to the parent element (deduped)
            if (xsdDocumentationData.getExtendedXsdElementMap().containsKey(parentXPath)) {
                List<String> parentChildren = xsdDocumentationData.getExtendedXsdElementMap().get(parentXPath).getChildren();
                if (!parentChildren.contains(childXPath)) {
                    parentChildren.add(childXPath);
                }
            }

            traverseNode(child, childXPath, parentXPath, level + 1, visitedOnPath);
        }
    }

    private void processComplexContent(Node contentNode, String parentXPath, int level, Set<Node> visitedOnPath) {
        String localName = contentNode.getLocalName();

        // Handle extension
        if ("extension".equals(localName)) {
            String baseType = getAttributeValue(contentNode, "base");
            Node baseTypeNode = findTypeDefinition(null, baseType);
            if (baseTypeNode != null) {
                // First, process the base type's content
                Node baseContentModel = findContentModel(baseTypeNode, null);
                if (baseContentModel != null) {
                    processComplexContent(baseContentModel, parentXPath, level, visitedOnPath);
                }
            }
        }

        // Process all direct children (sequence, choice, element, attribute, etc.)
        for (Node child : getDirectChildElements(contentNode)) {
            String childName = getAttributeValue(child, "name", getAttributeValue(child, "ref"));
            String childLocalName = child.getLocalName();
            String childXPath;

            if ("attribute".equals(childLocalName)) {
                childXPath = parentXPath + "/@" + childName;
                // Ensure attributes are added as children of the parent element (only if not already added)
                if (xsdDocumentationData.getExtendedXsdElementMap().containsKey(parentXPath)) {
                    List<String> parentChildren = xsdDocumentationData.getExtendedXsdElementMap().get(parentXPath).getChildren();
                    if (!parentChildren.contains(childXPath)) {
                        parentChildren.add(childXPath);
                    }
                }
            } else if ("element".equals(childLocalName)) {
                childXPath = parentXPath + "/" + childName;
            } else if ("sequence".equals(childLocalName) || "choice".equals(childLocalName) || "all".equals(childLocalName)) {
                // Create explicit sequence/choice/all nodes for SVG visualization
                String containerName = childLocalName.toUpperCase();
                childXPath = parentXPath + "/" + containerName + "_" + counter;
                traverseNode(child, childXPath, parentXPath, level + 1, visitedOnPath);
                continue; // Skip normal traverseNode call for containers
            } else {
                childXPath = parentXPath; // For other containers
            }
            traverseNode(child, childXPath, parentXPath, level + 1, visitedOnPath);
        }
    }

    public String generateSampleXml(boolean mandatoryOnly, int maxOccurrences) {
        if (xsdDocumentationData.getExtendedXsdElementMap().isEmpty()) {
            try {
                processXsd(false);
            } catch (Exception e) {
                logger.error("Failed to process XSD for sample XML generation.", e);
                return "<!-- Error processing XSD: " + e.getMessage() + " -->";
            }
        }

        List<XsdExtendedElement> rootElements = xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                .filter(e -> e.getParentXpath() == null || e.getParentXpath().equals("/"))
                .sorted(Comparator.comparing(XsdExtendedElement::getCounter))
                .toList();

        if (rootElements.isEmpty()) {
            return "<!-- No root element found in XSD -->";
        }

        StringBuilder xmlBuilder = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        
        // Add schema reference (supports namespaced and no-namespace schemas)
        String targetNamespace = xsdDocumentationData.getTargetNamespace();
        String schemaLocationUri = new File(xsdFilePath).toURI().toString();

        String rootName = rootElements.getFirst().getElementName();
        xmlBuilder.append("<").append(rootName)
                 .append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");

        if (targetNamespace != null && !targetNamespace.isBlank()) {
            // Default namespace and schemaLocation pair (namespace + absolute file URI)
            xmlBuilder.append(" xmlns=\"").append(targetNamespace).append("\"")
                     .append(" xsi:schemaLocation=\"")
                     .append(targetNamespace).append(" ")
                     .append(schemaLocationUri)
                     .append("\"");
        } else {
            // No namespace schema
            xmlBuilder.append(" xsi:noNamespaceSchemaLocation=\"")
                     .append(schemaLocationUri)
                     .append("\"");
        }

        xmlBuilder.append(">\n");

        // Build the content without the root element tags
        buildXmlElementContent(xmlBuilder, rootElements.getFirst(), mandatoryOnly, maxOccurrences, 1);

        xmlBuilder.append("</").append(rootName).append(">\n");
        
        return xmlBuilder.toString();
    }

    /**
     * Validates the generated XML against the XSD schema.
     * @param xmlContent The XML content to validate
     * @return Validation result with success status and any error messages
     */
    public ValidationResult validateXmlAgainstSchema(String xmlContent) {
        try {
            // Create schema factory
            SchemaFactory factory = SchemaFactory.newInstance(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);
            
            // Load the schema
            Source schemaSource = new StreamSource(new File(xsdFilePath));
            Schema schema = factory.newSchema(schemaSource);
            
            // Create validator
            Validator validator = schema.newValidator();
            
            // Create error handler to collect validation errors
            StringBuilder errorMessages = new StringBuilder();
            validator.setErrorHandler(new org.xml.sax.ErrorHandler() {
                @Override
                public void warning(org.xml.sax.SAXParseException exception) {
                    errorMessages.append("Warning: ").append(exception.getMessage()).append("\n");
                }
                
                @Override
                public void error(org.xml.sax.SAXParseException exception) {
                    errorMessages.append("Error: ").append(exception.getMessage()).append("\n");
                }
                
                @Override
                public void fatalError(org.xml.sax.SAXParseException exception) {
                    errorMessages.append("Fatal Error: ").append(exception.getMessage()).append("\n");
                }
            });
            
            // Validate the XML
            Source xmlSource = new StreamSource(new StringReader(xmlContent));
            validator.validate(xmlSource);
            
            return new ValidationResult(true, errorMessages.toString());
            
        } catch (Exception e) {
            return new ValidationResult(false, "Validation failed: " + e.getMessage());
        }
    }

    /**
         * Result of XML validation against XSD schema.
         */
        public record ValidationResult(boolean isValid, String message) {
    }

    private void buildXmlElement(StringBuilder sb, XsdExtendedElement element, boolean mandatoryOnly, int maxOccurrences, int indentLevel) {
        if (element == null || (mandatoryOnly && !element.isMandatory())) {
            return;
        }
        if (element.getElementName().startsWith("@")) {
            return; // Attributes are handled by their parent
        }

        String maxOccurs = getAttributeValue(element.getCurrentNode(), "maxOccurs", "1");
        int repeatCount = 1;
        if (!"1".equals(maxOccurs)) {
            if ("unbounded".equalsIgnoreCase(maxOccurs)) {
                repeatCount = maxOccurrences;
            } else {
                try {
                    repeatCount = Math.min(Integer.parseInt(maxOccurs), maxOccurrences);
                } catch (NumberFormatException e) {
                    repeatCount = 1;
                }
            }
        }

        for (int i = 0; i < repeatCount; i++) {
            String indent = "\t".repeat(indentLevel);
            sb.append(indent).append("<").append(element.getElementName());

            // Find all attributes for this element by searching for elements with @ prefix in the children
            List<XsdExtendedElement> attributes = element.getChildren().stream()
                    .map(xsdDocumentationData.getExtendedXsdElementMap()::get)
                    .filter(Objects::nonNull)
                    .filter(e -> e.getElementName().startsWith("@"))
                    .toList();

            List<XsdExtendedElement> childElements = element.getChildren().stream()
                    .map(xsdDocumentationData.getExtendedXsdElementMap()::get)
                    .filter(Objects::nonNull)
                    .filter(e -> !e.getElementName().startsWith("@"))
                    .toList();

            // Render attributes
            for (XsdExtendedElement attr : attributes) {
                // Include attribute if mandatory, or if it has a fixed/default value even when mandatoryOnly is true
                String fixedOrDefault = getAttributeValue(attr.getCurrentNode(), "fixed", getAttributeValue(attr.getCurrentNode(), "default", null));
                if (mandatoryOnly && !attr.isMandatory() && fixedOrDefault == null) continue;
                String attrName = attr.getElementName().substring(1);
                String attrValue = (fixedOrDefault != null)
                        ? fixedOrDefault
                        : (attr.getDisplaySampleData() != null ? attr.getDisplaySampleData() : "");
                sb.append(" ").append(attrName).append("=\"").append(escapeXml(attrValue)).append("\"");
            }

            String sampleData = element.getDisplaySampleData() != null ? element.getDisplaySampleData() : "";
            if (childElements.isEmpty() && sampleData.isEmpty()) {
                sb.append("/>\n"); // Self-closing tag
            } else {
                sb.append(">");
                sb.append(escapeXml(sampleData));

                if (!childElements.isEmpty()) {
                    sb.append("\n");
                    for (XsdExtendedElement childElement : childElements) {
                        buildXmlElement(sb, childElement, mandatoryOnly, maxOccurrences, indentLevel + 1);
                    }
                    sb.append(indent);
                }
                sb.append("</").append(element.getElementName()).append(">\n");
            }
        }
    }

    /**
     * Builds XML element content without the root element tags (for schema-referenced XML).
     */
    private void buildXmlElementContent(StringBuilder sb, XsdExtendedElement element, boolean mandatoryOnly, int maxOccurrences, int indentLevel) {
        if (element == null || (mandatoryOnly && !element.isMandatory())) {
            return;
        }
        if (element.getElementName().startsWith("@")) {
            return; // Attributes are handled by their parent
        }

        String maxOccurs = getAttributeValue(element.getCurrentNode(), "maxOccurs", "1");
        int repeatCount = 1;
        if (!"1".equals(maxOccurs)) {
            if ("unbounded".equalsIgnoreCase(maxOccurs)) {
                repeatCount = maxOccurrences;
            } else {
                try {
                    repeatCount = Math.min(Integer.parseInt(maxOccurs), maxOccurrences);
                } catch (NumberFormatException e) {
                    repeatCount = 1;
                }
            }
        }

        for (int i = 0; i < repeatCount; i++) {
            String indent = "\t".repeat(indentLevel);
            sb.append(indent).append("<").append(element.getElementName());

            // Find all attributes for this element by searching for elements with @ prefix in the children
            List<XsdExtendedElement> attributes = element.getChildren().stream()
                    .map(xsdDocumentationData.getExtendedXsdElementMap()::get)
                    .filter(Objects::nonNull)
                    .filter(e -> e.getElementName().startsWith("@"))
                    .toList();

            List<XsdExtendedElement> childElements = element.getChildren().stream()
                    .map(xsdDocumentationData.getExtendedXsdElementMap()::get)
                    .filter(Objects::nonNull)
                    .filter(e -> !e.getElementName().startsWith("@"))
                    .toList();

            // Render attributes
            for (XsdExtendedElement attr : attributes) {
                // Include attribute if mandatory, or if it has a fixed/default value even when mandatoryOnly is true
                String fixedOrDefault = getAttributeValue(attr.getCurrentNode(), "fixed", getAttributeValue(attr.getCurrentNode(), "default", null));
                if (mandatoryOnly && !attr.isMandatory() && fixedOrDefault == null) continue;
                String attrName = attr.getElementName().substring(1);
                String attrValue = (fixedOrDefault != null)
                        ? fixedOrDefault
                        : (attr.getDisplaySampleData() != null ? attr.getDisplaySampleData() : "");
                sb.append(" ").append(attrName).append("=\"").append(escapeXml(attrValue)).append("\"");
            }

            String sampleData = element.getDisplaySampleData() != null ? element.getDisplaySampleData() : "";
            if (childElements.isEmpty() && sampleData.isEmpty()) {
                sb.append("/>\n"); // Self-closing tag
            } else {
                sb.append(">");
                sb.append(escapeXml(sampleData));

                if (!childElements.isEmpty()) {
                    sb.append("\n");
                    // Process children, handling CHOICE elements with random selection
                    processChildElementsForGeneration(sb, childElements, mandatoryOnly, maxOccurrences, indentLevel + 1);
                    sb.append(indent);
                }
                sb.append("</").append(element.getElementName()).append(">\n");
            }
        }
    }

    /**
     * Processes child elements for XML generation, handling CHOICE elements with random selection.
     * When a child element is a CHOICE container, this method randomly selects one or more elements
     * from the choice based on the choice's cardinality (minOccurs/maxOccurs).
     *
     * @param sb             StringBuilder to append the generated XML
     * @param childElements  List of child elements to process
     * @param mandatoryOnly  Whether to only generate mandatory elements
     * @param maxOccurrences Maximum number of occurrences for repeating elements
     * @param indentLevel    Current indentation level
     */
    private void processChildElementsForGeneration(StringBuilder sb, List<XsdExtendedElement> childElements,
                                                   boolean mandatoryOnly, int maxOccurrences, int indentLevel) {
        for (XsdExtendedElement childElement : childElements) {
            // Check if this child is a CHOICE container
            if (childElement.getElementName() != null && childElement.getElementName().startsWith("CHOICE")) {
                // Get the choice's cardinality
                Node choiceNode = childElement.getCurrentNode();
                String minOccursStr = getAttributeValue(choiceNode, "minOccurs", "1");
                String maxOccursStr = getAttributeValue(choiceNode, "maxOccurs", "1");

                int minOccurs;
                int maxOccurs;
                try {
                    minOccurs = Integer.parseInt(minOccursStr);
                } catch (NumberFormatException e) {
                    minOccurs = 1;
                }

                if ("unbounded".equalsIgnoreCase(maxOccursStr)) {
                    maxOccurs = maxOccurrences;
                } else {
                    try {
                        maxOccurs = Math.min(Integer.parseInt(maxOccursStr), maxOccurrences);
                    } catch (NumberFormatException e) {
                        maxOccurs = 1;
                    }
                }

                // Skip optional choices if generating only mandatory elements
                if (mandatoryOnly && minOccurs == 0) {
                    continue;
                }

                // Get the options from the choice (the children of the CHOICE element)
                List<XsdExtendedElement> choiceOptions = childElement.getChildren().stream()
                        .map(xsdDocumentationData.getExtendedXsdElementMap()::get)
                        .filter(Objects::nonNull)
                        .filter(e -> !e.getElementName().startsWith("@"))
                        .toList();

                if (choiceOptions.isEmpty()) {
                    logger.debug("CHOICE element has no valid options, skipping");
                    continue;
                }

                // Determine how many elements to select from the choice
                // Use minOccurs as the count, but cap it at the available options
                int selectCount = Math.max(1, Math.min(minOccurs, choiceOptions.size()));

                // If not mandatory-only mode, we might select more based on maxOccurs
                if (!mandatoryOnly && maxOccurs > selectCount) {
                    // Randomly decide how many to select (between minOccurs and maxOccurs)
                    selectCount = minOccurs + random.nextInt(Math.min(maxOccurs - minOccurs + 1, choiceOptions.size() - selectCount + 1));
                }

                // Randomly select elements from the choice
                List<XsdExtendedElement> selectedOptions = new ArrayList<>(choiceOptions);
                Collections.shuffle(selectedOptions, new Random(random.nextLong()));

                // Generate XML for the selected options
                for (int i = 0; i < selectCount && i < selectedOptions.size(); i++) {
                    XsdExtendedElement selected = selectedOptions.get(i);
                    logger.debug("Selected element '{}' from CHOICE (option {} of {})",
                            selected.getElementName(), i + 1, selectCount);
                    buildXmlElementContent(sb, selected, mandatoryOnly, maxOccurrences, indentLevel);
                }
            } else {
                // Not a CHOICE element, process normally
                buildXmlElementContent(sb, childElement, mandatoryOnly, maxOccurrences, indentLevel);
            }
        }
    }

    // --- XML/DOM Processing Helper Methods ---
    private void initializeXmlTools() throws Exception {
        XPathFactory xPathFactory = XPathFactory.newInstance();
        xpath = xPathFactory.newXPath();
        xpath.setNamespaceContext(new NamespaceContext() {
            public String getNamespaceURI(String prefix) {
                return NS_PREFIX.equals(prefix) ? NS_URI : null;
            }

            public String getPrefix(String uri) {
                return null;
            }

            public Iterator<String> getPrefixes(String uri) {
                return null;
            }
        });
    }

    private Document parseXsdContent(String xsdContent) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xsdContent)));
    }

    private void initializeCaches(Document doc) throws Exception {
        // Initialize maps if they don't exist yet
        if (elementMap == null) elementMap = new HashMap<>();
        if (complexTypeMap == null) complexTypeMap = new HashMap<>();
        if (simpleTypeMap == null) simpleTypeMap = new HashMap<>();
        if (groupMap == null) groupMap = new HashMap<>();
        if (attributeGroupMap == null) attributeGroupMap = new HashMap<>();

        // Accumulate definitions from this document
        elementMap.putAll(findAndCacheGlobalDefs(doc, "element"));
        complexTypeMap.putAll(findAndCacheGlobalDefs(doc, "complexType"));
        simpleTypeMap.putAll(findAndCacheGlobalDefs(doc, "simpleType"));
        groupMap.putAll(findAndCacheGlobalDefs(doc, "group"));
        attributeGroupMap.putAll(findAndCacheGlobalDefs(doc, "attributeGroup"));
    }

    private void populateDocumentationData() throws Exception {
        // Use the main schema file for basic metadata
        Element schemaElement = doc.getDocumentElement();
        xsdDocumentationData.setTargetNamespace(getAttributeValue(schemaElement, "targetNamespace"));
        xsdDocumentationData.setVersion(getAttributeValue(schemaElement, "version"));

        // NEW: Read the schema attributes and store them in the data object.
        // The default value "unqualified" is used if the attribute is not present.
        xsdDocumentationData.setAttributeFormDefault(getAttributeValue(schemaElement, "attributeFormDefault", "unqualified"));
        xsdDocumentationData.setElementFormDefault(getAttributeValue(schemaElement, "elementFormDefault", "unqualified"));

        // XSD 1.1: Process xs:defaultOpenContent (applies to all types in schema)
        Node defaultOpenContentNode = getDirectChildElement(schemaElement, "defaultOpenContent");
        if (defaultOpenContentNode != null) {
            OpenContent defaultOpenContent = parseOpenContentNode(defaultOpenContentNode, true);
            xsdDocumentationData.setDefaultOpenContent(defaultOpenContent);
        }

        // Get global elements from the main schema
        xsdDocumentationData.setGlobalElements(nodeListToList((NodeList) xpath.evaluate("/xs:schema/xs:element[@name]", doc, XPathConstants.NODESET)));

        // Create the lists of global types and sort them alphabetically by name.
        List<Node> complexTypes = new ArrayList<>(complexTypeMap.values());
        complexTypes.sort(Comparator.comparing(node -> getAttributeValue(node, "name")));
        xsdDocumentationData.setGlobalComplexTypes(complexTypes);

        List<Node> simpleTypes = new ArrayList<>(simpleTypeMap.values());
        simpleTypes.sort(Comparator.comparing(node -> getAttributeValue(node, "name")));
        xsdDocumentationData.setGlobalSimpleTypes(simpleTypes);

        // Extract namespaces from all processed schemas
        Map<String, String> nsMap = new HashMap<>();

        // Start with the main schema
        var attributes = schemaElement.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            if (attr.getNodeName().startsWith("xmlns:")) {
                nsMap.put(attr.getLocalName(), attr.getNodeValue());
            }
        }

        // Add namespaces from included schemas
        Path baseDirectory = new File(this.xsdFilePath).toPath().getParent();
        if (baseDirectory != null) {
            Queue<Path> filesToProcess = new LinkedList<>();
            filesToProcess.add(new File(this.xsdFilePath).toPath());
            Set<String> processedFiles = new HashSet<>();

            while (!filesToProcess.isEmpty()) {
                Path currentFile = filesToProcess.poll();
                if (!Files.exists(currentFile)) continue;

                String currentFilePath = currentFile.toAbsolutePath().toString();
                if (processedFiles.contains(currentFilePath)) continue;
                processedFiles.add(currentFilePath);

                Document document = parseXsdContent(Files.readString(currentFile, StandardCharsets.UTF_8));
                Element currentSchemaElement = document.getDocumentElement();

                // Add namespaces from this schema
                var currentAttributes = currentSchemaElement.getAttributes();
                for (int i = 0; i < currentAttributes.getLength(); i++) {
                    Node attr = currentAttributes.item(i);
                    if (attr.getNodeName().startsWith("xmlns:")) {
                        String prefix = attr.getLocalName();
                        String uri = attr.getNodeValue();
                        if (!nsMap.containsKey(prefix)) {
                            nsMap.put(prefix, uri);
                        }
                    }
                }

                // Find and queue included/imported files
                NodeList includeNodes = (NodeList) xpath.evaluate("//xs:include[@schemaLocation]", document, XPathConstants.NODESET);
                NodeList importNodes = (NodeList) xpath.evaluate("//xs:import[@schemaLocation]", document, XPathConstants.NODESET);

                for (int i = 0; i < includeNodes.getLength(); i++) {
                    Element includeElement = (Element) includeNodes.item(i);
                    String location = includeElement.getAttribute("schemaLocation");
                    if (location != null && !location.isEmpty()) {
                        Path includedFile = baseDirectory.resolve(location);
                        if (Files.exists(includedFile)) {
                            filesToProcess.add(includedFile);
                        }
                    }
                }

                for (int i = 0; i < importNodes.getLength(); i++) {
                    Element importElement = (Element) importNodes.item(i);
                    String location = importElement.getAttribute("schemaLocation");
                    if (location != null && !location.isEmpty() && !isRemote(location)) {
                        Path importedFile = baseDirectory.resolve(location);
                        if (Files.exists(importedFile)) {
                            filesToProcess.add(importedFile);
                        }
                    }
                }
            }
        }
        
        xsdDocumentationData.setNamespaces(nsMap);
    }

    private void buildTypeUsageMap() {
        logger.debug("Building type usage index for faster lookups...");
        Map<String, List<XsdExtendedElement>> typeUsageMap = xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                .filter(element -> element.getElementType() != null && !element.getElementType().isEmpty())
                .collect(Collectors.groupingBy(XsdExtendedElement::getElementType));

        typeUsageMap.values().parallelStream()
                .forEach(list -> list.sort(Comparator.comparing(XsdExtendedElement::getCurrentXpath)));

        xsdDocumentationData.setTypeUsageMap(typeUsageMap);
        logger.debug("Type usage index built with {} types.", typeUsageMap.size());
    }

    private void processAnnotations(Node annotationNode, XsdExtendedElement extendedElem) {
        if (annotationNode == null) {
            return;
        }

        // 1. Extract documentation
        for (Node docNode : getDirectChildElements(annotationNode, "documentation")) {
            String lang = getAttributeValue(docNode, "xml:lang", "default");
            extendedElem.getDocumentations().add(new DocumentationInfo(lang, docNode.getTextContent()));
        }

        // 2. Process AppInfo tags (for Javadoc and Altova examples)
        XsdDocInfo xsdDocInfo = extendedElem.getXsdDocInfo() != null ? extendedElem.getXsdDocInfo() : new XsdDocInfo();
        List<String> genericAppInfos = extendedElem.getGenericAppInfos() != null ? extendedElem.getGenericAppInfos() : new ArrayList<>();
        List<String> exampleValues = extendedElem.getExampleValues(); // Get list for example values

        for (Node appInfoNode : getDirectChildElements(annotationNode, "appinfo")) {
            // Process Javadoc-style tags
            String source = getAttributeValue(appInfoNode, "source");
            if (source != null && !source.isBlank()) {
                if (source.startsWith("@since")) xsdDocInfo.setSince(source.substring("@since".length()).trim());
                else if (source.startsWith("@see")) xsdDocInfo.getSee().add(source.substring("@see".length()).trim());
                else if (source.startsWith("@deprecated"))
                    xsdDocInfo.setDeprecated(source.substring("@deprecated".length()).trim());
                else genericAppInfos.add(source);
            } else {
                // Extract Altova example values
                boolean isAltovaExample = false;
                for (Node appInfoChild : getDirectChildElements(appInfoNode)) {
                    // Check if the node belongs to Altova and is <exampleValues>
                    if (ALTOVA_NS_URI.equals(appInfoChild.getNamespaceURI()) && "exampleValues".equals(appInfoChild.getLocalName())) {
                        isAltovaExample = true;
                        // Iterate through all <example> children
                        for (Node exampleNode : getDirectChildElements(appInfoChild)) {
                            if (ALTOVA_NS_URI.equals(exampleNode.getNamespaceURI()) && "example".equals(exampleNode.getLocalName())) {
                                String value = getAttributeValue(exampleNode, "value");
                                if (value != null) {
                                    exampleValues.add(value);
                                }
                            }
                        }
                    }
                }
                // If it wasn't an Altova example, treat as generic info
                if (!isAltovaExample) {
                    genericAppInfos.add(appInfoNode.getTextContent());
                }
            }
        }

        // 3. Store collected information in the element
        if (xsdDocInfo.hasData()) {
            extendedElem.setXsdDocInfo(xsdDocInfo);
        }
        if (!genericAppInfos.isEmpty()) {
            extendedElem.setGenericAppInfos(genericAppInfos);
        }
        // The 'exampleValues' list was modified directly, setExampleValues is not needed.
    }

    /**
     * Processes XSD 1.0 identity constraints (xs:key, xs:keyref, xs:unique) on an element.
     * Identity constraints define uniqueness and referential integrity rules within XML documents.
     *
     * @param elementNode  The element node that may contain identity constraints
     * @param extendedElem The extended element to populate with identity constraint information
     */
    private void processIdentityConstraints(Node elementNode, XsdExtendedElement extendedElem) {
        if (elementNode == null) {
            return;
        }

        List<IdentityConstraint> identityConstraints = new ArrayList<>();

        logger.debug("Processing identity constraints for element: {}, node type: {}",
                extendedElem.getElementName(), elementNode.getLocalName());

        for (Node child : getDirectChildElements(elementNode)) {
            String localName = child.getLocalName();
            logger.debug("Checking child node: {}", localName);
            IdentityConstraint.Type type = null;

            // Determine the type of identity constraint
            if ("key".equals(localName)) {
                type = IdentityConstraint.Type.KEY;
            } else if ("keyref".equals(localName)) {
                type = IdentityConstraint.Type.KEYREF;
            } else if ("unique".equals(localName)) {
                type = IdentityConstraint.Type.UNIQUE;
            }

            if (type != null) {
                IdentityConstraint constraint = new IdentityConstraint();
                constraint.setType(type);
                constraint.setName(getAttributeValue(child, "name"));

                // For keyref, extract the referenced key/unique name
                if (type == IdentityConstraint.Type.KEYREF) {
                    constraint.setRefer(getAttributeValue(child, "refer"));
                }

                // Extract selector XPath
                Node selectorNode = getDirectChildElement(child, "selector");
                if (selectorNode != null) {
                    constraint.setSelector(getAttributeValue(selectorNode, "xpath"));
                }

                // Extract field XPath(s)
                List<String> fields = new ArrayList<>();
                for (Node fieldNode : getDirectChildElements(child, "field")) {
                    String xpath = getAttributeValue(fieldNode, "xpath");
                    if (xpath != null) {
                        fields.add(xpath);
                    }
                }
                constraint.setFields(fields);

                // Extract documentation from annotation
                Node annotationNode = getDirectChildElement(child, "annotation");
                if (annotationNode != null) {
                    Node docNode = getDirectChildElement(annotationNode, "documentation");
                    if (docNode != null) {
                        constraint.setDocumentation(docNode.getTextContent());
                    }
                }

                identityConstraints.add(constraint);
                logger.debug("Found identity constraint: {} of type {} on element {}",
                        constraint.getName(), type, extendedElem.getElementName());
            }
        }

        if (!identityConstraints.isEmpty()) {
            extendedElem.setIdentityConstraints(identityConstraints);
        }
    }

    /**
     * Processes XSD 1.1 assertions (xs:assert on complexType, xs:assertion on simpleType).
     * Assertions are XPath 2.0 boolean expressions that must evaluate to true for the instance to be valid.
     *
     * @param typeNode     The type node (complexType or simpleType) that may contain assertions
     * @param extendedElem The extended element to populate with assertion information
     */
    private void processAssertions(Node typeNode, XsdExtendedElement extendedElem) {
        if (typeNode == null) {
            return;
        }

        List<XsdAssertion> assertions = new ArrayList<>();

        for (Node child : getDirectChildElements(typeNode)) {
            String localName = child.getLocalName();
            XsdAssertion.Type type = null;

            // Determine the type of assertion
            if ("assert".equals(localName)) {
                type = XsdAssertion.Type.ASSERT;  // xs:assert on complexType
            } else if ("assertion".equals(localName)) {
                type = XsdAssertion.Type.ASSERTION;  // xs:assertion on simpleType restriction
            }

            if (type != null) {
                XsdAssertion assertion = new XsdAssertion();
                assertion.setType(type);
                assertion.setTest(getAttributeValue(child, "test"));
                assertion.setXpathDefaultNamespace(getAttributeValue(child, "xpathDefaultNamespace"));

                // Extract documentation from annotation
                Node annotationNode = getDirectChildElement(child, "annotation");
                if (annotationNode != null) {
                    Node docNode = getDirectChildElement(annotationNode, "documentation");
                    if (docNode != null) {
                        assertion.setDocumentation(docNode.getTextContent());
                    }
                }

                assertions.add(assertion);
                logger.debug("Found {} assertion with test: {} on element {}",
                        type, assertion.getTest(), extendedElem.getElementName());
            }
        }

        if (!assertions.isEmpty()) {
            extendedElem.setAssertions(assertions);
        }
    }

    /**
     * Processes XSD 1.1 type alternatives (xs:alternative) on an element.
     * Type alternatives provide conditional type assignment based on XPath 2.0 expressions.
     *
     * @param elementNode  The element node that may contain type alternatives
     * @param extendedElem The extended element to populate with type alternative information
     */
    private void processTypeAlternatives(Node elementNode, XsdExtendedElement extendedElem) {
        if (elementNode == null) {
            return;
        }

        List<TypeAlternative> typeAlternatives = new ArrayList<>();

        for (Node child : getDirectChildElements(elementNode, "alternative")) {
            TypeAlternative alternative = new TypeAlternative();
            alternative.setTest(getAttributeValue(child, "test"));  // null for default alternative
            alternative.setType(getAttributeValue(child, "type"));
            alternative.setXpathDefaultNamespace(getAttributeValue(child, "xpathDefaultNamespace"));

            // Extract documentation from annotation
            Node annotationNode = getDirectChildElement(child, "annotation");
            if (annotationNode != null) {
                Node docNode = getDirectChildElement(annotationNode, "documentation");
                if (docNode != null) {
                    alternative.setDocumentation(docNode.getTextContent());
                }
            }

            typeAlternatives.add(alternative);
            logger.debug("Found type alternative with test: {} and type: {} on element {}",
                    alternative.getTest() != null ? alternative.getTest() : "default",
                    alternative.getType(), extendedElem.getElementName());
        }

        if (!typeAlternatives.isEmpty()) {
            extendedElem.setTypeAlternatives(typeAlternatives);
        }
    }

    /**
     * Processes list and union types from simpleType definitions.
     *
     * @param typeDefinitionNode The simpleType node
     * @param extendedElem       The extended element to populate with list/union information
     */
    private void processListAndUnionTypes(Node typeDefinitionNode, XsdExtendedElement extendedElem) {
        if (typeDefinitionNode == null || !"simpleType".equals(typeDefinitionNode.getLocalName())) {
            return;
        }

        // Check for xs:list
        Node listNode = getDirectChildElement(typeDefinitionNode, "list");
        if (listNode != null) {
            String itemType = getAttributeValue(listNode, "itemType");
            if (itemType != null && !itemType.isEmpty()) {
                extendedElem.setListItemType(itemType);
                logger.debug("Found list type with itemType: {} on element {}",
                        itemType, extendedElem.getElementName());
            }
        }

        // Check for xs:union
        Node unionNode = getDirectChildElement(typeDefinitionNode, "union");
        if (unionNode != null) {
            String memberTypes = getAttributeValue(unionNode, "memberTypes");
            if (memberTypes != null && !memberTypes.isEmpty()) {
                // Split by whitespace to get individual member types
                List<String> types = Arrays.stream(memberTypes.split("\\s+"))
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
                extendedElem.setUnionMemberTypes(types);
                logger.debug("Found union type with member types: {} on element {}",
                        types, extendedElem.getElementName());
            }
        }
    }

    /**
     * Processes XSD 1.1 open content (xs:openContent) on a complexType.
     * Open content allows a type to accept additional elements beyond those explicitly defined.
     *
     * @param typeNode     The complexType node
     * @param extendedElem The extended element to populate with open content information
     */
    private void processOpenContent(Node typeNode, XsdExtendedElement extendedElem) {
        if (typeNode == null || !"complexType".equals(typeNode.getLocalName())) {
            return;
        }

        // Look for xs:openContent as direct child of complexType
        Node openContentNode = getDirectChildElement(typeNode, "openContent");
        if (openContentNode != null) {
            OpenContent openContent = parseOpenContentNode(openContentNode, false);
            if (openContent != null) {
                extendedElem.setOpenContent(openContent);
                logger.debug("Found openContent with mode: {} on element {}",
                        openContent.getMode(), extendedElem.getElementName());
            }
        }
    }

    /**
     * Parses an xs:openContent or xs:defaultOpenContent node.
     *
     * @param openContentNode The openContent or defaultOpenContent node
     * @param isDefault       Whether this is default open content
     * @return The parsed OpenContent object, or null if parsing failed
     */
    private OpenContent parseOpenContentNode(Node openContentNode, boolean isDefault) {
        if (openContentNode == null) {
            return null;
        }

        OpenContent openContent = new OpenContent();
        openContent.setDefault(isDefault);

        // Parse mode attribute (interleave or suffix, default is interleave)
        String modeStr = getAttributeValue(openContentNode, "mode", "interleave");
        try {
            openContent.setMode(OpenContent.Mode.valueOf(modeStr.toUpperCase()));
        } catch (IllegalArgumentException e) {
            openContent.setMode(OpenContent.Mode.INTERLEAVE);
            logger.warn("Invalid openContent mode: {}, defaulting to INTERLEAVE", modeStr);
        }

        // Look for nested xs:any
        Node anyNode = getDirectChildElement(openContentNode, "any");
        if (anyNode != null) {
            openContent.setNamespace(getAttributeValue(anyNode, "namespace", "##any"));
            openContent.setProcessContents(getAttributeValue(anyNode, "processContents", "strict"));
        }

        // Extract documentation from annotation
        Node annotationNode = getDirectChildElement(openContentNode, "annotation");
        if (annotationNode != null) {
            Node docNode = getDirectChildElement(annotationNode, "documentation");
            if (docNode != null) {
                openContent.setDocumentation(docNode.getTextContent());
            }
        }

        return openContent;
    }

    /**
     * Processes XSD wildcards (xs:any and xs:anyAttribute).
     * Wildcards allow elements or attributes from specified namespaces to appear in the content model.
     *
     * @param contextNode  The context node (element, complexType, or extension) that may contain wildcards
     * @param extendedElem The extended element to populate with wildcard information
     */
    private void processWildcards(Node contextNode, XsdExtendedElement extendedElem) {
        if (contextNode == null) {
            return;
        }

        List<Wildcard> wildcards = new ArrayList<>();

        // Process xs:any (element wildcards)
        for (Node child : getDirectChildElements(contextNode, "any")) {
            Wildcard wildcard = new Wildcard();
            wildcard.setType(Wildcard.Type.ANY);
            wildcard.setNamespace(getAttributeValue(child, "namespace", "##any"));
            wildcard.setMinOccurs(getAttributeValue(child, "minOccurs"));
            wildcard.setMaxOccurs(getAttributeValue(child, "maxOccurs"));

            // Parse processContents attribute
            String processContentsStr = getAttributeValue(child, "processContents", "strict");
            try {
                wildcard.setProcessContents(Wildcard.ProcessContents.valueOf(processContentsStr.toUpperCase()));
            } catch (IllegalArgumentException e) {
                wildcard.setProcessContents(Wildcard.ProcessContents.STRICT);
                logger.warn("Invalid processContents value: {}, defaulting to STRICT", processContentsStr);
            }

            // Extract documentation from annotation
            Node annotationNode = getDirectChildElement(child, "annotation");
            if (annotationNode != null) {
                Node docNode = getDirectChildElement(annotationNode, "documentation");
                if (docNode != null) {
                    wildcard.setDocumentation(docNode.getTextContent());
                }
            }

            wildcards.add(wildcard);
            logger.debug("Found xs:any wildcard with namespace: {} on element {}",
                    wildcard.getNamespace(), extendedElem.getElementName());
        }

        // Process xs:anyAttribute (attribute wildcards)
        for (Node child : getDirectChildElements(contextNode, "anyAttribute")) {
            Wildcard wildcard = new Wildcard();
            wildcard.setType(Wildcard.Type.ANY_ATTRIBUTE);
            wildcard.setNamespace(getAttributeValue(child, "namespace", "##any"));

            // Parse processContents attribute
            String processContentsStr = getAttributeValue(child, "processContents", "strict");
            try {
                wildcard.setProcessContents(Wildcard.ProcessContents.valueOf(processContentsStr.toUpperCase()));
            } catch (IllegalArgumentException e) {
                wildcard.setProcessContents(Wildcard.ProcessContents.STRICT);
                logger.warn("Invalid processContents value: {}, defaulting to STRICT", processContentsStr);
            }

            // Extract documentation from annotation
            Node annotationNode = getDirectChildElement(child, "annotation");
            if (annotationNode != null) {
                Node docNode = getDirectChildElement(annotationNode, "documentation");
                if (docNode != null) {
                    wildcard.setDocumentation(docNode.getTextContent());
                }
            }

            wildcards.add(wildcard);
            logger.debug("Found xs:anyAttribute wildcard with namespace: {} on element {}",
                    wildcard.getNamespace(), extendedElem.getElementName());
        }

        if (!wildcards.isEmpty()) {
            extendedElem.setWildcards(wildcards);
        }
    }

    private RestrictionInfo parseRestriction(Node restrictionNode) {
        String base = getAttributeValue(restrictionNode, "base");
        // The map is now initialized for lists
        Map<String, List<String>> facets = new LinkedHashMap<>();

        for (Node facetNode : getDirectChildElements(restrictionNode)) {
            String facetName = facetNode.getLocalName();
            if (!"annotation".equals(facetName)) {
                // Adds the value to the list of the corresponding facet name.
                // computeIfAbsent ensures that the list exists.
                facets.computeIfAbsent(facetName, k -> new ArrayList<>())
                        .add(getAttributeValue(facetNode, "value"));
            }
        }
        return new RestrictionInfo(base, facets);
    }

    private Node findTypeDefinition(Node elementNode, String typeName) {
        // Case 1: Inline definition
        if (elementNode != null) {
            Node inlineComplex = getDirectChildElement(elementNode, "complexType");
            if (inlineComplex != null) return inlineComplex;
            Node inlineSimple = getDirectChildElement(elementNode, "simpleType");
            if (inlineSimple != null) return inlineSimple;
        }
        // Case 2: Global reference
        if (typeName != null && !typeName.isEmpty()) {
            String cleanTypeName = stripNamespace(typeName);
            Node typeNode = complexTypeMap.get(cleanTypeName);
            if (typeNode != null) return typeNode;
            return simpleTypeMap.get(cleanTypeName);
        }
        return null;
    }

    /**
     * Finds a globally defined type (simple or complex) by its name.
     * This method is intended for use by other services that need to look up types.
     *
     * @param typeName The name of the type to find (e.g., "MyComplexType", "xs:string").
     * @return The corresponding DOM Node if a custom type is found, otherwise null.
     */
    public Node findTypeNodeByName(String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return null;
        }
        // Remove namespace prefix if present (e.g., "myNs:MyType" -> "MyType")
        String cleanTypeName = stripNamespace(typeName);

        // First, check the cache for complex types
        Node typeNode = complexTypeMap.get(cleanTypeName);
        if (typeNode != null) {
            return typeNode;
        }

        // If not found, check the cache for simple types
        return simpleTypeMap.get(cleanTypeName);
    }

    private Node findContentModel(Node elementNode, Node typeDefinitionNode) {
        Node contextNode = (typeDefinitionNode != null) ? typeDefinitionNode : elementNode;
        if (contextNode == null) return null;

        // Look inside <complexContent>
        Node complexContent = getDirectChildElement(contextNode, "complexContent");
        if (complexContent != null) {
            Node extension = getDirectChildElement(complexContent, "extension");
            if (extension != null) return extension;
            Node restriction = getDirectChildElement(complexContent, "restriction");
            if (restriction != null) return restriction;
        }

        // Look inside <simpleContent>
        Node simpleContent = getDirectChildElement(contextNode, "simpleContent");
        if (simpleContent != null) {
            Node extension = getDirectChildElement(simpleContent, "extension");
            if (extension != null) return extension;
            Node restriction = getDirectChildElement(simpleContent, "restriction");
            if (restriction != null) return restriction;
        }

        // Look for direct particles if no complex/simpleContent is present
        Node sequence = getDirectChildElement(contextNode, "sequence");
        if (sequence != null) return sequence;
        Node choice = getDirectChildElement(contextNode, "choice");
        if (choice != null) return choice;
        Node all = getDirectChildElement(contextNode, "all");
        if (all != null) return all;
        Node group = getDirectChildElement(contextNode, "group");
        return group;
    }

    private Node findRestriction(Node elementNode, Node typeDefinitionNode) {
        Node contextNode = (typeDefinitionNode != null) ? typeDefinitionNode : elementNode;
        if (contextNode == null) return null;

        // Direct <restriction> inside a <simpleType>
        Node restriction = getDirectChildElement(contextNode, "restriction");
        if (restriction != null) return restriction;

        // Restriction inside <simpleContent>
        Node simpleContent = getDirectChildElement(contextNode, "simpleContent");
        if (simpleContent != null) {
            return getDirectChildElement(simpleContent, "restriction");
        }
        return null;
    }

    public Node findReferencedNode(String localName, String ref) {
        String cleanRef = stripNamespace(ref);
        return switch (localName) {
            case "group" -> groupMap.get(cleanRef);
            case "attributeGroup" -> attributeGroupMap.get(cleanRef);
            case "element" -> elementMap.get(cleanRef);
            // Note: element references are handled in the main traversal logic
            // by looking up in the global element map, but this could be centralized here too.
            default -> null;
        };
    }

    private Map<String, Node> findAndCacheGlobalDefs(Document doc, String tagName) throws Exception {
        Map<String, Node> map = new HashMap<>();
        // Find global definitions directly under the schema root
        NodeList nodes = (NodeList) xpath.evaluate("/xs:schema/xs:" + tagName + "[@name]", doc, XPathConstants.NODESET);
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            String name = getAttributeValue(node, "name");
            if (name != null && !name.isEmpty()) {
                map.put(name, node);
            }
        }
        logger.debug("Cached {} global definitions for tag <{}>", map.size(), tagName);
        return map;
    }

    private void executeAndTrack(String taskName, Runnable task) {
        long startTime = System.currentTimeMillis();
        if (progressListener != null) {
            progressListener.onProgressUpdate(new ProgressUpdate(taskName, Status.RUNNING, 0));
        }
        try {
            task.run();
            long duration = System.currentTimeMillis() - startTime;
            if (progressListener != null) {
                progressListener.onProgressUpdate(new ProgressUpdate(taskName, Status.FINISHED, duration));
            }
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            if (progressListener != null) {
                progressListener.onProgressUpdate(new ProgressUpdate(taskName, Status.FAILED, duration));
            }
            throw e; // Re-throw the exception after reporting
        }
    }

    // --- General DOM/String Helper Methods ---

    private String getAttributeValue(Node node, String attrName) {
        return getAttributeValue(node, attrName, null);
    }

    private String getAttributeValue(Node node, String attrName, String defaultValue) {
        if (node == null || node.getAttributes() == null) return defaultValue;
        Node attrNode = node.getAttributes().getNamedItem(attrName);
        return (attrNode != null) ? attrNode.getNodeValue() : defaultValue;
    }

    private Node getDirectChildElement(Node parent, String childName) {
        if (parent == null) return null;
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && childName.equals(child.getLocalName())) {
                return child;
            }
        }
        return null;
    }

    private List<Node> getDirectChildElements(Node parent) {
        if (parent == null) return Collections.emptyList();
        List<Node> children = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                children.add(child);
            }
        }
        return children;
    }

    private List<Node> getDirectChildElements(Node parent, String childName) {
        if (parent == null) return Collections.emptyList();
        List<Node> children = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && childName.equals(child.getLocalName())) {
                children.add(child);
            }
        }
        return children;
    }

    private List<Node> nodeListToList(NodeList nodeList) {
        List<Node> list = new ArrayList<>();
        for (int i = 0; i < nodeList.getLength(); i++) {
            list.add(nodeList.item(i));
        }
        return list;
    }

    private String nodeToString(Node node) {
        try {
            StringWriter writer = new StringWriter();
            // Gets the Transformer instance that is specific to the current thread.
            Transformer transformer = transformerThreadLocal.get();
            transformer.transform(new DOMSource(node), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            logger.warn("Could not serialize node to string.", e);
            return "<!-- Error serializing node -->";
        }
    }

    public String stripNamespace(String value) {
        if (value == null) return "";
        int colonIndex = value.indexOf(':');
        return (colonIndex != -1) ? value.substring(colonIndex + 1) : value;
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private boolean isRemote(String location) {
        if (location == null || location.isBlank()) {
            return false;
        }
        String loc = location.toLowerCase();
        return loc.startsWith("http://") || loc.startsWith("https://") || loc.startsWith("ftp://");
    }

    private String getFileNameFromUrl(String urlString) {
        try {
            URI uri = new URI(urlString);
            String path = uri.getPath();
            if (path == null || path.isEmpty() || path.equals("/")) return "";
            return new File(path).getName();
        } catch (Exception e) {
            logger.warn("Could not parse URL to get filename: {}", urlString, e);
            return "";
        }
    }

    /**
     * Generates source code for a node, optionally including the full definition of referenced types.
     * When includeTypeDefinitionsInSourceCode is enabled and the node references a complexType or simpleType,
     * the complete type definition is appended to the source code.
     *
     * @param node               The current node (element or attribute)
     * @param typeName           The name of the referenced type (if any)
     * @param typeDefinitionNode The type definition node (if found)
     * @return The source code string, potentially including the type definition
     */
    private String generateSourceCodeWithOptionalTypeDefinition(Node node, String typeName, Node typeDefinitionNode) {
        // Always include the node's own source code
        String baseSourceCode = nodeToString(node);

        // If the option is disabled or there's no type reference, return base source code
        if (!includeTypeDefinitionsInSourceCode || typeName == null || typeName.isEmpty()) {
            return baseSourceCode;
        }

        // Check if this is a built-in XSD type (xs:string, xs:int, etc.)
        if (typeName.startsWith("xs:") || typeName.startsWith("xsd:")) {
            logger.debug("Skipping built-in type definition for: {}", typeName);
            return baseSourceCode;
        }

        // If we have an inline type definition, it's already included in the base source code
        if (typeDefinitionNode != null && typeDefinitionNode.getParentNode() == node) {
            logger.debug("Type definition is inline, already included in base source code");
            return baseSourceCode;
        }

        // Look up the global type definition
        String cleanTypeName = stripNamespace(typeName);
        Node globalTypeNode = complexTypeMap.get(cleanTypeName);
        if (globalTypeNode == null) {
            globalTypeNode = simpleTypeMap.get(cleanTypeName);
        }

        // If we found a global type definition, append it to the source code
        if (globalTypeNode != null) {
            String typeSourceCode = nodeToString(globalTypeNode);
            logger.debug("Including type definition for '{}' in source code", cleanTypeName);

            // Combine the base source code with the type definition
            String combinedSource = "<!-- Element Definition -->\n" +
                    baseSourceCode +
                    "\n\n" +
                    "<!-- Referenced Type Definition: " + cleanTypeName + " -->\n" +
                    typeSourceCode;

            return combinedSource;
        }

        logger.debug("No global type definition found for: {}", cleanTypeName);
        return baseSourceCode;
    }

    /**
     * Sets separate source code snippets for element definition and referenced type definition.
     * This method splits the source code into two parts for better display in collapsible containers.
     *
     * @param extendedElem       The extended element to update
     * @param node               The current node
     * @param typeName           The type name reference
     * @param typeDefinitionNode The type definition node (if inline)
     */
    private void setSourceCodeSnippets(XsdExtendedElement extendedElem, Node node, String typeName, Node typeDefinitionNode) {
        // Always set the element's own source code
        String elementSourceCode = nodeToString(node);
        extendedElem.setSourceCode(elementSourceCode);

        // If the option is disabled or there's no type reference, don't set referenced type code
        if (!includeTypeDefinitionsInSourceCode || typeName == null || typeName.isEmpty()) {
            return;
        }

        // Check if this is a built-in XSD type (xs:string, xs:int, etc.)
        if (typeName.startsWith("xs:") || typeName.startsWith("xsd:")) {
            logger.debug("Skipping built-in type definition for: {}", typeName);
            return;
        }

        // If we have an inline type definition, it's already included in the element source code
        if (typeDefinitionNode != null && typeDefinitionNode.getParentNode() == node) {
            logger.debug("Type definition is inline, already included in element source code");
            return;
        }

        // Look up the global type definition
        String cleanTypeName = stripNamespace(typeName);
        Node globalTypeNode = complexTypeMap.get(cleanTypeName);
        if (globalTypeNode == null) {
            globalTypeNode = simpleTypeMap.get(cleanTypeName);
        }

        // If we found a global type definition, set it separately
        if (globalTypeNode != null) {
            String typeSourceCode = nodeToString(globalTypeNode);
            logger.debug("Setting separate type definition for '{}' in source code", cleanTypeName);

            extendedElem.setReferencedTypeCode(typeSourceCode);
            extendedElem.setReferencedTypeName(cleanTypeName);
        } else {
            logger.debug("No global type definition found for: {}", cleanTypeName);
        }
    }
}