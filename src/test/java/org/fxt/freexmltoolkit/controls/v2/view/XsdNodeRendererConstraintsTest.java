package org.fxt.freexmltoolkit.controls.v2.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.IntStream;

import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.NodeWrapperType;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/** The constraint section of a card: its size, and that a card without one stays as it was. */
@ExtendWith(ApplicationExtension.class)
class XsdNodeRendererConstraintsTest {

    private final XsdNodeRenderer renderer = new XsdNodeRenderer();

    private static VisualNode card(int rows) {
        VisualNode node = new VisualNode("library", "LibraryType", NodeWrapperType.ELEMENT, null, null);
        List<ConstraintRow> list = IntStream.range(0, rows)
                .mapToObj(i -> ConstraintRow.assertion("@a" + i + " gt 0", null)).toList();
        node.setConstraintSource(() -> list);
        return node;
    }

    @Test
    void aCardWithoutConstraintsKeepsItsSize() {
        VisualNode plain = new VisualNode("library", "LibraryType", NodeWrapperType.ELEMENT, null, null);

        assertEquals(renderer.calculateNodeHeight(plain), renderer.calculateNodeHeight(card(0)));
        assertEquals(renderer.calculateNodeWidth(plain), renderer.calculateNodeWidth(card(0)));
    }

    @Test
    void everyRowAddsHeightUpToTheLimit() {
        double one = renderer.calculateNodeHeight(card(1));
        double two = renderer.calculateNodeHeight(card(2));

        assertTrue(one > renderer.calculateNodeHeight(card(0)));
        assertEquals(18, two - one);
    }

    @Test
    void rowsPastTheLimitShareOneLine() {
        double limit = renderer.calculateNodeHeight(card(XsdNodeRenderer.MAX_CONSTRAINT_ROWS));
        double over = renderer.calculateNodeHeight(card(XsdNodeRenderer.MAX_CONSTRAINT_ROWS + 1));
        double farOver = renderer.calculateNodeHeight(card(XsdNodeRenderer.MAX_CONSTRAINT_ROWS + 20));

        assertEquals(18, over - limit);
        assertEquals(over, farOver);
    }

    @Test
    void switchedOffACardIsDrawnAsIfItHadNone() {
        VisualNode node = card(3);
        renderer.setShowConstraints(false);

        assertEquals(renderer.calculateNodeHeight(card(0)), renderer.calculateNodeHeight(node));
        assertEquals(renderer.calculateNodeWidth(card(0)), renderer.calculateNodeWidth(node));
        assertNull(renderer.constraintTooltipAt(node, 1000));
    }

    @Test
    void aLongXPathDoesNotBlowUpTheCard() {
        VisualNode node = new VisualNode("x", "t", NodeWrapperType.ELEMENT, null, null);
        ConstraintRow row = ConstraintRow.assertion("every $a in b satisfies c ".repeat(100), null);
        node.setConstraintSource(() -> List.of(row));

        assertTrue(renderer.calculateNodeWidth(node) < 500, "width " + renderer.calculateNodeWidth(node));
    }

    @Test
    void theTooltipCoversOnlyTheSectionAndNamesAllRows() {
        VisualNode node = card(XsdNodeRenderer.MAX_CONSTRAINT_ROWS + 1);
        node.setY(100);

        assertNull(renderer.constraintTooltipAt(node, 110), "over the header");
        String text = renderer.constraintTooltipAt(node, 100 + renderer.calculateNodeHeight(node) - 2);
        assertNotNull(text);
        assertTrue(text.contains("@a" + XsdNodeRenderer.MAX_CONSTRAINT_ROWS + " gt 0"), text);
    }
}
