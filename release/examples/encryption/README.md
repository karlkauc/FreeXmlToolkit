# XML Encryption Examples

Demo material for **Sign & Encrypt → Encrypt / Decrypt / Inspect Encryption** and for
`fxt-cli encrypt` / `fxt-cli decrypt` (W3C XML Encryption 1.1).

| File | What it shows |
|------|---------------|
| `order.xml` | The plain document (an order with customer and payment data) |
| `order-payment-rsa.xml` | Only `payment` encrypted for `demo-rsa` (AES-256-GCM, RSA-OAEP, `Recipient`, an EncryptionProperty) |
| `order-customer-content-ec.xml` | Only the *content* of `customer` for `demo-ec` (ECDH-ES with ConcatKDF, AES key wrap) |
| `order-three-recipients.xml` | The whole document for three recipients: `demo-rsa`, `demo-ec` and the password - each one opens it |
| `order-shared-key.xml` | The whole document with the shared key `demo-shared.key` (KeyName `demo-shared-2026`, AES key wrap) |
| `order-password.xml` | The whole document with the password (PBKDF2 `xenc11:DerivedKey`) |
| `order-legacy-3des-rsa15.xml` | `payment` with TripleDES and RSA-1_5 - legacy algorithms, only for old partners (warnings) |

## Keys

| File | Content | Password |
|------|---------|----------|
| `demo-rsa.p12`, `demo-rsa.pem`, `demo-rsa-private.pem` | RSA 2048 keystore (alias `demo-rsa`), certificate, private key | keystore and key: `changeit` |
| `demo-ec.p12`, `demo-ec.pem`, `demo-ec-private.pem` | EC P-256 keystore (alias `demo-ec`), certificate, private key | keystore and key: `changeit` |
| `demo-shared.key` | 256-bit shared AES key (hex) | – |
| – | The password of `order-three-recipients.xml` and `order-password.xml` | `example` |

> **Not secrets:** demo keys generated only for these examples. Never use them for real data.

## Trying it out

- **Inspect:** open any `order-*.xml`, choose **Sign & Encrypt → Inspect Encryption**: what is
  encrypted, for whom and with which algorithms - without a key.
- **Decrypt:** open `order-payment-rsa.xml`, **Decrypt**, key source **Keystore** with `demo-rsa.p12`,
  alias `demo-rsa`, password `changeit`. Or **Private key (PEM)** with `demo-rsa-private.pem`.
- **Encrypt:** open `order.xml`, place the caret in `<payment>`, right-click → **Encrypt Element…**,
  add `demo-ec.pem` with **Certificate…** and press **Encrypt**.

On the command line:

```bash
fxt-cli decrypt --inspect order-three-recipients.xml
fxt-cli decrypt order-payment-rsa.xml --keystore demo-rsa.p12 --storepass changeit -o plain.xml
fxt-cli decrypt order-customer-content-ec.xml --private-key demo-ec-private.pem -o plain.xml
fxt-cli decrypt order-shared-key.xml --key-file demo-shared.key -o plain.xml
fxt-cli decrypt order-password.xml --password -o plain.xml          # prompts; type: example
fxt-cli encrypt order.xml -x "//*[local-name()='payment']" --cert demo-ec.pem -o mine.xml
```
