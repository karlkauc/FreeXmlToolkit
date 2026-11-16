package org.fxt.freexmltoolkit.service.yaml;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.fxt.freexmltoolkit.service.DataFormatConverter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Writes changed data back into a YAML text without reformatting it: the old data (read from the
 * text) is compared with the new data, and only what differs is replaced at its place in the text -
 * a changed value, a renamed key, an added or removed entry or item. Comments, anchors, quoting,
 * flow style and blank lines elsewhere stay as they are.
 *
 * <p>Every result is read again and must give exactly the new data; when it does not (for example
 * when a value reached through an alias was changed, or a document was added) there is no result
 * and the caller decides whether to write the whole text again ({@link #rewrite}). UI-free.
 */
public final class YamlTextPatcher {

    private YamlTextPatcher() {
    }

    /** One replacement in the old text. */
    private record Edit(int start, int end, String text) {
    }

    /** Thrown inside the diff when a change cannot be placed in the text. */
    private static final class NotPatchable extends Exception {
        NotPatchable() {
            super(null, null, false, false);
        }
    }

    /**
     * @param text   the current YAML text
     * @param target the data it should hold (an array of documents for a text with several documents)
     * @return the text with only the differences replaced, or empty when that is not possible
     */
    public static Optional<String> patch(String text, JsonElement target) {
        YamlDocument document = YamlDocument.parse(text);
        if (!document.isValid() || document.json() == null || target == null) {
            return Optional.empty();
        }
        if (same(document.json(), target)) {
            return Optional.of(text);
        }
        Patch patch = new Patch(text, document);
        try {
            if (document.documentCount() > 1) {
                JsonArray oldDocs = document.json().getAsJsonArray();
                if (!target.isJsonArray() || target.getAsJsonArray().size() != oldDocs.size()) {
                    return Optional.empty(); // a document added or removed
                }
                for (int i = 0; i < oldDocs.size(); i++) {
                    patch.diff(List.of(i), oldDocs.get(i), target.getAsJsonArray().get(i), null);
                }
            } else if (document.documentCount() == 1) {
                patch.diff(List.of(), document.json(), target, null);
            } else {
                return Optional.empty();
            }
        } catch (NotPatchable e) {
            return Optional.empty();
        }
        Optional<String> result = patch.apply();
        if (result.isEmpty()) {
            return result;
        }
        YamlDocument check = YamlDocument.parse(result.get());
        return check.isValid() && same(target, check.json()) && check.documentCount() == document.documentCount()
                ? result : Optional.empty();
    }

    /**
     * Writes the data as a new YAML text, block style (the fallback when {@link #patch} has no
     * result): comments, anchors and tags of the old text are lost.
     *
     * @param text   the current text (only whether it starts with {@code ---} is kept)
     * @param target the data
     * @param indent spaces per level
     * @return the new text
     */
    public static String rewrite(String text, JsonElement target, int indent) {
        DataFormatConverter.Options options = DataFormatConverter.Options.DEFAULTS.withIndent(Math.max(2, indent));
        YamlDocument document = YamlDocument.parse(text);
        boolean several = document.documentCount() > 1 && target.isJsonArray();
        boolean marker = several || text != null && text.stripLeading().startsWith("---");
        StringBuilder out = new StringBuilder();
        List<JsonElement> documents = new ArrayList<>();
        if (several) {
            target.getAsJsonArray().forEach(documents::add);
        } else {
            documents.add(target);
        }
        for (JsonElement doc : documents) {
            if (marker) {
                out.append("---\n");
            }
            out.append(DataFormatConverter.writeYaml(doc, options));
        }
        return out.toString();
    }

    // ---- the diff -----------------------------------------------------------------------------

    private static final class Patch {
        private final String text;
        private final YamlDocument document;
        private final List<Edit> edits = new ArrayList<>();
        private final int unit;

        Patch(String text, YamlDocument document) {
            this.text = text;
            this.document = document;
            this.unit = indentUnit();
        }

        /**
         * @param flowParent whether the value is written inside a flow collection ({@code […]}/{@code {…}}),
         *                   {@code null} for a document root
         */
        void diff(List<Object> path, JsonElement oldValue, JsonElement newValue, Boolean flowParent)
                throws NotPatchable {
            if (same(oldValue, newValue)) {
                return;
            }
            YamlDocument.Location location = location(path);
            boolean flow = isFlow(location);
            if (isScalar(oldValue) && isScalar(newValue)) {
                edits.add(new Edit(contentStart(location), scalarEnd(location), scalar(newValue)));
                return;
            }
            int mark = edits.size();
            try {
                if (!flow && oldValue.isJsonObject() && newValue.isJsonObject()
                        && !newValue.getAsJsonObject().isEmpty()) {
                    diffMapping(path, oldValue.getAsJsonObject(), newValue.getAsJsonObject());
                    return;
                }
                if (!flow && oldValue.isJsonArray() && newValue.isJsonArray() && !newValue.getAsJsonArray().isEmpty()) {
                    diffSequence(path, oldValue.getAsJsonArray(), newValue.getAsJsonArray());
                    return;
                }
            } catch (NotPatchable e) {
                // the change cannot be placed piece by piece: write this value again as a whole
                edits.subList(mark, edits.size()).clear();
                replace(path, location, newValue, Boolean.TRUE.equals(flowParent));
                return;
            }
            if (flow && oldValue.isJsonArray() && newValue.isJsonArray()
                    && oldValue.getAsJsonArray().size() == newValue.getAsJsonArray().size()) {
                for (int i = 0; i < oldValue.getAsJsonArray().size(); i++) {
                    diff(append(path, i), oldValue.getAsJsonArray().get(i), newValue.getAsJsonArray().get(i), true);
                }
                return;
            }
            if (flow && oldValue.isJsonObject() && newValue.isJsonObject()
                    && oldValue.getAsJsonObject().keySet().equals(newValue.getAsJsonObject().keySet())
                    && List.copyOf(oldValue.getAsJsonObject().keySet())
                    .equals(List.copyOf(newValue.getAsJsonObject().keySet()))) {
                for (String key : oldValue.getAsJsonObject().keySet()) {
                    diff(append(path, key), oldValue.getAsJsonObject().get(key),
                            newValue.getAsJsonObject().get(key), true);
                }
                return;
            }
            replace(path, location, newValue, Boolean.TRUE.equals(flowParent) || flow);
        }

        private void diffMapping(List<Object> path, JsonObject oldMap, JsonObject newMap) throws NotPatchable {
            List<String> oldKeys = List.copyOf(oldMap.keySet());
            List<String> newKeys = List.copyOf(newMap.keySet());
            List<String> removed = oldKeys.stream().filter(k -> !newMap.has(k)).toList();
            List<String> added = newKeys.stream().filter(k -> !oldMap.has(k)).toList();
            List<String> commonOld = oldKeys.stream().filter(newMap::has).toList();
            List<String> commonNew = newKeys.stream().filter(oldMap::has).toList();
            if (!commonOld.equals(commonNew)) {
                throw new NotPatchable(); // entries moved
            }
            // a renamed key: one key gone, one new at the same place
            if (removed.size() == 1 && added.size() == 1
                    && oldKeys.indexOf(removed.get(0)) == newKeys.indexOf(added.get(0))) {
                String oldKey = removed.get(0);
                String newKey = added.get(0);
                YamlDocument.Location location = location(append(path, oldKey));
                if (location.keyStart() < 0) {
                    throw new NotPatchable();
                }
                edits.add(new Edit(location.keyStart(), location.keyEnd(), scalar(new com.google.gson.JsonPrimitive(newKey))));
                diff(append(path, oldKey), oldMap.get(oldKey), newMap.get(newKey), false);
                for (String key : commonOld) {
                    diff(append(path, key), oldMap.get(key), newMap.get(key), false);
                }
                return;
            }
            for (String key : removed) {
                removeLines(location(append(path, key)), append(path, key), oldMap.get(key), true);
            }
            for (String key : commonOld) {
                diff(append(path, key), oldMap.get(key), newMap.get(key), false);
            }
            for (String key : added) {
                int index = newKeys.indexOf(key);
                String before = null; // the nearest earlier key that stays
                for (int i = index - 1; i >= 0 && before == null; i--) {
                    if (oldMap.has(newKeys.get(i))) {
                        before = newKeys.get(i);
                    }
                }
                List<String> lines = entryLines(key, newMap.get(key));
                if (before != null) {
                    insertAfter(append(path, before), oldMap.get(before), keyColumn(append(path, before)), lines);
                } else if (!commonOld.isEmpty()) {
                    YamlDocument.Location next = location(append(path, commonOld.get(0)));
                    insertBefore(next.keyStart(), column(next.keyStart()), lines);
                } else {
                    throw new NotPatchable();
                }
            }
        }

        private void diffSequence(List<Object> path, JsonArray oldItems, JsonArray newItems) throws NotPatchable {
            int oldSize = oldItems.size();
            int newSize = newItems.size();
            if (oldSize == newSize) {
                for (int i = 0; i < oldSize; i++) {
                    diff(append(path, i), oldItems.get(i), newItems.get(i), false);
                }
                return;
            }
            int prefix = 0;
            while (prefix < Math.min(oldSize, newSize) && same(oldItems.get(prefix), newItems.get(prefix))) {
                prefix++;
            }
            int suffix = 0;
            while (suffix < Math.min(oldSize, newSize) - prefix
                    && same(oldItems.get(oldSize - 1 - suffix), newItems.get(newSize - 1 - suffix))) {
                suffix++;
            }
            if (prefix + suffix == oldSize) {
                // items inserted at prefix
                int column = dashColumn(append(path, 0));
                for (int i = prefix; i < newSize - suffix; i++) {
                    List<String> lines = itemLines(newItems.get(i));
                    if (prefix < oldSize) {
                        insertBefore(dashStart(append(path, prefix)), column, lines);
                    } else {
                        insertAfter(append(path, oldSize - 1), oldItems.get(oldSize - 1), column, lines);
                    }
                }
                return;
            }
            if (prefix + suffix == newSize) {
                for (int i = prefix; i < oldSize - suffix; i++) {
                    removeLines(location(append(path, i)), append(path, i), oldItems.get(i), false);
                }
                return;
            }
            replace(path, location(path), newItems, false);
        }

        /**
         * Replaces a whole value; a block value under a key or dash is written in block style, a
         * value in or as a flow collection in flow style. Refused when the old value's text holds
         * comments, anchors or tags, which the new text would not keep.
         */
        private void replace(List<Object> path, YamlDocument.Location location, JsonElement value, boolean inFlow)
                throws NotPatchable {
            JsonElement old = document.valueAt(path);
            int start = contentStart(location);
            int end = effectiveEnd(path, old, location);
            if (end < start || YamlFormatter.lostByFormatting(text.substring(start, end)) != null) {
                throw new NotPatchable();
            }
            boolean simple = isScalar(value) || isEmptyCollection(value);
            if (inFlow || isFlow(location) || isScalar(old) && simple) {
                edits.add(new Edit(start, end, flowText(value)));
                return;
            }
            if (isRoot(path)) {
                edits.add(new Edit(start, end, simple ? flowText(value) : String.join("\n", blockLines(value))));
                return;
            }
            if (location.keyStart() >= 0) {
                int colon = colonAfter(location.keyEnd());
                if (simple) {
                    edits.add(new Edit(colon + 1, end, " " + flowText(value)));
                    return;
                }
                int childColumn = column(location.keyStart()) + (value.isJsonArray() ? 0 : unit);
                StringBuilder out = new StringBuilder();
                for (String line : blockLines(value)) {
                    out.append('\n').append(" ".repeat(childColumn)).append(line);
                }
                edits.add(new Edit(colon + 1, end, out.toString()));
                return;
            }
            // a sequence item
            int dash = dashStart(path);
            if (simple) {
                edits.add(new Edit(dash + 1, end, " " + flowText(value)));
                return;
            }
            String indent = "\n" + " ".repeat(column(dash) + 2);
            edits.add(new Edit(dash + 1, end, " " + String.join(indent, blockLines(value))));
        }

        private boolean isRoot(List<Object> path) {
            return path.size() == (document.documentCount() > 1 ? 1 : 0);
        }

        /** Where a value's content starts, after its anchor ({@code &a}) and tag ({@code !Ref}). */
        private int contentStart(YamlDocument.Location location) {
            int i = location.valueStart();
            while (i < location.valueEnd() && i < text.length() && (text.charAt(i) == '&' || text.charAt(i) == '!')) {
                int j = i;
                while (j < text.length() && !Character.isWhitespace(text.charAt(j))) {
                    j++;
                }
                while (j < text.length() && (text.charAt(j) == ' ' || text.charAt(j) == '\t')) {
                    j++;
                }
                if (j >= location.valueEnd() || text.charAt(j) == '\n' || text.charAt(j) == '#') {
                    return i; // a block collection after its properties: keep the start
                }
                i = j;
            }
            return i;
        }

        /** Removes an entry or item: its whole lines, or up to the next sibling when it shares a line. */
        private void removeLines(YamlDocument.Location location, List<Object> path, JsonElement value, boolean keyed)
                throws NotPatchable {
            int start = keyed ? location.keyStart() : dashStart(path);
            if (start < 0) {
                throw new NotPatchable();
            }
            int end = effectiveEnd(path, value, location);
            int lineStart = lineStart(start);
            if (!text.substring(lineStart, start).isBlank()) {
                throw new NotPatchable(); // shares its line with a "- " or another entry
            }
            edits.add(new Edit(lineStart, nextLineStart(end), ""));
        }

        private void insertAfter(List<Object> previousPath, JsonElement previousValue, int column, List<String> lines)
                throws NotPatchable {
            int end = lineEnd(effectiveEnd(previousPath, previousValue, location(previousPath)));
            StringBuilder out = new StringBuilder();
            for (String line : lines) {
                out.append('\n').append(" ".repeat(column)).append(line);
            }
            edits.add(new Edit(end, end, out.toString()));
        }

        private void insertBefore(int offset, int column, List<String> lines) {
            String indent = " ".repeat(column);
            StringBuilder out = new StringBuilder();
            for (String line : lines) {
                out.append(line).append('\n').append(indent);
            }
            edits.add(new Edit(offset, offset, out.toString()));
        }

        Optional<String> apply() {
            List<Edit> sorted = new ArrayList<>(edits);
            sorted.sort(Comparator.comparingInt(Edit::start).thenComparingInt(Edit::end));
            for (int i = 1; i < sorted.size(); i++) {
                Edit previous = sorted.get(i - 1);
                Edit current = sorted.get(i);
                if (current.start() < previous.end()
                        || current.start() == previous.start() && current.end() > current.start()) {
                    return Optional.empty(); // overlapping replacements
                }
            }
            boolean crlf = text.contains("\r\n");
            StringBuilder out = new StringBuilder(text);
            for (int i = sorted.size() - 1; i >= 0; i--) {
                Edit edit = sorted.get(i);
                out.replace(edit.start(), edit.end(), crlf ? edit.text().replace("\n", "\r\n") : edit.text());
            }
            return Optional.of(out.toString());
        }

        // ---- positions ----------------------------------------------------------------------

        private YamlDocument.Location location(List<Object> path) throws NotPatchable {
            YamlDocument.Location location = document.locationOf(path);
            if (location == null) {
                throw new NotPatchable();
            }
            return location;
        }

        private boolean isFlow(YamlDocument.Location location) {
            int start = contentStart(location);
            if (start >= text.length()) {
                return false;
            }
            char c = text.charAt(start);
            return c == '[' || c == '{';
        }

        /** End of a scalar without the line breaks a block scalar takes along. */
        private int scalarEnd(YamlDocument.Location location) {
            int end = location.valueEnd();
            while (end > location.valueStart() && Character.isWhitespace(text.charAt(end - 1))) {
                end--;
            }
            return end;
        }

        /** Where a value's own text ends: the last child's end for a block collection, without trailing comments. */
        private int effectiveEnd(List<Object> path, JsonElement value, YamlDocument.Location location) {
            if (value == null || isScalar(value) || isFlow(location)) {
                return isFlow(location) ? location.valueEnd() : scalarEnd(location);
            }
            int end = location.valueStart();
            if (value.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
                    end = Math.max(end, childEnd(append(path, entry.getKey()), entry.getValue(), location));
                }
            } else if (value.isJsonArray()) {
                JsonArray items = value.getAsJsonArray();
                for (int i = 0; i < items.size(); i++) {
                    end = Math.max(end, childEnd(append(path, i), items.get(i), location));
                }
            }
            return end == location.valueStart() ? scalarEnd(location) : end;
        }

        private int childEnd(List<Object> path, JsonElement value, YamlDocument.Location parent) {
            YamlDocument.Location child = document.locationOf(path);
            if (child == null || child.valueStart() < parent.valueStart() || child.valueStart() > parent.valueEnd()) {
                return -1; // merged from elsewhere or reached through an alias
            }
            return effectiveEnd(path, value, child);
        }

        private int lineStart(int offset) {
            int i = Math.min(offset, text.length());
            while (i > 0 && text.charAt(i - 1) != '\n') {
                i--;
            }
            return i;
        }

        /** End of the line's content (before {@code \r\n} or {@code \n}). */
        private int lineEnd(int offset) {
            int newline = text.indexOf('\n', Math.min(offset, text.length()));
            if (newline < 0) {
                return text.length();
            }
            return newline > 0 && text.charAt(newline - 1) == '\r' && newline - 1 >= offset ? newline - 1 : newline;
        }

        /** Start of the next line (after the line break), or the text's end. */
        private int nextLineStart(int offset) {
            int newline = text.indexOf('\n', Math.min(offset, text.length()));
            return newline < 0 ? text.length() : newline + 1;
        }

        private int column(int offset) {
            return offset - lineStart(offset);
        }

        private int keyColumn(List<Object> path) throws NotPatchable {
            YamlDocument.Location location = location(path);
            if (location.keyStart() < 0) {
                throw new NotPatchable();
            }
            return column(location.keyStart());
        }

        private int colonAfter(int keyEnd) throws NotPatchable {
            int i = keyEnd;
            while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) {
                i++;
            }
            if (i >= text.length() || text.charAt(i) != ':') {
                throw new NotPatchable();
            }
            return i;
        }

        /** The {@code -} of a block sequence item. */
        private int dashStart(List<Object> itemPath) throws NotPatchable {
            YamlDocument.Location location = location(itemPath);
            int i = location.valueStart() - 1;
            while (i >= 0 && (text.charAt(i) == ' ' || text.charAt(i) == '\t' || text.charAt(i) == '\n'
                    || text.charAt(i) == '\r')) {
                i--;
            }
            if (i < 0 || text.charAt(i) != '-') {
                throw new NotPatchable();
            }
            return i;
        }

        private int dashColumn(List<Object> itemPath) throws NotPatchable {
            return column(dashStart(itemPath));
        }

        /** Spaces per level as the text uses them (2 when nothing tells). */
        private int indentUnit() {
            for (Map.Entry<List<Object>, YamlDocument.Location> entry : document.locations().entrySet()) {
                List<Object> path = entry.getKey();
                YamlDocument.Location child = entry.getValue();
                if (path.size() < 2 || child.keyStart() < 0 || !(path.get(path.size() - 2) instanceof String)) {
                    continue;
                }
                YamlDocument.Location parent = document.locationOf(path.subList(0, path.size() - 1));
                if (parent == null || parent.keyStart() < 0 || isFlow(parent)) {
                    continue;
                }
                int difference = column(child.keyStart()) - column(parent.keyStart());
                if (difference >= 2 && difference <= 8) {
                    return difference;
                }
            }
            return 2;
        }

        // ---- rendering ----------------------------------------------------------------------

        private List<String> entryLines(String key, JsonElement value) {
            Map<String, JsonElement> single = new LinkedHashMap<>();
            single.put(key, value);
            JsonObject object = new JsonObject();
            single.forEach(object::add);
            return blockLines(object);
        }

        private List<String> itemLines(JsonElement value) {
            JsonArray array = new JsonArray();
            array.add(value);
            return blockLines(array);
        }

        private List<String> blockLines(JsonElement value) {
            String yaml = DataFormatConverter.writeYaml(value, DataFormatConverter.Options.DEFAULTS.withIndent(unit));
            if (yaml.endsWith("\n")) {
                yaml = yaml.substring(0, yaml.length() - 1);
            }
            return List.of(yaml.split("\n", -1));
        }
    }

    // ---- values -------------------------------------------------------------------------------

    /**
     * Equal data with the keys in the same order (Gson's {@code equals} ignores the order of keys,
     * but a moved entry is a change here).
     */
    static boolean same(JsonElement a, JsonElement b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a.isJsonObject() && b.isJsonObject()) {
            var x = a.getAsJsonObject().entrySet().iterator();
            var y = b.getAsJsonObject().entrySet().iterator();
            while (x.hasNext() && y.hasNext()) {
                var ex = x.next();
                var ey = y.next();
                if (!ex.getKey().equals(ey.getKey()) || !same(ex.getValue(), ey.getValue())) {
                    return false;
                }
            }
            return !x.hasNext() && !y.hasNext();
        }
        if (a.isJsonArray() && b.isJsonArray()) {
            JsonArray x = a.getAsJsonArray();
            JsonArray y = b.getAsJsonArray();
            if (x.size() != y.size()) {
                return false;
            }
            for (int i = 0; i < x.size(); i++) {
                if (!same(x.get(i), y.get(i))) {
                    return false;
                }
            }
            return true;
        }
        return a.equals(b);
    }

    private static boolean isScalar(JsonElement value) {
        return value == null || value.isJsonNull() || value.isJsonPrimitive();
    }

    private static boolean isEmptyCollection(JsonElement value) {
        return value.isJsonObject() && value.getAsJsonObject().isEmpty()
                || value.isJsonArray() && value.getAsJsonArray().isEmpty();
    }

    private static String scalar(JsonElement value) {
        return YamlCompletionProvider.scalar(value);
    }

    /** A value in flow style: {@code [a, b]}, {@code {k: v}} or a scalar. */
    static String flowText(JsonElement value) {
        if (value.isJsonArray()) {
            List<String> items = new ArrayList<>();
            value.getAsJsonArray().forEach(item -> items.add(flowText(item)));
            return "[" + String.join(", ", items) + "]";
        }
        if (value.isJsonObject()) {
            List<String> entries = new ArrayList<>();
            value.getAsJsonObject().entrySet().forEach(e ->
                    entries.add(scalar(new com.google.gson.JsonPrimitive(e.getKey())) + ": " + flowText(e.getValue())));
            return "{" + String.join(", ", entries) + "}";
        }
        return scalar(value);
    }

    private static List<Object> append(List<Object> path, Object segment) {
        List<Object> child = new ArrayList<>(path.size() + 1);
        child.addAll(path);
        child.add(segment);
        return child;
    }
}
