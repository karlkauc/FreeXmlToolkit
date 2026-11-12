package org.fxt.freexmltoolkit.service.ai.tool;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.fxt.freexmltoolkit.service.SandboxedQuery;

import com.google.gson.JsonObject;

/** {@code xquery}: runs an XQuery 3.1 query, optionally against a document (Saxon, sandboxed). */
public final class XQueryTool implements AiTool {

    private static final Set<String> ARGUMENTS =
            Set.of("xml_path", "xml_content", "query_path", "query_content", "variables", "xquery_version");

    @Override
    public String name() {
        return "xquery";
    }

    @Override
    public String description() {
        return "Run an XQuery query (3.1 unless xquery_version says otherwise), with an XML document as the context item when one is given. The result "
                + "sequence is serialized as XML (maps, arrays and attributes adaptively); 'item_count' says how many "
                + "items it has. External variables are passed as strings in 'variables' (declare them with "
                + "'declare variable $name external;'). Static and dynamic errors come back with code, phase and "
                + "line. Reads only inside the allowed folders; stops at the time limit. With xquery_version 1.0 or "
                + "3.0 the query is first checked for newer syntax and functions (code FXT-COMPAT, phase compile).";
    }

    @Override
    public JsonObject inputSchema() {
        return ToolSchemas.builder()
                .input("xml", "XML document used as the context item (optional)")
                .input("query", "XQuery")
                .stringMap("variables", "External variables: name → string value.")
                .version("xquery_version", "XQuery version the query must conform to (default 3.1).",
                        org.fxt.freexmltoolkit.service.standards.XQueryVersion.class)
                .build();
    }

    @Override
    public ToolResult call(JsonObject arguments, ToolContext context) throws ToolException {
        ToolArgs args = new ToolArgs(arguments, ARGUMENTS);
        ToolInput query = ToolInput.required(args, "query", context);
        Map<String, String> variables = args.stringMap("variables");
        var version = args.version("xquery_version", org.fxt.freexmltoolkit.service.standards.XQueryVersion.class);
        Optional<ToolInput> xml = ToolInput.optional(args, "xml", context);
        SandboxedQuery.Outcome outcome = SandboxedQuery.run(SandboxedQuery.Language.XQUERY,
                xml.map(ToolInput::text).orElse(null), xml.map(QueryResults::uriOf).orElse(null),
                query.text(), QueryResults.uriOf(query), variables, Map.of(),
                QueryResults.engineDeadline(context.limits()), context.files().roots(), true, version);
        return QueryResults.of(outcome, context.limits());
    }
}
