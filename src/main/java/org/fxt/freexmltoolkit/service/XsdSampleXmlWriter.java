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

import static org.fxt.freexmltoolkit.service.XsdDomHelpers.escapeXml;
import static org.fxt.freexmltoolkit.service.XsdDomHelpers.getAttributeValue;
import static org.fxt.freexmltoolkit.service.XsdSampleXmlSupport.MAX_REQUIRED_REPETITIONS;
import static org.fxt.freexmltoolkit.service.XsdSampleXmlSupport.appendWildcardSample;
import static org.fxt.freexmltoolkit.service.XsdSampleXmlSupport.appendXsiType;
import static org.fxt.freexmltoolkit.service.XsdSampleXmlSupport.attributeName;
import static org.fxt.freexmltoolkit.service.XsdSampleXmlSupport.completeOptions;
import static org.fxt.freexmltoolkit.service.XsdSampleXmlSupport.declareEmissionNamespace;
import static org.fxt.freexmltoolkit.service.XsdSampleXmlSupport.elementDefaultNamespace;
import static org.fxt.freexmltoolkit.service.XsdSampleXmlSupport.elementRepeatCount;
import static org.fxt.freexmltoolkit.service.XsdSampleXmlSupport.fixedOrDefaultValue;
import static org.fxt.freexmltoolkit.service.XsdSampleXmlSupport.hasCompleteContent;
import static org.fxt.freexmltoolkit.service.XsdSampleXmlSupport.idForEmittedReference;
import static org.fxt.freexmltoolkit.service.XsdSampleXmlSupport.isWildcardPlaceholder;
import static org.fxt.freexmltoolkit.service.XsdSampleXmlSupport.limitRepeatedOccurrences;
import static org.fxt.freexmltoolkit.service.XsdSampleXmlSupport.qualifiedElementName;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.random.RandomGenerator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.w3c.dom.Node;

/**
 * Writes one plain sample XML document from the element map that {@link XsdDocumentationService} expanded: root
 * element with schema reference and namespace declarations, then the content, with random choices and repetitions,
 * identity constraint values and the output size limit. One instance per document; it holds the document's state.
 */
final class XsdSampleXmlWriter {

    private static final Logger logger = LogManager.getLogger(XsdSampleXmlWriter.class);

    private final XsdDocumentationData xsdDocumentationData;
    private final XsdSampleDataGenerator xsdSampleDataGenerator;
    private final RandomGenerator random;
    private final String xsdFilePath;
    private final Path sampleOutputDirectory;

    /** Default namespace in scope at the element a plain sample is writing. */
    private String emissionDefaultNamespace = "";
    /** Character limit while building a plain sample; -1 outside generation. */
    private long outputCharLimit = -1;
    /** XPaths whose repetitions beyond minOccurs the current plain sample already emitted. */
    private final Set<String> repeatedXpaths = new HashSet<>();
    /** Completeness of element map entries for the current sample document, see {@link XsdSampleXmlSupport#hasCompleteContent}. */
    private final Map<String, Boolean> completeContent = new HashMap<>();

    /**
     * @param xsdDocumentationData   the expanded schema
     * @param xsdSampleDataGenerator the value source (already reseeded for this document)
     * @param random                 the choice and repetition source (already reseeded for this document)
     * @param xsdFilePath            the schema file the sample references
     * @param sampleOutputDirectory  the folder the sample will be written to, or {@code null}
     */
    XsdSampleXmlWriter(XsdDocumentationData xsdDocumentationData, XsdSampleDataGenerator xsdSampleDataGenerator,
                       RandomGenerator random, String xsdFilePath, Path sampleOutputDirectory) {
        this.xsdDocumentationData = xsdDocumentationData;
        this.xsdSampleDataGenerator = xsdSampleDataGenerator;
        this.random = random;
        this.xsdFilePath = xsdFilePath;
        this.sampleOutputDirectory = sampleOutputDirectory;
    }

