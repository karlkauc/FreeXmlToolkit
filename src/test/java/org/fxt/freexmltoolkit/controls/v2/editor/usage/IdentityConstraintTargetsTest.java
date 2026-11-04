package org.fxt.freexmltoolkit.controls.v2.editor.usage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class IdentityConstraintTargetsTest {

    private static boolean matches(String selector, String field, String... steps) {
        return IdentityConstraintTargets.matches(selector, field, List.of(steps));
    }

    @Test
    void selectorPlusFieldLeadsToTheTarget() {
        assertTrue(matches("FundStaticData/Benchmarks/Benchmark", "BenchmarkID",
                "FundStaticData", "Benchmarks", "Benchmark", "BenchmarkID"));
        assertFalse(matches("FundStaticData/Benchmarks/Benchmark", "BenchmarkID",
                "FundStaticData", "Benchmarks", "Benchmark"), "the selected element is no field target");
        assertFalse(matches("Benchmarks/Benchmark", "BenchmarkID", "Other", "Benchmark", "BenchmarkID"));
    }

    @Test
    void dotsDescendantsAndWildcards() {
        assertTrue(matches(".", "@ccy", "@ccy"));
        assertTrue(matches("./Book", "./ISBN", "Book", "ISBN"));
        assertTrue(matches(".//Book", "ISBN", "Shelf", "Row", "Book", "ISBN"));
        assertTrue(matches(".//Book", "ISBN", "Book", "ISBN"), ".// allows zero steps");
        assertTrue(matches("*/Book", "ISBN", "Shelf", "Book", "ISBN"));
        assertTrue(matches("Book", "@*", "Book", "@isbn"));
        assertFalse(matches("Book", "@*", "Book", "isbn"), "@* selects attributes only");
    }

    @Test
    void alternativesPrefixesAndAxes() {
        assertTrue(matches("Book|Loan", "BookRef", "Loan", "BookRef"));
        assertTrue(matches("Book", "ISBN|@isbn", "Book", "@isbn"));
        assertTrue(matches("f:Book", "f:ISBN", "Book", "ISBN"), "compared by local name");
        assertTrue(matches("Book", "ISBN", "f:Book", "f:ISBN"));
        assertTrue(matches("p:*", "ISBN", "Book", "ISBN"));
        assertTrue(matches("child::Book", "attribute::isbn", "Book", "@isbn"));
    }

    @Test
    void incompleteInputNeverMatches() {
        assertFalse(matches(null, "x", "x"));
        assertFalse(matches("a", null, "a", "x"));
        assertFalse(matches("", "x", "x"));
        assertFalse(matches("a//b", "x", "a", "b", "x"), "an inner // is not allowed");
    }
}
