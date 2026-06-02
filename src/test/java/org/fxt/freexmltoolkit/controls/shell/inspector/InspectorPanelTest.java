package org.fxt.freexmltoolkit.controls.shell.inspector;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * TestFX verification that the inspector reflects the active editor's caret in
 * Text mode: moving the caret into an element updates the Node &amp; XPath
 * section (debounced).
 */
@ExtendWith(ApplicationExtension.class)
class InspectorPanelTest {

    private EditorHost host;
    private InspectorPanel inspector;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        inspector = new InspectorPanel(host);
        stage.setScene(new Scene(new HBox(host, inspector), 1000, 600));
        stage.show();
    }

    @Test
    void caretInsideAnElementUpdatesNodeAndXPath(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("doc.xml");
        Files.writeString(file, "<root><item>value</item></root>");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(file));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("item")).orElse(false));

        int caret = "<root><item>".length() + 1; // inside "value"
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.moveActiveCaretTo(caret));

        // Wait for the debounced refresh to settle into the final state (name AND xpath together),
        // so a trailing refresh can't leave one updated and the other still at the placeholder.
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS,
                () -> "item".equals(inspector.getNodeNameText())
                        && "/root/item".equals(inspector.getXPathText()));
        assertEquals("item", inspector.getNodeNameText());
        assertEquals("/root/item", inspector.getXPathText());
    }
}
