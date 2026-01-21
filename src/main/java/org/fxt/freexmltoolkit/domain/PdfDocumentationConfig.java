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
 * Configuration object for PDF documentation generation.
 * Contains all settings for page layout, typography, content sections,
 * design options, and PDF metadata.
 */
public class PdfDocumentationConfig {

    // === Page Layout Enums ===

    /**
     * Available page sizes for PDF output.
     * Supports common paper formats including A4, Letter, Legal, and A3.
     */
    public enum PageSize {
        /** A4 paper size (210mm x 297mm), standard in most countries. */
        A4("A4", "210mm", "297mm"),
        /** US Letter paper size (8.5in x 11in), standard in North America. */
        LETTER("Letter", "8.5in", "11in"),
        /** US Legal paper size (8.5in x 14in), commonly used for legal documents. */
        LEGAL("Legal", "8.5in", "14in"),
        /** A3 paper size (297mm x 420mm), larger format for diagrams and posters. */
        A3("A3", "297mm", "420mm");

        private final String displayName;
        private final String portraitWidth;
        private final String portraitHeight;

        /**
         * Constructs a PageSize enum constant.
         *
         * @param displayName the human-readable name of the page size
         * @param portraitWidth the width in portrait orientation
         * @param portraitHeight the height in portrait orientation
         */
        PageSize(String displayName, String portraitWidth, String portraitHeight) {
            this.displayName = displayName;
            this.portraitWidth = portraitWidth;
            this.portraitHeight = portraitHeight;
        }

        /**
         * Returns the human-readable display name of the page size.
         *
         * @return the display name (e.g., "A4", "Letter")
         */
        public String getDisplayName() {
            return displayName;
        }

        /**
         * Returns the page width when in portrait orientation.
         *
         * @return the portrait width as a CSS-style string (e.g., "210mm" or "8.5in")
         */
        public String getPortraitWidth() {
            return portraitWidth;
        }

        /**
         * Returns the page height when in portrait orientation.
         *
         * @return the portrait height as a CSS-style string (e.g., "297mm" or "11in")
         */
        public String getPortraitHeight() {
            return portraitHeight;
        }

        /**
         * Returns the string representation of this page size.
         *
         * @return the display name of the page size
         */
        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Page orientation options for PDF output.
     * Controls whether the page is taller than wide (portrait) or wider than tall (landscape).
     */
    public enum Orientation {
        /** Portrait orientation where the page is taller than wide. */
        PORTRAIT("Portrait"),
        /** Landscape orientation where the page is wider than tall. */
        LANDSCAPE("Landscape");

        private final String displayName;

        /**
         * Constructs an Orientation enum constant.
         *
         * @param displayName the human-readable name of the orientation
         */
        Orientation(String displayName) {
            this.displayName = displayName;
        }

        /**
         * Returns the human-readable display name of the orientation.
         *
         * @return the display name (e.g., "Portrait", "Landscape")
         */
        public String getDisplayName() {
            return displayName;
        }

        /**
         * Returns the string representation of this orientation.
         *
         * @return the display name of the orientation
         */
        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Page margin presets for PDF output.
     * Defines the amount of white space around the content on each page.
     */
    public enum Margins {
        /** Narrow margins (15mm) for maximum content area. */
        NARROW("Narrow", "15mm"),
        /** Normal margins (20mm) for standard documents. */
        NORMAL("Normal", "20mm"),
        /** Wide margins (30mm) for documents requiring more white space. */
        WIDE("Wide", "30mm");

        private final String displayName;
        private final String value;

        /**
         * Constructs a Margins enum constant.
         *
         * @param displayName the human-readable name of the margin preset
         * @param value the margin size as a CSS-style measurement string
         */
        Margins(String displayName, String value) {
            this.displayName = displayName;
            this.value = value;
        }

        /**
         * Returns the human-readable display name of the margin preset.
         *
         * @return the display name (e.g., "Narrow", "Normal", "Wide")
         */
        public String getDisplayName() {
            return displayName;
        }

        /**
         * Returns the margin size value.
         *
         * @return the margin size as a CSS-style string (e.g., "20mm")
         */
        public String getValue() {
            return value;
        }

        /**
         * Returns the string representation of this margin preset.
         *
         * @return a formatted string containing the display name and value
         */
        @Override
        public String toString() {
            return displayName + " (" + value + ")";
        }
    }

    // === Typography Enums ===

