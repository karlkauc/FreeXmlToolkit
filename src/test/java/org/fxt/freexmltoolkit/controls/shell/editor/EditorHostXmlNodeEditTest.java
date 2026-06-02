package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlCData;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlComment;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlProcessingInstruction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** Phase 2: comment / CDATA / processing-instruction content edits round-trip to the text. */
@ExtendWith(ApplicationExtension.class)
class EditorHostXmlNodeEditTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        stage.setScene(new Scene(host, 900, 600));
        stage.show();
    }

    @Test
    void commentCdataAndPiRoundTrip(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("data.xml");
        Files.writeString(xml, "<?demo type=\"x\"?>\n<root>\n  <!-- old comment -->\n"
                + "  <data><![CDATA[raw]]></data>\n</root>\n");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("CDATA")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.GRID);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        XmlGridView grid = (XmlGridView) host.lookupAll("*").stream()
                .filter(n -> n instanceof XmlGridView).findFirst().orElseThrow();

        // Processing instruction (document-level child).
        selectFirst(grid, XmlProcessingInstruction.class, true);
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.setActiveProcessingInstruction("demo", "type=\"y\"")));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().orElse("").contains("type=\"y\""));

        // Comment (child of root).
        selectFirst(grid, XmlComment.class, false);
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.setActiveCommentText(" changed comment ")));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().orElse("").contains("changed comment"));

        // CDATA (child of <data>).
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            var data = grid.getContext().getDocument().getRootElement().getChildElements("data").get(0);
            XmlNode cdata = data.getChildren().stream().filter(n -> n instanceof XmlCData).findFirst().orElseThrow();
            grid.getContext().getSelectionModel().setSelectedNode(cdata);
            return null;
        });
        WaitForAsyncUtils.waitFor(2, TimeUnit.SECONDS, () -> host.activeXmlNodeProperty().get() instanceof XmlCData);
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.setActiveCDataText("newdata")));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().orElse("").contains("newdata"));
    }

    @Test
    void xmlDeclarationRoundTrips(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("decl.xml");
        Files.writeString(xml, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root/>\n");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("root")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.GRID);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        XmlGridView grid = (XmlGridView) host.lookupAll("*").stream()
                .filter(n -> n instanceof XmlGridView).findFirst().orElseThrow();

        // Select the document node itself.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            grid.getContext().getSelectionModel().setSelectedNode(grid.getContext().getDocument());
            return null;
        });
        WaitForAsyncUtils.waitFor(2, TimeUnit.SECONDS,
                () -> host.activeXmlNodeProperty().get()
                        instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument);

        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.setActiveXmlDeclaration("1.1", "UTF-16", Boolean.TRUE)));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> {
            String t = host.getActiveText().orElse("");
            return t.contains("version=\"1.1\"") && t.contains("encoding=\"UTF-16\"")
                    && t.contains("standalone=\"yes\"");
        });
        String text = host.getActiveText().orElse("");
        assertTrue(text.contains("version=\"1.1\""), text);
        assertTrue(text.contains("standalone=\"yes\""), text);
    }

    private void selectFirst(XmlGridView grid, Class<? extends XmlNode> type, boolean documentLevel)
            throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            var children = documentLevel
                    ? grid.getContext().getDocument().getChildren()
                    : grid.getContext().getDocument().getRootElement().getChildren();
            XmlNode node = children.stream().filter(type::isInstance).findFirst().orElseThrow();
            grid.getContext().getSelectionModel().setSelectedNode(node);
            return null;
        });
        WaitForAsyncUtils.waitFor(2, TimeUnit.SECONDS, () -> type.isInstance(host.activeXmlNodeProperty().get()));
    }
}
