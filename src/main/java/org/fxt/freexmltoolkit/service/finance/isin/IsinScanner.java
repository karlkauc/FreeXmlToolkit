package org.fxt.freexmltoolkit.service.finance.isin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxt.freexmltoolkit.service.finance.FinancialIdentifiers;

/**
 * Finds ISINs in any text (XML, CSV, JSON …): 12 characters standing alone, a country prefix (ISO 3166 or one of the
 * international prefixes XS, EU, XA, XB, XC, XD, XF, QS, QT), valid check digit. The prefix and the check digit keep
 * random tokens out.
 */
public final class IsinScanner {

    private static final Pattern CANDIDATE = Pattern.compile("(?<![A-Za-z0-9])[A-Z]{2}[A-Z0-9]{9}[0-9](?![A-Za-z0-9])");
    private static final Set<String> PREFIXES = prefixes();

    /** One ISIN in the text: 1-based line and column, 0-based offset. */
    public record Occurrence(String isin, int line, int column, int offset) {
    }

    private IsinScanner() {
    }

    /** @return whether a 12-character token is an ISIN (prefix and check digit) */
    public static boolean isIsin(String token) {
        return token != null && CANDIDATE.matcher(token).matches() && PREFIXES.contains(token.substring(0, 2))
                && FinancialIdentifiers.checkIsin(token) == null;
    }

    /** @return every ISIN, in text order */
    public static List<Occurrence> scan(CharSequence text) {
        List<Occurrence> found = new ArrayList<>();
        Matcher m = CANDIDATE.matcher(text);
        int line = 1;
        int lineStart = 0;
        int pos = 0;
        while (m.find()) {
            for (; pos < m.start(); pos++) {
                if (text.charAt(pos) == '\n') {
                    line++;
                    lineStart = pos + 1;
                }
            }
            if (isIsin(m.group())) {
                found.add(new Occurrence(m.group(), line, m.start() - lineStart + 1, m.start()));
            }
        }
        return found;
    }

    /** @return the distinct ISINs of the occurrences, in order */
    public static Set<String> distinct(List<Occurrence> occurrences) {
        Set<String> isins = new LinkedHashSet<>();
        occurrences.forEach(o -> isins.add(o.isin()));
        return isins;
    }

    /** @return the ISIN at a 0-based offset of the text, or {@code null} */
    public static String at(CharSequence text, int offset) {
        if (offset < 0 || offset > text.length()) {
            return null;
        }
        int start = offset;
        while (start > 0 && Character.isLetterOrDigit(text.charAt(start - 1)) && offset - start < 12) {
            start--;
        }
        int end = offset;
        while (end < text.length() && Character.isLetterOrDigit(text.charAt(end)) && end - start < 13) {
            end++;
        }
        if (end - start != 12 || (start > 0 && Character.isLetterOrDigit(text.charAt(start - 1)))
                || (end < text.length() && Character.isLetterOrDigit(text.charAt(end)))) {
            return null;
        }
        String word = text.subSequence(start, end).toString();
        return isIsin(word) ? word : null;
    }

    private static Set<String> prefixes() {
        Set<String> p = new java.util.HashSet<>(Set.of(Locale.getISOCountries()));
        p.addAll(Set.of("XS", "EU", "XA", "XB", "XC", "XD", "XF", "QS", "QT"));
        return Set.copyOf(p);
    }
}
