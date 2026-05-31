package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link SchemaLocationResolver}, which finds a local XSD referenced by
 * an XML document's {@code xsi:noNamespaceSchemaLocation}, relative to the file.
 * Stateless (no global XmlService state), so it is safe in the multi-tab host.
 */
class SchemaLocationResolverTest {

    @Test
    void resolvesRelativeNoNamespaceSchemaLocation(@TempDir Path dir) throws Exception {
        Path xsd = dir.resolve("schema.xsd");
        Files.writeString(xsd, "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>");
        Path xml = dir.resolve("data.xml");
        String content = "<root xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + "xsi:noNamespaceSchemaLocation=\"schema.xsd\"/>";

        Optional<Path> resolved = SchemaLocationResolver.resolveLocalXsd(content, xml);

        assertTrue(resolved.isPresent());
        assertEquals(xsd.toRealPath(), resolved.get().toRealPath());
    }

    @Test
    void returnsEmptyWhenReferencedFileDoesNotExist(@TempDir Path dir) {
        Path xml = dir.resolve("data.xml");
        String content = "<root xsi:noNamespaceSchemaLocation=\"missing.xsd\"/>";
        assertTrue(SchemaLocationResolver.resolveLocalXsd(content, xml).isEmpty());
    }

    @Test
    void returnsEmptyWhenNoSchemaLocationPresent(@TempDir Path dir) {
        Path xml = dir.resolve("data.xml");
        assertTrue(SchemaLocationResolver.resolveLocalXsd("<root/>", xml).isEmpty());
    }

    @Test
    void ignoresRemoteHttpLocations(@TempDir Path dir) {
        Path xml = dir.resolve("data.xml");
        String content = "<root xsi:noNamespaceSchemaLocation=\"http://example.org/schema.xsd\"/>";
        assertTrue(SchemaLocationResolver.resolveLocalXsd(content, xml).isEmpty());
    }

    @Test
    void handlesNullInputsGracefully() {
        assertTrue(SchemaLocationResolver.resolveLocalXsd(null, null).isEmpty());
        assertTrue(SchemaLocationResolver.resolveLocalXsd("<root/>", null).isEmpty());
    }
}
