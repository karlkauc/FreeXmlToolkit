package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.fxt.freexmltoolkit.service.crypto.EncryptionReport;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService.DecryptionKeys;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService.DecryptionOutcome;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService.DecryptionStatus;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService.EncryptionOutcome;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService.EncryptionRequest;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService.XmlEncryptionException;
import org.fxt.freexmltoolkit.service.telemetry.QueryFailure;
import org.fxt.freexmltoolkit.util.SecureXmlFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * File-level XML encryption for the Sign &amp; Encrypt page and {@code fxt-cli encrypt/decrypt}:
 * reads the document without any DOCTYPE (encrypted documents come from outside), runs
 * {@link XmlEncryptionService} and writes the result. No UI.
 */
public final class EncryptionActionRunner {

    private static final XmlEncryptionService SERVICE = new XmlEncryptionService();

    private EncryptionActionRunner() {
    }

    /**
     * The result of an encryption or decryption.
     *
     * @param output   the written file, or {@code null} on failure
     * @param message  a one-line summary
     * @param warnings legacy algorithms met
     * @param failure  why it failed (telemetry), or {@code null}
     * @param binary   the output is binary data (a decrypted non-XML EncryptedData)
     */
    public record Result(Path output, String message, List<String> warnings, QueryFailure failure, boolean binary) {

        Result(Path output, String message, List<String> warnings, QueryFailure failure) {
            this(output, message, warnings, failure, false);
        }

        public boolean ok() {
            return failure == null;
        }
    }

