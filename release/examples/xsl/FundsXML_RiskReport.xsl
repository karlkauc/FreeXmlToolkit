<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fo="http://www.w3.org/1999/XSL/Format">

    <xsl:output method="xml" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <!-- Main template for Risk Report -->
    <xsl:template match="/FundsXML4">
        <fo:root>
            <!-- Page Layout Definition -->
            <fo:layout-master-set>
                <fo:simple-page-master master-name="risk-report-page"
                                       page-height="29.7cm"
                                       page-width="21cm"
                                       margin-top="2cm"
                                       margin-bottom="2cm"
                                       margin-left="2.5cm"
                                       margin-right="2.5cm">
                    <fo:region-body margin-top="2.5cm" margin-bottom="2cm"/>
                    <fo:region-before region-name="header" extent="2cm"/>
                    <fo:region-after region-name="footer" extent="1.5cm"/>
                </fo:simple-page-master>
            </fo:layout-master-set>

            <!-- Page Sequence -->
            <fo:page-sequence master-reference="risk-report-page">
                <!-- Header -->
                <fo:static-content flow-name="header">
                    <fo:block-container background-color="#dc3545" color="#ffffff" padding="8mm">
                        <fo:block font-size="18pt" font-weight="bold" text-align="center">
                            🚨 RISK ANALYSIS REPORT
                        </fo:block>
                        <fo:block font-size="10pt" text-align="center" space-before="2mm">
                            Comprehensive Risk Assessment and Portfolio Concentration Analysis
                        </fo:block>
                    </fo:block-container>
                </fo:static-content>

                <!-- Footer -->
                <fo:static-content flow-name="footer">
                    <fo:block-container border-top="1pt solid #dc3545" padding-top="4mm">
                        <fo:table table-layout="fixed" width="100%">
                            <fo:table-column column-width="33%"/>
                            <fo:table-column column-width="34%"/>
                            <fo:table-column column-width="33%"/>
                            <fo:table-body>
                                <fo:table-row>
                                    <fo:table-cell>
                                        <fo:block font-size="8pt" color="#666666">
                                            Doc:
                                            <xsl:value-of select="ControlData/UniqueDocumentID"/>
                                        </fo:block>
                                    </fo:table-cell>
                                    <fo:table-cell>
                                        <fo:block font-size="8pt" color="#666666" text-align="center">
                                            Page
                                            <fo:page-number/>
                                            of
                                            <fo:page-number-citation ref-id="last-page"/>
                                        </fo:block>
                                    </fo:table-cell>
                                    <fo:table-cell>
                                        <fo:block font-size="8pt" color="#666666" text-align="right">
                                            Generated:
                                            <xsl:value-of select="substring(ControlData/DocumentGenerated, 1, 10)"/>
                                        </fo:block>
                                    </fo:table-cell>
                                </fo:table-row>
                            </fo:table-body>
                        </fo:table>
                    </fo:block-container>
                </fo:static-content>

                <!-- Main Content Flow -->
                <fo:flow flow-name="xsl-region-body" font-family="Arial, Helvetica, sans-serif" font-size="10pt">

                    <!-- Risk Overview Dashboard -->
                    <fo:block-container background-color="#fff5f5" padding="8mm" space-after="8mm"
                                        border="2pt solid #dc3545">
                        <fo:block font-size="16pt" font-weight="bold" color="#dc3545" space-after="5mm">
                            ⚠️ RISK OVERVIEW DASHBOARD
                        </fo:block>

                        <fo:table table-layout="fixed" width="100%">
                            <fo:table-column column-width="25%"/>
                            <fo:table-column column-width="25%"/>
                            <fo:table-column column-width="25%"/>
                            <fo:table-column column-width="25%"/>
                            <fo:table-body>
                                <fo:table-row>
                                    <fo:table-cell padding="2mm">
                                        <fo:block-container background-color="#ffffff" padding="4mm"
                                                            border="1pt solid #dc3545" text-align="center">
                                            <fo:block font-size="8pt" color="#6c757d" font-weight="bold">TOTAL FUNDS
                                            </fo:block>
                                            <fo:block font-size="20pt" font-weight="bold" color="#dc3545">
                                                <xsl:value-of select="count(Funds/Fund)"/>
                                            </fo:block>
                                        </fo:block-container>
                                    </fo:table-cell>
                                    <fo:table-cell padding="2mm">
                                        <fo:block-container background-color="#ffffff" padding="4mm"
                                                            border="1pt solid #dc3545" text-align="center">
                                            <fo:block font-size="8pt" color="#6c757d" font-weight="bold">TOTAL
                                                POSITIONS
                                            </fo:block>
                                            <fo:block font-size="20pt" font-weight="bold" color="#dc3545">
                                                <xsl:value-of select="count(//Position)"/>
                                            </fo:block>
                                        </fo:block-container>
                                    </fo:table-cell>
                                    <fo:table-cell padding="2mm">
                                        <fo:block-container background-color="#ffffff" padding="4mm"
                                                            border="1pt solid #dc3545" text-align="center">
                                            <fo:block font-size="8pt" color="#6c757d" font-weight="bold">ANALYSIS DATE
                                            </fo:block>
                                            <fo:block font-size="12pt" font-weight="bold" color="#dc3545">
                                                <xsl:value-of select="ControlData/ContentDate"/>
                                            </fo:block>
                                        </fo:block-container>
                                    </fo:table-cell>
                                    <fo:table-cell padding="2mm">
                                        <fo:block-container background-color="#ffffff" padding="4mm"
                                                            border="1pt solid #dc3545" text-align="center">
                                            <fo:block font-size="8pt" color="#6c757d" font-weight="bold">RISK CODES
                                            </fo:block>
                                            <fo:block font-size="20pt" font-weight="bold" color="#dc3545">
                                                <xsl:value-of select="count(//RiskCode)"/>
                                            </fo:block>
                                        </fo:block-container>
                                    </fo:table-cell>
                                </fo:table-row>
                            </fo:table-body>
                        </fo:table>
                    </fo:block-container>

                    <!-- Individual Fund Risk Analysis -->
                    <xsl:for-each select="Funds/Fund">
                        <fo:block-container space-after="12mm" keep-together.within-page="always">
                            <!-- Fund Header with Risk Level Indicator -->
                            <xsl:variable name="top5Concentration"
                                          select="sum(FundDynamicData/Portfolios/Portfolio/Positions/Position[position() &lt;= 5]/TotalPercentage)"/>
                            <xsl:variable name="riskLevel">
                                <xsl:choose>
                                    <xsl:when test="$top5Concentration &gt; 50">HIGH</xsl:when>
                                    <xsl:when test="$top5Concentration &gt; 30">MEDIUM</xsl:when>
                                    <xsl:otherwise>LOW</xsl:otherwise>
                                </xsl:choose>
                            </xsl:variable>

                            <fo:block-container padding="6mm" space-after="6mm"
                                                border="2pt solid #ffc107" background-color="#fffbf0">
                                <fo:block font-size="16pt" font-weight="bold" color="#856404">
                                    📊 FUND ANALYSIS:
                                    <xsl:value-of select="Names/OfficialName"/>
                                </fo:block>
                                <fo:block font-size="10pt" color="#856404" space-before="2mm">
                                    LEI:
                                    <xsl:value-of select="Identifiers/LEI"/> •
                                    Currency:
                                    <xsl:value-of select="Currency"/> •
                                    Risk Level:
                                    <fo:inline font-weight="bold" color="#dc3545">
                                        <xsl:value-of select="$riskLevel"/>
                                    </fo:inline>
                                </fo:block>
                            </fo:block-container>

                            <!-- Risk Metrics Table -->
                            <fo:block font-size="14pt" font-weight="bold" color="#495057" space-after="4mm">
                                📈 Risk Metrics
                            </fo:block>

                            <xsl:choose>
                                <xsl:when test="FundDynamicData/Portfolios/Portfolio/RiskCodes/RiskCode">
                                    <fo:table table-layout="fixed" width="100%" border-collapse="collapse"
                                              border="1pt solid #dee2e6" space-after="6mm">
                                        <fo:table-column column-width="50%"/>
                                        <fo:table-column column-width="30%"/>
                                        <fo:table-column column-width="20%"/>

                                        <fo:table-header background-color="#f8f9fa">
                                            <fo:table-row>
                                                <fo:table-cell border="0.5pt solid #dee2e6" padding="4mm">
                                                    <fo:block font-weight="bold">Risk Code</fo:block>
                                                </fo:table-cell>
                                                <fo:table-cell border="0.5pt solid #dee2e6" padding="4mm">
                                                    <fo:block font-weight="bold" text-align="right">Value</fo:block>
                                                </fo:table-cell>
                                                <fo:table-cell border="0.5pt solid #dee2e6" padding="4mm">
                                                    <fo:block font-weight="bold" text-align="center">Status</fo:block>
                                                </fo:table-cell>
                                            </fo:table-row>
                                        </fo:table-header>

                                        <fo:table-body>
                                            <xsl:for-each
                                                    select="FundDynamicData/Portfolios/Portfolio/RiskCodes/RiskCode">
                                                <fo:table-row>
                                                    <xsl:if test="position() mod 2 = 0">
                                                        <xsl:attribute name="background-color">#f8f9fa</xsl:attribute>
                                                    </xsl:if>
                                                    <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                                        <fo:block>
                                                            <xsl:value-of select="ListedCode | UnlistedCode"/>
                                                        </fo:block>
                                                    </fo:table-cell>
                                                    <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                                        <fo:block text-align="right" font-family="Courier, monospace">
                                                            <xsl:value-of select="format-number(Value, '0.0000')"/>
                                                        </fo:block>
                                                    </fo:table-cell>
                                                    <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                                        <fo:block text-align="center" font-size="8pt"
                                                                  font-weight="bold">
                                                            <xsl:choose>
                                                                <xsl:when test="Value &gt; 0.8">
                                                                    <fo:inline color="#dc3545">HIGH</fo:inline>
                                                                </xsl:when>
                                                                <xsl:when test="Value &gt; 0.5">
                                                                    <fo:inline color="#ffc107">MED</fo:inline>
                                                                </xsl:when>
                                                                <xsl:otherwise>
                                                                    <fo:inline color="#28a745">LOW</fo:inline>
                                                                </xsl:otherwise>
                                                            </xsl:choose>
                                                        </fo:block>
                                                    </fo:table-cell>
                                                </fo:table-row>
                                            </xsl:for-each>
                                        </fo:table-body>
                                    </fo:table>
                                </xsl:when>
                                <xsl:otherwise>
                                    <fo:block-container background-color="#fff3cd" padding="5mm"
                                                        border="1pt solid #ffeaa7" space-after="6mm">
                                        <fo:block color="#856404" text-align="center">
                                            ⚠️ No risk metrics available for this fund
                                        </fo:block>
                                    </fo:block-container>
                                </xsl:otherwise>
                            </xsl:choose>

                            <!-- Portfolio Concentration Analysis -->
                            <fo:block font-size="14pt" font-weight="bold" color="#495057" space-after="4mm">
                                🎯 Portfolio Concentration Risk
                            </fo:block>

                            <fo:table table-layout="fixed" width="100%" space-after="6mm">
                                <fo:table-column column-width="25%"/>
                                <fo:table-column column-width="25%"/>
                                <fo:table-column column-width="25%"/>
                                <fo:table-column column-width="25%"/>
                                <fo:table-body>
                                    <fo:table-row>
                                        <fo:table-cell padding="2mm">
                                            <fo:block-container background-color="#e3f2fd" padding="4mm"
                                                                border="1pt solid #2196f3" text-align="center">
                                                <fo:block font-size="8pt" color="#1976d2" font-weight="bold">TOP 5
                                                    CONCENTRATION
                                                </fo:block>
                                                <fo:block font-size="16pt" font-weight="bold" color="#1976d2">
                                                    <xsl:value-of select="format-number($top5Concentration, '0.0')"/>%
                                                </fo:block>
                                            </fo:block-container>
                                        </fo:table-cell>
                                        <fo:table-cell padding="2mm">
                                            <fo:block-container background-color="#f3e5f5" padding="4mm"
                                                                border="1pt solid #9c27b0" text-align="center">
                                                <fo:block font-size="8pt" color="#7b1fa2" font-weight="bold">TOP 10
                                                    CONCENTRATION
                                                </fo:block>
                                                <fo:block font-size="16pt" font-weight="bold" color="#7b1fa2">
                                                    <xsl:value-of
                                                            select="format-number(sum(FundDynamicData/Portfolios/Portfolio/Positions/Position[position() &lt;= 10]/TotalPercentage), '0.0')"/>%
                                                </fo:block>
                                            </fo:block-container>
                                        </fo:table-cell>
                                        <fo:table-cell padding="2mm">
                                            <fo:block-container background-color="#e8f5e8" padding="4mm"
                                                                border="1pt solid #4caf50" text-align="center">
                                                <fo:block font-size="8pt" color="#2e7d32" font-weight="bold">TOTAL
                                                    POSITIONS
                                                </fo:block>
                                                <fo:block font-size="16pt" font-weight="bold" color="#2e7d32">
                                                    <xsl:value-of
                                                            select="count(FundDynamicData/Portfolios/Portfolio/Positions/Position)"/>
                                                </fo:block>
                                            </fo:block-container>
                                        </fo:table-cell>
                                        <fo:table-cell padding="2mm">
                                            <fo:block-container padding="4mm" text-align="center"
                                                                border="2pt solid">
                                                <xsl:attribute name="border-color">
                                                    <xsl:choose>
                                                        <xsl:when test="$riskLevel = 'HIGH'">#dc3545</xsl:when>
                                                        <xsl:when test="$riskLevel = 'MEDIUM'">#ffc107</xsl:when>
                                                        <xsl:otherwise>#28a745</xsl:otherwise>
                                                    </xsl:choose>
                                                </xsl:attribute>
                                                <xsl:attribute name="background-color">
                                                    <xsl:choose>
                                                        <xsl:when test="$riskLevel = 'HIGH'">#f8d7da</xsl:when>
                                                        <xsl:when test="$riskLevel = 'MEDIUM'">#fff3cd</xsl:when>
                                                        <xsl:otherwise>#d4edda</xsl:otherwise>
                                                    </xsl:choose>
                                                </xsl:attribute>
                                                <fo:block font-size="8pt" font-weight="bold">
                                                    <xsl:attribute name="color">
                                                        <xsl:choose>
                                                            <xsl:when test="$riskLevel = 'HIGH'">#721c24</xsl:when>
                                                            <xsl:when test="$riskLevel = 'MEDIUM'">#856404</xsl:when>
                                                            <xsl:otherwise>#155724</xsl:otherwise>
                                                        </xsl:choose>
                                                    </xsl:attribute>
                                                    RISK LEVEL
                                                </fo:block>
                                                <fo:block font-size="14pt" font-weight="bold">
                                                    <xsl:attribute name="color">
                                                        <xsl:choose>
                                                            <xsl:when test="$riskLevel = 'HIGH'">#721c24</xsl:when>
                                                            <xsl:when test="$riskLevel = 'MEDIUM'">#856404</xsl:when>
                                                            <xsl:otherwise>#155724</xsl:otherwise>
                                                        </xsl:choose>
                                                    </xsl:attribute>
                                                    <xsl:value-of select="$riskLevel"/>
                                                </fo:block>
                                            </fo:block-container>
                                        </fo:table-cell>
                                    </fo:table-row>
                                </fo:table-body>
                            </fo:table>

                            <!-- Currency Risk Analysis -->
                            <fo:block font-size="14pt" font-weight="bold" color="#495057" space-after="4mm">
                                💱 Currency Exposure Risk
                            </fo:block>

                            <fo:block-container background-color="#f8f9fa" padding="5mm" border="1pt solid #dee2e6">
                                <fo:block space-after="3mm">
                                    <fo:inline font-weight="bold">Base Currency:</fo:inline>
                                    <fo:inline color="#007bff" font-weight="bold">
                                        <xsl:value-of select="Currency"/>
                                    </fo:inline>
                                </fo:block>
                                <fo:block>
                                    <fo:inline font-weight="bold">Foreign Currency Positions:</fo:inline>
                                    <xsl:variable name="foreignPositions"
                                                  select="count(FundDynamicData/Portfolios/Portfolio/Positions/Position[Currency != current()/Currency])"/>
                                    <fo:inline font-weight="bold">
                                        <xsl:attribute name="color">
                                            <xsl:choose>
                                                <xsl:when test="$foreignPositions = 0">#28a745</xsl:when>
                                                <xsl:when test="$foreignPositions &lt; 10">#ffc107</xsl:when>
                                                <xsl:otherwise>#dc3545</xsl:otherwise>
                                            </xsl:choose>
                                        </xsl:attribute>
                                        <xsl:value-of select="$foreignPositions"/> of
                                        <xsl:value-of
                                                select="count(FundDynamicData/Portfolios/Portfolio/Positions/Position)"/>
                                    </fo:inline>
                                </fo:block>
                            </fo:block-container>
                        </fo:block-container>
                    </xsl:for-each>

                    <!-- Risk Assessment Summary -->
                    <fo:block-container background-color="#dc3545" color="#ffffff" padding="8mm"
                                        space-before="10mm" id="last-page">
                        <fo:block font-size="16pt" font-weight="bold" space-after="5mm">
                            🎯 RISK ASSESSMENT CONCLUSIONS
                        </fo:block>
                        <fo:block space-after="3mm">
                            This comprehensive risk analysis covers
                            <xsl:value-of select="count(Funds/Fund)"/> fund(s)
                            containing a total of
                            <xsl:value-of select="count(//Position)"/> positions as of
                            <xsl:value-of select="ControlData/ContentDate"/>.
                        </fo:block>
                        <fo:block space-after="3mm">
                            Risk factors analyzed include portfolio concentration, currency exposure,
                            and fund-specific risk metrics where available.
                        </fo:block>
                        <fo:block font-style="italic">
                            Data Provider:
                            <xsl:value-of select="ControlData/DataSupplier/Name"/>
                            (<xsl:value-of select="ControlData/DataSupplier/Contact/Email"/>)
                        </fo:block>
                    </fo:block-container>
                </fo:flow>
            </fo:page-sequence>
        </fo:root>
    </xsl:template>

</xsl:stylesheet>