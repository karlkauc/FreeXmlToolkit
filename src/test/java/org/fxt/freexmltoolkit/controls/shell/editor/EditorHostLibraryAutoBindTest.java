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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

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
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> host.activeSchemaProperty().get() != null);
        assertEquals(xsd.toFile().getAbsoluteFile(), host.activeSchemaProperty().get().getAbsoluteFile());
        assertEquals(EditorHost.SchemaStatus.READY, host.activeSchemaStatusProperty().get());
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
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> host.activeSchemaProperty().get() != null);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.setSchemaForActiveDocument(manualXsd.toFile()));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.redetectSchemaForActiveDocument());
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.sleep(500, TimeUnit.MILLISECONDS);
        assertEquals(manualXsd.toFile().getAbsoluteFile(), host.activeSchemaProperty().get().getAbsoluteFile());
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
