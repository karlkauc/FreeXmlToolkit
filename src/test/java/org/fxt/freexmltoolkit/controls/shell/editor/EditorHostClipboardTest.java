package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Copy/cut/paste of XSD nodes via the shell clipboard: copy-paste inserts a "_copy", cut-paste
 * also removes the original — both round-trip to the XSD text.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostClipboardTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="alpha" type="xs:string"/>
                    <xs:element name="beta" type="xs:string"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
              <xs:element name="box">
                <xs:complexType><xs:sequence/></xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    private EditorHost host;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        stage.setScene(new Scene(host, 900, 600));
        stage.show();
    }

    @Test
    void copyCutPasteRoundTrip(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("name=\"beta\"")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });
        XsdNode root = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveSchemaRoot().orElseThrow());

        assertFalse(host.canPaste(), "clipboard starts empty");

        // Copy "alpha" then paste into "box": a copy appears, the original stays.
        select(root, "alpha");
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.copyActiveNode()));
        assertTrue(host.canPaste(), "clipboard holds a node after copy");
        select(host.getActiveSchemaRoot().orElseThrow(), "box");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.pasteIntoActive());
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().orElse("").contains("alpha_copy"));
        assertTrue(host.getActiveText().orElse("").contains("name=\"alpha\""),
                "copy-paste keeps the original alpha");

        // Cut "beta" then paste into "box": the original is removed, a copy appears.
        select(host.getActiveSchemaRoot().orElseThrow(), "beta");
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.cutActiveNode()));
        select(host.getActiveSchemaRoot().orElseThrow(), "box");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.pasteIntoActive());
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().orElse("").contains("beta_copy")
                        && !host.getActiveText().orElse("").contains("name=\"beta\""));
        assertFalse(host.getActiveText().orElse("").contains("name=\"beta\""),
                "cut-paste removes the original beta");
    }

    private void select(XsdNode root, String name) {
        XsdNode target = find(root, name);
        assertNotNull(target, "node not found: " + name);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.selectNodeInActiveTree(target);
            return null;
        });
    }

    private XsdNode find(XsdNode node, String name) {
        if (name.equals(node.getName())) {
            return node;
        }
        for (XsdNode c : node.getChildren()) {
            XsdNode f = find(c, name);
            if (f != null) {
                return f;
            }
        }
        return null;
    }
}
