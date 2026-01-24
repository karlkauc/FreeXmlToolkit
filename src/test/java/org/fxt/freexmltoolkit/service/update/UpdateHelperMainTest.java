package org.fxt.freexmltoolkit.service.update;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UpdateHelperMainTest {

    @Test
    void findUpdateAppDirLocatesLauncherInNestedStructure() throws Exception {
        Path tempDir = Files.createTempDirectory("update-helper-find");
        Path nested = tempDir.resolve("FreeXmlToolkit");
        Files.createDirectories(nested.resolve("app"));
        Path launcher = nested.resolve("FreeXmlToolkit.exe");
        Files.writeString(launcher, "stub");

        Path logFile = tempDir.resolve("helper.log");
        UpdateHelperMain.UpdateHelperLogger logger = new UpdateHelperMain.UpdateHelperLogger(logFile);

        Path result = UpdateHelperMain.findUpdateAppDir(tempDir, "FreeXmlToolkit.exe", logger);
        assertEquals(nested, result);
    }
}
