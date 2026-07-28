xquery version "3.1";

(:~
 : Cross-Tab: Asset Type x Currency (Pivot Table)
 :
 : Purpose: Pivot the portfolio into a two-dimensional table — asset types as
 : rows (joined from AssetMasterData), position currencies as columns, position
 : counts and values (in fund currency) as cells, plus row/column totals.
 :
 : Techniques: two-dimensional grouping, dynamic column list, fixed-width
 : ASCII rendering with padding helpers.
 :
 : @author FreeXmlToolkit Examples
 : @version 1.0
 :)

declare namespace output = "http://www.w3.org/2010/xslt-xquery-serialization";
declare option output:method "text";

declare variable $nl := codepoints-to-string(10);

declare function local:repeat($char as xs:string, $n as xs:integer) as xs:string {
    string-join((1 to max(($n, 0))) ! $char, '')
};

declare function local:lpad($v as xs:anyAtomicType?, $w as xs:integer) as xs:string {
    let $s := local:repeat(' ', $w) || string($v)
    return substring($s, string-length($s) - $w + 1)
};

declare function local:rpad($v as xs:anyAtomicType?, $w as xs:integer) as xs:string {
    substring(string($v) || local:repeat(' ', $w), 1, $w)
};

let $root := /FundsXML4
let $fundCcy := string($root/Funds/Fund[1]/Currency)
let $assets := $root/AssetMasterData/Asset
let $positions := $root/Funds/Fund/FundDynamicData/Portfolios/Portfolio/Positions/Position
let $typeOf := function($p as element()) as xs:string {
        string(($assets[UniqueID = $p/UniqueID]/AssetType, 'N/A')[1])
    }
let $valueOf := function($p as element()*) as xs:double {
        sum($p/TotalValue/Amount[@ccy = $fundCcy]/number())
    }
let $mio := function($v as xs:double) as xs:string {
        format-number($v div 1000000, '#,##0.0')
    }

let $ccys := sort(distinct-values($positions/Currency))
let $types := sort(distinct-values($positions ! $typeOf(.)), (),
        function($t) { -$valueOf($positions[$typeOf(.) = $t]) })
let $colWidth := 14

let $header := local:rpad('Type', 6)
    || string-join($ccys ! local:lpad(. || ' (m)', $colWidth), '')
    || local:lpad('Total (m)', $colWidth)
let $separator := local:repeat('-', string-length($header))

let $rows :=
    for $type in $types
    let $ofType := $positions[$typeOf(.) = $type]
    return local:rpad($type, 6)
        || string-join(
               for $ccy in $ccys
               let $cell := $ofType[Currency = $ccy]
               return local:lpad(
                   if (exists($cell)) then $mio($valueOf($cell)) else '-',
                   $colWidth), '')
        || local:lpad($mio($valueOf($ofType)), $colWidth)

let $totals := local:rpad('Total', 6)
    || string-join(
           for $ccy in $ccys
           return local:lpad($mio($valueOf($positions[Currency = $ccy])), $colWidth), '')
    || local:lpad($mio($valueOf($positions)), $colWidth)

return string-join((
    'Portfolio pivot: asset type x currency, values in millions ' || $fundCcy,
    $separator, $header, $separator, $rows, $separator, $totals), $nl)
