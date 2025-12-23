xquery version "3.1";

(:~
 : Comprehensive Data Quality Summary Report
 :
 : Purpose: Execute all DQ checks and generate a unified summary report.
 : This is a "master" check that runs all validations and provides an overall DQ score.
 :
 : Checks included:
 : 1. NAV Reconciliation
 : 2. Currency Consistency
 : 3. Date Validation
 : 4. Percentage Sum
 : 5. Bond Calculations
 : 6. Identifier Uniqueness
 : 7. Required Fields
 : 8. Data Completeness
 :
 : @author FreeXmlToolkit Examples
 : @version 1.0
 :)

declare namespace output = "http://www.w3.org/2010/xslt-xquery-serialization";
declare option output:method "html";
declare option output:html-version "5";

(: ========== Helper Functions ========== :)

(: Check if field is present :)
declare function local:is-present($node as node()?) as xs:boolean {
    exists($node) and normalize-space(string($node)) != ""
};

(: Calculate NAV variance :)
declare function local:calc-nav-variance($fund as element()) as xs:decimal {
    let $reportedNav := xs:decimal(($fund/FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount, 0)[1])
    let $calculatedSum := sum($fund/FundDynamicData/Portfolios/Portfolio/Positions/Position/TotalValue/Amount/xs:decimal(.))
    return if ($reportedNav = 0) then 0 else round(abs(($calculatedSum - $reportedNav) div $reportedNav) * 10000) div 100
};

(: Calculate percentage sum variance :)
declare function local:calc-pct-variance($fund as element()) as xs:decimal {
    let $sum := sum($fund/FundDynamicData/Portfolios/Portfolio/Positions/Position/TotalPercentage/xs:decimal(.))
    return abs($sum - 100)
};

(: Find duplicate identifiers :)
declare function local:find-duplicates($items as xs:string*) as xs:string* {
    for $item in distinct-values($items)
    where count($items[. = $item]) > 1
    return $item
};

(: Parse date safely :)
declare function local:parse-date($dateStr as xs:string?) as xs:date? {
    if (empty($dateStr) or $dateStr = "") then ()
    else try { xs:date(substring($dateStr, 1, 10)) } catch * { () }
};

(: Get status based on pass/fail :)
declare function local:get-check-status($passed as xs:boolean) as element() {
    if ($passed) then
        <span class="status status-pass">PASS</span>
    else
        <span class="status status-fail">FAIL</span>
};

(: Get status color :)
declare function local:get-score-color($score as xs:decimal) as xs:string {
    if ($score >= 90) then "#28a745"
    else if ($score >= 70) then "#ffc107"
    else "#dc3545"
};

(: ========== Main Processing ========== :)
let $doc := /
let $controlData := $doc//ControlData
let $funds := $doc//Fund
let $positions := $doc//Position

(: ========== Run All Checks ========== :)

(: 1. NAV Reconciliation :)
let $navCheck := (
    for $fund in $funds
    let $variance := local:calc-nav-variance($fund)
    return $variance <= 0.1
)
let $navPassed := every $check in $navCheck satisfies $check

(: 2. Currency Consistency :)
let $currencyCheck := (
    for $fund in $funds
    let $baseCcy := $fund/Currency/text()
    let $foreignWithoutFx := $fund//Position[Currency != $baseCcy and not(FXRates/FXRate)]
    return count($foreignWithoutFx) = 0
)
let $currencyPassed := every $check in $currencyCheck satisfies $check

