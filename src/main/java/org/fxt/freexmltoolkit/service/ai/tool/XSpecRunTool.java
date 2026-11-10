package org.fxt.freexmltoolkit.service.ai.tool;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.fxt.freexmltoolkit.service.XSpecRunner;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * {@code xspec_run}: runs XSpec tests of an XSLT stylesheet, an XQuery module or a Schematron in a sandboxed
 * {@link XSpecRunner} — the tested code reads only inside the roots and XSpec's library, sees no environment, and
 * stops at the time limit; nothing it produces reaches the disk.
 */
public final class XSpecRunTool implements AiTool {

    private static final Set<String> ARGUMENTS = Set.of("xspec_path", "xspec_content", "base_path");

    @Override
    public String name() {
        return "xspec_run";
    }

    @Override
    public String description() {
        return "Run XSpec tests (for an XSLT stylesheet, an XQuery module or a Schematron) and return every scenario "
                + "and test with label, status (passed, failed, pending) and, for failures, the expected and actual "
                + "result. Relative references in the XSpec (stylesheet=, query-at=, schematron=, x:context href) "
                + "resolve against the XSpec file; for inline xspec_content give base_path, a path inside the "
                + "allowed folders that stands for where the XSpec would be. 'passed' is true when no test failed.";
    }

    @Override
    public JsonObject inputSchema() {
        return ToolSchemas.builder()
                .input("xspec", "XSpec description")
                .string("base_path", "For xspec_content: the file path relative references resolve against.", false)
                .build();
    }

    @Override
    public ToolResult call(JsonObject arguments, ToolContext context) throws ToolException {
        ToolArgs args = new ToolArgs(arguments, ARGUMENTS);
        ToolInput xspec = ToolInput.required(args, "xspec", context);
        Path location;
        if (xspec.file().isPresent()) {
            if (args.has("base_path")) {
                throw new ToolException("'base_path' is only for xspec_content");
            }
            location = xspec.file().get();
        } else {
            location = args.has("base_path") ? context.files().resolve(args.requiredString("base_path"))
                    : context.files().roots().getFirst().resolve("inline.xspec");
        }
        XSpecRunner runner;
        try {
            runner = XSpecRunner.sandboxed(context.files().roots(), QueryResults.engineDeadline(context.limits()));
        } catch (IOException e) {
            throw new ToolException("cannot prepare the test run: " + e.getMessage(), e);
        }
        XSpecRunner.Result result = runner.run(xspec.text(), location);
        ToolLimits limits = context.limits();
        JsonObject json = new JsonObject();
        json.addProperty("kind", result.kind().name().toLowerCase(Locale.ROOT));
        json.addProperty("passed", result.passed());
        json.addProperty("summary", result.summary());
        json.addProperty("timed_out", XSpecRunner.TIMED_OUT.equals(result.error()));
        JsonArray scenarios = new JsonArray();
        result.scenarios().forEach(s -> scenarios.add(scenario(s, limits)));
        json.add("scenarios", scenarios);
        json.addProperty("error", result.error());

        StringBuilder text = new StringBuilder(result.summary());
        for (XSpecRunner.Test test : result.tests()) {
            if (test.status() == XSpecRunner.Status.FAILED) {
                text.append("\nFAILED: ").append(test.label())
                        .append("\n  expected: ").append(limits.truncate(oneLine(test.expected())))
                        .append("\n  actual:   ").append(limits.truncate(oneLine(test.actual())));
            }
        }
        return result.passed() ? ToolResult.ok(text.toString(), json) : ToolResult.failed(text.toString(), json);
    }

    private static JsonObject scenario(XSpecRunner.Scenario scenario, ToolLimits limits) {
        JsonObject json = new JsonObject();
        json.addProperty("label", scenario.label());
        json.addProperty("pending", scenario.pending());
        JsonArray tests = new JsonArray();
        for (XSpecRunner.Test test : scenario.tests()) {
            JsonObject t = new JsonObject();
            t.addProperty("label", test.label());
            t.addProperty("status", test.status().name().toLowerCase(Locale.ROOT));
            if (test.status() == XSpecRunner.Status.FAILED) {
                t.addProperty("expected", limits.truncate(test.expected()));
                t.addProperty("actual", limits.truncate(test.actual()));
            }
            tests.add(t);
        }
        json.add("tests", tests);
        JsonArray children = new JsonArray();
        List<XSpecRunner.Scenario> nested = scenario.scenarios();
        nested.forEach(s -> children.add(scenario(s, limits)));
        json.add("scenarios", children);
        return json;
    }

    private static String oneLine(String text) {
        return text == null ? "" : text.strip().replaceAll("\\s*\\R\\s*", " ");
    }
}
