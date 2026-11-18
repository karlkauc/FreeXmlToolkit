package org.fxt.freexmltoolkit.service.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** The edit distance and the unprefixed-name scan behind {@link QuerySuggestions}. */
class QuerySuggestionsTest {

    @Test
    void distanceCountsATranspositionAsOneEdit() {
        assertEquals(0, QuerySuggestions.distance("count", "count"));
        assertEquals(1, QuerySuggestions.distance("cuont", "count"));
        assertEquals(1, QuerySuggestions.distance("distinct-value", "distinct-values"));
        assertEquals(2, QuerySuggestions.distance("strng-jon", "string-join"));
    }

    @Test
    void theFirstUnprefixedElementStepIsFound() {
        assertEquals("Fund", QuerySuggestions.firstUnprefixedName("//Fund/Name"));
        assertEquals("Name", QuerySuggestions.firstUnprefixedName("//*:Fund/Name"));
        assertEquals("Fund", QuerySuggestions.firstUnprefixedName("count(//Fund)"));
        assertNull(QuerySuggestions.firstUnprefixedName("//p:Fund/p:Name"));
        assertNull(QuerySuggestions.firstUnprefixedName("//*:Fund/text()"), "a node test, no name");
        assertNull(QuerySuggestions.firstUnprefixedName("//*:Fund/child::*:Name"), "an axis");
        assertNull(QuerySuggestions.firstUnprefixedName("1 + 2"));
    }
}
