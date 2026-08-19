package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The Validation panel treats JSON Schema as a first-class source: the SOURCES rows
 * follow the active document's type (JSON Schema row for JSON, XSD + Schematron for
 * the XML family), binding goes through the EditorHost (visible in
 * {@code activeSchemaProperty}), problems carry line numbers, and a declared
 * {@code "$schema"} auto-binds when the panel opens.
 */
@ExtendWith(ApplicationExtension.class)
class ValidationPanelJsonSchemaTest {

    private static final String SCHEMA = """
            {
              "type": "object",
              "required": ["name"],
              "properties": { "name": { "type": "string" } }
            }
            """;

    private EditorHost host;
    private ValidationPanel panel;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        panel = new ValidationPanel(host);
        stage.setScene(new Scene(new HBox(host, panel), 1100, 600));
        stage.show();
    }

    private void openAndAwait(Path file, String contained) throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(file));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains(contained)).orElse(false));
    }

    private boolean rowVisible(String id) {
        var node = panel.lookup("#" + id);
        return node != null && node.isVisible() && node.isManaged();
    }

    @Test
    void sourceRowsFollowTheActiveDocumentType(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root/>");
        Path json = tmp.resolve("doc.json");
        Files.writeString(json, "{\"a\": 1}");

        openAndAwait(xml, "root");
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> rowVisible("validation-xsd-row"));
        assertTrue(rowVisible("validation-schematron-row"));
        assertFalse(rowVisible("validation-json-schema-row"),
                "the JSON Schema row must be hidden for XML documents");

        openAndAwait(json, "\"a\"");
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> rowVisible("validation-json-schema-row"));
        assertFalse(rowVisible("validation-xsd-row"),
                "the XSD row must be hidden for JSON documents");
        assertFalse(rowVisible("validation-schematron-row"));
    }

    @Test
    void useJsonSchemaBindsViaTheHostAndReportsLineNumberedProblems(@TempDir Path tmp) throws Exception {
        Path schema = tmp.resolve("product-schema.json");
        Files.writeString(schema, SCHEMA);
        Path json = tmp.resolve("doc.json");
        Files.writeString(json, """
                {
                  "name": 42
                }
                """);

        openAndAwait(json, "name");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.useJsonSchema(schema.toFile());
            return null;
        });

        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS,
                () -> host.activeSchemaProperty().get() != null);
        assertEquals("product-schema.json", host.activeSchemaProperty().get().getName(),
                "the binding must live on the EditorHost, not in panel state");

        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> panel.getProblemCount() > 0);
        var problem = host.getActiveProblems().get(0);
        assertEquals("JSON Schema", problem.source());
        assertEquals(2, problem.line(), "the problem must point at the offending property's line");
    }

    @Test
    void declaredSchemaAutoBindsAndValidatesOnRun(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("product-schema.json"), SCHEMA);
        Path json = tmp.resolve("doc.json");
        Files.writeString(json, "{\"$schema\": \"./product-schema.json\"}");

        openAndAwait(json, "$schema");
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS,
                () -> host.activeSchemaProperty().get() != null);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.revalidate();
            return null;
        });
        // missing required "name" → exactly the auto-bound schema must have been used
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> panel.getProblemCount() > 0);
        assertTrue(panel.getProblemCount() > 0,
                "validation must use the auto-bound $schema without a manual binding");
    }

    @Test
    void liveValidationFiresForJsonDocuments(@TempDir Path tmp) throws Exception {
        Path schema = tmp.resolve("product-schema.json");
        Files.writeString(schema, SCHEMA);
        Path json = tmp.resolve("doc.json");
        Files.writeString(json, "{\"name\": \"ok\"}");

        openAndAwait(json, "name");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.useJsonSchema(schema.toFile());
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS,
                () -> panel.getStatusText().matches("Valid( · .*)?"));

        // Typing an invalid value must re-validate via the debounce (no manual run).
        assertTrue(panel.isLiveValidationEnabled(), "live validation is on by default");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.activeEditorView().setText("{\"name\": 42}");
            host.activeEditorView().getCodeArea().moveTo(1);
            return null;
        });
        WaitForAsyncUtils.waitFor(6, TimeUnit.SECONDS, () -> panel.getProblemCount() > 0);
        assertTrue(panel.getProblemCount() > 0,
                "live validation must pick up JSON documents too");
    }
}
