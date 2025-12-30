# Security Features

> **Last Updated:** December 2025 | **Version:** 1.1.0

This page describes the security measures built into FreeXmlToolkit to protect you from XML-based attacks.

---

## Overview

When working with XML files, there are several potential security risks - especially when processing untrusted documents or stylesheets. FreeXmlToolkit includes built-in protections against these common attack vectors:

| Protection | What It Prevents |
|------------|------------------|
| [XXE Protection](#xxe-xml-external-entity-protection) | Malicious files reading data from your computer |
| [XSLT/XQuery Extension Security](#xsltxquery-extension-security) | Untrusted stylesheets running code on your system |
| [SSRF Protection](#ssrf-server-side-request-forgery-protection) | Documents accessing your internal network |
| [Path Traversal Protection](#path-traversal-protection) | Files accessing restricted system directories |
| [XPath Injection Protection](#xpath-injection-protection) | Malicious input breaking XPath queries |

These protections work automatically in the background - you do not need to configure them for normal use.

---

## XXE (XML External Entity) Protection

### What Is XXE?

XXE is a type of attack where a malicious XML document tries to read files from your computer or access network resources. This happens through special "entity" declarations in the XML that reference external files.

### Example of a Malicious XML

This is what a dangerous XML file might look like (FreeXmlToolkit blocks this):

```xml
<?xml version="1.0"?>
<!DOCTYPE data [
  <!ENTITY steal SYSTEM "file:///etc/passwd">
]>
<data>&steal;</data>
```

If processed without protection, this could expose sensitive files from your computer.

### What FreeXmlToolkit Blocks

| Attack Type | Description | Status |
|-------------|-------------|--------|
| External file access | Reading local files via `file://` URIs | Blocked |
| External DTD loading | Loading DTD definitions from remote servers | Blocked |
| Parameter entities | Using `%entity;` to include external content | Blocked |
| Entity expansion bombs | "Billion laughs" denial-of-service attacks | Limited |

### How It Works

All XML parsing in FreeXmlToolkit uses secure configurations that:
- Disable external general entities
- Disable external parameter entities
- Disable external DTD loading
- Limit entity expansion

### What This Means for You

**Normal XML files work without issues.** You can:
- Open and edit any XML file
- Validate against XSD schemas
- Transform with XSLT stylesheets
- Work with XML that contains internal (non-external) entities

**DTDs with external references are not resolved.** If you have an XML file that relies on an external DTD for entity definitions, those entities will appear as empty or cause parsing to fail. This is intentional for security.

---

## XSLT/XQuery Extension Security

### What Are XSLT Extensions?

XSLT 3.0 and XQuery can include "extension functions" that call Java code directly from your stylesheet. While this is powerful for advanced users, it can be dangerous if you process untrusted stylesheets.

### Example of a Dangerous Stylesheet

This XSLT tries to execute system commands (FreeXmlToolkit blocks this by default):

```xml
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:rt="java:java.lang.Runtime">
    <xsl:template match="/">
        <xsl:variable name="runtime" select="rt:getRuntime()"/>
        <xsl:variable name="proc" select="rt:exec($runtime, 'whoami')"/>
    </xsl:template>
</xsl:stylesheet>
```

### What Is Blocked by Default

| Extension Type | Description | Default Status |
|----------------|-------------|----------------|
| Java namespace (`java:`) | Direct calls to Java classes | Blocked |
| Reflexive extensions | Runtime method invocation | Blocked |
| External functions | Custom extension functions | Blocked |

### How It Works

FreeXmlToolkit uses Saxon for XSLT/XQuery processing. By default, the `ALLOW_EXTERNAL_FUNCTIONS` feature is disabled, preventing stylesheets from executing arbitrary Java code.

### Enabling Extensions (Advanced Users)

If you need to use Java extensions in your XSLT transformations:

1. Open the application settings
2. Find the security section
3. Enable "Allow XSLT Extensions"

**Warning:** Only enable this if you trust all XSLT stylesheets you will process. Malicious stylesheets could:
- Read files from your computer
- Execute system commands
- Access network resources
- Modify or delete files

The setting is stored as:
```
security.xslt.allow.extensions=true
```

When extensions are enabled, you will see a warning message in the application log.

---

## SSRF (Server-Side Request Forgery) Protection

### What Is SSRF?

SSRF attacks trick an application into making requests to internal network resources. In XML processing, this can happen when schemas or stylesheets reference URLs.

### What FreeXmlToolkit Blocks

When loading remote schemas (via `xs:import` or `xs:include` with HTTP URLs), FreeXmlToolkit blocks access to:

| Address Type | Examples | Why Blocked |
|--------------|----------|-------------|
| Localhost | `127.0.0.1`, `localhost`, `::1` | Prevents access to local services |
| Private networks (Class A) | `10.0.0.0` - `10.255.255.255` | Blocks internal network access |
| Private networks (Class B) | `172.16.0.0` - `172.31.255.255` | Blocks internal network access |
| Private networks (Class C) | `192.168.0.0` - `192.168.255.255` | Blocks internal network access |
| Link-local addresses | `169.254.0.0` - `169.254.255.255` | Blocks auto-configured addresses |
| Cloud metadata endpoints | `169.254.169.254` | Blocks AWS/Azure/GCP metadata |
| Multicast addresses | Various | Blocks broadcast addresses |

### What Is Allowed

| Address Type | Examples | Status |
|--------------|----------|--------|
| Public internet URLs | `https://www.w3.org/2001/XMLSchema.xsd` | Allowed |
| Local file URLs | `file:///path/to/schema.xsd` | Allowed |
| Only HTTP/HTTPS | `http://`, `https://` | Other protocols blocked |

### What This Means for You

**Public schemas work normally.** You can reference standard schemas from W3C and other public sources.

**Internal network schemas are blocked.** If you need to load schemas from:
- Your company's internal server (e.g., `http://192.168.1.100/schemas/`)
- Localhost development servers (e.g., `http://localhost:8080/`)

You should:
1. Download the schema files to your local machine
2. Reference them using file paths or `file://` URLs
3. Place them in the same directory as your XML files for relative references

---

## Path Traversal Protection

### What Is Path Traversal?

Path traversal attacks use special characters like `../` to access files outside the intended directory. This could allow a malicious document to read or write to sensitive system locations.

### What FreeXmlToolkit Blocks

| Protection | Description |
|------------|-------------|
| Excessive parent traversals | More than 5 levels of `../` are flagged |
| System directory access | Writes to `/etc`, `/bin`, `C:\Windows`, etc. |
| Encoded sequences | URL-encoded traversals like `%2e%2e/` |
| Double-encoded sequences | `%252e` and similar bypass attempts |
| Null byte injection | Paths containing `\0` characters |
| Symbolic link escapes | Following symlinks outside base directories |

### Protected System Directories

**Windows:**
- `C:\Windows\`
- `C:\Program Files\`
- `C:\ProgramData\`

**Linux/macOS:**
- `/etc/`
- `/bin/`, `/sbin/`
- `/usr/bin/`, `/usr/sbin/`
- `/var/`
- `/root/`
- `/boot/`

### Affected Features

Path validation applies to:
- Schema includes (`xs:include`, `xs:import` with relative paths)
- Linked file detection (auto-linking in the unified editor)
- Export paths (saving output files)
- Any relative file references in XML documents

### What This Means for You

**Normal file operations work fine.** You can:
- Open files anywhere on your computer
- Save files to your documents folder
- Use relative paths within your project directories

**Suspicious paths are blocked.** If you see a path traversal warning, check that your schema or stylesheet references do not contain unusual `../` sequences or try to access system directories.

---

## XPath Injection Protection

### What Is XPath Injection?

XPath injection is similar to SQL injection - it happens when user input is inserted directly into XPath queries without proper escaping. This could allow malicious input to modify the query's behavior.

### How FreeXmlToolkit Protects You

When you use the XPath/XQuery snippet system with parameters, all parameter values are automatically escaped before being inserted into queries.

### How Value Escaping Works

| Input Contains | Escape Method |
|----------------|---------------|
| No quotes | Wrapped in single quotes: `'value'` |
| Single quotes only | Wrapped in double quotes: `"value"` |
| Double quotes only | Wrapped in single quotes: `'value'` |
| Both quote types | Uses `concat()` function for safe combination |

### Example

If you have a snippet with parameter `${searchTerm}` and the user enters:

```
O'Brien "Bob"
```

The system generates:

```xpath
concat('O', "'", 'Brien "Bob"')
```

This prevents the user input from breaking out of the string literal and modifying the query structure.

### What This Means for You

**This protection is automatic and transparent.** You do not need to do anything special - just use the parameter syntax `${paramName}` in your snippets, and values will be properly escaped.

---

## How to Configure Security Settings

### Finding Security Settings

1. Open FreeXmlToolkit
2. Go to **Settings** (or use the keyboard shortcut)
3. Look for the **Security** section

### Available Settings

| Setting | Description | Default |
|---------|-------------|---------|
| Allow XSLT Extensions | Enable Java extension functions in XSLT | **Off** (secure) |

### Security Property Reference

For advanced users, security settings are stored in the application properties file:

| Property | Values | Description |
|----------|--------|-------------|
| `security.xslt.allow.extensions` | `true` / `false` | Enable/disable Java extensions in XSLT |

### Best Practices

1. **Keep extensions disabled** unless you specifically need them
2. **Only process trusted stylesheets** if you enable extensions
3. **Download remote schemas** to local files when working with internal network resources
4. **Review XML files** before processing if they come from untrusted sources
5. **Check validation errors** - they may indicate blocked security threats

---

## Troubleshooting

### "External entity not resolved"

**Cause:** Your XML references an external DTD or entity that was blocked for security.

**Solution:** If you need the entity definitions, manually include them in your XML file or convert the external DTD to a local file.

### "URL blocked for security reasons"

**Cause:** You tried to load a schema from a localhost or private network address.

**Solution:** Download the schema file to your local machine and reference it as a local file.

### "XSLT extension function not available"

**Cause:** Your stylesheet uses Java extension functions, which are disabled by default.

**Solution:** If you trust the stylesheet, enable XSLT extensions in settings. Otherwise, modify the stylesheet to not use extensions.

### "Path traversal detected"

**Cause:** A file reference contains suspicious `../` sequences that would access files outside the expected directory.

**Solution:** Check your schema imports and includes for unusual paths. Use absolute paths or properly relative paths that stay within your project directory.

---

## Technical Reference

For developers and security professionals, here are the specific protections implemented:

### XML Parser Configuration

All XML parsers use these security features:
- `http://apache.org/xml/features/disallow-doctype-decl` = false (DOCTYPE allowed, but entities disabled)
- `http://xml.org/sax/features/external-general-entities` = false
- `http://xml.org/sax/features/external-parameter-entities` = false
- `http://apache.org/xml/features/nonvalidating/load-external-dtd` = false
- `javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING` = true
- Entity reference expansion = disabled

### XSLT/XQuery Configuration (Saxon)

- `Feature.ALLOW_EXTERNAL_FUNCTIONS` = false (by default)
- Configurable via application settings

### URL Validation

Uses Java's `InetAddress` class to check:
- `isLoopbackAddress()` - blocks localhost
- `isLinkLocalAddress()` - blocks 169.254.x.x
- `isSiteLocalAddress()` - blocks private networks
- Special check for 169.254.169.254 (cloud metadata)

### Path Validation

- Canonical path comparison for traversal detection
- Regex patterns for encoded sequences
- Blocklist of system directories
- Symbolic link resolution for escape detection

---

## Navigation

| Previous | Home | Next |
|----------|------|------|
| [Technology Stack](technology-stack.md) | [Home](index.md) | [Licenses](licenses.md) |

**All Pages:** [XML Editor](xml-editor.md) | [XML Features](xml-editor-features.md) | [XSD Tools](xsd-tools.md) | [XSD Validation](xsd-validation.md) | [XSLT](xslt-viewer.md) | [XSLT Developer](xslt-developer.md) | [FOP/PDF](pdf-generator.md) | [Signatures](digital-signatures.md) | [IntelliSense](context-sensitive-intellisense.md) | [Schematron](schematron-support.md) | [Favorites](favorites-system.md) | [Templates](template-management.md) | [Tech Stack](technology-stack.md) | **Security** | [Licenses](licenses.md)
