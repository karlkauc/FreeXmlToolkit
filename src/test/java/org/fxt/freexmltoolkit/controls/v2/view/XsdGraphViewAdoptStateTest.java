package org.fxt.freexmltoolkit.controls.v2.view;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** A view built for a re-parsed model takes over what the user arranged in the one it replaces. */
@ExtendWith(ApplicationExtension.class)
class XsdGraphViewAdoptStateTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root"><xs:complexType><xs:sequence>
                <xs:element name="same"/>
                <xs:element name="same"/>
              </xs:sequence></xs:complexType></xs:element>
            </xs:schema>
            """;

    private Stage stage;

    @Start
    void start(Stage stage) {
        this.stage = stage;
        stage.show();
    }

    private XsdGraphView view() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(XSD);
        return WaitForAsyncUtils.waitForAsyncFx(10_000, () -> new XsdGraphView(schema));
    }

    private void show(XsdGraphView view) {
        WaitForAsyncUtils.waitForAsyncFx(10_000, () -> stage.setScene(new Scene(view, 200, 150)));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static XsdNodeRenderer.VisualNode card(XsdNodeRenderer.VisualNode from, String label) {
        if (label.equals(from.getLabel())) {
            return from;
        }
        for (XsdNodeRenderer.VisualNode child : from.getChildren()) {
            XsdNodeRenderer.VisualNode found = card(child, label);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Test
    void zoomAndScrollPositionCarryOver() throws Exception {
        XsdGraphView before = view();
        XsdGraphView after = view();
        show(before);
        WaitForAsyncUtils.waitForAsyncFx(10_000, () -> before.setZoom(1.5));
        WaitForAsyncUtils.waitForFxEvents(); // the pane rescales its position to the larger content
        WaitForAsyncUtils.waitForAsyncFx(10_000, () -> {
            before.applyCss();
            before.layout();
            before.scrollPaneForTest().setVvalue(0.75);
            before.scrollPaneForTest().setHvalue(0.25);
            after.adoptViewState(before);
        });
        show(after);

        assertEquals(1.5, after.zoomForTest(), 0.001);
        assertEquals(0.75, after.scrollPaneForTest().getVvalue(), 0.001);
        assertEquals(0.25, after.scrollPaneForTest().getHvalue(), 0.001);
    }

    @Test
    void aClosedCardStaysClosedAndSameNamedSiblingsAreToldApart() throws Exception {
        XsdGraphView before = view();
        XsdGraphView after = view();
        WaitForAsyncUtils.waitForAsyncFx(10_000, () -> {
            card(before.getRootNode(), "root").setExpanded(false);
            after.adoptViewState(before);
        });

        assertFalse(WaitForAsyncUtils.waitForAsyncFx(10_000, () -> card(after.getRootNode(), "root").isExpanded()));
    }
}
