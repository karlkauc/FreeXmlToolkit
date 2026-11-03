package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests the detail-page diagram: attributes listed inside element boxes, the emphasised
 * root element and centred compositor icons.
 */
class XsdDocumentationImageAttributesTest {

    @TempDir
    Path tempDir;

    private static final String XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:complexType name="AmountType">
                    <xs:simpleContent>
                        <xs:extension base="xs:decimal">
                            <xs:attribute name="ccy" type="xs:string" use="required"/>
                            <xs:attribute name="isFundCcy" type="xs:boolean"/>
                        </xs:extension>
                    </xs:simpleContent>
                </xs:complexType>
                <xs:element name="Total">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="Amount" type="AmountType" maxOccurs="unbounded"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
            </xs:schema>
            """;

    private XsdDocumentationService service;
    private XsdDocumentationImageService imageService;

    @BeforeEach
    void setUp() throws Exception {
        File xsdFile = tempDir.resolve("amount.xsd").toFile();
        Files.writeString(xsdFile.toPath(), XSD);

        service = new XsdDocumentationService();
        service.setXsdFilePath(xsdFile.getAbsolutePath());
        service.processXsd(false);

        imageService = new XsdDocumentationImageService(service.xsdDocumentationData.getExtendedXsdElementMap());
    }

    private XsdExtendedElement element(String name) {
        return service.xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                .filter(e -> name.equals(e.getElementName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Element " + name + " not found"));
    }

    @Test
    void childBoxListsItsAttributes() {
        String svg = imageService.generateSvgString(element("Total"));

        assertTrue(svg.contains(">@ccy</text>"), "@ccy should be listed inside the Amount box");
        assertTrue(svg.contains(">@isFundCcy</text>"), "@isFundCcy should be listed inside the Amount box");
        assertTrue(svg.contains(">[0..1]</text>"), "optional attribute should show [0..1]");
        assertTrue(svg.contains("attr_ccy_"), "attribute row should link to the attribute page");
        assertFalse(svg.contains("#modern-plus-icon"),
                "Amount has only attributes, so no expand icon should be drawn");
    }

    @Test
    void rootBoxListsItsAttributesInsteadOfChildNodes() {
        String svg = imageService.generateSvgString(element("Amount"));

        assertTrue(svg.contains(">@ccy</text>"), "@ccy should be listed inside the root box");
        assertFalse(svg.contains("id=\"" + element("@ccy").getCurrentXpath() + "\""),
                "attributes must not be drawn as separate node boxes");
        long nodeBoxes = Pattern.compile("<rect[^>]*\\sid=\"", Pattern.DOTALL).matcher(svg).results().count();
        assertEquals(1, nodeBoxes, "only the root box expected, no separate attribute nodes");
    }

    @Test
    void rootElementIsEmphasised() {
        String svg = imageService.generateSvgString(element("Total"));

        Matcher text = Pattern.compile("<text[^>]*>Total</text>", Pattern.DOTALL).matcher(svg);
        assertTrue(text.find(), "root name text expected");
        assertTrue(text.group().contains("font-weight=\"700\""), "root name should be bold");
        assertTrue(text.group().contains("font-size=\"16\""), "root name should be larger");
        assertTrue(svg.contains("stroke-width: 3"), "root box should have a thicker border");
    }

    @Test
    void rootNameIsCentredVerticallyInItsBox() {
        String svg = imageService.generateSvgString(element("Total"));

        Matcher rect = Pattern.compile("<rect[^>]*data-root=\"true\"[^>]*/>", Pattern.DOTALL).matcher(svg);
        assertTrue(rect.find(), "root box expected");
        double top = attr(rect.group(), "y");
        double bottom = top + attr(rect.group(), "height");
        Matcher text = Pattern.compile("<text[^>]*>Total</text>", Pattern.DOTALL).matcher(svg);
        assertTrue(text.find(), "root name text expected");
        double baseline = attr(text.group(), "y");

        // Total has no attributes, so the name is the box's only line: the space above the
        // ascent and below the descent must match.
        java.awt.font.LineMetrics metrics = imageService.font
                .deriveFont(java.awt.Font.BOLD, (float) XsdDocumentationImageService.ROOT_FONT_SIZE)
                .getLineMetrics("Total", imageService.frc);
        double above = baseline - metrics.getAscent() - top;
        double below = bottom - baseline - metrics.getDescent();
        assertEquals(above, below, 0.5, "name sits " + (above - below) + " px low");
    }

    @Test
    void sequenceIconIsCentredInItsSymbol() {
        String svg = imageService.generateSvgString(element("Total"));

        Matcher rect = Pattern.compile("<rect[^>]*fill=\"url\\(#sequenceGradient\\)\"[^>]*/>", Pattern.DOTALL).matcher(svg);
        assertTrue(rect.find(), "sequence symbol expected");
        double x = attr(rect.group(), "x");
        double y = attr(rect.group(), "y");
        double w = attr(rect.group(), "width");
        double h = attr(rect.group(), "height");

        Matcher use = Pattern.compile("<use[^>]*#sequence-icon\"[^>]*/>", Pattern.DOTALL).matcher(svg);
        assertTrue(use.find(), "sequence icon expected");
        assertEquals(x + w / 2 - 12, attr(use.group(), "x"), 0.01);
        assertEquals(y + h / 2 - 12, attr(use.group(), "y"), 0.01);
    }

    private static double attr(String tag, String name) {
        Matcher m = Pattern.compile("\\s" + name + "=\"([^\"]+)\"").matcher(tag);
        assertTrue(m.find(), name + " missing in " + tag);
        return Double.parseDouble(m.group(1));
    }
}
