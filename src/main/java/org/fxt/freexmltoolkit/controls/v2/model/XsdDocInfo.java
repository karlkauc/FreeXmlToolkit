package org.fxt.freexmltoolkit.controls.v2.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents Javadoc-style technical documentation for XSD elements and types.
 * Supports @since, @see, and @deprecated annotations with {@link} tags.
 * <p>
 * These annotations are stored in XSD files as:
 * &lt;xs:appinfo source="@since 4.0.0"/&gt;
 * &lt;xs:appinfo source="@see {@link /Path/To/Element}"/&gt;
 * &lt;xs:appinfo source="@deprecated use {@link /Path/To/New/Element} instead"/&gt;
 *
 * @since 2.0
 */
public class XsdDocInfo {

    private String sinceVersion;
    private final List<SeeReference> seeReferences = new ArrayList<>();
    private DeprecationInfo deprecationInfo;

    // Pattern to parse {@link XPath} tags
    private static final Pattern LINK_PATTERN = Pattern.compile("\\{@link\\s+([^}]+)\\}");

    /**
     * Creates an empty XsdDocInfo object.
     */
    public XsdDocInfo() {
    }

    /**
     * Returns the version since which this element is available.
     *
     * @return the since version, or null if not set
     */
    public String getSinceVersion() {
        return sinceVersion;
    }

    /**
     * Sets the since version.
     *
     * @param sinceVersion the version string (e.g., "4.0.0")
     */
    public void setSinceVersion(String sinceVersion) {
        this.sinceVersion = sinceVersion;
    }

    /**
     * Returns all @see references.
     *
     * @return unmodifiable list of see references
     */
    public List<SeeReference> getSeeReferences() {
        return Collections.unmodifiableList(seeReferences);
    }

    /**
     * Adds a @see reference.
     *
     * @param reference the reference to add
     */
    public void addSeeReference(SeeReference reference) {
        Objects.requireNonNull(reference, "See reference cannot be null");
        this.seeReferences.add(reference);
    }

    /**
     * Adds a @see reference from a string.
     * Parses {@link} tags if present.
     *
     * @param referenceText the reference text
     */
    public void addSeeReference(String referenceText) {
        Objects.requireNonNull(referenceText, "Reference text cannot be null");
        this.seeReferences.add(parseSeeReference(referenceText));
    }

    /**
     * Returns the deprecation information.
     *
     * @return the deprecation info, or null if not deprecated
     */
    public DeprecationInfo getDeprecationInfo() {
        return deprecationInfo;
    }

    /**
     * Sets the deprecation information.
     *
     * @param deprecationInfo the deprecation info
     */
    public void setDeprecationInfo(DeprecationInfo deprecationInfo) {
        this.deprecationInfo = deprecationInfo;
    }

    /**
     * Marks this element as deprecated.
     *
     * @param message the deprecation message
     */
    public void setDeprecated(String message) {
        this.deprecationInfo = parseDeprecation(message);
    }

    /**
     * Checks if this element is deprecated.
     *
     * @return true if deprecated, false otherwise
     */
    public boolean isDeprecated() {
        return deprecationInfo != null;
    }

    /**
     * Checks if this XsdDocInfo contains any information.
     *
     * @return true if empty, false if it contains any documentation
     */
    public boolean isEmpty() {
        return sinceVersion == null && seeReferences.isEmpty() && deprecationInfo == null;
    }

    /**
     * Parses a @see reference string and extracts {@link} tags.
     */
    private SeeReference parseSeeReference(String text) {
        List<XsdLinkTag> links = extractLinks(text);
        return new SeeReference(text, links);
    }

    /**
     * Parses a @deprecated message and extracts {@link} tags.
     */
    private DeprecationInfo parseDeprecation(String message) {
        List<XsdLinkTag> links = extractLinks(message);
        return new DeprecationInfo(message, links);
    }

    /**
     * Extracts all {@link XPath} tags from a string.
     */
    private List<XsdLinkTag> extractLinks(String text) {
        List<XsdLinkTag> links = new ArrayList<>();
        Matcher matcher = LINK_PATTERN.matcher(text);
        while (matcher.find()) {
            String xpath = matcher.group(1).trim();
            links.add(new XsdLinkTag(xpath));
        }
        return links;
    }

    /**
         * Represents a @see reference.
         */
        public record SeeReference(String text, List<XsdLinkTag> linkTags) {
            public SeeReference(String text, List<XsdLinkTag> linkTags) {
                this.text = Objects.requireNonNull(text, "Text cannot be null");
                this.linkTags = linkTags != null ? new ArrayList<>(linkTags) : new ArrayList<>();
            }

            @Override
            public List<XsdLinkTag> linkTags() {
                return Collections.unmodifiableList(linkTags);
            }

            public boolean hasLinks() {
                return !linkTags.isEmpty();
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                SeeReference that = (SeeReference) o;
                return Objects.equals(text, that.text);
            }

            @Override
            public int hashCode() {
                return Objects.hash(text);
            }

            @Override
            public String toString() {
                return "@see " + text;
            }
        }

    /**
         * Represents deprecation information.
         */
        public record DeprecationInfo(String message, List<XsdLinkTag> replacementLinks) {
            public DeprecationInfo(String message, List<XsdLinkTag> replacementLinks) {
                this.message = Objects.requireNonNull(message, "Message cannot be null");
                this.replacementLinks = replacementLinks != null ? new ArrayList<>(replacementLinks) : new ArrayList<>();
            }

            @Override
            public List<XsdLinkTag> replacementLinks() {
                return Collections.unmodifiableList(replacementLinks);
            }

            public boolean hasReplacements() {
                return !replacementLinks.isEmpty();
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                DeprecationInfo that = (DeprecationInfo) o;
                return Objects.equals(message, that.message);
            }

            @Override
            public int hashCode() {
                return Objects.hash(message);
            }

            @Override
            public String toString() {
                return "@deprecated " + message;
            }
        }
}
