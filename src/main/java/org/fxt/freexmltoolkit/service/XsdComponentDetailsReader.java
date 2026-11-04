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

import static org.fxt.freexmltoolkit.service.XsdDomHelpers.getAttributeValue;
import static org.fxt.freexmltoolkit.service.XsdDomHelpers.getDirectChildElement;
import static org.fxt.freexmltoolkit.service.XsdDomHelpers.getDirectChildElements;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAppInfo;
import org.fxt.freexmltoolkit.domain.IdentityConstraint;
import org.fxt.freexmltoolkit.domain.OpenContent;
import org.fxt.freexmltoolkit.domain.TypeAlternative;
import org.fxt.freexmltoolkit.domain.Wildcard;
import org.fxt.freexmltoolkit.domain.XsdAssertion;
import org.fxt.freexmltoolkit.domain.XsdDocInfo;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement.DocumentationInfo;
import org.w3c.dom.Node;

/**
 * Reads the per-component details of a schema node into an {@link XsdExtendedElement} for
 * {@link XsdDocumentationService}: annotations (documentation, appinfo tags, example values), identity constraints,
 * XSD 1.1 assertions, type alternatives and open content, list/union types and wildcards.
 */
final class XsdComponentDetailsReader {

    private static final Logger logger = LogManager.getLogger(XsdComponentDetailsReader.class);
    private static final String ALTOVA_NS_URI = XsdAppInfo.ALTOVA_NS;
    private static final String FXT_EXT_NS_URI = XsdAppInfo.FXT_EXT_NS;

    private final XsdDocumentationService service;

    XsdComponentDetailsReader(XsdDocumentationService service) {
        this.service = service;
    }

    /**
     * Reads the example values declared by an {@code xs:appinfo}, accepting both the
     * FreeXmlToolkit namespace and the legacy Altova one.
     *
     * @param appInfoNode the appinfo element
     * @return the declared example values, empty when the appinfo declares none
     */
    private List<String> readExampleValues(Node appInfoNode) {
        List<String> values = new ArrayList<>();
        for (Node appInfoChild : getDirectChildElements(appInfoNode)) {
            if (!isExampleValuesNamespace(appInfoChild.getNamespaceURI())
                    || !"exampleValues".equals(appInfoChild.getLocalName())) {
                continue;
            }
            for (Node exampleNode : getDirectChildElements(appInfoChild)) {
                if (isExampleValuesNamespace(exampleNode.getNamespaceURI())
                        && "example".equals(exampleNode.getLocalName())) {
                    String value = getAttributeValue(exampleNode, "value");
                    if (value != null) {
                        values.add(value);
                    }
                }
            }
        }
        return values;
    }

    private static boolean isExampleValuesNamespace(String namespaceUri) {
        return ALTOVA_NS_URI.equals(namespaceUri) || FXT_EXT_NS_URI.equals(namespaceUri);
    }

