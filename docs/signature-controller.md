# XML Digital Signature

This part of the application provides comprehensive tools for creating, managing, and validating digital signatures for
XML files. A digital signature acts like a tamper-proof seal, ensuring that the document is authentic and has not been
altered since it was signed.

The application supports both **Basic Mode** for common use cases and **Expert Mode** for advanced XML-DSig operations
with full W3C compliance.

![Screenshot of Signature Controller](img/signature-sign.png)

![Screenshot of Signature Controller](img/signature-validation.png)

> **Note:** The screenshots above show the Basic Mode interface. The Expert Mode provides additional advanced
> configuration options and real-time logging capabilities for professional XML-DSig operations.

## 1. Create a New Certificate

Before you can sign a document, you need a digital certificate, which is like a digital ID card. This tool helps you create one.

-   **Certificate Details:** You can fill in your personal or organizational details (like name, organization, country) to be included in the certificate.
-   **Keystore:** The certificate is stored in a secure, password-protected file called a Keystore (`.jks` file). You will need to set a password for the keystore itself and another password for your specific certificate (alias) within it.
-   **Save:** The tool will generate and save the keystore file for you to use later.

## 2. Sign an XML Document

This feature applies a digital signature to an XML file.

-   **File Selection:** You need to select two files:
    1.  The **XML file** you want to sign.
    2.  The **Keystore file** (`.jks`) that contains your certificate.
-   **Credentials:** You must provide the passwords for the keystore and the specific certificate (alias) you want to use for signing.
-   **Output File:** The tool will create a new XML file with the digital signature embedded inside it. You can specify a suffix for the new file\\'s name (e.g., `_signed`).

## 3. Validate a Signature

This feature allows you to check if a signed XML document is valid.

-   **Load Signed File:** Simply load the XML file that contains a digital signature.
-   **Validation Check:** The tool will analyze the signature to verify two things:
    1.  **Authenticity:** That the signature was created by the certificate holder.
    2.  **Integrity:** That the document has not been changed in any way since it was signed.
-   **Result:** You will receive a clear message indicating whether the signature is **Valid** or **Invalid**.

## 4. Expert Mode - Advanced XML-DSig Operations

The Expert Mode provides comprehensive XML Digital Signature capabilities according to W3C XML-DSig standards. This
advanced interface is designed for professional users who need full control over cryptographic parameters and compliance
with enterprise security requirements.

### 4.1 Advanced Key Generation

The Expert Mode allows you to generate certificates with advanced cryptographic configurations:

#### Key Algorithms

- **RSA:** Most widely used, supports key sizes from 1024 to 4096 bits
- **DSA:** Digital Signature Algorithm, supports 1024 to 3072 bits
- **EC (Elliptic Curve):** Modern, efficient algorithm with 256, 384, or 521-bit keys
- **ECDSA:** Elliptic Curve Digital Signature Algorithm variant

#### Signature Algorithms

- **SHA1withRSA/DSA/ECDSA:** Legacy, not recommended for new applications
- **SHA256withRSA/DSA/ECDSA:** Current standard, widely supported
- **SHA384withRSA/ECDSA:** Enhanced security for high-value applications
- **SHA512withRSA/ECDSA:** Maximum security for critical systems

#### Advanced Certificate Options

- **Certificate Validity Period:** Configure custom validity periods (default: 365 days)
- **Subject Alternative Names (SAN):** Add DNS names, IP addresses for multi-domain certificates
- **Enhanced Logging:** Real-time progress tracking with timestamped results

### 4.2 Advanced XML Signing

Expert Mode provides full control over XML-DSig parameters:

#### Canonicalization Methods (C14N)

