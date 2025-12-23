xquery version "3.1";

(:~
 : Data Completeness Score
 :
 : Purpose: Calculate a comprehensive completeness score for each entity.
 : This helps identify positions or funds with data quality issues.
 :
 : Scoring:
 : - Mandatory fields (UniqueID, Currency, TotalValue): 2 points each
 : - Important fields (ISIN, TotalPercentage): 1.5 points each
 : - Optional fields (Exposures, FXRates, Bond details): 1 point each
 :
 : @author FreeXmlToolkit Examples
 : @version 1.0
 :)

declare namespace output = "http://www.w3.org/2010/xslt-xquery-serialization";
declare option output:method "html";
declare option output:html-version "5";

(: Field weight definitions :)
declare variable $local:MANDATORY_WEIGHT := 2;
declare variable $local:IMPORTANT_WEIGHT := 1.5;
declare variable $local:OPTIONAL_WEIGHT := 1;

(: Helper function to check if a field is present and non-empty :)
declare function local:is-present($node as node()?) as xs:boolean {
    exists($node) and normalize-space(string($node)) != ""
};

(: Helper function to calculate position completeness :)
declare function local:calc-position-score($pos as element()) as map(*) {
    let $fields := (
        (: Mandatory fields - weight 2 :)
        map { "name": "UniqueID", "present": local:is-present($pos/UniqueID), "weight": $local:MANDATORY_WEIGHT, "category": "mandatory" },
        map { "name": "Currency", "present": local:is-present($pos/Currency), "weight": $local:MANDATORY_WEIGHT, "category": "mandatory" },
        map { "name": "TotalValue", "present": local:is-present($pos/TotalValue/Amount), "weight": $local:MANDATORY_WEIGHT, "category": "mandatory" },

        (: Important fields - weight 1.5 :)
        map { "name": "ISIN/OtherID", "present": local:is-present($pos/Identifiers/ISIN) or local:is-present($pos/Identifiers/OtherID), "weight": $local:IMPORTANT_WEIGHT, "category": "important" },
        map { "name": "TotalPercentage", "present": local:is-present($pos/TotalPercentage), "weight": $local:IMPORTANT_WEIGHT, "category": "important" },

        (: Optional fields - weight 1 :)
        map { "name": "Exposures", "present": exists($pos/Exposures/Exposure), "weight": $local:OPTIONAL_WEIGHT, "category": "optional" },
        map { "name": "FXRates", "present": exists($pos/FXRates/FXRate), "weight": $local:OPTIONAL_WEIGHT, "category": "optional" }
    )

    let $earnedPoints := sum(for $f in $fields return if ($f?present) then $f?weight else 0)
    let $maxPoints := sum(for $f in $fields return $f?weight)
    let $score := round($earnedPoints div $maxPoints * 100)

    return map {
        "fields": $fields,
        "earnedPoints": $earnedPoints,
        "maxPoints": $maxPoints,
        "score": $score
    }
};

(: Helper function to get score color :)
declare function local:get-score-color($score as xs:decimal) as xs:string {
    if ($score >= 90) then "#28a745"
    else if ($score >= 70) then "#ffc107"
    else "#dc3545"
};

(: Main processing :)
let $doc := /
let $funds := $doc//Fund