    /**
     * Font size options for body text in the PDF document.
     * Ranges from compact (9pt) to large (12pt) for different readability needs.
     */
    public enum FontSize {
        /** Compact 9 point font size for dense content. */
        PT_9("9pt", "9pt"),
        /** Standard 10 point font size. */
        PT_10("10pt", "10pt"),
        /** Default 11 point font size for good readability. */
        PT_11("11pt", "11pt"),
        /** Large 12 point font size for enhanced readability. */
        PT_12("12pt", "12pt");

        private final String displayName;
        private final String value;

        /**
         * Constructs a FontSize enum constant.
         *
         * @param displayName the human-readable name of the font size
         * @param value the font size as a CSS-style point value
         */
        FontSize(String displayName, String value) {
            this.displayName = displayName;
            this.value = value;
        }

        /**
         * Returns the human-readable display name of the font size.
         *
         * @return the display name (e.g., "10pt", "11pt")
         */
        public String getDisplayName() {
            return displayName;
        }

        /**
         * Returns the font size value for use in XSL-FO.
         *
         * @return the font size as a CSS-style string (e.g., "11pt")
         */
        public String getValue() {
            return value;
        }

        /**
         * Returns the string representation of this font size.
         *
         * @return the display name of the font size
         */
        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Font family options for PDF output.
     * Uses standard PDF base fonts that are guaranteed to be available in all PDF viewers.
     */
    public enum FontFamily {
        /** Helvetica sans-serif font for modern, clean appearance. */
        HELVETICA("Helvetica", "Helvetica", "sans-serif"),
        /** Times serif font for traditional, formal documents. */
        TIMES("Times", "Times", "serif"),
        /** Courier monospace font for code and technical content. */
        COURIER("Courier", "Courier", "monospace");

        private final String displayName;
        private final String fontName;
        private final String fontType;

        /**
         * Constructs a FontFamily enum constant.
         *
         * @param displayName the human-readable name of the font family
         * @param fontName the actual font name for XSL-FO
         * @param fontType the CSS generic font family (serif, sans-serif, monospace)
         */
        FontFamily(String displayName, String fontName, String fontType) {
            this.displayName = displayName;
            this.fontName = fontName;
            this.fontType = fontType;
        }

        /**
         * Returns the human-readable display name of the font family.
         *
         * @return the display name (e.g., "Helvetica", "Times")
         */
        public String getDisplayName() {
            return displayName;
        }

        /**
         * Returns the font name for use in XSL-FO.
         *
         * @return the actual font name (e.g., "Helvetica")
         */
        public String getFontName() {
            return fontName;
        }

        /**
         * Returns the generic CSS font type category.
         *
         * @return the font type (e.g., "serif", "sans-serif", "monospace")
         */
        public String getFontType() {
            return fontType;
        }

        /**
         * Returns the string representation of this font family.
         *
         * @return the display name of the font family
         */
        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Heading style options for section titles in the PDF document.
     * Defines the visual appearance of headings including font weight, decoration, and color.
     */
    public enum HeadingStyle {
        /** Bold blue headings for a modern, professional look. */
        BOLD_BLUE("Bold Blue", true, false, "#2563EB"),
        /** Bold black headings for a classic, formal appearance. */
        BOLD_BLACK("Bold Black", true, false, "#000000"),
        /** Underlined headings without bold for a subtle, traditional style. */
        UNDERLINED("Underlined", false, true, "#000000");

        private final String displayName;
        private final boolean bold;
        private final boolean underlined;
        private final String color;

        /**
         * Constructs a HeadingStyle enum constant.
         *
         * @param displayName the human-readable name of the heading style
         * @param bold whether headings should be bold
         * @param underlined whether headings should be underlined
         * @param color the text color as a hex string
         */
        HeadingStyle(String displayName, boolean bold, boolean underlined, String color) {
            this.displayName = displayName;
            this.bold = bold;
            this.underlined = underlined;
            this.color = color;
        }

        /**
         * Returns the human-readable display name of the heading style.
         *
         * @return the display name (e.g., "Bold Blue", "Underlined")
         */
        public String getDisplayName() {
            return displayName;
        }

        /**
         * Returns whether headings should be rendered in bold.
         *
         * @return true if headings should be bold, false otherwise
         */
        public boolean isBold() {
            return bold;
        }

        /**
         * Returns whether headings should be underlined.
         *
         * @return true if headings should be underlined, false otherwise
         */
        public boolean isUnderlined() {
            return underlined;
        }

