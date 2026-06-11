package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Phase A: the XML {@code EditorTab} holds ONE shared {@link org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext}
 * across the Grid/Tree/Text views (mirroring the XSD {@code editorContext} pattern). An edit and its
 * undo history therefore survive a Grid&rarr;Text&rarr;Grid view switch, and edits round-trip to the text.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostXmlSharedContextTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        stage.setScene(new Scene(host, 900, 600));
        stage.show();
    }

    @Test
    void editAndUndoSurviveViewSwitch(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root a=\"1\"/>\n");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("root")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.GRAPHIC);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        XmlGridView grid = (XmlGridView) host.lookupAll("*").stream()
                .filter(n -> n instanceof XmlGridView).findFirst().orElseThrow();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            XmlElement root = grid.getContext().getDocument().getRootElement();
            grid.getContext().getSelectionModel().setSelectedNode(root);
            return null;
        });

        // Edit on the shared context: add an attribute; it round-trips to the text.
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.setActiveXmlAttribute("b", "2")));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> host.getActiveText().orElse("").contains("b=\"2\""));

        // Switch away to Text and back to Grid — the shared context (and its undo stack) must persist.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TEXT);
            return null;
        });
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.GRAPHIC);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        // Undo the attribute add via the shared context; the edit is reverted in the text.
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.undoXml()),
                "undo must operate on the shared context that survived the view switch");
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> !host.getActiveText().orElse("x").contains("b=\"2\""));
        assertTrue(host.getActiveText().orElse("").contains("a=\"1\""), "original attribute must remain after undo");
    }
}
