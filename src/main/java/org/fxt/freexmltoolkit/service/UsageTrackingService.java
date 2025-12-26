package org.fxt.freexmltoolkit.service;

import org.fxt.freexmltoolkit.domain.statistics.DailyStatistics;
import org.fxt.freexmltoolkit.domain.statistics.FeatureTip;
import org.fxt.freexmltoolkit.domain.statistics.FeatureUsage;
import org.fxt.freexmltoolkit.domain.statistics.UsageStatistics;

import java.util.List;

/**
 * Service for tracking application usage statistics.
 * Collects metrics for the gamification dashboard including validations,
 * transformations, feature discovery, and productivity scoring.
 */
public interface UsageTrackingService {

    // ================== Tracking Methods ==================

    /**
     * Track a file validation operation.
     *
     * @param errorCount number of validation errors found
     */
    void trackFileValidation(int errorCount);

    /**
     * Track when errors have been corrected (file re-validated with fewer errors).
     *
     * @param count number of errors corrected
     */
    void trackErrorCorrected(int count);

    /**
     * Track an XSLT transformation.
     */
    void trackTransformation();

    /**
     * Track usage of a specific feature.
     *
     * @param featureId unique identifier for the feature
     */
    void trackFeatureUsed(String featureId);

    /**
     * Track an XPath query execution.
     */
    void trackXPathQuery();

    /**
     * Track an XQuery execution.
     */
    void trackXQueryExecution();

    /**
     * Track a Schematron validation.
     */
    void trackSchematronValidation();

    /**
     * Track document formatting/pretty-print.
     */
    void trackFormatting();

    /**
     * Track schema generation.
     */
    void trackSchemaGeneration();

    /**
     * Track a digital signature operation.
     *
     * @param isSign true for signing, false for verification
     */
    void trackSignatureOperation(boolean isSign);

    /**
     * Track PDF generation.
     */
    void trackPdfGeneration();

    /**
     * Track file opening.
     */
    void trackFileOpened();

    // ================== Session Management ==================

    /**
     * Start a new usage session.
     * Called when the application starts.
     */
    void startSession();

    /**
     * End the current usage session.
     * Called when the application closes.
     */
    void endSession();

    // ================== Statistics Retrieval ==================

    /**
     * Get the complete usage statistics.
     *
     * @return current usage statistics
     */
    UsageStatistics getStatistics();

    /**
     * Get daily statistics for the specified number of past days.
     *
     * @param daysBack number of days to retrieve (including today)
     * @return list of daily statistics, most recent first
     */
    List<DailyStatistics> getDailyStats(int daysBack);

    /**
     * Calculate and return the current productivity score (0-100).
     *
     * @return productivity score
     */
    int getProductivityScore();

    /**
     * Get the productivity level label based on the current score.
     *
     * @return productivity level (e.g., "Beginner", "Professional")
     */
    String getProductivityLevel();

    /**
     * Get list of features that have not been discovered yet.
     *
     * @return list of undiscovered features
     */
    List<FeatureUsage> getUnusedFeatures();

    /**
     * Get list of all features with their usage status.
     *
     * @return list of all features
     */
    List<FeatureUsage> getAllFeatures();

    /**
     * Get contextual tips for undiscovered features.
     *
     * @return list of relevant tips, sorted by priority
     */
    List<FeatureTip> getRelevantTips();

    /**
     * Get the change in a specific metric compared to last week.
     *
     * @param metric metric name (e.g., "validations", "transformations")
     * @return change value (positive = increase, negative = decrease)
     */
    int getWeeklyChange(String metric);

    // ================== Settings ==================

    /**
     * Check if tracking is enabled.
     *
     * @return true if tracking is active
     */
    boolean isTrackingEnabled();

    /**
     * Enable or disable tracking.
     *
     * @param enabled true to enable tracking
     */
    void setTrackingEnabled(boolean enabled);

    /**
     * Clear all collected statistics.
     */
    void clearStatistics();

    /**
     * Force save statistics to disk.
     */
    void saveStatistics();
}
