package org.fxt.freexmltoolkit.controls.v2.editor.flatten;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;

import org.fxt.freexmltoolkit.controls.shell.editor.SchemaActionRunner;
import org.junit.jupiter.api.Test;

/**
 * Real-scale smoke test: flatten FundsXML4 with all reduction options and prove
 * the result is still a compilable schema (the server-side validation use case)
 * and meaningfully smaller than the unreduced flatten.
 */
class SchemaFlattenFundsXmlTest {

    private static final Path FUNDS_XML =
            Path.of("src/test/resources/schema/include_files/FundsXML4.xsd");

    @Test
    void minimalFlattenOfFundsXmlStillCompiles() throws Exception {
        assumeTrue(Files.exists(FUNDS_XML), "FundsXML4.xsd fixture not available");
        String content = Files.readString(FUNDS_XML);
        Path baseDir = FUNDS_XML.toAbsolutePath().getParent();

        String plain = SchemaActionRunner.flatten(content, baseDir);
        assertFalse(plain.startsWith("ERROR:"), plain);

        String minimal = SchemaActionRunner.flatten(content, baseDir,
                new FlattenOptions(true, true, true, true, true));
        assertFalse(minimal.startsWith("ERROR:"), minimal);
        assertFalse(minimal.contains("<xs:annotation"), "annotations should be stripped");
        assertFalse(minimal.contains("<!--"), "comments should be stripped");
        assertTrue(minimal.length() < plain.length(),
                "minimal output (" + minimal.length() + ") should be smaller than plain flatten ("
                        + plain.length() + ")");

        // The point of the feature: the reduced schema must still compile for validation.
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(new StreamSource(new StringReader(minimal)));
    }
}
