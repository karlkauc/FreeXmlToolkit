package org.fxt.freexmltoolkit.controls.shell.inspector;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Schema facts of an XML element's attributes in the inspector: the declared type as a badge
 * ("*" = required), attributes the schema declares but the element does not set as "+ name"
 * chips (a click adds one), and "No documentation in the schema" instead of an empty section.
 */
@ExtendWith(ApplicationExtension.class)
class InspectorXmlAttributeSchemaTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="item" maxOccurs="unbounded">
                      <xs:complexType>
                        <xs:attribute name="id" type="xs:ID" use="required"/>
                        <xs:attribute name="lang" default="en">
                          <xs:simpleType>
                            <xs:restriction base="xs:string">
                              <xs:enumeration value="en"/>
                              <xs:enumeration value="de"/>
                            </xs:restriction>
                          </xs:simpleType>
                        </xs:attribute>
                        <xs:attribute name="note" type="xs:string"/>
                      </xs:complexType>
                    </xs:element>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    private static final String XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <root>
              <item lang="de"/>
            </root>
            """;

    private EditorHost host;
    private InspectorPanel inspector;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        inspector = new InspectorPanel(host);
        stage.setScene(new Scene(new HBox(host, inspector), 1300, 900));
        stage.show();
    }

    @Test
    void attributesCarryTheirSchemaFacts(@TempDir Path dir) throws Exception {
        Path xsd = dir.resolve("a.xsd");
        Files.writeString(xsd, XSD);
        Path xml = dir.resolve("a.xml");
        Files.writeString(xml, XML);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(8, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("<item")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.setSchemaForActiveDocument(xsd.toFile()));
        WaitForAsyncUtils.asyncFx(() -> host.goToLine(3, 5));
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> "item".equals(inspector.getNodeNameText())
                && !inspector.getMissingAttributeChips().isEmpty());
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(List.of("+ id *", "+ note"), inspector.getMissingAttributeChips());
        assertTrue(inspector.getXmlAttributeBadges().containsKey("lang"));
        assertEquals("1", inspector.getDepthText());
        assertTrue(inspector.isDocSectionVisible());
        assertEquals("No documentation in the schema", inspector.getSchemaDocText());

        // A click on the required attribute's chip adds it to the element.
        javafx.scene.control.Button id = WaitForAsyncUtils.waitForAsyncFx(2000, () -> inspector
                .lookupAll(".fxt-inspector-chip").stream().map(n -> (javafx.scene.control.Button) n)
                .filter(b -> b.getText().startsWith("+ id")).findFirst().orElseThrow());
        WaitForAsyncUtils.waitForAsyncFx(2000, id::fire);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("id=\"\"")).orElse(false));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> inspector.getMissingAttributeChips().equals(List.of("+ note")));
    }
}
