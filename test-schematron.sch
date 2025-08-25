<?xml version="1.0" encoding="UTF-8"?>
<schema xmlns="http://purl.oclc.org/dsdl/schematron"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        queryBinding="xslt2">

    <title>Test Schematron Rules</title>
    
    <ns prefix="funds" uri="http://www.fundsxml.org/schema"/>
    
    <pattern>
        <title>Basic Fund Validation</title>
        
        <rule context="funds:Fund">
            <assert test="funds:FundName">A Fund must have a FundName</assert>
            <assert test="string-length(funds:FundName) > 0">FundName cannot be empty</assert>
        </rule>
        
        <rule context="funds:Price">
            <assert test="number(.) > 0">Price must be a positive number</assert>
            <report test="number(.) > 1000">Price seems unusually high</report>
        </rule>
        
    </pattern>
    
</schema>