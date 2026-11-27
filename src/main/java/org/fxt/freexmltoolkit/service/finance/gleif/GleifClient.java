package org.fxt.freexmltoolkit.service.finance.gleif;

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
import java.util.Map;
import java.util.Set;

import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.finance.FinancialIdentifiers;
import org.fxt.freexmltoolkit.service.progress.Progress;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Asks GLEIF's public API ({@code api.gleif.org}, no key) about LEIs: up to {@link #BATCH} LEIs per request, at most one
 * request per {@link #MIN_INTERVAL_MILLIS} (GLEIF allows 60 a minute). Only LEIs with valid check digits are sent.
 * HTTP goes through a {@link Fetcher} - in the application {@code ConnectionService} (proxy, certificates).
 */
public final class GleifClient {

    /** LEIs per request (GLEIF's maximum page size). */
    public static final int BATCH = 200;
    /** Pause between two requests. */
    static final long MIN_INTERVAL_MILLIS = 1_000;
    static final String BASE = "https://api.gleif.org/api/v1/lei-records";

    /** Fetches a URL: HTTP status and body. */
    @FunctionalInterface
    public interface Fetcher {
        Response fetch(URI uri) throws IOException;
    }

    /** An HTTP answer. */
    public record Response(int status, byte[] body) {
    }

    /** GLEIF could not be asked (network) or answered with an error. */
    public static final class GleifException extends IOException {
        private static final long serialVersionUID = 1L;

        public GleifException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final Fetcher fetcher;
    private final Clock clock;
    private long lastRequest;

    public GleifClient(Fetcher fetcher, Clock clock) {
        this.fetcher = fetcher;
        this.clock = clock;
    }

    /** @return a client on the application's {@code ConnectionService} */
    public static GleifClient withConnectionService() {
        return new GleifClient(uri -> {
            var connection = org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.ConnectionService.class);
            var response = connection.fetchBinary(uri);
            return new Response(response.status(), response.body());
        }, Clock.systemUTC());
    }

    /**
     * Looks the LEIs up. LEIs with wrong check digits are skipped (not in the result).
     *
     * @return one record per valid LEI, in the given order; {@link LeiRecord#notFound} for the unknown ones
     * @throws GleifException when a request fails
     */
    public Map<String, LeiRecord> lookup(Collection<String> leis) throws GleifException {
        return lookup(leis, Progress.NONE, part -> { });
    }

    /**
     * Like {@link #lookup(Collection)}, reporting each request and handing over each batch's answers as they arrive.
     *
     * @param partial gets the records of each request (also those already answered when a later one fails)
     * @throws org.fxt.freexmltoolkit.service.progress.CancelledException when the progress is cancelled (between
     *                                                                     requests or while waiting for the next one)
     */
    public Map<String, LeiRecord> lookup(Collection<String> leis, Progress progress,
                                         java.util.function.Consumer<Map<String, LeiRecord>> partial) throws GleifException {
        Set<String> valid = new LinkedHashSet<>();
        for (String lei : leis) {
            String l = lei == null ? "" : lei.strip().toUpperCase(java.util.Locale.ROOT);
            if (FinancialIdentifiers.checkLei(l) == null) {
                valid.add(l);
            }
        }
        Map<String, LeiRecord> result = new LinkedHashMap<>();
        List<String> all = new ArrayList<>(valid);
        int requests = (all.size() + BATCH - 1) / BATCH;
        for (int i = 0; i < all.size(); i += BATCH) {
            List<String> batch = all.subList(i, Math.min(all.size(), i + BATCH));
            int n = i / BATCH + 1;
            progress.checkCancelled();
            progress.message(I18n.t("Progress.gleif-request", n, requests, i + 1, i + batch.size(), all.size()));
            progress.progress(n - 1L, requests);
            Map<String, LeiRecord> found = request(batch, progress);
            Instant now = clock.instant();
            Map<String, LeiRecord> answered = new LinkedHashMap<>();
            for (String lei : batch) {
                answered.put(lei, found.getOrDefault(lei, LeiRecord.notFound(lei, now)));
            }
            result.putAll(answered);
            partial.accept(answered);
            progress.progress(n, requests);
        }
        return result;
    }

    private Map<String, LeiRecord> request(List<String> batch, Progress progress) throws GleifException {
        throttle(progress);
        URI uri = URI.create(BASE + "?" + URLEncoder.encode("filter[lei]", StandardCharsets.UTF_8) + "="
                + String.join(",", batch) + "&" + URLEncoder.encode("page[size]", StandardCharsets.UTF_8) + "=" + BATCH);
        Response response;
        try {
            response = fetcher.fetch(uri);
        } catch (IOException | RuntimeException e) {
            throw new GleifException(org.fxt.freexmltoolkit.service.NetworkErrors.unreachable("GLEIF", e), e);
        }
        if (response.status() == 429) {
            throw new GleifException("GLEIF refused the request: too many requests - try again in a minute.", null);
        }
        if (response.status() != 200) {
            throw new GleifException("GLEIF answered with HTTP " + response.status() + ".", null);
        }
        try {
            return parse(new String(response.body(), StandardCharsets.UTF_8), clock.instant());
        } catch (RuntimeException e) {
            throw new GleifException("GLEIF's answer could not be read: " + e.getMessage(), e);
        }
    }

    private synchronized void throttle(Progress progress) {
        long now = clock.millis();
        long wait = lastRequest + MIN_INTERVAL_MILLIS - now;
        if (wait > 0 && lastRequest > 0) {
            progress.sleep(wait); // ends early (CancelledException) when the job is cancelled
        }
        lastRequest = clock.millis();
    }

    /** Reads a {@code lei-records} answer (JSON:API). */
    static Map<String, LeiRecord> parse(String json, Instant fetchedAt) {
        Map<String, LeiRecord> records = new LinkedHashMap<>();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("data") || !root.get("data").isJsonArray()) {
            throw new IllegalArgumentException("no \"data\" list");
        }
        for (JsonElement item : root.getAsJsonArray("data")) {
            JsonObject a = item.getAsJsonObject().getAsJsonObject("attributes");
            if (a == null) {
                continue;
            }
            String lei = string(a, "lei");
            JsonObject entity = object(a, "entity");
            JsonObject registration = object(a, "registration");
            JsonObject address = object(entity, "legalAddress");
            String renewal = string(registration, "nextRenewalDate");
            records.put(lei, new LeiRecord(lei, true, string(object(entity, "legalName"), "name"), string(address, "country"),
                    string(address, "city"), string(entity, "status"), string(registration, "status"),
                    renewal == null || renewal.length() < 10 ? null : LocalDate.parse(renewal.substring(0, 10)),
                    string(registration, "managingLou"), fetchedAt));
        }
        return records;
    }

    private static JsonObject object(JsonObject parent, String name) {
        return parent != null && parent.has(name) && parent.get(name).isJsonObject() ? parent.getAsJsonObject(name) : null;
    }

    private static String string(JsonObject parent, String name) {
        return parent != null && parent.has(name) && parent.get(name).isJsonPrimitive() ? parent.get(name).getAsString() : null;
    }
}
