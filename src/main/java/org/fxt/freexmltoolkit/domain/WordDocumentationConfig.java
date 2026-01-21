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
        /** A4 paper size (210 x 297 mm). */
        A4("A4"),
        /** US Letter paper size (8.5 x 11 inches). */
        LETTER("Letter"),
        /** US Legal paper size (8.5 x 14 inches). */
        LEGAL("Legal");

        private final String displayName;

        PageSize(String displayName) {
            this.displayName = displayName;
        }

        /**
         * Returns the human-readable display name.
         *
         * @return the display name
         */
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
        /** Portrait orientation (taller than wide). */
        PORTRAIT("Portrait"),
        /** Landscape orientation (wider than tall). */
        LANDSCAPE("Landscape");

        private final String displayName;

        Orientation(String displayName) {
            this.displayName = displayName;
        }

        /**
         * Returns the human-readable display name.
         *
         * @return the display name
         */
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
        /** Professional style with formal formatting. */
        PROFESSIONAL("Professional"),
        /** Minimal style with clean, simple formatting. */
        MINIMAL("Minimal"),
        /** Colorful style with vibrant formatting. */
        COLORFUL("Colorful");

        private final String displayName;

        HeaderStyle(String displayName) {
            this.displayName = displayName;
        }

        /**
         * Returns the human-readable display name.
         *
         * @return the display name
         */
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
    private boolean includeElementDiagrams = false;

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
        this.includeElementDiagrams = other.includeElementDiagrams;
        this.headerStyle = other.headerStyle;
    }

    // === Getters and Setters ===

    /**
     * Returns the page size setting.
     *
     * @return the page size
     */
    public PageSize getPageSize() {
        return pageSize;
    }

    /**
     * Sets the page size.
     *
     * @param pageSize the page size to set
     */
    public void setPageSize(PageSize pageSize) {
        this.pageSize = pageSize;
    }

    /**
     * Returns the page orientation setting.
     *
     * @return the orientation
     */
    public Orientation getOrientation() {
        return orientation;
    }

    /**
     * Sets the page orientation.
     *
     * @param orientation the orientation to set
     */
    public void setOrientation(Orientation orientation) {
        this.orientation = orientation;
    }

    /**
     * Returns whether to include a cover page.
     *
     * @return true if cover page should be included
     */
    public boolean isIncludeCoverPage() {
        return includeCoverPage;
    }

    /**
     * Sets whether to include a cover page.
     *
     * @param includeCoverPage true to include cover page
     */
    public void setIncludeCoverPage(boolean includeCoverPage) {
        this.includeCoverPage = includeCoverPage;
    }

    /**
     * Returns whether to include a table of contents.
     *
     * @return true if TOC should be included
     */
    public boolean isIncludeToc() {
        return includeToc;
    }

    /**
     * Sets whether to include a table of contents.
     *
     * @param includeToc true to include TOC
     */
    public void setIncludeToc(boolean includeToc) {
        this.includeToc = includeToc;
    }

    /**
     * Returns whether to include the data dictionary section.
     *
     * @return true if data dictionary should be included
     */
    public boolean isIncludeDataDictionary() {
        return includeDataDictionary;
    }

    /**
     * Sets whether to include the data dictionary section.
     *
     * @param includeDataDictionary true to include data dictionary
     */
    public void setIncludeDataDictionary(boolean includeDataDictionary) {
        this.includeDataDictionary = includeDataDictionary;
    }

    /**
     * Returns whether to include the schema diagram.
     *
     * @return true if schema diagram should be included
     */
    public boolean isIncludeSchemaDiagram() {
        return includeSchemaDiagram;
    }

    /**
     * Sets whether to include the schema diagram.
     *
     * @param includeSchemaDiagram true to include schema diagram
     */
    public void setIncludeSchemaDiagram(boolean includeSchemaDiagram) {
        this.includeSchemaDiagram = includeSchemaDiagram;
    }

    /**
     * Returns whether to include element diagrams.
     *
     * @return true if element diagrams should be included
     */
    public boolean isIncludeElementDiagrams() {
        return includeElementDiagrams;
    }

    /**
     * Sets whether to include element diagrams.
     *
     * @param includeElementDiagrams true to include element diagrams
     */
    public void setIncludeElementDiagrams(boolean includeElementDiagrams) {
        this.includeElementDiagrams = includeElementDiagrams;
    }

    /**
     * Returns the header style setting.
     *
     * @return the header style
     */
    public HeaderStyle getHeaderStyle() {
        return headerStyle;
    }

    /**
     * Sets the header style.
     *
     * @param headerStyle the header style to set
     */
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
