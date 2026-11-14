package org.fxt.freexmltoolkit.service.jsonschema;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.fxt.freexmltoolkit.service.JsonService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Tests {@link JsonSchemaSampleGenerator}: samples validate against their own schema. */
class JsonSchemaSampleGeneratorTest {

    private static final Gson GSON = new Gson();

    private static List<JsonService.SchemaError> validate(JsonElement sample, File schema) {
        return new JsonService().validateAgainstSchemaDetailed(GSON.toJson(sample), schema);
    }

    @Test
    void orderSampleIsValid() throws Exception {
        File schema = new File(getClass().getResource("/jsonschema/order.schema.json").toURI());
        var resolver = new JsonSchemaResolver(JsonSchemaDocument.load(schema));
        JsonElement sample = JsonSchemaSampleGenerator.generate(resolver, JsonSchemaSampleGenerator.Options.defaults());
        JsonObject object = sample.getAsJsonObject();
        assertEquals("open", object.get("status").getAsString(), "the default is used");
        assertTrue(object.has("id"));
        assertFalse(object.has("customer"), "optional properties are left out");
        assertEquals(List.of(), validate(sample, schema));
    }

    @Test
    void allPropertiesFormatsAndLimits(@TempDir Path tmp) throws Exception {
        Path schema = tmp.resolve("s.json");
        Files.writeString(schema, """
                {"$schema": "https://json-schema.org/draft/2020-12/schema", "type": "object",
                 "required": ["when", "mail", "count"],
                 "properties": {
                   "when": {"type": "string", "format": "date-time"},
                   "mail": {"type": "string", "format": "email"},
                   "count": {"type": "integer", "minimum": 3, "multipleOf": 2},
                   "ratio": {"type": "number", "exclusiveMinimum": 0},
                   "code": {"type": "string", "minLength": 8, "maxLength": 10},
                   "tags": {"type": "array", "minItems": 2, "uniqueItems": true, "items": {"type": "string"}},
                   "kind": {"const": "fixed"},
                   "flag": {"type": "boolean"},
                   "nested": {"$ref": "#/$defs/node"}
                 },
                 "$defs": {"node": {"type": "object", "properties": {"child": {"$ref": "#/$defs/node"}}}}}
                """);
        var resolver = new JsonSchemaResolver(JsonSchemaDocument.load(schema.toFile()));
        JsonObject sample = JsonSchemaSampleGenerator.generate(resolver,
                new JsonSchemaSampleGenerator.Options(false, 4, 1)).getAsJsonObject();
        assertEquals(4, sample.get("count").getAsInt(), "minimum 3, multiple of 2");
        assertEquals("user@example.com", sample.get("mail").getAsString());
        assertEquals("fixed", sample.get("kind").getAsString());
        assertEquals(8, sample.get("code").getAsString().length());
        assertEquals(2, sample.getAsJsonArray("tags").size());
        assertTrue(sample.getAsJsonObject("nested").has("child"), "recursion goes on to the depth limit");
        assertEquals(List.of(), validate(sample, schema.toFile()));
    }

    @Test
    void sampleAtAPath() throws Exception {
        File schema = new File(getClass().getResource("/jsonschema/order.schema.json").toURI());
        var resolver = new JsonSchemaResolver(JsonSchemaDocument.load(schema));
        JsonElement item = JsonSchemaSampleGenerator.generateAt(resolver, List.of("items", 0),
                JsonSchemaSampleGenerator.Options.defaults());
        assertEquals("string", item.getAsJsonObject().get("sku").getAsString());
    }
}
