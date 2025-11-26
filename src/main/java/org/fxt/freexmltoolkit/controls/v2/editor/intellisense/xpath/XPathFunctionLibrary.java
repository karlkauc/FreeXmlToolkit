package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.xpath;

import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItem;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItemType;

import java.util.ArrayList;
import java.util.List;

/**
 * Static library of XPath 1.0/2.0/3.1 functions, axes, operators, and XQuery keywords.
 * Provides CompletionItem instances for autocomplete suggestions.
 */
public final class XPathFunctionLibrary {

    private static final List<FunctionInfo> FUNCTIONS = new ArrayList<>();
    private static final List<AxisInfo> AXES = new ArrayList<>();
    private static final List<OperatorInfo> OPERATORS = new ArrayList<>();
    private static final List<KeywordInfo> XQUERY_KEYWORDS = new ArrayList<>();
    private static final List<NodeTestInfo> NODE_TESTS = new ArrayList<>();

    static {
        initializeFunctions();
        initializeAxes();
        initializeOperators();
        initializeXQueryKeywords();
        initializeNodeTests();
    }

    private XPathFunctionLibrary() {
        // Static utility class
    }

    // ===================== FUNCTIONS =====================

    private static void initializeFunctions() {
        // XPath 1.0 String Functions
        addFunction("concat", "concat($str1, $str2, ...)", "Concatenates two or more strings", "xs:string");
        addFunction("contains", "contains($str, $search)", "Returns true if first string contains second", "xs:boolean");
        addFunction("normalize-space", "normalize-space($str?)", "Normalizes whitespace in a string", "xs:string");
        addFunction("starts-with", "starts-with($str, $prefix)", "Returns true if string starts with prefix", "xs:boolean");
        addFunction("string", "string($arg?)", "Converts argument to string", "xs:string");
        addFunction("string-length", "string-length($str?)", "Returns the length of a string", "xs:integer");
        addFunction("substring", "substring($str, $start, $len?)", "Returns a substring", "xs:string");
        addFunction("substring-after", "substring-after($str, $search)", "Returns substring after occurrence", "xs:string");
        addFunction("substring-before", "substring-before($str, $search)", "Returns substring before occurrence", "xs:string");
        addFunction("translate", "translate($str, $from, $to)", "Translates characters in a string", "xs:string");

        // XPath 1.0 Numeric Functions
        addFunction("ceiling", "ceiling($num)", "Returns smallest integer >= argument", "xs:integer");
        addFunction("floor", "floor($num)", "Returns largest integer <= argument", "xs:integer");
        addFunction("number", "number($arg?)", "Converts argument to number", "xs:double");
        addFunction("round", "round($num)", "Rounds to nearest integer", "xs:integer");
        addFunction("sum", "sum($seq)", "Returns sum of numeric values in sequence", "xs:double");

        // XPath 1.0 Boolean Functions
        addFunction("boolean", "boolean($arg)", "Converts argument to boolean", "xs:boolean");
        addFunction("false", "false()", "Returns boolean false", "xs:boolean");
        addFunction("not", "not($arg)", "Returns negation of argument", "xs:boolean");
        addFunction("true", "true()", "Returns boolean true", "xs:boolean");

        // XPath 1.0 Node Functions
        addFunction("count", "count($seq)", "Returns number of items in sequence", "xs:integer");
        addFunction("id", "id($ids)", "Returns elements with matching IDs", "element()*");
        addFunction("last", "last()", "Returns size of context sequence", "xs:integer");
        addFunction("local-name", "local-name($node?)", "Returns local name of node", "xs:string");
        addFunction("name", "name($node?)", "Returns qualified name of node", "xs:string");
        addFunction("namespace-uri", "namespace-uri($node?)", "Returns namespace URI of node", "xs:string");
        addFunction("position", "position()", "Returns position of context item", "xs:integer");

        // XPath 2.0 String Functions
        addFunction("compare", "compare($str1, $str2)", "Compares two strings", "xs:integer");
        addFunction("ends-with", "ends-with($str, $suffix)", "Returns true if string ends with suffix", "xs:boolean");
        addFunction("lower-case", "lower-case($str)", "Converts string to lowercase", "xs:string");
        addFunction("matches", "matches($str, $pattern)", "Returns true if string matches regex pattern", "xs:boolean");
        addFunction("replace", "replace($str, $pattern, $repl)", "Replaces pattern occurrences", "xs:string");
        addFunction("tokenize", "tokenize($str, $pattern?)", "Splits string by pattern", "xs:string*");
        addFunction("upper-case", "upper-case($str)", "Converts string to uppercase", "xs:string");
        addFunction("string-join", "string-join($seq, $sep?)", "Joins sequence with separator", "xs:string");
        addFunction("encode-for-uri", "encode-for-uri($str)", "Encodes string for URI", "xs:string");
        addFunction("escape-html-uri", "escape-html-uri($str)", "Escapes URI for HTML", "xs:string");
        addFunction("iri-to-uri", "iri-to-uri($iri)", "Converts IRI to URI", "xs:string");

        // XPath 2.0 Numeric Functions
        addFunction("abs", "abs($num)", "Returns absolute value", "numeric");
        addFunction("avg", "avg($seq)", "Returns average of numeric values", "xs:double");
        addFunction("max", "max($seq)", "Returns maximum value", "xs:anyAtomicType");
        addFunction("min", "min($seq)", "Returns minimum value", "xs:anyAtomicType");
        addFunction("round-half-to-even", "round-half-to-even($num, $prec?)", "Banker's rounding", "numeric");

        // XPath 2.0 Sequence Functions
        addFunction("distinct-values", "distinct-values($seq)", "Returns unique values from sequence", "xs:anyAtomicType*");
        addFunction("empty", "empty($seq)", "Returns true if sequence is empty", "xs:boolean");
        addFunction("exists", "exists($seq)", "Returns true if sequence is not empty", "xs:boolean");
        addFunction("index-of", "index-of($seq, $search)", "Returns positions of matching items", "xs:integer*");
        addFunction("insert-before", "insert-before($seq, $pos, $items)", "Inserts items into sequence", "item()*");
        addFunction("remove", "remove($seq, $pos)", "Removes item at position", "item()*");
        addFunction("reverse", "reverse($seq)", "Reverses sequence order", "item()*");
        addFunction("subsequence", "subsequence($seq, $start, $len?)", "Returns subsequence", "item()*");
        addFunction("unordered", "unordered($seq)", "Returns sequence in implementation-dependent order", "item()*");

        // XPath 2.0 Date/Time Functions
        addFunction("current-date", "current-date()", "Returns current date", "xs:date");
        addFunction("current-dateTime", "current-dateTime()", "Returns current date and time", "xs:dateTime");
        addFunction("current-time", "current-time()", "Returns current time", "xs:time");
        addFunction("day-from-date", "day-from-date($date)", "Extracts day from date", "xs:integer");
        addFunction("hours-from-time", "hours-from-time($time)", "Extracts hours from time", "xs:integer");
        addFunction("month-from-date", "month-from-date($date)", "Extracts month from date", "xs:integer");
        addFunction("year-from-date", "year-from-date($date)", "Extracts year from date", "xs:integer");
        addFunction("format-date", "format-date($date, $picture)", "Formats date according to picture", "xs:string");
        addFunction("format-dateTime", "format-dateTime($dt, $picture)", "Formats dateTime", "xs:string");
        addFunction("format-number", "format-number($num, $picture)", "Formats number according to picture", "xs:string");
        addFunction("format-time", "format-time($time, $picture)", "Formats time according to picture", "xs:string");

        // XPath 2.0 Node/Context Functions
        addFunction("base-uri", "base-uri($node?)", "Returns base URI of node", "xs:anyURI?");
        addFunction("document-uri", "document-uri($node?)", "Returns document URI", "xs:anyURI?");
        addFunction("root", "root($node?)", "Returns root of tree containing node", "node()");
        addFunction("lang", "lang($lang)", "Returns true if context lang matches", "xs:boolean");
        addFunction("nilled", "nilled($node?)", "Returns true if element is nilled", "xs:boolean");

        // XPath 2.0 Type Functions
        addFunction("data", "data($arg)", "Returns atomized value", "xs:anyAtomicType*");
        addFunction("deep-equal", "deep-equal($seq1, $seq2)", "Deep comparison of sequences", "xs:boolean");
        addFunction("error", "error($code?, $desc?, $obj?)", "Raises an error", "none");
        addFunction("trace", "trace($value, $label)", "Traces value for debugging", "item()*");

        // XPath 2.0 Constructor Functions
        addFunction("xs:string", "xs:string($arg)", "Constructs xs:string value", "xs:string");
        addFunction("xs:integer", "xs:integer($arg)", "Constructs xs:integer value", "xs:integer");
        addFunction("xs:decimal", "xs:decimal($arg)", "Constructs xs:decimal value", "xs:decimal");
        addFunction("xs:double", "xs:double($arg)", "Constructs xs:double value", "xs:double");
        addFunction("xs:boolean", "xs:boolean($arg)", "Constructs xs:boolean value", "xs:boolean");
        addFunction("xs:date", "xs:date($arg)", "Constructs xs:date value", "xs:date");
        addFunction("xs:dateTime", "xs:dateTime($arg)", "Constructs xs:dateTime value", "xs:dateTime");
        addFunction("xs:time", "xs:time($arg)", "Constructs xs:time value", "xs:time");

        // XPath 3.0/3.1 Functions
        addFunction("head", "head($seq)", "Returns first item of sequence", "item()?");
        addFunction("tail", "tail($seq)", "Returns all but first item", "item()*");
        addFunction("sort", "sort($seq)", "Sorts sequence", "item()*");
        addFunction("fold-left", "fold-left($seq, $zero, $func)", "Left fold over sequence", "item()*");
        addFunction("fold-right", "fold-right($seq, $zero, $func)", "Right fold over sequence", "item()*");
        addFunction("for-each", "for-each($seq, $func)", "Applies function to each item", "item()*");
        addFunction("filter", "filter($seq, $func)", "Filters sequence by predicate", "item()*");
        addFunction("for-each-pair", "for-each-pair($seq1, $seq2, $func)", "Applies function to pairs", "item()*");
        addFunction("generate-id", "generate-id($node?)", "Generates unique ID for node", "xs:string");
        addFunction("has-children", "has-children($node?)", "Returns true if node has children", "xs:boolean");
        addFunction("innermost", "innermost($nodes)", "Returns innermost nodes", "node()*");
        addFunction("outermost", "outermost($nodes)", "Returns outermost nodes", "node()*");
        addFunction("path", "path($node?)", "Returns path expression for node", "xs:string?");
        addFunction("parse-xml", "parse-xml($str)", "Parses XML string to document", "document-node()");
        addFunction("serialize", "serialize($arg)", "Serializes to XML string", "xs:string");
        addFunction("unparsed-text", "unparsed-text($uri)", "Reads text file", "xs:string");

        // XPath 3.1 Map/Array Functions
        addFunction("map:merge", "map:merge($maps)", "Merges maps", "map(*)");
        addFunction("map:keys", "map:keys($map)", "Returns keys of map", "xs:anyAtomicType*");
        addFunction("map:contains", "map:contains($map, $key)", "Tests if map contains key", "xs:boolean");
        addFunction("map:get", "map:get($map, $key)", "Gets value from map", "item()*");
        addFunction("map:put", "map:put($map, $key, $val)", "Puts entry in map", "map(*)");
        addFunction("map:remove", "map:remove($map, $key)", "Removes entry from map", "map(*)");
        addFunction("array:size", "array:size($array)", "Returns array size", "xs:integer");
        addFunction("array:get", "array:get($array, $pos)", "Gets array element", "item()*");
        addFunction("array:put", "array:put($array, $pos, $val)", "Puts element in array", "array(*)");
        addFunction("array:append", "array:append($array, $val)", "Appends to array", "array(*)");
        addFunction("array:join", "array:join($arrays)", "Joins arrays", "array(*)");
    }