(: 3. Date Validation :)
let $contentDate := local:parse-date($controlData/ContentDate/text())
let $navDates := distinct-values($funds//TotalAssetValue/NavDate/text())
let $datesPassed := exists($contentDate) and
    (every $navDate in $navDates satisfies local:parse-date($navDate) = $contentDate)

(: 4. Percentage Sum :)
let $pctCheck := (
    for $fund in $funds
    let $variance := local:calc-pct-variance($fund)
    return $variance <= 0.5
)
let $pctPassed := every $check in $pctCheck satisfies $check

(: 5. Bond Calculations :)
let $bondPositions := $positions[Bond]
let $bondCheck := (
    for $pos in $bondPositions
    let $bond := $pos/Bond
    let $nominal := xs:decimal(($bond/Nominal, 0)[1])
    let $price := xs:decimal(($bond/Price/Amount, 0)[1])
    let $indexfactor := xs:decimal(($bond/Indexfactor, 1)[1])
    let $poolfactor := xs:decimal(($bond/Poolfactor, 1)[1])
    let $reportedMV := xs:decimal(($bond/MarketValue/Amount, 0)[1])
    let $calculatedMV := round($nominal * ($price div 100) * $indexfactor * $poolfactor * 100) div 100
    let $diff := abs($reportedMV - $calculatedMV)
    return $diff <= 1 or ($reportedMV > 0 and $diff div $reportedMV * 100 <= 0.1)
)
let $bondPassed := count($bondPositions) = 0 or (every $check in $bondCheck satisfies $check)

(: 6. Identifier Uniqueness :)
let $uniqueIdDuplicates := local:find-duplicates($positions/UniqueID/text())
let $uniquenessPassed := count($uniqueIdDuplicates) = 0

(: 7. Required Fields :)
let $missingRequired := count($positions[
    not(local:is-present(UniqueID)) or
    not(local:is-present(Currency)) or
    not(local:is-present(TotalValue/Amount))
])
let $requiredPassed := $missingRequired = 0

(: 8. Data Completeness :)
let $positionsWithFullData := count($positions[
    local:is-present(UniqueID) and
    local:is-present(Currency) and
    local:is-present(TotalValue/Amount) and
    local:is-present(TotalPercentage) and
    (local:is-present(Identifiers/ISIN) or local:is-present(Identifiers/OtherID))
])
let $completenessScore := round($positionsWithFullData div count($positions) * 100)
let $completenessPassed := $completenessScore >= 90

(: Calculate overall score :)
let $checksTotal := 8
let $checksPassed := (
    (if ($navPassed) then 1 else 0) +
    (if ($currencyPassed) then 1 else 0) +
    (if ($datesPassed) then 1 else 0) +
    (if ($pctPassed) then 1 else 0) +
    (if ($bondPassed) then 1 else 0) +
    (if ($uniquenessPassed) then 1 else 0) +
    (if ($requiredPassed) then 1 else 0) +
    (if ($completenessPassed) then 1 else 0)
)
let $overallScore := round($checksPassed div $checksTotal * 100)

return
<html>
    <head>
        <title>Comprehensive Data Quality Report</title>
        <style>
            body {{ font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                   margin: 20px; background: #f8f9fa; color: #212529; }}
            .container {{ max-width: 1200px; margin: 0 auto; }}
            h1 {{ color: #0d6efd; border-bottom: 3px solid #0d6efd; padding-bottom: 10px; }}
            h2 {{ color: #495057; margin-top: 30px; }}
            .summary-card {{ background: white; border-radius: 8px; padding: 20px;
                           margin: 20px 0; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }}
            .summary-stats {{ display: flex; gap: 20px; flex-wrap: wrap; }}
            .stat {{ flex: 1; min-width: 150px; text-align: center; padding: 20px;
                    background: #e9ecef; border-radius: 6px; }}
            .stat-value {{ font-size: 36px; font-weight: bold; }}
            .stat-label {{ font-size: 14px; color: #6c757d; margin-top: 5px; }}
            table {{ width: 100%; border-collapse: collapse; background: white;
                    border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }}
            th {{ background: #0d6efd; color: white; padding: 15px; text-align: left; }}
            td {{ padding: 15px; border-bottom: 1px solid #dee2e6; }}
            tr:hover {{ background: #f8f9fa; }}
            .status {{ padding: 5px 15px; border-radius: 20px; font-size: 12px;
                      font-weight: bold; color: white; display: inline-block; }}
            .status-pass {{ background-color: #28a745; }}
            .status-fail {{ background-color: #dc3545; }}
            .status-warning {{ background-color: #ffc107; color: #212529; }}
            .score-circle {{ width: 150px; height: 150px; border-radius: 50%;
                           display: flex; align-items: center; justify-content: center;
                           font-size: 48px; font-weight: bold; color: white;
                           margin: 20px auto; }}
            .check-description {{ color: #6c757d; font-size: 13px; }}
            .detail-value {{ font-family: monospace; background: #e9ecef; padding: 2px 8px; border-radius: 4px; }}
            .timestamp {{ color: #6c757d; font-size: 12px; margin-top: 20px; }}
            .overall-grade {{ font-size: 24px; font-weight: bold; margin-top: 10px; }}
            .progress-bar {{ width: 100%; height: 10px; background: #e9ecef; border-radius: 5px; overflow: hidden; }}
            .progress-fill {{ height: 100%; transition: width 0.3s; }}
        </style>
    </head>
    <body>
        <div class="container">
            <h1>Comprehensive Data Quality Report</h1>

            <div class="summary-card">
                <h2 style="margin-top: 0;">Overall Data Quality Score</h2>
                <div style="text-align: center;">
                    <div class="score-circle" style="background-color: {local:get-score-color($overallScore)};">
                        {$overallScore}%
                    </div>
                    <div class="overall-grade" style="color: {local:get-score-color($overallScore)};">
                        {if ($overallScore >= 90) then "EXCELLENT"
                         else if ($overallScore >= 70) then "GOOD"
                         else if ($overallScore >= 50) then "NEEDS IMPROVEMENT"
                         else "CRITICAL"}
                    </div>
                    <p>{$checksPassed} of {$checksTotal} checks passed</p>
                </div>

                <div class="progress-bar" style="margin: 20px 0;">
                    <div class="progress-fill" style="width: {$overallScore}%; background-color: {local:get-score-color($overallScore)};"></div>
                </div>
            </div>

            <div class="summary-card">
                <h2 style="margin-top: 0;">Document Overview</h2>
                <div class="summary-stats">
                    <div class="stat">
                        <div class="stat-value">{count($funds)}</div>
                        <div class="stat-label">Funds</div>
                    </div>
                    <div class="stat">
                        <div class="stat-value">{count($positions)}</div>
                        <div class="stat-label">Positions</div>
                    </div>
                    <div class="stat">
                        <div class="stat-value">{count($bondPositions)}</div>
                        <div class="stat-label">Bonds</div>
                    </div>
                    <div class="stat">
                        <div class="stat-value">{count(distinct-values($positions/Currency))}</div>
                        <div class="stat-label">Currencies</div>
                    </div>
                </div>
            </div>

            <h2>Data Quality Check Results</h2>
            <table>
                <thead>
                    <tr>
                        <th style="width: 5%;">#</th>
                        <th style="width: 25%;">Check</th>
                        <th style="width: 40%;">Description</th>
                        <th style="width: 15%;">Details</th>
                        <th style="width: 15%;">Status</th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td>1</td>
                        <td><strong>NAV Reconciliation</strong></td>
                        <td class="check-description">Sum of position values equals reported NAV (tolerance: 0.1%)</td>
                        <td>
                            {for $fund in $funds
                             let $var := local:calc-nav-variance($fund)
                             return <span class="detail-value">{round($var * 100) div 100}%</span>}
                        </td>
                        <td>{local:get-check-status($navPassed)}</td>
                    </tr>
                    <tr>
                        <td>2</td>
                        <td><strong>Currency Consistency</strong></td>
                        <td class="check-description">Foreign currency positions have valid FX rates</td>
                        <td>
                            <span class="detail-value">
                                {count($positions[Currency != ancestor::Fund/Currency and not(FXRates/FXRate)])} missing
                            </span>
                        </td>
                        <td>{local:get-check-status($currencyPassed)}</td>
                    </tr>
                    <tr>
                        <td>3</td>
                        <td><strong>Date Validation</strong></td>
                        <td class="check-description">NAV dates match content date, dates are valid</td>
                        <td>
                            <span class="detail-value">{string($contentDate)}</span>
                        </td>
                        <td>{local:get-check-status($datesPassed)}</td>
                    </tr>
                    <tr>
                        <td>4</td>
                        <td><strong>Percentage Sum</strong></td>
                        <td class="check-description">Position percentages sum to 100% (tolerance: 0.5%)</td>
                        <td>
                            {for $fund in $funds
                             let $var := local:calc-pct-variance($fund)
                             return <span class="detail-value">{round($var * 100) div 100}% off</span>}
                        </td>
                        <td>{local:get-check-status($pctPassed)}</td>
                    </tr>
                    <tr>
                        <td>5</td>
                        <td><strong>Bond Calculations</strong></td>
                        <td class="check-description">Bond market values match Nominal * Price formula</td>
                        <td>
                            <span class="detail-value">{count($bondPositions)} bonds</span>
                        </td>
                        <td>{local:get-check-status($bondPassed)}</td>
                    </tr>
                    <tr>
                        <td>6</td>
                        <td><strong>Identifier Uniqueness</strong></td>
                        <td class="check-description">No duplicate UniqueIDs within portfolios</td>
                        <td>
                            <span class="detail-value">{count($uniqueIdDuplicates)} duplicates</span>
                        </td>
                        <td>{local:get-check-status($uniquenessPassed)}</td>
                    </tr>
                    <tr>
                        <td>7</td>
                        <td><strong>Required Fields</strong></td>
                        <td class="check-description">All mandatory fields (UniqueID, Currency, TotalValue) present</td>
                        <td>
                            <span class="detail-value">{$missingRequired} missing</span>
                        </td>
                        <td>{local:get-check-status($requiredPassed)}</td>
                    </tr>
                    <tr>
                        <td>8</td>
                        <td><strong>Data Completeness</strong></td>
                        <td class="check-description">At least 90% of positions have complete data</td>
                        <td>
                            <span class="detail-value">{$completenessScore}% complete</span>
                        </td>
                        <td>{local:get-check-status($completenessPassed)}</td>
                    </tr>
                </tbody>
            </table>

            {if ($checksPassed < $checksTotal) then
                <div class="summary-card" style="background: #fff3cd; border-left: 4px solid #ffc107;">
                    <h3 style="margin-top: 0; color: #856404;">Recommendations</h3>
                    <ul>
                        {if (not($navPassed)) then <li>Review NAV reconciliation - position values don't match reported NAV</li> else ()}
                        {if (not($currencyPassed)) then <li>Add FX rates for foreign currency positions</li> else ()}
                        {if (not($datesPassed)) then <li>Verify date consistency across document</li> else ()}
                        {if (not($pctPassed)) then <li>Check position percentages - they should sum to 100%</li> else ()}
                        {if (not($bondPassed)) then <li>Verify bond calculations (Nominal * Price = Market Value)</li> else ()}
                        {if (not($uniquenessPassed)) then <li>Remove duplicate position identifiers</li> else ()}
                        {if (not($requiredPassed)) then <li>Fill in missing required fields</li> else ()}
                        {if (not($completenessPassed)) then <li>Improve data completeness across positions</li> else ()}
                    </ul>
                </div>
            else
                <div class="summary-card" style="background: #d4edda; border-left: 4px solid #28a745;">
                    <h3 style="margin-top: 0; color: #155724;">All Checks Passed</h3>
                    <p>The document meets all data quality requirements.</p>
                </div>
            }

            <div class="summary-card">
                <h3>Document Information</h3>
                <table>
                    <tbody>
                        <tr>
                            <td><strong>Document ID</strong></td>
                            <td>{$controlData/UniqueDocumentID/text()}</td>
                        </tr>
                        <tr>
                            <td><strong>Content Date</strong></td>
                            <td>{$controlData/ContentDate/text()}</td>
                        </tr>
                        <tr>
                            <td><strong>Generated</strong></td>
                            <td>{substring($controlData/DocumentGenerated/text(), 1, 19)}</td>
                        </tr>
                        <tr>
                            <td><strong>Data Supplier</strong></td>
                            <td>{$controlData/DataSupplier/Name/text()}</td>
                        </tr>
                        <tr>
                            <td><strong>Funds</strong></td>
                            <td>{string-join($funds/Names/OfficialName/text(), ", ")}</td>
                        </tr>
                    </tbody>
                </table>
            </div>

            <p class="timestamp">Report generated: {current-dateTime()}</p>
        </div>
    </body>
</html>
