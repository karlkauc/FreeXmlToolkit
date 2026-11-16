package org.fxt.freexmltoolkit.service.finance.gleif;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * GLEIF answers kept for a while (in memory and in a file), so a validation or a hover asks GLEIF only about LEIs it has
 * not seen recently. Thread-safe.
 */
public final class LeiCache {

    private static final Logger logger = LogManager.getLogger(LeiCache.class);
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final GleifClient client;
    private final Path file;
    private final Clock clock;
    private volatile Duration maxAge;
    private final Map<String, LeiRecord> records = new ConcurrentHashMap<>();
    private boolean loaded;

    /**
     * @param file   where the answers are kept between runs, {@code null} for memory only
     * @param maxAge how long an answer is used
     */
    public LeiCache(GleifClient client, Path file, Clock clock, Duration maxAge) {
        this.client = client;
        this.file = file;
        this.clock = clock;
        this.maxAge = maxAge;
    }

    public void setMaxAge(Duration maxAge) {
        this.maxAge = maxAge;
    }

    /** @return the known answer for an LEI, if it is recent enough (no request) */
    public Optional<LeiRecord> cached(String lei) {
        load();
        LeiRecord r = records.get(lei);
        return r != null && fresh(r) ? Optional.of(r) : Optional.empty();
    }

    /**
     * Looks the LEIs up, asking GLEIF only about those without a recent answer.
     *
     * @return one record per LEI with valid check digits
     */
    public Map<String, LeiRecord> lookup(Collection<String> leis) throws GleifClient.GleifException {
        load();
        List<String> missing = new ArrayList<>();
        for (String lei : leis) {
            if (cached(lei).isEmpty()) {
                missing.add(lei);
            }
        }
        if (!missing.isEmpty()) {
            records.putAll(client.lookup(missing));
            save();
        }
        Map<String, LeiRecord> result = new LinkedHashMap<>();
        for (String lei : leis) {
            LeiRecord r = records.get(lei);
            if (r != null) {
                result.put(lei, r);
            }
        }
        return result;
    }

    /** Forgets every answer (also in the file). */
    public void clear() {
        records.clear();
        if (file != null) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                logger.debug("GLEIF cache not deleted: {}", e.getMessage());
            }
        }
    }

    /** @return the number of answers kept */
    public int size() {
        load();
        return records.size();
    }

    private boolean fresh(LeiRecord r) {
        return r.fetchedAt() != null && r.fetchedAt().plus(maxAge).isAfter(clock.instant());
    }

    private synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : root.getAsJsonObject("records").entrySet()) {
                Stored s = GSON.fromJson(e.getValue(), Stored.class);
                records.putIfAbsent(e.getKey(), s.toRecord());
            }
        } catch (IOException | RuntimeException e) {
            logger.debug("GLEIF cache not read: {}", e.getMessage());
        }
    }

    private synchronized void save() {
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            JsonObject recs = new JsonObject();
            records.forEach((lei, r) -> {
                if (fresh(r)) {
                    recs.add(lei, GSON.toJsonTree(Stored.of(r)));
                }
            });
            JsonObject root = new JsonObject();
            root.add("records", recs);
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            logger.debug("GLEIF cache not written: {}", e.getMessage());
        }
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
