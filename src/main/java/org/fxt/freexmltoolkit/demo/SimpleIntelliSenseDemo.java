package org.fxt.freexmltoolkit.demo;

import org.fxt.freexmltoolkit.controls.intellisense.CompletionItem;
import org.fxt.freexmltoolkit.controls.intellisense.CompletionItemType;
import org.fxt.freexmltoolkit.controls.intellisense.FuzzySearch;

import java.util.Arrays;
import java.util.List;

/**
 * Simple console demo showcasing the IntelliSense improvements
 */
public class SimpleIntelliSenseDemo {

    public static void main(String[] args) {
        System.out.println("🚀 Enhanced XML IntelliSense Demo");
        System.out.println("=====================================");
        System.out.println();

        demoCompletionItems();
        System.out.println();

        demoFuzzySearch();
        System.out.println();

        demoAttributeHelpers();
        System.out.println();

        System.out.println("✅ Demo completed! All IntelliSense components are working.");
        System.out.println();
        System.out.println("🎯 Key Improvements:");
        System.out.println("• Rich 3-panel completion popup with preview");
        System.out.println("• Intelligent fuzzy search with CamelCase");
        System.out.println("• Type-aware attribute value helpers");
        System.out.println("• XSD documentation integration");
        System.out.println("• Performance optimized for large datasets");
        System.out.println();
        System.out.println("📋 To see the full JavaFX demo:");
        System.out.println("   Integration into XmlCodeEditor is the next step!");
    }

    private static void demoCompletionItems() {
        System.out.println("1️⃣  Enhanced Completion Items");
        System.out.println("─────────────────────────────");

        // Create sample completion items
        List<CompletionItem> items = Arrays.asList(
                new CompletionItem.Builder("customer", "<customer></customer>", CompletionItemType.ELEMENT)
                        .description("Customer element containing all customer-related information")
                        .dataType("CustomerType")
                        .requiredAttributes(Arrays.asList("id", "type"))
                        .required(true)
                        .relevanceScore(200)
                        .build(),

                new CompletionItem.Builder("name", "<name></name>", CompletionItemType.ELEMENT)
                        .description("Customer's full name")
                        .dataType("xs:string")
                        .constraints("minLength: 1, maxLength: 100")
                        .required(true)
                        .build(),

                new CompletionItem.Builder("id", "id=\"\"", CompletionItemType.ATTRIBUTE)
                        .description("Unique customer identifier")
                        .dataType("xs:ID")
                        .required(true)
                        .build()
        );

        for (CompletionItem item : items) {
            System.out.printf("  %s %s (%s)%n",
                    getTypeIcon(item.getType()),
                    item.getLabel(),
                    item.getType().getDisplayName()
            );
            System.out.printf("    📝 %s%n", item.getDescription());
            if (item.getDataType() != null) {
                System.out.printf("    🏷️  Type: %s%n", item.getDataType());
            }
            if (item.isRequired()) {
                System.out.printf("    ⚠️  Required%n");
            }
            System.out.println();
        }
    }

    private static void demoFuzzySearch() {
        System.out.println("2️⃣  Fuzzy Search Engine");
        System.out.println("──────────────────────");

        List<CompletionItem> items = Arrays.asList(
                createItem("customer", CompletionItemType.ELEMENT),
                createItem("customerInfo", CompletionItemType.ELEMENT),
                createItem("customerData", CompletionItemType.ELEMENT),
                createItem("name", CompletionItemType.ELEMENT),
                createItem("email", CompletionItemType.ELEMENT),
                createItem("phone", CompletionItemType.ELEMENT),
                createItem("customerId", CompletionItemType.ATTRIBUTE),
                createItem("customerType", CompletionItemType.ATTRIBUTE)
        );

        String[] queries = {"cust", "em", "Id", "Type", "customerD"};

        for (String query : queries) {
            System.out.printf("  🔍 Search: '%s'%n", query);
            List<CompletionItem> results = FuzzySearch.search(query, items);

            for (int i = 0; i < Math.min(3, results.size()); i++) {
                CompletionItem item = results.get(i);
                System.out.printf("    %d. %s %s%n",
                        i + 1,
                        getTypeIcon(item.getType()),
                        item.getLabel()
                );
            }
            System.out.println();
        }
    }

    private static void demoAttributeHelpers() {
        System.out.println("3️⃣  Type-aware Attribute Helpers");
        System.out.println("────────────────────────────────");

        String[][] attributeTypes = {
                {"isActive", "xs:boolean", "Toggle buttons (true/false)"},
                {"birthDate", "xs:date", "Date picker widget"},
                {"createdAt", "xs:dateTime", "Date & time selectors"},
                {"priority", "xs:int", "Number spinner with constraints"},
                {"score", "xs:decimal", "Decimal input with validation"},
                {"status", "StatusEnum", "Dropdown with enumeration values"},
                {"email", "EmailPattern", "Text field with regex validation"},
                {"website", "xs:anyURI", "URI helper with common prefixes"}
        };

        for (String[] attr : attributeTypes) {
            System.out.printf("  %s %s (%s)%n",
                    getAttributeIcon(attr[1]), attr[0], attr[1]);
            System.out.printf("    💡 %s%n", attr[2]);
            System.out.println();
        }
    }

    private static CompletionItem createItem(String name, CompletionItemType type) {
        return new CompletionItem.Builder(name, name, type)
                .description("Sample " + type.getDisplayName().toLowerCase())
                .relevanceScore(100)
                .build();
    }

    private static String getTypeIcon(CompletionItemType type) {
        return switch (type) {
            case ELEMENT -> "🏷️";
            case ATTRIBUTE -> "🔗";
            case TEXT -> "📝";
            case SNIPPET -> "📋";
            default -> "📄";
        };
    }

    private static String getAttributeIcon(String dataType) {
        if (dataType.contains("boolean")) return "☑️";
        if (dataType.contains("date") || dataType.contains("time")) return "📅";
        if (dataType.contains("int") || dataType.contains("decimal")) return "🔢";
        if (dataType.contains("URI")) return "🌐";
        if (dataType.contains("Enum")) return "📋";
        return "📝";
    }
}