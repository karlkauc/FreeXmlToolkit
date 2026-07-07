package org.fxt.freexmltoolkit.controls.shell.inspector;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The inspector's XPath block: a header line ("XPath" key + the two copy buttons) with the
 * XPath value on its own full-width line below, and a click on the value copies the XPath
 * to the system clipboard.
 */
@ExtendWith(ApplicationExtension.class)
class InspectorXPathRowTest {

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
    void xpathValueSitsBelowTheHeaderAndClickCopiesIt(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, XML);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("child")).orElse(false));

        // Move the caret into <child> so the inspector resolves its XPath.
        int caret = XML.indexOf("child") + 2;
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.moveActiveCaretTo(caret));
        Label value = (Label) inspector.lookup("#inspector-xpath-value");
        assertNotNull(value, "the XPath value label must exist");
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> value.getText() != null && value.getText().startsWith("/root"));

        // Layout: the value is the second child of the block; the header line above it
        // carries the "XPath" key and the two copy buttons (no icons beside the value).
        assertInstanceOf(VBox.class, value.getParent(), "the XPath value must sit on its own line");
        VBox block = (VBox) value.getParent();
        assertEquals(2, block.getChildren().size());
        HBox header = (HBox) block.getChildren().get(0);
        assertEquals(value, block.getChildren().get(1), "the value must be below the header");
        assertEquals("XPath", ((Label) header.getChildren().get(0)).getText());
        assertEquals(2, header.getChildren().stream().filter(n -> n instanceof Button).count(),
                "the header must carry the Copy XPath and Copy Node buttons");

        // Wait until the caret-selected model node is fully resolved (not just the transient
        // text-mode XPath), so the displayed value and the copy source are the same.
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> "child".equals(inspector.getNodeNameText()));
        // Clicking the value copies the XPath to the system clipboard.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            value.getOnMouseClicked().handle(null);
            return null;
        });
        String clipboard = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> javafx.scene.input.Clipboard.getSystemClipboard().getString());
        assertEquals(value.getText(), clipboard, "a click on the XPath value must copy it");
    }
}
