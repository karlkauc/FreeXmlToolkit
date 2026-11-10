package org.fxt.freexmltoolkit.service.ai.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.service.SandboxedQuery;
import org.fxt.freexmltoolkit.service.ai.provider.CancelToken;
import org.fxt.freexmltoolkit.service.ai.tool.ToolContext;

/**
 * Checks an XPath expression or an XQuery query: it compiles, runs on the document in the sandbox and returns the
 * item count and the first results. Namespace prefixes are not declared from the document, so a verified expression
 * runs the same in the Query Console (which declares none).
 */
public final class QueryVerifier implements Verifier {

    private final SandboxedQuery.Language language;
    private final String xml;
    private final String xmlBaseUri;
    private final ToolContext context;
    private volatile SandboxedQuery.Outcome lastOutcome;

    public QueryVerifier(SandboxedQuery.Language language, String xml, String xmlBaseUri, ToolContext context) {
        this.language = language;
        this.xml = xml;
        this.xmlBaseUri = xmlBaseUri;
        this.context = context;
    }

    /** The result of the last check (shown under the expression). */
    public SandboxedQuery.Outcome lastOutcome() {
        return lastOutcome;
    }

    @Override
    public Verification verify(String candidate, CancelToken cancel) {
        SandboxedQuery.Outcome outcome = SandboxedQuery.run(language, xml, xmlBaseUri, candidate, null, Map.of(),
                Map.of(), context.limits().timeout(), context.files().roots(), false);
        lastOutcome = outcome;
        String kind = language == SandboxedQuery.Language.XPATH ? "XPath" : "XQuery";
        List<Check> checks = new ArrayList<>();
        if (!outcome.ok()) {
            String where = outcome.errorLine() > 0 ? " at line " + outcome.errorLine() : "";
            String code = outcome.errorCode() != null ? " [" + outcome.errorCode() + "]" : "";
            boolean compiled = !"compile".equals(outcome.errorPhase());
            checks.add(new Check("Compiles", compiled, compiled ? "" : outcome.errorMessage()));
            if (compiled) {
                checks.add(new Check("Runs on the document", false, outcome.errorMessage()));
            }
            return new Verification(Verification.Status.FAILED, checks, kind + " "
                    + (outcome.errorPhase() == null ? "" : outcome.errorPhase() + " ") + "error" + where + code + ": "
                    + outcome.errorMessage() + (outcome.timedOut() ? " (time limit)" : ""));
        }
        checks.add(new Check("Compiles", true, ""));
        checks.add(new Check("Runs on the document", true, outcome.itemCount() + " item"
                + (outcome.itemCount() == 1 ? "" : "s")));
        if (outcome.itemCount() == 0) {
            // an empty result is often a namespace mistake: say so, but do not reject (the answer may be "none")
            checks.add(Check.info("Result", "empty — check namespaces (*:name) if items were expected"));
        } else {
            checks.add(Check.info("First results", firstLines(outcome.output(), 5)));
        }
        return new Verification(Verification.Status.VERIFIED, checks, "");
    }

    private static String firstLines(String text, int count) {
        String[] lines = text.strip().split("\\R");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < Math.min(count, lines.length); i++) {
            String line = lines[i].length() > 160 ? lines[i].substring(0, 160) + "…" : lines[i];
            out.append(i == 0 ? "" : "\n").append(line);
        }
        if (lines.length > count) {
            out.append("\n…");
        }
        return out.toString();
    }
}
