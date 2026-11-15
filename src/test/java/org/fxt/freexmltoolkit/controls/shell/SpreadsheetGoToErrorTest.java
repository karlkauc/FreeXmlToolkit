package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** "Go to Error" after a failed spreadsheet export shows the exported document at the parser's line and column. */
@ExtendWith(ApplicationExtension.class)
class SpreadsheetGoToErrorTest {

    private UnifiedShellView shell;

    @Start
    void start(Stage stage) {
        shell = new UnifiedShellView();
        stage.setScene(new Scene(shell, 1000, 700));
        stage.show();
    }

    @Test
    void goToErrorSelectsTheExportedDocumentAndMovesTheCaret(@TempDir Path tmp) throws Exception {
        Path broken = Files.writeString(tmp.resolve("broken.xml"), "<root>\n  <a>\n  <b/>\n</root>");
        Path other = Files.writeString(tmp.resolve("other.xml"), "<other/>");
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> shell.openFile(broken));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> shell.getEditorHost().getActiveText().map(t -> t.contains("<b/>")).orElse(false)));
        var exported = WaitForAsyncUtils.waitForAsyncFx(2000, () -> shell.getEditorHost().getActiveDocument().orElseThrow());
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> shell.openFile(other));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> shell.getEditorHost().getActiveText().map(t -> t.contains("<other/>")).orElse(false)));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            shell.goToConversionError(exported, 4, 3);
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> shell.getEditorHost().getActiveCaretLineColumn()[0] == 4));
        assertSame(exported, WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> shell.getEditorHost().getActiveDocument().orElseThrow()), "the exported document is shown");
        int[] caret = WaitForAsyncUtils.waitForAsyncFx(2000, () -> shell.getEditorHost().getActiveCaretLineColumn());
        assertEquals(4, caret[0]);
        assertEquals(3, caret[1]);
    }
}
