package org.fxt.freexmltoolkit.service.yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits YAML text into tokens for syntax highlighting. Tolerant: it never fails, also not on
 * text that is not valid YAML (the editor highlights while the user types). Covers block and flow
 * style, plain / quoted / block scalars, comments, anchors, aliases, tags, directives and document
 * markers; plain scalars are classified with the YAML 1.2 core schema (null, booleans, numbers).
 *
 * <p>The tokens cover the text without gaps; {@link Kind#TEXT} is everything without a style
 * (whitespace, line breaks). UI-free.
 */
public final class YamlTokenizer {

    /** What a token is. */
    public enum Kind {
        /** A mapping key (plain or quoted). */
        KEY,
        /** A string value (plain, quoted or the lines of a block scalar). */
        STRING,
        /** A number in the core schema ({@code 12}, {@code 0x1F}, {@code 1.5e3}, {@code .inf}). */
        NUMBER,
        /** {@code true} / {@code false} in any case the core schema accepts. */
        BOOLEAN,
        /** {@code null}, {@code ~}. */
        NULL,
        /** A comment, from {@code #} to the end of the line. */
        COMMENT,
        /** {@code :}, {@code -}, {@code ?}, {@code ,}, block scalar indicators. */
        PUNCTUATION,
        /** Flow brackets {@code [ ] { }}. */
        BRACKET,
        /** An anchor {@code &name}. */
        ANCHOR,
        /** An alias {@code *name}. */
        ALIAS,
        /** A tag {@code !name}, {@code !!str}. */
        TAG,
        /** A directive line ({@code %YAML 1.2}) or a document marker ({@code ---}, {@code ...}). */
        DIRECTIVE,
        /** Whitespace, line breaks and anything else without a style. */
        TEXT
    }

    /**
     * A token.
     *
     * @param kind  its kind
     * @param start offset of its first character
     * @param end   offset after its last character
     */
    public record Token(Kind kind, int start, int end) {
    }

    private static final Pattern NULL = Pattern.compile("~|null|Null|NULL");
    private static final Pattern BOOLEAN = Pattern.compile("true|True|TRUE|false|False|FALSE");
    private static final Pattern NUMBER = Pattern.compile(
            "[-+]?[0-9]+|0o[0-7]+|0x[0-9a-fA-F]+"
                    + "|[-+]?(\\.[0-9]+|[0-9]+(\\.[0-9]*)?)([eE][-+]?[0-9]+)?"
                    + "|[-+]?\\.(inf|Inf|INF)|\\.(nan|NaN|NAN)");

    private final String text;
    private final int length;
    private final List<Token> tokens = new ArrayList<>();
    private int pos;
    private int textStart = -1;
    private int flowDepth;
    /** Indentation of the line where a block scalar started, or -1 outside one. */
    private int blockScalarParentIndent = -1;

    private YamlTokenizer(String text) {
        this.text = text;
        this.length = text.length();
    }

    /**
     * @param text the YAML text ({@code null} = empty)
     * @return the tokens, covering the whole text in order
     */
    public static List<Token> tokenize(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        YamlTokenizer tokenizer = new YamlTokenizer(text);
        tokenizer.run();
        return tokenizer.tokens;
    }

    private void run() {
        while (pos < length) {
            line();
        }
        flushText();
    }

    /** One line, starting at its first character. */
    private void line() {
        int lineStart = pos;
        int indent = 0;
        while (pos < length && text.charAt(pos) == ' ') {
            pos++;
            indent++;
        }
        if (blockScalarParentIndent >= 0) {
            if (atLineEnd() || indent > blockScalarParentIndent) {
                // a line of the block scalar (blank lines belong to it)
                int end = lineEnd();
                if (end > pos) {
                    plain(lineStart, pos);
                    add(Kind.STRING, pos, end);
                    pos = end;
                }
                newline();
                return;
            }
            blockScalarParentIndent = -1;
        }
        plain(lineStart, pos);
        if (flowDepth == 0 && pos == lineStart) {
            if (pos < length && text.charAt(pos) == '%') {
                int end = lineEnd();
                add(Kind.DIRECTIVE, pos, end);
                pos = end;
                newline();
                return;
            }
            if (startsMarker("---") || startsMarker("...")) {
                add(Kind.DIRECTIVE, pos, pos + 3);
                pos += 3;
            }
        }
        rest(indent);
        newline();
    }

    /** The rest of a line after its indentation. */
    private void rest(int indent) {
        while (pos < length) {
            char c = text.charAt(pos);
            if (c == '\n' || c == '\r') {
                return;
            }
            if (c == ' ' || c == '\t') {
                plainChar();
                continue;
            }
            if (c == '#' && (pos == 0 || isBlank(text.charAt(pos - 1)))) {
                int end = lineEnd();
                add(Kind.COMMENT, pos, end);
                pos = end;
                return;
            }
            if ((c == '-' || c == '?' || c == ':') && indicatorFollows(pos + 1)) {
                add(Kind.PUNCTUATION, pos, pos + 1);
                pos++;
                continue;
            }
            if (c == '[' || c == '{') {
                flowDepth++;
                add(Kind.BRACKET, pos, pos + 1);
                pos++;
                continue;
            }
            if (c == ']' || c == '}') {
                flowDepth = Math.max(0, flowDepth - 1);
                add(Kind.BRACKET, pos, pos + 1);
                pos++;
                continue;
            }
            if (c == ',' && flowDepth > 0) {
                add(Kind.PUNCTUATION, pos, pos + 1);
                pos++;
                continue;
            }
            if (c == ':' && flowDepth > 0) {
                add(Kind.PUNCTUATION, pos, pos + 1);
                pos++;
                continue;
            }
            if (c == '&' || c == '*') {
                int end = wordEnd(pos + 1);
                add(c == '&' ? Kind.ANCHOR : Kind.ALIAS, pos, end);
                pos = end;
                continue;
            }
            if (c == '!') {
                int end = wordEnd(pos + 1);
                add(Kind.TAG, pos, end);
                pos = end;
                continue;
            }
            if ((c == '|' || c == '>') && flowDepth == 0) {
                int end = pos + 1;
                while (end < length && "+-0123456789".indexOf(text.charAt(end)) >= 0) {
                    end++;
                }
                if (end >= length || isBlank(text.charAt(end)) || isBreak(text.charAt(end))) {
                    add(Kind.PUNCTUATION, pos, end);
                    pos = end;
                    blockScalarParentIndent = indent;
                    continue;
                }
            }
            if (c == '"' || c == '\'') {
                int end = quotedEnd(pos, c);
                add(keyFollows(end) ? Kind.KEY : Kind.STRING, pos, end);
                pos = end;
                continue;
            }
            plainScalar();
        }
    }

    /** A plain scalar: up to {@code ": "}, {@code " #"}, the line end or (in flow) a flow indicator. */
    private void plainScalar() {
        int start = pos;
        int end = pos;
        int lastNonBlank = pos;
        while (end < length) {
            char c = text.charAt(end);
            if (isBreak(c)) {
                break;
            }
            if (c == ':' && indicatorFollows(end + 1)) {
                break;
            }
            if (c == '#' && end > start && isBlank(text.charAt(end - 1))) {
                break;
            }
            if (flowDepth > 0 && (c == ',' || c == '[' || c == ']' || c == '{' || c == '}' || c == ':')) {
                break;
            }
            end++;
            if (!isBlank(c)) {
                lastNonBlank = end;
            }
        }
        if (lastNonBlank == start) {
            lastNonBlank = Math.min(length, start + 1); // never stall
        }
        String value = text.substring(start, lastNonBlank);
        Kind kind;
        if (keyFollows(lastNonBlank)) {
            kind = Kind.KEY;
        } else if (NULL.matcher(value).matches()) {
            kind = Kind.NULL;
        } else if (BOOLEAN.matcher(value).matches()) {
            kind = Kind.BOOLEAN;
        } else if (NUMBER.matcher(value).matches()) {
            kind = Kind.NUMBER;
        } else {
            kind = Kind.STRING;
        }
        add(kind, start, lastNonBlank);
        pos = lastNonBlank;
    }

    /** @return whether a {@code :} key indicator follows (after blanks) at {@code from} */
    private boolean keyFollows(int from) {
        int i = from;
        while (i < length && isBlank(text.charAt(i))) {
            i++;
        }
        return i < length && text.charAt(i) == ':' && (flowDepth > 0 || indicatorFollows(i + 1));
    }

    /** @return whether the character at {@code i} ends an indicator (blank, line end, or in flow a flow character) */
    private boolean indicatorFollows(int i) {
        if (i >= length) {
            return true;
        }
        char c = text.charAt(i);
        return isBlank(c) || isBreak(c) || flowDepth > 0 && (c == ',' || c == ']' || c == '}');
    }

    /** End of a quoted scalar starting at {@code start} (may span lines; unterminated runs to the end). */
    private int quotedEnd(int start, char quote) {
        int i = start + 1;
        while (i < length) {
            char c = text.charAt(i);
            if (quote == '"' && c == '\\') {
                i += 2;
                continue;
            }
            if (c == quote) {
                if (quote == '\'' && i + 1 < length && text.charAt(i + 1) == '\'') {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return length;
    }

    private int wordEnd(int from) {
        int i = from;
        while (i < length) {
            char c = text.charAt(i);
            if (isBlank(c) || isBreak(c) || flowDepth > 0 && (c == ',' || c == ']' || c == '}' || c == '[' || c == '{')) {
                break;
            }
            i++;
        }
        return i;
    }

    private boolean startsMarker(String marker) {
        return text.startsWith(marker, pos) && indicatorFollows(pos + 3);
    }

    private int lineEnd() {
        int i = pos;
        while (i < length && !isBreak(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private boolean atLineEnd() {
        return pos >= length || isBreak(text.charAt(pos));
    }

    private void newline() {
        if (pos < length && text.charAt(pos) == '\r') {
            plainChar();
        }
        if (pos < length && text.charAt(pos) == '\n') {
            plainChar();
        }
    }

    private void plainChar() {
        if (textStart < 0) {
            textStart = pos;
        }
        pos++;
    }

    private void plain(int start, int end) {
        if (end > start && textStart < 0) {
            textStart = start;
        }
    }

    private void add(Kind kind, int start, int end) {
        if (end <= start) {
            return;
        }
        if (textStart >= 0 && textStart < start) {
            tokens.add(new Token(Kind.TEXT, textStart, start));
        }
        textStart = -1;
        tokens.add(new Token(kind, start, end));
    }

    private void flushText() {
        if (textStart >= 0 && textStart < length) {
            tokens.add(new Token(Kind.TEXT, textStart, length));
        }
        textStart = -1;
    }

    private static boolean isBlank(char c) {
        return c == ' ' || c == '\t';
    }

    private static boolean isBreak(char c) {
        return c == '\n' || c == '\r';
    }
}
