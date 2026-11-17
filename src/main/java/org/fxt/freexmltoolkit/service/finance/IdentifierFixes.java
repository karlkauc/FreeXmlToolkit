package org.fxt.freexmltoolkit.service.finance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxt.freexmltoolkit.service.finance.isin.IsinPairs;
import org.fxt.freexmltoolkit.service.finance.isin.IsinRecord;
import org.fxt.freexmltoolkit.service.xmledit.TextEdit;

/**
 * Collective fixes of reported values that ESMA FIRDS knows better: the CFI code and the issuer LEI of an ISIN, in
 * <b>every</b> place the document reports them ({@link IsinPairs}). A security that occurs a hundred times is
 * corrected with one action - one undo step in the editor.
 *
 * <p>The edits cover only the element's text; a value is replaced only where the document still holds the value the
 * check reported. UI-free.
 */
public final class IdentifierFixes {

    /** The reported value a fix replaces. */
    public enum Field { CFI, ISSUER_LEI }

    /**
     * What a plan changes for one ISIN and field.
     *
     * @param from        the reported value(s), as reported (several when they differ between occurrences)
     * @param to          the value ESMA FIRDS has
     * @param occurrences how many places change
     */
    public record Fix(String isin, Field field, String from, String to, String name, int occurrences) {
    }

    /** The edits (descending start, not overlapping) and what they change. */
    public record Plan(List<TextEdit> edits, List<Fix> fixes) {

        public boolean isEmpty() {
            return edits.isEmpty();
        }

        /** @return the number of distinct ISINs changed */
        public int isins() {
            return (int) fixes.stream().map(Fix::isin).distinct().count();
        }
    }

    /** How far from the reported position the value is looked for (start tags can be long). */
    private static final int WINDOW = 4_000;

    private IdentifierFixes() {
    }

    /**
     * Plans the fixes in a document.
     *
     * @param xml    the document
     * @param isins  which ISINs to fix ({@code isin -> true}); {@code null} for every one
     * @param fields which reported values to fix
     * @param firds  the FIRDS answer of an ISIN (from the lookup cache), or {@code null} when unknown
     */
    public static Plan plan(String xml, Predicate<String> isins, Set<Field> fields, Function<String, IsinRecord> firds) {
        int[] lineStarts = lineStarts(xml);
        Map<Integer, TextEdit> edits = new LinkedHashMap<>();
        Map<String, Fix> fixes = new LinkedHashMap<>();
        for (IsinPairs.Pair pair : IsinPairs.scan(xml)) {
            Field field = pair.kind() == IsinPairs.Kind.CFI ? Field.CFI : Field.ISSUER_LEI;
            if (!fields.contains(field) || (isins != null && !isins.test(pair.isin()))) {
                continue;
            }
            IsinRecord record = firds.apply(pair.isin());
            if (record == null || record.source() != IsinRecord.Source.FIRDS) {
                continue;
            }
            String correct = field == Field.CFI ? record.cfi() : record.issuerLei();
            if (correct == null || correct.isBlank() || differsNot(field, pair.value(), correct)) {
                continue;
            }
            int[] span = locate(xml, lineStarts, pair);
            if (span == null || edits.containsKey(span[0])) {
                continue;
            }
            edits.put(span[0], new TextEdit(span[0], span[1], correct));
            fixes.merge(pair.isin() + "|" + field,
                    new Fix(pair.isin(), field, pair.value(), correct, record.name(), 1),
                    (a, b) -> new Fix(a.isin(), a.field(), a.from().contains(b.from()) ? a.from() : a.from() + ", " + b.from(),
                            a.to(), a.name(), a.occurrences() + 1));
        }
        List<TextEdit> sorted = new ArrayList<>(edits.values());
        sorted.sort((a, b) -> Integer.compare(b.start(), a.start()));
        return new Plan(List.copyOf(sorted), List.copyOf(fixes.values()));
    }

    /** Plans the fixes of the given ISINs, every field. */
    public static Plan plan(String xml, Collection<String> isins, Function<String, IsinRecord> firds) {
        Set<String> wanted = Set.copyOf(isins);
        return plan(xml, wanted::contains, Set.of(Field.values()), firds);
    }

    private static boolean differsNot(Field field, String reported, String firds) {
        return field == Field.CFI ? IsinPairs.cfiMatches(reported, firds) : firds.equalsIgnoreCase(reported);
    }

    /**
     * @return {@code [start, end)} of the reported value's text near the element's position, or {@code null} when the
     *         document does not hold it there any more
     */
    static int[] locate(String xml, int[] lineStarts, IsinPairs.Pair pair) {
        if (pair.line() < 1 || pair.line() > lineStarts.length) {
            return null;
        }
        int at = Math.min(xml.length(), lineStarts[pair.line() - 1] + Math.max(0, pair.column() - 1));
        int from = Math.max(0, at - WINDOW);
        int to = Math.min(xml.length(), at + WINDOW);
        Matcher m = Pattern.compile(">\\s*(" + Pattern.quote(pair.value()) + ")\\s*<", Pattern.CASE_INSENSITIVE)
                .matcher(xml).region(from, to);
        int[] best = null;
        int bestDistance = Integer.MAX_VALUE;
        while (m.find()) {
            int distance = Math.abs(m.start(1) - at);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = new int[] {m.start(1), m.end(1)};
            }
        }
        return best;
    }

    /** @return the offset of every line's first character (index 0 = line 1) */
    static int[] lineStarts(String text) {
        int count = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        int[] starts = new int[count];
        int line = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                starts[line++] = i + 1;
            }
        }
        return starts;
    }
}
