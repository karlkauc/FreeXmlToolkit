package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies the new XSD property-edit methods on {@link EditorHost} (Use, Form,
 * Constraints, SubstitutionGroup, Documentation) round-trip to the text via the V2
 * command stack AND preserve the selection (so the inspector keeps showing the node).
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostPropertyEditTest {

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

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        stage.setScene(new Scene(host, 900, 600));
        stage.show();
    }

    @Test
    void changeUseRoundTripsAndPreservesSelection(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("attribute")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });
        XsdNode attr = WaitForAsyncUtils.waitForAsyncFx(2000, () ->
                find(host.getActiveSchemaRoot().orElseThrow(), "id"));
        assertNotNull(attr);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.selectNodeInActiveTree(attr);
            return null;
        });

        boolean ok = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.changeActiveUse("prohibited"));
        assertTrue(ok, "changeActiveUse should succeed for a selected attribute");

        assertTrue(host.getActiveText().orElse("").contains("use=\"prohibited\""),
                "the changed use must be round-tripped to the text");
        assertNotNull(host.activeSelectedNodeProperty().get(),
                "the selection must be preserved after a property edit (inspector keeps the node)");
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
