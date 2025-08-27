<?xml version="1.0" encoding="UTF-8"?>
<sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
    <sch:title>FundsXML Advanced Business Logic Validation</sch:title>
    <sch:ns prefix="fx" uri="http://www.fundsxml.org/FundsXML/4"/>

    <sch:pattern>
        <sch:title>Complex Business Logic and Regulatory Rules</sch:title>

        <!-- Advanced portfolio composition validation -->
        <sch:rule context="//Fund[FundType = 'UCITS']">
            <sch:let name="totalAssetValue" value="sum(.//Asset/MarketValue)"/>

            <!-- UCITS 5/10/40 rule: Max 5% in single issuer, 10% in single group, 40% in finance sector -->
            <sch:assert test="every $issuer in distinct-values(.//Asset/Issuer/CompanyID) 
                             satisfies (sum(.//Asset[Issuer/CompanyID = $issuer]/MarketValue) div $totalAssetValue) le 0.05">
                UCITS fund cannot invest more than 5% in securities from a single issuer.
            </sch:assert>

            <sch:assert test="every $group in distinct-values(.//Asset/Issuer/ParentCompany/CompanyID) 
                             satisfies (sum(.//Asset[Issuer/ParentCompany/CompanyID = $group]/MarketValue) div $totalAssetValue) le 0.10">
                UCITS fund cannot invest more than 10% in securities from a single group.
            </sch:assert>

            <sch:assert test="(sum(.//Asset[Sector = 'Financial']/MarketValue) div $totalAssetValue) le 0.40">
                UCITS fund cannot invest more than 40% in financial sector securities.
            </sch:assert>

            <!-- UCITS liquidity requirements -->
            <sch:assert test="(sum(.//Asset[LiquidityCategory = 'Daily']/MarketValue) div $totalAssetValue) ge 0.10">
                UCITS fund must maintain at least 10% in daily liquid assets.
            </sch:assert>
        </sch:rule>

        <!-- Alternative Investment Fund (AIF) leverage validation -->
        <sch:rule context="//Fund[FundType = 'AIF']">
            <sch:let name="nav" value="number(CurrentNAV/NAVValue)"/>
            <sch:let name="totalExposure" value="sum(.//Asset/MarketValue) + sum(.//Derivative/NotionalValue)"/>
            <sch:let name="leverage" value="$totalExposure div $nav"/>

            <sch:assert test="$leverage le MaximumLeverage or not(MaximumLeverage)">
                AIF leverage ratio of
                <sch:value-of select="format-number($leverage, '0.00')"/>
                exceeds maximum allowed leverage of<sch:value-of select="MaximumLeverage"/>.
            </sch:assert>

            <!-- AIF reporting thresholds -->
            <sch:assert test="($nav >= 100000000 and ReportingFrequency = 'Quarterly') or 
                             ($nav >= 500000000 and ReportingFrequency = 'Monthly') or 
                             $nav lt 100000000">
                AIF with NAV of<sch:value-of select="format-number($nav div 1000000, '0.0')"/>M must report quarterly
                (>100M) or monthly (>500M).
            </sch:assert>
        </sch:rule>

        <!-- Complex derivative validation -->
        <sch:rule context="//Derivative">
            <sch:let name="notional" value="number(NotionalValue)"/>
            <sch:let name="marketValue" value="number(MarketValue)"/>
            <sch:let name="maturityDate" value="MaturityDate"/>
            <sch:let name="underlyingType" value="UnderlyingAsset/AssetType"/>

            <!-- Validate derivative risk metrics -->
            <sch:assert test="not(Delta) or (number(Delta) >= -1 and number(Delta) le 1)">
                Derivative delta must be between -1 and 1.
            </sch:assert>

            <sch:assert test="not(Gamma) or number(Gamma) >= 0">
                Derivative gamma must be non-negative.
            </sch:assert>

            <!-- Validate derivative vs underlying correlation -->
            <sch:assert test="($underlyingType = 'Equity' and DerivativeType in ('Option', 'Future', 'Swap')) or
                             ($underlyingType = 'Bond' and DerivativeType in ('Option', 'Future', 'Swap', 'CDS')) or
                             ($underlyingType = 'Currency' and DerivativeType in ('Forward', 'Future', 'Option')) or
                             not($underlyingType)">
                Derivative type '<sch:value-of select="DerivativeType"/>' is not appropriate for underlying asset type '
                <sch:value-of select="$underlyingType"/>'.
            </sch:assert>

            <!-- Validate margin requirements -->
            <sch:assert test="not(InitialMargin and MaintenanceMargin) or 
                             number(MaintenanceMargin) le number(InitialMargin)">
                Maintenance margin cannot exceed initial margin.
            </sch:assert>
        </sch:rule>

        <!-- ESG and sustainability validation -->
        <sch:rule context="//Fund[SustainabilityDisclosure]">
            <sch:let name="esgAssets" value="sum(.//Asset[ESGRating]/MarketValue)"/>
            <sch:let name="totalAssets" value="sum(.//Asset/MarketValue)"/>
            <sch:let name="esgPercentage" value="$esgAssets div $totalAssets"/>

            <sch:assert test="(SustainabilityDisclosure = 'Article8' and $esgPercentage >= 0.51) or
                             (SustainabilityDisclosure = 'Article9' and $esgPercentage >= 0.80) or
                             SustainabilityDisclosure = 'Article6'">
                SFDR Article 8 funds must have >50% ESG assets, Article 9 funds must have >80% ESG assets.
            </sch:assert>

            <sch:assert test="not(SustainabilityDisclosure = 'Article9') or 
                             not(.//Asset[ESGRating and number(ESGRating) lt 3])">
                SFDR Article 9 funds cannot hold assets with ESG rating below 3.
            </sch:assert>
        </sch:rule>

        <!-- Complex fee calculation validation -->
        <sch:rule context="//FeeStructure">
            <sch:let name="managementFee" value="number(ManagementFee)"/>
            <sch:let name="performanceFee" value="number(PerformanceFee)"/>
            <sch:let name="totalExpenseRatio" value="number(TotalExpenseRatio)"/>

            <!-- Validate fee reasonableness -->
            <sch:assert test="$managementFee + $performanceFee le $totalExpenseRatio + 0.01">
                Sum of management fee (<sch:value-of select="$managementFee"/>%) and performance fee (<sch:value-of
                    select="$performanceFee"/>%) should not exceed TER (<sch:value-of select="$totalExpenseRatio"/>%).
            </sch:assert>

            <!-- High watermark validation for performance fees -->
            <sch:assert test="not($performanceFee > 0) or HighWaterMark = 'true'">
                Funds with performance fees must implement high watermark provisions.
            </sch:assert>

            <!-- Validate hurdle rate consistency -->
            <sch:assert test="not(HurdleRate and $performanceFee = 0)">
                Hurdle rate should only be specified when performance fees apply.
            </sch:assert>
        </sch:rule>

        <!-- Risk management validation -->
        <sch:rule context="//RiskMetrics">
            <sch:let name="var95" value="number(VaR95)"/>
            <sch:let name="var99" value="number(VaR99)"/>
            <sch:let name="volatility" value="number(Volatility)"/>
            <sch:let name="sharpeRatio" value="number(SharpeRatio)"/>

            <!-- VaR consistency checks -->
            <sch:assert test="not($var95 and $var99) or abs($var99) ge abs($var95)">
                99% VaR (<sch:value-of select="$var99"/>) should be greater than or equal to 95% VaR (<sch:value-of
                    select="$var95"/>) in absolute terms.
            </sch:assert>

            <!-- Volatility vs VaR relationship -->
            <sch:assert test="not($volatility and $var95) or abs($var95) le $volatility * 2">
                95% VaR should not exceed twice the volatility under normal market conditions.
            </sch:assert>

            <!-- Sharpe ratio reasonableness -->
            <sch:assert test="not($sharpeRatio) or ($sharpeRatio >= -3 and $sharpeRatio le 5)">
                Sharpe ratio of
                <sch:value-of select="$sharpeRatio"/>
                appears unrealistic (should be between -3 and 5).
            </sch:assert>
        </sch:rule>

        <!-- Currency hedging validation -->
        <sch:rule context="//Fund[CurrencyHedging = 'true']">
            <sch:let name="baseCurrency" value="BaseCurrency"/>
            <sch:let name="foreignExposure" value="sum(.//Asset[Currency != $baseCurrency]/MarketValue)"/>
            <sch:let name="hedgingInstruments"
                     value="sum(.//Derivative[DerivativeType = 'Forward' and UnderlyingAsset/AssetType = 'Currency']/NotionalValue)"/>
            <sch:let name="totalAssets" value="sum(.//Asset/MarketValue)"/>

            <sch:assert test="$hedgingInstruments >= ($foreignExposure * 0.8)">
                Currency hedged fund should hedge at least 80% of foreign currency exposure.
                Foreign exposure:<sch:value-of select="format-number($foreignExposure div $totalAssets * 100, '0.0')"/>
                %,
                Hedged:<sch:value-of select="format-number($hedgingInstruments div $totalAssets * 100, '0.0')"/>%.
            </sch:assert>
        </sch:rule>
    </sch:pattern>
</sch:schema>