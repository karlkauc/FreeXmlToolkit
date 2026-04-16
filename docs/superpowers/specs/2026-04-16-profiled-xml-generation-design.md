# Profiled XML Generation from XSD

## Context

The existing "Generate Example Data" tab in the XSD editor offers only two controls: a `mandatoryOnly` checkbox and a `maxOccurrences` spinner. For real-world use cases (integration testing, data migration, system setup), users need fine-grained control over which elements get which values, the ability to save and reuse configurations, and batch generation of multiple files at once.

This feature adds XPath-based generation rules, saveable profiles, and batch generation to the existing tab.

## Domain Model

### GenerationProfile

```java
public class GenerationProfile {
    private String name;
    private String description;
    private String schemaFile;              // Path, for profile-schema association
    private int batchCount;                 // 1 = single file
    private String fileNamePattern;         // e.g. "order_{seq:3}.xml"
    private boolean mandatoryOnly;
    private int maxOccurrences;
    private List<XPathRule> rules;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### XPathRule

```java
public class XPathRule {
    private String xpath;                   // "/order/@id", "//item[*]/sku"
    private GenerationStrategy strategy;    // Enum
    private Map<String, String> config;     // Strategy-specific parameters
    private int priority;                   // For overlapping XPaths (higher wins)
    private boolean enabled;                // Toggle without deleting
}
```

### GenerationStrategy (Enum)

| Strategy | Config Keys | Description |
|----------|-------------|-------------|
| `AUTO` | (none) | Default: type-based generation via XsdSampleDataGenerator |
| `FIXED` | `value` | Fixed literal value |
| `OMIT` | (none) | Skip element/attribute entirely (even if mandatory) |
| `EMPTY` | (none) | Create element but leave value empty |
| `XSD_EXAMPLE` | (none) | Use values from `xs:documentation` or `xs:appinfo` |
| `ENUM_CYCLE` | (none) | Cycle through enumeration values sequentially |
| `SEQUENCE` | `pattern`, `start`, `step` | Auto-increment, e.g. `pattern="ID-{seq:4}"`, `start="1"`, `step="1"` |
| `XPATH_REF` | `ref` | Copy value from another XPath, e.g. `ref="/order/@id"` |
| `RANDOM_FROM_LIST` | `values` | Random pick from comma-separated list |
| `TEMPLATE` | `pattern` | String template with placeholders: `{seq:N}`, `{date:format}`, `{random:N}`, `{ref:xpath}` |
| `NULL` | (none) | Set `xsi:nil="true"` (for nillable elements) |

### Config Examples

```json
{ "strategy": "FIXED", "config": { "value": "AT" } }
{ "strategy": "SEQUENCE", "config": { "pattern": "ORD-{seq:6}", "start": "1", "step": "1" } }
{ "strategy": "RANDOM_FROM_LIST", "config": { "values": "Mueller,Schmidt,Huber" } }
{ "strategy": "XPATH_REF", "config": { "ref": "/order/@id" } }
{ "strategy": "TEMPLATE", "config": { "pattern": "ORD-{seq:4}-{date:yyyy}" } }
```

## Service Architecture

### ProfiledXmlGeneratorService

Core service for XML generation with profile support.

```java
public class ProfiledXmlGeneratorService {
    // Main generation methods
    String generate(GenerationProfile profile, XsdDocumentationData data);
    List<GeneratedFile> generateBatch(GenerationProfile profile, XsdDocumentationData data);

    // XPath extraction from XSD
    List<XPathInfo> extractXPaths(XsdDocumentationData data);

