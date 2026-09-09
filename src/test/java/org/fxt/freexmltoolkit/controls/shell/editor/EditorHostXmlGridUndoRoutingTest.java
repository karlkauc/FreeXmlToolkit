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
 * {@link EditorHost#undoActive()} in the XML Grid view must go through the shared XML
 * model's command stack (not the code area's text undo), so the model history is kept.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostXmlGridUndoRoutingTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        stage.setScene(new Scene(host, 900, 600));
        stage.show();
    }

    @Test
    void undoActiveUsesTheXmlModelStackInGraphicMode(@TempDir Path tmp) throws Exception {
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
        XmlGridView grid = WaitForAsyncUtils.waitForAsyncFx(2000, () -> (XmlGridView) host.lookupAll("*").stream()
                .filter(n -> n instanceof XmlGridView).findFirst().orElseThrow());
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            XmlElement root = grid.getContext().getDocument().getRootElement();
            grid.getContext().getSelectionModel().setSelectedNode(root);
            return null;
        });
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.setActiveXmlAttribute("b", "2")));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> host.getActiveText().orElse("").contains("b=\"2\""));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.undoActive();
            return null;
        });

        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> !host.getActiveText().orElse("x").contains("b=\"2\""));
        assertTrue(grid.getContext().canRedo(), "undo went through the XML command stack, so redo is available");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.redoActive();
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> host.getActiveText().orElse("").contains("b=\"2\""));
    }
}
