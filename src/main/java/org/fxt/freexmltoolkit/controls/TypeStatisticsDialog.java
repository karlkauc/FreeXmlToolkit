package org.fxt.freexmltoolkit.controls;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.fxt.freexmltoolkit.domain.TypeInfo;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Document;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TypeStatisticsDialog extends Stage {
    private final Document xsdDocument;
    private final XsdDomManipulator domManipulator;
    private final List<TypeInfo> types;

    public TypeStatisticsDialog(Document xsdDocument, XsdDomManipulator domManipulator, List<TypeInfo> types) {
        this.xsdDocument = xsdDocument;
        this.domManipulator = domManipulator;
        this.types = types;

        initModality(Modality.APPLICATION_MODAL);
        setTitle("XSD Type Statistics");
        setResizable(true);

        initializeUI();
    }

    private void initializeUI() {
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));

        Label titleLabel = new Label("Type Library Statistics");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        GridPane statisticsGrid = createStatisticsGrid();

        TableView<TypeInfo> detailedTable = createDetailedTable();

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        Button closeButton = new Button("Close");
        closeButton.setOnAction(e -> close());

        Button exportButton = new Button("Export Statistics");
        exportButton.setOnAction(e -> exportStatistics());

        buttonBox.getChildren().addAll(exportButton, closeButton);

        root.getChildren().addAll(titleLabel, statisticsGrid,
                new Label("Detailed Type Information:"),
                detailedTable, buttonBox);

        Scene scene = new Scene(root, 800, 600);
        setScene(scene);
    }

    private GridPane createStatisticsGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        grid.setStyle("-fx-border-color: #cccccc; -fx-border-width: 1px; -fx-border-radius: 5px;");

        int totalTypes = types.size();
        int complexTypes = (int) types.stream().filter(t -> t.category() == org.fxt.freexmltoolkit.domain.TypeInfo.TypeCategory.COMPLEX_TYPE).count();
        int simpleTypes = (int) types.stream().filter(t -> t.category() == org.fxt.freexmltoolkit.domain.TypeInfo.TypeCategory.SIMPLE_TYPE).count();
        int usedTypes = (int) types.stream().filter(t -> t.usageCount() > 0).count();
        int unusedTypes = totalTypes - usedTypes;

        Map<Integer, Long> usageDistribution = types.stream()
                .collect(Collectors.groupingBy(TypeInfo::usageCount, Collectors.counting()));

        int row = 0;
        addStatisticRow(grid, row++, "Total Types:", String.valueOf(totalTypes));
        addStatisticRow(grid, row++, "Complex Types:", String.valueOf(complexTypes));
        addStatisticRow(grid, row++, "Simple Types:", String.valueOf(simpleTypes));
        addStatisticRow(grid, row++, "Used Types:", String.valueOf(usedTypes));
        addStatisticRow(grid, row++, "Unused Types:", String.valueOf(unusedTypes));

        if (!usageDistribution.isEmpty()) {
            int maxUsage = usageDistribution.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
            addStatisticRow(grid, row++, "Most Referenced:", maxUsage + " times");
        }

        return grid;
    }

    private void addStatisticRow(GridPane grid, int row, String label, String value) {
        Label labelControl = new Label(label);
        labelControl.setStyle("-fx-font-weight: bold;");

        Label valueControl = new Label(value);
        valueControl.setStyle("-fx-text-fill: #2196F3;");

        grid.add(labelControl, 0, row);
        grid.add(valueControl, 1, row);
    }

    private TableView<TypeInfo> createDetailedTable() {
        TableView<TypeInfo> table = new TableView<>();

        TableColumn<TypeInfo, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().name()));
        nameColumn.setPrefWidth(200);

        TableColumn<TypeInfo, String> typeColumn = new TableColumn<>("Type");
        typeColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().category().getDisplayName()));
        typeColumn.setPrefWidth(120);

        TableColumn<TypeInfo, Integer> usageColumn = new TableColumn<>("Usage Count");
        usageColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue().usageCount()));
        usageColumn.setPrefWidth(100);

        TableColumn<TypeInfo, String> statusColumn = new TableColumn<>("Status");
        statusColumn.setCellValueFactory(data -> {
            String status = data.getValue().usageCount() > 0 ? "Used" : "Unused";
            return new javafx.beans.property.SimpleStringProperty(status);
        });
        statusColumn.setPrefWidth(80);

        table.getColumns().addAll(nameColumn, typeColumn, usageColumn, statusColumn);
        table.getItems().addAll(types);
        table.setPrefHeight(300);

        return table;
    }

    private void exportStatistics() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Export Type Statistics");
        fileChooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("CSV Files", "*.csv"),
                new javafx.stage.FileChooser.ExtensionFilter("Text Files", "*.txt")
        );

        java.io.File file = fileChooser.showSaveDialog(this);
        if (file != null) {
            try {
                exportToFile(file);
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Export Complete");
                alert.setHeaderText(null);
                alert.setContentText("Statistics exported successfully to: " + file.getName());
                alert.showAndWait();
            } catch (Exception e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Export Error");
                alert.setHeaderText("Failed to export statistics");
                alert.setContentText("Error: " + e.getMessage());
                alert.showAndWait();
            }
        }
    }

    private void exportToFile(java.io.File file) throws java.io.IOException {
        try (java.io.PrintWriter writer = new java.io.PrintWriter(file)) {
            if (file.getName().toLowerCase().endsWith(".csv")) {
                exportToCsv(writer);
            } else {
                exportToText(writer);
            }
        }
    }

    private void exportToCsv(java.io.PrintWriter writer) {
        writer.println("Name,Type,Usage Count,Status");
        for (TypeInfo type : types) {
            String status = type.usageCount() > 0 ? "Used" : "Unused";
            writer.printf("%s,%s,%d,%s%n", type.name(), type.category().getDisplayName(), type.usageCount(), status);
        }
    }

    private void exportToText(java.io.PrintWriter writer) {
        writer.println("XSD Type Statistics Report");
        writer.println("==========================");
        writer.println();

        int totalTypes = types.size();
        int complexTypes = (int) types.stream().filter(t -> t.category() == org.fxt.freexmltoolkit.domain.TypeInfo.TypeCategory.COMPLEX_TYPE).count();
        int simpleTypes = (int) types.stream().filter(t -> t.category() == org.fxt.freexmltoolkit.domain.TypeInfo.TypeCategory.SIMPLE_TYPE).count();
        int usedTypes = (int) types.stream().filter(t -> t.usageCount() > 0).count();
        int unusedTypes = totalTypes - usedTypes;

        writer.printf("Total Types: %d%n", totalTypes);
        writer.printf("Complex Types: %d%n", complexTypes);
        writer.printf("Simple Types: %d%n", simpleTypes);
        writer.printf("Used Types: %d%n", usedTypes);
        writer.printf("Unused Types: %d%n", unusedTypes);
        writer.println();

        writer.println("Detailed Type Information:");
        writer.println("-------------------------");
        for (TypeInfo type : types) {
            String status = type.usageCount() > 0 ? "Used" : "Unused";
            writer.printf("%-30s %-15s %3d usages (%s)%n",
                    type.name(), type.category().getDisplayName(), type.usageCount(), status);
        }
    }
}