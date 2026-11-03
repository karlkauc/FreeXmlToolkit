package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.schema.XsdInstanceLocator;
import org.fxt.freexmltoolkit.controls.v2.editor.core.EditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.core.NavigationRequest;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * "Go to Definition" from an XML document opens the bound XSD in the Graphic view, expanded down
 * to the element's card and with that card selected — found by its instance path, so a local
 * declaration inside a named type is reached and same-named declarations are told apart.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostGoToDefinitionRevealTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root">
                <xs:complexType><xs:sequence>
                  <xs:element name="ControlData" type="ControlDataType"/>
                  <xs:element name="Fund" type="FundType"/>
                </xs:sequence></xs:complexType>
              </xs:element>
              <xs:complexType name="ControlDataType"><xs:sequence>
                <xs:element name="DocumentID" type="xs:string"/>
                <xs:element name="DataSupplier" type="DataSupplierType"/>
              </xs:sequence></xs:complexType>
              <xs:complexType name="DataSupplierType"><xs:sequence>
                <xs:element name="Short" type="xs:string"/>
                <xs:element name="Name" type="xs:string"/>
              </xs:sequence></xs:complexType>
              <xs:complexType name="FundType"><xs:sequence>
                <xs:element name="Names" type="NamesType"/>
              </xs:sequence></xs:complexType>
              <xs:complexType name="NamesType"><xs:sequence>
                <xs:element name="Name" type="xs:string"/>
              </xs:sequence></xs:complexType>
            </xs:schema>
            """;

    private static final String XML = """
            <root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="schema.xsd">
              <ControlData><DocumentID>1</DocumentID><DataSupplier><Short>S</Short><Name>Supplier</Name></DataSupplier></ControlData>
              <Fund><Names><Name>Fund name</Name></Names></Fund>
            </root>
            """;

    @TempDir
    Path tmp;

    private EditorHost host;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        stage.setScene(new Scene(new HBox(host), 1200, 760));
        stage.show();
    }

    private Path xsd;

    private void openBoundXml() throws Exception {
        xsd = Files.writeString(tmp.resolve("schema.xsd"), XSD);
        Path xml = Files.writeString(tmp.resolve("doc.xml"), XML);
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(8, TimeUnit.SECONDS, () -> host.activeSchemaProperty().get() != null);
    }

    /** An element of the same document, as the Graphic/Tree view would hand it over. */
    private static XmlElement element(String... path) {
        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString(XML);
        XmlElement element = context.getDocument().getRootElement();
        for (int i = 1; i < path.length; i++) {
            element = element.getChildElement(path[i]);
        }
        return element;
    }

    /** Waits until the XSD is active in the Graphic view with the card of {@code path} selected. */
    private XsdNode awaitRevealed(String... path) throws Exception {
        XsdNode[] expected = new XsdNode[1];
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            if (host.getActiveDocument().map(d -> !xsd.equals(d.getPath())).orElse(true)
                    || host.activeViewModeProperty().get() != ViewMode.GRAPHIC) {
                return false;
            }
            XsdNode root = host.getActiveSchemaRoot().orElse(null);
            if (!(root instanceof XsdSchema schema)) {
                return false;
            }
            expected[0] = XsdInstanceLocator.locateElement(schema, List.of(path))
                    .map(XsdInstanceLocator.Located::particle).orElse(null);
            return expected[0] != null && host.activeSelectedNodeProperty().get() == expected[0];
        }));
        return expected[0];
    }

    @Test
    void fromTheGraphicViewTheDeepLocalElementIsSelected() throws Exception {
        openBoundXml();

        WaitForAsyncUtils.waitForAsyncFx(3000, () -> host.goToXmlDefinition(
                element("root", "ControlData", "DataSupplier", "Name")));

        XsdNode selected = awaitRevealed("root", "ControlData", "DataSupplier", "Name");
        assertNotNull(selected);
        assertEquals("DataSupplierType", selected.getParent().getParent().getName(),
                "the Name inside DataSupplierType, not the first Name in the schema");
    }

    @Test
    void theSameNameElsewhereRevealsItsOwnDeclaration() throws Exception {
        openBoundXml();
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> host.goToXmlDefinition(
                element("root", "ControlData", "DataSupplier", "Name")));
        XsdNode supplierName = awaitRevealed("root", "ControlData", "DataSupplier", "Name");

        // back to the XML and the other Name: the XSD is already open now
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            host.openFile(tmp.resolve("doc.xml"));
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> host.getActiveDocument()
                .map(d -> d.getPath() != null && d.getPath().endsWith("doc.xml")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> host.goToXmlDefinition(element("root", "Fund", "Names", "Name")));

        XsdNode fundName = awaitRevealed("root", "Fund", "Names", "Name");
        assertNotSame(supplierName, fundName);
        assertEquals("NamesType", fundName.getParent().getParent().getName());
    }

    @Test
    void fromTheTextViewAnXPathRevealsTheDeclaration() throws Exception {
        openBoundXml();
        EditorContext ctx = WaitForAsyncUtils.waitForAsyncFx(2000, () ->
                host.activeEditorView() instanceof XmlEditorView xev ? xev.getEditorContext() : null);
        assertNotNull(ctx);

        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            ctx.getGoToDefinitionHandler().accept(
                    new NavigationRequest(xsd.toFile(), "Name", "/root/Fund/Names/Name"));
            return null;
        });

        XsdNode selected = awaitRevealed("root", "Fund", "Names", "Name");
        assertSame(selected, host.activeSelectedNodeProperty().get());
    }
}
