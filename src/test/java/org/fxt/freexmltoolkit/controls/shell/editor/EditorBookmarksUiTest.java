package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.shared.ScrollMarkerStrip;
import org.fxt.freexmltoolkit.service.bookmarks.Bookmark;
import org.fxt.freexmltoolkit.service.bookmarks.BookmarkService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** Line bookmarks in the editor: gutter, following edits, navigation, numbers, panel, re-anchoring. */
@ExtendWith(ApplicationExtension.class)
class EditorBookmarksUiTest {

    private EditorHost host;
    @TempDir
    Path tmp;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        host.setBookmarkPrompts((header, current) -> Optional.of("Named"), message -> true);
        stage.setScene(new Scene(host, 900, 600));
        stage.show();
    }

    @AfterEach
    void cleanUp() {
        BookmarkService service = BookmarkService.getInstance();
        service.keys().stream().filter(k -> k.startsWith(tmp.toString())).forEach(service::clear);
        for (int n = 1; n <= 9; n++) {
            int digit = n;
            service.byNumber(n).filter(l -> l.key().startsWith(tmp.toString()))
                    .ifPresent(l -> service.setNumber(l.key(), l.bookmark().line(), 0, null));
            assertTrue(service.byNumber(digit).map(l -> !l.key().startsWith(tmp.toString())).orElse(true));
        }
    }

    private static <T> T fx(java.util.concurrent.Callable<T> c) {
        return WaitForAsyncUtils.waitForAsyncFx(5000, c);
    }

    private static void fxRun(Runnable r) {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            r.run();
            return null;
        });
    }

    private Path file(String name, String text) throws Exception {
        Path p = tmp.resolve(name);
        Files.writeString(p, text);
        return p;
    }

    private void open(Path p) throws Exception {
        fxRun(() -> host.openFile(p));
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> fx(() -> {
            CodeArea ca = codeArea();
            return ca != null && host.getActiveDocument().map(d -> p.equals(d.getPath())).orElse(false)
                    && ca.getParagraphs().size() > 2 && slot(0) != null;
        }));
    }

    /** FX thread only. */
    private CodeArea codeArea() {
        return host.tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et ? et.view.getCodeArea() : null;
    }

    /** FX thread only. */
    private Node slot(int paragraph) {
        CodeArea ca = codeArea();
        if (ca == null || paragraph >= ca.getParagraphs().size()) {
            return null;
        }
        Node graphic = ca.getParagraphGraphic(paragraph);
        return graphic == null ? null : graphic.lookup("." + BookmarkGutter.SLOT_STYLE_CLASS);
    }

    private boolean marked(int paragraph) {
        return fx(() -> {
            Node s = slot(paragraph);
            return s != null && s.getStyleClass().contains(BookmarkGutter.MARKED_STYLE_CLASS);
        });
    }

    private static List<Integer> lines(Path p) {
        return BookmarkService.getInstance().forKey(BookmarkService.keyOf(p)).stream().map(Bookmark::line).toList();
    }

    private int caretLine() {
        return fx(() -> codeArea().getCurrentParagraph() + 1);
    }

    private static final String XML = "<root>\n  <a/>\n  <b/>\n  <c/>\n  <d/>\n</root>\n";

    @Test
    void toggleAtCaretMarksTheLineAndFollowsEdits() throws Exception {
        Path p = file("follow.xml", XML);
        open(p);
        fxRun(() -> {
            codeArea().moveTo(2, 0);
            assertTrue(host.toggleBookmarkAtCaret());
        });
        assertEquals(List.of(3), lines(p));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> marked(2));
        assertFalse(marked(1));

        // a new line at the very start of the document pushes it down
        fxRun(() -> codeArea().insertText(0, "<?xml version=\"1.0\"?>\n"));
        assertEquals(List.of(4), lines(p));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> marked(3));

        fxRun(() -> {
            codeArea().moveTo(3, 0);
            host.toggleBookmarkAtCaret();
        });
        assertTrue(lines(p).isEmpty());
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !marked(3));
    }

    @Test
    void nextAndPreviousWrapWithinTheDocument() throws Exception {
        Path p = file("nav.xml", XML);
        open(p);
        BookmarkService.getInstance().toggle(BookmarkService.keyOf(p), 2, "<a/>");
        BookmarkService.getInstance().toggle(BookmarkService.keyOf(p), 5, "<d/>");
        fxRun(() -> codeArea().moveTo(0, 0));
        fxRun(host::nextBookmark);
        assertEquals(2, caretLine());
        fxRun(host::nextBookmark);
        assertEquals(5, caretLine());
        fxRun(host::nextBookmark);
        assertEquals(2, caretLine(), "past the last one it starts over");
        fxRun(host::previousBookmark);
        assertEquals(5, caretLine());
    }

    @Test
    void numberedBookmarksJumpAcrossFiles() throws Exception {
        Path first = file("first.xml", XML);
        Path second = file("second.xml", XML);
        open(first);
        fxRun(() -> {
            codeArea().moveTo(3, 0);
            assertTrue(host.toggleNumberedBookmark(7));
        });
        assertEquals(7, BookmarkService.getInstance().at(BookmarkService.keyOf(first), 4).orElseThrow().number());
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> fx(() -> slot(3).lookup(".fxt-bookmark-badge") != null));

        open(second);
        assertTrue(fx(() -> host.goToNumberedBookmark(7)));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> fx(() ->
                host.getActiveDocument().map(d -> first.equals(d.getPath())).orElse(false)));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> caretLine() == 4);

        // the same key again takes the number off; the bookmark stays
        fxRun(() -> host.toggleNumberedBookmark(7));
        assertTrue(BookmarkService.getInstance().byNumber(7).isEmpty());
        assertEquals(List.of(4), lines(first));
    }

    @Test
    void renameAndClearUseTheirPrompts() throws Exception {
        Path p = file("rename.xml", XML);
        open(p);
        fxRun(() -> {
            codeArea().moveTo(1, 0);
            assertTrue(host.renameBookmarkAtCaret(), "naming a line without a bookmark sets one");
        });
        assertEquals("Named", BookmarkService.getInstance().at(BookmarkService.keyOf(p), 2).orElseThrow().name());
        BookmarkService.getInstance().toggle(BookmarkService.keyOf(p), 4, "<c/>");
        assertEquals(2, (int) fx(host::clearBookmarksInActive));
        assertTrue(lines(p).isEmpty());
    }

    @Test
    void scrollMarkersListTheBookmarks() throws Exception {
        Path p = file("markers.xml", XML);
        open(p);
        BookmarkService.getInstance().toggle(BookmarkService.keyOf(p), 2, "<a/>");
        BookmarkService.getInstance().toggle(BookmarkService.keyOf(p), 4, "<c/>");
        List<Integer> markerLines = fx(() -> {
            ScrollMarkerStrip strip = (ScrollMarkerStrip) host.lookup(".fxt-scroll-markers");
            assertNotNull(strip);
            return strip.getMarkers().stream().map(ScrollMarkerStrip.Marker::line).toList();
        });
        assertEquals(List.of(2, 4), markerLines);
    }

    @Test
    void openingAChangedFileFindsTheLinesAgain() throws Exception {
        Path p = file("moved.xml", XML);
        String key = BookmarkService.keyOf(p);
        BookmarkService.getInstance().toggle(key, 3, "  <b/>");
        BookmarkService.getInstance().toggle(key, 4, "  <gone/>");
        // two lines inserted above <b/> while the app was closed
        Files.writeString(p, "<root>\n  <new1/>\n  <new2/>\n  <a/>\n  <b/>\n  <c/>\n</root>\n");
        open(p);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> lines(p).contains(5));
        Bookmark orphan = BookmarkService.getInstance().forKey(key).stream().filter(Bookmark::orphaned)
                .findFirst().orElseThrow();
        assertEquals(4, orphan.line());
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> fx(() ->
                slot(3).getStyleClass().contains(BookmarkGutter.ORPHANED_STYLE_CLASS)));
    }

    @Test
    void panelListsBookmarksByFileAndOpensThem() throws Exception {
        Path a = file("panel-a.xml", XML);
        Path b = file("panel-b.xml", XML);
        BookmarkService.getInstance().toggle(BookmarkService.keyOf(a), 2, "<a/>");
        BookmarkService.getInstance().toggle(BookmarkService.keyOf(b), 5, "<d/>");
        BookmarkService.getInstance().rename(BookmarkService.keyOf(b), 5, "Delta");
        BookmarksPanel panel = fx(() -> new BookmarksPanel(host));
        fxRun(() -> panel.setFilter("panel-"));
        List<BookmarksPanel.Row> rows = fx(panel::rows);
        assertEquals(4, rows.size(), "two file headers, two bookmarks: " + rows);
        fxRun(() -> panel.setFilter("delta"));
        rows = fx(panel::rows);
        assertEquals(2, rows.size(), "the name matches: " + rows);
        BookmarksPanel.Row delta = rows.get(1);
        fxRun(() -> panel.openRow(delta));
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> fx(() ->
                host.getActiveDocument().map(d -> b.equals(d.getPath())).orElse(false)
                        && codeArea() != null && codeArea().getParagraphs().size() > 2));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> caretLine() == 5);

        // the list follows the service
        BookmarkService.getInstance().remove(BookmarkService.keyOf(b), 5);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> fx(panel::rows).isEmpty());
    }

    @Test
    void saveAsCopiesTheBookmarksToTheNewFile() throws Exception {
        Path p = file("orig.xml", XML);
        open(p);
        BookmarkService.getInstance().toggle(BookmarkService.keyOf(p), 3, "<b/>");
        Path copy = tmp.resolve("copy.xml");
        Boolean saved = fx(() -> host.fileSync.saveActiveAs(copy)).get(10, TimeUnit.SECONDS);
        assertTrue(saved);
        // (the save may put an "edited with" comment on top: the bookmark moves with its line)
        List<Integer> moved = lines(p);
        assertEquals(1, moved.size());
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> lines(copy).equals(moved));
        fxRun(() -> {
            codeArea().moveTo(0, 0);
            host.toggleBookmarkAtCaret();
        });
        assertEquals(2, lines(copy).size(), "the tab now edits the copy's bookmarks: " + lines(copy));
        assertEquals(moved, lines(p), "the original keeps its bookmarks");
    }

    @Test
    void xqueryEditorShowsTheBookmarkSlot() throws Exception {
        Path p = file("q.xq", "let $a := 1\nreturn\n$a\n");
        open(p);
        fxRun(() -> {
            codeArea().moveTo(1, 0);
            host.toggleBookmarkAtCaret();
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> marked(1));
    }
}
