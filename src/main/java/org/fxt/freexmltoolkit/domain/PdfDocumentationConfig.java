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
     */
    public enum PageSize {
        A4("A4", "210mm", "297mm"),
        LETTER("Letter", "8.5in", "11in"),
        LEGAL("Legal", "8.5in", "14in"),
        A3("A3", "297mm", "420mm");

        private final String displayName;
        private final String portraitWidth;
        private final String portraitHeight;

        PageSize(String displayName, String portraitWidth, String portraitHeight) {
            this.displayName = displayName;
            this.portraitWidth = portraitWidth;
            this.portraitHeight = portraitHeight;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getPortraitWidth() {
            return portraitWidth;
        }

        public String getPortraitHeight() {
            return portraitHeight;
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
     * Page margin presets.
     */
    public enum Margins {
        NARROW("Narrow", "15mm"),
        NORMAL("Normal", "20mm"),
        WIDE("Wide", "30mm");

        private final String displayName;
        private final String value;

        Margins(String displayName, String value) {
            this.displayName = displayName;
            this.value = value;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return displayName + " (" + value + ")";
        }
    }

    // === Typography Enums ===

    /**
     * Font size options for body text.
     */
    public enum FontSize {
        PT_9("9pt", "9pt"),
        PT_10("10pt", "10pt"),
        PT_11("11pt", "11pt"),
        PT_12("12pt", "12pt");

        private final String displayName;
        private final String value;

        FontSize(String displayName, String value) {
            this.displayName = displayName;
            this.value = value;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Font family options. Uses standard PDF base fonts.
     */
    public enum FontFamily {
        HELVETICA("Helvetica", "Helvetica", "sans-serif"),
        TIMES("Times", "Times", "serif"),
        COURIER("Courier", "Courier", "monospace");

        private final String displayName;
        private final String fontName;
        private final String fontType;

        FontFamily(String displayName, String fontName, String fontType) {
            this.displayName = displayName;
            this.fontName = fontName;
            this.fontType = fontType;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getFontName() {
            return fontName;
        }

        public String getFontType() {
            return fontType;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Heading style options.
     */
    public enum HeadingStyle {
        BOLD_BLUE("Bold Blue", true, false, "#2563EB"),
        BOLD_BLACK("Bold Black", true, false, "#000000"),
        UNDERLINED("Underlined", false, true, "#000000");

        private final String displayName;
        private final boolean bold;
        private final boolean underlined;
        private final String color;

        HeadingStyle(String displayName, boolean bold, boolean underlined, String color) {
            this.displayName = displayName;
            this.bold = bold;
            this.underlined = underlined;
            this.color = color;
        }

        public String getDisplayName() {
            return displayName;
        }

        public boolean isBold() {
            return bold;
        }

        public boolean isUnderlined() {
            return underlined;
        }

        public String getColor() {
            return color;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Line spacing options.
     */
    public enum LineSpacing {
        COMPACT("Compact", "1.0"),
        NORMAL("Normal", "1.2"),
        RELAXED("Relaxed", "1.5");

        private final String displayName;
        private final String value;

        LineSpacing(String displayName, String value) {
            this.displayName = displayName;
            this.value = value;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    // === Header & Footer Enums ===

    /**
     * Header and footer style options.
     */
    public enum HeaderFooterStyle {
        STANDARD("Standard"),
        MINIMAL("Minimal"),
        NONE("None");

        private final String displayName;

        HeaderFooterStyle(String displayName) {
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
     * Page number position options.
     */
    public enum PageNumberPosition {
        LEFT("Left", "left"),
        CENTER("Center", "center"),
        RIGHT("Right", "right");

        private final String displayName;
        private final String alignment;

        PageNumberPosition(String displayName, String alignment) {
            this.displayName = displayName;
            this.alignment = alignment;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getAlignment() {
            return alignment;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    // === Design & Colors Enums ===

    /**
     * Color scheme options for the PDF document.
     */
    public enum ColorScheme {
        BLUE("Blue", "#2563EB", "#EFF6FF", "#DBEAFE"),
        GREEN("Green", "#059669", "#ECFDF5", "#D1FAE5"),
        PURPLE("Purple", "#7C3AED", "#F5F3FF", "#EDE9FE"),
        GRAYSCALE("Grayscale", "#374151", "#F9FAFB", "#F3F4F6"),
        PROFESSIONAL("Professional", "#1E3A5F", "#F0F4F8", "#E2E8F0");

        private final String displayName;
        private final String primaryColor;
        private final String lightBackground;
        private final String tableHeaderBg;

        ColorScheme(String displayName, String primaryColor, String lightBackground, String tableHeaderBg) {
            this.displayName = displayName;
            this.primaryColor = primaryColor;
            this.lightBackground = lightBackground;
            this.tableHeaderBg = tableHeaderBg;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getPrimaryColor() {
            return primaryColor;
        }

        public String getLightBackground() {
            return lightBackground;
        }

        public String getTableHeaderBg() {
            return tableHeaderBg;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Table style options.
     */
    public enum TableStyle {
        BORDERED("Bordered"),
        ZEBRA_STRIPES("Zebra Stripes"),
        MINIMAL("Minimal"),
        FULL_GRID("Full Grid");

        private final String displayName;

        TableStyle(String displayName) {
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
     * Watermark options.
     */
    public enum Watermark {
        NONE("None", ""),
        DRAFT("Draft", "DRAFT"),
        CONFIDENTIAL("Confidential", "CONFIDENTIAL"),
        INTERNAL_USE_ONLY("Internal Use Only", "INTERNAL USE ONLY");

        private final String displayName;
        private final String text;

        Watermark(String displayName, String text) {
            this.displayName = displayName;
            this.text = text;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getText() {
            return text;
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

    public Margins getMargins() {
        return margins;
    }

    public void setMargins(Margins margins) {
        this.margins = margins;
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

    public boolean isIncludeSchemaOverview() {
        return includeSchemaOverview;
    }

    public void setIncludeSchemaOverview(boolean includeSchemaOverview) {
        this.includeSchemaOverview = includeSchemaOverview;
    }

    public boolean isIncludeSchemaDiagram() {
        return includeSchemaDiagram;
    }

    public void setIncludeSchemaDiagram(boolean includeSchemaDiagram) {
        this.includeSchemaDiagram = includeSchemaDiagram;
    }

    public boolean isIncludeElementDiagrams() {
        return includeElementDiagrams;
    }

    public void setIncludeElementDiagrams(boolean includeElementDiagrams) {
        this.includeElementDiagrams = includeElementDiagrams;
    }

    public boolean isIncludeComplexTypes() {
        return includeComplexTypes;
    }

    public void setIncludeComplexTypes(boolean includeComplexTypes) {
        this.includeComplexTypes = includeComplexTypes;
    }

    public boolean isIncludeSimpleTypes() {
        return includeSimpleTypes;
    }

    public void setIncludeSimpleTypes(boolean includeSimpleTypes) {
        this.includeSimpleTypes = includeSimpleTypes;
    }

    public boolean isIncludeDataDictionary() {
        return includeDataDictionary;
    }

    public void setIncludeDataDictionary(boolean includeDataDictionary) {
        this.includeDataDictionary = includeDataDictionary;
    }

    public FontSize getFontSize() {
        return fontSize;
    }

    public void setFontSize(FontSize fontSize) {
        this.fontSize = fontSize;
    }

    public FontFamily getFontFamily() {
        return fontFamily;
    }

    public void setFontFamily(FontFamily fontFamily) {
        this.fontFamily = fontFamily;
    }

    public HeadingStyle getHeadingStyle() {
        return headingStyle;
    }

    public void setHeadingStyle(HeadingStyle headingStyle) {
        this.headingStyle = headingStyle;
    }

    public LineSpacing getLineSpacing() {
        return lineSpacing;
    }

    public void setLineSpacing(LineSpacing lineSpacing) {
        this.lineSpacing = lineSpacing;
    }

    public HeaderFooterStyle getHeaderStyle() {
        return headerStyle;
    }

    public void setHeaderStyle(HeaderFooterStyle headerStyle) {
        this.headerStyle = headerStyle;
    }

    public HeaderFooterStyle getFooterStyle() {
        return footerStyle;
    }

    public void setFooterStyle(HeaderFooterStyle footerStyle) {
        this.footerStyle = footerStyle;
    }

    public boolean isIncludePageNumbers() {
        return includePageNumbers;
    }

    public void setIncludePageNumbers(boolean includePageNumbers) {
        this.includePageNumbers = includePageNumbers;
    }

    public PageNumberPosition getPageNumberPosition() {
        return pageNumberPosition;
    }

    public void setPageNumberPosition(PageNumberPosition pageNumberPosition) {
        this.pageNumberPosition = pageNumberPosition;
    }

    public ColorScheme getColorScheme() {
        return colorScheme;
    }

    public void setColorScheme(ColorScheme colorScheme) {
        this.colorScheme = colorScheme;
    }

    public TableStyle getTableStyle() {
        return tableStyle;
    }

    public void setTableStyle(TableStyle tableStyle) {
        this.tableStyle = tableStyle;
    }

    public Watermark getWatermark() {
        return watermark;
    }

    public void setWatermark(Watermark watermark) {
        this.watermark = watermark;
    }

    public boolean isGenerateBookmarks() {
        return generateBookmarks;
    }

    public void setGenerateBookmarks(boolean generateBookmarks) {
        this.generateBookmarks = generateBookmarks;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

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
