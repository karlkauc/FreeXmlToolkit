<?xml version="1.0" encoding="UTF-8"?>
<sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2"
            xmlns:sqf="http://www.schematron-quickfix.com/validator/process"
            xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <sch:pattern id="person-rules">
        <sch:rule context="person">
            <sch:let name="generated-id" value="concat('p-', count(preceding-sibling::person) + 1)"/>
            <sch:assert test="@id" id="person-needs-id" sqf:fix="addId">Person must have an id.</sch:assert>
            <sch:report test="deprecated" sqf:fix="removeDeprecated">Deprecated element present.</sch:report>
            <sch:assert test="not(name)" sqf:fix="renameNameToFullName">Use fullName, not name.</sch:assert>
            <sch:assert test="not(phone) or matches(phone, '^\+')" sqf:fix="normalizePhone">Phone must start with +.</sch:assert>
            <sch:assert test="note or fullName" sqf:fix="addNote addEmptyNote" sqf:default-fix="addEmptyNote">Person needs a note or a fullName.</sch:assert>
            <sqf:fix id="addId">
                <sqf:description>
                    <sqf:title>Add generated id attribute</sqf:title>
                    <sqf:p>Adds an id derived from the element position.</sqf:p>
                </sqf:description>
                <sqf:add node-type="attribute" target="id"><xsl:value-of select="$generated-id"/></sqf:add>
            </sqf:fix>
            <sqf:fix id="removeDeprecated">
                <sqf:description>
                    <sqf:title>Remove deprecated element</sqf:title>
                </sqf:description>
                <sqf:delete match="deprecated"/>
            </sqf:fix>
            <sqf:fix id="renameNameToFullName">
                <sqf:description>
                    <sqf:title>Rename name to fullName</sqf:title>
                </sqf:description>
                <sqf:replace match="name" node-type="element" target="fullName">
                    <xsl:value-of select="."/>
                </sqf:replace>
            </sqf:fix>
            <sqf:fix id="normalizePhone">
                <sqf:description>
                    <sqf:title>Replace leading 00 with +</sqf:title>
                </sqf:description>
                <sqf:stringReplace match="phone/text()" regex="^00">+</sqf:stringReplace>
            </sqf:fix>
            <sqf:fix id="addNote">
                <sqf:description>
                    <sqf:title>Add note element</sqf:title>
                </sqf:description>
                <sqf:add node-type="element" target="note" position="last-child">generated note</sqf:add>
            </sqf:fix>
        </sch:rule>
        <sch:rule context="company">
            <!-- identical @test as the person id rule, disambiguated by rule context -->
            <sch:assert test="@id" sqf:fix="addCompanyMarker">Company must have an id.</sch:assert>
        </sch:rule>
    </sch:pattern>
    <sqf:fixes>
        <sqf:fix id="addCompanyMarker">
            <sqf:description>
                <sqf:title>Mark company for review</sqf:title>
            </sqf:description>
            <sqf:add node-type="attribute" target="review">pending</sqf:add>
        </sqf:fix>
        <sqf:fix id="addEmptyNote">
            <sqf:description>
                <sqf:title>Add empty note</sqf:title>
            </sqf:description>
            <sqf:add node-type="element" target="note" position="last-child"/>
        </sqf:fix>
    </sqf:fixes>
</sch:schema>
