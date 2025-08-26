package org.fxt.freexmltoolkit.controls.intellisense;

import java.util.List;

/**
 * Demo showcasing multi-schema support for IntelliSense.
 */
public class MultiSchemaDemo {

    public static void main(String[] args) {
        System.out.println("🌐 Multi-Schema IntelliSense Demo");
        System.out.println("==================================");
        System.out.println();

        // Initialize components
        MultiSchemaManager schemaManager = new MultiSchemaManager();
        NamespaceResolver namespaceResolver = new NamespaceResolver();
        SchemaValidator validator = new SchemaValidator(schemaManager, namespaceResolver);

        try {
            // Demo 1: Basic Multi-Schema Setup
            demoBasicMultiSchema(schemaManager);
            System.out.println();

            // Demo 2: Namespace Resolution
            demoNamespaceResolution(namespaceResolver);
            System.out.println();

            // Demo 3: Multi-Schema Completion
            demoMultiSchemaCompletion(schemaManager, namespaceResolver);
            System.out.println();

            // Demo 4: Schema Validation
            demoSchemaValidation(validator);
            System.out.println();

            // Demo 5: Performance Metrics
            demoPerformanceMetrics(schemaManager, validator);

        } finally {
            // Cleanup
            schemaManager.shutdown();
        }

        System.out.println();
        System.out.println("✅ Multi-schema demo completed!");
    }

    private static void demoBasicMultiSchema(MultiSchemaManager schemaManager) {
        System.out.println("1️⃣  Basic Multi-Schema Setup");
        System.out.println("──────────────────────────");

        // Add mock schema listener
        schemaManager.addSchemaListener(new MultiSchemaManager.SchemaListener() {
            @Override
            public void onSchemaAdded(MultiSchemaManager.SchemaInfo schema) {
                System.out.printf("    ✓ Schema added: %s%n", schema);
            }

            @Override
            public void onPrimarySchemaChanged(MultiSchemaManager.SchemaInfo schema) {
                System.out.printf("    🎯 Primary schema set: %s%n", schema.schemaId);
            }
        });

        // Create mock schemas
        System.out.println("  Adding mock schemas...");

        try {
            // Schema 1: Customer schema
            String customerId = addMockCustomerSchema(schemaManager);
            System.out.printf("    Added customer schema: %s%n", customerId);

            // Schema 2: Order schema
            String orderId = addMockOrderSchema(schemaManager);
            System.out.printf("    Added order schema: %s%n", orderId);

            // Schema 3: Product schema
            String productId = addMockProductSchema(schemaManager);
            System.out.printf("    Added product schema: %s%n", productId);

            // Show registered schemas
            System.out.printf("  Total schemas registered: %d%n", schemaManager.getAllSchemas().size());
            System.out.printf("  Primary schema: %s%n",
                    schemaManager.getPrimarySchema() != null ? schemaManager.getPrimarySchema().schemaId : "none");

            // Show registered namespaces
            System.out.printf("  Registered namespaces: %s%n", schemaManager.getRegisteredNamespaces());

        } catch (Exception e) {
            System.err.printf("    ❌ Error in schema setup: %s%n", e.getMessage());
        }
    }

