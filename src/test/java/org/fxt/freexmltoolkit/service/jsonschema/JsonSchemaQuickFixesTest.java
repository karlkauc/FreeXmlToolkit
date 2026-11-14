package org.fxt.freexmltoolkit.service.jsonschema;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.fxt.freexmltoolkit.controls.shell.editor.ValidationProblem;
import org.fxt.freexmltoolkit.controls.shell.editor.ValidationRunner;
import org.fxt.freexmltoolkit.service.sqf.SqfExecutionEngine.SqfEditPlan;
import org.fxt.freexmltoolkit.service.sqf.SqfExecutionEngine.SqfTextEdit;
import org.fxt.freexmltoolkit.service.sqf.SqfFixSuggestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests {@link JsonSchemaQuickFixes}: fixes offered by validation and the text they produce. */
class JsonSchemaQuickFixesTest {

    private Path schema;

    @BeforeEach
    void schema(@TempDir Path tmp) throws Exception {
        schema = tmp.resolve("s.json");
        Files.writeString(schema, """
                {"type": "object", "required": ["name", "size"], "additionalProperties": false,
                 "properties": {
                   "name": {"type": "string"},
                   "size": {"enum": ["S", "M", "L"]},
                   "kind": {"const": "shirt"},
                   "address": {"type": "object", "required": ["city"], "properties": {"city": {"type": "string", "default": "Graz"}}}
                 }}
                """);
    }

    private List<ValidationProblem> validate(String json) {
        return ValidationRunner.validateJson(json, schema.toFile());
    }

    private static String apply(String text, SqfEditPlan plan) {
        StringBuilder out = new StringBuilder(text);
        for (SqfTextEdit edit : plan.edits()) { // descending
            out.replace(edit.start(), edit.end(), edit.replacement());
        }
        return out.toString();
    }

    private String fixWith(String json, String keyPrefix) {
        for (ValidationProblem problem : validate(json)) {
            for (SqfFixSuggestion fix : problem.fixes()) {
                if (fix.fixKey().startsWith(keyPrefix)) {
                    return apply(json, JsonSchemaQuickFixes.computeEdit(json, fix));
                }
            }
        }
        fail("no fix " + keyPrefix + " offered for " + validate(json));
        return null;
    }

    @Test
    void addsTheMissingRequiredPropertyInTheObjectsLayout() {
        String json = "{\n  \"name\": \"x\"\n}\n";
        String fixed = fixWith(json, JsonSchemaQuickFixes.ADD_REQUIRED);
        assertEquals("{\n  \"name\": \"x\",\n  \"size\": \"S\"\n}\n", fixed);
        assertTrue(validate(fixed).isEmpty(), validate(fixed).toString());
    }

    @Test
    void addsIntoNestedEmptyOneLineAndCommentedObjects() {
        assertEquals("{\"name\": \"x\", \"size\": \"S\", \"address\": {\"city\": \"Graz\"}}",
                fixWith("{\"name\": \"x\", \"size\": \"S\", \"address\": {}}", JsonSchemaQuickFixes.ADD_REQUIRED));
        String commented = "{\n  \"name\": \"x\" // the name\n}";
        assertEquals("{\n  \"name\": \"x\", // the name\n  \"size\": \"S\"\n}",
                fixWith(commented, JsonSchemaQuickFixes.ADD_REQUIRED));
        String trailing = "{\n  \"name\": \"x\",\n}";
        assertEquals("{\n  \"name\": \"x\",\n  \"size\": \"S\",\n}", fixWith(trailing, JsonSchemaQuickFixes.ADD_REQUIRED));
    }

    @Test
    void replacesAValueWithAnAllowedOne() {
        String json = "{\"name\": \"x\", \"size\": \"XL\"}";
        List<SqfFixSuggestion> fixes = validate(json).stream().flatMap(p -> p.fixes().stream()).toList();
        assertEquals(List.of("Replace with \"S\"", "Replace with \"M\"", "Replace with \"L\""),
                fixes.stream().map(SqfFixSuggestion::title).toList());
        assertEquals("{\"name\": \"x\", \"size\": \"M\"}", apply(json, JsonSchemaQuickFixes.computeEdit(json, fixes.get(1))));
        assertEquals("{\"name\": \"x\", \"size\": \"S\", \"kind\": \"shirt\"}",
                fixWith("{\"name\": \"x\", \"size\": \"S\", \"kind\": \"pants\"}", JsonSchemaQuickFixes.REPLACE_VALUE));
    }

    @Test
    void removesAPropertyTheSchemaDoesNotAllow() {
        String json = "{\n  \"name\": \"x\",\n  \"extra\": [1, 2],\n  \"size\": \"S\"\n}";
        assertEquals("{\n  \"name\": \"x\",\n  \"size\": \"S\"\n}", fixWith(json, JsonSchemaQuickFixes.REMOVE_PROPERTY));
        String last = "{\"name\": \"x\", \"size\": \"S\", \"extra\": 1}";
        assertEquals("{\"name\": \"x\", \"size\": \"S\"}", fixWith(last, JsonSchemaQuickFixes.REMOVE_PROPERTY));
    }

    @Test
    void aFixForAChangedDocumentIsRefused() {
        String json = "{\"name\": \"x\", \"size\": \"S\", \"address\": {}}";
        SqfFixSuggestion fix = validate(json).get(0).fixes().get(0);
        assertThrows(IllegalStateException.class, () -> JsonSchemaQuickFixes.computeEdit("{\"name\": 1}", fix));
        assertTrue(JsonSchemaQuickFixes.isJsonFix(fix));
    }
}
