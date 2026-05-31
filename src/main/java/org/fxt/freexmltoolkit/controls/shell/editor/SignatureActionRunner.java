package org.fxt.freexmltoolkit.controls.shell.editor;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.fxt.freexmltoolkit.service.SignatureService;

import java.io.File;

/**
 * UI-free signature helpers for the shell, reusing {@link SignatureService}.
 * Currently exposes self-signed certificate / JKS keystore creation; the
 * keystore is written under {@code certs/<alias>/} (as in the legacy tool).
 */
public final class SignatureActionRunner {

    private SignatureActionRunner() {
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
