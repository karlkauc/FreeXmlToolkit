package org.fxt.freexmltoolkit.service.standards;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.XPathParser;
import net.sf.saxon.query.QueryModule;
import net.sf.saxon.query.StaticQueryContext;
import net.sf.saxon.query.XQueryParser;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;

/**
 * Checks that an XQuery uses only what XQuery 1.0 or 3.0 has. Saxon HE compiles XQuery 3.1 only, so the query
 * is checked first and then runs on the 3.1 processor (the semantics of what passes are the same).
 *
 * <p>Three layers, each best effort and together close to the real grammar:
 * <ol>
 *   <li>Saxon's own XQuery parser held at the target's language level (it rejects {@code ||}, {@code !},
 *       {@code =>}, maps, arrays, inline functions, try/catch … below their version);</li>
 *   <li>a scan for the 3.0 FLWOR / prolog additions the parser accepts at any level ({@code group by},
 *       {@code switch}, windows, {@code count}, {@code allowing empty}, annotations, …);</li>
 *   <li>calls of built-in functions the target's function library does not have ({@link FunctionCatalog}).</li>
 * </ol>
 */
public final class XQueryCompatibilityCheck {

    private static final Configuration CONFIG = new Configuration();

    private record Construct(Pattern pattern, String what, XQueryVersion since) {
    }

    private static final List<Construct> CONSTRUCTS = List.of(
            new Construct(Pattern.compile("\\bgroup\\s+by\\b"), "group by", XQueryVersion.V3_0),
            new Construct(Pattern.compile("\\bswitch\\s*\\("), "switch expressions", XQueryVersion.V3_0),
            new Construct(Pattern.compile("\\b(?:tumbling|sliding)\\s+window\\b"), "window clauses",
                    XQueryVersion.V3_0),
            new Construct(Pattern.compile("\\bcount\\s+\\$"), "the count clause", XQueryVersion.V3_0),
            new Construct(Pattern.compile("\\ballowing\\s+empty\\b"), "allowing empty", XQueryVersion.V3_0),
            new Construct(Pattern.compile("(?<![\\w%-])%[A-Za-z_][\\w.:-]*"), "annotations", XQueryVersion.V3_0),
            new Construct(Pattern.compile("\\bdeclare\\s+context\\s+item\\b"), "declare context item",
                    XQueryVersion.V3_0),
            new Construct(Pattern.compile("\\bdeclare\\s+(?:default\\s+)?decimal-format\\b"),
                    "decimal-format declarations", XQueryVersion.V3_0),
            new Construct(Pattern.compile("\\bvalidate\\s+type\\b"), "validate type", XQueryVersion.V3_0),
            new Construct(Pattern.compile("``\\["), "string constructors", XQueryVersion.V3_1));

    private static final Pattern VERSION_DECL = Pattern.compile(
            "^\\s*xquery\\s+version\\s+[\"']([0-9.]+)[\"']");

    private XQueryCompatibilityCheck() {
    }

    /** The constructs {@code query} uses that {@code target} lacks; empty for 3.1 or a compatible query. */
    public static List<CompatibilityIssue> check(String query, XQueryVersion target) {
        if (query == null || target == null || target == XQueryVersion.V3_1) {
            return List.of();
        }
        List<CompatibilityIssue> issues = new ArrayList<>();
        SourceMask mask = SourceMask.ofQuery(query);
        CompatibilityIssue parse = parseAtLevel(query, target);
        if (parse != null) {
            issues.add(parse);
        }
        for (Construct c : CONSTRUCTS) {
            if (c.since().compareTo(target) <= 0) {
                continue;
            }
            Matcher m = c.pattern().matcher(mask.masked());
            while (m.find()) {
                issues.add(new CompatibilityIssue(mask.line(m.start()), mask.column(m.start()),
                        "FXT-COMPAT-SYNTAX", capitalize(c.what()) + " " + (c.what().endsWith("s") ? "are" : "is")
                        + " not available in " + target.label() + " (introduced in " + c.since().label() + ")."));
            }
        }
        issues.addAll(FunctionCatalog.findNewerCalls(mask, target.xpathVersion(), target.label()));
        Matcher declOriginal = VERSION_DECL.matcher(query);
        if (declOriginal.find()) {
            XQueryVersion declared = StandardVersion.parseStrict(XQueryVersion.class, declOriginal.group(1));
            if (declared != null && declared.compareTo(target) > 0) {
                issues.add(new CompatibilityIssue(1, 1, "FXT-COMPAT-VERSION", "The query declares xquery version \""
                        + declared.cliValue() + "\" but " + target.label() + " is selected."));
            }
        }
        return dedupe(issues);
    }

    /** One line per issue, for an "ERROR: …" result text. */
    public static String format(List<CompatibilityIssue> issues, String languageLabel) {
        StringBuilder sb = new StringBuilder(languageLabel).append(" compatibility check failed (")
                .append(issues.size()).append(issues.size() == 1 ? " issue):" : " issues):");
        for (CompatibilityIssue issue : issues) {
            sb.append(System.lineSeparator()).append("  ").append(issue.describe());
        }
        return sb.toString();
    }

    /** Saxon's XQuery parser at the target's level; the first syntax error, or {@code null}. */
    private static CompatibilityIssue parseAtLevel(String query, XQueryVersion target) {
        int level = target == XQueryVersion.V1_0 ? 10 : 30;
        try {
            StaticQueryContext sqc = CONFIG.newStaticQueryContext();
            sqc.setErrorReporter(error -> {
            });
            QueryModule module = new QueryModule(sqc);
            new LevelParser(module, level).makeXQueryExpression(query, module, CONFIG);
            return null;
        } catch (XPathException e) {
            String message = e.getMessage() == null ? "syntax error" : e.getMessage();
            boolean versionRelated = message.contains("XPath 3") || message.contains("XQuery 3")
                    || message.contains("3.0") || message.contains("3.1") || message.contains("map syntax");
            if (!versionRelated) {
                // a plain syntax or static error: the 3.1 compile reports it with full detail
                return null;
            }
            Location loc = e.getLocator();
            return new CompatibilityIssue(loc == null ? 0 : loc.getLineNumber(), loc == null ? 0
                    : loc.getColumnNumber(), e.getErrorCodeQName() == null ? "XPST0003"
                    : e.getErrorCodeQName().getLocalPart(), message.replace("XPath parser", "parser") + " (" + target.label()
                    + " selected).");
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static List<CompatibilityIssue> dedupe(List<CompatibilityIssue> issues) {
        List<CompatibilityIssue> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        issues.stream().sorted(Comparator.comparingInt(CompatibilityIssue::line)
                        .thenComparingInt(CompatibilityIssue::column))
                .forEach(i -> {
                    if (seen.add(i.line() + ":" + i.column() + ":" + i.message())) {
                        out.add(i);
                    }
                });
        return out;
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Saxon's XQuery parser pinned to a language level. */
    private static final class LevelParser extends XQueryParser {
        private final int level;

        LevelParser(QueryModule module, int level) {
            super(module);
            this.level = level;
        }

        @Override
        protected void setLanguage(XPathParser.ParsedLanguage language, int version) {
            // Called from the super constructor too, before level is assigned: pass the requested version
            // through then; once constructed, every parse runs at the pinned level
            super.setLanguage(language, level > 0 ? level : version);
        }
    }
}
