package org.fxt.freexmltoolkit.service.largefile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests {@link LargeFileIndex}: rows, long lines, encodings, positions, search. */
@DisplayName("LargeFileIndex")
class LargeFileIndexTest {

    @TempDir
    Path dir;

    private LargeFileIndex index(String name, byte[] bytes) throws IOException {
        Path file = dir.resolve(name);
        Files.write(file, bytes);
        return LargeFileIndex.build(file, null, null);
    }

    @Test
    @DisplayName("many lines across blocks: counts, random access, line ↔ row ↔ offset")
    void manyLines() throws Exception {
        StringBuilder text = new StringBuilder();
        for (int i = 1; i <= 5000; i++) {
            text.append("<row n=\"").append(i).append("\"/>\n");
        }
        try (LargeFileIndex index = index("many.xml", text.toString().getBytes(StandardCharsets.UTF_8))) {
            assertEquals(5000, index.rowCount());
            assertEquals(5000, index.lineCount());
            assertEquals("<row n=\"1\"/>", index.row(0).text());
            assertEquals("<row n=\"2049\"/>", index.row(2048).text());
            assertEquals(2049, index.row(2048).line());
            List<LargeFileIndex.Row> page = index.rows(1020, 10);
            assertEquals(10, page.size());
            assertEquals("<row n=\"1030\"/>", page.get(9).text(), "a page across a block boundary");
            assertEquals(4999, index.rowOfLine(5000));
            assertEquals(0, index.rowOfLine(-3));
            long offset = index.offsetOfRow(3000);
            assertEquals(text.indexOf("<row n=\"3001\"/>"), offset);
            assertEquals(3000, index.rowOfOffset(offset + 3));
            assertTrue(index.rows(4998, 10).size() == 2);
        }
    }

    @Test
    @DisplayName("a long line is split into rows of at most 4 KB, never inside a UTF-8 character")
    void longLine() throws Exception {
        StringBuilder line = new StringBuilder("<a>");
        while (line.length() < 3 * LargeFileIndex.MAX_ROW_BYTES) {
            line.append("äöü€x");
        }
        line.append("</a>");
        String text = "<root>\n" + line + "\nend";
        try (LargeFileIndex index = index("long.xml", text.getBytes(StandardCharsets.UTF_8))) {
            assertEquals(3, index.lineCount());
            assertTrue(index.rowCount() >= 6, "the long line is several rows: " + index.rowCount());
            StringBuilder joined = new StringBuilder();
            long rows = index.rowCount();
            for (LargeFileIndex.Row row : index.rows(1, (int) rows - 2)) {
                assertEquals(2, row.line());
                assertTrue(row.text().getBytes(StandardCharsets.UTF_8).length <= LargeFileIndex.MAX_ROW_BYTES + 3);
                joined.append(row.text());
            }
            assertEquals(line.toString(), joined.toString(), "no character lost or broken");
            assertFalse(index.row(1).continuation());
            assertTrue(index.row(2).continuation());
            assertEquals("end", index.row(rows - 1).text());
            assertEquals(3, index.row(rows - 1).line());
            assertEquals(rows - 1, index.rowOfLine(3));
            assertEquals(1, index.rowOfLine(2));
        }
    }

    @Test
    @DisplayName("BOM, CRLF, declared Latin-1, no final newline, empty file, UTF-16 refused")
    void encodings() throws Exception {
        try (LargeFileIndex bom = index("bom.xml", concat(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF},
                "<a>\r\n  <b/>\r\n</a>\r\n".getBytes(StandardCharsets.UTF_8)))) {
            assertEquals(3, bom.rowCount());
            assertEquals("<a>", bom.row(0).text());
            assertEquals("  <b/>", bom.row(1).text());
            assertEquals(3, bom.bomLength());
        }
        try (LargeFileIndex latin = index("latin.xml",
                "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n<a>Grüße</a>".getBytes(StandardCharsets.ISO_8859_1))) {
            assertEquals("<a>Grüße</a>", latin.row(1).text());
            assertEquals(StandardCharsets.ISO_8859_1, latin.charset());
        }
        try (LargeFileIndex empty = index("empty.xml", new byte[0])) {
            assertEquals(1, empty.rowCount());
            assertEquals("", empty.row(0).text());
        }
        assertThrows(IOException.class, () -> index("utf16.xml", "<a/>".getBytes(StandardCharsets.UTF_16)));
    }

    @Test
    @DisplayName("search finds every match with line and column; cancellation")
    void search() throws Exception {
        StringBuilder text = new StringBuilder();
        for (int i = 1; i <= 3000; i++) {
            text.append(i % 1000 == 0 ? "<hit id=\"" + i + "\"/>" : "<x/>").append('\n');
        }
        try (LargeFileIndex index = index("search.xml", text.toString().getBytes(StandardCharsets.UTF_8))) {
            List<LargeFileIndex.Hit> hits = index.search(Pattern.compile("hit id=\"(\\d+)\""), 100, null, null);
            assertEquals(3, hits.size());
            assertEquals(1000, hits.get(0).line());
            assertEquals(999, hits.get(0).row());
            assertEquals(1, hits.get(0).start());
            assertEquals(2, index.search(Pattern.compile("hit"), 2, null, null).size(), "stops at the limit");
            assertThrows(CancellationException.class, () -> index.search(Pattern.compile("x"), 10, null, () -> true));
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] all = new byte[a.length + b.length];
        System.arraycopy(a, 0, all, 0, a.length);
        System.arraycopy(b, 0, all, a.length, b.length);
        return all;
    }
}
