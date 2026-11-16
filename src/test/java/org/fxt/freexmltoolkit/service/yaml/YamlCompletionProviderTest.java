package org.fxt.freexmltoolkit.service.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.Set;

import org.fxt.freexmltoolkit.service.jsonschema.JsonCompletionProvider.Proposal;
import org.fxt.freexmltoolkit.service.jsonschema.JsonSchemaDocument;
import org.fxt.freexmltoolkit.service.jsonschema.JsonSchemaResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests {@link YamlCaretContext} and {@link YamlCompletionProvider}. */
@DisplayName("YAML completion")
class YamlCompletionProviderTest {

    private static final String SCHEMA = """
            {"type": "object", "required": ["name"],
             "properties": {
               "name": {"type": "string"},
               "kind": {"enum": ["Deployment", "Service", "007"]},
               "enabled": {"type": "boolean"},
               "spec": {"type": "object", "properties": {
                  "replicas": {"type": "integer", "default": 1},
                  "ports": {"type": "array", "items": {"type": "object",
                     "properties": {"port": {"type": "integer"}, "protocol": {"enum": ["TCP", "UDP"]}}}}}}}}
            """;

    private static JsonSchemaResolver resolver() throws Exception {
        return new JsonSchemaResolver(JsonSchemaDocument.of(SCHEMA, URI.create("mem:/s.json")));
    }

    /** Text with "|" as the caret. */
    private static List<Proposal> complete(String textWithCaret) throws Exception {
        int caret = textWithCaret.lastIndexOf('|');
        String text = textWithCaret.substring(0, caret) + textWithCaret.substring(caret + 1);
        return YamlCompletionProvider.complete(text, caret, resolver());
    }

    private static List<String> labels(List<Proposal> proposals) {
        return proposals.stream().map(Proposal::label).toList();
    }

    private static YamlCaretContext.Result context(String textWithCaret) {
        int caret = textWithCaret.lastIndexOf('|');
        return YamlCaretContext.analyze(textWithCaret.substring(0, caret) + textWithCaret.substring(caret + 1), caret);
    }

    @Test
    @DisplayName("caret context: keys, values and items with their paths")
    void caretContext() {
        var key = context("name: x\nspec:\n  rep|\n");
        assertEquals(YamlCaretContext.Kind.KEY, key.kind());
        assertEquals(List.of("spec"), key.path());
        assertEquals("rep", key.partial());

        var value = context("spec:\n  ports:\n  - port: 80\n    protocol: T|\n");
        assertEquals(YamlCaretContext.Kind.VALUE, value.kind());
        assertEquals(List.of("spec", "ports", 0, "protocol"), value.path());
        assertEquals("T", value.partial());

        var second = context("spec:\n  ports:\n    - port: 80\n    - port: 81\n      pro|\n");
        assertEquals(YamlCaretContext.Kind.KEY, second.kind());
        assertEquals(List.of("spec", "ports", 1), second.path());
        assertEquals(Set.of("port"), second.existingKeys());

        var item = context("spec:\n  ports:\n  - |\n");
        assertEquals(YamlCaretContext.Kind.ITEM, item.kind());
        assertEquals(List.of("spec", "ports", 0), item.path());

        assertEquals(YamlCaretContext.Kind.NONE, context("a: 1 # com|\n").kind());
        assertEquals(YamlCaretContext.Kind.NONE, context("text: |\n  some te|\n").kind());
    }

    @Test
    @DisplayName("keys: required first, existing ones left out, values written as YAML")
    void keys() throws Exception {
        List<Proposal> proposals = complete("kind: Service\n|\n");
        assertEquals("name", proposals.get(0).label());
        assertTrue(!labels(proposals).contains("kind"));
        Proposal spec = proposals.stream().filter(p -> p.label().equals("spec")).findFirst().orElseThrow();
        assertEquals("spec:\n  ", spec.insertText());
        Proposal kind = complete("|\n").stream().filter(p -> p.label().equals("name")).findFirst().orElseThrow();
        assertEquals("name: ", kind.insertText());
    }

    @Test
    @DisplayName("values: enum, booleans, default; quoted where a plain scalar would change type")
    void values() throws Exception {
        assertEquals(List.of("Deployment", "Service", "007"), labels(complete("kind: |\n")));
        assertEquals("'007'".replace('\'', '"'), complete("kind: |\n").get(2).insertText());
        assertEquals(List.of("true", "false"), labels(complete("enabled: |\n")));
        assertEquals(List.of("1"), labels(complete("spec:\n  replicas: |\n")));
        // opened by typing the colon: the value gets its space
        assertEquals(" Deployment", complete("kind:|\n").get(0).insertText());
    }

    @Test
    @DisplayName("an item of an array of objects starts with its keys")
    void itemKeys() throws Exception {
        assertEquals(List.of("port", "protocol"), labels(complete("spec:\n  ports:\n  - |\n")));
    }
}
