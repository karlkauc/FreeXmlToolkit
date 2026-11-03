package org.fxt.freexmltoolkit.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.transform.dom.DOMSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.fxt.freexmltoolkit.util.MarkdownSupport;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * What the PDF and the Word export of the XSD documentation read the same way: the schema's root
 * element, the type an element declares, usage of the global types, paths without compositor
 * containers, cardinalities, documentation in the selected language, restriction text, the
 * namespaces the schema declares and the deduplicated data dictionary.
 */
final class XsdDocumentationExportSupport {

    private static final Logger logger = LogManager.getLogger(XsdDocumentationExportSupport.class);

    private final XsdDocumentationData documentationData;
    private final Set<String> includedLanguages;

    /**
     * @param documentationData the documented schema
     * @param includedLanguages the documentation languages to prefer, empty or null for all
     */
    XsdDocumentationExportSupport(XsdDocumentationData documentationData, Set<String> includedLanguages) {
        this.documentationData = documentationData;
        this.includedLanguages = includedLanguages;
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
    XsdExtendedElement findRootElement() {
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

    static String declaredTypeAttribute(XsdExtendedElement element) {
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
    static List<String> derivationBases(Node node, boolean isType) {
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

    String extractSchemaName() {
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

    String getBaseType(org.w3c.dom.Element complexTypeElement) {
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

    String getSimpleTypeBase(org.w3c.dom.Element simpleTypeElement) {
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

    String getSimpleTypeFacetsSummary(org.w3c.dom.Element simpleTypeElement) {
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
    String getRestrictionsSummary(XsdExtendedElement element) {
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
     * These container elements are internal structures and are left out of the exports.
     */
    static boolean isContainerElement(XsdExtendedElement element) {
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
    static String getNodeAttribute(Node node, String attrName, String defaultValue) {
        if (node instanceof Element elem) {
            String value = elem.getAttribute(attrName);
            if (!value.isEmpty()) {
                return value;
            }
        }
        return defaultValue;
    }

    /**
     * Lists each named complex or simple type once, at its first occurrence in document order, as
     * the HTML data dictionary does with the same option. Elements of a built-in type or without a
     * type are always kept.
     */
    List<XsdExtendedElement> deduplicateByType(List<XsdExtendedElement> elements) {
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
        logger.info("Data dictionary deduplicated by type: {} -> {} rows", elements.size(), kept.size());
        return kept;
    }

    /** A namespace the schema declares: prefix ("(default)", or "-" when not declared), URI and what it is. */
    record NamespaceRow(String prefix, String uri, String kind) {
    }

    /** An import, include, redefine or override of the schema; "-" for an absent value. */
    record SchemaReference(String kind, String namespace, String location) {
    }

    /** What the namespace overview of an export lists. */
    record Namespaces(List<NamespaceRow> rows, List<SchemaReference> references) {
    }

    /**
     * Lists the namespaces the schema declares (prefix, URI and what they are: target namespace,
     * XML Schema, imported, or only declared), then namespaces it only imports, and the schema's
     * imports, includes, redefines and overrides with their locations.
     */
    Namespaces namespaces() {
        Element schema = schemaElement();
        String targetNs = documentationData.getTargetNamespace();
        Map<String, String> importLocations = new java.util.LinkedHashMap<>();
        List<SchemaReference> references = new ArrayList<>();
        if (schema != null) {
            for (Node child = schema.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof Element reference
                        && javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(reference.getNamespaceURI())
                        && List.of("import", "include", "redefine", "override").contains(reference.getLocalName())) {
                    String namespace = reference.getAttribute("namespace");
                    String location = reference.getAttribute("schemaLocation");
                    references.add(new SchemaReference(reference.getLocalName(), namespace.isEmpty() ? "-" : namespace,
                            location.isEmpty() ? "-" : location));
                    if ("import".equals(reference.getLocalName())) {
                        importLocations.put(namespace, location);
                    }
                }
            }
        }

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
        List<NamespaceRow> rows = new ArrayList<>();
        java.util.Set<String> listed = new java.util.HashSet<>();
        for (Map.Entry<String, String> entry : declared.entrySet()) {
            String uri = entry.getValue();
            rows.add(new NamespaceRow(entry.getKey(), uri, namespaceKind(uri, targetNs, importLocations)));
            listed.add(uri);
        }
        if (targetNs != null && !targetNs.isEmpty() && !listed.contains(targetNs)) {
            rows.add(new NamespaceRow("-", targetNs, "Target namespace"));
            listed.add(targetNs);
        }
        for (String imported : importLocations.keySet()) {
            if (!listed.contains(imported)) {
                rows.add(new NamespaceRow("-", imported.isEmpty() ? "(no namespace)" : imported, "Imported"));
            }
        }
        return new Namespaces(rows, references);
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

    /** @return the {@code xs:schema} element of the documented schema, or null */
    private Element schemaElement() {
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

}
