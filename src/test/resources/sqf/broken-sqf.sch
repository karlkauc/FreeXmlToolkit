<?xml version="1.0" encoding="UTF-8"?>
<sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2"
            xmlns:sqf="http://www.schematron-quickfix.com/validator/process">
    <sch:pattern>
        <sch:rule context="person">
            <sch:assert test="@id" sqf:fix="missingFixId brokenFix">Person must have an id.</sch:assert>
            <!-- fix without the required @id attribute -->
            <sqf:fix>
                <sqf:description>
                    <sqf:title>Broken fix without id</sqf:title>
                </sqf:description>
                <sqf:delete match="."/>
            </sqf:fix>
        </sch:rule>
    </sch:pattern>
</sch:schema>
