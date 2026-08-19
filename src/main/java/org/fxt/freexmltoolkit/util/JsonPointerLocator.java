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

package org.fxt.freexmltoolkit.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps a JSON pointer (RFC 6901) to the 1-based line of the addressed value in the
 * source text, so schema violations can navigate to their location in the editor.
 *
 * <p>Works on the raw text with a single-pass tolerant tokenizer (no DOM — Gson has no
 * position-aware tree): tracks lines, string escapes, and tolerates {@code //} and
 * {@code /* *}{@code /} comments plus trailing commas, so JSONC-flavored input does not
 * derail the scan. Purely lexical; never throws on malformed input (returns -1 instead).
 */
public final class JsonPointerLocator {

    private final String text;

    public JsonPointerLocator(String jsonText) {
        this.text = jsonText != null ? jsonText : "";
    }

    /**
     * @param segments instance path segments (String = object key, Integer = array index);
     *                 an empty list addresses the root value
     * @return 1-based line of the value at the given path, or -1 if not found
     */
    public int lineOf(List<Object> segments) {
        if (segments == null) {
            return -1;
        }
        try {
            return locate(new Cursor(), segments, 0);
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /**
     * Convenience overload for an RFC 6901 pointer string ({@code ""} = root).
     *
     * @return 1-based line of the addressed value, or -1 if not found
     */
    public int lineOf(String jsonPointer) {
        if (jsonPointer == null) {
            return -1;
        }
        if (jsonPointer.isEmpty()) {
            return lineOf(List.of());
        }
        if (!jsonPointer.startsWith("/")) {
            return -1;
        }
        List<Object> segments = new ArrayList<>();
        for (String raw : jsonPointer.substring(1).split("/", -1)) {
            segments.add(raw.replace("~1", "/").replace("~0", "~"));
        }
        return lineOf(segments);
    }

    private int locate(Cursor c, List<Object> segments, int depth) {
        c.skipWsAndComments();
        if (c.atEnd()) {
            return -1;
        }
        if (depth == segments.size()) {
            return c.line;
        }
        Object want = segments.get(depth);
        char ch = c.peek();
        if (ch == '{') {
            c.next();
            while (true) {
                c.skipWsAndComments();
                if (c.atEnd()) {
                    return -1;
                }
                char x = c.peek();
                if (x == '}') {
                    return -1;
                }
                if (x == ',') {
                    c.next();
                    continue;
                }
                if (x != '"' && x != '\'') {
                    return -1; // malformed member — give up on this container
                }
                String key = c.readString();
                c.skipWsAndComments();
                if (c.atEnd() || c.peek() != ':') {
                    return -1;
                }
                c.next();
                if (matchesKey(want, key)) {
                    return locate(c, segments, depth + 1);
                }
                c.skipValue();
            }
        }
        if (ch == '[') {
            c.next();
            int index = 0;
            while (true) {
                c.skipWsAndComments();
                if (c.atEnd()) {
                    return -1;
                }
                char x = c.peek();
                if (x == ']') {
                    return -1;
                }
                if (x == ',') {
                    c.next();
                    continue;
                }
                if (matchesIndex(want, index)) {
                    return locate(c, segments, depth + 1);
                }
                c.skipValue();
                index++;
            }
        }
        return -1; // scalar — the path expects children it cannot have
    }

    private static boolean matchesKey(Object want, String key) {
        // A pointer-string segment carries indices as strings; compare textually.
        return want != null && want.toString().equals(key);
    }

    private static boolean matchesIndex(Object want, int index) {
        if (want instanceof Integer i) {
            return i == index;
        }
        return want != null && want.toString().equals(String.valueOf(index));
    }

    /** Position/line-tracking scanner over {@link #text}. */
    private final class Cursor {
        int pos = 0;
        int line = 1;

        boolean atEnd() {
            return pos >= text.length();
        }

        char peek() {
            return text.charAt(pos);
        }

        void next() {
            if (text.charAt(pos) == '\n') {
                line++;
            }
            pos++;
        }

        void skipWsAndComments() {
            while (!atEnd()) {
                char ch = peek();
                if (Character.isWhitespace(ch)) {
                    next();
                } else if (ch == '/' && pos + 1 < text.length() && text.charAt(pos + 1) == '/') {
                    while (!atEnd() && peek() != '\n') {
                        next();
                    }
                } else if (ch == '/' && pos + 1 < text.length() && text.charAt(pos + 1) == '*') {
                    next();
                    next();
                    while (!atEnd() && !(peek() == '*' && pos + 1 < text.length() && text.charAt(pos + 1) == '/')) {
                        next();
                    }
                    if (!atEnd()) {
                        next();
                        next();
                    }
                } else {
                    return;
                }
            }
        }

        /** Reads a quoted string (assumes peek() is the quote) and returns its unescaped value. */
        String readString() {
            char quote = peek();
            next();
            StringBuilder sb = new StringBuilder();
            while (!atEnd()) {
                char ch = peek();
                if (ch == '\\') {
                    next();
                    if (atEnd()) {
                        break;
                    }
                    char esc = peek();
                    next();
                    switch (esc) {
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 <= text.length()) {
                                try {
                                    sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                                } catch (NumberFormatException ignored) {
                                    // tolerate malformed escape
                                }
                                for (int i = 0; i < 4 && !atEnd(); i++) {
                                    next();
                                }
                            }
                        }
                        default -> sb.append(esc); // covers \" \\ \/ and tolerates the rest
                    }
                } else if (ch == quote) {
                    next();
                    return sb.toString();
                } else {
                    sb.append(ch);
                    next();
                }
            }
            return sb.toString(); // unterminated string — tolerate
        }

        /** Skips one complete value (container, string, or primitive) including nesting. */
        void skipValue() {
            skipWsAndComments();
            if (atEnd()) {
                return;
            }
            char ch = peek();
            if (ch == '{' || ch == '[') {
                next();
                int depth = 1;
                while (!atEnd() && depth > 0) {
                    skipWsAndComments();
                    if (atEnd()) {
                        return;
                    }
                    char x = peek();
                    if (x == '"' || x == '\'') {
                        readString();
                    } else if (x == '{' || x == '[') {
                        depth++;
                        next();
                    } else if (x == '}' || x == ']') {
                        depth--;
                        next();
                    } else {
                        next();
                    }
                }
                return;
            }
            if (ch == '"' || ch == '\'') {
                readString();
                return;
            }
            while (!atEnd()) {
                char x = peek();
                if (x == ',' || x == '}' || x == ']' || Character.isWhitespace(x)) {
                    return;
                }
                next();
            }
        }
    }
}
