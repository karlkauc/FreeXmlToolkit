package org.fxt.freexmltoolkit.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.PdfDocumentationConfig;
import org.fxt.freexmltoolkit.domain.XsdAssertion;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Builds the detail sections of the global complex and simple types of the PDF documentation:
 * documentation, derivation, content model (diagram and children), facets with their values,
 * enumerations with their documentation, assertions, where the type is used and which types
 * derive from it. The sections have the structure of the element sections
 * ({@link PdfElementReferenceBuilder}), so the template renders both alike.
 */
final class PdfTypeReferenceBuilder {

    private static final Logger logger = LogManager.getLogger(PdfTypeReferenceBuilder.class);

    /** Users of a type a section lists by path; the rest are counted. */
    static final int MAX_LISTED_USERS = 20;

    private static final String XSD_NS = XMLConstants.W3C_XML_SCHEMA_NS_URI;

    private final XsdDocumentationPdfService pdf;
    private final PdfDocumentationConfig config;
    private final XsdDocumentationImageService imageService;
    private final Path imageDir;
    private final Set<String> languages;
    private final PdfElementReferenceBuilder elements;

    /** Section id per global type local name. */
    private final Map<String, String> sectionIds = new HashMap<>();

    PdfTypeReferenceBuilder(XsdDocumentationPdfService pdf, XsdDocumentationData data, PdfDocumentationConfig config,
                            XsdDocumentationImageService imageService, Path imageDir, Set<String> languages,
                            PdfElementReferenceBuilder elements) {
        this.pdf = pdf;
        this.config = config;
        this.imageService = imageService;
        this.imageDir = imageDir;
        this.languages = languages;
        this.elements = elements;
        int index = 0;
        List<Node> types = new ArrayList<>(data.getGlobalComplexTypes());
        types.addAll(data.getGlobalSimpleTypes());
        for (Node type : types) {
            if (type instanceof Element typeElement && !typeElement.getAttribute("name").isEmpty()) {
                sectionIds.putIfAbsent(typeElement.getAttribute("name"), "type-" + (++index));
            }
        }
    }

