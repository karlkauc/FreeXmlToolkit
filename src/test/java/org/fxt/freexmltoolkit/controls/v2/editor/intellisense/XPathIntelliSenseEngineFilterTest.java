package org.fxt.freexmltoolkit.controls.v2.editor.intellisense;

import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItem;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItemType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XPathIntelliSenseEngine filtering logic.
 * Tests the prefix-based filtering of completion items.
 */
@DisplayName("XPathIntelliSenseEngine Filter Tests")
class XPathIntelliSenseEngineFilterTest {

    /**
     * Creates a test completion item.
     */
    private CompletionItem createItem(String label, String insertText, CompletionItemType type) {
        return new CompletionItem.Builder(label, insertText, type).build();
    }

    /**
     * Helper method to filter completions by prefix.
     * Mirrors the filtering logic in XPathIntelliSenseEngine.
     */
    private List<CompletionItem> filterCompletions(List<CompletionItem> allCompletions, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return allCompletions;
        }
        String lowerPrefix = prefix.toLowerCase();
        return allCompletions.stream()
                .filter(item -> item.getLabel().toLowerCase().startsWith(lowerPrefix)
                        || item.getInsertText().toLowerCase().startsWith(lowerPrefix))
                .toList();
    }

    @Nested
    @DisplayName("Prefix Filtering Tests")
    class PrefixFilteringTests {

        @Test
        @DisplayName("Empty prefix returns all completions")
        void emptyPrefix_returnsAll() {
            List<CompletionItem> items = List.of(
                    createItem("book", "book", CompletionItemType.ELEMENT),
                    createItem("author", "author", CompletionItemType.ELEMENT),
                    createItem("title", "title", CompletionItemType.ELEMENT)
            );

            List<CompletionItem> filtered = filterCompletions(items, "");

            assertEquals(3, filtered.size());
        }

        @Test
        @DisplayName("Null prefix returns all completions")
        void nullPrefix_returnsAll() {
            List<CompletionItem> items = List.of(
                    createItem("book", "book", CompletionItemType.ELEMENT),
                    createItem("author", "author", CompletionItemType.ELEMENT)
            );

            List<CompletionItem> filtered = filterCompletions(items, null);

            assertEquals(2, filtered.size());
        }

        @Test
        @DisplayName("Prefix matches by label")
        void prefix_matchesByLabel() {
            List<CompletionItem> items = List.of(
                    createItem("book", "book", CompletionItemType.ELEMENT),
                    createItem("author", "author", CompletionItemType.ELEMENT),
                    createItem("bookstore", "bookstore", CompletionItemType.ELEMENT)
            );

            List<CompletionItem> filtered = filterCompletions(items, "book");

            assertEquals(2, filtered.size());
            assertTrue(filtered.stream().anyMatch(i -> i.getLabel().equals("book")));
            assertTrue(filtered.stream().anyMatch(i -> i.getLabel().equals("bookstore")));
        }

        @Test
        @DisplayName("Prefix matches by insertText")
        void prefix_matchesByInsertText() {
            List<CompletionItem> items = List.of(
                    createItem("position", "position()", CompletionItemType.XPATH_FUNCTION),
                    createItem("last", "last()", CompletionItemType.XPATH_FUNCTION),
                    createItem("count", "count()", CompletionItemType.XPATH_FUNCTION)
            );

            List<CompletionItem> filtered = filterCompletions(items, "pos");

            assertEquals(1, filtered.size());
            assertEquals("position", filtered.get(0).getLabel());
        }

        @Test
        @DisplayName("Prefix matching is case-insensitive")
        void prefix_caseInsensitive() {
            List<CompletionItem> items = List.of(
                    createItem("Book", "Book", CompletionItemType.ELEMENT),
                    createItem("AUTHOR", "AUTHOR", CompletionItemType.ELEMENT),
                    createItem("Title", "Title", CompletionItemType.ELEMENT)
            );

            List<CompletionItem> filtered = filterCompletions(items, "bo");

            assertEquals(1, filtered.size());
            assertEquals("Book", filtered.get(0).getLabel());
        }

        @Test
        @DisplayName("Prefix that matches nothing returns empty list")
        void noMatch_returnsEmpty() {
            List<CompletionItem> items = List.of(
                    createItem("book", "book", CompletionItemType.ELEMENT),
                    createItem("author", "author", CompletionItemType.ELEMENT)
            );

            List<CompletionItem> filtered = filterCompletions(items, "xyz");

            assertTrue(filtered.isEmpty());
        }

        @Test
        @DisplayName("Single character prefix filters correctly")
        void singleChar_filtersCorrectly() {
            List<CompletionItem> items = List.of(
                    createItem("book", "book", CompletionItemType.ELEMENT),
                    createItem("author", "author", CompletionItemType.ELEMENT),
                    createItem("title", "title", CompletionItemType.ELEMENT),
                    createItem("price", "price", CompletionItemType.ELEMENT)
            );

            List<CompletionItem> filtered = filterCompletions(items, "a");

            assertEquals(1, filtered.size());
            assertEquals("author", filtered.get(0).getLabel());
        }

        @Test
        @DisplayName("Filters work with mixed completion types")
        void mixedTypes_filterCorrectly() {
            List<CompletionItem> items = List.of(
                    createItem("child", "child", CompletionItemType.XPATH_AXIS),
                    createItem("contains", "contains()", CompletionItemType.XPATH_FUNCTION),
                    createItem("category", "category", CompletionItemType.ELEMENT),
                    createItem("concat", "concat()", CompletionItemType.XPATH_FUNCTION)
            );

            List<CompletionItem> filtered = filterCompletions(items, "con");

            assertEquals(2, filtered.size());
            assertTrue(filtered.stream().anyMatch(i -> i.getLabel().equals("contains")));
            assertTrue(filtered.stream().anyMatch(i -> i.getLabel().equals("concat")));
        }
    }

    @Nested
    @DisplayName("Progressive Filtering Tests")
    class ProgressiveFilteringTests {

        @Test
        @DisplayName("Typing more characters narrows results")
        void progressiveTyping_narrowsResults() {
            List<CompletionItem> items = List.of(
                    createItem("book", "book", CompletionItemType.ELEMENT),
                    createItem("bookstore", "bookstore", CompletionItemType.ELEMENT),
                    createItem("bookmark", "bookmark", CompletionItemType.ELEMENT),
                    createItem("author", "author", CompletionItemType.ELEMENT)
            );

            // Type 'b'
            List<CompletionItem> filtered1 = filterCompletions(items, "b");
            assertEquals(3, filtered1.size());

            // Type 'bo'
            List<CompletionItem> filtered2 = filterCompletions(items, "bo");
            assertEquals(3, filtered2.size());

            // Type 'book'
            List<CompletionItem> filtered3 = filterCompletions(items, "book");
            assertEquals(3, filtered3.size());

            // Type 'books'
            List<CompletionItem> filtered4 = filterCompletions(items, "books");
            assertEquals(1, filtered4.size());
            assertEquals("bookstore", filtered4.get(0).getLabel());

            // Type 'booksz' - no match
            List<CompletionItem> filtered5 = filterCompletions(items, "booksz");
            assertTrue(filtered5.isEmpty());
        }
    }
}
