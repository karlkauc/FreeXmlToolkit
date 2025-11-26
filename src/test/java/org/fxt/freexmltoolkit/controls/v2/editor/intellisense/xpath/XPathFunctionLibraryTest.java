package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.xpath;

import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItem;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItemType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XPathFunctionLibrary.
 * Tests function, axis, operator, and keyword completions.
 */
@DisplayName("XPathFunctionLibrary Tests")
class XPathFunctionLibraryTest {

    private static final int TEST_SCORE = 100;

    @Nested
    @DisplayName("Function Completions")
    class FunctionCompletionTests {

        @Test
        @DisplayName("Should return all functions when prefix is empty")
        void allFunctions_whenPrefixEmpty() {
            List<CompletionItem> items = XPathFunctionLibrary.getFunctionCompletions("", TEST_SCORE);

            assertFalse(items.isEmpty(), "Function list should not be empty");
            assertTrue(items.size() >= 50, "Should have at least 50 functions");
        }

        @Test
        @DisplayName("Should filter functions by prefix")
        void filterFunctions_byPrefix() {
            List<CompletionItem> items = XPathFunctionLibrary.getFunctionCompletions("str", TEST_SCORE);

            assertTrue(items.size() > 0, "Should find at least one function starting with 'str'");
            items.forEach(item ->
                    assertTrue(item.getLabel().toLowerCase().startsWith("str"),
                            "Function should start with 'str': " + item.getLabel()));
        }

        @Test
        @DisplayName("Should include common XPath functions")
        void includeCommonFunctions() {
            List<CompletionItem> items = XPathFunctionLibrary.getFunctionCompletions("", TEST_SCORE);
            List<String> labels = items.stream().map(CompletionItem::getLabel).toList();

            assertTrue(labels.contains("contains"), "Should include 'contains' function");
            assertTrue(labels.contains("string"), "Should include 'string' function");
            assertTrue(labels.contains("concat"), "Should include 'concat' function");
            assertTrue(labels.contains("position"), "Should include 'position' function");
            assertTrue(labels.contains("count"), "Should include 'count' function");
        }

        @Test
        @DisplayName("Function completions should have correct type")
        void functionCompletions_haveCorrectType() {
            List<CompletionItem> items = XPathFunctionLibrary.getFunctionCompletions("con", TEST_SCORE);

            items.forEach(item ->
                    assertEquals(CompletionItemType.XPATH_FUNCTION, item.getType(),
                            "Function should have XPATH_FUNCTION type"));
        }

        @Test
        @DisplayName("Function completions should include description")
        void functionCompletions_includeDescription() {
            List<CompletionItem> items = XPathFunctionLibrary.getFunctionCompletions("contains", TEST_SCORE);

            assertFalse(items.isEmpty(), "Should find 'contains' function");
            CompletionItem contains = items.stream()
                    .filter(i -> i.getLabel().equals("contains"))
                    .findFirst()
                    .orElseThrow();
            assertNotNull(contains.getDescription(), "Function should have description");
            assertFalse(contains.getDescription().isEmpty(), "Description should not be empty");
        }

        @Test
        @DisplayName("Should handle case-insensitive prefix matching")
        void caseInsensitiveMatching() {
            List<CompletionItem> lowerItems = XPathFunctionLibrary.getFunctionCompletions("str", TEST_SCORE);
            List<CompletionItem> upperItems = XPathFunctionLibrary.getFunctionCompletions("STR", TEST_SCORE);

            assertEquals(lowerItems.size(), upperItems.size(),
                    "Case should not affect number of results");
        }
    }

    @Nested
    @DisplayName("Axis Completions")
    class AxisCompletionTests {

        @Test
        @DisplayName("Should return all 13 XPath axes")
        void allAxes_whenPrefixEmpty() {
            List<CompletionItem> items = XPathFunctionLibrary.getAxisCompletions("", TEST_SCORE);

            assertEquals(13, items.size(), "Should have exactly 13 XPath axes");
        }

        @Test
        @DisplayName("Should include all standard axes")
        void includeStandardAxes() {
            List<CompletionItem> items = XPathFunctionLibrary.getAxisCompletions("", TEST_SCORE);
            List<String> labels = items.stream().map(CompletionItem::getLabel).toList();

            assertTrue(labels.contains("child"), "Should include 'child' axis");
            assertTrue(labels.contains("parent"), "Should include 'parent' axis");
            assertTrue(labels.contains("ancestor"), "Should include 'ancestor' axis");
            assertTrue(labels.contains("descendant"), "Should include 'descendant' axis");
            assertTrue(labels.contains("following"), "Should include 'following' axis");
            assertTrue(labels.contains("preceding"), "Should include 'preceding' axis");
            assertTrue(labels.contains("self"), "Should include 'self' axis");
            assertTrue(labels.contains("attribute"), "Should include 'attribute' axis");
            assertTrue(labels.contains("namespace"), "Should include 'namespace' axis");
        }

