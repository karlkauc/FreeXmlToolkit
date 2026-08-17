# Domain Quick-Reference (XSD/XML)

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
