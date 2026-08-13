package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Region;

import org.fxt.freexmltoolkit.service.DragDropService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies {@link FileDropSupport}: extension/regular-file matching, the valid/invalid
 * style-class feedback during drag-over, and the installed handlers' accept/consume/drop
 * semantics (driven with mocked {@link DragEvent}s — TestFX cannot simulate OS file drags).
 */
class FileDropSupportTest {

    // ---- firstMatch -----------------------------------------------------------------

    @Test
    void firstMatchFindsMatchingExtensionCaseInsensitively(@TempDir Path tmp) throws Exception {
        File upper = write(tmp, "STYLES.XSL");
        assertEquals(Optional.of(upper),
                FileDropSupport.firstMatch(List.of(upper), DragDropService.XSLT_EXTENSIONS));
    }

    @Test
    void firstMatchRejectsWrongExtensionAndDirectories(@TempDir Path tmp) throws Exception {
        File wrong = write(tmp, "notes.txt");
        File dirNamedLikeXslt = Files.createDirectory(tmp.resolve("fake.xsl")).toFile();
        assertEquals(Optional.empty(),
                FileDropSupport.firstMatch(List.of(wrong, dirNamedLikeXslt), DragDropService.XSLT_EXTENSIONS));
    }

    @Test
    void firstMatchReturnsFirstMatchingFileOfAMixedSelection(@TempDir Path tmp) throws Exception {
        File txt = write(tmp, "readme.txt");
        File sch = write(tmp, "rules.sch");
        File schematron = write(tmp, "more.schematron");
        assertEquals(Optional.of(sch),
                FileDropSupport.firstMatch(List.of(txt, sch, schematron), DragDropService.SCHEMATRON_EXTENSIONS));
    }

    // ---- drag-over feedback ---------------------------------------------------------

    @Test
    void feedbackTogglesBetweenValidAndInvalidWithoutDuplicates(@TempDir Path tmp) throws Exception {
        Region target = new Region();
        File xsl = write(tmp, "a.xsl");
        File txt = write(tmp, "a.txt");

        assertFalse(FileDropSupport.applyDragOverFeedback(target, List.of(txt), DragDropService.XSLT_EXTENSIONS));
        assertTrue(target.getStyleClass().contains(FileDropSupport.DROP_INVALID_CLASS));

        // continuous DRAG_OVER pulses must not stack classes
        FileDropSupport.applyDragOverFeedback(target, List.of(txt), DragDropService.XSLT_EXTENSIONS);
        assertEquals(1, target.getStyleClass().stream()
                .filter(FileDropSupport.DROP_INVALID_CLASS::equals).count());

        assertTrue(FileDropSupport.applyDragOverFeedback(target, List.of(xsl), DragDropService.XSLT_EXTENSIONS));
        assertTrue(target.getStyleClass().contains(FileDropSupport.DROP_VALID_CLASS));
        assertFalse(target.getStyleClass().contains(FileDropSupport.DROP_INVALID_CLASS));

        FileDropSupport.clearFeedback(target);
        assertFalse(target.getStyleClass().contains(FileDropSupport.DROP_VALID_CLASS));
        assertFalse(target.getStyleClass().contains(FileDropSupport.DROP_INVALID_CLASS));
    }

    // ---- installed handler semantics ------------------------------------------------

    @Test
    void dragOverWithLoadableFileAcceptsCopyAndConsumes(@TempDir Path tmp) throws Exception {
        Region target = new Region();
        FileDropSupport.install(target, DragDropService.XSLT_EXTENSIONS, f -> {
        });

        DragEvent event = dragEventWithFiles(write(tmp, "sheet.xslt"));
        target.getOnDragOver().handle(event);

        verify(event).acceptTransferModes(TransferMode.COPY);
        verify(event).consume();
        assertTrue(target.getStyleClass().contains(FileDropSupport.DROP_VALID_CLASS));
    }

