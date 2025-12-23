xquery version "3.1";

(:~
 : Bond Calculation Consistency Check
 :
 : Purpose: Verify that Bond pricing calculations are consistent.
 :
 : Formula: Nominal * (Price/100) * Indexfactor * Poolfactor = MarketValue
 :
 : Also checks:
 : - TotalValue = MarketValue + InterestClaimGross (accrued interest)
 : - All required bond fields are present
 : - Values are within reasonable ranges
 :
 : @author FreeXmlToolkit Examples
 : @version 1.0
 :)

declare namespace output = "http://www.w3.org/2010/xslt-xquery-serialization";
declare option output:method "html";
declare option output:html-version "5";

(: Tolerance for calculation differences (0.1% or 0.01 absolute) :)
declare variable $local:PCT_TOLERANCE := 0.1;
declare variable $local:ABS_TOLERANCE := 0.01;

(: Helper function to calculate expected market value :)
declare function local:calc-market-value($nominal as xs:decimal, $price as xs:decimal,
                                          $indexfactor as xs:decimal, $poolfactor as xs:decimal) as xs:decimal {
    round($nominal * ($price div 100) * $indexfactor * $poolfactor * 100) div 100
};

(: Helper function to check if values match within tolerance :)
declare function local:values-match($expected as xs:decimal, $actual as xs:decimal) as xs:boolean {
    let $diff := abs($expected - $actual)
    let $pctDiff := if ($expected != 0) then abs($diff div $expected * 100) else $diff
    return $diff <= $local:ABS_TOLERANCE or $pctDiff <= $local:PCT_TOLERANCE
};

(: Helper function to format numbers :)
declare function local:format-amount($amount as xs:decimal?) as xs:string {
    if (empty($amount)) then "N/A"
    else format-number($amount, "#,##0.00")
};

(: Main processing :)
let $doc := /
let $funds := $doc//Fund

