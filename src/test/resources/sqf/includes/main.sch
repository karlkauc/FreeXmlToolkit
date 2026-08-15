<?xml version="1.0" encoding="UTF-8"?>
<sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2"
            xmlns:sqf="http://www.schematron-quickfix.com/validator/process">
    <sch:include href="included.sch"/>
    <sqf:fixes>
        <sqf:fix id="globalRemoveLegacy">
            <sqf:description>
                <sqf:title>Remove legacy element</sqf:title>
            </sqf:description>
            <sqf:delete match="legacy"/>
        </sqf:fix>
    </sqf:fixes>
</sch:schema>