    // Internal
    String buildElement(String xpath, XsdExtendedElement element, GenerationContext context);
    Optional<XPathRule> matchRule(String currentXPath, List<XPathRule> rules);
    String resolveValue(XsdExtendedElement element, XPathRule rule, GenerationContext context);
}
```

**Key design decision:** This service uses `XsdDocumentationService` for XSD parsing/processing but handles XML building independently. This avoids bloating the already-large `XsdDocumentationService` (~2500 lines) and provides clean separation of concerns.

### GenerationContext

Mutable state tracked during a single generation run (across all batch files).

```java
public class GenerationContext {
    private Map<String, Integer> sequenceCounters;      // Per SEQUENCE rule
    private Map<String, String> generatedValues;        // For XPATH_REF lookups
    private Map<String, Integer> enumCyclePositions;    // For ENUM_CYCLE
    private int fileIndex;                              // Current file in batch (0-based)
    private String currentXPath;                        // Current path in tree
}
```

- `sequenceCounters` persist across batch files (IDs keep incrementing)
- `generatedValues` reset per file (each file is independent for references within a file)
- `enumCyclePositions` persist across files (cycling continues)
- `fileIndex` increments per file

### ValueStrategy Interface

Strategy pattern for value generation.

```java
public interface ValueStrategy {
    String resolve(XsdExtendedElement element, Map<String, String> config, GenerationContext context);
}
```

**Implementations (11 classes):**

| Class | Behavior |
|-------|----------|
| `AutoValueStrategy` | Delegates to existing `XsdSampleDataGenerator` |
| `FixedValueStrategy` | Returns `config.get("value")` |
| `OmitValueStrategy` | Returns sentinel value; caller skips element |
| `EmptyValueStrategy` | Returns empty string |
| `XsdExampleValueStrategy` | Extracts example from XSD annotation |
| `EnumCycleValueStrategy` | Cycles through enumerations using `context.enumCyclePositions` |
| `SequenceValueStrategy` | Parses pattern, increments `context.sequenceCounters` |
| `XPathRefValueStrategy` | Reads from `context.generatedValues` |
| `RandomFromListValueStrategy` | Splits `config.get("values")` by comma, picks random |
| `TemplateValueStrategy` | Parses `{seq:N}`, `{date:format}`, `{random:N}`, `{ref:xpath}` placeholders |
| `NullValueStrategy` | Returns sentinel; caller adds `xsi:nil="true"` |

### GenerationProfileService

Persistence for profiles, following the `FavoritesService` pattern.

```java
public class GenerationProfileService {
    void save(GenerationProfile profile);
    GenerationProfile load(String name);
    List<GenerationProfile> loadAll();
    void delete(String name);
    GenerationProfile duplicate(String name);
    void exportToFile(GenerationProfile profile, File target);
    GenerationProfile importFromFile(File source);
}
```

**Storage:** JSON via Gson, at `~/.freeXmlToolkit/generation-profiles/<name>.json`

**Example profile file:**

```json
{
  "name": "Order mit allen Feldern",
  "description": "Vollstaendige Order-Generierung fuer Integrationstests",
  "schemaFile": "order.xsd",
  "batchCount": 5,
  "fileNamePattern": "order_{seq:3}.xml",
  "mandatoryOnly": false,
  "maxOccurrences": 3,
  "rules": [
    { "xpath": "/order/@id", "strategy": "SEQUENCE", "config": { "pattern": "ORD-{seq:6}", "start": "1", "step": "1" }, "priority": 0, "enabled": true },
    { "xpath": "/order/customer/name", "strategy": "RANDOM_FROM_LIST", "config": { "values": "Mueller,Schmidt,Huber" }, "priority": 0, "enabled": true },
    { "xpath": "/order/item[*]/sku", "strategy": "TEMPLATE", "config": { "pattern": "SKU-{seq:4}-{date:yyyy}" }, "priority": 0, "enabled": true },
    { "xpath": "/order/notes", "strategy": "OMIT", "config": {}, "priority": 0, "enabled": true }
  ],
  "createdAt": "2026-04-16T10:00:00",
  "updatedAt": "2026-04-16T10:00:00"
}
```

## XPath Matching

Rules use simplified XPath expressions matched against the full path of each element during generation.

**Matching rules:**
- Exact match: `/order/customer/name` matches only that specific path
- Wildcard: `/order/item[*]/sku` matches `/order/item[1]/sku`, `/order/item[2]/sku`, etc.
- Descendant: `//sku` matches any `sku` element at any depth
- Attribute: `/order/@id` matches the `id` attribute of `order`
- Priority: When multiple rules match, highest `priority` value wins. On tie, most specific path wins (exact > wildcard > descendant).

## UI Design

The existing "Generate Example Data" tab is extended with a SplitPane layout:

```
+-----------------------------------------------------------+
| Toolbar: [Generate] [Validate] | Profile: [Dropdown] [Save] [Save As] [Delete] |
+-----------------------------------------------------------+
| Config Bar                                                |
| XSD: [path]  |  Mandatory only: [ ]  |  Max Occ: [3]     |
| Batch: [5] files  |  Pattern: [order_{seq:3}.xml]         |
| Output Dir: [path] [Browse]                               |
+---------------------------+-------------------------------+
|  XPath Rules (TableView)  |  Generated XML Preview        |
|                           |                               |
| [+ Add] [Auto-fill from   |  (CodeArea with syntax        |
|          XSD]             |   highlighting, as before)    |
|                           |                               |
| XPath     | Strategy    |  |                               |
| ----------|-------------|  |                               |
| /order/@id| SEQUENCE    |  |                               |
| /order/.. | AUTO        |  |                               |
| /order/.. | FIXED       |  |                               |
| /order/.. | OMIT        |  |                               |
|           |             |  |                               |
| Config Panel (below       |                               |
| table, shows config for   |                               |
| selected rule):           |                               |
| Pattern: [ORD-{seq:4}]   |                               |
| Start: [1] Step: [1]     |                               |
+---------------------------+-------------------------------+
| Validation Results (collapsible, as before)               |
+-----------------------------------------------------------+
```

**UI Behavior:**
- **Auto-fill button:** Extracts all XPaths from loaded XSD, fills table with `AUTO` default
- **Table:** Sortable, filterable (show only configured / all rules)
- **Strategy dropdown** in table: Changing strategy updates config panel below
- **Config panel:** Context-dependent fields per strategy type
- **Profile dropdown:** Quick-switch between saved profiles
- **Preview:** Shows first generated file; for batch, shows "N files generated to [dir]"
- **Backward compatibility:** Empty profile (no rules) behaves exactly like current generation

