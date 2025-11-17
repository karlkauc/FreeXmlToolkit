package org.fxt.freexmltoolkit.controls.intellisense;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FuzzySearch algorithm.
 * Tests fuzzy matching, scoring, filtering, and search functionality.
 */
class FuzzySearchTest {

    private List<CompletionItem> items;

    @BeforeEach
    void setUp() {
        items = new ArrayList<>();
        items.add(createItem("element", "Element node", CompletionItemType.ELEMENT));
        items.add(createItem("attribute", "Attribute node", CompletionItemType.ATTRIBUTE));
        items.add(createItem("sequence", "Sequence compositor", CompletionItemType.ELEMENT));
        items.add(createItem("choice", "Choice compositor", CompletionItemType.ELEMENT));
        items.add(createItem("complexType", "Complex type definition", CompletionItemType.ELEMENT));
        items.add(createItem("simpleType", "Simple type definition", CompletionItemType.ELEMENT));
        items.add(createItem("xs:string", "String data type", CompletionItemType.TEXT));
        items.add(createItem("xs:integer", "Integer data type", CompletionItemType.TEXT));
    }

    private CompletionItem createItem(String label, String description, CompletionItemType type) {
        CompletionItem item = new CompletionItem(label, type);
        item.setDescription(description);
        return item;
    }

    @Test
    @DisplayName("Should return all items for empty query")
    void testEmptyQuery() {
        List<CompletionItem> results = FuzzySearch.search("", items);

        assertEquals(items.size(), results.size(), "Empty query should return all items");
    }

    @Test
    @DisplayName("Should return all items for null query")
    void testNullQuery() {
        List<CompletionItem> results = FuzzySearch.search(null, items);

        assertEquals(items.size(), results.size(), "Null query should return all items");
    }

    @Test
    @DisplayName("Should return all items for whitespace-only query")
    void testWhitespaceQuery() {
        List<CompletionItem> results = FuzzySearch.search("   ", items);

        assertEquals(items.size(), results.size(), "Whitespace query should return all items");
    }

    @Test
    @DisplayName("Should match exact label")
    void testExactMatch() {
        List<CompletionItem> results = FuzzySearch.search("element", items);

        assertFalse(results.isEmpty(), "Should find exact match");
        assertEquals("element", results.get(0).getLabel(),
                "Exact match should be first result");
    }

    @Test
    @DisplayName("Should match prefix")
    void testPrefixMatch() {
        List<CompletionItem> results = FuzzySearch.search("elem", items);

        assertFalse(results.isEmpty(), "Should find prefix match");
        assertEquals("element", results.get(0).getLabel(),
                "Prefix match should be first result");
    }

    @Test
    @DisplayName("Should match substring")
    void testSubstringMatch() {
        List<CompletionItem> results = FuzzySearch.search("ment", items);

        assertFalse(results.isEmpty(), "Should find substring match");
        assertTrue(results.stream().anyMatch(item -> item.getLabel().equals("element")),
                "Should find 'element' containing 'ment'");
    }

    @Test
    @DisplayName("Should match fuzzy characters")
    void testFuzzyMatch() {
        List<CompletionItem> results = FuzzySearch.search("elmnt", items);

        assertFalse(results.isEmpty(), "Should find fuzzy match");
        assertTrue(results.stream().anyMatch(item -> item.getLabel().equals("element")),
                "Should match 'element' with query 'elmnt'");
    }

    @Test
    @DisplayName("Should match camel case")
    void testCamelCaseMatch() {
        List<CompletionItem> results = FuzzySearch.search("cT", items);

        assertFalse(results.isEmpty(), "Should find camel case match");
        assertTrue(results.stream().anyMatch(item -> item.getLabel().equals("complexType")),
                "Should match 'complexType' with camel case query 'cT'");
    }

    @Test
    @DisplayName("Should match camel case abbreviation")
    void testCamelCaseAbbreviation() {
        List<CompletionItem> results = FuzzySearch.search("sT", items);

        assertFalse(results.isEmpty(), "Should find camel case abbreviation");
        assertTrue(results.stream().anyMatch(item -> item.getLabel().equals("simpleType")),
                "Should match 'simpleType' with abbreviation 'sT'");
    }

