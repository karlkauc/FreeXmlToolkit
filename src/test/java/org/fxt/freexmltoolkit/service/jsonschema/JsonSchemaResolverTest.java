package org.fxt.freexmltoolkit.service.jsonschema;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Set;

import org.fxt.freexmltoolkit.service.JsonService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonPrimitive;

/** Tests {@link JsonSchemaDocument} and {@link JsonSchemaResolver}. */
class JsonSchemaResolverTest {

    private static JsonSchemaResolver resolver;

    @BeforeAll
    static void load() throws Exception {
        File file = new File(JsonSchemaResolverTest.class.getResource("/jsonschema/order.schema.json").toURI());
        resolver = new JsonSchemaResolver(JsonSchemaDocument.load(file));
    }

    @Test
    void rootLists2020PropertiesAndRequired() {
        SchemaView root = resolver.at(List.of(), null);
        assertEquals(JsonSchemaDocument.Draft.DRAFT_2020_12, resolver.document().draft());
        assertEquals("Order", root.title());
        assertEquals(Set.of("id", "status"), root.required());
        assertTrue(root.closed());
        assertTrue(root.properties().get("id").required());
        assertFalse(root.properties().get("customer").required());
        assertEquals("Order number", root.properties().get("id").schema().description());
        assertTrue(root.properties().get("legacy").schema().deprecated());
        assertTrue(root.properties().containsKey("trackingNo"), "if/then without an instance offers the then branch");
        assertFalse(root.required().contains("trackingNo"), "an undecided branch adds no required members");
    }

    @Test
    void enumDefaultAndConstraints() {
        SchemaView status = resolver.at(List.of("status"), null);
        assertEquals(List.of(new JsonPrimitive("open"), new JsonPrimitive("shipped"), new JsonPrimitive("closed")),
                status.enumValues());
        assertEquals(new JsonPrimitive("open"), status.defaultValue());
        assertEquals("^[A-Z]{2}-\\d+$", resolver.at(List.of("id"), null).constraints().get("pattern"));
    }

    @Test
    void refsIntoOtherFilesAndDraft7Definitions() {
        SchemaView customer = resolver.at(List.of("customer"), null);
        assertEquals("A customer", customer.description());
        assertEquals(Set.of("name"), customer.required());
        assertEquals("email", resolver.at(List.of("customer", "email"), null).format());
        SchemaView address = resolver.at(List.of("customer", "address"), null);
        assertEquals(Set.of("city", "zip"), address.properties().keySet(), "allOf members are merged");
        assertEquals(Set.of("string", "null"), resolver.at(List.of("customer", "address", "zip"), null).types());
    }

    @Test
    void arrayItemsAndNestedDefaults() {
        SchemaView item = resolver.at(List.of("items", 3), null);
        assertEquals(Set.of("sku"), item.required());
        assertEquals(new JsonPrimitive(1), resolver.at(List.of("items", 0, "qty"), null).defaultValue());
        assertEquals("1", resolver.at(List.of("items", 0, "qty"), null).constraints().get("minimum"));
        assertEquals(2, resolver.at(List.of("items", 0, "tags", 0), null).examples().size());
        assertEquals("1", resolver.at(List.of("items"), null).constraints().get("minItems"));
    }

    @Test
    void recursiveAnchorsTerminate() {
        SchemaView deep = resolver.at(List.of("tree", "children", 0, "children", 5, "children", 1), null);
        assertTrue(deep.properties().containsKey("name"));
        assertTrue(deep.properties().containsKey("children"));
    }

    @Test
    void oneOfBranchesArePickedByTheInstance() {
        SchemaView open = resolver.at(List.of("payment"), null);
        assertTrue(open.properties().keySet().containsAll(Set.of("kind", "cardNo", "dueDays")));
        assertTrue(open.required().isEmpty(), "two open branches: nothing is surely required");

        var instance = new JsonService().parseAuto("{\"payment\": {\"kind\": \"invoice\"}}");
        SchemaView invoice = resolver.at(List.of("payment"), instance);
        assertEquals(Set.of("kind", "dueDays"), invoice.properties().keySet());
        assertEquals(Set.of("kind"), invoice.required(), "the only fitting branch counts as required");
    }

    @Test
    void ifThenIsDecidedByTheInstance() {
        var shipped = new JsonService().parseAuto("{\"status\": \"shipped\"}");
        SchemaView root = resolver.at(List.of(), shipped);
        assertTrue(root.required().contains("trackingNo"));
        var open = new JsonService().parseAuto("{\"status\": \"open\"}");
        assertFalse(resolver.at(List.of(), open).properties().containsKey("trackingNo"));
    }

    @Test
    void unknownPlacesAndCycles() {
        assertFalse(resolver.at(List.of("nope"), null).known(), "additionalProperties false describes nothing more");
        JsonSchemaDocument loop = JsonSchemaDocument.of(
                "{\"$ref\": \"#/$defs/a\", \"$defs\": {\"a\": {\"$ref\": \"#/$defs/b\"}, \"b\": {\"$ref\": \"#/$defs/a\", \"description\": \"b\"}}}",
                URI.create("mem:/loop.json"));
        assertEquals("b", new JsonSchemaResolver(loop).at(List.of(), null).description());
    }
}
