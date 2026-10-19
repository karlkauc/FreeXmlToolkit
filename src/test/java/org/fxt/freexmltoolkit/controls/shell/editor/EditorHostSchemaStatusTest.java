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
    void addedReferenceIsPickedUpOnValidate(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("schema.xsd"), XSD);
        Path xml = tmp.resolve("plain.xml");
        Files.writeString(xml, "<root/>\n");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        awaitStatus(SchemaStatus.NONE);

        // The user adds the schema reference in the buffer (unsaved) and validates.
        String edited = """
                <root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:noNamespaceSchemaLocation="schema.xsd"/>
                """;
        var supplier = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.activeEditorView().setText(edited);
            return host.schemaForValidation(edited);
        });
        java.io.File xsd = supplier.get(); // worker-thread resolution, as in the validation runs

        assertNotNull(xsd, "the newly declared schema must be used for this validation run");
        assertEquals("schema.xsd", xsd.getName());
        awaitStatus(SchemaStatus.READY);
        assertNotNull(host.activeSchemaProperty().get());
    }

    @Test
    void removedReferenceDowngradesToNone(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("schema.xsd"), XSD);
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, """
                <root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:noNamespaceSchemaLocation="schema.xsd"/>
                """);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        awaitStatus(SchemaStatus.READY);

        String edited = "<root/>\n";
        var supplier = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.activeEditorView().setText(edited);
            return host.schemaForValidation(edited);
        });
        java.io.File xsd = supplier.get();

        assertNull(xsd, "with the reference removed, validation must degrade to well-formed");
        awaitStatus(SchemaStatus.NONE);
        assertNull(host.activeSchemaProperty().get());
    }

    @Test
    void manualBindingSurvivesBufferChange(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("plain.xml");
        Files.writeString(xml, "<root/>\n");
        Path goodXsd = tmp.resolve("good.xsd");
        Files.writeString(goodXsd, XSD);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        awaitStatus(SchemaStatus.NONE);
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(12000,
                () -> host.setSchemaForActiveDocument(goodXsd.toFile())).get(30, java.util.concurrent.TimeUnit.SECONDS));
        awaitStatus(SchemaStatus.READY);

        // The buffer now declares a different (unresolvable) schema — the manual
        // binding must win and stay untouched.
        String edited = """
                <root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:noNamespaceSchemaLocation="other.xsd"/>
                """;
        var supplier = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.activeEditorView().setText(edited);
            return host.schemaForValidation(edited);
        });
        java.io.File xsd = supplier.get();

        assertNotNull(xsd, "a manual binding must never be dropped by auto-detection");
        assertEquals("good.xsd", xsd.getName());
        awaitStatus(SchemaStatus.READY);
        assertEquals("good.xsd", host.activeSchemaProperty().get().getName());
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
                () -> host.setSchemaForActiveDocument(goodXsd.toFile())).get(30, java.util.concurrent.TimeUnit.SECONDS));
        awaitStatus(SchemaStatus.READY);

        assertFalse(WaitForAsyncUtils.waitForAsyncFx(12000,
                () -> host.setSchemaForActiveDocument(badXsd.toFile())).get(30, java.util.concurrent.TimeUnit.SECONDS));
        awaitStatus(SchemaStatus.ERROR);
    }

    @Test
    void manualBindParsesOffTheFxThread(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("plain.xml");
        Files.writeString(xml, "<root/>\n");
        Path goodXsd = tmp.resolve("good.xsd");
        Files.writeString(goodXsd, XSD);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        awaitStatus(SchemaStatus.NONE);

        // Telemetry: binding a large XSD froze the UI for up to two minutes. The call must return
        // at once with the status on LOADING, and complete once the worker has parsed the schema.
        record Started(java.util.concurrent.CompletableFuture<Boolean> bound, SchemaStatus status) {
        }
        Started started = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            var bound = host.setSchemaForActiveDocument(goodXsd.toFile());
            return new Started(bound, host.activeSchemaStatusProperty().get());
        });
        assertEquals(SchemaStatus.LOADING, started.status(), "the FX thread only starts the bind");
        assertTrue(started.bound().get(30, TimeUnit.SECONDS));
        awaitStatus(SchemaStatus.READY);
        assertEquals("good.xsd", host.activeSchemaProperty().get().getName());
    }

    @Test
    void newerManualBindSupersedesAPendingOne(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("plain.xml");
        Files.writeString(xml, "<root/>\n");
        Path firstXsd = tmp.resolve("first.xsd");
        Files.writeString(firstXsd, XSD);
        Path secondXsd = tmp.resolve("second.xsd");
        Files.writeString(secondXsd, XSD);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        awaitStatus(SchemaStatus.NONE);

        // Both binds are issued in one FX pulse, so the first is still parsing when the second arrives.
        var futures = WaitForAsyncUtils.waitForAsyncFx(2000, () -> java.util.List.of(
                host.setSchemaForActiveDocument(firstXsd.toFile()),
                host.setSchemaForActiveDocument(secondXsd.toFile())));
        assertFalse(futures.get(0).get(30, TimeUnit.SECONDS), "the superseded bind reports that it did not bind");
        assertTrue(futures.get(1).get(30, TimeUnit.SECONDS));
        awaitStatus(SchemaStatus.READY);
        assertEquals("second.xsd", host.activeSchemaProperty().get().getName(), "the newest choice wins");
    }

    @Test
    void validationReconcileDuringAManualBindKeepsTheChoice(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, """
                <root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:noNamespaceSchemaLocation="declared.xsd"/>
                """);
        Files.writeString(tmp.resolve("declared.xsd"), XSD);
        Files.writeString(tmp.resolve("other.xsd"), XSD);
        Path manualXsd = tmp.resolve("manual.xsd");
        Files.writeString(manualXsd, XSD);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        awaitStatus(SchemaStatus.READY);

        // A validation run whose buffer declares another schema (so it re-detects) starts while the manual
        // schema is still being parsed. Whichever takes the tab's schema lock first, the user's choice must
        // be what stays bound.
        String edited = """
                <root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:noNamespaceSchemaLocation="other.xsd"/>
                """;
        record Pending(java.util.concurrent.CompletableFuture<Boolean> bound,
                       java.util.function.Supplier<java.io.File> validationSchema) {
        }
        Pending pending = WaitForAsyncUtils.waitForAsyncFx(2000, () -> new Pending(
                host.setSchemaForActiveDocument(manualXsd.toFile()),
                host.schemaForValidation(edited)));
        pending.validationSchema().get(); // the reconcile, on this (worker) thread
        assertTrue(pending.bound().get(30, TimeUnit.SECONDS));

        awaitStatus(SchemaStatus.READY);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("manual.xsd", host.activeSchemaProperty().get().getName());
        var next = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.schemaForValidation(edited));
        assertEquals("manual.xsd", next.get().getName(), "later validations keep using the manual choice");
    }
}
