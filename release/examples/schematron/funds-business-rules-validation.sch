<?xml version="1.0" encoding="UTF-8"?>
<sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
    <sch:title>FundsXML Business Rules Validation</sch:title>
    <sch:ns prefix="fx" uri="http://www.fundsxml.org/FundsXML/4"/>

    <sch:pattern>
        <sch:title>Fund Business Logic Rules</sch:title>

        <sch:rule context="//Fund[LaunchDate]">
            <sch:assert test="xs:date(LaunchDate) le current-date()">
                Fund launch date cannot be in the future.
            </sch:assert>

            <sch:assert test="not(ClosureDate) or xs:date(LaunchDate) lt xs:date(ClosureDate)">
                Fund launch date must be before closure date when both are provided.
            </sch:assert>
        </sch:rule>

        <sch:rule context="//Fund[MinimumInvestment and MaximumInvestment]">
            <sch:assert test="number(MinimumInvestment) le number(MaximumInvestment)">
                Minimum investment amount must not exceed maximum investment amount.
            </sch:assert>
        </sch:rule>

        <sch:rule context="//Fund[AnnualManagementFee]">
            <sch:assert test="number(AnnualManagementFee) >= 0 and number(AnnualManagementFee) le 10">
                Annual management fee must be between 0% and 10%.
            </sch:assert>
        </sch:rule>

        <sch:rule context="//AllocationData[Percentage]">
            <sch:let name="totalPercentage" value="sum(Percentage)"/>
            <sch:assert test="$totalPercentage le 100.01 and $totalPercentage ge 99.99">
                Total allocation percentages should sum to approximately 100% (allowing for rounding).
            </sch:assert>
        </sch:rule>

        <sch:rule context="//Fund[FundStatus = 'Closed' or FundStatus = 'Liquidated']">
            <sch:assert test="ClosureDate">
                Closed or liquidated funds must have a closure date.
            </sch:assert>
        </sch:rule>

        <sch:rule context="//Fund[InceptionDate and LaunchDate]">
            <sch:assert test="xs:date(InceptionDate) le xs:date(LaunchDate)">
                Fund inception date must be on or before the launch date.
            </sch:assert>
        </sch:rule>

        <sch:rule context="//Asset[AssetType = 'Equity']">
            <sch:assert test="not(CouponRate) or number(CouponRate) = 0">
                Equity assets should not have coupon rates (or should be zero).
            </sch:assert>
        </sch:rule>

        <sch:rule context="//Asset[AssetType = 'Bond' and MaturityDate]">
            <sch:assert test="xs:date(MaturityDate) gt current-date()">
                Bond maturity date must be in the future for active bonds.
            </sch:assert>

            <sch:assert test="CouponRate">
                Bond assets must have a coupon rate specified.
            </sch:assert>
        </sch:rule>

        <sch:rule context="//RiskProfile">
            <sch:assert test="RiskLevel >= 1 and RiskLevel le 7">
                Risk level must be between 1 (lowest risk) and 7 (highest risk).
            </sch:assert>
        </sch:rule>

        <sch:rule context="//Fund[LiquidityProfile]">
            <sch:assert test="LiquidityProfile = 'Daily' or LiquidityProfile = 'Weekly' or 
                             LiquidityProfile = 'Monthly' or LiquidityProfile = 'Quarterly' or 
                             LiquidityProfile = 'Semi-Annual' or LiquidityProfile = 'Annual' or 
                             LiquidityProfile = 'Irregular'">
                Liquidity profile must be one of the standard values.
            </sch:assert>
        </sch:rule>
    </sch:pattern>
</sch:schema>