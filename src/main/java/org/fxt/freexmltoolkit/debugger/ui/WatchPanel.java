package org.fxt.freexmltoolkit.debugger.ui;

import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.fxt.freexmltoolkit.debugger.WatchExpression;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Compact panel for user-defined XPath watch expressions evaluated at every
 * pause point.
 */
public class WatchPanel extends VBox {

    private final ObservableList<WatchExpression> data = FXCollections.observableArrayList();
    private final TableView<WatchExpression> table = new TableView<>();
    private final TextField input = new TextField();

    private Consumer<WatchExpression> onAdded;
    private Consumer<WatchExpression> onRemoved;

    public WatchPanel() {
        setSpacing(4);
        setPadding(new Insets(4));

        TableColumn<WatchExpression, String> exprCol = new TableColumn<>("Expression");
        exprCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getXpath()));
        exprCol.setPrefWidth(180);

        TableColumn<WatchExpression, String> valueCol = new TableColumn<>("Value");
        valueCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getLastError() != null
                        ? "ERR: " + c.getValue().getLastError()
                        : c.getValue().getLastValue()));
        valueCol.setPrefWidth(200);

        TableColumn<WatchExpression, Void> deleteCol = new TableColumn<>("");
        deleteCol.setPrefWidth(36);
        deleteCol.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            private final Button btn = new Button();
            {
                FontIcon fi = new FontIcon("bi-x-circle");
                fi.setIconSize(12);
                btn.setGraphic(fi);
                btn.getStyleClass().add("btn-flat");
                btn.setOnAction(e -> {
                    WatchExpression w = getTableView().getItems().get(getIndex());
                    data.remove(w);
                    if (onRemoved != null) onRemoved.accept(w);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        table.getColumns().addAll(java.util.List.of(exprCol, valueCol, deleteCol));
        table.setItems(data);
        table.setPlaceholder(new javafx.scene.control.Label("Add an XPath expression to watch."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
        VBox.setVgrow(table, Priority.ALWAYS);

        input.setPromptText("XPath expression (e.g. count(*))");
        Button addBtn = new Button();
        FontIcon addIcon = new FontIcon("bi-plus-circle");
        addIcon.setIconSize(14);
        addBtn.setGraphic(addIcon);
        addBtn.setTooltip(new javafx.scene.control.Tooltip("Add watch expression"));
        addBtn.setOnAction(e -> addWatch());
        input.setOnAction(e -> addWatch());

        HBox bottom = new HBox(4, input, addBtn);
        HBox.setHgrow(input, Priority.ALWAYS);

        getChildren().addAll(table, bottom);
    }

    private void addWatch() {
        String text = input.getText() == null ? "" : input.getText().trim();
        if (text.isEmpty()) return;
        WatchExpression w = new WatchExpression(text);
        if (data.contains(w)) {
            input.clear();
            return;
        }
        data.add(w);
        input.clear();
        if (onAdded != null) onAdded.accept(w);
    }

    public ObservableList<WatchExpression> getWatches() {
        return data;
    }

    public void setOnAdded(Consumer<WatchExpression> h) { this.onAdded = h; }
    public void setOnRemoved(Consumer<WatchExpression> h) { this.onRemoved = h; }

    /** Re-render the value column. Safe from any thread. */
    public void refreshValues() {
        Runnable r = () -> table.refresh();
        if (Platform.isFxApplicationThread()) r.run();
        else Platform.runLater(r);
    }

    public void addExisting(WatchExpression w) {
        if (w != null && !data.contains(w)) data.add(w);
    }
}
