# Domain Quick-Reference (XSD/XML)

## XSD 1.0 vs 1.1

| Feature | XSD 1.0 | XSD 1.1 |
|---------|---------|---------|
| Assertions (`xs:assert`) | No | Yes |
| Conditional Type Assignment | No | Yes |
| Open Content | No | Yes |
| `xs:override` | No | Yes |
| Default Attribute Groups | No | Yes |
| `targetNamespace` on local elements | No | Yes |

**Library:** Xerces 2.12.2 (exist-db fork) - Full XSD 1.1 support

---

## 38 XsdNode Types (by Category)

### Schema Level
- `XsdSchema` - Root node
- `XsdImport` - Import external schema
- `XsdInclude` - Include same-namespace schema
- `XsdRedefine` - Redefine types (deprecated in 1.1)
- `XsdOverride` - Override types (XSD 1.1)

### Type Definitions
- `XsdComplexType` - Complex type with elements/attributes
- `XsdSimpleType` - Simple type (restriction/list/union)
- `XsdRestriction` - Type restriction with facets
- `XsdExtension` - Type extension
- `XsdList` - List type
- `XsdUnion` - Union type

### Structure
- `XsdElement` - Element declaration
- `XsdAttribute` - Attribute declaration
- `XsdSequence` - Ordered children (AND, ordered)
- `XsdChoice` - Alternative children (OR)
- `XsdAll` - Unordered children (AND, unordered)
- `XsdGroup` - Reusable element group
- `XsdAttributeGroup` - Reusable attribute group
- `XsdAny` - Wildcard element
- `XsdAnyAttribute` - Wildcard attribute

### Constraints
- `XsdKey` - Unique key (like primary key)
- `XsdKeyRef` - Foreign key reference
- `XsdUnique` - Uniqueness constraint
- `XsdAssert` - XPath assertion (XSD 1.1)

### Facets (14 types)
- **Length:** `length`, `minLength`, `maxLength`
- **Value:** `minInclusive`, `maxInclusive`, `minExclusive`, `maxExclusive`
- **Digits:** `totalDigits`, `fractionDigits`
- **Other:** `pattern`, `enumeration`, `whiteSpace`
- **XSD 1.1:** `assertion`, `explicitTimezone`

### XSD 1.1 Only
- `XsdAlternative` - Conditional type assignment
- `XsdOpenContent` - Allow extension elements
- `XsdDefaultOpenContent` - Schema-wide open content

### Annotation
- `XsdAnnotation` - Documentation container
- `XsdDocumentation` - Human-readable docs
- `XsdAppInfo` - Machine-readable metadata

---

## XML Processing Libraries

| Task | Library | Version |
|------|---------|---------|
| Parsing/Validation | Xerces | 2.12.2 (XSD 1.1) |
| XSLT 3.0 | Saxon HE | 12.9 |
| XPath 3.1 | Saxon HE | 12.9 |
| XQuery 3.1 | Saxon HE | 12.9 |
| PDF (XSL-FO) | Apache FOP | 2.11 |
| Digital Signatures | Apache Santuario | - |
| Excel Export | Apache POI | 5.4.1 |

---

## Facets by Datatype

### Applicable to All
- `pattern`, `enumeration`, `whiteSpace`

### Numeric Types (integer, decimal, float, double)
- `minInclusive`, `maxInclusive`, `minExclusive`, `maxExclusive`
- `totalDigits`, `fractionDigits` (decimal/integer only)

### String Types
- `length`, `minLength`, `maxLength`

### Date/Time Types
- `minInclusive`, `maxInclusive`, `minExclusive`, `maxExclusive`
- `explicitTimezone` (XSD 1.1)

---

## Common XSD Patterns

### Restricting a SimpleType
```xml
<xs:simpleType name="Age">
  <xs:restriction base="xs:integer">
    <xs:minInclusive value="0"/>
    <xs:maxInclusive value="150"/>
  </xs:restriction>
</xs:simpleType>
```

### ComplexType with Sequence
```xml
<xs:complexType name="Person">
  <xs:sequence>
    <xs:element name="name" type="xs:string"/>
    <xs:element name="age" type="Age"/>
  </xs:sequence>
</xs:complexType>
```

### XSD 1.1 Assertion
```xml
<xs:assert test="@min le @max" xpathDefaultNamespace="##targetNamespace"/>
```

---

## Circular Reference Warning

XSD elements can reference types that contain the same element type. Example:
```xml
<xs:element name="folder" type="FolderType"/>
<xs:complexType name="FolderType">
  <xs:sequence>
    <xs:element ref="folder" minOccurs="0" maxOccurs="unbounded"/>
  </xs:sequence>
</xs:complexType>
```

**ALWAYS check for circular references when traversing XsdNode trees!**
