package org.fxt.freexmltoolkit.service.ai.agent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.fxt.freexmltoolkit.service.ai.provider.CancelToken;
import org.fxt.freexmltoolkit.service.ai.tool.ToolContext;
import org.fxt.freexmltoolkit.service.ai.tool.ToolRegistry;
import org.fxt.freexmltoolkit.service.ai.tool.ToolResult;

import com.google.gson.JsonObject;

/**
 * Checks a generated stylesheet with the Phase 1 tools: it compiles and runs on the source ({@code xslt_transform},
 * sandboxed), its output is well-formed and — when a target schema is given — valid ({@code validate}); the
 * differences to the target sample are reported ({@code compare}) but do not decide, because a sample's values are
 * examples.
 */
public final class XsltVerifier implements Verifier {

    private final String source;
    private final String targetSample;
    private final Path targetSchema;
    private final ToolRegistry tools;
    private final ToolContext context;
    private volatile String lastOutput = "";

    public XsltVerifier(String source, String targetSample, Path targetSchema, ToolContext context) {
        this.source = source;
        this.targetSample = targetSample;
        this.targetSchema = targetSchema;
        this.tools = ToolRegistry.standard();
        this.context = context;
    }

    /** The output of the last run. */
    public String lastOutput() {
        return lastOutput;
    }

    @Override
    public Verification verify(String candidate, CancelToken cancel) {
        List<Check> checks = new ArrayList<>();
        JsonObject args = new JsonObject();
        args.addProperty("xml_content", source);
        args.addProperty("xslt_content", candidate);
        ToolResult run = tools.call("xslt_transform", args, context);
        JsonObject result = run.structured();
        if (run.error() || result == null || !result.get("ok").getAsBoolean()) {
            JsonObject error = result != null && result.has("error") && result.get("error").isJsonObject()
                    ? result.getAsJsonObject("error") : null;
            boolean compiled = error == null || !"compile".equals(string(error, "phase"));
            checks.add(new Check("Compiles", compiled, compiled ? "" : string(error, "message")));
            if (compiled) {
                checks.add(new Check("Runs on the source", false, run.text()));
            }
            return new Verification(Verification.Status.FAILED, checks, run.text());
        }
        checks.add(new Check("Compiles", true, ""));
        lastOutput = result.get("output").getAsString();
        checks.add(new Check("Runs on the source", true, lastOutput.length() + " characters of "
                + result.get("output_method").getAsString() + " output"));
        StringBuilder feedback = new StringBuilder();
        boolean ok = true;
        String method = result.get("output_method").getAsString();
        if (method.equals("xml") || method.equals("xhtml")) {
            JsonObject validate = new JsonObject();
            validate.addProperty("xml_content", lastOutput);
            validate.addProperty("declared_schema", false);
            if (targetSchema != null) {
                validate.addProperty("xsd_path", targetSchema.toString());
            }
            ToolResult valid = tools.call("validate", validate, context);
            boolean passed = !valid.error();
            ok &= passed;
            checks.add(new Check(targetSchema != null ? "Output valid against " + targetSchema.getFileName()
                    : "Output well-formed", passed, passed ? "" : firstLines(valid.text(), 6)));
            if (!passed) {
                feedback.append("The output is not valid:\n").append(valid.text()).append('\n');
            }
        }
        if (targetSample != null && !targetSample.isBlank() && (method.equals("xml") || method.equals("xhtml"))) {
            JsonObject compare = new JsonObject();
            compare.addProperty("left_content", targetSample);
            compare.addProperty("right_content", lastOutput);
            ToolResult diff = tools.call("compare", compare, context);
            JsonObject d = diff.structured();
            String detail = d == null ? diff.text() : d.get("equivalent") != null && d.get("equivalent").getAsBoolean()
                    ? "identical to the sample" : d.get("total").getAsInt() + " differences to the sample (values of "
                    + "a sample are examples)";
            checks.add(Check.info("Compared with the target sample", detail));
            if (!ok && d != null && !d.get("equivalent").getAsBoolean()) {
                feedback.append("Differences to the target sample (first ones):\n").append(firstLines(diff.text(), 12));
            }
        }
        return ok ? new Verification(Verification.Status.VERIFIED, checks, "")
                : new Verification(Verification.Status.FAILED, checks, feedback.toString());
    }

    private static String string(JsonObject o, String name) {
        return o != null && o.has(name) && !o.get(name).isJsonNull() ? o.get(name).getAsString() : "";
    }

    static String firstLines(String text, int count) {
        String[] lines = text.strip().split("\\R");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < Math.min(count, lines.length); i++) {
            out.append(i == 0 ? "" : "\n").append(lines[i]);
        }
        return out.toString();
    }
}
