package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.Test;

/** Column keys of grid records: attribute keys are one shared string per attribute name. */
class XmlGridRecordKeysTest {

    @Test
    void recordsShareTheirAttributeKeyStrings() {
        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString("<r><o id=\"1\" s=\"a\"><n>x</n></o><o id=\"2\" s=\"b\"><n>y</n></o></r>");
        List<XmlElement> orders = context.getDocument().getRootElement().getChildElements("o");
        XmlGridRecord first = new XmlGridRecord(orders.get(0));
        XmlGridRecord second = new XmlGridRecord(orders.get(1));

        assertEquals(List.of("@id", "@s", "n"), first.columnKeys());
        assertSame(first.columnKeys().get(0), second.columnKeys().get(0));
        assertSame(XmlGridRecord.attributeKey("id"), first.columnKeys().get(0));
        assertEquals("2", second.values().get("id"));
        assertSame(first.columnKeys(), second.columnKeys(), "one list for the same column order");
    }
}
