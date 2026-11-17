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

    @Test
    void deleteInTheTreeRemovesTheNodeCompletely(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("pos.xml");
        Files.writeString(xml, "<Position><Id>1</Id><TotalPercentage>5.5</TotalPercentage><Name>A</Name></Position>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("Position")).orElse(false));
        showTree();

        selectAndDelete(".fxt-xml-tree",
                (org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode n) ->
                        n instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement e
                                && "TotalPercentage".equals(e.getName()));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> !t.contains("TotalPercentage")).orElse(false));
        String text = host.getActiveText().orElseThrow();
        assertTrue(text.contains("<Id>1</Id>") && text.contains("<Name>A</Name>"), text);

        var tree = (org.fxt.freexmltoolkit.controls.shell.schema.XmlInstanceTreeView) host.lookup(".fxt-xml-tree");
        var selected = WaitForAsyncUtils.waitForAsyncFx(2000, () -> tree.getSelectionModel().getSelectedItem().getValue());
        assertEquals("Name", ((org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement) selected).getName(),
                "the next sibling is selected");
        assertNull(WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> find(tree.getRoot(), (org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode n) ->
                        n instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement e
                                && "TotalPercentage".equals(e.getName()))), "gone from the tree");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.undoXml());
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("<TotalPercentage>5.5</TotalPercentage>")).orElse(false));
        assertNotNull(WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> find(tree.getRoot(), (org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode n) ->
                        n instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement e
                                && "TotalPercentage".equals(e.getName()))), "undo brings it back into the tree");
    }

    /** Selects the first tree item whose value matches and presses Delete on the tree. */
    @SuppressWarnings("unchecked")
    private <T> void selectAndDelete(String selector, java.util.function.Predicate<T> match) {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            javafx.scene.control.TreeView<T> tree = (javafx.scene.control.TreeView<T>) host.lookup(selector);
            javafx.scene.control.TreeItem<T> item = find(tree.getRoot(), match);
            assertNotNull(item, "tree item to delete");
            tree.getSelectionModel().select(item);
            tree.requestFocus();
            javafx.event.Event.fireEvent(tree, new javafx.scene.input.KeyEvent(
                    javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", javafx.scene.input.KeyCode.DELETE,
                    false, false, false, false));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static <T> javafx.scene.control.TreeItem<T> find(javafx.scene.control.TreeItem<T> item,
                                                             java.util.function.Predicate<T> match) {
        if (item == null) {
            return null;
        }
        if (item.getValue() != null && match.test(item.getValue())) {
            return item;
        }
        for (javafx.scene.control.TreeItem<T> child : item.getChildren()) {
            javafx.scene.control.TreeItem<T> hit = find(child, match);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private void showTree() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }
}
