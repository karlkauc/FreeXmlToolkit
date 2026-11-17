package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XsdSchemaAdapter;
import org.fxt.freexmltoolkit.service.XsdDocumentationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A value typed into an empty grid cell creates the missing child element where the schema declares it
 * (FundsXML: {@code TotalPercentage} right after {@code TotalValue}), not at the end of the record.
 */
class XmlGridChildOrderTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="Positions"><xs:complexType><xs:sequence>
                <xs:element name="Position" maxOccurs="unbounded"><xs:complexType><xs:sequence>
                  <xs:element name="UniqueID" type="xs:string"/>
                  <xs:element name="Identifiers" minOccurs="0"><xs:complexType><xs:sequence>
                    <xs:element name="ISIN" type="xs:string"/>
                  </xs:sequence></xs:complexType></xs:element>
                  <xs:element name="Currency" type="xs:string" minOccurs="0"/>
                  <xs:element name="TotalValue" minOccurs="0"><xs:complexType><xs:sequence>
                    <xs:element name="Amount" type="xs:decimal"/>
                  </xs:sequence></xs:complexType></xs:element>
                  <xs:element name="TotalPercentage" type="xs:decimal" minOccurs="0"/>
                  <xs:choice minOccurs="0">
                    <xs:element name="Account" type="xs:string"/>
                    <xs:element name="PrivateEquity" type="xs:string"/>
                    <xs:element name="Fee" type="xs:string"/>
                  </xs:choice>
                  <xs:element name="CustomAttributes" type="xs:string" minOccurs="0"/>
                </xs:sequence></xs:complexType></xs:element>
              </xs:sequence></xs:complexType></xs:element>
            </xs:schema>
            """;

    /** The user's example, shortened. */
    private static final String XML = """
            <Positions>
              <Position>
                <UniqueID>Cash</UniqueID>
                <Currency>EUR</Currency>
                <TotalValue><Amount>2334994.29</Amount></TotalValue>
                <TotalPercentage>103.84</TotalPercentage>
                <Account/>
              </Position>
              <Position>
                <UniqueID>GREENPEAK</UniqueID>
                <Identifiers><ISIN>YYGPKATANIA9</ISIN></Identifiers>
                <Currency>EUR</Currency>
                <TotalValue><Amount>4700</Amount></TotalValue>

                <PrivateEquity/>
                <CustomAttributes>x</CustomAttributes>
              </Position>
            </Positions>
            """;

    @TempDir
    Path dir;

    private XsdSchemaAdapter schema;

    @BeforeEach
    void setUp() throws Exception {
        Path xsd = dir.resolve("positions.xsd");
        Files.writeString(xsd, XSD);
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsd.toString());
        service.processXsd(Boolean.TRUE);
        schema = new XsdSchemaAdapter();
        schema.setXsdDocumentationData(service.xsdDocumentationData);
        RepeatingElementsTable.clearAllCaches();
    }

    private XmlEditorContext load(String xml, boolean withSchema) {
        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString(xml);
        if (withSchema) {
            context.setSchemaProvider(schema);
        }
        return context;
    }

    private static RepeatingElementsTable table(XmlEditorContext context) {
        XmlElement root = context.getDocument().getRootElement();
        return new RepeatingElementsTable("Position", root.getChildElements("Position"), 1, () -> { });
    }

    private static List<String> names(XmlElement element) {
        return element.getChildElements().stream().map(XmlElement::getName).toList();
    }

    @Test
    void typedPercentageGoesRightAfterTotalValue() {
        for (boolean withSchema : new boolean[] {true, false}) {
            XmlEditorContext context = load(XML, withSchema);
            RepeatingElementsTable table = table(context);
            assertTrue(new XmlGridAdapter(context).commitCellEdit(table, 1, "TotalPercentage", "0.21"));
            XmlElement second = context.getDocument().getRootElement().getChildElements("Position").get(1);
            assertEquals(List.of("UniqueID", "Identifiers", "Currency", "TotalValue", "TotalPercentage",
                    "PrivateEquity", "CustomAttributes"), names(second), withSchema ? "schema" : "column order");
            String text = context.serializeToString();
            assertTrue(text.indexOf("<TotalPercentage>0.21") > text.indexOf("<Amount>4700"), text);
            assertTrue(text.indexOf("<TotalPercentage>0.21") < text.indexOf("<PrivateEquity"), text);
        }
    }

    @Test
    void theSchemaWinsOverTheColumnOrder() {
        // The columns put TotalPercentage before TotalValue (it only occurs in a record without TotalValue)
        XmlEditorContext context = load("""
                <Positions>
                  <Position><UniqueID>a</UniqueID><TotalPercentage>1</TotalPercentage></Position>
                  <Position><UniqueID>b</UniqueID><TotalValue><Amount>1</Amount></TotalValue><Fee/></Position>
                </Positions>""", true);
        RepeatingElementsTable table = table(context);
        assertTrue(new XmlGridAdapter(context).commitCellEdit(table, 1, "TotalPercentage", "2"));
        XmlElement second = context.getDocument().getRootElement().getChildElements("Position").get(1);
        assertEquals(List.of("UniqueID", "TotalValue", "TotalPercentage", "Fee"), names(second));
    }

    @Test
    void entryHelperChildrenAreAddedAtTheirSchemaPlace() {
        XmlEditorContext context = load(XML, true);
        XmlElement second = context.getDocument().getRootElement().getChildElements("Position").get(1);
        var cmd = EntryHelperModel.addChild(schema, new EntryHelperModel.Focus(second, null, null, null),
                new EntryHelperModel.Entry(EntryHelperModel.Kind.ELEMENT, "TotalPercentage", false, true));
        assertNotNull(cmd);
        assertTrue(context.executeCommand(cmd));
        assertEquals("TotalPercentage", names(second).get(4));
    }
}
