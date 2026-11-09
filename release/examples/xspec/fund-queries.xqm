xquery version "3.1";

(:~
 : fund-queries.xqm — a small XQuery library module under test (03-xquery-module.xspec).
 :
 :   q:total-nav($fund)            the fund's official total net asset value
 :   q:positions-over($fund, $pct) ISINs (or UniqueIDs) of positions above $pct %, largest first
 :   q:currencies($doc)            the distinct position currencies, sorted
 :)
module namespace q = "urn:fxt:examples:fund-queries";

declare function q:total-nav($fund as element(Fund)) as xs:decimal {
    xs:decimal($fund/FundDynamicData/TotalAssetValues/TotalAssetValue[1]/TotalNetAssetValue/Amount[1])
};

declare function q:positions-over($fund as element(Fund), $pct as xs:decimal) as xs:string* {
    for $position in $fund//Positions/Position
    let $weight := xs:decimal($position/TotalPercentage)
    where abs($weight) gt $pct
    order by $weight descending
    return string(($position/Identifiers/ISIN, $position/UniqueID)[1])
};

declare function q:currencies($doc as node()) as xs:string* {
    sort(distinct-values($doc//Position/Currency ! string()))
};