    /**
     * Reads documentation and appinfo tags of one {@code xs:annotation} into the element.
     *
     * @param annotationNode the annotation to read, may be null
     * @param extendedElem   the element to populate
     * @return the {@code @markdown} flag stated by this annotation, or null when it says nothing.
     *         Applying it (including the element-beats-type rule) is the caller's job.
     */
    Boolean processAnnotations(Node annotationNode, XsdExtendedElement extendedElem) {
        if (annotationNode == null) {
            return null;
        }
        Boolean markdownFlag = null;

        // 1. Extract documentation
        for (Node docNode : getDirectChildElements(annotationNode, "documentation")) {
            String lang = getAttributeValue(docNode, "xml:lang", "default");
            // Normalize language to lowercase for case-insensitive comparison
            String normalizedLang = lang != null ? lang.toLowerCase() : "default";
            service.discoveredLanguages.add(normalizedLang); // Track discovered language for UI configuration
            if (!service.isLanguageIncluded(normalizedLang)) {
                continue; // De-selected in the generator options: never reaches any output format
            }
            extendedElem.addDocumentation(service.documentationMemo.computeIfAbsent(docNode,
                    n -> new DocumentationInfo(normalizedLang, n.getTextContent())));
        }

        // 2. Process AppInfo tags (for Javadoc and Altova examples)
        XsdDocInfo xsdDocInfo = extendedElem.getXsdDocInfo() != null ? extendedElem.getXsdDocInfo() : new XsdDocInfo();
        List<String> genericAppInfos = extendedElem.getGenericAppInfos() != null ? new ArrayList<>(extendedElem.getGenericAppInfos()) : new ArrayList<>();
        List<String> exampleValues = new ArrayList<>(extendedElem.getExampleValues()); // Mutable copy for example values

        for (Node appInfoNode : getDirectChildElements(annotationNode, "appinfo")) {
            String source = getAttributeValue(appInfoNode, "source");

            // Example values (fxt: or the legacy altova: convention) - independent of @source.
            List<String> declaredExamples = readExampleValues(appInfoNode);
            if (!declaredExamples.isEmpty()) {
                exampleValues.addAll(declaredExamples);
                continue;
            }

            // JavaDoc-style tags. The tag lives in @source; its value is the element's text
            // content, falling back to the remainder of @source for the legacy encoding
            // <xs:appinfo source="@since 4.0.0"/>.
            String tag = XsdAppInfo.tagOf(source);
            if (tag == null) {
                String generic = (source != null && !source.isBlank())
                        ? source
                        : appInfoNode.getTextContent();
                if (generic != null && !generic.isBlank()) {
                    genericAppInfos.add(generic);
                }
                continue;
            }

            String value = XsdAppInfo.valueOf(source, appInfoNode.getTextContent());
            switch (tag) {
                case "@since" -> xsdDocInfo.setSince(value);
                case "@see" -> xsdDocInfo.addSee(value);
                case "@deprecated" -> xsdDocInfo.setDeprecated(value);
                case "@markdown" -> {
                    // Recorded only; OFF and the element-beats-type rule are applied by the caller.
                    Boolean markdown = XsdAppInfo.parseBooleanFlag(value);
                    if (markdown != null) {
                        markdownFlag = markdown; // last explicit value within one annotation wins
                    }
                }
                default -> genericAppInfos.add(value.isEmpty() ? tag : tag + " " + value);
            }
        }

        // 3. Store collected information in the element
        if (xsdDocInfo.hasData()) {
            extendedElem.setXsdDocInfo(xsdDocInfo);
        }
        if (!genericAppInfos.isEmpty()) {
            extendedElem.setGenericAppInfos(genericAppInfos);
        }
        if (!exampleValues.isEmpty()) {
            extendedElem.setExampleValues(exampleValues);
        }
        return markdownFlag;
    }

    /**
     * Processes XSD 1.0 identity constraints (xs:key, xs:keyref, xs:unique) on an element.
     * Identity constraints define uniqueness and referential integrity rules within XML documents.
     *
     * @param elementNode  The element node that may contain identity constraints
     * @param extendedElem The extended element to populate with identity constraint information
     */
    void processIdentityConstraints(Node elementNode, XsdExtendedElement extendedElem) {
        if (elementNode == null) {
            return;
        }

        List<IdentityConstraint> identityConstraints = new ArrayList<>();

        if (logger.isDebugEnabled()) {
            logger.debug("Processing identity constraints for element: {}, node type: {}",
                    extendedElem.getElementName(), elementNode.getLocalName());
        }

        for (Node child : getDirectChildElements(elementNode)) {
            String localName = child.getLocalName();
            if (logger.isDebugEnabled()) {
                logger.debug("Checking child node: {}", localName);
            }
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
                if (logger.isDebugEnabled()) {
                    logger.debug("Found identity constraint: {} of type {} on element {}",
                            constraint.getName(), type, extendedElem.getElementName());
                }
            }
        }

