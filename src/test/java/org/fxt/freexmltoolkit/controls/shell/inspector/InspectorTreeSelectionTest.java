package org.fxt.freexmltoolkit.controls.shell.inspector;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.ViewMode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * End-to-end TestFX verification that selecting a node in the XSD Tree view
 * drives the inspector (real model: kind/name/xpath/type/cardinality).
 */
@ExtendWith(ApplicationExtension.class)
class InspectorTreeSelectionTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="FundsXML4">
                <xs:complexType><xs:sequence>
                  <xs:element name="ControlData">
                    <xs:complexType><xs:sequence>
                      <xs:element name="UniqueDocumentID" type="xs:string"/>
                    </xs:sequence></xs:complexType>
                  </xs:element>
                </xs:sequence></xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    private EditorHost host;
    private InspectorPanel inspector;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        inspector = new InspectorPanel(host);
        stage.setScene(new Scene(new HBox(host, inspector), 1100, 600));
        stage.show();
    }

    @Test
    void selectingTreeNodeFillsInspectorFromModel(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("FundsXML4")).orElse(false));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });

        XsdNode target = WaitForAsyncUtils.waitForAsyncFx(2000, () ->
                find(host.getActiveSchemaRoot().orElseThrow(), "UniqueDocumentID"));
        assertNotNull(target, "test setup: node must exist in the tree's schema");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.selectNodeInActiveTree(target);
            return null;
        });

        // wait for the name AND the type, read on the FX thread: the inspector fills its fields one
        // after the other, so the name alone can show while the type field is still empty
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> "UniqueDocumentID".equals(inspector.getNodeNameText())
                        && "xs:string".equals(inspector.getTypeText())));
        String xpath = WaitForAsyncUtils.waitForAsyncFx(2000, inspector::getXPathText);
        // The inspector shows the XSD schema XPath (mirrors XsdNode.getXPath()).
        assertTrue(xpath.startsWith("/xs:schema/"), xpath);
        assertTrue(xpath.endsWith("xs:element[@name='UniqueDocumentID']"), xpath);
        assertEquals("xs:string", WaitForAsyncUtils.waitForAsyncFx(2000, inspector::getTypeText));
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
