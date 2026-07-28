xquery version "3.1";

(:~
 : JSON Fund Export
 :
 : Purpose: Export the fund, its share classes and all positions (asset-master
 : join included) as one JSON document — the XQuery counterpart of the bundled
 : FundsXML_to_JSON.xslt stylesheet.
 :
 : Techniques: XQuery 3.1 maps/arrays, fn:serialize with the JSON output
 : method (returned as a string so the query works in every host, regardless
 : of the host's own serializer settings).
 :
 : @author FreeXmlToolkit Examples
 : @version 1.0
 :)

declare namespace output = "http://www.w3.org/2010/xslt-xquery-serialization";
declare option output:method "text";

let $root := /FundsXML4
let $fund := $root/Funds/Fund[1]
let $fundCcy := string($fund/Currency)
let $assets := $root/AssetMasterData/Asset
let $positions := $fund/FundDynamicData/Portfolios/Portfolio/Positions/Position

let $export := map {
    'document': map {
        'id': string($root/ControlData/UniqueDocumentID),
        'contentDate': string($root/ControlData/ContentDate),
        'supplier': string($root/ControlData/DataSupplier/Name)
    },
    'fund': map {
        'name': string($fund/Names/OfficialName),
        'isin': string($fund/Identifiers/ISIN),
        'currency': $fundCcy,
        'netAssetValue': number($fund/FundDynamicData/TotalAssetValues
                                     /TotalAssetValue[1]/TotalNetAssetValue/Amount[1])
    },
    'shareClasses': array {
        for $sc in $fund/SingleFund/ShareClasses/ShareClass
        return map {
            'name': string($sc/Names/OfficialName),
            'isin': string($sc/Identifiers/ISIN),
            'currency': string($sc/Currency),
            'navPrice': number($sc/Prices/Price[1]/NavPrice)
        }
    },
    'positions': array {
        for $pos in sort($positions, (),
                function($p) { -number($p/TotalValue/Amount[@ccy = $fundCcy][1]) })
        let $asset := $assets[UniqueID = $pos/UniqueID][1]
        return map {
            'id': string($pos/UniqueID),
            'name': string(($asset/Name, $pos/UniqueID)[1]),
            'isin': string($asset/Identifiers/ISIN),
            'assetType': string($asset/AssetType),
            'country': string($asset/Country),
            'currency': string($pos/Currency),
            'value': number($pos/TotalValue/Amount[@ccy = $fundCcy][1]),
            'percent': number($pos/TotalPercentage)
        }
    }
}

return serialize($export, map { 'method': 'json', 'indent': true() })
