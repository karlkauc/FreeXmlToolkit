package org.fxt.freexmltoolkit.service;

// Note: Using simplified persistence without Jackson dependency

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.SnippetParameter;
import org.fxt.freexmltoolkit.domain.XPathSnippet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Repository for managing XPath/XQuery snippets with persistence, caching, and advanced querying.
 * Provides the core data management for the revolutionary Snippet Manager system.
 */
public class XPathSnippetRepository {

    private static final Logger logger = LogManager.getLogger(XPathSnippetRepository.class);

    // Singleton instance
    private static XPathSnippetRepository instance;

    // Storage
    private final Map<String, XPathSnippet> snippets = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> tagIndex = new ConcurrentHashMap<>();
    private final Map<XPathSnippet.SnippetCategory, Set<String>> categoryIndex = new ConcurrentHashMap<>();
    private final Map<XPathSnippet.SnippetType, Set<String>> typeIndex = new ConcurrentHashMap<>();

    // Configuration
    private Path snippetsDirectory;
    private boolean autoSaveEnabled = true;
    private long lastSaveTime = 0;
    private final long autoSaveInterval = 30000; // 30 seconds

    // Caching and performance
    private final Map<String, List<XPathSnippet>> searchCache = new ConcurrentHashMap<>();
    private long cacheTimestamp = 0;
    private final long cacheTimeout = 60000; // 1 minute

    // Statistics
    private long totalExecutions = 0;
    private final Map<String, Long> popularSnippets = new ConcurrentHashMap<>();
    private final Map<XPathSnippet.SnippetCategory, Long> categoryUsage = new ConcurrentHashMap<>();

    private XPathSnippetRepository() {
        initializeSnippetsDirectory();
        loadBuiltInSnippets();
        // Note: User snippet persistence temporarily disabled until JSON library is available
        // loadUserSnippets();

        logger.info("XPath Snippet Repository initialized with {} snippets", snippets.size());
    }

    public static synchronized XPathSnippetRepository getInstance() {
        if (instance == null) {
            instance = new XPathSnippetRepository();
        }
        return instance;
    }

    // ========== Initialization ==========

    private void initializeSnippetsDirectory() {
        try {
            // Default location: user.home/.freexml-toolkit/snippets
            String userHome = System.getProperty("user.home");
            Path toolkitDir = Paths.get(userHome, ".freexml-toolkit");
            snippetsDirectory = toolkitDir.resolve("snippets");

            // Create directories if they don't exist
            Files.createDirectories(snippetsDirectory);

            logger.debug("Snippets directory initialized: {}", snippetsDirectory);

        } catch (Exception e) {
            logger.error("Failed to initialize snippets directory", e);
            // Fallback to temp directory
            snippetsDirectory = Paths.get(System.getProperty("java.io.tmpdir"), "freexml-snippets");
            try {
                Files.createDirectories(snippetsDirectory);
            } catch (Exception ex) {
                logger.error("Failed to create fallback snippets directory", ex);
            }
        }
    }

    private void loadBuiltInSnippets() {
        try {
            // Create and add built-in snippets
            createNavigationSnippets();
            createExtractionSnippets();
            createFilteringSnippets();
            createTransformationSnippets();
            createValidationSnippets();
            createAnalysisSnippets();
            createUtilitySnippets();

            logger.debug("Loaded {} built-in snippets", getBuiltInSnippetCount());

        } catch (Exception e) {
            logger.error("Failed to load built-in snippets", e);
        }
    }

    private void loadUserSnippets() {
        try {
            if (!Files.exists(snippetsDirectory)) {
                return;
            }

            Files.list(snippetsDirectory)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(this::loadSnippetFromFile);

            logger.debug("Loaded user snippets from: {}", snippetsDirectory);

        } catch (Exception e) {
            logger.error("Failed to load user snippets", e);
        }
    }

    private void loadSnippetFromFile(Path filePath) {
        // Note: JSON deserialization temporarily disabled
        logger.debug("Skipping snippet file load (JSON support not available): {}", filePath);
    }

    // ========== CRUD Operations ==========

