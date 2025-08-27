<?xml version="1.0" encoding="UTF-8"?>
<sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
    <sch:title>FundsXML Temporal and Historical Data Validation</sch:title>
    <sch:ns prefix="fx" uri="http://www.fundsxml.org/FundsXML/4"/>

    <sch:pattern>
        <sch:title>Complex Temporal Validation Rules</sch:title>

        <sch:rule context="//Fund">
            <sch:let name="launchDate" value="LaunchDate"/>
            <sch:let name="inceptionDate" value="InceptionDate"/>
            <sch:let name="closureDate" value="ClosureDate"/>

            <!-- Complex date sequence validation -->
            <sch:assert test="not($inceptionDate and $launchDate) or 
                             xs:date($inceptionDate) le xs:date($launchDate)">
                Fund inception date must be on or before launch date.
            </sch:assert>

            <sch:assert test="not($launchDate and $closureDate) or 
                             xs:date($launchDate) lt xs:date($closureDate)">
                Fund launch date must be before closure date.
            </sch:assert>

            <!-- Validate that historical data aligns with fund lifecycle -->
            <sch:assert test="not(.//HistoricalNAV/@Date[xs:date(.) lt xs:date($launchDate)])">
                Historical NAV dates cannot be before the fund launch date.
            </sch:assert>

            <sch:assert test="not($closureDate) or 
                             not(.//HistoricalNAV/@Date[xs:date(.) > xs:date($closureDate)])">
                Historical NAV dates cannot be after the fund closure date.
            </sch:assert>
        </sch:rule>

        <!-- NAV sequence and consistency validation -->
        <sch:rule context="//HistoricalNAV[position() > 1]">
            <sch:let name="currentDate" value="xs:date(@Date)"/>
            <sch:let name="previousDate" value="xs:date(preceding-sibling::HistoricalNAV[1]/@Date)"/>
            <sch:let name="currentNAV" value="number(NAVValue)"/>
            <sch:let name="previousNAV" value="number(preceding-sibling::HistoricalNAV[1]/NAVValue)"/>

            <!-- Validate chronological order -->
            <sch:assert test="$currentDate ge $previousDate">
                Historical NAV entries must be in chronological order. Date
                <sch:value-of select="@Date"/>
                comes before<sch:value-of select="preceding-sibling::HistoricalNAV[1]/@Date"/>.
            </sch:assert>

            <!-- Validate reasonable NAV changes (no more than 50% in one day) -->
            <sch:assert test="$currentDate = $previousDate or 
                             abs(($currentNAV - $previousNAV) div $previousNAV) le 0.5">
                NAV change of<sch:value-of
                    select="format-number(abs(($currentNAV - $previousNAV) div $previousNAV) * 100, '0.00')"/>% between
                <sch:value-of select="preceding-sibling::HistoricalNAV[1]/@Date"/>
                and
                <sch:value-of select="@Date"/>
                exceeds reasonable daily limit of 50%.
            </sch:assert>

            <!-- Validate no duplicate dates -->
            <sch:assert test="$currentDate != $previousDate or $currentNAV = $previousNAV">
                Duplicate NAV dates must have identical values. Date
                <sch:value-of select="@Date"/>
                has conflicting values.
            </sch:assert>
        </sch:rule>

        <!-- Performance data temporal validation -->
        <sch:rule context="//PerformanceData">
            <sch:let name="periodStart" value="xs:date(PeriodStart)"/>
            <sch:let name="periodEnd" value="xs:date(PeriodEnd)"/>
            <sch:let name="fundLaunch" value="xs:date(ancestor::Fund/LaunchDate)"/>

            <sch:assert test="$periodStart lt $periodEnd">
                Performance period start date must be before end date.
            </sch:assert>

            <sch:assert test="$periodStart ge $fundLaunch">
                Performance period cannot start before fund launch date.
            </sch:assert>

            <sch:assert test="$periodEnd le current-date()">
                Performance period end date cannot be in the future.
            </sch:assert>

            <!-- Validate performance period length for different return types -->
            <sch:assert test="(ReturnType = '1M' and days-from-duration($periodEnd - $periodStart) >= 28 and days-from-duration($periodEnd - $periodStart) <= 31) or
                             (ReturnType = '3M' and days-from-duration($periodEnd - $periodStart) >= 89 and days-from-duration($periodEnd - $periodStart) <= 92) or
                             (ReturnType = '1Y' and days-from-duration($periodEnd - $periodStart) >= 365 and days-from-duration($periodEnd - $periodStart) <= 366) or
                             (ReturnType = 'YTD' and month-from-date($periodStart) = 1 and day-from-date($periodStart) = 1) or
                             not(ReturnType = ('1M', '3M', '1Y', 'YTD'))">
                Performance period length must match the specified return type.
            </sch:assert>
        </sch:rule>

        <!-- Dividend and distribution temporal validation -->
        <sch:rule context="//Distribution">
            <sch:let name="exDate" value="ExDividendDate"/>
            <sch:let name="recordDate" value="RecordDate"/>
            <sch:let name="paymentDate" value="PaymentDate"/>

            <sch:assert test="not($exDate and $recordDate) or xs:date($exDate) le xs:date($recordDate)">
                Ex-dividend date must be on or before record date.
            </sch:assert>

            <sch:assert test="not($recordDate and $paymentDate) or xs:date($recordDate) le xs:date($paymentDate)">
                Record date must be on or before payment date.
            </sch:assert>

            <sch:assert test="not($exDate) or xs:date($exDate) ge xs:date(ancestor::Fund/LaunchDate)">
                Distribution ex-dividend date cannot be before fund launch.
            </sch:assert>

            <!-- Validate distribution frequency consistency -->
            <sch:assert test="not(ancestor::Fund/DistributionPolicy/Frequency = 'Annual') or
                             count(preceding-sibling::Distribution[year-from-date(xs:date(ExDividendDate)) = year-from-date(xs:date(current()/ExDividendDate))]) = 0">
                Annual distribution funds should only have one distribution per year.
            </sch:assert>
        </sch:rule>

        <!-- Market data freshness validation -->
        <sch:rule context="//MarketData">
            <sch:let name="dataDate" value="xs:date(@Date)"/>
            <sch:let name="daysDiff" value="days-from-duration(current-date() - $dataDate)"/>

            <sch:assert test="$daysDiff le 5">
                Market data dated
                <sch:value-of select="@Date"/>
                is
                <sch:value-of select="$daysDiff"/>
                days old and may be stale (maximum 5 days).
            </sch:assert>

            <!-- Weekend/holiday data validation -->
            <sch:assert
                    test="format-date($dataDate, '[F1]') != 'Saturday' and format-date($dataDate, '[F1]') != 'Sunday'">
                Market data should not be dated on weekends unless specifically justified.
            </sch:assert>
        </sch:rule>
    </sch:pattern>
</sch:schema>