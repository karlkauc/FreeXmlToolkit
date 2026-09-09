package org.fxt.freexmltoolkit.controls.v2.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents structured xs:appinfo content with JavaDoc-style tags.
 * <p>
 * The canonical serialization keeps the tag in the {@code source} attribute and the value in the
 * element's text content:
 * <pre>{@code
 * <xs:appinfo source="@since">4.0.0</xs:appinfo>
 * }</pre>
 * The legacy form that packed both into the attribute ({@code <xs:appinfo source="@since 4.0.0"/>})
 * is still read and is migrated to the canonical form on the next save.
 * <p>
 * Supported tags:
 * <ul>
 *   <li>@since - Version information</li>
 *   <li>@see - References to other elements</li>
 *   <li>@deprecated - Deprecation notices</li>
 *   <li>@author - Author information</li>
 *   <li>@version - Version details</li>
 *   <li>@markdown - Whether the node's documentation is rendered as Markdown</li>
 * </ul>
 *
 * @since 2.0
 */
public class XsdAppInfo {

    private final List<AppInfoEntry> entries = new ArrayList<>();

    /**
     * Represents a single appinfo entry with a tag and content.
     */
    public static class AppInfoEntry {
        private final String source;  // The "source" attribute from xs:appinfo
        private final String tag;     // e.g., "@since", "@see", "@deprecated"
        private final String content; // The value of the tag (or the plain text content)
        private final String rawXml;  // Raw XML content (if appinfo contains XML elements)

        public AppInfoEntry(String source, String tag, String content) {
            this(source, tag, content, null);
        }

        public AppInfoEntry(String source, String tag, String content, String rawXml) {
            this.source = source;
            this.tag = tag;
            this.content = content;
            this.rawXml = rawXml;
        }

        public String getSource() {
            return source;
        }

        public String getTag() {
            return tag;
        }

        public String getContent() {
            return content;
        }

        /**
         * Gets the raw XML content of the appinfo element.
         * This is used when the appinfo contains XML elements (like fxt:exampleValues).
         *
         * @return the raw XML content, or null if only text content
         */
        public String getRawXml() {
            return rawXml;
        }

        /**
         * Checks if this entry contains raw XML content.
         *
         * @return true if rawXml is set
         */
        public boolean hasRawXml() {
            return rawXml != null && !rawXml.isEmpty();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            AppInfoEntry that = (AppInfoEntry) o;
            return Objects.equals(source, that.source) &&
                    Objects.equals(tag, that.tag) &&
                    Objects.equals(content, that.content) &&
                    Objects.equals(rawXml, that.rawXml);
        }

        @Override
        public int hashCode() {
            return Objects.hash(source, tag, content, rawXml);
        }

        @Override
        public String toString() {
            if (tag != null && !tag.isEmpty()) {
                return content == null || content.isEmpty() ? tag : tag + " " + content;
            }
            return content;
        }
    }

    /**
     * Adds an appinfo entry.
     *
     * @param entry the entry to add
     */
    public void addEntry(AppInfoEntry entry) {
        if (entry != null) {
            entries.add(entry);
        }
    }

    /**
     * Adds an appinfo entry with source and content (parses tag from source or content).
     *
     * @param source  the source attribute value
     * @param content the text content of the appinfo element
     */
    public void addEntry(String source, String content) {
        addEntry(source, content, null);
    }

