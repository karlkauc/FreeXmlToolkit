package org.fxt.freexmltoolkit.service.finance.isin;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import org.fxt.freexmltoolkit.service.AdminPolicy;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;
import org.fxt.freexmltoolkit.service.finance.lookup.LookupCache;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

/**
 * The application's ISIN lookups (ESMA FIRDS, OpenFIGI as fallback): one shared cache
 * ({@code ~/.freeXmlToolkit/isin/cache.json}), the user's settings ({@code isin.*}) and the administrator's switches
 * {@code isin.allowed} / {@code isin.openfigi.allowed}. ISINs only leave the computer on an explicit action, or when
 * the user turned on {@link #KEY_ON_VALIDATION} / {@link #KEY_HOVER}.
 */
public final class Isin {

    public static final String KEY_ON_VALIDATION = "isin.onValidation";
    public static final String KEY_HOVER = "isin.hover";
    /** Settings: ask OpenFIGI about ISINs FIRDS does not know. Default on. */
    public static final String KEY_OPENFIGI = "isin.openfigi";
    /** Settings: OpenFIGI API key (stored encrypted - the {@code .apiKey} suffix). */
    public static final String KEY_OPENFIGI_API_KEY = "isin.openfigi.apiKey";
    /** Environment variable with an OpenFIGI API key (command line, CI). */
    public static final String ENV_OPENFIGI_API_KEY = "FXT_OPENFIGI_APIKEY";
    public static final String KEY_CACHE_HOURS = "isin.cacheHours";
    public static final int DEFAULT_CACHE_HOURS = 24;

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private static volatile Cache shared;
    private static volatile Boolean validationOverride;
    private static volatile Boolean openFigiOverride;

    private Isin() {
    }

    /**
     * ISIN answers kept for a while; looks up through the current settings' {@link IsinLookup}. Answers with and
     * without the OpenFIGI fallback are kept apart (key suffix {@code #firds}), so switching the fallback never shows an
     * answer of the other mode.
     */
    public static final class Cache {
        private final LookupCache<IsinRecord> cache;
        private final java.util.function.Supplier<IsinLookup> lookup;
        private final java.util.function.BooleanSupplier withOpenFigi;

        /** A cache whose mode follows {@link Isin#openFigi()}. */
        public Cache(Path file, Clock clock, Duration maxAge, java.util.function.Supplier<IsinLookup> lookup) {
            this(file, clock, maxAge, lookup, Isin::openFigi);
        }

        public Cache(Path file, Clock clock, Duration maxAge, java.util.function.Supplier<IsinLookup> lookup,
                     java.util.function.BooleanSupplier withOpenFigi) {
            this.lookup = lookup;
            this.withOpenFigi = withOpenFigi;
            this.cache = new LookupCache<>(file, clock, maxAge, new LookupCache.Codec<>() {
                @Override
                public JsonElement write(IsinRecord r) {
                    return GSON.toJsonTree(Stored.of(r));
                }

                @Override
                public IsinRecord read(JsonElement json) {
                    return GSON.fromJson(json, Stored.class).toRecord();
                }

                @Override
                public Instant fetchedAt(IsinRecord r) {
                    // a FIRDS answer cached before the record's document id was read (3.3.0) cannot link the
                    // instrument's page in the register: no age, so it is asked again
                    return r.source() == IsinRecord.Source.FIRDS && r.firdsId() == null ? null : r.fetchedAt();
                }
            });
        }

        private String key(String isin) {
            return withOpenFigi.getAsBoolean() ? isin : isin + "#firds";
        }

        public Optional<IsinRecord> cached(String isin) {
            return cache.cached(key(isin));
        }

        /** @return one record per ISIN with a valid check digit */
        public Map<String, IsinRecord> lookup(Collection<String> isins) throws IsinLookup.LookupException {
            Map<String, String> byKey = new java.util.LinkedHashMap<>();
            isins.forEach(i -> byKey.put(key(i), i));
            Map<String, IsinRecord> found;
            try {
                found = cache.lookup(byKey.keySet(), keys -> {
                    Map<String, IsinRecord> answers = lookup.get().lookup(keys.stream().map(byKey::get).toList());
                    Map<String, IsinRecord> keyed = new java.util.LinkedHashMap<>();
                    answers.forEach((isin, r) -> keyed.put(key(isin), r));
                    return keyed;
                });
            } catch (IsinLookup.LookupException e) {
                throw e;
            } catch (IOException e) {
                throw new IsinLookup.LookupException(e.getMessage(), e);
            }
            Map<String, IsinRecord> result = new java.util.LinkedHashMap<>();
            found.forEach((k, r) -> result.put(byKey.get(k), r));
            return result;
        }

