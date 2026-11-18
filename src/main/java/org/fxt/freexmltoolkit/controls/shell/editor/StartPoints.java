package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxt.freexmltoolkit.service.XsltTransformationEngine.StartPoint;

/**
 * Where a stylesheet can start, from its text: the default (apply templates to the input), each mode its template
 * rules use, each named template ({@code xsl:initial-template} first), each public function with its parameters. A
 * scan of the stylesheet's own text (fast and tolerant of half-edited files); included and imported modules are not
 * read.
 */
final class StartPoints {

    private static final Pattern TEMPLATE = Pattern.compile("<\\s*xsl:template\\b([^>]*)>");
    private static final Pattern FUNCTION = Pattern.compile(
            "<\\s*xsl:function\\b([^>]*)>(.*?)</\\s*xsl:function\\s*>", Pattern.DOTALL);
    private static final Pattern PARAM = Pattern.compile("<\\s*xsl:param\\b([^>]*)>");
    private static final Pattern ATTR = Pattern.compile("([\\w:.-]+)\\s*=\\s*([\"'])(.*?)\\2", Pattern.DOTALL);

    /**
     * A start a user can choose.
     *
     * @param start      the run's start point
     * @param label      what the choice shows, e.g. "Template: f:main" or "Function: f:twice#1"
     * @param parameters a function's parameters as {@code $name as type}, else empty
     */
    record Choice(StartPoint start, String label, List<String> parameters) {
        @Override
        public String toString() {
            return label;
        }
    }

    private StartPoints() {
    }

    /** The default choice: templates matching the input, default mode. */
    static Choice applyTemplates() {
        return new Choice(StartPoint.DEFAULT, org.fxt.freexmltoolkit.i18n.I18n.t("TransformPanel.start.apply"),
                List.of());
    }

    /** @return the choices of {@code xslt}: the default first, then the initial template, modes, templates, functions */
    static List<Choice> of(String xslt) {
        List<Choice> out = new ArrayList<>();
        out.add(applyTemplates());
        if (xslt == null) {
            return out;
        }
        TreeSet<String> modes = new TreeSet<>();
        TreeSet<String> names = new TreeSet<>();
        boolean initial = false;
        Matcher t = TEMPLATE.matcher(xslt);
        while (t.find()) {
            Map<String, String> a = attributes(t.group(1));
            String name = a.get("name");
            if ("xsl:initial-template".equals(name)) {
                initial = true;
            } else if (name != null && !name.isBlank()) {
                names.add(name.strip());
            }
            String mode = a.get("mode");
            if (a.containsKey("match") && mode != null) {
                for (String m : mode.strip().split("\\s+")) {
                    if (!m.isEmpty() && !m.startsWith("#")) {
                        modes.add(m);
                    }
                }
            }
        }
        if (initial) {
            out.add(new Choice(new StartPoint(StartPoint.Kind.TEMPLATE, null, List.of()),
                    org.fxt.freexmltoolkit.i18n.I18n.t("TransformPanel.start.initial"), List.of()));
        }
        for (String m : modes) {
            out.add(new Choice(new StartPoint(StartPoint.Kind.APPLY, m, List.of()), "Mode: " + m, List.of()));
        }
        for (String n : names) {
            out.add(new Choice(new StartPoint(StartPoint.Kind.TEMPLATE, n, List.of()), "Template: " + n, List.of()));
        }
        Matcher f = FUNCTION.matcher(xslt);
        while (f.find()) {
            Map<String, String> a = attributes(f.group(1));
            String name = a.get("name");
            String visibility = a.getOrDefault("visibility", "private");
            if (name == null || !("public".equals(visibility) || "final".equals(visibility))) {
                continue; // only public functions can be called from outside
            }
            List<String> params = new ArrayList<>();
            Matcher p = PARAM.matcher(f.group(2));
            while (p.find()) {
                Map<String, String> pa = attributes(p.group(1));
                if (pa.get("name") != null) {
                    params.add("$" + pa.get("name") + (pa.get("as") != null ? " as " + pa.get("as") : ""));
                }
            }
            out.add(new Choice(new StartPoint(StartPoint.Kind.FUNCTION, name.strip(), List.of()),
                    "Function: " + name.strip() + "#" + params.size(), params));
        }
        return out;
    }

    private static Map<String, String> attributes(String text) {
        Map<String, String> out = new LinkedHashMap<>();
        Matcher m = ATTR.matcher(text);
        while (m.find()) {
            out.put(m.group(1), m.group(3));
        }
        return out;
    }
}
