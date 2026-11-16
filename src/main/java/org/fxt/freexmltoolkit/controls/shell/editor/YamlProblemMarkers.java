package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.ArrayList;
import java.util.List;

import org.fxt.freexmltoolkit.service.yaml.YamlDocument;
import org.fxt.freexmltoolkit.service.yaml.YamlTokenizer;
import org.fxt.freexmltoolkit.util.JsonPointerLocator;

/**
 * Turns the validation problems of a YAML document into text ranges to underline: a JSON Schema
 * violation marks the key (or, for a sequence item or the root, the first line of the value) its
 * instance pointer addresses, a syntax error the token at its line and column. UI-free.
 */
public final class YamlProblemMarkers {

    private YamlProblemMarkers() {
    }

    /**
     * @param text     the document text the problems were found in
     * @param problems the problems
     * @return the markers, in problem order; problems that cannot be placed are left out
     */
    public static List<JsonProblemMarkers.Marker> compute(String text, List<ValidationProblem> problems) {
        List<JsonProblemMarkers.Marker> markers = new ArrayList<>();
        if (text == null || text.isEmpty() || problems == null || problems.isEmpty()) {
            return markers;
        }
        int[] lineStarts = JsonProblemMarkers.lineStarts(text);
        YamlDocument document = null;
        List<YamlTokenizer.Token> tokens = null;
        for (ValidationProblem problem : problems) {
            int[] range = null;
            String pointer = problem.context();
            if (pointer != null && (pointer.isEmpty() || pointer.startsWith("/"))
                    && "JSON Schema".equals(problem.source())) {
                if (document == null) {
                    document = YamlDocument.parse(text);
                }
                range = schemaRange(text, document, JsonPointerLocator.segments(pointer));
            }
            if (range == null && problem.line() > 0 && problem.line() <= lineStarts.length) {
                if (tokens == null) {
                    tokens = YamlTokenizer.tokenize(text);
                }
                range = rangeAtLine(text, lineStarts, tokens, problem.line(), problem.column());
            }
            if (range != null && range[1] > range[0]) {
                markers.add(new JsonProblemMarkers.Marker(range[0], range[1], lineOf(lineStarts, range[0]),
                        problem.message()));
            }
        }
        return markers;
    }

    private static int[] schemaRange(String text, YamlDocument document, List<Object> segments) {
        for (int n = segments.size(); n >= 0; n--) {
            YamlDocument.Location location = document.locationOf(segments.subList(0, n));
            if (location == null) {
                continue;
            }
            if (location.keyStart() >= 0 && location.keyEnd() > location.keyStart()) {
                return new int[] {location.keyStart(), location.keyEnd()};
            }
            int start = location.valueStart();
            int end = Math.min(location.valueEnd(), lineEnd(text, start));
            return end > start ? new int[] {start, end} : null;
        }
        return null;
    }

    /** The token at a 1-based line and column (column 0 = the first non-blank character of the line). */
    private static int[] rangeAtLine(String text, int[] lineStarts, List<YamlTokenizer.Token> tokens, int line,
                                     int column) {
        int lineStart = lineStarts[line - 1];
        int end = lineEnd(text, lineStart);
        if (end <= lineStart) {
            // an empty line: mark the line break before it (or the first character)
            return new int[] {Math.max(0, lineStart - 1), Math.min(text.length(), Math.max(lineStart, 1))};
        }
        int offset = lineStart;
        if (column > 0) {
            offset = Math.min(lineStart + column - 1, end - 1);
        } else {
            while (offset < end - 1 && Character.isWhitespace(text.charAt(offset))) {
                offset++;
            }
        }
        for (YamlTokenizer.Token token : tokens) {
            if (offset >= token.start() && offset < token.end()) {
                if (token.kind() == YamlTokenizer.Kind.TEXT) {
                    break;
                }
                return new int[] {Math.max(token.start(), lineStart), Math.min(token.end(), end)};
            }
        }
        return new int[] {offset, offset + 1};
    }

    private static int lineEnd(String text, int from) {
        int end = text.indexOf('\n', from);
        end = end < 0 ? text.length() : end;
        if (end > from && text.charAt(end - 1) == '\r') {
            end--;
        }
        return end;
    }

    private static int lineOf(int[] lineStarts, int offset) {
        int idx = java.util.Arrays.binarySearch(lineStarts, offset);
        return idx >= 0 ? idx : -idx - 2;
    }
}
