# XSD Documentation: Type Definition Inclusion Feature

## Overview

This feature enhances the XSD HTML documentation generation by optionally including the complete definition of referenced types (complexType and simpleType) in the source code display.

## Problem

Previously, when viewing the source code of an XSD element in the generated HTML documentation, only the element definition itself was displayed. For example:

```xml
<xs:element name="Fund" type="FundType">
   <xs:annotation>
      <xs:documentation>All single fund, umbrella, sicav, portfolio related data</xs:documentation>
   </xs:annotation>
</xs:element>
```

Users had to manually navigate to the FundType definition to see its structure, which made understanding the complete schema more difficult.

## Solution

When the **"include type definitions in source code"** option is enabled, the source code display now includes both the element definition and the complete referenced type definition:

```xml
<!-- Element Definition -->
<xs:element name="Fund" type="FundType">
   <xs:annotation>
      <xs:documentation>All single fund, umbrella, sicav, portfolio related data</xs:documentation>
   </xs:annotation>
</xs:element>


<!-- Referenced Type Definition: FundType -->
<xs:complexType name="FundType">
   <xs:sequence>
      <xs:element name="FundName" type="xs:string"/>
      <xs:element name="FundValue" type="xs:decimal"/>
   </xs:sequence>
</xs:complexType>
```

## Usage

### In the User Interface

1. Open the **XSD** tab
2. Go to the **Documentation** section
3. Locate the checkbox: **"include type definitions in source code"**
4. Check the box to enable this feature
5. Click **"generate"** to create the documentation

The option is **disabled by default** to maintain backward compatibility.

### Programmatic Usage

```java
XsdDocumentationService docService = new XsdDocumentationService();
docService.setXsdFilePath("/path/to/schema.xsd");

// Enable type definition inclusion
docService.setIncludeTypeDefinitionsInSourceCode(true);

// Generate documentation
docService.generateXsdDocumentation(outputDirectory);
```

## Implementation Details

### Files Modified

1. **XsdDocumentationService.java**
   - Added property: `Boolean includeTypeDefinitionsInSourceCode`
   - Added setter: `setIncludeTypeDefinitionsInSourceCode(Boolean)`
   - Added method: `generateSourceCodeWithOptionalTypeDefinition()`
   - Modified `processElementOrAttribute()` to use the new method

2. **tab_xsd.fxml**
   - Added UI checkbox: `includeTypeDefinitionsInSourceCode`
   - Grid row 7 in the Documentation section

3. **XsdController.java**
   - Added field: `@FXML private CheckBox includeTypeDefinitionsInSourceCode`
   - Added configuration call: `docService.setIncludeTypeDefinitionsInSourceCode(includeTypeDefinitionsInSourceCode.isSelected())`

### How It Works

1. When processing each XSD element, the service checks if type definition inclusion is enabled
2. If enabled and the element has a type reference (e.g., `type="FundType"`):
   - The service extracts the element's source code
   - It looks up the referenced type in the global type maps (complexTypeMap or simpleTypeMap)
   - If found, it appends the type definition to the element's source code
   - Comment markers are added to clearly separate the element and type definitions

3. Built-in XSD types (xs:string, xs:int, etc.) are excluded from this process
4. Inline type definitions are not duplicated (they're already part of the element's source code)

### Key Methods

**`generateSourceCodeWithOptionalTypeDefinition(Node node, String typeName, Node typeDefinitionNode)`**

This method:
- Generates the base source code for the element
- Checks if type definition inclusion is enabled
- Validates that the type is not a built-in XSD type
- Looks up the global type definition
- Combines element and type source code with appropriate comments
- Returns the complete source code string

## Testing

Comprehensive tests have been added in `XsdDocumentationTypeDefinitionInclusionTest.java`:

- ✅ **testTypeDefinitionInclusionDisabled**: Verifies default behavior without type definitions
- ✅ **testTypeDefinitionInclusionEnabledForComplexType**: Tests inclusion of complexType definitions
- ✅ **testTypeDefinitionInclusionEnabledForSimpleType**: Tests inclusion of simpleType definitions
- ✅ **testBuiltInTypeNotIncluded**: Ensures built-in types are not included
- ✅ **testSetterGetter**: Validates property setter/getter

All tests pass successfully.

## Benefits

1. **Improved Documentation Clarity**: Users can see the complete structure of complex types without navigating away
2. **Better Understanding**: Viewing element and type together provides complete context
3. **Optional Feature**: Can be disabled if users prefer the previous behavior
4. **No Performance Impact**: Only affects documentation generation, not runtime

## Examples

### Complex Type Example

**Element with type reference:**
```xml
<xs:element name="Product" type="ProductType"/>
```

**With feature enabled, displays:**
```xml
<!-- Element Definition -->
<xs:element name="Product" type="ProductType"/>


<!-- Referenced Type Definition: ProductType -->
<xs:complexType name="ProductType">
   <xs:sequence>
      <xs:element name="ProductCode" type="xs:string"/>
      <xs:element name="Price" type="xs:decimal"/>
      <xs:element name="Description" type="xs:string"/>
   </xs:sequence>
</xs:complexType>
```

### Simple Type Example

**Element with simple type reference:**
```xml
<xs:element name="StatusCode" type="StatusCodeType"/>
```

**With feature enabled, displays:**
```xml
<!-- Element Definition -->
<xs:element name="StatusCode" type="StatusCodeType"/>


<!-- Referenced Type Definition: StatusCodeType -->
<xs:simpleType name="StatusCodeType">
   <xs:restriction base="xs:string">
      <xs:enumeration value="ACTIVE"/>
      <xs:enumeration value="INACTIVE"/>
      <xs:enumeration value="PENDING"/>
   </xs:restriction>
</xs:simpleType>
```

## Future Enhancements

Potential improvements for future versions:

1. Add hyperlinks between element and type definitions
2. Support recursive type inclusion (types referenced by types)
3. Add syntax highlighting to differentiate element vs type sections
4. Option to show/hide type definitions interactively in the HTML viewer

## Compatibility

- **Backward Compatible**: The feature is disabled by default
- **No Breaking Changes**: Existing documentation generation continues to work as before
- **JavaFX UI Integration**: Seamlessly integrated with existing documentation controls
- **Test Coverage**: Comprehensive unit tests ensure reliability
