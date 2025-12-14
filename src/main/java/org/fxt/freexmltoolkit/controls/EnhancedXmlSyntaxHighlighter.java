package org.fxt.freexmltoolkit.controls;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enhanced XML Syntax Highlighter with improved performance and semantic awareness.
 * Provides advanced highlighting for XML, XSD, XSLT, and other XML-based formats.
 *
 * @deprecated Use {@link org.fxt.freexmltoolkit.controls.v2.editor.managers.SyntaxHighlightManagerV2} instead.
 *             This V1 class will be removed in a future version.
 */
@Deprecated(since = "2.0", forRemoval = true)
public class EnhancedXmlSyntaxHighlighter {

    private static final Logger logger = LogManager.getLogger(EnhancedXmlSyntaxHighlighter.class);

    // Enhanced XML syntax patterns
    private static final String[] XML_KEYWORDS = {
            "xml", "version", "encoding", "standalone"
    };

    private static final String[] XSD_KEYWORDS = {
            "schema", "element", "attribute", "complexType", "simpleType",
            "sequence", "choice", "all", "group", "attributeGroup",
            "restriction", "extension", "union", "list", "import",
            "include", "redefine", "notation", "annotation", "documentation",
            "appinfo", "key", "keyref", "unique", "selector", "field"
    };

    private static final String[] XSLT_KEYWORDS = {
            "stylesheet", "transform", "template", "apply-templates", "call-template",
            "for-each", "if", "choose", "when", "otherwise", "variable", "param",
            "copy", "copy-of", "value-of", "text", "element", "attribute",
            "comment", "processing-instruction", "sort", "number", "import", "include",
            "output", "key", "decimal-format", "namespace-alias", "preserve-space",
            "strip-space", "fallback"
    };

    // Comprehensive regex patterns
    private static final Pattern XML_DECLARATION = Pattern.compile("(<\\?xml[^>]*\\?>)");
    private static final Pattern XML_COMMENT = Pattern.compile("(<!--[^>]*-->)");
    private static final Pattern XML_CDATA = Pattern.compile("(<\\!\\[CDATA\\[.*?\\]\\]>)", Pattern.DOTALL);
    private static final Pattern XML_DOCTYPE = Pattern.compile("(<!DOCTYPE[^>]*>)", Pattern.CASE_INSENSITIVE);
    private static final Pattern XML_PROCESSING_INSTRUCTION = Pattern.compile("(<\\?[^>]*\\?>)");

    // Enhanced tag patterns
    private static final Pattern XML_TAG_START = Pattern.compile("(</?\\s*)(\\w+(?:[:-]\\w+)*)([^>]*)(/?\\s*>)");
    private static final Pattern XML_NAMESPACE = Pattern.compile("(xmlns(?::[^\\s=]+)?)(\\s*=\\s*)([\"'][^\"']*[\"'])");
    private static final Pattern XML_ATTRIBUTE = Pattern.compile("(\\w+(?:[:-]\\w+)*)(\\s*=\\s*)([\"'][^\"']*[\"'])");

    // XPath and expression patterns
    private static final Pattern XPATH_EXPRESSION = Pattern.compile("(@[\\w-]+|//|/|\\[|\\]|\\(|\\)|\\.|\\*|text\\(\\)|node\\(\\)|comment\\(\\))");

    // Text content pattern
    private static final Pattern XML_TEXT_CONTENT = Pattern.compile("(>[^<]*<)", Pattern.DOTALL);

    // Semantic patterns for enhanced highlighting
    private static final Pattern XSD_TYPE_REFERENCE = Pattern.compile("type\\s*=\\s*[\"']([^\"']*)[\"']");
    private static final Pattern XSD_MINOCCURS_MAXOCCURS = Pattern.compile("(minOccurs|maxOccurs)\\s*=\\s*[\"']([^\"']*)[\"']");

    /**
     * Compute comprehensive XML syntax highlighting
     */
    public static StyleSpans<Collection<String>> computeHighlighting(String text) {
        if (text == null || text.isEmpty()) {
            StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
            spansBuilder.add(Collections.emptyList(), 0);
            return spansBuilder.create();
        }

        try {
            return computeAdvancedHighlighting(text);
        } catch (Exception e) {
            logger.warn("Advanced highlighting failed, falling back to basic highlighting", e);
            return computeBasicHighlighting(text);
        }
    }

    /**
     * Compute advanced semantic XML highlighting
     */
    private static StyleSpans<Collection<String>> computeAdvancedHighlighting(String text) {
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        int lastKwEnd = 0;

        // Process the text in multiple passes for different types of syntax

        // Pass 1: XML declarations, comments, CDATA, DOCTYPE
        lastKwEnd = highlightSpecialElements(text, spansBuilder, lastKwEnd);

        // Pass 2: XML tags with namespace awareness
        lastKwEnd = highlightXmlTags(text, spansBuilder, lastKwEnd);

        // Pass 3: Attributes with semantic awareness
        lastKwEnd = highlightAttributes(text, spansBuilder, lastKwEnd);

        // Pass 4: Text content
        highlightTextContent(text, spansBuilder, lastKwEnd);

        return spansBuilder.create();
    }

