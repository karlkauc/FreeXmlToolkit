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
 * Configuration object for Word documentation generation.
 * Contains all settings for page layout, content sections, and styling.
 */
public class WordDocumentationConfig {

    // === Enums ===

    /**
     * Available page sizes for Word output.
     */
    public enum PageSize {
        A4("A4"),
        LETTER("Letter"),
        LEGAL("Legal");

        private final String displayName;

        PageSize(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Page orientation options.
     */
    public enum Orientation {
        PORTRAIT("Portrait"),
        LANDSCAPE("Landscape");

        private final String displayName;

        Orientation(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Header style options for Word documents.
     */
    public enum HeaderStyle {
        PROFESSIONAL("Professional"),
        MINIMAL("Minimal"),
        COLORFUL("Colorful");

        private final String displayName;

        HeaderStyle(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    // === Configuration Fields ===

    // Page Layout
    private PageSize pageSize = PageSize.A4;
    private Orientation orientation = Orientation.PORTRAIT;

    // Content Sections
    private boolean includeCoverPage = false;
    private boolean includeToc = true;
    private boolean includeDataDictionary = true;
    private boolean includeSchemaDiagram = true;

    // Styling
    private HeaderStyle headerStyle = HeaderStyle.PROFESSIONAL;

    // === Constructors ===

    /**
     * Creates a new Word configuration with default settings.
     */
    public WordDocumentationConfig() {
        // Default values are set in field declarations
    }

    /**
     * Creates a copy of another configuration.
     *
     * @param other the configuration to copy
     */
    public WordDocumentationConfig(WordDocumentationConfig other) {
        this.pageSize = other.pageSize;
        this.orientation = other.orientation;
        this.includeCoverPage = other.includeCoverPage;
        this.includeToc = other.includeToc;
        this.includeDataDictionary = other.includeDataDictionary;
        this.includeSchemaDiagram = other.includeSchemaDiagram;
        this.headerStyle = other.headerStyle;
    }

    // === Getters and Setters ===

    public PageSize getPageSize() {
        return pageSize;
    }

    public void setPageSize(PageSize pageSize) {
        this.pageSize = pageSize;
    }

    public Orientation getOrientation() {
        return orientation;
    }

    public void setOrientation(Orientation orientation) {
        this.orientation = orientation;
    }

    public boolean isIncludeCoverPage() {
        return includeCoverPage;
    }

    public void setIncludeCoverPage(boolean includeCoverPage) {
        this.includeCoverPage = includeCoverPage;
    }

    public boolean isIncludeToc() {
        return includeToc;
    }

    public void setIncludeToc(boolean includeToc) {
        this.includeToc = includeToc;
    }

    public boolean isIncludeDataDictionary() {
        return includeDataDictionary;
    }

    public void setIncludeDataDictionary(boolean includeDataDictionary) {
        this.includeDataDictionary = includeDataDictionary;
    }

    public boolean isIncludeSchemaDiagram() {
        return includeSchemaDiagram;
    }

    public void setIncludeSchemaDiagram(boolean includeSchemaDiagram) {
        this.includeSchemaDiagram = includeSchemaDiagram;
    }

    public HeaderStyle getHeaderStyle() {
        return headerStyle;
    }

    public void setHeaderStyle(HeaderStyle headerStyle) {
        this.headerStyle = headerStyle;
    }

    @Override
    public String toString() {
        return "WordDocumentationConfig{" +
                "pageSize=" + pageSize +
                ", orientation=" + orientation +
                ", includeCoverPage=" + includeCoverPage +
                ", includeToc=" + includeToc +
                ", headerStyle=" + headerStyle +
                '}';
    }
}
