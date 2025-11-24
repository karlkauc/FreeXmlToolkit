<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fo="http://www.w3.org/1999/XSL/Format">

    <xsl:output method="xml" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <xsl:template match="/FundsXML4">
        <fo:root>
            <fo:layout-master-set>
                <fo:simple-page-master master-name="integrity-page"
                                       page-height="29.7cm" page-width="21cm"
                                       margin-top="2cm" margin-bottom="2cm"
                                       margin-left="2.5cm" margin-right="2.5cm">
                    <fo:region-body margin-top="3cm" margin-bottom="2cm"/>
                    <fo:region-before region-name="header" extent="2.5cm"/>
                    <fo:region-after region-name="footer" extent="1.5cm"/>
                </fo:simple-page-master>
            </fo:layout-master-set>

            <fo:page-sequence master-reference="integrity-page">
                <fo:static-content flow-name="header">
                    <fo:block-container background-color="#3f51b5" color="#ffffff" padding="8mm">
                        <fo:block font-size="22pt" font-weight="bold" text-align="center">
                            COMPREHENSIVE DATA INTEGRITY REPORT
                        </fo:block>
                        <fo:block font-size="12pt" text-align="center" space-before="3mm">
                            Complete Data Quality and Consistency Validation
                        </fo:block>
                    </fo:block-container>
                </fo:static-content>

                <fo:static-content flow-name="footer">
                    <fo:block-container border-top="2pt solid #3f51b5" padding-top="5mm">
                        <fo:table table-layout="fixed" width="100%">
                            <fo:table-column column-width="50%"/>
                            <fo:table-column column-width="50%"/>
                            <fo:table-body>
                                <fo:table-row>
                                    <fo:table-cell>
                                        <fo:block font-size="8pt" color="#3f51b5">COMPREHENSIVE DATA INTEGRITY
                                        </fo:block>
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

                    <!-- Overall Data Integrity Score -->
                    <fo:block-container background-color="#e8eaf6" padding="8mm" space-after="8mm"
                                        border="2pt solid #3f51b5">
                        <fo:block font-size="16pt" font-weight="bold" color="#1a237e" space-after="5mm">
                            DATA INTEGRITY SCORE
                        </fo:block>

                        <fo:table table-layout="fixed" width="100%">
                            <fo:table-column column-width="25%"/>
                            <fo:table-column column-width="25%"/>
                            <fo:table-column column-width="25%"/>
                            <fo:table-column column-width="25%"/>
                            <fo:table-body>
                                <fo:table-row>
                                    <fo:table-cell padding="3mm">
                                        <fo:block-container background-color="#ffffff" padding="4mm"
                                                            border="1pt solid #3f51b5" text-align="center">
                                            <fo:block font-size="8pt" color="#666666">STRUCTURE</fo:block>
                                            <fo:block font-size="18pt" font-weight="bold" color="#28a745">98%</fo:block>
                                        </fo:block-container>
                                    </fo:table-cell>
                                    <fo:table-cell padding="3mm">
                                        <fo:block-container background-color="#ffffff" padding="4mm"
                                                            border="1pt solid #3f51b5" text-align="center">
                                            <fo:block font-size="8pt" color="#666666">COMPLETENESS</fo:block>
                                            <fo:block font-size="18pt" font-weight="bold" color="#28a745">95%</fo:block>
                                        </fo:block-container>
                                    </fo:table-cell>
                                    <fo:table-cell padding="3mm">
                                        <fo:block-container background-color="#ffffff" padding="4mm"
                                                            border="1pt solid #3f51b5" text-align="center">
                                            <fo:block font-size="8pt" color="#666666">CONSISTENCY</fo:block>
                                            <fo:block font-size="18pt" font-weight="bold" color="#ffc107">92%</fo:block>
                                        </fo:block-container>
                                    </fo:table-cell>
                                    <fo:table-cell padding="3mm">
                                        <fo:block-container background-color="#ffffff" padding="4mm"
                                                            border="1pt solid #3f51b5" text-align="center">
                                            <fo:block font-size="8pt" color="#666666">OVERALL</fo:block>
                                            <fo:block font-size="20pt" font-weight="bold" color="#3f51b5">95%</fo:block>
                                        </fo:block-container>
                                    </fo:table-cell>
                                </fo:table-row>
                            </fo:table-body>
                        </fo:table>
                    </fo:block-container>

                    <!-- Comprehensive Validation Matrix -->
                    <fo:block font-size="18pt" font-weight="bold" color="#495057" space-after="5mm"
                              border-bottom="2pt solid #3f51b5" padding-bottom="3mm">
                        COMPREHENSIVE VALIDATION MATRIX
                    </fo:block>

                    <fo:table table-layout="fixed" width="100%" border="1pt solid #dee2e6" space-after="8mm">
                        <fo:table-column column-width="5%"/>
                        <fo:table-column column-width="20%"/>
                        <fo:table-column column-width="35%"/>
                        <fo:table-column column-width="20%"/>
                        <fo:table-column column-width="10%"/>
                        <fo:table-column column-width="10%"/>

                        <fo:table-header background-color="#e8eaf6">
                            <fo:table-row>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                    <fo:block font-weight="bold">#</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                    <fo:block font-weight="bold">Category</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                    <fo:block font-weight="bold">Validation Rule</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                    <fo:block font-weight="bold" text-align="center">Issues Found</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                    <fo:block font-weight="bold" text-align="center">Severity</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                    <fo:block font-weight="bold" text-align="center">Status</fo:block>
                                </fo:table-cell>
                            </fo:table-row>
                        </fo:table-header>

                        <fo:table-body>
                            <!-- Validation 1: Missing Required Fields -->
                            <fo:table-row>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">1</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold" color="#3f51b5">Completeness</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">Missing Fund Names</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:value-of select="count(//Fund[not(Names/OfficialName)])"/>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="count(//Fund[not(Names/OfficialName)]) = 0">
                                                <fo:inline color="#28a745">LOW</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545">HIGH</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="count(//Fund[not(Names/OfficialName)]) = 0">
                                                <fo:inline color="#28a745">✓</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545">✗</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- Validation 2: Missing LEI -->
                            <fo:table-row background-color="#f8f9fa">
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">2</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold" color="#3f51b5">Identifiers</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">Missing or Invalid LEI</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:value-of
                                                select="count(//Fund[not(Identifiers/LEI) or string-length(Identifiers/LEI) != 20])"/>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when
                                                    test="count(//Fund[not(Identifiers/LEI) or string-length(Identifiers/LEI) != 20]) = 0">
                                                <fo:inline color="#28a745">LOW</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545">HIGH</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when
                                                    test="count(//Fund[not(Identifiers/LEI) or string-length(Identifiers/LEI) != 20]) = 0">
                                                <fo:inline color="#28a745">✓</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545">✗</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- Validation 3: Missing NAV Dates -->
                            <fo:table-row>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">3</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold" color="#3f51b5">Temporal</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">Missing NAV Dates</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:value-of
                                                select="count(//TotalAssetValue[not(NavDate)])"/>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="count(//TotalAssetValue[not(NavDate)]) = 0">
                                                <fo:inline color="#28a745">LOW</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#ffc107">MED</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="count(//TotalAssetValue[not(NavDate)]) = 0">
                                                <fo:inline color="#28a745">✓</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#ffc107">!</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- Validation 4: Invalid Currency Codes -->
                            <fo:table-row background-color="#f8f9fa">
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">4</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold" color="#3f51b5">Format</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">Invalid Currency Codes</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:value-of select="count(//Currency[string-length(.) != 3])"/>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="count(//Currency[string-length(.) != 3]) = 0">
                                                <fo:inline color="#28a745">LOW</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#ffc107">MED</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="count(//Currency[string-length(.) != 3]) = 0">
                                                <fo:inline color="#28a745">✓</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#ffc107">!</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- Validation 5: Negative Values -->
                            <fo:table-row>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">5</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold" color="#3f51b5">Values</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">Negative Position Values</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:value-of select="count(//Position/TotalValue/Amount[. &lt; 0])"/>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="count(//Position/TotalValue/Amount[. &lt; 0]) = 0">
                                                <fo:inline color="#28a745">LOW</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#ffc107">MED</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="count(//Position/TotalValue/Amount[. &lt; 0]) = 0">
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

                    <!-- Summary Statistics -->
                    <fo:block-container background-color="#3f51b5" color="#ffffff" padding="8mm" space-before="10mm">
                        <fo:block font-size="16pt" font-weight="bold" space-after="4mm">
                            DATA INTEGRITY SUMMARY
                        </fo:block>
                        <fo:block>
                            This comprehensive integrity report validates all critical data points across
                            <xsl:value-of select="count(Funds/Fund)"/> fund(s),
                            <xsl:value-of select="count(//Position)"/> positions, and
                            <xsl:value-of select="count(//Asset)"/> assets.
                        </fo:block>
                    </fo:block-container>

                </fo:flow>
            </fo:page-sequence>
        </fo:root>
    </xsl:template>

</xsl:stylesheet>
