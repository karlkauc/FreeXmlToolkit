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
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Selecting an element that declares identity constraints shows them (read-only) in the
 * inspector's Constraints section; a plain element shows none.
 */
@ExtendWith(ApplicationExtension.class)
class InspectorConstraintsTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="catalog">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="book" maxOccurs="unbounded"/>
                  </xs:sequence>
                </xs:complexType>
                <xs:key name="bookKey">
                  <xs:selector xpath="book"/>
                  <xs:field xpath="@id"/>
                </xs:key>
              </xs:element>
            </xs:schema>
            """;

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
    void elementWithKeyShowsConstraintReadOnly(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("bookKey")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });
        XsdNode root = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveSchemaRoot().orElseThrow());

        select(root, "catalog");
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> inspector.getConstraintCount() == 1);
        assertEquals(1, inspector.getConstraintCount(), "catalog declares one key constraint");

        select(host.getActiveSchemaRoot().orElseThrow(), "book");
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> "book".equals(inspector.getNodeNameText()) && inspector.getConstraintCount() == 0);
        assertEquals(0, inspector.getConstraintCount(), "book has no constraints");
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
