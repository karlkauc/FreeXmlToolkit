package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A FundsXML document without a usable schema reference is bound to the active FundsXML schema. */
class FundsXmlSchemaFallbackTest {

    private static final String FUNDS_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <FundsXML4 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
              <ControlData/>
            </FundsXML4>
            """;

    @TempDir
    Path tmp;

    private Path activeSchema() throws Exception {
        Path xsd = tmp.resolve("schema").resolve("4.2.10").resolve("FundsXML4.xsd");
        Files.createDirectories(xsd.getParent());
        Files.writeString(xsd, "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>");
        return xsd;
    }

    @Test
    void fundsXmlDocumentGetsTheActiveSchema() throws Exception {
        Path xsd = activeSchema();
        var hit = EditorSchemaBinding.fundsXmlSchema(FUNDS_XML, true, () -> xsd);

        assertNotNull(hit);
        assertEquals(xsd.toFile(), hit.file());
        assertEquals(EditorHost.SchemaSource.FUNDSXML, hit.source());
        assertEquals("4.2.10", hit.detail());
    }

    @Test
    void nothingWhenSwitchedOffNoFundsXmlOrNoSchema() throws Exception {
        Path xsd = activeSchema();
        assertNull(EditorSchemaBinding.fundsXmlSchema(FUNDS_XML, false, () -> xsd), "extension switched off");
        assertNull(EditorSchemaBinding.fundsXmlSchema("<Document/>", true, () -> xsd), "not a FundsXML document");
        assertNull(EditorSchemaBinding.fundsXmlSchema(FUNDS_XML, true, () -> null), "no version downloaded");
        assertNull(EditorSchemaBinding.fundsXmlSchema(FUNDS_XML, true, () -> tmp.resolve("missing.xsd")),
                "schema file gone");
    }
}
