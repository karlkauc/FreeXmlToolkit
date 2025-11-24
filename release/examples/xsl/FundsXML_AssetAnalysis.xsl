<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fo="http://www.w3.org/1999/XSL/Format">

    <xsl:output method="xml" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <!-- Key for fast asset lookup -->
    <xsl:key name="asset-by-id" match="Asset" use="UniqueID"/>
    <xsl:key name="asset-by-type" match="Asset" use="AssetType"/>

    <!-- Main template for Asset Analysis Report -->
    <xsl:template match="/FundsXML4">
        <fo:root>
            <!-- Page Layout Definition -->
            <fo:layout-master-set>
                <fo:simple-page-master master-name="asset-analysis-page"
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
            <fo:page-sequence master-reference="asset-analysis-page">
                <!-- Header -->
                <fo:static-content flow-name="header">
                    <fo:block-container background-color="#20c997" color="#ffffff" padding="8mm">
                        <fo:block font-size="22pt" font-weight="bold" text-align="center">
                            ASSET ANALYSIS REPORT
                        </fo:block>
                        <fo:block font-size="12pt" text-align="center" space-before="3mm">
                            Comprehensive Asset Type Distribution and Quality Analysis
                        </fo:block>
                        <fo:block text-align="center" font-size="10pt" space-before="2mm">
                            Analysis Date:
                            <xsl:value-of select="ControlData/ContentDate"/> •
                            Total Assets:
                            <xsl:value-of select="count(//Asset)"/>
                        </fo:block>
                    </fo:block-container>
                </fo:static-content>

                <!-- Footer -->
                <fo:static-content flow-name="footer">
                    <fo:block-container border-top="2pt solid #20c997" padding-top="5mm">
                        <fo:table table-layout="fixed" width="100%">
                            <fo:table-column column-width="40%"/>
                            <fo:table-column column-width="20%"/>
                            <fo:table-column column-width="40%"/>
                            <fo:table-body>
                                <fo:table-row>
                                    <fo:table-cell>
                                        <fo:block font-size="8pt" color="#20c997" font-weight="bold">
                                            ASSET ANALYSIS
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
                                            <fo:page-number-citation ref-id="asset-end"/>
                                        </fo:block>
                                    </fo:table-cell>
                                    <fo:table-cell>
                                        <fo:block font-size="8pt" color="#666666" text-align="right">
                                            Doc ID:
                                            <xsl:value-of select="ControlData/UniqueDocumentID"/>
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
                    <fo:block-container background-color="#d1f2eb" padding="8mm" space-after="8mm"
                                        border="2pt solid #20c997">
                        <fo:block font-size="16pt" font-weight="bold" color="#0c5443" space-after="5mm">
                            ASSET PORTFOLIO OVERVIEW
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
                                                            border="1pt solid #20c997" text-align="center">
                                            <fo:block font-size="8pt" color="#666666" font-weight="bold">TOTAL ASSETS
                                            </fo:block>
                                            <fo:block font-size="20pt" font-weight="bold" color="#20c997">
                                                <xsl:value-of select="count(//Asset)"/>
                                            </fo:block>
                                        </fo:block-container>
                                    </fo:table-cell>
                                    <fo:table-cell padding="3mm">
                                        <fo:block-container background-color="#ffffff" padding="4mm"
                                                            border="1pt solid #20c997" text-align="center">
                                            <fo:block font-size="8pt" color="#666666" font-weight="bold">ASSET TYPES
                                            </fo:block>
                                            <fo:block font-size="20pt" font-weight="bold" color="#20c997">
                                                <xsl:value-of
                                                        select="count(//Asset[not(AssetType = preceding::Asset/AssetType)])"/>
                                            </fo:block>
                                        </fo:block-container>
                                    </fo:table-cell>
                                    <fo:table-cell padding="3mm">
                                        <fo:block-container background-color="#ffffff" padding="4mm"
                                                            border="1pt solid #20c997" text-align="center">
                                            <fo:block font-size="8pt" color="#666666" font-weight="bold">WITH ISIN
                                            </fo:block>
                                            <fo:block font-size="20pt" font-weight="bold" color="#20c997">
                                                <xsl:value-of select="count(//Asset/Identifiers/ISIN)"/>
                                            </fo:block>
                                        </fo:block-container>
                                    </fo:table-cell>
                                    <fo:table-cell padding="3mm">
                                        <fo:block-container background-color="#ffffff" padding="4mm"
                                                            border="1pt solid #20c997" text-align="center">
                                            <fo:block font-size="8pt" color="#666666" font-weight="bold">CURRENCIES
                                            </fo:block>
                                            <fo:block font-size="20pt" font-weight="bold" color="#20c997">
                                                <xsl:value-of
                                                        select="count(//Position[not(Currency = preceding::Position/Currency)])"/>
                                            </fo:block>
                                        </fo:block-container>
                                    </fo:table-cell>
                                </fo:table-row>
                            </fo:table-body>
                        </fo:table>
                    </fo:block-container>

                    <!-- Asset Type Distribution -->
                    <fo:block font-size="18pt" font-weight="bold" color="#495057" space-after="5mm"
                              border-bottom="2pt solid #20c997" padding-bottom="3mm">
                        ASSET TYPE DISTRIBUTION
                    </fo:block>

                    <xsl:if test="count(//Asset) &gt; 0">
                        <fo:table table-layout="fixed" width="100%" border-collapse="collapse"
                                  border="1pt solid #dee2e6" space-after="8mm">
                            <fo:table-column column-width="5%"/>
                            <fo:table-column column-width="35%"/>
                            <fo:table-column column-width="20%"/>
                            <fo:table-column column-width="20%"/>
                            <fo:table-column column-width="20%"/>

                            <fo:table-header background-color="#f8f9fa">
                                <fo:table-row>
                                    <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                        <fo:block font-weight="bold" text-align="center">#</fo:block>
                                    </fo:table-cell>
                                    <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                        <fo:block font-weight="bold">Asset Type</fo:block>
                                    </fo:table-cell>
                                    <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                        <fo:block font-weight="bold" text-align="right">Count</fo:block>
                                    </fo:table-cell>
                                    <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                        <fo:block font-weight="bold" text-align="right">Percentage</fo:block>
                                    </fo:table-cell>
                                    <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                        <fo:block font-weight="bold" text-align="center">Data Quality</fo:block>
                                    </fo:table-cell>
                                </fo:table-row>
                            </fo:table-header>

                            <fo:table-body>
                                <xsl:variable name="totalAssets" select="count(//Asset)"/>
                                <xsl:for-each
                                        select="//Asset[not(AssetType = preceding::Asset/AssetType)]/AssetType">
                                    <xsl:sort select="count(key('asset-by-type', .))" data-type="number"
                                              order="descending"/>
                                    <xsl:variable name="assetType" select="."/>
                                    <xsl:variable name="assetCount" select="count(key('asset-by-type', $assetType))"/>
                                    <xsl:variable name="assetsWithISIN"
                                                  select="count(key('asset-by-type', $assetType)/Identifiers/ISIN)"/>

                                    <fo:table-row>
                                        <xsl:if test="position() mod 2 = 0">
                                            <xsl:attribute name="background-color">#f8f9fa</xsl:attribute>
                                        </xsl:if>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center">
                                                <xsl:value-of select="position()"/>
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block font-weight="bold" color="#20c997">
                                                <xsl:value-of select="$assetType"/>
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="right">
                                                <xsl:value-of select="$assetCount"/>
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="right">
                                                <xsl:value-of
                                                        select="format-number(($assetCount div $totalAssets) * 100, '0.00')"/>%
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center">
                                                <xsl:variable name="qualityPercentage"
                                                              select="($assetsWithISIN div $assetCount) * 100"/>
                                                <xsl:choose>
                                                    <xsl:when test="$qualityPercentage &gt; 80">
                                                        <fo:inline color="#28a745" font-weight="bold">HIGH</fo:inline>
                                                    </xsl:when>
                                                    <xsl:when test="$qualityPercentage &gt; 50">
                                                        <fo:inline color="#ffc107" font-weight="bold">MEDIUM</fo:inline>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <fo:inline color="#dc3545" font-weight="bold">LOW</fo:inline>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>
                                </xsl:for-each>
                            </fo:table-body>
                        </fo:table>
                    </xsl:if>

                    <!-- Asset Identifier Completeness -->
                    <fo:block font-size="18pt" font-weight="bold" color="#495057" space-after="5mm"
                              border-bottom="2pt solid #20c997" padding-bottom="3mm">
                        ASSET IDENTIFIER COMPLETENESS
                    </fo:block>

                    <fo:table table-layout="fixed" width="100%" border-collapse="collapse"
                              border="1pt solid #dee2e6" space-after="8mm">
                        <fo:table-column column-width="40%"/>
                        <fo:table-column column-width="20%"/>
                        <fo:table-column column-width="20%"/>
                        <fo:table-column column-width="20%"/>

                        <fo:table-header background-color="#f8f9fa">
                            <fo:table-row>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                    <fo:block font-weight="bold">Identifier Type</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                    <fo:block font-weight="bold" text-align="right">Present</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                    <fo:block font-weight="bold" text-align="right">Missing</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                    <fo:block font-weight="bold" text-align="right">Completeness</fo:block>
                                </fo:table-cell>
                            </fo:table-row>
                        </fo:table-header>

                        <fo:table-body>
                            <!-- ISIN -->
                            <fo:table-row>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">ISIN (International Securities ID)</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="right" color="#28a745" font-weight="bold">
                                        <xsl:value-of select="count(//Asset/Identifiers/ISIN)"/>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="right" color="#dc3545" font-weight="bold">
                                        <xsl:value-of select="count(//Asset[not(Identifiers/ISIN)])"/>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="right">
                                        <xsl:variable name="completeness"
                                                      select="(count(//Asset/Identifiers/ISIN) div count(//Asset)) * 100"/>
                                        <xsl:value-of select="format-number($completeness, '0.0')"/>%
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- SEDOL -->
                            <fo:table-row background-color="#f8f9fa">
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">SEDOL (Stock Exchange Daily Official List)</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="right" color="#28a745" font-weight="bold">
                                        <xsl:value-of select="count(//Asset/Identifiers/SEDOL)"/>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="right" color="#dc3545" font-weight="bold">
                                        <xsl:value-of select="count(//Asset[not(Identifiers/SEDOL)])"/>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="right">
                                        <xsl:variable name="completeness"
                                                      select="(count(//Asset/Identifiers/SEDOL) div count(//Asset)) * 100"/>
                                        <xsl:value-of select="format-number($completeness, '0.0')"/>%
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- WKN -->
                            <fo:table-row>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">WKN (Wertpapierkennnummer)</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="right" color="#28a745" font-weight="bold">
                                        <xsl:value-of select="count(//Asset/Identifiers/WKN)"/>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="right" color="#dc3545" font-weight="bold">
                                        <xsl:value-of select="count(//Asset[not(Identifiers/WKN)])"/>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="right">
                                        <xsl:variable name="completeness"
                                                      select="(count(//Asset/Identifiers/WKN) div count(//Asset)) * 100"/>
                                        <xsl:value-of select="format-number($completeness, '0.0')"/>%
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- Asset Name -->
                            <fo:table-row background-color="#f8f9fa">
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">Asset Name</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="right" color="#28a745" font-weight="bold">
                                        <xsl:value-of select="count(//Asset/Name)"/>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="right" color="#dc3545" font-weight="bold">
                                        <xsl:value-of select="count(//Asset[not(Name)])"/>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="right">
                                        <xsl:variable name="completeness"
                                                      select="(count(//Asset/Name) div count(//Asset)) * 100"/>
                                        <xsl:value-of select="format-number($completeness, '0.0')"/>%
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>
                        </fo:table-body>
                    </fo:table>

                    <!-- Asset Data Quality Issues -->
                    <fo:block font-size="18pt" font-weight="bold" color="#495057" space-after="5mm"
                              border-bottom="2pt solid #20c997" padding-bottom="3mm">
                        ASSET DATA QUALITY ISSUES
                    </fo:block>

                    <fo:table table-layout="fixed" width="100%" border-collapse="collapse"
                              border="1pt solid #dee2e6" space-after="8mm">
                        <fo:table-column column-width="5%"/>
                        <fo:table-column column-width="45%"/>
                        <fo:table-column column-width="20%"/>
                        <fo:table-column column-width="20%"/>
                        <fo:table-column column-width="10%"/>

                        <fo:table-header background-color="#f8f9fa">
                            <fo:table-row>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                    <fo:block font-weight="bold" text-align="center">#</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                    <fo:block font-weight="bold">Quality Check</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                    <fo:block font-weight="bold" text-align="center">Issues Found</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                    <fo:block font-weight="bold" text-align="center">Impact</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                    <fo:block font-weight="bold" text-align="center">Status</fo:block>
                                </fo:table-cell>
                            </fo:table-row>
                        </fo:table-header>

                        <fo:table-body>
                            <!-- Issue 1: Missing ISIN -->
                            <fo:table-row>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">1</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block>Assets without ISIN</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-weight="bold">
                                        <xsl:value-of select="count(//Asset[not(Identifiers/ISIN)])"/>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="count(//Asset[not(Identifiers/ISIN)]) = 0">
                                                <fo:inline color="#28a745">None</fo:inline>
                                            </xsl:when>
                                            <xsl:when test="count(//Asset[not(Identifiers/ISIN)]) &lt; 5">
                                                <fo:inline color="#ffc107">Low</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545">High</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="count(//Asset[not(Identifiers/ISIN)]) = 0">
                                                <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                            </xsl:when>
                                            <xsl:when test="count(//Asset[not(Identifiers/ISIN)]) &lt; 5">
                                                <fo:inline color="#ffc107" font-weight="bold">!</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- Issue 2: Missing Asset Name -->
                            <fo:table-row background-color="#f8f9fa">
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">2</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block>Assets without Name</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-weight="bold">
                                        <xsl:value-of select="count(//Asset[not(Name)])"/>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="count(//Asset[not(Name)]) = 0">
                                                <fo:inline color="#28a745">None</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545">Critical</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="count(//Asset[not(Name)]) = 0">
                                                <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- Issue 3: Missing Asset Type -->
                            <fo:table-row>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">3</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block>Assets without Asset Type</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-weight="bold">
                                        <xsl:value-of select="count(//Asset[not(AssetType)])"/>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="count(//Asset[not(AssetType)]) = 0">
                                                <fo:inline color="#28a745">None</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545">Critical</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="count(//Asset[not(AssetType)]) = 0">
                                                <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- Issue 4: Duplicate Asset IDs -->
                            <fo:table-row background-color="#f8f9fa">
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">4</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block>Potential Duplicate Asset UniqueIDs</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-weight="bold">
                                        <xsl:value-of
                                                select="count(//Asset[UniqueID = following::Asset/UniqueID])"/>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when
                                                    test="count(//Asset[UniqueID = following::Asset/UniqueID]) = 0">
                                                <fo:inline color="#28a745">None</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545">Critical</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when
                                                    test="count(//Asset[UniqueID = following::Asset/UniqueID]) = 0">
                                                <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- Issue 5: Invalid ISIN Format -->
                            <fo:table-row>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">5</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block>ISINs with incorrect length (not 12 chars)</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-weight="bold">
                                        <xsl:value-of
                                                select="count(//Asset/Identifiers/ISIN[string-length(.) != 12])"/>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when
                                                    test="count(//Asset/Identifiers/ISIN[string-length(.) != 12]) = 0">
                                                <fo:inline color="#28a745">None</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545">High</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when
                                                    test="count(//Asset/Identifiers/ISIN[string-length(.) != 12]) = 0">
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

                    <!-- Position-Asset Linkage Quality -->
                    <fo:block font-size="18pt" font-weight="bold" color="#495057" space-after="5mm"
                              border-bottom="2pt solid #20c997" padding-bottom="3mm">
                        POSITION-ASSET LINKAGE QUALITY
                    </fo:block>

                    <fo:block-container background-color="#fff3cd" border="1pt solid #ffc107" padding="6mm"
                                        space-after="8mm">
                        <fo:block font-weight="bold" color="#856404" space-after="3mm">
                            Linkage Statistics:
                        </fo:block>

                        <fo:table table-layout="fixed" width="100%">
                            <fo:table-column column-width="50%"/>
                            <fo:table-column column-width="50%"/>
                            <fo:table-body>
                                <fo:table-row>
                                    <fo:table-cell padding="2mm">
                                        <fo:block>
                                            <fo:inline font-weight="bold">Total Positions:</fo:inline>
                                            <xsl:value-of select="count(//Position)"/>
                                        </fo:block>
                                    </fo:table-cell>
                                    <fo:table-cell padding="2mm">
                                        <fo:block>
                                            <fo:inline font-weight="bold">Positions with UniqueID:</fo:inline>
                                            <xsl:value-of select="count(//Position/UniqueID)"/>
                                        </fo:block>
                                    </fo:table-cell>
                                </fo:table-row>
                                <fo:table-row>
                                    <fo:table-cell padding="2mm">
                                        <fo:block>
                                            <fo:inline font-weight="bold">Orphaned Positions (no matching asset):
                                            </fo:inline>
                                            <fo:inline>
                                                <xsl:attribute name="color">
                                                    <xsl:choose>
                                                        <xsl:when
                                                                test="count(//Position[UniqueID and not(key('asset-by-id', UniqueID))]) = 0">
                                                            #28a745
                                                        </xsl:when>
                                                        <xsl:otherwise>#dc3545</xsl:otherwise>
                                                    </xsl:choose>
                                                </xsl:attribute>
                                                <xsl:attribute name="font-weight">bold</xsl:attribute>
                                                <xsl:value-of
                                                        select="count(//Position[UniqueID and not(key('asset-by-id', UniqueID))])"/>
                                            </fo:inline>
                                        </fo:block>
                                    </fo:table-cell>
                                    <fo:table-cell padding="2mm">
                                        <fo:block>
                                            <fo:inline font-weight="bold">Linkage Quality:</fo:inline>
                                            <fo:inline>
                                                <xsl:variable name="linkageQuality"
                                                              select="(count(//Position[UniqueID and key('asset-by-id', UniqueID)]) div count(//Position)) * 100"/>
                                                <xsl:attribute name="color">
                                                    <xsl:choose>
                                                        <xsl:when test="$linkageQuality &gt; 95">#28a745</xsl:when>
                                                        <xsl:when test="$linkageQuality &gt; 80">#ffc107</xsl:when>
                                                        <xsl:otherwise>#dc3545</xsl:otherwise>
                                                    </xsl:choose>
                                                </xsl:attribute>
                                                <xsl:attribute name="font-weight">bold</xsl:attribute>
                                                <xsl:value-of select="format-number($linkageQuality, '0.0')"/>%
                                            </fo:inline>
                                        </fo:block>
                                    </fo:table-cell>
                                </fo:table-row>
                            </fo:table-body>
                        </fo:table>
                    </fo:block-container>

                    <!-- Summary -->
                    <fo:block-container background-color="#20c997" color="#ffffff" padding="8mm"
                                        space-before="10mm" id="asset-end">
                        <fo:block font-size="18pt" font-weight="bold" space-after="5mm">
                            ASSET ANALYSIS SUMMARY
                        </fo:block>
                        <fo:block space-after="3mm">
                            This comprehensive asset analysis report covers
                            <xsl:value-of select="count(//Asset)"/> assets
                            across
                            <xsl:value-of
                                    select="count(//Asset[not(AssetType = preceding::Asset/AssetType)])"/> different
                            asset types.
                        </fo:block>
                        <fo:block space-after="3mm">
                            Analysis includes asset type distribution, identifier completeness,
                            data quality validation, and position-asset linkage integrity.
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
