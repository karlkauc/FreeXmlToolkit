package org.fxt.freexmltoolkit.service.sampleaudit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl;
import org.fxt.freexmltoolkit.service.SchemaResourceCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Guards the classification logic of the real-world sample XML audit
 * ({@link SampleXmlGeneratorRealWorldAuditTest}) so the audit's numbers can be trusted.
 */
class AuditHarnessSelfTest {

    private static final String NOTE_XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="note">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="to" type="xs:string"/>
                    <xs:element name="priority" type="xs:int"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
              <xs:element name="base" abstract="true" type="xs:string"/>
            </xs:schema>
            """;

    @TempDir
    Path dir;

    private SchemaLibraryServiceImpl emptyLibrary() {
        return new SchemaLibraryServiceImpl(dir.resolve("lib.json"), new SchemaResourceCache(dir.resolve("cache")),
                () -> new ByteArrayInputStream("{\"version\":1,\"entries\":[]}".getBytes()));
    }

    private AuditSchemaCompiler.Compiled compileNote() throws Exception {
        Path xsd = dir.resolve("note.xsd");
        Files.writeString(xsd, NOTE_XSD);
        return AuditSchemaCompiler.compile(xsd, false, new AuditResourceResolver(emptyLibrary()));
    }

    @Test
    void validInstanceIsValid() throws Exception {
        AuditSchemaCompiler.Compiled compiled = compileNote();
        assertTrue(compiled.ok(), () -> compiled.errors().toString());

        var validation = AuditSchemaCompiler.validate(compiled.schema(), "<note><to>a</to><priority>1</priority></note>");
        assertTrue(validation.wellFormed());
        assertEquals(0, validation.errorCount(), () -> validation.issues().toString());
        assertEquals("note", validation.rootElement());
    }

    @Test
    void invalidInstanceKeepsErrorKeyPathAndSnippet() throws Exception {
        AuditSchemaCompiler.Compiled compiled = compileNote();

        var validation = AuditSchemaCompiler.validate(compiled.schema(), "<note>\n<to>a</to>\n<priority>x</priority>\n</note>");
        assertTrue(validation.wellFormed());
        assertTrue(validation.errorCount() > 0);
        assertTrue(validation.errorKeys().containsKey("cvc-datatype-valid.1.2.1"), validation.errorKeys().toString());
        var issue = validation.issues().getFirst();
        assertEquals("/note/priority", issue.elementPath());
        assertEquals(3, issue.line());
        assertNotNull(issue.snippet());
        assertTrue(issue.snippet().contains("<priority>x</priority>"), issue.snippet());

        var missingChild = AuditSchemaCompiler.validate(compiled.schema(), "<note><priority>1</priority></note>");
        assertTrue(missingChild.errorKeys().containsKey("cvc-complex-type.2.4.a"), missingChild.errorKeys().toString());
    }

    @Test
    void notWellFormedInstanceIsDetected() throws Exception {
        AuditSchemaCompiler.Compiled compiled = compileNote();
        var validation = AuditSchemaCompiler.validate(compiled.schema(), "<note><to>a</note>");
        assertFalse(validation.wellFormed());

        var withoutSchema = AuditSchemaCompiler.validate(null, "<note><to>a</to></note>");
        assertTrue(withoutSchema.wellFormed());
        assertEquals(0, withoutSchema.errorCount());
    }

    @Test
    void globalElementsIncludeAbstractFlag() throws Exception {
        AuditSchemaCompiler.Compiled compiled = compileNote();
        assertTrue(compiled.globals().contains(new AuditModels.GlobalElement(null, "base", true)), compiled.globals().toString());
        assertTrue(compiled.globals().contains(new AuditModels.GlobalElement(null, "note", false)), compiled.globals().toString());
    }

    @Test
    void unmappedRemoteImportIsBlockedInsteadOfDownloaded() throws Exception {
        Path xsd = dir.resolve("main.xsd");
        Files.writeString(xsd, """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:import namespace="urn:remote" schemaLocation="https://example.invalid/remote.xsd"/>
                  <xs:element name="root" type="xs:string"/>
                </xs:schema>
                """);
        SchemaLibraryServiceImpl library = emptyLibrary();
        AuditResourceResolver resolver = new AuditResourceResolver(library);

        AuditSchemaCompiler.compile(xsd, false, resolver);

        assertEquals(java.util.List.of("https://example.invalid/remote.xsd"), resolver.blockedRemote());
        try (var cached = Files.list(dir.resolve("cache"))) {
            assertTrue(cached.noneMatch(p -> p.getFileName().toString().endsWith(".xsd")), "nothing may be downloaded");
        }
    }

    @Test
    void catalogMappedImportAndNestedRelativeIncludeResolveLocally() throws Exception {
        Path sub = Files.createDirectories(dir.resolve("mirror").resolve("types"));
        Files.writeString(sub.resolve("types.xsd"), """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:types">
                  <xs:include schemaLocation="more/codes.xsd"/>
                </xs:schema>
                """);
        Files.createDirectories(sub.resolve("more"));
        Files.writeString(sub.resolve("more").resolve("codes.xsd"), """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:types">
                  <xs:simpleType name="Code"><xs:restriction base="xs:string"><xs:length value="3"/></xs:restriction></xs:simpleType>
                </xs:schema>
                """);
        Path catalog = dir.resolve("catalog.xml");
        Files.writeString(catalog, """
                <catalog xmlns="urn:oasis:names:tc:entity:xmlns:xml:catalog">
                  <uri name="https://example.org/types/types.xsd" uri="mirror/types/types.xsd"/>
                </catalog>
                """);
        Path xsd = dir.resolve("main.xsd");
        Files.writeString(xsd, """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:t="urn:types">
                  <xs:import namespace="urn:types" schemaLocation="https://example.org/types/types.xsd"/>
                  <xs:element name="code" type="t:Code"/>
                </xs:schema>
                """);
        SchemaLibraryServiceImpl library = emptyLibrary();
        library.addCatalog(catalog);
        AuditResourceResolver resolver = new AuditResourceResolver(library);

        AuditSchemaCompiler.Compiled compiled = AuditSchemaCompiler.compile(xsd, false, resolver);

        assertTrue(compiled.ok(), () -> compiled.errors().toString());
        assertTrue(resolver.blockedRemote().isEmpty(), resolver.blockedRemote().toString());
        assertEquals(0, AuditSchemaCompiler.validate(compiled.schema(), "<code>abc</code>").errorCount());
        assertTrue(AuditSchemaCompiler.validate(compiled.schema(), "<code>abcd</code>").errorKeys()
                .containsKey("cvc-length-valid"));
    }

    @Test
    void missingOrUnmappedImportIsServedByNamespaceEntry() throws Exception {
        Path local = Files.createDirectories(dir.resolve("local")).resolve("xml.xsd");
        Files.writeString(local, """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="http://www.w3.org/XML/1998/namespace">
                  <xs:attribute name="lang" type="xs:language"/>
                </xs:schema>
                """);
        Path xsd = dir.resolve("main.xsd");
        Files.writeString(xsd, """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:import namespace="http://www.w3.org/XML/1998/namespace" schemaLocation="../missing/xml.xsd"/>
                  <xs:element name="text">
                    <xs:complexType><xs:attribute ref="xml:lang" use="required"/></xs:complexType>
                  </xs:element>
                </xs:schema>
                """);
        SchemaLibraryServiceImpl library = emptyLibrary();
        library.addEntry(org.fxt.freexmltoolkit.domain.SchemaLibraryEntry.user("http://www.w3.org/XML/1998/namespace",
                local.toString(), org.fxt.freexmltoolkit.domain.SchemaKind.XSD, "", null));
        AuditResourceResolver resolver = new AuditResourceResolver(library);

        AuditSchemaCompiler.Compiled compiled = AuditSchemaCompiler.compile(xsd, false, resolver);

        assertTrue(compiled.ok(), () -> compiled.errors().toString());
        assertTrue(resolver.missingLocal().isEmpty(), resolver.missingLocal().toString());
        assertEquals(0, AuditSchemaCompiler.validate(compiled.schema(), "<text xml:lang=\"en\"/>").errorCount());
    }

    @Test
    void schemaDoctypeDoesNotReachTheNetwork() throws Exception {
        Path xsd = dir.resolve("doctype.xsd");
        Files.writeString(xsd, """
                <?xml version="1.0"?>
                <!DOCTYPE schema PUBLIC "-//W3C//DTD XMLSchema 200102//EN" "https://example.invalid/XMLSchema.dtd">
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="root" type="xs:string"/>
                </xs:schema>
                """);
        AuditResourceResolver resolver = new AuditResourceResolver(emptyLibrary());

        AuditSchemaCompiler.Compiled compiled = AuditSchemaCompiler.compile(xsd, false, resolver);

        assertTrue(compiled.ok(), () -> compiled.errors().toString());
        assertTrue(resolver.blockedRemote().isEmpty(), resolver.blockedRemote().toString());
    }

    @Test
    void errorKeyIsExtractedFromXercesMessages() {
        assertEquals("cvc-complex-type.2.4.a", AuditSchemaCompiler.errorKey("cvc-complex-type.2.4.a: Invalid content was found"));
        assertEquals("cvc-pattern-valid", AuditSchemaCompiler.errorKey("cvc-pattern-valid: Value 'x' is not facet-valid"));
        assertEquals("schema_reference.4", AuditSchemaCompiler.errorKey("schema_reference.4: Failed to read schema document"));
        assertEquals("other", AuditSchemaCompiler.errorKey("Premature end of file."));
        assertEquals("other", AuditSchemaCompiler.errorKey(null));
    }
}
