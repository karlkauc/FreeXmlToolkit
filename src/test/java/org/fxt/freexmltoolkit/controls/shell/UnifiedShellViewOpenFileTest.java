package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies the MainController→shell open-file bridge at the view level: calling
 * {@link UnifiedShellView#openFile} opens the file as a document in the shell's
 * editor host (used so legacy file routing — e.g. .sch files — can hand a file
 * to the shell after a legacy editor tab is retired).
 */
@ExtendWith(ApplicationExtension.class)
class UnifiedShellViewOpenFileTest {

    private UnifiedShellView shell;

    @Start
    void start(Stage stage) {
        shell = new UnifiedShellView();
        stage.setScene(new Scene(shell, 1000, 700));
        stage.show();
    }

    @Test
    void openFileOpensADocumentInTheEditorHost() throws Exception {
        Path xml = Files.createTempFile("shell-open", ".xml");
        Files.writeString(xml, "<root><child>x</child></root>");
        xml.toFile().deleteOnExit();

        assertTrue(shell.getEditorHost().isEmpty(), "no documents before open");

        WaitForAsyncUtils.waitForAsyncFx(3000, () -> shell.openFile(xml));
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(shell.getEditorHost().isEmpty(), "the file must be open in the shell");
        assertTrue(shell.getEditorHost().getActiveDocument()
                        .map(d -> d.getPath() != null && d.getPath().equals(xml)).orElse(false),
                "the opened file must be the active document");
    }
}
