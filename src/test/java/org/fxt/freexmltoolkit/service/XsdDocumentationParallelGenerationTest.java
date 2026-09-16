package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("XSD Documentation - Parallel Generation Thread Safety")
class XsdDocumentationParallelGenerationTest {

    @Test
    @DisplayName("Parallel documentation generation on FundsXML4 succeeds without DOM concurrency exceptions")
    void testParallelGenerationThreadSafety(@TempDir Path tmp) {
        File xsdFile = new File("src/test/resources/FundsXML_428.xsd");
        if (!xsdFile.exists()) {
            xsdFile = new File("src/test/resources/FundsXML4.xsd");
        }
        if (!xsdFile.exists()) {
            return;
        }

        final File targetXsd = xsdFile;
        assertDoesNotThrow(() -> {
            XsdDocumentationService service = new XsdDocumentationService();
            service.setXsdFilePath(targetXsd.getAbsolutePath());
            service.setMethod(XsdDocumentationService.ImageOutputMethod.SVG);
            service.setIncludedLanguages(Set.of("de", "en"));
            service.generateXsdDocumentation(tmp.toFile());
        });

        assertTrue(Files.exists(tmp.resolve("complexTypes.html")), "complexTypes.html must be generated");
        assertTrue(Files.exists(tmp.resolve("simpleTypes.html")), "simpleTypes.html must be generated");
        assertTrue(Files.exists(tmp.resolve("dataDictionary.html")), "dataDictionary.html must be generated");
    }
}