    /**
     * Adds an appinfo entry with source, content, and optional raw XML.
     * <p>
     * Handles all three encodings of a JavaDoc-style tag: the canonical
     * {@code source="@since"} + text content, the legacy {@code source="@since 4.0.0"} with no
     * text content, and the old duplicated artifact that wrote the tag into both.
     *
     * @param source  the source attribute value
     * @param content the text content (may itself start with a tag like "@since")
     * @param rawXml  the raw XML content (if appinfo contains XML elements)
     */
    public void addEntry(String source, String content, String rawXml) {
        String text = content == null ? "" : content.trim();
        String trimmedSource = source == null ? null : source.trim();

        if (rawXml != null && !rawXml.trim().isEmpty()) {
            entries.add(new AppInfoEntry(source, tagOf(trimmedSource), text, rawXml.trim()));
            return;
        }

        String sourceTag = tagOf(trimmedSource);
        if (sourceTag != null) {
            entries.add(new AppInfoEntry(sourceTag, sourceTag, valueOf(trimmedSource, text), null));
            return;
        }

        boolean hasSource = trimmedSource != null && !trimmedSource.isEmpty();
        if (text.isEmpty() && !hasSource) {
            return;
        }

        // Legacy form without a source attribute: the tag lives in the text content.
        if (!hasSource && text.startsWith("@")) {
            String tag = tagOf(text);
            entries.add(new AppInfoEntry(tag, tag, valueOf(text, ""), null));
            return;
        }

        entries.add(new AppInfoEntry(source, null, text, null));
    }

    /**
     * Extracts the JavaDoc-style tag from an appinfo {@code source} attribute.
     *
     * @param source the source attribute value
     * @return the tag (e.g. {@code "@since"}), or null when the source is not a tag
     */
    public static String tagOf(String source) {
        if (source == null) {
            return null;
        }
        String trimmed = source.trim();
        if (!trimmed.startsWith("@") || trimmed.length() < 2) {
            return null;
        }
        int space = indexOfWhitespace(trimmed);
        return space > 0 ? trimmed.substring(0, space) : trimmed;
    }

    /**
     * Resolves the value of a tagged appinfo from its {@code source} attribute and its text
     * content. The text content wins; the remainder of the source attribute is the legacy
     * fallback.
     *
     * @param source      the source attribute value (must start with the tag)
     * @param textContent the element's text content (may be empty)
     * @return the tag's value, never null
     */
    public static String valueOf(String source, String textContent) {
        String tag = tagOf(source);
        if (tag == null) {
            return textContent == null ? "" : textContent.trim();
        }
        String trimmedSource = source.trim();
        String fromSource = trimmedSource.length() > tag.length()
                ? trimmedSource.substring(tag.length()).trim()
                : "";
        String text = textContent == null ? "" : textContent.trim();
        if (!text.isEmpty()) {
            // Strip a repeated tag written by older versions of the editor.
            if (text.equals(trimmedSource)) {
                text = fromSource;
            } else if (text.equals(tag)) {
                text = "";
            } else if (text.startsWith(tag) && isWhitespace(text.charAt(tag.length()))) {
                text = text.substring(tag.length()).trim();
            }
        }
        return text.isEmpty() ? fromSource : text;
    }

