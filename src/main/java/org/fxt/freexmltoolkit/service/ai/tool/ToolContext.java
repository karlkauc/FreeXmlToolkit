package org.fxt.freexmltoolkit.service.ai.tool;

import java.util.Objects;
import java.util.Optional;

/** What a tool may use besides its arguments: the workspace roots and the limits. */
public interface ToolContext {

    WorkspaceAccess files();

    ToolLimits limits();

    /**
     * The text of an open document ({@code "active"} or a tab name). Only the in-app assistant has open
     * documents; for the MCP server this is always empty.
     */
    default Optional<String> openDocument(String reference) {
        return Optional.empty();
    }

    /** A context without open documents (the MCP server, tests). */
    static ToolContext of(WorkspaceAccess files, ToolLimits limits) {
        Objects.requireNonNull(files, "files");
        Objects.requireNonNull(limits, "limits");
        return new ToolContext() {
            @Override
            public WorkspaceAccess files() {
                return files;
            }

            @Override
            public ToolLimits limits() {
                return limits;
            }
        };
    }
}
