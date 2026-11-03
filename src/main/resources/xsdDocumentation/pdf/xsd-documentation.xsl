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
    <xsl:param name="includeElementReference">true</xsl:param>

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

            <!-- Content pages: one page-sequence per chapter, and the long chapters in blocks.
                 FOP breaks pages per page-sequence and keeps a whole sequence in memory, so one
                 sequence for thousands of pages runs out of memory on large schemas. -->
            <xsl:variable name="doc" select="xsd-documentation"/>

            <xsl:if test="$includeToc = 'true'">
                <fo:page-sequence master-reference="content-page">
                    <xsl:call-template name="page-statics"/>
                    <fo:flow flow-name="xsl-region-body">
                        <xsl:call-template name="flow-start"/>
                        <xsl:call-template name="table-of-contents"/>
                    </fo:flow>
                </fo:page-sequence>
            </xsl:if>

            <xsl:if test="$includeSchemaOverview = 'true'">
                <fo:page-sequence master-reference="content-page">
                    <xsl:call-template name="page-statics"/>
                    <fo:flow flow-name="xsl-region-body">
                        <xsl:call-template name="flow-start"/>
                        <xsl:call-template name="schema-overview"/>
                    </fo:flow>
                </fo:page-sequence>
            </xsl:if>

            <xsl:if test="$doc/namespace-overview">
                <fo:page-sequence master-reference="content-page">
                    <xsl:call-template name="page-statics"/>
                    <fo:flow flow-name="xsl-region-body">
                        <xsl:call-template name="flow-start"/>
                        <xsl:call-template name="namespace-overview"/>
                    </fo:flow>
                </fo:page-sequence>
            </xsl:if>

            <xsl:if test="$includeSchemaDiagram = 'true' and $doc/schema-diagram/image-path">
                <fo:page-sequence master-reference="content-page">
                    <xsl:call-template name="page-statics"/>
                    <fo:flow flow-name="xsl-region-body">
                        <xsl:call-template name="flow-start"/>
                        <xsl:call-template name="schema-diagram-section"/>
                    </fo:flow>
                </fo:page-sequence>
            </xsl:if>

            <xsl:if test="$includeComplexTypes = 'true' and $doc/complex-types/complex-type">
                <fo:page-sequence master-reference="content-page">
                    <xsl:call-template name="page-statics"/>
                    <fo:flow flow-name="xsl-region-body">
                        <xsl:call-template name="flow-start"/>
                        <xsl:call-template name="complex-types-section"/>
                    </fo:flow>
                </fo:page-sequence>
                <xsl:call-template name="section-sequences">
                    <xsl:with-param name="sections" select="$doc/complex-types/complex-type/section"/>
                </xsl:call-template>
            </xsl:if>

            <xsl:if test="$includeSimpleTypes = 'true' and $doc/simple-types/simple-type">
                <fo:page-sequence master-reference="content-page">
                    <xsl:call-template name="page-statics"/>
                    <fo:flow flow-name="xsl-region-body">
                        <xsl:call-template name="flow-start"/>
                        <xsl:call-template name="simple-types-section"/>
                    </fo:flow>
                </fo:page-sequence>
                <xsl:call-template name="section-sequences">
                    <xsl:with-param name="sections" select="$doc/simple-types/simple-type/section"/>
                </xsl:call-template>
            </xsl:if>

            <xsl:if test="$includeDataDictionary = 'true'">
                <xsl:choose>
                    <xsl:when test="$doc/data-dictionary/element">
                        <xsl:for-each select="$doc/data-dictionary/element[position() mod $dictionaryChunk = 1]">
                            <fo:page-sequence master-reference="content-page">
                                <xsl:call-template name="page-statics"/>
                                <fo:flow flow-name="xsl-region-body">
                                    <xsl:call-template name="flow-start"/>
                                    <xsl:call-template name="data-dictionary-section">
                                        <xsl:with-param name="first" select="position() = 1"/>
                                        <xsl:with-param name="rows" select=". | following-sibling::element[position() &lt; $dictionaryChunk]"/>
                                    </xsl:call-template>
                                </fo:flow>
                            </fo:page-sequence>
                        </xsl:for-each>
                    </xsl:when>
                    <xsl:otherwise>
                        <fo:page-sequence master-reference="content-page">
                            <xsl:call-template name="page-statics"/>
                            <fo:flow flow-name="xsl-region-body">
                                <xsl:call-template name="flow-start"/>
                                <xsl:call-template name="data-dictionary-section">
                                    <xsl:with-param name="first" select="true()"/>
                                    <xsl:with-param name="rows" select="/.."/>
                                </xsl:call-template>
                            </fo:flow>
                        </fo:page-sequence>
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:if>

            <xsl:if test="$includeElementReference = 'true' and $doc/element-reference/section">
                <xsl:for-each select="$doc/element-reference/section[position() mod $sectionChunk = 1]">
                    <fo:page-sequence master-reference="content-page">
                        <xsl:call-template name="page-statics"/>
                        <fo:flow flow-name="xsl-region-body">
                            <xsl:call-template name="flow-start"/>
                            <xsl:if test="position() = 1">
                                <xsl:call-template name="element-reference-heading"/>
                            </xsl:if>
                            <xsl:for-each select=". | following-sibling::section[position() &lt; $sectionChunk]">
                                <xsl:call-template name="reference-entry"/>
                            </xsl:for-each>
                        </fo:flow>
                    </fo:page-sequence>
                </xsl:for-each>
            </xsl:if>

            <!-- Index, last: it carries the last-page marker for "Page x of y" -->
            <xsl:choose>
                <xsl:when test="$doc/index/entry">
                    <xsl:for-each select="$doc/index/entry[position() mod $indexChunk = 1]">
                        <fo:page-sequence master-reference="content-page">
                            <xsl:call-template name="page-statics"/>
                            <fo:flow flow-name="xsl-region-body">
                                <xsl:call-template name="flow-start"/>
                                <xsl:call-template name="index-section">
                                    <xsl:with-param name="first" select="position() = 1"/>
                                    <xsl:with-param name="entries" select=". | following-sibling::entry[position() &lt; $indexChunk]"/>
                                </xsl:call-template>
                                <xsl:if test="position() = last()">
                                    <fo:block id="last-page"/>
                                </xsl:if>
                            </fo:flow>
                        </fo:page-sequence>
                    </xsl:for-each>
                </xsl:when>
                <xsl:otherwise>
                    <fo:page-sequence master-reference="content-page">
                        <xsl:call-template name="page-statics"/>
                        <fo:flow flow-name="xsl-region-body">
                            <fo:block id="last-page"/>
                        </fo:flow>
                    </fo:page-sequence>
                </xsl:otherwise>
            </xsl:choose>
        </fo:root>
    </xsl:template>

    <!-- Items per page-sequence of the long chapters (see the root template) -->
    <xsl:variable name="sectionChunk" select="40"/>
    <xsl:variable name="dictionaryChunk" select="500"/>
    <xsl:variable name="indexChunk" select="1000"/>

    <!-- Running header and footer of a content page-sequence -->
    <xsl:template name="page-statics">
        <xsl:if test="$headerStyle != 'NONE'">
            <fo:static-content flow-name="xsl-region-before">
                <xsl:call-template name="page-header"/>
            </fo:static-content>
        </xsl:if>
        <xsl:if test="$footerStyle != 'NONE'">
            <fo:static-content flow-name="xsl-region-after">
                <xsl:call-template name="page-footer"/>
            </fo:static-content>
        </xsl:if>
    </xsl:template>

    <!-- What every content flow starts with -->
    <xsl:template name="flow-start">
        <xsl:if test="$hasWatermark = 'true' and string-length($watermarkText) > 0">
            <xsl:call-template name="watermark"/>
        </xsl:if>
    </xsl:template>

    <!-- Type or element sections in blocks of $sectionChunk, one page-sequence each -->
    <xsl:template name="section-sequences">
        <xsl:param name="sections"/>
        <xsl:for-each select="$sections[position() mod $sectionChunk = 1]">
            <xsl:variable name="from" select="(position() - 1) * $sectionChunk + 1"/>
            <fo:page-sequence master-reference="content-page">
                <xsl:call-template name="page-statics"/>
                <fo:flow flow-name="xsl-region-body">
                    <xsl:call-template name="flow-start"/>
                    <xsl:for-each select="$sections[position() &gt;= $from and position() &lt; $from + $sectionChunk]">
                        <xsl:call-template name="reference-entry"/>
                    </xsl:for-each>
                </fo:flow>
            </fo:page-sequence>
        </xsl:for-each>
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
                <fo:bookmark internal-destination="complex-types" starting-state="hide">
                    <fo:bookmark-title>Complex Types</fo:bookmark-title>
                    <xsl:for-each select="xsd-documentation/complex-types/complex-type/section">
                        <fo:bookmark internal-destination="{@id}">
                            <fo:bookmark-title><xsl:value-of select="@name"/></fo:bookmark-title>
                        </fo:bookmark>
                    </xsl:for-each>
                </fo:bookmark>
            </xsl:if>

            <xsl:if test="$includeSimpleTypes = 'true' and xsd-documentation/simple-types/simple-type">
                <fo:bookmark internal-destination="simple-types" starting-state="hide">
                    <fo:bookmark-title>Simple Types</fo:bookmark-title>
                    <xsl:for-each select="xsd-documentation/simple-types/simple-type/section">
                        <fo:bookmark internal-destination="{@id}">
                            <fo:bookmark-title><xsl:value-of select="@name"/></fo:bookmark-title>
                        </fo:bookmark>
                    </xsl:for-each>
                </fo:bookmark>
            </xsl:if>

            <xsl:if test="$includeDataDictionary = 'true'">
                <fo:bookmark internal-destination="data-dictionary">
                    <fo:bookmark-title>Data Dictionary</fo:bookmark-title>
                </fo:bookmark>
            </xsl:if>

            <xsl:if test="$includeElementReference = 'true' and xsd-documentation/element-reference/section">
                <fo:bookmark internal-destination="element-reference" starting-state="hide">
                    <fo:bookmark-title>Element Reference</fo:bookmark-title>
                    <xsl:for-each select="xsd-documentation/element-reference/section">
                        <fo:bookmark internal-destination="{@id}">
                            <fo:bookmark-title><xsl:value-of select="@name"/></fo:bookmark-title>
                        </fo:bookmark>
                    </xsl:for-each>
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

        <xsl:if test="$includeElementReference = 'true' and xsd-documentation/element-reference/section">
            <fo:block font-size="{$toc-font-size}" space-after="5mm">
                <fo:basic-link internal-destination="element-reference">
                    Element Reference
                    <fo:leader leader-pattern="dots"/>
                    <fo:page-number-citation ref-id="element-reference"/>
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

        <xsl:if test="xsd-documentation/namespace-overview/schema-reference">
            <fo:block font-size="14pt" font-weight="bold" space-before="10mm" space-after="4mm">
                <xsl:attribute name="color"><xsl:value-of select="$primaryColor"/></xsl:attribute>
                Imports and Includes
            </fo:block>
            <fo:table table-layout="fixed" width="100%" border="0.5pt solid">
                <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                <fo:table-column column-width="15%"/>
                <fo:table-column column-width="40%"/>
                <fo:table-column column-width="45%"/>
                <fo:table-header>
                    <fo:table-row>
                        <xsl:attribute name="background-color"><xsl:value-of select="$tableHeaderBg"/></xsl:attribute>
                        <fo:table-cell padding="3mm"><fo:block font-weight="bold">Kind</fo:block></fo:table-cell>
                        <fo:table-cell padding="3mm"><fo:block font-weight="bold">Namespace</fo:block></fo:table-cell>
                        <fo:table-cell padding="3mm"><fo:block font-weight="bold">Schema location</fo:block></fo:table-cell>
                    </fo:table-row>
                </fo:table-header>
                <fo:table-body>
                    <xsl:for-each select="xsd-documentation/namespace-overview/schema-reference">
                        <fo:table-row>
                            <xsl:call-template name="apply-table-row-style"/>
                            <fo:table-cell padding="2mm"><fo:block font-size="9pt"><xsl:value-of select="kind"/></fo:block></fo:table-cell>
                            <fo:table-cell padding="2mm"><fo:block font-size="9pt" wrap-option="wrap"><xsl:value-of select="namespace"/></fo:block></fo:table-cell>
                            <fo:table-cell padding="2mm"><fo:block font-size="9pt" wrap-option="wrap"><xsl:value-of select="location"/></fo:block></fo:table-cell>
                        </fo:table-row>
                    </xsl:for-each>
                </fo:table-body>
            </fo:table>
        </xsl:if>

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
                                <fo:block font-size="10pt">
                                    <xsl:call-template name="link-to">
                                        <xsl:with-param name="text" select="name"/>
                                        <xsl:with-param name="target" select="string(@ref)"/>
                                    </xsl:call-template>
                                </fo:block>
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
                                <fo:block font-size="10pt">
                                    <xsl:call-template name="link-to">
                                        <xsl:with-param name="text" select="name"/>
                                        <xsl:with-param name="target" select="string(@ref)"/>
                                    </xsl:call-template>
                                </fo:block>
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

            
            </xsl:if>
    </xsl:template>

    <!-- ============================================== -->
    <!-- DATA DICTIONARY SECTION                       -->
    <!-- ============================================== -->
    <xsl:template name="data-dictionary-section">
        <xsl:param name="first" select="true()"/>
        <xsl:param name="rows"/>
        <xsl:if test="$first">
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
        </xsl:if>

        <xsl:if test="$rows">
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
                    <xsl:for-each select="$rows">
                        <fo:table-row>
                            <xsl:call-template name="apply-table-row-style"/>
                            <fo:table-cell padding="2mm" border="0.5pt solid">
                                <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                                <fo:block font-size="8pt" wrap-option="wrap">
                                    <xsl:call-template name="reference-link">
                                        <xsl:with-param name="text" select="path"/>
                                    </xsl:call-template>
                                </fo:block>
                            </fo:table-cell>
                            <fo:table-cell padding="2mm" border="0.5pt solid">
                                <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                                <fo:block font-size="8pt">
                                    <xsl:call-template name="link-to">
                                        <xsl:with-param name="text" select="type"/>
                                        <xsl:with-param name="target" select="string(type/@ref)"/>
                                    </xsl:call-template>
                                </fo:block>
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
                <fo:external-graphic src="{xsd-documentation/schema-diagram/image-path}">
                    <xsl:for-each select="xsd-documentation/schema-diagram/image-path">
                        <xsl:call-template name="image-size"/>
                    </xsl:for-each>
                </fo:external-graphic>
            </fo:block>

            <!-- Caption -->
            <fo:block font-size="10pt" font-style="italic" text-align="center" space-before="3mm" space-after="8mm">
                <xsl:attribute name="color"><xsl:value-of select="$color-secondary"/></xsl:attribute>
                Figure: Complete Schema Structure
            </fo:block>

            <!-- The whole schema at a glance (vector, can be zoomed in the PDF viewer) -->
            <xsl:if test="xsd-documentation/overview-diagram/image-path">
                <fo:block break-before="page" font-size="14pt" font-weight="bold" space-after="4mm">
                    <xsl:attribute name="color"><xsl:value-of select="$primaryColor"/></xsl:attribute>
                    Overview of the Whole Schema
                </fo:block>
                <fo:block text-align="center">
                    <fo:external-graphic src="{xsd-documentation/overview-diagram/image-path}">
                        <xsl:for-each select="xsd-documentation/overview-diagram/image-path">
                            <xsl:call-template name="image-size"/>
                        </xsl:for-each>
                    </fo:external-graphic>
                </fo:block>
            </xsl:if>
            <xsl:if test="xsd-documentation/overview-diagram/@skipped">
                <fo:block font-size="9pt" font-style="italic" space-before="4mm">
                    <xsl:attribute name="color"><xsl:value-of select="$color-secondary"/></xsl:attribute>
                    <xsl:text>No overview of the whole schema: with </xsl:text>
                    <xsl:value-of select="xsd-documentation/overview-diagram/@skipped"/>
                    <xsl:text> elements it would not be legible on a page. The Element Reference shows every element with its diagram.</xsl:text>
                </fo:block>
            </xsl:if>

            </xsl:if>
    </xsl:template>

    <!-- Size of an external graphic from @width-mm / @height-mm of the image-path context node:
         FOP does not bound an image by max-height, so the size is computed beforehand -->
    <xsl:template name="image-size">
        <xsl:choose>
            <xsl:when test="@width-mm and @height-mm">
                <xsl:attribute name="content-width"><xsl:value-of select="@width-mm"/>mm</xsl:attribute>
                <xsl:attribute name="content-height"><xsl:value-of select="@height-mm"/>mm</xsl:attribute>
            </xsl:when>
            <xsl:otherwise>
                <xsl:attribute name="content-width">scale-down-to-fit</xsl:attribute>
                <xsl:attribute name="width">100%</xsl:attribute>
                <xsl:attribute name="scaling">uniform</xsl:attribute>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <!-- ============================================== -->
    <!-- LINK TO THE ELEMENT REFERENCE                 -->
    <!-- ============================================== -->
    <!-- The text, as a link to the section $target (a reference or type section id), if any -->
    <xsl:template name="link-to">
        <xsl:param name="text"/>
        <xsl:param name="target"/>
        <xsl:choose>
            <xsl:when test="string-length($target) > 0">
                <fo:basic-link internal-destination="{$target}">
                    <xsl:attribute name="color"><xsl:value-of select="$primaryColor"/></xsl:attribute>
                    <xsl:value-of select="$text"/>
                </fo:basic-link>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="$text"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <!-- The text, as a link to the element reference section in @ref of the context node, if any -->
    <xsl:template name="reference-link">
        <xsl:param name="text"/>
        <xsl:choose>
            <xsl:when test="@ref">
                <fo:basic-link internal-destination="{@ref}">
                    <xsl:attribute name="color"><xsl:value-of select="$primaryColor"/></xsl:attribute>
                    <xsl:value-of select="$text"/>
                </fo:basic-link>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="$text"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <!-- ============================================== -->
    <!-- ELEMENT REFERENCE SECTION                     -->
    <!-- ============================================== -->
    <xsl:template name="element-reference-heading">
        <fo:block id="element-reference" font-size="20pt" space-before="5mm" space-after="10mm">
            <xsl:attribute name="font-weight"><xsl:value-of select="$headingBold"/></xsl:attribute>
            <xsl:attribute name="text-decoration"><xsl:value-of select="$headingUnderlined"/></xsl:attribute>
            <xsl:attribute name="color"><xsl:value-of select="$primaryColor"/></xsl:attribute>
            Element Reference
        </fo:block>

        <fo:block font-size="11pt" space-after="8mm">
            <xsl:attribute name="color"><xsl:value-of select="$color-secondary"/></xsl:attribute>
            Every element and attribute with its diagram, documentation, restrictions, children and constraints.
        </fo:block>
    </xsl:template>

    <xsl:template name="reference-subheading">
        <xsl:param name="title"/>
        <fo:block font-size="10pt" font-weight="bold" space-before="4mm" space-after="1.5mm" keep-with-next="always">
            <xsl:attribute name="color"><xsl:value-of select="$primaryColor"/></xsl:attribute>
            <xsl:value-of select="$title"/>
        </fo:block>
    </xsl:template>

    <!-- A two-column label/value table -->
    <xsl:template name="reference-row">
        <xsl:param name="label"/>
        <xsl:param name="value"/>
        <xsl:param name="mono" select="'false'"/>
        <xsl:param name="ref" select="''"/>
        <fo:table-row>
            <fo:table-cell padding="1.2mm" border="0.5pt solid">
                <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                <xsl:attribute name="background-color"><xsl:value-of select="$lightBackground"/></xsl:attribute>
                <fo:block font-size="8pt" font-weight="bold"><xsl:value-of select="$label"/></fo:block>
            </fo:table-cell>
            <fo:table-cell padding="1.2mm" border="0.5pt solid">
                <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                <fo:block font-size="8pt" wrap-option="wrap">
                    <xsl:if test="$mono = 'true'">
                        <xsl:attribute name="font-family">Courier</xsl:attribute>
                    </xsl:if>
                    <xsl:call-template name="link-to">
                        <xsl:with-param name="text" select="$value"/>
                        <xsl:with-param name="target" select="$ref"/>
                    </xsl:call-template>
                </fo:block>
            </fo:table-cell>
        </fo:table-row>
    </xsl:template>

    <xsl:template name="reference-entry">
        <fo:block space-before="8mm" padding-top="3mm" border-top="0.5pt solid">
            <xsl:attribute name="border-top-color"><xsl:value-of select="$color-border"/></xsl:attribute>

            <!-- Name and kind -->
            <fo:block id="{@id}" font-size="14pt" font-weight="bold" keep-with-next="always">
                <xsl:attribute name="color"><xsl:value-of select="$primaryColor"/></xsl:attribute>
                <xsl:value-of select="@name"/>
                <fo:inline font-size="9pt" font-weight="normal">
                    <xsl:attribute name="color"><xsl:value-of select="$color-secondary"/></xsl:attribute>
                    <xsl:text>  </xsl:text><xsl:value-of select="@kind"/>
                </fo:inline>
            </fo:block>
            <fo:block font-size="8pt" space-after="2mm" keep-with-next="always" wrap-option="wrap">
                <xsl:attribute name="color"><xsl:value-of select="$color-secondary"/></xsl:attribute>
                <xsl:value-of select="path"/>
            </fo:block>

            <!-- Other paths of the same declaration -->
            <xsl:if test="occurrences">
                <fo:block font-size="8pt" space-after="2mm">
                    <xsl:attribute name="color"><xsl:value-of select="$color-secondary"/></xsl:attribute>
                    <xsl:text>Used at </xsl:text><xsl:value-of select="occurrences/@count"/><xsl:text> paths. Also at:</xsl:text>
                    <xsl:for-each select="occurrences/path">
                        <fo:block margin-left="4mm" wrap-option="wrap"><xsl:value-of select="."/></fo:block>
                    </xsl:for-each>
                    <xsl:if test="occurrences/@count - 1 > count(occurrences/path)">
                        <fo:block margin-left="4mm">
                            <xsl:text>… and </xsl:text>
                            <xsl:value-of select="occurrences/@count - 1 - count(occurrences/path)"/>
                            <xsl:text> more</xsl:text>
                        </fo:block>
                    </xsl:if>
                </fo:block>
            </xsl:if>

            <!-- Properties -->
            <xsl:if test="properties/property">
                <fo:table table-layout="fixed" width="100%" space-after="2mm">
                    <fo:table-column column-width="25%"/>
                    <fo:table-column column-width="75%"/>
                    <fo:table-body>
                        <xsl:for-each select="properties/property">
                            <xsl:call-template name="reference-row">
                                <xsl:with-param name="label" select="@name"/>
                                <xsl:with-param name="value" select="."/>
                                <xsl:with-param name="ref" select="string(@ref)"/>
                            </xsl:call-template>
                        </xsl:for-each>
                    </fo:table-body>
                </fo:table>
            </xsl:if>

            <!-- Diagram -->
            <xsl:if test="image-path">
                <fo:block text-align="center" space-before="3mm" space-after="3mm">
                    <fo:external-graphic src="{image-path}">
                        <xsl:call-template name="image-size"/>
                    </fo:external-graphic>
                </fo:block>
            </xsl:if>

            <!-- Documentation, every selected language -->
            <xsl:if test="documentation">
                <xsl:call-template name="reference-subheading">
                    <xsl:with-param name="title" select="'Documentation'"/>
                </xsl:call-template>
                <xsl:for-each select="documentation">
                    <fo:block font-size="9pt" space-after="1.5mm" linefeed-treatment="preserve" wrap-option="wrap">
                        <xsl:if test="string-length(@lang) > 0">
                            <fo:inline font-weight="bold">
                                <xsl:text>[</xsl:text><xsl:value-of select="@lang"/><xsl:text>] </xsl:text>
                            </fo:inline>
                        </xsl:if>
                        <xsl:value-of select="."/>
                    </fo:block>
                </xsl:for-each>
            </xsl:if>

            <!-- Restrictions -->
            <xsl:if test="restrictions">
                <xsl:call-template name="reference-subheading">
                    <xsl:with-param name="title" select="'Restrictions'"/>
                </xsl:call-template>
                <fo:table table-layout="fixed" width="100%" space-after="2mm">
                    <fo:table-column column-width="25%"/>
                    <fo:table-column column-width="75%"/>
                    <fo:table-body>
                        <xsl:if test="restrictions/@base">
                            <xsl:call-template name="reference-row">
                                <xsl:with-param name="label" select="'Base'"/>
                                <xsl:with-param name="value" select="restrictions/@base"/>
                            </xsl:call-template>
                        </xsl:if>
                        <xsl:for-each select="restrictions/facet">
                            <xsl:call-template name="reference-row">
                                <xsl:with-param name="label" select="@name"/>
                                <xsl:with-param name="value" select="."/>
                                <xsl:with-param name="mono" select="'true'"/>
                            </xsl:call-template>
                        </xsl:for-each>
                        <xsl:if test="restrictions/enumeration">
                            <fo:table-row>
                                <fo:table-cell padding="1.2mm" border="0.5pt solid">
                                    <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                                    <xsl:attribute name="background-color"><xsl:value-of select="$lightBackground"/></xsl:attribute>
                                    <fo:block font-size="8pt" font-weight="bold">
                                        <xsl:text>Values (</xsl:text>
                                        <xsl:value-of select="count(restrictions/enumeration)"/>
                                        <xsl:text>)</xsl:text>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell padding="1.2mm" border="0.5pt solid">
                                    <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                                    <xsl:choose>
                                        <xsl:when test="restrictions/enumeration/@documentation">
                                            <!-- One value per line with its documentation -->
                                            <xsl:for-each select="restrictions/enumeration">
                                                <fo:block font-size="8pt" wrap-option="wrap" space-after="0.8mm">
                                                    <fo:inline font-family="Courier"><xsl:value-of select="."/></fo:inline>
                                                    <xsl:if test="@documentation">
                                                        <xsl:text> - </xsl:text><xsl:value-of select="@documentation"/>
                                                    </xsl:if>
                                                </fo:block>
                                            </xsl:for-each>
                                        </xsl:when>
                                        <xsl:otherwise>
                                            <fo:block font-size="8pt" font-family="Courier" wrap-option="wrap">
                                                <xsl:for-each select="restrictions/enumeration">
                                                    <xsl:value-of select="."/>
                                                    <xsl:if test="position() != last()"><xsl:text>, </xsl:text></xsl:if>
                                                </xsl:for-each>
                                            </fo:block>
                                        </xsl:otherwise>
                                    </xsl:choose>
                                </fo:table-cell>
                            </fo:table-row>
                        </xsl:if>
                    </fo:table-body>
                </fo:table>
            </xsl:if>

            <!-- Children and attributes -->
            <xsl:if test="children/child">
                <xsl:call-template name="reference-subheading">
                    <xsl:with-param name="title" select="'Children and Attributes'"/>
                </xsl:call-template>
                <fo:table table-layout="fixed" width="100%" space-after="2mm" border="0.5pt solid">
                    <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                    <fo:table-column column-width="25%"/>
                    <fo:table-column column-width="22%"/>
                    <fo:table-column column-width="10%"/>
                    <fo:table-column column-width="43%"/>
                    <fo:table-header>
                        <fo:table-row>
                            <xsl:attribute name="background-color"><xsl:value-of select="$tableHeaderBg"/></xsl:attribute>
                            <fo:table-cell padding="1.2mm"><fo:block font-size="8pt" font-weight="bold">Name</fo:block></fo:table-cell>
                            <fo:table-cell padding="1.2mm"><fo:block font-size="8pt" font-weight="bold">Type</fo:block></fo:table-cell>
                            <fo:table-cell padding="1.2mm"><fo:block font-size="8pt" font-weight="bold">Card.</fo:block></fo:table-cell>
                            <fo:table-cell padding="1.2mm"><fo:block font-size="8pt" font-weight="bold">Description</fo:block></fo:table-cell>
                        </fo:table-row>
                    </fo:table-header>
                    <fo:table-body>
                        <xsl:for-each select="children/child">
                            <fo:table-row>
                                <xsl:call-template name="apply-table-row-style"/>
                                <fo:table-cell padding="1.2mm">
                                    <fo:block font-size="8pt" wrap-option="wrap">
                                        <xsl:call-template name="reference-link">
                                            <xsl:with-param name="text" select="@name"/>
                                        </xsl:call-template>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell padding="1.2mm">
                                    <fo:block font-size="8pt" wrap-option="wrap">
                                        <xsl:call-template name="link-to">
                                            <xsl:with-param name="text" select="type"/>
                                            <xsl:with-param name="target" select="string(type/@ref)"/>
                                        </xsl:call-template>
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell padding="1.2mm"><fo:block font-size="8pt"><xsl:value-of select="cardinality"/></fo:block></fo:table-cell>
                                <fo:table-cell padding="1.2mm"><fo:block font-size="8pt" wrap-option="wrap"><xsl:value-of select="description"/></fo:block></fo:table-cell>
                            </fo:table-row>
                        </xsl:for-each>
                    </fo:table-body>
                </fo:table>
            </xsl:if>

            <!-- Identity constraints -->
            <xsl:if test="constraint">
                <xsl:call-template name="reference-subheading">
                    <xsl:with-param name="title" select="'Identity Constraints'"/>
                </xsl:call-template>
                <xsl:for-each select="constraint">
                    <fo:table table-layout="fixed" width="100%" space-after="2mm">
                        <fo:table-column column-width="25%"/>
                        <fo:table-column column-width="75%"/>
                        <fo:table-body>
                            <xsl:call-template name="reference-row">
                                <xsl:with-param name="label" select="@kind"/>
                                <xsl:with-param name="value" select="@name"/>
                            </xsl:call-template>
                            <xsl:call-template name="reference-row">
                                <xsl:with-param name="label" select="'Selector'"/>
                                <xsl:with-param name="value" select="selector"/>
                                <xsl:with-param name="mono" select="'true'"/>
                            </xsl:call-template>
                            <xsl:for-each select="field">
                                <xsl:call-template name="reference-row">
                                    <xsl:with-param name="label" select="'Field'"/>
                                    <xsl:with-param name="value" select="."/>
                                    <xsl:with-param name="mono" select="'true'"/>
                                </xsl:call-template>
                            </xsl:for-each>
                            <xsl:if test="refer">
                                <xsl:call-template name="reference-row">
                                    <xsl:with-param name="label" select="'Refers to'"/>
                                    <xsl:with-param name="value" select="refer"/>
                                </xsl:call-template>
                            </xsl:if>
                            <xsl:if test="documentation">
                                <xsl:call-template name="reference-row">
                                    <xsl:with-param name="label" select="'Documentation'"/>
                                    <xsl:with-param name="value" select="documentation"/>
                                </xsl:call-template>
                            </xsl:if>
                        </fo:table-body>
                    </fo:table>
                </xsl:for-each>
            </xsl:if>

            <!-- Assertions -->
            <xsl:if test="assertion">
                <xsl:call-template name="reference-subheading">
                    <xsl:with-param name="title" select="'Assertions (XSD 1.1)'"/>
                </xsl:call-template>
                <fo:table table-layout="fixed" width="100%" space-after="2mm">
                    <fo:table-column column-width="25%"/>
                    <fo:table-column column-width="75%"/>
                    <fo:table-body>
                        <xsl:for-each select="assertion">
                            <xsl:call-template name="reference-row">
                                <xsl:with-param name="label" select="@kind"/>
                                <xsl:with-param name="value" select="test"/>
                                <xsl:with-param name="mono" select="'true'"/>
                            </xsl:call-template>
                            <xsl:if test="documentation">
                                <xsl:call-template name="reference-row">
                                    <xsl:with-param name="label" select="'Documentation'"/>
                                    <xsl:with-param name="value" select="documentation"/>
                                </xsl:call-template>
                            </xsl:if>
                        </xsl:for-each>
                    </fo:table-body>
                </fo:table>
            </xsl:if>

            <!-- Type alternatives -->
            <xsl:if test="alternative">
                <xsl:call-template name="reference-subheading">
                    <xsl:with-param name="title" select="'Type Alternatives (XSD 1.1)'"/>
                </xsl:call-template>
                <fo:table table-layout="fixed" width="100%" space-after="2mm">
                    <fo:table-column column-width="25%"/>
                    <fo:table-column column-width="75%"/>
                    <fo:table-body>
                        <xsl:for-each select="alternative">
                            <xsl:call-template name="reference-row">
                                <xsl:with-param name="label" select="type"/>
                                <xsl:with-param name="value" select="test"/>
                                <xsl:with-param name="mono" select="'true'"/>
                            </xsl:call-template>
                        </xsl:for-each>
                    </fo:table-body>
                </fo:table>
            </xsl:if>

            <!-- Wildcards and open content -->
            <xsl:if test="wildcard or open-content">
                <xsl:call-template name="reference-subheading">
                    <xsl:with-param name="title" select="'Wildcards and Open Content'"/>
                </xsl:call-template>
                <fo:table table-layout="fixed" width="100%" space-after="2mm">
                    <fo:table-column column-width="25%"/>
                    <fo:table-column column-width="75%"/>
                    <fo:table-body>
                        <xsl:for-each select="wildcard">
                            <xsl:call-template name="reference-row">
                                <xsl:with-param name="label" select="@kind"/>
                                <xsl:with-param name="value" select="concat(namespace, ' (processContents: ', process-contents, ')')"/>
                            </xsl:call-template>
                        </xsl:for-each>
                        <xsl:for-each select="open-content">
                            <xsl:call-template name="reference-row">
                                <xsl:with-param name="label" select="'Open content'"/>
                                <xsl:with-param name="value" select="concat(mode, ', ', namespace)"/>
                            </xsl:call-template>
                        </xsl:for-each>
                    </fo:table-body>
                </fo:table>
            </xsl:if>

            <!-- Where a type is used, and which types derive from it -->
            <xsl:if test="used-in or derived-type">
                <xsl:call-template name="reference-subheading">
                    <xsl:with-param name="title" select="'Used In'"/>
                </xsl:call-template>
                <xsl:for-each select="used-in/user">
                    <fo:block font-size="8pt" margin-left="2mm" wrap-option="wrap">
                        <xsl:call-template name="reference-link">
                            <xsl:with-param name="text" select="."/>
                        </xsl:call-template>
                    </fo:block>
                </xsl:for-each>
                <xsl:if test="used-in/@count > count(used-in/user)">
                    <fo:block font-size="8pt" margin-left="2mm">
                        <xsl:attribute name="color"><xsl:value-of select="$color-secondary"/></xsl:attribute>
                        <xsl:text>… and </xsl:text>
                        <xsl:value-of select="used-in/@count - count(used-in/user)"/>
                        <xsl:text> more</xsl:text>
                    </fo:block>
                </xsl:if>
                <xsl:if test="derived-type">
                    <fo:block font-size="8pt" margin-left="2mm" space-before="1mm" wrap-option="wrap">
                        <xsl:text>Derived types: </xsl:text>
                        <xsl:for-each select="derived-type">
                            <xsl:call-template name="link-to">
                                <xsl:with-param name="text" select="."/>
                                <xsl:with-param name="target" select="string(@ref)"/>
                            </xsl:call-template>
                            <xsl:if test="position() != last()"><xsl:text>, </xsl:text></xsl:if>
                        </xsl:for-each>
                    </fo:block>
                </xsl:if>
            </xsl:if>

            <!-- XSD source -->
            <xsl:if test="source">
                <xsl:call-template name="reference-subheading">
                    <xsl:with-param name="title" select="'XSD Source'"/>
                </xsl:call-template>
                <fo:block font-size="7pt" font-family="Courier" padding="2mm" border="0.5pt solid"
                          linefeed-treatment="preserve" white-space-treatment="preserve"
                          white-space-collapse="false" wrap-option="wrap">
                    <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                    <xsl:attribute name="background-color"><xsl:value-of select="$lightBackground"/></xsl:attribute>
                    <xsl:value-of select="source"/>
                </fo:block>
            </xsl:if>

            <!-- Appinfo -->
            <xsl:if test="appinfo">
                <xsl:call-template name="reference-subheading">
                    <xsl:with-param name="title" select="'Additional Information'"/>
                </xsl:call-template>
                <xsl:for-each select="appinfo">
                    <fo:block font-size="8pt" font-family="Courier" space-after="1mm" wrap-option="wrap"
                              linefeed-treatment="preserve">
                        <xsl:value-of select="."/>
                    </fo:block>
                </xsl:for-each>
            </xsl:if>
        </fo:block>
    </xsl:template>

    <!-- ============================================== -->
    <!-- INDEX SECTION                                 -->
    <!-- ============================================== -->
    <xsl:template name="index-section">
        <xsl:param name="first" select="true()"/>
        <xsl:param name="entries"/>
        <xsl:if test="$first">
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
        </xsl:if>

        <xsl:if test="$entries">
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
                    <xsl:for-each select="$entries">
                        <fo:table-row>
                            <xsl:call-template name="apply-table-row-style"/>
                            <fo:table-cell padding="3mm" border="0.5pt solid">
                                <xsl:attribute name="border-color"><xsl:value-of select="$color-border"/></xsl:attribute>
                                <fo:block font-size="10pt">
                                    <xsl:call-template name="reference-link">
                                        <xsl:with-param name="text" select="name"/>
                                    </xsl:call-template>
                                </fo:block>
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
