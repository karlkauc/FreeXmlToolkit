<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fo="http://www.w3.org/1999/XSL/Format">

    <xsl:output method="xml" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <xsl:template match="/FundsXML4">
        <fo:root>
            <fo:layout-master-set>
                <fo:simple-page-master master-name="exposure-page"
                                       page-height="29.7cm" page-width="21cm"
                                       margin-top="2cm" margin-bottom="2cm"
                                       margin-left="2.5cm" margin-right="2.5cm">
                    <fo:region-body margin-top="3cm" margin-bottom="2cm"/>
                    <fo:region-before region-name="header" extent="2.5cm"/>
                    <fo:region-after region-name="footer" extent="1.5cm"/>
                </fo:simple-page-master>
            </fo:layout-master-set>

            <fo:page-sequence master-reference="exposure-page">
                <fo:static-content flow-name="header">
                    <fo:block-container background-color="#ff5722" color="#ffffff" padding="8mm">
                        <fo:block font-size="22pt" font-weight="bold" text-align="center">
                            EXPOSURE ANALYSIS REPORT
                        </fo:block>
                        <fo:block font-size="12pt" text-align="center" space-before="3mm">
                            Derivatives and Market Exposure Assessment
                        </fo:block>
                    </fo:block-container>
                </fo:static-content>

                <fo:static-content flow-name="footer">
                    <fo:block-container border-top="2pt solid #ff5722" padding-top="5mm">
                        <fo:table table-layout="fixed" width="100%">
                            <fo:table-column column-width="50%"/>
                            <fo:table-column column-width="50%"/>
                            <fo:table-body>
                                <fo:table-row>
                                    <fo:table-cell>
                                        <fo:block font-size="8pt" color="#ff5722">EXPOSURE ANALYSIS</fo:block>
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

                    <!-- Executive Summary -->
                    <fo:block-container background-color="#ffe8e0" padding="8mm" space-after="8mm"
                                        border="2pt solid #ff5722">
                        <fo:block font-size="16pt" font-weight="bold" color="#bf360c" space-after="5mm">
                            EXPOSURE OVERVIEW
                        </fo:block>

                        <fo:table table-layout="fixed" width="100%">
                            <fo:table-column column-width="33%"/>
                            <fo:table-column column-width="33%"/>
                            <fo:table-column column-width="34%"/>
                            <fo:table-body>
                                <fo:table-row>
                                    <fo:table-cell padding="3mm">
                                        <fo:block-container background-color="#ffffff" padding="4mm"
                                                            border="1pt solid #ff5722" text-align="center">
                                            <fo:block font-size="8pt" color="#666666">POSITIONS WITH EXPOSURE</fo:block>
                                            <fo:block font-size="18pt" font-weight="bold" color="#ff5722">
                                                <xsl:value-of select="count(//Position[Exposures/Exposure])"/>
                                            </fo:block>
                                        </fo:block-container>
                                    </fo:table-cell>
                                    <fo:table-cell padding="3mm">
                                        <fo:block-container background-color="#ffffff" padding="4mm"
                                                            border="1pt solid #ff5722" text-align="center">
                                            <fo:block font-size="8pt" color="#666666">TOTAL EXPOSURES</fo:block>
                                            <fo:block font-size="18pt" font-weight="bold" color="#ff5722">
                                                <xsl:value-of select="count(//Exposure)"/>
                                            </fo:block>
                                        </fo:block-container>
                                    </fo:table-cell>
                                    <fo:table-cell padding="3mm">
                                        <fo:block-container background-color="#ffffff" padding="4mm"
                                                            border="1pt solid #ff5722" text-align="center">
                                            <fo:block font-size="8pt" color="#666666">EXPOSURE TYPES</fo:block>
                                            <fo:block font-size="18pt" font-weight="bold" color="#ff5722">
                                                <xsl:value-of
                                                        select="count(//Exposure/Type[not(. = preceding::Exposure/Type)])"/>
                                            </fo:block>
                                        </fo:block-container>
                                    </fo:table-cell>
                                </fo:table-row>
                            </fo:table-body>
                        </fo:table>
                    </fo:block-container>

                    <!-- Exposure Data Quality Validation -->
                    <fo:block font-size="18pt" font-weight="bold" color="#495057" space-after="5mm"
                              border-bottom="2pt solid #ff5722" padding-bottom="3mm">
                        EXPOSURE DATA QUALITY
                    </fo:block>

                    <fo:table table-layout="fixed" width="100%" border="1pt solid #dee2e6" space-after="8mm">
                        <fo:table-column column-width="5%"/>
                        <fo:table-column column-width="45%"/>
                        <fo:table-column column-width="25%"/>
                        <fo:table-column column-width="15%"/>
                        <fo:table-column column-width="10%"/>

                        <fo:table-header background-color="#f8f9fa">
                            <fo:table-row>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                    <fo:block font-weight="bold">#</fo:block>
                                </fo:table-cell>
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
                            <!-- DQ Check 1 -->
                            <fo:table-row>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">1</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">Exposure Type Present</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">All exposures have type defined</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-size="9pt">
                                        <xsl:choose>
                                            <xsl:when test="count(//Exposure[not(Type)]) = 0">PASS</xsl:when>
                                            <xsl:otherwise>FAIL</xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="count(//Exposure[not(Type)]) = 0">
                                                <fo:inline color="#28a745">✓</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545">✗</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- DQ Check 2 -->
                            <fo:table-row background-color="#f8f9fa">
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">2</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">Exposure Value Present</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">All exposures have value</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-size="9pt">
                                        <xsl:choose>
                                            <xsl:when test="count(//Exposure[not(Value)]) = 0">PASS</xsl:when>
                                            <xsl:otherwise>FAIL</xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="count(//Exposure[not(Value)]) = 0">
                                                <fo:inline color="#28a745">✓</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545">✗</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- DQ Check 3 -->
                            <fo:table-row>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">3</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">Positive Exposure Values</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">Exposure values are valid</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-size="9pt">
                                        <xsl:choose>
                                            <xsl:when test="count(//Exposure/Value/Amount[. &lt; 0]) = 0">PASS
                                            </xsl:when>
                                            <xsl:otherwise>WARN</xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="count(//Exposure/Value/Amount[. &lt; 0]) = 0">
                                                <fo:inline color="#28a745">✓</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#ffc107">!</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>
                        </fo:table-body>
                    </fo:table>

                    <!-- Detailed Exposure Analysis per Fund -->
                    <xsl:for-each select="Funds/Fund">
                        <xsl:if test="FundDynamicData/Portfolios/Portfolio/Positions/Position/Exposures/Exposure">
                            <fo:block-container space-after="10mm">
                                <fo:block-container background-color="#ff5722" color="#ffffff" padding="5mm"
                                                    space-after="5mm">
                                    <fo:block font-size="14pt" font-weight="bold">
                                        <xsl:value-of select="Names/OfficialName"/>
                                    </fo:block>
                                </fo:block-container>

                                <!-- Exposure Types Summary -->
                                <fo:table table-layout="fixed" width="100%" border="1pt solid #dee2e6"
                                          space-after="6mm">
                                    <fo:table-column column-width="50%"/>
                                    <fo:table-column column-width="25%"/>
                                    <fo:table-column column-width="25%"/>

                                    <fo:table-header background-color="#ffe8e0">
                                        <fo:table-row>
                                            <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                                <fo:block font-weight="bold">Exposure Type</fo:block>
                                            </fo:table-cell>
                                            <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                                <fo:block font-weight="bold" text-align="right">Total Value</fo:block>
                                            </fo:table-cell>
                                            <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                                <fo:block font-weight="bold" text-align="center">Positions</fo:block>
                                            </fo:table-cell>
                                        </fo:table-row>
                                    </fo:table-header>

                                    <fo:table-body>
                                        <xsl:for-each
                                                select="FundDynamicData/Portfolios/Portfolio/Positions/Position/Exposures/Exposure/Type[not(. = preceding::Type)]">
                                            <xsl:variable name="exposureType" select="."/>
                                            <xsl:variable name="totalExposure"
                                                          select="sum(../../../../Position/Exposures/Exposure[Type = $exposureType]/Value/Amount)"/>
                                            <xsl:variable name="positionCount"
                                                          select="count(../../../../Position[Exposures/Exposure/Type = $exposureType])"/>

                                            <fo:table-row>
                                                <xsl:if test="position() mod 2 = 0">
                                                    <xsl:attribute name="background-color">#f8f9fa</xsl:attribute>
                                                </xsl:if>
                                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                                    <fo:block>
                                                        <xsl:value-of select="$exposureType"/>
                                                    </fo:block>
                                                </fo:table-cell>
                                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                                    <fo:block text-align="right" font-family="Courier, monospace"
                                                              font-size="9pt">
                                                        <xsl:value-of
                                                                select="format-number($totalExposure, '#,##0.00')"/>
                                                    </fo:block>
                                                </fo:table-cell>
                                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                                    <fo:block text-align="center">
                                                        <xsl:value-of select="$positionCount"/>
                                                    </fo:block>
                                                </fo:table-cell>
                                            </fo:table-row>
                                        </xsl:for-each>
                                    </fo:table-body>
                                </fo:table>
                            </fo:block-container>
                        </xsl:if>
                    </xsl:for-each>

                </fo:flow>
            </fo:page-sequence>
        </fo:root>
    </xsl:template>

</xsl:stylesheet>