    /**
     * Writes the sample document for a root element of the expanded element map.
     *
     * @param rootElement    the global element the document is generated for
     * @param mandatoryOnly  emit only required elements/attributes when {@code true}
     * @param maxOccurrences cap on repeated elements
     * @return the sample XML, or an XML comment when a limit was exceeded
     */
    String write(XsdExtendedElement rootElement, boolean mandatoryOnly, int maxOccurrences) {
        StringBuilder xmlBuilder = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xsdSampleDataGenerator.startDocument();
        repeatedXpaths.clear();
        completeContent.clear();
        String documentNamespace = xsdDocumentationData.getTargetNamespace();
        emissionDefaultNamespace = documentNamespace == null || documentNamespace.isBlank() ? "" : documentNamespace;

        // Add schema reference (supports namespaced and no-namespace schemas). Portable by
        // design: the bare file name, or a path relative to a known output folder — never
        // an absolute file: URI of this machine (see SampleSchemaLocation).
        String targetNamespace = xsdDocumentationData.getTargetNamespace();
        String schemaLocationUri = SampleSchemaLocation.forSample(xsdFilePath, sampleOutputDirectory);

        String rootName = rootElement.getElementName();
        xmlBuilder.append("<").append(rootName)
                 .append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
        appendXsiType(xmlBuilder, rootElement);

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

        // Collect and declare all used namespaces from imported/included schemas
        Map<String, String> usedNamespaces = collectUsedNamespaces(rootElement, mandatoryOnly);
        for (Map.Entry<String, String> ns : usedNamespaces.entrySet()) {
            String prefix = ns.getKey();
            String uri = ns.getValue();
            if (prefix != null && !prefix.isEmpty() && uri != null && !uri.isEmpty()) {
                xmlBuilder.append(" xmlns:").append(prefix).append("=\"").append(uri).append("\"");
            }
        }

        // Add root element attributes (those children starting with @)
        List<XsdExtendedElement> rootAttributes = rootElement.getChildren().stream()
                .map(xsdDocumentationData.getExtendedXsdElementMap()::get)
                .filter(Objects::nonNull)
                .filter(e -> e.getElementName().startsWith("@"))
                .toList();

        for (XsdExtendedElement attr : rootAttributes) {
            // Include attribute if mandatory, or if it has a fixed/default value
            String fixedOrDefault = fixedOrDefaultValue(attr);
            if (mandatoryOnly && !attr.isMandatory() && fixedOrDefault == null
                    && !idForEmittedReference(attr, rootAttributes, xsdSampleDataGenerator)) {
                continue;
            }
            String attrName = attributeName(attr);
            String attrValue = (fixedOrDefault != null)
                    ? fixedOrDefault
                    : occurrenceValue(attr, attr.getDisplaySampleData() != null ? attr.getDisplaySampleData() : "");
            xmlBuilder.append(" ").append(attrName).append("=\"").append(escapeXml(attrValue)).append("\"");
        }

        // Build the content without the root element tags (only child elements, not attributes)
        List<XsdExtendedElement> rootChildElements = rootElement.getChildren().stream()
                .map(xsdDocumentationData.getExtendedXsdElementMap()::get)
                .filter(Objects::nonNull)
                .filter(e -> !e.getElementName().startsWith("@"))
                .toList();

        if (rootChildElements.isEmpty()) {
            // Simple or empty content: the root carries its own value and no indentation whitespace
            String rootValue = textValue(rootElement);
            if (rootValue.isEmpty()) {
                xmlBuilder.append("/>\n");
            } else {
                xmlBuilder.append(">").append(escapeXml(rootValue)).append("</").append(rootName).append(">\n");
            }
            return xsdSampleDataGenerator.resolveDanglingReferences(xmlBuilder.toString());
        }

        xmlBuilder.append(">\n");

        // Create and populate identity constraint tracker for unique value generation
        IdentityConstraintTracker constraintTracker = new IdentityConstraintTracker();
        constraintTracker.setRandom(xsdSampleDataGenerator.random());
        constraintTracker.scanConstraints(xsdDocumentationData.getExtendedXsdElementMap());

        outputCharLimit = SampleXmlLimits.maxOutputChars();
        try {
            for (XsdExtendedElement child : rootChildElements) {
                buildXmlElementContent(xmlBuilder, child, mandatoryOnly, maxOccurrences, 1, constraintTracker);
            }
        } catch (SampleXmlLimits.LimitExceededException e) {
            return e.toXmlComment();
        } finally {
            outputCharLimit = -1;
        }

        xmlBuilder.append("</").append(rootName).append(">\n");

        return xsdSampleDataGenerator.resolveDanglingReferences(xmlBuilder.toString());
    }

    /**
     * The text a plain sample emits for an element: its sample data, generated anew per occurrence for an ID or IDREF
     * (the element map holds one value per schema node). Fixed and default values stay.
     */
    private String textValue(XsdExtendedElement element) {
        String value = element.getDisplaySampleData() != null ? element.getDisplaySampleData() : "";
        if (value.isEmpty() || fixedOrDefaultValue(element) != null) {
            return value;
        }
        return occurrenceValue(element, value);
    }

