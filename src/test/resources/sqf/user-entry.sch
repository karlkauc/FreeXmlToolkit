<?xml version="1.0" encoding="UTF-8"?>
<sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2"
            xmlns:sqf="http://www.schematron-quickfix.com/validator/process"
            xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <sch:pattern>
        <sch:rule context="person">
            <sch:assert test="@nickname" sqf:fix="addNickname">Person needs a nickname.</sch:assert>
            <sqf:fix id="addNickname">
                <sqf:description>
                    <sqf:title>Add nickname</sqf:title>
                </sqf:description>
                <sqf:user-entry name="nick" default="concat(name, 'y')">
                    <sqf:description>
                        <sqf:title>Nickname</sqf:title>
                        <sqf:p>The nickname to add to the person.</sqf:p>
                    </sqf:description>
                </sqf:user-entry>
                <sqf:add node-type="attribute" target="nickname"><xsl:value-of select="$nick"/></sqf:add>
            </sqf:fix>
        </sch:rule>
    </sch:pattern>
</sch:schema>
