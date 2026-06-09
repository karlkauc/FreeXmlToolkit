package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.inspector.InspectorPanel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * XSD parity with the XML Text view: moving the caret into an XSD construct in the Text view selects
 * the corresponding XsdNode, so the inspector shows it editable and an inspector edit round-trips to
 * the text — using the shared XSD model + command stack the Tree/Graphic views already use.
 */
@ExtendWith(ApplicationExtension.class)
class XsdTextCaretSelectsNodeTest {

    private static final String XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root" type="xs:string"/>
              <xs:complexType name="PersonType">
                <xs:sequence>
                  <xs:element name="firstName" type="xs:string"/>
                </xs:sequence>
              </xs:complexType>
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
    void caretInTopLevelElementSelectsItEditable(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("xs:element")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TEXT);
            return null;
        });

        int caret = XSD.indexOf("name=\"root\"");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.moveActiveCaretTo(caret));

        // The inspector shows the editable "root" element (not the read-only caret/XPath view).
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> "root".equals(inspector.getNodeNameText()) && "Element".equals(inspector.getKindText()));
        assertEquals("root", inspector.getNodeNameText());

        // An inspector edit on the caret-selected node round-trips to the text.
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.changeActiveType("xs:integer")));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().orElse("").contains("type=\"xs:integer\""));
    }

    @Test
    void caretInNestedElementAfterAnnotationResolvesCorrectly(@TempDir Path tmp) throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:annotation>
                    <xs:documentation>schema doc</xs:documentation>
                  </xs:annotation>
                  <xs:element name="alpha" type="xs:string"/>
                  <xs:complexType name="Wrapper">
                    <xs:sequence>
                      <xs:element name="beta" type="xs:string"/>
                    </xs:sequence>
                  </xs:complexType>
                </xs:schema>
                """;
        Path file = tmp.resolve("annotated.xsd");
        Files.writeString(file, xsd);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(file));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("beta")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TEXT);
            return null;
        });

        // The leading <xs:annotation> is folded in the model; the index path must still resolve
        // the nested <beta> element correctly (not a sibling shifted by the annotation).
        int caret = xsd.indexOf("name=\"beta\"");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.moveActiveCaretTo(caret));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> "beta".equals(inspector.getNodeNameText()));
        assertEquals("beta", inspector.getNodeNameText());
    }
}
