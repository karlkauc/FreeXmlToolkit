package org.fxt.freexmltoolkit.controls.shell.inspector;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.Spinner;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.OpenDocument;
import org.fxt.freexmltoolkit.controls.shell.editor.ViewMode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdFacetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Editing the bound XSD from an XML document: with the caret on an element, the inspector shows
 * where it is declared and its XSD rows edit that declaration. The change lands in the schema's
 * tab (opened behind the XML tab), as one undo step there; included declarations stay read-only.
 */
@ExtendWith(ApplicationExtension.class)
class SchemaEditFromXmlTest {

    private static final String XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:include schemaLocation="part.xsd"/>
              <xs:simpleType name="CodeType">
                <xs:restriction base="xs:string">
                  <xs:maxLength value="3"/>
                </xs:restriction>
              </xs:simpleType>
              <xs:element name="order">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="item" type="xs:string" maxOccurs="1"/>
                    <xs:element name="code" type="CodeType"/>
                    <xs:element name="other" type="CodeType"/>
                    <xs:element ref="part"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    private static final String PART = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="part" type="xs:string"/>
            </xs:schema>
            """;

    private static final String XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <order>
              <item>one</item>
              <code>AB</code>
              <other>CD</other>
              <part>p</part>
            </order>
            """;

    @TempDir
    Path dir;

    private Path xml;
    private Path xsd;
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

    @BeforeEach
    void files() throws Exception {
        xsd = dir.resolve("order.xsd");
        Files.writeString(xsd, XSD);
        Files.writeString(dir.resolve("part.xsd"), PART);
        xml = dir.resolve("order.xml");
        Files.writeString(xml, XML);
    }

    /** Opens the XML, binds the XSD and puts the caret on {@code line}; waits for the declaration. */
    private void caretOn(int line, String element) throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(8, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("<order>")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.setSchemaForActiveDocument(xsd.toFile()));
        WaitForAsyncUtils.asyncFx(() -> host.goToLine(line, 6));
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> element.equals(inspector.getNodeNameText())
                && !inspector.getDeclarationText().isEmpty());
        WaitForAsyncUtils.waitForFxEvents();
    }

    private OpenDocument schemaDocument() {
        return host.getOpenDocuments().stream()
                .filter(d -> d.getPath() != null && d.getPath().getFileName().toString().equals("order.xsd"))
                .findFirst().orElse(null);
    }

    /** @return the schema tab's text (brings it to the front) */
    private String schemaText() throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        return WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveText().orElse(""));
    }

    @Test
    void cardinalityEditLandsInTheSchemaTabBehindTheXml() throws Exception {
        caretOn(3, "item");
        assertTrue(inspector.getDeclarationText().startsWith("Declared in order.xsd · local element"),
                inspector.getDeclarationText());
        assertNull(schemaDocument(), "nothing is opened just by looking");

        Spinner<?> max = (Spinner<?>) inspector.lookup("#inspector-max");
        assertTrue(max.isVisible());
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> ((Spinner<Integer>) max).getValueFactory().setValue(5));

        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> schemaDocument() != null && schemaDocument().isDirty());
        assertEquals("order.xml", host.getActiveDocument().map(OpenDocument::getDisplayName).orElse(""),
                "the XML document keeps the focus");
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> host.resolveActiveXmlSchemaDeclaration(
                "/order/item", null).map(d -> d.located().particle().getMaxOccurs() == 5).orElse(false));

        String text = schemaText();
        assertTrue(text.contains("name=\"item\"") && text.contains("maxOccurs=\"5\""), text);
        WaitForAsyncUtils.waitForAsyncFx(2000, host::undoActive);
        String undone = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveText().orElse(""));
        assertFalse(undone.contains("maxOccurs=\"5\""), "one undo step in the schema tab reverts it");
    }

    @Test
    void facetsOfANamedTypeAreEditedOnTheType() throws Exception {
        caretOn(4, "code");
        assertTrue(inspector.getFacetHintText().contains("CodeType"), inspector.getFacetHintText());
        assertEquals(1, inspector.getFacetCount(), "maxLength of CodeType, editable here");
        assertEquals(0, inspector.getInheritedFacetCount());

        // CodeType is used by two elements; the inspector asks before changing it — the API does not.
        boolean[] ok = {false};
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.editActiveXmlSchemaDeclaration("/order/code", null,
                t -> t.addFacet(true, XsdFacetType.MIN_LENGTH, "2"), r -> ok[0] = r));
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> ok[0]);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> inspector.getFacetCount() == 2);

        String text = schemaText();
        int type = text.indexOf("name=\"CodeType\"");
        int facet = text.indexOf("<xs:minLength value=\"2\"/>");
        assertTrue(type >= 0 && facet > type && facet < text.indexOf("</xs:simpleType>", type), text);
    }

    @Test
    void includedDeclarationsStayReadOnly() throws Exception {
        caretOn(6, "part");
        assertTrue(inspector.getDeclarationText().contains("included or imported"), inspector.getDeclarationText());
        boolean[] done = {false};
        boolean[] ok = {true};
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.editActiveXmlSchemaDeclaration("/order/part", null,
                t -> t.changeType("xs:int"), r -> {
                    ok[0] = r;
                    done[0] = true;
                }));
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> done[0]);
        assertFalse(ok[0], "an included declaration is never written through the main schema");
    }

    @Test
    void openInSchemaShowsTheGraphicViewAtTheNode() throws Exception {
        caretOn(4, "code");
        WaitForAsyncUtils.waitForAsyncFx(2000, inspector::onOpenInSchema);
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> host.getActiveDocument()
                .map(d -> "order.xsd".equals(d.getDisplayName())).orElse(false)
                && host.activeSelectedNodeProperty().get() != null);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(ViewMode.GRAPHIC, host.activeViewModeProperty().get());
        XsdNode selected = host.activeSelectedNodeProperty().get();
        assertEquals("code", selected.getName());
        assertEquals("order", selected.getParent().getParent().getParent().getName(),
                "the local particle inside order's sequence");
    }
}
