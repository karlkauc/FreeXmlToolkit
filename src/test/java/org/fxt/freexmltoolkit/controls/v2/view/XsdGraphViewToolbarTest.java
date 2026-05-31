package org.fxt.freexmltoolkit.controls.v2.view;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Objects;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * P5: the Canvas XSD editor toolbar is a clean, graph-only control strip — no
 * dev-ish labels — with one obvious place for zoom/fit.
 */
@ExtendWith(ApplicationExtension.class)
class XsdGraphViewToolbarTest {

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
        stage.setScene(new Scene(graph, 800, 500));
        stage.show();
    }

    @Test
    void toolbarHasNoDevLabelButKeepsZoomAndFit() throws Exception {
        WaitForAsyncUtils.waitForFxEvents();

        boolean hasDevLabel = graph.lookupAll(".label").stream()
                .filter(n -> n instanceof Label)
                .map(n -> ((Label) n).getText())
                .filter(Objects::nonNull)
                .anyMatch(t -> t.contains("XSD Editor V2") || t.contains("Graphical View"));
        assertFalse(hasDevLabel, "toolbar must not show a dev-ish 'XSD Editor V2 - Graphical View' label");

        boolean hasFit = hasButton("Fit to View");
        boolean hasZoomIn = hasButton("+");
        boolean hasExpand = hasButton("Expand All");
        assertTrue(hasFit && hasZoomIn && hasExpand,
                "the graph toolbar must keep its zoom/fit/expand controls");
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