        /**
         * Returns the heading text color.
         *
         * @return the color as a hex string (e.g., "#2563EB")
         */
        public String getColor() {
            return color;
        }

        /**
         * Returns the string representation of this heading style.
         *
         * @return the display name of the heading style
         */
        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Line spacing options for body text in the PDF document.
     * Controls the vertical space between lines of text for readability.
     */
    public enum LineSpacing {
        /** Compact line spacing (1.0) for dense content with minimal vertical space. */
        COMPACT("Compact", "1.0"),
        /** Normal line spacing (1.2) for standard document readability. */
        NORMAL("Normal", "1.2"),
        /** Relaxed line spacing (1.5) for enhanced readability with more vertical space. */
        RELAXED("Relaxed", "1.5");

        private final String displayName;
        private final String value;

        /**
         * Constructs a LineSpacing enum constant.
         *
         * @param displayName the human-readable name of the line spacing option
         * @param value the line height multiplier as a string
         */
        LineSpacing(String displayName, String value) {
            this.displayName = displayName;
            this.value = value;
        }

        /**
         * Returns the human-readable display name of the line spacing option.
         *
         * @return the display name (e.g., "Compact", "Normal", "Relaxed")
         */
        public String getDisplayName() {
            return displayName;
        }

        /**
         * Returns the line height multiplier value.
         *
         * @return the line spacing multiplier (e.g., "1.0", "1.2", "1.5")
         */
        public String getValue() {
            return value;
        }

        /**
         * Returns the string representation of this line spacing option.
         *
         * @return the display name of the line spacing
         */
        @Override
        public String toString() {
            return displayName;
        }
    }

    // === Header & Footer Enums ===

    /**
     * Header and footer style options for PDF pages.
     * Controls the amount of information displayed in the header and footer regions.
     */
    public enum HeaderFooterStyle {
        /** Standard style with full header/footer content including title and metadata. */
        STANDARD("Standard"),
        /** Minimal style with reduced header/footer content. */
        MINIMAL("Minimal"),
        /** No header/footer content displayed. */
        NONE("None");

        private final String displayName;

        /**
         * Constructs a HeaderFooterStyle enum constant.
         *
         * @param displayName the human-readable name of the header/footer style
         */
        HeaderFooterStyle(String displayName) {
            this.displayName = displayName;
        }

        /**
         * Returns the human-readable display name of the header/footer style.
         *
         * @return the display name (e.g., "Standard", "Minimal", "None")
         */
        public String getDisplayName() {
            return displayName;
        }

        /**
         * Returns the string representation of this header/footer style.
         *
         * @return the display name of the style
         */
        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Page number position options for PDF footer.
     * Controls the horizontal alignment of page numbers on each page.
     */
    public enum PageNumberPosition {
        /** Page numbers aligned to the left side of the footer. */
        LEFT("Left", "left"),
        /** Page numbers centered in the footer. */
        CENTER("Center", "center"),
        /** Page numbers aligned to the right side of the footer. */
        RIGHT("Right", "right");

        private final String displayName;
        private final String alignment;

        /**
         * Constructs a PageNumberPosition enum constant.
         *
         * @param displayName the human-readable name of the position
         * @param alignment the CSS text-align value for XSL-FO
         */
        PageNumberPosition(String displayName, String alignment) {
            this.displayName = displayName;
            this.alignment = alignment;
        }

        /**
         * Returns the human-readable display name of the position.
         *
         * @return the display name (e.g., "Left", "Center", "Right")
         */
        public String getDisplayName() {
            return displayName;
        }

        /**
         * Returns the CSS alignment value for use in XSL-FO.
         *
         * @return the alignment value (e.g., "left", "center", "right")
         */
        public String getAlignment() {
            return alignment;
        }

        /**
         * Returns the string representation of this page number position.
         *
         * @return the display name of the position
         */
        @Override
        public String toString() {
            return displayName;
        }
    }

    // === Design & Colors Enums ===

    /**
     * Color scheme options for the PDF document.
     * Defines a coordinated set of colors for headings, backgrounds, and tables.
     */
    public enum ColorScheme {
        /** Blue color scheme with professional blue tones. */
        BLUE("Blue", "#2563EB", "#EFF6FF", "#DBEAFE"),
        /** Green color scheme with natural green tones. */
        GREEN("Green", "#059669", "#ECFDF5", "#D1FAE5"),
        /** Purple color scheme with elegant purple tones. */
        PURPLE("Purple", "#7C3AED", "#F5F3FF", "#EDE9FE"),
        /** Grayscale color scheme for a neutral, minimalist look. */
        GRAYSCALE("Grayscale", "#374151", "#F9FAFB", "#F3F4F6"),
        /** Professional color scheme with dark blue corporate tones. */
        PROFESSIONAL("Professional", "#1E3A5F", "#F0F4F8", "#E2E8F0");

