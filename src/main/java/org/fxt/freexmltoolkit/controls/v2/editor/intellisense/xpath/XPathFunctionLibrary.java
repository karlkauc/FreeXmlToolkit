package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.xpath;

import java.util.ArrayList;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItem;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItemType;
import org.fxt.freexmltoolkit.service.standards.XPathVersion;

/**
 * Static library of XPath 1.0/2.0/3.1 functions, axes, operators, and XQuery keywords. The functions come from a
 * catalog generated from Saxon's own function library ({@value #CATALOG}).
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

    /** The catalog generated from Saxon's function library (see {@code XPathFunctionCatalogTest}). */
    static final String CATALOG = "/intellisense/xpath-functions.json";

    private static void initializeFunctions() {
        try (var in = XPathFunctionLibrary.class.getResourceAsStream(CATALOG)) {
            if (in == null) {
                return;
            }
            var root = com.google.gson.JsonParser.parseReader(
                    new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : root.getAsJsonArray("functions")) {
                var f = e.getAsJsonObject();
                List<String[]> params = new ArrayList<>();
                for (var p : f.getAsJsonArray("params")) {
                    params.add(new String[]{p.getAsJsonObject().get("name").getAsString(),
                            p.getAsJsonObject().get("type").getAsString()});
                }
                String name = f.get("name").getAsString();
                int min = f.get("min").getAsInt();
                int max = f.get("max").getAsInt();
                FUNCTIONS.add(new FunctionInfo(name, signature(name, min, max, params),
                        f.get("description").getAsString(), f.get("returns").getAsString(),
                        XPathVersion.parse(f.get("since").getAsString()), f.get("xsltOnly").getAsBoolean()));
            }
            for (var e : root.getAsJsonArray("extra")) {
                var f = e.getAsJsonObject();
                FUNCTIONS.add(new FunctionInfo(f.get("name").getAsString(), f.get("signature").getAsString(),
                        f.get("description").getAsString(), f.get("returns").getAsString(), XPathVersion.V2_0,
                        false));
            }
        } catch (java.io.IOException | RuntimeException e) {
            org.apache.logging.log4j.LogManager.getLogger(XPathFunctionLibrary.class)
                    .warn("Function catalog not readable: {}", e.getMessage());
        }
    }

    /**
     * {@code substring($value as xs:string?, $start as xs:numeric[, $length as xs:numeric?])}: optional arguments in
     * brackets; {@code max < 0} = any number of further arguments.
     */
    static String signature(String name, int min, int max, List<String[]> params) {
        StringBuilder sb = new StringBuilder(name).append('(');
        int shown = max < 0 ? Math.max(min, params.size()) : Math.min(max, params.size());
        for (int i = 0; i < shown; i++) {
            String[] p = params.get(Math.min(i, params.size() - 1));
            String argument = "$" + (max < 0 ? p[0].replaceAll("\\d+$", "") + (i + 1) : p[0]) + " as " + p[1];
            if (i >= min && max >= 0) {
                sb.append(i == 0 ? "[" : "[, ").append(argument);
            } else {
                sb.append(i == 0 ? "" : ", ").append(argument);
            }
        }
        if (max >= 0) {
            sb.append("]".repeat(Math.max(0, shown - min)));
        } else {
            sb.append(", ...");
        }
        return sb.append(')').toString();
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
     * @return the names of all functions of XPath / XQuery (unprefixed for {@code fn:}, e.g. {@code distinct-values},
     *         {@code map:get}, {@code xs:date}); XSLT-only functions are not among them
     */
    public static List<String> functionNames() {
        return FUNCTIONS.stream().filter(f -> !f.xsltOnly()).map(FunctionInfo::name).distinct().toList();
    }

    /** @return the number of functions in the catalog, XSLT-only ones included */
    public static int functionCount() {
        return FUNCTIONS.size();
    }

    /** @return the signature of {@code name} (e.g. {@code substring($value as xs:string?, …)}), or {@code null} */
    public static String signatureOf(String name) {
        return FUNCTIONS.stream().filter(f -> f.name().equals(name)).map(FunctionInfo::signature).findFirst()
                .orElse(null);
    }

    /**
     * Returns all XPath functions as CompletionItems.
     */
    public static List<CompletionItem> getFunctionCompletions(String prefix, int baseScore) {
        return getFunctionCompletions(prefix, baseScore, null);
    }

    /**
     * The functions of {@code version} whose name starts with {@code prefix} (all versions for {@code null});
     * XSLT-only functions are left out.
     */
    public static List<CompletionItem> getFunctionCompletions(String prefix, int baseScore, XPathVersion version) {
        List<CompletionItem> items = new ArrayList<>();
        String lowerPrefix = prefix != null ? prefix.toLowerCase() : "";

        for (FunctionInfo func : FUNCTIONS) {
            if (func.xsltOnly() || version != null && func.since().compareTo(version) > 0) {
                continue;
            }
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
        return getAllXPathCompletions(prefix, baseScore, null);
    }

    /** As {@link #getAllXPathCompletions(String, int)}, with the functions of {@code version} only. */
    public static List<CompletionItem> getAllXPathCompletions(String prefix, int baseScore, XPathVersion version) {
        List<CompletionItem> items = new ArrayList<>();
        items.addAll(getFunctionCompletions(prefix, baseScore, version));
        items.addAll(getAxisCompletions(prefix, baseScore - 10));
        items.addAll(getOperatorCompletions(prefix, baseScore - 20));
        items.addAll(getNodeTestCompletions(prefix, baseScore - 15));
        return items;
    }

    /**
     * Returns all completions relevant for XQuery (includes XPath + FLWOR keywords).
     */
    public static List<CompletionItem> getAllXQueryCompletions(String prefix, int baseScore) {
        return getAllXQueryCompletions(prefix, baseScore, null);
    }

    /** As {@link #getAllXQueryCompletions(String, int)}, with the functions of {@code version} only. */
    public static List<CompletionItem> getAllXQueryCompletions(String prefix, int baseScore, XPathVersion version) {
        List<CompletionItem> items = new ArrayList<>(getAllXPathCompletions(prefix, baseScore, version));
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

    private record FunctionInfo(String name, String signature, String description, String returnType,
                                XPathVersion since, boolean xsltOnly) {}
    private record AxisInfo(String name, String description) {}
    private record OperatorInfo(String name, String description) {}
    private record KeywordInfo(String name, String template, String description) {}
    private record NodeTestInfo(String name, String description) {}
}
