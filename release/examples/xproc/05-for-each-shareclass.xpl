<?xml version="1.0" encoding="UTF-8"?>
<!--
    Per-share-class report - iterating with p:for-each

    Purpose:    Splits the document into one sub-document per ShareClass,
                reduces each to a compact summary element, and wraps the
                resulting sequence back into a single report document.
    Techniques: p:for-each with a select expression (document splitting),
                p:xslt with an inline stylesheet, p:wrap-sequence
                (sequence -> single document)

    Input:      any FundsXML4 file, e.g. xml/FundsXML4_Equity_Fund.xml
    Output:     <ShareClassReport> with one <ShareClassSummary> per class

    @author FreeXmlToolkit Examples
    @version 1.0
-->
<p:declare-step xmlns:p="http://www.w3.org/ns/xproc" version="3.0">

    <p:input port="source"/>
    <p:output port="result"/>

    <p:for-each>
        <p:with-input select="//ShareClasses/ShareClass"/>

        <p:xslt>
            <p:with-input port="stylesheet">
                <!-- expand-text="false": the {...} braces belong to the inline XSLT's
                     value templates, not to XProc's text value templates -->
                <p:inline expand-text="false">
                    <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                                    version="3.0" expand-text="yes">
                        <xsl:template match="/ShareClass">
                            <ShareClassSummary isin="{Identifiers/ISIN}">
                                <Name>{Names/OfficialName}</Name>
                                <Currency>{Currency}</Currency>
                                <LatestNav>{(Prices/Price/NavPrice)[1]}</LatestNav>
                            </ShareClassSummary>
                        </xsl:template>
                    </xsl:stylesheet>
                </p:inline>
            </p:with-input>
        </p:xslt>
    </p:for-each>

    <p:wrap-sequence wrapper="ShareClassReport"/>

</p:declare-step>
