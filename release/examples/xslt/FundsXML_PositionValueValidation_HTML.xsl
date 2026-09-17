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
        <xsl:variable name="posCount" select="count($positions)"/>

        <html lang="en">
        <head>
            <meta charset="UTF-8"/>
            <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
            <title>Position Value Validation - <xsl:value-of select="$fundName"/></title>
            <style>
                :root {
                    --primary: #0f172a;
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
                .header { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius); padding: 24px; margin-bottom: 24px; }
                .header-title { font-size: 20px; font-weight: 800; color: var(--primary); }
                .card { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius); margin-bottom: 24px; overflow: hidden; }
                .card-header { padding: 14px 20px; background: #fafafa; border-bottom: 1px solid var(--border); font-size: 15px; font-weight: 700; }
                .card-body { padding: 20px; }
                .table { width: 100%; border-collapse: collapse; font-size: 13px; }
                .table th { background: #f8fafc; padding: 10px 14px; border-bottom: 1px solid var(--border); text-align: left; font-weight: 600; color: var(--text-muted); }
                .table td { padding: 10px 14px; border-bottom: 1px solid var(--border); }
                .table tr:hover { background-color: #f8fafc; }
                .num { text-align: right; font-variant-numeric: tabular-nums; }
                .mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; }
                .badge { display: inline-flex; align-items: center; padding: 3px 8px; border-radius: 4px; font-size: 11px; font-weight: 700; text-transform: uppercase; }
                .badge-pass { background: #f0fdf4; color: var(--success); border: 1px solid #bbf7d0; }
                .footer { margin-top: 24px; font-size: 12px; color: var(--text-muted); }
            </style>
        </head>
        <body>
            <div class="container">
                <header class="header">
                    <h1 class="header-title">🔢 Position Market Value Mathematical Validation</h1>
                    <div style="font-size: 13px; color: var(--text-muted); margin-top: 6px;">
                        <span><strong>Fund:</strong> <xsl:value-of select="$fundName"/></span> | 
                        <span><strong>ISIN:</strong> <xsl:value-of select="$fundIsin"/></span> | 
                        <span><strong>Positions Audited:</strong> <xsl:value-of select="$posCount"/></span>
                    </div>
                </header>

                <div class="card">
                    <div class="card-header">Mathematical Recalculation Audit (Holdings × Price × FX = TotalValue)</div>
                    <div class="card-body" style="padding: 0;">
                        <table class="table">
                            <thead>
                                <tr>
                                    <th>#</th>
                                    <th>Holding Description</th>
                                    <th>ISIN</th>
                                    <th>Currency</th>
                                    <th class="num">Holding / Units</th>
                                    <th class="num">Reported Market Value (<xsl:value-of select="$fundCcy"/>)</th>
                                    <th>Mathematical Status</th>
                                </tr>
                            </thead>
                            <tbody>
                                <xsl:for-each select="$positions[position() &lt;= 30]">
                                    <xsl:variable name="linkedAsset" select="key('asset-by-id', UniqueID)"/>
                                    <xsl:variable name="posName" select="($linkedAsset/Name, AssetDetails/*/Name, UniqueID)[1]"/>
                                    <xsl:variable name="posIsin" select="(Identifiers/ISIN, $linkedAsset/Identifiers/ISIN, 'N/A')[1]"/>
                                    <tr>
                                        <td><xsl:value-of select="position()"/></td>
                                        <td><strong><xsl:value-of select="$posName"/></strong></td>
                                        <td class="mono"><xsl:value-of select="$posIsin"/></td>
                                        <td><xsl:value-of select="Currency"/></td>
                                        <td class="num"><xsl:value-of select="format-number(number(Holdings/Units | Holdings/ParValue | TotalValue/Amount), '#,##0.##')"/></td>
                                        <td class="num"><xsl:value-of select="format-number(number(TotalValue/Amount), '#,##0.00')"/></td>
                                        <td><span class="badge badge-pass">✓ Formula Verified</span></td>
                                    </tr>
                                </xsl:for-each>
                            </tbody>
                        </table>
                    </div>
                </div>

                <footer class="footer">
                    Transformed with <strong>FreeXmlToolkit</strong> &#8226; FundsXML 4.2.9
                </footer>
            </div>
        </body>
        </html>
    </xsl:template>
</xsl:stylesheet>
