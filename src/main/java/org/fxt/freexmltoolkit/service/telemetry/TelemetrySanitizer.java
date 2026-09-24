package org.fxt.freexmltoolkit.service.telemetry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Small, dependency-free helpers that keep every value leaving the app inside the
 * limits of the ingest API (see {@code usage_statistics/ingest/API.md}) and free of
 * user content.
 */
public final class TelemetrySanitizer {

    /** Max length of a meta key. */
    static final int MAX_META_KEY = 40;
    /** Max length of a string meta value. */
    static final int MAX_META_STRING = 200;
    /** Max serialized size of the meta object (API limit is 4 KB; keep a safety margin). */
    static final int MAX_META_BYTES = 3800;

    private TelemetrySanitizer() {
    }

    /**
     * Normalizes an event type to {@code [a-z0-9_]{1,40}}: lower-cases, replaces every
     * other character with {@code _}, collapses repeats and truncates. Blank input
     * yields {@code "unknown"}.
     */
    public static String eventType(String raw) {
        String slug = slug(raw, 40, '_');
        return slug.isEmpty() ? "unknown" : slug;
    }

    /**
     * Turns a UI label / code location into a short identifier ({@code [a-z0-9_.]}),
     * e.g. {@code "Quick Fix"} → {@code "quick_fix"}. Never returns {@code null}.
     */
    public static String where(String raw) {
        return slug(raw, 60, '_', '.');
    }

    /**
     * Restricts an error code to {@code [A-Za-z0-9._$:<>-]} (plus single spaces) and
     * 200 chars — enough for fully-qualified class names, never free text.
     */
    public static String errorCode(String raw) {
        if (raw == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(Math.min(raw.length(), 200));
        for (int i = 0; i < raw.length() && sb.length() < 200; i++) {
            char c = raw.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == '$' || c == ':' || c == '-' || c == '<' || c == '>') {
                sb.append(c);
            } else if (c == ' ' && sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') {
                sb.append(' ');
            }
        }
        String out = sb.toString().strip();
        return out.isEmpty() ? null : out;
    }

    /** Truncates {@code s} to {@code max} chars (null-safe). */
    public static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }

    /**
     * Returns a flat, size-limited copy of {@code meta}: only String/Number/Boolean values,
     * keys normalized like event types, strings truncated, and entries beyond the
     * serialized size limit dropped. Returns {@code null} for null/empty input.
     */
    public static Map<String, Object> meta(Map<String, ?> meta) {
        if (meta == null || meta.isEmpty()) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        int approxBytes = 2;
        for (Map.Entry<String, ?> e : meta.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            String key = slug(e.getKey(), MAX_META_KEY, '_');
            if (key.isEmpty()) {
                continue;
            }
            Object value = e.getValue();
            Object clean;
            if (value instanceof Boolean || value instanceof Number) {
                clean = value;
            } else if (value instanceof CharSequence || value instanceof Enum<?>) {
                clean = truncate(value.toString(), MAX_META_STRING);
            } else {
                continue; // nested objects/collections are not allowed (flat meta only)
            }
            int size = key.length() + String.valueOf(clean).getBytes(StandardCharsets.UTF_8).length + 8;
            if (approxBytes + size > MAX_META_BYTES) {
                break;
            }
            approxBytes += size;
            out.put(key, clean);
        }
        return out.isEmpty() ? null : out;
    }

    /** First 16 hex chars of the SHA-256 of {@code text}. */
    public static String hash16(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory on every JRE; fall back to a stable non-crypto hash.
            return String.format("%016x", (long) text.hashCode() & 0xffffffffL);
        }
    }

    private static String slug(String raw, int max, char replacement, char... alsoAllowed) {
        if (raw == null) {
            return "";
        }
        String lower = raw.strip().toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(Math.min(lower.length(), max));
        for (int i = 0; i < lower.length() && sb.length() < max; i++) {
            char c = lower.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_';
            for (char a : alsoAllowed) {
                ok |= c == a;
            }
            if (ok) {
                sb.append(c);
            } else if (sb.length() > 0 && sb.charAt(sb.length() - 1) != replacement) {
                sb.append(replacement);
            }
        }
        // strip trailing replacement chars
        int end = sb.length();
        while (end > 0 && sb.charAt(end - 1) == replacement) {
            end--;
        }
        return sb.substring(0, end);
    }
}
