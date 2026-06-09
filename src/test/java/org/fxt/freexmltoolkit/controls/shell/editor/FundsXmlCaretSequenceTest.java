package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.inspector.InspectorPanel;
import org.fxt.freexmltoolkit.controls.shell.schema.XmlInstanceTreeView;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Reproduces the smoke-test sequence (Grid edit &rarr; Tree select &rarr; Text caret) on the real
 * FundsXML instance, to verify the Text-view caret resolves the deepest element on its line even
 * after view switches. No schema is bound (the caret mapping does not need it).
 */
@ExtendWith(ApplicationExtension.class)
class FundsXmlCaretSequenceTest {

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
    void textCaretResolvesDataSupplierAfterViewSwitches() throws Exception {
        File xml = new File("src/test/resources/FundsXML_306.xml");
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> host.openFile(xml.toPath()));
        WaitForAsyncUtils.waitFor(8, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("DataSupplier")).orElse(false));

        // Grid: select root, add an attribute (round-trips).
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.GRID);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        XmlGridView grid = (XmlGridView) host.lookupAll("*").stream()
                .filter(n -> n instanceof XmlGridView).findFirst().orElseThrow();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            grid.getContext().getSelectionModel().setSelectedNode(grid.getContext().getDocument().getRootElement());
            return null;
        });
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.setActiveXmlAttribute("smoke", "1")));

        // Tree: select a child node.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        XmlInstanceTreeView tree = (XmlInstanceTreeView) host.lookupAll("*").stream()
                .filter(n -> n instanceof XmlInstanceTreeView).findFirst().orElseThrow();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            TreeItem<XmlNode> rootItem = tree.getRoot();
            tree.getSelectionModel().select(rootItem.getChildren().get(0));
            return null;
        });

        // Text: move the caret into <DataSupplier> and verify it resolves the deepest element.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TEXT);
            return null;
        });
        int caret = host.getActiveText().orElse("").indexOf("<DataSupplier") + 3;
        assertTrue(caret > 3, "the instance must contain a <DataSupplier> element");
        int line = lineOfOffset(host.getActiveText().orElse(""), caret);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.moveActiveCaretTo(caret));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> "DataSupplier".equals(inspector.getNodeNameText()));
        assertEquals("DataSupplier", inspector.getNodeNameText(),
                "Text caret on line " + line + " must resolve the DataSupplier element");
    }

    private static int lineOfOffset(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
