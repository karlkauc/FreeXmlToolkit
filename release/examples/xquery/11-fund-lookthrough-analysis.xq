xquery version "3.1";

(:~
 : Fund Look-Through Analysis
 :
 : Purpose: Identify fund-of-funds look-through candidates. Positions whose asset
 : is of type SC (a fund / share-class investment) are not "final" holdings - to
 : compute true exposure you would substitute the target fund's own portfolio,
 : weighted by this position's weight. A single FundsXML file rarely contains the
 : nested funds, so this report lists the look-through candidates and the residual
 : directly-held portion per fund.
 :
 : Detection: a position is a look-through candidate when its UniqueID matches an
 : AssetMasterData asset with AssetType = 'SC', or when the position itself
 : carries a ShareClass detail element.
 :
 : Usage in FreeXmlToolkit: Execute this XQuery against FundsXML files.
 : Inspired by look-through.xq in https://github.com/fundsxml/examples
 :
 : @author FreeXmlToolkit Examples
 : @version 1.0
 :)

declare namespace output = "http://www.w3.org/2010/xslt-xquery-serialization";
declare option output:method "html";
declare option output:html-version "5";

(: Helper function to format numbers with thousand separators :)
declare function local:format-amount($amount as xs:decimal?) as xs:string {
    if (empty($amount)) then "N/A"
    else format-number($amount, "#,##0.00")
};

(: Helper function to format a 0-100 weight as a percentage string :)
declare function local:format-weight($weight as xs:decimal?) as xs:string {
    if (empty($weight)) then "N/A"
    else format-number($weight, "#,##0.00") || " %"
};

(: True if the position is a fund / share-class investment (look-through candidate) :)
declare function local:is-fund-investment($pos as element(Position), $assets as element(Asset)*) as xs:boolean {
    exists($assets[UniqueID = $pos/UniqueID and AssetType = 'SC'])
    or exists($pos/ShareClass)
};

(: Main processing - FundsXML 4.x has no XML namespace, query bare element names :)
let $doc := /FundsXML4
let $funds := $doc//Funds/Fund
let $assets := $doc//AssetMasterData/Asset

return
<html>
    <head>
        <title>Fund Look-Through Analysis</title>
        <style>
            body {{ font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                   margin: 20px; background: #f8f9fa; color: #212529; }}
            .container {{ max-width: 1200px; margin: 0 auto; }}
            h1 {{ color: #0d6efd; border-bottom: 3px solid #0d6efd; padding-bottom: 10px; }}
            h2 {{ color: #495057; margin-top: 30px; }}
            .summary-card {{ background: white; border-radius: 8px; padding: 20px;
                           margin: 20px 0; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }}
            .summary-stats {{ display: flex; gap: 20px; flex-wrap: wrap; }}
            .stat {{ flex: 1; min-width: 150px; text-align: center; padding: 15px;
                    background: #e9ecef; border-radius: 6px; }}
            .stat-value {{ font-size: 24px; font-weight: bold; color: #0d6efd; }}
            .stat-label {{ font-size: 12px; color: #6c757d; margin-top: 5px; }}
            table {{ width: 100%; border-collapse: collapse; background: white;
                    border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }}
            th {{ background: #0d6efd; color: white; padding: 12px; text-align: left; }}
            td {{ padding: 12px; border-bottom: 1px solid #dee2e6; }}
            tr:hover {{ background: #f8f9fa; }}
            .status {{ padding: 4px 12px; border-radius: 20px; font-size: 12px;
                      font-weight: bold; color: white; display: inline-block; }}
            .amount {{ text-align: right; font-family: monospace; }}
            .note {{ color: #6c757d; font-size: 13px; }}
        </style>
    </head>
    <body>
        <div class="container">
            <h1>Fund Look-Through Analysis</h1>

            <div class="summary-card">
                <h3>Document Information</h3>
                <p><strong>Document ID:</strong> {$doc//ControlData/UniqueDocumentID/text()}</p>
                <p><strong>Content Date:</strong> {$doc//ControlData/ContentDate/text()}</p>
                <p><strong>Generated:</strong> {$doc//ControlData/DocumentGenerated/text()}</p>
            </div>

            {
            for $fund in $funds
            let $fundName := $fund/Names/OfficialName/text()
            let $ccy := $fund/Currency/text()
            let $positions := $fund/FundDynamicData/Portfolios/Portfolio/Positions/Position
            let $candidates := $positions[local:is-fund-investment(., $assets)]
            let $ltWeight := sum($candidates/TotalPercentage/xs:decimal(.))
            let $directWeight := sum($positions/TotalPercentage/xs:decimal(.)) - $ltWeight
            return
            <div class="summary-card">
                <h3>{$fundName}</h3>
                <div class="summary-stats">
                    <div class="stat">
                        <div class="stat-value">{count($positions)}</div>
                        <div class="stat-label">Total Positions</div>
                    </div>
                    <div class="stat">
                        <div class="stat-value">{count($candidates)}</div>
                        <div class="stat-label">Fund Investments</div>
                    </div>
                    <div class="stat">
                        <div class="stat-value">{local:format-weight($ltWeight)}</div>
                        <div class="stat-label">Look-Through Weight</div>
                    </div>
                    <div class="stat">
                        <div class="stat-value">{local:format-weight($directWeight)}</div>
                        <div class="stat-label">Directly Held Weight</div>
                    </div>
                </div>

                {
                if (empty($candidates)) then
                    <p><span class="status" style="background-color: #28a745">NO LOOK-THROUGH REQUIRED</span>
                       <span class="note"> - all positions are direct holdings.</span></p>
                else
                    <div>
                        <h2>Look-Through Candidates</h2>
                        <p class="note">For true exposure, substitute each target fund's own portfolio,
                           scaled by the position weight below.</p>
                        <table>
                            <thead>
                                <tr>
                                    <th>ISIN</th>
                                    <th>Name</th>
                                    <th>Issuer</th>
                                    <th>Currency</th>
                                    <th class="amount">Value ({$ccy})</th>
                                    <th class="amount">Weight</th>
                                </tr>
                            </thead>
                            <tbody>
                            {
                                for $pos in $candidates
                                let $asset := $assets[UniqueID = $pos/UniqueID][1]
                                let $value := ($pos/TotalValue/Amount[@ccy = $ccy], $pos/TotalValue/Amount)[1]
                                order by number($pos/TotalPercentage) descending
                                return
                                <tr>
                                    <td>{(($pos/Identifiers/ISIN, $asset/Identifiers/ISIN)/text(), '-')[1]}</td>
                                    <td>{(($asset/Name, $pos/Identifiers/OtherID)/text(), '-')[1]}</td>
                                    <td>{($asset/AssetDetails/ShareClass/Issuer/Name/text(), '-')[1]}</td>
                                    <td>{$pos/Currency/text()}</td>
                                    <td class="amount">{local:format-amount($value/xs:decimal(.))}</td>
                                    <td class="amount">{local:format-weight($pos/TotalPercentage/xs:decimal(.))}</td>
                                </tr>
                            }
                            </tbody>
                        </table>
                    </div>
                }
            </div>
            }
        </div>
    </body>
</html>
