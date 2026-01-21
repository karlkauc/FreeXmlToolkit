package org.fxt.freexmltoolkit.service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Service interface for Schematron validation operations.
 * Provides methods to validate XML documents against Schematron rules.
 */
public interface SchematronService {

    /**
     * Validates an XML string against a Schematron file.
     *
     * @param xmlContent     The XML content to validate
     * @param schematronFile The Schematron file containing validation rules
     * @return List of validation errors, empty if validation passes
     * @throws SchematronLoadException if the Schematron file cannot be loaded or compiled
     */
    List<SchematronValidationError> validateXml(String xmlContent, File schematronFile) throws SchematronLoadException;

    /**
     * Validates an XML file against a Schematron file.
     *
     * @param xmlFile        The XML file to validate
     * @param schematronFile The Schematron file containing validation rules
     * @return List of validation errors, empty if validation passes
     * @throws SchematronLoadException if the Schematron file cannot be loaded or compiled
     */
    List<SchematronValidationError> validateXmlFile(File xmlFile, File schematronFile) throws SchematronLoadException;

    /**
     * Validates an XML file against a Schematron file with comprehensive result.
     *
     * @param xmlFile        The XML file to validate
     * @param schematronFile The Schematron file containing validation rules
     * @return Comprehensive validation result
     * @throws SchematronLoadException if the Schematron file cannot be loaded or compiled
     */
    SchematronValidationResult validateXmlWithSchematron(File xmlFile, File schematronFile) throws SchematronLoadException;

    /**
     * Checks if a file is a valid Schematron file.
     *
     * @param file The file to check
     * @return true if the file is a valid Schematron file, false otherwise
     */
    boolean isValidSchematronFile(File file);

    /**
     * Comprehensive validation result containing errors and warnings.
     * This class collects validation issues found during Schematron validation
     * and provides methods to query the validation state.
     */
    class SchematronValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        /**
         * Creates a new empty validation result.
         * Initially, the result contains no errors or warnings.
         */
        public SchematronValidationResult() {
            // Default constructor - lists are already initialized
        }

        /**
         * Adds an error message to this validation result.
         *
         * @param error the error message to add
         */
        public void addError(String error) {
            errors.add(error);
        }

        /**
         * Adds a warning message to this validation result.
         *
         * @param warning the warning message to add
         */
        public void addWarning(String warning) {
            warnings.add(warning);
        }

        /**
         * Returns a copy of all error messages in this validation result.
         *
         * @return a new list containing all error messages
         */
        public List<String> getErrors() {
            return new ArrayList<>(errors);
        }

        /**
         * Returns a copy of all warning messages in this validation result.
         *
         * @return a new list containing all warning messages
         */
        public List<String> getWarnings() {
            return new ArrayList<>(warnings);
        }

        /**
         * Checks if this validation result contains any errors.
         *
         * @return true if there is at least one error, false otherwise
         */
        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        /**
         * Checks if this validation result contains any warnings.
         *
         * @return true if there is at least one warning, false otherwise
         */
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }

    /**
     * Represents a Schematron validation error with detailed information.
     * @param message The error message
     * @param ruleId The rule ID
     * @param context The context path
     * @param lineNumber The line number
     * @param columnNumber The column number
     * @param severity The severity level
     */
    record SchematronValidationError(
            String message,
            String ruleId,
            String context,
            int lineNumber,
            int columnNumber,
            String severity
    ) {
    }
}
