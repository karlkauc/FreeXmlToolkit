package org.fxt.freexmltoolkit.service.ai.context;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.fxt.freexmltoolkit.util.SecureXmlFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * The shape of an XML document read in one streaming pass (StAX, the application's secure factory — no DOM, so
 * a document of hundreds of MB is outlined in bounded memory): root, namespace declarations, declared schema,
 * size, element count, depth and the distinct element paths ({@code /a/b}, no positions) with their count,
 * attribute names and — optionally — one sample value.
 *
 * <p>With {@code samples == false} no text or attribute value of the document appears in the result, only names
 * (the {@code STRUCTURE} data level of Phase 2 depends on it). The declared schema location is the exception: it
 * names a file, it is no content.
 */
public final class XmlOutline {

    /** Characters of a sample value. */
    static final int SAMPLE_LENGTH = 80;
    /** Attribute names kept per path. */
    static final int MAX_ATTRIBUTES = 20;

    private static final String XSI = "http://www.w3.org/2001/XMLSchema-instance";

    /**
     * @param path       element names from the root, prefixed as in the document
     * @param count      how often the path occurs
     * @param attributes the attribute names seen on it, in first-seen order (at most {@value #MAX_ATTRIBUTES})
     * @param sample     the first non-blank text directly inside it, trimmed and cut, or {@code null}
     */
    public record PathInfo(String path, long count, List<String> attributes, String sample) {
    }

    /**
     * @param declaredSchema {@code xsi:schemaLocation} or {@code xsi:noNamespaceSchemaLocation} of the root, or null
     * @param truncated      more distinct paths exist than were kept
     */
    public record Outline(String root, Map<String, String> namespaces, String declaredSchema, long sizeBytes,
                          long elementCount, int maxDepth, List<PathInfo> paths, boolean truncated, boolean samples) {

        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("root", root);
            JsonObject ns = new JsonObject();
            namespaces.forEach(ns::addProperty);
            json.add("namespaces", ns);
            json.addProperty("declared_schema", declaredSchema);
            json.addProperty("size_bytes", sizeBytes);
            json.addProperty("element_count", elementCount);
            json.addProperty("max_depth", maxDepth);
            JsonArray array = new JsonArray();
            for (PathInfo info : paths) {
                JsonObject p = new JsonObject();
                p.addProperty("path", info.path());
                p.addProperty("count", info.count());
                JsonArray attributes = new JsonArray();
                info.attributes().forEach(attributes::add);
                p.add("attributes", attributes);
                if (samples) {
                    p.addProperty("sample", info.sample());
                }
                array.add(p);
            }
            json.add("paths", array);
            json.addProperty("truncated", truncated);
            return json;
        }

