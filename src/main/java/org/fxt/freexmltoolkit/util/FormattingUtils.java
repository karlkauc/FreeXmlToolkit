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

package org.fxt.freexmltoolkit.util;

import java.io.File;
import java.util.Locale;

/**
 * Utility class for common formatting operations.
 *
 * <p>This class provides static methods for:
 * <ul>
 *   <li>Time formatting (elapsed time in MM:SS or HH:MM:SS format)</li>
 *   <li>File size formatting (bytes to human-readable format)</li>
 *   <li>CSV and JSON escaping</li>
 *   <li>File extension extraction</li>
 * </ul>
 *
 * <p>These methods are extracted from controllers to enable unit testing
 * without JavaFX dependencies.
 *
 * @since 2.0
 */
public final class FormattingUtils {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private FormattingUtils() {
        // Utility class - no instantiation
    }

    /**
     * Formats elapsed time in milliseconds to MM:SS or HH:MM:SS format.
     *
     * @param elapsedMillis elapsed time in milliseconds
     * @return formatted time string
     */
    public static String formatElapsedTime(long elapsedMillis) {
        long totalSeconds = elapsedMillis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    /**
     * Formats a file size in bytes to a human-readable format.
     * Uses Locale.US for consistent decimal separator (dot).
     *
     * @param bytes the file size in bytes
     * @return formatted string (e.g., "1.50 KB", "2.30 MB")
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * Escapes a field for CSV format (handles quotes and commas).
     *
     * @param field the field to escape
     * @return escaped field value
     */
    public static String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }
        // Escape double quotes by doubling them
        return field.replace("\"", "\"\"");
    }

    /**
     * Escapes a value for CSV format with proper quoting.
     * Wraps the value in quotes if it contains special characters.
     *
     * @param value the value to escape
     * @return escaped and possibly quoted value
     */
    public static String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Escapes a value for JSON format.
     *
     * @param value the value to escape
     * @return escaped value
     */
    public static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Gets the file extension from a file.
     *
     * @param file the file
     * @return the extension (without dot), or empty string if none
     */
    public static String getFileExtension(File file) {
        if (file == null) return "";
        String name = file.getName();
        int lastIndexOf = name.lastIndexOf(".");
        if (lastIndexOf == -1) {
            return "";
        }
        return name.substring(lastIndexOf + 1);
    }

    /**
     * Gets the file extension from a filename.
     *
     * @param filename the filename
     * @return the extension (without dot), or empty string if none
     */
    public static String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) return "";
        int lastIndexOf = filename.lastIndexOf(".");
        if (lastIndexOf == -1) {
            return "";
        }
        return filename.substring(lastIndexOf + 1);
    }
}
