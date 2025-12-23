xquery version "3.1";

(:~
 : Required Fields Check
 :
 : Purpose: Verify that all mandatory fields are present and non-empty.
 :
 : Checks fields at multiple levels:
 : - ControlData: UniqueDocumentID, ContentDate, DocumentGenerated, Language
 : - Fund: Names/OfficialName, Currency, Identifiers (LEI or ISIN)
 : - Position: UniqueID, Currency, TotalValue, TotalPercentage
 :
 : @author FreeXmlToolkit Examples
 : @version 1.0
 :)

declare namespace output = "http://www.w3.org/2010/xslt-xquery-serialization";
declare option output:method "html";
declare option output:html-version "5";

(: Helper function to check if a field is present and non-empty :)
declare function local:is-present($node as node()?) as xs:boolean {
    exists($node) and normalize-space(string($node)) != ""
};

(: Helper function to get field status icon :)
declare function local:get-field-status($present as xs:boolean) as element() {
    if ($present) then
        <span style="color: #28a745;">&#10004;</span>
    else
        <span style="color: #dc3545;">&#10008;</span>
};

(: Main processing :)
let $doc := /
let $controlData := $doc//ControlData
let $funds := $doc//Fund

(: Define required fields :)
let $controlDataFields := (
    map { "name": "UniqueDocumentID", "path": $controlData/UniqueDocumentID },
    map { "name": "ContentDate", "path": $controlData/ContentDate },
    map { "name": "DocumentGenerated", "path": $controlData/DocumentGenerated },
    map { "name": "Language", "path": $controlData/Language },
    map { "name": "DataSupplier/Name", "path": $controlData/DataSupplier/Name }
)

