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
                <title>Currency Exposure Analysis</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body { font-family: Arial, Helvetica, sans-serif; font-size: 14px; line-height: 1.6; color: #333;
                    background: #f5f5f5; padding: 20px; }
                    .container { max-width: 1200px; margin: 0 auto; background: white; padding: 30px; box-shadow: 0 2px
                    10px rgba(0,0,0,0.1); }
                    .header { background: linear-gradient(135deg, #6c757d 0%, #868e96 100%); color: white; padding:
                    40px; margin: -30px -30px 30px -30px; text-align: center; }
                    .header h1 { font-size: 36px; margin-bottom: 10px; }
                    .header p { font-size: 18px; opacity: 0.9; }
                    .header .stats { font-size: 14px; margin-top: 15px; opacity: 0.8; }
                    .summary-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap:
                    20px; margin-bottom: 30px; }
                    .card { background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%); border-left: 4px solid
                    #6c757d; padding: 20px; border-radius: 5px; }
                    .card-label { font-size: 12px; color: #666; text-transform: uppercase; margin-bottom: 5px; }
                    .card-value { font-size: 28px; font-weight: bold; color: #6c757d; }
                    .section { margin-bottom: 40px; }
                    .section-title { font-size: 24px; font-weight: bold; color: #495057; border-bottom: 3px solid
                    #6c757d; padding-bottom: 10px; margin-bottom: 20px; }
                    table { width: 100%; border-collapse: collapse; margin-bottom: 20px; }
                    th { background: #f8f9fa; padding: 12px; text-align: left; font-weight: bold; border: 1px solid
                    #dee2e6; }
                    td { padding: 10px; border: 1px solid #dee2e6; }
                    tr:nth-child(even) { background: #f8f9fa; }
                    .status-pass { color: #28a745; font-weight: bold; }
                    .status-warn { color: #ffc107; font-weight: bold; }
                    .status-fail { color: #dc3545; font-weight: bold; }
                    .risk-base { color: #28a745; font-weight: bold; }
                    .risk-low { color: #28a745; font-weight: bold; }
                    .risk-med { color: #ffc107; font-weight: bold; }
                    .risk-high { color: #dc3545; font-weight: bold; }
                    .icon-pass::before { content: '✓'; margin-right: 5px; }
                    .icon-warn::before { content: '!'; margin-right: 5px; }
                    .icon-fail::before { content: '✗'; margin-right: 5px; }
                    .fund-header { background: #6c757d; color: white; padding: 20px; margin: 20px 0 15px 0;
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
                        <h1>CURRENCY EXPOSURE ANALYSIS</h1>
                        <p>Foreign Exchange Exposure and Risk Assessment</p>
                        <div class="stats">
                            Currencies:
                            <xsl:value-of select="count(//Position/Currency[not(. = preceding::Position/Currency)])"/> •
                            FX Rates:
                            <xsl:value-of select="count(//FXRate)"/>
                        </div>
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
                            <div class="card-label">Unique Currencies</div>
                            <div class="card-value">
                                <xsl:value-of
                                        select="count(//Position/Currency[not(. = preceding::Position/Currency)])"/>
                            </div>
                        </div>
                        <div class="card">
                            <div class="card-label">Total Positions</div>
                            <div class="card-value">
                                <xsl:value-of select="count(//Position)"/>
                            </div>
                        </div>
                        <div class="card">
                            <div class="card-label">FX Rates Defined</div>
                            <div class="card-value">
                                <xsl:value-of select="count(//FXRate)"/>
                            </div>
                        </div>
                    </div>

                    <!-- Currency Distribution per Fund -->
                    <div class="section">
                        <h2 class="section-title">CURRENCY DISTRIBUTION PER FUND</h2>
                        <xsl:for-each select="Funds/Fund">
                            <xsl:variable name="fundCurrency" select="Currency"/>

                            <div class="fund-header">
                                <h3>
                                    <xsl:value-of select="Names/OfficialName"/>
                                </h3>
                                <p style="font-size: 14px; margin-top: 5px;">
                                    Base Currency:
                                    <strong>
                                        <xsl:value-of select="$fundCurrency"/>
                                    </strong>
                                </p>
                            </div>

                            <table>
                                <thead>
                                    <tr>
                                        <th style="width: 20%;">Currency</th>
                                        <th style="width: 25%;" class="text-right">Total Value</th>
                                        <th style="width: 20%;" class="text-right">Percentage</th>
                                        <th style="width: 15%;" class="text-center">Positions</th>
                                        <th style="width: 20%;" class="text-center">Risk Level</th>
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
                                            <td class="text-right monospace">
                                                <xsl:value-of select="format-number($percentage, '0.00')"/>%
                                            </td>
                                            <td class="text-center">
                                                <xsl:value-of select="$positionCount"/>
                                            </td>
                                            <td class="text-center">
                                                <xsl:choose>
                                                    <xsl:when test="$currency = $fundCurrency">
                                                        <span class="risk-base">BASE</span>
                                                    </xsl:when>
                                                    <xsl:when test="$percentage &lt; 10">
                                                        <span class="risk-low">LOW</span>
                                                    </xsl:when>
                                                    <xsl:when test="$percentage &lt; 25">
                                                        <span class="risk-med">MED</span>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <span class="risk-high">HIGH</span>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </td>
                                        </tr>
                                    </xsl:for-each>
                                </tbody>
                            </table>

                            <!-- FX Rates Data Quality -->
                            <h3 style="font-size: 18px; font-weight: bold; margin: 20px 0 10px 0;">FX Rates Data
                                Quality
                            </h3>
                            <table>
                                <thead>
                                    <tr>
                                        <th style="width: 40%;">Quality Check</th>
                                        <th style="width: 30%;">Description</th>
                                        <th style="width: 20%;" class="text-center">Result</th>
                                        <th style="width: 10%;" class="text-center">Status</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <tr>
                                        <td>
                                            <strong>FX Rates Present</strong>
                                        </td>
                                        <td>All positions have FX rates</td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when
                                                        test="count(FundDynamicData/Portfolios/Portfolio/Positions/Position[not(FXRates/FXRate)]) = 0">
                                                    PASS
                                                </xsl:when>
                                                <xsl:otherwise>FAIL</xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when
                                                        test="count(FundDynamicData/Portfolios/Portfolio/Positions/Position[not(FXRates/FXRate)]) = 0">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-fail icon-fail"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>

                                    <tr>
                                        <td>
                                            <strong>Valid FX Rate Values</strong>
                                        </td>
                                        <td>All rates are positive</td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when
                                                        test="count(FundDynamicData/Portfolios/Portfolio/Positions/Position/FXRates/FXRate[. &lt;= 0]) = 0">
                                                    PASS
                                                </xsl:when>
                                                <xsl:otherwise>FAIL</xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when
                                                        test="count(FundDynamicData/Portfolios/Portfolio/Positions/Position/FXRates/FXRate[. &lt;= 0]) = 0">
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
                        </xsl:for-each>
                    </div>

                    <!-- Footer -->
                    <div style="margin-top: 40px; padding-top: 20px; border-top: 2px solid #6c757d; text-align: center; color: #666; font-size: 12px;">
                        <p>Currency Exposure Analysis Report Generated from FundsXML 4.28 | Report Date:
                            <xsl:value-of select="ControlData/ContentDate"/>
                        </p>
                    </div>
                </div>
            </body>
        </html>
    </xsl:template>

</xsl:stylesheet>