        /** One line per path: {@code /a/b ×12 @id @type = "sample"}. */
        public String toText() {
            StringBuilder text = new StringBuilder();
            text.append("root ").append(root).append(", ").append(elementCount).append(" elements, depth ")
                    .append(maxDepth).append(", ").append(sizeBytes).append(" bytes");
            if (declaredSchema != null) {
                text.append(", schema ").append(declaredSchema);
            }
            namespaces.forEach((prefix, uri) -> text.append("\nxmlns").append(prefix.isEmpty() ? "" : ":" + prefix)
                    .append(" = ").append(uri));
            for (PathInfo info : paths) {
                text.append('\n').append(info.path()).append(" ×").append(info.count());
                info.attributes().forEach(a -> text.append(" @").append(a));
                if (samples && info.sample() != null) {
                    text.append(" = \"").append(info.sample()).append('"');
                }
            }
            if (truncated) {
                text.append("\n… more paths");
            }
            return text.toString();
        }
    }

    private XmlOutline() {
    }

    /** Outlines a file, streamed from disk (the parser detects the encoding from the bytes). */
    public static Outline of(Path file, int maxPaths, boolean samples) throws IOException, XMLStreamException {
        try (InputStream in = Files.newInputStream(file)) {
            XMLStreamReader reader = SecureXmlFactory.createSecureXMLInputFactory().createXMLStreamReader(in);
            return read(reader, Files.size(file), maxPaths, samples);
        }
    }

    /** Outlines text already in memory. */
    public static Outline of(Reader text, long sizeBytes, int maxPaths, boolean samples) throws XMLStreamException {
        XMLStreamReader reader = SecureXmlFactory.createSecureXMLInputFactory().createXMLStreamReader(text);
        return read(reader, sizeBytes, maxPaths, samples);
    }

    private static final class Accumulator {
        long count;
        final Set<String> attributes = new LinkedHashSet<>();
        String sample;
    }

    private static Outline read(XMLStreamReader reader, long sizeBytes, int maxPaths, boolean samples)
            throws XMLStreamException {
        Map<String, Accumulator> paths = new LinkedHashMap<>();
        Map<String, String> namespaces = new LinkedHashMap<>();
        List<String> stack = new ArrayList<>();
        List<StringBuilder> texts = new ArrayList<>();
        String root = null;
        String declaredSchema = null;
        long elements = 0;
        int maxDepth = 0;
        boolean truncated = false;
        try {
            while (reader.hasNext()) {
                switch (reader.next()) {
                    case XMLStreamConstants.START_ELEMENT -> {
                        elements++;
                        String name = qualified(reader.getPrefix(), reader.getLocalName());
                        String parent = stack.isEmpty() ? "" : stack.getLast();
                        String path = parent + "/" + name;
                        stack.add(path);
                        texts.add(samples ? new StringBuilder() : null);
                        maxDepth = Math.max(maxDepth, stack.size());
                        for (int i = 0; i < reader.getNamespaceCount(); i++) {
                            String prefix = reader.getNamespacePrefix(i);
                            namespaces.putIfAbsent(prefix == null ? "" : prefix, reader.getNamespaceURI(i));
                        }
                        if (root == null) {
                            root = name;
                            declaredSchema = schemaLocation(reader);
                        }
                        Accumulator acc = paths.get(path);
                        if (acc == null) {
                            if (paths.size() >= maxPaths) {
                                truncated = true;
                                continue;
                            }
                            acc = new Accumulator();
                            paths.put(path, acc);
                        }
                        acc.count++;
                        for (int i = 0; i < reader.getAttributeCount() && acc.attributes.size() < MAX_ATTRIBUTES; i++) {
                            acc.attributes.add(qualified(reader.getAttributePrefix(i), reader.getAttributeLocalName(i)));
                        }
                    }
                    case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> {
                        if (samples && !texts.isEmpty()) {
                            StringBuilder text = texts.getLast();
                            if (text != null && text.length() <= SAMPLE_LENGTH * 4) {
                                text.append(reader.getText());
                            }
                        }
                    }
                    case XMLStreamConstants.END_ELEMENT -> {
                        String path = stack.removeLast();
                        StringBuilder text = texts.removeLast();
                        Accumulator acc = paths.get(path);
                        if (samples && acc != null && acc.sample == null && text != null) {
                            String value = text.toString().strip().replaceAll("\\s+", " ");
                            if (!value.isEmpty()) {
                                acc.sample = value.length() > SAMPLE_LENGTH
                                        ? value.substring(0, SAMPLE_LENGTH) + "…" : value;
                            }
                        }
                    }
                    default -> {
                        // comments, processing instructions, DTD
                    }
                }
            }
        } finally {
            reader.close();
        }
        List<PathInfo> infos = new ArrayList<>();
        paths.forEach((path, acc) -> infos.add(new PathInfo(path, acc.count, List.copyOf(acc.attributes),
                samples ? acc.sample : null)));
        return new Outline(root, namespaces, declaredSchema, sizeBytes, elements, maxDepth, infos, truncated, samples);
    }

    private static String schemaLocation(XMLStreamReader reader) {
        String location = reader.getAttributeValue(XSI, "schemaLocation");
        return location != null ? location.strip().replaceAll("\\s+", " ") : reader.getAttributeValue(XSI, "noNamespaceSchemaLocation");
    }

    private static String qualified(String prefix, String local) {
        return prefix == null || prefix.isEmpty() ? local : prefix + ":" + local;
    }
}
