package org.fxt.freexmltoolkit.debugger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The text format of the Breakpoints panel's Export / Import: a header line, the line
 * breakpoints (one per line, the format the preferences use, with absolute file paths) and,
 * after a {@code # symbols} marker, the template / function / mode breakpoints.
 */
public final class BreakpointExport {

    static final String HEADER = "# FreeXmlToolkit XSLT breakpoints v1";
    static final String SYMBOLS = "# symbols";

    /** What an import file contains. */
    public record Content(Set<Breakpoint> breakpoints, List<SymbolBreakpoint> symbols) { }

    private BreakpointExport() {
    }

    public static String write(Collection<Breakpoint> breakpoints, Collection<SymbolBreakpoint> symbols) {
        StringBuilder sb = new StringBuilder(HEADER).append('\n');
        String lines = BreakpointStore.format(breakpoints);
        if (!lines.isEmpty()) {
            sb.append(lines).append('\n');
        }
        sb.append(SYMBOLS).append('\n');
        String sym = WatchStore.formatSymbols(symbols);
        if (!sym.isEmpty()) {
            sb.append(sym).append('\n');
        }
        return sb.toString();
    }

    /**
     * @throws IllegalArgumentException when the text is not a breakpoint export
     */
    public static Content read(String text) {
        if (text == null || !text.strip().startsWith(HEADER)) {
            throw new IllegalArgumentException("Not a FreeXmlToolkit breakpoint file");
        }
        StringBuilder lines = new StringBuilder();
        StringBuilder symbols = new StringBuilder();
        boolean inSymbols = false;
        for (String line : text.split("\\R")) {
            if (line.isBlank() || line.equals(HEADER)) {
                continue;
            }
            if (line.equals(SYMBOLS)) {
                inSymbols = true;
                continue;
            }
            (inSymbols ? symbols : lines).append(line).append('\n');
        }
        Set<Breakpoint> bps = new LinkedHashSet<>(BreakpointStore.parse(lines.toString().strip()));
        List<SymbolBreakpoint> syms = new ArrayList<>(WatchStore.parseSymbols(symbols.toString().strip()));
        return new Content(bps, syms);
    }
}
