package org.fxt.freexmltoolkit.service.finance.gleif;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;

import org.fxt.freexmltoolkit.service.AdminPolicy;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;

/**
 * The application's access to GLEIF: one shared {@link LeiCache} (file {@code ~/.freeXmlToolkit/gleif/cache.json}),
 * the user's settings ({@code gleif.*}) and the administrator's switch {@code gleif.allowed}. LEIs only leave the
 * computer on an explicit action, or when the user turned on {@link #KEY_ON_VALIDATION} / {@link #KEY_HOVER}.
 */
public final class Gleif {

    /** Settings: check the document's LEIs at GLEIF with every validation (F8). Default off. */
    public static final String KEY_ON_VALIDATION = "gleif.onValidation";
    /** Settings: look a hovered LEI up at GLEIF. Default off (without it the hover shows cached answers only). */
    public static final String KEY_HOVER = "gleif.hover";
    /** Settings: hours an answer is used. Default 24. */
    public static final String KEY_CACHE_HOURS = "gleif.cacheHours";
    public static final int DEFAULT_CACHE_HOURS = 24;

    private static volatile LeiCache shared;
    /** Set by the command line ({@code validate --gleif}); the GUI reads the setting instead. */
    private static volatile Boolean validationOverride;

    private Gleif() {
    }

    /** @return {@code false} when the administrator switched GLEIF lookups off */
    public static boolean allowed() {
        try {
            return AdminPolicy.current().gleifAllowed();
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** @return the shared cache (created on first use) */
    public static LeiCache cache() {
        LeiCache c = shared;
        if (c == null) {
            synchronized (Gleif.class) {
                c = shared;
                if (c == null) {
                    c = new LeiCache(GleifClient.withConnectionService(),
                            Path.of(System.getProperty("user.home"), ".freeXmlToolkit", "gleif", "cache.json"),
                            Clock.systemUTC(), Duration.ofHours(cacheHours()));
                    shared = c;
                }
            }
        }
        return c;
    }

    /** Test hook: replaces the shared cache ({@code null} = the application's). */
    public static void setCache(LeiCache cache) {
        shared = cache;
    }

    /** @return whether a validation also checks the LEIs at GLEIF */
    public static boolean onValidation() {
        if (!allowed()) {
            return false;
        }
        Boolean o = validationOverride;
        return o != null ? o : flag(KEY_ON_VALIDATION);
    }

    /** Command line: checks LEIs with every validation of this process ({@code null} = follow the settings). */
    public static void setValidationOverride(Boolean on) {
        validationOverride = on;
    }

    /** @return whether a hover may ask GLEIF */
    public static boolean hoverLookups() {
        return allowed() && flag(KEY_HOVER);
    }

    /** @return the hours an answer is used */
    public static int cacheHours() {
        try {
            String v = properties().get(KEY_CACHE_HOURS);
            int h = v == null || v.isBlank() ? DEFAULT_CACHE_HOURS : Integer.parseInt(v.strip());
            return Math.max(1, Math.min(24 * 30, h));
        } catch (RuntimeException e) {
            return DEFAULT_CACHE_HOURS;
        }
    }

    /** Applies a changed cache duration to the shared cache. */
    public static void cacheHoursChanged() {
        LeiCache c = shared;
        if (c != null) {
            c.setMaxAge(Duration.ofHours(cacheHours()));
        }
    }

    private static boolean flag(String key) {
        try {
            return "true".equalsIgnoreCase(properties().get(key));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static PropertiesService properties() {
        return PropertiesServiceImpl.getInstance();
    }
}
