package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * For a large document, edits on the shared XML model are not serialized into the text after
 * every change (seconds per edit on a 170-MB file): the text follows when it is read, when the
 * Text view is shown and when the file is saved.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostXmlDeferredRoundTripTest {

    private EditorHost host;
    private final int defaultThreshold = EditorTab.deferredXmlRoundTripChars;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        stage.setScene(new Scene(host, 900, 600));
        stage.show();
    }

    @AfterEach
    void restoreThreshold() {
        EditorTab.deferredXmlRoundTripChars = defaultThreshold;
    }

    @Test
    void largeDocumentTextFollowsOnReadViewSwitchAndSave(@TempDir Path tmp) throws Exception {
        EditorTab.deferredXmlRoundTripChars = 1; // every document counts as large
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root a=\"1\"/>\n");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("root")).orElse(false));
        showGrid();

        editRoot("b", "2");
        String raw = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveCodeArea().getText());
        assertFalse(raw.contains("b=\"2\""), "not written into the text right after the edit");
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveDocument().orElseThrow().isDirty()));

        // a reader gets the edited text
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveText().orElse("")).contains("b=\"2\""));

        // the Text view shows it
        editRoot("c", "3");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TEXT);
            return null;
        });
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveCodeArea().getText()).contains("c=\"3\""));

        // saving writes it
        showGrid();
        editRoot("d", "4");
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.saveActive()).get(5, TimeUnit.SECONDS));
        String saved = Files.readString(xml);
        assertTrue(saved.contains("b=\"2\"") && saved.contains("c=\"3\"") && saved.contains("d=\"4\""), saved);
    }

    @Test
    void smallDocumentTextFollowsEveryEditAtOnce(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root a=\"1\"/>\n");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("root")).orElse(false));
        showGrid();

        editRoot("b", "2");
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveCodeArea().getText()).contains("b=\"2\""));
    }

    private void showGrid() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.GRAPHIC);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Selects the root in the visible grid and sets an attribute, once the grid is ready. */
    private void editRoot(String name, String value) throws Exception {
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            XmlGridView grid = (XmlGridView) host.lookupAll("*").stream()
                    .filter(n -> n instanceof XmlGridView && n.isVisible()).findFirst().orElse(null);
            if (grid == null || grid.getContext() == null) {
                return false;
            }
            XmlElement root = grid.getContext().getDocument().getRootElement();
            // a view switch drops the tab's selection while the grid keeps its own: select anew
            grid.getContext().getSelectionModel().setSelectedNode(null);
            grid.getContext().getSelectionModel().setSelectedNode(root);
            return host.setActiveXmlAttribute(name, value);
        }));
    }
}
