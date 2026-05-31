package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

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
        assertTrue(hasButton("Generate Documentation"),
                "Schema panel must offer the 'Generate Documentation' action");
    }

    @Test
    void exposesTheGenerateSampleXmlAction() {
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(hasButton("Generate Sample XML"),
                "Schema panel must offer the 'Generate Sample XML' action");
    }

    private boolean hasButton(String text) {
        return panel.lookupAll(".button").stream()
                .anyMatch(n -> n instanceof Button b && text.equals(b.getText()));
    }
}
