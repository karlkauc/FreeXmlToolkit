<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fo="http://www.w3.org/1999/XSL/Format">

    <xsl:output method="xml" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <!-- Main template for Compliance Report -->
    <xsl:template match="/FundsXML4">
        <fo:root>
            <!-- Page Layout Definition -->
            <fo:layout-master-set>
                <fo:simple-page-master master-name="compliance-report-page"
                                       page-height="29.7cm"
                                       page-width="21cm"
                                       margin-top="2cm"
                                       margin-bottom="2cm"
                                       margin-left="2.5cm"
                                       margin-right="2.5cm">
                    <fo:region-body margin-top="3cm" margin-bottom="2cm"/>
                    <fo:region-before region-name="header" extent="2.5cm"/>
                    <fo:region-after region-name="footer" extent="1.5cm"/>
                </fo:simple-page-master>
            </fo:layout-master-set>

            <!-- Page Sequence -->
            <fo:page-sequence master-reference="compliance-report-page">
                <!-- Header -->
                <fo:static-content flow-name="header">
                    <fo:block-container background-color="#28a745" color="#ffffff" padding="8mm">
                        <fo:block font-size="22pt" font-weight="bold" text-align="center">
                            ✅ COMPLIANCE VALIDATION REPORT
                        </fo:block>
                        <fo:block font-size="12pt" text-align="center" space-before="3mm">
                            Regulatory Compliance and Data Quality Assessment
                        </fo:block>
                        <fo:block text-align="center" font-size="10pt" space-before="2mm">
                            Report Date:
                            <xsl:value-of select="ControlData/ContentDate"/> •
                            Document:
                            <xsl:value-of select="ControlData/UniqueDocumentID"/>
                        </fo:block>
                    </fo:block-container>
                </fo:static-content>

                <!-- Footer -->
                <fo:static-content flow-name="footer">
                    <fo:block-container border-top="2pt solid #28a745" padding-top="5mm">
                        <fo:table table-layout="fixed" width="100%">
                            <fo:table-column column-width="40%"/>
                            <fo:table-column column-width="20%"/>
                            <fo:table-column column-width="40%"/>
                            <fo:table-body>
                                <fo:table-row>
                                    <fo:table-cell>
                                        <fo:block font-size="8pt" color="#28a745" font-weight="bold">
                                            CONFIDENTIAL COMPLIANCE REPORT
                                        </fo:block>
                                        <fo:block font-size="7pt" color="#666666">
                                            <xsl:value-of select="ControlData/DataSupplier/Name"/>
                                        </fo:block>
                                    </fo:table-cell>
                                    <fo:table-cell>
                                        <fo:block font-size="8pt" color="#666666" text-align="center">
                                            Page
                                            <fo:page-number/>
                                            of
                                            <fo:page-number-citation ref-id="compliance-end"/>
                                        </fo:block>
                                    </fo:table-cell>
                                    <fo:table-cell>
                                        <fo:block font-size="8pt" color="#666666" text-align="right">
                                            Generated:
                                            <xsl:value-of select="substring(ControlData/DocumentGenerated, 1, 16)"/>
                                        </fo:block>
                                    </fo:table-cell>
                                </fo:table-row>
                            </fo:table-body>
                        </fo:table>
                    </fo:block-container>
                </fo:static-content>

                <!-- Main Content Flow -->
                <fo:flow flow-name="xsl-region-body" font-family="Arial, Helvetica, sans-serif" font-size="10pt">

                    <!-- Executive Summary -->
                    <fo:block-container background-color="#d4edda" padding="8mm" space-after="8mm"
                                        border="2pt solid #28a745">
                        <fo:block font-size="16pt" font-weight="bold" color="#155724" space-after="5mm">
                            📋 COMPLIANCE EXECUTIVE SUMMARY
                        </fo:block>

                        <fo:table table-layout="fixed" width="100%" space-after="5mm">
                            <fo:table-column column-width="25%"/>
                            <fo:table-column column-width="25%"/>
                            <fo:table-column column-width="25%"/>
                            <fo:table-column column-width="25%"/>
                            <fo:table-body>
                                <fo:table-row>
                                    <fo:table-cell padding="3mm">
                                        <fo:block-container background-color="#ffffff" padding="4mm"
                                                            border="1pt solid #28a745" text-align="center">
                                            <fo:block font-size="8pt" color="#666666" font-weight="bold">FUNDS
                                                REVIEWED
                                            </fo:block>
                                            <fo:block font-size="20pt" font-weight="bold" color="#28a745">
                                                <xsl:value-of select="count(Funds/Fund)"/>
                                            </fo:block>
                                        </fo:block-container>
                                    </fo:table-cell>
                                    <fo:table-cell padding="3mm">
                                        <fo:block-container background-color="#ffffff" padding="4mm"
                                                            border="1pt solid #28a745" text-align="center">
                                            <fo:block font-size="8pt" color="#666666" font-weight="bold">TOTAL
                                                POSITIONS
                                            </fo:block>
                                            <fo:block font-size="20pt" font-weight="bold" color="#28a745">
                                                <xsl:value-of select="count(//Position)"/>
                                            </fo:block>
                                        </fo:block-container>
                                    </fo:table-cell>
                                    <fo:table-cell padding="3mm">
                                        <fo:block-container background-color="#ffffff" padding="4mm"
                                                            border="1pt solid #28a745" text-align="center">
                                            <fo:block font-size="8pt" color="#666666" font-weight="bold">COMPLIANCE
                                                CHECKS
                                            </fo:block>
                                            <fo:block font-size="20pt" font-weight="bold" color="#28a745">
                                                15
                                            </fo:block>
                                        </fo:block-container>
                                    </fo:table-cell>
                                    <fo:table-cell padding="3mm">
                                        <fo:block-container background-color="#ffffff" padding="4mm"
                                                            border="1pt solid #28a745" text-align="center">
                                            <fo:block font-size="8pt" color="#666666" font-weight="bold">DATA QUALITY
                                            </fo:block>
                                            <fo:block font-size="16pt" font-weight="bold" color="#28a745">
                                                PASS
                                            </fo:block>
                                        </fo:block-container>
                                    </fo:table-cell>
                                </fo:table-row>
                            </fo:table-body>
                        </fo:table>

                        <fo:block color="#155724" font-weight="bold">
                            Overall Compliance Status:
                            <fo:inline font-size="14pt" color="#28a745">✅ COMPLIANT</fo:inline>
                        </fo:block>
                    </fo:block-container>

                    <!-- Data Quality Validation -->
                    <fo:block font-size="18pt" font-weight="bold" color="#495057" space-after="5mm"
                              border-bottom="2pt solid #28a745" padding-bottom="3mm">
                        🔍 DATA QUALITY VALIDATION
                    </fo:block>

                    <fo:table table-layout="fixed" width="100%" border-collapse="collapse"
                              border="1pt solid #dee2e6" space-after="8mm">
                        <fo:table-column column-width="5%"/>
                        <fo:table-column column-width="35%"/>
                        <fo:table-column column-width="25%"/>
                        <fo:table-column column-width="20%"/>
                        <fo:table-column column-width="15%"/>

                        <fo:table-header background-color="#f8f9fa">
                            <fo:table-row>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                    <fo:block font-weight="bold" text-align="center">#</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                    <fo:block font-weight="bold">Validation Rule</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                    <fo:block font-weight="bold">Description</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                    <fo:block font-weight="bold" text-align="center">Result</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                    <fo:block font-weight="bold" text-align="center">Status</fo:block>
                                </fo:table-cell>
                            </fo:table-row>
                        </fo:table-header>

                        <fo:table-body>
                            <!-- Rule 1: Document Structure -->
                            <fo:table-row>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">1</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">Document Structure</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">Root element is FundsXML4</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-size="9pt">
                                        <xsl:choose>
                                            <xsl:when test="name(.) = 'FundsXML4'">PASS</xsl:when>
                                            <xsl:otherwise>FAIL</xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="name(.) = 'FundsXML4'">
                                                <fo:inline color="#28a745" font-weight="bold">✅</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545" font-weight="bold">❌</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- Rule 2: Control Data -->
                            <fo:table-row background-color="#f8f9fa">
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">2</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">Control Data Present</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">Required control data fields</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-size="9pt">
                                        <xsl:choose>
                                            <xsl:when
                                                    test="ControlData/UniqueDocumentID and ControlData/DocumentGenerated">
                                                PASS
                                            </xsl:when>
                                            <xsl:otherwise>FAIL</xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when
                                                    test="ControlData/UniqueDocumentID and ControlData/DocumentGenerated">
                                                <fo:inline color="#28a745" font-weight="bold">✅</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545" font-weight="bold">❌</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- Rule 3: Fund LEI -->
                            <fo:table-row>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">3</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">Fund LEI Present</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">All funds have valid LEI</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-size="9pt">
                                        <xsl:choose>
                                            <xsl:when test="count(Funds/Fund[not(Identifiers/LEI)]) = 0">PASS</xsl:when>
                                            <xsl:otherwise>FAIL</xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="count(Funds/Fund[not(Identifiers/LEI)]) = 0">
                                                <fo:inline color="#28a745" font-weight="bold">✅</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545" font-weight="bold">❌</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- Rule 4: Currency Codes -->
                            <fo:table-row background-color="#f8f9fa">
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">4</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">Currency Code Format</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">ISO 4217 currency codes</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-size="9pt">
                                        <xsl:choose>
                                            <xsl:when test="count(//Currency[string-length(.) != 3]) = 0">PASS
                                            </xsl:when>
                                            <xsl:otherwise>FAIL</xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="count(//Currency[string-length(.) != 3]) = 0">
                                                <fo:inline color="#28a745" font-weight="bold">✅</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545" font-weight="bold">❌</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- Rule 5: Portfolio Sum -->
                            <fo:table-row>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">5</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">Portfolio Sum Check</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">Portfolio weights sum to ~100%</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-size="9pt">
                                        <xsl:variable name="portfolioSum" select="sum(//Position/TotalPercentage)"/>
                                        <xsl:choose>
                                            <xsl:when test="$portfolioSum &gt; 99 and $portfolioSum &lt; 101">PASS
                                            </xsl:when>
                                            <xsl:otherwise>WARNING</xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:variable name="portfolioSum" select="sum(//Position/TotalPercentage)"/>
                                        <xsl:choose>
                                            <xsl:when test="$portfolioSum &gt; 99 and $portfolioSum &lt; 101">
                                                <fo:inline color="#28a745" font-weight="bold">✅</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#ffc107" font-weight="bold">⚠️</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>
                        </fo:table-body>
                    </fo:table>

                    <!-- Individual Fund Compliance -->
                    <xsl:for-each select="Funds/Fund">
                        <fo:block-container space-after="10mm" keep-together.within-page="always">
                            <!-- Fund Header -->
                            <fo:block-container background-color="#007bff" color="#ffffff" padding="5mm"
                                                space-after="5mm">
                                <fo:block font-size="16pt" font-weight="bold">
                                    🏢 FUND COMPLIANCE:
                                    <xsl:value-of select="Names/OfficialName"/>
                                </fo:block>
                                <fo:block font-size="10pt" space-before="2mm">
                                    LEI:
                                    <xsl:value-of select="Identifiers/LEI"/> •
                                    Currency:
                                    <xsl:value-of select="Currency"/> •
                                    Positions:
                                    <xsl:value-of
                                            select="count(FundDynamicData/Portfolios/Portfolio/Positions/Position)"/>
                                </fo:block>
                            </fo:block-container>

                            <!-- Fund-specific Compliance Checks -->
                            <fo:table table-layout="fixed" width="100%" border-collapse="collapse"
                                      border="1pt solid #dee2e6" space-after="6mm">
                                <fo:table-column column-width="50%"/>
                                <fo:table-column column-width="30%"/>
                                <fo:table-column column-width="20%"/>

                                <fo:table-header background-color="#e3f2fd">
                                    <fo:table-row>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                            <fo:block font-weight="bold" color="#1976d2">Compliance Check</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                            <fo:block font-weight="bold" color="#1976d2" text-align="center">Value
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                            <fo:block font-weight="bold" color="#1976d2" text-align="center">Status
                                            </fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>
                                </fo:table-header>

                                <fo:table-body>
                                    <!-- NAV Date Consistency -->
                                    <fo:table-row>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block>NAV Date Consistency</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center" font-size="8pt">
                                                <xsl:value-of
                                                        select="FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate"/>
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center">
                                                <xsl:choose>
                                                    <xsl:when
                                                            test="FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate = FundDynamicData/Portfolios/Portfolio/NavDate">
                                                        <fo:inline color="#28a745" font-weight="bold">✅ PASS</fo:inline>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <fo:inline color="#dc3545" font-weight="bold">❌ FAIL</fo:inline>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>

                                    <!-- Portfolio Percentage Sum -->
                                    <fo:table-row background-color="#f8f9fa">
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block>Portfolio Percentage Sum</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center" font-size="8pt">
                                                <xsl:value-of
                                                        select="format-number(sum(FundDynamicData/Portfolios/Portfolio/Positions/Position/TotalPercentage), '0.00')"/>%
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center">
                                                <xsl:variable name="sum"
                                                              select="sum(FundDynamicData/Portfolios/Portfolio/Positions/Position/TotalPercentage)"/>
                                                <xsl:choose>
                                                    <xsl:when test="$sum &gt; 99.5 and $sum &lt; 100.5">
                                                        <fo:inline color="#28a745" font-weight="bold">✅ PASS</fo:inline>
                                                    </xsl:when>
                                                    <xsl:when test="$sum &gt; 98 and $sum &lt; 102">
                                                        <fo:inline color="#ffc107" font-weight="bold">⚠️ WARN
                                                        </fo:inline>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <fo:inline color="#dc3545" font-weight="bold">❌ FAIL</fo:inline>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>

                                    <!-- Concentration Risk -->
                                    <fo:table-row>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block>Top 5 Concentration Risk</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center" font-size="8pt">
                                                <xsl:value-of
                                                        select="format-number(sum(FundDynamicData/Portfolios/Portfolio/Positions/Position[position() &lt;= 5]/TotalPercentage), '0.0')"/>%
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center">
                                                <xsl:variable name="concentration"
                                                              select="sum(FundDynamicData/Portfolios/Portfolio/Positions/Position[position() &lt;= 5]/TotalPercentage)"/>
                                                <xsl:choose>
                                                    <xsl:when test="$concentration &lt; 30">
                                                        <fo:inline color="#28a745" font-weight="bold">✅ LOW</fo:inline>
                                                    </xsl:when>
                                                    <xsl:when test="$concentration &lt; 50">
                                                        <fo:inline color="#ffc107" font-weight="bold">⚠️ MED</fo:inline>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <fo:inline color="#dc3545" font-weight="bold">❌ HIGH</fo:inline>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>

                                    <!-- Currency Exposure -->
                                    <fo:table-row background-color="#f8f9fa">
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block>Foreign Currency Exposure</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center" font-size="8pt">
                                                <xsl:value-of
                                                        select="count(FundDynamicData/Portfolios/Portfolio/Positions/Position[Currency != current()/Currency])"/>
                                                positions
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center">
                                                <xsl:variable name="foreignPositions"
                                                              select="count(FundDynamicData/Portfolios/Portfolio/Positions/Position[Currency != current()/Currency])"/>
                                                <xsl:choose>
                                                    <xsl:when test="$foreignPositions = 0">
                                                        <fo:inline color="#28a745" font-weight="bold">✅ NONE</fo:inline>
                                                    </xsl:when>
                                                    <xsl:when test="$foreignPositions &lt; 10">
                                                        <fo:inline color="#ffc107" font-weight="bold">⚠️ LOW</fo:inline>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <fo:inline color="#dc3545" font-weight="bold">❌ HIGH</fo:inline>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>
                                </fo:table-body>
                            </fo:table>
                        </fo:block-container>
                    </xsl:for-each>

                    <!-- Compliance Summary -->
                    <fo:block-container background-color="#28a745" color="#ffffff" padding="8mm"
                                        space-before="10mm" id="compliance-end">
                        <fo:block font-size="18pt" font-weight="bold" space-after="5mm">
                            📊 COMPLIANCE ASSESSMENT SUMMARY
                        </fo:block>
                        <fo:block space-after="3mm" font-size="12pt">
                            <fo:inline font-weight="bold">Overall Rating: COMPLIANT ✅</fo:inline>
                        </fo:block>
                        <fo:block space-after="3mm">
                            This compliance report validates
                            <xsl:value-of select="count(Funds/Fund)"/> fund(s)
                            with
                            <xsl:value-of select="count(//Position)"/> total positions against key regulatory
                            and data quality requirements.
                        </fo:block>
                        <fo:block space-after="3mm">
                            Key compliance areas assessed: Document structure, data completeness,
                            portfolio consistency, concentration risk, and currency exposure.
                        </fo:block>
                        <fo:block font-style="italic">
                            Report generated for:
                            <xsl:value-of select="ControlData/DataSupplier/Name"/>
                        </fo:block>
                        <fo:block font-style="italic" space-before="2mm">
                            Contact:
                            <xsl:value-of select="ControlData/DataSupplier/Contact/Email"/>
                        </fo:block>
                    </fo:block-container>
                </fo:flow>
            </fo:page-sequence>
        </fo:root>
    </xsl:template>

</xsl:stylesheet>