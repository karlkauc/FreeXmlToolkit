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

package org.fxt.freexmltoolkit.service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Result container for batch XQuery transformation over multiple XML files.
 * Supports both combined output and per-file results.
 */
public class BatchTransformationResult {

    // Execution metadata
    private boolean success;
    private String combinedOutput;
    private String errorMessage;
    private LocalDateTime executedAt;
    private long totalExecutionTime; // milliseconds

    // Per-file results (LinkedHashMap to preserve order)
    private final Map<File, String> perFileResults = new LinkedHashMap<>();
    private final Map<File, String> perFileErrors = new LinkedHashMap<>();
    private final Map<File, Long> perFileExecutionTimes = new LinkedHashMap<>();

    // Statistics
    private int totalFiles;
    private int successCount;
    private int errorCount;
    private int skippedCount;

    // Output format
    private XsltTransformationEngine.OutputFormat outputFormat;

    /**
     * Default constructor.
     */
    public BatchTransformationResult() {
        this.executedAt = LocalDateTime.now();
        this.success = false;
    }

    // ========== Factory Methods ==========

    /**
     * Create a successful batch result with combined output.
     * @param combinedOutput The combined output string
     * @param totalFiles The total number of files processed
     * @return The result object
     */
    public static BatchTransformationResult success(String combinedOutput, int totalFiles) {
        BatchTransformationResult result = new BatchTransformationResult();
        result.success = true;
        result.combinedOutput = combinedOutput;
        result.totalFiles = totalFiles;
        result.successCount = totalFiles;
        return result;
    }

    /**
     * Create an error result for batch processing failure.
     * @param errorMessage The error message
     * @return The error result
     */
    public static BatchTransformationResult error(String errorMessage) {
        BatchTransformationResult result = new BatchTransformationResult();
        result.success = false;
        result.errorMessage = errorMessage;
        return result;
    }

    // ========== Per-File Result Management ==========

    /**
     * Add a successful result for a specific file.
     * @param file The processed file
     * @param output The output content
     * @param executionTime The execution time in milliseconds
     */
    public void addFileResult(File file, String output, long executionTime) {
        perFileResults.put(file, output);
        perFileExecutionTimes.put(file, executionTime);
        successCount++;
    }

    /**
     * Add an error result for a specific file.
     * @param file The processed file
     * @param error The error message
     */
    public void addFileError(File file, String error) {
        perFileErrors.put(file, error);
        errorCount++;
    }

    /**
     * Get the result for a specific file.
     * @param file The file to lookup
     * @return The result output or null
     */
    public String getFileResult(File file) {
        return perFileResults.get(file);
    }

    /**
     * Get the error for a specific file.
     * @param file The file to lookup
     * @return The error message or null
     */
    public String getFileError(File file) {
        return perFileErrors.get(file);
    }

    /**
     * Check if a specific file was processed successfully.
     * @param file The file to check
     * @return true if successful
     */
    public boolean isFileSuccess(File file) {
        return perFileResults.containsKey(file);
    }

    /**
     * Check if a specific file had an error.
     * @param file The file to check
     * @return true if error occurred
     */
    public boolean isFileError(File file) {
        return perFileErrors.containsKey(file);
    }

    // ========== Summary Methods ==========

    /**
     * Get summary statistics as formatted text.
     *
     * @return The summary text.
     */
    public String getSummaryText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Batch XQuery Transformation Results\n");
        sb.append("====================================\n\n");
        sb.append(String.format("Total Files: %d\n", totalFiles));
        sb.append(String.format("Successful:  %d\n", successCount));
        sb.append(String.format("Errors:      %d\n", errorCount));
        sb.append(String.format("Skipped:     %d\n", skippedCount));
        sb.append(String.format("Total Time:  %d ms\n", totalExecutionTime));

        if (!perFileErrors.isEmpty()) {
            sb.append("\nErrors:\n");
            for (Map.Entry<File, String> entry : perFileErrors.entrySet()) {
                sb.append(String.format("  - %s: %s\n", entry.getKey().getName(), entry.getValue()));
            }
        }

