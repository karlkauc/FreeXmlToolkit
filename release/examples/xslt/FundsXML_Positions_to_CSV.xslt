<?xml version="1.0" encoding="UTF-8"?>
<!--
    FundsXML4 portfolio positions to CSV (XSLT 3.0, text output)

    Exports every portfolio position as one CSV row - ready for Excel or any
    other spreadsheet/database import. Fields containing commas or quotes are
    quoted RFC-4180 style.

    Input : any FundsXML4 file, e.g. xml/FundsXML4_Equity_Fund.xml
    Output: text/csv with header row
            FundISIN,FundName,PositionID,ISIN,AssetName,AssetClass,Currency,TotalValue,Percentage

    A comparable stylesheet lives in the official FundsXML example repository:
    XSLT_Transformations/CSV_Export/positions_csv.xslt
    (https://github.com/fundsxml/examples)
-->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:fxt="urn:freexmltoolkit:csv"
                exclude-result-prefixes="xs fxt"
                version="3.0"
                expand-text="yes">

    <xsl:output method="text" encoding="UTF-8"/>

    <!-- RFC-4180 field quoting: quote when the value contains comma, quote or newline -->
    <xsl:function name="fxt:csv" as="xs:string">
        <xsl:param name="value" as="xs:string?"/>
        <xsl:variable name="v" select="string($value)"/>
        <xsl:sequence select="if (matches($v, '[,&quot;\n\r]'))
                              then '&quot;' || replace($v, '&quot;', '&quot;&quot;') || '&quot;'
                              else $v"/>
    </xsl:function>

    <xsl:template match="/">
        <xsl:text>FundISIN,FundName,PositionID,ISIN,AssetName,AssetClass,Currency,TotalValue,Percentage&#10;</xsl:text>
        <xsl:for-each select="FundsXML4/Funds/Fund">
            <xsl:variable name="fundIsin" select="string(Identifiers/ISIN)"/>
            <xsl:variable name="fundName" select="string(Names/OfficialName)"/>
            <xsl:for-each select="FundDynamicData/Portfolios/Portfolio/Positions/Position">
                <!-- the asset-class element (Equity, Bond, Future, Fee, Account, ...) names the position type -->
                <xsl:variable name="assetClass"
                              select="(local-name((* except (UniqueID, Identifiers, Currency, TotalValue,
                                       TotalPercentage, Exposures, FXRates))[1]), 'Other')[1]"/>
                <xsl:value-of select="string-join((
                        fxt:csv($fundIsin),
                        fxt:csv($fundName),
                        fxt:csv(UniqueID),
                        fxt:csv((Identifiers/ISIN)[1]),
                        fxt:csv((Identifiers/OtherID)[1]),
                        fxt:csv($assetClass),
                        fxt:csv(Currency),
                        fxt:csv((TotalValue/Amount)[1]),
                        fxt:csv(TotalPercentage)
                    ), ',')"/>
                <xsl:text>&#10;</xsl:text>
            </xsl:for-each>
        </xsl:for-each>
    </xsl:template>

</xsl:stylesheet>
