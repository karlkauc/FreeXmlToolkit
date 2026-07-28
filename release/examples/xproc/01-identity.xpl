<?xml version="1.0" encoding="UTF-8"?>
<!--
    Identity pipeline - the "hello world" of XProc 3.0

    Purpose:    Shows the anatomy of every pipeline: a p:declare-step with an
                input port ("source"), an output port ("result") and one step.
                p:identity simply passes the input document through unchanged.
    Techniques: p:declare-step, p:input / p:output ports, p:identity

    How to run: open this file, open a FundsXML instance (or pick one via the
                Target dropdown) and press Run Pipeline / Ctrl+Enter.

    @author FreeXmlToolkit Examples
    @version 1.0
-->
<p:declare-step xmlns:p="http://www.w3.org/ns/xproc" version="3.0">

    <p:input port="source"/>
    <p:output port="result"/>

    <p:identity/>

</p:declare-step>
