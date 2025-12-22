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
import org.fxt.freexmltoolkit.di.ServiceRegistry;
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

    public enum ImageOutputMethod {SVG, PNG, JPG}

    public ImageOutputMethod imageOutputMethod = ImageOutputMethod.SVG;
    Boolean useMarkdownRenderer = true;
    Boolean includeTypeDefinitionsInSourceCode = false;

    private TaskProgressListener progressListener;
    private final XsdSampleDataGenerator xsdSampleDataGenerator = new XsdSampleDataGenerator();
    private final PropertiesService propertiesService = ServiceRegistry.get(PropertiesService.class);
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

    // Language configuration for documentation generation
    private Set<String> discoveredLanguages = new LinkedHashSet<>();
    private Set<String> includedLanguages = null; // null = all languages
    private String fallbackLanguage = null; // Fallback language when "default" is not available

    // SVG documentation display configuration
    private boolean showDocumentationInSvg = true; // Whether to show documentation in SVG diagrams

    // Thread-local storage for element reference nodes (to preserve cardinality attributes)
    private static final ThreadLocal<Node> referenceNodeThreadLocal = new ThreadLocal<>();

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
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", String.valueOf(ServiceRegistry.get(PropertiesService.class).getXmlIndentSpaces()));
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

    /**
     * Returns all languages discovered during XSD parsing.
     * Must be called after processXsd() has completed.
     *
     * @return An unmodifiable set of language codes (e.g., "en", "de", "default")
     */
    public Set<String> getDiscoveredLanguages() {
        return Collections.unmodifiableSet(discoveredLanguages);
    }

    /**
     * Sets the languages to include in documentation output.
     * Pass null to include all languages.
     *
     * @param languages Set of language codes to include, or null for all
     */
    public void setIncludedLanguages(Set<String> languages) {
        this.includedLanguages = languages != null ? new LinkedHashSet<>(languages) : null;
    }

    /**
     * Returns the configured included languages.
     *
     * @return Set of included language codes, or null if all languages are included
     */
    public Set<String> getIncludedLanguages() {
        return includedLanguages;
    }

    /**
     * Sets the fallback language to use when "default" (no language tag) documentation is not available.
     *
     * @param language The language code to use as fallback (e.g., "en", "de"), or null for no fallback
     */
    public void setFallbackLanguage(String language) {
        this.fallbackLanguage = language;
    }

    /**
     * Returns the configured fallback language.
     *
     * @return The fallback language code, or null if no fallback is configured
     */
    public String getFallbackLanguage() {
        return fallbackLanguage;
    }

    /**
     * Sets whether documentation should be displayed in SVG diagrams.
     *
     * @param showDocumentation true to show documentation, false to hide it
     */
    public void setShowDocumentationInSvg(boolean showDocumentation) {
        this.showDocumentationInSvg = showDocumentation;
    }

    /**
     * Returns whether documentation is displayed in SVG diagrams.
     *
     * @return true if documentation is shown, false otherwise
     */
    public boolean isShowDocumentationInSvg() {
        return showDocumentationInSvg;
    }

    public void generateXsdDocumentation(File outputDirectory) throws Exception {
        logger.debug("Starting documentation generation...");
        processXsd(this.useMarkdownRenderer);

        xsdDocumentationHtmlService.setOutputDirectory(outputDirectory);
        xsdDocumentationHtmlService.setDocumentationData(xsdDocumentationData);
        xsdDocumentationHtmlService.setXsdDocumentationService(this);
        xsdDocumentationHtmlService.setIncludedLanguages(this.includedLanguages);

        xsdDocumentationSvgService.setOutputDirectory(outputDirectory);
        xsdDocumentationSvgService.setDocumentationData(xsdDocumentationData);

        // The ImageService is now centrally initialized here so it's ready for pre-creation.
        xsdDocumentationHtmlService.xsdDocumentationImageService = new XsdDocumentationImageService(xsdDocumentationData.getExtendedXsdElementMap());
        xsdDocumentationHtmlService.xsdDocumentationImageService.setShowDocumentation(this.showDocumentationInSvg);

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

        // Generate languages.json for JavaScript-based language switching
        executeAndTrack("Generating languages.json", () -> generateLanguagesJson(outputDirectory));
    }

    /**
     * Generates languages.json file containing all discovered documentation languages.
     * This file is used by the JavaScript language switcher for on-the-fly language switching.
     *
     * @param outputDirectory The output directory where languages.json will be created
     */
    private void generateLanguagesJson(File outputDirectory) {
        try {
            // Build the JSON content
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"available\": [");

            // Always include "default" first
            List<String> sortedLanguages = new ArrayList<>();
            sortedLanguages.add("default");

            // Add other discovered languages sorted alphabetically
            discoveredLanguages.stream()
                    .filter(lang -> !"default".equalsIgnoreCase(lang))
                    .sorted()
                    .forEach(sortedLanguages::add);

            for (int i = 0; i < sortedLanguages.size(); i++) {
                if (i > 0) json.append(", ");
                json.append("\"").append(sortedLanguages.get(i)).append("\"");
            }

            json.append("],\n");
            json.append("  \"default\": \"default\",\n");

            // Add fallback language setting
            if (fallbackLanguage != null && !fallbackLanguage.isBlank()) {
                json.append("  \"fallback\": \"").append(fallbackLanguage).append("\"\n");
            } else {
                json.append("  \"fallback\": null\n");
            }

            json.append("}\n");

            // Write to file
            Path languagesJsonPath = outputDirectory.toPath().resolve("languages.json");
            Files.writeString(languagesJsonPath, json.toString(), StandardCharsets.UTF_8);

            logger.debug("Generated languages.json with {} languages, fallback: {}", sortedLanguages.size(), fallbackLanguage);
        } catch (IOException e) {
            logger.error("Failed to generate languages.json", e);
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

        // Configure the sample data generator with type resolver BEFORE traversal
        // so that sample data generation can resolve named types to base XML types
        configureTypeResolver();

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
     * Configures the XsdSampleDataGenerator with a type resolver that resolves
     * named types to base XML types, collecting all restrictions along the way.
     */
    private void configureTypeResolver() {
        xsdSampleDataGenerator.setTypeResolver(typeName -> resolveTypeToBase(typeName, new HashSet<>()));
    }

    /**
     * Resolves a type name recursively to its base XML Schema type,
     * collecting all restrictions and facets along the type hierarchy.
     *
     * @param typeName The type name to resolve (e.g., "FundAmountType")
     * @param visited Set of already visited types to prevent infinite loops
     * @return ResolvedType with base type and merged restrictions, or null if not found
     */
    private XsdSampleDataGenerator.ResolvedType resolveTypeToBase(String typeName, Set<String> visited) {
        if (typeName == null || typeName.isBlank()) {
            return null;
        }

        // Normalize type name (remove namespace prefix for lookup)
        String localTypeName = typeName.contains(":")
            ? typeName.substring(typeName.lastIndexOf(":") + 1)
            : typeName;

        // Check for base XML types - these are the terminal cases
        if (isBaseXmlType(localTypeName)) {
            return new XsdSampleDataGenerator.ResolvedType(typeName, null);
        }

        // Prevent infinite loops in circular type references
        if (visited.contains(localTypeName)) {
            logger.debug("Circular type reference detected for type: {}", typeName);
            return null;
        }
        visited.add(localTypeName);

        // Look up the type in simpleTypeMap
        Node typeNode = simpleTypeMap.get(localTypeName);
        if (typeNode != null) {
            return resolveSimpleType(typeNode, visited);
        }

        // Look up the type in complexTypeMap
        typeNode = complexTypeMap.get(localTypeName);
        if (typeNode != null) {
            return resolveComplexType(typeNode, visited);
        }

        // Type not found in schema
        logger.debug("Type '{}' not found in schema, cannot resolve", typeName);
        return null;
    }

    /**
     * Resolves a simpleType node to its base type with restrictions.
     */
    private XsdSampleDataGenerator.ResolvedType resolveSimpleType(Node typeNode, Set<String> visited) {
        // Look for restriction element
        Node restriction = getDirectChildElement(typeNode, "restriction");
        if (restriction != null) {
            String baseType = getAttributeValue(restriction, "base");
            Map<String, List<String>> facets = extractFacetsFromRestriction(restriction);

            // Recursively resolve the base type
            XsdSampleDataGenerator.ResolvedType baseResolved = resolveTypeToBase(baseType, visited);
            if (baseResolved != null) {
                // Merge facets: current facets override inherited ones
                Map<String, List<String>> mergedFacets = new HashMap<>();
                if (baseResolved.mergedRestriction() != null && baseResolved.mergedRestriction().facets() != null) {
                    mergedFacets.putAll(baseResolved.mergedRestriction().facets());
                }
                mergedFacets.putAll(facets);

                return new XsdSampleDataGenerator.ResolvedType(
                    baseResolved.baseType(),
                    new XsdExtendedElement.RestrictionInfo(baseResolved.baseType(), mergedFacets)
                );
            }

            // Base type is a base XML type, return with current facets
            return new XsdSampleDataGenerator.ResolvedType(
                baseType,
                new XsdExtendedElement.RestrictionInfo(baseType, facets)
            );
        }

        // Look for list element
        Node listNode = getDirectChildElement(typeNode, "list");
        if (listNode != null) {
            String itemType = getAttributeValue(listNode, "itemType");
            if (itemType != null) {
                XsdSampleDataGenerator.ResolvedType itemResolved = resolveTypeToBase(itemType, visited);
                return itemResolved; // Return the item type's resolution
            }
        }

        // Look for union element
        Node unionNode = getDirectChildElement(typeNode, "union");
        if (unionNode != null) {
            String memberTypes = getAttributeValue(unionNode, "memberTypes");
            if (memberTypes != null && !memberTypes.isBlank()) {
                // Use the first member type
                String firstMember = memberTypes.split("\\s+")[0];
                return resolveTypeToBase(firstMember, visited);
            }
        }

        return null;
    }

    /**
     * Resolves a complexType node to its base type (for simpleContent extensions).
     */
    private XsdSampleDataGenerator.ResolvedType resolveComplexType(Node typeNode, Set<String> visited) {
        // Look for simpleContent element
        Node simpleContent = getDirectChildElement(typeNode, "simpleContent");
        if (simpleContent != null) {
            // Check for extension
            Node extension = getDirectChildElement(simpleContent, "extension");
            if (extension != null) {
                String baseType = getAttributeValue(extension, "base");
                return resolveTypeToBase(baseType, visited);
            }

            // Check for restriction
            Node restriction = getDirectChildElement(simpleContent, "restriction");
            if (restriction != null) {
                String baseType = getAttributeValue(restriction, "base");
                Map<String, List<String>> facets = extractFacetsFromRestriction(restriction);

                XsdSampleDataGenerator.ResolvedType baseResolved = resolveTypeToBase(baseType, visited);
                if (baseResolved != null) {
                    Map<String, List<String>> mergedFacets = new HashMap<>();
                    if (baseResolved.mergedRestriction() != null && baseResolved.mergedRestriction().facets() != null) {
                        mergedFacets.putAll(baseResolved.mergedRestriction().facets());
                    }
                    mergedFacets.putAll(facets);

                    return new XsdSampleDataGenerator.ResolvedType(
                        baseResolved.baseType(),
                        new XsdExtendedElement.RestrictionInfo(baseResolved.baseType(), mergedFacets)
                    );
                }

                return new XsdSampleDataGenerator.ResolvedType(
                    baseType,
                    new XsdExtendedElement.RestrictionInfo(baseType, facets)
                );
            }
        }

        // Look for complexContent element
        Node complexContent = getDirectChildElement(typeNode, "complexContent");
        if (complexContent != null) {
            // Check for extension or restriction
            Node extension = getDirectChildElement(complexContent, "extension");
            if (extension != null) {
                String baseType = getAttributeValue(extension, "base");
                return resolveTypeToBase(baseType, visited);
            }

            Node restriction = getDirectChildElement(complexContent, "restriction");
            if (restriction != null) {
                String baseType = getAttributeValue(restriction, "base");
                return resolveTypeToBase(baseType, visited);
            }
        }

        // Complex type without simpleContent - cannot generate text content
        return null;
    }

    /**
     * Extracts facets from a restriction element.
     */
    private Map<String, List<String>> extractFacetsFromRestriction(Node restriction) {
        Map<String, List<String>> facets = new HashMap<>();

        NodeList children = restriction.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) continue;

            String localName = child.getLocalName();
            if (localName == null) continue;

            // These are facet elements
            switch (localName) {
                case "enumeration", "pattern" -> {
                    String value = getAttributeValue(child, "value");
                    if (value != null) {
                        facets.computeIfAbsent(localName, k -> new ArrayList<>()).add(value);
                    }
                }
                case "minLength", "maxLength", "length",
                     "minInclusive", "maxInclusive", "minExclusive", "maxExclusive",
                     "totalDigits", "fractionDigits", "whiteSpace" -> {
                    String value = getAttributeValue(child, "value");
                    if (value != null) {
                        facets.put(localName, List.of(value));
                    }
                }
            }
        }

        return facets;
    }

    /**
     * Checks if the given type name is a base XML Schema type.
     */
    private boolean isBaseXmlType(String typeName) {
        if (typeName == null) return false;

        String localName = typeName.contains(":")
            ? typeName.substring(typeName.lastIndexOf(":") + 1)
            : typeName;

        return switch (localName.toLowerCase()) {
            case "string", "normalizedstring", "token", "language", "nmtoken", "nmtokens",
                 "name", "ncname", "id", "idref", "idrefs", "entity", "entities",
                 "integer", "nonnegativeinteger", "positiveinteger", "nonpositiveinteger", "negativeinteger",
                 "long", "int", "short", "byte",
                 "unsignedlong", "unsignedint", "unsignedshort", "unsignedbyte",
                 "decimal", "float", "double",
                 "boolean",
                 "date", "time", "datetime", "duration", "gyear", "gmonth", "gday", "gyearmonth", "gmonthday",
                 "base64binary", "hexbinary",
                 "anyuri", "qname", "notation",
                 "anysimpletype", "anytype" -> true;
            default -> false;
        };
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
                    // Flatten any SEQUENCE/CHOICE/ALL containers to get actual child elements
                    List<XsdExtendedElement> flattenedChildren = flattenContainerChildren(targetElement);

                    for (XsdExtendedElement childElement : flattenedChildren) {
                        if (childElement != null && childElement.isMandatory()) {
                            // Skip attributes (elements starting with @)
                            if (childElement.getElementName().startsWith("@")) {
                                logger.debug("Skipping attribute: {}", childElement.getElementName());
                                continue;
                            }

                            // Skip container elements - they are structural, not actual XML elements
                            String childName = childElement.getElementName();
                            if (childName.startsWith("SEQUENCE") || childName.startsWith("CHOICE") || childName.startsWith("ALL")) {
                                logger.debug("Skipping container element: {}", childName);
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
                            logger.debug("Added mandatory child from processed data: {} (isAttribute=false)",
                                    childElement.getElementName());
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
     * Flattens container children (SEQUENCE, CHOICE, ALL) to get actual element children.
     * Container elements are structural and should not appear as actual XML elements.
     *
     * @param parentElement The parent element whose children should be flattened
     * @return List of actual child elements (not containers)
     */
    private List<XsdExtendedElement> flattenContainerChildren(XsdExtendedElement parentElement) {
        List<XsdExtendedElement> result = new ArrayList<>();

        if (parentElement == null || !parentElement.hasChildren()) {
            return result;
        }

        for (String childXPath : parentElement.getChildren()) {
            XsdExtendedElement childElement = xsdDocumentationData.getExtendedXsdElementMap().get(childXPath);
            if (childElement == null) {
                continue;
            }

            String childName = childElement.getElementName();
            if (childName == null) {
                continue;
            }

            // Check if this is a container element (SEQUENCE, CHOICE, ALL)
            if (childName.startsWith("SEQUENCE") || childName.startsWith("CHOICE") || childName.startsWith("ALL")) {
                // Recursively flatten container children
                result.addAll(flattenContainerChildren(childElement));
            } else {
                // Regular element - add it directly
                result.add(childElement);
            }
        }

        return result;
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
                        // Flatten any SEQUENCE/CHOICE/ALL containers to get actual child elements
                        List<XsdExtendedElement> flattenedChildren = flattenContainerChildren(element);

                        for (XsdExtendedElement childElement : flattenedChildren) {
                            if (childElement == null) continue;

                            String childName = childElement.getElementName();
                            // Skip container elements - they are structural, not actual XML elements
                            if (childName != null && (childName.startsWith("SEQUENCE") || childName.startsWith("CHOICE") || childName.startsWith("ALL"))) {
                                continue;
                            }

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
                    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", String.valueOf(ServiceRegistry.get(PropertiesService.class).getXmlIndentSpaces()));
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
                // Skip elements from external namespaces that can't be properly generated as sample data
                // These typically require special handling (e.g., ds:Signature from XML Digital Signature)
                if (ref.contains(":") && isExternalNamespaceReference(ref)) {
                    logger.debug("Skipping external namespace reference: {}", ref);
                    return;
                }

                Node referencedNode = findReferencedNode(node.getLocalName(), ref);
                if (referencedNode != null) {
                    // Store the reference node to preserve cardinality attributes (minOccurs/maxOccurs)
                    referenceNodeThreadLocal.set(node);
                    try {
                        traverseNode(referencedNode, currentXPath, parentXPath, level, visitedOnPath);
                    } finally {
                        referenceNodeThreadLocal.remove();
                    }
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

        // Check if this element is from a reference and set the cardinality node
        Node refNode = referenceNodeThreadLocal.get();
        if (refNode != null) {
            extendedElem.setCardinalityNode(refNode);
        }

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
                // Named type reference - but also check if it's a complexType with simpleContent extension
                // to expose the base type (e.g., FXRateType extends xs:decimal)
                String resolvedType = typeName;
                if (typeDefinitionNode != null && "complexType".equals(typeDefinitionNode.getLocalName())) {
                    Node simpleContent = getDirectChildElement(typeDefinitionNode, "simpleContent");
                    if (simpleContent != null) {
                        Node extension = getDirectChildElement(simpleContent, "extension");
                        if (extension != null) {
                            String baseType = getAttributeValue(extension, "base");
                            if (baseType != null && !baseType.isEmpty()) {
                                // Use the base type (e.g., xs:decimal) for sample data generation
                                // since the generator needs the primitive type, not the named type
                                resolvedType = baseType;
                                logger.debug("Resolved named type '{}' to base type '{}' via simpleContent extension",
                                        typeName, baseType);
                            }
                        }
                    }
                }
                extendedElem.setElementType(resolvedType);
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
                    // Check for simpleContent with extension (e.g., xs:simpleContent > xs:extension base="xs:float")
                    Node simpleContent = getDirectChildElement(typeDefinitionNode, "simpleContent");
                    if (simpleContent != null) {
                        Node extension = getDirectChildElement(simpleContent, "extension");
                        if (extension != null) {
                            String baseType = getAttributeValue(extension, "base");
                            if (baseType != null && !baseType.isEmpty()) {
                                extendedElem.setElementType(baseType);
                            } else {
                                extendedElem.setElementType("(anonymous)");
                            }
                        } else {
                            extendedElem.setElementType("(anonymous)");
                        }
                    } else {
                        extendedElem.setElementType("(anonymous)");
                    }
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

        // Handle the case where contentNode itself is a choice/sequence/all
        // This happens when an inline complexType has a direct compositor as its content
        if ("choice".equals(localName) || "sequence".equals(localName) || "all".equals(localName)) {
            // Create a wrapper element for this compositor
            String containerName = localName.toUpperCase();
            String containerXPath = parentXPath + "/" + containerName + "_" + counter;

            // Create and register the container element
            XsdExtendedElement containerElem = new XsdExtendedElement();
            containerElem.setElementName(containerName);
            containerElem.setCurrentXpath(containerXPath);
            containerElem.setParentXpath(parentXPath);
            containerElem.setLevel(level);
            containerElem.setCounter(counter);
            containerElem.setCurrentNode(contentNode);
            containerElem.setElementType("(container)");

            // Add container as child of parent
            if (xsdDocumentationData.getExtendedXsdElementMap().containsKey(parentXPath)) {
                List<String> parentChildren = xsdDocumentationData.getExtendedXsdElementMap().get(parentXPath).getChildren();
                if (!parentChildren.contains(containerXPath)) {
                    parentChildren.add(containerXPath);
                }
            }
            xsdDocumentationData.getExtendedXsdElementMap().put(containerXPath, containerElem);

            // Process children of this compositor with the container as their parent
            for (Node child : getDirectChildElements(contentNode)) {
                String childName = getAttributeValue(child, "name", getAttributeValue(child, "ref"));
                String childLocalName = child.getLocalName();

                if ("element".equals(childLocalName)) {
                    String childXPath = containerXPath + "/" + childName;
                    traverseNode(child, childXPath, containerXPath, level + 1, visitedOnPath);
                } else if ("sequence".equals(childLocalName) || "choice".equals(childLocalName) || "all".equals(childLocalName)) {
                    // Nested compositor
                    processComplexContent(child, containerXPath, level + 1, visitedOnPath);
                } else if ("attribute".equals(childLocalName)) {
                    String childXPath = parentXPath + "/@" + childName; // Attributes go on parent, not container
                    traverseNode(child, childXPath, parentXPath, level + 1, visitedOnPath);
                }
            }
            return; // Done processing this compositor
        }

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

        XsdExtendedElement rootElement = rootElements.getFirst();
        String rootName = rootElement.getElementName();
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

        // Add root element attributes (those children starting with @)
        List<XsdExtendedElement> rootAttributes = rootElement.getChildren().stream()
                .map(xsdDocumentationData.getExtendedXsdElementMap()::get)
                .filter(Objects::nonNull)
                .filter(e -> e.getElementName().startsWith("@"))
                .toList();

        for (XsdExtendedElement attr : rootAttributes) {
            // Include attribute if mandatory, or if it has a fixed/default value
            String fixedOrDefault = getAttributeValue(attr.getCurrentNode(), "fixed",
                    getAttributeValue(attr.getCurrentNode(), "default", null));
            if (mandatoryOnly && !attr.isMandatory() && fixedOrDefault == null) continue;
            String attrName = attr.getElementName().substring(1); // Remove @ prefix
            String attrValue = (fixedOrDefault != null)
                    ? fixedOrDefault
                    : (attr.getDisplaySampleData() != null ? attr.getDisplaySampleData() : "");
            xmlBuilder.append(" ").append(attrName).append("=\"").append(escapeXml(attrValue)).append("\"");
        }

        xmlBuilder.append(">\n");

        // Build the content without the root element tags (only child elements, not attributes)
        List<XsdExtendedElement> rootChildElements = rootElement.getChildren().stream()
                .map(xsdDocumentationData.getExtendedXsdElementMap()::get)
                .filter(Objects::nonNull)
                .filter(e -> !e.getElementName().startsWith("@"))
                .toList();

        for (XsdExtendedElement child : rootChildElements) {
            buildXmlElementContent(xmlBuilder, child, mandatoryOnly, maxOccurrences, 1);
        }

        xmlBuilder.append("</").append(rootName).append(">\n");
        
        return xmlBuilder.toString();
    }

    /**
     * Validates the generated XML against the XSD schema.
     * Automatically detects XSD 1.1 schemas and uses appropriate validation.
     * @param xmlContent The XML content to validate
     * @return Validation result with success status and any error messages
     */
    public ValidationResult validateXmlAgainstSchema(String xmlContent) {
        try {
            // Determine if schema requires XSD 1.1
            boolean requiresXsd11 = isXsd11Schema();

            // Create appropriate schema factory
            SchemaFactory factory;
            if (requiresXsd11) {
                // Use Xerces XSD 1.1 factory
                factory = new org.apache.xerces.jaxp.validation.XMLSchema11Factory();
                logger.debug("Using XSD 1.1 schema factory for validation");
            } else {
                factory = SchemaFactory.newInstance(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);
                logger.debug("Using XSD 1.0 schema factory for validation");
            }

            // Set up resource resolver for imports/includes
            File schemaFile = new File(xsdFilePath);
            File schemaDir = schemaFile.getParentFile();
            factory.setResourceResolver(new org.w3c.dom.ls.LSResourceResolver() {
                @Override
                public org.w3c.dom.ls.LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId, String baseURI) {
                    if (systemId != null && schemaDir != null) {
                        File resolvedFile = new File(schemaDir, systemId);
                        if (resolvedFile.exists()) {
                            try {
                                return new LSInputImpl(publicId, systemId, resolvedFile);
                            } catch (Exception e) {
                                logger.debug("Could not resolve resource: {}", systemId);
                            }
                        }
                    }
                    return null;
                }
            });

            // Load the schema
            Source schemaSource = new StreamSource(schemaFile);
            Schema schema = factory.newSchema(schemaSource);

            // Create validator
            Validator validator = schema.newValidator();

            // Create error handler to collect validation errors
            List<ValidationError> errors = new ArrayList<>();
            final int[] errorCount = {0};
            final int[] warningCount = {0};

            validator.setErrorHandler(new org.xml.sax.ErrorHandler() {
                @Override
                public void warning(org.xml.sax.SAXParseException exception) {
                    warningCount[0]++;
                    errors.add(new ValidationError(
                            exception.getLineNumber(),
                            exception.getColumnNumber(),
                            "Warning",
                            exception.getMessage()
                    ));
                }

                @Override
                public void error(org.xml.sax.SAXParseException exception) {
                    errorCount[0]++;
                    errors.add(new ValidationError(
                            exception.getLineNumber(),
                            exception.getColumnNumber(),
                            "Error",
                            exception.getMessage()
                    ));
                }

                @Override
                public void fatalError(org.xml.sax.SAXParseException exception) {
                    errorCount[0]++;
                    errors.add(new ValidationError(
                            exception.getLineNumber(),
                            exception.getColumnNumber(),
                            "Fatal Error",
                            exception.getMessage()
                    ));
                }
            });

            // Validate the XML
            Source xmlSource = new StreamSource(new StringReader(xmlContent));
            validator.validate(xmlSource);

            // Determine if validation passed (no errors, warnings are OK)
            boolean isValid = errorCount[0] == 0;
            String message;

            if (isValid && warningCount[0] > 0) {
                message = warningCount[0] + " warning(s) found";
            } else if (!isValid) {
                message = errorCount[0] + " error(s) found";
            } else {
                message = "Validation successful";
            }

            return new ValidationResult(isValid, message, errors);

        } catch (Exception e) {
            return new ValidationResult(false, "Validation failed: " + e.getMessage(),
                    List.of(new ValidationError(0, 0, "Fatal Error", e.getMessage())));
        }
    }

    /**
     * Result of XML validation against XSD schema.
     */
    public record ValidationResult(boolean isValid, String message, List<ValidationError> errors) {
        public ValidationResult(boolean isValid, String message) {
            this(isValid, message, List.of());
        }
    }

    /**
     * Represents a single validation error with details.
     */
    public record ValidationError(
            int lineNumber,
            int columnNumber,
            String severity,  // "Error", "Warning", "Fatal Error"
            String message
    ) {
        @Override
        public String toString() {
            return String.format("[%s] Line %d, Column %d: %s", severity, lineNumber, columnNumber, message);
        }
    }

    private void buildXmlElement(StringBuilder sb, XsdExtendedElement element, boolean mandatoryOnly, int maxOccurrences, int indentLevel) {
        if (element == null || (mandatoryOnly && !element.isMandatory())) {
            return;
        }
        String elementName = element.getElementName();
        if (elementName == null || elementName.startsWith("@")) {
            return; // Attributes are handled by their parent
        }

        // Handle container elements (SEQUENCE, CHOICE, ALL) - output their children, not the container itself
        if (elementName.startsWith("SEQUENCE") || elementName.startsWith("ALL")) {
            // For SEQUENCE and ALL, just process all children
            List<XsdExtendedElement> containerChildren = element.getChildren().stream()
                    .map(xsdDocumentationData.getExtendedXsdElementMap()::get)
                    .filter(Objects::nonNull)
                    .filter(e -> !e.getElementName().startsWith("@"))
                    .toList();
            for (XsdExtendedElement child : containerChildren) {
                buildXmlElement(sb, child, mandatoryOnly, maxOccurrences, indentLevel);
            }
            return;
        }
        if (elementName.startsWith("CHOICE")) {
            // For CHOICE, randomly select one option
            List<XsdExtendedElement> choiceOptions = element.getChildren().stream()
                    .map(xsdDocumentationData.getExtendedXsdElementMap()::get)
                    .filter(Objects::nonNull)
                    .filter(e -> !e.getElementName().startsWith("@"))
                    .toList();
            if (!choiceOptions.isEmpty()) {
                XsdExtendedElement selected = choiceOptions.get(random.nextInt(choiceOptions.size()));
                buildXmlElement(sb, selected, mandatoryOnly, maxOccurrences, indentLevel);
            }
            return;
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
        String elementName = element.getElementName();
        if (elementName == null || elementName.startsWith("@")) {
            return; // Attributes are handled by their parent
        }

        // Skip optional container elements that would be empty
        // This prevents generating elements like <BreakDowns></BreakDowns> when there are no children
        if (!element.isMandatory() && wouldProduceEmptyContainer(element, mandatoryOnly, indentLevel)) {
            return;
        }

        // Handle container elements (SEQUENCE, CHOICE, ALL) - these are structural, not actual XML elements
        // Process their children instead of outputting the container itself
        if (elementName.startsWith("SEQUENCE") || elementName.startsWith("ALL")) {
            List<XsdExtendedElement> containerChildren = element.getChildren().stream()
                    .map(xsdDocumentationData.getExtendedXsdElementMap()::get)
                    .filter(Objects::nonNull)
                    .filter(e -> e.getElementName() != null && !e.getElementName().startsWith("@"))
                    .toList();
            for (XsdExtendedElement child : containerChildren) {
                buildXmlElementContent(sb, child, mandatoryOnly, maxOccurrences, indentLevel);
            }
            return;
        }
        if (elementName.startsWith("CHOICE")) {
            List<XsdExtendedElement> choiceOptions = element.getChildren().stream()
                    .map(xsdDocumentationData.getExtendedXsdElementMap()::get)
                    .filter(Objects::nonNull)
                    .filter(e -> e.getElementName() != null && !e.getElementName().startsWith("@"))
                    .toList();
            if (!choiceOptions.isEmpty()) {
                // Get minOccurs and maxOccurs from the CHOICE element
                Node choiceNode = element.getCurrentNode();
                String minOccursStr = getAttributeValue(choiceNode, "minOccurs", "1");
                String maxOccursStr = getAttributeValue(choiceNode, "maxOccurs", "1");

                int minOccurs;
                int choiceMaxOccurs;
                try {
                    minOccurs = Integer.parseInt(minOccursStr);
                } catch (NumberFormatException e) {
                    minOccurs = 1;
                }
                if ("unbounded".equalsIgnoreCase(maxOccursStr)) {
                    choiceMaxOccurs = maxOccurrences;
                } else {
                    try {
                        choiceMaxOccurs = Math.min(Integer.parseInt(maxOccursStr), maxOccurrences);
                    } catch (NumberFormatException e) {
                        choiceMaxOccurs = 1;
                    }
                }

                // Skip optional choices in mandatory-only mode
                if (mandatoryOnly && minOccurs == 0) {
                    return;
                }

                // Calculate repeat count based on mode
                int repeatCount;
                if (mandatoryOnly) {
                    repeatCount = minOccurs;
                } else {
                    int effectiveMax = Math.min(choiceMaxOccurs, maxOccurrences);
                    if (minOccurs >= effectiveMax) {
                        repeatCount = effectiveMax;
                    } else {
                        repeatCount = minOccurs + random.nextInt(effectiveMax - minOccurs + 1);
                    }
                }

                // Generate the appropriate number of selections from the choice
                for (int i = 0; i < repeatCount; i++) {
                    XsdExtendedElement selected = choiceOptions.get(random.nextInt(choiceOptions.size()));
                    buildXmlElementContent(sb, selected, mandatoryOnly, maxOccurrences, indentLevel);
                }
            }
            return;
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
            String elementName = childElement.getElementName();
            if (elementName == null) continue;

            // Check if this child is a SEQUENCE or ALL container (structural, not actual XML elements)
            if (elementName.startsWith("SEQUENCE") || elementName.startsWith("ALL")) {
                // SEQUENCE and ALL are structural containers - just output all their children
                // without outputting the container element itself
                List<XsdExtendedElement> containerChildren = childElement.getChildren().stream()
                        .map(xsdDocumentationData.getExtendedXsdElementMap()::get)
                        .filter(Objects::nonNull)
                        .filter(e -> !e.getElementName().startsWith("@"))
                        .toList();

                if (!containerChildren.isEmpty()) {
                    logger.debug("Processing {} container with {} children", elementName, containerChildren.size());
                    // Recursively process children (they may contain nested CHOICE/SEQUENCE)
                    processChildElementsForGeneration(sb, containerChildren, mandatoryOnly, maxOccurrences, indentLevel);
                }
            }
            // Check if this child is a CHOICE container
            else if (elementName.startsWith("CHOICE")) {
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

                // For CHOICE: select exactly ONE option per occurrence (XSD choice means pick one alternative)
                // minOccurs/maxOccurs on the choice refers to how many times the whole choice
                // can appear, not how many options to select from within a single choice
                int repeatCount;
                if (mandatoryOnly) {
                    // In mandatory mode, generate exactly minOccurs times
                    repeatCount = minOccurs;
                } else {
                    // In non-mandatory mode, generate between minOccurs and maxOccurs
                    // Use a random value in that range, but cap at maxOccurrences
                    int effectiveMax = Math.min(maxOccurs, maxOccurrences);
                    if (minOccurs >= effectiveMax) {
                        repeatCount = effectiveMax;
                    } else {
                        repeatCount = minOccurs + random.nextInt(effectiveMax - minOccurs + 1);
                    }
                }

                // Randomly select ONE element from the choice options
                XsdExtendedElement selectedOption = choiceOptions.get(random.nextInt(choiceOptions.size()));
                logger.debug("Selected element '{}' from CHOICE (1 of {} options)",
                        selectedOption.getElementName(), choiceOptions.size());

                // Generate XML for the selected option (may repeat if choice has maxOccurs > 1)
                for (int i = 0; i < repeatCount; i++) {
                    // For repeated choices, re-select to potentially get different options
                    if (i > 0) {
                        selectedOption = choiceOptions.get(random.nextInt(choiceOptions.size()));
                    }
                    buildXmlElementContent(sb, selectedOption, mandatoryOnly, maxOccurrences, indentLevel);
                }
            } else {
                // Not a container element, process normally
                buildXmlElementContent(sb, childElement, mandatoryOnly, maxOccurrences, indentLevel);
            }
        }
    }

    /**
     * Checks if an element would produce an empty container (no text content and no child elements).
     * This is used to skip optional elements that would result in invalid XML like <BreakDowns></BreakDowns>.
     *
     * @param element The element to check
     * @param mandatoryOnly Whether only mandatory children should be considered
     * @param indentLevel Current recursion depth
     * @return true if the element would be empty, false otherwise
     */
    private boolean wouldProduceEmptyContainer(XsdExtendedElement element, boolean mandatoryOnly, int indentLevel) {
        // Check if element has sample data (text content)
        String sampleData = element.getDisplaySampleData();
        if (sampleData != null && !sampleData.isEmpty()) {
            return false; // Has text content, not empty
        }

        // Check if element has any children that would be generated
        List<XsdExtendedElement> childElements = element.getChildren().stream()
                .map(xsdDocumentationData.getExtendedXsdElementMap()::get)
                .filter(Objects::nonNull)
                .filter(e -> e.getElementName() != null && !e.getElementName().startsWith("@"))
                .toList();

        if (childElements.isEmpty()) {
            // No non-attribute children - check if it has attributes (which make it non-empty)
            boolean hasAttributes = element.getChildren().stream()
                    .map(xsdDocumentationData.getExtendedXsdElementMap()::get)
                    .filter(Objects::nonNull)
                    .anyMatch(e -> e.getElementName() != null && e.getElementName().startsWith("@"));
            return !hasAttributes;
        }

        // Check if any child would actually be rendered
        for (XsdExtendedElement child : childElements) {
            String childName = child.getElementName();
            if (childName == null) continue;

            // Handle structural containers
            if (childName.startsWith("SEQUENCE") || childName.startsWith("ALL") || childName.startsWith("CHOICE")) {
                // Recursively check container children
                if (!wouldProduceEmptyContainer(child, mandatoryOnly, indentLevel + 1)) {
                    return false;
                }
            } else if (!mandatoryOnly || child.isMandatory()) {
                // Non-container element that would be rendered
                return false;
            }
        }

        // All checks passed - element would be empty
        return true;
    }

    /**
     * Checks if a reference points to an external namespace that shouldn't be generated as sample data.
     * External namespace references (like ds:Signature from XML Digital Signature) require special handling
     * and typically reference schemas that are imported but not included in the current document.
     *
     * @param ref The element reference (e.g., "ds:Signature", "tns:Element")
     * @return true if the reference is to an external namespace that should be skipped
     */
    private boolean isExternalNamespaceReference(String ref) {
        if (ref == null || ref.isEmpty() || !ref.contains(":")) {
            return false;
        }

        String prefix = ref.substring(0, ref.indexOf(":"));

        // Check if this prefix maps to an external namespace (not the target namespace)
        // Common external namespace prefixes that should be skipped:
        // - ds: XML Digital Signature (http://www.w3.org/2000/09/xmldsig#)
        // - xenc: XML Encryption
        // - saml: SAML assertions
        // - wsse: WS-Security
        Set<String> externalPrefixes = Set.of("ds", "dsig", "xenc", "saml", "wsse", "wsu", "soap", "xsi");
        if (externalPrefixes.contains(prefix)) {
            return true;
        }

        // Also check if we have namespace information from the schema
        if (xsdDocumentationData != null && xsdDocumentationData.getNamespaces() != null) {
            Map<String, String> namespaces = xsdDocumentationData.getNamespaces();
            String targetNs = xsdDocumentationData.getTargetNamespace();
            String prefixNs = namespaces.get(prefix);

            // If the prefix maps to a namespace different from the target namespace,
            // and it's not the XSD namespace itself, it's likely external
            if (prefixNs != null && targetNs != null && !prefixNs.equals(targetNs)
                    && !prefixNs.equals(NS_URI)) {
                return true;
            }
        }

        return false;
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
        // Allow DOCTYPE declarations (required for some W3C schemas like xmldsig-core-schema.xsd)
        // but disable external entity processing for security
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setExpandEntityReferences(false);

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
            // Normalize language to lowercase for case-insensitive comparison
            String normalizedLang = lang != null ? lang.toLowerCase() : "default";
            discoveredLanguages.add(normalizedLang); // Track discovered language for UI configuration
            extendedElem.getDocumentations().add(new DocumentationInfo(normalizedLang, docNode.getTextContent()));
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

        // First, inherit facets from the base type if it's a named type (not primitive)
        if (base != null && !base.startsWith("xs:") && !base.startsWith("xsd:")) {
            RestrictionInfo inheritedFacets = getInheritedFacets(base);
            if (inheritedFacets != null && inheritedFacets.facets() != null) {
                facets.putAll(inheritedFacets.facets());
                // Update the base to the ultimate primitive type if available
                if (inheritedFacets.base() != null) {
                    base = inheritedFacets.base();
                }
            }
        }

        // Then, parse direct child facets (these override inherited facets)
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

    /**
     * Gets inherited facets from a named type by following the type chain.
     * This handles cases like a restriction with base="LEICodeType" where the actual
     * pattern facet is defined in the LEICodeType simpleType.
     *
     * @param typeName The name of the type to get inherited facets from.
     * @return RestrictionInfo with the ultimate base type and all inherited facets.
     */
    private RestrictionInfo getInheritedFacets(String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return null;
        }

        String cleanTypeName = stripNamespace(typeName);
        Node typeNode = simpleTypeMap.get(cleanTypeName);
        if (typeNode == null) {
            return null;
        }

        // Find restriction in the simpleType
        Node restriction = getDirectChildElement(typeNode, "restriction");
        if (restriction == null) {
            return null;
        }

        // Recursively get base type's facets (for chains like Type1 -> Type2 -> xs:string)
        String baseType = getAttributeValue(restriction, "base");
        Map<String, List<String>> facets = new LinkedHashMap<>();

        // First get inherited facets from the base type
        if (baseType != null && !baseType.startsWith("xs:") && !baseType.startsWith("xsd:")) {
            RestrictionInfo inherited = getInheritedFacets(baseType);
            if (inherited != null && inherited.facets() != null) {
                facets.putAll(inherited.facets());
                baseType = inherited.base(); // Use the ultimate primitive type
            }
        }

        // Then add this type's direct facets (override inherited)
        for (Node facetNode : getDirectChildElements(restriction)) {
            String facetName = facetNode.getLocalName();
            if (!"annotation".equals(facetName)) {
                facets.computeIfAbsent(facetName, k -> new ArrayList<>())
                        .add(getAttributeValue(facetNode, "value"));
            }
        }

        return new RestrictionInfo(baseType, facets);
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

    // --- Schema Version Detection ---

    /**
     * Checks if the loaded schema requires XSD 1.1.
     * Looks for vc:minVersion="1.1" attribute on the schema element.
     */
    private boolean isXsd11Schema() {
        // If doc is not loaded yet, try to parse the schema file to check version
        Document schemaDoc = doc;
        if (schemaDoc == null && xsdFilePath != null) {
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                DocumentBuilder builder = factory.newDocumentBuilder();
                schemaDoc = builder.parse(new File(xsdFilePath));
            } catch (Exception e) {
                logger.debug("Could not parse schema file to check version: {}", e.getMessage());
                return false;
            }
        }

        if (schemaDoc == null) return false;
        Element root = schemaDoc.getDocumentElement();
        if (root == null) return false;

        // Check for vc:minVersion attribute
        String minVersion = root.getAttributeNS("http://www.w3.org/2007/XMLSchema-versioning", "minVersion");
        if ("1.1".equals(minVersion)) {
            return true;
        }

        // Also check without namespace (some schemas use it without proper namespace)
        minVersion = root.getAttribute("vc:minVersion");
        return "1.1".equals(minVersion);
    }

    /**
     * Simple LSInput implementation for resource resolution.
     */
    private static class LSInputImpl implements org.w3c.dom.ls.LSInput {
        private final String publicId;
        private final String systemId;
        private final File file;

        public LSInputImpl(String publicId, String systemId, File file) {
            this.publicId = publicId;
            this.systemId = systemId;
            this.file = file;
        }

        @Override
        public java.io.Reader getCharacterStream() {
            try {
                return new java.io.FileReader(file);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public void setCharacterStream(java.io.Reader characterStream) {}

        @Override
        public java.io.InputStream getByteStream() {
            try {
                return new java.io.FileInputStream(file);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public void setByteStream(java.io.InputStream byteStream) {}

        @Override
        public String getStringData() { return null; }

        @Override
        public void setStringData(String stringData) {}

        @Override
        public String getSystemId() { return systemId; }

        @Override
        public void setSystemId(String systemId) {}

        @Override
        public String getPublicId() { return publicId; }

        @Override
        public void setPublicId(String publicId) {}

        @Override
        public String getBaseURI() { return file.toURI().toString(); }

        @Override
        public void setBaseURI(String baseURI) {}

        @Override
        public String getEncoding() { return "UTF-8"; }

        @Override
        public void setEncoding(String encoding) {}

        @Override
        public boolean getCertifiedText() { return false; }

        @Override
        public void setCertifiedText(boolean certifiedText) {}
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

        // If the option is disabled or there's no type reference, set only element source code
        if (!includeTypeDefinitionsInSourceCode || typeName == null || typeName.isEmpty()) {
            extendedElem.setSourceCode(elementSourceCode);
            return;
        }

        // Check if this is a built-in XSD type (xs:string, xs:int, etc.)
        if (typeName.startsWith("xs:") || typeName.startsWith("xsd:")) {
            logger.debug("Skipping built-in type definition for: {}", typeName);
            extendedElem.setSourceCode(elementSourceCode);
            return;
        }

        // If we have an inline type definition, it's already included in the element source code
        if (typeDefinitionNode != null && typeDefinitionNode.getParentNode() == node) {
            logger.debug("Type definition is inline, already included in element source code");
            extendedElem.setSourceCode(elementSourceCode);
            return;
        }

        // Look up the global type definition
        String cleanTypeName = stripNamespace(typeName);
        Node globalTypeNode = complexTypeMap.get(cleanTypeName);
        if (globalTypeNode == null) {
            globalTypeNode = simpleTypeMap.get(cleanTypeName);
        }

        // If we found a global type definition, combine it with the element source code
        if (globalTypeNode != null) {
            String typeSourceCode = nodeToString(globalTypeNode);
            logger.debug("Including type definition for '{}' in combined source code", cleanTypeName);

            // Combine the element source code with the type definition using marker comments
            String combinedSource = "<!-- Element Definition -->\n" +
                    elementSourceCode +
                    "\n\n" +
                    "<!-- Referenced Type Definition: " + cleanTypeName + " -->\n" +
                    typeSourceCode;

            extendedElem.setSourceCode(combinedSource);
            extendedElem.setReferencedTypeCode(typeSourceCode);
            extendedElem.setReferencedTypeName(cleanTypeName);
        } else {
            logger.debug("No global type definition found for: {}", cleanTypeName);
            extendedElem.setSourceCode(elementSourceCode);
        }
    }
}