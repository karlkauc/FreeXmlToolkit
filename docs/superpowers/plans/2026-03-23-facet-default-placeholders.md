# Facet Default Value Placeholders

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show XSD spec default values as placeholder text in facet input fields instead of generic "Enter X value" prompts.

**Architecture:** Add a `getDefaultFacetPlaceholder(String datatype, XsdFacetType facetType)` method to `XsdDatatypeFacets` that returns the XSD spec default value for any facet/datatype combination. Then replace the `setPromptText("Enter " + ...)` in `XsdPropertiesPanel.updateFacets()` with the default value. For facets with no meaningful default (e.g., `length` on `xs:string`), keep a short descriptive fallback. This is a pure data lookup + one-line UI change.

**Tech Stack:** Java 25, JUnit 5

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| Modify | `controls/v2/model/XsdDatatypeFacets.java` | Add `getDefaultFacetPlaceholder()` method |
| Modify | `controls/v2/editor/panels/XsdPropertiesPanel.java:2375` | Use default value as placeholder text |
| Test | `test: controls/v2/model/XsdDatatypeFacetsTest.java` | Add tests for `getDefaultFacetPlaceholder()` |

All paths relative to `src/main/java/org/fxt/freexmltoolkit/` (or `src/test/java/...` for tests).

---

## XSD Spec Default Values Reference

These are the W3C XSD spec defaults — the implicit value when no facet restriction is set:

| Facet | String types | Numeric types (decimal/integer) | Float/Double | Date/Time | Binary |
|-------|-------------|--------------------------------|-------------|-----------|--------|
| `length` | — | — | — | — | — |
| `minLength` | `0` | — | — | — | `0` |
| `maxLength` | — | — | — | — | — |
| `totalDigits` | — | — | — | — | — |
| `fractionDigits` | — | — (fixed=0 for integers) | — | — | — |
| `minInclusive` | — | — (type-specific for bounded) | — | — | — |
| `maxInclusive` | — | — (type-specific for bounded) | — | — | — |
| `minExclusive` | — | — | — | — | — |
| `maxExclusive` | — | — | — | — | — |
| `whiteSpace` | `preserve` | `collapse` | `collapse` | `collapse` | `collapse` |
| `explicitTimezone` | — | — | — | `optional` | — |

"—" means no inherent default (unbounded/unconstrained). For these, we use the fixed value if available (e.g., `byte` minInclusive = `-128`), otherwise a short hint.

---

## Task 1: Add `getDefaultFacetPlaceholder` to `XsdDatatypeFacets`

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/v2/model/XsdDatatypeFacets.java`
- Modify: `src/test/java/org/fxt/freexmltoolkit/controls/v2/model/XsdDatatypeFacetsTest.java`

- [ ] **Step 1: Write the failing tests**

Add these tests to `XsdDatatypeFacetsTest.java`:

```java
// === Default facet placeholder tests ===

@Test
void getDefaultFacetPlaceholder_stringWhiteSpaceIsPreserve() {
    assertEquals("preserve", XsdDatatypeFacets.getDefaultFacetPlaceholder("xs:string", XsdFacetType.WHITE_SPACE));
}

@Test
void getDefaultFacetPlaceholder_normalizedStringWhiteSpaceIsReplace() {
    assertEquals("replace", XsdDatatypeFacets.getDefaultFacetPlaceholder("normalizedString", XsdFacetType.WHITE_SPACE));
}

@Test
void getDefaultFacetPlaceholder_integerWhiteSpaceIsCollapse() {
    assertEquals("collapse", XsdDatatypeFacets.getDefaultFacetPlaceholder("xs:integer", XsdFacetType.WHITE_SPACE));
}

@Test
void getDefaultFacetPlaceholder_integerFractionDigitsIsZero() {
    assertEquals("0", XsdDatatypeFacets.getDefaultFacetPlaceholder("xs:integer", XsdFacetType.FRACTION_DIGITS));
}

@Test
void getDefaultFacetPlaceholder_byteMinInclusiveIsMinus128() {
    assertEquals("-128", XsdDatatypeFacets.getDefaultFacetPlaceholder("xs:byte", XsdFacetType.MIN_INCLUSIVE));
}

