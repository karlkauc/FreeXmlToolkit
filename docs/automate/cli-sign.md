# fxt-cli: sign and encrypt

`sign`, `verify`, `encrypt` and `decrypt`. Installation, exit codes and logging are on the [overview page](command-line.md).

## sign

Signs an XML document (XML digital signature), like [Sign & Encrypt → Sign](../guides/sign-encrypt/signatures.md).

```bash
fxt-cli sign INPUT --keystore FILE [--alias ALIAS] (--storepass[=PW] | --storepass-env VAR) [options]
```

| Option | Meaning |
|---|---|
| `--keystore FILE` | JKS or PKCS#12 keystore with the signing key |
| `--alias ALIAS` | The key's alias (default: the keystore's only key) |
| `--storepass`, `--storepass-env VAR` | The keystore password: prompted when `--storepass` has no value, or read from an environment variable (preferred in scripts) |
| `--keypass`, `--keypass-env VAR` | The key entry's password (default: the keystore password) |
| `--type TYPE` | `enveloped` (default), `enveloping` or `detached` |
| `--signature-alg ALG` | `rsa-sha256` (default for RSA keys), `rsa-sha384`, `rsa-sha512`, `rsa-pss-sha256/384/512`, `ecdsa-sha256` (default for EC keys), `ecdsa-sha384`, `ecdsa-sha512` |
| `--digest-alg ALG` | `sha256` (default), `sha384`, `sha512` |
| `--c14n METHOD` | `exclusive` (default), `exclusive-comments`, `inclusive`, `inclusive-comments`, `inclusive-11` |
| `-o FILE` | Output (default: `INPUT.signed.xml`, detached: `INPUT.sig.xml`) |

## verify

Checks XML signatures; with a trust store also the signer's certificate chain (PKIX).

```bash
fxt-cli verify INPUT... [--trust-store FILE (--storepass… ) | --trust-cacerts] [--revocation] [-f text|json]
```

Exit code `0` when every file is valid (and trusted), `1` when one is not, `3` on errors.

## encrypt

Encrypts XML according to W3C XML Encryption 1.1, like
[Sign & Encrypt → Encrypt](../guides/sign-encrypt/encryption.md).

```bash
fxt-cli encrypt INPUT [-x XPATH]... [--content | --binary] RECIPIENTS... [options]
```

| Option | Meaning |
|---|---|
| `-x`, `--xpath XPATH` | Elements to encrypt (repeatable; default: the document element) |
| `--ns PREFIX=URI` | Namespace prefixes for `--xpath` (the document element's are known) |
| `--content` | Encrypt only the content of the elements |
| `--binary`, `--mime-type TYPE` | Encrypt INPUT as binary data (any file) |
| `--data-alg ALG` | Data algorithm (default `aes256-gcm`) |
| `--cert FILE` | A recipient certificate, PEM or DER (repeatable) |
| `--public-key FILE` | A recipient public key, PEM (repeatable) |
| `--keystore FILE --alias ALIAS` | Recipient certificates from a keystore (`--alias` repeatable; `--storepass…`) |
| `--key-file FILE`, `--key-name NAME` | A shared symmetric key (hex, Base64 or raw) and its KeyName |
| `--key-wrap ALG` | Key wrap for key files, passwords and key agreement; `none` uses the key file as data key |
| `--password`, `--password-env VAR` | A password recipient (PBKDF2); `--iterations N`, `--prf sha256|sha384|sha512` |
| `--key-transport ALG`, `--oaep-digest`, `--mgf` | RSA: `rsa-oaep` (default), `rsa-oaep-mgf1p`, `rsa-1_5`; OAEP digest and MGF |
| `--kdf concatkdf|hkdf`, `--kdf-digest` | Key derivation after ECDH-ES / X25519 / X448 |
| `--x509 KIND` | How a certificate is named: `issuer-serial` (default), `certificate`, `ski`, `subject`, `none` |
| `--recipient-name NAME`, `--property NAME=VALUE` | `Recipient` attribute; EncryptionProperties (repeatable) |
| `--keys-outside` | EncryptedKeys next to the data with a ReferenceList |
| `--list-algorithms` | List all algorithms with their URIs |
| `-o FILE` | Output (default: `INPUT.encrypted.xml`) |

Legacy algorithms (RSA-1_5, TripleDES, CBC modes) work but print a warning.

## decrypt

Decrypts every EncryptedData the given keys open (also several layers), or describes an encrypted
document without a key.

```bash
fxt-cli decrypt INPUT (--keystore FILE … | --private-key FILE | --key-file FILE | --password) [--id ID]... [-o FILE]
fxt-cli decrypt --inspect [-f text|json] INPUT
```

| Option | Meaning |
|---|---|
| `--keystore FILE`, `--alias`, `--storepass…`, `--keypass…` | Your private key from a keystore |
| `--private-key FILE` | Your private key as PEM (`--keypass` for an encrypted one; repeatable) |
| `--key-file FILE`, `--key-name NAME` | A shared symmetric key (repeatable) |
| `--password`, `--password-env VAR` | The document's password |
| `--id ID` | Only the EncryptedData with this Id (repeatable) |
| `--inspect`, `-f text|json` | Describe the encryption instead of decrypting |
| `-o FILE` | Output (default: `INPUT.decrypted.xml`; binary data `INPUT.decrypted.bin`) |

Exit code `0` when everything selected was decrypted, `1` when no key fits or data stays encrypted,
`3` on errors.
