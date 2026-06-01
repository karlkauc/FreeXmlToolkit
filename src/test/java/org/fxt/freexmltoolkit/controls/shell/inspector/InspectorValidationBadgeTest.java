package org.fxt.freexmltoolkit.controls.shell.inspector;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The inspector shows the document's validation result as a badge (Figma node
 * header "✓ Valid"), driven by the shared validation status on {@link EditorHost}.
 */
@ExtendWith(ApplicationExtension.class)
class InspectorValidationBadgeTest {

    private EditorHost host;
    private InspectorPanel inspector;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        inspector = new InspectorPanel(host);
        stage.setScene(new Scene(new HBox(host, inspector), 1000, 600));
        stage.show();
    }

    @Test
    void showsInvalidBadgeWithProblemCount() throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setValidationStatus(EditorHost.ValidationState.INVALID, 2, "2 problem(s)");
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        Label badge = badge();
        assertNotNull(badge, "the validation badge must exist");
        assertTrue(badge.isVisible(), "the badge must be visible when validated");
        assertTrue(badge.getText().contains("2 problem(s)"), "badge shows the problem summary: " + badge.getText());
    }

    @Test
    void showsValidBadge() throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setValidationStatus(EditorHost.ValidationState.VALID, 0, "Valid");
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        Label badge = badge();
        assertTrue(badge.isVisible());
        assertTrue(badge.getText().contains("Valid"), "badge shows the valid status: " + badge.getText());
    }

    @Test
    void hidesBadgeWhenNotValidated() throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setValidationStatus(EditorHost.ValidationState.NOT_VALIDATED, 0, "Not validated");
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(badge().isVisible(), "the badge is hidden until the document is validated");
    }

    private Label badge() {
        return (Label) inspector.lookup("#inspector-validation-badge");
    }
}
