package org.fxt.freexmltoolkit.service.finance.gleif;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** GLEIF lookups without the network: a saved answer of api.gleif.org and fake fetchers. */
class GleifTest {

    /** GLEIF's own LEI (public). */
    static final String GLEIF_LEI = "506700GE1G29325QX363";
    /** Made up, valid check digits, not registered. */
    static final String UNKNOWN = "5299009FXTMMFCP00069";

    @TempDir
    Path dir;

    static byte[] savedAnswer() throws IOException {
        try (InputStream in = GleifTest.class.getResourceAsStream("/gleif/lei-records.json")) {
            return in.readAllBytes();
        }
    }

    /** A fetcher that answers with the saved record for every request and records the asked LEIs. */
    static GleifClient.Fetcher saved(List<List<String>> asked) {
        return uri -> {
            asked.add(leisOf(uri));
            return new GleifClient.Response(200, savedAnswer());
        };
    }

    static List<String> leisOf(URI uri) {
        String query = URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8);
        String value = query.substring(query.indexOf("filter[lei]=") + 12);
        return List.of(value.substring(0, value.indexOf('&')).split(","));
    }

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void parsesGleifsAnswerAndMarksUnknownLeis() throws Exception {
        List<List<String>> asked = new ArrayList<>();
        GleifClient client = new GleifClient(saved(asked), CLOCK);
        Map<String, LeiRecord> records = client.lookup(List.of(GLEIF_LEI, UNKNOWN.toLowerCase(), "529900XXXXXXXXXXXX00"));
        assertEquals(List.of(List.of(GLEIF_LEI, UNKNOWN)), asked, "only valid LEIs are sent, upper case");
        LeiRecord gleif = records.get(GLEIF_LEI);
        assertTrue(gleif.found());
        assertEquals("Global Legal Entity Identifier Foundation", gleif.legalName());
        assertEquals("CH", gleif.country());
        assertEquals("ISSUED", gleif.registrationStatus());
        assertEquals("ACTIVE", gleif.entityStatus());
        assertEquals(LocalDate.of(2027, 3, 15), gleif.nextRenewal());
        assertNull(gleif.problem());
        LeiRecord unknown = records.get(UNKNOWN);
        assertFalse(unknown.found());
        assertEquals(LeiRecord.Severity.ERROR, unknown.problem().severity());
        assertEquals(2, records.size());
    }

    @Test
    void batchesOfTwoHundredAndErrors() throws Exception {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 450; i++) {
            many.add(lei(String.format("5299009FXTBATCH%03d", i)));
        }
        List<List<String>> asked = new ArrayList<>();
        new GleifClient(saved(asked), CLOCK).lookup(many);
        assertEquals(List.of(200, 200, 50), asked.stream().map(List::size).toList());

        GleifClient tooMany = new GleifClient(uri -> new GleifClient.Response(429, new byte[0]), CLOCK);
        assertTrue(assertThrows(GleifClient.GleifException.class, () -> tooMany.lookup(List.of(GLEIF_LEI)))
                .getMessage().contains("too many"));
        GleifClient offline = new GleifClient(uri -> {
            throw new IOException("no route to host");
        }, CLOCK);
        assertTrue(assertThrows(GleifClient.GleifException.class, () -> offline.lookup(List.of(GLEIF_LEI)))
                .getMessage().contains("no route to host"));
    }

    @Test
    void statusesBecomeProblems() {
        Instant now = CLOCK.instant();
        assertNull(record("ISSUED", "ACTIVE").problem());
        assertEquals(LeiRecord.Severity.WARNING, record("LAPSED", "ACTIVE").problem().severity());
        assertEquals(LeiRecord.Severity.ERROR, record("RETIRED", "ACTIVE").problem().severity());
        assertEquals(LeiRecord.Severity.ERROR, record("ANNULLED", "ACTIVE").problem().severity());
        assertEquals(LeiRecord.Severity.WARNING, record("ISSUED", "INACTIVE").problem().severity());
        assertEquals(LeiRecord.Severity.INFO, record("PENDING_TRANSFER", "ACTIVE").problem().severity());
        assertTrue(LeiRecord.notFound(UNKNOWN, now).summary().contains("not registered"));
    }

    private static LeiRecord record(String registration, String entity) {
        return new LeiRecord(GLEIF_LEI, true, "Name", "AT", "Wien", entity, registration, LocalDate.of(2026, 1, 1), null,
                CLOCK.instant());
    }

    @Test
    void scannerFindsLeisWithValidCheckDigits() {
        String text = "<a>\n  <LEI>" + GLEIF_LEI + "</LEI> x" + UNKNOWN + "\n" + UNKNOWN + ";529900XXXXXXXXXXXX00\n"
                + "AB" + GLEIF_LEI + "</a>";
        List<LeiScanner.Occurrence> found = LeiScanner.scan(text);
        assertEquals(List.of(GLEIF_LEI, UNKNOWN), found.stream().map(LeiScanner.Occurrence::lei).toList(),
                "glued to letters or wrong check digits: no LEI");
        assertEquals(2, found.get(0).line());
        assertEquals(8, found.get(0).column());
        assertEquals(3, found.get(1).line());
        assertEquals(1, found.get(1).column());
        int inside = text.indexOf(GLEIF_LEI) + 5;
        assertEquals(GLEIF_LEI, LeiScanner.at(text, inside));
        assertEquals(GLEIF_LEI, LeiScanner.at(text, text.indexOf(GLEIF_LEI)));
        assertNull(LeiScanner.at(text, text.lastIndexOf(GLEIF_LEI) + 3), "part of a longer word");
        assertNull(LeiScanner.at(text, 1));
    }

    @Test
    void cacheAsksOnlyForMissingOrOldAnswersAndKeepsThemInAFile() throws Exception {
        List<List<String>> asked = new ArrayList<>();
        Instant[] now = {CLOCK.instant()};
        Clock clock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now[0];
            }
        };
        Path file = dir.resolve("gleif/cache.json");
        LeiCache cache = new LeiCache(new GleifClient(saved(asked), clock), file, clock, Duration.ofHours(24));
        cache.lookup(List.of(GLEIF_LEI, UNKNOWN));
        cache.lookup(List.of(GLEIF_LEI, UNKNOWN));
        assertEquals(1, asked.size(), "the second lookup is answered from the cache");

        LeiCache reopened = new LeiCache(new GleifClient(saved(asked), clock), file, clock, Duration.ofHours(24));
        assertEquals("Global Legal Entity Identifier Foundation", reopened.cached(GLEIF_LEI).orElseThrow().legalName());
        assertFalse(reopened.cached(UNKNOWN).orElseThrow().found());

        now[0] = now[0].plus(Duration.ofHours(25));
        assertTrue(reopened.cached(GLEIF_LEI).isEmpty(), "too old");
        reopened.lookup(List.of(GLEIF_LEI));
        assertEquals(2, asked.size());
        reopened.clear();
        assertEquals(0, reopened.size());
        assertFalse(java.nio.file.Files.exists(file));
    }

    @Test
    void checkReportsEveryOccurrenceAndFailuresWithoutThrowing() {
        String text = "<r>\n<LEI>" + GLEIF_LEI + "</LEI>\n<LEI>" + UNKNOWN + "</LEI>\n<Other>" + UNKNOWN + "</Other>\n</r>";
        LeiCache cache = new LeiCache(new GleifClient(saved(new ArrayList<>()), CLOCK), null, CLOCK, Duration.ofHours(1));
        GleifCheck.Result result = GleifCheck.check(text, cache);
        assertEquals(2, result.leis());
        assertNull(result.failure());
        assertEquals(List.of(3, 4), result.findings().stream().map(GleifCheck.Finding::line).toList());
        assertEquals(6, result.findings().getFirst().column());

        LeiCache offline = new LeiCache(new GleifClient(uri -> {
            throw new IOException("offline");
        }, CLOCK), null, CLOCK, Duration.ofHours(1));
        GleifCheck.Result failed = GleifCheck.check(text, offline);
        assertNotNull(failed.failure());
        assertTrue(failed.findings().isEmpty());
        assertEquals(0, GleifCheck.check("<r>no lei</r>", offline).leis());
    }

    /** Wrong check digits (the sample in the FundsXML examples): not registered either. */
    static final String WRONG_CHECK_DIGITS = "724500Y6P2B0MRLY2T56";

    @Test
    void wrongCheckDigitsInAnLeiElementAreAnErrorWithoutAskingGleif() {
        String text = "<r>\n<LEI>" + WRONG_CHECK_DIGITS + "</LEI>\n<Other>" + WRONG_CHECK_DIGITS + "</Other>\n"
                + "<Party lei=\"" + WRONG_CHECK_DIGITS + "\"/>\n<LEI>" + WRONG_CHECK_DIGITS + "</LEI>\n</r>";
        List<List<String>> asked = new ArrayList<>();
        LeiCache cache = new LeiCache(new GleifClient(saved(asked), CLOCK), null, CLOCK, Duration.ofHours(1));

        GleifCheck.Result result = GleifCheck.check(text, cache);

        assertTrue(asked.isEmpty(), "a value with wrong check digits is never sent");
        assertEquals(1, result.leis(), "distinct values");
        assertNull(result.failure());
        assertEquals(List.of(2, 4, 5), result.findings().stream().map(GleifCheck.Finding::line).toList(),
                "the LEI elements and the lei attribute, not <Other>");
        GleifCheck.Finding first = result.findings().getFirst();
        assertEquals(GleifCheck.CHECK_DIGITS_RULE, first.rule());
        assertEquals(LeiRecord.Severity.ERROR, first.severity());
        assertEquals(WRONG_CHECK_DIGITS, first.lei());
        assertTrue(first.message().contains("wrong check digits"), first.message());

        var combined = org.fxt.freexmltoolkit.service.finance.IdentifierCheck.check(text, cache, null);
        assertEquals(1, combined.leis());
        assertEquals(GleifCheck.CHECK_DIGITS_RULE, combined.findings().getFirst().rule());
        assertEquals(org.fxt.freexmltoolkit.service.finance.IdentifierCheck.Severity.ERROR,
                combined.findings().getFirst().severity());
    }

    @Test
    void wrongCheckDigitsAreReportedEvenWhenGleifCannotBeAsked() {
        String text = "<r>\n<LEI>" + GLEIF_LEI + "</LEI>\n<IssuerLEI>" + WRONG_CHECK_DIGITS + "</IssuerLEI>\n</r>";
        LeiCache offline = new LeiCache(new GleifClient(uri -> {
            throw new IOException("offline");
        }, CLOCK), null, CLOCK, Duration.ofHours(1));

        GleifCheck.Result result = GleifCheck.check(text, offline);

        assertNotNull(result.failure());
        assertEquals(2, result.leis());
        assertEquals(List.of(GleifCheck.CHECK_DIGITS_RULE), result.findings().stream().map(GleifCheck.Finding::rule).toList());
    }

    @Test
    void leiElementsKnowTheNamesAndLeaveOtherValuesToTheSchema() {
        for (String name : List.of("LEI", "lei", "Lei", "IssuerLEI", "LEICode", "LEI_Code", "LegalEntityIdentifier",
                "issuerLegalEntityIdentifier")) {
            assertTrue(LeiElements.isLeiName(name), name);
        }
        for (String name : List.of("Leistung", "Deleted", "ReleaseDate", "Lei2", "Name")) {
            assertFalse(LeiElements.isLeiName(name), name);
        }
        String text = "<r><LEI>N/A</LEI><LEI> " + WRONG_CHECK_DIGITS + " </LEI><LEI>" + GLEIF_LEI + "</LEI>"
                + "<LEI><Id>" + WRONG_CHECK_DIGITS + "</Id></LEI></r>";
        List<LeiElements.Invalid> invalid = LeiElements.scan(text);
        assertEquals(List.of(WRONG_CHECK_DIGITS), invalid.stream().map(LeiElements.Invalid::value).toList(),
                "only the 20-character value with wrong check digits; other lengths and valid LEIs are not reported");
        assertTrue(LeiElements.scan("LEI;" + WRONG_CHECK_DIGITS).isEmpty(), "no XML");
        assertTrue(LeiElements.scan("<r><LEI>" + WRONG_CHECK_DIGITS + "</LEI><broken").size() == 1,
                "what was found before the error");
    }

    /** @return the base with the two check digits that make it a valid LEI */
    static String lei(String base18) {
        StringBuilder digits = new StringBuilder();
        for (char c : (base18 + "00").toCharArray()) {
            digits.append(Character.digit(c, 36));
        }
        int check = 98 - new java.math.BigInteger(digits.toString()).mod(java.math.BigInteger.valueOf(97)).intValue();
        return base18 + String.format("%02d", check);
    }
}