## Batch Generation

- `batchCount` controls how many files are generated (1 = single file, same as today)
- `fileNamePattern` supports `{seq:N}` for zero-padded numbering (e.g. `order_{seq:3}.xml` -> `order_001.xml`)
- Output directory is chosen via directory chooser
- `SEQUENCE` counters persist across files (IDs keep incrementing)
- `RANDOM_FROM_LIST` and `AUTO` produce different values per file
- `FIXED` values remain constant across files
- `CHOICE` elements in XSD may select different alternatives per file
- `ENUM_CYCLE` continues cycling across files

## File Locations

### New files to create

**Domain:**
- `src/main/java/.../domain/GenerationProfile.java`
- `src/main/java/.../domain/XPathRule.java`
- `src/main/java/.../domain/GenerationStrategy.java` (enum)
- `src/main/java/.../domain/GeneratedFile.java` (name + content pair)
- `src/main/java/.../domain/XPathInfo.java` (xpath + type info for auto-extraction)

**Services:**
- `src/main/java/.../service/ProfiledXmlGeneratorService.java`
- `src/main/java/.../service/GenerationProfileService.java`
- `src/main/java/.../service/GenerationContext.java`

**Value Strategies:**
- `src/main/java/.../service/strategy/ValueStrategy.java` (interface)
- `src/main/java/.../service/strategy/AutoValueStrategy.java`
- `src/main/java/.../service/strategy/FixedValueStrategy.java`
- `src/main/java/.../service/strategy/OmitValueStrategy.java`
- `src/main/java/.../service/strategy/EmptyValueStrategy.java`
- `src/main/java/.../service/strategy/XsdExampleValueStrategy.java`
- `src/main/java/.../service/strategy/EnumCycleValueStrategy.java`
- `src/main/java/.../service/strategy/SequenceValueStrategy.java`
- `src/main/java/.../service/strategy/XPathRefValueStrategy.java`
- `src/main/java/.../service/strategy/RandomFromListValueStrategy.java`
- `src/main/java/.../service/strategy/TemplateValueStrategy.java`
- `src/main/java/.../service/strategy/NullValueStrategy.java`
- `src/main/java/.../service/strategy/ValueStrategyFactory.java`

**Tests:**
- `src/test/java/.../domain/GenerationProfileTest.java`
- `src/test/java/.../domain/XPathRuleTest.java`
- `src/test/java/.../service/ProfiledXmlGeneratorServiceTest.java`
- `src/test/java/.../service/GenerationProfileServiceTest.java`
- `src/test/java/.../service/GenerationContextTest.java`
- `src/test/java/.../service/strategy/*StrategyTest.java` (one per strategy)
- `src/test/java/.../service/BatchGenerationTest.java`
- `src/test/java/.../service/XPathExtractionTest.java`
- `src/test/java/.../service/GeneratedXmlValidationTest.java`

### Files to modify

- `src/main/resources/pages/tab_xsd.fxml` - Extend "Generate Example Data" tab layout
- `src/main/java/.../controller/XsdController.java` - Wire new UI controls and profile logic
- `src/main/java/module-info.java` - Export new packages if needed

### Existing files to reuse

- `src/main/java/.../service/XsdDocumentationService.java` - XSD parsing, `XsdDocumentationData`, `XsdExtendedElement`
- `src/main/java/.../service/XsdSampleDataGenerator.java` - Reused by `AutoValueStrategy`
- `src/main/java/.../service/IdentityConstraintTracker.java` - Reused for key/unique constraints
- `src/main/java/.../domain/XsdExtendedElement.java` - Element model
- `src/main/java/.../domain/XsdDocumentationData.java` - Parsed XSD data
- `src/main/java/.../util/DialogHelper.java` - File/directory dialogs
- `src/main/java/.../service/FavoritesService.java` - Pattern reference for persistence

## Verification

### Automated Tests
1. Run `./gradlew test` - all new and existing tests must pass
2. Strategy tests verify each strategy independently with various inputs
3. Integration tests use real XSD files from `examples/` and `src/test/resources/`
4. Round-trip test: generate with profile -> validate against XSD -> must be valid
5. Batch test: generate N files, verify sequence continuity and file count

### Manual Testing
1. Load an XSD in the XSD editor
2. Switch to "Generate Example Data" tab
3. Click "Auto-fill" -> verify all XPaths appear in table
4. Configure several rules (FIXED, SEQUENCE, OMIT, RANDOM_FROM_LIST)
5. Generate single file -> verify rules applied correctly
6. Save profile -> reload profile -> verify rules preserved
7. Generate batch (5 files) -> verify all files created with correct names
8. Verify sequences increment across files
9. Validate generated XML against schema
10. Test with empty profile -> must behave like current generation (backward compatibility)
