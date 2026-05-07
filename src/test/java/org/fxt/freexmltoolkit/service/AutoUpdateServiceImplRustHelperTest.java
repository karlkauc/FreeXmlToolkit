package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AutoUpdateServiceImplRustHelperTest {

    @Test
    void writeRustHelperConfig_emitsValidToml(@TempDir Path tmp) throws Exception {
        Path configFile = tmp.resolve("helper-config.toml");
        Path extractedDir = tmp.resolve("extract");
        Path appDir = tmp.resolve("app");
        Path launcher = appDir.resolve("FreeXmlToolkit.exe");
        Path helperLog = tmp.resolve("helper.log");

        Files.createDirectories(extractedDir);
        Files.createDirectories(appDir);
        Files.createFile(launcher);

        AutoUpdateServiceImpl impl = (AutoUpdateServiceImpl) AutoUpdateServiceImpl.getInstance();

        Method m = AutoUpdateServiceImpl.class.getDeclaredMethod(
            "writeRustHelperConfig",
            Path.class, Path.class, Path.class, Path.class, Path.class);
        m.setAccessible(true);
        m.invoke(impl, configFile, extractedDir, appDir, launcher, helperLog);

        String toml = Files.readString(configFile);

        // Must contain all required keys
        for (String key : List.of(
                "schema_version = 1",
                "parent_pid =",
                "parent_creation_time =",
                "extracted_dir =",
                "install_dir =",
                "launcher_path =",
                "log_path =",
                "old_version =",
                "new_version =")) {
            assertTrue(toml.contains(key), "missing key: " + key + "\nTOML:\n" + toml);
        }

        // Paths must be single-quoted (TOML literal strings)
        assertTrue(toml.contains("'" + extractedDir.toAbsolutePath() + "'"),
            "extracted_dir must be single-quoted: " + toml);
    }

    @Test
    void copyTreeReplaceExisting_overwritesExistingFiles(@TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("src");
        Path target = tmp.resolve("dst");
        Files.createDirectories(source.resolve("nested"));
        Files.writeString(source.resolve("a.txt"), "new-a");
        Files.writeString(source.resolve("nested/b.txt"), "new-b");

        Files.createDirectories(target.resolve("nested"));
        Files.writeString(target.resolve("a.txt"), "old-a");
        Files.writeString(target.resolve("nested/b.txt"), "old-b");

        AutoUpdateServiceImpl impl = (AutoUpdateServiceImpl) AutoUpdateServiceImpl.getInstance();
        Method m = AutoUpdateServiceImpl.class.getDeclaredMethod(
            "copyTreeReplaceExisting", Path.class, Path.class);
        m.setAccessible(true);
        m.invoke(impl, source, target);

        assertEquals("new-a", Files.readString(target.resolve("a.txt")));
        assertEquals("new-b", Files.readString(target.resolve("nested/b.txt")));
    }

    @Test
    void currentProcessFiletime_isPositiveAndPlausible() throws Exception {
        AutoUpdateServiceImpl impl = (AutoUpdateServiceImpl) AutoUpdateServiceImpl.getInstance();
        Method m = AutoUpdateServiceImpl.class.getDeclaredMethod("currentProcessFiletime");
        m.setAccessible(true);
        long ft = (long) m.invoke(impl);

        // FILETIME for current millennium is in the range ~1.32e17 to 1.34e17
        if (ft != 0L) {
            assertTrue(ft > 130_000_000_000_000_000L, "FILETIME too small: " + ft);
            assertTrue(ft < 140_000_000_000_000_000L, "FILETIME too large: " + ft);
        }
    }
}
