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
