package org.fxt.freexmltoolkit.debugger;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmValue;

/**
 * The values shown at the end of variable and parameter declarations while the debugger is
 * paused (IntelliJ-style inline values): for the stylesheet text of the paused module, the
 * {@code xsl:variable} / {@code xsl:param} declarations whose value the paused snapshot holds.
 *
 * <ul>
 *   <li>Locals: declarations inside the template / function that contains the paused line, at
 *       or before it; when a name is declared more than once there, only the last declaration
 *       before the paused line shows the value.</li>
 *   <li>Globals: top-level declarations whose evaluation has completed.</li>
 * </ul>
 *
 * <p>A declaration's line is the line where its start tag <em>ends</em> - the line Saxon
 * reports for it, and the one breakpoints use. Text that does not parse (unsaved, half-edited)
 * yields no values.</p>
 */
public final class InlineValues {

    /** Longest value text shown inline. */
    public static final int MAX_TEXT = 80;

    private static final String XSL = "http://www.w3.org/1999/XSL/Transform";

    private InlineValues() {
    }

    /**
     * @param stylesheetText the paused module's text, as the editor shows it
     * @param pausedLine     the 1-based paused line in that text
     * @param snapshot       the paused state (its local and global values)
     * @return 1-based line → text to show ({@code $name = value}), in line order
     */
    public static Map<Integer, String> compute(String stylesheetText, int pausedLine, PausedSnapshot snapshot) {
        Map<Integer, String> result = new TreeMap<>();
        if (stylesheetText == null || stylesheetText.isEmpty() || snapshot == null || pausedLine < 1) {
            return result;
        }
        List<Decl> decls;
        List<int[]> components;
        try {
            Parsed parsed = parse(stylesheetText);
            decls = parsed.decls();
            components = parsed.components();
        } catch (XMLStreamException | RuntimeException e) {
            return result;
        }
        int component = -1;
        for (int i = 0; i < components.size(); i++) {
            int[] span = components.get(i);
            if (pausedLine > span[0] && pausedLine <= span[1]) {
                component = i;
                break;
            }
        }
        Map<String, XdmValue> locals = byDisplayName(snapshot.localVariables());
        Map<String, XdmValue> globals = byDisplayName(snapshot.globalVariables());
        Map<String, Decl> lastLocal = new LinkedHashMap<>();
        for (Decl d : decls) {
            if (d.topLevel()) {
                XdmValue v = globals.get(d.name());
                if (v != null) {
                    result.put(d.line(), format(d.name(), v));
                }
            } else if (component >= 0 && d.component() == component && d.line() <= pausedLine) {
                lastLocal.put(d.name(), d);
            }
        }
        for (Decl d : lastLocal.values()) {
            XdmValue v = locals.get(d.name());
            if (v != null) {
                result.put(d.line(), format(d.name(), v));
            }
        }
        return result;
    }

    /** {@code $name = value} on one line, capped at {@value #MAX_TEXT} characters. */
    static String format(String name, XdmValue value) {
        String text;
        if (value.size() == 1 && value.itemAt(0) instanceof XdmAtomicValue atomic
                && atomic.getStringValue().isEmpty()) {
            text = "\"\"";
        } else {
            text = WatchEvaluator.render(value, MAX_TEXT);
        }
        text = text.replaceAll("\\s+", " ").strip();
        if (text.length() > MAX_TEXT) {
            text = text.substring(0, MAX_TEXT) + "…";
        }
        return "$" + name + " = " + text;
    }

    private static Map<String, XdmValue> byDisplayName(Map<QName, XdmValue> values) {
        Map<String, XdmValue> out = new LinkedHashMap<>();
        if (values != null) {
            values.forEach((q, v) -> out.put(q.getPrefix() == null || q.getPrefix().isEmpty()
                    ? q.getLocalName() : q.getPrefix() + ":" + q.getLocalName(), v));
        }
        return out;
    }

    private record Decl(String name, int line, boolean topLevel, int component) {
    }

    /** Declarations, and per top-level element its line span (previous sibling's end, own end]. */
    private record Parsed(List<Decl> decls, List<int[]> components) {
    }

    private static int[] lineStarts(String text) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                starts.add(i + 1);
            }
        }
        return starts.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * The line where the start tag reported at {@code line:column} ends. StAX implementations
     * differ: some report the position of {@code <}, others the position after {@code >}; from
     * a {@code <} the tag is scanned to its closing {@code >} (quoted attribute values may hold
     * {@code >} and line breaks).
     */
    static int tagEndLine(String text, int[] lineStarts, int line, int column) {
        if (line < 1 || line > lineStarts.length || column < 1) {
            return line;
        }
        int offset = lineStarts[line - 1] + column - 1;
        if (offset >= text.length() || text.charAt(offset) != '<') {
            return line; // already the position after the start tag
        }
        int endLine = line;
        char quote = 0;
        for (int i = offset + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                endLine++;
            } else if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '>') {
                return endLine;
            }
        }
        return line;
    }

    private static Parsed parse(String text) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        try {
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        } catch (IllegalArgumentException ignored) {
            // not supported by this StAX implementation
        }
        List<Decl> decls = new ArrayList<>();
        List<int[]> components = new ArrayList<>();
        int[] lineStarts = lineStarts(text);
        XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(text));
        try {
            int depth = 0;
            int previousEnd = 0;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    depth++;
                    if (XSL.equals(reader.getNamespaceURI())
                            && ("variable".equals(reader.getLocalName()) || "param".equals(reader.getLocalName()))) {
                        String name = reader.getAttributeValue(null, "name");
                        if (name != null && !name.isBlank()) {
                            int line = tagEndLine(text, lineStarts, reader.getLocation().getLineNumber(),
                                    reader.getLocation().getColumnNumber());
                            boolean topLevel = depth == 2;
                            decls.add(new Decl(name.strip(), line, topLevel, topLevel ? -1 : components.size()));
                        }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if (depth == 2) {
                        int end = reader.getLocation().getLineNumber();
                        components.add(new int[]{previousEnd, end});
                        previousEnd = end;
                    }
                    depth--;
                }
            }
        } finally {
            reader.close();
        }
        return new Parsed(decls, components);
    }
}
