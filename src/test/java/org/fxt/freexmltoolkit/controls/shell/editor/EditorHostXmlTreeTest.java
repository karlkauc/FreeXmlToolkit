package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies an XML instance document gains a Tree view (the DOM tree) — not just
 * XSD/JSON. Graphic stays XSD-only.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostXmlTreeTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        stage.setScene(new Scene(host, 1000, 700));
        stage.show();
    }

    @Test
    void xmlDocumentSupportsTreeAndGraphic(@TempDir Path tmp) throws Exception {
        openXml(tmp);
        assertTrue(host.activeSupportsView(ViewMode.TREE), "XML must support a Tree view");
        assertTrue(host.activeSupportsView(ViewMode.GRAPHIC),
                "XML must support Graphic (the instance grid lives there)");
    }

    @Test
    void switchingXmlToTreeRendersTheDomTree(@TempDir Path tmp) throws Exception {
        openXml(tmp);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(ViewMode.TREE, host.activeViewModeProperty().get(),
                "XML Tree mode must stick (not fall back to Text)");
        assertNotNull(host.lookup(".fxt-xml-tree"), "the XML DOM tree must be rendered");
    }

    @Test
    void writesSnapshotWhenRequested(@TempDir Path tmp) throws Exception {
        if (!"true".equals(System.getenv("FXT_SHELL_SNAPSHOT"))) {
            return;
        }
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<order id=\"1\" status=\"open\">\n  <customer>ACME</customer>\n"
                + "  <items>\n    <item sku=\"A\">Widget</item>\n    <item sku=\"B\">Gadget</item>\n"
                + "  </items>\n</order>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("order")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });
        WaitForAsyncUtils.sleep(300, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
        javafx.scene.image.WritableImage img = WaitForAsyncUtils.waitForAsyncFx(3000,
                () -> host.snapshot(new javafx.scene.SnapshotParameters(), null));
        javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(img, null), "png",
                new java.io.File(System.getProperty("java.io.tmpdir"), "fxt_xml_tree.png"));
    }

    private void openXml(Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<order id=\"1\"><item>a</item><item>b</item></order>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("order")).orElse(false));
    }
}
