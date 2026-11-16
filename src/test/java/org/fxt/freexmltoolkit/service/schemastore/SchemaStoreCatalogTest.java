package org.fxt.freexmltoolkit.service.schemastore;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchemaStoreCatalogTest {

    static final String CATALOG = """
            {"$schema": "https://json.schemastore.org/schema-catalog.json", "version": 1, "schemas": [
              {"name": "GitHub Workflow", "description": "workflows",
               "fileMatch": ["**/.github/workflows/*.yml", "**/.github/workflows/*.yaml"],
               "url": "https://json.schemastore.org/github-workflow.json"},
              {"name": "docker-compose", "fileMatch": ["docker-compose.yml", "docker-compose*.yml", "compose.yaml"],
               "url": "https://example.org/compose.json"},
              {"name": "Any YAML", "fileMatch": ["*.yml"], "url": "https://example.org/any.json"},
              {"name": "No URL", "fileMatch": ["x.json"]},
              {"name": "Local", "fileMatch": ["y.json"], "url": "file:///etc/passwd"}
            ]}
            """;

    @TempDir Path dir;

    @Test
    void parsesSchemasWithAWebAddressOnly() throws IOException {
        List<SchemaStoreCatalog.Schema> schemas = SchemaStoreCatalog.parse(CATALOG);
        assertEquals(3, schemas.size());
        assertEquals(List.of("docker-compose.yml", "docker-compose*.yml", "compose.yaml"), schemas.get(1).fileMatch());
        assertThrows(IOException.class, () -> SchemaStoreCatalog.parse("{\"x\": 1}"));
        assertThrows(IOException.class, () -> SchemaStoreCatalog.parse("not json"));
    }

    @Test
    void refreshKeepsTheCatalogAndMatchesTheMostSpecificSchema() throws IOException {
        List<URI> fetched = new ArrayList<>();
        SchemaStoreCatalog catalog = new SchemaStoreCatalog(dir.resolve("c/catalog.json"), uri -> {
            fetched.add(uri);
            return CATALOG.getBytes(StandardCharsets.UTF_8);
        });
        assertTrue(catalog.isStale());
        assertTrue(catalog.match(Path.of("/r/docker-compose.yml")).isEmpty(), "no catalog yet");
        assertEquals(3, catalog.refresh(null));
        assertEquals(URI.create(SchemaStoreCatalog.DEFAULT_URL), fetched.get(0));
        assertFalse(catalog.isStale());

        var compose = catalog.match(Path.of("/r/docker-compose.yml")).orElseThrow();
        assertEquals("docker-compose", compose.schema().name());
        assertEquals("docker-compose.yml", compose.pattern());
        assertEquals("GitHub Workflow", catalog.match(Path.of("/r/.github/workflows/ci.yml")).orElseThrow().schema().name());
        assertEquals("Any YAML", catalog.match(Path.of("/r/other.yml")).orElseThrow().schema().name());
        assertTrue(catalog.match(Path.of("/r/other.json")).isEmpty());

        // read again from the file by a new instance
        SchemaStoreCatalog again = new SchemaStoreCatalog(dir.resolve("c/catalog.json"), uri -> {
            throw new IOException("offline");
        });
        assertEquals(3, again.schemas().size());
        assertThrows(IOException.class, () -> again.refresh("https://mirror.example/catalog.json"));
        assertEquals(3, again.schemas().size(), "a failed update keeps the catalog");
    }

    @Test
    void aDownloadThatIsNoCatalogIsNotKept() throws IOException {
        Path file = dir.resolve("catalog.json");
        Files.writeString(file, CATALOG);
        SchemaStoreCatalog catalog = new SchemaStoreCatalog(file, uri -> "<html>login</html>".getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> catalog.refresh(null));
        assertEquals(CATALOG, Files.readString(file));
    }
}
