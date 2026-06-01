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
 * A list/union simple type whose item/member types are named simple types shows those facets
 * read-only (inherited) in the inspector.
 */
@ExtendWith(ApplicationExtension.class)
class InspectorListUnionFacetsTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:simpleType name="Code">
                <xs:restriction base="xs:string"><xs:maxLength value="3"/></xs:restriction>
              </xs:simpleType>
              <xs:simpleType name="Amount">
                <xs:restriction base="xs:decimal"><xs:totalDigits value="9"/></xs:restriction>
              </xs:simpleType>
              <xs:simpleType name="CodeList">
                <xs:list itemType="Code"/>
              </xs:simpleType>
              <xs:simpleType name="CodeOrAmount">
                <xs:union memberTypes="Code Amount"/>
              </xs:simpleType>
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
    void listAndUnionShowInheritedItemMemberFacets(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("CodeOrAmount")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });
        XsdNode root = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveSchemaRoot().orElseThrow());

        // List: inherits the item type's single facet, read-only.
        XsdNode list = WaitForAsyncUtils.waitForAsyncFx(2000, () -> find(root, "CodeList"));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.selectNodeInActiveTree(list);
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> inspector.getFacetCount() == 1 && inspector.getInheritedFacetCount() == 1);
        assertEquals(1, inspector.getInheritedFacetCount(), "list item-type facet is inherited");

        // Union: inherits one facet from each member type.
        XsdNode union = WaitForAsyncUtils.waitForAsyncFx(2000, () -> find(root, "CodeOrAmount"));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.selectNodeInActiveTree(union);
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> inspector.getFacetCount() == 2 && inspector.getInheritedFacetCount() == 2);
        assertEquals(2, inspector.getInheritedFacetCount(), "both union member facets are inherited");
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
