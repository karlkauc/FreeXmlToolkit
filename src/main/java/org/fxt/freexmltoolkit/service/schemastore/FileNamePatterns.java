package org.fxt.freexmltoolkit.service.schemastore;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * File name patterns that choose a JSON Schema for a file, as the SchemaStore catalog and the
 * YAML / JSON language servers write them ({@code fileMatch}):
 * <ul>
 *   <li>a pattern without {@code /} is matched against the file name ({@code docker-compose*.yml});</li>
 *   <li>a pattern with {@code /} is matched against the end of the path, whole folders only
 *       ({@code .github/workflows/*.yml}); a leading {@code **}{@code /} means the same;</li>
 *   <li>{@code *} matches within a name, {@code **} across folders, {@code ?} one character,
 *       {@code {a,b}} alternatives, {@code [..]} a character class;</li>
 *   <li>a pattern starting with {@code !} excludes what it matches.</li>
 * </ul>
 * Names are compared ignoring case. UI-free, thread-safe.
 */
public final class FileNamePatterns {

    private static final Map<String, Pattern> COMPILED = new ConcurrentHashMap<>();

    private FileNamePatterns() {
    }

    /**
     * @param patterns the patterns (exclusions start with {@code !})
     * @param file     the file
     * @return whether a pattern matches the file and no exclusion does
     */
    public static boolean matches(List<String> patterns, Path file) {
        return bestMatch(patterns, file) != null;
    }

    /**
     * @param patterns the patterns (exclusions start with {@code !})
     * @param file     the file
     * @return the most specific pattern that matches (see {@link #specificity}), or {@code null}
     *         when none does or an exclusion matches
     */
    public static String bestMatch(List<String> patterns, Path file) {
        if (patterns == null || patterns.isEmpty() || file == null || file.getFileName() == null) {
            return null;
        }
        String path = file.toAbsolutePath().normalize().toString().replace('\\', '/');
        String name = file.getFileName().toString();
        String best = null;
        for (String pattern : patterns) {
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            String p = pattern.strip();
            boolean exclude = p.startsWith("!");
            if (exclude) {
                p = p.substring(1);
            }
            boolean hit;
            try {
                hit = matchesOne(p, path, name);
            } catch (PatternSyntaxException e) {
                hit = false;
            }
            if (hit && exclude) {
                return null;
            }
            if (hit && (best == null || specificity(p) > specificity(best))) {
                best = p;
            }
        }
        return best;
    }

    private static boolean matchesOne(String pattern, String path, String name) {
        String p = pattern.replace('\\', '/');
        while (p.startsWith("./")) {
            p = p.substring(2);
        }
        if (!p.contains("/")) {
            return compile(p).matcher(name).matches();
        }
        if (p.startsWith("/")) {
            p = p.substring(1);
        }
        if (!p.startsWith("**/")) {
            p = "**/" + p;
        }
        return compile(p).matcher(path.startsWith("/") ? path : "/" + path).matches();
    }

    /**
     * How specific a pattern is: its number of literal characters, so {@code docker-compose.yml}
     * beats {@code *.yml}.
     *
     * @param pattern a pattern
     * @return the number of characters that are no wildcards
     */
    public static int specificity(String pattern) {
        int count = 0;
        for (char c : pattern.toCharArray()) {
            if (c != '*' && c != '?' && c != '!' && c != '{' && c != '}' && c != '[' && c != ']' && c != ',') {
                count++;
            }
        }
        return count;
    }

    /**
     * @param pattern a pattern
     * @return why the pattern cannot be used, or {@code null} when it is fine
     */
    public static String problemOf(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return "The pattern is empty.";
        }
        String p = pattern.strip();
        if (p.startsWith("!")) {
            p = p.substring(1);
        }
        try {
            compile(p.contains("/") ? "**/" + p : p);
            return null;
        } catch (PatternSyntaxException e) {
            return "Invalid file pattern: " + pattern;
        }
    }

    private static Pattern compile(String glob) {
        return COMPILED.computeIfAbsent(glob, FileNamePatterns::toRegex);
    }

    /** Glob → regular expression (whole string, case-insensitive). */
    static Pattern toRegex(String glob) {
        StringBuilder out = new StringBuilder();
        int braces = 0;
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> {
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                        i++;
                        if (i + 1 < glob.length() && glob.charAt(i + 1) == '/') {
                            i++;
                            out.append("(?:.*/)?"); // **/ : any folders, also none
                        } else {
                            out.append(".*");
                        }
                    } else {
                        out.append("[^/]*");
                    }
                }
                case '?' -> out.append("[^/]");
                case '{' -> {
                    braces++;
                    out.append("(?:");
                }
                case '}' -> {
                    if (braces > 0) {
                        braces--;
                        out.append(')');
                    } else {
                        out.append("\\}");
                    }
                }
                case ',' -> out.append(braces > 0 ? "|" : ",");
                case '[' -> {
                    int close = glob.indexOf(']', i + 1);
                    if (close < 0) {
                        out.append("\\[");
                    } else {
                        String set = glob.substring(i + 1, close);
                        if (set.startsWith("!")) {
                            set = "^" + set.substring(1);
                        }
                        out.append('[').append(set.replace("\\", "\\\\")).append(']');
                        i = close;
                    }
                }
                default -> {
                    if ("\\.^$+()|".indexOf(c) >= 0) {
                        out.append('\\');
                    }
                    out.append(c);
                }
            }
        }
        if (braces > 0) {
            throw new PatternSyntaxException("Unclosed {", glob, glob.length());
        }
        return Pattern.compile(out.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /** @return the pattern list normalized: trimmed, without blanks */
    public static List<String> normalize(List<String> patterns) {
        if (patterns == null) {
            return List.of();
        }
        return patterns.stream().filter(p -> p != null && !p.isBlank()).map(String::strip).toList();
    }

    /**
     * Splits a comma-, semicolon- or line-separated list typed by the user (commas inside
     * {@code {..}} stay).
     *
     * @param text the list
     * @return the patterns
     */
    public static List<String> parseList(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> out = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        int braces = 0;
        for (char c : text.toCharArray()) {
            if (c == '{') {
                braces++;
            } else if (c == '}' && braces > 0) {
                braces--;
            }
            if ((c == ',' && braces == 0) || c == ';' || c == '\n' || c == '\r') {
                if (!current.toString().isBlank()) {
                    out.add(current.toString().strip());
                }
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (!current.toString().isBlank()) {
            out.add(current.toString().strip());
        }
        return List.copyOf(out);
    }
}
