package org.fxt.freexmltoolkit.controls.shell.editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link SpreadsheetActionRunner#exportToCsv} (no UI): XML is written to a
 * non-empty CSV file containing the values.
 */
class SpreadsheetActionRunnerTest {

    @Test
    void exportsXmlToCsv(@TempDir Path tmp) throws Exception {
        Path csv = tmp.resolve("out.csv");
        String result = SpreadsheetActionRunner.exportToCsv(
                "<root><a>alpha</a><b>beta</b></root>", csv.toFile());

        assertTrue(result.startsWith("OK:"), result);
        assertTrue(Files.exists(csv), "CSV must be created");
        String content = Files.readString(csv);
        assertTrue(content.contains("alpha") && content.contains("beta"), content);
    }

    @Test
    void invalidXmlReturnsError(@TempDir Path tmp) {
        assertTrue(SpreadsheetActionRunner.exportToCsv("<not-closed>", tmp.resolve("x.csv").toFile())
                .startsWith("ERROR:"));
    }
}
