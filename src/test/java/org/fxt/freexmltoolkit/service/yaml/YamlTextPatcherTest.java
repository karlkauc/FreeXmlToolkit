package org.fxt.freexmltoolkit.service.yaml;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

class YamlTextPatcherTest {

    private static String patched(String yaml, Consumer<JsonElement> change) {
        JsonElement data = YamlDocument.parse(yaml).json().deepCopy();
        change.accept(data);
        Optional<String> result = YamlTextPatcher.patch(yaml, data);
        assertTrue(result.isPresent(), "no patch for " + data);
        assertEquals(data, YamlDocument.parse(result.get()).json());
        return result.get();
    }

    private static String patchedTo(String yaml, String json) {
        Optional<String> result = YamlTextPatcher.patch(yaml, JsonParser.parseString(json));
        assertTrue(result.isPresent(), "no patch to " + json);
        return result.get();
    }

    @Test
    void unchangedDataGivesTheSameText() {
        String yaml = "# head\na: 1 # one\n";
        assertEquals(yaml, patchedTo(yaml, "{\"a\":1}"));
    }

    @Test
    void aChangedValueIsReplacedInPlace() {
        String yaml = """
                # service
                name: web   # the name
                spec:
                  replicas: 2
                  image: "nginx:1"
                """;
        String out = patched(yaml, d -> d.getAsJsonObject().getAsJsonObject("spec").addProperty("replicas", 5));
        assertEquals(yaml.replace("replicas: 2", "replicas: 5"), out);
        out = patched(yaml, d -> d.getAsJsonObject().addProperty("name", "api server"));
        assertEquals(yaml.replace("name: web ", "name: api server "), out);
        out = patched(yaml, d -> d.getAsJsonObject().addProperty("name", "true"));
        assertTrue(out.contains("name: \"true\"   # the name"), out);
    }

    @Test
    void aRenamedKeyKeepsItsLineAndComment() {
        String yaml = "a: 1 # first\nb: 2\n";
        assertEquals("x: 1 # first\nb: 2\n", patchedTo(yaml, "{\"x\":1,\"b\":2}"));
    }

    @Test
    void entriesAreAddedAndRemovedAsLines() {
        String yaml = """
                spec:
                    # how many
                    replicas: 2
                    image: nginx
                # tail
                other: x
                """;
        String added = patched(yaml, d -> d.getAsJsonObject().getAsJsonObject("spec").addProperty("port", 80));
        assertEquals(yaml.replace("image: nginx\n", "image: nginx\n    port: 80\n"), added);

        String nested = patched(yaml, d -> {
            var spec = d.getAsJsonObject().getAsJsonObject("spec");
            var labels = new com.google.gson.JsonObject();
            labels.addProperty("app", "web");
            spec.add("labels", labels);
        });
        assertTrue(nested.contains("    image: nginx\n    labels:\n        app: web\n# tail"), nested);

        String removed = patched(yaml, d -> d.getAsJsonObject().getAsJsonObject("spec").remove("image"));
        assertEquals(yaml.replace("    image: nginx\n", ""), removed);

        String first = patchedTo(yaml, "{\"top\":true,\"spec\":{\"replicas\":2,\"image\":\"nginx\"},\"other\":\"x\"}");
        assertTrue(first.startsWith("top: true\nspec:\n"), first);
    }

    @Test
    void sequenceItemsAreInsertedAndRemoved() {
        String yaml = """
                items:
                - a   # first
                - b
                - c
                """;
        assertEquals(yaml.replace("- c\n", "- c\n- d\n"),
                patched(yaml, d -> d.getAsJsonObject().getAsJsonArray("items").add("d")));
        assertEquals(yaml.replace("- b\n", "- x\n- b\n"),
                patchedTo(yaml, "{\"items\":[\"a\",\"x\",\"b\",\"c\"]}"));
        assertEquals(yaml.replace("- b\n", ""), patchedTo(yaml, "{\"items\":[\"a\",\"c\"]}"));
        String objects = patchedTo("list:\n  - name: a\n    v: 1\n", "{\"list\":[{\"name\":\"a\",\"v\":1},{\"name\":\"b\",\"v\":2}]}");
        assertEquals("list:\n  - name: a\n    v: 1\n  - name: b\n    v: 2\n", objects);
    }

