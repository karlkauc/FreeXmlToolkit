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

import org.fxt.freexmltoolkit.service.json.JsonTokenizer;
import org.fxt.freexmltoolkit.service.json.JsonTokenizer.Kind;
import org.fxt.freexmltoolkit.service.json.JsonTokenizer.Token;

/**
 * Maps a JSON pointer (RFC 6901) to the source location of the addressed value, so schema
 * violations can navigate to and be underlined in the editor.
 *
 * <p>Works on the tokens of {@link JsonTokenizer} (no DOM — Gson has no position-aware
 * tree), so comments, trailing commas, single-quoted strings and unquoted JSON5 keys do not
 * derail it. Never throws on malformed input (returns -1 / {@code null} instead).
 */
public final class JsonPointerLocator {

    /**
     * The source range of a located value.
     *
     * @param line       1-based line of the value's first character
     * @param valueStart offset of the value's first character
     * @param valueEnd   offset after the value's last character
     * @param keyStart   offset of the member key's first character, or -1 for an array item / the root
     * @param keyEnd     offset after the member key, or -1
     */
    public record Location(int line, int valueStart, int valueEnd, int keyStart, int keyEnd) {

        /**
         * The range to underline for a problem: the member key when there is one (an object's
         * whole range would mark half the document), else a scalar value, else the opening bracket.
         *
         * @param text the source text
         * @return {@code [start, end)}
         */
        public int[] markRange(String text) {
            if (keyStart >= 0) {
                return new int[] {keyStart, keyEnd};
            }
            char first = valueStart < text.length() ? text.charAt(valueStart) : ' ';
            if (first == '{' || first == '[') {
                return new int[] {valueStart, valueStart + 1};
            }
            return new int[] {valueStart, Math.max(valueStart + 1, valueEnd)};
        }
    }

    private final String text;
    private List<Token> significant;
    private int[] lineStarts;

    public JsonPointerLocator(String jsonText) {
        this.text = jsonText != null ? jsonText : "";
    }

    /**
     * @param segments instance path segments (String = object key, Integer = array index);
     *                 an empty list addresses the root value
     * @return 1-based line of the value at the given path, or -1 if not found
     */
    public int lineOf(List<Object> segments) {
        Location location = locationOf(segments);
        return location == null ? -1 : location.line();
    }

    /**
     * Convenience overload for an RFC 6901 pointer string ({@code ""} = root).
     *
     * @return 1-based line of the addressed value, or -1 if not found
     */
    public int lineOf(String jsonPointer) {
        Location location = locationOf(jsonPointer);
        return location == null ? -1 : location.line();
    }

    /**
     * @param jsonPointer an RFC 6901 pointer ({@code ""} = root)
     * @return the addressed value's location, or {@code null} if not found
     */
    public Location locationOf(String jsonPointer) {
        List<Object> segments = segments(jsonPointer);
        return segments == null ? null : locationOf(segments);
    }

    /**
     * @param segments instance path segments (String = object key, Integer = array index)
     * @return the addressed value's location, or {@code null} if not found
     */
    public Location locationOf(List<Object> segments) {
        if (segments == null) {
            return null;
        }
        try {
            return locate(segments);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Splits an RFC 6901 pointer into its unescaped segments.
     *
     * @return the segments, or {@code null} for a malformed pointer
     */
    public static List<Object> segments(String jsonPointer) {
        if (jsonPointer == null || (!jsonPointer.isEmpty() && !jsonPointer.startsWith("/"))) {
            return null;
        }
        List<Object> segments = new ArrayList<>();
        if (!jsonPointer.isEmpty()) {
            for (String raw : jsonPointer.substring(1).split("/", -1)) {
                segments.add(raw.replace("~1", "/").replace("~0", "~"));
            }
        }
        return segments;
    }

    private Location locate(List<Object> segments) {
        if (significant == null) {
            significant = JsonTokenizer.tokenize(text).stream().filter(t -> !t.kind().isTrivia()).toList();
        }
        List<Token> tokens = significant;
        if (tokens.isEmpty()) {
            return null;
        }
        int i = 0;
        Token key = null;
        for (Object want : segments) {
            Token open = tokens.get(i);
            key = null;
            if (open.kind() == Kind.BEGIN_OBJECT) {
                int j = i + 1;
                int found = -1;
                while (j < tokens.size() && tokens.get(j).kind() != Kind.END_OBJECT) {
                    if (tokens.get(j).kind() == Kind.COMMA) {
                        j++;
                        continue;
                    }
                    Token name = tokens.get(j);
                    if (j + 1 >= tokens.size() || tokens.get(j + 1).kind() != Kind.COLON) {
                        return null; // malformed member — give up on this container
                    }
                    if (want != null && want.toString().equals(JsonTokenizer.unquote(name))) {
                        found = j + 2;
                        key = name;
                        break;
                    }
                    j = skipValue(tokens, j + 2);
                }
                if (found < 0 || found >= tokens.size()) {
                    return null;
                }
                i = found;
            } else if (open.kind() == Kind.BEGIN_ARRAY) {
                int j = i + 1;
                int index = 0;
                int found = -1;
                while (j < tokens.size() && tokens.get(j).kind() != Kind.END_ARRAY) {
                    if (tokens.get(j).kind() == Kind.COMMA) {
                        j++;
                        continue;
                    }
                    if (want != null && want.toString().equals(String.valueOf(index))) {
                        found = j;
                        break;
                    }
                    j = skipValue(tokens, j);
                    index++;
                }
                if (found < 0) {
                    return null;
                }
                i = found;
            } else {
                return null; // scalar — the path expects children it cannot have
            }
        }
        Token first = tokens.get(i);
        if (!isValueStart(first.kind())) {
            return null;
        }
        int after = skipValue(tokens, i);
        Token last = tokens.get(Math.min(after, tokens.size()) - 1);
        return new Location(lineAt(first.start()), first.start(), last.end(),
                key == null ? -1 : key.start(), key == null ? -1 : key.end());
    }

    private static boolean isValueStart(Kind kind) {
        return switch (kind) {
            case BEGIN_OBJECT, BEGIN_ARRAY, STRING, NUMBER, LITERAL, IDENTIFIER -> true;
            default -> false;
        };
    }

    /** @return the index after the value starting at {@code i} (a container with its contents) */
    private static int skipValue(List<Token> tokens, int i) {
        if (i >= tokens.size()) {
            return tokens.size();
        }
        Kind kind = tokens.get(i).kind();
        if (kind != Kind.BEGIN_OBJECT && kind != Kind.BEGIN_ARRAY) {
            return i + 1;
        }
        int depth = 0;
        for (int j = i; j < tokens.size(); j++) {
            switch (tokens.get(j).kind()) {
                case BEGIN_OBJECT, BEGIN_ARRAY -> depth++;
                case END_OBJECT, END_ARRAY -> {
                    if (--depth == 0) {
                        return j + 1;
                    }
                }
                default -> {
                    // inside the container
                }
            }
        }
        return tokens.size();
    }

    /** @return the 1-based line of an offset */
    private int lineAt(int offset) {
        if (lineStarts == null) {
            List<Integer> starts = new ArrayList<>();
            starts.add(0);
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n') {
                    starts.add(i + 1);
                }
            }
            lineStarts = starts.stream().mapToInt(Integer::intValue).toArray();
        }
        int idx = java.util.Arrays.binarySearch(lineStarts, offset);
        return (idx >= 0 ? idx : -idx - 2) + 1;
    }
}
