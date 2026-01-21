package org.fxt.freexmltoolkit.domain.statistics;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Main container for usage statistics tracking.
 * Stores cumulative metrics, daily breakdowns, and feature usage data.
 */
public class UsageStatistics {

    private LocalDateTime firstLaunch;
    private LocalDateTime lastLaunch;
    private long totalUsageSeconds;
    private int totalLaunches;

    // Cumulative metrics
    private int filesValidated;
    private int validationErrors;
    private int errorsCorrected;
    private int transformationsPerformed;
    private int documentsFormatted;
    private int xpathQueriesExecuted;
    private int xqueryExecutions;
    private int schematronValidations;
    private int schemasGenerated;
    private int signaturesCreated;
    private int signaturesVerified;
    private int pdfsGenerated;
    private int filesOpened;

    // Daily breakdown for trends (last 30 days kept)
    private Map<LocalDate, DailyStatistics> dailyStats;

    // Feature usage tracking
    private Map<String, FeatureUsage> featureUsage;

    public UsageStatistics() {
        this.firstLaunch = LocalDateTime.now();
        this.lastLaunch = LocalDateTime.now();
        this.totalLaunches = 0;
        this.dailyStats = new HashMap<>();
        this.featureUsage = new HashMap<>();
    }

    // Getters and Setters

    /**
     * Gets the first launch time.
     *
     * @return The date and time of the first launch
     */
    public LocalDateTime getFirstLaunch() {
        return firstLaunch;
    }

    /**
     * Sets the first launch time.
     *
     * @param firstLaunch The date and time of the first launch
     */
    public void setFirstLaunch(LocalDateTime firstLaunch) {
        this.firstLaunch = firstLaunch;
    }

    /**
     * Gets the last launch time.
     *
     * @return The date and time of the last launch
     */
    public LocalDateTime getLastLaunch() {
        return lastLaunch;
    }

    /**
     * Sets the last launch time.
     *
     * @param lastLaunch The date and time of the last launch
     */
    public void setLastLaunch(LocalDateTime lastLaunch) {
        this.lastLaunch = lastLaunch;
    }

    /**
     * Gets total usage seconds.
     *
     * @return The total usage time in seconds
     */
    public long getTotalUsageSeconds() {
        return totalUsageSeconds;
    }

    /**
     * Sets total usage seconds.
     *
     * @param totalUsageSeconds The total usage time in seconds
     */
    public void setTotalUsageSeconds(long totalUsageSeconds) {
        this.totalUsageSeconds = totalUsageSeconds;
    }

    /**
     * Adds seconds to the total usage time.
     *
     * @param seconds The seconds to add
     */
    public void addUsageSeconds(long seconds) {
        this.totalUsageSeconds += seconds;
    }

    /**
     * Gets total launches.
     *
     * @return The total number of application launches
     */
    public int getTotalLaunches() {
        return totalLaunches;
    }

    /**
     * Sets total launches.
     *
     * @param totalLaunches The total number of application launches
     */
    public void setTotalLaunches(int totalLaunches) {
        this.totalLaunches = totalLaunches;
    }

    /**
     * Increments the launch count.
     */
    public void incrementLaunches() {
        this.totalLaunches++;
    }

    /**
     * Gets files validated count.
     *
     * @return The number of files validated
     */
    public int getFilesValidated() {
        return filesValidated;
    }

    /**
     * Sets files validated count.
     *
     * @param filesValidated The number of files validated
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
     * Gets validation errors count.
     *
     * @return The number of validation errors found
     */
    public int getValidationErrors() {
        return validationErrors;
    }

    /**
     * Sets validation errors count.
     *
     * @param validationErrors The number of validation errors found
     */
    public void setValidationErrors(int validationErrors) {
        this.validationErrors = validationErrors;
    }

    /**
     * Adds to the validation error count.
     *
     * @param count The number of errors to add
     */
    public void addValidationErrors(int count) {
        this.validationErrors += count;
    }

    /**
     * Gets errors corrected count.
     *
     * @return The number of errors corrected
     */
    public int getErrorsCorrected() {
        return errorsCorrected;
    }

    /**
     * Sets errors corrected count.
     *
     * @param errorsCorrected The number of errors corrected
     */
    public void setErrorsCorrected(int errorsCorrected) {
        this.errorsCorrected = errorsCorrected;
    }