    /** A fresh ID or an IDREF to an emitted ID when {@code node} has such a type, else {@code value}. */
    private String occurrenceValue(XsdExtendedElement node, String value) {
        return switch (xsdSampleDataGenerator.identityKind(node)) {
            case ID -> xsdSampleDataGenerator.nextId();
            case IDREF -> xsdSampleDataGenerator.nextIdReference();
            case NONE -> value;
        };
    }

    /**
     * Collects all namespaces used by elements in the tree.
     * This is needed to declare namespace prefixes for elements from imported schemas.
     * @param element The root element to start from
     * @param mandatoryOnly Whether to only include mandatory elements
     * @return Map of namespace prefix to namespace URI
     */
    private Map<String, String> collectUsedNamespaces(XsdExtendedElement element, boolean mandatoryOnly) {
        Map<String, String> namespaces = new HashMap<>();
        collectNamespacesRecursive(element, mandatoryOnly, namespaces, new HashSet<>());
        return namespaces;
    }

    /**
     * Recursively collects namespaces from an element and its children.
     * @param element The current element
     * @param mandatoryOnly Whether to only include mandatory elements
     * @param namespaces The map to collect namespaces into
     * @param visited Set of visited element XPaths to avoid infinite loops
     */
    private void collectNamespacesRecursive(XsdExtendedElement element, boolean mandatoryOnly,
                                           Map<String, String> namespaces, Set<String> visited) {
        String elementName = element == null ? null : element.getElementName();
        boolean attribute = elementName != null && elementName.startsWith("@");
        // Optional attributes with a fixed or default value are emitted in mandatory-only mode too
        if (elementName == null || (!attribute && mandatoryOnly && !element.isMandatory())) {
            return;
        }

        String elementXpath = element.getCurrentXpath();
        if (elementXpath != null && visited.contains(elementXpath)) {
            return; // Avoid circular references
        }
        if (elementXpath != null) {
            visited.add(elementXpath);
        }

        declareEmissionNamespace(namespaces, element);
        if (attribute) {
            return;
        }

        // Process children recursively
        List<XsdExtendedElement> childElements = element.getChildren().stream()
                .map(xsdDocumentationData.getExtendedXsdElementMap()::get)
                .filter(Objects::nonNull)
                .toList();

        for (XsdExtendedElement child : childElements) {
            collectNamespacesRecursive(child, mandatoryOnly, namespaces, visited);
        }
    }

    /**
     * Builds XML element content without the root element tags (for schema-referenced XML).
     */
    private void buildXmlElementContent(StringBuilder sb, XsdExtendedElement element, boolean mandatoryOnly, int maxOccurrences, int indentLevel, IdentityConstraintTracker constraintTracker) {
        if (Thread.currentThread().isInterrupted()) {
            throw new java.util.concurrent.CancellationException("XML generation cancelled");
        }
        if (outputCharLimit >= 0 && sb.length() > outputCharLimit) {
            throw SampleXmlLimits.outputLimitExceeded(outputCharLimit);
        }
        if (element == null || (mandatoryOnly && !element.isMandatory())) {
            return;
        }
        String elementName = element.getElementName();
        if (elementName == null || elementName.startsWith("@")) {
            return; // Attributes are handled by their parent
        }
        if (!element.isMandatory()
                && !hasCompleteContent(element, xsdDocumentationData.getExtendedXsdElementMap(), completeContent)) {
            return; // optional content a sample cannot complete (a cut recursion, a strict wildcard) is left out
        }
        if (!element.isMandatory() && constraintTracker != null
                && constraintTracker.isKeylessSelection(element.getCurrentXpath())) {
            return; // a key selects it, but it cannot carry the key's field
        }

        if (isWildcardPlaceholder(element)) {
            int repeatCount = limitRepeatedOccurrences(element, elementRepeatCount(element, maxOccurrences),
                    repeatedXpaths);
            for (int i = 0; i < repeatCount; i++) {
                appendWildcardSample(sb, element, "\t".repeat(indentLevel), emissionDefaultNamespace);
            }
            return;
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
            // A model group repeats as a whole, like an element (a sequence with minOccurs="2")
            int groupRepeats = limitRepeatedOccurrences(element, elementRepeatCount(element, maxOccurrences),
                    repeatedXpaths);
            for (int repetition = 0; repetition < groupRepeats; repetition++) {
                for (XsdExtendedElement child : containerChildren) {
                    buildXmlElementContent(sb, child, mandatoryOnly, maxOccurrences, indentLevel, constraintTracker);
                }
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
                // Get minOccurs and maxOccurs from the CHOICE element, or from the group reference that holds it
                Node choiceNode = element.getCardinalityNode() != null
                        ? element.getCardinalityNode() : element.getCurrentNode();
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
                    repeatCount = Math.min(minOccurs, MAX_REQUIRED_REPETITIONS);
                } else {
                    int effectiveMax = Math.min(choiceMaxOccurs, maxOccurrences);
                    if (minOccurs >= effectiveMax) {
                        repeatCount = Math.min(minOccurs, MAX_REQUIRED_REPETITIONS); // at least minOccurs
                    } else {
                        repeatCount = minOccurs + random.nextInt(effectiveMax - minOccurs + 1);
                    }
                }

                // Generate the appropriate number of selections from the options a sample can complete
                List<XsdExtendedElement> selectable = completeOptions(choiceOptions,
                        xsdDocumentationData.getExtendedXsdElementMap(), completeContent);
                for (int i = 0; i < repeatCount; i++) {
                    XsdExtendedElement selected = selectable.get(random.nextInt(selectable.size()));
                    buildXmlElementContent(sb, selected, mandatoryOnly, maxOccurrences, indentLevel, constraintTracker);
                }
            }
            return;
        }

