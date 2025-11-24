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
                <title>Temporal Consistency Report</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body { font-family: Arial, Helvetica, sans-serif; font-size: 14px; line-height: 1.6; color: #333;
                    background: #f5f5f5; padding: 20px; }
                    .container { max-width: 1200px; margin: 0 auto; background: white; padding: 30px; box-shadow: 0 2px
                    10px rgba(0,0,0,0.1); }
                    .header { background: linear-gradient(135deg, #fd7e14 0%, #ff922b 100%); color: white; padding:
                    40px; margin: -30px -30px 30px -30px; text-align: center; }
                    .header h1 { font-size: 36px; margin-bottom: 10px; }
                    .header p { font-size: 18px; opacity: 0.9; }
                    .summary-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap:
                    20px; margin-bottom: 30px; }
                    .card { background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%); border-left: 4px solid
                    #fd7e14; padding: 20px; border-radius: 5px; }
                    .card-label { font-size: 12px; color: #666; text-transform: uppercase; margin-bottom: 5px; }
                    .card-value { font-size: 28px; font-weight: bold; color: #fd7e14; }
                    .section { margin-bottom: 40px; }
                    .section-title { font-size: 24px; font-weight: bold; color: #495057; border-bottom: 3px solid
                    #fd7e14; padding-bottom: 10px; margin-bottom: 20px; }
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
                    .fund-header { background: #fd7e14; color: white; padding: 20px; margin: 20px 0 15px 0;
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
                        <h1>TEMPORAL CONSISTENCY REPORT</h1>
                        <p>Date and Time Consistency Validation</p>
                    </div>

                    <!-- Summary Cards -->
                    <div class="summary-cards">
                        <div class="card">
                            <div class="card-label">Content Date</div>
                            <div class="card-value" style="font-size: 18px;">
                                <xsl:value-of select="ControlData/ContentDate"/>
                            </div>
                        </div>
                        <div class="card">
                            <div class="card-label">Document Generated</div>
                            <div class="card-value" style="font-size: 18px;">
                                <xsl:value-of select="substring(ControlData/DocumentGenerated, 1, 10)"/>
                            </div>
                        </div>
                        <div class="card">
                            <div class="card-label">Total Funds</div>
                            <div class="card-value">
                                <xsl:value-of select="count(Funds/Fund)"/>
                            </div>
                        </div>
                        <div class="card">
                            <div class="card-label">NAV Dates</div>
                            <div class="card-value">
                                <xsl:value-of select="count(//NavDate[not(. = preceding::NavDate)])"/>
                            </div>
                        </div>
                    </div>

                    <!-- Document-Level Date Validation -->
                    <div class="section">
                        <h2 class="section-title">DOCUMENT-LEVEL DATE VALIDATION</h2>
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
                                <!-- Rule 1: ContentDate Format -->
                                <tr>
                                    <td class="text-center">1</td>
                                    <td>
                                        <strong>ContentDate Format</strong>
                                    </td>
                                    <td>YYYY-MM-DD format</td>
                                    <td class="text-center">
                                        <xsl:choose>
                                            <xsl:when test="string-length(ControlData/ContentDate) = 10">PASS</xsl:when>
                                            <xsl:otherwise>FAIL</xsl:otherwise>
                                        </xsl:choose>
                                    </td>
                                    <td class="text-center">
                                        <xsl:choose>
                                            <xsl:when test="string-length(ControlData/ContentDate) = 10">
                                                <span class="status-pass icon-pass"/>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <span class="status-fail icon-fail"/>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </td>
                                </tr>

                                <!-- Rule 2: DocumentGenerated Present -->
                                <tr>
                                    <td class="text-center">2</td>
                                    <td>
                                        <strong>DocumentGenerated Present</strong>
                                    </td>
                                    <td>Document generation timestamp exists</td>
                                    <td class="text-center">
                                        <xsl:choose>
                                            <xsl:when test="ControlData/DocumentGenerated">PASS</xsl:when>
                                            <xsl:otherwise>FAIL</xsl:otherwise>
                                        </xsl:choose>
                                    </td>
                                    <td class="text-center">
                                        <xsl:choose>
                                            <xsl:when test="ControlData/DocumentGenerated">
                                                <span class="status-pass icon-pass"/>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <span class="status-fail icon-fail"/>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </td>
                                </tr>

                                <!-- Rule 3: ContentDate vs DocumentGenerated -->
                                <tr>
                                    <td class="text-center">3</td>
                                    <td>
                                        <strong>ContentDate Alignment</strong>
                                    </td>
                                    <td>ContentDate matches DocumentGenerated date</td>
                                    <td class="text-center">
                                        <xsl:choose>
                                            <xsl:when
                                                    test="ControlData/ContentDate = substring(ControlData/DocumentGenerated, 1, 10)">
                                                PASS
                                            </xsl:when>
                                            <xsl:otherwise>WARN</xsl:otherwise>
                                        </xsl:choose>
                                    </td>
                                    <td class="text-center">
                                        <xsl:choose>
                                            <xsl:when
                                                    test="ControlData/ContentDate = substring(ControlData/DocumentGenerated, 1, 10)">
                                                <span class="status-pass icon-pass"/>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <span class="status-warn icon-warn"/>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </td>
                                </tr>

                                <!-- Rule 4: All Funds Have NAV Date -->
                                <tr>
                                    <td class="text-center">4</td>
                                    <td>
                                        <strong>NAV Dates Present</strong>
                                    </td>
                                    <td>All funds have NAV dates</td>
                                    <td class="text-center">
                                        <xsl:variable name="missingNAVDates"
                                                      select="count(Funds/Fund[not(FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate)])"/>
                                        <xsl:choose>
                                            <xsl:when test="$missingNAVDates = 0">PASS</xsl:when>
                                            <xsl:otherwise>FAIL (<xsl:value-of select="$missingNAVDates"/> missing)
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </td>
                                    <td class="text-center">
                                        <xsl:variable name="missingNAVDates"
                                                      select="count(Funds/Fund[not(FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate)])"/>
                                        <xsl:choose>
                                            <xsl:when test="$missingNAVDates = 0">
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

                    <!-- Fund-Level Date Validation -->
                    <div class="section">
                        <h2 class="section-title">FUND-LEVEL DATE VALIDATION</h2>
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
                                        <th style="width: 45%;">Temporal Check</th>
                                        <th style="width: 25%;" class="text-center">Value</th>
                                        <th style="width: 15%;" class="text-center">Result</th>
                                        <th style="width: 10%;" class="text-center">Status</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <!-- Check 1: NAV Date Alignment -->
                                    <tr>
                                        <td class="text-center">1</td>
                                        <td>
                                            <strong>NAV Date = ContentDate</strong>
                                        </td>
                                        <td class="text-center monospace">
                                            <xsl:value-of
                                                    select="FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate"/>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when
                                                        test="FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate = /FundsXML4/ControlData/ContentDate">
                                                    PASS
                                                </xsl:when>
                                                <xsl:otherwise>WARN</xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when
                                                        test="FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate = /FundsXML4/ControlData/ContentDate">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-warn icon-warn"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>

                                    <!-- Check 2: Inception Date Present -->
                                    <tr>
                                        <td class="text-center">2</td>
                                        <td>
                                            <strong>Inception Date Present</strong>
                                        </td>
                                        <td class="text-center monospace">
                                            <xsl:choose>
                                                <xsl:when test="FundStaticData/InceptionDate">
                                                    <xsl:value-of select="FundStaticData/InceptionDate"/>
                                                </xsl:when>
                                                <xsl:otherwise>N/A</xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when test="FundStaticData/InceptionDate">PASS</xsl:when>
                                                <xsl:otherwise>WARN</xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when test="FundStaticData/InceptionDate">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <span class="status-warn icon-warn"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                    </tr>

                                    <!-- Check 3: Inception Before NAV Date -->
                                    <tr>
                                        <td class="text-center">3</td>
                                        <td>
                                            <strong>Inception Before NAV Date</strong>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when
                                                        test="FundStaticData/InceptionDate and FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate">
                                                    Logical check
                                                </xsl:when>
                                                <xsl:otherwise>N/A</xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when
                                                        test="FundStaticData/InceptionDate &lt;= FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate">
                                                    PASS
                                                </xsl:when>
                                                <xsl:when test="not(FundStaticData/InceptionDate)">N/A</xsl:when>
                                                <xsl:otherwise>FAIL</xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                        <td class="text-center">
                                            <xsl:choose>
                                                <xsl:when
                                                        test="FundStaticData/InceptionDate &lt;= FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate">
                                                    <span class="status-pass icon-pass"/>
                                                </xsl:when>
                                                <xsl:when test="not(FundStaticData/InceptionDate)">
                                                    <span style="color: #999;">-</span>
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
                    <div style="margin-top: 40px; padding-top: 20px; border-top: 2px solid #fd7e14; text-align: center; color: #666; font-size: 12px;">
                        <p>Temporal Consistency Report Generated from FundsXML 4.28 | Report Date:
                            <xsl:value-of select="ControlData/ContentDate"/>
                        </p>
                    </div>
                </div>
            </body>
        </html>
    </xsl:template>

</xsl:stylesheet>
