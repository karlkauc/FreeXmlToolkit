package org.fxt.freexmltoolkit.service;

import java.io.File;
import java.io.StringWriter;

import javax.xml.transform.dom.DOMSource;

import org.fxt.freexmltoolkit.debugger.SchematronSourceMap;
import org.w3c.dom.Document;

import com.helger.schematron.schxslt.xslt2.SchematronResourceSchXslt_XSLT2;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;

/**
 * Compiles a Schematron for the debugger: the XSLT that ph-schematron's SchXslt generates (the
 * same compiler the validator uses), serialized with indentation so every instruction has its
 * own line, plus the {@link SchematronSourceMap} that maps those lines back to the {@code .sch}.
 * The compiled stylesheet runs under the Schematron's own URI, so relative {@code doc()} calls
 * resolve as during validation.
 */
public final class SchematronDebugCompiler {

    /** The compiled stylesheet and how its lines map to the Schematron. */
    public record Compiled(String xslt, String systemId, SchematronSourceMap sourceMap) { }

    private SchematronDebugCompiler() {
    }

    /**
     * @param schematron the saved Schematron file (SchXslt resolves its includes from disk)
     * @throws IllegalArgumentException when the Schematron does not compile
     */
    public static Compiled compile(File schematron) {
        SchematronResourceSchXslt_XSLT2 resource = SchematronResourceSchXslt_XSLT2.builderFromFile(schematron)
                .useCache(false)
                // like the validator: schematrons without @queryBinding compile as xslt2
                .parameter("schxslt.compile.default-query-binding", "xslt2")
                .validateSVRL(false)
                .build();
        var provider = resource.getXSLTProvider();
        Document xsltDocument = provider == null ? null : provider.getXSLTDocument();
        if (xsltDocument == null) {
            throw new IllegalArgumentException("The Schematron " + schematron.getName()
                    + " does not compile - validate it to see the errors.");
        }
        Processor processor = XsltTransformationEngine.getInstance().getDebugProcessor();
        String systemId = schematron.toURI().toString();
        String xslt;
        try {
            StringWriter out = new StringWriter();
            Serializer serializer = processor.newSerializer(out);
            serializer.setOutputProperty(Serializer.Property.METHOD, "xml");
            serializer.setOutputProperty(Serializer.Property.INDENT, "yes");
            serializer.serializeNode(processor.newDocumentBuilder().build(new DOMSource(xsltDocument, systemId)));
            xslt = out.toString();
        } catch (SaxonApiException e) {
            throw new IllegalArgumentException("Cannot serialize the compiled Schematron: " + e.getMessage(), e);
        }
        return new Compiled(xslt, systemId, SchematronSourceMap.build(processor, xslt, systemId, systemId, null));
    }

    /**
     * The failed asserts and successful reports of a run's SVRL as Messages rows, each at the
     * {@code .sch} line of its check (found by its test text): a failed assert as WARNING, a
     * report as INFO.
     */
    public static java.util.List<XsltTransformationResult.TransformationMessage> findings(
            String svrl, SchematronSourceMap map) {
        java.util.List<XsltTransformationResult.TransformationMessage> rows = new java.util.ArrayList<>();
        if (svrl == null || svrl.isBlank()) {
            return rows;
        }
        try {
            net.sf.saxon.s9api.XdmNode doc = XsltTransformationEngine.getInstance().getDebugProcessor()
                    .newDocumentBuilder().build(new javax.xml.transform.stream.StreamSource(new java.io.StringReader(svrl)));
            doc.select(net.sf.saxon.s9api.streams.Steps.descendant()).forEach(node -> {
                if (node.getNodeKind() != net.sf.saxon.s9api.XdmNodeKind.ELEMENT
                        || !SVRL.equals(node.getNodeName().getNamespace())) {
                    return;
                }
                String local = node.getNodeName().getLocalName();
                boolean failed = "failed-assert".equals(local);
                if (!failed && !"successful-report".equals(local)) {
                    return;
                }
                String test = node.getAttributeValue(new net.sf.saxon.s9api.QName("test"));
                String location = node.getAttributeValue(new net.sf.saxon.s9api.QName("location"));
                String text = node.select(net.sf.saxon.s9api.streams.Steps.child(SVRL, "text")).findFirst()
                        .map(t -> t.getStringValue().strip().replaceAll("\\s+", " ")).orElse("");
                SchematronSourceMap.SchItem check = map == null ? null : map.findTest(test);
                int line = check == null ? -1 : check.line();
                String where = check == null ? "" : org.fxt.freexmltoolkit.debugger.SourceLocations
                        .fileName(check.systemId()) + ":" + line;
                rows.add(new XsltTransformationResult.TransformationMessage(failed ? "WARNING" : "INFO",
                        (failed ? "Failed assert: " : "Report: ") + text
                                + (location == null || location.isEmpty() ? "" : " - " + location.replace("Q{}", "")),
                        line, where));
            });
        } catch (SaxonApiException | RuntimeException e) {
            rows.clear();
        }
        return rows;
    }

    private static final String SVRL = "http://purl.oclc.org/dsdl/svrl";
}