    /**
     * Increments the errors corrected count.
     *
     * @param count The number of errors corrected to add
     */
    public void incrementErrorsCorrected(int count) {
        this.errorsCorrected += count;
    }

    /**
     * Gets transformations performed count.
     *
     * @return The number of transformations performed
     */
    public int getTransformationsPerformed() {
        return transformationsPerformed;
    }

    /**
     * Sets transformations performed count.
     *
     * @param transformationsPerformed The number of transformations performed
     */
    public void setTransformationsPerformed(int transformationsPerformed) {
        this.transformationsPerformed = transformationsPerformed;
    }

    /**
     * Increments the transformations count.
     */
    public void incrementTransformations() {
        this.transformationsPerformed++;
    }

    /**
     * Gets documents formatted count.
     *
     * @return The number of documents formatted
     */
    public int getDocumentsFormatted() {
        return documentsFormatted;
    }

    /**
     * Sets documents formatted count.
     *
     * @param documentsFormatted The number of documents formatted
     */
    public void setDocumentsFormatted(int documentsFormatted) {
        this.documentsFormatted = documentsFormatted;
    }

    /**
     * Increments the documents formatted count.
     */
    public void incrementDocumentsFormatted() {
        this.documentsFormatted++;
    }

    /**
     * Gets XPath queries executed count.
     *
     * @return The number of XPath queries executed
     */
    public int getXpathQueriesExecuted() {
        return xpathQueriesExecuted;
    }

    /**
     * Sets XPath queries executed count.
     *
     * @param xpathQueriesExecuted The number of XPath queries executed
     */
    public void setXpathQueriesExecuted(int xpathQueriesExecuted) {
        this.xpathQueriesExecuted = xpathQueriesExecuted;
    }

    /**
     * Increments the XPath queries count.
     */
    public void incrementXpathQueries() {
        this.xpathQueriesExecuted++;
    }

    /**
     * Gets XQuery executions count.
     *
     * @return The number of XQuery executions
     */
    public int getXqueryExecutions() {
        return xqueryExecutions;
    }

    /**
     * Sets XQuery executions count.
     *
     * @param xqueryExecutions The number of XQuery executions
     */
    public void setXqueryExecutions(int xqueryExecutions) {
        this.xqueryExecutions = xqueryExecutions;
    }

    /**
     * Increments the XQuery executions count.
     */
    public void incrementXqueryExecutions() {
        this.xqueryExecutions++;
    }

    /**
     * Gets Schematron validations count.
     *
     * @return The number of Schematron validations performed
     */
    public int getSchematronValidations() {
        return schematronValidations;
    }

    /**
     * Sets Schematron validations count.
     *
     * @param schematronValidations The number of Schematron validations performed
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
     * Gets schemas generated count.
     *
     * @return The number of schemas generated
     */
    public int getSchemasGenerated() {
        return schemasGenerated;
    }

    /**
     * Sets schemas generated count.
     *
     * @param schemasGenerated The number of schemas generated
     */
    public void setSchemasGenerated(int schemasGenerated) {
        this.schemasGenerated = schemasGenerated;
    }

    /**
     * Increments the schemas generated count.
     */
    public void incrementSchemasGenerated() {
        this.schemasGenerated++;
    }

    /**
     * Gets signatures created count.
     *
     * @return The number of signatures created
     */
    public int getSignaturesCreated() {
        return signaturesCreated;
    }

    /**
     * Sets signatures created count.
     *
     * @param signaturesCreated The number of signatures created
     */
    public void setSignaturesCreated(int signaturesCreated) {
        this.signaturesCreated = signaturesCreated;
    }

    /**
     * Increments the signatures created count.
     */
    public void incrementSignaturesCreated() {
        this.signaturesCreated++;
    }

    /**
     * Gets signatures verified count.
     *
     * @return The number of signatures verified
     */
    public int getSignaturesVerified() {
        return signaturesVerified;
    }

    /**
     * Sets signatures verified count.
     *
     * @param signaturesVerified The number of signatures verified
     */
    public void setSignaturesVerified(int signaturesVerified) {
        this.signaturesVerified = signaturesVerified;
    }

    /**
     * Increments the signatures verified count.
     */
    public void incrementSignaturesVerified() {
        this.signaturesVerified++;
    }

