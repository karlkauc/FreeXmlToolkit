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

import java.util.ArrayList;
import java.util.List;

/**
 * A lossless, tolerant tokenizer for JSON, JSONC and JSON5 text.
 *
 * <p>Every character of the input belongs to exactly one token, so concatenating the
 * tokens' texts gives the input back. It never throws: an unterminated string or comment
 * ends at the end of the text, an unknown character becomes an {@link Kind#ERROR} token.
 * Callers (format detection, JSON5 → JSON conversion, reindenting, folding, the caret
 * context) work on the token list instead of regular expressions, so a {@code //} inside
 * a string is never taken for a comment.
 */
public final class JsonTokenizer {

    /** Token kinds. */
    public enum Kind {
        BEGIN_OBJECT, END_OBJECT, BEGIN_ARRAY, END_ARRAY, COLON, COMMA,
        /** A double- or single-quoted string (see {@link Token#singleQuoted()}). */
        STRING,
        /** A number, including the JSON5 forms (hex, leading/trailing dot, sign, Infinity, NaN). */
        NUMBER,
        /** {@code true}, {@code false} or {@code null}. */
        LITERAL,
        /** Any other bare word, e.g. a JSON5 unquoted key. */
        IDENTIFIER,
        LINE_COMMENT, BLOCK_COMMENT, WHITESPACE,
        /** A character that cannot start any token. */
        ERROR;

        /** @return whether the token carries no data (whitespace or a comment) */
        public boolean isTrivia() {
            return this == WHITESPACE || this == LINE_COMMENT || this == BLOCK_COMMENT;
        }

        /** @return whether the token is a comment */
        public boolean isComment() {
            return this == LINE_COMMENT || this == BLOCK_COMMENT;
        }
    }

    /**
     * One token.
     *
     * @param kind  the kind
     * @param start the offset of its first character
     * @param end   the offset after its last character
     * @param text  its source text
     */
    public record Token(Kind kind, int start, int end, String text) {

        /** @return whether this is a string written with single quotes (JSON5) */
        public boolean singleQuoted() {
            return kind == Kind.STRING && text.startsWith("'");
        }

        /** @return whether this is a string whose closing quote is missing */
        public boolean unterminated() {
            if (kind != Kind.STRING) {
                return kind == Kind.BLOCK_COMMENT && !text.endsWith("*/");
            }
            if (text.length() < 2 || text.charAt(text.length() - 1) != text.charAt(0)) {
                return true;
            }
            int backslashes = 0;
            for (int i = text.length() - 2; i > 0 && text.charAt(i) == '\\'; i--) {
                backslashes++;
            }
            return backslashes % 2 == 1;
        }

        /** @return whether the token is a number in a form strict JSON does not allow */
        public boolean json5Number() {
            if (kind != Kind.NUMBER) {
                return false;
            }
            String t = text.startsWith("-") ? text.substring(1) : text;
            return text.startsWith("+") || t.startsWith(".") || t.endsWith(".")
                    || t.startsWith("0x") || t.startsWith("0X") || t.equals("Infinity") || t.equals("NaN")
                    || (t.length() > 1 && t.charAt(0) == '0' && Character.isDigit(t.charAt(1)));
        }
    }

    private JsonTokenizer() {
    }