        return sb.toString();
    }

    /**
     * Get summary as HTML for preview.
     *
     * @return The summary HTML.
     */
    public String getSummaryHtml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><style>");
        sb.append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; padding: 20px; }");
        sb.append(".stats { display: flex; gap: 20px; margin: 20px 0; }");
        sb.append(".stat { background: #f8f9fa; padding: 15px; border-radius: 8px; text-align: center; }");
        sb.append(".stat-value { font-size: 24px; font-weight: bold; color: #0d6efd; }");
        sb.append(".stat-label { font-size: 12px; color: #6c757d; }");
        sb.append(".success { color: #198754; }");
        sb.append(".error { color: #dc3545; }");
        sb.append("table { width: 100%; border-collapse: collapse; margin-top: 20px; }");
        sb.append("th, td { padding: 8px; text-align: left; border-bottom: 1px solid #dee2e6; }");
        sb.append("th { background: #f8f9fa; }");
        sb.append("</style></head><body>");

        sb.append("<h2>Batch XQuery Results</h2>");

        // Stats
        sb.append("<div class='stats'>");
        sb.append(String.format("<div class='stat'><div class='stat-value'>%d</div><div class='stat-label'>Total Files</div></div>", totalFiles));
        sb.append(String.format("<div class='stat'><div class='stat-value success'>%d</div><div class='stat-label'>Successful</div></div>", successCount));
        sb.append(String.format("<div class='stat'><div class='stat-value error'>%d</div><div class='stat-label'>Errors</div></div>", errorCount));
        sb.append(String.format("<div class='stat'><div class='stat-value'>%d ms</div><div class='stat-label'>Total Time</div></div>", totalExecutionTime));
        sb.append("</div>");

        // File list
        if (!perFileResults.isEmpty() || !perFileErrors.isEmpty()) {
            sb.append("<h3>File Results</h3>");
            sb.append("<table><thead><tr><th>File</th><th>Status</th><th>Time</th></tr></thead><tbody>");

            for (File file : perFileResults.keySet()) {
                Long time = perFileExecutionTimes.get(file);
                sb.append(String.format("<tr><td>%s</td><td class='success'>Success</td><td>%d ms</td></tr>",
                        file.getName(), time != null ? time : 0));
            }

            for (Map.Entry<File, String> entry : perFileErrors.entrySet()) {
                sb.append(String.format("<tr><td>%s</td><td class='error'>Error: %s</td><td>-</td></tr>",
                        entry.getKey().getName(), entry.getValue()));
            }

            sb.append("</tbody></table>");
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    // ========== Getters and Setters ==========

    /**
     * Checks if the batch transformation was successful.
     *
     * @return True if successful, false otherwise.
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Sets the success status of the batch transformation.
     *
     * @param success True if successful.
     */
    public void setSuccess(boolean success) {
        this.success = success;
    }

    /**
     * Gets the combined output of the transformation.
     *
     * @return The combined output string.
     */
    public String getCombinedOutput() {
        return combinedOutput;
    }

    /**
     * Sets the combined output of the transformation.
     *
     * @param combinedOutput The combined output string to set.
     */
    public void setCombinedOutput(String combinedOutput) {
        this.combinedOutput = combinedOutput;
    }

    /**
     * Gets the error message if the transformation failed.
     *
     * @return The error message.
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Sets the error message.
     *
     * @param errorMessage The error message to set.
     */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * Gets the timestamp when the transformation was executed.
     *
     * @return The execution timestamp.
     */
    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    /**
     * Sets the execution timestamp.
     *
     * @param executedAt The timestamp to set.
     */
    public void setExecutedAt(LocalDateTime executedAt) {
        this.executedAt = executedAt;
    }

    /**
     * Gets the total execution time in milliseconds.
     *
     * @return The total execution time.
     */
    public long getTotalExecutionTime() {
        return totalExecutionTime;
    }

    /**
     * Sets the total execution time in milliseconds.
     *
     * @param totalExecutionTime The execution time to set.
     */
    public void setTotalExecutionTime(long totalExecutionTime) {
        this.totalExecutionTime = totalExecutionTime;
    }

    /**
     * Gets the map of per-file results.
     *
     * @return A map of file to result string.
     */
    public Map<File, String> getPerFileResults() {
        return new LinkedHashMap<>(perFileResults);
    }

    /**
     * Gets the map of per-file errors.
     *
     * @return A map of file to error message.
     */
    public Map<File, String> getPerFileErrors() {
        return new LinkedHashMap<>(perFileErrors);
    }

    /**
     * Gets the map of per-file execution times.
     *
     * @return A map of file to execution time in milliseconds.
     */
    public Map<File, Long> getPerFileExecutionTimes() {
        return new LinkedHashMap<>(perFileExecutionTimes);
    }

    /**
     * Gets the total number of files processed.
     *
     * @return The total files count.
     */
    public int getTotalFiles() {
        return totalFiles;
    }

    /**
     * Sets the total number of files processed.
     *
     * @param totalFiles The total files count to set.
     */
    public void setTotalFiles(int totalFiles) {
        this.totalFiles = totalFiles;
    }

    /**
     * Gets the number of successfully processed files.
     *
     * @return The success count.
     */
    public int getSuccessCount() {
        return successCount;
    }

    /**
     * Sets the number of successfully processed files.
     *
     * @param successCount The success count to set.
     */
    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    /**
     * Gets the number of files that failed processing.
     *
     * @return The error count.
     */
    public int getErrorCount() {
        return errorCount;
    }

    /**
     * Sets the number of files that failed processing.
     *
     * @param errorCount The error count to set.
     */
    public void setErrorCount(int errorCount) {
        this.errorCount = errorCount;
    }

    /**
     * Gets the number of skipped files.
     *
     * @return The skipped count.
     */
    public int getSkippedCount() {
        return skippedCount;
    }

    /**
     * Sets the number of skipped files.
     *
     * @param skippedCount The skipped count to set.
     */
    public void setSkippedCount(int skippedCount) {
        this.skippedCount = skippedCount;
    }

    /**
     * Gets the output format used for the transformation.
     *
     * @return The output format.
     */
    public XsltTransformationEngine.OutputFormat getOutputFormat() {
        return outputFormat;
    }

    /**
     * Sets the output format used for the transformation.
     *
     * @param outputFormat The output format to set.
     */
    public void setOutputFormat(XsltTransformationEngine.OutputFormat outputFormat) {
        this.outputFormat = outputFormat;
    }

    @Override
    public String toString() {
        return String.format("BatchTransformationResult{success=%s, total=%d, success=%d, errors=%d, time=%dms}",
                success, totalFiles, successCount, errorCount, totalExecutionTime);
    }
}
