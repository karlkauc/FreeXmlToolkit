package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TestFX verification that the Schema panel generates an XSD from the active XML
 * and opens it as a new document.
 */
@ExtendWith(ApplicationExtension.class)
class SchemaGenerateTest {

    private EditorHost host;
    private TypeLibraryPanel panel;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        panel = new TypeLibraryPanel(host);
        stage.setScene(new Scene(new HBox(host, panel), 1000, 600));
        stage.show();
    }

    @Test
    void generatesXsdAsNewDocument(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("data.xml");
        Files.writeString(xml, "<root><name>x</name><age>30</age></root>");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("root")).orElse(false));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.generateXsdFromActive();
            return null;
        });
        // Wait for the generated XSD content to land in a new active document.
        WaitForAsyncUtils.waitFor(6, TimeUnit.SECONDS, () ->
                host.getActiveDocument().map(d -> d.getFileType() == EditorFileType.XSD).orElse(false)
                        && host.getActiveText().map(t -> t.contains("schema")).orElse(false));

        assertEquals(EditorFileType.XSD, host.getActiveDocument().orElseThrow().getFileType());
        assertTrue(host.getActiveText().orElse("").contains("schema"),
                "generated document should be an XSD");
        assertEquals(2, host.getOpenDocuments().size(), "original XML + generated XSD are open");
    }
}
