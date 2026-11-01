package org.fxt.freexmltoolkit.controls.v2.view;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The bar for unloaded schemas shows in both chromes of the diagram and can be dismissed. */
@ExtendWith(ApplicationExtension.class)
class UnresolvedReferencesBarTest {

    private static final String BROKEN = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:include schemaLocation="gone.xsd"/>
              <xs:element name="root" type="xs:string"/>
            </xs:schema>
            """;
    private static final String HEALTHY = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root" type="xs:string"/>
            </xs:schema>
            """;

    private StackPane host;

    @Start
    void start(Stage stage) {
        host = new StackPane();
        stage.setScene(new Scene(host, 900, 600));
        stage.show();
    }

    private XsdGraphView show(String xsd, boolean minimal) throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(xsd);
        XsdGraphView graph = WaitForAsyncUtils.asyncFx(() -> {
            XsdGraphView view = new XsdGraphView(schema);
            if (minimal) {
                view.useMinimalChrome();
            }
            host.getChildren().setAll(view);
            return view;
        }).get();
        WaitForAsyncUtils.waitForFxEvents();
        return graph;
    }

    private static boolean barShown(XsdGraphView graph) throws Exception {
        return WaitForAsyncUtils.asyncFx(() -> {
            Node bar = graph.lookup("#unresolved-references");
            return bar != null && bar.isVisible();
        }).get();
    }

    @Test
    void shownWithTheToolbar() throws Exception {
        XsdGraphView graph = show(BROKEN, false);

        assertTrue(barShown(graph));
        String text = WaitForAsyncUtils.asyncFx(
                () -> ((Label) graph.lookup("#unresolved-references-message")).getText()).get();
        assertTrue(text.contains("gone.xsd"), text);
    }

    @Test
    void shownInMinimalChrome() throws Exception {
        assertTrue(barShown(show(BROKEN, true)));
    }

    @Test
    void absentWhenEverythingLoaded() throws Exception {
        assertFalse(barShown(show(HEALTHY, false)));
        assertFalse(barShown(show(HEALTHY, true)));
    }

    @Test
    void dismissHidesIt() throws Exception {
        XsdGraphView graph = show(BROKEN, true);

        WaitForAsyncUtils.asyncFx(() -> {
            ((Button) graph.lookup("#unresolved-references-dismiss")).fire();
            return null;
        }).get();
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(barShown(graph));
    }

    @Test
    void selectingAPlaceholderLeavesTheModelAlone() throws Exception {
        XsdGraphView graph = show("""
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="root" type="Missing"/>
                </xs:schema>
                """, false);

        XsdNodeRenderer.VisualNode placeholder = WaitForAsyncUtils.asyncFx(() -> {
            XsdNodeRenderer.VisualNode card = graph.getRootNode().getChildren().getFirst();
            graph.getSelectionModel().select(card);
            return card;
        }).get();
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("Unresolved type: Missing", placeholder.getLabel());
        assertNull(placeholder.getModelNode());
    }
}
