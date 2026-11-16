package org.fxt.freexmltoolkit.service.finance.isin;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.fxt.freexmltoolkit.service.finance.FinancialIdentifiers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Looks ISINs up: first at ESMA FIRDS (EU reference data, {@code registers.esma.europa.eu}, no key), the ISINs FIRDS does
 * not know at OpenFIGI ({@code api.openfigi.com}, optional API key) when that is allowed. HTTP through injected
 * functions - in the application {@code ConnectionService}.
 */
public final class IsinLookup {

    /** ISINs per FIRDS request. */
    static final int FIRDS_BATCH = 100;
    /** Jobs per OpenFIGI request without / with an API key. */
    static final int OPENFIGI_BATCH = 10;
    static final int OPENFIGI_BATCH_WITH_KEY = 100;
    static final String FIRDS = "https://registers.esma.europa.eu/solr/esma_registers_firds/select";
    static final String OPENFIGI = "https://api.openfigi.com/v3/mapping";
    private static final Set<String> ENDED = Set.of("TERM", "CANC");

    /** GET: status and body. */
    @FunctionalInterface
    public interface Get {
        Response get(URI uri) throws IOException;
    }

    /** POST of JSON with extra headers: status and body. */
    @FunctionalInterface
    public interface PostJson {
        Response post(URI uri, String json, Map<String, String> headers) throws IOException;
    }

    /** An HTTP answer. */
    public record Response(int status, byte[] body) {
    }

    /** A service could not be asked or answered with an error. */
    public static final class LookupException extends IOException {
        private static final long serialVersionUID = 1L;

        public LookupException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final Get get;
    private final PostJson post;
    private final Clock clock;
    private final boolean openFigi;
    private final String apiKey;
    private long lastFirds;
    private long lastFigi;

    /**
     * @param openFigi whether ISINs FIRDS does not know are asked at OpenFIGI
     * @param apiKey   the OpenFIGI API key, {@code null} or blank for none
     */
    public IsinLookup(Get get, PostJson post, Clock clock, boolean openFigi, String apiKey) {
        this.get = get;
        this.post = post;
        this.clock = clock;
        this.openFigi = openFigi;
        this.apiKey = apiKey == null || apiKey.isBlank() ? null : apiKey.strip();
    }

