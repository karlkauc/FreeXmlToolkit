# Security Policy

## Supported Versions

Security fixes are provided for the **latest release** of FreeXmlToolkit. Please update to the
[latest version](https://github.com/karlkauc/FreeXmlToolkit/releases/latest) before reporting an issue
(**Help → Check for Updates…**).

| Version         | Supported |
|-----------------|-----------|
| Latest release  | ✅        |
| Older releases  | ❌        |

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub issues.**

Report them privately via
[GitHub private vulnerability reporting](https://github.com/karlkauc/FreeXmlToolkit/security/advisories/new).
If that is not possible, send an e-mail to **karl.kauc@gmail.com** with the subject
`[SECURITY] FreeXmlToolkit`.

Please include:

- The affected version and operating system
- The type of issue (e.g. XXE, path traversal, code execution through a stylesheet, insecure update)
- Step-by-step instructions and, if possible, a minimal proof-of-concept file
- The impact you see — what an attacker could achieve

## What to Expect

- You will receive an acknowledgement within **7 days**.
- We will investigate, keep you informed about the progress and agree on a disclosure date with you.
- Fixes are published in a new release; with your consent you will be credited in the release notes.

## Built-in Protections

FreeXmlToolkit protects against common XML attacks (XXE, SSRF, path traversal, untrusted XSLT/XQuery
extensions, tampered updates) and keeps its optional AI features in check (a sandbox for code an AI agent
runs through the MCP server, data levels for the AI assistant). See
[Security Features](https://karlkauc.github.io/FreeXmlToolkit/SECURITY/) and the
[Threat Model](https://karlkauc.github.io/FreeXmlToolkit/threat-model/) in the user guide for details.
