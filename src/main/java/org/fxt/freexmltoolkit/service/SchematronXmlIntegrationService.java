package org.fxt.freexmltoolkit.service;

import javafx.application.Platform;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controller.MainController;
import org.fxt.freexmltoolkit.controller.SchematronController;
import org.fxt.freexmltoolkit.controller.XmlUltimateController;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service for integrating XML Editor and Schematron Editor functionality.
 * Provides cross-linking, validation workflows, and enhanced user experience.
 *
 * <p>This service manages the relationship between XML files and Schematron files,
 * enabling automatic validation when either file changes, and provides methods
 * to navigate between editors and trigger validation workflows.</p>
 */
public class SchematronXmlIntegrationService {

    private static final Logger logger = LogManager.getLogger(SchematronXmlIntegrationService.class);

    private MainController mainController;
    private XmlUltimateController xmlController;
    private SchematronController schematronController;

    // Integration state
    private File currentXmlFile;
    private File currentSchematronFile;
    private boolean autoValidationEnabled = true;
    private final List<IntegrationListener> listeners = new ArrayList<>();

    /**
     * Creates a new SchematronXmlIntegrationService instance.
     * The service is initialized with auto-validation enabled by default.
     * Call {@link #initialize(MainController, XmlUltimateController, SchematronController)}
     * to set up the controllers before using the service.
     */
    public SchematronXmlIntegrationService() {
        logger.debug("SchematronXmlIntegrationService initialized");
    }

    /**
     * Initializes the integration service with the required controllers.
     * This method must be called before using any integration features.
     *
     * @param mainController the main application controller for navigation
     * @param xmlController the XML editor controller
     * @param schematronController the Schematron editor controller
     */
    public void initialize(MainController mainController, XmlUltimateController xmlController,
                           SchematronController schematronController) {
        this.mainController = mainController;
        this.xmlController = xmlController;
        this.schematronController = schematronController;

        logger.info("Integration service initialized with controllers");
    }

    /**
     * Sets the current XML file and triggers integration workflows.
     * If auto-validation is enabled and a Schematron file is loaded,
     * validation will be performed automatically.
     *
     * @param xmlFile the XML file to set as current, or null to clear
     */
    public void setCurrentXmlFile(File xmlFile) {
        this.currentXmlFile = xmlFile;
        notifyXmlFileChanged(xmlFile);

        if (autoValidationEnabled && currentSchematronFile != null) {
            performAutoValidation();
        }

        logger.debug("Current XML file set: {}", xmlFile != null ? xmlFile.getName() : "null");
    }

    /**
     * Sets the current Schematron file and triggers integration workflows.
     * If auto-validation is enabled and an XML file is loaded,
     * validation will be performed automatically.
     *
     * @param schematronFile the Schematron file to set as current, or null to clear
     */
    public void setCurrentSchematronFile(File schematronFile) {
        this.currentSchematronFile = schematronFile;
        notifySchematronFileChanged(schematronFile);

        if (autoValidationEnabled && currentXmlFile != null) {
            performAutoValidation();
        }

        logger.debug("Current Schematron file set: {}", schematronFile != null ? schematronFile.getName() : "null");
    }

    /**
     * Validates the current XML file against the current Schematron file asynchronously.
     * Both files must be loaded for validation to proceed. Listeners are notified
     * when validation completes.
     *
     * @return a CompletableFuture containing the validation result
     */
    public CompletableFuture<ValidationResult> validateCurrentFiles() {
        return CompletableFuture.supplyAsync(() -> {
            if (currentXmlFile == null || currentSchematronFile == null) {
                return new ValidationResult(false, "Both XML and Schematron files must be loaded",
                        new ArrayList<>(), new ArrayList<>());
            }

            try {
                SchematronService schematronService = new SchematronServiceImpl();
                var result = schematronService.validateXmlWithSchematron(currentXmlFile, currentSchematronFile);

                ValidationResult validationResult = new ValidationResult(
                        !result.hasErrors(),
                        result.hasErrors() ? "Validation failed" : "Validation successful",
                        result.getErrors(),
                        result.getWarnings()
                );

                Platform.runLater(() -> notifyValidationComplete(validationResult));

                return validationResult;

            } catch (Exception e) {
                logger.error("Error during integrated validation", e);
                ValidationResult errorResult = new ValidationResult(false,
                        "Validation error: " + e.getMessage(), new ArrayList<>(), new ArrayList<>());
                Platform.runLater(() -> notifyValidationComplete(errorResult));
                return errorResult;
            }
        });
    }

    /**
     * Opens the specified XML file in the XML editor.
     * Switches to the XML editor tab and loads the file.
     *
     * @param xmlFile the XML file to open in the editor
     */
    public void openXmlInEditor(File xmlFile) {
        if (mainController != null) {
            Platform.runLater(() -> {
                mainController.switchToXmlViewAndLoadFile(xmlFile);
                setCurrentXmlFile(xmlFile);
            });
        }
    }

