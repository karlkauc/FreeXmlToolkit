package org.fxt.freexmltoolkit.service.largefile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The element structure of a large XML file, read on demand: a byte-level scanner (no DOM, no
 * full parse) lists the child elements of one element at a time, a page at a time, with their
 * exact byte ranges. Children of an element with millions of children are listed page by page; a
 * page continues where the previous one stopped ({@link Cursor}). Not a validating parser: it relies
 * on the document being well-formed and reads comments, CDATA, processing instructions and the
 * DOCTYPE only to skip them.
 */
public final class LargeXmlStructure {

    /** Values (attributes, leaf texts) longer than this are cut. */
    static final int MAX_VALUE = 200;
    /** At most this many fields per element. */
    static final int MAX_FIELDS = 60;
    private static final int BUFFER = 1 << 20;
    private static final Pattern ATTRIBUTE = Pattern.compile("([^\\s=/>]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')");
    private static final Pattern CHAR_REF = Pattern.compile("&#(x[0-9a-fA-F]+|[0-9]+);");
    private static final Pattern STEP = Pattern.compile("([^\\[\\]/]+)(?:\\[(\\d+)])?");

    /**
     * An element.
     *
     * @param name         the tag name (with prefix)
     * @param start        byte offset of its {@code <}
     * @param contentStart byte offset after its start tag's {@code >} (its end for an empty element)
     * @param end          byte offset after its end tag (or after {@code />})
     * @param position     1-based position among the same-named siblings
     * @param index        0-based position among all element siblings
     * @param xpath        its path from the root, e.g. {@code /orders[1]/order[42]}
     * @param hasChildren  whether it contains elements
     * @param text         its text when it contains no elements (cut), else empty
     * @param fields       its attributes ({@code @name}) and the texts of its child elements without
     *                     children of their own (by name; the first of each), in document order
     */
    public record Node(String name, long start, long contentStart, long end, int position, long index, String xpath,
                       boolean hasChildren, String text, Map<String, String> fields) {
    }

    /**
     * Where a listing of children continues.
     *
     * @param offset    the byte offset of the first child to list
     * @param positions the same-name counters of the children before it (their sum is the number of
     *                  children before it)
     */
    public record Cursor(long offset, Map<String, Integer> positions) {
        public Cursor {
            positions = Map.copyOf(positions);
        }

        /** @return the number of children before the cursor */
        public long index() {
            long sum = 0;
            for (int count : positions.values()) {
                sum += count;
            }
            return sum;
        }
    }

    /**
     * A page of children.
     *
     * @param children  the children found
     * @param next      where the next page starts (inside the parent), or -1 when all were listed
     * @param positions the same-name counters after this page (for the positions of the next page)
     */
    public record Page(List<Node> children, long next, Map<String, Integer> positions) {
        /** @return the cursor of the next page, or {@code null} when all children were listed */
        public Cursor nextCursor() {
            return next < 0 ? null : new Cursor(next, positions);
        }
    }

    /**
     * The page starts of one element's children.
     *
     * @param pages the cursor of every page ({@code pages.get(p)} starts with child {@code p * pageSize})
     * @param total the number of children
     */
    public record PageIndex(List<Cursor> pages, long total, int pageSize) {
        /** @return the cursor of the page that contains child {@code index}, or {@code null} */
        public Cursor pageOf(long index) {
            if (index < 0 || index >= total) {
                return null;
            }
            return pages.get((int) (index / pageSize));
        }
    }

    /**
     * One step of a path from the root to an element.
     *
     * @param name     the tag name
     * @param position 1-based position among the same-named siblings
     * @param index    0-based position among all element siblings
     * @param start    byte offset of its {@code <}
     * @param page     the cursor of the page (of the given page size) that lists it among its siblings;
     *                 {@code null} for the root
     */
    public record Step(String name, int position, long index, long start, Cursor page) {
    }

    private final LargeFileIndex index;
    private final Charset charset;
    private final boolean utf8;

    public LargeXmlStructure(LargeFileIndex index) {
        this.index = index;
        this.charset = index.charset();
        this.utf8 = StandardCharsets.UTF_8.equals(charset);
    }

    /** @return the file this structure reads */
    public LargeFileIndex index() {
        return index;
    }

