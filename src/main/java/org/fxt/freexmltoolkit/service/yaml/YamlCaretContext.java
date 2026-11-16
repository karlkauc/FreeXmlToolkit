package org.fxt.freexmltoolkit.service.yaml;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What can be typed at the caret of a block-style YAML text, read from the indentation (the text
 * is usually incomplete while it is typed, so it is not parsed): a mapping key, the value of a key,
 * or a sequence item - with the data path of the place (String keys, Integer indexes, like
 * {@link YamlDocument}). Flow collections ({@code {…}}, {@code […]}) and block scalars are not
 * analysed. UI-free.
 */
public final class YamlCaretContext {

    /** What the caret is at. */
    public enum Kind {
        /** A key of the mapping at {@link Result#path()}. */
        KEY,
        /** The value at {@link Result#path()} (after {@code key: }). */
        VALUE,
        /** A new or partly typed sequence item at {@link Result#path()} (the item's path): a value or the first key of a mapping. */
        ITEM,
        /** Nothing to complete. */
        NONE
    }

    /**
     * The caret context.
     *
     * @param kind         key, value, item or none
     * @param path         KEY: the enclosing mapping; VALUE / ITEM: the value itself
     * @param partial      the word typed so far (without an opening quote)
     * @param replaceStart start of the text a completion replaces
     * @param replaceEnd   end of it (the whole word, also after the caret)
     * @param column       column where a key starts (indentation of new lines below it)
     * @param colonFollows KEY: whether the word is followed by {@code :} already
     * @param existingKeys KEY / ITEM: the keys the mapping has already (the word being typed excluded)
     */
    public record Result(Kind kind, List<Object> path, String partial, int replaceStart, int replaceEnd,
                         int column, boolean colonFollows, Set<String> existingKeys) {

        static Result none(int offset) {
            return new Result(Kind.NONE, List.of(), "", offset, offset, 0, false, Set.of());
        }
    }

    /** A dash or a key on a line. */
    private record Frame(int column, boolean dash, String key, boolean inlineValue) {
    }

    /** The frames of one line: its sequence dashes, then at most one key. */
    private record Line(int start, String text, List<Frame> frames) {

        boolean isContent() {
            return !frames.isEmpty() || !text.isBlank() && !text.strip().startsWith("#");
        }

        int minColumn() {
            return frames.isEmpty() ? indent(text) : frames.get(0).column();
        }
    }

    private YamlCaretContext() {
    }

    /**
     * @param text   the text
     * @param offset the caret offset
     * @return the context
     */
    public static Result analyze(String text, int offset) {
        if (text == null || offset < 0 || offset > text.length()) {
            return Result.none(Math.max(0, offset));
        }
        List<Line> lines = lines(text);
        int index = lineIndexAt(lines, offset);
        Line line = lines.get(index);
        int caretColumn = offset - line.start();
        String prefix = line.text().substring(0, caretColumn);
        if (inComment(prefix) || prefix.startsWith("%") || prefix.startsWith("---") || prefix.startsWith("...")
                || insideFlowOrBlockScalar(lines, index, prefix)) {
            return Result.none(offset);
        }
        // the dashes before the caret
        int pos = indent(prefix);
        List<Integer> dashes = new ArrayList<>();
        while (pos < prefix.length() && prefix.charAt(pos) == '-'
                && (pos + 1 == prefix.length() || prefix.charAt(pos + 1) == ' ')) {
            dashes.add(pos);
            pos++;
            while (pos < prefix.length() && prefix.charAt(pos) == ' ') {
                pos++;
            }
        }
        String rest = prefix.substring(pos);
        int colon = keyColon(rest);
        if (colon >= 0) {
            // after "key:" → the key's value
            String key = unquote(rest.substring(0, colon).strip());
            int valueStart = colon + 1;
            while (valueStart < rest.length() && rest.charAt(valueStart) == ' ') {
                valueStart++;
            }
            if (valueStart == colon + 1 && valueStart < rest.length()) {
                return Result.none(offset); // "key:x" is no key
            }
            String partial = rest.substring(valueStart);
            if (partial.startsWith("&") || partial.startsWith("*") || partial.startsWith("!")
                    || partial.startsWith("|") || partial.startsWith(">") || partial.startsWith("[")
                    || partial.startsWith("{")) {
                return Result.none(offset);
            }
            int replaceStart = line.start() + pos + valueStart;
            List<Object> container = containerPath(lines, index, pos, dashes);
            if (container == null) {
                return Result.none(offset);
            }
            List<Object> path = append(container, key);
            return new Result(Kind.VALUE, path, unquote(partial), replaceStart, valueEnd(line, replaceStart),
                    pos, false, Set.of());
        }
        if (rest.contains(" #") || rest.startsWith("#")) {
            return Result.none(offset);
        }
        int keyColumn = pos;
        int replaceStart = line.start() + keyColumn;
        int replaceEnd = wordEnd(line, offset);
        boolean colonFollows = replaceEnd < line.start() + line.text().length()
                && line.text().charAt(replaceEnd - line.start()) == ':';
        List<Object> container = containerPath(lines, index, keyColumn, dashes);
        if (container == null) {
            return Result.none(offset);
        }
        String partial = unquote(rest);
        Set<String> existing = existingKeys(lines, index, keyColumn, !dashes.isEmpty());
        existing.remove(partial);
        if (!dashes.isEmpty() && !colonFollows) {
            // right after "- ": a value of the item or the first key of a mapping item
            return new Result(Kind.ITEM, container, partial, replaceStart, replaceEnd, keyColumn, false, existing);
        }
        return new Result(Kind.KEY, container, partial, replaceStart, replaceEnd, keyColumn, colonFollows, existing);
    }

