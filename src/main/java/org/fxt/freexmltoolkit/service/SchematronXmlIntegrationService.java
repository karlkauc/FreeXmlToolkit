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
     * Constructor
     */
    public SchematronXmlIntegrationService() {
        logger.debug("SchematronXmlIntegrationService initialized");
    }

    /**
     * Initialize the integration service with controllers
     */
    public void initialize(MainController mainController, XmlUltimateController xmlController,
                           SchematronController schematronController) {
        this.mainController = mainController;
        this.xmlController = xmlController;
        this.schematronController = schematronController;

        logger.info("Integration service initialized with controllers");
    }

    /**
     * Set the current XML file and trigger integrations
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
     * Set the current Schematron file and trigger integrations
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
     * Validate current XML file against current Schematron file
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
     * Switch to XML editor with specific file
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
     * Switch to Schematron editor with specific file
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
     * Quick validate - validate current XML against a specific Schematron file
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
     * Generate Schematron rules from XML structure
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
     * Add integration listener
     */
    public void addIntegrationListener(IntegrationListener listener) {
        listeners.add(listener);
    }

    /**
     * Remove integration listener
     */
    public void removeIntegrationListener(IntegrationListener listener) {
        listeners.remove(listener);
    }

    /**
     * Enable/disable automatic validation
     */
    public void setAutoValidationEnabled(boolean enabled) {
        this.autoValidationEnabled = enabled;
        logger.debug("Auto validation enabled: {}", enabled);
    }

    /**
     * Get current files info
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
     * Interface for integration event listeners
     */
    public interface IntegrationListener {
        default void onXmlFileChanged(File xmlFile) {
        }

        default void onSchematronFileChanged(File schematronFile) {
        }

        default void onValidationComplete(ValidationResult result) {
        }
    }

    /**
     * Validation result container
     * @param valid Whether the validation was successful
     * @param message The validation message
     * @param errors List of errors
     * @param warnings List of warnings
     */
    public record ValidationResult(boolean valid, String message, List<String> errors, List<String> warnings) {
            public ValidationResult(boolean valid, String message, List<String> errors, List<String> warnings) {
                this.valid = valid;
                this.message = message;
                this.errors = new ArrayList<>(errors);
                this.warnings = new ArrayList<>(warnings);
            }

            @Override
            public List<String> errors() {
                return new ArrayList<>(errors);
            }

            @Override
            public List<String> warnings() {
                return new ArrayList<>(warnings);
            }
        }

    /**
     * Integration status container
     * @param xmlFile The currently loaded XML file
     * @param schematronFile The currently loaded Schematron file
     * @param autoValidationEnabled Whether auto-validation is enabled
     */
    public record IntegrationStatus(File xmlFile, File schematronFile, boolean autoValidationEnabled) {

        public boolean isBothFilesLoaded() {
                return xmlFile != null && schematronFile != null;
            }
        }
}