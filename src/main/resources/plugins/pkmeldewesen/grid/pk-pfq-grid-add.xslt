<?xml version="1.0" encoding="UTF-8"?>
<!--
  pk-pfq-grid-add.xslt - adds FreeXmlToolkit grid formulas and saved filters to a QMV report file
  (Nationale_Meldung_pfq, Taxonomie 2.9.0). Open the result in FreeXmlToolkit, Graphic view:
  every table shows its formulas (row "ƒ") with the live result; the saved filter of a table
  comes up switched off - click its funnel line to switch it on.

  Formulas are XQuery 3.1 expressions with the table element as context (stored as
  <?fxt-formula name="…" store="false" expression="…"?>), filters likewise
  (<?fxt-filter expression="…"?>, one per table). No formula stores its result, so no data
  changes. Existing fxt-formula / fxt-filter instructions are replaced (the rules of
  pk-pfq-grid-remove.xslt are part of this stylesheet, so it needs no other file), so running it
  twice gives the same file.

  Before submitting the file, run pk-pfq-grid-remove.xslt on it.

  Parameters: filters / formulas (xs:boolean, default true) - add only one of both.
  Saxon: java -cp Saxon-HE.jar net.sf.saxon.Transform -s:in.xml -xsl:pk-pfq-grid-add.xslt -o:out.xml