    @Test
    void flowCollectionsStayFlow() {
        String yaml = "tags: [a, b]   # tags\nmeta: {x: 1}\n";
        assertEquals("tags: [a, z]   # tags\nmeta: {x: 1}\n", patchedTo(yaml, "{\"tags\":[\"a\",\"z\"],\"meta\":{\"x\":1}}"));
        assertEquals("tags: [a, b, c]   # tags\nmeta: {x: 1}\n",
                patchedTo(yaml, "{\"tags\":[\"a\",\"b\",\"c\"],\"meta\":{\"x\":1}}"));
        assertEquals("tags: [a, b]   # tags\nmeta: {x: 1, y: two words}\n",
                patchedTo(yaml, "{\"tags\":[\"a\",\"b\"],\"meta\":{\"x\":1,\"y\":\"two words\"}}"));
    }

    @Test
    void typeChangesAreWrittenInBlockStyle() {
        String yaml = "# c\na:\n  b: 1\n  c: 2\nd: 3\n";
        assertEquals("# c\na: 5\nd: 3\n", patchedTo(yaml, "{\"a\":5,\"d\":3}"));
        assertEquals("# c\na:\n  b: 1\n  c: 2\nd:\n  e: f\n", patchedTo(yaml, "{\"a\":{\"b\":1,\"c\":2},\"d\":{\"e\":\"f\"}}"));
        assertEquals("# c\na: {}\nd: 3\n", patchedTo(yaml, "{\"a\":{},\"d\":3}"));
    }

    @Test
    void tagsAndAnchorsOfAChangedScalarStay() {
        String yaml = "ref: !Ref Bucket\nname: &n web\n";
        assertEquals("ref: !Ref Table\nname: &n web\n", patchedTo(yaml, "{\"ref\":\"Table\",\"name\":\"web\"}"));
    }

    @Test
    void severalDocumentsArePatchedEachInPlace() {
        String yaml = "---\na: 1 # x\n---\nb: 2\n";
        assertEquals("---\na: 1 # x\n---\nb: 3\n", patchedTo(yaml, "[{\"a\":1},{\"b\":3}]"));
        assertTrue(YamlTextPatcher.patch(yaml, JsonParser.parseString("[{\"a\":1}]")).isEmpty(),
                "a removed document is not patched");
    }

    @Test
    void aChangeThroughAnAliasIsNotPatched() {
        String yaml = "base: &b {x: 1}\nuse: *b\n";
        assertTrue(YamlTextPatcher.patch(yaml, JsonParser.parseString("{\"base\":{\"x\":1},\"use\":{\"x\":2}}"))
                .isEmpty());
    }

    @Test
    void aMovedEntryIsRewrittenLocallyWhenNothingIsLost() {
        String yaml = "# keep\nm:\n  a: 1\n  b: 2\nz: 0\n";
        String out = patchedTo(yaml, "{\"m\":{\"b\":2,\"a\":1},\"z\":0}");
        assertEquals("# keep\nm:\n  b: 2\n  a: 1\nz: 0\n", out);
        // with a comment inside the moved block the text cannot be patched without losing it
        assertTrue(YamlTextPatcher.patch("m:\n  a: 1 # c\n  b: 2\n", JsonParser.parseString("{\"m\":{\"b\":2,\"a\":1}}"))
                .isEmpty());
    }

    @Test
    void theFirstKeyOfAnItemIsRemovedByRewritingTheItem() {
        String yaml = "# list\nl:\n- a: 1\n  b: 2\n";
        assertEquals("# list\nl:\n- b: 2\n", patchedTo(yaml, "{\"l\":[{\"b\":2}]}"));
    }

    @Test
    void windowsLineBreaksAreKept() {
        String yaml = "a: 1 # x\r\nb:\r\n  c: 2\r\n";
        assertEquals("a: 1 # x\r\nb:\r\n  c: 2\r\n  d: 3\r\n", patchedTo(yaml, "{\"a\":1,\"b\":{\"c\":2,\"d\":3}}"));
        assertEquals("b:\r\n  c: 2\r\n", patchedTo(yaml, "{\"b\":{\"c\":2}}"));
    }

    @Test
    void rewriteWritesBlockYamlAndKeepsDocumentMarkers() {
        assertEquals("a:\n  b: 1\n", YamlTextPatcher.rewrite("a: {b: 1} # c\n", JsonParser.parseString("{\"a\":{\"b\":1}}"), 2));
        assertEquals("---\na: 1\n---\nb: 2\n",
                YamlTextPatcher.rewrite("a: 0\n---\nb: 0\n", JsonParser.parseString("[{\"a\":1},{\"b\":2}]"), 2));
    }
}
