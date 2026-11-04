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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Static rules shared by the plain sample writer ({@link XsdSampleXmlWriter}) and the profiled generator
 * ({@link ProfiledXmlGeneratorService}): root element selection, occurrence counts, completeness of sample content,
 * emitted names and namespaces, and the element written for a wildcard.
 */
final class XsdSampleXmlSupport {

    private XsdSampleXmlSupport() {
    }

    /** Element type of a wildcard placeholder in the element map of a sample expansion. */
    static final String WILDCARD_TYPE = "(wildcard)";
    /** Namespace of the element a sample writes for {@code ##any} and {@code ##other} wildcards. */
    static final String WILDCARD_SAMPLE_NAMESPACE = "urn:fxt:sample:extension";

    /** Whether an element map entry stands for an element wildcard of a sample expansion. */
    static boolean isWildcardPlaceholder(XsdExtendedElement element) {
        return WILDCARD_TYPE.equals(element.getElementType()) && element.getCurrentNode() != null
                && "any".equals(element.getCurrentNode().getLocalName());
    }

    /**
     * The namespace of the element a sample writes for a wildcard: the sample namespace for {@code ##any} and
     * {@code ##other}, none ({@code ""}) for {@code ##local}, the declaring document's target namespace for
     * {@code ##targetNamespace}, else the first listed namespace; {@code null} for {@code processContents="strict"},
     * which needs a declaration.
     */
    static String wildcardSampleNamespace(Node wildcard) {
        if (!(wildcard instanceof Element any)) {
            return null;
        }
        String processContents = any.hasAttribute("processContents") ? any.getAttribute("processContents") : "strict";
        if ("strict".equals(processContents.trim())) {
            return null;
        }
        String constraint = any.hasAttribute("namespace") ? any.getAttribute("namespace").trim() : "##any";
        for (String token : constraint.split("\\s+")) {
            switch (token) {
                case "##any", "##other" -> {
                    return WILDCARD_SAMPLE_NAMESPACE;
                }
                case "##local" -> {
                    return "";
                }
                case "##targetNamespace" -> {
                    return any.getOwnerDocument().getDocumentElement().getAttribute("targetNamespace");
                }
                default -> {
                    if (!token.isEmpty()) {
                        return token;
                    }
                }
            }
        }
        return WILDCARD_SAMPLE_NAMESPACE;
    }