- **Standard C14N (http://www.w3.org/TR/2001/REC-xml-c14n-20010315):** Basic canonicalization
- **Standard C14N with Comments:** Preserves XML comments in canonical form
- **Exclusive C14N (http://www.w3.org/2001/10/xml-exc-c14n#):** Recommended for most applications
- **Exclusive C14N with Comments:** Exclusive canonicalization preserving comments
- **C14N 1.1:** Latest canonicalization standard with enhanced Unicode support

#### Transform Methods

- **Enveloped Signature Transform:** Removes the signature element itself from digest calculation
- **Canonicalization Transforms:** Apply specific canonicalization to referenced data
- **XPath Transforms:** Select specific parts of the document for signing
- **XSLT Transforms:** Apply XSLT transformation before signing
- **XPath Filter 2.0:** Advanced document subset selection

#### Digest Methods

- **SHA1:** Legacy, not recommended for new applications
- **SHA256:** Current standard, excellent security/performance balance
- **SHA384:** Enhanced security for sensitive applications
- **SHA512:** Maximum security for critical systems
- **MD5:** Legacy, not recommended (included for compatibility only)

#### Signature Types (XML-DSig Forms)

- **Enveloped:** Signature embedded within the signed document (most common)
- **Enveloping:** Signed document embedded within the signature structure
- **Detached:** Signature and document are separate files

### 4.3 Advanced Signature Validation

Expert Mode provides comprehensive validation with detailed reporting:

#### Validation Options

- **Certificate Chain Validation:** Verify complete certificate hierarchy up to root CA
- **Trust Anchor Validation:** Check signatures against configured trusted certificate authorities
- **Revocation Checking (OCSP/CRL):** Verify certificates haven't been revoked
- **Timestamp Validation:** Validate time-stamped signatures for long-term validity
- **Detailed Reports:** Generate comprehensive technical validation reports

#### Professional Features

- **Real-time Logging:** Timestamped progress and status messages in results area
- **Comprehensive Error Reporting:** Detailed technical error descriptions
- **Standards Compliance:** Full W3C XML-DSig and related security standards support
- **Enterprise Integration:** Suitable for high-security and regulated environments

### 4.4 Expert Mode Workflow

1. **Advanced Certificate Creation:**
    - Select cryptographic algorithms and parameters
    - Configure certificate validity and extensions
    - Generate certificates with professional-grade options

2. **Professional XML Signing:**
    - Load XML document and keystore files (drag-and-drop supported)
    - Configure canonicalization, transforms, and digest methods
    - Select signature type based on your security architecture
    - Sign with full XML-DSig compliance

3. **Comprehensive Validation:**
    - Load signed XML documents for analysis
    - Enable desired validation checks (chain, trust, revocation, timestamp)
    - Review detailed validation reports
    - Verify compliance with security policies

### 4.5 Technical Specifications

- **Standards Compliance:** W3C XML-Signature Syntax and Processing, XML-Encryption
- **Cryptographic Libraries:** BouncyCastle for advanced cryptographic operations
- **Supported Formats:** JKS keystores, PEM certificates, XML-DSig standard signatures
- **Security Features:** Secure random number generation, proper key storage, memory protection

## General Features

### User Interface

- **Tabbed Interface:** Organized workflow with Instructions, Create Certificate, Sign XML File, Validate Signed File,
  and Expert Mode tabs
- **File Loading:** Drag-and-drop support and file dialogs for all file operations
- **Progress Tracking:** Real-time feedback with timestamps in Expert Mode results area
- **User Feedback:** Clear success/error messages and comprehensive validation reports

### Security Features

- **Secure Key Generation:** Uses cryptographically secure random number generators
- **Memory Protection:** Sensitive data cleared from memory after use
- **Standards Compliance:** Full adherence to W3C XML-DSig specifications
- **Enterprise Ready:** Suitable for regulated and high-security environments

### File Format Support

- **Input:** XML documents, JKS keystores, PEM certificates
- **Output:** Signed XML documents with embedded signatures
- **Validation:** Any XML-DSig compliant signed document

## Use Cases

### Basic Users

- **Document Authenticity:** Sign important XML documents for authenticity verification
- **Simple Validation:** Quick verification of signed XML documents
- **Certificate Creation:** Generate basic certificates for personal or small business use

### Enterprise Users

- **Regulatory Compliance:** Full XML-DSig compliance for regulated industries
- **Advanced Security:** Multiple cryptographic algorithms and security levels
- **Integration:** Professional-grade signatures compatible with enterprise systems
- **Audit Trail:** Detailed logging and validation reports for compliance documentation

### Developers

- **Testing:** Generate test signatures with various algorithms and parameters
- **Standards Verification:** Validate XML-DSig implementation against W3C standards
- **Security Research:** Experiment with different cryptographic configurations

---

[Previous: PDF Generator (FOP)](fop-controller.md) | [Home](index.md)
