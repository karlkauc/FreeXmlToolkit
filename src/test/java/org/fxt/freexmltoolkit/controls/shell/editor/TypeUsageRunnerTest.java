package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Test;

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
}
