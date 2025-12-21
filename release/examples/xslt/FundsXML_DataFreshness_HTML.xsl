<?xml version="1.0" encoding="UTF-8"?>
<!--
    FundsXML4 Data Freshness Report
    ===============================
    This XSLT generates an HTML report analyzing temporal data quality,
    detecting stale data and expired securities.

    Layout: Calendar/Timeline view with age badges
    Theme: Brown (#744210) with Amber accents (#f6ad55)

    Checks NOT in XSD (business logic only):
    - ContentDate vs DocumentGenerated gap
    - Matured securities still in portfolio
    - Price staleness (NavDate alignment)
    - Future-dated entries detection
-->
<xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:fn="http://www.w3.org/2005/xpath-functions">

    <xsl:output method="html" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <!-- Store key dates -->
    <xsl:variable name="contentDate" select="/FundsXML4/ControlData/ContentDate"/>
    <xsl:variable name="docGenerated" select="/FundsXML4/ControlData/DocumentGenerated"/>

    <!-- Main Template -->
    <xsl:template match="/FundsXML4">
        <html lang="en">
            <head>
                <meta charset="UTF-8"/>
                <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                <title>FundsXML - Data Freshness Report</title>
                <style>
                    /* Reset and Base Styles */
                    * { margin: 0; padding: 0; box-sizing: border-box; }

                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
                        background: linear-gradient(135deg, #744210 0%, #b45309 50%, #f6ad55 100%);
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
                        background: linear-gradient(135deg, #744210 0%, #b45309 50%, #f6ad55 100%);
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

                    /* Timeline Container */
                    .timeline-container {
                        background: linear-gradient(135deg, #fffbeb 0%, #fef3c7 100%);
                        border: 2px solid #f59e0b;
                        border-radius: 1rem;
                        padding: 2rem;
                        margin-bottom: 2rem;
                    }

                    .timeline-title {
                        font-size: 1.25rem;
                        font-weight: 700;
                        color: #744210;
                        margin-bottom: 1.5rem;
                        text-align: center;
                    }

                    .timeline {
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        position: relative;
                        padding: 1rem 0;
                    }

                    .timeline::before {
                        content: "";
                        position: absolute;
                        left: 100px;
                        right: 100px;
                        top: 50%;
                        height: 4px;
                        background: linear-gradient(90deg, #f59e0b 0%, #92400e 100%);
                        transform: translateY(-50%);
                        border-radius: 2px;
                    }

                    .timeline-point {
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        z-index: 1;
                    }

                    .timeline-dot {
                        width: 24px;
                        height: 24px;
                        border-radius: 50%;
                        background: #f59e0b;
                        border: 4px solid white;
                        box-shadow: 0 2px 8px rgba(0,0,0,0.2);
                    }

                    .timeline-dot.content { background: #059669; }
                    .timeline-dot.today { background: #dc2626; }
                    .timeline-dot.generated { background: #7c3aed; }

                    .timeline-label {
                        margin-top: 0.75rem;
                        font-size: 0.8rem;
                        color: #92400e;
                        font-weight: 600;
                    }

                    .timeline-date {
                        font-size: 0.75rem;
                        color: #b45309;
                    }

                    /* Summary Cards */
                    .summary-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                        gap: 1rem;
                        margin-bottom: 2rem;
                    }

                    .summary-card {
                        background: white;
                        border: 2px solid #e5e7eb;
                        border-radius: 0.75rem;
                        padding: 1.25rem;
                        text-align: center;
                        transition: transform 0.2s, border-color 0.2s;
                    }

                    .summary-card:hover {
                        transform: translateY(-2px);
                        border-color: #f59e0b;
                    }

                    .summary-card.fresh { background: linear-gradient(135deg, #d1fae5 0%, #a7f3d0 100%); border-color: #34d399; }
                    .summary-card.stale { background: linear-gradient(135deg, #fee2e2 0%, #fecaca 100%); border-color: #f87171; }
                    .summary-card.expired { background: linear-gradient(135deg, #fef3c7 0%, #fde68a 100%); border-color: #fbbf24; }

                    .summary-card .number { font-size: 2.25rem; font-weight: 700; }
                    .summary-card.fresh .number { color: #059669; }
                    .summary-card.stale .number { color: #dc2626; }
                    .summary-card.expired .number { color: #d97706; }
                    .summary-card .label { font-size: 0.85rem; font-weight: 600; color: #374151; margin-top: 0.25rem; }

                    /* Section */
                    .section {
                        background: #f9fafb;
                        border: 1px solid #e5e7eb;
                        border-radius: 0.75rem;
                        margin-bottom: 1.5rem;
                        overflow: hidden;
                    }

                    .section-header {
                        background: linear-gradient(135deg, #744210 0%, #b45309 100%);
                        color: white;
                        padding: 1rem 1.5rem;
                        display: flex;
                        align-items: center;
                        gap: 0.75rem;
                    }

                    .section-header.warning { background: linear-gradient(135deg, #d97706 0%, #fbbf24 100%); }
                    .section-header.error { background: linear-gradient(135deg, #dc2626 0%, #f87171 100%); }

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

                    /* Age Badges */
                    .age-badge {
                        display: inline-flex;
                        align-items: center;
                        gap: 0.25rem;
                        padding: 0.25rem 0.75rem;
                        border-radius: 1rem;
                        font-size: 0.75rem;
                        font-weight: 600;
                    }

                    .age-badge.fresh { background: #d1fae5; color: #059669; }
                    .age-badge.recent { background: #dbeafe; color: #2563eb; }
                    .age-badge.old { background: #fef3c7; color: #d97706; }
                    .age-badge.stale { background: #fee2e2; color: #dc2626; }

                    /* Data Table */
                    .data-table {
                        width: 100%;
                        border-collapse: collapse;
                        font-size: 0.875rem;
                    }

                    .data-table th {
                        background: #744210;
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

                    /* Check Row */
                    .check-row {
                        display: flex;
                        align-items: center;
                        gap: 1rem;
                        padding: 1rem;
                        background: white;
                        border-radius: 0.5rem;
                        margin-bottom: 0.75rem;
                        border: 1px solid #e5e7eb;
                    }

                    .check-row.pass { border-left: 4px solid #059669; }
                    .check-row.warn { border-left: 4px solid #d97706; }
                    .check-row.fail { border-left: 4px solid #dc2626; }

                    .check-row .icon { font-size: 1.5rem; }
                    .check-row .info { flex: 1; }
                    .check-row .title { font-weight: 600; color: #1f2937; }
                    .check-row .desc { font-size: 0.85rem; color: #6b7280; }
                    .check-row .value { font-weight: 700; font-size: 0.9rem; }

                    /* Date Card */
                    .date-card {
                        background: white;
                        border: 1px solid #e5e7eb;
                        border-radius: 0.5rem;
                        padding: 1rem;
                        display: flex;
                        align-items: center;
                        gap: 1rem;
                        margin-bottom: 0.5rem;
                    }

                    .date-card .icon { font-size: 2rem; }
                    .date-card .info { flex: 1; }
                    .date-card .label { font-size: 0.85rem; color: #6b7280; }
                    .date-card .value { font-size: 1.1rem; font-weight: 600; color: #1f2937; }
                    .date-card .age { text-align: right; }

                    /* Badge variants */
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
                            <h1>Data Freshness Report</h1>
                            <p>Temporal Data Quality Analysis</p>
                            <div class="subtitle">
                                Content Date: <xsl:value-of select="$contentDate"/> |
                                Generated: <xsl:value-of select="format-dateTime(current-dateTime(), '[Y0001]-[M01]-[D01] [H01]:[m01]:[s01]')"/>
                            </div>
                        </div>

                        <div class="content">
                            <!-- Timeline Visualization -->
                            <xsl:variable name="contentDateParsed" select="xs:date($contentDate)"/>
                            <xsl:variable name="docGenDate" select="xs:date(substring($docGenerated, 1, 10))"/>
                            <xsl:variable name="today" select="current-date()"/>
                            <xsl:variable name="docToContentDays" select="days-from-duration($docGenDate - $contentDateParsed)"/>
                            <xsl:variable name="todayToContentDays" select="days-from-duration($today - $contentDateParsed)"/>

                            <div class="timeline-container">
                                <div class="timeline-title">Document Timeline</div>
                                <div class="timeline">
                                    <div class="timeline-point">
                                        <div class="timeline-dot content"></div>
                                        <div class="timeline-label">Content Date</div>
                                        <div class="timeline-date"><xsl:value-of select="$contentDate"/></div>
                                    </div>
                                    <div class="timeline-point">
                                        <div class="timeline-dot generated"></div>
                                        <div class="timeline-label">Document Generated</div>
                                        <div class="timeline-date"><xsl:value-of select="substring($docGenerated, 1, 10)"/></div>
                                        <div class="timeline-date">+<xsl:value-of select="$docToContentDays"/> days</div>
                                    </div>
                                    <div class="timeline-point">
                                        <div class="timeline-dot today"></div>
                                        <div class="timeline-label">Today</div>
                                        <div class="timeline-date"><xsl:value-of select="$today"/></div>
                                        <div class="timeline-date">+<xsl:value-of select="$todayToContentDays"/> days</div>
                                    </div>
                                </div>
                            </div>

                            <!-- Gather data for analysis -->
                            <xsl:variable name="allBonds" select="AssetMasterData/Asset[AssetType='BO']"/>
                            <xsl:variable name="expiredBonds" select="$allBonds[AssetDetails/Bond/MaturityDate and xs:date(AssetDetails/Bond/MaturityDate) &lt; $contentDateParsed]"/>
                            <xsl:variable name="fundNavDate" select="Funds/Fund/FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate"/>
                            <xsl:variable name="navDateMismatch" select="$fundNavDate != $contentDate"/>

                            <!-- Summary Cards -->
                            <div class="summary-grid">
                                <div class="summary-card {if ($docToContentDays &lt; 30) then 'fresh' else if ($docToContentDays &lt; 90) then '' else 'stale'}">
                                    <div class="number"><xsl:value-of select="$docToContentDays"/></div>
                                    <div class="label">Days: Content to Generated</div>
                                </div>
                                <div class="summary-card {if ($todayToContentDays &lt; 30) then 'fresh' else if ($todayToContentDays &lt; 180) then 'expired' else 'stale'}">
                                    <div class="number"><xsl:value-of select="$todayToContentDays"/></div>
                                    <div class="label">Days Since Content Date</div>
                                </div>
                                <div class="summary-card {if (count($expiredBonds) = 0) then 'fresh' else 'expired'}">
                                    <div class="number"><xsl:value-of select="count($expiredBonds)"/></div>
                                    <div class="label">Matured Securities</div>
                                </div>
                            </div>

                            <!-- Key Dates Section -->
                            <div class="section">
                                <div class="section-header">
                                    <span class="icon">&#128197;</span>
                                    <h2>Key Document Dates</h2>
                                </div>
                                <div class="section-body">
                                    <div class="date-card">
                                        <span class="icon">&#128198;</span>
                                        <div class="info">
                                            <div class="label">Content Date (Data Snapshot)</div>
                                            <div class="value"><xsl:value-of select="$contentDate"/></div>
                                        </div>
                                        <div class="age">
                                            <xsl:choose>
                                                <xsl:when test="$todayToContentDays &lt; 7">
                                                    <span class="age-badge fresh">Fresh</span>
                                                </xsl:when>
                                                <xsl:when test="$todayToContentDays &lt; 30">
                                                    <span class="age-badge recent">Recent</span>
                                                </xsl:when>
                                                <xsl:when test="$todayToContentDays &lt; 90">
                                                    <span class="age-badge old">Aging</span>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="age-badge stale">Stale</span>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </div>
                                    </div>
                                    <div class="date-card">
                                        <span class="icon">&#9881;</span>
                                        <div class="info">
                                            <div class="label">Document Generated (Processing Date)</div>
                                            <div class="value"><xsl:value-of select="$docGenerated"/></div>
                                        </div>
                                        <div class="age">
                                            <xsl:if test="$docToContentDays > 7">
                                                <span class="age-badge old">+<xsl:value-of select="$docToContentDays"/> days after content</span>
                                            </xsl:if>
                                        </div>
                                    </div>
                                    <div class="date-card">
                                        <span class="icon">&#128176;</span>
                                        <div class="info">
                                            <div class="label">Fund NAV Date</div>
                                            <div class="value"><xsl:value-of select="$fundNavDate"/></div>
                                        </div>
                                        <div class="age">
                                            <xsl:choose>
                                                <xsl:when test="$fundNavDate = $contentDate">
                                                    <span class="age-badge fresh">Aligned</span>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="age-badge old">Misaligned</span>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </div>
                                    </div>
                                    <xsl:if test="Funds/Fund/FundStaticData/InceptionDate">
                                        <div class="date-card">
                                            <span class="icon">&#127775;</span>
                                            <div class="info">
                                                <div class="label">Fund Inception Date</div>
                                                <div class="value"><xsl:value-of select="Funds/Fund/FundStaticData/InceptionDate"/></div>
                                            </div>
                                            <div class="age">
                                                <xsl:variable name="inceptionDate" select="xs:date(Funds/Fund/FundStaticData/InceptionDate)"/>
                                                <xsl:variable name="fundAge" select="days-from-duration($contentDateParsed - $inceptionDate) div 365"/>
                                                <span class="age-badge recent"><xsl:value-of select="format-number($fundAge, '0.0')"/> years old</span>
                                            </div>
                                        </div>
                                    </xsl:if>
                                </div>
                            </div>

                            <!-- Freshness Checks -->
                            <div class="section">
                                <div class="section-header">
                                    <span class="icon">&#9989;</span>
                                    <h2>Freshness Validation Checks</h2>
                                </div>
                                <div class="section-body">
                                    <!-- Check 1: ContentDate not in future -->
                                    <div class="check-row {if ($contentDateParsed &lt;= $today) then 'pass' else 'fail'}">
                                        <span class="icon">
                                            <xsl:choose>
                                                <xsl:when test="$contentDateParsed &lt;= $today">&#9989;</xsl:when>
                                                <xsl:otherwise>&#10060;</xsl:otherwise>
                                            </xsl:choose>
                                        </span>
                                        <div class="info">
                                            <div class="title">Content Date Not in Future</div>
                                            <div class="desc">ContentDate should be on or before today</div>
                                        </div>
                                        <div class="value">
                                            <xsl:choose>
                                                <xsl:when test="$contentDateParsed &lt;= $today"><span class="badge-pass">PASS</span></xsl:when>
                                                <xsl:otherwise><span class="badge-fail">FUTURE DATE</span></xsl:otherwise>
                                            </xsl:choose>
                                        </div>
                                    </div>

                                    <!-- Check 2: NAV Date alignment -->
                                    <div class="check-row {if ($fundNavDate = $contentDate) then 'pass' else 'warn'}">
                                        <span class="icon">
                                            <xsl:choose>
                                                <xsl:when test="$fundNavDate = $contentDate">&#9989;</xsl:when>
                                                <xsl:otherwise>&#9888;</xsl:otherwise>
                                            </xsl:choose>
                                        </span>
                                        <div class="info">
                                            <div class="title">NAV Date = Content Date</div>
                                            <div class="desc">Fund NAV date should match content date</div>
                                        </div>
                                        <div class="value">
                                            <xsl:choose>
                                                <xsl:when test="$fundNavDate = $contentDate"><span class="badge-pass">ALIGNED</span></xsl:when>
                                                <xsl:otherwise><span class="badge-warn">MISMATCH</span></xsl:otherwise>
                                            </xsl:choose>
                                        </div>
                                    </div>

                                    <!-- Check 3: Processing delay -->
                                    <div class="check-row {if ($docToContentDays &lt; 7) then 'pass' else if ($docToContentDays &lt; 30) then 'warn' else 'fail'}">
                                        <span class="icon">
                                            <xsl:choose>
                                                <xsl:when test="$docToContentDays &lt; 7">&#9989;</xsl:when>
                                                <xsl:when test="$docToContentDays &lt; 30">&#9888;</xsl:when>
                                                <xsl:otherwise>&#10060;</xsl:otherwise>
                                            </xsl:choose>
                                        </span>
                                        <div class="info">
                                            <div class="title">Processing Delay</div>
                                            <div class="desc">Time between content date and document generation</div>
                                        </div>
                                        <div class="value">
                                            <xsl:choose>
                                                <xsl:when test="$docToContentDays &lt; 7"><span class="badge-pass"><xsl:value-of select="$docToContentDays"/> days</span></xsl:when>
                                                <xsl:when test="$docToContentDays &lt; 30"><span class="badge-warn"><xsl:value-of select="$docToContentDays"/> days</span></xsl:when>
                                                <xsl:otherwise><span class="badge-fail"><xsl:value-of select="$docToContentDays"/> days</span></xsl:otherwise>
                                            </xsl:choose>
                                        </div>
                                    </div>

                                    <!-- Check 4: No expired securities -->
                                    <div class="check-row {if (count($expiredBonds) = 0) then 'pass' else 'fail'}">
                                        <span class="icon">
                                            <xsl:choose>
                                                <xsl:when test="count($expiredBonds) = 0">&#9989;</xsl:when>
                                                <xsl:otherwise>&#10060;</xsl:otherwise>
                                            </xsl:choose>
                                        </span>
                                        <div class="info">
                                            <div class="title">No Matured Securities</div>
                                            <div class="desc">Bonds should not have maturity date before content date</div>
                                        </div>
                                        <div class="value">
                                            <xsl:choose>
                                                <xsl:when test="count($expiredBonds) = 0"><span class="badge-pass">NONE</span></xsl:when>
                                                <xsl:otherwise><span class="badge-fail"><xsl:value-of select="count($expiredBonds)"/> EXPIRED</span></xsl:otherwise>
                                            </xsl:choose>
                                        </div>
                                    </div>

                                    <!-- Check 5: Inception date before content -->
                                    <xsl:if test="Funds/Fund/FundStaticData/InceptionDate">
                                        <xsl:variable name="inceptionDate" select="xs:date(Funds/Fund/FundStaticData/InceptionDate)"/>
                                        <div class="check-row {if ($inceptionDate &lt; $contentDateParsed) then 'pass' else 'fail'}">
                                            <span class="icon">
                                                <xsl:choose>
                                                    <xsl:when test="$inceptionDate &lt; $contentDateParsed">&#9989;</xsl:when>
                                                    <xsl:otherwise>&#10060;</xsl:otherwise>
                                                </xsl:choose>
                                            </span>
                                            <div class="info">
                                                <div class="title">Inception Date &lt; Content Date</div>
                                                <div class="desc">Fund inception should be before content date</div>
                                            </div>
                                            <div class="value">
                                                <xsl:choose>
                                                    <xsl:when test="$inceptionDate &lt; $contentDateParsed"><span class="badge-pass">VALID</span></xsl:when>
                                                    <xsl:otherwise><span class="badge-fail">INVALID</span></xsl:otherwise>
                                                </xsl:choose>
                                            </div>
                                        </div>
                                    </xsl:if>
                                </div>
                            </div>

                            <!-- Matured Securities Table -->
                            <xsl:if test="count($expiredBonds) > 0">
                                <div class="section">
                                    <div class="section-header error">
                                        <span class="icon">&#128683;</span>
                                        <h2>Matured Securities Still in Portfolio</h2>
                                        <span class="badge"><xsl:value-of select="count($expiredBonds)"/> bonds</span>
                                    </div>
                                    <div class="section-body">
                                        <table class="data-table">
                                            <thead>
                                                <tr>
                                                    <th>ISIN</th>
                                                    <th>Name</th>
                                                    <th>Maturity Date</th>
                                                    <th>Days Since Maturity</th>
                                                    <th>Status</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                <xsl:for-each select="$expiredBonds">
                                                    <xsl:sort select="AssetDetails/Bond/MaturityDate"/>
                                                    <xsl:variable name="matDate" select="xs:date(AssetDetails/Bond/MaturityDate)"/>
                                                    <xsl:variable name="daysSince" select="days-from-duration($contentDateParsed - $matDate)"/>
                                                    <tr>
                                                        <td class="mono"><xsl:value-of select="Identifiers/ISIN"/></td>
                                                        <td><xsl:value-of select="substring(Name, 1, 35)"/><xsl:if test="string-length(Name) > 35">...</xsl:if></td>
                                                        <td><xsl:value-of select="AssetDetails/Bond/MaturityDate"/></td>
                                                        <td>
                                                            <xsl:choose>
                                                                <xsl:when test="$daysSince &lt; 30">
                                                                    <span class="age-badge old"><xsl:value-of select="$daysSince"/> days</span>
                                                                </xsl:when>
                                                                <xsl:otherwise>
                                                                    <span class="age-badge stale"><xsl:value-of select="$daysSince"/> days</span>
                                                                </xsl:otherwise>
                                                            </xsl:choose>
                                                        </td>
                                                        <td><span class="badge-fail">EXPIRED</span></td>
                                                    </tr>
                                                </xsl:for-each>
                                            </tbody>
                                        </table>
                                    </div>
                                </div>
                            </xsl:if>

                            <!-- Bond Date Validation -->
                            <div class="section">
                                <div class="section-header">
                                    <span class="icon">&#128197;</span>
                                    <h2>Bond Date Sequence Validation</h2>
                                </div>
                                <div class="section-body">
                                    <xsl:variable name="invalidDateSeq" select="$allBonds[AssetDetails/Bond/IssueDate and AssetDetails/Bond/MaturityDate and xs:date(AssetDetails/Bond/IssueDate) >= xs:date(AssetDetails/Bond/MaturityDate)]"/>
                                    <xsl:variable name="invalidCouponSeq" select="$allBonds[AssetDetails/Bond/IssueDate and AssetDetails/Bond/DateFirstCoupon and xs:date(AssetDetails/Bond/DateFirstCoupon) &lt; xs:date(AssetDetails/Bond/IssueDate)]"/>

                                    <div class="check-row {if (count($invalidDateSeq) = 0) then 'pass' else 'fail'}">
                                        <span class="icon">
                                            <xsl:choose>
                                                <xsl:when test="count($invalidDateSeq) = 0">&#9989;</xsl:when>
                                                <xsl:otherwise>&#10060;</xsl:otherwise>
                                            </xsl:choose>
                                        </span>
                                        <div class="info">
                                            <div class="title">IssueDate &lt; MaturityDate</div>
                                            <div class="desc">Issue date must be before maturity date</div>
                                        </div>
                                        <div class="value">
                                            <xsl:choose>
                                                <xsl:when test="count($invalidDateSeq) = 0"><span class="badge-pass">ALL VALID</span></xsl:when>
                                                <xsl:otherwise><span class="badge-fail"><xsl:value-of select="count($invalidDateSeq)"/> INVALID</span></xsl:otherwise>
                                            </xsl:choose>
                                        </div>
                                    </div>

                                    <div class="check-row {if (count($invalidCouponSeq) = 0) then 'pass' else 'warn'}">
                                        <span class="icon">
                                            <xsl:choose>
                                                <xsl:when test="count($invalidCouponSeq) = 0">&#9989;</xsl:when>
                                                <xsl:otherwise>&#9888;</xsl:otherwise>
                                            </xsl:choose>
                                        </span>
                                        <div class="info">
                                            <div class="title">DateFirstCoupon >= IssueDate</div>
                                            <div class="desc">First coupon should be on or after issue date</div>
                                        </div>
                                        <div class="value">
                                            <xsl:choose>
                                                <xsl:when test="count($invalidCouponSeq) = 0"><span class="badge-pass">ALL VALID</span></xsl:when>
                                                <xsl:otherwise><span class="badge-warn"><xsl:value-of select="count($invalidCouponSeq)"/> INVALID</span></xsl:otherwise>
                                            </xsl:choose>
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
