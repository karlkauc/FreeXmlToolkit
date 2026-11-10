package org.fxt.freexmltoolkit.service.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.fxt.freexmltoolkit.controls.theme.DesignTokens;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.view.XsdGraphView;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;
import org.fxt.freexmltoolkit.controls.v2.view.render.DiagramGraphics;
import org.fxt.freexmltoolkit.controls.v2.view.render.ExportableDiagram;
import org.fxt.freexmltoolkit.controls.v2.view.render.SvgDiagramGraphics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** PNG, SVG, PDF and clipboard exports of the XSD diagram and of a synthetic drawing. */
@ExtendWith(ApplicationExtension.class)
class DiagramExporterTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="Library">
                <xs:complexType><xs:sequence>
                  <xs:element name="Book" maxOccurs="unbounded">
                    <xs:complexType><xs:sequence>
                      <xs:element name="Title" type="xs:string"/>
                      <xs:element name="Author" type="xs:string" minOccurs="0"/>
                    </xs:sequence>
                    <xs:attribute name="isbn" type="xs:string"/></xs:complexType>
                  </xs:element>
                </xs:sequence></xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    @TempDir
    Path dir;

    private XsdGraphView graph;

    @Start
    void start(Stage stage) throws Exception {
        graph = new XsdGraphView(new XsdNodeFactory().fromString(XSD));
        stage.setScene(new Scene(graph, 900, 600));
        stage.show();
    }

    private void expandAll() throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            expand(graph.getRootNode());
            graph.repaint();
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static void expand(VisualNode node) {
        if (node == null) {
            return;
        }
        node.setExpanded(true);
        for (VisualNode child : node.getChildren()) {
            expand(child);
        }
    }

    private <T> T onFx(java.util.concurrent.Callable<java.util.concurrent.CompletableFuture<T>> call) throws Exception {
        java.util.concurrent.CompletableFuture<T> future = WaitForAsyncUtils.asyncFx(call).get(10, TimeUnit.SECONDS);
        return future.get(60, TimeUnit.SECONDS);
    }

    @Test
    void svgContainsTheCardsAndParsesWithBatik() throws Exception {
        expandAll();
        Path file = dir.resolve("schema.svg");
        DiagramExportOptions options = DiagramExportOptions.defaults(DiagramExportOptions.Format.SVG).withTitle("Library");
        onFx(() -> DiagramExporter.export(graph, options, file, Runnable::run, null));

        String xml = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(xml.contains(">Library<"), "card names are text");
        assertTrue(xml.contains(">Title<"));
        assertTrue(xml.contains("<title>Library</title>"));
        assertFalse(xml.contains("@font-face"), "fonts are only embedded on request");
        new org.apache.batik.anim.dom.SAXSVGDocumentFactory(
                org.apache.batik.util.XMLResourceDescriptor.getXMLParserClassName())
                .createSVGDocument(file.toUri().toString());
    }

    @Test
    void pngHasTheRegionSizeTimesTheScale() throws Exception {
        expandAll();
        Path file = dir.resolve("schema.png");
        DiagramExportOptions options = new DiagramExportOptions(DiagramExportOptions.Format.PNG,
                DiagramExportOptions.Area.WHOLE, 2, DiagramExportOptions.ThemeChoice.LIGHT,
                DiagramExportOptions.Background.WHITE, 10, false, DiagramExportOptions.PageSize.FIT, "");
        Rectangle2D region = WaitForAsyncUtils.asyncFx(() -> DiagramExporter.exportRegion(graph, options))
                .get(5, TimeUnit.SECONDS);
        onFx(() -> DiagramExporter.export(graph, options, file, Runnable::run, null));

        BufferedImage image = ImageIO.read(file.toFile());
        int[] expected = DiagramExporter.pixelSize(region, 2);
        assertEquals(expected[0], image.getWidth());
        assertEquals(expected[1], image.getHeight());
        assertEquals(0xffffffff, image.getRGB(0, 0), "white background in the padding");
        boolean drawn = false;
        for (int x = 0; x < image.getWidth() && !drawn; x += 3) {
            for (int y = 0; y < image.getHeight() && !drawn; y += 3) {
                drawn = image.getRGB(x, y) != 0xffffffff;
            }
        }
        assertTrue(drawn, "the cards are drawn");
    }

    @Test
    void pdfHasTheChosenPageSizeAndSelectableText() throws Exception {
        expandAll();
        Path file = dir.resolve("schema.pdf");
        DiagramExportOptions options = new DiagramExportOptions(DiagramExportOptions.Format.PDF,
                DiagramExportOptions.Area.WHOLE, 1, DiagramExportOptions.ThemeChoice.LIGHT,
                DiagramExportOptions.Background.THEME, 16, false, DiagramExportOptions.PageSize.A4, "");
        onFx(() -> DiagramExporter.export(graph, options, file, Runnable::run, null));

        try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
            assertEquals(1, pdf.getNumberOfPages());
            PDRectangle box = pdf.getPage(0).getMediaBox();
            double longSide = Math.max(box.getWidth(), box.getHeight()) / 72 * 25.4;
            double shortSide = Math.min(box.getWidth(), box.getHeight()) / 72 * 25.4;
            assertEquals(297, longSide, 1.5);
            assertEquals(210, shortSide, 1.5);
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("Library"), "text stays text: " + text);
        }
    }

    @Test
    void largeImagesAreAssembledFromTiles() throws Exception {
        ExportableDiagram stripes = new Stripes(DiagramExporter.TILE * 1.5, 300);
        DiagramExportOptions options = new DiagramExportOptions(DiagramExportOptions.Format.PNG,
                DiagramExportOptions.Area.WHOLE, 1, DiagramExportOptions.ThemeChoice.LIGHT,
                DiagramExportOptions.Background.TRANSPARENT, 0, false, DiagramExportOptions.PageSize.FIT, "");
        BufferedImage image = onFx(() -> DiagramExporter.renderImage(stripes, options, null));
        assertEquals(DiagramExporter.TILE * 3 / 2, image.getWidth());
        // A 1-unit-wide red line at x = TILE + 10 lies in the second tile
        assertEquals(0xffff0000, image.getRGB(DiagramExporter.TILE + 10, 150));
        assertEquals(0xffff0000, image.getRGB(10, 150), "and one in the first tile");
        assertEquals(0, image.getRGB(500, 150) >>> 24, "transparent elsewhere");
    }

    @Test
    void emptyDiagramsAndHugeImagesAreRefused() throws Exception {
        ExportableDiagram empty = new Stripes(0, 0);
        DiagramExportOptions png = DiagramExportOptions.defaults(DiagramExportOptions.Format.PNG);
        ExecutionException emptyError = org.junit.jupiter.api.Assertions.assertThrows(ExecutionException.class,
                () -> onFx(() -> DiagramExporter.renderImage(empty, png, null)));
        assertInstanceOf(DiagramExporter.DiagramExportException.class, emptyError.getCause());

        ExportableDiagram huge = new Stripes(100_000, 100_000);
        ExecutionException hugeError = org.junit.jupiter.api.Assertions.assertThrows(ExecutionException.class,
                () -> onFx(() -> DiagramExporter.renderImage(huge, png, null)));
        assertTrue(hugeError.getCause().getMessage().contains("SVG or PDF"));
    }

    @Test
    void visibleAreaIsCutToTheContent() throws Exception {
        ExportableDiagram stripes = new Stripes(1000, 500) {
            @Override
            public Rectangle2D exportVisibleBounds() {
                return new Rectangle2D(800, 100, 400, 200);
            }
        };
        DiagramExportOptions options = new DiagramExportOptions(DiagramExportOptions.Format.PNG,
                DiagramExportOptions.Area.VISIBLE, 1, DiagramExportOptions.ThemeChoice.LIGHT,
                DiagramExportOptions.Background.THEME, 5, false, DiagramExportOptions.PageSize.FIT, "");
        Rectangle2D region = DiagramExporter.exportRegion(stripes, options);
        assertEquals(new Rectangle2D(795, 95, 210, 210), region);
    }

    @Test
    void clipboardReceivesTheImage() throws Exception {
        expandAll();
        DiagramExportOptions options = DiagramExportOptions.defaults(DiagramExportOptions.Format.PNG);
        var image = onFx(() -> DiagramExporter.copyToClipboard(graph, options, null));
        assertNotNull(image);
        boolean hasImage = WaitForAsyncUtils.asyncFx(() -> javafx.scene.input.Clipboard.getSystemClipboard().hasImage())
                .get(5, TimeUnit.SECONDS);
        assertTrue(hasImage);
    }

    @Test
    void exportLeavesSelectionAndThemeOfTheViewUntouched() throws Exception {
        expandAll();
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> graph.getSelectionModel().select(graph.getRootNode()));
        DiagramExportOptions dark = new DiagramExportOptions(DiagramExportOptions.Format.SVG,
                DiagramExportOptions.Area.WHOLE, 1, DiagramExportOptions.ThemeChoice.DARK,
                DiagramExportOptions.Background.THEME, 0, false, DiagramExportOptions.PageSize.FIT, "");
        SvgDiagramGraphics svg = WaitForAsyncUtils.asyncFx(() -> DiagramExporter.renderSvg(graph, dark))
                .get(10, TimeUnit.SECONDS);
        String xml = svg.toXml();
        String darkCanvas = hex(DesignTokens.ColorToken.BG_CANVAS.color(DesignTokens.Theme.DARK));
        assertTrue(xml.contains("fill=\"" + darkCanvas + "\""), "dark page background");
        String primary = hex(DesignTokens.ColorToken.PRIMARY.color(DesignTokens.Theme.DARK));
        assertFalse(xml.contains("stroke=\"" + primary + "\" stroke-width=\"2\""), "no selection outline");
        assertTrue(graph.getSelectionModel().isSelected(graph.getRootNode()), "the selection itself stays");
    }

    private static String hex(Color color) {
        return String.format("#%02x%02x%02x", Math.round(color.getRed() * 255), Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255));
    }

    /** A synthetic drawing: red vertical lines every {@link DiagramExporter#TILE} units starting at 10. */
    private static class Stripes implements ExportableDiagram {
        private final double width;
        private final double height;

        Stripes(double width, double height) {
            this.width = width;
            this.height = height;
        }

        @Override
        public Rectangle2D exportContentBounds() {
            return width <= 0 ? Rectangle2D.EMPTY : new Rectangle2D(0, 0, width, height);
        }

        @Override
        public Rectangle2D exportVisibleBounds() {
            return exportContentBounds();
        }

        @Override
        public String exportTitle() {
            return "stripes";
        }

        @Override
        public void paintExport(DiagramGraphics g, Rectangle2D region, DesignTokens.Theme theme) {
            g.setFill(Color.RED);
            for (double x = 10; x < width; x += DiagramExporter.TILE) {
                g.fillRect(x, 0, 1, height);
            }
        }

        @Override
        public Color exportBackground(DesignTokens.Theme theme) {
            return Color.WHITE;
        }
    }
}
