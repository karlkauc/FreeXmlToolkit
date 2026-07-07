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
 * The active document's schema-binding lifecycle ({@link EditorHost.SchemaStatus}) drives the
 * status bar's IntelliSense indicator: it must settle on READY when a linked XSD loads, on NONE
 * when the document references no schema (LOADING must never stick), on ERROR when a referenced
 * schema cannot be loaded, and it must follow tab switches.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostSchemaStatusTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="alpha" type="xs:string" minOccurs="0"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
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

    private void awaitText(String contained) throws Exception {
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains(contained)).orElse(false));
    }

    @Test
    void linkedXsdSettlesOnReady(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("schema.xsd"), XSD);
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, """
                <root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:noNamespaceSchemaLocation="schema.xsd"/>
                """);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        awaitStatus(SchemaStatus.READY);
        assertNotNull(host.activeSchemaProperty().get(),
                "READY must come with the bound XSD");
        assertEquals("schema.xsd", host.activeSchemaProperty().get().getName());
    }

    @Test
    void noSchemaReferenceSettlesOnNone(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("plain.xml");
        Files.writeString(xml, "<root/>\n");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        awaitText("root");
        // LOADING must not stick once detection finished without a schema reference.
        awaitStatus(SchemaStatus.NONE);
        assertNull(host.activeSchemaProperty().get());
    }

    @Test
    void unresolvableReferenceSettlesOnError(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("broken.xml");
        Files.writeString(xml, """
                <root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:noNamespaceSchemaLocation="does-not-exist.xsd"/>
                """);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        awaitStatus(SchemaStatus.ERROR);
        assertNull(host.activeSchemaProperty().get(),
                "an unresolvable schema reference must not bind an XSD");
    }

    @Test
    void tabSwitchRestoresPerTabStatus(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("schema.xsd"), XSD);
        Path withSchema = tmp.resolve("with.xml");
        Files.writeString(withSchema, """
                <root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:noNamespaceSchemaLocation="schema.xsd"/>
                """);
        Path withoutSchema = tmp.resolve("without.xml");
        Files.writeString(withoutSchema, "<root/>\n");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(withSchema));
        awaitStatus(SchemaStatus.READY);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(withoutSchema));
        awaitStatus(SchemaStatus.NONE);

        // Re-opening an already open file re-selects its tab; the stored status returns.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(withSchema));
        awaitStatus(SchemaStatus.READY);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(withoutSchema));
        awaitStatus(SchemaStatus.NONE);
    }

    @Test
    void manualBindPublishesReadyOrError(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("plain.xml");
        Files.writeString(xml, "<root/>\n");
        Path goodXsd = tmp.resolve("good.xsd");
        Files.writeString(goodXsd, XSD);
        Path badXsd = tmp.resolve("bad.xsd");
        Files.writeString(badXsd, "this is not an XSD at all");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        awaitStatus(SchemaStatus.NONE);

        assertTrue(WaitForAsyncUtils.waitForAsyncFx(12000,
                () -> host.setSchemaForActiveDocument(goodXsd.toFile())));
        awaitStatus(SchemaStatus.READY);

        assertFalse(WaitForAsyncUtils.waitForAsyncFx(12000,
                () -> host.setSchemaForActiveDocument(badXsd.toFile())));
        awaitStatus(SchemaStatus.ERROR);
    }
}