    /**
     * The path of the collection holding something at {@code column} of line {@code index}; for a
     * caret right after dashes the innermost sequence item.
     */
    private static List<Object> containerPath(List<Line> lines, int index, int column, List<Integer> dashesOnLine) {
        if (!dashesOnLine.isEmpty()) {
            int dash = dashesOnLine.get(dashesOnLine.size() - 1);
            return itemPath(lines, index, dash, dashesOnLine.subList(0, dashesOnLine.size() - 1));
        }
        return mappingPath(lines, index, column);
    }

    /** The path of a mapping whose keys start at {@code column}, of which line {@code index} is part. */
    private static List<Object> mappingPath(List<Line> lines, int index, int column) {
        for (int j = index - 1; j >= 0; j--) {
            Line line = lines.get(j);
            if (!line.isContent() || line.minColumn() >= column) {
                continue;
            }
            Frame outer = innermostBefore(line, column);
            if (outer == null) {
                continue;
            }
            if (outer.dash()) {
                return itemPath(lines, j, outer.column(), dashesBefore(line, outer.column()));
            }
            if (outer.inlineValue()) {
                return null; // deeper lines under "key: value" are no YAML
            }
            List<Object> parent = keyContainer(lines, j, outer);
            return parent == null ? null : append(parent, outer.key());
        }
        return new ArrayList<>(documentPrefix(lines, index)); // a key of the document's root mapping
    }

    /** The path of the mapping holding the key frame {@code key} of line {@code index}. */
    private static List<Object> keyContainer(List<Line> lines, int index, Frame key) {
        List<Integer> dashes = dashesBefore(lines.get(index), key.column());
        if (!dashes.isEmpty()) {
            return itemPath(lines, index, dashes.get(dashes.size() - 1), dashes.subList(0, dashes.size() - 1));
        }
        return mappingPath(lines, index, key.column());
    }

    /** The path of the sequence item whose dash is at {@code dash} on line {@code index}. */
    private static List<Object> itemPath(List<Line> lines, int index, int dash, List<Integer> outerDashes) {
        if (!outerDashes.isEmpty()) {
            // "- - x": the inner sequence is the first value of the outer item
            List<Object> outer = itemPath(lines, index, outerDashes.get(outerDashes.size() - 1),
                    outerDashes.subList(0, outerDashes.size() - 1));
            return outer == null ? null : append(outer, 0);
        }
        // the sequence's parent: a key without value at a smaller column or at the same column
        int count = 0;
        for (int j = index; j >= 0; j--) {
            Line line = lines.get(j);
            if (!line.isContent()) {
                continue;
            }
            Frame first = line.frames().isEmpty() ? null : line.frames().get(0);
            if (j < index && first != null && first.column() == dash && first.dash()) {
                count++;
                continue;
            }
            if (j == index) {
                continue;
            }
            if (line.minColumn() > dash) {
                continue;
            }
            Frame outer = innermostAtOrBefore(line, dash);
            if (outer == null) {
                continue;
            }
            if (outer.dash()) {
                if (outer.column() == dash) {
                    continue;
                }
                List<Object> item = itemPath(lines, j, outer.column(), dashesBefore(line, outer.column()));
                return item == null ? null : append(item, count);
            }
            if (outer.inlineValue()) {
                return null;
            }
            List<Object> parent = keyContainer(lines, j, outer);
            return parent == null ? null : append(append(parent, outer.key()), count);
        }
        return dash == 0 ? append(new ArrayList<>(documentPrefix(lines, index)), count) : null;
    }

