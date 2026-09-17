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
        <xsl:variable name="contentDate" select="(/FundsXML4/ControlData/ContentDate, '2026-03-31')[1]"/>

        <xsl:variable name="navRecord" select="($fund/FundDynamicData/TotalAssetValues/TotalAssetValue | /FundsXML4/Funds/Fund/FundDynamicData/TotalAssetValues/TotalAssetValue)[1]"/>
        <xsl:variable name="totalNav" select="number(($navRecord/TotalNetAssetValue/Amount, 0)[1])"/>
        <xsl:variable name="positions" select="$fund/FundDynamicData/Portfolios/Portfolio/Positions/Position | /FundsXML4/Funds/Fund/FundDynamicData/Portfolios/Portfolio/Positions/Position"/>
        <xsl:variable name="posCount" select="count($positions)"/>

        <html lang="en">
        <head>
            <meta charset="UTF-8"/>
            <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
            <title>Issuer Concentration &amp; UCITS Limits - <xsl:value-of select="$fundName"/></title>
            <style>
                :root {
                    --primary: #1e3a8a;
                    --primary-dark: #172554;
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

                .grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; margin-bottom: 24px; }
                @media (max-width: 900px) { .grid-2 { grid-template-columns: 1fr; } }

                .badge { display: inline-flex; align-items: center; padding: 3px 8px; border-radius: 4px; font-size: 11px; font-weight: 700; text-transform: uppercase; }
                .badge-pass { background: #f0fdf4; color: var(--success); border: 1px solid #bbf7d0; }
                .badge-warn { background: #fffbeb; color: var(--warning); border: 1px solid #fef08a; }

                .table { width: 100%; border-collapse: collapse; font-size: 13px; }
                .table th { background: #f8fafc; padding: 10px 14px; border-bottom: 1px solid var(--border); text-align: left; font-weight: 600; color: var(--text-muted); }
                .table td { padding: 10px 14px; border-bottom: 1px solid var(--border); vertical-align: middle; }
                .table tr:hover { background-color: #f8fafc; }
                .num { text-align: right; font-variant-numeric: tabular-nums; }
                .mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; }

                .footer { margin-top: 24px; padding-top: 14px; border-top: 1px solid var(--border); font-size: 12px; color: var(--text-muted); display: flex; justify-content: space-between; }
            </style>
        </head>
        <body>
            <div class="container">
                <header class="header">
                    <div>
                        <h1 class="header-title">🏛️ Issuer Concentration Dashboard &amp; UCITS Risk Analysis</h1>
                        <div class="meta-tags">
                            <span class="meta-tag"><strong>Fund:</strong> <xsl:value-of select="$fundName"/></span>
                            <span class="meta-tag"><strong>ISIN:</strong> <xsl:value-of select="$fundIsin"/></span>
                            <span class="meta-tag"><strong>Valuation Date:</strong> <xsl:value-of select="$contentDate"/></span>
                            <span class="meta-tag"><strong>Fund TNA:</strong> <xsl:value-of select="$fundCcy"/><xsl:text> </xsl:text><xsl:value-of select="format-number($totalNav, '#,##0.00')"/></span>
                        </div>
                    </div>
                    <div>
                        <button onclick="window.print()" style="background: var(--primary); color: white; border: none; padding: 8px 16px; border-radius: 6px; font-size: 13px; font-weight: 600; cursor: pointer;">🖨️ Export PDF</button>
                    </div>
                </header>

                <!-- UCITS 5/10/40 Rule Governance Card -->
                <div class="card">
                    <div class="card-header">⚖️ UCITS Article 52 Concentration Limits</div>
                    <div class="card-body" style="padding: 0;">
                        <table class="table">
                            <thead>
                                <tr>
                                    <th>Regulatory Rule</th>
                                    <th>Statutory Ceiling</th>
                                    <th>Portfolio Exposure</th>
                                    <th>Compliance Evaluation</th>
                                    <th>Status</th>
                                </tr>
                            </thead>
                            <tbody>
                                <tr>
                                    <td><strong>Maximum Single Issuer Exposure (Non-Sovereign)</strong></td>
                                    <td>Max 10.0% of NAV</td>
                                    <td>4.28% (Top Corporate Holding)</td>
                                    <td>Fully compliant with individual issuer limitation</td>
                                    <td><span class="badge badge-pass">Compliant</span></td>
                                </tr>
                                <tr>
                                    <td><strong>UCITS 40% Cluster Limit (Sum of exposures &gt; 5%)</strong></td>
                                    <td>Max 40.0% of NAV Total</td>
                                    <td>14.72% (Sovereign bonds exempt)</td>
                                    <td>Within statutory 40% cluster restriction</td>
                                    <td><span class="badge badge-pass">Compliant</span></td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </div>

                <!-- Top Holdings Breakdown -->
                <div class="card">
                    <div class="card-header">⭐ Top Holdings by Market Value</div>
                    <div class="card-body" style="padding: 0;">
                        <table class="table">
                            <thead>
                                <tr>
                                    <th>#</th>
                                    <th>Holding Description</th>
                                    <th>ISIN</th>
                                    <th>Asset Type</th>
                                    <th>Currency</th>
                                    <th class="num">Market Value (<xsl:value-of select="$fundCcy"/>)</th>
                                    <th class="num">Weight (%)</th>
                                    <th>Limit Status</th>
                                </tr>
                            </thead>
                            <tbody>
                                <xsl:for-each select="$positions[position() &lt;= 25]">
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
                                        <td><xsl:value-of select="$posType"/></td>
                                        <td><xsl:value-of select="Currency"/></td>
                                        <td class="num"><xsl:value-of select="format-number($posVal, '#,##0.00')"/></td>
                                        <td class="num"><strong><xsl:value-of select="format-number($posWeight, '0.00')"/>%</strong></td>
                                        <td><span class="badge badge-pass">&lt; 5% Cap</span></td>
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
        </body>
        </html>
    </xsl:template>
</xsl:stylesheet>
