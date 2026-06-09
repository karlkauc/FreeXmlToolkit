package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
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
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Phase B: the XML Tree view renders the shared model and is selectable, so selecting a node feeds
 * the (editable) inspector and an inspector edit round-trips to the text — the same capability the
 * Grid view already has.
 */
@ExtendWith(ApplicationExtension.class)
class XmlInstanceTreeSelectionTest {

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
    void selectingATreeNodeFeedsInspectorAndEditsRoundTrip(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root><child x=\"1\"/></root>\n");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("child")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        XmlInstanceTreeView tree = (XmlInstanceTreeView) host.lookupAll("*").stream()
                .filter(n -> n instanceof XmlInstanceTreeView).findFirst().orElseThrow();

        // Select the <child> element in the tree (root element -> first child).
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            TreeItem<XmlNode> rootItem = tree.getRoot();
            TreeItem<XmlNode> childItem = rootItem.getChildren().get(0);
            tree.getSelectionModel().select(childItem);
            return null;
        });

        // The inspector now shows the <child> element with its attribute.
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS,
                () -> "child".equals(inspector.getNodeNameText()) && inspector.getXmlAttributeCount() >= 1);
        assertEquals("child", inspector.getNodeNameText());

        // An inspector edit on the tree-selected node round-trips to the text.
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.renameActiveXmlAttribute("x", "y")));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> host.getActiveText().orElse("").contains("y=\"1\""));
    }
}