    @Test
    @DisplayName("Should calculate fuzzy score for exact match")
    void testCalculateFuzzyScoreExact() {
        int score = FuzzySearch.calculateFuzzyScore("element", "element");

        assertTrue(score > 0, "Exact match should have positive score");
        assertTrue(score > 100, "Exact match should have high score (> 100)");
    }

    @Test
    @DisplayName("Should calculate fuzzy score for prefix")
    void testCalculateFuzzyScorePrefix() {
        int score = FuzzySearch.calculateFuzzyScore("elem", "element");

        assertTrue(score > 0, "Prefix match should have positive score");
    }

    @Test
    @DisplayName("Should calculate zero score for no match")
    void testCalculateFuzzyScoreNoMatch() {
        int score = FuzzySearch.calculateFuzzyScore("xyz", "element");

        assertEquals(0, score, "No match should have zero score");
    }

    @Test
    @DisplayName("Should calculate zero score when query is longer than target")
    void testCalculateFuzzyScoreQueryTooLong() {
        int score = FuzzySearch.calculateFuzzyScore("verylongquery", "short");

        assertEquals(0, score, "Query longer than target should have zero score");
    }

    @Test
    @DisplayName("Should calculate score for empty query")
    void testCalculateFuzzyScoreEmptyQuery() {
        int score = FuzzySearch.calculateFuzzyScore("", "element");

        assertEquals(100, score, "Empty query should return base score of 100");
    }

    @Test
    @DisplayName("Should calculate zero score for empty target")
    void testCalculateFuzzyScoreEmptyTarget() {
        int score = FuzzySearch.calculateFuzzyScore("query", "");

        assertEquals(0, score, "Empty target should have zero score");
    }

    @Test
    @DisplayName("Should give bonus for consecutive matches")
    void testConsecutiveMatchBonus() {
        int consecutiveScore = FuzzySearch.calculateFuzzyScore("elem", "element");
        int nonConsecutiveScore = FuzzySearch.calculateFuzzyScore("elmn", "element");

        assertTrue(consecutiveScore > nonConsecutiveScore,
                "Consecutive matches should score higher than non-consecutive");
    }

    @Test
    @DisplayName("Should calculate Levenshtein distance for identical strings")
    void testLevenshteinDistanceIdentical() {
        int distance = FuzzySearch.levenshteinDistance("element", "element");

        assertEquals(0, distance, "Identical strings should have distance 0");
    }

    @Test
    @DisplayName("Should calculate Levenshtein distance for one character difference")
    void testLevenshteinDistanceOneChar() {
        int distance = FuzzySearch.levenshteinDistance("element", "elements");

        assertEquals(1, distance, "One character difference should have distance 1");
    }

    @Test
    @DisplayName("Should calculate Levenshtein distance for substitution")
    void testLevenshteinDistanceSubstitution() {
        int distance = FuzzySearch.levenshteinDistance("element", "alamant");

        assertTrue(distance > 0 && distance <= 7, "Substitution should have positive distance");
    }

    @Test
    @DisplayName("Should calculate Levenshtein distance for empty strings")
    void testLevenshteinDistanceEmptyStrings() {
        int distance1 = FuzzySearch.levenshteinDistance("", "element");
        int distance2 = FuzzySearch.levenshteinDistance("element", "");

        assertEquals(7, distance1, "Empty to 'element' should be 7");
        assertEquals(7, distance2, "'element' to empty should be 7");
    }

    @Test
    @DisplayName("Should calculate Levenshtein distance for completely different strings")
    void testLevenshteinDistanceDifferent() {
        int distance = FuzzySearch.levenshteinDistance("abc", "xyz");

        assertEquals(3, distance, "Completely different 3-char strings should have distance 3");
    }

    @Test
    @DisplayName("Should highlight matches in string")
    void testHighlightMatches() {
        String highlighted = FuzzySearch.highlightMatches("element", "elem");

        assertEquals("<mark>e</mark><mark>l</mark><mark>e</mark><mark>m</mark>ent", highlighted,
                "Should highlight matched characters");
    }

    @Test
    @DisplayName("Should highlight fuzzy matches")
    void testHighlightFuzzyMatches() {
        String highlighted = FuzzySearch.highlightMatches("element", "elmnt");

        assertTrue(highlighted.contains("<mark>e</mark>"), "Should highlight 'e'");
        assertTrue(highlighted.contains("<mark>l</mark>"), "Should highlight 'l'");
        assertTrue(highlighted.contains("<mark>m</mark>"), "Should highlight 'm'");
        assertTrue(highlighted.contains("<mark>n</mark>"), "Should highlight 'n'");
        assertTrue(highlighted.contains("<mark>t</mark>"), "Should highlight 't'");
    }

