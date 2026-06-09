package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.schema.SchemaFacets;
import org.fxt.freexmltoolkit.controls.v2.model.XsdFacet;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies that editing a facet value (via {@link EditorHost#editActiveFacet}) round-trips
 * to the text — the inspector's Type &amp; Facets table commits through this path.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostFacetEditTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:simpleType name="Code">
                <xs:restriction base="xs:string">
                  <xs:maxLength value="128"/>
                </xs:restriction>
              </xs:simpleType>
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
    void editingFacetValueRoundTripsToText(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("maxLength")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });
        XsdNode code = WaitForAsyncUtils.waitForAsyncFx(2000, () -> find(host.getActiveSchemaRoot().orElseThrow(), "Code"));
        assertNotNull(code);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.selectNodeInActiveTree(code);
            return null;
        });

        XsdFacet facet = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            List<XsdFacet> facets = SchemaFacets.collect(code);
            return facets.isEmpty() ? null : facets.get(0);
        });
        assertNotNull(facet, "the maxLength facet must be found");

        boolean ok = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.editActiveFacet(facet, "64"));
        assertTrue(ok, "editActiveFacet should succeed");
        assertTrue(host.getActiveText().orElse("").contains("value=\"64\""),
                "the edited facet value must be round-tripped to the text");
        assertFalse(host.getActiveText().orElse("").contains("value=\"128\""),
                "the old facet value must be gone");
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
