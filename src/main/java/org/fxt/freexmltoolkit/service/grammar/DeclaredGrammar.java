package org.fxt.freexmltoolkit.service.grammar;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The DTD or RELAX NG grammar an XML document declares itself: a {@code <!DOCTYPE … SYSTEM/PUBLIC …>}
 * or an {@code <?xml-model href="…" schematypens="http://relaxng.org/ns/structure/1.0"?>} processing
 * instruction (also {@code type="application/relax-ng-compact-syntax"}).
 *
 * @param kind          DTD, RELAX_NG or RELAX_NG_COMPACT
 * @param systemId      the system id / href (relative to the document), or {@code null} for a DOCTYPE
 *                      with only an internal subset
 * @param publicId      the public id of a DOCTYPE, or {@code null}
 * @param rootName      the root element name of a DOCTYPE, or {@code null}
 * @param internalSubset whether the DOCTYPE has an internal subset ({@code [ … ]})
 */
public record DeclaredGrammar(GrammarKind kind, String systemId, String publicId, String rootName,
                              boolean internalSubset) {

    /** The RELAX NG namespace, as used by xml-model's schematypens. */
    public static final String RELAX_NG_NS = "http://relaxng.org/ns/structure/1.0";

    private static final Pattern DOCTYPE = Pattern.compile(
            "<!DOCTYPE\\s+([^\\s\\[>]+)(?:\\s+(?:SYSTEM\\s+(\"[^\"]*\"|'[^']*')|PUBLIC\\s+(\"[^\"]*\"|'[^']*')\\s*(\"[^\"]*\"|'[^']*')?))?\\s*(\\[)?",
            Pattern.DOTALL);
    private static final Pattern XML_MODEL = Pattern.compile("<\\?xml-model\\s([^?]*)\\?>", Pattern.DOTALL);
    private static final Pattern PSEUDO_ATTRIBUTE = Pattern.compile("([a-zA-Z-]+)\\s*=\\s*(\"[^\"]*\"|'[^']*')");

    /** @return the grammar the document declares in its prolog (before the root element), if any */
    public static Optional<DeclaredGrammar> of(String xml) {
        if (xml == null) {
            return Optional.empty();
        }
        String prolog = prolog(xml);
        Matcher model = XML_MODEL.matcher(prolog);
        while (model.find()) {
            String href = null;
            String type = null;
            String ns = null;
            Matcher attribute = PSEUDO_ATTRIBUTE.matcher(model.group(1));
            while (attribute.find()) {
                String value = unquote(attribute.group(2));
                switch (attribute.group(1)) {
                    case "href" -> href = value;
                    case "type" -> type = value;
                    case "schematypens" -> ns = value;
                    default -> { }
                }
            }
            if (href == null) {
                continue;
            }
            String lower = href.toLowerCase(Locale.ROOT);
            if ("application/relax-ng-compact-syntax".equals(type) || lower.endsWith(".rnc")) {
                return Optional.of(new DeclaredGrammar(GrammarKind.RELAX_NG_COMPACT, href, null, null, false));
            }
            if (RELAX_NG_NS.equals(ns) || lower.endsWith(".rng")) {
                return Optional.of(new DeclaredGrammar(GrammarKind.RELAX_NG, href, null, null, false));
            }
            if ("application/xml-dtd".equals(type) || lower.endsWith(".dtd")) {
                return Optional.of(new DeclaredGrammar(GrammarKind.DTD, href, null, null, false));
            }
        }
        Matcher doctype = DOCTYPE.matcher(prolog);
        if (doctype.find()) {
            String system = doctype.group(2) != null ? unquote(doctype.group(2))
                    : doctype.group(4) != null ? unquote(doctype.group(4)) : null;
            String publicId = doctype.group(3) != null ? unquote(doctype.group(3)) : null;
            boolean subset = doctype.group(5) != null;
            if (system != null || publicId != null || subset) {
                return Optional.of(new DeclaredGrammar(GrammarKind.DTD, system, publicId, doctype.group(1), subset));
            }
        }
        return Optional.empty();
    }

    /** @return the text before the root element's start tag (comments and PIs included) */
    static String prolog(String xml) {
        int i = 0;
        int n = Math.min(xml.length(), 200_000);
        while (i < n) {
            int lt = xml.indexOf('<', i);
            if (lt < 0 || lt + 1 >= n) {
                return xml.substring(0, n);
            }
            char next = xml.charAt(lt + 1);
            if (next == '?') {
                int end = xml.indexOf("?>", lt);
                i = end < 0 ? n : end + 2;
            } else if (xml.startsWith("<!--", lt)) {
                int end = xml.indexOf("-->", lt);
                i = end < 0 ? n : end + 3;
            } else if (xml.startsWith("<!DOCTYPE", lt)) {
                int end = doctypeEnd(xml, lt);
                i = end < 0 ? n : end;
            } else {
                return xml.substring(0, lt);
            }
        }
        return xml.substring(0, n);
    }

    /** @return the index after the DOCTYPE declaration (internal subset included) */
    private static int doctypeEnd(String xml, int start) {
        int depth = 0;
        char quote = 0;
        for (int i = start; i < xml.length(); i++) {
            char c = xml.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
            } else if (c == '>' && depth <= 0) {
                return i + 1;
            }
        }
        return -1;
    }

    private static String unquote(String quoted) {
        return quoted.substring(1, quoted.length() - 1);
    }
}
