package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Guards the shipped JSON Schema examples ({@code release/examples/json/}): every
 * instance document must keep behaving exactly as its README documents — the
 * {@code $schema} declaration resolves to the sibling schema file, the *-valid
 * files validate clean, and the *-invalid files produce violations. Runs the real
 * sniff → resolve → validate pipeline, so a change to either the examples or the
 * pipeline that breaks the demo fails the build.
 */
@DisplayName("release/examples/json")
class JsonSchemaExamplesTest {

    private static final Path EXAMPLES = Path.of("release/examples/json");

    private final JsonService jsonService = new JsonService();

    /** Sniffs the $schema declaration and resolves it exactly like EditorHost does. */
    private List<JsonService.SchemaError> validate(String instanceFile) throws Exception {
        Path instance = EXAMPLES.resolve(instanceFile);
        assertTrue(Files.exists(instance), "missing example: " + instance.toAbsolutePath());
        String json = Files.readString(instance);
        String declared = jsonService.getSchemaLocationFromJsonContent(json)
                .orElseThrow(() -> new AssertionError(instanceFile + " must declare a $schema"));
        File schema = jsonService.resolveJsonSchemaLocation(declared, EXAMPLES.toFile());
        assertNotNull(schema, instanceFile + ": declared schema '" + declared + "' must resolve");
        return jsonService.validateAgainstSchemaDetailed(json, schema);
    }

    @Test
    @DisplayName("products-valid.json validates clean (incl. relative $ref to manufacturer-schema.json)")
    void validCatalogHasNoErrors() throws Exception {
        List<JsonService.SchemaError> errors = validate("products-valid.json");
        assertTrue(errors.isEmpty(), "expected no violations, got: " + errors);
    }

    @Test
    @DisplayName("products-invalid.json shows the six documented violations")
    void invalidCatalogShowsDocumentedViolations() throws Exception {
        List<JsonService.SchemaError> errors = validate("products-invalid.json");
        // README documents six deliberate violations: version pattern, price type,
        // currency enum, id pattern, missing required name / negative price, country pattern.
        assertTrue(errors.size() >= 6, "expected at least 6 violations, got: " + errors);
        assertTrue(errors.stream().anyMatch(e -> "/catalog/version".equals(e.instancePointer())),
                "version pattern violation missing: " + errors);
        assertTrue(errors.stream().anyMatch(e -> "/products/0/price".equals(e.instancePointer())),
                "price type violation missing: " + errors);
        assertTrue(errors.stream().anyMatch(e -> "/products/1/manufacturer/country".equals(e.instancePointer())),
                "country pattern violation (via relative $ref) missing: " + errors);
    }

    @ParameterizedTest
    @CsvSource({
            "person-invalid-draft07.json, dependencies",
            "order-invalid-2019-09.json, dependentRequired"
    })
    @DisplayName("Dialect demos fail on their dialect-specific keyword")
    void dialectDemosViolateTheirKeyword(String instanceFile, String keyword) throws Exception {
        List<JsonService.SchemaError> errors = validate(instanceFile);
        assertFalse(errors.isEmpty(), instanceFile + " must produce violations");
        assertTrue(errors.stream().anyMatch(e -> keyword.equals(e.keyword())),
                instanceFile + " must fail on '" + keyword + "', got: " + errors);
    }

    @Test
    @DisplayName("All shipped schemas are themselves valid JSON")
    void schemasAreValidJson() throws Exception {
        for (String schema : List.of("product-schema.json", "manufacturer-schema.json",
                "person-schema-draft07.json", "order-schema-2019-09.json")) {
            String text = Files.readString(EXAMPLES.resolve(schema));
            assertNull(jsonService.validateJson(text), schema + " must be valid JSON");
        }
    }
}
