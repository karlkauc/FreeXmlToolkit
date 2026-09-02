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
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies {@link EditorHost#revealSchemaNodeByXPath(String)}: the Schema Analysis report
 * works on a freshly parsed model, so it navigates by the node's schema XPath
 * ({@code XsdNode.getXPath()}) rather than by node identity.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostRevealByXPathTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        stage.setScene(new Scene(new HBox(host), 1200, 760));
        stage.show();
    }

    private Path openSchema() throws Exception {
        Path xsd = Files.createTempFile("reveal-xpath", ".xsd");
        Files.writeString(xsd, """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:complexType name="OrphanType">
                    <xs:sequence>
                      <xs:element name="unused" type="xs:string"/>
                    </xs:sequence>
                  </xs:complexType>
                  <xs:element name="root">
                    <xs:complexType>
                      <xs:sequence>
                        <xs:element name="target" type="xs:string"/>
                      </xs:sequence>
                    </xs:complexType>
                  </xs:element>
                </xs:schema>
                """);
        xsd.toFile().deleteOnExit();
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(8, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("target")).orElse(false));
        return xsd;
    }

    @Test
    void revealsNestedNodeInTreeByXPath() throws Exception {
        openSchema();
        String xpath = "/xs:schema/xs:element[@name='root']/xs:complexType/xs:sequence/xs:element[@name='target']";
        boolean found = WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.revealSchemaNodeByXPath(xpath));
        assertTrue(found, "node should be found by XPath");
        WaitForAsyncUtils.waitFor(8, TimeUnit.SECONDS, () -> {
            WaitForAsyncUtils.waitForFxEvents();
            var sel = host.activeSelectedNodeProperty().get();
            return sel != null && "target".equals(sel.getName());
        });
        assertEquals(ViewMode.TREE, host.activeViewModeProperty().get());
        assertEquals("target", host.activeSelectedNodeProperty().get().getName());
    }

    @Test
    void revealsGlobalTypeByXPath() throws Exception {
        openSchema();
        boolean found = WaitForAsyncUtils.waitForAsyncFx(5000,
                () -> host.revealSchemaNodeByXPath("/xs:schema/xs:complexType[@name='OrphanType']"));
        assertTrue(found);
        WaitForAsyncUtils.waitFor(8, TimeUnit.SECONDS, () -> {
            WaitForAsyncUtils.waitForFxEvents();
            var sel = host.activeSelectedNodeProperty().get();
            return sel != null && "OrphanType".equals(sel.getName());
        });
        assertEquals("OrphanType", host.activeSelectedNodeProperty().get().getName());
    }

    @Test
    void unknownXPathReturnsFalse() throws Exception {
        openSchema();
        assertFalse(WaitForAsyncUtils.waitForAsyncFx(5000,
                () -> host.revealSchemaNodeByXPath("/xs:schema/xs:element[@name='nope']")));
        assertFalse(WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.revealSchemaNodeByXPath("")));
    }
}
