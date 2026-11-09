package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** Query hits in the grid: whole table rows for members, cells for nodes inside them, rows otherwise. */
@ExtendWith(ApplicationExtension.class)
class GridQueryHighlightsTest {

    private static final String XML = """
            <order>
              <customer>Ada</customer>
              <item><name>A</name><price>1</price></item>
              <item><name>B</name><price>2</price></item>
              <item><name>C</name><price>3</price></item>
            </order>
            """;

    private XmlCanvasView view;
    private XmlEditorContext context;

    @Start
    void start(Stage stage) throws Exception {
        context = new XmlEditorContext(
                new org.fxt.freexmltoolkit.controls.v2.xmleditor.serialization.StreamingXmlParser().parse(XML));
        view = new XmlCanvasView(context);
        stage.setScene(new Scene(view, 900, 500));
        stage.show();
    }

    private void highlight(List<GridCanvasView.Target<XmlNode>> targets) {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> view.setQueryHighlights(targets));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private RepeatingElementsTable itemTable() {
        return view.visibleRowList().stream().filter(FlatRow::hasRepeatingTable).findFirst().orElseThrow()
                .getRepeatingTable();
    }

    @Test
    void membersCellsAndRows() {
        XmlElement root = context.getDocument().getRootElement();
        var items = root.getChildElements("item");
        highlight(List.of(new GridCanvasView.Target<>(items.get(0), null),
                new GridCanvasView.Target<>(items.get(2).getChildElements("price").getFirst(), null),
                new GridCanvasView.Target<>(root.getChildElements("customer").getFirst(), null)));
        List<int[]> cells = view.queryHitsOf(itemTable());
        assertEquals(2, cells.size());
        assertArrayEquals(new int[]{0, -1}, cells.get(0), "a member: its whole table row");
        assertEquals(2, cells.get(1)[0]);
        assertEquals("price", itemTable().getColumn(cells.get(1)[1]).getName(), "a node inside: its cell");
        FlatRow customer = view.visibleRowList().stream().filter(r -> "customer".equals(r.getLabel())).findFirst().orElseThrow();
        assertTrue(view.isQueryHit(customer), "a plain element: its row");

        highlight(List.of());
        assertTrue(view.queryHitsOf(itemTable()).isEmpty());
        assertFalse(view.isQueryHit(customer));
    }
}
