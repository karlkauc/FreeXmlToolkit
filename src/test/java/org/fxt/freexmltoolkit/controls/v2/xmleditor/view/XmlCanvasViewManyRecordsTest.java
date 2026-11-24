package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Switching a large repeating group between table and numbered rows keys every row by its
 * positional XPath; that must not cost a sibling scan per row (telemetry: a 139 s UI stall for a
 * 20 MB document in "Display as Table").
 */
@ExtendWith(ApplicationExtension.class)
class XmlCanvasViewManyRecordsTest {

    private static final int RECORDS = 50_000;

    private Stage stage;
    private XmlCanvasView view;

    @Start
    void start(Stage stage) {
        this.stage = stage;
    }

    @Test
    void switchingALargeGroupToNumberedRowsAndBackIsQuick() {
        StringBuilder xml = new StringBuilder("<Root>");
        for (int i = 1; i <= RECORDS; i++) {
            xml.append("<Rec><A>").append(i).append("</A></Rec>");
        }
        String doc = xml.append("</Root>").toString();
        WaitForAsyncUtils.waitForAsyncFx(60_000, () -> {
            RepeatingElementsTable.clearAllCaches();
            XmlEditorContext context = new XmlEditorContext();
            context.loadDocumentFromString(doc);
            view = new XmlCanvasView(context);
            stage.setScene(new Scene(view, 1200, 700));
            stage.show();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        long ms = WaitForAsyncUtils.waitForAsyncFx(120_000, () -> {
            FlatRow owner = view.visibleRowList().stream()
                    .filter(r -> "Rec".equals(r.getLabel()) && r.hasRepeatingTable()).findFirst().orElseThrow();
            long start = System.nanoTime();
            view.toggleTableDisplay(owner); // numbered rows: one row per record
            FlatRow member = view.visibleRowList().stream()
                    .filter(r -> "Rec".equals(r.getLabel())).findFirst().orElseThrow();
            view.toggleTableDisplay(member); // back to the table
            return (System.nanoTime() - start) / 1_000_000;
        });
        assertTrue(ms < 10_000, "switching took " + ms + " ms");
    }
}
