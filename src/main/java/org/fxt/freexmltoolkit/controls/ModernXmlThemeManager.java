package org.fxt.freexmltoolkit.controls;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.prefs.Preferences;

/**
 * Modern Theme Manager for XML Syntax Highlighting.
 * Provides multiple built-in themes, custom theme creation, and live theme preview.
 */
public class ModernXmlThemeManager {

    private static final Logger logger = LogManager.getLogger(ModernXmlThemeManager.class);

    private static ModernXmlThemeManager instance;
    private final Map<String, XmlHighlightTheme> themes = new HashMap<>();
    private XmlHighlightTheme currentTheme;
    private final ObservableList<ThemeChangeListener> listeners = FXCollections.observableArrayList();
    private final Preferences preferences = Preferences.userNodeForPackage(ModernXmlThemeManager.class);

    // Theme preference key
    private static final String CURRENT_THEME_KEY = "xml_editor_theme";
    private static final String DEFAULT_THEME_NAME = "Modern Light";

    /**
     * XML Highlight Theme definition
     */
    public static class XmlHighlightTheme {
        private final String name;
        private final String displayName;
        private final boolean isDark;
        private final Map<HighlightType, HighlightStyle> styles = new HashMap<>();
        private final Map<String, String> baseStyles = new HashMap<>();

        public enum HighlightType {
            // XML Structure
            XML_DECLARATION("XML Declaration"),
            XML_ELEMENT_TAG("Element Tag"),
            XML_ELEMENT_NAME("Element Name"),
            XML_CLOSING_TAG("Closing Tag"),
            XML_SELF_CLOSING("Self-Closing Tag"),

            // Attributes
            XML_ATTRIBUTE_NAME("Attribute Name"),
            XML_ATTRIBUTE_VALUE("Attribute Value"),
            XML_ATTRIBUTE_EQUALS("Attribute Equals"),
            XML_ATTRIBUTE_QUOTES("Attribute Quotes"),

            // Content
            XML_TEXT_CONTENT("Text Content"),
            XML_CDATA("CDATA Section"),
            XML_COMMENT("Comment"),
            XML_PROCESSING_INSTRUCTION("Processing Instruction"),

            // Special
            XML_NAMESPACE("Namespace"),
            XML_ENTITY("Entity Reference"),
            XML_WHITESPACE("Whitespace"),

            // Error States
            XML_ERROR("Syntax Error"),
            XML_WARNING("Warning"),
            XML_DEPRECATED("Deprecated"),

            // Line Elements
            LINE_NUMBER("Line Number"),
            CURRENT_LINE("Current Line"),
            SELECTION("Selection"),

            // Search & Replace
            SEARCH_MATCH("Search Match"),
            SEARCH_CURRENT("Current Search Match"),

            // Folding
            FOLDED_TEXT("Folded Text"),
            FOLD_INDICATOR("Fold Indicator");

            private final String displayName;

            HighlightType(String displayName) {
                this.displayName = displayName;
            }

            public String getDisplayName() {
                return displayName;
            }
        }

        /**
         * Highlight style definition
         */
        public static class HighlightStyle {
            private final String textColor;
            private String backgroundColor;
            private boolean bold;
            private boolean italic;
            private boolean underline;
            private String borderColor;
            private String borderStyle;
            private String fontFamily;
            private Integer fontSize;

            public HighlightStyle(String textColor) {
                this.textColor = textColor;
                this.bold = false;
                this.italic = false;
                this.underline = false;
            }

            public HighlightStyle(String textColor, boolean bold) {
                this(textColor);
                this.bold = bold;
            }

            public HighlightStyle(String textColor, boolean bold, boolean italic) {
                this(textColor, bold);
                this.italic = italic;
            }

            public HighlightStyle(String textColor, String backgroundColor, boolean bold, boolean italic) {
                this(textColor, bold, italic);
                this.backgroundColor = backgroundColor;
            }

            // Builder methods
            public HighlightStyle withBackground(String backgroundColor) {
                this.backgroundColor = backgroundColor;
                return this;
            }

            public HighlightStyle withBorder(String borderColor, String borderStyle) {
                this.borderColor = borderColor;
                this.borderStyle = borderStyle;
                return this;
            }

            public HighlightStyle withUnderline() {
                this.underline = true;
                return this;
            }

