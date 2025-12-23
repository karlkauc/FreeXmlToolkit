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

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;

import java.io.File;

/**
 * Model class representing an XML file entry for batch processing.
 * Used in the TableView for multi-file selection in the XSLT Developer tab.
 */
public class XmlFileEntry {

    /**
     * Processing status for XML files
     */
    public enum Status {
        READY("Ready"),
        PROCESSING("Processing..."),
        SUCCESS("Success"),
        ERROR("Error"),
        SKIPPED("Skipped");

        private final String displayName;

        Status(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private final File file;
    private final SimpleBooleanProperty selected;
    private final SimpleStringProperty status;
    private final SimpleStringProperty statusMessage;
    private final long fileSize;
    private String resultOutput;
    private String errorMessage;

    /**
     * Create a new XmlFileEntry for the given file.
     *
     * @param file the XML file
     */
    public XmlFileEntry(File file) {
        this.file = file;
        this.selected = new SimpleBooleanProperty(true);
        this.status = new SimpleStringProperty(Status.READY.getDisplayName());
        this.statusMessage = new SimpleStringProperty("");
        this.fileSize = file.exists() ? file.length() : 0;
    }

    // ========== Getters ==========

    public File getFile() {
        return file;
    }

    public String getFileName() {
        return file.getName();
    }

    public String getFilePath() {
        return file.getAbsolutePath();
    }

    public long getFileSize() {
        return fileSize;
    }

    /**
     * Get formatted file size string (e.g., "12.5 KB")
     */
    public String getFormattedSize() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
        }
    }

    public String getResultOutput() {
        return resultOutput;
    }

    public void setResultOutput(String resultOutput) {
        this.resultOutput = resultOutput;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    // ========== Property Accessors for TableView binding ==========

    public boolean isSelected() {
        return selected.get();
    }

    public void setSelected(boolean selected) {
        this.selected.set(selected);
    }

    public SimpleBooleanProperty selectedProperty() {
        return selected;
    }

    public String getStatus() {
        return status.get();
    }

    public void setStatus(Status status) {
        this.status.set(status.getDisplayName());
    }

    public void setStatus(String status) {
        this.status.set(status);
    }

    public SimpleStringProperty statusProperty() {
        return status;
    }

    public String getStatusMessage() {
        return statusMessage.get();
    }

    public void setStatusMessage(String message) {
        this.statusMessage.set(message);
    }

    public SimpleStringProperty statusMessageProperty() {
        return statusMessage;
    }

    // ========== Utility Methods ==========

    /**
     * Mark this file as successfully processed
     */
    public void markSuccess(String output) {
        setStatus(Status.SUCCESS);
        setResultOutput(output);
        setErrorMessage(null);
    }

    /**
     * Mark this file as failed with an error
     */
    public void markError(String error) {
        setStatus(Status.ERROR);
        setErrorMessage(error);
        setResultOutput(null);
    }

    /**
     * Mark this file as currently being processed
     */
    public void markProcessing() {
        setStatus(Status.PROCESSING);
    }

    /**
     * Reset this file to ready state
     */
    public void reset() {
        setStatus(Status.READY);
        setResultOutput(null);
        setErrorMessage(null);
        setStatusMessage("");
    }

    /**
     * Check if this file was processed successfully
     */
    public boolean isSuccess() {
        return Status.SUCCESS.getDisplayName().equals(getStatus());
    }

    /**
     * Check if this file had an error
     */
    public boolean isError() {
        return Status.ERROR.getDisplayName().equals(getStatus());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        XmlFileEntry that = (XmlFileEntry) o;
        return file.equals(that.file);
    }

    @Override
    public int hashCode() {
        return file.hashCode();
    }

    @Override
    public String toString() {
        return String.format("XmlFileEntry{file=%s, size=%s, status=%s}",
                getFileName(), getFormattedSize(), getStatus());
    }
}
