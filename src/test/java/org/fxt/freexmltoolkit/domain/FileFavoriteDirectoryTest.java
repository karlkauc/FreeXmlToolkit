package org.fxt.freexmltoolkit.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FileFavoriteDirectoryTest {

    @Test
    void newFavoriteIsFileByDefault() {
        FileFavorite f = new FileFavorite("alpha", "/tmp/alpha.xml", "Work");
        assertFalse(f.isDirectory(), "existing constructor must default to file favorite");
    }

    @Test
    void directoryFlagIsPersistedInState() {
        FileFavorite f = new FileFavorite("xmlRoot", "/data/xml", "Work");
        f.setDirectory(true);
        assertTrue(f.isDirectory());
    }

    @Test
    void directoryConstructorSetsFlagAndType() {
        FileFavorite f = FileFavorite.forDirectory("xmlRoot", "/data/xml", FileFavorite.FileType.XML);
        assertTrue(f.isDirectory());
        assertEquals(FileFavorite.FileType.XML, f.getFileType());
        assertEquals("/data/xml", f.getFilePath());
    }
}
