package org.fxt.freexmltoolkit.controls.v2.editor;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.v2.editor.services.MutableXmlSchemaProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The XML editor highlights the caret's line (Figma current-line tint): the
 * paragraph holding the caret carries the {@code current-line} style class and
 * other paragraphs do not.
 */
@ExtendWith(ApplicationExtension.class)
class XmlCodeEditorV2CurrentLineTest {

    private XmlCodeEditorV2 editor;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        editor = new XmlCodeEditorV2(new MutableXmlSchemaProvider());
        stage.setScene(new Scene(editor, 600, 400));
        stage.show();
    }

    @Test
    void caretParagraphCarriesTheCurrentLineStyle() throws Exception {
        CodeArea ca = editor.getCodeArea();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            editor.setText("<a>\n  <b/>\n  <c/>\n</a>\n");
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            ca.moveTo(2, 0); // caret into line 3 (paragraph index 2)
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(ca.getParagraph(2).getParagraphStyle().contains("current-line"),
                "the caret's paragraph must carry the current-line style");
        assertFalse(ca.getParagraph(0).getParagraphStyle().contains("current-line"),
                "other paragraphs must not carry it");

        // Moving the caret elsewhere moves the highlight.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            ca.moveTo(0, 0);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(ca.getParagraph(0).getParagraphStyle().contains("current-line"));
        assertFalse(ca.getParagraph(2).getParagraphStyle().contains("current-line"));
    }
}
