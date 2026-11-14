package org.fxt.freexmltoolkit.service.jsonschema;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.List;

import org.fxt.freexmltoolkit.service.jsonschema.JsonCompletionProvider.Proposal;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Tests {@link JsonCompletionProvider}. */
class JsonCompletionProviderTest {

    private static JsonSchemaResolver resolver;

    @BeforeAll
    static void load() throws Exception {
        File file = new File(JsonCompletionProviderTest.class.getResource("/jsonschema/order.schema.json").toURI());
        resolver = new JsonSchemaResolver(JsonSchemaDocument.load(file));
    }

    private static List<Proposal> complete(String marked) {
        int offset = marked.indexOf('|');
        return JsonCompletionProvider.complete(marked.replace("|", ""), offset, resolver);
    }

    private static String apply(String marked, Proposal p) {
        String text = marked.replace("|", "");
        return text.substring(0, p.replaceStart()) + p.insertText() + text.substring(p.replaceEnd());
    }

    @Test
    void missingPropertiesRequiredFirst() {
        List<Proposal> proposals = complete("{\n  \"id\": \"AB-1\",\n  |\n}");
        List<String> labels = proposals.stream().map(Proposal::label).toList();
        assertEquals("status", labels.get(0), "the missing required property comes first");
        assertFalse(labels.contains("id"), "existing keys are not offered");
        assertTrue(labels.containsAll(List.of("customer", "items", "payment")));
        Proposal items = proposals.stream().filter(p -> p.label().equals("items")).findFirst().orElseThrow();
        assertEquals("\"items\": []", items.insertText());
        assertEquals("\"items\": [".length(), items.caretOffset(), "the caret goes between the brackets");
    }

    @Test
    void typedPrefixFiltersAndReplacesTheToken() {
        String marked = "{\"cu|\": 1}";
        List<Proposal> proposals = complete(marked);
        assertEquals(List.of("customer"), proposals.stream().map(Proposal::label).toList());
        assertEquals("{\"customer\": 1}", apply(marked, proposals.get(0)), "an existing colon is kept");
    }

    @Test
    void enumAndDefaultValues() {
        String marked = "{\"status\": |}";
        List<Proposal> proposals = complete(marked);
        assertEquals(List.of("open", "shipped", "closed"), proposals.stream().map(Proposal::label).toList());
        assertEquals("{\"status\": \"shipped\"}", apply(marked, proposals.get(1)));
        assertEquals("default", proposals.get(0).description());
    }

    @Test
    void valuesInsideAStringAndBooleans() {
        String marked = "{\"status\": \"sh|\"}";
        List<Proposal> proposals = complete(marked);
        assertEquals(List.of("shipped"), proposals.stream().map(Proposal::label).toList());
        assertEquals("{\"status\": \"shipped\"}", apply(marked, proposals.get(0)));
        assertEquals(List.of("true", "false"),
                complete("{\"legacy\": |}").stream().map(Proposal::label).toList());
    }

    @Test
    void nestedObjectsInArraysAndOtherFiles() {
        List<String> item = complete("{\"items\": [{\"sku\": \"x\", |}]}").stream().map(Proposal::label).toList();
        assertEquals(List.of("qty", "tags"), item);
        List<String> customer = complete("{\"customer\": {|}}").stream().map(Proposal::label).toList();
        assertEquals(List.of("name", "email", "address"), customer);
    }

    @Test
    void noProposalsWithoutSchemaOrPlace() {
        assertTrue(JsonCompletionProvider.complete("{|}", 1, null).isEmpty());
        assertTrue(complete("{\"id\": \"x\" |}").isEmpty());
    }
}