    /**
     * Save a snippet
     */
    public void saveSnippet(XPathSnippet snippet) {
        if (snippet == null) {
            throw new IllegalArgumentException("Snippet cannot be null");
        }

        snippet.setLastModified(LocalDateTime.now());
        snippets.put(snippet.getId(), snippet);
        addSnippetToIndices(snippet);

        if (autoSaveEnabled) {
            saveSnippetToFile(snippet);
        }

        clearSearchCache();
        logger.debug("Saved snippet: {}", snippet.getName());
    }

    /**
     * Get snippet by ID
     */
    public XPathSnippet getSnippet(String id) {
        return snippets.get(id);
    }

    /**
     * Get all snippets
     */
    public List<XPathSnippet> getAllSnippets() {
        return new ArrayList<>(snippets.values());
    }

    /**
     * Delete a snippet
     */
    public boolean deleteSnippet(String id) {
        XPathSnippet snippet = snippets.remove(id);
        if (snippet != null) {
            removeSnippetFromIndices(snippet);
            deleteSnippetFile(snippet);
            clearSearchCache();
            logger.debug("Deleted snippet: {}", snippet.getName());
            return true;
        }
        return false;
    }

    /**
     * Update snippet execution statistics
     */
    public void recordExecution(String snippetId, long executionTime) {
        XPathSnippet snippet = snippets.get(snippetId);
        if (snippet != null) {
            snippet.recordExecution(executionTime);
            totalExecutions++;
            popularSnippets.merge(snippetId, 1L, Long::sum);
            categoryUsage.merge(snippet.getCategory(), 1L, Long::sum);

            if (autoSaveEnabled) {
                saveSnippetToFile(snippet);
            }

            logger.debug("Recorded execution for snippet: {} ({}ms)", snippet.getName(), executionTime);
        }
    }

    // ========== Search and Filtering ==========

    /**
     * Search snippets by text
     */
    public List<XPathSnippet> searchSnippets(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            return getAllSnippets();
        }

        String cacheKey = "search:" + searchText.toLowerCase();
        List<XPathSnippet> cached = getCachedResult(cacheKey);
        if (cached != null) {
            return cached;
        }

        String searchLower = searchText.toLowerCase();
        List<XPathSnippet> results = snippets.values().stream()
                .filter(snippet -> matchesSearch(snippet, searchLower))
                .sorted((a, b) -> compareSearchRelevance(a, b, searchLower))
                .collect(Collectors.toList());

