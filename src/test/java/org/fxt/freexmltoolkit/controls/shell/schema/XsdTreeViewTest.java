package org.fxt.freexmltoolkit.controls.shell.schema;

import javafx.scene.Scene;
import javafx.stage.Stage;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TestFX verification of {@link XsdTreeView}: valid XSD renders a tree rooted at
 * the schema; invalid XSD clears the tree without throwing.
 */
@ExtendWith(ApplicationExtension.class)
class XsdTreeViewTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root">
                <xs:complexType><xs:sequence>
                  <xs:element name="item" type="xs:string"/>
                </xs:sequence></xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    private XsdTreeView tree;

    @Start
    void start(Stage stage) {
        tree = new XsdTreeView();
        stage.setScene(new Scene(tree, 400, 500));
        stage.show();
    }

    @Test
    void rendersValidXsdRootedAtSchema() {
        boolean ok = WaitForAsyncUtils.waitForAsyncFx(2000, () -> tree.setXsdFromText(XSD));
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(ok, "valid XSD should parse");
        assertNotNull(tree.getRoot());
        assertInstanceOf(XsdSchema.class, tree.getRoot().getValue());
    }

    @Test
    void invalidXsdClearsTreeGracefully() {
        boolean ok = WaitForAsyncUtils.waitForAsyncFx(2000, () -> tree.setXsdFromText("<not-a-schema"));
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(ok, "invalid XSD should report failure");
        assertNull(tree.getRoot());
    }
}
