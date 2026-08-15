<?xml version="1.0" encoding="UTF-8"?>
<sch:pattern xmlns:sch="http://purl.oclc.org/dsdl/schematron"
             xmlns:sqf="http://www.schematron-quickfix.com/validator/process"
             id="included-pattern">
    <sch:rule context="item">
        <sch:report test="legacy" sqf:fix="globalRemoveLegacy">Legacy element present.</sch:report>
        <sch:assert test="@code" sqf:fix="addCode">Item must have a code.</sch:assert>
        <sqf:fix id="addCode">
            <sqf:description>
                <sqf:title>Add default code</sqf:title>
            </sqf:description>
            <sqf:add node-type="attribute" target="code">TODO</sqf:add>
        </sqf:fix>
    </sch:rule>
</sch:pattern>