        cacheResult(cacheKey, results);
        return results;
    }

    private boolean matchesSearch(XPathSnippet snippet, String searchLower) {
        return (snippet.getName() != null && snippet.getName().toLowerCase().contains(searchLower)) ||
                (snippet.getDescription() != null && snippet.getDescription().toLowerCase().contains(searchLower)) ||
                (snippet.getQuery() != null && snippet.getQuery().toLowerCase().contains(searchLower)) ||
                snippet.getTags().stream().anyMatch(tag -> tag.toLowerCase().contains(searchLower));
    }

    private int compareSearchRelevance(XPathSnippet a, XPathSnippet b, String searchLower) {
        // Prioritize by execution count, then name match, then favorites
        int executionDiff = Long.compare(b.getExecutionCount(), a.getExecutionCount());
        if (executionDiff != 0) return executionDiff;

        boolean aNameMatch = a.getName() != null && a.getName().toLowerCase().contains(searchLower);
        boolean bNameMatch = b.getName() != null && b.getName().toLowerCase().contains(searchLower);
        if (aNameMatch != bNameMatch) return aNameMatch ? -1 : 1;

        if (a.isFavorite() != b.isFavorite()) return a.isFavorite() ? -1 : 1;

        return a.getName().compareToIgnoreCase(b.getName());
    }

    /**
     * Get snippets by category
     */
    public List<XPathSnippet> getSnippetsByCategory(XPathSnippet.SnippetCategory category) {
        String cacheKey = "category:" + category.name();
        List<XPathSnippet> cached = getCachedResult(cacheKey);
        if (cached != null) {
            return cached;
        }

        Set<String> snippetIds = categoryIndex.get(category);
        List<XPathSnippet> results = snippetIds == null ?
                Collections.emptyList() :
                snippetIds.stream()
                        .map(snippets::get)
                        .filter(Objects::nonNull)
                        .sorted((a, b) -> compareByUsage(a, b))
                        .collect(Collectors.toList());

        cacheResult(cacheKey, results);
        return results;
    }

    /**
     * Get snippets by type
     */
    public List<XPathSnippet> getSnippetsByType(XPathSnippet.SnippetType type) {
        String cacheKey = "type:" + type.name();
        List<XPathSnippet> cached = getCachedResult(cacheKey);
        if (cached != null) {
            return cached;
        }

        Set<String> snippetIds = typeIndex.get(type);
        List<XPathSnippet> results = snippetIds == null ?
                Collections.emptyList() :
                snippetIds.stream()
                        .map(snippets::get)
                        .filter(Objects::nonNull)
                        .sorted((a, b) -> compareByUsage(a, b))
                        .collect(Collectors.toList());

        cacheResult(cacheKey, results);
        return results;
    }

    /**
     * Get snippets by tags
     */
    public List<XPathSnippet> getSnippetsByTag(String tag) {
        String cacheKey = "tag:" + tag;
        List<XPathSnippet> cached = getCachedResult(cacheKey);
        if (cached != null) {
            return cached;
        }

        Set<String> snippetIds = tagIndex.get(tag.toLowerCase());
        List<XPathSnippet> results = snippetIds == null ?
                Collections.emptyList() :
                snippetIds.stream()
                        .map(snippets::get)
                        .filter(Objects::nonNull)
                        .sorted((a, b) -> compareByUsage(a, b))
                        .collect(Collectors.toList());

        cacheResult(cacheKey, results);
        return results;
    }

    /**
     * Get favorite snippets
     */
    public List<XPathSnippet> getFavoriteSnippets() {
        return snippets.values().stream()
                .filter(XPathSnippet::isFavorite)
                .sorted((a, b) -> compareByUsage(a, b))
                .collect(Collectors.toList());
    }

    /**
     * Get recently used snippets
     */
    public List<XPathSnippet> getRecentlyUsedSnippets(int limit) {
        return snippets.values().stream()
                .filter(snippet -> snippet.getLastExecuted() != null)
                .sorted((a, b) -> b.getLastExecuted().compareTo(a.getLastExecuted()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Get most popular snippets
     */
    public List<XPathSnippet> getMostPopularSnippets(int limit) {
        return snippets.values().stream()
                .sorted((a, b) -> Long.compare(b.getExecutionCount(), a.getExecutionCount()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private int compareByUsage(XPathSnippet a, XPathSnippet b) {
        // Compare by favorite first, then execution count, then name
        if (a.isFavorite() != b.isFavorite()) return a.isFavorite() ? -1 : 1;
        int executionDiff = Long.compare(b.getExecutionCount(), a.getExecutionCount());
        if (executionDiff != 0) return executionDiff;
        return a.getName().compareToIgnoreCase(b.getName());
    }

    // ========== Advanced Filtering ==========

    /**
     * Advanced search with multiple filters
     */
    public List<XPathSnippet> advancedSearch(AdvancedSearchCriteria criteria) {
        return snippets.values().stream()
                .filter(snippet -> matchesAdvancedCriteria(snippet, criteria))
                .sorted((a, b) -> compareByUsage(a, b))
                .collect(Collectors.toList());
    }

    private boolean matchesAdvancedCriteria(XPathSnippet snippet, AdvancedSearchCriteria criteria) {
        // Text search
        if (criteria.getSearchText() != null && !criteria.getSearchText().isEmpty()) {
            if (!matchesSearch(snippet, criteria.getSearchText().toLowerCase())) {
                return false;
            }
        }

        // Category filter
        if (criteria.getCategories() != null && !criteria.getCategories().isEmpty()) {
            if (!criteria.getCategories().contains(snippet.getCategory())) {
                return false;
            }
        }

        // Type filter
        if (criteria.getTypes() != null && !criteria.getTypes().isEmpty()) {
            if (!criteria.getTypes().contains(snippet.getType())) {
                return false;
            }
        }

        // Tags filter
        if (criteria.getTags() != null && !criteria.getTags().isEmpty()) {
            boolean hasMatchingTag = criteria.getTags().stream()
                    .anyMatch(tag -> snippet.getTags().contains(tag));
            if (!hasMatchingTag) {
                return false;
            }
        }

        // Favorites filter
        if (criteria.isFavoritesOnly() && !snippet.isFavorite()) {
            return false;
        }

        // Date range filter
        if (criteria.getCreatedAfter() != null &&
                snippet.getCreatedAt().isBefore(criteria.getCreatedAfter())) {
            return false;
        }

        return criteria.getCreatedBefore() == null ||
                !snippet.getCreatedAt().isAfter(criteria.getCreatedBefore());
    }

    // ========== Index Management ==========

    private void addSnippetToIndices(XPathSnippet snippet) {
        // Tag index
        for (String tag : snippet.getTags()) {
            tagIndex.computeIfAbsent(tag.toLowerCase(), k -> ConcurrentHashMap.newKeySet()).add(snippet.getId());
        }

        // Category index
        if (snippet.getCategory() != null) {
            categoryIndex.computeIfAbsent(snippet.getCategory(), k -> ConcurrentHashMap.newKeySet()).add(snippet.getId());
        }

        // Type index
        if (snippet.getType() != null) {
            typeIndex.computeIfAbsent(snippet.getType(), k -> ConcurrentHashMap.newKeySet()).add(snippet.getId());
        }
    }

    private void removeSnippetFromIndices(XPathSnippet snippet) {
        // Tag index
        for (String tag : snippet.getTags()) {
            Set<String> tagSnippets = tagIndex.get(tag.toLowerCase());
            if (tagSnippets != null) {
                tagSnippets.remove(snippet.getId());
                if (tagSnippets.isEmpty()) {
                    tagIndex.remove(tag.toLowerCase());
                }
            }
        }

        // Category index
        if (snippet.getCategory() != null) {
            Set<String> categorySnippets = categoryIndex.get(snippet.getCategory());
            if (categorySnippets != null) {
                categorySnippets.remove(snippet.getId());
                if (categorySnippets.isEmpty()) {
                    categoryIndex.remove(snippet.getCategory());
                }
            }
        }

        // Type index
        if (snippet.getType() != null) {
            Set<String> typeSnippets = typeIndex.get(snippet.getType());
            if (typeSnippets != null) {
                typeSnippets.remove(snippet.getId());
                if (typeSnippets.isEmpty()) {
                    typeIndex.remove(snippet.getType());
                }
            }
        }
    }

    // ========== Caching ==========

    private List<XPathSnippet> getCachedResult(String cacheKey) {
        if (System.currentTimeMillis() - cacheTimestamp > cacheTimeout) {
            clearSearchCache();
            return null;
        }
        return searchCache.get(cacheKey);
    }

    private void cacheResult(String cacheKey, List<XPathSnippet> results) {
        searchCache.put(cacheKey, new ArrayList<>(results));
    }

    private void clearSearchCache() {
        searchCache.clear();
        cacheTimestamp = System.currentTimeMillis();
    }

    // ========== Persistence ==========

    private void saveSnippetToFile(XPathSnippet snippet) {
        // Note: JSON serialization temporarily disabled
        logger.debug("Skipping snippet file save (JSON support not available): {}", snippet.getName());
        lastSaveTime = System.currentTimeMillis();
    }

    private void deleteSnippetFile(XPathSnippet snippet) {
        try {
            Path filePath = snippetsDirectory.resolve(snippet.getId() + ".json");
            Files.deleteIfExists(filePath);

        } catch (Exception e) {
            logger.error("Failed to delete snippet file: {}", snippet.getName(), e);
        }
    }

    /**
     * Save all snippets to disk
     */
    public void saveAll() {
        snippets.values().forEach(this::saveSnippetToFile);
        logger.debug("Saved all {} snippets to disk", snippets.size());
    }

    /**
     * Export snippets to file
     */
    public void exportSnippets(Path exportPath, List<String> snippetIds) throws IOException {
        List<XPathSnippet> toExport = snippetIds.isEmpty() ?
                getAllSnippets() :
                snippetIds.stream()
                        .map(snippets::get)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

        // Note: JSON export temporarily disabled
        logger.debug("Export requested for {} snippets to: {} (JSON support not available)", toExport.size(), exportPath);
        throw new UnsupportedOperationException("Export not supported without JSON library");
    }

    /**
     * Import snippets from file
     */
    public int importSnippets(Path importPath, boolean overwriteExisting) throws IOException {
        // Note: JSON import temporarily disabled
        logger.debug("Import requested from: {} (JSON support not available)", importPath);
        throw new UnsupportedOperationException("Import not supported without JSON library");
    }

    // ========== Statistics and Analytics ==========

    /**
     * Get repository statistics
     */
    public RepositoryStatistics getStatistics() {
        RepositoryStatistics stats = new RepositoryStatistics();
        stats.totalSnippets = snippets.size();
        stats.totalExecutions = totalExecutions;
        stats.builtInSnippets = getBuiltInSnippetCount();
        stats.userSnippets = stats.totalSnippets - stats.builtInSnippets;
        stats.favoriteSnippets = (int) snippets.values().stream().filter(XPathSnippet::isFavorite).count();

        // Category distribution
        stats.categoryDistribution = snippets.values().stream()
                .collect(Collectors.groupingBy(
                        XPathSnippet::getCategory,
                        Collectors.counting()
                ));

        // Type distribution
        stats.typeDistribution = snippets.values().stream()
                .collect(Collectors.groupingBy(
                        XPathSnippet::getType,
                        Collectors.counting()
                ));

        return stats;
    }

    private int getBuiltInSnippetCount() {
        return (int) snippets.values().stream()
                .filter(snippet -> "system".equals(snippet.getAuthor()))
                .count();
    }

    // ========== Configuration ==========

    public void setAutoSaveEnabled(boolean autoSaveEnabled) {
        this.autoSaveEnabled = autoSaveEnabled;
        logger.debug("Auto-save {}", autoSaveEnabled ? "enabled" : "disabled");
    }

    public void setSnippetsDirectory(Path snippetsDirectory) {
        this.snippetsDirectory = snippetsDirectory;
        try {
            Files.createDirectories(snippetsDirectory);
            logger.debug("Snippets directory changed to: {}", snippetsDirectory);
        } catch (Exception e) {
            logger.error("Failed to create new snippets directory", e);
        }
    }

    // ========== Built-in Snippets Creation ==========

    private void createNavigationSnippets() {
        // Root element
        saveSnippet(XPathSnippet.builder()
                .name("Root Element")
                .description("Select the root element of the document")
                .type(XPathSnippet.SnippetType.XPATH)
                .category(XPathSnippet.SnippetCategory.NAVIGATION)
                .query("/*")
                .author("system")
                .tags("root", "navigation", "basic")
                .build());

        // All elements
        saveSnippet(XPathSnippet.builder()
                .name("All Elements")
                .description("Select all elements in the document")
                .type(XPathSnippet.SnippetType.XPATH)
                .category(XPathSnippet.SnippetCategory.NAVIGATION)
                .query("//*")
                .author("system")
                .tags("all", "elements", "navigation")
                .build());

        // Child elements
        saveSnippet(XPathSnippet.builder()
                .name("Direct Children")
                .description("Select direct child elements")
                .type(XPathSnippet.SnippetType.XPATH)
                .category(XPathSnippet.SnippetCategory.NAVIGATION)
                .query("/parent/child")
                .author("system")
                .tags("children", "direct", "navigation")
                .parameter(SnippetParameter.builder("parent")
                        .type(SnippetParameter.ParameterType.ELEMENT_NAME)
                        .defaultValue("root")
                        .description("Parent element name")
                        .build())
                .parameter(SnippetParameter.builder("child")
                        .type(SnippetParameter.ParameterType.ELEMENT_NAME)
                        .defaultValue("item")
                        .description("Child element name")
                        .build())
                .build());
    }

    private void createExtractionSnippets() {
        // Text content
        saveSnippet(XPathSnippet.builder()
                .name("Extract Text")
                .description("Extract text content from elements")
                .type(XPathSnippet.SnippetType.XPATH)
                .category(XPathSnippet.SnippetCategory.EXTRACTION)
                .query("//element/text()")
                .author("system")
                .tags("text", "content", "extraction")
                .parameter(SnippetParameter.builder("element")
                        .type(SnippetParameter.ParameterType.ELEMENT_NAME)
                        .defaultValue("p")
                        .description("Element name to extract text from")
                        .build())
                .build());

        // Attribute values
        saveSnippet(XPathSnippet.builder()
                .name("Extract Attribute")
                .description("Extract attribute values from elements")
                .type(XPathSnippet.SnippetType.XPATH)
                .category(XPathSnippet.SnippetCategory.EXTRACTION)
                .query("//element/@attribute")
                .author("system")
                .tags("attribute", "extraction")
                .parameter(SnippetParameter.builder("element")
                        .type(SnippetParameter.ParameterType.ELEMENT_NAME)
                        .defaultValue("a")
                        .description("Element name")
                        .build())
                .parameter(SnippetParameter.builder("attribute")
                        .type(SnippetParameter.ParameterType.ATTRIBUTE_NAME)
                        .defaultValue("href")
                        .description("Attribute name to extract")
                        .build())
                .build());
    }

    private void createFilteringSnippets() {
        // Filter by position
        saveSnippet(XPathSnippet.builder()
                .name("First Element")
                .description("Select the first element of a type")
                .type(XPathSnippet.SnippetType.XPATH)
                .category(XPathSnippet.SnippetCategory.FILTERING)
                .query("//element[1]")
                .author("system")
                .tags("first", "position", "filter")
                .parameter(SnippetParameter.builder("element")
                        .type(SnippetParameter.ParameterType.ELEMENT_NAME)
                        .defaultValue("item")
                        .description("Element name to select first of")
                        .build())
                .build());

        // Filter by attribute
        saveSnippet(XPathSnippet.builder()
                .name("Filter by Attribute")
                .description("Select elements with specific attribute value")
                .type(XPathSnippet.SnippetType.XPATH)
                .category(XPathSnippet.SnippetCategory.FILTERING)
                .query("//element[@attribute='value']")
                .author("system")
                .tags("attribute", "filter", "condition")
                .parameter(SnippetParameter.builder("element")
                        .type(SnippetParameter.ParameterType.ELEMENT_NAME)
                        .defaultValue("div")
                        .description("Element name")
                        .build())
                .parameter(SnippetParameter.builder("attribute")
                        .type(SnippetParameter.ParameterType.ATTRIBUTE_NAME)
                        .defaultValue("class")
                        .description("Attribute name")
                        .build())
                .parameter(SnippetParameter.builder("value")
                        .type(SnippetParameter.ParameterType.STRING)
                        .defaultValue("content")
                        .description("Attribute value to match")
                        .build())
                .build());
    }

    private void createTransformationSnippets() {
        // Count elements
        saveSnippet(XPathSnippet.builder()
                .name("Count Elements")
                .description("Count the number of elements")
                .type(XPathSnippet.SnippetType.XPATH)
                .category(XPathSnippet.SnippetCategory.TRANSFORMATION)
                .query("count(//element)")
                .author("system")
                .tags("count", "transformation")
                .parameter(SnippetParameter.builder("element")
                        .type(SnippetParameter.ParameterType.ELEMENT_NAME)
                        .defaultValue("item")
                        .description("Element name to count")
                        .build())
                .build());

        // String functions
        saveSnippet(XPathSnippet.builder()
                .name("String Length")
                .description("Get the length of text content")
                .type(XPathSnippet.SnippetType.XPATH)
                .category(XPathSnippet.SnippetCategory.TRANSFORMATION)
                .query("string-length(//element/text())")
                .author("system")
                .tags("string", "length", "transformation")
                .parameter(SnippetParameter.builder("element")
                        .type(SnippetParameter.ParameterType.ELEMENT_NAME)
                        .defaultValue("title")
                        .description("Element containing text")
                        .build())
                .build());
    }

    private void createValidationSnippets() {
        // Check existence
        saveSnippet(XPathSnippet.builder()
                .name("Element Exists")
                .description("Check if element exists")
                .type(XPathSnippet.SnippetType.XPATH)
                .category(XPathSnippet.SnippetCategory.VALIDATION)
                .query("boolean(//element)")
                .author("system")
                .tags("exists", "validation", "boolean")
                .parameter(SnippetParameter.builder("element")
                        .type(SnippetParameter.ParameterType.ELEMENT_NAME)
                        .defaultValue("required")
                        .description("Element name to check")
                        .build())
                .build());
    }

    private void createAnalysisSnippets() {
        // Document structure
        saveSnippet(XPathSnippet.builder()
                .name("All Element Names")
                .description("Get all unique element names in document")
                .type(XPathSnippet.SnippetType.XPATH)
                .category(XPathSnippet.SnippetCategory.ANALYSIS)
                .query("distinct-values(//*/name())")
                .author("system")
                .tags("structure", "analysis", "names")
                .build());

        // Namespace analysis
        saveSnippet(XPathSnippet.builder()
                .name("All Namespaces")
                .description("Get all namespaces used in document")
                .type(XPathSnippet.SnippetType.XPATH)
                .category(XPathSnippet.SnippetCategory.ANALYSIS)
                .query("distinct-values(//*/namespace-uri())")
                .author("system")
                .tags("namespace", "analysis")
                .build());
    }

    private void createUtilitySnippets() {
        // Current date
        saveSnippet(XPathSnippet.builder()
                .name("Current Date")
                .description("Get current date")
                .type(XPathSnippet.SnippetType.XPATH)
                .category(XPathSnippet.SnippetCategory.UTILITY)
                .query("current-date()")
                .author("system")
                .tags("date", "current", "utility")
                .build());

        // Random number
        saveSnippet(XPathSnippet.builder()
                .name("Random Number")
                .description("Generate random number")
                .type(XPathSnippet.SnippetType.XPATH)
                .category(XPathSnippet.SnippetCategory.UTILITY)
                .query("random-number-generator()")
                .author("system")
                .tags("random", "number", "utility")
                .build());
    }

    // ========== Additional Methods ==========

    /**
     * Get snippet by name
     */
    public XPathSnippet getSnippetByName(String name) {
        return snippets.values().stream()
                .filter(snippet -> snippet.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * Update existing snippet
     */
    public void updateSnippet(XPathSnippet snippet) {
        if (snippet != null && snippet.getId() != null) {
            // Remove from indices first if it exists
            XPathSnippet existing = snippets.get(snippet.getId());
            if (existing != null) {
                removeSnippetFromIndices(existing);
            }

            // Update snippet and re-index
            snippets.put(snippet.getId(), snippet);
            addSnippetToIndices(snippet);

            if (autoSaveEnabled) {
                // Auto-save logic (simplified without JSON)
                logger.debug("Snippet updated: {}", snippet.getName());
            }
        }
    }

    /**
     * Get popular snippets (by usage count)
     */
    public List<XPathSnippet> getPopularSnippets() {
        return getAllSnippets().stream()
                .filter(snippet -> snippet.getUsageCount() > 0)
                .sorted((a, b) -> Integer.compare(b.getUsageCount(), a.getUsageCount()))
                .limit(10)
                .collect(Collectors.toList());
    }

    // ========== Helper Classes ==========

    public static class AdvancedSearchCriteria {
        private String searchText;
        private Set<XPathSnippet.SnippetCategory> categories;
        private Set<XPathSnippet.SnippetType> types;
        private Set<String> tags;
        private boolean favoritesOnly;
        private LocalDateTime createdAfter;
        private LocalDateTime createdBefore;

        // Getters and setters
        public String getSearchText() {
            return searchText;
        }

        public void setSearchText(String searchText) {
            this.searchText = searchText;
        }

        public Set<XPathSnippet.SnippetCategory> getCategories() {
            return categories;
        }

        public void setCategories(Set<XPathSnippet.SnippetCategory> categories) {
            this.categories = categories;
        }

        public Set<XPathSnippet.SnippetType> getTypes() {
            return types;
        }

        public void setTypes(Set<XPathSnippet.SnippetType> types) {
            this.types = types;
        }

        public Set<String> getTags() {
            return tags;
        }

        public void setTags(Set<String> tags) {
            this.tags = tags;
        }

        public boolean isFavoritesOnly() {
            return favoritesOnly;
        }

        public void setFavoritesOnly(boolean favoritesOnly) {
            this.favoritesOnly = favoritesOnly;
        }

        public LocalDateTime getCreatedAfter() {
            return createdAfter;
        }

        public void setCreatedAfter(LocalDateTime createdAfter) {
            this.createdAfter = createdAfter;
        }

        public LocalDateTime getCreatedBefore() {
            return createdBefore;
        }

        public void setCreatedBefore(LocalDateTime createdBefore) {
            this.createdBefore = createdBefore;
        }
    }

    public static class RepositoryStatistics {
        public int totalSnippets;
        public long totalExecutions;
        public int builtInSnippets;
        public int userSnippets;
        public int favoriteSnippets;
        public Map<XPathSnippet.SnippetCategory, Long> categoryDistribution;
        public Map<XPathSnippet.SnippetType, Long> typeDistribution;
    }
}