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
import org.fxt.freexmltoolkit.service.progress.CancelledException;
import org.fxt.freexmltoolkit.service.progress.Progress;
import org.fxt.freexmltoolkit.service.progress.ProgressParts;

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
     * @param unchecked   identifiers a service could not be asked about (failure or cancellation)
     * @param cancelled   whether the user cancelled the check (the findings cover what was answered before)
     */
    public record Result(int leis, int isins, List<Finding> findings, List<Failure> failures,
                         Map<String, LeiRecord> leiRecords, Map<String, IsinRecord> isinRecords, int examples,
                         int unchecked, boolean cancelled) {

        public Result(int leis, int isins, List<Finding> findings, List<Failure> failures,
                      Map<String, LeiRecord> leiRecords, Map<String, IsinRecord> isinRecords, int examples) {
            this(leis, isins, findings, failures, leiRecords, isinRecords, examples, 0, false);
        }
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
        return check(text, leiCache, isinCache, Progress.NONE);
    }

    /** Checks with the application's caches, reporting progress; each part only when allowed and asked for. */
    public static Result check(CharSequence text, boolean leis, boolean isins, Progress progress) {
        return check(text, leis && Gleif.allowed() ? Gleif.cache() : null, isins && Isin.allowed() ? Isin.cache() : null,
                progress);
    }

    /**
     * Checks with the given caches ({@code null} = that part is not checked), GLEIF and the ISIN services side by side.
     * Never throws: a service that fails becomes a {@link Failure}; a cancelled run ({@link Result#cancelled()})
     * reports the identifiers answered before. Either way the answers got so far are cached, so the next run asks
     * only for the rest.
     */
    public static Result check(CharSequence text, LeiCache leiCache, Isin.Cache isinCache, Progress progress) {
        ProgressParts parts = new ProgressParts(progress, 2);
        java.util.concurrent.CompletableFuture<GleifCheck.Result> leiPart = leiCache == null
                ? java.util.concurrent.CompletableFuture.completedFuture(null)
                : java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        return GleifCheck.check(text, leiCache, parts.part(0));
                    } finally {
                        parts.finished(0);
                    }
                }, runnable -> Thread.ofVirtual().name("identifier-check-gleif").start(runnable));
        IsinPart isinPart = isinCache == null ? null : checkIsins(text, isinCache, parts.part(1));
        parts.finished(1);
        GleifCheck.Result lei = leiPart.join();

        List<Finding> findings = new ArrayList<>();
        List<Failure> failures = new ArrayList<>();
        int leiCount = 0;
        int examples = 0;
        int unchecked = 0;
        boolean cancelled = false;
        Map<String, LeiRecord> leiRecords = Map.of();
        if (lei != null) {
            leiCount = lei.leis();
            examples += lei.examples();
            leiRecords = lei.records();
            unchecked += lei.unchecked();
            cancelled |= lei.cancelled();
            if (lei.failure() != null) {
                failures.add(new Failure(GleifCheck.SOURCE, withUnchecked(lei.failure(), lei.unchecked())));
            }
            for (GleifCheck.Finding f : lei.findings()) {
                findings.add(new Finding(GleifCheck.SOURCE, f.rule(), switch (f.severity()) {
                    case ERROR -> Severity.ERROR;
                    case WARNING -> Severity.WARNING;
                    case INFO -> Severity.INFO;
                }, f.line(), f.column(), f.lei(), f.message()));
            }
        }
        int isinCount = 0;
        Map<String, IsinRecord> isinRecords = Map.of();
        if (isinPart != null) {
            isinCount = isinPart.count();
            examples += isinPart.examples();
            isinRecords = isinPart.records();
            unchecked += isinPart.unchecked();
            cancelled |= isinPart.cancelled();
            if (isinPart.failure() != null) {
                failures.add(new Failure(ISIN_SOURCE, withUnchecked(isinPart.failure(), isinPart.unchecked())));
            }
            findings.addAll(isinPart.findings());
        }
        findings.sort(Comparator.comparingInt(Finding::line).thenComparingInt(Finding::column));
        return new Result(leiCount, isinCount, findings, failures, leiRecords, isinRecords, examples, unchecked, cancelled);
    }

    /** The outcome of the ISIN part. */
    private record IsinPart(int count, int examples, Map<String, IsinRecord> records, List<Finding> findings,
                            String failure, int unchecked, boolean cancelled) {
    }

    private static IsinPart checkIsins(CharSequence text, Isin.Cache isinCache, Progress progress) {
        progress.message(org.fxt.freexmltoolkit.i18n.I18n.t("Progress.scanning-isins"));
        var all = IsinScanner.scan(text);
        var occurrences = all.stream().filter(o -> !FinancialIdentifiers.isExampleIsin(o.isin())).toList();
        var distinct = IsinScanner.distinct(occurrences);
        int examples = IsinScanner.distinct(all).size() - distinct.size();
        if (distinct.isEmpty()) {
            return new IsinPart(0, examples, Map.of(), List.of(), null, 0, false);
        }
        Map<String, IsinRecord> records;
        String failure = null;
        boolean cancelled = false;
        try {
            records = isinCache.lookup(distinct, progress);
        } catch (IsinLookup.LookupException e) {
            failure = e.getMessage();
            records = answered(distinct, isinCache);
        } catch (CancelledException e) {
            cancelled = true;
            records = answered(distinct, isinCache);
        }
        Map<String, IsinRecord> got = records;
        int unchecked = (int) distinct.stream().filter(i -> !got.containsKey(i)).count();
        List<Finding> findings = new ArrayList<>();
        for (IsinScanner.Occurrence o : occurrences) {
            IsinRecord rec = records.get(o.isin());
            IsinRecord.Problem p = rec == null ? null : rec.problem();
            if (p != null) {
                findings.add(new Finding(ISIN_SOURCE, ISIN_RULE,
                        p.severity() == IsinRecord.Severity.WARNING ? Severity.WARNING : Severity.INFO,
                        o.line(), o.column(), o.isin(), p.message()));
            }
        }
        // reported CFI codes and issuer LEIs against FIRDS
        for (IsinPairs.Pair pair : IsinPairs.scan(text.toString())) {
            IsinRecord rec = records.get(pair.isin());
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
        return new IsinPart(distinct.size(), examples, records, findings, failure, unchecked, cancelled);
    }

    /** @return the answers already known for the ISINs (those a failed or cancelled run got before it ended) */
    private static Map<String, IsinRecord> answered(java.util.Set<String> isins, Isin.Cache cache) {
        Map<String, IsinRecord> known = new java.util.LinkedHashMap<>();
        for (String isin : isins) {
            cache.cached(isin).ifPresent(r -> known.put(isin, r));
        }
        return known;
    }

    /** Adds how many identifiers were not checked to a service's failure. */
    private static String withUnchecked(String failure, int unchecked) {
        return unchecked <= 0 ? failure
                : failure + " " + org.fxt.freexmltoolkit.i18n.I18n.t("Progress.not-checked-continue", unchecked);
    }
}
