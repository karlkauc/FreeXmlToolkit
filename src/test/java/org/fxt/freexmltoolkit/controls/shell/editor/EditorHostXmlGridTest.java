package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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

        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeSupportsView(ViewMode.GRAPHIC)),
                "XML must offer a Grid view");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.GRAPHIC);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        boolean hasGrid = WaitForAsyncUtils.waitForAsyncFx(2000, () ->
                host.lookupAll("*").stream().anyMatch(n -> n instanceof XmlCanvasView));
        assertTrue(hasGrid, "Grid mode must embed the Canvas-based XmlCanvasView grid");
    }

    @Test
    void gridHeaderOffersExpandAllCollapseAllAndZoom(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("order.xml");
        Files.writeString(xml, XML);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("item")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.GRAPHIC);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        XmlCanvasView canvas = WaitForAsyncUtils.waitForAsyncFx(2000, () -> (XmlCanvasView) host.lookupAll("*")
                .stream().filter(n -> n instanceof XmlCanvasView).findFirst().orElseThrow());
        try {
            // Collapse all → only the root and its attribute row stay; Expand all → the item group returns.
            fire("#grid-collapse-all");
            assertEquals(2, canvas.visibleRowCount(), "order + @id");
            fire("#grid-expand-all");
            assertEquals(3, canvas.visibleRowCount(), "order + @id + item group");

            // Zoom pill: reset, then two steps in → 120 %.
            WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
                ((Label) host.lookup("#grid-zoom-label")).getOnMouseClicked()
                        .handle(null);
                return null;
            });
            fire("#grid-zoom-in");
            fire("#grid-zoom-in");
            assertEquals(1.2, canvas.getZoom(), 0.001);
            assertEquals("120%", WaitForAsyncUtils.waitForAsyncFx(2000,
                    () -> ((Label) host.lookup("#grid-zoom-label")).getText()));
            fire("#grid-zoom-out");
            assertEquals(1.1, canvas.getZoom(), 0.001);
        } finally {
            WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
                canvas.zoomReset();
                return null;
            });
        }
    }

    private void fire(String buttonId) {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            ((Button) host.lookup(buttonId)).fire();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void xsdGraphicIsTheSchemaDiagramNotTheInstanceGrid(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("schema")).orElse(false));

        // Graphic is one switch position for all: XSD gets the schema diagram there…
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeSupportsView(ViewMode.GRAPHIC)),
                "XSD must offer the Graphic view");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.GRAPHIC);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        // …and never the XML instance grid.
        boolean hasGrid = WaitForAsyncUtils.waitForAsyncFx(2000, () ->
                host.lookupAll("*").stream().anyMatch(n -> n instanceof XmlCanvasView));
        assertFalse(hasGrid, "XSD's Graphic view is the schema diagram, not the XML instance grid");
    }
}
