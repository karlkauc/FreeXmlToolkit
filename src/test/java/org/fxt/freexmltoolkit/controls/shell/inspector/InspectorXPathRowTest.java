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

        // Layout (Figma 33:2): an "XPATH" sub-label, then a code box holding the value and one
        // copy button; "Copy Node (XML)" lives in the top bar's ⋮ menu.
        assertInstanceOf(HBox.class, value.getParent(), "the XPath value sits in its code box");
        HBox box = (HBox) value.getParent();
        assertTrue(box.getStyleClass().contains("fxt-inspector-codebox"), box.getStyleClass().toString());
        assertEquals(value, box.getChildren().get(0));
        assertEquals(1, box.getChildren().stream().filter(n -> n instanceof Button).count(),
                "the code box carries the Copy XPath button");
        VBox body = (VBox) box.getParent();
        Label sublabel = (Label) body.getChildren().get(body.getChildren().indexOf(box) - 1);
        assertEquals("XPATH", sublabel.getText(), "the sub-label sits above the code box");
        javafx.scene.control.MenuButton more = (javafx.scene.control.MenuButton) inspector.lookup("#inspector-more");
        assertTrue(more.getItems().stream().anyMatch(i -> "Copy Node (XML)".equals(i.getText())),
                "Copy Node (XML) is in the ⋮ menu");

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
