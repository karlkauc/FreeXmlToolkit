package org.fxt.freexmltoolkit.service.grammar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.fxt.freexmltoolkit.controls.shell.editor.ValidationProblem;
import org.fxt.freexmltoolkit.controls.shell.editor.ValidationRunner;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** DTD and RELAX NG: declared grammars, validation (and its security limits), translation to XSD. */
@DisplayName("DTD and RELAX NG")
class GrammarTest {

    static final String DTD = """
            <!ELEMENT book (title, chapter+)>
            <!ATTLIST book id ID #REQUIRED lang (en|de) "en">
            <!ELEMENT title (#PCDATA)>
            <!ELEMENT chapter (#PCDATA)>
            """;
    static final String RNG = """
            <grammar xmlns="http://relaxng.org/ns/structure/1.0">
              <start><element name="book">
                <attribute name="id"/>
                <element name="title"><text/></element>
                <oneOrMore><element name="chapter"><text/></element></oneOrMore>
              </element></start>
            </grammar>
            """;
    static final String RNC = """
            start = element book { attribute id { text }, element title { text }, element chapter { text }+ }
            """;

    @TempDir
    Path dir;

    @BeforeAll
    static void services() {
        ServiceRegistry.initialize();
    }

    @Test
    @DisplayName("declared grammars: DOCTYPE SYSTEM / PUBLIC / internal subset, xml-model for RELAX NG")
    void declared() {
        var system = DeclaredGrammar.of("<?xml version='1.0'?>\n<!-- c -->\n<!DOCTYPE book SYSTEM \"book.dtd\">\n<book/>").orElseThrow();
        assertEquals(new DeclaredGrammar(GrammarKind.DTD, "book.dtd", null, "book", false), system);
        var pub = DeclaredGrammar.of("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\"><html/>").orElseThrow();
        assertEquals("-//W3C//DTD XHTML 1.0 Strict//EN", pub.publicId());
        assertEquals("http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd", pub.systemId());
        var internal = DeclaredGrammar.of("<!DOCTYPE a [<!ELEMENT a (#PCDATA)>]><a/>").orElseThrow();
        assertTrue(internal.internalSubset());
        assertEquals(GrammarKind.RELAX_NG, DeclaredGrammar.of("<?xml-model href=\"b.rng\" schematypens=\"http://relaxng.org/ns/structure/1.0\"?><book/>")
                .orElseThrow().kind());
        assertEquals(GrammarKind.RELAX_NG_COMPACT, DeclaredGrammar.of("<?xml-model href='b.rnc' type='application/relax-ng-compact-syntax'?><book/>")
                .orElseThrow().kind());
        assertTrue(DeclaredGrammar.of("<book><!DOCTYPE x SYSTEM 'y'></book>").isEmpty(), "only the prolog counts");
        assertTrue(DeclaredGrammar.of("<?xml-model href='s.sch' schematypens='http://purl.oclc.org/dsdl/schematron'?><a/>").isEmpty());
    }

    @Test
    @DisplayName("DTD validation: external DTD, internal subset, line numbers")
    void dtd() throws Exception {
        Files.writeString(dir.resolve("book.dtd"), DTD);
        Path doc = dir.resolve("book.xml");
        String valid = "<!DOCTYPE book SYSTEM \"book.dtd\">\n<book id=\"b1\"><title>T</title><chapter>C</chapter></book>";
        assertEquals(List.of(), DtdValidator.validate(valid, doc.toUri().toString(), null));
        String invalid = "<!DOCTYPE book SYSTEM \"book.dtd\">\n<book lang=\"fr\">\n<chapter>C</chapter></book>";
        List<GrammarProblem> problems = DtdValidator.validate(invalid, doc.toUri().toString(), null);
        assertTrue(problems.size() >= 2, problems.toString());
        assertTrue(problems.stream().allMatch(p -> p.line() >= 2), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.message().contains("id")), problems.toString());

        String internal = "<!DOCTYPE a [<!ELEMENT a (b)><!ELEMENT b EMPTY>]>\n<a><c/></a>";
        assertFalse(DtdValidator.validate(internal, null, null).isEmpty());