    private static void addFunction(String name, String signature, String description, String returnType) {
        FUNCTIONS.add(new FunctionInfo(name, signature, description, returnType));
    }

    // ===================== AXES =====================

    private static void initializeAxes() {
        addAxis("ancestor", "Selects all ancestors (parent, grandparent, etc.)");
        addAxis("ancestor-or-self", "Selects all ancestors and the current node");
        addAxis("attribute", "Selects all attributes of the current node (shorthand: @)");
        addAxis("child", "Selects all children of the current node (default axis)");
        addAxis("descendant", "Selects all descendants (children, grandchildren, etc.)");
        addAxis("descendant-or-self", "Selects all descendants and the current node (shorthand: //)");
        addAxis("following", "Selects everything after the closing tag of the current node");
        addAxis("following-sibling", "Selects all siblings after the current node");
        addAxis("namespace", "Selects all namespace nodes of the current node");
        addAxis("parent", "Selects the parent of the current node (shorthand: ..)");
        addAxis("preceding", "Selects all nodes before the current node");
        addAxis("preceding-sibling", "Selects all siblings before the current node");
        addAxis("self", "Selects the current node (shorthand: .)");
    }

    private static void addAxis(String name, String description) {
        AXES.add(new AxisInfo(name, description));
    }

    // ===================== OPERATORS =====================