        private final String displayName;
        private final String primaryColor;
        private final String lightBackground;
        private final String tableHeaderBg;

        /**
         * Constructs a ColorScheme enum constant.
         *
         * @param displayName the human-readable name of the color scheme
         * @param primaryColor the primary accent color as a hex string
         * @param lightBackground the light background color as a hex string
         * @param tableHeaderBg the table header background color as a hex string
         */
        ColorScheme(String displayName, String primaryColor, String lightBackground, String tableHeaderBg) {
            this.displayName = displayName;
            this.primaryColor = primaryColor;
            this.lightBackground = lightBackground;
            this.tableHeaderBg = tableHeaderBg;
        }

        /**
         * Returns the human-readable display name of the color scheme.
         *
         * @return the display name (e.g., "Blue", "Professional")
         */
        public String getDisplayName() {
            return displayName;
        }

        /**
         * Returns the primary accent color for headings and highlights.
         *
         * @return the primary color as a hex string (e.g., "#2563EB")
         */
        public String getPrimaryColor() {
            return primaryColor;
        }

        /**
         * Returns the light background color for content areas.
         *
         * @return the light background color as a hex string (e.g., "#EFF6FF")
         */
        public String getLightBackground() {
            return lightBackground;
        }

        /**
         * Returns the table header background color.
         *
         * @return the table header background color as a hex string (e.g., "#DBEAFE")
         */
        public String getTableHeaderBg() {
            return tableHeaderBg;
        }

        /**
         * Returns the string representation of this color scheme.
         *
         * @return the display name of the color scheme
         */
        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Table style options for data tables in the PDF document.
     * Controls the visual appearance of borders and alternating row colors.
     */
    public enum TableStyle {
        /** Bordered style with simple outer borders and header divider. */
        BORDERED("Bordered"),
        /** Zebra stripes with alternating row background colors for readability. */
        ZEBRA_STRIPES("Zebra Stripes"),
        /** Minimal style with minimal borders for a clean look. */
        MINIMAL("Minimal"),
        /** Full grid style with borders around every cell. */
        FULL_GRID("Full Grid");

        private final String displayName;

        /**
         * Constructs a TableStyle enum constant.
         *
         * @param displayName the human-readable name of the table style
         */
        TableStyle(String displayName) {
            this.displayName = displayName;
        }

        /**
         * Returns the human-readable display name of the table style.
         *
         * @return the display name (e.g., "Bordered", "Zebra Stripes")
         */
        public String getDisplayName() {
            return displayName;
        }

        /**
         * Returns the string representation of this table style.
         *
         * @return the display name of the table style
         */
        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Watermark options for PDF pages.
     * Adds semi-transparent diagonal text across each page to indicate document status.
     */
    public enum Watermark {
        /** No watermark displayed on pages. */
        NONE("None", ""),
        /** "DRAFT" watermark for work-in-progress documents. */
        DRAFT("Draft", "DRAFT"),
        /** "CONFIDENTIAL" watermark for sensitive documents. */
        CONFIDENTIAL("Confidential", "CONFIDENTIAL"),
        /** "INTERNAL USE ONLY" watermark for internal distribution documents. */
        INTERNAL_USE_ONLY("Internal Use Only", "INTERNAL USE ONLY");

        private final String displayName;
        private final String text;

        /**
         * Constructs a Watermark enum constant.
         *
         * @param displayName the human-readable name of the watermark option
         * @param text the actual text to display as the watermark
         */
        Watermark(String displayName, String text) {
            this.displayName = displayName;
            this.text = text;
        }

        /**
         * Returns the human-readable display name of the watermark option.
         *
         * @return the display name (e.g., "Draft", "Confidential")
         */
        public String getDisplayName() {
            return displayName;
        }

        /**
         * Returns the watermark text to display on pages.
         *
         * @return the watermark text, or empty string if no watermark
         */
        public String getText() {
            return text;
        }

        /**
         * Returns the string representation of this watermark option.
         *
         * @return the display name of the watermark
         */
        @Override
        public String toString() {
            return displayName;
        }
    }

