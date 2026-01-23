package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class DragDropServiceTest {
    @Test
    void testExtensions() {
        assertFalse(DragDropService.XML_EXTENSIONS.isEmpty());
    }
}