    private static void initializeOperators() {
        // Comparison operators
        addOperator("=", "Equality (general comparison)");
        addOperator("!=", "Inequality (general comparison)");
        addOperator("<", "Less than (general comparison)");
        addOperator("<=", "Less than or equal (general comparison)");
        addOperator(">", "Greater than (general comparison)");
        addOperator(">=", "Greater than or equal (general comparison)");
        addOperator("eq", "Equality (value comparison)");
        addOperator("ne", "Inequality (value comparison)");
        addOperator("lt", "Less than (value comparison)");
        addOperator("le", "Less than or equal (value comparison)");
        addOperator("gt", "Greater than (value comparison)");
        addOperator("ge", "Greater than or equal (value comparison)");

        // Logical operators
        addOperator("and", "Logical AND");
        addOperator("or", "Logical OR");

        // Arithmetic operators
        addOperator("+", "Addition");
        addOperator("-", "Subtraction");
        addOperator("*", "Multiplication");
        addOperator("div", "Division");
        addOperator("mod", "Modulo (remainder)");
        addOperator("idiv", "Integer division");

        // Sequence operators
        addOperator("|", "Union of sequences");
        addOperator("union", "Union of sequences");
        addOperator("intersect", "Intersection of sequences");
        addOperator("except", "Difference of sequences");
        addOperator("to", "Range expression (1 to 10)");
        addOperator(",", "Sequence concatenation");

        // Node comparison
        addOperator("is", "Node identity comparison");
        addOperator("<<", "Node precedes");
        addOperator(">>", "Node follows");

        // Instance/Type operators
        addOperator("instance of", "Type instance test");
        addOperator("treat as", "Type treatment");
        addOperator("cast as", "Type cast");
        addOperator("castable as", "Castability test");
    }