    /**
     * Parses the truthiness of a flag-style tag value such as {@code @markdown}.
     *
     * @param value the raw value
     * @return TRUE/FALSE for a recognised value, null when the value says nothing
     */
    public static Boolean parseBooleanFlag(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.trim().toLowerCase()) {
            case "true", "1", "yes", "on" -> Boolean.TRUE;
            case "false", "0", "no", "off" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static int indexOfWhitespace(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }

    /** Adds (or re-adds) a tagged entry in the canonical form. */
    private void addTagEntry(String tag, String value) {
        entries.add(new AppInfoEntry(tag, tag, value == null ? "" : value.trim(), null));
    }

    /**
     * Gets all appinfo entries.
     *
     * @return list of entries
     */
    public List<AppInfoEntry> getEntries() {
        return new ArrayList<>(entries);
    }

    /**
     * Gets entries with a specific tag.
     *
     * @param tag the tag to filter by (e.g., "@since", "@see")
     * @return list of matching entries
     */
    public List<AppInfoEntry> getEntriesWithTag(String tag) {
        return entries.stream()
                .filter(e -> Objects.equals(e.getTag(), tag))
                .toList();
    }

    /**
     * Checks if there are any entries.
     *
     * @return true if entries exist
     */
    public boolean hasEntries() {
        return !entries.isEmpty();
    }

    /**
     * Gets the number of entries.
     *
     * @return entry count
     */
    public int size() {
        return entries.size();
    }

    /**
     * Clears all entries.
     */
    public void clear() {
        entries.clear();
    }

    /**
     * Namespace URI for FreeXmlToolkit flattening extensions ({@code fxt:sourceFile}).
     */
    private static final String FXT_NS = "http://freexmltoolkit.org/schema/flattening";

    /**
     * Namespace URI for the FreeXmlToolkit XSD extensions - the namespace
     * {@code <fxt:exampleValues>} is written in.
     */
    public static final String FXT_EXT_NS = "http://freexmltoolkit.org/xml-schema-extensions";

    /**
     * Altova namespace URI for {@code <altova:exampleValues>} — the convention used by FundsXML
     * schemas. Still read, but no longer written.
     */
    public static final String ALTOVA_NS = "http://www.altova.com/xml-schema-extensions";

    /**
     * Converts to XML format for serialization.
     *
     * @return list of XML strings representing xs:appinfo elements
     */
    public List<String> toXmlStrings() {
        List<String> xmlStrings = new ArrayList<>();
        for (AppInfoEntry entry : entries) {
            String content = entry.getContent() == null ? "" : entry.getContent();

            // The source-tracking marker keeps its own element form (see the Flatten Schema tool).
            if ("@sourceFile".equals(entry.getTag()) && !entry.hasRawXml()) {
                xmlStrings.add("<xs:appinfo><fxt:sourceFile xmlns:fxt=\"" + FXT_NS + "\">"
                        + escapeXml(content) + "</fxt:sourceFile></xs:appinfo>");
                continue;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("<xs:appinfo");
            String source = entry.getTag() != null && !entry.getTag().isEmpty()
                    ? entry.getTag()
                    : entry.getSource();
            if (source != null && !source.isEmpty()) {
                sb.append(" source=\"").append(escapeXml(source)).append("\"");
            }

            if (entry.hasRawXml()) {
                sb.append(">\n").append(entry.getRawXml()).append("\n</xs:appinfo>");
            } else if (content.isEmpty()) {
                sb.append("/>");
            } else {
                sb.append(">").append(escapeXml(content)).append("</xs:appinfo>");
            }
            xmlStrings.add(sb.toString());
        }
        return xmlStrings;
    }

    /**
     * Converts to a display string for UI.
     *
     * @return formatted string with all entries
     */
    public String toDisplayString() {
        if (entries.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                sb.append("\n");
            }
            AppInfoEntry entry = entries.get(i);
            if (entry.getTag() != null && !entry.getTag().isEmpty()) {
                sb.append(entry.getTag()).append(" ");
                sb.append(entry.getContent());
            } else if ((entry.getContent() == null || entry.getContent().isEmpty())
                    && entry.getSource() != null && !entry.getSource().isEmpty()) {
                // A bare <xs:appinfo source="urn:..."/> has no text of its own to show.
                sb.append(entry.getSource());
            } else {
                sb.append(entry.getContent());
            }
        }
        return sb.toString();
    }

    /**
     * Parses display string back to entries.
     * Each line starting with @ is treated as a tagged entry.
     *
     * @param displayString the string from UI
     * @return XsdAppInfo instance
     */
    public static XsdAppInfo fromDisplayString(String displayString) {
        XsdAppInfo appInfo = new XsdAppInfo();
        if (displayString == null || displayString.trim().isEmpty()) {
            return appInfo;
        }

        String[] lines = displayString.split("\n");
        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty()) {
                continue;
            }
            String tag = tagOf(trimmedLine);
            if (tag != null) {
                appInfo.addTagEntry(tag, valueOf(trimmedLine, ""));
            } else {
                appInfo.addEntry(null, trimmedLine, null);
            }
        }

