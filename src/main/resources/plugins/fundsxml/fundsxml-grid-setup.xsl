<?xml version="1.0" encoding="UTF-8"?>
<!--
  FundsXML 4: adds grid formulas and saved filters (FreeXmlToolkit Grid view) to a document.

  Formulas  <?fxt-formula name="…" store="false" expression="…"?>  show a live XQuery result as the
            first rows of their element (counts, sums per currency, weights, largest position …).
  Filters   <?fxt-filter expression="…"?>  are saved switched off; a click on the funnel line in the
            grid shows only the matching rows (positions over 5 %, assets without ISIN …).

  Grid expressions are evaluated on their element only (not the whole document), so every
  expression here stays inside it.

  The document is otherwise copied unchanged. A formula whose name already exists in an element and
  an element that already has a saved filter are left alone, so the stylesheet can run again.
  Parameter with-filters=false adds formulas only.
-->
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:f="urn:fxt:fundsxml-grid"
                exclude-result-prefixes="xs f">

  <xsl:param name="with-filters" as="xs:boolean" select="true()"/>
  <xsl:mode on-no-match="shallow-copy"/>

  <!-- A formula instruction, unless the element has one of that name -->
  <xsl:function name="f:formula">
    <xsl:param name="context" as="element()"/>
    <xsl:param name="name" as="xs:string"/>
    <xsl:param name="expression" as="xs:string"/>
    <xsl:if test="not($context/processing-instruction('fxt-formula')[contains(., 'name=&quot;' || $name || '&quot;')])">
      <xsl:processing-instruction name="fxt-formula"
          select="'name=&quot;' || $name || '&quot; store=&quot;false&quot; expression=&quot;' || $expression || '&quot;'"/>
    </xsl:if>
  </xsl:function>

  <!-- A saved filter, unless the element has one -->
  <xsl:function name="f:filter">
    <xsl:param name="context" as="element()"/>
    <xsl:param name="expression" as="xs:string"/>
    <xsl:if test="$with-filters and not($context/processing-instruction('fxt-filter'))">
      <xsl:processing-instruction name="fxt-filter" select="'expression=&quot;' || $expression || '&quot;'"/>
    </xsl:if>
  </xsl:function>

  <xsl:template match="/*[starts-with(local-name(), 'FundsXML')]">
    <xsl:copy>
      <xsl:apply-templates select="@*"/>
      <xsl:sequence select="f:formula(., 'Funds', 'count(Funds/Fund)')"/>
      <xsl:sequence select="f:formula(., 'Positions', 'count(.//Position)')"/>
      <xsl:sequence select="f:formula(., 'Assets', 'count(AssetMasterData/Asset)')"/>
      <xsl:apply-templates/>
    </xsl:copy>
  </xsl:template>

  <!-- Positions value and Weights % of the fund's own portfolio: share-class portfolios repeat the positions
       weighted against the share class, so .//Position would count them several times. -->
  <xsl:template match="Fund[parent::Funds]">
    <xsl:copy>
      <xsl:apply-templates select="@*"/>
      <xsl:sequence select="f:formula(., 'Net asset value',
          '(FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount)[1] ! (format-number(xs:decimal(.), ''#,##0.00'') || '' '' || @ccy)')"/>
      <xsl:sequence select="f:formula(., 'Positions value',
          'let $c := string(Currency) return format-number(sum(FundDynamicData/Portfolios/Portfolio/Positions/Position/TotalValue/Amount[@ccy = $c] ! xs:decimal(.)), ''#,##0.00'') || '' '' || $c')"/>
      <xsl:sequence select="f:formula(., 'Weights %', 'format-number(sum(FundDynamicData/Portfolios/Portfolio/Positions/Position/TotalPercentage ! xs:decimal(.)), ''0.00'')')"/>
      <xsl:sequence select="f:formula(., 'Share classes', 'count(SingleFund/ShareClasses/ShareClass)')"/>
      <xsl:apply-templates/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="Positions">
    <xsl:copy>
      <xsl:apply-templates select="@*"/>
      <xsl:sequence select="f:formula(., 'Count', 'count(Position)')"/>
      <xsl:sequence select="f:formula(., 'Value',
          'string-join(for $c in distinct-values(Position/TotalValue/Amount/@ccy) return format-number(sum(Position/TotalValue/Amount[@ccy = $c] ! xs:decimal(.)), ''#,##0.00'') || '' '' || $c, '', '')')"/>
      <xsl:sequence select="f:formula(., 'Weight %', 'format-number(sum(Position/TotalPercentage ! xs:decimal(.)), ''0.00'')')"/>
      <xsl:sequence select="f:formula(., 'Largest',
          '(for $p in Position order by xs:decimal($p/TotalPercentage) descending return $p/UniqueID || '' ('' || $p/TotalPercentage || '' %)'')[1]')"/>
      <xsl:sequence select="f:formula(., 'Without ISIN', 'count(Position[not(Identifiers/ISIN)])')"/>
      <xsl:sequence select="f:filter(., 'Position[xs:decimal(TotalPercentage) gt 5]')"/>
      <xsl:apply-templates/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="AssetMasterData">
    <xsl:copy>
      <xsl:apply-templates select="@*"/>
      <xsl:sequence select="f:formula(., 'Assets', 'count(Asset)')"/>
      <xsl:sequence select="f:formula(., 'By asset type',
          'string-join(for $t in distinct-values(Asset/AssetType) order by $t return $t || '' '' || count(Asset[AssetType = $t]), '', '')')"/>
      <xsl:sequence select="f:formula(., 'By country',
          'string-join(for $c in distinct-values(Asset/Country) order by $c return $c || '' '' || count(Asset[Country = $c]), '', '')')"/>
      <!-- Grid expressions see only their element: "unused assets" needs the whole document (DQ check) -->
      <xsl:sequence select="f:filter(., 'Asset[not(Identifiers/ISIN)]')"/>
      <xsl:apply-templates/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="Transactions">
    <xsl:copy>
      <xsl:apply-templates select="@*"/>
      <xsl:sequence select="f:formula(., 'Count', 'count(Transaction)')"/>
      <xsl:sequence select="f:formula(., 'Market value',
          'string-join(for $c in distinct-values(Transaction/MarketValue/Amount/@ccy) return format-number(sum(Transaction/MarketValue/Amount[@ccy = $c] ! xs:decimal(.)), ''#,##0.00'') || '' '' || $c, '', '')')"/>
      <xsl:sequence select="f:formula(., 'By kind',
          'string-join(for $k in distinct-values(Transaction/TransactionKind) order by $k return $k || '' '' || count(Transaction[TransactionKind = $k]), '', '')')"/>
      <xsl:sequence select="f:filter(., 'Transaction[ValutaDate castable as xs:date and EntryDate castable as xs:date and xs:date(ValutaDate) lt xs:date(EntryDate)]')"/>
      <xsl:apply-templates/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="ShareClasses">
    <xsl:copy>
      <xsl:apply-templates select="@*"/>
      <xsl:sequence select="f:formula(., 'Share classes', 'count(ShareClass)')"/>
      <xsl:sequence select="f:formula(., 'Net asset value',
          'string-join(for $c in distinct-values(ShareClass/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount/@ccy) return format-number(sum(ShareClass/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount[@ccy = $c] ! xs:decimal(.)), ''#,##0.00'') || '' '' || $c, '', '')')"/>
      <xsl:apply-templates/>
    </xsl:copy>
  </xsl:template>
</xsl:stylesheet>
