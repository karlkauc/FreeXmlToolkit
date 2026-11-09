package org.fxt.freexmltoolkit.controls.v2.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.editor.services.MutableXmlSchemaProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * {@link XmlCodeEditorV2#getText()} builds the document String once per text revision: the
 * Inspector asked for it on every caret move, which took 2.5 s for a 3.7-million-line document
 * (telemetry {@code 8317acc70b9aa03c}). An edit must still be seen at once.
 */
@ExtendWith(ApplicationExtension.class)
class XmlCodeEditorV2TextCacheTest {

    private XmlCodeEditorV2 editor;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        editor = new XmlCodeEditorV2(new MutableXmlSchemaProvider());
        stage.setScene(new Scene(editor, 400, 300));
        stage.show();
    }

    @Test
    void theLoadedTextIsReturnedUntilTheNextEdit() {
        String loaded = "<root>\n  <a>1</a>\n</root>";
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            editor.setText(loaded);
            assertSame(loaded, editor.getText(), "no rebuild right after loading");
            assertSame(editor.getText(), editor.getText(), "no rebuild while nothing changes");

            editor.replaceTextRegion(12, 13, "2");
            String edited = editor.getText();
            assertEquals("<root>\n  <a>2</a>\n</root>", edited, "an edit is seen at once");
            assertSame(edited, editor.getText());

            editor.getCodeArea().appendText("\n");
            assertNotSame(edited, editor.getText());
            assertEquals(edited + "\n", editor.getText());
            return null;
        });
    }

    @Test
    void aPreparedLoadIsCachedToo() {
        String big = "<r>" + "<x>1</x>\n".repeat(XmlCodeEditorV2.PREPARE_THRESHOLD_CHARS / 9 + 1) + "</r>";
        XmlCodeEditorV2.PreparedText prepared = editor.prepareText(big);
        WaitForAsyncUtils.waitForAsyncFx(10_000, () -> {
            editor.setText(prepared);
            assertSame(big, editor.getText());
            return null;
        });
    }
}
