package org.fxt.freexmltoolkit.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.fxt.freexmltoolkit.util.SecureXmlFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Derives an XSD from sample XML documents ("Generate XSD from XML").
 *
 * <p>The schema is written in the Russian-doll style: one global element per root element name,
 * everything else declared locally. All occurrences of an element name below the same parent
 * declaration - repeated in one document or found in several documents - are merged into one
 * declaration, so that every sample is valid against the result:</p>
 * <ul>
 *   <li>a child that is missing in one occurrence of its parent gets {@code minOccurs="0"}, one that
 *       repeats gets {@code maxOccurs="unbounded"};</li>
 *   <li>an attribute is {@code use="required"} only when every occurrence carries it;</li>
 *   <li>a data type is the narrowest built-in type that fits <em>all</em> values seen;</li>
 *   <li>children are listed in document order; when the samples disagree about the order the
 *       content becomes a repeatable {@code xs:choice}.</li>
 * </ul>
 * Elements and attributes of another namespace than the root's cannot be declared in the same
 * schema file; they are allowed through {@code xs:any} / {@code xs:anyAttribute} wildcards.
 */
public final class XsdInference {

    private static final String XS = XMLConstants.W3C_XML_SCHEMA_NS_URI;

    private XsdInference() {
    }

    /** The samples cannot be turned into a schema: not well-formed, or roots in different namespaces. */
    public static final class InvalidInputException extends Exception {
        private static final long serialVersionUID = 1L;

        public InvalidInputException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * @param documents the texts of the sample documents (at least one)
     * @return the schema that all of them are valid against
     * @throws InvalidInputException when a document is not well-formed or the roots are in different namespaces
     */
    public static String infer(List<String> documents) throws InvalidInputException {
        List<InputSource> sources = new ArrayList<>();
        for (String text : documents == null ? List.<String>of() : documents) {
            sources.add(new InputSource(new StringReader(text == null ? "" : text)));
        }
        return inferSources(sources);
    }

    /**
     * Like {@link #infer(List)} for files; the parser reads the bytes, so the encoding named in each
     * file's XML declaration is honoured.
     *
     * @throws InvalidInputException also when a file cannot be read
     */
    public static String inferFiles(List<Path> files) throws InvalidInputException {
        List<InputSource> sources = new ArrayList<>();
        for (Path file : files == null ? List.<Path>of() : files) {
            try {
                sources.add(new InputSource(new ByteArrayInputStream(Files.readAllBytes(file))));
            } catch (IOException e) {
                throw new InvalidInputException("Cannot read " + file.getFileName() + ": " + e.getMessage(), e);
            }
        }
        return inferSources(sources);
    }

    private static String inferSources(List<InputSource> documents) throws InvalidInputException {
        if (documents.isEmpty()) {
            throw new InvalidInputException("No XML document given", null);
        }
        Map<String, ElementModel> roots = new LinkedHashMap<>();
        String targetNamespace = null;
        int number = 0;
        for (InputSource source : documents) {
            number++;
            Element root = parse(source, number, documents.size()).getDocumentElement();
            String namespace = root.getNamespaceURI();
            if (number == 1) {
                targetNamespace = namespace;
            } else if (!Objects.equals(targetNamespace, namespace)) {
                throw new InvalidInputException("The documents are in different namespaces ("
                        + describe(targetNamespace) + " and " + describe(namespace)
                        + "); one schema file describes one namespace", null);
            }
            roots.computeIfAbsent(root.getLocalName(), ElementModel::new).add(root, targetNamespace);
        }
        return new Writer(targetNamespace, documents.size()).write(roots.values());
    }

    private static String describe(String namespace) {
        return namespace == null ? "no namespace" : namespace;
    }

