package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class DataDictionaryExcelExporterTest {
    @Test
    void testConstruction() {
        DataDictionaryExcelExporter exporter = new DataDictionaryExcelExporter(null, null);
        assertNotNull(exporter);
    }
}
