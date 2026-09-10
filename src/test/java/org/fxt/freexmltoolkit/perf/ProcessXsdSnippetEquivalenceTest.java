package org.fxt.freexmltoolkit.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.fxt.freexmltoolkit.service.XsdDocumentationService;
import org.junit.jupiter.api.Test;

/**
 * Correctness gate for the memory optimizations of {@code processXsd}: the per-entry source code snippets and the
 * documentation (raw and Markdown-rendered) of every XPath entry on FundsXML_428.xsd must stay byte-identical when
 * snippets, documentation texts and the Markdown renderer are shared between the expanded copies of a schema node.
 * Complements {@link ProcessXsdEquivalenceTest}, which deliberately leaves these fields out.
 */
class ProcessXsdSnippetEquivalenceTest {

    private static final Path BIG_XSD = Path.of("src/test/resources/FundsXML_428.xsd");
    /** ASCII SOH (U+0001) - a field separator that can't occur in XSD/XPath text. */
    private static final char SEP = '';

    // Captured from the code before the memory optimizations. DO NOT edit after capture.
    private static final String EXPECTED_SNIPPETS_SHA256 = "33e4d44a37fd78c0e179821c50391342d6c69d2371d8c15695fcf79e8e8fd03b";
    private static final String EXPECTED_RENDERED_DOCS_SHA256 = "3231801c114bd42313e67d1a2e145cfe53e27f9ac743720477c4fa1b61d61053";

    @Test
    void sourceSnippetsPerXpathAreUnchanged() throws Exception {
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(BIG_XSD.toAbsolutePath().toString());
        service.processXsd(Boolean.FALSE);

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, XsdExtendedElement> e : new TreeMap<>(service.xsdDocumentationData.getExtendedXsdElementMap()).entrySet()) {
            XsdExtendedElement x = e.getValue();
            sb.append(e.getKey()).append(SEP)
              .append(x.getSourceCode()).append(SEP)
              .append(x.getReferencedTypeCode()).append(SEP)
              .append(x.getReferencedTypeName()).append(SEP)
              .append(x.getDocumentations()).append('');
        }
        String actual = sha256(sb.toString());
        System.out.println("PROCESSXSD_SNIPPETS_SHA256=" + actual);
        assertEquals(EXPECTED_SNIPPETS_SHA256, actual, "source snippets or documentation of an XPath entry changed");
    }

    @Test
    void renderedDocumentationPerXpathIsUnchanged() throws Exception {
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(BIG_XSD.toAbsolutePath().toString());
        service.processXsd(Boolean.TRUE);

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, XsdExtendedElement> e : new TreeMap<>(service.xsdDocumentationData.getExtendedXsdElementMap()).entrySet()) {
            sb.append(e.getKey()).append(SEP)
              .append(new TreeMap<>(e.getValue().getLanguageDocumentation())).append('');
        }
        String actual = sha256(sb.toString());
        System.out.println("PROCESSXSD_RENDERED_DOCS_SHA256=" + actual);
        assertEquals(EXPECTED_RENDERED_DOCS_SHA256, actual, "Markdown-rendered documentation of an XPath entry changed");
    }

    private static String sha256(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }
}
