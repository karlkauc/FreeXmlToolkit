package org.fxt.freexmltoolkit.controls.v2.model;

import java.util.Objects;

/**
 * Represents an XSD documentation element (xs:documentation).
 * Supports the xml:lang attribute for multi-language documentation.
 *
 * @since 2.0
 */
public class XsdDocumentation {

    private String text;
    private String lang; // xml:lang attribute
    private String source; // source attribute (optional)

    /**
     * Creates a new documentation element with text only.
     *
     * @param text the documentation text
     */
    public XsdDocumentation(String text) {
        this.text = text;
    }

    /**
     * Creates a new documentation element with text and language.
     *
     * @param text the documentation text
     * @param lang the xml:lang attribute value (e.g., "en", "de")
     */
    public XsdDocumentation(String text, String lang) {
        this.text = text;
        this.lang = lang;
    }

    /**
     * Creates a new documentation element with text, language, and source.
     *
     * @param text   the documentation text
     * @param lang   the xml:lang attribute value
     * @param source the source attribute value
     */
    public XsdDocumentation(String text, String lang, String source) {
        this.text = text;
        this.lang = lang;
        this.source = source;
    }

    /**
     * Gets the documentation text.
     *
     * @return the text
     */
    public String getText() {
        return text;
    }

    /**
     * Sets the documentation text.
     *
     * @param text the text
     */
    public void setText(String text) {
        this.text = text;
    }

    /**
     * Gets the language attribute (xml:lang).
     *
     * @return the language, or null if not set
     */
    public String getLang() {
        return lang;
    }

    /**
     * Sets the language attribute (xml:lang).
     *
     * @param lang the language (e.g., "en", "de")
     */
    public void setLang(String lang) {
        this.lang = lang;
    }

    /**
     * Gets the source attribute.
     *
     * @return the source, or null if not set
     */
    public String getSource() {
        return source;
    }

    /**
     * Sets the source attribute.
     *
     * @param source the source
     */
    public void setSource(String source) {
        this.source = source;
    }

    /**
     * Checks if this documentation has a language attribute.
     *
     * @return true if lang is set
     */
    public boolean hasLang() {
        return lang != null && !lang.isEmpty();
    }

    /**
     * Creates a deep copy of this documentation.
     *
     * @return a new XsdDocumentation with the same values
     */
    public XsdDocumentation deepCopy() {
        return new XsdDocumentation(text, lang, source);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        XsdDocumentation that = (XsdDocumentation) o;
        return Objects.equals(text, that.text) &&
               Objects.equals(lang, that.lang) &&
               Objects.equals(source, that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, lang, source);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("XsdDocumentation{");
        if (lang != null) {
            sb.append("lang='").append(lang).append("', ");
        }
        if (text != null) {
            String preview = text.length() > 50 ? text.substring(0, 47) + "..." : text;
            sb.append("text='").append(preview).append("'");
        }
        sb.append("}");
        return sb.toString();
    }
}
