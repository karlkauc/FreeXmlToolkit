<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xs">

    <xsl:output method="html" encoding="UTF-8" indent="yes" omit-xml-declaration="yes"/>

    <xsl:template match="/">
        <xsl:variable name="fund" select="(/FundsXML4/Funds/Fund | /FundsXML4/Funds/Fund/SingleFund | /FundsXML4/Funds/Fund/Subfunds/Subfund)[1]"/>
        <xsl:variable name="fundName" select="($fund/Names/OfficialName, /FundsXML4/Funds/Fund/Names/OfficialName, 'Unnamed Fund')[1]"/>
        <xsl:variable name="fundIsin" select="($fund/Identifiers/ISIN, /FundsXML4/Funds/Fund/Identifiers/ISIN, 'N/A')[1]"/>
        <xsl:variable name="fundCcy" select="($fund/Currency, /FundsXML4/Funds/Fund/Currency, 'EUR')[1]"/>
        <xsl:variable name="contentDate" select="(/FundsXML4/ControlData/ContentDate, '2026-03-31')[1]"/>

        <xsl:variable name="navRecord" select="($fund/FundDynamicData/TotalAssetValues/TotalAssetValue | /FundsXML4/Funds/Fund/FundDynamicData/TotalAssetValues/TotalAssetValue)[1]"/>
        <xsl:variable name="totalNav" select="number(($navRecord/TotalNetAssetValue/Amount, 0)[1])"/>
        <xsl:variable name="positions" select="$fund/FundDynamicData/Portfolios/Portfolio/Positions/Position | /FundsXML4/Funds/Fund/FundDynamicData/Portfolios/Portfolio/Positions/Position"/>
        <xsl:variable name="sumPositions" select="sum($positions/TotalValue/Amount)"/>
        <xsl:variable name="delta" select="abs($totalNav - $sumPositions)"/>

        <html lang="en">
        <head>
            <meta charset="UTF-8"/>
            <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
            <title>FundsXML Consistency Check - <xsl:value-of select="$fundName"/></title>
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
                .recon-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
                @media (max-width: 800px) { .recon-grid { grid-template-columns: 1fr; } }
                .recon-box { background: #f8fafc; border: 1px solid var(--border); border-radius: 6px; padding: 14px; text-align: center; }
                .recon-label { font-size: 12px; color: var(--text-muted); font-weight: 600; margin-bottom: 4px; }
                .recon-val { font-size: 20px; font-weight: 700; font-family: ui-monospace, monospace; }
                .badge { display: inline-flex; align-items: center; padding: 4px 10px; border-radius: 4px; font-size: 12px; font-weight: 700; text-transform: uppercase; }
                .badge-pass { background: #f0fdf4; color: var(--success); border: 1px solid #bbf7d0; }
                .badge-crit { background: #fef2f2; color: var(--danger); border: 1px solid #fecaca; }
                .footer { margin-top: 24px; font-size: 12px; color: var(--text-muted); }
            </style>
        </head>
        <body>
            <div class="container">
                <header class="header">
                    <h1 class="header-title">⚖️ Financial NAV &amp; Position Consistency Check</h1>
                    <div style="font-size: 13px; color: var(--text-muted); margin-top: 6px;">
                        <span><strong>Fund:</strong> <xsl:value-of select="$fundName"/> (<xsl:value-of select="$fundIsin"/>)</span> | 
                        <span><strong>Date:</strong> <xsl:value-of select="$contentDate"/></span>
                    </div>
                </header>

                <div class="card">
                    <div class="card-header">Reconciliation Breakdown</div>
                    <div class="card-body">
                        <div class="recon-grid">
                            <div class="recon-box">
                                <div class="recon-label">Reported Total Net Asset Value</div>
                                <div class="recon-val"><xsl:value-of select="$fundCcy"/><xsl:text> </xsl:text><xsl:value-of select="format-number($totalNav, '#,##0.00')"/></div>
                            </div>
                            <div class="recon-box">
                                <div class="recon-label">Calculated Sum of Positions</div>
                                <div class="recon-val"><xsl:value-of select="$fundCcy"/><xsl:text> </xsl:text><xsl:value-of select="format-number($sumPositions, '#,##0.00')"/></div>
                            </div>
                            <div class="recon-box">
                                <div class="recon-label">Variance (Delta)</div>
                                <div class="recon-val">
                                    <xsl:choose>
                                        <xsl:when test="$delta &lt; 0.01">
                                            <span style="color: var(--success);"><xsl:value-of select="$fundCcy"/> 0.00</span>
                                        </xsl:when>
                                        <xsl:otherwise>
                                            <span style="color: var(--danger);"><xsl:value-of select="$fundCcy"/><xsl:text> </xsl:text><xsl:value-of select="format-number($delta, '#,##0.00')"/></span>
                                        </xsl:otherwise>
                                    </xsl:choose>
                                </div>
                            </div>
                        </div>

                        <div style="margin-top: 20px; text-align: center;">
                            <xsl:choose>
                                <xsl:when test="$delta &lt; 0.01">
                                    <span class="badge badge-pass">✓ 100% Consistent - Zero NAV Variance</span>
                                </xsl:when>
                                <xsl:otherwise>
                                    <span class="badge badge-crit">⚠️ Reconciliation Discrepancy Detected</span>
                                </xsl:otherwise>
                            </xsl:choose>
                        </div>
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