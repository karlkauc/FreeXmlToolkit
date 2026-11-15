package org.fxt.freexmltoolkit.service.spreadsheet;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService;
import org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService.ConversionConfig;
import org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService.RowData;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

/** The table layout: which element is the record, the columns, and the rows back. */
class RecordTableLayoutTest {

    static final String ORDERS = """
            <Orders version="2">
              <Header><Date>2026-11-01</Date></Header>
              <Order id="1"><Date>2026-11-01</Date><Customer><Name>Müller</Name></Customer>
                <Item><Qty>3</Qty></Item><Item><Qty>4</Qty></Item></Order>
              <Order id="2"><Date>2026-11-02</Date><Customer><Name>Huber</Name></Customer>
                <Item><Qty>1</Qty></Item></Order>
              <Order id="3"><Customer><Name>Gruber</Name></Customer><Note>call first</Note></Order>
              <Trailer count="3"/>
            </Orders>
            """;

    static List<RowData> rows(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        var doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        return new XmlSpreadsheetConverterService().extractRowsFromXml(doc, new ConversionConfig());
    }

    @Test
    void theRepeatingElementThatShowsMostIsTheDefaultRecord() throws Exception {
        var candidates = RecordTableLayout.candidates(rows(ORDERS));
        assertEquals("/Orders/Order", candidates.getFirst().path(), "Order holds the Items too");
        assertEquals(3, candidates.getFirst().count());
        assertEquals("Order", candidates.getFirst().name());
        assertTrue(candidates.stream().anyMatch(c -> c.path().equals("/Orders/Order/Item")));
        assertEquals("/Orders/Order", RecordTableLayout.defaultRecordPath(rows(ORDERS)));
    }

    @Test
    void columnsAreTheValuesInsideARecordInTheirOrder() throws Exception {
        var table = RecordTableLayout.toTable(rows(ORDERS), "/Orders/Order");
        assertEquals(List.of("@id", "Date", "Customer/Name", "Item[1]/Qty", "Item[2]/Qty", "Note"), table.headers());
        assertEquals(3, table.records().size());
        assertFalse(table.hasParentColumn());
        var second = table.records().get(1).cells();
        assertEquals("Huber", second.get("Customer/Name").getValue());
        assertEquals("/Orders/Order[2]/Customer/Name", second.get("Customer/Name").getXpath());
        assertNull(second.get("Item[2]/Qty"));
        // Header and trailer stay outside, with a placeholder where the records were
        var other = table.otherRows();
        assertTrue(other.stream().anyMatch(r -> r.getXpath().equals("/Orders/Header/Date")));
        assertTrue(other.stream().anyMatch(r -> r.getXpath().equals("/Orders/Trailer/@count")));
        int placeholder = indexOf(other, RecordTableLayout.RECORDS_TYPE);
        assertTrue(placeholder > indexOfXPath(other, "/Orders/Header/Date"));
        assertTrue(placeholder < indexOfXPath(other, "/Orders/Trailer/@count"));
        assertTrue(table.hasOtherData());
    }

    @Test
    void recordsUnderSeveralParentsGetAParentColumn() throws Exception {
        var table = RecordTableLayout.toTable(rows("""
                <Shop><Customer><Order n="1"/><Order n="2"/></Customer><Customer><Order n="3"/></Customer></Shop>"""),
                "/Shop/Customer/Order");
        assertTrue(table.hasParentColumn());
        assertEquals(List.of("Parent", "@n"), table.headerRow());
        assertEquals("/Shop/Customer[2]", table.records().get(2).parent());
        assertEquals(2, table.otherRows().stream().filter(r -> RecordTableLayout.RECORDS_TYPE.equals(r.getNodeType())).count());
    }

    @Test
    void aRecordWithOnlyTextHasItsOwnValueColumn() throws Exception {
        var table = RecordTableLayout.toTable(rows("<Tags><Tag>a</Tag><Tag>b</Tag></Tags>"), "/Tags/Tag");
        assertEquals(List.of(RecordTableLayout.OWN_VALUE), table.headers());
        assertEquals("b", table.records().get(1).cells().get(RecordTableLayout.OWN_VALUE).getValue());
    }

    @Test
    void foreignHeadersBecomeValidXmlNames() {
        assertEquals(List.of("Order_date", "_2024", "Name", "Name[2]", "@id", "Address/City"),
                RecordTableLayout.columnPaths(List.of("Order date", "2024", "Name", "Name", "@id", "Address/City"), -1));
        assertEquals("_xml_data", RecordTableLayout.xmlName("xml data"));
        assertEquals("ns:Item", RecordTableLayout.xmlName("ns:Item"));
        assertNull(RecordTableLayout.columnPaths(List.of("Parent", " "), 0).get(0));
        assertNull(RecordTableLayout.columnPaths(List.of("Parent", " "), 0).get(1));
    }

    @Test
    void aParentColumnOnlyWhenItsValuesArePaths() {
        assertEquals(0, RecordTableLayout.parentColumn(List.of("Parent", "x"), List.of(List.of("/a/b[2]", "1"))));
        assertEquals(-1, RecordTableLayout.parentColumn(List.of("Parent", "x"), List.of(List.of("Anna", "1"))),
                "a foreign column named Parent is data");
    }

    @Test
    void assemblePutsTheRecordsWhereTheirPlaceholderWas() {
        List<RowData> other = List.of(new RowData("/R/Head", "h", "element", 0),
                new RowData("/R", "", RecordTableLayout.RECORDS_TYPE, 1), new RowData("/R/Tail", "t", "element", 2));
        var record = List.of(new RowData("/R/Rec[1]", "", "element"), new RowData("/R/Rec[1]/@a", "1", "attribute"));
        var added = List.of(new RowData("/X/Rec[1]", "", "element"));
        var rows = RecordTableLayout.assemble(other, new java.util.LinkedHashMap<>(Map.of("/R", List.of(record),
                "/X", List.of(added))));
        assertEquals(List.of("/R/Head", "/R/Rec[1]", "/R/Rec[1]/@a", "/R/Tail", "/X/Rec[1]"),
                rows.stream().map(RowData::getXpath).toList());
        assertEquals(List.of(0, 1, 2, 3, 4), rows.stream().map(RowData::getOriginalIndex).toList());
    }

    private static int indexOf(List<RowData> rows, String type) {
        for (int i = 0; i < rows.size(); i++) {
            if (type.equals(rows.get(i).getNodeType())) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfXPath(List<RowData> rows, String xpath) {
        for (int i = 0; i < rows.size(); i++) {
            if (xpath.equals(rows.get(i).getXpath())) {
                return i;
            }
        }
        return -1;
    }
}
