package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.inspector.InspectorPanel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Phase C: in the Text view, moving the caret into an element selects that element's model node, so
 * the inspector shows it editable and an inspector edit round-trips to the text — the same editing
 * capability the Grid and Tree views have.
 */
@ExtendWith(ApplicationExtension.class)
class TextCaretSelectsXmlNodeTest {

    private static final String XML = "<root>\n  <child x=\"1\"/>\n</root>\n";

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
    void caretInsideElementSelectsItForEditing(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, XML);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("child")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TEXT);
            return null;
        });

        // Move the caret into the <child> element (Text view).
        int caret = XML.indexOf("child") + 2;
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.moveActiveCaretTo(caret));

        // The inspector shows the editable <child> element with its attribute.
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> "child".equals(inspector.getNodeNameText()) && inspector.getXmlAttributeCount() >= 1);
        assertEquals("child", inspector.getNodeNameText());

        // An inspector edit on the caret-selected node round-trips to the text.
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.renameActiveXmlAttribute("x", "y")));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> host.getActiveText().orElse("").contains("y=\"1\""));
    }
}