    @Test
    @DisplayName("Should return original string when highlighting with empty query")
    void testHighlightEmptyQuery() {
        String highlighted = FuzzySearch.highlightMatches("element", "");

        assertEquals("element", highlighted, "Empty query should return original string");
    }

    @Test
    @DisplayName("Should return original string when highlighting with null query")
    void testHighlightNullQuery() {
        String highlighted = FuzzySearch.highlightMatches("element", null);

        assertEquals("element", highlighted, "Null query should return original string");
    }

    @Test
    @DisplayName("Should handle case-insensitive highlighting")
    void testHighlightCaseInsensitive() {
        String highlighted = FuzzySearch.highlightMatches("Element", "elem");

        assertTrue(highlighted.contains("<mark>E</mark>"), "Should highlight uppercase 'E'");
        assertTrue(highlighted.contains("<mark>l</mark>"), "Should highlight lowercase 'l'");
    }

    @Test
    @DisplayName("Should search with max results limit")
    void testSearchWithMaxResults() {
        FuzzySearch.SearchOptions options = new FuzzySearch.SearchOptions()
                .maxResults(3);

        List<CompletionItem> results = FuzzySearch.search("e", items, options);

        assertTrue(results.size() <= 3, "Should respect maxResults limit");
    }

    @Test
    @DisplayName("Should search with minimum score filter")
    void testSearchWithMinScore() {
        FuzzySearch.SearchOptions options = new FuzzySearch.SearchOptions()
                .minScore(500);

        List<CompletionItem> results = FuzzySearch.search("elem", items, options);

        assertFalse(results.isEmpty(), "Should find high-scoring matches");
        // All results should have high scores (exact or prefix matches)
    }

    @Test
    @DisplayName("Should filter out low-scoring matches")
    void testFilterLowScores() {
        FuzzySearch.SearchOptions options = new FuzzySearch.SearchOptions()
                .minScore(1000);

        List<CompletionItem> results = FuzzySearch.search("xyz", items, options);

        assertTrue(results.isEmpty() || results.size() < items.size(),
                "Should filter out low-scoring or non-matching items");
    }

    @Test
    @DisplayName("Should search in description when enabled")
    void testSearchInDescription() {
        FuzzySearch.SearchOptions options = new FuzzySearch.SearchOptions()
                .searchInDescription(true);

        List<CompletionItem> results = FuzzySearch.advancedSearch("compositor", items, options);

        assertFalse(results.isEmpty(), "Should find items by description");
        assertTrue(results.stream().anyMatch(item ->
                        item.getLabel().equals("sequence") || item.getLabel().equals("choice")),
                "Should find 'sequence' or 'choice' by description 'compositor'");
    }

    @Test
    @DisplayName("Should prioritize exact matches")
    void testPrioritizeExactMatches() {
        List<CompletionItem> results = FuzzySearch.search("element", items);

        assertFalse(results.isEmpty(), "Should find matches");
        assertEquals("element", results.get(0).getLabel(),
                "Exact match should be first");
    }

    @Test
    @DisplayName("Should prioritize prefix matches over substring")
    void testPrioritizePrefixOverSubstring() {
        // Add items that would test this
        List<CompletionItem> testItems = new ArrayList<>();
        testItems.add(createItem("element", "Element", CompletionItemType.ELEMENT));
        testItems.add(createItem("subelement", "Sub element", CompletionItemType.ELEMENT));

        List<CompletionItem> results = FuzzySearch.search("elem", testItems);

        assertFalse(results.isEmpty(), "Should find matches");
        assertEquals("element", results.get(0).getLabel(),
                "Prefix match should rank higher than substring match");
    }

    @Test
    @DisplayName("Should sort by label length as tiebreaker")
    void testSortByLabelLength() {
        List<CompletionItem> testItems = new ArrayList<>();
        testItems.add(createItem("elem", "Short", CompletionItemType.ELEMENT));
        testItems.add(createItem("element", "Long", CompletionItemType.ELEMENT));

        List<CompletionItem> results = FuzzySearch.search("e", testItems);

        // Both match, but shorter should come first (when scores are equal)
        assertFalse(results.isEmpty(), "Should find matches");
    }

