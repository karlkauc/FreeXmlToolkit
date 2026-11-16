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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The HTML check report of a regulatory report: header (document, kind, time), counts, the findings
 * grouped by rule with the field the rule belongs to (from the field catalogues), and optional
 * sections a format adds (a fund table …). Self-contained page, light and dark colours.
 */
public final class RegReportHtml {

    /** An extra section; {@code html} is inserted as is (build it with {@link #esc}). */
    public record Section(String title, String html) {
    }

    private RegReportHtml() {
    }

    /**
     * @param title        the report's heading ({@code "AIFMD AIF file"})
     * @param documentName the checked file
     * @param summary      what the document is (one line, plain text), or {@code null}
     * @param findings     the findings of the rules
     * @param catalogs     the field catalogues used to name the field of a rule id ({@code FMA-AIF-48} → field 48)
     * @param sections     extra sections after the findings
     */
    public static String render(String title, String documentName, String summary, List<RegRuleSet.Finding> findings,
                                List<FieldCatalog> catalogs, List<Section> sections) {
        long errors = findings.stream().filter(f -> RegRuleSet.ERROR.equals(f.severity())).count();
        long warnings = findings.size() - errors;
        StringBuilder html = new StringBuilder(8192);
        html.append("<!DOCTYPE html>\n<html lang=\"en\"><head><meta charset=\"UTF-8\"><title>").append(esc(title))
                .append(" – check report</title>\n<style>").append(CSS).append("</style></head><body>\n");
        html.append("<h1>").append(esc(title)).append("</h1>\n<p class=\"meta\">").append(esc(documentName));
        if (summary != null && !summary.isBlank()) {
            html.append(" · ").append(esc(summary.replace('\n', ' ')));
        }
        html.append(" · checked ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                .append("</p>\n<div class=\"tiles\">");
        tile(html, errors == 0 && warnings == 0 ? "ok" : errors > 0 ? "error" : "warning",
                errors == 0 && warnings == 0 ? "✓" : String.valueOf(findings.size()),
                errors == 0 && warnings == 0 ? "no findings" : findings.size() == 1 ? "finding" : "findings");
        tile(html, errors > 0 ? "error" : "muted", String.valueOf(errors), errors == 1 ? "error" : "errors");
        tile(html, warnings > 0 ? "warning" : "muted", String.valueOf(warnings), warnings == 1 ? "warning" : "warnings");
        html.append("</div>\n");

        if (!findings.isEmpty()) {
            Map<String, List<RegRuleSet.Finding>> byRule = new LinkedHashMap<>();
            findings.stream().sorted(java.util.Comparator.comparing(RegRuleSet.Finding::ruleId, RegReportHtml::compareIds)
                            .thenComparingInt(RegRuleSet.Finding::line))
                    .forEach(f -> byRule.computeIfAbsent(f.ruleId(), k -> new java.util.ArrayList<>()).add(f));
            html.append("<h2>Findings by rule</h2>\n<table><thead><tr><th>Rule</th><th>Field</th><th>Severity</th>"
                    + "<th class=\"num\">Line</th><th>Message</th></tr></thead><tbody>\n");
            for (Map.Entry<String, List<RegRuleSet.Finding>> rule : byRule.entrySet()) {
                String field = fieldOf(rule.getKey(), catalogs);
                boolean first = true;
                for (RegRuleSet.Finding f : rule.getValue()) {
                    html.append("<tr>");
                    if (first) {
                        int rows = rule.getValue().size();
                        html.append("<td rowspan=\"").append(rows).append("\" class=\"rule\">").append(esc(rule.getKey()))
                                .append("</td><td rowspan=\"").append(rows).append("\">").append(esc(field)).append("</td>");
                        first = false;
                    }
                    String sev = RegRuleSet.ERROR.equals(f.severity()) ? "error" : "warning";
                    html.append("<td><span class=\"badge ").append(sev).append("\">").append(sev).append("</span></td><td class=\"num\">")
                            .append(f.line() > 0 ? String.valueOf(f.line()) : "").append("</td><td>").append(esc(f.message()))
                            .append("</td></tr>\n");
                }
            }
            html.append("</tbody></table>\n");
        }
        for (Section section : sections) {
            html.append("<h2>").append(esc(section.title())).append("</h2>\n").append(section.html()).append('\n');
        }
        html.append("<p class=\"foot\">Created with FreeXmlToolkit. The schema is checked separately (F8); this report "
                + "lists the rules of the extension.</p>\n</body></html>\n");
        return html.toString();
    }