        if (!identityConstraints.isEmpty()) {
            extendedElem.setIdentityConstraints(identityConstraints);
        }
    }

    /**
     * Copies the identity-constraint result from a previously-processed donor entry onto
     * a new entry, instead of re-parsing the (XPath-independent) constraints from the DOM.
     * Mirrors exactly the single field {@link #processIdentityConstraints} sets.
     * The setter performs a defensive copy of the list, so the entries do not share state.
     *
     * @param from the donor element whose identity constraints were already parsed
     * @param to   the new element to receive a copy of the constraints
     */
    void copyIdentityConstraints(XsdExtendedElement from, XsdExtendedElement to) {
        List<IdentityConstraint> constraints = from.getIdentityConstraints();
        if (constraints != null && !constraints.isEmpty()) {
            // setIdentityConstraints() makes a fresh ArrayList; IdentityConstraint instances
            // are immutable metadata describing the definition, so sharing them is safe.
            to.setIdentityConstraints(constraints);
        }
        // else: the donor node has no constraints — nothing to copy; the target keeps its
        // default empty list (the common case, since most elements carry no key/keyref/unique).
    }

    /**
     * Processes XSD 1.1 assertions (xs:assert on complexType, xs:assertion on simpleType).
     * Assertions are XPath 2.0 boolean expressions that must evaluate to true for the instance to be valid.
     *
     * @param typeNode     The type node (complexType or simpleType) that may contain assertions
     * @param extendedElem The extended element to populate with assertion information
     */
    void processAssertions(Node typeNode, XsdExtendedElement extendedElem) {
        if (typeNode == null) {
            return;
        }

        // Added to what an earlier call found: a simple type's xs:assertion and a complex type's
        // xs:assert can both apply to one element.
        List<XsdAssertion> assertions = new ArrayList<>(extendedElem.getAssertions() == null
                ? List.of() : extendedElem.getAssertions());
        collectAssertions(typeNode, extendedElem, assertions, new HashSet<>());

        if (!assertions.isEmpty()) {
            extendedElem.setAssertions(assertions);
        }
    }

    /**
     * Collects the assertions of a type: its own (also inside complexContent / simpleContent
     * extension and restriction), then those of the named base types it derives from. Cycle-safe.
     *
     * @param node    a complexType, simpleType, content model or derivation node
     * @param visited the nodes already looked at
     */
    private void collectAssertions(Node node, XsdExtendedElement extendedElem, List<XsdAssertion> assertions,
                                   Set<Node> visited) {
        if (node == null || !visited.add(node)) {
            return;
        }
        List<Node> derivations = new ArrayList<>();
        for (Node child : getDirectChildElements(node)) {
            String localName = child.getLocalName();
            XsdAssertion.Type type = null;

            // Determine the type of assertion
            if ("assert".equals(localName)) {
                type = XsdAssertion.Type.ASSERT;  // xs:assert on complexType
            } else if ("assertion".equals(localName)) {
                type = XsdAssertion.Type.ASSERTION;  // xs:assertion on simpleType restriction
            } else if ("complexContent".equals(localName) || "simpleContent".equals(localName)
                    || "extension".equals(localName) || "restriction".equals(localName)) {
                derivations.add(child);
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

                // The restriction of a simpleContent type is reached from the element's
                // restriction and from its complex type: list each assertion once.
                boolean known = assertions.stream().anyMatch(a -> a.getType() == assertion.getType()
                        && Objects.equals(a.getTest(), assertion.getTest()));
                if (!known) {
                    assertions.add(assertion);
                    logger.debug("Found {} assertion with test: {} on element {}",
                            type, assertion.getTest(), extendedElem.getElementName());
                }
            }
        }

        for (Node derivation : derivations) {
            collectAssertions(derivation, extendedElem, assertions, visited);
        }
        // Inherited assertions after the type's own ones
        String localName = node.getLocalName();
        if ("extension".equals(localName) || "restriction".equals(localName)) {
            String base = getAttributeValue(node, "base");
            String basePrefix = base == null || !base.contains(":") ? null : base.substring(0, base.indexOf(':'));
            boolean builtIn = javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(node.lookupNamespaceURI(basePrefix));
            if (base != null && !base.isBlank() && !builtIn) {
                String baseName = base.contains(":") ? base.substring(base.indexOf(':') + 1) : base;
                Node baseType = service.complexTypeMap.get(baseName);
                if (baseType == null) {
                    baseType = service.simpleTypeMap.get(baseName);
                }
                collectAssertions(baseType, extendedElem, assertions, visited);
            }
        }
    }

    /**
     * Processes XSD 1.1 type alternatives (xs:alternative) on an element.
     * Type alternatives provide conditional type assignment based on XPath 2.0 expressions.
     *
     * @param elementNode  The element node that may contain type alternatives
     * @param extendedElem The extended element to populate with type alternative information
     */
    void processTypeAlternatives(Node elementNode, XsdExtendedElement extendedElem) {
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
    void processListAndUnionTypes(Node typeDefinitionNode, XsdExtendedElement extendedElem) {
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
    void processOpenContent(Node typeNode, XsdExtendedElement extendedElem) {
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
    OpenContent parseOpenContentNode(Node openContentNode, boolean isDefault) {
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
    void processWildcards(Node contextNode, XsdExtendedElement extendedElem) {
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
}
