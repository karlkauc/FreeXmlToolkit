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

    @Test
    void statisticsOpensReport(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">"
                + "<xs:element name=\"root\" type=\"xs:string\"/></xs:schema>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("xs:element")).orElse(false));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.statisticsActive();
            return null;
        });
        WaitForAsyncUtils.waitFor(6, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("Schema Statistics")).orElse(false));
        assertTrue(host.getActiveText().orElse("").contains("Elements:"));
    }

    @Test
    void flattenOpensXsd(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">"
                + "<xs:complexType name=\"T\"><xs:sequence>"
                + "<xs:element name=\"a\" type=\"xs:string\"/></xs:sequence></xs:complexType>"
                + "<xs:element name=\"root\" type=\"T\"/></xs:schema>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("complexType")).orElse(false));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.flattenActive();
            return null;
        });
        WaitForAsyncUtils.waitFor(6, TimeUnit.SECONDS, () ->
                host.getOpenDocuments().size() == 2
                        && host.getActiveText().map(t -> t.contains("schema")).orElse(false));
        assertEquals(EditorFileType.XSD, host.getActiveDocument().orElseThrow().getFileType());
    }
}
