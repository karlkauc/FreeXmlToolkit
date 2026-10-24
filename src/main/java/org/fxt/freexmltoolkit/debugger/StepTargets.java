package org.fxt.freexmltoolkit.debugger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the call targets on a paused line for "Smart Step Into": user functions called there,
 * {@code xsl:call-template}, and {@code xsl:apply-templates} (with or without a mode). A start
 * tag over several lines counts as one line - the one Saxon reports, where the tag ends.
 */
public final class StepTargets {

    private static final Pattern CALL_TEMPLATE =
            Pattern.compile("<[\\w.-]+:call-template\\b([^>]*)>");
    private static final Pattern APPLY_TEMPLATES =
            Pattern.compile("<[\\w.-]+:apply-templates\\b([^>]*)>");
    private static final Pattern ATTRIBUTE =
            Pattern.compile("([\\w.:-]+)\\s*=\\s*(\"[^\"]*\"|'[^']*')");

    private StepTargets() {
    }

    /**
     * @param stylesheetText the paused module's text
     * @param line           the 1-based paused line (as Saxon reports it)
     * @param functions      names of the user functions declared in the stylesheet and its modules
     * @return the targets in the order they appear on the line, without duplicates
     */
    public static List<StepTarget> find(String stylesheetText, int line, List<String> functions) {
        String region = region(stylesheetText, line);
        Map<String, StepTarget> found = new LinkedHashMap<>();
        if (region.isEmpty()) {
            return List.of();
        }
        List<int[]> spans = new ArrayList<>(); // [offset, order key] to sort by position
        List<StepTarget> targets = new ArrayList<>();
        Matcher call = CALL_TEMPLATE.matcher(region);
        while (call.find()) {
            String name = attribute(call.group(1), "name");
            if (name != null) {
                add(targets, spans, call.start(), new StepTarget("template name=\"" + name + "\"",
                        new SymbolBreakpoint(SymbolBreakpoint.Kind.TEMPLATE_NAME, name, true)));
            }
        }
        Matcher apply = APPLY_TEMPLATES.matcher(region);
        while (apply.find()) {
            String mode = attribute(apply.group(1), "mode");
            if (mode != null && !mode.isBlank() && !mode.strip().startsWith("#")) {
                add(targets, spans, apply.start(), new StepTarget("templates in mode " + mode.strip(),
                        new SymbolBreakpoint(SymbolBreakpoint.Kind.MODE, mode.strip(), true)));
            } else {
                add(targets, spans, apply.start(), new StepTarget("next template rule (apply-templates)", null));
            }
        }
        if (functions != null) {
            for (String fn : functions) {
                Matcher m = Pattern.compile("(?<![\\w.:-])" + Pattern.quote(fn) + "\\s*\\(").matcher(region);
                if (m.find()) {
                    add(targets, spans, m.start(), new StepTarget("function " + fn + "()",
                            new SymbolBreakpoint(SymbolBreakpoint.Kind.FUNCTION, fn, true)));
                }
            }
        }
        Integer[] order = new Integer[targets.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, java.util.Comparator.comparingInt(i -> spans.get(i)[0]));
        for (Integer i : order) {
            found.putIfAbsent(targets.get(i).label(), targets.get(i));
        }
        return List.copyOf(found.values());
    }

    private static void add(List<StepTarget> targets, List<int[]> spans, int offset, StepTarget target) {
        targets.add(target);
        spans.add(new int[]{offset});
    }

    /** The text of {@code line} plus the earlier lines of a start tag that ends on it. */
    static String region(String text, int line) {
        if (text == null || line < 1) {
            return "";
        }
        String[] lines = text.split("\n", -1);
        if (line > lines.length) {
            return "";
        }
        int[] map = StartTagLines.map(text);
        int first = line;
        while (first > 1 && StartTagLines.resolve(map, first - 1) == line) {
            first--;
        }
        StringBuilder sb = new StringBuilder();
        for (int l = first; l <= line; l++) {
            sb.append(lines[l - 1]).append('\n');
        }
        return sb.toString();
    }

    private static String attribute(String attrs, String name) {
        Matcher m = ATTRIBUTE.matcher(attrs);
        while (m.find()) {
            if (name.equals(m.group(1))) {
                String v = m.group(2);
                return v.substring(1, v.length() - 1);
            }
        }
        return null;
    }
}
