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
                <title>Exposure Analysis Report</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body { font-family: Arial, Helvetica, sans-serif; font-size: 14px; line-height: 1.6; color: #333;
                    background: #f5f5f5; padding: 20px; }
                    .container { max-width: 1200px; margin: 0 auto; background: white; padding: 30px; box-shadow: 0 2px
                    10px rgba(0,0,0,0.1); }
                    .header { background: linear-gradient(135deg, #ff5722 0%, #ff7043 100%); color: white; padding:
                    40px; margin: -30px -30px 30px -30px; text-align: center; }
                    .header h1 { font-size: 36px; margin-bottom: 10px; }
                    .header p { font-size: 18px; opacity: 0.9; }
                    .summary-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap:
                    20px; margin-bottom: 30px; }
                    .card { background: linear-gradient(135deg, #ffe8e0 0%, #ffccbc 100%); border-left: 4px solid
                    #ff5722; padding: 20px; border-radius: 5px; }
                    .card-label { font-size: 12px; color: #666; text-transform: uppercase; margin-bottom: 5px; }
                    .card-value { font-size: 28px; font-weight: bold; color: #ff5722; }
                    .section { margin-bottom: 40px; }
                    .section-title { font-size: 24px; font-weight: bold; color: #495057; border-bottom: 3px solid
                    #ff5722; padding-bottom: 10px; margin-bottom: 20px; }
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
                    .fund-header { background: #ff5722; color: white; padding: 20px; margin: 20px 0 15px 0;
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
                        <h1>EXPOSURE ANALYSIS REPORT</h1>
                        <p>Derivatives and Market Exposure Assessment</p>
                    </div>

                    <!-- Summary Cards -->
                    <div class="summary-cards">
                        <div class="card">
                            <div class="card-label">Positions with Exposure</div>
                            <div class="card-value">
                                <xsl:value-of select="count(//Position[Exposures/Exposure])"/>
                            </div>
                        </div>
                        <div class="card">
                            <div class="card-label">Total Exposures</div>
                            <div class="card-value">
                                <xsl:value-of select="count(//Exposure)"/>
                            </div>
                        </div>
                        <div class="card">
                            <div class="card-label">Exposure Types</div>
                            <div class="card-value">
                                <xsl:value-of select="count(//Exposure/Type[not(. = preceding::Exposure/Type)])"/>
                            </div>
                        </div>
                        <div class="card">
                            <div class="card-label">Total Funds</div>
                            <div class="card-value">
                                <xsl:value-of select="count(Funds/Fund)"/>
                            </div>
                        </div>
                    </div>

                    <!-- Exposure Data Quality -->
                    <div class="section">
                        <h2 class="section-title">EXPOSURE DATA QUALITY</h2>
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
                                <!-- DQ Check 1 -->
                                <tr>
                                    <td class="text-center">1</td>
                                    <td>
                                        <strong>Exposure Type Present</strong>
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

                                <!-- DQ Check 2 -->
                                <tr>
                                    <td class="text-center">2</td>
                                    <td>
                                        <strong>Exposure Value Present</strong>
                                    </td>
                                    <td>All exposures have value</td>
                                    <td class="text-center">
                                        <xsl:choose>
                                            <xsl:when test="count(//Exposure[not(Value)]) = 0">PASS</xsl:when>
                                            <xsl:otherwise>FAIL</xsl:otherwise>
                                        </xsl:choose>
                                    </td>
                                    <td class="text-center">
                                        <xsl:choose>
                                            <xsl:when test="count(//Exposure[not(Value)]) = 0">
                                                <span class="status-pass icon-pass"/>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <span class="status-fail icon-fail"/>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </td>
                                </tr>

                                <!-- DQ Check 3 -->
                                <tr>
                                    <td class="text-center">3</td>
                                    <td>
                                        <strong>Positive Exposure Values</strong>
                                    </td>
                                    <td>Exposure values are valid</td>
                                    <td class="text-center">
                                        <xsl:choose>
                                            <xsl:when test="count(//Exposure/Value/Amount[. &lt; 0]) = 0">PASS
                                            </xsl:when>
                                            <xsl:otherwise>WARN</xsl:otherwise>
                                        </xsl:choose>
                                    </td>
                                    <td class="text-center">
                                        <xsl:choose>
                                            <xsl:when test="count(//Exposure/Value/Amount[. &lt; 0]) = 0">
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

                    <!-- Detailed Exposure Analysis per Fund -->
                    <div class="section">
                        <h2 class="section-title">EXPOSURE ANALYSIS PER FUND</h2>
                        <xsl:for-each select="Funds/Fund">
                            <xsl:if test="FundDynamicData/Portfolios/Portfolio/Positions/Position/Exposures/Exposure">
                                <div class="fund-header">
                                    <h3>
                                        <xsl:value-of select="Names/OfficialName"/>
                                    </h3>
                                </div>

                                <table>
                                    <thead>
                                        <tr>
                                            <th style="width: 50%;">Exposure Type</th>
                                            <th style="width: 25%;" class="text-right">Total Value</th>
                                            <th style="width: 25%;" class="text-center">Positions</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        <xsl:for-each
                                                select="FundDynamicData/Portfolios/Portfolio/Positions/Position/Exposures/Exposure/Type[not(. = preceding::Type)]">
                                            <xsl:variable name="exposureType" select="."/>
                                            <xsl:variable name="totalExposure"
                                                          select="sum(../../../../Position/Exposures/Exposure[Type = $exposureType]/Value/Amount)"/>
                                            <xsl:variable name="positionCount"
                                                          select="count(../../../../Position[Exposures/Exposure/Type = $exposureType])"/>

                                            <tr>
                                                <td>
                                                    <xsl:value-of select="$exposureType"/>
                                                </td>
                                                <td class="text-right monospace">
                                                    <xsl:value-of select="format-number($totalExposure, '#,##0.00')"/>
                                                </td>
                                                <td class="text-center">
                                                    <xsl:value-of select="$positionCount"/>
                                                </td>
                                            </tr>
                                        </xsl:for-each>
                                    </tbody>
                                </table>
                            </xsl:if>
                        </xsl:for-each>
                    </div>

                    <!-- Footer -->
                    <div style="margin-top: 40px; padding-top: 20px; border-top: 2px solid #ff5722; text-align: center; color: #666; font-size: 12px;">
                        <p>Exposure Analysis Report Generated from FundsXML 4.28 | Report Date:
                            <xsl:value-of select="ControlData/ContentDate"/>
                        </p>
                    </div>
                </div>
            </body>
        </html>
    </xsl:template>

</xsl:stylesheet>
