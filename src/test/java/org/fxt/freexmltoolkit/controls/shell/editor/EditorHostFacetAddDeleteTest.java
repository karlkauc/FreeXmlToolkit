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
import org.fxt.freexmltoolkit.controls.v2.model.XsdFacetType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies adding and deleting facets (incl. enumeration) on a simple-type restriction
 * via the inspector path (EditorHost.addActiveFacet / deleteActiveFacet) round-trips.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostFacetAddDeleteTest {

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
    void addAndDeleteFacetsRoundTrip(@TempDir Path tmp) throws Exception {
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
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.selectNodeInActiveTree(code);
            return null;
        });

        // Add a minLength facet to the existing restriction.
        boolean added = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.addActiveFacet(XsdFacetType.MIN_LENGTH, "1"));
        assertTrue(added);
        assertTrue(host.getActiveText().orElse("").contains("minLength"),
                "added minLength facet must round-trip to the text");

        // Add an enumeration.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.addActiveFacet(XsdFacetType.ENUMERATION, "ABC"));
        assertTrue(host.getActiveText().orElse("").contains("enumeration"),
                "added enumeration must round-trip");

        // Delete the maxLength facet.
        XsdFacet maxLen = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            List<XsdFacet> facets = SchemaFacets.collect(code);
            return facets.stream().filter(f -> f.getFacetType() == XsdFacetType.MAX_LENGTH).findFirst().orElse(null);
        });
        assertNotNull(maxLen, "maxLength facet must exist");
        boolean deleted = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.deleteActiveFacet(maxLen));
        assertTrue(deleted);
        assertFalse(host.getActiveText().orElse("").contains("maxLength"),
                "deleted maxLength facet must be gone from the text");
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
