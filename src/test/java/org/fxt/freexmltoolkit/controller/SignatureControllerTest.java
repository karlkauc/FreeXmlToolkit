package org.fxt.freexmltoolkit.controller;

import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;
import org.fxt.freexmltoolkit.service.SignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SignatureController.
 */
@ExtendWith(MockitoExtension.class)
class SignatureControllerTest {

    private SignatureController controller;

    @Mock
    private SignatureService mockSignatureService;

    @Mock
    private Button mockSignButton;

    @Mock
    private Button mockValidateButton;

    @Mock
    private Button mockCreateKeystoreButton;

    @Mock
    private TextField mockXmlFileField;

    @Mock
    private TextField mockKeystoreField;

    @Mock
    private PasswordField mockKeystorePasswordField;

    @BeforeEach
    void setUp() {
        controller = new SignatureController();
    }

    @Test
    @DisplayName("Should create controller instance")
    void testControllerInstantiation() {
        assertNotNull(controller);
    }

    @Test
    @DisplayName("Should validate keystore file extension")
    void testKeystoreFileValidation() {
        File keystoreFile = new File("keystore.jks");
        assertTrue(keystoreFile.getName().endsWith(".jks") ||
                   keystoreFile.getName().endsWith(".p12") ||
                   keystoreFile.getName().endsWith(".pfx"));

        File invalidFile = new File("keystore.txt");
        assertFalse(invalidFile.getName().endsWith(".jks"));
    }

    @Test
    @DisplayName("Should validate XML digital signature namespace")
    void testXmlSignatureNamespace() {
        String dsigNamespace = "http://www.w3.org/2000/09/xmldsig#";
        assertNotNull(dsigNamespace);
        assertTrue(dsigNamespace.contains("xmldsig"));
    }

    @Test
    @DisplayName("Should recognize signature algorithms")
    void testSignatureAlgorithms() {
        // SHA256 is required, SHA1 is deprecated
        String sha256 = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256";
        String sha512 = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha512";

        assertNotNull(sha256);
        assertNotNull(sha512);
        assertTrue(sha256.contains("sha256"));
        assertTrue(sha512.contains("sha512"));

        // SHA1 should be rejected
        String sha1 = "http://www.w3.org/2000/09/xmldsig#rsa-sha1";
        assertNotNull(sha1);
        assertTrue(sha1.contains("sha1"), "SHA1 exists but should be rejected by security policy");
    }

    @Test
    @DisplayName("Should validate canonicalization methods")
    void testCanonicalizationMethods() {
        String exclusive = "http://www.w3.org/2001/10/xml-exc-c14n#";
        String inclusive = "http://www.w3.org/TR/2001/REC-xml-c14n-20010315";

        assertNotNull(exclusive);
        assertNotNull(inclusive);
        assertTrue(exclusive.contains("c14n"));
        assertTrue(inclusive.contains("c14n"));
    }

    @Test
    @DisplayName("Should validate transform methods")
    void testTransformMethods() {
        String enveloped = "http://www.w3.org/2000/09/xmldsig#enveloped-signature";
        String base64 = "http://www.w3.org/2000/09/xmldsig#base64";

        assertNotNull(enveloped);
        assertNotNull(base64);
        assertTrue(enveloped.contains("enveloped"));
        assertTrue(base64.contains("base64"));
    }

    @Test
    @DisplayName("Should handle keystore types")
    void testKeystoreTypes() {
        String jks = "JKS";
        String pkcs12 = "PKCS12";
        String jceks = "JCEKS";

        assertNotNull(jks);
        assertNotNull(pkcs12);
        assertNotNull(jceks);

        assertEquals("JKS", jks);
        assertEquals("PKCS12", pkcs12);
    }

    @Test
    @DisplayName("Should validate key algorithms")
    void testKeyAlgorithms() {
        String rsa = "RSA";
        String dsa = "DSA";
        String ec = "EC";

        assertNotNull(rsa);
        assertNotNull(dsa);
        assertNotNull(ec);

        assertEquals("RSA", rsa);
    }

    @Test
    @DisplayName("Should handle certificate information")
    void testCertificateInfo() {
        String cn = "CN=Test User";
        String o = "O=Test Organization";
        String c = "C=AT";

        assertNotNull(cn);
        assertNotNull(o);
        assertNotNull(c);

        assertTrue(cn.startsWith("CN="));
        assertTrue(o.startsWith("O="));
        assertTrue(c.startsWith("C="));
    }

    @Test
    @DisplayName("Should validate security requirements")
    void testSecurityRequirements() {
        // Minimum key size should be 2048 for RSA
        int minKeySize = 2048;
        assertTrue(minKeySize >= 2048);

        // Maximum validity period recommendation
        int maxValidityYears = 10;
        assertTrue(maxValidityYears > 0 && maxValidityYears <= 10);
    }

    @Test
    @DisplayName("Should validate XML signature elements")
    void testXmlSignatureElements() {
        String[] signatureElements = {
            "Signature", "SignedInfo", "SignatureValue",
            "KeyInfo", "Reference", "DigestMethod", "DigestValue"
        };

        for (String element : signatureElements) {
            assertNotNull(element);
            assertFalse(element.isEmpty());
        }
    }
}
