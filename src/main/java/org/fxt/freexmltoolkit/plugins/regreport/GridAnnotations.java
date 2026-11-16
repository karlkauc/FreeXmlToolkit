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

package org.fxt.freexmltoolkit.plugins.regreport;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Grid formulas and saved filters for a report: {@code <?fxt-formula …?>} / {@code <?fxt-filter …?>}
 * processing instructions right after the start tag of the elements they belong to (the grid shows each
 * formula with its live result, a filter comes up switched off). Works on the text, so the rest of the
 * file stays byte for byte as it was; no formula stores a result, so no data changes. Before a report is
 * submitted {@link #remove} takes them out again. Both are idempotent.
 */
public final class GridAnnotations {

    /**
     * A formula ({@code filter == false}) or a filter on every element with the local name {@code element}.
     *
     * @param expression an XQuery 3.1 expression with the element as context
     */
    public record Annotation(String element, boolean filter, String name, String expression) {

        /** @return a named formula */
        public static Annotation formula(String element, String name, String expression) {
            return new Annotation(element, false, name, expression);
        }

        /** @return a saved filter (the grid uses the first one of an element) */
        public static Annotation filter(String element, String expression) {
            return new Annotation(element, true, null, expression);
        }
    }

    private static final Pattern ANNOTATION = Pattern.compile("[ \\t]*(?:\\r?\\n[ \\t]*)?<\\?fxt-(?:formula|filter)\\b.*?\\?>",
            Pattern.DOTALL);
    private static final Pattern START_TAG = Pattern.compile("<(?:[A-Za-z_][\\w.-]*:)?([A-Za-z_][\\w.-]*)(?:\\s[^<>]*?)?(/?)>");
    private static final Pattern IGNORED = Pattern.compile("<!--.*?-->|<!\\[CDATA\\[.*?]]>|<\\?.*?\\?>", Pattern.DOTALL);

    private GridAnnotations() {
    }

    /** @return whether the text has grid formulas or filters (they must not be submitted) */
    public static boolean has(String xml) {
        return xml != null && (xml.contains("<?fxt-formula") || xml.contains("<?fxt-filter"));
    }

    /** @return the text without grid formulas and filters */
    public static String remove(String xml) {
        return ANNOTATION.matcher(xml).replaceAll("");
    }

    /** @return the text with the annotations (existing ones are replaced): filters first, then the formulas */
    public static String add(String xml, List<Annotation> annotations) {
        String clean = remove(xml);
        List<int[]> skipped = new ArrayList<>();
        Matcher ignored = IGNORED.matcher(clean);
        while (ignored.find()) {
            skipped.add(new int[] {ignored.start(), ignored.end()});
        }
        StringBuilder out = new StringBuilder(clean.length() + annotations.size() * 200);
        Matcher tag = START_TAG.matcher(clean);
        int last = 0;
        while (tag.find()) {
            if (!tag.group(2).isEmpty() || inside(skipped, tag.start())) {
                continue;
            }
            List<Annotation> mine = new ArrayList<>();
            for (Annotation a : annotations) {
                if (a.filter() && a.element().equals(tag.group(1))) {
                    mine.add(a);
                }
            }
            for (Annotation a : annotations) {
                if (!a.filter() && a.element().equals(tag.group(1))) {
                    mine.add(a);
                }
            }
            if (mine.isEmpty()) {
                continue;
            }
            String indent = indentAfter(clean, tag.end());
            out.append(clean, last, tag.end());
            for (Annotation a : mine) {
                out.append(indent).append(a.filter()
                        ? "<?fxt-filter expression=\"" + escape(a.expression()) + "\"?>"
                        : "<?fxt-formula name=\"" + escape(a.name()) + "\" store=\"false\" expression=\"" + escape(a.expression()) + "\"?>");
            }
            last = tag.end();
        }
        return out.append(clean, last, clean.length()).toString();
    }

    private static boolean inside(List<int[]> ranges, int offset) {
        for (int[] r : ranges) {
            if (offset >= r[0] && offset < r[1]) {
                return true;
            }
        }
        return false;
    }

    /** @return the line break and indentation that follow a start tag (the first child's), or a space */
    private static String indentAfter(String xml, int offset) {
        int i = offset;
        while (i < xml.length() && (xml.charAt(i) == ' ' || xml.charAt(i) == '\t' || xml.charAt(i) == '\r' || xml.charAt(i) == '\n')) {
            i++;
        }
        String ws = xml.substring(offset, i);
        int nl = ws.lastIndexOf('\n');
        return nl < 0 ? "" : (ws.contains("\r\n") ? "\r\n" : "\n") + ws.substring(nl + 1);
    }

    /** The escaping of the grid's formula instructions: {@code &}, {@code "} and {@code >} (the data must never contain "?>"). */
    static String escape(String text) {
        return text.replace("&", "&amp;").replace("\"", "&quot;").replace(">", "&gt;");
    }
}
