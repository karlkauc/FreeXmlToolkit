<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fo="http://www.w3.org/1999/XSL/Format">

    <xsl:output method="xml" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <!-- Key for fast asset lookup -->
    <xsl:key name="asset-by-id" match="Asset" use="UniqueID"/>

    <!-- Main template for Position Value Validation Report -->
    <xsl:template match="/FundsXML4">
        <fo:root>
            <!-- Page Layout Definition -->
            <fo:layout-master-set>
                <fo:simple-page-master master-name="position-value-page"
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
            <fo:page-sequence master-reference="position-value-page">
                <!-- Header -->
                <fo:static-content flow-name="header">
                    <fo:block-container background-color="#e83e8c" color="#ffffff" padding="8mm">
                        <fo:block font-size="22pt" font-weight="bold" text-align="center">
                            POSITION VALUE VALIDATION REPORT
                        </fo:block>
                        <fo:block font-size="12pt" text-align="center" space-before="3mm">
                            Financial Data Quality and Position Value Analysis
                        </fo:block>
                        <fo:block text-align="center" font-size="10pt" space-before="2mm">
                            Total Positions:
                            <xsl:value-of select="count(//Position)"/> •
                            Analysis Date:
                            <xsl:value-of select="ControlData/ContentDate"/>
                        </fo:block>
                    </fo:block-container>
                </fo:static-content>

                <!-- Footer -->
                <fo:static-content flow-name="footer">
                    <fo:block-container border-top="2pt solid #e83e8c" padding-top="5mm">
                        <fo:table table-layout="fixed" width="100%">
                            <fo:table-column column-width="40%"/>
                            <fo:table-column column-width="20%"/>
                            <fo:table-column column-width="40%"/>
                            <fo:table-body>
                                <fo:table-row>
                                    <fo:table-cell>
                                        <fo:block font-size="8pt" color="#e83e8c" font-weight="bold">
                                            POSITION VALUE VALIDATION
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
                                            <fo:page-number-citation ref-id="position-end"/>
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

                    <!-- Executive Summary -->
                    <fo:block-container background-color="#fce4ec" padding="8mm" space-after="8mm"
                                        border="2pt solid #e83e8c">
                        <fo:block font-size="16pt" font-weight="bold" color="#831843" space-after="5mm">
                            FINANCIAL DATA OVERVIEW
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
                                                            border="1pt solid #e83e8c" text-align="center">
                                            <fo:block font-size="8pt" color="#666666" font-weight="bold">TOTAL POSITIONS
                                            </fo:block>
                                            <fo:block font-size="20pt" font-weight="bold" color="#e83e8c">
                                                <xsl:value-of select="count(//Position)"/>
                                            </fo:block>
                                        </fo:block-container>
                                    </fo:table-cell>
                                    <fo:table-cell padding="3mm">
                                        <fo:block-container background-color="#ffffff" padding="4mm"
                                                            border="1pt solid #e83e8c" text-align="center">
                                            <fo:block font-size="8pt" color="#666666" font-weight="bold">TOTAL NAV
                                            </fo:block>
                                            <fo:block font-size="14pt" font-weight="bold" color="#e83e8c">
                                                <xsl:value-of
                                                        select="format-number(sum(//Fund/FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount), '#,##0')"/>
                                            </fo:block>
                                        </fo:block-container>
                                    </fo:table-cell>
                                    <fo:table-cell padding="3mm">
                                        <fo:block-container background-color="#ffffff" padding="4mm"
                                                            border="1pt solid #e83e8c" text-align="center">
                                            <fo:block font-size="8pt" color="#666666" font-weight="bold">VALIDATION
                                                RULES
                                            </fo:block>
                                            <fo:block font-size="20pt" font-weight="bold" color="#e83e8c">
                                                18
                                            </fo:block>
                                        </fo:block-container>
                                    </fo:table-cell>
                                    <fo:table-cell padding="3mm">
                                        <fo:block-container background-color="#ffffff" padding="4mm"
                                                            border="1pt solid #e83e8c" text-align="center">
                                            <fo:block font-size="8pt" color="#666666" font-weight="bold">VALUE
                                                CURRENCIES
                                            </fo:block>
                                            <fo:block font-size="20pt" font-weight="bold" color="#e83e8c">
                                                <xsl:value-of
                                                        select="count(//Position/TotalValue/Amount/@ccy[not(. = preceding::Position/TotalValue/Amount/@ccy)])"/>
                                            </fo:block>
                                        </fo:block-container>
                                    </fo:table-cell>
                                </fo:table-row>
                            </fo:table-body>
                        </fo:table>
                    </fo:block-container>

                    <!-- Position Value Validation Rules -->
                    <fo:block font-size="18pt" font-weight="bold" color="#495057" space-after="5mm"
                              border-bottom="2pt solid #e83e8c" padding-bottom="3mm">
                        POSITION VALUE VALIDATION RULES
                    </fo:block>

                    <fo:table table-layout="fixed" width="100%" border-collapse="collapse"
                              border="1pt solid #dee2e6" space-after="8mm">
                        <fo:table-column column-width="5%"/>
                        <fo:table-column column-width="40%"/>
                        <fo:table-column column-width="25%"/>
                        <fo:table-column column-width="20%"/>
                        <fo:table-column column-width="10%"/>

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
                            <!-- Rule 1: All positions have TotalValue -->
                            <fo:table-row>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">1</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">TotalValue Present</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">All positions have TotalValue element</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-size="9pt">
                                        <xsl:choose>
                                            <xsl:when test="count(//Position[not(TotalValue)]) = 0">PASS</xsl:when>
                                            <xsl:otherwise>FAIL</xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="count(//Position[not(TotalValue)]) = 0">
                                                <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- Rule 2: TotalValue is positive -->
                            <fo:table-row background-color="#f8f9fa">
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">2</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">Positive TotalValue</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">All position values are positive</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-size="9pt">
                                        <xsl:choose>
                                            <xsl:when test="count(//Position/TotalValue/Amount[. &lt;= 0]) = 0">PASS
                                            </xsl:when>
                                            <xsl:otherwise>WARN</xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="count(//Position/TotalValue/Amount[. &lt;= 0]) = 0">
                                                <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#ffc107" font-weight="bold">!</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- Rule 3: TotalPercentage Present -->
                            <fo:table-row>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">3</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">TotalPercentage Present</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">All positions have percentage</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-size="9pt">
                                        <xsl:choose>
                                            <xsl:when test="count(//Position[not(TotalPercentage)]) = 0">PASS</xsl:when>
                                            <xsl:otherwise>FAIL</xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="count(//Position[not(TotalPercentage)]) = 0">
                                                <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- Rule 4: TotalPercentage is positive -->
                            <fo:table-row background-color="#f8f9fa">
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">4</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">Positive Percentages</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">Percentages are positive</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-size="9pt">
                                        <xsl:choose>
                                            <xsl:when test="count(//Position/TotalPercentage[. &lt; 0]) = 0">PASS
                                            </xsl:when>
                                            <xsl:otherwise>FAIL</xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="count(//Position/TotalPercentage[. &lt; 0]) = 0">
                                                <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- Rule 5: Currency Code Present -->
                            <fo:table-row>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">5</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">Currency Code Present</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">All positions have currency</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-size="9pt">
                                        <xsl:choose>
                                            <xsl:when test="count(//Position[not(Currency)]) = 0">PASS</xsl:when>
                                            <xsl:otherwise>FAIL</xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="count(//Position[not(Currency)]) = 0">
                                                <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- Rule 6: Currency Format -->
                            <fo:table-row background-color="#f8f9fa">
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">6</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">Currency Code Format</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">ISO 4217 (3 characters)</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-size="9pt">
                                        <xsl:choose>
                                            <xsl:when test="count(//Position/Currency[string-length(.) != 3]) = 0">
                                                PASS
                                            </xsl:when>
                                            <xsl:otherwise>FAIL</xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="count(//Position/Currency[string-length(.) != 3]) = 0">
                                                <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- Rule 7: Value Currency Attribute -->
                            <fo:table-row>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">7</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">TotalValue Currency Attribute</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">Amount has @ccy attribute</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-size="9pt">
                                        <xsl:choose>
                                            <xsl:when test="count(//Position/TotalValue/Amount[not(@ccy)]) = 0">PASS
                                            </xsl:when>
                                            <xsl:otherwise>FAIL</xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="count(//Position/TotalValue/Amount[not(@ccy)]) = 0">
                                                <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- Rule 8: UniqueID Present -->
                            <fo:table-row background-color="#f8f9fa">
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">8</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">Position UniqueID</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">All positions have UniqueID</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-size="9pt">
                                        <xsl:choose>
                                            <xsl:when test="count(//Position[not(UniqueID)]) = 0">PASS</xsl:when>
                                            <xsl:otherwise>FAIL</xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="count(//Position[not(UniqueID)]) = 0">
                                                <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>
                        </fo:table-body>
                    </fo:table>

                    <!-- Fund-Level Position Value Analysis -->
                    <fo:block font-size="18pt" font-weight="bold" color="#495057" space-after="5mm"
                              border-bottom="2pt solid #e83e8c" padding-bottom="3mm">
                        FUND-LEVEL POSITION VALUE ANALYSIS
                    </fo:block>

                    <xsl:for-each select="Funds/Fund">
                        <xsl:variable name="fundNAV"
                                      select="FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount"/>
                        <xsl:variable name="fundCurrency" select="Currency"/>
                        <xsl:variable name="totalPositionValue"
                                      select="sum(FundDynamicData/Portfolios/Portfolio/Positions/Position/TotalValue/Amount[@ccy = $fundCurrency])"/>
                        <xsl:variable name="totalPercentage"
                                      select="sum(FundDynamicData/Portfolios/Portfolio/Positions/Position/TotalPercentage)"/>

                        <fo:block-container space-after="10mm" keep-together.within-page="always">
                            <!-- Fund Header -->
                            <fo:block-container background-color="#e83e8c" color="#ffffff" padding="5mm"
                                                space-after="5mm">
                                <fo:block font-size="14pt" font-weight="bold">
                                    FUND:
                                    <xsl:value-of select="Names/OfficialName"/>
                                </fo:block>
                                <fo:block font-size="10pt" space-before="2mm">
                                    LEI:
                                    <xsl:value-of select="Identifiers/LEI"/> •
                                    Currency:
                                    <xsl:value-of select="$fundCurrency"/> •
                                    Positions:
                                    <xsl:value-of
                                            select="count(FundDynamicData/Portfolios/Portfolio/Positions/Position)"/>
                                </fo:block>
                            </fo:block-container>

                            <!-- Value Consistency Checks -->
                            <fo:table table-layout="fixed" width="100%" border-collapse="collapse"
                                      border="1pt solid #dee2e6" space-after="6mm">
                                <fo:table-column column-width="45%"/>
                                <fo:table-column column-width="25%"/>
                                <fo:table-column column-width="20%"/>
                                <fo:table-column column-width="10%"/>

                                <fo:table-header background-color="#fce4ec">
                                    <fo:table-row>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                            <fo:block font-weight="bold" color="#831843">Value Check</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                            <fo:block font-weight="bold" color="#831843" text-align="right">Value
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                            <fo:block font-weight="bold" color="#831843" text-align="center">Result
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                            <fo:block font-weight="bold" color="#831843" text-align="center">Status
                                            </fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>
                                </fo:table-header>

                                <fo:table-body>
                                    <!-- Check: Fund NAV -->
                                    <fo:table-row>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block>Fund Net Asset Value (NAV)</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="right" font-size="8pt"
                                                      font-family="Courier, monospace">
                                                <xsl:value-of select="format-number($fundNAV, '#,##0.00')"/>
                                                <xsl:text> </xsl:text>
                                                <xsl:value-of select="$fundCurrency"/>
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center" font-size="8pt">
                                                <xsl:choose>
                                                    <xsl:when test="$fundNAV &gt; 0">PASS</xsl:when>
                                                    <xsl:otherwise>FAIL</xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center">
                                                <xsl:choose>
                                                    <xsl:when test="$fundNAV &gt; 0">
                                                        <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>

                                    <!-- Check: Total Position Value -->
                                    <fo:table-row background-color="#f8f9fa">
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block>Total Position Value (Fund Currency)</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="right" font-size="8pt"
                                                      font-family="Courier, monospace">
                                                <xsl:value-of select="format-number($totalPositionValue, '#,##0.00')"/>
                                                <xsl:text> </xsl:text>
                                                <xsl:value-of select="$fundCurrency"/>
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center" font-size="8pt">
                                                <xsl:choose>
                                                    <xsl:when test="$totalPositionValue &gt; 0">PASS</xsl:when>
                                                    <xsl:otherwise>WARN</xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center">
                                                <xsl:choose>
                                                    <xsl:when test="$totalPositionValue &gt; 0">
                                                        <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <fo:inline color="#ffc107" font-weight="bold">!</fo:inline>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>

                                    <!-- Check: NAV vs Position Value Difference -->
                                    <fo:table-row>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block>NAV vs Position Value Difference</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="right" font-size="8pt"
                                                      font-family="Courier, monospace">
                                                <xsl:value-of
                                                        select="format-number($fundNAV - $totalPositionValue, '#,##0.00')"/>
                                                <xsl:text> </xsl:text>
                                                <xsl:value-of select="$fundCurrency"/>
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center" font-size="8pt">
                                                <xsl:variable name="difference"
                                                              select="$fundNAV - $totalPositionValue"/>
                                                <xsl:variable name="percentDiff"
                                                              select="($difference div $fundNAV) * 100"/>
                                                <xsl:choose>
                                                    <xsl:when test="$percentDiff &lt; 5 and $percentDiff &gt; -5">LOW
                                                    </xsl:when>
                                                    <xsl:when test="$percentDiff &lt; 10 and $percentDiff &gt; -10">
                                                        MEDIUM
                                                    </xsl:when>
                                                    <xsl:otherwise>HIGH</xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center">
                                                <xsl:variable name="difference"
                                                              select="$fundNAV - $totalPositionValue"/>
                                                <xsl:variable name="percentDiff"
                                                              select="($difference div $fundNAV) * 100"/>
                                                <xsl:choose>
                                                    <xsl:when test="$percentDiff &lt; 5 and $percentDiff &gt; -5">
                                                        <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                                    </xsl:when>
                                                    <xsl:when test="$percentDiff &lt; 10 and $percentDiff &gt; -10">
                                                        <fo:inline color="#ffc107" font-weight="bold">!</fo:inline>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>

                                    <!-- Check: Total Percentage Sum -->
                                    <fo:table-row background-color="#f8f9fa">
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block>Total Portfolio Percentage</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="right" font-size="8pt"
                                                      font-family="Courier, monospace">
                                                <xsl:value-of select="format-number($totalPercentage, '0.00')"/>%
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center" font-size="8pt">
                                                <xsl:choose>
                                                    <xsl:when
                                                            test="$totalPercentage &gt; 99 and $totalPercentage &lt; 101">
                                                        PASS
                                                    </xsl:when>
                                                    <xsl:when
                                                            test="$totalPercentage &gt; 98 and $totalPercentage &lt; 102">
                                                        WARN
                                                    </xsl:when>
                                                    <xsl:otherwise>FAIL</xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center">
                                                <xsl:choose>
                                                    <xsl:when
                                                            test="$totalPercentage &gt; 99 and $totalPercentage &lt; 101">
                                                        <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                                    </xsl:when>
                                                    <xsl:when
                                                            test="$totalPercentage &gt; 98 and $totalPercentage &lt; 102">
                                                        <fo:inline color="#ffc107" font-weight="bold">!</fo:inline>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>

                                    <!-- Check: Largest Position -->
                                    <fo:table-row>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block>Largest Single Position</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="right" font-size="8pt"
                                                      font-family="Courier, monospace">
                                                <xsl:for-each
                                                        select="FundDynamicData/Portfolios/Portfolio/Positions/Position">
                                                    <xsl:sort select="TotalPercentage" data-type="number"
                                                              order="descending"/>
                                                    <xsl:if test="position() = 1">
                                                        <xsl:value-of select="format-number(TotalPercentage, '0.00')"/>%
                                                    </xsl:if>
                                                </xsl:for-each>
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center" font-size="8pt">
                                                <xsl:variable name="maxPercentage">
                                                    <xsl:for-each
                                                            select="FundDynamicData/Portfolios/Portfolio/Positions/Position">
                                                        <xsl:sort select="TotalPercentage" data-type="number"
                                                                  order="descending"/>
                                                        <xsl:if test="position() = 1">
                                                            <xsl:value-of select="TotalPercentage"/>
                                                        </xsl:if>
                                                    </xsl:for-each>
                                                </xsl:variable>
                                                <xsl:choose>
                                                    <xsl:when test="$maxPercentage &lt; 10">LOW</xsl:when>
                                                    <xsl:when test="$maxPercentage &lt; 20">MEDIUM</xsl:when>
                                                    <xsl:otherwise>HIGH</xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center">
                                                <xsl:variable name="maxPercentage">
                                                    <xsl:for-each
                                                            select="FundDynamicData/Portfolios/Portfolio/Positions/Position">
                                                        <xsl:sort select="TotalPercentage" data-type="number"
                                                                  order="descending"/>
                                                        <xsl:if test="position() = 1">
                                                            <xsl:value-of select="TotalPercentage"/>
                                                        </xsl:if>
                                                    </xsl:for-each>
                                                </xsl:variable>
                                                <xsl:choose>
                                                    <xsl:when test="$maxPercentage &lt; 10">
                                                        <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                                    </xsl:when>
                                                    <xsl:when test="$maxPercentage &lt; 20">
                                                        <fo:inline color="#ffc107" font-weight="bold">!</fo:inline>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>

                                    <!-- Check: Smallest Position -->
                                    <fo:table-row background-color="#f8f9fa">
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block>Smallest Position</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="right" font-size="8pt"
                                                      font-family="Courier, monospace">
                                                <xsl:for-each
                                                        select="FundDynamicData/Portfolios/Portfolio/Positions/Position">
                                                    <xsl:sort select="TotalPercentage" data-type="number"
                                                              order="ascending"/>
                                                    <xsl:if test="position() = 1">
                                                        <xsl:value-of select="format-number(TotalPercentage, '0.000')"/>%
                                                    </xsl:if>
                                                </xsl:for-each>
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center" font-size="8pt">
                                                <xsl:variable name="minPercentage">
                                                    <xsl:for-each
                                                            select="FundDynamicData/Portfolios/Portfolio/Positions/Position">
                                                        <xsl:sort select="TotalPercentage" data-type="number"
                                                                  order="ascending"/>
                                                        <xsl:if test="position() = 1">
                                                            <xsl:value-of select="TotalPercentage"/>
                                                        </xsl:if>
                                                    </xsl:for-each>
                                                </xsl:variable>
                                                <xsl:choose>
                                                    <xsl:when test="$minPercentage &gt; 0">PASS</xsl:when>
                                                    <xsl:otherwise>WARN</xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center">
                                                <xsl:variable name="minPercentage">
                                                    <xsl:for-each
                                                            select="FundDynamicData/Portfolios/Portfolio/Positions/Position">
                                                        <xsl:sort select="TotalPercentage" data-type="number"
                                                                  order="ascending"/>
                                                        <xsl:if test="position() = 1">
                                                            <xsl:value-of select="TotalPercentage"/>
                                                        </xsl:if>
                                                    </xsl:for-each>
                                                </xsl:variable>
                                                <xsl:choose>
                                                    <xsl:when test="$minPercentage &gt; 0">
                                                        <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <fo:inline color="#ffc107" font-weight="bold">!</fo:inline>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>
                                </fo:table-body>
                            </fo:table>
                        </fo:block-container>
                    </xsl:for-each>

                    <!-- Summary -->
                    <fo:block-container background-color="#e83e8c" color="#ffffff" padding="8mm"
                                        space-before="10mm" id="position-end">
                        <fo:block font-size="18pt" font-weight="bold" space-after="5mm">
                            POSITION VALUE VALIDATION SUMMARY
                        </fo:block>
                        <fo:block space-after="3mm">
                            This comprehensive position value validation report analyzes
                            <xsl:value-of select="count(//Position)"/> positions
                            across
                            <xsl:value-of select="count(Funds/Fund)"/> fund(s).
                        </fo:block>
                        <fo:block space-after="3mm">
                            Analysis includes position value completeness, percentage accuracy,
                            currency consistency, NAV reconciliation, and portfolio concentration metrics.
                        </fo:block>
                        <fo:block font-style="italic">
                            Data Provider:
                            <xsl:value-of select="ControlData/DataSupplier/Name"/>
                        </fo:block>
                    </fo:block-container>
                </fo:flow>
            </fo:page-sequence>
        </fo:root>
    </xsl:template>

</xsl:stylesheet>
