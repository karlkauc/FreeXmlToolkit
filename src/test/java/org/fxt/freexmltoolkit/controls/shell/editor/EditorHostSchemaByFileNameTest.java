package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.SchemaKind;
import org.fxt.freexmltoolkit.domain.SchemaLibraryEntry;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.SchemaLibraryService;
import org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl;
import org.fxt.freexmltoolkit.service.SchemaResourceCache;
import org.fxt.freexmltoolkit.service.schemastore.SchemaStoreCatalog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * A JSON or YAML file that declares no schema gets one by its file name: from a Schema Library
 * entry's file patterns, else (when switched on) from the SchemaStore catalog. The binding
 * survives the validation-time reconcile.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostSchemaByFileNameTest {

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
        ServiceRegistry.get(PropertiesService.class).set(SchemaStoreCatalog.ENABLED_KEY, "false");
        SchemaStoreCatalog.setShared(null);
        ServiceRegistry.reset();
    }

    private void library(Path dir, SchemaLibraryEntry entry) {
        var svc = new SchemaLibraryServiceImpl(dir.resolve("lib.json"), new SchemaResourceCache(dir.resolve("cache")),
                () -> new ByteArrayInputStream("{\"version\":1,\"entries\":[]}".getBytes(StandardCharsets.UTF_8)));
        if (entry != null) {
            svc.addEntry(entry);
        }
        ServiceRegistry.register(SchemaLibraryService.class, svc);
    }

    @Test
    void aLibraryFilePatternBindsTheSchemaAndSurvivesValidation(@TempDir Path tmp) throws Exception {
        Path schema = tmp.resolve("compose.schema.json");
        Files.writeString(schema, "{\"type\":\"object\",\"properties\":{\"services\":{\"type\":\"object\"}}}");
        library(tmp, SchemaLibraryEntry.user("", schema.toString(), SchemaKind.JSON_SCHEMA, "compose", null)
                .withFileMatch(List.of("docker-compose*.yml")));

        Path yaml = tmp.resolve("docker-compose.prod.yml");
        Files.writeString(yaml, "# no modeline\nservices: 3\n");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(yaml.toFile()));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> host.activeSchemaProperty().get() != null
                && host.activeSchemaStatusProperty().get() == EditorHost.SchemaStatus.READY);
        assertEquals(schema.toFile().getAbsoluteFile(), host.activeSchemaProperty().get().getAbsoluteFile());
        assertEquals(EditorHost.SchemaSource.LIBRARY, host.activeSchemaSourceProperty().get());
        assertEquals("file name docker-compose*.yml", host.activeSchemaSourceDetailProperty().get());

        // the validation-time reconcile keeps it (the file name counts as a declaration)
        var supplier = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.schemaForValidation(host.getActiveText().orElseThrow()));
        File forValidation = CompletableFuture.supplyAsync(supplier::get).get(5, TimeUnit.SECONDS);
        assertEquals(schema.toFile().getAbsoluteFile(), forValidation.getAbsoluteFile());
        assertEquals(1, ValidationRunner.validateYaml(host.getActiveText().orElseThrow(), forValidation).size());
    }

    @Test
    void aDeclaredSchemaWinsOverTheFileName(@TempDir Path tmp) throws Exception {
        Path byName = tmp.resolve("by-name.json");
        Files.writeString(byName, "{\"type\":\"object\"}");
        Path declared = tmp.resolve("declared.json");
        Files.writeString(declared, "{\"type\":\"object\"}");
        library(tmp, SchemaLibraryEntry.user("", byName.toString(), SchemaKind.JSON_SCHEMA, "", null)
                .withFileMatch(List.of("*.yml")));
        Path yaml = tmp.resolve("a.yml");
        Files.writeString(yaml, "# yaml-language-server: $schema=./declared.json\na: 1\n");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(yaml.toFile()));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> host.activeSchemaProperty().get() != null);
        assertEquals(declared.toAbsolutePath().normalize(),
                host.activeSchemaProperty().get().toPath().toAbsolutePath().normalize());
    }

    @Test
    void theSchemaStoreCatalogIsAskedOnlyWhenSwitchedOn(@TempDir Path tmp) throws Exception {
        library(tmp, null);
        Path catalogFile = tmp.resolve("catalog.json");
        Files.writeString(catalogFile, """
                {"schemas": [{"name": "GitHub Workflow", "fileMatch": [".github/workflows/*.yml"],
                              "url": "https://json.schemastore.org/github-workflow.json"}]}
                """);
        SchemaStoreCatalog.setShared(new SchemaStoreCatalog(catalogFile, uri -> {
            throw new java.io.IOException("offline");
        }));
        Path workflow = tmp.resolve(".github/workflows/ci.yml");
        Files.createDirectories(workflow.getParent());
        Files.writeString(workflow, "on: push\n");

        assertNull(EditorSchemaBinding.schemaByFileName(workflow, false), "off by default");
        ServiceRegistry.get(PropertiesService.class).set(SchemaStoreCatalog.ENABLED_KEY, "true");
        var hit = EditorSchemaBinding.schemaByFileName(workflow, false);
        assertNotNull(hit);
        assertEquals("https://json.schemastore.org/github-workflow.json", hit.entry().location());
        assertEquals("SchemaStore: GitHub Workflow, file name .github/workflows/*.yml", hit.detail());

        // opened offline (tests do not download): the schema it names is reported, not silently dropped
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(workflow.toFile()));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> host.activeSchemaStatusProperty().get() == EditorHost.SchemaStatus.ERROR);
    }
}
