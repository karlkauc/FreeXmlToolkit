package org.fxt.freexmltoolkit.controls.v2.editor.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2;
import org.fxt.freexmltoolkit.controls.v2.editor.services.MutableXmlSchemaProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The editor's context menu does not ask the system clipboard when it opens (on Windows that blocked
 * the UI for seconds with large clipboard content): Paste is always offered, and pasting reads the
 * clipboard once - an empty clipboard inserts nothing.
 */
@ExtendWith(ApplicationExtension.class)
class ContextMenuClipboardTest {

    private XmlCodeEditorV2 editor;

    @Start
    void start(Stage stage) {
        editor = new XmlCodeEditorV2(new MutableXmlSchemaProvider());
        editor.getCodeArea().replaceText("<root>x</root>");
        stage.setScene(new Scene(editor, 600, 300));
        stage.show();
    }

    private <T> T fx(java.util.concurrent.Callable<T> action) {
        T result = WaitForAsyncUtils.waitForAsyncFx(5000, action);
        WaitForAsyncUtils.waitForFxEvents();
        return result;
    }

    private MenuItem openMenuAndFindPaste() {
        return fx(() -> {
            ContextMenu menu = editor.getContextMenuManager().getContextMenu();
            menu.getOnShowing().handle(null); // the menu-state update that runs on every right-click
            return menu.getItems().stream()
                    .filter(i -> i.getText() != null && i.getText().startsWith("Paste"))
                    .findFirst().orElseThrow();
        });
    }

    @Test
    void pasteIsOfferedWithoutAskingTheClipboardAndAnEmptyOneInsertsNothing() {
        fx(() -> {
            Clipboard.getSystemClipboard().clear();
            return null;
        });

        MenuItem paste = openMenuAndFindPaste();
        assertFalse(fx(paste::isDisable), "Paste stays available");

        fx(() -> {
            editor.getCodeArea().moveTo(editor.getCodeArea().getLength());
            paste.fire();
            return null;
        });
        assertEquals("<root>x</root>", fx(() -> editor.getCodeArea().getText()));
    }

    @Test
    void pasteInsertsTheClipboardText() {
        fx(() -> {
            ClipboardContent content = new ClipboardContent();
            content.putString("<!--c-->");
            Clipboard.getSystemClipboard().setContent(content);
            editor.getCodeArea().moveTo(editor.getCodeArea().getLength());
            return null;
        });

        MenuItem paste = openMenuAndFindPaste();
        fx(() -> {
            paste.fire();
            return null;
        });

        assertEquals("<root>x</root><!--c-->", fx(() -> editor.getCodeArea().getText()));
    }
}
