<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fo="http://www.w3.org/1999/XSL/Format">

    <xsl:output method="xml" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <!-- Main template for Executive Summary -->
    <xsl:template match="/FundsXML4">
        <fo:root>
            <!-- Page Layout Definition -->
            <fo:layout-master-set>
                <fo:simple-page-master master-name="executive-summary-page"
                                       page-height="29.7cm"
                                       page-width="21cm"
                                       margin-top="1.5cm"
                                       margin-bottom="1.5cm"
                                       margin-left="2cm"
                                       margin-right="2cm">
                    <fo:region-body margin-top="2cm" margin-bottom="1.5cm"/>
                    <fo:region-before region-name="header" extent="1.5cm"/>
                    <fo:region-after region-name="footer" extent="1cm"/>
                </fo:simple-page-master>
            </fo:layout-master-set>

            <!-- Page Sequence -->
            <fo:page-sequence master-reference="executive-summary-page">
                <!-- Header -->
                <fo:static-content flow-name="header">
                    <fo:block text-align="center" font-size="8pt" color="#666666"
                              border-bottom="0.5pt solid #cccccc" padding-bottom="3mm">
                        FundsXML Executive Summary • Generated on
                        <xsl:value-of select="ControlData/DocumentGenerated"/>
                    </fo:block>
                </fo:static-content>

                <!-- Footer -->
                <fo:static-content flow-name="footer">
                    <fo:block text-align="center" font-size="8pt" color="#666666"
                              border-top="0.5pt solid #cccccc" padding-top="3mm">
                        Page
                        <fo:page-number/>
                        • Document ID:
                        <xsl:value-of select="ControlData/UniqueDocumentID"/>
                    </fo:block>
                </fo:static-content>

                <!-- Main Content Flow -->
                <fo:flow flow-name="xsl-region-body" font-family="Arial, Helvetica, sans-serif" font-size="10pt">
                    <!-- Document Title -->
                    <fo:block font-size="24pt" font-weight="bold" text-align="center"
                              color="#2c3e50" space-after="8mm">
                        EXECUTIVE SUMMARY
                    </fo:block>

                    <fo:block font-size="14pt" text-align="center" color="#7f8c8d" space-after="12mm">
                        Fund Performance and Risk Overview
                    </fo:block>

                    <!-- Key Metrics Dashboard -->
                    <fo:block-container background-color="#ecf0f1" padding="8mm" space-after="8mm"
                                        border="1pt solid #bdc3c7">
                        <fo:block font-size="16pt" font-weight="bold" color="#34495e" space-after="5mm">
                            📊 Key Performance Indicators
                        </fo:block>

                        <fo:table table-layout="fixed" width="100%">
                            <fo:table-column column-width="25%"/>
                            <fo:table-column column-width="25%"/>
                            <fo:table-column column-width="25%"/>
                            <fo:table-column column-width="25%"/>
                            <fo:table-body>
                                <fo:table-row>
                                    <xsl:for-each select="Funds/Fund[position() &lt;= 4]">
                                        <fo:table-cell padding="3mm">
                                            <fo:block-container background-color="#ffffff" padding="4mm"
                                                                border="1pt solid #d5dbdb" text-align="center">
                                                <fo:block font-weight="bold" font-size="8pt" color="#7f8c8d">
                                                    FUND
                                                    <xsl:value-of select="position()"/>
                                                </fo:block>
                                                <fo:block font-size="14pt" font-weight="bold" color="#2980b9"
                                                          space-after="2mm">
                                                    <xsl:value-of
                                                            select="format-number(FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount div 1000000, '0.0')"/>M
                                                </fo:block>
                                                <fo:block font-size="8pt" color="#34495e">
                                                    <xsl:value-of
                                                            select="FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount/@ccy"/>
                                                    NAV
                                                </fo:block>
                                                <fo:block font-size="8pt" color="#7f8c8d" space-before="1mm">
                                                    <xsl:value-of
                                                            select="count(FundDynamicData/Portfolios/Portfolio/Positions/Position)"/>
                                                    Positions
                                                </fo:block>
                                            </fo:block-container>
                                        </fo:table-cell>
                                    </xsl:for-each>
                                </fo:table-row>
                            </fo:table-body>
                        </fo:table>
                    </fo:block-container>

                    <!-- Fund Overview Section -->
                    <xsl:for-each select="Funds/Fund">
                        <fo:block-container space-after="10mm" keep-together.within-page="always">
                            <!-- Fund Header -->
                            <fo:block font-size="18pt" font-weight="bold" color="#27ae60"
                                      background-color="#e8f8f5" padding="4mm" space-after="5mm"
                                      border-left="4pt solid #27ae60">
                                💼
                                <xsl:value-of select="Names/OfficialName"/>
                            </fo:block>

                            <!-- Fund Details Grid -->
                            <fo:table table-layout="fixed" width="100%" space-after="6mm">
                                <fo:table-column column-width="50%"/>
                                <fo:table-column column-width="50%"/>
                                <fo:table-body>
                                    <fo:table-row>
                                        <!-- Left Column -->
                                        <fo:table-cell padding-right="4mm">
                                            <fo:block-container background-color="#f8f9fa" padding="5mm"
                                                                border="1pt solid #dee2e6">
                                                <fo:block font-weight="bold" color="#495057" space-after="3mm">
                                                    Fund Information
                                                </fo:block>
                                                <fo:block space-after="2mm">
                                                    <fo:inline font-weight="bold">LEI:</fo:inline>
                                                    <xsl:value-of select="Identifiers/LEI"/>
                                                </fo:block>
                                                <fo:block space-after="2mm">
                                                    <fo:inline font-weight="bold">Currency:</fo:inline>
                                                    <xsl:value-of select="Currency"/>
                                                </fo:block>
                                                <fo:block space-after="2mm">
                                                    <fo:inline font-weight="bold">Inception:</fo:inline>
                                                    <xsl:value-of select="FundStaticData/InceptionDate"/>
                                                </fo:block>
                                                <fo:block>
                                                    <fo:inline font-weight="bold">Structure:</fo:inline>
                                                    <xsl:value-of select="FundStaticData/ListedLegalStructure"/>
                                                </fo:block>
                                            </fo:block-container>
                                        </fo:table-cell>

                                        <!-- Right Column -->
                                        <fo:table-cell padding-left="4mm">
                                            <fo:block-container background-color="#fff3cd" padding="5mm"
                                                                border="1pt solid #ffeaa7">
                                                <fo:block font-weight="bold" color="#856404" space-after="3mm">
                                                    Financial Metrics
                                                </fo:block>
                                                <fo:block space-after="2mm">
                                                    <fo:inline font-weight="bold">Total NAV:</fo:inline>
                                                    <xsl:value-of
                                                            select="format-number(FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount, '#,##0.00')"/>
                                                    <xsl:text> </xsl:text>
                                                    <xsl:value-of
                                                            select="FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount/@ccy"/>
                                                </fo:block>
                                                <fo:block space-after="2mm">
                                                    <fo:inline font-weight="bold">Positions:</fo:inline>
                                                    <xsl:value-of
                                                            select="count(FundDynamicData/Portfolios/Portfolio/Positions/Position)"/>
                                                </fo:block>
                                                <fo:block>
                                                    <fo:inline font-weight="bold">NAV Date:</fo:inline>
                                                    <xsl:value-of
                                                            select="FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate"/>
                                                </fo:block>
                                            </fo:block-container>
                                        </fo:table-cell>
                                    </fo:table-row>
                                </fo:table-body>
                            </fo:table>

                            <!-- Top 5 Positions -->
                            <fo:block font-size="14pt" font-weight="bold" color="#e74c3c" space-after="4mm">
                                🎯 Top 5 Holdings
                            </fo:block>

                            <fo:table table-layout="fixed" width="100%" border-collapse="collapse"
                                      border="1pt solid #dee2e6" space-after="8mm">
                                <fo:table-column column-width="10%"/>
                                <fo:table-column column-width="40%"/>
                                <fo:table-column column-width="25%"/>
                                <fo:table-column column-width="25%"/>

                                <fo:table-header background-color="#f8f9fa">
                                    <fo:table-row>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                            <fo:block font-weight="bold" text-align="center">#</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                            <fo:block font-weight="bold">Position ID</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                            <fo:block font-weight="bold" text-align="right">Value</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                            <fo:block font-weight="bold" text-align="right">Weight</fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>
                                </fo:table-header>

                                <fo:table-body>
                                    <xsl:for-each select="FundDynamicData/Portfolios/Portfolio/Positions/Position">
                                        <xsl:sort select="TotalPercentage" data-type="number" order="descending"/>
                                        <xsl:if test="position() &lt;= 5">
                                            <fo:table-row>
                                                <xsl:if test="position() mod 2 = 0">
                                                    <xsl:attribute name="background-color">#f8f9fa</xsl:attribute>
                                                </xsl:if>
                                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                                    <fo:block text-align="center" font-weight="bold" color="#e74c3c">
                                                        <xsl:value-of select="position()"/>
                                                    </fo:block>
                                                </fo:table-cell>
                                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                                    <fo:block font-size="8pt">
                                                        <xsl:value-of select="UniqueID"/>
                                                    </fo:block>
                                                </fo:table-cell>
                                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                                    <fo:block text-align="right" font-size="8pt">
                                                        <xsl:value-of
                                                                select="format-number(TotalValue/Amount, '#,##0')"/>
                                                        <xsl:text> </xsl:text>
                                                        <xsl:value-of select="TotalValue/Amount/@ccy"/>
                                                    </fo:block>
                                                </fo:table-cell>
                                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                                    <fo:block text-align="right" font-weight="bold" color="#27ae60">
                                                        <xsl:value-of select="format-number(TotalPercentage, '0.00')"/>%
                                                    </fo:block>
                                                </fo:table-cell>
                                            </fo:table-row>
                                        </xsl:if>
                                    </xsl:for-each>
                                </fo:table-body>
                            </fo:table>
                        </fo:block-container>
                    </xsl:for-each>

                    <!-- Summary Section -->
                    <fo:block-container background-color="#2c3e50" color="#ffffff" padding="8mm"
                                        space-before="10mm">
                        <fo:block font-size="16pt" font-weight="bold" space-after="5mm">
                            📈 Portfolio Summary
                        </fo:block>
                        <fo:block>
                            This executive summary provides an overview of
                            <xsl:value-of select="count(Funds/Fund)"/> fund(s)
                            with a combined portfolio containing
                            <xsl:value-of select="count(//Position)"/> positions.
                            The analysis is based on data as of <xsl:value-of select="ControlData/ContentDate"/>.
                        </fo:block>
                        <fo:block space-before="3mm" font-style="italic">
                            Data provided by:
                            <xsl:value-of select="ControlData/DataSupplier/Name"/>
                        </fo:block>
                    </fo:block-container>
                </fo:flow>
            </fo:page-sequence>
        </fo:root>
    </xsl:template>

</xsl:stylesheet>