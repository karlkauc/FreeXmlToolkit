package org.fxt.freexmltoolkit.controls.shell.inspector;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
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
 * Phase 2 (UI): selecting an XML-instance node in the Grid populates the inspector with
 * the node's name and its attributes (VALUE &amp; ATTRIBUTES section).
 */
@ExtendWith(ApplicationExtension.class)
class InspectorXmlNodeTest {

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
    void selectingXmlElementShowsNameAndAttributes(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("data.xml");
        Files.writeString(xml, "<root>\n  <item x=\"1\" y=\"2\">hello</item>\n</root>\n");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("item")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.GRID);
            return null;
        });
        // Let the grid finish building (it may auto-select the root) before selecting our node.
        WaitForAsyncUtils.waitForFxEvents();
        Thread.sleep(400);

        XmlGridView grid = (XmlGridView) host.lookupAll("*").stream()
                .filter(n -> n instanceof XmlGridView).findFirst().orElseThrow();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            XmlElement item = grid.getContext().getDocument().getRootElement().getChildElements("item").get(0);
            grid.getContext().getSelectionModel().setSelectedNode(item);
            return null;
        });

        // Wait for both name and attributes to settle (robust against a transient root selection).
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS,
                () -> "item".equals(inspector.getNodeNameText()) && inspector.getXmlAttributeCount() == 2);
        assertEquals("item", inspector.getNodeNameText());
        assertEquals(2, inspector.getXmlAttributeCount(), "both attributes must be shown");
    }
}
