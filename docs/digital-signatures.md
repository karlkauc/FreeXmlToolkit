# XML Digital Signatures

> **Last Updated:** November 2025 | **Version:** 1.0.0

This tool lets you digitally sign XML documents and verify signatures. A digital signature proves that a document is
authentic and hasn't been changed.

---

## Overview

![Digital Signatures Overview](img/signature-sign.png)
*The digital signature interface*

### What Can You Do?

1. **Create Certificates** - Generate your own digital ID
2. **Sign Documents** - Add a digital signature to XML files
3. **Verify Signatures** - Check if signed documents are valid

---

## 1. Create a Certificate

Before signing documents, you need a digital certificate (like a digital ID card).

![Create Certificate](img/signature-create-cert.png)
*Screenshot placeholder: Certificate creation form*

### How to Create a Certificate

1. Go to the **"Create Certificate"** tab
2. Fill in your details:
    - Name
    - Organization
    - Country
3. Set a password for the certificate
4. Click **"Create"**
5. Save the keystore file (`.jks`) - you'll need this for signing

**Important:** Remember your password! You'll need it every time you sign a document.

---

## 2. Sign an XML Document

![Sign Document](img/signature-sign-process.png)
*Screenshot placeholder: Document signing interface*

### How to Sign a Document

1. Go to the **"Sign XML File"** tab
2. Select the **XML file** you want to sign
3. Select your **keystore file** (`.jks`)
4. Enter your passwords
5. Click **"Sign"**
6. A new signed XML file is created

The signed file includes the original content plus a digital signature.

---

## 3. Verify a Signature

![Verify Signature](img/signature-validation.png)
*Verifying a signed document*

### How to Verify a Signature

1. Go to the **"Validate Signed File"** tab
2. Select the signed XML file
3. Click **"Validate"**
4. See the result: **Valid** or **Invalid**

### What the Verification Checks

| Check            | What It Means                                       |
|------------------|-----------------------------------------------------|
| **Authenticity** | The signature was created by the certificate holder |
| **Integrity**    | The document hasn't been changed since signing      |

---

## Expert Mode (Advanced)

For users who need more control, the Expert Mode offers additional options:

![Expert Mode](img/signature-expert.png)
*Screenshot placeholder: Expert mode interface*

### Advanced Features

- **Different encryption methods** - Choose from various security levels
- **Custom certificate options** - Set validity periods and extensions
- **Detailed validation** - Get comprehensive technical reports
- **Advanced signature types** - Choose how the signature is embedded

---

## Tips

- **Keep your keystore safe** - It's your digital identity
- **Remember your passwords** - They cannot be recovered
- **Signed files are new files** - The original is not modified
- **Check validity regularly** - Signatures can expire

---

## Common Questions

### What if I lose my password?

Unfortunately, passwords cannot be recovered. You'll need to create a new certificate.

### What file formats are supported?

- **Input**: XML files, JKS keystores
- **Output**: Signed XML files with embedded signatures

### Can I sign PDFs?

No, this tool is specifically for XML documents.

---

## Navigation

| Previous                                | Home             | Next                                                 |
|-----------------------------------------|------------------|------------------------------------------------------|
| [PDF Generator (FOP)](pdf-generator.md) | [Home](index.md) | [Auto-Completion](context-sensitive-intellisense.md) |

**All Pages:
** [XML Editor](xml-editor.md) | [XML Features](xml-editor-features.md) | [XSD Tools](xsd-tools.md) | [XSD Validation](xsd-validation.md) | [XSLT](xslt-viewer.md) | [FOP/PDF](pdf-generator.md) | [Signatures](digital-signatures.md) | [IntelliSense](context-sensitive-intellisense.md) | [Schematron](schematron-support.md) | [Favorites](favorites-system.md) | [Templates](template-management.md) | [Tech Stack](technology-stack.md) | [Licenses](licenses.md)
