package org.fxt.freexmltoolkit.service.largefile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The element structure of a large XML file, read on demand: a byte-level scanner (no DOM, no
 * full parse) lists the child elements of one element at a time, a page at a time, with their
 * exact byte ranges. Children of an element with millions of children are listed page by page; a
 * page continues where the previous one stopped. Not a validating parser: it relies on the
 * document being well-formed and reads comments, CDATA, processing instructions and the DOCTYPE
 * only to skip them.
 */
public final class LargeXmlStructure {

    /** Values (attributes, leaf texts) longer than this are cut. */
    static final int MAX_VALUE = 200;
    /** At most this many fields per element. */
    static final int MAX_FIELDS = 60;
    private static final int BUFFER = 1 << 20;
    private static final Pattern ATTRIBUTE = Pattern.compile("([^\\s=/>]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')");

    /**
     * An element.
     *
     * @param name        the tag name (with prefix)
     * @param start       byte offset of its {@code <}
     * @param end         byte offset after its end tag (or after {@code />}); the file size when unknown
     * @param position    1-based position among the same-named siblings
     * @param xpath       its path from the root, e.g. {@code /orders[1]/order[42]}
     * @param hasChildren whether it contains elements
     * @param text        its text when it contains no elements (cut), else empty
     * @param fields      its attributes ({@code @name}) and the texts of its child elements without
     *                    children of their own (by name; the first of each), in document order
     */
    public record Node(String name, long start, long end, int position, String xpath, boolean hasChildren,
                       String text, Map<String, String> fields) {
    }

    /**
     * A page of children.
     *
     * @param children the children found
     * @param next     where the next page starts (inside the parent), or -1 when all were listed
     * @param positions the same-name counters after this page (for the positions of the next page)
     */
    public record Page(List<Node> children, long next, Map<String, Integer> positions) {
    }

    private final LargeFileIndex index;
    private final Charset charset;

    public LargeXmlStructure(LargeFileIndex index) {
        this.index = index;
        this.charset = index.charset();
    }

    /** @return the root element (its end is the end of the file until its children were listed) */
    public Node root() throws IOException {
        Scanner scanner = new Scanner(index.bomLength(), 0, null);
        Node[] found = new Node[1];
        scanner.run(new Listener() {
            @Override
            public boolean start(String name, long offset, String attributes, boolean selfClosing, long afterTag) {
                found[0] = new Node(name, offset, selfClosing ? afterTag : index.size(), 1, "/" + name + "[1]",
                        !selfClosing, "", fields(attributes));
                return false;
            }
        }, () -> false);
        if (found[0] == null) {
            throw new IOException("No root element found: the file is not XML.");
        }
        return found[0];
    }

    /**
     * Lists the children of {@code parent}.
     *
     * @param from      where to continue (a {@link Page#next()}), or the parent's start for the first page
     * @param positions the same-name counters of the previous page, or {@code null} for the first page
     * @param max       the page size
     * @param cancelled polled per megabyte (may be {@code null})
     * @param progress  receives the offset reached (may be {@code null})
     */
    public Page children(Node parent, long from, Map<String, Integer> positions, int max, BooleanSupplier cancelled,
                         LongConsumer progress) throws IOException {
        boolean first = from <= parent.start();
        Map<String, Integer> counters = new HashMap<>(positions == null ? Map.of() : positions);
        List<Node> children = new ArrayList<>();
        long[] next = {-1};
        // depth 1 = inside the parent; a child's start tag takes the depth to 2
        Scanner scanner = new Scanner(first ? parent.start() : from, first ? 0 : 1, progress);
        scanner.run(new Listener() {
            // the child being read and what is known about it
            String childName;
            long childStart;
            String childAttributes;
            boolean childHasChildren;
            StringBuilder childText;
            Map<String, String> leafTexts;
            String leafName;
            boolean leafHasChildren;
            StringBuilder leafText;

            @Override
            public boolean start(String name, long offset, String attributes, boolean selfClosing, long afterTag) {
                // the depth the element itself occupies (a self-closing tag does not change the scanner's depth)
                int depth = selfClosing ? scanner.depth + 1 : scanner.depth;
                if (depth == 2) {
                    if (children.size() >= max) {
                        next[0] = offset;
                        return false;
                    }
                    childName = name;
                    childStart = offset;
                    childAttributes = attributes;
                    childHasChildren = false;
                    childText = new StringBuilder();
                    leafTexts = new LinkedHashMap<>();
                    if (selfClosing) {
                        finishChild(afterTag);
                    }
                } else if (depth == 3) {
                    childHasChildren = true;
                    leafName = name;
                    leafHasChildren = false;
                    leafText = new StringBuilder();
                    if (selfClosing) {
                        finishLeaf();
                    }
                } else if (depth >= 4) {
                    leafHasChildren = true;
                }
                return true;
            }

            @Override
            public void text(String text) {
                int depth = scanner.depth;
                if (depth == 2 && childText != null && !childHasChildren && childText.length() < MAX_VALUE) {
                    childText.append(text);
                } else if (depth == 3 && leafText != null && !leafHasChildren && leafText.length() < MAX_VALUE) {
                    leafText.append(text);
                }
            }

            @Override
            public boolean end(String name, long afterTag) {
                int depthBefore = scanner.depthBefore;
                if (depthBefore == 3 && leafName != null) {
                    finishLeaf();
                } else if (depthBefore == 2 && childName != null) {
                    finishChild(afterTag);
                } else if (depthBefore == 1) {
                    return false;               // the parent ends: all children listed
                }
                return true;
            }

            private void finishLeaf() {
                String value = leafText.toString().strip();
                if (!leafHasChildren && !value.isEmpty() && leafTexts.size() < MAX_FIELDS) {
                    leafTexts.putIfAbsent(leafName, cut(value));
                }
                leafName = null;
            }

            private void finishChild(long end) {
                int position = counters.merge(childName, 1, Integer::sum);
                Map<String, String> fields = fields(childAttributes);
                leafTexts.forEach((k, v) -> {
                    if (fields.size() < MAX_FIELDS) {
                        fields.putIfAbsent(k, v);
                    }
                });
                String text = childHasChildren ? "" : cut(childText.toString().strip());
                children.add(new Node(childName, childStart, end, position,
                        parent.xpath() + "/" + childName + "[" + position + "]", childHasChildren, text, fields));
                childName = null;
            }
        }, cancelled);
        return new Page(children, next[0], counters);
    }

