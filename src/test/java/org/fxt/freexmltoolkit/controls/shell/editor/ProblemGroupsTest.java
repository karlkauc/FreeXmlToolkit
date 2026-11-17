package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.plugin.DocumentValidator;
import org.fxt.freexmltoolkit.plugin.PluginManager;
import org.fxt.freexmltoolkit.service.finance.IdentifierCheck;
import org.fxt.freexmltoolkit.service.finance.isin.IsinRecord;
import org.junit.jupiter.api.Test;

/** Categories, subjects and groups of problems: a security that occurs many times is one group. */
class ProblemGroupsTest {

    static final String ISIN = "US0378331005";
    static final String ISIN2 = "IE00B4L5Y983";

    static ValidationProblem online(String isin, String rule, String severity, int line) {
        return new ValidationProblem("ISIN", severity, line, "ISIN " + isin + " …", rule, null)
                .withSubject(new ProblemSubject(ProblemSubject.Kind.ISIN, isin, "Apple"));
    }

    @Test
    void onlineFindingsKeepTheirSecurityCategoryAndHintLevel() {
        IsinRecord apple = new IsinRecord(ISIN, IsinRecord.Source.FIRDS, "Apple Inc.", "ESVUFR", null, "USD", 0, 2, null,
                null, null, null, false, Instant.now(), "1");
        var result = new IdentifierCheck.Result(0, 1, List.of(
                new IdentifierCheck.Finding("ISIN", IdentifierCheck.ISIN_RULE, IdentifierCheck.Severity.INFO, 4, 9, ISIN,
                        "ISIN US0378331005 is not traded on an EU venue."),
                new IdentifierCheck.Finding("ISIN", IdentifierCheck.CFI_RULE, IdentifierCheck.Severity.WARNING, 5, 9, ISIN,
                        "The reported CFI code …")), List.of(), Map.of(), Map.of(ISIN, apple), 0);

        List<ValidationProblem> problems = ValidationRunner.toProblems(result);

        assertEquals("info", problems.get(0).severity(), "a hint stays a hint");
        assertEquals(ValidationProblem.Level.INFO, problems.get(0).level());
        assertEquals(ProblemCategory.ISIN_NOT_TRADED, problems.get(0).category());
        assertEquals(ProblemCategory.ISIN_CFI, problems.get(1).category());
        assertEquals(new ProblemSubject(ProblemSubject.Kind.ISIN, ISIN, "Apple Inc."), problems.get(1).subject());
    }

    @Test
    void pluginProblemsCarryRuleCategoryAndSubject() {
        var found = new PluginManager.ValidatorProblem("PK-Meldewesen", "error", 12, "PK123 Sum – VRG 1 · DE0001102424",
                new DocumentValidator.Problem("error", 12, "…").withRule("PK123").withCategory("FMA_ASSETS")
                        .withSubject("ASSET", ISIN2, "iShares Core"));
        ValidationProblem p = ValidationRunner.fromPlugin(found);
        assertEquals("PK123", p.ruleId());
        assertEquals(ProblemCategory.FMA_ASSETS, p.category());
        assertEquals(ProblemSubject.Kind.ISIN, p.subject().kind(), "an asset id that is an ISIN");
        assertEquals("iShares Core", p.subject().label());

        ValidationProblem internal = ValidationRunner.fromPlugin(new PluginManager.ValidatorProblem("PK-Meldewesen",
                "warning", 3, "x", new DocumentValidator.Problem("warning", 3, "x").withSubject("ASSET", "FUND-0815", null)));
        assertEquals(ProblemSubject.Kind.ASSET, internal.subject().kind());
        assertEquals(ProblemCategory.PLUGIN_RULE, internal.category(), "no category given: the extension's rules");

        ValidationProblem plain = ValidationRunner.fromPlugin(new PluginManager.ValidatorProblem("ISO 20022 rules", "error", 1, "m"));
        assertNull(plain.subject());
        assertEquals(ProblemCategory.PLUGIN_RULE, plain.category());
    }

