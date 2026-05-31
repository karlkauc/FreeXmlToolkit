package org.fxt.freexmltoolkit.controls.v2.view;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Aligns the embedded Canvas XSD editor with the Figma "Redesign · Unified —
 * Schema (graphical)" frame: in shell (minimal) chrome the bulky top action
 * toolbar is gone and zooming lives in a small bottom pill, leaving a clean
 * diagram under the shared editor toolbar.
 */
@ExtendWith(ApplicationExtension.class)
class XsdGraphViewMinimalChromeTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="Root" type="xs:string"/>
            </xs:schema>
            """;

    private XsdGraphView graph;

    @Start
    void start(Stage stage) throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(XSD);
        graph = new XsdGraphView(schema);
        stage.setScene(new Scene(graph, 900, 600));
        stage.show();
    }

    @Test
    void minimalChromeDropsTopToolbarAndKeepsAZoomPill() throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            graph.useMinimalChrome();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertNull(graph.getTop(), "minimal chrome removes the top action toolbar");
        assertFalse(hasButton("Expand All"), "no bulky Expand All button in minimal chrome");
        assertFalse(hasButton("Fit to View"), "no Fit to View button in minimal chrome");
        assertTrue(hasButton("+") && hasButton("-"),
                "a small zoom pill (- and +) remains, matching the Figma bottom control");
    }

    private boolean hasButton(String text) {
        for (Node n : graph.lookupAll(".button")) {
            if (n instanceof Button b && text.equals(b.getText())) {
                return true;
            }
        }
        return false;
    }
}
