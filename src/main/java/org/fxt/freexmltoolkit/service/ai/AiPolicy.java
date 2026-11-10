package org.fxt.freexmltoolkit.service.ai;

import org.fxt.freexmltoolkit.service.AdminPolicy;

/**
 * What the administrator policy allows of the AI features ({@code docs/administration.md}). Both keys are policy
 * only — a user setting cannot switch them on again.
 */
public final class AiPolicy {

    private AiPolicy() {
    }

    /** {@code ai.allowed}: {@code false} removes every AI feature. */
    public static boolean allowed() {
        return AdminPolicy.current().aiAllowed();
    }

    /** {@code ai.mcp.allowed}: {@code false} refuses {@code fxt-cli mcp}; {@code ai.allowed=false} implies it. */
    public static boolean mcpAllowed() {
        return allowed() && AdminPolicy.current().aiMcpAllowed();
    }

    /** The policy key that refuses the MCP server, for the message; {@code null} when it is allowed. */
    public static String mcpRefusedBy() {
        if (!allowed()) {
            return AdminPolicy.KEY_AI_ALLOWED;
        }
        return AdminPolicy.current().aiMcpAllowed() ? null : AdminPolicy.KEY_AI_MCP_ALLOWED;
    }
}
