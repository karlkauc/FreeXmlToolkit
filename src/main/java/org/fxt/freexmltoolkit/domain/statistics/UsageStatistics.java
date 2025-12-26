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

    public LocalDateTime getFirstLaunch() {
        return firstLaunch;
    }

    public void setFirstLaunch(LocalDateTime firstLaunch) {
        this.firstLaunch = firstLaunch;
    }

    public LocalDateTime getLastLaunch() {
        return lastLaunch;
    }

    public void setLastLaunch(LocalDateTime lastLaunch) {
        this.lastLaunch = lastLaunch;
    }

    public long getTotalUsageSeconds() {
        return totalUsageSeconds;
    }

    public void setTotalUsageSeconds(long totalUsageSeconds) {
        this.totalUsageSeconds = totalUsageSeconds;
    }

    public void addUsageSeconds(long seconds) {
        this.totalUsageSeconds += seconds;
    }

    public int getTotalLaunches() {
        return totalLaunches;
    }

    public void setTotalLaunches(int totalLaunches) {
        this.totalLaunches = totalLaunches;
    }

    public void incrementLaunches() {
        this.totalLaunches++;
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

    public int getValidationErrors() {
        return validationErrors;
    }

    public void setValidationErrors(int validationErrors) {
        this.validationErrors = validationErrors;
    }

    public void addValidationErrors(int count) {
        this.validationErrors += count;
    }

    public int getErrorsCorrected() {
        return errorsCorrected;
    }

    public void setErrorsCorrected(int errorsCorrected) {
        this.errorsCorrected = errorsCorrected;
    }

    public void incrementErrorsCorrected(int count) {
        this.errorsCorrected += count;
    }

    public int getTransformationsPerformed() {
        return transformationsPerformed;
    }

    public void setTransformationsPerformed(int transformationsPerformed) {
        this.transformationsPerformed = transformationsPerformed;
    }

    public void incrementTransformations() {
        this.transformationsPerformed++;
    }

    public int getDocumentsFormatted() {
        return documentsFormatted;
    }

    public void setDocumentsFormatted(int documentsFormatted) {
        this.documentsFormatted = documentsFormatted;
    }

    public void incrementDocumentsFormatted() {
        this.documentsFormatted++;
    }

    public int getXpathQueriesExecuted() {
        return xpathQueriesExecuted;
    }

    public void setXpathQueriesExecuted(int xpathQueriesExecuted) {
        this.xpathQueriesExecuted = xpathQueriesExecuted;
    }

    public void incrementXpathQueries() {
        this.xpathQueriesExecuted++;
    }

    public int getXqueryExecutions() {
        return xqueryExecutions;
    }

    public void setXqueryExecutions(int xqueryExecutions) {
        this.xqueryExecutions = xqueryExecutions;
    }

    public void incrementXqueryExecutions() {
        this.xqueryExecutions++;
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

    public int getSchemasGenerated() {
        return schemasGenerated;
    }

    public void setSchemasGenerated(int schemasGenerated) {
        this.schemasGenerated = schemasGenerated;
    }

    public void incrementSchemasGenerated() {
        this.schemasGenerated++;
    }

    public int getSignaturesCreated() {
        return signaturesCreated;
    }

    public void setSignaturesCreated(int signaturesCreated) {
        this.signaturesCreated = signaturesCreated;
    }

    public void incrementSignaturesCreated() {
        this.signaturesCreated++;
    }

    public int getSignaturesVerified() {
        return signaturesVerified;
    }

    public void setSignaturesVerified(int signaturesVerified) {
        this.signaturesVerified = signaturesVerified;
    }

    public void incrementSignaturesVerified() {
        this.signaturesVerified++;
    }

    public int getPdfsGenerated() {
        return pdfsGenerated;
    }

    public void setPdfsGenerated(int pdfsGenerated) {
        this.pdfsGenerated = pdfsGenerated;
    }

    public void incrementPdfsGenerated() {
        this.pdfsGenerated++;
    }

    public int getFilesOpened() {
        return filesOpened;
    }

    public void setFilesOpened(int filesOpened) {
        this.filesOpened = filesOpened;
    }

    public void incrementFilesOpened() {
        this.filesOpened++;
    }

    public Map<LocalDate, DailyStatistics> getDailyStats() {
        return dailyStats;
    }

    public void setDailyStats(Map<LocalDate, DailyStatistics> dailyStats) {
        this.dailyStats = dailyStats != null ? dailyStats : new HashMap<>();
    }

    public DailyStatistics getTodayStats() {
        LocalDate today = LocalDate.now();
        return dailyStats.computeIfAbsent(today, DailyStatistics::new);
    }

    public Map<String, FeatureUsage> getFeatureUsage() {
        return featureUsage;
    }

    public void setFeatureUsage(Map<String, FeatureUsage> featureUsage) {
        this.featureUsage = featureUsage != null ? featureUsage : new HashMap<>();
    }

    public FeatureUsage getFeatureUsage(String featureId) {
        return featureUsage.get(featureId);
    }

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
     */
    public int getTotalActivityCount() {
        return filesValidated + transformationsPerformed + documentsFormatted
            + xpathQueriesExecuted + schematronValidations;
    }

    /**
     * Get count of discovered features
     */
    public long getDiscoveredFeaturesCount() {
        return featureUsage.values().stream()
            .filter(FeatureUsage::isDiscovered)
            .count();
    }

    /**
     * Get count of active days in the last 7 days
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