    // === Configuration Fields ===

    // Page Layout
    private PageSize pageSize = PageSize.A4;
    private Orientation orientation = Orientation.PORTRAIT;
    private Margins margins = Margins.NORMAL;

    // Content Sections
    private boolean includeCoverPage = true;
    private boolean includeToc = true;
    private boolean includeSchemaOverview = true;
    private boolean includeSchemaDiagram = true;
    private boolean includeElementDiagrams = false;
    private boolean includeComplexTypes = true;
    private boolean includeSimpleTypes = true;
    private boolean includeDataDictionary = true;

    // Typography
    private FontSize fontSize = FontSize.PT_11;
    private FontFamily fontFamily = FontFamily.HELVETICA;
    private HeadingStyle headingStyle = HeadingStyle.BOLD_BLUE;
    private LineSpacing lineSpacing = LineSpacing.NORMAL;

    // Header & Footer
    private HeaderFooterStyle headerStyle = HeaderFooterStyle.STANDARD;
    private HeaderFooterStyle footerStyle = HeaderFooterStyle.STANDARD;
    private boolean includePageNumbers = true;
    private PageNumberPosition pageNumberPosition = PageNumberPosition.CENTER;

    // Design & Colors
    private ColorScheme colorScheme = ColorScheme.BLUE;
    private TableStyle tableStyle = TableStyle.BORDERED;
    private Watermark watermark = Watermark.NONE;

    // PDF Metadata
    private boolean generateBookmarks = true;
    private String title;      // null = auto (schema name)
    private String author;     // null = auto (system user)
    private String subject;
    private String keywords;

    // === Constructors ===

    /**
     * Creates a new PDF configuration with default settings.
     */
    public PdfDocumentationConfig() {
        // Default values are set in field declarations
    }

    /**
     * Creates a copy of another configuration.
     *
     * @param other the configuration to copy
     */
    public PdfDocumentationConfig(PdfDocumentationConfig other) {
        this.pageSize = other.pageSize;
        this.orientation = other.orientation;
        this.margins = other.margins;
        this.includeCoverPage = other.includeCoverPage;
        this.includeToc = other.includeToc;
        this.includeSchemaOverview = other.includeSchemaOverview;
        this.includeSchemaDiagram = other.includeSchemaDiagram;
        this.includeElementDiagrams = other.includeElementDiagrams;
        this.includeComplexTypes = other.includeComplexTypes;
        this.includeSimpleTypes = other.includeSimpleTypes;
        this.includeDataDictionary = other.includeDataDictionary;
        this.fontSize = other.fontSize;
        this.fontFamily = other.fontFamily;
        this.headingStyle = other.headingStyle;
        this.lineSpacing = other.lineSpacing;
        this.headerStyle = other.headerStyle;
        this.footerStyle = other.footerStyle;
        this.includePageNumbers = other.includePageNumbers;
        this.pageNumberPosition = other.pageNumberPosition;
        this.colorScheme = other.colorScheme;
        this.tableStyle = other.tableStyle;
        this.watermark = other.watermark;
        this.generateBookmarks = other.generateBookmarks;
        this.title = other.title;
        this.author = other.author;
        this.subject = other.subject;
        this.keywords = other.keywords;
    }

    // === Helper Methods for XSL-FO Parameters ===

    /**
     * Gets the page width based on page size and orientation.
     *
     * @return the page width as a CSS-style string (e.g., "210mm" or "8.5in")
     */
    public String getPageWidth() {
        return switch (pageSize) {
            case A4 -> orientation == Orientation.PORTRAIT ? "210mm" : "297mm";
            case LETTER -> orientation == Orientation.PORTRAIT ? "8.5in" : "11in";
            case LEGAL -> orientation == Orientation.PORTRAIT ? "8.5in" : "14in";
            case A3 -> orientation == Orientation.PORTRAIT ? "297mm" : "420mm";
        };
    }

    /**
     * Gets the page height based on page size and orientation.
     *
     * @return the page height as a CSS-style string (e.g., "297mm" or "11in")
     */
    public String getPageHeight() {
        return switch (pageSize) {
            case A4 -> orientation == Orientation.PORTRAIT ? "297mm" : "210mm";
            case LETTER -> orientation == Orientation.PORTRAIT ? "11in" : "8.5in";
            case LEGAL -> orientation == Orientation.PORTRAIT ? "14in" : "8.5in";
            case A3 -> orientation == Orientation.PORTRAIT ? "420mm" : "297mm";
        };
    }

