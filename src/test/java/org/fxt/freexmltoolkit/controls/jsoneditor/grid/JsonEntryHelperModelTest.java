package org.fxt.freexmltoolkit.controls.jsoneditor.grid;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.util.List;

import org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonEditorContext;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonPrimitive;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.EntryHelperModel.Entry;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.TableEditOps.Placement;
import org.fxt.freexmltoolkit.service.jsonschema.JsonSchemaDocument;
import org.fxt.freexmltoolkit.service.jsonschema.JsonSchemaResolver;
import org.junit.jupiter.api.Test;

/** Tests {@link JsonEntryHelperModel}: the JSON grid's entry helpers from a JSON Schema. */
class JsonEntryHelperModelTest {

    private static final JsonSchemaResolver SCHEMA = new JsonSchemaResolver(JsonSchemaDocument.of("""
            {"type": "object", "required": ["name"],
             "properties": {
               "name": {"type": "string"},
               "status": {"enum": ["open", "closed"]},
               "address": {"type": "object", "properties": {"city": {"type": "string"}, "zip": {"type": "string"}}},
               "tags": {"type": "array"},
               "count": {"type": "integer", "default": 3}
             }}
            """, URI.create("mem:/s.json")));

    private JsonEditorContext load(String json) {
        JsonEditorContext context = new JsonEditorContext();
        context.loadDocumentFromString(json);
        return context;
    }

    private static List<String> names(List<Entry> entries) {
        return entries.stream().map(e -> (e.available() ? "" : "-") + e.name() + (e.mandatory() ? "!" : "")).toList();
    }

    @Test
    void siblingsChildrenAndValues() {
        JsonEditorContext context = load("{\"name\": \"x\", \"status\": \"open\", \"address\": {\"city\": \"Graz\"}}");
        JsonObject root = (JsonObject) context.getDocument().getRootValue();

        var onStatus = JsonEntryHelperModel.Focus.of(root.getProperty("status"));
        assertEquals(List.of("-name!", "-status", "-address", "tags", "count"),
                names(JsonEntryHelperModel.siblings(SCHEMA, onStatus)));
        assertEquals(List.of("-\"open\"", "\"closed\""), names(JsonEntryHelperModel.values(SCHEMA, onStatus)));
        assertTrue(JsonEntryHelperModel.children(SCHEMA, onStatus).isEmpty());

        var onAddress = JsonEntryHelperModel.Focus.of(root.getProperty("address"));
        assertEquals(List.of("-city", "zip"), names(JsonEntryHelperModel.children(SCHEMA, onAddress)));
    }

    @Test
    void commandsAddWithSkeletonsAndSetValues() {
        JsonEditorContext context = load("{\"name\": \"x\", \"status\": \"open\"}");
        JsonObject root = (JsonObject) context.getDocument().getRootValue();
        var onName = JsonEntryHelperModel.Focus.of(root.getProperty("name"));

        assertTrue(context.executeCommand(JsonEntryHelperModel.insertSibling(SCHEMA, onName,
                new Entry(org.fxt.freexmltoolkit.controls.v2.xmleditor.view.EntryHelperModel.Kind.PROPERTY, "count", false, true),
                Placement.AFTER)));
        assertEquals(List.of("name", "count", "status"), root.getChildren().stream().map(n -> n.getKey()).toList());
        assertEquals(3L, ((Number) ((JsonPrimitive) root.getProperty("count")).getValue()).longValue(), "the default");

        assertTrue(context.executeCommand(JsonEntryHelperModel.insertSibling(SCHEMA, onName,
                new Entry(org.fxt.freexmltoolkit.controls.v2.xmleditor.view.EntryHelperModel.Kind.PROPERTY, "address", false, true),
                Placement.APPEND)));
        assertInstanceOf(JsonObject.class, root.getProperty("address"), "an object skeleton");

        var onStatus = JsonEntryHelperModel.Focus.of(root.getProperty("status"));
        Entry closed = JsonEntryHelperModel.values(SCHEMA, onStatus).get(1);
        assertTrue(context.executeCommand(JsonEntryHelperModel.setValue(onStatus, closed)));
        assertEquals("closed", ((JsonPrimitive) root.getProperty("status")).getValue());
        assertNull(JsonEntryHelperModel.addChild(SCHEMA, onStatus, closed), "a scalar has no children");
    }

    @Test
    void instancePaths() {
        JsonEditorContext context = load("{\"a\": [{\"b\": 1}]}");
        JsonObject root = (JsonObject) context.getDocument().getRootValue();
        var b = ((JsonObject) root.getProperty("a").getChild(0)).getProperty("b");
        assertEquals(List.of("a", 0, "b"), JsonEntryHelperModel.instancePath(b));
        assertEquals(List.of(), JsonEntryHelperModel.instancePath(root));
    }
}
