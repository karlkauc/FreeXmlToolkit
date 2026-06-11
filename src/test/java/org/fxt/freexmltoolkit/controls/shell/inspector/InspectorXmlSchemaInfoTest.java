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
import org.fxt.freexmltoolkit.controls.shell.editor.XmlGridView;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Item 1 (deferred polish): when an XSD is bound to an XML document, selecting an XML element
 * shows read-only XSD-derived info (declared type + documentation).
 */
@ExtendWith(ApplicationExtension.class)
class InspectorXmlSchemaInfoTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="note" type="xs:string">
                <xs:annotation>
                  <xs:documentation>A short note element.</xs:documentation>
                </xs:annotation>
              </xs:element>
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
    void selectingXmlElementWithBoundSchemaShowsTypeAndDocumentation(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("note.xsd");
        Files.writeString(xsd, XSD);
        Path xml = tmp.resolve("note.xml");
        Files.writeString(xml, "<note>hello</note>\n");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("note")).orElse(false));

        // Bind the XSD explicitly (triggers async schema-provider load).
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.setSchemaForActiveDocument(xsd.toFile()));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.GRAPHIC);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        Thread.sleep(400);

        XmlGridView grid = (XmlGridView) host.lookupAll("*").stream()
                .filter(n -> n instanceof XmlGridView).findFirst().orElseThrow();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            XmlElement root = grid.getContext().getDocument().getRootElement();
            grid.getContext().getSelectionModel().setSelectedNode(root);
            return null;
        });

        // The provider loads on a background thread; allow generous time, then re-select to refresh.
        WaitForAsyncUtils.waitFor(6, TimeUnit.SECONDS, () -> {
            String type = inspector.getSchemaTypeText();
            return type != null && type.toLowerCase().contains("string");
        });
        assertTrue(inspector.getSchemaTypeText().toLowerCase().contains("string"),
                "schema-derived type must be shown, was: " + inspector.getSchemaTypeText());
        assertTrue(inspector.getSchemaDocText().toLowerCase().contains("short note"),
                "schema-derived documentation must be shown, was: " + inspector.getSchemaDocText());
    }
}