    /**
     * Opens the specified Schematron file in the Schematron editor.
     * Switches to the Schematron editor tab and loads the file.
     *
     * @param schematronFile the Schematron file to open in the editor
     */
    public void openSchematronInEditor(File schematronFile) {
        if (mainController != null) {
            Platform.runLater(() -> {
                mainController.switchToSchematronViewAndLoadFile(schematronFile);
                setCurrentSchematronFile(schematronFile);
            });
        }
    }

    /**
     * Performs a quick validation of an XML file against a Schematron file.
     * The validation runs asynchronously and results are displayed to the user.
     *
     * @param xmlFile the XML file to validate
     * @param schematronFile the Schematron file containing the validation rules
     */
    public void quickValidate(File xmlFile, File schematronFile) {
        CompletableFuture.runAsync(() -> {
            try {
                SchematronService schematronService = new SchematronServiceImpl();
                var result = schematronService.validateXmlWithSchematron(xmlFile, schematronFile);

                Platform.runLater(() -> {
                    if (result.hasErrors()) {
                        showValidationResults("Validation Results",
                                "Found " + result.getErrors().size() + " error(s) and " +
                                        result.getWarnings().size() + " warning(s)", result);
                    } else {
                        showValidationResults("Validation Results",
                                "Validation successful! No errors found.", result);
                    }
                });

            } catch (Exception e) {
                logger.error("Quick validation failed", e);
                Platform.runLater(() ->
                        showError("Validation Error", "Quick validation failed: " + e.getMessage()));
            }
        });
    }

    /**
     * Generates a basic Schematron template from an XML file structure.
     * The generated template includes placeholder rules that can be customized.
     *
     * @param xmlFile the XML file to analyze for generating Schematron rules
     * @return a CompletableFuture containing the generated Schematron XML as a string
     */
    public CompletableFuture<String> generateSchematronFromXml(File xmlFile) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // This is a simplified schema generator
                // In a full implementation, this would analyze XML structure
                String xmlName = xmlFile.getName().replaceFirst("[.][^.]+$", "");

                String schematron = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<schema xmlns=\"http://purl.oclc.org/dsdl/schematron\"\n" +
                        "        xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"\n" +
                        "        queryBinding=\"xslt2\">\n\n" +
                        "    <title>Generated Schematron for " + xmlName + "</title>\n\n" +
                        "    <!-- Generated from: " + xmlFile.getName() + " -->\n" +
                        "    <!-- Add namespace declarations as needed -->\n" +
                        "    <!-- <ns prefix=\"example\" uri=\"http://example.org/ns\"/> -->\n\n" +
                        "    <pattern>\n" +
                        "        <title>Basic Structure Rules</title>\n\n" +
                        "        <rule context=\"/*\">\n" +
                        "            <assert test=\"true()\">Root element validation placeholder</assert>\n" +
                        "        </rule>\n\n" +
                        "        <!-- Add more rules based on XML structure -->\n" +
                        "        <!-- <rule context=\"//specific-element\">\n" +
                        "             <assert test=\"@required-attribute\">Required attribute missing</assert>\n" +
                        "        </rule> -->\n\n" +
                        "    </pattern>\n\n" +
                        "</schema>\n";

