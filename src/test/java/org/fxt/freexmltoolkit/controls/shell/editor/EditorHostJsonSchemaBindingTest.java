package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.SchemaStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * JSON documents participate in the per-tab schema-binding lifecycle exactly like the
 * XML family: a top-level {@code "$schema"} member auto-binds a JSON Schema (READY),
 * no declaration settles on NONE, an unresolvable declaration on ERROR, meta-schema
 * ids bind nothing, manual bindings publish READY/ERROR, and a removed declaration
 * clears an AUTO binding at validation time.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostJsonSchemaBindingTest {

    private static final String SCHEMA = """
            {
              "type": "object",
              "required": ["name"],
              "properties": { "name": { "type": "string" } }
            }
            """;

    private EditorHost host;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        stage.setScene(new Scene(host, 900, 600));
        stage.show();
    }

    private void awaitStatus(SchemaStatus expected) throws Exception {
        WaitForAsyncUtils.waitFor(12, TimeUnit.SECONDS,
                () -> host.activeSchemaStatusProperty().get() == expected);
    }

    @Test
    void declaredSchemaSettlesOnReady(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("product-schema.json"), SCHEMA);
        Path json = tmp.resolve("doc.json");
        Files.writeString(json, """
                {
                  "$schema": "./product-schema.json",
                  "name": "widget"
                }
                """);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(json));
        awaitStatus(SchemaStatus.READY);
        assertNotNull(host.activeSchemaProperty().get(), "READY must come with the bound schema");
        assertEquals("product-schema.json", host.activeSchemaProperty().get().getName());
    }

    @Test
    void noDeclarationSettlesOnNone(@TempDir Path tmp) throws Exception {
        Path json = tmp.resolve("plain.json");
        Files.writeString(json, "{\"a\": 1}\n");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(json));
        awaitStatus(SchemaStatus.NONE);
        assertNull(host.activeSchemaProperty().get());
    }

    @Test
    void unresolvableDeclarationSettlesOnError(@TempDir Path tmp) throws Exception {
        Path json = tmp.resolve("broken.json");
        Files.writeString(json, "{\"$schema\": \"./does-not-exist.json\"}\n");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(json));
        awaitStatus(SchemaStatus.ERROR);
        assertNull(host.activeSchemaProperty().get(),
                "an unresolvable schema reference must not bind a schema");
    }

    @Test
    void metaSchemaIdBindsNothing(@TempDir Path tmp) throws Exception {
        // A schema document declaring its dialect is NOT an instance-validation binding.
        Path json = tmp.resolve("a-schema.json");
        Files.writeString(json, """
                {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "object"
                }
                """);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(json));
        awaitStatus(SchemaStatus.NONE);
        assertNull(host.activeSchemaProperty().get());
    }

    @Test
    void manualBindPublishesReadyOrError(@TempDir Path tmp) throws Exception {
        Path json = tmp.resolve("plain.json");
        Files.writeString(json, "{\"a\": 1}\n");
        Path goodSchema = tmp.resolve("good-schema.json");
        Files.writeString(goodSchema, SCHEMA);
        Path badSchema = tmp.resolve("bad-schema.json");
        Files.writeString(badSchema, "this is not JSON at all");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(json));
        awaitStatus(SchemaStatus.NONE);

        assertTrue(WaitForAsyncUtils.waitForAsyncFx(12000,
                () -> host.setSchemaForActiveDocument(goodSchema.toFile())));
        awaitStatus(SchemaStatus.READY);
        assertEquals("good-schema.json", host.activeSchemaProperty().get().getName());

        assertFalse(WaitForAsyncUtils.waitForAsyncFx(12000,
                () -> host.setSchemaForActiveDocument(badSchema.toFile())));
        awaitStatus(SchemaStatus.ERROR);
    }

    @Test
    void removedDeclarationDowngradesToNone(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("product-schema.json"), SCHEMA);
        Path json = tmp.resolve("doc.json");
        Files.writeString(json, "{\"$schema\": \"./product-schema.json\", \"name\": \"x\"}\n");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(json));
        awaitStatus(SchemaStatus.READY);

        String edited = "{\"name\": \"x\"}\n";
        var supplier = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.activeEditorView().setText(edited);
            return host.schemaForValidation(edited);
        });
        java.io.File schema = supplier.get(); // worker-thread resolution, as in validation runs

        assertNull(schema, "with the declaration removed, validation must degrade to well-formed");
        awaitStatus(SchemaStatus.NONE);
        assertNull(host.activeSchemaProperty().get());
    }

    @Test
    void toolbarValidateUsesTheBoundJsonSchema(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("product-schema.json"), SCHEMA);
        Path json = tmp.resolve("doc.json");
        // Declares a schema but misses the required "name" — only a run that actually
        // uses the bound schema can produce a problem here.
        Files.writeString(json, "{\"$schema\": \"./product-schema.json\"}\n");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(json));
        awaitStatus(SchemaStatus.READY);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            new EditorActions(host).validateActive();
            return null;
        });
        WaitForAsyncUtils.waitFor(6, TimeUnit.SECONDS, () ->
                host.validationStatusProperty().get().state() == EditorHost.ValidationState.INVALID);
        assertTrue(host.validationStatusProperty().get().problemCount() > 0,
                "toolbar/F8 validation must validate against the bound JSON Schema");
    }

    @Test
    void addedDeclarationIsPickedUpOnValidate(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("product-schema.json"), SCHEMA);
        Path json = tmp.resolve("plain.json");
        Files.writeString(json, "{\"a\": 1}\n");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(json));
        awaitStatus(SchemaStatus.NONE);

        String edited = "{\"$schema\": \"./product-schema.json\", \"name\": \"x\"}\n";
        var supplier = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.activeEditorView().setText(edited);
            return host.schemaForValidation(edited);
        });
        java.io.File schema = supplier.get();

        assertNotNull(schema, "the newly declared schema must be used for this validation run");
        assertEquals("product-schema.json", schema.getName());
        awaitStatus(SchemaStatus.READY);
    }
}
