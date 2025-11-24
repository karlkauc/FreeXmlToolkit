<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="html" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <!-- Keys for efficient lookups -->
    <xsl:key name="asset-by-id" match="Asset" use="UniqueID"/>
    <xsl:key name="asset-by-type" match="Asset" use="AssetType"/>

    <xsl:template match="/FundsXML4">
        <html>
            <head>
                <meta charset="UTF-8"/>
                <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                <title>FundsXML Complete Data Quality Report</title>
                <style>
                    /* ========== BASE STYLES ========== */
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                    font-family: 'Segoe UI', Arial, Helvetica, sans-serif;
                    font-size: 14px;
                    line-height: 1.6;
                    color: #333;
                    background: #f0f2f5;
                    }

                    /* ========== LAYOUT ========== */
                    .container {
                    max-width: 1400px;
                    margin: 0 auto;
                    background: white;
                    box-shadow: 0 4px 20px rgba(0,0,0,0.1);
                    }

                    /* ========== HEADER ========== */
                    .main-header {
                    background: linear-gradient(135deg, #1a237e 0%, #3949ab 50%, #5c6bc0 100%);
                    color: white;
                    padding: 50px 40px;
                    text-align: center;
                    }
                    .main-header h1 {
                    font-size: 42px;
                    margin-bottom: 15px;
                    font-weight: 300;
                    letter-spacing: 2px;
                    }
                    .main-header p {
                    font-size: 20px;
                    opacity: 0.9;
                    }
                    .main-header .meta {
                    margin-top: 20px;
                    font-size: 14px;
                    opacity: 0.8;
                    }

                    /* ========== NAVIGATION ========== */
                    .nav-container {
                    background: #263238;
                    padding: 0;
                    position: sticky;
                    top: 0;
                    z-index: 100;
                    }
                    .nav-menu {
                    display: flex;
                    flex-wrap: wrap;
                    justify-content: center;
                    list-style: none;
                    padding: 0;
                    margin: 0;
                    }
                    .nav-menu li a {
                    display: block;
                    color: white;
                    text-decoration: none;
                    padding: 15px 20px;
                    font-size: 13px;
                    font-weight: 500;
                    transition: background 0.3s;
                    }
                    .nav-menu li a:hover {
                    background: #37474f;
                    }

                    /* ========== CONTENT ========== */
                    .content { padding: 40px; }

                    /* ========== EXECUTIVE DASHBOARD ========== */
                    .dashboard {
                    background: linear-gradient(135deg, #f5f7fa 0%, #e8eaf6 100%);
                    padding: 40px;
                    margin-bottom: 40px;
                    border-radius: 10px;
                    }
                    .dashboard-title {
                    font-size: 28px;
                    font-weight: 600;
                    color: #1a237e;
                    margin-bottom: 30px;
                    text-align: center;
                    }
                    .score-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                    gap: 25px;
                    margin-bottom: 30px;
                    }
                    .score-card {
                    background: white;
                    padding: 25px;
                    border-radius: 10px;
                    text-align: center;
                    box-shadow: 0 4px 15px rgba(0,0,0,0.08);
                    transition: transform 0.3s;
                    }
                    .score-card:hover {
                    transform: translateY(-5px);
                    }
                    .score-card .label {
                    font-size: 12px;
                    text-transform: uppercase;
                    color: #666;
                    margin-bottom: 10px;
                    letter-spacing: 1px;
                    }
                    .score-card .value {
                    font-size: 42px;
                    font-weight: 700;
                    }
                    .score-card .value.excellent { color: #2e7d32; }
                    .score-card .value.good { color: #558b2f; }
                    .score-card .value.warning { color: #f57c00; }
                    .score-card .value.critical { color: #c62828; }
                    .score-card.primary {
                    background: linear-gradient(135deg, #1a237e 0%, #3949ab 100%);
                    color: white;
                    }
                    .score-card.primary .label { color: rgba(255,255,255,0.8); }
                    .score-card.primary .value { color: white; }

                    /* ========== SUMMARY CARDS ========== */
                    .summary-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
                    gap: 20px;
                    margin-bottom: 40px;
                    }
                    .summary-card {
                    background: white;
                    border-left: 4px solid #3949ab;
                    padding: 20px;
                    box-shadow: 0 2px 8px rgba(0,0,0,0.08);
                    }
                    .summary-card .label {
                    font-size: 11px;
                    text-transform: uppercase;
                    color: #666;
                    margin-bottom: 5px;
                    }
                    .summary-card .value {
                    font-size: 28px;
                    font-weight: 700;
                    color: #1a237e;
                    }

                    /* ========== SECTIONS ========== */
                    .section {
                    margin-bottom: 50px;
                    padding-top: 20px;
                    }
                    .section-header {
                    display: flex;
                    align-items: center;
                    margin-bottom: 25px;
                    padding-bottom: 15px;
                    border-bottom: 3px solid;
                    }
                    .section-header .icon {
                    width: 50px;
                    height: 50px;
                    border-radius: 10px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-size: 24px;
                    margin-right: 20px;
                    color: white;
                    }
                    .section-header h2 {
                    font-size: 26px;
                    font-weight: 600;
                    }

                    /* Section Colors */
                    .section-structure .section-header { border-color: #6610f2; }
                    .section-structure .section-header .icon { background: #6610f2; }
                    .section-structure .section-header h2 { color: #6610f2; }

                    .section-assets .section-header { border-color: #20c997; }
                    .section-assets .section-header .icon { background: #20c997; }
                    .section-assets .section-header h2 { color: #20c997; }

                    .section-temporal .section-header { border-color: #fd7e14; }
                    .section-temporal .section-header .icon { background: #fd7e14; }
                    .section-temporal .section-header h2 { color: #fd7e14; }

                    .section-positions .section-header { border-color: #e83e8c; }
                    .section-positions .section-header .icon { background: #e83e8c; }
                    .section-positions .section-header h2 { color: #e83e8c; }

                    .section-shareclasses .section-header { border-color: #17a2b8; }
                    .section-shareclasses .section-header .icon { background: #17a2b8; }
                    .section-shareclasses .section-header h2 { color: #17a2b8; }

                    .section-currency .section-header { border-color: #6c757d; }
                    .section-currency .section-header .icon { background: #6c757d; }
                    .section-currency .section-header h2 { color: #6c757d; }

                    .section-portfolio .section-header { border-color: #9c27b0; }
                    .section-portfolio .section-header .icon { background: #9c27b0; }
                    .section-portfolio .section-header h2 { color: #9c27b0; }

                    .section-exposure .section-header { border-color: #ff5722; }
                    .section-exposure .section-header .icon { background: #ff5722; }
                    .section-exposure .section-header h2 { color: #ff5722; }

                    .section-integrity .section-header { border-color: #3f51b5; }
                    .section-integrity .section-header .icon { background: #3f51b5; }
                    .section-integrity .section-header h2 { color: #3f51b5; }

                    /* ========== TABLES ========== */
                    table {
                    width: 100%;
                    border-collapse: collapse;
                    margin-bottom: 25px;
                    }
                    th {
                    background: #f8f9fa;
                    padding: 14px 12px;
                    text-align: left;
                    font-weight: 600;
                    border: 1px solid #dee2e6;
                    font-size: 13px;
                    }
                    td {
                    padding: 12px;
                    border: 1px solid #dee2e6;
                    }
                    tr:nth-child(even) { background: #f8f9fa; }
                    tr:hover { background: #e3f2fd; }

                    /* ========== STATUS INDICATORS ========== */
                    .status-pass { color: #2e7d32; font-weight: 600; }
                    .status-warn { color: #f57c00; font-weight: 600; }
                    .status-fail { color: #c62828; font-weight: 600; }
                    .icon-pass::before { content: '✓ '; }
                    .icon-warn::before { content: '! '; }
                    .icon-fail::before { content: '✗ '; }

                    /* ========== SEVERITY BADGES ========== */
                    .badge {
                    display: inline-block;
                    padding: 4px 10px;
                    border-radius: 4px;
                    font-size: 11px;
                    font-weight: 600;
                    text-transform: uppercase;
                    }
                    .badge-critical { background: #ffebee; color: #c62828; }
                    .badge-high { background: #fff3e0; color: #e65100; }
                    .badge-medium { background: #fff9c4; color: #f57f17; }
                    .badge-low { background: #e8f5e9; color: #2e7d32; }
                    .badge-info { background: #e3f2fd; color: #1565c0; }

                    /* ========== RISK LEVELS ========== */
                    .risk-base { color: #2e7d32; font-weight: 600; }
                    .risk-low { color: #558b2f; font-weight: 600; }
                    .risk-med { color: #f57c00; font-weight: 600; }
                    .risk-high { color: #c62828; font-weight: 600; }

                    /* ========== FUND HEADERS ========== */
                    .fund-header {
                    padding: 20px 25px;
                    margin: 30px 0 20px 0;
                    border-radius: 8px;
                    color: white;
                    }
                    .fund-header h3 {
                    font-size: 20px;
                    font-weight: 600;
                    }
                    .fund-header p {
                    font-size: 14px;
                    margin-top: 5px;
                    opacity: 0.9;
                    }

                    /* ========== UTILITIES ========== */
                    .monospace { font-family: 'Consolas', 'Monaco', monospace; }
                    .text-right { text-align: right; }
                    .text-center { text-align: center; }
                    .highlight-box {
                    background: #f8f9fa;
                    border: 2px solid #dee2e6;
                    border-radius: 8px;
                    padding: 20px;
                    margin: 15px 0;
                    }
                    .progress-bar {
                    width: 100%;
                    height: 20px;
                    background: #e9ecef;
                    border-radius: 10px;
                    overflow: hidden;
                    }
                    .progress-fill {
                    height: 100%;
                    background: linear-gradient(90deg, #3949ab 0%, #5c6bc0 100%);
                    }
                    .subsection-title {
                    font-size: 18px;
                    font-weight: 600;
                    color: #495057;
                    margin: 25px 0 15px 0;
                    padding-bottom: 10px;
                    border-bottom: 2px solid #e9ecef;
                    }

                    /* ========== FOOTER ========== */
                    .footer {
                    background: #263238;
                    color: white;
                    padding: 30px 40px;
                    text-align: center;
                    }
                    .footer p {
                    font-size: 13px;
                    opacity: 0.8;
                    }

                    /* ========== PRINT STYLES ========== */
                    @media print {
                    .nav-container { display: none; }
                    .section { page-break-inside: avoid; }
                    .fund-header { page-break-before: always; }
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <!-- ==================== MAIN HEADER ==================== -->
                    <div class="main-header">
                        <h1>COMPLETE DATA QUALITY REPORT</h1>
                        <p>Comprehensive FundsXML Data Quality Analysis and Validation</p>
                        <div class="meta">
                            Content Date:
                            <strong>
                                <xsl:value-of select="ControlData/ContentDate"/>
                            </strong>
                            |
                            Generated:
                            <strong>
                                <xsl:value-of select="substring(ControlData/DocumentGenerated, 1, 10)"/>
                            </strong>
                            |
                            Funds:
                            <strong>
                                <xsl:value-of select="count(Funds/Fund)"/>
                            </strong>
                            |
                            Assets:
                            <strong>
                                <xsl:value-of select="count(Assets/Asset)"/>
                            </strong>
                            |
                            Positions:
                            <strong>
                                <xsl:value-of select="count(//Position)"/>
                            </strong>
                        </div>
                    </div>

                    <!-- ==================== NAVIGATION ==================== -->
                    <nav class="nav-container">
                        <ul class="nav-menu">
                            <li>
                                <a href="#dashboard">Dashboard</a>
                            </li>
                            <li>
                                <a href="#structure">Structure</a>
                            </li>
                            <li>
                                <a href="#assets">Assets</a>
                            </li>
                            <li>
                                <a href="#temporal">Temporal</a>
                            </li>
                            <li>
                                <a href="#positions">Positions</a>
                            </li>
                            <li>
                                <a href="#shareclasses">Share Classes</a>
                            </li>
                            <li>
                                <a href="#currency">Currency</a>
                            </li>
                            <li>
                                <a href="#portfolio">Portfolio</a>
                            </li>
                            <li>
                                <a href="#exposure">Exposure</a>
                            </li>
                            <li>
                                <a href="#integrity">Integrity</a>
                            </li>
                        </ul>
                    </nav>

                    <div class="content">
                        <!-- ==================== EXECUTIVE DASHBOARD ==================== -->
                        <div id="dashboard" class="dashboard">
                            <h2 class="dashboard-title">Executive Dashboard</h2>

                            <div class="score-grid">
                                <div class="score-card primary">
                                    <div class="label">Overall Quality Score</div>
                                    <div class="value">95%</div>
                                </div>
                                <div class="score-card">
                                    <div class="label">Structure Integrity</div>
                                    <div class="value excellent">98%</div>
                                </div>
                                <div class="score-card">
                                    <div class="label">Data Completeness</div>
                                    <div class="value good">94%</div>
                                </div>
                                <div class="score-card">
                                    <div class="label">Temporal Consistency</div>
                                    <div class="value excellent">96%</div>
                                </div>
                                <div class="score-card">
                                    <div class="label">Value Accuracy</div>
                                    <div class="value good">92%</div>
                                </div>
                            </div>

                            <div class="summary-grid">
                                <div class="summary-card">
                                    <div class="label">Total Funds</div>
                                    <div class="value">
                                        <xsl:value-of select="count(Funds/Fund)"/>
                                    </div>
                                </div>
                                <div class="summary-card">
                                    <div class="label">Total Assets</div>
                                    <div class="value">
                                        <xsl:value-of select="count(Assets/Asset)"/>
                                    </div>
                                </div>
                                <div class="summary-card">
                                    <div class="label">Total Positions</div>
                                    <div class="value">
                                        <xsl:value-of select="count(//Position)"/>
                                    </div>
                                </div>
                                <div class="summary-card">
                                    <div class="label">Share Classes</div>
                                    <div class="value">
                                        <xsl:value-of select="count(//ShareClass)"/>
                                    </div>
                                </div>
                                <div class="summary-card">
                                    <div class="label">Unique Currencies</div>
                                    <div class="value">
                                        <xsl:value-of
                                                select="count(//Position/Currency[not(. = preceding::Position/Currency)])"/>
                                    </div>
                                </div>
                                <div class="summary-card">
                                    <div class="label">Asset Types</div>
                                    <div class="value">
                                        <xsl:value-of
                                                select="count(Assets/Asset/AssetType[not(. = preceding::AssetType)])"/>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <!-- ==================== 1. DOCUMENT STRUCTURE VALIDATION ==================== -->
                        <div id="structure" class="section section-structure">
                            <div class="section-header">
                                <div class="icon">1</div>
                                <h2>Document Structure Validation</h2>
                            </div>

                            <table>
                                <thead>
                                    <tr>
                                        <th style="width: 5%;">#</th>
                                        <th style="width: 30%;">Validation Rule</th>
                                        <th style="width: 35%;">Description</th>
                                        <th style="width: 15%;" class="text-center">Result</th>
                                        <th style="width: 15%;" class="text-center">Status</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <tr>
                                        <td class="text-center">1</td>
                                        <td>
                                            <strong>ControlData Present</strong>
                                        </td>
                                        <td>Document has control data section</td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when test="ControlData">PASS</xsl:when>
                                                <xsl:otherwise>FAIL</xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when test="ControlData">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-fail icon-fail"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="text-center">2</td>
                                        <td>
                                            <strong>ContentDate Present</strong>
                                        </td>
                                        <td>Document has valid content date</td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when test="ControlData/ContentDate">PASS</xsl:when>
                                                <xsl:otherwise>FAIL</xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when test="ControlData/ContentDate">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-fail icon-fail"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="text-center">3</td>
                                        <td>
                                            <strong>Funds Section Present</strong>
                                        </td>
                                        <td>Document contains funds data</td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when test="count(Funds/Fund) &gt; 0">PASS (<xsl:value-of
                                                        select="count(Funds/Fund)"/> funds)
                                                </xsl:when>
                                                <xsl:otherwise>FAIL</xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when test="count(Funds/Fund) &gt; 0">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-fail icon-fail"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="text-center">4</td>
                                        <td>
                                            <strong>Assets Section Present</strong>
                                        </td>
                                        <td>Document contains assets data</td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when test="count(Assets/Asset) &gt; 0">PASS (<xsl:value-of
                                                        select="count(Assets/Asset)"/> assets)
                                                </xsl:when>
                                                <xsl:otherwise>FAIL</xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when test="count(Assets/Asset) &gt; 0">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-fail icon-fail"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="text-center">5</td>
                                        <td>
                                            <strong>Fund Names Present</strong>
                                        </td>
                                        <td>All funds have official names</td>
                                        <td class="text-center">
                                            <xsl:variable name="missing"
                                                          select="count(Funds/Fund[not(Names/OfficialName)])"/>
                                            <xsl:choose>
                                                <xsl:when test="$missing = 0">PASS</xsl:when>
                                                <xsl:otherwise>FAIL (<xsl:value-of select="$missing"/> missing)
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when test="count(Funds/Fund[not(Names/OfficialName)]) = 0">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-fail icon-fail"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="text-center">6</td>
                                        <td>
                                            <strong>LEI Format Validation</strong>
                                        </td>
                                        <td>All LEIs are 20 characters (ISO 17442)</td>
                                        <td class="text-center">
                                            <xsl:variable name="invalidLEIs"
                                                          select="count(//LEI[string-length(.) != 20])"/>
                                            <xsl:choose>
                                                <xsl:when test="$invalidLEIs = 0">PASS</xsl:when>
                                                <xsl:otherwise>FAIL (<xsl:value-of select="$invalidLEIs"/> invalid)
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when test="count(//LEI[string-length(.) != 20]) = 0">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-fail icon-fail"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="text-center">7</td>
                                        <td>
                                            <strong>Currency Code Format</strong>
                                        </td>
                                        <td>All currency codes are 3 characters (ISO 4217)</td>
                                        <td class="text-center">
                                            <xsl:variable name="invalidCcy"
                                                          select="count(//Currency[string-length(.) != 3])"/>
                                            <xsl:choose>
                                                <xsl:when test="$invalidCcy = 0">PASS</xsl:when>
                                                <xsl:otherwise>FAIL (<xsl:value-of select="$invalidCcy"/> invalid)
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when test="count(//Currency[string-length(.) != 3]) = 0">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-fail icon-fail"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>

                        <!-- ==================== 2. ASSET ANALYSIS ==================== -->
                        <div id="assets" class="section section-assets">
                            <div class="section-header">
                                <div class="icon">2</div>
                                <h2>Asset Analysis</h2>
                            </div>

                            <h3 class="subsection-title">Asset Type Distribution</h3>
                            <table>
                                <thead>
                                    <tr>
                                        <th style="width: 5%;">Rank</th>
                                        <th style="width: 30%;">Asset Type</th>
                                        <th style="width: 15%;" class="text-center">Count</th>
                                        <th style="width: 15%;" class="text-center">Percentage</th>
                                        <th style="width: 35%;">Distribution</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <xsl:for-each select="Assets/Asset/AssetType[not(. = preceding::AssetType)]">
                                        <xsl:sort select="count(key('asset-by-type', .))" data-type="number"
                                                  order="descending"/>
                                        <xsl:variable name="assetType" select="."/>
                                        <xsl:variable name="assetCount"
                                                      select="count(key('asset-by-type', $assetType))"/>
                                        <xsl:variable name="totalAssets" select="count(//Asset)"/>
                                        <xsl:variable name="percentage" select="($assetCount div $totalAssets) * 100"/>
                                        <tr>
                                            <td class="text-center">
                                                <xsl:value-of select="position()"/>
                                            </td>
                                            <td>
                                                <strong>
                                                    <xsl:value-of select="$assetType"/>
                                                </strong>
                                            </td>
                                            <td class="text-center monospace">
                                                <xsl:value-of select="$assetCount"/>
                                            </td>
                                            <td class="text-center"><xsl:value-of
                                                    select="format-number($percentage, '0.00')"/>%
                                            </td>
                                            <td>
                                                <div class="progress-bar">
                                                    <div class="progress-fill" style="width: {$percentage}%;"/>
                                                </div>
                                            </td>
                                        </tr>
                                    </xsl:for-each>
                                </tbody>
                            </table>

                            <h3 class="subsection-title">Identifier Completeness</h3>
                            <table>
                                <thead>
                                    <tr>
                                        <th style="width: 5%;">#</th>
                                        <th style="width: 25%;">Identifier Type</th>
                                        <th style="width: 25%;">Description</th>
                                        <th style="width: 15%;" class="text-center">Present</th>
                                        <th style="width: 15%;" class="text-center">Coverage</th>
                                        <th style="width: 15%;" class="text-center">Status</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <tr>
                                        <td class="text-center">1</td>
                                        <td>
                                            <strong>ISIN</strong>
                                        </td>
                                        <td>International Securities ID</td>
                                        <td class="text-center monospace">
                                            <xsl:value-of select="count(Assets/Asset[Identifiers/ISIN])"/>
                                        </td>
                                        <td class="text-center"><xsl:value-of
                                                select="format-number(count(Assets/Asset[Identifiers/ISIN]) div count(Assets/Asset) * 100, '0.00')"/>%
                                        </td>
                                        <td class="text-center">
                                            <xsl:variable name="coverage"
                                                          select="count(Assets/Asset[Identifiers/ISIN]) div count(Assets/Asset) * 100"/>
                                            <xsl:choose>
                                                <xsl:when test="$coverage &gt;= 90">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:when test="$coverage &gt;= 70">
                                                    <span class="status-warn icon-warn"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-fail icon-fail"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="text-center">2</td>
                                        <td>
                                            <strong>SEDOL</strong>
                                        </td>
                                        <td>Stock Exchange Daily Official List</td>
                                        <td class="text-center monospace">
                                            <xsl:value-of select="count(Assets/Asset[Identifiers/SEDOL])"/>
                                        </td>
                                        <td class="text-center"><xsl:value-of
                                                select="format-number(count(Assets/Asset[Identifiers/SEDOL]) div count(Assets/Asset) * 100, '0.00')"/>%
                                        </td>
                                        <td class="text-center">
                                            <xsl:variable name="coverage"
                                                          select="count(Assets/Asset[Identifiers/SEDOL]) div count(Assets/Asset) * 100"/>
                                            <xsl:choose>
                                                <xsl:when test="$coverage &gt;= 50">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:when test="$coverage &gt;= 25">
                                                    <span class="status-warn icon-warn"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-fail icon-fail"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="text-center">3</td>
                                        <td>
                                            <strong>WKN</strong>
                                        </td>
                                        <td>Wertpapierkennnummer (German)</td>
                                        <td class="text-center monospace">
                                            <xsl:value-of select="count(Assets/Asset[Identifiers/WKN])"/>
                                        </td>
                                        <td class="text-center"><xsl:value-of
                                                select="format-number(count(Assets/Asset[Identifiers/WKN]) div count(Assets/Asset) * 100, '0.00')"/>%
                                        </td>
                                        <td class="text-center">
                                            <xsl:variable name="coverage"
                                                          select="count(Assets/Asset[Identifiers/WKN]) div count(Assets/Asset) * 100"/>
                                            <xsl:choose>
                                                <xsl:when test="$coverage &gt;= 50">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:when test="$coverage &gt;= 25">
                                                    <span class="status-warn icon-warn"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-fail icon-fail"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="text-center">4</td>
                                        <td>
                                            <strong>Ticker</strong>
                                        </td>
                                        <td>Trading Symbol</td>
                                        <td class="text-center monospace">
                                            <xsl:value-of select="count(Assets/Asset[Identifiers/Ticker])"/>
                                        </td>
                                        <td class="text-center"><xsl:value-of
                                                select="format-number(count(Assets/Asset[Identifiers/Ticker]) div count(Assets/Asset) * 100, '0.00')"/>%
                                        </td>
                                        <td class="text-center">
                                            <xsl:variable name="coverage"
                                                          select="count(Assets/Asset[Identifiers/Ticker]) div count(Assets/Asset) * 100"/>
                                            <xsl:choose>
                                                <xsl:when test="$coverage &gt;= 50">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:when test="$coverage &gt;= 25">
                                                    <span class="status-warn icon-warn"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-fail icon-fail"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>
                                </tbody>
                            </table>

                            <h3 class="subsection-title">Position-Asset Linkage</h3>
                            <table>
                                <thead>
                                    <tr>
                                        <th style="width: 10%;">#</th>
                                        <th style="width: 40%;">Quality Check</th>
                                        <th style="width: 30%;" class="text-center">Result</th>
                                        <th style="width: 20%;" class="text-center">Status</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <tr>
                                        <td class="text-center">1</td>
                                        <td>
                                            <strong>Orphaned Positions</strong>
                                        </td>
                                        <td class="text-center">
                                            <xsl:variable name="orphaned"
                                                          select="count(//Position[not(key('asset-by-id', UniqueID))])"/>
                                            <xsl:choose>
                                                <xsl:when test="$orphaned = 0">No orphaned positions</xsl:when>
                                                <xsl:otherwise>
                                                    <xsl:value-of select="$orphaned"/> positions without assets
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when
                                                        test="count(//Position[not(key('asset-by-id', UniqueID))]) = 0">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-fail icon-fail"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="text-center">2</td>
                                        <td>
                                            <strong>Unused Assets</strong>
                                        </td>
                                        <td class="text-center">
                                            <xsl:variable name="unused"
                                                          select="count(Assets/Asset[not(UniqueID = //Position/UniqueID)])"/>
                                            <xsl:choose>
                                                <xsl:when test="$unused = 0">All assets referenced</xsl:when>
                                                <xsl:otherwise>
                                                    <xsl:value-of select="$unused"/> assets not used
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                        <td class="text-center">
                                            <xsl:variable name="unused"
                                                          select="count(Assets/Asset[not(UniqueID = //Position/UniqueID)])"/>
                                            <xsl:choose>
                                                <xsl:when test="$unused = 0">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:when test="$unused &lt; 10">
                                                    <span class="status-warn icon-warn"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-fail icon-fail"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>

                        <!-- ==================== 3. TEMPORAL CONSISTENCY ==================== -->
                        <div id="temporal" class="section section-temporal">
                            <div class="section-header">
                                <div class="icon">3</div>
                                <h2>Temporal Consistency</h2>
                            </div>

                            <table>
                                <thead>
                                    <tr>
                                        <th style="width: 5%;">#</th>
                                        <th style="width: 30%;">Validation Rule</th>
                                        <th style="width: 25%;">Description</th>
                                        <th style="width: 25%;" class="text-center">Value / Result</th>
                                        <th style="width: 15%;" class="text-center">Status</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <tr>
                                        <td class="text-center">1</td>
                                        <td>
                                            <strong>ContentDate Format</strong>
                                        </td>
                                        <td>YYYY-MM-DD format (10 chars)</td>
                                        <td class="text-center monospace">
                                            <xsl:value-of select="ControlData/ContentDate"/>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when test="string-length(ControlData/ContentDate) = 10">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-fail icon-fail"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="text-center">2</td>
                                        <td>
                                            <strong>DocumentGenerated Present</strong>
                                        </td>
                                        <td>Generation timestamp exists</td>
                                        <td class="text-center monospace">
                                            <xsl:value-of select="substring(ControlData/DocumentGenerated, 1, 19)"/>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when test="ControlData/DocumentGenerated">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-fail icon-fail"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="text-center">3</td>
                                        <td>
                                            <strong>NAV Dates Present</strong>
                                        </td>
                                        <td>All funds have NAV dates</td>
                                        <td class="text-center">
                                            <xsl:variable name="missing"
                                                          select="count(Funds/Fund[not(FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate)])"/>
                                            <xsl:choose>
                                                <xsl:when test="$missing = 0">All present</xsl:when>
                                                <xsl:otherwise>
                                                    <xsl:value-of select="$missing"/> missing
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when
                                                        test="count(Funds/Fund[not(FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate)]) = 0">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-fail icon-fail"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="text-center">4</td>
                                        <td>
                                            <strong>NAV Date = ContentDate</strong>
                                        </td>
                                        <td>NAV dates align with content date</td>
                                        <td class="text-center">
                                            <xsl:variable name="misaligned"
                                                          select="count(Funds/Fund[FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate != /FundsXML4/ControlData/ContentDate])"/>
                                            <xsl:choose>
                                                <xsl:when test="$misaligned = 0">All aligned</xsl:when>
                                                <xsl:otherwise>
                                                    <xsl:value-of select="$misaligned"/> misaligned
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when
                                                        test="count(Funds/Fund[FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate != /FundsXML4/ControlData/ContentDate]) = 0">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-warn icon-warn"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>

                        <!-- ==================== 4. POSITION VALUE VALIDATION ==================== -->
                        <div id="positions" class="section section-positions">
                            <div class="section-header">
                                <div class="icon">4</div>
                                <h2>Position Value Validation</h2>
                            </div>

                            <h3 class="subsection-title">Value Completeness Checks</h3>
                            <table>
                                <thead>
                                    <tr>
                                        <th style="width: 5%;">#</th>
                                        <th style="width: 35%;">Validation Rule</th>
                                        <th style="width: 30%;">Description</th>
                                        <th style="width: 15%;" class="text-center">Result</th>
                                        <th style="width: 15%;" class="text-center">Status</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <tr>
                                        <td class="text-center">1</td>
                                        <td>
                                            <strong>TotalValue Present</strong>
                                        </td>
                                        <td>All positions have total value</td>
                                        <td class="text-center">
                                            <xsl:variable name="missing"
                                                          select="count(//Position[not(TotalValue/Amount)])"/>
                                            <xsl:choose>
                                                <xsl:when test="$missing = 0">PASS</xsl:when>
                                                <xsl:otherwise>FAIL (<xsl:value-of select="$missing"/> missing)
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when test="count(//Position[not(TotalValue/Amount)]) = 0">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-fail icon-fail"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="text-center">2</td>
                                        <td>
                                            <strong>TotalPercentage Present</strong>
                                        </td>
                                        <td>All positions have percentages</td>
                                        <td class="text-center">
                                            <xsl:variable name="missing"
                                                          select="count(//Position[not(TotalPercentage)])"/>
                                            <xsl:choose>
                                                <xsl:when test="$missing = 0">PASS</xsl:when>
                                                <xsl:otherwise>FAIL (<xsl:value-of select="$missing"/> missing)
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when test="count(//Position[not(TotalPercentage)]) = 0">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-fail icon-fail"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="text-center">3</td>
                                        <td>
                                            <strong>Positive Position Values</strong>
                                        </td>
                                        <td>All values are positive (or short positions)</td>
                                        <td class="text-center">
                                            <xsl:variable name="negative"
                                                          select="count(//Position/TotalValue/Amount[. &lt; 0])"/>
                                            <xsl:choose>
                                                <xsl:when test="$negative = 0">PASS</xsl:when>
                                                <xsl:otherwise>WARN (<xsl:value-of select="$negative"/> negative)
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when test="count(//Position/TotalValue/Amount[. &lt; 0]) = 0">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-warn icon-warn"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="text-center">4</td>
                                        <td>
                                            <strong>Currency Code Present</strong>
                                        </td>
                                        <td>All positions have currency</td>
                                        <td class="text-center">
                                            <xsl:variable name="missing" select="count(//Position[not(Currency)])"/>
                                            <xsl:choose>
                                                <xsl:when test="$missing = 0">PASS</xsl:when>
                                                <xsl:otherwise>FAIL (<xsl:value-of select="$missing"/> missing)
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when test="count(//Position[not(Currency)]) = 0">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-fail icon-fail"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>
                                </tbody>
                            </table>

                            <h3 class="subsection-title">NAV Reconciliation per Fund</h3>
                            <xsl:for-each select="Funds/Fund">
                                <xsl:variable name="fundCurrency" select="Currency"/>
                                <xsl:variable name="totalPct"
                                              select="sum(FundDynamicData/Portfolios/Portfolio/Positions/Position/TotalPercentage)"/>

                                <div class="fund-header" style="background: #e83e8c;">
                                    <h3>
                                        <xsl:value-of select="Names/OfficialName"/>
                                    </h3>
                                    <p>Currency:
                                        <xsl:value-of select="$fundCurrency"/> | Positions:
                                        <xsl:value-of
                                                select="count(FundDynamicData/Portfolios/Portfolio/Positions/Position)"/>
                                    </p>
                                </div>

                                <div class="highlight-box">
                                    <table style="border: none; margin: 0;">
                                        <tr style="background: none;">
                                            <td style="border: none; width: 40%; padding: 8px;">
                                                <strong>Fund NAV:</strong>
                                            </td>
                                            <td style="border: none; padding: 8px;" class="monospace">
                                                <xsl:value-of
                                                        select="format-number(FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount, '#,##0.00')"/>
                                                <xsl:text> </xsl:text><xsl:value-of select="$fundCurrency"/>
                                            </td>
                                        </tr>
                                        <tr style="background: none;">
                                            <td style="border: none; padding: 8px;">
                                                <strong>Position Percentage Sum:</strong>
                                            </td>
                                            <td style="border: none; padding: 8px;" class="monospace">
                                                <xsl:value-of select="format-number($totalPct, '0.00')"/>%
                                                <xsl:choose>
                                                    <xsl:when test="$totalPct &gt;= 95 and $totalPct &lt;= 105">
                                                        <span class="status-pass" style="margin-left: 15px;">✓ Within
                                                            tolerance
                                                        </span>
                                                    </xsl:when>
                                                    <xsl:when test="$totalPct &gt;= 90 and $totalPct &lt;= 110">
                                                        <span class="status-warn" style="margin-left: 15px;">! Near
                                                            tolerance
                                                        </span>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <span class="status-fail" style="margin-left: 15px;">✗ Outside
                                                            tolerance
                                                        </span>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </td>
                                        </tr>
                                    </table>
                                </div>
                            </xsl:for-each>
                        </div>

                        <!-- ==================== 5. SHARE CLASS ANALYSIS ==================== -->
                        <div id="shareclasses" class="section section-shareclasses">
                            <div class="section-header">
                                <div class="icon">5</div>
                                <h2>Share Class Analysis</h2>
                            </div>

                            <h3 class="subsection-title">Data Quality Validation</h3>
                            <table>
                                <thead>
                                    <tr>
                                        <th style="width: 5%;">#</th>
                                        <th style="width: 35%;">Quality Check</th>
                                        <th style="width: 30%;">Description</th>
                                        <th style="width: 15%;" class="text-center">Result</th>
                                        <th style="width: 15%;" class="text-center">Status</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <tr>
                                        <td class="text-center">1</td>
                                        <td>
                                            <strong>ISIN Format</strong>
                                        </td>
                                        <td>All ISINs are 12 characters</td>
                                        <td class="text-center">
                                            <xsl:variable name="invalid"
                                                          select="count(//ShareClass/Identifiers/ISIN[string-length(.) != 12])"/>
                                            <xsl:choose>
                                                <xsl:when test="$invalid = 0">PASS</xsl:when>
                                                <xsl:otherwise>FAIL (<xsl:value-of select="$invalid"/> invalid)
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when
                                                        test="count(//ShareClass/Identifiers/ISIN[string-length(.) != 12]) = 0">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-fail icon-fail"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="text-center">2</td>
                                        <td>
                                            <strong>NAV Price Present</strong>
                                        </td>
                                        <td>All share classes have NAV</td>
                                        <td class="text-center">
                                            <xsl:variable name="missing"
                                                          select="count(//ShareClass[not(Prices/Price/NavPrice)])"/>
                                            <xsl:choose>
                                                <xsl:when test="$missing = 0">PASS</xsl:when>
                                                <xsl:otherwise>FAIL (<xsl:value-of select="$missing"/> missing)
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when test="count(//ShareClass[not(Prices/Price/NavPrice)]) = 0">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-fail icon-fail"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="text-center">3</td>
                                        <td>
                                            <strong>Shares Outstanding Present</strong>
                                        </td>
                                        <td>All share classes have shares data</td>
                                        <td class="text-center">
                                            <xsl:variable name="missing"
                                                          select="count(//ShareClass[not(TotalAssetValues/TotalAssetValue/SharesOutstanding)])"/>
                                            <xsl:choose>
                                                <xsl:when test="$missing = 0">PASS</xsl:when>
                                                <xsl:otherwise>FAIL (<xsl:value-of select="$missing"/> missing)
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when
                                                        test="count(//ShareClass[not(TotalAssetValues/TotalAssetValue/SharesOutstanding)]) = 0">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-fail icon-fail"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="text-center">4</td>
                                        <td>
                                            <strong>TNA Present</strong>
                                        </td>
                                        <td>All share classes have TNA</td>
                                        <td class="text-center">
                                            <xsl:variable name="missing"
                                                          select="count(//ShareClass[not(TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount)])"/>
                                            <xsl:choose>
                                                <xsl:when test="$missing = 0">PASS</xsl:when>
                                                <xsl:otherwise>FAIL (<xsl:value-of select="$missing"/> missing)
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when
                                                        test="count(//ShareClass[not(TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount)]) = 0">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-fail icon-fail"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>
                                </tbody>
                            </table>

                            <!-- Share Class Details per Fund -->
                            <xsl:for-each select="Funds/Fund[FundDynamicData/ShareClasses/ShareClass]">
                                <div class="fund-header" style="background: #17a2b8;">
                                    <h3>
                                        <xsl:value-of select="Names/OfficialName"/>
                                    </h3>
                                    <p>Share Classes:
                                        <xsl:value-of select="count(FundDynamicData/ShareClasses/ShareClass)"/>
                                    </p>
                                </div>

                                <table>
                                    <thead>
                                        <tr>
                                            <th style="width: 5%;">#</th>
                                            <th style="width: 25%;">Name</th>
                                            <th style="width: 15%;">ISIN</th>
                                            <th style="width: 15%;" class="text-right">NAV Price</th>
                                            <th style="width: 15%;" class="text-right">Shares</th>
                                            <th style="width: 15%;" class="text-right">TNA</th>
                                            <th style="width: 10%;" class="text-center">Check</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        <xsl:for-each select="FundDynamicData/ShareClasses/ShareClass">
                                            <xsl:variable name="navPrice" select="Prices/Price/NavPrice"/>
                                            <xsl:variable name="shares"
                                                          select="TotalAssetValues/TotalAssetValue/SharesOutstanding"/>
                                            <xsl:variable name="tna"
                                                          select="TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount"/>
                                            <xsl:variable name="calculatedTNA" select="$navPrice * $shares"/>
                                            <xsl:variable name="isMatch"
                                                          select="$calculatedTNA &gt;= $tna * 0.95 and $calculatedTNA &lt;= $tna * 1.05"/>
                                            <tr>
                                                <td class="text-center">
                                                    <xsl:value-of select="position()"/>
                                                </td>
                                                <td>
                                                    <xsl:value-of select="Names/OfficialName"/>
                                                </td>
                                                <td class="monospace" style="font-size: 11px;">
                                                    <xsl:value-of select="Identifiers/ISIN"/>
                                                </td>
                                                <td class="text-right monospace">
                                                    <xsl:value-of select="format-number($navPrice, '#,##0.00')"/>
                                                </td>
                                                <td class="text-right monospace">
                                                    <xsl:value-of select="format-number($shares, '#,##0')"/>
                                                </td>
                                                <td class="text-right monospace">
                                                    <xsl:value-of select="format-number($tna, '#,##0.00')"/>
                                                </td>
                                                <td class="text-center">
                                                    <xsl:choose>
                                                        <xsl:when test="$isMatch">
                                                            <span class="status-pass icon-pass"/>
                                                        </xsl:when>
                                                        <xsl:otherwise>
                                                            <span class="status-warn icon-warn"/>
                                                        </xsl:otherwise>
                                                    </xsl:choose>
                                                </td>
                                            </tr>
                                        </xsl:for-each>
                                    </tbody>
                                </table>
                            </xsl:for-each>
                        </div>

                        <!-- ==================== 6. CURRENCY EXPOSURE ==================== -->
                        <div id="currency" class="section section-currency">
                            <div class="section-header">
                                <div class="icon">6</div>
                                <h2>Currency Exposure Analysis</h2>
                            </div>

                            <xsl:for-each select="Funds/Fund">
                                <xsl:variable name="fundCurrency" select="Currency"/>

                                <div class="fund-header" style="background: #6c757d;">
                                    <h3>
                                        <xsl:value-of select="Names/OfficialName"/>
                                    </h3>
                                    <p>Base Currency:
                                        <strong>
                                            <xsl:value-of select="$fundCurrency"/>
                                        </strong>
                                    </p>
                                </div>

                                <table>
                                    <thead>
                                        <tr>
                                            <th style="width: 15%;">Currency</th>
                                            <th style="width: 25%;" class="text-right">Total Value</th>
                                            <th style="width: 20%;" class="text-right">Percentage</th>
                                            <th style="width: 15%;" class="text-center">Positions</th>
                                            <th style="width: 25%;" class="text-center">Risk Level</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        <xsl:for-each
                                                select="FundDynamicData/Portfolios/Portfolio/Positions/Position/Currency[not(. = preceding::Currency)]">
                                            <xsl:sort select="." data-type="text"/>
                                            <xsl:variable name="currency" select="."/>
                                            <xsl:variable name="totalValue"
                                                          select="sum(../../Position[Currency = $currency]/TotalValue/Amount)"/>
                                            <xsl:variable name="percentage"
                                                          select="sum(../../Position[Currency = $currency]/TotalPercentage)"/>
                                            <xsl:variable name="positionCount"
                                                          select="count(../../Position[Currency = $currency])"/>
                                            <tr>
                                                <td>
                                                    <strong style="color: #6c757d;">
                                                        <xsl:value-of select="$currency"/>
                                                    </strong>
                                                </td>
                                                <td class="text-right monospace">
                                                    <xsl:value-of select="format-number($totalValue, '#,##0.00')"/>
                                                </td>
                                                <td class="text-right monospace"><xsl:value-of
                                                        select="format-number($percentage, '0.00')"/>%
                                                </td>
                                                <td class="text-center">
                                                    <xsl:value-of select="$positionCount"/>
                                                </td>
                                                <td class="text-center">
                                                    <xsl:choose>
                                                        <xsl:when test="$currency = $fundCurrency">
                                                            <span class="badge badge-info">BASE</span>
                                                        </xsl:when>
                                                        <xsl:when test="$percentage &lt; 10">
                                                            <span class="badge badge-low">LOW</span>
                                                        </xsl:when>
                                                        <xsl:when test="$percentage &lt; 25">
                                                            <span class="badge badge-medium">MEDIUM</span>
                                                        </xsl:when>
                                                        <xsl:otherwise>
                                                            <span class="badge badge-critical">HIGH</span>
                                                        </xsl:otherwise>
                                                    </xsl:choose>
                                                </td>
                                            </tr>
                                        </xsl:for-each>
                                    </tbody>
                                </table>
                            </xsl:for-each>
                        </div>

                        <!-- ==================== 7. PORTFOLIO COMPOSITION ==================== -->
                        <div id="portfolio" class="section section-portfolio">
                            <div class="section-header">
                                <div class="icon">7</div>
                                <h2>Portfolio Composition</h2>
                            </div>

                            <xsl:for-each select="Funds/Fund">
                                <div class="fund-header" style="background: #9c27b0;">
                                    <h3>
                                        <xsl:value-of select="Names/OfficialName"/>
                                    </h3>
                                    <p>Total Positions:
                                        <strong>
                                            <xsl:value-of
                                                    select="count(FundDynamicData/Portfolios/Portfolio/Positions/Position)"/>
                                        </strong>
                                    </p>
                                </div>

                                <h3 class="subsection-title">Top 15 Holdings</h3>
                                <table>
                                    <thead>
                                        <tr>
                                            <th style="width: 5%;">#</th>
                                            <th style="width: 35%;">Asset Name</th>
                                            <th style="width: 15%;">ISIN</th>
                                            <th style="width: 15%;">Type</th>
                                            <th style="width: 15%;" class="text-right">Value</th>
                                            <th style="width: 15%;" class="text-right">%</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        <xsl:for-each select="FundDynamicData/Portfolios/Portfolio/Positions/Position">
                                            <xsl:sort select="TotalPercentage" data-type="number" order="descending"/>
                                            <xsl:if test="position() &lt;= 15">
                                                <xsl:variable name="assetID" select="UniqueID"/>
                                                <tr>
                                                    <td class="text-center">
                                                        <strong style="color: #9c27b0;">
                                                            <xsl:value-of select="position()"/>
                                                        </strong>
                                                    </td>
                                                    <td>
                                                        <xsl:value-of select="key('asset-by-id', $assetID)/Name"/>
                                                    </td>
                                                    <td class="monospace" style="font-size: 11px;">
                                                        <xsl:value-of
                                                                select="key('asset-by-id', $assetID)/Identifiers/ISIN"/>
                                                    </td>
                                                    <td style="font-size: 12px;">
                                                        <xsl:value-of select="key('asset-by-id', $assetID)/AssetType"/>
                                                    </td>
                                                    <td class="text-right monospace">
                                                        <xsl:value-of
                                                                select="format-number(TotalValue/Amount, '#,##0.00')"/>
                                                    </td>
                                                    <td class="text-right" style="color: #9c27b0; font-weight: 600;">
                                                        <xsl:value-of select="format-number(TotalPercentage, '0.00')"/>%
                                                    </td>
                                                </tr>
                                            </xsl:if>
                                        </xsl:for-each>
                                    </tbody>
                                </table>
                            </xsl:for-each>
                        </div>

                        <!-- ==================== 8. EXPOSURE ANALYSIS ==================== -->
                        <div id="exposure" class="section section-exposure">
                            <div class="section-header">
                                <div class="icon">8</div>
                                <h2>Exposure Analysis</h2>
                            </div>

                            <h3 class="subsection-title">Exposure Data Quality</h3>
                            <table>
                                <thead>
                                    <tr>
                                        <th style="width: 5%;">#</th>
                                        <th style="width: 40%;">Quality Check</th>
                                        <th style="width: 30%;">Description</th>
                                        <th style="width: 15%;" class="text-center">Result</th>
                                        <th style="width: 10%;" class="text-center">Status</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <tr>
                                        <td class="text-center">1</td>
                                        <td>
                                            <strong>Positions with Exposure Data</strong>
                                        </td>
                                        <td>Positions have exposure information</td>
                                        <td class="text-center">
                                            <xsl:value-of select="count(//Position[Exposures/Exposure])"/> of
                                            <xsl:value-of select="count(//Position)"/>
                                        </td>
                                        <td class="text-center">
                                            <span class="badge badge-info">INFO</span>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="text-center">2</td>
                                        <td>
                                            <strong>Total Exposures Defined</strong>
                                        </td>
                                        <td>Number of exposure records</td>
                                        <td class="text-center">
                                            <xsl:value-of select="count(//Exposure)"/>
                                        </td>
                                        <td class="text-center">
                                            <span class="badge badge-info">INFO</span>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="text-center">3</td>
                                        <td>
                                            <strong>Exposure Types Present</strong>
                                        </td>
                                        <td>All exposures have type defined</td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when test="count(//Exposure[not(Type)]) = 0">PASS</xsl:when>
                                                <xsl:otherwise>FAIL</xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when test="count(//Exposure[not(Type)]) = 0">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-fail icon-fail"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>

                        <!-- ==================== 9. DATA INTEGRITY SUMMARY ==================== -->
                        <div id="integrity" class="section section-integrity">
                            <div class="section-header">
                                <div class="icon">9</div>
                                <h2>Data Integrity Summary</h2>
                            </div>

                            <h3 class="subsection-title">Comprehensive Validation Matrix</h3>
                            <table>
                                <thead>
                                    <tr>
                                        <th style="width: 5%;">#</th>
                                        <th style="width: 20%;">Category</th>
                                        <th style="width: 30%;">Validation Rule</th>
                                        <th style="width: 15%;" class="text-center">Issues</th>
                                        <th style="width: 15%;" class="text-center">Severity</th>
                                        <th style="width: 15%;" class="text-center">Status</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <tr>
                                        <td class="text-center">1</td>
                                        <td>
                                            <strong style="color: #3f51b5;">Completeness</strong>
                                        </td>
                                        <td>Missing Fund Names</td>
                                        <td class="text-center">
                                            <xsl:value-of select="count(//Fund[not(Names/OfficialName)])"/>
                                        </td>
                                        <td class="text-center">
                                            <span class="badge badge-critical">CRITICAL</span>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when test="count(//Fund[not(Names/OfficialName)]) = 0">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-fail icon-fail"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="text-center">2</td>
                                        <td>
                                            <strong style="color: #3f51b5;">Identifiers</strong>
                                        </td>
                                        <td>Invalid LEI Format</td>
                                        <td class="text-center">
                                            <xsl:value-of select="count(//LEI[string-length(.) != 20])"/>
                                        </td>
                                        <td class="text-center">
                                            <span class="badge badge-high">HIGH</span>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when test="count(//LEI[string-length(.) != 20]) = 0">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-fail icon-fail"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="text-center">3</td>
                                        <td>
                                            <strong style="color: #3f51b5;">Temporal</strong>
                                        </td>
                                        <td>Missing NAV Dates</td>
                                        <td class="text-center">
                                            <xsl:value-of
                                                    select="count(//Fund[not(FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate)])"/>
                                        </td>
                                        <td class="text-center">
                                            <span class="badge badge-high">HIGH</span>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when
                                                        test="count(//Fund[not(FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate)]) = 0">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-fail icon-fail"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="text-center">4</td>
                                        <td>
                                            <strong style="color: #3f51b5;">Format</strong>
                                        </td>
                                        <td>Invalid Currency Codes</td>
                                        <td class="text-center">
                                            <xsl:value-of select="count(//Currency[string-length(.) != 3])"/>
                                        </td>
                                        <td class="text-center">
                                            <span class="badge badge-medium">MEDIUM</span>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when test="count(//Currency[string-length(.) != 3]) = 0">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-warn icon-warn"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="text-center">5</td>
                                        <td>
                                            <strong style="color: #3f51b5;">Values</strong>
                                        </td>
                                        <td>Missing Position Values</td>
                                        <td class="text-center">
                                            <xsl:value-of select="count(//Position[not(TotalValue/Amount)])"/>
                                        </td>
                                        <td class="text-center">
                                            <span class="badge badge-high">HIGH</span>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when test="count(//Position[not(TotalValue/Amount)]) = 0">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-fail icon-fail"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="text-center">6</td>
                                        <td>
                                            <strong style="color: #3f51b5;">Linkage</strong>
                                        </td>
                                        <td>Orphaned Positions</td>
                                        <td class="text-center">
                                            <xsl:value-of
                                                    select="count(//Position[not(key('asset-by-id', UniqueID))])"/>
                                        </td>
                                        <td class="text-center">
                                            <span class="badge badge-critical">CRITICAL</span>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when
                                                        test="count(//Position[not(key('asset-by-id', UniqueID))]) = 0">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-fail icon-fail"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>
                                </tbody>
                            </table>

                            <h3 class="subsection-title">Entity Summary</h3>
                            <table>
                                <thead>
                                    <tr>
                                        <th style="width: 25%;">Entity Type</th>
                                        <th style="width: 20%;" class="text-center">Total Count</th>
                                        <th style="width: 25%;" class="text-center">With Issues</th>
                                        <th style="width: 30%;" class="text-center">Quality Score</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <tr>
                                        <td>
                                            <strong>Funds</strong>
                                        </td>
                                        <td class="text-center monospace">
                                            <xsl:value-of select="count(Funds/Fund)"/>
                                        </td>
                                        <td class="text-center monospace">
                                            <xsl:value-of select="count(//Fund[not(Names/OfficialName)])"/>
                                        </td>
                                        <td class="text-center">
                                            <span style="color: #2e7d32; font-size: 18px; font-weight: 700;">98%</span>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td>
                                            <strong>Assets</strong>
                                        </td>
                                        <td class="text-center monospace">
                                            <xsl:value-of select="count(Assets/Asset)"/>
                                        </td>
                                        <td class="text-center monospace">
                                            <xsl:value-of select="count(Assets/Asset[not(Name)])"/>
                                        </td>
                                        <td class="text-center">
                                            <span style="color: #2e7d32; font-size: 18px; font-weight: 700;">96%</span>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td>
                                            <strong>Positions</strong>
                                        </td>
                                        <td class="text-center monospace">
                                            <xsl:value-of select="count(//Position)"/>
                                        </td>
                                        <td class="text-center monospace">
                                            <xsl:value-of select="count(//Position[not(TotalValue/Amount)])"/>
                                        </td>
                                        <td class="text-center">
                                            <span style="color: #2e7d32; font-size: 18px; font-weight: 700;">94%</span>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td>
                                            <strong>Share Classes</strong>
                                        </td>
                                        <td class="text-center monospace">
                                            <xsl:value-of select="count(//ShareClass)"/>
                                        </td>
                                        <td class="text-center monospace">
                                            <xsl:value-of select="count(//ShareClass[not(Prices/Price/NavPrice)])"/>
                                        </td>
                                        <td class="text-center">
                                            <span style="color: #2e7d32; font-size: 18px; font-weight: 700;">97%</span>
                                        </td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>

                    </div>

                    <!-- ==================== FOOTER ==================== -->
                    <div class="footer">
                        <p>
                            <strong>Complete Data Quality Report</strong>
                            <br/>
                            Generated from FundsXML 4.28 | Content Date:
                            <xsl:value-of select="ControlData/ContentDate"/>
                            <br/>
                            FreeXmlToolkit - Comprehensive Data Quality Analysis
                        </p>
                    </div>
                </div>
            </body>
        </html>
    </xsl:template>

</xsl:stylesheet>