    /**
     * @param typeName a type name as written in the schema, with or without prefix
     * @return the id of the section of that global type, or null for built-in and unknown types
     */
    String sectionIdOf(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return null;
        }
        return sectionIds.get(XsdDocumentationPdfService.localName(typeName.strip()));
    }

    /**
     * Adds the detail section of a global complex type to its overview entry.
     *
     * @param doc   the intermediate document
     * @param entry the type's entry in the overview table
     * @param type  the {@code xs:complexType}
     */
    void writeComplexType(Document doc, Element entry, Element type) {
        String name = type.getAttribute("name");
        Element section = section(doc, entry, name, "Complex Type", "Global complex type");
        PdfTypeReferenceBuilder.TypeUsage usage = usageOf(name);

        Element properties = doc.createElement("properties");
        section.appendChild(properties);
        Element derivation = derivationOf(type);
        if (derivation != null) {
            String base = derivation.getAttribute("base");
            property(doc, properties, "Base", base, sectionIdOf(base));
            property(doc, properties, "Derivation", derivation.getLocalName() + " of "
                    + (derivation.getParentNode() != null ? derivation.getParentNode().getLocalName() : "content"), null);
        }
        for (String attribute : List.of("abstract", "mixed", "final", "block")) {
            if (type.hasAttribute(attribute)) {
                property(doc, properties, Character.toUpperCase(attribute.charAt(0)) + attribute.substring(1),
                        type.getAttribute(attribute), null);
            }
        }
        property(doc, properties, "Used", String.valueOf(usage.count()), null);

        writeDocumentation(doc, section, type);

        // The content model, as shown for the first element of this type
        XsdExtendedElement representative = usage.users().isEmpty() ? null : usage.users().getFirst();
        if (representative != null) {
            writeDiagram(doc, section, representative);
            writeChildren(doc, section, representative);
            for (XsdAssertion assertion : nonNull(representative.getAssertions())) {
                assertion(doc, section, assertion.getType() == XsdAssertion.Type.ASSERTION
                        ? "Assertion (simpleType)" : "Assert", assertion.getTest(), assertion.getDocumentation());
            }
        } else {
            for (Element assertion : descendants(type, "assert")) {
                assertion(doc, section, "Assert", assertion.getAttribute("test"), documentationText(assertion));
            }
        }
        writeUsage(doc, section, usage);
        if (config.isIncludeSourceCode()) {
            PdfElementReferenceBuilder.addSource(doc, section, XsdDocumentationPdfService.serialize(type));
        }
    }

    /**
     * Adds the detail section of a global simple type to its overview entry.
     *
     * @param doc   the intermediate document
     * @param entry the type's entry in the overview table
     * @param type  the {@code xs:simpleType}
     */
    void writeSimpleType(Document doc, Element entry, Element type) {
        String name = type.getAttribute("name");
        Element section = section(doc, entry, name, "Simple Type", "Global simple type");
        TypeUsage usage = usageOf(name);

        Element properties = doc.createElement("properties");
        section.appendChild(properties);
        Element restriction = child(type, "restriction");
        Element list = child(type, "list");
        Element union = child(type, "union");
        if (restriction != null) {
            property(doc, properties, "Variety", "Restriction", null);
            String base = restriction.getAttribute("base");
            if (!base.isEmpty()) {
                property(doc, properties, "Base", base, sectionIdOf(base));
            }
        } else if (list != null) {
            property(doc, properties, "Variety", "List", null);
            String item = list.getAttribute("itemType");
            property(doc, properties, "Item type", item.isEmpty() ? "(anonymous)" : item, sectionIdOf(item));
        } else if (union != null) {
            property(doc, properties, "Variety", "Union", null);
            String members = union.getAttribute("memberTypes").strip();
            property(doc, properties, "Member types", members.isEmpty() ? "(anonymous)" : members.replaceAll("\\s+", " | "), null);
        }
        if (type.hasAttribute("final")) {
            property(doc, properties, "Final", type.getAttribute("final"), null);
        }
        property(doc, properties, "Used", String.valueOf(usage.count()), null);

        writeDocumentation(doc, section, type);

        if (restriction != null) {
            Element restrictions = doc.createElement("restrictions");
            for (Node node = restriction.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (!(node instanceof Element facet) || !XSD_NS.equals(facet.getNamespaceURI())) {
                    continue;
                }
                String facetName = facet.getLocalName();
                switch (facetName) {
                    case "annotation", "simpleType" -> {
                        // not a facet
                    }
                    case "enumeration" -> {
                        Element value = add(doc, restrictions, "enumeration", facet.getAttribute("value"));
                        String documentation = documentationText(facet);
                        if (documentation != null) {
                            value.setAttribute("documentation", documentation);
                        }
                    }
                    case "assertion" -> assertion(doc, section, "Assertion (simpleType)", facet.getAttribute("test"),
                            documentationText(facet));
                    default -> {
                        Element f = add(doc, restrictions, "facet",
                                XsdDocumentationPdfService.breakable(facet.getAttribute("value")));
                        f.setAttribute("name", facetName);
                    }
                }
            }
            if (restrictions.hasChildNodes()) {
                section.insertBefore(restrictions, firstOf(section, "assertion"));
            }
        }
        writeUsage(doc, section, usage);
        if (config.isIncludeSourceCode()) {
            PdfElementReferenceBuilder.addSource(doc, section, XsdDocumentationPdfService.serialize(type));
        }
    }

    /** Usage of a type, with the users reduced to one per element section. */
    private TypeUsage usageOf(String name) {
        return new TypeUsage(pdf.typeUsage(name));
    }

    private Element section(Document doc, Element entry, String name, String kind, String path) {
        String id = sectionIdOf(name);
        entry.setAttribute("ref", id);
        Element section = doc.createElement("section");
        entry.appendChild(section);
        section.setAttribute("id", id);
        section.setAttribute("name", name);
        section.setAttribute("kind", kind);
        add(doc, section, "path", path);
        return section;
    }

    private void writeDocumentation(Document doc, Element section, Element type) {
        Element annotation = child(type, "annotation");
        if (annotation == null) {
            return;
        }
        for (Element documentation : children(annotation, "documentation")) {
            String lang = documentation.getAttributeNS(XMLConstants.XML_NS_URI, "lang");
            boolean wanted = lang.isEmpty() || languages == null || languages.isEmpty() || languages.contains(lang);
            String text = pdf.stripHtml(documentation.getTextContent()).strip();
            if (wanted && !text.isEmpty()) {
                add(doc, section, "documentation", text).setAttribute("lang", lang);
            }
        }
    }

    private void writeDiagram(Document doc, Element section, XsdExtendedElement representative) {
        if (!config.isIncludeElementDiagrams() || imageService == null || imageDir == null
                || elements.flattenedChildren(representative).isEmpty()) {
            return;
        }
        elements.addDiagram(doc, section, representative, section.getAttribute("id"));
    }

    private void writeChildren(Document doc, Element section, XsdExtendedElement representative) {
        List<XsdExtendedElement> children = elements.flattenedChildren(representative);
        if (children.isEmpty()) {
            return;
        }
        Element list = doc.createElement("children");
        section.appendChild(list);
        for (XsdExtendedElement child : children) {
            list.appendChild(elements.childEntry(doc, child));
        }
    }

    private void writeUsage(Document doc, Element section, TypeUsage usage) {
        if (!usage.users.isEmpty()) {
            Element usedIn = doc.createElement("used-in");
            usedIn.setAttribute("count", String.valueOf(usage.users.size()));
            section.appendChild(usedIn);
            usage.users.stream().limit(MAX_LISTED_USERS).forEach(user -> {
                Element u = add(doc, usedIn, "user", XsdDocumentationPdfService.breakable(pdf.getCleanXPath(user)));
                String ref = elements.sectionIdOf(user.getCurrentXpath());
                if (ref != null) {
                    u.setAttribute("ref", ref);
                }
            });
        }
        for (String derived : usage.derivedTypes) {
            Element d = add(doc, section, "derived-type", derived);
            String ref = sectionIdOf(derived);
            if (ref != null) {
                d.setAttribute("ref", ref);
            }
        }
    }

    private static void assertion(Document doc, Element section, String kind, String test, String documentation) {
        Element a = doc.createElement("assertion");
        section.appendChild(a);
        a.setAttribute("kind", kind);
        add(doc, a, "test", XsdDocumentationPdfService.breakable(test == null ? "" : test.strip()));
        if (documentation != null && !documentation.isBlank()) {
            add(doc, a, "documentation", documentation.strip());
        }
    }

    private static void property(Document doc, Element properties, String name, String value, String ref) {
        Element p = add(doc, properties, "property", XsdDocumentationPdfService.breakable(value));
        p.setAttribute("name", name);
        if (ref != null) {
            p.setAttribute("ref", ref);
        }
    }

    /** @return the documentation of an annotated schema component in the wanted language, or null */
    private String documentationText(Element component) {
        Element annotation = child(component, "annotation");
        if (annotation == null) {
            return null;
        }
        String fallback = null;
        for (Element documentation : children(annotation, "documentation")) {
            String text = pdf.stripHtml(documentation.getTextContent()).strip();
            if (text.isEmpty()) {
                continue;
            }
            String lang = documentation.getAttributeNS(XMLConstants.XML_NS_URI, "lang");
            if (languages != null && languages.contains(lang)) {
                return text;
            }
            if (fallback == null) {
                fallback = text;
            }
        }
        return fallback;
    }

    /** The extension or restriction of a complex type's simple or complex content, or null. */
    private static Element derivationOf(Element type) {
        for (String content : List.of("complexContent", "simpleContent")) {
            Element model = child(type, content);
            if (model != null) {
                Element derivation = child(model, "extension");
                return derivation != null ? derivation : child(model, "restriction");
            }
        }
        return null;
    }

    private static Element child(Element parent, String localName) {
        List<Element> found = children(parent, localName);
        return found.isEmpty() ? null : found.getFirst();
    }

    private static List<Element> children(Element parent, String localName) {
        List<Element> result = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && XSD_NS.equals(element.getNamespaceURI())
                    && localName.equals(element.getLocalName())) {
                result.add(element);
            }
        }
        return result;
    }

    /** Elements named {@code localName} inside a type, not inside the element declarations it holds. */
    private static List<Element> descendants(Element type, String localName) {
        List<Element> result = new ArrayList<>();
        for (Node node = type.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && XSD_NS.equals(element.getNamespaceURI())) {
                if (localName.equals(element.getLocalName())) {
                    result.add(element);
                } else if (!"element".equals(element.getLocalName())) {
                    result.addAll(descendants(element, localName));
                }
            }
        }
        return result;
    }

    private static Node firstOf(Element parent, String name) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (name.equals(node.getNodeName())) {
                return node;
            }
        }
        return null;
    }

    private static Element add(Document doc, Element parent, String name, String text) {
        Element element = doc.createElement(name);
        element.setTextContent(text == null ? "" : text);
        parent.appendChild(element);
        return element;
    }

    private static <T> List<T> nonNull(List<T> list) {
        return list == null ? List.of() : list;
    }

    /** The users of a type, one per element section (a declaration used at many paths counts once). */
    private final class TypeUsage {
        private final List<XsdExtendedElement> users;
        private final List<String> derivedTypes;
        private final int count;

        TypeUsage(XsdDocumentationPdfService.TypeUsage usage) {
            this.count = usage.count();
            this.derivedTypes = usage.derivedTypes();
            List<XsdExtendedElement> distinct = new ArrayList<>();
            Set<String> seen = new java.util.HashSet<>();
            for (XsdExtendedElement user : usage.users()) {
                String ref = elements.sectionIdOf(user.getCurrentXpath());
                if (ref == null || seen.add(ref)) {
                    distinct.add(user);
                }
            }
            this.users = distinct;
        }

        int count() {
            return count;
        }

        List<XsdExtendedElement> users() {
            return users;
        }
    }
}
