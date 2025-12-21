<?xml version="1.0" encoding="UTF-8"?>
<!--
    FundsXML4 Issuer Concentration Dashboard
    ========================================
    This XSLT generates an HTML report analyzing issuer concentration risk
    with focus on top issuers by count/value, country distribution, and
    concentration warnings.

    Layout: Treemap-inspired grid with concentration bars
    Theme: Orange (#c05621) with Gold accents (#ecc94b)

    Checks NOT in XSD (business logic only):
    - Top 10 issuers by position count
    - Top 10 issuers by total value
    - Issuer LEI coverage
    - Country concentration
    - Single-issuer concentration > 5% warning
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
                <title>FundsXML - Issuer Concentration Dashboard</title>
                <style>
                    /* Reset and Base Styles */
                    * { margin: 0; padding: 0; box-sizing: border-box; }

                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
                        background: linear-gradient(135deg, #c05621 0%, #dd6b20 50%, #ed8936 100%);
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
                        background: linear-gradient(135deg, #c05621 0%, #dd6b20 50%, #ecc94b 100%);
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

                    .header p { color: #fef3c7; font-size: 1.1rem; }
                    .header .subtitle { margin-top: 0.5rem; font-size: 0.9rem; color: #fde68a; }

                    /* Content */
                    .content { padding: 2rem; }

                    /* Summary Tiles - Large Numbers */
                    .tile-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                        gap: 1.5rem;
                        margin-bottom: 2rem;
                    }

                    .tile {
                        background: linear-gradient(135deg, #fffbeb 0%, #fef3c7 100%);
                        border: 2px solid #f59e0b;
                        border-radius: 1rem;
                        padding: 1.5rem;
                        text-align: center;
                        transition: transform 0.2s, box-shadow 0.2s;
                    }

                    .tile:hover {
                        transform: translateY(-4px);
                        box-shadow: 0 15px 30px -5px rgba(245, 158, 11, 0.3);
                    }

                    .tile .big-number {
                        font-size: 3rem;
                        font-weight: 800;
                        color: #c05621;
                        line-height: 1;
                    }

                    .tile .label {
                        font-size: 0.9rem;
                        font-weight: 600;
                        color: #92400e;
                        margin-top: 0.5rem;
                    }

                    .tile.warning {
                        background: linear-gradient(135deg, #fee2e2 0%, #fecaca 100%);
                        border-color: #ef4444;
                    }

                    .tile.warning .big-number { color: #dc2626; }
                    .tile.warning .label { color: #991b1b; }

                    /* Section */
                    .section {
                        background: #f9fafb;
                        border: 1px solid #e5e7eb;
                        border-radius: 0.75rem;
                        margin-bottom: 1.5rem;
                        overflow: hidden;
                    }

                    .section-header {
                        background: linear-gradient(135deg, #c05621 0%, #dd6b20 100%);
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

                    /* Top Issuers Grid */
                    .issuer-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
                        gap: 1rem;
                    }

                    .issuer-card {
                        background: white;
                        border: 1px solid #e5e7eb;
                        border-radius: 0.75rem;
                        padding: 1rem;
                        display: flex;
                        align-items: center;
                        gap: 1rem;
                        transition: box-shadow 0.2s;
                    }

                    .issuer-card:hover {
                        box-shadow: 0 4px 12px rgba(0,0,0,0.1);
                    }

                    .issuer-rank {
                        width: 40px;
                        height: 40px;
                        background: linear-gradient(135deg, #c05621 0%, #ed8936 100%);
                        color: white;
                        border-radius: 50%;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        font-weight: 700;
                        font-size: 1.1rem;
                    }

                    .issuer-info { flex: 1; }
                    .issuer-name { font-weight: 600; color: #1f2937; font-size: 0.95rem; }
                    .issuer-detail { font-size: 0.8rem; color: #6b7280; }
                    .issuer-value { text-align: right; }
                    .issuer-value .count { font-size: 1.25rem; font-weight: 700; color: #c05621; }
                    .issuer-value .label { font-size: 0.75rem; color: #6b7280; }

                    /* Concentration Bar */
                    .concentration-row {
                        display: flex;
                        align-items: center;
                        gap: 1rem;
                        padding: 0.75rem 0;
                        border-bottom: 1px solid #f3f4f6;
                    }

                    .concentration-row:last-child { border-bottom: none; }

                    .concentration-label {
                        width: 200px;
                        font-size: 0.9rem;
                        font-weight: 500;
                        color: #374151;
                        overflow: hidden;
                        text-overflow: ellipsis;
                        white-space: nowrap;
                    }

                    .concentration-bar-bg {
                        flex: 1;
                        background: #e5e7eb;
                        border-radius: 0.25rem;
                        height: 24px;
                        overflow: hidden;
                    }

                    .concentration-bar {
                        height: 100%;
                        background: linear-gradient(90deg, #f59e0b 0%, #ecc94b 100%);
                        border-radius: 0.25rem;
                        display: flex;
                        align-items: center;
                        padding-left: 0.5rem;
                        color: #92400e;
                        font-weight: 600;
                        font-size: 0.8rem;
                        min-width: 40px;
                    }

                    .concentration-bar.high {
                        background: linear-gradient(90deg, #dc2626 0%, #f87171 100%);
                        color: white;
                    }

                    .concentration-percent {
                        width: 60px;
                        text-align: right;
                        font-weight: 600;
                        font-size: 0.9rem;
                        color: #374151;
                    }

                    /* Country Grid */
                    .country-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(100px, 1fr));
                        gap: 0.75rem;
                    }

                    .country-tile {
                        background: linear-gradient(135deg, #fff7ed 0%, #ffedd5 100%);
                        border: 1px solid #fed7aa;
                        border-radius: 0.5rem;
                        padding: 0.75rem;
                        text-align: center;
                    }

                    .country-code {
                        font-size: 1.5rem;
                        font-weight: 800;
                        color: #c05621;
                    }

                    .country-count {
                        font-size: 0.8rem;
                        color: #92400e;
                    }

                    /* Data Table */
                    .data-table {
                        width: 100%;
                        border-collapse: collapse;
                        font-size: 0.875rem;
                    }

                    .data-table th {
                        background: #c05621;
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
                    .data-table tr:hover { background: #fef3c7; }

                    /* Status Badges */
                    .badge-high {
                        background: #fee2e2;
                        color: #dc2626;
                        padding: 0.25rem 0.5rem;
                        border-radius: 0.25rem;
                        font-size: 0.75rem;
                        font-weight: 600;
                    }

                    .badge-medium {
                        background: #fef3c7;
                        color: #d97706;
                        padding: 0.25rem 0.5rem;
                        border-radius: 0.25rem;
                        font-size: 0.75rem;
                        font-weight: 600;
                    }

                    .badge-low {
                        background: #d1fae5;
                        color: #059669;
                        padding: 0.25rem 0.5rem;
                        border-radius: 0.25rem;
                        font-size: 0.75rem;
                        font-weight: 600;
                    }

                    /* Alert Box */
                    .alert {
                        padding: 1rem 1.25rem;
                        border-radius: 0.5rem;
                        margin-bottom: 1.5rem;
                        display: flex;
                        align-items: flex-start;
                        gap: 0.75rem;
                    }

                    .alert.warning {
                        background: #fef3c7;
                        border: 1px solid #fde68a;
                        color: #92400e;
                    }

                    .alert.error {
                        background: #fee2e2;
                        border: 1px solid #fecaca;
                        color: #991b1b;
                    }

                    .alert .icon { font-size: 1.25rem; }

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
                            <h1>Issuer Concentration Dashboard</h1>
                            <p>Portfolio Concentration Risk Analysis</p>
                            <div class="subtitle">
                                Content Date: <xsl:value-of select="$contentDate"/> |
                                Generated: <xsl:value-of select="format-dateTime(current-dateTime(), '[Y0001]-[M01]-[D01] [H01]:[m01]:[s01]')"/>
                            </div>
                        </div>

                        <div class="content">
                            <!-- Gather all bond issuers -->
                            <xsl:variable name="allBonds" select="AssetMasterData/Asset[AssetType='BO']"/>
                            <xsl:variable name="totalBonds" select="count($allBonds)"/>
                            <xsl:variable name="bondsWithIssuer" select="$allBonds[AssetDetails/Bond/Issuer/Name]"/>
                            <xsl:variable name="bondsWithIssuerLEI" select="$allBonds[AssetDetails/Bond/Issuer/Identifiers/LEI]"/>
                            <xsl:variable name="bondsNoIssuer" select="$allBonds[not(AssetDetails/Bond/Issuer/Name)]"/>

                            <!-- Count unique issuers -->
                            <xsl:variable name="uniqueIssuers" select="distinct-values($bondsWithIssuer/AssetDetails/Bond/Issuer/Name)"/>
                            <xsl:variable name="uniqueCountries" select="distinct-values($allBonds/Country)"/>

                            <!-- Calculate high concentration (any issuer > 5% of portfolio) -->
                            <xsl:variable name="highConcentrationThreshold" select="ceiling($totalBonds * 0.05)"/>

                            <!-- Summary Tiles -->
                            <div class="tile-grid">
                                <div class="tile">
                                    <div class="big-number"><xsl:value-of select="$totalBonds"/></div>
                                    <div class="label">Total Bonds</div>
                                </div>
                                <div class="tile">
                                    <div class="big-number"><xsl:value-of select="count($uniqueIssuers)"/></div>
                                    <div class="label">Unique Issuers</div>
                                </div>
                                <div class="tile">
                                    <div class="big-number"><xsl:value-of select="count($uniqueCountries)"/></div>
                                    <div class="label">Countries</div>
                                </div>
                                <div class="tile">
                                    <div class="big-number">
                                        <xsl:value-of select="if ($totalBonds > 0) then format-number(count($bondsWithIssuerLEI) div $totalBonds * 100, '0') else '0'"/>%
                                    </div>
                                    <div class="label">LEI Coverage</div>
                                </div>
                                <xsl:if test="count($bondsNoIssuer) > 0">
                                    <div class="tile warning">
                                        <div class="big-number"><xsl:value-of select="count($bondsNoIssuer)"/></div>
                                        <div class="label">Missing Issuer</div>
                                    </div>
                                </xsl:if>
                            </div>

                            <!-- Top 10 Issuers by Bond Count -->
                            <div class="section">
                                <div class="section-header">
                                    <span class="icon">&#127942;</span>
                                    <h2>Top 10 Issuers by Bond Count</h2>
                                    <span class="badge">Concentration Analysis</span>
                                </div>
                                <div class="section-body">
                                    <xsl:for-each-group select="$bondsWithIssuer" group-by="AssetDetails/Bond/Issuer/Name">
                                        <xsl:sort select="count(current-group())" order="descending"/>
                                        <xsl:if test="position() &lt;= 10">
                                            <xsl:variable name="count" select="count(current-group())"/>
                                            <xsl:variable name="pct" select="if ($totalBonds > 0) then $count div $totalBonds * 100 else 0"/>
                                            <div class="concentration-row">
                                                <div class="concentration-label">
                                                    <xsl:value-of select="position()"/>. <xsl:value-of select="current-grouping-key()"/>
                                                </div>
                                                <div class="concentration-bar-bg">
                                                    <div class="concentration-bar" style="width: {$pct}%">
                                                        <xsl:if test="$pct > 5">
                                                            <xsl:attribute name="class">concentration-bar high</xsl:attribute>
                                                        </xsl:if>
                                                        <xsl:value-of select="$count"/>
                                                    </div>
                                                </div>
                                                <div class="concentration-percent">
                                                    <xsl:value-of select="format-number($pct, '0.0')"/>%
                                                    <xsl:if test="$pct > 5">
                                                        <xsl:text> </xsl:text><span class="badge-high">HIGH</span>
                                                    </xsl:if>
                                                </div>
                                            </div>
                                        </xsl:if>
                                    </xsl:for-each-group>
                                </div>
                            </div>

                            <!-- Country Distribution -->
                            <div class="section">
                                <div class="section-header">
                                    <span class="icon">&#127758;</span>
                                    <h2>Country Distribution</h2>
                                    <span class="badge"><xsl:value-of select="count($uniqueCountries)"/> countries</span>
                                </div>
                                <div class="section-body">
                                    <div class="country-grid">
                                        <xsl:for-each-group select="$allBonds" group-by="Country">
                                            <xsl:sort select="count(current-group())" order="descending"/>
                                            <div class="country-tile">
                                                <div class="country-code">
                                                    <xsl:value-of select="current-grouping-key()"/>
                                                </div>
                                                <div class="country-count">
                                                    <xsl:value-of select="count(current-group())"/> bonds
                                                    (<xsl:value-of select="if ($totalBonds > 0) then format-number(count(current-group()) div $totalBonds * 100, '0.0') else '0.0'"/>%)
                                                </div>
                                            </div>
                                        </xsl:for-each-group>
                                    </div>
                                </div>
                            </div>

                            <!-- Issuer LEI Coverage -->
                            <div class="section">
                                <div class="section-header">
                                    <span class="icon">&#128477;</span>
                                    <h2>Issuer Identifier Quality</h2>
                                </div>
                                <div class="section-body">
                                    <table class="data-table">
                                        <thead>
                                            <tr>
                                                <th>Check</th>
                                                <th>Count</th>
                                                <th>Percentage</th>
                                                <th>Status</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            <tr>
                                                <td>Bonds with Issuer Name</td>
                                                <td><xsl:value-of select="count($bondsWithIssuer)"/></td>
                                                <td><xsl:value-of select="if ($totalBonds > 0) then format-number(count($bondsWithIssuer) div $totalBonds * 100, '0.0') else '0.0'"/>%</td>
                                                <td>
                                                    <xsl:choose>
                                                        <xsl:when test="count($bondsWithIssuer) = $totalBonds"><span class="badge-low">COMPLETE</span></xsl:when>
                                                        <xsl:when test="count($bondsWithIssuer) >= $totalBonds * 0.9"><span class="badge-medium">GOOD</span></xsl:when>
                                                        <xsl:otherwise><span class="badge-high">INCOMPLETE</span></xsl:otherwise>
                                                    </xsl:choose>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td>Bonds with Issuer LEI</td>
                                                <td><xsl:value-of select="count($bondsWithIssuerLEI)"/></td>
                                                <td><xsl:value-of select="if ($totalBonds > 0) then format-number(count($bondsWithIssuerLEI) div $totalBonds * 100, '0.0') else '0.0'"/>%</td>
                                                <td>
                                                    <xsl:choose>
                                                        <xsl:when test="count($bondsWithIssuerLEI) = $totalBonds"><span class="badge-low">COMPLETE</span></xsl:when>
                                                        <xsl:when test="count($bondsWithIssuerLEI) >= $totalBonds * 0.9"><span class="badge-medium">GOOD</span></xsl:when>
                                                        <xsl:otherwise><span class="badge-high">INCOMPLETE</span></xsl:otherwise>
                                                    </xsl:choose>
                                                </td>
                                            </tr>
                                            <xsl:variable name="bondsWithAddress" select="$allBonds[AssetDetails/Bond/Issuer/Address]"/>
                                            <tr>
                                                <td>Bonds with Issuer Address</td>
                                                <td><xsl:value-of select="count($bondsWithAddress)"/></td>
                                                <td><xsl:value-of select="if ($totalBonds > 0) then format-number(count($bondsWithAddress) div $totalBonds * 100, '0.0') else '0.0'"/>%</td>
                                                <td>
                                                    <xsl:choose>
                                                        <xsl:when test="count($bondsWithAddress) = $totalBonds"><span class="badge-low">COMPLETE</span></xsl:when>
                                                        <xsl:when test="count($bondsWithAddress) >= $totalBonds * 0.9"><span class="badge-medium">GOOD</span></xsl:when>
                                                        <xsl:otherwise><span class="badge-high">INCOMPLETE</span></xsl:otherwise>
                                                    </xsl:choose>
                                                </td>
                                            </tr>
                                        </tbody>
                                    </table>
                                </div>
                            </div>

                            <!-- Bonds Without Issuer Information -->
                            <xsl:if test="count($bondsNoIssuer) > 0">
                                <div class="section">
                                    <div class="section-header" style="background: linear-gradient(135deg, #dc2626 0%, #f87171 100%);">
                                        <span class="icon">&#9888;</span>
                                        <h2>Bonds Missing Issuer Information</h2>
                                        <span class="badge"><xsl:value-of select="count($bondsNoIssuer)"/> bonds</span>
                                    </div>
                                    <div class="section-body">
                                        <table class="data-table">
                                            <thead>
                                                <tr>
                                                    <th>ISIN</th>
                                                    <th>Name</th>
                                                    <th>Country</th>
                                                    <th>Currency</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                <xsl:for-each select="$bondsNoIssuer">
                                                    <xsl:sort select="Name"/>
                                                    <tr>
                                                        <td class="mono"><xsl:value-of select="Identifiers/ISIN"/></td>
                                                        <td><xsl:value-of select="Name"/></td>
                                                        <td><xsl:value-of select="Country"/></td>
                                                        <td><xsl:value-of select="Currency"/></td>
                                                    </tr>
                                                </xsl:for-each>
                                            </tbody>
                                        </table>
                                    </div>
                                </div>
                            </xsl:if>

                            <!-- Diversification Score -->
                            <div class="section">
                                <div class="section-header">
                                    <span class="icon">&#128200;</span>
                                    <h2>Diversification Metrics</h2>
                                </div>
                                <div class="section-body">
                                    <xsl:variable name="avgBondsPerIssuer" select="$totalBonds div count($uniqueIssuers)"/>
                                    <div class="issuer-grid">
                                        <div class="issuer-card">
                                            <div class="issuer-rank" style="background: linear-gradient(135deg, #059669 0%, #34d399 100%);">&#9989;</div>
                                            <div class="issuer-info">
                                                <div class="issuer-name">Avg. Bonds per Issuer</div>
                                                <div class="issuer-detail">Diversification indicator</div>
                                            </div>
                                            <div class="issuer-value">
                                                <div class="count"><xsl:value-of select="format-number($avgBondsPerIssuer, '0.0')"/></div>
                                            </div>
                                        </div>
                                        <div class="issuer-card">
                                            <div class="issuer-rank" style="background: linear-gradient(135deg, #7c3aed 0%, #a78bfa 100%);">&#128202;</div>
                                            <div class="issuer-info">
                                                <div class="issuer-name">Issuer-to-Bond Ratio</div>
                                                <div class="issuer-detail">Higher = more diversified</div>
                                            </div>
                                            <div class="issuer-value">
                                                <div class="count"><xsl:value-of select="if ($totalBonds > 0) then format-number(count($uniqueIssuers) div $totalBonds * 100, '0.0') else '0.0'"/>%</div>
                                            </div>
                                        </div>
                                        <div class="issuer-card">
                                            <div class="issuer-rank" style="background: linear-gradient(135deg, #0891b2 0%, #22d3ee 100%);">&#127759;</div>
                                            <div class="issuer-info">
                                                <div class="issuer-name">Country Diversification</div>
                                                <div class="issuer-detail">Number of countries</div>
                                            </div>
                                            <div class="issuer-value">
                                                <div class="count"><xsl:value-of select="count($uniqueCountries)"/></div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>
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