    private static Document parse(InputSource source, int number, int count) throws InvalidInputException {
        try {
            DocumentBuilderFactory factory = SecureXmlFactory.createSecureDocumentBuilderFactory();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(null); // the exception carries the message; nothing goes to stderr
            return builder.parse(source);
        } catch (SAXException | IOException e) {
            String which = count > 1 ? "Document " + number + " is" : "The document is";
            throw new InvalidInputException(which + " not well-formed XML: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalStateException("XML parser failed: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ model

    /** The built-in types the inference knows, each with the types it widens to. */
    enum ValueType {
        BOOLEAN("xs:boolean"), INTEGER("xs:integer"), DECIMAL("xs:decimal"), DATE("xs:date"),
        DATE_TIME("xs:dateTime"), TIME("xs:time"), ANY_URI("xs:anyURI"), STRING("xs:string");

        final String xsdName;

        ValueType(String xsdName) {
            this.xsdName = xsdName;
        }

        /** The narrowest type that holds values of both types. */
        ValueType widen(ValueType other) {
            if (other == null || other == this) {
                return this;
            }
            boolean numeric = (this == INTEGER || this == DECIMAL) && (other == INTEGER || other == DECIMAL);
            return numeric ? DECIMAL : STRING;
        }

        /** @return the type of one value as written in a document (already trimmed, not empty) */
        static ValueType of(String value) {
            if (value.equals("true") || value.equals("false")) {
                return BOOLEAN; // 0 and 1 are numbers far more often than booleans
            }
            if (isNumber(value, false)) {
                return INTEGER;
            }
            if (isNumber(value, true)) {
                return DECIMAL;
            }
            char first = value.charAt(0);
            if (first >= '0' && first <= '9' || first == '-') {
                if (parses(value, ValueType::parseDate)) {
                    return DATE;
                }
                if (parses(value, ValueType::parseDateTime)) {
                    return DATE_TIME;
                }
                if (parses(value, ValueType::parseTime)) {
                    return TIME;
                }
            }
            if ((value.startsWith("http://") || value.startsWith("https://")) && value.indexOf(' ') < 0) {
                return ANY_URI;
            }
            return STRING;
        }

        /**
         * A number that survives a round trip through the numeric type: no leading zeros (postal
         * codes, account numbers), no sign-only or exponent forms, at most 18 digits for integers.
         */
        private static boolean isNumber(String value, boolean decimal) {
            int start = value.startsWith("-") ? 1 : 0;
            int dot = value.indexOf('.');
            if (decimal != (dot >= 0) || value.length() == start) {
                return false;
            }
            String whole = dot < 0 ? value.substring(start) : value.substring(start, dot);
            String fraction = dot < 0 ? "" : value.substring(dot + 1);
            if (whole.isEmpty() || (decimal && fraction.isEmpty()) || !digits(whole) || !digits(fraction)) {
                return false;
            }
            if (whole.length() > 1 && whole.charAt(0) == '0') {
                return false;
            }
            if (!decimal && whole.length() > 18) {
                return false;
            }
            try {
                new BigDecimal(value);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        private static boolean digits(String text) {
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c < '0' || c > '9') {
                    return false;
                }
            }
            return true;
        }

        private interface Parser {
            void parse(String value);
        }

        private static boolean parses(String value, Parser parser) {
            try {
                parser.parse(value);
                return true;
            } catch (DateTimeParseException e) {
                return false;
            }
        }

        private static void parseDate(String value) {
            if (value.length() != 10) {
                throw new DateTimeParseException("not a plain date", value, 0);
            }
            LocalDate.parse(value);
        }

        private static void parseDateTime(String value) {
            if (value.length() < 19 || value.charAt(10) != 'T') {
                throw new DateTimeParseException("not a date and time", value, 0);
            }
            if (hasZone(value.substring(11))) {
                OffsetDateTime.parse(value);
            } else {
                LocalDateTime.parse(value);
            }
        }

        private static void parseTime(String value) {
            if (value.length() < 8 || value.charAt(2) != ':') {
                throw new DateTimeParseException("not a time", value, 0);
            }
            if (hasZone(value)) {
                OffsetTime.parse(value);
            } else {
                LocalTime.parse(value);
            }
        }

        private static boolean hasZone(String time) {
            return time.endsWith("Z") || time.indexOf('+') >= 0 || time.indexOf('-') >= 0;
        }
    }

    /** The values seen at one place (an element's text or an attribute). */
    private static final class Values {
        private ValueType type;
        private boolean empty;

        void add(String raw) {
            String value = raw == null ? "" : raw.strip();
            if (value.isEmpty()) {
                empty = true;
            } else {
                type = type == null ? ValueType.of(value) : type.widen(ValueType.of(value));
            }
        }

        /** An empty value is only valid for xs:string, so one empty occurrence decides. */
        ValueType type() {
            return type == null || empty ? ValueType.STRING : type;
        }
    }

    private static final class AttributeModel {
        final Values values = new Values();
        int occurrences;
    }

    /** One element declaration: everything seen for a name below one parent declaration. */
    private static final class ElementModel {
        final String name;
        int instances;
        final Values text = new Values();
        boolean hasText;
        boolean hasChildren;
        /** Children of the element's own namespace handling, in the order of the schema. */
        final Map<String, ChildModel> children = new LinkedHashMap<>();
        final List<String> order = new ArrayList<>();
        boolean unordered;
        final Map<String, AttributeModel> attributes = new LinkedHashMap<>();
        /** Namespaces of child elements / attributes that this schema file cannot declare. */
        final Set<String> foreignChildNamespaces = new LinkedHashSet<>();
        final Set<String> foreignAttributeNamespaces = new LinkedHashSet<>();

        ElementModel(String name) {
            this.name = name;
        }

        void add(Element element, String targetNamespace) {
            instances++;
            addAttributes(element);
            StringBuilder ownText = new StringBuilder();
            List<String> sequence = new ArrayList<>(); // names of this occurrence, repeats merged when adjacent
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
                short kind = node.getNodeType();
                if (kind == Node.TEXT_NODE || kind == Node.CDATA_SECTION_NODE) {
                    ownText.append(node.getNodeValue());
                } else if (kind == Node.ELEMENT_NODE) {
                    Element child = (Element) node;
                    hasChildren = true;
                    String namespace = child.getNamespaceURI();
                    boolean local = namespace == null || namespace.equals(targetNamespace);
                    String key = local ? childKey(child, targetNamespace) : "\u0000" + namespace;
                    if (local) {
                        ChildModel model = children.computeIfAbsent(key,
                                k -> new ChildModel(new ElementModel(child.getLocalName()),
                                        namespace == null && targetNamespace != null));
                        model.element.add(child, targetNamespace);
                    } else {
                        foreignChildNamespaces.add(namespace);
                    }
                    if (sequence.isEmpty() || !sequence.get(sequence.size() - 1).equals(key)) {
                        if (sequence.contains(key)) {
                            unordered = true; // a, b, a: no sequence of repeatable particles describes it
                        }
                        sequence.add(key);
                    }
                    counts.merge(key, 1, Integer::sum);
                }
            }
            mergeOrder(sequence);
            for (Map.Entry<String, ChildModel> entry : children.entrySet()) {
                entry.getValue().count(counts.getOrDefault(entry.getKey(), 0), instances);
            }
            String value = ownText.toString();
            if (!value.isBlank()) {
                hasText = true;
            }
            if (!hasChildren) {
                text.add(value);
            }
        }

        /** Qualified and unqualified children of the same local name are different declarations. */
        private static String childKey(Element child, String targetNamespace) {
            return (child.getNamespaceURI() == null && targetNamespace != null ? "u:" : "q:") + child.getLocalName();
        }

        private void addAttributes(Element element) {
            NamedNodeMap map = element.getAttributes();
            for (int i = 0; i < map.getLength(); i++) {
                Attr attribute = (Attr) map.item(i);
                String namespace = attribute.getNamespaceURI();
                if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(namespace)
                        || XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI.equals(namespace)) {
                    continue; // namespace declarations and xsi:* belong to no schema
                }
                if (namespace != null) {
                    foreignAttributeNamespaces.add(namespace);
                    continue;
                }
                AttributeModel model = attributes.computeIfAbsent(attribute.getName(), k -> new AttributeModel());
                model.occurrences++;
                model.values.add(attribute.getValue());
            }
        }

        /** Merges the order of one occurrence into the declared order; a contradiction ends the sequence. */
        private void mergeOrder(List<String> sequence) {
            int last = -1;
            for (String key : sequence) {
                int index = order.indexOf(key);
                if (index < 0) {
                    order.add(last + 1, key);
                    last++;
                } else if (index < last) {
                    unordered = true;
                } else {
                    last = index;
                }
            }
        }
    }

    /** A child declaration with how often it occurs per parent occurrence. */
    private static final class ChildModel {
        final ElementModel element;
        final boolean unqualified;
        boolean optional;
        boolean repeats;
        private int parentsSeen;

        ChildModel(ElementModel element, boolean unqualified) {
            this.element = element;
            this.unqualified = unqualified;
        }

        /** @param parentInstances occurrences of the parent so far, this one included */
        void count(int count, int parentInstances) {
            if (count == 0 || parentsSeen + 1 < parentInstances) {
                optional = true; // missing now, or first seen after earlier parents went without it
            }
            if (count > 0) {
                parentsSeen = parentInstances;
            }
            if (count > 1) {
                repeats = true;
            }
        }
    }

    // ------------------------------------------------------------------ output

    private static final class Writer {
        private final StringBuilder out = new StringBuilder();
        private final String targetNamespace;
        private final int documents;

        Writer(String targetNamespace, int documents) {
            this.targetNamespace = targetNamespace;
            this.documents = documents;
        }

        String write(Iterable<ElementModel> roots) {
            out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            out.append("<xs:schema xmlns:xs=\"").append(XS).append('"');
            if (targetNamespace != null) {
                out.append("\n           targetNamespace=\"").append(escape(targetNamespace)).append('"');
                out.append("\n           xmlns:tns=\"").append(escape(targetNamespace)).append('"');
                out.append("\n           elementFormDefault=\"qualified\"");
            }
            out.append(">\n\n");
            line(1, "<xs:annotation>");
            line(2, "<xs:documentation>Generated by FreeXmlToolkit from sample XML. Source documents: " + documents
                    + ". Review cardinalities, data types and optional parts before use.</xs:documentation>");
            line(1, "</xs:annotation>");
            for (ElementModel root : roots) {
                out.append('\n');
                element(1, root, "");
            }
            out.append("\n</xs:schema>\n");
            return out.toString();
        }

        private void element(int depth, ElementModel model, String particle) {
            String open = "<xs:element name=\"" + escape(model.name) + "\"";
            boolean attributes = !model.attributes.isEmpty() || !model.foreignAttributeNamespaces.isEmpty();
            if (!model.hasChildren && !attributes) {
                line(depth, open + " type=\"" + model.text.type().xsdName + "\"" + particle + "/>");
                return;
            }
            line(depth, open + particle + ">");
            if (!model.hasChildren) {
                line(depth + 1, "<xs:complexType>");
                line(depth + 2, "<xs:simpleContent>");
                line(depth + 3, "<xs:extension base=\"" + model.text.type().xsdName + "\">");
                attributes(depth + 4, model);
                line(depth + 3, "</xs:extension>");
                line(depth + 2, "</xs:simpleContent>");
            } else {
                line(depth + 1, model.hasText ? "<xs:complexType mixed=\"true\">" : "<xs:complexType>");
                content(depth + 2, model);
                attributes(depth + 2, model);
            }
            line(depth + 1, "</xs:complexType>");
            line(depth, "</xs:element>");
        }

        private void content(int depth, ElementModel model) {
            if (model.unordered) {
                // the samples list the children in different orders, or one comes back after another
                line(depth, "<xs:choice minOccurs=\"0\" maxOccurs=\"unbounded\">");
            } else {
                line(depth, "<xs:sequence>");
            }
            for (String key : model.order) {
                ChildModel child = model.children.get(key);
                if (child == null) {
                    String occurs = model.unordered ? "" : " minOccurs=\"0\" maxOccurs=\"unbounded\"";
                    line(depth + 1, "<xs:any namespace=\"" + escape(key.substring(1)) + "\" processContents=\"lax\""
                            + occurs + "/>");
                    continue;
                }
                StringBuilder particle = new StringBuilder();
                if (child.unqualified) {
                    particle.append(" form=\"unqualified\"");
                }
                if (!model.unordered) {
                    if (child.optional) {
                        particle.append(" minOccurs=\"0\"");
                    }
                    if (child.repeats) {
                        particle.append(" maxOccurs=\"unbounded\"");
                    }
                }
                element(depth + 1, child.element, particle.toString());
            }
            line(depth, model.unordered ? "</xs:choice>" : "</xs:sequence>");
        }

        private void attributes(int depth, ElementModel model) {
            for (Map.Entry<String, AttributeModel> entry : model.attributes.entrySet()) {
                AttributeModel attribute = entry.getValue();
                String use = attribute.occurrences == model.instances ? " use=\"required\"" : "";
                line(depth, "<xs:attribute name=\"" + escape(entry.getKey()) + "\" type=\""
                        + attribute.values.type().xsdName + "\"" + use + "/>");
            }
            if (!model.foreignAttributeNamespaces.isEmpty()) {
                line(depth, "<xs:anyAttribute namespace=\"" + escape(String.join(" ", model.foreignAttributeNamespaces))
                        + "\" processContents=\"lax\"/>");
            }
        }

        private void line(int depth, String text) {
            out.append("  ".repeat(depth)).append(text).append('\n');
        }

        private static String escape(String text) {
            return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
        }
    }
}