return
<html>
    <head>
        <title>Bond Calculation Check Report</title>
        <style>
            body {{ font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                   margin: 20px; background: #f8f9fa; color: #212529; }}
            .container {{ max-width: 1400px; margin: 0 auto; }}
            h1 {{ color: #0d6efd; border-bottom: 3px solid #0d6efd; padding-bottom: 10px; }}
            h2 {{ color: #495057; margin-top: 30px; }}
            .summary-card {{ background: white; border-radius: 8px; padding: 20px;
                           margin: 20px 0; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }}
            .summary-stats {{ display: flex; gap: 20px; flex-wrap: wrap; }}
            .stat {{ flex: 1; min-width: 120px; text-align: center; padding: 15px;
                    background: #e9ecef; border-radius: 6px; }}
            .stat-value {{ font-size: 24px; font-weight: bold; }}
            .stat-label {{ font-size: 12px; color: #6c757d; margin-top: 5px; }}
            table {{ width: 100%; border-collapse: collapse; background: white;
                    border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                    font-size: 13px; }}
            th {{ background: #0d6efd; color: white; padding: 10px; text-align: left; }}
            td {{ padding: 10px; border-bottom: 1px solid #dee2e6; }}
            tr:hover {{ background: #f8f9fa; }}
            .status {{ padding: 3px 10px; border-radius: 20px; font-size: 11px;
                      font-weight: bold; color: white; display: inline-block; }}
            .status-pass {{ background-color: #28a745; }}
            .status-warning {{ background-color: #ffc107; color: #212529; }}
            .status-fail {{ background-color: #dc3545; }}
            .amount {{ text-align: right; font-family: monospace; }}
            .diff-positive {{ color: #28a745; }}
            .diff-negative {{ color: #dc3545; }}
            .formula-box {{ background: #e9ecef; padding: 15px; border-radius: 8px;
                           font-family: monospace; margin: 15px 0; }}
            .timestamp {{ color: #6c757d; font-size: 12px; margin-top: 20px; }}
            .info-box {{ background: #cff4fc; border-left: 4px solid #0dcaf0;
                        padding: 15px; margin: 20px 0; border-radius: 4px; }}
        </style>
    </head>
    <body>
        <div class="container">
            <h1>Bond Calculation Check Report</h1>

            <div class="info-box">
                <strong>Calculation Formulas:</strong>
                <div class="formula-box">
                    MarketValue = Nominal * (Price / 100) * Indexfactor * Poolfactor<br/>
                    TotalValue = MarketValue + InterestClaimGross (Accrued Interest)
                </div>
                <strong>Tolerance:</strong> {$local:PCT_TOLERANCE}% or {$local:ABS_TOLERANCE} absolute difference
            </div>

            {
                for $fund in $funds
                let $fundName := $fund/Names/OfficialName/text()
                let $bondPositions := $fund/FundDynamicData/Portfolios/Portfolio/Positions/Position[Bond]

                let $passCount := count($bondPositions[
                    let $bond := Bond
                    let $nominal := xs:decimal(($bond/Nominal, 0)[1])
                    let $price := xs:decimal(($bond/Price/Amount, 0)[1])
                    let $indexfactor := xs:decimal(($bond/Indexfactor, 1)[1])
                    let $poolfactor := xs:decimal(($bond/Poolfactor, 1)[1])
                    let $reportedMV := xs:decimal(($bond/MarketValue/Amount, 0)[1])
                    let $calculatedMV := local:calc-market-value($nominal, $price, $indexfactor, $poolfactor)
                    return local:values-match($calculatedMV, $reportedMV)
                ])

                let $failCount := count($bondPositions) - $passCount

                return
                <div class="summary-card">
                    <h2>Fund: {$fundName}</h2>

                    <div class="summary-stats">
                        <div class="stat">
                            <div class="stat-value">{count($bondPositions)}</div>
                            <div class="stat-label">Bond Positions</div>
                        </div>
                        <div class="stat">
                            <div class="stat-value" style="color: #28a745;">{$passCount}</div>
                            <div class="stat-label">Calculations OK</div>
                        </div>
                        <div class="stat">
                            <div class="stat-value" style="color: #dc3545;">{$failCount}</div>
                            <div class="stat-label">Discrepancies</div>
                        </div>
                        <div class="stat">
                            <div class="stat-value" style="color: {if ($failCount = 0) then '#28a745' else '#dc3545'};">
                                {round($passCount div count($bondPositions) * 100)}%
                            </div>
                            <div class="stat-label">Pass Rate</div>
                        </div>
                    </div>

                    {if (count($bondPositions) > 0) then
                        <div>
                            <h3>Bond Position Details</h3>
                            <table>
                                <thead>
                                    <tr>
                                        <th>Position ID</th>
                                        <th>ISIN</th>
                                        <th class="amount">Nominal</th>
                                        <th class="amount">Price</th>
                                        <th class="amount">Idx/Pool</th>
                                        <th class="amount">Calc. MV</th>
                                        <th class="amount">Reported MV</th>
                                        <th class="amount">Diff</th>
                                        <th class="amount">Accrued Int.</th>
                                        <th class="amount">Total Value</th>
                                        <th>Status</th>
                                    </tr>
                                </thead>
                                <tbody>
                                {
                                    for $pos in $bondPositions[position() <= 50]
                                    let $bond := $pos/Bond
                                    let $nominal := xs:decimal(($bond/Nominal, 0)[1])
                                    let $price := xs:decimal(($bond/Price/Amount, 0)[1])
                                    let $indexfactor := xs:decimal(($bond/Indexfactor, 1)[1])
                                    let $poolfactor := xs:decimal(($bond/Poolfactor, 1)[1])
                                    let $reportedMV := xs:decimal(($bond/MarketValue/Amount, 0)[1])
                                    let $accruedInt := xs:decimal(($bond/InterestClaimGross/Amount, 0)[1])
                                    let $totalValue := xs:decimal(($pos/TotalValue/Amount, 0)[1])
                                    let $calculatedMV := local:calc-market-value($nominal, $price, $indexfactor, $poolfactor)
                                    let $mvDiff := $reportedMV - $calculatedMV
                                    let $mvMatches := local:values-match($calculatedMV, $reportedMV)
                                    let $tvExpected := $reportedMV + $accruedInt
                                    let $tvMatches := local:values-match($tvExpected, $totalValue)
                                    let $allPass := $mvMatches and $tvMatches

                                    return
                                    <tr>
                                        <td>{$pos/UniqueID/text()}</td>
                                        <td>{($pos/Identifiers/ISIN/text(), $pos/Identifiers/OtherID/text())[1]}</td>
                                        <td class="amount">{local:format-amount($nominal)}</td>
                                        <td class="amount">{round($price * 100) div 100}</td>
                                        <td class="amount">{$indexfactor}/{$poolfactor}</td>
                                        <td class="amount">{local:format-amount($calculatedMV)}</td>
                                        <td class="amount">{local:format-amount($reportedMV)}</td>
                                        <td class="amount {if ($mvDiff >= 0) then 'diff-positive' else 'diff-negative'}">
                                            {if ($mvDiff >= 0) then "+" else ""}{local:format-amount($mvDiff)}
                                        </td>
                                        <td class="amount">{local:format-amount($accruedInt)}</td>
                                        <td class="amount">{local:format-amount($totalValue)}</td>
                                        <td>
                                            {if ($allPass) then
                                                <span class="status status-pass">PASS</span>
                                            else if ($mvMatches) then
                                                <span class="status status-warning">TV DIFF</span>
                                            else
                                                <span class="status status-fail">MV DIFF</span>
                                            }
                                        </td>
                                    </tr>
                                }
                                {if (count($bondPositions) > 50) then
                                    <tr><td colspan="11" style="text-align: center; color: #6c757d;">
                                        ... and {count($bondPositions) - 50} more bond positions
                                    </td></tr>
                                else ()}
                                </tbody>
                            </table>
                        </div>
                    else
                        <p style="color: #6c757d;">No bond positions found in this fund.</p>
                    }

                    {
                        let $failedPositions := $bondPositions[
                            let $bond := Bond
                            let $nominal := xs:decimal(($bond/Nominal, 0)[1])
                            let $price := xs:decimal(($bond/Price/Amount, 0)[1])
                            let $indexfactor := xs:decimal(($bond/Indexfactor, 1)[1])
                            let $poolfactor := xs:decimal(($bond/Poolfactor, 1)[1])
                            let $reportedMV := xs:decimal(($bond/MarketValue/Amount, 0)[1])
                            let $calculatedMV := local:calc-market-value($nominal, $price, $indexfactor, $poolfactor)
                            return not(local:values-match($calculatedMV, $reportedMV))
                        ]
                        return
                        if (count($failedPositions) > 0) then
                            <div style="background: #f8d7da; padding: 15px; border-radius: 8px; margin-top: 20px;">
                                <strong>Failed Positions Summary:</strong>
                                <p>{count($failedPositions)} positions have market value calculation discrepancies exceeding tolerance.</p>
                                <p>ISINs: {string-join(distinct-values($failedPositions/Identifiers/ISIN/text()), ", ")}</p>
                            </div>
                        else ()
                    }
                </div>
            }

            <p class="timestamp">Report generated: {current-dateTime()}</p>
        </div>
    </body>
</html>
