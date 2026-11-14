<?xml version="1.0" encoding="UTF-8"?>
<!--
  PK-QMV rule check (PK-Meldewesen, Taxonomie 2.9.0 Hotfix 2), bundled with FreeXmlToolkit

  Input : Nationale_Meldung_pfq (FMA quarterly report of a Pensionskasse)
  Output: self-contained HTML report (dashboard -> findings -> all results -> rule catalogue)

  Processing runs in two phases:
    1. $results  - every check is evaluated once and written as a small result tree
                   (vrg / grp / row / c elements, see named template "vrg")
    2. rendering - dashboard, findings and the full result tables are all built from $results

  Rule ids follow the validation workbook "PK-Meldewesen_Validierungen_2.9.0_Hotfix2.xlsx"
  (Validation ID, e.g. PBV38, plus the legacy code, e.g. VDL010). Checks with an id INT_*
  are additional plausibility checks that are not part of the FMA rule set; the parameter
  "internal" switches them off.

  Parameter "resultsOnly" returns the result tree (element results) instead of the HTML report;
  FreeXmlToolkit reads it to list the findings with their lines.

  Requires an XSLT 3.0 processor (tested with Saxon HE 12).
-->
<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:map="http://www.w3.org/2005/xpath-functions/map"
                xmlns:pk="urn:freexmltoolkit:pk-meldewesen"
                exclude-result-prefixes="#all">

	<xsl:output method="html" html-version="5" encoding="UTF-8" indent="no"/>

	<!-- ===================================================================== -->
	<!-- Parameters                                                            -->
	<!-- ===================================================================== -->
	<!-- amount differences above this value are errors (EUR) -->
	<xsl:param name="tolerance" as="xs:decimal" select="3"/>
	<!-- amount differences above this value (but within tolerance) are warnings (EUR) -->
	<xsl:param name="toleranceWarning" as="xs:decimal" select="0.99"/>
	<!-- non-looked-through share of a single fund in % of the VRG (INT_LT2) -->
	<xsl:param name="lookThroughLimitAsset" as="xs:decimal" select="2"/>
	<!-- non-looked-through share of the whole VRG in % (INT_LT10) -->
	<xsl:param name="lookThroughLimitTotal" as="xs:decimal" select="10"/>
	<!-- maximum number of look-through findings listed per rule and VRG -->
	<xsl:param name="maxLookThroughRows" as="xs:integer" select="25"/>
	<!-- test interface: when set, the evaluated result tree is also written to this file (XML) -->
	<xsl:param name="resultsFile" as="xs:string?" select="()"/>
	<!-- false: leave out the additional plausibility checks (ids INT_*), which are not FMA rules -->
	<xsl:param name="internal" as="xs:boolean" select="true()"/>
	<!-- true: the output is the result tree (element results), not the HTML report -->
	<xsl:param name="resultsOnly" as="xs:boolean" select="false()"/>

	<xsl:decimal-format name="de" decimal-separator="," grouping-separator="."/>

	<!-- ===================================================================== -->
	<!-- Keys                                                                  -->
	<!-- ===================================================================== -->
	<!-- template element by "<template code>|<VRG>", e.g. "PK.800.01|2" -->
	<xsl:key name="tbl" match="/Nationale_Meldung_pfq/*" use="substring-before(local-name(), '_') || '|' || @VRG"/>
	<!-- closed templates: fact by "<template code>|<VRG>|<row code>" -->
	<xsl:key name="fact"
	         match="/Nationale_Meldung_pfq/*[starts-with(local-name(), 'PK.000.01_') or starts-with(local-name(), 'PK.800.01_') or starts-with(local-name(), 'PK.900.01_')]/*[RowCode]"
	         use="substring-before(local-name(..), '_') || '|' || ../@VRG || '|' || normalize-space(RowCode)"/>
	<!-- open lists by "<VRG>|<asset id>" -->
	<xsl:key name="pos" match="/Nationale_Meldung_pfq/*[starts-with(local-name(), 'PK.006.02.01.01_')]/Datenpunkt" use="../@VRG || '|' || Wert_C0010PK"/>
	<xsl:key name="info" match="/Nationale_Meldung_pfq/*[starts-with(local-name(), 'PK.006.02.01.02_')]/Datenpunkt" use="../@VRG || '|' || Wert_C0010PK"/>
	<xsl:key name="lt" match="/Nationale_Meldung_pfq/*[starts-with(local-name(), 'PK.006.03_')]/Datenpunkt" use="../@VRG || '|' || Wert_C0010PK"/>
	<xsl:key name="der1" match="/Nationale_Meldung_pfq/*[starts-with(local-name(), 'PK.008.01.01.01_')]/Datenpunkt" use="../@VRG || '|' || Wert_C0010PK"/>
	<xsl:key name="der2" match="/Nationale_Meldung_pfq/*[starts-with(local-name(), 'PK.008.01.01.02_')]/Datenpunkt" use="../@VRG || '|' || Wert_C0010PK"/>
	<!-- open lists by "<VRG>|<PNR>" -->
	<xsl:key name="posPnr" match="/Nationale_Meldung_pfq/*[starts-with(local-name(), 'PK.006.02.01.01_')]/Datenpunkt" use="../@VRG || '|' || Wert_C1120PK"/>
	<xsl:key name="ltPnr" match="/Nationale_Meldung_pfq/*[starts-with(local-name(), 'PK.006.03_')]/Datenpunkt" use="../@VRG || '|' || Wert_C1120PK"/>
	<!-- VDL080: all identifying fields of a position -->
	<xsl:key name="posSig" match="/Nationale_Meldung_pfq/*[starts-with(local-name(), 'PK.006.02.01.01_')]/Datenpunkt"
	         use="string-join((string(../@VRG), for $n in ('C0010PK','C0020PK','C0035','C0040','C0050','C0051PK','C0060','C0070','EC0141','C0075','C0080','C0090','C1100PK','C1110PK','C1120PK','C1130PK') return string(*[local-name() eq 'Wert_' || $n])), '|')"/>
	<xsl:key name="rule" match="rule" use="@id"/>

	<!-- ===================================================================== -->
	<!-- Static data                                                           -->
	<!-- ===================================================================== -->
	<xsl:variable name="doc" select="/"/>
	<xsl:variable name="root" select="/Nationale_Meldung_pfq"/>

	<!-- Formblatt 800: hundreds and their detail positions (V80004-V80008, V80020, VDL001-VDL006) -->
	<xsl:variable name="sub800" as="map(xs:string, xs:string*)" select="map {
		'100': ('R800-110','R800-130','R800-140','R800-160','R800-170','R800-180','R800-190'),
		'200': ('R800-210','R800-220','R800-230'),
		'300': ('R800-310','R800-315','R800-320','R800-325','R800-330','R800-335','R800-372','R800-374','R800-382','R800-384','R800-390'),
		'400': ('R800-410','R800-420','R800-430','R800-470','R800-480','R800-490'),
		'500': ('R800-510','R800-560','R800-570','R800-580','R800-590'),
		'600': ('R800-610','R800-620','R800-630','R800-670','R800-680','R800-690')}"/>
	<xsl:variable name="hundreds" as="xs:string*" select="('100','200','300','400','500','600')"/>
	<xsl:variable name="sumRule800" as="map(xs:string, xs:string)" select="map {'100':'PK_186','200':'PK_179','300':'PK_180','400':'PK_181','500':'PK_182','600':'PK_183'}"/>
	<xsl:variable name="ltRule800" as="map(xs:string, xs:string)" select="map {'100':'PK_201','200':'PK_202','300':'PK_203','400':'PK_204','500':'PK_205','600':'PK_206'}"/>
	<!-- VA00011: valid PNR in PK.006.02 -->
	<xsl:variable name="pnr800" as="xs:string*" select="for $h in $hundreds return $sub800($h)"/>
	<xsl:variable name="pnr300" as="xs:string*" select="for $p in $pnr800[. ne 'R800-160'] return replace($p, '^R800', 'R300')"/>
	<xsl:variable name="pnr100" as="xs:string*" select="('R100-131','R100-132','R100-133','R100-134','R100-135','R100-136','R100-137','R100-138','R100-139','R100-221','R100-222','R100-223','R100-224','R100-230')"/>
	<!-- VDL008: every R800 position up to 690 -->
	<xsl:variable name="pnr800All" as="xs:string*" select="($hundreds ! ('R800-' || .), $pnr800)"/>
	<!-- VDL074 / VDL078 filters on PK.006.03 -->
	<xsl:variable name="pnr074" as="xs:string*" select="('R800-110','R800-130','R800-140','R800-160','R300-110','R300-130','R300-140','R300-160')"/>
	<xsl:variable name="pnr078" as="xs:string*" select="for $c in ('170','180','190','372','374','382','384','390','470','480','490','570','580','590','670','680','690'), $f in ('R800-','R300-') return $f || $c"/>
	<!-- V80017 / V80021: rows compared across VRG -->
	<xsl:variable name="rows800Total" as="xs:string*" select="($pnr800All, for $c in ('800','810','820','830','835','840','850','851','852','853','854','855','856','857','858','859','860','861','862','863','864') return 'R800-' || $c)"/>
	<xsl:variable name="rows900" as="xs:string*" select="('R900-100','R900-110','R900-120','R900-300','R900-310','R900-320')"/>
	<!-- VA00023: sign convention of Formblatt 800 (zero is accepted for every rule) -->
	<xsl:variable name="sign40" as="xs:string*" select="for $c in ('110','130','180','200','210','220','230','310','315','320','325','330','335','382','384','410','420','430','480','510','580','610','620','630','680','840','850','851','852','853','854','855','856','858','860','861','862','863','864') return 'R800-' || $c"/>
	<xsl:variable name="sign41" as="xs:string*" select="for $c in ('140','170','372','374','470','560','570','670','857') return 'R800-' || $c"/>
	<!-- PK.000.01 rows that must be reported (PK_43) -->
	<xsl:variable name="rows000Required" as="xs:string*" select="('R000-100','R000-110','R000-120','R000-140','R000-150','R000-160','R000-170','R000-180','R000-200')"/>
	<!-- CIC sub categories accepted by VDL010 (positions 3-4 of the CIC) -->
	<xsl:variable name="cicValid" select="'11|12|13|14|15|16|17|18|19|21|22|23|24|25|26|27|28|29|31|32|33|34|39|41|42|43|44|45|46|47|48|49|51|52|53|54|55|56|57|58|59|61|62|63|64|65|66|67|68|69|71|72|73|74|75|79|81|82|84|85|86|87|88|89|91|92|93|94|95|96|99|09|A1|A2|A3|A5|A7|A8|A9|B1|B2|B3|B4|B5|B6|B7|B8|B9|C1|C2|C3|C4|C5|C6|C7|C8|C9|D1|D2|D3|D4|D5|D7|D8|D9|E1|E2|E7|E8|E9|F1|F2|F3|F4|F9'"/>
	<xsl:variable name="sectorsEsa" as="xs:string*" select="('S12K','S124','S120','S128','S129','S11','S13','S1M')"/>
	<xsl:variable name="bondCats" as="xs:string*" select="('1','2','5','6')"/>

	<xsl:variable name="grpOrder" as="xs:string*" select="('stamm','fb800','abgl','assets','lt','alloc','fb900')"/>
	<xsl:variable name="grpLabel" as="map(xs:string, xs:string)" select="map {
		'stamm': 'Stammdaten',
		'fb800': 'Formblatt 800',
		'abgl': 'Abstimmung 800 ↔ 006',
		'assets': 'List of Assets (006.02)',
		'lt': 'Look-through (006.03)',
		'alloc': 'Kennzahlen (zusätzlich)',
		'fb900': 'Formblatt 900',
		'gesamt': 'VRG-übergreifend'}"/>
	<xsl:variable name="artLabel" as="map(xs:string, xs:string)" select="map {
		'0': 'Summen-VRG', '1': 'VRG ohne Sub-VG', '2': 'VRG mit Sub-VG', '3': 'Sub-VG', '4': 'Sicherheits-VRG', '9': 'AG'}"/>

	<!-- reference date from GJ + Stichtag (Q1..Q4) -->
	<xsl:variable name="refDate" as="xs:date?" select="
		let $q := map {'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31'}($root/@Stichtag)
		return if (exists($q) and $root/@GJ castable as xs:integer) then xs:date($root/@GJ || $q) else ()"/>

	<!-- rule catalogue (generated from the validation workbook, plus the additional checks) -->
	<xsl:variable name="catalogAll">
		<catalog>
		<rule id="PK_43" old="" grp="stamm" sev="E" name="Meldepflicht" msg="Ein Row-code wurde nicht entsprechend der Vorzeichenkonvention gemeldet.">
			<note>Pflichtfelder der Stammdaten (R000-100, -110, -120, -140, -150, -160, -170, -180, -200) müssen gemeldet sein.</note>
			<cond>some $r in ('R000-100', 'R000-110', 'R000-120', 'R000-140', 'R000-150', 'R000-160', 'R000-170', 'R000-180', 'R000-200') satisfies not($has($fact('PK.000.01', $v, $r)/Wert))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) },
    $has006 := function($vrg) { exists(($doc/*/*[starts-with(name(), 'PK.006.02.01.01_')] | $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')])[@VRG = $vrg]/Datenpunkt) }
return for $t in $doc/*/*[starts-with(name(), 'PK.000.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if (some $r in ('R000-100', 'R000-110', 'R000-120', 'R000-140', 'R000-150', 'R000-160', 'R000-170', 'R000-180', 'R000-200') satisfies not($has($fact('PK.000.01', $v, $r)/Wert))) then $t else ()</xpath>
		</rule>
		<rule id="PK_64" old="VA00033" grp="stamm" sev="E" name="Art der VRG 0" msg="Bitte führen Sie die VRGART gemäß Technischen Handbuch korrekt an.">
			<note>Summen-VRG (VRG-Art 0): R000-100 muss 0 sein.</note>
			<cond>$art($v) = '0' and normalize-space($fact('PK.000.01', $v, 'R000-100')/Wert) ne '0'</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) },
    $has006 := function($vrg) { exists(($doc/*/*[starts-with(name(), 'PK.006.02.01.01_')] | $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')])[@VRG = $vrg]/Datenpunkt) }
return for $t in $doc/*/*[starts-with(name(), 'PK.000.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($art($v) = '0' and normalize-space($fact('PK.000.01', $v, 'R000-100')/Wert) ne '0') then $t else ()</xpath>
		</rule>
		<rule id="PK_65" old="VA00034" grp="stamm" sev="E" name="Art der VRG 999" msg="Bitte führen Sie die VRGART gemäß Technischen Handbuch korrekt an.">
			<note>AG (VRG-Art 9): R000-100 muss 999 sein.</note>
			<cond>$art($v) = '9' and normalize-space($fact('PK.000.01', $v, 'R000-100')/Wert) ne '999'</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) },
    $has006 := function($vrg) { exists(($doc/*/*[starts-with(name(), 'PK.006.02.01.01_')] | $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')])[@VRG = $vrg]/Datenpunkt) }
return for $t in $doc/*/*[starts-with(name(), 'PK.000.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($art($v) = '9' and normalize-space($fact('PK.000.01', $v, 'R000-100')/Wert) ne '999') then $t else ()</xpath>
		</rule>
		<rule id="PK_14" old="VA00022" grp="stamm" sev="E" name="Konsortialgeschäft" msg="Bei VRG 0 und VRG 999 kann kein Konsortialgeschäft bestehen.">
			<note>VRG 0 und VRG 999 (VRG-Art 0, 9): kein Konsortialgeschäft (R000-200 = N).</note>
			<cond>$art($v) = ('0', '9') and $kons ne 'N'</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) },
    $has006 := function($vrg) { exists(($doc/*/*[starts-with(name(), 'PK.006.02.01.01_')] | $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')])[@VRG = $vrg]/Datenpunkt) }
return for $t in $doc/*/*[starts-with(name(), 'PK.000.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($art($v) = ('0', '9') and $kons ne 'N') then $t else ()</xpath>
		</rule>
		<rule id="PK_12" old="VA00018" grp="stamm" sev="E" name="Konsortialgeschäft" msg="Die Konsortialquote muss kleiner Hundert sein.">
			<note>Konsortialgeschäft: Konsortialquote (R000-240) muss kleiner 1 (100 %) sein.</note>
			<cond>$kons = 'J' and not($val('PK.000.01', $v, 'R000-240') lt 1)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) },
    $has006 := function($vrg) { exists(($doc/*/*[starts-with(name(), 'PK.006.02.01.01_')] | $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')])[@VRG = $vrg]/Datenpunkt) }
return for $t in $doc/*/*[starts-with(name(), 'PK.000.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($kons = 'J' and not($val('PK.000.01', $v, 'R000-240') lt 1)) then $t else ()</xpath>
		</rule>
		<rule id="PK_13" old="VA00019" grp="stamm" sev="E" name="Konsortialgeschäft" msg="Die Veranlagungsquote muss kleiner gleich Hundert sein.">
			<note>Konsortialgeschäft: Veranlagungsquote (R000-250) muss kleiner gleich 1 (100 %) sein.</note>
			<cond>$kons = 'J' and not($val('PK.000.01', $v, 'R000-250') le 1)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) },
    $has006 := function($vrg) { exists(($doc/*/*[starts-with(name(), 'PK.006.02.01.01_')] | $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')])[@VRG = $vrg]/Datenpunkt) }
return for $t in $doc/*/*[starts-with(name(), 'PK.000.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($kons = 'J' and not($val('PK.000.01', $v, 'R000-250') le 1)) then $t else ()</xpath>
		</rule>
		<rule id="PK_60" old="VA00026" grp="stamm" sev="E" name="Konsortialgeschäft" msg="Bei Nichtbestehen eines Konsortialgeschäftes dürfen keine Merkmale zum Konsortialgeschäft befüllt werden.">
			<note>Kein Konsortialgeschäft: R000-210 bis R000-250 bleiben leer.</note>
			<cond>$kons = 'N' and (some $r in ('R000-210', 'R000-220', 'R000-230', 'R000-240', 'R000-250') satisfies $real($fact('PK.000.01', $v, $r)/Wert))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) },
    $has006 := function($vrg) { exists(($doc/*/*[starts-with(name(), 'PK.006.02.01.01_')] | $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')])[@VRG = $vrg]/Datenpunkt) }
return for $t in $doc/*/*[starts-with(name(), 'PK.000.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($kons = 'N' and (some $r in ('R000-210', 'R000-220', 'R000-230', 'R000-240', 'R000-250') satisfies $real($fact('PK.000.01', $v, $r)/Wert))) then $t else ()</xpath>
		</rule>
		<rule id="PK_61" old="VA00027" grp="stamm" sev="E" name="Gehört zu VRG" msg='Das Element "Gehört zu VRG" ist nur bei einer Sub-VG (VRGART = 3) zu befüllen.'>
			<note>VRG-Art 0, 1, 2, 4, 9: „Gehört zu VRG“ (R000-130) bleibt leer (nur bei Sub-VG).</note>
			<cond>$art($v) = ('0', '1', '2', '4', '9') and $real($fact('PK.000.01', $v, 'R000-130')/Wert)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) },
    $has006 := function($vrg) { exists(($doc/*/*[starts-with(name(), 'PK.006.02.01.01_')] | $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')])[@VRG = $vrg]/Datenpunkt) }
return for $t in $doc/*/*[starts-with(name(), 'PK.000.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($art($v) = ('0', '1', '2', '4', '9') and $real($fact('PK.000.01', $v, 'R000-130')/Wert)) then $t else ()</xpath>
		</rule>
		<rule id="PK_66" old="VA00035" grp="stamm" sev="E" name="DB-Quote" msg='Die DB-Quote darf nicht 0 oder 1 sein, sofern in VRG 0 bei der Art der Pensionszusage "MIX" gemeldet wird.'>
			<note>VRG 0 mit Pensionszusage MIX: DB-Quote (Attribut DB) größer 0 und kleiner 1.</note>
			<cond>$art($v) = '0' and normalize-space($fact('PK.000.01', $v, 'R000-140')/Wert) = 'MIX' and not($num(/*/@DB) gt 0 and $num(/*/@DB) lt 1)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) },
    $has006 := function($vrg) { exists(($doc/*/*[starts-with(name(), 'PK.006.02.01.01_')] | $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')])[@VRG = $vrg]/Datenpunkt) }
return for $t in $doc/*/*[starts-with(name(), 'PK.000.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($art($v) = '0' and normalize-space($fact('PK.000.01', $v, 'R000-140')/Wert) = 'MIX' and not($num($doc/*/@DB) gt 0 and $num($doc/*/@DB) lt 1)) then $t else ()</xpath>
		</rule>
		<rule id="PK_67" old="VA00042" grp="stamm" sev="E" name="DB-Quote" msg='Die DB-Quote muss 1 sein, sofern in VRG 0 bei der Art der Pensionszusage "LO" gemeldet wird.'>
			<note>VRG 0 mit Pensionszusage LO: DB-Quote muss 1 sein.</note>
			<cond>$art($v) = '0' and normalize-space($fact('PK.000.01', $v, 'R000-140')/Wert) = 'LO' and $num(/*/@DB) ne 1</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) },
    $has006 := function($vrg) { exists(($doc/*/*[starts-with(name(), 'PK.006.02.01.01_')] | $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')])[@VRG = $vrg]/Datenpunkt) }
return for $t in $doc/*/*[starts-with(name(), 'PK.000.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($art($v) = '0' and normalize-space($fact('PK.000.01', $v, 'R000-140')/Wert) = 'LO' and $num($doc/*/@DB) ne 1) then $t else ()</xpath>
		</rule>
		<rule id="PK_68" old="VA00043" grp="stamm" sev="E" name="DB-Quote" msg='Die DB-Quote muss 0 sein, sofern in VRG 0 bei der Art der Pensionszusage "BO" gemeldet wird.'>
			<note>VRG 0 mit Pensionszusage BO: DB-Quote muss 0 sein.</note>
			<cond>$art($v) = '0' and normalize-space($fact('PK.000.01', $v, 'R000-140')/Wert) = 'BO' and $num(/*/@DB) ne 0</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) },
    $has006 := function($vrg) { exists(($doc/*/*[starts-with(name(), 'PK.006.02.01.01_')] | $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')])[@VRG = $vrg]/Datenpunkt) }
return for $t in $doc/*/*[starts-with(name(), 'PK.000.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($art($v) = '0' and normalize-space($fact('PK.000.01', $v, 'R000-140')/Wert) = 'BO' and $num($doc/*/@DB) ne 0) then $t else ()</xpath>
		</rule>
		<rule id="PK_3_1" old="VA00012" grp="stamm" sev="E" name="Sub-VG, VRG" msg="Die Sub-VG bzw. VRG hat kein Meldetemplate PK.006.02 gemeldet.">
			<note>VRG-Art 1, 3, 4, 9 mit Vermögen (PNR 800 &gt; 0), nicht konsortial oder mit Veranlagungsquote &gt; 0: PK.006.02 muss gemeldet sein.</note>
			<cond>$art($v) = ('1', '3', '4', '9') and $val('PK.800.01', $v, 'R800-800') gt 0 and ($kons = 'N' or $val('PK.000.01', $v, 'R000-250') gt 0) and not($has006($v))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) },
    $has006 := function($vrg) { exists(($doc/*/*[starts-with(name(), 'PK.006.02.01.01_')] | $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')])[@VRG = $vrg]/Datenpunkt) }
return for $t in $doc/*/*[starts-with(name(), 'PK.000.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($art($v) = ('1', '3', '4', '9') and $val('PK.800.01', $v, 'R800-800') gt 0 and ($kons = 'N' or $val('PK.000.01', $v, 'R000-250') gt 0) and not($has006($v))) then $t else ()</xpath>
		</rule>
		<rule id="PK_3_2" old="" grp="stamm" sev="E" name="Sub-VG, VRG" msg="Die Sub-VG bzw. VRG hat kein Meldetemplate PK.800.01 gemeldet.">
			<note>VRG-Art 0–4: PK.800.01 muss gemeldet sein.</note>
			<cond>$art($v) = ('0', '1', '2', '3', '4') and empty(/*/*[starts-with(name(), 'PK.800.01_')][@VRG = $v])</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) },
    $has006 := function($vrg) { exists(($doc/*/*[starts-with(name(), 'PK.006.02.01.01_')] | $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')])[@VRG = $vrg]/Datenpunkt) }
return for $t in $doc/*/*[starts-with(name(), 'PK.000.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($art($v) = ('0', '1', '2', '3', '4') and empty($doc/*/*[starts-with(name(), 'PK.800.01_')][@VRG = $v])) then $t else ()</xpath>
		</rule>
		<rule id="PK_3_3" old="" grp="stamm" sev="E" name="Sub-VG, VRG" msg="Die Sub-VG bzw. VRG hat kein Meldetemplate PK.900.01 gemeldet.">
			<note>VRG-Art 0–4: PK.900.01 muss gemeldet sein.</note>
			<cond>$art($v) = ('0', '1', '2', '3', '4') and empty(/*/*[starts-with(name(), 'PK.900.01_')][@VRG = $v])</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) },
    $has006 := function($vrg) { exists(($doc/*/*[starts-with(name(), 'PK.006.02.01.01_')] | $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')])[@VRG = $vrg]/Datenpunkt) }
return for $t in $doc/*/*[starts-with(name(), 'PK.000.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($art($v) = ('0', '1', '2', '3', '4') and empty($doc/*/*[starts-with(name(), 'PK.900.01_')][@VRG = $v])) then $t else ()</xpath>
		</rule>
		<rule id="PK_4" old="VA00013" grp="stamm" sev="E" name="Konsortialgeschäft" msg="Es ist kein Meldetemplate PK.006.02 für eine konsortialgeführte VRG oder Sub-VG erforderlich, wo die Veranlagungsquote = „0.00“ ist.">
			<note>Konsortialgeschäft mit Veranlagungsquote 0: kein PK.006.02.</note>
			<cond>$kons = 'J' and $val('PK.000.01', $v, 'R000-250') = 0 and $has006($v)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) },
    $has006 := function($vrg) { exists(($doc/*/*[starts-with(name(), 'PK.006.02.01.01_')] | $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')])[@VRG = $vrg]/Datenpunkt) }
return for $t in $doc/*/*[starts-with(name(), 'PK.000.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($kons = 'J' and $val('PK.000.01', $v, 'R000-250') = 0 and $has006($v)) then $t else ()</xpath>
		</rule>
		<rule id="PK_5" old="VA00014" grp="stamm" sev="E" name="VRG mit Sub-VG" msg="Handelt es sich um eine VRG mit Sub-VG, so ist für die VRG mit Sub-VG kein Meldetemplate PK.006.02 zu melden.">
			<note>VRG mit Sub-VG (VRG-Art 2): kein PK.006.02 (die Sub-VG melden).</note>
			<cond>$art($v) = '2' and $has006($v)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) },
    $has006 := function($vrg) { exists(($doc/*/*[starts-with(name(), 'PK.006.02.01.01_')] | $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')])[@VRG = $vrg]/Datenpunkt) }
return for $t in $doc/*/*[starts-with(name(), 'PK.000.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($art($v) = '2' and $has006($v)) then $t else ()</xpath>
		</rule>
		<rule id="PK_6" old="VA00015" grp="stamm" sev="E" name="Kein Vermögen wird verwaltet." msg="Sofern zum Meldestichtag noch kein Vermögen in der VRG, Sub-VG oder Sicherheits-VRG verwaltet wird, ist kein Meldetemplate PK.006.02 erforderlich. Sofern zum Meldestichtag noch kein Vermögen in der VRG, Sub-VG oder Sicherheits-VRG verwaltet wird, ist kein Meldetemplate PK.006.02 erforderlich.">
			<note>PNR 800 = 0 (kein Vermögen): kein PK.006.02.</note>
			<cond>exists($fact('PK.800.01', $v, 'R800-800')) and $val('PK.800.01', $v, 'R800-800') = 0 and $has006($v)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) },
    $has006 := function($vrg) { exists(($doc/*/*[starts-with(name(), 'PK.006.02.01.01_')] | $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')])[@VRG = $vrg]/Datenpunkt) }
return for $t in $doc/*/*[starts-with(name(), 'PK.000.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if (exists($fact('PK.800.01', $v, 'R800-800')) and $val('PK.800.01', $v, 'R800-800') = 0 and $has006($v)) then $t else ()</xpath>
		</rule>
		<rule id="PK_75" old="VA00051" grp="stamm" sev="E" name="PK.006.02 VRG 999" msg="Für die VRG 999 ist Meldetemplate PK.006.02 zu melden.">
			<note>AG (VRG-Art 9) mit Konsortialquote &gt; 0: kein PK.006.02.</note>
			<cond>$val('PK.000.01', $v, 'R000-240') gt 0 and $art($v) = '9' and $has006($v)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) },
    $has006 := function($vrg) { exists(($doc/*/*[starts-with(name(), 'PK.006.02.01.01_')] | $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')])[@VRG = $vrg]/Datenpunkt) }
return for $t in $doc/*/*[starts-with(name(), 'PK.000.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($val('PK.000.01', $v, 'R000-240') gt 0 and $art($v) = '9' and $has006($v)) then $t else ()</xpath>
		</rule>
		<rule id="PK_56" old="V80022" grp="fb800" sev="E" name="Meldepflicht" msg="Eine im Formblatt 800 verpflichtend zu meldende Position wurde nicht gemeldet.">
			<note>PNR 800 muss gemeldet sein.</note>
			<cond>empty($fact('PK.800.01', $v, 'R800-800'))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.800.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if (empty($fact('PK.800.01', $v, 'R800-800'))) then $t else ()</xpath>
		</rule>
		<rule id="PK_57" old="V80022" grp="fb800" sev="E" name="Meldepflicht" msg="Eine im Formblatt 800 verpflichtend zu meldende Position wurde nicht gemeldet.">
			<note>PNR 810 und 820 müssen gemeldet sein.</note>
			<cond>empty($fact('PK.800.01', $v, 'R800-810')) or empty($fact('PK.800.01', $v, 'R800-820'))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.800.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if (empty($fact('PK.800.01', $v, 'R800-810')) or empty($fact('PK.800.01', $v, 'R800-820'))) then $t else ()</xpath>
		</rule>
		<rule id="PK_40" old="VA00023" grp="fb800" sev="E" name="Vorzeichen" msg="Ein Row-code wurde nicht entsprechend der Vorzeichenkonvention gemeldet.">
			<note>Vorzeichen: diese Positionen müssen positiv sein (110, 130, 180, 200, 210, 220, 230, 310, 315, 320, 325, 330, 335, 382, 384, 410, 420, 430, 480, 510, 580, 610, 620, 630, 680, 840, 850, 851, 852, 853, 854, 855, 856, 858, 860, 861, 862, 863, 864). (0 wird akzeptiert.)</note>
			<cond>normalize-space(RowCode) = ('R800-110', 'R800-130', 'R800-180', 'R800-200', 'R800-210', 'R800-220', 'R800-230', 'R800-310', 'R800-315', 'R800-320', 'R800-325', 'R800-330', 'R800-335', 'R800-382', 'R800-384', 'R800-410', 'R800-420', 'R800-430', 'R800-480', 'R800-510', 'R800-580', 'R800-610', 'R800-620', 'R800-630', 'R800-680', 'R800-840', 'R800-850', 'R800-851', 'R800-852', 'R800-853', 'R800-854', 'R800-855', 'R800-856', 'R800-858', 'R800-860', 'R800-861', 'R800-862', 'R800-863', 'R800-864') and $num(Wert) lt 0</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.800.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return $t/*[normalize-space(RowCode) = ('R800-110', 'R800-130', 'R800-180', 'R800-200', 'R800-210', 'R800-220', 'R800-230', 'R800-310', 'R800-315', 'R800-320', 'R800-325', 'R800-330', 'R800-335', 'R800-382', 'R800-384', 'R800-410', 'R800-420', 'R800-430', 'R800-480', 'R800-510', 'R800-580', 'R800-610', 'R800-620', 'R800-630', 'R800-680', 'R800-840', 'R800-850', 'R800-851', 'R800-852', 'R800-853', 'R800-854', 'R800-855', 'R800-856', 'R800-858', 'R800-860', 'R800-861', 'R800-862', 'R800-863', 'R800-864') and $num(Wert) lt 0]</xpath>
		</rule>
		<rule id="PK_41" old="VA00023" grp="fb800" sev="E" name="Vorzeichen" msg="Ein Row-code wurde nicht entsprechend der Vorzeichenkonvention gemeldet.">
			<note>Vorzeichen: diese Positionen müssen negativ sein (140, 170, 372, 374, 470, 560, 570, 670, 857). (0 wird akzeptiert.)</note>
			<cond>normalize-space(RowCode) = ('R800-140', 'R800-170', 'R800-372', 'R800-374', 'R800-470', 'R800-560', 'R800-570', 'R800-670', 'R800-857') and $num(Wert) gt 0</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.800.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return $t/*[normalize-space(RowCode) = ('R800-140', 'R800-170', 'R800-372', 'R800-374', 'R800-470', 'R800-560', 'R800-570', 'R800-670', 'R800-857') and $num(Wert) gt 0]</xpath>
		</rule>
		<rule id="PK_42_1" old="VA00023" grp="fb800" sev="W" name="Vorzeichen" msg="Ein Row-code wurde nicht entsprechend der Vorzeichenkonvention gemeldet.">
			<note>Vorzeichen: PNR 830 und 835 müssen positiv sein (Non-Blocking). (0 wird akzeptiert.)</note>
			<cond>normalize-space(RowCode) = ('R800-830', 'R800-835') and $num(Wert) lt 0</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.800.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return $t/*[normalize-space(RowCode) = ('R800-830', 'R800-835') and $num(Wert) lt 0]</xpath>
		</rule>
		<rule id="PK_42_2" old="VA00023" grp="fb800" sev="E" name="Vorzeichen" msg="Ein Row-code wurde nicht entsprechend der Vorzeichenkonvention gemeldet.">
			<note>Vorzeichen: PNR 800 und 820 dürfen nicht negativ sein. (0 wird akzeptiert.)</note>
			<cond>normalize-space(RowCode) = ('R800-800', 'R800-820') and $num(Wert) lt 0</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.800.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return $t/*[normalize-space(RowCode) = ('R800-800', 'R800-820') and $num(Wert) lt 0]</xpath>
		</rule>
		<rule id="PK_42_3" old="VA00023" grp="fb800" sev="W" name="Vorzeichen" msg="Ein Row-code wurde nicht entsprechend der Vorzeichenkonvention gemeldet.">
			<note>Vorzeichen: PNR 810 darf nicht negativ sein (Non-Blocking). (0 wird akzeptiert.)</note>
			<cond>normalize-space(RowCode) = ('R800-810') and $num(Wert) lt 0</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.800.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return $t/*[normalize-space(RowCode) = ('R800-810') and $num(Wert) lt 0]</xpath>
		</rule>
		<rule id="PK_179" old="V80004" grp="fb800" sev="E" name="Summe Darlehen und Kredite" msg="Summe Darlehen und Kredite ungleich Summe Einzelpositionen">
			<note>PNR 200 = Summe der PNR 210 + 220 + 230 (Fehler über 3 €, Warnung über 0,99 €).</note>
			<cond>abs($val('PK.800.01', $v, 'R800-200') - sum(for $r in ('R800-210', 'R800-220', 'R800-230') return $val('PK.800.01', $v, $r))) gt 0.99</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.800.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if (exists($t/*[normalize-space(RowCode) = ('R800-200', 'R800-210', 'R800-220', 'R800-230')]) and abs($val('PK.800.01', $v, 'R800-200') - sum(for $r in ('R800-210', 'R800-220', 'R800-230') return $val('PK.800.01', $v, $r))) gt 0.99)
       then ($fact('PK.800.01', $v, 'R800-200'), $t)[1] else ()</xpath>
		</rule>
		<rule id="PK_180" old="V80005" grp="fb800" sev="E" name="Summe Schuldverschreibungen" msg="Summe Schuldverschreibungen ungleich Summe Einzelpositionen">
			<note>PNR 300 = Summe der PNR 310 + 315 + 320 + 325 + 330 + 335 + 372 + 374 + 382 + 384 + 390 (Fehler über 3 €, Warnung über 0,99 €).</note>
			<cond>abs($val('PK.800.01', $v, 'R800-300') - sum(for $r in ('R800-310', 'R800-315', 'R800-320', 'R800-325', 'R800-330', 'R800-335', 'R800-372', 'R800-374', 'R800-382', 'R800-384', 'R800-390') return $val('PK.800.01', $v, $r))) gt 0.99</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.800.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if (exists($t/*[normalize-space(RowCode) = ('R800-300', 'R800-310', 'R800-315', 'R800-320', 'R800-325', 'R800-330', 'R800-335', 'R800-372', 'R800-374', 'R800-382', 'R800-384', 'R800-390')]) and abs($val('PK.800.01', $v, 'R800-300') - sum(for $r in ('R800-310', 'R800-315', 'R800-320', 'R800-325', 'R800-330', 'R800-335', 'R800-372', 'R800-374', 'R800-382', 'R800-384', 'R800-390') return $val('PK.800.01', $v, $r))) gt 0.99)
       then ($fact('PK.800.01', $v, 'R800-300'), $t)[1] else ()</xpath>
		</rule>
		<rule id="PK_181" old="V80006" grp="fb800" sev="E" name="Summe Aktien" msg="Summe Aktien ungleich Summe Einzelpositionen">
			<note>PNR 400 = Summe der PNR 410 + 420 + 430 + 470 + 480 + 490 (Fehler über 3 €, Warnung über 0,99 €).</note>
			<cond>abs($val('PK.800.01', $v, 'R800-400') - sum(for $r in ('R800-410', 'R800-420', 'R800-430', 'R800-470', 'R800-480', 'R800-490') return $val('PK.800.01', $v, $r))) gt 0.99</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.800.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if (exists($t/*[normalize-space(RowCode) = ('R800-400', 'R800-410', 'R800-420', 'R800-430', 'R800-470', 'R800-480', 'R800-490')]) and abs($val('PK.800.01', $v, 'R800-400') - sum(for $r in ('R800-410', 'R800-420', 'R800-430', 'R800-470', 'R800-480', 'R800-490') return $val('PK.800.01', $v, $r))) gt 0.99)
       then ($fact('PK.800.01', $v, 'R800-400'), $t)[1] else ()</xpath>
		</rule>
		<rule id="PK_182" old="V80007" grp="fb800" sev="E" name="Summe Immobilien" msg="Summe Immobilien ungleich Summe Einzelpositionen">
			<note>PNR 500 = Summe der PNR 510 + 560 + 570 + 580 + 590 (Fehler über 3 €, Warnung über 0,99 €).</note>
			<cond>abs($val('PK.800.01', $v, 'R800-500') - sum(for $r in ('R800-510', 'R800-560', 'R800-570', 'R800-580', 'R800-590') return $val('PK.800.01', $v, $r))) gt 0.99</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.800.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if (exists($t/*[normalize-space(RowCode) = ('R800-500', 'R800-510', 'R800-560', 'R800-570', 'R800-580', 'R800-590')]) and abs($val('PK.800.01', $v, 'R800-500') - sum(for $r in ('R800-510', 'R800-560', 'R800-570', 'R800-580', 'R800-590') return $val('PK.800.01', $v, $r))) gt 0.99)
       then ($fact('PK.800.01', $v, 'R800-500'), $t)[1] else ()</xpath>
		</rule>
		<rule id="PK_183" old="V80008" grp="fb800" sev="E" name="Summe Sonstige Vermögenswerte" msg="Summe Sonstige Vermögenswerte ungleich Summe Einzelpositionen">
			<note>PNR 600 = Summe der PNR 610 + 620 + 630 + 670 + 680 + 690 (Fehler über 3 €, Warnung über 0,99 €).</note>
			<cond>abs($val('PK.800.01', $v, 'R800-600') - sum(for $r in ('R800-610', 'R800-620', 'R800-630', 'R800-670', 'R800-680', 'R800-690') return $val('PK.800.01', $v, $r))) gt 0.99</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.800.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if (exists($t/*[normalize-space(RowCode) = ('R800-600', 'R800-610', 'R800-620', 'R800-630', 'R800-670', 'R800-680', 'R800-690')]) and abs($val('PK.800.01', $v, 'R800-600') - sum(for $r in ('R800-610', 'R800-620', 'R800-630', 'R800-670', 'R800-680', 'R800-690') return $val('PK.800.01', $v, $r))) gt 0.99)
       then ($fact('PK.800.01', $v, 'R800-600'), $t)[1] else ()</xpath>
		</rule>
		<rule id="PK_184" old="V80009" grp="fb800" sev="E" name="Summe Vermögen" msg="Summe Vermögen ungleich Summe der Summenpositionen">
			<note>PNR 800 (Summe Vermögen) = PNR 100 + 200 + 300 + 400 + 500 + 600, wenn PNR 800 &gt; 0.</note>
			<cond>abs($val('PK.800.01', $v, 'R800-800') - sum(for $h in ('R800-100', 'R800-200', 'R800-300', 'R800-400', 'R800-500', 'R800-600') return $val('PK.800.01', $v, $h))) gt 0.99</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.800.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($val('PK.800.01', $v, 'R800-800') gt 0 and abs($val('PK.800.01', $v, 'R800-800') - sum(for $h in ('R800-100', 'R800-200', 'R800-300', 'R800-400', 'R800-500', 'R800-600') return $val('PK.800.01', $v, $h))) gt 0.99) then ($fact('PK.800.01', $v, 'R800-800'), $t)[1] else ()</xpath>
		</rule>
		<rule id="PK_186" old="V80020" grp="fb800" sev="E" name="Summe Guthaben bei Kreditinstituten" msg="Summe Guthaben bei Kreditinstituten ungleich Summe Einzelpositionen">
			<note>PNR 100 = Summe der PNR 110 + 130 + 140 + 160 + 170 + 180 + 190 (Fehler über 3 €, Warnung über 0,99 €).</note>
			<cond>abs($val('PK.800.01', $v, 'R800-100') - sum(for $r in ('R800-110', 'R800-130', 'R800-140', 'R800-160', 'R800-170', 'R800-180', 'R800-190') return $val('PK.800.01', $v, $r))) gt 0.99</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.800.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if (exists($t/*[normalize-space(RowCode) = ('R800-100', 'R800-110', 'R800-130', 'R800-140', 'R800-160', 'R800-170', 'R800-180', 'R800-190')]) and abs($val('PK.800.01', $v, 'R800-100') - sum(for $r in ('R800-110', 'R800-130', 'R800-140', 'R800-160', 'R800-170', 'R800-180', 'R800-190') return $val('PK.800.01', $v, $r))) gt 0.99)
       then ($fact('PK.800.01', $v, 'R800-100'), $t)[1] else ()</xpath>
		</rule>
		<rule id="PK_201" old="VDL001" grp="abgl" sev="E" name="Summe Guthaben bei Kreditinstituten" msg="Summe Guthaben bei Kreditinstituten ungleich Summe Einzelpositionen der List of Assets und Look-Through (Basis = Bilanzwert)">
			<note>VRG-Art 1, 3, 4, nicht konsortial: PNR 100 = Summe Bilanzwerte (C1100PK) aus PK.006.02 und PK.006.03 mit PNR 110, 130, 140, 160, 170, 180, 190.</note>
			<cond>abs($val('PK.800.01', $v, 'R800-100') - $sumPnr($v, ('R800-110', 'R800-130', 'R800-140', 'R800-160', 'R800-170', 'R800-180', 'R800-190'))) gt 0.99</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.800.01_')]
return let $v := string($t/@VRG),
    $sumPnr := function($v, $pnrs) { sum(for $x in ($doc/*/*[starts-with(name(), 'PK.006.02.01.01_')] | $doc/*/*[starts-with(name(), 'PK.006.03_')])[@VRG = $v]/Datenpunkt[normalize-space(Wert_C1120PK) = $pnrs]/Wert_C1100PK return $num($x)) },
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($art($v) = ('1', '3', '4') and $kons = 'N' and abs($val('PK.800.01', $v, 'R800-100') - $sumPnr($v, ('R800-110', 'R800-130', 'R800-140', 'R800-160', 'R800-170', 'R800-180', 'R800-190'))) gt 0.99) then ($fact('PK.800.01', $v, 'R800-100'), $t)[1] else ()</xpath>
		</rule>
		<rule id="PK_202" old="VDL002" grp="abgl" sev="E" name="Summe Darlehen und Kredite" msg="Summe Darlehen und Kredite ungleich Summe Einzelpositionen der List of Assets und Look-Through (Basis = Bilanzwert)">
			<note>VRG-Art 1, 3, 4, nicht konsortial: PNR 200 = Summe Bilanzwerte (C1100PK) aus PK.006.02 und PK.006.03 mit PNR 210, 220, 230.</note>
			<cond>abs($val('PK.800.01', $v, 'R800-200') - $sumPnr($v, ('R800-210', 'R800-220', 'R800-230'))) gt 0.99</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.800.01_')]
return let $v := string($t/@VRG),
    $sumPnr := function($v, $pnrs) { sum(for $x in ($doc/*/*[starts-with(name(), 'PK.006.02.01.01_')] | $doc/*/*[starts-with(name(), 'PK.006.03_')])[@VRG = $v]/Datenpunkt[normalize-space(Wert_C1120PK) = $pnrs]/Wert_C1100PK return $num($x)) },
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($art($v) = ('1', '3', '4') and $kons = 'N' and abs($val('PK.800.01', $v, 'R800-200') - $sumPnr($v, ('R800-210', 'R800-220', 'R800-230'))) gt 0.99) then ($fact('PK.800.01', $v, 'R800-200'), $t)[1] else ()</xpath>
		</rule>
		<rule id="PK_203" old="VDL003" grp="abgl" sev="E" name="Summe Schuldverschreibungen" msg="Summe Schuldverschreibungen ungleich Summe Einzelpositionen der List of Assets und Look-Through (Basis = Bilanzwert)">
			<note>VRG-Art 1, 3, 4, nicht konsortial: PNR 300 = Summe Bilanzwerte (C1100PK) aus PK.006.02 und PK.006.03 mit PNR 310, 315, 320, 325, 330, 335, 372, 374, 382, 384, 390.</note>
			<cond>abs($val('PK.800.01', $v, 'R800-300') - $sumPnr($v, ('R800-310', 'R800-315', 'R800-320', 'R800-325', 'R800-330', 'R800-335', 'R800-372', 'R800-374', 'R800-382', 'R800-384', 'R800-390'))) gt 0.99</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.800.01_')]
return let $v := string($t/@VRG),
    $sumPnr := function($v, $pnrs) { sum(for $x in ($doc/*/*[starts-with(name(), 'PK.006.02.01.01_')] | $doc/*/*[starts-with(name(), 'PK.006.03_')])[@VRG = $v]/Datenpunkt[normalize-space(Wert_C1120PK) = $pnrs]/Wert_C1100PK return $num($x)) },
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($art($v) = ('1', '3', '4') and $kons = 'N' and abs($val('PK.800.01', $v, 'R800-300') - $sumPnr($v, ('R800-310', 'R800-315', 'R800-320', 'R800-325', 'R800-330', 'R800-335', 'R800-372', 'R800-374', 'R800-382', 'R800-384', 'R800-390'))) gt 0.99) then ($fact('PK.800.01', $v, 'R800-300'), $t)[1] else ()</xpath>
		</rule>
		<rule id="PK_204" old="VDL004" grp="abgl" sev="E" name="Summe Aktien" msg="Summe Aktien ungleich Summe Einzelpositionen der List of Assets und Look-Through (Basis = Bilanzwert)">
			<note>VRG-Art 1, 3, 4, nicht konsortial: PNR 400 = Summe Bilanzwerte (C1100PK) aus PK.006.02 und PK.006.03 mit PNR 410, 420, 430, 470, 480, 490.</note>
			<cond>abs($val('PK.800.01', $v, 'R800-400') - $sumPnr($v, ('R800-410', 'R800-420', 'R800-430', 'R800-470', 'R800-480', 'R800-490'))) gt 0.99</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.800.01_')]
return let $v := string($t/@VRG),
    $sumPnr := function($v, $pnrs) { sum(for $x in ($doc/*/*[starts-with(name(), 'PK.006.02.01.01_')] | $doc/*/*[starts-with(name(), 'PK.006.03_')])[@VRG = $v]/Datenpunkt[normalize-space(Wert_C1120PK) = $pnrs]/Wert_C1100PK return $num($x)) },
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($art($v) = ('1', '3', '4') and $kons = 'N' and abs($val('PK.800.01', $v, 'R800-400') - $sumPnr($v, ('R800-410', 'R800-420', 'R800-430', 'R800-470', 'R800-480', 'R800-490'))) gt 0.99) then ($fact('PK.800.01', $v, 'R800-400'), $t)[1] else ()</xpath>
		</rule>
		<rule id="PK_205" old="VDL005" grp="abgl" sev="E" name="Summe Immobilien" msg="Summe Immobilien ungleich Summe Einzelpositionen der List of Assets und Look-Through (Basis = Bilanzwert)">
			<note>VRG-Art 1, 3, 4, nicht konsortial: PNR 500 = Summe Bilanzwerte (C1100PK) aus PK.006.02 und PK.006.03 mit PNR 510, 560, 570, 580, 590.</note>
			<cond>abs($val('PK.800.01', $v, 'R800-500') - $sumPnr($v, ('R800-510', 'R800-560', 'R800-570', 'R800-580', 'R800-590'))) gt 0.99</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.800.01_')]
return let $v := string($t/@VRG),
    $sumPnr := function($v, $pnrs) { sum(for $x in ($doc/*/*[starts-with(name(), 'PK.006.02.01.01_')] | $doc/*/*[starts-with(name(), 'PK.006.03_')])[@VRG = $v]/Datenpunkt[normalize-space(Wert_C1120PK) = $pnrs]/Wert_C1100PK return $num($x)) },
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($art($v) = ('1', '3', '4') and $kons = 'N' and abs($val('PK.800.01', $v, 'R800-500') - $sumPnr($v, ('R800-510', 'R800-560', 'R800-570', 'R800-580', 'R800-590'))) gt 0.99) then ($fact('PK.800.01', $v, 'R800-500'), $t)[1] else ()</xpath>
		</rule>
		<rule id="PK_206" old="VDL006" grp="abgl" sev="E" name="Summe Sonstige Vermögenswerte" msg="Summe Sonstige Vermögenswerte ungleich Summe Einzelpositionen der List of Assets und Look-Through (Basis = Bilanzwert)">
			<note>VRG-Art 1, 3, 4, nicht konsortial: PNR 600 = Summe Bilanzwerte (C1100PK) aus PK.006.02 und PK.006.03 mit PNR 610, 620, 630, 670, 680, 690.</note>
			<cond>abs($val('PK.800.01', $v, 'R800-600') - $sumPnr($v, ('R800-610', 'R800-620', 'R800-630', 'R800-670', 'R800-680', 'R800-690'))) gt 0.99</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.800.01_')]
return let $v := string($t/@VRG),
    $sumPnr := function($v, $pnrs) { sum(for $x in ($doc/*/*[starts-with(name(), 'PK.006.02.01.01_')] | $doc/*/*[starts-with(name(), 'PK.006.03_')])[@VRG = $v]/Datenpunkt[normalize-space(Wert_C1120PK) = $pnrs]/Wert_C1100PK return $num($x)) },
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($art($v) = ('1', '3', '4') and $kons = 'N' and abs($val('PK.800.01', $v, 'R800-600') - $sumPnr($v, ('R800-610', 'R800-620', 'R800-630', 'R800-670', 'R800-680', 'R800-690'))) gt 0.99) then ($fact('PK.800.01', $v, 'R800-600'), $t)[1] else ()</xpath>
		</rule>
		<rule id="PK_207" old="VDL007" grp="abgl" sev="E" name="Summencheck pro VRG / Sub-VG pro Positionsnummer" msg="Summe Einzelpositionen der List of Assets und Look-Through ungleich Einzelposition Vermögensausweis (Basis = Bilanzwert)">
			<note>VRG-Art 1, 3, 4, nicht konsortial: jede Detailposition des Formblatts 800 = Summe Bilanzwerte (C1100PK) aus PK.006.02 und PK.006.03 mit dieser PNR.</note>
			<cond>abs(Wert der PNR - $sumPnr($v, PNR)) gt 0.99</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.800.01_')]
return let $v := string($t/@VRG),
    $sumPnr := function($v, $pnrs) { sum(for $x in ($doc/*/*[starts-with(name(), 'PK.006.02.01.01_')] | $doc/*/*[starts-with(name(), 'PK.006.03_')])[@VRG = $v]/Datenpunkt[normalize-space(Wert_C1120PK) = $pnrs]/Wert_C1100PK return $num($x)) },
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($art($v) = ('1', '3', '4') and $kons = 'N') then (for $pn in ('R800-110', 'R800-130', 'R800-140', 'R800-160', 'R800-170', 'R800-180', 'R800-190', 'R800-210', 'R800-220', 'R800-230', 'R800-310', 'R800-315', 'R800-320', 'R800-325', 'R800-330', 'R800-335', 'R800-372', 'R800-374', 'R800-382', 'R800-384', 'R800-390', 'R800-410', 'R800-420', 'R800-430', 'R800-470', 'R800-480', 'R800-490', 'R800-510', 'R800-560', 'R800-570', 'R800-580', 'R800-590', 'R800-610', 'R800-620', 'R800-630', 'R800-670', 'R800-680', 'R800-690')
         return let $soll := $sumPnr($v, $pn)
         return if ((exists($fact('PK.800.01', $v, $pn)) or $soll ne 0) and abs($val('PK.800.01', $v, $pn) - $soll) gt 0.99)
                then ($fact('PK.800.01', $v, $pn),
                      ($doc/*/*[starts-with(name(), 'PK.006.02.01.01_')] | $doc/*/*[starts-with(name(), 'PK.006.03_')])[@VRG = $v]/Datenpunkt[normalize-space(Wert_C1120PK) = $pn])[1]
                else ()) else ()</xpath>
		</rule>
		<rule id="PK_208" old="VDL008" grp="abgl" sev="E" name="Summencheck pro VRG / Sub-VG gesamt" msg="Summe Einzelpositionen der List of Assets und Look-Through ungleich Summe Vermögen (Basis = Bilanzwert)">
			<note>VRG-Art 1, 3, 4, nicht konsortial, PNR 800 &gt; 0: PNR 800 = Summe Bilanzwerte aus PK.006.02 und PK.006.03 aller Positionen R800-100 bis R800-690.</note>
			<cond>$val('PK.800.01', $v, 'R800-800') gt 0 and abs($val('PK.800.01', $v, 'R800-800') - $sumPnr($v, ('R800-100', 'R800-200', 'R800-300', 'R800-400', 'R800-500', 'R800-600', 'R800-110', 'R800-130', 'R800-140', 'R800-160', 'R800-170', 'R800-180', 'R800-190', 'R800-210', 'R800-220', 'R800-230', 'R800-310', 'R800-315', 'R800-320', 'R800-325', 'R800-330', 'R800-335', 'R800-372', 'R800-374', 'R800-382', 'R800-384', 'R800-390', 'R800-410', 'R800-420', 'R800-430', 'R800-470', 'R800-480', 'R800-490', 'R800-510', 'R800-560', 'R800-570', 'R800-580', 'R800-590', 'R800-610', 'R800-620', 'R800-630', 'R800-670', 'R800-680', 'R800-690'))) gt 0.99</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.800.01_')]
return let $v := string($t/@VRG),
    $sumPnr := function($v, $pnrs) { sum(for $x in ($doc/*/*[starts-with(name(), 'PK.006.02.01.01_')] | $doc/*/*[starts-with(name(), 'PK.006.03_')])[@VRG = $v]/Datenpunkt[normalize-space(Wert_C1120PK) = $pnrs]/Wert_C1100PK return $num($x)) },
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($art($v) = ('1', '3', '4') and $kons = 'N' and $val('PK.800.01', $v, 'R800-800') gt 0 and abs($val('PK.800.01', $v, 'R800-800') - $sumPnr($v, ('R800-100', 'R800-200', 'R800-300', 'R800-400', 'R800-500', 'R800-600', 'R800-110', 'R800-130', 'R800-140', 'R800-160', 'R800-170', 'R800-180', 'R800-190', 'R800-210', 'R800-220', 'R800-230', 'R800-310', 'R800-315', 'R800-320', 'R800-325', 'R800-330', 'R800-335', 'R800-372', 'R800-374', 'R800-382', 'R800-384', 'R800-390', 'R800-410', 'R800-420', 'R800-430', 'R800-470', 'R800-480', 'R800-490', 'R800-510', 'R800-560', 'R800-570', 'R800-580', 'R800-590', 'R800-610', 'R800-620', 'R800-630', 'R800-670', 'R800-680', 'R800-690'))) gt 0.99) then ($fact('PK.800.01', $v, 'R800-800'), $t)[1] else ()</xpath>
		</rule>
		<rule id="PK_215" old="VDL073" grp="abgl" sev="E" name="Total Amount" msg="Die Summe der Bilanzwerte entspricht nicht der Summe Vermögen im Formblatt 800.">
			<note>VRG-Art 1, 3, 4, nicht konsortial, PNR 800 &gt; 0: PNR 800 = Summe aller Bilanzwerte (C1100PK) in PK.006.02.</note>
			<cond>$val('PK.800.01', $v, 'R800-800') gt 0 and abs($val('PK.800.01', $v, 'R800-800') - sum(for $x in /*/*[starts-with(name(), 'PK.006.02.01.01_')][@VRG = $v]/Datenpunkt/Wert_C1100PK return $num($x))) gt 0.99</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.800.01_')]
return let $v := string($t/@VRG),
    $sumPnr := function($v, $pnrs) { sum(for $x in ($doc/*/*[starts-with(name(), 'PK.006.02.01.01_')] | $doc/*/*[starts-with(name(), 'PK.006.03_')])[@VRG = $v]/Datenpunkt[normalize-space(Wert_C1120PK) = $pnrs]/Wert_C1100PK return $num($x)) },
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($art($v) = ('1', '3', '4') and $kons = 'N' and $val('PK.800.01', $v, 'R800-800') gt 0 and abs($val('PK.800.01', $v, 'R800-800') - sum(for $x in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')][@VRG = $v]/Datenpunkt/Wert_C1100PK return $num($x))) gt 0.99) then ($fact('PK.800.01', $v, 'R800-800'), $t)[1] else ()</xpath>
		</rule>
		<rule id="PK_209_1" old="VDL066" grp="assets" sev="E" name="Total Amount" msg="Summe der Bilanzwerte ungleich dem Total Amount.">
			<note>Fonds mit Einzelwert 2/3: Bilanzwert (C1100PK) = Summe C1100PK der Durchrechnung (Fehler über 3 €, Warnung über 0,99 €).</note>
			<cond>normalize-space($a/Wert_C1140PK) = ('2', '3') and
  abs($num($p/Wert_C1100PK) - sum(for $x in $lts($vrg, $p/Wert_C0010PK)/Wert_C1100PK return $num($x))) gt 0.99</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (normalize-space($a/Wert_C1140PK) = ('2', '3') and
  abs($num($p/Wert_C1100PK) - sum(for $x in $lts($vrg, $p/Wert_C0010PK)/Wert_C1100PK return $num($x))) gt 0.99) then $p else ()</xpath>
		</rule>
		<rule id="PK_1" old="VA00009" grp="assets" sev="E" name="Positionsnummer" msg="Im Meldetemplate PK.006.02 (VRG 999) wurde eine ungültige Positionsnummer verwendet.">
			<note>AG (VRG-Art 9): die Positionsnummer muss eine Position des Formblatts 100 sein.</note>
			<cond>$art = '9' and $has($p/Wert_C1120PK) and not(matches(normalize-space($p/Wert_C1120PK), '^R100-(131|132|133|134|135|136|137|138|139|221|222|223|224|230)$'))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ($art = '9' and $has($p/Wert_C1120PK) and not(matches(normalize-space($p/Wert_C1120PK), '^R100-(131|132|133|134|135|136|137|138|139|221|222|223|224|230)$'))) then $p else ()</xpath>
		</rule>
		<rule id="PK_2_1" old="VA00011" grp="assets" sev="E" name="Positionsnummer" msg="Im Meldetemplate PK.006.02 (VRG XXX) wurde eine ungültige Positionsnummer verwendet. Es sind keine Hievon-Positionen zu melden.">
			<note>VRG-Art 1, 3, 4: Positionsnummer aus Formblatt 800 (R800-…), VRG-Art 2: aus Formblatt 300 (R300-…); Hunderter-Summen und „davon“-Positionen sind nicht zulässig. VRG-Art 1 und 4 dürfen beide Formblätter verwenden.</note>
			<cond>$art = ('1', '2', '3', '4') and $has($p/Wert_C1120PK) and not(matches(normalize-space($p/Wert_C1120PK),
  if ($art = '3') then '^R800-(110|130|140|160|170|180|190|210|220|230|310|315|320|325|330|335|372|374|382|384|390|410|420|430|470|480|490|510|560|570|580|590|610|620|630|670|680|690)$' else if ($art = '2') then '^R300-(110|130|140|170|180|190|210|220|230|310|315|320|325|330|335|372|374|382|384|390|410|420|430|470|480|490|510|560|570|580|590|610|620|630|670|680|690)$' else '^(R800-(110|130|140|160|170|180|190|210|220|230|310|315|320|325|330|335|372|374|382|384|390|410|420|430|470|480|490|510|560|570|580|590|610|620|630|670|680|690)|R300-(110|130|140|170|180|190|210|220|230|310|315|320|325|330|335|372|374|382|384|390|410|420|430|470|480|490|510|560|570|580|590|610|620|630|670|680|690))$'))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ($art = ('1', '2', '3', '4') and $has($p/Wert_C1120PK) and not(matches(normalize-space($p/Wert_C1120PK),
  if ($art = '3') then '^R800-(110|130|140|160|170|180|190|210|220|230|310|315|320|325|330|335|372|374|382|384|390|410|420|430|470|480|490|510|560|570|580|590|610|620|630|670|680|690)$' else if ($art = '2') then '^R300-(110|130|140|170|180|190|210|220|230|310|315|320|325|330|335|372|374|382|384|390|410|420|430|470|480|490|510|560|570|580|590|610|620|630|670|680|690)$' else '^(R800-(110|130|140|160|170|180|190|210|220|230|310|315|320|325|330|335|372|374|382|384|390|410|420|430|470|480|490|510|560|570|580|590|610|620|630|670|680|690)|R300-(110|130|140|170|180|190|210|220|230|310|315|320|325|330|335|372|374|382|384|390|410|420|430|470|480|490|510|560|570|580|590|610|620|630|670|680|690))$'))) then $p else ()</xpath>
		</rule>
		<rule id="PK_7" old="VA00016" grp="assets" sev="E" name="Type Of Code bei CAU/ISIN" msg="Wird beim Type of Code 'CAU/ISIN' angegeben, so ist im Feld Asset ID Code die ISIN um 4 Stellen zu erweitern (4. letzte Stellen = '+XXX') (plus und Währungscode)">
			<note>Type of Code CAU/ISIN: der Asset-ID-Code muss ISIN + "+" + Währungscode sein.</note>
			<cond>$p/Wert_C0020PK = 'CAU/ISIN' and not(matches($p/Wert_C0010PK, '^[A-Z]{2}[0-9A-Z]{8}[0-9]{2}\+[A-Z]{3}$'))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ($p/Wert_C0020PK = 'CAU/ISIN' and not(matches($p/Wert_C0010PK, '^[A-Z]{2}[0-9A-Z]{8}[0-9]{2}\+[A-Z]{3}$'))) then $p else ()</xpath>
		</rule>
		<rule id="PBV38" old="VDL010" grp="assets" sev="E" name="CIC Code" msg="The reported CIC is not valid.">
			<note>Die Stellen 3–4 des CIC müssen eine gültige Kategorie sein (Liste laut Spezifikation).</note>
			<cond>not(string-length($cic) = 4 and $like('11|12|13|14|15|16|17|18|19|21|22|23|24|25|26|27|28|29|31|32|33|34|39|41|42|43|44|45|46|47|48|49|51|52|53|54|55|56|57|58|59|61|62|63|64|65|66|67|68|69|71|72|73|74|75|79|81|82|84|85|86|87|88|89|91|92|93|94|95|96|99|09|A1|A2|A3|A5|A7|A8|A9|B1|B2|B3|B4|B5|B6|B7|B8|B9|C1|C2|C3|C4|C5|C6|C7|C8|C9|D1|D2|D3|D4|D5|D7|D8|D9|E1|E2|E7|E8|E9|F1|F2|F3|F4|F9'))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (not(string-length($cic) = 4 and $like('11|12|13|14|15|16|17|18|19|21|22|23|24|25|26|27|28|29|31|32|33|34|39|41|42|43|44|45|46|47|48|49|51|52|53|54|55|56|57|58|59|61|62|63|64|65|66|67|68|69|71|72|73|74|75|79|81|82|84|85|86|87|88|89|91|92|93|94|95|96|99|09|A1|A2|A3|A5|A7|A8|A9|B1|B2|B3|B4|B5|B6|B7|B8|B9|C1|C2|C3|C4|C5|C6|C7|C8|C9|D1|D2|D3|D4|D5|D7|D8|D9|E1|E2|E7|E8|E9|F1|F2|F3|F4|F9'))) then $p else ()</xpath>
		</rule>
		<rule id="PBV39" old="VDL011" grp="assets" sev="E" name="Unit price und Unit percentage of par amount price" msg="The items “Unit price” and “Unit percentage of par amount price” should not be reported simultaneously.">
			<note>Kurs (C0370) und Kurs in % (C0380) dürfen nicht gleichzeitig gemeldet sein.</note>
			<cond>$has($a/Wert_C0370) and $has($a/Wert_C0380)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ($has($a/Wert_C0370) and $has($a/Wert_C0380)) then $p else ()</xpath>
		</rule>
		<rule id="PBV40" old="VDL012" grp="assets" sev="W" name="Asset ID Code" msg='If the Asset ID Code Type equals "CAU", the asset should be generally be reported as "not listed in a stock exchange" or "not exchange tradable", although there might be some exemptions.'>
			<note>Type of Code CAU/INST (außer Derivate): CIC muss mit XL oder XT beginnen.</note>
			<cond>$p/Wert_C0020PK = 'CAU/INST' and not($like('A#|B#|C#|D#|E#|F#')) and not(matches($cic, '^(XL|XT)'))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ($p/Wert_C0020PK = 'CAU/INST' and not($like('A#|B#|C#|D#|E#|F#')) and not(matches($cic, '^(XL|XT)'))) then $p else ()</xpath>
		</rule>
		<rule id="PBV41" old="VDL081" grp="assets" sev="E" name="Country of custody" msg='The item "Country of custody" was not reported.'>
			<note>Außer CIC ##71, ##75, ##8#, ##95, ##09: Verwahrland (C0040) muss gemeldet sein.</note>
			<cond>(not($like('71|75|8#|95|09'))) and not($has($p/Wert_C0040))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ((not($like('71|75|8#|95|09'))) and not($has($p/Wert_C0040))) then $p else ()</xpath>
		</rule>
		<rule id="PBV42" old="VDL013" grp="assets" sev="E" name="Country of custody" msg="The item &quot;Country of custody&quot; should not be reported for assets with CIC '##71', '##75', '##8#' or '##95'.">
			<note>CIC ##71, ##75, ##8#, ##95: Verwahrland (C0040) darf nicht gemeldet sein („Not applicable“ ist zulässig).</note>
			<cond>($like('71|75|8#|95')) and $real($p/Wert_C0040)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (($like('71|75|8#|95')) and $real($p/Wert_C0040)) then $p else ()</xpath>
		</rule>
		<rule id="PBV43" old="VDL014" grp="assets" sev="W" name="Acquisition value" msg="The item &quot;Acquisition value&quot; should not be reported for assets with CIC '##7#' or '##8#' and derivatives.">
			<note>CIC ##7#, ##8#, ##09 und Derivate: Anschaffungswert (C0080) darf nicht gemeldet sein („Not applicable“ ist zulässig).</note>
			<cond>($like('7#|8#|09|A#|B#|C#|D#|E#|F#')) and $real($p/Wert_C0080)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (($like('7#|8#|09|A#|B#|C#|D#|E#|F#')) and $real($p/Wert_C0080)) then $p else ()</xpath>
		</rule>
		<rule id="PBV44" old="VDL015" grp="assets" sev="W" name="Acquisition value" msg="The item &quot;Acquisition value&quot; should be reported, except for assets with CIC '##7#' or '##8#'.">
			<note>Außer CIC ##7#, ##8#, ##09 und Derivaten: Anschaffungswert (C0080) muss gemeldet sein.</note>
			<cond>(not($like('7#|8#|09|A#|B#|C#|D#|E#|F#'))) and not($has($p/Wert_C0080))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ((not($like('7#|8#|09|A#|B#|C#|D#|E#|F#'))) and not($has($p/Wert_C0080))) then $p else ()</xpath>
		</rule>
		<rule id="PBV47" old="VDL016" grp="assets" sev="W" name="Issuer Name" msg="The item &quot;Issuer name&quot; should not be reported for assets with CIC '##71', '##75' or '##9#'.">
			<note>CIC ##71, ##75, ##9#: Emittentenname (C0140) darf nicht gemeldet sein („Not applicable“ ist zulässig).</note>
			<cond>($like('71|75|9#')) and $real($a/Wert_C0140)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (($like('71|75|9#')) and $real($a/Wert_C0140)) then $p else ()</xpath>
		</rule>
		<rule id="PBV48" old="VDL017" grp="assets" sev="W" name="Issuer Name" msg="The item &quot;Issuer name&quot; should be reported, except for assets with CIC '##71', '##75' or '##9#'.">
			<note>Außer CIC ##71, ##75, ##9#, ##09: Emittentenname (C0140) muss gemeldet sein.</note>
			<cond>(not($like('71|75|9#|09'))) and not($has($a/Wert_C0140))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ((not($like('71|75|9#|09'))) and not($has($a/Wert_C0140))) then $p else ()</xpath>
		</rule>
		<rule id="PBV49" old="VDL018" grp="assets" sev="W" name="Issuer Sector" msg="The item &quot;Issuer sector&quot; should not be reported for assets with CIC '##71', '##75', '##87', '##88', '##9#' or '##09'.">
			<note>CIC ##71, ##75, ##87, ##88, ##9#, ##09: Emittentensektor (C0170 und C0175) darf nicht gemeldet sein.</note>
			<cond>$like('71|75|87|88|9#|09') and ($real($a/Wert_C0170) or $real($a/Wert_C0175))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ($like('71|75|87|88|9#|09') and ($real($a/Wert_C0170) or $real($a/Wert_C0175))) then $p else ()</xpath>
		</rule>
		<rule id="PBV50" old="VDL082" grp="assets" sev="E" name="Issuer Sector" msg="The item &quot;Issuer sector&quot; should be reported, except for assets with CIC '##71', '##75', '##87', '##88', '##9#'.">
			<note>Außer CIC ##71, ##75, ##87, ##88, ##9#, ##09: Emittentensektor nach NACE 2.0 (C0170) oder NACE 2.1 (C0175) muss gemeldet sein.</note>
			<cond>not($like('71|75|87|88|9#|09')) and not($has($a/Wert_C0170) or $has($a/Wert_C0175))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (not($like('71|75|87|88|9#|09')) and not($has($a/Wert_C0170) or $has($a/Wert_C0175))) then $p else ()</xpath>
		</rule>
		<rule id="PK_270" old="" grp="assets" sev="E" name="Issuer Sector" msg="Der Issuer Sector ist als NACE 2.0 oder NACE 2.1 zu melden, beides ist nicht erlaubt.">
			<note>Außer CIC ##71, ##75, ##87, ##88, ##9#, ##09: Emittentensektor nur nach NACE 2.0 (C0170) oder 2.1 (C0175), nicht beides.</note>
			<cond>not($like('71|75|87|88|9#|09')) and $real($a/Wert_C0170) and $real($a/Wert_C0175)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (not($like('71|75|87|88|9#|09')) and $real($a/Wert_C0170) and $real($a/Wert_C0175)) then $p else ()</xpath>
		</rule>
		<rule id="PBV51" old="VDL019" grp="assets" sev="W" name="Issuer Country" msg="The item &quot;Issuer country&quot; should not be reported for assets with CIC '##71', '##75', '##87', '##88', '##9#' or '##09'.">
			<note>CIC ##71, ##75, ##87, ##88, ##9#, ##09: Emittentenland (C0210) darf nicht gemeldet sein („Not applicable“ ist zulässig).</note>
			<cond>($like('71|75|87|88|9#|09')) and $real($a/Wert_C0210)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (($like('71|75|87|88|9#|09')) and $real($a/Wert_C0210)) then $p else ()</xpath>
		</rule>
		<rule id="PBV52" old="VDL083" grp="assets" sev="E" name="Issuer Country" msg="The item &quot;Issuer country&quot; should not be reported for assets with CIC '##71', '##75', '##87', '##88', '##9#' or '##09'.">
			<note>Außer CIC ##71, ##75, ##87, ##88, ##9#, ##09: Emittentenland (C0210) muss gemeldet sein.</note>
			<cond>(not($like('71|75|87|88|9#|09'))) and not($has($a/Wert_C0210))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ((not($like('71|75|87|88|9#|09'))) and not($has($a/Wert_C0210))) then $p else ()</xpath>
		</rule>
		<rule id="PBV53" old="VDL020" grp="assets" sev="W" name="Currency" msg="The item &quot;Currency&quot; should not be reported for assets with CIC '##75', '##87', '##88', '##95' or '##09'.">
			<note>CIC ##75, ##95, ##87, ##88, ##09: Währung (C0220) darf nicht gemeldet sein („Not applicable“ ist zulässig).</note>
			<cond>($like('75|95|87|88|09')) and $real($a/Wert_C0220)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (($like('75|95|87|88|09')) and $real($a/Wert_C0220)) then $p else ()</xpath>
		</rule>
		<rule id="PBV54" old="VDL201" grp="assets" sev="W" name="Currency" msg="The item &quot;Currency&quot; must be reported, except for assets with CIC '##75', '##87', '##88', '##95' or '##09'.">
			<note>Außer CIC ##75, ##95, ##87, ##88, ##09: Währung (C0220) muss gemeldet sein.</note>
			<cond>(not($like('75|95|87|88|09'))) and not($has($a/Wert_C0220))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ((not($like('75|95|87|88|09'))) and not($has($a/Wert_C0220))) then $p else ()</xpath>
		</rule>
		<rule id="PBV55" old="VDL021" grp="assets" sev="E" name="Duration" msg="The Item &quot;Duration&quot; should not be reported for assets with CIC '##3#' , '##7#' , '##8#' , '##9#'.">
			<note>CIC ##3#, ##7#, ##8#, ##9#: Duration (C0270) darf nicht gemeldet sein („Not applicable“ ist zulässig).</note>
			<cond>($like('(3|7|8|9)#')) and $real($a/Wert_C0270)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (($like('(3|7|8|9)#')) and $real($a/Wert_C0270)) then $p else ()</xpath>
		</rule>
		<rule id="PBV56" old="VDL022" grp="assets" sev="W" name="Duration" msg="The item &quot;Duration&quot; should be reported for assets with CIC '##1#', '##2#', '##5#', '##6#'.">
			<note>CIC ##1#, ##2#, ##42, ##5#, ##6#: Duration (C0270) muss gemeldet sein.</note>
			<cond>($like('(1|2|5|6)#|42')) and not($has($a/Wert_C0270))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (($like('(1|2|5|6)#|42')) and not($has($a/Wert_C0270))) then $p else ()</xpath>
		</rule>
		<rule id="PBV57" old="VDL023" grp="assets" sev="W" name="Maturity Date" msg="The item &quot;Maturity date&quot; should not be reported for assets with CIC '##3#' , '##4#' ,'##71' , '##72', '##73', '##75' or '##9#'.">
			<note>CIC ##3#, ##4#, ##71, ##72, ##73, ##75, ##9#: Fälligkeit (C0280) darf nicht gemeldet sein („Not applicable“ ist zulässig).</note>
			<cond>($like('3#|4#|71|72|73|75|9#')) and $real($a/Wert_C0280)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (($like('3#|4#|71|72|73|75|9#')) and $real($a/Wert_C0280)) then $p else ()</xpath>
		</rule>
		<rule id="PBV58" old="VDL024" grp="assets" sev="W" name="Maturity Date" msg="The item &quot;Maturity date&quot; should be reported for assets with CIC '##1#', '##2#', '##5#', '##6#', '##74', '##79', '##8#'.">
			<note>CIC ##1#, ##2#, ##5#, ##6#, ##74, ##79, ##8#: Fälligkeit (C0280) muss gemeldet sein.</note>
			<cond>($like('1#|2#|5#|6#|74|79|8#')) and not($has($a/Wert_C0280))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (($like('1#|2#|5#|6#|74|79|8#')) and not($has($a/Wert_C0280))) then $p else ()</xpath>
		</rule>
		<rule id="PBV10" old="VDL009" grp="assets" sev="W" name="Maturity Date" msg="The maturity date should be equal to or after the reporting reference date, except for specific cases such as assets in default.">
			<note>Die Fälligkeit (C0280) soll nicht vor dem Stichtag (GJ + Quartal) liegen.</note>
			<cond>exists($date($a/Wert_C0280)) and $date($a/Wert_C0280) lt $ref</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (exists($date($a/Wert_C0280)) and $date($a/Wert_C0280) lt $ref) then $p else ()</xpath>
		</rule>
		<rule id="PBV59" old="" grp="assets" sev="E" name="Quantity und Par Amount" msg="Quantity or Par Amount must be reported, except for assets with CIC '##71' or '##9#'.">
			<note>Außer CIC ##71, ##9#, ##09 und Derivaten muss Menge (C0060) oder Nominale (C0070) gemeldet sein.</note>
			<cond>not($like('71|9#|09|A#|B#|C#|D#|E#|F#')) and not($has($p/Wert_C0060) or $has($p/Wert_C0070))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (not($like('71|9#|09|A#|B#|C#|D#|E#|F#')) and not($has($p/Wert_C0060) or $has($p/Wert_C0070))) then $p else ()</xpath>
		</rule>
		<rule id="PBV60" old="VDL026" grp="assets" sev="E" name="Quantity und Par Amount" msg="The items “Par amount” and “Quantity” should not be reported simultaneously.">
			<note>Menge (C0060) und Nominale (C0070) dürfen nicht gleichzeitig gemeldet sein.</note>
			<cond>$has($p/Wert_C0060) and $has($p/Wert_C0070)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ($has($p/Wert_C0060) and $has($p/Wert_C0070)) then $p else ()</xpath>
		</rule>
		<rule id="PBV61" old="VDL027" grp="assets" sev="E" name="Unit price und Quantity" msg="The items “Unit price” and “Quantity” should be reported simultaneously.">
			<note>Außer CIC ##71, ##9#, ##09: Menge (C0060) und Kurs (C0370) nur gemeinsam melden.</note>
			<cond>not($like('71|9#|09')) and $has($p/Wert_C0060) ne $has($a/Wert_C0370)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (not($like('71|9#|09')) and $has($p/Wert_C0060) ne $has($a/Wert_C0370)) then $p else ()</xpath>
		</rule>
		<rule id="PBV63" old="VDL029" grp="assets" sev="E" name="Par Amount und Unit percentage of par amount price" msg="The items “Par amount” and “Unit percentage of par amount price” should be reported simultaneously.">
			<note>Außer CIC ##71, ##9#, ##09: Nominale (C0070) und Kurs in % (C0380) nur gemeinsam melden.</note>
			<cond>not($like('71|9#|09')) and $has($p/Wert_C0070) ne $has($a/Wert_C0380)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (not($like('71|9#|09')) and $has($p/Wert_C0070) ne $has($a/Wert_C0380)) then $p else ()</xpath>
		</rule>
		<rule id="PBV65" old="VDL031" grp="assets" sev="W" name="Total Amount" msg="The item &quot;Bilanzwert&quot; should be positive for the CIC '##7#'.">
			<note>Einlagen und Kassa (CIC ##7#): Bilanzwert darf nicht negativ sein.</note>
			<cond>$like('7#') and $num($p/Wert_C1100PK) lt 0</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ($like('7#') and $num($p/Wert_C1100PK) lt 0) then $p else ()</xpath>
		</rule>
		<rule id="PBV66" old="VDL032" grp="assets" sev="W" name="Total Amount" msg='The item "Bilanzwert" is different from the product of the items "Quantity" and "Unit price" plus the item "Accrued interest".'>
			<note>Außer CIC ##71, ##9#, ##09: Bilanzwert (C1100PK) = Menge × Kurs + Stückzinsen, Toleranz min(1.000 €, 1 % des berechneten Werts).</note>
			<cond>not($like('71|9#|09')) and $has($p/Wert_C0060) and
  (let $e := $num($p/Wert_C0060) * $num($a/Wert_C0370) + $num($p/Wert_C0090)
   return abs($num($p/Wert_C1100PK) - $e) ge min((1000, abs($e) * 0.01)))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (not($like('71|9#|09')) and $has($p/Wert_C0060) and
  (let $e := $num($p/Wert_C0060) * $num($a/Wert_C0370) + $num($p/Wert_C0090)
   return abs($num($p/Wert_C1100PK) - $e) ge min((1000, abs($e) * 0.01)))) then $p else ()</xpath>
		</rule>
		<rule id="PBV67" old="VDL033" grp="assets" sev="W" name="Total Amount" msg='The item "Bilanzwert" is different from the product of the items "Par amount" and "Unit percentage of par amount price" plus the item "Accrued interest".'>
			<note>Außer CIC ##71, ##9#, ##09: Bilanzwert (C1100PK) = Nominale × Kurs in % + Stückzinsen, Toleranz min(1.000 €, 1 % des berechneten Werts).</note>
			<cond>not($like('71|9#|09')) and $has($p/Wert_C0070) and
  (let $e := $num($p/Wert_C0070) * $num($a/Wert_C0380) + $num($p/Wert_C0090)
   return abs($num($p/Wert_C1100PK) - $e) ge min((1000, abs($e) * 0.01)))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (not($like('71|9#|09')) and $has($p/Wert_C0070) and
  (let $e := $num($p/Wert_C0070) * $num($a/Wert_C0380) + $num($p/Wert_C0090)
   return abs($num($p/Wert_C1100PK) - $e) ge min((1000, abs($e) * 0.01)))) then $p else ()</xpath>
		</rule>
		<rule id="PBV85" old="VDL100" grp="assets" sev="W" name="External Rating" msg="Item “External rating” should be reported at least for items with CIC like '##1#','##2#', '##5#' or '##6#'.">
			<note>Anleihen (CIC ##1#, ##2#, ##5#, ##6#): Rating (C0250) muss gemeldet sein.</note>
			<cond>($like('(1|2|5|6)#')) and not($has($a/Wert_C0250))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (($like('(1|2|5|6)#')) and not($has($a/Wert_C0250))) then $p else ()</xpath>
		</rule>
		<rule id="PBV86" old="VDL101" grp="assets" sev="W" name="Nominated ECAI" msg="Item “Nominated ECAI” should be reported at least for items with CIC like '##1#','##2#', '##5#' or '##6#'.">
			<note>Anleihen (CIC ##1#, ##2#, ##5#, ##6#): Ratingagentur (ECAI) (C0260) muss gemeldet sein.</note>
			<cond>($like('(1|2|5|6)#')) and not($has($a/Wert_C0260))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (($like('(1|2|5|6)#')) and not($has($a/Wert_C0260))) then $p else ()</xpath>
		</rule>
		<rule id="PBV87" old="" grp="assets" sev="W" name="Quantity und Par Amount" msg="For assets with CIC codes like '##72' or '##73' or '##74' or '##75' or '##79' or '##8#' item “Par amount” should be reported, while item &quot;Quantity&quot; should be left empty.">
			<note>CIC ##72–##75, ##79, ##8#: Nominale (C0070) melden, Menge (C0060) leer lassen.</note>
			<cond>$like('72|73|74|75|79|8#') and not(not($has($p/Wert_C0060)) and $has($p/Wert_C0070))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ($like('72|73|74|75|79|8#') and not(not($has($p/Wert_C0060)) and $has($p/Wert_C0070))) then $p else ()</xpath>
		</rule>
		<rule id="PBV88" old="VDL102" grp="assets" sev="W" name="Custodian" msg="Item “Custodian” should not be reported for items with CIC like '##71', '##75', '##8#', '##9#' or '##09'.">
			<note>CIC ##71, ##75, ##8#, ##9#, ##09: Verwahrer (C0050) darf nicht gemeldet sein („Not applicable“ ist zulässig).</note>
			<cond>($like('71|75|8#|9#|09')) and $real($p/Wert_C0050)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (($like('71|75|8#|9#|09')) and $real($p/Wert_C0050)) then $p else ()</xpath>
		</rule>
		<rule id="PBV96" old="VDL107" grp="assets" sev="W" name="Custodian" msg="Item “Custodian” should be reported at least for items with CIC codes different from '##71', '##75', '##8#', '##9#' or '##09'.">
			<note>Außer CIC ##71, ##75, ##8#, ##9#, ##09: Verwahrer (C0050) muss gemeldet sein.</note>
			<cond>(not($like('71|75|8#|9#|09'))) and not($has($p/Wert_C0050))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ((not($like('71|75|8#|9#|09'))) and not($has($p/Wert_C0050))) then $p else ()</xpath>
		</rule>
		<rule id="PBV89" old="VDL103" grp="assets" sev="E" name="Issuer Code" msg="Item “Issuer Code” should not be reported for items with CIC like '##71', '##75', '##87', '##88' or '##9#'.">
			<note>CIC ##71, ##75, ##87, ##88, ##9#: Emittentencode (C0150PK) darf nicht gemeldet sein („Not applicable“ ist zulässig).</note>
			<cond>($like('71|75|87|88|9#')) and $real($a/Wert_C0150PK)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (($like('71|75|87|88|9#')) and $real($a/Wert_C0150PK)) then $p else ()</xpath>
		</rule>
		<rule id="PBV97" old="VDL108" grp="assets" sev="W" name="Issuer Code" msg="Item “Issuer Code” should be reported at least for items with CIC codes different from '##71', '##75', '##87', '##88', '##9#' or '##09'.">
			<note>Außer CIC ##71, ##75, ##87, ##88, ##9#, ##09: Emittentencode (C0150PK) muss gemeldet sein.</note>
			<cond>(not($like('71|75|87|88|9#|09'))) and not($has($a/Wert_C0150PK))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ((not($like('71|75|87|88|9#|09'))) and not($has($a/Wert_C0150PK))) then $p else ()</xpath>
		</rule>
		<rule id="PBV90" old="VDL104" grp="assets" sev="E" name="Issuer Group" msg="Item “Issuer Group” should not be reported for items with CIC like '##11', '##12', '##13', '##14', '##15', '##17', '##18', '##71', '##75', '##87', '##88', '##9#' or '##09'.">
			<note>CIC ##11, ##12, ##13, ##14, ##15, ##17, ##18, ##71, ##75, ##87, ##88, ##9#, ##09: Emittentengruppe (C0180) darf nicht gemeldet sein („Not applicable“ ist zulässig).</note>
			<cond>($like('11|12|13|14|15|17|18|71|75|87|88|9#|09')) and $real($a/Wert_C0180)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (($like('11|12|13|14|15|17|18|71|75|87|88|9#|09')) and $real($a/Wert_C0180)) then $p else ()</xpath>
		</rule>
		<rule id="PBV98" old="VDL109" grp="assets" sev="W" name="Issuer Group" msg="Item “Issuer Group” should be reported at least for items with CIC codes different from '##11', '##12', '##13', '##14', '##15', '##17', '##18', '##71', '##75', '##87', '##88', '##9#' or '##09'.">
			<note>Außer CIC ##11, ##12, ##13, ##14, ##15, ##17, ##18, ##71, ##75, ##87, ##88, ##9#, ##09: Emittentengruppe (C0180) muss gemeldet sein.</note>
			<cond>(not($like('11|12|13|14|15|17|18|71|75|87|88|9#|09'))) and not($has($a/Wert_C0180))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ((not($like('11|12|13|14|15|17|18|71|75|87|88|9#|09'))) and not($has($a/Wert_C0180))) then $p else ()</xpath>
		</rule>
		<rule id="PBV91" old="VDL105" grp="assets" sev="E" name="Issuer Group Code" msg="Item “Issuer Group Code” should not be reported for items with CIC like '##11', '##12', '##13', '##14', '##15', '##17', '##18', '##71', '##75', '##87', '##88', '##9#' or '##09'.">
			<note>CIC ##11, ##12, ##13, ##14, ##15, ##17, ##18, ##71, ##75, ##87, ##88, ##9#, ##09: Emittentengruppencode (C0190PK) darf nicht gemeldet sein („Not applicable“ ist zulässig).</note>
			<cond>($like('11|12|13|14|15|17|18|71|75|87|88|9#|09')) and $real($a/Wert_C0190PK)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (($like('11|12|13|14|15|17|18|71|75|87|88|9#|09')) and $real($a/Wert_C0190PK)) then $p else ()</xpath>
		</rule>
		<rule id="PBV99" old="VDL110" grp="assets" sev="W" name="Issuer Group Code" msg="Item “Issuer Group Code” should be reported at least for items with CIC codes different from '##11', '##12', '##13', '##14', '##15', '##17', '##18', '##71', '##75', '##87', '##88', '##9#' or '##09'.">
			<note>Außer CIC ##11, ##12, ##13, ##14, ##15, ##17, ##18, ##71, ##75, ##87, ##88, ##9#, ##09: Emittentengruppencode (C0190PK) muss gemeldet sein.</note>
			<cond>(not($like('11|12|13|14|15|17|18|71|75|87|88|9#|09'))) and not($has($a/Wert_C0190PK))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ((not($like('11|12|13|14|15|17|18|71|75|87|88|9#|09'))) and not($has($a/Wert_C0190PK))) then $p else ()</xpath>
		</rule>
		<rule id="PBV94" old="" grp="assets" sev="W" name="Quantity" msg="Item “Quantity ” should not be reported for items with CIC like '##71' or '##9#'.">
			<note>CIC ##71, ##9#: Menge (C0060) darf nicht gemeldet sein („Not applicable“ ist zulässig).</note>
			<cond>($like('71|9#')) and $real($p/Wert_C0060)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (($like('71|9#')) and $real($p/Wert_C0060)) then $p else ()</xpath>
		</rule>
		<rule id="PBV95" old="" grp="assets" sev="W" name="Par Amount" msg="Item “Par amount ” should not be reported for items with CIC like '##71', '##9#' or '##09'.">
			<note>CIC ##71, ##09, ##9#: Nominale (C0070) darf nicht gemeldet sein („Not applicable“ ist zulässig).</note>
			<cond>($like('71|09|9#')) and $real($p/Wert_C0070)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (($like('71|09|9#')) and $real($p/Wert_C0070)) then $p else ()</xpath>
		</rule>
		<rule id="PK_209_2" old="" grp="assets" sev="E" name="PNR" msg="Für Assets mit CIC-Kategorie 4 muss PNR in der List of Assets leer bleiben.">
			<note>Investmentfonds (CIC ##4#): Positionsnummer (C1120PK) darf nicht gemeldet sein („Not applicable“ ist zulässig).</note>
			<cond>($like('4#')) and $real($p/Wert_C1120PK)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (($like('4#')) and $real($p/Wert_C1120PK)) then $p else ()</xpath>
		</rule>
		<rule id="PK_212" old="VDL069" grp="assets" sev="E" name="Einzelwert" msg="The item &quot;Einzelwert&quot; is equal to '1' for assets with CIC unlike ##4#.">
			<note>Kein Investmentfonds (CIC nicht ##4#): Einzelwert (C1140PK) muss 1 sein.</note>
			<cond>not($like('4#')) and normalize-space($a/Wert_C1140PK) ne '1'</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (not($like('4#')) and normalize-space($a/Wert_C1140PK) ne '1') then $p else ()</xpath>
		</rule>
		<rule id="PK_213" old="VDL070" grp="assets" sev="E" name="Einzelwert" msg="The item &quot;Einzelwert&quot; is equal to '2' or '3' for assets with CIC ##4#.">
			<note>Investmentfonds (CIC ##4#): Einzelwert (C1140PK) muss 2 oder 3 sein.</note>
			<cond>$like('4#') and not(normalize-space($a/Wert_C1140PK) = ('2', '3'))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ($like('4#') and not(normalize-space($a/Wert_C1140PK) = ('2', '3'))) then $p else ()</xpath>
		</rule>
		<rule id="PK_214" old="VDL072" grp="assets" sev="E" name="Reclassification" msg='The item "Reclassification" should not be equal to the item "CIC".'>
			<note>Die Umklassifizierung (EC0041) darf nicht gleich dem CIC sein.</note>
			<cond>$has($a/Wert_EC0041) and normalize-space($a/Wert_EC0041) = $cic</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ($has($a/Wert_EC0041) and normalize-space($a/Wert_EC0041) = $cic) then $p else ()</xpath>
		</rule>
		<rule id="PK_217" old="VDL075" grp="assets" sev="W" name="Valuation Method" msg='Bei den Buchwerten der Pensionskasse ist jeweils "market-consistent valuation not applicable" zu wählen.'>
			<note>AG (VRG-Art 9), außer CIC ##7#: Bewertungsmethode (C0075) muss x5004 sein.</note>
			<cond>$art = '9' and not($like('7#')) and not(ends-with(normalize-space($p/Wert_C0075), 'x5004'))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ($art = '9' and not($like('7#')) and not(ends-with(normalize-space($p/Wert_C0075), 'x5004'))) then $p else ()</xpath>
		</rule>
		<rule id="PK_218" old="VDL076" grp="assets" sev="E" name="Valuation Method" msg='Bei HTM-Werten ist jeweils "market-consistent valuation not applicable" zu wählen.'>
			<note>Held to Maturity (C1130PK = J): Bewertungsmethode (C0075) muss x5004 sein.</note>
			<cond>normalize-space($p/Wert_C1130PK) = 'J' and not(ends-with(normalize-space($p/Wert_C0075), 'x5004'))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (normalize-space($p/Wert_C1130PK) = 'J' and not(ends-with(normalize-space($p/Wert_C0075), 'x5004'))) then $p else ()</xpath>
		</rule>
		<rule id="PK_220" old="VDL080" grp="assets" sev="E" name="Kriteriensatz" msg='Vermögenswerte dürfen je VRG nur einmal im Kriteriensatz gemeldet werden, sofern Unterscheidung nur im Element "Total Amount" gegeben wäre.'>
			<note>Eine Position (alle identifizierenden Felder von PK.006.02.01.01 gleich) darf nur einmal gemeldet werden.</note>
			<cond>(let $sig := function($d) { string-join(for $n in ('C0010PK', 'C0020PK', 'C0035', 'C0040', 'C0050', 'C0051PK', 'C0060', 'C0070', 'EC0141', 'C0075', 'C0080', 'C0090', 'C1100PK', 'C1110PK', 'C1120PK', 'C1130PK') return string($d/*[local-name() = 'Wert_' || $n]), '|') }
  return count($p/../Datenpunkt[$sig(.) = $sig($p)]) gt 1)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ((let $sig := function($d) { string-join(for $n in ('C0010PK', 'C0020PK', 'C0035', 'C0040', 'C0050', 'C0051PK', 'C0060', 'C0070', 'EC0141', 'C0075', 'C0080', 'C0090', 'C1100PK', 'C1110PK', 'C1120PK', 'C1130PK') return string($d/*[local-name() = 'Wert_' || $n]), '|') }
  return count($p/../Datenpunkt[$sig(.) = $sig($p)]) gt 1)) then $p else ()</xpath>
		</rule>
		<rule id="PK_221" old="VDL087" grp="assets" sev="W" name="Unit percentage of par amount price" msg='Das Element "Unit percentage of par amount price" ist als Dezimalzahl zu melden.'>
			<note>Kurs in % (C0380) muss größer 0 und kleiner 10 sein (Angabe als Faktor, 1 = 100 %).</note>
			<cond>$has($a/Wert_C0380) and not($num($a/Wert_C0380) gt 0 and $num($a/Wert_C0380) lt 10)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ($has($a/Wert_C0380) and not($num($a/Wert_C0380) gt 0 and $num($a/Wert_C0380) lt 10)) then $p else ()</xpath>
		</rule>
		<rule id="PK_233" old="VDL111" grp="assets" sev="W" name="Credit quality step (externes Rating)" msg="Item “Credit quality step (externes Rating)” should be reported at least for items with CIC like '##1#','##2#', '##5#' or '##6#'.">
			<note>Anleihen (CIC ##1#, ##2#, ##5#, ##6#): externe Bonitätsstufe (C1150PK) muss gemeldet und ungleich 9 sein.</note>
			<cond>$like('(1|2|5|6)#') and not($has($a/Wert_C1150PK) and normalize-space($a/Wert_C1150PK) ne '9')</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ($like('(1|2|5|6)#') and not($has($a/Wert_C1150PK) and normalize-space($a/Wert_C1150PK) ne '9')) then $p else ()</xpath>
		</rule>
		<rule id="PK_234" old="VDL112" grp="assets" sev="W" name="Credit quality step (internes Rating)" msg="Item “Credit quality step (internes Rating)” should be reported at least for items with CIC like '##1#','##2#', '##5#' or '##6#'.">
			<note>Anleihen (CIC ##1#, ##2#, ##5#, ##6#): interne Bonitätsstufe (C1160PK) muss gemeldet und ungleich 9 sein.</note>
			<cond>$like('(1|2|5|6)#') and not($has($a/Wert_C1160PK) and normalize-space($a/Wert_C1160PK) ne '9')</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ($like('(1|2|5|6)#') and not($has($a/Wert_C1160PK) and normalize-space($a/Wert_C1160PK) ne '9')) then $p else ()</xpath>
		</rule>
		<rule id="PK_235" old="VDL113" grp="assets" sev="W" name="Issuer Institution" msg='The item "Issuer Institution" was reported.'>
			<note>ISIN / CAU/ISIN: Emittent (EC0212) (EC0212) darf nicht gemeldet sein („Not applicable“ ist zulässig).</note>
			<cond>($isin) and $real($a/Wert_EC0212)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (($isin) and $real($a/Wert_EC0212)) then $p else ()</xpath>
		</rule>
		<rule id="PK_236" old="VDL114" grp="assets" sev="W" name="Issuer Institution" msg='The item "Issuer Institution" was not reported.'>
			<note>Ohne ISIN, CIC ##1#, ##2#, ##5#, ##6#, ##8#: Emittent (EC0212) (EC0212) muss gemeldet sein.</note>
			<cond>(not($isin) and $like('(1|2|5|6|8)#')) and not($has($a/Wert_EC0212))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ((not($isin) and $like('(1|2|5|6|8)#')) and not($has($a/Wert_EC0212))) then $p else ()</xpath>
		</rule>
		<rule id="PK_237" old="" grp="assets" sev="W" name="Cancellation Option" msg='The item "Cancellation Option" was reported.'>
			<note>ISIN / CAU/ISIN: EC0310 (EC0310) darf nicht gemeldet sein („Not applicable“ ist zulässig).</note>
			<cond>($isin) and $real($a/Wert_EC0310)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (($isin) and $real($a/Wert_EC0310)) then $p else ()</xpath>
		</rule>
		<rule id="PK_238" old="" grp="assets" sev="W" name="Cancellation Option" msg='The item "Cancellation Option" was not reported.'>
			<note>Ohne ISIN, CIC ##1#, ##2#, ##5#, ##6#, ##8#: EC0310 (EC0310) muss gemeldet sein.</note>
			<cond>(not($isin) and $like('(1|2|5|6|8)#')) and not($has($a/Wert_EC0310))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ((not($isin) and $like('(1|2|5|6|8)#')) and not($has($a/Wert_EC0310))) then $p else ()</xpath>
		</rule>
		<rule id="PK_239" old="" grp="assets" sev="W" name="Grundstücke und Bauten" msg='Die Positionen "Grundstücke und Bauten" gelten nur für CIC Kategorie 9 - Immobilien.'>
			<note>Außer Immobilien (CIC ##9#): Grundstücksangaben C2010PK–C2040PK dürfen nicht gemeldet sein.</note>
			<cond>not($like('9#')) and (some $f in ('C2010PK', 'C2020PK', 'C2025PK', 'C2030PK', 'C2035PK', 'C2040PK') satisfies $real($a/*[local-name() = 'Wert_' || $f]))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (not($like('9#')) and (some $f in ('C2010PK', 'C2020PK', 'C2025PK', 'C2030PK', 'C2035PK', 'C2040PK') satisfies $real($a/*[local-name() = 'Wert_' || $f]))) then $p else ()</xpath>
		</rule>
		<rule id="PK_240" old="" grp="assets" sev="W" name="Grundstücke und Bauten" msg='Die Positionen "Grundstücke und Bauten" sind für CIC Kategorie 9 - Immobilien zu befüllen.'>
			<note>Immobilien (CIC ##9#): Grundstücksangaben C2010PK–C2040PK müssen gemeldet sein.</note>
			<cond>$like('9#') and (some $f in ('C2010PK', 'C2020PK', 'C2025PK', 'C2030PK', 'C2035PK', 'C2040PK') satisfies not($has($a/*[local-name() = 'Wert_' || $f])))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ($like('9#') and (some $f in ('C2010PK', 'C2020PK', 'C2025PK', 'C2030PK', 'C2035PK', 'C2040PK') satisfies not($has($a/*[local-name() = 'Wert_' || $f])))) then $p else ()</xpath>
		</rule>
		<rule id="PK_241" old="" grp="assets" sev="W" name="Beteiligungen und Anteile an verbundenen Unternehmen" msg='Die Position "Beteiligungen und Anteile an verbundenen Unternehmen" betreffend gelten nur für CIC Kategorie 3 - Eigenkapitalinstrumente".'>
			<note>Außer Eigenkapitalinstrumenten (CIC ##3#): Angaben C2060PK–C2090PK dürfen nicht gemeldet sein.</note>
			<cond>not($like('3#')) and (some $f in ('C2060PK', 'C2070PK', 'C2080PK', 'C2090PK') satisfies $real($a/*[local-name() = 'Wert_' || $f]))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (not($like('3#')) and (some $f in ('C2060PK', 'C2070PK', 'C2080PK', 'C2090PK') satisfies $real($a/*[local-name() = 'Wert_' || $f]))) then $p else ()</xpath>
		</rule>
		<rule id="PK_242" old="" grp="assets" sev="W" name="Beteiligungen und Anteile an verbundenen Unternehmen" msg='Die Position "Beteiligungen und Anteile an verbundenen Unternehmen" betreffend gelten nur für CIC Kategorie 3 - Eigenkapitalinstrumente".'>
			<note>Eigenkapitalinstrumente (CIC ##3#): Angaben C2060PK–C2090PK müssen gemeldet sein.</note>
			<cond>$like('3#') and (some $f in ('C2060PK', 'C2070PK', 'C2080PK', 'C2090PK') satisfies not($has($a/*[local-name() = 'Wert_' || $f])))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ($like('3#') and (some $f in ('C2060PK', 'C2070PK', 'C2080PK', 'C2090PK') satisfies not($has($a/*[local-name() = 'Wert_' || $f])))) then $p else ()</xpath>
		</rule>
		<rule id="PK_243" old="" grp="assets" sev="W" name="Regional Governments and Local Authorities (RGLA)" msg='Die Position "Regional Governments and Local Authorities (RGLA)" gilt nur für CIC ##13 und ##14.'>
			<note>Außer CIC ##13, ##14: RGLA (C3010PK) darf nicht gemeldet sein („Not applicable“ ist zulässig).</note>
			<cond>(not($like('13|14'))) and $real($a/Wert_C3010PK)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ((not($like('13|14'))) and $real($a/Wert_C3010PK)) then $p else ()</xpath>
		</rule>
		<rule id="PK_244" old="" grp="assets" sev="W" name="Regional Governments and Local Authorities (RGLA)" msg='Die Position "Regional Governments and Local Authorities (RGLA)" sind CIC ##13 und ##14 zu befüllen.'>
			<note>CIC ##13, ##14: RGLA (C3010PK) muss gemeldet sein.</note>
			<cond>($like('13|14')) and not($has($a/Wert_C3010PK))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (($like('13|14')) and not($has($a/Wert_C3010PK))) then $p else ()</xpath>
		</rule>
		<rule id="PK_245" old="" grp="assets" sev="E" name="Property" msg='Die Positionen "Property type" und "Property location" gelten nur für CIC Kategorie 9 - Immobilien.'>
			<note>Außer Immobilien (CIC ##9#): Property type (C3020PK) und location (C3030PK) dürfen nicht gemeldet sein.</note>
			<cond>not($like('9#')) and ($real($a/Wert_C3020PK) or $real($a/Wert_C3030PK))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (not($like('9#')) and ($real($a/Wert_C3020PK) or $real($a/Wert_C3030PK))) then $p else ()</xpath>
		</rule>
		<rule id="PK_246" old="" grp="assets" sev="W" name="Property" msg='Die Positionen "Property type" und "Property location" sind für CIC Kategorie 9 - Immobilien zu befüllen.'>
			<note>Immobilien (CIC ##9#): Property type (C3020PK) und location (C3030PK) müssen gemeldet sein.</note>
			<cond>$like('9#') and not($has($a/Wert_C3020PK) and $has($a/Wert_C3030PK))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ($like('9#') and not($has($a/Wert_C3020PK) and $has($a/Wert_C3030PK))) then $p else ()</xpath>
		</rule>
		<rule id="PK_247" old="" grp="assets" sev="W" name="Look-Through" msg="Vermögenswerte mit CIC-Kategorie 4 müssen durchgerechnet werden.">
			<note>Investmentfonds (CIC ##4#) müssen in PK.006.03 durchgerechnet werden.</note>
			<cond>$like('4#') and empty($lts($vrg, $p/Wert_C0010PK))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ($like('4#') and empty($lts($vrg, $p/Wert_C0010PK))) then $p else ()</xpath>
		</rule>
		<rule id="PK_249" old="" grp="assets" sev="W" name="Derivate" msg="Derivate müssen in der List of Assets und in der Derivate-Liste gemeldet werden.">
			<note>Derivate (CIC ##A#–##F#) müssen in PK.008.01.01.01 gemeldet sein.</note>
			<cond>$like('A#|B#|C#|D#|E#|F#') and empty(/*/*[starts-with(name(), 'PK.008.01.01.01_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK])</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ($like('A#|B#|C#|D#|E#|F#') and empty($doc/*/*[starts-with(name(), 'PK.008.01.01.01_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK])) then $p else ()</xpath>
		</rule>
		<rule id="PK_251" old="" grp="assets" sev="W" name="Derivate" msg="Derivate müssen in der List of Assets und in der Derivate-Liste gemeldet werden.">
			<note>Derivate (CIC ##A#–##F#) müssen in PK.008.01.01.02 gemeldet sein.</note>
			<cond>$like('A#|B#|C#|D#|E#|F#') and empty(/*/*[starts-with(name(), 'PK.008.01.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK])</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ($like('A#|B#|C#|D#|E#|F#') and empty($doc/*/*[starts-with(name(), 'PK.008.01.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK])) then $p else ()</xpath>
		</rule>
		<rule id="PK_253" old="" grp="assets" sev="E" name="Derivate" msg="Derivate werden in der Derivate-Liste einzeilig gemeldet.">
			<note>Derivate dürfen in PK.008.01.01.01 nur einmal gemeldet sein.</note>
			<cond>$like('A#|B#|C#|D#|E#|F#') and count(/*/*[starts-with(name(), 'PK.008.01.01.01_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK]) gt 1</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ($like('A#|B#|C#|D#|E#|F#') and count($doc/*/*[starts-with(name(), 'PK.008.01.01.01_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK]) gt 1) then $p else ()</xpath>
		</rule>
		<rule id="PK_254" old="" grp="assets" sev="E" name="Derivate" msg="Derivate werden in der Derivate-Liste einzeilig gemeldet.">
			<note>Derivate dürfen in PK.008.01.01.02 nur einmal gemeldet sein.</note>
			<cond>$like('A#|B#|C#|D#|E#|F#') and count(/*/*[starts-with(name(), 'PK.008.01.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK]) gt 1</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ($like('A#|B#|C#|D#|E#|F#') and count($doc/*/*[starts-with(name(), 'PK.008.01.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK]) gt 1) then $p else ()</xpath>
		</rule>
		<rule id="PK_255" old="" grp="assets" sev="E" name="Asset List" msg="Vermögenswerte werden in der Tabelle PK.006.02.01.02 (Informationen zum Vermögenswert) einzeilig gemeldet.">
			<note>Jeder Vermögenswert ist in PK.006.02.01.02 genau einmal zu melden.</note>
			<cond>count(/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK]) ne 1</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (count($doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK]) ne 1) then $p else ()</xpath>
		</rule>
		<rule id="PK_269" old="VDL084" grp="assets" sev="E" name="Item Title, Issuer Name" msg="The text '…' should not start or end with space characters.">
			<note>Bezeichnung (C0130) und Emittentenname (C0140) dürfen nicht mit Leerzeichen beginnen oder enden.</note>
			<cond>exists($a) and (matches(string($a/Wert_C0130), '^\s|\s$') or matches(string($a/Wert_C0140), '^\s|\s$'))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (exists($a) and (matches(string($a/Wert_C0130), '^\s|\s$') or matches(string($a/Wert_C0140), '^\s|\s$'))) then $p else ()</xpath>
		</rule>
		<rule id="PEV31" old="VDL039" grp="assets" sev="W" name="Issue Date" msg="Issue date should be equal to or before the Reporting reference date.">
			<note>Das Ausgabedatum (EC0271) darf nicht nach dem Stichtag liegen.</note>
			<cond>exists($date($a/Wert_EC0271)) and $date($a/Wert_EC0271) gt $ref</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (exists($date($a/Wert_EC0271)) and $date($a/Wert_EC0271) gt $ref) then $p else ()</xpath>
		</rule>
		<rule id="PEV58" old="VDL041" grp="assets" sev="W" name="Write-offs/write-downs" msg='The item "Write-offs/write-downs" was not reported'>
			<note>CIC ##8# oder Anleihen/Aktien mit Instrumentenklassifikation A2N/A30: EC0141 muss gemeldet sein.</note>
			<cond>($like('8#') or ($like('(1|2|3|5|6)#') and normalize-space($a/Wert_EC0232) = ('A2N', 'A30'))) and not($has($p/Wert_EC0141))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (($like('8#') or ($like('(1|2|3|5|6)#') and normalize-space($a/Wert_EC0232) = ('A2N', 'A30'))) and not($has($p/Wert_EC0141))) then $p else ()</xpath>
		</rule>
		<rule id="PEV59" old="VDL042" grp="assets" sev="W" name="Counterparty Sector according to ESA 2010" msg="The item &quot;counterparty sector according to ESA 2010&quot; shouldn't be reported if CIC is like &quot;##4#&quot;, &quot;##9#&quot; or &quot;##09&quot; or for CIC is like &quot;##1#&quot;, &quot;##2#&quot;, &quot;##3#&quot;, &quot;##5#&quot; or &quot;##6#&quot; where ISIN is reported.">
			<note>Derivate, ##4#, ##9#, ##09 oder ISIN-Wertpapiere ##1#–##6#: Gegenparteisektor ESVG 2010 (EC0172) darf nicht gemeldet sein („Not applicable“ ist zulässig).</note>
			<cond>($like('A#|B#|C#|D#|E#|F#|4#|9#|09') or ($isin and $like('(1|2|3|5|6)#'))) and $real($a/Wert_EC0172)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (($like('A#|B#|C#|D#|E#|F#|4#|9#|09') or ($isin and $like('(1|2|3|5|6)#'))) and $real($a/Wert_EC0172)) then $p else ()</xpath>
		</rule>
		<rule id="PEV60" old="VDL043" grp="assets" sev="E" name="Counterparty Sector according to ESA 2010" msg='The item "counterparty sector according to ESA 2010" shall be equal to 1 (central bank (ESA 2010 sector S.121)), 2 (deposit-taking corporations except the central bank (ESA 2010 sector S.122)), 3 (money market funds (ESA 2010 sector S.123)), 4 (investment funds other than money market funds (ESA 2010 sector S.124)), 5 (other financial intermediaries, except insurance corporations and pension funds (excluding financial vehicle corporations engaged in securitisation transactions) + financial auxiliaries + captive financial institutions and money lenders (ESA 2010 sector S.125 excluding FVCs + ESA 2010 sector S.126 + ESA 2010 sector S.127)), 6 (financial vehicle corporations engaged in securitisation transactions (a subdivision of ESA 2010 sector S.125)), 7 (insurance corporations (ESA 2010 sector S.128)), 8 (pension funds (ESA 2010 sector S.129)), 9 (non-financial corporations (ESA 2010 sector S.11)), 10 (general government (ESA 2010 sector S.13)), 11 (households and non-profit institutions serving households (ESA 2010 sector S.14 + ESA 2010 sector S.15)).'>
			<note>CIC ##8# oder Wertpapiere ohne ISIN (##1#–##6#): Gegenparteisektor (EC0172) muss S12K, S124, S120, S128, S129, S11, S13 oder S1M sein.</note>
			<cond>($like('8#') or (not($isin) and $like('(1|2|3|5|6)#'))) and not(normalize-space($a/Wert_EC0172) = ('S12K', 'S124', 'S120', 'S128', 'S129', 'S11', 'S13', 'S1M'))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (($like('8#') or (not($isin) and $like('(1|2|3|5|6)#'))) and not(normalize-space($a/Wert_EC0172) = ('S12K', 'S124', 'S120', 'S128', 'S129', 'S11', 'S13', 'S1M'))) then $p else ()</xpath>
		</rule>
		<rule id="PEV61" old="VDL044" grp="assets" sev="E" name="Counterparty Sector according to ESA 2010" msg='The item "counterparty sector according to ESA 2010" shall be equal to 12 (central bank (ESA 2010 sector S.121), deposit-taking corporations except the central bank (ESA 2010 sector S.122) and money market funds (ESA 2010 sector S.123)), 13 (non-MFIs, not included under 12).'>
			<note>CIC ##72, ##73, ##74: Gegenparteisektor (EC0172) muss S12K, S124, S120, S128, S129, S11, S13 oder S1M sein.</note>
			<cond>$like('72|73|74') and not(normalize-space($a/Wert_EC0172) = ('S12K', 'S124', 'S120', 'S128', 'S129', 'S11', 'S13', 'S1M'))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ($like('72|73|74') and not(normalize-space($a/Wert_EC0172) = ('S12K', 'S124', 'S120', 'S128', 'S129', 'S11', 'S13', 'S1M'))) then $p else ()</xpath>
		</rule>
		<rule id="PEV62" old="VDL045" grp="assets" sev="E" name="Counterparty Sector according to ESA 2010" msg='The item "Counterparty sector according to ESA 2010" should be reported.'>
			<note>CIC ##7#, ##8# oder Wertpapiere ohne ISIN: Gegenparteisektor ESVG 2010 (EC0172) muss gemeldet sein.</note>
			<cond>($like('(7|8)#') or (not($isin) and $like('(1|2|3|5|6)#'))) and not($has($a/Wert_EC0172))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (($like('(7|8)#') or (not($isin) and $like('(1|2|3|5|6)#'))) and not($has($a/Wert_EC0172))) then $p else ()</xpath>
		</rule>
		<rule id="PEV63" old="VDL046" grp="assets" sev="W" name="Country of residence for collective investment undertakings" msg='"Country of residence for collective investment undertakings” should not be reported if CIC not like “##4#”.'>
			<note>ISIN-Wertpapiere außer Fonds: Sitzland des Investmentfonds (EC0211) darf nicht gemeldet sein („Not applicable“ ist zulässig).</note>
			<cond>(not($like('4#')) and $isin) and $real($a/Wert_EC0211)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ((not($like('4#')) and $isin) and $real($a/Wert_EC0211)) then $p else ()</xpath>
		</rule>
		<rule id="PEV64" old="VDL047" grp="assets" sev="E" name="Country of residence for collective investment undertakings" msg='The item "Country of residence for collective investment undertakings" should be reported if CIC like "##4#" for non-ISINs.'>
			<note>Fonds ohne ISIN: Sitzland des Investmentfonds (EC0211) muss gemeldet sein.</note>
			<cond>($like('4#') and not($isin)) and not($has($a/Wert_EC0211))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (($like('4#') and not($isin)) and not($has($a/Wert_EC0211))) then $p else ()</xpath>
		</rule>
		<rule id="PEV65" old="VDL048" grp="assets" sev="E" name="Instrument classification according to ESA 2010" msg='The item "Instrument classification according to ESA 2010" should be reported.'>
			<note>Ohne ISIN: Instrumentenklassifikation ESVG 2010 (EC0232) muss gemeldet sein.</note>
			<cond>(not($isin)) and not($has($a/Wert_EC0232))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ((not($isin)) and not($has($a/Wert_EC0232))) then $p else ()</xpath>
		</rule>
		<rule id="PEV67" old="VDL050" grp="assets" sev="E" name="Instrument classification according to ESA 2010" msg='The item "instrument classification according to ESA 2010" shall be equal to 1 (instrument is (I), (ii) or (iii)), 2 (instrument is (iv)), 3 (instrument is (v) or (vi)), 9 (any other instrument)'>
			<note>Wertpapiere ohne ISIN (##1#–##6#): Instrumentenklassifikation (EC0232) muss A2N, A30, A51, A52 oder A53 sein.</note>
			<cond>$like('(1|2|3|5|6)#') and not($isin) and not(normalize-space($a/Wert_EC0232) = ('A2N', 'A30', 'A52', 'A51', 'A53'))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ($like('(1|2|3|5|6)#') and not($isin) and not(normalize-space($a/Wert_EC0232) = ('A2N', 'A30', 'A52', 'A51', 'A53'))) then $p else ()</xpath>
		</rule>
		<rule id="PEV68" old="VDL051" grp="assets" sev="E" name="Issue Date" msg='Issue date should be equal to or before "Maturity date".'>
			<note>Das Ausgabedatum (EC0271) darf nicht nach der Fälligkeit (C0280) liegen.</note>
			<cond>exists($date($a/Wert_EC0271)) and exists($date($a/Wert_C0280)) and $date($a/Wert_EC0271) gt $date($a/Wert_C0280)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (exists($date($a/Wert_EC0271)) and exists($date($a/Wert_C0280)) and $date($a/Wert_EC0271) gt $date($a/Wert_C0280)) then $p else ()</xpath>
		</rule>
		<rule id="PEV69" old="VDL052" grp="assets" sev="E" name="Issue Date" msg='"Issue date" should be equal to or before "Split date".'>
			<note>Das Ausgabedatum (EC0271) darf nicht nach dem Splitdatum (EC0290) liegen.</note>
			<cond>exists($date($a/Wert_EC0271)) and exists($date($a/Wert_EC0290)) and $date($a/Wert_EC0271) gt $date($a/Wert_EC0290)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (exists($date($a/Wert_EC0271)) and exists($date($a/Wert_EC0290)) and $date($a/Wert_EC0271) gt $date($a/Wert_EC0290)) then $p else ()</xpath>
		</rule>
		<rule id="PEV70" old="VDL053" grp="assets" sev="E" name="Issue Date" msg='The item "Issue date" was not reported.'>
			<note>CIC ##8# oder Anleihen ohne ISIN: Ausgabedatum (EC0271) muss gemeldet sein.</note>
			<cond>($like('8#') or (not($isin) and $like('(1|2|5|6)#'))) and not($has($a/Wert_EC0271))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (($like('8#') or (not($isin) and $like('(1|2|5|6)#'))) and not($has($a/Wert_EC0271))) then $p else ()</xpath>
		</rule>
		<rule id="PEV71" old="VDL054" grp="assets" sev="W" name="Issue Date" msg='The item "Issue date" was reported.'>
			<note>CIC ##3#, ##4#, ##7#, ##9#, ##09 oder ISIN-Anleihen: Ausgabedatum (EC0271) darf nicht gemeldet sein („Not applicable“ ist zulässig).</note>
			<cond>($like('3#|4#|7#|9#|09') or ($isin and $like('(1|2|5|6)#'))) and $real($a/Wert_EC0271)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (($like('3#|4#|7#|9#|09') or ($isin and $like('(1|2|5|6)#'))) and $real($a/Wert_EC0271)) then $p else ()</xpath>
		</rule>
		<rule id="PEV72" old="VDL055" grp="assets" sev="E" name="Split date oder Split Faktor" msg='Either "split date" or "split factor" was reported while the other was not.'>
			<note>Splitdatum (EC0290) und Splitfaktor (EC0300) nur gemeinsam melden.</note>
			<cond>$has($a/Wert_EC0290) ne $has($a/Wert_EC0300)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ($has($a/Wert_EC0290) ne $has($a/Wert_EC0300)) then $p else ()</xpath>
		</rule>
		<rule id="PEV74" old="VDL057" grp="assets" sev="W" name="Split date oder Split Faktor" msg="&quot;Split date&quot; or &quot;split factor&quot; is reported and CIC is not like '##3#' or '##4#' and no ISIN was reported.">
			<note>CAU/INST mit Split (EC0290/EC0300): CIC muss ##3# oder ##4# sein.</note>
			<cond>$p/Wert_C0020PK = 'CAU/INST' and ($has($a/Wert_EC0290) or $has($a/Wert_EC0300)) and not($like('3#|4#'))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ($p/Wert_C0020PK = 'CAU/INST' and ($has($a/Wert_EC0290) or $has($a/Wert_EC0300)) and not($like('3#|4#'))) then $p else ()</xpath>
		</rule>
		<rule id="PEV114" old="" grp="assets" sev="W" name="Issue Price and Accrued Interest" msg="Accrued interest filed should be empty in case Issue price is reported.">
			<note>Ist ein Ausgabepreis (EC0272) gemeldet, müssen die Stückzinsen (C0090) leer sein.</note>
			<cond>$has($a/Wert_EC0272) and $has($p/Wert_C0090)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ($has($a/Wert_EC0272) and $has($p/Wert_C0090)) then $p else ()</xpath>
		</rule>
		<rule id="PK_9" old="VA00016" grp="lt" sev="E" name="Type Of Code bei CAU/ISIN" msg="Wird beim Type of Code 'CAU/ISIN' angegeben, so ist im Feld Asset ID Code die ISIN um 4 Stellen zu erweitern (4. letzte Stellen = '+XXX') (plus und Währungscode)">
			<note>Type of Code CAU/ISIN: der Fonds-Code (C0010PK) muss ISIN + "+" + Währungscode sein.</note>
			<cond>$l/Wert_C0020PK = 'CAU/ISIN' and not(matches($l/Wert_C0010PK, '^[A-Z]{2}[0-9A-Z]{8}[0-9]{2}\+[A-Z]{3}$'))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 }
return for $l in $doc/*/*[starts-with(name(), 'PK.006.03_')]/Datenpunkt
return let $cat := normalize-space($l/Wert_C0030PK)
return if ($l/Wert_C0020PK = 'CAU/ISIN' and not(matches($l/Wert_C0010PK, '^[A-Z]{2}[0-9A-Z]{8}[0-9]{2}\+[A-Z]{3}$'))) then $l else ()</xpath>
		</rule>
		<rule id="PBV70" old="VDL034" grp="lt" sev="E" name="Issuer Country SubBestand" msg="The item &quot;IssuerCountrySubBestand&quot; should not be reported for Underlying asset category 'Mortgages and Loans' or 'Property'.">
			<note>Kategorie 8 (Hypotheken/Darlehen) und 9 (Immobilien): Emittentenland (C0040PK) darf nicht gemeldet sein.</note>
			<cond>$cat = ('8', '9') and $real($l/Wert_C0040PK)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 }
return for $l in $doc/*/*[starts-with(name(), 'PK.006.03_')]/Datenpunkt
return let $cat := normalize-space($l/Wert_C0030PK)
return if ($cat = ('8', '9') and $real($l/Wert_C0040PK)) then $l else ()</xpath>
		</rule>
		<rule id="PK_210" old="VDL067" grp="lt" sev="E" name="Listed Subbestand" msg="If “UnderlyingAssetCategorySubBestand” is '3L' the “ListedSubBestand” shall be equal to 'J'.">
			<note>Kategorie 3L (notierte Aktien): Notiert (C1090PK) muss J sein.</note>
			<cond>$cat = '3L' and normalize-space($l/Wert_C1090PK) ne 'J'</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 }
return for $l in $doc/*/*[starts-with(name(), 'PK.006.03_')]/Datenpunkt
return let $cat := normalize-space($l/Wert_C0030PK)
return if ($cat = '3L' and normalize-space($l/Wert_C1090PK) ne 'J') then $l else ()</xpath>
		</rule>
		<rule id="PK_211" old="VDL068" grp="lt" sev="E" name="Listed Subbestand" msg="If “UnderlyingAssetCategorySubBestand” is '3X' the “ListedSubBestand” shall be equal to 'N'.">
			<note>Kategorie 3X (nicht notierte Aktien): Notiert (C1090PK) muss N sein.</note>
			<cond>$cat = '3X' and normalize-space($l/Wert_C1090PK) ne 'N'</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 }
return for $l in $doc/*/*[starts-with(name(), 'PK.006.03_')]/Datenpunkt
return let $cat := normalize-space($l/Wert_C0030PK)
return if ($cat = '3X' and normalize-space($l/Wert_C1090PK) ne 'N') then $l else ()</xpath>
		</rule>
		<rule id="PK_216" old="VDL074" grp="lt" sev="E" name="Underlying asset category SubBestand" msg="Negative amounts should be classified as liabilities (“L”), except when they relate to derivatives, in which case they should be classified as derivatives (“A” to “F”).">
			<note>PNR 110/130/140/160 (Formblatt 800 oder 300): negative Bilanzwerte nur in Kategorie L oder Derivaten (A–F).</note>
			<cond>normalize-space($l/Wert_C1120PK) = ('R800-110', 'R800-130', 'R800-140', 'R800-160', 'R300-110', 'R300-130', 'R300-140', 'R300-160') and $num($l/Wert_C1100PK) lt 0 and not($cat = ('L', 'A', 'B', 'C', 'D', 'E', 'F'))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 }
return for $l in $doc/*/*[starts-with(name(), 'PK.006.03_')]/Datenpunkt
return let $cat := normalize-space($l/Wert_C0030PK)
return if (normalize-space($l/Wert_C1120PK) = ('R800-110', 'R800-130', 'R800-140', 'R800-160', 'R300-110', 'R300-130', 'R300-140', 'R300-160') and $num($l/Wert_C1100PK) lt 0 and not($cat = ('L', 'A', 'B', 'C', 'D', 'E', 'F'))) then $l else ()</xpath>
		</rule>
		<rule id="PK_219" old="VDL078" grp="lt" sev="E" name="Underlying asset category SubBestand" msg='Durchgerechnete Derivate (pro "Asset ID Code SubBestand") sind mit dem identen "Underlying asset category SubBestand" zu melden.'>
			<note>Durchgerechnete Derivate (PNR x70/x80/x90, 372–390): ein Underlying (C1010PK) darf nur eine Kategorie (C0030PK) haben.</note>
			<cond>count(distinct-values(Kategorien je C1010PK)) gt 1</cond>
			<xpath>let $doc := (/)
return let $d078 := ('R800-170', 'R300-170', 'R800-180', 'R300-180', 'R800-190', 'R300-190', 'R800-372', 'R300-372', 'R800-374', 'R300-374', 'R800-382', 'R300-382', 'R800-384', 'R300-384', 'R800-390', 'R300-390', 'R800-470', 'R300-470', 'R800-480', 'R300-480', 'R800-490', 'R300-490', 'R800-570', 'R300-570', 'R800-580', 'R300-580', 'R800-590', 'R300-590', 'R800-670', 'R300-670', 'R800-680', 'R300-680', 'R800-690', 'R300-690')
return for $t in $doc/*/*[starts-with(name(), 'PK.006.03_')]
return let $rows := $t/Datenpunkt[normalize-space(Wert_C1120PK) = $d078]
return for $u in distinct-values($rows/Wert_C1010PK ! normalize-space())
return let $g := $rows[normalize-space(Wert_C1010PK) = $u]
return if (count(distinct-values($g/Wert_C0030PK ! normalize-space())) gt 1) then $g[1] else ()</xpath>
		</rule>
		<rule id="PK_257" old="" grp="lt" sev="W" name="Issuer Code SubBestand" msg="Bei Kategorie 9 - Immobilien ist das Element leer zu belassen.">
			<note>Kategorie 9 (Immobilien): Emittentencode (C1050PK) leer lassen („Not applicable“ ist zulässig).</note>
			<cond>$cat = '9' and $real($l/Wert_C1050PK)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 }
return for $l in $doc/*/*[starts-with(name(), 'PK.006.03_')]/Datenpunkt
return let $cat := normalize-space($l/Wert_C0030PK)
return if ($cat = '9' and $real($l/Wert_C1050PK)) then $l else ()</xpath>
		</rule>
		<rule id="PK_258" old="" grp="lt" sev="W" name="Issuer Sector SubBestand" msg="Bei Kategorie 0 - Sonstige Anlagen und Kategorie 9 - Immobilien ist das Element leer zu belassen.">
			<note>Kategorie 0 und 9: Emittentensektor (C1070PK) leer lassen („Not applicable“ ist zulässig).</note>
			<cond>$cat = ('0', '9') and $real($l/Wert_C1070PK)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 }
return for $l in $doc/*/*[starts-with(name(), 'PK.006.03_')]/Datenpunkt
return let $cat := normalize-space($l/Wert_C0030PK)
return if ($cat = ('0', '9') and $real($l/Wert_C1070PK)) then $l else ()</xpath>
		</rule>
		<rule id="PK_260_1" old="" grp="lt" sev="E" name="Credit quality step (externes Rating) SubBestand" msg="Bonitätsstufe muss zumindest für die in C0030PK gemeldeten Asset Kategorien 1, 2, 5, 6 angegeben werden.">
			<note>Kategorie 1, 2, 5, 6 (Anleihen): externe Bonitätsstufe (C1150PK) muss gemeldet sein.</note>
			<cond>$cat = ('1', '2', '5', '6') and not($has($l/Wert_C1150PK))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 }
return for $l in $doc/*/*[starts-with(name(), 'PK.006.03_')]/Datenpunkt
return let $cat := normalize-space($l/Wert_C0030PK)
return if ($cat = ('1', '2', '5', '6') and not($has($l/Wert_C1150PK))) then $l else ()</xpath>
		</rule>
		<rule id="PK_260_2" old="" grp="lt" sev="E" name="Credit quality step (externes Rating) SubBestand" msg="Bonitätsstufe soll nicht '9' sein, wenn die in C0030PK gemeldeten Asset Kategorien nicht 1, 2, 5, 6 sind.">
			<note>Andere Kategorien: externe Bonitätsstufe (C1150PK) darf nicht 9 sein (wörtlich laut Spezifikation).</note>
			<cond>not($cat = ('1', '2', '5', '6')) and normalize-space($l/Wert_C1150PK) = '9'</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 }
return for $l in $doc/*/*[starts-with(name(), 'PK.006.03_')]/Datenpunkt
return let $cat := normalize-space($l/Wert_C0030PK)
return if (not($cat = ('1', '2', '5', '6')) and normalize-space($l/Wert_C1150PK) = '9') then $l else ()</xpath>
		</rule>
		<rule id="PK_261_1" old="" grp="lt" sev="E" name="Credit quality step (internes Rating) SubBestand" msg="Bonitätsstufe muss zumindest für die in C0030PK gemeldeten Asset Kategorien 1, 2, 5, 6 angegeben werden.">
			<note>Kategorie 1, 2, 5, 6 (Anleihen): interne Bonitätsstufe (C1160PK) muss gemeldet sein.</note>
			<cond>$cat = ('1', '2', '5', '6') and not($has($l/Wert_C1160PK))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 }
return for $l in $doc/*/*[starts-with(name(), 'PK.006.03_')]/Datenpunkt
return let $cat := normalize-space($l/Wert_C0030PK)
return if ($cat = ('1', '2', '5', '6') and not($has($l/Wert_C1160PK))) then $l else ()</xpath>
		</rule>
		<rule id="PK_261_2" old="" grp="lt" sev="E" name="Credit quality step (internes Rating) SubBestand" msg="Bonitätsstufe soll nicht '9' sein, wenn die in C0030PK gemeldeten Asset Kategorien nicht 1, 2, 5, 6 sind.">
			<note>Andere Kategorien: interne Bonitätsstufe (C1160PK) darf nicht 9 sein (wörtlich laut Spezifikation).</note>
			<cond>not($cat = ('1', '2', '5', '6')) and normalize-space($l/Wert_C1160PK) = '9'</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 }
return for $l in $doc/*/*[starts-with(name(), 'PK.006.03_')]/Datenpunkt
return let $cat := normalize-space($l/Wert_C0030PK)
return if (not($cat = ('1', '2', '5', '6')) and normalize-space($l/Wert_C1160PK) = '9') then $l else ()</xpath>
		</rule>
		<rule id="PBV68" old="" grp="lt" sev="W" name="Asset list, Look-Through" msg="There is at least one Collective Investment Undertaking reported in template PF.06.03 that is not reported in template PF.06.02.">
			<note>Ein durchgerechneter Fonds (C0010PK in PK.006.03) muss in PK.006.02 mit CIC gemeldet sein.</note>
			<cond>$cic = ''</cond>
			<xpath>let $doc := (/)
return for $t in $doc/*/*[starts-with(name(), 'PK.006.03_')]
return for $f in distinct-values($t/Datenpunkt/Wert_C0010PK ! normalize-space())
return let $cic := normalize-space(($doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $t/@VRG]/Datenpunkt[normalize-space(Wert_C0010PK) = $f])[1]/Wert_C0230)
return if ($cic = '') then ($t/Datenpunkt[normalize-space(Wert_C0010PK) = $f])[1] else ()</xpath>
		</rule>
		<rule id="PBV69" old="" grp="lt" sev="W" name="Asset list, Look-Through" msg="There is at least one asset reported in PF.06.03 that according to PF.06.02 doesn’t have a form of Collective Investment Undertaking.">
			<note>Ein durchgerechneter Fonds muss in PK.006.02 den CIC ##4# haben.</note>
			<cond>$cic ne '' and not(matches(substring($cic, 3, 2), '^4.$'))</cond>
			<xpath>let $doc := (/)
return for $t in $doc/*/*[starts-with(name(), 'PK.006.03_')]
return for $f in distinct-values($t/Datenpunkt/Wert_C0010PK ! normalize-space())
return let $cic := normalize-space(($doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $t/@VRG]/Datenpunkt[normalize-space(Wert_C0010PK) = $f])[1]/Wert_C0230)
return if ($cic ne '' and not(matches(substring($cic, 3, 2), '^4.$'))) then ($t/Datenpunkt[normalize-space(Wert_C0010PK) = $f])[1] else ()</xpath>
		</rule>
		<rule id="PK_58" old="V90011" grp="fb900" sev="E" name="Meldepflicht" msg="Die Anzahl der Anwartschafts- und Leistungsberechtigten ist zu befüllen.">
			<note>PNR 900-100, 110 und 120 müssen gemeldet sein.</note>
			<cond>some $r in ('R900-100', 'R900-110', 'R900-120') satisfies empty($fact('PK.900.01', $v, $r))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.900.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if (some $r in ('R900-100', 'R900-110', 'R900-120') satisfies empty($fact('PK.900.01', $v, $r))) then $t else ()</xpath>
		</rule>
		<rule id="PK_188" old="V90001" grp="fb900" sev="E" name="Anwartschafts- und Leistungsberechtigte" msg="Summe Anwartschafts- und Leistungsberechtigte ungleich Summe Einzelpositionen">
			<note>Anwartschafts- und Leistungsberechtigte: PNR 900-100 = 110 + 120.</note>
			<cond>$val('PK.900.01', $v, 'R900-100') ne $val('PK.900.01', $v, 'R900-110') + $val('PK.900.01', $v, 'R900-120')</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.900.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($val('PK.900.01', $v, 'R900-100') ne $val('PK.900.01', $v, 'R900-110') + $val('PK.900.01', $v, 'R900-120')) then ($fact('PK.900.01', $v, 'R900-100'), $t)[1] else ()</xpath>
		</rule>
		<rule id="PK_189" old="V90005" grp="fb900" sev="E" name="Anwartschafts- und Leistungsberechtigte - konsortialgeführt" msg="Summe Anwartschafts- und Leistungsberechtigte ungleich Summe Einzelpositionen">
			<note>Konsortialgeschäft: PNR 900-300 = 310 + 320.</note>
			<cond>$kons = 'J' and $val('PK.900.01', $v, 'R900-300') ne $val('PK.900.01', $v, 'R900-310') + $val('PK.900.01', $v, 'R900-320')</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.900.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($kons = 'J' and $val('PK.900.01', $v, 'R900-300') ne $val('PK.900.01', $v, 'R900-310') + $val('PK.900.01', $v, 'R900-320')) then ($fact('PK.900.01', $v, 'R900-300'), $t)[1] else ()</xpath>
		</rule>
		<rule id="PK_62" old="VA00028" grp="fb900" sev="E" name="Konsortialgeschäft" msg="Bei Bestehen eines Konsortialgeschäftes sind die entsprechenden Positionen zur Anzahl der Anwartschafts- und Leistungsberechtigten - konsortialgeführt im Formblatt 900 zu befüllen.">
			<note>VRG-Art 1–4 mit Konsortialgeschäft: PNR 900-300, 310 und 320 müssen gemeldet sein.</note>
			<cond>$art($v) = ('1', '2', '3', '4') and $kons = 'J' and (some $r in ('R900-300', 'R900-310', 'R900-320') satisfies empty($fact('PK.900.01', $v, $r)))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.900.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($art($v) = ('1', '2', '3', '4') and $kons = 'J' and (some $r in ('R900-300', 'R900-310', 'R900-320') satisfies empty($fact('PK.900.01', $v, $r)))) then $t else ()</xpath>
		</rule>
		<rule id="PK_63" old="VA00029" grp="fb900" sev="E" name="Konsortialgeschäft" msg="Bei Nichtbestehen eines Konsortialgeschäftes dürfen die entsprechenden Positionen zur Anzahl der Anwartschafts- und Leistungsberechtigten - konsortialgeführt im Formblatt 900 nicht befüllt werden.">
			<note>VRG-Art 1–4 ohne Konsortialgeschäft: PNR 900-300, 310 und 320 bleiben leer.</note>
			<cond>$art($v) = ('1', '2', '3', '4') and $kons = 'N' and (some $r in ('R900-300', 'R900-310', 'R900-320') satisfies exists($fact('PK.900.01', $v, $r)))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.900.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($art($v) = ('1', '2', '3', '4') and $kons = 'N' and (some $r in ('R900-300', 'R900-310', 'R900-320') satisfies exists($fact('PK.900.01', $v, $r)))) then $t else ()</xpath>
		</rule>
		<rule id="PK_191" old="V90009" grp="fb900" sev="E" name="Konsortialgeführte Anwartschafts- und Leistungsberechtigte" msg="Die Anzahl der konsortialgeführten Anwartschafts- und Leistungsberechtigten wurde nicht entsprechend der Konsortialquote gemeldet.">
			<note>Konsortialgeschäft: PNR 900-310 = 110 × Konsortialquote (R000-240) und 320 = 120 × Konsortialquote (Toleranz unter 1).</note>
			<cond>abs($val(310 bzw. 320) - $val(110 bzw. 120) * R000-240) gt 0.999</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.900.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($kons = 'J') then (for $pair in (['R900-310', 'R900-110'], ['R900-320', 'R900-120'])
       return if (abs($val('PK.900.01', $v, $pair?1) - $val('PK.900.01', $v, $pair?2) * $val('PK.000.01', $v, 'R000-240')) gt 0.999)
              then ($fact('PK.900.01', $v, $pair?1), $t)[1] else ()) else ()</xpath>
		</rule>
		<rule id="PK_185" old="V80017" grp="gesamt" sev="E" name="Summencheck 1" msg="Summe aller VRG ist NULL.">
			<note>Summencheck 1: jede Position des Formblatts 800 der VRG 0 = Summe über alle VRG der Art 1, 2 und 4 (Fehler über 3 €, Warnung über 0,99 €).</note>
			<cond>abs(Wert VRG 0 - Summe VRG-Art 1, 2, 4) gt 0.99</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag)))
return let $parts := distinct-values($doc/*/*[starts-with(name(), 'PK.000.01_')]/@VRG)[$art(.) = ('1', '2', '4')]
return for $rc in ('R800-100', 'R800-200', 'R800-300', 'R800-400', 'R800-500', 'R800-600', 'R800-110', 'R800-130', 'R800-140', 'R800-160', 'R800-170', 'R800-180', 'R800-190', 'R800-210', 'R800-220', 'R800-230', 'R800-310', 'R800-315', 'R800-320', 'R800-325', 'R800-330', 'R800-335', 'R800-372', 'R800-374', 'R800-382', 'R800-384', 'R800-390', 'R800-410', 'R800-420', 'R800-430', 'R800-470', 'R800-480', 'R800-490', 'R800-510', 'R800-560', 'R800-570', 'R800-580', 'R800-590', 'R800-610', 'R800-620', 'R800-630', 'R800-670', 'R800-680', 'R800-690', 'R800-800', 'R800-810', 'R800-820', 'R800-830', 'R800-835', 'R800-840', 'R800-850', 'R800-851', 'R800-852', 'R800-853', 'R800-854', 'R800-855', 'R800-856', 'R800-857', 'R800-858', 'R800-859', 'R800-860', 'R800-861', 'R800-862', 'R800-863', 'R800-864')
return let $ist := $val('PK.800.01', '0', $rc), $soll := sum(for $p in $parts return $val('PK.800.01', $p, $rc))
return if (exists($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = '0']) and exists(($parts, '0') ! $fact('PK.800.01', ., $rc)) and abs($ist - $soll) gt 0.99)
       then (($parts, '0') ! $fact('PK.800.01', ., $rc))[last()] else ()</xpath>
		</rule>
		<rule id="PK_187" old="V80021" grp="gesamt" sev="E" name="Summencheck 4 (Sub-VG)" msg="Summe pro Sub-VG ungleich gemeldeter Wert in VRG mit Sub-VG.">
			<note>Summencheck 4: jede Position des Formblatts 800 einer VRG mit Sub-VG (Art 2) = Summe ihrer Sub-VG (Art 3, Zuordnung über R000-130; Fehler über 3 €, Warnung über 0,99 €).</note>
			<cond>abs(Wert VRG mit Sub-VG - Summe ihrer Sub-VG) gt 0.99</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag)))
return let $mains := distinct-values($doc/*/*[starts-with(name(), 'PK.000.01_')]/@VRG)[$art(.) = '2'],
    $subs := distinct-values($doc/*/*[starts-with(name(), 'PK.000.01_')]/@VRG)[$art(.) = '3']
return for $m in $mains
return let $mine := $subs[normalize-space($fact('PK.000.01', ., 'R000-130')/Wert) = $m],
    $children := if (exists($mine)) then $mine else if (count($mains) = 1) then $subs else ()
return for $rc in ('R800-100', 'R800-200', 'R800-300', 'R800-400', 'R800-500', 'R800-600', 'R800-110', 'R800-130', 'R800-140', 'R800-160', 'R800-170', 'R800-180', 'R800-190', 'R800-210', 'R800-220', 'R800-230', 'R800-310', 'R800-315', 'R800-320', 'R800-325', 'R800-330', 'R800-335', 'R800-372', 'R800-374', 'R800-382', 'R800-384', 'R800-390', 'R800-410', 'R800-420', 'R800-430', 'R800-470', 'R800-480', 'R800-490', 'R800-510', 'R800-560', 'R800-570', 'R800-580', 'R800-590', 'R800-610', 'R800-620', 'R800-630', 'R800-670', 'R800-680', 'R800-690', 'R800-800', 'R800-810', 'R800-820', 'R800-830', 'R800-835', 'R800-840', 'R800-850', 'R800-851', 'R800-852', 'R800-853', 'R800-854', 'R800-855', 'R800-856', 'R800-857', 'R800-858', 'R800-859', 'R800-860', 'R800-861', 'R800-862', 'R800-863', 'R800-864')
return if (exists(($m, $children) ! $fact('PK.800.01', ., $rc)) and
           abs($val('PK.800.01', $m, $rc) - sum(for $c in $children return $val('PK.800.01', $c, $rc))) gt 0.99)
       then ($fact('PK.800.01', $m, $rc), $children ! $fact('PK.800.01', ., $rc))[1] else ()</xpath>
		</rule>
		<rule id="PK_190" old="V90006" grp="gesamt" sev="E" name="Summencheck 1" msg="Summe aller VRG ist NULL">
			<note>Summencheck: jede Position des Formblatts 900 der VRG 0 = Summe über alle VRG der Art 1, 2 und 4.</note>
			<cond>abs(Wert VRG 0 - Summe VRG-Art 1, 2, 4) ne 0</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag)))
return let $parts := distinct-values($doc/*/*[starts-with(name(), 'PK.000.01_')]/@VRG)[$art(.) = ('1', '2', '4')]
return for $rc in ('R900-100', 'R900-110', 'R900-120', 'R900-300', 'R900-310', 'R900-320')
return let $ist := $val('PK.900.01', '0', $rc), $soll := sum(for $p in $parts return $val('PK.900.01', $p, $rc))
return if (exists($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = '0']) and exists(($parts, '0') ! $fact('PK.900.01', ., $rc)) and $ist ne $soll)
       then (($parts, '0') ! $fact('PK.900.01', ., $rc))[last()] else ()</xpath>
		</rule>
		<rule id="PK_192" old="V90010" grp="gesamt" sev="E" name="Summencheck 4 (Sub-VG)" msg="Summe pro Sub-VG ungleich gemeldeter Wert in VRG mit Sub-VG.">
			<note>Jede Position des Formblatts 900 einer VRG mit Sub-VG (Art 2) = Summe ihrer Sub-VG (Art 3).</note>
			<cond>abs(Wert VRG mit Sub-VG - Summe ihrer Sub-VG) ne 0</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag)))
return let $mains := distinct-values($doc/*/*[starts-with(name(), 'PK.000.01_')]/@VRG)[$art(.) = '2'],
    $subs := distinct-values($doc/*/*[starts-with(name(), 'PK.000.01_')]/@VRG)[$art(.) = '3']
return for $m in $mains
return let $mine := $subs[normalize-space($fact('PK.000.01', ., 'R000-130')/Wert) = $m],
    $children := if (exists($mine)) then $mine else if (count($mains) = 1) then $subs else ()
return for $rc in ('R900-100', 'R900-110', 'R900-120', 'R900-300', 'R900-310', 'R900-320')
return if (exists(($m, $children) ! $fact('PK.900.01', ., $rc)) and
           $val('PK.900.01', $m, $rc) ne sum(for $c in $children return $val('PK.900.01', $c, $rc)))
       then ($fact('PK.900.01', $m, $rc), $children ! $fact('PK.900.01', ., $rc))[1] else ()</xpath>
		</rule>
		<rule id="PK_VA01" old="VA00001" grp="gesamt" sev="E" name="Meldeperiode" msg="Die Meldeperiode in der XML-Datei stimmt nicht mit der Meldeperiode im Dateinamen überein.">
			<note>Die Meldeperiode im Dateinamen (8-stelliges Datum) muss dem Stichtag (GJ + Quartal) entsprechen.</note>
			<cond>Datum im Dateinamen ne Stichtag</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag)))
return let $fd := tokenize(tokenize(string(base-uri($doc)), '[/\\]')[last()], '_')[matches(., '^\d{8}$')][1]
return if (exists($fd) and $fd ne format-date($ref, '[Y0001][M01][D01]')) then $doc/* else ()</xpath>
		</rule>
		<rule id="PK_VA02" old="VA00002" grp="gesamt" sev="E" name="PK-Nummer" msg='Es wurde eine ungültige PK-Nummer gemeldet (PK-Nummer "1" ist nur für die Test-PK FMA-intern vorgesehen).'>
			<note>PK-Nummer 1 ist der FMA-internen Test-PK vorbehalten.</note>
			<cond>/*/@PK-Nummer = '1'</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag)))
return if (string($doc/*/@PK-Nummer) = '1') then $doc/* else ()</xpath>
		</rule>
		<rule id="PK_VA04" old="VA00004" grp="gesamt" sev="E" name="Checksumme LEI-Code" msg="Der LEI-Code hat eine ungültige Checksumme.">
			<note>LEI-Prüfziffer (ISO 17442, MOD 97-10): Buchstaben in Zahlen umsetzen (A = 10 …), der Rest mod 97 muss 1 sein.</note>
			<cond>xs:integer(LEI in Ziffern) mod 97 ne 1</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag)))
return let $lei := replace(normalize-space($doc/*/@LEI), '^LEI/', '')
return if ($lei ne '' and (if (not(matches($lei, '^[A-Z0-9]{18}[0-9]{2}$'))) then true()
       else xs:integer(string-join(for $c in string-to-codepoints($lei) return if ($c ge 65) then string($c - 55) else codepoints-to-string($c))) mod 97 ne 1)) then $doc/* else ()</xpath>
		</rule>
		<rule id="PK_VA07" old="VA00007" grp="gesamt" sev="E" name="Mehrfachmeldung von VRG" msg="Es wurde eine VRG Nummer mehrfach gemeldet.">
			<note>Jede VRG darf nur einmal (ein PK.000.01 je VRG) gemeldet werden.</note>
			<cond>count(PK.000.01[@VRG = $v]) gt 1</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag)))
return for $v in distinct-values($doc/*/*[starts-with(name(), 'PK.000.01_')]/@VRG)
return if (count($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $v]) gt 1) then $doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $v] else ()</xpath>
		</rule>
		<rule id="PK_VA23" old="" grp="gesamt" sev="E" name="LEI-Code (Attribut)" msg="Es wurde kein LEI im XML Attribut LEI gemeldet!">
			<note>Das Attribut LEI muss gemeldet sein.</note>
			<cond>replace(normalize-space(/*/@LEI), '^LEI/', '') = ''</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag)))
return if (replace(normalize-space($doc/*/@LEI), '^LEI/', '') = '') then $doc/* else ()</xpath>
		</rule>
		<rule id="PK_VA28" old="" grp="gesamt" sev="E" name="PK-Nummer (Attribut)" msg="Die gemeldete PK-Nummer im Attribut 'PK-Nummer' entspricht nicht der PK-Nummer im Dateinamen.">
			<note>Die PK-Nummer im Dateinamen (2. Teil) muss dem Attribut PK-Nummer entsprechen.</note>
			<cond>tokenize(Dateiname, '_')[2] ne @PK-Nummer</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag)))
return let $file := tokenize(string(base-uri($doc)), '[/\\]')[last()]
return if (matches($file, '^\d+_\d+_') and tokenize($file, '_')[2] ne string($doc/*/@PK-Nummer)) then $doc/* else ()</xpath>
		</rule>
		<rule id="PK_VA12" old="VA00032" grp="stamm" sev="E" name="Mehrfachmeldung von PNR" msg="Es wurde eine PNR Nummer mehrfach gemeldet.">
			<note>Eine Positionsnummer (RowCode) darf je VRG in PK.000.01, PK.800.01 und PK.900.01 nur einmal vorkommen.</note>
			<cond>(let $codes := /*/*[@VRG = $v][starts-with(name(), 'PK.000.01_') or starts-with(name(), 'PK.800.01_') or starts-with(name(), 'PK.900.01_')]/*/RowCode ! normalize-space()
  return count($codes) ne count(distinct-values($codes)))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) },
    $has006 := function($vrg) { exists(($doc/*/*[starts-with(name(), 'PK.006.02.01.01_')] | $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')])[@VRG = $vrg]/Datenpunkt) }
return for $t in $doc/*/*[starts-with(name(), 'PK.000.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ((let $codes := $doc/*/*[@VRG = $v][starts-with(name(), 'PK.000.01_') or starts-with(name(), 'PK.800.01_') or starts-with(name(), 'PK.900.01_')]/*/RowCode ! normalize-space()
  return count($codes) ne count(distinct-values($codes)))) then $t else ()</xpath>
		</rule>
		<rule id="PK_VA22" old="" grp="assets" sev="E" name="ISIN-Prüfziffer" msg="Die ISIN hat eine ungültige Checksumme (gilt auch für CAU/ISIN, PK_VA26).">
			<note>Bei ISIN und CAU/ISIN muss die Prüfziffer der ISIN (Luhn über die in Ziffern umgesetzten ersten 11 Zeichen) stimmen.</note>
			<cond>$isin and (let $s := substring($p/Wert_C0010PK, 1, 12) return
  if (not(matches($s, '^[A-Z]{2}[A-Z0-9]{9}[0-9]$'))) then true() else
  let $d := string-join(for $c in string-to-codepoints(substring($s, 1, 11))
                        return if ($c ge 65) then string($c - 55) else codepoints-to-string($c)),
      $r := reverse(for $c in string-to-codepoints($d) return $c - 48),
      $sum := sum(for $i in 1 to count($r) return if ($i mod 2 = 1)
                  then (if ($r[$i] * 2 gt 9) then $r[$i] * 2 - 9 else $r[$i] * 2) else $r[$i])
  return (10 - $sum mod 10) mod 10 ne xs:integer(substring($s, 12, 1)))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if ($isin and (let $s := substring($p/Wert_C0010PK, 1, 12) return
  if (not(matches($s, '^[A-Z]{2}[A-Z0-9]{9}[0-9]$'))) then true() else
  let $d := string-join(for $c in string-to-codepoints(substring($s, 1, 11))
                        return if ($c ge 65) then string($c - 55) else codepoints-to-string($c)),
      $r := reverse(for $c in string-to-codepoints($d) return $c - 48),
      $sum := sum(for $i in 1 to count($r) return if ($i mod 2 = 1)
                  then (if ($r[$i] * 2 gt 9) then $r[$i] * 2 - 9 else $r[$i] * 2) else $r[$i])
  return (10 - $sum mod 10) mod 10 ne xs:integer(substring($s, 12, 1)))) then $p else ()</xpath>
		</rule>
		<rule id="INT_VRGNR" old="extra" grp="stamm" sev="W" name="VRG-Nummer" msg="R000-100 (VRG-Nummer) stimmt nicht mit dem Attribut VRG überein.">
			<note>Zusätzlich: die VRG-Nummer in R000-100 muss dem Attribut VRG entsprechen.</note>
			<cond>normalize-space($fact('PK.000.01', $v, 'R000-100')/Wert) ne $v</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) },
    $has006 := function($vrg) { exists(($doc/*/*[starts-with(name(), 'PK.006.02.01.01_')] | $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')])[@VRG = $vrg]/Datenpunkt) }
return for $t in $doc/*/*[starts-with(name(), 'PK.000.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if (normalize-space($fact('PK.000.01', $v, 'R000-100')/Wert) ne $v) then $t else ()</xpath>
		</rule>
		<rule id="INT_PARENT" old="extra" grp="stamm" sev="E" name="Sub-VG ohne VRG mit Sub-VG" msg="Die in R000-130 angegebene VRG existiert nicht oder ist keine VRG mit Sub-VG (VRG-Art 2).">
			<note>Zusätzlich: eine Sub-VG (VRG-Art 3) muss in R000-130 eine gemeldete VRG mit Sub-VG (VRG-Art 2) angeben.</note>
			<cond>$art($v) = '3' and (let $par := normalize-space($fact('PK.000.01', $v, 'R000-130')/Wert)
  return not(exists(/*/*[@VRG = $par]) and $art($par) = '2'))</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) },
    $has006 := function($vrg) { exists(($doc/*/*[starts-with(name(), 'PK.006.02.01.01_')] | $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')])[@VRG = $vrg]/Datenpunkt) }
return for $t in $doc/*/*[starts-with(name(), 'PK.000.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($art($v) = '3' and (let $par := normalize-space($fact('PK.000.01', $v, 'R000-130')/Wert)
  return not(exists($doc/*/*[@VRG = $par]) and $art($par) = '2'))) then $t else ()</xpath>
		</rule>
		<rule id="INT_A02" old="extra" grp="assets" sev="W" name="Vermögenswert ohne Position" msg="Für den Vermögenswert in PK.006.02.01.02 gibt es keine Position in PK.006.02.01.01.">
			<note>Zusätzlich: zu jedem Vermögenswert in PK.006.02.01.02 muss es eine Position in PK.006.02.01.01 geben.</note>
			<cond>empty(Position mit gleichem C0010PK in derselben VRG)</cond>
			<xpath>let $doc := (/)
return for $a in $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')]/Datenpunkt
return if (empty($doc/*/*[starts-with(name(), 'PK.006.02.01.01_')][@VRG = $a/../@VRG]/Datenpunkt[Wert_C0010PK = $a/Wert_C0010PK]))
       then $a else ()</xpath>
		</rule>
		<rule id="INT_LTMW" old="extra" grp="assets" sev="E" name="Marktwert vs. Look-through" msg="Marktwert der Position (C1110PK) ungleich Summe Marktwert der Durchrechnung (PK.006.03 C1110PK).">
			<note>Zusätzlich: Marktwert (C1110PK) eines durchgerechneten Fonds = Summe Marktwert (C1110PK) der Durchrechnung.</note>
			<cond>exists($lts($vrg, $p/Wert_C0010PK)) and abs($num($p/Wert_C1110PK) - sum(for $x in $lts($vrg, $p/Wert_C0010PK)/Wert_C1110PK return $num($x))) gt 0.99</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (exists($lts($vrg, $p/Wert_C0010PK)) and abs($num($p/Wert_C1110PK) - sum(for $x in $lts($vrg, $p/Wert_C0010PK)/Wert_C1110PK return $num($x))) gt 0.99) then $p else ()</xpath>
		</rule>
		<rule id="INT_LT2" old="extra" grp="assets" sev="W" name="Durchrechnungsquote Einzelpapier" msg="Nicht durchgerechneter Anteil (Kategorie 4 in PK.006.03) eines Fonds übersteigt den Grenzwert in % der VRG.">
			<note>Zusätzlich: nicht durchgerechneter Anteil eines Fonds (Kategorie 4 in PK.006.03, Marktwert) in % von PNR 800 der VRG; Warnung über 2 %.</note>
			<cond>exists($lts($vrg, $p/Wert_C0010PK)) and (let $fb := $num(/*/*[starts-with(name(), 'PK.800.01_')][@VRG = $vrg]/*[normalize-space(RowCode) = 'R800-800'][1]/Wert)
  return $fb ne 0 and sum(for $x in $lts($vrg, $p/Wert_C0010PK)[normalize-space(Wert_C0030PK) = '4']/Wert_C1110PK return $num($x)) div $fb * 100 gt 2)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $date := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:date) then xs:date($s) else () },
    $ref := xs:date($doc/*/@GJ || map { 'Q1': '-03-31', 'Q2': '-06-30', 'Q3': '-09-30', 'Q4': '-12-31' }(string($doc/*/@Stichtag))),
    $cicLike := function($cic, $pattern) { matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$') }
return for $p in $doc/*/*[starts-with(name(), 'PK.006.02.01.01_')]/Datenpunkt
return let $vrg := string($p/../@VRG),
    $a := $doc/*/*[starts-with(name(), 'PK.006.02.01.02_')][@VRG = $vrg]/Datenpunkt[Wert_C0010PK = $p/Wert_C0010PK][1],
    $cic := normalize-space($a/Wert_C0230),
    $isin := $p/Wert_C0020PK = ('ISIN', 'CAU/ISIN'),
    $art := normalize-space($doc/*/*[starts-with(name(), 'PK.000.01_')][@VRG = $vrg]/*[RowCode = 'R000-120'][1]/Wert),
    $lts := function($v, $id) { $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[Wert_C0010PK = $id] },
    $like := $cicLike($cic, ?)
return if (exists($lts($vrg, $p/Wert_C0010PK)) and (let $fb := $num($doc/*/*[starts-with(name(), 'PK.800.01_')][@VRG = $vrg]/*[normalize-space(RowCode) = 'R800-800'][1]/Wert)
  return $fb ne 0 and sum(for $x in $lts($vrg, $p/Wert_C0010PK)[normalize-space(Wert_C0030PK) = '4']/Wert_C1110PK return $num($x)) div $fb * 100 gt 2)) then $p else ()</xpath>
		</rule>
		<rule id="INT_LT10" old="extra" grp="alloc" sev="W" name="Durchrechnungsquote gesamt" msg="Nicht durchgerechneter Anteil (Kategorie 4 in PK.006.03) der VRG übersteigt den Grenzwert.">
			<note>Zusätzlich: nicht durchgerechneter Anteil der VRG (Kategorie 4 in PK.006.03, Marktwert) in % von PNR 800; Warnung über 10 %.</note>
			<cond>$val('PK.800.01', $v, 'R800-800') ne 0 and exists(/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt) and sum(for $x in /*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[normalize-space(Wert_C0030PK) = '4']/Wert_C1110PK return $num($x)) div $val('PK.800.01', $v, 'R800-800') * 100 gt 10</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.800.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($art($v) = ('1', '2', '3', '4') and $val('PK.800.01', $v, 'R800-800') ne 0 and exists($doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt) and sum(for $x in $doc/*/*[starts-with(name(), 'PK.006.03_')][@VRG = $v]/Datenpunkt[normalize-space(Wert_C0030PK) = '4']/Wert_C1110PK return $num($x)) div $val('PK.800.01', $v, 'R800-800') * 100 gt 10) then $t else ()</xpath>
		</rule>
		<rule id="INT_AA1" old="extra" grp="alloc" sev="E" name="Anleihen HY inkl. Not Rated" msg="(PNR 315 + 325 + 335) in % von PNR 300 – Grenzwert 25 %.">
			<note>Zusätzlich: Anleihen High Yield inkl. Not Rated (PNR 315 + 325 + 335) in % von PNR 300; Fehler über 25 %.</note>
			<cond>$val('PK.800.01', $v, 'R800-300') ne 0 and (sum(for $r in ('R800-315', 'R800-325', 'R800-335') return $val('PK.800.01', $v, $r)) div $val('PK.800.01', $v, 'R800-300') * 100) gt 25</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.800.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($art($v) = ('1', '2', '3', '4') and $val('PK.800.01', $v, 'R800-300') ne 0 and (sum(for $r in ('R800-315', 'R800-325', 'R800-335') return $val('PK.800.01', $v, $r)) div $val('PK.800.01', $v, 'R800-300') * 100) gt 25) then $t else ()</xpath>
		</rule>
		<rule id="INT_AA2" old="extra" grp="alloc" sev="I" name="Fremdwährungsquote vor Derivaten" msg="PNR 830 in % von PNR 800 (nur Information).">
			<note>Zusätzlich, nur Information: Fremdwährungsquote vor Derivaten (PNR 830 in % von PNR 800), kein Grenzwert.</note>
			<cond>PNR 830 div PNR 800 * 100</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.800.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($art($v) = ('1', '2', '3', '4') and $val('PK.800.01', $v, 'R800-800') ne 0) then $v || ': ' || round((sum(for $r in ('R800-830') return $val('PK.800.01', $v, $r)) div $val('PK.800.01', $v, 'R800-800') * 100), 2) || ' %' else ()</xpath>
		</rule>
		<rule id="INT_AA3" old="extra" grp="alloc" sev="E" name="Fremdwährungsquote nach Derivaten" msg="PNR 835 in % von PNR 800 – Grenzwert 30 %, nicht negativ.">
			<note>Zusätzlich: Fremdwährungsquote nach Derivaten (PNR 835 in % von PNR 800); Fehler über 30 % oder negativ.</note>
			<cond>$val('PK.800.01', $v, 'R800-800') ne 0 and ((sum(for $r in ('R800-835') return $val('PK.800.01', $v, $r)) div $val('PK.800.01', $v, 'R800-800') * 100) gt 30 or (sum(for $r in ('R800-835') return $val('PK.800.01', $v, $r)) div $val('PK.800.01', $v, 'R800-800') * 100) lt 0)</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.800.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($art($v) = ('1', '2', '3', '4') and $val('PK.800.01', $v, 'R800-800') ne 0 and ((sum(for $r in ('R800-835') return $val('PK.800.01', $v, $r)) div $val('PK.800.01', $v, 'R800-800') * 100) gt 30 or (sum(for $r in ('R800-835') return $val('PK.800.01', $v, $r)) div $val('PK.800.01', $v, 'R800-800') * 100) lt 0)) then $t else ()</xpath>
		</rule>
		<rule id="INT_AA4" old="extra" grp="alloc" sev="E" name="Aktienquote" msg="(PNR 410 + 420 + 430 + 480) in % von PNR 800 – Grenzwert 50 %.">
			<note>Zusätzlich: Aktienquote (PNR 410 + 420 + 430 + 480 in % von PNR 800); Fehler über 50 %.</note>
			<cond>$val('PK.800.01', $v, 'R800-800') ne 0 and (sum(for $r in ('R800-410', 'R800-420', 'R800-430', 'R800-480') return $val('PK.800.01', $v, $r)) div $val('PK.800.01', $v, 'R800-800') * 100) gt 50</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.800.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($art($v) = ('1', '2', '3', '4') and $val('PK.800.01', $v, 'R800-800') ne 0 and (sum(for $r in ('R800-410', 'R800-420', 'R800-430', 'R800-480') return $val('PK.800.01', $v, $r)) div $val('PK.800.01', $v, 'R800-800') * 100) gt 50) then $t else ()</xpath>
		</rule>
		<rule id="INT_AA5" old="extra" grp="alloc" sev="E" name="Leverage" msg="(PNR 382 + 384 + 480 + 580 + 680 − 100) in % von PNR 800 – Grenzwert 0 %.">
			<note>Zusätzlich: Leverage (PNR 382 + 384 + 480 + 580 + 680 − PNR 100, in % von PNR 800); Fehler über 0 %.</note>
			<cond>$val('PK.800.01', $v, 'R800-800') ne 0 and (sum(for $r in ('R800-382', 'R800-384', 'R800-480', 'R800-580', 'R800-680') return $val('PK.800.01', $v, $r)) - $val('PK.800.01', $v, 'R800-100')) div $val('PK.800.01', $v, 'R800-800') * 100 gt 0</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.800.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($art($v) = ('1', '2', '3', '4') and $val('PK.800.01', $v, 'R800-800') ne 0 and (sum(for $r in ('R800-382', 'R800-384', 'R800-480', 'R800-580', 'R800-680') return $val('PK.800.01', $v, $r)) - $val('PK.800.01', $v, 'R800-100')) div $val('PK.800.01', $v, 'R800-800') * 100 gt 0) then $t else ()</xpath>
		</rule>
		<rule id="INT_AA6" old="extra" grp="alloc" sev="E" name="Nicht zuteilbare Finanzinstrumente / Rohstoffe" msg="PNR 630 in % von PNR 800 – Warnung über 5 %, Fehler ab 10 %.">
			<note>Zusätzlich: Nicht zuteilbare Finanzinstrumente / Rohstoffe (PNR 630 in % von PNR 800); Warnung über 5 %, Fehler ab 10 %.</note>
			<cond>$val('PK.800.01', $v, 'R800-800') ne 0 and (sum(for $r in ('R800-630') return $val('PK.800.01', $v, $r)) div $val('PK.800.01', $v, 'R800-800') * 100) gt 5</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.800.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($art($v) = ('1', '2', '3', '4') and $val('PK.800.01', $v, 'R800-800') ne 0 and (sum(for $r in ('R800-630') return $val('PK.800.01', $v, $r)) div $val('PK.800.01', $v, 'R800-800') * 100) gt 5) then $t else ()</xpath>
		</rule>
		<rule id="INT_AA7" old="extra" grp="alloc" sev="E" name="Nicht geregelte Märkte" msg="PNR 840 in % von PNR 800 – Warnung über 5 %, Fehler ab 10 %.">
			<note>Zusätzlich: Nicht geregelte Märkte (PNR 840 in % von PNR 800); Warnung über 5 %, Fehler ab 10 %.</note>
			<cond>$val('PK.800.01', $v, 'R800-800') ne 0 and (sum(for $r in ('R800-840') return $val('PK.800.01', $v, $r)) div $val('PK.800.01', $v, 'R800-800') * 100) gt 5</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.800.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($art($v) = ('1', '2', '3', '4') and $val('PK.800.01', $v, 'R800-800') ne 0 and (sum(for $r in ('R800-840') return $val('PK.800.01', $v, $r)) div $val('PK.800.01', $v, 'R800-800') * 100) gt 5) then $t else ()</xpath>
		</rule>
		<rule id="INT_AA8" old="extra" grp="alloc" sev="E" name="Veranlagung bei einem Emittenten" msg="PNR 861 in % von PNR 800 – Warnung über 2,5 %, Fehler ab 5 %.">
			<note>Zusätzlich: Veranlagung bei einem Emittenten (PNR 861 in % von PNR 800); Warnung über 2,5 %, Fehler ab 5 %.</note>
			<cond>$val('PK.800.01', $v, 'R800-800') ne 0 and (sum(for $r in ('R800-861') return $val('PK.800.01', $v, $r)) div $val('PK.800.01', $v, 'R800-800') * 100) gt 2.5</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.800.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($art($v) = ('1', '2', '3', '4') and $val('PK.800.01', $v, 'R800-800') ne 0 and (sum(for $r in ('R800-861') return $val('PK.800.01', $v, $r)) div $val('PK.800.01', $v, 'R800-800') * 100) gt 2.5) then $t else ()</xpath>
		</rule>
		<rule id="INT_AA9" old="extra" grp="alloc" sev="E" name="Veranlagung bei einer Unternehmensgruppe" msg="PNR 862 in % von PNR 800 – Warnung über 5 %, Fehler ab 10 %.">
			<note>Zusätzlich: Veranlagung bei einer Unternehmensgruppe (PNR 862 in % von PNR 800); Warnung über 5 %, Fehler ab 10 %.</note>
			<cond>$val('PK.800.01', $v, 'R800-800') ne 0 and (sum(for $r in ('R800-862') return $val('PK.800.01', $v, $r)) div $val('PK.800.01', $v, 'R800-800') * 100) gt 5</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.800.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($art($v) = ('1', '2', '3', '4') and $val('PK.800.01', $v, 'R800-800') ne 0 and (sum(for $r in ('R800-862') return $val('PK.800.01', $v, $r)) div $val('PK.800.01', $v, 'R800-800') * 100) gt 5) then $t else ()</xpath>
		</rule>
		<rule id="INT_AA10" old="extra" grp="alloc" sev="E" name="Guthaben bei einer Kreditinstitutsgruppe" msg="PNR 863 in % von PNR 800 – Warnung über 5 %, Fehler ab 10 %.">
			<note>Zusätzlich: Guthaben bei einer Kreditinstitutsgruppe (PNR 863 in % von PNR 800); Warnung über 5 %, Fehler ab 10 %.</note>
			<cond>$val('PK.800.01', $v, 'R800-800') ne 0 and (sum(for $r in ('R800-863') return $val('PK.800.01', $v, $r)) div $val('PK.800.01', $v, 'R800-800') * 100) gt 5</cond>
			<xpath>let $doc := (/)
return let $has := function($v) { exists($v[normalize-space() != '']) },
    $real := function($v) { exists($v[normalize-space() != '' and normalize-space() != 'Not applicable']) },
    $num := function($v) { let $s := normalize-space(string($v[1])) return if ($s castable as xs:decimal) then xs:decimal($s) else 0 },
    $fact := function($tpl, $vrg, $row) { ($doc/*/*[starts-with(name(), $tpl || '_')][@VRG = $vrg]/*[normalize-space(RowCode) = $row])[1] },
    $val := function($tpl, $vrg, $row) { $num($fact($tpl, $vrg, $row)/Wert) },
    $art := function($vrg) { normalize-space($fact('PK.000.01', $vrg, 'R000-120')/Wert) }
return for $t in $doc/*/*[starts-with(name(), 'PK.800.01_')]
return let $v := string($t/@VRG),
    $kons := normalize-space($fact('PK.000.01', $v, 'R000-200')/Wert)
return if ($art($v) = ('1', '2', '3', '4') and $val('PK.800.01', $v, 'R800-800') ne 0 and (sum(for $r in ('R800-863') return $val('PK.800.01', $v, $r)) div $val('PK.800.01', $v, 'R800-800') * 100) gt 5) then $t else ()</xpath>
		</rule>
		</catalog>
	</xsl:variable>
	<xsl:variable name="catalog">
		<catalog>
			<xsl:sequence select="$catalogAll/catalog/rule[$internal or not(starts-with(@id, 'INT_'))]"/>
		</catalog>
	</xsl:variable>
	<xsl:variable name="sevMap" as="map(xs:string, xs:string)"
	              select="map:merge(for $r in $catalogAll/catalog/rule return map:entry(string($r/@id), string($r/@sev)))"/>

	<!-- ===================================================================== -->
	<!-- Helper functions                                                      -->
	<!-- ===================================================================== -->
	<!-- reported: not absent, not blank ("Not applicable" is a valid, reported value) -->
	<xsl:function name="pk:has" as="xs:boolean">
		<xsl:param name="v" as="item()*"/>
		<xsl:sequence select="exists($v[normalize-space(string(.)) ne ''])"/>
	</xsl:function>

	<!-- reported with content: not blank and not "Not applicable" (used where a field must stay empty) -->
	<xsl:function name="pk:real" as="xs:boolean">
		<xsl:param name="v" as="item()*"/>
		<xsl:sequence select="exists($v[normalize-space(string(.)) ne '' and normalize-space(string(.)) ne 'Not applicable'])"/>
	</xsl:function>

	<!-- decimal value of the first item; 0 when absent or not numeric -->
	<xsl:function name="pk:d" as="xs:decimal">
		<xsl:param name="v" as="item()*"/>
		<xsl:variable name="s" select="normalize-space(string($v[1]))"/>
		<xsl:sequence select="if ($s castable as xs:decimal) then xs:decimal($s) else 0"/>
	</xsl:function>

	<!-- sum of all numeric items -->
	<xsl:function name="pk:sum" as="xs:decimal">
		<xsl:param name="v" as="item()*"/>
		<xsl:sequence select="sum(for $x in $v return pk:d($x))"/>
	</xsl:function>

	<xsl:function name="pk:date" as="xs:date?">
		<xsl:param name="v" as="item()*"/>
		<xsl:variable name="s" select="normalize-space(string($v[1]))"/>
		<xsl:sequence select="if ($s castable as xs:date) then xs:date($s) else ()"/>
	</xsl:function>

	<!-- CIC "##<pattern>": pattern applies to positions 3-4, '#' is any character -->
	<xsl:function name="pk:cic" as="xs:boolean">
		<xsl:param name="cic" as="xs:string"/>
		<xsl:param name="pattern" as="xs:string"/>
		<xsl:sequence select="matches(substring($cic, 3, 2), '^(' || translate($pattern, '#', '.') || ')$')"/>
	</xsl:function>

	<!-- first value of field "Wert_<f>" within the given data points -->
	<xsl:function name="pk:f" as="xs:string">
		<xsl:param name="r" as="node()*"/>
		<xsl:param name="f" as="xs:string"/>
		<xsl:sequence select="normalize-space(string(($r/*[local-name() eq 'Wert_' || $f])[1]))"/>
	</xsl:function>

	<!-- fact of a closed template -->
	<xsl:function name="pk:fact" as="element()?">
		<xsl:param name="tpl" as="xs:string"/>
		<xsl:param name="vrg" as="xs:string"/>
		<xsl:param name="row" as="xs:string"/>
		<xsl:sequence select="key('fact', $tpl || '|' || $vrg || '|' || $row, $doc)[1]"/>
	</xsl:function>
	<xsl:function name="pk:factValue" as="xs:string">
		<xsl:param name="tpl" as="xs:string"/>
		<xsl:param name="vrg" as="xs:string"/>
		<xsl:param name="row" as="xs:string"/>
		<xsl:sequence select="normalize-space(string(pk:fact($tpl, $vrg, $row)/Wert))"/>
	</xsl:function>
	<xsl:function name="pk:factNum" as="xs:decimal">
		<xsl:param name="tpl" as="xs:string"/>
		<xsl:param name="vrg" as="xs:string"/>
		<xsl:param name="row" as="xs:string"/>
		<xsl:sequence select="pk:d(pk:fact($tpl, $vrg, $row)/Wert)"/>
	</xsl:function>
	<xsl:function name="pk:factSum" as="xs:decimal">
		<xsl:param name="tpl" as="xs:string"/>
		<xsl:param name="vrgs" as="xs:string*"/>
		<xsl:param name="rows" as="xs:string*"/>
		<xsl:sequence select="sum(for $v in $vrgs, $r in $rows return pk:factNum($tpl, $v, $r))"/>
	</xsl:function>

	<xsl:function name="pk:art" as="xs:string">
		<xsl:param name="vrg" as="xs:string"/>
		<xsl:sequence select="pk:factValue('PK.000.01', $vrg, 'R000-120')"/>
	</xsl:function>

	<!-- balance-sheet values (C1100PK) of positions and look-through rows booked on the given PNRs -->
	<xsl:function name="pk:sumPos" as="xs:decimal">
		<xsl:param name="vrg" as="xs:string"/>
		<xsl:param name="pnrs" as="xs:string*"/>
		<xsl:sequence select="pk:sum(key('posPnr', $pnrs ! ($vrg || '|' || .), $doc)/Wert_C1100PK)"/>
	</xsl:function>
	<xsl:function name="pk:sumLt" as="xs:decimal">
		<xsl:param name="vrg" as="xs:string"/>
		<xsl:param name="pnrs" as="xs:string*"/>
		<xsl:sequence select="pk:sum(key('ltPnr', $pnrs ! ($vrg || '|' || .), $doc)/Wert_C1100PK)"/>
	</xsl:function>

	<xsl:function name="pk:pct" as="xs:decimal?">
		<xsl:param name="part" as="xs:decimal"/>
		<xsl:param name="total" as="xs:decimal"/>
		<xsl:sequence select="if ($total ne 0) then $part div $total * 100 else ()"/>
	</xsl:function>

	<xsl:function name="pk:sev" as="xs:string">
		<xsl:param name="id" as="xs:string"/>
		<xsl:sequence select="($sevMap($id), 'W')[1]"/>
	</xsl:function>

	<!-- ISIN check digit (Luhn over the letter-expanded first 11 characters) -->
	<xsl:function name="pk:isinValid" as="xs:boolean">
		<xsl:param name="code" as="xs:string"/>
		<xsl:variable name="isin" select="substring($code, 1, 12)"/>
		<xsl:choose>
			<xsl:when test="not(matches($isin, '^[A-Z]{2}[A-Z0-9]{9}[0-9]$'))">
				<xsl:sequence select="false()"/>
			</xsl:when>
			<xsl:otherwise>
				<xsl:variable name="digits" select="string-join(for $c in string-to-codepoints(substring($isin, 1, 11)) return if ($c ge 65) then string($c - 55) else codepoints-to-string($c), '')"/>
				<xsl:variable name="rev" select="reverse(for $c in string-to-codepoints($digits) return $c - 48)"/>
				<xsl:variable name="sum" select="sum(for $i in 1 to count($rev) return (if ($i mod 2 eq 1) then (if ($rev[$i] * 2 gt 9) then $rev[$i] * 2 - 9 else $rev[$i] * 2) else $rev[$i]))"/>
				<xsl:sequence select="(10 - $sum mod 10) mod 10 eq xs:integer(substring($isin, 12, 1))"/>
			</xsl:otherwise>
		</xsl:choose>
	</xsl:function>

	<!-- LEI check digits (ISO 17442 / ISO 7064 MOD 97-10) -->
	<xsl:function name="pk:leiValid" as="xs:boolean">
		<xsl:param name="lei" as="xs:string"/>
		<xsl:sequence select="matches($lei, '^[A-Z0-9]{18}[0-9]{2}$') and
			xs:integer(string-join(for $c in string-to-codepoints($lei) return if ($c ge 65) then string($c - 55) else codepoints-to-string($c), '')) mod 97 eq 1"/>
	</xsl:function>

	<!-- ===================================================================== -->
	<!-- Result constructors                                                   -->
	<!-- ===================================================================== -->
	<!-- a single check: status NA when not applicable, OK, or the rule severity -->
	<xsl:function name="pk:c" as="element(c)">
		<xsl:param name="id" as="xs:string"/>
		<xsl:param name="applies" as="xs:boolean"/>
		<xsl:param name="ok" as="xs:boolean?"/>
		<xsl:param name="detail" as="xs:string?"/>
		<c id="{$id}" s="{if (not($applies)) then 'NA' else if ($ok) then 'OK' else pk:sev($id)}">
			<xsl:if test="$applies and not($ok) and $detail">
				<xsl:attribute name="d" select="$detail"/>
			</xsl:if>
		</c>
	</xsl:function>

	<!-- check with an object key (PNR, label) and an actual value -->
	<xsl:function name="pk:ck" as="element(c)">
		<xsl:param name="id" as="xs:string"/>
		<xsl:param name="key" as="xs:string"/>
		<xsl:param name="applies" as="xs:boolean"/>
		<xsl:param name="ok" as="xs:boolean?"/>
		<xsl:param name="detail" as="xs:string?"/>
		<xsl:param name="ist" as="xs:decimal?"/>
		<c id="{$id}" key="{$key}" s="{if (not($applies)) then 'NA' else if ($ok) then 'OK' else pk:sev($id)}">
			<xsl:if test="exists($ist)"><xsl:attribute name="ist" select="$ist"/></xsl:if>
			<xsl:if test="$applies and not($ok) and $detail"><xsl:attribute name="d" select="$detail"/></xsl:if>
		</c>
	</xsl:function>

	<!-- field must be empty -->
	<xsl:function name="pk:nul" as="element(c)">
		<xsl:param name="id" as="xs:string"/>
		<xsl:param name="applies" as="xs:boolean"/>
		<xsl:param name="r" as="node()*"/>
		<xsl:param name="f" as="xs:string"/>
		<xsl:variable name="v" select="pk:f($r, $f)"/>
		<xsl:sequence select="pk:c($id, $applies, not(pk:real($v)), $f || ' = „' || $v || '“ gemeldet, soll leer sein')"/>
	</xsl:function>

	<!-- field must be reported -->
	<xsl:function name="pk:req" as="element(c)">
		<xsl:param name="id" as="xs:string"/>
		<xsl:param name="applies" as="xs:boolean"/>
		<xsl:param name="r" as="node()*"/>
		<xsl:param name="f" as="xs:string"/>
		<xsl:variable name="v" select="pk:f($r, $f)"/>
		<xsl:sequence select="pk:c($id, $applies, pk:has($v), $f || ' fehlt')"/>
	</xsl:function>

	<!-- amount comparison: actual (ist) vs expected (soll) with tolerances -->
	<xsl:function name="pk:cmp" as="element(c)">
		<xsl:param name="id" as="xs:string"/>
		<xsl:param name="key" as="xs:string"/>
		<xsl:param name="applies" as="xs:boolean"/>
		<xsl:param name="ist" as="xs:decimal"/>
		<xsl:param name="soll" as="xs:decimal"/>
		<xsl:param name="tol" as="xs:decimal"/>
		<xsl:param name="tolWarn" as="xs:decimal"/>
		<xsl:param name="unit" as="xs:string"/>
		<xsl:variable name="diff" select="$ist - $soll"/>
		<c id="{$id}" key="{$key}" ist="{$ist}" soll="{$soll}" diff="{$diff}" u="{$unit}"
		   s="{if (not($applies)) then 'NA' else if (abs($diff) gt $tol) then pk:sev($id) else if (abs($diff) gt $tolWarn) then 'W' else 'OK'}"/>
	</xsl:function>

	<!-- key figure in % with an explicit status -->
	<xsl:function name="pk:kpi" as="element(c)">
		<xsl:param name="id" as="xs:string"/>
		<xsl:param name="value" as="xs:decimal?"/>
		<xsl:param name="status" as="xs:string"/>
		<xsl:param name="detail" as="xs:string?"/>
		<c id="{$id}" s="{if (empty($value)) then 'NA' else $status}" u="%">
			<xsl:if test="exists($value)"><xsl:attribute name="ist" select="$value"/></xsl:if>
			<xsl:if test="$detail"><xsl:attribute name="d" select="$detail"/></xsl:if>
		</c>
	</xsl:function>

	<!-- ===================================================================== -->
	<!-- Phase 1: evaluate all checks                                          -->
	<!-- ===================================================================== -->
	<xsl:variable name="vrgList" as="xs:string*">
		<xsl:perform-sort select="distinct-values($root/*/@VRG ! string(.))">
			<xsl:sort select="if (. castable as xs:integer) then xs:integer(.) else 0"/>
		</xsl:perform-sort>
	</xsl:variable>

	<xsl:variable name="results">
		<results>
			<xsl:for-each select="$vrgList">
				<xsl:call-template name="vrg">
					<xsl:with-param name="v" select="."/>
				</xsl:call-template>
			</xsl:for-each>
			<xsl:call-template name="gesamt"/>
		</results>
	</xsl:variable>

	<xsl:template name="vrg">
		<xsl:param name="v" as="xs:string"/>
		<xsl:variable name="art" select="pk:art($v)"/>
		<xsl:variable name="kons" select="pk:factValue('PK.000.01', $v, 'R000-200')"/>
		<xsl:variable name="t000" select="key('tbl', 'PK.000.01|' || $v, $doc)"/>
		<xsl:variable name="t800" select="key('tbl', 'PK.800.01|' || $v, $doc)"/>
		<xsl:variable name="t900" select="key('tbl', 'PK.900.01|' || $v, $doc)"/>
		<xsl:variable name="t01" select="key('tbl', 'PK.006.02.01.01|' || $v, $doc)/Datenpunkt"/>
		<xsl:variable name="t02" select="key('tbl', 'PK.006.02.01.02|' || $v, $doc)/Datenpunkt"/>
		<xsl:variable name="tlt" select="key('tbl', 'PK.006.03|' || $v, $doc)/Datenpunkt"/>
		<xsl:variable name="has006" select="exists($t01 | $t02)"/>
		<xsl:variable name="fb800" select="pk:factNum('PK.800.01', $v, 'R800-800')"/>
		<xsl:variable name="hasFb800" select="exists(pk:fact('PK.800.01', $v, 'R800-800'))"/>
		<xsl:variable name="q240" select="pk:factNum('PK.000.01', $v, 'R000-240')"/>
		<xsl:variable name="q250" select="pk:factNum('PK.000.01', $v, 'R000-250')"/>

		<vrg nr="{$v}" art="{$art}" name="{pk:factValue('PK.000.01', $v, 'R000-110')}" kons="{$kons}"
		     parent="{pk:factValue('PK.000.01', $v, 'R000-130')}" fb800="{$fb800}"
		     n01="{count($t01)}" n02="{count($t02)}" nlt="{count($tlt)}">

			<!-- ======================= Stammdaten ======================= -->
			<grp id="stamm">
				<xsl:variable name="missing" select="$rows000Required[not(pk:has(pk:factValue('PK.000.01', $v, .)))]"/>
				<xsl:sequence select="pk:c('PK_43', true(), exists($t000) and empty($missing),
					if (empty($t000)) then 'Template PK.000.01 nicht gemeldet' else 'fehlt: ' || string-join($missing, ', '))"/>
				<xsl:sequence select="pk:c('INT_VRGNR', exists($t000), pk:factValue('PK.000.01', $v, 'R000-100') eq $v,
					'R000-100 = ' || pk:factValue('PK.000.01', $v, 'R000-100'))"/>
				<xsl:sequence select="pk:c('PK_64', $art eq '0', pk:factValue('PK.000.01', $v, 'R000-100') eq '0', 'R000-100 = ' || pk:factValue('PK.000.01', $v, 'R000-100'))"/>
				<xsl:sequence select="pk:c('PK_65', $art eq '9', pk:factValue('PK.000.01', $v, 'R000-100') eq '999', 'R000-100 = ' || pk:factValue('PK.000.01', $v, 'R000-100'))"/>
				<xsl:sequence select="pk:c('PK_14', $art = ('0', '9'), $kons eq 'N', 'R000-200 = ' || $kons)"/>
				<xsl:sequence select="pk:c('PK_12', $kons eq 'J', $q240 lt 1, 'R000-240 = ' || $q240)"/>
				<xsl:sequence select="pk:c('PK_13', $kons eq 'J', $q250 le 1, 'R000-250 = ' || $q250)"/>
				<xsl:variable name="konsRows" select="('R000-210','R000-220','R000-230','R000-240','R000-250')[pk:real(pk:factValue('PK.000.01', $v, .))]"/>
				<xsl:sequence select="pk:c('PK_60', $kons eq 'N', empty($konsRows), 'gemeldet: ' || string-join($konsRows, ', '))"/>
				<xsl:sequence select="pk:c('PK_61', $art = ('0','1','2','4','9'), not(pk:real(pk:factValue('PK.000.01', $v, 'R000-130'))),
					'R000-130 = ' || pk:factValue('PK.000.01', $v, 'R000-130'))"/>
				<xsl:variable name="parent" select="pk:factValue('PK.000.01', $v, 'R000-130')"/>
				<xsl:sequence select="pk:c('INT_PARENT', $art eq '3', $parent = $vrgList and pk:art($parent) eq '2',
					if ($parent eq '') then 'R000-130 fehlt' else 'R000-130 = ' || $parent || ' (Art ' || pk:art($parent) || ')')"/>
				<xsl:if test="$art eq '0'">
					<xsl:variable name="pz" select="pk:factValue('PK.000.01', $v, 'R000-140')"/>
					<xsl:variable name="db" select="pk:d($root/@DB)"/>
					<xsl:sequence select="pk:c('PK_66', $pz eq 'MIX', $db gt 0 and $db lt 1, 'DB = ' || $root/@DB)"/>
					<xsl:sequence select="pk:c('PK_67', $pz eq 'LO', $db eq 1, 'DB = ' || $root/@DB)"/>
					<xsl:sequence select="pk:c('PK_68', $pz eq 'BO', $db eq 0, 'DB = ' || $root/@DB)"/>
				</xsl:if>
				<!-- which templates have to be (or must not be) reported -->
				<xsl:sequence select="pk:c('PK_3_1', $art = ('1','3','4','9') and $fb800 gt 0 and ($kons eq 'N' or $q250 gt 0), $has006, 'PK.006.02 fehlt bei PNR 800 = ' || format-number($fb800, '#.##0,00', 'de'))"/>
				<xsl:sequence select="pk:c('PK_3_2', $art = ('0','1','2','3','4'), exists($t800), 'PK.800.01 fehlt')"/>
				<xsl:sequence select="pk:c('PK_3_3', $art = ('0','1','2','3','4'), exists($t900), 'PK.900.01 fehlt')"/>
				<xsl:sequence select="pk:c('PK_4', $kons eq 'J' and $q250 eq 0, not($has006), 'PK.006.02 gemeldet')"/>
				<xsl:sequence select="pk:c('PK_5', $art eq '2', not($has006), 'PK.006.02 gemeldet')"/>
				<xsl:sequence select="pk:c('PK_6', $hasFb800 and $fb800 eq 0, not($has006), 'PK.006.02 gemeldet, obwohl PNR 800 = 0')"/>
				<xsl:sequence select="pk:c('PK_75', $q240 gt 0 and $art eq '9', not($has006), 'PK.006.02 gemeldet')"/>
				<!-- VA00032: PNR reported twice -->
				<xsl:variable name="codes" select="($t000, $t800, $t900)/*/RowCode ! normalize-space(.)"/>
				<xsl:variable name="dups" select="distinct-values($codes[count(index-of($codes, .)) gt 1])"/>
				<xsl:sequence select="pk:c('PK_VA12', exists($codes), empty($dups), 'mehrfach: ' || string-join($dups, ', '))"/>
			</grp>

			<!-- ======================= Formblatt 800 ======================= -->
			<xsl:if test="exists($t800)">
				<grp id="fb800">
					<xsl:sequence select="pk:c('PK_56', true(), $hasFb800, 'R800-800 fehlt')"/>
					<xsl:variable name="miss57" select="('R800-810','R800-820')[empty(pk:fact('PK.800.01', $v, .))]"/>
					<xsl:sequence select="pk:c('PK_57', true(), empty($miss57), 'fehlt: ' || string-join($miss57, ', '))"/>
					<xsl:for-each select="$hundreds">
						<xsl:variable name="h" select="."/>
						<xsl:variable name="rows" select="('R800-' || $h, $sub800($h))"/>
						<xsl:sequence select="pk:cmp($sumRule800($h), 'R800-' || $h, exists($rows ! pk:fact('PK.800.01', $v, .)),
							pk:factNum('PK.800.01', $v, 'R800-' || $h), pk:factSum('PK.800.01', $v, $sub800($h)), $tolerance, $toleranceWarning, '€')"/>
					</xsl:for-each>
					<xsl:sequence select="pk:cmp('PK_184', 'R800-800', $fb800 gt 0, $fb800, sum($hundreds ! pk:factNum('PK.800.01', $v, 'R800-' || .)), $tolerance, $toleranceWarning, '€')"/>
					<!-- VA00023 sign convention -->
					<xsl:for-each select="$t800/Datenpunkt">
						<xsl:sort select="RowCode"/>
						<xsl:variable name="rc" select="normalize-space(RowCode)"/>
						<xsl:variable name="w" select="pk:d(Wert)"/>
						<xsl:choose>
							<xsl:when test="$rc = $sign40"><xsl:sequence select="pk:ck('PK_40', $rc, true(), $w ge 0, 'Wert muss positiv sein', $w)"/></xsl:when>
							<xsl:when test="$rc = $sign41"><xsl:sequence select="pk:ck('PK_41', $rc, true(), $w le 0, 'Wert muss negativ sein', $w)"/></xsl:when>
							<xsl:when test="$rc = ('R800-830','R800-835')"><xsl:sequence select="pk:ck('PK_42_1', $rc, true(), $w ge 0, 'Wert muss positiv sein', $w)"/></xsl:when>
							<xsl:when test="$rc = ('R800-800','R800-820')"><xsl:sequence select="pk:ck('PK_42_2', $rc, true(), $w ge 0, 'Wert darf nicht negativ sein', $w)"/></xsl:when>
							<xsl:when test="$rc eq 'R800-810'"><xsl:sequence select="pk:ck('PK_42_3', $rc, true(), $w ge 0, 'Wert darf nicht negativ sein', $w)"/></xsl:when>
						</xsl:choose>
					</xsl:for-each>
				</grp>
			</xsl:if>

			<!-- ======================= Abstimmung 800 <-> 006 ======================= -->
			<xsl:if test="exists($t800) and $art = ('1','3','4') and $kons eq 'N'">
				<grp id="abgl">
					<xsl:for-each select="$hundreds">
						<xsl:variable name="h" select="."/>
						<xsl:sequence select="pk:cmp($ltRule800($h), 'R800-' || $h, true(), pk:factNum('PK.800.01', $v, 'R800-' || $h),
							pk:sumPos($v, $sub800($h)) + pk:sumLt($v, $sub800($h)), $tolerance, $toleranceWarning, '€')"/>
					</xsl:for-each>
					<xsl:for-each select="$pnr800">
						<xsl:variable name="p" select="."/>
						<xsl:variable name="soll" select="pk:sumPos($v, $p) + pk:sumLt($v, $p)"/>
						<xsl:if test="exists(pk:fact('PK.800.01', $v, $p)) or $soll ne 0">
							<xsl:sequence select="pk:cmp('PK_207', $p, true(), pk:factNum('PK.800.01', $v, $p), $soll, $tolerance, $toleranceWarning, '€')"/>
						</xsl:if>
					</xsl:for-each>
					<xsl:sequence select="pk:cmp('PK_208', 'R800-800', $fb800 gt 0, $fb800, pk:sumPos($v, $pnr800All) + pk:sumLt($v, $pnr800All), $tolerance, $toleranceWarning, '€')"/>
					<xsl:sequence select="pk:cmp('PK_215', 'R800-800', $fb800 gt 0, $fb800, pk:sum($t01/Wert_C1100PK), $tolerance, $toleranceWarning, '€')"/>
				</grp>
			</xsl:if>

			<!-- ======================= List of Assets ======================= -->
			<xsl:if test="$has006">
				<grp id="assets">
					<xsl:for-each select="$t01">
						<xsl:sort select="Wert_C0010PK"/>
						<xsl:call-template name="asset">
							<xsl:with-param name="v" select="$v"/>
							<xsl:with-param name="art" select="$art"/>
							<xsl:with-param name="fb800" select="$fb800"/>
						</xsl:call-template>
					</xsl:for-each>
					<!-- asset information without a position -->
					<xsl:for-each select="$t02[empty(key('pos', $v || '|' || Wert_C0010PK, $doc))]">
						<row key="{Wert_C0010PK}" toc="{Wert_C0020PK}" title="{Wert_C0130}" cic="{Wert_C0230}" orphan="true">
							<xsl:sequence select="pk:c('INT_A02', true(), false(), 'nur in PK.006.02.01.02 gemeldet')"/>
						</row>
					</xsl:for-each>
				</grp>
			</xsl:if>

			<!-- ======================= Look-through ======================= -->
			<xsl:if test="exists($tlt)">
				<grp id="lt">
					<xsl:for-each select="$tlt">
						<xsl:variable name="cat" select="normalize-space(Wert_C0030PK)"/>
						<xsl:variable name="cqsE" select="normalize-space(Wert_C1150PK)"/>
						<xsl:variable name="cqsI" select="normalize-space(Wert_C1160PK)"/>
						<xsl:variable name="fails" as="xs:string*" select="(
							if (Wert_C0020PK eq 'CAU/ISIN' and not(matches(Wert_C0010PK, '^[A-Z]{2}[0-9A-Z]{8}[0-9]{2}\+[A-Z]{3}$'))) then 'PK_9' else (),
							if ($cat = ('8','9') and pk:real(Wert_C0040PK)) then 'PBV70' else (),
							if ($cat eq '3L' and normalize-space(Wert_C1090PK) ne 'J') then 'PK_210' else (),
							if ($cat eq '3X' and normalize-space(Wert_C1090PK) ne 'N') then 'PK_211' else (),
							if (normalize-space(Wert_C1120PK) = $pnr074 and pk:d(Wert_C1100PK) lt 0 and not($cat = ('L','A','B','C','D','E','F'))) then 'PK_216' else (),
							if ($cat eq '9' and pk:real(Wert_C1050PK)) then 'PK_257' else (),
							if ($cat = ('0','9') and pk:real(Wert_C1070PK)) then 'PK_258' else (),
							if ($cat = $bondCats and not(pk:has($cqsE))) then 'PK_260_1' else (),
							if (not($cat = $bondCats) and $cqsE eq '9') then 'PK_260_2' else (),
							if ($cat = $bondCats and not(pk:has($cqsI))) then 'PK_261_1' else (),
							if (not($cat = $bondCats) and $cqsI eq '9') then 'PK_261_2' else ())"/>
						<xsl:if test="exists($fails)">
							<xsl:variable name="dp" select="."/>
							<row key="{Wert_C1010PK}" fund="{Wert_C0010PK}" title="{Wert_C1030PK}" cat="{$cat}" pnr="{Wert_C1120PK}" bw="{pk:d(Wert_C1100PK)}">
								<xsl:for-each select="$fails">
									<c id="{.}" s="{pk:sev(.)}" d="{
										if (. eq 'PK_9') then 'C0010PK = ' || $dp/Wert_C0010PK
										else if (. eq 'PBV70') then 'C0040PK = ' || $dp/Wert_C0040PK || ' bei Kategorie ' || $cat
										else if (. = ('PK_210','PK_211')) then 'C1090PK = „' || $dp/Wert_C1090PK || '“ bei Kategorie ' || $cat
										else if (. eq 'PK_216') then 'negativer Bilanzwert bei Kategorie ' || $cat
										else if (. eq 'PK_257') then 'C1050PK = ' || $dp/Wert_C1050PK
										else if (. eq 'PK_258') then 'C1070PK = ' || $dp/Wert_C1070PK
										else if (. = ('PK_260_1','PK_261_1')) then 'Bonitätsstufe fehlt bei Kategorie ' || $cat
										else 'Bonitätsstufe 9 bei Kategorie ' || $cat}"/>
								</xsl:for-each>
							</row>
						</xsl:if>
					</xsl:for-each>
					<!-- VDL078: one asset category per underlying (derivatives) -->
					<xsl:for-each-group select="$tlt[normalize-space(Wert_C1120PK) = $pnr078]" group-by="normalize-space(Wert_C1010PK)">
						<xsl:variable name="cats" select="distinct-values(current-group()/Wert_C0030PK ! normalize-space(.))"/>
						<xsl:if test="count($cats) gt 1">
							<row key="{current-grouping-key()}" fund="{string-join(distinct-values(current-group()/Wert_C0010PK), ', ')}" title="{current-group()[1]/Wert_C1030PK}" pnr="{string-join(distinct-values(current-group()/Wert_C1120PK), ', ')}">
								<c id="PK_219" s="{pk:sev('PK_219')}" d="Kategorien: {string-join($cats, ', ')}"/>
							</row>
						</xsl:if>
					</xsl:for-each-group>
					<!-- PBV68 / PBV69: looked-through fund must be an asset with CIC ##4# -->
					<xsl:for-each-group select="$tlt" group-by="normalize-space(Wert_C0010PK)">
						<xsl:variable name="a" select="key('info', $v || '|' || current-grouping-key(), $doc)[1]"/>
						<xsl:variable name="cic" select="normalize-space($a/Wert_C0230)"/>
						<fund key="{current-grouping-key()}" title="{($a/Wert_C0130, current-group()[1]/Wert_C0025PK)[1]}" cic="{$cic}"
						      n="{count(current-group())}" bw="{pk:sum(current-group()/Wert_C1100PK)}" mw="{pk:sum(current-group()/Wert_C1110PK)}"
						      nd="{pk:sum(current-group()[normalize-space(Wert_C0030PK) eq '4']/Wert_C1110PK)}"/>
						<xsl:if test="$cic eq '' or not(pk:cic($cic, '4#'))">
							<row key="{current-grouping-key()}" fund="{current-grouping-key()}" title="{current-group()[1]/Wert_C0025PK}">
								<xsl:sequence select="if ($cic eq '') then pk:c('PBV68', true(), false(), 'Fonds nicht in PK.006.02 (bzw. ohne CIC)')
									else pk:c('PBV69', true(), false(), 'CIC = ' || $cic || ', erwartet ##4#')"/>
							</row>
						</xsl:if>
					</xsl:for-each-group>
				</grp>
			</xsl:if>

			<!-- ======================= Kennzahlen (zusätzlich) ======================= -->
			<xsl:if test="exists($t800) and $art = ('1','2','3','4')">
				<grp id="alloc">
					<xsl:variable name="p" select="function($rows as xs:string*, $base as xs:string) as xs:decimal? {
						pk:pct(pk:factSum('PK.800.01', $v, $rows), pk:factNum('PK.800.01', $v, $base)) }"/>
					<xsl:variable name="hy" select="$p(('R800-315','R800-325','R800-335'), 'R800-300')"/>
					<xsl:sequence select="pk:kpi('INT_AA1', $hy, if ($hy gt 25) then 'E' else 'OK', ())"/>
					<xsl:sequence select="pk:kpi('INT_AA2', $p('R800-830', 'R800-800'), 'I', ())"/>
					<xsl:variable name="fx" select="$p('R800-835', 'R800-800')"/>
					<xsl:sequence select="pk:kpi('INT_AA3', $fx, if ($fx gt 30 or $fx lt 0) then 'E' else 'OK', ())"/>
					<xsl:variable name="eq" select="$p(('R800-410','R800-420','R800-430','R800-480'), 'R800-800')"/>
					<xsl:sequence select="pk:kpi('INT_AA4', $eq, if ($eq gt 50) then 'E' else 'OK', ())"/>
					<xsl:variable name="lev" select="pk:pct(pk:factSum('PK.800.01', $v, ('R800-382','R800-384','R800-480','R800-580','R800-680')) - pk:factNum('PK.800.01', $v, 'R800-100'), $fb800)"/>
					<xsl:sequence select="pk:kpi('INT_AA5', $lev, if ($lev gt 0) then 'E' else 'OK', ())"/>
					<xsl:for-each select="(['INT_AA6','R800-630',5,10], ['INT_AA7','R800-840',5,10], ['INT_AA8','R800-861',2.5,5], ['INT_AA9','R800-862',5,10], ['INT_AA10','R800-863',5,10])">
						<xsl:variable name="k" select="$p(?2, 'R800-800')"/>
						<xsl:sequence select="pk:kpi(?1, $k, if ($k ge ?4) then 'E' else if ($k gt ?3) then 'W' else 'OK', ())"/>
					</xsl:for-each>
					<xsl:if test="exists($tlt)">
						<xsl:variable name="nd" select="pk:sum($tlt[normalize-space(Wert_C0030PK) eq '4']/Wert_C1110PK)"/>
						<xsl:variable name="ndp" select="pk:pct($nd, $fb800)"/>
						<xsl:sequence select="pk:kpi('INT_LT10', $ndp, if ($ndp gt $lookThroughLimitTotal) then 'W' else 'OK',
							'nicht durchgerechnet ' || format-number($nd, '#.##0,00', 'de') || ' € · PNR 820 = ' || format-number(pk:factNum('PK.800.01', $v, 'R800-820'), '#.##0,00', 'de')
							|| ' · PNR 810 = ' || format-number(pk:factNum('PK.800.01', $v, 'R800-810'), '#.##0,00', 'de'))"/>
					</xsl:if>
				</grp>
			</xsl:if>

			<!-- ======================= Formblatt 900 ======================= -->
			<xsl:if test="exists($t900)">
				<grp id="fb900">
					<xsl:variable name="miss58" select="('R900-100','R900-110','R900-120')[empty(pk:fact('PK.900.01', $v, .))]"/>
					<xsl:sequence select="pk:c('PK_58', true(), empty($miss58), 'fehlt: ' || string-join($miss58, ', '))"/>
					<xsl:sequence select="pk:cmp('PK_188', 'R900-100', true(), pk:factNum('PK.900.01', $v, 'R900-100'), pk:factSum('PK.900.01', $v, ('R900-110','R900-120')), 0, 0, 'n')"/>
					<xsl:sequence select="pk:cmp('PK_189', 'R900-300', $kons eq 'J', pk:factNum('PK.900.01', $v, 'R900-300'), pk:factSum('PK.900.01', $v, ('R900-310','R900-320')), 0, 0, 'n')"/>
					<xsl:variable name="k300" select="('R900-300','R900-310','R900-320')"/>
					<xsl:variable name="rep300" select="$k300[exists(pk:fact('PK.900.01', $v, .))]"/>
					<xsl:sequence select="pk:c('PK_62', $art = ('1','2','3','4') and $kons eq 'J', count($rep300) eq 3, 'fehlt: ' || string-join($k300[not(. = $rep300)], ', '))"/>
					<xsl:sequence select="pk:c('PK_63', $art = ('1','2','3','4') and $kons eq 'N', empty($rep300), 'gemeldet: ' || string-join($rep300, ', '))"/>
					<xsl:sequence select="pk:cmp('PK_191', 'R900-310', $kons eq 'J', pk:factNum('PK.900.01', $v, 'R900-310'), pk:factNum('PK.900.01', $v, 'R900-110') * $q240, 0.999, 0.999, 'n')"/>
					<xsl:sequence select="pk:cmp('PK_191', 'R900-320', $kons eq 'J', pk:factNum('PK.900.01', $v, 'R900-320'), pk:factNum('PK.900.01', $v, 'R900-120') * $q240, 0.999, 0.999, 'n')"/>
				</grp>
			</xsl:if>
		</vrg>
	</xsl:template>

	<!-- one position of PK.006.02.01.01 together with its asset information (PK.006.02.01.02) -->
	<xsl:template name="asset">
		<xsl:param name="v" as="xs:string"/>
		<xsl:param name="art" as="xs:string"/>
		<xsl:param name="fb800" as="xs:decimal"/>
		<xsl:variable name="p" select="."/>
		<xsl:variable name="id" select="normalize-space(Wert_C0010PK)"/>
		<xsl:variable name="infos" select="key('info', $v || '|' || $id, $doc)"/>
		<xsl:variable name="a" select="$infos[1]"/>
		<xsl:variable name="r" select="($p, $a)"/>
		<xsl:variable name="toc" select="normalize-space(Wert_C0020PK)"/>
		<xsl:variable name="isin" select="$toc = ('ISIN', 'CAU/ISIN')"/>
		<xsl:variable name="cic" select="pk:f($a, 'C0230')"/>
		<xsl:variable name="pnr" select="pk:f($p, 'C1120PK')"/>
		<xsl:variable name="lts" select="key('lt', $v || '|' || $id, $doc)"/>
		<xsl:variable name="bw" select="pk:d(Wert_C1100PK)"/>
		<xsl:variable name="mw" select="pk:d(Wert_C1110PK)"/>
		<xsl:variable name="ai" select="pk:d(Wert_C0090)"/>
		<xsl:variable name="hasQty" select="pk:has(Wert_C0060)"/>
		<xsl:variable name="hasPar" select="pk:has(Wert_C0070)"/>
		<xsl:variable name="qp" select="pk:d(Wert_C0060) * pk:d($a/Wert_C0370) + $ai"/>
		<xsl:variable name="pp" select="pk:d(Wert_C0070) * pk:d($a/Wert_C0380) + $ai"/>
		<xsl:variable name="ltbw" select="pk:sum($lts/Wert_C1100PK)"/>
		<xsl:variable name="ltmw" select="pk:sum($lts/Wert_C1110PK)"/>
		<xsl:variable name="nd" select="pk:sum($lts[normalize-space(Wert_C0030PK) eq '4']/Wert_C1110PK)"/>
		<xsl:variable name="ew" select="pk:f($a, 'C1140PK')"/>
		<xsl:variable name="L18" select="'71|75|87|88|9#|09'"/>
		<xsl:variable name="L104" select="'11|12|13|14|15|17|18|71|75|87|88|9#|09'"/>
		<xsl:variable name="DER" select="'A#|B#|C#|D#|E#|F#'"/>
		<xsl:variable name="c" select="function($pattern as xs:string) as xs:boolean { pk:cic($cic, $pattern) }"/>
		<xsl:variable name="d1" select="key('der1', $v || '|' || $id, $doc)"/>
		<xsl:variable name="d2" select="key('der2', $v || '|' || $id, $doc)"/>
		<xsl:variable name="e0271" select="pk:date($a/Wert_EC0271)"/>
		<xsl:variable name="d0280" select="pk:date($a/Wert_C0280)"/>
		<xsl:variable name="e0290" select="pk:date($a/Wert_EC0290)"/>
		<xsl:variable name="ec0172" select="pk:f($a, 'EC0172')"/>
		<xsl:variable name="ec0232" select="pk:f($a, 'EC0232')"/>
		<xsl:variable name="vm" select="pk:f($p, 'C0075')"/>

		<row key="{$id}" toc="{$toc}" title="{$a/Wert_C0130}" cic="{$cic}" pnr="{$pnr}" bw="{$bw}" mw="{$mw}"
		     qty="{$hasQty}" par="{$hasPar}" qp="{$qp}" pp="{$pp}" acq="{Wert_C0080}" ltn="{count($lts)}" ltbw="{$ltbw}" ltmw="{$ltmw}" nd="{$nd}">
			<!-- identification -->
			<xsl:sequence select="pk:c('PK_7', $toc eq 'CAU/ISIN', matches($id, '^[A-Z]{2}[0-9A-Z]{8}[0-9]{2}\+[A-Z]{3}$'), 'C0010PK = ' || $id)"/>
			<xsl:sequence select="pk:c('PK_VA22', $isin, pk:isinValid($id), 'Prüfziffer ungültig: ' || $id)"/>
			<xsl:sequence select="pk:c('PK_255', true(), count($infos) eq 1, count($infos) || ' Zeilen in PK.006.02.01.02')"/>
			<xsl:sequence select="pk:c('PK_220', true(), count(key('posSig', string-join(($v, for $n in ('C0010PK','C0020PK','C0035','C0040','C0050','C0051PK','C0060','C0070','EC0141','C0075','C0080','C0090','C1100PK','C1110PK','C1120PK','C1130PK') return string($p/*[local-name() eq 'Wert_' || $n])), '|'), $doc)) eq 1, 'Position mehrfach gemeldet')"/>
			<xsl:sequence select="pk:c('PK_269', exists($a), not(matches(string($a/Wert_C0130), '^\s|\s$') or matches(string($a/Wert_C0140), '^\s|\s$')), 'führende/abschließende Leerzeichen in C0130/C0140')"/>
			<!-- PNR -->
			<xsl:variable name="allowed" select="if ($art eq '9') then $pnr100 else if ($art eq '3') then $pnr800 else if ($art eq '2') then $pnr300 else ($pnr800, $pnr300)"/>
			<xsl:sequence select="pk:c('PK_1', $art eq '9' and $pnr ne '', $pnr = $pnr100, 'C1120PK = ' || $pnr)"/>
			<xsl:sequence select="pk:c('PK_2_1', $art = ('1','2','3','4') and $pnr ne '', $pnr = $allowed, 'C1120PK = ' || $pnr)"/>
			<xsl:sequence select="pk:nul('PK_209_2', $c('4#'), $p, 'C1120PK')"/>
			<!-- CIC -->
			<xsl:sequence select="pk:c('PBV38', true(), string-length($cic) eq 4 and $c($cicValid), 'CIC = „' || $cic || '“')"/>
			<xsl:sequence select="pk:c('PBV40', $toc eq 'CAU/INST' and not($c($DER)), matches($cic, '^(XL|XT)'), 'CIC = ' || $cic)"/>
			<xsl:sequence select="pk:c('PK_214', pk:has($a/Wert_EC0041), pk:f($a, 'EC0041') ne $cic, 'EC0041 = CIC = ' || $cic)"/>
			<xsl:sequence select="pk:c('PK_212', not($c('4#')), $ew eq '1', 'C1140PK = „' || $ew || '“')"/>
			<xsl:sequence select="pk:c('PK_213', $c('4#'), $ew = ('2','3'), 'C1140PK = „' || $ew || '“')"/>
			<!-- quantity / par amount / price -->
			<xsl:sequence select="pk:c('PBV59', not($c('71|9#|09|' || $DER)), $hasQty or $hasPar, 'weder C0060 noch C0070 gemeldet')"/>
			<xsl:sequence select="pk:c('PBV60', true(), not($hasQty and $hasPar), 'C0060 und C0070 gemeldet')"/>
			<xsl:sequence select="pk:c('PBV39', true(), not(pk:has($a/Wert_C0370) and pk:has($a/Wert_C0380)), 'C0370 und C0380 gemeldet')"/>
			<xsl:sequence select="pk:c('PBV61', not($c('71|9#|09')), $hasQty eq pk:has($a/Wert_C0370), if ($hasQty) then 'C0060 ohne C0370' else 'C0370 ohne C0060')"/>
			<xsl:sequence select="pk:c('PBV63', not($c('71|9#|09')), $hasPar eq pk:has($a/Wert_C0380), if ($hasPar) then 'C0070 ohne C0380' else 'C0380 ohne C0070')"/>
			<xsl:sequence select="pk:c('PBV87', $c('72|73|74|75|79|8#'), not($hasQty) and $hasPar, 'C0070 erwartet, C0060 leer')"/>
			<xsl:sequence select="pk:nul('PBV94', $c('71|9#'), $p, 'C0060')"/>
			<xsl:sequence select="pk:nul('PBV95', $c('71|09|9#'), $p, 'C0070')"/>
			<xsl:sequence select="pk:c('PK_221', pk:has($a/Wert_C0380), pk:d($a/Wert_C0380) gt 0 and pk:d($a/Wert_C0380) lt 10, 'C0380 = ' || pk:f($a, 'C0380'))"/>
			<xsl:variable name="limQ" select="min((1000, abs($qp) * 0.01))"/>
			<xsl:sequence select="pk:c('PBV66', not($c('71|9#|09')) and $hasQty, abs($bw - $qp) lt $limQ,
				'Menge × Kurs + Stückzinsen = ' || format-number($qp, '#.##0,00', 'de') || ', Differenz ' || format-number($bw - $qp, '#.##0,00', 'de'))"/>
			<xsl:variable name="limP" select="min((1000, abs($pp) * 0.01))"/>
			<xsl:sequence select="pk:c('PBV67', not($c('71|9#|09')) and $hasPar, abs($bw - $pp) lt $limP,
				'Nominale × Kurs-% + Stückzinsen = ' || format-number($pp, '#.##0,00', 'de') || ', Differenz ' || format-number($bw - $pp, '#.##0,00', 'de'))"/>
			<xsl:sequence select="pk:c('PBV65', $c('7#'), $bw ge 0, 'C1100PK = ' || format-number($bw, '#.##0,00', 'de'))"/>
			<xsl:sequence select="pk:c('PEV114', pk:has($a/Wert_EC0272), not(pk:has(Wert_C0090)), 'C0090 = ' || Wert_C0090 || ' trotz Ausgabepreis')"/>
			<!-- custody, acquisition value, valuation -->
			<xsl:sequence select="pk:req('PBV41', not($c('71|75|8#|95|09')), $p, 'C0040')"/>
			<xsl:sequence select="pk:nul('PBV42', $c('71|75|8#|95'), $p, 'C0040')"/>
			<xsl:sequence select="pk:nul('PBV88', $c('71|75|8#|9#|09'), $p, 'C0050')"/>
			<xsl:sequence select="pk:req('PBV96', not($c('71|75|8#|9#|09')), $p, 'C0050')"/>
			<xsl:sequence select="pk:nul('PBV43', $c('7#|8#|09|' || $DER), $p, 'C0080')"/>
			<xsl:sequence select="pk:req('PBV44', not($c('7#|8#|09|' || $DER)), $p, 'C0080')"/>
			<xsl:sequence select="pk:c('PK_218', pk:f($p, 'C1130PK') eq 'J', ends-with($vm, 'x5004'), 'C0075 = ' || $vm)"/>
			<xsl:sequence select="pk:c('PK_217', $art eq '9' and not($c('7#')), ends-with($vm, 'x5004'), 'C0075 = ' || $vm)"/>
			<!-- issuer -->
			<xsl:sequence select="pk:nul('PBV47', $c('71|75|9#'), $a, 'C0140')"/>
			<xsl:sequence select="pk:req('PBV48', not($c('71|75|9#|09')), $a, 'C0140')"/>
			<xsl:sequence select="pk:nul('PBV89', $c('71|75|87|88|9#'), $a, 'C0150PK')"/>
			<xsl:sequence select="pk:req('PBV97', not($c($L18)), $a, 'C0150PK')"/>
			<xsl:sequence select="pk:c('PBV49', $c($L18), not(pk:real($a/Wert_C0170) or pk:real($a/Wert_C0175)), 'C0170/C0175 gemeldet')"/>
			<xsl:sequence select="pk:c('PBV50', not($c($L18)), pk:has($a/Wert_C0170) or pk:has($a/Wert_C0175), 'C0170 und C0175 fehlen')"/>
			<xsl:sequence select="pk:c('PK_270', not($c($L18)), not(pk:real($a/Wert_C0170) and pk:real($a/Wert_C0175)), 'C0170 und C0175 gemeldet')"/>
			<xsl:sequence select="pk:nul('PBV90', $c($L104), $a, 'C0180')"/>
			<xsl:sequence select="pk:req('PBV98', not($c($L104)), $a, 'C0180')"/>
			<xsl:sequence select="pk:nul('PBV91', $c($L104), $a, 'C0190PK')"/>
			<xsl:sequence select="pk:req('PBV99', not($c($L104)), $a, 'C0190PK')"/>
			<xsl:sequence select="pk:nul('PBV51', $c($L18), $a, 'C0210')"/>
			<xsl:sequence select="pk:req('PBV52', not($c($L18)), $a, 'C0210')"/>
			<xsl:sequence select="pk:nul('PBV53', $c('75|95|87|88|09'), $a, 'C0220')"/>
			<xsl:sequence select="pk:req('PBV54', not($c('75|95|87|88|09')), $a, 'C0220')"/>
			<xsl:sequence select="pk:nul('PK_235', $isin, $a, 'EC0212')"/>
			<xsl:sequence select="pk:req('PK_236', not($isin) and $c('(1|2|5|6|8)#'), $a, 'EC0212')"/>
			<xsl:sequence select="pk:nul('PK_237', $isin, $a, 'EC0310')"/>
			<xsl:sequence select="pk:req('PK_238', not($isin) and $c('(1|2|5|6|8)#'), $a, 'EC0310')"/>
			<!-- rating, duration, maturity -->
			<xsl:sequence select="pk:req('PBV85', $c('(1|2|5|6)#'), $a, 'C0250')"/>
			<xsl:sequence select="pk:req('PBV86', $c('(1|2|5|6)#'), $a, 'C0260')"/>
			<xsl:sequence select="pk:c('PK_233', $c('(1|2|5|6)#'), pk:has($a/Wert_C1150PK) and pk:f($a, 'C1150PK') ne '9', 'C1150PK = „' || pk:f($a, 'C1150PK') || '“')"/>
			<xsl:sequence select="pk:c('PK_234', $c('(1|2|5|6)#'), pk:has($a/Wert_C1160PK) and pk:f($a, 'C1160PK') ne '9', 'C1160PK = „' || pk:f($a, 'C1160PK') || '“')"/>
			<xsl:sequence select="pk:nul('PBV55', $c('(3|7|8|9)#'), $a, 'C0270')"/>
			<xsl:sequence select="pk:req('PBV56', $c('(1|2|5|6)#|42'), $a, 'C0270')"/>
			<xsl:sequence select="pk:nul('PBV57', $c('3#|4#|71|72|73|75|9#'), $a, 'C0280')"/>
			<xsl:sequence select="pk:req('PBV58', $c('1#|2#|5#|6#|74|79|8#'), $a, 'C0280')"/>
			<xsl:sequence select="pk:c('PBV10', exists($d0280) and exists($refDate), $d0280 ge $refDate, 'C0280 = ' || $d0280 || ' vor Stichtag ' || $refDate)"/>
			<!-- ECB items -->
			<xsl:sequence select="pk:c('PEV58', $c('8#') or ($c('(1|2|3|5|6)#') and $ec0232 = ('A2N','A30')), pk:has($p/Wert_EC0141), 'EC0141 fehlt')"/>
			<xsl:sequence select="pk:nul('PEV59', $c($DER || '|4#|9#|09') or ($isin and $c('(1|2|3|5|6)#')), $a, 'EC0172')"/>
			<xsl:sequence select="pk:c('PEV60', $c('8#') or (not($isin) and $c('(1|2|3|5|6)#')), $ec0172 = $sectorsEsa, 'EC0172 = „' || $ec0172 || '“')"/>
			<xsl:sequence select="pk:c('PEV61', $c('72|73|74'), $ec0172 = $sectorsEsa, 'EC0172 = „' || $ec0172 || '“')"/>
			<xsl:sequence select="pk:req('PEV62', $c('(7|8)#') or (not($isin) and $c('(1|2|3|5|6)#')), $a, 'EC0172')"/>
			<xsl:sequence select="pk:nul('PEV63', not($c('4#')) and $isin, $a, 'EC0211')"/>
			<xsl:sequence select="pk:req('PEV64', $c('4#') and not($isin), $a, 'EC0211')"/>
			<xsl:sequence select="pk:req('PEV65', not($isin), $a, 'EC0232')"/>
			<xsl:sequence select="pk:c('PEV67', $c('(1|2|3|5|6)#') and not($isin), $ec0232 = ('A2N','A30','A52','A51','A53'), 'EC0232 = „' || $ec0232 || '“')"/>
			<xsl:sequence select="pk:req('PEV70', $c('8#') or (not($isin) and $c('(1|2|5|6)#')), $a, 'EC0271')"/>
			<xsl:sequence select="pk:nul('PEV71', $c('3#|4#|7#|9#|09') or ($isin and $c('(1|2|5|6)#')), $a, 'EC0271')"/>
			<xsl:sequence select="pk:c('PEV31', exists($e0271) and exists($refDate), $e0271 le $refDate, 'EC0271 = ' || $e0271 || ' nach Stichtag')"/>
			<xsl:sequence select="pk:c('PEV68', exists($e0271) and exists($d0280), $e0271 le $d0280, 'EC0271 ' || $e0271 || ' nach C0280 ' || $d0280)"/>
			<xsl:sequence select="pk:c('PEV69', exists($e0271) and exists($e0290), $e0271 le $e0290, 'EC0271 ' || $e0271 || ' nach EC0290 ' || $e0290)"/>
			<xsl:sequence select="pk:c('PEV72', true(), pk:has($a/Wert_EC0290) eq pk:has($a/Wert_EC0300), 'nur eines von EC0290/EC0300 gemeldet')"/>
			<xsl:sequence select="pk:c('PEV74', $toc eq 'CAU/INST' and (pk:has($a/Wert_EC0290) or pk:has($a/Wert_EC0300)), $c('3#|4#'), 'CIC = ' || $cic)"/>
			<!-- property, RGLA -->
			<xsl:variable name="prop" select="('C2010PK','C2020PK','C2025PK','C2030PK','C2035PK','C2040PK')"/>
			<xsl:sequence select="pk:c('PK_239', not($c('9#')), not(some $f in $prop satisfies pk:real(pk:f($a, $f))), 'Grundstücksangaben (C2010PK–C2040PK) gemeldet')"/>
			<xsl:sequence select="pk:c('PK_240', $c('9#'), every $f in $prop satisfies pk:has(pk:f($a, $f)), 'fehlt: ' || string-join($prop[not(pk:has(pk:f($a, .)))], ', '))"/>
			<xsl:variable name="eq" select="('C2060PK','C2070PK','C2080PK','C2090PK')"/>
			<xsl:sequence select="pk:c('PK_241', not($c('3#')), not(some $f in $eq satisfies pk:real(pk:f($a, $f))), 'Angaben C2060PK–C2090PK gemeldet')"/>
			<xsl:sequence select="pk:c('PK_242', $c('3#'), every $f in $eq satisfies pk:has(pk:f($a, $f)), 'fehlt: ' || string-join($eq[not(pk:has(pk:f($a, .)))], ', '))"/>
			<xsl:sequence select="pk:nul('PK_243', not($c('13|14')), $a, 'C3010PK')"/>
			<xsl:sequence select="pk:req('PK_244', $c('13|14'), $a, 'C3010PK')"/>
			<xsl:sequence select="pk:c('PK_245', not($c('9#')), not(pk:real($a/Wert_C3020PK) or pk:real($a/Wert_C3030PK)), 'C3020PK/C3030PK gemeldet')"/>
			<xsl:sequence select="pk:c('PK_246', $c('9#'), pk:has($a/Wert_C3020PK) and pk:has($a/Wert_C3030PK), 'C3020PK/C3030PK fehlt')"/>
			<!-- derivatives -->
			<xsl:sequence select="pk:c('PK_249', $c($DER), exists($d1), 'nicht in PK.008.01.01.01')"/>
			<xsl:sequence select="pk:c('PK_251', $c($DER), exists($d2), 'nicht in PK.008.01.01.02')"/>
			<xsl:sequence select="pk:c('PK_253', $c($DER), count($d1) le 1, count($d1) || ' Zeilen in PK.008.01.01.01')"/>
			<xsl:sequence select="pk:c('PK_254', $c($DER), count($d2) le 1, count($d2) || ' Zeilen in PK.008.01.01.02')"/>
			<!-- look-through -->
			<xsl:sequence select="pk:c('PK_247', $c('4#'), exists($lts), 'keine Durchrechnung in PK.006.03')"/>
			<xsl:sequence select="pk:cmp('PK_209_1', $id, $ew = ('2','3'), $bw, $ltbw, $tolerance, $toleranceWarning, '€')"/>
			<xsl:sequence select="pk:cmp('INT_LTMW', $id, exists($lts), $mw, $ltmw, $tolerance, $toleranceWarning, '€')"/>
			<xsl:variable name="ndp" select="pk:pct($nd, $fb800)"/>
			<xsl:sequence select="pk:kpi('INT_LT2', if (exists($lts)) then $ndp else (), if ($ndp gt $lookThroughLimitAsset) then 'W' else 'OK',
				'nicht durchgerechnet ' || format-number($nd, '#.##0,00', 'de') || ' €')"/>
		</row>
	</xsl:template>

	<!-- checks across VRG and of the report header -->
	<xsl:template name="gesamt">
		<xsl:variable name="byArt" select="function($arts as xs:string*) as xs:string* { $vrgList[pk:art(.) = $arts] }"/>
		<xsl:variable name="file" select="tokenize(string(base-uri($root)), '[/\\]')[last()]"/>
		<xsl:variable name="fileTok" select="tokenize($file, '_')"/>
		<xsl:variable name="fileDate" select="$fileTok[matches(., '^\d{8}$')][1]"/>
		<xsl:variable name="lei" select="replace(normalize-space($root/@LEI), '^LEI/', '')"/>
		<vrg nr="gesamt" name="VRG-übergreifende Prüfungen und Meldungskopf" art="">
			<grp id="gesamt">
				<xsl:sequence select="pk:c('PK_VA01', exists($fileDate) and exists($refDate), string($fileDate) eq string(format-date($refDate, '[Y0001][M01][D01]')),
					'Dateiname ' || $fileDate || ', Meldung ' || $refDate)"/>
				<xsl:sequence select="pk:c('PK_VA28', matches($file, '^\d+_\d+_'), string($fileTok[2]) eq string($root/@PK-Nummer),
					'Dateiname ' || $fileTok[2] || ', Attribut ' || $root/@PK-Nummer)"/>
				<xsl:sequence select="pk:c('PK_VA02', true(), string($root/@PK-Nummer) ne '1', 'PK-Nummer 1')"/>
				<xsl:sequence select="pk:c('PK_VA23', true(), $lei ne '', 'LEI fehlt')"/>
				<xsl:sequence select="pk:c('PK_VA04', $lei ne '', pk:leiValid($lei), 'LEI ' || $lei)"/>
				<xsl:variable name="nrs" select="$root/*[starts-with(local-name(), 'PK.000.01_')]/@VRG ! string(.)"/>
				<xsl:sequence select="pk:c('PK_VA07', true(), count($nrs) eq count(distinct-values($nrs)),
					'mehrfach: ' || string-join(distinct-values($nrs[count(index-of($nrs, .)) gt 1]), ', '))"/>

				<!-- V80017 / V90006: VRG 0 = sum of VRG-Art 1, 2, 4 -->
				<xsl:if test="$vrgList = '0'">
					<xsl:variable name="parts" select="$byArt(('1','2','4'))"/>
					<xsl:for-each select="$rows800Total">
						<xsl:variable name="rc" select="."/>
						<xsl:if test="exists(($parts, '0') ! pk:fact('PK.800.01', ., $rc))">
							<xsl:sequence select="pk:cmp('PK_185', $rc, true(), pk:factNum('PK.800.01', '0', $rc), pk:factSum('PK.800.01', $parts, $rc), $tolerance, $toleranceWarning, '€')"/>
						</xsl:if>
					</xsl:for-each>
					<xsl:for-each select="$rows900">
						<xsl:variable name="rc" select="."/>
						<xsl:if test="exists(($parts, '0') ! pk:fact('PK.900.01', ., $rc))">
							<xsl:sequence select="pk:cmp('PK_190', $rc, true(), pk:factNum('PK.900.01', '0', $rc), pk:factSum('PK.900.01', $parts, $rc), 0, 0, 'n')"/>
						</xsl:if>
					</xsl:for-each>
				</xsl:if>
				<!-- V80021 / V90010: VRG mit Sub-VG = sum of its Sub-VG (R000-130) -->
				<xsl:variable name="subs" select="$byArt('3')"/>
				<xsl:variable name="mains" select="$byArt('2')"/>
				<xsl:for-each select="$mains">
					<xsl:variable name="m" select="."/>
					<xsl:variable name="mine" select="$subs[pk:factValue('PK.000.01', ., 'R000-130') eq $m]"/>
					<xsl:variable name="children" select="if (exists($mine)) then $mine else if (count($mains) eq 1) then $subs else ()"/>
					<xsl:for-each select="$rows800Total">
						<xsl:variable name="rc" select="."/>
						<xsl:if test="exists(($m, $children) ! pk:fact('PK.800.01', ., $rc))">
							<xsl:sequence select="pk:cmp('PK_187', 'VRG ' || $m || ' · ' || $rc, true(), pk:factNum('PK.800.01', $m, $rc), pk:factSum('PK.800.01', $children, $rc), $tolerance, $toleranceWarning, '€')"/>
						</xsl:if>
					</xsl:for-each>
					<xsl:for-each select="$rows900">
						<xsl:variable name="rc" select="."/>
						<xsl:if test="exists(($m, $children) ! pk:fact('PK.900.01', ., $rc))">
							<xsl:sequence select="pk:cmp('PK_192', 'VRG ' || $m || ' · ' || $rc, true(), pk:factNum('PK.900.01', $m, $rc), pk:factSum('PK.900.01', $children, $rc), 0, 0, 'n')"/>
						</xsl:if>
					</xsl:for-each>
				</xsl:for-each>
			</grp>
		</vrg>
	</xsl:template>

	<!-- ===================================================================== -->
	<!-- Phase 2: rendering                                                    -->
	<!-- ===================================================================== -->
	<!-- the result tree without the additional checks when "internal" is off -->
	<xsl:variable name="resFiltered">
		<xsl:apply-templates select="$results/results" mode="filter"/>
	</xsl:variable>
	<xsl:variable name="res" select="$resFiltered/results"/>

	<xsl:mode name="filter" on-no-match="shallow-copy"/>
	<xsl:template match="c[not($internal) and starts-with(@id, 'INT_')]" mode="filter"/>
	<xsl:template match="row[not($internal) and c and empty(c[not(starts-with(@id, 'INT_'))])]" mode="filter"/>
	<xsl:template match="grp[not($internal) and @id eq 'alloc']" mode="filter"/>

	<xsl:function name="pk:fmt" as="xs:string">
		<xsl:param name="v"/>
		<xsl:param name="unit" as="xs:string?"/>
		<xsl:variable name="s" select="normalize-space(string($v))"/>
		<xsl:sequence select="if (not($s castable as xs:decimal)) then '–'
			else if ($unit eq 'n') then format-number(round(xs:decimal($s)), '#.##0', 'de')
			else if ($unit eq '%') then format-number(round(xs:decimal($s), 2), '#.##0,00', 'de') || ' %'
			else format-number(round(xs:decimal($s), 2), '#.##0,00', 'de')"/>
	</xsl:function>

	<xsl:function name="pk:statusLabel" as="xs:string">
		<xsl:param name="s" as="xs:string"/>
		<xsl:sequence select="map {'E': 'Fehler', 'W': 'Warnung', 'OK': 'OK', 'I': 'Info', 'NA': 'n. a.'}($s)"/>
	</xsl:function>

	<xsl:function name="pk:code" as="xs:string">
		<xsl:param name="id" as="xs:string"/>
		<xsl:variable name="r" select="key('rule', $id, $catalog)"/>
		<xsl:sequence select="if ($r/@old ne '' and $r/@old ne 'extra') then string($r/@old) else $id"/>
	</xsl:function>

	<xsl:template name="chip">
		<xsl:param name="s" as="xs:string"/>
		<span class="st st-{$s}"><xsl:value-of select="map {'E': '✗', 'W': '!', 'OK': '✓', 'I': 'i', 'NA': '–'}($s)"/><xsl:text> </xsl:text><xsl:value-of select="pk:statusLabel($s)"/></span>
	</xsl:template>

	<xsl:template name="ruleCell">
		<xsl:param name="id" as="xs:string"/>
		<xsl:variable name="r" select="key('rule', $id, $catalog)"/>
		<td class="rule" title="{$r/@msg}">
			<a class="code" href="#rule-{$id}"><xsl:value-of select="pk:code($id)"/></a>
			<xsl:if test="pk:code($id) ne $id"><span class="sid"><xsl:value-of select="$id"/></span></xsl:if>
			<div class="rname"><xsl:value-of select="$r/@name"/></div>
		</td>
	</xsl:template>

	<!-- one result line: status | rule | object | ist | soll | diff | hint -->
	<xsl:template match="c" mode="line">
		<xsl:variable name="row" select="parent::row"/>
		<tr class="r-{@s}">
			<td><xsl:call-template name="chip"><xsl:with-param name="s" select="@s"/></xsl:call-template></td>
			<xsl:call-template name="ruleCell"><xsl:with-param name="id" select="@id"/></xsl:call-template>
			<td class="obj">
				<xsl:choose>
					<xsl:when test="$row/@fund">
						<span class="mono"><xsl:value-of select="$row/@key"/></span>
						<div class="sub">
							<xsl:value-of select="$row/@title"/>
							<xsl:if test="$row/@fund ne $row/@key"> · Fonds <span class="mono"><xsl:value-of select="$row/@fund"/></span></xsl:if>
							<xsl:if test="$row/@cat ne ''"> · Kat. <xsl:value-of select="$row/@cat"/></xsl:if>
							<xsl:if test="$row/@pnr ne ''"> · <xsl:value-of select="$row/@pnr"/></xsl:if>
						</div>
					</xsl:when>
					<xsl:when test="$row">
						<span class="mono"><xsl:value-of select="$row/@key"/></span>
						<div class="sub"><xsl:value-of select="string-join(($row/@title[. ne ''], $row/@cic[. ne ''] ! ('CIC ' || .), $row/@pnr[. ne '']), ' · ')"/></div>
					</xsl:when>
					<xsl:otherwise><span class="mono"><xsl:value-of select="@key"/></span></xsl:otherwise>
				</xsl:choose>
			</td>
			<td class="num"><xsl:value-of select="if (@ist) then pk:fmt(@ist, @u) else ''"/></td>
			<td class="num"><xsl:value-of select="if (@soll) then pk:fmt(@soll, @u) else ''"/></td>
			<td class="num diff"><xsl:value-of select="if (@diff) then pk:fmt(@diff, @u) else ''"/></td>
			<td class="hint"><xsl:value-of select="(@d, key('rule', @id, $catalog)/@name)[1]"/></td>
		</tr>
	</xsl:template>

	<xsl:template name="lineTable">
		<xsl:param name="items" as="element(c)*"/>
		<table class="tbl filterable">
			<thead><tr><th>Status</th><th>Regel</th><th>Objekt</th><th class="num">Ist</th><th class="num">Soll</th><th class="num">Differenz</th><th>Hinweis</th></tr></thead>
			<tbody><xsl:apply-templates select="$items" mode="line"/></tbody>
		</table>
	</xsl:template>

	<xsl:template name="counts">
		<xsl:param name="scope" as="element()*"/>
		<xsl:variable name="e" select="count($scope//c[@s = 'E'])"/>
		<xsl:variable name="w" select="count($scope//c[@s = 'W'])"/>
		<xsl:if test="$e gt 0"><span class="badge b-E"><xsl:value-of select="$e"/> ✗</span></xsl:if>
		<xsl:if test="$w gt 0"><span class="badge b-W"><xsl:value-of select="$w"/> !</span></xsl:if>
		<xsl:if test="$e + $w eq 0 and exists($scope//c[@s ne 'NA'])"><span class="badge b-OK">✓</span></xsl:if>
	</xsl:template>

	<xsl:template name="vrgTitle">
		<xsl:param name="vrg" as="element(vrg)"/>
		<span class="vt"><xsl:value-of select="if ($vrg/@nr eq 'gesamt') then 'Gesamt' else 'VRG ' || $vrg/@nr"/></span>
		<span class="vn"><xsl:value-of select="$vrg/@name"/></span>
		<xsl:if test="$vrg/@art ne ''">
			<span class="tag"><xsl:value-of select="($artLabel($vrg/@art), 'Art ' || $vrg/@art)[1]"/></span>
		</xsl:if>
		<xsl:if test="$vrg/@parent ne ''"><span class="tag">gehört zu VRG <xsl:value-of select="$vrg/@parent"/></span></xsl:if>
		<xsl:if test="$vrg/@kons eq 'J'"><span class="tag">konsortial</span></xsl:if>
	</xsl:template>

	<xsl:template match="/">
		<xsl:choose>
			<xsl:when test="$resultsOnly">
				<xsl:sequence select="$res"/>
			</xsl:when>
			<xsl:otherwise>
				<xsl:call-template name="report"/>
			</xsl:otherwise>
		</xsl:choose>
	</xsl:template>

	<xsl:template name="report">
		<xsl:if test="$resultsFile">
			<xsl:result-document href="{$resultsFile}" method="xml" indent="yes">
				<xsl:sequence select="$res"/>
			</xsl:result-document>
		</xsl:if>
		<xsl:variable name="all" select="$res//c[@s ne 'NA']"/>
		<xsl:variable name="nE" select="count($all[@s = 'E'])"/>
		<xsl:variable name="nW" select="count($all[@s = 'W'])"/>
		<xsl:variable name="vrgs" select="$res/vrg[@nr ne 'gesamt']"/>
		<xsl:variable name="ges" select="$res/vrg[@nr eq 'gesamt']"/>
		<html lang="de">
			<head>
				<meta charset="utf-8"/>
				<meta name="viewport" content="width=device-width, initial-scale=1"/>
				<title>QMV-Prüfbericht PK <xsl:value-of select="$root/@PK-Nummer"/> · <xsl:value-of select="$root/@GJ"/> <xsl:value-of select="$root/@Stichtag"/></title>
				<style><xsl:call-template name="css"/></style>
			</head>
			<body>
				<header class="top">
					<div class="wrap">
						<div class="brand">QMV-Prüfbericht</div>
						<h1>Pensionskasse <xsl:value-of select="$root/@PK-Nummer"/> · <xsl:value-of select="$root/@GJ"/>
							<xsl:text> </xsl:text><xsl:value-of select="$root/@Stichtag"/></h1>
						<dl class="meta">
							<div><dt>Stichtag</dt><dd><xsl:value-of select="if (exists($refDate)) then format-date($refDate, '[D01].[M01].[Y0001]') else '–'"/></dd></div>
							<div><dt>Meldung</dt><dd><xsl:value-of select="$root/@Typ_der_Meldung"/></dd></div>
							<div><dt>Taxonomie</dt><dd><xsl:value-of select="$root/@Taxonomie"/></dd></div>
							<div><dt>LEI</dt><dd class="mono"><xsl:value-of select="$root/@LEI"/></dd></div>
							<div><dt>DB-Quote</dt><dd><xsl:value-of select="$root/@DB"/></dd></div>
							<div><dt>Datei</dt><dd class="mono"><xsl:value-of select="tokenize(string(base-uri($root)), '[/\\]')[last()]"/></dd></div>
							<div><dt>Erstellt</dt><dd><xsl:value-of select="format-dateTime(current-dateTime(), '[D01].[M01].[Y0001] [H01]:[m01]')"/></dd></div>
						</dl>
					</div>
				</header>
				<nav class="toolbar">
					<div class="wrap">
						<a href="#dashboard">Übersicht</a>
						<a href="#issues">Auffälligkeiten</a>
						<a href="#all">Alle Ergebnisse</a>
						<a href="#legend">Regelkatalog</a>
						<span class="spacer"/>
						<input id="q" type="search" placeholder="Filter: Asset-ID, Regel, Text …" aria-label="Tabellen filtern"/>
						<button type="button" id="expand">Alle aufklappen</button>
						<button type="button" id="collapse">Alle zuklappen</button>
					</div>
				</nav>
				<main class="wrap">
					<!-- ============================ Dashboard ============================ -->
					<section id="dashboard">
						<h2>Übersicht</h2>
						<div class="tiles">
							<div class="tile t-{if ($nE gt 0) then 'E' else if ($nW gt 0) then 'W' else 'OK'} big">
								<div class="tv"><xsl:value-of select="if ($nE gt 0) then 'Fehler gefunden' else if ($nW gt 0) then 'Nur Warnungen' else 'Keine Auffälligkeiten'"/></div>
								<div class="tl">Gesamtergebnis</div>
							</div>
							<div class="tile t-E"><div class="tv"><xsl:value-of select="format-number($nE, '#.##0', 'de')"/></div><div class="tl">Fehler</div></div>
							<div class="tile t-W"><div class="tv"><xsl:value-of select="format-number($nW, '#.##0', 'de')"/></div><div class="tl">Warnungen</div></div>
							<div class="tile"><div class="tv"><xsl:value-of select="format-number(count($all), '#.##0', 'de')"/></div><div class="tl">Prüfungen ausgewertet¹</div></div>
							<div class="tile"><div class="tv"><xsl:value-of select="count($vrgs)"/></div><div class="tl">VRG</div></div>
							<div class="tile"><div class="tv"><xsl:value-of select="format-number(sum($vrgs/@n01), '#.##0', 'de')"/></div><div class="tl">Positionen (006.02)</div></div>
							<div class="tile"><div class="tv"><xsl:value-of select="format-number(sum($vrgs/@nlt), '#.##0', 'de')"/></div><div class="tl">Look-through-Zeilen</div></div>
						</div>
						<p class="note">¹ ohne die zeilenweisen Look-through-Prüfungen (dort werden nur Verstöße gezählt). Toleranz bei Beträgen: Fehler über <xsl:value-of select="pk:fmt($tolerance, ())"/> €, Warnung über <xsl:value-of select="pk:fmt($toleranceWarning, ())"/> €.</p>

						<h3>Ergebnis je VRG und Prüfgruppe</h3>
						<div class="scroll">
							<table class="tbl matrix">
								<thead>
									<tr>
										<th>VRG</th><th>Bezeichnung</th><th>Art</th>
										<xsl:for-each select="$grpOrder"><th class="c"><xsl:value-of select="$grpLabel(.)"/></th></xsl:for-each>
										<th class="c">Gesamt</th>
									</tr>
								</thead>
								<tbody>
									<xsl:for-each select="$vrgs">
										<xsl:variable name="vrg" select="."/>
										<tr>
											<td><a href="#a-{@nr}"><strong><xsl:value-of select="@nr"/></strong></a></td>
											<td><xsl:value-of select="@name"/></td>
											<td class="muted"><xsl:value-of select="($artLabel(@art), @art)[1]"/></td>
											<xsl:for-each select="$grpOrder">
												<xsl:variable name="g" select="$vrg/grp[@id = current()]"/>
												<xsl:variable name="e" select="count($g//c[@s = 'E'])"/>
												<xsl:variable name="w" select="count($g//c[@s = 'W'])"/>
												<td class="c m-{if (empty($g) or empty($g//c[@s ne 'NA'])) then 'NA' else if ($e gt 0) then 'E' else if ($w gt 0) then 'W' else 'OK'}">
													<xsl:choose>
														<xsl:when test="empty($g) or empty($g//c[@s ne 'NA'])">–</xsl:when>
														<xsl:when test="$e + $w eq 0">✓</xsl:when>
														<xsl:otherwise>
															<a href="#i-{$vrg/@nr}-{current()}">
																<xsl:if test="$e gt 0"><xsl:value-of select="$e"/> ✗</xsl:if>
																<xsl:if test="$e gt 0 and $w gt 0"><xsl:text> · </xsl:text></xsl:if>
																<xsl:if test="$w gt 0"><xsl:value-of select="$w"/> !</xsl:if>
															</a>
														</xsl:otherwise>
													</xsl:choose>
												</td>
											</xsl:for-each>
											<td class="c"><xsl:call-template name="counts"><xsl:with-param name="scope" select="$vrg"/></xsl:call-template></td>
										</tr>
									</xsl:for-each>
									<tr class="total">
										<td colspan="3"><a href="#a-gesamt"><strong>VRG-übergreifend</strong></a> <span class="muted"> (Meldungskopf, V80017, V80021, V90006, V90010)</span></td>
										<td colspan="{count($grpOrder)}"/>
										<td class="c"><xsl:call-template name="counts"><xsl:with-param name="scope" select="$ges"/></xsl:call-template></td>
									</tr>
								</tbody>
							</table>
						</div>

						<h3>Häufigste Auffälligkeiten</h3>
						<xsl:variable name="top" as="element(t)*">
							<xsl:for-each-group select="$res//c[@s = ('E','W')]" group-by="@id">
								<xsl:sort select="count(current-group())" order="descending"/>
								<t id="{current-grouping-key()}" s="{if (current-group()/@s = 'E') then 'E' else 'W'}" n="{count(current-group())}"
								   vrgs="{string-join(distinct-values(current-group()/ancestor::vrg/@nr ! (if (. eq 'gesamt') then 'Σ' else .)), ', ')}"/>
							</xsl:for-each-group>
						</xsl:variable>
						<xsl:choose>
							<xsl:when test="empty($top)"><p class="ok-msg">Keine Auffälligkeiten.</p></xsl:when>
							<xsl:otherwise>
								<table class="tbl">
									<thead><tr><th>Status</th><th>Regel</th><th class="num">Treffer</th><th>VRG</th><th>Fehlertext laut Spezifikation</th></tr></thead>
									<tbody>
										<xsl:for-each select="$top[position() le 15]">
											<tr>
												<td><xsl:call-template name="chip"><xsl:with-param name="s" select="@s"/></xsl:call-template></td>
												<xsl:call-template name="ruleCell"><xsl:with-param name="id" select="@id"/></xsl:call-template>
												<td class="num"><xsl:value-of select="format-number(xs:integer(@n), '#.##0', 'de')"/></td>
												<td><xsl:value-of select="@vrgs"/></td>
												<td class="hint"><xsl:value-of select="key('rule', @id, $catalog)/@msg"/></td>
											</tr>
										</xsl:for-each>
									</tbody>
								</table>
								<xsl:if test="count($top) gt 15"><p class="note">… und <xsl:value-of select="count($top) - 15"/> weitere Regeln mit Treffern (siehe Regelkatalog).</p></xsl:if>
							</xsl:otherwise>
						</xsl:choose>
					</section>

					<!-- ============================ Findings ============================ -->
					<section id="issues">
						<h2>Auffälligkeiten</h2>
						<xsl:if test="$nE + $nW eq 0"><p class="ok-msg">Keine Fehler oder Warnungen.</p></xsl:if>
						<xsl:for-each select="($ges, $vrgs)[.//c[@s = ('E','W')]]">
							<xsl:variable name="vrg" select="."/>
							<details class="vrg" open="open" id="i-{@nr}">
								<summary><xsl:call-template name="vrgTitle"><xsl:with-param name="vrg" select="."/></xsl:call-template>
									<span class="sp"/><xsl:call-template name="counts"><xsl:with-param name="scope" select="."/></xsl:call-template></summary>
								<xsl:for-each select="grp[.//c[@s = ('E','W')]]">
									<xsl:sort select="index-of(($grpOrder, 'gesamt'), @id)"/>
									<h4 id="i-{$vrg/@nr}-{@id}"><xsl:value-of select="$grpLabel(@id)"/>
										<xsl:text> </xsl:text><xsl:call-template name="counts"><xsl:with-param name="scope" select="."/></xsl:call-template></h4>
									<xsl:choose>
										<xsl:when test="@id eq 'lt'">
											<!-- look-through: limited number of rows per rule -->
											<xsl:variable name="items" select=".//c[@s = ('E','W')]"/>
											<table class="tbl filterable">
												<thead><tr><th>Status</th><th>Regel</th><th>Objekt</th><th class="num">Ist</th><th class="num">Soll</th><th class="num">Differenz</th><th>Hinweis</th></tr></thead>
												<tbody>
													<xsl:for-each-group select="$items" group-by="@id">
														<xsl:sort select="@s"/>
														<xsl:apply-templates select="current-group()[position() le $maxLookThroughRows]" mode="line"/>
														<xsl:if test="count(current-group()) gt $maxLookThroughRows">
															<tr class="more"><td colspan="7">… <xsl:value-of select="format-number(count(current-group()) - $maxLookThroughRows, '#.##0', 'de')"/> weitere Treffer für <xsl:value-of select="pk:code(current-grouping-key())"/> (Parameter <code>maxLookThroughRows</code>)</td></tr>
														</xsl:if>
													</xsl:for-each-group>
												</tbody>
											</table>
										</xsl:when>
										<xsl:otherwise>
											<xsl:call-template name="lineTable">
												<xsl:with-param name="items" as="element(c)*">
													<xsl:perform-sort select=".//c[@s = ('E','W')]"><xsl:sort select="@s"/></xsl:perform-sort>
												</xsl:with-param>
											</xsl:call-template>
										</xsl:otherwise>
									</xsl:choose>
								</xsl:for-each>
							</details>
						</xsl:for-each>
					</section>

					<!-- ============================ All results ============================ -->
					<section id="all">
						<h2>Alle Ergebnisse</h2>
						<p class="note">Je VRG eingeklappt. Nicht anwendbare Prüfungen werden nicht angezeigt.</p>
						<xsl:for-each select="($vrgs, $ges)">
							<details class="vrg" id="a-{@nr}">
								<summary><xsl:call-template name="vrgTitle"><xsl:with-param name="vrg" select="."/></xsl:call-template>
									<span class="sp"/><xsl:call-template name="counts"><xsl:with-param name="scope" select="."/></xsl:call-template></summary>
								<xsl:if test="@fb800 and @nr ne 'gesamt'">
									<p class="facts">PNR 800 Bilanzwert: <strong><xsl:value-of select="pk:fmt(@fb800, ())"/> €</strong>
										· Positionen 006.02: <xsl:value-of select="@n01"/> · Vermögenswerte: <xsl:value-of select="@n02"/>
										· Look-through-Zeilen: <xsl:value-of select="format-number(xs:integer(@nlt), '#.##0', 'de')"/></p>
								</xsl:if>
								<xsl:for-each select="grp">
									<xsl:sort select="index-of(($grpOrder, 'gesamt'), @id)"/>
									<details class="grp">
										<summary><xsl:value-of select="$grpLabel(@id)"/><span class="sp"/><xsl:call-template name="counts"><xsl:with-param name="scope" select="."/></xsl:call-template></summary>
										<xsl:choose>
											<xsl:when test="@id eq 'assets'"><xsl:apply-templates select="." mode="assets"/></xsl:when>
											<xsl:when test="@id eq 'lt'"><xsl:apply-templates select="." mode="lt"/></xsl:when>
											<xsl:otherwise>
												<xsl:call-template name="lineTable"><xsl:with-param name="items" select="c[@s ne 'NA']"/></xsl:call-template>
											</xsl:otherwise>
										</xsl:choose>
									</details>
								</xsl:for-each>
							</details>
						</xsl:for-each>
					</section>

					<!-- ============================ Rule catalogue ============================ -->
					<!-- ============================ Rule catalogue (collapsed) ============================ -->
					<details id="legend" class="legend">
						<summary>
							<span class="lt">Regelkatalog und Prüflogik</span>
							<span class="muted"><xsl:value-of select="count($catalog/catalog/rule)"/> Regeln · Kommentar und XPath-Abfrage je Regel</span>
						</summary>
						<div class="legend-intro">
							<p>Regeln laut <em>PK-Meldewesen_Validierungen 2.9.0 Hotfix 2</em> (Blocking = Fehler, Non-Blocking = Warnung) sowie zusätzliche Plausibilitätsprüfungen („zusätzlich“, abschaltbar).</p>
							<p>Die <strong>XPath-Abfrage</strong> (XPath 3.1) lässt sich direkt auf die Meldungsdatei ausführen, z. B. in einer XPath-Konsole.
								Sie liefert genau die Datenpunkte, die der Bericht für diese Regel meldet: bei Vermögenswerten die Position (PK.006.02.01.01), bei der Durchrechnung die Zeile aus PK.006.03, bei Formblättern die betroffene Position; bei VRG- und Kopfprüfungen die Stammdaten-Tabelle bzw. das Wurzelelement.
								Die <strong>Bedingung</strong> ist der regelspezifische Kern der Abfrage.</p>
							<ul>
								<li><code>$has(x)</code>: Feld gemeldet (nicht leer); „Not applicable“ ist ein gültiger Wert und gilt als gemeldet.</li>
								<li><code>$real(x)</code>: Feld mit Inhalt (nicht leer und nicht „Not applicable“). Bei „darf nicht gemeldet sein“ ist „Not applicable“ daher zulässig.</li>
								<li><code>$like('71|8#')</code>: CIC an Stelle 3–4 (Muster ##71, ##8#; # = beliebiges Zeichen). <code>$isin</code>: Type of Code ISIN oder CAU/ISIN.</li>
								<li><code>$num(x)</code>: Zahlenwert, 0 wenn leer. <code>$ref</code>: Stichtag aus GJ und Quartal. <code>$art(v)</code>: VRG-Art (R000-120).</li>
								<li>Beträge: Abweichung über <xsl:value-of select="pk:fmt($toleranceWarning, ())"/> € = Warnung, über <xsl:value-of select="pk:fmt($tolerance, ())"/> € = Fehler; Anzahlen (Formblatt 900) exakt.</li>
							</ul>
							<p class="note">Nicht geprüft, weil die Templates nicht Teil dieser Meldung sind: VDL088–VDL096 (PK.300.01), VDL097–VDL099 (PK.100.01), VA00044 (PK.300.01), VA00055 (Jahresmeldung) sowie die Prüfungen gegen FMA-Stammdaten und GLEIF.</p>
						</div>
						<table class="tbl filterable legend-tbl">
							<thead><tr><th class="sortable" data-first="ascending" aria-sort="ascending" tabindex="0" title="Nach Prüfgruppe sortieren">Regel</th><th>Kommentar</th><th>Bedingung und XPath-Abfrage</th><th class="num sortable" aria-sort="none" tabindex="0" title="Nach Anzahl Prüfungen sortieren">Geprüft</th><th class="num sortable" aria-sort="none" tabindex="0" title="Nach Anzahl Treffer sortieren">Treffer</th></tr></thead>
							<tbody>
								<xsl:for-each select="$catalog/catalog/rule">
									<xsl:sort select="index-of(($grpOrder, 'gesamt'), @grp)"/>
									<xsl:variable name="hits" select="$res//c[@id = current()/@id]"/>
									<tr id="rule-{@id}">
										<td class="rule" data-sort="{position()}"><span class="code"><xsl:value-of select="pk:code(@id)"/></span>
											<xsl:if test="pk:code(@id) ne @id"><span class="sid"><xsl:value-of select="@id"/></span></xsl:if>
											<div class="rname"><xsl:value-of select="@name"/></div>
											<div class="rmeta"><xsl:value-of select="$grpLabel(@grp)"/> · <xsl:value-of select="if (@old eq 'extra') then 'zusätzlich' else if (@sev eq 'E') then 'Blocking' else 'Non-Blocking'"/></div></td>
										<td class="comment">
											<xsl:value-of select="note"/>
											<xsl:if test="@msg ne ''"><div class="spec">Spec: <xsl:value-of select="@msg"/></div></xsl:if>
										</td>
										<td class="logic">
											<code class="cond"><xsl:value-of select="cond"/></code>
											<details class="xp">
												<summary>XPath-Abfrage <button type="button" class="copy-xp" title="XPath-Abfrage in die Zwischenablage kopieren"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16" width="12" height="12" aria-hidden="true"><path fill="currentColor" d="M4 2a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V2Zm2-1a1 1 0 0 0-1 1v8a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1V2a1 1 0 0 0-1-1H6ZM2 5a1 1 0 0 0-1 1v8a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1v-1h1v1a2 2 0 0 1-2 2H2a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h1v1H2Z"/></svg><span>Kopieren</span></button></summary>
												<pre><xsl:value-of select="xpath"/></pre>
											</details>
										</td>
										<td class="num" data-sort="{if (@grp eq 'lt') then -1 else count($hits[@s ne 'NA'])}"><xsl:value-of select="if (@grp eq 'lt') then '–' else format-number(count($hits[@s ne 'NA']), '#.##0', 'de')"/></td>
										<td class="num">
											<xsl:variable name="n" select="count($hits[@s = ('E','W')])"/>
											<xsl:attribute name="data-sort" select="$n"/>
											<xsl:attribute name="class" select="'num' || (if ($n gt 0) then ' hit' else '')"/>
											<xsl:value-of select="format-number($n, '#.##0', 'de')"/>
										</td>
									</tr>
								</xsl:for-each>
							</tbody>
						</table>
					</details>
				</main>
				<footer class="wrap">Erstellt mit FreeXmlToolkit (PK-Meldewesen) · Taxonomie 2.9.0 Hotfix 2</footer>
				<script><xsl:call-template name="js"/></script>
			</body>
		</html>
	</xsl:template>

	<!-- all positions of a VRG with their key figures -->
	<xsl:template match="grp" mode="assets">
		<xsl:variable name="rows" select="row"/>
		<div class="scroll">
			<table class="tbl filterable assets">
				<thead>
					<tr>
						<th>Asset-ID</th><th>Bezeichnung</th><th>CIC</th><th>PNR</th>
						<th class="num">Bilanzwert</th>
						<th class="num" title="Summe C1100PK der Durchrechnung; darunter Differenz zum Bilanzwert (VDL066)">Σ Look-through BW</th>
						<th class="num" title="Summe C1110PK der Durchrechnung; darunter Differenz zum Marktwert">Σ Look-through MW</th>
						<th class="num" title="Menge × Kurs + Stückzinsen; darunter Differenz zum Bilanzwert (VDL032)">Menge × Kurs</th>
						<th class="num" title="Nominale × Kurs-% + Stückzinsen; darunter Differenz zum Bilanzwert (VDL033)">Nominale × %</th>
						<th class="num">Anschaffungswert</th>
						<th>Ergebnis</th>
					</tr>
				</thead>
				<tbody>
					<xsl:for-each select="$rows">
						<xsl:variable name="bw" select="xs:decimal((@bw, 0)[1])"/>
						<xsl:variable name="bad" select="c[@s = ('E','W')]"/>
						<tr class="{if ($bad[@s = 'E']) then 'r-E' else if ($bad) then 'r-W' else ''}">
							<td class="mono"><xsl:value-of select="@key"/></td>
							<td><xsl:value-of select="@title"/></td>
							<td class="mono"><xsl:value-of select="@cic"/></td>
							<td class="mono"><xsl:value-of select="@pnr"/></td>
							<td class="num"><xsl:value-of select="pk:fmt(@bw, ())"/></td>
							<xsl:call-template name="valueCell">
								<xsl:with-param name="show" select="xs:integer((@ltn, 0)[1]) gt 0"/>
								<xsl:with-param name="value" select="@ltbw"/>
								<xsl:with-param name="diff" select="if (@ltbw) then $bw - xs:decimal(@ltbw) else ()"/>
								<xsl:with-param name="check" select="c[@id = 'PK_209_1']"/>
							</xsl:call-template>
							<xsl:call-template name="valueCell">
								<xsl:with-param name="show" select="xs:integer((@ltn, 0)[1]) gt 0"/>
								<xsl:with-param name="value" select="@ltmw"/>
								<xsl:with-param name="diff" select="if (@ltmw) then xs:decimal(@mw) - xs:decimal(@ltmw) else ()"/>
								<xsl:with-param name="check" select="c[@id = 'INT_LTMW']"/>
							</xsl:call-template>
							<xsl:call-template name="valueCell">
								<xsl:with-param name="show" select="string(@qty) eq 'true'"/>
								<xsl:with-param name="value" select="@qp"/>
								<xsl:with-param name="diff" select="if (@qp) then $bw - xs:decimal(@qp) else ()"/>
								<xsl:with-param name="check" select="c[@id = 'PBV66']"/>
							</xsl:call-template>
							<xsl:call-template name="valueCell">
								<xsl:with-param name="show" select="string(@par) eq 'true'"/>
								<xsl:with-param name="value" select="@pp"/>
								<xsl:with-param name="diff" select="if (@pp) then $bw - xs:decimal(@pp) else ()"/>
								<xsl:with-param name="check" select="c[@id = 'PBV67']"/>
							</xsl:call-template>
							<td class="num {c[@id = ('PBV43','PBV44')][@s = ('E','W')][1]/('cell-' || @s)}"><xsl:value-of select="pk:fmt(@acq, ())"/></td>
							<td class="res">
								<xsl:choose>
									<xsl:when test="empty($bad)"><span class="st st-OK">✓ <xsl:value-of select="count(c[@s ne 'NA'])"/> Prüfungen OK</span></xsl:when>
									<xsl:otherwise>
										<xsl:for-each select="$bad">
											<xsl:sort select="@s"/>
											<a class="pill p-{@s}" href="#rule-{@id}" title="{(@d, key('rule', @id, $catalog)/@name)[1]}"><xsl:value-of select="pk:code(@id)"/></a>
										</xsl:for-each>
									</xsl:otherwise>
								</xsl:choose>
							</td>
						</tr>
					</xsl:for-each>
				</tbody>
			</table>
		</div>
		<details class="sub">
			<summary>Regelstatistik List of Assets</summary>
			<table class="tbl">
				<thead><tr><th>Regel</th><th class="num">Anwendbar</th><th class="num">OK</th><th class="num">Fehler</th><th class="num">Warnungen</th></tr></thead>
				<tbody>
					<xsl:for-each-group select="$rows/c[@s ne 'NA']" group-by="@id">
						<xsl:sort select="count(current-group()[@s = ('E','W')])" order="descending"/>
						<tr>
							<xsl:call-template name="ruleCell"><xsl:with-param name="id" select="current-grouping-key()"/></xsl:call-template>
							<td class="num"><xsl:value-of select="count(current-group())"/></td>
							<td class="num"><xsl:value-of select="count(current-group()[@s = ('OK','I')])"/></td>
							<td class="num"><xsl:value-of select="count(current-group()[@s = 'E'])"/></td>
							<td class="num"><xsl:value-of select="count(current-group()[@s = 'W'])"/></td>
						</tr>
					</xsl:for-each-group>
				</tbody>
			</table>
		</details>
	</xsl:template>

	<xsl:template name="valueCell">
		<xsl:param name="show" as="xs:boolean"/>
		<xsl:param name="value"/>
		<xsl:param name="diff" as="xs:decimal?"/>
		<xsl:param name="check" as="element(c)?"/>
		<td class="num {if ($check/@s = ('E','W')) then 'cell-' || $check/@s else ''}">
			<xsl:choose>
				<xsl:when test="$show">
					<xsl:value-of select="pk:fmt($value, ())"/>
					<div class="dv"><xsl:value-of select="pk:fmt($diff, ())"/></div>
				</xsl:when>
				<xsl:otherwise><span class="muted">–</span></xsl:otherwise>
			</xsl:choose>
		</td>
	</xsl:template>

	<!-- look-through overview: violations per rule and the looked-through funds -->
	<xsl:template match="grp" mode="lt">
		<xsl:variable name="vrg" select="ancestor::vrg"/>
		<xsl:variable name="fb800" select="xs:decimal(($vrg/@fb800, 0)[1])"/>
		<table class="tbl">
			<thead><tr><th>Regel</th><th class="num">Verstöße</th><th>Status</th></tr></thead>
			<tbody>
				<xsl:for-each select="$catalog/catalog/rule[@grp = 'lt']">
					<xsl:variable name="n" select="count($vrg/grp[@id = 'lt']//c[@id = current()/@id])"/>
					<tr>
						<xsl:call-template name="ruleCell"><xsl:with-param name="id" select="@id"/></xsl:call-template>
						<td class="num"><xsl:value-of select="format-number($n, '#.##0', 'de')"/></td>
						<td><xsl:call-template name="chip"><xsl:with-param name="s" select="if ($n gt 0) then string(@sev) else 'OK'"/></xsl:call-template></td>
					</tr>
				</xsl:for-each>
			</tbody>
		</table>
		<h5>Durchgerechnete Fonds</h5>
		<div class="scroll">
			<table class="tbl filterable">
				<thead><tr><th>Fonds (C0010PK)</th><th>Bezeichnung</th><th>CIC</th><th class="num">Zeilen</th><th class="num">Σ Bilanzwert</th><th class="num">Σ Marktwert</th><th class="num">nicht durchgerechnet</th><th class="num">% der VRG</th></tr></thead>
				<tbody>
					<xsl:for-each select="fund">
						<xsl:sort select="xs:decimal(@nd)" order="descending"/>
						<xsl:sort select="@key"/>
						<xsl:variable name="ndp" select="pk:pct(xs:decimal(@nd), $fb800)"/>
						<tr>
							<td class="mono"><xsl:value-of select="@key"/></td>
							<td><xsl:value-of select="@title"/></td>
							<td class="mono"><xsl:value-of select="@cic"/></td>
							<td class="num"><xsl:value-of select="format-number(xs:integer(@n), '#.##0', 'de')"/></td>
							<td class="num"><xsl:value-of select="pk:fmt(@bw, ())"/></td>
							<td class="num"><xsl:value-of select="pk:fmt(@mw, ())"/></td>
							<td class="num"><xsl:value-of select="pk:fmt(@nd, ())"/></td>
							<td class="num {if ($ndp gt $lookThroughLimitAsset) then 'cell-W' else ''}"><xsl:value-of select="pk:fmt($ndp, '%')"/></td>
						</tr>
					</xsl:for-each>
				</tbody>
			</table>
		</div>
	</xsl:template>

	<!-- ===================================================================== -->
	<!-- Inline CSS and JavaScript                                             -->
	<!-- ===================================================================== -->
	<xsl:template name="css">
:root {
	--bg: #f4f6f9; --surface: #ffffff; --surface-2: #f8fafc; --text: #1c2530; --muted: #66727f; --border: #dde3ea;
	--accent: #1d5c96; --accent-fg: #ffffff; --header: #17324d;
	--err: #b42318; --err-bg: #fdecec; --warn: #9a5b00; --warn-bg: #fff4dc; --ok: #22744a; --ok-bg: #e7f5ec; --info: #2f5f8a; --info-bg: #e8f0f8;
	--radius: 8px; --shadow: 0 1px 2px rgba(16, 24, 40, .06), 0 1px 3px rgba(16, 24, 40, .08);
}
@media (prefers-color-scheme: dark) {
	:root {
		--bg: #0f151c; --surface: #18212b; --surface-2: #1d2833; --text: #e4e9ef; --muted: #93a1b0; --border: #2c3946;
		--accent: #6aa8e0; --accent-fg: #0f151c; --header: #0b1824;
		--err: #ff8a80; --err-bg: #3b1d1d; --warn: #f5c060; --warn-bg: #3a2e14; --ok: #74d39b; --ok-bg: #173325; --info: #8cbbe6; --info-bg: #1b2d3f;
		--shadow: none;
	}
}
* { box-sizing: border-box; }
body { margin: 0; background: var(--bg); color: var(--text); font: 14px/1.45 "Segoe UI", system-ui, -apple-system, "Open Sans", Arial, sans-serif; }
.wrap { max-width: 1600px; margin: 0 auto; padding: 0 24px; }
a { color: var(--accent); text-decoration: none; }
a:hover { text-decoration: underline; }
.mono { font-family: ui-monospace, "Cascadia Mono", Consolas, monospace; font-size: 12.5px; }
.muted { color: var(--muted); }
header.top { background: var(--header); color: #fff; padding: 20px 0 16px; }
header.top .brand { text-transform: uppercase; letter-spacing: .12em; font-size: 11px; opacity: .7; }
header.top h1 { margin: 2px 0 12px; font-size: 24px; font-weight: 600; }
dl.meta { display: flex; flex-wrap: wrap; gap: 6px 28px; margin: 0; }
dl.meta div { display: flex; flex-direction: column; }
dl.meta dt { font-size: 11px; opacity: .65; }
dl.meta dd { margin: 0; font-size: 13px; }
nav.toolbar { position: sticky; top: 0; z-index: 10; background: var(--surface); border-bottom: 1px solid var(--border); box-shadow: var(--shadow); }
nav.toolbar .wrap { display: flex; align-items: center; gap: 16px; padding-top: 8px; padding-bottom: 8px; flex-wrap: wrap; }
nav.toolbar a { font-weight: 600; }
nav.toolbar .spacer { flex: 1; }
nav.toolbar input { width: 280px; padding: 6px 10px; border: 1px solid var(--border); border-radius: 6px; background: var(--surface-2); color: var(--text); }
nav.toolbar button { padding: 6px 12px; border: 1px solid var(--border); border-radius: 6px; background: var(--surface-2); color: var(--text); cursor: pointer; }
nav.toolbar button:hover { border-color: var(--accent); }
section { margin: 28px 0 40px; }
h2 { font-size: 20px; margin: 0 0 14px; padding-bottom: 6px; border-bottom: 2px solid var(--border); }
h3 { font-size: 15px; margin: 26px 0 10px; }
h4 { font-size: 14px; margin: 18px 0 8px; display: flex; align-items: center; gap: 8px; }
h5 { font-size: 13px; margin: 16px 0 6px; }
.note { color: var(--muted); font-size: 12.5px; margin: 8px 0; }
.ok-msg { color: var(--ok); font-weight: 600; }
.tiles { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 12px; }
.tile { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius); padding: 14px 16px; box-shadow: var(--shadow); }
.tile .tv { font-size: 24px; font-weight: 650; font-variant-numeric: tabular-nums; }
.tile .tl { color: var(--muted); font-size: 12px; }
.tile.big { grid-column: span 2; }
.tile.big .tv { font-size: 20px; }
.tile.t-E { border-left: 4px solid var(--err); } .tile.t-E .tv { color: var(--err); }
.tile.t-W { border-left: 4px solid var(--warn); } .tile.t-W .tv { color: var(--warn); }
.tile.t-OK { border-left: 4px solid var(--ok); } .tile.t-OK .tv { color: var(--ok); }
.scroll { overflow-x: auto; }
.scroll table.tbl th { position: static; }
table.tbl { width: 100%; border-collapse: separate; border-spacing: 0; background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius); box-shadow: var(--shadow); font-size: 13px; }
table.tbl th { position: sticky; top: 49px; background: var(--surface-2); text-align: left; font-weight: 600; color: var(--muted); font-size: 12px; padding: 8px 10px; border-bottom: 1px solid var(--border); white-space: nowrap; }
table.tbl td { padding: 6px 10px; border-bottom: 1px solid var(--border); vertical-align: top; }
table.tbl tbody tr:last-child td { border-bottom: 0; }
table.tbl tbody tr:hover td { background: var(--surface-2); }
.num { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }
table.tbl th.num { text-align: right; }
table.tbl th.num, table.tbl td.num { width: 1%; padding-left: 16px; }
table.tbl td.num + td.num, table.tbl th.num + th.num { border-left: 1px solid var(--border); }
tr.r-E td.diff { color: var(--err); font-weight: 650; }
tr.r-W td.diff { color: var(--warn); font-weight: 650; }
th.c, td.c { text-align: center; }
td.rule { white-space: nowrap; }
td.rule .code { font-weight: 650; font-family: ui-monospace, Consolas, monospace; font-size: 12.5px; }
td.rule .sid { margin-left: 6px; color: var(--muted); font-size: 11px; }
td.rule .rname { color: var(--muted); font-size: 11.5px; white-space: normal; max-width: 260px; }
td.obj .sub, .dv { color: var(--muted); font-size: 11.5px; }
td.hint { max-width: 520px; }
.st { display: inline-block; padding: 1px 8px; border-radius: 999px; font-size: 11.5px; font-weight: 600; white-space: nowrap; }
.st-E { background: var(--err-bg); color: var(--err); }
.st-W { background: var(--warn-bg); color: var(--warn); }
.st-OK { background: var(--ok-bg); color: var(--ok); }
.st-I, .st-NA { background: var(--info-bg); color: var(--info); }
tr.r-E td:first-child { box-shadow: inset 3px 0 0 var(--err); }
tr.r-W td:first-child { box-shadow: inset 3px 0 0 var(--warn); }
tr.r-OK td, tr.r-I td { color: var(--muted); }
tr.more td { color: var(--muted); font-style: italic; }
td.cell-E { background: var(--err-bg); } td.cell-E .dv { color: var(--err); font-weight: 600; }
td.cell-W { background: var(--warn-bg); } td.cell-W .dv { color: var(--warn); font-weight: 600; }
td.num.hit { color: var(--err); font-weight: 650; }
.badge { display: inline-block; min-width: 26px; text-align: center; padding: 0 7px; border-radius: 999px; font-size: 11.5px; font-weight: 650; margin-left: 4px; }
.b-E { background: var(--err); color: #fff; } .b-W { background: var(--warn); color: #fff; } .b-OK { background: var(--ok-bg); color: var(--ok); }
.pill { display: inline-block; margin: 1px 3px 1px 0; padding: 0 6px; border-radius: 4px; font-size: 11px; font-family: ui-monospace, Consolas, monospace; }
.p-E { background: var(--err-bg); color: var(--err); } .p-W { background: var(--warn-bg); color: var(--warn); }
table.matrix td.m-E { background: var(--err-bg); font-weight: 650; } table.matrix td.m-E a { color: var(--err); }
table.matrix td.m-W { background: var(--warn-bg); font-weight: 650; } table.matrix td.m-W a { color: var(--warn); }
table.matrix td.m-OK { color: var(--ok); }
table.matrix td.m-NA { color: var(--muted); }
table.matrix tr.total td { background: var(--surface-2); }
details.vrg { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius); margin: 12px 0; padding: 0 16px; box-shadow: var(--shadow); }
details.vrg[open] { padding-bottom: 16px; }
details.vrg > summary { margin: 0 -16px; padding: 12px 16px; cursor: pointer; display: flex; align-items: center; gap: 10px; font-size: 15px; }
details.vrg[open] > summary { border-bottom: 1px solid var(--border); }
details.grp { margin: 10px 0; }
details.grp > summary, details.sub > summary { cursor: pointer; font-weight: 600; padding: 6px 0; display: flex; gap: 8px; align-items: center; }
summary .vt { font-weight: 700; }
summary .sp { flex: 1; }
.tag { font-size: 11px; color: var(--muted); border: 1px solid var(--border); border-radius: 999px; padding: 0 8px; }
.facts { color: var(--muted); font-size: 12.5px; margin-top: 12px; }
details.legend { margin: 40px 0 24px; background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius); box-shadow: var(--shadow); }
details.legend > summary { padding: 14px 16px; cursor: pointer; display: flex; align-items: baseline; gap: 12px; }
details.legend > summary .lt { font-size: 18px; font-weight: 650; }
details.legend[open] > summary { border-bottom: 1px solid var(--border); }
details.legend > .legend-intro, details.legend > table { margin: 12px 16px 16px; width: calc(100% - 32px); }
.legend-intro { font-size: 13px; }
.legend-intro p, .legend-intro ul { margin: 6px 0; }
.legend-intro code, code.cond { font-family: ui-monospace, Consolas, monospace; font-size: 12px; background: var(--surface-2); border: 1px solid var(--border); border-radius: 4px; padding: 0 4px; }
table.legend-tbl td.comment { max-width: 420px; }
table.legend-tbl td.comment .spec { color: var(--muted); font-size: 11.5px; margin-top: 4px; }
table.legend-tbl td.logic { max-width: 620px; }
table.legend-tbl code.cond { display: block; white-space: pre-wrap; word-break: break-word; padding: 4px 6px; }
td.rule .rmeta { color: var(--muted); font-size: 11px; }
details.xp > summary { cursor: pointer; color: var(--accent); font-size: 12px; margin-top: 4px; }
th.sortable { cursor: pointer; user-select: none; }
th.sortable:hover, th.sortable:focus-visible { color: var(--accent); outline: none; }
th.sortable::after { content: ' ↕'; opacity: .45; }
th.sortable[aria-sort="descending"]::after { content: ' ↓'; opacity: 1; }
th.sortable[aria-sort="ascending"]::after { content: ' ↑'; opacity: 1; }
.copy-xp { display: inline-flex; align-items: center; gap: 4px; vertical-align: middle; margin-left: 8px; padding: 1px 8px; font-size: 11px; color: var(--text); background: var(--surface); border: 1px solid var(--border); border-radius: 4px; cursor: pointer; }
.copy-xp:hover { border-color: var(--accent); color: var(--accent); }
.copy-xp.ok { border-color: var(--ok); color: var(--ok); }
.copy-xp.fail { border-color: var(--err); color: var(--err); }
@media print { .copy-xp { display: none; } }
details.xp pre { margin: 6px 0 0; padding: 8px 10px; max-height: 360px; overflow: auto; white-space: pre-wrap; word-break: break-word; font: 11.5px/1.4 ui-monospace, Consolas, monospace; background: var(--surface-2); border: 1px solid var(--border); border-radius: 6px; }
details.vrg > summary, details.grp > summary, details.sub > summary, details.legend > summary { list-style: none; }
details.vrg > summary::-webkit-details-marker, details.grp > summary::-webkit-details-marker,
details.sub > summary::-webkit-details-marker, details.legend > summary::-webkit-details-marker { display: none; }
details.vrg > summary::before, details.grp > summary::before, details.sub > summary::before, details.legend > summary::before {
	content: '▸'; color: var(--muted); font-size: 12px; transition: transform .15s; display: inline-block; }
details[open].vrg > summary::before, details[open].grp > summary::before, details[open].sub > summary::before,
details[open].legend > summary::before { transform: rotate(90deg); }
footer { color: var(--muted); font-size: 12px; padding-bottom: 30px; }
[hidden] { display: none !important; }
@media print {
	nav.toolbar { display: none; }
	body { background: #fff; }
	details > summary { list-style: none; }
	table.tbl th { position: static; }
	.tile, table.tbl, details.vrg { box-shadow: none; }
}
	</xsl:template>

	<xsl:template name="js">
(function () {
	var q = document.getElementById('q');
	var all = function (open) { document.querySelectorAll('details').forEach(function (d) { d.open = open; }); };
	document.getElementById('expand').addEventListener('click', function () { all(true); });
	document.getElementById('collapse').addEventListener('click', function () { all(false); });
	var timer;
	q.addEventListener('input', function () {
		clearTimeout(timer);
		timer = setTimeout(function () {
			var t = q.value.trim().toLowerCase();
			document.querySelectorAll('table.filterable tbody tr').forEach(function (r) {
				r.hidden = t !== '' ? r.textContent.toLowerCase().indexOf(t) === -1 : false;
			});
			if (t !== '') { all(true); }
		}, 200);
	});
	window.addEventListener('beforeprint', function () { all(true); });
	// sortable columns (data-sort holds the numeric value of each cell)
	document.querySelectorAll('th.sortable').forEach(function (th) {
		var sort = function () {
			var table = th.closest('table');
			var tbody = table.tBodies[0];
			var idx = Array.prototype.indexOf.call(th.parentNode.children, th);
			var current = th.getAttribute('aria-sort');
			var dir = current === 'none' ? (th.getAttribute('data-first') || 'descending')
				: (current === 'descending' ? 'ascending' : 'descending');
			table.querySelectorAll('th.sortable').forEach(function (h) { h.setAttribute('aria-sort', 'none'); });
			th.setAttribute('aria-sort', dir);
			var val = function (r) { return parseFloat(r.cells[idx].getAttribute('data-sort')) || 0; };
			var rows = Array.prototype.slice.call(tbody.rows);
			rows.sort(function (a, b) { var d = val(a) - val(b); return dir === 'descending' ? -d : d; });
			rows.forEach(function (r) { tbody.appendChild(r); });
		};
		th.addEventListener('click', sort);
		th.addEventListener('keydown', function (e) {
			if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); sort(); }
		});
	});
	// copy the XPath query of a rule (button in the summary: does not toggle the details)
	document.addEventListener('click', function (e) {
		var b = e.target.closest ? e.target.closest('.copy-xp') : null;
		if (!b) { return; }
		e.preventDefault();
		e.stopPropagation();
		var text = b.closest('details').querySelector('pre').textContent;
		var done = function (ok) {
			var label = b.querySelector('span');
			label.textContent = ok ? 'Kopiert' : 'Kopieren fehlgeschlagen';
			b.classList.add(ok ? 'ok' : 'fail');
			setTimeout(function () { label.textContent = 'Kopieren'; b.classList.remove('ok', 'fail'); }, 1500);
		};
		var fallback = function () {
			var t = document.createElement('textarea');
			t.value = text;
			t.style.position = 'fixed';
			t.style.opacity = '0';
			document.body.appendChild(t);
			t.select();
			var ok = false;
			try { ok = document.execCommand('copy'); } catch (x) { ok = false; }
			document.body.removeChild(t);
			done(ok);
		};
		if (navigator.clipboard) {
			navigator.clipboard.writeText(text).then(function () { done(true); }, fallback);
		} else {
			fallback();
		}
	});
	// open the collapsed sections on the way to an anchor target
	var reveal = function () {
		var el = location.hash ? document.getElementById(location.hash.substring(1)) : null;
		for (var p = el; p; p = p.parentElement) { if (p.tagName === 'DETAILS') { p.open = true; } }
		if (el) { el.scrollIntoView(); }
	};
	window.addEventListener('hashchange', reveal);
	reveal();
})();
	</xsl:template>
</xsl:stylesheet>
