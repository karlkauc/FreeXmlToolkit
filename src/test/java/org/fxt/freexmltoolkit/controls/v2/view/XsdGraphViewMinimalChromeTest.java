package org.fxt.freexmltoolkit.controls.v2.view;

import static org.junit.jupiter.api.Assertions.*;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The embedded Canvas XSD editor in shell (minimal) chrome, after Figma 07.6b: the bulky top
 * action toolbar is gone, a title chip names the node in focus at the top-left, and zooming
 * lives in a small control at the bottom-right.
 */
@ExtendWith(ApplicationExtension.class)
class XsdGraphViewMinimalChromeTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="Root">
                <xs:complexType><xs:sequence>
                  <xs:element name="Child" type="xs:string"/>
                </xs:sequence></xs:complexType>
              </xs:element>
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

    @BeforeEach
    void minimalChrome() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            graph.useMinimalChrome();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void minimalChromeDropsTopToolbarAndKeepsTheZoomControl() {
        assertNull(graph.getTop(), "minimal chrome removes the top action toolbar");
        assertFalse(hasButton("Expand All"), "no bulky Expand All button in minimal chrome");
        assertFalse(hasButton("Fit to View"), "no Fit to View button in minimal chrome");
        assertNotNull(lookup("#graph-zoom-in"));
        assertNotNull(lookup("#graph-zoom-out"));
        assertEquals(Pos.BOTTOM_RIGHT, StackPane.getAlignment(lookup("#graph-zoom-control")));
    }

    @Test
    void theZoomButtonsChangeTheShownPercentage() {
        Label percent = (Label) lookup("#graph-zoom-label");
        assertEquals("100%", percent.getText());

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> ((Button) lookup("#graph-zoom-in")).fire());
        assertNotEquals("100%", percent.getText());

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> percent.getOnMouseClicked().handle(null));
        assertEquals("100%", percent.getText(), "a click on the percentage resets");
    }

    @Test
    void theTitleChipNamesTheRootWithKindAndContent() {
        XsdGraphTitleChip chip = graph.getTitleChip();
        assertNotNull(chip);
        assertEquals(Pos.TOP_LEFT, StackPane.getAlignment(chip));
        assertEquals("Root", chip.nameText());
        assertTrue(chip.metaText().startsWith("element"), chip.metaText());
        assertTrue(chip.metaText().endsWith("sequence"), chip.metaText());
    }

    private Node lookup(String selector) {
        return WaitForAsyncUtils.waitForAsyncFx(2000, () -> graph.lookup(selector));
    }

    private boolean hasButton(String text) {
        // lookupAll must run on the FX thread (scene graph may still be mutating)
        return WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            for (Node n : graph.lookupAll(".button")) {
                if (n instanceof Button b && text.equals(b.getText())) {
                    return true;
                }
            }
            return false;
        });
    }
}
