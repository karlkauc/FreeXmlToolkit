package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.schema.overview.SchemaOverviewView;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.GlobalComponentKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The XSD Overview view mode inside the editor host: offered for XSD only, edits round-trip
 * to the text, undo is shared with the other views, the Inspector follows the selection.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostSchemaOverviewTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root" type="xs:string"/>
              <xs:simpleType name="Code">
                <xs:restriction base="xs:string"/>
              </xs:simpleType>
            </xs:schema>
            """;

    private EditorHost host;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        stage.setScene(new Scene(host, 1100, 700));
        stage.show();
    }

    @Test
    void overviewEditsRoundTripAndUndo(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root/>");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("root")).orElse(false));
        assertFalse(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeSupportsView(ViewMode.OVERVIEW)),
                "Overview is an XSD view");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("Code")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.OVERVIEW);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        SchemaOverviewView overview = WaitForAsyncUtils.waitForAsyncFx(2000, () ->
                (SchemaOverviewView) host.lookupAll("#schema-overview").stream()
                        .filter(javafx.scene.Node::isVisible).findFirst().orElseThrow());

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> overview.addComponent(GlobalComponentKind.COMPLEX_TYPE, false));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("<xs:complexType name=\"NewComplexType\">")).orElse(false));
        assertEquals("NewComplexType", WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.activeSelectedNodeProperty().get() != null
                        ? host.activeSelectedNodeProperty().get().getName() : null),
                "the Inspector follows the overview's selection");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.undoActive();
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> !t.contains("NewComplexType")).orElse(false));
        assertEquals(2, WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> overview.lookupAll("#overview-components").stream().findFirst()
                        .map(n -> ((javafx.scene.control.TableView<?>) n).getItems().size()).orElse(-1)));
    }
}
