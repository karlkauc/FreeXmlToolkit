package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class CsvHandlerTest {
    @Test
    void testConfig() {
        CsvHandler.CsvConfig config = new CsvHandler.CsvConfig();
        assertNotNull(config);
    }
}
