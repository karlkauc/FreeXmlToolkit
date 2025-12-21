<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xs">

    <xsl:output method="html" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <!-- Key for fast asset lookup -->
    <xsl:key name="asset-by-id" match="Asset" use="UniqueID"/>

    <!-- Main Template -->
    <xsl:template match="/FundsXML4">
        <html lang="en">
            <head>
                <meta charset="UTF-8"/>
                <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                <title>Fund Factsheet - <xsl:value-of select="Funds/Fund/Names/OfficialName"/></title>
                <style>
                    :root {
                        --primary: #1e3a5f;
                        --primary-light: #2d5a8e;
                        --secondary: #17a2b8;
                        --accent: #28a745;
                        --warning: #ffc107;
                        --danger: #dc3545;
                        --text-dark: #2c3e50;
                        --text-light: #6c757d;
                        --bg-light: #f8f9fa;
                        --bg-card: #ffffff;
                        --border-color: #e9ecef;
                        --shadow: 0 4px 15px rgba(0,0,0,0.08);
                        --shadow-hover: 0 8px 25px rgba(0,0,0,0.12);
                    }

                    * { box-sizing: border-box; margin: 0; padding: 0; }

                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                        line-height: 1.6;
                        color: var(--text-dark);
                        background: linear-gradient(135deg, #f5f7fa 0%, #e4e8ec 100%);
                        min-height: 100vh;
                        padding: 30px;
                    }

                    .container {
                        max-width: 1200px;
                        margin: 0 auto;
                    }

                    /* Header Section */
                    .header {
                        background: linear-gradient(135deg, var(--primary) 0%, var(--primary-light) 100%);
                        color: white;
                        padding: 40px;
                        border-radius: 16px;
                        margin-bottom: 30px;
                        box-shadow: var(--shadow);
                        position: relative;
                        overflow: hidden;
                    }

                    .header::before {
                        content: '';
                        position: absolute;
                        top: -50%;
                        right: -30%;
                        width: 60%;
                        height: 200%;
                        background: linear-gradient(45deg, transparent 30%, rgba(255,255,255,0.05) 50%, transparent 70%);
                        transform: rotate(-20deg);
                    }

                    .header h1 {
                        font-size: 2.2rem;
                        font-weight: 700;
                        margin-bottom: 10px;
                        position: relative;
                    }

                    .header-subtitle {
                        font-size: 1.1rem;
                        opacity: 0.9;
                        display: flex;
                        gap: 30px;
                        flex-wrap: wrap;
                        margin-top: 15px;
                    }

                    .header-subtitle span {
                        display: flex;
                        align-items: center;
                        gap: 8px;
                    }

                    /* Cards Grid */
                    .cards-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
                        gap: 20px;
                        margin-bottom: 30px;
                    }

                    .card {
                        background: var(--bg-card);
                        border-radius: 12px;
                        padding: 25px;
                        box-shadow: var(--shadow);
                        transition: transform 0.3s ease, box-shadow 0.3s ease;
                    }

                    .card:hover {
                        transform: translateY(-3px);
                        box-shadow: var(--shadow-hover);
                    }

                    .card-header {
                        display: flex;
                        justify-content: space-between;
                        align-items: flex-start;
                        margin-bottom: 15px;
                    }

                    .card-title {
                        font-size: 0.9rem;
                        color: var(--text-light);
                        text-transform: uppercase;
                        letter-spacing: 0.5px;
                        font-weight: 600;
                    }

                    .card-icon {
                        width: 40px;
                        height: 40px;
                        border-radius: 10px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        font-size: 1.2rem;
                    }

                    .card-icon.blue { background: rgba(23, 162, 184, 0.15); color: var(--secondary); }
                    .card-icon.green { background: rgba(40, 167, 69, 0.15); color: var(--accent); }
                    .card-icon.gold { background: rgba(255, 193, 7, 0.15); color: #d4a006; }
                    .card-icon.red { background: rgba(220, 53, 69, 0.15); color: var(--danger); }

                    .card-value {
                        font-size: 1.8rem;
                        font-weight: 700;
                        color: var(--text-dark);
                        margin-bottom: 5px;
                    }

                    .card-value small {
                        font-size: 0.9rem;
                        font-weight: 500;
                        color: var(--text-light);
                    }

                    .card-subtitle {
                        font-size: 0.85rem;
                        color: var(--text-light);
                    }

                    /* Section */
                    .section {
                        background: var(--bg-card);
                        border-radius: 12px;
                        padding: 30px;
                        margin-bottom: 25px;
                        box-shadow: var(--shadow);
                    }

                    .section-title {
                        font-size: 1.3rem;
                        color: var(--primary);
                        margin-bottom: 25px;
                        padding-bottom: 12px;
                        border-bottom: 2px solid var(--border-color);
                        display: flex;
                        align-items: center;
                        gap: 10px;
                    }

                    .section-icon {
                        font-size: 1.4rem;
                    }

                    /* Tables */
                    table {
                        width: 100%;
                        border-collapse: collapse;
                        font-size: 0.95rem;
                    }

                    th, td {
                        padding: 14px 16px;
                        text-align: left;
                        border-bottom: 1px solid var(--border-color);
                    }

                    th {
                        background: var(--bg-light);
                        font-weight: 600;
                        color: var(--text-dark);
                        white-space: nowrap;
                    }

                    tr:hover {
                        background: rgba(23, 162, 184, 0.04);
                    }

                    tr:last-child td {
                        border-bottom: none;
                    }

                    .text-right { text-align: right; }
                    .text-center { text-align: center; }
                    .font-mono { font-family: 'SF Mono', Monaco, 'Cascadia Code', monospace; }
                    .font-bold { font-weight: 600; }

                    /* Info Grid */
                    .info-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
                        gap: 20px;
                    }

                    .info-item {
                        display: flex;
                        flex-direction: column;
                        gap: 4px;
                    }

                    .info-label {
                        font-size: 0.85rem;
                        color: var(--text-light);
                        text-transform: uppercase;
                        letter-spacing: 0.3px;
                    }

                    .info-value {
                        font-size: 1.05rem;
                        font-weight: 500;
                        color: var(--text-dark);
                    }

                    /* Badges */
                    .badge {
                        display: inline-block;
                        padding: 4px 10px;
                        border-radius: 20px;
                        font-size: 0.75rem;
                        font-weight: 600;
                        text-transform: uppercase;
                    }

                    .badge-primary { background: var(--primary); color: white; }
                    .badge-success { background: var(--accent); color: white; }
                    .badge-warning { background: var(--warning); color: #212529; }
                    .badge-info { background: var(--secondary); color: white; }

                    /* Progress Bar */
                    .progress-container {
                        display: flex;
                        align-items: center;
                        gap: 10px;
                    }

                    .progress-bar {
                        flex: 1;
                        height: 8px;
                        background: var(--border-color);
                        border-radius: 4px;
                        overflow: hidden;
                    }

                    .progress-fill {
                        height: 100%;
                        border-radius: 4px;
                        transition: width 0.3s ease;
                    }

                    .progress-label {
                        font-size: 0.85rem;
                        font-weight: 600;
                        min-width: 50px;
                        text-align: right;
                    }

                    /* Charts */
                    .chart-container {
                        display: flex;
                        flex-wrap: wrap;
                        gap: 20px;
                    }

                    .chart-item {
                        flex: 1;
                        min-width: 200px;
                        display: flex;
                        align-items: center;
                        gap: 15px;
                        padding: 15px;
                        background: var(--bg-light);
                        border-radius: 10px;
                    }

                    .chart-bar {
                        width: 6px;
                        border-radius: 3px;
                    }

                    .chart-info h4 {
                        font-size: 0.9rem;
                        color: var(--text-light);
                        margin-bottom: 4px;
                    }

                    .chart-info .value {
                        font-size: 1.3rem;
                        font-weight: 700;
                        color: var(--text-dark);
                    }

                    /* Country Distribution */
                    .distribution-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
                        gap: 15px;
                    }

                    .distribution-item {
                        background: var(--bg-light);
                        padding: 15px;
                        border-radius: 10px;
                        text-align: center;
                    }

                    .distribution-item .country-code {
                        font-size: 1.5rem;
                        font-weight: 700;
                        color: var(--primary);
                        margin-bottom: 5px;
                    }

                    .distribution-item .count {
                        font-size: 0.85rem;
                        color: var(--text-light);
                    }

                    /* Risk Metrics */
                    .metrics-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                        gap: 15px;
                    }

                    .metric-card {
                        background: var(--bg-light);
                        padding: 20px;
                        border-radius: 10px;
                        text-align: center;
                        border-left: 4px solid var(--secondary);
                    }

                    .metric-card .metric-name {
                        font-size: 0.85rem;
                        color: var(--text-light);
                        margin-bottom: 8px;
                        text-transform: uppercase;
                    }

                    .metric-card .metric-value {
                        font-size: 1.6rem;
                        font-weight: 700;
                        color: var(--primary);
                    }

                    /* Share Class Grid */
                    .share-class-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
                        gap: 20px;
                    }

                    .share-class-card {
                        background: var(--bg-light);
                        border-radius: 12px;
                        padding: 20px;
                        border-left: 4px solid var(--secondary);
                    }

                    .share-class-header {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        margin-bottom: 15px;
                    }

                    .share-class-name {
                        font-size: 1.1rem;
                        font-weight: 600;
                        color: var(--text-dark);
                    }

                    .share-class-details {
                        display: grid;
                        grid-template-columns: 1fr 1fr;
                        gap: 10px;
                    }

                    .share-class-detail {
                        display: flex;
                        flex-direction: column;
                    }

                    .share-class-detail .label {
                        font-size: 0.75rem;
                        color: var(--text-light);
                        text-transform: uppercase;
                    }

                    .share-class-detail .value {
                        font-size: 1rem;
                        font-weight: 600;
                        color: var(--text-dark);
                    }

                    /* Position Rank */
                    .rank-badge {
                        width: 28px;
                        height: 28px;
                        border-radius: 50%;
                        display: inline-flex;
                        align-items: center;
                        justify-content: center;
                        font-size: 0.8rem;
                        font-weight: 700;
                        color: white;
                    }

                    .rank-1 { background: linear-gradient(135deg, #FFD700, #FFA500); }
                    .rank-2 { background: linear-gradient(135deg, #C0C0C0, #A0A0A0); }
                    .rank-3 { background: linear-gradient(135deg, #CD7F32, #A0522D); }
                    .rank-other { background: var(--text-light); }

                    /* Footer */
                    .footer {
                        text-align: center;
                        padding: 20px;
                        color: var(--text-light);
                        font-size: 0.85rem;
                    }

                    .footer a {
                        color: var(--secondary);
                        text-decoration: none;
                    }

                    /* Two Column Layout */
                    .two-columns {
                        display: grid;
                        grid-template-columns: 1fr 1fr;
                        gap: 25px;
                    }

                    @media (max-width: 768px) {
                        .two-columns {
                            grid-template-columns: 1fr;
                        }

                        .header {
                            padding: 25px;
                        }

                        .header h1 {
                            font-size: 1.6rem;
                        }

                        .cards-grid {
                            grid-template-columns: 1fr 1fr;
                        }
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <xsl:apply-templates select="Funds/Fund"/>

                    <!-- Footer -->
                    <div class="footer">
                        Generated by <strong>FreeXmlToolkit</strong> |
                        Document ID: <xsl:value-of select="ControlData/UniqueDocumentID"/> |
                        Data Supplier: <xsl:value-of select="ControlData/DataSupplier/Name"/>
                    </div>
                </div>
            </body>
        </html>
    </xsl:template>

    <!-- Fund Template -->
    <xsl:template match="Fund">
        <xsl:variable name="fund" select="."/>
        <xsl:variable name="positions" select="FundDynamicData/Portfolios/Portfolio/Positions/Position"/>
        <xsl:variable name="totalPositions" select="count($positions)"/>
        <xsl:variable name="bondPositions" select="$positions[Bond]"/>
        <xsl:variable name="totalBonds" select="count($bondPositions)"/>
        <xsl:variable name="shareClasses" select="SingleFund/ShareClasses/ShareClass"/>
        <xsl:variable name="totalShareClasses" select="count($shareClasses)"/>
        <xsl:variable name="fundTNA" select="number(FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount)"/>
        <xsl:variable name="fundCurrency" select="FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount/@ccy"/>
        <xsl:variable name="navDate" select="FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate"/>

        <!-- Header -->
        <div class="header">
            <h1><xsl:value-of select="Names/OfficialName"/></h1>
            <div class="header-subtitle">
                <span>📅 NAV Date: <xsl:value-of select="$navDate"/></span>
                <span>🏢 <xsl:value-of select="FundStaticData/ListedLegalStructure"/></span>
                <span>💱 <xsl:value-of select="Currency"/></span>
                <span>📋 LEI: <xsl:value-of select="Identifiers/LEI"/></span>
            </div>
        </div>

        <!-- Key Metrics Cards -->
        <div class="cards-grid">
            <div class="card">
                <div class="card-header">
                    <span class="card-title">Total Net Assets</span>
                    <div class="card-icon blue">💰</div>
                </div>
                <div class="card-value">
                    <xsl:value-of select="format-number($fundTNA, '#,##0.00')"/>
                    <small><xsl:value-of select="$fundCurrency"/></small>
                </div>
                <div class="card-subtitle">As of <xsl:value-of select="$navDate"/></div>
            </div>

            <div class="card">
                <div class="card-header">
                    <span class="card-title">Total Positions</span>
                    <div class="card-icon green">📊</div>
                </div>
                <div class="card-value"><xsl:value-of select="$totalPositions"/></div>
                <div class="card-subtitle">
                    <xsl:value-of select="$totalBonds"/> bonds (<xsl:value-of select="if ($totalPositions > 0) then format-number($totalBonds div $totalPositions * 100, '0.0') else '0'"/>%)
                </div>
            </div>

            <div class="card">
                <div class="card-header">
                    <span class="card-title">Share Classes</span>
                    <div class="card-icon gold">🏷️</div>
                </div>
                <div class="card-value"><xsl:value-of select="$totalShareClasses"/></div>
                <div class="card-subtitle">Active share classes</div>
            </div>

            <div class="card">
                <div class="card-header">
                    <span class="card-title">Inception Date</span>
                    <div class="card-icon red">📆</div>
                </div>
                <div class="card-value font-mono"><xsl:value-of select="FundStaticData/InceptionDate"/></div>
                <div class="card-subtitle">
                    <xsl:variable name="inceptionDate" select="xs:date(FundStaticData/InceptionDate)"/>
                    <xsl:variable name="navDateParsed" select="xs:date($navDate)"/>
                    <xsl:value-of select="days-from-duration($navDateParsed - $inceptionDate) idiv 365"/> years since inception
                </div>
            </div>
        </div>

        <!-- Fund Information -->
        <div class="section">
            <h2 class="section-title"><span class="section-icon">ℹ️</span> Fund Information</h2>
            <div class="info-grid">
                <div class="info-item">
                    <span class="info-label">Official Name</span>
                    <span class="info-value"><xsl:value-of select="Names/OfficialName"/></span>
                </div>
                <div class="info-item">
                    <span class="info-label">LEI</span>
                    <span class="info-value font-mono"><xsl:value-of select="Identifiers/LEI"/></span>
                </div>
                <div class="info-item">
                    <span class="info-label">Fund Currency</span>
                    <span class="info-value"><xsl:value-of select="Currency"/></span>
                </div>
                <div class="info-item">
                    <span class="info-label">Legal Structure</span>
                    <span class="info-value"><xsl:value-of select="FundStaticData/ListedLegalStructure"/></span>
                </div>
                <div class="info-item">
                    <span class="info-label">Inception Date</span>
                    <span class="info-value"><xsl:value-of select="FundStaticData/InceptionDate"/></span>
                </div>
                <div class="info-item">
                    <span class="info-label">Fiscal Year Start</span>
                    <span class="info-value">
                        <xsl:value-of select="FundStaticData/StartOfFiscalYear/Day"/>/<xsl:value-of select="FundStaticData/StartOfFiscalYear/Month"/>
                    </span>
                </div>
                <div class="info-item">
                    <span class="info-label">Single Fund</span>
                    <span class="info-value">
                        <xsl:choose>
                            <xsl:when test="SingleFundFlag = 'true'">
                                <span class="badge badge-success">Yes</span>
                            </xsl:when>
                            <xsl:otherwise>
                                <span class="badge badge-warning">No</span>
                            </xsl:otherwise>
                        </xsl:choose>
                    </span>
                </div>
                <div class="info-item">
                    <span class="info-label">Data Supplier</span>
                    <span class="info-value"><xsl:value-of select="DataSupplier/Name"/></span>
                </div>
            </div>
        </div>

        <!-- Risk Metrics -->
        <xsl:variable name="riskCodes" select="FundDynamicData/Portfolios/Portfolio/RiskCodes/RiskCode"/>
        <xsl:if test="$riskCodes">
            <div class="section">
                <h2 class="section-title"><span class="section-icon">📈</span> Risk Metrics &amp; Key Figures</h2>
                <div class="metrics-grid">
                    <xsl:for-each select="$riskCodes">
                        <div class="metric-card">
                            <div class="metric-name">
                                <xsl:value-of select="(ListedCode | UnlistedCode)[1]"/>
                            </div>
                            <div class="metric-value">
                                <xsl:value-of select="format-number(Value, '#,##0.0000')"/>
                            </div>
                        </div>
                    </xsl:for-each>
                </div>
            </div>
        </xsl:if>

        <!-- Share Classes -->
        <div class="section">
            <h2 class="section-title"><span class="section-icon">🏷️</span> Share Classes (<xsl:value-of select="$totalShareClasses"/>)</h2>

            <!-- Share Classes Table -->
            <table>
                <thead>
                    <tr>
                        <th>Name</th>
                        <th>ISIN</th>
                        <th>Currency</th>
                        <th>Type</th>
                        <th class="text-right">NAV Price</th>
                        <th class="text-right">Shares Outstanding</th>
                        <th class="text-right">TNA</th>
                        <th class="text-right">Ratio %</th>
                    </tr>
                </thead>
                <tbody>
                    <xsl:for-each select="$shareClasses">
                        <xsl:sort select="TotalAssetValues/TotalAssetValue/Ratio" data-type="number" order="descending"/>
                        <xsl:variable name="scTNA" select="number(TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount)"/>
                        <xsl:variable name="ratio" select="TotalAssetValues/TotalAssetValue/Ratio"/>
                        <tr>
                            <td class="font-bold"><xsl:value-of select="Names/OfficialName"/></td>
                            <td class="font-mono"><xsl:value-of select="Identifiers/ISIN"/></td>
                            <td><xsl:value-of select="Currency"/></td>
                            <td>
                                <span class="badge badge-info">
                                    <xsl:value-of select="ShareClassType/Code"/>
                                    <xsl:text> / </xsl:text>
                                    <xsl:value-of select="ShareClassType/EarningUse"/>
                                </span>
                            </td>
                            <td class="text-right font-mono">
                                <xsl:value-of select="format-number(Prices/Price/NavPrice, '#,##0.00')"/>
                            </td>
                            <td class="text-right font-mono">
                                <xsl:value-of select="format-number(TotalAssetValues/TotalAssetValue/SharesOutstanding, '#,##0.000')"/>
                            </td>
                            <td class="text-right font-mono">
                                <xsl:value-of select="format-number($scTNA, '#,##0.00')"/>
                            </td>
                            <td class="text-right">
                                <div class="progress-container">
                                    <div class="progress-bar">
                                        <div class="progress-fill" style="width: {$ratio}%; background: linear-gradient(90deg, var(--secondary), var(--primary));"></div>
                                    </div>
                                    <span class="progress-label"><xsl:value-of select="format-number($ratio, '0.00')"/>%</span>
                                </div>
                            </td>
                        </tr>
                    </xsl:for-each>
                </tbody>
            </table>
        </div>

        <!-- Asset Allocation by Country -->
        <div class="section">
            <h2 class="section-title"><span class="section-icon">🌍</span> Geographic Allocation (by ISIN Country)</h2>
            <xsl:variable name="allAssets" select="/FundsXML4/AssetMasterData/Asset"/>
            <div class="distribution-grid">
                <xsl:for-each-group select="$positions[key('asset-by-id', UniqueID)/Identifiers/ISIN]" group-by="substring(key('asset-by-id', UniqueID)/Identifiers/ISIN, 1, 2)">
                    <xsl:sort select="sum(current-group()/TotalPercentage)" data-type="number" order="descending"/>
                    <xsl:variable name="countryPct" select="sum(current-group()/TotalPercentage)"/>
                    <div class="distribution-item">
                        <div class="country-code"><xsl:value-of select="current-grouping-key()"/></div>
                        <div class="count">
                            <xsl:value-of select="count(current-group())"/> positions
                        </div>
                        <div style="margin-top: 8px;">
                            <div class="progress-bar" style="height: 6px;">
                                <div class="progress-fill" style="width: {$countryPct}%; background: var(--secondary);"></div>
                            </div>
                            <div style="font-size: 0.9rem; font-weight: 600; margin-top: 4px;">
                                <xsl:value-of select="format-number($countryPct, '0.00')"/>%
                            </div>
                        </div>
                    </div>
                </xsl:for-each-group>
            </div>
        </div>

        <!-- Currency Breakdown -->
        <div class="section">
            <h2 class="section-title"><span class="section-icon">💱</span> Currency Exposure</h2>
            <div class="chart-container">
                <xsl:for-each-group select="$positions" group-by="Currency">
                    <xsl:sort select="sum(current-group()/TotalPercentage)" data-type="number" order="descending"/>
                    <xsl:variable name="currencyPct" select="sum(current-group()/TotalPercentage)"/>
                    <xsl:variable name="currencyValue" select="sum(current-group()/TotalValue/Amount)"/>
                    <div class="chart-item">
                        <div class="chart-bar" style="height: 60px; background: linear-gradient(to top, var(--secondary), var(--primary));"></div>
                        <div class="chart-info">
                            <h4><xsl:value-of select="current-grouping-key()"/></h4>
                            <div class="value"><xsl:value-of select="format-number($currencyPct, '0.00')"/>%</div>
                            <div style="font-size: 0.8rem; color: var(--text-light);">
                                <xsl:value-of select="format-number($currencyValue, '#,##0')"/> | <xsl:value-of select="count(current-group())"/> pos.
                            </div>
                        </div>
                    </div>
                </xsl:for-each-group>
            </div>
        </div>

        <!-- Bond Analysis (if applicable) -->
        <xsl:if test="$totalBonds > 0">
            <div class="section">
                <h2 class="section-title"><span class="section-icon">📜</span> Bond Portfolio Analysis</h2>

                <xsl:variable name="totalBondValue" select="sum($bondPositions/TotalValue/Amount)"/>
                <xsl:variable name="totalNominal" select="sum($bondPositions/Bond/Nominal)"/>
                <xsl:variable name="avgPrice" select="if ($totalBonds > 0) then sum($bondPositions/Bond/Price/Amount) div $totalBonds else 0"/>
                <xsl:variable name="totalInterest" select="sum($bondPositions/Bond/InterestClaimGross/Amount)"/>

                <div class="cards-grid" style="margin-bottom: 25px;">
                    <div class="card">
                        <div class="card-title">Total Bond Value</div>
                        <div class="card-value" style="font-size: 1.4rem;">
                            <xsl:value-of select="format-number($totalBondValue, '#,##0.00')"/>
                            <small><xsl:value-of select="$fundCurrency"/></small>
                        </div>
                    </div>
                    <div class="card">
                        <div class="card-title">Total Nominal</div>
                        <div class="card-value" style="font-size: 1.4rem;">
                            <xsl:value-of select="format-number($totalNominal, '#,##0')"/>
                        </div>
                    </div>
                    <div class="card">
                        <div class="card-title">Average Price</div>
                        <div class="card-value" style="font-size: 1.4rem;">
                            <xsl:value-of select="format-number($avgPrice, '0.00')"/>%
                        </div>
                    </div>
                    <div class="card">
                        <div class="card-title">Accrued Interest</div>
                        <div class="card-value" style="font-size: 1.4rem;">
                            <xsl:value-of select="format-number($totalInterest, '#,##0.00')"/>
                            <small><xsl:value-of select="$fundCurrency"/></small>
                        </div>
                    </div>
                </div>

                <!-- Bond Price Distribution -->
                <h3 style="font-size: 1.1rem; margin-bottom: 15px; color: var(--text-dark);">Price Distribution</h3>
                <div class="chart-container" style="margin-bottom: 25px;">
                    <xsl:variable name="below95" select="count($bondPositions[Bond/Price/Amount &lt; 95])"/>
                    <xsl:variable name="between95_100" select="count($bondPositions[Bond/Price/Amount >= 95 and Bond/Price/Amount &lt; 100])"/>
                    <xsl:variable name="between100_105" select="count($bondPositions[Bond/Price/Amount >= 100 and Bond/Price/Amount &lt; 105])"/>
                    <xsl:variable name="above105" select="count($bondPositions[Bond/Price/Amount >= 105])"/>

                    <div class="chart-item">
                        <div class="chart-bar" style="height: 50px; background: var(--danger);"></div>
                        <div class="chart-info">
                            <h4>&lt; 95%</h4>
                            <div class="value"><xsl:value-of select="$below95"/> bonds</div>
                        </div>
                    </div>
                    <div class="chart-item">
                        <div class="chart-bar" style="height: 50px; background: var(--warning);"></div>
                        <div class="chart-info">
                            <h4>95-100%</h4>
                            <div class="value"><xsl:value-of select="$between95_100"/> bonds</div>
                        </div>
                    </div>
                    <div class="chart-item">
                        <div class="chart-bar" style="height: 50px; background: var(--accent);"></div>
                        <div class="chart-info">
                            <h4>100-105%</h4>
                            <div class="value"><xsl:value-of select="$between100_105"/> bonds</div>
                        </div>
                    </div>
                    <div class="chart-item">
                        <div class="chart-bar" style="height: 50px; background: var(--secondary);"></div>
                        <div class="chart-info">
                            <h4>&gt; 105%</h4>
                            <div class="value"><xsl:value-of select="$above105"/> bonds</div>
                        </div>
                    </div>
                </div>
            </div>
        </xsl:if>

        <!-- Top 20 Holdings -->
        <div class="section">
            <h2 class="section-title"><span class="section-icon">🏆</span> Top 20 Holdings</h2>
            <table>
                <thead>
                    <tr>
                        <th style="width: 40px;">#</th>
                        <th>Name</th>
                        <th>ISIN</th>
                        <th>Type</th>
                        <th>Country</th>
                        <th class="text-right">Value</th>
                        <th class="text-right">Weight</th>
                    </tr>
                </thead>
                <tbody>
                    <xsl:for-each select="$positions">
                        <xsl:sort select="TotalPercentage" data-type="number" order="descending"/>
                        <xsl:if test="position() &lt;= 20">
                            <xsl:variable name="pos" select="position()"/>
                            <xsl:variable name="asset" select="key('asset-by-id', UniqueID)"/>
                            <xsl:variable name="pctValue" select="TotalPercentage"/>
                            <tr>
                                <td>
                                    <span class="rank-badge rank-{if ($pos &lt;= 3) then $pos else 'other'}">
                                        <xsl:value-of select="$pos"/>
                                    </span>
                                </td>
                                <td class="font-bold">
                                    <xsl:value-of select="$asset/Name"/>
                                </td>
                                <td class="font-mono">
                                    <xsl:value-of select="$asset/Identifiers/ISIN"/>
                                </td>
                                <td>
                                    <span class="badge badge-primary">
                                        <xsl:value-of select="$asset/AssetType"/>
                                    </span>
                                </td>
                                <td><xsl:value-of select="$asset/Country"/></td>
                                <td class="text-right font-mono">
                                    <xsl:value-of select="format-number(TotalValue/Amount, '#,##0.00')"/>
                                    <xsl:text> </xsl:text>
                                    <xsl:value-of select="TotalValue/Amount/@ccy"/>
                                </td>
                                <td class="text-right">
                                    <div class="progress-container">
                                        <div class="progress-bar">
                                            <div class="progress-fill" style="width: {$pctValue * 10}%; background: linear-gradient(90deg, var(--accent), var(--secondary));"></div>
                                        </div>
                                        <span class="progress-label"><xsl:value-of select="format-number($pctValue, '0.00')"/>%</span>
                                    </div>
                                </td>
                            </tr>
                        </xsl:if>
                    </xsl:for-each>
                </tbody>
            </table>

            <!-- Top 20 Sum -->
            <xsl:variable name="top20Sum" select="sum((for $p in $positions return $p)[position() &lt;= 20]/TotalPercentage)"/>
            <div style="margin-top: 15px; padding: 15px; background: var(--bg-light); border-radius: 8px; display: flex; justify-content: space-between; align-items: center;">
                <span style="font-weight: 600;">Top 20 Concentration</span>
                <span style="font-size: 1.2rem; font-weight: 700; color: var(--primary);">
                    <xsl:value-of select="format-number($top20Sum, '0.00')"/>%
                </span>
            </div>
        </div>

        <!-- Top Issuers -->
        <xsl:variable name="assetsWithIssuer" select="/FundsXML4/AssetMasterData/Asset[AssetDetails/Bond/Issuer/Name]"/>
        <xsl:if test="count($assetsWithIssuer) > 0">
            <div class="section">
                <h2 class="section-title"><span class="section-icon">🏛️</span> Top Issuers</h2>
                <table>
                    <thead>
                        <tr>
                            <th>#</th>
                            <th>Issuer Name</th>
                            <th>LEI</th>
                            <th>Country</th>
                            <th class="text-right">Positions</th>
                            <th class="text-right">Total Value</th>
                        </tr>
                    </thead>
                    <tbody>
                        <xsl:for-each-group select="$positions[key('asset-by-id', UniqueID)/AssetDetails/Bond/Issuer/Name]" group-by="key('asset-by-id', UniqueID)/AssetDetails/Bond/Issuer/Name">
                            <xsl:sort select="sum(current-group()/TotalValue/Amount)" data-type="number" order="descending"/>
                            <xsl:if test="position() &lt;= 10">
                                <xsl:variable name="issuer" select="key('asset-by-id', UniqueID)/AssetDetails/Bond/Issuer"/>
                                <xsl:variable name="issuerValue" select="sum(current-group()/TotalValue/Amount)"/>
                                <tr>
                                    <td><xsl:value-of select="position()"/></td>
                                    <td class="font-bold"><xsl:value-of select="current-grouping-key()"/></td>
                                    <td class="font-mono"><xsl:value-of select="$issuer/Identifiers/LEI"/></td>
                                    <td><xsl:value-of select="$issuer/Address/Country"/></td>
                                    <td class="text-right"><xsl:value-of select="count(current-group())"/></td>
                                    <td class="text-right font-mono">
                                        <xsl:value-of select="format-number($issuerValue, '#,##0.00')"/>
                                    </td>
                                </tr>
                            </xsl:if>
                        </xsl:for-each-group>
                    </tbody>
                </table>
            </div>
        </xsl:if>

        <!-- Data Supplier Information -->
        <div class="section">
            <h2 class="section-title"><span class="section-icon">📧</span> Data Supplier &amp; Contact</h2>
            <div class="info-grid">
                <div class="info-item">
                    <span class="info-label">Supplier Name</span>
                    <span class="info-value"><xsl:value-of select="DataSupplier/Name"/></span>
                </div>
                <div class="info-item">
                    <span class="info-label">Short Name</span>
                    <span class="info-value"><xsl:value-of select="DataSupplier/Short"/></span>
                </div>
                <div class="info-item">
                    <span class="info-label">Type</span>
                    <span class="info-value"><xsl:value-of select="DataSupplier/Type"/></span>
                </div>
                <div class="info-item">
                    <span class="info-label">Country</span>
                    <span class="info-value"><xsl:value-of select="DataSupplier/SystemCountry"/></span>
                </div>
                <div class="info-item">
                    <span class="info-label">Email</span>
                    <span class="info-value">
                        <a href="mailto:{DataSupplier/Contact/Email}" style="color: var(--secondary);">
                            <xsl:value-of select="DataSupplier/Contact/Email"/>
                        </a>
                    </span>
                </div>
            </div>
        </div>

    </xsl:template>

</xsl:stylesheet>
