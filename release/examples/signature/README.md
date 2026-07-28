# XML Signature Example (FundsXML4)

Demo material for the **Signature** side panel: a FundsXML4 document signed with an
enveloped XML-DSig signature, plus the matching demo keystore.

| File | Purpose |
|------|---------|
| `FundsXML4_Equity_Fund_signed.xml` | `xml/FundsXML4_Equity_Fund.xml` with an enveloped `ds:Signature` (RSA-2048, SHA-256) |
| `fundsxml-demo_KeyStore.jks` | Java keystore (JKS) holding the demo key pair |
| `fundsxml-demo_publicKey.pem` | Public key / certificate (PEM) |
| `fundsxml-demo_privateKey.pem` | Private key (PEM) |

## Credentials

| Setting | Value |
|---------|-------|
| Keystore type | JKS |
| Alias | `fundsxml-demo` |
| Keystore password | `changeit` |
| Alias (key) password | `changeit` |

> **Not a secret:** this is a self-signed demo key generated solely for this example.
> Never use it for anything but trying out the Signature panel.

## Trying it out

1. Open the **Signature** activity in the side bar.
2. **Validate:** select `FundsXML4_Equity_Fund_signed.xml` — the signature verifies
   against the certificate embedded in the document.
3. **Sign:** pick any XML file (e.g. `../xml/FundsXML4_Equity_Fund.xml`), select
   `fundsxml-demo_KeyStore.jks`, enter alias and passwords from the table above.

## Notes

- **Never re-format `FundsXML4_Equity_Fund_signed.xml`** — pretty-printing changes the
  canonicalized bytes and breaks the digest, invalidating the signature.
- The demo certificate is valid for one year from generation (generated 2026-07-28,
  expires ~2027-07). Cryptographic signature validation still succeeds after expiry;
  trust checks in other tools may warn.
- Regenerate everything with the app's own `SignatureService` via
  `src/test/java/org/fxt/freexmltoolkit/SignatureExampleGeneratorTest.java`
  (remove `@Disabled`, run `./gradlew test --tests "SignatureExampleGeneratorTest"`).
- See also the official FundsXML example repository:
  <https://github.com/fundsxml/examples> (`XML_Signature/`).
