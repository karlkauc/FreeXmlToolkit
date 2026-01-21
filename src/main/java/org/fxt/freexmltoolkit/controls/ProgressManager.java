package org.fxt.freexmltoolkit.controls;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Centralized progress management for long-running operations.
 * Provides consistent progress indication across the Schematron Editor and other components.
 *
 * <p>This class implements the Singleton pattern and manages a dedicated thread pool
 * for executing background tasks. It provides visual feedback through a modal progress
 * dialog that displays task status and progress information.</p>
 *
 * <p>Key features:</p>
 * <ul>
 *   <li>Asynchronous task execution with automatic progress dialog display</li>
 *   <li>Support for both simple tasks (via Supplier) and complex tasks with progress updates (via JavaFX Task)</li>
 *   <li>Automatic error handling with customizable error callbacks</li>
 *   <li>Thread-safe singleton access</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ProgressManager.getInstance().executeWithProgress(
 *     "Loading file",
 *     () -> loadFile(path),
 *     result -> displayResult(result),
 *     error -> showError(error)
 * );
 * }</pre>
 *
 * @author FreeXmlToolkit
 * @since 1.0
 */
public class ProgressManager {

    /**
     * Logger instance for this class.
     */
    private static final Logger logger = LogManager.getLogger(ProgressManager.class);

    /**
     * Singleton instance of the ProgressManager.
     */
    private static ProgressManager instance;

    /**
     * Executor service for running background tasks.
     * Uses daemon threads to allow application shutdown without explicit cleanup.
     */
    private final ExecutorService executor;

    /**
     * The progress dialog stage displayed during task execution.
     */
    private Stage progressStage;

    /**
     * Progress indicator widget showing task progress.
     */
    private ProgressIndicator progressIndicator;

    /**
     * Label displaying the current task name or status message.
     */
    private Label progressLabel;

