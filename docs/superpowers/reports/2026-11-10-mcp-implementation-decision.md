# MCP implementation decision (Phase 1, Task 1 / decision D4)

**Date:** 2026-11-10 · **Base:** `main` at `333269060` · **Status:** recommendation, awaiting the maintainer's approval

## What was measured

### Protocol (step 1)

- Latest revision: **`2026-07-28`** (`https://modelcontextprotocol.io/specification/latest` redirects to it;
  revisions in the spec repository: 2024-11-05, 2025-03-26, 2025-06-18, 2025-11-25, 2026-07-28).
- `2026-07-28` is a **breaking redesign**, not an increment ("Key Changes" page of that revision):
  - no `initialize` / `notifications/initialized` handshake — every request carries protocol version and
    client capabilities in `_meta`; mismatch → `UnsupportedProtocolVersionError` (`-32022`);
  - servers **MUST** implement `server/discover` (supported versions, capabilities, identity);
  - `ping` removed; results carry a required `resultType` (`"complete"`); `tools/list` results carry
    `ttlMs` and `cacheScope`; tools SHOULD be listed in a deterministic order;
  - `inputSchema` may use any JSON Schema 2020-12 keyword; `structuredContent` may be any JSON value.
- The spec calls servers with the handshake **legacy** (`2025-11-25` and earlier) and defines the
  compatibility path: over **stdio a modern client probes with `server/discover` and falls back to the
  `initialize` handshake** on any error that is not a recognised modern error (`basic/versioning`,
  "Backward Compatibility with Initialization-Based Versions"). A legacy-only stdio server therefore
  works with old and new clients; a dual-era server is optional.

### Java SDK (step 2)

Measured from Maven Central POMs and jars, and our resolved `runtimeClasspath` (`./gradlew dependencies`);
the SDK was not yet compiled into the build.

| Item | Result |
|---|---|
| Latest stable release | `io.modelcontextprotocol.sdk:mcp-core` **2.1.0** (2026-10-16); 2.2.0-M1/M2 are milestones |
| Spec revisions negotiated | 2.1.0 **and** 2.2.0-M2: 2024-11-05 … **2025-11-25** — none supports 2026-07-28 yet |
| Bytecode | class version 61 (Java 17) — runs on Java 25; `--enable-preview` irrelevant for a library |
| Stdio server transport | `StdioServerTransportProvider` included in `mcp-core` |
| Modules needed | `mcp-core` + `mcp-json-jackson3` |
| Direct dependencies | `slf4j-api` 2.0.16, `jackson-annotations` 2.21, `reactor-core` 3.7.0; module: `tools.jackson.core:jackson-databind` 3.1.4, `com.networknt:json-schema-validator` 3.0.6 |
| Already on our classpath | `slf4j-api` 2.0.20 (bound to Log4j via `log4j-slf4j2-impl`), `jackson-annotations` 2.22, `jackson-databind` **3.2.4** (pinned), `json-schema-validator` 3.0.9 — all resolve to our newer versions |
| **New artifacts** | `mcp-core` (662 KB), `mcp-json-jackson3` (12 KB), `reactor-core` (1.88 MB) + `reactive-streams` (~11 KB) ≈ **2.6 MB** |
| Licences | MIT (MCP SDK), Apache-2.0 (Reactor), MIT-0 (Reactive Streams) |
| Logging backend added | none (slf4j-api only) |
| Second JSON stack | no — uses the Jackson 3 we already pin; the tool layer stays on Gson behind the adapter |

## Criteria (step 3)

| Criterion | Result |
|---|---|
| Stable release exists | yes (2.1.0) |
| No logging backend, no conflicting JSON stack | yes |
| Added size < ~5 MB | yes (~2.6 MB) |
| Stdio server with a sync tool handler < ~80 lines of our code | **not yet measured** — needs the prototype in the adapter (Task 7) |

## Recommendation

**Use the SDK 2.1.0 behind `McpToolServer`**, as a legacy-era (`2025-11-25`) server. Reasons: the
criteria hold, the footprint is small because most dependencies are already present, and modern clients
reach a legacy stdio server through the spec's fallback. When an SDK release supports `2026-07-28`, it is a
version bump inside `service/ai/mcp/`.

Fall back to the own implementation only if the Task 7 prototype exceeds the line budget or Reactor
causes trouble with the CLI shutdown / the jpackage launcher.

## Consequences for the plans

- Phase 1 Task 7 tests: "initialise with the current revision" must read "initialise with `2025-11-25`
  (the newest the SDK speaks) and with an older one"; add a test that `server/discover` gets an error a
  modern client treats as "legacy server" (method not found), so the fallback works.
- Spec D4 changes to "decided" after approval.
- SBOM / `docs/licenses.md`: add MCP Java SDK (MIT), Reactor Core (Apache-2.0), Reactive Streams (MIT-0).
- `AiLayeringGuardTest` keeps `io.modelcontextprotocol` and `reactor` imports inside `service/ai/mcp/`.