    /**
     * Splits {@code text} into tokens.
     *
     * @param text the text, may be {@code null}
     * @return all tokens in order (empty for empty text)
     */
    public static List<Token> tokenize(String text) {
        List<Token> tokens = new ArrayList<>();
        if (text == null) {
            return tokens;
        }
        int n = text.length();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            int start = i;
            Kind kind;
            switch (c) {
                case '{' -> { kind = Kind.BEGIN_OBJECT; i++; }
                case '}' -> { kind = Kind.END_OBJECT; i++; }
                case '[' -> { kind = Kind.BEGIN_ARRAY; i++; }
                case ']' -> { kind = Kind.END_ARRAY; i++; }
                case ':' -> { kind = Kind.COLON; i++; }
                case ',' -> { kind = Kind.COMMA; i++; }
                case '"', '\'' -> { kind = Kind.STRING; i = endOfString(text, i); }
                default -> {
                    if (Character.isWhitespace(c) || c == '﻿') {
                        kind = Kind.WHITESPACE;
                        while (i < n && (Character.isWhitespace(text.charAt(i)) || text.charAt(i) == '﻿')) {
                            i++;
                        }
                    } else if (c == '/' && i + 1 < n && text.charAt(i + 1) == '/') {
                        kind = Kind.LINE_COMMENT;
                        i = endOfLine(text, i);
                    } else if (c == '#') {
                        // Gson's lenient mode (and YAML-minded authors) accept '#' comments
                        kind = Kind.LINE_COMMENT;
                        i = endOfLine(text, i);
                    } else if (c == '/' && i + 1 < n && text.charAt(i + 1) == '*') {
                        kind = Kind.BLOCK_COMMENT;
                        int close = text.indexOf("*/", i + 2);
                        i = close < 0 ? n : close + 2;
                    } else if (isNumberStart(text, i)) {
                        kind = Kind.NUMBER;
                        i = endOfNumber(text, i);
                    } else if (Character.isLetter(c) || c == '_' || c == '$') {
                        while (i < n && isWordChar(text.charAt(i))) {
                            i++;
                        }
                        String word = text.substring(start, i);
                        kind = switch (word) {
                            case "true", "false", "null" -> Kind.LITERAL;
                            case "Infinity", "NaN" -> Kind.NUMBER;
                            default -> Kind.IDENTIFIER;
                        };
                    } else {
                        kind = Kind.ERROR;
                        i++;
                    }
                }
            }
            tokens.add(new Token(kind, start, i, text.substring(start, i)));
        }
        return tokens;
    }

    /**
     * The value of a string token (quotes removed, escapes resolved) or the text of any other
     * token (a bare JSON5 key). Tolerates a missing closing quote and malformed escapes.
     *
     * @param token the token
     * @return its value
     */
    public static String unquote(Token token) {
        String t = token.text();
        if (token.kind() != Kind.STRING) {
            return t;
        }
        boolean closed = !token.unterminated();
        String body = t.substring(1, closed ? t.length() - 1 : t.length());
        if (body.indexOf('\\') < 0) {
            return body;
        }
        StringBuilder sb = new StringBuilder(body.length());
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (ch != '\\' || i + 1 >= body.length()) {
                sb.append(ch);
                continue;
            }
            char esc = body.charAt(++i);
            switch (esc) {
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    if (i + 4 < body.length()) {
                        try {
                            sb.append((char) Integer.parseInt(body.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (NumberFormatException ignored) {
                            // tolerate a malformed escape
                        }
                    }
                }
                default -> sb.append(esc); // covers \" \\ \/ \' and tolerates the rest
            }
        }
        return sb.toString();
    }

    private static int endOfLine(String text, int i) {
        int n = text.length();
        while (i < n && text.charAt(i) != '\n' && text.charAt(i) != '\r') {
            i++;
        }
        return i;
    }

    private static int endOfString(String text, int i) {
        char quote = text.charAt(i);
        int n = text.length();
        i++;
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == quote) {
                return i + 1;
            }
            // a raw line break does not end the string: Gson's lenient reader accepts it
            i++;
        }
        return n;
    }

    private static boolean isNumberStart(String text, int i) {
        char c = text.charAt(i);
        if (Character.isDigit(c)) {
            return true;
        }
        int n = text.length();
        if ((c == '-' || c == '+') && i + 1 < n) {
            char next = text.charAt(i + 1);
            return Character.isDigit(next) || next == '.' || next == 'I' || next == 'N';
        }
        return c == '.' && i + 1 < n && Character.isDigit(text.charAt(i + 1));
    }

    private static int endOfNumber(String text, int i) {
        int n = text.length();
        if (text.charAt(i) == '-' || text.charAt(i) == '+') {
            i++;
        }
        if (text.startsWith("Infinity", i)) {
            return i + "Infinity".length();
        }
        if (text.startsWith("NaN", i)) {
            return i + "NaN".length();
        }
        while (i < n) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '.' || c == '_') {
                i++;
            } else if ((c == '+' || c == '-') && (text.charAt(i - 1) == 'e' || text.charAt(i - 1) == 'E')
                    && !text.startsWith("0x", i - 2) && !text.startsWith("0X", i - 2)) {
                i++;
            } else {
                break;
            }
        }
        return i;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '-' || c == '.';
    }
}
