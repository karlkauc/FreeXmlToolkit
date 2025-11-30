package org.fxt.freexmltoolkit.controls.v2.editor.tabs;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.views.SimpleTypeEditorView;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleType;

/**
 * Tab for editing a SimpleType.
 * Shows panels for General, Restriction, List, Union, and Annotation.
 *
 * Phase 3 Implementation - Real panels with model integration
 *
 * @since 2.0
 */
public class SimpleTypeEditorTab extends AbstractTypeEditorTab {

    private static final Logger logger = LogManager.getLogger(SimpleTypeEditorTab.class);

    private final XsdSimpleType simpleType;
    private final XsdSchema mainSchema;
    private final XsdEditorContext editorContext;
    private SimpleTypeEditorView editorView;

    /**
     * Creates a new SimpleType editor tab.
     *
     * @param simpleType the simple type to edit
     * @param mainSchema the main schema (for context)
     */
    public SimpleTypeEditorTab(XsdSimpleType simpleType, XsdSchema mainSchema) {
        super(simpleType, "SimpleType: " + simpleType.getName());
        this.simpleType = simpleType;
        this.mainSchema = mainSchema;
        this.editorContext = new XsdEditorContext(mainSchema);
        initializeContent(); // Call after field initialization
    }

    @Override
    protected void initializeContent() {
        // Create real editor view with editor context
        editorView = new SimpleTypeEditorView(simpleType, editorContext);
        setContent(editorView);

        // Setup change tracking
        editorView.setOnChangeCallback(() -> setDirty(true));

        // Setup action callbacks (Phase 6)
        editorView.setOnSaveCallback(this::save);
        editorView.setOnCloseCallback(() -> {
            // Request tab close (will trigger the close handler in TypeEditorTabManager)
            getTabPane().getTabs().remove(this);
        });
        editorView.setOnFindUsageCallback(this::showFindUsageDialog);
    }

    /**
     * Gets the SimpleType being edited.
     *
     * @return the simple type
     */
    public XsdSimpleType getSimpleType() {
        return simpleType;
    }

    @Override
    public boolean save() {
        try {
            // For SimpleType, changes are applied directly to the model
            // (unlike ComplexType which uses a virtual schema)
            // So save just means: accept all changes and clear dirty flag

            setDirty(false);
            logger.info("SimpleType saved successfully: {}", simpleType.getName());

            // Phase 6: User feedback for successful save
            showSuccessMessage("Save Successful",
                "SimpleType '" + simpleType.getName() + "' has been saved successfully.");

            return true;

        } catch (Exception e) {
            logger.error("Error saving SimpleType: {}", simpleType.getName(), e);

            // Phase 6: User feedback for save error
            showErrorMessage("Save Failed",
                "Failed to save SimpleType '" + simpleType.getName() + "'",
                "Error: " + e.getMessage());

            return false;
        }
    }

