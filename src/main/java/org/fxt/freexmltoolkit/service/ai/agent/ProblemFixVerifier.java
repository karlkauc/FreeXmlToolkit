package org.fxt.freexmltoolkit.service.ai.agent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxt.freexmltoolkit.service.ai.context.ValidationProblemFacts;
import org.fxt.freexmltoolkit.service.ai.provider.CancelToken;
import org.fxt.freexmltoolkit.service.ai.tool.ToolContext;
import org.fxt.freexmltoolkit.service.ai.tool.ToolRegistry;
import org.fxt.freexmltoolkit.service.ai.tool.ToolResult;
import org.fxt.freexmltoolkit.service.xmledit.EditPlan;
import org.fxt.freexmltoolkit.service.xmledit.XPathMatchLocator;
import org.fxt.freexmltoolkit.service.xmledit.XPathReplacePlanner;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Checks a proposed fix of a validation problem. The model answers with a {@code FixProposal} (JSON: xpath,
 * namespaces, mode, replacement); it is planned with the editor's formatting-preserving machinery
 * ({@link XPathMatchLocator} → {@link XPathReplacePlanner} → {@link EditPlan}), applied to a copy, and the copy is
 * validated the same way as the document: the problem must be gone, no new problem may appear, and no element may
 * disappear unless the mode is {@code DELETE}. The {@link EditPlan} of every applicable candidate is kept for applying
 * (one undo step).
 */
public final class ProblemFixVerifier implements Verifier {

    private static final Pattern START_TAG = Pattern.compile("<[\\p{L}_][^\\s/>]*");

    private final String xml;
    private final ValidationProblemFacts problem;
    private final Path schema;
    private final Path schematron;
    private final ToolRegistry tools = ToolRegistry.standard();
    private final ToolContext context;
    private final Map<String, EditPlan> plans = new ConcurrentHashMap<>();
    private List<String> before;

    public ProblemFixVerifier(String xml, ValidationProblemFacts problem, Path schema, Path schematron,
                              ToolContext context) {
        this.xml = xml;
        this.problem = problem;
        this.schema = schema;
        this.schematron = schematron;
        this.context = context;
    }

    /** The plan of a candidate that could be applied (verified or not), or {@code null}. */
    public EditPlan planFor(String candidate) {
        return plans.get(candidate);
    }

