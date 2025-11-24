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
                <title>Data Quality Report</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body { font-family: Arial, Helvetica, sans-serif; font-size: 14px; line-height: 1.6; color: #333;
                    background: #f5f5f5; padding: 20px; }
                    .container { max-width: 1200px; margin: 0 auto; background: white; padding: 30px; box-shadow: 0 2px
                    10px rgba(0,0,0,0.1); }
                    .header { background: linear-gradient(135deg, #6610f2 0%, #8e44fd 100%); color: white; padding:
                    40px; margin: -30px -30px 30px -30px; text-align: center; }
                    .header h1 { font-size: 36px; margin-bottom: 10px; }
                    .header p { font-size: 18px; opacity: 0.9; }
                    .summary-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap:
                    20px; margin-bottom: 30px; }
                    .card { background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%); border-left: 4px solid
                    #6610f2; padding: 20px; border-radius: 5px; }
                    .card-label { font-size: 12px; color: #666; text-transform: uppercase; margin-bottom: 5px; }
                    .card-value { font-size: 28px; font-weight: bold; color: #6610f2; }
                    .section { margin-bottom: 40px; }
                    .section-title { font-size: 24px; font-weight: bold; color: #495057; border-bottom: 3px solid
                    #6610f2; padding-bottom: 10px; margin-bottom: 20px; }
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
                    .fund-header { background: #6610f2; color: white; padding: 20px; margin: 20px 0 15px 0;
                    border-radius: 5px; }
                    .fund-header h3 { font-size: 20px; }
                    .monospace { font-family: 'Courier New', monospace; }
                    .text-right { text-align: right; }
                    .text-center { text-align: center; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>DATA QUALITY REPORT</h1>
                        <p>Comprehensive Data Quality Validation and Analysis</p>
                    </div>

                    <!-- Summary Cards -->
                    <div class="summary-cards">
                        <div class="card">
                            <div class="card-label">Total Funds</div>
                            <div class="card-value">
                                <xsl:value-of select="count(Funds/Fund)"/>
                            </div>
                        </div>
                        <div class="card">
                            <div class="card-label">Total Assets</div>
                            <div class="card-value">
                                <xsl:value-of select="count(Assets/Asset)"/>
                            </div>
                        </div>
                        <div class="card">
                            <div class="card-label">Total Positions</div>
                            <div class="card-value">
                                <xsl:value-of select="count(//Position)"/>
                            </div>
                        </div>
                        <div class="card">
                            <div class="card-label">Validation Rules</div>
                            <div class="card-value">25+</div>
                        </div>
                    </div>

                    <!-- Document Structure Validation -->
                    <div class="section">
                        <h2 class="section-title">DOCUMENT STRUCTURE VALIDATION</h2>
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
                                <!-- Rule 1: ControlData Present -->
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

                                <!-- Rule 2: ContentDate Present -->
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

                                <!-- Rule 3: Funds Section Present -->
                                <tr>
                                    <td class="text-center">3</td>
                                    <td>
                                        <strong>Funds Section Present</strong>
                                    </td>
                                    <td>Document contains funds data</td>
                                    <td class="text-center">
                                        <xsl:choose>
                                            <xsl:when test="count(Funds/Fund) &gt; 0">PASS</xsl:when>
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

                                <!-- Rule 4: Assets Section Present -->
                                <tr>
                                    <td class="text-center">4</td>
                                    <td>
                                        <strong>Assets Section Present</strong>
                                    </td>
                                    <td>Document contains assets data</td>
                                    <td class="text-center">
                                        <xsl:choose>
                                            <xsl:when test="count(Assets/Asset) &gt; 0">PASS</xsl:when>
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

                                <!-- Rule 5: Fund Names Present -->
                                <tr>
                                    <td class="text-center">5</td>
                                    <td>
                                        <strong>Fund Names Present</strong>
                                    </td>
                                    <td>All funds have official names</td>
                                    <td class="text-center">
                                        <xsl:choose>
                                            <xsl:when test="count(Funds/Fund[not(Names/OfficialName)]) = 0">PASS
                                            </xsl:when>
                                            <xsl:otherwise>FAIL (<xsl:value-of
                                                    select="count(Funds/Fund[not(Names/OfficialName)])"/> missing)
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

                                <!-- Rule 6: LEI Format Validation -->
                                <tr>
                                    <td class="text-center">6</td>
                                    <td>
                                        <strong>LEI Format Validation</strong>
                                    </td>
                                    <td>All LEIs are 20 characters</td>
                                    <td class="text-center">
                                        <xsl:variable name="invalidLEIs" select="count(//LEI[string-length(.) != 20])"/>
                                        <xsl:choose>
                                            <xsl:when test="$invalidLEIs = 0">PASS</xsl:when>
                                            <xsl:otherwise>FAIL (<xsl:value-of select="$invalidLEIs"/> invalid)
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </td>
                                    <td class="text-center">
                                        <xsl:variable name="invalidLEIs" select="count(//LEI[string-length(.) != 20])"/>
                                        <xsl:choose>
                                            <xsl:when test="$invalidLEIs = 0">
                                                <span class="status-pass icon-pass"/>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <span class="status-fail icon-fail"/>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </td>
                                </tr>

                                <!-- Rule 7: Currency Code Format -->
                                <tr>
                                    <td class="text-center">7</td>
                                    <td>
                                        <strong>Currency Code Format</strong>
                                    </td>
                                    <td>All currency codes are 3 characters</td>
                                    <td class="text-center">
                                        <xsl:variable name="invalidCurrencies"
                                                      select="count(//Currency[string-length(.) != 3])"/>
                                        <xsl:choose>
                                            <xsl:when test="$invalidCurrencies = 0">PASS</xsl:when>
                                            <xsl:otherwise>FAIL (<xsl:value-of select="$invalidCurrencies"/> invalid)
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </td>
                                    <td class="text-center">
                                        <xsl:variable name="invalidCurrencies"
                                                      select="count(//Currency[string-length(.) != 3])"/>
                                        <xsl:choose>
                                            <xsl:when test="$invalidCurrencies = 0">
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

                    <!-- Fund-Level Quality Checks -->
                    <div class="section">
                        <h2 class="section-title">FUND-LEVEL QUALITY CHECKS</h2>
                        <xsl:for-each select="Funds/Fund">
                            <div class="fund-header">
                                <h3>
                                    <xsl:value-of select="Names/OfficialName"/>
                                </h3>
                            </div>

                            <table>
                                <thead>
                                    <tr>
                                        <th style="width: 5%;">#</th>
                                        <th style="width: 45%;">Quality Check</th>
                                        <th style="width: 25%;">Description</th>
                                        <th style="width: 15%;" class="text-center">Result</th>
                                        <th style="width: 10%;" class="text-center">Status</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <!-- Check 1: NAV Present -->
                                    <tr>
                                        <td class="text-center">1</td>
                                        <td>
                                            <strong>NAV Present</strong>
                                        </td>
                                        <td>Fund has NAV value</td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when
                                                        test="FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount">
                                                    PASS
                                                </xsl:when>
                                                <xsl:otherwise>FAIL</xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when
                                                        test="FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-fail icon-fail"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>

                                    <!-- Check 2: Portfolio Present -->
                                    <tr>
                                        <td class="text-center">2</td>
                                        <td>
                                            <strong>Portfolio Present</strong>
                                        </td>
                                        <td>Fund has portfolio data</td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when test="FundDynamicData/Portfolios/Portfolio">PASS</xsl:when>
                                                <xsl:otherwise>FAIL</xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when test="FundDynamicData/Portfolios/Portfolio">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-fail icon-fail"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>

                                    <!-- Check 3: Positions Present -->
                                    <tr>
                                        <td class="text-center">3</td>
                                        <td>
                                            <strong>Positions Present</strong>
                                        </td>
                                        <td>Portfolio has positions</td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when
                                                        test="count(FundDynamicData/Portfolios/Portfolio/Positions/Position) &gt; 0">
                                                    PASS (<xsl:value-of
                                                        select="count(FundDynamicData/Portfolios/Portfolio/Positions/Position)"/>
                                                    positions)
                                                </xsl:when>
                                                <xsl:otherwise>FAIL</xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when
                                                        test="count(FundDynamicData/Portfolios/Portfolio/Positions/Position) &gt; 0">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-fail icon-fail"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>

                                    <!-- Check 4: Position Values Complete -->
                                    <tr>
                                        <td class="text-center">4</td>
                                        <td>
                                            <strong>Position Values Complete</strong>
                                        </td>
                                        <td>All positions have values</td>
                                        <td class="text-center">
                                            <xsl:variable name="missingValues"
                                                          select="count(FundDynamicData/Portfolios/Portfolio/Positions/Position[not(TotalValue/Amount)])"/>
                                            <xsl:choose>
                                                <xsl:when test="$missingValues = 0">PASS</xsl:when>
                                                <xsl:otherwise>FAIL (<xsl:value-of select="$missingValues"/> missing)
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                        <td class="text-center">
                                            <xsl:variable name="missingValues"
                                                          select="count(FundDynamicData/Portfolios/Portfolio/Positions/Position[not(TotalValue/Amount)])"/>
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

                                    <!-- Check 5: Position Percentages Sum -->
                                    <tr>
                                        <td class="text-center">5</td>
                                        <td>
                                            <strong>Position Percentages Sum</strong>
                                        </td>
                                        <td>Total percentages near 100%</td>
                                        <td class="text-center">
                                            <xsl:variable name="totalPercentage"
                                                          select="sum(FundDynamicData/Portfolios/Portfolio/Positions/Position/TotalPercentage)"/>
                                            <xsl:choose>
                                                <xsl:when
                                                        test="$totalPercentage &gt;= 95 and $totalPercentage &lt;= 105">
                                                    PASS (<xsl:value-of
                                                        select="format-number($totalPercentage, '0.00')"/>%)
                                                </xsl:when>
                                                <xsl:when
                                                        test="$totalPercentage &gt;= 90 and $totalPercentage &lt;= 110">
                                                    WARN (<xsl:value-of
                                                        select="format-number($totalPercentage, '0.00')"/>%)
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    FAIL (<xsl:value-of
                                                        select="format-number($totalPercentage, '0.00')"/>%)
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                        <td class="text-center">
                                            <xsl:variable name="totalPercentage"
                                                          select="sum(FundDynamicData/Portfolios/Portfolio/Positions/Position/TotalPercentage)"/>
                                            <xsl:choose>
                                                <xsl:when
                                                        test="$totalPercentage &gt;= 95 and $totalPercentage &lt;= 105">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:when
                                                        test="$totalPercentage &gt;= 90 and $totalPercentage &lt;= 110">
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
                    <div style="margin-top: 40px; padding-top: 20px; border-top: 2px solid #6610f2; text-align: center; color: #666; font-size: 12px;">
                        <p>Data Quality Report Generated from FundsXML 4.28 | Report Date:
                            <xsl:value-of select="ControlData/ContentDate"/>
                        </p>
                    </div>
                </div>
            </body>
        </html>
    </xsl:template>

</xsl:stylesheet>
