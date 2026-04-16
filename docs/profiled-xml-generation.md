# Profiled XML Generation

> **Added in:** April 2026

Generate realistic, customized XML sample data from your XSD schemas. This feature lets you control exactly how each element and attribute gets its value, save your configurations as reusable profiles, and generate multiple files in one go.

---

## Why Use Profiled Generation?

The basic sample XML generator creates generic data based on element types. That is useful for quick tests, but real-world scenarios often need more:

- **Integration testing** -- You need specific values in specific fields (country codes, IDs, dates).
- **Data migration** -- You want sample files that look like actual production data.
- **System setup** -- You need multiple files with sequential IDs and realistic names.
- **Schema validation** -- You want to test edge cases by omitting optional fields or using boundary values.

Profiled generation solves these by letting you define rules for individual XPath locations in your XML.

---

## Getting Started

1. Open your XSD file in the **XSD** tab.
2. Go to the **Generate Example Data** sub-tab.
3. You will see the generation workspace with a rules table on the left and an XML preview on the right.

If you have used this tab before: the basic controls (mandatory only, max occurrences) are still there. Without any rules, generation works exactly as it did before.

---

## The Generation Workspace

The workspace is split into several areas:

### Toolbar

At the top, you will find:

- **Generate** -- Create the XML output.
- **Validate** -- Check the generated XML against the schema.
- **Profile dropdown** -- Select a saved profile.
- **Save / Save As / Delete** -- Manage your profiles.

### Configuration Bar

Below the toolbar:

- **XSD file path** -- Shows which schema is loaded.
- **Mandatory only** -- When checked, only required elements are generated.
- **Max occurrences** -- Limits how many times repeating elements appear (default: 3).
- **Batch count** -- How many files to generate (default: 1).
- **File name pattern** -- Naming pattern for batch files (for example, `order_{seq:3}.xml`).
- **Output directory** -- Where batch files are saved (choose with the Browse button).

### Rules Table

The left side of the workspace shows a table of generation rules. Each row defines how a specific XPath location in the XML should be populated. The columns are:

| Column | Description |
|--------|-------------|
| **XPath** | The path to the element or attribute |
| **Strategy** | How the value should be generated |
| **Enabled** | Toggle the rule on or off without deleting it |

When you select a rule in the table, a configuration panel appears below it. This panel shows settings specific to the chosen strategy.

### XML Preview

The right side shows the generated XML with syntax highlighting. For batch generation, it shows the first generated file.

---

## Generation Strategies

Each rule uses a strategy to determine the value. Here are all available strategies:

### Auto (Default)

Uses the standard type-based generation. This is the same behavior as the basic generator. Numbers get numeric values, dates get date values, strings get string values, and so on. You do not need to configure anything.

**When to use:** For elements where you do not care about the specific value.

### Fixed Value

Always uses a specific value you provide.

| Setting | Description |
|---------|-------------|
| **Value** | The exact value to use |

**Example:** Set a country code to always be `AT`, or a currency to `EUR`.

### Omit

Skips the element or attribute entirely, even if the schema marks it as required.

**When to use:** Testing how your system handles missing data, or generating minimal files.

### Empty

Creates the element in the XML but leaves its content empty.

**When to use:** Testing required-but-empty field handling.

### XSD Example

Uses example values found in the XSD annotations (from `xs:documentation` or `xs:appinfo`). If no example is found, falls back to auto-generation.

**When to use:** When your schema already contains good example values.

### Enum Cycle

For elements with enumeration restrictions, cycles through the allowed values in order. If the element appears three times and the enumeration has values `A`, `B`, `C`, the generated values will be `A`, `B`, `C`. The cycle continues across batch files.

**When to use:** Ensuring all enumeration values appear in your test data.

### Sequence

Generates auto-incrementing values using a pattern.

| Setting | Description |
|---------|-------------|
| **Pattern** | The format string, for example `ORD-{seq:6}` |
| **Start** | The first number in the sequence (default: 1) |
| **Step** | How much to increment each time (default: 1) |

The `{seq:N}` placeholder is replaced with a zero-padded number, where `N` is the number of digits. For example, `ORD-{seq:4}` with start 1 produces `ORD-0001`, `ORD-0002`, `ORD-0003`, and so on.

Sequence counters persist across batch files. If you generate 5 files with 3 orders each, the IDs continue from 1 to 15.

**When to use:** Generating unique IDs, order numbers, or any incrementing values.

