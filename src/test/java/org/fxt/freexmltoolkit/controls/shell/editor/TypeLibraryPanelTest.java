package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * TestFX verification of the Schema side panel (Figma mockup node 37:2): the
 * declarations grouped into GLOBAL ELEMENTS / COMPLEX TYPES / SIMPLE TYPES, the
 * filter field, and the schema tools in the ⋮ overflow menu (instead of the
 * former button stack).
 */
@ExtendWith(ApplicationExtension.class)
class TypeLibraryPanelTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="FundsXML4" type="ControlDataType"/>
              <xs:complexType name="ControlDataType">
                <xs:sequence><xs:element name="id" type="ISINType"/></xs:sequence>
              </xs:complexType>
              <xs:complexType name="AssetType">
                <xs:sequence><xs:element name="name" type="xs:string"/></xs:sequence>
              </xs:complexType>
              <xs:simpleType name="ISINType">
                <xs:restriction base="xs:string"/>
              </xs:simpleType>
            </xs:schema>
            """;

    private EditorHost host;
    private TypeLibraryPanel panel;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        panel = new TypeLibraryPanel(host);
        panel.setPrefWidth(280);
        stage.setScene(new Scene(new HBox(panel, host), 1100, 700));
        stage.show();
    }

    private void openSchema(Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("ControlDataType")).orElse(false));
        WaitForAsyncUtils.waitForFxEvents();
    }

    @SuppressWarnings("unchecked")
    private ListView<XsdNode> list(String id) {
        return (ListView<XsdNode>) panel.lookup("#" + id);
    }

    @Test
    void titleFollowsTheSharedSidePanelConvention() {
        WaitForAsyncUtils.waitForFxEvents();
        Label title = (Label) panel.lookup(".fxt-side-panel-title");
        assertNotNull(title, "panel must keep the shared side-panel title class");
        assertEquals("SCHEMA", title.getText());
    }

    @Test
    void groupsDeclarationsIntoElementsComplexAndSimpleTypes(@TempDir Path tmp) throws Exception {
        openSchema(tmp);
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS,
                () -> list("schema-complex-list").getItems().size() == 2);

        assertEquals(1, list("schema-elements-list").getItems().size(), "one global element");
        assertEquals("FundsXML4", list("schema-elements-list").getItems().get(0).getName());
        assertEquals(2, list("schema-complex-list").getItems().size(), "two complex types");
        assertEquals(1, list("schema-simple-list").getItems().size(), "one simple type");
        assertEquals("ISINType", list("schema-simple-list").getItems().get(0).getName());
    }

    @Test
    void filterNarrowsAllGroups(@TempDir Path tmp) throws Exception {
        openSchema(tmp);
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS,
                () -> list("schema-complex-list").getItems().size() == 2);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            ((TextField) panel.lookup("#schema-filter")).setText("asset");
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(0, list("schema-elements-list").getItems().size());
        assertEquals(1, list("schema-complex-list").getItems().size());
        assertEquals("AssetType", list("schema-complex-list").getItems().get(0).getName());
        assertEquals(0, list("schema-simple-list").getItems().size());
    }

    @Test
    void schemaToolsAreVisibleAboveTheFilter() {
        WaitForAsyncUtils.waitForFxEvents();
        var names = panel.toolNames();
        assertTrue(names.contains("Generate XSD from XML"), names.toString());
        assertTrue(names.contains("Generate XSD (Batch)…"), names.toString());
        assertTrue(names.contains("Generate Sample XML…"), names.toString());
        assertTrue(names.contains("Generate Sample XML (Advanced)…"), names.toString());
        assertTrue(names.contains("Flatten Schema…"), names.toString());
        assertTrue(names.contains("Schema Analysis"), names.toString());
        assertFalse(names.contains("Statistics"), "replaced by Schema Analysis: " + names);
        assertFalse(names.contains("Schema Quality"), "replaced by Schema Analysis: " + names);
        assertNotNull(panel.lookup("#schema-tool-analysis"));
        assertTrue(names.contains("Generate Documentation…"), names.toString());
        assertNotNull(panel.lookup("#schema-tool-documentation"),
                "the tools must be visible buttons (not hidden in an overflow menu)");
    }

    @Test
    void typeContextMenuOffersEditorAndFindUsage() {
        WaitForAsyncUtils.waitForFxEvents();
        var items = panel.typeContextMenuItemTexts();
        assertTrue(items.contains("Reveal in Tree"), items.toString());
        assertTrue(items.contains("Open Type Editor"), items.toString());
        assertTrue(items.contains("Find Usage"), items.toString());
    }

    @Test
    void droppingAnXsdOnThePanelOpensIt(@TempDir Path tmp) throws Exception {
        assertNotNull(panel.getOnDragOver(), "the Schema panel must accept file drags");

        Path xsd = tmp.resolve("dropped.xsd");
        Files.writeString(xsd, XSD);
        javafx.scene.input.Dragboard dragboard = org.mockito.Mockito.mock(javafx.scene.input.Dragboard.class);
        org.mockito.Mockito.when(dragboard.hasFiles()).thenReturn(true);
        org.mockito.Mockito.when(dragboard.getFiles()).thenReturn(java.util.List.of(xsd.toFile()));
        javafx.scene.input.DragEvent event = org.mockito.Mockito.mock(javafx.scene.input.DragEvent.class);
        org.mockito.Mockito.when(event.getDragboard()).thenReturn(dragboard);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.getOnDragDropped().handle(event);
            return null;
        });

        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> host.getOpenDocuments().stream()
                .anyMatch(d -> d.getPath() != null && d.getPath().equals(xsd)));
        org.mockito.Mockito.verify(event).setDropCompleted(true);
    }

    /**
     * Regression test for issue #36: {@code xsheet-master.xsd} imports 7 sibling schemas via
     * relative {@code schemaLocation}s (e.g. {@code xsheet-core.xsd}). Opening the file directly
     * from {@code src/test/resources/xsd/xsheet/} (siblings present on disk, unlike a copy into an
     * isolated {@code @TempDir}) exercises {@code EditorHost.resolveActiveSchemaForLibrary()},
     * which must thread the document's directory into the parse so the imports resolve locally
     * instead of falling back to a namespace-URL download.
     */
    @Test
    void typeLibraryListsTypesFromRelativelyImportedSchema() throws Exception {
        Path master = Path.of("src/test/resources/xsd/xsheet/xsheet-master.xsd").toAbsolutePath();
        assertTrue(Files.exists(master), "fixture missing: " + master);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(master));
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("xsheet-core.xsd")).orElse(false));
        WaitForAsyncUtils.waitForFxEvents();

        // schema-elements-list / schema-complex-list are populated from
        // EditorHost.resolveActiveSchemaForLibrary(); wait for the async refresh to pick up
        // ExposureSheet + the four complex types declared in the imported xsheet-core.xsd.
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS,
                () -> list("schema-complex-list").getItems().size() >= 4);

        List<String> elementNames = list("schema-elements-list").getItems().stream()
                .map(XsdNode::getName).toList();
        List<String> complexTypeNames = list("schema-complex-list").getItems().stream()
                .map(XsdNode::getName).toList();

        assertTrue(elementNames.contains("ExposureSheet"),
                "expected the global element from xsheet-core.xsd: " + elementNames);
        assertTrue(complexTypeNames.contains("ProductionType"),
                "expected a complex type from xsheet-core.xsd: " + complexTypeNames);

        // The import must have resolved via the local file, not a namespace-URL download.
        // XsdSchemaReference.isResolved() is never true (known gotcha) — check
        // getResolvedPath() != null instead.
        var schemaRoot = host.getActiveSchemaRoot();
        // Force the Tree view to parse (getActiveSchemaRoot() only reflects an already-built
        // structured view), matching how a user would inspect the imports after Find/Reveal.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        schemaRoot = host.getActiveSchemaRoot();
        assertTrue(schemaRoot.isPresent(), "the Tree view should have parsed the schema");
        List<org.fxt.freexmltoolkit.controls.v2.model.XsdImport> imports = schemaRoot.get().getChildren().stream()
                .filter(org.fxt.freexmltoolkit.controls.v2.model.XsdImport.class::isInstance)
                .map(org.fxt.freexmltoolkit.controls.v2.model.XsdImport.class::cast)
                .toList();
        assertEquals(7, imports.size(), "xsheet-master.xsd declares 7 imports");
        assertTrue(imports.stream().allMatch(i -> i.getResolvedPath() != null),
                "all 7 relative imports must resolve against the document's directory: "
                        + imports.stream().map(i -> i.getSchemaLocation() + "=" + i.getResolvedPath()).toList());
    }

    @Test
    void droppingANonXsdOnThePanelIsRejected(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("data.xml");
        Files.writeString(xml, "<root/>");
        javafx.scene.input.Dragboard dragboard = org.mockito.Mockito.mock(javafx.scene.input.Dragboard.class);
        org.mockito.Mockito.when(dragboard.hasFiles()).thenReturn(true);
        org.mockito.Mockito.when(dragboard.getFiles()).thenReturn(java.util.List.of(xml.toFile()));
        javafx.scene.input.DragEvent event = org.mockito.Mockito.mock(javafx.scene.input.DragEvent.class);
        org.mockito.Mockito.when(event.getDragboard()).thenReturn(dragboard);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.getOnDragDropped().handle(event);
            return null;
        });

        assertTrue(host.getOpenDocuments().stream()
                        .noneMatch(d -> d.getPath() != null && d.getPath().equals(xml)),
                "a non-XSD drop on the Schema panel must not open the file");
        org.mockito.Mockito.verify(event).setDropCompleted(false);
    }
}
