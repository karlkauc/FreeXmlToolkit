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
 * Task 11: a document without an {@code xsi:schemaLocation} reference is auto-bound to a
 * schema resolved from the Schema Library by root namespace (or root element, when
 * unnamespaced) — unless a manual binding is already active, or the auto-bind toggle is off.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostLibraryAutoBindTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:lib:test"
                       xmlns="urn:lib:test" elementFormDefault="qualified">
              <xs:element name="root"><xs:complexType><xs:sequence>
                <xs:element name="alpha" type="xs:string"/>
              </xs:sequence></xs:complexType></xs:element>
            </xs:schema>
            """;

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

    private Path registerLibraryWith(Path dir, String namespace, Path xsd) {
        var svc = new SchemaLibraryServiceImpl(dir.resolve("lib.json"), new SchemaResourceCache(dir.resolve("cache")),
                () -> new ByteArrayInputStream("{\"version\":1,\"entries\":[]}".getBytes()));
        svc.addEntry(SchemaLibraryEntry.user(namespace, xsd.toString(), SchemaKind.XSD, "", null));
        ServiceRegistry.register(SchemaLibraryService.class, svc);
        return xsd;
    }

    @Test
    void documentWithNamespaceOnlyIsBoundThroughLibrary(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("lib").resolve("test.xsd");
        Files.createDirectories(xsd.getParent());
        Files.writeString(xsd, XSD);
        registerLibraryWith(tmp, "urn:lib:test", xsd);
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root xmlns=\"urn:lib:test\"><alpha>x</alpha></root>\n");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml.toFile()));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> host.activeSchemaProperty().get() != null
                        && host.activeSchemaStatusProperty().get() == EditorHost.SchemaStatus.READY);
        assertEquals(xsd.toFile().getAbsoluteFile(), host.activeSchemaProperty().get().getAbsoluteFile());
        assertEquals(EditorHost.SchemaStatus.READY, host.activeSchemaStatusProperty().get());
        assertEquals(EditorHost.SchemaSource.LIBRARY, host.activeSchemaSourceProperty().get());
    }

    /**
     * Regression: {@code declaredSchemaLocation} must treat a Schema Library auto-binding as
     * "declared", or {@code SchemaRebindPolicy.decideRebind(AUTO, null, libPath)} would CLEAR
     * it on the very first validation run. This drives the same path
     * {@code ValidationPanel} uses: {@link EditorHost#schemaForValidation(String)} on the FX
     * thread, then resolving the returned supplier off it.
     */
    @Test
    void libraryBindingSurvivesValidationReconcile(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("lib").resolve("test.xsd");
        Files.createDirectories(xsd.getParent());
        Files.writeString(xsd, XSD);
        registerLibraryWith(tmp, "urn:lib:test", xsd);
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root xmlns=\"urn:lib:test\"><alpha>x</alpha></root>\n");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml.toFile()));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> host.activeSchemaProperty().get() != null
                        && host.activeSchemaStatusProperty().get() == EditorHost.SchemaStatus.READY);
        assertEquals(xsd.toFile().getAbsoluteFile(), host.activeSchemaProperty().get().getAbsoluteFile());

        String content = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveText().orElse(""));
        Supplier<File> supplier = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.schemaForValidation(content));
        File validated = supplier.get(); // resolved off the FX thread, like ValidationPanel does

        assertNotNull(validated, "validation-time reconcile must keep the library binding");
        assertEquals(xsd.toFile().getAbsoluteFile(), validated.getAbsoluteFile());
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(xsd.toFile().getAbsoluteFile(), host.activeSchemaProperty().get().getAbsoluteFile());
    }

    @Test
    void manualBindingIsNotOverriddenByLibrary(@TempDir Path tmp) throws Exception {
        Path libXsd = tmp.resolve("lib.xsd");
        Files.writeString(libXsd, XSD);
        Path manualXsd = tmp.resolve("manual.xsd");
        Files.writeString(manualXsd, XSD);
        registerLibraryWith(tmp, "urn:lib:test", libXsd);
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root xmlns=\"urn:lib:test\"/>\n");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml.toFile()));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> host.activeSchemaProperty().get() != null
                        && host.activeSchemaStatusProperty().get() == EditorHost.SchemaStatus.READY);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.setSchemaForActiveDocument(manualXsd.toFile()));
        WaitForAsyncUtils.waitForFxEvents();

        // redetectSchemaForActiveDocument() is a no-op once a schema is bound (it returns
        // early on tab.schemaFile != null), so it never actually contends with the library.
        // schemaForValidation(...) — the path ValidationPanel drives on every validation run
        // — is what arbitrates MANUAL vs. the library's AUTO candidate; exercise that instead.
        String content = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveText().orElse(""));
        Supplier<File> supplier = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.schemaForValidation(content));
        File validated = supplier.get(); // resolved off the FX thread, like ValidationPanel does

        assertEquals(manualXsd.toFile().getAbsoluteFile(), validated.getAbsoluteFile());
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(manualXsd.toFile().getAbsoluteFile(), host.activeSchemaProperty().get().getAbsoluteFile());
        assertEquals(EditorHost.SchemaSource.MANUAL, host.activeSchemaSourceProperty().get());
    }

    @Test
    void declaredUnreachableLocationIsRewrittenByCatalog(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schemas").resolve("test.xsd");
        Files.createDirectories(xsd.getParent());
        Files.writeString(xsd, XSD);
        Path catalog = tmp.resolve("catalog.xml");
        Files.writeString(catalog, "<catalog xmlns='urn:oasis:names:tc:entity:xmlns:xml:catalog'>"
                + "<rewriteSystem systemIdStartString='http://schemas.invalid/v1/' rewritePrefix='schemas/'/></catalog>");
        var svc = new SchemaLibraryServiceImpl(tmp.resolve("lib.json"), new SchemaResourceCache(tmp.resolve("cache")),
                () -> new ByteArrayInputStream("{\"version\":1,\"entries\":[]}".getBytes()));
        svc.addCatalog(catalog);
        ServiceRegistry.register(SchemaLibraryService.class, svc);
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root xmlns=\"urn:lib:test\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                + " xsi:schemaLocation=\"urn:lib:test http://schemas.invalid/v1/test.xsd\"><alpha>x</alpha></root>\n");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml.toFile()));
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> host.activeSchemaProperty().get() != null
                        && host.activeSchemaStatusProperty().get() == EditorHost.SchemaStatus.READY);
        assertEquals(xsd.toFile().getAbsoluteFile(), host.activeSchemaProperty().get().getAbsoluteFile());
        assertEquals(EditorHost.SchemaStatus.READY, host.activeSchemaStatusProperty().get());
        assertEquals(EditorHost.SchemaSource.CATALOG, host.activeSchemaSourceProperty().get());
    }

    @Test
    void declaredUnreachableLocationFallsBackToNamespaceMapping(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("lib").resolve("test.xsd");
        Files.createDirectories(xsd.getParent());
        Files.writeString(xsd, XSD);
        registerLibraryWith(tmp, "urn:lib:test", xsd);
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root xmlns=\"urn:lib:test\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                + " xsi:schemaLocation=\"urn:lib:test http://schemas.invalid/v1/test.xsd\"><alpha>x</alpha></root>\n");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml.toFile()));
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> host.activeSchemaProperty().get() != null
                        && host.activeSchemaStatusProperty().get() == EditorHost.SchemaStatus.READY);
        assertEquals(xsd.toFile().getAbsoluteFile(), host.activeSchemaProperty().get().getAbsoluteFile());
        assertEquals(EditorHost.SchemaStatus.READY, host.activeSchemaStatusProperty().get());
        assertEquals(EditorHost.SchemaSource.LIBRARY, host.activeSchemaSourceProperty().get());
    }

    @Test
    void toggleOffDisablesLibraryAutoBind(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("lib.xsd");
        Files.writeString(xsd, XSD);
        registerLibraryWith(tmp, "urn:lib:test", xsd);
        ServiceRegistry.get(PropertiesService.class).setSchemaLibraryAutoBindEnabled(false);
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root xmlns=\"urn:lib:test\"/>\n");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml.toFile()));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> host.getActiveText().map(t -> t.contains("root")).orElse(false));
        WaitForAsyncUtils.sleep(1500, TimeUnit.MILLISECONDS);
        assertNull(host.activeSchemaProperty().get());
    }
}
