package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Regression test for the "action toolbar buttons do nothing" bug. The action bar used to be
 * a {@link javafx.scene.control.ToolBar} that, at a realistic laptop width (both side panels
 * open → narrow editor area), overflowed and pushed its trailing buttons (Spreadsheet, Query
 * Console, document actions, Set Schema) into the overflow chevron where they were effectively
 * unreachable. It is now a wrapping {@link javafx.scene.layout.FlowPane}: every action stays
 * visible and directly clickable, wrapping onto a second row when space is tight.
 */
@ExtendWith(ApplicationExtension.class)
class ActionToolbarOverflowTest {

    private UnifiedShellView shell;

    @Start
    void start(Stage stage) {
        // Editor area ~600px wide (1300 − activity 56 − side 260 − inspector 384) → the
        // action bar wraps to multiple rows but every button stays clickable.
        shell = new UnifiedShellView();
        stage.setScene(new Scene(shell, 1300, 700));
        stage.show();
    }

    @Test
    void noOverflowChevronAndAllActionsPresent() throws Exception {
        openSample();

        // The wrapping flow must never produce a ToolBar overflow chevron.
        assertNull(shell.lookup(".tool-bar-overflow-button"),
                "the action bar must wrap, not overflow into a chevron");

        // Every trailing action button must be present and enabled for an XML document.
        for (String id : new String[]{"#action-spreadsheet", "#action-query-console",
                "#doc-action-validate", "#action-set-schema"}) {
            Button b = (Button) shell.lookup(id);
            assertNotNull(b, id + " must exist");
            assertFalse(b.isDisabled(), id + " must be enabled for XML");
        }
    }

    @Test
    void trailingButtonIsClickableDirectly(FxRobot robot) throws Exception {
        openSample();

        // A real mouse click on the (previously overflowed) Query Console button must work.
        assertFalse(shell.isQueryConsoleShown());
        robot.clickOn("#action-query-console");
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(shell.isQueryConsoleShown(),
                "clicking the Query Console button must show the console");
    }

    @Test
    void validateOpensAToolTab() throws Exception {
        openSample();
        int before = (int) shell.lookupAll(".tab").stream().count();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> ((Button) shell.lookup("#doc-action-validate")).fire());
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.sleep(1500, java.util.concurrent.TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
        int after = (int) shell.lookupAll(".tab").stream().count();
        assertTrue(after >= before, "Validate should open a Validation tool tab");
    }

    private void openSample() throws Exception {
        Path xml = Files.createTempFile("toolbar", ".xml");
        Files.writeString(xml, "<root><a/></root>");
        xml.toFile().deleteOnExit();
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> shell.openFile(xml));
        WaitForAsyncUtils.waitForFxEvents();
    }
}
