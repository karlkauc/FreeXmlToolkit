package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.input.Clipboard;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The Large File Viewer: indexing, go to line, selection and copy, find, opening a large file. */
@ExtendWith(ApplicationExtension.class)
class LargeFileViewTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        stage.setScene(new Scene(host, 1000, 600));
        stage.show();
    }

    @AfterEach
    void reset() {
        EditorFileSync.largeFileAnswer = null;
    }

    private static Path orders(Path dir, int count) throws Exception {
        Path file = dir.resolve("orders.xml");
        try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            out.write("<orders>\n");
            for (int i = 1; i <= count; i++) {
                out.write("  <order id=\"" + i + "\"><amount>" + (i * 3) + "</amount></order>\n");
            }
            out.write("</orders>\n");
        }
        return file;
    }

    private LargeFileView open(Path file) throws Exception {
        LargeFileView view = WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.openLargeFileViewer(file));
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                () -> WaitForAsyncUtils.waitForAsyncFx(2000, () -> view.indexForTesting() != null));
        return view;
    }

    @Test
    void goToLineSelectCopyAndFind(@TempDir Path dir) throws Exception {
        Path file = orders(dir, 20_000);
        LargeFileView view = open(file);
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, view::infoForTesting).contains("20,002 lines"));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.setLineForTesting("15000");
            view.onGoToLine();
            return null;
        });
        assertEquals(14_999, WaitForAsyncUtils.waitForAsyncFx(2000, view::selectionForTesting)[0]);
        WaitForAsyncUtils.waitForFxEvents();
        int first = WaitForAsyncUtils.waitForAsyncFx(2000, view::firstVisibleRowForTesting);
        assertTrue(first > 14_900 && first <= 14_999, "scrolled to the line: " + first + " "
                + WaitForAsyncUtils.waitForAsyncFx(2000, view::flowStateForTesting));
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, view::statusForTesting).startsWith("Line 15,000"));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.showRow(1);
            view.copySelectionForTesting();
            return null;
        });
        assertEquals("  <order id=\"1\"><amount>3</amount></order>",
                WaitForAsyncUtils.waitForAsyncFx(2000, () -> Clipboard.getSystemClipboard().getString()));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.setFindForTesting("id=\"1999\\d\"", true);
            view.onFind();
            return null;
        });
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS,
                () -> WaitForAsyncUtils.waitForAsyncFx(2000, () -> view.hitsForTesting().size() == 10));
        assertEquals(19_991, WaitForAsyncUtils.waitForAsyncFx(2000, view::hitsForTesting).get(0).line());
        assertEquals(19_990, WaitForAsyncUtils.waitForAsyncFx(2000, view::selectionForTesting)[0],
                "the first hit is shown");
    }

    @Test
    void highlightsXml() {
        List<Text> parts = LargeFileView.highlight("<a id=\"1\">x<!-- c --></a>");
        assertEquals("<a", parts.get(0).getText());
        assertTrue(parts.get(0).getStyleClass().contains("fxt-lf-tag"));
        assertTrue(parts.stream().anyMatch(t -> t.getText().equals("id") && t.getStyleClass().contains("fxt-lf-attr")));
        assertTrue(parts.stream().anyMatch(t -> t.getText().equals("\"1\"") && t.getStyleClass().contains("fxt-lf-value")));
        assertTrue(parts.stream().anyMatch(t -> t.getText().equals("<!-- c -->") && t.getStyleClass().contains("fxt-lf-comment")));
        assertEquals("<a id=\"1\">x<!-- c --></a>", parts.stream().map(Text::getText).reduce("", String::concat));
    }

    @Test
    void aLargeFileCanOpenInTheViewer(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("big.xml");
        byte[] line = "<r>0123456789012345678901234567890123456789012345678901234567890123456789</r>\n"
                .getBytes(StandardCharsets.UTF_8);
        long target = LargeFileGuard.WARN_BYTES + 1024;
        try (var out = new java.io.BufferedOutputStream(Files.newOutputStream(file), 1 << 20)) {
            for (long written = 0; written < target; written += line.length) {
                out.write(line);
            }
        }
        boolean[] asked = {false};
        EditorFileSync.largeFileAnswer = (path, tooLarge) -> {
            asked[0] = true;
            return EditorFileSync.LargeFileChoice.VIEWER;
        };
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(file));
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.lookup("#large-file-flow") != null));
        assertTrue(asked[0]);
        assertFalse(WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.getOpenDocuments().stream().anyMatch(d -> file.equals(d.getPath()))),
                "no editor tab for the file");
        LargeFileView view = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openLargeFileViewer(file));
        assertEquals(1, WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.lookupAll(".fxt-large-file-view").size()), "the open viewer is reused");
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                () -> WaitForAsyncUtils.waitForAsyncFx(2000, () -> view.indexForTesting() != null));
    }
}
