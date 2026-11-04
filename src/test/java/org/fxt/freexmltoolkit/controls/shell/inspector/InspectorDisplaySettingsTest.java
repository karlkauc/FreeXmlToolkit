package org.fxt.freexmltoolkit.controls.shell.inspector;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.v2.view.XsdDiagramOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The DISPLAY section on top of the Inspector: XSD only, bound to the shared diagram options. */
@ExtendWith(ApplicationExtension.class)
class InspectorDisplaySettingsTest {

    private EditorHost host;
    private InspectorPanel inspector;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        inspector = new InspectorPanel(host);
        stage.setScene(new Scene(new HBox(host, inspector), 1100, 700));
        stage.show();
    }

    @AfterEach
    void restore() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            XsdDiagramOptions.showConstraintsProperty().set(true);
            XsdDiagramOptions.showConstraintTargetsProperty().set(false);
        });
    }

    private Node section() {
        return inspector.lookup("#inspector-display-settings");
    }

    private void open(Path file, String content) throws Exception {
        Files.writeString(file, content);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(file));
    }

    @Test
    void shownForXsdHiddenForXml(@TempDir Path tmp) throws Exception {
        assertFalse(section().isVisible(), "no document");

        open(tmp.resolve("a.xml"), "<root/>");
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> host.getActiveDocument().isPresent());
        WaitForAsyncUtils.sleep(400, TimeUnit.MILLISECONDS);
        assertFalse(section().isVisible(), "XML document");

        open(tmp.resolve("s.xsd"), """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"><xs:element name="r"/></xs:schema>
                """);
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> section().isVisible() && section().isManaged());
    }

    @Test
    void checkBoxesFollowAndChangeTheOptions(@TempDir Path tmp) throws Exception {
        open(tmp.resolve("s.xsd"), """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"><xs:element name="r"/></xs:schema>
                """);
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> section().isVisible());
        CheckBox constraints = (CheckBox) inspector.lookup("#inspector-show-constraints");
        CheckBox targets = (CheckBox) inspector.lookup("#inspector-show-constraint-targets");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> XsdDiagramOptions.showConstraintTargetsProperty().set(false));
        assertFalse(targets.isSelected());

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> targets.setSelected(true));
        assertTrue(XsdDiagramOptions.showConstraintTargetsProperty().get(), "the check box sets the option");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> XsdDiagramOptions.showConstraintsProperty().set(false));
        assertFalse(constraints.isSelected(), "follows the option (e.g. the View menu)");
        assertTrue(targets.isDisabled(), "targets need constraints to be shown at all");
    }
}
