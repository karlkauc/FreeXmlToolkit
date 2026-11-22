# Sign and encrypt

Clicking **Sign & Encrypt** in the activity bar opens one page, **Sign & Encrypt**, as a tab in the
main editor area. The side panel on the left only lists the actions in order of use - **Sign XML
File**, **Validate Signature**, **Encrypt**, **Decrypt**, **Inspect Encryption**, **Create
Certificate** and **Expert Mode** (the encryption actions: [XML Encryption](encryption.md)). Click one to show it on
the page; the current action is highlighted, and the status message of the previous action is
cleared.

The page is built the same way for every action, from top to bottom:

1. A **header** naming the selected action.
2. **DOCUMENT** - the XML file to work on: the active document by default, **Browse** picks a
   different file. Shown for **Sign XML File**, **Validate Signature** and **Expert Mode**.
3. **KEYSTORE** - the keystore file, **Keystore alias**, **Keystore password** and **Alias
   password**, entered only once and shared by the actions that need them (**Sign XML File** and
   **Create Certificate**). The keystore slot shows the file name on its own line (full path as
   tooltip); while none is chosen it reads *"No keystore — choose or drop a .jks / .p12"*, and
   clicking it works like **Change**.
4. The **form of the action** with its primary button (for example **Sign Document**).
5. The **status line** with the outcome of the last action.

Secondary actions such as **Validate (Details)** are labelled rows (icon + name) below the primary
button. The keystore row also carries a **star menu** that lists your favorited keystores -
keystore files (`.jks`, `.p12`, `.pfx`, `.keystore`) are their own **Keystore**
favorite type with a lock icon (see [Favorites](../edit-xml/favorites.md)):

- **Sign XML File** *(default)* - The document to sign (DOCUMENT row), the keystore (KEYSTORE),
  the signature type and algorithm (RSA-SHA256 · C14N exclusive), and a
  **Sign Document** button. Signature types: **Enveloped** (the signature is inserted into the
  document, written as `name.signed.xml`), **Enveloping** (the content is wrapped in a
  `ds:Object` inside the signature, also `name.signed.xml`; comments and processing
  instructions outside the root element are not kept) and **Detached** (a separate
  `name.sig.xml` that references the original - validate that file and keep both in the same
  folder; the card shows this hint when Detached is selected). The result is opened in a
  background tab; the page stays in front and offers **Validate this signature**, which switches
  to Validate Signature with the signed file and runs the check. The saved file on disk is signed - if the document has unsaved changes, you are
  first asked whether to save them (see [Tools that read the saved file](../edit-xml/working-with-files.md#tools-that-read-the-saved-file)).
  **Show certificate details** loads the keystore's certificate and shows the mockup's
  certificate inspector: subject (CN/O/C) with a self-signed/CA badge, the validity window
  with a *"Valid · N days remaining"* banner (red when expired), serial, signature
  algorithm, key usage, and the **SHA-256 fingerprint** with a copy button.
- **Validate Signature** - **Run Validation** checks the signature of the document in the
  page's **DOCUMENT** row (the active document unless you picked another one with **Browse**);
  **Validate (Details)** opens a detailed report (validity + signing-certificate details).
  The result is explained in plain language: a valid signature
  shows a green status; a document without a signature shows a red hint suggesting you sign
  it first; an invalid signature opens an error dialog that explains the document was
  modified after signing and names what failed (the signature value or a specific
  reference); a signature whose certificate cannot be used (not embedded, only referenced,
  or an unsupported algorithm) opens a dialog telling you to ask the sender for a signature
  that embeds an RSA X.509 certificate - or use **Validate (Details)**; signatures using
  the weak SHA-1 algorithm are rejected for security (re-sign with SHA-256 or SHA-512).
  Any other error shows a dialog with collapsible technical details.
- **Create Certificate** - Creates a self-signed certificate / keystore from the DN fields,
  using the alias and passwords from the KEYSTORE row. The new keystore is selected
  automatically so you can sign immediately.
- **Expert Mode** - Full PKIX trust validation of the document in the DOCUMENT row: choose a
  **trust store** (defaults to the JVM's built-in `cacerts`; the trust store row also has a **star
  menu** with your favorited keystores), enter its **Trust store password** if it has one (the
  field is optional and separate from the keystore password), optionally tick **Check revocation
  (OCSP/CRL)**, then **Validate (Trust)** produces a trust report (trusted / trust anchor / revocation /
  timestamp).

The **KEYSTORE row** and Expert Mode's **trust store row** are also
**drop targets**: drop a keystore file (`.jks`, `.keystore`, `.p12`, `.pfx`) from your
file manager onto either row to select it - the row glows **green** while a loadable file
hovers over it and **red** for a wrong file type (which is rejected), and the drop
behaves exactly like choosing the file via **Change** or a favorite.

Missing inputs are highlighted: signing without a keystore marks the
keystore entry in red, and a blank alias or password is marked in red when you sign or
create a certificate. The highlight disappears as soon as you start typing in the field.

See [XML Digital Signatures](signatures.md) for full details.

## Next steps

- [XML digital signatures](signatures.md) - create a certificate, sign and verify step by step
- [XML Encryption](encryption.md) - recipients, algorithms, decrypting and inspecting
