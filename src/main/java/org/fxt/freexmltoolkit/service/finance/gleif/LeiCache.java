package org.fxt.freexmltoolkit.service.finance.gleif;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import org.fxt.freexmltoolkit.service.finance.lookup.LookupCache;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

/**
 * GLEIF answers kept for a while (in memory and in a file, {@link LookupCache}), so a validation or a hover asks GLEIF
 * only about LEIs it has not seen recently. Thread-safe.
 */
public final class LeiCache {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final GleifClient client;
    private final LookupCache<LeiRecord> cache;

    /**
     * @param file   where the answers are kept between runs, {@code null} for memory only
     * @param maxAge how long an answer is used
     */
    public LeiCache(GleifClient client, Path file, Clock clock, Duration maxAge) {
        this.client = client;
        this.cache = new LookupCache<>(file, clock, maxAge, new LookupCache.Codec<>() {
            @Override
            public JsonElement write(LeiRecord r) {
                return GSON.toJsonTree(Stored.of(r));
            }

            @Override
            public LeiRecord read(JsonElement json) {
                return GSON.fromJson(json, Stored.class).toRecord();
            }

            @Override
            public Instant fetchedAt(LeiRecord r) {
                return r.fetchedAt();
            }
        });
    }

    public void setMaxAge(Duration maxAge) {
        cache.setMaxAge(maxAge);
    }

    /** @return the known answer for an LEI, if it is recent enough (no request) */
    public Optional<LeiRecord> cached(String lei) {
        return cache.cached(lei);
    }

    /**
     * Looks the LEIs up, asking GLEIF only about those without a recent answer.
     *
     * @return one record per LEI with valid check digits
     */
    public Map<String, LeiRecord> lookup(Collection<String> leis) throws GleifClient.GleifException {
        return lookup(leis, org.fxt.freexmltoolkit.service.progress.Progress.NONE);
    }

    /**
     * Like {@link #lookup(Collection)}, reporting the requests; the answers of each request are kept at once, so a
     * failed or cancelled run loses nothing it already got.
     */
    public Map<String, LeiRecord> lookup(Collection<String> leis, org.fxt.freexmltoolkit.service.progress.Progress progress)
            throws GleifClient.GleifException {
        try {
            int known = leis.size() - cache.missing(leis).size();
            if (known > 0 && !leis.isEmpty()) {
                progress.message(org.fxt.freexmltoolkit.i18n.I18n.t("Progress.gleif-cached", known, leis.size()));
            }
            return cache.lookup(leis, (missing, partial) -> client.lookup(missing, progress, partial));
        } catch (GleifClient.GleifException e) {
            throw e;
        } catch (IOException e) {
            throw new GleifClient.GleifException(e.getMessage(), e);
        }
    }

    /** Forgets every answer (also in the file). */
    public void clear() {
        cache.clear();
    }

    /** @return the number of answers kept */
    public int size() {
        return cache.size();
    }

    /** The file form of a record (dates as text). */
    private record Stored(String lei, boolean found, String legalName, String country, String city, String entityStatus,
                          String registrationStatus, String nextRenewal, String managingLou, String fetchedAt) {

        static Stored of(LeiRecord r) {
            return new Stored(r.lei(), r.found(), r.legalName(), r.country(), r.city(), r.entityStatus(), r.registrationStatus(),
                    r.nextRenewal() == null ? null : r.nextRenewal().toString(), r.managingLou(), r.fetchedAt().toString());
        }

        LeiRecord toRecord() {
            return new LeiRecord(lei, found, legalName, country, city, entityStatus, registrationStatus,
                    nextRenewal == null ? null : LocalDate.parse(nextRenewal), managingLou, Instant.parse(fetchedAt));
        }
    }
}