    /**
     * Highlight special XML elements (declarations, comments, etc.)
     */
    private static int highlightSpecialElements(String text, StyleSpansBuilder<Collection<String>> spansBuilder, int lastEnd) {
        int currentEnd = lastEnd;

        // XML Declaration
        Matcher xmlDeclMatcher = XML_DECLARATION.matcher(text);
        while (xmlDeclMatcher.find()) {
            if (xmlDeclMatcher.start() >= currentEnd) {
                spansBuilder.add(Collections.emptyList(), xmlDeclMatcher.start() - currentEnd);
                spansBuilder.add(Collections.singleton("xml-declaration"), xmlDeclMatcher.end() - xmlDeclMatcher.start());
                currentEnd = xmlDeclMatcher.end();
            }
        }

        // XML Comments
        Matcher commentMatcher = XML_COMMENT.matcher(text);
        while (commentMatcher.find()) {
            if (commentMatcher.start() >= currentEnd) {
                spansBuilder.add(Collections.emptyList(), commentMatcher.start() - currentEnd);
                spansBuilder.add(Collections.singleton("comment"), commentMatcher.end() - commentMatcher.start());
                currentEnd = commentMatcher.end();
            }
        }

        // CDATA Sections
        Matcher cdataMatcher = XML_CDATA.matcher(text);
        while (cdataMatcher.find()) {
            if (cdataMatcher.start() >= currentEnd) {
                spansBuilder.add(Collections.emptyList(), cdataMatcher.start() - currentEnd);
                spansBuilder.add(Collections.singleton("cdata"), cdataMatcher.end() - cdataMatcher.start());
                currentEnd = cdataMatcher.end();
            }
        }

        // DOCTYPE Declaration
        Matcher doctypeMatcher = XML_DOCTYPE.matcher(text);
        while (doctypeMatcher.find()) {
            if (doctypeMatcher.start() >= currentEnd) {
                spansBuilder.add(Collections.emptyList(), doctypeMatcher.start() - currentEnd);
                spansBuilder.add(Collections.singleton("doctype"), doctypeMatcher.end() - doctypeMatcher.start());
                currentEnd = doctypeMatcher.end();
            }
        }

        // Processing Instructions
        Matcher piMatcher = XML_PROCESSING_INSTRUCTION.matcher(text);
        while (piMatcher.find()) {
            if (piMatcher.start() >= currentEnd) {
                spansBuilder.add(Collections.emptyList(), piMatcher.start() - currentEnd);
                spansBuilder.add(Collections.singleton("processing-instruction"), piMatcher.end() - piMatcher.start());
                currentEnd = piMatcher.end();
            }
        }

        return currentEnd;
    }

    /**
     * Highlight XML tags with namespace awareness
     */
    private static int highlightXmlTags(String text, StyleSpansBuilder<Collection<String>> spansBuilder, int lastEnd) {
        Matcher tagMatcher = XML_TAG_START.matcher(text);
        int currentEnd = lastEnd;

        while (tagMatcher.find()) {
            if (tagMatcher.start() >= currentEnd) {
                // Add any text before the tag
                if (tagMatcher.start() > currentEnd) {
                    spansBuilder.add(Collections.emptyList(), tagMatcher.start() - currentEnd);
                }

                String tagBrackets = tagMatcher.group(1); // < or </
                String tagName = tagMatcher.group(2);     // element name
                String attributes = tagMatcher.group(3);  // attributes
                String closeBracket = tagMatcher.group(4); // > or />

                // Highlight tag brackets
                spansBuilder.add(Collections.singleton("tagmark"), tagBrackets.length());

                // Highlight tag name with semantic awareness
                String tagStyle = getSemanticTagStyle(tagName);
                spansBuilder.add(Collections.singleton(tagStyle), tagName.length());

                // Highlight attributes (will be processed separately)
                if (!attributes.trim().isEmpty()) {
                    spansBuilder.add(Collections.emptyList(), attributes.length());
                }

                // Highlight closing bracket
                spansBuilder.add(Collections.singleton("tagmark"), closeBracket.length());

                currentEnd = tagMatcher.end();
            }
        }

        return currentEnd;
    }

    /**
     * Get semantic style for tag based on XML dialect
     */
    private static String getSemanticTagStyle(String tagName) {
        // Check for XSD elements
        for (String xsdKeyword : XSD_KEYWORDS) {
            if (xsdKeyword.equals(tagName) || tagName.endsWith(":" + xsdKeyword)) {
                return "xsd-element";
            }
        }

        // Check for XSLT elements
        for (String xsltKeyword : XSLT_KEYWORDS) {
            if (xsltKeyword.equals(tagName) || tagName.endsWith(":" + xsltKeyword)) {
                return "xslt-element";
            }
        }

        // Check for namespace prefixes
        if (tagName.contains(":")) {
            return "namespaced-element";
        }

        // Default XML element
        return "anytag";
    }

