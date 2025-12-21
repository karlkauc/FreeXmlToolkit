<?xml version="1.0" encoding="UTF-8"?>
<!--
    FundsXML4 Reference Integrity Check Report
    ==========================================
    This XSLT generates an HTML report validating cross-references
    between Positions and Assets, detecting orphan positions and
    unused assets.

    Layout: Two-column link visualization with summary counts
    Theme: Green (#276749) with Light Green accents (#68d391)

    Checks NOT in XSD (business logic only):
    - Position UniqueID -> Asset UniqueID matching
    - Orphan positions (no matching asset)
    - Unused assets (not referenced by any position)
    - ISIN mismatches between Position and Asset
-->
<xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:fn="http://www.w3.org/2005/xpath-functions">

    <xsl:output method="html" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <!-- Store ContentDate -->
    <xsl:variable name="contentDate" select="/FundsXML4/ControlData/ContentDate"/>

    <!-- Create key for efficient asset lookup -->
    <xsl:key name="assetByUniqueID" match="AssetMasterData/Asset" use="UniqueID"/>
    <xsl:key name="assetByISIN" match="AssetMasterData/Asset" use="Identifiers/ISIN"/>

    <!-- Main Template -->
    <xsl:template match="/FundsXML4">
        <html lang="en">
            <head>
                <meta charset="UTF-8"/>
                <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                <title>FundsXML - Reference Integrity Check</title>
                <style>
                    /* Reset and Base Styles */
                    * { margin: 0; padding: 0; box-sizing: border-box; }

                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
                        background: linear-gradient(135deg, #276749 0%, #38a169 50%, #68d391 100%);
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
                        background: linear-gradient(135deg, #276749 0%, #38a169 50%, #68d391 100%);
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

                    .header p { color: #c6f6d5; font-size: 1.1rem; }
                    .header .subtitle { margin-top: 0.5rem; font-size: 0.9rem; color: #9ae6b4; }

                    /* Content */
                    .content { padding: 2rem; }

                    /* Summary Stats */
                    .stats-row {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
                        gap: 1rem;
                        margin-bottom: 2rem;
                    }

                    .stat-box {
                        background: white;
                        border: 2px solid #e5e7eb;
                        border-radius: 0.75rem;
                        padding: 1rem;
                        text-align: center;
                        transition: transform 0.2s, border-color 0.2s;
                    }

                    .stat-box:hover {
                        transform: translateY(-2px);
                        border-color: #38a169;
                    }

                    .stat-box.linked { border-color: #38a169; background: linear-gradient(135deg, #f0fff4 0%, #c6f6d5 100%); }
                    .stat-box.orphan { border-color: #ef4444; background: linear-gradient(135deg, #fef2f2 0%, #fecaca 100%); }
                    .stat-box.unused { border-color: #f59e0b; background: linear-gradient(135deg, #fffbeb 0%, #fef3c7 100%); }
                    .stat-box.mismatch { border-color: #8b5cf6; background: linear-gradient(135deg, #f5f3ff 0%, #ede9fe 100%); }

                    .stat-box .number { font-size: 2.25rem; font-weight: 700; }
                    .stat-box.linked .number { color: #276749; }
                    .stat-box.orphan .number { color: #dc2626; }
                    .stat-box.unused .number { color: #d97706; }
                    .stat-box.mismatch .number { color: #7c3aed; }

                    .stat-box .label { font-size: 0.8rem; font-weight: 600; color: #374151; margin-top: 0.25rem; }

                    /* Two Column Layout */
                    .two-col {
                        display: grid;
                        grid-template-columns: 1fr 1fr;
                        gap: 2rem;
                        margin-bottom: 2rem;
                    }

                    @media (max-width: 900px) {
                        .two-col { grid-template-columns: 1fr; }
                    }

                    /* Entity Panel */
                    .entity-panel {
                        background: #f9fafb;
                        border: 1px solid #e5e7eb;
                        border-radius: 0.75rem;
                        overflow: hidden;
                    }

                    .panel-header {
                        padding: 1rem 1.5rem;
                        display: flex;
                        align-items: center;
                        gap: 0.75rem;
                        color: white;
                    }

                    .panel-header.positions { background: linear-gradient(135deg, #276749 0%, #38a169 100%); }
                    .panel-header.assets { background: linear-gradient(135deg, #1e40af 0%, #3b82f6 100%); }

                    .panel-header .icon { font-size: 1.5rem; }
                    .panel-header h3 { font-size: 1.1rem; font-weight: 600; }
                    .panel-header .count {
                        margin-left: auto;
                        background: rgba(255,255,255,0.2);
                        padding: 0.25rem 0.75rem;
                        border-radius: 1rem;
                        font-size: 0.85rem;
                    }

                    .panel-body { padding: 1rem 1.5rem; }

                    .entity-stat {
                        display: flex;
                        justify-content: space-between;
                        padding: 0.5rem 0;
                        border-bottom: 1px solid #e5e7eb;
                    }

                    .entity-stat:last-child { border-bottom: none; }
                    .entity-stat .label { color: #6b7280; }
                    .entity-stat .value { font-weight: 600; color: #1f2937; }

                    /* Section */
                    .section {
                        background: #f9fafb;
                        border: 1px solid #e5e7eb;
                        border-radius: 0.75rem;
                        margin-bottom: 1.5rem;
                        overflow: hidden;
                    }

                    .section-header {
                        background: linear-gradient(135deg, #276749 0%, #38a169 100%);
                        color: white;
                        padding: 1rem 1.5rem;
                        display: flex;
                        align-items: center;
                        gap: 0.75rem;
                    }

                    .section-header.error { background: linear-gradient(135deg, #dc2626 0%, #f87171 100%); }
                    .section-header.warning { background: linear-gradient(135deg, #d97706 0%, #fbbf24 100%); }

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

                    /* Link Diagram */
                    .link-diagram {
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        padding: 1.5rem;
                        background: linear-gradient(135deg, #f0fff4 0%, #c6f6d5 100%);
                        border-radius: 0.5rem;
                        margin-bottom: 1rem;
                    }

                    .link-box {
                        background: white;
                        border: 2px solid #38a169;
                        border-radius: 0.5rem;
                        padding: 1rem 1.5rem;
                        text-align: center;
                        min-width: 150px;
                    }

                    .link-box .title { font-weight: 700; color: #276749; font-size: 1.1rem; }
                    .link-box .count { font-size: 0.9rem; color: #6b7280; }

                    .link-arrow {
                        display: flex;
                        align-items: center;
                        padding: 0 1.5rem;
                    }

                    .arrow-line {
                        width: 60px;
                        height: 3px;
                        background: #38a169;
                        position: relative;
                    }

                    .arrow-line::after {
                        content: "";
                        position: absolute;
                        right: -8px;
                        top: -5px;
                        border: 6px solid transparent;
                        border-left: 10px solid #38a169;
                    }

                    .arrow-label {
                        font-size: 0.75rem;
                        color: #38a169;
                        font-weight: 600;
                        position: absolute;
                        top: -18px;
                        left: 50%;
                        transform: translateX(-50%);
                        white-space: nowrap;
                    }

                    /* Data Table */
                    .data-table {
                        width: 100%;
                        border-collapse: collapse;
                        font-size: 0.875rem;
                    }

                    .data-table th {
                        background: #276749;
                        color: white;
                        padding: 0.75rem 1rem;
                        text-align: left;
                        font-weight: 600;
                    }

                    .data-table.error th { background: #dc2626; }
                    .data-table.warning th { background: #d97706; }

                    .data-table td {
                        padding: 0.75rem 1rem;
                        border-bottom: 1px solid #e5e7eb;
                    }

                    .data-table tr:nth-child(even) { background: #f9fafb; }
                    .data-table tr:hover { background: #f3f4f6; }

                    /* Status Badges */
                    .badge-success {
                        background: #d1fae5;
                        color: #059669;
                        padding: 0.25rem 0.5rem;
                        border-radius: 0.25rem;
                        font-size: 0.75rem;
                        font-weight: 600;
                    }

                    .badge-error {
                        background: #fee2e2;
                        color: #dc2626;
                        padding: 0.25rem 0.5rem;
                        border-radius: 0.25rem;
                        font-size: 0.75rem;
                        font-weight: 600;
                    }

                    .badge-warning {
                        background: #fef3c7;
                        color: #d97706;
                        padding: 0.25rem 0.5rem;
                        border-radius: 0.25rem;
                        font-size: 0.75rem;
                        font-weight: 600;
                    }

                    /* Link Status Icon */
                    .link-ok { color: #059669; }
                    .link-broken { color: #dc2626; }

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
                            <h1>Reference Integrity Check</h1>
                            <p>Position-Asset Cross-Reference Validation</p>
                            <div class="subtitle">
                                Content Date: <xsl:value-of select="$contentDate"/> |
                                Generated: <xsl:value-of select="format-dateTime(current-dateTime(), '[Y0001]-[M01]-[D01] [H01]:[m01]:[s01]')"/>
                            </div>
                        </div>

                        <div class="content">
                            <!-- Gather all positions and assets -->
                            <xsl:variable name="allPositions" select="//Funds/Fund/FundDynamicData/Portfolios/Portfolio/Positions/Position"/>
                            <xsl:variable name="allAssets" select="AssetMasterData/Asset"/>
                            <xsl:variable name="positionIDs" select="distinct-values($allPositions/UniqueID)"/>
                            <xsl:variable name="assetIDs" select="distinct-values($allAssets/UniqueID)"/>

                            <!-- Calculate matches -->
                            <xsl:variable name="linkedPositions" select="$allPositions[UniqueID = $assetIDs]"/>
                            <xsl:variable name="orphanPositions" select="$allPositions[not(UniqueID = $assetIDs)]"/>
                            <xsl:variable name="referencedAssets" select="$allAssets[UniqueID = $positionIDs]"/>
                            <xsl:variable name="unusedAssets" select="$allAssets[not(UniqueID = $positionIDs)]"/>

                            <!-- ISIN mismatch check -->
                            <xsl:variable name="isinMismatches" select="$linkedPositions[Identifiers/ISIN and key('assetByUniqueID', UniqueID)/Identifiers/ISIN and Identifiers/ISIN != key('assetByUniqueID', UniqueID)/Identifiers/ISIN]"/>

                            <!-- Summary Stats -->
                            <div class="stats-row">
                                <div class="stat-box linked">
                                    <div class="number"><xsl:value-of select="count($linkedPositions)"/></div>
                                    <div class="label">Linked Positions</div>
                                </div>
                                <div class="stat-box orphan">
                                    <div class="number"><xsl:value-of select="count($orphanPositions)"/></div>
                                    <div class="label">Orphan Positions</div>
                                </div>
                                <div class="stat-box unused">
                                    <div class="number"><xsl:value-of select="count($unusedAssets)"/></div>
                                    <div class="label">Unused Assets</div>
                                </div>
                                <div class="stat-box mismatch">
                                    <div class="number"><xsl:value-of select="count($isinMismatches)"/></div>
                                    <div class="label">ISIN Mismatches</div>
                                </div>
                            </div>

                            <!-- Two Column Overview -->
                            <div class="two-col">
                                <div class="entity-panel">
                                    <div class="panel-header positions">
                                        <span class="icon">&#128203;</span>
                                        <h3>Positions</h3>
                                        <span class="count"><xsl:value-of select="count($allPositions)"/> total</span>
                                    </div>
                                    <div class="panel-body">
                                        <div class="entity-stat">
                                            <span class="label">Total Positions</span>
                                            <span class="value"><xsl:value-of select="count($allPositions)"/></span>
                                        </div>
                                        <div class="entity-stat">
                                            <span class="label">Unique Position IDs</span>
                                            <span class="value"><xsl:value-of select="count($positionIDs)"/></span>
                                        </div>
                                        <div class="entity-stat">
                                            <span class="label">Linked to Assets</span>
                                            <span class="value" style="color: #059669"><xsl:value-of select="count($linkedPositions)"/></span>
                                        </div>
                                        <div class="entity-stat">
                                            <span class="label">Orphans (no asset)</span>
                                            <span class="value" style="color: #dc2626"><xsl:value-of select="count($orphanPositions)"/></span>
                                        </div>
                                    </div>
                                </div>

                                <div class="entity-panel">
                                    <div class="panel-header assets">
                                        <span class="icon">&#128230;</span>
                                        <h3>Assets</h3>
                                        <span class="count"><xsl:value-of select="count($allAssets)"/> total</span>
                                    </div>
                                    <div class="panel-body">
                                        <div class="entity-stat">
                                            <span class="label">Total Assets</span>
                                            <span class="value"><xsl:value-of select="count($allAssets)"/></span>
                                        </div>
                                        <div class="entity-stat">
                                            <span class="label">Unique Asset IDs</span>
                                            <span class="value"><xsl:value-of select="count($assetIDs)"/></span>
                                        </div>
                                        <div class="entity-stat">
                                            <span class="label">Referenced by Positions</span>
                                            <span class="value" style="color: #059669"><xsl:value-of select="count($referencedAssets)"/></span>
                                        </div>
                                        <div class="entity-stat">
                                            <span class="label">Unused (no positions)</span>
                                            <span class="value" style="color: #d97706"><xsl:value-of select="count($unusedAssets)"/></span>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <!-- Integrity Check Results -->
                            <div class="section">
                                <div class="section-header">
                                    <span class="icon">&#9989;</span>
                                    <h2>Integrity Check Results</h2>
                                </div>
                                <div class="section-body">
                                    <!-- Check 1: No Orphan Positions -->
                                    <div class="check-result {if (count($orphanPositions) = 0) then 'pass' else 'fail'}">
                                        <span class="icon">
                                            <xsl:choose>
                                                <xsl:when test="count($orphanPositions) = 0">&#9989;</xsl:when>
                                                <xsl:otherwise>&#10060;</xsl:otherwise>
                                            </xsl:choose>
                                        </span>
                                        <div class="info">
                                            <div class="title">All Positions Have Matching Assets</div>
                                            <div class="desc">Every position UniqueID should exist in AssetMasterData</div>
                                        </div>
                                        <div class="status">
                                            <xsl:choose>
                                                <xsl:when test="count($orphanPositions) = 0"><span class="badge-success">PASS</span></xsl:when>
                                                <xsl:otherwise><span class="badge-error"><xsl:value-of select="count($orphanPositions)"/> ORPHANS</span></xsl:otherwise>
                                            </xsl:choose>
                                        </div>
                                    </div>

                                    <!-- Check 2: No Unused Assets -->
                                    <div class="check-result {if (count($unusedAssets) = 0) then 'pass' else 'warn'}">
                                        <span class="icon">
                                            <xsl:choose>
                                                <xsl:when test="count($unusedAssets) = 0">&#9989;</xsl:when>
                                                <xsl:otherwise>&#9888;</xsl:otherwise>
                                            </xsl:choose>
                                        </span>
                                        <div class="info">
                                            <div class="title">All Assets Are Referenced</div>
                                            <div class="desc">Assets in master data should be referenced by at least one position</div>
                                        </div>
                                        <div class="status">
                                            <xsl:choose>
                                                <xsl:when test="count($unusedAssets) = 0"><span class="badge-success">PASS</span></xsl:when>
                                                <xsl:otherwise><span class="badge-warning"><xsl:value-of select="count($unusedAssets)"/> UNUSED</span></xsl:otherwise>
                                            </xsl:choose>
                                        </div>
                                    </div>

                                    <!-- Check 3: No ISIN Mismatches -->
                                    <div class="check-result {if (count($isinMismatches) = 0) then 'pass' else 'fail'}">
                                        <span class="icon">
                                            <xsl:choose>
                                                <xsl:when test="count($isinMismatches) = 0">&#9989;</xsl:when>
                                                <xsl:otherwise>&#10060;</xsl:otherwise>
                                            </xsl:choose>
                                        </span>
                                        <div class="info">
                                            <div class="title">ISIN Consistency</div>
                                            <div class="desc">Position ISIN should match corresponding Asset ISIN</div>
                                        </div>
                                        <div class="status">
                                            <xsl:choose>
                                                <xsl:when test="count($isinMismatches) = 0"><span class="badge-success">PASS</span></xsl:when>
                                                <xsl:otherwise><span class="badge-error"><xsl:value-of select="count($isinMismatches)"/> MISMATCHES</span></xsl:otherwise>
                                            </xsl:choose>
                                        </div>
                                    </div>

                                    <!-- Check 4: Link Coverage -->
                                    <xsl:variable name="linkCoverage" select="count($linkedPositions) div count($allPositions) * 100"/>
                                    <div class="check-result {if ($linkCoverage = 100) then 'pass' else if ($linkCoverage >= 95) then 'warn' else 'fail'}">
                                        <span class="icon">
                                            <xsl:choose>
                                                <xsl:when test="$linkCoverage = 100">&#9989;</xsl:when>
                                                <xsl:when test="$linkCoverage >= 95">&#9888;</xsl:when>
                                                <xsl:otherwise>&#10060;</xsl:otherwise>
                                            </xsl:choose>
                                        </span>
                                        <div class="info">
                                            <div class="title">Position-Asset Link Coverage</div>
                                            <div class="desc">Percentage of positions successfully linked to assets</div>
                                        </div>
                                        <div class="status">
                                            <span class="{if ($linkCoverage = 100) then 'badge-success' else if ($linkCoverage >= 95) then 'badge-warning' else 'badge-error'}">
                                                <xsl:value-of select="format-number($linkCoverage, '0.0')"/>%
                                            </span>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <!-- Orphan Positions Detail -->
                            <xsl:if test="count($orphanPositions) > 0">
                                <div class="section">
                                    <div class="section-header error">
                                        <span class="icon">&#128683;</span>
                                        <h2>Orphan Positions (No Matching Asset)</h2>
                                        <span class="badge"><xsl:value-of select="count($orphanPositions)"/> positions</span>
                                    </div>
                                    <div class="section-body">
                                        <table class="data-table error">
                                            <thead>
                                                <tr>
                                                    <th>UniqueID</th>
                                                    <th>ISIN</th>
                                                    <th>Currency</th>
                                                    <th>Total Value</th>
                                                    <th>Status</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                <xsl:for-each select="$orphanPositions">
                                                    <xsl:sort select="UniqueID"/>
                                                    <tr>
                                                        <td class="mono"><xsl:value-of select="UniqueID"/></td>
                                                        <td class="mono"><xsl:value-of select="Identifiers/ISIN"/></td>
                                                        <td><xsl:value-of select="Currency"/></td>
                                                        <td><xsl:value-of select="format-number(TotalValue/Amount, '#,##0.00')"/></td>
                                                        <td><span class="badge-error">ORPHAN</span></td>
                                                    </tr>
                                                </xsl:for-each>
                                            </tbody>
                                        </table>
                                    </div>
                                </div>
                            </xsl:if>

                            <!-- Unused Assets Detail -->
                            <xsl:if test="count($unusedAssets) > 0">
                                <div class="section">
                                    <div class="section-header warning">
                                        <span class="icon">&#9888;</span>
                                        <h2>Unused Assets (No Position Reference)</h2>
                                        <span class="badge"><xsl:value-of select="count($unusedAssets)"/> assets</span>
                                    </div>
                                    <div class="section-body">
                                        <table class="data-table warning">
                                            <thead>
                                                <tr>
                                                    <th>UniqueID</th>
                                                    <th>ISIN</th>
                                                    <th>Name</th>
                                                    <th>Asset Type</th>
                                                    <th>Status</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                <xsl:for-each select="$unusedAssets">
                                                    <xsl:sort select="UniqueID"/>
                                                    <tr>
                                                        <td class="mono"><xsl:value-of select="UniqueID"/></td>
                                                        <td class="mono"><xsl:value-of select="Identifiers/ISIN"/></td>
                                                        <td><xsl:value-of select="substring(Name, 1, 40)"/><xsl:if test="string-length(Name) > 40">...</xsl:if></td>
                                                        <td><xsl:value-of select="AssetType"/></td>
                                                        <td><span class="badge-warning">UNUSED</span></td>
                                                    </tr>
                                                </xsl:for-each>
                                            </tbody>
                                        </table>
                                    </div>
                                </div>
                            </xsl:if>

                            <!-- ISIN Mismatches Detail -->
                            <xsl:if test="count($isinMismatches) > 0">
                                <div class="section">
                                    <div class="section-header error">
                                        <span class="icon">&#10060;</span>
                                        <h2>ISIN Mismatches</h2>
                                        <span class="badge"><xsl:value-of select="count($isinMismatches)"/> mismatches</span>
                                    </div>
                                    <div class="section-body">
                                        <table class="data-table error">
                                            <thead>
                                                <tr>
                                                    <th>UniqueID</th>
                                                    <th>Position ISIN</th>
                                                    <th>Asset ISIN</th>
                                                    <th>Asset Name</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                <xsl:for-each select="$isinMismatches">
                                                    <xsl:variable name="posISIN" select="Identifiers/ISIN"/>
                                                    <xsl:variable name="assetISIN" select="key('assetByUniqueID', UniqueID)/Identifiers/ISIN"/>
                                                    <tr>
                                                        <td class="mono"><xsl:value-of select="UniqueID"/></td>
                                                        <td class="mono"><xsl:value-of select="$posISIN"/></td>
                                                        <td class="mono"><xsl:value-of select="$assetISIN"/></td>
                                                        <td><xsl:value-of select="key('assetByUniqueID', UniqueID)/Name"/></td>
                                                    </tr>
                                                </xsl:for-each>
                                            </tbody>
                                        </table>
                                    </div>
                                </div>
                            </xsl:if>
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
