package org.fxt.freexmltoolkit.debugger;

import java.util.Arrays;
import java.util.Optional;

/**
 * Which stylesheet instruction wrote which part of the principal output: a list of
 * (start offset, module, line) entries, appended by the Saxon thread in output order and read by
 * the UI ("go to the instruction that wrote this"). Each entry covers the text from its offset up
 * to the next entry's.
 */
public final class OutputSourceMap {

    /** Where a piece of output came from. */
    public record Source(String systemId, int line) { }

    private int[] offsets = new int[256];
    private int[] lines = new int[256];
    private String[] systemIds = new String[256];
    private int size;

    /** Saxon thread: the text from {@code offset} on was written by {@code systemId:line}. */
    public synchronized void add(int offset, String systemId, int line) {
        if (line < 1) {
            return;
        }
        if (size > 0 && offsets[size - 1] == offset) {
            size--; // nothing was written by the previous entry: replace it
        }
        if (size > 0 && lines[size - 1] == line && java.util.Objects.equals(systemIds[size - 1], systemId)) {
            return; // the same instruction goes on writing
        }
        if (size == offsets.length) {
            offsets = Arrays.copyOf(offsets, size * 2);
            lines = Arrays.copyOf(lines, size * 2);
            systemIds = Arrays.copyOf(systemIds, size * 2);
        }
        offsets[size] = offset;
        lines[size] = line;
        systemIds[size] = systemId == null ? "" : systemId;
        size++;
    }

    /** @return the instruction that wrote the character at {@code offset}, if known. */
    public synchronized Optional<Source> locate(int offset) {
        if (size == 0 || offset < 0 || offset < offsets[0]) {
            return Optional.empty();
        }
        int i = Arrays.binarySearch(offsets, 0, size, offset);
        int index = i >= 0 ? i : -i - 2;
        return Optional.of(new Source(systemIds[index], lines[index]));
    }

    /** @return the number of entries (tests). */
    public synchronized int size() {
        return size;
    }

    public synchronized void clear() {
        size = 0;
    }
}