### XPath Reference

Copies the value from another element that was already generated in the same file.

| Setting | Description |
|---------|-------------|
| **Reference XPath** | The XPath of the element to copy from |

**Example:** You want a confirmation message to include the order ID. Set the reference to `/order/@id`, and the value from that attribute will be reused.

**When to use:** When one field should match or reference another field in the same document.

### Random from List

Picks a random value from a list you provide.

| Setting | Description |
|---------|-------------|
| **Values** | Comma-separated list of possible values |

**Example:** Enter `Mueller,Schmidt,Huber,Wagner` to get random German last names.

**When to use:** Creating realistic-looking data with variety across multiple files or repeating elements.

### Template

Combines multiple placeholders into a single value.

| Setting | Description |
|---------|-------------|
| **Pattern** | A string with placeholders |

Available placeholders:

| Placeholder | Description | Example |
|-------------|-------------|---------|
| `{seq:N}` | Zero-padded sequence number | `{seq:4}` becomes `0001` |
| `{date:format}` | Current date in the given format | `{date:yyyy-MM-dd}` becomes `2026-04-16` |
| `{random:N}` | Random N-digit number | `{random:4}` becomes `7283` |
| `{ref:xpath}` | Value from another XPath | `{ref:/order/@id}` copies the order ID |

**Example:** The pattern `INV-{seq:4}-{date:yyyy}` produces values like `INV-0001-2026`, `INV-0002-2026`.

**When to use:** Creating composite values that include sequences, dates, or references.

### Null

Sets the element to `xsi:nil="true"`. This only works for elements that are marked as nillable in the schema.

**When to use:** Testing how your system handles explicitly null values.

---

## Working with Rules

### Adding Rules Manually

1. Click the **Add** button below the rules table.
2. Enter the XPath for the element or attribute.
3. Choose a strategy from the dropdown.
4. Configure the strategy settings in the panel below the table.

### Auto-Fill from XSD

Instead of typing XPaths manually, click the **Auto-fill from XSD** button. This scans the loaded schema and populates the rules table with all available XPaths, each set to the **Auto** strategy by default. You can then change individual strategies as needed.

This is the recommended way to start: auto-fill first, then customize.

### Enabling and Disabling Rules

Each rule has an enabled/disabled toggle. Disabling a rule keeps it in the table (with all its settings) but ignores it during generation. This is useful for temporarily turning off rules without losing your configuration.

### Rule Priority

When multiple rules could match the same element (for example, an exact path and a descendant wildcard), the rule with the highest priority number wins. If priorities are equal, more specific paths take precedence:

1. Exact paths (most specific): `/order/customer/name`
2. Wildcard paths: `/order/item[*]/sku`
3. Descendant paths (least specific): `//sku`

### XPath Patterns

Rules use simplified XPath expressions:

| Pattern | Matches | Example |
|---------|---------|---------|
| `/root/child/element` | Exact path | `/order/customer/name` |
| `/root/child[*]/element` | Any index position | `/order/item[*]/sku` |
| `//element` | Any element at any depth | `//sku` |
| `/root/@attribute` | An attribute | `/order/@id` |

---

## Profiles

Profiles let you save your generation configuration and reuse it later. This is especially useful when you need to generate test data repeatedly during development.

### Saving a Profile

1. Configure your rules and settings.
2. Click **Save As** in the toolbar.
3. Enter a name for the profile.
4. The profile is saved and appears in the profile dropdown.

To update an existing profile, make your changes and click **Save**.

### Loading a Profile

Select a profile from the dropdown in the toolbar. All rules and settings are restored.

### Deleting a Profile

Select the profile in the dropdown and click **Delete**.

### Where Profiles Are Stored

Profiles are saved as JSON files in your user directory:

