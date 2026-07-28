<?xml version="1.0" encoding="UTF-8"?>
<!--
    Fund KPIs as JSON - XML-to-JSON conversion in a pipeline

    Purpose:    Converts a FundsXML4 document into the compact JSON summary
                produced by the bundled FundsXML_to_JSON.xslt stylesheet. The
                OUTPUT panel detects the application/json result and routes it
                to the text view with JSON formatting.
    Techniques: p:xslt with an XSLT 3.0 JSON-emitting stylesheet (relative
                href), JSON output media type flowing through the pipeline

    Input:      any FundsXML4 file, e.g. xml/FundsXML4_Equity_Fund.xml
    Output:     application/json - document header, funds, share classes,
                portfolio positions

    @author FreeXmlToolkit Examples
    @version 1.0
-->
<p:declare-step xmlns:p="http://www.w3.org/ns/xproc" version="3.0">

    <p:input port="source"/>
    <p:output port="result"/>

    <p:xslt>
        <p:with-input port="stylesheet">
            <p:document href="../xslt/FundsXML_to_JSON.xslt"/>
        </p:with-input>
    </p:xslt>

</p:declare-step>
