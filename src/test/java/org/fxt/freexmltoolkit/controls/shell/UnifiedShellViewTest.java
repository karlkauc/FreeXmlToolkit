package org.fxt.freexmltoolkit.controls.shell;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.Start;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.util.WaitForAsyncUtils;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Renders the {@link UnifiedShellView} skeleton headlessly (Monocle) and verifies
 * its structure wires up against the design tokens. When run with
 * {@code -Dfxt.shell.snapshot=true} it also writes light/dark PNGs to the system
 * temp dir (used for review checkpoints); the normal suite skips image output.
 */
@ExtendWith(ApplicationExtension.class)
class UnifiedShellViewTest {

    private UnifiedShellView shell;
    private Scene scene;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        shell = new UnifiedShellView();
        scene = new Scene(shell, 1280, 800);
        scene.getStylesheets().addAll(
                getClass().getResource("/css/design-tokens.css").toExternalForm(),
                getClass().getResource("/css/unified-shell.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    @Test
    void activityBarRendersOneButtonPerActivity() {
        WaitForAsyncUtils.waitForFxEvents();
        Set<javafx.scene.Node> buttons = shell.lookupAll(".fxt-activity-button");
        assertEquals(Activity.values().length, buttons.size(),
                "Activity Bar must render one button per activity");
    }

    @Test
    void inspectorRendersTheFourRequiredSections() {
        WaitForAsyncUtils.waitForFxEvents();
        Set<javafx.scene.Node> sections = shell.lookupAll(".fxt-inspector-section");
        assertEquals(4, sections.size(), "Inspector must show the four required sections");
        boolean hasNodeXpath = sections.stream()
                .map(n -> ((TitledPane) n).getText())
                .anyMatch("Node & XPath"::equals);
        assertTrue(hasNodeXpath, "Inspector must contain the 'Node & XPath' section");
    }

    @Test
    void selectingAnActivitySwapsTheSidePanel() {
        // VALIDATION still uses the generic placeholder panel (Explorer/Schema have real panels).
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> shell.getSelectionModel().select(Activity.VALIDATION));
        WaitForAsyncUtils.waitForFxEvents();
        Label title = (Label) shell.lookup(".fxt-side-panel-title");
        assertNotNull(title, "side panel title must exist");
        assertEquals("VALIDATION", title.getText(), "side panel must follow the active activity");
    }

    @Test
    void writesLightAndDarkSnapshotsWhenRequested() throws Exception {
        if (!Boolean.getBoolean("fxt.shell.snapshot")
                && !"true".equals(System.getenv("FXT_SHELL_SNAPSHOT"))) {
            return; // image output is opt-in; keep the normal suite side-effect free
        }
        File dir = new File(System.getProperty("java.io.tmpdir"));

        // Open a real XML document so the snapshot shows the editor in context.
        File sample = File.createTempFile("fxt_shell_sample", ".xml");
        java.nio.file.Files.writeString(sample.toPath(),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<FundsXML4 xmlns=\"http://www.fundsxml.org\">\n"
                        + "    <ControlData>\n"
                        + "        <UniqueDocumentID>EAM_FUND_2024_0926</UniqueDocumentID>\n"
                        + "        <DocumentGenerated>2024-09-26T18:37:12</DocumentGenerated>\n"
                        + "    </ControlData>\n"
                        + "</FundsXML4>\n");
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> shell.getEditorHost().openFile(sample.toPath()));
        WaitForAsyncUtils.waitFor(3, java.util.concurrent.TimeUnit.SECONDS,
                () -> shell.getEditorHost().getActiveText().map(t -> t.contains("FundsXML4")).orElse(false));
        // Place the caret inside <UniqueDocumentID> so the inspector is populated.
        int caret = shell.getEditorHost().getActiveText().orElse("").indexOf("EAM_FUND");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> shell.getEditorHost().moveActiveCaretTo(caret));
        WaitForAsyncUtils.sleep(400, java.util.concurrent.TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
        snapshot(new File(dir, "fxt_shell_light.png"));

        // Open a JSON document to show the JSON editor (file-type polymorphism).
        File json = File.createTempFile("fxt_shell_sample", ".json");
        java.nio.file.Files.writeString(json.toPath(),
                "{\n  \"fund\": {\n    \"id\": \"EAM_FUND_2024\",\n    \"items\": [1, 2, 3]\n  }\n}\n");
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> shell.getEditorHost().openFile(json.toPath()));
        WaitForAsyncUtils.waitFor(3, java.util.concurrent.TimeUnit.SECONDS,
                () -> shell.getEditorHost().getActiveText().map(t -> t.contains("fund")).orElse(false));
        WaitForAsyncUtils.sleep(400, java.util.concurrent.TimeUnit.MILLISECONDS); // let inspector debounce settle
        WaitForAsyncUtils.waitForFxEvents();
        snapshot(new File(dir, "fxt_shell_json.png"));

