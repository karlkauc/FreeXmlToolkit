<?xml version="1.0" encoding="UTF-8"?>
<schema xmlns="http://purl.oclc.org/dsdl/schematron">
    <title>Simple XML Validation Schema</title>
    <ns uri="http://example.com" prefix="ex"/>
    
    <!-- Pattern 1: Basic structure validation -->
    <pattern id="basic-structure">
        <title>Basic Structure Validation</title>
        <rule context="root">
            <assert test="element">Root element must contain at least one 'element' child</assert>
            <assert test="count(element) &lt;= 5">Root element can contain at most 5 'element' children</assert>
        </rule>
    </pattern>
    
    <!-- Pattern 2: Element content validation -->
    <pattern id="element-content">
        <title>Element Content Validation</title>
        <rule context="element">
            <assert test="string-length(text()) &gt; 0">Element must contain non-empty text content</assert>
            <assert test="string-length(text()) &lt;= 100">Element text content must not exceed 100 characters</assert>
        </rule>
    </pattern>
    
    <!-- Pattern 3: Attribute validation -->
    <pattern id="attribute-validation">
        <title>Attribute Validation</title>
        <rule context="element[@id]">
            <assert test="matches(@id, '^[a-zA-Z][a-zA-Z0-9_]*$')">ID attribute must start with a letter and contain only letters, numbers, and underscores</assert>
        </rule>
    </pattern>
    
    <!-- Pattern 4: Nested structure validation -->
    <pattern id="nested-structure">
        <title>Nested Structure Validation</title>
        <rule context="element/nested">
            <assert test="parent::element">Nested elements must be direct children of 'element'</assert>
            <assert test="not(nested)">Nested elements cannot contain other nested elements</assert>
        </rule>
    </pattern>
</schema>
