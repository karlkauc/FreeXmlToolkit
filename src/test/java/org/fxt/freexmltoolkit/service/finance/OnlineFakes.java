package org.fxt.freexmltoolkit.service.finance;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.fxt.freexmltoolkit.service.finance.gleif.Gleif;
import org.fxt.freexmltoolkit.service.finance.gleif.GleifClient;
import org.fxt.freexmltoolkit.service.finance.gleif.LeiCache;
import org.fxt.freexmltoolkit.service.finance.isin.Isin;
import org.fxt.freexmltoolkit.service.finance.isin.IsinLookup;

/**
 * Online identifier services without the network, for tests: GLEIF answers with the saved record of GLEIF itself
 * ({@code gleif/lei-records.json}), ESMA FIRDS with Apple and an iShares ETF ({@code isin/firds-select.json}), OpenFIGI
 * knows nothing ("No identifier found." per job). Every request is recorded.
 */
public final class OnlineFakes {

    /** The URIs / bodies the fakes were asked with. */
    public static final List<String> REQUESTS = java.util.Collections.synchronizedList(new ArrayList<>());

    private OnlineFakes() {
    }

    public static byte[] resource(String path) throws IOException {
        try (InputStream in = OnlineFakes.class.getResourceAsStream(path)) {
            return in.readAllBytes();
        }
    }

    /** @return an ISIN lookup on the saved answers */
    public static IsinLookup isinLookup(boolean openFigi) {
        return new IsinLookup(uri -> {
            REQUESTS.add(uri.toString());
            return new IsinLookup.Response(200, resource("/isin/firds-select.json"));
        }, (uri, json, headers) -> {
            REQUESTS.add(json);
            int jobs = com.google.gson.JsonParser.parseString(json).getAsJsonArray().size();
            StringBuilder answer = new StringBuilder("[");
            for (int i = 0; i < jobs; i++) {
                answer.append(i == 0 ? "" : ",").append("{\"warning\":\"No identifier found.\"}");
            }
            return new IsinLookup.Response(200, answer.append(']').toString().getBytes(StandardCharsets.UTF_8));
        }, Clock.systemUTC(), openFigi, null);
    }

    /** Installs the fakes as the application's caches. */
    public static void install() {
        REQUESTS.clear();
        Gleif.setCache(new LeiCache(new GleifClient(uri -> {
            REQUESTS.add(uri.toString());
            return new GleifClient.Response(200, resource("/gleif/lei-records.json"));
        }, Clock.systemUTC()), null, Clock.systemUTC(), Duration.ofHours(1)));
        Isin.setCache(new Isin.Cache(null, Clock.systemUTC(), Duration.ofHours(1), () -> isinLookup(Isin.openFigi())));
    }

    /** Back to the application's caches. */
    public static void reset() {
        Gleif.setCache(null);
        Isin.setCache(null);
    }
}
