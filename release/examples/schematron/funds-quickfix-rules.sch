<?xml version="1.0" encoding="UTF-8"?>
<!--
    FundsXML Quick Fix (SQF) example for FreeXmlToolkit.

    A realistic companion to quickfix-demo.sch: business rules for FundsXML4
    documents whose findings can all be corrected with a single click.

    Open funds-quickfix-demo.xml, bind this file in the Validation panel
    (SCHEMATRON source row) and validate. Fixes are offered via the right-click
    "Quick Fix" menu on a problem row, the yellow lightbulb in the editor gutter
    (Alt+Enter / Ctrl+.), or the "Fix" column in the Schematron report.

    Demonstrated here:
      - sqf:add with a value COMPUTED from elsewhere in the document
        (missing Amount/@ccy is taken from the fund's own Currency element)
      - sqf:add with sqf:user-entry (missing ControlData/Language prompts a dialog)
      - sqf:add with @position (SingleFundFlag is inserted right after Currency)
      - sqf:replace with dynamic content (lowercase Currency is uppercased)
      - sqf:stringReplace (spaces are stripped from ISIN and e-mail values)
      - sqf:default-fix with two alternatives (negative NAV amount)
      - a global fix in the schema-level sqf:fixes container (internal comments)
-->
<sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2"
            xmlns:sqf="http://www.schematron-quickfix.com/validator/process"
            xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <sch:pattern id="control-data">
        <sch:rule context="ControlData">
            <!-- sqf:user-entry: the language code is prompted, pre-filled with EN -->
            <sch:assert test="Language" sqf:fix="addLanguage">ControlData should declare the document Language.</sch:assert>

            <sqf:fix id="addLanguage">
                <sqf:description>
                    <sqf:title>Add Language element…</sqf:title>
                    <sqf:p>Prompts for the ISO language code (default EN).</sqf:p>
                </sqf:description>
                <sqf:user-entry name="languageCode" default="'EN'">
                    <sqf:description>
                        <sqf:title>Language code</sqf:title>
                        <sqf:p>Two-letter ISO 639-1 code, e.g. EN or DE.</sqf:p>
                    </sqf:description>
                </sqf:user-entry>
                <sqf:add node-type="element" target="Language" position="last-child"><xsl:value-of select="$languageCode"/></sqf:add>
            </sqf:fix>
        </sch:rule>

        <sch:rule context="ControlData/DataSupplier/Contact/Email">
            <!-- sqf:stringReplace: remove accidental whitespace in the address -->
            <sch:assert test="not(matches(., '\s'))" sqf:fix="removeEmailWhitespace">E-mail addresses must not contain whitespace.</sch:assert>

            <sqf:fix id="removeEmailWhitespace">
                <sqf:description>
                    <sqf:title>Remove whitespace from e-mail address</sqf:title>
                </sqf:description>
                <sqf:stringReplace match="text()" regex="\s+"/>
            </sqf:fix>
        </sch:rule>
    </sch:pattern>

    <sch:pattern id="fund-master-data">
        <sch:rule context="Fund">
            <!-- sqf:replace with dynamic content: uppercase the ISO currency code -->
            <sch:assert test="not(Currency) or Currency = upper-case(Currency)"
                        sqf:fix="uppercaseFundCurrency">Fund currency codes must be uppercase ISO 4217.</sch:assert>

            <!-- sqf:add with @position: insert the flag right after Currency -->
            <sch:assert test="SingleFundFlag" sqf:fix="addSingleFundFlag">Fund must state its SingleFundFlag.</sch:assert>

            <!-- global fix (defined in sqf:fixes below) -->
            <sch:report test="InternalComment" sqf:fix="removeInternalComments">Internal comments must not be delivered.</sch:report>

            <sqf:fix id="uppercaseFundCurrency">
                <sqf:description>
                    <sqf:title>Uppercase the currency code</sqf:title>
                </sqf:description>
                <sqf:replace match="Currency" node-type="element" target="Currency">
                    <xsl:value-of select="upper-case(.)"/>
                </sqf:replace>
            </sqf:fix>

            <sqf:fix id="addSingleFundFlag">
                <sqf:description>
                    <sqf:title>Add SingleFundFlag after Currency</sqf:title>
                    <sqf:p>Inserts &lt;SingleFundFlag&gt;true&lt;/SingleFundFlag&gt; directly after the Currency element.</sqf:p>
                </sqf:description>
                <sqf:add match="Currency" node-type="element" target="SingleFundFlag" position="after">true</sqf:add>
            </sqf:fix>
        </sch:rule>

        <sch:rule context="Fund/Identifiers/ISIN">
            <!-- sqf:stringReplace: strip spaces that sneak in via copy/paste -->
            <sch:assert test="not(contains(., ' '))" sqf:fix="removeIsinSpaces">ISIN must not contain spaces.</sch:assert>

            <sqf:fix id="removeIsinSpaces">
                <sqf:description>
                    <sqf:title>Remove spaces from ISIN</sqf:title>
                </sqf:description>
                <sqf:stringReplace match="text()" regex=" "/>
            </sqf:fix>
        </sch:rule>
    </sch:pattern>

    <sch:pattern id="amounts">
        <sch:rule context="TotalNetAssetValue/Amount">
            <!-- computed sqf:add (default) vs. sqf:user-entry: two ways to fill @ccy -->
            <sch:assert test="@ccy" sqf:fix="ccyFromFundCurrency ccyManually"
                        sqf:default-fix="ccyFromFundCurrency">Amount needs a ccy attribute.</sch:assert>

            <!-- two alternatives, the non-destructive one is the default -->
            <sch:report test="number(.) lt 0" sqf:fix="makeAmountPositive clearAmount"
                        sqf:default-fix="makeAmountPositive">A total net asset value must not be negative.</sch:report>

            <sqf:fix id="ccyFromFundCurrency">
                <sqf:description>
                    <sqf:title>Take currency from the fund</sqf:title>
                    <sqf:p>Copies the value of the surrounding fund's Currency element.</sqf:p>
                </sqf:description>
                <sqf:add node-type="attribute" target="ccy"><xsl:value-of select="ancestor::Fund/Currency"/></sqf:add>
            </sqf:fix>

            <sqf:fix id="ccyManually">
                <sqf:description>
                    <sqf:title>Enter currency code…</sqf:title>
                </sqf:description>
                <sqf:user-entry name="ccyCode" default="'EUR'">
                    <sqf:description>
                        <sqf:title>Currency code</sqf:title>
                        <sqf:p>ISO 4217 code for the amount, e.g. EUR or HUF.</sqf:p>
                    </sqf:description>
                </sqf:user-entry>
                <sqf:add node-type="attribute" target="ccy"><xsl:value-of select="$ccyCode"/></sqf:add>
            </sqf:fix>

            <sqf:fix id="makeAmountPositive">
                <sqf:description>
                    <sqf:title>Remove the minus sign</sqf:title>
                </sqf:description>
                <sqf:stringReplace match="text()" regex="^-"/>
            </sqf:fix>

            <sqf:fix id="clearAmount">
                <sqf:description>
                    <sqf:title>Clear the amount</sqf:title>
                    <sqf:p>Replaces the negative value with 0 for later correction.</sqf:p>
                </sqf:description>
                <sqf:replace match="text()" node-type="keep">0</sqf:replace>
            </sqf:fix>
        </sch:rule>
    </sch:pattern>

    <sqf:fixes>
        <!-- Global fix: available to every rule that references it by id. -->
        <sqf:fix id="removeInternalComments">
            <sqf:description>
                <sqf:title>Remove all internal comments</sqf:title>
            </sqf:description>
            <sqf:delete match="InternalComment"/>
        </sqf:fix>
    </sqf:fixes>
</sch:schema>
