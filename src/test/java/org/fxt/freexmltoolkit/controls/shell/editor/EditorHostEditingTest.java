package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.scene.Scene;
import javafx.stage.Stage;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end TestFX verification of structured editing: deleting a node in the
 * Tree view goes through the command stack, mutates the model, round-trips to
 * the text editor (marking it dirty), and is reversible via undo.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostEditingTest {

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
    void deleteThroughCommandStackMutatesModelAndUndoRestores(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("DocumentGenerated")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });

        // Select the node to delete, then delete it via the command stack.
        XsdNode target = WaitForAsyncUtils.waitForAsyncFx(2000, () ->
                find(host.getActiveSchemaRoot().orElseThrow(), "DocumentGenerated"));
        assertNotNull(target);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.selectNodeInActiveTree(target);
            return null;
        });
        boolean deleted = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.deleteActiveNode());

        assertTrue(deleted, "delete should succeed");
        assertFalse(host.getActiveText().orElse("").contains("DocumentGenerated"),
                "deleted node must be gone from the round-tripped text");
        assertTrue(host.getActiveText().orElse("").contains("UniqueDocumentID"),
                "sibling must remain");
        assertTrue(host.getActiveDocument().orElseThrow().isDirty(), "editing marks the document dirty");

        // Undo restores it.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.undoActive();
            return null;
        });
        assertTrue(host.getActiveText().orElse("").contains("DocumentGenerated"),
                "undo must restore the deleted node");
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