    private static void demoNamespaceResolution(NamespaceResolver resolver) {
        System.out.println("2️⃣  Namespace Resolution");
        System.out.println("───────────────────────");

        // Sample XML content with namespaces
        String xmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <root xmlns="http://example.com/default" 
                      xmlns:cust="http://example.com/customer"
                      xmlns:ord="http://example.com/order">
                    <cust:customer id="123">
                        <cust:name>John Doe</cust:name>
                        <cust:email>john@example.com</cust:email>
                    </cust:customer>
                    <ord:order id="456">
                        <ord:item>Widget</ord:item>
                        <ord:quantity>5</ord:quantity>
                    </ord:order>
                </root>
                """;

        System.out.println("  Parsing namespace declarations...");
        resolver.parseNamespaceDeclarations(xmlContent);

        System.out.printf("  Default namespace: %s%n", resolver.getDefaultNamespace());
        System.out.println("  Namespace mappings:");
        resolver.getAllNamespaces().forEach((prefix, namespace) ->
                System.out.printf("    %s -> %s%n", prefix, namespace));

        // Test element resolution
        System.out.println("  Resolving element names:");
        String[] elements = {"customer", "cust:customer", "ord:order", "name"};
        for (String element : elements) {
            NamespaceResolver.ResolvedName resolved = resolver.resolveElementName(element);
            System.out.printf("    '%s' -> %s%n", element, resolved);
        }

        // Test context namespace detection
        System.out.println("  Context namespace detection:");
        int[] positions = {200, 350, 500}; // Sample positions in XML
        for (int pos : positions) {
            String contextNs = resolver.getContextNamespace(xmlContent, pos);
            System.out.printf("    Position %d: %s%n", pos, contextNs);
        }

        // Get namespace suggestions
        System.out.println("  Namespace completion suggestions:");
        List<CompletionItem> nsSuggestions = resolver.getNamespaceSuggestions();
        nsSuggestions.forEach(item ->
                System.out.printf("    %s - %s%n", item.getLabel(), item.getDescription()));
    }

    private static void demoMultiSchemaCompletion(MultiSchemaManager schemaManager, NamespaceResolver resolver) {
        System.out.println("3️⃣  Multi-Schema Completion");
        System.out.println("─────────────────────────");

        // Test completion for different contexts
        String[] contexts = {"customer", "order", "product", "address"};
        String[] namespaces = {
                "http://example.com/customer",
                "http://example.com/order",
                "http://example.com/product",
                null // Default namespace
        };

        for (int i = 0; i < contexts.length; i++) {
            String context = contexts[i];
            String namespace = namespaces[i];

            System.out.printf("  Context: '%s', Namespace: %s%n", context, namespace);

            List<CompletionItem> items = schemaManager.getCompletionItems(context, namespace);
            System.out.printf("    Found %d completion items%n", items.size());

            // Show top 3 items
            items.stream().limit(3).forEach(item ->
                    System.out.printf("      %s %s - %s%n",
                            getTypeIcon(item.getType()),
                            item.getLabel(),
                            item.getDescription() != null ? item.getDescription() : "No description"));

            if (items.size() > 3) {
                System.out.printf("      ... and %d more items%n", items.size() - 3);
            }
            System.out.println();
        }

        // Test combined completion (no specific namespace)
        System.out.println("  Combined completion (all schemas):");
        List<CompletionItem> combinedItems = schemaManager.getCompletionItems("item", null);
        System.out.printf("    Found %d items from all schemas%n", combinedItems.size());
        combinedItems.stream().limit(5).forEach(item ->
                System.out.printf("      %s %s%n", getTypeIcon(item.getType()), item.getLabel()));
    }

    private static void demoSchemaValidation(SchemaValidator validator) {
        System.out.println("4️⃣  Schema Validation");
        System.out.println("───────────────────");

        // Test different XML samples
        String[] xmlSamples = {
                // Valid XML
                """
            <?xml version="1.0"?>
            <customer xmlns="http://example.com/customer">
                <name>John Doe</name>
                <email>john@example.com</email>
            </customer>
            """,

                // Invalid XML - unmatched tags
                """
            <?xml version="1.0"?>
            <customer>
                <name>John Doe
                <email>john@example.com</email>
            </customer>
            """,

                // Invalid XML - undefined namespace
                """
            <?xml version="1.0"?>
            <cust:customer>
                <cust:name>John Doe</cust:name>
            </cust:customer>
            """,

                // Empty content
                ""
        };

        String[] sampleNames = {"Valid XML", "Unmatched Tags", "Undefined Namespace", "Empty Content"};

        for (int i = 0; i < xmlSamples.length; i++) {
            System.out.printf("  Testing: %s%n", sampleNames[i]);

            SchemaValidator.ValidationResult result = validator.validateContent(xmlSamples[i]);
            System.out.printf("    Valid: %s%n", result.isValid());
            System.out.printf("    Message: %s%n", result.message());

            if (!result.issues().isEmpty()) {
                System.out.printf("    Issues (%d):%n", result.issues().size());
                result.issues().stream().limit(3).forEach(issue ->
                        System.out.printf("      %s%n", issue));

                if (result.issues().size() > 3) {
                    System.out.printf("      ... and %d more issues%n", result.issues().size() - 3);
                }
            }
            System.out.println();
        }
    }

    private static void demoPerformanceMetrics(MultiSchemaManager schemaManager, SchemaValidator validator) {
        System.out.println("5️⃣  Performance Metrics");
        System.out.println("──────────────────────");

        // Schema manager statistics
        CompletionCache.CacheStatistics schemaStats = schemaManager.getCacheStatistics();
        System.out.println("  Schema Manager Cache:");
        System.out.printf("    %s%n", schemaStats);

        // Validator statistics
        SchemaValidator.CacheStatistics validatorStats = validator.getCacheStatistics();
        System.out.println("  Validator Cache:");
        System.out.printf("    %s%n", validatorStats);

        // Performance profiler summary
        PerformanceProfiler.PerformanceSummary profilerSummary =
                PerformanceProfiler.getInstance().getSummary();

        System.out.println("  Performance Profiler:");
        System.out.printf("    %s%n", profilerSummary.toString().replace("\n", "\n    "));
    }

    // Helper methods to create mock schemas
    private static String addMockCustomerSchema(MultiSchemaManager schemaManager) {
        // In a real implementation, this would load an actual XSD file
        // For demo purposes, we'll simulate this
        System.out.println("    [Mock] Loading customer schema...");
        return "customer-schema";
    }

    private static String addMockOrderSchema(MultiSchemaManager schemaManager) {
        System.out.println("    [Mock] Loading order schema...");
        return "order-schema";
    }

    private static String addMockProductSchema(MultiSchemaManager schemaManager) {
        System.out.println("    [Mock] Loading product schema...");
        return "product-schema";
    }

    private static String getTypeIcon(CompletionItemType type) {
        return switch (type) {
            case ELEMENT -> "🏷️";
            case ATTRIBUTE -> "🔗";
            case TEXT -> "📝";
            case NAMESPACE -> "🌐";
            case SNIPPET -> "📋";
            default -> "📄";
        };
    }
}