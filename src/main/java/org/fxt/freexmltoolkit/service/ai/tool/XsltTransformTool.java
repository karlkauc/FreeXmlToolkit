package org.fxt.freexmltoolkit.service.ai.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.fxt.freexmltoolkit.controls.shell.editor.TransformRunner;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * {@code xslt_transform}: runs an XSLT 1.0–3.0 stylesheet on a document in the sandbox
 * ({@link TransformRunner#xsltTransformSandboxed}) — nothing is written, {@code xsl:result-document} output is
 * returned.
 */
public final class XsltTransformTool implements AiTool {

    private static final Set<String> ARGUMENTS =
            Set.of("xml_path", "xml_content", "xslt_path", "xslt_content", "parameters", "output");

    private static final Map<String, OutputFormat> OUTPUTS = Map.of(
            "xml", OutputFormat.XML, "html", OutputFormat.HTML, "xhtml", OutputFormat.XHTML,
            "text", OutputFormat.TEXT, "json", OutputFormat.JSON);

    @Override
    public String name() {
        return "xslt_transform";
    }

    @Override
    public String description() {
        return "Run an XSLT stylesheet (1.0, 2.0 or 3.0, Saxon) on an XML document and return the output. Pass the "
                + "stylesheet you just wrote as xslt_content to check it without saving it. Nothing is written to "
                + "disk: xsl:result-document output comes back in 'result_documents'. xsl:message output is in "
                + "'messages'. A stylesheet that does not compile or fails at run time returns 'error' with code, "
                + "phase (compile, input, runtime) and line — fix the stylesheet and call again. No Java "
                + "extensions, no network, reads only inside the allowed folders, stops at the time limit.";
    }

    @Override
    public JsonObject inputSchema() {
        return ToolSchemas.builder()
                .input("xml", "XML source document")
                .input("xslt", "XSLT stylesheet")
                .stringMap("parameters", "Stylesheet parameters: name → string value.")
                .enumeration("output", "Output method; by default the one xsl:output declares.",
                        List.of("xml", "html", "xhtml", "text", "json"), null)
                .build();
    }

    @Override
    public ToolResult call(JsonObject arguments, ToolContext context) throws ToolException {
        ToolArgs args = new ToolArgs(arguments, ARGUMENTS);
        ToolInput xml = ToolInput.required(args, "xml", context);
        ToolInput xslt = ToolInput.required(args, "xslt", context);
        Map<String, Object> parameters = new LinkedHashMap<>(args.stringMap("parameters"));
        OutputFormat format;
        String requested = args.optionalString("output").orElse(null);
        if (requested == null) {
            format = TransformRunner.detectXsltOutputFormat(xslt.text());
        } else {
            format = OUTPUTS.get(requested);
            if (format == null) {
                throw new ToolException("argument 'output' must be one of xml, html, xhtml, text, json");
            }
        }
        XsltTransformationEngine.SandboxedTransformation run = TransformRunner.xsltTransformSandboxed(xml.text(),
                QueryResults.uriOf(xml), xslt.text(), QueryResults.uriOf(xslt), parameters, format,
                QueryResults.engineDeadline(context.limits()), context.files().roots());
        XsltTransformationResult r = run.result();
        ToolLimits limits = context.limits();

        JsonObject result = new JsonObject();
        result.addProperty("ok", r.isSuccess());
        result.addProperty("output_method", format.getSaxonMethod());
        ToolJson.putText(result, "output", r.isSuccess() ? r.getOutputContent() : "", limits);
        JsonObject documents = new JsonObject();
        run.resultDocuments().forEach((uri, text) -> documents.addProperty(uri, limits.truncate(text)));
        result.add("result_documents", documents);
        JsonArray messages = new JsonArray();
        if (r.getMessages() != null) {
            r.getMessages().stream().limit(limits.maxItems()).forEach(m -> {
                JsonObject message = new JsonObject();
                message.addProperty("level", m.getLevel());
                message.addProperty("text", limits.truncate(m.getMessage()));
                if (m.getLineNumber() > 0) {
                    message.addProperty("line", m.getLineNumber());
                }
                messages.add(message);
            });
        }
        result.add("messages", messages);
        result.addProperty("timed_out", run.timedOut());

        if (r.isSuccess()) {
            result.add("error", com.google.gson.JsonNull.INSTANCE);
            StringBuilder text = new StringBuilder("ok (").append(format.getSaxonMethod()).append(" output, ")
                    .append(r.getOutputContent().length()).append(" characters");
            if (!run.resultDocuments().isEmpty()) {
                text.append(", ").append(run.resultDocuments().size()).append(" result document(s)");
            }
            text.append(")\n").append(limits.truncate(r.getOutputContent()));
            return ToolResult.ok(text.toString(), result);
        }
        String phase = run.timedOut() ? "runtime" : r.getErrorPhase();
        result.add("error", ToolJson.error(r.getErrorCode(), phase, r.getErrorLine(), r.getErrorMessage()));
        return ToolResult.failed(ToolJson.describe(r.getErrorCode(), phase, r.getErrorLine(), r.getErrorMessage()),
                result);
    }
}
