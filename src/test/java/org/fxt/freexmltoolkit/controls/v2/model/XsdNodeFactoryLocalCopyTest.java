package org.fxt.freexmltoolkit.controls.v2.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.fxt.freexmltoolkit.service.LocalSchemaCopies;
import org.fxt.freexmltoolkit.service.NamespaceSchemaDownloader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A downloaded import is saved next to the importing schema under its original name and read
 * from there afterwards (no md5-named cache path as "resolved path"). The namespace is not in the
 * Schema Library, so the (mocked) namespace downloader serves it and no test touches the network.
 */
class XsdNodeFactoryLocalCopyTest {

    private static final String DSIG = "urn:example:signature";

    private String previous;

    @BeforeEach
    void enable() {
        previous = System.getProperty(LocalSchemaCopies.PROPERTY);
        System.setProperty(LocalSchemaCopies.PROPERTY, "true");
    }

    @AfterEach
    void restore() {
        if (previous == null) {
            System.clearProperty(LocalSchemaCopies.PROPERTY);
        } else {
            System.setProperty(LocalSchemaCopies.PROPERTY, previous);
        }
    }

    private static final String DSIG_XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:example:signature">
              <xs:element name="Signature" type="xs:string"/>
            </xs:schema>
            """;

    private static final String MAIN_XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:funds">
              <xs:import namespace="urn:example:signature" schemaLocation="xmldsig-core-schema.xsd"/>
            </xs:schema>
            """;

    @Test
    void downloadedImportIsSavedNextToTheSchemaAndReadFromThere(@TempDir Path tmp) throws Exception {
        Path project = Files.createDirectory(tmp.resolve("project"));
        Path cacheFile = Files.writeString(Files.createDirectory(tmp.resolve("cache")).resolve("3d3790a9.xsd"), DSIG_XSD);
        Path main = Files.writeString(project.resolve("FundsXML4.xsd"), MAIN_XSD);

        NamespaceSchemaDownloader downloader = mock(NamespaceSchemaDownloader.class);
        when(downloader.resolve(DSIG, "xmldsig-core-schema.xsd")).thenReturn(Optional.of(
                new NamespaceSchemaDownloader.ResolvedNamespaceSchema(DSIG_XSD, cacheFile,
                        "https://www.w3.org/TR/xmldsig-core/xmldsig-core-schema.xsd")));
        XsdNodeFactory factory = new XsdNodeFactory();
        factory.setRemoteNamespaceFallbackEnabled(true);
        factory.setNamespaceSchemaDownloader(downloader);
        XsdSchema schema = factory.fromFile(main);

        Path copy = project.resolve("xmldsig-core-schema.xsd");
        assertTrue(Files.isRegularFile(copy), "the import is saved under its original name");
        XsdImport anImport = (XsdImport) schema.getChildren().stream()
                .filter(XsdImport.class::isInstance).findFirst().orElseThrow();
        assertEquals(copy, anImport.getResolvedPath());
        assertTrue(schema.getImportedSchemas().containsKey(DSIG));

        // Next time the copy is read: no lookup, even with the cache file gone.
        Files.delete(cacheFile);
        NamespaceSchemaDownloader offline = mock(NamespaceSchemaDownloader.class);
        XsdNodeFactory second = new XsdNodeFactory();
        second.setRemoteNamespaceFallbackEnabled(true);
        second.setNamespaceSchemaDownloader(offline);
        XsdSchema again = second.fromFile(main);
        verify(offline, never()).resolve(any(), any());
        assertTrue(again.getImportedSchemas().containsKey(DSIG));
    }

    @Test
    void switchedOffKeepsTheCachePath(@TempDir Path tmp) throws Exception {
        System.setProperty(LocalSchemaCopies.PROPERTY, "false");
        Path project = Files.createDirectory(tmp.resolve("project"));
        Path cacheFile = Files.writeString(Files.createDirectory(tmp.resolve("cache")).resolve("abc.xsd"), DSIG_XSD);
        Path main = Files.writeString(project.resolve("FundsXML4.xsd"), MAIN_XSD);
        NamespaceSchemaDownloader downloader = mock(NamespaceSchemaDownloader.class);
        when(downloader.resolve(DSIG, "xmldsig-core-schema.xsd")).thenReturn(Optional.of(
                new NamespaceSchemaDownloader.ResolvedNamespaceSchema(DSIG_XSD, cacheFile, "cache:" + cacheFile)));
        XsdNodeFactory factory = new XsdNodeFactory();
        factory.setRemoteNamespaceFallbackEnabled(true);
        factory.setNamespaceSchemaDownloader(downloader);
        XsdSchema schema = factory.fromFile(main);

        assertFalse(Files.exists(project.resolve("xmldsig-core-schema.xsd")));
        XsdImport anImport = (XsdImport) schema.getChildren().stream()
                .filter(XsdImport.class::isInstance).findFirst().orElseThrow();
        assertEquals(cacheFile, anImport.getResolvedPath());
    }
}
