<?xml version="1.0" encoding="UTF-8"?>
<!--
    Extract the fund core - slim a FundsXML document with p:delete

    Purpose:    Produces a lightweight "fund core" document by deleting the
                bulky parts (portfolio positions and FX rate tables) from a
                FundsXML4 instance - a typical redaction / slimming task
                before archiving or sending a document downstream.
    Techniques: p:delete with an XPath match pattern (union of two patterns)

    Input:      any FundsXML4 file, e.g. xml/FundsXML4_Equity_Fund.xml
    Output:     the same document without Positions and FXRates subtrees

    @author FreeXmlToolkit Examples
    @version 1.0
-->
<p:declare-step xmlns:p="http://www.w3.org/ns/xproc" version="3.0">

    <p:input port="source"/>
    <p:output port="result"/>

    <p:delete match="//Positions | //FXRates"/>

</p:declare-step>
