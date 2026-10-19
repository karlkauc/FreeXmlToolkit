package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.test.FxThreadBudget;
import org.fxt.freexmltoolkit.test.LargeDocuments;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * UI-freeze regressions on a 5 MB XML document: typing, Format and switching to the Graphic view only
 * hand work to background threads, so their share of the FX thread stays small. Budgets are generous
 * for loaded CI machines; the former code took hundreds of ms to seconds for each of these.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostLargeDocumentBudgetTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        stage.setScene(new Scene(host, 1000, 700));
        stage.show();
    }

    private void openLargeXml(Path tmp) throws Exception {
        Path xml = tmp.resolve("large.xml");
        Files.writeString(xml, LargeDocuments.xml(5 * 1024 * 1024));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.length() > 5_000_000).orElse(false));
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void typingStaysOffTheFxThread(@TempDir Path tmp) throws Exception {
        openLargeXml(tmp);
        int[] offset = {100};
        FxThreadBudget.assertFast(30, 5, "a keystroke in a 5 MB document",
                () -> host.activeEditorView().getCodeArea().insertText(offset[0]++, "x"));
    }

    @Test
    void formatAndViewSwitchOnlyStartBackgroundWork(@TempDir Path tmp) throws Exception {
        openLargeXml(tmp);

        java.util.concurrent.CompletableFuture<?>[] format = new java.util.concurrent.CompletableFuture<?>[1];
        FxThreadBudget.assertFast(50, 1, "starting Format on a 5 MB document",
                () -> format[0] = host.formatActive());
        format[0].get(120, TimeUnit.SECONDS);

        // The first switch also loads the grid's classes and FXML; building every repeating table up
        // front used to add ~350 ms here, they are now built when a group is opened.
        FxThreadBudget.assertFast(150, 1, "starting the switch to the Graphic view",
                () -> host.setActiveViewMode(ViewMode.GRAPHIC));
        WaitForAsyncUtils.waitFor(120, TimeUnit.SECONDS,
                () -> host.activeViewModeProperty().get() == ViewMode.GRAPHIC);
        assertEquals(ViewMode.GRAPHIC, host.activeViewModeProperty().get(), "the switch completes once parsed");
    }
}
