package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.transform.stream.StreamSource;

import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XQueryCompiler;
import net.sf.saxon.s9api.XQueryEvaluator;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XdmSequenceIterator;
import net.sf.saxon.s9api.XdmValue;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;

/**
 * Evaluates the XQuery 3.1 expressions of the grid's filters and formulas against the XML
 * model: the context element is serialized (with the namespace declarations in scope), parsed
 * by Saxon, the expression runs with that element as context item, and result nodes are mapped
 * back to the model by their element position below the context element.
 */
public final class XmlGridQuery {

    private static final Processor PROCESSOR = new Processor(false);

    private XmlGridQuery() {
    }

    /** The result of an evaluation: the model nodes selected, or the atomic text. */
    public record Result(Set<XmlNode> nodes, String text) {
    }

    /**
     * @param context    the context element (the filtered element / the formula's parent)
     * @param expression an XQuery 3.1 expression
     * @return the selected model nodes (attributes and text map to their element) and the
     *         result as text (atomic values joined by spaces, nodes by their string value)
     * @throws IllegalArgumentException with Saxon's message when the expression fails
     */
    public static Result evaluate(XmlElement context, String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("The expression is empty");
        }
        Map<String, String> namespaces = inScopeNamespaces(context);
        try {
            XdmNode root = parse(context, namespaces);
            XQueryCompiler compiler = PROCESSOR.newXQueryCompiler();
            StringBuilder prolog = new StringBuilder();
            namespaces.forEach((prefix, uri) -> {
                if (prefix.isEmpty()) {
                    prolog.append("declare default element namespace \"").append(uri.replace("\"", "\"\""))
                            .append("\";\n");
                } else {
                    compiler.declareNamespace(prefix, uri);
                }
            });
            XQueryEvaluator evaluator = compiler.compile(prolog + expression).load();
            XdmNode contextElement = firstElement(root);
            evaluator.setContextItem(contextElement);
            XdmValue value = evaluator.evaluate();

            Set<XmlNode> nodes = Collections.newSetFromMap(new IdentityHashMap<>());
            List<String> texts = new ArrayList<>();
            for (XdmItem item : value) {
                texts.add(item.getStringValue());
                if (item instanceof XdmNode node) {
                    XmlNode model = toModel(node, contextElement, context);
                    if (model != null) {
                        nodes.add(model);
                    }
                }
            }
            return new Result(nodes, String.join(" ", texts));
        } catch (SaxonApiException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    /** @return the namespace declarations in scope of the element (prefix → uri, "" = default) */
    static Map<String, String> inScopeNamespaces(XmlElement element) {
        Map<String, String> result = new LinkedHashMap<>();
        XmlNode current = element;
        while (current instanceof XmlElement el) {
            for (Map.Entry<String, String> attr : el.getAttributes().entrySet()) {
                String name = attr.getKey();
                if (name.equals("xmlns")) {
                    result.putIfAbsent("", attr.getValue());
                } else if (name.startsWith("xmlns:")) {
                    result.putIfAbsent(name.substring(6), attr.getValue());
                }
            }
            if (el.getNamespacePrefix() != null && el.getNamespaceURI() != null) {
                result.putIfAbsent(el.getNamespacePrefix(), el.getNamespaceURI());
            }
            current = el.getParent();
        }
        return result;
    }

    private static XdmNode parse(XmlElement context, Map<String, String> namespaces) throws SaxonApiException {
        XmlElement copy = (XmlElement) context.deepCopy("");
        namespaces.forEach((prefix, uri) -> {
            String attr = prefix.isEmpty() ? "xmlns" : "xmlns:" + prefix;
            if (!copy.hasAttribute(attr)) {
                copy.setAttribute(attr, uri);
            }
        });
        DocumentBuilder builder = PROCESSOR.newDocumentBuilder();
        return builder.build(new StreamSource(new StringReader(copy.serialize(0))));
    }

    private static XdmNode firstElement(XdmNode document) {
        XdmSequenceIterator<XdmNode> it = document.axisIterator(Axis.CHILD);
        while (it.hasNext()) {
            XdmNode child = it.next();
            if (child.getNodeKind() == XdmNodeKind.ELEMENT) {
                return child;
            }
        }
        return document;
    }

    /** Maps a Saxon node back to the model by element positions below the context element. */
    private static XmlNode toModel(XdmNode node, XdmNode contextElement, XmlElement context) {
        XdmNode element = node;
        while (element != null && element.getNodeKind() != XdmNodeKind.ELEMENT) {
            element = element.getParent(); // attributes, text, comments → their element
        }
        List<Integer> path = new ArrayList<>();
        XdmNode current = element;
        while (current != null && !current.equals(contextElement)) {
            int index = 0;
            XdmSequenceIterator<XdmNode> preceding = current.axisIterator(Axis.PRECEDING_SIBLING);
            while (preceding.hasNext()) {
                if (preceding.next().getNodeKind() == XdmNodeKind.ELEMENT) {
                    index++;
                }
            }
            path.add(0, index);
            current = current.getParent();
        }
        if (current == null) {
            return null; // outside the context element (e.g. a constructed node)
        }
        XmlElement model = context;
        for (int index : path) {
            List<XmlElement> children = model.getChildElements();
            if (index >= children.size()) {
                return null;
            }
            model = children.get(index);
        }
        return model;
    }
}
