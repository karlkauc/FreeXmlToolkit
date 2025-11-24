<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="html" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <xsl:key name="asset-by-id" match="Asset" use="UniqueID"/>
    <xsl:key name="asset-by-type" match="Asset" use="AssetType"/>

    <xsl:template match="/FundsXML4">
        <html>
            <head>
                <meta charset="UTF-8"/>
                <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                <title>Portfolio Composition Analysis</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body { font-family: Arial, Helvetica, sans-serif; font-size: 14px; line-height: 1.6; color: #333;
                    background: #f5f5f5; padding: 20px; }
                    .container { max-width: 1200px; margin: 0 auto; background: white; padding: 30px; box-shadow: 0 2px
                    10px rgba(0,0,0,0.1); }
                    .header { background: linear-gradient(135deg, #9c27b0 0%, #ba68c8 100%); color: white; padding:
                    40px; margin: -30px -30px 30px -30px; text-align: center; }
                    .header h1 { font-size: 36px; margin-bottom: 10px; }
                    .header p { font-size: 18px; opacity: 0.9; }
                    .fund-header { background: #9c27b0; color: white; padding: 20px; margin: 30px 0 20px 0;
                    border-radius: 5px; }
                    .fund-header h3 { font-size: 20px; margin-bottom: 5px; }
                    .section { margin-bottom: 40px; }
                    .section-title { font-size: 20px; font-weight: bold; color: #495057; margin: 20px 0 15px 0; }
                    table { width: 100%; border-collapse: collapse; margin-bottom: 25px; }
                    th { background: #f3e5f5; padding: 12px; text-align: left; font-weight: bold; border: 1px solid
                    #dee2e6; }
                    td { padding: 10px; border: 1px solid #dee2e6; }
                    tr:nth-child(even) { background: #f8f9fa; }
                    .monospace { font-family: 'Courier New', monospace; }
                    .text-right { text-align: right; }
                    .text-center { text-align: center; }
                    .rank-badge { background: #9c27b0; color: white; padding: 3px 8px; border-radius: 3px; font-weight:
                    bold; }
                    .percentage-highlight { color: #9c27b0; font-weight: bold; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>PORTFOLIO COMPOSITION ANALYSIS</h1>
                        <p>Detailed Holdings and Asset Allocation</p>
                    </div>

                    <xsl:for-each select="Funds/Fund">
                        <div class="section">
                            <div class="fund-header">
                                <h3>
                                    <xsl:value-of select="Names/OfficialName"/>
                                </h3>
                                <p style="font-size: 14px; margin-top: 5px;">
                                    Total Positions:
                                    <strong>
                                        <xsl:value-of
                                                select="count(FundDynamicData/Portfolios/Portfolio/Positions/Position)"/>
                                    </strong>
                                </p>
                            </div>

                            <!-- Asset Type Allocation -->
                            <div class="section-title">Asset Type Allocation</div>
                            <table>
                                <thead>
                                    <tr>
                                        <th style="width: 35%;">Asset Type</th>
                                        <th style="width: 25%;" class="text-right">Total Value</th>
                                        <th style="width: 20%;" class="text-right">Percentage</th>
                                        <th style="width: 20%;" class="text-center">Count</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <xsl:for-each select="FundDynamicData/Portfolios/Portfolio/Positions/Position">
                                        <xsl:variable name="assetID" select="UniqueID"/>
                                        <xsl:variable name="assetType" select="key('asset-by-id', $assetID)/AssetType"/>

                                        <xsl:if test="not($assetType = preceding-sibling::Position/key('asset-by-id', UniqueID)/AssetType)">
                                            <xsl:variable name="typeTotal"
                                                          select="sum(../Position[key('asset-by-id', UniqueID)/AssetType = $assetType]/TotalValue/Amount)"/>
                                            <xsl:variable name="typePercentage"
                                                          select="sum(../Position[key('asset-by-id', UniqueID)/AssetType = $assetType]/TotalPercentage)"/>
                                            <xsl:variable name="typeCount"
                                                          select="count(../Position[key('asset-by-id', UniqueID)/AssetType = $assetType])"/>

                                            <tr>
                                                <td>
                                                    <xsl:choose>
                                                        <xsl:when test="$assetType">
                                                            <xsl:value-of select="$assetType"/>
                                                        </xsl:when>
                                                        <xsl:otherwise>Unknown</xsl:otherwise>
                                                    </xsl:choose>
                                                </td>
                                                <td class="text-right monospace">
                                                    <xsl:value-of select="format-number($typeTotal, '#,##0.00')"/>
                                                </td>
                                                <td class="text-right percentage-highlight">
                                                    <xsl:value-of select="format-number($typePercentage, '0.00')"/>%
                                                </td>
                                                <td class="text-center">
                                                    <xsl:value-of select="$typeCount"/>
                                                </td>
                                            </tr>
                                        </xsl:if>
                                    </xsl:for-each>
                                </tbody>
                            </table>

                            <!-- Top 20 Holdings -->
                            <div class="section-title">Top 20 Holdings</div>
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
                                        <xsl:if test="position() &lt;= 20">
                                            <xsl:variable name="assetID" select="UniqueID"/>
                                            <tr>
                                                <td class="text-center">
                                                    <span class="rank-badge">
                                                        <xsl:value-of select="position()"/>
                                                    </span>
                                                </td>
                                                <td>
                                                    <xsl:value-of select="key('asset-by-id', $assetID)/Name"/>
                                                </td>
                                                <td class="monospace" style="font-size: 11px;">
                                                    <xsl:value-of
                                                            select="key('asset-by-id', $assetID)/Identifiers/ISIN"/>
                                                </td>
                                                <td style="font-size: 11px;">
                                                    <xsl:value-of select="key('asset-by-id', $assetID)/AssetType"/>
                                                </td>
                                                <td class="text-right monospace">
                                                    <xsl:value-of
                                                            select="format-number(TotalValue/Amount, '#,##0.00')"/>
                                                </td>
                                                <td class="text-right percentage-highlight">
                                                    <xsl:value-of select="format-number(TotalPercentage, '0.00')"/>%
                                                </td>
                                            </tr>
                                        </xsl:if>
                                    </xsl:for-each>
                                </tbody>
                            </table>
                        </div>
                    </xsl:for-each>

                    <!-- Footer -->
                    <div style="margin-top: 40px; padding-top: 20px; border-top: 2px solid #9c27b0; text-align: center; color: #666; font-size: 12px;">
                        <p>Portfolio Composition Report Generated from FundsXML 4.28 | Report Date:
                            <xsl:value-of select="ControlData/ContentDate"/>
                        </p>
                    </div>
                </div>
            </body>
        </html>
    </xsl:template>

</xsl:stylesheet>
