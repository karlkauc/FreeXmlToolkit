package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.ExitChoice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** Quitting with unsaved documents asks once: Save All / Don't Save / Cancel. */
@org.junit.jupiter.api.extension.ExtendWith(ApplicationExtension.class)
class EditorHostExitPromptTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        stage.setScene(new Scene(host, 900, 600));
        stage.show();
    }

    @Test
    void withoutUnsavedChangesTheApplicationClosesWithoutAsking(@TempDir Path tmp) throws Exception {
        Path xml = openClean(tmp);
        assertNotNull(xml);
        boolean mayClose = WaitForAsyncUtils.waitForAsyncFx(5000,
                () -> host.confirmExit(docs -> fail("no prompt expected for clean documents")));
        assertTrue(mayClose);
    }

    @Test
    void cancelKeepsTheApplicationOpenAndTheChanges(@TempDir Path tmp) throws Exception {
        Path xml = openClean(tmp);
        makeDirty();
        AtomicReference<List<OpenDocument>> asked = new AtomicReference<>();
        boolean mayClose = WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.confirmExit(docs -> {
            asked.set(docs);
            return ExitChoice.CANCEL;
        }));
        assertFalse(mayClose, "Cancel vetoes the close");
        assertEquals(1, asked.get().size(), "the prompt lists the unsaved documents");
        assertEquals(xml, asked.get().get(0).getPath());
        assertTrue(host.getActiveDocument().orElseThrow().isDirty(), "nothing was saved or discarded");
        assertFalse(Files.readString(xml).contains("<!-- changed -->"), "the file is untouched");
    }

    @Test
    void dontSaveClosesWithoutWriting(@TempDir Path tmp) throws Exception {
        Path xml = openClean(tmp);
        makeDirty();
        boolean mayClose = WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.confirmExit(docs -> ExitChoice.DISCARD));
        assertTrue(mayClose);
        assertFalse(Files.readString(xml).contains("<!-- changed -->"), "Don't Save leaves the file as it was");
    }

    @Test
    void saveAllWritesEveryUnsavedDocumentAndCloses(@TempDir Path tmp) throws Exception {
        Path xml = openClean(tmp);
        makeDirty();
        boolean mayClose = WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.confirmExit(docs -> ExitChoice.SAVE_ALL));
        assertTrue(mayClose);
        assertTrue(Files.readString(xml).contains("<!-- changed -->"), "Save All wrote the change to disk");
        assertFalse(host.getActiveDocument().orElseThrow().isDirty());
    }

    /** Opens a small XML file and waits until it is loaded and clean. */
    private Path openClean(Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root>\n  <item/>\n</root>\n");
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.getActiveText().map(t -> t.contains("<item/>")).orElse(false)));
        WaitForAsyncUtils.waitForFxEvents();
        return xml;
    }

    /** Types into the active editor and waits for the dirty flag. */
    private void makeDirty() throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.insertTextAtCaret("<!-- changed -->"));
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.getActiveDocument().map(OpenDocument::isDirty).orElse(false)));
    }
}
