package org.fxt.freexmltoolkit.controls.v2.view.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;

import javafx.geometry.VPos;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.util.XMLResourceDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** The SVG drawing surface: elements, attributes, transforms, clips and fonts (FX for font metrics). */
@ExtendWith(ApplicationExtension.class)
class SvgDiagramGraphicsTest {

    private static Element first(SvgDiagramGraphics svg, String tag) {
        NodeList list = svg.getDocument().getElementsByTagNameNS(SvgDiagramGraphics.SVG_NS, tag);
        return list.getLength() == 0 ? null : (Element) list.item(0);
    }

    private static int count(SvgDiagramGraphics svg, String tag) {
        return svg.getDocument().getElementsByTagNameNS(SvgDiagramGraphics.SVG_NS, tag).getLength();
    }

    @Test
    void rootCarriesSizeViewBoxAndCanvasDefaults() {
        SvgDiagramGraphics svg = new SvgDiagramGraphics(200.5, 100);
        Element root = svg.getDocument().getDocumentElement();
        assertEquals("svg", root.getLocalName());
        assertEquals("200.5", root.getAttribute("width"));
        assertEquals("0 0 200.5 100", root.getAttribute("viewBox"));
        assertEquals("square", root.getAttribute("stroke-linecap"));
        assertEquals("10", root.getAttribute("stroke-miterlimit"));
    }

    @Test
    void filledAndStrokedShapes() {
        SvgDiagramGraphics svg = new SvgDiagramGraphics(100, 100);
        svg.setFill(Color.web("#112233"));
        svg.fillRoundRect(1, 2, 30, 20, 8, 8);
        svg.setStroke(Color.rgb(255, 0, 0, 0.5));
        svg.setLineWidth(2);
        svg.setLineDashes(4, 3);
        svg.setLineCap(StrokeLineCap.BUTT);
        svg.strokeLine(0, 0, 10, 10);
        svg.setLineDashes((double[]) null);
        svg.strokePolyline(new double[]{0, 5, 5}, new double[]{0, 0, 5}, 3);
        svg.fillOval(10, 10, 20, 10);

        Element rect = first(svg, "rect");
        assertEquals("#112233", rect.getAttribute("fill"));
        assertEquals("4", rect.getAttribute("rx"));
        Element line = first(svg, "line");
        assertEquals("#ff0000", line.getAttribute("stroke"));
        assertEquals("0.5", line.getAttribute("stroke-opacity"));
        assertEquals("2", line.getAttribute("stroke-width"));
        assertEquals("4 3", line.getAttribute("stroke-dasharray"));
        assertEquals("butt", line.getAttribute("stroke-linecap"));
        Element polyline = first(svg, "polyline");
        assertEquals("0,0 5,0 5,5", polyline.getAttribute("points"));
        assertEquals("", polyline.getAttribute("stroke-dasharray"));
        Element ellipse = first(svg, "ellipse");
        assertEquals("20", ellipse.getAttribute("cx"));
        assertEquals("5", ellipse.getAttribute("ry"));
    }

    @Test
    void globalAlphaMultipliesTheColourOpacity() {
        SvgDiagramGraphics svg = new SvgDiagramGraphics(10, 10);
        svg.setGlobalAlpha(0.5);
        svg.setFill(Color.rgb(0, 0, 0, 0.5));
        svg.fillRect(0, 0, 5, 5);
        assertEquals("0.25", first(svg, "rect").getAttribute("fill-opacity"));
    }

    @Test
    void transformsApplyToShapesAndRestoreUndoesThem() {
        SvgDiagramGraphics svg = new SvgDiagramGraphics(100, 100);
        svg.save();
        svg.translate(10, 20);
        svg.scale(2, 2);
        svg.fillRect(0, 0, 1, 1);
        svg.restore();
        svg.fillRect(0, 0, 1, 1);
        NodeList rects = svg.getDocument().getElementsByTagNameNS(SvgDiagramGraphics.SVG_NS, "rect");
        assertEquals("matrix(2 0 0 2 10 20)", ((Element) rects.item(0)).getAttribute("transform"));
        assertEquals("", ((Element) rects.item(1)).getAttribute("transform"));
    }

    @Test
    void pathsAreWrittenInDeviceSpaceWithScaledStroke() {
        SvgDiagramGraphics svg = new SvgDiagramGraphics(100, 100);
        svg.scale(2, 2);
        svg.setLineWidth(1.5);
        svg.beginPath();
        svg.moveTo(1, 1);
        svg.lineTo(5, 1);
        svg.bezierCurveTo(6, 1, 7, 2, 7, 3);
        svg.closePath();
        svg.stroke();
        Element path = first(svg, "path");
        assertEquals("M 2 2 L 10 2 C 12 2 14 4 14 6Z", path.getAttribute("d"));
        assertEquals("3", path.getAttribute("stroke-width"));
        assertEquals("none", path.getAttribute("fill"));
    }

