package org.fxt.freexmltoolkit.service.finance.gleif;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Checks every LEI of a document at GLEIF: one finding per occurrence of an LEI that GLEIF does not know, that lapsed,
 * was retired or annulled, or whose entity is inactive. Shared by the extensions' panel tool, the validation stage and
 * {@code fxt-cli}.
 */
public final class GleifCheck {

    /** Source and rule id of the findings. */
    public static final String SOURCE = "GLEIF";
    public static final String RULE = "GLEIF-LEI";

    /** A finding: 1-based line and column. */
    public record Finding(LeiRecord.Severity severity, int line, int column, String lei, String message) {
    }

    /**
     * The outcome of a check.
     *
     * @param leis     the distinct LEIs with valid check digits in the text
     * @param records  GLEIF's answers
     * @param findings the problems, in text order
     * @param failure  why GLEIF could not be asked, or {@code null}
     */
    public record Result(int leis, Map<String, LeiRecord> records, List<Finding> findings, String failure) {
    }

    private GleifCheck() {
    }

    /** Checks the LEIs of a text with the given cache. Never throws: a failed request is {@link Result#failure()}. */
    public static Result check(CharSequence text, LeiCache cache) {
        List<LeiScanner.Occurrence> occurrences = LeiScanner.scan(text);
        var distinct = LeiScanner.distinct(occurrences);
        if (distinct.isEmpty()) {
            return new Result(0, Map.of(), List.of(), null);
        }
        Map<String, LeiRecord> records;
        try {
            records = cache.lookup(distinct);
        } catch (GleifClient.GleifException e) {
            return new Result(distinct.size(), Map.of(), List.of(), e.getMessage());
        }
        List<Finding> findings = new ArrayList<>();
        for (LeiScanner.Occurrence o : occurrences) {
            LeiRecord r = records.get(o.lei());
            LeiRecord.Problem p = r == null ? null : r.problem();
            if (p != null) {
                findings.add(new Finding(p.severity(), o.line(), o.column(), o.lei(), p.message()));
            }
        }
        return new Result(distinct.size(), records, findings, null);
    }

    /** Checks with the application's shared cache. */
    public static Result check(CharSequence text) {
        return check(text, Gleif.cache());
    }
}
