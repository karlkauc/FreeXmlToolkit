/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2026.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.service.json;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.fxt.freexmltoolkit.service.json.JsonTokenizer.Kind;
import org.fxt.freexmltoolkit.service.json.JsonTokenizer.Token;

/**
 * Formats JSON, JSONC and JSON5 text without losing anything but whitespace: comments stay
 * where they were (before a member, or after it on the same line), strings and numbers keep
 * their source spelling (quote style, escapes, hex numbers), unquoted keys stay unquoted and a
 * container that had a trailing comma keeps it. Gson-based formatting dropped all of that.
 *
 * <p>Works on a small concrete syntax tree built from {@link JsonTokenizer}'s tokens; the same
 * tree sorts object members ({@link #sortKeys}), moving each member's comments with it.
 */
public final class JsonFormatter {

    private JsonFormatter() {
    }

    /**
     * Thrown when the text is not a well-formed JSON / JSONC / JSON5 value (a
     * {@link com.google.gson.JsonParseException}, so telemetry counts it as the user's input).
     */
    public static final class FormatException extends com.google.gson.JsonParseException {
        private final int offset;

        FormatException(String message, int offset) {
            super(message);
            this.offset = offset;
        }

        /** @return the offset of the offending token (or the text length at the end) */
        public int offset() {
            return offset;
        }
    }

    /**
     * Pretty-prints the text: one member / item per line, {@code indent} spaces per level,
     * {@code "key": value} with one blank after the colon.
     *
     * @param text   the text
     * @param indent spaces per level (at least 1)
     * @return the formatted text, ending with a line break iff the input did
     * @throws FormatException when the text is not well-formed
     */
    public static String format(String text, int indent) {
        Document document = parse(text);
        return print(document, Math.max(1, indent), text.endsWith("\n"));
    }

    /**
     * Sorts the members of objects by key (case-insensitive, then exact), carrying each
     * member's comments along, and formats the result.
     *
     * @param text      the text
     * @param recursive whether nested objects (also inside arrays) are sorted too
     * @param indent    spaces per level
     * @return the sorted, formatted text
     * @throws FormatException when the text is not well-formed
     */
    public static String sortKeys(String text, boolean recursive, int indent) {
        Document document = parse(text);
        sort(document.root, recursive, true);
        return print(document, Math.max(1, indent), text.endsWith("\n"));
    }

    // ==================== Syntax tree ====================

    /** A value: an object, an array or a scalar token. */
    private sealed interface Value permits Obj, Arr, Scalar {
    }

    /** One member or array item with its comments. */
    private static final class Entry {
        final List<Token> leading = new ArrayList<>();
        Token key;
        Value value;
        final List<Token> trailing = new ArrayList<>();
    }

    private static final class Obj implements Value {
        final List<Entry> entries = new ArrayList<>();
        /** Comments after the last member (before the closing bracket). */
        final List<Token> dangling = new ArrayList<>();
        boolean trailingComma;
    }

    private static final class Arr implements Value {
        final List<Entry> entries = new ArrayList<>();
        final List<Token> dangling = new ArrayList<>();
        boolean trailingComma;
    }

    private record Scalar(Token token) implements Value {
    }

    private static final class Document {
        final List<Token> leading = new ArrayList<>();
        Value root;
        final List<Token> trailing = new ArrayList<>();
    }

    // ==================== Parsing ====================

    private static Document parse(String text) {
        Parser parser = new Parser(JsonTokenizer.tokenize(text), text == null ? 0 : text.length());
        Document document = new Document();
        parser.comments(document.leading, false);
        if (parser.atEnd()) {
            throw new FormatException("The document is empty", 0);
        }
        document.root = parser.value();
        parser.comments(document.trailing, false);
        if (!parser.atEnd()) {
            Token extra = parser.peek();
            throw new FormatException("Unexpected '" + extra.text() + "' after the value", extra.start());
        }
        return document;
    }

    private static final class Parser {
        private final List<Token> tokens;
        private final int length;
        private int i;

        Parser(List<Token> tokens, int length) {
            this.tokens = tokens;
            this.length = length;
        }

        boolean atEnd() {
            skipWhitespace();
            return i >= tokens.size();
        }