    private static void addOperator(String name, String description) {
        OPERATORS.add(new OperatorInfo(name, description));
    }

    // ===================== XQUERY KEYWORDS =====================

    private static void initializeXQueryKeywords() {
        addKeyword("for", "for $var in $seq", "FLWOR: Iterate over sequence");
        addKeyword("let", "let $var := $expr", "FLWOR: Bind variable to expression");
        addKeyword("where", "where $condition", "FLWOR: Filter results");
        addKeyword("order by", "order by $key", "FLWOR: Sort results");
        addKeyword("return", "return $expr", "FLWOR: Return expression");
        addKeyword("if", "if ($cond) then $expr else $expr", "Conditional expression");
        addKeyword("then", "then $expr", "Then branch of conditional");
        addKeyword("else", "else $expr", "Else branch of conditional");
        addKeyword("some", "some $var in $seq satisfies $cond", "Existential quantification");
        addKeyword("every", "every $var in $seq satisfies $cond", "Universal quantification");
        addKeyword("satisfies", "satisfies $condition", "Quantifier condition");
        addKeyword("in", "in $sequence", "Sequence binding");
        addKeyword("ascending", "ascending", "Sort direction");
        addKeyword("descending", "descending", "Sort direction");
        addKeyword("empty greatest", "empty greatest", "Empty value ordering");
        addKeyword("empty least", "empty least", "Empty value ordering");
        addKeyword("collation", "collation $uri", "Collation specification");
        addKeyword("stable", "stable order by", "Stable sorting");
        addKeyword("at", "at $pos", "Position variable in for");
        addKeyword("group by", "group by $key", "Grouping clause");
        addKeyword("allowing empty", "allowing empty", "Allow empty sequences in for");
        addKeyword("count", "count $var", "Count variable in for");
    }

