package org.fxt.freexmltoolkit.controls.shell.editor;

import com.xmlcalabash.XmlCalabash;
import com.xmlcalabash.datamodel.DeclareStepInstruction;
import com.xmlcalabash.documents.XProcDocument;
import com.xmlcalabash.exceptions.XProcException;
import com.xmlcalabash.io.DocumentWriter;
import com.xmlcalabash.runtime.XProcPipeline;
import com.xmlcalabash.runtime.api.RuntimePort;
import com.xmlcalabash.util.BufferingReceiver;
import net.sf.saxon.s9api.XdmNode;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine;

import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * UI-free XProc 3.0 pipeline execution via XML Calabash: runs a pipeline (the
 * active {@code .xpl} document's text) against an optional primary-input XML
 * document and returns the serialized result. Errors are returned as
 * {@code "ERROR: …"} text rather than thrown, so the OUTPUT panel can show them
 * directly — mirroring {@link TransformRunner}.
 *
 * <p>All XML Calabash API usage is confined to this class (plus the smoke test
 * pinning the call sequence, {@code XmlCalabashSmokeTest}) because upstream
 * declares the Calabash API unstable. A fresh {@link XmlCalabash} instance is
 * created per run — thread-safety of a shared instance is undocumented, and the
 * instantiation cost is acceptable for an explicit user action.</p>
 */
public final class XProcRunner {

    private XProcRunner() {
    }

    /** Serialized primary-output text (or {@code "ERROR: …"}) plus the detected output format. */
    public record Result(String text, XsltTransformationEngine.OutputFormat format) {
        public boolean isError() {
            return text.startsWith("ERROR:");
        }
    }

    /**
     * Runs an XProc 3.0 pipeline.
     *
     * <p>The pipeline's static base URI is the backing file's URI when available, so
     * relative {@code href}s ({@code p:document}, {@code p:xslt} stylesheets, Schematron
     * files) resolve against the pipeline's directory. Untitled pipelines resolve
     * relative hrefs against the working directory.</p>
     *
     * @param pipelineText the {@code .xpl} source (live editor text, possibly unsaved)
     * @param pipelineFile the backing file, or {@code null} for untitled documents
     * @param inputXmlText in-memory primary-input XML (open-document target), or {@code null}
     * @param inputXmlFile file-system primary-input target, or {@code null}; at most one
     *                     of the two is non-null — both {@code null} runs the pipeline
     *                     unbound (fine for self-contained pipelines)
     * @return the serialized primary output, or an {@code "ERROR: …"} result
     */
    public static Result runPipeline(String pipelineText, File pipelineFile,
                                     String inputXmlText, File inputXmlFile) {
        try {
            XmlCalabash calabash = XmlCalabash.Companion.newInstance();

            StreamSource pipelineSource = new StreamSource(new StringReader(pipelineText));
            pipelineSource.setSystemId(baseUriFor(pipelineFile));
            DeclareStepInstruction decl = calabash.newXProcParser().parse(pipelineSource);
            XProcPipeline exec = decl.getExecutable();

            RuntimePort inputPort = primaryInputPort(exec);
            if (inputPort != null) {
                if (inputXmlText != null || inputXmlFile != null) {
                    exec.input(inputPort.getName(), buildInputDocument(calabash, exec,
                            inputXmlText, inputXmlFile, pipelineFile));
                } else if (inputPort.getDefaultBindings().isEmpty()) {
                    return error("The pipeline declares an input port ('" + inputPort.getName()
                            + "') but no XML target is available — open an XML document or pick "
                            + "one via the Target dropdown.");
                }
            }

            BufferingReceiver receiver = new BufferingReceiver();
            exec.setReceiver(receiver);
            exec.run();

            List<XProcDocument> outputs = primaryOutputDocuments(exec, receiver);
            if (outputs.isEmpty()) {
                return new Result("(the pipeline produced no output documents)",
                        XsltTransformationEngine.OutputFormat.TEXT);
            }
            return new Result(serialize(outputs), detectFormat(outputs.getFirst()));
        } catch (XProcException e) {
            return error(xprocMessage(e));
        } catch (Throwable t) {
            String message = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            return error(message);
        }
    }

    private static Result error(String message) {
        return new Result("ERROR: " + message, XsltTransformationEngine.OutputFormat.TEXT);
    }

    /** Formats an {@link XProcException} as {@code "[err:XC0050] message"}. */
    private static String xprocMessage(XProcException e) {
        String code = e.getError().getCode().toString();
        String message = e.getMessage() != null ? e.getMessage() : "XProc error";
        return message.contains(code) ? message : "[" + code + "] " + message;
    }

    private static String baseUriFor(File pipelineFile) {
        File base = pipelineFile != null
                ? pipelineFile.getAbsoluteFile()
                : new File(System.getProperty("user.dir"), "untitled.xpl").getAbsoluteFile();
        return base.toURI().toString();
    }

    /**
     * @return the pipeline's primary input port (explicit {@code primary="true"} or the
     *         single declared input), or {@code null} when the pipeline declares no inputs
     */
    private static RuntimePort primaryInputPort(XProcPipeline exec) {
        Map<String, RuntimePort> inputs = exec.getInputManifold();
        return inputs.values().stream().filter(RuntimePort::getPrimary).findFirst()
                .orElseGet(() -> inputs.size() == 1 ? inputs.values().iterator().next() : null);
    }

    private static XProcDocument buildInputDocument(XmlCalabash calabash, XProcPipeline exec,
                                                    String inputXmlText, File inputXmlFile,
                                                    File pipelineFile) throws Exception {
        StreamSource source;
        if (inputXmlFile != null) {
            source = new StreamSource(inputXmlFile);
        } else {
            source = new StreamSource(new StringReader(inputXmlText));
            File dir = pipelineFile != null ? pipelineFile.getAbsoluteFile().getParentFile()
                    : new File(System.getProperty("user.dir"));
            source.setSystemId(new File(dir, "input.xml").toURI().toString());
        }
        XdmNode node = calabash.getSaxonConfiguration().getProcessor()
                .newDocumentBuilder().build(source);
        return XProcDocument.Companion.ofXml(node, exec.getConfig());
    }

    /** @return the documents on the primary output port, or the first non-empty port. */
    private static List<XProcDocument> primaryOutputDocuments(XProcPipeline exec,
                                                              BufferingReceiver receiver) {
        Map<String, List<XProcDocument>> outputs = Map.copyOf(receiver.getOutputs());
        return exec.getOutputManifold().values().stream()
                .filter(RuntimePort::getPrimary)
                .map(port -> outputs.get(port.getName()))
                .filter(docs -> docs != null && !docs.isEmpty())
                .findFirst()
                .orElseGet(() -> outputs.values().stream()
                        .filter(docs -> !docs.isEmpty())
                        .findFirst().orElse(List.of()));
    }

    /** Serializes all documents honoring their declared serialization properties. */
    private static String serialize(List<XProcDocument> documents) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        boolean first = true;
        for (XProcDocument doc : documents) {
            if (!first) {
                bos.writeBytes("\n".getBytes(StandardCharsets.UTF_8));
            }
            new DocumentWriter(doc, bos, Map.of()).write();
            first = false;
        }
        return bos.toString(StandardCharsets.UTF_8);
    }

    /** Maps the output document's media type onto the OUTPUT panel's format toggle. */
    private static XsltTransformationEngine.OutputFormat detectFormat(XProcDocument doc) {
        if (doc.getContentType() == null) {
            return XsltTransformationEngine.OutputFormat.XML;
        }
        return switch (doc.getContentType().classification()) {
            case HTML -> XsltTransformationEngine.OutputFormat.HTML;
            case XHTML -> XsltTransformationEngine.OutputFormat.XHTML;
            case JSON -> XsltTransformationEngine.OutputFormat.JSON;
            case TEXT, YAML, TOML, BINARY -> XsltTransformationEngine.OutputFormat.TEXT;
            case XML -> XsltTransformationEngine.OutputFormat.XML;
        };
    }
}
