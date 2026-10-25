package org.fxt.freexmltoolkit.controls.v2.editor;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.editor.services.MutableXmlSchemaProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * A large file's editor document is built off the FX thread ({@link XmlCodeEditorV2#prepareText})
 * and only swapped in on it; the result must match a plain {@link XmlCodeEditorV2#setText(String)}.
 */
@ExtendWith(ApplicationExtension.class)
class XmlCodeEditorV2PreparedTextTest {

    private XmlCodeEditorV2 editor;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        editor = new XmlCodeEditorV2(new MutableXmlSchemaProvider());
        stage.setScene(new Scene(editor, 600, 400));
        stage.show();
    }

    private static String largeXml() {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\"?>\n<root>\n");
        int i = 0;
        while (sb.length() < XmlCodeEditorV2.PREPARE_THRESHOLD_CHARS + 1000) {
            sb.append("  <item id=\"").append(i++).append("\">value</item>\r\n");
        }
        return sb.append("</root>\n").toString();
    }

    @Test
    void shortTextsAreNotPrepared() {
        assertNull(editor.prepareText("<root/>"));
        assertNull(editor.prepareText(null));
    }

    @Test
    void preparedLargeTextShowsTheSameDocumentAsSetText() throws Exception {
        String xml = largeXml();

        // built on this (non-FX) test thread, as the loader does
        XmlCodeEditorV2.PreparedText prepared = editor.prepareText(xml);
        assertNotNull(prepared);

        int preparedParagraphs = WaitForAsyncUtils.waitForAsyncFx(10_000, () -> {
            editor.getCodeArea().replaceText("something else");
            editor.setText(prepared);
            assertEquals(0, editor.getCodeArea().getCaretPosition());
            return editor.getCodeArea().getParagraphs().size();
        });
        String preparedText = WaitForAsyncUtils.waitForAsyncFx(10_000, () -> editor.getText());

        int plainParagraphs = WaitForAsyncUtils.waitForAsyncFx(10_000, () -> {
            editor.setText(xml);
            return editor.getCodeArea().getParagraphs().size();
        });
        String plainText = WaitForAsyncUtils.waitForAsyncFx(10_000, () -> editor.getText());

        assertEquals(plainText, preparedText);
        assertEquals(plainParagraphs, preparedParagraphs);
    }
}
