package org.fxt.freexmltoolkit.controls.shell.schema;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies the XSD Graphic view preserves expand/collapse state across a
 * re-render (matrix #50), the same way the Tree view does.
 */
@ExtendWith(ApplicationExtension.class)
class XsdGraphicViewStateTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root">
                <xs:complexType><xs:sequence>
                  <xs:element name="a" type="xs:string"/>
                  <xs:element name="b" type="xs:string"/>
                </xs:sequence></xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    private XsdGraphicView view;

    @Start
    void start(Stage stage) {
        view = new XsdGraphicView(n -> {
        });
        stage.setScene(new Scene(view, 600, 600));
        stage.show();
    }

    @Test
    void preservesCollapsedStateAcrossReRender() throws Exception {
        XsdSchema schema = (XsdSchema) new XsdNodeFactory().fromString(XSD);

        XsdNode container = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.setSchema(schema);
            XsdNode c = firstContainerWithChildren(schema);
            assertNotNull(c, "fixture must have a container node");
            assertTrue(view.isNodeExpanded(c), "container should start expanded");
            view.setNodeExpanded(c, false);
            return c;
        });

        Boolean expandedAfter = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.setSchema(schema);
            return view.isNodeExpanded(container);
        });

        assertFalse(expandedAfter, "collapsed card must remain collapsed after re-render (matrix #50)");
    }

    private XsdNode firstContainerWithChildren(XsdNode node) {
        for (XsdNode child : node.getChildren()) {
            if (!child.getChildren().isEmpty()) {
                return child;
            }
            XsdNode deeper = firstContainerWithChildren(child);
            if (deeper != null) {
                return deeper;
            }
        }
        return null;
    }
}