    /** @return the root element */
    public Node root() throws IOException {
        Scanner scanner = new Scanner(index.bomLength(), 0, null, MAX_VALUE);
        Node[] found = new Node[1];
        scanner.run(new Listener() {
            @Override
            public boolean start(String name, long offset, Tag tag, boolean selfClosing, long afterTag) {
                long end = selfClosing ? afterTag : rootEnd(name);
                found[0] = new Node(name, offset, afterTag, end, 1, 0, "/" + name + "[1]",
                        !selfClosing, "", fields(tag.text(), MAX_VALUE));
                return false;
            }
        }, () -> false);
        if (found[0] == null) {
            throw new IOException("No root element found: the file is not XML.");
        }
        return found[0];
    }

    /** The offset after the root's end tag, read from the end of the file (the file size when not found). */
    private long rootEnd(String name) {
        try {
            long size = index.size();
            int tail = (int) Math.min(size, BUFFER);
            byte[] bytes = index.read(size - tail, tail);
            byte[] endTag = ("</" + name).getBytes(charset);
            for (int at = bytes.length - endTag.length; at >= 0; at--) {
                if (matches(bytes, at, endTag)) {
                    int after = at + endTag.length;
                    while (after < bytes.length && isSpace(bytes[after])) {
                        after++;
                    }
                    if (after < bytes.length && bytes[after] == '>') {
                        return size - tail + after + 1;
                    }
                }
            }
            return size;
        } catch (IOException e) {
            return index.size();
        }
    }

