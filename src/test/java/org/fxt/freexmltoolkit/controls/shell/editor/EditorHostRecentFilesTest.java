package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import javafx.stage.Stage;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

@ExtendWith(ApplicationExtension.class)
class EditorHostRecentFilesTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        ServiceRegistry.initialize();
        host = new EditorHost();
    }

    @Test
    void openingAFileRecordsItInRecentFiles(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("recent-probe.xml");
        Files.writeString(xml, "<root/>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitForFxEvents();

        PropertiesService props = ServiceRegistry.get(PropertiesService.class);
        boolean present = props.getLastOpenFiles().stream()
                .anyMatch(f -> f.getName().equals("recent-probe.xml"));
        assertTrue(present, "opened file should be recorded in recent files");
    }
}
