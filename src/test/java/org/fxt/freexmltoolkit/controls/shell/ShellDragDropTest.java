package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

@ExtendWith(ApplicationExtension.class)
class ShellDragDropTest {

    private UnifiedShellView shell;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        shell = WaitForAsyncUtils.waitForAsyncFx(3000, UnifiedShellView::new);
    }

    @Test
    void acceptsXmlFamilyFilesOnly() {
        assertTrue(UnifiedShellView.acceptsDrop(List.of(new File("a.xml"))));
        assertTrue(UnifiedShellView.acceptsDrop(List.of(new File("a.xsd"))));
        assertFalse(UnifiedShellView.acceptsDrop(List.of(new File("a.png"))));
        assertFalse(UnifiedShellView.acceptsDrop(List.of()));
    }

    @Test
    void openDroppedFilesOpensSupportedFiles(@TempDir Path tmp) throws Exception {
        File xml = tmp.resolve("dropped.xml").toFile();
        Files.writeString(xml.toPath(), "<root/>");
        File png = tmp.resolve("ignored.png").toFile();
        Files.writeString(png.toPath(), "x");

        int opened = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> shell.openDroppedFiles(List.of(xml, png)));
        assertEquals(1, opened, "only the XML file is opened");
    }
}
