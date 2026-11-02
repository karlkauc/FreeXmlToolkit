package org.fxt.freexmltoolkit.controls.v2.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The cardinality badge and the expand button share the right end of a card. */
class XsdNodeRendererBadgeTest {

    private static final double X = 100;
    private static final double WIDTH = 220;

    @ParameterizedTest
    @ValueSource(doubles = {24, 40, 90})
    void theBadgeEndsLeftOfTheExpandButton(double badgeWidth) {
        double badgeEnd = XsdNodeRenderer.badgeX(X, WIDTH, badgeWidth, true) + badgeWidth;

        assertTrue(badgeEnd < XsdNodeRenderer.expandButtonX(X, WIDTH));
    }

    @ParameterizedTest
    @ValueSource(doubles = {24, 40, 90})
    void aCardWithoutButtonKeepsTheBadgeAtItsEnd(double badgeWidth) {
        assertEquals(X + WIDTH - 10 - badgeWidth, XsdNodeRenderer.badgeX(X, WIDTH, badgeWidth, false));
    }
}
