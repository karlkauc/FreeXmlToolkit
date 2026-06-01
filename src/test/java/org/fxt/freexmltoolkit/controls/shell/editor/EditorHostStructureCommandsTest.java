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
 * Structural editing commands wired into the shell's context menu: move (reorder siblings),
 * duplicate, and add an {@code xs:all} compositor — all round-trip to the XSD text.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostStructureCommandsTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="a" type="xs:string"/>
                    <xs:element name="b" type="xs:string"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
              <xs:element name="box">
                <xs:complexType/>
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
    void moveDuplicateAndAddAllRoundTrip(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("name=\"b\"")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });
        XsdNode root = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveSchemaRoot().orElseThrow());

        // Move "a" down past "b".
        selectAndRun(root, "a", host::moveActiveNodeDown);
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> {
            String t = host.getActiveText().orElse("");
            return t.indexOf("name=\"b\"") >= 0 && t.indexOf("name=\"b\"") < t.indexOf("name=\"a\"");
        });

        // Duplicate "a" -> "a_copy".
        XsdNode root2 = host.getActiveSchemaRoot().orElseThrow();
        selectAndRun(root2, "a", host::duplicateActiveNode);
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().orElse("").contains("a_copy"));

        // Add an xs:all compositor to the empty "box" complex type.
        XsdNode root3 = host.getActiveSchemaRoot().orElseThrow();
        selectAndRun(root3, "box", host::addAllToActive);
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().orElse("").contains(":all"));
        assertTrue(host.getActiveText().orElse("").contains(":all"), "an xs:all compositor must round-trip");
    }

    private void selectAndRun(XsdNode root, String name, Runnable action) {
        XsdNode target = find(root, name);
        assertNotNull(target, "node not found: " + name);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.selectNodeInActiveTree(target);
            return null;
        });
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            action.run();
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
