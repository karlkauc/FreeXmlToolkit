package org.fxt.freexmltoolkit.plugin;

import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.fxt.freexmltoolkit.util.SecureXmlFactory;

/**
 * Reads an XML document once into a flat list of its elements (path, text, line, attributes) — what
 * most plugin validators need to check values and sums. Securely configured (no DTDs, no external
 * entities).
 */
public final class XmlScan {

    /**
     * An element.
     *
     * @param path       the local names from the root, joined by {@code /} ({@code Document/CstmrCdtTrfInitn/GrpHdr})
     * @param localName  the local name
     * @param namespace  the namespace URI ({@code ""} for none)
     * @param text       its own character data, stripped (without that of child elements)
     * @param line       the line of its start tag
     * @param attributes local name → value
     * @param depth      0 for the root
     * @param index      its position in {@link #elements()}
     */
    public record Element(String path, String localName, String namespace, String text, int line,
                          Map<String, String> attributes, int depth, int index) {

        /** @return the parent's path, or {@code ""} for the root */
        public String parentPath() {
            int slash = path.lastIndexOf('/');
            return slash < 0 ? "" : path.substring(0, slash);
        }
    }

    private final List<Element> elements;

    private XmlScan(List<Element> elements) {
        this.elements = elements;
    }

    /**
     * @return the elements in document order
     * @throws XMLStreamException when the document is not well-formed
     */
    public static XmlScan of(String content) throws XMLStreamException {
        List<Element> elements = new ArrayList<>();
        XMLStreamReader reader = SecureXmlFactory.createSecureXMLInputFactory().createXMLStreamReader(new StringReader(content));
        try {
            Deque<Object[]> open = new ArrayDeque<>(); // [path, localName, ns, line, attrs, StringBuilder, index]
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String name = reader.getLocalName();
                    String path = open.isEmpty() ? name : open.peek()[0] + "/" + name;
                    Map<String, String> attributes = new LinkedHashMap<>();
                    for (int i = 0; i < reader.getAttributeCount(); i++) {
                        attributes.put(reader.getAttributeLocalName(i), reader.getAttributeValue(i));
                    }
                    elements.add(null); // placeholder, filled at the end tag (document order kept)
                    open.push(new Object[] {path, name, reader.getNamespaceURI() == null ? "" : reader.getNamespaceURI(),
                            reader.getLocation().getLineNumber(), attributes, new StringBuilder(), elements.size() - 1});
                } else if ((event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA)
                        && !open.isEmpty()) {
                    ((StringBuilder) open.peek()[5]).append(reader.getText());
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    Object[] e = open.pop();
                    @SuppressWarnings("unchecked")
                    Map<String, String> attributes = (Map<String, String>) e[4];
                    elements.set((Integer) e[6], new Element((String) e[0], (String) e[1], (String) e[2],
                            e[5].toString().strip(), (Integer) e[3], Map.copyOf(attributes), open.size(), (Integer) e[6]));
                }
            }
        } finally {
            reader.close();
        }
        return new XmlScan(List.copyOf(elements));
    }

    public List<Element> elements() {
        return elements;
    }

    /** @return the root element */
    public Optional<Element> root() {
        return elements.isEmpty() ? Optional.empty() : Optional.of(elements.getFirst());
    }

    /** @return the elements with this local name */
    public List<Element> named(String localName) {
        return elements.stream().filter(e -> e.localName().equals(localName)).toList();
    }

    /** @return the elements whose path ends with {@code suffix} ({@code GrpHdr/CtrlSum}) */
    public List<Element> endingWith(String suffix) {
        return elements.stream().filter(e -> e.path().equals(suffix) || e.path().endsWith("/" + suffix)).toList();
    }

    /** @return the elements below {@code ancestor} (its descendants) */
    public List<Element> within(Element ancestor) {
        int start = ancestor.index();
        List<Element> inside = new ArrayList<>();
        for (int i = start + 1; i < elements.size() && elements.get(i).depth() > ancestor.depth(); i++) {
            inside.add(elements.get(i));
        }
        return inside;
    }

    /**
     * Reads the root element's name and namespace cheaply (for {@code DocumentValidator.accepts}).
     *
     * @return {@code {localName, namespace}} or empty when there is none or the document is broken
     */
    public static Optional<String[]> rootOf(String content) {
        try {
            XMLStreamReader reader = SecureXmlFactory.createSecureXMLInputFactory()
                    .createXMLStreamReader(new StringReader(content));
            try {
                while (reader.hasNext()) {
                    if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                        return Optional.of(new String[] {reader.getLocalName(),
                                reader.getNamespaceURI() == null ? "" : reader.getNamespaceURI()});
                    }
                }
            } finally {
                reader.close();
            }
        } catch (XMLStreamException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }
}
