<?xml version="1.0" encoding="UTF-8"?>
<!--
  fund-summary.xsl — summarises a FundsXML4 document. Used by the regression-check example
  (regression.fxtproj): its result is compared with expected/equity-summary.xml.

  The result carries values that change from run to run (when it was created, a run id): the
  expected file holds XMLUnit placeholders for them, so the check passes on every run.
-->
<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                exclude-result-prefixes="xs">

    <xsl:output method="xml" indent="yes"/>

    <xsl:template match="/FundsXML4">
        <fund-summary created="{current-dateTime()}" run="{generate-id()}-{format-dateTime(current-dateTime(), '[s][f]')}">
            <xsl:apply-templates select="Funds/Fund"/>
        </fund-summary>
    </xsl:template>

    <xsl:template match="Fund">
        <xsl:variable name="positions" select=".//Portfolios/Portfolio[1]/Positions/Position"/>
        <fund isin="{Identifiers/ISIN}" currency="{Currency}">
            <name><xsl:value-of select="Names/OfficialName"/></name>
            <nav><xsl:value-of select="FundDynamicData/TotalAssetValues/TotalAssetValue[1]/TotalNetAssetValue/Amount[1]"/></nav>
            <positions count="{count($positions)}">
                <weights-total><xsl:value-of select="sum($positions/TotalPercentage)"/></weights-total>
                <xsl:for-each select="$positions[xs:decimal(TotalPercentage) ge 10]">
                    <xsl:sort select="xs:decimal(TotalPercentage)" order="descending"/>
                    <core isin="{Identifiers/ISIN}" weight="{TotalPercentage}"/>
                </xsl:for-each>
            </positions>
        </fund>
    </xsl:template>

</xsl:stylesheet>
