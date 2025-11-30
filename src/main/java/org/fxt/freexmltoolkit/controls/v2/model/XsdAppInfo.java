package org.fxt.freexmltoolkit.controls.v2.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents structured xs:appinfo content with JavaDoc-style tags.
 * <p>
 * Supports tags like:
 * <ul>
 *   <li>@since - Version information</li>
 *   <li>@see - References to other elements</li>
 *   <li>@deprecated - Deprecation notices</li>
 *   <li>@author - Author information</li>
 *   <li>@version - Version details</li>
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
        private final String content; // The content after the tag
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
         * This is used when the appinfo contains XML elements (like altova:exampleValues).
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
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
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
                return tag + " " + content;
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
     * Adds an appinfo entry with source and content (parses tag from content).
     *
     * @param source  the source attribute value
     * @param content the content (may start with a tag like "@since")
     */
    public void addEntry(String source, String content) {
        addEntry(source, content, null);
    }

    /**
     * Adds an appinfo entry with source, content, and optional raw XML.
     *
     * @param source  the source attribute value
     * @param content the text content (may start with a tag like "@since")
     * @param rawXml  the raw XML content (if appinfo contains XML elements)
     */
    public void addEntry(String source, String content, String rawXml) {
        // If we have raw XML, store it directly
        if (rawXml != null && !rawXml.trim().isEmpty()) {
            String trimmedContent = content != null ? content.trim() : "";
            String tag = null;

            // Parse JavaDoc-style tag from source attribute if present
            if (source != null && source.startsWith("@")) {
                int spaceIndex = source.indexOf(' ');
                if (spaceIndex > 0) {
                    tag = source.substring(0, spaceIndex);
                } else {
                    tag = source;
                }
            }

            entries.add(new AppInfoEntry(source, tag, trimmedContent, rawXml.trim()));
            return;
        }

        if (content == null || content.trim().isEmpty()) {
            return;
        }

        String trimmedContent = content.trim();
        String tag = null;
        String actualContent = trimmedContent;

        // Parse JavaDoc-style tag
        if (trimmedContent.startsWith("@")) {
            int spaceIndex = trimmedContent.indexOf(' ');
            if (spaceIndex > 0) {
                tag = trimmedContent.substring(0, spaceIndex);
                actualContent = trimmedContent.substring(spaceIndex + 1).trim();
            } else {
                tag = trimmedContent;
                actualContent = "";
            }
        }

        entries.add(new AppInfoEntry(source, tag, actualContent, null));
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
     * Converts to XML format for serialization.
     *
     * @return list of XML strings representing xs:appinfo elements
     */
    public List<String> toXmlStrings() {
        List<String> xmlStrings = new ArrayList<>();
        for (AppInfoEntry entry : entries) {
            StringBuilder sb = new StringBuilder();
            sb.append("<xs:appinfo");
            if (entry.getSource() != null && !entry.getSource().isEmpty()) {
                sb.append(" source=\"").append(escapeXml(entry.getSource())).append("\"");
            }
            sb.append(">");

            // Use raw XML if available, otherwise use text content
            if (entry.hasRawXml()) {
                sb.append("\n").append(entry.getRawXml()).append("\n");
            } else {
                if (entry.getTag() != null && !entry.getTag().isEmpty()) {
                    sb.append(escapeXml(entry.getTag())).append(" ");
                }
                sb.append(escapeXml(entry.getContent()));
            }

            sb.append("</xs:appinfo>");
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
            }
            sb.append(entry.getContent());
        }
        return sb.toString();
    }

    /**
     * Parses display string back to entries.
     * Each line starting with @ is treated as a new entry.
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
            if (!trimmedLine.isEmpty()) {
                appInfo.addEntry(trimmedLine, trimmedLine);
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
        return getEntriesWithTag("@since").stream()
                .findFirst()
                .map(AppInfoEntry::getContent)
                .orElse(null);
    }

    /**
     * Sets the @since version.
     *
     * @param version the version string (e.g., "4.0.0"), or null to clear
     */
    public void setSince(String version) {
        removeEntriesWithTag("@since");
        if (version != null && !version.trim().isEmpty()) {
            String trimmed = version.trim();
            addEntry("@since " + trimmed, "@since " + trimmed);
        }
    }

    /**
     * Gets the @version string.
     *
     * @return the version, or null if not set
     */
    public String getVersion() {
        return getEntriesWithTag("@version").stream()
                .findFirst()
                .map(AppInfoEntry::getContent)
                .orElse(null);
    }

    /**
     * Sets the @version string.
     *
     * @param version the version string, or null to clear
     */
    public void setVersion(String version) {
        removeEntriesWithTag("@version");
        if (version != null && !version.trim().isEmpty()) {
            String trimmed = version.trim();
            addEntry("@version " + trimmed, "@version " + trimmed);
        }
    }

    /**
     * Gets the @author string.
     *
     * @return the author, or null if not set
     */
    public String getAuthor() {
        return getEntriesWithTag("@author").stream()
                .findFirst()
                .map(AppInfoEntry::getContent)
                .orElse(null);
    }

    /**
     * Sets the @author string.
     *
     * @param author the author name, or null to clear
     */
    public void setAuthor(String author) {
        removeEntriesWithTag("@author");
        if (author != null && !author.trim().isEmpty()) {
            String trimmed = author.trim();
            addEntry("@author " + trimmed, "@author " + trimmed);
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
            String trimmed = reference.trim();
            addEntry("@see " + trimmed, "@see " + trimmed);
        }
    }

    /**
     * Removes a specific @see reference.
     *
     * @param reference the reference to remove
     */
    public void removeSeeReference(String reference) {
        if (reference == null) return;
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
        return getEntriesWithTag("@deprecated").stream()
                .findFirst()
                .map(AppInfoEntry::getContent)
                .orElse(null);
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
            String trimmed = message.trim();
            addEntry("@deprecated " + trimmed, "@deprecated " + trimmed);
        }
    }

    /**
     * Clears the deprecation status.
     */
    public void clearDeprecated() {
        removeEntriesWithTag("@deprecated");
    }

    /**
     * Removes all entries with a specific tag.
     *
     * @param tag the tag to remove (e.g., "@since", "@deprecated")
     */
    public void removeEntriesWithTag(String tag) {
        entries.removeIf(e -> Objects.equals(tag, e.getTag()));
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
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
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
