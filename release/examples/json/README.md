# JSON Schema Validation Examples

Demo material for validating JSON documents against a JSON Schema. Every instance
document declares its schema with a top-level `"$schema"` member, so FreeXmlToolkit
binds it **automatically when the file opens** — the status bar shows
**"JSON Schema: name"** and the Validation panel's JSON Schema row fills in.

| File | Purpose |
|------|---------|
| `product-schema.json` | Main demo schema (2020-12): required properties, `pattern`, `enum`, number ranges, `$defs`, and a **relative `$ref`** to `manufacturer-schema.json` |
| `manufacturer-schema.json` | Sibling schema pulled in via `$ref` |
| `products-valid.json` | Valid catalog — validates clean |
| `products-invalid.json` | Six deliberate violations (wrong type, enum, pattern, missing required, negative price) — each problem in the PROBLEMS list carries its **line number**, the failing keyword, and the JSON pointer |
| `person-schema-draft07.json` | Dialect demo: draft-07 `dependencies` |
| `person-invalid-draft07.json` | Violates it (`credit_card` without `billing_address`) |
| `order-schema-2019-09.json` | Dialect demo: 2019-09 `dependentRequired` |
| `order-invalid-2019-09.json` | Violates it (`gift_message` without `gift`) |

## Try It

1. Open `products-invalid.json` — the status bar switches to
   **"JSON Schema: product-schema.json"** on its own.
2. Press **F8** (or **Run Validation** in the Validation panel, or just type —
   *Validate while typing* is on by default).
3. Click a problem to jump to its line; fix it and watch the problem disappear.

The dialect is selected by each schema's own `$schema` declaration
(Draft-07, 2019-09 and 2020-12 are supported; 2020-12 is the default for schemas
without one). A schema you bind manually — via the status bar indicator, the
Validation panel row, or drag & drop — always wins over the declared one.
