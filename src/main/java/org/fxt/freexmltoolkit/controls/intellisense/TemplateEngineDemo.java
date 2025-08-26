package org.fxt.freexmltoolkit.controls.intellisense;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Demo showcasing the Template Engine for code snippets.
 */
public class TemplateEngineDemo {

    public static void main(String[] args) {
        System.out.println("📋 Template Engine & Snippets Demo");
        System.out.println("===================================");
        System.out.println();

        // Initialize template engine
        TemplateEngine templateEngine = new TemplateEngine();

        try {
            // Demo 1: Basic Template Registration
            demoBasicTemplateRegistration(templateEngine);
            System.out.println();

            // Demo 2: Template Expansion
            demoTemplateExpansion(templateEngine);
            System.out.println();

            // Demo 3: Context-Aware Suggestions
            demoContextAwareSuggestions(templateEngine);
            System.out.println();

            // Demo 4: Template Search
            demoTemplateSearch(templateEngine);
            System.out.println();

            // Demo 5: File Repository
            demoFileRepository(templateEngine);
            System.out.println();

            // Demo 6: Template Completion Items
            demoTemplateCompletions(templateEngine);
            System.out.println();

            // Show engine statistics
            showEngineStatistics(templateEngine);

        } catch (Exception e) {
            System.err.println("Demo error: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
        System.out.println("✅ Template engine demo completed!");
    }

    private static void demoBasicTemplateRegistration(TemplateEngine engine) {
        System.out.println("1️⃣  Basic Template Registration");
        System.out.println("────────────────────────────────");

        // Create and register custom templates
        SnippetTemplate customElement = new SnippetTemplate.Builder(
                "custom-element", "Custom Element",
                "<${elementName:customElement}>\n    <${childName:child}>${content:value}</${childName:child}>\n    $0\n</${elementName:customElement}>"
        )
                .description("A custom XML element with child elements")
                .category(SnippetTemplate.TemplateCategory.CUSTOM)
                .tags("custom", "element", "nested")
                .priority(160)
                .defaultValue("elementName", "customElement")
                .defaultValue("childName", "property")
                .contextSensitive(true)
                .author("Demo")
                .build();

        engine.registerTemplate(customElement);
        System.out.println("  ✓ Registered custom element template");

        // Register a configuration template
        SnippetTemplate configTemplate = new SnippetTemplate.Builder(
                "app-config", "Application Configuration",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<configuration>\n    <settings>\n        <setting name=\"${settingName:debugMode}\" value=\"${settingValue:true}\"/>\n        $0\n    </settings>\n    <database>\n        <connectionString>${connectionString:jdbc:mysql://localhost:3306/db}</connectionString>\n    </database>\n</configuration>"
        )
                .description("Application configuration file template")
                .category(SnippetTemplate.TemplateCategory.CONFIGURATION)
                .tags("config", "application", "settings")
                .priority(140)
                .requiredVariable("settingName")
                .build();

        engine.registerTemplate(configTemplate);
        System.out.println("  ✓ Registered application config template");

        // Show total registered templates
        System.out.printf("  Total registered templates: %d%n", engine.getAllTemplates().size());

        // Show templates by category
        for (SnippetTemplate.TemplateCategory category : SnippetTemplate.TemplateCategory.values()) {
            List<SnippetTemplate> templates = engine.getTemplatesByCategory(category);
            if (!templates.isEmpty()) {
                System.out.printf("    %s: %d templates%n", category, templates.size());
            }
        }
    }

    private static void demoTemplateExpansion(TemplateEngine engine) {
        System.out.println("2️⃣  Template Expansion");
        System.out.println("─────────────────────");

        // Expand built-in template
        System.out.println("  Expanding XML element template...");
        Map<String, String> variables = new HashMap<>();
        variables.put("element", "customer");
        variables.put("content", "John Doe");

        SnippetTemplate.TemplateExpansion expansion =
                engine.expandTemplate("xml-element", variables);

        System.out.println("  Expanded content:");
        System.out.println("    " + expansion.expandedContent().replace("\n", "\n    "));
        System.out.printf("  Cursor position: %d%n", expansion.cursorPosition());
        System.out.printf("  Tab stops: %d%n", expansion.tabStops().size());
        System.out.println("  Used variables: " + expansion.usedVariables());
        System.out.println();

        // Expand custom template with context
        System.out.println("  Expanding custom template with context...");
        SnippetTemplate.TemplateContext context = new SnippetTemplate.TemplateContext();
        context.currentElement = "product";
        context.currentNamespace = "http://example.com/catalog";
        context.fileName = "catalog.xml";

        SnippetTemplate.ExpansionOptions options = new SnippetTemplate.ExpansionOptions()
                .context(context)
                .globalDefault("elementName", "product");

        variables.clear();
        variables.put("childName", "name");
        variables.put("content", "Widget");

        expansion = engine.expandTemplate("custom-element", variables, options);

        System.out.println("  Context-aware expanded content:");
        System.out.println("    " + expansion.expandedContent().replace("\n", "\n    "));
        System.out.println();

        // Show system variable resolution
        System.out.println("  System variables demo:");
        SnippetTemplate systemVarTemplate = new SnippetTemplate.Builder(
                "system-vars", "System Variables",
                "<!-- Generated on ${date} at ${time} by ${user} -->\n<document id=\"${uuid}\">\n    $0\n</document>"
        ).build();

        engine.registerTemplate(systemVarTemplate);
        expansion = engine.expandTemplate("system-vars", Collections.emptyMap());

        System.out.println("    " + expansion.expandedContent().replace("\n", "\n    "));
    }

    private static void demoContextAwareSuggestions(TemplateEngine engine) {
        System.out.println("3️⃣  Context-Aware Suggestions");
        System.out.println("────────────────────────────");

        // Test different contexts
        SnippetTemplate.TemplateContext[] contexts = {
                new SnippetTemplate.TemplateContext("http://www.w3.org/2001/XMLSchema", "element"),
                new SnippetTemplate.TemplateContext("http://schemas.xmlsoap.org/soap/envelope/", "envelope"),
                new SnippetTemplate.TemplateContext(null, "configuration")
        };

        String[] contextNames = {"XSD Schema", "SOAP Envelope", "Configuration"};

        for (int i = 0; i < contexts.length; i++) {
            System.out.printf("  Context: %s%n", contextNames[i]);

            contexts[i].expectedCategory = getExpectedCategory(contextNames[i]);
            List<SnippetTemplate> suggestions = engine.getContextualSuggestions(contexts[i], 5);

            if (suggestions.isEmpty()) {
                System.out.println("    No context-specific suggestions");
            } else {
                for (SnippetTemplate template : suggestions) {
                    System.out.printf("    %s %s (%s) - %s%n",
                            template.isContextSensitive() ? "🎯" : "📄",
                            template.getName(),
                            template.getCategory(),
                            template.getDescription()
                    );
                }
            }
            System.out.println();
        }
    }

    private static void demoTemplateSearch(TemplateEngine engine) {
        System.out.println("4️⃣  Template Search");
        System.out.println("──────────────────");

        String[] queries = {"xml", "element", "soap", "config", "namespace"};

        for (String query : queries) {
            System.out.printf("  Search: '%s'%n", query);

            List<SnippetTemplate> results = engine.searchTemplates(query);

            if (results.isEmpty()) {
                System.out.println("    No results found");
            } else {
                results.stream().limit(3).forEach(template ->
                        System.out.printf("    📋 %s (Priority: %d) - %s%n",
                                template.getName(),
                                template.getPriority(),
                                template.getDescription())
                );

                if (results.size() > 3) {
                    System.out.printf("    ... and %d more results%n", results.size() - 3);
                }
            }
            System.out.println();
        }
    }

    private static void demoFileRepository(TemplateEngine engine) {
        System.out.println("5️⃣  File Repository");
        System.out.println("──────────────────");

        try {
            // Create temporary repository
            Path tempPath = Paths.get(System.getProperty("java.io.tmpdir"), "template-demo");
            FileTemplateRepository repository = FileTemplateRepository.createWithDefaults(tempPath);

            System.out.printf("  Created repository at: %s%n", repository.getRepositoryPath());

            // Add repository to engine
            engine.addRepository(repository);
            System.out.println("  ✓ Repository added to engine");

            // Show loaded templates
            System.out.println("  Templates loaded from repository:");
            for (SnippetTemplate template : engine.getAllTemplates()) {
                if (template.getAuthor().equals("Template Engine")) {
                    System.out.printf("    📁 %s - %s%n", template.getName(), template.getDescription());
                }
            }

            // Save a custom template to repository
            SnippetTemplate customRepo = new SnippetTemplate.Builder(
                    "repo-test", "Repository Test",
                    "<test>${content:Hello from repository!}</test>"
            )
                    .description("Test template from repository")
                    .category(SnippetTemplate.TemplateCategory.CUSTOM)
                    .tags("test", "repository")
                    .author("Demo User")
                    .build();

            repository.saveTemplate(customRepo);
            System.out.println("  ✓ Saved custom template to repository");

            // Reload to verify persistence
            engine.reloadRepositories();
            System.out.println("  ✓ Reloaded repositories");

            System.out.printf("  Total templates after reload: %d%n", engine.getAllTemplates().size());

        } catch (Exception e) {
            System.err.printf("  ❌ Repository demo error: %s%n", e.getMessage());
        }
    }

    private static void demoTemplateCompletions(TemplateEngine engine) {
        System.out.println("6️⃣  Template Completions");
        System.out.println("───────────────────────");

        // Get completion items for different queries
        String[] queries = {"xml", "soap", null}; // null for all templates
        String[] queryNames = {"XML templates", "SOAP templates", "All templates"};

        for (int i = 0; i < queries.length; i++) {
            System.out.printf("  %s:%n", queryNames[i]);

            List<CompletionItem> completions = engine.getTemplateCompletions(queries[i]);

            completions.stream().limit(5).forEach(item -> {
                System.out.printf("    📋 %s - %s (Score: %d)%n",
                        item.getLabel(),
                        item.getDescription(),
                        item.getRelevanceScore()
                );
            });

            if (completions.size() > 5) {
                System.out.printf("    ... and %d more completions%n", completions.size() - 5);
            }
            System.out.println();
        }

        // Context-aware completions
        System.out.println("  Context-aware completions (XSD context):");
        SnippetTemplate.TemplateContext xsdContext = new SnippetTemplate.TemplateContext();
        xsdContext.currentNamespace = "http://www.w3.org/2001/XMLSchema";
        xsdContext.expectedCategory = SnippetTemplate.TemplateCategory.XML_SCHEMA;

        List<CompletionItem> contextCompletions = engine.getTemplateCompletions(null, xsdContext);
        contextCompletions.stream().limit(3).forEach(item ->
                System.out.printf("    🎯 %s - %s%n", item.getLabel(), item.getDescription())
        );
    }

    private static void showEngineStatistics(TemplateEngine engine) {
        System.out.println("📊 Engine Statistics");
        System.out.println("═══════════════════");

        TemplateEngine.EngineStatistics stats = engine.getStatistics();

        System.out.printf("  Total Templates: %d%n", stats.totalTemplates());
        System.out.printf("  Repositories: %d%n", stats.repositories());
        System.out.println("  Templates by Category:");

        stats.templatesByCategory().forEach((category, count) ->
                System.out.printf("    %s: %d%n", category, count)
        );

        System.out.println("  Cache Statistics:");
        System.out.printf("    %s%n", stats.cacheStats());

        // Performance summary
        PerformanceProfiler.PerformanceSummary perfSummary =
                PerformanceProfiler.getInstance().getSummary();

        if (perfSummary.totalOperations > 0) {
            System.out.println("  Performance Summary:");
            System.out.printf("    Total Operations: %d%n", perfSummary.totalOperations);
            System.out.printf("    Average Duration: %.2f ms%n", perfSummary.getAverageDurationMs());
        }
    }

    private static SnippetTemplate.TemplateCategory getExpectedCategory(String contextName) {
        return switch (contextName) {
            case "XSD Schema" -> SnippetTemplate.TemplateCategory.XML_SCHEMA;
            case "SOAP Envelope" -> SnippetTemplate.TemplateCategory.WEB_SERVICES;
            case "Configuration" -> SnippetTemplate.TemplateCategory.CONFIGURATION;
            default -> SnippetTemplate.TemplateCategory.GENERAL;
        };
    }
}