        // Open an XSD and switch to the Tree view (virtualized renderer).
        File xsd = File.createTempFile("fxt_shell_schema", ".xsd");
        java.nio.file.Files.writeString(xsd.toPath(),
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n"
                        + "  <xs:element name=\"FundsXML4\">\n"
                        + "    <xs:complexType><xs:sequence>\n"
                        + "      <xs:element name=\"ControlData\">\n"
                        + "        <xs:complexType><xs:sequence>\n"
                        + "          <xs:element name=\"UniqueDocumentID\" type=\"xs:string\"/>\n"
                        + "          <xs:element name=\"DocumentGenerated\" type=\"xs:dateTime\"/>\n"
                        + "        </xs:sequence></xs:complexType>\n"
                        + "      </xs:element>\n"
                        + "      <xs:element name=\"Funds\">\n"
                        + "        <xs:complexType><xs:sequence>\n"
                        + "          <xs:element name=\"Fund\" type=\"xs:string\" maxOccurs=\"unbounded\"/>\n"
                        + "        </xs:sequence></xs:complexType>\n"
                        + "      </xs:element>\n"
                        + "    </xs:sequence></xs:complexType>\n"
                        + "  </xs:element>\n"
                        + "</xs:schema>\n");
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> shell.getEditorHost().openFile(xsd.toPath()));
        WaitForAsyncUtils.waitFor(3, java.util.concurrent.TimeUnit.SECONDS,
                () -> shell.getEditorHost().getActiveText().map(t -> t.contains("FundsXML4")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            shell.getEditorHost().setActiveViewMode(
                    org.fxt.freexmltoolkit.controls.shell.editor.ViewMode.TREE);
            return null;
        });
        // Select a node so the inspector is driven by the model.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            shell.getEditorHost().getActiveSchemaRoot().ifPresent(rootNode -> {
                var target = findNode(rootNode, "UniqueDocumentID");
                if (target != null) {
                    shell.getEditorHost().selectNodeInActiveTree(target);
                }
            });
            return null;
        });
        WaitForAsyncUtils.sleep(300, java.util.concurrent.TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
        snapshot(new File(dir, "fxt_shell_tree.png"));

        // Switch the same XSD to the Graphic view (cards + embedded grid).
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            shell.getEditorHost().setActiveViewMode(
                    org.fxt.freexmltoolkit.controls.shell.editor.ViewMode.GRAPHIC);
            return null;
        });
        WaitForAsyncUtils.sleep(300, java.util.concurrent.TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
        snapshot(new File(dir, "fxt_shell_graphic.png"));

        // Schema activity: Type Library side panel + facets in the inspector.
        File facetXsd = File.createTempFile("fxt_shell_facets", ".xsd");
        java.nio.file.Files.writeString(facetXsd.toPath(),
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n"
                        + "  <xs:element name=\"Age\">\n"
                        + "    <xs:simpleType><xs:restriction base=\"xs:integer\">\n"
                        + "      <xs:minInclusive value=\"0\"/>\n"
                        + "      <xs:maxInclusive value=\"150\"/>\n"
                        + "    </xs:restriction></xs:simpleType>\n"
                        + "  </xs:element>\n"
                        + "  <xs:complexType name=\"PersonType\">\n"
                        + "    <xs:sequence><xs:element name=\"name\" type=\"xs:string\"/></xs:sequence>\n"
                        + "  </xs:complexType>\n"
                        + "</xs:schema>\n");
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> shell.getEditorHost().openFile(facetXsd.toPath()));
        WaitForAsyncUtils.waitFor(3, java.util.concurrent.TimeUnit.SECONDS,
                () -> shell.getEditorHost().getActiveText().map(t -> t.contains("PersonType")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            shell.getSelectionModel().select(org.fxt.freexmltoolkit.controls.shell.Activity.SCHEMA);
            shell.getEditorHost().setActiveViewMode(
                    org.fxt.freexmltoolkit.controls.shell.editor.ViewMode.TREE);
            return null;
        });
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            var ageNode = findNode(shell.getEditorHost().getActiveSchemaRoot().orElseThrow(), "Age");
            if (ageNode != null) {
                shell.getEditorHost().selectNodeInActiveTree(ageNode);
            }
            return null;
        });
        WaitForAsyncUtils.sleep(300, java.util.concurrent.TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
        snapshot(new File(dir, "fxt_shell_schema_activity.png"));

        // Validation activity: invalid XML against a schema -> problems list.
        File vXsd = File.createTempFile("fxt_shell_v", ".xsd");
        java.nio.file.Files.writeString(vXsd.toPath(),
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n"
                        + "  <xs:element name=\"root\"><xs:complexType><xs:sequence>\n"
                        + "    <xs:element name=\"name\" type=\"xs:string\"/>\n"
                        + "  </xs:sequence></xs:complexType></xs:element>\n"
                        + "</xs:schema>\n");
        File vXml = File.createTempFile("fxt_shell_v", ".xml");
        java.nio.file.Files.writeString(vXml.toPath(), "<root>\n  <wrong/>\n</root>\n");
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> shell.getEditorHost().openFile(vXml.toPath()));
        WaitForAsyncUtils.waitFor(3, java.util.concurrent.TimeUnit.SECONDS,
                () -> shell.getEditorHost().getActiveText().map(t -> t.contains("wrong")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            shell.getEditorHost().setSchemaForActiveDocument(vXsd);
            shell.getSelectionModel().select(org.fxt.freexmltoolkit.controls.shell.Activity.VALIDATION);
            return null;
        });
        // Trigger the panel's Validate button.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            shell.lookupAll(".button").stream()
                    .filter(n -> n instanceof javafx.scene.control.Button b && "Validate".equals(b.getText()))
                    .findFirst().ifPresent(b -> ((javafx.scene.control.Button) b).fire());
            return null;
        });
        WaitForAsyncUtils.sleep(700, java.util.concurrent.TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
        snapshot(new File(dir, "fxt_shell_validation.png"));

        // Transform activity: show the panel (Set XSLT / Transform / XPath / Result).
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            shell.getSelectionModel().select(org.fxt.freexmltoolkit.controls.shell.Activity.TRANSFORM);
            if (shell.lookup(".fxt-xpath-field") instanceof javafx.scene.control.TextField field) {
                field.setText("/root");
            }
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        snapshot(new File(dir, "fxt_shell_transform.png"));

        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            shell.getStyleClass().add("fxt-theme-dark");
            shell.applyCss();
            ((Region) scene.getRoot()).layout();
        });
        snapshot(new File(dir, "fxt_shell_dark.png"));
    }

    private static org.fxt.freexmltoolkit.controls.v2.model.XsdNode findNode(
            org.fxt.freexmltoolkit.controls.v2.model.XsdNode node, String name) {
        if (name.equals(node.getName())) {
            return node;
        }
        for (var child : node.getChildren()) {
            var found = findNode(child, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void snapshot(File target) throws Exception {
        WritableImage image = WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            shell.applyCss();
            shell.layout();
            return scene.snapshot(null);
        });
        ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", target);
        assertTrue(target.exists() && target.length() > 0, "snapshot should be written: " + target);
    }
}
