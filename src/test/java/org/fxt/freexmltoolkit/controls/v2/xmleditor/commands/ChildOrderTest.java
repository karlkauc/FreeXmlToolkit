package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XsdSchemaAdapter;
import org.fxt.freexmltoolkit.service.XsdDocumentationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Where a missing child element is inserted: its schema position, not the end. */
class ChildOrderTest {

    private static final List<String> POSITION_ORDER = List.of(
            "UniqueID", "Identifiers", "Currency", "TotalValue", "TotalPercentage", "Account", "PrivateEquity",
            "Fee", "CustomAttributes");

    @TempDir
    Path dir;

    private static XmlElement position(String xml) {
        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString(xml);
        return context.getDocument().getRootElement();
    }

    private static List<String> names(XmlElement element) {
        return element.getChildElements().stream().map(XmlElement::getName).toList();
    }

    @Test
    void aMissingElementGoesBeforeTheFirstLaterOne() {
        XmlElement p = position("""
                <Position>
                  <UniqueID>GREENPEAK</UniqueID>
                  <Currency>EUR</Currency>
                  <TotalValue><Amount>4700</Amount></TotalValue>
                  <PrivateEquity/>
                  <CustomAttributes/>
                </Position>""");
        int index = ChildOrder.insertionIndex(p, "TotalPercentage", POSITION_ORDER);
        XmlNode atIndex = p.getChildren().get(index);
        assertEquals("PrivateEquity", ((XmlElement) atIndex).getName(), "right before PrivateEquity");

        assertTrue(new AddElementInOrderCommand(p, new XmlElement("TotalPercentage"), POSITION_ORDER).execute());
        assertEquals(List.of("UniqueID", "Currency", "TotalValue", "TotalPercentage", "PrivateEquity",
                "CustomAttributes"), names(p));
    }

    @Test
    void repeatedElementsGoAfterTheLastOfTheirName() {
        XmlElement list = position("<l><a/><b/><b/><c/></l>");
        assertEquals(3, ChildOrder.insertionIndex(list, "b", List.of("a", "b", "c")));
        assertEquals(3, ChildOrder.insertionIndex(list, "b", List.of()), "even without an order");
    }

    @Test
    void unknownNamesOrNothingLaterAppend() {
        XmlElement p = position("<p><a/><b/></p>");
        assertEquals(-1, ChildOrder.insertionIndex(p, "x", List.of("a", "b")));
        assertEquals(-1, ChildOrder.insertionIndex(p, "c", List.of("a", "b", "c")));
        assertEquals(-1, ChildOrder.insertionIndex(p, "z", List.of()), "no order at all");
    }

    @Test
    void prefixesAreIgnored() {
        XmlElement p = position("<f:p xmlns:f=\"urn:f\"><f:a/><f:c/></f:p>");
        assertEquals(1, ChildOrder.insertionIndex(p, "f:b", List.of("a", "b", "c")));
    }

    @Test
    void undoRemovesTheInsertedElement() {
        XmlElement p = position("<p><a/><c/></p>");
        AddElementInOrderCommand cmd = new AddElementInOrderCommand(p, new XmlElement("b"), List.of("a", "b", "c"));
        assertTrue(cmd.execute());
        assertEquals(List.of("a", "b", "c"), names(p));
        assertTrue(cmd.undo());
        assertEquals(List.of("a", "c"), names(p));
    }

    @Test
    void mergeKeepsThePrimaryOrderAndFillsInTheFallback() {
        assertEquals(List.of("a", "x", "b", "c"), ChildOrder.merge(List.of("a", "b", "c"), List.of("x", "b")));
        assertEquals(List.of("a", "b", "y"), ChildOrder.merge(List.of("a", "b"), List.of("y")));
        assertEquals(List.of("a", "b"), ChildOrder.merge(null, List.of("a", "b")));
    }

    @Test
    void theSchemaOrderComesFromTheBoundXsd() throws Exception {
        Path xsd = dir.resolve("p.xsd");
        Files.writeString(xsd, """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="Position"><xs:complexType><xs:sequence>
                    <xs:element name="UniqueID" type="xs:string"/>
                    <xs:element name="Currency" type="xs:string"/>
                    <xs:element name="TotalPercentage" type="xs:decimal" minOccurs="0"/>
                    <xs:choice>
                      <xs:element name="Account" type="xs:string"/>
                      <xs:element name="PrivateEquity" type="xs:string"/>
                    </xs:choice>
                  </xs:sequence></xs:complexType></xs:element>
                </xs:schema>""");
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsd.toString());
        service.processXsd(Boolean.TRUE);
        XsdSchemaAdapter schema = new XsdSchemaAdapter();
        schema.setXsdDocumentationData(service.xsdDocumentationData);

        List<String> order = ChildOrder.orderFor(schema, "/Position", List.of("Extra"));
        assertEquals(List.of("UniqueID", "Currency", "TotalPercentage", "Account", "PrivateEquity", "Extra"), order);
        assertEquals(List.of("Extra"), ChildOrder.orderFor(null, "/Position", List.of("Extra")));
    }
}
