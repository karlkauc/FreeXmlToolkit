package org.fxt.freexmltoolkit.debugger;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The line ranges of the templates and functions of a stylesheet text, for coverage: a
 * component none of whose lines ran is dead code for that input.
 */
public final class ComponentRanges {

    /**
     * @param label     e.g. {@code template match="item"} or {@code function f:total()}
     * @param startLine 1-based line where the component's start tag begins
     * @param endLine   1-based line of its end tag (or of the start tag when empty)
     */
    public record Range(String label, int startLine, int endLine) {

        public boolean contains(int line) {
            return line >= startLine && line <= endLine;
        }
    }

    private static final Pattern START = Pattern.compile("<([\\w.-]+):(template|function)\\b");
    private static final Pattern ATTRIBUTE = Pattern.compile("([\\w.:-]+)\\s*=\\s*(\"[^\"]*\"|'[^']*')");

    private ComponentRanges() {
    }

    public static List<Range> scan(String text) {
        List<Range> ranges = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return ranges;
        }
        Matcher m = START.matcher(text);
        int from = 0;
        while (m.find(from)) {
            int start = m.start();
            int gt = tagEnd(text, start);
            if (gt < 0) {
                break;
            }
            String attrs = text.substring(m.end(), gt);
            String kind = m.group(2);
            boolean empty = text.charAt(gt - 1) == '/';
            int end;
            if (empty) {
                end = gt;
            } else {
                String close = "</" + m.group(1) + ":" + kind;
                int c = text.indexOf(close, gt);
                end = c < 0 ? text.length() - 1 : c;
            }
            ranges.add(new Range(label(kind, attrs), lineOf(text, start), lineOf(text, end)));
            from = Math.max(gt + 1, end);
        }
        return ranges;
    }

    private static String label(String kind, String attrs) {
        String match = attribute(attrs, "match");
        String name = attribute(attrs, "name");
        if ("function".equals(kind)) {
            return "function " + (name == null ? "?" : name) + "()";
        }
        return match != null ? "template match=\"" + match + "\"" : "template name=\"" + name + "\"";
    }

    private static String attribute(String attrs, String name) {
        Matcher a = ATTRIBUTE.matcher(attrs);
        while (a.find()) {
            if (name.equals(a.group(1))) {
                String v = a.group(2);
                return v.substring(1, v.length() - 1);
            }
        }
        return null;
    }

    private static int tagEnd(String text, int lt) {
        char quote = 0;
        for (int i = lt + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '>') {
                return i;
            }
        }
        return -1;
    }

    private static int lineOf(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
