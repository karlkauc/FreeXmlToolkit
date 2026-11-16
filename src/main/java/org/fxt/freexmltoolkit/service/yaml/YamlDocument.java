package org.fxt.freexmltoolkit.service.yaml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.fxt.freexmltoolkit.service.DataFormatConverter;
import org.snakeyaml.engine.v2.api.lowlevel.Compose;
import org.snakeyaml.engine.v2.exceptions.Mark;
import org.snakeyaml.engine.v2.exceptions.MarkedYamlEngineException;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.SequenceNode;

import com.google.gson.JsonElement;

/**
 * A YAML text read once for everything the editor needs: its data as a JSON tree (the same
 * {@link DataFormatConverter#parseYaml} reading the conversions use, so a file with several
 * documents is an array), where every value and key is in the text, and the first syntax error
 * with its line and column.
 *
 * <p>Paths are lists of segments like JSON Schema instance locations: a {@code String} for a
 * mapping key, an {@code Integer} for a sequence index. With several documents the first segment
 * is the document's index. Values reached through an alias are located at their anchor; keys merged
 * with {@code <<} at the merged mapping. UI-free and immutable.
 */
public final class YamlDocument {

    /**
     * Where a value is in the text (character offsets).
     *
     * @param keyStart   offset of its key, or -1 for a sequence item or a document root
     * @param keyEnd     end of its key, or -1
     * @param valueStart offset of the value
     * @param valueEnd   end of the value
     * @param keyLine    0-based line of the key (of the value without a key)
     */
    public record Location(int keyStart, int keyEnd, int valueStart, int valueEnd, int keyLine) {

        /** @return the start of the key, or of the value without a key */
        public int start() {
            return keyStart >= 0 ? keyStart : valueStart;
        }
    }

    /**
     * A syntax error.
     *
     * @param message what is wrong, for the user
     * @param line    1-based line, 0 when unknown
     * @param column  1-based column, 0 when unknown
     */
    public record Problem(String message, int line, int column) {
    }

    private final String text;
    private final JsonElement json;
    private final Problem problem;
    private final Map<List<Object>, Location> locations;
    private final int documentCount;

    private YamlDocument(String text, JsonElement json, Problem problem, Map<List<Object>, Location> locations,
                         int documentCount) {
        this.text = text;
        this.json = json;
        this.problem = problem;
        this.locations = locations;
        this.documentCount = documentCount;
    }

    /**
     * Reads a YAML text. Never throws: an unreadable text gives a document with a {@link #problem()}.
     *
     * @param text the YAML text ({@code null} = empty)
     * @return the document
     */
    public static YamlDocument parse(String text) {
        String source = text == null ? "" : text;
        CodePoints offsets = new CodePoints(source);
        Map<List<Object>, Location> locations = new LinkedHashMap<>();
        List<Node> roots = new ArrayList<>();
        try {
            for (Node root : new Compose(DataFormatConverter.yamlLoadSettings()).composeAllFromString(source)) {
                roots.add(root);
            }
        } catch (YamlEngineException e) {
            return new YamlDocument(source, null, problemOf(e, offsets), Map.of(), 0);
        }
        boolean several = roots.size() > 1;
        IdentityHashMap<Node, Boolean> visited = new IdentityHashMap<>();
        for (int i = 0; i < roots.size(); i++) {
            List<Object> path = several ? List.of(i) : List.of();
            locate(roots.get(i), path, null, locations, visited, offsets);
        }
        JsonElement json;
        try {
            json = DataFormatConverter.parseYaml(source);
        } catch (DataFormatConverter.ConversionException e) {
            Throwable cause = e.getCause();
            Problem problem = cause instanceof YamlEngineException yaml
                    ? problemOf(yaml, offsets) : new Problem(e.getMessage(), 0, 0);
            return new YamlDocument(source, null, problem, Collections.unmodifiableMap(locations), roots.size());
        }
        return new YamlDocument(source, json, null, Collections.unmodifiableMap(locations), roots.size());
    }

    private static void locate(Node node, List<Object> path, Node key, Map<List<Object>, Location> out,
                               IdentityHashMap<Node, Boolean> visited, CodePoints offsets) {
        int keyStart = key == null ? -1 : offsets.start(key);
        int keyEnd = key == null ? -1 : offsets.end(key);
        int valueStart = offsets.start(node);
        int valueEnd = offsets.end(node);
        int lineOf = key != null ? line(key.getStartMark()) : line(node.getStartMark());
        out.putIfAbsent(List.copyOf(path), new Location(keyStart, keyEnd, valueStart, valueEnd, Math.max(0, lineOf)));
        if (visited.put(node, Boolean.TRUE) != null) {
            return; // an alias: its content is located at the anchor already
        }
        if (node instanceof MappingNode mapping) {
            for (NodeTuple tuple : mapping.getValue()) {
                Node keyNode = tuple.getKeyNode();
                if (keyNode instanceof ScalarNode scalar) {
                    if ("<<".equals(scalar.getValue()) && scalar.isPlain()) {
                        continue; // merge key: the merged values stay located where they are written
                    }
                    locate(tuple.getValueNode(), append(path, scalar.getValue()), keyNode, out, visited, offsets);
                }
            }
        } else if (node instanceof SequenceNode sequence) {
            List<Node> items = sequence.getValue();
            for (int i = 0; i < items.size(); i++) {
                locate(items.get(i), append(path, i), null, out, visited, offsets);
            }
        }
    }

