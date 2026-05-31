package org.fxt.freexmltoolkit.controls.shell.schema;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicReference;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * TestFX verification of {@link XsdGraphicView}: renders a parsed XSD, exposes a
 * grid for repeating elements, and reports node selection via the callback.
 */
@ExtendWith(ApplicationExtension.class)
class XsdGraphicViewTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="Funds">
                <xs:complexType><xs:sequence>
                  <xs:element name="Fund" maxOccurs="unbounded">
                    <xs:complexType><xs:sequence>
                      <xs:element name="Name" type="xs:string"/>
                    </xs:sequence></xs:complexType>
                  </xs:element>
                </xs:sequence></xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    private final AtomicReference<XsdNode> selected = new AtomicReference<>();
    private XsdGraphicView view;

    @Start
    void start(Stage stage) {
        view = new XsdGraphicView(selected::set);
        stage.setScene(new Scene(view, 700, 500));
        stage.show();
    }

    @Test
    void rendersAndReportsSelection() {
        boolean ok = WaitForAsyncUtils.waitForAsyncFx(2000, () -> view.setXsdFromText(XSD));
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(ok, "valid XSD should render");
        assertNotNull(view.getSchemaRoot());

        XsdNode funds = WaitForAsyncUtils.waitForAsyncFx(2000, () -> find(view.getSchemaRoot(), "Funds"));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.selectNode(funds);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertSame(funds, selected.get(), "selecting a node must report it via the callback");
    }

    @Test
    void embedsGridForRepeatingElement() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> view.setXsdFromText(XSD));
        WaitForAsyncUtils.waitForFxEvents();
        // The repeating 'Fund' renders an embedded grid; its field column 'Name' appears.
        boolean hasGrid = !view.lookupAll(".fxt-grid").isEmpty();
        boolean hasGridCell = view.lookupAll(".fxt-grid-cell").stream()
                .anyMatch(n -> n instanceof javafx.scene.control.Label l && l.getText().contains("Name"));
        assertTrue(hasGrid, "repeating element should render an embedded grid");
        assertTrue(hasGridCell, "grid should contain the field column 'Name'");
    }

    private XsdNode find(XsdNode node, String name) {
        if (name.equals(node.getName())) {
            return node;
        }
        for (XsdNode child : node.getChildren()) {
            XsdNode found = find(child, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
