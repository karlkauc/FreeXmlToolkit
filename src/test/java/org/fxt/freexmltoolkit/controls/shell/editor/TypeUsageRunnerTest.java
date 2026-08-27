package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests {@link TypeUsageRunner} (no UI): finds where a named type is referenced
 * in an XSD, reusing {@code TypeUsageFinder}.
 */
class TypeUsageRunnerTest {

    private String purchaseOrderXsd() throws Exception {
        return Files.readString(java.nio.file.Path.of("src/test/resources/purchageOrder.xsd"));
    }

    @Test
    void findsUsagesOfAReferencedType() throws Exception {
        List<String> usages = TypeUsageRunner.findUsages(purchaseOrderXsd(), "Address");
        assertFalse(usages.isEmpty(), "Address is used by ShipTo/BillTo");
    }

    @Test
    void reportsNoUsagesForAnUnusedTypeName() throws Exception {
        assertTrue(TypeUsageRunner.findUsages(purchaseOrderXsd(), "NoSuchTypeXyz").isEmpty());
    }

    @Test
    void invalidXsdYieldsEmptyList() {
        assertTrue(TypeUsageRunner.findUsages("<not-a-schema/>", "X").isEmpty());
    }

    private static final String MAIN_WITH_INCLUDE = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:include schemaLocation="lib.xsd"/>
            </xs:schema>
            """;

    private static final String INCLUDED_LIB = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:complexType name="AddressType">
                <xs:sequence>
                  <xs:element name="city" type="xs:string"/>
                </xs:sequence>
              </xs:complexType>
              <xs:element name="ShipTo" type="AddressType"/>
            </xs:schema>
            """;

    /**
     * Regression test for issue #36 (analogous root cause for {@code xs:include}): a relative
     * {@code schemaLocation} can only be resolved against the document's base directory.
     * {@link org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory#fromString(String)} (no base
     * directory, the old {@code TypeUsageRunner.findUsages(String, String)} behaviour) silently
     * skips the include, so a type usage that only exists in the included file is invisible. The
     * new {@code (String, Path, String)} overload resolves it and finds the usage.
     */
    @Test
    void findsUsagesFromIncludedSchemaOnlyWhenBaseDirectoryIsProvided(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("main.xsd"), MAIN_WITH_INCLUDE);
        Files.writeString(tmp.resolve("lib.xsd"), INCLUDED_LIB);

        List<String> withoutBaseDir = TypeUsageRunner.findUsages(MAIN_WITH_INCLUDE, "AddressType");
        List<String> withBaseDir = TypeUsageRunner.findUsages(MAIN_WITH_INCLUDE, tmp, "AddressType");

        assertTrue(withoutBaseDir.isEmpty(),
                "without a base directory the relative include cannot be resolved: " + withoutBaseDir);
        assertFalse(withBaseDir.isEmpty(),
                "with the document's directory as base directory the include resolves"
                        + " and ShipTo's usage of AddressType is found");
    }
}
