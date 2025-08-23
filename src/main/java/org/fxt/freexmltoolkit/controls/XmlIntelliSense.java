package org.fxt.freexmltoolkit.controls;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intelligent auto-completion system for XML editing.
 * Provides context-sensitive suggestions for XML elements, attributes, and values.
 */
public class XmlIntelliSense {

    private static final Logger logger = LogManager.getLogger(XmlIntelliSense.class);

    // Common XML elements
    private static final String[] COMMON_XML_ELEMENTS = {
            "root", "item", "element", "value", "name", "id", "type", "data",
            "content", "description", "title", "text", "number", "date", "time"
    };

    // Common XML attributes
    private static final String[] COMMON_XML_ATTRIBUTES = {
            "id", "name", "type", "class", "value", "href", "src", "alt", "title",
            "lang", "dir", "style", "xmlns", "version", "encoding"
    };

    // XSD specific elements
    private static final String[] XSD_ELEMENTS = {
            "schema", "element", "attribute", "complexType", "simpleType",
            "sequence", "choice", "all", "group", "attributeGroup",
            "restriction", "extension", "union", "list", "enumeration",
            "pattern", "minInclusive", "maxInclusive", "minExclusive", "maxExclusive",
            "length", "minLength", "maxLength", "totalDigits", "fractionDigits",
            "whiteSpace", "import", "include", "redefine", "notation",
            "annotation", "documentation", "appinfo", "key", "keyref", "unique"
    };

    // XSD attributes
    private static final String[] XSD_ATTRIBUTES = {
            "name", "type", "ref", "minOccurs", "maxOccurs", "use", "default",
            "fixed", "form", "targetNamespace", "elementFormDefault",
            "attributeFormDefault", "blockDefault", "finalDefault", "version",
            "base", "memberTypes", "itemType", "value", "xpath", "refer",
            "schemaLocation", "namespace", "processContents", "public", "system"
    };

    // XSLT elements
    private static final String[] XSLT_ELEMENTS = {
            "stylesheet", "transform", "template", "apply-templates", "call-template",
            "for-each", "if", "choose", "when", "otherwise", "variable", "param",
            "copy", "copy-of", "value-of", "text", "element", "attribute",
            "comment", "processing-instruction", "sort", "number", "import",
            "include", "output", "key", "decimal-format", "namespace-alias",
            "preserve-space", "strip-space", "fallback", "message", "with-param"
    };

    // XSLT attributes
    private static final String[] XSLT_ATTRIBUTES = {
            "match", "name", "select", "test", "use", "priority", "mode",
            "method", "version", "encoding", "omit-xml-declaration", "standalone",
            "doctype-public", "doctype-system", "indent", "media-type",
            "disable-output-escaping", "xmlns:xsl", "exclude-result-prefixes",
            "extension-element-prefixes", "id", "lang", "data-type", "order",
            "case-order", "level", "count", "from", "value", "format",
            "grouping-separator", "grouping-size"
    };

    // Data types
    private static final String[] XML_SCHEMA_TYPES = {
            "string", "boolean", "decimal", "float", "double", "duration",
            "dateTime", "time", "date", "gYearMonth", "gYear", "gMonthDay",
            "gDay", "gMonth", "hexBinary", "base64Binary", "anyURI", "QName",
            "NOTATION", "normalizedString", "token", "language", "NMTOKEN",
            "NMTOKENS", "Name", "NCName", "ID", "IDREF", "IDREFS", "ENTITY",
            "ENTITIES", "integer", "nonPositiveInteger", "negativeInteger",
            "long", "int", "short", "byte", "nonNegativeInteger",
            "unsignedLong", "unsignedInt", "unsignedShort", "unsignedByte",
            "positiveInteger"
    };

    /**
     * Get context-sensitive completion suggestions
     */
    public List<CompletionItem> getCompletions(String textBeforeCursor, int caretPosition) {
        List<CompletionItem> completions = new ArrayList<>();

        // Determine context
        CompletionContext context = analyzeContext(textBeforeCursor);

        switch (context.getType()) {
            case ELEMENT_START:
                completions.addAll(getElementCompletions(context));
                break;
            case ATTRIBUTE_NAME:
                completions.addAll(getAttributeCompletions(context));
                break;
            case ATTRIBUTE_VALUE:
                completions.addAll(getAttributeValueCompletions(context));
                break;
            case TEXT_CONTENT:
                completions.addAll(getTextContentCompletions(context));
                break;
            case NAMESPACE:
                completions.addAll(getNamespaceCompletions(context));
                break;
            default:
                completions.addAll(getDefaultCompletions());
                break;
        }

        logger.debug("Generated {} completions for context: {}", completions.size(), context.getType());
        return completions;
    }

