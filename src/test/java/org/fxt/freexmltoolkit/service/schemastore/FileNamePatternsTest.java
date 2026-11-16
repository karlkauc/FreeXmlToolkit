package org.fxt.freexmltoolkit.service.schemastore;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class FileNamePatternsTest {

    private static boolean matches(String pattern, String file) {
        return FileNamePatterns.matches(List.of(pattern), Path.of(file));
    }

    @Test
    void aPatternWithoutSlashMatchesTheFileName() {
        assertTrue(matches("docker-compose*.yml", "/p/docker-compose.prod.yml"));
        assertTrue(matches("Docker-Compose.yml", "/p/docker-compose.YML"), "case is ignored");
        assertFalse(matches("docker-compose*.yml", "/p/docker-compose.yaml"));
        assertTrue(matches("*.{yml,yaml}", "/p/a.yaml"));
        assertTrue(matches("?.json", "/p/a.json"));
        assertFalse(matches("?.json", "/p/ab.json"));
        assertTrue(matches("[ab].json", "/p/b.json"));
    }

    @Test
    void aPatternWithSlashMatchesTheEndOfThePathByWholeFolders() {
        assertTrue(matches(".github/workflows/*.yml", "/home/u/repo/.github/workflows/build.yml"));
        assertTrue(matches("**/.github/workflows/*.yml", "/home/u/repo/.github/workflows/build.yml"));
        assertFalse(matches(".github/workflows/*.yml", "/home/u/repo/.github/workflows/sub/build.yml"));
        assertFalse(matches(".github/workflows/*.yml", "/home/u/repo/x.github/workflows/build.yml"));
        assertTrue(matches(".github/**/*.yml", "/r/.github/a/b/c.yml"));
        assertTrue(matches("/.vscode/settings.json", "/r/.vscode/settings.json"));
    }

    @Test
    void exclusionsAndSpecificity() {
        Path file = Path.of("/r/.github/workflows/build.yml");
        assertNull(FileNamePatterns.bestMatch(List.of("*.yml", "!**/.github/**"), file));
        assertEquals(".github/workflows/*.yml",
                FileNamePatterns.bestMatch(List.of("*.yml", ".github/workflows/*.yml"), file));
        assertTrue(FileNamePatterns.specificity("docker-compose.yml") > FileNamePatterns.specificity("*.yml"));
    }

    @Test
    void userListsAndProblems() {
        assertEquals(List.of("a.yml", "*.{json,yaml}", "b"), FileNamePatterns.parseList(" a.yml, *.{json,yaml};\nb "));
        assertNull(FileNamePatterns.problemOf("*.yml"));
        assertNotNull(FileNamePatterns.problemOf("{a,b"));
        assertNotNull(FileNamePatterns.problemOf(" "));
    }
}
