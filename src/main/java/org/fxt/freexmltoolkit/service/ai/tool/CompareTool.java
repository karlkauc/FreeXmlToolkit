package org.fxt.freexmltoolkit.service.ai.tool;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.fxt.freexmltoolkit.service.XmlStructuralDiff;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** {@code compare}: structural comparison of two XML documents ({@link XmlStructuralDiff}, XMLUnit). */
public final class CompareTool implements AiTool {

    private static final Set<String> ARGUMENTS = Set.of("left_path", "left_content", "right_path", "right_content",
            "ignore_whitespace", "ignore_comments", "ignore_order", "ignore_xpaths", "numeric_tolerance");

    @Override
    public String name() {
        return "compare";
    }

    @Override
    public String description() {
        return "Compare two XML documents structurally (not as text): every added, removed, changed or moved node with "
                + "its XPath on both sides, line numbers and values. 'equivalent' is true when nothing differs. "
                + "Whitespace between elements is ignored by default; options ignore comments, element order, "
                + "the nodes matched by ignore_xpaths, and numeric differences up to numeric_tolerance. Use it to "
                + "check a transformation's output against an expected result.";
    }

    @Override
    public JsonObject inputSchema() {
        return ToolSchemas.builder()
                .input("left", "left (expected) document")
                .input("right", "right (actual) document")
                .bool("ignore_whitespace", "Trim text and collapse whitespace.", true)
                .bool("ignore_comments", "Ignore comments.", false)
                .bool("ignore_order", "Match child elements regardless of their order.", false)
                .stringList("ignore_xpaths", "XPaths of nodes to leave out on both sides.")
                .number("numeric_tolerance", "Numbers that differ by at most this much count as equal.")
                .build();
    }

    @Override
    public ToolResult call(JsonObject arguments, ToolContext context) throws ToolException {
        ToolArgs args = new ToolArgs(arguments, ARGUMENTS);
        ToolInput left = ToolInput.required(args, "left", context);
        ToolInput right = ToolInput.required(args, "right", context);
        List<String> ignore = args.stringList("ignore_xpaths");
        double tolerance = args.optionalNumber("numeric_tolerance").orElse(0.0);
        if (tolerance < 0) {
            throw new ToolException("argument 'numeric_tolerance' must not be negative");
        }
        XmlStructuralDiff.Options options = XmlStructuralDiff.Options.DEFAULTS
                .withBasics(args.optionalBoolean("ignore_whitespace", true), args.optionalBoolean("ignore_comments", false),
                        false, args.optionalBoolean("ignore_order", false), List.of())
                .withIgnoreXPaths(ignore)
                .withNumericTolerance(tolerance);
        String invalid = options.invalidIgnoreXPath();
        if (invalid != null) {
            throw new ToolException("argument 'ignore_xpaths': " + invalid);
        }
        XmlStructuralDiff.Result result = XmlStructuralDiff.compare(left.text(), right.text(), options);
        JsonObject json = new JsonObject();
        json.addProperty("equivalent", result.equivalent());
        json.addProperty("left_error", result.leftError());
        json.addProperty("right_error", result.rightError());
        if (!result.compared()) {
            json.addProperty("total", 0);
            json.add("differences", new JsonArray());
            String text = (result.leftError() != null ? "left: " + result.leftError() : "")
                    + (result.leftError() != null && result.rightError() != null ? "\n" : "")
                    + (result.rightError() != null ? "right: " + result.rightError() : "");
            return ToolResult.failed("Not compared: " + text, json);
        }
        JsonArray differences = new JsonArray();
        StringBuilder text = new StringBuilder(result.equivalent() ? "equivalent"
                : result.differences().size() + " difference" + (result.differences().size() == 1 ? "" : "s"));
        ToolLimits limits = context.limits();
        for (XmlStructuralDiff.Difference d : result.differences()) {
            if (differences.size() >= limits.maxItems()) {
                break;
            }
            String description = describe(d);
            JsonObject row = new JsonObject();
            row.addProperty("change", d.change().name().toLowerCase(Locale.ROOT));
            row.addProperty("kind", d.kind().name().toLowerCase(Locale.ROOT));
            row.addProperty("name", d.name());
            row.addProperty("left_xpath", d.leftXPath());
            row.addProperty("right_xpath", d.rightXPath());
            row.addProperty("left_value", limits.truncate(d.leftValue()));
            row.addProperty("right_value", limits.truncate(d.rightValue()));
            row.addProperty("left_line", Math.max(0, d.leftLine()));
            row.addProperty("right_line", Math.max(0, d.rightLine()));
            row.addProperty("similar", d.similar());
            row.addProperty("description", limits.truncate(description));
            differences.add(row);
            text.append('\n').append(limits.truncate(description));
        }
        json.addProperty("total", result.differences().size());
        json.addProperty("truncated", result.differences().size() > differences.size());
        json.add("differences", differences);
        return result.equivalent() ? ToolResult.ok(text.toString(), json) : ToolResult.failed(text.toString(), json);
    }

    private static String describe(XmlStructuralDiff.Difference d) {
        String kind = d.kind().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        String where = d.leftXPath() != null ? d.leftXPath() : d.rightXPath();
        return switch (d.change()) {
            case ADDED -> kind + " " + nameOf(d) + " added at " + d.rightXPath() + value(" ", d.rightValue());
            case REMOVED -> kind + " " + nameOf(d) + " removed at " + d.leftXPath() + value(" ", d.leftValue());
            case CHANGED -> kind + " " + nameOf(d) + " changed at " + where + ": " + quote(d.leftValue()) + " → "
                    + quote(d.rightValue());
            case MOVED -> kind + " " + nameOf(d) + " moved from " + d.leftXPath() + " to " + d.rightXPath();
        };
    }

    private static String nameOf(XmlStructuralDiff.Difference d) {
        return d.name() == null ? "" : d.name();
    }

    private static String value(String prefix, String value) {
        return value == null || value.isBlank() ? "" : prefix + quote(value);
    }

    private static String quote(String value) {
        if (value == null) {
            return "(none)";
        }
        String one = value.strip().replaceAll("\\s+", " ");
        return "\"" + (one.length() > 120 ? one.substring(0, 120) + "…" : one) + "\"";
    }
}
