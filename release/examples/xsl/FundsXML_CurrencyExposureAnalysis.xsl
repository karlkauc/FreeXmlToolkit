<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fo="http://www.w3.org/1999/XSL/Format">

    <xsl:output method="xml" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <!-- Main template for Currency Exposure Analysis Report -->
    <xsl:template match="/FundsXML4">
        <fo:root>
            <!-- Page Layout Definition -->
            <fo:layout-master-set>
                <fo:simple-page-master master-name="currency-page"
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
            <fo:page-sequence master-reference="currency-page">
                <!-- Header -->
                <fo:static-content flow-name="header">
                    <fo:block-container background-color="#6c757d" color="#ffffff" padding="8mm">
                        <fo:block font-size="22pt" font-weight="bold" text-align="center">
                            CURRENCY EXPOSURE ANALYSIS
                        </fo:block>
                        <fo:block font-size="12pt" text-align="center" space-before="3mm">
                            Foreign Exchange Exposure and Risk Assessment
                        </fo:block>
                        <fo:block text-align="center" font-size="10pt" space-before="2mm">
                            Currencies:
                            <xsl:value-of
                                    select="count(//Position/Currency[not(. = preceding::Position/Currency)])"/> •
                            FX Rates:
                            <xsl:value-of select="count(//FXRate)"/>
                        </fo:block>
                    </fo:block-container>
                </fo:static-content>

                <!-- Footer -->
                <fo:static-content flow-name="footer">
                    <fo:block-container border-top="2pt solid #6c757d" padding-top="5mm">
                        <fo:table table-layout="fixed" width="100%">
                            <fo:table-column column-width="40%"/>
                            <fo:table-column column-width="20%"/>
                            <fo:table-column column-width="40%"/>
                            <fo:table-body>
                                <fo:table-row>
                                    <fo:table-cell>
                                        <fo:block font-size="8pt" color="#6c757d" font-weight="bold">
                                            CURRENCY EXPOSURE ANALYSIS
                                        </fo:block>
                                    </fo:table-cell>
                                    <fo:table-cell>
                                        <fo:block font-size="8pt" color="#666666" text-align="center">
                                            Page
                                            <fo:page-number/>
                                        </fo:block>
                                    </fo:table-cell>
                                    <fo:table-cell>
                                        <fo:block font-size="8pt" color="#666666" text-align="right">
                                            <xsl:value-of select="ControlData/ContentDate"/>
                                        </fo:block>
                                    </fo:table-cell>
                                </fo:table-row>
                            </fo:table-body>
                        </fo:table>
                    </fo:block-container>
                </fo:static-content>

                <!-- Main Content -->
                <fo:flow flow-name="xsl-region-body" font-family="Arial, Helvetica, sans-serif" font-size="10pt">

                    <!-- Currency Distribution per Fund -->
                    <xsl:for-each select="Funds/Fund">
                        <xsl:variable name="fundCurrency" select="Currency"/>

                        <fo:block-container space-after="10mm">
                            <fo:block-container background-color="#6c757d" color="#ffffff" padding="5mm"
                                                space-after="5mm">
                                <fo:block font-size="16pt" font-weight="bold">
                                    <xsl:value-of select="Names/OfficialName"/>
                                </fo:block>
                                <fo:block font-size="10pt">
                                    Base Currency:
                                    <xsl:value-of select="$fundCurrency"/>
                                </fo:block>
                            </fo:block-container>

                            <!-- Currency Breakdown Table -->
                            <fo:table table-layout="fixed" width="100%" border="1pt solid #dee2e6" space-after="6mm">
                                <fo:table-column column-width="20%"/>
                                <fo:table-column column-width="25%"/>
                                <fo:table-column column-width="25%"/>
                                <fo:table-column column-width="15%"/>
                                <fo:table-column column-width="15%"/>

                                <fo:table-header background-color="#f8f9fa">
                                    <fo:table-row>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                            <fo:block font-weight="bold">Currency</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                            <fo:block font-weight="bold" text-align="right">Total Value</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                            <fo:block font-weight="bold" text-align="right">Percentage</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                            <fo:block font-weight="bold" text-align="center">Positions</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                            <fo:block font-weight="bold" text-align="center">Risk</fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>
                                </fo:table-header>

                                <fo:table-body>
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

                                        <fo:table-row>
                                            <xsl:if test="position() mod 2 = 0">
                                                <xsl:attribute name="background-color">#f8f9fa</xsl:attribute>
                                            </xsl:if>
                                            <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                                <fo:block font-weight="bold" color="#6c757d">
                                                    <xsl:value-of select="$currency"/>
                                                </fo:block>
                                            </fo:table-cell>
                                            <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                                <fo:block text-align="right" font-family="Courier, monospace"
                                                          font-size="9pt">
                                                    <xsl:value-of select="format-number($totalValue, '#,##0.00')"/>
                                                </fo:block>
                                            </fo:table-cell>
                                            <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                                <fo:block text-align="right" font-family="Courier, monospace"
                                                          font-size="9pt">
                                                    <xsl:value-of select="format-number($percentage, '0.00')"/>%
                                                </fo:block>
                                            </fo:table-cell>
                                            <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                                <fo:block text-align="center">
                                                    <xsl:value-of select="$positionCount"/>
                                                </fo:block>
                                            </fo:table-cell>
                                            <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                                <fo:block text-align="center">
                                                    <xsl:choose>
                                                        <xsl:when test="$currency = $fundCurrency">
                                                            <fo:inline color="#28a745">BASE</fo:inline>
                                                        </xsl:when>
                                                        <xsl:when test="$percentage &lt; 10">
                                                            <fo:inline color="#28a745">LOW</fo:inline>
                                                        </xsl:when>
                                                        <xsl:when test="$percentage &lt; 25">
                                                            <fo:inline color="#ffc107">MED</fo:inline>
                                                        </xsl:when>
                                                        <xsl:otherwise>
                                                            <fo:inline color="#dc3545">HIGH</fo:inline>
                                                        </xsl:otherwise>
                                                    </xsl:choose>
                                                </fo:block>
                                            </fo:table-cell>
                                        </fo:table-row>
                                    </xsl:for-each>
                                </fo:table-body>
                            </fo:table>

                            <!-- FX Rates Quality Check -->
                            <fo:block font-size="14pt" font-weight="bold" space-after="4mm">
                                FX Rates Data Quality
                            </fo:block>

                            <fo:table table-layout="fixed" width="100%" border="1pt solid #dee2e6" space-after="8mm">
                                <fo:table-column column-width="40%"/>
                                <fo:table-column column-width="30%"/>
                                <fo:table-column column-width="20%"/>
                                <fo:table-column column-width="10%"/>

                                <fo:table-header background-color="#f8f9fa">
                                    <fo:table-row>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                            <fo:block font-weight="bold">Quality Check</fo:block>
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
                                    <fo:table-row>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block>FX Rates Present</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block font-size="9pt">All positions have FX rates</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center" font-size="9pt">
                                                <xsl:choose>
                                                    <xsl:when
                                                            test="count(FundDynamicData/Portfolios/Portfolio/Positions/Position[not(FXRates/FXRate)]) = 0">
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
                                                            test="count(FundDynamicData/Portfolios/Portfolio/Positions/Position[not(FXRates/FXRate)]) = 0">
                                                        <fo:inline color="#28a745">✓</fo:inline>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <fo:inline color="#dc3545">✗</fo:inline>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>

                                    <fo:table-row background-color="#f8f9fa">
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block>Valid FX Rate Values</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block font-size="9pt">All rates are positive</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center" font-size="9pt">
                                                <xsl:choose>
                                                    <xsl:when
                                                            test="count(FundDynamicData/Portfolios/Portfolio/Positions/Position/FXRates/FXRate[. &lt;= 0]) = 0">
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
                                                            test="count(FundDynamicData/Portfolios/Portfolio/Positions/Position/FXRates/FXRate[. &lt;= 0]) = 0">
                                                        <fo:inline color="#28a745">✓</fo:inline>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <fo:inline color="#dc3545">✗</fo:inline>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>
                                </fo:table-body>
                            </fo:table>
                        </fo:block-container>
                    </xsl:for-each>

                </fo:flow>
            </fo:page-sequence>
        </fo:root>
    </xsl:template>

</xsl:stylesheet>
