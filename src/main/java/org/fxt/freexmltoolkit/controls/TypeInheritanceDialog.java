package org.fxt.freexmltoolkit.controls;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.fxt.freexmltoolkit.domain.TypeInfo;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.*;

/**
 * Dialog for visualizing type inheritance relationships in XSD schemas.
 * Shows base types, derived types, and inheritance hierarchy.
 */
public class TypeInheritanceDialog extends Stage {
    private final Document xsdDocument;
    private final XsdDomManipulator domManipulator;
    private final List<TypeInfo> types;

    private TreeView<String> inheritanceTree;
    private TextArea detailsArea;
    private ComboBox<String> typeSelector;
    private Label statsLabel;

    public TypeInheritanceDialog(Document xsdDocument, XsdDomManipulator domManipulator, List<TypeInfo> types) {
        this.xsdDocument = xsdDocument;
        this.domManipulator = domManipulator;
        this.types = types;

        initModality(Modality.APPLICATION_MODAL);
        setTitle("Type Inheritance Visualizer");
        setResizable(true);

        initializeUI();
        buildInheritanceTree();
    }

    private void initializeUI() {
        BorderPane root = new BorderPane();

        // Top section - controls
        VBox topSection = new VBox(10);
        topSection.setPadding(new Insets(10));

        Label titleLabel = new Label("Type Inheritance Hierarchy");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        HBox controlsBox = new HBox(10);
        Label selectorLabel = new Label("Focus on type:");
        typeSelector = new ComboBox<>();
        typeSelector.getItems().add("All Types");
        types.forEach(type -> typeSelector.getItems().add(type.name()));
        typeSelector.setValue("All Types");
        typeSelector.setOnAction(e -> updateTreeForSelectedType());

        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> buildInheritanceTree());

        controlsBox.getChildren().addAll(selectorLabel, typeSelector, refreshButton);

        statsLabel = new Label();
        statsLabel.setStyle("-fx-text-fill: #666666;");

        topSection.getChildren().addAll(titleLabel, controlsBox, statsLabel);