        Token peek() {
            skipWhitespace();
            return i < tokens.size() ? tokens.get(i) : null;
        }

        private void skipWhitespace() {
            while (i < tokens.size() && tokens.get(i).kind() == Kind.WHITESPACE) {
                i++;
            }
        }

        /**
         * Collects comments. With {@code sameLineOnly} only those that start on the current line
         * (no line break in the whitespace before them) - a member's trailing comments.
         */
        void comments(List<Token> into, boolean sameLineOnly) {
            while (i < tokens.size()) {
                Token t = tokens.get(i);
                if (t.kind() == Kind.WHITESPACE) {
                    if (sameLineOnly && t.text().indexOf('\n') >= 0) {
                        return;
                    }
                    i++;
                } else if (t.kind().isComment()) {
                    into.add(t);
                    i++;
                    if (sameLineOnly && t.kind() == Kind.LINE_COMMENT) {
                        return;
                    }
                } else {
                    return;
                }
            }
        }

        private FormatException error(String expected) {
            Token t = peek();
            return t == null
                    ? new FormatException("Unexpected end of the document, expected " + expected, length)
                    : new FormatException("Unexpected '" + t.text() + "', expected " + expected, t.start());
        }

        Value value() {
            Token t = peek();
            if (t == null) {
                throw error("a value");
            }
            switch (t.kind()) {
                case BEGIN_OBJECT -> {
                    i++;
                    return object();
                }
                case BEGIN_ARRAY -> {
                    i++;
                    return array();
                }
                case STRING, NUMBER, LITERAL, IDENTIFIER -> {
                    if (t.unterminated()) {
                        throw new FormatException("Unterminated string", t.start());
                    }
                    i++;
                    return new Scalar(t);
                }
                default -> throw error("a value");
            }
        }

        private Obj object() {
            Obj obj = new Obj();
            while (true) {
                List<Token> leading = new ArrayList<>();
                comments(leading, false);
                Token t = peek();
                if (t == null) {
                    throw error("'}'");
                }
                if (t.kind() == Kind.END_OBJECT) {
                    i++;
                    obj.dangling.addAll(leading);
                    return obj;
                }
                if (t.kind() != Kind.STRING && t.kind() != Kind.IDENTIFIER && t.kind() != Kind.LITERAL
                        && t.kind() != Kind.NUMBER) {
                    throw error("a key");
                }
                Entry entry = new Entry();
                entry.leading.addAll(leading);
                entry.key = t;
                i++;
                comments(entry.leading, false);
                Token colon = peek();
                if (colon == null || colon.kind() != Kind.COLON) {
                    throw error("':'");
                }
                i++;
                comments(entry.leading, false);
                entry.value = value();
                obj.entries.add(entry);
                if (separator(entry, Kind.END_OBJECT)) {
                    obj.trailingComma |= trailingCommaJustSeen;
                    continue;
                }
                obj.dangling.addAll(beforeClose);
                i++; // the closing bracket
                return obj;
            }
        }

        private Arr array() {
            Arr arr = new Arr();
            while (true) {
                List<Token> leading = new ArrayList<>();
                comments(leading, false);
                Token t = peek();
                if (t == null) {
                    throw error("']'");
                }
                if (t.kind() == Kind.END_ARRAY) {
                    i++;
                    arr.dangling.addAll(leading);
                    return arr;
                }
                Entry entry = new Entry();
                entry.leading.addAll(leading);
                entry.value = value();
                arr.entries.add(entry);
                if (separator(entry, Kind.END_ARRAY)) {
                    arr.trailingComma |= trailingCommaJustSeen;
                    continue;
                }
                arr.dangling.addAll(beforeClose);
                i++;
                return arr;
            }
        }

        private boolean trailingCommaJustSeen;
        /** Comments on lines of their own between the last entry and the closing bracket. */
        private List<Token> beforeClose = List.of();