    @Test
    void categoriesAreDerivedForOldSources() {
        assertEquals(ProblemCategory.SCHEMA, new ValidationProblem("XSD", "error", 1, "cvc-x").effectiveCategory());
        assertEquals(ProblemCategory.WELL_FORMED, new ValidationProblem("Well-formed", "error", 1, "x").effectiveCategory());
        assertEquals(ProblemCategory.LEI_CHECK_DIGITS,
                new ValidationProblem("GLEIF", "error", 1, "x", "LEI-CHECK-DIGITS", null).effectiveCategory());
        assertEquals(ProblemCategory.LEI_PENDING, new ValidationProblem("GLEIF", "info", 1, "x").effectiveCategory());
        assertEquals(ProblemCategory.ONLINE_SERVICE,
                new ValidationProblem("ISIN", "warning", 0, "Not checked online: offline").effectiveCategory());
        assertEquals(ProblemCategory.FMA_RECONCILIATION, ProblemCategory.ofFmaGroup("abgl", false));
        assertEquals(ProblemCategory.FMA_ADDITIONAL, ProblemCategory.ofFmaGroup("assets", true));
    }

    @Test
    void groupedBySecurityAHundredOccurrencesAreOneGroup() {
        List<ValidationProblem> problems = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            problems.add(online(ISIN, IdentifierCheck.CFI_RULE, "warning", 10 + i));
            problems.add(online(ISIN, IdentifierCheck.ISIN_RULE, "info", 10 + i));
        }
        problems.add(online(ISIN2, IdentifierCheck.ISIN_RULE, "warning", 500));
        problems.add(new ValidationProblem("XSD", "error", 7, "cvc-complex-type.2.4.a: Invalid content at A"));
        problems.add(new ValidationProblem("XSD", "error", 8, "cvc-complex-type.2.4.a: Invalid content at B"));

        List<ProblemGroups.Group> groups = ProblemGroups.group(problems, ProblemGroups.By.SUBJECT);

        assertEquals(3, groups.size());
        assertEquals(ISIN2, groups.get(1).subject().key(), "same worst level: more occurrences first");
        ProblemGroups.Group apple = groups.getFirst();
        assertEquals(ISIN, apple.subject().key());
        assertEquals(200, apple.count());
        assertEquals(2, apple.kinds().size(), "two kinds of problems");
        assertEquals(ProblemGroups.ruleKey(problems.getFirst()), apple.kinds().getFirst().key(), "warnings before hints");
        ProblemGroups.Group xsd = groups.getLast();
        assertNull(xsd.subject(), "problems without a security come last, grouped by rule");
        assertEquals(2, xsd.count(), "the same XSD error code is one kind");
        assertTrue(xsd.title().startsWith(ValidationRunnerTestTexts.NO_SUBJECT), xsd.title());

        List<ProblemGroups.Group> byRule = ProblemGroups.group(problems, ProblemGroups.By.RULE);
        assertEquals(ProblemCategory.SCHEMA, byRule.getFirst().category(), "errors first");
        ProblemGroups.Group cfi = byRule.stream().filter(g -> g.category() == ProblemCategory.ISIN_CFI).findFirst().orElseThrow();
        assertEquals(100, cfi.count());
        assertEquals(1, cfi.kinds().size(), "one security");

        List<ProblemGroups.Group> byCategory = ProblemGroups.group(problems, ProblemGroups.By.CATEGORY);
        assertEquals(4, byCategory.size(), "schema, CFI, unknown, not traded");
        assertEquals(Map.of(ProblemCategory.ISIN_CFI, 100, ProblemCategory.ISIN_NOT_TRADED, 100,
                ProblemCategory.ISIN_UNKNOWN, 1, ProblemCategory.SCHEMA, 2), ProblemGroups.countByCategory(problems));
    }

    /** Texts of the test locale (en). */
    static final class ValidationRunnerTestTexts {
        static final String NO_SUBJECT = "No security";
    }
}
