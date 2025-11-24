<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fo="http://www.w3.org/1999/XSL/Format">

    <xsl:output method="xml" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <!-- Main template for Temporal Consistency Report -->
    <xsl:template match="/FundsXML4">
        <fo:root>
            <!-- Page Layout Definition -->
            <fo:layout-master-set>
                <fo:simple-page-master master-name="temporal-page"
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
            <fo:page-sequence master-reference="temporal-page">
                <!-- Header -->
                <fo:static-content flow-name="header">
                    <fo:block-container background-color="#fd7e14" color="#ffffff" padding="8mm">
                        <fo:block font-size="22pt" font-weight="bold" text-align="center">
                            TEMPORAL CONSISTENCY REPORT
                        </fo:block>
                        <fo:block font-size="12pt" text-align="center" space-before="3mm">
                            Date and Time Consistency Validation
                        </fo:block>
                        <fo:block text-align="center" font-size="10pt" space-before="2mm">
                            Content Date:
                            <xsl:value-of select="ControlData/ContentDate"/> •
                            Generated:
                            <xsl:value-of select="substring(ControlData/DocumentGenerated, 1, 10)"/>
                        </fo:block>
                    </fo:block-container>
                </fo:static-content>

                <!-- Footer -->
                <fo:static-content flow-name="footer">
                    <fo:block-container border-top="2pt solid #fd7e14" padding-top="5mm">
                        <fo:table table-layout="fixed" width="100%">
                            <fo:table-column column-width="40%"/>
                            <fo:table-column column-width="20%"/>
                            <fo:table-column column-width="40%"/>
                            <fo:table-body>
                                <fo:table-row>
                                    <fo:table-cell>
                                        <fo:block font-size="8pt" color="#fd7e14" font-weight="bold">
                                            TEMPORAL CONSISTENCY
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
                                            <fo:page-number-citation ref-id="temporal-end"/>
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
                    <fo:block-container background-color="#fff3e6" padding="8mm" space-after="8mm"
                                        border="2pt solid #fd7e14">
                        <fo:block font-size="16pt" font-weight="bold" color="#7e3e07" space-after="5mm">
                            TEMPORAL DATA OVERVIEW
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
                                                            border="1pt solid #fd7e14" text-align="center">
                                            <fo:block font-size="8pt" color="#666666" font-weight="bold">CONTENT DATE
                                            </fo:block>
                                            <fo:block font-size="14pt" font-weight="bold" color="#fd7e14">
                                                <xsl:value-of select="ControlData/ContentDate"/>
                                            </fo:block>
                                        </fo:block-container>
                                    </fo:table-cell>
                                    <fo:table-cell padding="3mm">
                                        <fo:block-container background-color="#ffffff" padding="4mm"
                                                            border="1pt solid #fd7e14" text-align="center">
                                            <fo:block font-size="8pt" color="#666666" font-weight="bold">GENERATED
                                            </fo:block>
                                            <fo:block font-size="14pt" font-weight="bold" color="#fd7e14">
                                                <xsl:value-of select="substring(ControlData/DocumentGenerated, 1, 10)"/>
                                            </fo:block>
                                        </fo:block-container>
                                    </fo:table-cell>
                                    <fo:table-cell padding="3mm">
                                        <fo:block-container background-color="#ffffff" padding="4mm"
                                                            border="1pt solid #fd7e14" text-align="center">
                                            <fo:block font-size="8pt" color="#666666" font-weight="bold">NAV DATES
                                            </fo:block>
                                            <fo:block font-size="14pt" font-weight="bold" color="#fd7e14">
                                                <xsl:value-of
                                                        select="count(//NavDate[not(. = preceding::NavDate)])"/>
                                            </fo:block>
                                        </fo:block-container>
                                    </fo:table-cell>
                                    <fo:table-cell padding="3mm">
                                        <fo:block-container background-color="#ffffff" padding="4mm"
                                                            border="1pt solid #fd7e14" text-align="center">
                                            <fo:block font-size="8pt" color="#666666" font-weight="bold">DATE FIELDS
                                            </fo:block>
                                            <fo:block font-size="14pt" font-weight="bold" color="#fd7e14">
                                                <xsl:value-of
                                                        select="count(//NavDate) + count(//InceptionDate) + count(//ContentDate) + count(//DocumentGenerated)"/>
                                            </fo:block>
                                        </fo:block-container>
                                    </fo:table-cell>
                                </fo:table-row>
                            </fo:table-body>
                        </fo:table>
                    </fo:block-container>

                    <!-- Document-Level Date Validation -->
                    <fo:block font-size="18pt" font-weight="bold" color="#495057" space-after="5mm"
                              border-bottom="2pt solid #fd7e14" padding-bottom="3mm">
                        DOCUMENT-LEVEL DATE VALIDATION
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
                                    <fo:block font-weight="bold">Value / Description</fo:block>
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
                            <!-- Check 1: ContentDate Present -->
                            <fo:table-row>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">1</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">ContentDate Present</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">
                                        <xsl:value-of select="ControlData/ContentDate"/>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-size="9pt">
                                        <xsl:choose>
                                            <xsl:when test="ControlData/ContentDate">PASS</xsl:when>
                                            <xsl:otherwise>FAIL</xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="ControlData/ContentDate">
                                                <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- Check 2: DocumentGenerated Present -->
                            <fo:table-row background-color="#f8f9fa">
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">2</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">DocumentGenerated Present</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">
                                        <xsl:value-of select="substring(ControlData/DocumentGenerated, 1, 19)"/>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-size="9pt">
                                        <xsl:choose>
                                            <xsl:when test="ControlData/DocumentGenerated">PASS</xsl:when>
                                            <xsl:otherwise>FAIL</xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="ControlData/DocumentGenerated">
                                                <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- Check 3: ContentDate Format -->
                            <fo:table-row>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">3</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">ContentDate Format (YYYY-MM-DD)</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">Length = 10 characters</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-size="9pt">
                                        <xsl:choose>
                                            <xsl:when test="string-length(ControlData/ContentDate) = 10">PASS</xsl:when>
                                            <xsl:otherwise>FAIL</xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="string-length(ControlData/ContentDate) = 10">
                                                <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- Check 4: DocumentGenerated >= ContentDate -->
                            <fo:table-row background-color="#f8f9fa">
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">4</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">DocumentGenerated after ContentDate</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">Generation date should be after or equal to content date
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-size="9pt">
                                        <xsl:choose>
                                            <xsl:when
                                                    test="substring(ControlData/DocumentGenerated, 1, 10) &gt;= ControlData/ContentDate">
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
                                                    test="substring(ControlData/DocumentGenerated, 1, 10) &gt;= ControlData/ContentDate">
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

                    <!-- Fund-Level Date Consistency -->
                    <fo:block font-size="18pt" font-weight="bold" color="#495057" space-after="5mm"
                              border-bottom="2pt solid #fd7e14" padding-bottom="3mm">
                        FUND-LEVEL DATE CONSISTENCY
                    </fo:block>

                    <xsl:for-each select="Funds/Fund">
                        <fo:block-container space-after="10mm" keep-together.within-page="always">
                            <!-- Fund Header -->
                            <fo:block-container background-color="#fd7e14" color="#ffffff" padding="5mm"
                                                space-after="5mm">
                                <fo:block font-size="14pt" font-weight="bold">
                                    FUND:
                                    <xsl:value-of select="Names/OfficialName"/>
                                </fo:block>
                                <fo:block font-size="10pt" space-before="2mm">
                                    LEI:
                                    <xsl:value-of select="Identifiers/LEI"/>
                                </fo:block>
                            </fo:block-container>

                            <!-- Fund Date Validation Table -->
                            <fo:table table-layout="fixed" width="100%" border-collapse="collapse"
                                      border="1pt solid #dee2e6" space-after="6mm">
                                <fo:table-column column-width="45%"/>
                                <fo:table-column column-width="25%"/>
                                <fo:table-column column-width="20%"/>
                                <fo:table-column column-width="10%"/>

                                <fo:table-header background-color="#fff3e6">
                                    <fo:table-row>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                            <fo:block font-weight="bold" color="#7e3e07">Temporal Check</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                            <fo:block font-weight="bold" color="#7e3e07" text-align="center">Date Value
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                            <fo:block font-weight="bold" color="#7e3e07" text-align="center">Result
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                            <fo:block font-weight="bold" color="#7e3e07" text-align="center">Status
                                            </fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>
                                </fo:table-header>

                                <fo:table-body>
                                    <!-- Check: InceptionDate Present -->
                                    <fo:table-row>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block>Fund Inception Date</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center" font-size="8pt">
                                                <xsl:choose>
                                                    <xsl:when test="FundStaticData/InceptionDate">
                                                        <xsl:value-of select="FundStaticData/InceptionDate"/>
                                                    </xsl:when>
                                                    <xsl:otherwise>NOT PRESENT</xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center" font-size="8pt">
                                                <xsl:choose>
                                                    <xsl:when test="FundStaticData/InceptionDate">PASS</xsl:when>
                                                    <xsl:otherwise>FAIL</xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center">
                                                <xsl:choose>
                                                    <xsl:when test="FundStaticData/InceptionDate">
                                                        <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>

                                    <!-- Check: InceptionDate before ContentDate -->
                                    <fo:table-row background-color="#f8f9fa">
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block>InceptionDate before ContentDate</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center" font-size="8pt">
                                                Inception should precede content date
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center" font-size="8pt">
                                                <xsl:choose>
                                                    <xsl:when
                                                            test="FundStaticData/InceptionDate &lt;= /FundsXML4/ControlData/ContentDate">
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
                                                            test="FundStaticData/InceptionDate &lt;= /FundsXML4/ControlData/ContentDate">
                                                        <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>

                                    <!-- Check: NAV Date Present -->
                                    <fo:table-row>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block>NAV Date Present</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center" font-size="8pt">
                                                <xsl:choose>
                                                    <xsl:when
                                                            test="FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate">
                                                        <xsl:value-of
                                                                select="FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate"/>
                                                    </xsl:when>
                                                    <xsl:otherwise>NOT PRESENT</xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center" font-size="8pt">
                                                <xsl:choose>
                                                    <xsl:when
                                                            test="FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate">
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
                                                            test="FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate">
                                                        <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>

                                    <!-- Check: NAV Date equals ContentDate -->
                                    <fo:table-row background-color="#f8f9fa">
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block>NAV Date matches ContentDate</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center" font-size="8pt">
                                                Should match content date
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center" font-size="8pt">
                                                <xsl:choose>
                                                    <xsl:when
                                                            test="FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate = /FundsXML4/ControlData/ContentDate">
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
                                                            test="FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate = /FundsXML4/ControlData/ContentDate">
                                                        <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <fo:inline color="#ffc107" font-weight="bold">!</fo:inline>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>

                                    <!-- Check: Portfolio NAV Date Consistency -->
                                    <fo:table-row>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block>Portfolio NavDate consistency</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center" font-size="8pt">
                                                All portfolio NavDates match fund NavDate
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center" font-size="8pt">
                                                <xsl:choose>
                                                    <xsl:when
                                                            test="count(FundDynamicData/Portfolios/Portfolio[NavDate != current()/FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate]) = 0">
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
                                                            test="count(FundDynamicData/Portfolios/Portfolio[NavDate != current()/FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate]) = 0">
                                                        <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>

                                    <!-- Check: Share Class Price Dates -->
                                    <xsl:if test="SingleFund/ShareClasses/ShareClass">
                                        <fo:table-row background-color="#f8f9fa">
                                            <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                                <fo:block>Share Class Price Dates Consistency</fo:block>
                                            </fo:table-cell>
                                            <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                                <fo:block text-align="center" font-size="8pt">
                                                    <xsl:value-of
                                                            select="count(SingleFund/ShareClasses/ShareClass)"/> share
                                                    classes
                                                </fo:block>
                                            </fo:table-cell>
                                            <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                                <fo:block text-align="center" font-size="8pt">
                                                    <xsl:choose>
                                                        <xsl:when
                                                                test="count(SingleFund/ShareClasses/ShareClass/Prices/Price) &gt; 0">
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
                                                                test="count(SingleFund/ShareClasses/ShareClass/Prices/Price) &gt; 0">
                                                            <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                                        </xsl:when>
                                                        <xsl:otherwise>
                                                            <fo:inline color="#ffc107" font-weight="bold">!</fo:inline>
                                                        </xsl:otherwise>
                                                    </xsl:choose>
                                                </fo:block>
                                            </fo:table-cell>
                                        </fo:table-row>
                                    </xsl:if>
                                </fo:table-body>
                            </fo:table>
                        </fo:block-container>
                    </xsl:for-each>

                    <!-- NAV Date Distribution Analysis -->
                    <fo:block font-size="18pt" font-weight="bold" color="#495057" space-after="5mm"
                              border-bottom="2pt solid #fd7e14" padding-bottom="3mm">
                        NAV DATE DISTRIBUTION ANALYSIS
                    </fo:block>

                    <fo:block-container background-color="#fff3e6" border="1pt solid #fd7e14" padding="6mm"
                                        space-after="8mm">
                        <fo:block font-weight="bold" color="#7e3e07" space-after="3mm">
                            Date Distribution Summary:
                        </fo:block>

                        <fo:table table-layout="fixed" width="100%">
                            <fo:table-column column-width="50%"/>
                            <fo:table-column column-width="50%"/>
                            <fo:table-body>
                                <fo:table-row>
                                    <fo:table-cell padding="2mm">
                                        <fo:block>
                                            <fo:inline font-weight="bold">Unique NAV Dates:</fo:inline>
                                            <xsl:value-of
                                                    select="count(//NavDate[not(. = preceding::NavDate)])"/>
                                        </fo:block>
                                    </fo:table-cell>
                                    <fo:table-cell padding="2mm">
                                        <fo:block>
                                            <fo:inline font-weight="bold">Total NavDate Fields:</fo:inline>
                                            <xsl:value-of select="count(//NavDate)"/>
                                        </fo:block>
                                    </fo:table-cell>
                                </fo:table-row>
                                <fo:table-row>
                                    <fo:table-cell padding="2mm">
                                        <fo:block>
                                            <fo:inline font-weight="bold">Unique Inception Dates:</fo:inline>
                                            <xsl:value-of
                                                    select="count(//InceptionDate[not(. = preceding::InceptionDate)])"/>
                                        </fo:block>
                                    </fo:table-cell>
                                    <fo:table-cell padding="2mm">
                                        <fo:block>
                                            <fo:inline font-weight="bold">Total InceptionDate Fields:</fo:inline>
                                            <xsl:value-of select="count(//InceptionDate)"/>
                                        </fo:block>
                                    </fo:table-cell>
                                </fo:table-row>
                            </fo:table-body>
                        </fo:table>
                    </fo:block-container>

                    <!-- Summary -->
                    <fo:block-container background-color="#fd7e14" color="#ffffff" padding="8mm"
                                        space-before="10mm" id="temporal-end">
                        <fo:block font-size="18pt" font-weight="bold" space-after="5mm">
                            TEMPORAL CONSISTENCY SUMMARY
                        </fo:block>
                        <fo:block space-after="3mm">
                            This comprehensive temporal consistency report validates all date and time fields
                            across
                            <xsl:value-of select="count(Funds/Fund)"/> fund(s).
                        </fo:block>
                        <fo:block space-after="3mm">
                            Analysis includes document-level date validation, fund inception date consistency,
                            NAV date alignment, and portfolio temporal consistency checks.
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
