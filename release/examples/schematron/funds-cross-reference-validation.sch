<?xml version="1.0" encoding="UTF-8"?>
<sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
    <sch:title>FundsXML Cross-Reference and Consistency Validation</sch:title>
    <sch:ns prefix="fx" uri="http://www.fundsxml.org/FundsXML/4"/>

    <sch:pattern>
        <sch:title>Complex Cross-Reference Rules</sch:title>

        <sch:rule context="//Fund">
            <sch:let name="fundCurrency" value="BaseCurrency"/>
            <sch:let name="fundId" value="@FundID"/>

            <!-- Validate that all NAV entries for this fund use the same base currency -->
            <sch:assert test="count(distinct-values(.//NAV[@Currency != $fundCurrency])) = 0">
                All NAV values for fund '<sch:value-of select="$fundId"/>' must be in the fund's base currency '
                <sch:value-of select="$fundCurrency"/>'.
            </sch:assert>

            <!-- Validate that fund manager is referenced consistently -->
            <sch:assert test="count(distinct-values(.//Company[Role = 'Manager']/CompanyName)) le 1">
                Fund '<sch:value-of select="$fundId"/>' should not have multiple different manager names.
            </sch:assert>

            <!-- Complex allocation validation -->
            <sch:assert test="not(.//AllocationData) or 
                             (sum(.//AllocationData/Allocation/Percentage) >= 99.5 and 
                              sum(.//AllocationData/Allocation/Percentage) <= 100.5)">
                Total allocations for fund '<sch:value-of select="$fundId"/>' must sum to approximately 100% (±0.5%).
            </sch:assert>
        </sch:rule>

        <sch:rule context="//Asset">
            <sch:let name="assetId" value="AssetID"/>
            <sch:let name="assetType" value="AssetType"/>

            <!-- Cross-validate asset references in portfolios -->
            <sch:assert
                    test="count(//Portfolio//AssetID[. = $assetId]) >= count(//AssetMasterData//Asset[AssetID = $assetId])">
                Asset '<sch:value-of select="$assetId"/>' is referenced in master data but may not be properly linked in
                portfolios.
            </sch:assert>

            <!-- Validate asset type consistency with market data -->
            <sch:assert test="not(MarketData) or 
                             ($assetType = 'Equity' and MarketData/StockPrice) or
                             ($assetType = 'Bond' and MarketData/BondPrice) or
                             ($assetType = 'Fund' and MarketData/NAV) or
                             ($assetType not in ('Equity', 'Bond', 'Fund'))">
                Asset '<sch:value-of select="$assetId"/>' of type '<sch:value-of select="$assetType"/>' must have
                appropriate market data type.
            </sch:assert>
        </sch:rule>

        <sch:rule context="//CompanyType[CompanyID]">
            <sch:let name="companyId" value="CompanyID"/>

            <!-- Validate company information consistency across document -->
            <sch:assert test="count(distinct-values(//CompanyType[CompanyID = $companyId]/CompanyName)) = 1">
                Company '<sch:value-of select="$companyId"/>' must have consistent company name across all references.
            </sch:assert>

            <sch:assert
                    test="count(distinct-values(//CompanyType[CompanyID = $companyId]/LEI[string-length(.) > 0])) le 1">
                Company '<sch:value-of select="$companyId"/>' must have consistent LEI across all references.
            </sch:assert>
        </sch:rule>

        <!-- Complex conditional validation based on fund status -->
        <sch:rule context="//Fund[FundStatus = 'Active']">
            <sch:assert test="not(ClosureDate) or xs:date(ClosureDate) > current-date()">
                Active funds cannot have a closure date in the past.
            </sch:assert>

            <sch:assert test="count(.//NAV[xs:date(@Date) >= current-date() - xs:dayTimeDuration('P90D')]) > 0">
                Active funds must have at least one NAV within the last 90 days.
            </sch:assert>
        </sch:rule>

        <!-- Validate regulatory reporting consistency -->
        <sch:rule context="//RegulatoryReporting">
            <sch:let name="reportingCountry" value="CountryCode"/>
            <sch:let name="fundDomicile" value="ancestor::Fund/Domicile/CountryCode"/>

            <sch:assert test="$reportingCountry = $fundDomicile or 
                             exists(//Fund/DistributionCountries/Country[CountryCode = $reportingCountry])">
                Regulatory reporting for country '<sch:value-of select="$reportingCountry"/>' is only valid if the fund
                is domiciled there or distributed there.
            </sch:assert>
        </sch:rule>
    </sch:pattern>
</sch:schema>