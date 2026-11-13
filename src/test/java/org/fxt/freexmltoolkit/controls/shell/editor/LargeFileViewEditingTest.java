package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.scene.Scene;
import javafx.scene.control.TreeItem;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.SetAttributeCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.service.largefile.LargeXmlStructure.Node;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The Large File Viewer's Graphic tab (a window of the file in the grid, edited and written back),
 * navigation (Go to XPath, Show in Structure, Go to child), statistics, export and change detection.
 */
@ExtendWith(ApplicationExtension.class)
class LargeFileViewEditingTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        stage.setScene(new Scene(host, 1100, 700));
        stage.show();
    }

    @AfterEach
    void reset() {
        LargeFileView.pendingAnswer = null;
        LargeFileStructurePane.exportTarget = null;
    }

    private static Path orders(Path dir, int count) throws Exception {
        Path file = dir.resolve("orders.xml");
        try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<orders xmlns=\"urn:o\">\n");
            for (int i = 1; i <= count; i++) {
                out.write("  <order id=\"" + i + "\" st=\"open\"><amount>" + (i * 3) + "</amount></order>\n");
            }
            out.write("</orders>\n");
        }
        return file;
    }

    private LargeFileView open(Path file) throws Exception {
        LargeFileView view = WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.openLargeFileViewer(file));
        waitIdle(view);
        return view;
    }

    private static void waitIdle(LargeFileView view) throws Exception {
        WaitForAsyncUtils.waitFor(60, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> view.indexForTesting() != null && !view.isBusyForTesting()));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static <T> T fx(java.util.concurrent.Callable<T> action) {
        return WaitForAsyncUtils.waitForAsyncFx(5000, action);
    }

    private static void fxRun(Runnable action) {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            action.run();
            return null;
        });
    }

    @Test
    void goToXPathShowsTheElementAndAPageOfItsSiblings(@TempDir Path dir) throws Exception {
        LargeFileView view = open(orders(dir, 5_500));
        LargeFileStructurePane pane = fx(view::structurePaneForTesting);
        fxRun(() -> {
            view.selectStructureForTesting();
            pane.setPathForTesting("/orders/order[4321]");
            pane.onGoToPath();
        });
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> fx(() -> {
            var item = view.treeForTesting().getSelectionModel().getSelectedItem();
            return item != null && item.getValue() instanceof Node node && node.xpath().equals("/orders[1]/order[4321]");
        }));
        TreeItem<Object> root = fx(() -> view.treeForTesting().getRoot());
        assertTrue(fx(() -> root.getChildren().getFirst().getValue()) instanceof LargeFileStructurePane.Previous,
                "the page 4,001–5,000 is shown with a Previous entry");
        assertEquals(1002, fx(() -> root.getChildren().size()), "previous + 1,000 + more");

        fxRun(() -> view.treeForTesting().getSelectionModel().select(root.getChildren().getFirst()));
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> fx(() -> root.getChildren().size() == 2002));
        assertEquals("/orders[1]/order[3001]", fx(() -> ((Node) root.getChildren().get(1).getValue()).xpath()));

        // Go to child: the records table of the root
        fxRun(() -> view.treeForTesting().getSelectionModel().select(root));
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> fx(() -> !view.recordsForTesting().isEmpty()));
        fxRun(() -> {
            pane.setChildForTesting("5400");
            pane.onGoToChild();
        });
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> fx(() -> {
            Node selected = pane.recordsTable().getSelectionModel().getSelectedItem();
            return selected != null && selected.xpath().equals("/orders[1]/order[5400]");
        }));
        assertTrue(fx(view::recordsLabelForTesting).contains("5,001–5,500 of 5,500"), fx(view::recordsLabelForTesting));
    }

    @Test
    void showInStructureFromTheTextTab(@TempDir Path dir) throws Exception {
        LargeFileView view = open(orders(dir, 3_000));
        fxRun(() -> view.showRow(2_502)); // line 2,503 = order 2,501
        fxRun(view::showSelectionInStructure);
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> fx(() -> {
            var item = view.treeForTesting().getSelectionModel().getSelectedItem();
            return item != null && item.getValue() instanceof Node node && node.xpath().equals("/orders[1]/order[2501]");
        }));
    }

    @Test
    void editAWindowInTheGridAndApplyItToTheFile(@TempDir Path dir) throws Exception {
        Path file = orders(dir, 3_000);
        String before = Files.readString(file);
        LargeFileView view = open(file);
        LargeFileStructurePane pane = fx(view::structurePaneForTesting);
        LargeFileWindowPane window = fx(view::windowPaneForTesting);
        fxRun(view::selectStructureForTesting);
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> fx(() -> view.recordsForTesting().size() == 1000));
        // a page of children, starting at child 11
        Node eleventh = fx(() -> view.recordsForTesting().get(10));
        fxRun(() -> pane.showRecordInGraphic(eleventh));
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> fx(() -> window.contextForTesting() != null));
        assertEquals("/orders[1] · children 11–1,010", fx(window::labelForTesting));
        XmlEditorContext context = fx(window::contextForTesting);
        XmlElement root = fx(() -> context.getDocument().getRootElement());
        assertEquals("orders", root.getName());
        assertEquals(1000, fx(() -> root.getChildElements().size()));
        XmlElement first = fx(() -> root.getChildElements().getFirst());
        assertEquals("11", fx(() -> first.getAttribute("id")));

        fxRun(() -> {
            context.executeCommand(new SetAttributeCommand(first, "st", "closed"));
            window.modelChangedForTesting();
        });
        assertTrue(fx(window::isDirty));
        // switching away asks; Cancel keeps the window
        AtomicReference<String> asked = new AtomicReference<>();
        LargeFileView.pendingAnswer = question -> {
            asked.set(question);
            return LargeFileView.PendingChoice.CANCEL;
        };
        Node other = fx(() -> view.recordsForTesting().get(500));
        fxRun(() -> pane.showRecordInGraphic(other));
        assertNotNull(asked.get());
        assertTrue(fx(window::isDirty), "still the edited window");
        assertFalse(fx(view::confirmClose), "closing is refused while the user cancels");

        fxRun(window::onApply);
        waitIdle(view);
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> fx(() -> window.contextForTesting() != null
                && !window.isDirty() && window.contextForTesting() != context));
        String after = Files.readString(file);
        assertTrue(after.contains("<order id=\"11\" st=\"closed\">"), "the edit is in the file");
        int windowStart = before.indexOf("<order id=\"11\"");
        int windowEnd = before.indexOf("<order id=\"1011\"");
        assertEquals(before.substring(0, windowStart), after.substring(0, windowStart), "bytes before the window are unchanged");
        String tail = before.substring(before.lastIndexOf("</order>", windowEnd) + "</order>".length());
        assertTrue(after.endsWith(tail), "bytes after the window are unchanged");
        assertTrue(fx(view::statusForTesting).startsWith("Wrote orders.xml"), fx(view::statusForTesting));
        assertEquals("/orders[1] · children 11–1,010", fx(window::labelForTesting), "the same window again");
        assertEquals("closed", fx(() -> window.contextForTesting().getDocument().getRootElement()
                .getChildElements().getFirst().getAttribute("st")));
        assertFalse(fx(view::changedBarShownForTesting), "our own write is no external change");

        // next window and back
        fxRun(window::onNext);
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> fx(() -> window.labelForTesting().contains("1,011–2,010")));
        fxRun(window::onPrevious);
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> fx(() -> window.labelForTesting().contains("11–1,010")));

        // discard drops edits
        XmlEditorContext again = fx(window::contextForTesting);
        fxRun(() -> {
            XmlElement el = again.getDocument().getRootElement().getChildElements().get(1);
            again.executeCommand(new SetAttributeCommand(el, "st", "x"));
            window.modelChangedForTesting();
        });
        fxRun(window::onDiscard);
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> fx(() -> window.contextForTesting() != again
                && window.contextForTesting() != null && !window.isDirty()));
        assertFalse(Files.readString(file).contains("st=\"x\""));
    }

    @Test
    void theRootFitsAndTheWindowIsTheWholeDocument(@TempDir Path dir) throws Exception {
        Path file = orders(dir, 10);
        LargeFileView view = open(file);
        LargeFileWindowPane window = fx(view::windowPaneForTesting);
        fxRun(view::selectGraphicForTesting);
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> fx(() -> window.contextForTesting() != null));
        assertEquals("/orders[1] (whole document)", fx(window::labelForTesting));
        XmlEditorContext context = fx(window::contextForTesting);
        fxRun(() -> {
            XmlElement third = context.getDocument().getRootElement().getChildElements().get(2);
            context.executeCommand(new SetAttributeCommand(third, "id", "three"));
            window.modelChangedForTesting();
        });
        LargeFileView.pendingAnswer = question -> LargeFileView.PendingChoice.DISCARD;
        assertTrue(fx(view::confirmDiscard));
        fxRun(window::onApply);
        waitIdle(view);
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> Files.readString(file).contains("id=\"three\""));
        assertTrue(Files.readString(file).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<orders"),
                "the prolog stays");
    }

    @Test
    void statisticsExportAndChangeDetection(@TempDir Path dir) throws Exception {
        Path file = orders(dir, 1_200);
        LargeFileView view = open(file);
        LargeFileStatisticsPane stats = fx(view::statisticsPaneForTesting);
        fxRun(stats::onScan);
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> fx(() -> stats.resultForTesting() != null));
        var result = fx(stats::resultForTesting);
        assertEquals(1 + 2 * 1_200, result.elements());
        assertEquals("/orders/order/amount", result.paths().get(2).path());

        // export all 1,200 children, not only the loaded page
        LargeFileStructurePane pane = fx(view::structurePaneForTesting);
        fxRun(view::selectStructureForTesting);
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> fx(() -> view.recordsForTesting().size() == 1000));
        Path csv = dir.resolve("orders.csv");
        LargeFileStructurePane.exportTarget = format -> csv.toFile();
        fxRun(() -> pane.onExportCsv());
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> Files.exists(csv)
                && fx(() -> view.statusForTesting().startsWith("Exported 1,200 records")));
        assertEquals(1_201, Files.readAllLines(csv).size());

        Files.writeString(file, "<orders/>\n");
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5000));
        fxRun(view::checkForChangesForTesting);
        assertTrue(fx(view::changedBarShownForTesting));
        fxRun(view::onReload);
        waitIdle(view);
        assertFalse(fx(view::changedBarShownForTesting));
        assertTrue(fx(view::infoForTesting).contains(" 1 lines"), fx(view::infoForTesting));
    }
}