        public void clear() {
            cache.clear();
        }

        public void setMaxAge(Duration maxAge) {
            cache.setMaxAge(maxAge);
        }
    }

    /** @return {@code false} when the administrator switched ISIN lookups off */
    public static boolean allowed() {
        try {
            return AdminPolicy.current().isinLookupsAllowed();
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** @return whether ISINs FIRDS does not know may be asked at OpenFIGI (policy and setting) */
    public static boolean openFigi() {
        try {
            if (!AdminPolicy.current().openFigiAllowed()) {
                return false;
            }
        } catch (RuntimeException e) {
            // no policy: allowed
        }
        Boolean o = openFigiOverride;
        if (o != null) {
            return o;
        }
        String v = get(KEY_OPENFIGI);
        return v == null || v.isBlank() || "true".equalsIgnoreCase(v);
    }

    /** Command line: OpenFIGI on/off for this process ({@code null} = settings). */
    public static void setOpenFigiOverride(Boolean on) {
        openFigiOverride = on;
    }

    /** @return the OpenFIGI API key: the environment's, else the settings' (may be {@code null}) */
    public static String apiKey() {
        String env = System.getenv(ENV_OPENFIGI_API_KEY);
        if (env != null && !env.isBlank()) {
            return env.strip();
        }
        String v = get(KEY_OPENFIGI_API_KEY);
        return v == null || v.isBlank() ? null : v.strip();
    }

    /** @return the shared cache (created on first use) */
    public static Cache cache() {
        Cache c = shared;
        if (c == null) {
            synchronized (Isin.class) {
                c = shared;
                if (c == null) {
                    c = new Cache(Path.of(System.getProperty("user.home"), ".freeXmlToolkit", "isin", "cache.json"),
                            Clock.systemUTC(), Duration.ofHours(cacheHours()),
                            () -> IsinLookup.withConnectionService(openFigi(), apiKey()));
                    shared = c;
                }
            }
        }
        return c;
    }

    /** Test hook: replaces the shared cache ({@code null} = the application's). */
    public static void setCache(Cache cache) {
        shared = cache;
    }

    /** @return whether a validation also checks the ISINs online */
    public static boolean onValidation() {
        if (!allowed()) {
            return false;
        }
        Boolean o = validationOverride;
        return o != null ? o : "true".equalsIgnoreCase(get(KEY_ON_VALIDATION));
    }

    /** Command line: checks ISINs with every validation of this process ({@code null} = settings). */
    public static void setValidationOverride(Boolean on) {
        validationOverride = on;
    }

    /** @return whether a hover may look an ISIN up */
    public static boolean hoverLookups() {
        return allowed() && "true".equalsIgnoreCase(get(KEY_HOVER));
    }

    /** @return the hours an answer is used */
    public static int cacheHours() {
        try {
            String v = get(KEY_CACHE_HOURS);
            int h = v == null || v.isBlank() ? DEFAULT_CACHE_HOURS : Integer.parseInt(v.strip());
            return Math.max(1, Math.min(24 * 30, h));
        } catch (RuntimeException e) {
            return DEFAULT_CACHE_HOURS;
        }
    }

    /** Applies a changed cache duration to the shared cache. */
    public static void cacheHoursChanged() {
        Cache c = shared;
        if (c != null) {
            c.setMaxAge(Duration.ofHours(cacheHours()));
        }
    }

    private static String get(String key) {
        try {
            PropertiesService p = PropertiesServiceImpl.getInstance();
            return p.get(key);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The file form of a record. */
    private record Stored(String isin, String source, String name, String cfi, String issuerLei, String currency,
                          int activeVenues, int totalVenues, String terminatedSince, String figi, String ticker,
                          String securityType, boolean openFigiAsked, String fetchedAt, String firdsId) {

        static Stored of(IsinRecord r) {
            return new Stored(r.isin(), r.source().name(), r.name(), r.cfi(), r.issuerLei(), r.currency(), r.activeVenues(),
                    r.totalVenues(), r.terminatedSince() == null ? null : r.terminatedSince().toString(), r.figi(), r.ticker(),
                    r.securityType(), r.openFigiAsked(), r.fetchedAt().toString(), r.firdsId());
        }

        IsinRecord toRecord() {
            return new IsinRecord(isin, IsinRecord.Source.valueOf(source), name, cfi, issuerLei, currency, activeVenues,
                    totalVenues, terminatedSince == null ? null : LocalDate.parse(terminatedSince), figi, ticker,
                    securityType, openFigiAsked, Instant.parse(fetchedAt), firdsId);
        }
    }
}
