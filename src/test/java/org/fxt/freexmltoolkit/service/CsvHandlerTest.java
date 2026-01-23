package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class CsvHandlerTest {
    @Test
    void testConfig() {
        CsvHandler.CsvConfig config = new CsvHandler.CsvConfig();
        assertNotNull(config);
    }
}
