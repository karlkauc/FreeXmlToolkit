package org.fxt.freexmltoolkit.service.ai.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceAccessTest {

    @TempDir
    Path temp;

    private Path root;
    private Path outside;
    private WorkspaceAccess access;

    @BeforeEach
    void setUp() throws IOException {
        root = Files.createDirectories(temp.resolve("root"));
        outside = Files.createDirectories(temp.resolve("outside"));
        Files.writeString(root.resolve("a.xml"), "<a/>");
        Files.createDirectories(root.resolve("sub"));
        Files.writeString(root.resolve("sub/b.xml"), "<b/>");
        Files.writeString(outside.resolve("secret.xml"), "<secret/>");
        access = new WorkspaceAccess(List.of(root));
    }

    @Test
    void pathInsideARootResolves() throws Exception {
        assertEquals(root.resolve("sub/b.xml").toRealPath(), access.resolve(root.resolve("sub/b.xml").toString()));
    }

    @Test
    void relativePathsResolveAgainstTheFirstRoot() throws Exception {
        assertEquals(root.resolve("sub/b.xml").toRealPath(), access.resolve("sub/b.xml"));
    }

    @Test
    void dotDotEscapeIsRefused() {
        ToolException e = assertThrows(ToolException.class, () -> access.resolve("../outside/secret.xml"));
        assertTrue(e.getMessage().contains(root.toString()) || e.getMessage().contains("root"), e.getMessage());
    }

    @Test
    void absolutePathOutsideIsRefusedNamingTheRoots() throws Exception {
        ToolException e = assertThrows(ToolException.class,
                () -> access.resolve(outside.resolve("secret.xml").toString()));
        assertTrue(e.getMessage().contains(root.toRealPath().toString()), e.getMessage());
    }

    @Test
    void symlinkPointingOutsideIsRefused() throws Exception {
        Path link = root.resolve("link.xml");
        Path dirLink = root.resolve("dirlink");
        try {
            Files.createSymbolicLink(link, outside.resolve("secret.xml"));
            Files.createSymbolicLink(dirLink, outside);
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            Assumptions.abort("symbolic links not available: " + e);
        }
        assertThrows(ToolException.class, () -> access.resolve("link.xml"));
        assertThrows(ToolException.class, () -> access.resolve("dirlink/secret.xml"));
        // a not-yet-existing file below a linked folder is outside as well
        assertThrows(ToolException.class, () -> access.resolve("dirlink/new.xml"));
    }

    @Test
    void nonExistingFileUnderARootResolvesButCannotBeRead() throws Exception {
        Path missing = access.resolve("sub/missing.xml");
        assertEquals(root.toRealPath().resolve("sub/missing.xml"), missing);
        assertThrows(ToolException.class, () -> access.read(missing, 1024));
    }

    @Test
    void fileLargerThanTheLimitIsRefusedWithItsSize() throws Exception {
        Path big = root.resolve("big.xml");
        Files.writeString(big, "<a>" + "x".repeat(2000) + "</a>");
        ToolException e = assertThrows(ToolException.class, () -> access.read(access.resolve("big.xml"), 100));
        assertTrue(e.getMessage().contains(String.valueOf(Files.size(big))), e.getMessage());
    }

    @Test
    void readHonoursTheDeclaredEncoding() throws Exception {
        Path latin = root.resolve("latin.xml");
        Files.write(latin, "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><a>Größe</a>"
                .getBytes(StandardCharsets.ISO_8859_1));
        assertTrue(access.read(access.resolve("latin.xml"), 1024).contains("Größe"));
        Path utf8 = root.resolve("utf8.xml");
        Files.write(utf8, "﻿<a>Größe</a>".getBytes(StandardCharsets.UTF_8));
        assertEquals("<a>Größe</a>", access.read(access.resolve("utf8.xml"), 1024));
    }

    @Test
    void aFolderCannotBeRead() {
        assertThrows(ToolException.class, () -> access.read(access.resolve("sub"), 1024));
    }

    @Test
    void missingRootIsRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new WorkspaceAccess(List.of(temp.resolve("nope"))));
        assertThrows(IllegalArgumentException.class, () -> new WorkspaceAccess(List.of()));
    }

    @Test
    void blankPathIsRefused() {
        assertThrows(ToolException.class, () -> access.resolve(" "));
        assertThrows(ToolException.class, () -> access.resolve(null));
    }
}