@Test
void getDefaultFacetPlaceholder_byteMaxInclusiveIs127() {
    assertEquals("127", XsdDatatypeFacets.getDefaultFacetPlaceholder("xs:byte", XsdFacetType.MAX_INCLUSIVE));
}

@Test
void getDefaultFacetPlaceholder_unsignedByteMinInclusiveIsZero() {
    assertEquals("0", XsdDatatypeFacets.getDefaultFacetPlaceholder("unsignedByte", XsdFacetType.MIN_INCLUSIVE));
}

@Test
void getDefaultFacetPlaceholder_unsignedByteMaxInclusiveIs255() {
    assertEquals("255", XsdDatatypeFacets.getDefaultFacetPlaceholder("unsignedByte", XsdFacetType.MAX_INCLUSIVE));
}

@Test
void getDefaultFacetPlaceholder_dateTimeExplicitTimezoneIsOptional() {
    assertEquals("optional", XsdDatatypeFacets.getDefaultFacetPlaceholder("dateTime", XsdFacetType.EXPLICIT_TIMEZONE));
}

@Test
void getDefaultFacetPlaceholder_dateTimeStampExplicitTimezoneIsRequired() {
    assertEquals("required", XsdDatatypeFacets.getDefaultFacetPlaceholder("dateTimeStamp", XsdFacetType.EXPLICIT_TIMEZONE));
}

@Test
void getDefaultFacetPlaceholder_stringMinLengthIsZero() {
    assertEquals("0", XsdDatatypeFacets.getDefaultFacetPlaceholder("xs:string", XsdFacetType.MIN_LENGTH));
}

@Test
void getDefaultFacetPlaceholder_decimalMinInclusiveIsNull() {
    // decimal has no bounded range — no default
    assertNull(XsdDatatypeFacets.getDefaultFacetPlaceholder("xs:decimal", XsdFacetType.MIN_INCLUSIVE));
}

@Test
void getDefaultFacetPlaceholder_stringLengthIsNull() {
    // no inherent default for exact length
    assertNull(XsdDatatypeFacets.getDefaultFacetPlaceholder("xs:string", XsdFacetType.LENGTH));
}

@Test
void getDefaultFacetPlaceholder_stringMaxLengthIsNull() {
    // no inherent default for max length (unbounded)
    assertNull(XsdDatatypeFacets.getDefaultFacetPlaceholder("xs:string", XsdFacetType.MAX_LENGTH));
}

@Test
void getDefaultFacetPlaceholder_integerTotalDigitsIsNull() {
    // no inherent default for totalDigits on base integer
    assertNull(XsdDatatypeFacets.getDefaultFacetPlaceholder("xs:integer", XsdFacetType.TOTAL_DIGITS));
}

