xquery version "3.1";

(:~
 : Currency Consistency Validator
 :
 : Purpose: Ensure all positions match the fund's base currency or have valid FX rates.
 : Foreign currency positions without FX rates may indicate data quality issues.
 :
 : Checks:
 : - Position currency matches fund currency
 : - FX rates are present for foreign currency positions
 : - FX rate values are valid (non-zero, reasonable range)
 :
 : @author FreeXmlToolkit Examples
 : @version 1.0
 :)

declare namespace output = "http://www.w3.org/2010/xslt-xquery-serialization";
declare option output:method "html";
declare option output:html-version "5";

(: Helper function to check if FX rate is valid :)
declare function local:is-valid-fx-rate($rate as xs:string?) as xs:boolean {
    if (empty($rate) or $rate = "") then false()
    else
        let $numRate := xs:decimal($rate)
        return $numRate > 0 and $numRate < 1000000
};

(: Main processing :)
let $doc := /
let $funds := $doc//Fund

return
<html>
    <head>
        <title>Currency Consistency Report</title>
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
            .status-pass {{ background-color: #28a745; }}
            .status-warning {{ background-color: #ffc107; color: #212529; }}
            .status-fail {{ background-color: #dc3545; }}
            .currency-badge {{ padding: 2px 8px; border-radius: 4px; font-family: monospace;
                             background: #e9ecef; font-size: 12px; }}
            .currency-match {{ background: #d4edda; color: #155724; }}
            .currency-foreign {{ background: #fff3cd; color: #856404; }}
            .currency-mismatch {{ background: #f8d7da; color: #721c24; }}
            .timestamp {{ color: #6c757d; font-size: 12px; margin-top: 20px; }}
            .info-box {{ background: #cff4fc; border-left: 4px solid #0dcaf0;
                        padding: 15px; margin: 20px 0; border-radius: 4px; }}
        </style>
    </head>
    <body>
        <div class="container">
            <h1>Currency Consistency Report</h1>

            <div class="info-box">
                <strong>Check Description:</strong> This report validates that all portfolio positions
                either match the fund's base currency or have valid FX rates defined for currency conversion.
            </div>

            {
                for $fund in $funds
                let $fundName := $fund/Names/OfficialName/text()
                let $baseCurrency := $fund/Currency/text()
                let $positions := $fund/FundDynamicData/Portfolios/Portfolio/Positions/Position
                let $matchingCurrency := $positions[Currency = $baseCurrency]
                let $foreignWithFx := $positions[Currency != $baseCurrency and FXRates/FXRate]
                let $foreignWithoutFx := $positions[Currency != $baseCurrency and not(FXRates/FXRate)]

                return
                <div class="summary-card">
                    <h2>Fund: {$fundName}</h2>
                    <p><strong>Base Currency:</strong>
                        <span class="currency-badge currency-match">{$baseCurrency}</span>
                    </p>

                    <div class="summary-stats">
                        <div class="stat">
                            <div class="stat-value">{count($positions)}</div>
                            <div class="stat-label">Total Positions</div>
                        </div>
                        <div class="stat">
                            <div class="stat-value" style="color: #28a745;">{count($matchingCurrency)}</div>
                            <div class="stat-label">Base Currency</div>
                        </div>
                        <div class="stat">
                            <div class="stat-value" style="color: #ffc107;">{count($foreignWithFx)}</div>
                            <div class="stat-label">Foreign with FX</div>
                        </div>
                        <div class="stat">
                            <div class="stat-value" style="color: #dc3545;">{count($foreignWithoutFx)}</div>
                            <div class="stat-label">Missing FX Rate</div>
                        </div>
                    </div>

                    {if (count($foreignWithoutFx) > 0) then
                        <div>
                            <h3 style="color: #dc3545;">Positions Missing FX Rates</h3>
                            <table>
                                <thead>
                                    <tr>
                                        <th>Position ID</th>
                                        <th>ISIN</th>
                                        <th>Position Currency</th>
                                        <th>Status</th>
                                    </tr>
                                </thead>
                                <tbody>
                                {
                                    for $pos in $foreignWithoutFx
                                    return
                                    <tr>
                                        <td>{$pos/UniqueID/text()}</td>
                                        <td>{($pos/Identifiers/ISIN/text(), $pos/Identifiers/OtherID/text())[1]}</td>
                                        <td><span class="currency-badge currency-mismatch">{$pos/Currency/text()}</span></td>
                                        <td><span class="status status-fail">MISSING FX</span></td>
                                    </tr>
                                }
                                </tbody>
                            </table>
                        </div>
                    else
                        <p style="color: #28a745;"><strong>All positions have valid currency configuration.</strong></p>
                    }

                    {if (count($foreignWithFx) > 0) then
                        <div>
                            <h3 style="color: #856404;">Foreign Currency Positions with FX Rates</h3>
                            <table>
                                <thead>
                                    <tr>
                                        <th>Position ID</th>
                                        <th>Position Currency</th>
                                        <th>FX From</th>
                                        <th>FX To</th>
                                        <th>FX Rate</th>
                                        <th>Status</th>
                                    </tr>
                                </thead>
                                <tbody>
                                {
                                    for $pos in $foreignWithFx
                                    let $fxRate := $pos/FXRates/FXRate[1]
                                    let $rateValue := $fxRate/text()
                                    let $isValid := local:is-valid-fx-rate($rateValue)
                                    return
                                    <tr>
                                        <td>{$pos/UniqueID/text()}</td>
                                        <td><span class="currency-badge currency-foreign">{$pos/Currency/text()}</span></td>
                                        <td>{$fxRate/@fromCcy/string()}</td>
                                        <td>{$fxRate/@toCcy/string()}</td>
                                        <td>{$rateValue}</td>
                                        <td>
                                            {if ($isValid) then
                                                <span class="status status-pass">VALID</span>
                                            else
                                                <span class="status status-fail">INVALID</span>
                                            }
                                        </td>
                                    </tr>
                                }
                                </tbody>
                            </table>
                        </div>
                    else ()}

                    <h3>Currency Distribution</h3>
                    <table>
                        <thead>
                            <tr>
                                <th>Currency</th>
                                <th>Count</th>
                                <th>Percentage</th>
                            </tr>
                        </thead>
                        <tbody>
                        {
                            for $currency in distinct-values($positions/Currency)
                            let $count := count($positions[Currency = $currency])
                            let $pct := round($count div count($positions) * 100 * 100) div 100
                            order by $count descending
                            return
                            <tr>
                                <td>
                                    <span class="currency-badge {if ($currency = $baseCurrency) then 'currency-match' else 'currency-foreign'}">
                                        {$currency}
                                    </span>
                                </td>
                                <td>{$count}</td>
                                <td>{$pct}%</td>
                            </tr>
                        }
                        </tbody>
                    </table>
                </div>
            }

            <p class="timestamp">Report generated: {current-dateTime()}</p>
        </div>
    </body>
</html>
