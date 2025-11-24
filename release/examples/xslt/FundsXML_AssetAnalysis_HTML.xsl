<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="html" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <xsl:key name="asset-by-type" match="Asset" use="AssetType"/>
    <xsl:key name="asset-by-id" match="Asset" use="UniqueID"/>

    <xsl:template match="/FundsXML4">
        <html>
            <head>
                <meta charset="UTF-8"/>
                <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                <title>Asset Analysis Report</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body { font-family: Arial, Helvetica, sans-serif; font-size: 14px; line-height: 1.6; color: #333;
                    background: #f5f5f5; padding: 20px; }
                    .container { max-width: 1200px; margin: 0 auto; background: white; padding: 30px; box-shadow: 0 2px
                    10px rgba(0,0,0,0.1); }
                    .header { background: linear-gradient(135deg, #20c997 0%, #3dd5ae 100%); color: white; padding:
                    40px; margin: -30px -30px 30px -30px; text-align: center; }
                    .header h1 { font-size: 36px; margin-bottom: 10px; }
                    .header p { font-size: 18px; opacity: 0.9; }
                    .summary-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap:
                    20px; margin-bottom: 30px; }
                    .card { background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%); border-left: 4px solid
                    #20c997; padding: 20px; border-radius: 5px; }
                    .card-label { font-size: 12px; color: #666; text-transform: uppercase; margin-bottom: 5px; }
                    .card-value { font-size: 28px; font-weight: bold; color: #20c997; }
                    .section { margin-bottom: 40px; }
                    .section-title { font-size: 24px; font-weight: bold; color: #495057; border-bottom: 3px solid
                    #20c997; padding-bottom: 10px; margin-bottom: 20px; }
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
                    .monospace { font-family: 'Courier New', monospace; }
                    .text-right { text-align: right; }
                    .text-center { text-align: center; }
                    .progress-bar { width: 100%; height: 20px; background: #e9ecef; border-radius: 10px; overflow:
                    hidden; }
                    .progress-fill { height: 100%; background: linear-gradient(90deg, #20c997 0%, #3dd5ae 100%);
                    transition: width 0.3s; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>ASSET ANALYSIS REPORT</h1>
                        <p>Asset Type Distribution and Quality Analysis</p>
                    </div>

                    <!-- Summary Cards -->
                    <div class="summary-cards">
                        <div class="card">
                            <div class="card-label">Total Assets</div>
                            <div class="card-value">
                                <xsl:value-of select="count(Assets/Asset)"/>
                            </div>
                        </div>
                        <div class="card">
                            <div class="card-label">Asset Types</div>
                            <div class="card-value">
                                <xsl:value-of select="count(Assets/Asset/AssetType[not(. = preceding::AssetType)])"/>
                            </div>
                        </div>
                        <div class="card">
                            <div class="card-label">Total Positions</div>
                            <div class="card-value">
                                <xsl:value-of select="count(//Position)"/>
                            </div>
                        </div>
                        <div class="card">
                            <div class="card-label">Identifier Coverage</div>
                            <div class="card-value">
                                <xsl:value-of
                                        select="format-number(count(Assets/Asset[Identifiers/ISIN]) div count(Assets/Asset) * 100, '0')"/>%
                            </div>
                        </div>
                    </div>

                    <!-- Asset Type Distribution -->
                    <div class="section">
                        <h2 class="section-title">ASSET TYPE DISTRIBUTION</h2>
                        <table>
                            <thead>
                                <tr>
                                    <th style="width: 5%;">Rank</th>
                                    <th style="width: 35%;">Asset Type</th>
                                    <th style="width: 15%;" class="text-center">Count</th>
                                    <th style="width: 15%;" class="text-center">Percentage</th>
                                    <th style="width: 30%;">Distribution</th>
                                </tr>
                            </thead>
                            <tbody>
                                <xsl:for-each select="Assets/Asset/AssetType[not(. = preceding::AssetType)]">
                                    <xsl:sort select="count(key('asset-by-type', .))" data-type="number"
                                              order="descending"/>
                                    <xsl:variable name="assetType" select="."/>
                                    <xsl:variable name="assetCount" select="count(key('asset-by-type', $assetType))"/>
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
                    </div>

                    <!-- Identifier Completeness -->
                    <div class="section">
                        <h2 class="section-title">IDENTIFIER COMPLETENESS</h2>
                        <table>
                            <thead>
                                <tr>
                                    <th style="width: 5%;">#</th>
                                    <th style="width: 30%;">Identifier Type</th>
                                    <th style="width: 20%;">Description</th>
                                    <th style="width: 15%;" class="text-center">Present</th>
                                    <th style="width: 15%;" class="text-center">Coverage</th>
                                    <th style="width: 15%;" class="text-center">Status</th>
                                </tr>
                            </thead>
                            <tbody>
                                <!-- ISIN -->
                                <tr>
                                    <td class="text-center">1</td>
                                    <td>
                                        <strong>ISIN</strong>
                                    </td>
                                    <td>International Securities ID</td>
                                    <td class="text-center monospace">
                                        <xsl:value-of select="count(Assets/Asset[Identifiers/ISIN])"/>
                                    </td>
                                    <td class="text-center">
                                        <xsl:value-of
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

                                <!-- SEDOL -->
                                <tr>
                                    <td class="text-center">2</td>
                                    <td>
                                        <strong>SEDOL</strong>
                                    </td>
                                    <td>Stock Exchange Daily Official List</td>
                                    <td class="text-center monospace">
                                        <xsl:value-of select="count(Assets/Asset[Identifiers/SEDOL])"/>
                                    </td>
                                    <td class="text-center">
                                        <xsl:value-of
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

                                <!-- WKN -->
                                <tr>
                                    <td class="text-center">3</td>
                                    <td>
                                        <strong>WKN</strong>
                                    </td>
                                    <td>Wertpapierkennnummer (German)</td>
                                    <td class="text-center monospace">
                                        <xsl:value-of select="count(Assets/Asset[Identifiers/WKN])"/>
                                    </td>
                                    <td class="text-center">
                                        <xsl:value-of
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

                                <!-- Ticker -->
                                <tr>
                                    <td class="text-center">4</td>
                                    <td>
                                        <strong>Ticker</strong>
                                    </td>
                                    <td>Trading Symbol</td>
                                    <td class="text-center monospace">
                                        <xsl:value-of select="count(Assets/Asset[Identifiers/Ticker])"/>
                                    </td>
                                    <td class="text-center">
                                        <xsl:value-of
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
                    </div>

                    <!-- Orphaned Positions Check -->
                    <div class="section">
                        <h2 class="section-title">POSITION-ASSET LINKAGE</h2>
                        <table>
                            <thead>
                                <tr>
                                    <th style="width: 10%;">#</th>
                                    <th style="width: 45%;">Quality Check</th>
                                    <th style="width: 25%;" class="text-center">Result</th>
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
                                        <xsl:variable name="orphanedCount">
                                            <xsl:value-of
                                                    select="count(//Position[not(key('asset-by-id', UniqueID))])"/>
                                        </xsl:variable>
                                        <xsl:choose>
                                            <xsl:when test="$orphanedCount = 0">
                                                No orphaned positions
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <xsl:value-of select="$orphanedCount"/> positions without assets
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </td>
                                    <td class="text-center">
                                        <xsl:variable name="orphanedCount"
                                                      select="count(//Position[not(key('asset-by-id', UniqueID))])"/>
                                        <xsl:choose>
                                            <xsl:when test="$orphanedCount = 0">
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
                                        <xsl:variable name="unusedCount">
                                            <xsl:value-of
                                                    select="count(Assets/Asset[not(UniqueID = //Position/UniqueID)])"/>
                                        </xsl:variable>
                                        <xsl:choose>
                                            <xsl:when test="$unusedCount = 0">
                                                All assets referenced
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <xsl:value-of select="$unusedCount"/> assets not used
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </td>
                                    <td class="text-center">
                                        <xsl:variable name="unusedCount"
                                                      select="count(Assets/Asset[not(UniqueID = //Position/UniqueID)])"/>
                                        <xsl:choose>
                                            <xsl:when test="$unusedCount = 0">
                                                <span class="status-pass icon-pass"/>
                                            </xsl:when>
                                            <xsl:when test="$unusedCount &lt; 10">
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

                    <!-- Footer -->
                    <div style="margin-top: 40px; padding-top: 20px; border-top: 2px solid #20c997; text-align: center; color: #666; font-size: 12px;">
                        <p>Asset Analysis Report Generated from FundsXML 4.28 | Report Date:
                            <xsl:value-of select="ControlData/ContentDate"/>
                        </p>
                    </div>
                </div>
            </body>
        </html>
    </xsl:template>

</xsl:stylesheet>
