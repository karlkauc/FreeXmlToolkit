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

package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.ArrayList;
import java.util.List;

import org.fxt.freexmltoolkit.service.json.JsonTokenizer;
import org.fxt.freexmltoolkit.service.json.JsonTokenizer.Token;
import org.fxt.freexmltoolkit.util.JsonPointerLocator;

/**
 * Turns the validation problems of a JSON document into text ranges to underline: a JSON
 * Schema violation marks the member key (or scalar value / opening bracket) its instance
 * pointer addresses, a parse error the token at its line and column. UI-free.
 */
public final class JsonProblemMarkers {

    /**
     * One marker.
     *
     * @param start   offset of the first marked character
     * @param end     offset after the last marked character ({@code > start})
     * @param line    0-based line of {@code start}
     * @param message the problem message
     */
    public record Marker(int start, int end, int line, String message) {
    }

    private JsonProblemMarkers() {
    }

    /**
     * @param text     the document text the problems were found in
     * @param problems the problems (others than JSON / JSON Schema ones are placed by line)
     * @return the markers, in problem order; problems that cannot be placed are left out
     */
    public static List<Marker> compute(String text, List<ValidationProblem> problems) {
        List<Marker> markers = new ArrayList<>();
        if (text == null || text.isEmpty() || problems == null || problems.isEmpty()) {
            return markers;
        }
        int[] lineStarts = lineStarts(text);
        JsonPointerLocator locator = new JsonPointerLocator(text);
        List<Token> tokens = null;
        for (ValidationProblem problem : problems) {
            int[] range = null;
            String pointer = problem.context();
            if (pointer != null && (pointer.isEmpty() || pointer.startsWith("/"))
                    && "JSON Schema".equals(problem.source())) {
                var location = locator.locationOf(pointer);
                if (location != null) {
                    range = location.markRange(text);
                }
            }
            if (range == null && problem.line() > 0 && problem.line() <= lineStarts.length) {
                if (tokens == null) {
                    tokens = JsonTokenizer.tokenize(text);
                }
                range = rangeAtLine(text, lineStarts, tokens, problem.line(), problem.column());
            }
            if (range != null && range[1] > range[0]) {
                markers.add(new Marker(range[0], range[1], lineOf(lineStarts, range[0]), problem.message()));
            }
        }
        return markers;
    }

    /**
     * The token at a 1-based line and column (column 0 = the line's first non-blank token);
     * a position after the last token of the line marks the line's last character.
     */
    private static int[] rangeAtLine(String text, int[] lineStarts, List<Token> tokens, int line, int column) {
        int lineStart = lineStarts[line - 1];
        int lineEnd = line < lineStarts.length ? lineStarts[line] - 1 : text.length();
        if (lineEnd > lineStart && text.charAt(lineEnd - 1) == '\r') {
            lineEnd--;
        }
        int offset;
        if (column > 0) {
            // Gson reports the column after the offending character
            offset = Math.min(lineStart + Math.max(0, column - 2), Math.max(lineStart, lineEnd - 1));
        } else {
            offset = lineStart;
            while (offset < lineEnd && Character.isWhitespace(text.charAt(offset))) {
                offset++;
            }
        }
        if (lineEnd <= lineStart) {
            return new int[] {Math.max(0, lineStart - 1), Math.min(text.length(), lineStart)};
        }
        Token token = tokenAt(tokens, offset);
        if (token == null || token.kind() == JsonTokenizer.Kind.WHITESPACE) {
            int start = Math.min(offset, lineEnd - 1);
            return new int[] {start, start + 1};
        }
        // a multi-line token (block comment, string with line breaks) is cut at the line end
        return new int[] {Math.max(token.start(), lineStart), Math.min(token.end(), Math.max(lineEnd, offset + 1))};
    }

    private static Token tokenAt(List<Token> tokens, int offset) {
        int lo = 0;
        int hi = tokens.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            Token t = tokens.get(mid);
            if (offset < t.start()) {
                hi = mid - 1;
            } else if (offset >= t.end()) {
                lo = mid + 1;
            } else {
                return t;
            }
        }
        return null;
    }

    static int[] lineStarts(String text) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                starts.add(i + 1);
            }
        }
        return starts.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int lineOf(int[] lineStarts, int offset) {
        int idx = java.util.Arrays.binarySearch(lineStarts, offset);
        return idx >= 0 ? idx : -idx - 2;
    }
}
