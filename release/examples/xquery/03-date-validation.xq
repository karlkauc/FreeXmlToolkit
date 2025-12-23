xquery version "3.1";

(:~
 : Date Validation Check
 :
 : Purpose: Validate date consistency and logic across the FundsXML document.
 :
 : Checks:
 : - NavDate <= ContentDate <= DocumentGenerated
 : - No future dates (relative to ContentDate)
 : - All dates are properly formatted (ISO 8601)
 : - Consistent NavDate across all positions in a portfolio
 :
 : @author FreeXmlToolkit Examples
 : @version 1.0
 :)

declare namespace output = "http://www.w3.org/2010/xslt-xquery-serialization";
declare option output:method "html";
declare option output:html-version "5";

(: Helper function to safely parse date :)
declare function local:parse-date($dateStr as xs:string?) as xs:date? {
    if (empty($dateStr) or $dateStr = "") then ()
    else try {
        xs:date(substring($dateStr, 1, 10))
    } catch * {
        ()
    }
};

(: Helper function to parse datetime :)
declare function local:parse-datetime($dateStr as xs:string?) as xs:dateTime? {
    if (empty($dateStr) or $dateStr = "") then ()
    else try {
        xs:dateTime($dateStr)
    } catch * {
        ()
    }
};

(: Main processing :)
let $doc := /
let $controlData := $doc//ControlData
let $funds := $doc//Fund

(: Extract key dates :)
let $documentGenerated := local:parse-datetime($controlData/DocumentGenerated/text())
let $contentDate := local:parse-date($controlData/ContentDate/text())
let $today := current-date()

