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

        public AppInfoEntry(String source, String tag, String content) {
            this.source = source;
            this.tag = tag;
            this.content = content;
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AppInfoEntry that = (AppInfoEntry) o;
            return Objects.equals(source, that.source) &&
                    Objects.equals(tag, that.tag) &&
                    Objects.equals(content, that.content);
        }

        @Override
        public int hashCode() {
            return Objects.hash(source, tag, content);
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

        entries.add(new AppInfoEntry(source, tag, actualContent));
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
            if (entry.getTag() != null && !entry.getTag().isEmpty()) {
                sb.append(escapeXml(entry.getTag())).append(" ");
            }
            sb.append(escapeXml(entry.getContent()));
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
