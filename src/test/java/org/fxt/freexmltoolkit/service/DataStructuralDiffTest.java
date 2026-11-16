package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.fxt.freexmltoolkit.service.DataStructuralDiff.Format;
import org.fxt.freexmltoolkit.service.DataStructuralDiff.Options;
import org.fxt.freexmltoolkit.service.XmlStructuralDiff.Change;
import org.fxt.freexmltoolkit.service.XmlStructuralDiff.Difference;
import org.fxt.freexmltoolkit.service.XmlStructuralDiff.NodeKind;
import org.fxt.freexmltoolkit.service.XmlStructuralDiff.Result;
import org.junit.jupiter.api.Test;

class DataStructuralDiffTest {

    private static Result yaml(String left, String right, Options options) {
        return DataStructuralDiff.compare(left, Format.YAML, right, Format.YAML, options);
    }

    @Test
    void formattingCommentsOrderAndAnchorsDoNotCount() {
        String left = """
                # settings
                defaults: &d {retries: 3, timeout: 1.50}
                service:
                  <<: *d
                  name: api
                """;
        String right = """
                service: {name: "api", timeout: 1.5, retries: 3}
                defaults:
                  timeout: 1.5
                  retries: 3
                """;
        assertTrue(yaml(left, right, Options.DEFAULTS).equivalent(), () -> yaml(left, right, Options.DEFAULTS).toString());
        // JSON against YAML: the same data
        assertTrue(DataStructuralDiff.compare("{\"a\": [1, {\"b\": true}]}", Format.JSON,
                "a:\n  - 1\n  - b: true\n", Format.YAML, Options.DEFAULTS).equivalent());
    }

    @Test
    void changesAdditionsAndRemovalsWithPathsAndLines() {
        String left = "name: api\nport: 80\nold: x\n";
        String right = "name: api\nport: 8080\nnew: y\n";
        List<Difference> d = yaml(left, right, Options.DEFAULTS).differences();
        assertEquals(3, d.size(), d::toString);
        assertEquals(Change.CHANGED, d.get(0).change());
        assertEquals(NodeKind.MEMBER, d.get(0).kind());
        assertEquals("$.port", d.get(0).leftXPath());
        assertEquals("80", d.get(0).leftValue());
        assertEquals("8080", d.get(0).rightValue());
        assertEquals(2, d.get(0).leftLine());
        assertEquals(Change.REMOVED, d.get(1).change());
        assertEquals("$.old", d.get(1).leftXPath());
        assertEquals(3, d.get(1).leftLine());
        assertEquals(Change.ADDED, d.get(2).change());
        assertEquals("$.new", d.get(2).rightXPath());
        assertEquals(3, d.get(2).rightLine());
        assertEquals("Member 'port' changed: \"80\" → \"8080\"", d.get(0).describe());
    }

    @Test
    void anInsertedItemIsOneDifference() {
        String left = "steps:\n  - checkout\n  - build\n  - test\n";
        String right = "steps:\n  - checkout\n  - lint\n  - build\n  - test\n";
        List<Difference> d = yaml(left, right, Options.DEFAULTS).differences();
        assertEquals(1, d.size(), d::toString);
        assertEquals(Change.ADDED, d.get(0).change());
        assertEquals(NodeKind.ITEM, d.get(0).kind());
        assertEquals("$.steps[1]", d.get(0).rightXPath());
        assertEquals("lint", d.get(0).rightValue());
        assertEquals(3, d.get(0).rightLine());
    }

    @Test
    void aChangedObjectInAnArrayIsComparedMemberByMember() {
        String left = "[{\"id\": 1, \"v\": \"a\"}, {\"id\": 2, \"v\": \"b\"}]";
        String right = "[{\"id\": 1, \"v\": \"a\"}, {\"id\": 2, \"v\": \"c\"}]";
        List<Difference> d = DataStructuralDiff.compare(left, Format.JSON, right, Format.JSON, Options.DEFAULTS).differences();
        assertEquals(1, d.size(), d::toString);
        assertEquals("$[1].v", d.get(0).leftXPath());
    }

    @Test
    void keysAndIgnoredOrderMatchItems() {
        String left = "- {id: a, v: 1}\n- {id: b, v: 2}\n";
        String right = "- {id: b, v: 3}\n- {id: a, v: 1}\n";
        List<Difference> keyed = yaml(left, right, new Options(false, List.of("id"), 0)).differences();
        assertEquals(1, keyed.size(), keyed::toString);
        assertEquals("$[1].v", keyed.get(0).leftXPath());
        assertEquals("$[0].v", keyed.get(0).rightXPath());

        Result unordered = yaml("[1, 2, 3]", "[3, 1, 2]", new Options(true, List.of(), 0));
        assertTrue(unordered.equivalent(), unordered::toString);
        Result ordered = yaml("[1, 2, 3]", "[3, 1, 2]", Options.DEFAULTS);
        assertFalse(ordered.equivalent());
    }

    @Test
    void numbersCompareByValueAndTolerance() {
        assertTrue(yaml("a: 1.0", "a: 1", Options.DEFAULTS).equivalent());
        assertFalse(yaml("a: 1.0", "a: 1.004", Options.DEFAULTS).equivalent());
        assertTrue(yaml("a: 1.0", "a: 1.004", new Options(false, List.of(), 0.005)).equivalent());
        assertFalse(yaml("a: '1'", "a: 1", Options.DEFAULTS).equivalent(), "a string is no number");
    }

    @Test
    void parseErrorsAreReportedPerSide() {
        Result result = DataStructuralDiff.compare("a: [1", Format.YAML, "{", Format.JSON, Options.DEFAULTS);
        assertFalse(result.compared());
        assertNotNull(result.leftError());
        assertNotNull(result.rightError());
    }

    @Test
    void formatByNameOrContent() {
        assertEquals(Format.YAML, DataStructuralDiff.formatOf("ci.yml", ""));
        assertEquals(Format.JSON, DataStructuralDiff.formatOf("a.jsonc", ""));
        assertEquals(Format.JSON, DataStructuralDiff.formatOf("Untitled", " {\"a\":1}"));
        assertNull(DataStructuralDiff.formatOf("a.xml", "{"));
        assertNull(DataStructuralDiff.formatOf(null, "<a/>"));
    }
}
