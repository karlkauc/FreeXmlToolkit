package org.fxt.freexmltoolkit.service.finance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.fxt.freexmltoolkit.service.finance.gleif.GleifClient;
import org.fxt.freexmltoolkit.service.finance.gleif.LeiCache;
import org.fxt.freexmltoolkit.service.finance.isin.Isin;
import org.fxt.freexmltoolkit.service.finance.isin.IsinLookup;
import org.fxt.freexmltoolkit.service.progress.CancelledException;
import org.fxt.freexmltoolkit.service.progress.Progress;
import org.junit.jupiter.api.Test;

/**
 * Progress, cancellation and kept answers of the online identifier check (no network: fake services, a clock that
 * moves on so the request pacing never waits).
 */
class IdentifierProgressTest {

    /** A clock 10 s further at every reading: the pacing between requests is always over. */
    static Clock stepping() {
        AtomicLong now = new AtomicLong(1_800_000_000_000L);
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return Instant.ofEpochMilli(now.addAndGet(10_000));
            }
        };
    }

    /** @return {@code count} distinct LEIs with valid check digits */
    static List<String> leis(int count) {
        List<String> leis = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String base = String.format("TEST00PROGRESS%04d", i);
            BigInteger number = new BigInteger(toDigits(base + "00"));
            int check = 98 - number.mod(BigInteger.valueOf(97)).intValue();
            leis.add(base + String.format("%02d", check));
        }
        return leis;
    }

    /** @return {@code count} distinct ISINs with a valid check digit */
    static List<String> isins(int count) {
        List<String> isins = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String base = String.format("DE000T%05d", i);
            for (int d = 0; d <= 9; d++) {
                if (FinancialIdentifiers.checkIsin(base + d) == null) {
                    isins.add(base + d);
                    break;
                }
            }
        }
        return isins;
    }

    private static String toDigits(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            b.append(Character.isDigit(c) ? String.valueOf(c) : String.valueOf(c - 'A' + 10));
        }
        return b.toString();
    }

    /** A progress that records messages and steps and can be cancelled from the test. */
    static final class Recording implements Progress {
        final List<String> messages = new CopyOnWriteArrayList<>();
        final List<Hint> hints = new CopyOnWriteArrayList<>();
        final AtomicBoolean cancelled = new AtomicBoolean();
        volatile long done;
        volatile long total;

        @Override
        public void message(String text) {
            messages.add(text);
        }

        @Override
        public void progress(long done, long total) {
            this.done = done;
            this.total = total;
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }

        @Override
        public void hint(Hint hint) {
            hints.add(hint);
        }
    }

    private static GleifClient.Response noRecords() {
        return new GleifClient.Response(200, "{\"data\":[]}".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void gleifReportsEachRequestAndKeepsTheAnswersOfACancelledRun() throws Exception {
        List<String> leis = leis(450); // three requests of 200
        Recording progress = new Recording();
        AtomicInteger requests = new AtomicInteger();
        GleifClient client = new GleifClient(uri -> {
            if (requests.incrementAndGet() == 1) {
                progress.cancelled.set(true); // the user clicks Cancel during the first request
            }
            return noRecords();
        }, stepping());
        LeiCache cache = new LeiCache(client, null, stepping(), Duration.ofDays(3650));

        assertThrows(CancelledException.class, () -> cache.lookup(leis, progress));
        assertEquals(1, requests.get(), "no request after Cancel");
        assertTrue(progress.messages.stream().anyMatch(m -> m.contains("request 1 of 3") && m.contains("of 450")),
                progress.messages.toString());
        assertEquals(200, leis.stream().filter(l -> cache.cached(l).isPresent()).count(),
                "the first request's answers are kept");

        // the next run asks only for the rest
        Recording again = new Recording();
        cache.lookup(leis, again);
        assertEquals(3, requests.get(), "250 LEIs left: two more requests");
        assertTrue(again.messages.stream().anyMatch(m -> m.contains("200 of 450 LEIs known")), again.messages.toString());
        assertEquals(2, again.done);
        assertEquals(2, again.total);
    }

    @Test
    void aCancelledCheckReportsWhatWasAnswered() {
        List<String> leis = leis(250);
        StringBuilder text = new StringBuilder("<r>\n");
        leis.forEach(l -> text.append("<LEI>").append(l).append("</LEI>\n"));
        text.append("</r>");
        Recording progress = new Recording();
        AtomicInteger requests = new AtomicInteger();
        LeiCache cache = new LeiCache(new GleifClient(uri -> {
            if (requests.incrementAndGet() == 1) {
                progress.cancelled.set(true);
            }
            return noRecords();
        }, stepping()), null, stepping(), Duration.ofDays(3650));

        IdentifierCheck.Result result = IdentifierCheck.check(text, cache, null, progress);

        assertTrue(result.cancelled());
        assertEquals(250, result.leis());
        assertEquals(50, result.unchecked(), "the second request never ran");
        assertEquals(200, result.findings().size(), "one 'not registered' finding per answered LEI");
        assertTrue(result.failures().isEmpty(), "a cancellation is no failure");
    }

    @Test
    void gleifAndTheIsinServicesRunSideBySide() {
        List<String> leis = leis(3);
        List<String> isins = isins(3);
        String text = "<r>" + String.join(" ", leis) + " " + String.join(" ", isins) + "</r>";
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger maxInside = new AtomicInteger();
        Runnable slow = () -> {
            maxInside.accumulateAndGet(inside.incrementAndGet(), Math::max);
            try {
                Thread.sleep(400);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            inside.decrementAndGet();
        };
        LeiCache leiCache = new LeiCache(new GleifClient(uri -> {
            slow.run();
            return noRecords();
        }, stepping()), null, stepping(), Duration.ofDays(3650));
        Isin.Cache isinCache = new Isin.Cache(null, stepping(), Duration.ofDays(3650), () -> new IsinLookup(uri -> {
            slow.run();
            return new IsinLookup.Response(200, emptyFirds());
        }, null, stepping(), false, null), () -> false);

        IdentifierCheck.Result result = IdentifierCheck.check(text, leiCache, isinCache, new Recording());

        assertEquals(3, result.leis());
        assertEquals(3, result.isins());
        assertEquals(2, maxInside.get(), "GLEIF and ESMA FIRDS were asked at the same time");
    }

    @Test
    void openFigiWithoutAKeySuggestsOneAndARefusalKeepsWhatWasAnswered() {
        List<String> isins = isins(500); // 5 FIRDS requests, then 50 OpenFIGI requests of 10 without a key
        Recording progress = new Recording();
        AtomicInteger figi = new AtomicInteger();
        IsinLookup lookup = new IsinLookup(uri -> new IsinLookup.Response(200, emptyFirds()), (uri, json, headers) -> {
            if (figi.incrementAndGet() > 2) {
                return new IsinLookup.Response(429, new byte[0]); // the rate limit after two requests
            }
            return new IsinLookup.Response(200, "[{},{},{},{},{},{},{},{},{},{}]".getBytes(StandardCharsets.UTF_8));
        }, stepping(), true, null);
        Isin.Cache cache = new Isin.Cache(null, stepping(), Duration.ofDays(3650), () -> lookup, () -> true);
        String text = "<r>" + String.join(" ", isins) + "</r>";

        IdentifierCheck.Result result = IdentifierCheck.check(text, null, cache, progress);

        assertEquals(List.of(Progress.Hint.OPENFIGI_API_KEY), progress.hints, "50 requests à 2.5 s: a key is suggested");
        assertTrue(progress.messages.stream().anyMatch(m -> m.startsWith("OpenFIGI: request 1 of 50") && m.contains("min")),
                progress.messages.toString());
        assertFalse(result.cancelled());
        assertEquals(1, result.failures().size());
        assertTrue(result.failures().getFirst().message().contains("480 were not checked"), result.failures().getFirst().message());
        assertEquals(480, result.unchecked());
        assertEquals(20, isins.stream().filter(i -> cache.cached(i).isPresent()).count(),
                "the answers before the refusal are kept for the next run");
    }

    @Test
    void partsAreShownTogether() {
        Recording parent = new Recording();
        var parts = new org.fxt.freexmltoolkit.service.progress.ProgressParts(parent, 2);
        parts.part(0).message("GLEIF 1/2");
        parts.part(1).message("FIRDS 3/9");
        parts.part(0).progress(1, 2);
        parts.part(1).progress(3, 9);
        assertEquals("GLEIF 1/2 · FIRDS 3/9", parent.messages.getLast());
        assertEquals(4, parent.done);
        assertEquals(11, parent.total);
        parts.finished(0);
        assertEquals("FIRDS 3/9", parent.messages.getLast());
        parent.cancelled.set(true);
        assertTrue(parts.part(1).isCancelled());
        assertEquals(Collections.emptyList(), parent.hints);
    }

    /** A FIRDS answer that knows none of the ISINs. */
    private static byte[] emptyFirds() {
        return "{\"grouped\":{\"isin\":{\"groups\":[]}},\"facet_counts\":{\"facet_pivot\":{\"isin,status\":[]}}}"
                .getBytes(StandardCharsets.UTF_8);
    }
}
