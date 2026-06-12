package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * TestFX verification that the Validation panel validates the active XML against
 * its bound XSD and reports problems (or none for a valid document).
 */
@ExtendWith(ApplicationExtension.class)
class ValidationPanelTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root">
                <xs:complexType><xs:sequence>
                  <xs:element name="name" type="xs:string"/>
                </xs:sequence></xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    private EditorHost host;
    private ValidationPanel panel;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        panel = new ValidationPanel(host);
        stage.setScene(new Scene(new HBox(host, panel), 1100, 600));
        stage.show();
    }

    @Test
    void reportsProblemsForInvalidXmlAndNoneForValid(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);

        Path invalid = tmp.resolve("invalid.xml");
        Files.writeString(invalid, "<root><wrong/></root>");
        open(invalid, xsd);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.revalidate();
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> panel.getProblemCount() > 0);
        assertTrue(panel.getProblemCount() > 0, "invalid XML must report problems");

        Path valid = tmp.resolve("valid.xml");
        Files.writeString(valid, "<root><name>ok</name></root>");
        open(valid, xsd);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.revalidate();
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> panel.getProblemCount() == 0
                && "valid.xml".equals(host.getActiveDocument().map(OpenDocument::getDisplayName).orElse("")));
        assertEquals(0, panel.getProblemCount(), "valid XML must report no problems");
    }

    private static final String SCHEMATRON = """
            <sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron">
              <sch:pattern><sch:rule context="root">
                <sch:assert test="name">root must have a name child</sch:assert>
              </sch:rule></sch:pattern>
            </sch:schema>
            """;

    @Test
    void reportsSchematronProblems(@TempDir Path tmp) throws Exception {
        Path sch = tmp.resolve("rules.sch");
        Files.writeString(sch, SCHEMATRON);
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root/>");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("root")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveSchematron(sch.toFile());
            panel.revalidate();
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> panel.getProblemCount() > 0);
        assertTrue(panel.getProblemCount() > 0, "failing Schematron rule must report a problem");
    }

    @Test
    void batchValidationPopulatesResults(@TempDir Path tmp) throws Exception {
        Path sch = tmp.resolve("rules.sch");
        Files.writeString(sch, SCHEMATRON);
        Path bad = tmp.resolve("bad.xml");
        Files.writeString(bad, "<root/>");
        Path good = tmp.resolve("good.xml");
        Files.writeString(good, "<root><name>x</name></root>");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(bad));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("root")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveSchematron(sch.toFile());
            panel.runBatch(java.util.List.of(bad.toFile(), good.toFile()));
            return null;
        });
        // Poll the LAST side effect of showBatchResults (the header text) — asserting
        // a sibling effect of the same runLater right after the first one is racy.
        var header = (javafx.scene.control.Label) panel.lookup("#validation-results-header");
        WaitForAsyncUtils.waitFor(6, TimeUnit.SECONDS,
                () -> panel.batchResultCount() == 2 && header.getText().contains("1 OF 2 FAILED"));
        assertEquals(1, panel.batchFailedCount(), "only bad.xml fails the Schematron rule");
        assertTrue(header.getText().contains("1 OF 2 FAILED"), header.getText());
    }

    @Test
    void autoValidatesAfterChangesWhenLiveEnabled(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);
        Path invalid = tmp.resolve("invalid.xml");
        Files.writeString(invalid, "<root><wrong/></root>");

        // Open + bind schema, but do NOT call revalidate() manually — the debounced
        // continuous validation must report problems on its own.
        open(invalid, xsd);
        WaitForAsyncUtils.waitFor(6, TimeUnit.SECONDS, () -> panel.getProblemCount() > 0);
        assertTrue(panel.getProblemCount() > 0, "live validation must auto-report problems without a manual run");
    }

    @Test
    void liveValidationToggleIsOnByDefault() {
        WaitForAsyncUtils.waitForFxEvents();
        // The toggle lives in the ⋮ overflow menu (MenuItems are not in the scene graph).
        assertTrue(panel.overflowMenuItemTexts().contains("Validate while typing"),
                "the 'Validate while typing' toggle must exist in the overflow menu");
        assertTrue(panel.isLiveValidationEnabled(), "live validation must default to on");
    }

    @Test
    void exposesSchematronToolsInOverflowMenu() {
        WaitForAsyncUtils.waitForFxEvents();
        var texts = panel.overflowMenuItemTexts();
        for (String label : new String[]{"Rule Templates", "Tester", "Rule Builder",
                "Check Rules", "Documentation"}) {
            assertTrue(texts.contains(label),
                    "the ⋮ menu must offer the '" + label + "' Schematron tool: " + texts);
        }
    }

    @Test
    void opensSchematronTesterAsTab() {
        assertTrue(host.isEmpty(), "host starts empty");
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            panel.openSchematronTester();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(host.isEmpty(), "opening the Schematron tester must add a tab");
    }

    @Test
    void reValidatingWhileAProblemIsSelectedDoesNotCrash(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);
        Path invalid = tmp.resolve("invalid.xml");
        Files.writeString(invalid, "<root><wrong/></root>");
        open(invalid, xsd);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.revalidate();
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> panel.getProblemCount() > 0);

        // Select a problem, then re-validate repeatedly — must not throw the JavaFX
        // ListViewBehavior IndexOutOfBoundsException (items.setAll with a selection).
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            ((javafx.scene.control.ListView<?>) panel.lookup("#validation-problems-list"))
                    .getSelectionModel().select(0);
            return null;
        });
        for (int i = 0; i < 3; i++) {
            WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
                panel.revalidate();
                return null;
            });
            WaitForAsyncUtils.waitForFxEvents();
        }
        assertTrue(panel.getProblemCount() > 0, "still reports problems after re-validation");
    }

    @Test
    void validatesJsonAgainstAJsonSchema(@TempDir Path tmp) throws Exception {
        Path schema = tmp.resolve("schema.json");
        Files.writeString(schema, "{\"type\":\"object\",\"required\":[\"name\"],"
                + "\"properties\":{\"name\":{\"type\":\"string\"}}}");
        Path json = tmp.resolve("doc.json");
        Files.writeString(json, "{}"); // missing required 'name'

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(json));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("{}")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setJsonSchema(schema.toFile());
            panel.revalidate();
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> panel.getProblemCount() > 0);
        assertTrue(panel.getProblemCount() > 0, "JSON invalid against its schema must report problems");
    }

    @Test
    void setXsdViaPanelBindsAndValidates(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);
        Path invalid = tmp.resolve("invalid.xml");
        Files.writeString(invalid, "<root><wrong/></root>");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(invalid));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("root")).orElse(false));
        assertNull(host.activeSchemaProperty().get(), "no schema is bound from the document itself");

        // Choosing an XSD in the Validation panel binds it and validates the XML against it.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.useXsd(xsd.toFile());
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> panel.getProblemCount() > 0);

        assertEquals(xsd.toFile().getName(), host.activeSchemaProperty().get().getName(),
                "the chosen XSD must be bound to the document");
        assertTrue(panel.getProblemCount() > 0,
                "validating the XML against the chosen XSD must report the invalid element");
    }

    @Test
    void constructsWithFundsXmlEntryGatedOnFeatureFlag() {
        // The "Validate against FundsXML" ⋮-menu entry is conditional on the feature
        // flag and may be absent when it is off — construction must succeed either way.
        ValidationPanel[] built = new ValidationPanel[1];
        assertDoesNotThrow(() -> WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            built[0] = new ValidationPanel(host);
            return null;
        }));
        WaitForAsyncUtils.waitForFxEvents();
        assertNotNull(built[0], "ValidationPanel must construct");

        boolean fundsEntryPresent = built[0].overflowMenuItemTexts()
                .contains("Validate against FundsXML");
        assertEquals(FundsXmlRunner.isEnabled(), fundsEntryPresent,
                "the FundsXML validation entry must be present iff the feature flag is enabled");
    }

    @Test
    void clickingTheBoundXsdNameOpensItInTheEditor(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root><name>ok</name></root>");
        open(xml, xsd);

        javafx.scene.control.Label name = (javafx.scene.control.Label) panel.lookup(".fxt-vp-source-open");
        assertNotNull(name, "the XSD source name must be the clickable open-in-editor link");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            name.getOnMouseClicked().handle(new javafx.scene.input.MouseEvent(
                    javafx.scene.input.MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                    javafx.scene.input.MouseButton.PRIMARY, 1,
                    false, false, false, false, true, false, false, true, false, true, null));
            return null;
        });

        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> host.getOpenDocuments().stream()
                .anyMatch(d -> d.getPath() != null && d.getPath().equals(xsd)));
    }

    @Test
    void openingThePanelBindsTheReferencedXsd(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("auto.xsd");
        Files.writeString(xsd, XSD);
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root>x</root>"); // opened without a reference
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("root")).orElse(false));
        assertNull(host.activeSchemaProperty().get());

        // The reference appears later; re-creating the panel (= switching to the
        // Validation activity) must auto-bind the referenced schema.
        Files.writeString(xml, "<root xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + "xsi:noNamespaceSchemaLocation=\"auto.xsd\">x</root>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> new ValidationPanel(host));

        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> host.activeSchemaProperty().get() != null);
        assertEquals("auto.xsd", host.activeSchemaProperty().get().getName());
    }

    private void open(Path xml, Path xsd) throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("root")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.setSchemaForActiveDocument(xsd.toFile()));
    }
}
