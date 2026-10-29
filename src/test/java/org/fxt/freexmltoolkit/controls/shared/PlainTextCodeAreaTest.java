package org.fxt.freexmltoolkit.controls.shared;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import javafx.scene.Scene;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.stage.Stage;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.ReadOnlyStyledDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Paste takes the clipboard's plain text, and RichTextFX's styled format holding something other
 * than {@code byte[]} (as macOS reported it) no longer throws.
 */
@ExtendWith(ApplicationExtension.class)
class PlainTextCodeAreaTest {

    private PlainTextCodeArea area;

    @Start
    void start(Stage stage) {
        area = new PlainTextCodeArea();
        stage.setScene(new Scene(area, 300, 200));
        stage.show();
    }

    @Test
    void pastesThePlainTextAndReplacesTheSelection() throws Exception {
        String text = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            area.replaceText("<a>old</a>");
            area.selectRange(3, 6);
            ClipboardContent content = new ClipboardContent();
            content.putString("new");
            content.put(richTextFormat(area), "not bytes");
            Clipboard.getSystemClipboard().setContent(content);
            area.paste();
            return area.getText();
        });
        assertEquals("<a>new</a>", text);
    }

    @Test
    void styledFormatWithoutBytesDoesNotThrow() throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            area.replaceText("x");
            Clipboard.getSystemClipboard().setContent(Map.of(richTextFormat(area), "not bytes"));
            assertDoesNotThrow(() -> area.paste());
            return null;
        });
        assertEquals("x", WaitForAsyncUtils.waitForAsyncFx(2000, () -> area.getText()));
    }

    /** The data format RichTextFX's own paste reads (and casts to {@code byte[]}). */
    private static DataFormat richTextFormat(CodeArea area) {
        var codecs = area.getStyleCodecs().orElseThrow();
        String name = ReadOnlyStyledDocument.codec(codecs._1, codecs._2, area.getSegOps()).getName();
        DataFormat existing = DataFormat.lookupMimeType(name);
        return existing != null ? existing : new DataFormat(name);
    }
}
