package org.fxt.freexmltoolkit.controls.v2.view;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Placeholder component shown when the graphical XSD view is not yet loaded.
 * Displays a message prompting the user to load the view, and shows a progress indicator during loading.
 * Includes visual feedback for loading progress with status messages.
 *
 * @since 2.0
 */
public class XsdGraphViewPlaceholder extends VBox {

    private final Label messageLabel;
    private final ProgressIndicator progressIndicator;
    private final ProgressBar progressBar;
    private final Button loadButton;
    private final Label statusLabel;
    private final Label detailLabel;
    private Runnable onLoadRequested;

    /**
     * Creates a new placeholder for the graphical XSD view.
     */
    public XsdGraphViewPlaceholder() {
        setAlignment(Pos.CENTER);
        setSpacing(20);
        setStyle("-fx-background-color: #f8f9fa; -fx-padding: 40;");

        // Icon
        FontIcon icon = new FontIcon("bi-diagram-3");
        icon.setIconSize(64);
        icon.setIconColor(Color.valueOf("#6c757d"));

        // Message label
        messageLabel = new Label("Graphical View Not Loaded");
        messageLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #495057;");

        // Description
        Label descriptionLabel = new Label("The graphical schema visualization will be loaded when you click the button below.\n" +
                "This helps conserve system resources when working with the text view.");
        descriptionLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #6c757d; -fx-text-alignment: center;");
        descriptionLabel.setWrapText(true);
        descriptionLabel.setMaxWidth(400);

        // Load button
        loadButton = new Button("Load Graphical View");
        loadButton.setStyle("-fx-background-color: #007bff; -fx-text-fill: white; -fx-font-size: 14px; " +
                "-fx-padding: 10 24; -fx-background-radius: 4;");
        loadButton.setOnAction(e -> {
            if (onLoadRequested != null) {
                onLoadRequested.run();
            }
        });

        // Progress indicator (initially hidden)
        progressIndicator = new ProgressIndicator();
        progressIndicator.setPrefSize(48, 48);
        progressIndicator.setVisible(false);
        progressIndicator.setManaged(false);

        // Progress bar (initially hidden)
        progressBar = new ProgressBar();
        progressBar.setPrefWidth(250);
        progressBar.setProgress(-1);  // Indeterminate
        progressBar.setVisible(false);
        progressBar.setManaged(false);

        // Status label for loading progress (initially hidden)
        statusLabel = new Label("Loading...");
        statusLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #495057;");
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);

        // Detail label for sub-status (initially hidden)
        detailLabel = new Label("");
        detailLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6c757d;");
        detailLabel.setVisible(false);
        detailLabel.setManaged(false);

        getChildren().addAll(icon, messageLabel, descriptionLabel, loadButton, progressIndicator, progressBar, statusLabel, detailLabel);
    }

    /**
     * Sets the callback to invoke when the user requests to load the graphical view.
     *
     * @param onLoadRequested the callback to invoke
     */
    public void setOnLoadRequested(Runnable onLoadRequested) {
        this.onLoadRequested = onLoadRequested;
    }

    /**
     * Shows the loading state with a progress indicator.
     *
     * @param statusMessage the status message to display
     */
    public void showLoading(String statusMessage) {
        loadButton.setVisible(false);
        loadButton.setManaged(false);
        messageLabel.setText("Loading Graphical View...");
        messageLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #495057;");

        progressIndicator.setVisible(true);
        progressIndicator.setManaged(true);
        progressIndicator.setProgress(-1); // Indeterminate

        progressBar.setVisible(true);
        progressBar.setManaged(true);
        progressBar.setProgress(-1);  // Indeterminate

        statusLabel.setText(statusMessage);
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);

        detailLabel.setText("This may take a moment for large schemas...");
        detailLabel.setVisible(true);
        detailLabel.setManaged(true);
    }

    /**
     * Updates the loading status message.
     *
     * @param statusMessage the new status message
     */
    public void updateLoadingStatus(String statusMessage) {
        statusLabel.setText(statusMessage);
    }

    /**
     * Updates the loading status with both main and detail messages.
     *
     * @param statusMessage the main status message
     * @param detailMessage the detailed status message
     */
    public void updateLoadingStatus(String statusMessage, String detailMessage) {
        statusLabel.setText(statusMessage);
        detailLabel.setText(detailMessage);
    }

    /**
     * Updates the loading progress with a specific value.
     *
     * @param progress the progress value (0.0 to 1.0, or -1 for indeterminate)
     * @param statusMessage the status message to display
     */
    public void updateProgress(double progress, String statusMessage) {
        progressBar.setProgress(progress);
        statusLabel.setText(statusMessage);
    }

    /**
     * Resets the placeholder to its initial state.
     */
    public void reset() {
        loadButton.setVisible(true);
        loadButton.setManaged(true);
        loadButton.setText("Load Graphical View");  // Reset button text
        messageLabel.setText("Graphical View Not Loaded");
        messageLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #495057;");

        progressIndicator.setVisible(false);
        progressIndicator.setManaged(false);

        progressBar.setVisible(false);
        progressBar.setManaged(false);

        statusLabel.setVisible(false);
        statusLabel.setManaged(false);

        detailLabel.setVisible(false);
        detailLabel.setManaged(false);
    }

    /**
     * Shows an error state when loading fails.
     *
     * @param errorMessage the error message to display
     */
    public void showError(String errorMessage) {
        loadButton.setVisible(true);
        loadButton.setManaged(true);
        loadButton.setText("Try Again");
        messageLabel.setText("Failed to Load Graphical View");
        messageLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #dc3545;");

        progressIndicator.setVisible(false);
        progressIndicator.setManaged(false);

        progressBar.setVisible(false);
        progressBar.setManaged(false);

        statusLabel.setText(errorMessage);
        statusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #dc3545;");
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);

        detailLabel.setText("Click 'Try Again' to retry loading");
        detailLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6c757d;");
        detailLabel.setVisible(true);
        detailLabel.setManaged(true);
    }
}