    /** Parses a document namespace-aware, without DOCTYPE, external entities or network; keeps its location. */
    public static Document parse(Path file) throws Exception {
        DocumentBuilderFactory factory = SecureXmlFactory.createSecureDocumentBuilderFactory(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document document = factory.newDocumentBuilder().parse(file.toFile());
        document.setDocumentURI(file.toUri().toString());
        return document;
    }

    /** Parses a text (the editor's document), like {@link #parse(Path)}. */
    public static Document parse(String xml, Path location) throws Exception {
        DocumentBuilderFactory factory = SecureXmlFactory.createSecureDocumentBuilderFactory(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document document = factory.newDocumentBuilder().parse(
                new org.xml.sax.InputSource(new java.io.StringReader(xml)));
        if (location != null) {
            document.setDocumentURI(location.toUri().toString());
        }
        return document;
    }

    /** Serializes a document as UTF-8 XML (no reformatting). */
    public static String serialize(Document document) throws Exception {
        Transformer transformer = SecureXmlFactory.createSecureTransformerFactory().newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        StringWriter out = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(out));
        return out.toString();
    }

    /** Encrypts {@code input} as the request says and writes {@code output}. */
    public static Result encryptFile(Path input, Path output, EncryptionRequest request) {
        try {
            Document document = parse(input);
            EncryptionOutcome outcome = SERVICE.encrypt(document, request);
            Files.writeString(output, serialize(document), StandardCharsets.UTF_8);
            return new Result(output, "Encrypted " + outcome.encrypted() + " element(s) for "
                    + request.recipients().size() + " recipient(s)", outcome.warnings(), null);
        } catch (XmlEncryptionException e) {
            return failure(e.getMessage(), e);
        } catch (Exception e) {
            return failure("Encryption failed: " + e.getMessage(), e);
        }
    }

    /** Encrypts any file as binary data (EncryptedData root with MimeType) and writes {@code output}. */
    public static Result encryptBinaryFile(Path input, Path output, String mimeType, EncryptionRequest request) {
        try {
            String type = mimeType != null && !mimeType.isBlank() ? mimeType : Files.probeContentType(input);
            Document document = SERVICE.encryptBinary(Files.readAllBytes(input), type, request);
            Files.writeString(output, serialize(document), StandardCharsets.UTF_8);
            return new Result(output, "Encrypted " + input.getFileName() + " (" + Files.size(input) + " bytes)"
                    + (type != null ? " as " + type : ""), List.of(), null);
        } catch (XmlEncryptionException e) {
            return failure(e.getMessage(), e);
        } catch (Exception e) {
            return failure("Encryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * Decrypts {@code input} and writes {@code output}: the XML with its EncryptedData replaced, or
     * - for a document that is one EncryptedData of non-XML data - the plain bytes.
     *
     * @param ids only these EncryptedData Ids (empty: all)
     */
    public static Result decryptFile(Path input, Path output, DecryptionKeys keys, Set<String> ids) {
        try {
            Document document = parse(input);
            if (isBinary(document)) {
                byte[] data = SERVICE.decryptBinary(document, keys);
                Files.write(output, data);
                return new Result(output, "Decrypted " + data.length + " bytes", List.of(), null, true);
            }
            DecryptionOutcome outcome = SERVICE.decrypt(document, keys, ids);
            if (!outcome.isSuccess()) {
                return new Result(null, outcome.message(), outcome.warnings(), failureOf(outcome.status(), outcome.cause()));
            }
            Files.writeString(output, serialize(document), StandardCharsets.UTF_8);
            return new Result(output, outcome.message(), outcome.warnings(), null);
        } catch (XmlEncryptionException e) {
            return new Result(null, e.getMessage(), List.of(), failureOf(e.status(), e));
        } catch (Exception e) {
            return failure("Decryption failed: " + e.getMessage(), e);
        }
    }

    /** Decrypts a document text (the editor's), returning the decrypted text. */
    public static DecryptedText decryptText(String xml, Path location, DecryptionKeys keys, Set<String> ids) {
        try {
            Document document = parse(xml, location);
            if (isBinary(document)) {
                byte[] data = SERVICE.decryptBinary(document, keys);
                return new DecryptedText(null, data, "Decrypted " + data.length + " bytes of binary data", List.of(), null);
            }
            DecryptionOutcome outcome = SERVICE.decrypt(document, keys, ids);
            if (!outcome.isSuccess()) {
                return new DecryptedText(null, null, outcome.message(), outcome.warnings(),
                        failureOf(outcome.status(), outcome.cause()));
            }
            return new DecryptedText(serialize(document), null, outcome.message(), outcome.warnings(), null);
        } catch (XmlEncryptionException e) {
            return new DecryptedText(null, null, e.getMessage(), List.of(), failureOf(e.status(), e));
        } catch (Exception e) {
            return new DecryptedText(null, null, "Decryption failed: " + e.getMessage(), List.of(), QueryFailure.of(e));
        }
    }

    /** A decrypted text (XML) or bytes (binary data), with message, warnings and failure. */
    public record DecryptedText(String xml, byte[] binary, String message, List<String> warnings, QueryFailure failure) {
        public boolean ok() {
            return failure == null;
        }
    }

    /** Encrypts a document text (the editor's), returning the encrypted text. */
    public static EncryptedText encryptText(String xml, Path location, EncryptionRequest request) {
        try {
            Document document = parse(xml, location);
            EncryptionOutcome outcome = SERVICE.encrypt(document, request);
            return new EncryptedText(serialize(document), "Encrypted " + outcome.encrypted() + " element(s) for "
                    + request.recipients().size() + " recipient(s)", outcome.warnings(), null);
        } catch (XmlEncryptionException e) {
            return new EncryptedText(null, e.getMessage(), List.of(), failureOf(e.status(), e));
        } catch (Exception e) {
            return new EncryptedText(null, "Encryption failed: " + e.getMessage(), List.of(), QueryFailure.of(e));
        }
    }

    /** An encrypted text, with message, warnings and failure. */
    public record EncryptedText(String xml, String message, List<String> warnings, QueryFailure failure) {
        public boolean ok() {
            return failure == null;
        }
    }

    /** Reads the structure of an encrypted file (no key needed). */
    public static EncryptionReport inspectFile(Path input) throws Exception {
        return EncryptionReport.of(parse(input));
    }

    /** Reads the structure of an encrypted text (no key needed). */
    public static EncryptionReport inspectText(String xml) throws Exception {
        return EncryptionReport.of(parse(xml, null));
    }

    /** @return whether the document is one EncryptedData of non-XML data (no Element/Content type) */
    static boolean isBinary(Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !XmlEncryptionService.XENC_NS.equals(root.getNamespaceURI())
                || !"EncryptedData".equals(root.getLocalName())) {
            return false;
        }
        String type = root.getAttribute("Type");
        return !type.endsWith("#Element") && !type.endsWith("#Content");
    }

    /** @return the sibling file {@code name.<suffix>} next to {@code file} ("order.xml" → "order.encrypted.xml") */
    public static Path sibling(Path file, String suffix) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        Path parent = file.toAbsolutePath().getParent();
        return parent.resolve(base + suffix);
    }

    private static Result failure(String message, Throwable cause) {
        QueryFailure failure = cause instanceof XmlEncryptionException e ? failureOf(e.status(), e) : QueryFailure.of(cause);
        return new Result(null, message, List.of(), failure);
    }

    /** Wrong keys, policy and missing data are the user's input, not app errors. */
    static QueryFailure failureOf(DecryptionStatus status, Throwable cause) {
        return switch (status) {
            case WRONG_KEY -> QueryFailure.invalidInput("wrong_key");
            case NO_ENCRYPTED_DATA -> QueryFailure.invalidInput("no_encrypted_data");
            case POLICY_BLOCKED -> QueryFailure.invalidInput("policy_blocked");
            case UNSUPPORTED -> QueryFailure.invalidInput("unsupported_algorithm");
            default -> cause instanceof XmlEncryptionException && cause.getCause() == null
                    ? QueryFailure.invalidInput("invalid_request")
                    : cause != null ? QueryFailure.of(cause) : QueryFailure.UNKNOWN;
        };
    }
}
