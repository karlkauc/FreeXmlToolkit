# Threat Model

This page is for security reviewers and administrators. It lists what an attacker can try with
the files FreeXmlToolkit opens, which defaults protect against it, which settings change them,
and where the protection ends. The user-facing description of the protections is on
[Security Features](SECURITY.md); every network connection is listed on
[Operation without internet](offline-operation.md).

## Scope and assumptions

FreeXmlToolkit is a desktop application. It runs with the rights of the signed-in user and has
no server component, no listening port and no account system. The assets to protect are the
user's files, the credentials the application stores (proxy password) and the machine's network
position (it may reach intranet hosts that an attacker cannot).

**Untrusted input** is any file the user opens or a document refers to: XML instances, XSD,
XSLT, XQuery, Schematron, XProc pipelines, JSON, HTML previews and the URLs inside them
(`xsi:schemaLocation`, `xs:import`, `doc()`, `$ref`). Running a stylesheet, query or pipeline is
running code: treat them like scripts.

**Trusted**: the application package itself (signed on macOS; checksums and an SBOM with every
release, see [Licenses](licenses.md#software-bill-of-materials-sbom)) and the update server
(GitHub releases, see below).

## Threats and mitigations

| Threat | Mitigation (default) | Setting |
|---|---|---|
| **XXE**: an XML document reads local files or URLs through external entities | Parsers are created by one factory with external general and parameter entities off, external DTD loading off and JAXP secure processing on. In the editor, internal entities are not expanded. A DOCTYPE is allowed (internal subsets are common in real files). XML signature files reject any DOCTYPE. The parser Saxon reads documents with (XPath, XQuery, XSLT, grid formulas and filters) gets the same settings. | – |
| **Entity expansion** ("billion laughs") | The parsers for documents and schemas run in secure-processing mode, which stops a document after 100,000 entity expansions; this also holds when Saxon reads a document (XPath, XQuery, XSLT, grid formulas and filters). The editor keeps internal entities as references; the StAX parsers do not read DTDs. | – |
| **DTDs named by schemas** | Resolved to an empty document; never downloaded. | – |
| **Remote schemas** (`schemaLocation`, `xs:import`) | Downloaded over http/https only from public addresses: loopback, private (10/8, 172.16/12, 192.168/16), link-local, cloud metadata (169.254.169.254) and multicast addresses are refused, at most 5 redirects. Downloads are checked to be a schema before they are cached. | `-Dfxt.schema.namespaceFallback=false`, `-Dfxt.schema.localCopies=false`; Schema Library / OASIS catalogs map URLs to local files |
| **Stylesheets calling Java** | Saxon-HE has no reflexive extension calls; external functions are switched off in the transformation engine. | `security.xslt.allow.extensions` (Settings, default off) |
| **Stylesheets and queries reading the network** | In XSLT, XQuery and XPath (Query Console, grid formulas and filters), `doc()`, `document()`, `unparsed-text()` and `unparsed-text-lines()` refuse http, https and ftp (URLs mapped by the Schema Library or a catalog excepted); `collection()` serves only the files selected for the run. | – |
| **XSL-FO reading or writing remote resources** | FOP's resource resolver refuses remote reads and writes. | – |
| **Forged or weak XML signatures** | Verification uses the JDK's XML DSig with its secure-validation default; SHA-1 is rejected; references resolve only to plain file names in the signature's own folder. | – |
| **Man in the middle on updates** | The update check, the update download and the SHA-256 digest it is checked against always validate the server certificate, also when "Trust all certificates" is on. | `update.requireChecksum=true` refuses updates without a published digest |
| **Man in the middle on other downloads** | TLS certificates are validated unless the user switches on "Trust all certificates" (meant for proxies that re-sign traffic); it applies per connection, never to the JVM defaults. | `ssl.trustAllCerts` (default off) |
| **Code opening its own connections** (proxy, TLS settings bypassed) | All HTTP goes through one service; a build test fails when any other class opens a connection. | – |
| **Stored proxy password** | Encrypted with AES-256-GCM; the key is derived (PBKDF2-SHA256) from machine identifiers, so the properties file alone does not reveal it on another machine. This protects against casual disclosure, not against code running as the same user on the same machine. | – |
| **YAML that expands or loops** (alias bombs, recursive aliases) when converting to JSON or XML | YAML is read with the core schema only (no Java objects are created from tags); at most 50 aliases per document, duplicate keys and recursive aliases are refused. | – |
| **Path traversal in file names from documents** | Paths are validated (parent traversals, system directories, encoded sequences, null bytes, symlink escapes). | – |
| **Telemetry leaking data** | Anonymous counts and error reports without file contents; documented on [Privacy & Telemetry](telemetry.md). | `FXT_TELEMETRY_DISABLED=1` or `DO_NOT_TRACK=1` (machine-wide), Settings |

## Known limits

These are not covered by the protections above. Keep them in mind when files come from
untrusted sources:

- **XProc pipelines** run unrestricted: `p:http-request` and remote `href`s are fetched (through
  the configured proxy). Run only pipelines you trust.
- **HTML previews** (HTML files, XSLT output) are shown in an embedded WebKit view with
  JavaScript enabled, and it loads the remote resources the page refers to.
- **Schematron** is compiled and run without extra restrictions beyond Saxon's defaults.
- **Opening a document downloads its schema.** An `xsi:schemaLocation` with an http(s) URL is
  fetched when the file is opened (from public addresses only; the Schema Library and catalogs
  are asked first). The download reveals to that server that the file was opened.
- **Host names that do not resolve** on the client are not refused by the private-address check;
  the connection is left to fail on its own.
- **Same-user attacks** (malware running as the signed-in user) are out of scope: it can read
  the settings, caches and the derived key like the application itself.

## Hardened configuration

For machines that process untrusted files or must not contact the internet:

| Goal | How |
|---|---|
| Users cannot change the settings below | Put them into the machine-wide policy file, see [Administration & Policies](administration.md) |
| No telemetry | Environment variable `FXT_TELEMETRY_DISABLED=1` (machine-wide), or `fxt.telemetry.disabled=true` in the policy file |
| No update check | `update.check.enabled=false` in the properties file (Settings → Updates); `update.allowed=false` in the policy file also removes manual checks and installs |
| Schemas only from local files | Schema Library entries or an OASIS catalog for every schema; `-Dfxt.schema.namespaceFallback=false` |
| Nothing written next to schemas | `-Dfxt.schema.localCopies=false` |
| Certificate checks everywhere | Leave "Trust all certificates" off |
| No Java from stylesheets | Leave `security.xslt.allow.extensions` off (default) |

The `-D…` options are Java options; the policy file takes them as `fxt.…=value` lines, and
[Operation without internet](offline-operation.md#java-options-for-an-installed-application)
shows where else to set them for an installed application, and lists every connection and cache.

## Reporting a vulnerability

See the [Security Policy](security-policy.md).