    /**
     * Gets the margin size value.
     *
     * @return the margin size as a CSS-style string (e.g., "20mm")
     */
    public String getMarginSize() {
        return margins.getValue();
    }

    /**
     * Gets the primary color from the color scheme.
     *
     * @return the primary color as a hex string (e.g., "#2563EB")
     */
    public String getPrimaryColor() {
        return colorScheme.getPrimaryColor();
    }

    /**
     * Gets the font size value.
     *
     * @return the font size as a CSS-style string (e.g., "11pt")
     */
    public String getFontSizeValue() {
        return fontSize.getValue();
    }

    /**
     * Gets the font family name for XSL-FO.
     *
     * @return the font family name (e.g., "Helvetica")
     */
    public String getFontFamilyName() {
        return fontFamily.getFontName();
    }

    /**
     * Gets the line height multiplier.
     *
     * @return the line spacing value (e.g., "1.2")
     */
    public String getLineHeightValue() {
        return lineSpacing.getValue();
    }

    /**
     * Checks if the watermark should be rendered.
     *
     * @return true if a watermark is configured
     */
    public boolean hasWatermark() {
        return watermark != Watermark.NONE;
    }

    /**
     * Gets the watermark text.
     *
     * @return the watermark text, or empty string if none
     */
    public String getWatermarkText() {
        return watermark.getText();
    }

    // === Getters and Setters ===

    /**
     * Returns the configured page size for the PDF output.
     *
     * @return the page size (defaults to A4)
     */
    public PageSize getPageSize() {
        return pageSize;
    }

    /**
     * Sets the page size for the PDF output.
     *
     * @param pageSize the page size to use
     */
    public void setPageSize(PageSize pageSize) {
        this.pageSize = pageSize;
    }

    /**
     * Returns the configured page orientation.
     *
     * @return the page orientation (defaults to PORTRAIT)
     */
    public Orientation getOrientation() {
        return orientation;
    }

    /**
     * Sets the page orientation for the PDF output.
     *
     * @param orientation the page orientation to use
     */
    public void setOrientation(Orientation orientation) {
        this.orientation = orientation;
    }

    /**
     * Returns the configured page margins.
     *
     * @return the margin preset (defaults to NORMAL)
     */
    public Margins getMargins() {
        return margins;
    }

    /**
     * Sets the page margins for the PDF output.
     *
     * @param margins the margin preset to use
     */
    public void setMargins(Margins margins) {
        this.margins = margins;
    }

    /**
     * Returns whether a cover page should be included in the PDF.
     *
     * @return true if a cover page should be included, false otherwise
     */
    public boolean isIncludeCoverPage() {
        return includeCoverPage;
    }

    /**
     * Sets whether to include a cover page in the PDF.
     *
     * @param includeCoverPage true to include a cover page, false otherwise
     */
    public void setIncludeCoverPage(boolean includeCoverPage) {
        this.includeCoverPage = includeCoverPage;
    }

    /**
     * Returns whether a table of contents should be included in the PDF.
     *
     * @return true if a table of contents should be included, false otherwise
     */
    public boolean isIncludeToc() {
        return includeToc;
    }

    /**
     * Sets whether to include a table of contents in the PDF.
     *
     * @param includeToc true to include a table of contents, false otherwise
     */
    public void setIncludeToc(boolean includeToc) {
        this.includeToc = includeToc;
    }

    /**
     * Returns whether a schema overview section should be included in the PDF.
     *
     * @return true if a schema overview should be included, false otherwise
     */
    public boolean isIncludeSchemaOverview() {
        return includeSchemaOverview;
    }

    /**
     * Sets whether to include a schema overview section in the PDF.
     *
     * @param includeSchemaOverview true to include a schema overview, false otherwise
     */
    public void setIncludeSchemaOverview(boolean includeSchemaOverview) {
        this.includeSchemaOverview = includeSchemaOverview;
    }

    /**
     * Returns whether a schema diagram should be included in the PDF.
     *
     * @return true if a schema diagram should be included, false otherwise
     */
    public boolean isIncludeSchemaDiagram() {
        return includeSchemaDiagram;
    }

    /**
     * Sets whether to include a schema diagram in the PDF.
     *
     * @param includeSchemaDiagram true to include a schema diagram, false otherwise
     */
    public void setIncludeSchemaDiagram(boolean includeSchemaDiagram) {
        this.includeSchemaDiagram = includeSchemaDiagram;
    }

