<?xml version="1.0" encoding="UTF-8"?>
<sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2"
            xmlns:sqf="http://www.schematron-quickfix.com/validator/process">
    <sch:pattern>
        <sch:rule context="doc">
            <sch:report test="marker" sqf:fix="removeAllMarkers">Markers present.</sch:report>
            <sqf:fix id="removeAllMarkers">
                <sqf:description>
                    <sqf:title>Remove all markers</sqf:title>
                </sqf:description>
                <sqf:call-fix ref="removeMatching">
                    <sqf:with-param name="victims" select="marker"/>
                </sqf:call-fix>
            </sqf:fix>
        </sch:rule>
        <sch:rule context="direct">
            <!-- referencing a default-less generic fix directly must offer nothing -->
            <sch:report test="true()" sqf:fix="removeMatching">Direct generic reference.</sch:report>
        </sch:rule>
        <sch:rule context="loop">
            <sch:report test="true()" sqf:fix="cycleA">Loop detected.</sch:report>
            <sqf:fix id="cycleA">
                <sqf:description><sqf:title>Cycle A</sqf:title></sqf:description>
                <sqf:call-fix ref="cycleB"/>
            </sqf:fix>
            <sqf:fix id="cycleB">
                <sqf:description><sqf:title>Cycle B</sqf:title></sqf:description>
                <sqf:call-fix ref="cycleA"/>
            </sqf:fix>
        </sch:rule>
    </sch:pattern>
    <sqf:fixes>
        <!-- generic fix: only reachable via sqf:call-fix (param without default) -->
        <sqf:fix id="removeMatching">
            <sqf:description>
                <sqf:title>Remove matching nodes</sqf:title>
            </sqf:description>
            <sqf:param name="victims"/>
            <sqf:delete match="$victims"/>
        </sqf:fix>
    </sqf:fixes>
</sch:schema>
