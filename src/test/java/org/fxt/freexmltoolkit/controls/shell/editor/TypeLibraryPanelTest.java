package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
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
    void schemaToolsLiveInTheOverflowMenu() {
        WaitForAsyncUtils.waitForFxEvents();
        var items = panel.overflowMenuItemTexts();
        assertTrue(items.contains("Generate XSD from XML"), items.toString());
        assertTrue(items.contains("Generate XSD (Batch)…"), items.toString());
        assertTrue(items.contains("Generate Sample XML…"), items.toString());
        assertTrue(items.contains("Generate Sample XML (Advanced)…"), items.toString());
        assertTrue(items.contains("Flatten Schema"), items.toString());
        assertTrue(items.contains("Statistics"), items.toString());
        assertTrue(items.contains("Schema Quality"), items.toString());
        assertTrue(items.contains("Generate Documentation…"), items.toString());
    }

    @Test
    void typeContextMenuOffersEditorAndFindUsage() {
        WaitForAsyncUtils.waitForFxEvents();
        var items = panel.typeContextMenuItemTexts();
        assertTrue(items.contains("Reveal in Tree"), items.toString());
        assertTrue(items.contains("Open Type Editor"), items.toString());
        assertTrue(items.contains("Find Usage"), items.toString());
    }
}