    private static boolean matches(byte[] bytes, int at, byte[] pattern) {
        for (int k = 0; k < pattern.length; k++) {
            if (bytes[at + k] != pattern[k]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSpace(int c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
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
        List<Node> children = new ArrayList<>();
        ScanResult result = scanChildren(parent, from, positions, max, MAX_VALUE, true, children::add, cancelled, progress);
        return new Page(children, result.next(), result.positions());
    }

    /** Lists a page of {@code parent}'s children from a cursor ({@code null} = the first page). */
    public Page children(Node parent, Cursor from, int max, BooleanSupplier cancelled, LongConsumer progress)
            throws IOException {
        return from == null ? children(parent, parent.start(), null, max, cancelled, progress)
                : children(parent, from.offset(), from.positions(), max, cancelled, progress);
    }

    /**
     * Streams all children of {@code parent} from a cursor with values cut at {@code valueLimit}
     * characters (instead of {@link #MAX_VALUE}).
     *
     * @param sink receives each child; returning {@code false} stops the listing
     */
    public void forEachChild(Node parent, Cursor from, int valueLimit, Predicate<Node> sink, BooleanSupplier cancelled,
                             LongConsumer progress) throws IOException {
        long offset = from == null ? parent.start() : from.offset();
        scanChildren(parent, offset, from == null ? null : from.positions(), Long.MAX_VALUE, valueLimit, true, sink,
                cancelled, progress);
    }

    /** Counts the children of {@code parent} and remembers where every page of {@code pageSize} starts. */
    public PageIndex pageIndex(Node parent, int pageSize, BooleanSupplier cancelled, LongConsumer progress)
            throws IOException {
        List<Cursor> pages = new ArrayList<>();
        Map<String, Integer> counters = new HashMap<>();
        long[] count = {0};
        scanChildren(parent, parent.start(), null, Long.MAX_VALUE, MAX_VALUE, false, child -> {
            if (count[0] % pageSize == 0) {
                pages.add(new Cursor(child.start(), counters));
            }
            counters.merge(child.name(), 1, Integer::sum);
            count[0]++;
            return true;
        }, cancelled, progress);
        return new PageIndex(List.copyOf(pages), count[0], pageSize);
    }

    /** Where a child listing stopped. */
    private record ScanResult(long next, Map<String, Integer> positions) {
    }

    /**
     * The child listing: depth 1 = inside the parent; a child's start tag takes the depth to 2.
     *
     * @param withFields whether attributes and leaf texts are read (else fields and text are empty)
     */
    private ScanResult scanChildren(Node parent, long from, Map<String, Integer> positions, long max, int valueLimit,
                                    boolean withFields, Predicate<Node> sink, BooleanSupplier cancelled,
                                    LongConsumer progress) throws IOException {
        boolean first = from <= parent.start();
        Map<String, Integer> counters = new HashMap<>(positions == null ? Map.of() : positions);
        long[] next = {-1};
        long[] listed = {0};
        long before = new Cursor(from, counters).index();
        Scanner scanner = new Scanner(first ? parent.start() : from, first ? 0 : 1, progress, valueLimit);
        scanner.run(new Listener() {
            // the child being read and what is known about it
            String childName;
            long childStart;
            long childContentStart;
            String childAttributes;
            boolean childHasChildren;
            StringBuilder childText;
            Map<String, String> leafTexts;
            String leafName;
            boolean leafHasChildren;
            StringBuilder leafText;
            boolean stopped;

            @Override
            public boolean wantsText(int depth) {
                return withFields && (depth == 2 || depth == 3);
            }

            @Override
            public boolean start(String name, long offset, Tag tag, boolean selfClosing, long afterTag) {
                // the depth the element itself occupies (a self-closing tag does not change the scanner's depth)
                int depth = selfClosing ? scanner.depth + 1 : scanner.depth;
                if (depth == 2) {
                    if (listed[0] >= max) {
                        next[0] = offset;
                        return false;
                    }
                    childName = name;
                    childStart = offset;
                    childContentStart = afterTag;
                    childAttributes = withFields ? tag.text() : null;
                    childHasChildren = false;
                    childText = new StringBuilder();
                    leafTexts = new LinkedHashMap<>();
                    if (selfClosing) {
                        finishChild(afterTag);
                        return !stopped;
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
                if (depth == 2 && childText != null && !childHasChildren && childText.length() < valueLimit) {
                    childText.append(text);
                } else if (depth == 3 && leafText != null && !leafHasChildren && leafText.length() < valueLimit) {
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
                    return !stopped;
                } else if (depthBefore == 1) {
                    return false;               // the parent ends: all children listed
                }
                return true;
            }

            private void finishLeaf() {
                if (withFields) {
                    String value = leafText.toString().strip();
                    if (!leafHasChildren && !value.isEmpty() && leafTexts.size() < MAX_FIELDS) {
                        leafTexts.putIfAbsent(leafName, cut(value, valueLimit));
                    }
                }
                leafName = null;
            }

            private void finishChild(long end) {
                int position = counters.merge(childName, 1, Integer::sum);
                Map<String, String> fields = withFields ? fields(childAttributes, valueLimit) : Map.of();
                if (withFields) {
                    leafTexts.forEach((k, v) -> {
                        if (fields.size() < MAX_FIELDS) {
                            fields.putIfAbsent(k, v);
                        }
                    });
                }
                String text = childHasChildren || !withFields ? "" : cut(childText.toString().strip(), valueLimit);
                Node child = new Node(childName, childStart, Math.min(childContentStart, end), end, position, before + listed[0],
                        parent.xpath() + "/" + childName + "[" + position + "]", childHasChildren, text, fields);
                listed[0]++;
                childName = null;
                if (!sink.test(child)) {
                    stopped = true;
                }
            }
        }, cancelled);
        return new ScanResult(next[0], Map.copyOf(counters));
    }

    /**
     * The path from the root to the innermost element that contains {@code offset} (one scan from the
     * start of the file up to the offset).
     *
     * @param pageSize the page size of {@link Step#page()}
     * @return the steps from the root down; empty when the offset is outside the root element
     */
    public List<Step> pathAt(long offset, int pageSize, BooleanSupplier cancelled, LongConsumer progress)
            throws IOException {
        Deque<Frame> stack = new ArrayDeque<>();
        Frame document = new Frame(null, null);
        stack.push(document);
        Scanner scanner = new Scanner(index.bomLength(), 0, progress, MAX_VALUE);
        List<Step> found = new ArrayList<>();
        scanner.run(new Listener() {
            @Override
            public boolean start(String name, long tagStart, Tag tag, boolean selfClosing, long afterTag) {
                if (tagStart > offset) {
                    return false;
                }
                Frame parent = stack.peek();
                Frame frame = parent.child(name, tagStart, pageSize);
                if (selfClosing) {
                    if (offset < afterTag) {
                        stack.push(frame);
                        return false;
                    }
                } else {
                    stack.push(frame);
                }
                return true;
            }

            @Override
            public boolean end(String name, long afterTag) {
                if (afterTag > offset) {
                    return false;               // the offset is inside this end tag: the element contains it
                }
                if (stack.size() > 1) {
                    stack.pop();
                }
                return stack.size() > 1 || document.children == 0;
            }
        }, cancelled);
        List<Frame> frames = new ArrayList<>(stack);
        java.util.Collections.reverse(frames);
        for (Frame frame : frames) {
            if (frame.step != null) {
                found.add(frame.step);
            }
        }
        return found;
    }

    /** An open element during {@link #pathAt}: its step and the counters of its children. */
    private static final class Frame {
        final Step step;
        final Map<String, Integer> counters = new HashMap<>();
        long children;
        Cursor page;

        Frame(Step step, Cursor page) {
            this.step = step;
            this.page = page;
        }

        Frame child(String name, long start, int pageSize) {
            boolean root = step == null;
            if (!root && children % pageSize == 0) {
                page = new Cursor(start, counters);
            }
            int position = counters.merge(name, 1, Integer::sum);
            Step child = new Step(name, position, children, start, root ? null : page);
            children++;
            return new Frame(child, null);
        }
    }

    /**
     * Resolves a path of the form {@code /a[1]/b[42]/c}: each step is a name (with prefix as in the
     * file) or {@code *}, optionally with a 1-based position (default 1; with {@code *} the position
     * among all element siblings). A leading {@code /} is optional.
     *
     * @return the steps from the root down
     * @throws IOException with a message naming the first step that matches nothing
     */
    public List<Step> resolve(String path, int pageSize, BooleanSupplier cancelled, LongConsumer progress)
            throws IOException {
        String text = path == null ? "" : path.strip();
        while (text.startsWith("/")) {
            text = text.substring(1);
        }
        if (text.isEmpty()) {
            throw new IOException("Enter a path such as /orders/order[42].");
        }
        String[] parts = text.split("/");
        List<Step> steps = new ArrayList<>();
        Node current = root();
        for (int i = 0; i < parts.length; i++) {
            Matcher matcher = STEP.matcher(parts[i].strip());
            if (!matcher.matches()) {
                throw new IOException("Step " + (i + 1) + " (\"" + parts[i] + "\") is not a name with an optional [position].");
            }
            String name = matcher.group(1);
            long wanted = matcher.group(2) == null ? 1 : Long.parseLong(matcher.group(2));
            if (wanted < 1) {
                throw new IOException("Positions start at 1: \"" + parts[i] + "\".");
            }
            if (i == 0) {
                if (!(name.equals("*") || name.equals(current.name())) || wanted != 1) {
                    throw new IOException("The root element is " + current.name() + ", not " + parts[i] + ".");
                }
                steps.add(new Step(current.name(), 1, 0, current.start(), null));
                continue;
            }
            Node parent = current;
            Node[] match = new Node[1];
            Cursor[] page = new Cursor[1];
            Map<String, Integer> counters = new HashMap<>();
            scanChildren(parent, parent.start(), null, Long.MAX_VALUE, MAX_VALUE, false, child -> {
                if (child.index() % pageSize == 0) {
                    page[0] = new Cursor(child.start(), counters);
                }
                counters.merge(child.name(), 1, Integer::sum);
                boolean hit = name.equals("*") ? child.index() + 1 == wanted
                        : child.name().equals(name) && child.position() == wanted;
                if (hit) {
                    match[0] = child;
                    return false;
                }
                return true;
            }, cancelled, progress);
            if (match[0] == null) {
                throw new IOException(parent.xpath() + " has no child " + name + "[" + wanted + "].");
            }
            steps.add(new Step(match[0].name(), match[0].position(), match[0].index(), match[0].start(), page[0]));
            current = match[0];
        }
        return steps;
    }

    /**
     * The offset of the first element whose names from the root are {@code namePath} (e.g.
     * {@code /orders/order/item}, no positions), or -1.
     */
    public long firstOffsetOf(String namePath, BooleanSupplier cancelled, LongConsumer progress) throws IOException {
        String[] wanted = namePath.replaceFirst("^/+", "").split("/");
        Deque<String> stack = new ArrayDeque<>();
        long[] found = {-1};
        new Scanner(index.bomLength(), 0, progress, MAX_VALUE).run(new Listener() {
            /** how many of the open elements match the path so far (a prefix match only) */
            int matching;

            @Override
            public boolean start(String name, long offset, Tag tag, boolean selfClosing, long afterTag) {
                int depth = stack.size();
                boolean match = depth == matching && depth < wanted.length && wanted[depth].equals(name);
                if (match && depth + 1 == wanted.length) {
                    found[0] = offset;
                    return false;
                }
                if (!selfClosing) {
                    stack.push(name);
                    if (match) {
                        matching++;
                    }
                }
                return true;
            }

            @Override
            public boolean end(String name, long afterTag) {
                if (stack.isEmpty()) {
                    return false;
                }
                stack.pop();
                if (matching > stack.size()) {
                    matching = stack.size();
                }
                return !stack.isEmpty();
            }
        }, cancelled);
        return found[0];
    }

    /** Reads one child at a cursor with its fields (the element a {@link Step} names, given its parent). */
    public Node childAt(Node parent, Step step) throws IOException {
        Cursor page = step.page();
        Page listed = children(parent, page, (int) (step.index() - (page == null ? 0 : page.index())) + 1, null, null);
        for (Node child : listed.children()) {
            if (child.start() == step.start()) {
                return child;
            }
        }
        throw new IOException("Cannot read " + step.name() + "[" + step.position() + "] of " + parent.xpath() + ".");
    }

    /** @return the element's bytes decoded, if it is at most {@code maxBytes} long */
    public String text(Node node, int maxBytes) throws IOException {
        return text(node.start(), node.end(), maxBytes);
    }

    /** @return the bytes of a range decoded, if it is at most {@code maxBytes} long */
    public String text(long start, long end, int maxBytes) throws IOException {
        long length = end - start;
        if (length > maxBytes) {
            throw new IOException("The element has " + length + " bytes; at most " + maxBytes + " can be opened.");
        }
        return new String(index.read(start, (int) length), charset);
    }

    private static String cut(String value, int limit) {
        return value.length() > limit ? value.substring(0, limit) + "…" : value;
    }

    /** @return the attributes of a start tag's text after the name, as {@code @name} → value */
    static Map<String, String> fields(String attributes, int valueLimit) {
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
            fields.put("@" + name, cut(unescape(value), valueLimit));
        }
        return fields;
    }

    /** Decodes the predefined entities and character references ({@code &#233;}, {@code &#xE9;}). */
    static String unescape(String value) {
        if (value.indexOf('&') < 0) {
            return value;
        }
        String result = value;
        if (result.contains("&#")) {
            Matcher matcher = CHAR_REF.matcher(result);
            StringBuilder decoded = new StringBuilder();
            while (matcher.find()) {
                String ref = matcher.group(1);
                String replacement = matcher.group();
                try {
                    int codePoint = ref.charAt(0) == 'x' ? Integer.parseInt(ref.substring(1), 16) : Integer.parseInt(ref);
                    if (Character.isValidCodePoint(codePoint)) {
                        replacement = Character.toString(codePoint);
                    }
                } catch (NumberFormatException e) {
                    // too large: left as written
                }
                matcher.appendReplacement(decoded, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(decoded);
            result = decoded.toString();
        }
        return result.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
                .replace("&amp;", "&");
    }

    /** Runs the scanner over the whole file (statistics). */
    void scanAll(Listener listener, BooleanSupplier cancelled, LongConsumer progress) throws IOException {
        new Scanner(index.bomLength(), 0, progress, MAX_VALUE).runWithDepth(listener, cancelled);
    }

    // ---- the scanner --------------------------------------------------------------------------

    /** The attributes of the start tag just read, decoded on demand. */
    interface Tag {
        /** @return the text after the element name up to (not including) {@code >} or {@code />} */
        String text();
    }

    /** Callbacks of the scanner; returning {@code false} stops it. */
    interface Listener {
        boolean start(String name, long offset, Tag tag, boolean selfClosing, long afterTag);

        /** @return whether the text at this element depth is wanted (else it is skipped undecoded) */
        default boolean wantsText(int depth) {
            return false;
        }

        default void text(String text) {
        }

        default boolean end(String name, long afterTag) {
            return true;
        }

        /** Told the depth after every tag when run by {@link #scanAll}. */
        default void depth(int depth) {
        }
    }

    /** A growable byte buffer that keeps at most {@code cap} bytes (the rest is dropped). */
    private final class Bytes {
        private byte[] data = new byte[256];
        private int length;
        private final int cap;

        Bytes(int cap) {
            this.cap = cap;
        }

        void add(int b) {
            if (length >= cap) {
                return;
            }
            if (length == data.length) {
                data = java.util.Arrays.copyOf(data, Math.min(cap, data.length * 2));
            }
            data[length++] = (byte) b;
        }

        boolean isEmpty() {
            return length == 0;
        }

        void reset() {
            length = 0;
        }

        /** Decodes the bytes; a UTF-8 sequence cut at the end is dropped instead of shown as U+FFFD. */
        String decode() {
            int end = length;
            if (utf8 && end > 0) {
                end = completeUtf8(data, end);
            }
            return new String(data, 0, end, charset);
        }
    }

    /** @return the length without a trailing, incomplete UTF-8 sequence */
    static int completeUtf8(byte[] data, int length) {
        int lead = length - 1;
        int continuation = 0;
        while (lead >= 0 && continuation < 3 && (data[lead] & 0xC0) == 0x80) {
            lead--;
            continuation++;
        }
        if (lead < 0) {
            return length;
        }
        int b = data[lead] & 0xFF;
        int needed = b < 0x80 ? 0 : b >= 0xF0 ? 3 : b >= 0xE0 ? 2 : b >= 0xC0 ? 1 : 0;
        return continuation < needed ? lead : length;
    }

    /** A byte-level XML tokenizer from an offset, tracking the element depth. */
    private final class Scanner {
        private final long from;
        private final LongConsumer progress;
        private final Bytes name = new Bytes(4096);
        private final Bytes attributes;
        private final Bytes text;
        int depth;
        int depthBefore;

        Scanner(long from, int depth, LongConsumer progress, int valueLimit) {
            this.from = from;
            this.depth = depth;
            this.progress = progress;
            int limit = Math.min(Integer.MAX_VALUE / 16, Math.max(valueLimit, MAX_VALUE));
            this.attributes = new Bytes(8 * limit);
            this.text = new Bytes(4 * limit);
        }

        void run(Listener listener, BooleanSupplier cancelled) throws IOException {
            scan(listener, cancelled, false);
        }

        void runWithDepth(Listener listener, BooleanSupplier cancelled) throws IOException {
            scan(listener, cancelled, true);
        }

        private void scan(Listener listener, BooleanSupplier cancelled, boolean reportDepth) throws IOException {
            String[] decodedAttributes = new String[1];
            Tag tag = () -> {
                if (decodedAttributes[0] == null) {
                    decodedAttributes[0] = attributes.decode();
                }
                return decodedAttributes[0];
            };
            try (FileChannel channel = FileChannel.open(index.path(), StandardOpenOption.READ)) {
                Reader in = new Reader(channel, from);
                long nextCheck = from + BUFFER;
                boolean collect = listener.wantsText(depth);
                int b;
                while ((b = in.next()) >= 0) {
                    if (b != '<') {
                        if (collect) {
                            text.add(b);
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
                    int c = in.next();
                    if (c == '!') {
                        int d = in.next();
                        if (d == '-') {
                            in.next();
                            skipPast(in, "-->");
                        } else if (d == '[') {
                            skipPast(in, "CDATA[");
                            if (collect) {
                                readUntil(in, "]]>", text);   // CDATA joins the surrounding text
                            } else {
                                skipPast(in, "]]>");
                            }
                        } else {
                            skipDoctype(in);
                        }
                        continue;
                    }
                    if (collect && !text.isEmpty()) {
                        listener.text(unescape(text.decode()));
                    }
                    text.reset();
                    if (c == '/') {
                        String endName = readName(in, -1);
                        skipTo(in, '>');
                        depthBefore = depth;
                        depth--;
                        if (!listener.end(endName, in.position())) {
                            return;
                        }
                    } else if (c == '?') {
                        skipPast(in, "?>");
                    } else if (c >= 0) {
                        String startName = readName(in, c);
                        attributes.reset();
                        decodedAttributes[0] = null;
                        boolean selfClosing = readTagRest(in);
                        depthBefore = depth;
                        if (!selfClosing) {
                            depth++;
                        }
                        if (!listener.start(startName, tagStart, tag, selfClosing, in.position())) {
                            return;
                        }
                    }
                    if (reportDepth) {
                        listener.depth(depth);
                    }
                    collect = listener.wantsText(depth);
                }
            }
        }

        private String readName(Reader in, int first) throws IOException {
            name.reset();
            int c = first;
            if (c < 0) {
                c = in.next();
            }
            while (c >= 0 && c != '>' && c != '/' && c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                name.add(c);
                c = in.next();
            }
            if (c >= 0) {
                in.back();
            }
            return name.decode();
        }

        /** Reads the attributes up to {@code >}; @return whether the tag ends with {@code />} */
        private boolean readTagRest(Reader in) throws IOException {
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
                attributes.add(c);
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

        private void readUntil(Reader in, String end, Bytes into) throws IOException {
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
                            into.add(pattern[k]);
                        }
                    }
                    matched = c == pattern[0] ? 1 : 0;
                    if (matched == 0 && into != null) {
                        into.add(c);
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
        private final byte[] bytes = buffer.array();
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
            return bytes[at++] & 0xFF;
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
