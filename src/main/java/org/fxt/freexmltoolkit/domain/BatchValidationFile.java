/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.domain;

import javafx.beans.property.*;
import org.xml.sax.SAXParseException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a file to be validated during batch XSD validation.
 * Uses JavaFX properties for binding to TableView columns.
 */
public class BatchValidationFile {

    private final ObjectProperty<File> xmlFile = new SimpleObjectProperty<>();
    private final ObjectProperty<File> xsdFile = new SimpleObjectProperty<>();
    private final ObjectProperty<ValidationStatus> status = new SimpleObjectProperty<>(ValidationStatus.PENDING);
    private final IntegerProperty errorCount = new SimpleIntegerProperty(0);
    private final LongProperty validationTimeMs = new SimpleLongProperty(0);
    private final StringProperty errorMessage = new SimpleStringProperty();

    private List<SAXParseException> errors = new ArrayList<>();

    /**
     * Creates a new BatchValidationFile for the given XML file.
     *
     * @param xmlFile the XML file to validate
     */
    public BatchValidationFile(File xmlFile) {
        this.xmlFile.set(xmlFile);
    }

    /**
     * Creates a new BatchValidationFile with a specific XSD file.
     *
     * @param xmlFile the XML file to validate
     * @param xsdFile the XSD schema to validate against (null for auto-detect)
     */
    public BatchValidationFile(File xmlFile, File xsdFile) {
        this.xmlFile.set(xmlFile);
        this.xsdFile.set(xsdFile);
    }

    // XML File property
    /**
     * Gets the XML file.
     *
     * @return The XML file.
     */
    public File getXmlFile() {
        return xmlFile.get();
    }

    /**
     * Sets the XML file.
     *
     * @param file The XML file to set.
     */
    public void setXmlFile(File file) {
        this.xmlFile.set(file);
    }

    /**
     * Returns the XML file property for binding.
     *
     * @return the XML file property for binding
     */
    public ObjectProperty<File> xmlFileProperty() {
        return xmlFile;
    }

    // XSD File property
    /**
     * Gets the XSD file.
     *
     * @return The XSD file.
     */
    public File getXsdFile() {
        return xsdFile.get();
    }

    /**
     * Sets the XSD file.
     *
     * @param file The XSD file to set.
     */
    public void setXsdFile(File file) {
        this.xsdFile.set(file);
    }

    /**
     * Returns the XSD file property for binding.
     *
     * @return the XSD file property for binding
     */
    public ObjectProperty<File> xsdFileProperty() {
        return xsdFile;
    }

    // Status property
    /**
     * Gets the validation status.
     *
     * @return The status.
     */
    public ValidationStatus getStatus() {
        return status.get();
    }

    /**
     * Sets the validation status.
     *
     * @param status The status to set.
     */
    public void setStatus(ValidationStatus status) {
        this.status.set(status);
    }

    /**
     * Returns the validation status property for binding.
     *
     * @return the validation status property for binding
     */
    public ObjectProperty<ValidationStatus> statusProperty() {
        return status;
    }

    // Error count property
    /**
     * Gets the error count.
     *
     * @return The error count.
     */
    public int getErrorCount() {
        return errorCount.get();
    }

    /**
     * Sets the error count.
     *
     * @param count The error count to set.
     */
    public void setErrorCount(int count) {
        this.errorCount.set(count);
    }

    /**
     * Returns the error count property for binding.
     *
     * @return the error count property for binding
     */
    public IntegerProperty errorCountProperty() {
        return errorCount;
    }

    // Validation time property
    /**
     * Gets the validation time in milliseconds.
     *
     * @return The validation time.
     */
    public long getValidationTimeMs() {
        return validationTimeMs.get();
    }

    /**
     * Sets the validation time in milliseconds.
     *
     * @param timeMs The validation time to set.
     */
    public void setValidationTimeMs(long timeMs) {
        this.validationTimeMs.set(timeMs);
    }

    /**
     * Returns the validation time property for binding.
     *
     * @return the validation time property for binding
     */
    public LongProperty validationTimeMsProperty() {
        return validationTimeMs;
    }

    // Error message property (for non-validation errors)
    /**
     * Gets the error message.
     *
     * @return The error message.
     */
    public String getErrorMessage() {
        return errorMessage.get();
    }

    /**
     * Sets the error message.
     *
     * @param message The error message to set.
     */
    public void setErrorMessage(String message) {
        this.errorMessage.set(message);
    }

    /**
     * Returns the error message property for binding.
     *
     * @return the error message property for binding
     */
    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    // Errors list (not a JavaFX property, stored separately)
    /**
     * Gets the list of validation errors.
     *
     * @return The list of errors.
     */
    public List<SAXParseException> getErrors() {
        return errors;
    }

    /**
     * Sets the list of validation errors.
     *
     * @param errors The list of errors to set.
     */
    public void setErrors(List<SAXParseException> errors) {
        this.errors = errors != null ? errors : new ArrayList<>();
        this.errorCount.set(this.errors.size());
    }

    /**
     * Gets the filename without path for display purposes.
     *
     * @return the filename
     */
    public String getFileName() {
        File file = getXmlFile();
        return file != null ? file.getName() : "";
    }

    /**
     * Gets the parent path for display purposes.
     *
     * @return the parent directory path
     */
    public String getFilePath() {
        File file = getXmlFile();
        return file != null ? file.getParent() : "";
    }

    /**
     * Gets the XSD filename for display purposes.
     *
     * @return the XSD filename or "Auto-detect" if not set
     */
    public String getXsdFileName() {
        File xsd = getXsdFile();
        return xsd != null ? xsd.getName() : "Auto-detect";
    }

    /**
     * Gets a human-readable duration string.
     *
     * @return formatted duration (e.g., "125 ms")
     */
    public String getDurationText() {
        long ms = getValidationTimeMs();
        if (ms == 0) {
            return "-";
        } else if (ms < 1000) {
            return ms + " ms";
        } else {
            return String.format("%.2f s", ms / 1000.0);
        }
    }

    /**
     * Resets the validation state for re-validation.
     */
    public void reset() {
        setStatus(ValidationStatus.PENDING);
        setErrorCount(0);
        setValidationTimeMs(0);
        setErrorMessage(null);
        this.errors = new ArrayList<>();
    }

    @Override
    public String toString() {
        return getFileName();
    }
}