    /**
     * Highlight attributes with semantic awareness
     */
    private static int highlightAttributes(String text, StyleSpansBuilder<Collection<String>> spansBuilder, int lastEnd) {
        // Namespace declarations
        Matcher namespaceMatcher = XML_NAMESPACE.matcher(text);
        while (namespaceMatcher.find()) {
            if (namespaceMatcher.start() >= lastEnd) {
                String attrName = namespaceMatcher.group(1);
                String equals = namespaceMatcher.group(2);
                String attrValue = namespaceMatcher.group(3);

                spansBuilder.add(Collections.singleton("namespace-declaration"), attrName.length());
                spansBuilder.add(Collections.singleton("paren"), equals.length());
                spansBuilder.add(Collections.singleton("namespace-uri"), attrValue.length());
            }
        }

        // Regular attributes
        Matcher attributeMatcher = XML_ATTRIBUTE.matcher(text);
        while (attributeMatcher.find()) {
            if (attributeMatcher.start() >= lastEnd) {
                String attrName = attributeMatcher.group(1);
                String equals = attributeMatcher.group(2);
                String attrValue = attributeMatcher.group(3);

                // Semantic attribute styling
                String attrStyle = getSemanticAttributeStyle(attrName, attrValue);
                spansBuilder.add(Collections.singleton(attrStyle), attrName.length());
                spansBuilder.add(Collections.singleton("paren"), equals.length());

                String valueStyle = getSemanticValueStyle(attrName, attrValue);
                spansBuilder.add(Collections.singleton(valueStyle), attrValue.length());
            }
        }

        return lastEnd;
    }

    /**
     * Get semantic style for attributes
     */
    private static String getSemanticAttributeStyle(String attrName, String attrValue) {
        // XSD type references
        if ("type".equals(attrName)) {
            return "type-reference";
        }

        // XSD occurrence indicators
        if ("minOccurs".equals(attrName) || "maxOccurs".equals(attrName)) {
            return "occurrence-indicator";
        }

        // Namespace attributes
        if (attrName.startsWith("xmlns")) {
            return "namespace-declaration";
        }

        // ID attributes
        if ("id".equals(attrName) || "ref".equals(attrName)) {
            return "id-reference";
        }

        return "attribute";
    }

    /**
     * Get semantic style for attribute values
     */
    private static String getSemanticValueStyle(String attrName, String attrValue) {
        // Boolean values
        if ("true".equals(attrValue) || "false".equals(attrValue)) {
            return "boolean-value";
        }

        // Numeric values
        if (attrValue.matches("\"?\\d+\"?")) {
            return "numeric-value";
        }

        // URI values
        if (attrValue.contains("http://") || attrValue.contains("https://") || attrValue.contains("urn:")) {
            return "uri-value";
        }

        // XPath expressions
        if (attrName.equals("select") || attrName.equals("test") || attrName.equals("match")) {
            return "xpath-expression";
        }

        return "avalue";
    }

    /**
     * Highlight text content
     */
    private static void highlightTextContent(String text, StyleSpansBuilder<Collection<String>> spansBuilder, int lastEnd) {
        // Simple text content highlighting
        if (lastEnd < text.length()) {
            spansBuilder.add(Collections.emptyList(), text.length() - lastEnd);
        }
    }

    /**
     * Compute basic highlighting for fallback
     */
    public static StyleSpans<Collection<String>> computeBasicHighlighting(String text) {
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        // Basic tag highlighting
        Pattern basicTagPattern = Pattern.compile("(<[^>]*>)");
        Matcher matcher = basicTagPattern.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastEnd);
            spansBuilder.add(Collections.singleton("anytag"), matcher.end() - matcher.start());
            lastEnd = matcher.end();
        }

        spansBuilder.add(Collections.emptyList(), text.length() - lastEnd);
        return spansBuilder.create();
    }

    /**
     * Compute streaming highlighting for large files
     */
    public static StyleSpans<Collection<String>> computeStreamingHighlighting(String text, int chunkSize) {
        if (text.length() <= chunkSize) {
            return computeAdvancedHighlighting(text);
        }

        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        // Process in chunks
        int processed = 0;
        while (processed < text.length()) {
            int chunkEnd = Math.min(processed + chunkSize, text.length());

            // Find safe break point (end of XML tag)
            if (chunkEnd < text.length()) {
                int safeBreak = text.lastIndexOf('>', chunkEnd);
                if (safeBreak > processed) {
                    chunkEnd = safeBreak + 1;
                }
            }

            String chunk = text.substring(processed, chunkEnd);
            StyleSpans<Collection<String>> chunkHighlighting = computeAdvancedHighlighting(chunk);

            // Add chunk highlighting to main builder
            for (var span : chunkHighlighting) {
                spansBuilder.add(span.getStyle(), span.getLength());
            }

            processed = chunkEnd;
        }

        return spansBuilder.create();
    }
}