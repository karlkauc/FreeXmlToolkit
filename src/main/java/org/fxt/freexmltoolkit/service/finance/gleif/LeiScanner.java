package org.fxt.freexmltoolkit.service.finance.gleif;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxt.freexmltoolkit.service.finance.FinancialIdentifiers;

/**
 * Finds LEIs in any text (XML, CSV, JSON …): 20 letters or digits ending in two digits, standing alone, with valid
 * check digits. The check digits make random matches very unlikely.
 */
public final class LeiScanner {

    private static final Pattern CANDIDATE = Pattern.compile("(?<![A-Za-z0-9])[A-Z0-9]{18}[0-9]{2}(?![A-Za-z0-9])");

    /** One LEI in the text: 1-based line and column, 0-based offset. */
    public record Occurrence(String lei, int line, int column, int offset) {
    }

    private LeiScanner() {
    }

    /** @return every LEI with valid check digits, in text order */
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
            String lei = m.group();
            if (FinancialIdentifiers.checkLei(lei) == null) {
                found.add(new Occurrence(lei, line, m.start() - lineStart + 1, m.start()));
            }
        }
        return found;
    }

    /** @return the distinct LEIs of the occurrences, in order */
    public static Set<String> distinct(List<Occurrence> occurrences) {
        Set<String> leis = new LinkedHashSet<>();
        occurrences.forEach(o -> leis.add(o.lei()));
        return leis;
    }

    /** @return the LEI at a 0-based offset of the text, or {@code null} */
    public static String at(CharSequence text, int offset) {
        if (offset < 0 || offset > text.length()) {
            return null;
        }
        int start = offset;
        while (start > 0 && Character.isLetterOrDigit(text.charAt(start - 1)) && offset - start < 20) {
            start--;
        }
        int end = offset;
        while (end < text.length() && Character.isLetterOrDigit(text.charAt(end)) && end - start < 21) {
            end++;
        }
        if (end - start != 20 || (start > 0 && Character.isLetterOrDigit(text.charAt(start - 1)))
                || (end < text.length() && Character.isLetterOrDigit(text.charAt(end)))) {
            return null;
        }
        String word = text.subSequence(start, end).toString();
        return CANDIDATE.matcher(word).matches() && FinancialIdentifiers.checkLei(word) == null ? word : null;
    }
}