        // A chosen DTD for a document without DOCTYPE
        assertEquals(List.of(), DtdValidator.validate("<book id='x'><title>T</title><chapter>C</chapter></book>", null,
                dir.resolve("book.dtd").toFile()));
        assertFalse(DtdValidator.validate("<book><chapter>C</chapter></book>", null, dir.resolve("book.dtd").toFile()).isEmpty());
    }

    @Test
    @DisplayName("DTD security: no external entities (XXE), entity expansion limited, switch in the settings")
    void dtdSecurity() throws Exception {
        Path secret = Files.writeString(dir.resolve("secret.txt"), "TOP-SECRET");
        String xxe = "<!DOCTYPE a [<!ELEMENT a (#PCDATA)><!ENTITY s SYSTEM \"" + secret.toUri() + "\">]>\n<a>&s;</a>";
        StringBuilder seen = new StringBuilder();
        List<GrammarProblem> problems = DtdValidator.validate(xxe, null, null);
        problems.forEach(p -> seen.append(p.message()));
        assertFalse(seen.toString().contains("TOP-SECRET"), "the file is never read");

        StringBuilder laughs = new StringBuilder("<!DOCTYPE lolz [<!ELEMENT lolz (#PCDATA)><!ENTITY lol0 \"lol\">");
        for (int i = 1; i <= 9; i++) {
            laughs.append("<!ENTITY lol").append(i).append(" \"").append(("&lol" + (i - 1) + ";").repeat(10)).append("\">");
        }
        laughs.append("]><lolz>&lol9;</lolz>");
        List<GrammarProblem> limited = DtdValidator.validate(laughs.toString(), null, null);
        assertTrue(limited.stream().anyMatch(p -> p.severity().equals("fatal")), "stopped by the expansion limit: " + limited);

        PropertiesService props = ServiceRegistry.get(PropertiesService.class);
        try {
            props.set(GrammarPolicy.DTD_ALLOWED, "false");
            assertTrue(DtdValidator.validate("<!DOCTYPE a [<!ELEMENT a EMPTY>]><a/>", null, null).getFirst().message()
                    .contains("switched off"));
        } finally {
            props.set(GrammarPolicy.DTD_ALLOWED, "true");
        }
    }

    @Test
    @DisplayName("RELAX NG validation, XML and compact syntax")
    void relaxNg() throws Exception {
        Path rng = Files.writeString(dir.resolve("book.rng"), RNG);
        Path rnc = Files.writeString(dir.resolve("book.rnc"), RNC);
        String valid = "<book id='1'><title>T</title><chapter>C</chapter></book>";
        String invalid = "<book>\n<title>T</title>\n<para/></book>";
        for (Path schema : List.of(rng, rnc)) {
            assertEquals(List.of(), RelaxNgValidator.validate(valid, null, schema.toFile()), schema.toString());
            List<GrammarProblem> problems = RelaxNgValidator.validate(invalid, null, schema.toFile());
            assertFalse(problems.isEmpty(), schema.toString());
            assertTrue(problems.stream().anyMatch(p -> p.line() >= 1), problems.toString());
        }
        Path broken = Files.writeString(dir.resolve("broken.rnc"), "start = element { ");
        assertFalse(RelaxNgValidator.validate(valid, null, broken.toFile()).isEmpty());
    }

    @Test
    @DisplayName("DTD and RELAX NG translate to XSD for IntelliSense")
    void toXsd() throws Exception {
        System.setProperty("fxt.grammarXsd.dir", dir.resolve("cache").toString());
        try {
            for (String name : List.of("book.dtd", "book.rng", "book.rnc")) {
                Path grammar = Files.writeString(dir.resolve(name), name.endsWith(".dtd") ? DTD : name.endsWith(".rng") ? RNG : RNC);
                String xsd = Files.readString(GrammarToXsd.convert(grammar));
                assertTrue(xsd.contains("http://www.w3.org/2001/XMLSchema") && xsd.contains("name=\"book\"")
                        && xsd.contains("chapter"), name + ":\n" + xsd);
            }
            Path internal = GrammarToXsd.convertInternalSubset("<!DOCTYPE a [<!ELEMENT a (b*)><!ELEMENT b EMPTY>]><a/>");
            assertTrue(Files.readString(internal).contains("name=\"b\""));
        } finally {
            System.clearProperty("fxt.grammarXsd.dir");
        }
    }

    @Test
    @DisplayName("ValidationRunner: declared and bound grammars; an unavailable DTD is a warning")
    void runner() throws Exception {
        Files.writeString(dir.resolve("book.dtd"), DTD);
        Path doc = dir.resolve("book.xml");
        String declared = "<!DOCTYPE book SYSTEM \"book.dtd\">\n<book><title>T</title></book>";
        List<ValidationProblem> problems = ValidationRunner.runWithReport(declared, null, null, "book.xml", doc).problems();
        assertTrue(!problems.isEmpty() && problems.stream().allMatch(p -> p.source().equals("DTD")), problems.toString());

        String missing = "<!DOCTYPE book SYSTEM \"nope.dtd\">\n<book/>";
        List<ValidationProblem> warned = ValidationRunner.runWithReport(missing, null, null, "x.xml", doc).problems();
        assertEquals(1, warned.size(), warned.toString());
        assertEquals("warning", warned.getFirst().severity());

        Path rng = Files.writeString(dir.resolve("book.rng"), RNG);
        List<ValidationProblem> bound = ValidationRunner.runWithReport("<book/>", rng.toFile(), null, "x.xml", doc).problems();
        assertTrue(!bound.isEmpty() && bound.getFirst().source().equals("RELAX NG"), bound.toString());

        assertTrue(ValidationRunner.runWithReport(declared, null, null, "b.xml", doc, false).problems().isEmpty(),
                "without declared grammars: well-formed only");
    }
}