    /** @return the element's bytes decoded, if it is at most {@code maxBytes} long */
    public String text(Node node, int maxBytes) throws IOException {
        long length = node.end() - node.start();
        if (length > maxBytes) {
            throw new IOException("The element has " + length + " bytes; at most " + maxBytes + " can be opened.");
        }
        return new String(index.read(node.start(), (int) length), charset);
    }

    private static String cut(String value) {
        return value.length() > MAX_VALUE ? value.substring(0, MAX_VALUE) + "…" : value;
    }

    private static Map<String, String> fields(String attributes) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (attributes == null || attributes.isBlank()) {
            return fields;
        }
        Matcher matcher = ATTRIBUTE.matcher(attributes);
        while (matcher.find() && fields.size() < MAX_FIELDS) {
            String name = matcher.group(1);
            if (name.equals("xmlns") || name.startsWith("xmlns:")) {
                continue;
            }
            String value = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            fields.put("@" + name, cut(unescape(value)));
        }
        return fields;
    }

    static String unescape(String value) {
        if (value.indexOf('&') < 0) {
            return value;
        }
        return value.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
                .replace("&amp;", "&");
    }

    // ---- the scanner --------------------------------------------------------------------------

    /** Callbacks of the scanner; returning {@code false} stops it. */
    private interface Listener {
        boolean start(String name, long offset, String attributes, boolean selfClosing, long afterTag);

        default void text(String text) {
        }

        default boolean end(String name, long afterTag) {
            return true;
        }
    }

    /** A byte-level XML tokenizer from an offset, tracking the element depth. */
    private final class Scanner {
        private final long from;
        private final LongConsumer progress;
        int depth;
        int depthBefore;

        Scanner(long from, int depth, LongConsumer progress) {
            this.from = from;
            this.depth = depth;
            this.progress = progress;
        }

        void run(Listener listener, BooleanSupplier cancelled) throws IOException {
            try (FileChannel channel = FileChannel.open(index.path(), StandardOpenOption.READ)) {
                Reader in = new Reader(channel, from);
                java.io.ByteArrayOutputStream text = new java.io.ByteArrayOutputStream();
                long nextCheck = from + BUFFER;
                int b;
                while ((b = in.next()) >= 0) {
                    if (b != '<') {
                        if (text.size() < 4 * MAX_VALUE) {
                            text.write(b);
                        }
                        continue;
                    }
                    long tagStart = in.position() - 1;
                    if (tagStart >= nextCheck) {
                        nextCheck = tagStart + BUFFER;
                        if (cancelled != null && cancelled.getAsBoolean()) {
                            throw new CancellationException("Cancelled");
                        }
                        if (progress != null) {
                            progress.accept(tagStart);
                        }
                    }
                    if (text.size() > 0) {
                        listener.text(unescape(text.toString(charset)));
                        text.reset();
                    }
                    int c = in.next();
                    if (c == '/') {
                        String name = readName(in, -1);
                        skipTo(in, '>');
                        depthBefore = depth;
                        depth--;
                        if (!listener.end(name, in.position())) {
                            return;
                        }
                    } else if (c == '!') {
                        int d = in.next();
                        if (d == '-') {
                            in.next();
                            skipPast(in, "-->");
                        } else if (d == '[') {
                            skipPast(in, "CDATA[");
                            java.io.ByteArrayOutputStream cdata = new java.io.ByteArrayOutputStream();
                            readUntil(in, "]]>", cdata);
                            listener.text(cdata.toString(charset));
                        } else {
                            skipDoctype(in);
                        }
                    } else if (c == '?') {
                        skipPast(in, "?>");
                    } else if (c >= 0) {
                        String name = readName(in, c);
                        java.io.ByteArrayOutputStream attributes = new java.io.ByteArrayOutputStream();
                        boolean selfClosing = readTagRest(in, attributes);
                        depthBefore = depth;
                        if (!selfClosing) {
                            depth++;
                        }
                        if (!listener.start(name, tagStart, attributes.toString(charset), selfClosing, in.position())) {
                            return;
                        }
                    }
                }
            }
        }

        private String readName(Reader in, int first) throws IOException {
            java.io.ByteArrayOutputStream name = new java.io.ByteArrayOutputStream();
            int c = first;
            if (c < 0) {
                c = in.next();
            }
            while (c >= 0 && c != '>' && c != '/' && c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                name.write(c);
                c = in.next();
            }
            if (c >= 0) {
                in.back();
            }
            return name.toString(charset);
        }

        /** Reads the attributes up to {@code >}; @return whether the tag ends with {@code />} */
        private boolean readTagRest(Reader in, java.io.ByteArrayOutputStream attributes) throws IOException {
            int quote = 0;
            int previous = 0;
            int c;
            while ((c = in.next()) >= 0) {
                if (quote != 0) {
                    if (c == quote) {
                        quote = 0;
                    }
                } else if (c == '"' || c == '\'') {
                    quote = c;
                } else if (c == '>') {
                    return previous == '/';
                }
                if (attributes.size() < 8 * MAX_VALUE) {
                    attributes.write(c);
                }
                if (!Character.isWhitespace(c)) {
                    previous = c;
                }
            }
            return false;
        }

        private void skipTo(Reader in, int stop) throws IOException {
            int c = in.next();
            while (c >= 0 && c != stop) {
                c = in.next();
            }
        }

        private void skipPast(Reader in, String end) throws IOException {
            readUntil(in, end, null);
        }

        private void readUntil(Reader in, String end, java.io.ByteArrayOutputStream into) throws IOException {
            byte[] pattern = end.getBytes(charset);
            int matched = 0;
            int c;
            while ((c = in.next()) >= 0) {
                if (c == pattern[matched]) {
                    matched++;
                    if (matched == pattern.length) {
                        return;
                    }
                } else {
                    if (into != null) {
                        for (int k = 0; k < matched; k++) {
                            into.write(pattern[k]);
                        }
                    }
                    matched = c == pattern[0] ? 1 : 0;
                    if (matched == 0 && into != null && into.size() < 4 * MAX_VALUE) {
                        into.write(c);
                    }
                }
            }
        }

        /** Skips {@code <!DOCTYPE …>} with an internal subset in brackets. */
        private void skipDoctype(Reader in) throws IOException {
            int brackets = 0;
            int quote = 0;
            int c;
            while ((c = in.next()) >= 0) {
                if (quote != 0) {
                    if (c == quote) {
                        quote = 0;
                    }
                } else if (c == '"' || c == '\'') {
                    quote = c;
                } else if (c == '[') {
                    brackets++;
                } else if (c == ']') {
                    brackets--;
                } else if (c == '>' && brackets <= 0) {
                    return;
                }
            }
        }
    }

    /** Buffered byte reader over a channel from an offset, with one byte of push-back. */
    private static final class Reader {
        private final FileChannel channel;
        private final ByteBuffer buffer = ByteBuffer.allocate(BUFFER);
        private long bufferStart;
        private int limit;
        private int at;

        Reader(FileChannel channel, long from) {
            this.channel = channel;
            this.bufferStart = from;
        }

        int next() throws IOException {
            if (at >= limit) {
                bufferStart += limit;
                buffer.clear();
                int read = channel.read(buffer, bufferStart);
                if (read <= 0) {
                    limit = 0;
                    at = 0;
                    return -1;
                }
                limit = read;
                at = 0;
            }
            return buffer.get(at++) & 0xFF;
        }

        void back() {
            at--;
        }

        /** @return the offset of the next byte */
        long position() {
            return bufferStart + at;
        }
    }
}
