package org.fxt.freexmltoolkit.service;

import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.xerces.util.XMLChar;
import org.fxt.freexmltoolkit.util.SecureXmlFactory;
import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.common.FlowStyle;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;
import org.snakeyaml.engine.v2.schema.CoreSchema;

import com.google.gson.FormattingStyle;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.internal.LazilyParsedNumber;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;

/**
 * Converts between XML, JSON and YAML, with two mappings between XML and JSON (YAML goes through
 * the JSON form):
 *
 * <ul>
 *   <li>{@link Mapping#STRUCTURED}: a readable mapping for any XML. An element becomes a property
 *       named after it; attributes become {@code "@name"} properties, namespace declarations
 *       {@code "@xmlns:p"}, text next to attributes or child elements {@code "#text"}; repeated
 *       child elements become an array. An element with only text becomes a string (a number or
 *       boolean with {@link Options#typedValues()}). JSON is mapped back the same way: a top-level
 *       object with one property names the root element, otherwise the root is
 *       {@link Options#rootName()}; array items inside arrays are {@code <item>} elements; property
 *       names that are no XML names are made into names ({@code "first name"} → {@code first_name}).
 *       Comments, processing instructions and the order between differently named siblings are
 *       not kept.</li>
 *   <li>{@link Mapping#W3C}: the XML representation of JSON from XPath 3.1
 *       ({@code fn:json-to-xml} / {@code fn:xml-to-json}, namespace
 *       {@value #W3C_NAMESPACE}), lossless for any JSON. XML → JSON then needs a document in that
 *       vocabulary.</li>
 * </ul>
 *
 * <p>JSON input may be JSON, JSON with comments or JSON5 ({@link JsonService#parseAuto}). YAML
 * input is YAML 1.2 (core schema); several documents in one file become a JSON array; YAML output
 * is block style ({@link #writeYaml}). XML is parsed without DTDs and external entities. UI-free; used by the shell's Tools menu and
 * {@code fxt-cli convert}. Documented in {@code docs/json-editor.md}.
 */
public final class DataFormatConverter {

    /** Namespace of the W3C XML representation of JSON. */
    public static final String W3C_NAMESPACE = "http://www.w3.org/2005/xpath-functions";

    /** A document format. */
    public enum Format {
        XML("xml"), JSON("json"), YAML("yaml");

        private final String extension;

        Format(String extension) {
            this.extension = extension;
        }

        /** @return the usual file extension, without dot */
        public String extension() {
            return extension;
        }

        /** @return the format's name for messages and titles ({@code XML}, {@code JSON}, {@code YAML}) */
        public String label() {
            return name();
        }

        /** @return the format of a file name by its extension: JSON, YAML, else XML */
        public static Format ofFileName(String fileName) {
            String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
            if (name.endsWith(".json") || name.endsWith(".jsonc") || name.endsWith(".json5")) {
                return JSON;
            }
            if (name.endsWith(".yaml") || name.endsWith(".yml")) {
                return YAML;
            }
            return XML;
        }
    }

    /** How XML and JSON are mapped onto each other. */
    public enum Mapping {
        /** Readable mapping for any XML: {@code "@attr"}, {@code "#text"}, arrays for repeated elements. */
        STRUCTURED,
        /** The XPath 3.1 XML representation of JSON ({@code fn:json-to-xml}), lossless. */
        W3C
    }

    /**
     * Conversion options.
     *
     * @param mapping     the mapping
     * @param typedValues XML → JSON (structured): text that is a JSON number or {@code true}/{@code false}
     *                    becomes a number or boolean instead of a string
     * @param rootName    JSON → XML (structured): root element when the JSON does not name one
     * @param indent      spaces per indentation level of the output (0 = compact)
     */
    public record Options(Mapping mapping, boolean typedValues, String rootName, int indent) {

        /** Structured mapping, strings only, root {@code json}, indent 2. */
        public static final Options DEFAULTS = new Options(Mapping.STRUCTURED, false, "json", 2);

