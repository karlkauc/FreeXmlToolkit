package org.fxt.freexmltoolkit.service.finance.gleif;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.fxt.freexmltoolkit.service.finance.FinancialIdentifiers;

/**
 * Checks every LEI of a document at GLEIF: one finding per occurrence of an LEI that GLEIF does not know, that lapsed,
 * was retired or annulled, or whose entity is inactive. In XML, a value with wrong check digits in an LEI element or
 * attribute ({@link LeiElements}) is an error as well - it is never sent to GLEIF. Synthetic example LEIs
 * ({@code XMPL…}, {@link FinancialIdentifiers#isExampleLei}) are not sent either and only counted. Shared by the
 * extensions' panel tool, the validation stage and {@code fxt-cli}.
 */
public final class GleifCheck {

    /** Source and rule id of the findings. */
    public static final String SOURCE = "GLEIF";
    public static final String RULE = "GLEIF-LEI";
    /** Rule id of a value with wrong check digits in an LEI element or attribute. */
    public static final String CHECK_DIGITS_RULE = "LEI-CHECK-DIGITS";

    /** A finding: its rule ({@link #RULE} or {@link #CHECK_DIGITS_RULE}), 1-based line and column. */
    public record Finding(String rule, LeiRecord.Severity severity, int line, int column, String lei, String message) {
    }

    /**
     * The outcome of a check.
     *
     * @param leis     the distinct LEIs checked: those with valid check digits in the text plus the distinct invalid
     *                 values of LEI elements
     * @param records  GLEIF's answers
     * @param findings the problems, in text order
     * @param failure  why GLEIF could not be asked, or {@code null}
     * @param examples the distinct synthetic example LEIs that were not looked up (not part of {@code leis})
     */
    public record Result(int leis, Map<String, LeiRecord> records, List<Finding> findings, String failure, int examples) {
    }

    private GleifCheck() {
    }

    /** Checks the LEIs of a text with the given cache. Never throws: a failed request is {@link Result#failure()}. */
    public static Result check(CharSequence text, LeiCache cache) {
        List<Finding> findings = new ArrayList<>();
        Set<String> invalid = new LinkedHashSet<>();
        for (LeiElements.Invalid i : LeiElements.scan(text.toString())) {
            invalid.add(i.value());
            findings.add(new Finding(CHECK_DIGITS_RULE, LeiRecord.Severity.ERROR, i.line(), i.column(), i.value(),
                    i.message()));
        }
        List<LeiScanner.Occurrence> all = LeiScanner.scan(text);
        List<LeiScanner.Occurrence> occurrences = all.stream()
                .filter(o -> !FinancialIdentifiers.isExampleLei(o.lei())).toList();
        int examples = LeiScanner.distinct(all).size() - LeiScanner.distinct(occurrences).size();
        var distinct = LeiScanner.distinct(occurrences);
        int count = distinct.size() + invalid.size();
        if (distinct.isEmpty()) {
            return new Result(count, Map.of(), findings, null, examples);
        }
        Map<String, LeiRecord> records;
        try {
            records = cache.lookup(distinct);
        } catch (GleifClient.GleifException e) {
            return new Result(count, Map.of(), findings, e.getMessage(), examples);
        }
        for (LeiScanner.Occurrence o : occurrences) {
            LeiRecord r = records.get(o.lei());
            LeiRecord.Problem p = r == null ? null : r.problem();
            if (p != null) {
                findings.add(new Finding(RULE, p.severity(), o.line(), o.column(), o.lei(), p.message()));
            }
        }
        findings.sort(Comparator.comparingInt(Finding::line).thenComparingInt(Finding::column));
        return new Result(count, records, findings, null, examples);
    }

    /** Checks with the application's shared cache. */
    public static Result check(CharSequence text) {
        return check(text, Gleif.cache());
    }
}
