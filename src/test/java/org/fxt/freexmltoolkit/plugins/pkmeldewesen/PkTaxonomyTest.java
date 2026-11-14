package org.fxt.freexmltoolkit.plugins.pkmeldewesen;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests {@link PkTaxonomy}: the bundled taxonomy and the version store. */
class PkTaxonomyTest {

    @Test
    void theBundledTaxonomyIsInstalledOnce(@TempDir Path tmp) throws Exception {
        PkTaxonomy taxonomy = new PkTaxonomy(tmp);
        assertEquals(List.of(), taxonomy.versions());
        Path dir = taxonomy.ensureBundled();
        for (String name : PkTaxonomy.BUNDLED_FILES) {
            assertTrue(Files.isRegularFile(dir.resolve(name)), name);
        }
        assertEquals(List.of(PkTaxonomy.BUNDLED_VERSION), taxonomy.versions());
        assertEquals(PkTaxonomy.BUNDLED_VERSION, taxonomy.activeVersion());
        for (PkModule module : PkModule.values()) {
            Path schema = taxonomy.schema(PkTaxonomy.BUNDLED_VERSION, module);
            assertNotNull(schema, module.name());
            assertTrue(Files.readString(schema).contains("name=\"" + module.rootElement() + "\""), module.name());
        }
        assertTrue(taxonomy.documentation(PkTaxonomy.BUNDLED_VERSION).toString().endsWith(".pdf"));

        long size = Files.size(dir.resolve(PkTaxonomy.BUNDLED_FILES.getFirst()));
        Files.writeString(dir.resolve(PkTaxonomy.BUNDLED_FILES.getFirst()), "kept");
        taxonomy.ensureBundled();
        assertEquals("kept", Files.readString(dir.resolve(PkTaxonomy.BUNDLED_FILES.getFirst())),
                "an installed file is not overwritten");
        assertTrue(size > 4);
    }

    @Test
    void versionsAreOrderedNewestFirst(@TempDir Path tmp) throws Exception {
        PkTaxonomy taxonomy = new PkTaxonomy(tmp);
        for (String version : List.of("2.9.0", "2.10.0", "2.9.0-hotfix2", "2.9.0-hotfix10")) {
            Files.createDirectories(tmp.resolve(version));
            Files.writeString(tmp.resolve(version).resolve("pk_meldung_pfq_x.xsd"), "<xs:schema/>");
        }
        Files.createDirectories(tmp.resolve("not-a-taxonomy"));
        assertEquals(List.of("2.10.0", "2.9.0-hotfix10", "2.9.0-hotfix2", "2.9.0"), taxonomy.versions());
        assertEquals("2.10.0", taxonomy.activeVersion());
    }

    private static Path zip(Path dir, String name, String... entries) throws Exception {
        Path zip = dir.resolve(name);
        try (var out = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
            for (int i = 0; i < entries.length; i += 2) {
                out.putNextEntry(new java.util.zip.ZipEntry(entries[i]));
                out.write(entries[i + 1].getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return zip;
    }

    private static final String PFQ = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">"
            + "<xs:element name=\"Nationale_Meldung_pfq\"/></xs:schema>";

    @Test
    void aTaxonomyZipIsImportedAsAVersionFolder(@TempDir Path tmp) throws Exception {
        Path store = tmp.resolve("store");
        PkTaxonomy taxonomy = new PkTaxonomy(store);
        taxonomy.ensureBundled();
        Path zip = zip(tmp, "fma.zip",
                "Website/pk_meldung_pfq_2.10.0_Hotfix1.xsd", PFQ,
                "Website/pk_meldung_pfa_2.10.0_Hotfix1.xsd", PFQ.replace("pfq", "pfa"),
                "Website/PK-Meldewesen_Dokumentation 2.10.0.pdf", "%PDF",
                "../../evil.xlsx", "x",
                "readme.txt", "skipped",
                "__MACOSX/._pk_meldung_pfq_2.10.0_Hotfix1.xsd", "junk");
        assertEquals("2.10.0-hotfix1", taxonomy.importZip(zip));
        Path dir = store.resolve("2.10.0-hotfix1");
        assertTrue(Files.isRegularFile(dir.resolve("pk_meldung_pfq_2.10.0_Hotfix1.xsd")));
        assertTrue(Files.isRegularFile(dir.resolve("evil.xlsx")), "only the file name of an entry counts");
        assertFalse(Files.exists(tmp.resolve("evil.xlsx")));
        assertFalse(Files.exists(dir.resolve("readme.txt")));
        assertFalse(Files.exists(dir.resolve("._pk_meldung_pfq_2.10.0_Hotfix1.xsd")));
        assertEquals(List.of("2.10.0-hotfix1", PkTaxonomy.BUNDLED_VERSION), taxonomy.versions());
        assertEquals("2.10.0-hotfix1", taxonomy.activeVersion(), "the newest is used");
        try (var files = Files.list(store)) {
            assertTrue(files.noneMatch(f -> f.getFileName().toString().startsWith("_import")), "no staging folder left");
        }
        // importing again replaces the folder
        taxonomy.importZip(zip(tmp, "again.zip", "pk_meldung_pfq_2.10.0_Hotfix1.xsd", PFQ));
        assertFalse(Files.exists(dir.resolve("evil.xlsx")));
    }

    @Test
    void zipsWithoutOrWithMixedSchemasAreRefused(@TempDir Path tmp) throws Exception {
        PkTaxonomy taxonomy = new PkTaxonomy(tmp.resolve("store"));
        assertThrows(java.io.IOException.class, () -> taxonomy.importZip(zip(tmp, "none.zip", "a.xlsx", "x")));
        assertThrows(java.io.IOException.class, () -> taxonomy.importZip(zip(tmp, "mixed.zip",
                "pk_meldung_pfq_2.9.0.xsd", PFQ, "pk_meldung_pfa_2.10.0.xsd", PFQ)));
        assertEquals(List.of(), taxonomy.versions());
        assertEquals("2.9.0-hotfix2", PkTaxonomy.versionOf("pk_meldung_pfq_2.9.0_Hotfix2.xsd"));
        assertEquals(null, PkTaxonomy.versionOf("other.xsd"));
    }

    @Test
    void activeSchemasFollowTheChosenVersion(@TempDir Path tmp) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize(); // the chosen version is a setting
        Path store = tmp.resolve("store");
        PkTaxonomy taxonomy = new PkTaxonomy(store);
        taxonomy.ensureBundled();
        taxonomy.importZip(zip(tmp, "new.zip", "pk_meldung_pfq_3.0.0.xsd", PFQ));
        Path stable = taxonomy.activeSchema(PkModule.PFQ);
        assertEquals(store.resolve(PkTaxonomy.ACTIVE_DIR).resolve("pk_meldung_pfq.xsd"), stable);
        assertEquals(PFQ, Files.readString(stable));
        assertEquals(null, taxonomy.activeSchema(PkModule.PFA), "3.0.0 has no annual schema");
        try {
            taxonomy.setActiveVersion(PkTaxonomy.BUNDLED_VERSION);
            assertTrue(Files.readString(taxonomy.activeSchema(PkModule.PFQ)).length() > 10_000, "the bundled schema");
            assertTrue(Files.isRegularFile(taxonomy.activeSchema(PkModule.PFA)));
        } finally {
            taxonomy.setActiveVersion("");
        }
        assertEquals(PFQ, Files.readString(taxonomy.activeSchema(PkModule.PFQ)));
        assertFalse(taxonomy.versions().contains(PkTaxonomy.ACTIVE_DIR));
    }
}