    /**
     * Returns whether element diagrams should be included in the PDF.
     *
     * @return true if element diagrams should be included, false otherwise
     */
    public boolean isIncludeElementDiagrams() {
        return includeElementDiagrams;
    }

    /**
     * Sets whether to include element diagrams in the PDF.
     *
     * @param includeElementDiagrams true to include element diagrams, false otherwise
     */
    public void setIncludeElementDiagrams(boolean includeElementDiagrams) {
        this.includeElementDiagrams = includeElementDiagrams;
    }

    /**
     * Returns whether complex types documentation should be included in the PDF.
     *
     * @return true if complex types should be included, false otherwise
     */
    public boolean isIncludeComplexTypes() {
        return includeComplexTypes;
    }

    /**
     * Sets whether to include complex types documentation in the PDF.
     *
     * @param includeComplexTypes true to include complex types, false otherwise
     */
    public void setIncludeComplexTypes(boolean includeComplexTypes) {
        this.includeComplexTypes = includeComplexTypes;
    }

    /**
     * Returns whether simple types documentation should be included in the PDF.
     *
     * @return true if simple types should be included, false otherwise
     */
    public boolean isIncludeSimpleTypes() {
        return includeSimpleTypes;
    }

    /**
     * Sets whether to include simple types documentation in the PDF.
     *
     * @param includeSimpleTypes true to include simple types, false otherwise
     */
    public void setIncludeSimpleTypes(boolean includeSimpleTypes) {
        this.includeSimpleTypes = includeSimpleTypes;
    }

    /**
     * Returns whether a data dictionary should be included in the PDF.
     *
     * @return true if a data dictionary should be included, false otherwise
     */
    public boolean isIncludeDataDictionary() {
        return includeDataDictionary;
    }

    /**
     * Sets whether to include a data dictionary in the PDF.
     *
     * @param includeDataDictionary true to include a data dictionary, false otherwise
     */
    public void setIncludeDataDictionary(boolean includeDataDictionary) {
        this.includeDataDictionary = includeDataDictionary;
    }

    /**
     * Returns the configured font size for body text.
     *
     * @return the font size (defaults to PT_11)
     */
    public FontSize getFontSize() {
        return fontSize;
    }

    /**
     * Sets the font size for body text in the PDF.
     *
     * @param fontSize the font size to use
     */
    public void setFontSize(FontSize fontSize) {
        this.fontSize = fontSize;
    }

    /**
     * Returns the configured font family.
     *
     * @return the font family (defaults to HELVETICA)
     */
    public FontFamily getFontFamily() {
        return fontFamily;
    }

    /**
     * Sets the font family for the PDF.
     *
     * @param fontFamily the font family to use
     */
    public void setFontFamily(FontFamily fontFamily) {
        this.fontFamily = fontFamily;
    }

    /**
     * Returns the configured heading style.
     *
     * @return the heading style (defaults to BOLD_BLUE)
     */
    public HeadingStyle getHeadingStyle() {
        return headingStyle;
    }

    /**
     * Sets the heading style for section titles in the PDF.
     *
     * @param headingStyle the heading style to use
     */
    public void setHeadingStyle(HeadingStyle headingStyle) {
        this.headingStyle = headingStyle;
    }

    /**
     * Returns the configured line spacing.
     *
     * @return the line spacing (defaults to NORMAL)
     */
    public LineSpacing getLineSpacing() {
        return lineSpacing;
    }

    /**
     * Sets the line spacing for body text in the PDF.
     *
     * @param lineSpacing the line spacing to use
     */
    public void setLineSpacing(LineSpacing lineSpacing) {
        this.lineSpacing = lineSpacing;
    }

    /**
     * Returns the configured header style.
     *
     * @return the header style (defaults to STANDARD)
     */
    public HeaderFooterStyle getHeaderStyle() {
        return headerStyle;
    }

    /**
     * Sets the header style for PDF pages.
     *
     * @param headerStyle the header style to use
     */
    public void setHeaderStyle(HeaderFooterStyle headerStyle) {
        this.headerStyle = headerStyle;
    }

    /**
     * Returns the configured footer style.
     *
     * @return the footer style (defaults to STANDARD)
     */
    public HeaderFooterStyle getFooterStyle() {
        return footerStyle;
    }

