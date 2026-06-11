package org.fxt.freexmltoolkit.controls.shell.inspector;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.ViewMode;
import org.fxt.freexmltoolkit.controls.shell.editor.XmlGridView;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Phase 1 (UI): the inspector shows the NAMESPACE section for elements, hides the editable Text box
 * for container (non-leaf) elements, and the attribute Add button commits a new attribute.
 */
@ExtendWith(ApplicationExtension.class)
class InspectorXmlElementEditTest {

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

    @Test
    void namespaceSectionAttributeAddAndLeafOnlyText(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("data.xml");
        Files.writeString(xml, "<root>\n  <leaf id=\"1\">v</leaf>\n  <box><c/></box>\n</root>\n");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("leaf")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.GRAPHIC);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        XmlGridView grid = (XmlGridView) host.lookupAll("*").stream()
                .filter(n -> n instanceof XmlGridView).findFirst().orElseThrow();

        // Leaf element: NAMESPACE section + editable Text are shown. Poll the
        // attribute count as part of the combined condition — it is a sibling
        // effect of the same populate pass (asserting it right away is racy).
        selectChild(grid, "leaf");
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS,
                () -> "leaf".equals(inspector.getNodeNameText())
                        && inspector.isNamespaceSectionVisible() && inspector.isXmlTextVisible()
                        && inspector.getXmlAttributeCount() == 1);
        assertEquals(1, inspector.getXmlAttributeCount());

        // Add an attribute via the inspector's Add row.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            ((TextField) inspector.lookup("#inspector-xml-attr-name")).setText("lang");
            ((TextField) inspector.lookup("#inspector-xml-attr-value")).setText("en");
            ((Button) inspector.lookup("#inspector-xml-attr-add")).fire();
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS,
                () -> host.getActiveText().orElse("").contains("lang=\"en\""));

        // Container element: the editable Text box is hidden (mixed content not allowed).
        selectChild(grid, "box");
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS,
                () -> "box".equals(inspector.getNodeNameText()) && !inspector.isXmlTextVisible());
        assertFalse(inspector.isXmlTextVisible(), "container elements have no editable Text box");
    }

    private void selectChild(XmlGridView grid, String name) {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            XmlElement el = grid.getContext().getDocument().getRootElement().getChildElements(name).get(0);
            grid.getContext().getSelectionModel().setSelectedNode(el);
            return null;
        });
    }
}