        public Options {
            mapping = mapping == null ? Mapping.STRUCTURED : mapping;
            rootName = rootName == null || rootName.isBlank() ? "json" : rootName.trim();
            indent = Math.max(0, Math.min(indent, 16));
        }

        public Options withMapping(Mapping value) {
            return new Options(value, typedValues, rootName, indent);
        }

        public Options withTypedValues(boolean value) {
            return new Options(mapping, value, rootName, indent);
        }

        public Options withRootName(String value) {
            return new Options(mapping, typedValues, value, indent);
        }

        public Options withIndent(int value) {
            return new Options(mapping, typedValues, rootName, value);
        }
    }

    /** A document could not be read or converted; the message is meant for the user. */
    public static final class ConversionException extends Exception {
        public ConversionException(String message) {
            super(message);
        }

        public ConversionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final Pattern JSON_NUMBER = Pattern.compile("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?");
    private static final String ARRAY_ITEM = "item";

    private DataFormatConverter() {
    }

    /**
     * Converts {@code text} from one format to another.
     *
     * @throws ConversionException if the input cannot be read or the conversion is not possible
     *                             (YAML is not written; same format in and out formats the text)
     */
    public static String convert(String text, Format from, Format to, Options options) throws ConversionException {
        if (from == Format.XML && to == Format.XML) {
            throw new ConversionException("The document is XML already.");
        }
        JsonElement tree = switch (from) {
            case XML -> xmlToJsonElement(text, options);
            case JSON -> parseJson(text);
            case YAML -> parseYaml(text);
        };
        return switch (to) {
            case XML -> jsonToXml(tree, options);
            case JSON -> writeJson(tree, options);
            case YAML -> writeYaml(tree, options);
        };
    }

    /** The JSON tree of an XML document in the mapping of the options. */
    private static JsonElement xmlToJsonElement(String xml, Options options) throws ConversionException {
        return options.mapping() == Mapping.W3C ? parseJson(w3cXmlToJson(xml))
                : xmlToJsonTree(xml, options.typedValues());
    }

    // ---- XML → JSON ---------------------------------------------------------------------------

    /** Converts an XML document to JSON. */
    public static String xmlToJson(String xml, Options options) throws ConversionException {
        return writeJson(xmlToJsonElement(xml, options), options);
    }

    /** The structured JSON of an XML document: {@code {"root": …}}. */
    public static JsonObject xmlToJsonTree(String xml, boolean typedValues) throws ConversionException {
        XMLInputFactory factory = SecureXmlFactory.createSecureXMLInputFactory();
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        Deque<ElementFrame> stack = new ArrayDeque<>();
        JsonObject result = new JsonObject();
        XMLStreamReader reader = null;
        try {
            reader = factory.createXMLStreamReader(new StringReader(xml));
            while (reader.hasNext()) {
                switch (reader.next()) {
                    case XMLStreamConstants.START_ELEMENT -> {
                        ElementFrame frame = new ElementFrame(qualifiedName(reader.getPrefix(), reader.getLocalName()));
                        for (int i = 0; i < reader.getNamespaceCount(); i++) {
                            String prefix = reader.getNamespacePrefix(i);
                            frame.attributes.put(prefix == null || prefix.isEmpty() ? "@xmlns" : "@xmlns:" + prefix,
                                    new JsonPrimitive(nullToEmpty(reader.getNamespaceURI(i))));
                        }
                        for (int i = 0; i < reader.getAttributeCount(); i++) {
                            String name = qualifiedName(reader.getAttributePrefix(i), reader.getAttributeLocalName(i));
                            frame.attributes.put("@" + name, value(reader.getAttributeValue(i), typedValues));
                        }
                        if (!stack.isEmpty()) {
                            stack.peek().hasChildElements = true;
                        }
                        stack.push(frame);
                    }
                    case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA, XMLStreamConstants.SPACE -> {
                        if (!stack.isEmpty()) {
                            stack.peek().text.append(reader.getText());
                        }
                    }
                    case XMLStreamConstants.ENTITY_REFERENCE -> {
                        if (!stack.isEmpty()) {
                            String replacement = reader.getText();
                            stack.peek().text.append(replacement != null && !replacement.isEmpty()
                                    ? replacement : "&" + reader.getLocalName() + ";");
                        }
                    }
                    case XMLStreamConstants.END_ELEMENT -> {
                        ElementFrame frame = stack.pop();
                        JsonElement value = frame.toJson(typedValues);
                        if (stack.isEmpty()) {
                            result.add(frame.name, value);
                        } else {
                            stack.peek().children.computeIfAbsent(frame.name, k -> new ArrayList<>()).add(value);
                        }
                    }
                    default -> {
                        // comments, processing instructions, DTD: not part of the JSON
                    }
                }
            }
        } catch (XMLStreamException e) {
            throw new ConversionException("The XML is not well-formed" + location(e) + ": " + cleanMessage(e), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (XMLStreamException ignored) {
                    // nothing to release
                }
            }
        }
        if (result.isEmpty()) {
            throw new ConversionException("The XML has no root element.");
        }
        return result;
    }

