package org.fxt.freexmltoolkit.service.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class UpdateHelperConfig {
    private final Path appDir;
    private final Path updateDir;
    private final Path launcher;
    private final Path logFile;
    private final String platform;
    private final String processName;

    private UpdateHelperConfig(Path appDir, Path updateDir, Path launcher, Path logFile,
                               String platform, String processName) {
        this.appDir = appDir;
        this.updateDir = updateDir;
        this.launcher = launcher;
        this.logFile = logFile;
        this.platform = platform;
        this.processName = processName;
    }

    public static UpdateHelperConfig load(Path configPath) throws IOException {
        if (configPath == null || !Files.exists(configPath)) {
            throw new IOException("Config file not found: " + configPath);
        }

        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(configPath)) {
            properties.load(in);
        }

        Path appDir = getPath(properties, "appDir");
        Path updateDir = getPath(properties, "updateDir");
        Path launcher = getPath(properties, "launcher");
        Path logFile = getPath(properties, "logFile");
        String platform = getRequired(properties, "platform");
        String processName = getRequired(properties, "processName");

        return new UpdateHelperConfig(appDir, updateDir, launcher, logFile, platform, processName);
    }

    private static Path getPath(Properties properties, String key) throws IOException {
        String value = getRequired(properties, key);
        return Path.of(value);
    }

    private static String getRequired(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IOException("Missing required config key: " + key);
        }
        return value.trim();
    }

    public Path getAppDir() {
        return appDir;
    }

    public Path getUpdateDir() {
        return updateDir;
    }

    public Path getLauncher() {
        return launcher;
    }

    public Path getLogFile() {
        return logFile;
    }

    public String getPlatform() {
        return platform;
    }

    public String getProcessName() {
        return processName;
    }
}