        return appInfo;
    }

    private static String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    // ==================== Structured Access Methods ====================

    /**
     * Gets the @since version string.
     *
     * @return the since version, or null if not set
     */
    public String getSince() {
        return firstContentOf("@since");
    }

    /**
     * Sets the @since version.
     *
     * @param version the version string (e.g., "4.0.0"), or null to clear
     */
    public void setSince(String version) {
        removeEntriesWithTag("@since");
        if (version != null && !version.trim().isEmpty()) {
            addTagEntry("@since", version);
        }
    }

    /**
     * Gets the @version string.
     *
     * @return the version, or null if not set
     */
    public String getVersion() {
        return firstContentOf("@version");
    }

    /**
     * Sets the @version string.
     *
     * @param version the version string, or null to clear
     */
    public void setVersion(String version) {
        removeEntriesWithTag("@version");
        if (version != null && !version.trim().isEmpty()) {
            addTagEntry("@version", version);
        }
    }

    /**
     * Gets the @author string.
     *
     * @return the author, or null if not set
     */
    public String getAuthor() {
        return firstContentOf("@author");
    }

    /**
     * Sets the @author string.
     *
     * @param author the author name, or null to clear
     */
    public void setAuthor(String author) {
        removeEntriesWithTag("@author");
        if (author != null && !author.trim().isEmpty()) {
            addTagEntry("@author", author);
        }
    }

    /**
     * Gets all @see references.
     *
     * @return list of see references (may contain {@link} tags)
     */
    public List<String> getSeeReferences() {
        return getEntriesWithTag("@see").stream()
                .map(AppInfoEntry::getContent)
                .toList();
    }

    /**
     * Adds a @see reference.
     *
     * @param reference the reference (may contain {@link} tags)
     */
    public void addSeeReference(String reference) {
        if (reference != null && !reference.trim().isEmpty()) {
            addTagEntry("@see", reference);
        }
    }

    /**
     * Removes a specific @see reference.
     *
     * @param reference the reference to remove
     */
    public void removeSeeReference(String reference) {
        if (reference == null) {
            return;
        }
        String trimmed = reference.trim();
        entries.removeIf(e -> "@see".equals(e.getTag()) &&
                Objects.equals(trimmed, e.getContent()));
    }

    /**
     * Clears all @see references.
     */
    public void clearSeeReferences() {
        removeEntriesWithTag("@see");
    }

    /**
     * Gets the @deprecated message.
     *
     * @return the deprecation message (may contain {@link} tags), or null if not deprecated
     */
    public String getDeprecated() {
        return firstContentOf("@deprecated");
    }

    /**
     * Checks if the element is marked as deprecated.
     *
     * @return true if deprecated
     */
    public boolean isDeprecated() {
        return !getEntriesWithTag("@deprecated").isEmpty();
    }

    /**
     * Sets the @deprecated message.
     *
     * @param message the deprecation message (may contain {@link} tags), or null to clear
     */
    public void setDeprecated(String message) {
        removeEntriesWithTag("@deprecated");
        if (message != null) {
            addTagEntry("@deprecated", message);
        }
    }

    /**
     * Clears the deprecation status.
     */
    public void clearDeprecated() {
        removeEntriesWithTag("@deprecated");
    }

    /**
     * Gets the node-level Markdown rendering preference (@markdown).
     *
     * @return TRUE/FALSE when the node states a preference, null when it does not
     */
    public Boolean getMarkdown() {
        return getEntriesWithTag("@markdown").stream()
                .findFirst()
                .map(e -> parseBooleanFlag(e.getContent()))
                .orElse(null);
    }

    /**
     * Sets the node-level Markdown rendering preference.
     *
     * @param markdown TRUE/FALSE to state a preference, null to remove the tag
     */
    public void setMarkdown(Boolean markdown) {
        removeEntriesWithTag("@markdown");
        if (markdown != null) {
            addTagEntry("@markdown", markdown ? "true" : "false");
        }
    }

    /**
     * Removes all entries with a specific tag.
     *
     * @param tag the tag to remove (e.g., "@since", "@deprecated")
     */
    public void removeEntriesWithTag(String tag) {
        entries.removeIf(e -> Objects.equals(tag, e.getTag()));
    }

    private String firstContentOf(String tag) {
        return getEntriesWithTag(tag).stream()
                .findFirst()
                .map(AppInfoEntry::getContent)
                .orElse(null);
    }

    // ==================== Example Values (fxt:exampleValues / altova:exampleValues) ====================

    /**
     * Checks whether an entry holds an {@code exampleValues} block, in either the FreeXmlToolkit
     * or the Altova namespace.
     *
     * @param entry the entry to test
     * @return true if the entry's raw XML contains an {@code exampleValues} element
     */
    public static boolean isExampleValuesEntry(AppInfoEntry entry) {
        if (entry == null || !entry.hasRawXml()) {
            return false;
        }
        String raw = entry.getRawXml();
        return raw.contains(":exampleValues") || raw.contains("<exampleValues");
    }

    /**
     * Gets the example values declared via {@code fxt:exampleValues} or the legacy
     * {@code altova:exampleValues}.
     *
     * @return the list of example values (empty if none)
     */
    public List<String> getExampleValues() {
        List<String> values = new ArrayList<>();
        for (AppInfoEntry entry : entries) {
            if (isExampleValuesEntry(entry)) {
                java.util.regex.Matcher m =
                        java.util.regex.Pattern.compile("value\\s*=\\s*\"([^\"]*)\"").matcher(entry.getRawXml());
                while (m.find()) {
                    values.add(unescapeXml(m.group(1)));
                }
                break; // only the first exampleValues block is managed
            }
        }
        return values;
    }

    /**
     * Checks whether any example values are present.
     *
     * @return true if an {@code exampleValues} block exists
     */
    public boolean hasExampleValues() {
        return entries.stream().anyMatch(XsdAppInfo::isExampleValuesEntry);
    }

    /**
     * Replaces the example-values block with the given values, written as
     * {@code fxt:exampleValues}. Any existing block (including a legacy {@code altova:} one) is
     * removed first; a blank/empty list clears example values entirely. The block carries its own
     * {@code xmlns:fxt} declaration so it serializes as valid XML regardless of the schema-root
     * namespace declarations.
     *
     * @param values the example values (null/empty clears)
     */
    public void setExampleValues(List<String> values) {
        entries.removeIf(XsdAppInfo::isExampleValuesEntry);
        if (values == null) {
            return;
        }
        List<String> clean = values.stream().filter(v -> v != null && !v.isBlank()).map(String::trim).toList();
        if (clean.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<fxt:exampleValues xmlns:fxt=\"").append(FXT_EXT_NS).append("\">");
        for (String v : clean) {
            sb.append("<fxt:example value=\"").append(escapeXml(v)).append("\"/>");
        }
        sb.append("</fxt:exampleValues>");
        entries.add(new AppInfoEntry(null, null, "", sb.toString()));
    }

    private static String unescapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
    }

    /**
     * Creates a deep copy of this XsdAppInfo.
     *
     * @return a new XsdAppInfo with copied entries
     */
    public XsdAppInfo copy() {
        XsdAppInfo copy = new XsdAppInfo();
        for (AppInfoEntry entry : entries) {
            copy.addEntry(new AppInfoEntry(entry.getSource(), entry.getTag(), entry.getContent(), entry.getRawXml()));
        }
        return copy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        XsdAppInfo that = (XsdAppInfo) o;
        return Objects.equals(entries, that.entries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entries);
    }

    @Override
    public String toString() {
        return toDisplayString();
    }
}
