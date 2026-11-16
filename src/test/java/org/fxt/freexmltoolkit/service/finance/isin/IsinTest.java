package org.fxt.freexmltoolkit.service.finance.isin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.service.finance.IdentifierCheck;
import org.fxt.freexmltoolkit.service.finance.OnlineFakes;
import org.fxt.freexmltoolkit.service.finance.gleif.GleifClient;
import org.fxt.freexmltoolkit.service.finance.gleif.LeiCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** ISIN lookups without the network: saved answers of ESMA FIRDS and OpenFIGI. */
class IsinTest {

    static final String APPLE = "US0378331005";
    static final String ETF = "IE00B4L5Y983";
    /** Made up, valid check digit, known to nobody. */
    static final String UNKNOWN = "AT000FXTST04";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path dir;

    @Test
    void firdsFirstOpenFigiForTheRest() throws Exception {
        OnlineFakes.REQUESTS.clear();
        Map<String, IsinRecord> records = OnlineFakes.isinLookup(true).lookup(List.of(APPLE, ETF.toLowerCase(), UNKNOWN, "DE0000000001"));
        assertEquals(List.of(APPLE, ETF, UNKNOWN), List.copyOf(records.keySet()), "invalid check digit skipped, upper case");
        IsinRecord etf = records.get(ETF);
        assertEquals(IsinRecord.Source.FIRDS, etf.source());
        assertEquals("iShares Core MSCI World UCITS ETF", etf.name());
        assertEquals("CEOGES", etf.cfi());
        assertEquals("549300QS4Q1IT6XCA514", etf.issuerLei());
        assertEquals(87, etf.totalVenues());
        assertEquals(51, etf.activeVenues(), "UNCH + MODF, not TERM/CANC");
        assertNull(etf.problem());
        assertEquals("ESVUFR", records.get(APPLE).cfi());
        IsinRecord unknown = records.get(UNKNOWN);
        assertFalse(unknown.found());
        assertEquals(IsinRecord.Severity.WARNING, unknown.problem().severity());
        assertEquals(2, OnlineFakes.REQUESTS.size());
        assertTrue(OnlineFakes.REQUESTS.get(1).contains(UNKNOWN) && !OnlineFakes.REQUESTS.get(1).contains(APPLE),
                "only the ISIN FIRDS does not know goes to OpenFIGI: " + OnlineFakes.REQUESTS.get(1));

        OnlineFakes.REQUESTS.clear();
        IsinRecord firdsOnly = OnlineFakes.isinLookup(false).lookup(List.of(UNKNOWN)).get(UNKNOWN);
        assertEquals(IsinRecord.Severity.INFO, firdsOnly.problem().severity(), "without OpenFIGI: only a hint");
        assertEquals(1, OnlineFakes.REQUESTS.size());
    }

    @Test
    void openFigiAnswersAndBatches() throws Exception {
        String saved = new String(OnlineFakes.resource("/isin/openfigi-mapping.json"), StandardCharsets.UTF_8);
        Map<String, IsinRecord> figi = IsinLookup.parseOpenFigi(List.of(UNKNOWN, APPLE), saved, CLOCK.instant());
        assertEquals(List.of(APPLE), List.copyOf(figi.keySet()));
        assertEquals("BBG000B9XRY4", figi.get(APPLE).figi());
        assertEquals("AAPL", figi.get(APPLE).ticker());
        assertEquals("Common Stock", figi.get(APPLE).securityType());
        assertNull(figi.get(APPLE).problem());

        List<Integer> jobs = new ArrayList<>();
        String emptyFirds = "{\"grouped\":{\"isin\":{\"groups\":[]}}}";
        IsinLookup lookup = new IsinLookup(uri -> new IsinLookup.Response(200, emptyFirds.getBytes(StandardCharsets.UTF_8)),
                (uri, json, headers) -> {
                    int n = com.google.gson.JsonParser.parseString(json).getAsJsonArray().size();
                    jobs.add(n);
                    return new IsinLookup.Response(200, ("[" + "{\"warning\":\"No identifier found.\"},".repeat(n - 1)
                            + "{\"warning\":\"No identifier found.\"}]").getBytes(StandardCharsets.UTF_8));
                }, Clock.systemUTC(), true, null);
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            many.add(isin(String.format("AT000FXT%03d", i)));
        }
        lookup.lookup(many);
        assertEquals(List.of(10, 2), jobs, "10 jobs per OpenFIGI request without a key");

