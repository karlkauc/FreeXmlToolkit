package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The Output tab: principal and secondary results, loaded documents. */
@ExtendWith(ApplicationExtension.class)
class OutputSoFarViewTest {

    @Start
    void start(Stage stage) {
    }

    @Test
    void switchesBetweenResultsAndListsDocuments() {
        Map<String, String> parts = new LinkedHashMap<>();
        parts.put("file:/tmp/out/part1.xml", "<part>9</part>");
        String[] r = WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            OutputSoFarView view = new OutputSoFarView();
            java.util.List<String> opened = new java.util.ArrayList<>();
            view.setOnOpenDocument(opened::add);
            view.showSoFar("<main>", parts, List.of("file:/tmp/prices.xml", "collection(data/)"));
            String principal = view.getText();
            view.selectResult("file:/tmp/out/part1.xml");
            String part = view.getText();
            String info = view.getInfo();
            view.showFinal("<main/>", parts, List.of("file:/tmp/prices.xml"));
            String keptSelection = view.getText();
            return new String[]{principal, part, info, keptSelection, String.join("|", view.documentItems())};
        });
        assertEquals("<main>", r[0]);
        assertEquals("<part>9</part>", r[1], "the secondary result");
        assertTrue(r[2].startsWith("part1.xml: Written so far"), r[2]);
        assertEquals("<part>9</part>", r[3], "the chosen result stays selected when the run finishes");
        assertEquals("file:/tmp/prices.xml", r[4]);
    }
}
