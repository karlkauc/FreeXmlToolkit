package org.fxt.freexmltoolkit.controls.shell.inspector;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;
import org.fxt.freexmltoolkit.controls.jsoneditor.view.JsonTreeView;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.ViewMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The inspector shows what the bound JSON Schema says about the selected JSON value. */
@ExtendWith(ApplicationExtension.class)
class InspectorJsonSchemaTest {

    private EditorHost host;
    private InspectorPanel inspector;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        inspector = new InspectorPanel(host);
        stage.setScene(new Scene(new HBox(host, inspector), 1100, 700));
        stage.show();
    }

    @Test
    void schemaSectionDescribesTheSelectedValue(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("s.json"), """
                {"type": "object", "required": ["status"],
                 "properties": {"status": {"enum": ["open", "closed"], "description": "State", "default": "open"},
                                "list": {"type": "array", "items": {"type": "integer", "minimum": 1}}}}
                """);
        Path json = tmp.resolve("data.json");
        Files.writeString(json, "{\n  \"$schema\": \"./s.json\",\n  \"status\": \"open\",\n  \"list\": [1]\n}\n");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(json));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> host.getActiveJsonSchema().isPresent());
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        Thread.sleep(300);
        JsonTreeView tree = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> (JsonTreeView) host.lookupAll("*").stream().filter(n -> n instanceof JsonTreeView).findFirst()
                        .orElseThrow());
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            JsonNode status = ((JsonObject) tree.getDocument().getRootValue()).getProperty("status");
            tree.selectNode(status);
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> "status".equals(inspector.getNodeNameText()));
        var list = WaitForAsyncUtils.waitForAsyncFx(2000, () -> (InspectorKeyValueList<?>) inspector.lookup("#inspector-json-schema"));
        assertNotNull(list);
        assertTrue(list.isVisible() || list.getParent() != null);
        String shown = WaitForAsyncUtils.waitForAsyncFx(2000, () -> String.join("|",
                list.lookupAll(".label").stream().map(n -> ((javafx.scene.control.Labeled) n).getText()).toList()));
        assertTrue(shown.contains("State"), shown);
        assertTrue(shown.contains("\"open\", \"closed\""), shown);
        assertTrue(shown.contains("Required"), shown);
    }

    @Test
    void jsonPathAndEntries() {
        var context = new org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonEditorContext();
        context.loadDocumentFromString("{\"a\": [{\"b\": 1}]}");
        JsonObject root = (JsonObject) context.getDocument().getRootValue();
        JsonNode b = ((JsonObject) root.getProperty("a").getChild(0)).getProperty("b");
        assertEquals(List.of("a", 0, "b"), InspectorPanel.jsonPath(b));
        assertEquals(List.of(), InspectorPanel.jsonPath(root));
    }
}