    private static void addKeyword(String name, String template, String description) {
        XQUERY_KEYWORDS.add(new KeywordInfo(name, template, description));
    }

    // ===================== NODE TESTS =====================

    private static void initializeNodeTests() {
        addNodeTest("*", "Matches any element node");
        addNodeTest("node()", "Matches any node");
        addNodeTest("text()", "Matches text nodes");
        addNodeTest("comment()", "Matches comment nodes");
        addNodeTest("processing-instruction()", "Matches processing instruction nodes");
        addNodeTest("element()", "Matches element nodes");
        addNodeTest("attribute()", "Matches attribute nodes");
        addNodeTest("document-node()", "Matches document nodes");
        addNodeTest("namespace-node()", "Matches namespace nodes");
        addNodeTest("schema-element()", "Matches schema-validated elements");
        addNodeTest("schema-attribute()", "Matches schema-validated attributes");
    }

    private static void addNodeTest(String name, String description) {
        NODE_TESTS.add(new NodeTestInfo(name, description));
    }

    // ===================== PUBLIC API =====================

    /**
     * Returns all XPath functions as CompletionItems.
     */
    public static List<CompletionItem> getFunctionCompletions(String prefix, int baseScore) {
        List<CompletionItem> items = new ArrayList<>();
        String lowerPrefix = prefix != null ? prefix.toLowerCase() : "";

        for (FunctionInfo func : FUNCTIONS) {
            if (lowerPrefix.isEmpty() || func.name.toLowerCase().startsWith(lowerPrefix)) {
                int score = baseScore;
                // Boost exact prefix matches
                if (func.name.toLowerCase().startsWith(lowerPrefix)) {
                    score += 10;
                }
                // Boost common functions
                if (isCommonFunction(func.name)) {
                    score += 5;
                }

                items.add(new CompletionItem.Builder(
                        func.name,
                        func.name + "()",
                        CompletionItemType.XPATH_FUNCTION
                )
                        .description(func.description + "\n" + func.signature)
                        .dataType(func.returnType)
                        .relevanceScore(score)
                        .build());
            }
        }

        return items;
    }

    /**
     * Returns all XPath axes as CompletionItems.
     */
    public static List<CompletionItem> getAxisCompletions(String prefix, int baseScore) {
        List<CompletionItem> items = new ArrayList<>();
        String lowerPrefix = prefix != null ? prefix.toLowerCase() : "";

        for (AxisInfo axis : AXES) {
            if (lowerPrefix.isEmpty() || axis.name.toLowerCase().startsWith(lowerPrefix)) {
                items.add(new CompletionItem.Builder(
                        axis.name,
                        axis.name + "::",
                        CompletionItemType.XPATH_AXIS
                )
                        .description(axis.description)
                        .relevanceScore(baseScore)
                        .build());
            }
        }

        return items;
    }

