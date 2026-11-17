package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javafx.scene.Scene;
import javafx.scene.control.Spinner;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XsdSchemaAdapter;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.widgets.NumericSpinner;
import org.fxt.freexmltoolkit.service.XsdDocumentationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Typed values in the grid: FundsXML's {@code TotalPercentage} ({@code PercentageType} = restriction of
 * xs:decimal) and {@code Amount} (simple content extending a decimal type) are edited with a number spinner that
 * keeps fractions; a value the schema does not allow is written and a warning names the reason — in rows, table
 * cells and pasted blocks.
 */
@ExtendWith(ApplicationExtension.class)
class XmlGridTypedValueEditTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="Fund">
                <xs:complexType><xs:sequence>
                  <xs:element name="TotalPercentage" type="PercentageType"/>
                  <xs:element name="Amount" type="AmountType"/>
                  <xs:element name="Position" maxOccurs="unbounded">
                    <xs:complexType><xs:sequence>
                      <xs:element name="Weight" type="PercentageType"/>
                    </xs:sequence>
                    <xs:attribute name="units" type="xs:unsignedByte"/></xs:complexType>
                  </xs:element>
                </xs:sequence></xs:complexType>
              </xs:element>
              <xs:simpleType name="PercentageType"><xs:restriction base="xs:decimal"/></xs:simpleType>
              <xs:complexType name="AmountType">
                <xs:simpleContent><xs:extension base="xs:decimal">
                  <xs:attribute name="ccy" type="xs:string"/>
                </xs:extension></xs:simpleContent>
              </xs:complexType>
            </xs:schema>
            """;

    private static final String XML = "<Fund><TotalPercentage>1</TotalPercentage><Amount ccy=\"EUR\">10</Amount>"
            + "<Position units=\"1\"><Weight>1.5</Weight></Position><Position units=\"2\"><Weight>2</Weight></Position>"
            + "</Fund>";

    @TempDir
    Path dir;

    private Stage stage;
    private XmlEditorContext context;
    private XmlCanvasView view;
    private XmlGridAdapter adapter;
    private final List<String> warnings = new ArrayList<>();

    @Start
    void start(Stage stage) {
        this.stage = stage;
    }

    private void load() throws Exception {
        Path xsd = dir.resolve("fund.xsd");
        Files.writeString(xsd, XSD);
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsd.toString());
        service.processXsd(Boolean.TRUE);
        XsdSchemaAdapter schema = new XsdSchemaAdapter();
        schema.setXsdDocumentationData(service.xsdDocumentationData);
        fx(() -> {
            RepeatingElementsTable.clearAllCaches();
            context = new XmlEditorContext();
            context.loadDocumentFromString(XML);
            context.setSchemaProvider(schema);
            adapter = new XmlGridAdapter(context);
            adapter.setWarningHandler(warnings::add);
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

    private FlatRow row(String label) {
        return view.visibleRowList().stream().filter(r -> label.equals(r.getLabel())).findFirst().orElseThrow();
    }

    private String text(String name) {
        XmlElement fund = context.getDocument().getRootElement();
        return fund.getChildElement(name).getTextContent();
    }

    @SuppressWarnings("unchecked")
    private static String type(GridModelAdapter.EditSpec spec, String text) {
        assertInstanceOf(NumericSpinner.class, spec.widget());
        Spinner<String> spinner = (Spinner<String>) spec.widget().getNode();
        spinner.getEditor().setText("");
        spinner.getEditor().replaceSelection(text);
        return spec.widget().getValue();
    }

    @Test
    void percentageKeepsItsFraction() throws Exception {
        load();
        String value = fx(() -> type(adapter.editSpec(row("TotalPercentage"), false), "3,5"));
        assertEquals("3.5", value);
        fx(() -> adapter.commitRowEdit(row("TotalPercentage"), false, value));
        assertEquals("3.5", fx(() -> text("TotalPercentage")));
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void simpleContentIsANumberToo() throws Exception {
        load();
        String value = fx(() -> type(adapter.editSpec(row("Amount"), false), "1234.56"));
        fx(() -> adapter.commitRowEdit(row("Amount"), false, value));
        assertEquals("1234.56", fx(() -> text("Amount")));
    }

    @Test
    void invalidValuesAreWrittenWithAWarning() throws Exception {
        load();
        fx(() -> adapter.commitRowEdit(row("TotalPercentage"), false, "abc"));
        assertEquals("abc", fx(() -> text("TotalPercentage")), "warn, but write");
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("TotalPercentage"), warnings.getFirst());
        assertTrue(warnings.getFirst().contains("abc"), "the reason names the value: " + warnings.getFirst());
    }

    @Test
    void tableCellsAndPastedBlocksAreChecked() throws Exception {
        load();
        RepeatingElementsTable table = fx(() -> view.visibleRowList().stream()
                .filter(r -> "Position".equals(r.getLabel()) && r.hasRepeatingTable()).findFirst().orElseThrow()
                .getRepeatingTable());
        fx(() -> adapter.commitCellEdit(table, 0, "Weight", "0.25"));
        assertTrue(warnings.isEmpty(), warnings.toString());
        fx(() -> adapter.commitCellEdit(table, 0, "Weight", "x"));
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("Position/Weight"), warnings.getFirst());

        warnings.clear();
        String unitsColumn = table.getColumns().stream()
                .filter(c -> c.getType() == RepeatingElementsTable.ColumnType.ATTRIBUTE).findFirst().orElseThrow()
                .getName();
        int unitsIndex = fx(() -> table.getColumns().indexOf(table.getColumn(unitsColumn)));
        fx(() -> adapter.pasteCells(table, 0, unitsIndex, List.of(List.of("300"), List.of("-1"))));
        assertEquals(1, warnings.size(), "one warning for the whole block");
        assertTrue(warnings.getFirst().contains("2"), warnings.getFirst());
    }
}
