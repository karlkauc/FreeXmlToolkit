package org.fxt.freexmltoolkit.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.fxt.freexmltoolkit.service.LocalSchemaCopies;
import org.fxt.freexmltoolkit.service.ai.AiPolicy;
import org.fxt.freexmltoolkit.service.ai.mcp.McpToolServer;
import org.fxt.freexmltoolkit.service.ai.mcp.StdoutGuard;
import org.fxt.freexmltoolkit.service.ai.tool.AiTool;
import org.fxt.freexmltoolkit.service.ai.tool.ToolContext;
import org.fxt.freexmltoolkit.service.ai.tool.ToolLimits;
import org.fxt.freexmltoolkit.service.ai.tool.ToolRegistry;
import org.fxt.freexmltoolkit.service.ai.tool.WorkspaceAccess;
import org.fxt.freexmltoolkit.util.VersionUtil;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * {@code fxt-cli mcp}: serves the read-only XML tools ({@link ToolRegistry#standard()}) to an AI agent over the Model
 * Context Protocol on standard input and output. Standard output carries protocol messages only ({@link StdoutGuard});
 * logs go to standard error. Refused when the administrator policy sets {@code ai.allowed} or {@code ai.mcp.allowed}
 * to {@code false}.
 */
@Command(name = "mcp",
        mixinStandardHelpOptions = true,
        header = "Serve the XML tools to AI agents over the Model Context Protocol (stdio).",
        footer = {
                "  # Register with Claude Code, tools reading the current project folder",
                "  claude mcp add fxt -- fxt-cli mcp --root .",
                "",
                "  # Several folders, only some tools",
                "  fxt-cli mcp --root ~/schemas --root ~/data --tools validate,xpath,schema_describe",
                "",
                "  # What the server offers",
                "  fxt-cli mcp --list-tools"
        },
        description = {
                "Runs an MCP server on standard input and output for an agent (Claude Code, other MCP clients). The "
                        + "agent can validate, query (XPath, XQuery), transform and test (XSLT, XSpec), compare "
                        + "documents and explore schemas.",
                "The tools only read: nothing is written to disk (xsl:result-document output is returned), files "
                        + "are read only inside the --root folders, stylesheets and queries run without Java "
                        + "extensions or network access and stop at a time limit. See docs/mcp-server.md."
        },
        sortOptions = false)
final class McpCommand implements Callable<Integer> {

    @Spec
    CommandLine.Model.CommandSpec spec;

    @Option(names = "--root", paramLabel = "DIR",
            description = "Folder the tools may read; repeatable. Default: the current folder.")
    List<Path> roots;

    @Option(names = "--tools", split = ",", paramLabel = "NAMES",
            description = "Offer only these tools (comma-separated). Default: all; see --list-tools.")
    List<String> tools;

    @Option(names = "--list-tools", description = "Print the tools with a one-line description and exit.")
    boolean listTools;

    @Override
    public Integer call() throws Exception {
        String refusedBy = AiPolicy.mcpRefusedBy();
        if (refusedBy != null) {
            spec.commandLine().getErr().println("fxt-cli: the MCP server is disabled by the administrator policy ("
                    + refusedBy + "=false)");
            return ExitCodes.USAGE;
        }
        ToolRegistry registry = ToolRegistry.standard();
        if (tools != null && !tools.isEmpty()) {
            try {
                registry = registry.only(tools.stream().map(String::strip).filter(t -> !t.isEmpty()).toList());
            } catch (IllegalArgumentException e) {
                throw new CommandLine.ParameterException(spec.commandLine(), "--tools: " + e.getMessage(), e);
            }
        }
        if (listTools) {
            for (AiTool tool : registry.tools()) {
                spec.commandLine().getOut().println(tool.name() + "  " + firstSentence(tool.description()));
            }
            return ExitCodes.OK;
        }
        List<Path> folders = new ArrayList<>();
        for (Path root : roots == null || roots.isEmpty() ? List.of(Path.of("")) : roots) {
            Path folder = root.toAbsolutePath().normalize();
            if (!Files.isDirectory(folder)) {
                throw new CommandLine.ParameterException(spec.commandLine(), "--root: not a folder: " + root);
            }
            folders.add(folder);
        }
        // a downloaded import would otherwise be copied next to the schema that names it: a write into the
        // user's folder, and the tools write nothing
        System.setProperty(LocalSchemaCopies.PROPERTY, "false");
        try (StdoutGuard guard = StdoutGuard.install()) {
            CliSupport.initServices();
            ToolContext context = ToolContext.of(new WorkspaceAccess(folders), ToolLimits.DEFAULTS);
            McpToolServer.Ending ending = new McpToolServer(registry, context, "freexmltoolkit",
                    VersionUtil.getVersion()).serve(System.in, guard.protocolStream());
            if (ending == McpToolServer.Ending.RUNAWAYS) {
                spec.commandLine().getErr().println("fxt-cli mcp: stopped because tool calls kept running past "
                        + "their time limit; the client can start the server again");
                return ExitCodes.FAILED;
            }
            return ExitCodes.OK;
        }
    }

    private static String firstSentence(String text) {
        int end = text.indexOf(". ");
        return end > 0 ? text.substring(0, end + 1) : text;
    }
}
