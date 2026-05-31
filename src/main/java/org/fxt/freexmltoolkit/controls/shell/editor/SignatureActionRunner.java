package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;

import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.parsers.DocumentBuilderFactory;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.fxt.freexmltoolkit.service.SignatureService;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * UI-free signature helpers for the shell, reusing {@link SignatureService}.
 * Currently exposes self-signed certificate / JKS keystore creation; the
 * keystore is written under {@code certs/<alias>/} (as in the legacy tool).
 */
public final class SignatureActionRunner {

    private SignatureActionRunner() {
    }

    /**
     * Produces a detailed validation report for a signed XML document: the
     * cryptographic validity (reusing {@link SignatureService#isSignatureValid})
     * plus the embedded signing certificate's details (subject, issuer, validity
     * window, serial).
     *
     * <p>Note: certificate-chain, trust-anchor, revocation (OCSP/CRL) and
     * timestamp validation are <em>not</em> performed — they require a configured
     * trust store / online checks and are out of scope here.
     *
     * @return the report, or {@code "ERROR: …"}
     */
    public static String describeSignature(File signedFile) {
        if (!signedFile.isFile()) {
            return "ERROR: file not found: " + signedFile;
        }
        try {
            boolean valid = new SignatureService().isSignatureValid(signedFile);
            StringBuilder report = new StringBuilder();
            report.append("Signature: ").append(valid ? "VALID ✓" : "INVALID ✗").append('\n');

            X509Certificate cert = extractSigningCertificate(signedFile);
            if (cert != null) {
                report.append("\nSigning certificate:\n");
                report.append("  Subject:    ").append(cert.getSubjectX500Principal().getName()).append('\n');
                report.append("  Issuer:     ").append(cert.getIssuerX500Principal().getName()).append('\n');
                report.append("  Valid from: ").append(cert.getNotBefore()).append('\n');
                report.append("  Valid to:   ").append(cert.getNotAfter()).append('\n');
                report.append("  Serial:     ").append(cert.getSerialNumber()).append('\n');
                report.append("  Algorithm:  ").append(cert.getSigAlgName()).append('\n');
            } else {
                report.append("\n(No embedded X.509 certificate found.)\n");
            }
            report.append("\nNot checked: certificate chain, trust anchor, revocation (OCSP/CRL), timestamp.\n");
            return report.toString();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /** Extracts the first embedded X.509 certificate from a signed XML document, or {@code null}. */
    private static X509Certificate extractSigningCertificate(File signedFile) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);
        Document doc = factory.newDocumentBuilder().parse(signedFile);
        NodeList certs = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "X509Certificate");
        if (certs.getLength() == 0) {
            return null;
        }
        String base64 = certs.item(0).getTextContent().replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(der));
    }

    /**
     * The distinguished-name fields for a self-signed certificate. All fields are
     * optional; blank ones are omitted from the subject DN.
     */
    public record CertificateInfo(String commonName, String organizationUnit, String organization,
                                  String locality, String state, String country, String email) {
    }

    /**
     * Creates a self-signed certificate and a JKS keystore containing it.
     *
     * @return the created keystore file
     * @throws org.fxt.freexmltoolkit.service.SignatureService.SignatureServiceException on failure
     */
    public static File createKeystore(CertificateInfo info, String alias,
                                      String keystorePassword, String aliasPassword) {
        X500NameBuilder nameBuilder = new X500NameBuilder(X500Name.getDefaultStyle());
        addRdn(nameBuilder, BCStyle.CN, info.commonName());
        addRdn(nameBuilder, BCStyle.OU, info.organizationUnit());
        addRdn(nameBuilder, BCStyle.O, info.organization());
        addRdn(nameBuilder, BCStyle.L, info.locality());
        addRdn(nameBuilder, BCStyle.ST, info.state());
        addRdn(nameBuilder, BCStyle.C, info.country());
        addRdn(nameBuilder, BCStyle.EmailAddress, info.email());
        return new SignatureService().createNewKeystoreFile(nameBuilder, alias, keystorePassword, aliasPassword);
    }

    private static void addRdn(X500NameBuilder builder, org.bouncycastle.asn1.ASN1ObjectIdentifier oid, String value) {
        if (value != null && !value.isBlank()) {
            builder.addRDN(oid, value.trim());
        }
    }
}
