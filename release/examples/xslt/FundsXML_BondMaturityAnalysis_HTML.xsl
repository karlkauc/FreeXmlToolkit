<?xml version="1.0" encoding="UTF-8"?>
<!--
    FundsXML4 Bond Maturity Analysis Report
    =======================================
    This XSLT generates an HTML report analyzing bond-specific data quality
    with focus on maturity profile, coupon distribution, and expired bonds.

    Layout: Timeline/Ladder visualization with modern cards
    Theme: Navy blue (#1a365d) with Teal accents (#38b2ac)

    Checks NOT in XSD (business logic only):
    - Maturity date buckets (<1Y, 1-3Y, 3-5Y, 5-10Y, 10Y+)
    - Expired bonds (MaturityDate < ContentDate)
    - Coupon rate distribution
    - IssueDate -> MaturityDate sequence
    - Missing bond details
-->
<xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:fn="http://www.w3.org/2005/xpath-functions">

    <xsl:output method="html" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <!-- Store ContentDate for comparisons -->
    <xsl:variable name="contentDate" select="/FundsXML4/ControlData/ContentDate"/>

    <!-- Main Template -->
    <xsl:template match="/FundsXML4">
        <html lang="en">
            <head>
                <meta charset="UTF-8"/>
                <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                <title>FundsXML - Bond Maturity Analysis</title>
                <style>
                    /* Reset and Base Styles */
                    * { margin: 0; padding: 0; box-sizing: border-box; }

                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
                        background: linear-gradient(135deg, #1a365d 0%, #2c5282 50%, #2b6cb0 100%);
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
                        background: linear-gradient(135deg, #1a365d 0%, #2c5282 50%, #38b2ac 100%);
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

                    /* Summary Cards Grid */
                    .summary-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
                        gap: 1rem;
                        margin-bottom: 2rem;
                    }

                    .summary-card {
                        border-radius: 0.75rem;
                        padding: 1.25rem;
                        text-align: center;
                        transition: transform 0.2s, box-shadow 0.2s;
                    }

                    .summary-card:hover {
                        transform: translateY(-2px);
                        box-shadow: 0 10px 25px -5px rgba(0,0,0,0.1);
                    }

                    .summary-card.total { background: linear-gradient(135deg, #ebf8ff 0%, #bee3f8 100%); border: 2px solid #4299e1; }
                    .summary-card.expired { background: linear-gradient(135deg, #fee2e2 0%, #fecaca 100%); border: 2px solid #f87171; }
                    .summary-card.short { background: linear-gradient(135deg, #d1fae5 0%, #a7f3d0 100%); border: 2px solid #34d399; }
                    .summary-card.medium { background: linear-gradient(135deg, #fef3c7 0%, #fde68a 100%); border: 2px solid #fbbf24; }
                    .summary-card.long { background: linear-gradient(135deg, #e9d5ff 0%, #c4b5fd 100%); border: 2px solid #a78bfa; }

                    .summary-card .count { font-size: 2rem; font-weight: 700; }
                    .summary-card.total .count { color: #1e40af; }
                    .summary-card.expired .count { color: #dc2626; }
                    .summary-card.short .count { color: #059669; }
                    .summary-card.medium .count { color: #d97706; }
                    .summary-card.long .count { color: #7c3aed; }

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
                        background: linear-gradient(135deg, #1a365d 0%, #2c5282 100%);
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

                    /* Maturity Ladder */
                    .maturity-ladder { display: flex; flex-direction: column; gap: 0.75rem; }

                    .ladder-row {
                        display: flex;
                        align-items: center;
                        gap: 1rem;
                    }

                    .ladder-label {
                        width: 100px;
                        font-weight: 600;
                        color: #374151;
                        text-align: right;
                        font-size: 0.9rem;
                    }

                    .ladder-bar-container {
                        flex: 1;
                        background: #e5e7eb;
                        border-radius: 0.5rem;
                        height: 32px;
                        overflow: hidden;
                        position: relative;
                    }

                    .ladder-bar {
                        height: 100%;
                        border-radius: 0.5rem;
                        display: flex;
                        align-items: center;
                        padding-left: 0.75rem;
                        color: white;
                        font-weight: 600;
                        font-size: 0.85rem;
                        transition: width 0.5s ease;
                    }

                    .ladder-bar.expired { background: linear-gradient(90deg, #dc2626 0%, #f87171 100%); }
                    .ladder-bar.bucket-1y { background: linear-gradient(90deg, #059669 0%, #34d399 100%); }
                    .ladder-bar.bucket-3y { background: linear-gradient(90deg, #0891b2 0%, #22d3ee 100%); }
                    .ladder-bar.bucket-5y { background: linear-gradient(90deg, #7c3aed 0%, #a78bfa 100%); }
                    .ladder-bar.bucket-10y { background: linear-gradient(90deg, #c026d3 0%, #e879f9 100%); }
                    .ladder-bar.bucket-long { background: linear-gradient(90deg, #ea580c 0%, #fb923c 100%); }

                    .ladder-count {
                        width: 80px;
                        text-align: left;
                        font-size: 0.9rem;
                        color: #6b7280;
                    }

                    /* Coupon Distribution */
                    .coupon-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
                        gap: 1rem;
                    }

                    .coupon-card {
                        background: white;
                        border: 1px solid #e5e7eb;
                        border-radius: 0.5rem;
                        padding: 1rem;
                        text-align: center;
                    }

                    .coupon-card .rate { font-size: 1.5rem; font-weight: 700; color: #1a365d; }
                    .coupon-card .count { font-size: 0.9rem; color: #6b7280; }

                    /* Data Table */
                    .data-table {
                        width: 100%;
                        border-collapse: collapse;
                        font-size: 0.875rem;
                    }

                    .data-table th {
                        background: #1a365d;
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
                    .data-table tr:hover { background: #f3f4f6; }

                    /* Status Badges */
                    .badge-expired {
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

                    .badge-ok {
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
                        margin-bottom: 1rem;
                        display: flex;
                        align-items: flex-start;
                        gap: 0.75rem;
                    }

                    .alert.error {
                        background: #fee2e2;
                        border: 1px solid #fecaca;
                        color: #991b1b;
                    }

                    .alert.warning {
                        background: #fef3c7;
                        border: 1px solid #fde68a;
                        color: #92400e;
                    }

                    .alert.info {
                        background: #dbeafe;
                        border: 1px solid #bfdbfe;
                        color: #1e40af;
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

                    /* Monospace for identifiers */
                    .mono { font-family: 'SF Mono', Consolas, Monaco, monospace; font-size: 0.85rem; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="main-card">
                        <!-- Header -->
                        <div class="header">
                            <h1>Bond Maturity Analysis</h1>
                            <p>Data Quality Report for FundsXML Bond Holdings</p>
                            <div class="subtitle">
                                Content Date: <xsl:value-of select="$contentDate"/> |
                                Generated: <xsl:value-of select="format-dateTime(current-dateTime(), '[Y0001]-[M01]-[D01] [H01]:[m01]:[s01]')"/>
                            </div>
                        </div>

                        <div class="content">
                            <!-- Variables for bond analysis -->
                            <xsl:variable name="allBonds" select="AssetMasterData/Asset[AssetType='BO']"/>
                            <xsl:variable name="totalBonds" select="count($allBonds)"/>

                            <!-- Count expired bonds -->
                            <xsl:variable name="expiredBonds" select="$allBonds[AssetDetails/Bond/MaturityDate and xs:date(AssetDetails/Bond/MaturityDate) &lt; xs:date($contentDate)]"/>
                            <xsl:variable name="expiredCount" select="count($expiredBonds)"/>

                            <!-- Count bonds by maturity bucket -->
                            <xsl:variable name="activeBonds" select="$allBonds[AssetDetails/Bond/MaturityDate and xs:date(AssetDetails/Bond/MaturityDate) >= xs:date($contentDate)]"/>
                            <xsl:variable name="bucket1Y" select="$activeBonds[xs:date(AssetDetails/Bond/MaturityDate) &lt; xs:date($contentDate) + xs:dayTimeDuration('P365D')]"/>
                            <xsl:variable name="bucket3Y" select="$activeBonds[xs:date(AssetDetails/Bond/MaturityDate) >= xs:date($contentDate) + xs:dayTimeDuration('P365D') and xs:date(AssetDetails/Bond/MaturityDate) &lt; xs:date($contentDate) + xs:dayTimeDuration('P1095D')]"/>
                            <xsl:variable name="bucket5Y" select="$activeBonds[xs:date(AssetDetails/Bond/MaturityDate) >= xs:date($contentDate) + xs:dayTimeDuration('P1095D') and xs:date(AssetDetails/Bond/MaturityDate) &lt; xs:date($contentDate) + xs:dayTimeDuration('P1825D')]"/>
                            <xsl:variable name="bucket10Y" select="$activeBonds[xs:date(AssetDetails/Bond/MaturityDate) >= xs:date($contentDate) + xs:dayTimeDuration('P1825D') and xs:date(AssetDetails/Bond/MaturityDate) &lt; xs:date($contentDate) + xs:dayTimeDuration('P3650D')]"/>
                            <xsl:variable name="bucketLong" select="$activeBonds[xs:date(AssetDetails/Bond/MaturityDate) >= xs:date($contentDate) + xs:dayTimeDuration('P3650D')]"/>
                            <xsl:variable name="noMaturity" select="$allBonds[not(AssetDetails/Bond/MaturityDate)]"/>

                            <!-- Summary Cards -->
                            <div class="summary-grid">
                                <div class="summary-card total">
                                    <div class="count"><xsl:value-of select="$totalBonds"/></div>
                                    <div class="label">Total Bonds</div>
                                </div>
                                <div class="summary-card expired">
                                    <div class="count"><xsl:value-of select="$expiredCount"/></div>
                                    <div class="label">Expired</div>
                                </div>
                                <div class="summary-card short">
                                    <div class="count"><xsl:value-of select="count($bucket1Y)"/></div>
                                    <div class="label">&lt; 1 Year</div>
                                </div>
                                <div class="summary-card medium">
                                    <div class="count"><xsl:value-of select="count($bucket3Y)"/></div>
                                    <div class="label">1-3 Years</div>
                                </div>
                                <div class="summary-card long">
                                    <div class="count"><xsl:value-of select="count($bucket5Y) + count($bucket10Y) + count($bucketLong)"/></div>
                                    <div class="label">3+ Years</div>
                                </div>
                            </div>

                            <!-- Alert for expired bonds -->
                            <xsl:if test="$expiredCount > 0">
                                <div class="alert error">
                                    <span class="icon">&#9888;</span>
                                    <div>
                                        <strong>Data Quality Issue:</strong>
                                        <xsl:value-of select="$expiredCount"/> bond(s) have maturity dates before the ContentDate (<xsl:value-of select="$contentDate"/>).
                                        These securities may need to be removed from the portfolio.
                                    </div>
                                </div>
                            </xsl:if>

                            <!-- Alert for bonds without maturity date -->
                            <xsl:if test="count($noMaturity) > 0">
                                <div class="alert warning">
                                    <span class="icon">&#9888;</span>
                                    <div>
                                        <strong>Missing Data:</strong>
                                        <xsl:value-of select="count($noMaturity)"/> bond(s) are missing maturity date information.
                                    </div>
                                </div>
                            </xsl:if>

                            <!-- Maturity Ladder Section -->
                            <div class="section">
                                <div class="section-header">
                                    <span class="icon">&#128202;</span>
                                    <h2>Maturity Ladder</h2>
                                    <span class="badge"><xsl:value-of select="count($activeBonds)"/> Active Bonds</span>
                                </div>
                                <div class="section-body">
                                    <div class="maturity-ladder">
                                        <xsl:variable name="maxCount" select="max((count($bucket1Y), count($bucket3Y), count($bucket5Y), count($bucket10Y), count($bucketLong), 1))"/>

                                        <xsl:if test="$expiredCount > 0">
                                            <div class="ladder-row">
                                                <div class="ladder-label">Expired</div>
                                                <div class="ladder-bar-container">
                                                    <div class="ladder-bar expired" style="width: {round($expiredCount div $maxCount * 100)}%">
                                                        <xsl:value-of select="$expiredCount"/>
                                                    </div>
                                                </div>
                                                <div class="ladder-count">(<xsl:value-of select="if ($totalBonds > 0) then format-number($expiredCount div $totalBonds * 100, '0.0') else '0.0'"/>%)</div>
                                            </div>
                                        </xsl:if>

                                        <div class="ladder-row">
                                            <div class="ladder-label">&lt; 1 Year</div>
                                            <div class="ladder-bar-container">
                                                <div class="ladder-bar bucket-1y" style="width: {round(count($bucket1Y) div $maxCount * 100)}%">
                                                    <xsl:value-of select="count($bucket1Y)"/>
                                                </div>
                                            </div>
                                            <div class="ladder-count">(<xsl:value-of select="if ($totalBonds > 0) then format-number(count($bucket1Y) div $totalBonds * 100, '0.0') else '0.0'"/>%)</div>
                                        </div>

                                        <div class="ladder-row">
                                            <div class="ladder-label">1-3 Years</div>
                                            <div class="ladder-bar-container">
                                                <div class="ladder-bar bucket-3y" style="width: {round(count($bucket3Y) div $maxCount * 100)}%">
                                                    <xsl:value-of select="count($bucket3Y)"/>
                                                </div>
                                            </div>
                                            <div class="ladder-count">(<xsl:value-of select="if ($totalBonds > 0) then format-number(count($bucket3Y) div $totalBonds * 100, '0.0') else '0.0'"/>%)</div>
                                        </div>

                                        <div class="ladder-row">
                                            <div class="ladder-label">3-5 Years</div>
                                            <div class="ladder-bar-container">
                                                <div class="ladder-bar bucket-5y" style="width: {round(count($bucket5Y) div $maxCount * 100)}%">
                                                    <xsl:value-of select="count($bucket5Y)"/>
                                                </div>
                                            </div>
                                            <div class="ladder-count">(<xsl:value-of select="if ($totalBonds > 0) then format-number(count($bucket5Y) div $totalBonds * 100, '0.0') else '0.0'"/>%)</div>
                                        </div>

                                        <div class="ladder-row">
                                            <div class="ladder-label">5-10 Years</div>
                                            <div class="ladder-bar-container">
                                                <div class="ladder-bar bucket-10y" style="width: {round(count($bucket10Y) div $maxCount * 100)}%">
                                                    <xsl:value-of select="count($bucket10Y)"/>
                                                </div>
                                            </div>
                                            <div class="ladder-count">(<xsl:value-of select="if ($totalBonds > 0) then format-number(count($bucket10Y) div $totalBonds * 100, '0.0') else '0.0'"/>%)</div>
                                        </div>

                                        <div class="ladder-row">
                                            <div class="ladder-label">10+ Years</div>
                                            <div class="ladder-bar-container">
                                                <div class="ladder-bar bucket-long" style="width: {round(count($bucketLong) div $maxCount * 100)}%">
                                                    <xsl:value-of select="count($bucketLong)"/>
                                                </div>
                                            </div>
                                            <div class="ladder-count">(<xsl:value-of select="if ($totalBonds > 0) then format-number(count($bucketLong) div $totalBonds * 100, '0.0') else '0.0'"/>%)</div>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <!-- Coupon Rate Distribution -->
                            <div class="section">
                                <div class="section-header">
                                    <span class="icon">&#128176;</span>
                                    <h2>Coupon Rate Distribution</h2>
                                </div>
                                <div class="section-body">
                                    <xsl:variable name="zeroCoupon" select="$allBonds[AssetDetails/Bond/InterestRate = 0 or not(AssetDetails/Bond/InterestRate)]"/>
                                    <xsl:variable name="lowCoupon" select="$allBonds[AssetDetails/Bond/InterestRate > 0 and AssetDetails/Bond/InterestRate &lt; 1]"/>
                                    <xsl:variable name="medCoupon" select="$allBonds[AssetDetails/Bond/InterestRate >= 1 and AssetDetails/Bond/InterestRate &lt; 3]"/>
                                    <xsl:variable name="highCoupon" select="$allBonds[AssetDetails/Bond/InterestRate >= 3 and AssetDetails/Bond/InterestRate &lt; 5]"/>
                                    <xsl:variable name="veryHighCoupon" select="$allBonds[AssetDetails/Bond/InterestRate >= 5]"/>

                                    <div class="coupon-grid">
                                        <div class="coupon-card">
                                            <div class="rate">0%</div>
                                            <div class="count"><xsl:value-of select="count($zeroCoupon)"/> bonds</div>
                                        </div>
                                        <div class="coupon-card">
                                            <div class="rate">0-1%</div>
                                            <div class="count"><xsl:value-of select="count($lowCoupon)"/> bonds</div>
                                        </div>
                                        <div class="coupon-card">
                                            <div class="rate">1-3%</div>
                                            <div class="count"><xsl:value-of select="count($medCoupon)"/> bonds</div>
                                        </div>
                                        <div class="coupon-card">
                                            <div class="rate">3-5%</div>
                                            <div class="count"><xsl:value-of select="count($highCoupon)"/> bonds</div>
                                        </div>
                                        <div class="coupon-card">
                                            <div class="rate">5%+</div>
                                            <div class="count"><xsl:value-of select="count($veryHighCoupon)"/> bonds</div>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <!-- Data Quality Checks -->
                            <div class="section">
                                <div class="section-header">
                                    <span class="icon">&#9989;</span>
                                    <h2>Bond Data Quality Checks</h2>
                                </div>
                                <div class="section-body">
                                    <table class="data-table">
                                        <thead>
                                            <tr>
                                                <th>Check</th>
                                                <th>Description</th>
                                                <th>Count</th>
                                                <th>Status</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            <!-- Missing MaturityDate -->
                                            <tr>
                                                <td>Missing Maturity Date</td>
                                                <td>Bonds without MaturityDate element</td>
                                                <td><xsl:value-of select="count($noMaturity)"/></td>
                                                <td>
                                                    <xsl:choose>
                                                        <xsl:when test="count($noMaturity) = 0"><span class="badge-ok">PASS</span></xsl:when>
                                                        <xsl:otherwise><span class="badge-warning">WARN</span></xsl:otherwise>
                                                    </xsl:choose>
                                                </td>
                                            </tr>
                                            <!-- Missing IssueDate -->
                                            <xsl:variable name="missingIssueDate" select="$allBonds[not(AssetDetails/Bond/IssueDate)]"/>
                                            <tr>
                                                <td>Missing Issue Date</td>
                                                <td>Bonds without IssueDate element</td>
                                                <td><xsl:value-of select="count($missingIssueDate)"/></td>
                                                <td>
                                                    <xsl:choose>
                                                        <xsl:when test="count($missingIssueDate) = 0"><span class="badge-ok">PASS</span></xsl:when>
                                                        <xsl:otherwise><span class="badge-warning">WARN</span></xsl:otherwise>
                                                    </xsl:choose>
                                                </td>
                                            </tr>
                                            <!-- Missing InterestRate -->
                                            <xsl:variable name="missingInterestRate" select="$allBonds[not(AssetDetails/Bond/InterestRate)]"/>
                                            <tr>
                                                <td>Missing Interest Rate</td>
                                                <td>Bonds without InterestRate element</td>
                                                <td><xsl:value-of select="count($missingInterestRate)"/></td>
                                                <td>
                                                    <xsl:choose>
                                                        <xsl:when test="count($missingInterestRate) = 0"><span class="badge-ok">PASS</span></xsl:when>
                                                        <xsl:otherwise><span class="badge-warning">WARN</span></xsl:otherwise>
                                                    </xsl:choose>
                                                </td>
                                            </tr>
                                            <!-- Missing Issuer LEI -->
                                            <xsl:variable name="missingIssuerLEI" select="$allBonds[not(AssetDetails/Bond/Issuer/Identifiers/LEI)]"/>
                                            <tr>
                                                <td>Missing Issuer LEI</td>
                                                <td>Bonds without issuer LEI identifier</td>
                                                <td><xsl:value-of select="count($missingIssuerLEI)"/></td>
                                                <td>
                                                    <xsl:choose>
                                                        <xsl:when test="count($missingIssuerLEI) = 0"><span class="badge-ok">PASS</span></xsl:when>
                                                        <xsl:otherwise><span class="badge-warning">WARN</span></xsl:otherwise>
                                                    </xsl:choose>
                                                </td>
                                            </tr>
                                            <!-- Expired Bonds -->
                                            <tr>
                                                <td>Expired Bonds</td>
                                                <td>Bonds with MaturityDate before ContentDate</td>
                                                <td><xsl:value-of select="$expiredCount"/></td>
                                                <td>
                                                    <xsl:choose>
                                                        <xsl:when test="$expiredCount = 0"><span class="badge-ok">PASS</span></xsl:when>
                                                        <xsl:otherwise><span class="badge-expired">FAIL</span></xsl:otherwise>
                                                    </xsl:choose>
                                                </td>
                                            </tr>
                                            <!-- Invalid Date Sequence -->
                                            <xsl:variable name="invalidDateSeq" select="$allBonds[AssetDetails/Bond/IssueDate and AssetDetails/Bond/MaturityDate and xs:date(AssetDetails/Bond/IssueDate) >= xs:date(AssetDetails/Bond/MaturityDate)]"/>
                                            <tr>
                                                <td>Invalid Date Sequence</td>
                                                <td>Bonds where IssueDate >= MaturityDate</td>
                                                <td><xsl:value-of select="count($invalidDateSeq)"/></td>
                                                <td>
                                                    <xsl:choose>
                                                        <xsl:when test="count($invalidDateSeq) = 0"><span class="badge-ok">PASS</span></xsl:when>
                                                        <xsl:otherwise><span class="badge-expired">FAIL</span></xsl:otherwise>
                                                    </xsl:choose>
                                                </td>
                                            </tr>
                                        </tbody>
                                    </table>
                                </div>
                            </div>

                            <!-- Expired Bonds Detail Table -->
                            <xsl:if test="$expiredCount > 0">
                                <div class="section">
                                    <div class="section-header" style="background: linear-gradient(135deg, #dc2626 0%, #f87171 100%);">
                                        <span class="icon">&#128683;</span>
                                        <h2>Expired Bonds Detail</h2>
                                        <span class="badge"><xsl:value-of select="$expiredCount"/> bonds</span>
                                    </div>
                                    <div class="section-body">
                                        <table class="data-table">
                                            <thead>
                                                <tr>
                                                    <th>ISIN</th>
                                                    <th>Name</th>
                                                    <th>Maturity Date</th>
                                                    <th>Days Expired</th>
                                                    <th>Issuer</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                <xsl:for-each select="$expiredBonds">
                                                    <xsl:sort select="AssetDetails/Bond/MaturityDate"/>
                                                    <tr>
                                                        <td class="mono"><xsl:value-of select="Identifiers/ISIN"/></td>
                                                        <td><xsl:value-of select="Name"/></td>
                                                        <td><xsl:value-of select="AssetDetails/Bond/MaturityDate"/></td>
                                                        <td>
                                                            <xsl:variable name="daysExpired" select="days-from-duration(xs:date($contentDate) - xs:date(AssetDetails/Bond/MaturityDate))"/>
                                                            <span class="badge-expired"><xsl:value-of select="$daysExpired"/> days</span>
                                                        </td>
                                                        <td><xsl:value-of select="AssetDetails/Bond/Issuer/Name"/></td>
                                                    </tr>
                                                </xsl:for-each>
                                            </tbody>
                                        </table>
                                    </div>
                                </div>
                            </xsl:if>

                            <!-- Soon Maturing Bonds (next 90 days) -->
                            <xsl:variable name="soonMaturing" select="$activeBonds[xs:date(AssetDetails/Bond/MaturityDate) &lt; xs:date($contentDate) + xs:dayTimeDuration('P90D')]"/>
                            <xsl:if test="count($soonMaturing) > 0">
                                <div class="section">
                                    <div class="section-header" style="background: linear-gradient(135deg, #d97706 0%, #fbbf24 100%);">
                                        <span class="icon">&#9200;</span>
                                        <h2>Soon Maturing (within 90 days)</h2>
                                        <span class="badge"><xsl:value-of select="count($soonMaturing)"/> bonds</span>
                                    </div>
                                    <div class="section-body">
                                        <table class="data-table">
                                            <thead>
                                                <tr>
                                                    <th>ISIN</th>
                                                    <th>Name</th>
                                                    <th>Maturity Date</th>
                                                    <th>Days Until</th>
                                                    <th>Coupon</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                <xsl:for-each select="$soonMaturing">
                                                    <xsl:sort select="AssetDetails/Bond/MaturityDate"/>
                                                    <tr>
                                                        <td class="mono"><xsl:value-of select="Identifiers/ISIN"/></td>
                                                        <td><xsl:value-of select="Name"/></td>
                                                        <td><xsl:value-of select="AssetDetails/Bond/MaturityDate"/></td>
                                                        <td>
                                                            <xsl:variable name="daysUntil" select="days-from-duration(xs:date(AssetDetails/Bond/MaturityDate) - xs:date($contentDate))"/>
                                                            <span class="badge-warning"><xsl:value-of select="$daysUntil"/> days</span>
                                                        </td>
                                                        <td><xsl:value-of select="format-number(AssetDetails/Bond/InterestRate, '0.00')"/>%</td>
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
