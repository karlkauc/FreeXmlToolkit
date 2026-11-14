package org.fxt.freexmltoolkit.controls.jsoneditor.grid;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonEditorContext;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonArray;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.FlatRow;
import org.junit.jupiter.api.Test;

/** JSONPath filters of the JSON grid ({@link JsonGridAdapter#filterPredicate}). */
class JsonGridFilterTest {

    private static final String DOC = """
            {"orders": [
              {"id": "A", "total": 5, "customer": {"name": "Ann", "vip": true}},
              {"id": "B", "total": 50, "customer": {"name": "Bob", "vip": false}},
              {"id": "C", "total": 500, "customer": {"name": "it's me", "vip": true}}
            ]}
            """;

    private JsonEditorContext context;
    private JsonGridAdapter adapter;
    private FlatRow root;

    private void load() {
        context = new JsonEditorContext();
        context.loadDocumentFromString(DOC);
        adapter = new JsonGridAdapter(context);
        root = adapter.flatten().get(0);
    }

    private JsonArray orders() {
        return (JsonArray) ((JsonObject) context.getDocument().getRootValue()).getProperty("orders");
    }

    @Test
    void matchesTheirAncestorsAndSubtreesPass() {
        load();
        var keep = adapter.filterPredicate(root, "$.orders[?(@.total > 10)]");
        JsonNode a = orders().get(0);
        JsonNode b = orders().get(1);
        assertFalse(keep.test(a));
        assertTrue(keep.test(b));
        assertTrue(keep.test(((JsonObject) b).getProperty("customer")), "a match's subtree stays");
        assertTrue(keep.test(orders()), "the matches' ancestors stay");
    }

    @Test
    void deepScanAndQuotedKeys() {
        load();
        var keep = adapter.filterPredicate(root, "$..customer[?(@.vip == true)]");
        assertTrue(keep.test(((JsonObject) orders().get(2)).getProperty("customer")));
        assertFalse(keep.test(((JsonObject) orders().get(1)).getProperty("customer")));
        assertSame(((JsonObject) ((JsonObject) orders().get(2)).getProperty("customer")).getProperty("name"),
                JsonGridAdapter.nodeAtPath(context.getDocument().getRootValue(), "$['orders'][2]['customer']['name']"));
    }

    @Test
    void invalidExpressionsAreReported() {
        load();
        assertThrows(IllegalArgumentException.class, () -> adapter.filterPredicate(root, "$[?(@.x >"));
    }

    @Test
    void suggestedExpressionsSelectTheValueAndTheFocus() {
        load();
        JsonObject c = (JsonObject) orders().get(2);
        String byName = adapter.valueFilterExpression(c.getProperty("customer"), "name", "it's me");
        assertEquals("$[?(@['name'] == 'it\\'s me')]", byName);
        assertEquals("$[?(@['total'] == 500)]", adapter.valueFilterExpression(c, "total", "500"));

        String focus = adapter.focusFilterExpression(root, c.getProperty("id"));
        assertEquals("$['orders'][2]['id']", focus);
        var keep = adapter.filterPredicate(root, focus);
        assertTrue(keep.test(c), "the focused node's ancestors stay");
        assertFalse(keep.test(orders().get(0)));
    }

    @Test
    void resultsFollowEdits() {
        load();
        assertFalse(adapter.filterPredicate(root, "$.orders[?(@.total > 100)]").test(orders().get(0)));
        JsonObject a = (JsonObject) orders().get(0);
        context.executeCommand(new org.fxt.freexmltoolkit.controls.jsoneditor.commands.SetPrimitiveValueCommand(
                (org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonPrimitive) a.getProperty("total"), 1000));
        assertTrue(adapter.filterPredicate(root, "$.orders[?(@.total > 100)]").test(orders().get(0)),
                "a cached result of the old model is not reused");
        assertEquals(List.of(), List.of());
    }
}
