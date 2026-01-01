# JSON Demo Files

This directory contains various JSON files to demonstrate and test the JSON Editor features.

## Standard JSON Files

| File | Description |
|------|-------------|
| `basic.json` | Simple JSON with common data types |
| `nested.json` | Deeply nested object structures |
| `arrays.json` | Various array types and nesting |
| `users.json` | User data for JSONPath queries |
| `products.json` | Product catalog (matches product-schema.json) |

## JSONPath Testing

| File | Description |
|------|-------------|
| `bookstore.json` | Classic JSONPath example (store with books) |
| `jsonpath-examples.json` | Contains example queries as comments |

### Example JSONPath Queries

Try these queries with `jsonpath-examples.json`:

- `$.store.book[*].author` - All authors
- `$.store.book[?(@.price < 10)]` - Books under $10
- `$..price` - All prices (recursive)
- `$.store.book[0]` - First book
- `$.store.book[-1]` - Last book
- `$.store.book[?(@.isbn)]` - Books with ISBN

## JSONC (JSON with Comments)

| File | Description |
|------|-------------|
| `config.jsonc` | Configuration file with `//` and `/* */` comments |

## JSON5 (Extended Syntax)

| File | Description |
|------|-------------|
| `settings.json5` | Demonstrates JSON5 features |

### JSON5 Features Demonstrated

- Single-line comments (`//`)
- Block comments (`/* */`)
- Unquoted property names
- Single-quoted strings
- Trailing commas
- Hexadecimal numbers
- Infinity and NaN values

## JSON Schema Validation

| File | Description |
|------|-------------|
| `product-schema.json` | JSON Schema for product validation |
| `products.json` | Valid products matching the schema |

### Testing Schema Validation

1. Open `products.json` in the JSON Editor
2. Click Schema > Load Schema
3. Select `product-schema.json`
4. Click Schema > Validate Against Schema

## Performance Testing

| File | Description |
|------|-------------|
| `large-dataset.json` | 1000 records (~650KB) for performance testing |

### JSONPath Performance Queries

Try these with `large-dataset.json`:

- `$.records[*].email` - All 1000 emails
- `$.records[?(@.age > 40)]` - Filter by age
- `$.records[?(@.active == true)]` - Active users only
- `$.records[?(@.salary > 100000)]` - High earners
- `$..projects[*]` - All projects (2000 items)

## Edge Cases and Testing

| File | Description |
|------|-------------|
| `edge-cases.json` | Unicode, escapes, deep nesting, special values |
| `invalid-json.txt` | Intentionally invalid JSON for error testing |