    private static void tile(StringBuilder html, String kind, String value, String label) {
        html.append("<div class=\"tile ").append(kind).append("\"><b>").append(esc(value)).append("</b><span>")
                .append(esc(label)).append("</span></div>");
    }

    /** @return {@code Field 48 · Total AuM amount} for a rule id ending in a known field id, else empty */
    static String fieldOf(String ruleId, List<FieldCatalog> catalogs) {
        int dash = ruleId.lastIndexOf('-');
        if (dash < 0 || catalogs == null) {
            return "";
        }
        String id = ruleId.substring(dash + 1);
        for (FieldCatalog catalog : catalogs) {
            FieldCatalog.Field field = catalog.byId(id);
            if (field != null) {
                return field.label();
            }
        }
        return "";
    }

    /** Orders rule ids by their text, numbers by value ({@code X-9} before {@code X-10}). */
    static int compareIds(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int si = i;
                int sj = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) {
                    i++;
                }
                while (j < b.length() && Character.isDigit(b.charAt(j))) {
                    j++;
                }
                int c = new java.math.BigInteger(a.substring(si, i)).compareTo(new java.math.BigInteger(b.substring(sj, j)));
                if (c != 0) {
                    return c;
                }
            } else {
                if (ca != cb) {
                    return Character.compare(ca, cb);
                }
                i++;
                j++;
            }
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }

    /** @return the text with {@code & < > "} escaped */
    public static String esc(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (char c : text.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static final String CSS = """
            :root{--bg:#fff;--fg:#1f2933;--muted:#6b7785;--line:#dde3ea;--head:#f3f6f9;--err:#c62828;--errbg:#fdecea;\
            --warn:#8a5a00;--warnbg:#fff4d6;--ok:#1b7a3e;--okbg:#e6f5ec}
            @media (prefers-color-scheme: dark){:root{--bg:#1e1f22;--fg:#e3e6ea;--muted:#9aa4af;--line:#3a3d42;--head:#2a2c30;\
            --err:#ff8a80;--errbg:#3b2224;--warn:#ffcc66;--warnbg:#3a3220;--ok:#7bd88f;--okbg:#1f3326}}
            body{font-family:Inter,"Segoe UI",system-ui,sans-serif;font-size:14px;background:var(--bg);color:var(--fg);margin:24px}
            h1{font-size:22px;margin:0 0 4px}h2{font-size:16px;margin:28px 0 8px}
            .meta,.foot{color:var(--muted)}.foot{margin-top:28px;font-size:12px}
            .tiles{display:flex;gap:12px;margin:16px 0}
            .tile{border:1px solid var(--line);border-radius:8px;padding:10px 16px;min-width:90px}
            .tile b{display:block;font-size:22px}.tile span{color:var(--muted);font-size:12px}
            .tile.error{background:var(--errbg);border-color:var(--err)}.tile.error b{color:var(--err)}
            .tile.warning{background:var(--warnbg);border-color:var(--warn)}.tile.warning b{color:var(--warn)}
            .tile.ok{background:var(--okbg);border-color:var(--ok)}.tile.ok b{color:var(--ok)}
            table{border-collapse:collapse;width:100%}
            th,td{border-bottom:1px solid var(--line);padding:6px 8px;text-align:left;vertical-align:top}
            th{background:var(--head);font-weight:600}td.num,th.num{text-align:right;font-variant-numeric:tabular-nums}
            td.rule{font-family:"JetBrains Mono",monospace;white-space:nowrap}
            .badge{border-radius:10px;padding:1px 8px;font-size:12px}
            .badge.error{background:var(--errbg);color:var(--err)}.badge.warning{background:var(--warnbg);color:var(--warn)}
            .badge.ok{background:var(--okbg);color:var(--ok)}
            """;
}
