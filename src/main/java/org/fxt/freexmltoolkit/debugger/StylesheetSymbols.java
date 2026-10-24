package org.fxt.freexmltoolkit.debugger;

import java.util.EnumMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The template match patterns, template names, function names and modes declared in stylesheet
 * text — suggestions for template / function / mode breakpoints. A regex scan (fast, tolerant
 * of half-edited files); entities in attribute values are decoded for the common ones.
 */
public final class StylesheetSymbols {

    private static final Pattern TEMPLATE = Pattern.compile("<\\s*(?:[\\w.-]+:)?template\\b([^>]*)>");
    private static final Pattern FUNCTION = Pattern.compile("<\\s*(?:[\\w.-]+:)?function\\b([^>]*)>");
    private static final Pattern XQUERY_FUNCTION = Pattern.compile(
            "declare\\s+(?:%[\\w:.-]+\\s+)*function\\s+([\\w.-]+(?::[\\w.-]+)?)\\s*\\(");
    private static final Pattern MODE_DECL = Pattern.compile("<\\s*(?:[\\w.-]+:)?mode\\b([^>]*)>");
    private static final Pattern ATTR = Pattern.compile("\\b(match|name|mode)\\s*=\\s*([\"'])(.*?)\\2", Pattern.DOTALL);

    private StylesheetSymbols() {
    }

    /** @return the declared names per kind, sorted, without duplicates. */
    public static Map<SymbolBreakpoint.Kind, java.util.List<String>> scan(String text) {
        Map<SymbolBreakpoint.Kind, TreeSet<String>> found = new EnumMap<>(SymbolBreakpoint.Kind.class);
        for (SymbolBreakpoint.Kind k : SymbolBreakpoint.Kind.values()) {
            found.put(k, new TreeSet<>());
        }
        if (text != null) {
            Matcher t = TEMPLATE.matcher(text);
            while (t.find()) {
                Map<String, String> attrs = attributes(t.group(1));
                add(found, SymbolBreakpoint.Kind.TEMPLATE_MATCH, attrs.get("match"));
                add(found, SymbolBreakpoint.Kind.TEMPLATE_NAME, attrs.get("name"));
                String modes = attrs.get("mode");
                if (modes != null) {
                    for (String m : modes.trim().split("\\s+")) {
                        if (!m.startsWith("#")) {
                            add(found, SymbolBreakpoint.Kind.MODE, m);
                        }
                    }
                }
            }
            Matcher f = FUNCTION.matcher(text);
            while (f.find()) {
                add(found, SymbolBreakpoint.Kind.FUNCTION, attributes(f.group(1)).get("name"));
            }
            Matcher q = XQUERY_FUNCTION.matcher(text); // an XQuery: declare function local:f(…)
            while (q.find()) {
                add(found, SymbolBreakpoint.Kind.FUNCTION, q.group(1));
            }
            Matcher m = MODE_DECL.matcher(text);
            while (m.find()) {
                add(found, SymbolBreakpoint.Kind.MODE, attributes(m.group(1)).get("name"));
            }
        }
        Map<SymbolBreakpoint.Kind, java.util.List<String>> result = new EnumMap<>(SymbolBreakpoint.Kind.class);
        found.forEach((k, v) -> result.put(k, java.util.List.copyOf(v)));
        return result;
    }

    private static Map<String, String> attributes(String tagBody) {
        Map<String, String> attrs = new java.util.HashMap<>();
        Matcher a = ATTR.matcher(tagBody);
        while (a.find()) {
            attrs.putIfAbsent(a.group(1), a.group(3).replace("&quot;", "\"").replace("&apos;", "'")
                    .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&"));
        }
        return attrs;
    }

    private static void add(Map<SymbolBreakpoint.Kind, TreeSet<String>> found, SymbolBreakpoint.Kind kind, String value) {
        if (value != null && !value.isBlank()) {
            found.get(kind).add(value.strip());
        }
    }
}
