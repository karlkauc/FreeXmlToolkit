package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.fxt.freexmltoolkit.service.finance.gleif.Gleif;
import org.fxt.freexmltoolkit.service.finance.gleif.GleifClient;
import org.fxt.freexmltoolkit.service.finance.gleif.LeiCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The optional GLEIF stage of a validation (setting "check LEIs at GLEIF on validation"). */
class ValidationRunnerGleifTest {

    private static final String XML = "<r>\n  <LEI>506700GE1G29325QX363</LEI>\n  <LEI>5299009FXTMMFCP00069</LEI>\n</r>\n";

    @AfterEach
    void reset() {
        Gleif.setCache(null);
        Gleif.setValidationOverride(null);
        org.fxt.freexmltoolkit.service.finance.isin.Isin.setValidationOverride(null);
        org.fxt.freexmltoolkit.service.finance.OnlineFakes.reset();
    }

    @Test
    void isinStageOnlyWhenTurnedOn() {
        org.fxt.freexmltoolkit.service.finance.OnlineFakes.install();
        Gleif.setValidationOverride(false);
        org.fxt.freexmltoolkit.service.finance.isin.Isin.setValidationOverride(true);
        String xml = "<r>\n  <ISIN>IE00B4L5Y983</ISIN>\n  <ISIN>AT000FXTST04</ISIN>\n  <LEI>5299009FXTMMFCP00069</LEI>\n</r>\n";
        List<ValidationProblem> problems = ValidationRunner.runWithReport(xml, null, null, "r.xml").problems().stream()
                .filter(p -> !"Well-formed".equals(p.source())).toList();
        assertEquals(List.of("ISIN:3"), problems.stream().map(p -> p.source() + ":" + p.line()).toList(), problems.toString());
        assertEquals("warning", problems.getFirst().severity());
    }

    private static void fake(GleifClient.Fetcher fetcher) {
        Gleif.setCache(new LeiCache(new GleifClient(fetcher, Clock.systemUTC()), null, Clock.systemUTC(), Duration.ofHours(1)));
    }

    private static GleifClient.Response saved() throws IOException {
        try (InputStream in = ValidationRunnerGleifTest.class.getResourceAsStream("/gleif/lei-records.json")) {
            return new GleifClient.Response(200, in.readAllBytes());
        }
    }

    private static List<ValidationProblem> gleifProblems() {
        return ValidationRunner.runWithReport(XML, null, null, "r.xml").problems().stream()
                .filter(p -> "GLEIF".equals(p.source())).toList();
    }

    @Test
    void onlyWhenTurnedOn() {
        fake(uri -> saved());
        Gleif.setValidationOverride(false);
        assertEquals(List.of(), gleifProblems());

        Gleif.setValidationOverride(true);
        List<ValidationProblem> problems = gleifProblems();
        assertEquals(1, problems.size(), problems.toString());
        assertEquals("error", problems.getFirst().severity());
        assertEquals(3, problems.getFirst().line());
        assertEquals("GLEIF-LEI", problems.getFirst().ruleId());
    }

    @Test
    void unreachableGleifIsAWarningOnly() {
        fake(uri -> {
            throw new IOException("offline");
        });
        Gleif.setValidationOverride(true);
        List<ValidationProblem> problems = gleifProblems();
        assertEquals(1, problems.size());
        assertEquals("warning", problems.getFirst().severity());
        assertTrue(problems.getFirst().message().contains("offline"));
    }
}
