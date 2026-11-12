package org.fxt.freexmltoolkit.service.standards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class XQueryCompatibilityCheckTest {

    @ParameterizedTest
    @CsvSource(delimiterString = " @ ", quoteCharacter = '`', value = {
            "let $x := 1 return $x || 'a' @ V1_0",
            "for $i in (1,2) group by $k := $i return $k @ V1_0",
            "switch (1) case 1 return 2 default return 3 @ V1_0",
            "try { 1 } catch * { 2 } @ V1_0",
            "head((1,2)) @ V1_0",
            "(1,2) ! string() @ V1_0",
            "function($x) { $x }(1) @ V1_0",
            "for $i at $p in (1,2) count $c return $c @ V1_0",
            "declare %private function local:f() { 1 }; local:f() @ V1_0",
            "map { 1 : 2 } @ V3_0",
            "(1) => count() @ V3_0",
            "parse-json('{}') @ V3_0",
            "math:pi() @ V1_0"})
    void newerConstructsAreReported(String query, XQueryVersion target) {
        List<CompatibilityIssue> issues = XQueryCompatibilityCheck.check(query, target);
        assertFalse(issues.isEmpty(), query + " in " + target);
    }

    @ParameterizedTest
    @CsvSource(delimiterString = " @ ", quoteCharacter = '`', value = {
            "for $i in 1 to 3 where $i > 1 order by $i descending return <a>{$i}</a> @ V1_0",
            "declare function local:f($a) { $a }; local:f(1) @ V1_0",
            "string-join(('a', 'b'), ',') @ V1_0",
            "'head(1) || group by' (: switch ( try { :) @ V1_0",
            "let $x := 1 return $x || 'a' @ V3_0",
            "head((1,2)) @ V3_0",
            "map { 1 : 2 } @ V3_1"})
    void fittingQueriesPass(String query, XQueryVersion target) {
        assertEquals(List.of(), XQueryCompatibilityCheck.check(query, target), query + " in " + target);
    }

    @Test
    void issuesCarryTheLine() {
        var issues = XQueryCompatibilityCheck.check("let $a := 1\nreturn\n  head(($a, 2))", XQueryVersion.V1_0);
        assertTrue(issues.stream().anyMatch(i -> i.line() == 3), issues.toString());
    }

    @Test
    void aNewerVersionDeclarationIsReported() {
        var issues = XQueryCompatibilityCheck.check("xquery version \"3.1\"; 1", XQueryVersion.V1_0);
        assertTrue(issues.stream().anyMatch(i -> i.code().equals("FXT-COMPAT-VERSION")), issues.toString());
    }
}
