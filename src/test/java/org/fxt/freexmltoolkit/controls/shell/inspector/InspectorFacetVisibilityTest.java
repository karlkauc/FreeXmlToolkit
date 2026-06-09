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
 * Facets must only be shown for simple-type-bearing nodes (element/attribute/simpleType/
 * restriction), never aggregated across the whole schema when a container/root is selected.
 */
@ExtendWith(ApplicationExtension.class)
class InspectorFacetVisibilityTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:simpleType name="Code">
                <xs:restriction base="xs:string">
                  <xs:minLength value="2"/>
                  <xs:maxLength value="3"/>
                </xs:restriction>
              </xs:simpleType>
              <xs:simpleType name="Other">
                <xs:restriction base="xs:string">
                  <xs:maxLength value="9"/>
                </xs:restriction>
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
    void schemaRootShowsNoFacetsButSimpleTypeDoes(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("minLength")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });

        // Selecting the schema root must NOT aggregate facets from all global types.
        XsdNode root = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveSchemaRoot().orElseThrow());
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.selectNodeInActiveTree(root);
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> "Schema".equals(inspector.getKindText()));
        assertEquals(0, inspector.getFacetCount(),
                "schema root must show no facets (was aggregating all global types)");

        // Selecting the "Code" simple type must show exactly its two inline facets.
        XsdNode code = WaitForAsyncUtils.waitForAsyncFx(2000, () -> find(root, "Code"));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.selectNodeInActiveTree(code);
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> inspector.getFacetCount() == 2);
        assertEquals(2, inspector.getFacetCount(), "the Code simple type has exactly two inline facets");
    }

    private XsdNode find(XsdNode node, String name) {
        if (name.equals(node.getName())) {
            return node;
        }
        for (XsdNode child : node.getChildren()) {
            XsdNode found = find(child, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