    /**
     * Sets the footer style for PDF pages.
     *
     * @param footerStyle the footer style to use
     */
    public void setFooterStyle(HeaderFooterStyle footerStyle) {
        this.footerStyle = footerStyle;
    }

    /**
     * Returns whether page numbers should be included in the PDF footer.
     *
     * @return true if page numbers should be included, false otherwise
     */
    public boolean isIncludePageNumbers() {
        return includePageNumbers;
    }

    /**
     * Sets whether to include page numbers in the PDF footer.
     *
     * @param includePageNumbers true to include page numbers, false otherwise
     */
    public void setIncludePageNumbers(boolean includePageNumbers) {
        this.includePageNumbers = includePageNumbers;
    }

    /**
     * Returns the configured page number position.
     *
     * @return the page number position (defaults to CENTER)
     */
    public PageNumberPosition getPageNumberPosition() {
        return pageNumberPosition;
    }

    /**
     * Sets the page number position in the footer.
     *
     * @param pageNumberPosition the page number position to use
     */
    public void setPageNumberPosition(PageNumberPosition pageNumberPosition) {
        this.pageNumberPosition = pageNumberPosition;
    }

    /**
     * Returns the configured color scheme.
     *
     * @return the color scheme (defaults to BLUE)
     */
    public ColorScheme getColorScheme() {
        return colorScheme;
    }

    /**
     * Sets the color scheme for the PDF document.
     *
     * @param colorScheme the color scheme to use
     */
    public void setColorScheme(ColorScheme colorScheme) {
        this.colorScheme = colorScheme;
    }

    /**
     * Returns the configured table style.
     *
     * @return the table style (defaults to BORDERED)
     */
    public TableStyle getTableStyle() {
        return tableStyle;
    }

    /**
     * Sets the table style for data tables in the PDF.
     *
     * @param tableStyle the table style to use
     */
    public void setTableStyle(TableStyle tableStyle) {
        this.tableStyle = tableStyle;
    }

    /**
     * Returns the configured watermark option.
     *
     * @return the watermark option (defaults to NONE)
     */
    public Watermark getWatermark() {
        return watermark;
    }

    /**
     * Sets the watermark option for PDF pages.
     *
     * @param watermark the watermark option to use
     */
    public void setWatermark(Watermark watermark) {
        this.watermark = watermark;
    }

    /**
     * Returns whether PDF bookmarks should be generated.
     *
     * @return true if bookmarks should be generated, false otherwise
     */
    public boolean isGenerateBookmarks() {
        return generateBookmarks;
    }

    /**
     * Sets whether to generate PDF bookmarks for navigation.
     *
     * @param generateBookmarks true to generate bookmarks, false otherwise
     */
    public void setGenerateBookmarks(boolean generateBookmarks) {
        this.generateBookmarks = generateBookmarks;
    }

    /**
     * Returns the configured PDF document title.
     *
     * @return the title, or null to use the schema name automatically
     */
    public String getTitle() {
        return title;
    }

    /**
     * Sets the PDF document title.
     *
     * @param title the title to use, or null to derive from schema name
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Returns the configured PDF document author.
     *
     * @return the author, or null to use the system user automatically
     */
    public String getAuthor() {
        return author;
    }

    /**
     * Sets the PDF document author.
     *
     * @param author the author name to use, or null to use system user
     */
    public void setAuthor(String author) {
        this.author = author;
    }

    /**
     * Returns the configured PDF document subject.
     *
     * @return the subject, or null if not set
     */
    public String getSubject() {
        return subject;
    }

    /**
     * Sets the PDF document subject metadata.
     *
     * @param subject the subject description
     */
    public void setSubject(String subject) {
        this.subject = subject;
    }

    /**
     * Returns the configured PDF document keywords.
     *
     * @return the keywords, or null if not set
     */
    public String getKeywords() {
        return keywords;
    }

    /**
     * Sets the PDF document keywords metadata.
     *
     * @param keywords the keywords for search and indexing
     */
    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    /**
     * Returns a string representation of this configuration.
     * Includes key settings for debugging and logging purposes.
     *
     * @return a string representation with page size, orientation, margins, color scheme, and font size
     */
    @Override
    public String toString() {
        return "PdfDocumentationConfig{" +
                "pageSize=" + pageSize +
                ", orientation=" + orientation +
                ", margins=" + margins +
                ", colorScheme=" + colorScheme +
                ", fontSize=" + fontSize +
                '}';
    }
}
