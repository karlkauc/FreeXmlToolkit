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
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlComment;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlProcessingInstruction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** Phase 2 (UI): selecting a comment or PI shows the matching inspector section, populated. */
@ExtendWith(ApplicationExtension.class)
class InspectorXmlOtherNodeTest {

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
    void commentAndPiShowTheirSections(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("data.xml");
        Files.writeString(xml, "<?demo type=\"x\"?>\n<root>\n  <!-- hello -->\n</root>\n");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("hello")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.GRAPHIC);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        XmlGridView grid = (XmlGridView) host.lookupAll("*").stream()
                .filter(n -> n instanceof XmlGridView).findFirst().orElseThrow();

        // Comment -> CONTENT section, populated; namespace/value sections hidden.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            XmlNode c = grid.getContext().getDocument().getRootElement().getChildren().stream()
                    .filter(n -> n instanceof XmlComment).findFirst().orElseThrow();
            grid.getContext().getSelectionModel().setSelectedNode(c);
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS,
                () -> inspector.isContentSectionVisible() && inspector.getContentText().contains("hello"));
        assertFalse(inspector.isNamespaceSectionVisible(), "namespace section is element-only");

        // PI -> PROCESSING INSTRUCTION section, populated.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            XmlNode pi = grid.getContext().getDocument().getChildren().stream()
                    .filter(n -> n instanceof XmlProcessingInstruction).findFirst().orElseThrow();
            grid.getContext().getSelectionModel().setSelectedNode(pi);
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS,
                () -> inspector.isPiSectionVisible() && "demo".equals(inspector.getPiTargetText()));
        assertFalse(inspector.isContentSectionVisible(), "content section hidden for a PI");
    }
}