        int repeatCount = limitRepeatedOccurrences(element, elementRepeatCount(element, maxOccurrences),
                repeatedXpaths);

        for (int i = 0; i < repeatCount; i++) {
            String indent = "\t".repeat(indentLevel);

            String qualifiedName = qualifiedElementName(element);
            String elementDefaultNamespace = elementDefaultNamespace(element, emissionDefaultNamespace);

            sb.append(indent).append("<").append(qualifiedName);
            if (!elementDefaultNamespace.equals(emissionDefaultNamespace)) {
                sb.append(" xmlns=\"").append(escapeXml(elementDefaultNamespace)).append('"');
            }
            appendXsiType(sb, element);

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
                String fixedOrDefault = fixedOrDefaultValue(attr);
                if (mandatoryOnly && !attr.isMandatory() && fixedOrDefault == null
                        && !idForEmittedReference(attr, attributes, xsdSampleDataGenerator)) {
                    continue;
                }
                String attrName = attributeName(attr);
                String attrValue = (fixedOrDefault != null)
                        ? fixedOrDefault
                        : occurrenceValue(attr, attr.getDisplaySampleData() != null ? attr.getDisplaySampleData() : "");

                // Apply constraint tracking for attributes
                String attrXpath = attr.getCurrentXpath() != null ? attr.getCurrentXpath()
                        : element.getCurrentXpath() + "/@" + attrName;
                if (constraintTracker != null && fixedOrDefault == null
                        && (constraintTracker.isConstrainedField(attrXpath) || constraintTracker.isKeyrefField(attrXpath))) {
                    attrValue = constraintTracker.getUniqueValue(attrXpath, attrValue, attr);
                }

                sb.append(" ").append(attrName).append("=\"").append(escapeXml(attrValue)).append("\"");
            }

            String sampleData = textValue(element);

            // Apply constraint tracking for element text content
            if (constraintTracker != null && !sampleData.isEmpty()) {
                String elemXpath = element.getCurrentXpath();
                if (constraintTracker.isConstrainedField(elemXpath) || constraintTracker.isKeyrefField(elemXpath)) {
                    sampleData = constraintTracker.getUniqueValue(elemXpath, sampleData, element);
                }
            }

