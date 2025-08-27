<?xml version="1.0" encoding="UTF-8"?>
<sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
    <sch:title>FundsXML Control Data Validation</sch:title>
    <sch:ns prefix="fx" uri="http://www.fundsxml.org/FundsXML/4"/>

    <sch:pattern>
        <sch:title>Control Data Consistency Rules</sch:title>

        <sch:rule context="//ControlData">
            <sch:assert test="CreationDateTime">
                Control data must include a creation date/time.
            </sch:assert>

            <sch:assert test="DataSupplier">
                Control data must specify the data supplier.
            </sch:assert>

            <sch:assert test="string-length(Version) > 0">
                Version information must be provided and non-empty.
            </sch:assert>
        </sch:rule>

        <sch:rule context="//ControlData/CreationDateTime">
            <sch:assert test="matches(., '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}')">
                Creation date/time must be in ISO 8601 format (YYYY-MM-DDTHH:MM:SS).
            </sch:assert>

            <sch:assert test="xs:dateTime(.) le current-dateTime()">
                Creation date/time cannot be in the future.
            </sch:assert>
        </sch:rule>

        <sch:rule context="//ControlData/DataSupplier">
            <sch:assert test="CompanyName and string-length(CompanyName) >= 2">
                Data supplier must have a company name with at least 2 characters.
            </sch:assert>

            <sch:assert test="matches(ContactEmail, '^[^@]+@[^@]+\.[^@]+$')">
                Data supplier contact email must be in valid email format.
            </sch:assert>
        </sch:rule>
    </sch:pattern>
</sch:schema>