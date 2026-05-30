package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for the editor empty-state landing: it offers quick "new document"
 * actions, an "open file" action, and a clickable list of recent files —
 * wiring parity for the welcome dashboard that the shell-boot replaces.
 */
@ExtendWith(ApplicationExtension.class)
class EditorWelcomePaneTest {

    private final AtomicReference<EditorFileType> newType = new AtomicReference<>();
    private final AtomicReference<Boolean> openCalled = new AtomicReference<>(false);
    private final AtomicReference<File> openedRecent = new AtomicReference<>();
    private EditorWelcomePane pane;

    @Start
    void start(Stage stage) {
        pane = new EditorWelcomePane(newType::set, () -> openCalled.set(true), openedRecent::set);
        stage.setScene(new Scene(pane, 800, 600));
        stage.show();
    }

    @Test
    void carriesTheEmptyStateStyleClass() {
        assertTrue(pane.getStyleClass().contains("fxt-editor-empty-state"));
    }

    @Test
    void newXmlButtonInvokesTheNewCallback() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            findButton(pane, "New XML").fire();
            return null;
        });
        assertEquals(EditorFileType.XML, newType.get());
    }

    @Test
    void openButtonInvokesTheOpenCallback() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            findButton(pane, "Open File…").fire();
            return null;
        });
        assertTrue(openCalled.get());
    }

    @Test
    void clickingARecentEntryOpensIt() {
        File recent = new File("/tmp/sample.xml");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            pane.setRecentFiles(List.of(recent));
            return null;
        });
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            @SuppressWarnings("unchecked")
            ListView<File> list = (ListView<File>) pane.lookup(".fxt-welcome-recent");
            list.getSelectionModel().select(recent);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(recent, openedRecent.get());
    }

    private static Button findButton(Node root, String text) {
        return root.lookupAll(".button").stream()
                .filter(n -> n instanceof Button b && text.equals(b.getText()))
                .map(n -> (Button) n)
                .findFirst()
                .orElseThrow(() -> new AssertionError("button not found: " + text));
    }
}