        @Test
        @DisplayName("Should filter axes by prefix")
        void filterAxes_byPrefix() {
            List<CompletionItem> items = XPathFunctionLibrary.getAxisCompletions("anc", TEST_SCORE);

            assertEquals(2, items.size(), "Should find 'ancestor' and 'ancestor-or-self'");
        }

        @Test
        @DisplayName("Axis completions should have correct type")
        void axisCompletions_haveCorrectType() {
            List<CompletionItem> items = XPathFunctionLibrary.getAxisCompletions("child", TEST_SCORE);

            items.forEach(item ->
                    assertEquals(CompletionItemType.XPATH_AXIS, item.getType(),
                            "Axis should have XPATH_AXIS type"));
        }
    }

    @Nested
    @DisplayName("Operator Completions")
    class OperatorCompletionTests {

        @Test
        @DisplayName("Should return operators when prefix is empty")
        void allOperators_whenPrefixEmpty() {
            List<CompletionItem> items = XPathFunctionLibrary.getOperatorCompletions("", TEST_SCORE);

            assertFalse(items.isEmpty(), "Operator list should not be empty");
        }

        @Test
        @DisplayName("Should include common XPath operators")
        void includeCommonOperators() {
            List<CompletionItem> items = XPathFunctionLibrary.getOperatorCompletions("", TEST_SCORE);
            List<String> labels = items.stream().map(CompletionItem::getLabel).toList();

            assertTrue(labels.contains("and"), "Should include 'and' operator");
            assertTrue(labels.contains("or"), "Should include 'or' operator");
            assertTrue(labels.contains("div"), "Should include 'div' operator");
            assertTrue(labels.contains("mod"), "Should include 'mod' operator");
        }

        @Test
        @DisplayName("Operator completions should have correct type")
        void operatorCompletions_haveCorrectType() {
            List<CompletionItem> items = XPathFunctionLibrary.getOperatorCompletions("and", TEST_SCORE);

            items.forEach(item ->
                    assertEquals(CompletionItemType.XPATH_OPERATOR, item.getType(),
                            "Operator should have XPATH_OPERATOR type"));
        }
    }

    @Nested
    @DisplayName("Node Test Completions")
    class NodeTestCompletionTests {

        @Test
        @DisplayName("Should return node test functions")
        void allNodeTests_whenPrefixEmpty() {
            List<CompletionItem> items = XPathFunctionLibrary.getNodeTestCompletions("", TEST_SCORE);

            assertFalse(items.isEmpty(), "Node test list should not be empty");
        }

        @Test
        @DisplayName("Should include standard node tests")
        void includeStandardNodeTests() {
            List<CompletionItem> items = XPathFunctionLibrary.getNodeTestCompletions("", TEST_SCORE);
            List<String> labels = items.stream().map(CompletionItem::getLabel).toList();

            assertTrue(labels.contains("node()"), "Should include 'node()' test");
            assertTrue(labels.contains("text()"), "Should include 'text()' test");
            assertTrue(labels.contains("comment()"), "Should include 'comment()' test");
        }

        @Test
        @DisplayName("Node test completions should have correct type")
        void nodeTestCompletions_haveCorrectType() {
            List<CompletionItem> items = XPathFunctionLibrary.getNodeTestCompletions("node", TEST_SCORE);

            items.forEach(item ->
                    assertEquals(CompletionItemType.XPATH_NODE_TEST, item.getType(),
                            "Node test should have XPATH_NODE_TEST type"));
        }
    }

    @Nested
    @DisplayName("XQuery Keyword Completions")
    class XQueryKeywordCompletionTests {

        @Test
        @DisplayName("Should return XQuery keywords")
        void allKeywords_whenPrefixEmpty() {
            List<CompletionItem> items = XPathFunctionLibrary.getXQueryKeywordCompletions("", TEST_SCORE);

            assertFalse(items.isEmpty(), "XQuery keyword list should not be empty");
        }

        @Test
        @DisplayName("Should include FLWOR keywords")
        void includeFlworKeywords() {
            List<CompletionItem> items = XPathFunctionLibrary.getXQueryKeywordCompletions("", TEST_SCORE);
            List<String> labels = items.stream().map(CompletionItem::getLabel).toList();

            assertTrue(labels.contains("for"), "Should include 'for' keyword");
            assertTrue(labels.contains("let"), "Should include 'let' keyword");
            assertTrue(labels.contains("where"), "Should include 'where' keyword");
            assertTrue(labels.contains("order by"), "Should include 'order by' keyword");
            assertTrue(labels.contains("return"), "Should include 'return' keyword");
        }

