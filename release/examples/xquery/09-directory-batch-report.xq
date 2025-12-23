xquery version "3.1";

(:~
 : Directory Batch Report
 :
 : Purpose: Process multiple XML files and generate an aggregated report.
 : This query can analyze all FundsXML files in a directory.
 :
 : Usage:
 : - Set the $directory variable to point to your XML files folder
 : - Or use Saxon's collection() function with a directory URI
 : - Works with FreeXmlToolkit's XQuery execution panel
 :
 : Note: The collection() function behavior depends on the XQuery processor.
 : For Saxon, use: collection('file:///path/to/folder?select=*.xml')
 :
 : @author FreeXmlToolkit Examples
 : @version 1.0
 :)

declare namespace output = "http://www.w3.org/2010/xslt-xquery-serialization";
declare option output:method "html";
declare option output:html-version "5";

(: Configuration - adjust this path as needed :)
declare variable $local:SAMPLE_MODE := true(); (: Set to false when using collection() :)

(: Helper function to format amounts :)
declare function local:format-amount($amount as xs:decimal?) as xs:string {
    if (empty($amount)) then "N/A"
    else format-number($amount, "#,##0.00")
};

(: Helper function to analyze a single document :)
declare function local:analyze-document($doc as document-node()) as map(*) {
    let $controlData := $doc//ControlData
    let $funds := $doc//Fund

    let $totalFunds := count($funds)
    let $totalPositions := count($doc//Position)
    let $totalNav := sum($doc//TotalNetAssetValue/Amount/xs:decimal(.))

    let $currencies := distinct-values($doc//Position/Currency)
    let $navDate := ($doc//TotalAssetValue/NavDate)[1]/text()

    let $positionsWithIsin := count($doc//Position[Identifiers/ISIN])
    let $bondPositions := count($doc//Position[Bond])

    return map {
        "documentId": string($controlData/UniqueDocumentID),
        "contentDate": string($controlData/ContentDate),
        "supplier": string($controlData/DataSupplier/Name),
        "fundCount": $totalFunds,
        "positionCount": $totalPositions,
        "totalNav": $totalNav,
        "navDate": $navDate,
        "currencies": string-join($currencies, ", "),
        "currencyCount": count($currencies),
        "isinCoverage": round($positionsWithIsin div $totalPositions * 100),
        "bondPositions": $bondPositions,
        "fundNames": string-join($funds/Names/OfficialName/text(), "; ")
    }
};

(: Main processing :)
let $currentDoc := /
let $currentAnalysis := local:analyze-document(root($currentDoc))

(: In sample mode, we only analyze the current document
   In real batch mode, use collection() to get multiple documents :)

return
<html>
    <head>
        <title>FundsXML Batch Analysis Report</title>
        <style>
            body {{ font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                   margin: 20px; background: #f8f9fa; color: #212529; }}
            .container {{ max-width: 1400px; margin: 0 auto; }}
            h1 {{ color: #0d6efd; border-bottom: 3px solid #0d6efd; padding-bottom: 10px; }}
            h2 {{ color: #495057; margin-top: 30px; }}
            .summary-card {{ background: white; border-radius: 8px; padding: 20px;
                           margin: 20px 0; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }}
            .summary-stats {{ display: flex; gap: 20px; flex-wrap: wrap; }}
            .stat {{ flex: 1; min-width: 150px; text-align: center; padding: 20px;
                    background: #e9ecef; border-radius: 6px; }}
            .stat-value {{ font-size: 32px; font-weight: bold; color: #0d6efd; }}
            .stat-label {{ font-size: 12px; color: #6c757d; margin-top: 5px; }}
            table {{ width: 100%; border-collapse: collapse; background: white;
                    border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }}
            th {{ background: #0d6efd; color: white; padding: 12px; text-align: left; }}
            td {{ padding: 12px; border-bottom: 1px solid #dee2e6; }}
            tr:hover {{ background: #f8f9fa; }}
            .amount {{ text-align: right; font-family: monospace; }}
            .timestamp {{ color: #6c757d; font-size: 12px; margin-top: 20px; }}
            .info-box {{ background: #cff4fc; border-left: 4px solid #0dcaf0;
                        padding: 15px; margin: 20px 0; border-radius: 4px; }}
            .warning-box {{ background: #fff3cd; border-left: 4px solid #ffc107;
                           padding: 15px; margin: 20px 0; border-radius: 4px; }}
            .code-box {{ background: #2d2d2d; color: #f8f8f2; padding: 15px;
                        border-radius: 8px; font-family: monospace; overflow-x: auto;
                        margin: 15px 0; font-size: 13px; }}
            .code-comment {{ color: #75715e; }}
            .code-keyword {{ color: #66d9ef; }}
            .code-string {{ color: #e6db74; }}
        </style>
    </head>
    <body>
        <div class="container">
            <h1>FundsXML Batch Analysis Report</h1>

            {if ($local:SAMPLE_MODE) then
                <div class="warning-box">
                    <strong>Sample Mode:</strong> Currently analyzing the single input document.
                    To process multiple files, see the XQuery code for collection() usage examples.
                </div>
            else ()}

            <div class="info-box">
                <strong>Batch Processing Example:</strong>
                <p>This XQuery demonstrates how to analyze multiple FundsXML files at once.
                   Modify the collection URI pattern to match your file location.</p>
            </div>

            <h2>How to Use Collection-Based Batch Processing</h2>
            <div class="code-box">
                <span class="code-comment">(: Process all XML files in a directory :)</span><br/>
                <span class="code-keyword">let</span> $docs := <span class="code-keyword">collection</span>(<span class="code-string">'file:///path/to/xmlfiles?select=*.xml'</span>)<br/>
                <span class="code-keyword">for</span> $doc <span class="code-keyword">in</span> $docs<br/>
                <span class="code-keyword">return</span> local:analyze-document($doc)<br/>
                <br/>
                <span class="code-comment">(: Or use a specific file pattern :)</span><br/>
                <span class="code-keyword">let</span> $docs := <span class="code-keyword">collection</span>(<span class="code-string">'file:///path/to/xmlfiles?select=FundsXML*.xml'</span>)
            </div>

            <div class="summary-card">
                <h2>Aggregated Statistics</h2>
                <div class="summary-stats">
                    <div class="stat">
                        <div class="stat-value">1</div>
                        <div class="stat-label">Files Analyzed</div>
                    </div>
                    <div class="stat">
                        <div class="stat-value">{$currentAnalysis?fundCount}</div>
                        <div class="stat-label">Total Funds</div>
                    </div>
                    <div class="stat">
                        <div class="stat-value">{$currentAnalysis?positionCount}</div>
                        <div class="stat-label">Total Positions</div>
                    </div>
                    <div class="stat">
                        <div class="stat-value">{local:format-amount($currentAnalysis?totalNav)}</div>
                        <div class="stat-label">Total NAV</div>
                    </div>
                    <div class="stat">
                        <div class="stat-value">{$currentAnalysis?currencyCount}</div>
                        <div class="stat-label">Currencies</div>
                    </div>
                </div>
            </div>

            <h2>Document Details</h2>
            <table>
                <thead>
                    <tr>
                        <th>Document ID</th>
                        <th>Content Date</th>
                        <th>Supplier</th>
                        <th>Funds</th>
                        <th>Positions</th>
                        <th class="amount">Total NAV</th>
                        <th>ISIN Coverage</th>
                        <th>Bond Positions</th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td>{$currentAnalysis?documentId}</td>
                        <td>{$currentAnalysis?contentDate}</td>
                        <td>{$currentAnalysis?supplier}</td>
                        <td>{$currentAnalysis?fundCount}</td>
                        <td>{$currentAnalysis?positionCount}</td>
                        <td class="amount">{local:format-amount($currentAnalysis?totalNav)}</td>
                        <td>{$currentAnalysis?isinCoverage}%</td>
                        <td>{$currentAnalysis?bondPositions}</td>
                    </tr>
                </tbody>
            </table>

            <div class="summary-card">
                <h3>Fund Breakdown</h3>
                <table>
                    <thead>
                        <tr>
                            <th>Fund Name</th>
                            <th>Currency</th>
                            <th>NAV Date</th>
                            <th class="amount">Total NAV</th>
                            <th>Positions</th>
                        </tr>
                    </thead>
                    <tbody>
                    {
                        for $fund in $currentDoc//Fund
                        return
                        <tr>
                            <td>{$fund/Names/OfficialName/text()}</td>
                            <td>{$fund/Currency/text()}</td>
                            <td>{$fund/FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate/text()}</td>
                            <td class="amount">{local:format-amount(xs:decimal($fund/FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount))}</td>
                            <td>{count($fund/FundDynamicData/Portfolios/Portfolio/Positions/Position)}</td>
                        </tr>
                    }
                    </tbody>
                </table>
            </div>

            <div class="summary-card">
                <h3>Currency Distribution</h3>
                <table>
                    <thead>
                        <tr>
                            <th>Currency</th>
                            <th>Position Count</th>
                            <th class="amount">Total Value</th>
                            <th>% of Total</th>
                        </tr>
                    </thead>
                    <tbody>
                    {
                        let $totalValue := sum($currentDoc//Position/TotalValue/Amount/xs:decimal(.))
                        for $ccy in distinct-values($currentDoc//Position/Currency)
                        let $positions := $currentDoc//Position[Currency = $ccy]
                        let $ccyValue := sum($positions/TotalValue/Amount/xs:decimal(.))
                        order by $ccyValue descending
                        return
                        <tr>
                            <td>{$ccy}</td>
                            <td>{count($positions)}</td>
                            <td class="amount">{local:format-amount($ccyValue)}</td>
                            <td>{round($ccyValue div $totalValue * 10000) div 100}%</td>
                        </tr>
                    }
                    </tbody>
                </table>
            </div>

            <div class="summary-card">
                <h3>Asset Type Distribution</h3>
                <table>
                    <thead>
                        <tr>
                            <th>Asset Type</th>
                            <th>Position Count</th>
                            <th class="amount">Total Value</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr>
                            <td>Bonds</td>
                            <td>{count($currentDoc//Position[Bond])}</td>
                            <td class="amount">{local:format-amount(sum($currentDoc//Position[Bond]/TotalValue/Amount/xs:decimal(.)))}</td>
                        </tr>
                        <tr>
                            <td>Accounts/Cash</td>
                            <td>{count($currentDoc//Position[Account])}</td>
                            <td class="amount">{local:format-amount(sum($currentDoc//Position[Account]/TotalValue/Amount/xs:decimal(.)))}</td>
                        </tr>
                        <tr>
                            <td>Other</td>
                            <td>{count($currentDoc//Position[not(Bond) and not(Account)])}</td>
                            <td class="amount">{local:format-amount(sum($currentDoc//Position[not(Bond) and not(Account)]/TotalValue/Amount/xs:decimal(.)))}</td>
                        </tr>
                    </tbody>
                </table>
            </div>

            <p class="timestamp">Report generated: {current-dateTime()}</p>
        </div>
    </body>
</html>
