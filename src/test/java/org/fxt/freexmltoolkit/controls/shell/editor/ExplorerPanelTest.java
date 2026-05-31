package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies the Explorer "Open Editors" list stays consistent — and does not throw
 * the JavaFX ListViewBehavior crash — when documents open while a row is selected.
 */
@ExtendWith(ApplicationExtension.class)
class ExplorerPanelTest {

    private EditorHost host;
    private ExplorerPanel panel;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        panel = new ExplorerPanel(host);
        stage.setScene(new Scene(new HBox(host, panel), 1100, 600));
        stage.show();
    }

    @Test
    void openingDocumentsWhileAnEntryIsSelectedDoesNotCrash(@TempDir Path tmp) throws Exception {
        Path a = tmp.resolve("a.xml");
        Path b = tmp.resolve("b.xml");
        Files.writeString(a, "<a/>");
        Files.writeString(b, "<b/>");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(a));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> host.getOpenDocuments().size() == 1);

        // Select the first entry in the Open Editors list, then open another doc.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            openEditorsList().getSelectionModel().select(0);
            return null;
        });
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(b));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> host.getOpenDocuments().size() == 2);
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(2, openEditorsList().getItems().size(), "Open Editors list mirrors both documents");
    }

    @SuppressWarnings("unchecked")
    private ListView<OpenDocument> openEditorsList() {
        // The Open Editors list is the first .fxt-open-editors ListView in the panel.
        return (ListView<OpenDocument>) panel.lookupAll(".fxt-open-editors").stream()
                .filter(n -> n instanceof ListView).findFirst().orElseThrow();
    }
}