    private static List<Object> append(List<Object> path, Object segment) {
        List<Object> child = new ArrayList<>(path.size() + 1);
        child.addAll(path);
        child.add(segment);
        return child;
    }

    private static int line(Optional<Mark> mark) {
        return mark.map(Mark::getLine).orElse(-1);
    }

    private static Problem problemOf(YamlEngineException e, CodePoints offsets) {
        if (e instanceof MarkedYamlEngineException marked) {
            Optional<Mark> mark = marked.getProblemMark().isPresent() ? marked.getProblemMark() : marked.getContextMark();
            String problem = marked.getProblem() != null ? marked.getProblem() : marked.getContext();
            String context = marked.getContext();
            StringBuilder message = new StringBuilder(capitalize(problem == null ? "invalid YAML" : problem.strip()));
            if (context != null && problem != null && !context.isBlank()) {
                message.append(" (").append(context.strip()).append(')');
            }
            if (mark.isPresent()) {
                return new Problem(message.toString(), mark.get().getLine() + 1, mark.get().getColumn() + 1);
            }
            return new Problem(message.toString(), 0, 0);
        }
        String message = e.getMessage() == null ? "The YAML is not valid." : e.getMessage().strip();
        return new Problem(capitalize(message), 0, 0);
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }

    // ---- queries ------------------------------------------------------------------------------

    /** @return the text this document was read from */
    public String text() {
        return text;
    }

    /** @return the data as JSON (an array for several documents, JSON null for none), or {@code null} on an error */
    public JsonElement json() {
        return json;
    }

    /** @return the syntax error, or {@code null} when the text is valid YAML */
    public Problem problem() {
        return problem;
    }

    /** @return whether the text could be read */
    public boolean isValid() {
        return problem == null;
    }

    /** @return the number of documents ({@code ---}) in the text */
    public int documentCount() {
        return documentCount;
    }

    /** @return every located path, in document order */
    public Map<List<Object>, Location> locations() {
        return locations;
    }

    /**
     * @param path a path
     * @return where exactly that value is, or {@code null}
     */
    public Location locationOf(List<Object> path) {
        return locations.get(normalize(path));
    }

    /**
     * The 1-based line of a value, for a problem reported at an instance location: the line of its
     * key (or of the value itself), or of the nearest enclosing value that is located.
     *
     * @param segments the instance location (String keys, Integer indexes; numeric strings are indexes too)
     * @return the line, 1 when nothing is located
     */
    public int lineOf(List<Object> segments) {
        List<Object> path = normalize(segments == null ? List.of() : segments);
        for (int n = path.size(); n >= 0; n--) {
            Location location = locations.get(path.subList(0, n));
            if (location != null) {
                return location.keyLine() + 1;
            }
        }
        return 1;
    }

    /**
     * @param offset a character offset
     * @return the path of the innermost key or value at it, or {@code null} when none is there
     */
    public List<Object> pathAt(int offset) {
        List<Object> best = null;
        int bestSize = Integer.MAX_VALUE;
        for (Map.Entry<List<Object>, Location> entry : locations.entrySet()) {
            Location location = entry.getValue();
            boolean inKey = location.keyStart() >= 0 && offset >= location.keyStart() && offset <= location.keyEnd();
            boolean inValue = offset >= location.valueStart() && offset <= location.valueEnd();
            if (!inKey && !inValue) {
                continue;
            }
            int size = inKey ? location.keyEnd() - location.keyStart() : location.valueEnd() - location.valueStart();
            if (size < bestSize || size == bestSize && best != null && entry.getKey().size() > best.size()) {
                best = entry.getKey();
                bestSize = size;
            }
        }
        return best;
    }

    /**
     * @param path a path
     * @return the value at it in {@link #json()}, or {@code null} when there is none
     */
    public JsonElement valueAt(List<Object> path) {
        JsonElement current = json;
        for (Object segment : path) {
            if (current == null) {
                return null;
            }
            if (segment instanceof Integer index && current.isJsonArray()) {
                var array = current.getAsJsonArray();
                current = index >= 0 && index < array.size() ? array.get(index) : null;
            } else if (current.isJsonObject()) {
                current = current.getAsJsonObject().get(String.valueOf(segment));
            } else {
                return null;
            }
        }
        return current;
    }

