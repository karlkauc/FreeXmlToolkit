package org.fxt.freexmltoolkit.controls.v2.editor.managers;

import static org.junit.jupiter.api.Assertions.*;

import org.fxmisc.richtext.CodeArea;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import javafx.stage.Stage;

@ExtendWith(ApplicationExtension.class)
class SyntaxHighlightManagerV2LongLineGuardTest {

    private SyntaxHighlightManagerV2 syntaxManager;

    @Start
    void start(Stage stage) {
        CodeArea codeArea = new CodeArea();
        syntaxManager = new SyntaxHighlightManagerV2(codeArea);
    }

    @Test
    @DisplayName("Should disable highlighting for text with line exceeding 200KB")
    void testDisablesHighlightingForLongLine() {
        String longLine = "<root>" + "x".repeat(200 * 1024 + 1) + "</root>";
        syntaxManager.applySyntaxHighlighting(longLine);
        assertTrue(syntaxManager.isHighlightingDisabled(),
                "Highlighting must be disabled for text with extremely long line");
    }

    @Test
    @DisplayName("Should not disable highlighting for normal text")
    void testDoesNotDisableForNormalText() {
        String normalXml = "<root>\n  <child>text</child>\n</root>";
        syntaxManager.applySyntaxHighlighting(normalXml);
        assertFalse(syntaxManager.isHighlightingDisabled(),
                "Highlighting must not be disabled for normal XML");
    }

    @Test
    @DisplayName("Should not disable highlighting for short single-line XML")
    void testDoesNotDisableForShortSingleLine() {
        String shortLine = "<root><child>text</child></root>";
        syntaxManager.applySyntaxHighlighting(shortLine);
        assertFalse(syntaxManager.isHighlightingDisabled(),
                "Highlighting must not be disabled for short single-line XML");
    }
}
