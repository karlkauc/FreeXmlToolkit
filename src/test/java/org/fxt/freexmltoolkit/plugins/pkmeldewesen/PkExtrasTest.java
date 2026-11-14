package org.fxt.freexmltoolkit.plugins.pkmeldewesen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import javax.xml.transform.stream.StreamSource;

import org.fxt.freexmltoolkit.service.FavoritesService;
import org.fxt.freexmltoolkit.service.TemplateRepository;
import org.fxt.freexmltoolkit.service.XPathSnippetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XQueryEvaluator;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;

/** Snippets, templates and favorites the extension adds; every snippet runs on the example report. */
class PkExtrasTest {

    private static String example() throws Exception {
        try (InputStream in = PkExtrasTest.class.getResourceAsStream(PkMeldewesenPanel.EXAMPLE)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void everySnippetRunsOnTheExample() throws Exception {
        Processor processor = new Processor(false);
        XdmNode doc = processor.newDocumentBuilder().build(new StreamSource(new StringReader(example())));
        var snippets = PkExtras.snippets();
        assertEquals(6, snippets.size());
        for (PkExtras.Snippet s : snippets) {
            XQueryEvaluator eval = processor.newXQueryCompiler().compile(s.query).load();
            eval.setContextItem(doc);
            XdmValue result = eval.evaluate();
            // the example has no derivatives and no look-through (PK.006.03); those queries only have to run
            if (!s.key.equals("derivatives") && !s.key.equals("look-through-ratio")) {
                assertTrue(result.size() > 0, s.key + " finds something in the example");
            }
        }
    }

    @Test
    void extrasAreRegisteredOnceWithFixedIds(@TempDir Path tmp) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        XPathSnippetRepository snippets = XPathSnippetRepository.getInstance();
        assertEquals(6, PkExtras.registerSnippets(snippets));
        PkExtras.registerSnippets(snippets);
        assertEquals(6, snippets.getSnippetsByTag(PkExtras.TAG).size(), "registering again updates");
        assertTrue(snippets.getSnippet("pk-meldewesen-top-positions").getQuery().contains("C1100PK"));

        TemplateRepository templates = TemplateRepository.getInstance();
        assertEquals(3, PkExtras.registerTemplates(templates));
        String pfq = templates.getTemplate("pk-meldewesen-template-pfq").getContent();
        assertTrue(pfq.contains("<Nationale_Meldung_pfq") && pfq.contains("<RowCode>R000-120</RowCode>"), pfq);
        assertTrue(templates.getTemplate("pk-meldewesen-template-pfe").getContent().contains("Stichtag=\"JAHR\""));

        FavoritesService.resetInstanceForTests();
        FavoritesService favorites = FavoritesService.getInstance();
        PkTaxonomy taxonomy = new PkTaxonomy(tmp);
        taxonomy.ensureBundled();
        int before = favorites.getFavoritesByFolder(PkExtras.CATEGORY).size();
        int added = PkExtras.registerFavorites(favorites, taxonomy);
        assertEquals(PkExtras.registerFavorites(favorites, taxonomy), 0, "no duplicates");
        assertEquals(before + added, favorites.getFavoritesByFolder(PkExtras.CATEGORY).size());
        assertTrue(favorites.getFavoritesByFolder(PkExtras.CATEGORY).stream()
                .anyMatch(f -> f.getFilePath().endsWith("pk_meldung_pfq.xsd")));
    }
}
