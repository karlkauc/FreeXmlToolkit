package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
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
 * TestFX verification that the {@link EditorHost} notices files changed or deleted on disk by
 * another program and applies the user's answer (reload/ignore, keep/close).
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostExternalChangeTest {

    /** Scripted answers in place of the modal dialogs; records what was asked. */
    private static final class FakePrompt implements ExternalChangePrompt {
        volatile ChangedChoice changedAnswer = ChangedChoice.IGNORE;
        volatile DeletedChoice deletedAnswer = DeletedChoice.KEEP;
        final List<String> asked = java.util.Collections.synchronizedList(new ArrayList<>());

        @Override
        public ChangedChoice fileChanged(OpenDocument document, boolean dirty) {
            asked.add("changed:" + document.getDisplayName() + ":" + dirty);
            return changedAnswer;
        }

        @Override
        public DeletedChoice fileDeleted(OpenDocument document, boolean dirty) {
            asked.add("deleted:" + document.getDisplayName() + ":" + dirty);
            return deletedAnswer;
        }
    }

    private EditorHost host;
    private final FakePrompt prompt = new FakePrompt();
    private long mtimeOffset = 10_000;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        host.setExternalChangePrompt(prompt);
        stage.setScene(new Scene(host, 800, 600));
        stage.show();
    }

    @Test
    void reloadReplacesTheContentAndLeavesTheDocumentClean(@TempDir Path tmp) throws Exception {
        Path file = open(tmp, "doc.xml", "<old/>");
        prompt.changedAnswer = ExternalChangePrompt.ChangedChoice.RELOAD;

        writeExternally(file, "<new-from-disk/>");
        checkUntil(() -> "<new-from-disk/>".equals(activeText()));

        assertEquals(List.of("changed:doc.xml:false"), prompt.asked);
        assertFalse(host.getActiveDocument().orElseThrow().isDirty());
        check();
        assertEquals(1, prompt.asked.size(), "a reloaded file must not be reported again");
    }

    @Test
    void reloadDiscardsUnsavedChanges(@TempDir Path tmp) throws Exception {
        Path file = open(tmp, "doc.xml", "<old/>");
        prompt.changedAnswer = ExternalChangePrompt.ChangedChoice.RELOAD;
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeEditorView().setText("<mine/>"));
        assertTrue(host.getActiveDocument().orElseThrow().isDirty());

        writeExternally(file, "<theirs/>");
        checkUntil(() -> "<theirs/>".equals(activeText()));

        assertEquals(List.of("changed:doc.xml:true"), prompt.asked);
        assertFalse(host.getActiveDocument().orElseThrow().isDirty());
    }

    @Test
    void ignoreKeepsTheContentMarksItDirtyAndAsksOnlyOnce(@TempDir Path tmp) throws Exception {
        Path file = open(tmp, "doc.xml", "<old/>");

        writeExternally(file, "<new-from-disk/>");
        checkUntil(() -> !prompt.asked.isEmpty());
        check();

        assertEquals(List.of("changed:doc.xml:false"), prompt.asked);
        assertEquals("<old/>", activeText());
        assertTrue(host.getActiveDocument().orElseThrow().isDirty(),
                "editor and disk differ, so the document has something to save");

        writeExternally(file, "<changed-again/>");
        checkUntil(() -> prompt.asked.size() == 2);
    }

    @Test
    void touchedFileWithIdenticalContentDoesNotPrompt(@TempDir Path tmp) throws Exception {
        Path file = open(tmp, "doc.xml", "<same/>");
        DiskStamp before = host.getActiveDocument().orElseThrow().getDiskStamp();

        writeExternally(file, "<same/>");
        checkUntil(() -> !before.equals(host.getActiveDocument().orElseThrow().getDiskStamp()));

        assertTrue(prompt.asked.isEmpty());
        assertFalse(host.getActiveDocument().orElseThrow().isDirty());
    }

    @Test
    void savingFromTheEditorIsNotAnExternalChange(@TempDir Path tmp) throws Exception {
        Path file = open(tmp, "doc.xml", "<old/>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeEditorView().setText("<saved-by-me/>"));
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.saveActive()));
        assertEquals("<saved-by-me/>", Files.readString(file));

        check();

        assertTrue(prompt.asked.isEmpty());
        assertFalse(host.getActiveDocument().orElseThrow().isDirty());
    }

    @Test
    void keepingADeletedFileLeavesTheTabOpenAndSavingRecreatesIt(@TempDir Path tmp) throws Exception {
        Path file = open(tmp, "doc.xml", "<keep-me/>");

        Files.delete(file);
        checkUntil(() -> !prompt.asked.isEmpty());
        check();

        assertEquals(List.of("deleted:doc.xml:false"), prompt.asked, "a kept file is asked about once");
        assertEquals(1, host.getOpenDocuments().size());
        assertEquals("<keep-me/>", activeText());
        assertTrue(host.getActiveDocument().orElseThrow().isDirty());

        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.saveActive()));
        assertEquals("<keep-me/>", Files.readString(file));
        check();
        assertEquals(1, prompt.asked.size(), "recreating the file by saving is not an external change");
        assertFalse(host.getActiveDocument().orElseThrow().isDirty());
    }

    @Test
    void closingADeletedFileRemovesTabAndDocument(@TempDir Path tmp) throws Exception {
        Path keep = open(tmp, "other.xml", "<other/>");
        Path file = open(tmp, "doc.xml", "<gone/>");
        prompt.deletedAnswer = ExternalChangePrompt.DeletedChoice.CLOSE;

        Files.delete(file);
        checkUntil(() -> host.getOpenDocuments().size() == 1);

        assertEquals(List.of("deleted:doc.xml:false"), prompt.asked);
        assertEquals(keep, host.getOpenDocuments().get(0).getPath());
        assertEquals("<other/>", activeText());
    }

    @Test
    void reloadRebuildsTheStructuredViewOfASchema(@TempDir Path tmp) throws Exception {
        Path file = open(tmp, "schema.xsd", schemaWithType("OldType"));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.setActiveViewMode(ViewMode.TREE));
        assertEquals(List.of("OldType"), namedTypes());
        prompt.changedAnswer = ExternalChangePrompt.ChangedChoice.RELOAD;

        writeExternally(file, schemaWithType("NewType"));
        checkUntil(() -> List.of("NewType").equals(namedTypes()));

        assertEquals(ViewMode.TREE, host.activeViewModeProperty().get(), "the view mode survives a reload");
        assertFalse(host.getActiveDocument().orElseThrow().isDirty());
    }

    // ----- helpers ----------------------------------------------------------

    private static String schemaWithType(String name) {
        return """
                <?xml version="1.0"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:complexType name="%s"><xs:sequence/></xs:complexType>
                </xs:schema>
                """.formatted(name);
    }

    private Path open(Path dir, String name, String content) throws Exception {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(file));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.getActiveDocument().filter(d -> file.equals(d.getPath()))
                        .map(d -> d.getDiskStamp() != null).orElse(false)));
        return file;
    }

    /** Writes like another program would, with a modification time that is certain to differ. */
    private void writeExternally(Path file, String content) throws Exception {
        Files.writeString(file, content);
        mtimeOffset += 10_000;
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + mtimeOffset));
    }

    private String activeText() {
        return WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveText().orElse(null));
    }

    private List<String> namedTypes() {
        return WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.getActiveNamedTypes().stream().map(XsdNode::getName).toList());
    }

    /** Runs one complete external-change check (probe + handling). */
    private void check() throws Exception {
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !host.isExternalChangeCheckRunning());
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.checkExternalChangesNow());
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !host.isExternalChangeCheckRunning());
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Checks repeatedly until {@code condition} holds (the reload itself is asynchronous). */
    private void checkUntil(Callable<Boolean> condition) throws Exception {
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> {
            check();
            return condition.call();
        });
    }
}