        /**
         * Reads what follows an entry: its same-line comments, then a comma (true: more may follow)
         * or the closing bracket (false, not consumed). Comments after the comma on the same line
         * belong to the entry too.
         */
        private boolean separator(Entry entry, Kind close) {
            trailingCommaJustSeen = false;
            comments(entry.trailing, true);
            List<Token> before = new ArrayList<>();
            comments(before, false);
            Token t = peek();
            if (t != null && t.kind() == Kind.COMMA) {
                entry.trailing.addAll(before);
                i++;
                comments(entry.trailing, true);
                Token next = peek();
                // a comment on its own line after the comma belongs to the next entry
                trailingCommaJustSeen = next != null && next.kind() == close
                        || (next != null && next.kind().isComment() && closesAfterComments(close));
                return true;
            }
            if (t != null && t.kind() == close) {
                // comments between the value and the bracket (own lines) stay before the bracket
                beforeClose = before;
                return false;
            }
            throw error(close == Kind.END_OBJECT ? "',' or '}'" : "',' or ']'");
        }

        /** @return whether only comments and whitespace stand between here and {@code close} */
        private boolean closesAfterComments(Kind close) {
            for (int j = i; j < tokens.size(); j++) {
                Kind kind = tokens.get(j).kind();
                if (!kind.isTrivia()) {
                    return kind == close;
                }
            }
            return false;
        }
    }

    // ==================== Sorting ====================

    private static final Collator COLLATOR = Collator.getInstance(Locale.ROOT);

    /** Case-insensitive natural order, then the exact spelling (stable for keys differing in case). */
    public static final Comparator<String> KEY_ORDER = Comparator.<String, String>comparing(
            k -> k.toLowerCase(Locale.ROOT), COLLATOR).thenComparing(Comparator.naturalOrder());

    private static void sort(Value value, boolean recursive, boolean top) {
        if (value instanceof Obj obj) {
            if (top || recursive) {
                obj.entries.sort(Comparator.comparing(e -> JsonTokenizer.unquote(e.key), KEY_ORDER));
            }
            if (recursive) {
                obj.entries.forEach(e -> sort(e.value, true, false));
            }
        } else if (value instanceof Arr arr && recursive) {
            arr.entries.forEach(e -> sort(e.value, true, false));
        }
    }

    // ==================== Printing ====================

    private static String print(Document document, int indent, boolean finalNewline) {
        StringBuilder out = new StringBuilder();
        for (Token comment : document.leading) {
            out.append(comment.text()).append('\n');
        }
        printValue(document.root, 0, indent, out);
        for (Token comment : document.trailing) {
            out.append(comment.kind() == Kind.LINE_COMMENT ? " " : "\n").append(comment.text());
        }
        if (finalNewline) {
            out.append('\n');
        }
        return out.toString();
    }

    private static void printValue(Value value, int depth, int indent, StringBuilder out) {
        switch (value) {
            case Scalar scalar -> out.append(scalar.token().text());
            case Obj obj -> printContainer('{', '}', obj.entries, obj.dangling, obj.trailingComma, depth, indent, out);
            case Arr arr -> printContainer('[', ']', arr.entries, arr.dangling, arr.trailingComma, depth, indent, out);
        }
    }

    private static void printContainer(char open, char close, List<Entry> entries, List<Token> dangling,
                                       boolean trailingComma, int depth, int indent, StringBuilder out) {
        out.append(open);
        if (entries.isEmpty() && dangling.isEmpty()) {
            out.append(close);
            return;
        }
        String inner = " ".repeat((depth + 1) * indent);
        for (int n = 0; n < entries.size(); n++) {
            Entry entry = entries.get(n);
            out.append('\n');
            for (Token comment : entry.leading) {
                out.append(inner).append(comment.text()).append('\n');
            }
            out.append(inner);
            if (entry.key != null) {
                out.append(entry.key.text()).append(": ");
            }
            printValue(entry.value, depth + 1, indent, out);
            boolean last = n == entries.size() - 1;
            if (!last || trailingComma) {
                out.append(',');
            }
            for (int c = 0; c < entry.trailing.size(); c++) {
                Token comment = entry.trailing.get(c);
                out.append(' ').append(comment.text());
                if (comment.kind() == Kind.LINE_COMMENT && c < entry.trailing.size() - 1) {
                    // a line comment ends the line: put what follows on lines of its own
                    out.append('\n').append(inner);
                }
            }
        }
        for (Token comment : dangling) {
            out.append('\n').append(inner).append(comment.text());
        }
        out.append('\n').append(" ".repeat(depth * indent)).append(close);
    }
}
