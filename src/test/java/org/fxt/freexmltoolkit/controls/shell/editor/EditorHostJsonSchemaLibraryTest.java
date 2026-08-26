package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.scene.Scene;
import javafx.stage.Stage;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.SchemaKind;
import org.fxt.freexmltoolkit.domain.SchemaLibraryEntry;
import org.fxt.freexmltoolkit.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 12: a JSON document's top-level {@code "$schema"} member is bound through the Schema
 * Library when the raw id (meta-schema ids included, unlike {@link JsonService#getSchemaLocationFromJsonContent})
 * maps to a registered {@link SchemaKind#JSON_SCHEMA} entry — before {@code resolveJsonSchemaLocation}
 * would otherwise attempt to download it as a remote URL.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostJsonSchemaLibraryTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        ServiceRegistry.initialize();
        host = new EditorHost();
        stage.setScene(new Scene(host, 900, 600));
        stage.show();
    }

    @AfterEach
    void tearDown() {
        ServiceRegistry.get(PropertiesService.class).setSchemaLibraryAutoBindEnabled(true);
        ServiceRegistry.reset();
    }

    private void registerLibraryWith(Path dir, String schemaUri, Path schema) {
        var svc = new SchemaLibraryServiceImpl(dir.resolve("lib.json"), new SchemaResourceCache(dir.resolve("cache")),
                () -> new ByteArrayInputStream("{\"version\":1,\"entries\":[]}".getBytes()));
        svc.addEntry(SchemaLibraryEntry.user(schemaUri, schema.toString(), SchemaKind.JSON_SCHEMA, "", null));
        ServiceRegistry.register(SchemaLibraryService.class, svc);
    }

    @Test
    void jsonSchemaUriMappedInLibraryIsBound(@TempDir Path tmp) throws Exception {
        Path schema = tmp.resolve("person.schema.json");
        Files.writeString(schema, "{\"$id\":\"https://example.org/person.json\",\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}");
        registerLibraryWith(tmp, "https://example.org/person.json", schema);

        Path json = tmp.resolve("p.json");
        Files.writeString(json, "{\"$schema\":\"https://example.org/person.json\",\"name\":\"x\"}");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(json.toFile()));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> host.activeSchemaProperty().get() != null);
        assertEquals(schema.toFile().getAbsoluteFile(), host.activeSchemaProperty().get().getAbsoluteFile());
    }

    /**
     * Regression (Task 11 lesson applied to JSON): {@code declaredSchemaLocation} must
     * recognize the library-mapped raw {@code $schema} id as "declared" — the filtered
     * {@code getSchemaLocationFromJsonContent} alone would report it as undeclared and
     * {@code SchemaRebindPolicy.decideRebind(AUTO, null, rawId)} would CLEAR the binding on
     * the very first validation run. This drives the same path {@code ValidationPanel} uses:
     * {@link EditorHost#schemaForValidation(String)} on the FX thread, then resolving the
     * returned supplier off it.
     */
    @Test
    void jsonLibraryBindingSurvivesValidationReconcile(@TempDir Path tmp) throws Exception {
        Path schema = tmp.resolve("person.schema.json");
        Files.writeString(schema, "{\"$id\":\"https://example.org/person.json\",\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}");
        registerLibraryWith(tmp, "https://example.org/person.json", schema);

        Path json = tmp.resolve("p.json");
        Files.writeString(json, "{\"$schema\":\"https://example.org/person.json\",\"name\":\"x\"}");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(json.toFile()));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> host.activeSchemaProperty().get() != null);
        assertEquals(schema.toFile().getAbsoluteFile(), host.activeSchemaProperty().get().getAbsoluteFile());

        String content = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveText().orElse(""));
        Supplier<File> supplier = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.schemaForValidation(content));
        File validated = supplier.get(); // resolved off the FX thread, like ValidationPanel does

        assertNotNull(validated, "validation-time reconcile must keep the library binding");
        assertEquals(schema.toFile().getAbsoluteFile(), validated.getAbsoluteFile());
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(schema.toFile().getAbsoluteFile(), host.activeSchemaProperty().get().getAbsoluteFile());
    }
}