    /** @return a lookup on the application's {@code ConnectionService} */
    public static IsinLookup withConnectionService(boolean openFigi, String apiKey) {
        return new IsinLookup(uri -> {
            var connection = org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.ConnectionService.class);
            var r = connection.fetchBinary(uri);
            return new Response(r.status(), r.body());
        }, (uri, json, headers) -> {
            var connection = org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.ConnectionService.class);
            var r = connection.postJson(uri, json, java.time.Duration.ofSeconds(30), headers);
            return new Response(r.status(), r.body().getBytes(StandardCharsets.UTF_8));
        }, Clock.systemUTC(), openFigi, apiKey);
    }

    /**
     * Looks the ISINs up; ISINs with wrong check digits are skipped.
     *
     * @return one record per valid ISIN, in the given order
     */
    public synchronized Map<String, IsinRecord> lookup(Collection<String> isins) throws LookupException {
        Set<String> valid = new LinkedHashSet<>();
        for (String isin : isins) {
            String i = isin == null ? "" : isin.strip().toUpperCase(Locale.ROOT);
            if (FinancialIdentifiers.checkIsin(i) == null) {
                valid.add(i);
            }
        }
        List<String> all = new ArrayList<>(valid);
        Map<String, IsinRecord> found = new LinkedHashMap<>();
        for (int i = 0; i < all.size(); i += FIRDS_BATCH) {
            found.putAll(firds(all.subList(i, Math.min(all.size(), i + FIRDS_BATCH))));
        }
        List<String> missing = all.stream().filter(i -> !found.containsKey(i)).toList();
        if (openFigi && !missing.isEmpty()) {
            int batch = apiKey == null ? OPENFIGI_BATCH : OPENFIGI_BATCH_WITH_KEY;
            for (int i = 0; i < missing.size(); i += batch) {
                found.putAll(openFigi(missing.subList(i, Math.min(missing.size(), i + batch))));
            }
        }
        Map<String, IsinRecord> result = new LinkedHashMap<>();
        Instant now = clock.instant();
        for (String isin : all) {
            result.put(isin, found.getOrDefault(isin, IsinRecord.notFound(isin, openFigi, now)));
        }
        return result;
    }

    // ------------------------------------------------------------------ FIRDS

    private Map<String, IsinRecord> firds(List<String> batch) throws LookupException {
        lastFirds = throttle(lastFirds, 1_000);
        String query = "isin:(" + String.join(" OR ", batch) + ")";
        URI uri = URI.create(FIRDS + "?q=" + enc(query) + "&fq=" + enc("type_s:parent") + "&wt=json&rows=" + batch.size()
                + "&group=true&group.field=isin&group.limit=1&group.sort=" + enc("status desc")
                + "&facet=true&facet.pivot=isin,status&facet.limit=-1&fl="
                + enc("isin,gnr_full_name,gnr_short_name,gnr_cfi_code,lei,gnr_notional_curr_code,status,mrkt_trdng_trmination_date"));
        Response r;
        try {
            r = get.get(uri);
        } catch (IOException | RuntimeException e) {
            throw new LookupException("ESMA FIRDS could not be reached: " + message(e), e);
        }
        if (r.status() != 200) {
            throw new LookupException("ESMA FIRDS answered with HTTP " + r.status() + ".", null);
        }
        try {
            return parseFirds(new String(r.body(), StandardCharsets.UTF_8), clock.instant());
        } catch (RuntimeException e) {
            throw new LookupException("ESMA FIRDS's answer could not be read: " + e.getMessage(), e);
        }
    }

    /** Reads a grouped FIRDS answer with the isin/status facet pivot. */
    static Map<String, IsinRecord> parseFirds(String json, Instant fetchedAt) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        Map<String, int[]> venues = new LinkedHashMap<>(); // lower-case isin -> {active, total}
        JsonObject pivots = root.has("facet_counts") ? root.getAsJsonObject("facet_counts").getAsJsonObject("facet_pivot") : null;
        if (pivots != null && pivots.has("isin,status")) {
            for (JsonElement p : pivots.getAsJsonArray("isin,status")) {
                JsonObject o = p.getAsJsonObject();
                int active = 0;
                int total = o.get("count").getAsInt();
                if (o.has("pivot")) {
                    for (JsonElement s : o.getAsJsonArray("pivot")) {
                        JsonObject so = s.getAsJsonObject();
                        if (!ENDED.contains(so.get("value").getAsString().toUpperCase(Locale.ROOT))) {
                            active += so.get("count").getAsInt();
                        }
                    }
                }
                venues.put(o.get("value").getAsString().toLowerCase(Locale.ROOT), new int[] {active, total});
            }
        }
        Map<String, IsinRecord> records = new LinkedHashMap<>();
        JsonArray groups = root.getAsJsonObject("grouped").getAsJsonObject("isin").getAsJsonArray("groups");
        for (JsonElement g : groups) {
            JsonArray docs = g.getAsJsonObject().getAsJsonObject("doclist").getAsJsonArray("docs");
            if (docs.isEmpty()) {
                continue;
            }
            JsonObject d = docs.get(0).getAsJsonObject();
            String isin = string(d, "isin").toUpperCase(Locale.ROOT);
            int[] v = venues.getOrDefault(isin.toLowerCase(Locale.ROOT), new int[] {0, 0});
            String end = string(d, "mrkt_trdng_trmination_date");
            LocalDate ended = v[0] == 0 && end != null && end.length() >= 10 ? LocalDate.parse(end.substring(0, 10)) : null;
            String name = string(d, "gnr_full_name");
            records.put(isin, new IsinRecord(isin, IsinRecord.Source.FIRDS, name != null ? name : string(d, "gnr_short_name"),
                    string(d, "gnr_cfi_code"), string(d, "lei"), string(d, "gnr_notional_curr_code"), v[0], v[1], ended,
                    null, null, null, false, fetchedAt));
        }
        return records;
    }

    // ------------------------------------------------------------------ OpenFIGI

    private Map<String, IsinRecord> openFigi(List<String> batch) throws LookupException {
        // 25 requests a minute without a key, 25 in 6 seconds with one
        lastFigi = throttle(lastFigi, apiKey == null ? 2_500 : 250);
        JsonArray jobs = new JsonArray();
        for (String isin : batch) {
            JsonObject job = new JsonObject();
            job.addProperty("idType", "ID_ISIN");
            job.addProperty("idValue", isin);
            jobs.add(job);
        }
        Map<String, String> headers = apiKey == null ? Map.of() : Map.of("X-OPENFIGI-APIKEY", apiKey);
        Response r;
        try {
            r = post.post(URI.create(OPENFIGI), jobs.toString(), headers);
        } catch (IOException | RuntimeException e) {
            throw new LookupException("OpenFIGI could not be reached: " + message(e), e);
        }
        if (r.status() == 429) {
            throw new LookupException("OpenFIGI refused the request: too many requests - try again in a minute"
                    + (apiKey == null ? " or set an API key." : "."), null);
        }
        if (r.status() == 401) {
            throw new LookupException("OpenFIGI refused the API key.", null);
        }
        if (r.status() != 200) {
            throw new LookupException("OpenFIGI answered with HTTP " + r.status() + ".", null);
        }
        try {
            return parseOpenFigi(batch, new String(r.body(), StandardCharsets.UTF_8), clock.instant());
        } catch (RuntimeException e) {
            throw new LookupException("OpenFIGI's answer could not be read: " + e.getMessage(), e);
        }
    }

    /** Reads an OpenFIGI mapping answer: one entry per job, in job order. */
    static Map<String, IsinRecord> parseOpenFigi(List<String> batch, String json, Instant fetchedAt) {
        JsonArray answers = JsonParser.parseString(json).getAsJsonArray();
        Map<String, IsinRecord> records = new LinkedHashMap<>();
        for (int i = 0; i < Math.min(batch.size(), answers.size()); i++) {
            JsonObject a = answers.get(i).getAsJsonObject();
            if (!a.has("data") || a.getAsJsonArray("data").isEmpty()) {
                continue;
            }
            JsonObject d = a.getAsJsonArray("data").get(0).getAsJsonObject();
            String isin = batch.get(i);
            records.put(isin, new IsinRecord(isin, IsinRecord.Source.OPENFIGI, string(d, "name"), null, null, null, 0, 0, null,
                    string(d, "figi"), string(d, "ticker"), string(d, "securityType"), true, fetchedAt));
        }
        return records;
    }

    // ------------------------------------------------------------------ helpers

    private long throttle(long last, long interval) {
        long now = clock.millis();
        long wait = last + interval - now;
        if (last > 0 && wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return clock.millis();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String message(Exception e) {
        return e.getMessage() == null ? e.toString() : e.getMessage();
    }

    private static String string(JsonObject o, String name) {
        return o != null && o.has(name) && o.get(name).isJsonPrimitive() ? o.get(name).getAsString() : null;
    }
}
