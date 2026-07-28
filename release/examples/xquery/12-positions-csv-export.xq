xquery version "3.1";

(:~
 : Position Export as CSV
 :
 : Purpose: Export every portfolio position as a CSV row, joining the position
 : with its AssetMasterData entry (Position/UniqueID = Asset/UniqueID) for
 : ISIN, asset name, type and country.
 :
 : Techniques: text output method, CSV escaping helper, FLWOR with order by,
 : string-join over a row sequence.
 :
 : @author FreeXmlToolkit Examples
 : @version 1.0
 :)

declare namespace output = "http://www.w3.org/2010/xslt-xquery-serialization";
declare option output:method "text";

declare variable $nl := codepoints-to-string(10);

(: Quote a CSV field when it contains a comma, quote or line break. :)
declare function local:csv($v as xs:anyAtomicType?) as xs:string {
    let $s := string($v)
    return if (matches($s, '[",\r\n]'))
           then '"' || replace($s, '"', '""') || '"'
           else $s
};

let $root := /FundsXML4
let $fund := $root/Funds/Fund[1]
let $fundCcy := string($fund/Currency)
let $assets := $root/AssetMasterData/Asset

let $header := string-join((
    'FundISIN', 'FundName', 'PositionID', 'ISIN', 'AssetName', 'AssetType',
    'Country', 'PositionCcy', 'TotalValue' || $fundCcy, 'Percent'), ',')

let $rows :=
    for $pos in $fund/FundDynamicData/Portfolios/Portfolio/Positions/Position
    let $asset := $assets[UniqueID = $pos/UniqueID][1]
    let $value := number($pos/TotalValue/Amount[@ccy = $fundCcy][1])
    order by $value descending
    return string-join((
        local:csv($fund/Identifiers/ISIN),
        local:csv($fund/Names/OfficialName),
        local:csv($pos/UniqueID),
        local:csv($asset/Identifiers/ISIN),
        local:csv($asset/Name),
        local:csv($asset/AssetType),
        local:csv($asset/Country),
        local:csv($pos/Currency),
        string($pos/TotalValue/Amount[@ccy = $fundCcy][1]),
        string($pos/TotalPercentage)), ',')

return string-join(($header, $rows), $nl)
