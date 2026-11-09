package org.fxt.freexmltoolkit.service.fundsxml;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.xml.transform.stream.StreamSource;

import org.fxt.freexmltoolkit.domain.XPathSnippet;
import org.fxt.freexmltoolkit.service.SaxonResourceAccess;
import org.fxt.freexmltoolkit.service.XPathSnippetRepository;

import com.google.gson.Gson;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XQueryEvaluator;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmMap;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;

/**
 * The data-quality checks for FundsXML 4 documents ({@code plugins/fundsxml/dq-checks.json}): XQuery
 * checks that each return one text per finding — control data, ISIN and LEI check digits, currency
 * codes, weights, positions against the net asset value, master data, share classes, transactions,
 * matured instruments. They run from the FundsXML panel and with every validation of a FundsXML
 * document, and are offered as snippets in the Query Console together with analysis queries.
 */
public final class FundsXmlDataQuality {

    /** A check of the catalog. */
    public static final class Check {
        public String key;
        public String name;
        public String description;
        public String query;
    }

    /** A query snippet of the catalog. */
    public static final class Snippet {
        public String name;
        public String description;
        public String type;
        public String category;
        public String query;
    }

    private static final class Catalog {
        String prolog;
        List<Check> checks;
        List<Snippet> snippets;
    }

    /**
     * The outcome of a check.
     *
     * @param findings one text per finding; empty when the check passed
     * @param error    why the check could not run, or {@code null}
     * @param lines    per finding the line of the node it is about (1-based), 0 when unknown
     */
    public record Result(Check check, List<String> findings, String error, List<Integer> lines) {
        public Result {
            findings = List.copyOf(findings);
            lines = lines == null || lines.size() != findings.size()
                    ? java.util.Collections.nCopies(findings.size(), 0) : List.copyOf(lines);
        }

        public Result(Check check, List<String> findings, String error) {
            this(check, findings, error, null);
        }

        public boolean passed() {
            return error == null && findings.isEmpty();
        }

        /** @return the line of finding {@code index}, 0 when it has none */
        public int lineOf(int index) {
            return lines.get(index);
        }
    }

    /**
     * {@code local:at($node, $text)} wraps every finding of a check. For the snippets and the Query
     * Console it is the text; {@link #run} uses {@link #AT_LOCATED}, which also returns the node, so a
     * finding knows its line.
     */
    private static final String AT_TEXT = """
            declare function local:at($n as node()?, $msg as xs:string) as item() { $msg };
            """;
    private static final String AT_LOCATED = """
            declare function local:at($n as node()?, $msg as xs:string) as item() {
              map { 'message': $msg, 'node': ($n/self::element(), $n/parent::element())[1] }
            };
            """;

    /** Tag of the snippets this class adds (the Query Console lists them in its FundsXML section). */
    public static final String SNIPPET_TAG = "fundsxml";
    private static final String ID_PREFIX = "fundsxml-dq-";

    private static volatile Catalog catalog;
    private static final Processor PROCESSOR = SaxonResourceAccess.restrictedProcessor();

    private FundsXmlDataQuality() {
    }

