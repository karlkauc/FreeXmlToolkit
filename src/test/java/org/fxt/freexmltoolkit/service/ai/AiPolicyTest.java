package org.fxt.freexmltoolkit.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Properties;

import org.fxt.freexmltoolkit.service.AdminPolicyTestAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiPolicyTest {

    @TempDir
    Path dir;

    @AfterEach
    void reset() {
        AdminPolicyTestAccess.reset();
    }

    private void policy(Properties locked, Properties defaults) {
        AdminPolicyTestAccess.setCurrent(dir.resolve("policy.properties"), locked, defaults);
    }

    private static Properties props(String... keyValues) {
        Properties p = new Properties();
        for (int i = 0; i < keyValues.length; i += 2) {
            p.setProperty(keyValues[i], keyValues[i + 1]);
        }
        return p;
    }

    @Test
    void everythingAllowedByDefault() {
        policy(new Properties(), new Properties());
        assertTrue(AiPolicy.allowed());
        assertTrue(AiPolicy.mcpAllowed());
        assertTrue(AiPolicy.remoteAllowed());
        assertNull(AiPolicy.mcpRefusedBy());
    }

    @Test
    void aiAllowedFalseImpliesTheOthers() {
        policy(props("ai.allowed", "false"), new Properties());
        assertFalse(AiPolicy.allowed());
        assertFalse(AiPolicy.mcpAllowed());
        assertFalse(AiPolicy.remoteAllowed());
        assertEquals("ai.allowed", AiPolicy.mcpRefusedBy());
    }

    @Test
    void remoteAloneCanBeSwitchedOff() {
        policy(props("ai.remote.allowed", "false"), new Properties());
        assertTrue(AiPolicy.allowed());
        assertTrue(AiPolicy.mcpAllowed());
        assertFalse(AiPolicy.remoteAllowed());
    }

    @Test
    void defaultsDoNotCountForPolicyOnlyKeys() {
        policy(new Properties(), props("ai.allowed", "false", "ai.remote.allowed", "false"));
        assertTrue(AiPolicy.allowed());
        assertTrue(AiPolicy.remoteAllowed());
    }
}
