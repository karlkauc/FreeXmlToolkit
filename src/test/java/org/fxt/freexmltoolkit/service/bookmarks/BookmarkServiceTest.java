package org.fxt.freexmltoolkit.service.bookmarks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.fxt.freexmltoolkit.debugger.LineShift;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BookmarkServiceTest {

    @TempDir
    Path dir;
    Path store;
    BookmarkService service;
    String key;

    @BeforeEach
    void setUp() throws Exception {
        store = dir.resolve("bookmarks.json");
        service = new BookmarkService(store);
        Path doc = dir.resolve("a.xml");
        Files.writeString(doc, "x");
        key = BookmarkService.keyOf(doc);
    }

    private List<Integer> lines(String k) {
        return service.forKey(k).stream().map(Bookmark::line).toList();
    }

    @Test
    void toggleSetsAndRemoves() {
        assertTrue(service.toggle(key, 5, "  <a/>  "));
        assertTrue(service.toggle(key, 2, "<b/>"));
        assertEquals(List.of(2, 5), lines(key));
        assertEquals("<a/>", service.at(key, 5).orElseThrow().lineText());
        assertFalse(service.toggle(key, 5, "<a/>"));
        assertEquals(List.of(2), lines(key));
        assertFalse(service.toggle(key, 2, ""));
        assertTrue(service.keys().isEmpty());
    }

    @Test
    void renameAndBlankNameClearsIt() {
        service.toggle(key, 3, "x");
        assertTrue(service.rename(key, 3, "  Header  "));
        assertEquals("Header", service.at(key, 3).orElseThrow().name());
        service.rename(key, 3, " ");
        assertNull(service.at(key, 3).orElseThrow().name());
        assertEquals("Line 3", service.at(key, 3).orElseThrow().label());
        assertFalse(service.rename(key, 4, "nope"));
    }

    @Test
    void numbersAreUniqueAcrossFiles() {
        String other = BookmarkService.keyOf(dir.resolve("b.xml"));
        service.setNumber(key, 4, 3, "four");
        assertEquals(4, service.byNumber(3).orElseThrow().bookmark().line());
        service.setNumber(other, 7, 3, "seven");
        var located = service.byNumber(3).orElseThrow();
        assertEquals(other, located.key());
        assertEquals(0, service.at(key, 4).orElseThrow().number(), "the first one lost the number");
        service.setNumber(other, 7, 0, null);
        assertTrue(service.byNumber(3).isEmpty());
        assertTrue(service.at(other, 7).isPresent(), "the bookmark stays without its number");
    }

    @Test
    void insertedLinesMoveBookmarksDown() {
        service.toggle(key, 3, "a");
        service.toggle(key, 10, "b");
        // two lines inserted at the start of line 5
        assertTrue(service.shiftLines(key, new LineShift(5, 0, 0, 2)));
        assertEquals(List.of(3, 12), lines(key));
        // a new line typed at the very start of line 3 pushes its bookmark down
        service.shiftLines(key, new LineShift(3, 0, 0, 1));
        assertEquals(List.of(4, 13), lines(key));
    }

    @Test
    void deletedLineKeepsTheBookmarkAtTheDeletionAndMerges() {
        service.toggle(key, 4, "a");
        service.rename(key, 4, "kept");
        service.toggle(key, 5, "b");
        service.setNumber(key, 5, 2, "b");
        // lines 4 and 5 merged into line 3 (removed text from line 3 through line 5)
        service.shiftLines(key, new LineShift(3, 10, 2, 0));
        List<Bookmark> list = service.forKey(key);
        assertEquals(1, list.size());
        assertEquals(3, list.get(0).line());
        assertEquals("kept", list.get(0).name());
        assertEquals(2, list.get(0).number());
    }

    @Test
    void neutralEditDoesNothing() {
        service.toggle(key, 2, "a");
        assertFalse(service.shiftLines(key, new LineShift(2, 3, 0, 0)));
    }

    @Test
    void clearAndClearAll() {
        String other = BookmarkService.keyOf(dir.resolve("b.xml"));
        service.toggle(key, 1, "a");
        service.toggle(key, 2, "b");
        service.toggle(other, 1, "c");
        assertEquals(3, service.count());
        assertEquals(2, service.clear(key));
        assertEquals(List.of(other), service.keys());
        service.clearAll();
        assertTrue(service.keys().isEmpty());
    }

    @Test
    void survivesARestart() {
        service.toggle(key, 7, "<seven/>");
        service.rename(key, 7, "Seven");
        service.setNumber(key, 7, 1, null);
        service.toggle(BookmarkService.untitledKey("1"), 1, "never saved");
        service.flush();

        BookmarkService again = new BookmarkService(store);
        Bookmark b = again.at(key, 7).orElseThrow();
        assertEquals("Seven", b.name());
        assertEquals(1, b.number());
        assertEquals("<seven/>", b.lineText());
        assertEquals(List.of(key), again.keys());
        assertTrue(again.forKey(BookmarkService.untitledKey("1")).isEmpty());
    }

    @Test
    void savesOnItsOwnAfterAChange() throws Exception {
        service.toggle(key, 1, "a");
        long deadline = System.currentTimeMillis() + 5000;
        while (!Files.exists(store) && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(Files.readString(store).contains("\"line\": 1"));
    }

    @Test
    void unreadableFileStartsEmpty() throws Exception {
        Files.writeString(store, "{ not json");
        assertTrue(new BookmarkService(store).keys().isEmpty());
    }

    @Test
    void renamePathMovesUntitledBookmarksToTheFile() {
        String untitled = BookmarkService.untitledKey("7");
        service.toggle(untitled, 2, "a");
        assertTrue(service.keys().isEmpty(), "untitled documents are not listed");
        service.renamePath(untitled, key);
        assertEquals(List.of(2), lines(key));
    }

    @Test
    void copyPathLeavesTheNumbers() {
        String other = BookmarkService.keyOf(dir.resolve("b.xml"));
        service.setNumber(key, 2, 5, "x");
        service.copyPath(key, other);
        assertEquals(0, service.at(other, 2).orElseThrow().number());
        assertEquals(5, service.at(key, 2).orElseThrow().number());
    }

    @Test
    void missingFilesAreFoundAndRemoved() {
        String gone = BookmarkService.keyOf(dir.resolve("gone.xml"));
        service.toggle(gone, 1, "a");
        service.toggle(key, 1, "b");
        assertEquals(List.of(gone), service.missingFiles());
        assertEquals(1, service.removeMissingFiles());
        assertEquals(List.of(key), service.keys());
    }

    @Test
    void reanchorAfterAnExternalChange() {
        service.toggle(key, 2, "<b/>");
        service.toggle(key, 3, "<c/>");
        var result = service.reanchor(key, "<new/>\n<a/>\n<x/>\n<b/>\n");
        assertEquals(1, result.moved());
        assertEquals(1, result.orphaned());
        assertEquals(4, service.forKey(key).stream().filter(b -> !b.orphaned()).findFirst().orElseThrow().line());
        assertTrue(service.at(key, 3).orElseThrow().orphaned());
        // saving the document takes the line texts as they are now
        service.updateLineTexts(key, "<new/>\n<a/>\n<x/>\n<b/>\n");
        assertFalse(service.at(key, 3).orElseThrow().orphaned());
        assertEquals("<x/>", service.at(key, 3).orElseThrow().lineText());
    }

    @Test
    void listenersHearTheChangedDocument() {
        List<String> heard = new ArrayList<>();
        service.addListener(heard::add);
        service.toggle(key, 1, "a");
        service.remove(key, 1);
        assertEquals(List.of(key, key), heard);
    }
}
