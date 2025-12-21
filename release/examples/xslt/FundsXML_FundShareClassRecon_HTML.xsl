<?xml version="1.0" encoding="UTF-8"?>
<!--
    FundsXML4 Fund-ShareClass Reconciliation Report
    ================================================
    This XSLT generates an HTML report validating consistency between
    Fund-level totals and ShareClass aggregates.

    Layout: Side-by-side comparison with difference highlighting
    Theme: Slate (#2d3748) with Blue accents (#4299e1)

    Checks NOT in XSD (business logic only):
    - Sum(ShareClass TNA) ≈ Fund TNA (tolerance check)
    - Sum(ShareClass Ratio) = 100%
    - ShareClass currency consistency with Fund
    - NAV Price × Shares ≈ TNA calculation
-->
<xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:fn="http://www.w3.org/2005/xpath-functions">

    <xsl:output method="html" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <!-- Store ContentDate -->
    <xsl:variable name="contentDate" select="/FundsXML4/ControlData/ContentDate"/>

    <!-- Main Template -->
    <xsl:template match="/FundsXML4">
        <html lang="en">
            <head>
                <meta charset="UTF-8"/>
                <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                <title>FundsXML - Fund-ShareClass Reconciliation</title>
                <style>
                    /* Reset and Base Styles */
                    * { margin: 0; padding: 0; box-sizing: border-box; }

                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
                        background: linear-gradient(135deg, #2d3748 0%, #4a5568 50%, #718096 100%);
                        min-height: 100vh;
                        line-height: 1.6;
                        padding: 2rem;
                    }

                    .container { max-width: 1400px; margin: 0 auto; }

                    /* Main Card */
                    .main-card {
                        background: white;
                        border-radius: 1rem;
                        box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.35);
                        overflow: hidden;
                    }

                    /* Header */
                    .header {
                        background: linear-gradient(135deg, #2d3748 0%, #4a5568 50%, #4299e1 100%);
                        padding: 2.5rem;
                        color: white;
                        text-align: center;
                    }

                    .header h1 {
                        font-size: 2.5rem;
                        font-weight: 700;
                        margin-bottom: 0.5rem;
                        text-shadow: 0 2px 4px rgba(0,0,0,0.2);
                    }

                    .header p { color: #bee3f8; font-size: 1.1rem; }
                    .header .subtitle { margin-top: 0.5rem; font-size: 0.9rem; color: #90cdf4; }

                    /* Content */
                    .content { padding: 2rem; }

                    /* Comparison Layout */
                    .comparison {
                        display: grid;
                        grid-template-columns: 1fr auto 1fr;
                        gap: 0;
                        margin-bottom: 2rem;
                        align-items: stretch;
                    }

                    @media (max-width: 900px) {
                        .comparison { grid-template-columns: 1fr; }
                        .comparison-divider { display: none; }
                    }

                    .comparison-panel {
                        background: #f9fafb;
                        border: 1px solid #e5e7eb;
                        border-radius: 0.75rem;
                        overflow: hidden;
                    }

                    .comparison-panel.fund { border-color: #4299e1; }
                    .comparison-panel.shareclasses { border-color: #38a169; }

                    .panel-header {
                        padding: 1.25rem 1.5rem;
                        color: white;
                        display: flex;
                        align-items: center;
                        gap: 0.75rem;
                    }

                    .panel-header.fund { background: linear-gradient(135deg, #2b6cb0 0%, #4299e1 100%); }
                    .panel-header.shareclasses { background: linear-gradient(135deg, #276749 0%, #38a169 100%); }

                    .panel-header .icon { font-size: 1.5rem; }
                    .panel-header h3 { font-size: 1.1rem; font-weight: 600; }

                    .panel-body { padding: 1.5rem; }

                    .big-value {
                        text-align: center;
                        padding: 1.5rem;
                        background: white;
                        border-radius: 0.5rem;
                        margin-bottom: 1rem;
                    }

                    .big-value .amount {
                        font-size: 1.75rem;
                        font-weight: 700;
                        color: #1a202c;
                    }

                    .big-value .currency {
                        font-size: 1rem;
                        color: #718096;
                        margin-left: 0.25rem;
                    }

                    .big-value .label {
                        font-size: 0.85rem;
                        color: #718096;
                        margin-top: 0.25rem;
                    }

                    /* Divider */
                    .comparison-divider {
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        justify-content: center;
                        padding: 1rem;
                    }

                    .divider-content {
                        background: #f9fafb;
                        border: 2px solid #e5e7eb;
                        border-radius: 0.75rem;
                        padding: 1.5rem 1rem;
                        text-align: center;
                    }

                    .divider-icon { font-size: 2rem; margin-bottom: 0.5rem; }
                    .divider-label { font-size: 0.8rem; color: #718096; font-weight: 600; }
                    .divider-value { font-size: 1.25rem; font-weight: 700; margin-top: 0.5rem; }
                    .divider-value.match { color: #059669; }
                    .divider-value.close { color: #d97706; }
                    .divider-value.mismatch { color: #dc2626; }

                    /* Section */
                    .section {
                        background: #f9fafb;
                        border: 1px solid #e5e7eb;
                        border-radius: 0.75rem;
                        margin-bottom: 1.5rem;
                        overflow: hidden;
                    }

                    .section-header {
                        background: linear-gradient(135deg, #2d3748 0%, #4a5568 100%);
                        color: white;
                        padding: 1rem 1.5rem;
                        display: flex;
                        align-items: center;
                        gap: 0.75rem;
                    }

                    .section-header .icon { font-size: 1.5rem; }
                    .section-header h2 { font-size: 1.25rem; font-weight: 600; }
                    .section-header .badge {
                        margin-left: auto;
                        background: rgba(255,255,255,0.2);
                        padding: 0.25rem 0.75rem;
                        border-radius: 1rem;
                        font-size: 0.8rem;
                    }

                    .section-body { padding: 1.5rem; }

                    /* ShareClass Cards */
                    .shareclass-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
                        gap: 1rem;
                    }

                    .shareclass-card {
                        background: white;
                        border: 1px solid #e5e7eb;
                        border-radius: 0.5rem;
                        padding: 1rem;
                        transition: box-shadow 0.2s, border-color 0.2s;
                    }

                    .shareclass-card:hover {
                        box-shadow: 0 4px 12px rgba(0,0,0,0.1);
                        border-color: #4299e1;
                    }

                    .shareclass-card .header-row {
                        display: flex;
                        justify-content: space-between;
                        align-items: flex-start;
                        margin-bottom: 0.75rem;
                        padding-bottom: 0.75rem;
                        border-bottom: 1px solid #e5e7eb;
                    }

                    .shareclass-card .name {
                        font-weight: 600;
                        color: #1a202c;
                        font-size: 0.95rem;
                    }

                    .shareclass-card .isin {
                        font-family: 'SF Mono', Consolas, monospace;
                        font-size: 0.8rem;
                        color: #718096;
                        background: #f3f4f6;
                        padding: 0.2rem 0.5rem;
                        border-radius: 0.25rem;
                    }

                    .shareclass-card .data-row {
                        display: flex;
                        justify-content: space-between;
                        padding: 0.4rem 0;
                        font-size: 0.875rem;
                    }

                    .shareclass-card .data-row .label { color: #718096; }
                    .shareclass-card .data-row .value { font-weight: 600; color: #1a202c; }

                    /* Data Table */
                    .data-table {
                        width: 100%;
                        border-collapse: collapse;
                        font-size: 0.875rem;
                    }

                    .data-table th {
                        background: #2d3748;
                        color: white;
                        padding: 0.75rem 1rem;
                        text-align: left;
                        font-weight: 600;
                    }

                    .data-table td {
                        padding: 0.75rem 1rem;
                        border-bottom: 1px solid #e5e7eb;
                    }

                    .data-table tr:nth-child(even) { background: #f9fafb; }
                    .data-table tr:hover { background: #edf2f7; }

                    .data-table .total-row {
                        background: #edf2f7 !important;
                        font-weight: 700;
                    }

                    /* Status Badges */
                    .badge-pass {
                        background: #d1fae5;
                        color: #059669;
                        padding: 0.25rem 0.5rem;
                        border-radius: 0.25rem;
                        font-size: 0.75rem;
                        font-weight: 600;
                    }

                    .badge-warn {
                        background: #fef3c7;
                        color: #d97706;
                        padding: 0.25rem 0.5rem;
                        border-radius: 0.25rem;
                        font-size: 0.75rem;
                        font-weight: 600;
                    }

                    .badge-fail {
                        background: #fee2e2;
                        color: #dc2626;
                        padding: 0.25rem 0.5rem;
                        border-radius: 0.25rem;
                        font-size: 0.75rem;
                        font-weight: 600;
                    }

                    /* Difference indicator */
                    .diff-positive { color: #059669; }
                    .diff-negative { color: #dc2626; }

                    /* Check Result Box */
                    .check-result {
                        display: flex;
                        align-items: center;
                        gap: 1rem;
                        padding: 1rem;
                        background: white;
                        border-radius: 0.5rem;
                        margin-bottom: 0.75rem;
                        border: 1px solid #e5e7eb;
                    }

                    .check-result.pass { border-left: 4px solid #059669; }
                    .check-result.fail { border-left: 4px solid #dc2626; }
                    .check-result.warn { border-left: 4px solid #d97706; }

                    .check-result .icon { font-size: 1.5rem; }
                    .check-result .info { flex: 1; }
                    .check-result .title { font-weight: 600; color: #1f2937; }
                    .check-result .desc { font-size: 0.85rem; color: #6b7280; }
                    .check-result .status { font-weight: 700; font-size: 0.9rem; }

                    /* Footer */
                    .footer {
                        text-align: center;
                        padding: 1.5rem;
                        background: #f9fafb;
                        border-top: 1px solid #e5e7eb;
                        color: #6b7280;
                        font-size: 0.85rem;
                    }

                    .mono { font-family: 'SF Mono', Consolas, Monaco, monospace; font-size: 0.85rem; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="main-card">
                        <!-- Header -->
                        <div class="header">
                            <h1>Fund-ShareClass Reconciliation</h1>
                            <p>Multi-Level Value Consistency Check</p>
                            <div class="subtitle">
                                Content Date: <xsl:value-of select="$contentDate"/> |
                                Generated: <xsl:value-of select="format-dateTime(current-dateTime(), '[Y0001]-[M01]-[D01] [H01]:[m01]:[s01]')"/>
                            </div>
                        </div>

                        <div class="content">
                            <!-- Process each Fund -->
                            <xsl:for-each select="Funds/Fund">
                                <xsl:variable name="fundName" select="Names/OfficialName"/>
                                <xsl:variable name="fundCurrency" select="Currency"/>
                                <xsl:variable name="fundTNA" select="FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount"/>
                                <xsl:variable name="fundTNAValue" select="number($fundTNA)"/>

                                <!-- ShareClass totals -->
                                <xsl:variable name="shareClasses" select="FundDynamicData/ShareClasses/ShareClass"/>
                                <xsl:variable name="shareClassCount" select="count($shareClasses)"/>
                                <xsl:variable name="sumShareClassTNA" select="sum($shareClasses/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount)"/>
                                <xsl:variable name="sumShareClassRatio" select="sum($shareClasses/TotalAssetValues/TotalAssetValue/Ratio) * 100"/>

                                <!-- Calculate difference (with zero protection) -->
                                <xsl:variable name="tnaDiff" select="$fundTNAValue - $sumShareClassTNA"/>
                                <xsl:variable name="tnaDiffPct" select="if ($fundTNAValue > 0) then abs($tnaDiff) div $fundTNAValue * 100 else 0"/>
                                <xsl:variable name="ratioDiff" select="abs(100 - $sumShareClassRatio)"/>

                                <!-- Fund Title -->
                                <div class="section">
                                    <div class="section-header">
                                        <span class="icon">&#127974;</span>
                                        <h2><xsl:value-of select="$fundName"/></h2>
                                        <span class="badge"><xsl:value-of select="$shareClassCount"/> Share Classes</span>
                                    </div>
                                </div>

                                <!-- Side-by-Side Comparison -->
                                <div class="comparison">
                                    <!-- Fund Panel -->
                                    <div class="comparison-panel fund">
                                        <div class="panel-header fund">
                                            <span class="icon">&#128188;</span>
                                            <h3>Fund Level</h3>
                                        </div>
                                        <div class="panel-body">
                                            <div class="big-value">
                                                <div class="amount">
                                                    <xsl:value-of select="format-number($fundTNAValue, '#,##0.00')"/>
                                                    <span class="currency"><xsl:value-of select="$fundCurrency"/></span>
                                                </div>
                                                <div class="label">Total Net Asset Value</div>
                                            </div>
                                            <div class="shareclass-card">
                                                <div class="data-row">
                                                    <span class="label">Currency</span>
                                                    <span class="value"><xsl:value-of select="$fundCurrency"/></span>
                                                </div>
                                                <div class="data-row">
                                                    <span class="label">NAV Date</span>
                                                    <span class="value"><xsl:value-of select="FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate"/></span>
                                                </div>
                                                <div class="data-row">
                                                    <span class="label">LEI</span>
                                                    <span class="value mono"><xsl:value-of select="Identifiers/LEI"/></span>
                                                </div>
                                            </div>
                                        </div>
                                    </div>

                                    <!-- Divider with comparison -->
                                    <div class="comparison-divider">
                                        <div class="divider-content">
                                            <div class="divider-icon">
                                                <xsl:choose>
                                                    <xsl:when test="$tnaDiffPct &lt; 0.01">&#9989;</xsl:when>
                                                    <xsl:when test="$tnaDiffPct &lt; 1">&#9888;</xsl:when>
                                                    <xsl:otherwise>&#10060;</xsl:otherwise>
                                                </xsl:choose>
                                            </div>
                                            <div class="divider-label">TNA DIFFERENCE</div>
                                            <div class="divider-value {if ($tnaDiffPct &lt; 0.01) then 'match' else if ($tnaDiffPct &lt; 1) then 'close' else 'mismatch'}">
                                                <xsl:choose>
                                                    <xsl:when test="$tnaDiff >= 0">+</xsl:when>
                                                    <xsl:otherwise>-</xsl:otherwise>
                                                </xsl:choose>
                                                <xsl:value-of select="format-number(abs($tnaDiff), '#,##0.00')"/>
                                            </div>
                                            <div class="divider-label" style="margin-top: 0.5rem;">
                                                (<xsl:value-of select="format-number($tnaDiffPct, '0.00')"/>%)
                                            </div>
                                        </div>
                                    </div>

                                    <!-- ShareClasses Panel -->
                                    <div class="comparison-panel shareclasses">
                                        <div class="panel-header shareclasses">
                                            <span class="icon">&#128200;</span>
                                            <h3>Sum of ShareClasses</h3>
                                        </div>
                                        <div class="panel-body">
                                            <div class="big-value">
                                                <div class="amount">
                                                    <xsl:value-of select="format-number($sumShareClassTNA, '#,##0.00')"/>
                                                    <span class="currency"><xsl:value-of select="$fundCurrency"/></span>
                                                </div>
                                                <div class="label">Combined TNA (<xsl:value-of select="$shareClassCount"/> classes)</div>
                                            </div>
                                            <div class="shareclass-card">
                                                <div class="data-row">
                                                    <span class="label">Total Ratio Sum</span>
                                                    <span class="value">
                                                        <xsl:value-of select="format-number($sumShareClassRatio, '0.00')"/>%
                                                        <xsl:if test="$ratioDiff > 0.01">
                                                            <xsl:text> </xsl:text>
                                                            <span class="{if ($ratioDiff &lt; 1) then 'badge-warn' else 'badge-fail'}">
                                                                <xsl:choose>
                                                                    <xsl:when test="$sumShareClassRatio > 100">+</xsl:when>
                                                                    <xsl:otherwise>-</xsl:otherwise>
                                                                </xsl:choose>
                                                                <xsl:value-of select="format-number($ratioDiff, '0.00')"/>%
                                                            </span>
                                                        </xsl:if>
                                                    </span>
                                                </div>
                                                <div class="data-row">
                                                    <span class="label">Avg. TNA per Class</span>
                                                    <span class="value">
                                                        <xsl:choose>
                                                            <xsl:when test="$shareClassCount > 0">
                                                                <xsl:value-of select="format-number($sumShareClassTNA div $shareClassCount, '#,##0.00')"/>
                                                            </xsl:when>
                                                            <xsl:otherwise>N/A</xsl:otherwise>
                                                        </xsl:choose>
                                                    </span>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </div>

                                <!-- Reconciliation Checks -->
                                <div class="section">
                                    <div class="section-header">
                                        <span class="icon">&#9989;</span>
                                        <h2>Reconciliation Checks</h2>
                                    </div>
                                    <div class="section-body">
                                        <!-- Check 1: TNA Match -->
                                        <div class="check-result {if ($tnaDiffPct &lt; 0.01) then 'pass' else if ($tnaDiffPct &lt; 1) then 'warn' else 'fail'}">
                                            <span class="icon">
                                                <xsl:choose>
                                                    <xsl:when test="$tnaDiffPct &lt; 0.01">&#9989;</xsl:when>
                                                    <xsl:when test="$tnaDiffPct &lt; 1">&#9888;</xsl:when>
                                                    <xsl:otherwise>&#10060;</xsl:otherwise>
                                                </xsl:choose>
                                            </span>
                                            <div class="info">
                                                <div class="title">Fund TNA = Sum(ShareClass TNA)</div>
                                                <div class="desc">Difference: <xsl:value-of select="format-number(abs($tnaDiff), '#,##0.00')"/> (<xsl:value-of select="format-number($tnaDiffPct, '0.00')"/>%)</div>
                                            </div>
                                            <div class="status">
                                                <xsl:choose>
                                                    <xsl:when test="$tnaDiffPct &lt; 0.01"><span class="badge-pass">MATCH</span></xsl:when>
                                                    <xsl:when test="$tnaDiffPct &lt; 1"><span class="badge-warn">CLOSE</span></xsl:when>
                                                    <xsl:otherwise><span class="badge-fail">MISMATCH</span></xsl:otherwise>
                                                </xsl:choose>
                                            </div>
                                        </div>

                                        <!-- Check 2: Ratio Sum = 100% -->
                                        <div class="check-result {if ($ratioDiff &lt; 0.01) then 'pass' else if ($ratioDiff &lt; 1) then 'warn' else 'fail'}">
                                            <span class="icon">
                                                <xsl:choose>
                                                    <xsl:when test="$ratioDiff &lt; 0.01">&#9989;</xsl:when>
                                                    <xsl:when test="$ratioDiff &lt; 1">&#9888;</xsl:when>
                                                    <xsl:otherwise>&#10060;</xsl:otherwise>
                                                </xsl:choose>
                                            </span>
                                            <div class="info">
                                                <div class="title">Sum(ShareClass Ratio) = 100%</div>
                                                <div class="desc">Actual: <xsl:value-of select="format-number($sumShareClassRatio, '0.00')"/>% (Diff: <xsl:value-of select="format-number($ratioDiff, '0.00')"/>%)</div>
                                            </div>
                                            <div class="status">
                                                <xsl:choose>
                                                    <xsl:when test="$ratioDiff &lt; 0.01"><span class="badge-pass">100%</span></xsl:when>
                                                    <xsl:when test="$ratioDiff &lt; 1"><span class="badge-warn">~100%</span></xsl:when>
                                                    <xsl:otherwise><span class="badge-fail">OFF</span></xsl:otherwise>
                                                </xsl:choose>
                                            </div>
                                        </div>

                                        <!-- Check 3: Currency Consistency -->
                                        <xsl:variable name="currencyMismatch" select="$shareClasses[Currency != $fundCurrency]"/>
                                        <div class="check-result {if (count($currencyMismatch) = 0) then 'pass' else 'warn'}">
                                            <span class="icon">
                                                <xsl:choose>
                                                    <xsl:when test="count($currencyMismatch) = 0">&#9989;</xsl:when>
                                                    <xsl:otherwise>&#9888;</xsl:otherwise>
                                                </xsl:choose>
                                            </span>
                                            <div class="info">
                                                <div class="title">Currency Consistency</div>
                                                <div class="desc">All ShareClasses use Fund currency (<xsl:value-of select="$fundCurrency"/>)</div>
                                            </div>
                                            <div class="status">
                                                <xsl:choose>
                                                    <xsl:when test="count($currencyMismatch) = 0"><span class="badge-pass">CONSISTENT</span></xsl:when>
                                                    <xsl:otherwise><span class="badge-warn"><xsl:value-of select="count($currencyMismatch)"/> DIFFERENT</span></xsl:otherwise>
                                                </xsl:choose>
                                            </div>
                                        </div>
                                    </div>
                                </div>

                                <!-- ShareClass Detail Table -->
                                <div class="section">
                                    <div class="section-header">
                                        <span class="icon">&#128203;</span>
                                        <h2>ShareClass Details</h2>
                                    </div>
                                    <div class="section-body">
                                        <table class="data-table">
                                            <thead>
                                                <tr>
                                                    <th>ISIN</th>
                                                    <th>Name</th>
                                                    <th>Currency</th>
                                                    <th>NAV Price</th>
                                                    <th>Shares Outstanding</th>
                                                    <th>TNA</th>
                                                    <th>Ratio</th>
                                                    <th>NAV Check</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                <xsl:for-each select="$shareClasses">
                                                    <xsl:variable name="navPrice" select="Prices/Price/NavPrice"/>
                                                    <xsl:variable name="shares" select="TotalAssetValues/TotalAssetValue/SharesOutstanding"/>
                                                    <xsl:variable name="tna" select="TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount"/>
                                                    <xsl:variable name="ratio" select="TotalAssetValues/TotalAssetValue/Ratio * 100"/>
                                                    <xsl:variable name="calcTNA" select="number($navPrice) * number($shares)"/>
                                                    <xsl:variable name="navCheckDiff" select="abs(number($tna) - $calcTNA) div number($tna) * 100"/>
                                                    <tr>
                                                        <td class="mono"><xsl:value-of select="Identifiers/ISIN"/></td>
                                                        <td><xsl:value-of select="substring(Names/OfficialName, 1, 30)"/></td>
                                                        <td><xsl:value-of select="Currency"/></td>
                                                        <td><xsl:value-of select="format-number($navPrice, '#,##0.00')"/></td>
                                                        <td><xsl:value-of select="format-number($shares, '#,##0.000')"/></td>
                                                        <td><xsl:value-of select="format-number($tna, '#,##0.00')"/></td>
                                                        <td><xsl:value-of select="format-number($ratio, '0.00')"/>%</td>
                                                        <td>
                                                            <xsl:choose>
                                                                <xsl:when test="$navCheckDiff &lt; 1"><span class="badge-pass">OK</span></xsl:when>
                                                                <xsl:when test="$navCheckDiff &lt; 5"><span class="badge-warn"><xsl:value-of select="format-number($navCheckDiff, '0.0')"/>%</span></xsl:when>
                                                                <xsl:otherwise><span class="badge-fail"><xsl:value-of select="format-number($navCheckDiff, '0.0')"/>%</span></xsl:otherwise>
                                                            </xsl:choose>
                                                        </td>
                                                    </tr>
                                                </xsl:for-each>
                                                <!-- Total Row -->
                                                <tr class="total-row">
                                                    <td colspan="5">TOTAL</td>
                                                    <td><xsl:value-of select="format-number($sumShareClassTNA, '#,##0.00')"/></td>
                                                    <td><xsl:value-of select="format-number($sumShareClassRatio, '0.00')"/>%</td>
                                                    <td></td>
                                                </tr>
                                            </tbody>
                                        </table>
                                    </div>
                                </div>
                            </xsl:for-each>
                        </div>

                        <!-- Footer -->
                        <div class="footer">
                            Generated by FreeXmlToolkit |
                            Document ID: <xsl:value-of select="ControlData/UniqueDocumentID"/> |
                            Data Supplier: <xsl:value-of select="ControlData/DataSupplier/Name"/>
                        </div>
                    </div>
                </div>
            </body>
        </html>
    </xsl:template>
</xsl:stylesheet>
