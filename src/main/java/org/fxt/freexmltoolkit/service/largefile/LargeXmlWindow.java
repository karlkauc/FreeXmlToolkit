package org.fxt.freexmltoolkit.service.largefile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxt.freexmltoolkit.service.largefile.LargeXmlStructure.Node;

/**
 * A part of a large XML file that is loaded into memory to be shown in the grid and edited: a run of
 * consecutive children of one element (one element is a run of one), or the root element of a file
 * small enough to load whole.
 *
 * <p>{@link #text()} is a document of its own: the parent's start tag as it is in the file (with the
 * namespace declarations of the ancestors it lacks added), the run's bytes and the parent's end tag.
 * {@link #patch(String)} turns the edited document back into the bytes that replace the run; the rest
 * of the file stays as it is ({@link LargeFilePatcher}).</p>
 */
public final class LargeXmlWindow {

    /** A window is at most this large. */
    public static final int MAX_WINDOW_BYTES = 20 * 1024 * 1024;

    private static final Pattern XMLNS = Pattern.compile("(xmlns(?::[^\\s=/>]+)?)\\s*=\\s*(\"[^\"]*\"|'[^']*')");

    private final Path path;
    private final Charset charset;
    private final long fileSize;
    private final long fileModified;
    private final Node parent;
    private final List<Node> ancestors;
    private final long rangeStart;
    private final long rangeEnd;
    private final long firstIndex;
    private final int count;
    private final String wrapperStartTag;
    private final String wrapperEndTag;
    private final String content;

    private LargeXmlWindow(LargeXmlStructure structure, Node parent, List<Node> ancestors, long rangeStart,
                           long rangeEnd, long firstIndex, int count) throws IOException {
        LargeFileIndex index = structure.index(); // NOPMD - owned by the structure's viewer
        this.path = index.path();
        this.charset = index.charset();
        this.fileSize = Files.size(path);
        this.fileModified = Files.getLastModifiedTime(path).toMillis();
        this.parent = parent;
        this.ancestors = List.copyOf(ancestors);
        this.rangeStart = rangeStart;
        this.rangeEnd = rangeEnd;
        this.firstIndex = firstIndex;
        this.count = count;
        this.content = structure.text(rangeStart, rangeEnd, MAX_WINDOW_BYTES);
        if (parent == null) {
            wrapperStartTag = "";
            wrapperEndTag = "";
        } else {
            String startTag = structure.text(parent.start(), parent.contentStart(), MAX_WINDOW_BYTES);
            if (startTag.endsWith("/>")) {
                throw new IOException(parent.xpath() + " has no content.");
            }
            wrapperStartTag = withInheritedNamespaces(startTag, parent.name(), declarations(structure, ancestors));
            wrapperEndTag = "</" + parent.name() + ">";
        }
    }

    /**
     * A window of consecutive children of {@code parent}.
     *
     * @param ancestors the elements from the root down to the parent's parent (their namespace
     *                  declarations are copied to the window's root)
     * @param children  consecutive children of the parent, in document order
     * @throws IOException when the run is larger than {@link #MAX_WINDOW_BYTES}
     */
    public static LargeXmlWindow ofChildren(LargeXmlStructure structure, Node parent, List<Node> ancestors,
                                            List<Node> children) throws IOException {
        if (children.isEmpty()) {
            throw new IOException(parent.xpath() + " has no child elements.");
        }
        Node first = children.getFirst();
        Node last = children.getLast();
        return new LargeXmlWindow(structure, parent, ancestors, first.start(), last.end(), first.index(), children.size());
    }

    /** A window of the root element (for a file whose root fits {@link #MAX_WINDOW_BYTES}). */
    public static LargeXmlWindow ofRoot(LargeXmlStructure structure, Node root) throws IOException {
        return new LargeXmlWindow(structure, null, List.of(), root.start(), root.end(), 0, 1);
    }

    /**
     * The longest prefix of {@code children} (at least one) whose bytes fit {@code maxBytes}.
     */
    public static List<Node> fitting(List<Node> children, long maxBytes) {
        if (children.isEmpty()) {
            return children;
        }
        long start = children.getFirst().start();
        int n = 1;
        while (n < children.size() && children.get(n).end() - start <= maxBytes) {
            n++;
        }
        return children.subList(0, n);
    }