    /**
     * Converts a child-index path (the n-th entry of a mapping, the n-th item) into keys and indexes.
     *
     * @param indexes the child indexes from the data's root (with several documents, the first is the document)
     * @return the path, or {@code null} when it does not exist
     */
    public List<Object> segmentsOf(List<Integer> indexes) {
        if (indexes == null || json == null) {
            return null;
        }
        List<Object> out = new ArrayList<>(indexes.size());
        JsonElement current = json;
        for (int index : indexes) {
            if (current != null && current.isJsonArray() && index >= 0 && index < current.getAsJsonArray().size()) {
                out.add(index);
                current = current.getAsJsonArray().get(index);
            } else if (current != null && current.isJsonObject() && index >= 0
                    && index < current.getAsJsonObject().size()) {
                String key = List.copyOf(current.getAsJsonObject().keySet()).get(index);
                out.add(key);
                current = current.getAsJsonObject().get(key);
            } else {
                return null;
            }
        }
        return out;
    }

    /**
     * Converts a path of keys and indexes into child indexes (see {@link #segmentsOf}).
     *
     * @param segments the path
     * @return the child indexes, or {@code null} when the path does not exist
     */
    public List<Integer> indexesOf(List<Object> segments) {
        if (segments == null || json == null) {
            return null;
        }
        List<Integer> out = new ArrayList<>(segments.size());
        JsonElement current = json;
        for (Object segment : segments) {
            if (segment instanceof Integer index && current != null && current.isJsonArray()
                    && index >= 0 && index < current.getAsJsonArray().size()) {
                out.add(index);
                current = current.getAsJsonArray().get(index);
            } else if (current != null && current.isJsonObject() && current.getAsJsonObject().has(String.valueOf(segment))) {
                String key = String.valueOf(segment);
                out.add(List.copyOf(current.getAsJsonObject().keySet()).indexOf(key));
                current = current.getAsJsonObject().get(key);
            } else {
                return null;
            }
        }
        return out;
    }

    /**
     * Index segments as integers: JSON Schema validators may report them as numeric strings.
     * Keys stay strings when the value at the parent path is a mapping.
     */
    private List<Object> normalize(List<Object> segments) {
        List<Object> out = new ArrayList<>(segments.size());
        for (Object segment : segments) {
            if (segment instanceof String s && !s.isEmpty() && s.chars().allMatch(Character::isDigit) && s.length() < 10
                    && !locations.containsKey(append(out, s))) {
                out.add(Integer.parseInt(s));
            } else {
                out.add(segment instanceof Number n ? (Object) n.intValue() : segment);
            }
        }
        return out;
    }

    /**
     * Formats a path like JSONPath ({@code $.spec.containers[0].image}).
     *
     * @param path the path
     * @return the expression
     */
    public static String toJsonPath(List<Object> path) {
        StringBuilder out = new StringBuilder("$");
        for (Object segment : path) {
            if (segment instanceof Integer i) {
                out.append('[').append(i).append(']');
            } else {
                String key = String.valueOf(segment);
                if (key.matches("[A-Za-z_$][A-Za-z0-9_$-]*")) {
                    out.append('.').append(key);
                } else {
                    out.append("['").append(key.replace("\\", "\\\\").replace("'", "\\'")).append("']");
                }
            }
        }
        return out.toString();
    }

    /** Converts the YAML parser's code-point indexes into character offsets. */
    private static final class CodePoints {
        private final String text;
        private final boolean bmpOnly;
        private int[] charOffsets;

        CodePoints(String text) {
            this.text = text;
            this.bmpOnly = text.codePoints().allMatch(Character::isBmpCodePoint);
        }

        int start(Node node) {
            return node.getStartMark().map(m -> offset(m.getIndex())).orElse(0);
        }

        int end(Node node) {
            return node.getEndMark().map(m -> offset(m.getIndex())).orElse(start(node));
        }

        int offset(int codePointIndex) {
            if (bmpOnly) {
                return Math.min(codePointIndex, text.length());
            }
            if (charOffsets == null) {
                int count = text.codePointCount(0, text.length());
                charOffsets = new int[count + 1];
                int offset = 0;
                for (int i = 0; i < count; i++) {
                    charOffsets[i] = offset;
                    offset += Character.charCount(text.codePointAt(offset));
                }
                charOffsets[count] = text.length();
            }
            return charOffsets[Math.max(0, Math.min(codePointIndex, charOffsets.length - 1))];
        }
    }
}
