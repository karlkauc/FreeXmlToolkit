package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link OpenDocument}, the per-tab model of the Unified editor host
 * (path, derived file type, display name, dirty state).
 */
class OpenDocumentTest {

    @Test
    void documentFromPathExposesTypeNameAndPath() {
        OpenDocument doc = OpenDocument.forPath(Path.of("/tmp/data.xml"));
        assertEquals(EditorFileType.XML, doc.getFileType());
        assertEquals("data.xml", doc.getDisplayName());
        assertEquals(Path.of("/tmp/data.xml"), doc.getPath());
        assertFalse(doc.isUntitled());
        assertFalse(doc.isDirty());
    }

    @Test
    void untitledDocumentHasNoPath() {
        OpenDocument doc = OpenDocument.untitled("Untitled 1", EditorFileType.XML);
        assertTrue(doc.isUntitled());
        assertNull(doc.getPath());
        assertEquals("Untitled 1", doc.getDisplayName());
        assertEquals(EditorFileType.XML, doc.getFileType());
    }

    @Test
    void dirtyFlagIsObservable() {
        OpenDocument doc = OpenDocument.forPath(Path.of("/tmp/data.xml"));
        AtomicBoolean notified = new AtomicBoolean(false);
        doc.dirtyProperty().addListener((obs, oldV, newV) -> notified.set(newV));

        doc.setDirty(true);

        assertTrue(doc.isDirty());
        assertTrue(notified.get());
    }

    @Test
    void savingToANewPathUpdatesPathTypeAndName() {
        OpenDocument doc = OpenDocument.untitled("Untitled 1", EditorFileType.XML);
        doc.setPath(Path.of("/tmp/out/rules.sch"));

        assertFalse(doc.isUntitled());
        assertEquals(Path.of("/tmp/out/rules.sch"), doc.getPath());
        assertEquals("rules.sch", doc.getDisplayName());
        assertEquals(EditorFileType.SCHEMATRON, doc.getFileType());
    }
}
