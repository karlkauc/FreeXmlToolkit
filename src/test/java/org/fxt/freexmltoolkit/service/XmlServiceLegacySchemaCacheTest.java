package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class XmlServiceLegacySchemaCacheTest {
    @Test void listsAndClearsMd5Dirs(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("schemas"));
        Files.writeString(root.resolve("schemas").resolve("keep.xsd"), "x");
        Path md5 = root.resolve("0123456789ABCDEF0123456789ABCDEF");
        Files.createDirectories(md5);
        Files.writeString(md5.resolve("a.xsd"), "x");
        assertEquals(java.util.List.of(md5), XmlServiceImpl.listAutoDetectedSchemaCacheDirs(root));
        assertEquals(1, XmlServiceImpl.clearAutoDetectedSchemaCache(root));
        assertFalse(Files.exists(md5));
        assertTrue(Files.exists(root.resolve("schemas").resolve("keep.xsd")));
    }
}
