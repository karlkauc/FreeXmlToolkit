/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2025.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.controls.v2.view;

import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for XsdGraphViewEventHandler utility class.
 * Tests mouse event detection and classification.
 */
class XsdGraphViewEventHandlerTest {

    private XsdGraphViewEventHandler handler = new XsdGraphViewEventHandler();

    private MouseEvent createMouseEvent(MouseButton button, int clickCount, boolean controlDown,
                                       boolean shiftDown, boolean altDown, boolean metaDown) {
        MouseEvent event = mock(MouseEvent.class);
        when(event.getButton()).thenReturn(button);
        when(event.getClickCount()).thenReturn(clickCount);
        when(event.isControlDown()).thenReturn(controlDown);
        when(event.isShiftDown()).thenReturn(shiftDown);
        when(event.isAltDown()).thenReturn(altDown);
        when(event.isMetaDown()).thenReturn(metaDown);
        return event;
    }

    @Test
    void isLeftClick_singleLeftClick() {
        MouseEvent event = createMouseEvent(MouseButton.PRIMARY, 1, false, false, false, false);
        assertTrue(handler.isLeftClick(event));
    }

    @Test
    void isLeftClick_doubleClick() {
        MouseEvent event = createMouseEvent(MouseButton.PRIMARY, 2, false, false, false, false);
        assertFalse(handler.isLeftClick(event));
    }

    @Test
    void isLeftClick_rightClick() {
        MouseEvent event = createMouseEvent(MouseButton.SECONDARY, 1, false, false, false, false);
        assertFalse(handler.isLeftClick(event));
    }

    @Test
    void isDoubleClick_doubleLeftClick() {
        MouseEvent event = createMouseEvent(MouseButton.PRIMARY, 2, false, false, false, false);
        assertTrue(handler.isDoubleClick(event));
    }

    @Test
    void isDoubleClick_tripleClick() {
        MouseEvent event = createMouseEvent(MouseButton.PRIMARY, 3, false, false, false, false);
        assertTrue(handler.isDoubleClick(event));
    }

    @Test
    void isDoubleClick_singleClick() {
        MouseEvent event = createMouseEvent(MouseButton.PRIMARY, 1, false, false, false, false);
        assertFalse(handler.isDoubleClick(event));
    }

    @Test
    void isRightClick_secondaryButton() {
        MouseEvent event = createMouseEvent(MouseButton.SECONDARY, 1, false, false, false, false);
        assertTrue(handler.isRightClick(event));
    }

    @Test
    void isRightClick_primaryButton() {
        MouseEvent event = createMouseEvent(MouseButton.PRIMARY, 1, false, false, false, false);
        assertFalse(handler.isRightClick(event));
    }

    @Test
    void hasControlModifier_controlDown() {
        MouseEvent event = createMouseEvent(MouseButton.PRIMARY, 1, true, false, false, false);
        assertTrue(handler.hasControlModifier(event));
    }

    @Test
    void hasControlModifier_metaDown() {
        MouseEvent event = createMouseEvent(MouseButton.PRIMARY, 1, false, false, false, true);
        assertTrue(handler.hasControlModifier(event));
    }

    @Test
    void hasControlModifier_noModifier() {
        MouseEvent event = createMouseEvent(MouseButton.PRIMARY, 1, false, false, false, false);
        assertFalse(handler.hasControlModifier(event));
    }

    @Test
    void hasShiftModifier_shiftDown() {
        MouseEvent event = createMouseEvent(MouseButton.PRIMARY, 1, false, true, false, false);
        assertTrue(handler.hasShiftModifier(event));
    }

    @Test
    void hasShiftModifier_noModifier() {
        MouseEvent event = createMouseEvent(MouseButton.PRIMARY, 1, false, false, false, false);
        assertFalse(handler.hasShiftModifier(event));
    }

    @Test
    void hasAltModifier_altDown() {
        MouseEvent event = createMouseEvent(MouseButton.PRIMARY, 1, false, false, true, false);
        assertTrue(handler.hasAltModifier(event));
    }

