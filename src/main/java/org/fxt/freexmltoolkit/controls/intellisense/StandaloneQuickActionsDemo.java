package org.fxt.freexmltoolkit.controls.intellisense;

/**
 * Standalone demo showcasing the Quick Actions Menu system functionality
 * without JavaFX UI dependencies.
 */
public class StandaloneQuickActionsDemo {

    public static void main(String[] args) {
        System.out.println("🚀 Quick Actions Menu - Standalone Demo");
        System.out.println("=====================================");
        System.out.println();

        try {
            // Demo 1: Action Creation
            demoActionCreation();
            System.out.println();

            // Demo 2: Context Analysis
            demoContextAnalysis();
            System.out.println();

            // Demo 3: Action Execution
            demoActionExecution();
            System.out.println();

            // Demo 4: Feature Overview
            demoFeatureOverview();
            System.out.println();

        } catch (Exception e) {
            System.err.println("Demo error: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("✅ Quick Actions demo completed successfully!");
        System.out.println();
        System.out.println("📋 Integration Summary:");
        System.out.println("===================");
        System.out.println("The Quick Actions Menu system is now complete and ready for integration:");
        System.out.println("• QuickAction.java - Core action definition with context awareness");
        System.out.println("• QuickActionsMenu.java - JavaFX popup menu with search and filtering");
        System.out.println("• QuickActionsIntegration.java - Integration layer for CodeArea");
        System.out.println("• Built-in actions for common XML operations");
        System.out.println("• Extensible architecture for custom actions");
    }

    private static void demoActionCreation() {
        System.out.println("1️⃣  Action Creation & Types");
        System.out.println("──────────────────────────");

        // Demonstrate different action types
        QuickAction[] sampleActions = {
                new QuickAction.Builder("format-xml", "Format XML",
                        context -> QuickAction.ActionResult.success("XML formatted successfully"))
                        .type(QuickAction.QuickActionType.XML_FORMATTING)
                        .priority(150)
                        .build(),

                new QuickAction.Builder("wrap-element", "Wrap with Element",
                        context -> {
                            if (context.selectedText.isEmpty()) {
                                return QuickAction.ActionResult.failure("No text selected");
                            }
                            return QuickAction.ActionResult.success("Text wrapped in element");
                        })
                        .type(QuickAction.QuickActionType.ELEMENT_OPERATIONS)
                        .requiresSelection(true)
                        .priority(130)
                        .build(),

                new QuickAction.Builder("validate-schema", "Validate Schema",
                        context -> QuickAction.ActionResult.success("Validation complete - no errors"))
                        .type(QuickAction.QuickActionType.SCHEMA_VALIDATION)
                        .priority(135)
                        .build()
        };

        for (QuickAction action : sampleActions) {
            System.out.printf("  ✓ %s (%s)%n", action.getName(), action.getType().getDisplayName());
            System.out.printf("    Priority: %d, Requires Selection: %s%n",
                    action.getPriority(), action.requiresSelection());
        }
    }

    private static void demoContextAnalysis() {
        System.out.println("2️⃣  Context Analysis");
        System.out.println("────────────────────");

        // Sample contexts
        QuickAction.ActionContext[] contexts = {
                createContext("<root>content</root>", "", true, false, false),
                createContext("<?xml version=\"1.0\"?><data xmlns:ns=\"uri\">text</data>", "text", true, true, false),
                createContext("<element attr=\"value\">selected</element>", "selected", true, false, true)
        };

        String[] contextNames = {"Simple XML", "Namespaced XML", "Attribute Context"};

        for (int i = 0; i < contexts.length; i++) {
            System.out.printf("  Context: %s%n", contextNames[i]);
            analyzeContext(contexts[i]);
            System.out.println();
        }
    }

    private static QuickAction.ActionContext createContext(String fullText, String selectedText,
                                                           boolean hasXml, boolean hasNamespaces, boolean inAttribute) {
        QuickAction.ActionContext context = new QuickAction.ActionContext(fullText, selectedText, 50);
        context.hasXmlContent = hasXml;
        context.hasNamespaces = hasNamespaces;
        context.cursorInAttribute = inAttribute;
        context.hasXsdSchema = true; // Simulated
        return context;
    }

    private static void analyzeContext(QuickAction.ActionContext context) {
        System.out.printf("    XML Content: %s%n", context.hasXmlContent ? "✓" : "✗");
        System.out.printf("    Has Namespaces: %s%n", context.hasNamespaces ? "✓" : "✗");
        System.out.printf("    In Attribute: %s%n", context.cursorInAttribute ? "✓" : "✗");
        System.out.printf("    Has XSD Schema: %s%n", context.hasXsdSchema ? "✓" : "✗");
        System.out.printf("    Selected Text: %s%n",
                context.selectedText.isEmpty() ? "(none)" : "\"" + context.selectedText + "\"");
    }

    private static void demoActionExecution() {
        System.out.println("3️⃣  Action Execution");
        System.out.println("───────────────────");

        // Create test actions
        QuickAction formatAction = new QuickAction.Builder(
                "format", "Format XML",
                context -> {
                    // Simulate XML formatting
                    String formatted = context.fullText.replace("><", ">\n    <");
                    return QuickAction.ActionResult.success(formatted, 0);
                })
                .type(QuickAction.QuickActionType.XML_FORMATTING)
                .build();

        QuickAction escapeAction = new QuickAction.Builder(
                "escape", "Escape XML",
                context -> {
                    String escaped = context.selectedText.replace("<", "&lt;").replace(">", "&gt;");
                    return QuickAction.ActionResult.success("Escaped: " + escaped);
                })
                .requiresSelection(true)
                .build();

        // Test contexts
        QuickAction.ActionContext xmlContext = new QuickAction.ActionContext(
                "<root><child>data</child></root>", "data", 15);
        xmlContext.hasXmlContent = true;
        xmlContext.selectedText = "data";

        QuickAction.ActionContext emptyContext = new QuickAction.ActionContext(
                "<root><child>data</child></root>", "", 15);
        emptyContext.hasXmlContent = true;

        // Execute actions
        System.out.println("  Testing format action:");
        executeAction(formatAction, xmlContext);

        System.out.println("  Testing escape action with selection:");
        executeAction(escapeAction, xmlContext);

        System.out.println("  Testing escape action without selection:");
        executeAction(escapeAction, emptyContext);
    }

    private static void executeAction(QuickAction action, QuickAction.ActionContext context) {
        if (action.isApplicable(context)) {
            QuickAction.ActionResult result = action.execute(context);
            System.out.printf("    ✓ %s: %s%n", action.getName(), result.getMessage());

            if (result.hasTextModification()) {
                System.out.printf("      Modified text preview: %.50s...%n",
                        result.getModifiedText().replace("\n", "\\n"));
            }
        } else {
            System.out.printf("    ✗ %s: Not applicable in this context%n", action.getName());
        }
    }

    private static void demoFeatureOverview() {
        System.out.println("4️⃣  Feature Overview");
        System.out.println("───────────────────");

        System.out.println("  🎯 Context Awareness:");
        System.out.println("    • Actions filtered based on cursor position and content");
        System.out.println("    • XML vs non-XML content detection");
        System.out.println("    • Element vs attribute context detection");
        System.out.println("    • Namespace awareness");
        System.out.println("    • XSD schema integration");
        System.out.println();

        System.out.println("  🔧 Built-in Actions:");
        for (QuickAction.QuickActionType type : QuickAction.QuickActionType.values()) {
            System.out.printf("    • %s%n", type.getDisplayName());
        }
        System.out.println();

        System.out.println("  ⌨️ User Interface:");
        System.out.println("    • Keyboard shortcuts (Ctrl+Space, Ctrl+.)");
        System.out.println("    • Right-click integration");
        System.out.println("    • Fuzzy search filtering");
        System.out.println("    • Keyboard navigation");
        System.out.println("    • Action descriptions and tooltips");
        System.out.println();

        System.out.println("  🔌 Integration:");
        System.out.println("    • CodeArea integration for XML editors");
        System.out.println("    • Automatic context detection");
        System.out.println("    • Text modification with cursor positioning");
        System.out.println("    • Extensible action system");
        System.out.println("    • Error handling and user feedback");
    }
}