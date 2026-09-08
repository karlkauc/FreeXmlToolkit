package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;

import org.fxt.freexmltoolkit.controls.icons.IconifyIconService;
import org.junit.jupiter.api.Test;

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
        assertEquals(EditorFileType.JSON, EditorFileType.fromFileName("settings.jsonc"));
        assertEquals(EditorFileType.JSON, EditorFileType.fromFileName("data.JSON5"));
        assertEquals(EditorFileType.XML, EditorFileType.fromFileName("service.wsdl"));
        assertEquals(EditorFileType.XQUERY, EditorFileType.fromFileName("query.xq"));
        assertEquals(EditorFileType.XQUERY, EditorFileType.fromFileName("query.xquery"));
        assertEquals(EditorFileType.XQUERY, EditorFileType.fromFileName("module.xqm"));
        assertEquals(EditorFileType.XQUERY, EditorFileType.fromFileName("query.XQY"));
        assertEquals(EditorFileType.XPATH, EditorFileType.fromFileName("expr.xpath"));
        assertEquals(EditorFileType.XPROC, EditorFileType.fromFileName("pipeline.xpl"));
        assertEquals(EditorFileType.XPROC, EditorFileType.fromFileName("pipeline.XPROC"));
        assertEquals(EditorFileType.HTML, EditorFileType.fromFileName("report.html"));
        assertEquals(EditorFileType.HTML, EditorFileType.fromFileName("REPORT.HTM"));
        assertEquals(EditorFileType.HTML, EditorFileType.fromFileName("page.xhtml"));
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
    void primaryExtensionMatchesFirstDeclaredExtension() {
        assertEquals("xml", EditorFileType.XML.primaryExtension());
        assertEquals("xsd", EditorFileType.XSD.primaryExtension());
        assertEquals("xsl", EditorFileType.XSLT.primaryExtension());
        assertEquals("sch", EditorFileType.SCHEMATRON.primaryExtension());
        assertEquals("json", EditorFileType.JSON.primaryExtension());
        assertEquals("xq", EditorFileType.XQUERY.primaryExtension());
        assertEquals("xpath", EditorFileType.XPATH.primaryExtension());
        assertEquals("xpl", EditorFileType.XPROC.primaryExtension());
        assertEquals("html", EditorFileType.HTML.primaryExtension());
        assertEquals("txt", EditorFileType.OTHER.primaryExtension());
    }

    @Test
    void defaultContentProvidesSensibleBoilerplate() {
        assertTrue(EditorFileType.XML.defaultContent().startsWith("<?xml version=\"1.0\""));
        assertTrue(EditorFileType.XSD.defaultContent().contains("<xs:schema"));
        assertTrue(EditorFileType.XSLT.defaultContent().contains("<xsl:stylesheet"));
        assertTrue(EditorFileType.SCHEMATRON.defaultContent()
                .contains("http://purl.oclc.org/dsdl/schematron"));
        assertEquals("{\n}\n", EditorFileType.JSON.defaultContent());
        assertTrue(EditorFileType.XQUERY.defaultContent().startsWith("xquery version"));
        assertTrue(EditorFileType.XPROC.defaultContent().contains("<p:declare-step"));
        assertTrue(EditorFileType.HTML.defaultContent().contains("<!DOCTYPE html>"));
        assertEquals("", EditorFileType.XPATH.defaultContent());
        assertEquals("", EditorFileType.OTHER.defaultContent());
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

    @Test
    void openableExtensionsCoverEveryTypedExtensionWithLeadingDot() {
        var openable = EditorFileType.openableExtensions();
        for (EditorFileType t : EditorFileType.values()) {
            for (String ext : t.extensions()) {
                assertTrue(openable.contains("." + ext), () -> "'." + ext + "' must be droppable/openable");
            }
        }
        assertTrue(openable.contains(".json"), "JSON documents open in the editor");
        assertTrue(openable.contains(".jsonc"), "JSONC documents open in the editor");
        assertTrue(openable.contains(".json5"), "JSON5 documents open in the editor");
        assertTrue(openable.contains(".html"), "HTML documents open in the editor (Preview)");
        assertEquals(openable.size(), openable.stream().distinct().count(), "no duplicates");
        assertEquals(EditorFileType.OTHER, EditorFileType.fromFileName("a.png"));
        assertFalse(openable.contains(".png"), "unknown types stay excluded");
    }

    @Test
    void openableGlobsMirrorOpenableExtensions() {
        var globs = EditorFileType.openableGlobs();
        assertEquals(EditorFileType.openableExtensions().size(), globs.size());
        assertTrue(globs.contains("*.xml"));
        assertTrue(globs.contains("*.jsonc"));
        assertTrue(globs.contains("*.html"));
        assertTrue(globs.stream().allMatch(g -> g.startsWith("*.")), "every glob is '*.<ext>'");
    }

    @Test
    void fileChooserFiltersOfferEverySupportedTypePlusAllFiles() {
        var filters = EditorFileType.fileChooserFilters();
        assertEquals("All supported files", filters.getFirst().getDescription());
        assertTrue(filters.getFirst().getExtensions().containsAll(EditorFileType.openableGlobs()),
                "the first filter accepts every openable type");
        assertEquals("All files", filters.getLast().getDescription());
        assertEquals(java.util.List.of("*.*"), filters.getLast().getExtensions());
        for (EditorFileType t : EditorFileType.values()) {
            if (t == EditorFileType.OTHER) {
                continue;
            }
            var own = filters.stream().filter(f -> f.getDescription().startsWith(t.label())).findFirst();
            assertTrue(own.isPresent(), () -> "a filter for " + t.label());
            for (String ext : t.extensions()) {
                assertTrue(own.get().getExtensions().contains("*." + ext), () -> t.label() + " filter lists *." + ext);
            }
        }
    }
}
