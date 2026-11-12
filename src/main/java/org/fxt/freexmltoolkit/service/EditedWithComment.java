package org.fxt.freexmltoolkit.service;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The "edited with FreeXmlToolkit" comment written into XML-family documents when they are saved, the way other XML
 * editors mark the files they wrote:
 *
 * <pre>{@code <?xml version="1.0" encoding="UTF-8"?>
 * <!-- Edited with FreeXmlToolkit 3.1.0 (https://github.com/karlkauc/FreeXmlToolkit) by Ada Lovelace (Analytical Engines) -->
 * <root>…}</pre>
 *
 * It goes right after the XML declaration (at the start without one), before any DOCTYPE and the root element. A
 * comment of an earlier save is replaced, never repeated; other comments stay untouched. Setting
 * {@value #KEY} ({@code true} by default) turns it off. The user name and company come from Settings → User; without
 * them the comment names no one.
 */
public final class EditedWithComment {

    /** Settings key: write the comment on save (default {@code true}). */
    public static final String KEY = "editor.editedWithComment";

    /** Start of the comment's text; how an earlier comment of ours is recognised. */
    static final String MARKER = "Edited with FreeXmlToolkit";

    private static final String URL = "https://github.com/karlkauc/FreeXmlToolkit";
    private static final Pattern DECLARATION = Pattern.compile("\\A(\\uFEFF?)(<\\?xml\\s[^?]*\\?>)");
    private static final Pattern OURS = Pattern.compile("<!--\\s*" + Pattern.quote(MARKER) + ".*?-->",
            Pattern.DOTALL);

    private EditedWithComment() {
    }

    /** Whether the setting is on ({@code true} when not set). */
    public static boolean enabled(Function<String, String> settings) {
        String value = settings == null ? null : settings.apply(KEY);
        return value == null || value.isBlank() || Boolean.parseBoolean(value.strip());
    }

    /** The comment for this installation and user ({@code name} / {@code company} may be blank). */
    public static String comment(String version, String name, String company) {
        StringBuilder sb = new StringBuilder("<!-- ").append(MARKER);
        if (version != null && !version.isBlank()) {
            sb.append(' ').append(version.strip());
        }
        sb.append(" (").append(URL).append(')');
        String who = clean(name);
        if (!who.isEmpty()) {
            sb.append(" by ").append(who);
            String org = clean(company);
            if (!org.isEmpty()) {
                sb.append(" (").append(org).append(')');
            }
        }
        return sb.append(" -->").toString();
    }

    /**
     * {@code text} with the comment in place: an earlier comment of ours in the prolog replaced, else inserted after
     * the XML declaration (or at the start). Text that does not start like an XML document is returned unchanged.
     */
    public static String apply(String text, String comment) {
        if (text == null || comment == null) {
            return text;
        }
        int prologEnd = prologEnd(text);
        if (prologEnd < 0) {
            return text;
        }
        Matcher ours = OURS.matcher(text).region(0, prologEnd);
        if (ours.find()) {
            return text.substring(0, ours.start()) + comment + text.substring(ours.end());
        }
        String newline = text.contains("\r\n") ? "\r\n" : "\n";
        Matcher declaration = DECLARATION.matcher(text);
        if (declaration.find()) {
            int at = declaration.end();
            return text.substring(0, at) + newline + comment + (startsWithNewline(text, at) ? "" : newline)
                    + text.substring(at);
        }
        int at = text.startsWith("﻿") ? 1 : 0;
        return text.substring(0, at) + comment + newline + text.substring(at);
    }

    /** The comment of this installation, applied when the setting is on. */
    public static String applyIfEnabled(String text, Function<String, String> settings) {
        if (!enabled(settings)) {
            return text;
        }
        return apply(text, comment(org.fxt.freexmltoolkit.util.VersionUtil.getVersion(),
                settings.apply("user.name"), settings.apply("user.company")));
    }

    /**
     * Where the prolog ends (the root element's {@code <}), or -1 when the text does not look like XML (it must
     * start, after a BOM and whitespace, with {@code <}) or has no root element.
     */
    static int prologEnd(String text) {
        int i = text.startsWith("﻿") ? 1 : 0;
        int n = text.length();
        while (i < n && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        if (i >= n || text.charAt(i) != '<') {
            return -1;
        }
        while (i < n) {
            if (Character.isWhitespace(text.charAt(i))) {
                i++;
            } else if (text.startsWith("<?", i)) {
                int end = text.indexOf("?>", i + 2);
                if (end < 0) {
                    return -1;
                }
                i = end + 2;
            } else if (text.startsWith("<!--", i)) {
                int end = text.indexOf("-->", i + 4);
                if (end < 0) {
                    return -1;
                }
                i = end + 3;
            } else if (text.startsWith("<!DOCTYPE", i)) {
                int depth = 0;
                int j = i;
                for (; j < n; j++) {
                    char c = text.charAt(j);
                    if (c == '[') {
                        depth++;
                    } else if (c == ']') {
                        depth--;
                    } else if (c == '>' && depth <= 0) {
                        break;
                    }
                }
                i = j + 1;
            } else if (text.charAt(i) == '<') {
                return i;
            } else {
                return -1;
            }
        }
        return -1;
    }

    private static boolean startsWithNewline(String text, int at) {
        return at < text.length() && (text.charAt(at) == '\n' || text.charAt(at) == '\r');
    }

    /** No "--" (not allowed in a comment) and no line breaks. */
    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\r\\n]+", " ").replace("--", "- -").strip();
    }
}
