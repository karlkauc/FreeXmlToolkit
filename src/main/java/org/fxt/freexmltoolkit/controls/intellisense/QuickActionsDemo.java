package org.fxt.freexmltoolkit.controls.intellisense;

/**
 * Demo showcasing the Quick Actions Menu system.
 */
public class QuickActionsDemo {

    public static void main(String[] args) {
        System.out.println("🚀 Quick Actions Menu Demo");
        System.out.println("========================");
        System.out.println();

        try {
            // Demo 1: Action Creation and Registration
            demoActionCreation();
            System.out.println();

            // Demo 2: Context Analysis
            demoContextAnalysis();
            System.out.println();

            // Demo 3: Action Filtering and Execution
            demoActionExecution();
            System.out.println();

            // Demo 4: Built-in Actions Overview
            demoBuiltInActions();
            System.out.println();

            // Demo 5: Integration Points
            demoIntegrationPoints();
            System.out.println();

        } catch (Exception e) {
            System.err.println("Demo error: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("✅ Quick Actions demo completed!");
    }

    private static void demoActionCreation() {
        System.out.println("1️⃣  Action Creation & Registration");
        System.out.println("─────────────────────────────────");

        // Create custom action
        QuickAction customAction = new QuickAction.Builder(
                "custom-wrap", "Wrap with CDATA",
                context -> {
                    String wrapped = "<![CDATA[" + context.selectedText + "]]>";
                    String result = context.fullText.substring(0, context.selectionStart) +
                            wrapped +
                            context.fullText.substring(context.selectionEnd);
                    return QuickAction.ActionResult.success(result, context.selectionStart + wrapped.length());
                }
        )
                .description("Wrap selected text with CDATA section")
                .type(QuickAction.QuickActionType.XML_FORMATTING)
                .icon("cdata")
                .priority(120)
                .requiresSelection(true)
                .build();

        System.out.println("  ✓ Created custom action: " + customAction.getName());
        System.out.println("    Type: " + customAction.getType().getDisplayName());
        System.out.println("    Shortcut: " + (customAction.getShortcut() != null ? customAction.getShortcut().getDisplayText() : "None"));
        System.out.println("    Requires Selection: " + customAction.requiresSelection());
        System.out.println("    Priority: " + customAction.getPriority());

        // Note: QuickActionsMenu requires JavaFX, so we'll simulate registration
        System.out.println("  ✓ Action ready for registration with menu system");
    }

    private static void demoContextAnalysis() {
        System.out.println("2️⃣  Context Analysis");
        System.out.println("──────────────────");

        // Sample XML content for analysis
        String xmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <root xmlns:ns="http://example.com/namespace">
                    <element attribute="value">content</element>
                    <ns:namespaced>data</ns:namespaced>
                </root>
                """;

        // Simulate different cursor positions
        int[] cursorPositions = {10, 50, 120, 180}; // Different positions in the XML
        String[] positionNames = {"XML Declaration", "Root Element", "Inside Element", "Namespaced Element"};

        for (int i = 0; i < cursorPositions.length; i++) {
            System.out.printf("  Context at %s (pos %d):%n", positionNames[i], cursorPositions[i]);

            QuickAction.ActionContext context = new QuickAction.ActionContext(
                    xmlContent, "", cursorPositions[i]
            );

            // Simulate context analysis
            context.hasXmlContent = true;
            context.hasNamespaces = xmlContent.contains("xmlns");
            context.hasXsdSchema = true; // Simulated

            // Simulate cursor context detection
            if (cursorPositions[i] > 80 && cursorPositions[i] < 150) {
                context.cursorInElement = true;
                context.currentElement = "element";
            }

            System.out.println("    Has XML Content: " + context.hasXmlContent);
            System.out.println("    Has Namespaces: " + context.hasNamespaces);
            System.out.println("    Has XSD Schema: " + context.hasXsdSchema);
            System.out.println("    In Element: " + context.cursorInElement);
            if (context.currentElement != null) {
                System.out.println("    Current Element: " + context.currentElement);
            }
            System.out.println();
        }
    }

    private static void demoActionExecution() {
        System.out.println("3️⃣  Action Filtering & Execution");
        System.out.println("───────────────────────────────");

        // Create sample actions
        QuickAction formatAction = new QuickAction.Builder(
                "format", "Format XML",
                context -> QuickAction.ActionResult.success("Formatted XML content", 0)
        )
                .type(QuickAction.QuickActionType.XML_FORMATTING)
                .priority(150)
                .build();

        QuickAction wrapAction = new QuickAction.Builder(
                "wrap", "Wrap Selection",
                context -> QuickAction.ActionResult.success("Wrapped content", 0)
        )
                .type(QuickAction.QuickActionType.ELEMENT_OPERATIONS)
                .requiresSelection(true)
                .priority(130)
                .build();

        QuickAction xpathAction = new QuickAction.Builder(
                "xpath", "Evaluate XPath",
                context -> QuickAction.ActionResult.success("XPath result: 5 nodes found")
        )
                .type(QuickAction.QuickActionType.XPATH_QUERY)
                .priority(120)
                .build();

        // Test action applicability
        QuickAction.ActionContext xmlContext = new QuickAction.ActionContext(
                "<root>content</root>", "content", 10
        );
        xmlContext.hasXmlContent = true;
        xmlContext.selectedText = "content";

        System.out.println("  Testing action applicability:");
        testActionApplicability(formatAction, xmlContext);
        testActionApplicability(wrapAction, xmlContext);
        testActionApplicability(xpathAction, xmlContext);

        System.out.println();
        System.out.println("  Executing applicable actions:");

        if (formatAction.isApplicable(xmlContext)) {
            QuickAction.ActionResult result = formatAction.execute(xmlContext);
            System.out.printf("    %s: %s%n", formatAction.getName(), result.getMessage());
        }

        if (wrapAction.isApplicable(xmlContext)) {
            QuickAction.ActionResult result = wrapAction.execute(xmlContext);
            System.out.printf("    %s: %s%n", wrapAction.getName(), result.getMessage());
        }

        if (xpathAction.isApplicable(xmlContext)) {
            QuickAction.ActionResult result = xpathAction.execute(xmlContext);
            System.out.printf("    %s: %s%n", xpathAction.getName(), result.getMessage());
        }
    }

    private static void testActionApplicability(QuickAction action, QuickAction.ActionContext context) {
        boolean applicable = action.isApplicable(context);
        System.out.printf("    %s (%s): %s%n",
                action.getName(),
                action.getType().getDisplayName(),
                applicable ? "✓ Applicable" : "✗ Not Applicable"
        );
    }

    private static void demoBuiltInActions() {
        System.out.println("4️⃣  Built-in Actions Overview");
        System.out.println("────────────────────────────");

        // Note: QuickActionsMenu creation would happen in JavaFX context
        // The menu automatically loads built-in actions
        System.out.println("  Built-in action categories:");

        for (QuickAction.QuickActionType type : QuickAction.QuickActionType.values()) {
            System.out.printf("    📂 %s%n", type.getDisplayName());

            // Show example actions for each category
            switch (type) {
                case XML_FORMATTING -> System.out.println("      • Format XML (Ctrl+Shift+F)");
                case ELEMENT_OPERATIONS -> System.out.println("      • Wrap with Element (Ctrl+E)");
                case XPATH_QUERY -> System.out.println("      • Evaluate XPath (Ctrl+X)");
                case SCHEMA_VALIDATION -> System.out.println("      • Validate against Schema (F9)");
                case TRANSFORMATION -> System.out.println("      • Apply XSLT Transform");
                case NAMESPACE_OPERATIONS -> System.out.println("      • Add Namespace Declaration");
                case ATTRIBUTE_OPERATIONS -> System.out.println("      • Sort Attributes");
                case GENERAL -> System.out.println("      • Escape XML Characters");
                default -> System.out.println("      • Other actions");
            }
        }

        System.out.println();
        System.out.println("  Key Features:");
        System.out.println("    • Context-sensitive action filtering");
        System.out.println("    • Keyboard shortcuts for fast access");
        System.out.println("    • Search functionality within menu");
        System.out.println("    • Integration with existing XML tools");
        System.out.println("    • Extensible action system");
    }

    private static void demoIntegrationPoints() {
        System.out.println("5️⃣  Integration Points");
        System.out.println("────────────────────");

        System.out.println("  Keyboard Shortcuts:");
        System.out.println("    • Ctrl+Space: Open Quick Actions Menu");
        System.out.println("    • Ctrl+.: Alternative shortcut");
        System.out.println("    • Right-click: Context menu integration");
        System.out.println();

        System.out.println("  Editor Integration:");
        System.out.println("    • Automatic context detection from cursor position");
        System.out.println("    • Selection-aware actions");
        System.out.println("    • XSD schema integration for validation actions");
        System.out.println("    • Namespace-aware operations");
        System.out.println();

        System.out.println("  Menu Features:");
        System.out.println("    • Fuzzy search filtering");
        System.out.println("    • Category-based organization");
        System.out.println("    • Priority-based sorting");
        System.out.println("    • Keyboard navigation (Up/Down/Enter/Escape)");
        System.out.println("    • Mouse selection with double-click execution");
        System.out.println();

        System.out.println("  Action Results:");
        System.out.println("    • Text replacement with automatic cursor positioning");
        System.out.println("    • Status messages for non-modifying actions");
        System.out.println("    • Error handling with user feedback");
        System.out.println("    • Integration with existing tool dialogs");
    }
}