xquery version "3.1";

(:~
 : ASCII KPI Dashboard
 :
 : Purpose: Render a complete fund dashboard as plain text — header box,
 : KPI summary, top-5 table and asset-type allocation bars — combining fund
 : master data, portfolio positions and the asset master data in one report.
 :
 : Techniques: text output, padding/repeat helpers, joins, fn:sort,
 : format-number, bar-chart rendering.
 :
 : @author FreeXmlToolkit Examples
 : @version 1.0
 :)

declare namespace output = "http://www.w3.org/2010/xslt-xquery-serialization";
declare option output:method "text";

declare variable $nl := codepoints-to-string(10);
declare variable $width := 76;

declare function local:repeat($char as xs:string, $n as xs:integer) as xs:string {
    string-join((1 to max(($n, 0))) ! $char, '')
};

declare function local:rpad($v as xs:anyAtomicType?, $w as xs:integer) as xs:string {
    substring(string($v) || local:repeat(' ', $w), 1, $w)
};

declare function local:lpad($v as xs:anyAtomicType?, $w as xs:integer) as xs:string {
    let $s := local:repeat(' ', $w) || string($v)
    return substring($s, string-length($s) - $w + 1)
};

declare function local:boxline($text as xs:string) as xs:string {
    '| ' || local:rpad($text, $width - 4) || ' |'
};

let $root := /FundsXML4
let $fund := $root/Funds/Fund[1]
let $fundCcy := string($fund/Currency)
let $assets := $root/AssetMasterData/Asset
let $positions := $fund/FundDynamicData/Portfolios/Portfolio/Positions/Position
let $valueOf := function($p as element()*) as xs:double {
        sum($p/TotalValue/Amount[@ccy = $fundCcy]/number())
    }
let $typeOf := function($p as element()) as xs:string {
        string(($assets[UniqueID = $p/UniqueID]/AssetType, 'N/A')[1])
    }
let $nav := number($fund/FundDynamicData/TotalAssetValues/TotalAssetValue[1]
                        /TotalNetAssetValue/Amount[1])
let $border := '+' || local:repeat('-', $width - 2) || '+'

let $headerBox := (
    $border,
    local:boxline(string($fund/Names/OfficialName)),
    local:boxline('ISIN ' || $fund/Identifiers/ISIN || '  |  ' || $fundCcy
        || '  |  as of ' || $root/ControlData/ContentDate),
    $border)

let $kpis := (
    '',
    'KPIs',
    local:repeat('-', $width),
    local:rpad('Net asset value:', 22) || local:lpad(format-number($nav, '#,##0.00') || ' ' || $fundCcy, 30),
    local:rpad('Positions:', 22) || local:lpad(count($positions), 30),
    local:rpad('Currencies:', 22)
        || local:lpad(string-join(sort(distinct-values($positions/Currency)), ', '), 30),
    local:rpad('Share classes:', 22)
        || local:lpad(count($fund/SingleFund/ShareClasses/ShareClass), 30),
    local:rpad('Transactions:', 22)
        || local:lpad(count($fund/FundDynamicData/Portfolios/Portfolio/Transactions/Transaction), 30))

let $top5 := (
    '',
    'Top 5 positions',
    local:repeat('-', $width),
    for $pos in subsequence(sort($positions, (),
            function($p) { -number($p/TotalValue/Amount[@ccy = $fundCcy][1]) }), 1, 5)
    let $asset := $assets[UniqueID = $pos/UniqueID][1]
    return local:rpad(substring(($asset/Name, $pos/UniqueID)[1], 1, 36), 38)
        || local:rpad($typeOf($pos), 5)
        || local:lpad(format-number(number($pos/TotalValue/Amount[@ccy = $fundCcy][1]), '#,##0'), 22)
        || local:lpad(format-number(number($pos/TotalPercentage), '0.00') || '%', 10))

let $allocation := (
    '',
    'Allocation by asset type',
    local:repeat('-', $width),
    let $total := $valueOf($positions)
    for $type in sort(distinct-values($positions ! $typeOf(.)), (),
            function($t) { -$valueOf($positions[$typeOf(.) = $t]) })
    let $pct := $valueOf($positions[$typeOf(.) = $type]) div $total * 100
    return local:rpad($type, 6)
        || local:rpad(local:repeat('#', xs:integer(round(abs($pct) div 2))), 52)
        || local:lpad(format-number($pct, '0.0') || '%', 8))

return string-join(($headerBox, $kpis, $top5, $allocation), $nl)
