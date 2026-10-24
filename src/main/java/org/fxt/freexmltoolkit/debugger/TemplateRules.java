package org.fxt.freexmltoolkit.debugger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The template rules of a stylesheet and its modules, for "Explain Match": which rules could
 * process a node, with the priority and import precedence that decide between them (XSLT 3.0
 * §6.4 – §6.5). Read from the stylesheet texts; the match itself is tested by evaluating
 * {@link #testExpression} against the node.
 */
public final class TemplateRules {

    /**
     * A template rule.
     *
     * @param match      the match pattern
     * @param modes      the mode names it applies to ({@code #default} when none is given, {@code #all})
     * @param priority   the explicit priority, or {@code null}
     * @param path       the module's normalized path
     * @param line       the line Saxon reports for the rule (where its start tag ends)
     * @param precedence import precedence (higher wins; the main stylesheet is highest)
     * @param order      position in declaration order across all modules
     */
    public record Rule(String match, Set<String> modes, Double priority, String path, int line,
                       int precedence, int order) { }

    /**
     * One alternative of a rule's pattern (a union pattern is split; each part has its own
     * default priority) that can be tested against a node.
     */
    public record Candidate(Rule rule, String alternative, double priority) {

        /** @return e.g. {@code item (priority 0, precedence 2, a.xsl:12)} */
        public String describe() {
            return "match=\"" + alternative + "\"  ·  priority " + formatPriority(priority)
                    + (rule.priority() == null ? " (default)" : "") + "  ·  "
                    + SourceLocations.fileName(rule.path()) + ":" + rule.line();
        }
    }

    private static final Pattern TEMPLATE = Pattern.compile("<([\\w.-]+):template\\b");
    private static final Pattern DIRECTIVE = Pattern.compile("<[\\w.-]+:(import|include)\\b([^>]*)>");
    private static final Pattern ATTRIBUTE = Pattern.compile("([\\w.:-]+)\\s*=\\s*(\"[^\"]*\"|'[^']*')");

    private TemplateRules() {
    }

    /**
     * Collects the template rules (templates with a {@code match}) of {@code mainPath} and the
     * modules it includes / imports, with import precedence after the import tree (post-order:
     * an importing module beats what it imports; a later import beats an earlier one).
     */
    public static List<Rule> collect(String mainPath, Function<String, String> texts) {
        List<Rule> rules = new ArrayList<>();
        int[] precedence = {0};
        int[] order = {0};
        visit(mainPath, texts, rules, precedence, order, new HashSet<>());
        return rules;
    }

    private static void visit(String path, Function<String, String> texts, List<Rule> rules,
                              int[] precedence, int[] order, Set<String> seen) {
        if (path == null || !seen.add(path)) {
            return;
        }
        // The module and everything it includes share one precedence; their imports come first.
        List<String> members = new ArrayList<>();
        List<String> imports = new ArrayList<>();
        collectMembers(path, texts, members, imports, seen);
        for (String imported : imports) {
            visit(imported, texts, rules, precedence, order, seen);
        }
        int mine = ++precedence[0];
        for (String member : members) {
            String text = text(texts, member);
            Matcher m = TEMPLATE.matcher(text);
            int[] map = StartTagLines.map(text);
            while (m.find()) {
                int gt = tagEnd(text, m.start());
                if (gt < 0) {
                    break;
                }
                String attrs = text.substring(m.end(), gt);
                String match = attribute(attrs, "match");
                if (match == null) {
                    continue;
                }
                Set<String> modes = new LinkedHashSet<>();
                String mode = attribute(attrs, "mode");
                if (mode == null || mode.isBlank()) {
                    modes.add("#default");
                } else {
                    for (String token : mode.strip().split("\\s+")) {
                        modes.add(token);
                    }
                }
                Double priority = null;
                try {
                    String p = attribute(attrs, "priority");
                    priority = p == null ? null : Double.valueOf(p.strip());
                } catch (NumberFormatException ignored) {
                    // an invalid priority is a static error Saxon reports; treat it as default
                }
                int line = StartTagLines.resolve(map, lineOf(text, m.start()));
                rules.add(new Rule(match.strip(), modes, priority, member, line, mine, ++order[0]));
            }
        }
    }

    private static void collectMembers(String path, Function<String, String> texts, List<String> members,
                                       List<String> imports, Set<String> seen) {
        members.add(path);
        String text = text(texts, path);
        Matcher d = DIRECTIVE.matcher(text);
        while (d.find()) {
            String href = attribute(d.group(2), "href");
            if (href == null) {
                continue;
            }
            String target = resolve(path, href);
            if ("include".equals(d.group(1))) {
                if (seen.add(target)) {
                    collectMembers(target, texts, members, imports, seen);
                }
            } else {
                imports.add(target);
            }
        }
    }

    private static String resolve(String base, String href) {
        try {
            Path parent = Path.of(base).getParent();
            return SourceLocations.normalize((parent == null ? Path.of(href) : parent.resolve(href)).toString());
        } catch (RuntimeException e) {
            return href;
        }
    }

    private static String text(Function<String, String> texts, String path) {
        try {
            String t = texts.apply(path);
            return t == null ? "" : t;
        } catch (RuntimeException e) {
            return "";
        }
    }

    /**
     * The candidates for a node processed in {@code mode} ({@code #default} for the unnamed one):
     * every alternative of every rule that applies to the mode, best first - higher precedence,
     * then higher priority, then later in declaration order (the rule XSLT picks).
     */
    public static List<Candidate> candidates(List<Rule> rules, String mode) {
        String wanted = mode == null || mode.isBlank() ? "#default" : mode;
        List<Candidate> out = new ArrayList<>();
        for (Rule rule : rules) {
            if (!rule.modes().contains("#all") && !rule.modes().contains(wanted)) {
                continue;
            }
            for (String alternative : alternatives(rule.match())) {
                double priority = rule.priority() != null ? rule.priority() : defaultPriority(alternative);
                out.add(new Candidate(rule, alternative, priority));
            }
        }
        out.sort(Comparator.comparingInt((Candidate c) -> c.rule().precedence()).reversed()
                .thenComparing(Comparator.comparingDouble(Candidate::priority).reversed())
                .thenComparing(Comparator.comparingInt((Candidate c) -> c.rule().order()).reversed()));
        return out;
    }

    /** Splits a pattern at its top-level {@code |} / {@code union}. */
    public static List<String> alternatives(String pattern) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        char quote = 0;
        int start = 0;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '[' || c == '(' || c == '{') {
                depth++;
            } else if (c == ']' || c == ')' || c == '}') {
                depth--;
            } else if (depth == 0 && c == '|') {
                parts.add(pattern.substring(start, i).strip());
                start = i + 1;
            } else if (depth == 0 && pattern.startsWith(" union ", i)) {
                parts.add(pattern.substring(start, i).strip());
                start = i + 7;
                i += 6;
            }
        }
        parts.add(pattern.substring(start).strip());
        parts.removeIf(String::isEmpty);
        return parts;
    }

    /**
     * The default priority of a single (non-union) pattern, after XSLT 3.0 §6.5: a plain name test
     * (optionally on the attribute / child axis) 0, {@code prefix:*} / {@code *:name} -0.25,
     * {@code *} / {@code node()} / kind tests -0.5, {@code /} -0.5, anything else
     * (paths, predicates) 0.5.
     */
    public static double defaultPriority(String pattern) {
        String p = pattern.strip();
        if (p.startsWith("child::")) {
            p = p.substring(7);
        } else if (p.startsWith("attribute::")) {
            p = p.substring(11);
        } else if (p.startsWith("@")) {
            p = p.substring(1);
        }
        if ("/".equals(p) || "document-node()".equals(p)) {
            return -0.5;
        }
        if (p.matches("\\*|node\\(\\)|text\\(\\)|comment\\(\\)|processing-instruction\\(\\)|element\\(\\)|attribute\\(\\)"
                + "|element\\(\\*\\)|attribute\\(\\*\\)")) {
            return -0.5;
        }
        if (p.matches("[\\w.-]+:\\*|\\*:[\\w.-]+|Q\\{[^}]*}\\*")) {
            return -0.25;
        }
        if (p.matches("([\\w.-]+:)?[\\w.-]+|Q\\{[^}]*}[\\w.-]+|processing-instruction\\(\\s*['\"]?[\\w.-]+['\"]?\\s*\\)"
                + "|element\\(\\s*[\\w.:-]+\\s*\\)|attribute\\(\\s*[\\w.:-]+\\s*\\)")) {
            return 0;
        }
        return 0.5;
    }

    /**
     * An XPath that is true when the node bound as {@code $value} matches {@code alternative}:
     * the node is among the pattern's results from some node of its tree.
     */
    public static String testExpression(String alternative) {
        return "some $fxt-c in (root($value)/descendant-or-self::node()/(" + alternative + ")) satisfies $fxt-c is $value";
    }

    static String formatPriority(double p) {
        return p == Math.rint(p) ? Long.toString((long) p) : Double.toString(p);
    }

    private static String attribute(String attrs, String name) {
        Matcher a = ATTRIBUTE.matcher(attrs);
        while (a.find()) {
            if (name.equals(a.group(1))) {
                String v = a.group(2);
                return v.substring(1, v.length() - 1);
            }
        }
        return null;
    }

    private static int tagEnd(String text, int lt) {
        char quote = 0;
        for (int i = lt + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '>') {
                return i;
            }
        }
        return -1;
    }

    private static int lineOf(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
