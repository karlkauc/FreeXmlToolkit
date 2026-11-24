<?xml version="1.0" encoding="UTF-8"?>
<!--
  CSV export - XSLT 2.0, text output.

  Flattens fund positions into one CSV row per position, joined to
  AssetMasterData by UniqueID. RFC-4180-style quoting: every field is wrapped
  in double quotes and embedded quotes are doubled, so commas/quotes in names
  are safe.

  19 columns: fund/document context, instrument identification, the holding's
  quantity & price, the position value in both quotation and fund currency,
  and (for bonds) maturity / coupon / issuer. A <Position> carries one "holding"
  child (Bond/Equity/ShareClass/Account/Future/...) selected generically as
  $hold, so quantity and price read uniformly across asset types. The richer
  152-column Solvency-II layout lives in tpt_v7_export.xslt.

  FundsXML 4.x has no namespace - XPath uses bare element names.
  Parameter: $delimiter (default ",").
-->
<xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                exclude-result-prefixes="xs">

  <xsl:output method="text" encoding="UTF-8"/>
  <xsl:param name="delimiter" as="xs:string" select="','"/>

  <xsl:template match="/">
    <xsl:variable name="d" select="$delimiter"/>
    <!-- Header: joined with the same delimiter as the data rows, so that
         delimiter=; yields a consistent 19-column file. -->
    <xsl:value-of select="string-join((
        'FundLEI','FundName','DocumentID','NavDate','UniqueID','ISIN','AssetName',
        'AssetType','Country','Currency','Quantity','Price','ValueQuotationCcy',
        'FundCurrency','ValueFundCcy','Percentage','MaturityDate','CouponRate',
        'IssuerName'), $d)"/>
    <xsl:text>&#10;</xsl:text>
    <!-- One row per position, across every fund (the FundLEI column keeps the
         funds apart in a multi-fund file). -->
    <xsl:for-each select="FundsXML4/Funds/Fund">
      <xsl:variable name="fund" select="."/>
      <xsl:variable name="ccy" select="$fund/Currency"/>
      <xsl:variable name="fundName" select="$fund/Names/OfficialName"/>
      <xsl:variable name="navDate"
                    select="($fund/FundDynamicData/Portfolios/Portfolio/NavDate,
                             $fund/FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate)[1]"/>
      <xsl:for-each select="$fund//Positions/Position">
      <xsl:variable name="aid" select="UniqueID"/>
      <xsl:variable name="asset" select="/FundsXML4/AssetMasterData/Asset[UniqueID=$aid]"/>
      <xsl:variable name="bond" select="$asset/AssetDetails/Bond"/>
      <xsl:variable name="qccy" select="Currency"/>
      <!-- The single holding child: any element that is not a generic wrapper.
           Its name is the instrument family; it carries quantity and price. -->
      <xsl:variable name="hold"
                    select="*[not(local-name() = ('UniqueID','Identifiers','Currency',
                              'TotalValue','TotalPercentage','Exposures','FXRates','Fee'))][1]"/>
      <xsl:variable name="qty" select="($hold/Units, $hold/Shares, $hold/Nominal, $hold/Contracts)[1]"/>
      <xsl:value-of select="string-join((
        concat('&quot;',$fund/Identifiers/LEI,'&quot;'),
        concat('&quot;',replace(string(($fundName,'')[1]),'&quot;','&quot;&quot;'),'&quot;'),
        concat('&quot;',/FundsXML4/ControlData/UniqueDocumentID,'&quot;'),
        concat('&quot;',$navDate,'&quot;'),
        concat('&quot;',UniqueID,'&quot;'),
        concat('&quot;',(Identifiers/ISIN,$asset/Identifiers/ISIN,'')[1],'&quot;'),
        concat('&quot;',replace(string(($asset/Name,'')[1]),'&quot;','&quot;&quot;'),'&quot;'),
        concat('&quot;',($asset/AssetType,'')[1],'&quot;'),
        concat('&quot;',($asset/Country,'')[1],'&quot;'),
        concat('&quot;',(Currency,'')[1],'&quot;'),
        concat('&quot;',($qty,'')[1],'&quot;'),
        concat('&quot;',($hold/Price/Amount,'')[1],'&quot;'),
        concat('&quot;',
               if (TotalValue/Amount[@ccy=$qccy])
               then format-number(number((TotalValue/Amount[@ccy=$qccy])[1]),'0.00')
               else '',                     (: no amount in quotation ccy -> empty, not NaN :)
               '&quot;'),
        concat('&quot;',$ccy,'&quot;'),
        concat('&quot;',format-number(number(TotalValue/Amount[@ccy=$ccy]),'0.00'),'&quot;'),
        concat('&quot;',format-number(number(TotalPercentage),'0.00'),'&quot;'),
        concat('&quot;',($bond/MaturityDate,'')[1],'&quot;'),
        (: FundsXML 4.2.9 carries the coupon under Bond/Coupon/InterestRate;
           the flat Bond/InterestRate element is the legacy fallback. :)
        concat('&quot;',($bond/Coupon/InterestRate,$bond/InterestRate,'')[1],'&quot;'),
        concat('&quot;',replace(string(($asset/AssetDetails/*/Issuer/Name)[1]),'&quot;','&quot;&quot;'),'&quot;')
      ), $d)"/>
      <xsl:text>&#10;</xsl:text>
      </xsl:for-each>
    </xsl:for-each>
  </xsl:template>
</xsl:stylesheet>
