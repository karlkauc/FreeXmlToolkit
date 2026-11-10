package org.fxt.freexmltoolkit.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.fxt.freexmltoolkit.service.AdminPolicyTestAccess;
import org.fxt.freexmltoolkit.service.ai.tool.ToolRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@code fxt-cli mcp}: options and policy; the protocol itself is covered by {@code McpToolServerTest}. */
class FxtCliMcpTest {

    @TempDir
    Path dir;

    private String out;
    private String err;

    @AfterEach
    void resetPolicy() {
        AdminPolicyTestAccess.reset();
    }

    private int run(String... args) {
        StringWriter outText = new StringWriter();
        StringWriter errText = new StringWriter();
        int code = FxtCli.newCommandLine(new PrintWriter(outText, true), new PrintWriter(errText, true)).execute(args);
        out = outText.toString();
        err = errText.toString();
        return code;
    }

    @Test
    void listToolsPrintsEveryToolAndExits() {
        assertEquals(ExitCodes.OK, run("mcp", "--list-tools"), err);
        for (var tool : ToolRegistry.standard().tools()) {
            assertTrue(out.contains(tool.name()), out);
        }
    }

    @Test
    void listToolsHonoursTheSubset() {
        assertEquals(ExitCodes.OK, run("mcp", "--tools", "validate,xpath", "--list-tools"), err);
        assertTrue(out.contains("validate") && out.contains("xpath"), out);
        assertFalse(out.contains("xslt_transform"), out);
    }

    @Test
    void anUnknownToolIsAUsageErrorNamingTheValidOnes() {
        assertEquals(ExitCodes.USAGE, run("mcp", "--tools", "nope", "--list-tools"));
        assertTrue(err.contains("nope") && err.contains("validate"), err);
    }

    @Test
    void aMissingRootIsAUsageError() {
        assertEquals(ExitCodes.USAGE, run("mcp", "--root", dir.resolve("missing").toString()));
        assertTrue(err.contains("missing"), err);
    }

    @Test
    void thePolicyRefusesTheServerBeforeReadingStdin() {
        assertRefused("ai.mcp.allowed");
        assertRefused("ai.allowed");
    }

    private void assertRefused(String key) {
        Properties locked = new Properties();
        locked.setProperty(key, "false");
        AdminPolicyTestAccess.setCurrent(dir.resolve("policy.properties"), locked, new Properties());
        InputStream realIn = System.in;
        AtomicBoolean read = new AtomicBoolean();
        System.setIn(new InputStream() {
            @Override
            public int read() {
                read.set(true);
                return -1;
            }
        });
        try {
            assertEquals(ExitCodes.USAGE, run("mcp", "--root", dir.toString()), key);
            assertTrue(err.contains(key), err);
            assertFalse(read.get(), "stdin was read");
        } finally {
            System.setIn(realIn);
            AdminPolicyTestAccess.reset();
        }
    }
}
