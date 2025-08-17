package org.fxt.freexmltoolkit.service;

import java.io.File;
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
     */
    List<SchematronValidationError> validateXml(String xmlContent, File schematronFile);

    /**
     * Validates an XML file against a Schematron file.
     *
     * @param xmlFile        The XML file to validate
     * @param schematronFile The Schematron file containing validation rules
     * @return List of validation errors, empty if validation passes
     */
    List<SchematronValidationError> validateXmlFile(File xmlFile, File schematronFile);

    /**
     * Checks if a file is a valid Schematron file.
     *
     * @param file The file to check
     * @return true if the file is a valid Schematron file, false otherwise
     */
    boolean isValidSchematronFile(File file);

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
