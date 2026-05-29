package org.fxt.freexmltoolkit.controls.shell.editor;

import org.fxt.freexmltoolkit.controls.icons.IconifyIconService;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link EditorFileType}, which classifies files opened in the Unified
 * editor host by extension so the editor and inspector can adapt.
 */
class EditorFileTypeTest {

    @Test
    void classifiesKnownExtensionsCaseInsensitively() {
        assertEquals(EditorFileType.XML, EditorFileType.fromFileName("data.xml"));
        assertEquals(EditorFileType.XML, EditorFileType.fromFileName("DATA.XML"));
        assertEquals(EditorFileType.XSD, EditorFileType.fromFileName("schema.xsd"));
        assertEquals(EditorFileType.XSLT, EditorFileType.fromFileName("t.xsl"));
        assertEquals(EditorFileType.XSLT, EditorFileType.fromFileName("t.xslt"));
        assertEquals(EditorFileType.SCHEMATRON, EditorFileType.fromFileName("rules.sch"));
        assertEquals(EditorFileType.JSON, EditorFileType.fromFileName("config.json"));
    }

    @Test
    void unknownOrMissingExtensionFallsBackToOther() {
        assertEquals(EditorFileType.OTHER, EditorFileType.fromFileName("readme.txt"));
        assertEquals(EditorFileType.OTHER, EditorFileType.fromFileName("noextension"));
        assertEquals(EditorFileType.OTHER, EditorFileType.fromFileName(""));
        assertEquals(EditorFileType.OTHER, EditorFileType.fromFileName(null));
    }

    @Test
    void everyTypeHasNonBlankLabelAndBootstrapIcon() {
        for (EditorFileType t : EditorFileType.values()) {
            assertFalse(t.label().isBlank(), () -> t + " has blank label");
            assertNotNull(t.icon());
            assertTrue(t.icon().startsWith("bi-"), () -> t + " icon must be a Bootstrap icon");
        }
    }

    @Test
    void everyIconResolvesInTheBundle() {
        IconifyIconService icons = IconifyIconService.getInstance();
        for (EditorFileType t : EditorFileType.values()) {
            assertTrue(icons.exists(t.icon()), () -> t + " uses unknown icon '" + t.icon() + "'");
        }
    }

    @Test
    void extensionsDoNotOverlapAcrossTypes() {
        Set<String> seen = new HashSet<>();
        for (EditorFileType t : EditorFileType.values()) {
            for (String ext : t.extensions()) {
                assertTrue(seen.add(ext), () -> "extension '" + ext + "' is mapped to more than one type");
            }
        }
    }
}
