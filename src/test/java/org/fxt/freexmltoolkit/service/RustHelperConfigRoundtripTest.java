package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RustHelperConfigRoundtripTest {

    @Test
    void javaConfigParsesInRustHelper(@TempDir Path tmp) throws Exception {
        Path helperBinary = locateRustHelper();
        Assumptions.assumeTrue(Files.exists(helperBinary),
            "Rust helper not built — skip. Run `cd update-helper && cargo build --release` first.");

        Path configFile = tmp.resolve("helper-config.toml");
        Path extractedDir = tmp.resolve("extract");
        Path appDir = tmp.resolve("app");
        Path launcher = appDir.resolve("FreeXmlToolkit");
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

        ProcessBuilder pb = new ProcessBuilder(
            helperBinary.toString(), "--validate-config", configFile.toString())
            .redirectErrorStream(true);
        Process p = pb.start();
        boolean done = p.waitFor(10, TimeUnit.SECONDS);
        if (!done) {
            p.destroyForcibly();
            throw new AssertionError("Helper --validate-config timed out");
        }
        String output = new String(p.getInputStream().readAllBytes());
        assertEquals(0, p.exitValue(),
            "Rust helper rejected Java-written config:\n" + output);
    }

    private Path locateRustHelper() {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String binName = isWindows ? "fxt-update-helper.exe" : "fxt-update-helper";
        return Path.of("update-helper", "target", "release", binName);
    }
}
