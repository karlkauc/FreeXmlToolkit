xquery version "3.1";

(:~
 : Portfolio NAV Reconciliation Check
 :
 : Purpose: Verify that the sum of all portfolio position values equals the fund's NAV.
 : This is a critical data quality check for investment fund reporting.
 :
 : Tolerance: 0.1% variance allowed due to rounding differences.
 :
 : Usage in FreeXmlToolkit: Execute this XQuery against FundsXML files.
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

(: Helper function to calculate variance percentage :)
declare function local:calc-variance($expected as xs:decimal, $actual as xs:decimal) as xs:decimal {
    if ($expected = 0) then 0
    else round((($actual - $expected) div $expected) * 10000) div 100
};

(: Helper function to determine status based on variance :)
declare function local:get-status($variance as xs:decimal) as xs:string {
    if (abs($variance) <= 0.1) then "PASS"
    else if (abs($variance) <= 1.0) then "WARNING"
    else "FAIL"
};

(: Helper function to get status color :)
declare function local:get-status-color($status as xs:string) as xs:string {
    switch ($status)
        case "PASS" return "#28a745"
        case "WARNING" return "#ffc107"
        default return "#dc3545"
};

(: Main processing :)
let $doc := /
let $funds := $doc//Fund

return
<html>
    <head>
        <title>Portfolio NAV Reconciliation Report</title>
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
            .variance {{ text-align: right; }}
            .positive {{ color: #28a745; }}
            .negative {{ color: #dc3545; }}
            .timestamp {{ color: #6c757d; font-size: 12px; margin-top: 20px; }}
        </style>
    </head>
    <body>
        <div class="container">
            <h1>Portfolio NAV Reconciliation Report</h1>

            <div class="summary-card">
                <h3>Document Information</h3>
                <p><strong>Document ID:</strong> {$doc//ControlData/UniqueDocumentID/text()}</p>
                <p><strong>Content Date:</strong> {$doc//ControlData/ContentDate/text()}</p>
                <p><strong>Generated:</strong> {$doc//ControlData/DocumentGenerated/text()}</p>
            </div>

            <div class="summary-card">
                <h3>Summary Statistics</h3>
                <div class="summary-stats">
                    <div class="stat">
                        <div class="stat-value">{count($funds)}</div>
                        <div class="stat-label">Total Funds</div>
                    </div>
                    <div class="stat">
                        <div class="stat-value">{count($funds[let $v := local:calc-variance(
                            xs:decimal(FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount),
                            sum(FundDynamicData/Portfolios/Portfolio/Positions/Position/TotalValue/Amount/xs:decimal(.))
                        ) return abs($v) <= 0.1])}</div>
                        <div class="stat-label">Passed</div>
                    </div>
                    <div class="stat">
                        <div class="stat-value">{count($funds[let $v := local:calc-variance(
                            xs:decimal(FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount),
                            sum(FundDynamicData/Portfolios/Portfolio/Positions/Position/TotalValue/Amount/xs:decimal(.))
                        ) return abs($v) > 0.1 and abs($v) <= 1.0])}</div>
                        <div class="stat-label">Warnings</div>
                    </div>
                    <div class="stat">
                        <div class="stat-value">{count($funds[let $v := local:calc-variance(
                            xs:decimal(FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount),
                            sum(FundDynamicData/Portfolios/Portfolio/Positions/Position/TotalValue/Amount/xs:decimal(.))
                        ) return abs($v) > 1.0])}</div>
                        <div class="stat-label">Failed</div>
                    </div>
                </div>
            </div>

            <h2>Reconciliation Details</h2>
            <table>
                <thead>
                    <tr>
                        <th>Fund Name</th>
                        <th>NAV Date</th>
                        <th class="amount">Reported NAV</th>
                        <th class="amount">Calculated Sum</th>
                        <th class="variance">Variance</th>
                        <th>Status</th>
                    </tr>
                </thead>
                <tbody>
                {
                    for $fund in $funds
                    let $fundName := $fund/Names/OfficialName/text()
                    let $navDate := $fund/FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate/text()
                    let $reportedNav := xs:decimal($fund/FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount)
                    let $positions := $fund/FundDynamicData/Portfolios/Portfolio/Positions/Position
                    let $calculatedSum := sum($positions/TotalValue/Amount/xs:decimal(.))
                    let $variance := local:calc-variance($reportedNav, $calculatedSum)
                    let $status := local:get-status($variance)
                    let $statusColor := local:get-status-color($status)
                    let $currency := $fund/Currency/text()
                    return
                    <tr>
                        <td>{$fundName}</td>
                        <td>{$navDate}</td>
                        <td class="amount">{local:format-amount($reportedNav)} {$currency}</td>
                        <td class="amount">{local:format-amount($calculatedSum)} {$currency}</td>
                        <td class="variance {if ($variance >= 0) then 'positive' else 'negative'}">{$variance}%</td>
                        <td><span class="status" style="background-color: {$statusColor}">{$status}</span></td>
                    </tr>
                }
                </tbody>
            </table>

            <h2>Position Details per Fund</h2>
            {
                for $fund in $funds
                let $fundName := $fund/Names/OfficialName/text()
                let $positions := $fund/FundDynamicData/Portfolios/Portfolio/Positions/Position
                return
                <div class="summary-card">
                    <h3>{$fundName}</h3>
                    <p><strong>Total Positions:</strong> {count($positions)}</p>
                    <table>
                        <thead>
                            <tr>
                                <th>Position ID</th>
                                <th>ISIN</th>
                                <th class="amount">Value</th>
                                <th class="amount">Percentage</th>
                            </tr>
                        </thead>
                        <tbody>
                        {
                            for $pos in $positions[position() <= 10]
                            return
                            <tr>
                                <td>{$pos/UniqueID/text()}</td>
                                <td>{($pos/Identifiers/ISIN/text(), $pos/Identifiers/OtherID/text())[1]}</td>
                                <td class="amount">{local:format-amount(xs:decimal($pos/TotalValue/Amount))}</td>
                                <td class="amount">{$pos/TotalPercentage/text()}%</td>
                            </tr>
                        }
                        {if (count($positions) > 10) then
                            <tr><td colspan="4" style="text-align: center; color: #6c757d;">
                                ... and {count($positions) - 10} more positions
                            </td></tr>
                        else ()}
                        </tbody>
                    </table>
                </div>
            }

            <p class="timestamp">Report generated: {current-dateTime()}</p>
        </div>
    </body>
</html>