    @Test
    void dragOverWithWrongExtensionRejectsButStillConsumes(@TempDir Path tmp) throws Exception {
        Region target = new Region();
        FileDropSupport.install(target, DragDropService.SCHEMATRON_EXTENSIONS, f -> {
        });

        DragEvent event = dragEventWithFiles(write(tmp, "wrong.xml"));
        target.getOnDragOver().handle(event);

        verify(event, never()).acceptTransferModes(TransferMode.COPY);
        verify(event).consume(); // consumed so the shell-wide open-file handler stays out
        assertTrue(target.getStyleClass().contains(FileDropSupport.DROP_INVALID_CLASS));
    }

    @Test
    void dragOverWithoutFilesPassesThroughUnconsumed() {
        Region target = new Region();
        FileDropSupport.install(target, DragDropService.XSLT_EXTENSIONS, f -> {
        });

        Dragboard dragboard = mock(Dragboard.class);
        when(dragboard.hasFiles()).thenReturn(false);
        DragEvent event = mock(DragEvent.class);
        when(event.getDragboard()).thenReturn(dragboard);

        target.getOnDragOver().handle(event);

        verify(event, never()).consume();
        assertFalse(target.getStyleClass().contains(FileDropSupport.DROP_VALID_CLASS));
        assertFalse(target.getStyleClass().contains(FileDropSupport.DROP_INVALID_CLASS));
    }

    @Test
    void droppingALoadableFileInvokesTheCallbackAndClearsFeedback(@TempDir Path tmp) throws Exception {
        Region target = new Region();
        AtomicReference<File> dropped = new AtomicReference<>();
        FileDropSupport.install(target, DragDropService.SCHEMATRON_EXTENSIONS, dropped::set);
        target.getStyleClass().add(FileDropSupport.DROP_VALID_CLASS); // as left by drag-over

        File sch = write(tmp, "rules.sch");
        DragEvent event = dragEventWithFiles(write(tmp, "skip.txt"), sch);
        target.getOnDragDropped().handle(event);

        assertEquals(sch, dropped.get(), "first matching file must be loaded");
        verify(event).setDropCompleted(true);
        verify(event).consume();
        assertFalse(target.getStyleClass().contains(FileDropSupport.DROP_VALID_CLASS));
    }

    @Test
    void droppingOnlyNonLoadableFilesCompletesUnsuccessfully(@TempDir Path tmp) throws Exception {
        Region target = new Region();
        AtomicReference<File> dropped = new AtomicReference<>();
        FileDropSupport.install(target, DragDropService.XSLT_EXTENSIONS, dropped::set);

        DragEvent event = dragEventWithFiles(write(tmp, "wrong.txt"));
        target.getOnDragDropped().handle(event);

        assertEquals(null, dropped.get());
        verify(event).setDropCompleted(false);
        verify(event).consume();
    }

    @Test
    void dragExitedClearsFeedback() {
        Region target = new Region();
        FileDropSupport.install(target, DragDropService.XSLT_EXTENSIONS, f -> {
        });
        target.getStyleClass().add(FileDropSupport.DROP_INVALID_CLASS);

        target.getOnDragExited().handle(mock(DragEvent.class));

        assertFalse(target.getStyleClass().contains(FileDropSupport.DROP_INVALID_CLASS));
    }

    // ---- helpers --------------------------------------------------------------------

    private static File write(Path dir, String name) throws Exception {
        Path file = dir.resolve(name);
        Files.writeString(file, "x");
        return file.toFile();
    }

    private static DragEvent dragEventWithFiles(File... files) {
        Dragboard dragboard = mock(Dragboard.class);
        when(dragboard.hasFiles()).thenReturn(true);
        when(dragboard.getFiles()).thenReturn(List.of(files));
        DragEvent event = mock(DragEvent.class);
        when(event.getDragboard()).thenReturn(dragboard);
        return event;
    }
}
