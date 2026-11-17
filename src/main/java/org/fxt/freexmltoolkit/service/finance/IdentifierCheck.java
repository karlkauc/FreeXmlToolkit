package org.fxt.freexmltoolkit.service.finance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.service.finance.gleif.Gleif;
import org.fxt.freexmltoolkit.service.finance.gleif.GleifCheck;
import org.fxt.freexmltoolkit.service.finance.gleif.LeiCache;
import org.fxt.freexmltoolkit.service.finance.gleif.LeiRecord;
import org.fxt.freexmltoolkit.service.finance.isin.Isin;
import org.fxt.freexmltoolkit.service.finance.isin.IsinLookup;
import org.fxt.freexmltoolkit.service.finance.isin.IsinPairs;
import org.fxt.freexmltoolkit.service.finance.isin.IsinRecord;
import org.fxt.freexmltoolkit.service.finance.isin.IsinScanner;

/**
 * Checks the identifiers of a document online: LEIs at GLEIF, ISINs at ESMA FIRDS (OpenFIGI for the rest). One finding
 * per occurrence; a service that cannot be reached is reported in {@link Result#failures()} while the other part
 * still counts. Synthetic example identifiers ({@code XMPL…} LEIs, {@code CCXMPL…} ISINs) are never sent, only
 * counted in {@link Result#examples()}. Shared by the panels, the Tools menu, the validation stage and {@code fxt-cli}.
 */
public final class IdentifierCheck {

    /** Source and rule id of the ISIN findings (LEIs: {@link GleifCheck#SOURCE}, {@link GleifCheck#RULE}). */
    public static final String ISIN_SOURCE = "ISIN";
    public static final String ISIN_RULE = "ISIN-LOOKUP";
    /** Rule id of a reported CFI code that does not agree with ESMA FIRDS. */
    public static final String CFI_RULE = "ISIN-CFI";
    /** Rule id of a reported issuer LEI that does not agree with ESMA FIRDS. */
    public static final String ISSUER_RULE = "ISIN-ISSUER";

    /** Severity of a finding. */
    public enum Severity { ERROR, WARNING, INFO }

    /** A finding: 1-based line and column. */
    public record Finding(String source, String rule, Severity severity, int line, int column, String id, String message) {
    }

    /** A service that could not be asked: the findings' source ({@code GLEIF} / {@code ISIN}) and why. */
    public record Failure(String source, String message) {
    }

    /** All sources of the findings. */
    public static final java.util.Set<String> SOURCES = java.util.Set.of(GleifCheck.SOURCE, ISIN_SOURCE);

    /**
     * @param leis     distinct LEIs checked (0 when LEIs were not checked)
     * @param isins    distinct ISINs checked
     * @param findings the findings in text order
     * @param failures why a service could not be asked
     * @param leiRecords  GLEIF's answers
     * @param isinRecords the ISIN answers
     * @param examples    distinct synthetic example LEIs and ISINs that were not looked up
     */
    public record Result(int leis, int isins, List<Finding> findings, List<Failure> failures,
                         Map<String, LeiRecord> leiRecords, Map<String, IsinRecord> isinRecords, int examples) {
    }

    private IdentifierCheck() {
    }

    /** @return whether any online check is allowed */
    public static boolean anyAllowed() {
        return Gleif.allowed() || Isin.allowed();
    }

    /** Checks with the application's caches; each part only when allowed and asked for. */
    public static Result check(CharSequence text, boolean leis, boolean isins) {
        return check(text, leis && Gleif.allowed() ? Gleif.cache() : null, isins && Isin.allowed() ? Isin.cache() : null);
    }

    /** Checks with the given caches ({@code null} = that part is not checked). Never throws. */
    public static Result check(CharSequence text, LeiCache leiCache, Isin.Cache isinCache) {
        List<Finding> findings = new ArrayList<>();
        List<Failure> failures = new ArrayList<>();
        int leiCount = 0;
        int examples = 0;
        Map<String, LeiRecord> leiRecords = Map.of();
        if (leiCache != null) {
            GleifCheck.Result r = GleifCheck.check(text, leiCache);
            leiCount = r.leis();
            examples += r.examples();
            leiRecords = r.records();
            if (r.failure() != null) {
                failures.add(new Failure(GleifCheck.SOURCE, r.failure()));
            }
            for (GleifCheck.Finding f : r.findings()) {
                findings.add(new Finding(GleifCheck.SOURCE, f.rule(), switch (f.severity()) {
                    case ERROR -> Severity.ERROR;
                    case WARNING -> Severity.WARNING;
                    case INFO -> Severity.INFO;
                }, f.line(), f.column(), f.lei(), f.message()));
            }
        }
        int isinCount = 0;
        Map<String, IsinRecord> isinRecords = Map.of();
        if (isinCache != null) {
            var all = IsinScanner.scan(text);
            var occurrences = all.stream().filter(o -> !FinancialIdentifiers.isExampleIsin(o.isin())).toList();
            var distinct = IsinScanner.distinct(occurrences);
            examples += IsinScanner.distinct(all).size() - distinct.size();
            isinCount = distinct.size();
            if (!distinct.isEmpty()) {
                try {
                    isinRecords = isinCache.lookup(distinct);
                    for (IsinScanner.Occurrence o : occurrences) {
                        IsinRecord rec = isinRecords.get(o.isin());
                        IsinRecord.Problem p = rec == null ? null : rec.problem();
                        if (p != null) {
                            findings.add(new Finding(ISIN_SOURCE, ISIN_RULE,
                                    p.severity() == IsinRecord.Severity.WARNING ? Severity.WARNING : Severity.INFO,
                                    o.line(), o.column(), o.isin(), p.message()));
                        }
                    }
                    // reported CFI codes and issuer LEIs against FIRDS
                    for (IsinPairs.Pair pair : IsinPairs.scan(text.toString())) {
                        IsinRecord rec = isinRecords.get(pair.isin());
                        if (rec == null || rec.source() != IsinRecord.Source.FIRDS) {
                            continue;
                        }
                        String about = rec.name() == null ? "" : " (" + rec.name() + ")";
                        if (pair.kind() == IsinPairs.Kind.CFI && rec.cfi() != null && !IsinPairs.cfiMatches(pair.value(), rec.cfi())) {
                            findings.add(new Finding(ISIN_SOURCE, CFI_RULE, Severity.WARNING, pair.line(), pair.column(),
                                    pair.isin(), "The reported CFI code " + pair.value() + " of ISIN " + pair.isin()
                                    + " differs from ESMA FIRDS: " + rec.cfi() + about + "."));
                        } else if (pair.kind() == IsinPairs.Kind.ISSUER_LEI && rec.issuerLei() != null
                                && !rec.issuerLei().equals(pair.value())) {
                            findings.add(new Finding(ISIN_SOURCE, ISSUER_RULE, Severity.WARNING, pair.line(), pair.column(),
                                    pair.isin(), "The reported issuer LEI " + pair.value() + " of ISIN " + pair.isin()
                                    + " differs from ESMA FIRDS: " + rec.issuerLei() + about + "."));
                        }
                    }
                } catch (IsinLookup.LookupException e) {
                    failures.add(new Failure(ISIN_SOURCE, e.getMessage()));
                }
            }
        }
        findings.sort(Comparator.comparingInt(Finding::line).thenComparingInt(Finding::column));
        return new Result(leiCount, isinCount, findings, failures, leiRecords, isinRecords, examples);
    }
}