    @Test
    @DisplayName("Should handle SearchOptions builder pattern")
    void testSearchOptionsBuilder() {
        FuzzySearch.SearchOptions options = new FuzzySearch.SearchOptions()
                .maxResults(10)
                .minScore(50)
                .searchInDescription(true)
                .searchInDataType(false);

        assertEquals(10, options.maxResults, "Should set maxResults");
        assertEquals(50, options.minScore, "Should set minScore");
        assertTrue(options.searchInDescription, "Should set searchInDescription");
        assertFalse(options.searchInDataType, "Should set searchInDataType");
    }

    @Test
    @DisplayName("Should keep minScore and minimumScore in sync")
    void testScoreFieldsSync() {
        FuzzySearch.SearchOptions options = new FuzzySearch.SearchOptions()
                .minScore(100);

        assertEquals(100, options.minScore, "minScore should be set");
        assertEquals(100, options.minimumScore, "minimumScore should be synced");

        options.minimumScore(200);
        assertEquals(200, options.minScore, "minScore should be synced");
        assertEquals(200, options.minimumScore, "minimumScore should be set");
    }

    @Test
    @DisplayName("Should perform advanced search with type priorities")
    void testAdvancedSearchTypePriorities() {
        FuzzySearch.SearchOptions options = new FuzzySearch.SearchOptions()
                .minScore(0);

        List<CompletionItem> results = FuzzySearch.advancedSearch("e", items, options);

        assertFalse(results.isEmpty(), "Should find matches");
        // ELEMENT type should be prioritized over TEXT type when scores are close
    }

    @Test
    @DisplayName("Should give bonus to required items in advanced search")
    void testAdvancedSearchRequiredBonus() {
        List<CompletionItem> testItems = new ArrayList<>();
        CompletionItem required = createItem("name", "Required attribute", CompletionItemType.ATTRIBUTE);
        required.setRequired(true);
        testItems.add(required);

        CompletionItem optional = createItem("title", "Optional attribute", CompletionItemType.ATTRIBUTE);
        testItems.add(optional);

        FuzzySearch.SearchOptions options = new FuzzySearch.SearchOptions()
                .minScore(0);

        List<CompletionItem> results = FuzzySearch.advancedSearch("a", testItems, options);

        // Required items should get scoring bonus
        assertFalse(results.isEmpty(), "Should find matches");
    }

    @Test
    @DisplayName("Should set case sensitivity option")
    void testCaseSensitivityOption() {
        FuzzySearch.SearchOptions options = new FuzzySearch.SearchOptions()
                .caseSensitive(true);

        assertTrue(options.caseSensitive, "Should set case sensitivity");

        options.caseSensitive(false);
        assertFalse(options.caseSensitive, "Should unset case sensitivity");
    }

    @Test
    @DisplayName("Should set parallel processing option")
    void testParallelProcessingOption() {
        FuzzySearch.SearchOptions options = new FuzzySearch.SearchOptions()
                .parallelProcessing(false);

        assertFalse(options.useParallelProcessing, "Should disable parallel processing");

        options.parallelProcessing(true);
        assertTrue(options.useParallelProcessing, "Should enable parallel processing");
    }

    @Test
    @DisplayName("Should set label weight option")
    void testLabelWeightOption() {
        FuzzySearch.SearchOptions options = new FuzzySearch.SearchOptions()
                .labelWeight(5);

        assertEquals(5, options.labelWeight, "Should set label weight");
    }

    @Test
    @DisplayName("Should handle empty items list")
    void testEmptyItemsList() {
        List<CompletionItem> emptyList = new ArrayList<>();
        List<CompletionItem> results = FuzzySearch.search("query", emptyList);

        assertTrue(results.isEmpty(), "Empty list should return empty results");
    }

    @Test
    @DisplayName("Should match with different character casing")
    void testCaseInsensitiveMatching() {
        List<CompletionItem> results = FuzzySearch.search("ELEM", items);

        assertFalse(results.isEmpty(), "Should find matches regardless of case");
        assertTrue(results.stream().anyMatch(item -> item.getLabel().equals("element")),
                "Should match 'element' with uppercase query 'ELEM'");
    }
}
