package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class DragDropServiceTest {
    @Test
    void testExtensions() {
        assertFalse(DragDropService.XML_EXTENSIONS.isEmpty());
    }
}
