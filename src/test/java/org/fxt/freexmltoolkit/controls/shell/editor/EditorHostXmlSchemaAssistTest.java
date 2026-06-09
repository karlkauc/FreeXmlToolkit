package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Phase 4: when a schema is bound, the inspector helper lists resolve the element's valid child
 * elements (from the XSD) and adding one round-trips to the text.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostXmlSchemaAssistTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="alpha" type="xs:string" maxOccurs="unbounded"/>
                    <xs:element name="beta" type="xs:string" minOccurs="0"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    private EditorHost host;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        stage.setScene(new Scene(host, 900, 600));
        stage.show();
    }

    @Test
    void validChildrenResolveAndAddRoundTrips(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root/>\n");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("root")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.setSchemaForActiveDocument(xsd.toFile()));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.GRID);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        XmlGridView grid = (XmlGridView) host.lookupAll("*").stream()
                .filter(n -> n instanceof XmlGridView).findFirst().orElseThrow();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            XmlElement root = grid.getContext().getDocument().getRootElement();
            grid.getContext().getSelectionModel().setSelectedNode(root);
            return null;
        });

        // The schema provider loads off-thread; wait for the valid children to resolve.
        WaitForAsyncUtils.waitFor(12, TimeUnit.SECONDS,
                () -> host.resolveValidChildren("/root").contains("alpha"));
        List<String> children = host.resolveValidChildren("/root");
        assertTrue(children.contains("alpha") && children.contains("beta"),
                "valid children must resolve from the XSD, was: " + children);

        // Adding a valid child element round-trips to the text.
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.addActiveXmlChildElement("alpha")));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().orElse("").contains("<alpha"));
    }
}