                logger.info("Generated basic Schematron template for: {}", xmlFile.getName());
                return schematron;

            } catch (Exception e) {
                logger.error("Error generating Schematron from XML", e);
                throw new RuntimeException("Failed to generate Schematron: " + e.getMessage());
            }
        });
    }

    /**
     * Adds an integration listener to receive notifications about file changes
     * and validation events.
     *
     * @param listener the listener to add
     */
    public void addIntegrationListener(IntegrationListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a previously registered integration listener.
     *
     * @param listener the listener to remove
     */
    public void removeIntegrationListener(IntegrationListener listener) {
        listeners.remove(listener);
    }

    /**
     * Enables or disables automatic validation.
     * When enabled, validation is triggered automatically when either
     * the XML or Schematron file changes.
     *
     * @param enabled true to enable auto-validation, false to disable
     */
    public void setAutoValidationEnabled(boolean enabled) {
        this.autoValidationEnabled = enabled;
        logger.debug("Auto validation enabled: {}", enabled);
    }

    /**
     * Gets the current integration status including loaded files
     * and auto-validation state.
     *
     * @return the current integration status
     */
    public IntegrationStatus getStatus() {
        return new IntegrationStatus(currentXmlFile, currentSchematronFile, autoValidationEnabled);
    }

    // ========== Private Helper Methods ==========

    /**
     * Perform automatic validation when files change
     */
    private void performAutoValidation() {
        if (currentXmlFile != null && currentSchematronFile != null) {
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(500); // Small delay to avoid excessive validation
                    validateCurrentFiles().thenAccept(result -> {
                        // Auto validation results are typically less intrusive
                        logger.debug("Auto validation completed: {}", result.valid());
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
    }

    /**
     * Notify listeners of XML file changes
     */
    private void notifyXmlFileChanged(File xmlFile) {
        for (IntegrationListener listener : listeners) {
            try {
                listener.onXmlFileChanged(xmlFile);
            } catch (Exception e) {
                logger.warn("Error notifying listener of XML file change", e);
            }
        }
    }

    /**
     * Notify listeners of Schematron file changes
     */
    private void notifySchematronFileChanged(File schematronFile) {
        for (IntegrationListener listener : listeners) {
            try {
                listener.onSchematronFileChanged(schematronFile);
            } catch (Exception e) {
                logger.warn("Error notifying listener of Schematron file change", e);
            }
        }
    }

    /**
     * Notify listeners of validation completion
     */
    private void notifyValidationComplete(ValidationResult result) {
        for (IntegrationListener listener : listeners) {
            try {
                listener.onValidationComplete(result);
            } catch (Exception e) {
                logger.warn("Error notifying listener of validation completion", e);
            }
        }
    }

    /**
     * Show validation results (simplified - in full implementation would use proper UI)
     */
    private void showValidationResults(String title, String message,
                                       SchematronService.SchematronValidationResult result) {
        // In a full implementation, this would show results in a proper dialog
        // For now, just log the results
        logger.info("Validation Results: {}", message);
        if (result.hasErrors()) {
            logger.info("Errors: {}", result.getErrors());
        }
        if (result.hasWarnings()) {
            logger.info("Warnings: {}", result.getWarnings());
        }
    }

    /**
     * Show error message (simplified)
     */
    private void showError(String title, String message) {
        logger.error("{}: {}", title, message);
    }

    // ========== Inner Classes and Interfaces ==========

    /**
     * Interface for receiving integration event notifications.
     * Implement this interface to be notified when XML or Schematron files change,
     * or when validation completes.
     */
    public interface IntegrationListener {
        /**
         * Called when the current XML file changes.
         *
         * @param xmlFile the new XML file, or null if cleared
         */
        default void onXmlFileChanged(File xmlFile) {
        }

        /**
         * Called when the current Schematron file changes.
         *
         * @param schematronFile the new Schematron file, or null if cleared
         */
        default void onSchematronFileChanged(File schematronFile) {
        }

        /**
         * Called when a validation operation completes.
         *
         * @param result the validation result containing success status, messages, errors, and warnings
         */
        default void onValidationComplete(ValidationResult result) {
        }
    }

    /**
     * Container for Schematron validation results.
     * This record holds the outcome of a validation operation including
     * the validation status, descriptive message, and any errors or warnings found.
     *
     * @param valid true if validation passed with no errors, false otherwise
     * @param message a descriptive message about the validation outcome
     * @param errors list of error messages found during validation
     * @param warnings list of warning messages found during validation
     */
    public record ValidationResult(boolean valid, String message, List<String> errors, List<String> warnings) {
        /**
         * Creates a new ValidationResult with defensive copies of the error and warning lists.
         *
         * @param valid true if validation passed with no errors, false otherwise
         * @param message a descriptive message about the validation outcome
         * @param errors list of error messages found during validation
         * @param warnings list of warning messages found during validation
         */
        public ValidationResult(boolean valid, String message, List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.message = message;
            this.errors = new ArrayList<>(errors);
            this.warnings = new ArrayList<>(warnings);
        }

        /**
         * Returns a defensive copy of the errors list.
         *
         * @return a new list containing all error messages
         */
        @Override
        public List<String> errors() {
            return new ArrayList<>(errors);
        }

        /**
         * Returns a defensive copy of the warnings list.
         *
         * @return a new list containing all warning messages
         */
        @Override
        public List<String> warnings() {
            return new ArrayList<>(warnings);
        }
    }

    /**
     * Container for the current integration status.
     * Provides information about which files are currently loaded
     * and whether automatic validation is enabled.
     *
     * @param xmlFile the currently loaded XML file, or null if none
     * @param schematronFile the currently loaded Schematron file, or null if none
     * @param autoValidationEnabled true if automatic validation is enabled
     */
    public record IntegrationStatus(File xmlFile, File schematronFile, boolean autoValidationEnabled) {

        /**
         * Checks whether both XML and Schematron files are loaded.
         *
         * @return true if both files are loaded, false otherwise
         */
        public boolean isBothFilesLoaded() {
            return xmlFile != null && schematronFile != null;
        }
    }
}