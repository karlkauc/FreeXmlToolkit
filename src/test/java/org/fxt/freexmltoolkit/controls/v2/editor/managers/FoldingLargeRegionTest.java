package org.fxt.freexmltoolkit.controls.v2.editor.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.NavigationActions;
import org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2;
import org.fxt.freexmltoolkit.controls.v2.editor.services.MutableXmlSchemaProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Folding {@code <Funds>} of a FundsXML file (24,450 lines) froze the application: RichTextFX's view built a
 * cell for every hidden line. With the patched Flowless view ({@code src/flowless}) hidden lines get no cell,
 * and {@link FoldingManagerV2} keeps the caret out of the folded block.
 */
@ExtendWith(ApplicationExtension.class)
class FoldingLargeRegionTest {

    private XmlCodeEditorV2 editor;
    private CodeArea area;
    private FoldingManagerV2 folding;
    private String text;
    private int funds;
    private int fundsEnd;

    @Start
    void start(Stage stage) throws Exception {
        editor = new XmlCodeEditorV2(new MutableXmlSchemaProvider());
        area = editor.getCodeArea();
        folding = editor.getFoldingManager();
        stage.setScene(new Scene(new StackPane(editor), 900, 600));
        stage.show();
        text = Files.readString(Path.of("src/test/resources/FundsXML_420.xml"));
    }

    private <T> T fx(Callable<T> call) {
        T result = WaitForAsyncUtils.waitForAsyncFx(60_000, call);
        WaitForAsyncUtils.waitForFxEvents();
        return result;
    }

    private void fxRun(Runnable run) {
        fx(() -> {
            run.run();
            return null;
        });
    }

    private void loadAndFoldFunds() {
        fxRun(() -> {
            editor.setText(text);
            folding.updateFoldingRegions(text);
        });
        var region = fx(() -> folding.getFoldableRegions().values().stream()
                .filter(r -> "Funds".equals(r.elementName())).findFirst().orElseThrow());
        funds = region.startLine();
        fundsEnd = region.endLine();
        fxRun(() -> {
            area.moveTo(funds, 0);
            area.requestFollowCaret();
        });
        fxRun(() -> folding.fold(funds));
    }

    private long cells() {
        return fx(() -> (long) area.lookupAll(".paragraph-box").size());
    }

    @Test
    void foldingFundsBuildsCellsOnlyForTheVisibleLines() {
        assertTrue(fx(folding::hasHiddenLineSupport), "the patched view was found");
        long start = System.nanoTime();
        loadAndFoldFunds();
        long ms = (System.nanoTime() - start) / 1_000_000;

        assertTrue(fx(() -> folding.isFolded(funds)));
        assertTrue(fundsEnd - funds > 20_000, "a large region: " + (fundsEnd - funds) + " lines");
        assertTrue(cells() < 200, "cells for the visible lines only, not one per hidden line: " + cells());
        assertTrue(ms < 20_000, "folding took " + ms + " ms"); // minutes before; generous for CI
        assertEquals(text, fx(area::getText), "folding never changes the text");
        assertEquals(fundsEnd - funds, fx(folding::hiddenLinesForTest).cardinality());
    }

    @Test
    void theCaretJumpsOverTheFoldedBlock() {
        loadAndFoldFunds();

        fxRun(() -> area.moveTo(funds + 1, 0)); // Down from the fold line
        assertEquals(fundsEnd + 1, (int) fx(area::getCurrentParagraph), "down lands after the block");

        fxRun(() -> area.moveTo(fundsEnd, 0)); // Up from the line after it
        assertEquals(funds, (int) fx(area::getCurrentParagraph), "up lands on the fold line");

        fxRun(() -> {
            area.moveTo(funds, 0);
            area.moveTo(funds + 1, 0, NavigationActions.SelectionPolicy.EXTEND); // Shift+Down
        });
        assertEquals(fundsEnd + 1, (int) fx(area::getCurrentParagraph));
        assertTrue(fx(() -> area.getSelection().getLength()) > 0, "the selection spans the block");
    }

    @Test
    void scrollingThroughTheDocumentKeepsFewCellsAndReachesTheEnd() {
        loadAndFoldFunds();
        fxRun(() -> area.scrollYToPixel(Double.MAX_VALUE));
        int lastLine = fx(() -> area.getParagraphs().size()) - 1;
        assertTrue(fx(area::lastVisibleParToAllParIndex) >= lastLine - 1, "the end of the document is reachable");
        fxRun(() -> area.scrollYToPixel(0));
        assertEquals(0, (int) fx(area::firstVisibleParToAllParIndex));
        assertTrue(cells() < 200, "still few cells: " + cells());
    }

    @Test
    void editsAboveTheFoldKeepTheHiddenLinesExact() {
        loadAndFoldFunds();
        fxRun(() -> area.insertText(0, 0, "<!-- a\nb -->\n")); // two new lines above
        var hidden = fx(folding::hiddenLinesForTest);
        assertEquals(funds + 3, hidden.nextSetBit(0), "the hidden block moved down with the text");
        assertEquals(fundsEnd - funds, hidden.cardinality());
        int mismatch = fx(() -> {
            for (int i = 0; i < area.getParagraphs().size(); i++) {
                if (hidden.get(i) != area.isFolded(i)) {
                    return i;
                }
            }
            return -1;
        });
        assertEquals(-1, mismatch, "every bit is a collapsed paragraph and every collapsed paragraph a bit");
    }

    @Test
    void unfoldingShowsEveryLineAgain() {
        loadAndFoldFunds();
        long start = System.nanoTime();
        fxRun(() -> folding.unfold(funds));
        long ms = (System.nanoTime() - start) / 1_000_000;

        assertFalse(fx(() -> folding.isFolded(funds)));
        assertTrue(fx(folding::hiddenLinesForTest).isEmpty());
        assertFalse(fx(() -> area.isFolded(funds + 1)));
        assertTrue(ms < 20_000, "unfolding took " + ms + " ms");
        assertEquals(text, fx(area::getText));
    }
}
