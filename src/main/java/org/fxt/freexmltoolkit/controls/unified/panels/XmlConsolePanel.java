package org.fxt.freexmltoolkit.controls.unified.panels;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Collapsible console panel for log output and messages.
 * Used in the Unified Editor bottom panel area.
 */
public class XmlConsolePanel extends VBox {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_LINES = 1000;

    private final TextArea consoleArea;
    private final Label statusLabel;
    private Runnable onCloseRequested;

    public XmlConsolePanel() {
        setSpacing(4);
        setPadding(new Insets(4));
        setMinHeight(100);
        setPrefHeight(200);

        // Header with title and buttons
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(2, 4, 2, 4));

        FontIcon icon = new FontIcon("bi-terminal");
        icon.setIconSize(14);
        Label title = new Label("Console");
        title.setGraphic(icon);
        title.setStyle("-fx-font-weight: bold;");

        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 11px;");

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button clearBtn = new Button();
        clearBtn.setGraphic(createIcon("bi-trash", 14));
        clearBtn.setOnAction(e -> clear());
        clearBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");

        Button closeBtn = new Button();
        closeBtn.setGraphic(createIcon("bi-x-lg", 14));
        closeBtn.setOnAction(e -> {
            if (onCloseRequested != null) onCloseRequested.run();
        });
        closeBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");

        header.getChildren().addAll(title, statusLabel, spacer, clearBtn, closeBtn);

        // Console text area
        consoleArea = new TextArea();
        consoleArea.setEditable(false);
        consoleArea.setWrapText(true);
        consoleArea.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");
        VBox.setVgrow(consoleArea, Priority.ALWAYS);

        getChildren().addAll(header, consoleArea);
    }

    /**
     * Appends a message to the console with timestamp.
     */
    public void log(String message) {
        Platform.runLater(() -> {
            String timestamp = LocalDateTime.now().format(TIME_FORMAT);
            consoleArea.appendText("[" + timestamp + "] " + message + "\n");
            trimIfNeeded();
            statusLabel.setText("Last: " + timestamp);
        });
    }

    /**
     * Appends an info message.
     */
    public void info(String message) {
        log("INFO  " + message);
    }

    /**
     * Appends a warning message.
     */
    public void warn(String message) {
        log("WARN  " + message);
    }

    /**
     * Appends an error message.
     */
    public void error(String message) {
        log("ERROR " + message);
    }

    /**
     * Clears the console.
     */
    public void clear() {
        consoleArea.clear();
        statusLabel.setText("Cleared");
    }

    /**
     * Trims console content if it exceeds the max line count.
     */
    private void trimIfNeeded() {
        String text = consoleArea.getText();
        long lineCount = text.lines().count();
        if (lineCount > MAX_LINES) {
            String[] lines = text.split("\n");
            int start = lines.length - MAX_LINES;
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < lines.length; i++) {
                sb.append(lines[i]).append("\n");
            }
            consoleArea.setText(sb.toString());
        }
    }

    public void setOnCloseRequested(Runnable handler) {
        this.onCloseRequested = handler;
    }

    private FontIcon createIcon(String literal, int size) {
        FontIcon fi = new FontIcon(literal);
        fi.setIconSize(size);
        return fi;
    }
}
