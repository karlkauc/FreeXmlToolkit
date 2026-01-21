package org.fxt.freexmltoolkit.controls;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;

import java.util.*;
import java.util.prefs.Preferences;

/**
 * Modern Theme Manager for XML Syntax Highlighting.
 * Provides multiple built-in themes, custom theme creation, and live theme preview.
 */
public class ModernXmlThemeManager {

    /**
     * Logger instance for this class.
     */
    private static final Logger logger = LogManager.getLogger(ModernXmlThemeManager.class);

    /**
     * Singleton instance of the theme manager.
     */
    private static ModernXmlThemeManager instance;

    /**
     * Map of all registered themes, keyed by theme name.
     */
    private final Map<String, XmlHighlightTheme> themes = new HashMap<>();

    /**
     * The currently active theme.
     */
    private XmlHighlightTheme currentTheme;

    /**
     * List of listeners that are notified when the theme changes.
     */
    private final ObservableList<ThemeChangeListener> listeners = FXCollections.observableArrayList();

    /**
     * User preferences storage for persisting theme selection.
     */
    private final Preferences preferences = Preferences.userNodeForPackage(ModernXmlThemeManager.class);

    /**
     * Preference key for storing the current theme name.
     */
    private static final String CURRENT_THEME_KEY = "xml_editor_theme";

    /**
     * Default theme name used when no theme is selected or the selected theme is not found.
     */
    private static final String DEFAULT_THEME_NAME = "Modern Light";

    /**
     * XML Highlight Theme definition.
     * Defines a complete theme for XML syntax highlighting including colors, styles, and base editor appearance.
     */
    public static class XmlHighlightTheme {

        /**
         * Internal unique name of the theme (e.g., "modern-light").
         */
        private final String name;

        /**
         * Human-readable display name of the theme (e.g., "Modern Light").
         */
        private final String displayName;

        /**
         * Indicates whether this is a dark theme.
         */
        private final boolean isDark;

        /**
         * Map of highlight styles for each highlight type.
         */
        private final Map<HighlightType, HighlightStyle> styles = new HashMap<>();

        /**
         * Map of base editor styles (background, text, selection, caret, line numbers).
         */
        private final Map<String, String> baseStyles = new HashMap<>();

        /**
         * Enumeration of all highlight types supported by the XML syntax highlighter.
         * Each type represents a different syntactic element that can be styled.
         */
        public enum HighlightType {
            // XML Structure
            /** XML declaration (e.g., &lt;?xml version="1.0"?&gt;). */
            XML_DECLARATION("XML Declaration"),
            /** Opening element tag bracket and angle brackets. */
            XML_ELEMENT_TAG("Element Tag"),
            /** Element name within a tag. */
            XML_ELEMENT_NAME("Element Name"),
            /** Closing element tag (e.g., &lt;/element&gt;). */
            XML_CLOSING_TAG("Closing Tag"),
            /** Self-closing element tag (e.g., &lt;element/&gt;). */
            XML_SELF_CLOSING("Self-Closing Tag"),

            // Attributes
            /** Attribute name within an element. */
            XML_ATTRIBUTE_NAME("Attribute Name"),
            /** Attribute value (the content within quotes). */
            XML_ATTRIBUTE_VALUE("Attribute Value"),
            /** Equals sign between attribute name and value. */
            XML_ATTRIBUTE_EQUALS("Attribute Equals"),
            /** Quote characters surrounding attribute values. */
            XML_ATTRIBUTE_QUOTES("Attribute Quotes"),

            // Content
            /** Text content between elements. */
            XML_TEXT_CONTENT("Text Content"),
            /** CDATA section content. */
            XML_CDATA("CDATA Section"),
            /** XML comment content. */
            XML_COMMENT("Comment"),
            /** Processing instruction (e.g., &lt;?target data?&gt;). */
            XML_PROCESSING_INSTRUCTION("Processing Instruction"),

            // Special
            /** Namespace prefix and declaration. */
            XML_NAMESPACE("Namespace"),
            /** Entity reference (e.g., &amp;amp;). */
            XML_ENTITY("Entity Reference"),
            /** Whitespace characters. */
            XML_WHITESPACE("Whitespace"),

            // Error States
            /** Syntax error highlighting. */
            XML_ERROR("Syntax Error"),
            /** Warning highlighting. */
            XML_WARNING("Warning"),
            /** Deprecated element highlighting. */
            XML_DEPRECATED("Deprecated"),

            // Line Elements
            /** Line number gutter style. */
            LINE_NUMBER("Line Number"),
            /** Current line highlight. */
            CURRENT_LINE("Current Line"),
            /** Text selection highlight. */
            SELECTION("Selection"),

            // Search & Replace
            /** Search result match highlight. */
            SEARCH_MATCH("Search Match"),
            /** Currently focused search match highlight. */
            SEARCH_CURRENT("Current Search Match"),

