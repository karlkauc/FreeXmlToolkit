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

package org.fxt.freexmltoolkit.domain;

/**
 * Enum representing the output format for XSD documentation generation.
 */
public enum DocumentationOutputFormat {

    /**
     * HTML format - generates multiple HTML files in a directory structure
     * with navigation, search functionality, and SVG diagrams.
     */
    HTML("html", "HTML Documentation", true),

    /**
     * Microsoft Word format - generates a single .docx file containing
     * the complete documentation with embedded images.
     */
    WORD("docx", "Word Document", false),

    /**
     * PDF format - generates a single PDF file using Apache FOP
     * with XSL-FO templates for professional layout.
     */
    PDF("pdf", "PDF Document", false);

    private final String fileExtension;
    private final String displayName;
    private final boolean outputsDirectory;

    DocumentationOutputFormat(String fileExtension, String displayName, boolean outputsDirectory) {
        this.fileExtension = fileExtension;
        this.displayName = displayName;
        this.outputsDirectory = outputsDirectory;
    }

    /**
     * Returns the file extension for this format (without the dot).
     *
     * @return the file extension
     */
    public String getFileExtension() {
        return fileExtension;
    }

    /**
     * Returns the human-readable display name for this format.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns whether this format outputs a directory (multiple files)
     * or a single file.
     *
     * @return true if outputs a directory, false if outputs a single file
     */
    public boolean outputsDirectory() {
        return outputsDirectory;
    }

    /**
     * Gets the default file name for single-file outputs.
     *
     * @param schemaName the name of the schema being documented
     * @return the default file name with extension
     */
    public String getDefaultFileName(String schemaName) {
        if (outputsDirectory) {
            return schemaName;
        }
        return schemaName + "." + fileExtension;
    }
}