return
<html>
    <head>
        <title>Required Fields Check Report</title>
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
            .field-present {{ color: #28a745; }}
            .field-missing {{ color: #dc3545; font-style: italic; }}
            .field-value {{ font-family: monospace; background: #e9ecef; padding: 2px 6px; border-radius: 4px; }}
            .completeness-bar {{ width: 100%; height: 8px; background: #e9ecef; border-radius: 4px; overflow: hidden; }}
            .completeness-fill {{ height: 100%; background: #28a745; }}
            .timestamp {{ color: #6c757d; font-size: 12px; margin-top: 20px; }}
            .section-header {{ background: #f8f9fa; padding: 10px 15px; margin: 20px 0 10px 0;
                              border-left: 4px solid #0d6efd; font-weight: bold; }}
        </style>
    </head>
    <body>
        <div class="container">
            <h1>Required Fields Check Report</h1>

            <div class="summary-card">
                <h2>ControlData Fields</h2>
                <table>
                    <thead>
                        <tr>
                            <th>Field</th>
                            <th>Status</th>
                            <th>Value</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr>
                            <td>UniqueDocumentID</td>
                            <td>{local:get-field-status(local:is-present($controlData/UniqueDocumentID))}</td>
                            <td>
                                {if (local:is-present($controlData/UniqueDocumentID)) then
                                    <span class="field-value">{$controlData/UniqueDocumentID/text()}</span>
                                else
                                    <span class="field-missing">MISSING</span>
                                }
                            </td>
                        </tr>
                        <tr>
                            <td>ContentDate</td>
                            <td>{local:get-field-status(local:is-present($controlData/ContentDate))}</td>
                            <td>
                                {if (local:is-present($controlData/ContentDate)) then
                                    <span class="field-value">{$controlData/ContentDate/text()}</span>
                                else
                                    <span class="field-missing">MISSING</span>
                                }
                            </td>
                        </tr>
                        <tr>
                            <td>DocumentGenerated</td>
                            <td>{local:get-field-status(local:is-present($controlData/DocumentGenerated))}</td>
                            <td>
                                {if (local:is-present($controlData/DocumentGenerated)) then
                                    <span class="field-value">{substring($controlData/DocumentGenerated/text(), 1, 19)}</span>
                                else
                                    <span class="field-missing">MISSING</span>
                                }
                            </td>
                        </tr>
                        <tr>
                            <td>Language</td>
                            <td>{local:get-field-status(local:is-present($controlData/Language))}</td>
                            <td>
                                {if (local:is-present($controlData/Language)) then
                                    <span class="field-value">{$controlData/Language/text()}</span>
                                else
                                    <span class="field-missing">MISSING</span>
                                }
                            </td>
                        </tr>
                        <tr>
                            <td>DataSupplier/Name</td>
                            <td>{local:get-field-status(local:is-present($controlData/DataSupplier/Name))}</td>
                            <td>
                                {if (local:is-present($controlData/DataSupplier/Name)) then
                                    <span class="field-value">{$controlData/DataSupplier/Name/text()}</span>
                                else
                                    <span class="field-missing">MISSING</span>
                                }
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>

            {
                for $fund in $funds
                let $fundName := ($fund/Names/OfficialName/text(), "[Unnamed Fund]")[1]
                let $positions := $fund/FundDynamicData/Portfolios/Portfolio/Positions/Position

                (: Count missing required fields in positions :)
                let $missingUniqueId := count($positions[not(local:is-present(UniqueID))])
                let $missingCurrency := count($positions[not(local:is-present(Currency))])
                let $missingTotalValue := count($positions[not(local:is-present(TotalValue/Amount))])
                let $missingTotalPct := count($positions[not(local:is-present(TotalPercentage))])
                let $missingIdentifiers := count($positions[not(local:is-present(Identifiers/ISIN)) and not(local:is-present(Identifiers/OtherID))])

                let $totalPositions := count($positions)
                let $totalIssues := $missingUniqueId + $missingCurrency + $missingTotalValue + $missingTotalPct + $missingIdentifiers

                return
                <div class="summary-card">
                    <h2>Fund: {$fundName}</h2>

                    <div class="section-header">Fund-Level Required Fields</div>
                    <table>
                        <thead>
                            <tr>
                                <th>Field</th>
                                <th>Status</th>
                                <th>Value</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr>
                                <td>Names/OfficialName</td>
                                <td>{local:get-field-status(local:is-present($fund/Names/OfficialName))}</td>
                                <td>
                                    {if (local:is-present($fund/Names/OfficialName)) then
                                        <span class="field-value">{$fund/Names/OfficialName/text()}</span>
                                    else
                                        <span class="field-missing">MISSING</span>
                                    }
                                </td>
                            </tr>
                            <tr>
                                <td>Currency</td>
                                <td>{local:get-field-status(local:is-present($fund/Currency))}</td>
                                <td>
                                    {if (local:is-present($fund/Currency)) then
                                        <span class="field-value">{$fund/Currency/text()}</span>
                                    else
                                        <span class="field-missing">MISSING</span>
                                    }
                                </td>
                            </tr>
                            <tr>
                                <td>Identifiers (LEI or ISIN)</td>
                                <td>{local:get-field-status(local:is-present($fund/Identifiers/LEI) or local:is-present($fund/Identifiers/ISIN))}</td>
                                <td>
                                    {if (local:is-present($fund/Identifiers/LEI)) then
                                        <span class="field-value">LEI: {$fund/Identifiers/LEI/text()}</span>
                                    else if (local:is-present($fund/Identifiers/ISIN)) then
                                        <span class="field-value">ISIN: {$fund/Identifiers/ISIN/text()}</span>
                                    else
                                        <span class="field-missing">MISSING</span>
                                    }
                                </td>
                            </tr>
                            <tr>
                                <td>TotalNetAssetValue</td>
                                <td>{local:get-field-status(local:is-present($fund/FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount))}</td>
                                <td>
                                    {if (local:is-present($fund/FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount)) then
                                        <span class="field-value">{format-number(xs:decimal($fund/FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount), "#,##0.00")}</span>
                                    else
                                        <span class="field-missing">MISSING</span>
                                    }
                                </td>
                            </tr>
                        </tbody>
                    </table>

                    <div class="section-header">Position-Level Required Fields Summary</div>
                    <div class="summary-stats">
                        <div class="stat">
                            <div class="stat-value">{$totalPositions}</div>
                            <div class="stat-label">Total Positions</div>
                        </div>
                        <div class="stat">
                            <div class="stat-value" style="color: {if ($totalIssues = 0) then '#28a745' else '#dc3545'};">{$totalIssues}</div>
                            <div class="stat-label">Missing Fields</div>
                        </div>
                        <div class="stat">
                            <div class="stat-value" style="color: {if ($totalIssues = 0) then '#28a745' else '#ffc107'};">
                                {round(($totalPositions * 5 - $totalIssues) div ($totalPositions * 5) * 100)}%
                            </div>
                            <div class="stat-label">Completeness</div>
                        </div>
                    </div>

                    <table style="margin-top: 15px;">
                        <thead>
                            <tr>
                                <th>Required Field</th>
                                <th>Present</th>
                                <th>Missing</th>
                                <th>Completeness</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr>
                                <td>UniqueID</td>
                                <td class="field-present">{$totalPositions - $missingUniqueId}</td>
                                <td class="{if ($missingUniqueId > 0) then 'field-missing' else ''}">{$missingUniqueId}</td>
                                <td>
                                    <div class="completeness-bar">
                                        <div class="completeness-fill" style="width: {round(($totalPositions - $missingUniqueId) div $totalPositions * 100)}%;"></div>
                                    </div>
                                </td>
                            </tr>
                            <tr>
                                <td>Currency</td>
                                <td class="field-present">{$totalPositions - $missingCurrency}</td>
                                <td class="{if ($missingCurrency > 0) then 'field-missing' else ''}">{$missingCurrency}</td>
                                <td>
                                    <div class="completeness-bar">
                                        <div class="completeness-fill" style="width: {round(($totalPositions - $missingCurrency) div $totalPositions * 100)}%;"></div>
                                    </div>
                                </td>
                            </tr>
                            <tr>
                                <td>TotalValue/Amount</td>
                                <td class="field-present">{$totalPositions - $missingTotalValue}</td>
                                <td class="{if ($missingTotalValue > 0) then 'field-missing' else ''}">{$missingTotalValue}</td>
                                <td>
                                    <div class="completeness-bar">
                                        <div class="completeness-fill" style="width: {round(($totalPositions - $missingTotalValue) div $totalPositions * 100)}%;"></div>
                                    </div>
                                </td>
                            </tr>
                            <tr>
                                <td>TotalPercentage</td>
                                <td class="field-present">{$totalPositions - $missingTotalPct}</td>
                                <td class="{if ($missingTotalPct > 0) then 'field-missing' else ''}">{$missingTotalPct}</td>
                                <td>
                                    <div class="completeness-bar">
                                        <div class="completeness-fill" style="width: {round(($totalPositions - $missingTotalPct) div $totalPositions * 100)}%;"></div>
                                    </div>
                                </td>
                            </tr>
                            <tr>
                                <td>Identifiers (ISIN/OtherID)</td>
                                <td class="field-present">{$totalPositions - $missingIdentifiers}</td>
                                <td class="{if ($missingIdentifiers > 0) then 'field-missing' else ''}">{$missingIdentifiers}</td>
                                <td>
                                    <div class="completeness-bar">
                                        <div class="completeness-fill" style="width: {round(($totalPositions - $missingIdentifiers) div $totalPositions * 100)}%;"></div>
                                    </div>
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            }

            <p class="timestamp">Report generated: {current-dateTime()}</p>
        </div>
    </body>
</html>
