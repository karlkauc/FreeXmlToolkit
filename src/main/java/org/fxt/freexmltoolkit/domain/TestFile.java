package org.fxt.freexmltoolkit.domain;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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

    // Detailed test results
    private List<TestResult> detailedResults;
    private String testDuration;

    public TestFile(File file) {
        this.file = file;
        this.filename = new SimpleStringProperty(file.getName());
        this.status = new SimpleStringProperty("Not tested");
        this.violations = new SimpleIntegerProperty(0);
        this.warnings = new SimpleIntegerProperty(0);
        this.lastTested = new SimpleStringProperty("-");
        this.detailedResults = new ArrayList<>();
        this.testDuration = "0ms";
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

    // Detailed Results
    public List<TestResult> getDetailedResults() {
        return detailedResults;
    }

    public void setDetailedResults(List<TestResult> detailedResults) {
        this.detailedResults = detailedResults != null ? detailedResults : new ArrayList<>();
    }

    public void addTestResult(TestResult result) {
        if (this.detailedResults == null) {
            this.detailedResults = new ArrayList<>();
        }
        this.detailedResults.add(result);
    }

    public void clearDetailedResults() {
        if (this.detailedResults != null) {
            this.detailedResults.clear();
        }
    }

    // Test Duration
    public String getTestDuration() {
        return testDuration;
    }

    public void setTestDuration(String testDuration) {
        this.testDuration = testDuration != null ? testDuration : "0ms";
    }

    /**
     * Inner class to represent individual test results
     *
     * @param ruleId The rule ID
     * @param message The error message
     * @param location The location of the error
     * @param type "assert", "report", "error"
     * @param pattern The pattern ID
     * @param lineNumber The line number
     */
    public record TestResult(String ruleId, String message, String location, String type, String pattern,
                             int lineNumber) {

        @Override
            public String toString() {
                return String.format("[%s] %s at %s (Line %d)", type.toUpperCase(), message, location, lineNumber);
            }
        }
}