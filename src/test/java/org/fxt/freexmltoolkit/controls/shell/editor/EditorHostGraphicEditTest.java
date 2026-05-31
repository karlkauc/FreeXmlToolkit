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
 * End-to-end verification that structured editing works in the Graphic view too
 * (matrix #13/#14): selecting a node in Graphic mode and editing it goes through
 * the command stack and round-trips to the text.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostGraphicEditTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="ControlData">
                <xs:complexType><xs:sequence>
                  <xs:element name="UniqueDocumentID" type="xs:string"/>
                  <xs:element name="DocumentGenerated" type="xs:dateTime"/>
                </xs:sequence></xs:complexType>
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
    void deleteViaGraphicSelectionUpdatesText(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("DocumentGenerated")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.GRAPHIC);
            return null;
        });

        XsdNode target = WaitForAsyncUtils.waitForAsyncFx(2000, () ->
                find(host.getActiveSchemaRoot().orElseThrow(), "DocumentGenerated"));
        assertNotNull(target);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.selectNodeInActiveTree(target); // selects the card in Graphic mode
            return null;
        });

        boolean deleted = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.deleteActiveNode());

        assertTrue(deleted, "delete should succeed from a Graphic-mode selection");
        assertFalse(host.getActiveText().orElse("").contains("DocumentGenerated"),
                "deleted node must be gone from the round-tripped text");
        assertTrue(host.getActiveText().orElse("").contains("UniqueDocumentID"), "sibling must remain");
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
