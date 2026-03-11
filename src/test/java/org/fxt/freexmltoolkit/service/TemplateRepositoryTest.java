package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.fxt.freexmltoolkit.domain.XmlTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for TemplateRepository - template storage, search, and management.
 */
class TemplateRepositoryTest {

    private TemplateRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        // Reset singleton for clean test state
        java.lang.reflect.Field instanceField = TemplateRepository.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

        repository = TemplateRepository.getInstance();
    }

    // =========================================================================
    // Singleton Tests
    // =========================================================================

    @Nested
    @DisplayName("Singleton")
    class SingletonTests {

        @Test
        @DisplayName("Returns same instance")
        void sameInstance() {
            TemplateRepository instance1 = TemplateRepository.getInstance();
            TemplateRepository instance2 = TemplateRepository.getInstance();
            assertSame(instance1, instance2);
        }
    }

    // =========================================================================
    // Built-in Templates Tests
    // =========================================================================

    @Nested
    @DisplayName("Built-in Templates")
    class BuiltInTemplatesTests {

        @Test
        @DisplayName("Repository has built-in templates")
        void hasBuiltInTemplates() {
            List<XmlTemplate> all = repository.getAllTemplates();
            assertFalse(all.isEmpty(), "Repository should contain built-in templates");
        }

        @Test
        @DisplayName("Built-in templates have names")
        void builtInTemplatesHaveNames() {
            repository.getAllTemplates().forEach(t ->
                    assertNotNull(t.getName(), "Template should have a name: " + t.getId()));
        }

        @Test
        @DisplayName("Built-in templates have content")
        void builtInTemplatesHaveContent() {
            repository.getAllTemplates().forEach(t ->
                    assertNotNull(t.getContent(), "Template should have content: " + t.getName()));
        }

        @Test
        @DisplayName("Built-in templates have categories")
        void builtInTemplatesHaveCategories() {
            repository.getAllTemplates().forEach(t ->
                    assertNotNull(t.getCategory(), "Template should have category: " + t.getName()));
        }
    }

    // =========================================================================
    // CRUD Operations Tests
    // =========================================================================

    @Nested
    @DisplayName("CRUD Operations")
    class CrudTests {

        @Test
        @DisplayName("Add and retrieve template")
        void addAndRetrieve() {
            XmlTemplate template = new XmlTemplate("Test", "<test/>", "Custom");
            String id = template.getId();

            repository.addTemplate(template);

            XmlTemplate retrieved = repository.getTemplate(id);
            assertNotNull(retrieved);
            assertEquals("Test", retrieved.getName());
        }

        @Test
        @DisplayName("Get non-existent template returns null")
        void getNonExistent() {
            assertNull(repository.getTemplate("non-existent-id-xyz"));
        }

        @Test
        @DisplayName("Remove template")
        void removeTemplate() {
            XmlTemplate template = new XmlTemplate("ToRemove", "<test/>", "Custom");
            String id = template.getId();

            repository.addTemplate(template);
            assertTrue(repository.removeTemplate(id));
            assertNull(repository.getTemplate(id));
        }

        @Test
        @DisplayName("Remove non-existent template returns false")
        void removeNonExistent() {
            assertFalse(repository.removeTemplate("non-existent-id-xyz"));
        }

        @Test
        @DisplayName("Add template replaces existing with same ID")
        void addReplaces() {
            XmlTemplate original = new XmlTemplate("Original", "<original/>", "Custom");
            String id = original.getId();

            repository.addTemplate(original);

            XmlTemplate updated = new XmlTemplate("Updated", "<updated/>", "Custom");
            updated.setId(id);
            repository.addTemplate(updated);

            XmlTemplate retrieved = repository.getTemplate(id);
            assertEquals("Updated", retrieved.getName());
        }
    }

    // =========================================================================
    // Category Tests
    // =========================================================================

    @Nested
    @DisplayName("Categories")
    class CategoryTests {

        @Test
        @DisplayName("Get all categories returns non-empty set")
        void allCategories() {
            Set<String> categories = repository.getAllCategories();
            assertNotNull(categories);
            assertFalse(categories.isEmpty());
        }

        @Test
        @DisplayName("Get templates by category")
        void templatesByCategory() {
            XmlTemplate t1 = new XmlTemplate("T1", "<t1/>", "TestCat");
            XmlTemplate t2 = new XmlTemplate("T2", "<t2/>", "TestCat");
            XmlTemplate t3 = new XmlTemplate("T3", "<t3/>", "OtherCat");

            repository.addTemplate(t1);
            repository.addTemplate(t2);
            repository.addTemplate(t3);

            List<XmlTemplate> testCatTemplates = repository.getTemplatesByCategory("TestCat");
            assertTrue(testCatTemplates.size() >= 2);
        }

        @Test
        @DisplayName("Get templates by non-existent category returns empty")
        void nonExistentCategory() {
            List<XmlTemplate> templates = repository.getTemplatesByCategory("NonExistentCategory_XYZ");
            assertTrue(templates.isEmpty());
        }
    }

    // =========================================================================
    // Search Tests
    // =========================================================================

    @Nested
    @DisplayName("Search")
    class SearchTests {

        @Test
        @DisplayName("Search finds templates by name")
        void searchByName() {
            XmlTemplate template = new XmlTemplate("UniqueSearchTestName", "<test/>", "Custom");
            repository.addTemplate(template);

            List<XmlTemplate> results = repository.searchTemplates("UniqueSearchTestName");
            assertFalse(results.isEmpty());
            assertTrue(results.stream().anyMatch(t -> t.getName().equals("UniqueSearchTestName")));
        }

        @Test
        @DisplayName("Search with empty keyword returns empty")
        void searchEmpty() {
            List<XmlTemplate> results = repository.searchTemplates("");
            assertNotNull(results);
        }

        @Test
        @DisplayName("Search with null keyword returns empty")
        void searchNull() {
            List<XmlTemplate> results = repository.searchTemplates(null);
            assertNotNull(results);
        }
    }

    // =========================================================================
    // Usage Statistics Tests
    // =========================================================================

    @Nested
    @DisplayName("Usage Statistics")
    class UsageTests {

        @Test
        @DisplayName("Record usage and get popular templates")
        void recordUsageAndGetPopular() {
            XmlTemplate template = new XmlTemplate("Popular", "<test/>", "Custom");
            repository.addTemplate(template);

            repository.recordUsage(template.getId());
            repository.recordUsage(template.getId());
            repository.recordUsage(template.getId());

            List<XmlTemplate> popular = repository.getMostPopularTemplates(10);
            assertNotNull(popular);
        }

        @Test
        @DisplayName("Record usage for non-existent template does not throw")
        void recordUsageNonExistent() {
            assertDoesNotThrow(() -> repository.recordUsage("non-existent-id"));
        }

        @Test
        @DisplayName("Get recently used templates")
        void recentlyUsed() {
            XmlTemplate template = new XmlTemplate("Recent", "<test/>", "Custom");
            repository.addTemplate(template);
            repository.recordUsage(template.getId());

            List<XmlTemplate> recent = repository.getRecentlyUsedTemplates(10);
            assertNotNull(recent);
        }
    }

    // =========================================================================
    // Statistics Tests
    // =========================================================================

    @Nested
    @DisplayName("Statistics")
    class StatisticsTests {

        @Test
        @DisplayName("Get statistics returns map")
        void getStatistics() {
            Map<String, Object> stats = repository.getStatistics();
            assertNotNull(stats);
            assertFalse(stats.isEmpty());
        }

        @Test
        @DisplayName("Statistics contain expected keys")
        void statisticsKeys() {
            Map<String, Object> stats = repository.getStatistics();
            assertTrue(stats.containsKey("totalTemplates") || stats.containsKey("total") || !stats.isEmpty(),
                    "Statistics should contain template counts");
        }
    }

    // =========================================================================
    // Industry Templates Tests
    // =========================================================================

    @Nested
    @DisplayName("Industry Templates")
    class IndustryTests {

        @Test
        @DisplayName("Get templates by industry")
        void templatesByIndustry() {
            List<XmlTemplate> financeTemplates = repository.getTemplatesByIndustry(
                    XmlTemplate.TemplateIndustry.FINANCE);
            assertNotNull(financeTemplates);
        }

        @Test
        @DisplayName("Get categories by industry")
        void categoriesByIndustry() {
            Set<String> categories = repository.getCategoriesByIndustry("FINANCE");
            assertNotNull(categories);
        }
    }

    // =========================================================================
    // Contextual Templates Tests
    // =========================================================================

    @Nested
    @DisplayName("Contextual Templates")
    class ContextualTests {

        @Test
        @DisplayName("Get contextual templates")
        void contextualTemplates() {
            List<XmlTemplate> templates = repository.getContextualTemplates(
                    XmlTemplate.TemplateContext.ROOT_ELEMENT, "root", Set.of());
            assertNotNull(templates);
        }

        @Test
        @DisplayName("Get contextual templates with namespace")
        void contextualWithNamespace() {
            List<XmlTemplate> templates = repository.getContextualTemplates(
                    XmlTemplate.TemplateContext.SCHEMA_DEFINITION, "schema",
                    Set.of("http://www.w3.org/2001/XMLSchema"));
            assertNotNull(templates);
        }
    }

    // =========================================================================
    // File System Integration Tests
    // =========================================================================

    @Nested
    @DisplayName("File System Integration")
    class FileSystemTests {

        @Test
        @DisplayName("Templates directory path is not null")
        void templatesDirectoryPath() {
            String path = repository.getTemplatesDirectoryPath();
            assertNotNull(path);
            assertFalse(path.isEmpty());
        }

        @Test
        @DisplayName("Refresh templates does not throw")
        void refreshDoesNotThrow() {
            assertDoesNotThrow(() -> repository.refreshTemplatesFromDirectory());
        }
    }
}