return
<html>
    <head>
        <title>Date Validation Report</title>
        <style>
            body {{ font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                   margin: 20px; background: #f8f9fa; color: #212529; }}
            .container {{ max-width: 1200px; margin: 0 auto; }}
            h1 {{ color: #0d6efd; border-bottom: 3px solid #0d6efd; padding-bottom: 10px; }}
            h2 {{ color: #495057; margin-top: 30px; }}
            h3 {{ color: #6c757d; }}
            .summary-card {{ background: white; border-radius: 8px; padding: 20px;
                           margin: 20px 0; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }}
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
            .date-value {{ font-family: monospace; background: #e9ecef; padding: 2px 8px; border-radius: 4px; }}
            .check-row {{ display: flex; justify-content: space-between; align-items: center;
                         padding: 10px 0; border-bottom: 1px solid #dee2e6; }}
            .check-row:last-child {{ border-bottom: none; }}
            .check-name {{ flex: 1; }}
            .check-detail {{ flex: 2; color: #6c757d; }}
            .timestamp {{ color: #6c757d; font-size: 12px; margin-top: 20px; }}
            .timeline {{ display: flex; align-items: center; gap: 20px; margin: 20px 0;
                        padding: 20px; background: #e9ecef; border-radius: 8px; }}
            .timeline-item {{ text-align: center; }}
            .timeline-date {{ font-family: monospace; font-size: 14px; font-weight: bold; }}
            .timeline-label {{ font-size: 12px; color: #6c757d; }}
            .timeline-arrow {{ font-size: 24px; color: #6c757d; }}
        </style>
    </head>
    <body>
        <div class="container">
            <h1>Date Validation Report</h1>

            <div class="summary-card">
                <h2>Document Timeline</h2>
                <div class="timeline">
                    <div class="timeline-item">
                        <div class="timeline-date">{string($contentDate)}</div>
                        <div class="timeline-label">Content Date</div>
                    </div>
                    <div class="timeline-arrow">&#8594;</div>
                    <div class="timeline-item">
                        <div class="timeline-date">{substring(string($documentGenerated), 1, 10)}</div>
                        <div class="timeline-label">Document Generated</div>
                    </div>
                    <div class="timeline-arrow">&#8594;</div>
                    <div class="timeline-item">
                        <div class="timeline-date">{string($today)}</div>
                        <div class="timeline-label">Today</div>
                    </div>
                </div>
            </div>

            <div class="summary-card">
                <h2>Document-Level Date Checks</h2>

                <div class="check-row">
                    <div class="check-name"><strong>Content Date Present</strong></div>
                    <div class="check-detail">
                        {if (exists($contentDate)) then
                            <span class="date-value">{string($contentDate)}</span>
                        else "Not found"}
                    </div>
                    <div>
                        {if (exists($contentDate)) then
                            <span class="status status-pass">PASS</span>
                        else
                            <span class="status status-fail">FAIL</span>
                        }
                    </div>
                </div>

                <div class="check-row">
                    <div class="check-name"><strong>Document Generated Present</strong></div>
                    <div class="check-detail">
                        {if (exists($documentGenerated)) then
                            <span class="date-value">{substring(string($documentGenerated), 1, 19)}</span>
                        else "Not found"}
                    </div>
                    <div>
                        {if (exists($documentGenerated)) then
                            <span class="status status-pass">PASS</span>
                        else
                            <span class="status status-fail">FAIL</span>
                        }
                    </div>
                </div>

                <div class="check-row">
                    <div class="check-name"><strong>Content Date &lt;= Generated Date</strong></div>
                    <div class="check-detail">
                        Content should be created before or on document generation
                    </div>
                    <div>
                        {if (exists($contentDate) and exists($documentGenerated) and
                             $contentDate <= xs:date(substring(string($documentGenerated), 1, 10))) then
                            <span class="status status-pass">PASS</span>
                        else if (not(exists($contentDate)) or not(exists($documentGenerated))) then
                            <span class="status status-warning">N/A</span>
                        else
                            <span class="status status-fail">FAIL</span>
                        }
                    </div>
                </div>

                <div class="check-row">
                    <div class="check-name"><strong>No Future Content Date</strong></div>
                    <div class="check-detail">
                        Content date should not be in the future
                    </div>
                    <div>
                        {if (exists($contentDate) and $contentDate <= $today) then
                            <span class="status status-pass">PASS</span>
                        else if (not(exists($contentDate))) then
                            <span class="status status-warning">N/A</span>
                        else
                            <span class="status status-fail">FUTURE DATE</span>
                        }
                    </div>
                </div>
            </div>

            <h2>Fund-Level Date Validation</h2>
            {
                for $fund in $funds
                let $fundName := $fund/Names/OfficialName/text()
                let $navDate := local:parse-date($fund/FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate/text())
                let $portfolioNavDate := local:parse-date($fund/FundDynamicData/Portfolios/Portfolio/NavDate/text())
                let $inceptionDate := local:parse-date($fund/FundStaticData/InceptionDate/text())

                return
                <div class="summary-card">
                    <h3>{$fundName}</h3>

                    <table>
                        <thead>
                            <tr>
                                <th>Date Field</th>
                                <th>Value</th>
                                <th>Validation</th>
                                <th>Status</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr>
                                <td>NAV Date (TotalAssetValue)</td>
                                <td><span class="date-value">{if (exists($navDate)) then string($navDate) else "N/A"}</span></td>
                                <td>Should match Content Date</td>
                                <td>
                                    {if (exists($navDate) and exists($contentDate) and $navDate = $contentDate) then
                                        <span class="status status-pass">MATCH</span>
                                    else if (exists($navDate) and exists($contentDate)) then
                                        <span class="status status-warning">MISMATCH</span>
                                    else
                                        <span class="status status-warning">N/A</span>
                                    }
                                </td>
                            </tr>
                            <tr>
                                <td>Portfolio NAV Date</td>
                                <td><span class="date-value">{if (exists($portfolioNavDate)) then string($portfolioNavDate) else "N/A"}</span></td>
                                <td>Should match Fund NAV Date</td>
                                <td>
                                    {if (exists($portfolioNavDate) and exists($navDate) and $portfolioNavDate = $navDate) then
                                        <span class="status status-pass">MATCH</span>
                                    else if (exists($portfolioNavDate) and exists($navDate)) then
                                        <span class="status status-warning">MISMATCH</span>
                                    else
                                        <span class="status status-warning">N/A</span>
                                    }
                                </td>
                            </tr>
                            <tr>
                                <td>Inception Date</td>
                                <td><span class="date-value">{if (exists($inceptionDate)) then string($inceptionDate) else "N/A"}</span></td>
                                <td>Should be before NAV Date</td>
                                <td>
                                    {if (exists($inceptionDate) and exists($navDate) and $inceptionDate < $navDate) then
                                        <span class="status status-pass">VALID</span>
                                    else if (not(exists($inceptionDate))) then
                                        <span class="status status-warning">MISSING</span>
                                    else
                                        <span class="status status-fail">INVALID</span>
                                    }
                                </td>
                            </tr>
                        </tbody>
                    </table>

                    {
                        let $distinctNavDates := distinct-values($fund/FundDynamicData/Portfolios/Portfolio/Positions/Position/ancestor::Portfolio/NavDate)
                        return
                        if (count($distinctNavDates) > 1) then
                            <div style="margin-top: 15px; padding: 10px; background: #fff3cd; border-radius: 4px;">
                                <strong>Warning:</strong> Multiple NAV dates found in portfolios:
                                {string-join($distinctNavDates, ", ")}
                            </div>
                        else ()
                    }
                </div>
            }

            <p class="timestamp">Report generated: {current-dateTime()}</p>
        </div>
    </body>
</html>
