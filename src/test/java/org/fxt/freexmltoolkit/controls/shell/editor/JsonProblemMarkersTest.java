package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests {@link JsonProblemMarkers}: where validation problems are underlined. */
class JsonProblemMarkersTest {

    private static String marked(String text, JsonProblemMarkers.Marker marker) {
        return text.substring(marker.start(), marker.end());
    }

    @Test
    void schemaViolationMarksTheMemberKey(@TempDir Path tmp) throws Exception {
        File schema = tmp.resolve("s.json").toFile();
        Files.writeString(schema.toPath(),
                "{\"type\":\"object\",\"properties\":{\"age\":{\"type\":\"integer\"},\"tags\":{\"type\":\"array\","
                        + "\"items\":{\"type\":\"string\"}}}}");
        String json = """
                {
                  // JSONC comment
                  "age": "old",
                  "tags": ["a", 2]
                }
                """;
        List<ValidationProblem> problems = ValidationRunner.validateJson(json, schema);
        List<JsonProblemMarkers.Marker> markers = JsonProblemMarkers.compute(json, problems);
        assertEquals(2, markers.size(), problems.toString());
        assertTrue(markers.stream().anyMatch(m -> marked(json, m).equals("\"age\"") && m.line() == 2), markers.toString());
        assertTrue(markers.stream().anyMatch(m -> marked(json, m).equals("2") && m.line() == 3), markers.toString());
    }

    @Test
    void missingRequiredMarksTheObjectsBracket(@TempDir Path tmp) throws Exception {
        File schema = tmp.resolve("s.json").toFile();
        Files.writeString(schema.toPath(), "{\"type\":\"object\",\"required\":[\"name\"]}");
        String json = "{\n  \"x\": 1\n}";
        var markers = JsonProblemMarkers.compute(json, ValidationRunner.validateJson(json, schema));
        assertEquals(1, markers.size());
        assertEquals("{", marked(json, markers.get(0)));
        assertEquals(0, markers.get(0).line());
    }

    @Test
    void parseErrorMarksTheTokenAtItsColumn() {
        String json = "{\n  \"a\": 1,\n  \"b\" 2\n}";
        List<ValidationProblem> problems = ValidationRunner.validateJson(json, null);
        assertEquals(1, problems.size());
        var markers = JsonProblemMarkers.compute(json, problems);
        assertEquals(1, markers.size(), problems.toString());
        assertEquals(2, markers.get(0).line(), problems + " → " + marked(json, markers.get(0)));
        assertFalse(marked(json, markers.get(0)).isBlank());
    }

    @Test
    void lineWithoutColumnMarksTheFirstToken() {
        String json = "{\n    \"a\": true\n}";
        var markers = JsonProblemMarkers.compute(json,
                List.of(new ValidationProblem("JSON", "error", 2, "boom")));
        assertEquals("\"a\"", marked(json, markers.get(0)));
        assertEquals("boom", markers.get(0).message());
    }

    @Test
    void unplaceableProblemsAreLeftOut() {
        assertTrue(JsonProblemMarkers.compute("{}", List.of(new ValidationProblem("JSON", "error", 9, "x"))).isEmpty());
        assertTrue(JsonProblemMarkers.compute("", List.of(new ValidationProblem("JSON", "error", 1, "x"))).isEmpty());
    }
}
