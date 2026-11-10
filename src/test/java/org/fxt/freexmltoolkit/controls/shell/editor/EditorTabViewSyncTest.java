package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Switching between Text, Tree and Graphic continues where the user was: the node at the caret
 * is selected in the structured view (the way to it opened), the node of a structured view gets
 * the caret in the Text view and its lines tinted ({@code linked-node-line}).
 */
@ExtendWith(ApplicationExtension.class)
class EditorTabViewSyncTest {

    private static final String XML = """
            <?xml version="1.0"?>
            <order>
              <customer>Ann</customer>
              <items>
                <item id="1">
                  <name>Pen</name>
                </item>
                <item id="2">
                  <name>Ink</name>
                  <price>3</price>
                </item>
              </items>
            </order>
            """;

    private static final String XSD = """
            <?xml version="1.0"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:annotation>
                <xs:documentation>Orders</xs:documentation>
              </xs:annotation>
              <xs:element name="order">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="customer" type="xs:string"/>
                    <xs:element name="total">
                      <xs:annotation><xs:documentation>Sum</xs:documentation></xs:annotation>
                      <xs:simpleType>
                        <xs:restriction base="xs:decimal"/>
                      </xs:simpleType>
                    </xs:element>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    private static final String JSON = """
            {
              "name": "Alice",
              "items": [
                {"id": 1, "n": "a"},
                {
                  "id": 2,
                  "n": "b"
                }
              ]
            }
            """;

    private EditorHost host;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        stage.setScene(new Scene(host, 1000, 700));
        stage.show();
    }

    private EditorTab open(Path tmp, String name, String content) throws Exception {
        Path file = tmp.resolve(name);
        Files.writeString(file, content);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(file));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> !t.isEmpty()).orElse(false));
        return WaitForAsyncUtils.waitForAsyncFx(2000, () -> (EditorTab) host.lookupAll(".tab-pane").stream()
                .map(n -> ((TabPane) n).getSelectionModel().getSelectedItem())
                .filter(t -> t instanceof EditorTab).findFirst().orElseThrow());
    }

    private void switchTo(ViewMode mode) {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(mode);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static void caretOnLineOf(EditorTab tab, String needle) {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            var area = tab.view.getCodeArea();
            area.moveTo(area.getText().indexOf(needle) + 1);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static int lineOf(String text, String needle) {
        int offset = text.indexOf(needle);
        return (int) text.substring(0, offset).chars().filter(c -> c == '\n').count() + 1;
    }

    private static void waitFor(java.util.concurrent.Callable<Boolean> condition) throws Exception {
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> WaitForAsyncUtils.waitForAsyncFx(2000, condition));
    }

    @Test
    void xmlCaretNodeIsSelectedInTreeAndGridAndTintedBackInText(@TempDir Path tmp) throws Exception {
        EditorTab tab = open(tmp, "order.xml", XML);
        caretOnLineOf(tab, "<price>");

        switchTo(ViewMode.TREE);
        waitFor(() -> tab.xmlInstanceTreeView != null
                && tab.xmlInstanceTreeView.getSelectionModel().getSelectedItem() != null
                && tab.xmlInstanceTreeView.getSelectionModel().getSelectedItem().getValue() instanceof XmlElement e
                && "price".equals(e.getName()));
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            var item = tab.xmlInstanceTreeView.getSelectionModel().getSelectedItem();
            return item.getParent().isExpanded() && item.getParent().getParent().isExpanded();
        }), "the way to the node is expanded");

        switchTo(ViewMode.GRAPHIC);
        waitFor(() -> tab.currentXmlSelection instanceof XmlElement e && "price".equals(e.getName()));
        // price is a cell of the item table: the grid selects that row's item (id 2) and the cell
        var gridSelection = WaitForAsyncUtils.waitForAsyncFx(2000, () -> tab.xmlGridView.getCanvasView().getSelectedNode());
        assertTrue(gridSelection == tab.currentXmlSelection || gridSelection == tab.currentXmlSelection.getParent(),
                "the grid selects the node or its table row, was " + gridSelection);

        // a different node chosen in the grid is what the Text view shows
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            var root = tab.xmlEditorContext.getDocument().getRootElement();
            var item1 = root.getChildElements().get(1).getChildElements().get(0);
            tab.xmlGridView.getCanvasView().revealProblem(item1, null);
            tab.currentXmlSelection = item1;
            return null;
        });

        switchTo(ViewMode.TEXT);
        String text = host.getActiveText().orElseThrow();
        int first = lineOf(text, "<item id=\"1\">");
        assertEquals(first, WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> tab.view.getCodeArea().getCurrentParagraph() + 1), "caret on the node's start tag");
        assertArrayEquals(new int[] {first, first + 2},
                WaitForAsyncUtils.waitForAsyncFx(2000, () -> tab.view.getLinkedNodeLines()),
                "the node's lines are tinted");
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> tab.view.getCodeArea()
                .getParagraph(first - 1).getParagraphStyle().contains("linked-node-line")));

        // the tint goes when the caret leaves the node
        caretOnLineOf(tab, "<customer>");
        waitFor(() -> tab.view.getLinkedNodeLines() == null);
    }

    @Test
    void xsdCaretNodeIsSelectedInTreeGraphicAndOverviewAndFoundBackInText(@TempDir Path tmp) throws Exception {
        EditorTab tab = open(tmp, "order.xsd", XSD);
        caretOnLineOf(tab, "name=\"total\"");

        switchTo(ViewMode.TREE);
        waitFor(() -> tab.currentSelection != null && "total".equals(tab.currentSelection.getName()));
        assertEquals("total", WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> tab.treeView.getSelectionModel().getSelectedItem().getValue().getName()));

        switchTo(ViewMode.GRAPHIC);
        waitFor(() -> tab.currentSelection != null && "total".equals(tab.currentSelection.getName()));

        switchTo(ViewMode.OVERVIEW);
        waitFor(() -> tab.currentSelection != null && "order".equals(tab.currentSelection.getName()));

        switchTo(ViewMode.TREE);
        XsdNode customer = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            var order = tab.editorContext.getSchema().getChildren().stream()
                    .filter(n -> "order".equals(n.getName())).findFirst().orElseThrow();
            XsdNode found = find(order, "customer");
            tab.treeView.revealNode(found);
            return found;
        });
        waitFor(() -> tab.currentSelection == customer);

        switchTo(ViewMode.TEXT);
        String text = host.getActiveText().orElseThrow();
        int line = lineOf(text, "name=\"customer\"");
        assertEquals(line, WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> tab.view.getCodeArea().getCurrentParagraph() + 1), "annotations do not shift the path");
        assertArrayEquals(new int[] {line, line},
                WaitForAsyncUtils.waitForAsyncFx(2000, () -> tab.view.getLinkedNodeLines()));
    }

    private static XsdNode find(XsdNode node, String name) {
        if (name.equals(node.getName())) {
            return node;
        }
        for (XsdNode child : node.getChildren()) {
            XsdNode found = find(child, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Test
    void jsonCaretNodeIsSelectedInTreeAndGridAndTintedBackInText(@TempDir Path tmp) throws Exception {
        EditorTab tab = open(tmp, "data.json", JSON);
        caretOnLineOf(tab, "\"n\": \"b\"");

        switchTo(ViewMode.TREE);
        waitFor(() -> tab.currentJsonSelection != null && "n".equals(tab.currentJsonSelection.getKey())
                && tab.currentJsonSelection.getParent().getParent().indexOf(tab.currentJsonSelection.getParent()) == 1);
        assertSame(tab.currentJsonSelection,
                WaitForAsyncUtils.waitForAsyncFx(2000, () -> tab.jsonTreeView.getSelectedNode()));

        switchTo(ViewMode.GRAPHIC);
        waitFor(() -> tab.currentJsonSelection != null && "n".equals(tab.currentJsonSelection.getKey()));

        // the second array item, chosen in the grid, comes back in the text with all its lines
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            tab.currentJsonSelection = tab.currentJsonSelection.getParent();
            return null;
        });
        switchTo(ViewMode.TEXT);
        String text = host.getActiveText().orElseThrow();
        int first = lineOf(text, "    {\n      \"id\": 2");
        assertEquals(first, WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> tab.view.getCodeArea().getCurrentParagraph() + 1));
        assertArrayEquals(new int[] {first, first + 3},
                WaitForAsyncUtils.waitForAsyncFx(2000, () -> tab.view.getLinkedNodeLines()));
    }
}