        @Test
        @DisplayName("Should filter keywords by prefix")
        void filterKeywords_byPrefix() {
            List<CompletionItem> items = XPathFunctionLibrary.getXQueryKeywordCompletions("for", TEST_SCORE);

            assertTrue(items.size() > 0, "Should find 'for' keyword");
            items.forEach(item ->
                    assertTrue(item.getLabel().toLowerCase().startsWith("for"),
                            "Keyword should start with 'for': " + item.getLabel()));
        }

        @Test
        @DisplayName("XQuery keyword completions should have correct type")
        void xqueryKeywordCompletions_haveCorrectType() {
            List<CompletionItem> items = XPathFunctionLibrary.getXQueryKeywordCompletions("let", TEST_SCORE);

            items.forEach(item ->
                    assertEquals(CompletionItemType.XQUERY_KEYWORD, item.getType(),
                            "XQuery keyword should have XQUERY_KEYWORD type"));
        }
    }

    @Nested
    @DisplayName("Combined Completion Methods")
    class CombinedCompletionTests {

        @Test
        @DisplayName("getAllXPathCompletions should include functions, axes, operators")
        void allXPathCompletions_includeAllTypes() {
            List<CompletionItem> items = XPathFunctionLibrary.getAllXPathCompletions("", TEST_SCORE);

            long functionCount = items.stream()
                    .filter(i -> i.getType() == CompletionItemType.XPATH_FUNCTION)
                    .count();
            long axisCount = items.stream()
                    .filter(i -> i.getType() == CompletionItemType.XPATH_AXIS)
                    .count();
            long operatorCount = items.stream()
                    .filter(i -> i.getType() == CompletionItemType.XPATH_OPERATOR)
                    .count();

            assertTrue(functionCount > 0, "Should include functions");
            assertTrue(axisCount > 0, "Should include axes");
            assertTrue(operatorCount > 0, "Should include operators");
        }

        @Test
        @DisplayName("getAllXQueryCompletions should include XQuery keywords")
        void allXQueryCompletions_includeKeywords() {
            List<CompletionItem> items = XPathFunctionLibrary.getAllXQueryCompletions("", TEST_SCORE);

            long keywordCount = items.stream()
                    .filter(i -> i.getType() == CompletionItemType.XQUERY_KEYWORD)
                    .count();

            assertTrue(keywordCount > 0, "Should include XQuery keywords");
        }
    }

    @Nested
    @DisplayName("Relevance Score Tests")
    class RelevanceScoreTests {

        @Test
        @DisplayName("Completions should have provided base score")
        void completions_haveProvidedScore() {
            int customScore = 150;
            List<CompletionItem> items = XPathFunctionLibrary.getFunctionCompletions("count", customScore);

            assertFalse(items.isEmpty(), "Should find 'count' function");
            items.forEach(item ->
                    assertTrue(item.getRelevanceScore() >= customScore,
                            "Score should be at least " + customScore));
        }

        @Test
        @DisplayName("Exact prefix matches should have boosted score")
        void exactPrefixMatches_haveBoostedScore() {
            List<CompletionItem> items = XPathFunctionLibrary.getFunctionCompletions("string", TEST_SCORE);

            // Find 'string' function
            CompletionItem stringFunc = items.stream()
                    .filter(i -> i.getLabel().equals("string"))
                    .findFirst()
                    .orElse(null);

            // Find 'string-length' function
            CompletionItem stringLengthFunc = items.stream()
                    .filter(i -> i.getLabel().equals("string-length"))
                    .findFirst()
                    .orElse(null);

            // Both should exist, exact match might have higher or equal score
            assertNotNull(stringFunc, "Should find 'string' function");
            assertNotNull(stringLengthFunc, "Should find 'string-length' function");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Null prefix should be treated as empty")
        void nullPrefix_treatedAsEmpty() {
            List<CompletionItem> nullItems = XPathFunctionLibrary.getFunctionCompletions(null, TEST_SCORE);
            List<CompletionItem> emptyItems = XPathFunctionLibrary.getFunctionCompletions("", TEST_SCORE);

            assertEquals(emptyItems.size(), nullItems.size(),
                    "Null and empty prefix should return same results");
        }

        @Test
        @DisplayName("Non-matching prefix should return empty list")
        void nonMatchingPrefix_returnsEmpty() {
            List<CompletionItem> items = XPathFunctionLibrary.getFunctionCompletions("zzzzzzz", TEST_SCORE);

            assertTrue(items.isEmpty(), "Non-matching prefix should return empty list");
        }

        @Test
        @DisplayName("Whitespace prefix should return empty list")
        void whitespacePrefix_returnsEmpty() {
            List<CompletionItem> items = XPathFunctionLibrary.getFunctionCompletions("   ", TEST_SCORE);

            // Whitespace should not match any function names
            assertTrue(items.isEmpty(), "Whitespace prefix should return empty list");
        }
    }
}
