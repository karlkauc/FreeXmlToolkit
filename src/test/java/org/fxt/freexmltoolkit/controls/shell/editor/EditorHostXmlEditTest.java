package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Phase 2: an XML-instance node selected in the Grid is editable from the inspector path —
 * EditorHost.setActiveXmlAttribute / setActiveXmlElementText commit via the XML command
 * stack and round-trip to the editor text.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostXmlEditTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        stage.setScene(new Scene(host, 900, 600));
        stage.show();
    }

    @Test
    void editsXmlAttributeAndTextViaInspectorPath(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("data.xml");
        Files.writeString(xml, "<root>\n  <item x=\"1\">hello</item>\n</root>\n");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("item")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.GRAPHIC);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        // Select the <item> element via the grid's context (as a row click would).
        XmlGridView grid = (XmlGridView) host.lookupAll("*").stream()
                .filter(n -> n instanceof XmlGridView).findFirst().orElseThrow();
        XmlElement item = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            XmlElement root = grid.getContext().getDocument().getRootElement();
            XmlElement it = root.getChildElements("item").get(0);
            grid.getContext().getSelectionModel().setSelectedNode(it);
            return it;
        });
        assertNotNull(item);
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> host.activeXmlNodeProperty().get() == item);

        boolean attrOk = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.setActiveXmlAttribute("x", "2"));
        assertTrue(attrOk);
        assertTrue(host.getActiveText().orElse("").contains("x=\"2\""),
                "editing the attribute via the inspector path must round-trip to the text");

        boolean textOk = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.setActiveXmlElementText("world"));
        assertTrue(textOk);
        assertTrue(host.getActiveText().orElse("").contains("world"),
                "editing the element text via the inspector path must round-trip to the text");
    }

    @Test
    void xsltFamilyFileIsEditableViaInspectorPath(@TempDir Path tmp) throws Exception {
        // XSLT (and Schematron) are XML-family: the Grid + inspector editing path applies.
        Path xsl = tmp.resolve("sheet.xsl");
        Files.writeString(xsl, "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"1.0\">\n"
                + "  <xsl:template match=\"a\"/>\n</xsl:stylesheet>\n");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsl));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("template")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.GRAPHIC);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        XmlGridView grid = (XmlGridView) host.lookupAll("*").stream()
                .filter(n -> n instanceof XmlGridView).findFirst().orElseThrow();
        XmlElement template = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            XmlElement t = grid.getContext().getDocument().getRootElement().getChildElements().get(0);
            grid.getContext().getSelectionModel().setSelectedNode(t);
            return t;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> host.activeXmlNodeProperty().get() == template);

        boolean ok = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.setActiveXmlAttribute("match", "b"));
        assertTrue(ok);
        assertTrue(host.getActiveText().orElse("").contains("match=\"b\""),
                "editing an XSLT element's attribute via the inspector path must round-trip");
    }
}
