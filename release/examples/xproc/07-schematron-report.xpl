<?xml version="1.0" encoding="UTF-8"?>
<!--
    Schematron validation report - orchestrating validation in a pipeline

    Purpose:    Validates the incoming document against the bundled identifier
                rules Schematron and emits the SVRL validation report (not the
                document) as the pipeline result. assert-valid="false" keeps
                the pipeline running even when rules fail, so you always get
                the report.
    Techniques: p:validate-with-schematron, assert-valid="false", reading a
                step's secondary port via pipe="report@stepname"

    Input:      any FundsXML4 file, e.g. xml/FundsXML4_Equity_Fund.xml
    Output:     an SVRL report (svrl:schematron-output)

    @author FreeXmlToolkit Examples
    @version 1.0
-->
<p:declare-step xmlns:p="http://www.w3.org/ns/xproc" version="3.0">

    <p:input port="source"/>
    <p:output port="result"/>

    <p:validate-with-schematron name="validate" assert-valid="false">
        <p:with-input port="schema">
            <p:document href="../schematron/funds-identifier-validation.sch"/>
        </p:with-input>
    </p:validate-with-schematron>

    <p:identity>
        <p:with-input pipe="report@validate"/>
    </p:identity>

</p:declare-step>
