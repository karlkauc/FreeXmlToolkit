package org.fxt.freexmltoolkit.controls.v2.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.NodeWrapperType;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/** The card layout of the diagram (Figma 07.6b): size, symbol, pill, type line and outline. */
@ExtendWith(ApplicationExtension.class)
class XsdNodeRendererCardLayoutTest {

    private final XsdNodeRenderer renderer = new XsdNodeRenderer();

    private static VisualNode element(String name, String type, int min, int max) {
        return new VisualNode(name, type, NodeWrapperType.ELEMENT, null, null, min, max);
    }

    @Test
    void aPlainCardHasTheFixedTwoLineHeight() {
        assertEquals(XsdNodeRenderer.CARD_HEIGHT, renderer.calculateNodeHeight(element("a", "xs:string", 1, 1)));
    }

    @Test
    void aShortCardKeepsTheMinimumWidth() {
        assertEquals(160, renderer.calculateNodeWidth(element("a", "xs:string", 1, 1)));
    }

    @Test
    void theWidthMakesRoomForPillAndExpandButton() {
        VisualNode plain = element("UniqueDocumentIdentifierWithALongName", "xs:string", 1, 1);
        VisualNode withPill = element("UniqueDocumentIdentifierWithALongName", "xs:string", 0, -1);
        VisualNode withButton = element("UniqueDocumentIdentifierWithALongName", "xs:string", 1, 1);
        withButton.addChild(element("child", "xs:string", 1, 1));

        assertTrue(renderer.calculateNodeWidth(withPill) > renderer.calculateNodeWidth(plain));
        assertTrue(renderer.calculateNodeWidth(withButton) > renderer.calculateNodeWidth(plain));
    }

    @Test
    void thePillIsHiddenForTheDefaultCardinality() {
        assertEquals("", element("a", "t", 1, 1).getCardinalityBadge());
        assertEquals("0..1", element("a", "t", 0, 1).getCardinalityBadge());
        assertEquals("1..*", element("a", "t", 1, -1).getCardinalityBadge());
        assertEquals("0..*", element("a", "t", 0, Integer.MAX_VALUE).getCardinalityBadge());
        assertEquals("2..5", element("a", "t", 2, 5).getCardinalityBadge());
    }

    @Test
    void attributesShowTheirUse() {
        XsdAttribute required = new XsdAttribute("id");
        required.setUse("required");
        XsdAttribute optional = new XsdAttribute("lang");

        VisualNode req = new VisualNode("id", "", NodeWrapperType.ATTRIBUTE, required, null);
        VisualNode opt = new VisualNode("lang", "", NodeWrapperType.ATTRIBUTE, optional, null);

        assertEquals("req", req.getCardinalityBadge());
        assertEquals("opt", opt.getCardinalityBadge());
        assertFalse(XsdNodeRenderer.isOptional(req));
        assertTrue(XsdNodeRenderer.isOptional(opt));
    }

    @Test
    void theSymbolFollowsTheStructure() {
        VisualNode simple = element("a", "xs:string", 1, 1);
        VisualNode complex = element("b", "BType", 1, 1);
        complex.addChild(element("c", "xs:string", 1, 1));

        assertEquals("bi-code-slash", XsdNodeRenderer.cardIcon(simple));
        assertEquals("bi-diagram-3", XsdNodeRenderer.cardIcon(complex));
        assertEquals("bi-at", XsdNodeRenderer.cardIcon(
                new VisualNode("x", "", NodeWrapperType.ATTRIBUTE, null, null)));
        assertEquals("bi-list-nested", XsdNodeRenderer.cardIcon(
                new VisualNode("s", "", NodeWrapperType.SEQUENCE, null, null)));
        for (NodeWrapperType type : NodeWrapperType.values()) {
            String icon = XsdNodeRenderer.cardIcon(new VisualNode("n", "", type, null, null));
            assertTrue(BootstrapIconPaths.hasPath(icon) || "bi-at".equals(icon), type + " → " + icon);
        }
    }

    @Test
    void theTypeLineFallsBackToTheKind() {
        assertEquals("xs:date", XsdNodeRenderer.typeLine(element("a", "xs:date", 1, 1)));
        assertEquals("element", XsdNodeRenderer.typeLine(element("a", "", 1, 1)));
        assertEquals("complexType", XsdNodeRenderer.typeLine(
                new VisualNode("T", null, NodeWrapperType.COMPLEX_TYPE, null, null)));
    }

    @Test
    void theOutlineCarriesOptionalAndRepeating() {
        assertFalse(XsdNodeRenderer.isOptional(element("a", "t", 1, 1)));
        assertTrue(XsdNodeRenderer.isOptional(element("a", "t", 0, 1)));
        assertFalse(XsdNodeRenderer.isRepeating(element("a", "t", 1, 1)));
        assertTrue(XsdNodeRenderer.isRepeating(element("a", "t", 1, -1)));
        assertTrue(XsdNodeRenderer.isRepeating(element("a", "t", 0, 3)));
    }

    @Test
    void theNameUsesTheBundledSemiBoldCut() {
        javafx.scene.text.Font.loadFont(getClass().getResourceAsStream("/css/fonts/Inter-SemiBold.ttf"), 10);
        boolean registered = javafx.scene.text.Font.getFamilies().contains("Inter SemiBold");

        javafx.scene.text.Font font = XsdNodeRenderer.weighted("Inter", "SemiBold",
                javafx.scene.text.FontWeight.BOLD, 12.5);

        // The headless test text engine (t2k) may not list the cut; then the bold fallback applies
        assertEquals(registered ? "Inter SemiBold" : javafx.scene.text.Font.font("Inter",
                javafx.scene.text.FontWeight.BOLD, 12.5).getFamily(), font.getFamily());
        assertEquals(12.5, font.getSize());
    }

    @Test
    void connectorsMeetTheMiddleOfTheTextLines() {
        VisualNode card = element("a", "t", 1, 1);
        card.setY(100);
        card.setHeight(120);   // with constraint rows

        assertEquals(100 + XsdNodeRenderer.CARD_HEIGHT / 2, XsdNodeRenderer.anchorY(card));
    }
}
