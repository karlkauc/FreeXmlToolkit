package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.ValidationPanel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies the editor toolbar "Validate" action: it validates the active XML for
 * structural well-formedness when no XSD is bound, surfacing the result in the
 * Validation activity.
 */
@ExtendWith(ApplicationExtension.class)
class UnifiedShellValidateTest {

    private UnifiedShellView shell;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        shell = new UnifiedShellView();
        stage.setScene(new Scene(shell, 1100, 720));
        stage.show();
    }

    @Test
    void editorValidateReportsStructuralProblemWhenNoXsd(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("malformed.xml");
        Files.writeString(xml, "<root><a></root>"); // not well-formed (mismatched tags)

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> shell.getEditorHost().openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> shell.getEditorHost().getActiveText().map(t -> t.contains("root")).orElse(false));

        clickToolbarValidate();

        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> validationPanel() != null
                && validationPanel().getProblemCount() > 0);
        assertTrue(validationPanel().getProblemCount() > 0,
                "a not-well-formed XML must report a structural problem even without an XSD");
    }

    @Test
    void editorValidateReportsNoProblemsForWellFormedXmlWithoutXsd(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("ok.xml");
        Files.writeString(xml, "<root><a>x</a></root>");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> shell.getEditorHost().openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> shell.getEditorHost().getActiveText().map(t -> t.contains("root")).orElse(false));

        clickToolbarValidate();

        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> validationPanel() != null
                && "Well-formed".equals(validationPanel().getStatusText()));
        assertEquals(0, validationPanel().getProblemCount(),
                "a well-formed XML without an XSD must report no problems");
        assertEquals("Well-formed", validationPanel().getStatusText());
    }

    private void clickToolbarValidate() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            Button validate = shell.lookupAll(".button").stream()
                    .filter(n -> n instanceof Button b && "Validate".equals(b.getText()))
                    .map(n -> (Button) n).findFirst().orElseThrow();
            validate.fire();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private ValidationPanel validationPanel() {
        return shell.lookupAll("*").stream()
                .filter(n -> n instanceof ValidationPanel)
                .map(n -> (ValidationPanel) n)
                .findFirst().orElse(null);
    }
}
