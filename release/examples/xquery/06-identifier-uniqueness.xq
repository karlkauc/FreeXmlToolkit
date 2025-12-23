xquery version "3.1";

(:~
 : Identifier Uniqueness Check
 :
 : Purpose: Verify that all position identifiers are unique within their context.
 :
 : Checks:
 : - UniqueID is unique within each portfolio
 : - ISIN appears at most once per portfolio (no duplicate holdings)
 : - No empty or null identifiers
 :
 : @author FreeXmlToolkit Examples
 : @version 1.0
 :)

declare namespace output = "http://www.w3.org/2010/xslt-xquery-serialization";
declare option output:method "html";
declare option output:html-version "5";

(: Helper function to find duplicates in a sequence :)
declare function local:find-duplicates($items as xs:string*) as xs:string* {
    for $item in distinct-values($items)
    where count($items[. = $item]) > 1
    return $item
};

(: Main processing :)
let $doc := /
let $funds := $doc//Fund

return
<html>
    <head>
        <title>Identifier Uniqueness Report</title>
        <style>
            body {{ font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                   margin: 20px; background: #f8f9fa; color: #212529; }}
            .container {{ max-width: 1200px; margin: 0 auto; }}
            h1 {{ color: #0d6efd; border-bottom: 3px solid #0d6efd; padding-bottom: 10px; }}
            h2 {{ color: #495057; margin-top: 30px; }}
            h3 {{ color: #6c757d; }}
            .summary-card {{ background: white; border-radius: 8px; padding: 20px;
                           margin: 20px 0; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }}
            .summary-stats {{ display: flex; gap: 20px; flex-wrap: wrap; }}
            .stat {{ flex: 1; min-width: 140px; text-align: center; padding: 15px;
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
            .status-pass {{ background-color: #28a745; }}
            .status-warning {{ background-color: #ffc107; color: #212529; }}
            .status-fail {{ background-color: #dc3545; }}
            .check-item {{ display: flex; justify-content: space-between; align-items: center;
                          padding: 10px; border-bottom: 1px solid #dee2e6; }}
            .check-item:last-child {{ border-bottom: none; }}
            .duplicate-badge {{ background: #f8d7da; color: #721c24; padding: 2px 8px;
                               border-radius: 4px; font-size: 12px; font-family: monospace; }}
            .timestamp {{ color: #6c757d; font-size: 12px; margin-top: 20px; }}
            .info-box {{ background: #cff4fc; border-left: 4px solid #0dcaf0;
                        padding: 15px; margin: 20px 0; border-radius: 4px; }}
            .alert-box {{ background: #f8d7da; border-left: 4px solid #dc3545;
                         padding: 15px; margin: 20px 0; border-radius: 4px; }}
        </style>
    </head>
    <body>
        <div class="container">
            <h1>Identifier Uniqueness Report</h1>

            <div class="info-box">
                <strong>Validation Rules:</strong>
                <ul style="margin: 10px 0 0 0;">
                    <li>Each Position must have a unique UniqueID within the portfolio</li>
                    <li>ISINs should not be duplicated (no duplicate holdings)</li>
                    <li>Identifiers should not be empty or null</li>
                </ul>
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

                        (: Extract identifiers :)
                        let $uniqueIds := $positions/UniqueID/text()
                        let $isins := $positions/Identifiers/ISIN/text()
                        let $otherIds := $positions/Identifiers/OtherID/text()

                        (: Find duplicates :)
                        let $duplicateUniqueIds := local:find-duplicates($uniqueIds)
                        let $duplicateIsins := local:find-duplicates($isins)

                        (: Count issues :)
                        let $emptyUniqueIds := count($positions[not(UniqueID) or UniqueID = ""])
                        let $emptyIdentifiers := count($positions[not(Identifiers/ISIN) and not(Identifiers/OtherID)])

                        let $hasIssues := count($duplicateUniqueIds) > 0 or count($duplicateIsins) > 0 or
                                          $emptyUniqueIds > 0 or $emptyIdentifiers > 0

                        return
                        <div>
                            <h3>Portfolio (NAV Date: {$navDate})</h3>

                            <div class="summary-stats">
                                <div class="stat">
                                    <div class="stat-value">{count($positions)}</div>
                                    <div class="stat-label">Total Positions</div>
                                </div>
                                <div class="stat">
                                    <div class="stat-value">{count(distinct-values($uniqueIds))}</div>
                                    <div class="stat-label">Unique IDs</div>
                                </div>
                                <div class="stat">
                                    <div class="stat-value">{count(distinct-values($isins))}</div>
                                    <div class="stat-label">Unique ISINs</div>
                                </div>
                                <div class="stat">
                                    <div class="stat-value" style="color: {if ($hasIssues) then '#dc3545' else '#28a745'};">
                                        {if ($hasIssues) then
                                            <span class="status status-fail">ISSUES</span>
                                        else
                                            <span class="status status-pass">PASS</span>
                                        }
                                    </div>
                                    <div class="stat-label">Status</div>
                                </div>
                            </div>

                            <h4>Uniqueness Checks</h4>
                            <div style="background: white; border-radius: 8px; padding: 15px; margin: 15px 0;">
                                <div class="check-item">
                                    <span><strong>UniqueID Uniqueness</strong></span>
                                    <span>
                                        {if (count($duplicateUniqueIds) = 0) then
                                            <span class="status status-pass">PASS</span>
                                        else
                                            <span class="status status-fail">{count($duplicateUniqueIds)} DUPLICATES</span>
                                        }
                                    </span>
                                </div>
                                <div class="check-item">
                                    <span><strong>ISIN Uniqueness</strong></span>
                                    <span>
                                        {if (count($duplicateIsins) = 0) then
                                            <span class="status status-pass">PASS</span>
                                        else
                                            <span class="status status-warning">{count($duplicateIsins)} DUPLICATES</span>
                                        }
                                    </span>
                                </div>
                                <div class="check-item">
                                    <span><strong>No Empty UniqueIDs</strong></span>
                                    <span>
                                        {if ($emptyUniqueIds = 0) then
                                            <span class="status status-pass">PASS</span>
                                        else
                                            <span class="status status-fail">{$emptyUniqueIds} EMPTY</span>
                                        }
                                    </span>
                                </div>
                                <div class="check-item">
                                    <span><strong>All Positions Have Identifiers</strong></span>
                                    <span>
                                        {if ($emptyIdentifiers = 0) then
                                            <span class="status status-pass">PASS</span>
                                        else
                                            <span class="status status-fail">{$emptyIdentifiers} MISSING</span>
                                        }
                                    </span>
                                </div>
                            </div>

                            {if (count($duplicateUniqueIds) > 0) then
                                <div class="alert-box">
                                    <strong>Duplicate UniqueIDs Found:</strong>
                                    <table style="margin-top: 10px;">
                                        <thead>
                                            <tr>
                                                <th>UniqueID</th>
                                                <th>Occurrence Count</th>
                                                <th>Affected ISINs</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                        {
                                            for $dupId in $duplicateUniqueIds
                                            let $affectedPositions := $positions[UniqueID = $dupId]
                                            return
                                            <tr>
                                                <td><span class="duplicate-badge">{$dupId}</span></td>
                                                <td>{count($affectedPositions)}</td>
                                                <td>{string-join($affectedPositions/Identifiers/ISIN/text(), ", ")}</td>
                                            </tr>
                                        }
                                        </tbody>
                                    </table>
                                </div>
                            else ()}

                            {if (count($duplicateIsins) > 0) then
                                <div style="background: #fff3cd; border-left: 4px solid #ffc107;
                                           padding: 15px; margin: 20px 0; border-radius: 4px;">
                                    <strong>Duplicate ISINs Found (possible duplicate holdings):</strong>
                                    <table style="margin-top: 10px;">
                                        <thead>
                                            <tr>
                                                <th>ISIN</th>
                                                <th>Occurrence Count</th>
                                                <th>Position IDs</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                        {
                                            for $dupIsin in $duplicateIsins
                                            let $affectedPositions := $positions[Identifiers/ISIN = $dupIsin]
                                            return
                                            <tr>
                                                <td><span class="duplicate-badge">{$dupIsin}</span></td>
                                                <td>{count($affectedPositions)}</td>
                                                <td>{string-join($affectedPositions/UniqueID/text(), ", ")}</td>
                                            </tr>
                                        }
                                        </tbody>
                                    </table>
                                </div>
                            else ()}
                        </div>
                    }
                </div>
            }

            <p class="timestamp">Report generated: {current-dateTime()}</p>
        </div>
    </body>
</html>
