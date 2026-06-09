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
}
