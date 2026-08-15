<?xml version="1.0" encoding="UTF-8"?>
<!--
    Schematron Quick Fix (SQF) demo for FreeXmlToolkit.

    Open quickfix-demo.xml, bind this file in the Validation panel (SCHEMATRON
    source row) and validate. Every reported problem offers one or more quick
    fixes — via the right-click "Quick Fix" menu on a problem row, the yellow
    lightbulb in the editor gutter (Alt+Enter / Ctrl+.), or the "Fix" column in
    the Schematron report.

    The demo covers all SQF building blocks:
      - sqf:add       (attribute with a computed value, element insertion)
      - sqf:delete    (remove an element)
      - sqf:replace   (rename an element, keeping its content)
      - sqf:stringReplace (regex correction inside a text node)
      - sqf:default-fix   (preferred fix listed first)
      - sqf:user-entry    (prompts a dialog, @default pre-filled)
      - sqf:call-fix + sqf:param (a generic, reusable fix)
      - global fixes in a schema-level sqf:fixes container
-->
<sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2"
            xmlns:sqf="http://www.schematron-quickfix.com/validator/process"
            xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <sch:pattern id="book-rules">
        <sch:rule context="book">
            <sch:let name="generated-id" value="concat('bk-', count(preceding-sibling::book) + 1)"/>

            <!-- sqf:add (attribute): the id value is computed from the position -->
            <sch:assert test="@id" sqf:fix="addBookId">Every book needs an id attribute.</sch:assert>

            <!-- sqf:delete: legacy markup is simply removed -->
            <sch:report test="legacy-note" sqf:fix="removeLegacyNote">Legacy note elements are obsolete.</sch:report>

            <!-- sqf:replace: rename the element, its text content is kept -->
            <sch:assert test="not(writer)" sqf:fix="renameWriterToAuthor">Use 'author' instead of 'writer'.</sch:assert>

            <!-- sqf:stringReplace: fix ISBN separators via regex -->
            <sch:assert test="not(isbn) or not(contains(isbn, ' '))" sqf:fix="normalizeIsbn">ISBN must use dashes, not spaces.</sch:assert>

            <!-- two fixes, the default one first (sqf:default-fix) -->
            <sch:assert test="summary or @summarized='false'" sqf:fix="addEmptySummary markNotSummarized"
                        sqf:default-fix="addEmptySummary">A book needs a summary (or must be marked as not summarized).</sch:assert>

            <!-- sqf:user-entry: the publisher name is prompted in a dialog -->
            <sch:assert test="publisher" sqf:fix="addPublisher">A book needs a publisher.</sch:assert>

            <!-- sqf:call-fix: delegates to the generic global fix below -->
            <sch:report test="draft" sqf:fix="removeAllDrafts">Draft fragments must not ship.</sch:report>

            <sqf:fix id="addBookId">
                <sqf:description>
                    <sqf:title>Add generated id attribute</sqf:title>
                    <sqf:p>Adds an id derived from the book's position (bk-1, bk-2, …).</sqf:p>
                </sqf:description>
                <sqf:add node-type="attribute" target="id"><xsl:value-of select="$generated-id"/></sqf:add>
            </sqf:fix>

            <sqf:fix id="removeLegacyNote">
                <sqf:description>
                    <sqf:title>Remove legacy note</sqf:title>
                </sqf:description>
                <sqf:delete match="legacy-note"/>
            </sqf:fix>

            <sqf:fix id="renameWriterToAuthor">
                <sqf:description>
                    <sqf:title>Rename writer to author</sqf:title>
                </sqf:description>
                <sqf:replace match="writer" node-type="element" target="author">
                    <xsl:value-of select="."/>
                </sqf:replace>
            </sqf:fix>

            <sqf:fix id="normalizeIsbn">
                <sqf:description>
                    <sqf:title>Replace spaces in ISBN with dashes</sqf:title>
                </sqf:description>
                <sqf:stringReplace match="isbn/text()" regex=" ">-</sqf:stringReplace>
            </sqf:fix>

            <sqf:fix id="addEmptySummary">
                <sqf:description>
                    <sqf:title>Add empty summary element</sqf:title>
                </sqf:description>
                <sqf:add node-type="element" target="summary" position="last-child"/>
            </sqf:fix>

            <sqf:fix id="markNotSummarized">
                <sqf:description>
                    <sqf:title>Mark book as not summarized</sqf:title>
                </sqf:description>
                <sqf:add node-type="attribute" target="summarized">false</sqf:add>
            </sqf:fix>

            <sqf:fix id="addPublisher">
                <sqf:description>
                    <sqf:title>Add publisher…</sqf:title>
                    <sqf:p>Prompts for the publisher name.</sqf:p>
                </sqf:description>
                <sqf:user-entry name="publisherName" default="'Unknown Publisher'">
                    <sqf:description>
                        <sqf:title>Publisher name</sqf:title>
                        <sqf:p>The name of the book's publisher.</sqf:p>
                    </sqf:description>
                </sqf:user-entry>
                <sqf:add node-type="element" target="publisher" position="last-child"><xsl:value-of select="$publisherName"/></sqf:add>
            </sqf:fix>

            <sqf:fix id="removeAllDrafts">
                <sqf:description>
                    <sqf:title>Remove all draft fragments</sqf:title>
                </sqf:description>
                <sqf:call-fix ref="removeMatching">
                    <sqf:with-param name="victims" select="draft"/>
                </sqf:call-fix>
            </sqf:fix>
        </sch:rule>
    </sch:pattern>

    <sqf:fixes>
        <!-- A generic, reusable fix: deletes whatever nodes the caller passes in.
             It declares a parameter without a default, so it is never offered
             directly — only via sqf:call-fix. -->
        <sqf:fix id="removeMatching">
            <sqf:description>
                <sqf:title>Remove matching nodes</sqf:title>
            </sqf:description>
            <sqf:param name="victims"/>
            <sqf:delete match="$victims"/>
        </sqf:fix>
    </sqf:fixes>
</sch:schema>
