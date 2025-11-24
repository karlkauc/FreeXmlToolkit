<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fo="http://www.w3.org/1999/XSL/Format">

    <xsl:output method="xml" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <!-- Main template for Data Quality Report -->
    <xsl:template match="/FundsXML4">
        <fo:root>
            <!-- Page Layout Definition -->
            <fo:layout-master-set>
                <fo:simple-page-master master-name="data-quality-page"
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
            <fo:page-sequence master-reference="data-quality-page">
                <!-- Header -->
                <fo:static-content flow-name="header">
                    <fo:block-container background-color="#6610f2" color="#ffffff" padding="8mm">
                        <fo:block font-size="22pt" font-weight="bold" text-align="center">
                            DATA QUALITY VALIDATION REPORT
                        </fo:block>
                        <fo:block font-size="12pt" text-align="center" space-before="3mm">
                            Comprehensive Data Quality Assessment and Validation
                        </fo:block>
                        <fo:block text-align="center" font-size="10pt" space-before="2mm">
                            Document ID:
                            <xsl:value-of select="ControlData/UniqueDocumentID"/> •
                            Generated:
                            <xsl:value-of select="substring(ControlData/DocumentGenerated, 1, 16)"/>
                        </fo:block>
                    </fo:block-container>
                </fo:static-content>

                <!-- Footer -->
                <fo:static-content flow-name="footer">
                    <fo:block-container border-top="2pt solid #6610f2" padding-top="5mm">
                        <fo:table table-layout="fixed" width="100%">
                            <fo:table-column column-width="40%"/>
                            <fo:table-column column-width="20%"/>
                            <fo:table-column column-width="40%"/>
                            <fo:table-body>
                                <fo:table-row>
                                    <fo:table-cell>
                                        <fo:block font-size="8pt" color="#6610f2" font-weight="bold">
                                            DATA QUALITY VALIDATION
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
                                            <fo:page-number-citation ref-id="dq-end"/>
                                        </fo:block>
                                    </fo:table-cell>
                                    <fo:table-cell>
                                        <fo:block font-size="8pt" color="#666666" text-align="right">
                                            Content Date:
                                            <xsl:value-of select="ControlData/ContentDate"/>
                                        </fo:block>
                                    </fo:table-cell>
                                </fo:table-row>
                            </fo:table-body>
                        </fo:table>
                    </fo:block-container>
                </fo:static-content>

                <!-- Main Content Flow -->
                <fo:flow flow-name="xsl-region-body" font-family="Arial, Helvetica, sans-serif" font-size="10pt">

                    <!-- Executive Summary Dashboard -->
                    <fo:block-container background-color="#e7e3fc" padding="8mm" space-after="8mm"
                                        border="2pt solid #6610f2">
                        <fo:block font-size="16pt" font-weight="bold" color="#4a148c" space-after="5mm">
                            EXECUTIVE SUMMARY
                        </fo:block>

                        <fo:table table-layout="fixed" width="100%" space-after="5mm">
                            <fo:table-column column-width="20%"/>
                            <fo:table-column column-width="20%"/>
                            <fo:table-column column-width="20%"/>
                            <fo:table-column column-width="20%"/>
                            <fo:table-column column-width="20%"/>
                            <fo:table-body>
                                <fo:table-row>
                                    <fo:table-cell padding="3mm">
                                        <fo:block-container background-color="#ffffff" padding="4mm"
                                                            border="1pt solid #6610f2" text-align="center">
                                            <fo:block font-size="8pt" color="#666666" font-weight="bold">TOTAL FUNDS
                                            </fo:block>
                                            <fo:block font-size="20pt" font-weight="bold" color="#6610f2">
                                                <xsl:value-of select="count(Funds/Fund)"/>
                                            </fo:block>
                                        </fo:block-container>
                                    </fo:table-cell>
                                    <fo:table-cell padding="3mm">
                                        <fo:block-container background-color="#ffffff" padding="4mm"
                                                            border="1pt solid #6610f2" text-align="center">
                                            <fo:block font-size="8pt" color="#666666" font-weight="bold">TOTAL POSITIONS
                                            </fo:block>
                                            <fo:block font-size="20pt" font-weight="bold" color="#6610f2">
                                                <xsl:value-of select="count(//Position)"/>
                                            </fo:block>
                                        </fo:block-container>
                                    </fo:table-cell>
                                    <fo:table-cell padding="3mm">
                                        <fo:block-container background-color="#ffffff" padding="4mm"
                                                            border="1pt solid #6610f2" text-align="center">
                                            <fo:block font-size="8pt" color="#666666" font-weight="bold">TOTAL ASSETS
                                            </fo:block>
                                            <fo:block font-size="20pt" font-weight="bold" color="#6610f2">
                                                <xsl:value-of select="count(//Asset)"/>
                                            </fo:block>
                                        </fo:block-container>
                                    </fo:table-cell>
                                    <fo:table-cell padding="3mm">
                                        <fo:block-container background-color="#ffffff" padding="4mm"
                                                            border="1pt solid #6610f2" text-align="center">
                                            <fo:block font-size="8pt" color="#666666" font-weight="bold">VALIDATION
                                                RULES
                                            </fo:block>
                                            <fo:block font-size="20pt" font-weight="bold" color="#6610f2">
                                                25
                                            </fo:block>
                                        </fo:block-container>
                                    </fo:table-cell>
                                    <fo:table-cell padding="3mm">
                                        <fo:block-container background-color="#ffffff" padding="4mm"
                                                            border="1pt solid #6610f2" text-align="center">
                                            <fo:block font-size="8pt" color="#666666" font-weight="bold">SHARE CLASSES
                                            </fo:block>
                                            <fo:block font-size="20pt" font-weight="bold" color="#6610f2">
                                                <xsl:value-of select="count(//ShareClass)"/>
                                            </fo:block>
                                        </fo:block-container>
                                    </fo:table-cell>
                                </fo:table-row>
                            </fo:table-body>
                        </fo:table>
                    </fo:block-container>

                    <!-- Document Structure Validation -->
                    <fo:block font-size="18pt" font-weight="bold" color="#495057" space-after="5mm"
                              border-bottom="2pt solid #6610f2" padding-bottom="3mm">
                        DOCUMENT STRUCTURE VALIDATION
                    </fo:block>

                    <fo:table table-layout="fixed" width="100%" border-collapse="collapse"
                              border="1pt solid #dee2e6" space-after="8mm">
                        <fo:table-column column-width="5%"/>
                        <fo:table-column column-width="40%"/>
                        <fo:table-column column-width="30%"/>
                        <fo:table-column column-width="15%"/>
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
                            <!-- Rule 1: Root Element -->
                            <fo:table-row>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">1</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">Root Element Name</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">Must be 'FundsXML4'</fo:block>
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
                                                <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- Rule 2: ControlData Present -->
                            <fo:table-row background-color="#f8f9fa">
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">2</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">ControlData Section</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">ControlData element exists</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-size="9pt">
                                        <xsl:choose>
                                            <xsl:when test="ControlData">PASS</xsl:when>
                                            <xsl:otherwise>FAIL</xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="ControlData">
                                                <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- Rule 3: UniqueDocumentID -->
                            <fo:table-row>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">3</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">UniqueDocumentID</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">Document has unique ID</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-size="9pt">
                                        <xsl:choose>
                                            <xsl:when
                                                    test="ControlData/UniqueDocumentID and string-length(ControlData/UniqueDocumentID) &gt; 0">
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
                                                    test="ControlData/UniqueDocumentID and string-length(ControlData/UniqueDocumentID) &gt; 0">
                                                <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- Rule 4: DocumentGenerated -->
                            <fo:table-row background-color="#f8f9fa">
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">4</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">DocumentGenerated Timestamp</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">Generation timestamp present</fo:block>
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

                            <!-- Rule 5: ContentDate -->
                            <fo:table-row>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">5</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">ContentDate</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">Content date is specified</fo:block>
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

                            <!-- Rule 6: DataSupplier -->
                            <fo:table-row background-color="#f8f9fa">
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">6</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">DataSupplier Information</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">Complete supplier info</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-size="9pt">
                                        <xsl:choose>
                                            <xsl:when
                                                    test="ControlData/DataSupplier/Name and ControlData/DataSupplier/Contact/Email">
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
                                                    test="ControlData/DataSupplier/Name and ControlData/DataSupplier/Contact/Email">
                                                <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>

                            <!-- Rule 7: Funds Section -->
                            <fo:table-row>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">7</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-weight="bold">Funds Section Present</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block font-size="9pt">At least one fund exists</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center" font-size="9pt">
                                        <xsl:choose>
                                            <xsl:when test="count(Funds/Fund) &gt; 0">PASS</xsl:when>
                                            <xsl:otherwise>FAIL</xsl:otherwise>
                                        </xsl:choose>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                    <fo:block text-align="center">
                                        <xsl:choose>
                                            <xsl:when test="count(Funds/Fund) &gt; 0">
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

                    <!-- Fund-Level Data Quality Validation -->
                    <fo:block font-size="18pt" font-weight="bold" color="#495057" space-after="5mm"
                              border-bottom="2pt solid #6610f2" padding-bottom="3mm">
                        FUND-LEVEL DATA QUALITY
                    </fo:block>

                    <xsl:for-each select="Funds/Fund">
                        <fo:block-container space-after="10mm" keep-together.within-page="always">
                            <!-- Fund Header -->
                            <fo:block-container background-color="#6610f2" color="#ffffff" padding="5mm"
                                                space-after="5mm">
                                <fo:block font-size="14pt" font-weight="bold">
                                    FUND:
                                    <xsl:value-of select="Names/OfficialName"/>
                                </fo:block>
                                <fo:block font-size="10pt" space-before="2mm">
                                    LEI:
                                    <xsl:value-of select="Identifiers/LEI"/> •
                                    Currency:
                                    <xsl:value-of select="Currency"/>
                                </fo:block>
                            </fo:block-container>

                            <!-- Fund Validation Table -->
                            <fo:table table-layout="fixed" width="100%" border-collapse="collapse"
                                      border="1pt solid #dee2e6" space-after="6mm">
                                <fo:table-column column-width="45%"/>
                                <fo:table-column column-width="30%"/>
                                <fo:table-column column-width="15%"/>
                                <fo:table-column column-width="10%"/>

                                <fo:table-header background-color="#e7e3fc">
                                    <fo:table-row>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                            <fo:block font-weight="bold" color="#4a148c">Validation Check</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                            <fo:block font-weight="bold" color="#4a148c">Expected</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                            <fo:block font-weight="bold" color="#4a148c" text-align="center">Result
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="3mm">
                                            <fo:block font-weight="bold" color="#4a148c" text-align="center">Status
                                            </fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>
                                </fo:table-header>

                                <fo:table-body>
                                    <!-- Check 1: LEI Format -->
                                    <fo:table-row>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block>LEI Format Validation</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block font-size="8pt">20 alphanumeric characters</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center" font-size="8pt">
                                                <xsl:choose>
                                                    <xsl:when test="string-length(Identifiers/LEI) = 20">PASS</xsl:when>
                                                    <xsl:otherwise>FAIL</xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center">
                                                <xsl:choose>
                                                    <xsl:when test="string-length(Identifiers/LEI) = 20">
                                                        <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>

                                    <!-- Check 2: Fund Name -->
                                    <fo:table-row background-color="#f8f9fa">
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block>Official Fund Name</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block font-size="8pt">Name is not empty</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center" font-size="8pt">
                                                <xsl:choose>
                                                    <xsl:when
                                                            test="Names/OfficialName and string-length(Names/OfficialName) &gt; 0">
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
                                                            test="Names/OfficialName and string-length(Names/OfficialName) &gt; 0">
                                                        <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>

                                    <!-- Check 3: Currency Code -->
                                    <fo:table-row>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block>Currency Code Format</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block font-size="8pt">ISO 4217 (3 characters)</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center" font-size="8pt">
                                                <xsl:choose>
                                                    <xsl:when test="string-length(Currency) = 3">PASS</xsl:when>
                                                    <xsl:otherwise>FAIL</xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center">
                                                <xsl:choose>
                                                    <xsl:when test="string-length(Currency) = 3">
                                                        <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>

                                    <!-- Check 4: Static Data Present -->
                                    <fo:table-row background-color="#f8f9fa">
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block>Fund Static Data</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block font-size="8pt">Static data section exists</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center" font-size="8pt">
                                                <xsl:choose>
                                                    <xsl:when test="FundStaticData">PASS</xsl:when>
                                                    <xsl:otherwise>FAIL</xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center">
                                                <xsl:choose>
                                                    <xsl:when test="FundStaticData">
                                                        <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>

                                    <!-- Check 5: Dynamic Data Present -->
                                    <fo:table-row>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block>Fund Dynamic Data</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block font-size="8pt">Dynamic data section exists</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center" font-size="8pt">
                                                <xsl:choose>
                                                    <xsl:when test="FundDynamicData">PASS</xsl:when>
                                                    <xsl:otherwise>FAIL</xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center">
                                                <xsl:choose>
                                                    <xsl:when test="FundDynamicData">
                                                        <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>

                                    <!-- Check 6: NAV Data -->
                                    <fo:table-row background-color="#f8f9fa">
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block>Total Net Asset Value</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block font-size="8pt">NAV value is numeric and positive</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center" font-size="8pt">
                                                <xsl:choose>
                                                    <xsl:when
                                                            test="FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount &gt; 0">
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
                                                            test="FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount &gt; 0">
                                                        <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>

                                    <!-- Check 7: NAV Currency -->
                                    <fo:table-row>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block>NAV Currency Consistency</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block font-size="8pt">NAV currency matches fund currency</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center" font-size="8pt">
                                                <xsl:choose>
                                                    <xsl:when
                                                            test="FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount/@ccy = Currency">
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
                                                            test="FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount/@ccy = Currency">
                                                        <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <fo:inline color="#ffc107" font-weight="bold">!</fo:inline>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>

                                    <!-- Check 8: Portfolio Positions -->
                                    <fo:table-row background-color="#f8f9fa">
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block>Portfolio Positions</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block font-size="8pt">At least one position exists</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center" font-size="8pt">
                                                <xsl:choose>
                                                    <xsl:when
                                                            test="count(FundDynamicData/Portfolios/Portfolio/Positions/Position) &gt; 0">
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
                                                            test="count(FundDynamicData/Portfolios/Portfolio/Positions/Position) &gt; 0">
                                                        <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <fo:inline color="#dc3545" font-weight="bold">✗</fo:inline>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>

                                    <!-- Check 9: Position Percentages Sum -->
                                    <fo:table-row>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block>Portfolio Percentage Sum</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block font-size="8pt">Sum approximately 100%</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center" font-size="8pt">
                                                <xsl:variable name="totalPercentage"
                                                              select="sum(FundDynamicData/Portfolios/Portfolio/Positions/Position/TotalPercentage)"/>
                                                <xsl:choose>
                                                    <xsl:when
                                                            test="$totalPercentage &gt; 98 and $totalPercentage &lt; 102">
                                                        PASS
                                                    </xsl:when>
                                                    <xsl:otherwise>WARN</xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center">
                                                <xsl:variable name="totalPercentage"
                                                              select="sum(FundDynamicData/Portfolios/Portfolio/Positions/Position/TotalPercentage)"/>
                                                <xsl:choose>
                                                    <xsl:when
                                                            test="$totalPercentage &gt; 98 and $totalPercentage &lt; 102">
                                                        <fo:inline color="#28a745" font-weight="bold">✓</fo:inline>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <fo:inline color="#ffc107" font-weight="bold">!</fo:inline>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>

                                    <!-- Check 10: Position UniqueIDs -->
                                    <fo:table-row background-color="#f8f9fa">
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block>Position UniqueID Completeness</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block font-size="8pt">All positions have UniqueID</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="0.5pt solid #dee2e6" padding="2mm">
                                            <fo:block text-align="center" font-size="8pt">
                                                <xsl:choose>
                                                    <xsl:when
                                                            test="count(FundDynamicData/Portfolios/Portfolio/Positions/Position[not(UniqueID)]) = 0">
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
                                                            test="count(FundDynamicData/Portfolios/Portfolio/Positions/Position[not(UniqueID)]) = 0">
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
                        </fo:block-container>
                    </xsl:for-each>

                    <!-- Summary -->
                    <fo:block-container background-color="#6610f2" color="#ffffff" padding="8mm"
                                        space-before="10mm" id="dq-end">
                        <fo:block font-size="18pt" font-weight="bold" space-after="5mm">
                            DATA QUALITY SUMMARY
                        </fo:block>
                        <fo:block space-after="3mm">
                            This comprehensive data quality report validates
                            <xsl:value-of select="count(Funds/Fund)"/> fund(s)
                            with
                            <xsl:value-of select="count(//Position)"/> total positions
                            and
                            <xsl:value-of select="count(//Asset)"/> assets.
                        </fo:block>
                        <fo:block space-after="3mm">
                            Validation includes document structure, fund identifiers, currency codes,
                            NAV data integrity, portfolio completeness, and position data quality.
                        </fo:block>
                        <fo:block font-style="italic">
                            Data Supplier:
                            <xsl:value-of select="ControlData/DataSupplier/Name"/>
                        </fo:block>
                    </fo:block-container>
                </fo:flow>
            </fo:page-sequence>
        </fo:root>
    </xsl:template>

</xsl:stylesheet>