            if (childElements.isEmpty() && sampleData.isEmpty()) {
                sb.append("/>\n"); // Self-closing tag
            } else {
                int startTagEnd = sb.length();
                sb.append(">");
                sb.append(escapeXml(sampleData));

                if (!childElements.isEmpty()) {
                    int contentStart = sb.append("\n").length();
                    // Process children, handling CHOICE elements with random selection
                    String outerDefaultNamespace = emissionDefaultNamespace;
                    emissionDefaultNamespace = elementDefaultNamespace;
                    try {
                        processChildElementsForGeneration(sb, childElements, mandatoryOnly, maxOccurrences,
                                indentLevel + 1, constraintTracker);
                    } finally {
                        emissionDefaultNamespace = outerDefaultNamespace;
                    }
                    if (sb.length() == contentStart && sampleData.isEmpty()) {
                        // Only structural children that produced nothing: empty content allows no whitespace
                        sb.setLength(startTagEnd);
                        sb.append("/>\n");
                        continue;
                    }
                    sb.append(indent);
                }
                sb.append("</").append(qualifiedName).append(">\n");
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
                                                   boolean mandatoryOnly, int maxOccurrences, int indentLevel, IdentityConstraintTracker constraintTracker) {
        for (XsdExtendedElement childElement : childElements) {
            String elementName = childElement.getElementName();
            if (elementName == null) {
                continue;
            }
            if (!childElement.isMandatory() && !hasCompleteContent(childElement,
                    xsdDocumentationData.getExtendedXsdElementMap(), completeContent)) {
                continue; // optional content a sample cannot complete is left out
            }

            // Check if this child is a SEQUENCE or ALL container (structural, not actual XML elements)
            if (elementName.startsWith("SEQUENCE") || elementName.startsWith("ALL")) {
                // An optional container is left out in mandatory-only mode, as buildXmlElementContent and the
                // namespace collection do; emitting it declared no namespace for its foreign elements
                if (mandatoryOnly && !childElement.isMandatory()) {
                    continue;
                }
                // SEQUENCE and ALL are structural containers - just output all their children
                // without outputting the container element itself
                List<XsdExtendedElement> containerChildren = childElement.getChildren().stream()
                        .map(xsdDocumentationData.getExtendedXsdElementMap()::get)
                        .filter(Objects::nonNull)
                        .filter(e -> !e.getElementName().startsWith("@"))
                        .toList();

                if (!containerChildren.isEmpty()) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Processing {} container with {} children", elementName, containerChildren.size());
                    }
                    // Recursively process children (they may contain nested CHOICE/SEQUENCE); a model group repeats
                    // as a whole, like an element
                    int groupRepeats = limitRepeatedOccurrences(childElement,
                            elementRepeatCount(childElement, maxOccurrences), repeatedXpaths);
                    for (int repetition = 0; repetition < groupRepeats; repetition++) {
                        processChildElementsForGeneration(sb, containerChildren, mandatoryOnly, maxOccurrences,
                                indentLevel, constraintTracker);
                    }
                }
            // Check if this child is a CHOICE container
            } else if (elementName.startsWith("CHOICE")) {
                // Get the choice's cardinality, or the cardinality of the group reference that holds it
                Node choiceNode = childElement.getCardinalityNode() != null
                        ? childElement.getCardinalityNode() : childElement.getCurrentNode();
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
                    repeatCount = Math.min(minOccurs, MAX_REQUIRED_REPETITIONS);
                } else {
                    // In non-mandatory mode, generate between minOccurs and maxOccurs
                    // Use a random value in that range, but cap at maxOccurrences
                    int effectiveMax = Math.min(maxOccurs, maxOccurrences);
                    if (minOccurs >= effectiveMax) {
                        repeatCount = Math.min(minOccurs, MAX_REQUIRED_REPETITIONS); // at least minOccurs
                    } else {
                        repeatCount = minOccurs + random.nextInt(effectiveMax - minOccurs + 1);
                    }
                }

                // Randomly select ONE element from the options a sample can complete
                choiceOptions = completeOptions(choiceOptions, xsdDocumentationData.getExtendedXsdElementMap(),
                        completeContent);
                XsdExtendedElement selectedOption = choiceOptions.get(random.nextInt(choiceOptions.size()));
                logger.debug("Selected element '{}' from CHOICE (1 of {} options)",
                        selectedOption.getElementName(), choiceOptions.size());

                // Generate XML for the selected option (may repeat if choice has maxOccurs > 1)
                for (int i = 0; i < repeatCount; i++) {
                    // For repeated choices, re-select to potentially get different options
                    if (i > 0) {
                        selectedOption = choiceOptions.get(random.nextInt(choiceOptions.size()));
                    }
                    buildXmlElementContent(sb, selectedOption, mandatoryOnly, maxOccurrences, indentLevel, constraintTracker);
                }
            } else {
                // Not a container element, process normally
                buildXmlElementContent(sb, childElement, mandatoryOnly, maxOccurrences, indentLevel, constraintTracker);
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
        // External namespace references (like ds:Signature) should never be considered empty containers
        // They reference elements defined in imported schemas and should be generated as self-closing tags
        if (element.isExternalNamespaceReference()) {
            return false;
        }

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
            if (childName == null) {
                continue;
            }

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
}
