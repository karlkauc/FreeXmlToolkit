<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fo="http://www.w3.org/1999/XSL/Format">

    <xsl:output method="xml" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <!-- Main template for Share Class Analysis Report -->
    <xsl:template match="/FundsXML4">
        <fo:root>
            <!-- Page Layout Definition -->
            <fo:layout-master-set>
                <fo:simple-page-master master-name="shareclass-page"
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
            <fo:page-sequence master-reference="shareclass-page">
                <!-- Header -->
                <fo:static-content flow-name="header">
                    <fo:block-container background-color="#17a2b8" color="#ffffff" padding="8mm">
                        <fo:block font-size="22pt" font-weight="bold" text-align="center">
                            SHARE CLASS ANALYSIS REPORT
                        </fo:block>
                        <fo:block font-size="12pt" text-align="center" space-before="3mm">
                            Comprehensive Share Class Structure and Pricing Analysis
                        </fo:block>
                        <fo:block text-align="center" font-size="10pt" space-before="2mm">
                            Total Share Classes:
                            <xsl:value-of select="count(//ShareClass)"/> •
                            Analysis Date:
                            <xsl:value-of select="ControlData/ContentDate"/>
                        </fo:block>
                    </fo:block-container>
                </fo:static-content>

                <!-- Footer -->
                <fo:static-content flow-name="footer">
                    <fo:block-container border-top="2pt solid #17a2b8" padding-top="5mm">
                        <fo:table table-layout="fixed" width="100%">
                            <fo:table-column column-width="40%"/>
                            <fo:table-column column-width="20%"/>
                            <fo:table-column column-width="40%"/>
                            <fo:table-body>
                                <fo:table-row>
                                    <fo:table-cell>
                                        <fo:block font-size="8pt" color="#17a2b8" font-weight="bold">
                                            SHARE CLASS ANALYSIS
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
                                            <fo:page-number-citation ref-id="shareclass-end"/>
                                        </fo:block>
                                    </fo:table-cell>
                                    <fo:table-cell>
                                        <fo:block font-size="8pt" color="#666666" text-align="right">
                                            Doc:
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
                    <fo:block-container background-color="#d1ecf1" padding="8mm" space-after="8mm"
                                        border="2pt solid #17a2b8">
                        <fo:block font-size="16pt" font-weight="bold" color="#0c5460" space-after="5mm">
                            SHARE CLASS PORTFOLIO OVERVIEW
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
                                                            border="1pt solid #17a2b8" text-align="center">
                                            <fo:block font-size="8pt" color="#666666" font-weight="bold">TOTAL SHARE
                                                CLASSES
                                            </fo:block>
                                            <fo:block font-size="20pt" font-weight="bold" color="#17a2b8">
                                                <xsl:value-of select="count(//ShareClass)"/>
                                            </fo:block>
                                        </fo:block-container>
                                    </fo:table-cell>
                                    <fo:table-cell padding="3mm">
                                        <fo:block-container background-color="#ffffff" padding="4mm"
                                                            border="1pt solid #17a2b8" text-align="center">
                                            <fo:block font-size="8pt" color="#666666" font-weight="bold">CURRENCIES
                                            </fo:block>
                                            <fo:block font-size="20pt" font-weight="bold" color="#17a2b8">
                                                <xsl:value-of
                                                        select="count(//ShareClass/Currency[not(. = preceding::ShareClass/Currency)])"/>
                                            </fo:block>
                                        </fo:block-container>
                                    </fo:table-cell>
                                    <fo:table-cell padding="3mm">
                                        <fo:block-container background-color="#ffffff" padding="4mm"
                                                            border="1pt solid #17a2b8" text-align="center">
                                            <fo:block font-size="8pt" color="#666666" font-weight="bold">WITH ISIN
                                            </fo:block>
                                            <fo:block font-size="20pt" font-weight="bold" color="#17a2b8">
                                                <xsl:value-of select="count(//ShareClass/Identifiers/ISIN)"/>
                                            </fo:block>
                                        </fo:block-container>
                                    </fo:table-cell>
                                    <fo:table-cell padding="3mm">
                                        <fo:block-container background-color="#ffffff" padding="4mm"
                                                            border="1pt solid #17a2b8" text-align="center">
                                            <fo:block font-size="8pt" color="#666666" font-weight="bold">TOTAL AUM
                                            </fo:block>
                                            <fo:block font-size="14pt" font-weight="bold" color="#17a2b8">
                                                <xsl:value-of
                                                        select="format-number(sum(//ShareClass/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount), '#,##0')"/>
                                            </fo:block>
                                        </fo:block-container>
                                    </fo:table-cell>
                                </fo:table-row>
                            </fo:table-body>
                        </fo:table>
                    </fo:block-container>

                    <!-- Share Class Data Quality Validation -->
                    <fo:block font-size="18pt" font-weight="bold" color="#495057" space-after="5mm"
                              border-bottom="2pt solid #17a2b8" padding-bottom="3mm">
                        SHARE CLASS DATA QUALITY VALIDATION
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
                                    <fo:block font-weight="bold">Data Quality Check</fo:block>
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
                            <!-- DQ Check 1: All share classes have names -->
                            <fo:table-row>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">1</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">Share Class Names</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">All share classes have official names</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-size="9pt">
                                        <xsl:choose>
                                            <xsl:when test="count(//ShareClass[not(Names/OfficialName)]) = 0">PASS
                                            </xsl:when>
                                            <xsl:otherwise>FAIL</xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="count(//ShareClass[not(Names/OfficialName)]) = 0">
                                                <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- DQ Check 2: ISIN Format -->
                            <fo:table-row background-color="#f8f9fa">
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">2</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">ISIN Format Validation</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">All ISINs have 12 characters</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-size="9pt">
                                        <xsl:choose>
                                            <xsl:when
                                                    test="count(//ShareClass/Identifiers/ISIN[string-length(.) != 12]) = 0">
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
                                                    test="count(//ShareClass/Identifiers/ISIN[string-length(.) != 12]) = 0">
                                                <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- DQ Check 3: NAV Prices Present -->
                            <fo:table-row>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">3</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">NAV Price Availability</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">All share classes have NAV prices</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-size="9pt">
                                        <xsl:choose>
                                            <xsl:when test="count(//ShareClass[not(Prices/Price/NavPrice)]) = 0">PASS
                                            </xsl:when>
                                            <xsl:otherwise>FAIL</xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="count(//ShareClass[not(Prices/Price/NavPrice)]) = 0">
                                                <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- DQ Check 4: Positive NAV Prices -->
                            <fo:table-row background-color="#f8f9fa">
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">4</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">Positive NAV Prices</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">All NAV prices are positive</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-size="9pt">
                                        <xsl:choose>
                                            <xsl:when test="count(//ShareClass/Prices/Price/NavPrice[. &lt;= 0]) = 0">
                                                PASS
                                            </xsl:when>
                                            <xsl:otherwise>FAIL</xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="count(//ShareClass/Prices/Price/NavPrice[. &lt;= 0]) = 0">
                                                <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- DQ Check 5: Shares Outstanding -->
                            <fo:table-row>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">5</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">Shares Outstanding Data</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">Shares outstanding information present</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-size="9pt">
                                        <xsl:choose>
                                            <xsl:when
                                                    test="count(//ShareClass[not(TotalAssetValues/TotalAssetValue/SharesOutstanding)]) = 0">
                                                PASS
                                            </xsl:when>
                                            <xsl:otherwise>WARN</xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when
                                                    test="count(//ShareClass[not(TotalAssetValues/TotalAssetValue/SharesOutstanding)]) = 0">
                                                <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#ffc107" font-weight="bold">!</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- DQ Check 6: Currency Consistency -->
                            <fo:table-row background-color="#f8f9fa">
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">6</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">Currency Code Format</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">All currencies are 3-letter codes</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-size="9pt">
                                        <xsl:choose>
                                            <xsl:when test="count(//ShareClass/Currency[string-length(.) != 3]) = 0">
                                                PASS
                                            </xsl:when>
                                            <xsl:otherwise>FAIL</xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="count(//ShareClass/Currency[string-length(.) != 3]) = 0">
                                                <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- DQ Check 7: TNA Calculation -->
                            <fo:table-row>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">7</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">TNA Calculation Check</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">NAV × Shares ≈ TNA</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-size="9pt">
                                        <xsl:variable name="mismatchCount">
                                            <xsl:value-of select="count(//ShareClass[
                                                TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount and
                                                Prices/Price/NavPrice and
                                                TotalAssetValues/TotalAssetValue/SharesOutstanding and
                                                (Prices/Price/NavPrice * TotalAssetValues/TotalAssetValue/SharesOutstanding
                                                 &lt; TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount * 0.95 or
                                                 Prices/Price/NavPrice * TotalAssetValues/TotalAssetValue/SharesOutstanding
                                                 &gt; TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount * 1.05)
                                            ])"/>
                                        </xsl:variable>
                                        <xsl:choose>
                                            <xsl:when test="$mismatchCount = 0">PASS</xsl:when>
                                            <xsl:otherwise>WARN</xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:variable name="mismatchCount">
                                            <xsl:value-of select="count(//ShareClass[
                                                TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount and
                                                Prices/Price/NavPrice and
                                                TotalAssetValues/TotalAssetValue/SharesOutstanding and
                                                (Prices/Price/NavPrice * TotalAssetValues/TotalAssetValue/SharesOutstanding
                                                 &lt; TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount * 0.95 or
                                                 Prices/Price/NavPrice * TotalAssetValues/TotalAssetValue/SharesOutstanding
                                                 &gt; TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount * 1.05)
                                            ])"/>
                                        </xsl:variable>
                                        <xsl:choose>
                                            <xsl:when test="$mismatchCount = 0">
                                                <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#ffc107" font-weight="bold">!</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- DQ Check 8: Duplicate ISINs -->
                            <fo:table-row background-color="#f8f9fa">
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">8</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">Duplicate ISIN Check</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">No duplicate ISINs exist</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-size="9pt">
                                        <xsl:choose>
                                            <xsl:when
                                                    test="count(//ShareClass/Identifiers/ISIN[. = following::ShareClass/Identifiers/ISIN]) = 0">
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
                                                    test="count(//ShareClass/Identifiers/ISIN[. = following::ShareClass/Identifiers/ISIN]) = 0">
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

                    <!-- Detailed Share Class Analysis per Fund -->
                    <fo:block font-size="18pt" font-weight="bold" color="#495057" space-after="5mm"
                              border-bottom="2pt solid #17a2b8" padding-bottom="3mm">
                        DETAILED SHARE CLASS ANALYSIS
                    </fo:block>

                    <xsl:for-each select="Funds/Fund">
                        <xsl:if test="SingleFund/ShareClasses/ShareClass">
                            <fo:block-container space-after="10mm" keep-together.within-page="always">
                                <!-- Fund Header -->
                                <fo:block-container background-color="#17a2b8" color="#ffffff" padding="5mm"
                                                    space-after="5mm">
                                    <fo:block font-size="14pt" font-weight="bold">
                                        FUND:
                                        <xsl:value-of select="Names/OfficialName"/>
                                    </fo:block>
                                    <fo:block font-size="10pt" space-before="2mm">
                                        LEI:
                                        <xsl:value-of select="Identifiers/LEI"/> •
                                        Share Classes:
                                        <xsl:value-of select="count(SingleFund/ShareClasses/ShareClass)"/>
                                    </fo:block>
                                </fo:block-container>

                                <!-- Share Classes Table -->
                                <fo:table table-layout="fixed" width="100%" border-collapse="collapse"
                                          border="1pt solid #dee2e6" space-after="6mm">
                                    <fo:table-column column-width="20%"/>
                                    <fo:table-column column-width="12%"/>
                                    <fo:table-column column-width="10%"/>
                                    <fo:table-column column-width="15%"/>
                                    <fo:table-column column-width="13%"/>
                                    <fo:table-column column-width="15%"/>
                                    <fo:table-column column-width="15%"/>

                                    <fo:table-header background-color="#d1ecf1">
                                        <fo:table-row>
                                            <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                                <fo:block font-weight="bold" color="#0c5460">Share Class Name</fo:block>
                                            </fo:table-cell>
                                            <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                                <fo:block font-weight="bold" color="#0c5460">ISIN</fo:block>
                                            </fo:table-cell>
                                            <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                                <fo:block font-weight="bold" color="#0c5460">Ccy</fo:block>
                                            </fo:table-cell>
                                            <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                                <fo:block font-weight="bold" color="#0c5460" text-align="right">NAV
                                                    Price
                                                </fo:block>
                                            </fo:table-cell>
                                            <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                                <fo:block font-weight="bold" color="#0c5460" text-align="right">Shares
                                                </fo:block>
                                            </fo:table-cell>
                                            <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                                <fo:block font-weight="bold" color="#0c5460" text-align="right">TNA
                                                </fo:block>
                                            </fo:table-cell>
                                            <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                                <fo:block font-weight="bold" color="#0c5460" text-align="center">Quality
                                                </fo:block>
                                            </fo:table-cell>
                                        </fo:table-row>
                                    </fo:table-header>

                                    <fo:table-body>
                                        <xsl:for-each select="SingleFund/ShareClasses/ShareClass">
                                            <fo:table-row>
                                                <xsl:if test="position() mod 2 = 0">
                                                    <xsl:attribute name="background-color">#f8f9fa</xsl:attribute>
                                                </xsl:if>
                                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                                    <fo:block font-size="8pt">
                                                        <xsl:value-of select="Names/OfficialName"/>
                                                    </fo:block>
                                                </fo:table-cell>
                                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                                    <fo:block font-size="8pt">
                                                        <xsl:value-of select="Identifiers/ISIN"/>
                                                    </fo:block>
                                                </fo:table-cell>
                                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                                    <fo:block font-size="8pt">
                                                        <xsl:value-of select="Currency"/>
                                                    </fo:block>
                                                </fo:table-cell>
                                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                                    <fo:block text-align="right" font-size="8pt"
                                                              font-family="Courier, monospace">
                                                        <xsl:value-of
                                                                select="format-number(Prices/Price/NavPrice, '#,##0.00')"/>
                                                    </fo:block>
                                                </fo:table-cell>
                                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                                    <fo:block text-align="right" font-size="8pt"
                                                              font-family="Courier, monospace">
                                                        <xsl:value-of
                                                                select="format-number(TotalAssetValues/TotalAssetValue/SharesOutstanding, '#,##0.00')"/>
                                                    </fo:block>
                                                </fo:table-cell>
                                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                                    <fo:block text-align="right" font-size="8pt"
                                                              font-family="Courier, monospace">
                                                        <xsl:value-of
                                                                select="format-number(TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount, '#,##0.00')"/>
                                                    </fo:block>
                                                </fo:table-cell>
                                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                                    <fo:block text-align="center">
                                                        <xsl:choose>
                                                            <xsl:when test="Identifiers/ISIN and
                                                                          string-length(Identifiers/ISIN) = 12 and
                                                                          Prices/Price/NavPrice &gt; 0 and
                                                                          TotalAssetValues/TotalAssetValue/SharesOutstanding &gt; 0">
                                                                <fo:inline color="#28a745" font-weight="bold">✓
                                                                </fo:inline>
                                                            </xsl:when>
                                                            <xsl:otherwise>
                                                                <fo:inline color="#ffc107" font-weight="bold">!
                                                                </fo:inline>
                                                            </xsl:otherwise>
                                                        </xsl:choose>
                                                    </fo:block>
                                                </fo:table-cell>
                                            </fo:table-row>
                                        </xsl:for-each>
                                    </fo:table-body>
                                </fo:table>

                                <!-- Share Class Statistics -->
                                <fo:block-container background-color="#d1ecf1" border="1pt solid #17a2b8" padding="5mm"
                                                    space-after="6mm">
                                    <fo:block font-weight="bold" color="#0c5460" space-after="3mm">
                                        Share Class Statistics:
                                    </fo:block>

                                    <fo:table table-layout="fixed" width="100%">
                                        <fo:table-column column-width="50%"/>
                                        <fo:table-column column-width="50%"/>
                                        <fo:table-body>
                                            <fo:table-row>
                                                <fo:table-cell padding="2mm">
                                                    <fo:block>
                                                        <fo:inline font-weight="bold">Total Share Classes:</fo:inline>
                                                        <xsl:value-of
                                                                select="count(SingleFund/ShareClasses/ShareClass)"/>
                                                    </fo:block>
                                                </fo:table-cell>
                                                <fo:table-cell padding="2mm">
                                                    <fo:block>
                                                        <fo:inline font-weight="bold">Total TNA (all classes):
                                                        </fo:inline>
                                                        <xsl:value-of
                                                                select="format-number(sum(SingleFund/ShareClasses/ShareClass/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount), '#,##0.00')"/>
                                                    </fo:block>
                                                </fo:table-cell>
                                            </fo:table-row>
                                            <fo:table-row>
                                                <fo:table-cell padding="2mm">
                                                    <fo:block>
                                                        <fo:inline font-weight="bold">Currencies:</fo:inline>
                                                        <xsl:value-of
                                                                select="count(SingleFund/ShareClasses/ShareClass/Currency[not(. = preceding-sibling::ShareClass/Currency)])"/>
                                                    </fo:block>
                                                </fo:table-cell>
                                                <fo:table-cell padding="2mm">
                                                    <fo:block>
                                                        <fo:inline font-weight="bold">Avg NAV Price:</fo:inline>
                                                        <xsl:value-of
                                                                select="format-number(sum(SingleFund/ShareClasses/ShareClass/Prices/Price/NavPrice) div count(SingleFund/ShareClasses/ShareClass), '#,##0.00')"/>
                                                    </fo:block>
                                                </fo:table-cell>
                                            </fo:table-row>
                                        </fo:table-body>
                                    </fo:table>
                                </fo:block-container>
                            </fo:block-container>
                        </xsl:if>
                    </xsl:for-each>

                    <!-- Summary -->
                    <fo:block-container background-color="#17a2b8" color="#ffffff" padding="8mm"
                                        space-before="10mm" id="shareclass-end">
                        <fo:block font-size="18pt" font-weight="bold" space-after="5mm">
                            SHARE CLASS ANALYSIS SUMMARY
                        </fo:block>
                        <fo:block space-after="3mm">
                            This comprehensive share class analysis report covers
                            <xsl:value-of select="count(//ShareClass)"/> share classes
                            across
                            <xsl:value-of select="count(Funds/Fund)"/> fund(s).
                        </fo:block>
                        <fo:block space-after="3mm">
                            Analysis includes identifier validation, pricing accuracy, shares outstanding,
                            TNA calculations, and comprehensive data quality checks.
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
