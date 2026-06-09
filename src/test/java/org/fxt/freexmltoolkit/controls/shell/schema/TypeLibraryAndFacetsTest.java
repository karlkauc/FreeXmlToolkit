package org.fxt.freexmltoolkit.controls.shell.schema;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.ViewMode;
import org.fxt.freexmltoolkit.controls.shell.inspector.InspectorPanel;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Integration tests (TestFX) for the Type Library (named types of the active XSD,
 * reveal-by-name) and the inspector's facet table.
 */
@ExtendWith(ApplicationExtension.class)
class TypeLibraryAndFacetsTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:simpleType name="AgeType">
                <xs:restriction base="xs:integer">
                  <xs:minInclusive value="0"/>
                  <xs:maxInclusive value="150"/>
                </xs:restriction>
              </xs:simpleType>
              <xs:element name="Age" type="AgeType"/>
              <xs:element name="Inline">
                <xs:simpleType><xs:restriction base="xs:string">
                  <xs:maxLength value="10"/>
                </xs:restriction></xs:simpleType>
              </xs:element>
            </xs:schema>
            """;

    private EditorHost host;
    private InspectorPanel inspector;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        inspector = new InspectorPanel(host);
        stage.setScene(new Scene(new HBox(host, inspector), 1100, 600));
        stage.show();
    }

    @Test
    void typeLibraryListsNamedTypesAndRevealSelectsThem(@TempDir Path tmp) throws Exception {
        openXsd(tmp);

        List<String> typeNames = WaitForAsyncUtils.waitForAsyncFx(2000, () ->
                host.getActiveNamedTypes().stream().map(XsdNode::getName).collect(Collectors.toList()));
        assertTrue(typeNames.contains("AgeType"), typeNames.toString());

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.revealTypeByName("AgeType");
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> "AgeType".equals(inspector.getNodeNameText()));
        assertEquals(ViewMode.TREE, host.activeViewModeProperty().get(), "reveal switches to Tree");
        assertEquals("AgeType", inspector.getNodeNameText());
    }

    @Test
    void inspectorShowsFacetsOfSelectedElement(@TempDir Path tmp) throws Exception {
        openXsd(tmp);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });
        XsdNode inline = WaitForAsyncUtils.waitForAsyncFx(2000, () ->
                find(host.getActiveSchemaRoot().orElseThrow(), "Inline"));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.selectNodeInActiveTree(inline);
            return null;
        });

        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> inspector.getFacetCount() == 1);
        assertEquals(1, inspector.getFacetCount(), "the inline maxLength facet should be shown");
    }

    private void openXsd(Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("AgeType")).orElse(false));
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
