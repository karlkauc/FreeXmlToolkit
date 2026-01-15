<?xml version="1.0" encoding="UTF-8"?>
<!--
  XSL-FO Template for XSD Documentation PDF Generation
  FreeXMLToolkit - Universal Toolkit for XML
  Copyright (c) Karl Kauc 2024.

  This template transforms the intermediate XML documentation
  into a professional PDF document using Apache FOP.
-->
<xsl:stylesheet version="1.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fo="http://www.w3.org/1999/XSL/Format">

    <!-- Parameters passed from Java code -->
    <xsl:param name="currentDate"/>
    <xsl:param name="schemaName"/>

    <!-- Content section toggles -->
    <xsl:param name="includeSchemaDiagram">false</xsl:param>
    <xsl:param name="includeElementDiagrams">false</xsl:param>
    <xsl:param name="generateBookmarks">true</xsl:param>

    <!-- Color definitions -->
    <xsl:variable name="color-primary">#2563EB</xsl:variable>
    <xsl:variable name="color-secondary">#64748B</xsl:variable>
    <xsl:variable name="color-header-bg">#F1F5F9</xsl:variable>
    <xsl:variable name="color-border">#CBD5E1</xsl:variable>
    <xsl:variable name="color-success">#059669</xsl:variable>
    <xsl:variable name="color-warning">#D97706</xsl:variable>

    <!-- Root template -->
    <xsl:template match="/">
        <fo:root font-family="Helvetica, Arial, sans-serif">
            <!-- Page layout definitions -->
            <fo:layout-master-set>
                <!-- Title page layout -->
                <fo:simple-page-master master-name="title-page"
                    page-width="210mm" page-height="297mm"
                    margin-top="30mm" margin-bottom="30mm"
                    margin-left="25mm" margin-right="25mm">
                    <fo:region-body/>
                </fo:simple-page-master>

                <!-- Content page layout -->
                <fo:simple-page-master master-name="content-page"
                    page-width="210mm" page-height="297mm"
                    margin-top="20mm" margin-bottom="20mm"
                    margin-left="25mm" margin-right="25mm">
                    <fo:region-body margin-top="15mm" margin-bottom="15mm"/>
                    <fo:region-before extent="15mm"/>
                    <fo:region-after extent="15mm"/>
                </fo:simple-page-master>
            </fo:layout-master-set>

            <!-- PDF Bookmarks / Outline -->
            <xsl:if test="$generateBookmarks = 'true'">
                <fo:bookmark-tree>
                    <fo:bookmark internal-destination="schema-overview">
                        <fo:bookmark-title>Schema Overview</fo:bookmark-title>
                    </fo:bookmark>

                    <xsl:if test="$includeSchemaDiagram = 'true'">
                        <xsl:if test="xsd-documentation/schema-diagram/image-path">
                            <fo:bookmark internal-destination="schema-diagram">
                                <fo:bookmark-title>Schema Diagram</fo:bookmark-title>
                            </fo:bookmark>
                        </xsl:if>
                    </xsl:if>

                    <xsl:if test="xsd-documentation/complex-types/complex-type">
                        <fo:bookmark internal-destination="complex-types">
                            <fo:bookmark-title>Complex Types</fo:bookmark-title>
                        </fo:bookmark>
                    </xsl:if>

                    <xsl:if test="xsd-documentation/simple-types/simple-type">
                        <fo:bookmark internal-destination="simple-types">
                            <fo:bookmark-title>Simple Types</fo:bookmark-title>
                        </fo:bookmark>
                    </xsl:if>

                    <fo:bookmark internal-destination="data-dictionary">
                        <fo:bookmark-title>Data Dictionary</fo:bookmark-title>
                    </fo:bookmark>

                    <xsl:if test="$includeElementDiagrams = 'true'">
                        <xsl:if test="xsd-documentation/element-diagrams/diagram">
                            <fo:bookmark internal-destination="element-diagrams">
                                <fo:bookmark-title>Element Diagrams</fo:bookmark-title>
                            </fo:bookmark>
                        </xsl:if>
                    </xsl:if>
                </fo:bookmark-tree>
            </xsl:if>

            <!-- Title Page -->
            <fo:page-sequence master-reference="title-page">
                <fo:flow flow-name="xsl-region-body">
                    <xsl:call-template name="title-page-content"/>
                </fo:flow>
            </fo:page-sequence>

            <!-- Content Pages -->
            <fo:page-sequence master-reference="content-page">
                <!-- Running header -->
                <fo:static-content flow-name="xsl-region-before">
                    <fo:block font-size="9pt" color="{$color-secondary}"
                        border-bottom="0.5pt solid {$color-border}" padding-bottom="3mm">
                        XSD Documentation: <xsl:value-of select="$schemaName"/>
                    </fo:block>
                </fo:static-content>

                <!-- Running footer -->
                <fo:static-content flow-name="xsl-region-after">
                    <fo:block font-size="9pt" color="{$color-secondary}" text-align="center"
                        border-top="0.5pt solid {$color-border}" padding-top="3mm">
                        Page <fo:page-number/> of <fo:page-number-citation ref-id="last-page"/>
                        <fo:inline padding-left="20mm">Generated by FreeXmlToolkit on <xsl:value-of select="$currentDate"/></fo:inline>
                    </fo:block>
                </fo:static-content>

                <!-- Main content -->
                <fo:flow flow-name="xsl-region-body">
                    <xsl:call-template name="table-of-contents"/>
                    <xsl:call-template name="schema-overview"/>
                    <xsl:call-template name="schema-diagram-section"/>
                    <xsl:call-template name="complex-types-section"/>
                    <xsl:call-template name="simple-types-section"/>
                    <xsl:call-template name="data-dictionary-section"/>
                    <xsl:call-template name="element-diagrams-section"/>

                    <!-- Last page marker for page count -->
                    <fo:block id="last-page"/>
                </fo:flow>
            </fo:page-sequence>
        </fo:root>
    </xsl:template>

    <!-- Title Page Content -->
    <xsl:template name="title-page-content">
        <fo:block text-align="center" margin-top="80mm">
            <fo:block font-size="32pt" font-weight="bold" color="{$color-primary}" space-after="15mm">
                XSD Schema Documentation
            </fo:block>

            <fo:block font-size="20pt" color="{$color-secondary}" space-after="30mm">
                <xsl:value-of select="xsd-documentation/metadata/schema-name"/>
            </fo:block>

            <fo:block font-size="12pt" color="{$color-secondary}" space-after="5mm">
                <fo:inline font-weight="bold">Target Namespace:</fo:inline>
                <xsl:text> </xsl:text>
                <xsl:value-of select="xsd-documentation/metadata/target-namespace"/>
            </fo:block>

            <fo:block font-size="12pt" font-style="italic" color="{$color-secondary}" space-after="5mm">
                Generated: <xsl:value-of select="xsd-documentation/metadata/generation-date"/>
            </fo:block>

            <fo:block font-size="11pt" color="{$color-secondary}" margin-top="50mm">
                Generated by <fo:inline font-weight="bold">FreeXmlToolkit</fo:inline>
            </fo:block>
        </fo:block>
    </xsl:template>

    <!-- Table of Contents -->
    <xsl:template name="table-of-contents">
        <fo:block font-size="20pt" font-weight="bold" color="{$color-primary}"
            space-before="5mm" space-after="10mm">
            Table of Contents
        </fo:block>

        <fo:block font-size="12pt" space-after="5mm">
            <fo:basic-link internal-destination="schema-overview">
                1. Schema Overview
                <fo:leader leader-pattern="dots"/>
                <fo:page-number-citation ref-id="schema-overview"/>
            </fo:basic-link>
        </fo:block>

        <xsl:if test="$includeSchemaDiagram = 'true'">
            <xsl:if test="xsd-documentation/schema-diagram/image-path">
                <fo:block font-size="12pt" space-after="5mm">
                    <fo:basic-link internal-destination="schema-diagram">
                        2. Schema Diagram
                        <fo:leader leader-pattern="dots"/>
                        <fo:page-number-citation ref-id="schema-diagram"/>
                    </fo:basic-link>
                </fo:block>
            </xsl:if>
        </xsl:if>

        <xsl:if test="xsd-documentation/complex-types/complex-type">
            <fo:block font-size="12pt" space-after="5mm">
                <fo:basic-link internal-destination="complex-types">
                    <xsl:choose>
                        <xsl:when test="$includeSchemaDiagram = 'true' and xsd-documentation/schema-diagram/image-path">3.</xsl:when>
                        <xsl:otherwise>2.</xsl:otherwise>
                    </xsl:choose>
                    <xsl:text> Complex Types</xsl:text>
                    <fo:leader leader-pattern="dots"/>
                    <fo:page-number-citation ref-id="complex-types"/>
                </fo:basic-link>
            </fo:block>
        </xsl:if>

        <xsl:if test="xsd-documentation/simple-types/simple-type">
            <fo:block font-size="12pt" space-after="5mm">
                <fo:basic-link internal-destination="simple-types">
                    <xsl:variable name="section-number">
                        <xsl:choose>
                            <xsl:when test="$includeSchemaDiagram = 'true' and xsd-documentation/schema-diagram/image-path">
                                <xsl:choose>
                                    <xsl:when test="xsd-documentation/complex-types/complex-type">4</xsl:when>
                                    <xsl:otherwise>3</xsl:otherwise>
                                </xsl:choose>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:choose>
                                    <xsl:when test="xsd-documentation/complex-types/complex-type">3</xsl:when>
                                    <xsl:otherwise>2</xsl:otherwise>
                                </xsl:choose>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:variable>
                    <xsl:value-of select="$section-number"/>
                    <xsl:text>. Simple Types</xsl:text>
                    <fo:leader leader-pattern="dots"/>
                    <fo:page-number-citation ref-id="simple-types"/>
                </fo:basic-link>
            </fo:block>
        </xsl:if>

        <fo:block font-size="12pt" space-after="5mm">
            <fo:basic-link internal-destination="data-dictionary">
                <xsl:variable name="section-number">
                    <xsl:variable name="base">1</xsl:variable>
                    <xsl:variable name="plus-schema-diagram">
                        <xsl:choose>
                            <xsl:when test="$includeSchemaDiagram = 'true' and xsd-documentation/schema-diagram/image-path">1</xsl:when>
                            <xsl:otherwise>0</xsl:otherwise>
                        </xsl:choose>
                    </xsl:variable>
                    <xsl:variable name="plus-complex">
                        <xsl:choose>
                            <xsl:when test="xsd-documentation/complex-types/complex-type">1</xsl:when>
                            <xsl:otherwise>0</xsl:otherwise>
                        </xsl:choose>
                    </xsl:variable>
                    <xsl:variable name="plus-simple">
                        <xsl:choose>
                            <xsl:when test="xsd-documentation/simple-types/simple-type">1</xsl:when>
                            <xsl:otherwise>0</xsl:otherwise>
                        </xsl:choose>
                    </xsl:variable>
                    <xsl:value-of select="$base + $plus-schema-diagram + $plus-complex + $plus-simple + 1"/>
                </xsl:variable>
                <xsl:value-of select="$section-number"/>
                <xsl:text>. Data Dictionary</xsl:text>
                <fo:leader leader-pattern="dots"/>
                <fo:page-number-citation ref-id="data-dictionary"/>
            </fo:basic-link>
        </fo:block>

        <xsl:if test="$includeElementDiagrams = 'true'">
            <xsl:if test="xsd-documentation/element-diagrams/diagram">
                <fo:block font-size="12pt" space-after="5mm">
                    <fo:basic-link internal-destination="element-diagrams">
                        <xsl:variable name="section-number">
                            <xsl:variable name="base">1</xsl:variable>
                            <xsl:variable name="plus-schema-diagram">
                                <xsl:choose>
                                    <xsl:when test="$includeSchemaDiagram = 'true' and xsd-documentation/schema-diagram/image-path">1</xsl:when>
                                    <xsl:otherwise>0</xsl:otherwise>
                                </xsl:choose>
                            </xsl:variable>
                            <xsl:variable name="plus-complex">
                                <xsl:choose>
                                    <xsl:when test="xsd-documentation/complex-types/complex-type">1</xsl:when>
                                    <xsl:otherwise>0</xsl:otherwise>
                                </xsl:choose>
                            </xsl:variable>
                            <xsl:variable name="plus-simple">
                                <xsl:choose>
                                    <xsl:when test="xsd-documentation/simple-types/simple-type">1</xsl:when>
                                    <xsl:otherwise>0</xsl:otherwise>
                                </xsl:choose>
                            </xsl:variable>
                            <xsl:value-of select="$base + $plus-schema-diagram + $plus-complex + $plus-simple + 2"/>
                        </xsl:variable>
                        <xsl:value-of select="$section-number"/>
                        <xsl:text>. Element Diagrams</xsl:text>
                        <fo:leader leader-pattern="dots"/>
                        <fo:page-number-citation ref-id="element-diagrams"/>
                    </fo:basic-link>
                </fo:block>
            </xsl:if>
        </xsl:if>

        <fo:block break-after="page"/>
    </xsl:template>

    <!-- Schema Overview Section -->
    <xsl:template name="schema-overview">
        <fo:block id="schema-overview" font-size="20pt" font-weight="bold" color="{$color-primary}"
            space-before="5mm" space-after="10mm">
            Schema Overview
        </fo:block>

        <fo:table table-layout="fixed" width="100%" border="0.5pt solid {$color-border}">
            <fo:table-column column-width="40%"/>
            <fo:table-column column-width="60%"/>
            <fo:table-body>
                <fo:table-row>
                    <fo:table-cell padding="4mm" background-color="{$color-header-bg}" border="0.5pt solid {$color-border}">
                        <fo:block font-weight="bold">File Path</fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="4mm" border="0.5pt solid {$color-border}">
                        <fo:block><xsl:value-of select="xsd-documentation/metadata/file-path"/></fo:block>
                    </fo:table-cell>
                </fo:table-row>
                <fo:table-row>
                    <fo:table-cell padding="4mm" background-color="{$color-header-bg}" border="0.5pt solid {$color-border}">
                        <fo:block font-weight="bold">Target Namespace</fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="4mm" border="0.5pt solid {$color-border}">
                        <fo:block>
                            <xsl:choose>
                                <xsl:when test="string-length(xsd-documentation/metadata/target-namespace) > 0">
                                    <xsl:value-of select="xsd-documentation/metadata/target-namespace"/>
                                </xsl:when>
                                <xsl:otherwise>(no namespace)</xsl:otherwise>
                            </xsl:choose>
                        </fo:block>
                    </fo:table-cell>
                </fo:table-row>
                <fo:table-row>
                    <fo:table-cell padding="4mm" background-color="{$color-header-bg}" border="0.5pt solid {$color-border}">
                        <fo:block font-weight="bold">Schema Version</fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="4mm" border="0.5pt solid {$color-border}">
                        <fo:block>
                            <xsl:choose>
                                <xsl:when test="string-length(xsd-documentation/metadata/version) > 0">
                                    <xsl:value-of select="xsd-documentation/metadata/version"/>
                                </xsl:when>
                                <xsl:otherwise>(not specified)</xsl:otherwise>
                            </xsl:choose>
                        </fo:block>
                    </fo:table-cell>
                </fo:table-row>
                <fo:table-row>
                    <fo:table-cell padding="4mm" background-color="{$color-header-bg}" border="0.5pt solid {$color-border}">
                        <fo:block font-weight="bold">Element Form Default</fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="4mm" border="0.5pt solid {$color-border}">
                        <fo:block><xsl:value-of select="xsd-documentation/metadata/element-form-default"/></fo:block>
                    </fo:table-cell>
                </fo:table-row>
                <fo:table-row>
                    <fo:table-cell padding="4mm" background-color="{$color-header-bg}" border="0.5pt solid {$color-border}">
                        <fo:block font-weight="bold">Attribute Form Default</fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="4mm" border="0.5pt solid {$color-border}">
                        <fo:block><xsl:value-of select="xsd-documentation/metadata/attribute-form-default"/></fo:block>
                    </fo:table-cell>
                </fo:table-row>
            </fo:table-body>
        </fo:table>

        <!-- Statistics -->
        <fo:block font-size="16pt" font-weight="bold" color="{$color-primary}"
            space-before="15mm" space-after="8mm">
            Statistics
        </fo:block>

        <fo:table table-layout="fixed" width="100%" border="0.5pt solid {$color-border}">
            <fo:table-column column-width="50%"/>
            <fo:table-column column-width="50%"/>
            <fo:table-body>
                <fo:table-row>
                    <fo:table-cell padding="4mm" background-color="{$color-header-bg}" border="0.5pt solid {$color-border}">
                        <fo:block font-weight="bold">Global Elements</fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="4mm" border="0.5pt solid {$color-border}">
                        <fo:block><xsl:value-of select="xsd-documentation/statistics/global-elements"/></fo:block>
                    </fo:table-cell>
                </fo:table-row>
                <fo:table-row>
                    <fo:table-cell padding="4mm" background-color="{$color-header-bg}" border="0.5pt solid {$color-border}">
                        <fo:block font-weight="bold">Global ComplexTypes</fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="4mm" border="0.5pt solid {$color-border}">
                        <fo:block><xsl:value-of select="xsd-documentation/statistics/global-complex-types"/></fo:block>
                    </fo:table-cell>
                </fo:table-row>
                <fo:table-row>
                    <fo:table-cell padding="4mm" background-color="{$color-header-bg}" border="0.5pt solid {$color-border}">
                        <fo:block font-weight="bold">Global SimpleTypes</fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="4mm" border="0.5pt solid {$color-border}">
                        <fo:block><xsl:value-of select="xsd-documentation/statistics/global-simple-types"/></fo:block>
                    </fo:table-cell>
                </fo:table-row>
                <fo:table-row>
                    <fo:table-cell padding="4mm" background-color="{$color-header-bg}" border="0.5pt solid {$color-border}">
                        <fo:block font-weight="bold">Total Elements Documented</fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="4mm" border="0.5pt solid {$color-border}">
                        <fo:block><xsl:value-of select="xsd-documentation/statistics/total-elements"/></fo:block>
                    </fo:table-cell>
                </fo:table-row>
            </fo:table-body>
        </fo:table>

        <fo:block break-after="page"/>
    </xsl:template>

    <!-- Complex Types Section -->
    <xsl:template name="complex-types-section">
        <xsl:if test="xsd-documentation/complex-types/complex-type">
            <fo:block id="complex-types" font-size="20pt" font-weight="bold" color="{$color-primary}"
                space-before="5mm" space-after="10mm">
                Complex Types
            </fo:block>

            <fo:block font-size="11pt" space-after="10mm" color="{$color-secondary}">
                This section lists all globally defined ComplexTypes in the schema.
            </fo:block>

            <fo:table table-layout="fixed" width="100%" border="0.5pt solid {$color-border}">
                <fo:table-column column-width="40%"/>
                <fo:table-column column-width="40%"/>
                <fo:table-column column-width="20%"/>
                <fo:table-header>
                    <fo:table-row background-color="{$color-header-bg}">
                        <fo:table-cell padding="4mm" border="0.5pt solid {$color-border}">
                            <fo:block font-weight="bold">Type Name</fo:block>
                        </fo:table-cell>
                        <fo:table-cell padding="4mm" border="0.5pt solid {$color-border}">
                            <fo:block font-weight="bold">Base Type</fo:block>
                        </fo:table-cell>
                        <fo:table-cell padding="4mm" border="0.5pt solid {$color-border}">
                            <fo:block font-weight="bold">Usage Count</fo:block>
                        </fo:table-cell>
                    </fo:table-row>
                </fo:table-header>
                <fo:table-body>
                    <xsl:for-each select="xsd-documentation/complex-types/complex-type">
                        <fo:table-row>
                            <xsl:if test="position() mod 2 = 0">
                                <xsl:attribute name="background-color">#FAFAFA</xsl:attribute>
                            </xsl:if>
                            <fo:table-cell padding="3mm" border="0.5pt solid {$color-border}">
                                <fo:block font-size="10pt"><xsl:value-of select="name"/></fo:block>
                            </fo:table-cell>
                            <fo:table-cell padding="3mm" border="0.5pt solid {$color-border}">
                                <fo:block font-size="10pt"><xsl:value-of select="base-type"/></fo:block>
                            </fo:table-cell>
                            <fo:table-cell padding="3mm" border="0.5pt solid {$color-border}">
                                <fo:block font-size="10pt" text-align="center"><xsl:value-of select="usage-count"/></fo:block>
                            </fo:table-cell>
                        </fo:table-row>
                    </xsl:for-each>
                </fo:table-body>
            </fo:table>

            <fo:block break-after="page"/>
        </xsl:if>
    </xsl:template>

    <!-- Simple Types Section -->
    <xsl:template name="simple-types-section">
        <xsl:if test="xsd-documentation/simple-types/simple-type">
            <fo:block id="simple-types" font-size="20pt" font-weight="bold" color="{$color-primary}"
                space-before="5mm" space-after="10mm">
                Simple Types
            </fo:block>

            <fo:block font-size="11pt" space-after="10mm" color="{$color-secondary}">
                This section lists all globally defined SimpleTypes in the schema.
            </fo:block>

            <fo:table table-layout="fixed" width="100%" border="0.5pt solid {$color-border}">
                <fo:table-column column-width="35%"/>
                <fo:table-column column-width="35%"/>
                <fo:table-column column-width="30%"/>
                <fo:table-header>
                    <fo:table-row background-color="{$color-header-bg}">
                        <fo:table-cell padding="4mm" border="0.5pt solid {$color-border}">
                            <fo:block font-weight="bold">Type Name</fo:block>
                        </fo:table-cell>
                        <fo:table-cell padding="4mm" border="0.5pt solid {$color-border}">
                            <fo:block font-weight="bold">Base Type</fo:block>
                        </fo:table-cell>
                        <fo:table-cell padding="4mm" border="0.5pt solid {$color-border}">
                            <fo:block font-weight="bold">Facets</fo:block>
                        </fo:table-cell>
                    </fo:table-row>
                </fo:table-header>
                <fo:table-body>
                    <xsl:for-each select="xsd-documentation/simple-types/simple-type">
                        <fo:table-row>
                            <xsl:if test="position() mod 2 = 0">
                                <xsl:attribute name="background-color">#FAFAFA</xsl:attribute>
                            </xsl:if>
                            <fo:table-cell padding="3mm" border="0.5pt solid {$color-border}">
                                <fo:block font-size="10pt"><xsl:value-of select="name"/></fo:block>
                            </fo:table-cell>
                            <fo:table-cell padding="3mm" border="0.5pt solid {$color-border}">
                                <fo:block font-size="10pt"><xsl:value-of select="base-type"/></fo:block>
                            </fo:table-cell>
                            <fo:table-cell padding="3mm" border="0.5pt solid {$color-border}">
                                <fo:block font-size="9pt"><xsl:value-of select="facets"/></fo:block>
                            </fo:table-cell>
                        </fo:table-row>
                    </xsl:for-each>
                </fo:table-body>
            </fo:table>

            <fo:block break-after="page"/>
        </xsl:if>
    </xsl:template>

    <!-- Data Dictionary Section -->
    <xsl:template name="data-dictionary-section">
        <fo:block id="data-dictionary" font-size="20pt" font-weight="bold" color="{$color-primary}"
            space-before="5mm" space-after="10mm">
            Data Dictionary
        </fo:block>

        <fo:block font-size="11pt" space-after="10mm" color="{$color-secondary}">
            Complete listing of all elements in the schema with their types and cardinality.
        </fo:block>

        <xsl:if test="xsd-documentation/data-dictionary/element">
            <fo:table table-layout="fixed" width="100%" border="0.5pt solid {$color-border}">
                <fo:table-column column-width="35%"/>
                <fo:table-column column-width="20%"/>
                <fo:table-column column-width="12%"/>
                <fo:table-column column-width="33%"/>
                <fo:table-header>
                    <fo:table-row background-color="{$color-header-bg}">
                        <fo:table-cell padding="3mm" border="0.5pt solid {$color-border}">
                            <fo:block font-weight="bold" font-size="9pt">Element Path</fo:block>
                        </fo:table-cell>
                        <fo:table-cell padding="3mm" border="0.5pt solid {$color-border}">
                            <fo:block font-weight="bold" font-size="9pt">Type</fo:block>
                        </fo:table-cell>
                        <fo:table-cell padding="3mm" border="0.5pt solid {$color-border}">
                            <fo:block font-weight="bold" font-size="9pt">Card.</fo:block>
                        </fo:table-cell>
                        <fo:table-cell padding="3mm" border="0.5pt solid {$color-border}">
                            <fo:block font-weight="bold" font-size="9pt">Description</fo:block>
                        </fo:table-cell>
                    </fo:table-row>
                </fo:table-header>
                <fo:table-body>
                    <xsl:for-each select="xsd-documentation/data-dictionary/element">
                        <fo:table-row>
                            <xsl:if test="position() mod 2 = 0">
                                <xsl:attribute name="background-color">#FAFAFA</xsl:attribute>
                            </xsl:if>
                            <fo:table-cell padding="2mm" border="0.5pt solid {$color-border}">
                                <fo:block font-size="8pt" wrap-option="wrap"><xsl:value-of select="path"/></fo:block>
                            </fo:table-cell>
                            <fo:table-cell padding="2mm" border="0.5pt solid {$color-border}">
                                <fo:block font-size="8pt"><xsl:value-of select="type"/></fo:block>
                            </fo:table-cell>
                            <fo:table-cell padding="2mm" border="0.5pt solid {$color-border}">
                                <fo:block font-size="8pt" text-align="center"><xsl:value-of select="cardinality"/></fo:block>
                            </fo:table-cell>
                            <fo:table-cell padding="2mm" border="0.5pt solid {$color-border}">
                                <fo:block font-size="8pt" wrap-option="wrap"><xsl:value-of select="description"/></fo:block>
                            </fo:table-cell>
                        </fo:table-row>
                    </xsl:for-each>
                </fo:table-body>
            </fo:table>
        </xsl:if>
    </xsl:template>

    <!-- Schema Diagram Section -->
    <xsl:template name="schema-diagram-section">
        <xsl:if test="$includeSchemaDiagram = 'true'">
            <xsl:if test="xsd-documentation/schema-diagram/image-path">
                <fo:block break-before="page"/>
                <fo:block id="schema-diagram" font-size="20pt" font-weight="bold" color="{$color-primary}"
                    space-before="5mm" space-after="10mm">
                    Schema Diagram
                </fo:block>

                <fo:block font-size="11pt" space-after="10mm" color="{$color-secondary}">
                    Visual representation of the complete schema structure starting from root element:
                    <fo:inline font-weight="bold">
                        <xsl:value-of select="xsd-documentation/schema-diagram/root-element"/>
                    </fo:inline>
                </fo:block>

                <!-- Diagram image -->
                <fo:block text-align="center" space-before="5mm">
                    <fo:external-graphic
                        src="{xsd-documentation/schema-diagram/image-path}"
                        content-width="scale-to-fit"
                        content-height="scale-to-fit"
                        max-width="160mm"
                        max-height="200mm"
                        scaling="uniform"/>
                </fo:block>

                <!-- Caption -->
                <fo:block font-size="10pt" font-style="italic" color="{$color-secondary}"
                    text-align="center" space-before="3mm" space-after="8mm">
                    Figure: Complete Schema Structure
                </fo:block>

                <fo:block break-after="page"/>
            </xsl:if>
        </xsl:if>
    </xsl:template>

    <!-- Element Diagrams Section -->
    <xsl:template name="element-diagrams-section">
        <xsl:if test="$includeElementDiagrams = 'true'">
            <xsl:if test="xsd-documentation/element-diagrams/diagram">
                <fo:block break-before="page"/>
                <fo:block id="element-diagrams" font-size="20pt" font-weight="bold" color="{$color-primary}"
                    space-before="5mm" space-after="10mm">
                    Element Diagrams
                </fo:block>

                <fo:block font-size="11pt" space-after="10mm" color="{$color-secondary}">
                    Detailed visual representation of individual elements and their structure.
                </fo:block>

                <xsl:for-each select="xsd-documentation/element-diagrams/diagram">
                    <fo:block space-before="8mm" space-after="4mm">
                        <!-- Element name heading -->
                        <fo:block font-size="16pt" font-weight="bold" color="{$color-primary}"
                            space-after="2mm">
                            <xsl:value-of select="element-name"/>
                        </fo:block>

                        <!-- Element info -->
                        <fo:block font-size="10pt" color="{$color-secondary}" space-after="1mm">
                            Path: <xsl:value-of select="path"/>
                        </fo:block>
                        <fo:block font-size="10pt" color="{$color-secondary}" space-after="3mm">
                            Type: <xsl:value-of select="type"/>
                        </fo:block>

                        <!-- Diagram image -->
                        <fo:block text-align="center">
                            <fo:external-graphic
                                src="{image-path}"
                                content-width="scale-to-fit"
                                content-height="scale-to-fit"
                                max-width="160mm"
                                max-height="100mm"
                                scaling="uniform"/>
                        </fo:block>

                        <!-- Caption -->
                        <fo:block font-size="9pt" font-style="italic" color="{$color-secondary}"
                            text-align="center" space-before="2mm">
                            Figure: Structure of <xsl:value-of select="element-name"/>
                        </fo:block>
                    </fo:block>
                </xsl:for-each>
            </xsl:if>
        </xsl:if>
    </xsl:template>

</xsl:stylesheet>
