package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Turns an XQuery result sequence into a tabular model for the shell's XQuery console: a sequence of
 * elements becomes rows with columns derived from their child elements (or attributes); a sequence of
 * atomic values becomes a single "value" column.
 */
class XQueryTableRunnerTest {

    private static final String XML = """
            <order>
              <item><sku>A</sku><qty>2</qty></item>
              <item><sku>B</sku><qty>5</qty></item>
            </order>
            """;

    @Test
    void elementSequenceBecomesColumnsFromChildElements() {
        XQueryTableRunner.XQueryTable t = XQueryTableRunner.run(XML, "for $i in /order/item return $i");
        assertFalse(t.isError(), t.error());
        assertEquals(List.of("sku", "qty"), t.columns());
        assertEquals(2, t.rows().size());
        assertEquals(List.of("A", "2"), t.rows().get(0));
        assertEquals(List.of("B", "5"), t.rows().get(1));
    }

    @Test
    void atomicSequenceBecomesSingleValueColumn() {
        XQueryTableRunner.XQueryTable t =
                XQueryTableRunner.run(XML, "for $i in /order/item return string($i/sku)");
        assertFalse(t.isError(), t.error());
        assertEquals(List.of("value"), t.columns());
        assertEquals(List.of(List.of("A"), List.of("B")), t.rows());
    }

    @Test
    void attributeSequenceBecomesColumnsFromAttributes() {
        String xml = "<r><row id=\"1\" name=\"x\"/><row id=\"2\" name=\"y\"/></r>";
        XQueryTableRunner.XQueryTable t = XQueryTableRunner.run(xml, "/r/row");
        assertFalse(t.isError(), t.error());
        assertEquals(List.of("id", "name"), t.columns());
        assertEquals(List.of("1", "x"), t.rows().get(0));
        assertEquals(List.of("2", "y"), t.rows().get(1));
    }

    @Test
    void invalidXQueryYieldsError() {
        XQueryTableRunner.XQueryTable t = XQueryTableRunner.run(XML, "for $i in (((");
        assertTrue(t.isError(), "a malformed query must surface an error");
        assertTrue(t.rows().isEmpty());
    }
}
