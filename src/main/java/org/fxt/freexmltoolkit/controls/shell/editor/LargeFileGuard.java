package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.Locale;

/**
 * Decides whether a file is small enough to open in the text editor. The editor holds the
 * whole document in memory several times over (the file as a {@code String}, the RichTextFX
 * paragraph model, syntax-highlighting spans), so a file of a few hundred MB exhausts even a
 * large heap and freezes the UI long before. Telemetry showed exactly that: a 294 MB XML file
 * ended in {@code OutOfMemoryError}s and UI stalls of up to 19 s.
 */
public final class LargeFileGuard {

    /** From this size on, opening asks for confirmation first. */
    public static final long WARN_BYTES = 50L * 1024 * 1024;

    /**
     * Heap needed per byte of file (String, paragraph model, styles, folding, parsed model).
     * Telemetry: a 294 MB file did not fit into a 4 GB heap, so the real factor is above 14.
     */
    static final long HEAP_BYTES_PER_FILE_BYTE = 16;

    /** What to do with a file of a given size. */
    public enum Verdict {
        /** Open right away. */
        OK,
        /** Open only after the user confirmed (editing will be slow). */
        WARN,
        /** Do not open: it would not fit into the maximum heap. */
        REFUSE
    }

    private LargeFileGuard() {
    }

    /**
     * @param fileBytes the file size; negative when unknown (treated as {@link Verdict#OK})
     * @param maxHeap   the maximum heap ({@link Runtime#maxMemory()}); {@link Long#MAX_VALUE} when unbounded
     * @return the verdict
     */
    public static Verdict check(long fileBytes, long maxHeap) {
        if (fileBytes < 0) {
            return Verdict.OK;
        }
        if (maxHeap > 0 && maxHeap != Long.MAX_VALUE
                && fileBytes > maxHeap / HEAP_BYTES_PER_FILE_BYTE) {
            return Verdict.REFUSE;
        }
        return fileBytes >= WARN_BYTES ? Verdict.WARN : Verdict.OK;
    }

    /** @return the largest file that fits the given maximum heap, in bytes */
    public static long largestOpenable(long maxHeap) {
        return maxHeap / HEAP_BYTES_PER_FILE_BYTE;
    }

    /** @return a size such as {@code 294 MB} or {@code 1.5 GB} */
    public static String formatSize(long bytes) {
        double mb = bytes / (1024.0 * 1024.0);
        if (mb >= 1024) {
            return String.format(Locale.ROOT, "%.1f GB", mb / 1024.0);
        }
        return String.format(Locale.ROOT, "%.0f MB", mb);
    }
}
