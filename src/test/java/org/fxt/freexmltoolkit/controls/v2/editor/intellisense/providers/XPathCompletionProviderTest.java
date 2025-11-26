package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.providers;

import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.XPathContextType;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.XPathEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItem;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItemType;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.xpath.XmlDocumentElementExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XPathCompletionProvider.
 * Tests context-aware XPath/XQuery completion generation.
 */
@DisplayName("XPathCompletionProvider Tests")
class XPathCompletionProviderTest {

    private XPathCompletionProvider xpathProvider;
    private XPathCompletionProvider xqueryProvider;
    private XmlDocumentElementExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new XmlDocumentElementExtractor();
        xpathProvider = new XPathCompletionProvider(extractor, false);
        xqueryProvider = new XPathCompletionProvider(extractor, true);

        // Pre-load some sample XML
        String sampleXml = """
                <bookstore>
                    <book isbn="123" category="fiction">
                        <title lang="en">Test Book</title>
                        <author>Test Author</author>
                        <price currency="USD">19.99</price>
                    </book>
                </bookstore>
                """;
        extractor.extractFromXml(sampleXml);
    }

    @Nested
    @DisplayName("Context Type Tests")
    class ContextTypeTests {

        @Test
        @DisplayName("PATH_START should include elements, axes, and functions")
        void pathStart_includesElementsAxesFunctions() {
            XPathEditorContext context = new XPathEditorContext.Builder()
                    .contextType(XPathContextType.PATH_START)
                    .currentToken("")
                    .build();

            List<CompletionItem> items = xpathProvider.getCompletions(context);

            boolean hasElements = items.stream().anyMatch(i -> i.getType() == CompletionItemType.ELEMENT);
            boolean hasAxes = items.stream().anyMatch(i -> i.getType() == CompletionItemType.XPATH_AXIS);
            boolean hasFunctions = items.stream().anyMatch(i -> i.getType() == CompletionItemType.XPATH_FUNCTION);

            assertTrue(hasElements, "Should include elements");
            assertTrue(hasAxes, "Should include axes");
            assertTrue(hasFunctions, "Should include functions");
        }

        @Test
        @DisplayName("AFTER_SLASH should include elements and axes")
        void afterSlash_includesElementsAndAxes() {
            XPathEditorContext context = new XPathEditorContext.Builder()
                    .contextType(XPathContextType.AFTER_SLASH)
                    .currentToken("")
                    .build();

            List<CompletionItem> items = xpathProvider.getCompletions(context);

            boolean hasElements = items.stream().anyMatch(i -> i.getType() == CompletionItemType.ELEMENT);
            boolean hasAxes = items.stream().anyMatch(i -> i.getType() == CompletionItemType.XPATH_AXIS);
            boolean hasNodeTests = items.stream().anyMatch(i -> i.getType() == CompletionItemType.XPATH_NODE_TEST);

            assertTrue(hasElements, "Should include elements");
            assertTrue(hasAxes, "Should include axes");
            assertTrue(hasNodeTests, "Should include node tests");
        }

        @Test
        @DisplayName("AFTER_AT should include only attributes")
        void afterAt_includesOnlyAttributes() {
            XPathEditorContext context = new XPathEditorContext.Builder()
                    .contextType(XPathContextType.AFTER_AT)
                    .currentToken("")
                    .build();

            List<CompletionItem> items = xpathProvider.getCompletions(context);

            // Should primarily be attributes
            long attributeCount = items.stream()
                    .filter(i -> i.getType() == CompletionItemType.ATTRIBUTE)
                    .count();

            assertTrue(attributeCount > 0, "Should include attributes");
            assertEquals(items.size(), attributeCount, "Should only include attributes");
        }

        @Test
        @DisplayName("IN_PREDICATE should include functions, attributes, operators, and elements")
        void inPredicate_includesFunctionsAttributesOperatorsElements() {
            XPathEditorContext context = new XPathEditorContext.Builder()
                    .contextType(XPathContextType.IN_PREDICATE)
                    .currentToken("")
                    .predicateDepth(1)
                    .build();

            List<CompletionItem> items = xpathProvider.getCompletions(context);

            boolean hasFunctions = items.stream().anyMatch(i -> i.getType() == CompletionItemType.XPATH_FUNCTION);
            boolean hasAttributes = items.stream().anyMatch(i -> i.getType() == CompletionItemType.ATTRIBUTE);
            boolean hasOperators = items.stream().anyMatch(i -> i.getType() == CompletionItemType.XPATH_OPERATOR);
            boolean hasElements = items.stream().anyMatch(i -> i.getType() == CompletionItemType.ELEMENT);

            assertTrue(hasFunctions, "Should include functions");
            assertTrue(hasAttributes, "Should include attributes");
            assertTrue(hasOperators, "Should include operators");
            assertTrue(hasElements, "Should include elements");
        }

        @Test
        @DisplayName("IN_FUNCTION_ARGS should include elements, attributes, and functions")
        void inFunctionArgs_includesElementsAttributesFunctions() {
            XPathEditorContext context = new XPathEditorContext.Builder()
                    .contextType(XPathContextType.IN_FUNCTION_ARGS)
                    .currentToken("")
                    .functionDepth(1)
                    .build();

            List<CompletionItem> items = xpathProvider.getCompletions(context);

            boolean hasElements = items.stream().anyMatch(i -> i.getType() == CompletionItemType.ELEMENT);
            boolean hasAttributes = items.stream().anyMatch(i -> i.getType() == CompletionItemType.ATTRIBUTE);
            boolean hasFunctions = items.stream().anyMatch(i -> i.getType() == CompletionItemType.XPATH_FUNCTION);

            assertTrue(hasElements, "Should include elements");
            assertTrue(hasAttributes, "Should include attributes");
            assertTrue(hasFunctions, "Should include functions");
        }

        @Test
        @DisplayName("IN_STRING_LITERAL should return no completions")
        void inStringLiteral_returnsNoCompletions() {
            XPathEditorContext context = new XPathEditorContext.Builder()
                    .contextType(XPathContextType.IN_STRING_LITERAL)
                    .currentToken("hello")
                    .build();

            List<CompletionItem> items = xpathProvider.getCompletions(context);

            assertTrue(items.isEmpty(), "Should not provide completions in string literal");
        }

        @Test
        @DisplayName("IN_COMMENT should return no completions")
        void inComment_returnsNoCompletions() {
            XPathEditorContext context = new XPathEditorContext.Builder()
                    .contextType(XPathContextType.IN_COMMENT)
                    .currentToken("comment text")
                    .build();

            List<CompletionItem> items = xpathProvider.getCompletions(context);

            assertTrue(items.isEmpty(), "Should not provide completions in comment");
        }

        @Test
        @DisplayName("AFTER_AXIS should include elements and node tests")
        void afterAxis_includesElementsAndNodeTests() {
            XPathEditorContext context = new XPathEditorContext.Builder()
                    .contextType(XPathContextType.AFTER_AXIS)
                    .currentToken("")
                    .build();

            List<CompletionItem> items = xpathProvider.getCompletions(context);

            boolean hasElements = items.stream().anyMatch(i -> i.getType() == CompletionItemType.ELEMENT);
            boolean hasNodeTests = items.stream().anyMatch(i -> i.getType() == CompletionItemType.XPATH_NODE_TEST);

            assertTrue(hasElements, "Should include elements");
            assertTrue(hasNodeTests, "Should include node tests");
        }
    }

    @Nested
    @DisplayName("XQuery Mode Tests")
    class XQueryModeTests {

        @Test
        @DisplayName("XQuery mode should include FLWOR keywords at PATH_START")
        void xqueryMode_includesFlworKeywords() {
            XPathEditorContext context = new XPathEditorContext.Builder()
                    .contextType(XPathContextType.PATH_START)
                    .currentToken("")
                    .isXQuery(true)
                    .build();

            List<CompletionItem> items = xqueryProvider.getCompletions(context);

            boolean hasXQueryKeywords = items.stream()
                    .anyMatch(i -> i.getType() == CompletionItemType.XQUERY_KEYWORD);

            assertTrue(hasXQueryKeywords, "XQuery mode should include FLWOR keywords");
        }

        @Test
        @DisplayName("XPath mode should not include FLWOR keywords")
        void xpathMode_excludesFlworKeywords() {
            XPathEditorContext context = new XPathEditorContext.Builder()
                    .contextType(XPathContextType.PATH_START)
                    .currentToken("")
                    .isXQuery(false)
                    .build();

            List<CompletionItem> items = xpathProvider.getCompletions(context);

            boolean hasXQueryKeywords = items.stream()
                    .anyMatch(i -> i.getType() == CompletionItemType.XQUERY_KEYWORD);

            assertFalse(hasXQueryKeywords, "XPath mode should not include FLWOR keywords");
        }

        @Test
        @DisplayName("AFTER_FOR should include variable suggestions in XQuery mode")
        void afterFor_includesVariableSuggestions() {
            XPathEditorContext context = new XPathEditorContext.Builder()
                    .contextType(XPathContextType.AFTER_FOR)
                    .currentToken("")
                    .isXQuery(true)
                    .build();

            List<CompletionItem> items = xqueryProvider.getCompletions(context);

            boolean hasVariables = items.stream()
                    .anyMatch(i -> i.getType() == CompletionItemType.XPATH_VARIABLE);

            assertTrue(hasVariables, "AFTER_FOR should suggest variables");
        }

        @Test
        @DisplayName("AFTER_DOLLAR should suggest variable names in XQuery mode")
        void afterDollar_suggestsVariableNames() {
            XPathEditorContext context = new XPathEditorContext.Builder()
                    .contextType(XPathContextType.AFTER_DOLLAR)
                    .currentToken("")
                    .isXQuery(true)
                    .build();

            List<CompletionItem> items = xqueryProvider.getCompletions(context);

            assertFalse(items.isEmpty(), "Should suggest variable names after $");
        }

        @Test
        @DisplayName("XQUERY_BODY should include keywords, elements, and functions")
        void xqueryBody_includesKeywordsElementsFunctions() {
            XPathEditorContext context = new XPathEditorContext.Builder()
                    .contextType(XPathContextType.XQUERY_BODY)
                    .currentToken("")
                    .isXQuery(true)
                    .build();

            List<CompletionItem> items = xqueryProvider.getCompletions(context);

            boolean hasKeywords = items.stream().anyMatch(i -> i.getType() == CompletionItemType.XQUERY_KEYWORD);
            boolean hasElements = items.stream().anyMatch(i -> i.getType() == CompletionItemType.ELEMENT);
            boolean hasFunctions = items.stream().anyMatch(i -> i.getType() == CompletionItemType.XPATH_FUNCTION);

            assertTrue(hasKeywords, "Should include XQuery keywords");
            assertTrue(hasElements, "Should include elements");
            assertTrue(hasFunctions, "Should include functions");
        }
    }

    @Nested
    @DisplayName("Prefix Filtering Tests")
    class PrefixFilteringTests {

        @Test
        @DisplayName("Should filter completions by current token")
        void filterCompletions_byCurrentToken() {
            XPathEditorContext context = new XPathEditorContext.Builder()
                    .contextType(XPathContextType.PATH_START)
                    .currentToken("book")
                    .build();

            List<CompletionItem> items = xpathProvider.getCompletions(context);

            // Should include 'book' and 'bookstore' from the sample XML
            boolean hasBook = items.stream().anyMatch(i -> i.getLabel().equals("book"));
            boolean hasBookstore = items.stream().anyMatch(i -> i.getLabel().equals("bookstore"));

            assertTrue(hasBook, "Should include 'book' element");
            assertTrue(hasBookstore, "Should include 'bookstore' element");
        }

        @Test
        @DisplayName("Should filter attributes by prefix")
        void filterAttributes_byPrefix() {
            XPathEditorContext context = new XPathEditorContext.Builder()
                    .contextType(XPathContextType.AFTER_AT)
                    .currentToken("is")
                    .build();

            List<CompletionItem> items = xpathProvider.getCompletions(context);

            // Should include 'isbn' attribute
            boolean hasIsbn = items.stream().anyMatch(i -> i.getLabel().equals("isbn"));
            assertTrue(hasIsbn, "Should include 'isbn' attribute");
        }

        @Test
        @DisplayName("Should filter functions by prefix")
        void filterFunctions_byPrefix() {
            XPathEditorContext context = new XPathEditorContext.Builder()
                    .contextType(XPathContextType.IN_PREDICATE)
                    .currentToken("con")
                    .build();

            List<CompletionItem> items = xpathProvider.getCompletions(context);

            boolean hasContains = items.stream().anyMatch(i -> i.getLabel().equals("contains"));
            boolean hasConcat = items.stream().anyMatch(i -> i.getLabel().equals("concat"));

            assertTrue(hasContains, "Should include 'contains' function");
            assertTrue(hasConcat, "Should include 'concat' function");
        }
    }

    @Nested
    @DisplayName("Sorting and Limiting Tests")
    class SortingLimitingTests {

        @Test
        @DisplayName("Results should be sorted by relevance score")
        void results_sortedByRelevance() {
            XPathEditorContext context = new XPathEditorContext.Builder()
                    .contextType(XPathContextType.PATH_START)
                    .currentToken("")
                    .build();

            List<CompletionItem> items = xpathProvider.getCompletions(context);

            if (items.size() > 1) {
                for (int i = 0; i < items.size() - 1; i++) {
                    int currentScore = items.get(i).getRelevanceScore();
                    int nextScore = items.get(i + 1).getRelevanceScore();
                    assertTrue(currentScore >= nextScore,
                            "Items should be sorted by descending score");
                }
            }
        }

        @Test
        @DisplayName("Results should be limited to 50 items")
        void results_limitedTo50() {
            XPathEditorContext context = new XPathEditorContext.Builder()
                    .contextType(XPathContextType.PATH_START)
                    .currentToken("")
                    .build();

            List<CompletionItem> items = xpathProvider.getCompletions(context);

            assertTrue(items.size() <= 50, "Should return at most 50 items");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Null context should return empty list")
        void nullContext_returnsEmpty() {
            List<CompletionItem> items = xpathProvider.getCompletions(null);

            assertTrue(items.isEmpty(), "Null context should return empty list");
        }

        @Test
        @DisplayName("Provider should work without XML extractor data")
        void workWithoutExtractorData() {
            XmlDocumentElementExtractor emptyExtractor = new XmlDocumentElementExtractor();
            XPathCompletionProvider provider = new XPathCompletionProvider(emptyExtractor, false);

            XPathEditorContext context = new XPathEditorContext.Builder()
                    .contextType(XPathContextType.PATH_START)
                    .currentToken("")
                    .build();

            List<CompletionItem> items = provider.getCompletions(context);

            // Should still provide functions, axes, etc.
            assertFalse(items.isEmpty(), "Should provide completions even without XML data");
        }

        @Test
        @DisplayName("Null extractor should be handled gracefully")
        void nullExtractor_handledGracefully() {
            XPathCompletionProvider provider = new XPathCompletionProvider(null, false);

            XPathEditorContext context = new XPathEditorContext.Builder()
                    .contextType(XPathContextType.PATH_START)
                    .currentToken("")
                    .build();

            assertDoesNotThrow(() -> provider.getCompletions(context),
                    "Should handle null extractor gracefully");
        }

        @Test
        @DisplayName("UNKNOWN context should provide fallback completions")
        void unknownContext_providesFallback() {
            XPathEditorContext context = new XPathEditorContext.Builder()
                    .contextType(XPathContextType.UNKNOWN)
                    .currentToken("")
                    .build();

            List<CompletionItem> items = xpathProvider.getCompletions(context);

            assertFalse(items.isEmpty(), "UNKNOWN context should still provide completions");
        }
    }

    @Nested
    @DisplayName("Provider Mode Tests")
    class ProviderModeTests {

        @Test
        @DisplayName("isXQueryMode should return correct value")
        void isXQueryMode_returnsCorrectValue() {
            assertFalse(xpathProvider.isXQueryMode(), "XPath provider should not be in XQuery mode");
            assertTrue(xqueryProvider.isXQueryMode(), "XQuery provider should be in XQuery mode");
        }
    }

    @Nested
    @DisplayName("Path-Aware Completion Tests")
    class PathAwareCompletionTests {

        @Test
        @DisplayName("At root (/) should show only root element")
        void atRoot_showsOnlyRootElement() {
            XPathEditorContext context = new XPathEditorContext.Builder()
                    .contextType(XPathContextType.AFTER_SLASH)
                    .currentToken("")
                    .isAbsolutePath(true)
                    .xpathPath(List.of())
                    .build();

            List<CompletionItem> items = xpathProvider.getCompletions(context);

            // Should include the root element "bookstore"
            boolean hasBookstore = items.stream()
                    .filter(i -> i.getType() == CompletionItemType.ELEMENT)
                    .anyMatch(i -> i.getLabel().equals("bookstore"));

            assertTrue(hasBookstore, "Should include root element 'bookstore'");

            // Should NOT include non-root elements at root position
            long elementCount = items.stream()
                    .filter(i -> i.getType() == CompletionItemType.ELEMENT)
                    .count();

            assertEquals(1, elementCount, "Should show only 1 element (root) at root position");
        }

        @Test
        @DisplayName("After /bookstore/ should show children of bookstore")
        void afterBookstore_showsBookstoreChildren() {
            XPathEditorContext context = new XPathEditorContext.Builder()
                    .contextType(XPathContextType.AFTER_SLASH)
                    .currentToken("")
                    .isAbsolutePath(true)
                    .xpathPath(List.of("bookstore"))
                    .build();

            List<CompletionItem> items = xpathProvider.getCompletions(context);

            // Should include "book" which is a child of bookstore
            boolean hasBook = items.stream()
                    .filter(i -> i.getType() == CompletionItemType.ELEMENT)
                    .anyMatch(i -> i.getLabel().equals("book"));

            assertTrue(hasBook, "Should include 'book' as child of bookstore");
        }

        @Test
        @DisplayName("After /bookstore/book/ should show children of book")
        void afterBook_showsBookChildren() {
            XPathEditorContext context = new XPathEditorContext.Builder()
                    .contextType(XPathContextType.AFTER_SLASH)
                    .currentToken("")
                    .isAbsolutePath(true)
                    .xpathPath(List.of("bookstore", "book"))
                    .build();

            List<CompletionItem> items = xpathProvider.getCompletions(context);

            // Should include title, author, price which are children of book
            boolean hasTitle = items.stream()
                    .filter(i -> i.getType() == CompletionItemType.ELEMENT)
                    .anyMatch(i -> i.getLabel().equals("title"));
            boolean hasAuthor = items.stream()
                    .filter(i -> i.getType() == CompletionItemType.ELEMENT)
                    .anyMatch(i -> i.getLabel().equals("author"));
            boolean hasPrice = items.stream()
                    .filter(i -> i.getType() == CompletionItemType.ELEMENT)
                    .anyMatch(i -> i.getLabel().equals("price"));

            assertTrue(hasTitle, "Should include 'title' as child of book");
            assertTrue(hasAuthor, "Should include 'author' as child of book");
            assertTrue(hasPrice, "Should include 'price' as child of book");
        }

        @Test
        @DisplayName("After // (descendant) should show all elements")
        void afterDoubleSlash_showsAllElements() {
            XPathEditorContext context = new XPathEditorContext.Builder()
                    .contextType(XPathContextType.AFTER_DOUBLE_SLASH)
                    .currentToken("")
                    .build();

            List<CompletionItem> items = xpathProvider.getCompletions(context);

            // Should include all elements
            long elementCount = items.stream()
                    .filter(i -> i.getType() == CompletionItemType.ELEMENT)
                    .count();

            assertTrue(elementCount > 1, "Should show multiple elements for descendant-or-self axis");
        }

        @Test
        @DisplayName("Path-aware completions with prefix filter")
        void pathAware_withPrefixFilter() {
            XPathEditorContext context = new XPathEditorContext.Builder()
                    .contextType(XPathContextType.AFTER_SLASH)
                    .currentToken("ti")
                    .isAbsolutePath(true)
                    .xpathPath(List.of("bookstore", "book"))
                    .build();

            List<CompletionItem> items = xpathProvider.getCompletions(context);

            // Should include 'title' but not 'author' or 'price'
            boolean hasTitle = items.stream()
                    .filter(i -> i.getType() == CompletionItemType.ELEMENT)
                    .anyMatch(i -> i.getLabel().equals("title"));

            assertTrue(hasTitle, "Should include 'title' matching prefix 'ti'");
        }
    }
}