    private static Catalog catalog() {
        if (catalog == null) {
            try (InputStream in = FundsXmlDataQuality.class.getResourceAsStream("/plugins/fundsxml/dq-checks.json")) {
                if (in == null) {
                    throw new IOException("plugins/fundsxml/dq-checks.json is missing");
                }
                catalog = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Catalog.class);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return catalog;
    }

    public static List<Check> checks() {
        return List.copyOf(catalog().checks);
    }

    public static List<Snippet> snippets() {
        return List.copyOf(catalog().snippets);
    }

    /** @return the full XQuery of a check: prolog plus its query, its findings as strings */
    public static String fullQuery(Check check) {
        return catalog().prolog + "\n" + AT_TEXT + check.query;
    }

    private static String locatedQuery(Check check) {
        return catalog().prolog + "\n" + AT_LOCATED + check.query;
    }

    /** @return whether the text is a FundsXML 4 document */
    public static boolean isFundsXml(String xml) {
        return org.fxt.freexmltoolkit.plugin.XmlScan.rootOf(xml).map(root -> root[0].startsWith("FundsXML"))
                .orElse(false);
    }

    /** Runs every check on a document (on the calling thread). */
    public static List<Result> run(String xml) {
        XdmNode document;
        try {
            var builder = PROCESSOR.newDocumentBuilder();
            builder.setLineNumbering(true);
            document = builder.build(new StreamSource(new StringReader(xml)));
        } catch (SaxonApiException e) {
            return checks().stream().map(c -> new Result(c, List.of(), "The document is not well-formed: "
                    + e.getMessage())).toList();
        }
        List<Result> results = new ArrayList<>();
        for (Check check : checks()) {
            results.add(run(check, document));
        }
        return results;
    }

    static Result run(Check check, XdmNode document) {
        try {
            XQueryEvaluator evaluator = PROCESSOR.newXQueryCompiler().compile(locatedQuery(check)).load();
            evaluator.setContextItem(document);
            XdmValue value = evaluator.evaluate();
            List<String> findings = new ArrayList<>();
            List<Integer> lines = new ArrayList<>();
            for (XdmItem item : value) {
                int line = 0;
                String text;
                if (item instanceof XdmMap map) {
                    XdmValue message = map.get("message");
                    text = message instanceof XdmItem m ? m.getStringValue() : "";
                    XdmValue node = map.get("node");
                    if (node instanceof XdmNode n && n.getLineNumber() > 0) {
                        line = n.getLineNumber();
                    }
                } else {
                    text = item.getStringValue();
                }
                text = text.strip();
                if (!text.isEmpty()) {
                    findings.add(text);
                    lines.add(line);
                }
            }
            return new Result(check, findings, null, lines);
        } catch (SaxonApiException | RuntimeException e) {
            return new Result(check, List.of(), String.valueOf(e.getMessage()));
        }
    }

    /** @return the results as an HTML report (summary, then each check with its findings) */
    public static String htmlReport(List<Result> results, String documentName) {
        long failed = results.stream().filter(r -> !r.passed()).count();
        StringBuilder html = new StringBuilder("""
                <!DOCTYPE html><html><head><meta charset="UTF-8"><title>FundsXML data quality</title><style>
                body{font-family:system-ui,sans-serif;margin:24px;color:#1a1d21}
                h1{font-size:20px} .summary{margin:8px 0 16px;color:#5a6472}
                table{border-collapse:collapse;width:100%} td,th{border-bottom:1px solid #dde1e7;padding:6px 8px;text-align:left;vertical-align:top}
                .ok{color:#1f7a35;font-weight:600} .fail{color:#c92a2a;font-weight:600} .err{color:#a65500;font-weight:600}
                ul{margin:0;padding-left:18px} .desc{color:#5a6472;font-size:12px}
                </style></head><body>
                """);
        html.append("<h1>FundsXML data quality</h1><div class=\"summary\">").append(escape(documentName == null ? "" : documentName))
                .append(" · ").append(results.size()).append(" checks · ")
                .append(failed == 0 ? "all passed" : failed + " with findings").append("</div><table>")
                .append("<tr><th>Check</th><th>Result</th><th>Findings</th></tr>");
        for (Result r : results) {
            String status = r.error() != null ? "<span class=\"err\">not run</span>"
                    : r.findings().isEmpty() ? "<span class=\"ok\">passed</span>"
                    : "<span class=\"fail\">" + r.findings().size() + (r.findings().size() == 1 ? " finding" : " findings") + "</span>";
            html.append("<tr><td>").append(escape(r.check().name.replaceFirst("^DQ: ", "")))
                    .append("<div class=\"desc\">").append(escape(r.check().description)).append("</div></td><td>")
                    .append(status).append("</td><td>");
            if (r.error() != null) {
                html.append(escape(r.error()));
            } else if (!r.findings().isEmpty()) {
                html.append("<ul>");
                r.findings().stream().limit(200).forEach(f -> html.append("<li>").append(escape(f)).append("</li>"));
                if (r.findings().size() > 200) {
                    html.append("<li>… ").append(r.findings().size() - 200).append(" more</li>");
                }
                html.append("</ul>");
            }
            html.append("</td></tr>");
        }
        return html.append("</table></body></html>").toString();
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /**
     * Adds the checks and the analysis queries to the snippet store (tag {@value #SNIPPET_TAG}); existing
     * ones are replaced, so a new version of the catalog updates them.
     */
    public static int registerSnippets(XPathSnippetRepository repository) {
        int count = 0;
        for (Check check : checks()) {
            repository.saveSnippet(snippet(ID_PREFIX + check.key, check.name, check.description,
                    XPathSnippet.SnippetType.XQUERY, XPathSnippet.SnippetCategory.VALIDATION, fullQuery(check)));
            count++;
        }
        for (Snippet s : snippets()) {
            String key = s.name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
            repository.saveSnippet(snippet(ID_PREFIX + key, s.name, s.description, XPathSnippet.SnippetType.valueOf(s.type),
                    XPathSnippet.SnippetCategory.valueOf(s.category), s.query));
            count++;
        }
        return count;
    }

    private static XPathSnippet snippet(String id, String name, String description, XPathSnippet.SnippetType type,
                                        XPathSnippet.SnippetCategory category, String query) {
        XPathSnippet snippet = new XPathSnippet(name, type, category, query);
        snippet.setId(id);
        snippet.setDescription(description);
        snippet.setAuthor("FreeXmlToolkit");
        Set<String> tags = new LinkedHashSet<>(List.of(SNIPPET_TAG, "data-quality"));
        snippet.setTags(tags);
        return snippet;
    }
}
