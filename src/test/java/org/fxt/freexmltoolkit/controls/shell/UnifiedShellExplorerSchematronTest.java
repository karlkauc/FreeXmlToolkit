package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.ExplorerPanel;
import org.fxt.freexmltoolkit.controls.shell.editor.ValidationPanel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies the Explorer's one-click Schematron validation end to end: picking a
 * Schematron and hitting Validate surfaces the rule violations of the active
 * document in the Validation activity (single-file flow).
 */
@ExtendWith(ApplicationExtension.class)
class UnifiedShellExplorerSchematronTest {

    private static final String SCHEMATRON = """
            <sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron">
              <sch:pattern><sch:rule context="root">
                <sch:assert test="name">root must have a name child</sch:assert>
              </sch:rule></sch:pattern>
            </sch:schema>
            """;

    private UnifiedShellView shell;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.service.PropertiesServiceImpl.getInstance()
                .clearRecentSchematronFiles();
        shell = new UnifiedShellView();
        stage.setScene(new Scene(shell, 1100, 720));
        stage.show();
    }

    @Test
    void explorerValidateShowsSchematronProblemsOfTheActiveDocument(@TempDir Path tmp) throws Exception {
        Path sch = tmp.resolve("rules.sch");
        Files.writeString(sch, SCHEMATRON);
        Path xml = tmp.resolve("bad.xml");
        Files.writeString(xml, "<root/>"); // violates the rule: no <name> child

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> shell.getEditorHost().openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> shell.getEditorHost().getActiveText().map(t -> t.contains("root")).orElse(false));

        ExplorerPanel explorer = explorerPanel();
        assertNotNull(explorer, "the Explorer activity is the default side panel");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            explorer.useSchematron(sch.toFile());
            ((Button) explorer.lookup("#explorer-validate")).fire();
            return null;
        });

        WaitForAsyncUtils.waitFor(6, TimeUnit.SECONDS, () -> validationPanel() != null
                && validationPanel().getProblemCount() > 0);
        assertTrue(validationPanel().getProblemCount() > 0,
                "the Schematron violation must show up in the Validation panel");
    }

    private ExplorerPanel explorerPanel() {
        return WaitForAsyncUtils.waitForAsyncFx(2000, () -> shell.lookupAll("*").stream()
                .filter(n -> n instanceof ExplorerPanel)
                .map(n -> (ExplorerPanel) n)
                .findFirst().orElse(null));
    }

    private ValidationPanel validationPanel() {
        // lookupAll must run on the FX thread (scene graph may still be mutating)
        return WaitForAsyncUtils.waitForAsyncFx(2000, () -> shell.lookupAll("*").stream()
                .filter(n -> n instanceof ValidationPanel)
                .map(n -> (ValidationPanel) n)
                .findFirst().orElse(null));
    }
}
