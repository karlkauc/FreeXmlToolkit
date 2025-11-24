<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fo="http://www.w3.org/1999/XSL/Format">

    <xsl:output method="xml" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <xsl:key name="asset-by-id" match="Asset" use="UniqueID"/>
    <xsl:key name="asset-by-type" match="Asset" use="AssetType"/>

    <xsl:template match="/FundsXML4">
        <fo:root>
            <fo:layout-master-set>
                <fo:simple-page-master master-name="portfolio-page"
                                       page-height="29.7cm" page-width="21cm"
                                       margin-top="2cm" margin-bottom="2cm"
                                       margin-left="2.5cm" margin-right="2.5cm">
                    <fo:region-body margin-top="3cm" margin-bottom="2cm"/>
                    <fo:region-before region-name="header" extent="2.5cm"/>
                    <fo:region-after region-name="footer" extent="1.5cm"/>
                </fo:simple-page-master>
            </fo:layout-master-set>

            <fo:page-sequence master-reference="portfolio-page">
                <fo:static-content flow-name="header">
                    <fo:block-container background-color="#9c27b0" color="#ffffff" padding="8mm">
                        <fo:block font-size="22pt" font-weight="bold" text-align="center">
                            PORTFOLIO COMPOSITION ANALYSIS
                        </fo:block>
                        <fo:block font-size="12pt" text-align="center" space-before="3mm">
                            Detailed Holdings and Asset Allocation
                        </fo:block>
                    </fo:block-container>
                </fo:static-content>

                <fo:static-content flow-name="footer">
                    <fo:block-container border-top="2pt solid #9c27b0" padding-top="5mm">
                        <fo:table table-layout="fixed" width="100%">
                            <fo:table-column column-width="50%"/>
                            <fo:table-column column-width="50%"/>
                            <fo:table-body>
                                <fo:table-row>
                                    <fo:table-cell>
                                        <fo:block font-size="8pt" color="#9c27b0">PORTFOLIO COMPOSITION</fo:block>
                                    </fo:table-cell>
                                    <fo:table-cell>
                                        <fo:block font-size="8pt" text-align="right">
                                            Page
                                            <fo:page-number/>
                                        </fo:block>
                                    </fo:table-cell>
                                </fo:table-row>
                            </fo:table-body>
                        </fo:table>
                    </fo:block-container>
                </fo:static-content>

                <fo:flow flow-name="xsl-region-body" font-family="Arial, Helvetica, sans-serif" font-size="10pt">

                    <xsl:for-each select="Funds/Fund">
                        <fo:block-container space-after="10mm">
                            <fo:block-container background-color="#9c27b0" color="#ffffff" padding="6mm"
                                                space-after="6mm">
                                <fo:block font-size="16pt" font-weight="bold">
                                    <xsl:value-of select="Names/OfficialName"/>
                                </fo:block>
                                <fo:block font-size="10pt" space-before="2mm">
                                    Total Positions:
                                    <xsl:value-of
                                            select="count(FundDynamicData/Portfolios/Portfolio/Positions/Position)"/>
                                </fo:block>
                            </fo:block-container>

                            <!-- Asset Type Breakdown -->
                            <fo:block font-size="14pt" font-weight="bold" space-after="4mm">
                                Asset Type Allocation
                            </fo:block>

                            <fo:table table-layout="fixed" width="100%" border="1pt solid #dee2e6" space-after="8mm">
                                <fo:table-column column-width="35%"/>
                                <fo:table-column column-width="25%"/>
                                <fo:table-column column-width="20%"/>
                                <fo:table-column column-width="20%"/>

                                <fo:table-header background-color="#f8f9fa">
                                    <fo:table-row>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                            <fo:block font-weight="bold">Asset Type</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                            <fo:block font-weight="bold" text-align="right">Total Value</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                            <fo:block font-weight="bold" text-align="right">Percentage</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                            <fo:block font-weight="bold" text-align="center">Count</fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>
                                </fo:table-header>

                                <fo:table-body>
                                    <xsl:for-each
                                            select="FundDynamicData/Portfolios/Portfolio/Positions/Position">
                                        <xsl:variable name="assetID" select="UniqueID"/>
                                        <xsl:variable name="assetType" select="key('asset-by-id', $assetID)/AssetType"/>

                                        <xsl:if
                                                test="not($assetType = preceding-sibling::Position/key('asset-by-id', UniqueID)/AssetType)">
                                            <xsl:variable name="typeTotal"
                                                          select="sum(../Position[key('asset-by-id', UniqueID)/AssetType = $assetType]/TotalValue/Amount)"/>
                                            <xsl:variable name="typePercentage"
                                                          select="sum(../Position[key('asset-by-id', UniqueID)/AssetType = $assetType]/TotalPercentage)"/>
                                            <xsl:variable name="typeCount"
                                                          select="count(../Position[key('asset-by-id', UniqueID)/AssetType = $assetType])"/>

                                            <fo:table-row>
                                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                                    <fo:block>
                                                        <xsl:choose>
                                                            <xsl:when test="$assetType">
                                                                <xsl:value-of select="$assetType"/>
                                                            </xsl:when>
                                                            <xsl:otherwise>Unknown</xsl:otherwise>
                                                        </xsl:choose>
                                                    </fo:block>
                                                </fo:table-cell>
                                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                                    <fo:block text-align="right" font-family="Courier, monospace"
                                                              font-size="9pt">
                                                        <xsl:value-of select="format-number($typeTotal, '#,##0.00')"/>
                                                    </fo:block>
                                                </fo:table-cell>
                                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                                    <fo:block text-align="right" font-weight="bold">
                                                        <xsl:value-of select="format-number($typePercentage, '0.00')"/>%
                                                    </fo:block>
                                                </fo:table-cell>
                                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                                    <fo:block text-align="center">
                                                        <xsl:value-of select="$typeCount"/>
                                                    </fo:block>
                                                </fo:table-cell>
                                            </fo:table-row>
                                        </xsl:if>
                                    </xsl:for-each>
                                </fo:table-body>
                            </fo:table>

                            <!-- Top 20 Holdings -->
                            <fo:block font-size="14pt" font-weight="bold" space-after="4mm">
                                Top 20 Holdings
                            </fo:block>

                            <fo:table table-layout="fixed" width="100%" border="1pt solid #dee2e6" space-after="8mm">
                                <fo:table-column column-width="5%"/>
                                <fo:table-column column-width="35%"/>
                                <fo:table-column column-width="15%"/>
                                <fo:table-column column-width="15%"/>
                                <fo:table-column column-width="15%"/>
                                <fo:table-column column-width="15%"/>

                                <fo:table-header background-color="#f3e5f5">
                                    <fo:table-row>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                            <fo:block font-weight="bold">#</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                            <fo:block font-weight="bold">Asset Name</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                            <fo:block font-weight="bold">ISIN</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                            <fo:block font-weight="bold">Type</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                            <fo:block font-weight="bold" text-align="right">Value</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                            <fo:block font-weight="bold" text-align="right">%</fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>
                                </fo:table-header>

                                <fo:table-body>
                                    <xsl:for-each
                                            select="FundDynamicData/Portfolios/Portfolio/Positions/Position">
                                        <xsl:sort select="TotalPercentage" data-type="number" order="descending"/>
                                        <xsl:if test="position() &lt;= 20">
                                            <xsl:variable name="assetID" select="UniqueID"/>
                                            <fo:table-row>
                                                <xsl:if test="position() mod 2 = 0">
                                                    <xsl:attribute name="background-color">#f8f9fa</xsl:attribute>
                                                </xsl:if>
                                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                                    <fo:block text-align="center" font-size="8pt">
                                                        <xsl:value-of select="position()"/>
                                                    </fo:block>
                                                </fo:table-cell>
                                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                                    <fo:block font-size="8pt">
                                                        <xsl:value-of select="key('asset-by-id', $assetID)/Name"/>
                                                    </fo:block>
                                                </fo:table-cell>
                                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                                    <fo:block font-size="8pt">
                                                        <xsl:value-of
                                                                select="key('asset-by-id', $assetID)/Identifiers/ISIN"/>
                                                    </fo:block>
                                                </fo:table-cell>
                                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                                    <fo:block font-size="8pt">
                                                        <xsl:value-of select="key('asset-by-id', $assetID)/AssetType"/>
                                                    </fo:block>
                                                </fo:table-cell>
                                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                                    <fo:block text-align="right" font-size="8pt"
                                                              font-family="Courier, monospace">
                                                        <xsl:value-of
                                                                select="format-number(TotalValue/Amount, '#,##0.00')"/>
                                                    </fo:block>
                                                </fo:table-cell>
                                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                                    <fo:block text-align="right" font-size="8pt" font-weight="bold"
                                                              color="#9c27b0">
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

                </fo:flow>
            </fo:page-sequence>
        </fo:root>
    </xsl:template>

</xsl:stylesheet>