            public HighlightStyle withFont(String fontFamily, Integer fontSize) {
                this.fontFamily = fontFamily;
                this.fontSize = fontSize;
                return this;
            }

            // Getters
            public String getTextColor() {
                return textColor;
            }

            public String getBackgroundColor() {
                return backgroundColor;
            }

            public boolean isBold() {
                return bold;
            }

            public boolean isItalic() {
                return italic;
            }

            public boolean isUnderline() {
                return underline;
            }

            public String getBorderColor() {
                return borderColor;
            }

            public String getBorderStyle() {
                return borderStyle;
            }

            public String getFontFamily() {
                return fontFamily;
            }

            public Integer getFontSize() {
                return fontSize;
            }

            /**
             * Generate CSS style string
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
             * Create CSS class definition
             */
            public String toCssClass(String className) {
                return "." + className + " { " + toCssStyle() + " }";
            }
        }

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

        // Getters
        public String getName() {
            return name;
        }

        public String getDisplayName() {
            return displayName;
        }

        public boolean isDark() {
            return isDark;
        }

        public Map<HighlightType, HighlightStyle> getStyles() {
            return new HashMap<>(styles);
        }

        public Map<String, String> getBaseStyles() {
            return new HashMap<>(baseStyles);
        }

        // Style management
        public void setStyle(HighlightType type, HighlightStyle style) {
            styles.put(type, style);
        }

        public HighlightStyle getStyle(HighlightType type) {
            return styles.get(type);
        }

        public void setBaseStyle(String key, String value) {
            baseStyles.put(key, value);
        }

        public String getBaseStyle(String key) {
            return baseStyles.get(key);
        }

        /**
         * Generate complete CSS for this theme
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
         * Adjust color brightness for theme variations
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
     * Theme change listener interface
     */
    public interface ThemeChangeListener {
        void onThemeChanged(XmlHighlightTheme oldTheme, XmlHighlightTheme newTheme);
    }

    private ModernXmlThemeManager() {
        initializeBuiltInThemes();
        loadCurrentTheme();
    }

    public static ModernXmlThemeManager getInstance() {
        if (instance == null) {
            instance = new ModernXmlThemeManager();
        }
        return instance;
    }

    /**
     * Initialize built-in themes
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
     * Create Modern Light theme (default)
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
     * Create Modern Dark theme
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
     * Create High Contrast theme
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
     * Create Solarized Light theme
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
     * Create Solarized Dark theme
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
     * Create Monokai theme
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
     * Create Tomorrow Night theme
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
     * Create Visual Studio theme
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
     * Get all available themes
     */
    public List<XmlHighlightTheme> getAvailableThemes() {
        return new ArrayList<>(themes.values());
    }

    /**
     * Get theme names
     */
    public List<String> getThemeNames() {
        return themes.values().stream()
                .map(XmlHighlightTheme::getDisplayName)
                .sorted()
                .toList();
    }

    /**
     * Get theme by name
     */
    public XmlHighlightTheme getTheme(String name) {
        return themes.get(name);
    }

    /**
     * Get theme by display name
     */
    public XmlHighlightTheme getThemeByDisplayName(String displayName) {
        return themes.values().stream()
                .filter(theme -> theme.getDisplayName().equals(displayName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get current theme
     */
    public XmlHighlightTheme getCurrentTheme() {
        return currentTheme;
    }

    /**
     * Set current theme
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
     * Set current theme by name
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
     * Set current theme by display name
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
     * Add custom theme
     */
    public void addCustomTheme(XmlHighlightTheme theme) {
        if (theme != null) {
            themes.put(theme.getName(), theme);
            logger.info("Added custom theme: {}", theme.getDisplayName());
        }
    }

    /**
     * Remove theme
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
     * Reset to default theme
     */
    public void resetToDefault() {
        setCurrentTheme(themes.get(DEFAULT_THEME_NAME.toLowerCase().replace(" ", "-")));
    }

    /**
     * Add theme change listener
     */
    public void addThemeChangeListener(ThemeChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Remove theme change listener
     */
    public void removeThemeChangeListener(ThemeChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * Generate CSS for current theme
     */
    public String getCurrentThemeCss() {
        return currentTheme != null ? currentTheme.generateThemeCss() : "";
    }

    /**
     * Export theme to properties format
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
     * Load current theme from preferences
     */
    private void loadCurrentTheme() {
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
     * Notify theme change listeners
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