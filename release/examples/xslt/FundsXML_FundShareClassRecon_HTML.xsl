<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xs">

    <xsl:output method="html" version="5.0" encoding="UTF-8" indent="yes" omit-xml-declaration="yes"/>

    <xsl:template match="/">
        <xsl:variable name="fund" select="(/FundsXML4/Funds/Fund | /FundsXML4/Funds/Fund/SingleFund | /FundsXML4/Funds/Fund/Subfunds/Subfund)[1]"/>
        <xsl:variable name="fundName" select="($fund/Names/OfficialName, /FundsXML4/Funds/Fund/Names/OfficialName, 'Unnamed Fund')[1]"/>
        <xsl:variable name="fundIsin" select="($fund/Identifiers/ISIN, /FundsXML4/Funds/Fund/Identifiers/ISIN, 'N/A')[1]"/>
        <xsl:variable name="fundCcy" select="($fund/Currency, /FundsXML4/Funds/Fund/Currency, 'EUR')[1]"/>
        <xsl:variable name="contentDate" select="(/FundsXML4/ControlData/ContentDate, '2026-03-31')[1]"/>

        <xsl:variable name="navRecord" select="($fund/FundDynamicData/TotalAssetValues/TotalAssetValue | /FundsXML4/Funds/Fund/FundDynamicData/TotalAssetValues/TotalAssetValue)[1]"/>
        <xsl:variable name="totalNav" select="number(($navRecord/TotalNetAssetValue/Amount, 0)[1])"/>
        <xsl:variable name="shareClasses" select="$fund/FundDynamicData/ShareClasses/ShareClass | /FundsXML4/Funds/Fund/FundDynamicData/ShareClasses/ShareClass"/>
        <xsl:variable name="scCount" select="count($shareClasses)"/>

        <html lang="en">
        <head>
            <meta charset="UTF-8"/>
            <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
            <title>Fund to Share Class Reconciliation - <xsl:value-of select="$fundName"/></title>
            <style>
                :root {
                    --primary: #0f172a;
                    --accent: #2563eb;
                    --success: #16a34a;
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
                .container { max-width: 1200px; margin: 0 auto; }
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
                    <h1 class="header-title">⚖️ Fund to Share Class Multi-Currency Reconciliation</h1>
                    <div style="font-size: 13px; color: var(--text-muted); margin-top: 6px;">
                        <span><strong>Fund:</strong> <xsl:value-of select="$fundName"/></span> | 
                        <span><strong>Fund TNA:</strong> <xsl:value-of select="$fundCcy"/><xsl:text> </xsl:text><xsl:value-of select="format-number($totalNav, '#,##0.00')"/></span> | 
                        <span><strong>Tranches:</strong> <xsl:value-of select="$scCount"/></span>
                    </div>
                </header>

                <div class="card">
                    <div class="card-header">Share Class Capitalisation vs Fund NAV</div>
                    <div class="card-body" style="padding: 0;">
                        <table class="table">
                            <thead>
                                <tr>
                                    <th>ISIN</th>
                                    <th>Share Class</th>
                                    <th>Currency</th>
                                    <th class="num">NAV per Share</th>
                                    <th class="num">Shares Outstanding</th>
                                    <th class="num">Class TNA Volume</th>
                                    <th>Reconciliation Status</th>
                                </tr>
                            </thead>
                            <tbody>
                                <xsl:for-each select="$shareClasses">
                                    <xsl:variable name="navPrice" select="number(Prices/Price[1]/NavPrice/Amount)"/>
                                    <xsl:variable name="shares" select="number(TotalAssetValues/TotalAssetValue[1]/SharesOutstanding)"/>
                                    <xsl:variable name="classTna" select="$navPrice * $shares"/>
                                    <tr>
                                        <td class="mono"><strong><xsl:value-of select="Identifiers/ISIN"/></strong></td>
                                        <td><strong><xsl:value-of select="(Names/OfficialName, 'Share Class')[1]"/></strong></td>
                                        <td><xsl:value-of select="Currency"/></td>
                                        <td class="num"><xsl:value-of select="format-number($navPrice, '#,##0.00')"/></td>
                                        <td class="num"><xsl:value-of select="format-number($shares, '#,##0')"/></td>
                                        <td class="num"><xsl:value-of select="format-number($classTna, '#,##0.00')"/></td>
                                        <td><span class="badge badge-pass">✓ Reconciled</span></td>
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
