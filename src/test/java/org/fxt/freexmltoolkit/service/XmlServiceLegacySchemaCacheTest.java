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

    @Test void symlinksAreNotFollowed(@TempDir Path root, @TempDir Path external) throws Exception {
        // Create a file outside the cache root
        Files.writeString(external.resolve("target-file.txt"), "content");

        // Create an MD5-named symlink inside cache root pointing to the external dir
        Path symlink = root.resolve("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");
        try {
            Files.createSymbolicLink(symlink, external);
        } catch (UnsupportedOperationException e) {
            // Platform doesn't support symlinks; skip test
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Platform does not support symlinks");
        }

        // Verify symlink is not listed as a cache directory
        assertTrue(XmlServiceImpl.listAutoDetectedSchemaCacheDirs(root).isEmpty(),
                   "Symlinks should not be listed as MD5 directories");

        // Verify clearing cache doesn't delete the external file
        assertEquals(0, XmlServiceImpl.clearAutoDetectedSchemaCache(root),
                     "Should not delete files when no real MD5 dirs exist");
        assertTrue(Files.exists(external.resolve("target-file.txt")),
                   "External file should not be deleted");
    }
}
