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

    /**
     * Creates a new TestFile for the specified file.
     *
     * @param file the file to be tested
     */
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

    /**
     * Gets the file being tested.
     *
     * @return the file
     */
    public File getFile() {
        return file;
    }

    /**
     * Gets the filename.
     *
     * @return the filename
     */
    public String getFilename() {
        return filename.get();
    }

    /**
     * Sets the filename.
     *
     * @param filename the filename to set
     */
    public void setFilename(String filename) {
        this.filename.set(filename);
    }

    /**
     * Gets the filename property for JavaFX binding.
     *
     * @return the filename property
     */
    public StringProperty filenameProperty() {
        return filename;
    }

    /**
     * Gets the test status.
     *
     * @return the status (e.g., "Not tested", "Passed", "Failed")
     */
    public String getStatus() {
        return status.get();
    }

    /**
     * Sets the test status.
     *
     * @param status the status to set
     */
    public void setStatus(String status) {
        this.status.set(status);
    }

    /**
     * Gets the status property for JavaFX binding.
     *
     * @return the status property
     */
    public StringProperty statusProperty() {
        return status;
    }

    /**
     * Gets the number of violations found.
     *
     * @return the violation count
     */
    public int getViolations() {
        return violations.get();
    }

    /**
     * Sets the number of violations found.
     *
     * @param violations the violation count to set
     */
    public void setViolations(int violations) {
        this.violations.set(violations);
    }

    /**
     * Gets the violations property for JavaFX binding.
     *
     * @return the violations property
     */
    public IntegerProperty violationsProperty() {
        return violations;
    }

    /**
     * Gets the number of warnings found.
     *
     * @return the warning count
     */
    public int getWarnings() {
        return warnings.get();
    }

    /**
     * Sets the number of warnings found.
     *
     * @param warnings the warning count to set
     */
    public void setWarnings(int warnings) {
        this.warnings.set(warnings);
    }

    /**
     * Gets the warnings property for JavaFX binding.
     *
     * @return the warnings property
     */
    public IntegerProperty warningsProperty() {
        return warnings;
    }

    /**
     * Gets the last tested timestamp as a formatted string.
     *
     * @return the last tested timestamp
     */
    public String getLastTested() {
        return lastTested.get();
    }

    /**
     * Sets the last tested timestamp.
     *
     * @param lastTested the timestamp to set
     */
    public void setLastTested(String lastTested) {
        this.lastTested.set(lastTested);
    }

    /**
     * Gets the last tested property for JavaFX binding.
     *
     * @return the last tested property
     */
    public StringProperty lastTestedProperty() {
        return lastTested;
    }

    /**
     * Gets the detailed test results.
     *
     * @return list of test results
     */
    public List<TestResult> getDetailedResults() {
        return detailedResults;
    }

    /**
     * Sets the detailed test results.
     *
     * @param detailedResults the results to set (null-safe)
     */
    public void setDetailedResults(List<TestResult> detailedResults) {
        this.detailedResults = detailedResults != null ? detailedResults : new ArrayList<>();
    }

    /**
     * Adds a single test result.
     *
     * @param result the result to add
     */
    public void addTestResult(TestResult result) {
        if (this.detailedResults == null) {
            this.detailedResults = new ArrayList<>();
        }
        this.detailedResults.add(result);
    }

    /**
     * Clears all detailed test results.
     */
    public void clearDetailedResults() {
        if (this.detailedResults != null) {
            this.detailedResults.clear();
        }
    }

    /**
     * Gets the test duration as a formatted string.
     *
     * @return the test duration (e.g., "125ms")
     */
    public String getTestDuration() {
        return testDuration;
    }

    /**
     * Sets the test duration.
     *
     * @param testDuration the duration to set (null defaults to "0ms")
     */
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