        IsinLookup tooMany = new IsinLookup(uri -> new IsinLookup.Response(200, emptyFirds.getBytes(StandardCharsets.UTF_8)),
                (uri, json, headers) -> new IsinLookup.Response(429, new byte[0]), Clock.systemUTC(), true, null);
        assertTrue(assertThrows(IsinLookup.LookupException.class, () -> tooMany.lookup(List.of(UNKNOWN)))
                .getMessage().contains("API key"));
        IsinLookup offline = new IsinLookup(uri -> {
            throw new IOException("no route to host");
        }, null, Clock.systemUTC(), true, null);
        assertTrue(assertThrows(IsinLookup.LookupException.class, () -> offline.lookup(List.of(APPLE)))
                .getMessage().contains("ESMA FIRDS could not be reached"));
    }

    @Test
    void scannerFindsIsinsWithCountryAndCheckDigit() {
        String text = "<a>\n <ISIN>" + APPLE + "</ISIN>;" + ETF + "\nXX0378331005 DE0000000001 AB" + APPLE + "\n</a>";
        var found = IsinScanner.scan(text);
        assertEquals(List.of(APPLE, ETF), found.stream().map(IsinScanner.Occurrence::isin).toList(),
                "unknown country prefix, wrong check digit or glued to letters: no ISIN");
        assertEquals(2, found.get(0).line());
        assertEquals(8, found.get(0).column());
        assertEquals(APPLE, IsinScanner.at(text, text.indexOf(APPLE) + 4));
        assertNull(IsinScanner.at(text, text.lastIndexOf(APPLE) + 3));
        assertTrue(IsinScanner.isIsin("XS0000000009") || !IsinScanner.isIsin("XS0000000008"));
    }

    @Test
    void cacheKeepsAnswersInAFile() throws Exception {
        Path file = dir.resolve("isin/cache.json");
        int[] asked = {0};
        Isin.Cache cache = new Isin.Cache(file, Clock.systemUTC(), Duration.ofHours(1), () -> {
            asked[0]++;
            return OnlineFakes.isinLookup(true);
        });
        cache.lookup(List.of(ETF, UNKNOWN));
        cache.lookup(List.of(ETF, UNKNOWN));
        assertEquals(1, asked[0]);
        Isin.Cache reopened = new Isin.Cache(file, Clock.systemUTC(), Duration.ofHours(1), () -> {
            throw new AssertionError("no request");
        });
        assertEquals("CEOGES", reopened.cached(ETF).orElseThrow().cfi());
        assertFalse(reopened.cached(UNKNOWN).orElseThrow().found());
    }

    @Test
    void identifierCheckCombinesLeisAndIsins() {
        String text = "<r>\n<LEI>5299009FXTMMFCP00069</LEI>\n<ISIN>" + UNKNOWN + "</ISIN>\n<ISIN>" + ETF + "</ISIN>\n</r>";
        LeiCache leis = new LeiCache(new GleifClient(uri -> new GleifClient.Response(200,
                OnlineFakes.resource("/gleif/lei-records.json")), Clock.systemUTC()), null, Clock.systemUTC(), Duration.ofHours(1));
        Isin.Cache isins = new Isin.Cache(null, Clock.systemUTC(), Duration.ofHours(1), () -> OnlineFakes.isinLookup(true));
        IdentifierCheck.Result result = IdentifierCheck.check(text, leis, isins);
        assertEquals(1, result.leis());
        assertEquals(2, result.isins());
        assertEquals(List.of("GLEIF:2", "ISIN:3"), result.findings().stream().map(f -> f.source() + ":" + f.line()).toList());
        assertTrue(result.failures().isEmpty());

        Isin.Cache offline = new Isin.Cache(null, Clock.systemUTC(), Duration.ofHours(1), () -> new IsinLookup(uri -> {
            throw new IOException("offline");
        }, null, Clock.systemUTC(), true, null));
        IdentifierCheck.Result half = IdentifierCheck.check(text, leis, offline);
        assertEquals(List.of("GLEIF"), half.findings().stream().map(IdentifierCheck.Finding::source).toList());
        assertEquals("ISIN", half.failures().getFirst().source());
        assertNotNull(IdentifierCheck.check(text, null, null));
    }

    @Test
    void cfiCodesArePairedWithTheirIsin() {
        String xml = """
                <Rpt>
                  <Hldg>
                    <AsstId>
                      <ClssfctnTp>CIOGES</ClssfctnTp>
                      <InstrmId><ISIN>IE00B4L5Y983</ISIN></InstrmId>
                    </AsstId>
                  </Hldg>
                  <Hldg>
                    <AsstId><ClssfctnTp>ESXXXX</ClssfctnTp><InstrmId><ISIN>US0378331005</ISIN></InstrmId></AsstId>
                  </Hldg>
                  <Both><CFI>DBFTFB</CFI><ISIN>IE00B4L5Y983</ISIN><ISIN>US0378331005</ISIN></Both>
                  <Note><ClssfctnTp>not a CFI</ClssfctnTp><ISIN>US0378331005</ISIN></Note>
                </Rpt>
                """;
        var pairs = CfiPairs.scan(xml);
        assertEquals(List.of("IE00B4L5Y983=CIOGES@4", "US0378331005=ESXXXX@9"),
                pairs.stream().map(p -> p.isin() + "=" + p.cfi() + "@" + p.line()).toList(),
                "two ISINs next to the CFI: ambiguous, no pair; a text that is no CFI: no pair");
        assertTrue(CfiPairs.scan("ISIN;CFI\nIE00B4L5Y983;CEOGES").isEmpty(), "not XML");
        assertTrue(CfiPairs.scan("<a><b>").isEmpty(), "not well-formed");

        assertTrue(CfiPairs.matches("CEOGES", "CEOGES"));
        assertTrue(CfiPairs.matches("CEXXXX", "CEOGES"), "X in the report: a construct");
        assertTrue(CfiPairs.matches("ESVUFR", "ESXXXX"));
        assertFalse(CfiPairs.matches("CIOGES", "CEOGES"));
        assertFalse(CfiPairs.matches("DBXXXX", "ESVUFR"));
    }

    @Test
    void aReportedCfiCodeThatDiffersFromFirdsIsAWarning() {
        String xml = "<Rpt>\n<AsstId>\n<ClssfctnTp>CIOGES</ClssfctnTp>\n<ISIN>" + ETF + "</ISIN>\n</AsstId>\n"
                + "<AsstId><ClssfctnTp>ESXXXX</ClssfctnTp><ISIN>" + APPLE + "</ISIN></AsstId>\n</Rpt>";
        Isin.Cache isins = new Isin.Cache(null, Clock.systemUTC(), Duration.ofHours(1), () -> OnlineFakes.isinLookup(true));
        IdentifierCheck.Result result = IdentifierCheck.check(xml, null, isins);
        assertEquals(1, result.findings().size(), result.findings().toString());
        IdentifierCheck.Finding f = result.findings().getFirst();
        assertEquals(IdentifierCheck.CFI_RULE, f.rule());
        assertEquals(3, f.line());
        assertTrue(f.message().contains("CIOGES") && f.message().contains("CEOGES"), f.message());
    }

    /** @return the base with the check digit that makes it a valid ISIN */
    static String isin(String base11) {
        StringBuilder digits = new StringBuilder();
        for (char c : base11.toCharArray()) {
            digits.append(Character.digit(c, 36));
        }
        int sum = 0;
        for (int i = 0; i < digits.length(); i++) {
            int d = digits.charAt(digits.length() - 1 - i) - '0';
            if (i % 2 == 0) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
        }
        return base11 + (10 - sum % 10) % 10;
    }
}
