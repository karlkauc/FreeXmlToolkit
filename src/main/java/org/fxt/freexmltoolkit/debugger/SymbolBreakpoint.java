package org.fxt.freexmltoolkit.debugger;

import java.util.Locale;

/**
 * A breakpoint on a stylesheet component instead of a line: the run pauses whenever a template
 * rule with a given match pattern, a named template, a function, or any template in a given
 * mode is entered — wherever it is defined (also in included modules or generated code).
 * Names compare without whitespace; a function or template name may be given with or without
 * its prefix.
 *
 * @param kind    what is named
 * @param name    the match pattern, template / function name or mode name
 * @param enabled whether it pauses
 */
public record SymbolBreakpoint(Kind kind, String name, boolean enabled) {

    /** The component kinds a symbol breakpoint can name. */
    public enum Kind {
        TEMPLATE_MATCH("Template rule (match)"),
        TEMPLATE_NAME("Named template"),
        FUNCTION("Function"),
        MODE("Any template in mode"),
        /** A node of the input document (see {@link #inputNode}); not offered in the Template… dialog. */
        INPUT_NODE("Input node");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public SymbolBreakpoint {
        if (kind == null) {
            kind = Kind.TEMPLATE_MATCH;
        }
        name = name == null ? "" : name.strip();
    }

    /**
     * A breakpoint on a node of the input document: the run pauses when that node becomes the
     * context item (a template rule matched it, a loop reached it). The node is identified the way
     * Saxon locates it: its file and the line / column just after its start tag's {@code >}.
     *
     * @param path   the input file's normalized path ({@code ""}: any input)
     * @param line   1-based line of the start tag's end
     * @param column 1-based column just after that {@code >}
     * @param label  how the node is shown, e.g. {@code <item>}
     */
    public static SymbolBreakpoint inputNode(String path, int line, int column, String label) {
        return new SymbolBreakpoint(Kind.INPUT_NODE,
                (label == null ? "" : label.replace('|', ' ')) + "|" + line + "|" + column + "|"
                        + (path == null ? "" : path), true);
    }

    private String inputPart(int index) {
        String[] parts = name.split("\\|", 4);
        return index < parts.length ? parts[index] : "";
    }

    /** @return the input node's label (kind {@link Kind#INPUT_NODE}). */
    public String inputLabel() { return inputPart(0); }

    /** @return the input node's line, or 0. */
    public int inputLine() { return parseInt(inputPart(1)); }

    /** @return the input node's column, or 0. */
    public int inputColumn() { return parseInt(inputPart(2)); }

    /** @return the input file's normalized path, {@code ""} for any. */
    public String inputPath() { return inputPart(3); }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.strip());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** @return whether this (enabled) input-node breakpoint names the node at {@code path:line:column}. */
    public boolean matchesInputNode(String path, int line, int column) {
        if (!enabled || kind != Kind.INPUT_NODE || line != inputLine() || column != inputColumn()) {
            return false;
        }
        String wanted = inputPath();
        String here = path == null ? "" : path;
        return wanted.isEmpty() || here.isEmpty() || wanted.equals(here);
    }

    /** Returns a copy with the enabled flag set. */
    public SymbolBreakpoint withEnabled(boolean on) {
        return new SymbolBreakpoint(kind, name, on);
    }

    /** @return the pause note / list text, e.g. {@code template match="item"}. */
    public String describe() {
        return switch (kind) {
            case TEMPLATE_MATCH -> "template match=\"" + name + "\"";
            case TEMPLATE_NAME -> "template name=\"" + name + "\"";
            case FUNCTION -> "function " + name + "()";
            case MODE -> "mode " + name;
            case INPUT_NODE -> "input node " + inputLabel() + " ("
                    + (inputPath().isEmpty() ? "" : SourceLocations.fileName(inputPath()) + ":") + inputLine() + ")";
        };
    }

    /**
     * @param match    the entered template rule's match pattern, or {@code null}
     * @param template the entered named template's name, or {@code null}
     * @param function the entered function's name, or {@code null}
     * @param mode     the mode the template runs in, or {@code null}
     * @return whether this (enabled) breakpoint fires for that component
     */
    public boolean matches(String match, String template, String function, String mode) {
        if (!enabled || name.isEmpty()) {
            return false;
        }
        return switch (kind) {
            case TEMPLATE_MATCH -> match != null && compact(match).equals(compact(name));
            case TEMPLATE_NAME -> template != null && sameName(template, name);
            case FUNCTION -> function != null && sameName(function, name);
            case MODE -> mode != null && (match != null || template != null) && sameName(mode, name);
            case INPUT_NODE -> false; // checked by matchesInputNode on every focus change
        };
    }

    private static String compact(String s) {
        return s.replaceAll("\\s+", "");
    }

    /** {@code f:sum} matches {@code f:sum} and {@code sum}; {@code sum} matches any prefix. */
    private static boolean sameName(String actual, String wanted) {
        String a = compact(actual);
        String w = compact(wanted);
        if (a.equals(w)) {
            return true;
        }
        int colon = a.indexOf(':');
        return w.indexOf(':') < 0 && colon >= 0 && a.substring(colon + 1).equals(w);
    }

    /** Storage line: {@code KIND \t name \t enabled} (name escaped like breakpoint conditions). */
    String format() {
        return kind.name() + "\t" + BreakpointStore.escape(name) + "\t" + enabled;
    }

    static SymbolBreakpoint parse(String line) {
        String[] f = line.split("\t", -1);
        if (f.length < 2) {
            return null;
        }
        try {
            Kind kind = Kind.valueOf(f[0].trim().toUpperCase(Locale.ROOT));
            boolean enabled = f.length < 3 || Boolean.parseBoolean(f[2].trim());
            return new SymbolBreakpoint(kind, BreakpointStore.unescape(f[1]), enabled);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
