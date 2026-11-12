package org.fxt.freexmltoolkit.service.ai.tool;

import java.util.Map;
import java.util.Set;

import org.fxt.freexmltoolkit.service.SandboxedQuery;

import com.google.gson.JsonObject;

/** {@code xpath}: evaluates an XPath 3.1 expression against a document (Saxon, sandboxed). */
public final class XPathTool implements AiTool {

    private static final Set<String> ARGUMENTS = Set.of("xml_path", "xml_content", "expression", "namespaces",
            "xpath_version");

    @Override
    public String name() {
        return "xpath";
    }

    @Override
    public String description() {
        return "Evaluate an XPath expression (3.1 unless xpath_version says otherwise) against an XML document "
                + "and return the items, one per line: "
                + "atomic values as text, attributes as name=\"value\", elements serialized as XML. The prefixes "
                + "declared on the root element are available; pass 'namespaces' for others, with the key \"\" for "
                + "the default element namespace (or use *:name). Errors come back with code and phase. Reads only "
                + "inside the allowed folders. Use it to inspect large documents instead of reading them whole. "
                + "xpath_version 1.0 runs on a real XPath 1.0 engine, 2.0 and 3.0 reject newer syntax and functions "
                + "- use it to write expressions for older processors.";
    }

    @Override
    public JsonObject inputSchema() {
        return ToolSchemas.builder()
                .input("xml", "XML document")
                .string("expression", "The XPath expression; the document node is the context item.", true)
                .stringMap("namespaces", "Prefix → namespace URI; the key \"\" sets the default element namespace.")
                .version("xpath_version", "XPath version the expression must conform to (default 3.1).",
                        org.fxt.freexmltoolkit.service.standards.XPathVersion.class)
                .build();
    }

    @Override
    public ToolResult call(JsonObject arguments, ToolContext context) throws ToolException {
        ToolArgs args = new ToolArgs(arguments, ARGUMENTS);
        String expression = args.requiredString("expression");
        Map<String, String> namespaces = args.stringMap("namespaces");
        var version = args.version("xpath_version", org.fxt.freexmltoolkit.service.standards.XPathVersion.class);
        ToolInput xml = ToolInput.required(args, "xml", context);
        SandboxedQuery.Outcome outcome = SandboxedQuery.run(SandboxedQuery.Language.XPATH, xml.text(),
                QueryResults.uriOf(xml), expression, null, Map.of(), namespaces,
                QueryResults.engineDeadline(context.limits()), context.files().roots(), true, version);
        return QueryResults.of(outcome, context.limits());
    }
}
