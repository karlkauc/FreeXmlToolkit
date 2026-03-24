package org.fxt.freexmltoolkit.controls.unified.xslt;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Panel displaying XSLT debug information including messages, warnings, and template trace.
 */
public class XsltDebugPanel extends VBox {

    private final CheckBox enableDebugCheck;
    private final ListView<String> messagesListView;
    private final ObservableList<String> messages;
    private final TextArea traceArea;

    public XsltDebugPanel() {
        setSpacing(8);
        setPadding(new Insets(8));

        // Header
        HBox header = new HBox(8);
        Label title = new Label("Debug Output");
        title.setStyle("-fx-font-weight: bold;");

        enableDebugCheck = new CheckBox("Enable Debug Mode");

        Button clearBtn = new Button("Clear");
        clearBtn.setStyle("-fx-font-size: 11px;");
        clearBtn.setOnAction(e -> clear());

        header.getChildren().addAll(title, enableDebugCheck, clearBtn);

        // Messages
        Label msgLabel = new Label("Messages & Warnings");
        msgLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");

        messages = FXCollections.observableArrayList();
        messagesListView = new ListView<>(messages);
        messagesListView.setPrefHeight(120);
        messagesListView.setPlaceholder(new Label("No messages"));

        // Trace
        Label traceLabel = new Label("Template Execution Trace");
        traceLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");

        traceArea = new TextArea();
        traceArea.setEditable(false);
        traceArea.setPromptText("Template execution trace will appear here when debug mode is enabled...");
        traceArea.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
        VBox.setVgrow(traceArea, Priority.ALWAYS);

        getChildren().addAll(header, msgLabel, messagesListView, traceLabel, traceArea);
    }

    /**
     * Returns whether debug mode is enabled.
     */
    public boolean isDebugEnabled() {
        return enableDebugCheck.isSelected();
    }

    /**
     * Adds a message to the messages list.
     */
    public void addMessage(String message) {
        messages.add(message);
    }

    /**
     * Sets the template execution trace text.
     */
    public void setTrace(String trace) {
        traceArea.setText(trace);
    }

    /**
     * Sets messages from a list.
     */
    public void setMessages(java.util.List<String> msgs) {
        messages.setAll(msgs);
    }

    /**
     * Clears all debug output.
     */
    public void clear() {
        messages.clear();
        traceArea.clear();
    }
}
