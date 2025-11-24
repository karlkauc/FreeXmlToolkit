<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="html" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <xsl:template match="/FundsXML4">
        <html>
            <head>
                <meta charset="UTF-8"/>
                <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                <title>Position Value Validation Report</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body { font-family: Arial, Helvetica, sans-serif; font-size: 14px; line-height: 1.6; color: #333;
                    background: #f5f5f5; padding: 20px; }
                    .container { max-width: 1200px; margin: 0 auto; background: white; padding: 30px; box-shadow: 0 2px
                    10px rgba(0,0,0,0.1); }
                    .header { background: linear-gradient(135deg, #e83e8c 0%, #f06ca3 100%); color: white; padding:
                    40px; margin: -30px -30px 30px -30px; text-align: center; }
                    .header h1 { font-size: 36px; margin-bottom: 10px; }
                    .header p { font-size: 18px; opacity: 0.9; }
                    .summary-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap:
                    20px; margin-bottom: 30px; }
                    .card { background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%); border-left: 4px solid
                    #e83e8c; padding: 20px; border-radius: 5px; }
                    .card-label { font-size: 12px; color: #666; text-transform: uppercase; margin-bottom: 5px; }
                    .card-value { font-size: 28px; font-weight: bold; color: #e83e8c; }
                    .section { margin-bottom: 40px; }
                    .section-title { font-size: 24px; font-weight: bold; color: #495057; border-bottom: 3px solid
                    #e83e8c; padding-bottom: 10px; margin-bottom: 20px; }
                    table { width: 100%; border-collapse: collapse; margin-bottom: 20px; }
                    th { background: #f8f9fa; padding: 12px; text-align: left; font-weight: bold; border: 1px solid
                    #dee2e6; }
                    td { padding: 10px; border: 1px solid #dee2e6; }
                    tr:nth-child(even) { background: #f8f9fa; }
                    .status-pass { color: #28a745; font-weight: bold; }
                    .status-warn { color: #ffc107; font-weight: bold; }
                    .status-fail { color: #dc3545; font-weight: bold; }
                    .icon-pass::before { content: '✓'; margin-right: 5px; }
                    .icon-warn::before { content: '!'; margin-right: 5px; }
                    .icon-fail::before { content: '✗'; margin-right: 5px; }
                    .fund-header { background: #e83e8c; color: white; padding: 20px; margin: 20px 0 15px 0;
                    border-radius: 5px; }
                    .fund-header h3 { font-size: 20px; }
                    .monospace { font-family: 'Courier New', monospace; }
                    .text-right { text-align: right; }
                    .text-center { text-align: center; }
                    .highlight-box { background: #fff0f6; border: 2px solid #e83e8c; padding: 15px; margin: 20px 0;
                    border-radius: 5px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>POSITION VALUE VALIDATION</h1>
                        <p>Financial Data and Position Value Analysis</p>
                    </div>

                    <!-- Summary Cards -->
                    <div class="summary-cards">
                        <div class="card">
                            <div class="card-label">Total Positions</div>
                            <div class="card-value">
                                <xsl:value-of select="count(//Position)"/>
                            </div>
                        </div>
                        <div class="card">
                            <div class="card-label">Positions with Values</div>
                            <div class="card-value">
                                <xsl:value-of select="count(//Position[TotalValue/Amount])"/>
                            </div>
                        </div>
                        <div class="card">
                            <div class="card-label">Total Funds</div>
                            <div class="card-value">
                                <xsl:value-of select="count(Funds/Fund)"/>
                            </div>
                        </div>
                        <div class="card">
                            <div class="card-label">Value Coverage</div>
                            <div class="card-value">
                                <xsl:value-of
                                        select="format-number(count(//Position[TotalValue/Amount]) div count(//Position) * 100, '0')"/>%
                            </div>
                        </div>
                    </div>

                    <!-- Value Completeness Checks -->
                    <div class="section">
                        <h2 class="section-title">VALUE COMPLETENESS VALIDATION</h2>
                        <table>
                            <thead>
                                <tr>
                                    <th style="width: 5%;">#</th>
                                    <th style="width: 40%;">Validation Rule</th>
                                    <th style="width: 30%;">Description</th>
                                    <th style="width: 15%;" class="text-center">Result</th>
                                    <th style="width: 10%;" class="text-center">Status</th>
                                </tr>
                            </thead>
                            <tbody>
                                <!-- Rule 1: TotalValue Present -->
                                <tr>
                                    <td class="text-center">1</td>
                                    <td>
                                        <strong>TotalValue Present</strong>
                                    </td>
                                    <td>All positions have total value</td>
                                    <td class="text-center">
                                        <xsl:variable name="missingValues"
                                                      select="count(//Position[not(TotalValue/Amount)])"/>
                                        <xsl:choose>
                                            <xsl:when test="$missingValues = 0">PASS</xsl:when>
                                            <xsl:otherwise>FAIL (<xsl:value-of select="$missingValues"/> missing)
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </td>
                                    <td class="text-center">
                                        <xsl:variable name="missingValues"
                                                      select="count(//Position[not(TotalValue/Amount)])"/>
                                        <xsl:choose>
                                            <xsl:when test="$missingValues = 0">
                                                <span class="status-pass icon-pass"/>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <span class="status-fail icon-fail"/>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </td>
                                </tr>

                                <!-- Rule 2: TotalPercentage Present -->
                                <tr>
                                    <td class="text-center">2</td>
                                    <td>
                                        <strong>TotalPercentage Present</strong>
                                    </td>
                                    <td>All positions have percentages</td>
                                    <td class="text-center">
                                        <xsl:variable name="missingPct"
                                                      select="count(//Position[not(TotalPercentage)])"/>
                                        <xsl:choose>
                                            <xsl:when test="$missingPct = 0">PASS</xsl:when>
                                            <xsl:otherwise>FAIL (<xsl:value-of select="$missingPct"/> missing)
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </td>
                                    <td class="text-center">
                                        <xsl:variable name="missingPct"
                                                      select="count(//Position[not(TotalPercentage)])"/>
                                        <xsl:choose>
                                            <xsl:when test="$missingPct = 0">
                                                <span class="status-pass icon-pass"/>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <span class="status-fail icon-fail"/>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </td>
                                </tr>

                                <!-- Rule 3: Positive Values -->
                                <tr>
                                    <td class="text-center">3</td>
                                    <td>
                                        <strong>Positive Position Values</strong>
                                    </td>
                                    <td>All values are positive</td>
                                    <td class="text-center">
                                        <xsl:variable name="negativeValues"
                                                      select="count(//Position/TotalValue/Amount[. &lt; 0])"/>
                                        <xsl:choose>
                                            <xsl:when test="$negativeValues = 0">PASS</xsl:when>
                                            <xsl:otherwise>WARN (<xsl:value-of select="$negativeValues"/> negative)
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </td>
                                    <td class="text-center">
                                        <xsl:variable name="negativeValues"
                                                      select="count(//Position/TotalValue/Amount[. &lt; 0])"/>
                                        <xsl:choose>
                                            <xsl:when test="$negativeValues = 0">
                                                <span class="status-pass icon-pass"/>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <span class="status-warn icon-warn"/>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </td>
                                </tr>

                                <!-- Rule 4: Currency Present -->
                                <tr>
                                    <td class="text-center">4</td>
                                    <td>
                                        <strong>Currency Code Present</strong>
                                    </td>
                                    <td>All positions have currency</td>
                                    <td class="text-center">
                                        <xsl:variable name="missingCcy" select="count(//Position[not(Currency)])"/>
                                        <xsl:choose>
                                            <xsl:when test="$missingCcy = 0">PASS</xsl:when>
                                            <xsl:otherwise>FAIL (<xsl:value-of select="$missingCcy"/> missing)
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </td>
                                    <td class="text-center">
                                        <xsl:variable name="missingCcy" select="count(//Position[not(Currency)])"/>
                                        <xsl:choose>
                                            <xsl:when test="$missingCcy = 0">
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

                    <!-- Fund-Level NAV Reconciliation -->
                    <div class="section">
                        <h2 class="section-title">NAV RECONCILIATION</h2>
                        <xsl:for-each select="Funds/Fund">
                            <xsl:variable name="fundCurrency" select="Currency"/>
                            <xsl:variable name="fundNAV"
                                          select="FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount"/>
                            <xsl:variable name="totalPositionValue"
                                          select="sum(FundDynamicData/Portfolios/Portfolio/Positions/Position/TotalValue/Amount[@ccy = $fundCurrency])"/>
                            <xsl:variable name="difference" select="$fundNAV - $totalPositionValue"/>
                            <xsl:variable name="percentDiff" select="($difference div $fundNAV) * 100"/>

                            <div class="fund-header">
                                <h3>
                                    <xsl:value-of select="Names/OfficialName"/>
                                </h3>
                            </div>

                            <div class="highlight-box">
                                <table style="border: none;">
                                    <tr style="background: none;">
                                        <td style="border: none; width: 30%; padding: 5px;">
                                            <strong>Fund NAV:</strong>
                                        </td>
                                        <td style="border: none; padding: 5px;" class="monospace">
                                            <xsl:value-of select="format-number($fundNAV, '#,##0.00')"/>
                                            <xsl:value-of select="$fundCurrency"/>
                                        </td>
                                    </tr>
                                    <tr style="background: none;">
                                        <td style="border: none; padding: 5px;">
                                            <strong>Position Total:</strong>
                                        </td>
                                        <td style="border: none; padding: 5px;" class="monospace">
                                            <xsl:value-of select="format-number($totalPositionValue, '#,##0.00')"/>
                                            <xsl:value-of select="$fundCurrency"/>
                                        </td>
                                    </tr>
                                    <tr style="background: none;">
                                        <td style="border: none; padding: 5px;">
                                            <strong>Difference:</strong>
                                        </td>
                                        <td style="border: none; padding: 5px;" class="monospace">
                                            <xsl:value-of select="format-number($difference, '#,##0.00')"/>
                                            (<xsl:value-of select="format-number($percentDiff, '0.00')"/>%)
                                            <xsl:choose>
                                                <xsl:when test="$percentDiff &gt;= -5 and $percentDiff &lt;= 5">
                                                    <span class="status-pass icon-pass" style="margin-left: 10px;"/>
                                                </xsl:when>
                                                <xsl:when test="$percentDiff &gt;= -10 and $percentDiff &lt;= 10">
                                                    <span class="status-warn icon-warn" style="margin-left: 10px;"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-fail icon-fail" style="margin-left: 10px;"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>
                                </table>
                            </div>

                            <table>
                                <thead>
                                    <tr>
                                        <th style="width: 5%;">#</th>
                                        <th style="width: 50%;">Quality Check</th>
                                        <th style="width: 25%;" class="text-center">Result</th>
                                        <th style="width: 20%;" class="text-center">Status</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <!-- Check 1: Percentage Sum -->
                                    <tr>
                                        <td class="text-center">1</td>
                                        <td>
                                            <strong>Percentage Sum ≈ 100%</strong>
                                        </td>
                                        <td class="text-center">
                                            <xsl:variable name="totalPct"
                                                          select="sum(FundDynamicData/Portfolios/Portfolio/Positions/Position/TotalPercentage)"/>
                                            <xsl:value-of select="format-number($totalPct, '0.00')"/>%
                                        </td>
                                        <td class="text-center">
                                            <xsl:variable name="totalPct"
                                                          select="sum(FundDynamicData/Portfolios/Portfolio/Positions/Position/TotalPercentage)"/>
                                            <xsl:choose>
                                                <xsl:when test="$totalPct &gt;= 95 and $totalPct &lt;= 105">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:when test="$totalPct &gt;= 90 and $totalPct &lt;= 110">
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
                        </xsl:for-each>
                    </div>

                    <!-- Footer -->
                    <div style="margin-top: 40px; padding-top: 20px; border-top: 2px solid #e83e8c; text-align: center; color: #666; font-size: 12px;">
                        <p>Position Value Validation Report Generated from FundsXML 4.28 | Report Date:
                            <xsl:value-of select="ControlData/ContentDate"/>
                        </p>
                    </div>
                </div>
            </body>
        </html>
    </xsl:template>

</xsl:stylesheet>