    @Test
    void clipOpensANestedGroupThatRestoreLeaves() {
        SvgDiagramGraphics svg = new SvgDiagramGraphics(100, 100);
        svg.save();
        svg.beginPath();
        svg.rect(0, 0, 10, 10);
        svg.clip();
        svg.fillRect(0, 0, 50, 50);
        svg.restore();
        svg.fillRect(60, 60, 5, 5);

        assertEquals(1, count(svg, "clipPath"));
        Element group = first(svg, "g");
        assertEquals("url(#clip1)", group.getAttribute("clip-path"));
        NodeList rects = svg.getDocument().getElementsByTagNameNS(SvgDiagramGraphics.SVG_NS, "rect");
        assertEquals("g", rects.item(0).getParentNode().getLocalName(), "drawn inside the clip");
        assertEquals("svg", rects.item(1).getParentNode().getLocalName(), "drawn after restore, outside");
    }

    @Test
    void textFollowsAlignmentBaselineAndFont() {
        SvgDiagramGraphics svg = new SvgDiagramGraphics(100, 100);
        Font font = Font.font("Inter", 12);
        svg.setFont(font);
        svg.setTextAlign(TextAlignment.CENTER);
        svg.setTextBaseline(VPos.TOP);
        svg.fillText("a < b & c", 50, 10);

        Element text = first(svg, "text");
        assertEquals("a < b & c", text.getTextContent());
        assertEquals("middle", text.getAttribute("text-anchor"));
        assertEquals("12", text.getAttribute("font-size"));
        assertTrue(text.getAttribute("font-family").startsWith("'"), text.getAttribute("font-family"));
        assertTrue(Double.parseDouble(text.getAttribute("y")) > 10, "TOP moves the baseline down by the ascent");
        assertFalse(svg.getUsedFontFaces().isEmpty());
    }

    @Test
    void cutFamiliesMapToFamilyAndWeight() {
        SvgDiagramGraphics.FontFace face = SvgDiagramGraphics.faceOf(Font.font("Inter SemiBold", 12));
        // The cut family only resolves where the font is installed or loaded; either way the
        // descriptor of the requested family decides
        if (face.family().equals("Inter")) {
            assertEquals(600, face.weight());
        }
        assertEquals("/css/fonts/Inter-SemiBold.ttf",
                SvgDiagramGraphics.bundledFontResource(new SvgDiagramGraphics.FontFace("Inter", 600, false)));
        assertEquals("/css/fonts/JetBrainsMono-Bold.ttf",
                SvgDiagramGraphics.bundledFontResource(new SvgDiagramGraphics.FontFace("JetBrains Mono", 700, false)));
        assertNull(SvgDiagramGraphics.bundledFontResource(new SvgDiagramGraphics.FontFace("Arial", 400, false)));
        assertTrue(SvgDiagramGraphics.cssFamilyList("Monospaced").endsWith("monospace"));
    }

    @Test
    void numbersHaveAtMostThreeDecimalsAndNoExponent() {
        assertEquals("1", SvgDiagramGraphics.num(1.0));
        assertEquals("0.333", SvgDiagramGraphics.num(1.0 / 3));
        assertEquals("0", SvgDiagramGraphics.num(1e-9));
        assertEquals("12345678", SvgDiagramGraphics.num(12345678));
        assertEquals("0", SvgDiagramGraphics.num(Double.NaN));
    }

    @Test
    void documentIsValidSvgForBatikWithTitleAndEmbeddedFonts() throws Exception {
        SvgDiagramGraphics svg = new SvgDiagramGraphics(120, 40);
        svg.setTitle("Order & Items");
        svg.setFont(Font.font("Inter", 12));
        svg.fillText("Hello", 5, 20);
        svg.beginPath();
        svg.rect(0, 0, 60, 40);
        svg.clip();
        svg.strokeRect(1, 1, 50, 30);
        svg.embedBundledFonts();

        String xml = svg.toXml();
        assertTrue(xml.contains("<title>Order &amp; Items</title>"), xml);
        assertTrue(xml.contains("xmlns=\"http://www.w3.org/2000/svg\""), xml);
        SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(XMLResourceDescriptor.getXMLParserClassName());
        var parsed = factory.createSVGDocument("file:///test.svg", new StringReader(xml));
        assertNotNull(parsed.getRootElement());
    }
}
