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
class FoldingManagerV2LongLineGuardTest {

    private FoldingManagerV2 foldingManager;

    @Start
    void start(Stage stage) {
        CodeArea codeArea = new CodeArea();
        foldingManager = new FoldingManagerV2(codeArea);
    }

    @Test
    @DisplayName("Should skip folding for single-line XML exceeding threshold")
    void testSkipsFoldingForLongSingleLine() {
        String longLine = "<root>" + "x".repeat(100 * 1024 + 1) + "</root>";
        foldingManager.updateFoldingRegions(longLine);
        assertTrue(foldingManager.getFoldableRegions().isEmpty(),
                "Folding regions must be empty for single-line XML exceeding threshold");
    }

    @Test
    @DisplayName("Should skip folding when one of two lines exceeds threshold")
    void testSkipsFoldingForTwoLinesWithOneLong() {
        String xml = "<root>" + "x".repeat(100 * 1024 + 1) + "\n</root>";
        foldingManager.updateFoldingRegions(xml);
        assertTrue(foldingManager.getFoldableRegions().isEmpty(),
                "Folding regions must be empty when any line exceeds threshold");
    }

    @Test
    @DisplayName("Should compute folding for normal multi-line XML")
    void testComputesFoldingForNormalXml() {
        String xml = "<root>\n  <child>text</child>\n  <child2>text2</child2>\n</root>";
        foldingManager.updateFoldingRegions(xml);
        assertFalse(foldingManager.getFoldableRegions().isEmpty(),
                "Folding regions should be computed for normal multi-line XML");
    }

    @Test
    @DisplayName("Should clear existing regions when long-line text is set")
    void testClearsExistingRegionsOnLongLine() {
        // First set normal XML to populate regions
        String normalXml = "<root>\n  <child>text</child>\n  <child2>text2</child2>\n</root>";
        foldingManager.updateFoldingRegions(normalXml);
        assertFalse(foldingManager.getFoldableRegions().isEmpty());

        // Now set long-line XML — must clear
        String longLine = "<root>" + "x".repeat(100 * 1024 + 1) + "</root>";
        foldingManager.updateFoldingRegions(longLine);
        assertTrue(foldingManager.getFoldableRegions().isEmpty(),
                "Existing folding regions must be cleared when long-line text is set");
    }
}