    /** An element while it is read. */
    private static final class ElementFrame {
        final String name;
        final Map<String, JsonElement> attributes = new LinkedHashMap<>();
        final Map<String, List<JsonElement>> children = new LinkedHashMap<>();
        final StringBuilder text = new StringBuilder();
        boolean hasChildElements;

        ElementFrame(String name) {
            this.name = name;
        }

        JsonElement toJson(boolean typedValues) {
            if (attributes.isEmpty() && !hasChildElements) {
                return value(text.toString(), typedValues);
            }
            JsonObject object = new JsonObject();
            attributes.forEach(object::add);
            String content = text.toString().strip();
            if (!content.isEmpty()) {
                object.add("#text", value(content, typedValues));
            }
            for (Map.Entry<String, List<JsonElement>> child : children.entrySet()) {
                List<JsonElement> values = child.getValue();
                if (values.size() == 1) {
                    object.add(child.getKey(), values.get(0));
                } else {
                    JsonArray array = new JsonArray(values.size());
                    values.forEach(array::add);
                    object.add(child.getKey(), array);
                }
            }
            return object;
        }
    }

    private static JsonElement value(String text, boolean typed) {
        if (typed) {
            String trimmed = text.strip();
            if ("true".equals(trimmed) || "false".equals(trimmed)) {
                return new JsonPrimitive(Boolean.parseBoolean(trimmed));
            }
            if (JSON_NUMBER.matcher(trimmed).matches()) {
                return new JsonPrimitive(new LazilyParsedNumber(trimmed));
            }
        }
        return new JsonPrimitive(text);
    }

    private static String qualifiedName(String prefix, String localName) {
        return prefix == null || prefix.isEmpty() ? localName : prefix + ":" + localName;
    }

    /** {@code fn:xml-to-json} of a document in the W3C vocabulary. */
    private static String w3cXmlToJson(String xml) throws ConversionException {
        Processor processor = SaxonResourceAccess.restrictedProcessor();
        XdmNode document;
        try {
            document = processor.newDocumentBuilder()
                    .build(new javax.xml.transform.stream.StreamSource(new StringReader(xml)));
        } catch (SaxonApiException e) {
            throw new ConversionException("The XML is not well-formed: " + saxonMessage(e), e);
        }
        try {
            XPathSelector selector = processor.newXPathCompiler().compile("xml-to-json(.)").load();
            selector.setContextItem(document);
            return selector.evaluateSingle().getStringValue();
        } catch (SaxonApiException e) {
            throw new ConversionException("The document is not in the W3C XML representation of JSON (namespace "
                    + W3C_NAMESPACE + "). Use the structured mapping for other XML. " + saxonMessage(e), e);
        }
    }

    // ---- JSON / YAML reading ------------------------------------------------------------------