    /**
     * Shows a success message to the user.
     * Phase 6: Error Handling & Validation
     */
    private void showSuccessMessage(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Shows an error message to the user.
     * Phase 6: Error Handling & Validation
     */
    private void showErrorMessage(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Shows a dialog with all usages of this SimpleType in the schema.
     * Phase 5: Find Usage implementation
     */
    private void showFindUsageDialog() {
        logger.info("Finding usages for SimpleType: {}", simpleType.getName());

        // Find all usages
        java.util.List<UsageInfo> usages = findTypeUsages(simpleType.getName());

        // Create dialog
        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle("Find Usage");
        dialog.setHeaderText("Usage of SimpleType: " + simpleType.getName());
        dialog.setResizable(true);

        if (usages.isEmpty()) {
            dialog.setContentText("No usages found for this type in the schema.");
        } else {
            // Create list view with usages
            javafx.scene.control.ListView<String> listView = new javafx.scene.control.ListView<>();
            for (UsageInfo usage : usages) {
                listView.getItems().add(usage.toString());
            }
            listView.setPrefHeight(300);
            listView.setPrefWidth(400);

            javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(10);
            content.getChildren().add(new javafx.scene.control.Label("Found " + usages.size() + " usage(s):"));
            content.getChildren().add(listView);

            dialog.getDialogPane().setContent(content);
        }

        dialog.showAndWait();
    }

    /**
     * Finds all usages of a type in the schema.
     *
     * @param typeName the type name to search for
     * @return list of UsageInfo describing each usage
     */
    private java.util.List<UsageInfo> findTypeUsages(String typeName) {
        java.util.List<UsageInfo> usages = new java.util.ArrayList<>();
        findTypeUsagesRecursive(mainSchema, typeName, usages, "");
        return usages;
    }

    /**
     * Recursively searches for type usages in the schema tree.
     */
    private void findTypeUsagesRecursive(org.fxt.freexmltoolkit.controls.v2.model.XsdNode node,
                                         String typeName,
                                         java.util.List<UsageInfo> usages,
                                         String path) {
        if (node == null) return;

        String nodeName = node.getName() != null ? node.getName() : node.getClass().getSimpleName();
        String currentPath = path.isEmpty() ? nodeName : path + " > " + nodeName;

        // Check if this node references the type
        if (node instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdElement element) {
            String type = element.getType();
            if (typeName.equals(type) || typeName.equals(stripNamespacePrefix(type))) {
                usages.add(new UsageInfo("Element", element.getName(), currentPath));
            }
        } else if (node instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute attribute) {
            String type = attribute.getType();
            if (typeName.equals(type) || typeName.equals(stripNamespacePrefix(type))) {
                usages.add(new UsageInfo("Attribute", attribute.getName(), currentPath));
            }
        } else if (node instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdRestriction restriction) {
            String base = restriction.getBase();
            if (typeName.equals(base) || typeName.equals(stripNamespacePrefix(base))) {
                usages.add(new UsageInfo("Restriction base", base, currentPath));
            }
        } else if (node instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdExtension extension) {
            String base = extension.getBase();
            if (typeName.equals(base) || typeName.equals(stripNamespacePrefix(base))) {
                usages.add(new UsageInfo("Extension base", base, currentPath));
            }
        } else if (node instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdList list) {
            String itemType = list.getItemType();
            if (typeName.equals(itemType) || typeName.equals(stripNamespacePrefix(itemType))) {
                usages.add(new UsageInfo("List itemType", itemType, currentPath));
            }
        } else if (node instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdUnion union) {
            for (String memberType : union.getMemberTypes()) {
                if (typeName.equals(memberType) || typeName.equals(stripNamespacePrefix(memberType))) {
                    usages.add(new UsageInfo("Union memberType", memberType, currentPath));
                }
            }
        }

        // Recurse into children
        for (org.fxt.freexmltoolkit.controls.v2.model.XsdNode child : node.getChildren()) {
            findTypeUsagesRecursive(child, typeName, usages, currentPath);
        }
    }

    /**
     * Strips namespace prefix from a type name (e.g., "tns:MyType" -> "MyType").
     */
    private String stripNamespacePrefix(String typeName) {
        if (typeName == null) return null;
        int colonIndex = typeName.indexOf(':');
        return colonIndex >= 0 ? typeName.substring(colonIndex + 1) : typeName;
    }

    /**
     * Information about a type usage.
     */
    private record UsageInfo(String usageType, String nodeName, String path) {
        @Override
        public String toString() {
            return usageType + ": " + nodeName + " (" + path + ")";
        }
    }

    @Override
    public void discardChanges() {
        try {
            // Discard changes by recreating the view
            // This will reload all data from the model's current state
            // (assuming model is reloaded from original source externally)

            editorView = new SimpleTypeEditorView(simpleType, editorContext);
            setContent(editorView);
            editorView.setOnChangeCallback(() -> setDirty(true));

            setDirty(false);
            logger.info("SimpleType changes discarded: {}", simpleType.getName());

        } catch (Exception e) {
            logger.error("Error discarding changes for SimpleType: {}", simpleType.getName(), e);
        }
    }
}
