package org.fxt.freexmltoolkit.controls.v2.editor.managers;

import javafx.application.Platform;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.fxt.freexmltoolkit.controls.v2.editor.core.EditorContext;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.xml.sax.SAXException;
import org.xml.sax.ErrorHandler;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Validation manager for XmlCodeEditorV2.
 * Clean-room V2 implementation with async validation and error highlighting.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Async XML well-formedness validation (SAXParser)</li>
 *   <li>XSD schema validation via SchemaProvider</li>
 *   <li>Error highlighting with red underline</li>
 *   <li>Background processing to avoid UI blocking</li>
 *   <li>Error model with line/column/message</li>
 * </ul>
 */
public class ValidationManagerV2 {

    private static final Logger logger = LogManager.getLogger(ValidationManagerV2.class);

    private final EditorContext editorContext;
    private final CodeArea codeArea;
    private final ExecutorService executor;

    // Validation state
    private List<ValidationError> currentErrors = new ArrayList<>();
    private boolean validationEnabled = true;

    // Optional: CombinedStyleManager for proper error highlighting
    private CombinedStyleManager styleManager;

    /**
     * Creates a new ValidationManagerV2.
     *
     * @param editorContext the editor context
     */
    public ValidationManagerV2(EditorContext editorContext) {
        this.editorContext = editorContext;
        this.codeArea = editorContext.getCodeArea();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ValidationV2");
            t.setDaemon(true);
            return t;
        });

        logger.info("ValidationManagerV2 created");
    }

    /**
     * Validates the XML content asynchronously.
     * Performs both well-formedness and XSD schema validation (if schema is available).
     *
     * @param xmlContent the XML content to validate
     * @return CompletableFuture with validation errors
     */
    public CompletableFuture<List<ValidationError>> validateAsync(String xmlContent) {
        if (!validationEnabled || xmlContent == null || xmlContent.trim().isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        return CompletableFuture.supplyAsync(() -> {
            List<ValidationError> errors = new ArrayList<>();

            try {
                // Step 1: Well-formedness validation
                errors.addAll(validateWellFormedness(xmlContent));

                // Step 2: XSD schema validation (if no well-formedness errors and schema available)
                if (errors.isEmpty() && hasSchema()) {
                    errors.addAll(validateAgainstSchema(xmlContent));
                }

            } catch (Exception e) {
                logger.error("Validation error", e);
                errors.add(new ValidationError(0, 0, "Validation failed: " + e.getMessage(), ValidationSeverity.ERROR));
            }

            return errors;
        }, executor);
    }

    /**
     * Validates the current editor content and applies error highlighting.
     */
    public void validate() {
        if (!validationEnabled) {
            return;
        }

        String xmlContent = editorContext.getText();

        validateAsync(xmlContent).thenAccept(errors -> {
            this.currentErrors = errors;

            Platform.runLater(() -> {
                applyErrorHighlighting(errors);
                fireValidationCompleted(errors);
            });
        });
    }

    /**
     * Performs live validation.
     * Delegates to validate() method.
     *
     * @param text the text to validate
     */
    public void performLiveValidation(String text) {
        validate();
    }

    /**
     * Validates XML well-formedness using SAXParser.
     *
     * @param xmlContent the XML content
     * @return list of validation errors
     */
    private List<ValidationError> validateWellFormedness(String xmlContent) {
        List<ValidationError> errors = new ArrayList<>();

        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            SAXParser parser = factory.newSAXParser();

            // Custom error handler
            parser.parse(
                    new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)),
                    new org.xml.sax.helpers.DefaultHandler() {
                        @Override
                        public void error(SAXParseException e) {
                            errors.add(new ValidationError(
                                    e.getLineNumber(),
                                    e.getColumnNumber(),
                                    "XML Error: " + e.getMessage(),
                                    ValidationSeverity.ERROR
                            ));
                        }

                        @Override
                        public void fatalError(SAXParseException e) {
                            errors.add(new ValidationError(
                                    e.getLineNumber(),
                                    e.getColumnNumber(),
                                    "XML Fatal Error: " + e.getMessage(),
                                    ValidationSeverity.ERROR
                            ));
                        }

                        @Override
                        public void warning(SAXParseException e) {
                            errors.add(new ValidationError(
                                    e.getLineNumber(),
                                    e.getColumnNumber(),
                                    "XML Warning: " + e.getMessage(),
                                    ValidationSeverity.WARNING
                            ));
                        }
                    }
            );

        } catch (Exception e) {
            logger.debug("Well-formedness validation failed: {}", e.getMessage());
            errors.add(new ValidationError(0, 0, "XML is not well-formed: " + e.getMessage(), ValidationSeverity.ERROR));
        }

        return errors;
    }

    /**
     * Validates XML against XSD schema.
     *
     * @param xmlContent the XML content
     * @return list of validation errors
     */
    private List<ValidationError> validateAgainstSchema(String xmlContent) {
        List<ValidationError> errors = new ArrayList<>();

        try {
            // Get schema from SchemaProvider
            if (!hasSchema()) {
                return errors;
            }

            // Get XSD file path from schema provider
            String xsdFilePath = editorContext.getSchemaProvider().getXsdFilePath();
            if (xsdFilePath == null || xsdFilePath.isEmpty()) {
                logger.debug("XSD file path not available");
                return errors;
            }

            File schemaFile = new File(xsdFilePath);
            if (!schemaFile.exists()) {
                logger.warn("XSD file not found: {}", xsdFilePath);
                return errors;
            }

            // Create schema factory and load XSD
            SchemaFactory schemaFactory = SchemaFactory.newInstance(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Schema schema = schemaFactory.newSchema(schemaFile);

            // Create validator
            Validator validator = schema.newValidator();

            // Custom error handler to collect validation errors
            List<ValidationError> schemaErrors = new ArrayList<>();
            validator.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException exception) {
                    schemaErrors.add(new ValidationError(
                        exception.getLineNumber(),
                        exception.getColumnNumber(),
                        "Warning: " + exception.getMessage(),
                        ValidationSeverity.WARNING
                    ));
                }

                @Override
                public void error(SAXParseException exception) {
                    schemaErrors.add(new ValidationError(
                        exception.getLineNumber(),
                        exception.getColumnNumber(),
                        exception.getMessage(),
                        ValidationSeverity.ERROR
                    ));
                }

                @Override
                public void fatalError(SAXParseException exception) {
                    schemaErrors.add(new ValidationError(
                        exception.getLineNumber(),
                        exception.getColumnNumber(),
                        "Fatal: " + exception.getMessage(),
                        ValidationSeverity.ERROR
                    ));
                }
            });

            // Validate XML against XSD
            validator.validate(new StreamSource(new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8))));

            errors.addAll(schemaErrors);
            logger.debug("XSD validation completed: {} errors found", schemaErrors.size());

        } catch (SAXException e) {
            logger.debug("Schema parsing failed: {}", e.getMessage());
            errors.add(new ValidationError(0, 0, "Schema error: " + e.getMessage(), ValidationSeverity.ERROR));
        } catch (Exception e) {
            logger.debug("Schema validation failed: {}", e.getMessage());
            errors.add(new ValidationError(0, 0, "Validation error: " + e.getMessage(), ValidationSeverity.ERROR));
        }

        return errors;
    }

    /**
     * Sets the combined style manager for proper error+syntax highlighting.
     *
     * @param styleManager the combined style manager
     */
    public void setStyleManager(CombinedStyleManager styleManager) {
        this.styleManager = styleManager;
    }

    /**
     * Applies error highlighting to the CodeArea.
     * Uses red underline style for error ranges.
     *
     * @param errors the validation errors
     */
    private void applyErrorHighlighting(List<ValidationError> errors) {
        if (errors.isEmpty()) {
            clearErrorHighlighting();
            return;
        }

        try {
            String text = codeArea.getText();

            // Build error ranges map
            Map<Integer, Integer> errorRanges = new HashMap<>();

            for (ValidationError error : errors) {
                if (error.line() <= 0) {
                    continue;
                }

                int errorPos = getPositionFromLineColumn(text, error.line(), error.column());
                if (errorPos >= 0) {
                    // Error length: highlight entire word or minimum 5 characters
                    int errorLength = calculateErrorLength(text, errorPos);
                    errorRanges.put(errorPos, errorLength);
                }
            }

            // Use CombinedStyleManager if available
            if (styleManager != null) {
                styleManager.setErrorRanges(errorRanges);
            } else {
                // Fallback: apply error styles directly (will overwrite syntax highlighting)
                applyErrorStylesDirectly(errorRanges, text);
            }

            logger.debug("Applied error highlighting for {} errors", errorRanges.size());

        } catch (Exception e) {
            logger.error("Error applying error highlighting", e);
        }
    }

    /**
     * Calculates the error highlight length.
     * Highlights entire word or minimum 5 characters.
     *
     * @param text the text
     * @param errorPos the error position
     * @return error length
     */
    private int calculateErrorLength(String text, int errorPos) {
        int endPos = errorPos;

        // Find end of word (alphanumeric + underscore)
        while (endPos < text.length() &&
               (Character.isLetterOrDigit(text.charAt(endPos)) || text.charAt(endPos) == '_' || text.charAt(endPos) == ':')) {
            endPos++;
        }

        int wordLength = endPos - errorPos;

        // Minimum 5 characters, maximum 50
        return Math.max(5, Math.min(50, wordLength));
    }

    /**
     * Applies error styles directly (fallback when no CombinedStyleManager).
     *
     * @param errorRanges map of error position to length
     * @param text the text
     */
    private void applyErrorStylesDirectly(Map<Integer, Integer> errorRanges, String text) {
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        int currentPos = 0;

        // Sort error positions
        List<Integer> sortedPositions = new ArrayList<>(errorRanges.keySet());
        Collections.sort(sortedPositions);

        for (int errorPos : sortedPositions) {
            int errorLength = errorRanges.get(errorPos);

            // Add normal style up to error position
            if (errorPos > currentPos) {
                spansBuilder.add(Collections.emptyList(), errorPos - currentPos);
            }

            // Add error style
            spansBuilder.add(Collections.singleton("validation-error"), errorLength);
            currentPos = errorPos + errorLength;
        }

        // Add remaining text with normal style
        if (currentPos < text.length()) {
            spansBuilder.add(Collections.emptyList(), text.length() - currentPos);
        }

        StyleSpans<Collection<String>> errorSpans = spansBuilder.create();
        codeArea.setStyleSpans(0, errorSpans);
    }

    /**
     * Clears error highlighting from the CodeArea.
     */
    private void clearErrorHighlighting() {
        try {
            if (styleManager != null) {
                styleManager.clearErrors();
            } else {
                // Fallback: clear all styles
                String text = codeArea.getText();
                if (text != null && !text.isEmpty()) {
                    StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
                    spansBuilder.add(Collections.emptyList(), text.length());
                    codeArea.setStyleSpans(0, spansBuilder.create());
                }
            }
        } catch (Exception e) {
            logger.debug("Error clearing error highlighting: {}", e.getMessage());
        }
    }

    /**
     * Calculates character position from line and column.
     *
     * @param text   the text
     * @param line   the line number (1-based)
     * @param column the column number (1-based)
     * @return the character position or -1 if invalid
     */
    private int getPositionFromLineColumn(String text, int line, int column) {
        if (line <= 0 || column < 0) {
            return -1;
        }

        int currentLine = 1;
        int pos = 0;

        while (pos < text.length() && currentLine < line) {
            if (text.charAt(pos) == '\n') {
                currentLine++;
            }
            pos++;
        }

        // Add column offset
        pos += Math.max(0, column - 1);

        return pos < text.length() ? pos : -1;
    }

    /**
     * Checks if a schema is available for validation.
     *
     * @return true if schema is available
     */
    private boolean hasSchema() {
        return editorContext.getSchemaProvider() != null &&
               editorContext.getSchemaProvider().hasSchema();
    }

    /**
     * Fires validation completed event.
     *
     * @param errors the validation errors
     */
    private void fireValidationCompleted(List<ValidationError> errors) {
        boolean hasErrors = !errors.isEmpty();
        int errorCount = errors.size();

        logger.debug("Validation completed with {} errors", errorCount);

        // Fire event via EditorEventBus
        editorContext.getEventBus().publish(
            new org.fxt.freexmltoolkit.controls.v2.editor.core.EditorEvent.ValidationCompletedEvent(
                hasErrors,
                errorCount
            )
        );
    }

    /**
     * Gets the current validation errors.
     *
     * @return list of validation errors
     */
    public List<ValidationError> getCurrentErrors() {
        return new ArrayList<>(currentErrors);
    }

    /**
     * Checks if validation is enabled.
     *
     * @return true if enabled
     */
    public boolean isValidationEnabled() {
        return validationEnabled;
    }

    /**
     * Enables or disables validation.
     *
     * @param enabled true to enable, false to disable
     */
    public void setValidationEnabled(boolean enabled) {
        this.validationEnabled = enabled;
        if (!enabled) {
            clearErrorHighlighting();
            currentErrors.clear();
        }
    }

    /**
     * Refreshes validation (re-validates current content).
     */
    public void refresh() {
        validate();
    }

    /**
     * Shuts down the executor service.
     * Should be called when the editor is closed.
     */
    public void shutdown() {
        executor.shutdown();
        logger.debug("ValidationManagerV2 shut down");
    }

    /**
     * Validation error record.
     *
     * @param line     the line number (1-based)
     * @param column   the column number (1-based)
     * @param message  the error message
     * @param severity the severity level
     */
    public record ValidationError(int line, int column, String message, ValidationSeverity severity) {
    }

    /**
     * Validation severity levels.
     */
    public enum ValidationSeverity {
        ERROR,
        WARNING,
        INFO
    }
}