    /** Parses JSON, JSON with comments or JSON5. */
    public static JsonElement parseJson(String json) throws ConversionException {
        try {
            JsonElement element = new JsonService().parseAuto(json);
            if (element == null) {
                throw new ConversionException("The JSON is empty.");
            }
            return element;
        } catch (JsonParseException | IllegalStateException e) {
            throw new ConversionException("The JSON is not valid: " + e.getMessage(), e);
        }
    }

    /**
     * Parses YAML 1.2 (core schema). Several documents become an array. Aliases are expanded
     * (at most 50 per document, so an alias bomb cannot exhaust memory); duplicate keys and
     * recursive aliases are errors.
     */
    public static JsonElement parseYaml(String yaml) throws ConversionException {
        LoadSettings settings = LoadSettings.builder()
                .setSchema(new CoreSchema())
                .setAllowDuplicateKeys(false)
                .setAllowRecursiveKeys(false)
                .setMaxAliasesForCollections(50)
                .setCodePointLimit(Integer.MAX_VALUE)
                .build();
        List<JsonElement> documents = new ArrayList<>();
        try {
            for (Object document : new Load(settings).loadAllFromString(yaml)) {
                documents.add(yamlToJson(document, new IdentityHashMap<>()));
            }
        } catch (YamlEngineException e) {
            throw new ConversionException("The YAML is not valid: " + e.getMessage(), e);
        }
        if (documents.isEmpty()) {
            return JsonNull.INSTANCE;
        }
        if (documents.size() == 1) {
            return documents.get(0);
        }
        JsonArray array = new JsonArray(documents.size());
        documents.forEach(array::add);
        return array;
    }

