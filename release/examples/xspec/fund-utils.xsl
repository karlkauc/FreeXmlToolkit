<?xml version="1.0" encoding="UTF-8"?>
<!--
  fund-utils.xsl — a small stylesheet under test for the XSpec examples.

  Functions (tested in 01-xslt-functions.xspec):
    f:weight-class($pct)          core (>= 10 %), satellite (>= 1 %) or minor, by absolute weight
    f:format-amount($value, $ccy) "1,234,567.89 HUF"
    f:is-valid-isin($isin)        format and check digit (ISO 6166, Luhn over the letter values)

  Templates (tested in 02-xslt-templates.xspec):
    /          -> <position-report> with one <fund> per Fund (a direct transformation of a
                  FundsXML file gives this report)
    Positions  -> <lines count="…"> with one <line> per position
    Position   -> <line isin="…" weight="…" class="…">amount</line>
-->
<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:f="urn:fxt:examples:fund-utils"
                exclude-result-prefixes="xs f">

    <xsl:output method="xml" indent="yes"/>

    <!-- Classifies a position by its share of the fund (TotalPercentage). -->
    <xsl:function name="f:weight-class" as="xs:string">
        <xsl:param name="pct" as="xs:decimal"/>
        <xsl:sequence select="if (abs($pct) ge 10) then 'core'
                              else if (abs($pct) ge 1) then 'satellite'
                              else 'minor'"/>
    </xsl:function>

    <!-- Formats an amount with thousands separators, two decimals and the currency code. -->
    <xsl:function name="f:format-amount" as="xs:string">
        <xsl:param name="value" as="xs:decimal"/>
        <xsl:param name="ccy" as="xs:string"/>
        <xsl:sequence select="format-number($value, '#,##0.00') || ' ' || $ccy"/>
    </xsl:function>

    <!-- An ISIN: two letters, nine letters or digits, one check digit. Letters count as 10..35,
         then the Luhn algorithm runs over the resulting digit string. -->
    <xsl:function name="f:is-valid-isin" as="xs:boolean">
        <xsl:param name="isin" as="xs:string?"/>
        <xsl:sequence select="
            if (not(matches(string($isin), '^[A-Z]{2}[A-Z0-9]{9}[0-9]$'))) then false()
            else
                let $digits := string-join(
                        for $c in string-to-codepoints($isin)
                        return if ($c le 57) then codepoints-to-string($c) else string($c - 55)),
                    $reversed := reverse(for $c in string-to-codepoints($digits) return $c - 48),
                    $sum := sum(
                        for $i in 1 to count($reversed)
                        return if ($i mod 2 = 0)
                               then (let $d := 2 * $reversed[$i] return if ($d gt 9) then $d - 9 else $d)
                               else $reversed[$i])
                return $sum mod 10 = 0"/>
    </xsl:function>

    <!-- The whole document: one entry per fund with its positions. Without this rule XSLT's
         built-in rules would copy the text of every other element into the result. -->
    <xsl:template match="/">
        <position-report>
            <xsl:for-each select="//Funds/Fund">
                <fund id="{(Identifiers/ISIN, Identifiers/LEI, Identifiers/*)[1]}" currency="{Currency}">
                    <name><xsl:value-of select="normalize-space((Names/OfficialName, Names/ShortName)[1])"/></name>
                    <xsl:apply-templates select=".//Portfolios/Portfolio/Positions"/>
                </fund>
            </xsl:for-each>
        </position-report>
    </xsl:template>

    <xsl:template match="Positions">
        <lines count="{count(Position)}">
            <xsl:apply-templates select="Position"/>
        </lines>
    </xsl:template>

    <xsl:template match="Position">
        <line isin="{(Identifiers/ISIN, UniqueID)[1]}"
              weight="{TotalPercentage}"
              class="{f:weight-class(xs:decimal(TotalPercentage))}">
            <xsl:value-of select="f:format-amount(xs:decimal(TotalValue/Amount[1]),
                                                  string(TotalValue/Amount[1]/@ccy))"/>
        </line>
    </xsl:template>

</xsl:stylesheet>
