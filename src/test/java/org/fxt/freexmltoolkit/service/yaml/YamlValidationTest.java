package org.fxt.freexmltoolkit.service.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.fxt.freexmltoolkit.controls.shell.editor.JsonProblemMarkers;
import org.fxt.freexmltoolkit.controls.shell.editor.ValidationProblem;
import org.fxt.freexmltoolkit.controls.shell.editor.ValidationRunner;
import org.fxt.freexmltoolkit.controls.shell.editor.YamlProblemMarkers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests {@link ValidationRunner#validateYaml} and {@link YamlProblemMarkers}. */
@DisplayName("YAML validation")
class YamlValidationTest {

    @TempDir
    Path dir;

    private Path schemaFile() throws Exception {
        Path schema = dir.resolve("s.json");
        Files.writeString(schema, """
                {"type": "object", "required": ["name"], "additionalProperties": false,
                 "properties": {"name": {"type": "string"},
                   "items": {"type": "array", "items": {"type": "object",
                     "properties": {"count": {"type": "integer"}}}}}}
                """);
        return schema;
    }

    @Test
    @DisplayName("a syntax error carries line and column")
    void syntax() {
        List<ValidationProblem> problems = ValidationRunner.validateYaml("a: 1\n  b: 2\n", null);
        assertEquals(1, problems.size());
        assertEquals("YAML", problems.get(0).source());
        assertEquals(2, problems.get(0).line());
        assertTrue(problems.get(0).column() > 0);
    }

    @Test
    @DisplayName("schema violations are placed on the line of their key")
    void schemaViolations() throws Exception {
        String yaml = "# config\nname: x\nitems:\n  - count: 1\n  - count: many\nextra: 1\n";
        List<ValidationProblem> problems = ValidationRunner.validateYaml(yaml, schemaFile().toFile());
        assertEquals(2, problems.size(), problems.toString());
        ValidationProblem type = problems.stream().filter(p -> "type".equals(p.ruleId())).findFirst().orElseThrow();
        assertEquals(5, type.line());
        ValidationProblem extra = problems.stream().filter(p -> !"type".equals(p.ruleId())).findFirst().orElseThrow();
        assertEquals(2, extra.line(), "additionalProperties is reported at the root mapping (after the comment)");
        assertTrue(ValidationRunner.validateYaml("name: ok\n", schemaFile().toFile()).isEmpty());

        List<JsonProblemMarkers.Marker> markers = YamlProblemMarkers.compute(yaml, problems);
        JsonProblemMarkers.Marker marker = markers.stream().filter(m -> m.line() == 4).findFirst().orElseThrow();
        assertEquals("count", yaml.substring(marker.start(), marker.end()));
    }

    @Test
    @DisplayName("a syntax error marks the token at its position")
    void syntaxMarker() {
        String yaml = "a: 1\nb: [1, 2\n";
        List<JsonProblemMarkers.Marker> markers = YamlProblemMarkers.compute(yaml,
                ValidationRunner.validateYaml(yaml, null));
        assertEquals(1, markers.size());
        assertTrue(markers.get(0).end() > markers.get(0).start());
    }
}