    /** @return the namespace declarations in scope at the parent's parent, outer first (inner wins) */
    private static Map<String, String> declarations(LargeXmlStructure structure, List<Node> ancestors)
            throws IOException {
        Map<String, String> declared = new LinkedHashMap<>();
        for (Node ancestor : ancestors) {
            String startTag = structure.text(ancestor.start(), ancestor.contentStart(), MAX_WINDOW_BYTES);
            Matcher matcher = XMLNS.matcher(startTag);
            while (matcher.find()) {
                declared.remove(matcher.group(1));
                declared.put(matcher.group(1), matcher.group(2));
            }
        }
        return declared;
    }

    /** Adds the declarations the start tag does not make itself right after the element name. */
    static String withInheritedNamespaces(String startTag, String name, Map<String, String> inherited) {
        Map<String, String> own = new LinkedHashMap<>();
        Matcher matcher = XMLNS.matcher(startTag);
        while (matcher.find()) {
            own.put(matcher.group(1), matcher.group(2));
        }
        StringBuilder added = new StringBuilder();
        inherited.forEach((attribute, value) -> {
            if (!own.containsKey(attribute)) {
                added.append(' ').append(attribute).append('=').append(value);
            }
        });
        int afterName = 1 + name.length();
        return startTag.substring(0, afterName) + added + startTag.substring(afterName);
    }

    /** @return the window as a document of its own */
    public String text() {
        return wrapperStartTag + content + wrapperEndTag;
    }

    /**
     * The bytes that replace the window's range for an edited document.
     *
     * @param edited the window's document after editing (as serialized by the editor; an XML
     *               declaration and leading comments are allowed)
     * @throws IOException when the edited document's root is not the window's root, when the parent's
     *                     start tag was changed (only its content is written), or when a character
     *                     cannot be written in the file's encoding outside text and attribute values
     */
    public byte[] patch(String edited) throws IOException {
        Root root = Root.of(edited);
        String replacement;
        if (parent == null) {
            replacement = edited.substring(root.start(), root.end());
        } else {
            if (!root.name().equals(parent.name())) {
                throw new IOException("The window's root must stay " + parent.name() + ".");
            }
            if (!attributes(root.startTag()).equals(attributes(wrapperStartTag))) {
                throw new IOException("Changes to the attributes of " + parent.name()
                        + " cannot be written from this window; only its content is written.");
            }
            replacement = root.selfClosing() ? "" : edited.substring(root.contentStart(), root.contentEnd()).strip();
        }
        return encode(replacement, charset);
    }

    /** The attributes of a start tag (name → value as written, quotes stripped), order-insensitive. */
    private static Map<String, String> attributes(String startTag) {
        Map<String, String> attributes = new java.util.TreeMap<>();
        Matcher matcher = Pattern.compile("([^\\s=/<>]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')").matcher(startTag);
        while (matcher.find()) {
            attributes.put(matcher.group(1), matcher.group(2) != null ? matcher.group(2) : matcher.group(3));
        }
        return attributes;
    }

