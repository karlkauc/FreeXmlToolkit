package org.fxt.freexmltoolkit.controls.v2.editor.managers;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxmisc.richtext.CodeArea;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Very large files (&gt; 2 MB) are no longer left unhighlighted: instead of disabling highlighting,
 * the manager switches to viewport mode and highlights only the visible region (kept fast for any
 * size). Verifies the top (visible) region is styled while a region far past the viewport is not.
 */
@ExtendWith(ApplicationExtension.class)
class SyntaxHighlightManagerV2ViewportTest {

    private CodeArea codeArea;
    private SyntaxHighlightManagerV2 syntaxManager;

    @Start
    void start(Stage stage) {
        codeArea = new CodeArea();
        syntaxManager = new SyntaxHighlightManagerV2(codeArea, 50);
        stage.setScene(new Scene(codeArea, 600, 400));
        stage.show();
    }

    private static String largeXml(int targetBytes) {
        StringBuilder sb = new StringBuilder(targetBytes + 64);
        sb.append("<root>\n");
        String line = "  <item id=\"1\">value</item>\n";
        while (sb.length() < targetBytes) {
            sb.append(line);
        }
        sb.append("</root>\n");
        return sb.toString();
    }

    @Test
    @DisplayName("A >2MB file is highlighted via viewport mode (visible region styled, not disabled)")
    void largeFileUsesViewportHighlighting() throws Exception {
        String text = largeXml(2_200_000); // > VERY_LARGE_FILE_THRESHOLD (2MB)
        assertTrue(text.length() > 2 * 1024 * 1024);

        WaitForAsyncUtils.waitForAsyncFx(4000, () -> {
            codeArea.replaceText(text); // moves the caret/scroll to the end
            return null;
        });
        // Scroll back to the top so the top region is the visible viewport.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            codeArea.moveTo(0);
            codeArea.showParagraphAtTop(0);
            return null;
        });
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            syntaxManager.applySyntaxHighlighting(text);
            return null;
        });

        // Highlighting must NOT be disabled (previously it was, for >2MB).
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !syntaxManager.isHighlightingDisabled());
        assertFalse(syntaxManager.isHighlightingDisabled(), "large file must use viewport mode, not be disabled");

        // The visible top region is highlighted: '<' is tagmark, the element name char is anytag.
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> styleAt(1).contains("anytag"));
        assertTrue(styleAt(0).contains("tagmark"), "'<' of <root> should be tagmark");
        assertTrue(styleAt(1).contains("anytag"), "'r' of <root> should be anytag");

        // A position in the middle — never inside any viewport — is NOT styled, proving the
        // highlighting is viewport-scoped (whole-document highlighting would have styled it).
        int middle = codeArea.getLength() / 2;
        // align to a '<' so we test a position that WOULD be a tag if the whole doc were highlighted
        int tagInMiddle = codeArea.getText(middle, Math.min(middle + 40, codeArea.getLength())).indexOf('<');
        int probe = tagInMiddle >= 0 ? middle + tagInMiddle + 1 : middle;
        assertFalse(styleAt(probe).contains("anytag"),
                "a tag in the middle (never scrolled into view) must remain unstyled in viewport mode");
    }

    private java.util.Collection<String> styleAt(int pos) {
        return WaitForAsyncUtils.waitForAsyncFx(2000, () -> codeArea.getStyleOfChar(pos));
    }
}
