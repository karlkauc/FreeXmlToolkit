package org.fxt.freexmltoolkit.debugger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;

/**
 * Persists breakpoints in the application properties, one entry per stylesheet file: a
 * file's breakpoints live under that file's own key, whether it is the main stylesheet or
 * an included module. The editor's standalone gutter ({@link FileBreakpoints}) reads and
 * writes a single file; a debug session writes all its breakpoints back with
 * {@link #saveByFile}. Older versions stored a whole session under the main stylesheet's
 * key — such aggregates are still read at session start and migrate on the next save.
 */
public final class BreakpointStore {

    private static final Logger logger = LogManager.getLogger(BreakpointStore.class);

    static final String KEY_PREFIX = "debug.breakpoints.";
    private static final String ENTRY_SEPARATOR = "\n";
    private static final String FIELD_SEPARATOR = "\t";

    private BreakpointStore() {
    }

    /** @return the breakpoints stored for {@code stylesheetPath} (never {@code null}). */
    public static Set<Breakpoint> load(String stylesheetPath) {
        PropertiesService service = properties();
        if (service == null) {
            return Set.of();
        }
        return parse(service.get(key(stylesheetPath)));
    }

    /** Stores {@code breakpoints} for {@code stylesheetPath}; an empty set removes the entry. */
    public static void save(String stylesheetPath, Collection<Breakpoint> breakpoints) {
        PropertiesService service = properties();
        if (service == null) {
            return;
        }
        try {
            service.set(key(stylesheetPath), format(breakpoints));
        } catch (RuntimeException e) {
            logger.debug("Could not persist breakpoints: {}", e.getMessage());
        }
    }

    /**
     * Saves every breakpoint under its own file's key. Files in {@code knownFiles} that have
     * no breakpoint left are saved empty (their entry disappears); the main stylesheet's key
     * thereby loses any legacy aggregate of other files' breakpoints.
     */
    public static void saveByFile(Collection<Breakpoint> all, Collection<String> knownFiles) {
        Map<String, Set<Breakpoint>> byFile = groupByFile(all, knownFiles);
        for (Map.Entry<String, Set<Breakpoint>> entry : byFile.entrySet()) {
            save(entry.getKey(), entry.getValue());
        }
    }

    /** @return the breakpoints grouped by (normalised) file, plus an empty group per known file without any. */
    static Map<String, Set<Breakpoint>> groupByFile(Collection<Breakpoint> all, Collection<String> knownFiles) {
        Map<String, Set<Breakpoint>> byFile = new LinkedHashMap<>();
        if (knownFiles != null) {
            for (String file : knownFiles) {
                String normalized = SourceLocations.normalize(file);
                if (!normalized.isEmpty()) {
                    byFile.computeIfAbsent(normalized, k -> new LinkedHashSet<>());
                }
            }
        }
        if (all != null) {
            for (Breakpoint bp : all) {
                if (bp != null && !bp.filePath().isEmpty()) {
                    byFile.computeIfAbsent(bp.filePath(), k -> new LinkedHashSet<>()).add(bp);
                }
            }
        }
        return byFile;
    }

    static String format(Collection<Breakpoint> breakpoints) {
        if (breakpoints == null || breakpoints.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Breakpoint bp : breakpoints) {
            if (sb.length() > 0) {
                sb.append(ENTRY_SEPARATOR);
            }
            sb.append(bp.filePath()).append(FIELD_SEPARATOR)
                    .append(bp.lineNumber()).append(FIELD_SEPARATOR)
                    .append(bp.enabled());
            if (bp.hasCondition()) {
                sb.append(FIELD_SEPARATOR).append(escape(bp.condition()));
            }
        }
        return sb.toString();
    }

    static Set<Breakpoint> parse(String stored) {
        Set<Breakpoint> result = new LinkedHashSet<>();
        if (stored == null || stored.isBlank()) {
            return result;
        }
        for (String entry : stored.split(ENTRY_SEPARATOR)) {
            String[] fields = entry.split(FIELD_SEPARATOR, -1);
            if (fields.length < 2) {
                continue;
            }
            try {
                int line = Integer.parseInt(fields[1].trim());
                boolean enabled = fields.length < 3 || Boolean.parseBoolean(fields[2].trim());
                String condition = fields.length < 4 ? "" : unescape(fields[3]);
                result.add(new Breakpoint(fields[0], line, enabled, condition));
            } catch (RuntimeException e) {
                logger.debug("Skipping malformed breakpoint entry '{}': {}", entry, e.getMessage());
            }
        }
        return result;
    }

    /** Escapes the separators of the entry format (a condition may contain tabs or newlines). */
    static String escape(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\t' -> sb.append("\\t");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    static String unescape(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                char next = text.charAt(++i);
                switch (next) {
                    case 't' -> sb.append('\t');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    default -> sb.append(next);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    static String key(String stylesheetPath) {
        String normalized = SourceLocations.normalize(stylesheetPath);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 12; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return KEY_PREFIX + hex;
        } catch (java.security.NoSuchAlgorithmException e) {
            return KEY_PREFIX + Integer.toHexString(normalized.hashCode());
        }
    }

    private static PropertiesService properties() {
        try {
            return ServiceRegistry.get(PropertiesService.class);
        } catch (RuntimeException e) {
            logger.debug("PropertiesService unavailable, breakpoints are not persisted: {}", e.getMessage());
            return null;
        }
    }
}
