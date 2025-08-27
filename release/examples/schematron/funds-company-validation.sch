<?xml version="1.0" encoding="UTF-8"?>
<sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
    <sch:title>FundsXML Company Data Validation</sch:title>
    <sch:ns prefix="fx" uri="http://www.fundsxml.org/FundsXML/4"/>

    <sch:pattern>
        <sch:title>Company Information Rules</sch:title>

        <sch:rule context="//CompanyType">
            <sch:assert test="CompanyName and string-length(normalize-space(CompanyName)) >= 2">
                Company name must be provided and contain at least 2 characters.
            </sch:assert>

            <sch:assert test="not(LEI) or matches(LEI, '^[A-Z0-9]{20}$')">
                If provided, Legal Entity Identifier (LEI) must be exactly 20 alphanumeric characters.
            </sch:assert>

            <sch:assert test="not(BIC) or matches(BIC, '^[A-Z]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?$')">
                If provided, BIC must follow the ISO 9362 format.
            </sch:assert>
        </sch:rule>

        <sch:rule context="//CompanyType/Address">
            <sch:assert test="CountryCode">
                Company address must include a country code.
            </sch:assert>

            <sch:assert test="matches(CountryCode, '^[A-Z]{2}$')">
                Country code must be a valid 2-letter ISO country code.
            </sch:assert>

            <sch:assert test="CityName and string-length(normalize-space(CityName)) >= 2">
                City name must be provided and contain at least 2 characters.
            </sch:assert>
        </sch:rule>

        <sch:rule context="//CompanyType[CompanyID]">
            <sch:assert test="string-length(CompanyID) >= 3">
                Company ID must contain at least 3 characters.
            </sch:assert>
        </sch:rule>
    </sch:pattern>
</sch:schema>