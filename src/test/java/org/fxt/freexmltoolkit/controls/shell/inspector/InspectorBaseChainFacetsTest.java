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
 * A simple type restricting a named base shows its own facets editable and the whole base chain
 * read-only (inherited); an element referencing it shows the full chain inherited.
 */
@ExtendWith(ApplicationExtension.class)
class InspectorBaseChainFacetsTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:simpleType name="RootCode">
                <xs:restriction base="xs:string"><xs:pattern value="[A-Z]+"/></xs:restriction>
              </xs:simpleType>
              <xs:simpleType name="Code">
                <xs:restriction base="RootCode"><xs:maxLength value="3"/></xs:restriction>
              </xs:simpleType>
              <xs:element name="ref" type="Code"/>
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
    void ownFacetEditableBaseChainInherited(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("RootCode")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });
        XsdNode root = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveSchemaRoot().orElseThrow());

        // "Code": own maxLength editable + RootCode's pattern inherited (read-only).
        select(root, "Code");
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> inspector.getFacetCount() == 2 && inspector.getInheritedFacetCount() == 1);
        assertEquals(2, inspector.getFacetCount(), "own maxLength + inherited pattern");
        assertEquals(1, inspector.getInheritedFacetCount(), "only the base-chain pattern is inherited");

        // "ref" references Code: the whole chain (maxLength + pattern) is inherited.
        select(host.getActiveSchemaRoot().orElseThrow(), "ref");
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> inspector.getFacetCount() == 2 && inspector.getInheritedFacetCount() == 2);
        assertEquals(2, inspector.getInheritedFacetCount(), "a referencing element inherits the full chain");
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
