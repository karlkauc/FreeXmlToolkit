package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.editor.commands.DeleteNodeCommand;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.view.XsdGraphView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Covers the Canvas XSD editor improvement plan items P1 (debounced/coalesced
 * round-trip of graphical edits) and P2 (the editor context — and its undo
 * history — is shared across Tree/Graphic and survives a detour through Text).
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostGraphicContextTest {

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

    /** P2: undo of a Tree edit still works after switching Tree → Text → Graphic. */
    @Test
    void sharedContextUndoSurvivesPassingThroughText(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("DocumentGenerated")).orElse(false));

        // Edit in the Tree view: select and delete a node (goes through the command stack).
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });
        XsdNode target = WaitForAsyncUtils.waitForAsyncFx(2000, () ->
                find(host.getActiveSchemaRoot().orElseThrow(), "DocumentGenerated"));
        assertNotNull(target);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.selectNodeInActiveTree(target);
            return null;
        });
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.deleteActiveNode()));
        assertFalse(host.getActiveText().orElse("").contains("DocumentGenerated"));

        // Detour through Text, then into Graphic.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TEXT);
            return null;
        });
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.GRAPHIC);
            return null;
        });

        // Undo must revert the Tree edit — proving the same context/history is in use.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.undoActive();
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("DocumentGenerated")).orElse(false));
        assertTrue(host.getActiveText().orElse("").contains("DocumentGenerated"),
                "undo after Tree→Text→Graphic must restore the node (shared context)");
    }

    /** P1: an internal graphical edit round-trips to text asynchronously (debounced), not synchronously. */
    @Test
    void internalGraphicEditRoundTripsDebounced(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("DocumentGenerated")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.GRAPHIC);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        XsdGraphView graph = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.lookupAll("*").stream()
                .filter(n -> n instanceof XsdGraphView).map(n -> (XsdGraphView) n).findFirst().orElseThrow());

        // Simulate an internal graphical edit by executing a command on the shared
        // context's command stack (as drag/right-click do), then read the text in the
        // same FX pulse: it must still be stale because the round-trip is debounced.
        String immediate = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            XsdNode target = find(graph.getEditorContext().getSchema(), "DocumentGenerated");
            graph.getEditorContext().getCommandManager().executeCommand(new DeleteNodeCommand(target));
            return host.getActiveText().orElse("");
        });
        assertTrue(immediate.contains("DocumentGenerated"),
                "round-trip of a graphical edit must be debounced (asynchronous), not synchronous");

        // After the debounce window, the edit is round-tripped to the text.
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> !host.getActiveText().orElse("").contains("DocumentGenerated"));
        assertFalse(host.getActiveText().orElse("").contains("DocumentGenerated"),
                "the debounced round-trip must eventually update the text");
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
