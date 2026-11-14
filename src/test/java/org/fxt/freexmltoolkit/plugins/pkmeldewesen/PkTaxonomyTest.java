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
}
