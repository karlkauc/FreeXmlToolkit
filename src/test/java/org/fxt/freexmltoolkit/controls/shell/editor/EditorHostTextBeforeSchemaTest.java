package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * A document's text must appear as soon as the file has been read, without waiting for the
 * schema detection that follows it.
 * <p>
 * Detection resolves the declared {@code xsi:schemaLocation}, which for a remote location means a
 * download: measured at ~10 s for an instance whose namespace is a GitHub URL. While the text was
 * published in the same FX pulse as the detection result, the editor stayed empty for that whole
 * time.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostTextBeforeSchemaTest {

    /**
     * Points at a routable-but-dead address, so the detection blocks on the connect timeout
     * instead of on a real download - no network access required, and no dependency on how fast
     * a real server answers.
     */
    private static final String XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  xsi:noNamespaceSchemaLocation="http://10.255.255.1/unreachable.xsd">
                <marker>text must not wait for the schema</marker>
            </root>
            """;

    private EditorHost host;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        stage.setScene(new Scene(new HBox(host), 900, 600));
        stage.show();
    }

    /**
     * A redetect issued while the open-time detection is still running must not be dropped.
     * <p>
     * Both share a per-tab "one detection at a time" guard. Publishing the text early (the test
     * above) makes the window in which a user or panel can trigger a redetect much wider, so a
     * lost race has to wait for the holder rather than return and leave the document unbound.
     */
    @Test
    @DisplayName("a redetect during the open-time detection still binds the schema")
    void redetectDuringInitialDetectionIsNotDropped(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("late.xsd");
        Files.writeString(xsd, """
                <?xml version="1.0"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="root" type="xs:string"/>
                </xs:schema>
                """);
        Path xml = tmp.resolve("data.xml");
        Files.writeString(xml, XML); // unreachable schema location -> slow open-time detection

        WaitForAsyncUtils.waitForAsyncFx(3000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("marker")).orElse(false));

        // The text is here, but the open-time detection is still blocked on the dead address:
        // point the buffer at a resolvable schema and ask for a redetect right now.
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            host.activeEditorView().setText(
                    "<root xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                            + "xsi:noNamespaceSchemaLocation=\"late.xsd\">x</root>");
            host.redetectSchemaForActiveDocument();
            return null;
        });

        WaitForAsyncUtils.waitFor(90, TimeUnit.SECONDS,
                () -> host.activeSchemaProperty().get() != null);
        assertEquals("late.xsd", host.activeSchemaProperty().get().getName(),
                "the redetect must bind the schema even though it lost the detection race");
    }

    /**
     * Closing a tab must stop its schema work. Without that, the detection runs to completion and
     * binds the schema anyway - which goes on to parse the whole XSD for a document nobody can
     * see (a real cost: FundsXML4.xsd is ~47k elements).
     * <p>
     * The schema here is deliberately large and <em>local</em>: detection has to be slow enough to
     * close the tab while it runs, yet still end in a successful binding - otherwise the assertion
     * would hold whether or not anything was abandoned.
     */
    @Test
    @DisplayName("closing a tab mid-detection applies no schema binding")
    void closingATabAbandonsItsSchemaDetection(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("big.xsd");
        writeLargeSchema(xsd, 40_000);
        String doc = """
                <?xml version="1.0" encoding="UTF-8"?>
                <root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:noNamespaceSchemaLocation="big.xsd">
                    <marker>text</marker>
                </root>
                """;

        // Baseline: left open, this document does bind - so the assertion below cannot pass
        // vacuously just because nothing would ever have been bound.
        Path kept = tmp.resolve("kept.xml");
        Files.writeString(kept, doc);
        long start = System.nanoTime();
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> host.openFile(kept));
        WaitForAsyncUtils.waitFor(90, TimeUnit.SECONDS, () -> host.schemaBindingsApplied() == 1);
        long bindMillis = (System.nanoTime() - start) / 1_000_000;

        int boundBefore = host.schemaBindingsApplied();
        Path closedEarly = tmp.resolve("closed.xml");
        Files.writeString(closedEarly, doc);
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> host.openFile(closedEarly));
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("marker")).orElse(false));
        // The text is up while the schema is still being parsed - close the tab now.
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            javafx.scene.control.TabPane tabs = (javafx.scene.control.TabPane) host.lookup(".tab-pane");
            tabs.getTabs().remove(tabs.getSelectionModel().getSelectedItem());
            return null;
        });

        // Absence of an event can only be observed by waiting. Scale that wait off the baseline
        // rather than fixing a number, so the test stays valid on a slow machine without costing
        // a minute of the suite's time on a fast one.
        Thread.sleep(Math.max(5_000, bindMillis * 4));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(boundBefore, host.schemaBindingsApplied(),
                "a tab closed mid-detection must not apply a schema binding");
    }

    /** Writes a schema big enough that loading it takes seconds. */
    private static void writeLargeSchema(Path target, int elementCount) throws Exception {
        StringBuilder sb = new StringBuilder(elementCount * 80);
        sb.append("<?xml version=\"1.0\"?>\n")
                .append("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n")
                .append("  <xs:element name=\"root\" type=\"xs:string\"/>\n");
        for (int i = 0; i < elementCount; i++) {
            sb.append("  <xs:element name=\"e").append(i).append("\" type=\"xs:string\"/>\n");
        }
        sb.append("</xs:schema>\n");
        Files.writeString(target, sb.toString());
    }

    @Test
    @DisplayName("the text appears without waiting for a slow schema detection")
    void textIsPublishedBeforeSchemaDetectionFinishes(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, XML);

        WaitForAsyncUtils.waitForAsyncFx(3000, () -> host.openFile(xml));

        // Deliberately tight: this must be governed by reading a small local file, never by the
        // unreachable schema location above.
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("marker")).orElse(false));
        assertTrue(host.getActiveText().orElse("").contains("text must not wait for the schema"));
    }
}