            // Folding
            /** Folded text placeholder style. */
            FOLDED_TEXT("Folded Text"),
            /** Fold indicator gutter style. */
            FOLD_INDICATOR("Fold Indicator");

            /**
             * Human-readable display name for this highlight type.
             */
            private final String displayName;

            /**
             * Constructs a HighlightType with the specified display name.
             *
             * @param displayName the human-readable name to display in the UI
             */
            HighlightType(String displayName) {
                this.displayName = displayName;
            }

            /**
             * Returns the human-readable display name for this highlight type.
             *
             * @return the display name
             */
            public String getDisplayName() {
                return displayName;
            }
        }

        /**
         * Highlight style definition.
         * Defines the visual appearance for a single highlight type including colors, font styles, and decorations.
         */
        public static class HighlightStyle {

            /**
             * Text foreground color in CSS format (e.g., "#007bff" or "rgba(0,0,0,0.5)").
             */
            private final String textColor;

            /**
             * Background color in CSS format, or null for no background.
             */
            private String backgroundColor;

            /**
             * Whether the text should be rendered in bold.
             */
            private boolean bold;

            /**
             * Whether the text should be rendered in italic.
             */
            private boolean italic;

            /**
             * Whether the text should be underlined.
             */
            private boolean underline;

            /**
             * Border color for the highlighted region, or null for no border.
             */
            private String borderColor;

            /**
             * Border style (e.g., "solid", "dashed", "dotted"), or null for no border.
             */
            private String borderStyle;

            /**
             * Font family name, or null to use the default editor font.
             */
            private String fontFamily;

            /**
             * Font size in pixels, or null to use the default editor font size.
             */
            private Integer fontSize;

            /**
             * Constructs a HighlightStyle with only a text color.
             *
             * @param textColor the text foreground color in CSS format
             */
            public HighlightStyle(String textColor) {
                this.textColor = textColor;
                this.bold = false;
                this.italic = false;
                this.underline = false;
            }

            /**
             * Constructs a HighlightStyle with a text color and bold setting.
             *
             * @param textColor the text foreground color in CSS format
             * @param bold      whether the text should be bold
             */
            public HighlightStyle(String textColor, boolean bold) {
                this(textColor);
                this.bold = bold;
            }

            /**
             * Constructs a HighlightStyle with text color, bold, and italic settings.
             *
             * @param textColor the text foreground color in CSS format
             * @param bold      whether the text should be bold
             * @param italic    whether the text should be italic
             */
            public HighlightStyle(String textColor, boolean bold, boolean italic) {
                this(textColor, bold);
                this.italic = italic;
            }

            /**
             * Constructs a HighlightStyle with text color, background color, bold, and italic settings.
             *
             * @param textColor       the text foreground color in CSS format
             * @param backgroundColor the background color in CSS format, or null for no background
             * @param bold            whether the text should be bold
             * @param italic          whether the text should be italic
             */
            public HighlightStyle(String textColor, String backgroundColor, boolean bold, boolean italic) {
                this(textColor, bold, italic);
                this.backgroundColor = backgroundColor;
            }

            /**
             * Sets the background color for this style.
             * This method supports fluent chaining.
             *
             * @param backgroundColor the background color in CSS format
             * @return this HighlightStyle instance for method chaining
             */
            public HighlightStyle withBackground(String backgroundColor) {
                this.backgroundColor = backgroundColor;
                return this;
            }

            /**
             * Sets the border properties for this style.
             * This method supports fluent chaining.
             *
             * @param borderColor the border color in CSS format
             * @param borderStyle the border style (e.g., "solid", "dashed", "dotted")
             * @return this HighlightStyle instance for method chaining
             */
            public HighlightStyle withBorder(String borderColor, String borderStyle) {
                this.borderColor = borderColor;
                this.borderStyle = borderStyle;
                return this;
            }

            /**
             * Enables underline decoration for this style.
             * This method supports fluent chaining.
             *
             * @return this HighlightStyle instance for method chaining
             */
            public HighlightStyle withUnderline() {
                this.underline = true;
                return this;
            }

            /**
             * Sets the font family and size for this style.
             * This method supports fluent chaining.
             *
             * @param fontFamily the font family name, or null to use the default
             * @param fontSize   the font size in pixels, or null to use the default
             * @return this HighlightStyle instance for method chaining
             */
            public HighlightStyle withFont(String fontFamily, Integer fontSize) {
                this.fontFamily = fontFamily;
                this.fontSize = fontSize;
                return this;
            }

            /**
             * Returns the text foreground color.
             *
             * @return the text color in CSS format
             */
            public String getTextColor() {
                return textColor;
            }

            /**
             * Returns the background color.
             *
             * @return the background color in CSS format, or null if not set
             */
            public String getBackgroundColor() {
                return backgroundColor;
            }

            /**
             * Returns whether the text should be bold.
             *
             * @return true if bold, false otherwise
             */
            public boolean isBold() {
                return bold;
            }

