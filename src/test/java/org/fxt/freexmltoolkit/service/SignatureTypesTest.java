package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.fxt.freexmltoolkit.controls.shell.editor.SignatureActionRunner;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Sign + verify round-trips for all three XML-DSig structures (enveloped,
 * enveloping, detached), using a really generated self-signed keystore.
 */
class SignatureTypesTest {

    private static File keystore;

    @BeforeAll
    static void createKeystore() {
        keystore = SignatureActionRunner.createKeystore(
                new SignatureActionRunner.CertificateInfo(
                        "Sig Types Test", null, "FreeXmlToolkit", null, null, "AT", null),
                "sig-types", "secret", "secret");
    }

    @AfterAll
    static void deleteKeystore() {
        if (keystore != null && keystore.exists() && !keystore.delete()) {
            keystore.deleteOnExit();
        }
    }

    private Path writeXml(Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root><child>value</child></root>");
        return xml;
    }

    @Test
    void envelopedSignatureRoundTrips(@TempDir Path tmp) throws Exception {
        Path xml = writeXml(tmp);
        File signed = new SignatureService().signDocument(xml.toFile(), keystore, "secret",
                "sig-types", "secret", tmp.resolve("doc.signed.xml").toString(),
                SignatureService.SignatureType.ENVELOPED);

        String content = Files.readString(signed.toPath());
        assertTrue(content.contains("<root>") && content.contains("Signature"),
                "enveloped: the signature sits inside the original document");
        assertTrue(new SignatureService().isSignatureValid(signed));
    }

    @Test
    void envelopingSignatureWrapsTheContent(@TempDir Path tmp) throws Exception {
        Path xml = writeXml(tmp);
        File signed = new SignatureService().signDocument(xml.toFile(), keystore, "secret",
                "sig-types", "secret", tmp.resolve("doc.signed.xml").toString(),
                SignatureService.SignatureType.ENVELOPING);

        String content = Files.readString(signed.toPath());
        assertTrue(content.contains("Object") && content.contains("<root"),
                "enveloping: the content is wrapped inside the signature's ds:Object, got:\n" + content);
        var doc = org.fxt.freexmltoolkit.util.SecureXmlFactory.createSecureDocumentBuilder(true)
                .parse(signed);
        assertEquals("Signature", doc.getDocumentElement().getLocalName(),
                "enveloping: the Signature element is the document root");
        assertTrue(new SignatureService().isSignatureValid(signed));
    }

    @Test
    void envelopingSignatureWorksForNamespacedContent(@TempDir Path tmp) throws Exception {
        // A namespaced root carries its own xmlns declaration - the no-namespace
        // fixup must not interfere with this path.
        Path xml = tmp.resolve("funds.xml");
        Files.writeString(xml, "<f:FundsXML4 xmlns:f=\"http://fundsxml.org\"><f:x>1</f:x></f:FundsXML4>");
        File signed = new SignatureService().signDocument(xml.toFile(), keystore, "secret",
                "sig-types", "secret", tmp.resolve("funds.signed.xml").toString(),
                SignatureService.SignatureType.ENVELOPING);

        assertTrue(new SignatureService().isSignatureValid(signed));
    }

    @Test
    void detachedSignatureValidatesAndDetectsTampering(@TempDir Path tmp) throws Exception {
        Path xml = writeXml(tmp);
        File signature = new SignatureService().signDocument(xml.toFile(), keystore, "secret",
                "sig-types", "secret", tmp.resolve("doc.sig.xml").toString(),
                SignatureService.SignatureType.DETACHED);

        String content = Files.readString(signature.toPath());
        assertFalse(content.contains("<root>"),
                "detached: the signature document does not contain the signed content");
        assertTrue(content.contains("doc.xml"), "detached: the file is referenced by name");
        assertTrue(new SignatureService().isSignatureValid(signature),
                "the untouched file next to the signature must validate");

        Files.writeString(xml, "<root><child>TAMPERED</child></root>");
        assertFalse(new SignatureService().isSignatureValid(signature),
                "a modified file must break the detached signature");
    }
}
