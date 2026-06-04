package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.security.KeyStore;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXCertPathValidatorResult;
import java.security.cert.PKIXParameters;
import java.security.cert.PKIXRevocationChecker;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * Real PKIX trust validation of an XML signature's certificate chain — the security work behind
 * "expert signature validation" (chain, trust anchor, revocation, timestamp), which the basic
 * core-validity check does not perform. The signing chain (embedded {@code X509Certificate}s) is
 * validated against a caller-supplied trust store; a self-signed certificate is trusted only when
 * it is itself present in the trust store. Revocation (OCSP/CRL) is opt-in and soft-fails; timestamp
 * presence is detected (full RFC&nbsp;3161/XAdES token validation is out of scope).
 */
public final class SignatureTrustValidator {

    private SignatureTrustValidator() {
    }

    /** Outcome of a trust validation. */
    public record TrustResult(boolean trusted, boolean selfSigned, String anchor,
                              String revocation, String timestamp, String message) {

        static TrustResult error(String message) {
            return new TrustResult(false, false, "—", "not checked", "none", message);
        }

        /** @return a multi-line human-readable report. */
        public String report() {
            return "Trust:      " + (trusted ? "TRUSTED ✓" : "NOT TRUSTED ✗") + '\n'
                    + "Chain:      " + (selfSigned ? "self-signed (single certificate)" : "validated to a trust anchor") + '\n'
                    + "Anchor:     " + anchor + '\n'
                    + "Revocation: " + revocation + '\n'
                    + "Timestamp:  " + timestamp + '\n'
                    + "Detail:     " + message;
        }
    }

    /**
     * Validates a signed XML file's certificate chain against {@code trustStore}.
     *
     * @param checkRevocation enable best-effort OCSP/CRL revocation checking (soft-fail)
     */
    public static TrustResult validate(File signedFile, KeyStore trustStore, boolean checkRevocation) {
        if (signedFile == null || !signedFile.isFile()) {
            return TrustResult.error("file not found: " + signedFile);
        }
        try {
            Document doc = parse(signedFile);
            List<X509Certificate> chain = extractChain(doc);
            TrustResult base = validateChain(chain, trustStore, checkRevocation);
            String timestamp = detectTimestamp(doc);
            return new TrustResult(base.trusted(), base.selfSigned(), base.anchor(),
                    base.revocation(), timestamp, base.message());
        } catch (Exception e) {
            return TrustResult.error(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    /** Core PKIX validation of an explicit certificate chain (end-entity first) against a trust store. */
    public static TrustResult validateChain(List<X509Certificate> chain, KeyStore trustStore,
            boolean checkRevocation) {
        if (chain == null || chain.isEmpty()) {
            return TrustResult.error("no certificate chain found in the signature");
        }
        try {
            X509Certificate leaf = chain.get(0);
            boolean selfSigned = chain.size() == 1
                    && leaf.getSubjectX500Principal().equals(leaf.getIssuerX500Principal());
            Set<TrustAnchor> anchors = anchors(trustStore);
            if (anchors.isEmpty()) {
                return new TrustResult(false, selfSigned, "(no trust anchors)",
                        "not checked", "none", "The trust store has no trusted certificates.");
            }

            // A self-signed certificate is its own trust anchor: it is trusted iff it is in the store.
            if (selfSigned) {
                boolean inStore = anchors.stream()
                        .anyMatch(a -> leaf.equals(a.getTrustedCert()));
                return new TrustResult(inStore, true,
                        inStore ? leaf.getSubjectX500Principal().getName() : "(self-signed, untrusted)",
                        "not applicable", "none",
                        inStore ? "Self-signed certificate is present in the trust store."
                                : "Self-signed certificate is not in the trust store.");
            }

            CertPath path = CertificateFactory.getInstance("X.509").generateCertPath(chain);
            PKIXParameters params = new PKIXParameters(anchors);
            String revocation;
            CertPathValidator validator = CertPathValidator.getInstance("PKIX");
            if (checkRevocation) {
                params.setRevocationEnabled(true);
                PKIXRevocationChecker checker = (PKIXRevocationChecker) validator.getRevocationChecker();
                checker.setOptions(EnumSet.of(PKIXRevocationChecker.Option.SOFT_FAIL,
                        PKIXRevocationChecker.Option.PREFER_CRLS));
                params.addCertPathChecker(checker);
                revocation = "checked (soft-fail)";
            } else {
                params.setRevocationEnabled(false);
                revocation = "not checked";
            }

            PKIXCertPathValidatorResult result = (PKIXCertPathValidatorResult) validator.validate(path, params);
            X509Certificate anchorCert = result.getTrustAnchor().getTrustedCert();
            String anchorName = anchorCert != null ? anchorCert.getSubjectX500Principal().getName() : "(anchor)";
            return new TrustResult(true, false, anchorName, revocation, "none",
                    "Certificate chain is trusted.");
        } catch (CertPathValidatorException e) {
            boolean selfSigned = chain.size() == 1;
            return new TrustResult(false, selfSigned, "(untrusted)",
                    checkRevocation ? "checked" : "not checked", "none", "Untrusted chain: " + e.getMessage());
        } catch (Exception e) {
            return TrustResult.error(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private static Set<TrustAnchor> anchors(KeyStore trustStore) throws Exception {
        Set<TrustAnchor> anchors = new HashSet<>();
        if (trustStore == null) {
            return anchors;
        }
        Enumeration<String> aliases = trustStore.aliases();
        while (aliases.hasMoreElements()) {
            Certificate cert = trustStore.getCertificate(aliases.nextElement());
            if (cert instanceof X509Certificate x509) {
                anchors.add(new TrustAnchor(x509, null));
            }
        }
        return anchors;
    }

    private static List<X509Certificate> extractChain(Document doc) throws Exception {
        NodeList certs = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "X509Certificate");
        List<X509Certificate> chain = new ArrayList<>();
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        for (int i = 0; i < certs.getLength(); i++) {
            byte[] der = Base64.getDecoder().decode(certs.item(i).getTextContent().replaceAll("\\s", ""));
            chain.add((X509Certificate) cf.generateCertificate(new ByteArrayInputStream(der)));
        }
        return chain;
    }

    /** Detects an XAdES signature timestamp token (presence only — full validation is out of scope). */
    private static String detectTimestamp(Document doc) {
        if (doc.getElementsByTagNameNS("*", "SignatureTimeStamp").getLength() > 0
                || doc.getElementsByTagNameNS("*", "EncapsulatedTimeStamp").getLength() > 0) {
            return "present (token not validated)";
        }
        return "none";
    }

    private static Document parse(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(file);
    }
}
