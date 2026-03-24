package org.fxt.freexmltoolkit.controls.unified.xslt;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.service.XsltTransformationResult;

/**
 * Panel displaying XSLT transformation performance metrics.
 */
public class XsltPerformancePanel extends VBox {

    private final Label executionTimeLabel;
    private final Label outputSizeLabel;
    private final Label throughputLabel;
    private final Label ratingLabel;
    private final TextArea detailsArea;

    public XsltPerformancePanel() {
        setSpacing(8);
        setPadding(new Insets(8));

        Label title = new Label("Performance Metrics");
        title.setStyle("-fx-font-weight: bold;");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(4);

        executionTimeLabel = new Label("-");
        outputSizeLabel = new Label("-");
        throughputLabel = new Label("-");
        ratingLabel = new Label("-");

        grid.add(new Label("Execution Time:"), 0, 0);
        grid.add(executionTimeLabel, 1, 0);
        grid.add(new Label("Output Size:"), 0, 1);
        grid.add(outputSizeLabel, 1, 1);
        grid.add(new Label("Throughput:"), 0, 2);
        grid.add(throughputLabel, 1, 2);
        grid.add(new Label("Rating:"), 0, 3);
        grid.add(ratingLabel, 1, 3);

        detailsArea = new TextArea();
        detailsArea.setEditable(false);
        detailsArea.setPromptText("Detailed performance report...");
        VBox.setVgrow(detailsArea, Priority.ALWAYS);

        getChildren().addAll(title, grid, detailsArea);
    }

    /**
     * Updates metrics from a transformation result.
     */
    public void updateFromResult(XsltTransformationResult result) {
        if (result == null) {
            clear();
            return;
        }

        long execTime = result.getExecutionTime();
        executionTimeLabel.setText(execTime + " ms");

        String output = result.getOutputContent();
        int outputBytes = output != null ? output.getBytes().length : 0;
        outputSizeLabel.setText(formatBytes(outputBytes));

        double throughputKBs = execTime > 0 ? (outputBytes / 1024.0) / (execTime / 1000.0) : 0;
        throughputLabel.setText(String.format("%.1f KB/s", throughputKBs));

        ratingLabel.setText(getRating(execTime));

        StringBuilder details = new StringBuilder();
        details.append("Execution Time: ").append(execTime).append(" ms\n");
        details.append("Output Size: ").append(formatBytes(outputBytes)).append("\n");
        details.append("Throughput: ").append(String.format("%.1f KB/s", throughputKBs)).append("\n");
        details.append("Success: ").append(result.isSuccess()).append("\n");

        if (!result.isSuccess() && result.getErrorMessage() != null) {
            details.append("\nError: ").append(result.getErrorMessage()).append("\n");
        }

        detailsArea.setText(details.toString());
    }

    /**
     * Clears all metrics.
     */
    public void clear() {
        executionTimeLabel.setText("-");
        outputSizeLabel.setText("-");
        throughputLabel.setText("-");
        ratingLabel.setText("-");
        detailsArea.clear();
    }

    private String getRating(long execTimeMs) {
        if (execTimeMs < 100) return "\u2605\u2605\u2605\u2605\u2605 Excellent";
        if (execTimeMs < 500) return "\u2605\u2605\u2605\u2605\u2606 Good";
        if (execTimeMs < 1000) return "\u2605\u2605\u2605\u2606\u2606 Average";
        if (execTimeMs < 5000) return "\u2605\u2605\u2606\u2606\u2606 Slow";
        return "\u2605\u2606\u2606\u2606\u2606 Very Slow";
    }

    private String formatBytes(int bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