    @Override
    public Verification verify(String candidate, CancelToken cancel) {
        List<Check> checks = new ArrayList<>();
        JsonObject proposal;
        try {
            JsonElement json = JsonParser.parseString(candidate);
            proposal = json.getAsJsonObject();
        } catch (RuntimeException e) {
            return Verification.failed("Answer format", "The fix is not a JSON object: " + e.getMessage());
        }
        String xpath = string(proposal, "xpath");
        String modeName = string(proposal, "mode");
        String replacement = string(proposal, "replacement");
        Map<String, String> namespaces = new LinkedHashMap<>();
        if (proposal.has("namespaces") && proposal.get("namespaces").isJsonObject()) {
            proposal.getAsJsonObject("namespaces").entrySet().forEach(e -> namespaces.put(e.getKey(),
                    e.getValue().getAsString()));
        }
        XPathReplacePlanner.ReplaceMode mode;
        try {
            mode = XPathReplacePlanner.ReplaceMode.valueOf(modeName);
        } catch (IllegalArgumentException e) {
            return Verification.failed("Answer format", "mode must be REPLACE_FRAGMENT, SET_VALUE or DELETE, not '"
                    + modeName + "'");
        }
        if (mode == XPathReplacePlanner.ReplaceMode.COMPUTE_VALUE) {
            return Verification.failed("Answer format", "mode COMPUTE_VALUE is not allowed; use SET_VALUE");
        }
        EditPlan plan;
        String changed;
        try {
            XPathMatchLocator.XPathQuery query = new XPathMatchLocator.XPathQuery(xpath, namespaces);
            List<XPathMatchLocator.NodeMatch> matches = XPathMatchLocator.locate(xml, query);
            if (matches.isEmpty()) {
                return new Verification(Verification.Status.FAILED, List.of(new Check("XPath selects a node", false,
                        "no match")), "The xpath '" + xpath + "' selects nothing in the document.");
            }
            if (matches.size() > 1) {
                return new Verification(Verification.Status.FAILED, List.of(new Check("XPath selects one node", false,
                        matches.size() + " matches")), "The xpath selects " + matches.size()
                        + " nodes; make it select exactly the node to change.");
            }
            plan = XPathReplacePlanner.plan(xml, query, mode, replacement, matches).plan();
            changed = plan.applyTo(xml);
        } catch (XPathMatchLocator.XPathEditException e) {
            return Verification.failed("Fix can be applied", e.getMessage());
        }
        checks.add(new Check("Fix can be applied", true, sizeOf(plan)));
        plans.put(candidate, plan); // kept also when a later check fails: "Apply unverified" needs it
        int elementsBefore = countElements(xml);
        int elementsAfter = countElements(changed);
        if (elementsAfter < elementsBefore && mode != XPathReplacePlanner.ReplaceMode.DELETE) {
            checks.add(new Check("No content removed", false, (elementsBefore - elementsAfter) + " element(s) fewer"));
            return new Verification(Verification.Status.FAILED, checks, "The fix removes "
                    + (elementsBefore - elementsAfter) + " element(s). Change the value or add what is missing instead "
                    + "of removing content.");
        }
        checks.add(new Check("No content removed", true, ""));
        if (before == null) {
            before = problems(xml);
        }
        List<String> after = problems(changed);
        boolean gone = after.stream().noneMatch(p -> sameProblem(p));
        List<String> fresh = new ArrayList<>(after);
        fresh.removeAll(before);
        fresh.removeIf(p -> !p.startsWith("error"));
        checks.add(new Check("The problem is gone", gone, gone ? "" : "still reported"));
        checks.add(new Check("No new problem", fresh.isEmpty(), fresh.isEmpty() ? "" : String.join("\n", fresh)));
        if (gone && fresh.isEmpty()) {
            return new Verification(Verification.Status.VERIFIED, checks, "");
        }
        StringBuilder feedback = new StringBuilder("After the fix:\n");
        if (!gone) {
            feedback.append("- the problem is still reported\n");
        }
        fresh.forEach(p -> feedback.append("- new problem: ").append(p).append('\n'));
        return new Verification(Verification.Status.FAILED, checks, feedback.toString());
    }

    /** The document's problems as comparable lines "severity: message" (line numbers move with edits). */
    private List<String> problems(String document) {
        JsonObject args = new JsonObject();
        args.addProperty("xml_content", document);
        args.addProperty("declared_schema", schema == null);
        if (schema != null) {
            args.addProperty("xsd_path", schema.toString());
        }
        if (schematron != null) {
            args.addProperty("schematron_path", schematron.toString());
        }
        ToolResult result = tools.call("validate", args, context);
        List<String> lines = new ArrayList<>();
        if (result.structured() != null && result.structured().has("problems")) {
            for (JsonElement p : result.structured().getAsJsonArray("problems")) {
                JsonObject o = p.getAsJsonObject();
                lines.add(o.get("severity").getAsString() + ": " + o.get("message").getAsString());
            }
        }
        return lines;
    }

    private boolean sameProblem(String line) {
        return line.endsWith(": " + problem.message()) || line.contains(problem.message());
    }

    private static int countElements(String text) {
        Matcher m = START_TAG.matcher(text);
        int count = 0;
        while (m.find()) {
            count++;
        }
        return count;
    }

    private static String sizeOf(EditPlan plan) {
        int removed = plan.edits().stream().mapToInt(e -> e.end() - e.start()).sum();
        int added = plan.edits().stream().mapToInt(e -> e.replacement().length()).sum();
        return plan.edits().size() + " edit(s): " + removed + " characters replaced by " + added;
    }

    private static String string(JsonObject o, String name) {
        return o.has(name) && !o.get(name).isJsonNull() ? o.get(name).getAsString() : "";
    }
}
