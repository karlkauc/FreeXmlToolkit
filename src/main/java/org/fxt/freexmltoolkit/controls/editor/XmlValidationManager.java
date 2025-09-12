package org.fxt.freexmltoolkit.controls.editor;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Tooltip;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.service.ThreadPoolManager;

import java.util.*;
import java.util.function.Consumer;

/**
 * Manages XML validation and error highlighting for the CodeArea.
 * This class handles live validation, error highlighting, and error tooltips.
 */
public class XmlValidationManager {

    private static final Logger logger = LogManager.getLogger(XmlValidationManager.class);

    private final CodeArea codeArea;
    private final ThreadPoolManager threadPoolManager;

    // Validation task management
    private Task<List<org.xml.sax.SAXParseException>> validationTask;

    // Error state
    private final Map<Integer, String> currentErrors = new HashMap<>();
    private Tooltip errorTooltip;
    private int lastTooltipLine = -1;

    // Callbacks
    private Consumer<Map<Integer, String>> errorCallback;
    private Runnable validationCompleteCallback;

    /**
     * Interface for providing validation services.
     */
    public interface ValidationService {
        List<org.xml.sax.SAXParseException> validateText(String text) throws Exception;
    }

    private ValidationService validationService;

    /**
     * Constructor for XmlValidationManager.
     *
     * @param codeArea          The CodeArea to manage validation for
     * @param threadPoolManager Thread pool manager for background operations
     */
    public XmlValidationManager(CodeArea codeArea, ThreadPoolManager threadPoolManager) {
        this.codeArea = codeArea;
        this.threadPoolManager = threadPoolManager;
        setupMouseEventHandlers();
    }

    /**
     * Sets the validation service to use for XML validation.
     *
     * @param validationService The validation service
     */
    public void setValidationService(ValidationService validationService) {
        this.validationService = validationService;
    }

    /**
     * Sets a callback to be called when errors are detected.
     *
     * @param errorCallback Callback that receives the current errors map
     */
    public void setErrorCallback(Consumer<Map<Integer, String>> errorCallback) {
        this.errorCallback = errorCallback;
    }

    /**
     * Sets a callback to be called when validation is complete.
     *
     * @param validationCompleteCallback Callback to run after validation
     */
    public void setValidationCompleteCallback(Runnable validationCompleteCallback) {
        this.validationCompleteCallback = validationCompleteCallback;
    }

    /**
     * Performs live XML validation and applies error highlighting.
     *
     * @param text The text to validate
     */
    public void performLiveValidation(String text) {
        if (text == null || text.isEmpty()) {
            currentErrors.clear();
            if (errorCallback != null) {
                errorCallback.accept(currentErrors);
            }
            return;
        }

        // Cancel any running validation task
        if (validationTask != null && validationTask.isRunning()) {
            validationTask.cancel();
        }

        // Create new background task for validation
        validationTask = new Task<List<org.xml.sax.SAXParseException>>() {
            @Override
            protected List<org.xml.sax.SAXParseException> call() throws Exception {
                if (isCancelled()) {
                    return new ArrayList<>();
                }

                // Use the provided validation service
                if (validationService != null) {
                    try {
                        return validationService.validateText(text);
                    } catch (Exception e) {
                        logger.debug("Validation error during live validation: {}", e.getMessage());
                        return new ArrayList<>();
                    }
                }
                return new ArrayList<>();
            }
        };

        validationTask.setOnSucceeded(event -> {
            List<org.xml.sax.SAXParseException> errors = validationTask.getValue();
            if (errors != null) {
                Platform.runLater(() -> {
                    applyErrorHighlighting(errors);
                    if (validationCompleteCallback != null) {
                        validationCompleteCallback.run();
                    }
                });
            }
        });

        validationTask.setOnFailed(event -> {
            logger.debug("Live validation task failed: {}", validationTask.getException().getMessage());
        });

        // Run the validation task using managed thread pool
        threadPoolManager.executeCPUIntensive("live-validation-" + System.currentTimeMillis(), () -> {
            Thread taskThread = new Thread(validationTask);
            taskThread.setName("LiveValidation-" + System.currentTimeMillis());
            taskThread.setDaemon(true);
            taskThread.start();
            return null;
        });
    }

