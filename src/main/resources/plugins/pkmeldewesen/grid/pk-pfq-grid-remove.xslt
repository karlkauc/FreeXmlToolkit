<?xml version="1.0" encoding="UTF-8"?>
<!--
  pk-pfq-grid-remove.xslt - removes the FreeXmlToolkit grid annotations from a QMV report file
  (or any XML file) so that it can be submitted:

    * every <?fxt-formula …?> and <?fxt-filter …?> processing instruction,
    * the stored result element of a formula with store="true" (the element named after the
      formula right after the instruction, as GridFormulas.storeResult writes it),
    * the indentation whitespace in front of each removed node, so no blank lines remain.

  Everything else is copied unchanged. Used on its own; pk-pfq-grid-add.xslt contains the same rules.

  Saxon: java -cp Saxon-HE.jar net.sf.saxon.Transform -s:in.xml -xsl:pk-pfq-grid-remove.xslt -o:out.xml
-->
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:fxt="urn:fxt:grid" exclude-result-prefixes="#all">

	<!-- the declaration is written by hand: Saxon puts no line break after it with indent="no" -->
	<xsl:output method="xml" encoding="UTF-8" indent="no" omit-xml-declaration="yes"/>
	<xsl:mode on-no-match="shallow-copy"/>

	<!-- report the removed annotations; false() keeps the run quiet -->
	<xsl:param name="report" as="xs:boolean" select="true()"/>

	<xsl:template match="/">
		<xsl:if test="$report">
			<xsl:variable name="pis" select="//processing-instruction()[fxt:is-annotation(.)]"/>
			<xsl:message expand-text="yes">qmv-grid-remove: {count($pis[name() eq 'fxt-formula'])} Formeln, {count($pis[name() eq 'fxt-filter'])} Filter, {count(//*[fxt:is-stored-result(.)])} gespeicherte Formelergebnisse entfernt</xsl:message>
		</xsl:if>
		<xsl:value-of select="'&lt;?xml version=&quot;1.0&quot; encoding=&quot;UTF-8&quot;?&gt;&#10;'" disable-output-escaping="yes"/>
		<xsl:apply-templates/>
		<xsl:text>&#10;</xsl:text>
	</xsl:template>

	<!-- the annotations themselves -->
	<xsl:template match="processing-instruction()[fxt:is-annotation(.)]"/>

	<!-- the stored result of a formula with store="true" -->
	<xsl:template match="*[fxt:is-stored-result(.)]"/>

	<!-- the indentation in front of a removed node -->
	<xsl:template match="text()[not(normalize-space())][fxt:is-removed(following-sibling::node()[1])]"/>

	<!-- ===================================================================== -->

	<!-- an fxt-formula or fxt-filter instruction -->
	<xsl:function name="fxt:is-annotation" as="xs:boolean">
		<xsl:param name="node" as="node()?"/>
		<xsl:sequence select="exists($node[self::processing-instruction('fxt-formula') or self::processing-instruction('fxt-filter')])"/>
	</xsl:function>

	<!-- a node this stylesheet drops -->
	<xsl:function name="fxt:is-removed" as="xs:boolean">
		<xsl:param name="node" as="node()?"/>
		<xsl:sequence select="fxt:is-annotation($node) or exists($node[self::*][fxt:is-stored-result(.)])"/>
	</xsl:function>

	<!-- element without element children whose previous non-whitespace sibling is a storing formula of the same name -->
	<xsl:function name="fxt:is-stored-result" as="xs:boolean">
		<xsl:param name="el" as="element()"/>
		<xsl:variable name="pi" select="$el[not(*)]/preceding-sibling::node()[not(self::text()[not(normalize-space())])][1][self::processing-instruction('fxt-formula')]"/>
		<xsl:sequence select="exists($pi) and fxt:field($pi, 'store') eq 'true' and fxt:field($pi, 'name') eq name($el)"/>
	</xsl:function>

	<!-- a field of the instruction data (name="…" store="…" expression="…"), unescaped -->
	<xsl:function name="fxt:field" as="xs:string?">
		<xsl:param name="pi" as="processing-instruction()"/>
		<xsl:param name="field" as="xs:string"/>
		<xsl:variable name="m" select="analyze-string(string($pi), '(^|\s)' || $field || '=&quot;([^&quot;]*)&quot;')//*:group[@nr = '2']"/>
		<xsl:sequence select="$m[1] ! replace(replace(replace(string(.), '&amp;quot;', '&quot;'), '&amp;gt;', '&gt;'), '&amp;amp;', '&amp;')"/>
	</xsl:function>
</xsl:stylesheet>
