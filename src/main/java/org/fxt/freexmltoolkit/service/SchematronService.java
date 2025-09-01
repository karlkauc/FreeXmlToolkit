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
     * Comprehensive validation result containing errors and warnings
     */
    class SchematronValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        public void addError(String error) {
            errors.add(error);
        }

        public void addWarning(String warning) {
            warnings.add(warning);
        }

        public List<String> getErrors() {
            return new ArrayList<>(errors);
        }

        public List<String> getWarnings() {
            return new ArrayList<>(warnings);
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }

    /**
     * Represents a Schematron validation error with detailed information.
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
