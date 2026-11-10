package org.fxt.freexmltoolkit.controls.jsoneditor.model;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Maps between positions in JSON text and the {@link JsonNode} model without a full parse: one
 * forward scan finds the source range of every value (an object member's range starts at its key).
 * The model keeps the members and items in source order, so a node is addressed by its child-index
 * path from the root value. Comments ({@code //}, {@code /* *&#47;}) are skipped; on malformed text
 * the scan returns what it read up to the error.
 */
public final class JsonTextLocator {

    /** A value's source range {@code [start, end)} and the ranges of its members or items. */
    public record Span(int start, int end, List<Span> children) {
    }

    private final String text;
    private int pos;

    private JsonTextLocator(String text) {
        this.text = text;
    }

    /**
     * @param text the JSON text
     * @return the root value's span, or {@code null} when the text holds no value
     */
    public static Span scan(String text) {
        if (text == null) {
            return null;
        }
        JsonTextLocator locator = new JsonTextLocator(text);
        try {
            locator.skipWhitespace();
            return locator.pos < text.length() ? locator.value(locator.pos) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * @param root   the root span from {@link #scan}
     * @param offset a caret offset
     * @return the child-index path of the innermost value containing {@code offset} (empty = the
     *         root value), or {@code null} when the offset lies outside the root value
     */
    public static List<Integer> pathAt(Span root, int offset) {
        if (root == null || offset < root.start() || offset > root.end()) {
            return null;
        }
        List<Integer> path = new ArrayList<>();
        Span current = root;
        boolean descended = true;
        while (descended) {
            descended = false;
            for (int i = 0; i < current.children().size(); i++) {
                Span child = current.children().get(i);
                if (offset >= child.start() && offset < child.end()) {
                    path.add(i);
                    current = child;
                    descended = true;
                    break;
                }
            }
        }
        return path;
    }

    /**
     * @param root the root span from {@link #scan}
     * @param path a child-index path
     * @return the span at the path, or {@code null} when it does not exist
     */
    public static Span spanAt(Span root, List<Integer> path) {
        if (root == null || path == null) {
            return null;
        }
        Span current = root;
        for (int index : path) {
            if (index < 0 || index >= current.children().size()) {
                return null;
            }
            current = current.children().get(index);
        }
        return current;
    }

    /**
     * @param node a model node
     * @return its child-index path from the document's root value, or {@code null} when it is not
     *         inside a document's root value
     */
    public static List<Integer> pathOf(JsonNode node) {
        if (node == null || node instanceof JsonDocument) {
            return null;
        }
        LinkedList<Integer> path = new LinkedList<>();
        JsonNode current = node;
        while (current.getParent() != null && !(current.getParent() instanceof JsonDocument)) {
            int index = current.getParent().indexOf(current);
            if (index < 0) {
                return null;
            }
            path.addFirst(index);
            current = current.getParent();
        }
        return path;
    }

    /**
     * @param document a model document
     * @param path     a child-index path from its root value
     * @return the node at the path, or {@code null} when it does not exist
     */
    public static JsonNode nodeAt(JsonDocument document, List<Integer> path) {
        if (document == null || document.getRootValue() == null || path == null) {
            return null;
        }
        JsonNode current = document.getRootValue();
        for (int index : path) {
            current = current.getChild(index);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private Span value(int start) {
        char c = text.charAt(pos);
        if (c == '{') {
            return container(start, true);
        }
        if (c == '[') {
            return container(start, false);
        }
        if (c == '"' || c == '\'') {
            skipString();
        } else {
            while (pos < text.length() && ",]}".indexOf(text.charAt(pos)) < 0
                    && !Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }
        return new Span(start, pos, List.of());
    }

    private Span container(int start, boolean object) {
        pos++; // the opening brace or bracket
        List<Span> children = new ArrayList<>();
        while (true) {
            skipWhitespace();
            if (pos >= text.length()) {
                return new Span(start, pos, children);
            }
            char c = text.charAt(pos);
            if (c == '}' || c == ']') { // its own close, or a mismatched one (malformed text)
                pos++;
                return new Span(start, pos, children);
            }
            if (c == ',') {
                pos++;
                continue;
            }
            int memberStart = pos;
            if (object) {
                skipString(); // the key
                skipWhitespace();
                if (pos < text.length() && text.charAt(pos) == ':') {
                    pos++;
                }
                skipWhitespace();
                if (pos >= text.length()) {
                    return new Span(start, pos, children);
                }
            }
            Span value = value(pos);
            children.add(new Span(memberStart, value.end(), value.children()));
        }
    }

    private void skipString() {
        char quote = text.charAt(pos);
        if (quote != '"' && quote != '\'') {
            // an unquoted (lenient) key
            while (pos < text.length() && text.charAt(pos) != ':' && !Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
            return;
        }
        pos++;
        while (pos < text.length()) {
            char c = text.charAt(pos++);
            if (c == '\\') {
                pos++;
            } else if (c == quote) {
                return;
            }
        }
    }

    private void skipWhitespace() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (Character.isWhitespace(c)) {
                pos++;
            } else if (c == '/' && pos + 1 < text.length() && text.charAt(pos + 1) == '/') {
                while (pos < text.length() && text.charAt(pos) != '\n') {
                    pos++;
                }
            } else if (c == '/' && pos + 1 < text.length() && text.charAt(pos + 1) == '*') {
                int endComment = text.indexOf("*/", pos + 2);
                pos = endComment < 0 ? text.length() : endComment + 2;
            } else {
                return;
            }
        }
    }
}