    /**
     * Analyze the context around the cursor
     */
    private CompletionContext analyzeContext(String textBeforeCursor) {
        CompletionContext context = new CompletionContext();

        // Check if we're in an element start context
        if (textBeforeCursor.endsWith("<")) {
            context.setType(CompletionContext.Type.ELEMENT_START);
            context.setDialect(detectXmlDialect(textBeforeCursor));
        }
        // Check if we're in attribute name context
        else if (isInAttributeNameContext(textBeforeCursor)) {
            context.setType(CompletionContext.Type.ATTRIBUTE_NAME);
            context.setElementName(extractCurrentElementName(textBeforeCursor));
            context.setDialect(detectXmlDialect(textBeforeCursor));
        }
        // Check if we're in attribute value context
        else if (isInAttributeValueContext(textBeforeCursor)) {
            context.setType(CompletionContext.Type.ATTRIBUTE_VALUE);
            context.setAttributeName(extractCurrentAttributeName(textBeforeCursor));
            context.setElementName(extractCurrentElementName(textBeforeCursor));
            context.setDialect(detectXmlDialect(textBeforeCursor));
        }
        // Check if we're in namespace context
        else if (isInNamespaceContext(textBeforeCursor)) {
            context.setType(CompletionContext.Type.NAMESPACE);
            context.setDialect(detectXmlDialect(textBeforeCursor));
        }
        // Default to text content
        else {
            context.setType(CompletionContext.Type.TEXT_CONTENT);
        }

        return context;
    }

    /**
     * Detect XML dialect (XSD, XSLT, regular XML)
     */
    private CompletionContext.Dialect detectXmlDialect(String text) {
        if (text.contains("xmlns:xsd") || text.contains("http://www.w3.org/2001/XMLSchema")) {
            return CompletionContext.Dialect.XSD;
        } else if (text.contains("xmlns:xsl") || text.contains("http://www.w3.org/1999/XSL/Transform")) {
            return CompletionContext.Dialect.XSLT;
        }
        return CompletionContext.Dialect.XML;
    }

    /**
     * Check if cursor is in attribute name context
     */
    private boolean isInAttributeNameContext(String text) {
        // Look for pattern: <element attribute|
        Pattern pattern = Pattern.compile("<\\w+(?:[:-]\\w+)*\\s+[\\w:-]*$");
        return pattern.matcher(text).find();
    }

    /**
     * Check if cursor is in attribute value context
     */
    private boolean isInAttributeValueContext(String text) {
        // Look for pattern: attribute="value|
        Pattern pattern = Pattern.compile("\\w+\\s*=\\s*[\"'][^\"']*$");
        return pattern.matcher(text).find();
    }

    /**
     * Check if cursor is in namespace context
     */
    private boolean isInNamespaceContext(String text) {
        // Look for xmlns: patterns
        return text.endsWith("xmlns:") || text.contains("xmlns:");
    }

    /**
     * Extract current element name from context
     */
    private String extractCurrentElementName(String text) {
        Pattern pattern = Pattern.compile("<(\\w+(?:[:-]\\w+)*)(?:\\s|$)");
        Matcher matcher = pattern.matcher(text);
        String elementName = "";
        while (matcher.find()) {
            elementName = matcher.group(1);
        }
        return elementName;
    }

