package org.fxt.freexmltoolkit.service;

import com.xmlcalabash.XmlCalabash;
import com.xmlcalabash.datamodel.DeclareStepInstruction;
import com.xmlcalabash.documents.XProcDocument;
import com.xmlcalabash.io.DocumentWriter;
import com.xmlcalabash.parsers.xpl.XplParser;
import com.xmlcalabash.runtime.XProcPipeline;
import com.xmlcalabash.util.BufferingReceiver;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.XdmNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the exact XML Calabash 3.x API call sequence used by
 * {@code XProcRunner}. Upstream declares the Calabash API unstable, so any
 * incompatible change in a future upgrade must fail here first, in a plain
 * JUnit test, rather than deep inside the editor integration.
 *
 * <p>API facts verified against the 3.0.51 sources and asserted here:</p>
 * <ul>
 *   <li>{@code XmlCalabash.Companion.newInstance()} is the entry point from Java
 *       (Kotlin companion object). Instances are cheap enough to create per run;
 *       thread-safety of a shared instance is not documented, so the runner
 *       creates a fresh one per pipeline execution.</li>
 *   <li>{@code XplParser.parse(Source)} accepts a {@link StreamSource} whose
 *       system id supplies the static base URI — this lets the runner execute
 *       unsaved editor text while relative {@code href}s still resolve.</li>
 *   <li>{@code XProcPipeline.input(port, XProcDocument)} binds an input port;
 *       {@code XProcDocument.Companion.ofXml(node, pipeline.getConfig())} builds
 *       the document ({@code StepConfiguration} implements {@code DocumentContext}).</li>
 *   <li>Outputs arrive through a {@link BufferingReceiver}; each
 *       {@link XProcDocument} exposes {@code getContentType()} (a Calabash
 *       {@code MediaType}) used for output-format routing, and
 *       {@link DocumentWriter} serializes honoring the pipeline's declared
 *       serialization properties.</li>
 * </ul>
 */
class XmlCalabashSmokeTest {

    private static final String IDENTITY_WITH_SOURCE_PORT = """
            <p:declare-step xmlns:p="http://www.w3.org/ns/xproc" version="3.0">
              <p:input port="source"/>
              <p:output port="result"/>
              <p:identity/>
            </p:declare-step>
            """;

    @TempDir
    Path tempDir;

    @Test
    void identityPipelineWithDocumentHrefRuns() throws Exception {
        Files.writeString(tempDir.resolve("data.xml"), "<fund><name>Equity One</name></fund>");
        Path pipeline = tempDir.resolve("pipeline.xpl");
        Files.writeString(pipeline, """
                <p:declare-step xmlns:p="http://www.w3.org/ns/xproc" version="3.0">
                  <p:output port="result"/>
                  <p:identity>
                    <p:with-input><p:document href="data.xml"/></p:with-input>
                  </p:identity>
                </p:declare-step>
                """);

        XmlCalabash calabash = XmlCalabash.Companion.newInstance();
        XplParser parser = calabash.newXProcParser();
        DeclareStepInstruction decl = parser.parse(pipeline.toUri());
        XProcPipeline exec = decl.getExecutable();
        BufferingReceiver receiver = new BufferingReceiver();
        exec.setReceiver(receiver);
        exec.run();

        String result = serialize(receiver.getOutputs().get("result"));
        assertTrue(result.contains("Equity One"), "expected the input document to pass through, got: " + result);
    }

    @Test
    void identityPipelineWithExplicitlyBoundSourcePortRuns() throws Exception {
        XmlCalabash calabash = XmlCalabash.Companion.newInstance();
        XplParser parser = calabash.newXProcParser();
        StreamSource pipelineSource = new StreamSource(new StringReader(IDENTITY_WITH_SOURCE_PORT));
        pipelineSource.setSystemId(tempDir.resolve("inline.xpl").toUri().toString());
        DeclareStepInstruction decl = parser.parse(pipelineSource);
        XProcPipeline exec = decl.getExecutable();

        assertTrue(exec.getInputManifold().containsKey("source"),
                "the pipeline should declare a 'source' input port");

        DocumentBuilder builder = calabash.getSaxonConfiguration().getProcessor().newDocumentBuilder();
        StreamSource inputSource = new StreamSource(new StringReader("<order id=\"42\"><item>ACME</item></order>"));
        inputSource.setSystemId(tempDir.resolve("input.xml").toUri().toString());
        XdmNode inputNode = builder.build(inputSource);
        exec.input("source", XProcDocument.Companion.ofXml(inputNode, exec.getConfig()));

        BufferingReceiver receiver = new BufferingReceiver();
        exec.setReceiver(receiver);
        exec.run();

        List<XProcDocument> outputs = receiver.getOutputs().get("result");
        assertNotNull(outputs, "expected documents on the 'result' port, got ports: "
                + receiver.getOutputs().keySet());
        assertFalse(outputs.isEmpty());
        assertNotNull(outputs.getFirst().getContentType(), "output documents should carry a media type");
        String result = serialize(outputs);
        assertTrue(result.contains("ACME"), "expected the bound input to pass through, got: " + result);
    }

    @Test
    void staticErrorSurfacesAsCatchableException() {
        XmlCalabash calabash = XmlCalabash.Companion.newInstance();
        XplParser parser = calabash.newXProcParser();
        StreamSource broken = new StreamSource(new StringReader("""
                <p:declare-step xmlns:p="http://www.w3.org/ns/xproc" version="3.0">
                  <p:output port="result"/>
                  <p:no-such-step/>
                </p:declare-step>
                """));
        broken.setSystemId(tempDir.resolve("broken.xpl").toUri().toString());

        Exception ex = assertThrows(Exception.class, () -> parser.parse(broken).getExecutable().run());
        assertNotNull(ex.getMessage(), "the XProc error should carry a message");
    }

    private static String serialize(List<XProcDocument> documents) {
        assertNotNull(documents);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        for (XProcDocument doc : documents) {
            new DocumentWriter(doc, bos, Map.of()).write();
        }
        return bos.toString(StandardCharsets.UTF_8);
    }
}
