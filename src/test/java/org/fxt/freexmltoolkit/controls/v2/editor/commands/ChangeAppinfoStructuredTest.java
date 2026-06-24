package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAppInfo;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the structured {@link XsdAppInfo} overload of {@link ChangeAppinfoCommand}, which
 * preserves complex (raw-XML) appinfo entries that the display-string form cannot round-trip.
 */
class ChangeAppinfoStructuredTest {

    private XsdEditorContext context;
    private XsdElement element;

    @BeforeEach
    void setUp() {
        XsdSchema schema = new XsdSchema();
        context = new XsdEditorContext(schema);
        element = new XsdElement("e");
        schema.addChild(element);
    }

    @Test
    void execute_setsStructuredAppinfoAndUndoRestores() {
        XsdAppInfo appInfo = new XsdAppInfo();
        appInfo.setSince("4.0.0");
        appInfo.setDeprecated("Use {@link /New} instead");

        ChangeAppinfoCommand cmd = new ChangeAppinfoCommand(context, element, appInfo);
        assertTrue(cmd.execute());
        assertEquals("4.0.0", element.getAppinfo().getSince());
        assertTrue(element.getAppinfo().isDeprecated());

        assertTrue(cmd.undo());
        assertNull(element.getAppinfo());
    }

    @Test
    void execute_preservesRawXmlEntry() {
        XsdAppInfo appInfo = new XsdAppInfo();
        appInfo.addEntry("altova:exampleValues", "", "<altova:exampleValues><altova:example value=\"7\"/></altova:exampleValues>");
        appInfo.setSince("4.2");

        ChangeAppinfoCommand cmd = new ChangeAppinfoCommand(context, element, appInfo);
        assertTrue(cmd.execute());

        boolean hasRaw = element.getAppinfo().getEntries().stream().anyMatch(XsdAppInfo.AppInfoEntry::hasRawXml);
        assertTrue(hasRaw, "Raw-XML appinfo entry must be preserved end-to-end");
        assertEquals("4.2", element.getAppinfo().getSince());
    }

    @Test
    void emptyAppinfo_clearsToNull() {
        element.setAppinfo(new XsdAppInfo());
        XsdAppInfo empty = new XsdAppInfo();
        ChangeAppinfoCommand cmd = new ChangeAppinfoCommand(context, element, empty);
        assertTrue(cmd.execute());
        assertNull(element.getAppinfo(), "An empty structured appinfo removes the appinfo");
    }
}
