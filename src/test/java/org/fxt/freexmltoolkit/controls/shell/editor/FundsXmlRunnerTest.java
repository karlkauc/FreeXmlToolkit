package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class FundsXmlRunnerTest {

    @Test
    void installedVersionsNeverNull() {
        List<String> versions = FundsXmlRunner.installedVersions();
        assertNotNull(versions, "versions list");
    }

    @Test
    void validateWithNoActiveSchemaReportsClearly() {
        // With no active schema / blank xml, validate returns a non-null human-readable summary
        // rather than throwing.
        String summary = FundsXmlRunner.validateSummary("<root/>");
        assertNotNull(summary);
        assertTrue(summary.length() > 0);
    }

    @Test
    void folderPathsResolve() {
        assertNotNull(FundsXmlRunner.examplesDir());
        assertNotNull(FundsXmlRunner.schemaDir());
        assertNotNull(FundsXmlRunner.schematronDir());
    }
}
