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
 * The inspector's XPath block: an "XPATH" sub-label above a code box with the value and the two
 * copy buttons (Copy XPath, Copy Node); a click on the value copies the XPath. With an XSD node
 * selected in the Tree view, both copy that node — not the element under the text caret.
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

        // Layout (Figma 33:2): an "XPATH" sub-label, then a code box holding the value and the
        // Copy XPath / Copy Node buttons; "Copy Node (XML)" is in the top bar's ⋮ menu as well.
        assertInstanceOf(HBox.class, value.getParent(), "the XPath value sits in its code box");
        HBox box = (HBox) value.getParent();
        assertTrue(box.getStyleClass().contains("fxt-inspector-codebox"), box.getStyleClass().toString());
        assertEquals(value, box.getChildren().get(0));
        assertEquals(2, box.getChildren().stream().filter(n -> n instanceof Button).count(),
                "the code box carries the Copy XPath and Copy Node buttons");
        assertNotNull(inspector.lookup("#inspector-copy-node"));
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

    @Test
    void copyActsOnTheSelectedXsdNode(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("s.xsd");
        Files.writeString(xsd, """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:complexType name="PartyType">
                    <xs:sequence><xs:element name="name" type="xs:string"/></xs:sequence>
                  </xs:complexType>
                  <xs:element name="root" type="PartyType"/>
                </xs:schema>
                """);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("PartyType")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.setActiveViewMode(
                org.fxt.freexmltoolkit.controls.shell.editor.ViewMode.TREE));
        String typeXPath = "/xs:schema/xs:complexType[@name='PartyType']";
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> {
            WaitForAsyncUtils.asyncFx(() -> host.revealSchemaNodeByXPath(typeXPath));
            Thread.sleep(300);
            return "PartyType".equals(inspector.getNodeNameText());
        });

        WaitForAsyncUtils.waitForAsyncFx(2000, inspector::copyXPathToClipboard);
        assertEquals(typeXPath, WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> javafx.scene.input.Clipboard.getSystemClipboard().getString()));

        WaitForAsyncUtils.waitForAsyncFx(2000, inspector::copyNodeToClipboard);
        String node = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> javafx.scene.input.Clipboard.getSystemClipboard().getString());
        assertTrue(node.startsWith("<xs:complexType") && node.contains("name=\"PartyType\""), node);
    }
}
