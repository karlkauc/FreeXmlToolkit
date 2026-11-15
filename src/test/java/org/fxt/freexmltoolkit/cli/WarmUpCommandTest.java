package org.fxt.freexmltoolkit.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@code fxt-cli warm-up}: opens every file of an installation, never fails, stays out of the help. */
class WarmUpCommandTest {

    @Test
    void opensEveryFileBelowTheFolder(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("FreeXmlToolkit.cfg"), "x");
        Files.createDirectories(tmp.resolve("app/lib"));
        Files.write(tmp.resolve("app/lib/a.jar"), new byte[]{1, 2, 3});
        Files.write(tmp.resolve("app/lib/empty.jar"), new byte[0]);

        var result = WarmUpCommand.warm(tmp, Duration.ofSeconds(30));

        assertEquals(3, result.files());
        assertEquals(3, result.opened());
        assertTrue(result.complete());
    }

    @Test
    void stopsAtTheTimeLimitWithoutFailing(@TempDir Path tmp) throws Exception {
        for (int i = 0; i < 50; i++) {
            Files.writeString(tmp.resolve("f" + i), "x");
        }
        var result = WarmUpCommand.warm(tmp, Duration.ZERO);
        assertEquals(50, result.files());
        assertFalse(result.complete());
    }

    @Test
    void alwaysExitsWithZeroAndIsHidden(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("a.txt"), "x");
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        var commandLine = FxtCli.newCommandLine(new PrintWriter(out, true), new PrintWriter(err, true));

        assertEquals(ExitCodes.OK, commandLine.execute("warm-up", tmp.toString()));
        assertTrue(out.toString().startsWith("Opened 1 of 1 files"), out.toString());
        assertEquals(ExitCodes.OK, commandLine.execute("warm-up", tmp.resolve("missing").toString()));

        StringWriter usage = new StringWriter();
        commandLine.usage(new PrintWriter(usage, true));
        assertFalse(usage.toString().contains("warm-up"), "an installer step, not a command for users");
    }

    @Test
    void theInstallationRootIsTheLaunchersFolder(@TempDir Path tmp) {
        String old = System.getProperty("jpackage.app-path");
        try {
            System.setProperty("jpackage.app-path", tmp.resolve("fxt-cli.exe").toString());
            assertEquals(tmp.toAbsolutePath(), WarmUpCommand.installationRoot());
            System.setProperty("jpackage.app-path", tmp.resolve("bin/fxt-cli").toString());
            assertEquals(tmp.toAbsolutePath(), WarmUpCommand.installationRoot(), "Linux: launchers in bin/");
        } finally {
            if (old == null) {
                System.clearProperty("jpackage.app-path");
            } else {
                System.setProperty("jpackage.app-path", old);
            }
        }
    }
}
