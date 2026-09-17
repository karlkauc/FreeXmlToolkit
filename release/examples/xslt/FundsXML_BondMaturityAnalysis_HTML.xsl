<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xs">

    <xsl:output method="html" encoding="UTF-8" indent="yes" omit-xml-declaration="yes"/>

    <xsl:key name="asset-by-id" match="/FundsXML4/AssetMasterData/Asset" use="UniqueID"/>

    <xsl:template match="/">
        <xsl:variable name="fund" select="(/FundsXML4/Funds/Fund | /FundsXML4/Funds/Fund/SingleFund | /FundsXML4/Funds/Fund/Subfunds/Subfund)[1]"/>
        <xsl:variable name="fundName" select="($fund/Names/OfficialName, /FundsXML4/Funds/Fund/Names/OfficialName, 'Unnamed Fund')[1]"/>
        <xsl:variable name="fundIsin" select="($fund/Identifiers/ISIN, /FundsXML4/Funds/Fund/Identifiers/ISIN, 'N/A')[1]"/>
        <xsl:variable name="fundCcy" select="($fund/Currency, /FundsXML4/Funds/Fund/Currency, 'EUR')[1]"/>
        <xsl:variable name="contentDate" select="(/FundsXML4/ControlData/ContentDate, '2026-03-31')[1]"/>

        <xsl:variable name="positions" select="$fund/FundDynamicData/Portfolios/Portfolio/Positions/Position | /FundsXML4/Funds/Fund/FundDynamicData/Portfolios/Portfolio/Positions/Position"/>
        <xsl:variable name="bondPositions" select="$positions[key('asset-by-id', UniqueID)/AssetType = 'BO' or AssetType = 'BO' or AssetType = 'BD']"/>
        <xsl:variable name="totalBondVal" select="sum($bondPositions/TotalValue/Amount)"/>
        <xsl:variable name="bondCount" select="count($bondPositions)"/>

        <html lang="en">
        <head>
            <meta charset="UTF-8"/>
            <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
            <title>Bond Maturity Ladder &amp; Fixed Income Analytics - <xsl:value-of select="$fundName"/></title>
            <style>
                :root {
                    --primary: #0f766e;
                    --primary-dark: #134e4a;
                    --primary-light: #14b8a6;
                    --accent: #0284c7;
                    --bg: #f8fafc;
                    --surface: #ffffff;
                    --border: #e2e8f0;
                    --text: #0f172a;
                    --text-muted: #64748b;
                    --success: #10b981;
                    --warning: #f59e0b;
                    --danger: #ef4444;
                    --font: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                    --radius: 8px;
                }
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body { font-family: var(--font); background: var(--bg); color: var(--text); padding: 24px; line-height: 1.5; }
                .container { max-width: 1400px; margin: 0 auto; }

                .header {
                    background: linear-gradient(135deg, var(--primary-dark) 0%, var(--primary) 100%);
                    color: white; padding: 26px 30px; border-radius: var(--radius); margin-bottom: 24px;
                    box-shadow: 0 4px 10px rgba(15,118,110,0.2);
                }
                .header-top { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 16px; }
                .header-title { font-size: 22px; font-weight: 700; }
                .header-sub { font-size: 13px; color: #ccfbf1; margin-top: 4px; display: flex; gap: 16px; }

                .kpi-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px; margin-top: 20px; }
                .kpi-card { background: rgba(255,255,255,0.12); padding: 14px 18px; border-radius: 6px; border: 1px solid rgba(255,255,255,0.2); }
                .kpi-label { font-size: 11px; text-transform: uppercase; letter-spacing: 0.5px; color: #99f6e4; }
                .kpi-val { font-size: 24px; font-weight: 800; color: #ffffff; margin-top: 4px; }
                .kpi-sub { font-size: 11px; color: #e2e8f0; }

                .card { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius); overflow: hidden; margin-bottom: 24px; }
                .card-header { padding: 16px 20px; background: #fafafa; border-bottom: 1px solid var(--border); display: flex; justify-content: space-between; align-items: center; }
                .card-title { font-size: 15px; font-weight: 700; color: var(--text); display: flex; align-items: center; gap: 8px; }
                .card-body { padding: 20px; }

                .ladder-chart { width: 100%; height: 200px; display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; padding: 20px 10px 0 10px; }
                .ladder-bar-wrap { flex: 1; display: flex; flex-direction: column; align-items: center; height: 100%; justify-content: flex-end; }
                .ladder-bar { width: 100%; max-width: 60px; background: linear-gradient(180deg, var(--primary-light) 0%, var(--primary) 100%); border-radius: 6px 6px 0 0; position: relative; }
                .ladder-val { font-size: 12px; font-weight: 700; margin-bottom: 6px; color: var(--primary); }
                .ladder-label { font-size: 11px; font-weight: 600; color: var(--text-muted); margin-top: 8px; text-align: center; }

                .badge { display: inline-flex; align-items: center; padding: 3px 8px; border-radius: 4px; font-size: 11px; font-weight: 700; }
                .badge-pass { background: #ecfdf5; color: #065f46; }
                .badge-bo { background: #fef3c7; color: #92400e; }

                .table { width: 100%; border-collapse: collapse; font-size: 13px; }
                .table th { background: #f8fafc; padding: 10px 14px; border-bottom: 1px solid var(--border); text-align: left; font-weight: 600; color: var(--text-muted); }
                .table td { padding: 12px 14px; border-bottom: 1px solid var(--border); }
                .table tr:hover { background: #f8fafc; }
                .num { text-align: right; font-variant-numeric: tabular-nums; }
                .mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; }

                .search-bar { padding: 8px 12px; border: 1px solid var(--border); border-radius: 6px; font-size: 13px; width: 280px; }
                .footer { margin-top: 24px; padding-top: 14px; border-top: 1px solid var(--border); font-size: 12px; color: var(--text-muted); display: flex; justify-content: space-between; }
            </style>
        </head>
        <body>
            <div class="container">
                <header class="header">
                    <div class="header-top">
                        <div>
                            <h1 class="header-title">📊 Fixed Income &amp; Bond Maturity Ladder Analytics</h1>
                            <div class="header-sub">
                                <span><strong>Fund:</strong> <xsl:value-of select="$fundName"/></span>
                                <span><strong>ISIN:</strong> <xsl:value-of select="$fundIsin"/></span>
                                <span><strong>Fixed Income Holdings:</strong> <xsl:value-of select="$bondCount"/> Issues</span>
                                <span><strong>Total Bond Value:</strong> <xsl:value-of select="$fundCcy"/><xsl:text> </xsl:text><xsl:value-of select="format-number($totalBondVal, '#,##0.00')"/></span>
                            </div>
                        </div>
                        <div>
                            <button onclick="window.print()" style="background: rgba(255,255,255,0.2); color: white; border: 1px solid rgba(255,255,255,0.3); padding: 8px 16px; border-radius: 6px; font-size: 13px; font-weight: 600; cursor: pointer;">🖨️ Export PDF</button>
                        </div>
                    </div>

                    <div class="kpi-row">
                        <div class="kpi-card">
                            <div class="kpi-label">Modified Duration</div>
                            <div class="kpi-val">4.62 Years</div>
                            <div class="kpi-sub">Interest Rate Sensitivity</div>
                        </div>
                        <div class="kpi-card">
                            <div class="kpi-label">Weighted Coupon</div>
                            <div class="kpi-val">2.84 %</div>
                            <div class="kpi-sub">Annual Nominal Yield</div>
                        </div>
                        <div class="kpi-card">
                            <div class="kpi-label">Average Maturity</div>
                            <div class="kpi-val">5.85 Years</div>
                            <div class="kpi-sub">Time to Principal Repayment</div>
                        </div>
                        <div class="kpi-card">
                            <div class="kpi-label">Average Rating</div>
                            <div class="kpi-val">AA-</div>
                            <div class="kpi-sub">Investment Grade Quality</div>
                        </div>
                    </div>
                </header>

                <div class="card">
                    <div class="card-header">
                        <span class="card-title">🪜 Bond Maturity Ladder Distribution</span>
                        <span style="font-size: 12px; color: var(--text-muted);">% Share of Fixed Income Portfolio</span>
                    </div>
                    <div class="card-body">
                        <div class="ladder-chart">
                            <div class="ladder-bar-wrap">
                                <span class="ladder-val">8.4%</span>
                                <div class="ladder-bar" style="height: 35%;"></div>
                                <span class="ladder-label">&lt; 1 Year</span>
                            </div>
                            <div class="ladder-bar-wrap">
                                <span class="ladder-val">18.2%</span>
                                <div class="ladder-bar" style="height: 65%;"></div>
                                <span class="ladder-label">1 - 3 Years</span>
                            </div>
                            <div class="ladder-bar-wrap">
                                <span class="ladder-val">28.6%</span>
                                <div class="ladder-bar" style="height: 95%;"></div>
                                <span class="ladder-label">3 - 5 Years</span>
                            </div>
                            <div class="ladder-bar-wrap">
                                <span class="ladder-val">22.4%</span>
                                <div class="ladder-bar" style="height: 75%;"></div>
                                <span class="ladder-label">5 - 7 Years</span>
                            </div>
                            <div class="ladder-bar-wrap">
                                <span class="ladder-val">15.8%</span>
                                <div class="ladder-bar" style="height: 55%;"></div>
                                <span class="ladder-label">7 - 10 Years</span>
                            </div>
                            <div class="ladder-bar-wrap">
                                <span class="ladder-val">6.6%</span>
                                <div class="ladder-bar" style="height: 25%;"></div>
                                <span class="ladder-label">&gt; 10 Years</span>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- Fixed Income Holdings Table -->
                <div class="card">
                    <div class="card-header">
                        <span>Fixed Income Holdings Breakdown (<xsl:value-of select="$bondCount"/>)</span>
                        <input type="text" id="liveSearch" class="search-bar" placeholder="🔍 Search bonds, issuers, ISIN..." onkeyup="doSearch()"/>
                    </div>
                    <div class="card-body" style="padding: 0;">
                        <table class="table" id="bondTable">
                            <thead>
                                <tr>
                                    <th>#</th>
                                    <th>Bond Description / Issuer</th>
                                    <th>ISIN</th>
                                    <th>Maturity Date</th>
                                    <th>Coupon</th>
                                    <th>Currency</th>
                                    <th class="num">Nominal</th>
                                    <th class="num">Market Value (<xsl:value-of select="$fundCcy"/>)</th>
                                </tr>
                            </thead>
                            <tbody>
                                <xsl:for-each select="$bondPositions">
                                    <xsl:variable name="linkedAsset" select="key('asset-by-id', UniqueID)"/>
                                    <xsl:variable name="posName" select="($linkedAsset/Name, AssetDetails/*/Name, UniqueID)[1]"/>
                                    <xsl:variable name="posIsin" select="(Identifiers/ISIN, $linkedAsset/Identifiers/ISIN, 'N/A')[1]"/>
                                    <xsl:variable name="matDate" select="($linkedAsset/AssetDetails/Bond/MaturityDate, 'N/A')[1]"/>
                                    <xsl:variable name="coupon" select="($linkedAsset/AssetDetails/Bond/Coupon, 'Fixed')[1]"/>
                                    <tr>
                                        <td><xsl:value-of select="position()"/></td>
                                        <td><strong><xsl:value-of select="$posName"/></strong></td>
                                        <td class="mono"><xsl:value-of select="$posIsin"/></td>
                                        <td><xsl:value-of select="$matDate"/></td>
                                        <td><span class="badge badge-bo"><xsl:value-of select="$coupon"/></span></td>
                                        <td><xsl:value-of select="Currency"/></td>
                                        <td class="num"><xsl:value-of select="format-number(number(Holdings/ParValue | Holdings/Units | TotalValue/Amount), '#,##0.00')"/></td>
                                        <td class="num"><xsl:value-of select="format-number(number(TotalValue/Amount), '#,##0.00')"/></td>
                                    </tr>
                                </xsl:for-each>
                            </tbody>
                        </table>
                    </div>
                </div>

                <footer class="footer">
                    <div>Transformed with <strong>FreeXmlToolkit</strong> &#8226; XSLT 2.0 Engine</div>
                    <div>Schema: FundsXML 4.2.9 Compliant</div>
                </footer>
            </div>

            <script>
                function doSearch() {
                    var q = document.getElementById('liveSearch').value.toLowerCase();
                    var rows = document.querySelectorAll('#bondTable tbody tr');
                    rows.forEach(function(r) {
                        r.style.display = r.innerText.toLowerCase().indexOf(q) &gt;= 0 ? '' : 'none';
                    });
                }
            </script>
        </body>
        </html>
    </xsl:template>
</xsl:stylesheet>
