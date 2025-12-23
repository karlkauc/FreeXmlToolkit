xquery version "3.1";

(:~
 : Percentage Sum Validation
 :
 : Purpose: Validate that TotalPercentage values sum to 100% per portfolio.
 : This ensures positions correctly represent 100% of the fund.
 :
 : Tolerance: 0.5% variance allowed (99.5% - 100.5%)
 :
 : Checks:
 : - Sum of all position TotalPercentage = 100%
 : - No negative percentages
 : - No percentages > 100% (for individual positions)
 :
 : @author FreeXmlToolkit Examples
 : @version 1.0
 :)

declare namespace output = "http://www.w3.org/2010/xslt-xquery-serialization";
declare option output:method "html";
declare option output:html-version "5";

(: Tolerance settings :)
declare variable $local:MIN_VALID_PCT := 99.5;
declare variable $local:MAX_VALID_PCT := 100.5;

(: Helper function to determine status :)
declare function local:get-status($sum as xs:decimal) as xs:string {
    if ($sum >= $local:MIN_VALID_PCT and $sum <= $local:MAX_VALID_PCT) then "PASS"
    else if ($sum >= 95 and $sum <= 105) then "WARNING"
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
        <title>Percentage Sum Validation Report</title>
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
            .stat-value {{ font-size: 24px; font-weight: bold; }}
            .stat-label {{ font-size: 12px; color: #6c757d; margin-top: 5px; }}
            table {{ width: 100%; border-collapse: collapse; background: white;
                    border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }}
            th {{ background: #0d6efd; color: white; padding: 12px; text-align: left; }}
            td {{ padding: 12px; border-bottom: 1px solid #dee2e6; }}
            tr:hover {{ background: #f8f9fa; }}
            .status {{ padding: 4px 12px; border-radius: 20px; font-size: 12px;
                      font-weight: bold; color: white; display: inline-block; }}
            .amount {{ text-align: right; font-family: monospace; }}
            .progress-bar {{ background: #e9ecef; border-radius: 10px; height: 20px; overflow: hidden; }}
            .progress-fill {{ height: 100%; transition: width 0.3s; }}
            .timestamp {{ color: #6c757d; font-size: 12px; margin-top: 20px; }}
            .anomaly {{ background: #fff3cd; padding: 2px 6px; border-radius: 4px; }}
            .info-box {{ background: #cff4fc; border-left: 4px solid #0dcaf0;
                        padding: 15px; margin: 20px 0; border-radius: 4px; }}
        </style>
    </head>
    <body>
        <div class="container">
            <h1>Percentage Sum Validation Report</h1>

            <div class="info-box">
                <strong>Validation Rule:</strong> The sum of all position TotalPercentage values
                should equal 100% (tolerance: {$local:MIN_VALID_PCT}% - {$local:MAX_VALID_PCT}%)
            </div>

            {
                for $fund in $funds
                let $fundName := $fund/Names/OfficialName/text()
                let $portfolios := $fund/FundDynamicData/Portfolios/Portfolio

                return
                <div class="summary-card">
                    <h2>Fund: {$fundName}</h2>

                    {
                        for $portfolio in $portfolios
                        let $navDate := $portfolio/NavDate/text()
                        let $positions := $portfolio/Positions/Position
                        let $percentages := $positions/TotalPercentage/xs:decimal(.)
                        let $sum := sum($percentages)
                        let $status := local:get-status($sum)
                        let $statusColor := local:get-status-color($status)
                        let $negativeCount := count($percentages[. < 0])
                        let $overHundredCount := count($percentages[. > 100])
                        let $minPct := min($percentages)
                        let $maxPct := max($percentages)
                        let $avgPct := avg($percentages)

                        return
                        <div>
                            <h3>Portfolio (NAV Date: {$navDate})</h3>

                            <div class="summary-stats">
                                <div class="stat">
                                    <div class="stat-value">{count($positions)}</div>
                                    <div class="stat-label">Positions</div>
                                </div>
                                <div class="stat">
                                    <div class="stat-value" style="color: {$statusColor};">
                                        {round($sum * 100) div 100}%
                                    </div>
                                    <div class="stat-label">Total Sum</div>
                                </div>
                                <div class="stat">
                                    <div class="stat-value">{round($avgPct * 1000) div 1000}%</div>
                                    <div class="stat-label">Avg Per Position</div>
                                </div>
                                <div class="stat">
                                    <div class="stat-value">
                                        <span class="status" style="background-color: {$statusColor};">{$status}</span>
                                    </div>
                                    <div class="stat-label">Status</div>
                                </div>
                            </div>

                            <div style="margin: 20px 0;">
                                <strong>Sum Progress:</strong>
                                <div class="progress-bar" style="margin-top: 5px;">
                                    <div class="progress-fill" style="width: {min(($sum, 100))}%; background-color: {$statusColor};"></div>
                                </div>
                                <div style="display: flex; justify-content: space-between; font-size: 12px; color: #6c757d;">
                                    <span>0%</span>
                                    <span>50%</span>
                                    <span>100%</span>
                                </div>
                            </div>

                            {if ($negativeCount > 0 or $overHundredCount > 0) then
                                <div style="background: #f8d7da; padding: 10px; border-radius: 4px; margin: 10px 0;">
                                    <strong>Anomalies Detected:</strong>
                                    {if ($negativeCount > 0) then
                                        <span class="anomaly"> {$negativeCount} negative percentages</span>
                                    else ()}
                                    {if ($overHundredCount > 0) then
                                        <span class="anomaly"> {$overHundredCount} over 100%</span>
                                    else ()}
                                </div>
                            else ()}

                            <h4>Distribution Statistics</h4>
                            <table>
                                <thead>
                                    <tr>
                                        <th>Metric</th>
                                        <th class="amount">Value</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <tr>
                                        <td>Minimum Percentage</td>
                                        <td class="amount">{round($minPct * 1000) div 1000}%</td>
                                    </tr>
                                    <tr>
                                        <td>Maximum Percentage</td>
                                        <td class="amount">{round($maxPct * 1000) div 1000}%</td>
                                    </tr>
                                    <tr>
                                        <td>Average Percentage</td>
                                        <td class="amount">{round($avgPct * 1000) div 1000}%</td>
                                    </tr>
                                    <tr>
                                        <td>Deviation from 100%</td>
                                        <td class="amount" style="color: {if (abs($sum - 100) <= 0.5) then '#28a745' else '#dc3545'};">
                                            {if ($sum >= 100) then "+" else ""}{round(($sum - 100) * 100) div 100}%
                                        </td>
                                    </tr>
                                </tbody>
                            </table>

                            <h4>Top 10 Positions by Percentage</h4>
                            <table>
                                <thead>
                                    <tr>
                                        <th>#</th>
                                        <th>Position ID</th>
                                        <th>ISIN</th>
                                        <th class="amount">Percentage</th>
                                        <th class="amount">Cumulative</th>
                                    </tr>
                                </thead>
                                <tbody>
                                {
                                    let $sortedPositions :=
                                        for $pos in $positions
                                        order by xs:decimal($pos/TotalPercentage) descending
                                        return $pos

                                    for $pos at $i in $sortedPositions[position() <= 10]
                                    let $pct := xs:decimal($pos/TotalPercentage)
                                    let $cumulative := sum($sortedPositions[position() <= $i]/TotalPercentage/xs:decimal(.))
                                    return
                                    <tr>
                                        <td>{$i}</td>
                                        <td>{$pos/UniqueID/text()}</td>
                                        <td>{($pos/Identifiers/ISIN/text(), $pos/Identifiers/OtherID/text())[1]}</td>
                                        <td class="amount">{round($pct * 1000) div 1000}%</td>
                                        <td class="amount">{round($cumulative * 100) div 100}%</td>
                                    </tr>
                                }
                                </tbody>
                            </table>
                        </div>
                    }
                </div>
            }

            <p class="timestamp">Report generated: {current-dateTime()}</p>
        </div>
    </body>
</html>