    /**
     * Gets PDFs generated count.
     *
     * @return The number of PDFs generated
     */
    public int getPdfsGenerated() {
        return pdfsGenerated;
    }

    /**
     * Sets PDFs generated count.
     *
     * @param pdfsGenerated The number of PDFs generated
     */
    public void setPdfsGenerated(int pdfsGenerated) {
        this.pdfsGenerated = pdfsGenerated;
    }

    /**
     * Increments the PDFs generated count.
     */
    public void incrementPdfsGenerated() {
        this.pdfsGenerated++;
    }

    /**
     * Gets files opened count.
     *
     * @return The number of files opened
     */
    public int getFilesOpened() {
        return filesOpened;
    }

    /**
     * Sets files opened count.
     *
     * @param filesOpened The number of files opened
     */
    public void setFilesOpened(int filesOpened) {
        this.filesOpened = filesOpened;
    }

    /**
     * Increments the files opened count.
     */
    public void incrementFilesOpened() {
        this.filesOpened++;
    }

    /**
     * Gets daily statistics map.
     *
     * @return A map of daily statistics
     */
    public Map<LocalDate, DailyStatistics> getDailyStats() {
        return dailyStats;
    }

    /**
     * Sets daily statistics map.
     *
     * @param dailyStats A map of daily statistics
     */
    public void setDailyStats(Map<LocalDate, DailyStatistics> dailyStats) {
        this.dailyStats = dailyStats != null ? dailyStats : new HashMap<>();
    }

    /**
     * Gets statistics for today.
     *
     * @return The statistics for today (creates new if absent)
     */
    public DailyStatistics getTodayStats() {
        LocalDate today = LocalDate.now();
        return dailyStats.computeIfAbsent(today, DailyStatistics::new);
    }

    /**
     * Gets feature usage map.
     *
     * @return A map of feature usage statistics
     */
    public Map<String, FeatureUsage> getFeatureUsage() {
        return featureUsage;
    }

    /**
     * Sets feature usage map.
     *
     * @param featureUsage A map of feature usage statistics
     */
    public void setFeatureUsage(Map<String, FeatureUsage> featureUsage) {
        this.featureUsage = featureUsage != null ? featureUsage : new HashMap<>();
    }

    /**
     * Gets usage for a specific feature.
     *
     * @param featureId The feature ID
     * @return The feature usage object, or null if not found
     */
    public FeatureUsage getFeatureUsage(String featureId) {
        return featureUsage.get(featureId);
    }

    /**
     * Tracks usage of a feature.
     *
     * @param featureId   The feature ID
     * @param featureName The feature name
     * @param category    The feature category
     */
    public void trackFeature(String featureId, String featureName, String category) {
        FeatureUsage usage = featureUsage.computeIfAbsent(featureId,
            id -> new FeatureUsage(id, featureName, category));
        usage.incrementUseCount();
        usage.setLastUsed(LocalDateTime.now());
        if (!usage.isDiscovered()) {
            usage.setDiscovered(true);
            usage.setFirstUsed(LocalDateTime.now());
        }
    }

    /**
     * Clean up old daily stats (keep last 30 days)
     */
    public void cleanupOldDailyStats() {
        LocalDate cutoffDate = LocalDate.now().minusDays(30);
        dailyStats.entrySet().removeIf(entry -> entry.getKey().isBefore(cutoffDate));
    }

    /**
     * Get total activity score for productivity calculation
     *
     * @return The total activity score
     */
    public int getTotalActivityCount() {
        return filesValidated + transformationsPerformed + documentsFormatted
            + xpathQueriesExecuted + schematronValidations;
    }

    /**
     * Get count of discovered features
     *
     * @return The number of discovered features
     */
    public long getDiscoveredFeaturesCount() {
        return featureUsage.values().stream()
            .filter(FeatureUsage::isDiscovered)
            .count();
    }

    /**
     * Get count of active days in the last 7 days
     *
     * @return The number of active days
     */
    public int getActiveDaysLast7() {
        LocalDate today = LocalDate.now();
        int count = 0;
        for (int i = 0; i < 7; i++) {
            LocalDate date = today.minusDays(i);
            DailyStatistics dayStats = dailyStats.get(date);
            if (dayStats != null && dayStats.hasActivity()) {
                count++;
            }
        }
        return count;
    }
}
