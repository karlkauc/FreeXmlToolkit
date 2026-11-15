package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * A validation result stays with its document: switching to another tab (also the Validation result tab
 * that Validate / F8 opens) and back shows it again while the text is unchanged, and a run that finishes
 * after a tab switch lands on the document it read.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostValidationResultTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        stage.setScene(new Scene(host, 900, 600));
        stage.show();
    }

    private <T> T fx(java.util.concurrent.Callable<T> action) {
        return WaitForAsyncUtils.waitForAsyncFx(3000, action);
    }

    private OpenDocument open(Path file, String marker) throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> host.openFile(file));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> fx(() -> host.getActiveText().map(t -> t.contains(marker)).orElse(false)));
        return fx(() -> host.getActiveDocument().orElseThrow());
    }

    private EditorHost.ValidationState state() {
        return fx(() -> host.validationStatusProperty().get().state());
    }

    @Test
    void validateResultIsShownAgainWhenComingBackToTheDocument(@TempDir Path tmp) throws Exception {
        Path broken = Files.writeString(tmp.resolve("broken.xml"), "<root><a></root>");
        OpenDocument doc = open(broken, "<a>");
        fx(() -> {
            new EditorActions(host).validateActive(); // opens the Validation result tab
            return null;
        });
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
                () -> fx(() -> host.getActiveDocument().isEmpty())); // the result tab is in front
        assertEquals(EditorHost.ValidationState.NOT_VALIDATED, state(), "a tool tab has no validation result");

        fx(() -> {
            host.selectDocument(doc);
            return null;
        });
        assertEquals(EditorHost.ValidationState.INVALID, state(), "the document's result comes back");
        assertTrue(fx(() -> host.validationStatusProperty().get().problemCount()) > 0);
    }

    @Test
    void anEditedDocumentDropsItsOldResult(@TempDir Path tmp) throws Exception {
        OpenDocument a = open(Files.writeString(tmp.resolve("a.xml"), "<a/>"), "<a/>");
        fx(() -> {
            host.setValidationStatus(EditorHost.ValidationState.VALID, 0, "Valid");
            return null;
        });
        OpenDocument b = open(Files.writeString(tmp.resolve("b.xml"), "<b/>"), "<b/>");
        assertEquals(EditorHost.ValidationState.NOT_VALIDATED, state(), "b has no result of its own");
        fx(() -> {
            host.selectDocument(a);
            return null;
        });
        assertEquals(EditorHost.ValidationState.VALID, state());

        fx(() -> {
            host.activeEditorView().setText("<a><changed/></a>");
            host.selectDocument(b);
            host.selectDocument(a);
            return null;
        });
        assertEquals(EditorHost.ValidationState.NOT_VALIDATED, state(), "the result was for the old text");
    }

    @Test
    void aRunFinishingAfterATabSwitchLandsOnItsDocument(@TempDir Path tmp) throws Exception {
        OpenDocument a = open(Files.writeString(tmp.resolve("a.xml"), "<a/>"), "<a/>");
        var target = fx(() -> host.validationTarget());
        OpenDocument b = open(Files.writeString(tmp.resolve("b.xml"), "<b/>"), "<b/>");
        var problem = new ValidationProblem("XSD", "error", 1, "broken");
        fx(() -> {
            host.publishValidation(target, EditorHost.ValidationState.INVALID, 1, "1 problem", List.of(problem));
            return null;
        });
        assertEquals(EditorHost.ValidationState.NOT_VALIDATED, state(), "b is untouched");
        assertTrue(fx(() -> host.getActiveProblems().isEmpty()));
        fx(() -> {
            host.selectDocument(a);
            return null;
        });
        assertEquals(EditorHost.ValidationState.INVALID, state());
        assertEquals(List.of(problem), fx(() -> List.copyOf(host.getActiveProblems())), "its problems come back too");
        assertNotNull(b);
    }
}
