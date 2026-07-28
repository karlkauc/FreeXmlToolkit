<?xml version="1.0" encoding="UTF-8"?>
<!--
    Stamp processing metadata - a multi-step modification chain

    Purpose:    Chains two document modifications: stamps the document element
                with a processedAt attribute (computed with an XPath expression
                via p:with-option) and inserts an audit comment element as the
                first child. Steps connect implicitly - each step's result
                feeds the next step's source.
    Techniques: p:add-attribute, p:with-option (computed option value),
                p:insert with an inline document, implicit step chaining

    Input:      any FundsXML4 file, e.g. xml/FundsXML4_Equity_Fund.xml
    Output:     the same document with /FundsXML4/@processedAt and a leading
                <ProcessingAudit> child element

    @author FreeXmlToolkit Examples
    @version 1.0
-->
<p:declare-step xmlns:p="http://www.w3.org/ns/xproc" version="3.0">

    <p:input port="source"/>
    <p:output port="result"/>

    <p:add-attribute match="/*" attribute-name="processedAt">
        <p:with-option name="attribute-value" select="string(current-dateTime())"/>
    </p:add-attribute>

    <p:insert match="/*" position="first-child">
        <p:with-input port="insertion">
            <p:inline>
                <ProcessingAudit tool="FreeXmlToolkit"
                                 pipeline="04-stamp-metadata.xpl">Processed by the XProc example
                    pipeline.</ProcessingAudit>
            </p:inline>
        </p:with-input>
    </p:insert>

</p:declare-step>