    /** With several documents the path starts with the document's index. */
    private static List<Object> documentPrefix(List<Line> lines, int index) {
        int markersBefore = 0;
        int markersTotal = 0;
        boolean contentBeforeFirst = false;
        for (int j = 0; j < lines.size(); j++) {
            String text = lines.get(j).text();
            boolean marker = text.startsWith("---") && (text.length() == 3 || text.charAt(3) == ' ');
            if (marker) {
                markersTotal++;
                if (j <= index) {
                    markersBefore++;
                }
            } else if (markersTotal == 0 && lines.get(j).isContent() && !text.startsWith("%")) {
                contentBeforeFirst = true;
            }
        }
        int documents = markersTotal + (contentBeforeFirst ? 1 : 0);
        if (documents <= 1) {
            return List.of();
        }
        int document = markersBefore - 1 + (contentBeforeFirst ? 1 : 0);
        return List.of(Math.max(0, document));
    }

    /** The keys of the mapping (keys at {@code column}) around line {@code index}. */
    private static Set<String> existingKeys(List<Line> lines, int index, int column, boolean newItem) {
        Set<String> keys = new LinkedHashSet<>();
        if (newItem) {
            // a new item's mapping goes on below its dash line only
            for (int j = index + 1; j < lines.size(); j++) {
                if (!collect(lines.get(j), column, keys)) {
                    break;
                }
            }
            return keys;
        }
        for (int j = index - 1; j >= 0; j--) {
            Line line = lines.get(j);
            if (!collect(line, column, keys)) {
                Frame outer = innermostBefore(line, column);
                if (outer != null && outer.dash()) {
                    for (Frame frame : line.frames()) {
                        if (!frame.dash() && frame.column() == column) {
                            keys.add(frame.key());
                        }
                    }
                }
                break;
            }
        }
        for (int j = index + 1; j < lines.size(); j++) {
            if (!collect(lines.get(j), column, keys)) {
                break;
            }
        }
        return keys;
    }

    /** @return false when the line ends the mapping (something at a smaller column) */
    private static boolean collect(Line line, int column, Set<String> keys) {
        if (!line.isContent()) {
            return true;
        }
        if (line.minColumn() < column) {
            return false;
        }
        if (line.minColumn() == column) {
            Frame first = line.frames().isEmpty() ? null : line.frames().get(0);
            if (first == null || first.dash()) {
                return false;
            }
            keys.add(first.key());
        }
        return true;
    }

    private static Frame innermostBefore(Line line, int column) {
        Frame best = null;
        for (Frame frame : line.frames()) {
            if (frame.column() < column) {
                best = frame;
            }
        }
        return best;
    }

    private static Frame innermostAtOrBefore(Line line, int column) {
        Frame best = null;
        for (Frame frame : line.frames()) {
            if (frame.column() <= column) {
                best = frame;
            }
        }
        return best;
    }

    private static List<Integer> dashesBefore(Line line, int column) {
        List<Integer> dashes = new ArrayList<>();
        for (Frame frame : line.frames()) {
            if (frame.dash() && frame.column() < column) {
                dashes.add(frame.column());
            }
        }
        return dashes;
    }

    // ---- lines ----------------------------------------------------------------------------------

    private static List<Line> lines(String text) {
        List<Line> lines = new ArrayList<>();
        int start = 0;
        while (true) {
            int end = text.indexOf('\n', start);
            String raw = text.substring(start, end < 0 ? text.length() : end);
            if (raw.endsWith("\r")) {
                raw = raw.substring(0, raw.length() - 1);
            }
            lines.add(new Line(start, raw, frames(raw)));
            if (end < 0) {
                break;
            }
            start = end + 1;
        }
        return lines;
    }

