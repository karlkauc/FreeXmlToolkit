<?xml version="1.0" encoding="UTF-8"?>
<sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
    <sch:title>FundsXML Identifier and Code Validation</sch:title>
    <sch:ns prefix="fx" uri="http://www.fundsxml.org/FundsXML/4"/>

    <sch:pattern>
        <sch:title>Fund and Security Identifier Rules</sch:title>

        <sch:rule context="//Fund">
            <sch:assert test="@FundID and string-length(@FundID) >= 3">
                Each fund must have a Fund ID with at least 3 characters.
            </sch:assert>

            <sch:assert test="count(//Fund[@FundID = current()/@FundID]) = 1">
                Fund IDs must be unique within the document.
            </sch:assert>
        </sch:rule>

        <sch:rule context="//*[contains(local-name(), 'ISIN')]">
            <sch:assert test="matches(., '^[A-Z]{2}[A-Z0-9]{9}[0-9]$')">
                ISIN must follow the correct format: 2 country letters + 9 alphanumeric + 1 check digit.
            </sch:assert>

            <sch:assert test="string-length(.) = 12">
                ISIN must be exactly 12 characters long.
            </sch:assert>
        </sch:rule>

        <sch:rule context="//*[contains(local-name(), 'WKN')]">
            <sch:assert test="matches(., '^[A-Z0-9]{6}$') and string-length(.) = 6">
                German WKN must be exactly 6 alphanumeric characters.
            </sch:assert>
        </sch:rule>

        <sch:rule context="//*[contains(local-name(), 'CUSIP')]">
            <sch:assert test="matches(., '^[A-Z0-9]{9}$') and string-length(.) = 9">
                CUSIP must be exactly 9 alphanumeric characters.
            </sch:assert>
        </sch:rule>

        <sch:rule context="//*[contains(local-name(), 'SEDOL')]">
            <sch:assert test="matches(., '^[A-Z0-9]{7}$') and string-length(.) = 7">
                SEDOL must be exactly 7 alphanumeric characters.
            </sch:assert>
        </sch:rule>

        <sch:rule context="//Ticker">
            <sch:assert test="string-length(normalize-space(.)) >= 1 and string-length(normalize-space(.)) <= 10">
                Ticker symbol must be between 1 and 10 characters long.
            </sch:assert>

            <sch:assert test="matches(., '^[A-Z0-9.-]+$')">
                Ticker symbol should only contain uppercase letters, numbers, dots, and hyphens.
            </sch:assert>
        </sch:rule>

        <sch:rule context="//*[@Currency]">
            <sch:assert test="matches(@Currency, '^[A-Z]{3}$')">
                Currency attributes must be valid 3-letter ISO currency codes.
            </sch:assert>
        </sch:rule>
    </sch:pattern>
</sch:schema>