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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** Phase 4 (UI): the inspector lists schema-valid children and example values for a bound element. */
@ExtendWith(ApplicationExtension.class)
class InspectorXmlSchemaAssistTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="alpha" type="xs:string"/>
                    <xs:element name="beta" type="xs:string"/>
                  </xs:sequence>
                </xs:complexType>
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
    void inspectorListsValidChildren(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root/>\n");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("root")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.setSchemaForActiveDocument(xsd.toFile()));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.GRAPHIC);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        // lookupAll must run on the FX thread (scene graph may still be mutating)
        XmlGridView grid = WaitForAsyncUtils.waitForAsyncFx(2000, () -> (XmlGridView) host.lookupAll("*").stream()
                .filter(n -> n instanceof XmlGridView).findFirst().orElseThrow());
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            grid.getContext().getSelectionModel().setSelectedNode(grid.getContext().getDocument().getRootElement());
            return null;
        });

        // Wait for the off-thread schema provider, then re-select to drive an inspector refresh.
        WaitForAsyncUtils.waitFor(12, TimeUnit.SECONDS,
                () -> host.resolveValidChildren("/root").size() >= 2);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            grid.getContext().getSelectionModel().clearSelection();
            grid.getContext().getSelectionModel().setSelectedNode(grid.getContext().getDocument().getRootElement());
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> inspector.getValidChildCount() >= 2);
        assertTrue(inspector.getValidChildCount() >= 2, "valid children (alpha, beta) must be listed");
    }

    private static final String MULTI_LANG_XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="alpha" type="xs:string">
                      <xs:annotation>
                        <xs:documentation xml:lang="en">The alpha value.</xs:documentation>
                        <xs:documentation xml:lang="de">Der Alpha-Wert.</xs:documentation>
                      </xs:annotation>
                    </xs:element>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    @Test
    void multiLanguageDocumentationShowsLanguageTags(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, MULTI_LANG_XSD);
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root><alpha>x</alpha></root>\n");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("alpha")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.setSchemaForActiveDocument(xsd.toFile()));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.GRAPHIC);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        XmlGridView grid = WaitForAsyncUtils.waitForAsyncFx(2000, () -> (XmlGridView) host.lookupAll("*").stream()
                .filter(n -> n instanceof XmlGridView).findFirst().orElseThrow());

        // Wait for the off-thread schema provider, then select alpha to drive the inspector.
        WaitForAsyncUtils.waitFor(12, TimeUnit.SECONDS,
                () -> host.resolveActiveXmlElementDocs("/root/alpha").size() >= 2);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            var root = grid.getContext().getDocument().getRootElement();
            var alpha = root.getChildren().stream()
                    .filter(n -> n instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement e
                            && "alpha".equals(e.getName()))
                    .findFirst().orElseThrow();
            grid.getContext().getSelectionModel().setSelectedNode(alpha);
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS,
                () -> inspector.getSchemaDocText().contains("[EN]"));
        String doc = inspector.getSchemaDocText();
        assertTrue(doc.contains("[EN] The alpha value."), "English entry must be tagged: " + doc);
        assertTrue(doc.contains("[DE] Der Alpha-Wert."), "German entry must be tagged: " + doc);
    }
}
