package org.fxt.freexmltoolkit.controls.intellisense;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Template engine for managing and expanding code snippets.
 * Provides template management, expansion, and IntelliSense integration.
 */
public class TemplateEngine {

    private final Map<String, SnippetTemplate> templates = new ConcurrentHashMap<>();
    private final Map<SnippetTemplate.TemplateCategory, List<SnippetTemplate>> categorizedTemplates = new ConcurrentHashMap<>();

    // Template repositories
    private final List<TemplateRepository> repositories = new ArrayList<>();

    // Cache for frequently used templates
    private final CompletionCache completionCache;

    // Performance profiler
    private final PerformanceProfiler profiler = PerformanceProfiler.getInstance();

    public TemplateEngine() {
        this.completionCache = new CompletionCache();
        initializeBuiltInTemplates();
    }

    /**
     * Register a template
     */
    public void registerTemplate(SnippetTemplate template) {
        SnippetTemplate.ValidationResult validation = template.validate();
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid template: " + validation.message());
        }

        templates.put(template.getId(), template);

        // Update categorized cache
        categorizedTemplates.computeIfAbsent(template.getCategory(), k -> new ArrayList<>())
                .add(template);

        // Clear completion cache for affected categories
        completionCache.clearAll();
    }

    /**
     * Register multiple templates
     */
    public void registerTemplates(Collection<SnippetTemplate> templateList) {
        for (SnippetTemplate template : templateList) {
            registerTemplate(template);
        }
    }

    /**
     * Unregister a template
     */
    public boolean unregisterTemplate(String templateId) {
        SnippetTemplate removed = templates.remove(templateId);
        if (removed != null) {
            categorizedTemplates.get(removed.getCategory()).remove(removed);
            completionCache.clearAll();
            return true;
        }
        return false;
    }

    /**
     * Get template by ID
     */
    public Optional<SnippetTemplate> getTemplate(String templateId) {
        return Optional.ofNullable(templates.get(templateId));
    }

    /**
     * Get all templates
     */
    public Collection<SnippetTemplate> getAllTemplates() {
        return new ArrayList<>(templates.values());
    }

    /**
     * Get templates by category
     */
    public List<SnippetTemplate> getTemplatesByCategory(SnippetTemplate.TemplateCategory category) {
        return categorizedTemplates.getOrDefault(category, Collections.emptyList())
                .stream()
                .collect(Collectors.toList());
    }

    /**
     * Search templates by query
     */
    public List<SnippetTemplate> searchTemplates(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>(templates.values());
        }

        String normalizedQuery = query.toLowerCase().trim();

        return templates.values().stream()
                .filter(template -> matchesQuery(template, normalizedQuery))
                .sorted((a, b) -> {
                    int priorityComp = Integer.compare(b.getPriority(), a.getPriority());
                    if (priorityComp != 0) return priorityComp;

                    return a.getName().compareToIgnoreCase(b.getName());
                })
                .collect(Collectors.toList());
    }

    /**
     * Get context-aware template suggestions
     */
    public List<SnippetTemplate> getContextualSuggestions(SnippetTemplate.TemplateContext context) {
        return getContextualSuggestions(context, 10);
    }

    /**
     * Get context-aware template suggestions with limit
     */
    public List<SnippetTemplate> getContextualSuggestions(SnippetTemplate.TemplateContext context, int limit) {
        try (AutoCloseable timer = profiler.startOperation("contextual-suggestions")) {

            return templates.values().stream()
                    .filter(template -> template.matchesContext(context))
                    .sorted((a, b) -> {
                        // Context-sensitive templates get higher priority
                        boolean aContextSensitive = a.isContextSensitive();
                        boolean bContextSensitive = b.isContextSensitive();

                        if (aContextSensitive && !bContextSensitive) return -1;
                        if (!aContextSensitive && bContextSensitive) return 1;

                        // Then sort by priority
                        int priorityComp = Integer.compare(b.getPriority(), a.getPriority());
                        if (priorityComp != 0) return priorityComp;

                        return a.getName().compareToIgnoreCase(b.getName());
                    })
                    .limit(limit)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            // Fallback without profiler
            return templates.values().stream()
                    .filter(template -> template.matchesContext(context))
                    .sorted(Comparator.comparing(SnippetTemplate::getName))
                    .limit(limit)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Expand template with variables
     */
    public SnippetTemplate.TemplateExpansion expandTemplate(String templateId, Map<String, String> variables) {
        return expandTemplate(templateId, variables, new SnippetTemplate.ExpansionOptions());
    }

    /**
     * Expand template with variables and options
     */
    public SnippetTemplate.TemplateExpansion expandTemplate(String templateId, Map<String, String> variables,
                                                            SnippetTemplate.ExpansionOptions options) {
        SnippetTemplate template = templates.get(templateId);
        if (template == null) {
            throw new IllegalArgumentException("Template not found: " + templateId);
        }

        try (AutoCloseable timer = profiler.startOperation("template-expansion")) {
            return template.expand(variables, options);
        } catch (Exception e) {
            // Fallback without profiler
            return template.expand(variables, options);
        }
    }

    /**
     * Get completion items for templates
     */
    public List<CompletionItem> getTemplateCompletions(String query) {
        return getTemplateCompletions(query, null);
    }

    /**
     * Get completion items for templates with context
     */
    public List<CompletionItem> getTemplateCompletions(String query, SnippetTemplate.TemplateContext context) {
        String cacheKey = "completions:" + (query != null ? query : "") + ":" +
                (context != null ? context.hashCode() : "null");

        return completionCache.getCompletionItems(cacheKey, key -> {
            List<SnippetTemplate> relevantTemplates;

            if (query != null && !query.trim().isEmpty()) {
                relevantTemplates = searchTemplates(query);
            } else if (context != null) {
                relevantTemplates = getContextualSuggestions(context);
            } else {
                relevantTemplates = new ArrayList<>(templates.values());
            }

            return relevantTemplates.stream()
                    .map(SnippetTemplate::toCompletionItem)
                    .collect(Collectors.toList());
        });
    }

    /**
     * Add template repository
     */
    public void addRepository(TemplateRepository repository) {
        repositories.add(repository);
        loadFromRepository(repository);
    }

    /**
     * Load templates from repository
     */
    private void loadFromRepository(TemplateRepository repository) {
        try {
            Collection<SnippetTemplate> repoTemplates = repository.loadTemplates();
            registerTemplates(repoTemplates);
        } catch (Exception e) {
            System.err.println("Failed to load templates from repository: " + e.getMessage());
        }
    }

    /**
     * Reload all repositories
     */
    public void reloadRepositories() {
        templates.clear();
        categorizedTemplates.clear();
        completionCache.clearAll();

        initializeBuiltInTemplates();

        for (TemplateRepository repository : repositories) {
            loadFromRepository(repository);
        }
    }

    /**
     * Export templates to file
     */
    public void exportTemplates(Path filePath) throws IOException {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(filePath))) {
            writer.println("# XML Template Export");
            writer.println("# Generated at: " + java.time.LocalDateTime.now());
            writer.println();

            for (SnippetTemplate template : templates.values()) {
                writer.println("## Template: " + template.getName());
                writer.println("ID: " + template.getId());
                writer.println("Category: " + template.getCategory());
                writer.println("Description: " + template.getDescription());
                writer.println("Priority: " + template.getPriority());
                writer.println("Tags: " + String.join(", ", template.getTags()));
                writer.println();
                writer.println("Content:");
                writer.println("```xml");
                writer.println(template.getContent());
                writer.println("```");
                writer.println();
                writer.println("---");
                writer.println();
            }
        }
    }

    /**
     * Get engine statistics
     */
    public EngineStatistics getStatistics() {
        Map<SnippetTemplate.TemplateCategory, Integer> categoryCount = new HashMap<>();
        for (SnippetTemplate template : templates.values()) {
            categoryCount.merge(template.getCategory(), 1, Integer::sum);
        }

        return new EngineStatistics(
                templates.size(),
                categoryCount,
                repositories.size(),
                completionCache.getStatistics()
        );
    }

    /**
     * Check if query matches template
     */
    private boolean matchesQuery(SnippetTemplate template, String query) {
        String name = template.getName().toLowerCase();
        String description = template.getDescription().toLowerCase();
        String id = template.getId().toLowerCase();

        // Exact matches
        if (name.equals(query) || id.equals(query)) {
            return true;
        }

        // Prefix matches
        if (name.startsWith(query) || id.startsWith(query)) {
            return true;
        }

        // Contains matches
        if (name.contains(query) || description.contains(query)) {
            return true;
        }

        // Tag matches
        for (String tag : template.getTags()) {
            if (tag.toLowerCase().contains(query)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Initialize built-in templates
     */
    private void initializeBuiltInTemplates() {
        // XML Structure Templates
        registerTemplate(new SnippetTemplate.Builder(
                "xml-element", "XML Element",
                "<${element:name}>${content:}$0</${element:name}>"
        )
                .description("Basic XML element with content")
                .category(SnippetTemplate.TemplateCategory.XML_STRUCTURE)
                .tags("element", "basic")
                .defaultValue("element", "element")
                .priority(150)
                .build());

        registerTemplate(new SnippetTemplate.Builder(
                "xml-element-attr", "XML Element with Attributes",
                "<${element:name} ${attr:attribute}=\"${value:}\">${content:}$0</${element:name}>"
        )
                .description("XML element with attributes")
                .category(SnippetTemplate.TemplateCategory.XML_STRUCTURE)
                .tags("element", "attribute")
                .priority(140)
                .build());

        registerTemplate(new SnippetTemplate.Builder(
                "xml-self-closing", "Self-closing XML Element",
                "<${element:name} ${attr:}=\"${value:}\"/>$0"
        )
                .description("Self-closing XML element")
                .category(SnippetTemplate.TemplateCategory.XML_STRUCTURE)
                .tags("element", "self-closing")
                .priority(130)
                .build());

        // Namespace Templates
        registerTemplate(new SnippetTemplate.Builder(
                "xml-namespace", "XML with Namespace",
                "<${element:root} xmlns${prefix::ns}=\"${namespace:http://example.com}\">\n    ${content:}\n$0\n</${element:root}>"
        )
                .description("XML element with namespace declaration")
                .category(SnippetTemplate.TemplateCategory.XML_NAMESPACE)
                .tags("namespace", "xmlns")
                .priority(120)
                .contextSensitive(true)
                .build());

        // Schema Templates
        registerTemplate(new SnippetTemplate.Builder(
                "xsd-element", "XSD Element Definition",
                "<xs:element name=\"${name:elementName}\" type=\"${type:xs:string}\"/>"
        )
                .description("XSD element definition")
                .category(SnippetTemplate.TemplateCategory.XML_SCHEMA)
                .tags("xsd", "schema", "element")
                .priority(110)
                .contextSensitive(true)
                .build());

        registerTemplate(new SnippetTemplate.Builder(
                "xsd-complextype", "XSD Complex Type",
                "<xs:complexType name=\"${name:TypeName}\">\n    <xs:sequence>\n        <xs:element name=\"${element:element}\" type=\"${type:xs:string}\"/>\n        $0\n    </xs:sequence>\n</xs:complexType>"
        )
                .description("XSD complex type definition")
                .category(SnippetTemplate.TemplateCategory.XML_SCHEMA)
                .tags("xsd", "schema", "complextype")
                .priority(105)
                .contextSensitive(true)
                .build());

        // Web Services Templates
        registerTemplate(new SnippetTemplate.Builder(
                "soap-envelope", "SOAP Envelope",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n    <soap:Header>\n        ${header:}\n    </soap:Header>\n    <soap:Body>\n        ${body:}\n        $0\n    </soap:Body>\n</soap:Envelope>"
        )
                .description("SOAP envelope structure")
                .category(SnippetTemplate.TemplateCategory.WEB_SERVICES)
                .tags("soap", "webservice", "envelope")
                .priority(100)
                .build());

        // Documentation Templates
        registerTemplate(new SnippetTemplate.Builder(
                "xml-comment", "XML Comment",
                "<!-- ${comment:Comment text} -->$0"
        )
                .description("XML comment")
                .category(SnippetTemplate.TemplateCategory.DOCUMENTATION)
                .tags("comment", "documentation")
                .priority(90)
                .build());

        registerTemplate(new SnippetTemplate.Builder(
                "xml-cdata", "CDATA Section",
                "<![CDATA[${content:}]]>$0"
        )
                .description("XML CDATA section")
                .category(SnippetTemplate.TemplateCategory.XML_STRUCTURE)
                .tags("cdata", "text")
                .priority(85)
                .build());
    }

    /**
     * Template repository interface
     */
    public interface TemplateRepository {
        Collection<SnippetTemplate> loadTemplates() throws IOException;

        void saveTemplate(SnippetTemplate template) throws IOException;

        boolean deleteTemplate(String templateId) throws IOException;
    }

    /**
         * Engine statistics
         */
        public record EngineStatistics(int totalTemplates,
                                       Map<SnippetTemplate.TemplateCategory, Integer> templatesByCategory, int repositories,
                                       CompletionCache.CacheStatistics cacheStats) {
            public EngineStatistics(int totalTemplates,
                                    Map<SnippetTemplate.TemplateCategory, Integer> templatesByCategory,
                                    int repositories,
                                    CompletionCache.CacheStatistics cacheStats) {
                this.totalTemplates = totalTemplates;
                this.templatesByCategory = new HashMap<>(templatesByCategory);
                this.repositories = repositories;
                this.cacheStats = cacheStats;
            }

            @Override
            public String toString() {
                return String.format("EngineStatistics{templates=%d, categories=%d, repositories=%d, %s}",
                        totalTemplates, templatesByCategory.size(), repositories, cacheStats);
            }
        }
}