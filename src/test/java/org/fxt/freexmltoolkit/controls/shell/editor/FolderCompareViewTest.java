package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.service.FolderComparison.Entry;
import org.fxt.freexmltoolkit.service.FolderComparison.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** Tools → Compare Folders: the table, the identical-files filter and opening a pair. */
@ExtendWith(ApplicationExtension.class)
class FolderCompareViewTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        stage.setScene(new Scene(host, 1100, 700));
        stage.show();
    }

    @Test
    void listsDifferencesAndOpensAPair(@TempDir Path dir) throws Exception {
        Path left = Files.createDirectories(dir.resolve("left"));
        Path right = Files.createDirectories(dir.resolve("right"));
        Files.writeString(left.resolve("same.xml"), "<a/>");
        Files.writeString(right.resolve("same.xml"), "<a/>");
        Files.writeString(left.resolve("diff.xml"), "<a>1</a>");
        Files.writeString(right.resolve("diff.xml"), "<a>2</a>");
        Files.writeString(left.resolve("only.txt"), "x");

        FolderCompareView view = WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            FolderCompareView v = new FolderCompareView(host, left, right);
            host.openToolTab("Compare Folders", "bi-folder2-open", v);
            return v;
        });
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> view.resultForTesting() != null);
        List<Entry> shown = WaitForAsyncUtils.waitForAsyncFx(2000, view::shownEntriesForTesting);
        assertEquals(List.of("diff.xml", "only.txt"), shown.stream().map(Entry::relativePath).toList(),
                "identical files are hidden by default");
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, view::summaryForTesting)
                .startsWith("3 files: 1 identical, 0 equivalent as XML, 1 different, 1 only left"));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.setShowIdenticalForTesting(true);
            return null;
        });
        assertEquals(3, WaitForAsyncUtils.waitForAsyncFx(2000, view::shownEntriesForTesting).size());

        Entry different = shown.stream().filter(e -> e.status() == Status.DIFFERENT).findFirst().orElseThrow();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.openForTesting(different);
            return null;
        });
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.lookupAll(".diff-view").size() > 0));
    }
}
