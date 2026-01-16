<?xml version="1.0" encoding="UTF-8"?>
<!--
  XSL-FO Template for XSD Documentation PDF Generation
  FreeXMLToolkit - Universal Toolkit for XML
  Copyright (c) Karl Kauc 2024.

  This template transforms the intermediate XML documentation
  into a professional PDF document using Apache FOP.

  All layout, typography, color, and content parameters are configurable
  via parameters passed from the Java code.
-->
<xsl:stylesheet version="1.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fo="http://www.w3.org/1999/XSL/Format">

    <!-- ============================================== -->
    <!-- PARAMETERS - All passed from Java code        -->
    <!-- ============================================== -->

    <!-- Basic info parameters -->
    <xsl:param name="currentDate"/>
    <xsl:param name="schemaName"/>

    <!-- Page layout parameters -->
    <xsl:param name="pageWidth">210mm</xsl:param>
    <xsl:param name="pageHeight">297mm</xsl:param>
    <xsl:param name="marginSize">20mm</xsl:param>

    <!-- Typography parameters -->
    <xsl:param name="fontSize">11pt</xsl:param>
    <xsl:param name="fontFamily">Helvetica</xsl:param>
    <xsl:param name="lineHeight">1.2</xsl:param>
    <xsl:param name="headingBold">bold</xsl:param>
    <xsl:param name="headingUnderlined">none</xsl:param>
    <xsl:param name="headingColor">#2563EB</xsl:param>

    <!-- Color scheme parameters -->
    <xsl:param name="primaryColor">#2563EB</xsl:param>
    <xsl:param name="lightBackground">#EFF6FF</xsl:param>
    <xsl:param name="tableHeaderBg">#DBEAFE</xsl:param>

    <!-- Content section toggles -->
    <xsl:param name="includeCoverPage">true</xsl:param>
    <xsl:param name="includeToc">true</xsl:param>
    <xsl:param name="includeSchemaOverview">true</xsl:param>
    <xsl:param name="includeSchemaDiagram">false</xsl:param>
    <xsl:param name="includeComplexTypes">true</xsl:param>
    <xsl:param name="includeSimpleTypes">true</xsl:param>
    <xsl:param name="includeDataDictionary">true</xsl:param>
    <xsl:param name="includeElementDiagrams">false</xsl:param>

    <!-- Header & Footer parameters -->
    <xsl:param name="headerStyle">STANDARD</xsl:param>
    <xsl:param name="footerStyle">STANDARD</xsl:param>
    <xsl:param name="includePageNumbers">true</xsl:param>
    <xsl:param name="pageNumberPosition">center</xsl:param>

    <!-- Table style parameter -->
    <xsl:param name="tableStyle">BORDERED</xsl:param>

    <!-- Watermark parameters -->
    <xsl:param name="watermarkText"/>
    <xsl:param name="hasWatermark">false</xsl:param>

    <!-- PDF metadata -->
    <xsl:param name="generateBookmarks">true</xsl:param>

    <!-- ============================================== -->
    <!-- DERIVED VARIABLES                             -->
    <!-- ============================================== -->

    <!-- Secondary colors derived from primary -->
    <xsl:variable name="color-secondary">#64748B</xsl:variable>
    <xsl:variable name="color-border">#CBD5E1</xsl:variable>

    <!-- Zebra stripe background (for ZEBRA_STRIPES table style) -->
    <xsl:variable name="zebra-stripe-bg">#F8FAFC</xsl:variable>

    <!-- ============================================== -->
    <!-- ROOT TEMPLATE                                 -->
    <!-- ============================================== -->
    <xsl:template match="/">
        <fo:root>
            <!-- Apply font-family and base font-size at root level -->
            <xsl:attribute name="font-family">
                <xsl:value-of select="$fontFamily"/>, Arial, sans-serif
            </xsl:attribute>
            <xsl:attribute name="font-size">
                <xsl:value-of select="$fontSize"/>
            </xsl:attribute>
            <xsl:attribute name="line-height">
                <xsl:value-of select="$lineHeight"/>
            </xsl:attribute>

            <!-- Page layout definitions -->
            <fo:layout-master-set>
                <!-- Title page layout (no header/footer) -->
                <fo:simple-page-master master-name="title-page">
                    <xsl:attribute name="page-width"><xsl:value-of select="$pageWidth"/></xsl:attribute>
                    <xsl:attribute name="page-height"><xsl:value-of select="$pageHeight"/></xsl:attribute>
                    <xsl:attribute name="margin-top">30mm</xsl:attribute>
                    <xsl:attribute name="margin-bottom">30mm</xsl:attribute>
                    <xsl:attribute name="margin-left"><xsl:value-of select="$marginSize"/></xsl:attribute>
                    <xsl:attribute name="margin-right"><xsl:value-of select="$marginSize"/></xsl:attribute>
                    <fo:region-body/>
                </fo:simple-page-master>

                <!-- Content page layout with configurable regions -->
                <fo:simple-page-master master-name="content-page">
                    <xsl:attribute name="page-width"><xsl:value-of select="$pageWidth"/></xsl:attribute>
                    <xsl:attribute name="page-height"><xsl:value-of select="$pageHeight"/></xsl:attribute>
                    <xsl:attribute name="margin-top"><xsl:value-of select="$marginSize"/></xsl:attribute>
                    <xsl:attribute name="margin-bottom"><xsl:value-of select="$marginSize"/></xsl:attribute>
                    <xsl:attribute name="margin-left"><xsl:value-of select="$marginSize"/></xsl:attribute>
                    <xsl:attribute name="margin-right"><xsl:value-of select="$marginSize"/></xsl:attribute>
                    <fo:region-body margin-top="15mm" margin-bottom="15mm"/>
                    <xsl:if test="$headerStyle != 'NONE'">
                        <fo:region-before extent="15mm"/>
                    </xsl:if>
                    <xsl:if test="$footerStyle != 'NONE'">
                        <fo:region-after extent="15mm"/>
                    </xsl:if>
                </fo:simple-page-master>
            </fo:layout-master-set>

            <!-- PDF Bookmarks / Outline -->
            <xsl:if test="$generateBookmarks = 'true'">
                <xsl:call-template name="generate-bookmarks"/>
            </xsl:if>

            <!-- Title/Cover Page -->
            <xsl:if test="$includeCoverPage = 'true'">
                <fo:page-sequence master-reference="title-page">
                    <fo:flow flow-name="xsl-region-body">
                        <!-- Watermark on cover page if enabled -->
                        <xsl:if test="$hasWatermark = 'true' and string-length($watermarkText) > 0">
                            <xsl:call-template name="watermark"/>
                        </xsl:if>
                        <xsl:call-template name="title-page-content"/>
                    </fo:flow>
                </fo:page-sequence>
            </xsl:if>

            <!-- Content Pages -->
            <fo:page-sequence master-reference="content-page">
                <!-- Running header (if not NONE) -->
                <xsl:if test="$headerStyle != 'NONE'">
                    <fo:static-content flow-name="xsl-region-before">
                        <xsl:call-template name="page-header"/>
                    </fo:static-content>
                </xsl:if>

                <!-- Running footer (if not NONE) -->
                <xsl:if test="$footerStyle != 'NONE'">
                    <fo:static-content flow-name="xsl-region-after">
                        <xsl:call-template name="page-footer"/>
                    </fo:static-content>
                </xsl:if>

                <!-- Main content -->
                <fo:flow flow-name="xsl-region-body">
                    <!-- Watermark on content pages if enabled -->
                    <xsl:if test="$hasWatermark = 'true' and string-length($watermarkText) > 0">
                        <xsl:call-template name="watermark"/>
                    </xsl:if>

                    <!-- Table of Contents -->
                    <xsl:if test="$includeToc = 'true'">
                        <xsl:call-template name="table-of-contents"/>
                    </xsl:if>

                    <!-- Schema Overview -->
                    <xsl:if test="$includeSchemaOverview = 'true'">
                        <xsl:call-template name="schema-overview"/>
                    </xsl:if>

                    <!-- Namespace Overview (if present in XML) -->
                    <xsl:if test="xsd-documentation/namespace-overview">
                        <xsl:call-template name="namespace-overview"/>
                    </xsl:if>

                    <!-- Schema Diagram -->
                    <xsl:if test="$includeSchemaDiagram = 'true'">
                        <xsl:call-template name="schema-diagram-section"/>
                    </xsl:if>

                    <!-- Complex Types -->
                    <xsl:if test="$includeComplexTypes = 'true'">
                        <xsl:call-template name="complex-types-section"/>
                    </xsl:if>

                    <!-- Simple Types -->
                    <xsl:if test="$includeSimpleTypes = 'true'">
                        <xsl:call-template name="simple-types-section"/>
                    </xsl:if>

                    <!-- Data Dictionary -->
                    <xsl:if test="$includeDataDictionary = 'true'">
                        <xsl:call-template name="data-dictionary-section"/>
                    </xsl:if>

                    <!-- Element Diagrams -->
                    <xsl:if test="$includeElementDiagrams = 'true'">
                        <xsl:call-template name="element-diagrams-section"/>
                    </xsl:if>

                    <!-- Index (if present in XML) -->
                    <xsl:if test="xsd-documentation/index">
                        <xsl:call-template name="index-section"/>
                    </xsl:if>

                    <!-- Last page marker for page count -->
                    <fo:block id="last-page"/>
                </fo:flow>
            </fo:page-sequence>
        </fo:root>
    </xsl:template>

    <!-- ============================================== -->
    <!-- WATERMARK TEMPLATE                            -->
    <!-- ============================================== -->
    <xsl:template name="watermark">
        <fo:block-container absolute-position="fixed" top="120mm" left="0mm" width="100%">
            <fo:block text-align="center" font-size="72pt" color="#E5E7EB"
                font-weight="bold" letter-spacing="15pt">
                <xsl:value-of select="$watermarkText"/>
            </fo:block>
        </fo:block-container>
    </xsl:template>

    <!-- ============================================== -->
    <!-- PAGE HEADER TEMPLATE                          -->
    <!-- ============================================== -->
    <xsl:template name="page-header">
        <xsl:choose>
            <xsl:when test="$headerStyle = 'STANDARD'">
                <fo:block font-size="9pt" border-bottom="0.5pt solid">
                    <xsl:attribute name="color"><xsl:value-of select="$color-secondary"/></xsl:attribute>
                    <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                    <xsl:attribute name="padding-bottom">3mm</xsl:attribute>
                    XSD Documentation: <xsl:value-of select="$schemaName"/>
                </fo:block>
            </xsl:when>
            <xsl:when test="$headerStyle = 'MINIMAL'">
                <fo:block border-bottom="0.5pt solid">
                    <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                    <xsl:attribute name="padding-bottom">3mm</xsl:attribute>
                    <!-- Minimal header: just a line -->
                </fo:block>
            </xsl:when>
            <xsl:when test="$headerStyle = 'FULL'">
                <fo:block font-size="9pt" border-bottom="0.5pt solid">
                    <xsl:attribute name="color"><xsl:value-of select="$color-secondary"/></xsl:attribute>
                    <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                    <xsl:attribute name="padding-bottom">3mm</xsl:attribute>
                    <fo:inline font-weight="bold">XSD Documentation</fo:inline>
                    <xsl:text> | </xsl:text>
                    <xsl:value-of select="$schemaName"/>
                    <fo:inline padding-left="10mm">
                        <xsl:value-of select="$currentDate"/>
                    </fo:inline>
                </fo:block>
            </xsl:when>
            <!-- NONE is handled by not creating the static-content at all -->
        </xsl:choose>
    </xsl:template>

    <!-- ============================================== -->
    <!-- PAGE FOOTER TEMPLATE                          -->
    <!-- ============================================== -->
    <xsl:template name="page-footer">
        <fo:block font-size="9pt" border-top="0.5pt solid" padding-top="3mm">
            <xsl:attribute name="color"><xsl:value-of select="$color-secondary"/></xsl:attribute>
            <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
            <xsl:attribute name="text-align"><xsl:value-of select="$pageNumberPosition"/></xsl:attribute>

            <xsl:choose>
                <xsl:when test="$footerStyle = 'STANDARD'">
                    <xsl:if test="$includePageNumbers = 'true'">
                        Page <fo:page-number/> of <fo:page-number-citation ref-id="last-page"/>
                    </xsl:if>
                    <fo:inline padding-left="20mm">Generated by FreeXmlToolkit on <xsl:value-of select="$currentDate"/></fo:inline>
                </xsl:when>
                <xsl:when test="$footerStyle = 'MINIMAL'">
                    <xsl:if test="$includePageNumbers = 'true'">
                        Page <fo:page-number/>
                    </xsl:if>
                </xsl:when>
                <xsl:when test="$footerStyle = 'FULL'">
                    <xsl:if test="$includePageNumbers = 'true'">
                        Page <fo:page-number/> of <fo:page-number-citation ref-id="last-page"/>
                    </xsl:if>
                    <fo:inline padding-left="15mm">
                        <xsl:value-of select="$schemaName"/>
                    </fo:inline>
                    <fo:inline padding-left="15mm">
                        Generated by FreeXmlToolkit on <xsl:value-of select="$currentDate"/>
                    </fo:inline>
                </xsl:when>
                <!-- NONE is handled by not creating the static-content at all -->
            </xsl:choose>
        </fo:block>
    </xsl:template>

    <!-- ============================================== -->
    <!-- BOOKMARKS TEMPLATE                            -->
    <!-- ============================================== -->
    <xsl:template name="generate-bookmarks">
        <fo:bookmark-tree>
            <xsl:if test="$includeSchemaOverview = 'true'">
                <fo:bookmark internal-destination="schema-overview">
                    <fo:bookmark-title>Schema Overview</fo:bookmark-title>
                </fo:bookmark>
            </xsl:if>

            <xsl:if test="xsd-documentation/namespace-overview">
                <fo:bookmark internal-destination="namespace-overview">
                    <fo:bookmark-title>Namespace Overview</fo:bookmark-title>
                </fo:bookmark>
            </xsl:if>

            <xsl:if test="$includeSchemaDiagram = 'true' and xsd-documentation/schema-diagram/image-path">
                <fo:bookmark internal-destination="schema-diagram">
                    <fo:bookmark-title>Schema Diagram</fo:bookmark-title>
                </fo:bookmark>
            </xsl:if>

            <xsl:if test="$includeComplexTypes = 'true' and xsd-documentation/complex-types/complex-type">
                <fo:bookmark internal-destination="complex-types">
                    <fo:bookmark-title>Complex Types</fo:bookmark-title>
                </fo:bookmark>
            </xsl:if>

            <xsl:if test="$includeSimpleTypes = 'true' and xsd-documentation/simple-types/simple-type">
                <fo:bookmark internal-destination="simple-types">
                    <fo:bookmark-title>Simple Types</fo:bookmark-title>
                </fo:bookmark>
            </xsl:if>

            <xsl:if test="$includeDataDictionary = 'true'">
                <fo:bookmark internal-destination="data-dictionary">
                    <fo:bookmark-title>Data Dictionary</fo:bookmark-title>
                </fo:bookmark>
            </xsl:if>

            <xsl:if test="$includeElementDiagrams = 'true' and xsd-documentation/element-diagrams/diagram">
                <fo:bookmark internal-destination="element-diagrams">
                    <fo:bookmark-title>Element Diagrams</fo:bookmark-title>
                </fo:bookmark>
            </xsl:if>

            <xsl:if test="xsd-documentation/index">
                <fo:bookmark internal-destination="index-section">
                    <fo:bookmark-title>Index</fo:bookmark-title>
                </fo:bookmark>
            </xsl:if>
        </fo:bookmark-tree>
    </xsl:template>

    <!-- ============================================== -->
    <!-- TITLE/COVER PAGE CONTENT                      -->
    <!-- ============================================== -->
    <xsl:template name="title-page-content">
        <fo:block text-align="center" margin-top="80mm">
            <fo:block font-size="32pt" space-after="15mm">
                <xsl:attribute name="font-weight"><xsl:value-of select="$headingBold"/></xsl:attribute>
                <xsl:attribute name="text-decoration"><xsl:value-of select="$headingUnderlined"/></xsl:attribute>
                <xsl:attribute name="color"><xsl:value-of select="$primaryColor"/></xsl:attribute>
                XSD Schema Documentation
            </fo:block>

            <fo:block font-size="20pt" space-after="30mm">
                <xsl:attribute name="color"><xsl:value-of select="$color-secondary"/></xsl:attribute>
                <xsl:value-of select="xsd-documentation/metadata/schema-name"/>
            </fo:block>

            <fo:block font-size="12pt" space-after="5mm">
                <xsl:attribute name="color"><xsl:value-of select="$color-secondary"/></xsl:attribute>
                <fo:inline font-weight="bold">Target Namespace:</fo:inline>
                <xsl:text> </xsl:text>
                <xsl:choose>
                    <xsl:when test="string-length(xsd-documentation/metadata/target-namespace) > 0">
                        <xsl:value-of select="xsd-documentation/metadata/target-namespace"/>
                    </xsl:when>
                    <xsl:otherwise>(no namespace)</xsl:otherwise>
                </xsl:choose>
            </fo:block>

            <xsl:if test="string-length(xsd-documentation/metadata/version) > 0">
                <fo:block font-size="12pt" space-after="5mm">
                    <xsl:attribute name="color"><xsl:value-of select="$color-secondary"/></xsl:attribute>
                    <fo:inline font-weight="bold">Version:</fo:inline>
                    <xsl:text> </xsl:text>
                    <xsl:value-of select="xsd-documentation/metadata/version"/>
                </fo:block>
            </xsl:if>

            <fo:block font-size="12pt" font-style="italic" space-after="5mm">
                <xsl:attribute name="color"><xsl:value-of select="$color-secondary"/></xsl:attribute>
                Generated: <xsl:value-of select="xsd-documentation/metadata/generation-date"/>
            </fo:block>

            <fo:block font-size="11pt" margin-top="50mm">
                <xsl:attribute name="color"><xsl:value-of select="$color-secondary"/></xsl:attribute>
                Generated by <fo:inline font-weight="bold">FreeXmlToolkit</fo:inline>
            </fo:block>
        </fo:block>
    </xsl:template>

    <!-- ============================================== -->
    <!-- TABLE OF CONTENTS                             -->
    <!-- ============================================== -->
    <xsl:template name="table-of-contents">
        <fo:block font-size="20pt" space-before="5mm" space-after="10mm">
            <xsl:attribute name="font-weight"><xsl:value-of select="$headingBold"/></xsl:attribute>
            <xsl:attribute name="text-decoration"><xsl:value-of select="$headingUnderlined"/></xsl:attribute>
            <xsl:attribute name="color"><xsl:value-of select="$primaryColor"/></xsl:attribute>
            Table of Contents
        </fo:block>

        <xsl:variable name="toc-font-size">12pt</xsl:variable>

        <xsl:if test="$includeSchemaOverview = 'true'">
            <fo:block font-size="{$toc-font-size}" space-after="5mm">
                <fo:basic-link internal-destination="schema-overview">
                    1. Schema Overview
                    <fo:leader leader-pattern="dots"/>
                    <fo:page-number-citation ref-id="schema-overview"/>
                </fo:basic-link>
            </fo:block>
        </xsl:if>

        <xsl:if test="xsd-documentation/namespace-overview">
            <fo:block font-size="{$toc-font-size}" space-after="5mm">
                <fo:basic-link internal-destination="namespace-overview">
                    2. Namespace Overview
                    <fo:leader leader-pattern="dots"/>
                    <fo:page-number-citation ref-id="namespace-overview"/>
                </fo:basic-link>
            </fo:block>
        </xsl:if>

        <xsl:if test="$includeSchemaDiagram = 'true' and xsd-documentation/schema-diagram/image-path">
            <fo:block font-size="{$toc-font-size}" space-after="5mm">
                <fo:basic-link internal-destination="schema-diagram">
                    Schema Diagram
                    <fo:leader leader-pattern="dots"/>
                    <fo:page-number-citation ref-id="schema-diagram"/>
                </fo:basic-link>
            </fo:block>
        </xsl:if>

        <xsl:if test="$includeComplexTypes = 'true' and xsd-documentation/complex-types/complex-type">
            <fo:block font-size="{$toc-font-size}" space-after="5mm">
                <fo:basic-link internal-destination="complex-types">
                    Complex Types
                    <fo:leader leader-pattern="dots"/>
                    <fo:page-number-citation ref-id="complex-types"/>
                </fo:basic-link>
            </fo:block>
        </xsl:if>

        <xsl:if test="$includeSimpleTypes = 'true' and xsd-documentation/simple-types/simple-type">
            <fo:block font-size="{$toc-font-size}" space-after="5mm">
                <fo:basic-link internal-destination="simple-types">
                    Simple Types
                    <fo:leader leader-pattern="dots"/>
                    <fo:page-number-citation ref-id="simple-types"/>
                </fo:basic-link>
            </fo:block>
        </xsl:if>

        <xsl:if test="$includeDataDictionary = 'true'">
            <fo:block font-size="{$toc-font-size}" space-after="5mm">
                <fo:basic-link internal-destination="data-dictionary">
                    Data Dictionary
                    <fo:leader leader-pattern="dots"/>
                    <fo:page-number-citation ref-id="data-dictionary"/>
                </fo:basic-link>
            </fo:block>
        </xsl:if>

        <xsl:if test="$includeElementDiagrams = 'true' and xsd-documentation/element-diagrams/diagram">
            <fo:block font-size="{$toc-font-size}" space-after="5mm">
                <fo:basic-link internal-destination="element-diagrams">
                    Element Diagrams
                    <fo:leader leader-pattern="dots"/>
                    <fo:page-number-citation ref-id="element-diagrams"/>
                </fo:basic-link>
            </fo:block>
        </xsl:if>

        <xsl:if test="xsd-documentation/index">
            <fo:block font-size="{$toc-font-size}" space-after="5mm">
                <fo:basic-link internal-destination="index-section">
                    Index
                    <fo:leader leader-pattern="dots"/>
                    <fo:page-number-citation ref-id="index-section"/>
                </fo:basic-link>
            </fo:block>
        </xsl:if>

        <fo:block break-after="page"/>
    </xsl:template>

    <!-- ============================================== -->
    <!-- SCHEMA OVERVIEW SECTION                       -->
    <!-- ============================================== -->
    <xsl:template name="schema-overview">
        <fo:block id="schema-overview" font-size="20pt" space-before="5mm" space-after="10mm">
            <xsl:attribute name="font-weight"><xsl:value-of select="$headingBold"/></xsl:attribute>
            <xsl:attribute name="text-decoration"><xsl:value-of select="$headingUnderlined"/></xsl:attribute>
            <xsl:attribute name="color"><xsl:value-of select="$primaryColor"/></xsl:attribute>
            Schema Overview
        </fo:block>

        <fo:table table-layout="fixed" width="100%" border="0.5pt solid">
            <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
            <fo:table-column column-width="40%"/>
            <fo:table-column column-width="60%"/>
            <fo:table-body>
                <xsl:call-template name="overview-row">
                    <xsl:with-param name="label">File Path</xsl:with-param>
                    <xsl:with-param name="value" select="xsd-documentation/metadata/file-path"/>
                </xsl:call-template>
                <xsl:call-template name="overview-row">
                    <xsl:with-param name="label">Target Namespace</xsl:with-param>
                    <xsl:with-param name="value">
                        <xsl:choose>
                            <xsl:when test="string-length(xsd-documentation/metadata/target-namespace) > 0">
                                <xsl:value-of select="xsd-documentation/metadata/target-namespace"/>
                            </xsl:when>
                            <xsl:otherwise>(no namespace)</xsl:otherwise>
                        </xsl:choose>
                    </xsl:with-param>
                </xsl:call-template>
                <xsl:call-template name="overview-row">
                    <xsl:with-param name="label">Schema Version</xsl:with-param>
                    <xsl:with-param name="value">
                        <xsl:choose>
                            <xsl:when test="string-length(xsd-documentation/metadata/version) > 0">
                                <xsl:value-of select="xsd-documentation/metadata/version"/>
                            </xsl:when>
                            <xsl:otherwise>(not specified)</xsl:otherwise>
                        </xsl:choose>
                    </xsl:with-param>
                </xsl:call-template>
                <xsl:call-template name="overview-row">
                    <xsl:with-param name="label">Element Form Default</xsl:with-param>
                    <xsl:with-param name="value" select="xsd-documentation/metadata/element-form-default"/>
                </xsl:call-template>
                <xsl:call-template name="overview-row">
                    <xsl:with-param name="label">Attribute Form Default</xsl:with-param>
                    <xsl:with-param name="value" select="xsd-documentation/metadata/attribute-form-default"/>
                </xsl:call-template>
            </fo:table-body>
        </fo:table>

        <!-- Statistics -->
        <fo:block font-size="16pt" space-before="15mm" space-after="8mm">
            <xsl:attribute name="font-weight"><xsl:value-of select="$headingBold"/></xsl:attribute>
            <xsl:attribute name="color"><xsl:value-of select="$primaryColor"/></xsl:attribute>
            Statistics
        </fo:block>

        <fo:table table-layout="fixed" width="100%" border="0.5pt solid">
            <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
            <fo:table-column column-width="50%"/>
            <fo:table-column column-width="50%"/>
            <fo:table-body>
                <xsl:call-template name="overview-row">
                    <xsl:with-param name="label">Global Elements</xsl:with-param>
                    <xsl:with-param name="value" select="xsd-documentation/statistics/global-elements"/>
                </xsl:call-template>
                <xsl:call-template name="overview-row">
                    <xsl:with-param name="label">Global ComplexTypes</xsl:with-param>
                    <xsl:with-param name="value" select="xsd-documentation/statistics/global-complex-types"/>
                </xsl:call-template>
                <xsl:call-template name="overview-row">
                    <xsl:with-param name="label">Global SimpleTypes</xsl:with-param>
                    <xsl:with-param name="value" select="xsd-documentation/statistics/global-simple-types"/>
                </xsl:call-template>
                <xsl:call-template name="overview-row">
                    <xsl:with-param name="label">Total Elements Documented</xsl:with-param>
                    <xsl:with-param name="value" select="xsd-documentation/statistics/total-elements"/>
                </xsl:call-template>
            </fo:table-body>
        </fo:table>

        <fo:block break-after="page"/>
    </xsl:template>

    <!-- Helper template for overview table rows -->
    <xsl:template name="overview-row">
        <xsl:param name="label"/>
        <xsl:param name="value"/>
        <fo:table-row>
            <fo:table-cell padding="4mm" border="0.5pt solid">
                <xsl:attribute name="background-color"><xsl:value-of select="$tableHeaderBg"/></xsl:attribute>
                <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                <fo:block font-weight="bold"><xsl:value-of select="$label"/></fo:block>
            </fo:table-cell>
            <fo:table-cell padding="4mm" border="0.5pt solid">
                <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                <fo:block><xsl:value-of select="$value"/></fo:block>
            </fo:table-cell>
        </fo:table-row>
    </xsl:template>

    <!-- ============================================== -->
    <!-- NAMESPACE OVERVIEW SECTION                    -->
    <!-- ============================================== -->
    <xsl:template name="namespace-overview">
        <fo:block id="namespace-overview" font-size="20pt" space-before="5mm" space-after="10mm">
            <xsl:attribute name="font-weight"><xsl:value-of select="$headingBold"/></xsl:attribute>
            <xsl:attribute name="text-decoration"><xsl:value-of select="$headingUnderlined"/></xsl:attribute>
            <xsl:attribute name="color"><xsl:value-of select="$primaryColor"/></xsl:attribute>
            Namespace Overview
        </fo:block>

        <fo:block font-size="11pt" space-after="10mm">
            <xsl:attribute name="color"><xsl:value-of select="$color-secondary"/></xsl:attribute>
            Overview of all namespaces used in this schema.
        </fo:block>

        <xsl:if test="xsd-documentation/namespace-overview/namespace">
            <fo:table table-layout="fixed" width="100%" border="0.5pt solid">
                <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                <fo:table-column column-width="25%"/>
                <fo:table-column column-width="50%"/>
                <fo:table-column column-width="25%"/>
                <fo:table-header>
                    <fo:table-row>
                        <xsl:attribute name="background-color"><xsl:value-of select="$tableHeaderBg"/></xsl:attribute>
                        <fo:table-cell padding="4mm" border="0.5pt solid">
                            <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                            <fo:block font-weight="bold">Prefix</fo:block>
                        </fo:table-cell>
                        <fo:table-cell padding="4mm" border="0.5pt solid">
                            <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                            <fo:block font-weight="bold">Namespace URI</fo:block>
                        </fo:table-cell>
                        <fo:table-cell padding="4mm" border="0.5pt solid">
                            <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                            <fo:block font-weight="bold">Type</fo:block>
                        </fo:table-cell>
                    </fo:table-row>
                </fo:table-header>
                <fo:table-body>
                    <xsl:for-each select="xsd-documentation/namespace-overview/namespace">
                        <fo:table-row>
                            <xsl:call-template name="apply-table-row-style"/>
                            <fo:table-cell padding="3mm" border="0.5pt solid">
                                <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                                <fo:block font-size="10pt"><xsl:value-of select="prefix"/></fo:block>
                            </fo:table-cell>
                            <fo:table-cell padding="3mm" border="0.5pt solid">
                                <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                                <fo:block font-size="10pt"><xsl:value-of select="uri"/></fo:block>
                            </fo:table-cell>
                            <fo:table-cell padding="3mm" border="0.5pt solid">
                                <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                                <fo:block font-size="10pt"><xsl:value-of select="type"/></fo:block>
                            </fo:table-cell>
                        </fo:table-row>
                    </xsl:for-each>
                </fo:table-body>
            </fo:table>
        </xsl:if>

        <fo:block break-after="page"/>
    </xsl:template>

    <!-- ============================================== -->
    <!-- COMPLEX TYPES SECTION                         -->
    <!-- ============================================== -->
    <xsl:template name="complex-types-section">
        <xsl:if test="xsd-documentation/complex-types/complex-type">
            <fo:block id="complex-types" font-size="20pt" space-before="5mm" space-after="10mm">
                <xsl:attribute name="font-weight"><xsl:value-of select="$headingBold"/></xsl:attribute>
                <xsl:attribute name="text-decoration"><xsl:value-of select="$headingUnderlined"/></xsl:attribute>
                <xsl:attribute name="color"><xsl:value-of select="$primaryColor"/></xsl:attribute>
                Complex Types
            </fo:block>

            <fo:block font-size="11pt" space-after="10mm">
                <xsl:attribute name="color"><xsl:value-of select="$color-secondary"/></xsl:attribute>
                This section lists all globally defined ComplexTypes in the schema.
            </fo:block>

            <fo:table table-layout="fixed" width="100%" border="0.5pt solid">
                <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                <fo:table-column column-width="40%"/>
                <fo:table-column column-width="40%"/>
                <fo:table-column column-width="20%"/>
                <fo:table-header>
                    <fo:table-row>
                        <xsl:attribute name="background-color"><xsl:value-of select="$tableHeaderBg"/></xsl:attribute>
                        <fo:table-cell padding="4mm" border="0.5pt solid">
                            <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                            <fo:block font-weight="bold">Type Name</fo:block>
                        </fo:table-cell>
                        <fo:table-cell padding="4mm" border="0.5pt solid">
                            <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                            <fo:block font-weight="bold">Base Type</fo:block>
                        </fo:table-cell>
                        <fo:table-cell padding="4mm" border="0.5pt solid">
                            <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                            <fo:block font-weight="bold">Usage Count</fo:block>
                        </fo:table-cell>
                    </fo:table-row>
                </fo:table-header>
                <fo:table-body>
                    <xsl:for-each select="xsd-documentation/complex-types/complex-type">
                        <fo:table-row>
                            <xsl:call-template name="apply-table-row-style"/>
                            <fo:table-cell padding="3mm" border="0.5pt solid">
                                <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                                <fo:block font-size="10pt"><xsl:value-of select="name"/></fo:block>
                            </fo:table-cell>
                            <fo:table-cell padding="3mm" border="0.5pt solid">
                                <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                                <fo:block font-size="10pt"><xsl:value-of select="base-type"/></fo:block>
                            </fo:table-cell>
                            <fo:table-cell padding="3mm" border="0.5pt solid">
                                <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                                <fo:block font-size="10pt" text-align="center"><xsl:value-of select="usage-count"/></fo:block>
                            </fo:table-cell>
                        </fo:table-row>
                    </xsl:for-each>
                </fo:table-body>
            </fo:table>

            <fo:block break-after="page"/>
        </xsl:if>
    </xsl:template>

    <!-- ============================================== -->
    <!-- SIMPLE TYPES SECTION                          -->
    <!-- ============================================== -->
    <xsl:template name="simple-types-section">
        <xsl:if test="xsd-documentation/simple-types/simple-type">
            <fo:block id="simple-types" font-size="20pt" space-before="5mm" space-after="10mm">
                <xsl:attribute name="font-weight"><xsl:value-of select="$headingBold"/></xsl:attribute>
                <xsl:attribute name="text-decoration"><xsl:value-of select="$headingUnderlined"/></xsl:attribute>
                <xsl:attribute name="color"><xsl:value-of select="$primaryColor"/></xsl:attribute>
                Simple Types
            </fo:block>

            <fo:block font-size="11pt" space-after="10mm">
                <xsl:attribute name="color"><xsl:value-of select="$color-secondary"/></xsl:attribute>
                This section lists all globally defined SimpleTypes in the schema.
            </fo:block>

            <fo:table table-layout="fixed" width="100%" border="0.5pt solid">
                <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                <fo:table-column column-width="35%"/>
                <fo:table-column column-width="35%"/>
                <fo:table-column column-width="30%"/>
                <fo:table-header>
                    <fo:table-row>
                        <xsl:attribute name="background-color"><xsl:value-of select="$tableHeaderBg"/></xsl:attribute>
                        <fo:table-cell padding="4mm" border="0.5pt solid">
                            <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                            <fo:block font-weight="bold">Type Name</fo:block>
                        </fo:table-cell>
                        <fo:table-cell padding="4mm" border="0.5pt solid">
                            <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                            <fo:block font-weight="bold">Base Type</fo:block>
                        </fo:table-cell>
                        <fo:table-cell padding="4mm" border="0.5pt solid">
                            <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                            <fo:block font-weight="bold">Facets</fo:block>
                        </fo:table-cell>
                    </fo:table-row>
                </fo:table-header>
                <fo:table-body>
                    <xsl:for-each select="xsd-documentation/simple-types/simple-type">
                        <fo:table-row>
                            <xsl:call-template name="apply-table-row-style"/>
                            <fo:table-cell padding="3mm" border="0.5pt solid">
                                <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                                <fo:block font-size="10pt"><xsl:value-of select="name"/></fo:block>
                            </fo:table-cell>
                            <fo:table-cell padding="3mm" border="0.5pt solid">
                                <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                                <fo:block font-size="10pt"><xsl:value-of select="base-type"/></fo:block>
                            </fo:table-cell>
                            <fo:table-cell padding="3mm" border="0.5pt solid">
                                <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                                <fo:block font-size="9pt"><xsl:value-of select="facets"/></fo:block>
                            </fo:table-cell>
                        </fo:table-row>
                    </xsl:for-each>
                </fo:table-body>
            </fo:table>

            <fo:block break-after="page"/>
        </xsl:if>
    </xsl:template>

    <!-- ============================================== -->
    <!-- DATA DICTIONARY SECTION                       -->
    <!-- ============================================== -->
    <xsl:template name="data-dictionary-section">
        <fo:block id="data-dictionary" font-size="20pt" space-before="5mm" space-after="10mm">
            <xsl:attribute name="font-weight"><xsl:value-of select="$headingBold"/></xsl:attribute>
            <xsl:attribute name="text-decoration"><xsl:value-of select="$headingUnderlined"/></xsl:attribute>
            <xsl:attribute name="color"><xsl:value-of select="$primaryColor"/></xsl:attribute>
            Data Dictionary
        </fo:block>

        <fo:block font-size="11pt" space-after="10mm">
            <xsl:attribute name="color"><xsl:value-of select="$color-secondary"/></xsl:attribute>
            Complete listing of all elements in the schema with their types and cardinality.
        </fo:block>

        <xsl:if test="xsd-documentation/data-dictionary/element">
            <fo:table table-layout="fixed" width="100%" border="0.5pt solid">
                <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                <fo:table-column column-width="30%"/>
                <fo:table-column column-width="18%"/>
                <fo:table-column column-width="10%"/>
                <fo:table-column column-width="15%"/>
                <fo:table-column column-width="27%"/>
                <fo:table-header>
                    <fo:table-row>
                        <xsl:attribute name="background-color"><xsl:value-of select="$tableHeaderBg"/></xsl:attribute>
                        <fo:table-cell padding="3mm" border="0.5pt solid">
                            <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                            <fo:block font-weight="bold" font-size="9pt">Element Path</fo:block>
                        </fo:table-cell>
                        <fo:table-cell padding="3mm" border="0.5pt solid">
                            <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                            <fo:block font-weight="bold" font-size="9pt">Type</fo:block>
                        </fo:table-cell>
                        <fo:table-cell padding="3mm" border="0.5pt solid">
                            <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                            <fo:block font-weight="bold" font-size="9pt">Card.</fo:block>
                        </fo:table-cell>
                        <fo:table-cell padding="3mm" border="0.5pt solid">
                            <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                            <fo:block font-weight="bold" font-size="9pt">Restrictions</fo:block>
                        </fo:table-cell>
                        <fo:table-cell padding="3mm" border="0.5pt solid">
                            <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                            <fo:block font-weight="bold" font-size="9pt">Description</fo:block>
                        </fo:table-cell>
                    </fo:table-row>
                </fo:table-header>
                <fo:table-body>
                    <xsl:for-each select="xsd-documentation/data-dictionary/element">
                        <fo:table-row>
                            <xsl:call-template name="apply-table-row-style"/>
                            <fo:table-cell padding="2mm" border="0.5pt solid">
                                <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                                <fo:block font-size="8pt" wrap-option="wrap"><xsl:value-of select="path"/></fo:block>
                            </fo:table-cell>
                            <fo:table-cell padding="2mm" border="0.5pt solid">
                                <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                                <fo:block font-size="8pt"><xsl:value-of select="type"/></fo:block>
                            </fo:table-cell>
                            <fo:table-cell padding="2mm" border="0.5pt solid">
                                <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                                <fo:block font-size="8pt" text-align="center"><xsl:value-of select="cardinality"/></fo:block>
                            </fo:table-cell>
                            <fo:table-cell padding="2mm" border="0.5pt solid">
                                <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                                <fo:block font-size="7pt" wrap-option="wrap"><xsl:value-of select="restrictions"/></fo:block>
                            </fo:table-cell>
                            <fo:table-cell padding="2mm" border="0.5pt solid">
                                <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                                <fo:block font-size="8pt" wrap-option="wrap"><xsl:value-of select="description"/></fo:block>
                            </fo:table-cell>
                        </fo:table-row>
                    </xsl:for-each>
                </fo:table-body>
            </fo:table>
        </xsl:if>
    </xsl:template>

    <!-- ============================================== -->
    <!-- SCHEMA DIAGRAM SECTION                        -->
    <!-- ============================================== -->
    <xsl:template name="schema-diagram-section">
        <xsl:if test="xsd-documentation/schema-diagram/image-path">
            <fo:block break-before="page"/>
            <fo:block id="schema-diagram" font-size="20pt" space-before="5mm" space-after="10mm">
                <xsl:attribute name="font-weight"><xsl:value-of select="$headingBold"/></xsl:attribute>
                <xsl:attribute name="text-decoration"><xsl:value-of select="$headingUnderlined"/></xsl:attribute>
                <xsl:attribute name="color"><xsl:value-of select="$primaryColor"/></xsl:attribute>
                Schema Diagram
            </fo:block>

            <fo:block font-size="11pt" space-after="10mm">
                <xsl:attribute name="color"><xsl:value-of select="$color-secondary"/></xsl:attribute>
                Visual representation of the complete schema structure starting from root element:
                <fo:inline font-weight="bold">
                    <xsl:value-of select="xsd-documentation/schema-diagram/root-element"/>
                </fo:inline>
            </fo:block>

            <!-- Diagram image - scale based on page size -->
            <fo:block text-align="center" space-before="5mm">
                <fo:external-graphic
                    src="{xsd-documentation/schema-diagram/image-path}"
                    content-width="scale-to-fit"
                    content-height="scale-to-fit"
                    scaling="uniform">
                    <!-- Use 80% of available width for diagram -->
                    <xsl:attribute name="max-width">90%</xsl:attribute>
                    <xsl:attribute name="max-height">200mm</xsl:attribute>
                </fo:external-graphic>
            </fo:block>

            <!-- Caption -->
            <fo:block font-size="10pt" font-style="italic" text-align="center" space-before="3mm" space-after="8mm">
                <xsl:attribute name="color"><xsl:value-of select="$color-secondary"/></xsl:attribute>
                Figure: Complete Schema Structure
            </fo:block>

            <fo:block break-after="page"/>
        </xsl:if>
    </xsl:template>

    <!-- ============================================== -->
    <!-- ELEMENT DIAGRAMS SECTION                      -->
    <!-- ============================================== -->
    <xsl:template name="element-diagrams-section">
        <xsl:if test="xsd-documentation/element-diagrams/diagram">
            <fo:block break-before="page"/>
            <fo:block id="element-diagrams" font-size="20pt" space-before="5mm" space-after="10mm">
                <xsl:attribute name="font-weight"><xsl:value-of select="$headingBold"/></xsl:attribute>
                <xsl:attribute name="text-decoration"><xsl:value-of select="$headingUnderlined"/></xsl:attribute>
                <xsl:attribute name="color"><xsl:value-of select="$primaryColor"/></xsl:attribute>
                Element Diagrams
            </fo:block>

            <fo:block font-size="11pt" space-after="10mm">
                <xsl:attribute name="color"><xsl:value-of select="$color-secondary"/></xsl:attribute>
                Detailed visual representation of individual elements and their structure.
            </fo:block>

            <xsl:for-each select="xsd-documentation/element-diagrams/diagram">
                <fo:block space-before="8mm" space-after="4mm">
                    <!-- Element name heading -->
                    <fo:block font-size="16pt" font-weight="bold" space-after="2mm">
                        <xsl:attribute name="color"><xsl:value-of select="$primaryColor"/></xsl:attribute>
                        <xsl:value-of select="element-name"/>
                    </fo:block>

                    <!-- Element info -->
                    <fo:block font-size="10pt" space-after="1mm">
                        <xsl:attribute name="color"><xsl:value-of select="$color-secondary"/></xsl:attribute>
                        Path: <xsl:value-of select="path"/>
                    </fo:block>
                    <fo:block font-size="10pt" space-after="3mm">
                        <xsl:attribute name="color"><xsl:value-of select="$color-secondary"/></xsl:attribute>
                        Type: <xsl:value-of select="type"/>
                    </fo:block>

                    <!-- Diagram image -->
                    <fo:block text-align="center">
                        <fo:external-graphic
                            src="{image-path}"
                            content-width="scale-to-fit"
                            content-height="scale-to-fit"
                            max-width="90%"
                            max-height="100mm"
                            scaling="uniform"/>
                    </fo:block>

                    <!-- Caption -->
                    <fo:block font-size="9pt" font-style="italic" text-align="center" space-before="2mm">
                        <xsl:attribute name="color"><xsl:value-of select="$color-secondary"/></xsl:attribute>
                        Figure: Structure of <xsl:value-of select="element-name"/>
                    </fo:block>
                </fo:block>
            </xsl:for-each>
        </xsl:if>
    </xsl:template>

    <!-- ============================================== -->
    <!-- INDEX SECTION                                 -->
    <!-- ============================================== -->
    <xsl:template name="index-section">
        <fo:block break-before="page"/>
        <fo:block id="index-section" font-size="20pt" space-before="5mm" space-after="10mm">
            <xsl:attribute name="font-weight"><xsl:value-of select="$headingBold"/></xsl:attribute>
            <xsl:attribute name="text-decoration"><xsl:value-of select="$headingUnderlined"/></xsl:attribute>
            <xsl:attribute name="color"><xsl:value-of select="$primaryColor"/></xsl:attribute>
            Index
        </fo:block>

        <fo:block font-size="11pt" space-after="10mm">
            <xsl:attribute name="color"><xsl:value-of select="$color-secondary"/></xsl:attribute>
            Alphabetical listing of all elements, types, and attributes.
        </fo:block>

        <xsl:if test="xsd-documentation/index/entry">
            <fo:table table-layout="fixed" width="100%" border="0.5pt solid">
                <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                <fo:table-column column-width="40%"/>
                <fo:table-column column-width="30%"/>
                <fo:table-column column-width="30%"/>
                <fo:table-header>
                    <fo:table-row>
                        <xsl:attribute name="background-color"><xsl:value-of select="$tableHeaderBg"/></xsl:attribute>
                        <fo:table-cell padding="4mm" border="0.5pt solid">
                            <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                            <fo:block font-weight="bold">Name</fo:block>
                        </fo:table-cell>
                        <fo:table-cell padding="4mm" border="0.5pt solid">
                            <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                            <fo:block font-weight="bold">Type</fo:block>
                        </fo:table-cell>
                        <fo:table-cell padding="4mm" border="0.5pt solid">
                            <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                            <fo:block font-weight="bold">Location</fo:block>
                        </fo:table-cell>
                    </fo:table-row>
                </fo:table-header>
                <fo:table-body>
                    <xsl:for-each select="xsd-documentation/index/entry">
                        <fo:table-row>
                            <xsl:call-template name="apply-table-row-style"/>
                            <fo:table-cell padding="3mm" border="0.5pt solid">
                                <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                                <fo:block font-size="10pt"><xsl:value-of select="name"/></fo:block>
                            </fo:table-cell>
                            <fo:table-cell padding="3mm" border="0.5pt solid">
                                <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                                <fo:block font-size="10pt"><xsl:value-of select="entry-type"/></fo:block>
                            </fo:table-cell>
                            <fo:table-cell padding="3mm" border="0.5pt solid">
                                <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                                <fo:block font-size="10pt"><xsl:value-of select="location"/></fo:block>
                            </fo:table-cell>
                        </fo:table-row>
                    </xsl:for-each>
                </fo:table-body>
            </fo:table>
        </xsl:if>
    </xsl:template>

    <!-- ============================================== -->
    <!-- TABLE ROW STYLE HELPER                        -->
    <!-- ============================================== -->
    <xsl:template name="apply-table-row-style">
        <xsl:choose>
            <xsl:when test="$tableStyle = 'ZEBRA_STRIPES'">
                <xsl:if test="position() mod 2 = 0">
                    <xsl:attribute name="background-color"><xsl:value-of select="$zebra-stripe-bg"/></xsl:attribute>
                </xsl:if>
            </xsl:when>
            <xsl:when test="$tableStyle = 'MINIMAL'">
                <!-- No background color for minimal style -->
            </xsl:when>
            <!-- BORDERED is default, no special row coloring -->
        </xsl:choose>
    </xsl:template>

</xsl:stylesheet>
