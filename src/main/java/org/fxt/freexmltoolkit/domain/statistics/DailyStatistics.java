package org.fxt.freexmltoolkit.domain.statistics;

import java.time.LocalDate;

/**
 * Daily usage statistics for trend tracking.
 * Each day has its own instance to track activity over time.
 */
public class DailyStatistics {

    private LocalDate date;
    private int filesValidated;
    private int errorsFound;
    private int errorsFixed;
    private int transformations;
    private int formattings;
    private int xpathQueries;
    private int schematronValidations;
    private int usageMinutes;

    public DailyStatistics() {
        this.date = LocalDate.now();
    }

    public DailyStatistics(LocalDate date) {
        this.date = date;
    }

    // Getters and Setters

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public int getFilesValidated() {
        return filesValidated;
    }

    public void setFilesValidated(int filesValidated) {
        this.filesValidated = filesValidated;
    }

    public void incrementFilesValidated() {
        this.filesValidated++;
    }

    public int getErrorsFound() {
        return errorsFound;
    }

    public void setErrorsFound(int errorsFound) {
        this.errorsFound = errorsFound;
    }

    public void addErrorsFound(int count) {
        this.errorsFound += count;
    }

    public int getErrorsFixed() {
        return errorsFixed;
    }

    public void setErrorsFixed(int errorsFixed) {
        this.errorsFixed = errorsFixed;
    }

    public void addErrorsFixed(int count) {
        this.errorsFixed += count;
    }

    public int getTransformations() {
        return transformations;
    }

    public void setTransformations(int transformations) {
        this.transformations = transformations;
    }

    public void incrementTransformations() {
        this.transformations++;
    }

    public int getFormattings() {
        return formattings;
    }

    public void setFormattings(int formattings) {
        this.formattings = formattings;
    }

    public void incrementFormattings() {
        this.formattings++;
    }

    public int getXpathQueries() {
        return xpathQueries;
    }

    public void setXpathQueries(int xpathQueries) {
        this.xpathQueries = xpathQueries;
    }

    public void incrementXpathQueries() {
        this.xpathQueries++;
    }

    public int getSchematronValidations() {
        return schematronValidations;
    }

    public void setSchematronValidations(int schematronValidations) {
        this.schematronValidations = schematronValidations;
    }

    public void incrementSchematronValidations() {
        this.schematronValidations++;
    }

    public int getUsageMinutes() {
        return usageMinutes;
    }

    public void setUsageMinutes(int usageMinutes) {
        this.usageMinutes = usageMinutes;
    }

    public void addUsageMinutes(int minutes) {
        this.usageMinutes += minutes;
    }

    /**
     * Check if there was any activity on this day
     */
    public boolean hasActivity() {
        return filesValidated > 0 || transformations > 0 || formattings > 0
            || xpathQueries > 0 || schematronValidations > 0 || usageMinutes > 0;
    }

    /**
     * Get total activity count for this day
     */
    public int getTotalActivity() {
        return filesValidated + transformations + formattings + xpathQueries + schematronValidations;
    }
}