    /**
     * Applies error highlighting to the CodeArea based on validation errors.
     *
     * @param errors List of SAX parsing exceptions
     */
    public void applyErrorHighlighting(List<org.xml.sax.SAXParseException> errors) {
        currentErrors.clear();

        if (errors == null || errors.isEmpty()) {
            if (errorCallback != null) {
                errorCallback.accept(currentErrors);
            }
            return;
        }

        // Process errors and store for tooltip functionality
        for (org.xml.sax.SAXParseException error : errors) {
            int lineNumber = error.getLineNumber();
            String errorMessage = error.getMessage();

            if (lineNumber > 0 && lineNumber <= codeArea.getParagraphs().size()) {
                currentErrors.put(lineNumber, errorMessage);
            }
        }

        // Notify callback about new errors
        if (errorCallback != null) {
            errorCallback.accept(currentErrors);
        }

        logger.debug("Applied error highlighting for {} errors", currentErrors.size());
    }

    /**
     * Gets the current errors map.
     *
     * @return Map of line numbers to error messages
     */
    public Map<Integer, String> getCurrentErrors() {
        return new HashMap<>(currentErrors);
    }

    /**
     * Clears all current errors and error highlighting.
     */
    public void clearErrors() {
        currentErrors.clear();
        hideErrorTooltip();
        if (errorCallback != null) {
            errorCallback.accept(currentErrors);
        }
    }

    /**
     * Shows error tooltip if present at the given line.
     *
     * @param lineNumber The line number to check for errors
     * @param screenX    Screen X coordinate for tooltip positioning
     * @param screenY    Screen Y coordinate for tooltip positioning
     */
    public void showErrorTooltipIfPresent(int lineNumber, double screenX, double screenY) {
        if (currentErrors.containsKey(lineNumber) && lastTooltipLine != lineNumber) {
            hideErrorTooltip(); // Hide any existing tooltip

            String errorMessage = currentErrors.get(lineNumber);
            errorTooltip = new Tooltip("Line " + lineNumber + ": " + errorMessage);
            errorTooltip.setAutoHide(true);
            errorTooltip.show(codeArea, screenX, screenY);
            lastTooltipLine = lineNumber;

            logger.debug("Showed error tooltip for line {}: {}", lineNumber, errorMessage);
        }
    }

    /**
     * Hides any currently visible error tooltip.
     */
    public void hideErrorTooltip() {
        if (errorTooltip != null) {
            errorTooltip.hide();
            errorTooltip = null;
        }
        lastTooltipLine = -1;
    }

    /**
     * Cancels any running validation task.
     */
    public void cancelValidation() {
        if (validationTask != null && validationTask.isRunning()) {
            validationTask.cancel();
        }
    }

    /**
     * Checks if a specific line has an error.
     *
     * @param lineNumber The line number to check (1-based)
     * @return true if the line has an error
     */
    public boolean hasError(int lineNumber) {
        return currentErrors.containsKey(lineNumber);
    }

    /**
     * Gets the error message for a specific line.
     *
     * @param lineNumber The line number (1-based)
     * @return The error message, or null if no error on that line
     */
    public String getErrorMessage(int lineNumber) {
        return currentErrors.get(lineNumber);
    }

    /**
     * Gets the total number of current errors.
     *
     * @return The number of errors
     */
    public int getErrorCount() {
        return currentErrors.size();
    }

    /**
     * Gets all lines that have errors.
     *
     * @return Set of line numbers that have errors
     */
    public Set<Integer> getErrorLines() {
        return new HashSet<>(currentErrors.keySet());
    }

    /**
     * Sets up mouse event handlers for error tooltip display.
     */
    private void setupMouseEventHandlers() {
        codeArea.setOnMouseMoved(event -> {
            // Only handle error tooltips if Ctrl is not down (to avoid conflict with Go-to-Definition)
            if (!event.isControlDown()) {
                var hit = codeArea.hit(event.getX(), event.getY());
                int characterIndex = hit.getCharacterIndex().orElse(-1);
                if (characterIndex >= 0) {
                    int lineNumber = codeArea.offsetToPosition(characterIndex,
                            org.fxmisc.richtext.model.TwoDimensional.Bias.Forward).getMajor() + 1;
                    showErrorTooltipIfPresent(lineNumber, event.getScreenX(), event.getScreenY());
                }
            }
        });

        codeArea.setOnMouseExited(event -> hideErrorTooltip());
    }

    /**
     * Updates validation settings and triggers re-validation if needed.
     *
     * @param enableLiveValidation Whether live validation should be enabled
     */
    public void setLiveValidationEnabled(boolean enableLiveValidation) {
        if (!enableLiveValidation) {
            cancelValidation();
            clearErrors();
        }
        // Store setting for future use if needed
        logger.debug("Live validation enabled: {}", enableLiveValidation);
    }
}