<?xml version="1.0" encoding="UTF-8"?>
<!--
    Portfolio positions to CSV - orchestrating an existing stylesheet

    Purpose:    Runs the bundled FundsXML_Positions_to_CSV.xslt stylesheet from
                a pipeline. Demonstrates that pipelines compose the tools you
                already have: the stylesheet is referenced with a relative href
                that resolves against this pipeline file's directory.
    Techniques: p:xslt, p:with-input port="stylesheet", p:document with a
                relative href, text (CSV) output

    Input:      any FundsXML4 file, e.g. xml/FundsXML4_Equity_Fund.xml
    Output:     text/csv - one row per portfolio position

    @author FreeXmlToolkit Examples
    @version 1.0
-->
<p:declare-step xmlns:p="http://www.w3.org/ns/xproc" version="3.0">

    <p:input port="source"/>
    <p:output port="result"/>

    <p:xslt>
        <p:with-input port="stylesheet">
            <p:document href="../xslt/FundsXML_Positions_to_CSV.xslt"/>
        </p:with-input>
    </p:xslt>

</p:declare-step>
