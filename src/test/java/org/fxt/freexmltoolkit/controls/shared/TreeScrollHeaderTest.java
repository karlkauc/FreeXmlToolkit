package org.fxt.freexmltoolkit.controls.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonEditorContext;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode;
import org.fxt.freexmltoolkit.controls.jsoneditor.view.JsonTreeView;
import org.fxt.freexmltoolkit.controls.shell.schema.XmlInstanceTreeView;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The tree views show the parents of the topmost visible row in a sticky bar; a click selects one. */
@ExtendWith(ApplicationExtension.class)
class TreeScrollHeaderTest {

    private Stage stage;

    @Start
    void start(Stage stage) {
        this.stage = stage;
    }

    private <T> T fx(java.util.concurrent.Callable<T> action) {
        T result = WaitForAsyncUtils.waitForAsyncFx(5000, action);
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.waitForFxEvents();
        return result;
    }

    private XmlInstanceTreeView xmlTree() {
        StringBuilder xml = new StringBuilder("<root><section><title>t</title><body>");
        for (int i = 0; i < 80; i++) {
            xml.append("<line").append(i).append(">v</line").append(i).append('>');
        }
        xml.append("</body></section></root>");
        return fx(() -> {
            XmlEditorContext context = new XmlEditorContext();
            context.loadDocumentFromString(xml.toString());
            XmlInstanceTreeView tree = new XmlInstanceTreeView();
            tree.setModel(context);
            stage.setScene(new Scene(tree, 500, 300));
            stage.show();
            return tree;
        });
    }

    private static String name(TreeItem<XmlNode> item) {
        return item.getValue() instanceof XmlElement e ? e.getName() : "?";
    }

    private static TreeItem<XmlNode> find(TreeItem<XmlNode> item, String name) {
        if (name.equals(name(item))) {
            return item;
        }
        for (TreeItem<XmlNode> child : item.getChildren()) {
            TreeItem<XmlNode> hit = find(child, name);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    @Test
    void xmlTreeShowsTheParentsOfTheTopRowAndAClickSelectsOne() {
        XmlInstanceTreeView tree = xmlTree();
        TreeScrollHeader<XmlNode> header = TreeScrollHeader.of(tree);
        assertNotNull(header);
        assertTrue(fx(header::chain).isEmpty(), "nothing at the top");
        assertFalse(header.bar().isVisible());

        TreeItem<XmlNode> line40 = find(tree.getRoot(), "line40");
        fx(() -> {
            tree.scrollTo(tree.getRow(line40));
            return null;
        });
        List<String> chain = fx(() -> header.chain().stream().map(TreeScrollHeaderTest::name).toList());
        assertEquals(List.of("root", "section", "body"), chain);
        assertTrue(header.bar().isVisible());

        // A click on "section" selects it and leaves its row below the bar
        Label section = fx(() -> (Label) header.bar().getChildren().stream()
                .filter(n -> n instanceof Label l && "section".equals(l.getText())).findFirst().orElseThrow());
        fx(() -> {
            Event.fireEvent(section, new MouseEvent(MouseEvent.MOUSE_CLICKED, 1, 1, 1, 1, MouseButton.PRIMARY, 1,
                    false, false, false, false, true, false, false, true, false, true, null));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("section", fx(() -> name(tree.getSelectionModel().getSelectedItem())));
        assertNotCovered(tree, header);
    }

    @Test
    void aSelectedRowNeverStaysUnderTheBar() {
        XmlInstanceTreeView tree = xmlTree();
        TreeScrollHeader<XmlNode> header = TreeScrollHeader.of(tree);
        TreeItem<XmlNode> line50 = find(tree.getRoot(), "line50");
        fx(() -> {
            tree.getSelectionModel().select(line50);
            tree.scrollTo(tree.getRow(line50));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(header.bar().isVisible());
        assertNotCovered(tree, header);
    }

    private static void assertNotCovered(XmlInstanceTreeView tree, TreeScrollHeader<XmlNode> header) {
        double[] tops = WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            VirtualFlow<?> flow = (VirtualFlow<?>) tree.lookup(".virtual-flow");
            IndexedCell<?> cell = flow.getVisibleCell(tree.getSelectionModel().getSelectedIndex());
            return new double[] {cell == null ? -1 : cell.getLayoutY(),
                    header.bar().isVisible() ? header.bar().getHeight() : 0};
        });
        assertTrue(tops[0] >= tops[1] - 0.5, "selected row at " + tops[0] + " is below the bar (" + tops[1] + ")");
    }

    @Test
    void jsonTreeNamesKeysAndArrayIndexes() {
        StringBuilder json = new StringBuilder("{\"orders\": [{\"lines\": [");
        for (int i = 0; i < 80; i++) {
            json.append(i > 0 ? "," : "").append(i);
        }
        json.append("]}]}");
        JsonTreeView view = fx(() -> {
            JsonEditorContext context = new JsonEditorContext();
            context.loadDocumentFromString(json.toString());
            JsonTreeView v = new JsonTreeView();
            v.setDocument(context.getDocument());
            stage.setScene(new Scene(v, 500, 300));
            stage.show();
            return v;
        });
        javafx.scene.control.TreeView<JsonNode> tree = fx(() -> {
            @SuppressWarnings("unchecked")
            javafx.scene.control.TreeView<JsonNode> t = (javafx.scene.control.TreeView<JsonNode>) view.lookup(".tree-view");
            return t;
        });
        TreeScrollHeader<JsonNode> header = TreeScrollHeader.of(tree);
        assertNotNull(header);
        fx(() -> {
            expandAll(tree.getRoot());
            tree.scrollTo(tree.getExpandedItemCount() - 10);
            return null;
        });
        List<String> chain = fx(() -> header.bar().getChildren().stream()
                .filter(n -> n.getStyleClass().contains("fxt-tree-scroll-header-segment"))
                .map(n -> ((Label) n).getText()).toList());
        assertEquals(List.of("$", "orders", "[0]", "lines"), chain);
    }

    private static void expandAll(TreeItem<?> item) {
        item.setExpanded(true);
        for (TreeItem<?> child : item.getChildren()) {
            expandAll(child);
        }
    }
}
