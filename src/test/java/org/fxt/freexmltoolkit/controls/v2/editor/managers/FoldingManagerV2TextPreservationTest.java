package org.fxt.freexmltoolkit.controls.v2.editor.managers;

import static org.junit.jupiter.api.Assertions.*;

import javafx.stage.Stage;

import org.fxmisc.richtext.CodeArea;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Regression test for the folding data-corruption bug: folding a region must be
 * PURELY VISUAL. The document text returned by {@code codeArea.getText()} feeds
 * XSD validation, XPath queries and save — it must stay the complete, unchanged
 * XML while regions are folded.
 */
@ExtendWith(ApplicationExtension.class)
class FoldingManagerV2TextPreservationTest {

    private static final String XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <root>
              <ControlData>
                <UniqueDocumentID>ID_1</UniqueDocumentID>
                <ContentDate>2024-09-25</ContentDate>
              </ControlData>
              <Funds>
                <Fund>
                  <Name>Demo</Name>
                </Fund>
              </Funds>
            </root>""";

    private CodeArea codeArea;
    private FoldingManagerV2 foldingManager;

    @Start
    void start(Stage stage) {
        codeArea = new CodeArea();
        foldingManager = new FoldingManagerV2(codeArea);
        stage.setScene(new javafx.scene.Scene(codeArea, 600, 400));
        stage.show();
    }

    @Test
    @DisplayName("Folding keeps the document text complete (validation/XPath/save source)")
    void foldingDoesNotChangeTheDocumentText() {
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            codeArea.replaceText(XML);
            foldingManager.updateFoldingRegions(XML);
            return null;
        });
        Integer foldLine = WaitForAsyncUtils.waitForAsyncFx(3000, () ->
                foldingManager.getFoldableRegions().keySet().stream()
                        .filter(l -> codeArea.getText().split("\n")[l].contains("ControlData"))
                        .findFirst().orElseThrow());

        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            foldingManager.fold(foldLine);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(foldingManager.isFolded(foldLine), "the region must report as folded");
        assertEquals(XML, WaitForAsyncUtils.waitForAsyncFx(3000, () -> codeArea.getText()),
                "folding must be visual only — the document text must stay complete");

        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            foldingManager.unfold(foldLine);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(foldingManager.isFolded(foldLine));
        assertEquals(XML, WaitForAsyncUtils.waitForAsyncFx(3000, () -> codeArea.getText()),
                "unfolding must leave the document text unchanged");
    }

    @Test
    @DisplayName("Fold all / unfold all keep the document text complete")
    void foldAllAndUnfoldAllDoNotChangeTheDocumentText() {
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            codeArea.replaceText(XML);
            foldingManager.updateFoldingRegions(XML);
            return null;
        });

        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            foldingManager.foldAll();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(foldingManager.getFoldedLines().isEmpty(), "fold all must fold regions");
        assertEquals(XML, WaitForAsyncUtils.waitForAsyncFx(3000, () -> codeArea.getText()));

        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            foldingManager.unfoldAll();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(foldingManager.getFoldedLines().isEmpty(), "unfold all must clear folds");
        assertEquals(XML, WaitForAsyncUtils.waitForAsyncFx(3000, () -> codeArea.getText()));
    }

    @Test
    @DisplayName("Folded paragraphs are hidden visually (RichTextFX paragraph folding)")
    void foldedRegionIsVisuallyCollapsed() {
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            codeArea.replaceText(XML);
            foldingManager.updateFoldingRegions(XML);
            return null;
        });
        Integer foldLine = WaitForAsyncUtils.waitForAsyncFx(3000, () ->
                foldingManager.getFoldableRegions().keySet().stream().findFirst().orElseThrow());

        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            foldingManager.fold(foldLine);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        Boolean hidden = WaitForAsyncUtils.waitForAsyncFx(3000,
                () -> codeArea.isFolded(foldLine + 1));
        assertTrue(hidden, "the paragraph after the fold start must be hidden");
    }
}
