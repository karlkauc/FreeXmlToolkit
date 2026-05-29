package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TestFX verification of the {@link EditorHost}: opening loads content
 * asynchronously, re-opening reuses the tab, and Save As writes the file and
 * retitles the document (XML text-view parity, UI rebuild Phase 3).
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        stage.setScene(new Scene(host, 800, 600));
        stage.show();
    }

    @Test
    void openingAFileLoadsItsContent(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("doc.xml");
        Files.writeString(file, "<root><child/></root>");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(file));
        WaitForAsyncUtils.waitFor(3, java.util.concurrent.TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("<child/>")).orElse(false));

        assertEquals(EditorFileType.XML, host.getActiveDocument().orElseThrow().getFileType());
        assertFalse(host.getActiveDocument().orElseThrow().isDirty(),
                "a freshly loaded document must not be dirty");
        assertEquals(1, host.getOpenDocuments().size());
    }

    @Test
    void openingTheSameFileTwiceReusesTheTab(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("doc.xml");
        Files.writeString(file, "<a/>");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(file));
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(file));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(1, host.getOpenDocuments().size(), "re-opening must reuse the existing tab");
    }

    @Test
    void autoBindsLocalSchemaFromNoNamespaceSchemaLocation(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, """
                <?xml version="1.0"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="root">
                    <xs:complexType><xs:sequence>
                      <xs:element name="item" type="xs:string"/>
                    </xs:sequence></xs:complexType>
                  </xs:element>
                </xs:schema>
                """);
        Path xml = tmp.resolve("data.xml");
        Files.writeString(xml, "<root xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + "xsi:noNamespaceSchemaLocation=\"schema.xsd\"><item>x</item></root>");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(4, java.util.concurrent.TimeUnit.SECONDS,
                () -> host.activeSchemaProperty().get() != null);

        assertEquals(xsd.toFile().getName(), host.activeSchemaProperty().get().getName());
    }

    @Test
    void saveAllPersistsDirtyTitledDocuments(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        Path f1 = tmp.resolve("a.xml");
        Files.writeString(f1, "<a/>");
        Path f2 = tmp.resolve("b.xml");
        Files.writeString(f2, "<b/>");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(f1));
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(f2));
        WaitForAsyncUtils.waitFor(3, java.util.concurrent.TimeUnit.SECONDS,
                () -> host.getOpenDocuments().size() == 2);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.getOpenDocuments().forEach(d -> d.setDirty(true));
            return null;
        });
        int saved = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.saveAll());

        assertEquals(2, saved);
        assertTrue(host.getOpenDocuments().stream().noneMatch(OpenDocument::isDirty));
    }

    @Test
    void formatActiveReformatsXmlToMultipleLines(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("c.xml");
        Files.writeString(file, "<a><b>x</b></a>");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(file));
        WaitForAsyncUtils.waitFor(3, java.util.concurrent.TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("<b>")).orElse(false));

        boolean ok = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.formatActive());

        assertTrue(ok, "formatActive should reformat valid XML");
        assertTrue(host.getActiveText().orElse("").lines().count() > 1,
                "formatted XML should span multiple lines");
    }

    @Test
    void saveAsWritesFileAndRetitlesDocument(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        Path target = tmp.resolve("new.xsd");

        OpenDocument doc = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.newDocument(EditorFileType.XML));
        assertTrue(doc.isUntitled());

        boolean ok = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.saveActiveAs(target));
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(ok, "saveActiveAs should succeed");
        assertTrue(Files.exists(target), "Save As must write the file");
        assertFalse(doc.isUntitled(), "document must become titled");
        assertEquals(EditorFileType.XSD, doc.getFileType(), "type follows the new extension");
        assertFalse(doc.isDirty());
    }
}