    /**
     * Writes the element a sample emits for a wildcard placeholder, declaring its namespace on the element itself.
     *
     * @param defaultNamespace the default namespace in scope
     */
    static void appendWildcardSample(StringBuilder sb, XsdExtendedElement wildcard, String indent,
                                     String defaultNamespace) {
        String namespace = wildcardSampleNamespace(wildcard.getCurrentNode());
        String name = "sample";
        if (wildcard.getReferencedTypeName() != null) {
            // A strict wildcard: the expansion chose a global element the validator can look up
            name = wildcard.getReferencedTypeName();
            namespace = wildcard.getSourceNamespace() == null ? "" : wildcard.getSourceNamespace();
        }
        if (namespace == null) {
            return;
        }
        sb.append(indent);
        if (namespace.equals(defaultNamespace)) {
            sb.append('<').append(name).append("/>\n");
        } else if (namespace.isEmpty()) {
            sb.append('<').append(name).append(" xmlns=\"\"/>\n");
        } else {
            sb.append("<ext:").append(name).append(" xmlns:ext=\"")
                    .append(namespace.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;"))
                    .append("\"/>\n");
        }
    }

    /**
     * The root generated when none is chosen: the first non-abstract global element of the processed schema, the first
     * one if all are abstract, or {@code null} if there is none.
     *
     * @param elementMap the XPath-keyed element map of a processed schema
     * @return the default root element, or {@code null}
     */
    static XsdExtendedElement findDefaultRootElement(Map<String, XsdExtendedElement> elementMap) {
        List<XsdExtendedElement> roots = findRootElements(elementMap);
        return roots.stream()
                .filter(e -> !isAbstractRoot(e))
                .findFirst()
                .orElse(roots.isEmpty() ? null : roots.getFirst());
    }

    /** Whether a root of a processed schema is an abstract global element. */
    static boolean isAbstractRoot(XsdExtendedElement root) {
        return root.getCurrentNode() instanceof org.w3c.dom.Element element
                && "true".equals(element.getAttribute("abstract"));
    }

    /**
     * Global elements of the processed schema, sorted by document order.
     *
     * @param elementMap the XPath-keyed element map of a processed schema
     * @return the root elements
     */
    static List<XsdExtendedElement> findRootElements(Map<String, XsdExtendedElement> elementMap) {
        return elementMap.values().stream()
                .filter(e -> e.getParentXpath() == null || e.getParentXpath().equals("/"))
                .sorted(Comparator.comparing(XsdExtendedElement::getCounter))
                .toList();
    }

    /**
     * Looks up a global element by its local name.
     *
     * @param elementMap      the XPath-keyed element map of a processed schema
     * @param rootElementName the local name of the global element
     * @return the root element
     * @throws IllegalArgumentException if there is no global element with that name
     */
    static XsdExtendedElement findRootElement(Map<String, XsdExtendedElement> elementMap, String rootElementName) {
        return findRootElements(elementMap).stream()
                .filter(e -> e.getElementName().equals(rootElementName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No global element named '" + rootElementName + "'"));
    }

    /**
     * Whether a sample can give an element map entry valid required content: no required particle below it was cut
     * from the sample expansion (JATS {@code fn} holding only {@code p} inside a {@code p}, datajud's strict
     * {@code xs:any}). A choice is complete when one option is; an optional particle never makes its parent
     * incomplete.
     *
     * @param memo completeness per XPath for the current document
     */
    static boolean hasCompleteContent(XsdExtendedElement entry, Map<String, XsdExtendedElement> map,
                                      Map<String, Boolean> memo) {
        Boolean known = memo.get(entry.getCurrentXpath());
        if (known != null) {
            return known;
        }
        boolean complete = !entry.isSampleContentIncomplete();
        if (complete) {
            List<XsdExtendedElement> particles = entry.getChildren().stream()
                    .map(map::get)
                    .filter(Objects::nonNull)
                    .filter(e -> e.getElementName() != null && !e.getElementName().startsWith("@"))
                    .toList();
            boolean choice = entry.getElementName() != null && entry.getElementName().startsWith("CHOICE");
            complete = choice
                    ? particles.stream().anyMatch(option -> !option.isMandatory()
                            || hasCompleteContent(option, map, memo))
                    : particles.stream().allMatch(child -> !child.isMandatory()
                            || hasCompleteContent(child, map, memo));
        }
        memo.put(entry.getCurrentXpath(), complete);
        return complete;
    }

    /**
     * The options of a choice a sample picks from: those whose content a sample can complete, or all of them when none
     * can (JATS {@code statement-model} offers {@code p} or a nested {@code statement}, whose expansion is cut).
     */
    static List<XsdExtendedElement> completeOptions(List<XsdExtendedElement> options,
                                                    Map<String, XsdExtendedElement> map, Map<String, Boolean> memo) {
        List<XsdExtendedElement> complete = options.stream()
                .filter(option -> !option.isMandatory() || hasCompleteContent(option, map, memo))
                .toList();
        return complete.isEmpty() ? options : complete;
    }

    /**
     * Repetitions beyond {@code minOccurs} only decorate a sample: they are emitted the first time an XPath is emitted
     * in a document, later emissions (inside a repeated ancestor) get {@code max(minOccurs, 1)}. UBL 2.1 references
     * almost every element with {@code maxOccurs="unbounded"}, so repeating every level doubled the document per level.
     *
     * @param repeatedXpaths the XPaths of the current document that already emitted their repetitions
     */
    static int limitRepeatedOccurrences(XsdExtendedElement element, int repeatCount, Set<String> repeatedXpaths) {
        Node bounds = element.getCardinalityNode() != null ? element.getCardinalityNode() : element.getCurrentNode();
        int required = Math.max(1, Math.min(occurs(bounds, "minOccurs"), MAX_REQUIRED_REPETITIONS));
        if (repeatCount <= required || repeatedXpaths.add(element.getCurrentXpath())) {
            return repeatCount;
        }
        return required;
    }

    /**
     * The fixed or default value of an element or attribute, or {@code null}: a reference's own {@code fixed} or
     * {@code default} wins over the referenced declaration's (INSPIRE
     * {@code <attribute ref="xlink:type" fixed="simple"/>}).
     */
    static String fixedOrDefaultValue(XsdExtendedElement node) {
        for (Node holder : new Node[]{node.getCardinalityNode(), node.getCurrentNode()}) {
            if (holder instanceof Element declaration) {
                if (declaration.hasAttribute("fixed")) {
                    return declaration.getAttribute("fixed");
                }
                if (declaration.hasAttribute("default")) {
                    return declaration.getAttribute("default");
                }
            }
        }
        return null;
    }

    /** The name a sample writes for an element: prefixed when its namespace is not the default namespace. */
    static String qualifiedElementName(XsdExtendedElement element) {
        String prefix = element.getEmitNamespace() != null
                ? element.getEmitPrefix() : element.getSourceNamespacePrefix();
        String name = element.getElementName();
        return prefix != null && !prefix.isEmpty() ? prefix + ":" + name : name;
    }

    /**
     * The default namespace in scope inside an element: an unprefixed element of a known namespace makes its namespace
     * the default (written as {@code xmlns="…"} where it differs from the inherited one, {@code xmlns=""} for an
     * unqualified local element); a prefixed element keeps the inherited default.
     */
    static String elementDefaultNamespace(XsdExtendedElement element, String inherited) {
        if (element.getEmitNamespace() == null || element.getEmitPrefix() != null) {
            return inherited;
        }
        return element.getEmitNamespace();
    }

    /** The name a sample writes for an attribute: prefixed when qualified ({@code xlink:href}, {@code xml:lang}). */
    static String attributeName(XsdExtendedElement attribute) {
        String name = attribute.getElementName().substring(1);
        return attribute.getEmitPrefix() != null ? attribute.getEmitPrefix() + ":" + name : name;
    }

    /** Records the prefix declarations an element or attribute needs on the root ({@code xml} is predeclared). */
    static void declareEmissionNamespace(Map<String, String> namespaces, XsdExtendedElement element) {
        boolean known = element.getEmitNamespace() != null;
        String prefix = known ? element.getEmitPrefix() : element.getSourceNamespacePrefix();
        String namespace = known ? element.getEmitNamespace() : element.getSourceNamespace();
        if (prefix != null && !prefix.isEmpty() && !"xml".equals(prefix) && namespace != null && !namespace.isEmpty()) {
            namespaces.put(prefix, namespace);
        }
        if (element.getXsiTypePrefix() != null && element.getXsiTypeNamespace() != null) {
            namespaces.put(element.getXsiTypePrefix(), element.getXsiTypeNamespace());
        }
    }

    /**
     * Whether an optional attribute is emitted in mandatory-only mode after all: it is the element's ID and the element
     * emits a required IDREF(S), which would otherwise point at nothing (JATS {@code answer} requires
     * {@code pointer-to-question} and has an optional {@code id}).
     */
    static boolean idForEmittedReference(XsdExtendedElement attribute, List<XsdExtendedElement> attributes,
                                         XsdSampleDataGenerator identityValues) {
        return identityValues.identityKind(attribute) == XsdSampleDataGenerator.IdentityKind.ID
                && attributes.stream().anyMatch(other -> other != attribute && other.isMandatory()
                && fixedOrDefaultValue(other) == null
                && identityValues.identityKind(other) == XsdSampleDataGenerator.IdentityKind.IDREF);
    }

    /** Upper bound for repeating an element or choice to reach its {@code minOccurs}. */
    static final int MAX_REQUIRED_REPETITIONS = 10_000;

    /**
     * How often a sample repeats an element: up to {@code maxOccurrences} within its {@code maxOccurs}, and at least
     * its {@code minOccurs} (UCI {@code Covariance} needs 6 to 120 values). The occurrence bounds of an element
     * reference are read from the reference.
     */
    static int elementRepeatCount(XsdExtendedElement element, int maxOccurrences) {
        Node bounds = element.getCardinalityNode() != null ? element.getCardinalityNode() : element.getCurrentNode();
        return repeatCount(occurs(bounds, "minOccurs"), occurs(bounds, "maxOccurs"), maxOccurrences);
    }

    /**
     * {@code min(maxOccurs, maxOccurrences)}, raised to {@code minOccurs} (capped at
     * {@link #MAX_REQUIRED_REPETITIONS}).
     *
     * @param maxOccurs {@code -1} for unbounded
     */
    static int repeatCount(int minOccurs, int maxOccurs, int maxOccurrences) {
        int upper = maxOccurs < 0 ? maxOccurrences : Math.min(maxOccurs, maxOccurrences);
        return Math.max(upper, Math.min(minOccurs, MAX_REQUIRED_REPETITIONS));
    }

    /** An occurrence bound of a particle: 1 when absent or unparsable, -1 for {@code unbounded}. */
    static int occurs(Node particle, String attribute) {
        String value = particle instanceof org.w3c.dom.Element element ? element.getAttribute(attribute) : "";
        if (value.isBlank()) {
            return 1;
        }
        if ("unbounded".equalsIgnoreCase(value.trim())) {
            return -1;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /**
     * Appends {@code xsi:type} for an element whose declared type is abstract.
     *
     * @see XsdExtendedElement#getXsiType()
     */
    static void appendXsiType(StringBuilder sb, XsdExtendedElement element) {
        if (element.getXsiType() != null) {
            sb.append(" xsi:type=\"").append(element.getXsiType()).append('"');
        }
    }
}