return
<html>
    <head>
        <title>Data Completeness Score Report</title>
        <style>
            body {{ font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                   margin: 20px; background: #f8f9fa; color: #212529; }}
            .container {{ max-width: 1200px; margin: 0 auto; }}
            h1 {{ color: #0d6efd; border-bottom: 3px solid #0d6efd; padding-bottom: 10px; }}
            h2 {{ color: #495057; margin-top: 30px; }}
            .summary-card {{ background: white; border-radius: 8px; padding: 20px;
                           margin: 20px 0; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }}
            .summary-stats {{ display: flex; gap: 20px; flex-wrap: wrap; }}
            .stat {{ flex: 1; min-width: 140px; text-align: center; padding: 15px;
                    background: #e9ecef; border-radius: 6px; }}
            .stat-value {{ font-size: 28px; font-weight: bold; }}
            .stat-label {{ font-size: 12px; color: #6c757d; margin-top: 5px; }}
            table {{ width: 100%; border-collapse: collapse; background: white;
                    border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }}
            th {{ background: #0d6efd; color: white; padding: 12px; text-align: left; }}
            td {{ padding: 12px; border-bottom: 1px solid #dee2e6; }}
            tr:hover {{ background: #f8f9fa; }}
            .score-badge {{ padding: 5px 15px; border-radius: 20px; font-size: 14px;
                          font-weight: bold; color: white; display: inline-block; }}
            .progress-ring {{ width: 120px; height: 120px; }}
            .progress-ring-circle {{ stroke-dasharray: 314; stroke-dashoffset: 0;
                                    stroke-linecap: round; transform: rotate(-90deg); transform-origin: 50% 50%; }}
            .score-distribution {{ display: flex; gap: 10px; margin: 15px 0; }}
            .score-bucket {{ flex: 1; text-align: center; padding: 10px; border-radius: 6px; }}
            .timestamp {{ color: #6c757d; font-size: 12px; margin-top: 20px; }}
            .info-box {{ background: #cff4fc; border-left: 4px solid #0dcaf0;
                        padding: 15px; margin: 20px 0; border-radius: 4px; }}
            .legend {{ display: flex; gap: 20px; margin: 15px 0; }}
            .legend-item {{ display: flex; align-items: center; gap: 5px; }}
            .legend-color {{ width: 16px; height: 16px; border-radius: 4px; }}
        </style>
    </head>
    <body>
        <div class="container">
            <h1>Data Completeness Score Report</h1>

            <div class="info-box">
                <strong>Scoring System:</strong>
                <div class="legend">
                    <div class="legend-item">
                        <div class="legend-color" style="background: #0d6efd;"></div>
                        <span>Mandatory Fields (2 pts)</span>
                    </div>
                    <div class="legend-item">
                        <div class="legend-color" style="background: #6c757d;"></div>
                        <span>Important Fields (1.5 pts)</span>
                    </div>
                    <div class="legend-item">
                        <div class="legend-color" style="background: #adb5bd;"></div>
                        <span>Optional Fields (1 pt)</span>
                    </div>
                </div>
                <p style="margin: 10px 0 0 0;">Score thresholds: <span style="color: #28a745;">&#9679; 90%+ Excellent</span> |
                    <span style="color: #ffc107;">&#9679; 70-89% Good</span> |
                    <span style="color: #dc3545;">&#9679; &lt;70% Needs Attention</span></p>
            </div>

            {
                for $fund in $funds
                let $fundName := $fund/Names/OfficialName/text()
                let $positions := $fund/FundDynamicData/Portfolios/Portfolio/Positions/Position

                let $positionScores := for $pos in $positions return local:calc-position-score($pos)
                let $avgScore := if (count($positionScores) > 0) then
                    round(avg(for $ps in $positionScores return $ps?score))
                else 0

                let $excellentCount := count($positionScores[?score >= 90])
                let $goodCount := count($positionScores[?score >= 70 and ?score < 90])
                let $needsAttentionCount := count($positionScores[?score < 70])

                return
                <div class="summary-card">
                    <h2>Fund: {$fundName}</h2>

                    <div class="summary-stats">
                        <div class="stat">
                            <div class="stat-value" style="color: {local:get-score-color($avgScore)};">{$avgScore}%</div>
                            <div class="stat-label">Average Score</div>
                        </div>
                        <div class="stat">
                            <div class="stat-value">{count($positions)}</div>
                            <div class="stat-label">Total Positions</div>
                        </div>
                        <div class="stat">
                            <div class="stat-value" style="color: #28a745;">{$excellentCount}</div>
                            <div class="stat-label">Excellent (90%+)</div>
                        </div>
                        <div class="stat">
                            <div class="stat-value" style="color: #ffc107;">{$goodCount}</div>
                            <div class="stat-label">Good (70-89%)</div>
                        </div>
                        <div class="stat">
                            <div class="stat-value" style="color: #dc3545;">{$needsAttentionCount}</div>
                            <div class="stat-label">Needs Attention</div>
                        </div>
                    </div>

                    <h3>Score Distribution</h3>
                    <div class="score-distribution">
                        <div class="score-bucket" style="background: #d4edda;">
                            <strong>{$excellentCount}</strong><br/>
                            <small>90-100%</small>
                        </div>
                        <div class="score-bucket" style="background: #fff3cd;">
                            <strong>{$goodCount}</strong><br/>
                            <small>70-89%</small>
                        </div>
                        <div class="score-bucket" style="background: #f8d7da;">
                            <strong>{$needsAttentionCount}</strong><br/>
                            <small>0-69%</small>
                        </div>
                    </div>

                    <h3>Position Details (sorted by completeness)</h3>
                    <table>
                        <thead>
                            <tr>
                                <th>Position ID</th>
                                <th>ISIN</th>
                                <th>Score</th>
                                <th>Mandatory</th>
                                <th>Important</th>
                                <th>Optional</th>
                                <th>Missing Fields</th>
                            </tr>
                        </thead>
                        <tbody>
                        {
                            let $posWithScores :=
                                for $pos at $i in $positions
                                let $scoreData := $positionScores[$i]
                                order by $scoreData?score ascending
                                return map { "pos": $pos, "score": $scoreData }

                            for $item in $posWithScores[position() <= 20]
                            let $pos := $item?pos
                            let $scoreData := $item?score
                            let $mandatoryPresent := count($scoreData?fields[?category = "mandatory" and ?present = true()])
                            let $mandatoryTotal := count($scoreData?fields[?category = "mandatory"])
                            let $importantPresent := count($scoreData?fields[?category = "important" and ?present = true()])
                            let $importantTotal := count($scoreData?fields[?category = "important"])
                            let $optionalPresent := count($scoreData?fields[?category = "optional" and ?present = true()])
                            let $optionalTotal := count($scoreData?fields[?category = "optional"])
                            let $missingFields := string-join(for $f in $scoreData?fields where not($f?present) return $f?name, ", ")

                            return
                            <tr>
                                <td>{$pos/UniqueID/text()}</td>
                                <td>{($pos/Identifiers/ISIN/text(), $pos/Identifiers/OtherID/text(), "-")[1]}</td>
                                <td>
                                    <span class="score-badge" style="background-color: {local:get-score-color($scoreData?score)};">
                                        {$scoreData?score}%
                                    </span>
                                </td>
                                <td>{$mandatoryPresent}/{$mandatoryTotal}</td>
                                <td>{$importantPresent}/{$importantTotal}</td>
                                <td>{$optionalPresent}/{$optionalTotal}</td>
                                <td style="color: #dc3545; font-size: 12px;">
                                    {if ($missingFields != "") then $missingFields else "-"}
                                </td>
                            </tr>
                        }
                        {if (count($positions) > 20) then
                            <tr><td colspan="7" style="text-align: center; color: #6c757d;">
                                ... showing lowest 20 of {count($positions)} positions
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