    /**
     * Encodes in the file's charset; a character the charset cannot represent becomes a character
     * reference ({@code &#x…;}), which is only valid in text and attribute values.
     */
    static byte[] encode(String text, Charset charset) throws IOException {
        CharsetEncoder encoder = charset.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return toBytes(encoder.encode(CharBuffer.wrap(text)));
        } catch (CharacterCodingException e) {
            encoder.reset();
            StringBuilder escaped = new StringBuilder(text.length() + 64);
            boolean inTag = false;
            char quote = 0;
            for (int i = 0; i < text.length(); ) {
                int codePoint = text.codePointAt(i);
                String ch = Character.toString(codePoint);
                if (!encoder.canEncode(ch)) {
                    if (inTag && quote == 0) {
                        throw new IOException("The character " + ch + " (U+" + Integer.toHexString(codePoint)
                                .toUpperCase(Locale.ROOT) + ") cannot be written in a name in " + charset.name() + ".", e);
                    }
                    escaped.append("&#x").append(Integer.toHexString(codePoint).toUpperCase(Locale.ROOT)).append(';');
                } else {
                    escaped.append(ch);
                    if (inTag) {
                        if (quote != 0 && codePoint == quote) {
                            quote = 0;
                        } else if (quote == 0 && (codePoint == '"' || codePoint == '\'')) {
                            quote = (char) codePoint;
                        } else if (quote == 0 && codePoint == '>') {
                            inTag = false;
                        }
                    } else if (codePoint == '<') {
                        inTag = true;
                    }
                }
                i += Character.charCount(codePoint);
            }
            return toBytes(encoder.reset().encode(CharBuffer.wrap(escaped)));
        }
    }

    private static byte[] toBytes(ByteBuffer buffer) {
        byte[] result = new byte[buffer.remaining()];
        buffer.get(result);
        return result;
    }

    /** The root element of a serialized document: where its start tag, content and end tag are. */
    record Root(String name, int start, int contentStart, int contentEnd, int end, String startTag, boolean selfClosing) {
        static Root of(String xml) throws IOException {
            int at = 0;
            while (true) {
                at = skipSpace(xml, at);
                if (xml.startsWith("<?", at)) {
                    at = after(xml, at, "?>");
                } else if (xml.startsWith("<!--", at)) {
                    at = after(xml, at, "-->");
                } else if (xml.startsWith("<!", at)) {
                    at = after(xml, at, ">");
                } else {
                    break;
                }
            }
            if (!xml.startsWith("<", at)) {
                throw new IOException("The edited window has no root element.");
            }
            int start = at;
            int nameEnd = at + 1;
            while (nameEnd < xml.length() && " \t\r\n/>".indexOf(xml.charAt(nameEnd)) < 0) {
                nameEnd++;
            }
            String name = xml.substring(start + 1, nameEnd);
            char quote = 0;
            int i = nameEnd;
            for (; i < xml.length(); i++) {
                char c = xml.charAt(i);
                if (quote != 0) {
                    if (c == quote) {
                        quote = 0;
                    }
                } else if (c == '"' || c == '\'') {
                    quote = c;
                } else if (c == '>') {
                    break;
                }
            }
            if (i >= xml.length()) {
                throw new IOException("The edited window's root start tag is not closed.");
            }
            int contentStart = i + 1;
            String startTag = xml.substring(start, contentStart);
            if (startTag.endsWith("/>")) {
                return new Root(name, start, contentStart, contentStart, contentStart, startTag, true);
            }
            String endTag = "</" + name;
            int contentEnd = xml.lastIndexOf(endTag);
            if (contentEnd < contentStart) {
                throw new IOException("The edited window's root end tag is missing.");
            }
            int end = xml.indexOf('>', contentEnd);
            if (end < 0) {
                throw new IOException("The edited window's root end tag is not closed.");
            }
            return new Root(name, start, contentStart, contentEnd, end + 1, startTag, false);
        }

        private static int skipSpace(String xml, int at) {
            while (at < xml.length() && Character.isWhitespace(xml.charAt(at))) {
                at++;
            }
            return at;
        }

        private static int after(String xml, int at, String end) throws IOException {
            int found = xml.indexOf(end, at);
            if (found < 0) {
                throw new IOException("The edited window is not well-formed.");
            }
            return found + end.length();
        }
    }

    /** @return the file */
    public Path path() {
        return path;
    }

    /** @return the file's size when the window was read */
    public long fileSize() {
        return fileSize;
    }

    /** @return the file's modification time (ms) when the window was read */
    public long fileModified() {
        return fileModified;
    }

    /** @return the parent whose children the window holds, or {@code null} for the root window */
    public Node parent() {
        return parent;
    }

    /** @return the ancestors of the parent, from the root down */
    public List<Node> ancestors() {
        return ancestors;
    }

    /** @return the offset of the window's first byte in the file */
    public long rangeStart() {
        return rangeStart;
    }

    /** @return the offset after the window's last byte in the file */
    public long rangeEnd() {
        return rangeEnd;
    }

    /** @return the 0-based index of the first child among the parent's children */
    public long firstIndex() {
        return firstIndex;
    }

    /** @return the number of children in the window */
    public int count() {
        return count;
    }

    /** @return the patch that writes an edited document over the window's range */
    public LargeFilePatcher.Patch patchFor(String edited) throws IOException {
        return new LargeFilePatcher.Patch(rangeStart, rangeEnd, patch(edited));
    }
}