-->
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:fxt="urn:fxt:grid" exclude-result-prefixes="#all">


	<!-- ===== removal of existing annotations (same as pk-pfq-grid-remove.xslt) ===== -->

	<!-- the declaration is written by hand: Saxon puts no line break after it with indent="no" -->
	<xsl:output method="xml" encoding="UTF-8" indent="no" omit-xml-declaration="yes"/>
	<xsl:mode on-no-match="shallow-copy"/>

	<!-- true(): report the replaced annotations -->
	<xsl:param name="report" as="xs:boolean" select="false()"/>

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

	<!-- ===== annotations to add ===== -->

	<xsl:param name="filters" as="xs:boolean" select="true()"/>
	<xsl:param name="formulas" as="xs:boolean" select="true()"/>

	<!-- indentation of a table's children in a QMV file -->
	<xsl:variable name="indent" select="'&#10;        '"/>

	<!--
	  The annotations per table (@table = template code, the element name starts with it).
	  Amounts: format-number(…, '#.##0,00', 'de') - the grid predeclares the German format 'de'.
	  Missing facts count as 0; differences are rounded to cents first (no "-0,00").
	-->
	<xsl:variable name="catalog" as="element()*">
		<!-- PK.006.02.01.01 List of Assets - Positionen -->
		<filter table="PK.006.02.01.01">Datenpunkt[number(Wert_C1100PK) ne number(Wert_C1110PK)]</filter>
		<formula table="PK.006.02.01.01" name="Anzahl_Positionen">count(Datenpunkt)</formula>
		<formula table="PK.006.02.01.01" name="Summe_Wert_C0060">format-number(sum(Datenpunkt/Wert_C0060[normalize-space()] ! xs:decimal(.)), '#.##0,00', 'de')</formula>
		<formula table="PK.006.02.01.01" name="Summe_Wert_C0070">format-number(sum(Datenpunkt/Wert_C0070[normalize-space()] ! xs:decimal(.)), '#.##0,00', 'de')</formula>
		<formula table="PK.006.02.01.01" name="Summe_Wert_C0080">format-number(sum(Datenpunkt/Wert_C0080[normalize-space()] ! xs:decimal(.)), '#.##0,00', 'de')</formula>
		<formula table="PK.006.02.01.01" name="Summe_Wert_C0090">format-number(sum(Datenpunkt/Wert_C0090[normalize-space()] ! xs:decimal(.)), '#.##0,00', 'de')</formula>
		<formula table="PK.006.02.01.01" name="Summe_Wert_C1100PK">format-number(sum(Datenpunkt/Wert_C1100PK[normalize-space()] ! xs:decimal(.)), '#.##0,00', 'de')</formula>
		<formula table="PK.006.02.01.01" name="Summe_Wert_C1110PK">format-number(sum(Datenpunkt/Wert_C1110PK[normalize-space()] ! xs:decimal(.)), '#.##0,00', 'de')</formula>
		<formula table="PK.006.02.01.01" name="Positionen_mit_PNR">count(Datenpunkt[normalize-space(Wert_C1120PK)])</formula>

		<!-- PK.006.02.01.02 List of Assets - Vermögenswerte -->
		<filter table="PK.006.02.01.02">Datenpunkt[substring(Wert_C0230, 3, 1) = '4']</filter>
		<formula table="PK.006.02.01.02" name="Anzahl_Vermoegenswerte">count(Datenpunkt)</formula>
		<formula table="PK.006.02.01.02" name="Anzahl_je_CIC_Kategorie">string-join(for $k in sort(distinct-values(Datenpunkt/Wert_C0230 ! substring(., 3, 1))) return $k || ': ' || count(Datenpunkt[substring(Wert_C0230, 3, 1) = $k]), ' · ')</formula>
		<formula table="PK.006.02.01.02" name="Not_rated">count(Datenpunkt[Wert_C0250 = 'Not rated'])</formula>

		<!-- PK.006.03 Durchrechnung (Look-through) -->
		<filter table="PK.006.03">Datenpunkt[number(Wert_C1100PK) lt 0]</filter>
		<formula table="PK.006.03" name="Anzahl_Zeilen">count(Datenpunkt)</formula>
		<formula table="PK.006.03" name="Anzahl_Fonds">count(distinct-values(Datenpunkt/Wert_C0010PK))</formula>
		<formula table="PK.006.03" name="Summe_Wert_C1100PK">format-number(sum(Datenpunkt/Wert_C1100PK[normalize-space()] ! xs:decimal(.)), '#.##0,00', 'de')</formula>
		<formula table="PK.006.03" name="Summe_Wert_C1110PK">format-number(sum(Datenpunkt/Wert_C1110PK[normalize-space()] ! xs:decimal(.)), '#.##0,00', 'de')</formula>

		<!-- PK.800.01 Vermögensausweis: each total minus its detail positions (should be 0), as qmv25-v005.xslt $sub800 -->
		<filter table="PK.800.01">Datenpunkt[RowCode = ('R800-100', 'R800-200', 'R800-300', 'R800-400', 'R800-500', 'R800-600', 'R800-800')]</filter>
		<formula table="PK.800.01" name="Diff_100">format-number(round(sum(Datenpunkt[RowCode = 'R800-100']/Wert ! xs:decimal(.)) - sum(Datenpunkt[RowCode = ('R800-110', 'R800-130', 'R800-140', 'R800-160', 'R800-170', 'R800-180', 'R800-190')]/Wert ! xs:decimal(.)), 2), '#.##0,00', 'de')</formula>
		<formula table="PK.800.01" name="Diff_200">format-number(round(sum(Datenpunkt[RowCode = 'R800-200']/Wert ! xs:decimal(.)) - sum(Datenpunkt[RowCode = ('R800-210', 'R800-220', 'R800-230')]/Wert ! xs:decimal(.)), 2), '#.##0,00', 'de')</formula>
		<formula table="PK.800.01" name="Diff_300">format-number(round(sum(Datenpunkt[RowCode = 'R800-300']/Wert ! xs:decimal(.)) - sum(Datenpunkt[RowCode = ('R800-310', 'R800-315', 'R800-320', 'R800-325', 'R800-330', 'R800-335', 'R800-372', 'R800-374', 'R800-382', 'R800-384', 'R800-390')]/Wert ! xs:decimal(.)), 2), '#.##0,00', 'de')</formula>
		<formula table="PK.800.01" name="Diff_400">format-number(round(sum(Datenpunkt[RowCode = 'R800-400']/Wert ! xs:decimal(.)) - sum(Datenpunkt[RowCode = ('R800-410', 'R800-420', 'R800-430', 'R800-470', 'R800-480', 'R800-490')]/Wert ! xs:decimal(.)), 2), '#.##0,00', 'de')</formula>
		<formula table="PK.800.01" name="Diff_500">format-number(round(sum(Datenpunkt[RowCode = 'R800-500']/Wert ! xs:decimal(.)) - sum(Datenpunkt[RowCode = ('R800-510', 'R800-560', 'R800-570', 'R800-580', 'R800-590')]/Wert ! xs:decimal(.)), 2), '#.##0,00', 'de')</formula>
		<formula table="PK.800.01" name="Diff_600">format-number(round(sum(Datenpunkt[RowCode = 'R800-600']/Wert ! xs:decimal(.)) - sum(Datenpunkt[RowCode = ('R800-610', 'R800-620', 'R800-630', 'R800-670', 'R800-680', 'R800-690')]/Wert ! xs:decimal(.)), 2), '#.##0,00', 'de')</formula>
		<formula table="PK.800.01" name="Diff_800">format-number(round(sum(Datenpunkt[RowCode = 'R800-800']/Wert ! xs:decimal(.)) - sum(Datenpunkt[RowCode = ('R800-100', 'R800-200', 'R800-300', 'R800-400', 'R800-500', 'R800-600')]/Wert ! xs:decimal(.)), 2), '#.##0,00', 'de')</formula>
		<formula table="PK.800.01" name="Leverage_Prozent">let $total := sum(Datenpunkt[RowCode = 'R800-800']/Wert ! xs:decimal(.)) return if ($total = 0) then 'R800-800 = 0' else format-number(round((sum(Datenpunkt[RowCode = ('R800-382', 'R800-384', 'R800-480', 'R800-580', 'R800-680')]/Wert ! xs:decimal(.)) - sum(Datenpunkt[RowCode = 'R800-100']/Wert ! xs:decimal(.))) div $total * 100, 2), '#.##0,00', 'de') || ' %'</formula>

		<!-- PK.900.01 Anwartschafts- und Leistungsberechtigte: totals minus parts (should be 0) -->
		<formula table="PK.900.01" name="Diff_100">sum(Datenpunkt[RowCode = 'R900-100']/Wert ! xs:decimal(.)) - sum(Datenpunkt[RowCode = ('R900-110', 'R900-120')]/Wert ! xs:decimal(.))</formula>
		<formula table="PK.900.01" name="Diff_300">sum(Datenpunkt[RowCode = 'R900-300']/Wert ! xs:decimal(.)) - sum(Datenpunkt[RowCode = ('R900-310', 'R900-320')]/Wert ! xs:decimal(.))</formula>
	</xsl:variable>

	<xsl:template match="/Nationale_Meldung_pfq/*">
		<xsl:variable name="code" select="substring-before(local-name(), '_')"/>
		<xsl:variable name="mine" select="$catalog[@table eq $code][$filters or self::formula][$formulas or self::filter]"/>
		<xsl:copy>
			<xsl:apply-templates select="@*"/>
			<!-- the filter first (the grid uses the first one), then the formulas -->
			<xsl:for-each select="$mine[self::filter], $mine[self::formula]">
				<xsl:value-of select="$indent"/>
				<xsl:processing-instruction name="{if (self::filter) then 'fxt-filter' else 'fxt-formula'}"
					select="if (self::filter) then 'expression=&quot;' || fxt:escape(.) || '&quot;'
					        else 'name=&quot;' || fxt:escape(@name) || '&quot; store=&quot;false&quot; expression=&quot;' || fxt:escape(.) || '&quot;'"/>
			</xsl:for-each>
			<xsl:apply-templates select="node()"/>
		</xsl:copy>
	</xsl:template>

	<!-- the escaping of GridFormulas.escape: ">" too, the data must never contain "?>" -->
	<xsl:function name="fxt:escape" as="xs:string">
		<xsl:param name="s" as="xs:string?"/>
		<xsl:sequence select="replace(replace(replace(string($s), '&amp;', '&amp;amp;'), '&quot;', '&amp;quot;'), '&gt;', '&amp;gt;')"/>
	</xsl:function>
</xsl:stylesheet>
