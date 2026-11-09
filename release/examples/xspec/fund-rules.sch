<?xml version="1.0" encoding="UTF-8"?>
<!--
  fund-rules.sch — a small Schematron under test (04-schematron.xspec).
  Every assert and report has an id: XSpec refers to them with x:expect-assert / x:expect-report.
-->
<sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">

    <sch:title>FundsXML example rules</sch:title>

    <sch:pattern id="identifiers">
        <sch:rule context="Identifiers/ISIN">
            <sch:assert id="isin-length" test="string-length(.) = 12">
                ISIN '<sch:value-of select="."/>' must have 12 characters.
            </sch:assert>
        </sch:rule>
    </sch:pattern>

    <sch:pattern id="currencies">
        <sch:rule context="Currency">
            <sch:assert id="currency-code" test="matches(., '^[A-Z]{3}$')">
                Currency '<sch:value-of select="."/>' must be a three-letter ISO 4217 code.
            </sch:assert>
        </sch:rule>
    </sch:pattern>

    <sch:pattern id="portfolio">
        <sch:rule context="Positions">
            <sch:assert id="weights-sum" test="abs(sum(Position/TotalPercentage) - 100) lt 0.01">
                The position weights add up to <sch:value-of select="sum(Position/TotalPercentage)"/> %, not 100 %.
            </sch:assert>
        </sch:rule>
        <sch:rule context="Position">
            <sch:report id="concentration" role="warning" test="number(TotalPercentage) gt 10">
                Position <sch:value-of select="UniqueID"/> holds <sch:value-of select="TotalPercentage"/> % of the fund.
            </sch:report>
        </sch:rule>
    </sch:pattern>

</sch:schema>
