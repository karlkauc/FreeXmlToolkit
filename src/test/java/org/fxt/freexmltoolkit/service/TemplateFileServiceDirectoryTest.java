package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.fxt.freexmltoolkit.domain.XmlTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that {@link TemplateFileService} honors a runtime directory override and
 * performs a correct save/load/delete round-trip in that directory.
 */
class TemplateFileServiceDirectoryTest {

    @Test
    void honorsRuntimeDirectoryOverrideAndRoundTrips(@TempDir Path dir) throws Exception {
        TemplateFileService service = TemplateFileService.getInstance();

        assertTrue(service.setTemplatesDirectory(dir), "directory should switch");
        assertEquals(dir, service.getTemplatesDirectoryPath());

        XmlTemplate template = new XmlTemplate("My Order", "<order><id/></order>", "Custom");
        template.setDescription("a custom order template");
        service.saveTemplateToDirectory(template);

        // A .template file was written.
        try (var files = Files.list(dir)) {
            assertTrue(files.anyMatch(p -> p.toString().endsWith(".template")));
        }

        List<XmlTemplate> loaded = service.loadTemplatesFromDirectory();
        assertEquals(1, loaded.size());
        XmlTemplate back = loaded.get(0);
        assertEquals("My Order", back.getName());
        assertEquals("Custom", back.getCategory());
        assertTrue(back.getContent().contains("<order>"));
        assertFalse(back.isBuiltIn());

        assertTrue(service.deleteTemplateFromDirectory(template.getId()));
        assertTrue(service.loadTemplatesFromDirectory().isEmpty());
    }

    @Test
    void loadPreservesPersistedTemplateId(@TempDir Path dir) throws Exception {
        TemplateFileService service = TemplateFileService.getInstance();
        assertTrue(service.setTemplatesDirectory(dir));

        XmlTemplate template = new XmlTemplate();
        template.setId("fundsxml-sample-regulatory-eft-xml");
        template.setName("EFT_Regulatory");
        template.setContent("<?xml version=\"1.0\"?>\n<FundsXML4/>");
        template.setCategory("FundsXML");
        template.setBuiltIn(false);
        service.saveTemplateToDirectory(template);

        List<XmlTemplate> loaded = service.loadTemplatesFromDirectory();
        assertEquals(1, loaded.size());
        // The persisted ID must survive the round-trip — a fresh UUID per load breaks
        // idempotent re-seeding (duplicates pile up) and delete-by-id (files become
        // undeletable because the filename no longer matches any template's ID).
        assertEquals("fundsxml-sample-regulatory-eft-xml", loaded.get(0).getId());
    }

    @Test
    void nullDirectoryIsIgnored() {
        TemplateFileService service = TemplateFileService.getInstance();
        Path before = service.getTemplatesDirectoryPath();
        assertNotNull(before);
        assertFalse(service.setTemplatesDirectory(null));
        assertEquals(before, service.getTemplatesDirectoryPath());
    }
}