            /**
             * Returns whether the text should be italic.
             *
             * @return true if italic, false otherwise
             */
            public boolean isItalic() {
                return italic;
            }

            /**
             * Returns whether the text should be underlined.
             *
             * @return true if underlined, false otherwise
             */
            public boolean isUnderline() {
                return underline;
            }

            /**
             * Returns the border color.
             *
             * @return the border color in CSS format, or null if not set
             */
            public String getBorderColor() {
                return borderColor;
            }

            /**
             * Returns the border style.
             *
             * @return the border style (e.g., "solid", "dashed"), or null if not set
             */
            public String getBorderStyle() {
                return borderStyle;
            }

            /**
             * Returns the font family name.
             *
             * @return the font family, or null if using the default
             */
            public String getFontFamily() {
                return fontFamily;
            }

            /**
             * Returns the font size.
             *
             * @return the font size in pixels, or null if using the default
             */
            public Integer getFontSize() {
                return fontSize;
            }

            /**
             * Generates a CSS style string from this highlight style.
             * The generated string includes all non-null style properties.
             *
             * @return the CSS style string
             */
            public String toCssStyle() {
                StringBuilder css = new StringBuilder();

                if (textColor != null) {
                    css.append("-fx-fill: ").append(textColor).append("; ");
                }
                if (backgroundColor != null) {
                    css.append("-fx-background-color: ").append(backgroundColor).append("; ");
                }
                if (bold) {
                    css.append("-fx-font-weight: bold; ");
                }
                if (italic) {
                    css.append("-fx-font-style: italic; ");
                }
                if (underline) {
                    css.append("-fx-underline: true; ");
                }
                if (borderColor != null && borderStyle != null) {
                    css.append("-fx-border-color: ").append(borderColor).append("; ");
                    css.append("-fx-border-style: ").append(borderStyle).append("; ");
                }
                if (fontFamily != null) {
                    css.append("-fx-font-family: '").append(fontFamily).append("'; ");
                }
                if (fontSize != null) {
                    css.append("-fx-font-size: ").append(fontSize).append("px; ");
                }

                return css.toString().trim();
            }

            /**
             * Creates a CSS class definition string for this highlight style.
             *
             * @param className the CSS class name (without the leading dot)
             * @return the complete CSS class definition
             */
            public String toCssClass(String className) {
                return "." + className + " { " + toCssStyle() + " }";
            }
        }

        /**
         * Constructs a new XmlHighlightTheme with the specified properties.
         * Initializes base styles based on whether this is a dark or light theme.
         *
         * @param name        the internal unique name of the theme
         * @param displayName the human-readable display name
         * @param isDark      true if this is a dark theme, false for a light theme
         */
        public XmlHighlightTheme(String name, String displayName, boolean isDark) {
            this.name = name;
            this.displayName = displayName;
            this.isDark = isDark;

            // Base editor styles
            if (isDark) {
                baseStyles.put("background", "#2b2b2b");
                baseStyles.put("text", "#e0e0e0");
                baseStyles.put("selection", "rgba(100, 150, 200, 0.3)");
                baseStyles.put("caret", "#ffffff");
                baseStyles.put("lineNumber", "#6c757d");
            } else {
                baseStyles.put("background", "#ffffff");
                baseStyles.put("text", "#212529");
                baseStyles.put("selection", "rgba(0, 123, 255, 0.2)");
                baseStyles.put("caret", "#000000");
                baseStyles.put("lineNumber", "#6c757d");
            }
        }

        /**
         * Returns the internal unique name of this theme.
         *
         * @return the theme name
         */
        public String getName() {
            return name;
        }

        /**
         * Returns the human-readable display name of this theme.
         *
         * @return the display name
         */
        public String getDisplayName() {
            return displayName;
        }

        /**
         * Returns whether this is a dark theme.
         *
         * @return true if this is a dark theme, false otherwise
         */
        public boolean isDark() {
            return isDark;
        }

        /**
         * Returns a copy of all highlight styles defined in this theme.
         *
         * @return a map of highlight types to their styles
         */
        public Map<HighlightType, HighlightStyle> getStyles() {
            return new HashMap<>(styles);
        }

        /**
         * Returns a copy of all base editor styles defined in this theme.
         *
         * @return a map of style names to their CSS values
         */
        public Map<String, String> getBaseStyles() {
            return new HashMap<>(baseStyles);
        }

        /**
         * Sets the highlight style for a specific highlight type.
         *
         * @param type  the highlight type to style
         * @param style the style to apply
         */
        public void setStyle(HighlightType type, HighlightStyle style) {
            styles.put(type, style);
        }

        /**
         * Returns the highlight style for a specific highlight type.
         *
         * @param type the highlight type
         * @return the style for the given type, or null if not defined
         */
        public HighlightStyle getStyle(HighlightType type) {
            return styles.get(type);
        }