    private static List<Frame> frames(String text) {
        List<Frame> frames = new ArrayList<>();
        int pos = indent(text);
        if (pos >= text.length() || text.charAt(pos) == '#' || text.startsWith("---") || text.startsWith("...")
                || text.startsWith("%")) {
            return frames;
        }
        while (pos < text.length() && text.charAt(pos) == '-'
                && (pos + 1 == text.length() || text.charAt(pos + 1) == ' ')) {
            frames.add(new Frame(pos, true, null, false));
            pos++;
            while (pos < text.length() && text.charAt(pos) == ' ') {
                pos++;
            }
        }
        String rest = text.substring(pos);
        int colon = keyColon(rest);
        if (colon > 0) {
            String value = stripComment(rest.substring(colon + 1)).strip();
            boolean inline = !value.isEmpty() && !value.matches("([&!][^\\s]*\\s*)+");
            frames.add(new Frame(pos, false, unquote(rest.substring(0, colon).strip()), inline));
        }
        return frames;
    }

    /** @return the index of the colon ending a key at the start of {@code rest}, or -1 */
    private static int keyColon(String rest) {
        if (rest.isEmpty()) {
            return -1;
        }
        char first = rest.charAt(0);
        int from = 0;
        if (first == '"' || first == '\'') {
            int close = rest.indexOf(first, 1);
            while (close > 0 && first == '"' && rest.charAt(close - 1) == '\\') {
                close = rest.indexOf(first, close + 1);
            }
            if (close < 0) {
                return -1;
            }
            from = close + 1;
        } else if ("[{&*!|>#%@`".indexOf(first) >= 0) {
            return -1;
        }
        for (int i = from; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '#' && i > 0 && rest.charAt(i - 1) == ' ') {
                return -1;
            }
            if (c == ':' && (i + 1 == rest.length() || rest.charAt(i + 1) == ' ' || rest.charAt(i + 1) == '\t')) {
                return i;
            }
        }
        return -1;
    }

    private static int lineIndexAt(List<Line> lines, int offset) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (lines.get(i).start() <= offset) {
                return i;
            }
        }
        return 0;
    }

    private static boolean inComment(String prefix) {
        String stripped = prefix.stripLeading();
        return stripped.startsWith("#") || prefix.contains(" #");
    }

    /** Inside {@code […]} / {@code {…}} on this line, or in the lines of a block scalar above. */
    private static boolean insideFlowOrBlockScalar(List<Line> lines, int index, String prefix) {
        int depth = 0;
        for (char c : prefix.toCharArray()) {
            if (c == '[' || c == '{') {
                depth++;
            } else if ((c == ']' || c == '}') && depth > 0) {
                depth--;
            }
        }
        if (depth > 0) {
            return true;
        }
        // the first line above that is indented less owns this line: is it a block scalar's header?
        int column = indent(prefix);
        for (int j = index - 1; j >= 0; j--) {
            Line line = lines.get(j);
            if (line.text().isBlank()) {
                continue;
            }
            if (indent(line.text()) < column) {
                String content = stripComment(line.text()).stripTrailing();
                return content.matches(".*(:|-)\\s+[|>][-+0-9]*");
            }
        }
        return false;
    }

    private static int valueEnd(Line line, int from) {
        String text = line.text();
        int end = text.length();
        int hash = text.indexOf(" #", from - line.start());
        if (hash >= 0) {
            end = hash;
        }
        while (end > from - line.start() && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return line.start() + Math.max(end, from - line.start());
    }

    private static int wordEnd(Line line, int offset) {
        String text = line.text();
        int i = offset - line.start();
        while (i < text.length() && text.charAt(i) != ':' && !Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return line.start() + i;
    }

    private static String stripComment(String text) {
        int hash = text.indexOf(" #");
        return hash >= 0 ? text.substring(0, hash) : text;
    }

    private static String unquote(String text) {
        if (text.length() >= 1 && (text.charAt(0) == '"' || text.charAt(0) == '\'')) {
            char quote = text.charAt(0);
            String inner = text.length() >= 2 && text.charAt(text.length() - 1) == quote
                    ? text.substring(1, text.length() - 1) : text.substring(1);
            return quote == '\'' ? inner.replace("''", "'") : inner.replace("\\\"", "\"");
        }
        return text;
    }

    private static int indent(String text) {
        int i = 0;
        while (i < text.length() && text.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    private static List<Object> append(List<Object> path, Object segment) {
        List<Object> out = new ArrayList<>(path.size() + 1);
        out.addAll(path);
        out.add(segment);
        return out;
    }
}
