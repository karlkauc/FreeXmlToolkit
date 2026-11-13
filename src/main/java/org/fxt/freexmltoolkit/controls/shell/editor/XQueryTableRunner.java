package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.transform.stream.StreamSource;

import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine;
import org.fxt.freexmltoolkit.service.standards.XPathVersion;

import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XQueryEvaluator;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XdmSequenceIterator;
import net.sf.saxon.s9api.XdmValue;

/**
 * UI-free execution of an XQuery (or an XPath expression) into a tabular model for the shell's XQuery console. A sequence of
 * element items becomes rows with columns taken from their child elements (or, if there are none,
 * their attributes); a sequence of atomic values becomes a single {@code "value"} column. The column
 * shape is decided from the first item. Reuses the shared Saxon {@link Processor}.
 */
public final class XQueryTableRunner {

    private XQueryTableRunner() {
    }

    /** A tabular XQuery result: column headers + string rows, or an {@code error}. */
    public record XQueryTable(List<String> columns, List<List<String>> rows, String error) {
        public boolean isError() {
            return error != null;
        }

        public boolean isEmpty() {
            return rows == null || rows.isEmpty();
        }

        static XQueryTable error(String message) {
            return new XQueryTable(List.of(), List.of(), message == null ? "error" : message);
        }
    }

    private enum Mode {
        CHILD, ATTR, VALUE
    }

    /**
     * Executes {@code xquery} against {@code xml} and projects the result sequence into a table.
     *
     * @return the tabular result, or a table carrying an {@code error}
     */
    public static XQueryTable run(String xml, String xquery) {
        return run(xml, xquery, null);
    }

    /**
     * As {@link #run(String, String)} with {@code baseUri} (the document's {@code file:} URI, or {@code null})
     * as the query's static base URI and the document's base URI.
     */
    public static XQueryTable run(String xml, String xquery, String baseUri) {
        if (xml == null || xml.isBlank()) {
            return XQueryTable.error(I18n.t("XQueryTableRunner.no-xml-to-query"));
        }
        if (xquery == null || xquery.isBlank()) {
            return XQueryTable.error(I18n.t("XQueryTableRunner.no-xquery-expression"));
        }
        try {
            Processor processor = XsltTransformationEngine.getInstance().getSaxonProcessor();
            XdmNode context = processor.newDocumentBuilder().build(new StreamSource(new StringReader(xml), baseUri));
            var compiler = processor.newXQueryCompiler();
            if (baseUri != null) {
                compiler.setBaseURI(java.net.URI.create(baseUri));
            }
            XQueryEvaluator evaluator = compiler.compile(xquery).load();
            evaluator.setContextItem(context);
            XdmValue result = evaluator.evaluate();
            return toTable(result);
        } catch (Exception e) {
            return XQueryTable.error(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    /**
     * Evaluates the XPath {@code expression} against {@code xml} and projects the result like {@link #run}. The
     * expression is compiled at the {@code version}'s level, as the console runs it (no namespace prefixes of the
     * document declared); XPath 1.0, which the console runs on the JDK engine, is evaluated by Saxon in 1.0
     * compatibility mode, so the rows are the nodes the run found.
     *
     * @return the tabular result, or a table carrying an {@code error}
     */
    public static XQueryTable xpath(String xml, String expression, XPathVersion version) {
        if (xml == null || xml.isBlank()) {
            return XQueryTable.error(I18n.t("XQueryTableRunner.no-xml-to-query"));
        }
        if (expression == null || expression.isBlank()) {
            return XQueryTable.error(I18n.t("XQueryTableRunner.no-xquery-expression"));
        }
        XPathVersion level = version == null ? XPathVersion.V3_1 : version;
        try {
            Processor processor = XsltTransformationEngine.getInstance().getSaxonProcessor();
            XdmNode context = processor.newDocumentBuilder().build(new StreamSource(new StringReader(xml)));
            XPathCompiler compiler = processor.newXPathCompiler();
            if (level != XPathVersion.V3_1) {
                compiler.setLanguageVersion(level.saxonLanguageVersion());
            }
            if (level == XPathVersion.V1_0) {
                compiler.setBackwardsCompatible(true);
            }
            if (level == XPathVersion.V3_0 || level == XPathVersion.V3_1) {
                compiler.declareNamespace("map", "http://www.w3.org/2005/xpath-functions/map");
                compiler.declareNamespace("array", "http://www.w3.org/2005/xpath-functions/array");
                compiler.declareNamespace("math", "http://www.w3.org/2005/xpath-functions/math");
            }
            return toTable(compiler.evaluate(expression, context));
        } catch (Exception e) {
            return XQueryTable.error(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private static XQueryTable toTable(XdmValue result) {
        List<XdmItem> items = new ArrayList<>();
        for (XdmItem item : result) {
            items.add(item);
        }
        if (items.isEmpty()) {
            return new XQueryTable(List.of("value"), List.of(), null);
        }

        Mode mode;
        List<String> columns;
        if (items.get(0) instanceof XdmNode node && node.getNodeKind() == XdmNodeKind.ELEMENT) {
            List<String> childCols = childElementNames(node);
            if (!childCols.isEmpty()) {
                mode = Mode.CHILD;
                columns = childCols;
            } else {
                List<String> attrCols = attributeNames(node);
                if (!attrCols.isEmpty()) {
                    mode = Mode.ATTR;
                    columns = attrCols;
                } else {
                    mode = Mode.VALUE;
                    columns = List.of("value");
                }
            }
        } else {
            mode = Mode.VALUE;
            columns = List.of("value");
        }

        List<List<String>> rows = new ArrayList<>();
        for (XdmItem item : items) {
            rows.add(rowFor(item, mode, columns));
        }
        return new XQueryTable(columns, rows, null);
    }

    private static List<String> childElementNames(XdmNode element) {
        List<String> names = new ArrayList<>();
        for (XdmNode child : element.children()) {
            if (child.getNodeKind() == XdmNodeKind.ELEMENT && !names.contains(child.getNodeName().getLocalName())) {
                names.add(child.getNodeName().getLocalName());
            }
        }
        return names;
    }

    private static List<String> attributeNames(XdmNode element) {
        List<String> names = new ArrayList<>();
        XdmSequenceIterator<XdmNode> attrs = element.axisIterator(Axis.ATTRIBUTE);
        while (attrs.hasNext()) {
            names.add(attrs.next().getNodeName().getLocalName());
        }
        return names;
    }

    private static List<String> rowFor(XdmItem item, Mode mode, List<String> columns) {
        if (mode == Mode.VALUE || !(item instanceof XdmNode node) || node.getNodeKind() != XdmNodeKind.ELEMENT) {
            return List.of(item.getStringValue());
        }
        Map<String, String> values = new LinkedHashMap<>();
        if (mode == Mode.CHILD) {
            for (XdmNode child : node.children()) {
                if (child.getNodeKind() == XdmNodeKind.ELEMENT) {
                    values.putIfAbsent(child.getNodeName().getLocalName(), child.getStringValue());
                }
            }
        } else {
            XdmSequenceIterator<XdmNode> attrs = node.axisIterator(Axis.ATTRIBUTE);
            while (attrs.hasNext()) {
                XdmNode attr = attrs.next();
                values.put(attr.getNodeName().getLocalName(), attr.getStringValue());
            }
        }
        List<String> row = new ArrayList<>();
        for (String column : columns) {
            row.add(values.getOrDefault(column, ""));
        }
        return row;
    }
}
