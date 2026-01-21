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

    /**
     * Default constructor using today's date.
     */
    public DailyStatistics() {
        this.date = LocalDate.now();
    }

    /**
     * Constructor with specific date.
     *
     * @param date The date.
     */
    public DailyStatistics(LocalDate date) {
        this.date = date;
    }

    // Getters and Setters

    /**
     * Gets the date.
     *
     * @return The date.
     */
    public LocalDate getDate() {
        return date;
    }

    /**
     * Sets the date.
     *
     * @param date The date to set.
     */
    public void setDate(LocalDate date) {
        this.date = date;
    }

    /**
     * Gets the number of files validated.
     *
     * @return The files validated count.
     */
    public int getFilesValidated() {
        return filesValidated;
    }

    /**
     * Sets the number of files validated.
     *
     * @param filesValidated The files validated count to set.
     */
    public void setFilesValidated(int filesValidated) {
        this.filesValidated = filesValidated;
    }

    /**
     * Increments the files validated count.
     */
    public void incrementFilesValidated() {
        this.filesValidated++;
    }

    /**
     * Gets the number of errors found.
     *
     * @return The errors found count.
     */
    public int getErrorsFound() {
        return errorsFound;
    }

    /**
     * Sets the number of errors found.
     *
     * @param errorsFound The errors found count to set.
     */
    public void setErrorsFound(int errorsFound) {
        this.errorsFound = errorsFound;
    }

    /**
     * Adds to the errors found count.
     *
     * @param count The amount to add.
     */
    public void addErrorsFound(int count) {
        this.errorsFound += count;
    }

    /**
     * Gets the number of errors fixed.
     *
     * @return The errors fixed count.
     */
    public int getErrorsFixed() {
        return errorsFixed;
    }

    /**
     * Sets the number of errors fixed.
     *
     * @param errorsFixed The errors fixed count to set.
     */
    public void setErrorsFixed(int errorsFixed) {
        this.errorsFixed = errorsFixed;
    }

    /**
     * Adds to the errors fixed count.
     *
     * @param count The amount to add.
     */
    public void addErrorsFixed(int count) {
        this.errorsFixed += count;
    }

    /**
     * Gets the number of transformations performed.
     *
     * @return The transformations count.
     */
    public int getTransformations() {
        return transformations;
    }

    /**
     * Sets the number of transformations performed.
     *
     * @param transformations The transformations count to set.
     */
    public void setTransformations(int transformations) {
        this.transformations = transformations;
    }

    /**
     * Increments the transformations count.
     */
    public void incrementTransformations() {
        this.transformations++;
    }

    /**
     * Gets the number of formattings performed.
     *
     * @return The formattings count.
     */
    public int getFormattings() {
        return formattings;
    }

    /**
     * Sets the number of formattings performed.
     *
     * @param formattings The formattings count to set.
     */
    public void setFormattings(int formattings) {
        this.formattings = formattings;
    }

    /**
     * Increments the formattings count.
     */
    public void incrementFormattings() {
        this.formattings++;
    }

    /**
     * Gets the number of XPath queries executed.
     *
     * @return The XPath queries count.
     */
    public int getXpathQueries() {
        return xpathQueries;
    }

    /**
     * Sets the number of XPath queries executed.
     *
     * @param xpathQueries The XPath queries count to set.
     */
    public void setXpathQueries(int xpathQueries) {
        this.xpathQueries = xpathQueries;
    }

    /**
     * Increments the XPath queries count.
     */
    public void incrementXpathQueries() {
        this.xpathQueries++;
    }

    /**
     * Gets the number of Schematron validations performed.
     *
     * @return The Schematron validations count.
     */
    public int getSchematronValidations() {
        return schematronValidations;
    }

    /**
     * Sets the number of Schematron validations performed.
     *
     * @param schematronValidations The Schematron validations count to set.
     */
    public void setSchematronValidations(int schematronValidations) {
        this.schematronValidations = schematronValidations;
    }

    /**
     * Increments the Schematron validations count.
     */
    public void incrementSchematronValidations() {
        this.schematronValidations++;
    }

    /**
     * Gets the usage minutes.
     *
     * @return The usage minutes.
     */
    public int getUsageMinutes() {
        return usageMinutes;
    }

    /**
     * Sets the usage minutes.
     *
     * @param usageMinutes The usage minutes to set.
     */
    public void setUsageMinutes(int usageMinutes) {
        this.usageMinutes = usageMinutes;
    }

    /**
     * Adds to the usage minutes.
     *
     * @param minutes The minutes to add.
     */
    public void addUsageMinutes(int minutes) {
        this.usageMinutes += minutes;
    }

    /**
     * Check if there was any activity on this day.
     *
     * @return True if any activity recorded.
     */
    public boolean hasActivity() {
        return filesValidated > 0 || transformations > 0 || formattings > 0
            || xpathQueries > 0 || schematronValidations > 0 || usageMinutes > 0;
    }

    /**
     * Get total activity count for this day.
     *
     * @return The total activity count.
     */
    public int getTotalActivity() {
        return filesValidated + transformations + formattings + xpathQueries + schematronValidations;
    }
}
