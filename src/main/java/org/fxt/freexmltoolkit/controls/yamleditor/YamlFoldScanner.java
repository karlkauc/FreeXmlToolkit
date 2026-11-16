package org.fxt.freexmltoolkit.controls.yamleditor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.TreeMap;

import org.fxt.freexmltoolkit.controls.v2.editor.managers.FoldingRegion;

/**
 * Finds the foldable regions of YAML text by indentation: a line opens a region that holds the
 * lines indented deeper below it (a key without an inline value also holds a block sequence written
 * at its own indentation). Documents separated by {@code ---} fold as a whole. Blank and
 * comment-only lines do not end a region. One pass over the lines; text that is not valid YAML is
 * folded as far as its indentation goes.
 */
public final class YamlFoldScanner {

    private static final int MAX_LABEL = 40;

    private YamlFoldScanner() {
    }

    private record Open(int line, int indent, boolean sequenceItem, boolean keyWithoutValue, String label,
                        int depth) {
    }

    /**
     * @param text     the text
     * @param minLines minimum distance in lines between a region's first and last line
     * @return the regions by 0-based start line
     */
    public static Map<Integer, FoldingRegion> scan(String text, int minLines) {
        Map<Integer, FoldingRegion> regions = new TreeMap<>();
        if (text == null || text.isEmpty()) {
            return regions;
        }
        String[] lines = text.split("\r?\n", -1);
        Deque<Open> stack = new ArrayDeque<>();
        int lastContent = -1;
        int documentStart = -1;
        int lastDocumentContent = -1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int indent = indentOf(line);
            if (indent == line.length() || line.charAt(indent) == '#') {
                continue; // blank or comment-only
            }
            if (indent == 0 && (isMarker(line, "---") || isMarker(line, "..."))) {
                closeAll(stack, lastContent, minLines, regions);
                if (documentStart >= 0) {
                    put(regions, documentStart, lastDocumentContent, "---", 0, minLines);
                }
                documentStart = isMarker(line, "---") ? i : -1;
                lastContent = i;
                lastDocumentContent = i;
                continue;
            }
            boolean sequenceItem = line.startsWith("-", indent)
                    && (indent + 1 == line.length() || line.charAt(indent + 1) == ' ');
            while (!stack.isEmpty()) {
                Open open = stack.peek();
                boolean keepsSequence = open.indent() == indent && open.keyWithoutValue() && sequenceItem
                        && !open.sequenceItem();
                if (open.indent() < indent || keepsSequence) {
                    break;
                }
                stack.pop();
                put(regions, open.line(), lastContent, open.label(), open.depth(), minLines);
            }
            String content = line.substring(indent).stripTrailing();
            stack.push(new Open(i, indent, sequenceItem, endsWithKey(content), label(content), stack.size()));
            lastContent = i;
            lastDocumentContent = i;
        }
        closeAll(stack, lastContent, minLines, regions);
        if (documentStart >= 0) {
            put(regions, documentStart, lastDocumentContent, "---", 0, minLines);
        }
        return regions;
    }

    private static void closeAll(Deque<Open> stack, int lastContent, int minLines, Map<Integer, FoldingRegion> regions) {
        while (!stack.isEmpty()) {
            Open open = stack.pop();
            put(regions, open.line(), lastContent, open.label(), open.depth(), minLines);
        }
    }

    private static void put(Map<Integer, FoldingRegion> regions, int start, int end, String label, int depth,
                            int minLines) {
        if (end - start >= Math.max(1, minLines) && !regions.containsKey(start)) {
            regions.put(start, new FoldingRegion(start, end, label, depth));
        }
    }

    private static int indentOf(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    private static boolean isMarker(String line, String marker) {
        return line.startsWith(marker) && (line.length() == 3 || line.charAt(3) == ' ' || line.charAt(3) == '\t');
    }

    /** @return whether the line ends with a key whose value follows on the next lines ({@code key:}, {@code - key:}) */
    private static boolean endsWithKey(String content) {
        String withoutComment = stripComment(content);
        return withoutComment.endsWith(":");
    }

    private static String stripComment(String content) {
        int hash = content.indexOf(" #");
        return (hash >= 0 ? content.substring(0, hash) : content).stripTrailing();
    }

    private static String label(String content) {
        String text = stripComment(content);
        int colon = text.indexOf(": ");
        if (colon > 0) {
            text = text.substring(0, colon + 1);
        }
        if (text.length() > MAX_LABEL) {
            text = text.substring(0, MAX_LABEL - 1) + "…";
        }
        return text + " …";
    }
}
