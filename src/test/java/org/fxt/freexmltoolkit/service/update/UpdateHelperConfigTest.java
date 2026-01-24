package org.fxt.freexmltoolkit.service.update;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UpdateHelperConfigTest {

    @Test
    void loadConfigParsesRequiredFields() throws Exception {
        Path tempDir = Files.createTempDirectory("update-helper-test");
        Path configPath = tempDir.resolve("config.properties");

        Properties properties = new Properties();
        properties.setProperty("appDir", tempDir.resolve("app").toString());
        properties.setProperty("updateDir", tempDir.resolve("update").toString());
        properties.setProperty("launcher", tempDir.resolve("FreeXmlToolkit.exe").toString());
        properties.setProperty("logFile", tempDir.resolve("helper.log").toString());
        properties.setProperty("platform", "windows");
        properties.setProperty("processName", "FreeXmlToolkit.exe");

        try (OutputStream out = Files.newOutputStream(configPath)) {
            properties.store(out, "test");
        }

        UpdateHelperConfig config = UpdateHelperConfig.load(configPath);
        assertNotNull(config);
        assertEquals("windows", config.getPlatform());
        assertEquals("FreeXmlToolkit.exe", config.getProcessName());
    }

    @Test
    void loadConfigFailsWhenMissingRequiredKey() throws Exception {
        Path tempDir = Files.createTempDirectory("update-helper-test-missing");
        Path configPath = tempDir.resolve("config.properties");

        Properties properties = new Properties();
        properties.setProperty("appDir", tempDir.resolve("app").toString());
        properties.setProperty("updateDir", tempDir.resolve("update").toString());

        try (OutputStream out = Files.newOutputStream(configPath)) {
            properties.store(out, "test");
        }

        assertThrows(IOException.class, () -> UpdateHelperConfig.load(configPath));
    }
}
