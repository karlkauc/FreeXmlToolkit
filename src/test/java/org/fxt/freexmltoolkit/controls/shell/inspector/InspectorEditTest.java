package org.fxt.freexmltoolkit.controls.shell.inspector;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Spinner;
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
 * The inspector edits XSD node properties in place (Figma sections stay), committing
 * through the EditorHost command stack and round-tripping to the text.
 */
@ExtendWith(ApplicationExtension.class)
class InspectorEditTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="child" type="xs:string" minOccurs="1" maxOccurs="1"/>
                  </xs:sequence>
                  <xs:attribute name="id" type="xs:string" use="required"/>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    private EditorHost host;
    private InspectorPanel inspector;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        inspector = new InspectorPanel(host);
        stage.setScene(new Scene(new HBox(host, inspector), 1100, 700));
        stage.show();
    }

    private void selectInTree(String name, Path xsd) throws Exception {
        Files.writeString(xsd, XSD);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("complexType")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });
        XsdNode node = WaitForAsyncUtils.waitForAsyncFx(2000, () -> find(host.getActiveSchemaRoot().orElseThrow(), name));
        assertNotNull(node, "fixture node: " + name);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.selectNodeInActiveTree(node);
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> name.equals(inspector.getNodeNameText()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void editingMaxOccursRoundTripsToText(@TempDir Path tmp) throws Exception {
        selectInTree("child", tmp.resolve("schema.xsd"));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            Spinner<Integer> max = (Spinner<Integer>) inspector.lookup("#inspector-max");
            max.getValueFactory().setValue(5);
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS,
                () -> host.getActiveText().orElse("").contains("maxOccurs=\"5\""));
        assertTrue(host.getActiveText().orElse("").contains("maxOccurs=\"5\""),
                "editing maxOccurs in the inspector must round-trip to the text");
    }

    @Test
    void useFieldIsAttributeOnly(@TempDir Path tmp) throws Exception {
        selectInTree("child", tmp.resolve("schema.xsd")); // element
        WaitForAsyncUtils.waitForFxEvents();
        ComboBox<?> useForElement = (ComboBox<?>) inspector.lookup("#inspector-use");
        assertFalse(useForElement.isVisible(), "Use must be hidden for elements");

        XsdNode attr = WaitForAsyncUtils.waitForAsyncFx(2000, () -> find(host.getActiveSchemaRoot().orElseThrow(), "id"));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.selectNodeInActiveTree(attr);
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> "id".equals(inspector.getNodeNameText()));
        assertTrue(((ComboBox<?>) inspector.lookup("#inspector-use")).isVisible(),
                "Use must be visible for attributes");
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