        /**
         * Sets a base editor style value.
         *
         * @param key   the style key (e.g., "background", "text", "selection")
         * @param value the CSS value for the style
         */
        public void setBaseStyle(String key, String value) {
            baseStyles.put(key, value);
        }

        /**
         * Returns a base editor style value.
         *
         * @param key the style key
         * @return the CSS value for the style, or null if not defined
         */
        public String getBaseStyle(String key) {
            return baseStyles.get(key);
        }

        /**
         * Generates complete CSS stylesheet for this theme.
         * Includes base code area styles, selection styles, line number styles,
         * and all syntax highlighting classes.
         *
         * @return the complete CSS stylesheet as a string
         */
        public String generateThemeCss() {
            StringBuilder css = new StringBuilder();
            css.append("/* Generated XML Theme: ").append(displayName).append(" */\\n");

            // Base code area styles
            css.append(".xml-code-area {\\n");
            css.append("    -fx-background-color: ").append(baseStyles.get("background")).append(";\\n");
            css.append("    -fx-text-fill: ").append(baseStyles.get("text")).append(";\\n");
            css.append("}\\n\\n");

            // Selection
            css.append(".xml-code-area .selection {\\n");
            css.append("    -fx-background-color: ").append(baseStyles.get("selection")).append(";\\n");
            css.append("}\\n\\n");

            // Line numbers
            css.append(".xml-code-area .paragraph-box {\\n");
            css.append("    -fx-text-fill: ").append(baseStyles.get("lineNumber")).append(";\\n");
            css.append("    -fx-background-color: ").append(adjustBrightness(baseStyles.get("background"), isDark ? 0.1f : -0.1f)).append(";\\n");
            css.append("}\\n\\n");

            // Generate syntax highlighting classes
            for (Map.Entry<HighlightType, HighlightStyle> entry : styles.entrySet()) {
                String className = "xml-" + entry.getKey().name().toLowerCase().replace("_", "-");
                css.append(".").append(className).append(" {\\n");
                css.append("    ").append(entry.getValue().toCssStyle()).append("\\n");
                css.append("}\\n\\n");
            }

            return css.toString();
        }

        /**
         * Adjusts the brightness of a hex color by a given factor.
         * Positive factors lighten the color, negative factors darken it.
         *
         * @param hexColor the color in hex format (e.g., "#ffffff")
         * @param factor   the brightness adjustment factor (-1.0 to 1.0)
         * @return the adjusted color in hex format, or the original color if parsing fails
         */
        private String adjustBrightness(String hexColor, float factor) {
            if (hexColor == null || !hexColor.startsWith("#")) {
                return hexColor;
            }

            try {
                int rgb = Integer.parseInt(hexColor.substring(1), 16);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                r = Math.min(255, Math.max(0, (int) (r * (1 + factor))));
                g = Math.min(255, Math.max(0, (int) (g * (1 + factor))));
                b = Math.min(255, Math.max(0, (int) (b * (1 + factor))));

                return String.format("#%02x%02x%02x", r, g, b);
            } catch (NumberFormatException e) {
                return hexColor;
            }
        }
    }

    /**
     * Listener interface for theme change events.
     * Implementations receive notifications when the current theme is changed.
     */
    public interface ThemeChangeListener {
        /**
         * Called when the theme is changed.
         *
         * @param oldTheme the previously active theme
         * @param newTheme the newly active theme
         */
        void onThemeChanged(XmlHighlightTheme oldTheme, XmlHighlightTheme newTheme);
    }

    /**
     * Private constructor to enforce singleton pattern.
     * Initializes built-in themes and loads the current theme from preferences.
     */
    private ModernXmlThemeManager() {
        initializeBuiltInThemes();
        loadCurrentTheme();
    }

    /**
     * Returns the singleton instance of the ModernXmlThemeManager.
     * Creates the instance if it does not already exist.
     *
     * @return the singleton instance
     */
    public static ModernXmlThemeManager getInstance() {
        if (instance == null) {
            instance = new ModernXmlThemeManager();
        }
        return instance;
    }

    /**
     * Initializes all built-in themes.
     * Called during construction to populate the themes map with default themes.
     */
    private void initializeBuiltInThemes() {
        createModernLightTheme();
        createModernDarkTheme();
        createHighContrastTheme();
        createSolarizedLightTheme();
        createSolarizedDarkTheme();
        createMonokaiTheme();
        createTomorrowNightTheme();
        createVisualStudioTheme();

        logger.info("Initialized {} built-in XML syntax highlighting themes", themes.size());
    }