    @Test
    void hasAltModifier_noModifier() {
        MouseEvent event = createMouseEvent(MouseButton.PRIMARY, 1, false, false, false, false);
        assertFalse(handler.hasAltModifier(event));
    }

    @Test
    void calculateDragDistance_noMovement() {
        double distance = handler.calculateDragDistance(10, 20, 10, 20);
        assertEquals(0, distance);
    }

    @Test
    void calculateDragDistance_horizontalMovement() {
        double distance = handler.calculateDragDistance(0, 0, 3, 0);
        assertEquals(3, distance);
    }

    @Test
    void calculateDragDistance_verticalMovement() {
        double distance = handler.calculateDragDistance(0, 0, 0, 4);
        assertEquals(4, distance);
    }

    @Test
    void calculateDragDistance_diagonalMovement() {
        double distance = handler.calculateDragDistance(0, 0, 3, 4);
        assertEquals(5, distance); // 3-4-5 triangle
    }

    @Test
    void isDragThresholdExceeded_belowThreshold() {
        boolean exceeded = handler.isDragThresholdExceeded(0, 0, 3, 0);
        assertFalse(exceeded); // 3 pixels < 5 pixel threshold
    }

    @Test
    void isDragThresholdExceeded_atThreshold() {
        boolean exceeded = handler.isDragThresholdExceeded(0, 0, 5, 0);
        assertFalse(exceeded); // exactly at threshold
    }

    @Test
    void isDragThresholdExceeded_aboveThreshold() {
        boolean exceeded = handler.isDragThresholdExceeded(0, 0, 10, 0);
        assertTrue(exceeded); // 10 pixels > 5 pixel threshold
    }

    @Test
    void getButtonName_primary() {
        MouseEvent event = createMouseEvent(MouseButton.PRIMARY, 1, false, false, false, false);
        assertEquals("LEFT", handler.getButtonName(event));
    }

    @Test
    void getButtonName_secondary() {
        MouseEvent event = createMouseEvent(MouseButton.SECONDARY, 1, false, false, false, false);
        assertEquals("RIGHT", handler.getButtonName(event));
    }

    @Test
    void getButtonName_middle() {
        MouseEvent event = createMouseEvent(MouseButton.MIDDLE, 1, false, false, false, false);
        assertEquals("MIDDLE", handler.getButtonName(event));
    }

    @Test
    void shouldToggleExpansion_singleLeftClick() {
        MouseEvent event = createMouseEvent(MouseButton.PRIMARY, 1, false, false, false, false);
        assertTrue(handler.shouldToggleExpansion(event));
    }

    @Test
    void shouldToggleExpansion_withControl() {
        MouseEvent event = createMouseEvent(MouseButton.PRIMARY, 1, true, false, false, false);
        assertFalse(handler.shouldToggleExpansion(event));
    }

    @Test
    void shouldToggleExpansion_withShift() {
        MouseEvent event = createMouseEvent(MouseButton.PRIMARY, 1, false, true, false, false);
        assertFalse(handler.shouldToggleExpansion(event));
    }

    @Test
    void shouldMultiSelect_leftClickWithControl() {
        MouseEvent event = createMouseEvent(MouseButton.PRIMARY, 1, true, false, false, false);
        assertTrue(handler.shouldMultiSelect(event));
    }

    @Test
    void shouldMultiSelect_leftClickNoControl() {
        MouseEvent event = createMouseEvent(MouseButton.PRIMARY, 1, false, false, false, false);
        assertFalse(handler.shouldMultiSelect(event));
    }

    @Test
    void shouldRangeSelect_leftClickWithShift() {
        MouseEvent event = createMouseEvent(MouseButton.PRIMARY, 1, false, true, false, false);
        assertTrue(handler.shouldRangeSelect(event));
    }

    @Test
    void shouldRangeSelect_leftClickNoShift() {
        MouseEvent event = createMouseEvent(MouseButton.PRIMARY, 1, false, false, false, false);
        assertFalse(handler.shouldRangeSelect(event));
    }
}
