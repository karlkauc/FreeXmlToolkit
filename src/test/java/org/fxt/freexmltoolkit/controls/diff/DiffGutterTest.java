package org.fxt.freexmltoolkit.controls.diff;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Guards the diff gutter against scaling with the number of differences: arrow buttons
 * are pooled per viewport, and "apply all" is a single edit.
 */
@ExtendWith(ApplicationExtension.class)
class DiffGutterTest {

    private static final int LINES = 4000;

    private TabPane tabs;

    @Start
    void start(Stage stage) {
        tabs = new TabPane();
        stage.setScene(new Scene(tabs, 1000, 600));
        stage.show();
    }

    private DiffView openDiff(Path tmp) throws Exception {
        StringBuilder left = new StringBuilder();
        StringBuilder right = new StringBuilder();
        for (int i = 0; i < LINES; i++) {
            left.append("line ").append(i).append('\n');
            // every second line differs: 2000 separate chunks
            right.append(i % 2 == 0 ? "line " : "LINE ").append(i).append('\n');
        }
        Path rightFile = tmp.resolve("right.txt");
        Files.writeString(rightFile, right);
        DiffView diff = WaitForAsyncUtils.waitForAsyncFx(10000, () -> {
            DiffView d = new DiffView("left.txt", left.toString(), text -> { }, rightFile.toFile());
            tabs.getTabs().add(d);
            tabs.getSelectionModel().select(d);
            return d;
        });
        WaitForAsyncUtils.waitForFxEvents();
        return diff;
    }

    @Test
    void arrowButtonsAreBoundedByTheViewport(@TempDir Path tmp) throws Exception {
        DiffView diff = openDiff(tmp);
        long changed = diff.getChunksForTesting().stream().filter(c -> !c.isEqual()).count();
        assertEquals(LINES / 2, changed, "test setup: every second line is its own chunk");

        DiffGutter gutter = diff.getGutterForTesting();
        // A 600 px window shows a few dozen lines; anything near the chunk count means
        // the gutter builds nodes for invisible differences again.
        assertTrue(gutter.getChildren().size() < 200,
                "gutter must only hold arrows for visible chunks, was " + gutter.getChildren().size());
        assertTrue(gutter.getVisibleArrowCountForTesting() <= gutter.getChildren().size());
        assertTrue(gutter.getVisibleArrowCountForTesting() > 0, "visible chunks must get arrows");
    }

    @Test
    void applyAllMakesBothSidesEqualInOneStep(@TempDir Path tmp) throws Exception {
        DiffView diff = openDiff(tmp);

        WaitForAsyncUtils.waitForAsyncFx(10000, () -> diff.applyAllForTesting(DiffGutter.Direction.LEFT_TO_RIGHT));
        WaitForAsyncUtils.waitForFxEvents();

        String[] texts = diff.getTextsForTesting();
        assertEquals(texts[0], texts[1], "right side must match the left side");
        assertTrue(diff.getChunksForTesting().stream().allMatch(DiffChunk::isEqual));
        assertEquals(0, diff.getGutterForTesting().getVisibleArrowCountForTesting());
    }
}