    /**
     * Extract current attribute name from context
     */
    private String extractCurrentAttributeName(String text) {
        Pattern pattern = Pattern.compile("(\\w+)\\s*=\\s*[\"'][^\"']*$");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    /**
     * Get element completions based on context
     */
    private List<CompletionItem> getElementCompletions(CompletionContext context) {
        List<CompletionItem> completions = new ArrayList<>();

        String[] elements;
        switch (context.getDialect()) {
            case XSD:
                elements = XSD_ELEMENTS;
                break;
            case XSLT:
                elements = XSLT_ELEMENTS;
                break;
            default:
                elements = COMMON_XML_ELEMENTS;
                break;
        }

        for (String element : elements) {
            CompletionItem item = new CompletionItem(
                    element,
                    element + "></" + element + ">",
                    "XML Element",
                    "XML element: " + element
            );
            completions.add(item);
        }

        return completions;
    }

    /**
     * Get attribute completions based on context
     */
    private List<CompletionItem> getAttributeCompletions(CompletionContext context) {
        List<CompletionItem> completions = new ArrayList<>();

        String[] attributes;
        switch (context.getDialect()) {
            case XSD:
                attributes = XSD_ATTRIBUTES;
                break;
            case XSLT:
                attributes = XSLT_ATTRIBUTES;
                break;
            default:
                attributes = COMMON_XML_ATTRIBUTES;
                break;
        }

        for (String attribute : attributes) {
            CompletionItem item = new CompletionItem(
                    attribute,
                    attribute + "=\"\"",
                    "XML Attribute",
                    "XML attribute: " + attribute
            );
            completions.add(item);
        }

        return completions;
    }

    /**
     * Get attribute value completions based on context
     */
    private List<CompletionItem> getAttributeValueCompletions(CompletionContext context) {
        List<CompletionItem> completions = new ArrayList<>();

        String attributeName = context.getAttributeName();

        // Type attribute suggestions
        if ("type".equals(attributeName)) {
            for (String type : XML_SCHEMA_TYPES) {
                CompletionItem item = new CompletionItem(
                        type,
                        type,
                        "XML Schema Type",
                        "XML Schema data type: " + type
                );
                completions.add(item);
            }
        }
        // Boolean attributes
        else if (Arrays.asList("required", "optional", "fixed", "abstract").contains(attributeName)) {
            completions.add(new CompletionItem("true", "true", "Boolean", "Boolean value: true"));
            completions.add(new CompletionItem("false", "false", "Boolean", "Boolean value: false"));
        }
        // Common occurrence values
        else if ("minOccurs".equals(attributeName) || "maxOccurs".equals(attributeName)) {
            completions.add(new CompletionItem("0", "0", "Number", "Occurrence count: 0"));
            completions.add(new CompletionItem("1", "1", "Number", "Occurrence count: 1"));
            if ("maxOccurs".equals(attributeName)) {
                completions.add(new CompletionItem("unbounded", "unbounded", "Keyword", "No maximum limit"));
            }
        }
        // Use attribute values
        else if ("use".equals(attributeName)) {
            completions.add(new CompletionItem("required", "required", "Keyword", "Required attribute"));
            completions.add(new CompletionItem("optional", "optional", "Keyword", "Optional attribute"));
            completions.add(new CompletionItem("prohibited", "prohibited", "Keyword", "Prohibited attribute"));
        }

        return completions;
    }

    /**
     * Get text content completions
     */
    private List<CompletionItem> getTextContentCompletions(CompletionContext context) {
        List<CompletionItem> completions = new ArrayList<>();

        // XML entities
        completions.add(new CompletionItem("&lt;", "&lt;", "XML Entity", "Less than symbol"));
        completions.add(new CompletionItem("&gt;", "&gt;", "XML Entity", "Greater than symbol"));
        completions.add(new CompletionItem("&amp;", "&amp;", "XML Entity", "Ampersand symbol"));
        completions.add(new CompletionItem("&quot;", "&quot;", "XML Entity", "Quote symbol"));
        completions.add(new CompletionItem("&apos;", "&apos;", "XML Entity", "Apostrophe symbol"));

        return completions;
    }

    /**
     * Get namespace completions
     */
    private List<CompletionItem> getNamespaceCompletions(CompletionContext context) {
        List<CompletionItem> completions = new ArrayList<>();

        // Common namespace URIs
        completions.add(new CompletionItem("xsd", "xsd=\"http://www.w3.org/2001/XMLSchema\"",
                "Namespace", "XML Schema namespace"));
        completions.add(new CompletionItem("xsl", "xsl=\"http://www.w3.org/1999/XSL/Transform\"",
                "Namespace", "XSLT namespace"));
        completions.add(new CompletionItem("xsi", "xsi=\"http://www.w3.org/2001/XMLSchema-instance\"",
                "Namespace", "XML Schema Instance namespace"));

        return completions;
    }

    /**
     * Get default completions when context is unclear
     */
    private List<CompletionItem> getDefaultCompletions() {
        List<CompletionItem> completions = new ArrayList<>();

        // Basic XML structure
        completions.add(new CompletionItem("<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>", "XML Declaration", "Standard XML declaration"));
        completions.add(new CompletionItem("<!-- -->", "<!-- -->", "Comment", "XML comment"));
        completions.add(new CompletionItem("<![CDATA[]]>", "<![CDATA[]]>", "CDATA", "Character data section"));

        return completions;
    }

    /**
     * Completion context information
     */
    public static class CompletionContext {
        public enum Type {
            ELEMENT_START, ATTRIBUTE_NAME, ATTRIBUTE_VALUE, TEXT_CONTENT, NAMESPACE
        }

        public enum Dialect {
            XML, XSD, XSLT
        }

        private Type type;
        private Dialect dialect = Dialect.XML;
        private String elementName = "";
        private String attributeName = "";

        public Type getType() {
            return type;
        }

        public void setType(Type type) {
            this.type = type;
        }

        public Dialect getDialect() {
            return dialect;
        }

        public void setDialect(Dialect dialect) {
            this.dialect = dialect;
        }

        public String getElementName() {
            return elementName;
        }

        public void setElementName(String elementName) {
            this.elementName = elementName;
        }

        public String getAttributeName() {
            return attributeName;
        }

        public void setAttributeName(String attributeName) {
            this.attributeName = attributeName;
        }
    }

    /**
     * Completion item with detailed information
     *
     * @param text        Display text
     * @param insertText  Text to insert
     * @param type        Type of completion
     * @param description Detailed description
     */
        public record CompletionItem(String text, String insertText, String type, String description) {

        @Override
            public String toString() {
                return text + " (" + type + ")";
            }
        }
}