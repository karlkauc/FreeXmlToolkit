package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests {@link ValidationRunner#validateJson} (no UI): well-formedness + JSON
 * Schema validation, reusing JsonService.
 */
class ValidationRunnerJsonTest {

    private static final String SCHEMA =
            "{\"type\":\"object\",\"required\":[\"name\"],\"properties\":{\"name\":{\"type\":\"string\"}}}";

    private File schema(Path tmp) throws Exception {
        File f = tmp.resolve("schema.json").toFile();
        Files.writeString(f.toPath(), SCHEMA);
        return f;
    }

    @Test
    void validJsonAgainstSchemaHasNoProblems(@TempDir Path tmp) throws Exception {
        List<ValidationProblem> p = ValidationRunner.validateJson("{\"name\":\"x\"}", schema(tmp));
        assertTrue(p.isEmpty(), p.toString());
    }

    @Test
    void invalidJsonAgainstSchemaReportsProblems(@TempDir Path tmp) throws Exception {
        List<ValidationProblem> p = ValidationRunner.validateJson("{}", schema(tmp));
        assertFalse(p.isEmpty(), "missing required property must be reported");
    }

    @Test
    void malformedJsonReportsProblem() {
        List<ValidationProblem> p = ValidationRunner.validateJson("{not json", null);
        assertFalse(p.isEmpty(), "malformed JSON must be reported");
    }

    @Test
    void wellFormedJsonWithoutSchemaHasNoProblems() {
        assertTrue(ValidationRunner.validateJson("{\"a\":1}", null).isEmpty());
    }

    @Test
    void schemaProblemsCarryLinePointerAndKeyword(@TempDir Path tmp) throws Exception {
        String json = """
                {
                  "name": 42
                }
                """;
        List<ValidationProblem> p = ValidationRunner.validateJson(json, schema(tmp));
        assertEquals(1, p.size(), p.toString());
        ValidationProblem problem = p.get(0);
        assertEquals("JSON Schema", problem.source());
        assertEquals(2, problem.line(), "problem must point at the offending property's line");
        assertEquals("/name", problem.context(), "context must carry the instance pointer");
        assertEquals("type", problem.ruleId(), "ruleId must carry the failing keyword");
    }

    @Test
    void syntaxErrorCarriesParsedLine() {
        String json = """
                {
                  "a": 1,
                  "b":
                }
                """;
        List<ValidationProblem> p = ValidationRunner.validateJson(json, null);
        assertEquals(1, p.size());
        assertEquals("JSON", p.get(0).source());
        assertTrue(p.get(0).line() > 1, "syntax error line must be parsed from the Gson message");
    }
}
