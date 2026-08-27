package org.fxt.freexmltoolkit.service;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression: processing a schema with a remote {@code xs:import} must never rewrite the
 * user's schema file or drop downloaded files into its directory (it used to re-serialize the
 * schema with rewritten schemaLocations whenever an XML document was opened).
 */
class XsdDocumentationServiceNoRewriteTest {

    @AfterEach
    void tearDown() {
        ServiceRegistry.reset();
    }

    @Test
    void remoteImportResolvedThroughCatalogLeavesSourceFilesUntouched(@TempDir Path tmp) throws Exception {
        Path example = Path.of("release/examples/catalog").toAbsolutePath();
        Path work = tmp.resolve("catalog");
        copyTree(example, work);
        Path invoice = work.resolve("schemas/invoice.xsd");
        byte[] before = Files.readAllBytes(invoice);
        Set<String> filesBefore = listFiles(work);

        var library = new SchemaLibraryServiceImpl(tmp.resolve("lib.json"), new SchemaResourceCache(tmp.resolve("cache")),
                () -> new ByteArrayInputStream("{\"version\":1,\"entries\":[]}".getBytes()));
        library.addCatalog(work.resolve("catalog.xml"));
        ServiceRegistry.register(SchemaLibraryService.class, library);

        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(invoice.toString());
        service.processXsd(Boolean.TRUE);

        assertArrayEquals(before, Files.readAllBytes(invoice), "the user's schema must not be rewritten");
        assertEquals(filesBefore, listFiles(work), "no files may be created in the user's directory");
        assertNotNull(service.xsdDocumentationData);
        library.awaitSave();
    }

    private static void copyTree(Path from, Path to) throws Exception {
        try (Stream<Path> s = Files.walk(from)) {
            for (Path p : s.toList()) {
                Path target = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(target); else Files.copy(p, target);
            }
        }
    }

    private static Set<String> listFiles(Path root) throws Exception {
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(Files::isRegularFile).map(p -> root.relativize(p).toString()).collect(Collectors.toSet());
        }
    }
}
