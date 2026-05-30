package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Schema side panel exposes the schema actions, including the new
 * "Generate Documentation" export. (The dialog-driven flow itself is modal and
 * exercised manually; {@link DocumentationRunnerTest} covers the export logic.)
 */
@ExtendWith(ApplicationExtension.class)
class TypeLibraryPanelTest {

    private TypeLibraryPanel panel;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        panel = new TypeLibraryPanel(new EditorHost());
        stage.setScene(new Scene(panel, 320, 600));
        stage.show();
    }

    @Test
    void exposesTheGenerateDocumentationAction() {
        WaitForAsyncUtils.waitForFxEvents();
        boolean present = panel.lookupAll(".button").stream()
                .anyMatch(n -> n instanceof Button b && "Generate Documentation".equals(b.getText()));
        assertTrue(present, "Schema panel must offer the 'Generate Documentation' action");
    }
}