@Test
void getDefaultFacetPlaceholder_inapplicableFacetReturnsNull() {
    // LENGTH is not applicable to integer
    assertNull(XsdDatatypeFacets.getDefaultFacetPlaceholder("xs:integer", XsdFacetType.LENGTH));
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.v2.model.XsdDatatypeFacetsTest"`
Expected: FAIL — `getDefaultFacetPlaceholder` does not exist yet.

- [ ] **Step 3: Implement `getDefaultFacetPlaceholder`**

Add this method to `XsdDatatypeFacets.java` after `getFixedFacetValue()` (after line 201):

```java
/**
 * Gets the XSD spec default value for a facet on a given datatype,
 * suitable for use as placeholder text in input fields.
 * <p>
 * Returns the implicit value when no facet restriction is set:
 * <ul>
 *   <li>Fixed facets: returns the fixed value (e.g., fractionDigits=0 for integers)</li>
 *   <li>whiteSpace: "preserve" for string, "replace" for normalizedString, "collapse" for others</li>
 *   <li>explicitTimezone: "optional" for date/time types, "required" for dateTimeStamp</li>
 *   <li>minLength: "0" for types that support it</li>
 *   <li>Unbounded facets (length, maxLength, totalDigits, etc.): null</li>
 * </ul>
 *
 * @param datatype  the XSD datatype (e.g., "xs:string", "byte")
 * @param facetType the facet type
 * @return the default value string, or null if no meaningful default exists
 */
public static String getDefaultFacetPlaceholder(String datatype, XsdFacetType facetType) {
    if (datatype == null || facetType == null) {
        return null;
    }

    // If the facet is not applicable to this datatype, no default
    if (!getApplicableFacets(datatype).contains(facetType)) {
        return null;
    }

    // Fixed facets already have spec-mandated values
    String fixedValue = getFixedFacetValue(datatype, facetType);
    if (fixedValue != null) {
        return fixedValue;
    }

    String typeName = removeNamespacePrefix(datatype);

    return switch (facetType) {
        case WHITE_SPACE -> switch (typeName) {
            case "string" -> "preserve";
            case "normalizedString" -> "replace";
            default -> "collapse";
        };
        case EXPLICIT_TIMEZONE -> "optional";
        case MIN_LENGTH -> "0";
        default -> null;
    };
}
```

**Key design decisions:**
- Delegates to `getFixedFacetValue()` first — this covers all bounded numeric types (byte, short, int, long, unsigned*), fractionDigits on integers, whiteSpace on non-string types, and dateTimeStamp's explicitTimezone.
- For non-fixed facets, returns the XSD spec inherited default: `whiteSpace` defaults per type hierarchy, `explicitTimezone` defaults to "optional", `minLength` defaults to 0.
- Returns `null` for facets with no meaningful default (length, maxLength, totalDigits, minExclusive, maxExclusive, min/maxInclusive on unbounded types like decimal). The caller can use a fallback for these.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.v2.model.XsdDatatypeFacetsTest"`
Expected: PASS (all existing + new tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/v2/model/XsdDatatypeFacets.java \
        src/test/java/org/fxt/freexmltoolkit/controls/v2/model/XsdDatatypeFacetsTest.java
git commit -m "feat: add getDefaultFacetPlaceholder to XsdDatatypeFacets"
```

---

## Task 2: Use default values as placeholder text in XsdPropertiesPanel

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/v2/editor/panels/XsdPropertiesPanel.java:2375`

- [ ] **Step 1: Change the placeholder text**

In `XsdPropertiesPanel.java`, in the `updateFacets()` method, replace line 2375:

```java
textField.setPromptText("Enter " + facetType.getXmlName() + " value");
```

With:

```java
String defaultValue = XsdDatatypeFacets.getDefaultFacetPlaceholder(datatype, facetType);
textField.setPromptText(defaultValue != null ? defaultValue : facetType.getXmlName());
```

This shows the XSD default value as placeholder when available (e.g., "preserve", "-128", "0"). When no default exists (null), it shows just the facet name (e.g., "length", "totalDigits") as a concise label.

- [ ] **Step 2: Run all tests to verify nothing is broken**

Run: `./gradlew test`
Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/v2/editor/panels/XsdPropertiesPanel.java
git commit -m "feat: show XSD default values as facet placeholder text"
```

---

## Summary of Changes

| File | Change |
|------|--------|
| `XsdDatatypeFacets.java` | Add `getDefaultFacetPlaceholder(String, XsdFacetType)` — returns XSD spec default or null |
| `XsdDatatypeFacetsTest.java` | Add 16 tests covering all default value scenarios |
| `XsdPropertiesPanel.java` | Replace "Enter X value" prompt with default value or facet name |

**Examples of what the user sees:**

| Datatype | Facet | Before (placeholder) | After (placeholder) |
|----------|-------|---------------------|-------------------|
| `xs:string` | whiteSpace | "Enter whiteSpace value" | "preserve" |
| `xs:string` | minLength | "Enter minLength value" | "0" |
| `xs:string` | maxLength | "Enter maxLength value" | "maxLength" |
| `xs:byte` | minInclusive | "Enter minInclusive value" | "-128" |
| `xs:byte` | maxInclusive | "Enter maxInclusive value" | "127" |
| `xs:integer` | fractionDigits | (fixed, pre-filled) | (fixed, pre-filled) |
| `xs:decimal` | totalDigits | "Enter totalDigits value" | "totalDigits" |
| `dateTime` | explicitTimezone | "Enter explicitTimezone value" | "optional" |
