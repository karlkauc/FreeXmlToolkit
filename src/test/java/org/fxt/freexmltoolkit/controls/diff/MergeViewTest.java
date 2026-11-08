package org.fxt.freexmltoolkit.controls.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** Tools → Merge Files: conflicts in the merged text, resolving them, XML check, saving, the dialog. */
@ExtendWith(ApplicationExtension.class)
class MergeViewTest {

    private static final String BASE = "<a>\n  <x>1</x>\n  <y>1</y>\n  <z>1</z>\n</a>\n";
    private static final String LEFT = "<a>\n  <x>2</x>\n  <y>1</y>\n  <z>L</z>\n</a>\n";
    private static final String RIGHT = "<a>\n  <x>9</x>\n  <y>1</y>\n  <z>R</z>\n</a>\n";

    private StackPane root;

    @Start
    void start(Stage stage) {
        root = new StackPane();
        stage.setScene(new Scene(root, 1000, 600));
        stage.show();
    }

    @AfterEach
    void reset() {
        MergeView.saveWithConflictsAnswer = null;
    }

    private MergeView open(Path dir, Path output) throws Exception {
        Path base = Files.writeString(dir.resolve("base.xml"), BASE);
        Path left = Files.writeString(dir.resolve("left.xml"), LEFT);
        Path right = Files.writeString(dir.resolve("right.xml"), RIGHT);
        MergeView view = MergeView.open(base, left, right, output).get(20, TimeUnit.SECONDS);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> root.getChildren().setAll(view));
        return view;
    }

    @Test
    void resolvesConflictsAndChecksTheXml(@TempDir Path dir) throws Exception {
        Path output = dir.resolve("merged.xml");
        MergeView view = open(dir, output);
        assertEquals(2, WaitForAsyncUtils.waitForAsyncFx(2000, view::conflictsForTesting));
        assertEquals("2 conflicts left", WaitForAsyncUtils.waitForAsyncFx(2000, view::statusForTesting));
        String text = WaitForAsyncUtils.waitForAsyncFx(2000, view::textForTesting);
        assertTrue(text.startsWith("<a>\n<<<<<<< left.xml\n  <x>2</x>\n||||||| base.xml\n  <x>1</x>\n=======\n  <x>9</x>\n>>>>>>> right.xml\n"),
                text);

        // the caret starts in the first conflict: Use Left resolves it, then the caret moves to the next
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.onUseLeft();
            return null;
        });
        assertEquals(1, WaitForAsyncUtils.waitForAsyncFx(2000, view::conflictsForTesting));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.onUseRight();
            return null;
        });
        assertEquals("<a>\n  <x>2</x>\n  <y>1</y>\n  <z>R</z>\n</a>\n", WaitForAsyncUtils.waitForAsyncFx(2000, view::textForTesting));
        assertEquals("No conflicts · well-formed XML", WaitForAsyncUtils.waitForAsyncFx(2000, view::statusForTesting));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.onSave();
            return null;
        });
        assertEquals("<a>\n  <x>2</x>\n  <y>1</y>\n  <z>R</z>\n</a>\n", Files.readString(output));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.setTextForTesting("<a>");
            return null;
        });
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, view::statusForTesting).startsWith("No conflicts · Not well-formed"));
    }

    @Test
    void savingWithConflictsAsksFirst(@TempDir Path dir) throws Exception {
        Path output = dir.resolve("merged.xml");
        MergeView view = open(dir, output);
        MergeView.saveWithConflictsAnswer = () -> false;
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.onSave();
            return null;
        });
        assertFalse(Files.exists(output), "declined: nothing written");
        MergeView.saveWithConflictsAnswer = () -> true;
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.onUseBoth();
            view.onUseBase();
            view.onSave();
            return null;
        });
        String saved = Files.readString(output);
        assertTrue(saved.contains("  <x>2</x>\n  <x>9</x>\n"), saved);
        assertTrue(saved.contains("  <z>1</z>\n"), "Use Base took the base: " + saved);
    }

    @Test
    void dialogValidatesTheFiles(@TempDir Path dir) throws Exception {
        Path base = Files.writeString(dir.resolve("b.xml"), BASE);
        MergeFilesDialog dialog = WaitForAsyncUtils.waitForAsyncFx(2000, () -> new MergeFilesDialog(base));
        assertEquals("Choose the base, the left and the right file.",
                WaitForAsyncUtils.waitForAsyncFx(2000, dialog::validate));
        assertEquals("One of the files does not exist.", WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            dialog.setPathsForTesting(base.toString(), base.toString(), dir.resolve("missing.xml").toString(), "");
            return dialog.validate();
        }));
        assertNull(WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            dialog.setPathsForTesting(base.toString(), base.toString(), base.toString(), "");
            return dialog.validate();
        }));
    }
}
