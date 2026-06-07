package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.control.Dialog;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

@ExtendWith(ApplicationExtension.class)
class HelpDialogsTest {

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
    }

    @Test
    void aboutDialogBuildsWithVersionAndRuntimeInfo() {
        Dialog<Void> dialog = WaitForAsyncUtils.waitForAsyncFx(3000, () -> AboutDialog.build(null));
        assertNotNull(dialog.getDialogPane().getContent(), "about content");
        // The info grid embeds the Java runtime version (from System.getProperty).
        String text = dialogText(dialog);
        assertTrue(text.contains(System.getProperty("java.version")), "shows java version");
    }

    @Test
    void shortcutsDialogBuilds() {
        Dialog<?> dialog = WaitForAsyncUtils.waitForAsyncFx(3000, () -> KeyboardShortcutsDialog.build());
        assertNotNull(dialog.getDialogPane(), "shortcuts dialog pane");
    }

    private static String dialogText(Dialog<?> dialog) {
        StringBuilder sb = new StringBuilder();
        collect(dialog.getDialogPane().getContent(), sb);
        return sb.toString();
    }

    private static void collect(javafx.scene.Node node, StringBuilder sb) {
        if (node instanceof javafx.scene.control.Label l) {
            sb.append(l.getText()).append('\n');
        }
        if (node instanceof javafx.scene.Parent p) {
            for (javafx.scene.Node child : p.getChildrenUnmodifiable()) {
                collect(child, sb);
            }
        }
    }
}
