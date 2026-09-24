# Privacy & Anonymous Telemetry

FreeXmlToolkit sends **anonymous usage statistics** and **anonymous error reports** to help
improve the application. Both are switched **on by default** (opt-out) and can be turned off
at any time. Nothing is sent before you have seen the one-time notice that appears after the
first start.

## What is collected

Every request contains a small envelope describing the environment:

| Field | Example | Meaning |
|-------|---------|---------|
| `install_id` | `9b2d…` | A random UUID created on the first launch. It is not derived from your computer, user name or network and can be reset at any time. |
| `session_id` | `4f1c…` | A random UUID per application launch. |
| `app_version` | `2.2.0` | FreeXmlToolkit version. |
| `os_name` | `Windows` | Operating-system family only: Windows, macOS, Linux or Other. |
| `os_arch` | `x64` | CPU architecture family: x64, arm64 or other. |
| `java_major` | `25` | Java feature release. |
| `locale` | `de` | UI language only (no country / region). |

Each **usage event** describes one action:

| Field | Meaning |
|-------|---------|
| `event_type`, `category`, `status` | Which feature was used (e.g. `validate`, `xslt_transform`) and whether it succeeded, failed, was cancelled, … |
| `client_event_at` | When it happened (UTC). |
| `doc_kind` | Coarse document type derived from the file extension: `xml`, `xsd`, `xslt`, `schematron`, `json` or `other`. |
| `file_count`, `input_bytes`, `element_count`, `error_count`, `duration_ms` | Counts, sizes and timings. |
| `is_first_run` | Whether this is the first launch of the installation (on the `app_start` event). |
| `meta` | A few flat technical attributes such as the view mode (`text`, `tree`, `graphic`). |

Each **error event** additionally contains:

| Field | Meaning |
|-------|---------|
| `error_code` | The exception class, e.g. `java.lang.NullPointerException`. |
| `error_detail` | A *stack signature*: the chain of exception classes plus up to 20 code locations (`class#method:line`), mainly from FreeXmlToolkit's own code. |
| `error_hash` | A 16-character fingerprint of that signature, used to group identical problems. |
| `meta.where` | Where in the app it happened, e.g. `uncaught`, `executor`, or the title of the error dialog. |

Identical errors are sent only once per session (with a repeat counter), and at most 30 error
events are sent per session.

## What is never collected

- File names, folder names or paths
- Document content, XPath/XQuery expressions, schema or element names
- Exception **messages** (they often contain paths or content — only class names and code
  locations are used)
- User names, e-mail addresses, the "User Info" from the settings, license or machine identifiers
- Your IP address: the server derives only the country from it and never stores or logs the IP

## Error reports you send yourself

Error dialogs, the **About** dialog and the **Help** panel offer **Send error report…** /
**Report a problem…**. There you describe the problem in your own words and may optionally
leave a contact address if you would like a reply. The checkbox **Include technical details**
attaches the exception class, fingerprint and stack signature described above. A collapsible
**Preview** shows exactly the JSON that will be sent.

## How to see what is sent

**Settings ▸ Usage Statistics ▸ What is sent?** shows a sample of the exact payload (built
from the events currently waiting to be sent) together with your anonymous installation ID.

## How to switch it off

Open **Settings ▸ Usage Statistics** and clear

- **Send anonymous usage statistics** and/or
- **Send anonymous error reports**,

then click **Save Settings**. Events of the disabled kind that are still waiting to be sent are
deleted immediately.

Administrators can disable telemetry completely by starting the application with the Java
system property `-Dfxt.telemetry.disabled=true`, or by setting these keys in
`FreeXmlToolkit.properties`:

```properties
telemetry.usage.enabled=false
telemetry.errors.enabled=false
```

## How to reset the installation ID

**Settings ▸ Usage Statistics ▸ What is sent? ▸ Reset ID** replaces the anonymous installation
ID with a new random one. Data sent afterwards can no longer be linked to data sent before.

## Where the data goes

Events are sent over HTTPS to `https://telemetry.status20.net`, a self-hosted server operated by
the FreeXmlToolkit author in the EU (Helsinki, Finland). The data is not shared with third
parties and is used only to understand which features are used and which errors occur.
Individual events are deleted automatically after **90 days**; long-term, only anonymous,
aggregated daily totals (counts per feature, version and operating system) are kept. Error
reports you send yourself via **Send error report…** are kept until they have been reviewed.
The country is derived from your IP address using the free DB-IP country database
(IP Geolocation by [DB-IP](https://db-ip.com), CC BY 4.0); the IP address itself is never
stored or logged.

## Offline use and proxies

Events are queued locally in `~/.freeXmlToolkit/telemetry-queue.jsonl` (at most 5000 events and
14 days, oldest dropped first) and sent in small batches about every five minutes. Requests use
the proxy configured in **Settings ▸ HTTP Proxy**. If the server cannot be reached, sending is
retried later with increasing pauses (up to six hours); the application is never slowed down.
