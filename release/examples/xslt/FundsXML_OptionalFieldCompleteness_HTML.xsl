<?xml version="1.0" encoding="UTF-8"?>
<!--
    FundsXML4 Optional Field Completeness Report
    =============================================
    This XSLT generates an HTML report analyzing data richness
    beyond minimum XSD requirements, showing coverage percentages
    for optional fields.

    Layout: Heatmap/Scorecard grid
    Theme: Purple (#553c9a) with Lavender accents (#9f7aea)

    Checks NOT in XSD (data richness analysis):
    - Bloomberg identifier coverage
    - SEDOL/WKN coverage
    - Issuer LEI coverage
    - Address completeness
    - Bond detail completeness
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
                <title>FundsXML - Optional Field Completeness</title>
                <style>
                    /* Reset and Base Styles */
                    * { margin: 0; padding: 0; box-sizing: border-box; }

                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
                        background: linear-gradient(135deg, #553c9a 0%, #805ad5 50%, #9f7aea 100%);
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
                        background: linear-gradient(135deg, #553c9a 0%, #805ad5 50%, #9f7aea 100%);
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

                    .header p { color: #e9d5ff; font-size: 1.1rem; }
                    .header .subtitle { margin-top: 0.5rem; font-size: 0.9rem; color: #c4b5fd; }

                    /* Content */
                    .content { padding: 2rem; }

                    /* Overall Score */
                    .overall-score {
                        background: linear-gradient(135deg, #f5f3ff 0%, #ede9fe 100%);
                        border: 2px solid #a78bfa;
                        border-radius: 1rem;
                        padding: 2rem;
                        text-align: center;
                        margin-bottom: 2rem;
                    }

                    .score-value {
                        font-size: 4rem;
                        font-weight: 800;
                        line-height: 1;
                    }

                    .score-value.excellent { color: #059669; }
                    .score-value.good { color: #0891b2; }
                    .score-value.fair { color: #d97706; }
                    .score-value.poor { color: #dc2626; }

                    .score-label {
                        font-size: 1.1rem;
                        color: #6b7280;
                        margin-top: 0.5rem;
                    }

                    .score-breakdown {
                        display: flex;
                        justify-content: center;
                        gap: 2rem;
                        margin-top: 1.5rem;
                        flex-wrap: wrap;
                    }

                    .score-item {
                        text-align: center;
                    }

                    .score-item .value {
                        font-size: 1.5rem;
                        font-weight: 700;
                        color: #553c9a;
                    }

                    .score-item .label {
                        font-size: 0.8rem;
                        color: #6b7280;
                    }

                    /* Section */
                    .section {
                        background: #f9fafb;
                        border: 1px solid #e5e7eb;
                        border-radius: 0.75rem;
                        margin-bottom: 1.5rem;
                        overflow: hidden;
                    }

                    .section-header {
                        background: linear-gradient(135deg, #553c9a 0%, #805ad5 100%);
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

                    /* Heatmap Grid */
                    .heatmap-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
                        gap: 0.75rem;
                    }

                    .heatmap-cell {
                        background: white;
                        border: 1px solid #e5e7eb;
                        border-radius: 0.5rem;
                        padding: 1rem;
                        text-align: center;
                        transition: transform 0.2s, box-shadow 0.2s;
                    }

                    .heatmap-cell:hover {
                        transform: translateY(-2px);
                        box-shadow: 0 4px 12px rgba(0,0,0,0.1);
                    }

                    /* Heatmap colors based on coverage percentage */
                    .heatmap-cell.coverage-100 { background: linear-gradient(135deg, #d1fae5 0%, #a7f3d0 100%); border-color: #34d399; }
                    .heatmap-cell.coverage-90 { background: linear-gradient(135deg, #d1fae5 0%, #a7f3d0 100%); border-color: #34d399; }
                    .heatmap-cell.coverage-75 { background: linear-gradient(135deg, #dbeafe 0%, #bfdbfe 100%); border-color: #60a5fa; }
                    .heatmap-cell.coverage-50 { background: linear-gradient(135deg, #fef3c7 0%, #fde68a 100%); border-color: #fbbf24; }
                    .heatmap-cell.coverage-25 { background: linear-gradient(135deg, #fed7aa 0%, #fdba74 100%); border-color: #fb923c; }
                    .heatmap-cell.coverage-0 { background: linear-gradient(135deg, #fee2e2 0%, #fecaca 100%); border-color: #f87171; }

                    .heatmap-cell .pct {
                        font-size: 1.75rem;
                        font-weight: 700;
                        line-height: 1;
                    }

                    .heatmap-cell.coverage-100 .pct, .heatmap-cell.coverage-90 .pct { color: #059669; }
                    .heatmap-cell.coverage-75 .pct { color: #2563eb; }
                    .heatmap-cell.coverage-50 .pct { color: #d97706; }
                    .heatmap-cell.coverage-25 .pct { color: #ea580c; }
                    .heatmap-cell.coverage-0 .pct { color: #dc2626; }

                    .heatmap-cell .field {
                        font-size: 0.8rem;
                        color: #374151;
                        font-weight: 600;
                        margin-top: 0.5rem;
                    }

                    .heatmap-cell .count {
                        font-size: 0.7rem;
                        color: #6b7280;
                        margin-top: 0.25rem;
                    }

                    /* Category Row */
                    .category-row {
                        display: flex;
                        align-items: center;
                        gap: 1rem;
                        padding: 1rem;
                        background: white;
                        border: 1px solid #e5e7eb;
                        border-radius: 0.5rem;
                        margin-bottom: 0.75rem;
                    }

                    .category-icon {
                        width: 48px;
                        height: 48px;
                        border-radius: 0.5rem;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        font-size: 1.5rem;
                        background: linear-gradient(135deg, #553c9a 0%, #805ad5 100%);
                        color: white;
                    }

                    .category-info { flex: 1; }
                    .category-name { font-weight: 600; color: #1f2937; }
                    .category-desc { font-size: 0.85rem; color: #6b7280; }

                    .category-bar {
                        width: 200px;
                    }

                    .progress-bar {
                        height: 12px;
                        background: #e5e7eb;
                        border-radius: 6px;
                        overflow: hidden;
                    }

                    .progress-fill {
                        height: 100%;
                        border-radius: 6px;
                        transition: width 0.5s ease;
                    }

                    .progress-fill.excellent { background: linear-gradient(90deg, #059669 0%, #34d399 100%); }
                    .progress-fill.good { background: linear-gradient(90deg, #0891b2 0%, #22d3ee 100%); }
                    .progress-fill.fair { background: linear-gradient(90deg, #d97706 0%, #fbbf24 100%); }
                    .progress-fill.poor { background: linear-gradient(90deg, #dc2626 0%, #f87171 100%); }

                    .category-pct {
                        width: 60px;
                        text-align: right;
                        font-weight: 700;
                        font-size: 1.1rem;
                    }

                    /* Data Table */
                    .data-table {
                        width: 100%;
                        border-collapse: collapse;
                        font-size: 0.875rem;
                    }

                    .data-table th {
                        background: #553c9a;
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
                    .data-table tr:hover { background: #f5f3ff; }

                    /* Coverage indicator */
                    .coverage-indicator {
                        display: inline-flex;
                        align-items: center;
                        gap: 0.5rem;
                    }

                    .coverage-dot {
                        width: 12px;
                        height: 12px;
                        border-radius: 50%;
                    }

                    .coverage-dot.full { background: #059669; }
                    .coverage-dot.high { background: #0891b2; }
                    .coverage-dot.medium { background: #d97706; }
                    .coverage-dot.low { background: #dc2626; }

                    /* Legend */
                    .legend {
                        display: flex;
                        justify-content: center;
                        gap: 1.5rem;
                        margin-bottom: 1.5rem;
                        flex-wrap: wrap;
                    }

                    .legend-item {
                        display: flex;
                        align-items: center;
                        gap: 0.5rem;
                        font-size: 0.85rem;
                        color: #6b7280;
                    }

                    .legend-color {
                        width: 20px;
                        height: 20px;
                        border-radius: 0.25rem;
                    }

                    .legend-color.excellent { background: #d1fae5; border: 1px solid #34d399; }
                    .legend-color.good { background: #dbeafe; border: 1px solid #60a5fa; }
                    .legend-color.fair { background: #fef3c7; border: 1px solid #fbbf24; }
                    .legend-color.poor { background: #fee2e2; border: 1px solid #f87171; }

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
                            <h1>Optional Field Completeness</h1>
                            <p>Data Richness Beyond Minimum Requirements</p>
                            <div class="subtitle">
                                Content Date: <xsl:value-of select="$contentDate"/> |
                                Generated: <xsl:value-of select="format-dateTime(current-dateTime(), '[Y0001]-[M01]-[D01] [H01]:[m01]:[s01]')"/>
                            </div>
                        </div>

                        <div class="content">
                            <!-- Calculate all coverage metrics -->
                            <xsl:variable name="allAssets" select="AssetMasterData/Asset"/>
                            <xsl:variable name="totalAssets" select="count($allAssets)"/>
                            <xsl:variable name="allBonds" select="$allAssets[AssetType='BO']"/>
                            <xsl:variable name="totalBonds" select="count($allBonds)"/>

                            <!-- Identifier Coverage -->
                            <xsl:variable name="hasISIN" select="count($allAssets[Identifiers/ISIN])"/>
                            <xsl:variable name="hasSEDOL" select="count($allAssets[Identifiers/SEDOL])"/>
                            <xsl:variable name="hasWKN" select="count($allAssets[Identifiers/WKN])"/>
                            <xsl:variable name="hasBloomberg" select="count($allAssets[Identifiers/Bloomberg])"/>
                            <xsl:variable name="hasCUSIP" select="count($allAssets[Identifiers/CUSIP])"/>

                            <!-- Bond Detail Coverage -->
                            <xsl:variable name="hasIssuerName" select="count($allBonds[AssetDetails/Bond/Issuer/Name])"/>
                            <xsl:variable name="hasIssuerLEI" select="count($allBonds[AssetDetails/Bond/Issuer/Identifiers/LEI])"/>
                            <xsl:variable name="hasIssuerAddress" select="count($allBonds[AssetDetails/Bond/Issuer/Address])"/>
                            <xsl:variable name="hasMaturityDate" select="count($allBonds[AssetDetails/Bond/MaturityDate])"/>
                            <xsl:variable name="hasIssueDate" select="count($allBonds[AssetDetails/Bond/IssueDate])"/>
                            <xsl:variable name="hasInterestRate" select="count($allBonds[AssetDetails/Bond/InterestRate])"/>
                            <xsl:variable name="hasIssueYield" select="count($allBonds[AssetDetails/Bond/IssueYield])"/>
                            <xsl:variable name="hasIssueRate" select="count($allBonds[AssetDetails/Bond/IssueRate])"/>

                            <!-- Calculate overall score (with zero protection) -->
                            <xsl:variable name="identifierScore" select="if ($totalAssets > 0) then (($hasISIN + $hasSEDOL + $hasWKN + $hasBloomberg + $hasCUSIP) div (5 * $totalAssets)) * 100 else 0"/>
                            <xsl:variable name="bondDetailScore">
                                <xsl:choose>
                                    <xsl:when test="$totalBonds > 0">
                                        <xsl:value-of select="(($hasIssuerName + $hasIssuerLEI + $hasIssuerAddress + $hasMaturityDate + $hasIssueDate + $hasInterestRate + $hasIssueYield + $hasIssueRate) div (8 * $totalBonds)) * 100"/>
                                    </xsl:when>
                                    <xsl:otherwise>0</xsl:otherwise>
                                </xsl:choose>
                            </xsl:variable>
                            <xsl:variable name="overallScore" select="($identifierScore + number($bondDetailScore)) div 2"/>

                            <!-- Overall Score Display -->
                            <div class="overall-score">
                                <div class="score-value {if ($overallScore >= 80) then 'excellent' else if ($overallScore >= 60) then 'good' else if ($overallScore >= 40) then 'fair' else 'poor'}">
                                    <xsl:value-of select="format-number($overallScore, '0')"/>%
                                </div>
                                <div class="score-label">Overall Data Completeness Score</div>
                                <div class="score-breakdown">
                                    <div class="score-item">
                                        <div class="value"><xsl:value-of select="$totalAssets"/></div>
                                        <div class="label">Total Assets</div>
                                    </div>
                                    <div class="score-item">
                                        <div class="value"><xsl:value-of select="$totalBonds"/></div>
                                        <div class="label">Bonds</div>
                                    </div>
                                    <div class="score-item">
                                        <div class="value"><xsl:value-of select="format-number($identifierScore, '0')"/>%</div>
                                        <div class="label">Identifier Score</div>
                                    </div>
                                    <div class="score-item">
                                        <div class="value"><xsl:value-of select="format-number($bondDetailScore, '0')"/>%</div>
                                        <div class="label">Bond Detail Score</div>
                                    </div>
                                </div>
                            </div>

                            <!-- Legend -->
                            <div class="legend">
                                <div class="legend-item">
                                    <div class="legend-color excellent"></div>
                                    <span>90-100% (Excellent)</span>
                                </div>
                                <div class="legend-item">
                                    <div class="legend-color good"></div>
                                    <span>75-89% (Good)</span>
                                </div>
                                <div class="legend-item">
                                    <div class="legend-color fair"></div>
                                    <span>50-74% (Fair)</span>
                                </div>
                                <div class="legend-item">
                                    <div class="legend-color poor"></div>
                                    <span>0-49% (Poor)</span>
                                </div>
                            </div>

                            <!-- Identifier Coverage Heatmap -->
                            <div class="section">
                                <div class="section-header">
                                    <span class="icon">&#128273;</span>
                                    <h2>Security Identifier Coverage</h2>
                                    <span class="badge"><xsl:value-of select="$totalAssets"/> assets</span>
                                </div>
                                <div class="section-body">
                                    <div class="heatmap-grid">
                                        <xsl:call-template name="heatmap-cell">
                                            <xsl:with-param name="count" select="$hasISIN"/>
                                            <xsl:with-param name="total" select="$totalAssets"/>
                                            <xsl:with-param name="field">ISIN</xsl:with-param>
                                        </xsl:call-template>
                                        <xsl:call-template name="heatmap-cell">
                                            <xsl:with-param name="count" select="$hasSEDOL"/>
                                            <xsl:with-param name="total" select="$totalAssets"/>
                                            <xsl:with-param name="field">SEDOL</xsl:with-param>
                                        </xsl:call-template>
                                        <xsl:call-template name="heatmap-cell">
                                            <xsl:with-param name="count" select="$hasWKN"/>
                                            <xsl:with-param name="total" select="$totalAssets"/>
                                            <xsl:with-param name="field">WKN</xsl:with-param>
                                        </xsl:call-template>
                                        <xsl:call-template name="heatmap-cell">
                                            <xsl:with-param name="count" select="$hasBloomberg"/>
                                            <xsl:with-param name="total" select="$totalAssets"/>
                                            <xsl:with-param name="field">Bloomberg</xsl:with-param>
                                        </xsl:call-template>
                                        <xsl:call-template name="heatmap-cell">
                                            <xsl:with-param name="count" select="$hasCUSIP"/>
                                            <xsl:with-param name="total" select="$totalAssets"/>
                                            <xsl:with-param name="field">CUSIP</xsl:with-param>
                                        </xsl:call-template>
                                    </div>
                                </div>
                            </div>

                            <!-- Bond Issuer Coverage -->
                            <xsl:if test="$totalBonds > 0">
                                <div class="section">
                                    <div class="section-header">
                                        <span class="icon">&#127970;</span>
                                        <h2>Bond Issuer Information Coverage</h2>
                                        <span class="badge"><xsl:value-of select="$totalBonds"/> bonds</span>
                                    </div>
                                    <div class="section-body">
                                        <div class="heatmap-grid">
                                            <xsl:call-template name="heatmap-cell">
                                                <xsl:with-param name="count" select="$hasIssuerName"/>
                                                <xsl:with-param name="total" select="$totalBonds"/>
                                                <xsl:with-param name="field">Issuer Name</xsl:with-param>
                                            </xsl:call-template>
                                            <xsl:call-template name="heatmap-cell">
                                                <xsl:with-param name="count" select="$hasIssuerLEI"/>
                                                <xsl:with-param name="total" select="$totalBonds"/>
                                                <xsl:with-param name="field">Issuer LEI</xsl:with-param>
                                            </xsl:call-template>
                                            <xsl:call-template name="heatmap-cell">
                                                <xsl:with-param name="count" select="$hasIssuerAddress"/>
                                                <xsl:with-param name="total" select="$totalBonds"/>
                                                <xsl:with-param name="field">Issuer Address</xsl:with-param>
                                            </xsl:call-template>
                                        </div>
                                    </div>
                                </div>

                                <!-- Bond Detail Coverage -->
                                <div class="section">
                                    <div class="section-header">
                                        <span class="icon">&#128197;</span>
                                        <h2>Bond Date &amp; Rate Coverage</h2>
                                    </div>
                                    <div class="section-body">
                                        <div class="heatmap-grid">
                                            <xsl:call-template name="heatmap-cell">
                                                <xsl:with-param name="count" select="$hasMaturityDate"/>
                                                <xsl:with-param name="total" select="$totalBonds"/>
                                                <xsl:with-param name="field">Maturity Date</xsl:with-param>
                                            </xsl:call-template>
                                            <xsl:call-template name="heatmap-cell">
                                                <xsl:with-param name="count" select="$hasIssueDate"/>
                                                <xsl:with-param name="total" select="$totalBonds"/>
                                                <xsl:with-param name="field">Issue Date</xsl:with-param>
                                            </xsl:call-template>
                                            <xsl:call-template name="heatmap-cell">
                                                <xsl:with-param name="count" select="$hasInterestRate"/>
                                                <xsl:with-param name="total" select="$totalBonds"/>
                                                <xsl:with-param name="field">Interest Rate</xsl:with-param>
                                            </xsl:call-template>
                                            <xsl:call-template name="heatmap-cell">
                                                <xsl:with-param name="count" select="$hasIssueYield"/>
                                                <xsl:with-param name="total" select="$totalBonds"/>
                                                <xsl:with-param name="field">Issue Yield</xsl:with-param>
                                            </xsl:call-template>
                                            <xsl:call-template name="heatmap-cell">
                                                <xsl:with-param name="count" select="$hasIssueRate"/>
                                                <xsl:with-param name="total" select="$totalBonds"/>
                                                <xsl:with-param name="field">Issue Rate</xsl:with-param>
                                            </xsl:call-template>
                                        </div>
                                    </div>
                                </div>
                            </xsl:if>

                            <!-- Category Summary -->
                            <div class="section">
                                <div class="section-header">
                                    <span class="icon">&#128202;</span>
                                    <h2>Coverage by Category</h2>
                                </div>
                                <div class="section-body">
                                    <!-- Identifier Category -->
                                    <xsl:variable name="identifierPct" select="if ($totalAssets > 0) then $hasISIN div $totalAssets * 100 else 0"/>
                                    <div class="category-row">
                                        <div class="category-icon">&#128273;</div>
                                        <div class="category-info">
                                            <div class="category-name">Primary Identifier (ISIN)</div>
                                            <div class="category-desc">International Securities Identification Number</div>
                                        </div>
                                        <div class="category-bar">
                                            <div class="progress-bar">
                                                <div class="progress-fill {if ($identifierPct >= 90) then 'excellent' else if ($identifierPct >= 75) then 'good' else if ($identifierPct >= 50) then 'fair' else 'poor'}" style="width: {$identifierPct}%"></div>
                                            </div>
                                        </div>
                                        <div class="category-pct" style="color: {if ($identifierPct >= 90) then '#059669' else if ($identifierPct >= 75) then '#0891b2' else if ($identifierPct >= 50) then '#d97706' else '#dc2626'}">
                                            <xsl:value-of select="format-number($identifierPct, '0')"/>%
                                        </div>
                                    </div>

                                    <!-- Issuer LEI Category -->
                                    <xsl:if test="$totalBonds > 0">
                                        <xsl:variable name="leiPct" select="$hasIssuerLEI div $totalBonds * 100"/>
                                        <div class="category-row">
                                            <div class="category-icon">&#127970;</div>
                                            <div class="category-info">
                                                <div class="category-name">Issuer LEI</div>
                                                <div class="category-desc">Legal Entity Identifier for bond issuers</div>
                                            </div>
                                            <div class="category-bar">
                                                <div class="progress-bar">
                                                    <div class="progress-fill {if ($leiPct >= 90) then 'excellent' else if ($leiPct >= 75) then 'good' else if ($leiPct >= 50) then 'fair' else 'poor'}" style="width: {$leiPct}%"></div>
                                                </div>
                                            </div>
                                            <div class="category-pct" style="color: {if ($leiPct >= 90) then '#059669' else if ($leiPct >= 75) then '#0891b2' else if ($leiPct >= 50) then '#d97706' else '#dc2626'}">
                                                <xsl:value-of select="format-number($leiPct, '0')"/>%
                                            </div>
                                        </div>

                                        <!-- Maturity Date Category -->
                                        <xsl:variable name="maturityPct" select="$hasMaturityDate div $totalBonds * 100"/>
                                        <div class="category-row">
                                            <div class="category-icon">&#128197;</div>
                                            <div class="category-info">
                                                <div class="category-name">Bond Maturity Date</div>
                                                <div class="category-desc">Maturity date for fixed income securities</div>
                                            </div>
                                            <div class="category-bar">
                                                <div class="progress-bar">
                                                    <div class="progress-fill {if ($maturityPct >= 90) then 'excellent' else if ($maturityPct >= 75) then 'good' else if ($maturityPct >= 50) then 'fair' else 'poor'}" style="width: {$maturityPct}%"></div>
                                                </div>
                                            </div>
                                            <div class="category-pct" style="color: {if ($maturityPct >= 90) then '#059669' else if ($maturityPct >= 75) then '#0891b2' else if ($maturityPct >= 50) then '#d97706' else '#dc2626'}">
                                                <xsl:value-of select="format-number($maturityPct, '0')"/>%
                                            </div>
                                        </div>

                                        <!-- Interest Rate Category -->
                                        <xsl:variable name="ratePct" select="$hasInterestRate div $totalBonds * 100"/>
                                        <div class="category-row">
                                            <div class="category-icon">&#128176;</div>
                                            <div class="category-info">
                                                <div class="category-name">Interest Rate</div>
                                                <div class="category-desc">Coupon rate for bonds</div>
                                            </div>
                                            <div class="category-bar">
                                                <div class="progress-bar">
                                                    <div class="progress-fill {if ($ratePct >= 90) then 'excellent' else if ($ratePct >= 75) then 'good' else if ($ratePct >= 50) then 'fair' else 'poor'}" style="width: {$ratePct}%"></div>
                                                </div>
                                            </div>
                                            <div class="category-pct" style="color: {if ($ratePct >= 90) then '#059669' else if ($ratePct >= 75) then '#0891b2' else if ($ratePct >= 50) then '#d97706' else '#dc2626'}">
                                                <xsl:value-of select="format-number($ratePct, '0')"/>%
                                            </div>
                                        </div>
                                    </xsl:if>
                                </div>
                            </div>

                            <!-- Detailed Coverage Table -->
                            <div class="section">
                                <div class="section-header">
                                    <span class="icon">&#128203;</span>
                                    <h2>Detailed Field Coverage</h2>
                                </div>
                                <div class="section-body">
                                    <table class="data-table">
                                        <thead>
                                            <tr>
                                                <th>Category</th>
                                                <th>Field</th>
                                                <th>Present</th>
                                                <th>Total</th>
                                                <th>Coverage</th>
                                                <th>Status</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            <xsl:if test="$totalAssets > 0">
                                            <tr>
                                                <td>Identifiers</td>
                                                <td>ISIN</td>
                                                <td><xsl:value-of select="$hasISIN"/></td>
                                                <td><xsl:value-of select="$totalAssets"/></td>
                                                <td><xsl:value-of select="format-number($hasISIN div $totalAssets * 100, '0.0')"/>%</td>
                                                <td><xsl:call-template name="coverage-badge"><xsl:with-param name="pct" select="$hasISIN div $totalAssets * 100"/></xsl:call-template></td>
                                            </tr>
                                            <tr>
                                                <td>Identifiers</td>
                                                <td>SEDOL</td>
                                                <td><xsl:value-of select="$hasSEDOL"/></td>
                                                <td><xsl:value-of select="$totalAssets"/></td>
                                                <td><xsl:value-of select="format-number($hasSEDOL div $totalAssets * 100, '0.0')"/>%</td>
                                                <td><xsl:call-template name="coverage-badge"><xsl:with-param name="pct" select="$hasSEDOL div $totalAssets * 100"/></xsl:call-template></td>
                                            </tr>
                                            <tr>
                                                <td>Identifiers</td>
                                                <td>WKN</td>
                                                <td><xsl:value-of select="$hasWKN"/></td>
                                                <td><xsl:value-of select="$totalAssets"/></td>
                                                <td><xsl:value-of select="format-number($hasWKN div $totalAssets * 100, '0.0')"/>%</td>
                                                <td><xsl:call-template name="coverage-badge"><xsl:with-param name="pct" select="$hasWKN div $totalAssets * 100"/></xsl:call-template></td>
                                            </tr>
                                            <tr>
                                                <td>Identifiers</td>
                                                <td>Bloomberg</td>
                                                <td><xsl:value-of select="$hasBloomberg"/></td>
                                                <td><xsl:value-of select="$totalAssets"/></td>
                                                <td><xsl:value-of select="format-number($hasBloomberg div $totalAssets * 100, '0.0')"/>%</td>
                                                <td><xsl:call-template name="coverage-badge"><xsl:with-param name="pct" select="$hasBloomberg div $totalAssets * 100"/></xsl:call-template></td>
                                            </tr>
                                            </xsl:if>
                                            <xsl:if test="$totalBonds > 0">
                                                <tr>
                                                    <td>Issuer</td>
                                                    <td>Name</td>
                                                    <td><xsl:value-of select="$hasIssuerName"/></td>
                                                    <td><xsl:value-of select="$totalBonds"/></td>
                                                    <td><xsl:value-of select="format-number($hasIssuerName div $totalBonds * 100, '0.0')"/>%</td>
                                                    <td><xsl:call-template name="coverage-badge"><xsl:with-param name="pct" select="$hasIssuerName div $totalBonds * 100"/></xsl:call-template></td>
                                                </tr>
                                                <tr>
                                                    <td>Issuer</td>
                                                    <td>LEI</td>
                                                    <td><xsl:value-of select="$hasIssuerLEI"/></td>
                                                    <td><xsl:value-of select="$totalBonds"/></td>
                                                    <td><xsl:value-of select="format-number($hasIssuerLEI div $totalBonds * 100, '0.0')"/>%</td>
                                                    <td><xsl:call-template name="coverage-badge"><xsl:with-param name="pct" select="$hasIssuerLEI div $totalBonds * 100"/></xsl:call-template></td>
                                                </tr>
                                                <tr>
                                                    <td>Bond Details</td>
                                                    <td>Maturity Date</td>
                                                    <td><xsl:value-of select="$hasMaturityDate"/></td>
                                                    <td><xsl:value-of select="$totalBonds"/></td>
                                                    <td><xsl:value-of select="format-number($hasMaturityDate div $totalBonds * 100, '0.0')"/>%</td>
                                                    <td><xsl:call-template name="coverage-badge"><xsl:with-param name="pct" select="$hasMaturityDate div $totalBonds * 100"/></xsl:call-template></td>
                                                </tr>
                                                <tr>
                                                    <td>Bond Details</td>
                                                    <td>Interest Rate</td>
                                                    <td><xsl:value-of select="$hasInterestRate"/></td>
                                                    <td><xsl:value-of select="$totalBonds"/></td>
                                                    <td><xsl:value-of select="format-number($hasInterestRate div $totalBonds * 100, '0.0')"/>%</td>
                                                    <td><xsl:call-template name="coverage-badge"><xsl:with-param name="pct" select="$hasInterestRate div $totalBonds * 100"/></xsl:call-template></td>
                                                </tr>
                                            </xsl:if>
                                        </tbody>
                                    </table>
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

    <!-- Heatmap Cell Template -->
    <xsl:template name="heatmap-cell">
        <xsl:param name="count"/>
        <xsl:param name="total"/>
        <xsl:param name="field"/>
        <xsl:variable name="pct" select="if ($total > 0) then $count div $total * 100 else 0"/>
        <xsl:variable name="coverageClass">
            <xsl:choose>
                <xsl:when test="$pct >= 90">coverage-90</xsl:when>
                <xsl:when test="$pct >= 75">coverage-75</xsl:when>
                <xsl:when test="$pct >= 50">coverage-50</xsl:when>
                <xsl:when test="$pct >= 25">coverage-25</xsl:when>
                <xsl:otherwise>coverage-0</xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <div class="heatmap-cell {$coverageClass}">
            <div class="pct"><xsl:value-of select="format-number($pct, '0')"/>%</div>
            <div class="field"><xsl:value-of select="$field"/></div>
            <div class="count"><xsl:value-of select="$count"/>/<xsl:value-of select="$total"/></div>
        </div>
    </xsl:template>

    <!-- Coverage Badge Template -->
    <xsl:template name="coverage-badge">
        <xsl:param name="pct"/>
        <xsl:choose>
            <xsl:when test="$pct >= 90">
                <span style="background: #d1fae5; color: #059669; padding: 0.25rem 0.5rem; border-radius: 0.25rem; font-size: 0.75rem; font-weight: 600;">EXCELLENT</span>
            </xsl:when>
            <xsl:when test="$pct >= 75">
                <span style="background: #dbeafe; color: #2563eb; padding: 0.25rem 0.5rem; border-radius: 0.25rem; font-size: 0.75rem; font-weight: 600;">GOOD</span>
            </xsl:when>
            <xsl:when test="$pct >= 50">
                <span style="background: #fef3c7; color: #d97706; padding: 0.25rem 0.5rem; border-radius: 0.25rem; font-size: 0.75rem; font-weight: 600;">FAIR</span>
            </xsl:when>
            <xsl:otherwise>
                <span style="background: #fee2e2; color: #dc2626; padding: 0.25rem 0.5rem; border-radius: 0.25rem; font-size: 0.75rem; font-weight: 600;">POOR</span>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>
</xsl:stylesheet>
