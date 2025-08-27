package org.fxt.freexmltoolkit.domain;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.io.File;

/**
 * Model class for test files in the Schematron test table.
 */
public class TestFile {

    private final File file;
    private final StringProperty filename;
    private final StringProperty status;
    private final IntegerProperty violations;
    private final IntegerProperty warnings;
    private final StringProperty lastTested;

    public TestFile(File file) {
        this.file = file;
        this.filename = new SimpleStringProperty(file.getName());
        this.status = new SimpleStringProperty("Not tested");
        this.violations = new SimpleIntegerProperty(0);
        this.warnings = new SimpleIntegerProperty(0);
        this.lastTested = new SimpleStringProperty("-");
    }

    // File
    public File getFile() {
        return file;
    }

    // Filename
    public String getFilename() {
        return filename.get();
    }

    public void setFilename(String filename) {
        this.filename.set(filename);
    }

    public StringProperty filenameProperty() {
        return filename;
    }

    // Status
    public String getStatus() {
        return status.get();
    }

    public void setStatus(String status) {
        this.status.set(status);
    }

    public StringProperty statusProperty() {
        return status;
    }

    // Violations
    public int getViolations() {
        return violations.get();
    }

    public void setViolations(int violations) {
        this.violations.set(violations);
    }

    public IntegerProperty violationsProperty() {
        return violations;
    }

    // Warnings
    public int getWarnings() {
        return warnings.get();
    }

    public void setWarnings(int warnings) {
        this.warnings.set(warnings);
    }

    public IntegerProperty warningsProperty() {
        return warnings;
    }

    // Last Tested
    public String getLastTested() {
        return lastTested.get();
    }

    public void setLastTested(String lastTested) {
        this.lastTested.set(lastTested);
    }

    public StringProperty lastTestedProperty() {
        return lastTested;
    }
}