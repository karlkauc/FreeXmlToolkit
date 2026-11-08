package org.fxt.freexmltoolkit.service.largefile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;

import org.fxt.freexmltoolkit.service.largefile.LargeFileValidation.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests {@link LargeFileValidation}: XSD, limit, well-formedness, schema errors, declared schema. */
@DisplayName("LargeFileValidation")
class LargeFileValidationTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="orders"><xs:complexType><xs:sequence>
                <xs:element name="order" maxOccurs="unbounded"><xs:complexType>
                  <xs:attribute name="id" type="xs:int" use="required"/>
                </xs:complexType></xs:element>
              </xs:sequence></xs:complexType></xs:element>
            </xs:schema>
            """;

    @TempDir
    Path dir;

    private Path orders(String name, int count, int badEvery) throws Exception {
        Path file = dir.resolve(name);
        try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            out.write("<orders xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"orders.xsd\">\n");
            for (int i = 1; i <= count; i++) {
                out.write("  <order id=\"" + (badEvery > 0 && i % badEvery == 0 ? "x" + i : i) + "\"/>\n");
            }
            out.write("</orders>\n");
        }
        return file;
    }

    @Test
    @DisplayName("valid, invalid with lines, the error limit")
    void validation() throws Exception {
        Path xsd = Files.writeString(dir.resolve("orders.xsd"), XSD);
        Result valid = LargeFileValidation.validate(orders("ok.xml", 50_000, 0), xsd.toFile(), 100, null, null);
        assertTrue(valid.valid(), valid.toString());

        Result invalid = LargeFileValidation.validate(orders("bad.xml", 10_000, 1000), xsd.toFile(), 100, null, null);
        assertFalse(invalid.valid());
        assertFalse(invalid.truncated());
        assertTrue(invalid.problems().size() >= 10, invalid.problems().toString());
        assertEquals(1001, invalid.problems().get(0).line(), "order 1000 is on line 1001");
        assertTrue(invalid.problems().get(0).message().contains("x1000"), invalid.problems().get(0).message());

        Result limited = LargeFileValidation.validate(orders("bad2.xml", 10_000, 10), xsd.toFile(), 25, null, null);
        assertEquals(25, limited.problems().size());
        assertTrue(limited.truncated());
    }

    @Test
    @DisplayName("well-formedness without a schema; broken document; broken schema; cancellation")
    void errors() throws Exception {
        assertTrue(LargeFileValidation.validate(orders("ok.xml", 100, 0), null, 10, null, null).valid());
        Path broken = Files.writeString(dir.resolve("broken.xml"), "<a>\n<b>\n</a>");
        Result fatal = LargeFileValidation.validate(broken, null, 10, null, null);
        assertTrue(fatal.fatal());
        assertEquals(3, fatal.problems().get(0).line());

        Path badSchema = Files.writeString(dir.resolve("bad.xsd"), "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"><xs:bogus/></xs:schema>");
        Result schemaError = LargeFileValidation.validate(orders("s.xml", 10, 0), badSchema.toFile(), 10, null, null);
        assertNotNull(schemaError.schemaError());
        assertTrue(schemaError.schemaError().startsWith("Schema is invalid or unreadable"), schemaError.schemaError());

        Path big = orders("big.xml", 200_000, 0);
        long[] seen = {0};
        assertThrows(CancellationException.class,
                () -> LargeFileValidation.validate(big, null, 10, read -> seen[0] = read, () -> seen[0] > 0));
    }

    @Test
    @DisplayName("the declared schema is found from the start of the file")
    void declaredSchema() throws Exception {
        Files.writeString(dir.resolve("orders.xsd"), XSD);
        Path file = orders("d.xml", 10, 0);
        assertEquals(dir.resolve("orders.xsd").toFile().getCanonicalFile(),
                LargeFileValidation.declaredSchema(file).orElseThrow().getCanonicalFile());
        assertEquals(41, LargeFileValidation.rootStartTagEnd("<?xml version=\"1.0\"?><!-- c --><r a=\"x>y\">"));
    }
}
