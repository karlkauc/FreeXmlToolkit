package org.fxt.freexmltoolkit.plugins.kagmeldewesen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.xml.transform.stream.StreamSource;

import org.fxt.freexmltoolkit.service.TemplateRepository;
import org.fxt.freexmltoolkit.service.XPathSnippetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XQueryEvaluator;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;

/** The KAG snippets run on the bundled examples; snippets and templates are registered with fixed ids. */
class KagExtrasTest {

    private static String example(String name) throws Exception {
        try (InputStream in = KagExtrasTest.class.getResourceAsStream("/plugins/kagmeldewesen/examples/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void everySnippetFindsSomethingInItsExample() throws Exception {
        Processor processor = new Processor(false);
        List<KagExtras.Snippet> snippets = KagExtras.snippets();
        assertEquals(8, snippets.size());
        for (KagExtras.Snippet s : snippets) {
            String file = s.key.startsWith("derimv") ? "derimv-example.xml" : s.key.startsWith("aif") ? "aif-example.xml" : "mmf-example.xml";
            XdmNode doc = processor.newDocumentBuilder().build(new StreamSource(new StringReader(example(file))));
            XQueryEvaluator eval = processor.newXQueryCompiler().compile(s.query).load();
            eval.setContextItem(doc);
            XdmValue result = eval.evaluate();
            // the example's absolute VaR fund is below 15 % and 90 % of its own limit; those queries only have to run
            if (!s.key.equals("derimv-avar-above") && !s.key.equals("derimv-own-limit")) {
                assertTrue(result.size() > 0, s.key + " finds something in " + file);
            }
        }
    }

    @Test
    void snippetsAndTemplatesAreRegisteredWithFixedIds(@TempDir Path tmp) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        XPathSnippetRepository snippets = XPathSnippetRepository.getInstance();
        assertEquals(8, KagExtras.registerSnippets(snippets));
        KagExtras.registerSnippets(snippets);
        assertEquals(8, snippets.getSnippetsByTag(KagExtras.TAG).size(), "registering again updates");

        var templateFiles = org.fxt.freexmltoolkit.service.TemplateFileService.getInstance();
        Path previous = templateFiles.getTemplatesDirectoryPath();
        templateFiles.setTemplatesDirectory(tmp.resolve("templates"));
        TemplateRepository templates = TemplateRepository.getInstance();
        try {
            assertEquals(4, KagExtras.registerTemplates(templates));
            assertTrue(templates.getTemplate("kag-meldewesen-template-derimv").getContent().contains("<Derivatemeldung"));
            assertTrue(Files.isRegularFile(tmp.resolve("templates").resolve("kag-meldewesen-template-aif.template")));
        } finally {
            for (String id : KagExtras.TEMPLATES.keySet()) {
                templates.removeTemplate("kag-meldewesen-template-" + id, false);
            }
            templateFiles.setTemplatesDirectory(previous);
        }
    }
}