    /**
     * Returns all XPath operators as CompletionItems.
     */
    public static List<CompletionItem> getOperatorCompletions(String prefix, int baseScore) {
        List<CompletionItem> items = new ArrayList<>();
        String lowerPrefix = prefix != null ? prefix.toLowerCase() : "";

        for (OperatorInfo op : OPERATORS) {
            if (lowerPrefix.isEmpty() || op.name.toLowerCase().startsWith(lowerPrefix)) {
                // Add spaces around word operators
                String insertText = op.name.length() > 2 ? " " + op.name + " " : op.name;
                items.add(new CompletionItem.Builder(
                        op.name,
                        insertText,
                        CompletionItemType.XPATH_OPERATOR
                )
                        .description(op.description)
                        .relevanceScore(baseScore)
                        .build());
            }
        }

        return items;
    }

    /**
     * Returns all XQuery keywords as CompletionItems.
     */
    public static List<CompletionItem> getXQueryKeywordCompletions(String prefix, int baseScore) {
        List<CompletionItem> items = new ArrayList<>();
        String lowerPrefix = prefix != null ? prefix.toLowerCase() : "";

        for (KeywordInfo kw : XQUERY_KEYWORDS) {
            if (lowerPrefix.isEmpty() || kw.name.toLowerCase().startsWith(lowerPrefix)) {
                items.add(new CompletionItem.Builder(
                        kw.name,
                        kw.name + " ",
                        CompletionItemType.XQUERY_KEYWORD
                )
                        .description(kw.description + "\nTemplate: " + kw.template)
                        .relevanceScore(baseScore)
                        .build());
            }
        }

        return items;
    }

    /**
     * Returns all node tests as CompletionItems.
     */
    public static List<CompletionItem> getNodeTestCompletions(String prefix, int baseScore) {
        List<CompletionItem> items = new ArrayList<>();
        String lowerPrefix = prefix != null ? prefix.toLowerCase() : "";

        for (NodeTestInfo test : NODE_TESTS) {
            if (lowerPrefix.isEmpty() || test.name.toLowerCase().startsWith(lowerPrefix)) {
                items.add(new CompletionItem.Builder(
                        test.name,
                        test.name,
                        CompletionItemType.XPATH_NODE_TEST
                )
                        .description(test.description)
                        .relevanceScore(baseScore)
                        .build());
            }
        }

        return items;
    }

    /**
     * Returns all completions relevant for XPath (functions, axes, operators).
     */
    public static List<CompletionItem> getAllXPathCompletions(String prefix, int baseScore) {
        List<CompletionItem> items = new ArrayList<>();
        items.addAll(getFunctionCompletions(prefix, baseScore));
        items.addAll(getAxisCompletions(prefix, baseScore - 10));
        items.addAll(getOperatorCompletions(prefix, baseScore - 20));
        items.addAll(getNodeTestCompletions(prefix, baseScore - 15));
        return items;
    }

    /**
     * Returns all completions relevant for XQuery (includes XPath + FLWOR keywords).
     */
    public static List<CompletionItem> getAllXQueryCompletions(String prefix, int baseScore) {
        List<CompletionItem> items = new ArrayList<>(getAllXPathCompletions(prefix, baseScore));
        items.addAll(getXQueryKeywordCompletions(prefix, baseScore + 5));
        return items;
    }

    private static boolean isCommonFunction(String name) {
        return switch (name) {
            case "count", "string", "concat", "contains", "starts-with", "ends-with",
                 "substring", "sum", "avg", "max", "min", "position", "last",
                 "not", "true", "false", "number", "string-length", "normalize-space" -> true;
            default -> false;
        };
    }

    // ===================== DATA CLASSES =====================

    private record FunctionInfo(String name, String signature, String description, String returnType) {}
    private record AxisInfo(String name, String description) {}
    private record OperatorInfo(String name, String description) {}
    private record KeywordInfo(String name, String template, String description) {}
    private record NodeTestInfo(String name, String description) {}
}
