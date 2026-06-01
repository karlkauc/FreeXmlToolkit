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
    void batchValidationOpensReport(@TempDir Path tmp) throws Exception {
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
        WaitForAsyncUtils.waitFor(6, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("Batch Validation Report")).orElse(false));
        assertTrue(host.getActiveText().orElse("").contains("good.xml: valid"));
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
        boolean present = panel.lookupAll(".check-box").stream()
                .anyMatch(n -> n instanceof javafx.scene.control.CheckBox cb
                        && "Validate while typing".equals(cb.getText()) && cb.isSelected());
        assertTrue(present, "the 'Validate while typing' toggle must exist and default to on");
    }

    @Test
    void exposesSchematronToolButtons() {
        WaitForAsyncUtils.waitForFxEvents();
        for (String label : new String[]{"Rule Templates", "Tester", "Rule Builder"}) {
            boolean present = panel.lookupAll(".button").stream()
                    .anyMatch(n -> n instanceof javafx.scene.control.Button b && label.equals(b.getText()));
            assertTrue(present, "Validation panel must offer the '" + label + "' Schematron tool");
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
            ((javafx.scene.control.ListView<?>) panel.lookup(".list-view"))
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

    private void open(Path xml, Path xsd) throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("root")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.setSchemaForActiveDocument(xsd.toFile()));
    }
}