        // Center section - tree view
        inheritanceTree = new TreeView<>();
        inheritanceTree.setShowRoot(false);
        inheritanceTree.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                showTypeDetails(newVal.getValue());
            }
        });

        // Bottom section - details
        VBox bottomSection = new VBox(5);
        bottomSection.setPadding(new Insets(10));

        Label detailsLabel = new Label("Type Details:");
        detailsLabel.setStyle("-fx-font-weight: bold;");

        detailsArea = new TextArea();
        detailsArea.setEditable(false);
        detailsArea.setPrefRowCount(8);
        detailsArea.setStyle("-fx-font-family: monospace;");

        bottomSection.getChildren().addAll(detailsLabel, detailsArea);

        // Button bar
        HBox buttonBox = new HBox(10);
        buttonBox.setPadding(new Insets(10));
        buttonBox.setStyle("-fx-alignment: center-right;");

        Button exportButton = new Button("Export Hierarchy");
        exportButton.setOnAction(e -> exportHierarchy());

        Button closeButton = new Button("Close");
        closeButton.setOnAction(e -> close());

        buttonBox.getChildren().addAll(exportButton, closeButton);

        root.setTop(topSection);
        root.setCenter(inheritanceTree);
        root.setBottom(new VBox(bottomSection, buttonBox));

        Scene scene = new Scene(root, 800, 700);
        setScene(scene);
    }

    private void buildInheritanceTree() {
        TreeItem<String> root = new TreeItem<>("Root");
        Map<String, TreeItem<String>> typeNodes = new HashMap<>();
        Map<String, String> baseTypes = new HashMap<>();
        Set<String> rootTypes = new HashSet<>();

        // First pass: create all type nodes and identify base types
        for (TypeInfo type : types) {
            TreeItem<String> typeNode = new TreeItem<>(formatTypeNode(type));
            typeNodes.put(type.name(), typeNode);

            String baseType = extractBaseType(type);
            if (baseType != null && !baseType.startsWith("xs:") && !baseType.startsWith("xsd:")) {
                baseTypes.put(type.name(), baseType);
            } else {
                rootTypes.add(type.name());
            }
        }

        // Second pass: build hierarchy
        for (String typeName : rootTypes) {
            TreeItem<String> typeNode = typeNodes.get(typeName);
            if (typeNode != null) {
                root.getChildren().add(typeNode);
                addDerivedTypes(typeNode, typeName, typeNodes, baseTypes);
            }
        }

        // Handle types that derive from types not in our list (orphaned inheritance chains)
        for (Map.Entry<String, String> entry : baseTypes.entrySet()) {
            String typeName = entry.getKey();
            String baseTypeName = entry.getValue();
            TreeItem<String> typeNode = typeNodes.get(typeName);

            if (typeNode != null && typeNode.getParent() == null) {
                // This type's base is not in our type list
                TreeItem<String> orphanedChain = new TreeItem<>(baseTypeName + " (external)");
                orphanedChain.getChildren().add(typeNode);
                root.getChildren().add(orphanedChain);

                addDerivedTypes(typeNode, typeName, typeNodes, baseTypes);
            }
        }

        // Sort root children
        root.getChildren().sort(Comparator.comparing(TreeItem::getValue));

        inheritanceTree.setRoot(root);
        expandTreeNodes(root, 2); // Expand first 2 levels

        updateStats();
    }

    private void addDerivedTypes(TreeItem<String> parentNode, String parentTypeName,
                                 Map<String, TreeItem<String>> typeNodes, Map<String, String> baseTypes) {
        for (Map.Entry<String, String> entry : baseTypes.entrySet()) {
            if (entry.getValue().equals(parentTypeName)) {
                String derivedTypeName = entry.getKey();
                TreeItem<String> derivedNode = typeNodes.get(derivedTypeName);
                if (derivedNode != null && derivedNode.getParent() == null) {
                    parentNode.getChildren().add(derivedNode);
                    addDerivedTypes(derivedNode, derivedTypeName, typeNodes, baseTypes);
                }
            }
        }

        // Sort children
        parentNode.getChildren().sort(Comparator.comparing(TreeItem::getValue));
    }

    private String extractBaseType(TypeInfo type) {
        Element typeElement = domManipulator.findTypeDefinition(xsdDocument, type.name());
        if (typeElement == null) return null;

        // Check for complexContent extension/restriction
        NodeList complexContent = typeElement.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "complexContent");
        if (complexContent.getLength() > 0) {
            Element content = (Element) complexContent.item(0);

            NodeList extensions = content.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "extension");
            if (extensions.getLength() > 0) {
                return ((Element) extensions.item(0)).getAttribute("base");
            }

            NodeList restrictions = content.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "restriction");
            if (restrictions.getLength() > 0) {
                return ((Element) restrictions.item(0)).getAttribute("base");
            }
        }

        // Check for simpleContent extension/restriction
        NodeList simpleContent = typeElement.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "simpleContent");
        if (simpleContent.getLength() > 0) {
            Element content = (Element) simpleContent.item(0);

            NodeList extensions = content.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "extension");
            if (extensions.getLength() > 0) {
                return ((Element) extensions.item(0)).getAttribute("base");
            }

            NodeList restrictions = content.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "restriction");
            if (restrictions.getLength() > 0) {
                return ((Element) restrictions.item(0)).getAttribute("base");
            }
        }

        // Check for simpleType restriction
        if ("simpleType".equals(typeElement.getLocalName())) {
            NodeList restrictions = typeElement.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "restriction");
            if (restrictions.getLength() > 0) {
                return ((Element) restrictions.item(0)).getAttribute("base");
            }
        }

        return null;
    }

    private String formatTypeNode(TypeInfo type) {
        String baseType = extractBaseType(type);
        String inheritance = baseType != null ? " extends " + baseType : "";
        return String.format("%s (%s)%s [%d uses]",
                type.name(), type.category().getDisplayName(), inheritance, type.usageCount());
    }

    private void expandTreeNodes(TreeItem<String> node, int maxDepth) {
        if (maxDepth <= 0) return;

        node.setExpanded(true);
        for (TreeItem<String> child : node.getChildren()) {
            expandTreeNodes(child, maxDepth - 1);
        }
    }

    private void updateTreeForSelectedType() {
        String selectedType = typeSelector.getValue();
        if ("All Types".equals(selectedType)) {
            buildInheritanceTree();
        } else {
            buildFilteredTree(selectedType);
        }
    }

    private void buildFilteredTree(String focusTypeName) {
        TreeItem<String> root = new TreeItem<>("Root");

        // Find the focused type
        TypeInfo focusType = types.stream()
                .filter(t -> t.name().equals(focusTypeName))
                .findFirst()
                .orElse(null);

        if (focusType != null) {
            TreeItem<String> focusNode = new TreeItem<>(formatTypeNode(focusType));

            // Build inheritance chain upward
            TreeItem<String> currentNode = focusNode;
            String currentTypeName = focusTypeName;

            while (currentTypeName != null) {
                final String finalCurrentTypeName = currentTypeName;
                String baseType = extractBaseType(types.stream()
                        .filter(t -> t.name().equals(finalCurrentTypeName))
                        .findFirst()
                        .orElse(null));

                if (baseType != null && !baseType.startsWith("xs:")) {
                    TypeInfo baseTypeInfo = types.stream()
                            .filter(t -> t.name().equals(baseType))
                            .findFirst()
                            .orElse(null);

                    if (baseTypeInfo != null) {
                        TreeItem<String> baseNode = new TreeItem<>(formatTypeNode(baseTypeInfo));
                        baseNode.getChildren().add(currentNode);
                        currentNode = baseNode;
                        currentTypeName = baseType;
                    } else {
                        // External base type
                        TreeItem<String> externalNode = new TreeItem<>(baseType + " (external)");
                        externalNode.getChildren().add(currentNode);
                        currentNode = externalNode;
                        break;
                    }
                } else {
                    break;
                }
            }

            // Add derived types
            addAllDerivedTypes(focusNode, focusTypeName);

            root.getChildren().add(currentNode);
            expandTreeNodes(root, 10); // Expand all for filtered view
        }

        inheritanceTree.setRoot(root);
        updateStats();
    }

    private void addAllDerivedTypes(TreeItem<String> parentNode, String parentTypeName) {
        for (TypeInfo type : types) {
            String baseType = extractBaseType(type);
            if (parentTypeName.equals(baseType)) {
                TreeItem<String> derivedNode = new TreeItem<>(formatTypeNode(type));
                parentNode.getChildren().add(derivedNode);
                addAllDerivedTypes(derivedNode, type.name());
            }
        }

        parentNode.getChildren().sort(Comparator.comparing(TreeItem::getValue));
    }

    private void showTypeDetails(String treeNodeValue) {
        // Extract type name from tree node value (format: "TypeName (typeKind)...")
        String typeName = treeNodeValue.split(" ")[0];

        TypeInfo type = types.stream()
                .filter(t -> t.name().equals(typeName))
                .findFirst()
                .orElse(null);

        if (type != null) {
            StringBuilder details = new StringBuilder();
            details.append("Type: ").append(type.name()).append("\n");
            details.append("Kind: ").append(type.category().getDisplayName()).append("\n");
            details.append("Usage Count: ").append(type.usageCount()).append("\n");

            String baseType = extractBaseType(type);
            if (baseType != null) {
                details.append("Base Type: ").append(baseType).append("\n");
            }

            if (type.documentation() != null) {
                details.append("Documentation: ").append(type.documentation()).append("\n");
            }

            details.append("XPath: ").append(type.xpath()).append("\n");

            // Find derived types
            List<String> derivedTypes = new ArrayList<>();
            for (TypeInfo otherType : types) {
                String otherBaseType = extractBaseType(otherType);
                if (type.name().equals(otherBaseType)) {
                    derivedTypes.add(otherType.name());
                }
            }

            if (!derivedTypes.isEmpty()) {
                details.append("Derived Types: ").append(String.join(", ", derivedTypes)).append("\n");
            }

            detailsArea.setText(details.toString());
        } else if (treeNodeValue.contains("(external)")) {
            detailsArea.setText("External type: " + typeName + "\n\nThis type is defined outside the current schema.");
        } else {
            detailsArea.setText("No details available for: " + treeNodeValue);
        }
    }

    private void updateStats() {
        int totalTypes = types.size();
        int typesWithBase = types.stream()
                .mapToInt(t -> extractBaseType(t) != null ? 1 : 0)
                .sum();
        int rootTypes = totalTypes - typesWithBase;

        statsLabel.setText(String.format(
                "Total types: %d | Root types: %d | Derived types: %d",
                totalTypes, rootTypes, typesWithBase));
    }

    private void exportHierarchy() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Export Type Hierarchy");
        fileChooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("Text Files", "*.txt"),
                new javafx.stage.FileChooser.ExtensionFilter("All Files", "*.*")
        );

        java.io.File file = fileChooser.showSaveDialog(this);
        if (file != null) {
            try (java.io.PrintWriter writer = new java.io.PrintWriter(file)) {
                writer.println("XSD Type Inheritance Hierarchy");
                writer.println("==============================");
                writer.println();

                exportTreeNode(inheritanceTree.getRoot(), writer, 0);

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Export Complete");
                alert.setHeaderText(null);
                alert.setContentText("Type hierarchy exported to: " + file.getName());
                alert.showAndWait();
            } catch (Exception e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Export Error");
                alert.setHeaderText("Failed to export hierarchy");
                alert.setContentText("Error: " + e.getMessage());
                alert.showAndWait();
            }
        }
    }

    private void exportTreeNode(TreeItem<String> node, java.io.PrintWriter writer, int depth) {
        if (depth > 0) { // Skip root node
            String indent = "  ".repeat(depth - 1);
            writer.println(indent + node.getValue());
        }

        for (TreeItem<String> child : node.getChildren()) {
            exportTreeNode(child, writer, depth + 1);
        }
    }
}