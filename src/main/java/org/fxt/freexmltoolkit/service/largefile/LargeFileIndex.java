package org.fxt.freexmltoolkit.service.largefile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A memory-light index of a text file of any size, for the Large File Viewer: the file is read
 * once and every {@value #BLOCK}th display row's byte offset and line number is kept; any row is
 * then read from disk on demand. A display row is a line, or a piece of at most
 * {@value #MAX_ROW_BYTES} bytes of a longer line (a minified 1-GB document is one line), so no row
 * is ever large. Memory: about 12 bytes per {@value #BLOCK} rows (a 1-GB file with 25 million lines
 * needs about 300 KB).
 *
 * <p>Single-byte encodings and UTF-8 only (bytes {@code \n} never appear inside a character); a
 * UTF-16 file is refused. Thread-safe for reading after construction.
 */
public final class LargeFileIndex implements AutoCloseable {

    /** Rows per index entry. */
    static final int BLOCK = 1024;
    /** A line longer than this is shown as several rows. */
    public static final int MAX_ROW_BYTES = 4096;
    private static final int READ_BUFFER = 1 << 20;
    private static final Pattern XML_ENCODING = Pattern.compile("<\\?xml[^>]*encoding\\s*=\\s*[\"']([^\"']+)[\"']");

    /** One display row. */
    public record Row(long index, long line, boolean continuation, long offset, String text) {
    }

    private final Path path;
    private final long size;
    private final Charset charset;
    private final int bomLength;
    private final long rows;
    private final long lines;
    private final long[] blockOffsets;
    private final long[] blockLines;
    private final boolean[] blockContinuation;
    private final FileChannel channel;
    private final Map<Long, List<Row>> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, List<Row>> eldest) {
            return size() > 8;
        }
    };

    private LargeFileIndex(Path path, long size, Charset charset, int bomLength, long rows, long lines,
                           long[] blockOffsets, long[] blockLines, boolean[] blockContinuation) throws IOException {
        this.path = path;
        this.size = size;
        this.charset = charset;
        this.bomLength = bomLength;
        this.rows = rows;
        this.lines = lines;
        this.blockOffsets = blockOffsets;
        this.blockLines = blockLines;
        this.blockContinuation = blockContinuation;
        this.channel = FileChannel.open(path, StandardOpenOption.READ);
    }

    /**
     * Reads the file once and builds the index.
     *
     * @param progress  receives the bytes read so far (may be {@code null})
     * @param cancelled polled per megabyte; {@code true} throws {@link CancellationException} (may be {@code null})
     * @throws IOException if the file cannot be read, or it is UTF-16/32
     */
    public static LargeFileIndex build(Path path, LongConsumer progress, BooleanSupplier cancelled) throws IOException {
        long size = Files.size(path);
        byte[] head = new byte[(int) Math.min(size, 4096)];
        try (InputStream in = Files.newInputStream(path)) {
            in.readNBytes(head, 0, head.length);
        }
        int bom = 0;
        Charset charset = StandardCharsets.UTF_8;
        if (head.length >= 2 && (head[0] == (byte) 0xFE && head[1] == (byte) 0xFF || head[0] == (byte) 0xFF && head[1] == (byte) 0xFE)
                || head.length >= 4 && head[0] == 0 && head[1] == 0) {
            throw new IOException("UTF-16 and UTF-32 files are not supported by the Large File Viewer.");
        }
        if (head.length >= 3 && head[0] == (byte) 0xEF && head[1] == (byte) 0xBB && head[2] == (byte) 0xBF) {
            bom = 3;
        } else {
            Matcher declared = XML_ENCODING.matcher(new String(head, StandardCharsets.ISO_8859_1));
            if (declared.find()) {
                charset = declaredCharset(declared.group(1));
            }
        }

        List<long[]> entries = new ArrayList<>();         // {offset, line, continuation}
        long rowCount;
        long lineCount = 1;
        long rowStart = bom;
        long position = 0;
        try (FileChannel in = FileChannel.open(path, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(READ_BUFFER);
            long lineOfRow = 1;
            entries.add(new long[] {rowStart, 1, 0});
            rowCount = 1;
            long bytesInRow = 0;
            int read;
            while ((read = in.read(buffer)) > 0) {
                if (cancelled != null && cancelled.getAsBoolean()) {
                    throw new CancellationException("Indexing cancelled");
                }
                byte[] bytes = buffer.array();
                for (int i = 0; i < read; i++) {
                    long offset = position + i;
                    if (offset < bom) {
                        continue;
                    }
                    byte b = bytes[i];
                    if (b == '\n') {
                        lineCount++;
                        // the next row starts after the newline
                        if (offset + 1 < size) {
                            if (rowCount % BLOCK == 0) {
                                entries.add(new long[] {offset + 1, lineCount, 0});
                            }
                            rowCount++;
                        }
                        bytesInRow = 0;
                        lineOfRow = lineCount;
                    } else {
                        bytesInRow++;
                        // split long lines, never inside a UTF-8 character (continuation bytes 10xxxxxx)
                        if (bytesInRow > MAX_ROW_BYTES && (b & 0xC0) != 0x80) {
                            if (rowCount % BLOCK == 0) {
                                entries.add(new long[] {offset, lineOfRow, 1});
                            }
                            rowCount++;
                            bytesInRow = 1;
                        }
                    }
                }
                position += read;
                buffer.clear();
                if (progress != null) {
                    progress.accept(position);
                }
            }
        }
        long[] offsets = new long[entries.size()];
        long[] lineNumbers = new long[entries.size()];
        boolean[] continued = new boolean[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            offsets[i] = entries.get(i)[0];
            lineNumbers[i] = entries.get(i)[1];
            continued[i] = entries.get(i)[2] == 1;
        }
        long lineTotal = size > bom && endsWithNewline(path, size) ? lineCount - 1 : lineCount;
        return new LargeFileIndex(path, size, charset, bom, Math.max(1, rowCount), Math.max(1, lineTotal), offsets,
                lineNumbers, continued);
    }

    private static boolean endsWithNewline(Path path, long size) throws IOException {
        try (FileChannel in = FileChannel.open(path, StandardOpenOption.READ)) {
            ByteBuffer last = ByteBuffer.allocate(1);
            in.read(last, size - 1);
            return last.get(0) == '\n';
        }
    }

    private static Charset declaredCharset(String name) throws IOException {
        String upper = name.toUpperCase(Locale.ROOT);
        if (upper.startsWith("UTF-16") || upper.startsWith("UTF-32") || upper.startsWith("UCS")) {
            throw new IOException("UTF-16 and UTF-32 files are not supported by the Large File Viewer.");
        }
        try {
            Charset charset = Charset.forName(name);
            float bytesPerChar = charset.newEncoder().maxBytesPerChar();
            return bytesPerChar <= 1.0f || charset.equals(StandardCharsets.UTF_8) ? charset : StandardCharsets.UTF_8;
        } catch (RuntimeException e) {
            return StandardCharsets.UTF_8;
        }
    }

    // ---- reading -----------------------------------------------------------------------------

    /** @return the rows {@code [from, from + count)} (fewer at the end) */
    public List<Row> rows(long from, int count) throws IOException {
        List<Row> result = new ArrayList<>(count);
        long row = Math.max(0, from);
        long end = Math.min(rows, from + count);
        while (row < end) {
            long block = row / BLOCK;
            List<Row> blockRows = block(block);
            int start = (int) (row - block * BLOCK);
            for (int i = start; i < blockRows.size() && row < end; i++, row++) {
                result.add(blockRows.get(i));
            }
            if (start >= blockRows.size()) {
                break;
            }
        }
        return result;
    }

    /** @return one row */
    public Row row(long index) throws IOException {
        List<Row> one = rows(index, 1);
        return one.isEmpty() ? new Row(index, 1, false, size, "") : one.get(0);
    }

    private List<Row> block(long block) throws IOException {
        synchronized (cache) {
            List<Row> cached = cache.get(block);
            if (cached != null) {
                return cached;
            }
        }
        List<Row> loaded = readBlock(block);
        synchronized (cache) {
            cache.put(block, loaded);
        }
        return loaded;
    }

    /** Reads the rows of one block from disk, splitting lines exactly like {@link #build}. */
    private List<Row> readBlock(long block) throws IOException {
        int b = (int) block;
        long start = blockOffsets[b];
        long endOffset = b + 1 < blockOffsets.length ? blockOffsets[b + 1] : size;
        long line = blockLines[b];
        boolean continuation = blockContinuation[b];
        int length = (int) (endOffset - start);
        ByteBuffer buffer = ByteBuffer.allocate(length);
        synchronized (channel) {
            int at = 0;
            while (at < length) {
                int n = channel.read(buffer, start + at);
                if (n < 0) {
                    break;
                }
                at += n;
            }
        }
        byte[] bytes = buffer.array();
        List<Row> result = new ArrayList<>(BLOCK);
        long rowIndex = block * BLOCK;
        int rowStart = 0;
        int bytesInRow = 0;
        for (int i = 0; i < length; i++) {
            byte c = bytes[i];
            if (c == '\n') {
                result.add(new Row(rowIndex++, line, continuation, start + rowStart, decode(bytes, rowStart, i)));
                line++;
                continuation = false;
                rowStart = i + 1;
                bytesInRow = 0;
            } else {
                bytesInRow++;
                if (bytesInRow > MAX_ROW_BYTES && (c & 0xC0) != 0x80) {
                    result.add(new Row(rowIndex++, line, continuation, start + rowStart, decode(bytes, rowStart, i)));
                    continuation = true;
                    rowStart = i;
                    bytesInRow = 1;
                }
            }
        }
        if (rowStart < length || result.size() < BLOCK && rowIndex < rows) {
            result.add(new Row(rowIndex, line, continuation, start + rowStart, decode(bytes, rowStart, length)));
        }
        return result;
    }

    private String decode(byte[] bytes, int from, int to) {
        int end = to;
        if (end > from && bytes[end - 1] == '\r') {
            end--;
        }
        return new String(bytes, from, end - from, charset);
    }

    /**
     * @param line a 1-based line number
     * @return the first row of that line (clamped to the file)
     */
    public long rowOfLine(long line) throws IOException {
        long target = Math.max(1, Math.min(line, lines));
        // the last block that starts before the target line: the line's first row is in it or later
        int lo = 0;
        int hi = blockLines.length - 1;
        int block = 0;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (blockLines[mid] < target) {
                block = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        for (long b = block; b <= (rows - 1) / BLOCK; b++) {
            for (Row row : block(b)) {
                if (row.line() == target && !row.continuation()) {
                    return row.index();
                }
                if (row.line() > target) {
                    return Math.max(0, row.index() - 1);
                }
            }
        }
        return rows - 1;
    }

    /** @return the byte offset where a row starts */
    public long offsetOfRow(long row) throws IOException {
        return row(Math.max(0, Math.min(row, rows - 1))).offset();
    }

    /**
     * @param offset a byte offset in the file
     * @return the row that contains it
     */
    public long rowOfOffset(long offset) throws IOException {
        int block = Arrays.binarySearch(blockOffsets, offset);
        if (block < 0) {
            block = -block - 2;
        }
        block = Math.max(0, block);
        long found = (long) block * BLOCK;
        for (Row row : block(block)) {
            if (row.offset() > offset) {
                break;
            }
            found = row.index();
        }
        return found;
    }

    /** A search hit: the row, the 1-based line, the character range in the row's text. */
    public record Hit(long row, long line, int start, int end, String text) {
    }

    /**
     * Searches every row (in a background thread). A match that would span two rows of a split long
     * line is not found.
     *
     * @param maxHits   stop after this many hits
     * @param progress  receives the bytes searched so far (may be {@code null})
     * @param cancelled polled per block (may be {@code null})
     * @return the hits in file order
     */
    public List<Hit> search(Pattern pattern, int maxHits, LongConsumer progress, BooleanSupplier cancelled)
            throws IOException {
        List<Hit> hits = new ArrayList<>();
        for (long b = 0; b < blockOffsets.length && hits.size() < maxHits; b++) {
            if (cancelled != null && cancelled.getAsBoolean()) {
                throw new CancellationException("Search cancelled");
            }
            for (Row row : readBlock(b)) {
                Matcher matcher = pattern.matcher(row.text());
                while (matcher.find() && hits.size() < maxHits) {
                    if (matcher.end() == matcher.start() && matcher.end() >= row.text().length()) {
                        break;
                    }
                    hits.add(hit(row, matcher.start(), matcher.end()));
                }
            }
            if (progress != null) {
                progress.accept(b + 1 < blockOffsets.length ? blockOffsets[(int) b + 1] : size);
            }
        }
        return hits;
    }

    /** A hit with the row text cut to a window around the match (rows can be 4 KB). */
    private static Hit hit(Row row, int start, int end) {
        String text = row.text();
        if (text.length() <= 400) {
            return new Hit(row.index(), row.line(), start, end, text);
        }
        int from = Math.max(0, start - 100);
        int to = Math.min(text.length(), Math.max(end, start) + 300);
        return new Hit(row.index(), row.line(), start - from, end - from, text.substring(from, to));
    }

    /**
     * Reads raw bytes of the file (for extracting an element).
     *
     * @return at most {@code length} bytes from {@code offset}
     */
    public byte[] read(long offset, int length) throws IOException {
        int n = (int) Math.max(0, Math.min(length, size - offset));
        ByteBuffer buffer = ByteBuffer.allocate(n);
        synchronized (channel) {
            int at = 0;
            while (at < n) {
                int r = channel.read(buffer, offset + at);
                if (r < 0) {
                    break;
                }
                at += r;
            }
        }
        return buffer.array();
    }

    public Path path() {
        return path;
    }

    /** @return the file size in bytes */
    public long size() {
        return size;
    }

    /** @return the number of display rows */
    public long rowCount() {
        return rows;
    }

    /** @return the number of lines */
    public long lineCount() {
        return lines;
    }

    public Charset charset() {
        return charset;
    }

    /** @return the length of the byte order mark (0 or 3) */
    public int bomLength() {
        return bomLength;
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