- **Windows:** `C:\Users\<your-name>\.freeXmlToolkit\generation-profiles\`
- **macOS:** `/Users/<your-name>/.freeXmlToolkit/generation-profiles/`
- **Linux:** `/home/<your-name>/.freeXmlToolkit/generation-profiles/`

### Sharing Profiles

You can share profiles with other users:

- **Export:** Use the export function to save a profile to any location on your computer.
- **Import:** Use the import function to load a profile from a file.

Simply copy the JSON profile file and share it. The recipient can import it into their own FreeXmlToolkit installation.

---

## Batch Generation

Generate multiple XML files at once, each with different values.

### How to Use Batch Generation

1. Set the **Batch count** to the number of files you want (for example, 5).
2. Set a **File name pattern** (for example, `order_{seq:3}.xml`).
3. Choose an **Output directory** using the Browse button.
4. Click **Generate**.

### File Name Patterns

The file name pattern supports the `{seq:N}` placeholder, where `N` is the number of digits for zero-padding:

| Pattern | Files Generated |
|---------|-----------------|
| `order_{seq:3}.xml` | `order_001.xml`, `order_002.xml`, `order_003.xml` |
| `test_{seq:2}.xml` | `test_01.xml`, `test_02.xml` |
| `data_{seq:4}.xml` | `data_0001.xml`, `data_0002.xml` |

### How Strategies Behave Across Files

| Strategy | Across Files |
|----------|--------------|
| **Sequence** | Counters keep incrementing. File 1 might have IDs 1-3, file 2 has 4-6. |
| **Enum Cycle** | Cycling continues. If file 1 ends on value B, file 2 starts with C. |
| **Random from List** | Different random picks per file. |
| **Auto** | Different generated values per file. |
| **Fixed** | Same value in every file. |
| **XPath Reference** | References are resolved within each file independently. |

---

## Common Tasks

### Generate Test Data with Sequential IDs

1. Load your XSD file.
2. Go to **Generate Example Data**.
3. Click **Auto-fill from XSD**.
4. Find the ID element in the rules table (for example, `/order/@id`).
5. Change its strategy to **Sequence**.
6. Set the pattern to `ORD-{seq:6}`, start to `1`, step to `1`.
7. Click **Generate**.

### Generate Multiple Files for Load Testing

1. Follow the steps above to configure your rules.
2. Set **Batch count** to the number of files you need (for example, 100).
3. Set the **File name pattern** to `test_{seq:4}.xml`.
4. Choose an output directory.
5. Click **Generate**.

### Create Realistic Customer Data

1. Load your XSD file and click **Auto-fill from XSD**.
2. Set the name field to **Random from List** with values like `Smith,Johnson,Williams,Brown,Jones`.
3. Set the country field to **Fixed** with value `US`.
4. Set the email field to **Template** with pattern `user{seq:3}@example.com`.
5. Click **Generate**.

### Skip Optional Fields

1. Load your XSD and click **Auto-fill from XSD**.
2. Check the **Mandatory only** checkbox, or
3. Find specific optional elements in the rules table and set their strategy to **Omit**.

---

## Backward Compatibility

If you do not configure any rules, the generator behaves exactly as it did before this feature was added. The basic controls (mandatory only, max occurrences) work the same way. You only need to use profiles and rules when you want more control.

---

## Troubleshooting

### Generated XML fails validation

- Check that **Omit** is not used on mandatory elements that the schema requires.
- Verify that **Fixed** values match the expected data type (for example, do not put text in a numeric field).
- Make sure **Null** is only used on elements marked as nillable in the schema.

### XPath Reference shows empty values

- The referenced XPath must appear earlier in the XML than the element using it. The generator processes elements in document order, so a reference can only point to something already generated.

### Sequence numbers restart unexpectedly

- Sequence counters persist across batch files but reset when you click Generate again. If you need to continue from a previous run, adjust the **Start** value.

### Auto-fill shows fewer XPaths than expected

- Auto-fill extracts XPaths from the schema structure. Elements inside deeply nested or imported schemas may not appear. You can always add rules manually for any XPath.

### Profile not loading

- Make sure the profile was saved for the same schema (or a compatible one). Profiles are associated with the schema file they were created for.

---

## Navigation

| Previous | Home | Next |
|----------|------|------|
| [XSD Tools](xsd-tools.md) | [Home](index.md) | [XSD Validation](xsd-validation.md) |

**All Pages:** [XML Editor](xml-editor.md) | [XML Features](xml-editor-features.md) | [XSD Tools](xsd-tools.md) | [Profiled XML Generation](profiled-xml-generation.md) | [XSD Validation](xsd-validation.md) | [XSLT Viewer](xslt-viewer.md) | [XSLT Developer](xslt-developer.md) | [FOP/PDF](pdf-generator.md) | [Signatures](digital-signatures.md) | [IntelliSense](context-sensitive-intellisense.md) | [Schematron](schematron-support.md) | [Favorites](favorites-system.md) | [Templates](template-management.md) | [Tech Stack](technology-stack.md) | [Licenses](licenses.md)
