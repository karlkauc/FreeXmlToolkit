<?xml version="1.0" encoding="UTF-8"?>
<!--
    Branch on document content - p:choose and p:variable

    Purpose:    Classifies the incoming document (does it hold equity assets?)
                and stamps a matching category attribute on the root element.
                Also computes the position count into a pipeline variable and
                writes it alongside - one value, used in both branches.
    Techniques: p:variable (computed once, used later), p:choose / p:when /
                p:otherwise, p:add-attribute per branch

    Input:      any FundsXML4 file - try both xml/FundsXML4_Equity_Fund.xml
                and xml/FundsXML_422_Bond_Fund.xml to see the branches
    Output:     the document with /FundsXML4/@fundCategory and @positionCount

    @author FreeXmlToolkit Examples
    @version 1.0
-->
<p:declare-step xmlns:p="http://www.w3.org/ns/xproc" version="3.0">

    <p:input port="source"/>
    <p:output port="result"/>

    <p:variable name="positionCount" select="count(//Position)"/>

    <p:choose>
        <p:when test="exists(//AssetDetails/Equity)">
            <p:add-attribute match="/*" attribute-name="fundCategory"
                             attribute-value="equity"/>
        </p:when>
        <p:when test="exists(//AssetDetails/Bond)">
            <p:add-attribute match="/*" attribute-name="fundCategory"
                             attribute-value="bond"/>
        </p:when>
        <p:otherwise>
            <p:add-attribute match="/*" attribute-name="fundCategory"
                             attribute-value="mixed"/>
        </p:otherwise>
    </p:choose>

    <p:add-attribute match="/*" attribute-name="positionCount">
        <p:with-option name="attribute-value" select="string($positionCount)"/>
    </p:add-attribute>

</p:declare-step>