    private static JsonElement yamlToJson(Object value, IdentityHashMap<Object, Boolean> open) throws ConversionException {
        if (value == null) {
            return JsonNull.INSTANCE;
        }
        if (value instanceof String s) {
            return new JsonPrimitive(s);
        }
        if (value instanceof Boolean b) {
            return new JsonPrimitive(b);
        }
        if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            return Double.isFinite(d) ? new JsonPrimitive(d)
                    : new JsonPrimitive(Double.isNaN(d) ? ".nan" : d > 0 ? ".inf" : "-.inf");
        }
        if (value instanceof Integer || value instanceof Long || value instanceof BigInteger
                || value instanceof Short || value instanceof Byte || value instanceof BigDecimal) {
            return new JsonPrimitive((Number) value);
        }
        if (value instanceof byte[] bytes) {
            return new JsonPrimitive(Base64.getEncoder().encodeToString(bytes));
        }
        if (value instanceof Map<?, ?> || value instanceof Collection<?>) {
            if (open.put(value, Boolean.TRUE) != null) {
                throw new ConversionException("The YAML contains a recursive alias, which JSON and XML cannot represent.");
            }
            try {
                if (value instanceof Map<?, ?> map) {
                    JsonObject object = new JsonObject();
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        object.add(String.valueOf(entry.getKey()), yamlToJson(entry.getValue(), open));
                    }
                    return object;
                }
                JsonArray array = new JsonArray();
                for (Object item : (Collection<?>) value) {
                    array.add(yamlToJson(item, open));
                }
                return array;
            } finally {
                open.remove(value);
            }
        }
        return new JsonPrimitive(String.valueOf(value));
    }

    /** Writes JSON with the indentation of the options. */
    public static String writeJson(JsonElement json, Options options) {
        GsonBuilder builder = new GsonBuilder().disableHtmlEscaping().serializeNulls();
        if (options.indent() > 0) {
            builder.setFormattingStyle(FormattingStyle.PRETTY.withIndent(" ".repeat(options.indent())));
        }
        return builder.create().toJson(json) + (options.indent() > 0 ? "\n" : "");
    }

    /**
     * Writes YAML 1.2 in block style (indent from the options, at least 2). Strings that would read
     * back as another type ({@code "007"}, {@code "true"}, {@code "null"}) are quoted.
     */
    public static String writeYaml(JsonElement json, Options options) {
        DumpSettings settings = DumpSettings.builder()
                .setSchema(new CoreSchema())
                .setDefaultFlowStyle(FlowStyle.BLOCK)
                .setIndent(Math.max(2, Math.min(options.indent(), 10)))
                .setIndicatorIndent(0)
                .setWidth(Integer.MAX_VALUE)
                .setSplitLines(false)
                .setMaxSimpleKeyLength(1024)
                .build();
        return new Dump(settings).dumpToString(toYamlValue(json));
    }

    /** JSON → plain Java values for the YAML dumper (numbers as Long, BigInteger, Double or BigDecimal). */
    private static Object toYamlValue(JsonElement json) {
        if (json == null || json.isJsonNull()) {
            return null;
        }
        if (json.isJsonObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
                map.put(entry.getKey(), toYamlValue(entry.getValue()));
            }
            return map;
        }
        if (json.isJsonArray()) {
            List<Object> list = new ArrayList<>(json.getAsJsonArray().size());
            for (JsonElement item : json.getAsJsonArray()) {
                list.add(toYamlValue(item));
            }
            return list;
        }
        JsonPrimitive primitive = json.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isNumber()) {
            BigDecimal number = primitive.getAsBigDecimal();
            String text = primitive.getAsString();
            if (!text.contains(".") && !text.contains("e") && !text.contains("E")) {
                BigInteger integer = number.toBigIntegerExact();
                return integer.bitLength() < 64 ? (Object) integer.longValue() : integer;
            }
            double d = number.doubleValue();
            return Double.isFinite(d) && new BigDecimal(Double.toString(d)).compareTo(number) == 0 ? (Object) d : number;
        }
        return primitive.getAsString();
    }

    // ---- JSON → XML ---------------------------------------------------------------------------

    /** Converts JSON (also JSON with comments or JSON5) to XML. */
    public static String jsonToXml(String json, Options options) throws ConversionException {
        return jsonToXml(parseJson(json), options);
    }

    /** Converts a JSON tree (for example from {@link #parseYaml}) to XML. */
    public static String jsonToXml(JsonElement json, Options options) throws ConversionException {
        if (options.mapping() == Mapping.W3C) {
            return w3cJsonToXml(writeJson(json, Options.DEFAULTS.withIndent(0)), options.indent());
        }
        XmlWriter writer = new XmlWriter(options.indent());
        writer.declaration();
        if (json instanceof JsonObject object && object.size() == 1) {
            Map.Entry<String, JsonElement> only = object.entrySet().iterator().next();
            if (!only.getKey().startsWith("@") && !only.getKey().startsWith("#") && !only.getValue().isJsonArray()) {
                writer.root(only.getKey(), only.getValue());
                return writer.toString();
            }
        }
        String root = options.rootName();
        if (!XMLChar.isValidName(root)) {
            throw new ConversionException("'" + root + "' is not a valid XML element name.");
        }
        writer.root(root, json);
        return writer.toString();
    }

    /** {@code fn:json-to-xml}, serialized with Saxon's indentation (Saxon-HE has a fixed indent width). */
    private static String w3cJsonToXml(String strictJson, int indent) throws ConversionException {
        Processor processor = SaxonResourceAccess.restrictedProcessor();
        try {
            XPathCompiler compiler = processor.newXPathCompiler();
            compiler.declareVariable(new net.sf.saxon.s9api.QName("json"));
            XPathSelector selector = compiler.compile("json-to-xml($json)").load();
            selector.setVariable(new net.sf.saxon.s9api.QName("json"), new XdmAtomicValue(strictJson));
            XdmValue result = selector.evaluate();
            java.io.StringWriter out = new java.io.StringWriter();
            Serializer serializer = processor.newSerializer(out);
            serializer.setOutputProperty(Serializer.Property.METHOD, "xml");
            serializer.setOutputProperty(Serializer.Property.ENCODING, "UTF-8");
            serializer.setOutputProperty(Serializer.Property.INDENT, indent > 0 ? "yes" : "no");
            for (XdmItem item : result) {
                serializer.serializeXdmValue(item);
            }
            String xml = out.toString();
            return xml.endsWith("\n") ? xml : xml + "\n";
        } catch (SaxonApiException e) {
            throw new ConversionException("The JSON cannot be converted: " + saxonMessage(e), e);
        }
    }

    /** Writes the structured XML of a JSON tree. */
    private static final class XmlWriter {
        private final StringBuilder out = new StringBuilder();
        private final String indentUnit;
        private final Deque<Set<String>> prefixScopes = new ArrayDeque<>();
        private int depth;

        XmlWriter(int indent) {
            this.indentUnit = " ".repeat(indent);
        }

        void declaration() {
            out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            newline();
        }

        /** The root element; an array becomes {@code <item>} children. */
        void root(String key, JsonElement value) throws ConversionException {
            single(key, value);
        }

        /** One element (or, for an array, one element per item). */
        void element(String key, JsonElement value) throws ConversionException {
            if (value.isJsonArray()) {
                for (JsonElement item : value.getAsJsonArray()) {
                    single(key, item);
                }
            } else {
                single(key, value);
            }
        }

        private void single(String key, JsonElement value) throws ConversionException {
            Set<String> declared = new HashSet<>();
            if (value.isJsonObject()) {
                for (String property : value.getAsJsonObject().keySet()) {
                    if (property.startsWith("@xmlns:")) {
                        declared.add(property.substring("@xmlns:".length()));
                    }
                }
            }
            prefixScopes.push(declared);
            try {
                String name = elementName(key);
                indent();
                out.append('<').append(name);
                if (value.isJsonArray() && value.getAsJsonArray().isEmpty()) {
                    out.append("/>");
                } else if (value.isJsonArray()) {                // an array inside an array
                    out.append('>');
                    depth++;
                    newline();
                    for (JsonElement item : value.getAsJsonArray()) {
                        single(ARRAY_ITEM, item);
                    }
                    depth--;
                    indent();
                    out.append("</").append(name).append('>');
                } else if (value.isJsonObject()) {
                    object(name, value.getAsJsonObject());
                } else {
                    String text = value.isJsonNull() ? "" : value.getAsString();
                    if (text.isEmpty()) {
                        out.append("/>");
                    } else {
                        out.append('>').append(escapeText(text)).append("</").append(name).append('>');
                    }
                }
                newline();
            } finally {
                prefixScopes.pop();
            }
        }

        private void object(String name, JsonObject object) throws ConversionException {
            String text = null;
            List<Map.Entry<String, JsonElement>> children = new ArrayList<>();
            Set<String> written = new HashSet<>();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                String key = entry.getKey();
                JsonElement value = entry.getValue();
                if ("#text".equals(key)) {
                    text = value.isJsonPrimitive() ? value.getAsString() : value.isJsonNull() ? "" : value.toString();
                } else if (key.startsWith("@") && key.length() > 1) {
                    String attribute = attributeName(key.substring(1));
                    if (written.add(attribute)) {
                        String attributeValue = value.isJsonPrimitive() ? value.getAsString()
                                : value.isJsonNull() ? "" : value.toString();
                        out.append(' ').append(attribute).append("=\"").append(escapeAttribute(attributeValue)).append('"');
                    }
                } else {
                    children.add(entry);
                }
            }
            boolean hasText = text != null && !text.isEmpty();
            if (children.isEmpty() && !hasText) {
                out.append("/>");
                return;
            }
            out.append('>');
            if (children.isEmpty()) {
                out.append(escapeText(text)).append("</").append(name).append('>');
                return;
            }
            depth++;
            newline();
            if (hasText) {
                indent();
                out.append(escapeText(text));
                newline();
            }
            for (Map.Entry<String, JsonElement> child : children) {
                element(child.getKey(), child.getValue());
            }
            depth--;
            indent();
            out.append("</").append(name).append('>');
        }

        private String elementName(String key) {
            return xmlName(key, false);
        }

        private String attributeName(String key) {
            if ("xmlns".equals(key)) {
                return key;
            }
            return xmlName(key, true);
        }

        /** A valid (namespace-well-formed) XML name for a JSON property name. */
        private String xmlName(String key, boolean attribute) {
            int colon = key.indexOf(':');
            if (colon > 0 && colon == key.lastIndexOf(':')) {
                String prefix = key.substring(0, colon);
                String local = key.substring(colon + 1);
                boolean xmlnsDeclaration = attribute && "xmlns".equals(prefix);
                if (XMLChar.isValidNCName(prefix) && XMLChar.isValidNCName(local)
                        && (xmlnsDeclaration || "xml".equals(prefix) || isDeclared(prefix))) {
                    return key;
                }
            }
            return ncName(key);
        }

        private boolean isDeclared(String prefix) {
            for (Set<String> scope : prefixScopes) {
                if (scope.contains(prefix)) {
                    return true;
                }
            }
            return false;
        }

        private static String ncName(String key) {
            if (key.isEmpty()) {
                return "_";
            }
            StringBuilder name = new StringBuilder(key.length() + 1);
            key.codePoints().forEach(c -> name.appendCodePoint(c < 0x10000 && XMLChar.isNCName(c) || c >= 0x10000 ? c : '_'));
            if (!XMLChar.isNCNameStart(name.charAt(0))) {
                name.insert(0, '_');
            }
            return name.toString();
        }

        private String escapeText(String text) throws ConversionException {
            return escape(text, false);
        }

        private String escapeAttribute(String text) throws ConversionException {
            return escape(text, true);
        }

        private static String escape(String text, boolean attribute) throws ConversionException {
            StringBuilder escaped = new StringBuilder(text.length() + 16);
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                switch (c) {
                    case '&' -> escaped.append("&amp;");
                    case '<' -> escaped.append("&lt;");
                    case '>' -> escaped.append("&gt;");
                    case '"' -> escaped.append(attribute ? "&quot;" : "\"");
                    case '\n' -> escaped.append(attribute ? "&#10;" : "\n");
                    case '\r' -> escaped.append("&#13;");
                    case '\t' -> escaped.append(attribute ? "&#9;" : "\t");
                    default -> {
                        if (Character.isHighSurrogate(c) && i + 1 < text.length()
                                && Character.isLowSurrogate(text.charAt(i + 1))) {
                            escaped.append(c).append(text.charAt(++i));
                        } else if (!XMLChar.isValid(c) || Character.isSurrogate(c)) {
                            throw new ConversionException(String.format(
                                    "The value contains the character U+%04X, which XML 1.0 cannot represent. "
                                            + "Use the W3C mapping, which replaces such characters.", (int) c));
                        } else {
                            escaped.append(c);
                        }
                    }
                }
            }
            return escaped.toString();
        }

        private void indent() {
            if (!indentUnit.isEmpty()) {
                out.append(indentUnit.repeat(depth));
            }
        }

        private void newline() {
            if (!indentUnit.isEmpty()) {
                out.append('\n');
            }
        }

        @Override
        public String toString() {
            return indentUnit.isEmpty() ? out.append('\n').toString() : out.toString();
        }
    }

    // ---- messages -----------------------------------------------------------------------------

    private static String location(XMLStreamException e) {
        javax.xml.stream.Location location = e.getLocation();
        return location == null || location.getLineNumber() < 0 ? ""
                : " (line " + location.getLineNumber() + ", column " + location.getColumnNumber() + ")";
    }

    private static String cleanMessage(XMLStreamException e) {
        String message = e.getMessage() == null ? e.toString() : e.getMessage();
        int marker = message.indexOf("Message: ");
        return marker >= 0 ? message.substring(marker + "Message: ".length()).strip() : message.strip();
    }

    private static String saxonMessage(SaxonApiException e) {
        String message = e.getMessage() == null ? e.toString() : e.getMessage();
        return e.getErrorCode() != null ? message + " [" + e.getErrorCode().getLocalName() + "]" : message;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
