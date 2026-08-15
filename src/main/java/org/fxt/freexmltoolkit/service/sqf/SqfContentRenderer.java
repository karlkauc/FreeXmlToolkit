package org.fxt.freexmltoolkit.service.sqf;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.transform.stream.StreamSource;

import org.fxt.freexmltoolkit.service.SaxonXPathHelper;

import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmSequenceIterator;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.s9api.Xslt30Transformer;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;

/**
 * Renders the dynamic content of an SQF activity by generating a tiny XSLT 3.0
 * stylesheet on the fly: the activity's content is copied verbatim into a named
 * template (so embedded {@code xsl:value-of}, {@code xsl:choose}, AVTs … are
 * executed natively by Saxon), {@code sch:ns} declarations become namespace
 * declarations, and pre-evaluated {@code sch:let} / user-entry values are passed
 * as stylesheet parameters. {@code sqf:copy-of} and {@code sqf:keep} are rewritten
 * to {@code xsl:copy-of}. The template runs with the target node as context item
 * and its serialized result (no added indentation, no XML declaration) is the
 * replacement fragment.
 */
final class SqfContentRenderer {

    private static final String TEMPLATE_NAME = "fxt-sqf-content";

    private SqfContentRenderer() {
    }

    /**
     * Renders the content of an add/replace activity.
     *
     * @param activity   the activity element (its children are the content template)
     * @param select     the activity's {@code @select} XPath, or {@code null}
     * @param context    the node the content evaluates against
     * @param params     pre-evaluated variable bindings (lets, user entries, params)
     * @param namespaces {@code sch:ns} prefix → URI declarations
     * @return the serialized result fragment
     */
    static String render(XdmNode activity, String select, XdmNode context,
                         Map<QName, XdmValue> params, Map<String, String> namespaces)
            throws SqfExecutionException {
        String body = select != null && !select.isBlank()
                ? "<xsl:copy-of select=\"" + escapeAttribute(select) + "\"/>"
                : serializedChildren(activity);
        return runTemplate(body, context, params, namespaces);
    }

    /**
     * Renders the new value of a text node processed by {@code sqf:stringReplace}:
     * the whole text value with every regex match replaced by the rendered content
     * (regex groups are available via {@code regex-group()}).
     *
     * @param context the matched text node
     */
    static String renderStringReplace(XdmNode activity, String select, String regex, String flags,
                                      XdmNode context, Map<QName, XdmValue> params,
                                      Map<String, String> namespaces) throws SqfExecutionException {
        String replacement = select != null && !select.isBlank()
                ? "<xsl:copy-of select=\"" + escapeAttribute(select) + "\"/>"
                : serializedChildren(activity);
        String body = "<xsl:analyze-string select=\"string(.)\" regex=\"" + escapeAttribute(regex) + "\""
                + (flags != null && !flags.isBlank() ? " flags=\"" + escapeAttribute(flags) + "\"" : "")
                + ">"
                + "<xsl:matching-substring>" + replacement + "</xsl:matching-substring>"
                + "<xsl:non-matching-substring><xsl:value-of select=\".\"/></xsl:non-matching-substring>"
                + "</xsl:analyze-string>";
        return runTemplate(body, context, params, namespaces);
    }

    private static String runTemplate(String body, XdmNode context,
                                      Map<QName, XdmValue> params, Map<String, String> namespaces)
            throws SqfExecutionException {
        StringBuilder sheet = new StringBuilder();
        sheet.append("<xsl:stylesheet version=\"3.0\" ")
                .append("xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"");
        for (Map.Entry<String, String> ns : namespaces.entrySet()) {
            String prefix = ns.getKey();
            if (prefix == null || prefix.isBlank() || "xsl".equals(prefix) || "xml".equals(prefix)) {
                continue; // never override the stylesheet's own bindings or the default ns
            }
            sheet.append(" xmlns:").append(prefix).append("=\"")
                    .append(escapeAttribute(ns.getValue())).append('"');
        }
        sheet.append(">");
        for (QName param : params.keySet()) {
            sheet.append("<xsl:param name=\"").append(param.getLocalName()).append("\"/>");
        }
        sheet.append("<xsl:template name=\"").append(TEMPLATE_NAME).append("\">")
                .append(body)
                .append("</xsl:template></xsl:stylesheet>");

        try {
            Processor processor = SaxonXPathHelper.getProcessor();
            XsltCompiler compiler = processor.newXsltCompiler();
            XsltExecutable executable = compiler.compile(new StreamSource(new StringReader(sheet.toString())));
            Xslt30Transformer transformer = executable.load30();
            if (!params.isEmpty()) {
                transformer.setStylesheetParameters(new LinkedHashMap<>(params));
            }
            transformer.setGlobalContextItem(context);
            StringWriter out = new StringWriter();
            Serializer serializer = processor.newSerializer(out);
            serializer.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, "yes");
            serializer.setOutputProperty(Serializer.Property.INDENT, "no");
            transformer.callTemplate(new QName(TEMPLATE_NAME), serializer);
            return out.toString();
        } catch (Exception e) {
            throw new SqfExecutionException("Cannot render fix content: " + e.getMessage(), e);
        }
    }

    /** Serializes the activity's child nodes verbatim, rewriting sqf:copy-of / sqf:keep. */
    private static String serializedChildren(XdmNode activity) throws SqfExecutionException {
        try {
            Processor processor = SaxonXPathHelper.getProcessor();
            StringBuilder out = new StringBuilder();
            for (XdmSequenceIterator<XdmNode> it = activity.axisIterator(Axis.CHILD); it.hasNext(); ) {
                XdmNode child = it.next();
                StringWriter part = new StringWriter();
                Serializer serializer = processor.newSerializer(part);
                serializer.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, "yes");
                serializer.setOutputProperty(Serializer.Property.INDENT, "no");
                serializer.serializeNode(child);
                out.append(part);
            }
            return rewriteSqfInstructions(out.toString(), sqfPrefixes(activity));
        } catch (Exception e) {
            throw new SqfExecutionException("Cannot serialize fix content: " + e.getMessage(), e);
        }
    }

    /** @return the prefixes bound to the SQF namespace in the activity's scope */
    private static List<String> sqfPrefixes(XdmNode activity) {
        List<String> prefixes = new ArrayList<>();
        for (XdmSequenceIterator<XdmNode> it = activity.axisIterator(Axis.NAMESPACE); it.hasNext(); ) {
            XdmNode ns = it.next();
            if (SqfNames.SQF_NS.equals(ns.getStringValue()) && ns.getNodeName() != null) {
                prefixes.add(ns.getNodeName().getLocalName());
            }
        }
        return prefixes;
    }

    /** Rewrites {@code sqf:copy-of} and {@code sqf:keep} content instructions to {@code xsl:copy-of}. */
    private static String rewriteSqfInstructions(String fragment, List<String> sqfPrefixes) {
        String result = fragment;
        for (String prefix : sqfPrefixes) {
            result = result
                    .replace("<" + prefix + ":keep/>", "<xsl:copy-of select=\"node()\"/>")
                    .replace("<" + prefix + ":keep />", "<xsl:copy-of select=\"node()\"/>")
                    .replace("<" + prefix + ":keep ", "<xsl:copy-of ")
                    .replace("</" + prefix + ":keep>", "</xsl:copy-of>")
                    .replace("<" + prefix + ":copy-of", "<xsl:copy-of")
                    .replace("</" + prefix + ":copy-of>", "</xsl:copy-of>");
        }
        return result;
    }

    private static String escapeAttribute(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;");
    }
}
