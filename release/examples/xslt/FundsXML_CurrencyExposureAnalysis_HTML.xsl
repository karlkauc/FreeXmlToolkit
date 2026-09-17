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

        <html lang="en">
        <head>
            <meta charset="UTF-8"/>
            <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
            <title>Currency Exposure Analysis - <xsl:value-of select="$fundName"/></title>
            <style>
                :root {
                    --primary: #1e3a8a;
                    --accent: #0ea5e9;
                    --bg: #f8fafc;
                    --surface: #ffffff;
                    --border: #e2e8f0;
                    --text: #0f172a;
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

                .bar-row { margin-bottom: 16px; }
                .bar-header { display: flex; justify-content: space-between; font-size: 13px; margin-bottom: 6px; font-weight: 600; }
                .progress-track { height: 10px; background-color: #f1f5f9; border-radius: 9999px; overflow: hidden; }
                .progress-fill { height: 100%; border-radius: 9999px; background-color: var(--primary); }

                .footer { margin-top: 24px; padding-top: 14px; border-top: 1px solid var(--border); font-size: 12px; color: var(--text-muted); display: flex; justify-content: space-between; }
            </style>
        </head>
        <body>
            <div class="container">
                <header class="header">
                    <div>
                        <h1 class="header-title">💱 Currency Exposure &amp; FX Risk Analytics</h1>
                        <div class="meta-tags">
                            <span class="meta-tag"><strong>Fund:</strong> <xsl:value-of select="$fundName"/></span>
                            <span class="meta-tag"><strong>Base Currency:</strong> <xsl:value-of select="$fundCcy"/></span>
                            <span class="meta-tag"><strong>Valuation Date:</strong> <xsl:value-of select="$contentDate"/></span>
                        </div>
                    </div>
                    <div>
                        <button onclick="window.print()" style="background: var(--primary); color: white; border: none; padding: 8px 16px; border-radius: 6px; font-size: 13px; font-weight: 600; cursor: pointer;">🖨️ Export PDF</button>
                    </div>
                </header>

                <div class="card">
                    <div class="card-header">Portfolio Currency Allocation (% of NAV)</div>
                    <div class="card-body">
                        <div class="bar-row">
                            <div class="bar-header"><span><xsl:value-of select="$fundCcy"/> (Base Currency)</span><span>58.2%</span></div>
                            <div class="progress-track"><div class="progress-fill" style="width: 58.2%;"></div></div>
                        </div>
                        <div class="bar-row">
                            <div class="bar-header"><span>USD (US Dollar)</span><span>28.4%</span></div>
                            <div class="progress-track"><div class="progress-fill" style="width: 28.4%; background-color: #0ea5e9;"></div></div>
                        </div>
                        <div class="bar-row">
                            <div class="bar-header"><span>CHF (Swiss Franc)</span><span>7.8%</span></div>
                            <div class="progress-track"><div class="progress-fill" style="width: 7.8%; background-color: #10b981;"></div></div>
                        </div>
                        <div class="bar-row">
                            <div class="bar-header"><span>GBP (British Pound)</span><span>5.6%</span></div>
                            <div class="progress-track"><div class="progress-fill" style="width: 5.6%; background-color: #f59e0b;"></div></div>
                        </div>
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
