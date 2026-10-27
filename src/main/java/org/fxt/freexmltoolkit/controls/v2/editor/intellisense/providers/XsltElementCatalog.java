package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.providers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The XSLT 3.0 instruction and declaration elements with their attributes, used for XSLT
 * IntelliSense (element names, attribute names, attribute value suggestions).
 *
 * <p>Every XSLT element additionally accepts the standard attributes ({@code use-when},
 * {@code expand-text}, …), see {@link #STANDARD_ATTRIBUTES}.</p>
 */
public final class XsltElementCatalog {

    /** The XSLT namespace URI. */
    public static final String XSLT_NAMESPACE = "http://www.w3.org/1999/XSL/Transform";

    /**
     * One attribute of an XSLT element.
     *
     * @param name        the attribute name
     * @param required    whether the specification requires it
     * @param description short help text
     * @param values      suggested values (empty for free text such as XPath expressions)
     */
    public record XsltAttribute(String name, boolean required, String description, List<String> values) {
    }

    /**
     * One XSLT element.
     *
     * @param name        the local name
     * @param description short help text
     * @param attributes  the element-specific attributes, in suggestion order
     */
    public record XsltElement(String name, String description, List<XsltAttribute> attributes) {
    }

    private static final List<String> YES_NO = List.of("yes", "no");
    private static final List<String> VERSIONS = List.of("3.0", "2.0", "1.0");
    private static final List<String> VALIDATION = List.of("strict", "lax", "preserve", "strip");
    private static final List<String> VISIBILITY = List.of("public", "private", "final", "abstract");
    private static final List<String> ACCEPT_VISIBILITY = List.of("public", "private", "final", "abstract", "hidden");
    private static final List<String> COMPONENTS = List.of("template", "function", "attribute-set", "variable", "mode", "*");
    private static final List<String> ORDER = List.of("ascending", "descending");
    private static final List<String> CASE_ORDER = List.of("upper-first", "lower-first");
    private static final List<String> DATA_TYPE = List.of("text", "number");
    private static final List<String> CONTEXT_USE = List.of("required", "optional", "absent");
    private static final List<String> METHOD = List.of("xml", "html", "xhtml", "text", "json", "adaptive");
    private static final List<String> STANDALONE = List.of("yes", "no", "omit");
    private static final List<String> TYPE_ANNOTATIONS = List.of("preserve", "strip", "unspecified");

    /** Attributes allowed on every XSLT element (XSLT 3.0 §3.5 standard attributes). */
    public static final List<XsltAttribute> STANDARD_ATTRIBUTES = List.of(
            opt("use-when", "Static XPath condition: the element is ignored when it is false"),
            opt("expand-text", "Enables text value templates ({…}) in descendant text nodes", YES_NO),
            opt("xpath-default-namespace", "Default namespace for unprefixed element names in XPath"),
            opt("exclude-result-prefixes", "Namespace prefixes not copied to the result (#all, #default)"),
            opt("extension-element-prefixes", "Prefixes that designate extension instructions"),
            opt("version", "XSLT version for this element and its descendants", VERSIONS),
            opt("default-collation", "Default collation URIs for this element"),
            opt("default-mode", "Default mode for apply-templates and templates inside this element"),
            opt("default-validation", "Default validation of new element and attribute nodes",
                    List.of("preserve", "strip")));

    private static final List<XsltAttribute> SERIALIZATION = List.of(
            opt("method", "Output method", METHOD),
            opt("indent", "Indent the output", YES_NO),
            opt("encoding", "Character encoding, e.g. UTF-8"),
            opt("omit-xml-declaration", "Omit the XML declaration", YES_NO),
            opt("standalone", "Standalone declaration", STANDALONE),
            opt("doctype-public", "Public identifier of the DOCTYPE"),
            opt("doctype-system", "System identifier of the DOCTYPE"),
            opt("media-type", "MIME type of the output"),
            opt("cdata-section-elements", "Elements whose text is written as CDATA sections"),
            opt("html-version", "HTML version for the html/xhtml methods"),
            opt("include-content-type", "Add a meta content-type element (html/xhtml)", YES_NO),
            opt("escape-uri-attributes", "Escape URI attribute values (html/xhtml)", YES_NO),
            opt("byte-order-mark", "Write a byte order mark", YES_NO),
            opt("normalization-form", "Unicode normalization", List.of("NFC", "NFD", "NFKC", "NFKD", "fully-normalized", "none")),
            opt("undeclare-prefixes", "Undeclare namespace prefixes (XML 1.1)", YES_NO),
            opt("use-character-maps", "Character maps applied during serialization"),
            opt("suppress-indentation", "Elements whose content is not indented"),
            opt("item-separator", "String written between items of the result sequence"),
            opt("build-tree", "Build a result tree instead of a raw sequence", YES_NO),
            opt("allow-duplicate-names", "Allow duplicate keys in JSON output", YES_NO),
            opt("json-node-output-method", "Serialization method for nodes inside JSON output", METHOD),
            opt("parameter-document", "URI of a serialization parameter document"));

    private static final Map<String, XsltElement> ELEMENTS = new LinkedHashMap<>();

    static {
        // Declarations
        el("stylesheet", "Root element of an XSLT stylesheet",
                req("version", "XSLT version of the stylesheet", VERSIONS),
                opt("id", "Identifier of the stylesheet"),
                opt("input-type-annotations", "Keep or strip type annotations of the input", TYPE_ANNOTATIONS));
        el("transform", "Root element of an XSLT stylesheet (synonym of xsl:stylesheet)",
                req("version", "XSLT version of the stylesheet", VERSIONS),
                opt("id", "Identifier of the stylesheet"),
                opt("input-type-annotations", "Keep or strip type annotations of the input", TYPE_ANNOTATIONS));
        el("package", "Root element of an XSLT 3.0 package",
                opt("name", "Absolute URI naming the package"),
                opt("package-version", "Version of the package"),
                req("version", "XSLT version", VERSIONS),
                opt("input-type-annotations", "Keep or strip type annotations of the input", TYPE_ANNOTATIONS),
                opt("declared-modes", "All modes must be declared with xsl:mode", YES_NO));
        el("use-package", "Uses the components of another package",
                req("name", "Name (URI) of the used package"),
                opt("package-version", "Required package version range"));
        el("accept", "Changes the visibility of components accepted from a used package",
                req("component", "Kind of component", COMPONENTS),
                req("names", "Names of the components (wildcards allowed)"),
                req("visibility", "New visibility", ACCEPT_VISIBILITY));
        el("expose", "Changes the visibility of components exposed by the package",
                req("component", "Kind of component", COMPONENTS),
                req("names", "Names of the components (wildcards allowed)"),
                req("visibility", "New visibility", ACCEPT_VISIBILITY));
        el("override", "Overrides components of a used package");
        el("include", "Includes another stylesheet module",
                req("href", "URI of the included module"));
        el("import", "Imports another stylesheet module (lower import precedence)",
                req("href", "URI of the imported module"));
        el("import-schema", "Imports a schema for schema-aware processing",
                opt("namespace", "Target namespace of the schema"),
                opt("schema-location", "URI of the schema document"));
        el("template", "Template rule (match) or named template (name)",
                opt("match", "Pattern selecting the nodes this template handles"),
                opt("name", "Name for xsl:call-template"),
                opt("mode", "Mode(s) the template rule belongs to (#all, #default, #unnamed)"),
                opt("priority", "Priority among matching template rules"),
                opt("as", "Sequence type of the result"),
                opt("visibility", "Visibility of a named template in a package", VISIBILITY));
        el("function", "Stylesheet function, callable from XPath",
                req("name", "Prefixed name of the function"),
                opt("as", "Sequence type of the result"),
                opt("visibility", "Visibility in a package", VISIBILITY),
                opt("streamability", "Streamability category",
                        List.of("unclassified", "absorbing", "inspection", "filter", "shallow-descent", "deep-descent", "ascent")),
                opt("override-extension-function", "Overrides a vendor extension function of the same name", YES_NO),
                opt("new-each-time", "Whether new nodes are created per call", List.of("yes", "no", "maybe")),
                opt("cache", "Allow the processor to cache results", YES_NO));
        el("variable", "Declares a variable",
                req("name", "Name of the variable"),
                opt("select", "XPath expression giving the value"),
                opt("as", "Sequence type of the value"),
                opt("static", "Static variable (evaluated at compile time)", YES_NO),
                opt("visibility", "Visibility of a global variable in a package", VISIBILITY));
        el("param", "Declares a stylesheet, template or function parameter",
                req("name", "Name of the parameter"),
                opt("select", "XPath expression giving the default value"),
                opt("as", "Sequence type of the value"),
                opt("required", "Whether a value must be supplied", YES_NO),
                opt("tunnel", "Tunnel parameter", YES_NO),
                opt("static", "Static parameter (set at compile time)", YES_NO));
        el("key", "Declares a key for the key() function",
                req("name", "Name of the key"),
                req("match", "Pattern selecting the indexed nodes"),
                opt("use", "XPath expression giving the key value"),
                opt("composite", "Treat the use value as a composite key", YES_NO),
                opt("collation", "Collation URI for comparing key values"));
        el("output", "Declares serialization parameters of the result",
                withSerialization(opt("name", "Name of the output definition (for result-document format)"),
                        opt("version", "Version of the output method")));
        el("output-character", "Maps a character to a string in a character map",
                req("character", "The character to replace"),
                req("string", "The replacement string"));
        el("character-map", "Declares a character map for serialization",
                req("name", "Name of the character map"),
                opt("use-character-maps", "Character maps included in this one"));
        el("decimal-format", "Declares a decimal format for format-number()",
                opt("name", "Name of the decimal format"),
                opt("decimal-separator", "Decimal separator character"),
                opt("grouping-separator", "Grouping separator character"),
                opt("infinity", "String representing infinity"),
                opt("minus-sign", "Minus sign character"),
                opt("exponent-separator", "Exponent separator character"),
                opt("NaN", "String representing NaN"),
                opt("percent", "Percent character"),
                opt("per-mille", "Per-mille character"),
                opt("zero-digit", "Zero digit character"),
                opt("digit", "Digit placeholder character in the picture"),
                opt("pattern-separator", "Separator of positive and negative sub-pictures"));
        el("namespace-alias", "Maps a stylesheet namespace to a result namespace",
                req("stylesheet-prefix", "Prefix used in the stylesheet (#default)"),
                req("result-prefix", "Prefix used in the result (#default)"));
        el("attribute-set", "Declares a named set of attributes",
                req("name", "Name of the attribute set"),
                opt("use-attribute-sets", "Attribute sets included in this one"),
                opt("visibility", "Visibility in a package", VISIBILITY),
                opt("streamable", "Streamable attribute set", YES_NO));
        el("mode", "Declares the properties of a mode",
                opt("name", "Name of the mode (omit for the unnamed mode)"),
                opt("streamable", "Streamable mode", YES_NO),
                opt("use-accumulators", "Accumulators applicable to documents processed in this mode"),
                opt("on-no-match", "Built-in behaviour when no template matches",
                        List.of("text-only-copy", "shallow-copy", "deep-copy", "shallow-skip", "deep-skip", "fail")),
                opt("on-multiple-match", "Behaviour when several templates match", List.of("use-last", "fail")),
                opt("warning-on-no-match", "Warn when no template matches", YES_NO),
                opt("warning-on-multiple-match", "Warn when several templates match", YES_NO),
                opt("typed", "Typed or untyped nodes", List.of("yes", "no", "strict", "lax", "unspecified")),
                opt("visibility", "Visibility in a package", List.of("public", "private", "final")));
        el("accumulator", "Declares an accumulator",
                req("name", "Name of the accumulator"),
                req("initial-value", "XPath expression giving the initial value"),
                opt("as", "Sequence type of the value"),
                opt("streamable", "Streamable accumulator", YES_NO));
        el("accumulator-rule", "Rule updating an accumulator value",
                req("match", "Pattern selecting the nodes"),
                opt("phase", "Fire on the start or end of the node", List.of("start", "end")),
                opt("select", "XPath expression giving the new value ($value is the old one)"));
        el("global-context-item", "Declares the global context item",
                opt("as", "Required type of the global context item"),
                opt("use", "Whether a global context item is needed", CONTEXT_USE));
        el("preserve-space", "Elements whose whitespace-only text nodes are kept",
                req("elements", "Element names (* allowed)"));
        el("strip-space", "Elements whose whitespace-only text nodes are removed",
                req("elements", "Element names (* allowed)"));

        // Instructions
        el("apply-templates", "Applies template rules to the selected nodes",
                opt("select", "XPath expression selecting the nodes (default: child::node())"),
                opt("mode", "Mode to use (#current, #default, #unnamed)"));
        el("call-template", "Calls a named template",
                req("name", "Name of the template"));
        el("apply-imports", "Applies imported template rules to the context node");
        el("next-match", "Applies the next matching template rule");
        el("for-each", "Processes each item of a sequence",
                req("select", "XPath expression selecting the items"));
        el("for-each-group", "Groups items and processes each group",
                req("select", "XPath expression selecting the population"),
                opt("group-by", "Grouping key expression"),
                opt("group-adjacent", "Grouping key of adjacent items"),
                opt("group-starting-with", "Pattern starting a new group"),
                opt("group-ending-with", "Pattern ending a group"),
                opt("composite", "Treat the grouping key as composite", YES_NO),
                opt("collation", "Collation URI for comparing keys"));
        el("iterate", "Iterates over a sequence with parameters passed between iterations",
                req("select", "XPath expression selecting the items"));
        el("next-iteration", "Continues xsl:iterate with new parameter values");
        el("break", "Leaves xsl:iterate",
                opt("select", "XPath expression giving the result"));
        el("on-completion", "Evaluated when xsl:iterate has processed all items",
                opt("select", "XPath expression giving the result"));
        el("if", "Conditional processing",
                req("test", "XPath condition"));
        el("choose", "Selects one of several alternatives");
        el("when", "Branch of xsl:choose",
                req("test", "XPath condition"));
        el("otherwise", "Default branch of xsl:choose");
        el("value-of", "Creates a text node from an expression",
                opt("select", "XPath expression giving the value"),
                opt("separator", "Separator between items"),
                opt("disable-output-escaping", "Write the text unescaped", YES_NO));
        el("sequence", "Returns the value of an expression",
                opt("select", "XPath expression giving the result"));
        el("copy", "Shallow copy of the context item",
                opt("select", "XPath expression selecting the item to copy"),
                opt("copy-namespaces", "Copy the namespace nodes", YES_NO),
                opt("inherit-namespaces", "Children inherit the namespace nodes", YES_NO),
                opt("use-attribute-sets", "Attribute sets added to the copied element"),
                opt("type", "Type annotation of the copy"),
                opt("validation", "Validation of the copy", VALIDATION));
        el("copy-of", "Deep copy of the selected items",
                req("select", "XPath expression selecting the items"),
                opt("copy-accumulators", "Copy accumulator values", YES_NO),
                opt("copy-namespaces", "Copy the namespace nodes", YES_NO),
                opt("type", "Type annotation of the copy"),
                opt("validation", "Validation of the copy", VALIDATION));
        el("element", "Creates an element with a computed name",
                req("name", "Element name (attribute value template)"),
                opt("namespace", "Namespace URI (attribute value template)"),
                opt("inherit-namespaces", "Children inherit the namespace nodes", YES_NO),
                opt("use-attribute-sets", "Attribute sets added to the element"),
                opt("type", "Type annotation"),
                opt("validation", "Validation of the element", VALIDATION));
        el("attribute", "Creates an attribute",
                req("name", "Attribute name (attribute value template)"),
                opt("namespace", "Namespace URI (attribute value template)"),
                opt("select", "XPath expression giving the value"),
                opt("separator", "Separator between items"),
                opt("type", "Type annotation"),
                opt("validation", "Validation of the attribute", VALIDATION));
        el("text", "Creates a text node from literal text",
                opt("disable-output-escaping", "Write the text unescaped", YES_NO));
        el("comment", "Creates a comment node",
                opt("select", "XPath expression giving the content"));
        el("processing-instruction", "Creates a processing instruction",
                req("name", "Target name (attribute value template)"),
                opt("select", "XPath expression giving the content"));
        el("namespace", "Creates a namespace node",
                req("name", "Namespace prefix (attribute value template)"),
                opt("select", "XPath expression giving the namespace URI"));
        el("document", "Creates a document node",
                opt("validation", "Validation of the document", VALIDATION),
                opt("type", "Type annotation"));
        el("result-document", "Writes a secondary result document",
                withSerialization(opt("href", "URI of the result document (attribute value template)"),
                        opt("format", "Name of an xsl:output declaration"),
                        opt("validation", "Validation of the result", VALIDATION),
                        opt("type", "Type annotation of the document node"),
                        opt("output-version", "Version of the output method")));
        el("source-document", "Reads a document, optionally streamed",
                req("href", "URI of the document (attribute value template)"),
                opt("streamable", "Process the document by streaming", YES_NO),
                opt("use-accumulators", "Accumulators applicable to the document"),
                opt("validation", "Validation of the document", VALIDATION),
                opt("type", "Type annotation"));
        el("number", "Inserts a formatted number",
                opt("value", "XPath expression giving the number"),
                opt("select", "Node to number (default: context node)"),
                opt("level", "How nodes are counted", List.of("single", "multiple", "any")),
                opt("count", "Pattern of the nodes counted"),
                opt("from", "Pattern where counting starts"),
                opt("format", "Format token, e.g. 1, a, i, 01"),
                opt("lang", "Language for alphabetic numbering"),
                opt("letter-value", "Alphabetic or traditional numbering", List.of("alphabetic", "traditional")),
                opt("ordinal", "Ordinal numbering (e.g. yes, or a language-specific value)"),
                opt("start-at", "Number of the first item"),
                opt("grouping-separator", "Grouping separator character"),
                opt("grouping-size", "Size of digit groups"));
        el("sort", "Sort key for the enclosing instruction",
                opt("select", "XPath expression giving the sort key"),
                opt("order", "Sort order", ORDER),
                opt("data-type", "Compare as text or numbers", DATA_TYPE),
                opt("case-order", "Upper or lower case first", CASE_ORDER),
                opt("lang", "Language of the sort keys"),
                opt("collation", "Collation URI"),
                opt("stable", "Keep the input order of equal items", YES_NO));
        el("perform-sort", "Sorts a sequence",
                opt("select", "XPath expression giving the sequence"));
        el("with-param", "Passes a parameter value",
                req("name", "Name of the parameter"),
                opt("select", "XPath expression giving the value"),
                opt("as", "Sequence type of the value"),
                opt("tunnel", "Tunnel parameter", YES_NO));
        el("analyze-string", "Processes a string with a regular expression",
                req("select", "XPath expression giving the string"),
                req("regex", "Regular expression (attribute value template)"),
                opt("flags", "Regex flags, e.g. i, m, s, x"));
        el("matching-substring", "Processes each substring matching the regex");
        el("non-matching-substring", "Processes each substring not matching the regex");
        el("message", "Writes a message, optionally terminating the transformation",
                opt("select", "XPath expression giving the message"),
                opt("terminate", "Terminate the transformation", YES_NO),
                opt("error-code", "Error code reported when terminating"));
        el("assert", "Asserts a condition; raises an error when it is false",
                req("test", "XPath condition"),
                opt("select", "XPath expression giving the error message"),
                opt("error-code", "Error code"));
        el("try", "Evaluates with error recovery",
                opt("select", "XPath expression to evaluate"),
                opt("rollback-output", "Discard partial output after an error", YES_NO));
        el("catch", "Handles errors of xsl:try",
                opt("errors", "Error codes handled (* for all)"),
                opt("select", "XPath expression giving the result"));
        el("fallback", "Fallback for an unsupported instruction");
        el("evaluate", "Evaluates a dynamically constructed XPath expression",
                req("xpath", "XPath expression giving the expression to evaluate"),
                opt("as", "Sequence type of the result"),
                opt("base-uri", "Static base URI (attribute value template)"),
                opt("with-params", "Map of parameter values"),
                opt("context-item", "Context item for the evaluation"),
                opt("namespace-context", "Node whose in-scope namespaces are used"),
                opt("schema-aware", "Schema-aware evaluation (attribute value template)", YES_NO));
        el("context-item", "Declares the context item of a template",
                opt("as", "Required type of the context item"),
                opt("use", "Whether a context item is needed", CONTEXT_USE));
        el("map", "Creates a map from xsl:map-entry instructions");
        el("map-entry", "Creates a single-entry map",
                req("key", "XPath expression giving the key"),
                opt("select", "XPath expression giving the value"));
        el("fork", "Evaluates instructions that may each consume a stream");
        el("merge", "Merges sorted input sequences");
        el("merge-source", "Input of xsl:merge",
                opt("name", "Name of the merge source"),
                opt("for-each-item", "Items each giving one input sequence"),
                opt("for-each-source", "URIs of the input documents"),
                req("select", "XPath expression selecting the items to merge"),
                opt("streamable", "Stream the input documents", YES_NO),
                opt("use-accumulators", "Accumulators applicable to the documents"),
                opt("sort-before-merge", "Sort each input before merging", YES_NO),
                opt("validation", "Validation of the documents", VALIDATION),
                opt("type", "Type annotation"));
        el("merge-key", "Merge key of an xsl:merge-source",
                opt("select", "XPath expression giving the key"),
                opt("order", "Sort order", ORDER),
                opt("data-type", "Compare as text or numbers", DATA_TYPE),
                opt("case-order", "Upper or lower case first", CASE_ORDER),
                opt("lang", "Language of the keys"),
                opt("collation", "Collation URI"));
        el("merge-action", "Processes each group of merged items");
        el("on-empty", "Evaluated when the enclosing content is empty",
                opt("select", "XPath expression giving the result"));
        el("on-non-empty", "Evaluated only when the enclosing content is not empty",
                opt("select", "XPath expression giving the result"));
        el("where-populated", "Discards the result when it is empty");
    }

    private XsltElementCatalog() {
    }

    /** @return all XSLT elements in catalog order */
    public static List<XsltElement> elements() {
        return List.copyOf(ELEMENTS.values());
    }

    /** @return the element with the given local name */
    public static Optional<XsltElement> element(String localName) {
        return Optional.ofNullable(localName == null ? null : ELEMENTS.get(localName));
    }

    /**
     * All attributes of an element: its own ones first, then the standard attributes it does
     * not already declare.
     *
     * @param localName the element's local name
     * @return the attributes, empty for an unknown element
     */
    public static List<XsltAttribute> attributesOf(String localName) {
        XsltElement element = ELEMENTS.get(localName);
        if (element == null) {
            return Collections.emptyList();
        }
        List<XsltAttribute> all = new ArrayList<>(element.attributes());
        Set<String> own = new HashSet<>();
        element.attributes().forEach(a -> own.add(a.name()));
        for (XsltAttribute standard : STANDARD_ATTRIBUTES) {
            if (!own.contains(standard.name())) {
                all.add(standard);
            }
        }
        return all;
    }

    /** @return the named attribute of the element (own or standard) */
    public static Optional<XsltAttribute> attribute(String elementLocalName, String attributeName) {
        return attributesOf(elementLocalName).stream().filter(a -> a.name().equals(attributeName)).findFirst();
    }

    private static void el(String name, String description, XsltAttribute... attributes) {
        ELEMENTS.put(name, new XsltElement(name, description, List.of(attributes)));
    }

    private static void el(String name, String description, List<XsltAttribute> attributes) {
        ELEMENTS.put(name, new XsltElement(name, description, List.copyOf(attributes)));
    }

    /** The given attributes followed by the serialization parameters not among them. */
    private static List<XsltAttribute> withSerialization(XsltAttribute... first) {
        List<XsltAttribute> all = new ArrayList<>(List.of(first));
        Set<String> names = new HashSet<>();
        all.forEach(a -> names.add(a.name()));
        SERIALIZATION.stream().filter(a -> !names.contains(a.name())).forEach(all::add);
        return all;
    }

    private static XsltAttribute req(String name, String description) {
        return new XsltAttribute(name, true, description, List.of());
    }

    private static XsltAttribute req(String name, String description, List<String> values) {
        return new XsltAttribute(name, true, description, values);
    }

    private static XsltAttribute opt(String name, String description) {
        return new XsltAttribute(name, false, description, List.of());
    }

    private static XsltAttribute opt(String name, String description, List<String> values) {
        return new XsltAttribute(name, false, description, values);
    }
}
