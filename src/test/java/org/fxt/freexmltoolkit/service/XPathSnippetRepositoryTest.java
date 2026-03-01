package org.fxt.freexmltoolkit.service;

import org.fxt.freexmltoolkit.domain.XPathSnippet;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("XPathSnippetRepository Tests")
public class XPathSnippetRepositoryTest {

    private XPathSnippetRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        // Reset Singleton using reflection for test isolation
        Field instanceField = XPathSnippetRepository.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
        
        repository = XPathSnippetRepository.getInstance();
    }

    @Test
    @DisplayName("Sollte Singleton-Instanz bereitstellen")
    void testSingleton() {
        XPathSnippetRepository instance2 = XPathSnippetRepository.getInstance();
        assertSame(repository, instance2);
    }

    @Test
    @DisplayName("Sollte integrierte Snippets laden")
    void testBuiltInSnippets() {
        List<XPathSnippet> all = repository.getAllSnippets();
        assertFalse(all.isEmpty(), "Es sollten Snippets geladen sein");
        
        long builtInCount = all.stream()
                .filter(s -> "system".equals(s.getAuthor()))
                .count();
        assertTrue(builtInCount > 0, "System-Snippets sollten vorhanden sein");
    }

    @Test
    @DisplayName("Sollte CRUD-Operationen unterstützen")
    void testCRUD() {
        String id = "test-snippet-123";
        XPathSnippet snippet = XPathSnippet.builder()
                .name("Test CRUD Snippet")
                .query("/root/test")
                .category(XPathSnippet.SnippetCategory.NAVIGATION)
                .type(XPathSnippet.SnippetType.XPATH)
                .author("junit")
                .tags("junit", "test")
                .build();
        snippet.setId(id);

        // Create
        repository.saveSnippet(snippet);
        
        // Read
        XPathSnippet retrieved = repository.getSnippet(id);
        assertNotNull(retrieved);
        assertEquals("Test CRUD Snippet", retrieved.getName());

        // Update
        retrieved.setDescription("Updated Description");
        repository.updateSnippet(retrieved);
        assertEquals("Updated Description", repository.getSnippet(id).getDescription());

        // Delete
        boolean deleted = repository.deleteSnippet(id);
        assertTrue(deleted);
        assertNull(repository.getSnippet(id));
    }

    @Test
    @DisplayName("Sollte Snippets suchen können")
    void testSearch() {
        XPathSnippet snippet = XPathSnippet.builder()
                .name("UniqueSearchName")
                .query("/unique/path")
                .category(XPathSnippet.SnippetCategory.UTILITY)
                .build();
        repository.saveSnippet(snippet);

        List<XPathSnippet> results = repository.searchSnippets("UniqueSearchName");
        assertFalse(results.isEmpty());
        assertEquals("UniqueSearchName", results.get(0).getName());
    }

    @Test
    @DisplayName("Sollte nach Kategorie filtern")
    void testFilterByCategory() {
        List<XPathSnippet> navSnippets = repository.getSnippetsByCategory(XPathSnippet.SnippetCategory.NAVIGATION);
        assertFalse(navSnippets.isEmpty());
        assertTrue(navSnippets.stream().allMatch(s -> s.getCategory() == XPathSnippet.SnippetCategory.NAVIGATION));
    }

    @Test
    @DisplayName("Sollte Statistiken erfassen")
    void testStatistics() {
        XPathSnippet snippet = XPathSnippet.builder()
                .name("StatSnippet")
                .query("/stat/test")
                .category(XPathSnippet.SnippetCategory.ANALYSIS)
                .build();
        repository.saveSnippet(snippet);

        repository.recordExecution(snippet.getId(), 100);
        repository.recordExecution(snippet.getId(), 150);

        XPathSnippet updated = repository.getSnippet(snippet.getId());
        assertEquals(2, updated.getExecutionCount());
        
        XPathSnippetRepository.RepositoryStatistics stats = repository.getStatistics();
        assertEquals(2, stats.totalExecutions);
    }

    @Test
    @DisplayName("Sollte fortgeschrittene Suche unterstützen")
    void testAdvancedSearch() {
        XPathSnippet snippet = XPathSnippet.builder()
                .name("AdvSearch")
                .query("/adv/test")
                .category(XPathSnippet.SnippetCategory.VALIDATION)
                .type(XPathSnippet.SnippetType.XQUERY)
                .tags("adv-tag")
                .build();
        repository.saveSnippet(snippet);

        XPathSnippetRepository.AdvancedSearchCriteria criteria = new XPathSnippetRepository.AdvancedSearchCriteria();
        criteria.setCategories(Set.of(XPathSnippet.SnippetCategory.VALIDATION));
        criteria.setTags(Set.of("adv-tag"));
        
        List<XPathSnippet> results = repository.advancedSearch(criteria);
        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(s -> s.getName().equals("AdvSearch")));
    }
}
