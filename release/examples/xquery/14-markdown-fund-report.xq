xquery version "3.1";

(:~
 : Markdown Fund Report
 :
 : Purpose: Generate a complete, multi-section fund report in Markdown —
 : overview, share classes, top-10 positions (asset join) and currency
 : exposure — ready to paste into any wiki, README or issue tracker.
 :
 : Techniques: text output, Markdown pipe tables, FLWOR with group-by-style
 : aggregation via distinct-values, joins to AssetMasterData.
 :
 : @author FreeXmlToolkit Examples
 : @version 1.0
 :)

declare namespace output = "http://www.w3.org/2010/xslt-xquery-serialization";
declare option output:method "text";

declare variable $nl := codepoints-to-string(10);

let $root := /FundsXML4
let $fund := $root/Funds/Fund[1]
let $fundCcy := string($fund/Currency)
let $assets := $root/AssetMasterData/Asset
let $positions := $fund/FundDynamicData/Portfolios/Portfolio/Positions/Position
let $valueOf := function($p as element()*) as xs:double {
        sum($p/TotalValue/Amount[@ccy = $fundCcy]/number())
    }
let $nav := number($fund/FundDynamicData/TotalAssetValues/TotalAssetValue[1]
                        /TotalNetAssetValue/Amount[1])

let $overview := (
    '# Fund Report: ' || $fund/Names/OfficialName,
    '',
    '- **ISIN:** ' || $fund/Identifiers/ISIN,
    '- **Currency:** ' || $fundCcy,
    '- **Content date:** ' || $root/ControlData/ContentDate,
    '- **Data supplier:** ' || $root/ControlData/DataSupplier/Name,
    '- **Net asset value:** ' || format-number($nav, '#,##0.00') || ' ' || $fundCcy,
    '- **Positions:** ' || count($positions))

let $shareClasses := (
    '',
    '## Share Classes',
    '',
    '| Name | ISIN | Ccy | NAV Price | Shares Outstanding |',
    '|---|---|---|---:|---:|',
    for $sc in $fund/SingleFund/ShareClasses/ShareClass
    return '| ' || string-join((
        string($sc/Names/OfficialName),
        string($sc/Identifiers/ISIN),
        string($sc/Currency),
        format-number(number($sc/Prices/Price[1]/NavPrice), '#,##0.0000'),
        format-number(number($sc/TotalAssetValues/TotalAssetValue[1]/SharesOutstanding), '#,##0')
    ), ' | ') || ' |')

let $topPositions := (
    '',
    '## Top 10 Positions',
    '',
    '| # | Asset | Type | Value ' || $fundCcy || ' | Share |',
    '|---:|---|---|---:|---:|',
    let $top := subsequence(sort($positions, (),
            function($p) { -number($p/TotalValue/Amount[@ccy = $fundCcy][1]) }), 1, 10)
    for $i in 1 to count($top)
    let $pos := $top[$i]
    let $asset := $assets[UniqueID = $pos/UniqueID][1]
    return '| ' || $i || ' | ' || ($asset/Name, $pos/UniqueID)[1]
        || ' | ' || ($asset/AssetType, '?')[1]
        || ' | ' || format-number(number($pos/TotalValue/Amount[@ccy = $fundCcy][1]), '#,##0')
        || ' | ' || format-number(number($pos/TotalPercentage), '0.00') || '% |')

let $currencyExposure := (
    '',
    '## Currency Exposure',
    '',
    '| Currency | Positions | Value ' || $fundCcy || ' | Share |',
    '|---|---:|---:|---:|',
    let $total := $valueOf($positions)
    for $ccy in sort(distinct-values($positions/Currency), (),
            function($c) { -$valueOf($positions[Currency = $c]) })
    let $group := $positions[Currency = $ccy]
    return '| ' || $ccy || ' | ' || count($group)
        || ' | ' || format-number($valueOf($group), '#,##0')
        || ' | ' || format-number($valueOf($group) div $total * 100, '0.00') || '% |')

let $footer := (
    '',
    '---',
    '_Generated from document ' || $root/ControlData/UniqueDocumentID
        || ' by FreeXmlToolkit._')

return string-join(($overview, $shareClasses, $topPositions, $currencyExposure, $footer), $nl)
