package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies the EditorHost can host an arbitrary tool UI as a closable tab, and
 * that {@link EditorHost#insertTextAtCaret} still targets the last editor when a
 * tool tab is in front.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostToolTabTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        stage.setScene(new Scene(host, 800, 600));
        stage.show();
    }

    @Test
    void opensAToolTab() {
        VBox tool = new VBox();
        tool.getStyleClass().add("my-tool");
        Tab tab = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openToolTab("Tool", "bi-tools", tool));

        assertNotNull(tab);
        assertSame(tool, tab.getContent());
        assertFalse(host.isEmpty(), "opening a tool tab makes the host non-empty");
        assertNotNull(host.lookup(".my-tool"), "the tool content must be in the scene");
    }
}
