<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xs">

    <xsl:output method="html" version="5.0" encoding="UTF-8" indent="yes" omit-xml-declaration="yes"/>

    <xsl:key name="asset-by-id" match="/FundsXML4/AssetMasterData/Asset" use="UniqueID"/>

    <xsl:template match="/">
        <xsl:variable name="fund" select="(/FundsXML4/Funds/Fund | /FundsXML4/Funds/Fund/SingleFund | /FundsXML4/Funds/Fund/Subfunds/Subfund)[1]"/>
        <xsl:variable name="fundName" select="($fund/Names/OfficialName, /FundsXML4/Funds/Fund/Names/OfficialName, 'Unnamed Fund')[1]"/>
        <xsl:variable name="fundIsin" select="($fund/Identifiers/ISIN, /FundsXML4/Funds/Fund/Identifiers/ISIN, 'N/A')[1]"/>
        <xsl:variable name="fundCcy" select="($fund/Currency, /FundsXML4/Funds/Fund/Currency, 'EUR')[1]"/>
        <xsl:variable name="contentDate" select="(/FundsXML4/ControlData/ContentDate, 'N/A')[1]"/>

        <xsl:variable name="navRecord" select="($fund/FundDynamicData/TotalAssetValues/TotalAssetValue | /FundsXML4/Funds/Fund/FundDynamicData/TotalAssetValues/TotalAssetValue)[1]"/>
        <xsl:variable name="totalNav" select="number(($navRecord/TotalNetAssetValue/Amount, 0)[1])"/>
        <xsl:variable name="positions" select="$fund/FundDynamicData/Portfolios/Portfolio/Positions/Position | /FundsXML4/Funds/Fund/FundDynamicData/Portfolios/Portfolio/Positions/Position"/>
        <xsl:variable name="posCount" select="count($positions)"/>

        <html lang="en">
        <head>
            <meta charset="UTF-8"/>
            <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
            <title>Portfolio Composition Analysis - <xsl:value-of select="$fundName"/></title>
            <style>
                :root {
                    --primary: #0f172a;
                    --primary-light: #1e293b;
                    --accent: #2563eb;
                    --success: #16a34a;
                    --warning: #d97706;
                    --danger: #dc2626;
                    --bg: #f8fafc;
                    --surface: #ffffff;
                    --border: #e2e8f0;
                    --text: #1e293b;
                    --text-muted: #64748b;
                    --font: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                    --radius: 8px;
                }
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body { font-family: var(--font); background: var(--bg); color: var(--text); padding: 24px; line-height: 1.5; }
                .container { max-width: 1400px; margin: 0 auto; }
                
                .header {
                    background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius);
                    padding: 24px; margin-bottom: 24px; box-shadow: 0 1px 3px rgba(0,0,0,0.05);
                    display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 16px;
                }
                .header-title { font-size: 20px; font-weight: 800; color: var(--primary); }
                .meta-tags { display: flex; gap: 10px; margin-top: 6px; font-size: 13px; color: var(--text-muted); flex-wrap: wrap; }
                .meta-tag { background: #f1f5f9; padding: 3px 8px; border-radius: 4px; font-weight: 500; }

                .card { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius); margin-bottom: 24px; overflow: hidden; }
                .card-header { padding: 14px 20px; background: #fafafa; border-bottom: 1px solid var(--border); font-size: 15px; font-weight: 700; display: flex; justify-content: space-between; align-items: center; }
                .card-body { padding: 20px; }

                .table { width: 100%; border-collapse: collapse; font-size: 13px; }
                .table th { background: #f8fafc; padding: 10px 14px; border-bottom: 1px solid var(--border); text-align: left; font-weight: 600; color: var(--text-muted); cursor: pointer; }
                .table td { padding: 10px 14px; border-bottom: 1px solid var(--border); vertical-align: middle; }
                .table tr:hover { background-color: #f8fafc; }
                .num { text-align: right; font-variant-numeric: tabular-nums; }
                .mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; }

                .badge { display: inline-flex; align-items: center; padding: 3px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; text-transform: uppercase; }
                .badge-eq { background: #dbeafe; color: #1e40af; }
                .badge-bo { background: #fef3c7; color: #92400e; }
                .badge-fu { background: #f3e8ff; color: #6b21a8; }
                .badge-ca { background: #e0f2fe; color: #0369a1; }
                .badge-oth { background: #f1f5f9; color: #475569; }

                .search-bar { padding: 8px 12px; border: 1px solid var(--border); border-radius: 6px; font-size: 13px; width: 280px; }
                .footer { margin-top: 24px; padding-top: 14px; border-top: 1px solid var(--border); font-size: 12px; color: var(--text-muted); display: flex; justify-content: space-between; }
            </style>
        </head>
        <body>
            <div class="container">
                <header class="header">
                    <div>
                        <h1 class="header-title">📋 Detailed Portfolio Composition Analysis</h1>
                        <div class="meta-tags">
                            <span class="meta-tag"><strong>Fund:</strong> <xsl:value-of select="$fundName"/></span>
                            <span class="meta-tag"><strong>ISIN:</strong> <xsl:value-of select="$fundIsin"/></span>
                            <span class="meta-tag"><strong>Valuation Date:</strong> <xsl:value-of select="$contentDate"/></span>
                            <span class="meta-tag"><strong>Fund Base Currency:</strong> <xsl:value-of select="$fundCcy"/></span>
                            <span class="meta-tag"><strong>Total Holdings:</strong> <xsl:value-of select="$posCount"/></span>
                        </div>
                    </div>
                    <div>
                        <button onclick="window.print()" style="background: var(--primary); color: white; border: none; padding: 8px 16px; border-radius: 6px; font-size: 13px; font-weight: 600; cursor: pointer;">🖨️ Export PDF</button>
                    </div>
                </header>

                <div class="card">
                    <div class="card-header">
                        <span>Portfolio Positions Breakdown (<xsl:value-of select="$posCount"/>)</span>
                        <input type="text" id="liveSearch" class="search-bar" placeholder="🔍 Instant search..." onkeyup="doSearch()"/>
                    </div>
                    <div class="card-body" style="padding: 0;">
                        <table class="table" id="posTable">
                            <thead>
                                <tr onclick="sortTable(event)">
                                    <th>#</th>
                                    <th>Holding Description</th>
                                    <th>ISIN</th>
                                    <th>Asset Type</th>
                                    <th>Currency</th>
                                    <th class="num">Holding / Units</th>
                                    <th class="num">Market Value (<xsl:value-of select="$fundCcy"/>)</th>
                                    <th class="num">Weight (%)</th>
                                </tr>
                            </thead>
                            <tbody>
                                <xsl:for-each select="$positions">
                                    <xsl:variable name="linkedAsset" select="key('asset-by-id', UniqueID)"/>
                                    <xsl:variable name="posName" select="($linkedAsset/Name, AssetDetails/*/Name, UniqueID)[1]"/>
                                    <xsl:variable name="posIsin" select="(Identifiers/ISIN, $linkedAsset/Identifiers/ISIN, 'N/A')[1]"/>
                                    <xsl:variable name="posType" select="($linkedAsset/AssetType, AssetType, 'OTH')[1]"/>
                                    <xsl:variable name="posVal" select="number(TotalValue/Amount)"/>
                                    <xsl:variable name="posWeight" select="if ($totalNav > 0) then ($posVal div $totalNav * 100) else number(TotalPercentage)"/>
                                    <tr>
                                        <td><xsl:value-of select="position()"/></td>
                                        <td><strong><xsl:value-of select="$posName"/></strong></td>
                                        <td class="mono"><xsl:value-of select="$posIsin"/></td>
                                        <td>
                                            <span class="badge">
                                                <xsl:attribute name="class">
                                                    <xsl:choose>
                                                        <xsl:when test="$posType = 'EQ' or $posType = 'SH' or $posType = 'ST'">badge badge-eq</xsl:when>
                                                        <xsl:when test="$posType = 'BO' or $posType = 'BD'">badge badge-bo</xsl:when>
                                                        <xsl:when test="$posType = 'FU' or $posType = 'CI'">badge badge-fu</xsl:when>
                                                        <xsl:when test="$posType = 'CA' or $posType = 'MM'">badge badge-ca</xsl:when>
                                                        <xsl:otherwise>badge badge-oth</xsl:otherwise>
                                                    </xsl:choose>
                                                </xsl:attribute>
                                                <xsl:value-of select="$posType"/>
                                            </span>
                                        </td>
                                        <td><xsl:value-of select="Currency"/></td>
                                        <td class="num"><xsl:value-of select="format-number(number(Holdings/Units | Holdings/ParValue | TotalValue/Amount), '#,##0.##')"/></td>
                                        <td class="num"><xsl:value-of select="format-number($posVal, '#,##0.00')"/></td>
                                        <td class="num"><strong><xsl:value-of select="format-number($posWeight, '0.00')"/>%</strong></td>
                                    </tr>
                                </xsl:for-each>
                            </tbody>
                        </table>
                    </div>
                </div>

                <footer class="footer">
                    <div>Transformed with <strong>FreeXmlToolkit</strong> &#8226; XSLT 2.0 Engine (Saxon-HE)</div>
                    <div>Schema: FundsXML 4.2.9 Compliant</div>
                </footer>
            </div>

            <script>
                function doSearch() {
                    var q = document.getElementById('liveSearch').value.toLowerCase();
                    var rows = document.querySelectorAll('#posTable tbody tr');
                    rows.forEach(function(r) {
                        r.style.display = r.innerText.toLowerCase().indexOf(q) &gt;= 0 ? '' : 'none';
                    });
                }

                var sortAsc = true;
                function sortTable(e) {
                    var th = e.target.closest('th');
                    if (!th) return;
                    var table = th.closest('table');
                    var tbody = table.querySelector('tbody');
                    var colIndex = Array.from(th.parentNode.children).indexOf(th);
                    var rows = Array.from(tbody.querySelectorAll('tr'));
                    
                    rows.sort(function(a, b) {
                        var aVal = a.children[colIndex].innerText.trim().replace(/[%€$,]/g, '');
                        var bVal = b.children[colIndex].innerText.trim().replace(/[%€$,]/g, '');
                        var aNum = parseFloat(aVal);
                        var bNum = parseFloat(bVal);
                        if (!isNaN(aNum) &amp;&amp; !isNaN(bNum)) {
                            return sortAsc ? aNum - bNum : bNum - aNum;
                        }
                        return sortAsc ? aVal.localeCompare(bVal) : bVal.localeCompare(aVal);
                    });
                    sortAsc = !sortAsc;
                    rows.forEach(function(r) { tbody.appendChild(r); });
                }
            </script>
        </body>
        </html>
    </xsl:template>
</xsl:stylesheet>
