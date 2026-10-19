package org.fxt.freexmltoolkit.controls.v2.editor.services;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link MutableXmlSchemaProvider#clearSchema()} runs on the FX thread: it must not wait for a load that
 * is still parsing on a worker, and that load must not publish its schema over the clear afterwards.
 */
class MutableXmlSchemaProviderClearTest {

    /** A schema large enough that parsing it takes a noticeable moment. */
    private static String largeSchema(int elements) {
        StringBuilder xsd = new StringBuilder("""
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="root">
                    <xs:complexType>
                      <xs:sequence>
                """);
        for (int i = 0; i < elements; i++) {
            xsd.append("        <xs:element name=\"e").append(i).append("\" type=\"T").append(i % 50)
                    .append("\" minOccurs=\"0\"/>\n");
        }
        xsd.append("      </xs:sequence>\n    </xs:complexType>\n  </xs:element>\n");
        for (int t = 0; t < 50; t++) {
            xsd.append("  <xs:simpleType name=\"T").append(t).append("\"><xs:restriction base=\"xs:string\">")
                    .append("<xs:pattern value=\"[A-Z]{2}[0-9]{").append(t % 9 + 1).append("}\"/>")
                    .append("</xs:restriction></xs:simpleType>\n");
        }
        return xsd.append("</xs:schema>\n").toString();
    }

    @Test
    void clearDoesNotWaitForARunningLoadAndWins(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("large.xsd");
        Files.writeString(xsd, largeSchema(3000));
        MutableXmlSchemaProvider provider = new MutableXmlSchemaProvider();

        AtomicBoolean loaded = new AtomicBoolean();
        Thread loader = new Thread(() -> loaded.set(provider.loadSchema(xsd.toFile())), "test-loader");
        loader.start();
        Thread.sleep(30);

        boolean loadStillRunning = loader.isAlive();
        long start = System.nanoTime();
        provider.clearSchema();
        long clearMs = (System.nanoTime() - start) / 1_000_000;
        loader.join(60_000);

        if (loadStillRunning) {
            assertTrue(clearMs < 500, "clearSchema waited " + clearMs + " ms for the running load");
            assertFalse(loaded.get(), "a load overtaken by a clear reports that it did not bind");
        }
        assertFalse(provider.hasSchema(), "the clear must win over a load that started before it");
        assertNull(provider.getXsdDocumentationDataFor(xsd.toFile()));
    }

    @Test
    void loadAfterAClearPublishesAgain(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("small.xsd");
        Files.writeString(xsd, largeSchema(3));
        MutableXmlSchemaProvider provider = new MutableXmlSchemaProvider();

        provider.clearSchema();
        assertTrue(provider.loadSchema(xsd.toFile()));
        assertTrue(provider.hasSchema());
        assertNotNull(provider.getXsdDocumentationDataFor(xsd.toFile()));
        assertNull(provider.getXsdDocumentationDataFor(tmp.resolve("other.xsd").toFile()),
                "the model is only handed out for the schema that is actually loaded");
    }
}
