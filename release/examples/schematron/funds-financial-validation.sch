<?xml version="1.0" encoding="UTF-8"?>
<sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
    <sch:title>FundsXML Financial Data Validation</sch:title>
    <sch:ns prefix="fx" uri="http://www.fundsxml.org/FundsXML/4"/>

    <sch:pattern>
        <sch:title>Financial Amount and Account Rules</sch:title>

        <sch:rule context="//AmountType">
            <sch:assert test="Amount and Amount >= 0">
                Financial amounts must be provided and non-negative.
            </sch:assert>

            <sch:assert test="Currency and matches(Currency, '^[A-Z]{3}$')">
                Currency must be a valid 3-letter ISO currency code.
            </sch:assert>

            <sch:assert
                    test="not(Amount) or format-number(Amount, '#.##') = string(Amount) or string-length(substring-after(string(Amount), '.')) le 4">
                Amount precision should not exceed 4 decimal places.
            </sch:assert>
        </sch:rule>

        <sch:rule context="//InterestRateDebit | //InterestRateCredit">
            <sch:assert test=". >= 0 and . <= 100">
                Interest rates must be between 0% and 100%.
            </sch:assert>
        </sch:rule>

        <sch:rule context="//IBAN">
            <sch:assert test="matches(., '^[A-Z]{2}[0-9]{2}[A-Z0-9]{4}[0-9]{7}([A-Z0-9]?){0,16}$')">
                IBAN must follow the correct format with country code and check digits.
            </sch:assert>

            <sch:assert test="string-length(.) >= 15 and string-length(.) <= 34">
                IBAN length must be between 15 and 34 characters.
            </sch:assert>
        </sch:rule>

        <sch:rule context="//AccountNumber">
            <sch:assert test=". > 0">
                Account number must be a positive integer.
            </sch:assert>
        </sch:rule>

        <sch:rule context="//*[contains(local-name(), 'NAV') or contains(local-name(), 'Price')]">
            <sch:assert test="not(text()) or number(.) > 0">
                NAV and price values must be positive numbers when provided.
            </sch:assert>
        </sch:rule>
    </sch:pattern>
</sch:schema>