    /**
     * Private constructor to enforce singleton pattern.
     * Initializes the executor service with daemon threads.
     */
    private ProgressManager() {
        executor = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            thread.setName("ProgressManager-Worker");
            return thread;
        });
        logger.info("ProgressManager initialized");
    }

    /**
     * Returns the singleton instance of the ProgressManager.
     *
     * <p>This method is thread-safe and will create the instance on first access.</p>
     *
     * @return the singleton ProgressManager instance
     */
    public static synchronized ProgressManager getInstance() {
        if (instance == null) {
            instance = new ProgressManager();
        }
        return instance;
    }

    /**
     * Executes a task asynchronously with automatic progress indication.
     *
     * <p>This method displays a progress dialog while the task is running and automatically
     * hides it upon completion. The dialog shows an indeterminate progress indicator since
     * simple Supplier tasks do not provide progress updates.</p>
     *
     * <p>The task is executed on a background thread, and the callbacks are invoked on the
     * JavaFX Application Thread, making it safe to update UI components directly.</p>
     *
     * @param <T>       the type of the result produced by the task
     * @param taskName  the name of the task, displayed in the progress dialog
     * @param task      the task to execute, provided as a Supplier that returns the result
     * @param onSuccess callback invoked on the JavaFX Application Thread when the task completes
     *                  successfully; receives the task result; may be null
     * @param onError   callback invoked on the JavaFX Application Thread when the task fails;
     *                  receives the exception; may be null (a default error dialog is shown if null)
     * @return a CompletableFuture that completes with the task result or exceptionally on failure
     */
    public <T> CompletableFuture<T> executeWithProgress(String taskName,
                                                        Supplier<T> task,
                                                        Consumer<T> onSuccess,
                                                        Consumer<Throwable> onError) {

        logger.info("Starting background task: {}", taskName);

        // Show progress dialog
        Platform.runLater(() -> showProgressDialog(taskName));

        return CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return task.get();
                    } catch (Exception e) {
                        logger.error("Task failed: {}", taskName, e);
                        throw new RuntimeException(e);
                    }
                }, executor)
                .whenComplete((result, throwable) -> {
                    Platform.runLater(() -> {
                        hideProgressDialog();

                        if (throwable != null) {
                            if (onError != null) {
                                onError.accept(throwable);
                            } else {
                                showErrorDialog("Task Failed",
                                        "An error occurred during: " + taskName + "\n" + throwable.getMessage());
                            }
                        } else {
                            if (onSuccess != null) {
                                onSuccess.accept(result);
                            }
                        }
                    });
                });
    }

    /**
     * Executes a JavaFX Task with support for custom progress updates.
     *
     * <p>This method is designed for tasks that report their own progress using
     * {@link Task#updateProgress(double, double)} and {@link Task#updateMessage(String)}.
     * The progress dialog automatically reflects these updates.</p>
     *
     * <p>Unlike {@link #executeWithProgress(String, Supplier, Consumer, Consumer)}, this method
     * accepts a JavaFX Task object, allowing fine-grained control over progress reporting
     * and cancellation support.</p>
     *
     * <p>The callbacks are invoked on the JavaFX Application Thread.</p>
     *
     * @param <T>       the type of the result produced by the task
     * @param taskName  the name of the task, displayed initially in the progress dialog
     * @param task      the JavaFX Task to execute; should call updateProgress() and updateMessage()
     *                  to provide progress feedback
     * @param onSuccess callback invoked on the JavaFX Application Thread when the task completes
     *                  successfully; receives the task result; may be null
     * @param onError   callback invoked on the JavaFX Application Thread when the task fails;
     *                  receives the exception; may be null (a default error dialog is shown if null)
     */
    public <T> void executeWithProgressUpdates(String taskName,
                                               Task<T> task,
                                               Consumer<T> onSuccess,
                                               Consumer<Throwable> onError) {

        logger.info("Starting task with progress updates: {}", taskName);

        // Set up progress binding
        task.progressProperty().addListener((obs, oldProgress, newProgress) -> {
            Platform.runLater(() -> {
                if (progressIndicator != null) {
                    progressIndicator.setProgress(newProgress.doubleValue());
                }
            });
        });

        task.messageProperty().addListener((obs, oldMessage, newMessage) -> {
            Platform.runLater(() -> {
                if (progressLabel != null) {
                    progressLabel.setText(newMessage);
                }
            });
        });

        task.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                hideProgressDialog();
                if (onSuccess != null) {
                    onSuccess.accept(task.getValue());
                }
            });
        });

        task.setOnFailed(e -> {
            Platform.runLater(() -> {
                hideProgressDialog();
                Throwable exception = task.getException();
                if (onError != null) {
                    onError.accept(exception);
                } else {
                    showErrorDialog("Task Failed",
                            "An error occurred during: " + taskName + "\n" +
                                    (exception != null ? exception.getMessage() : "Unknown error"));
                }
            });
        });

        // Show progress dialog and start task
        Platform.runLater(() -> showProgressDialog(taskName));
        executor.execute(task);
    }

    /**
     * Displays the progress dialog with the given task name.
     *
     * <p>Creates and shows a utility-style dialog containing a progress indicator
     * and a label with the task name. The dialog is centered on screen and
     * non-resizable.</p>
     *
     * <p>If a progress dialog is already showing, this method does nothing to prevent
     * multiple dialogs from appearing.</p>
     *
     * @param taskName the name of the task to display in the dialog
     */
    private void showProgressDialog(String taskName) {
        if (progressStage != null && progressStage.isShowing()) {
            return; // Already showing
        }

        progressStage = new Stage();
        progressStage.initStyle(StageStyle.UTILITY);
        progressStage.setTitle("Processing...");
        progressStage.setResizable(false);

        progressIndicator = new ProgressIndicator();
        progressIndicator.setProgress(-1); // Indeterminate by default

        progressLabel = new Label(taskName);
        progressLabel.setStyle("-fx-font-size: 12px;");

        VBox content = new VBox(10);
        content.getChildren().addAll(progressLabel, progressIndicator);
        content.setStyle("-fx-padding: 20px; -fx-alignment: center;");

        javafx.scene.Scene scene = new javafx.scene.Scene(content, 250, 100);
        progressStage.setScene(scene);

        // Center on screen
        progressStage.centerOnScreen();
        progressStage.show();

        logger.debug("Progress dialog shown for task: {}", taskName);
    }

    /**
     * Hides the progress dialog if it is currently showing.
     *
     * <p>Closes the dialog stage and clears all references to the UI components
     * to allow garbage collection.</p>
     */
    private void hideProgressDialog() {
        if (progressStage != null && progressStage.isShowing()) {
            progressStage.close();
            progressStage = null;
            progressIndicator = null;
            progressLabel = null;
            logger.debug("Progress dialog hidden");
        }
    }

    /**
     * Displays an error dialog with the given title and message.
     *
     * <p>This method is used as a fallback when no custom error handler is provided
     * for a failed task.</p>
     *
     * @param title   the dialog title
     * @param message the error message to display
     */
    private void showErrorDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Updates the progress display for the currently running task.
     *
     * <p>This method can be called from any thread to update the progress indicator
     * and status message. The actual UI update is performed on the JavaFX Application Thread.</p>
     *
     * <p>If no progress dialog is currently showing, the update is silently ignored.</p>
     *
     * @param progress the progress value between 0.0 and 1.0, or a negative value for
     *                 indeterminate progress
     * @param message  the status message to display; may be null to leave the message unchanged
     */
    public void updateProgress(double progress, String message) {
        Platform.runLater(() -> {
            if (progressIndicator != null) {
                progressIndicator.setProgress(progress);
            }
            if (progressLabel != null && message != null) {
                progressLabel.setText(message);
            }
        });
    }

    /**
     * Checks whether a task is currently running with a visible progress dialog.
     *
     * <p>This method can be used to prevent starting a new task while another is
     * already in progress, or to conditionally enable/disable UI elements.</p>
     *
     * @return {@code true} if a progress dialog is currently visible, {@code false} otherwise
     */
    public boolean isTaskRunning() {
        return progressStage != null && progressStage.isShowing();
    }

    /**
     * Shuts down the ProgressManager and releases all resources.
     *
     * <p>This method should be called when the application is closing to ensure
     * proper cleanup of the executor service. Any running tasks will be interrupted.</p>
     *
     * <p>After calling this method, the ProgressManager should not be used again.
     * However, due to the singleton pattern, a new instance cannot be created;
     * the application should be restarted instead.</p>
     */
    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            logger.info("ProgressManager executor shutdown");
        }

        Platform.runLater(() -> hideProgressDialog());
    }

    /**
     * Returns the executor service used by this ProgressManager.
     *
     * <p>This method provides access to the underlying executor for advanced use cases
     * where manual task submission is needed without the automatic progress dialog.
     * Tasks submitted directly to this executor will not trigger the progress UI.</p>
     *
     * <p>For most use cases, prefer using {@link #executeWithProgress(String, Supplier, Consumer, Consumer)}
     * or {@link #executeWithProgressUpdates(String, Task, Consumer, Consumer)} instead.</p>
     *
     * @return the executor service used for background task execution
     */
    public ExecutorService getExecutor() {
        return executor;
    }
}