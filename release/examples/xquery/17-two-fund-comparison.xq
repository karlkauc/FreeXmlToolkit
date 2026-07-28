xquery version "3.1";

(:~
 : Fund Comparison (doc() with graceful fallback)
 :
 : Purpose: Compare two funds side by side. When $comparisonFile points to a
 : second FundsXML4 file (absolute file URI), it is loaded via doc() and both
 : funds are compared metric by metric. Out of the box — queries pasted into
 : the Query Console have no base URI, so relative doc() references cannot
 : resolve — the query instead compares the share classes of the active
 : document with the same table layout.
 :
 : To enable the two-fund mode, set e.g.
 :   declare variable $comparisonFile :=
 :       'file:///C:/data/examples/xml/FundsXML_422_Bond_Fund.xml';
 :
 : Techniques: doc()/doc-available with try/catch, cross-document comparison,
 : dynamic Markdown table columns, higher-order metric rows.
 :
 : @author FreeXmlToolkit Examples
 : @version 1.0
 :)

declare namespace output = "http://www.w3.org/2010/xslt-xquery-serialization";
declare option output:method "text";

declare variable $nl := codepoints-to-string(10);

(: Absolute file URI of the second FundsXML4 document; '' = share-class mode. :)
declare variable $comparisonFile := '';

declare function local:try-doc($uri as xs:string) as document-node()? {
    try { if ($uri != '' and doc-available($uri)) then doc($uri) else () }
    catch * { () }
};

(: Extracts the comparable key figures of a FundsXML4 document as a map. :)
declare function local:metrics($root as element()) as map(*) {
    let $fund := $root/Funds/Fund[1]
    let $ccy := string($fund/Currency)
    let $positions := $fund/FundDynamicData/Portfolios/Portfolio/Positions/Position
    return map {
        'name': string($fund/Names/OfficialName),
        'isin': string(($fund/Identifiers/ISIN, 'n/a')[1]),
        'currency': $ccy,
        'nav': number($fund/FundDynamicData/TotalAssetValues/TotalAssetValue[1]
                           /TotalNetAssetValue/Amount[1]),
        'positions': count($positions),
        'currencies': count(distinct-values($positions/Currency)),
        'assets': count($root/AssetMasterData/Asset),
        'shareClasses': count($fund/SingleFund/ShareClasses/ShareClass),
        'topPct': max($positions/TotalPercentage/number()),
        'contentDate': string($root/ControlData/ContentDate)
    }
};

(: Renders one Markdown row: label + one cell per column value. :)
declare function local:row($label as xs:string, $cells as xs:string*) as xs:string {
    '| ' || $label || ' | ' || string-join($cells, ' | ') || ' |'
};

let $this := local:metrics(/FundsXML4)
let $otherDoc := local:try-doc($comparisonFile)

return
    if (exists($otherDoc)) then
        (: -------- Two-fund mode: active document vs. $comparisonFile -------- :)
        let $other := local:metrics($otherDoc/FundsXML4)
        return string-join((
            '# Fund Comparison',
            '',
            local:row('Metric', ('This document', 'Comparison file')),
            '|---|---|---|',
            local:row('Fund', ($this?name, $other?name)),
            local:row('ISIN', ($this?isin, $other?isin)),
            local:row('Content date', ($this?contentDate, $other?contentDate)),
            local:row('Currency', ($this?currency, $other?currency)),
            local:row('Net asset value',
                (format-number($this?nav, '#,##0') || ' ' || $this?currency,
                 format-number($other?nav, '#,##0') || ' ' || $other?currency)),
            local:row('Positions', (string($this?positions), string($other?positions))),
            local:row('Position currencies',
                (string($this?currencies), string($other?currencies))),
            local:row('Assets in master data',
                (string($this?assets), string($other?assets))),
            local:row('Share classes',
                (string($this?shareClasses), string($other?shareClasses))),
            local:row('Largest position',
                (format-number($this?topPct, '0.00') || '%',
                 format-number($other?topPct, '0.00') || '%'))), $nl)
    else
        (: -------- Fallback: share classes of the active document ------------ :)
        let $classes := /FundsXML4/Funds/Fund[1]/SingleFund/ShareClasses/ShareClass
        return string-join((
            '# Share Class Comparison: ' || $this?name,
            '',
            '_Set $comparisonFile to an absolute file URI to compare two funds via doc()._',
            '',
            local:row('Metric', $classes ! string(Identifiers/ISIN)),
            '|---|' || string-join($classes ! '---|', ''),
            local:row('Name', $classes ! string(Names/OfficialName)),
            local:row('Currency', $classes ! string(Currency)),
            local:row('Type', $classes ! string(ShareClassType/Code)),
            local:row('NAV price', $classes !
                format-number(number(Prices/Price[1]/NavPrice), '#,##0.0000')),
            local:row('NAV date', $classes ! string(Prices/Price[1]/NavDate)),
            local:row('Shares outstanding', $classes !
                format-number(number(TotalAssetValues/TotalAssetValue[1]/SharesOutstanding), '#,##0')),
            local:row('Total net assets', $classes !
                format-number(number(TotalAssetValues/TotalAssetValue[1]
                                     /TotalNetAssetValue/Amount[1]), '#,##0'))), $nl)
