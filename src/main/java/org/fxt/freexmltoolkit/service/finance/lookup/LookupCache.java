package org.fxt.freexmltoolkit.service.finance.lookup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Answers of an online lookup (GLEIF, ESMA FIRDS, OpenFIGI …) kept for a while in memory and in a JSON file, so a
 * validation or a hover asks the service only about keys it has not seen recently. Thread-safe.
 *
 * @param <R> the answer type
 */
public final class LookupCache<R> {

    private static final Logger logger = LogManager.getLogger(LookupCache.class);

    /** Asks the service about the keys without a recent answer. */
    @FunctionalInterface
    public interface Fetcher<R> {
        /** @return answers for (some of) the keys; keys without answer are asked again next time */
        Map<String, R> fetch(List<String> keys) throws IOException;
    }

    /** Turns an answer into JSON and back (the file form). */
    public interface Codec<R> {
        JsonElement write(R record);

        R read(JsonElement json);

        /** @return when the answer arrived */
        Instant fetchedAt(R record);
    }

    private final Path file;
    private final Clock clock;
    private final Codec<R> codec;
    private volatile Duration maxAge;
    private final Map<String, R> records = new ConcurrentHashMap<>();
    private boolean loaded;

    /**
     * @param file   where the answers are kept between runs, {@code null} for memory only
     * @param maxAge how long an answer is used
     */
    public LookupCache(Path file, Clock clock, Duration maxAge, Codec<R> codec) {
        this.file = file;
        this.clock = clock;
        this.maxAge = maxAge;
        this.codec = codec;
    }

    public void setMaxAge(Duration maxAge) {
        this.maxAge = maxAge;
    }

    /** @return the known answer for a key, if it is recent enough (no request) */
    public Optional<R> cached(String key) {
        load();
        R r = records.get(key);
        return r != null && fresh(r) ? Optional.of(r) : Optional.empty();
    }

    /**
     * Looks the keys up, asking the service only about those without a recent answer.
     *
     * @return the answers known for the keys, in the given order
     */
    public Map<String, R> lookup(Collection<String> keys, Fetcher<R> fetcher) throws IOException {
        load();
        List<String> missing = new ArrayList<>();
        for (String key : keys) {
            if (cached(key).isEmpty()) {
                missing.add(key);
            }
        }
        if (!missing.isEmpty()) {
            records.putAll(fetcher.fetch(missing));
            save();
        }
        Map<String, R> result = new LinkedHashMap<>();
        for (String key : keys) {
            R r = records.get(key);
            if (r != null) {
                result.put(key, r);
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
                logger.debug("Lookup cache not deleted: {}", e.getMessage());
            }
        }
    }

    /** @return the number of answers kept */
    public int size() {
        load();
        return records.size();
    }

    private boolean fresh(R r) {
        Instant at = codec.fetchedAt(r);
        return at != null && at.plus(maxAge).isAfter(clock.instant());
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
                records.putIfAbsent(e.getKey(), codec.read(e.getValue()));
            }
        } catch (IOException | RuntimeException e) {
            logger.debug("Lookup cache not read: {}", e.getMessage());
        }
    }

    private synchronized void save() {
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            JsonObject recs = new JsonObject();
            records.forEach((key, r) -> {
                if (fresh(r)) {
                    recs.add(key, codec.write(r));
                }
            });
            JsonObject root = new JsonObject();
            root.add("records", recs);
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, new GsonBuilder().disableHtmlEscaping().create().toJson(root), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            logger.debug("Lookup cache not written: {}", e.getMessage());
        }
    }
}
