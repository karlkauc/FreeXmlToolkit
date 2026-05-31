package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.XmlCanvasView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies the XML instance Grid view: XML-family documents offer a Grid mode
 * backed by the Canvas-based {@link XmlCanvasView} (XMLSpy grid style), while XSD
 * and JSON do not.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostXmlGridTest {

    private static final String XML = """
            <order id="A1">
              <item sku="X1" qty="2"/>
              <item sku="X2" qty="5"/>
            </order>
            """;

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root" type="xs:string"/>
            </xs:schema>
            """;

    private EditorHost host;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        stage.setScene(new Scene(host, 900, 600));
        stage.show();
    }

    @Test
    void xmlOffersAGridViewBackedByTheCanvasGrid(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("order.xml");
        Files.writeString(xml, XML);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("item")).orElse(false));

        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeSupportsView(ViewMode.GRID)),
                "XML must offer a Grid view");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.GRID);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        boolean hasGrid = WaitForAsyncUtils.waitForAsyncFx(2000, () ->
                host.lookupAll("*").stream().anyMatch(n -> n instanceof XmlCanvasView));
        assertTrue(hasGrid, "Grid mode must embed the Canvas-based XmlCanvasView grid");
    }

    @Test
    void xsdHasNoGridView(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("schema")).orElse(false));

        assertFalse(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeSupportsView(ViewMode.GRID)),
                "XSD has the Graphic (Canvas) view, not the XML instance Grid");
    }
}
