package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XsdSchemaAdapter;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.widgets.EnumerationComboBox;
import org.fxt.freexmltoolkit.service.XsdDocumentationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Attributes with an enumeration are edited with a dropdown on every way into the grid: attribute
 * rows of the tree, attribute columns of tables, rows inside opened cells and tables inside opened
 * cells (the FundsXML {@code OtherID/@ListedType} pattern: a simpleContent extension). A free-text
 * attribute ({@code @FreeType}) stays a text field; element columns of a table look their type up at
 * the child element.
 */
@ExtendWith(ApplicationExtension.class)
class XmlGridAttributeEnumerationEditTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="Positions">
                <xs:complexType><xs:sequence>
                  <xs:element name="Position" maxOccurs="unbounded">
                    <xs:complexType>
                      <xs:sequence>
                        <xs:element name="ID" type="xs:string"/>
                        <xs:element name="Identifiers">
                          <xs:complexType><xs:sequence>
                            <xs:element name="ISIN" type="xs:string" minOccurs="0"/>
                            <xs:element name="OtherID" minOccurs="0" maxOccurs="unbounded">
                              <xs:complexType><xs:simpleContent><xs:extension base="xs:string">
                                <xs:attribute name="ListedType" type="ListedIdentifierType"/>
                                <xs:attribute name="FreeType" type="Text100Type"/>
                              </xs:extension></xs:simpleContent></xs:complexType>
                            </xs:element>
                          </xs:sequence></xs:complexType>
                        </xs:element>
                        <xs:element name="Kind" type="KindType"/>
                      </xs:sequence>
                      <xs:attribute name="status" type="StatusType"/>
                    </xs:complexType>
                  </xs:element>
                </xs:sequence></xs:complexType>
              </xs:element>
              <xs:simpleType name="ListedIdentifierType">
                <xs:restriction base="xs:string">
                  <xs:enumeration value="AIF CODE"/><xs:enumeration value="CUSIP"/><xs:enumeration value="SEDOL"/>
                </xs:restriction>
              </xs:simpleType>
              <xs:simpleType name="Text100Type">
                <xs:restriction base="xs:string"><xs:maxLength value="100"/></xs:restriction>
              </xs:simpleType>
              <xs:simpleType name="KindType">
                <xs:restriction base="xs:string"><xs:enumeration value="BOND"/><xs:enumeration value="EQUITY"/></xs:restriction>
              </xs:simpleType>
              <xs:simpleType name="StatusType">
                <xs:restriction base="xs:string"><xs:enumeration value="open"/><xs:enumeration value="closed"/></xs:restriction>
              </xs:simpleType>
            </xs:schema>
            """;

    private static final String ONE = "<Position status=\"open\"><ID>1</ID><Identifiers><ISIN>X1</ISIN>"
            + "<OtherID ListedType=\"CUSIP\" FreeType=\"Name\">A</OtherID></Identifiers><Kind>BOND</Kind></Position>";
    private static final String TWO = "<Position status=\"closed\"><ID>2</ID><Identifiers><ISIN>X2</ISIN>"
            + "<OtherID ListedType=\"CUSIP\">B</OtherID><OtherID ListedType=\"SEDOL\">C</OtherID></Identifiers>"
            + "<Kind>EQUITY</Kind></Position>";

    @TempDir
    Path dir;

    private Stage stage;
    private XmlEditorContext context;
    private XmlCanvasView view;
    private XmlGridAdapter adapter;

    @Start
    void start(Stage stage) {
        this.stage = stage;
    }

    private void load(String xml) throws Exception {
        Path xsd = dir.resolve("positions.xsd");
        Files.writeString(xsd, XSD);
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsd.toString());
        service.processXsd(Boolean.TRUE);
        XsdSchemaAdapter schema = new XsdSchemaAdapter();
        schema.setXsdDocumentationData(service.xsdDocumentationData);
        fx(() -> {
            RepeatingElementsTable.clearAllCaches();
            context = new XmlEditorContext();
            context.loadDocumentFromString(xml);
            context.setSchemaProvider(schema);
            adapter = new XmlGridAdapter(context);
            view = new XmlCanvasView(context);
            stage.setScene(new Scene(view, 1100, 700));
            stage.show();
            view.expandAll();
            return null;
        });
    }

    private <T> T fx(java.util.concurrent.Callable<T> action) {
        T result = WaitForAsyncUtils.waitForAsyncFx(10000, action);
        WaitForAsyncUtils.waitForFxEvents();
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<String> dropdownValues(GridModelAdapter.EditSpec spec) {
        assertNotNull(spec);
        assertInstanceOf(EnumerationComboBox.class, spec.widget(), "an enumeration is edited with a dropdown");
        return List.copyOf(((ComboBox<String>) spec.widget().getNode()).getItems());
    }

    private static FlatRow attributeRow(List<FlatRow> rows, String owner, String name) {
        return rows.stream().filter(r -> r.getType() == FlatRow.RowType.ATTRIBUTE && name.equals(r.getLabel())
                && r.getParentRow() != null && owner.equals(r.getParentRow().getLabel())).findFirst().orElseThrow();
    }

    private RepeatingElementsTable rootTable() {
        return view.visibleRowList().stream().filter(r -> "Position".equals(r.getLabel()) && r.hasRepeatingTable())
                .findFirst().orElseThrow().getRepeatingTable();
    }

    private static String attributeColumn(RepeatingElementsTable table, String attribute) {
        return table.getColumns().stream().filter(c -> c.getType() == RepeatingElementsTable.ColumnType.ATTRIBUTE
                && c.getDisplayName().endsWith(attribute)).findFirst().orElseThrow().getName();
    }

    @Test
    void attributeRowsOfTheTree() throws Exception {
        load("<Positions>" + ONE + "</Positions>");
        List<FlatRow> rows = fx(() -> List.copyOf(view.visibleRowList()));
        assertEquals(List.of("AIF CODE", "CUSIP", "SEDOL"),
                fx(() -> dropdownValues(adapter.editSpec(attributeRow(rows, "OtherID", "ListedType"), false))));
        assertFalse(fx(() -> adapter.editSpec(attributeRow(rows, "OtherID", "FreeType"), false).widget())
                instanceof EnumerationComboBox, "free text stays a text field");
        assertEquals(List.of("open", "closed"),
                fx(() -> dropdownValues(adapter.editSpec(attributeRow(rows, "Position", "status"), false))));
    }

    @Test
    void attributeColumnsOfATable() throws Exception {
        load("<Positions>" + ONE + TWO + "</Positions>");
        assertEquals(List.of("open", "closed"), fx(() -> {
            RepeatingElementsTable table = rootTable();
            return dropdownValues(adapter.cellEditSpec(table, 1, attributeColumn(table, "status")));
        }));
        assertEquals(List.of("BOND", "EQUITY"), fx(() -> dropdownValues(adapter.cellEditSpec(rootTable(), 0, "Kind"))),
                "element enumerations keep their dropdown");
    }

    @Test
    void rowsAndTablesInsideOpenedCells() throws Exception {
        load("<Positions>" + ONE + TWO + "</Positions>");
        // Row 1: Identifiers holds one OtherID → its attribute rows in the opened cell
        GridBlock first = fx(() -> {
            rootTable().getRows().get(0).toggleColumnExpanded("Identifiers");
            view.rebuild();
            return rootTable().getRows().get(0).getCellGrid("Identifiers");
        });
        assertEquals(List.of("AIF CODE", "CUSIP", "SEDOL"),
                fx(() -> dropdownValues(adapter.editSpec(attributeRow(first.rows(), "OtherID", "ListedType"), false))));
        assertFalse(fx(() -> adapter.editSpec(attributeRow(first.rows(), "OtherID", "FreeType"), false).widget())
                instanceof EnumerationComboBox);

        // Row 2: two OtherIDs → a table inside the opened cell, @ListedType a column
        GridBlock second = fx(() -> {
            rootTable().getRows().get(1).toggleColumnExpanded("Identifiers");
            view.rebuild();
            return rootTable().getRows().get(1).getCellGrid("Identifiers");
        });
        assertEquals(List.of("AIF CODE", "CUSIP", "SEDOL"), fx(() -> {
            RepeatingElementsTable others = second.rows().stream().filter(FlatRow::hasRepeatingTable).findFirst()
                    .orElseThrow().getRepeatingTable();
            return dropdownValues(adapter.cellEditSpec(others, 1, attributeColumn(others, "ListedType")));
        }));
    }
}