    /**
     * Creates the Modern Light theme (the default theme).
     * A clean, modern light theme with blue element names and purple attribute names.
     */
    private void createModernLightTheme() {
        XmlHighlightTheme theme = new XmlHighlightTheme("modern-light", "Modern Light", false);

        // XML Structure
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_DECLARATION, new XmlHighlightTheme.HighlightStyle("#6f42c1", true));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_ELEMENT_TAG, new XmlHighlightTheme.HighlightStyle("#007bff"));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_ELEMENT_NAME, new XmlHighlightTheme.HighlightStyle("#007bff", true));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_CLOSING_TAG, new XmlHighlightTheme.HighlightStyle("#0056b3", true));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_SELF_CLOSING, new XmlHighlightTheme.HighlightStyle("#17a2b8", true));

        // Attributes
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_ATTRIBUTE_NAME, new XmlHighlightTheme.HighlightStyle("#6f42c1"));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_ATTRIBUTE_VALUE, new XmlHighlightTheme.HighlightStyle("#e83e8c"));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_ATTRIBUTE_EQUALS, new XmlHighlightTheme.HighlightStyle("#495057"));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_ATTRIBUTE_QUOTES, new XmlHighlightTheme.HighlightStyle("#e83e8c"));

        // Content
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_TEXT_CONTENT, new XmlHighlightTheme.HighlightStyle("#212529"));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_CDATA, new XmlHighlightTheme.HighlightStyle("#28a745", "rgba(40, 167, 69, 0.1)", false, false));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_COMMENT, new XmlHighlightTheme.HighlightStyle("#6c757d", false, true));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_PROCESSING_INSTRUCTION, new XmlHighlightTheme.HighlightStyle("#fd7e14", true));

        // Special
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_NAMESPACE, new XmlHighlightTheme.HighlightStyle("#20c997", true));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_ENTITY, new XmlHighlightTheme.HighlightStyle("#dc3545"));

        // Errors
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_ERROR, new XmlHighlightTheme.HighlightStyle("#dc3545", "rgba(220, 53, 69, 0.1)", true, false).withBorder("#dc3545", "dashed").withUnderline());
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_WARNING, new XmlHighlightTheme.HighlightStyle("#ffc107", "rgba(255, 193, 7, 0.1)", false, false).withBorder("#ffc107", "dotted"));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_DEPRECATED, new XmlHighlightTheme.HighlightStyle("#6c757d", false, true).withUnderline());

        // Search
        theme.setStyle(XmlHighlightTheme.HighlightType.SEARCH_MATCH, new XmlHighlightTheme.HighlightStyle("#212529", "rgba(255, 235, 59, 0.4)", false, false));
        theme.setStyle(XmlHighlightTheme.HighlightType.SEARCH_CURRENT, new XmlHighlightTheme.HighlightStyle("#212529", "rgba(255, 193, 7, 0.6)", true, false));

        themes.put(theme.getName(), theme);
    }

    /**
     * Creates the Modern Dark theme.
     * A sleek dark theme with soft blue element names and purple attribute names.
     */
    private void createModernDarkTheme() {
        XmlHighlightTheme theme = new XmlHighlightTheme("modern-dark", "Modern Dark", true);

        // XML Structure
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_DECLARATION, new XmlHighlightTheme.HighlightStyle("#bb86fc", true));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_ELEMENT_TAG, new XmlHighlightTheme.HighlightStyle("#64b5f6"));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_ELEMENT_NAME, new XmlHighlightTheme.HighlightStyle("#64b5f6", true));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_CLOSING_TAG, new XmlHighlightTheme.HighlightStyle("#42a5f5", true));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_SELF_CLOSING, new XmlHighlightTheme.HighlightStyle("#4fc3f7", true));

        // Attributes
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_ATTRIBUTE_NAME, new XmlHighlightTheme.HighlightStyle("#ab47bc"));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_ATTRIBUTE_VALUE, new XmlHighlightTheme.HighlightStyle("#f06292"));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_ATTRIBUTE_EQUALS, new XmlHighlightTheme.HighlightStyle("#e0e0e0"));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_ATTRIBUTE_QUOTES, new XmlHighlightTheme.HighlightStyle("#f06292"));

        // Content
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_TEXT_CONTENT, new XmlHighlightTheme.HighlightStyle("#e0e0e0"));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_CDATA, new XmlHighlightTheme.HighlightStyle("#66bb6a", "rgba(102, 187, 106, 0.2)", false, false));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_COMMENT, new XmlHighlightTheme.HighlightStyle("#9e9e9e", false, true));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_PROCESSING_INSTRUCTION, new XmlHighlightTheme.HighlightStyle("#ffab40", true));

        // Special
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_NAMESPACE, new XmlHighlightTheme.HighlightStyle("#4db6ac", true));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_ENTITY, new XmlHighlightTheme.HighlightStyle("#ef5350"));

        // Errors
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_ERROR, new XmlHighlightTheme.HighlightStyle("#f44336", "rgba(244, 67, 54, 0.2)", true, false).withBorder("#f44336", "dashed").withUnderline());
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_WARNING, new XmlHighlightTheme.HighlightStyle("#ff9800", "rgba(255, 152, 0, 0.2)", false, false).withBorder("#ff9800", "dotted"));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_DEPRECATED, new XmlHighlightTheme.HighlightStyle("#9e9e9e", false, true).withUnderline());

        themes.put(theme.getName(), theme);
    }

    /**
     * Creates the High Contrast theme.
     * A high-contrast dark theme designed for accessibility with maximum color contrast.
     */
    private void createHighContrastTheme() {
        XmlHighlightTheme theme = new XmlHighlightTheme("high-contrast", "High Contrast", true);
        theme.setBaseStyle("background", "#000000");
        theme.setBaseStyle("text", "#ffffff");
        theme.setBaseStyle("selection", "#ffffff");
        theme.setBaseStyle("caret", "#ffff00");
        theme.setBaseStyle("lineNumber", "#cccccc");

        // High contrast colors
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_ELEMENT_NAME, new XmlHighlightTheme.HighlightStyle("#00ffff", true));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_ATTRIBUTE_NAME, new XmlHighlightTheme.HighlightStyle("#ffff00"));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_ATTRIBUTE_VALUE, new XmlHighlightTheme.HighlightStyle("#00ff00"));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_COMMENT, new XmlHighlightTheme.HighlightStyle("#ff00ff", false, true));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_TEXT_CONTENT, new XmlHighlightTheme.HighlightStyle("#ffffff"));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_ERROR, new XmlHighlightTheme.HighlightStyle("#ff0000", "#ffff00", true, false));

        themes.put(theme.getName(), theme);
    }

    /**
     * Creates the Solarized Light theme.
     * Based on the popular Solarized color scheme by Ethan Schoonover (light variant).
     */
    private void createSolarizedLightTheme() {
        XmlHighlightTheme theme = new XmlHighlightTheme("solarized-light", "Solarized Light", false);
        theme.setBaseStyle("background", "#fdf6e3");
        theme.setBaseStyle("text", "#657b83");

        theme.setStyle(XmlHighlightTheme.HighlightType.XML_ELEMENT_NAME, new XmlHighlightTheme.HighlightStyle("#268bd2", true));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_ATTRIBUTE_NAME, new XmlHighlightTheme.HighlightStyle("#b58900"));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_ATTRIBUTE_VALUE, new XmlHighlightTheme.HighlightStyle("#2aa198"));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_COMMENT, new XmlHighlightTheme.HighlightStyle("#93a1a1", false, true));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_TEXT_CONTENT, new XmlHighlightTheme.HighlightStyle("#657b83"));

        themes.put(theme.getName(), theme);
    }

    /**
     * Creates the Solarized Dark theme.
     * Based on the popular Solarized color scheme by Ethan Schoonover (dark variant).
     */
    private void createSolarizedDarkTheme() {
        XmlHighlightTheme theme = new XmlHighlightTheme("solarized-dark", "Solarized Dark", true);
        theme.setBaseStyle("background", "#002b36");
        theme.setBaseStyle("text", "#839496");

        theme.setStyle(XmlHighlightTheme.HighlightType.XML_ELEMENT_NAME, new XmlHighlightTheme.HighlightStyle("#268bd2", true));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_ATTRIBUTE_NAME, new XmlHighlightTheme.HighlightStyle("#b58900"));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_ATTRIBUTE_VALUE, new XmlHighlightTheme.HighlightStyle("#2aa198"));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_COMMENT, new XmlHighlightTheme.HighlightStyle("#586e75", false, true));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_TEXT_CONTENT, new XmlHighlightTheme.HighlightStyle("#839496"));

        themes.put(theme.getName(), theme);
    }

    /**
     * Creates the Monokai theme.
     * Based on the popular Monokai color scheme originally for TextMate.
     */
    private void createMonokaiTheme() {
        XmlHighlightTheme theme = new XmlHighlightTheme("monokai", "Monokai", true);
        theme.setBaseStyle("background", "#272822");
        theme.setBaseStyle("text", "#f8f8f2");

        theme.setStyle(XmlHighlightTheme.HighlightType.XML_ELEMENT_NAME, new XmlHighlightTheme.HighlightStyle("#f92672", true));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_ATTRIBUTE_NAME, new XmlHighlightTheme.HighlightStyle("#a6e22e"));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_ATTRIBUTE_VALUE, new XmlHighlightTheme.HighlightStyle("#e6db74"));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_COMMENT, new XmlHighlightTheme.HighlightStyle("#75715e", false, true));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_TEXT_CONTENT, new XmlHighlightTheme.HighlightStyle("#f8f8f2"));

        themes.put(theme.getName(), theme);
    }

    /**
     * Creates the Tomorrow Night theme.
     * Based on the Tomorrow Night color scheme from the Tomorrow theme family.
     */
    private void createTomorrowNightTheme() {
        XmlHighlightTheme theme = new XmlHighlightTheme("tomorrow-night", "Tomorrow Night", true);
        theme.setBaseStyle("background", "#1d1f21");
        theme.setBaseStyle("text", "#c5c8c6");

        theme.setStyle(XmlHighlightTheme.HighlightType.XML_ELEMENT_NAME, new XmlHighlightTheme.HighlightStyle("#cc6666", true));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_ATTRIBUTE_NAME, new XmlHighlightTheme.HighlightStyle("#f0c674"));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_ATTRIBUTE_VALUE, new XmlHighlightTheme.HighlightStyle("#b5bd68"));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_COMMENT, new XmlHighlightTheme.HighlightStyle("#969896", false, true));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_TEXT_CONTENT, new XmlHighlightTheme.HighlightStyle("#c5c8c6"));

        themes.put(theme.getName(), theme);
    }

    /**
     * Creates the Visual Studio theme.
     * Based on the classic Visual Studio light color scheme.
     */
    private void createVisualStudioTheme() {
        XmlHighlightTheme theme = new XmlHighlightTheme("visual-studio", "Visual Studio", false);
        theme.setBaseStyle("background", "#ffffff");
        theme.setBaseStyle("text", "#000000");

        theme.setStyle(XmlHighlightTheme.HighlightType.XML_ELEMENT_NAME, new XmlHighlightTheme.HighlightStyle("#800000", true));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_ATTRIBUTE_NAME, new XmlHighlightTheme.HighlightStyle("#ff0000"));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_ATTRIBUTE_VALUE, new XmlHighlightTheme.HighlightStyle("#0000ff"));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_COMMENT, new XmlHighlightTheme.HighlightStyle("#008000", false, true));
        theme.setStyle(XmlHighlightTheme.HighlightType.XML_TEXT_CONTENT, new XmlHighlightTheme.HighlightStyle("#000000"));

        themes.put(theme.getName(), theme);
    }

    // ========== Public API ==========

    /**
     * Returns a list of all available themes.
     *
     * @return a list containing all registered themes
     */
    public List<XmlHighlightTheme> getAvailableThemes() {
        return new ArrayList<>(themes.values());
    }

    /**
     * Returns a sorted list of all theme display names.
     *
     * @return a list of theme display names sorted alphabetically
     */
    public List<String> getThemeNames() {
        return themes.values().stream()
                .map(XmlHighlightTheme::getDisplayName)
                .sorted()
                .toList();
    }

    /**
     * Returns the theme with the specified internal name.
     *
     * @param name the internal theme name (e.g., "modern-light")
     * @return the theme, or null if not found
     */
    public XmlHighlightTheme getTheme(String name) {
        return themes.get(name);
    }

    /**
     * Returns the theme with the specified display name.
     *
     * @param displayName the display name (e.g., "Modern Light")
     * @return the theme, or null if not found
     */
    public XmlHighlightTheme getThemeByDisplayName(String displayName) {
        return themes.values().stream()
                .filter(theme -> theme.getDisplayName().equals(displayName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Returns the currently active theme.
     *
     * @return the current theme
     */
    public XmlHighlightTheme getCurrentTheme() {
        return currentTheme;
    }

    /**
     * Sets the current theme and notifies all listeners.
     * The theme selection is persisted to user preferences.
     *
     * @param theme the theme to set as current
     */
    public void setCurrentTheme(XmlHighlightTheme theme) {
        if (theme != null && !theme.equals(currentTheme)) {
            XmlHighlightTheme oldTheme = currentTheme;
            currentTheme = theme;

            // Save preference
            preferences.put(CURRENT_THEME_KEY, theme.getName());

            // Notify listeners
            notifyThemeChanged(oldTheme, theme);

            logger.info("XML syntax highlighting theme changed to: {}", theme.getDisplayName());
        }
    }

    /**
     * Sets the current theme by internal name.
     * If the theme is not found, logs a warning and does nothing.
     *
     * @param themeName the internal theme name (e.g., "modern-light")
     */
    public void setCurrentTheme(String themeName) {
        XmlHighlightTheme theme = getTheme(themeName);
        if (theme != null) {
            setCurrentTheme(theme);
        } else {
            logger.warn("Unknown theme name: {}", themeName);
        }
    }

    /**
     * Sets the current theme by display name.
     * If the theme is not found, logs a warning and does nothing.
     *
     * @param displayName the display name (e.g., "Modern Light")
     */
    public void setCurrentThemeByDisplayName(String displayName) {
        XmlHighlightTheme theme = getThemeByDisplayName(displayName);
        if (theme != null) {
            setCurrentTheme(theme);
        } else {
            logger.warn("Unknown theme display name: {}", displayName);
        }
    }

    /**
     * Adds a custom theme to the available themes.
     * If a theme with the same name already exists, it will be replaced.
     *
     * @param theme the custom theme to add
     */
    public void addCustomTheme(XmlHighlightTheme theme) {
        if (theme != null) {
            themes.put(theme.getName(), theme);
            logger.info("Added custom theme: {}", theme.getDisplayName());
        }
    }

    /**
     * Removes a theme from the available themes.
     * If the removed theme was the current theme, switches to the default theme.
     *
     * @param themeName the internal name of the theme to remove
     * @return true if the theme was removed, false if it was not found
     */
    public boolean removeTheme(String themeName) {
        XmlHighlightTheme removed = themes.remove(themeName);
        if (removed != null) {
            if (removed.equals(currentTheme)) {
                setCurrentTheme(themes.get(DEFAULT_THEME_NAME.toLowerCase().replace(" ", "-")));
            }
            logger.info("Removed theme: {}", removed.getDisplayName());
            return true;
        }
        return false;
    }

    /**
     * Resets the current theme to the default theme (Modern Light).
     */
    public void resetToDefault() {
        setCurrentTheme(themes.get(DEFAULT_THEME_NAME.toLowerCase().replace(" ", "-")));
    }

    /**
     * Adds a listener to be notified when the current theme changes.
     *
     * @param listener the listener to add
     */
    public void addThemeChangeListener(ThemeChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Removes a theme change listener.
     *
     * @param listener the listener to remove
     */
    public void removeThemeChangeListener(ThemeChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * Generates the CSS stylesheet for the current theme.
     *
     * @return the CSS stylesheet, or an empty string if no theme is set
     */
    public String getCurrentThemeCss() {
        return currentTheme != null ? currentTheme.generateThemeCss() : "";
    }

    /**
     * Exports a theme to a Properties object for persistence or sharing.
     * The resulting Properties can be saved to a file or transmitted.
     *
     * @param theme the theme to export
     * @return a Properties object containing all theme settings
     */
    public Properties exportTheme(XmlHighlightTheme theme) {
        Properties props = new Properties();
        props.setProperty("name", theme.getName());
        props.setProperty("displayName", theme.getDisplayName());
        props.setProperty("isDark", String.valueOf(theme.isDark()));

        // Export base styles
        for (Map.Entry<String, String> entry : theme.getBaseStyles().entrySet()) {
            props.setProperty("base." + entry.getKey(), entry.getValue());
        }

        // Export highlight styles
        for (Map.Entry<XmlHighlightTheme.HighlightType, XmlHighlightTheme.HighlightStyle> entry : theme.getStyles().entrySet()) {
            String prefix = "highlight." + entry.getKey().name().toLowerCase() + ".";
            XmlHighlightTheme.HighlightStyle style = entry.getValue();

            if (style.getTextColor() != null) {
                props.setProperty(prefix + "textColor", style.getTextColor());
            }
            if (style.getBackgroundColor() != null) {
                props.setProperty(prefix + "backgroundColor", style.getBackgroundColor());
            }
            props.setProperty(prefix + "bold", String.valueOf(style.isBold()));
            props.setProperty(prefix + "italic", String.valueOf(style.isItalic()));
            props.setProperty(prefix + "underline", String.valueOf(style.isUnderline()));
        }

        return props;
    }

    // ========== Private Methods ==========

    /**
     * Loads the current theme from user preferences.
     * First tries to load from PropertiesService for consistency with other settings,
     * then falls back to Java Preferences API.
     * If the saved theme is not found, defaults to Modern Light.
     */
    private void loadCurrentTheme() {
        // First try to load from properties service for consistency
        try {
            PropertiesService propertiesService = ServiceRegistry.get(PropertiesService.class);
            String themeDisplayName = propertiesService.get("xml.editor.theme");

            if (themeDisplayName != null && !themeDisplayName.isEmpty()) {
                XmlHighlightTheme theme = getThemeByDisplayName(themeDisplayName);
                if (theme != null) {
                    currentTheme = theme;
                    logger.info("Loaded XML syntax highlighting theme from properties: {}", currentTheme.getDisplayName());
                    return;
                }
            }
        } catch (Exception e) {
            logger.debug("Could not load theme from properties service: {}", e.getMessage());
        }

        // Fallback to preferences
        String themeName = preferences.get(CURRENT_THEME_KEY, DEFAULT_THEME_NAME.toLowerCase().replace(" ", "-"));
        XmlHighlightTheme theme = getTheme(themeName);

        if (theme != null) {
            currentTheme = theme;
        } else {
            currentTheme = themes.get(DEFAULT_THEME_NAME.toLowerCase().replace(" ", "-"));
            logger.warn("Theme '{}' not found, using default", themeName);
        }

        logger.info("Loaded XML syntax highlighting theme: {}", currentTheme.getDisplayName());
    }

    /**
     * Notifies all registered listeners about a theme change.
     * Catches and logs any exceptions thrown by listeners to prevent
     * one faulty listener from affecting others.
     *
     * @param oldTheme the previously active theme
     * @param newTheme the newly active theme
     */
    private void notifyThemeChanged(XmlHighlightTheme oldTheme, XmlHighlightTheme newTheme) {
        for (ThemeChangeListener listener : listeners) {
            try {
                listener.onThemeChanged(oldTheme, newTheme);
            } catch (Exception e) {
                logger.warn("Error notifying theme change listener", e);
            }
        }
    }
}
