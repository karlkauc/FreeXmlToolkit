package org.fxt.freexmltoolkit.controls.v2.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.util.NoSuchElementException;

import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.v2.editor.services.MutableXmlSchemaProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Two RichTextFX failures reported by telemetry in 2.5.0: a mouse press that Flowless cannot hit-test
 * ({@code NoSuchElementException} from {@code CellListManager.getPresentCell}) and an NPE from
 * re-styling the current line in the middle of an undo/redo.
 */
@ExtendWith(ApplicationExtension.class)
class XmlCodeEditorV2RobustnessTest {

    private XmlCodeEditorV2 editor;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        editor = new XmlCodeEditorV2(new MutableXmlSchemaProvider());
        stage.setScene(new Scene(editor, 600, 400));
        stage.show();
    }

    private static MouseEvent press() {
        return new MouseEvent(MouseEvent.MOUSE_PRESSED, 5, 5, 5, 5, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, false, null);
    }

    private static NoSuchElementException failureFrom(String className) {
        NoSuchElementException e = new NoSuchElementException();
        e.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("java.util.Optional", "get", null, -1),
                new StackTraceElement(className, "getPresentCell", null, 66)});
        return e;
    }

    @Test
    void flowlessHitRaceOnAMousePressIsSwallowed() {
        CodeArea area = editor.getCodeArea();
        var handler = (javafx.event.EventHandler<MouseEvent>) e -> {
            throw failureFrom("org.fxmisc.flowless.CellListManager");
        };
        Throwable thrown = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            area.addEventHandler(MouseEvent.MOUSE_PRESSED, handler);
            try {
                Event.fireEvent(area, press());
                return null;
            } catch (Throwable t) {
                return t;
            } finally {
                area.removeEventHandler(MouseEvent.MOUSE_PRESSED, handler);
            }
        });
        assertNull(thrown, "the lost click must not surface as an uncaught error");
    }

    @Test
    void otherFailuresStillPropagate() {
        CodeArea area = editor.getCodeArea();
        var handler = (javafx.event.EventHandler<MouseEvent>) e -> {
            throw failureFrom("org.fxt.freexmltoolkit.SomethingElse");
        };
        Throwable thrown = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            area.addEventHandler(MouseEvent.MOUSE_PRESSED, handler);
            try {
                Event.fireEvent(area, press());
                return null;
            } catch (Throwable t) {
                return t;
            } finally {
                area.removeEventHandler(MouseEvent.MOUSE_PRESSED, handler);
            }
        });
        assertInstanceOf(NoSuchElementException.class, thrown, "only the Flowless hit race is ignored");
    }

    @Test
    void undoRedoMovesTheCurrentLineWithoutFailing() throws Exception {
        CodeArea area = editor.getCodeArea();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            editor.setText("<a>\n  <b/>\n</a>\n");
            area.getUndoManager().forgetHistory();
            area.moveTo(2, 0);
            area.replaceText(2, 0, 2, 0, "  <c/>\n  <d/>\n");
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            area.undo();
            area.redo();
            area.undo();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        int caretLine = WaitForAsyncUtils.waitForAsyncFx(2000, area::getCurrentParagraph);
        assertTrue(area.getParagraph(caretLine).getParagraphStyle().contains("current-line"),
                "after the edits settle, the caret's paragraph carries the current-line style");
    }
}
