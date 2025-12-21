<?xml version="1.0" encoding="UTF-8"?>
<!--
    FundsXML4 Data Quality Validation Rules - XSLT
    ===============================================
    This XSLT file implements comprehensive data quality checks
    for FundsXML4 documents, validating structural integrity, calculations,
    and business rules compliance.

    Equivalent to: eam-rules.sch (Schematron)

    Patterns:
    1. Structural Checks
    2. NAV Calculations
    3. Portfolio Validations
    4. Asset-Specific Validations
    5. Date Consistency
    6. Identifier Validations
    7. Currency Validations
-->
<xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:fn="http://www.w3.org/2005/xpath-functions">

    <xsl:output method="html" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <!-- Main Template -->
    <xsl:template match="/FundsXML4">
        <html lang="en">
            <head>
                <meta charset="UTF-8"/>
                <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                <title>FundsXML - EAM Rules Validation Report</title>
                <style>
                    /* Reset and Base Styles */
                    * { margin: 0; padding: 0; box-sizing: border-box; }

                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        min-height: 100vh;
                        line-height: 1.6;
                        padding: 2rem;
                    }

                    .container {
                        max-width: 1400px;
                        margin: 0 auto;
                    }

                    /* Main Card */
                    .main-card {
                        background: white;
                        border-radius: 1rem;
                        box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
                        overflow: hidden;
                    }

                    /* Header */
                    .header {
                        background: linear-gradient(135deg, #1e3a8a 0%, #3730a3 50%, #6d28d9 100%);
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

                    .header p {
                        color: #c7d2fe;
                        font-size: 1.1rem;
                    }

                    .header .subtitle {
                        margin-top: 0.5rem;
                        font-size: 0.9rem;
                        color: #a5b4fc;
                    }

                    /* Content */
                    .content { padding: 2rem; }

                    /* Summary Cards */
                    .summary-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                        gap: 1.5rem;
                        margin-bottom: 2rem;
                    }

                    .summary-card {
                        border-radius: 0.75rem;
                        padding: 1.5rem;
                        text-align: center;
                        transition: transform 0.2s, box-shadow 0.2s;
                    }

                    .summary-card:hover {
                        transform: translateY(-2px);
                        box-shadow: 0 10px 25px -5px rgba(0,0,0,0.1);
                    }

                    .summary-card.errors { background: linear-gradient(135deg, #fee2e2 0%, #fecaca 100%); border: 2px solid #f87171; }
                    .summary-card.warnings { background: linear-gradient(135deg, #fef3c7 0%, #fde68a 100%); border: 2px solid #fbbf24; }
                    .summary-card.info { background: linear-gradient(135deg, #dbeafe 0%, #bfdbfe 100%); border: 2px solid #60a5fa; }
                    .summary-card.success { background: linear-gradient(135deg, #d1fae5 0%, #a7f3d0 100%); border: 2px solid #34d399; }

                    .summary-card .count { font-size: 2.5rem; font-weight: 700; }
                    .summary-card.errors .count { color: #dc2626; }
                    .summary-card.warnings .count { color: #d97706; }
                    .summary-card.info .count { color: #2563eb; }
                    .summary-card.success .count { color: #059669; }

                    .summary-card .label { font-size: 0.9rem; font-weight: 600; color: #374151; margin-top: 0.25rem; }

                    /* Validation Sections */
                    .validation-section {
                        background: #f9fafb;
                        border: 1px solid #e5e7eb;
                        border-radius: 0.75rem;
                        margin-bottom: 1.5rem;
                        overflow: hidden;
                    }

                    .section-header {
                        background: linear-gradient(135deg, #1f2937 0%, #374151 100%);
                        color: white;
                        padding: 1rem 1.5rem;
                        display: flex;
                        align-items: center;
                        gap: 0.75rem;
                    }

                    .section-header .icon { font-size: 1.5rem; }
                    .section-header h2 { font-size: 1.25rem; font-weight: 600; }
                    .section-header .pattern-id {
                        margin-left: auto;
                        background: rgba(255,255,255,0.2);
                        padding: 0.25rem 0.75rem;
                        border-radius: 1rem;
                        font-size: 0.8rem;
                    }

                    .section-body { padding: 1.5rem; }

                    /* Tables */
                    table { width: 100%; border-collapse: collapse; }

                    thead th {
                        background: #f3f4f6;
                        padding: 0.75rem 1rem;
                        text-align: left;
                        font-weight: 600;
                        color: #374151;
                        border-bottom: 2px solid #d1d5db;
                        font-size: 0.85rem;
                        text-transform: uppercase;
                        letter-spacing: 0.025em;
                    }

                    tbody td {
                        padding: 0.75rem 1rem;
                        border-bottom: 1px solid #e5e7eb;
                        color: #4b5563;
                    }

                    tbody tr:hover { background: #f9fafb; }
                    tbody tr:last-child td { border-bottom: none; }

                    /* Status Badges */
                    .badge {
                        display: inline-flex;
                        align-items: center;
                        gap: 0.25rem;
                        padding: 0.25rem 0.75rem;
                        border-radius: 9999px;
                        font-size: 0.8rem;
                        font-weight: 600;
                    }

                    .badge-error { background: #fee2e2; color: #dc2626; }
                    .badge-warning { background: #fef3c7; color: #d97706; }
                    .badge-info { background: #dbeafe; color: #2563eb; }
                    .badge-success { background: #d1fae5; color: #059669; }

                    .badge::before { font-weight: 700; margin-right: 0.25rem; }
                    .badge-error::before { content: '✗'; }
                    .badge-warning::before { content: '!'; }
                    .badge-info::before { content: 'ℹ'; }
                    .badge-success::before { content: '✓'; }

                    /* Context */
                    .context { font-family: 'Courier New', monospace; font-size: 0.85rem; color: #6b7280; }
                    .message { font-size: 0.95rem; }

                    /* Fund Header */
                    .fund-header {
                        background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
                        color: white;
                        padding: 1rem 1.5rem;
                        border-radius: 0.5rem;
                        margin-bottom: 1rem;
                    }

                    .fund-header h3 { font-size: 1.1rem; }
                    .fund-header .meta { font-size: 0.85rem; opacity: 0.8; margin-top: 0.25rem; }

                    /* No Issues */
                    .no-issues {
                        text-align: center;
                        padding: 2rem;
                        color: #059669;
                        font-size: 1.1rem;
                    }

                    .no-issues::before {
                        content: '✓';
                        display: block;
                        font-size: 3rem;
                        margin-bottom: 0.5rem;
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

                    .footer a { color: #4f46e5; text-decoration: none; }
                    .footer a:hover { text-decoration: underline; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="main-card">
                        <div class="header">
                            <h1>EAM Rules Validation Report</h1>
                            <p>FundsXML4 Data Quality Validation</p>
                            <div class="subtitle">
                                Generated: <xsl:value-of select="format-dateTime(current-dateTime(), '[Y0001]-[M01]-[D01] [H01]:[m01]:[s01]')"/>
                                | Content Date: <xsl:value-of select="ControlData/ContentDate"/>
                            </div>
                        </div>

                        <div class="content">
                            <!-- Calculate Totals -->
                            <xsl:variable name="errorCount" select="count(.//validation-error[@level='error'])"/>
                            <xsl:variable name="warningCount" select="count(.//validation-error[@level='warning'])"/>

                            <!-- Summary Cards -->
                            <div class="summary-grid">
                                <div class="summary-card info">
                                    <div class="count"><xsl:value-of select="count(Funds/Fund)"/></div>
                                    <div class="label">Funds Analyzed</div>
                                </div>
                                <div class="summary-card info">
                                    <div class="count"><xsl:value-of select="count(Assets/Asset)"/></div>
                                    <div class="label">Assets</div>
                                </div>
                                <div class="summary-card info">
                                    <div class="count"><xsl:value-of select="count(//Position)"/></div>
                                    <div class="label">Positions</div>
                                </div>
                                <div class="summary-card success">
                                    <div class="count">7</div>
                                    <div class="label">Validation Patterns</div>
                                </div>
                            </div>

                            <!-- ============================================
                                 PATTERN 1: STRUCTURAL CHECKS
                                 ============================================ -->
                            <div class="validation-section">
                                <div class="section-header">
                                    <span class="icon">[1]</span>
                                    <h2>Structural Integrity Checks</h2>
                                    <span class="pattern-id">Pattern 1</span>
                                </div>
                                <div class="section-body">
                                    <table>
                                        <thead>
                                            <tr>
                                                <th style="width: 15%;">Context</th>
                                                <th style="width: 55%;">Validation Message</th>
                                                <th style="width: 15%;">Status</th>
                                                <th style="width: 15%;">Details</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            <xsl:for-each select="Funds/Fund">
                                                <xsl:variable name="fundName" select="Names/OfficialName"/>
                                                <xsl:variable name="fundCurrency" select="Currency"/>

                                                <!-- Check: Fund should have LEI -->
                                                <tr>
                                                    <td class="context">Fund</td>
                                                    <td class="message">
                                                        Fund "<xsl:value-of select="$fundName"/>"
                                                        <xsl:choose>
                                                            <xsl:when test="Identifiers/LEI">has LEI identifier</xsl:when>
                                                            <xsl:otherwise>should have a LEI identifier</xsl:otherwise>
                                                        </xsl:choose>
                                                    </td>
                                                    <td>
                                                        <xsl:choose>
                                                            <xsl:when test="Identifiers/LEI">
                                                                <span class="badge badge-success">PASS</span>
                                                            </xsl:when>
                                                            <xsl:otherwise>
                                                                <span class="badge badge-warning">WARNING</span>
                                                            </xsl:otherwise>
                                                        </xsl:choose>
                                                    </td>
                                                    <td>
                                                        <xsl:if test="Identifiers/LEI">
                                                            <xsl:value-of select="Identifiers/LEI"/>
                                                        </xsl:if>
                                                    </td>
                                                </tr>

                                                <!-- Check: Fund must have at least one portfolio -->
                                                <tr>
                                                    <td class="context">Fund</td>
                                                    <td class="message">
                                                        Fund must have at least one portfolio
                                                        (<xsl:value-of select="count(FundDynamicData/Portfolios/Portfolio)"/> found)
                                                    </td>
                                                    <td>
                                                        <xsl:choose>
                                                            <xsl:when test="count(FundDynamicData/Portfolios/Portfolio) > 0">
                                                                <span class="badge badge-success">PASS</span>
                                                            </xsl:when>
                                                            <xsl:otherwise>
                                                                <span class="badge badge-warning">WARNING</span>
                                                            </xsl:otherwise>
                                                        </xsl:choose>
                                                    </td>
                                                    <td><xsl:value-of select="count(FundDynamicData/Portfolios/Portfolio)"/> portfolio(s)</td>
                                                </tr>

                                                <!-- Check: Total Asset Value in fund currency -->
                                                <tr>
                                                    <td class="context">Fund</td>
                                                    <td class="message">
                                                        Fund Total Asset Value must be provided in fund currency (<xsl:value-of select="$fundCurrency"/>)
                                                    </td>
                                                    <td>
                                                        <xsl:choose>
                                                            <xsl:when test="FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount[@ccy = $fundCurrency]">
                                                                <span class="badge badge-success">PASS</span>
                                                            </xsl:when>
                                                            <xsl:otherwise>
                                                                <span class="badge badge-error">ERROR</span>
                                                            </xsl:otherwise>
                                                        </xsl:choose>
                                                    </td>
                                                    <td>
                                                        <xsl:value-of select="format-number(FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount[@ccy = $fundCurrency], '#,##0.00')"/>
                                                        <xsl:text> </xsl:text>
                                                        <xsl:value-of select="$fundCurrency"/>
                                                    </td>
                                                </tr>

                                                <!-- Check: ShareClasses should have ISIN -->
                                                <xsl:for-each select="SingleFund/ShareClasses/ShareClass">
                                                    <tr>
                                                        <td class="context">ShareClass</td>
                                                        <td class="message">
                                                            ShareClass "<xsl:value-of select="Names/OfficialName"/>"
                                                            <xsl:choose>
                                                                <xsl:when test="Identifiers/ISIN">has ISIN</xsl:when>
                                                                <xsl:otherwise>should have an ISIN</xsl:otherwise>
                                                            </xsl:choose>
                                                        </td>
                                                        <td>
                                                            <xsl:choose>
                                                                <xsl:when test="Identifiers/ISIN">
                                                                    <span class="badge badge-success">PASS</span>
                                                                </xsl:when>
                                                                <xsl:otherwise>
                                                                    <span class="badge badge-warning">WARNING</span>
                                                                </xsl:otherwise>
                                                            </xsl:choose>
                                                        </td>
                                                        <td><xsl:value-of select="Identifiers/ISIN"/></td>
                                                    </tr>
                                                </xsl:for-each>
                                            </xsl:for-each>
                                        </tbody>
                                    </table>
                                </div>
                            </div>

                            <!-- ============================================
                                 PATTERN 2: NAV CALCULATIONS
                                 ============================================ -->
                            <div class="validation-section">
                                <div class="section-header">
                                    <span class="icon">[2]</span>
                                    <h2>NAV Calculation Validations</h2>
                                    <span class="pattern-id">Pattern 2</span>
                                </div>
                                <div class="section-body">
                                    <table>
                                        <thead>
                                            <tr>
                                                <th style="width: 15%;">Context</th>
                                                <th style="width: 55%;">Validation Message</th>
                                                <th style="width: 15%;">Status</th>
                                                <th style="width: 15%;">Details</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            <xsl:for-each select="Funds/Fund[SingleFund/ShareClasses/ShareClass]">
                                                <xsl:variable name="fundCurrency" select="Currency"/>
                                                <xsl:variable name="fundTotalNAV" select="number(FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount[@ccy = $fundCurrency])"/>
                                                <xsl:variable name="sumShareClassNAV" select="sum(SingleFund/ShareClasses/ShareClass/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount[@ccy = $fundCurrency])"/>
                                                <xsl:variable name="difference" select="abs($fundTotalNAV - $sumShareClassNAV)"/>

                                                <!-- Check: Sum of ShareClass NAVs equals Fund NAV -->
                                                <tr>
                                                    <td class="context">Fund NAV</td>
                                                    <td class="message">
                                                        Sum of ShareClass NAVs (<xsl:value-of select="format-number($sumShareClassNAV, '#,##0.00')"/><xsl:text> </xsl:text><xsl:value-of select="$fundCurrency"/>)
                                                        vs Fund Total NAV (<xsl:value-of select="format-number($fundTotalNAV, '#,##0.00')"/><xsl:text> </xsl:text><xsl:value-of select="$fundCurrency"/>)
                                                    </td>
                                                    <td>
                                                        <xsl:choose>
                                                            <xsl:when test="$difference &lt; 1">
                                                                <span class="badge badge-success">PASS</span>
                                                            </xsl:when>
                                                            <xsl:otherwise>
                                                                <span class="badge badge-error">ERROR</span>
                                                            </xsl:otherwise>
                                                        </xsl:choose>
                                                    </td>
                                                    <td>Diff: <xsl:value-of select="format-number($difference, '#,##0.00')"/></td>
                                                </tr>

                                                <!-- Warning for small rounding differences -->
                                                <xsl:if test="$difference >= 0.01 and $difference &lt; 1">
                                                    <tr>
                                                        <td class="context">Fund NAV</td>
                                                        <td class="message">
                                                            Small rounding difference detected in NAV summation
                                                        </td>
                                                        <td><span class="badge badge-warning">WARNING</span></td>
                                                        <td>Diff: <xsl:value-of select="format-number($difference, '#,##0.00')"/><xsl:text> </xsl:text><xsl:value-of select="$fundCurrency"/></td>
                                                    </tr>
                                                </xsl:if>

                                                <!-- Check: ShareClass Price × Shares = NAV -->
                                                <xsl:for-each select="SingleFund/ShareClasses/ShareClass">
                                                    <xsl:variable name="shareclassCurrency" select="Currency"/>
                                                    <xsl:variable name="price" select="number(Prices/Price/NavPrice)"/>
                                                    <xsl:variable name="shares" select="number(TotalAssetValues/TotalAssetValue/SharesOutstanding)"/>
                                                    <xsl:variable name="reportedNAV" select="number(TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount[@ccy = $shareclassCurrency])"/>
                                                    <xsl:variable name="calculatedPrice" select="if ($shares > 0) then $reportedNAV div $shares else 0"/>
                                                    <xsl:variable name="priceDifference" select="abs($calculatedPrice - $price)"/>

                                                    <xsl:if test="$shares > 0">
                                                        <tr>
                                                            <td class="context">ShareClass</td>
                                                            <td class="message">
                                                                <xsl:value-of select="Identifiers/ISIN"/>: Price check
                                                                (Reported: <xsl:value-of select="format-number($price, '#,##0.0000')"/>,
                                                                Calculated: <xsl:value-of select="format-number($calculatedPrice, '#,##0.0000')"/>)
                                                            </td>
                                                            <td>
                                                                <xsl:choose>
                                                                    <xsl:when test="$priceDifference &lt; 0.1">
                                                                        <span class="badge badge-success">PASS</span>
                                                                    </xsl:when>
                                                                    <xsl:otherwise>
                                                                        <span class="badge badge-error">ERROR</span>
                                                                    </xsl:otherwise>
                                                                </xsl:choose>
                                                            </td>
                                                            <td>Diff: <xsl:value-of select="format-number($priceDifference, '#,##0.0000')"/></td>
                                                        </tr>
                                                    </xsl:if>
                                                </xsl:for-each>
                                            </xsl:for-each>

                                            <xsl:if test="not(Funds/Fund[SingleFund/ShareClasses/ShareClass])">
                                                <tr>
                                                    <td colspan="4" class="no-issues">No ShareClasses found to validate</td>
                                                </tr>
                                            </xsl:if>
                                        </tbody>
                                    </table>
                                </div>
                            </div>

                            <!-- ============================================
                                 PATTERN 3: PORTFOLIO VALIDATIONS
                                 ============================================ -->
                            <div class="validation-section">
                                <div class="section-header">
                                    <span class="icon">[3]</span>
                                    <h2>Portfolio Position Validations</h2>
                                    <span class="pattern-id">Pattern 3</span>
                                </div>
                                <div class="section-body">
                                    <table>
                                        <thead>
                                            <tr>
                                                <th style="width: 15%;">Context</th>
                                                <th style="width: 55%;">Validation Message</th>
                                                <th style="width: 15%;">Status</th>
                                                <th style="width: 15%;">Details</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            <xsl:for-each select="Funds/Fund[FundDynamicData/Portfolios/Portfolio]">
                                                <xsl:variable name="fundCurrency" select="Currency"/>
                                                <xsl:variable name="fundTotalNAV" select="number(FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount[@ccy = $fundCurrency])"/>
                                                <xsl:variable name="sumPositionValues" select="sum(FundDynamicData/Portfolios/Portfolio/Positions/Position/TotalValue/Amount[@ccy = $fundCurrency])"/>
                                                <xsl:variable name="posDifference" select="abs($sumPositionValues - $fundTotalNAV)"/>

                                                <!-- Check: Sum of position values = Fund NAV -->
                                                <tr>
                                                    <td class="context">Portfolio</td>
                                                    <td class="message">
                                                        Sum of position values (<xsl:value-of select="format-number($sumPositionValues, '#,##0.00')"/><xsl:text> </xsl:text><xsl:value-of select="$fundCurrency"/>)
                                                        vs Fund Total NAV (<xsl:value-of select="format-number($fundTotalNAV, '#,##0.00')"/><xsl:text> </xsl:text><xsl:value-of select="$fundCurrency"/>)
                                                    </td>
                                                    <td>
                                                        <xsl:choose>
                                                            <xsl:when test="$posDifference &lt; 1">
                                                                <span class="badge badge-success">PASS</span>
                                                            </xsl:when>
                                                            <xsl:otherwise>
                                                                <span class="badge badge-error">ERROR</span>
                                                            </xsl:otherwise>
                                                        </xsl:choose>
                                                    </td>
                                                    <td>Diff: <xsl:value-of select="format-number($posDifference, '#,##0.00')"/></td>
                                                </tr>

                                                <!-- Check: Portfolio percentages sum to 100% -->
                                                <xsl:variable name="sumPercentages" select="sum(FundDynamicData/Portfolios/Portfolio/Positions/Position/TotalPercentage)"/>
                                                <xsl:variable name="pctDifference" select="abs($sumPercentages - 100)"/>

                                                <xsl:if test="FundDynamicData/Portfolios/Portfolio/Positions/Position/TotalPercentage">
                                                    <tr>
                                                        <td class="context">Portfolio %</td>
                                                        <td class="message">
                                                            Position percentages sum: <xsl:value-of select="format-number($sumPercentages, '#,##0.0000')"/>%
                                                            (expected: 100%)
                                                        </td>
                                                        <td>
                                                            <xsl:choose>
                                                                <xsl:when test="$pctDifference &lt;= 1">
                                                                    <span class="badge badge-success">PASS</span>
                                                                </xsl:when>
                                                                <xsl:otherwise>
                                                                    <span class="badge badge-error">ERROR</span>
                                                                </xsl:otherwise>
                                                            </xsl:choose>
                                                        </td>
                                                        <td>Diff: <xsl:value-of select="format-number($pctDifference, '#,##0.0000')"/>%</td>
                                                    </tr>

                                                    <xsl:if test="$pctDifference > 0.01 and $pctDifference &lt;= 1">
                                                        <tr>
                                                            <td class="context">Portfolio %</td>
                                                            <td class="message">Small deviation in percentage sum</td>
                                                            <td><span class="badge badge-warning">WARNING</span></td>
                                                            <td>Total: <xsl:value-of select="format-number($sumPercentages, '#,##0.0000')"/>%</td>
                                                        </tr>
                                                    </xsl:if>
                                                </xsl:if>

                                                <!-- Check: Position value in fund currency -->
                                                <xsl:for-each select="FundDynamicData/Portfolios/Portfolio/Positions/Position">
                                                    <xsl:variable name="positionFundCurrency" select="ancestor::Fund/Currency"/>
                                                    <xsl:if test="not(TotalValue/Amount[@ccy = $positionFundCurrency])">
                                                        <tr>
                                                            <td class="context">Position</td>
                                                            <td class="message">
                                                                Position <xsl:value-of select="UniqueID"/> does not have value in fund currency (<xsl:value-of select="$positionFundCurrency"/>).
                                                                Available: <xsl:value-of select="string-join(TotalValue/Amount/@ccy, ', ')"/>
                                                            </td>
                                                            <td><span class="badge badge-error">ERROR</span></td>
                                                            <td>Missing <xsl:value-of select="$positionFundCurrency"/></td>
                                                        </tr>
                                                    </xsl:if>
                                                </xsl:for-each>

                                                <!-- Check: Consistent value direction across currencies -->
                                                <xsl:for-each select="FundDynamicData/Portfolios/Portfolio/Positions/Position[count(TotalValue/Amount) > 1]">
                                                    <xsl:variable name="hasPositive" select="count(TotalValue/Amount[number(.) > 1]) > 0"/>
                                                    <xsl:variable name="hasNegative" select="count(TotalValue/Amount[number(.) &lt; -1]) > 0"/>

                                                    <xsl:if test="$hasPositive and $hasNegative">
                                                        <tr>
                                                            <td class="context">Position</td>
                                                            <td class="message">
                                                                Position <xsl:value-of select="UniqueID"/> has mixed value directions across currencies.
                                                                All values must be either positive or negative.
                                                            </td>
                                                            <td><span class="badge badge-error">ERROR</span></td>
                                                            <td>Mixed signs</td>
                                                        </tr>
                                                    </xsl:if>
                                                </xsl:for-each>
                                            </xsl:for-each>

                                            <xsl:if test="not(Funds/Fund[FundDynamicData/Portfolios/Portfolio])">
                                                <tr>
                                                    <td colspan="4" class="no-issues">No Portfolios found to validate</td>
                                                </tr>
                                            </xsl:if>
                                        </tbody>
                                    </table>
                                </div>
                            </div>

                            <!-- ============================================
                                 PATTERN 4: ASSET-SPECIFIC VALIDATIONS
                                 ============================================ -->
                            <div class="validation-section">
                                <div class="section-header">
                                    <span class="icon">[4]</span>
                                    <h2>Asset-Specific Validations</h2>
                                    <span class="pattern-id">Pattern 4</span>
                                </div>
                                <div class="section-body">
                                    <table>
                                        <thead>
                                            <tr>
                                                <th style="width: 12%;">Asset Type</th>
                                                <th style="width: 20%;">Asset Name</th>
                                                <th style="width: 38%;">Validation Message</th>
                                                <th style="width: 15%;">Status</th>
                                                <th style="width: 15%;">Details</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            <!-- Equity, Bond, ShareClass assets must have ISIN -->
                                            <xsl:for-each select="Assets/Asset[AssetType = 'EQ' or AssetType = 'BO' or AssetType = 'SC']">
                                                <tr>
                                                    <td><xsl:value-of select="AssetType"/></td>
                                                    <td><xsl:value-of select="Name"/></td>
                                                    <td class="message">
                                                        <xsl:value-of select="AssetType"/> asset must have an ISIN identifier
                                                    </td>
                                                    <td>
                                                        <xsl:choose>
                                                            <xsl:when test="Identifiers/ISIN">
                                                                <span class="badge badge-success">PASS</span>
                                                            </xsl:when>
                                                            <xsl:otherwise>
                                                                <span class="badge badge-error">ERROR</span>
                                                            </xsl:otherwise>
                                                        </xsl:choose>
                                                    </td>
                                                    <td><xsl:value-of select="Identifiers/ISIN"/></td>
                                                </tr>
                                            </xsl:for-each>

                                            <!-- Account assets should have counterparty with LEI or BIC -->
                                            <xsl:for-each select="Assets/Asset[AssetType = 'AC']">
                                                <tr>
                                                    <td>AC</td>
                                                    <td><xsl:value-of select="Name"/></td>
                                                    <td class="message">
                                                        Account should have counterparty with LEI or BIC identifier
                                                    </td>
                                                    <td>
                                                        <xsl:choose>
                                                            <xsl:when test="AssetDetails/Account/Counterparty/Identifiers/LEI or AssetDetails/Account/Counterparty/Identifiers/BIC">
                                                                <span class="badge badge-success">PASS</span>
                                                            </xsl:when>
                                                            <xsl:otherwise>
                                                                <span class="badge badge-warning">WARNING</span>
                                                            </xsl:otherwise>
                                                        </xsl:choose>
                                                    </td>
                                                    <td>
                                                        <xsl:value-of select="AssetDetails/Account/Counterparty/Identifiers/LEI"/>
                                                        <xsl:value-of select="AssetDetails/Account/Counterparty/Identifiers/BIC"/>
                                                    </td>
                                                </tr>
                                            </xsl:for-each>

                                            <!-- Derivatives should have exposure -->
                                            <xsl:for-each select="Assets/Asset[AssetType = 'OP' or AssetType = 'FU' or AssetType = 'FX' or AssetType = 'SW']">
                                                <tr>
                                                    <td><xsl:value-of select="AssetType"/></td>
                                                    <td><xsl:value-of select="Name"/></td>
                                                    <td class="message">
                                                        Derivative should have exposure information
                                                    </td>
                                                    <td>
                                                        <xsl:choose>
                                                            <xsl:when test="AssetDetails//Exposure">
                                                                <span class="badge badge-success">PASS</span>
                                                            </xsl:when>
                                                            <xsl:otherwise>
                                                                <span class="badge badge-warning">WARNING</span>
                                                            </xsl:otherwise>
                                                        </xsl:choose>
                                                    </td>
                                                    <td></td>
                                                </tr>
                                            </xsl:for-each>

                                            <!-- Options must have underlying -->
                                            <xsl:for-each select="Assets/Asset[AssetType = 'OP']">
                                                <tr>
                                                    <td>OP</td>
                                                    <td><xsl:value-of select="Name"/></td>
                                                    <td class="message">
                                                        Option must have at least one underlying asset
                                                    </td>
                                                    <td>
                                                        <xsl:choose>
                                                            <xsl:when test="AssetDetails/Option/Underlyings/Underlying">
                                                                <span class="badge badge-success">PASS</span>
                                                            </xsl:when>
                                                            <xsl:otherwise>
                                                                <span class="badge badge-error">ERROR</span>
                                                            </xsl:otherwise>
                                                        </xsl:choose>
                                                    </td>
                                                    <td><xsl:value-of select="count(AssetDetails/Option/Underlyings/Underlying)"/> underlying(s)</td>
                                                </tr>
                                            </xsl:for-each>

                                            <!-- Futures must have underlying -->
                                            <xsl:for-each select="Assets/Asset[AssetType = 'FU']">
                                                <tr>
                                                    <td>FU</td>
                                                    <td><xsl:value-of select="Name"/></td>
                                                    <td class="message">
                                                        Future must have at least one underlying asset
                                                    </td>
                                                    <td>
                                                        <xsl:choose>
                                                            <xsl:when test="AssetDetails/Future/Underlyings/Underlying">
                                                                <span class="badge badge-success">PASS</span>
                                                            </xsl:when>
                                                            <xsl:otherwise>
                                                                <span class="badge badge-error">ERROR</span>
                                                            </xsl:otherwise>
                                                        </xsl:choose>
                                                    </td>
                                                    <td><xsl:value-of select="count(AssetDetails/Future/Underlyings/Underlying)"/> underlying(s)</td>
                                                </tr>
                                            </xsl:for-each>

                                            <xsl:if test="not(Assets/Asset)">
                                                <tr>
                                                    <td colspan="5" class="no-issues">No Assets found to validate</td>
                                                </tr>
                                            </xsl:if>
                                        </tbody>
                                    </table>
                                </div>
                            </div>

                            <!-- ============================================
                                 PATTERN 5: DATE CONSISTENCY
                                 ============================================ -->
                            <div class="validation-section">
                                <div class="section-header">
                                    <span class="icon">[5]</span>
                                    <h2>Date Consistency Validations</h2>
                                    <span class="pattern-id">Pattern 5</span>
                                </div>
                                <div class="section-body">
                                    <table>
                                        <thead>
                                            <tr>
                                                <th style="width: 15%;">Context</th>
                                                <th style="width: 55%;">Validation Message</th>
                                                <th style="width: 15%;">Status</th>
                                                <th style="width: 15%;">Details</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            <!-- Content date should not be in the future -->
                                            <xsl:if test="ControlData/ContentDate">
                                                <tr>
                                                    <td class="context">ContentDate</td>
                                                    <td class="message">
                                                        Content date (<xsl:value-of select="ControlData/ContentDate"/>) should not be in the future
                                                    </td>
                                                    <td>
                                                        <xsl:choose>
                                                            <xsl:when test="xs:date(ControlData/ContentDate) &lt;= current-date()">
                                                                <span class="badge badge-success">PASS</span>
                                                            </xsl:when>
                                                            <xsl:otherwise>
                                                                <span class="badge badge-warning">WARNING</span>
                                                            </xsl:otherwise>
                                                        </xsl:choose>
                                                    </td>
                                                    <td><xsl:value-of select="ControlData/ContentDate"/></td>
                                                </tr>
                                            </xsl:if>

                                            <!-- All NAV dates should be consistent -->
                                            <xsl:for-each select="Funds/Fund">
                                                <xsl:variable name="fundNavDate" select="FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate"/>
                                                <xsl:variable name="inconsistentDates" select="SingleFund/ShareClasses/ShareClass/TotalAssetValues/TotalAssetValue/NavDate[. != $fundNavDate]"/>

                                                <tr>
                                                    <td class="context">NAV Dates</td>
                                                    <td class="message">
                                                        All ShareClass NAV dates should match Fund NAV date (<xsl:value-of select="$fundNavDate"/>)
                                                    </td>
                                                    <td>
                                                        <xsl:choose>
                                                            <xsl:when test="count($inconsistentDates) = 0">
                                                                <span class="badge badge-success">PASS</span>
                                                            </xsl:when>
                                                            <xsl:otherwise>
                                                                <span class="badge badge-warning">WARNING</span>
                                                            </xsl:otherwise>
                                                        </xsl:choose>
                                                    </td>
                                                    <td>
                                                        <xsl:if test="count($inconsistentDates) > 0">
                                                            <xsl:value-of select="count($inconsistentDates)"/> mismatch(es)
                                                        </xsl:if>
                                                    </td>
                                                </tr>
                                            </xsl:for-each>
                                        </tbody>
                                    </table>
                                </div>
                            </div>

                            <!-- ============================================
                                 PATTERN 6: IDENTIFIER VALIDATIONS
                                 ============================================ -->
                            <div class="validation-section">
                                <div class="section-header">
                                    <span class="icon">[6]</span>
                                    <h2>Identifier Format Validations</h2>
                                    <span class="pattern-id">Pattern 6</span>
                                </div>
                                <div class="section-body">
                                    <table>
                                        <thead>
                                            <tr>
                                                <th style="width: 12%;">Type</th>
                                                <th style="width: 20%;">Value</th>
                                                <th style="width: 38%;">Validation Message</th>
                                                <th style="width: 15%;">Status</th>
                                                <th style="width: 15%;">Details</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            <!-- ISIN format validation -->
                                            <xsl:for-each select="//Identifiers/ISIN">
                                                <xsl:variable name="isinValue" select="."/>
                                                <xsl:variable name="isinLength" select="string-length($isinValue)"/>
                                                <xsl:variable name="isinFormat" select="matches($isinValue, '^[A-Z]{2}[A-Z0-9]{9}[0-9]$')"/>

                                                <xsl:if test="$isinLength != 12 or not($isinFormat)">
                                                    <tr>
                                                        <td>ISIN</td>
                                                        <td class="context"><xsl:value-of select="$isinValue"/></td>
                                                        <td class="message">
                                                            <xsl:choose>
                                                                <xsl:when test="$isinLength != 12">
                                                                    ISIN must be exactly 12 characters (found <xsl:value-of select="$isinLength"/>)
                                                                </xsl:when>
                                                                <xsl:otherwise>
                                                                    ISIN does not match format (2 letters, 9 alphanumeric, 1 digit)
                                                                </xsl:otherwise>
                                                            </xsl:choose>
                                                        </td>
                                                        <td>
                                                            <xsl:choose>
                                                                <xsl:when test="$isinLength != 12">
                                                                    <span class="badge badge-error">ERROR</span>
                                                                </xsl:when>
                                                                <xsl:otherwise>
                                                                    <span class="badge badge-warning">WARNING</span>
                                                                </xsl:otherwise>
                                                            </xsl:choose>
                                                        </td>
                                                        <td>Length: <xsl:value-of select="$isinLength"/></td>
                                                    </tr>
                                                </xsl:if>
                                            </xsl:for-each>

                                            <!-- LEI format validation -->
                                            <xsl:for-each select="//Identifiers/LEI">
                                                <xsl:variable name="leiValue" select="."/>
                                                <xsl:variable name="leiLength" select="string-length($leiValue)"/>
                                                <xsl:variable name="leiFormat" select="matches($leiValue, '^[A-Z0-9]{18}[0-9]{2}$')"/>

                                                <xsl:if test="$leiLength != 20 or not($leiFormat)">
                                                    <tr>
                                                        <td>LEI</td>
                                                        <td class="context"><xsl:value-of select="$leiValue"/></td>
                                                        <td class="message">
                                                            <xsl:choose>
                                                                <xsl:when test="$leiLength != 20">
                                                                    LEI must be exactly 20 characters (found <xsl:value-of select="$leiLength"/>)
                                                                </xsl:when>
                                                                <xsl:otherwise>
                                                                    LEI does not match format (18 alphanumeric, 2 check digits)
                                                                </xsl:otherwise>
                                                            </xsl:choose>
                                                        </td>
                                                        <td>
                                                            <xsl:choose>
                                                                <xsl:when test="$leiLength != 20">
                                                                    <span class="badge badge-error">ERROR</span>
                                                                </xsl:when>
                                                                <xsl:otherwise>
                                                                    <span class="badge badge-warning">WARNING</span>
                                                                </xsl:otherwise>
                                                            </xsl:choose>
                                                        </td>
                                                        <td>Length: <xsl:value-of select="$leiLength"/></td>
                                                    </tr>
                                                </xsl:if>
                                            </xsl:for-each>

                                            <!-- BIC format validation -->
                                            <xsl:for-each select="//Identifiers/BIC">
                                                <xsl:variable name="bicValue" select="."/>
                                                <xsl:variable name="bicLength" select="string-length($bicValue)"/>

                                                <xsl:if test="$bicLength != 8 and $bicLength != 11">
                                                    <tr>
                                                        <td>BIC</td>
                                                        <td class="context"><xsl:value-of select="$bicValue"/></td>
                                                        <td class="message">
                                                            BIC must be either 8 or 11 characters (found <xsl:value-of select="$bicLength"/>)
                                                        </td>
                                                        <td><span class="badge badge-error">ERROR</span></td>
                                                        <td>Length: <xsl:value-of select="$bicLength"/></td>
                                                    </tr>
                                                </xsl:if>
                                            </xsl:for-each>

                                            <!-- Show summary if all identifiers are valid -->
                                            <xsl:variable name="totalISIN" select="count(//Identifiers/ISIN)"/>
                                            <xsl:variable name="totalLEI" select="count(//Identifiers/LEI)"/>
                                            <xsl:variable name="totalBIC" select="count(//Identifiers/BIC)"/>

                                            <tr>
                                                <td colspan="2"><strong>Summary</strong></td>
                                                <td class="message">
                                                    Total identifiers checked: <xsl:value-of select="$totalISIN"/> ISINs,
                                                    <xsl:value-of select="$totalLEI"/> LEIs,
                                                    <xsl:value-of select="$totalBIC"/> BICs
                                                </td>
                                                <td><span class="badge badge-info">INFO</span></td>
                                                <td></td>
                                            </tr>
                                        </tbody>
                                    </table>
                                </div>
                            </div>

                            <!-- ============================================
                                 PATTERN 7: CURRENCY VALIDATIONS
                                 ============================================ -->
                            <div class="validation-section">
                                <div class="section-header">
                                    <span class="icon">[7]</span>
                                    <h2>Currency Validations</h2>
                                    <span class="pattern-id">Pattern 7</span>
                                </div>
                                <div class="section-body">
                                    <table>
                                        <thead>
                                            <tr>
                                                <th style="width: 15%;">Context</th>
                                                <th style="width: 55%;">Validation Message</th>
                                                <th style="width: 15%;">Status</th>
                                                <th style="width: 15%;">Details</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            <!-- Currency codes should be 3 letters (ISO 4217) -->
                                            <xsl:for-each select="//Currency[not(matches(., '^[A-Z]{3}$'))]">
                                                <tr>
                                                    <td class="context">Currency</td>
                                                    <td class="message">
                                                        Currency code "<xsl:value-of select="."/>" should be a 3-letter ISO code
                                                    </td>
                                                    <td><span class="badge badge-warning">WARNING</span></td>
                                                    <td><xsl:value-of select="."/></td>
                                                </tr>
                                            </xsl:for-each>

                                            <xsl:for-each select="//Amount/@ccy[not(matches(., '^[A-Z]{3}$'))]">
                                                <tr>
                                                    <td class="context">Amount@ccy</td>
                                                    <td class="message">
                                                        Currency attribute "<xsl:value-of select="."/>" should be a 3-letter ISO code
                                                    </td>
                                                    <td><span class="badge badge-warning">WARNING</span></td>
                                                    <td><xsl:value-of select="."/></td>
                                                </tr>
                                            </xsl:for-each>

                                            <!-- All amounts should have currency attribute -->
                                            <xsl:for-each select="//Amount[not(@ccy)]">
                                                <tr>
                                                    <td class="context">Amount</td>
                                                    <td class="message">
                                                        Amount element without currency attribute (value: <xsl:value-of select="."/>)
                                                    </td>
                                                    <td><span class="badge badge-error">ERROR</span></td>
                                                    <td>Missing @ccy</td>
                                                </tr>
                                            </xsl:for-each>

                                            <!-- Summary -->
                                            <xsl:variable name="totalCurrencies" select="count(distinct-values(//Currency | //Amount/@ccy))"/>
                                            <xsl:variable name="currencyList" select="distinct-values(//Currency | //Amount/@ccy)"/>

                                            <tr>
                                                <td><strong>Summary</strong></td>
                                                <td class="message">
                                                    Currencies used: <xsl:value-of select="string-join($currencyList, ', ')"/>
                                                </td>
                                                <td><span class="badge badge-info">INFO</span></td>
                                                <td><xsl:value-of select="$totalCurrencies"/> unique</td>
                                            </tr>
                                        </tbody>
                                    </table>
                                </div>
                            </div>

                        </div>

                        <div class="footer">
                            <p>Generated by <strong>FreeXmlToolkit</strong> |
                            Based on <a href="#">eam-rules.sch</a> Schematron validation rules</p>
                            <p style="margin-top: 0.5rem; font-size: 0.8rem; color: #9ca3af;">
                                7 Validation Patterns | Structural • NAV • Portfolio • Assets • Dates • Identifiers • Currency
                            </p>
                        </div>
                    </div>
                </div>
            </body>
        </html>
    </xsl:template>

</xsl:stylesheet>
