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
                <title>Share Class Analysis Report</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body { font-family: Arial, Helvetica, sans-serif; font-size: 14px; line-height: 1.6; color: #333;
                    background: #f5f5f5; padding: 20px; }
                    .container { max-width: 1200px; margin: 0 auto; background: white; padding: 30px; box-shadow: 0 2px
                    10px rgba(0,0,0,0.1); }
                    .header { background: linear-gradient(135deg, #17a2b8 0%, #20c997 100%); color: white; padding:
                    40px; margin: -30px -30px 30px -30px; text-align: center; }
                    .header h1 { font-size: 36px; margin-bottom: 10px; }
                    .header p { font-size: 18px; opacity: 0.9; }
                    .summary-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap:
                    20px; margin-bottom: 30px; }
                    .card { background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%); border-left: 4px solid
                    #17a2b8; padding: 20px; border-radius: 5px; }
                    .card-label { font-size: 12px; color: #666; text-transform: uppercase; margin-bottom: 5px; }
                    .card-value { font-size: 28px; font-weight: bold; color: #17a2b8; }
                    .section { margin-bottom: 40px; }
                    .section-title { font-size: 24px; font-weight: bold; color: #495057; border-bottom: 3px solid
                    #17a2b8; padding-bottom: 10px; margin-bottom: 20px; }
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
                    .fund-header { background: #17a2b8; color: white; padding: 20px; margin: 20px 0 15px 0;
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
                        <h1>SHARE CLASS ANALYSIS</h1>
                        <p>Share Class Structure and Pricing Analysis</p>
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
                            <div class="card-label">Total Share Classes</div>
                            <div class="card-value">
                                <xsl:value-of select="count(//ShareClass)"/>
                            </div>
                        </div>
                        <div class="card">
                            <div class="card-label">With NAV Prices</div>
                            <div class="card-value">
                                <xsl:value-of select="count(//ShareClass[Prices/Price/NavPrice])"/>
                            </div>
                        </div>
                        <div class="card">
                            <div class="card-label">With ISINs</div>
                            <div class="card-value">
                                <xsl:value-of select="count(//ShareClass[Identifiers/ISIN])"/>
                            </div>
                        </div>
                    </div>

                    <!-- Data Quality Validation -->
                    <div class="section">
                        <h2 class="section-title">DATA QUALITY VALIDATION</h2>
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
                                <!-- Check 1: ISIN Format -->
                                <tr>
                                    <td class="text-center">1</td>
                                    <td>
                                        <strong>ISIN Format</strong>
                                    </td>
                                    <td>All ISINs are 12 characters</td>
                                    <td class="text-center">
                                        <xsl:variable name="invalidISINs"
                                                      select="count(//ShareClass/Identifiers/ISIN[string-length(.) != 12])"/>
                                        <xsl:choose>
                                            <xsl:when test="$invalidISINs = 0">PASS</xsl:when>
                                            <xsl:otherwise>FAIL (<xsl:value-of select="$invalidISINs"/> invalid)
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </td>
                                    <td class="text-center">
                                        <xsl:variable name="invalidISINs"
                                                      select="count(//ShareClass/Identifiers/ISIN[string-length(.) != 12])"/>
                                        <xsl:choose>
                                            <xsl:when test="$invalidISINs = 0">
                                                <span class="status-pass icon-pass"/>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <span class="status-fail icon-fail"/>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </td>
                                </tr>

                                <!-- Check 2: NAV Price Present -->
                                <tr>
                                    <td class="text-center">2</td>
                                    <td>
                                        <strong>NAV Price Present</strong>
                                    </td>
                                    <td>All share classes have NAV</td>
                                    <td class="text-center">
                                        <xsl:variable name="missingNAV"
                                                      select="count(//ShareClass[not(Prices/Price/NavPrice)])"/>
                                        <xsl:choose>
                                            <xsl:when test="$missingNAV = 0">PASS</xsl:when>
                                            <xsl:otherwise>FAIL (<xsl:value-of select="$missingNAV"/> missing)
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </td>
                                    <td class="text-center">
                                        <xsl:variable name="missingNAV"
                                                      select="count(//ShareClass[not(Prices/Price/NavPrice)])"/>
                                        <xsl:choose>
                                            <xsl:when test="$missingNAV = 0">
                                                <span class="status-pass icon-pass"/>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <span class="status-fail icon-fail"/>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </td>
                                </tr>

                                <!-- Check 3: Shares Outstanding Present -->
                                <tr>
                                    <td class="text-center">3</td>
                                    <td>
                                        <strong>Shares Outstanding Present</strong>
                                    </td>
                                    <td>All share classes have shares data</td>
                                    <td class="text-center">
                                        <xsl:variable name="missingShares"
                                                      select="count(//ShareClass[not(TotalAssetValues/TotalAssetValue/SharesOutstanding)])"/>
                                        <xsl:choose>
                                            <xsl:when test="$missingShares = 0">PASS</xsl:when>
                                            <xsl:otherwise>FAIL (<xsl:value-of select="$missingShares"/> missing)
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </td>
                                    <td class="text-center">
                                        <xsl:variable name="missingShares"
                                                      select="count(//ShareClass[not(TotalAssetValues/TotalAssetValue/SharesOutstanding)])"/>
                                        <xsl:choose>
                                            <xsl:when test="$missingShares = 0">
                                                <span class="status-pass icon-pass"/>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <span class="status-fail icon-fail"/>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </td>
                                </tr>

                                <!-- Check 4: TNA Present -->
                                <tr>
                                    <td class="text-center">4</td>
                                    <td>
                                        <strong>TNA Present</strong>
                                    </td>
                                    <td>All share classes have TNA</td>
                                    <td class="text-center">
                                        <xsl:variable name="missingTNA"
                                                      select="count(//ShareClass[not(TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount)])"/>
                                        <xsl:choose>
                                            <xsl:when test="$missingTNA = 0">PASS</xsl:when>
                                            <xsl:otherwise>FAIL (<xsl:value-of select="$missingTNA"/> missing)
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </td>
                                    <td class="text-center">
                                        <xsl:variable name="missingTNA"
                                                      select="count(//ShareClass[not(TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount)])"/>
                                        <xsl:choose>
                                            <xsl:when test="$missingTNA = 0">
                                                <span class="status-pass icon-pass"/>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <span class="status-fail icon-fail"/>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </td>
                                </tr>

                                <!-- Check 5: NAV × Shares ≈ TNA -->
                                <tr>
                                    <td class="text-center">5</td>
                                    <td>
                                        <strong>NAV × Shares ≈ TNA</strong>
                                    </td>
                                    <td>Calculated TNA matches reported</td>
                                    <td class="text-center">
                                        <xsl:variable name="mismatchCount">
                                            <xsl:value-of select="count(//ShareClass[
                                                Prices/Price/NavPrice * TotalAssetValues/TotalAssetValue/SharesOutstanding
                                                &lt; TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount * 0.95 or
                                                Prices/Price/NavPrice * TotalAssetValues/TotalAssetValue/SharesOutstanding
                                                &gt; TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount * 1.05
                                            ])"/>
                                        </xsl:variable>
                                        <xsl:choose>
                                            <xsl:when test="$mismatchCount = 0">PASS</xsl:when>
                                            <xsl:otherwise>WARN (<xsl:value-of select="$mismatchCount"/> mismatches)
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </td>
                                    <td class="text-center">
                                        <xsl:variable name="mismatchCount" select="count(//ShareClass[
                                            Prices/Price/NavPrice * TotalAssetValues/TotalAssetValue/SharesOutstanding
                                            &lt; TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount * 0.95 or
                                            Prices/Price/NavPrice * TotalAssetValues/TotalAssetValue/SharesOutstanding
                                            &gt; TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount * 1.05
                                        ])"/>
                                        <xsl:choose>
                                            <xsl:when test="$mismatchCount = 0">
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

                    <!-- Share Class Details per Fund -->
                    <div class="section">
                        <h2 class="section-title">SHARE CLASS DETAILS</h2>
                        <xsl:for-each select="Funds/Fund">
                            <xsl:if test="FundDynamicData/ShareClasses/ShareClass">
                                <div class="fund-header">
                                    <h3>
                                        <xsl:value-of select="Names/OfficialName"/>
                                    </h3>
                                    <p style="font-size: 14px; margin-top: 5px;">
                                        Share Classes:
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
                                                <td class="monospace">
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
                            </xsl:if>
                        </xsl:for-each>
                    </div>

                    <!-- Footer -->
                    <div style="margin-top: 40px; padding-top: 20px; border-top: 2px solid #17a2b8; text-align: center; color: #666; font-size: 12px;">
                        <p>Share Class Analysis Report Generated from FundsXML 4.28 | Report Date:
                            <xsl:value-of select="ControlData/ContentDate"/>
                        </p>
                    </div>
                </div>
            </body>
        </html>
    </xsl:template>

</xsl:stylesheet>
