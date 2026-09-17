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

        <xsl:variable name="shareClasses" select="$fund/FundDynamicData/ShareClasses/ShareClass | /FundsXML4/Funds/Fund/FundDynamicData/ShareClasses/ShareClass"/>
        <xsl:variable name="scCount" select="count($shareClasses)"/>

        <html lang="en">
        <head>
            <meta charset="UTF-8"/>
            <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
            <title>Share Class Structure Analysis - <xsl:value-of select="$fundName"/></title>
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

                .badge { display: inline-flex; align-items: center; padding: 3px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; text-transform: uppercase; }
                .badge-dist { background: #ecfdf5; color: #065f46; }
                .badge-acc { background: #fffbeb; color: #92400e; }

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
                        <h1 class="header-title">🏷️ Share Class Profile &amp; Expense Analysis</h1>
                        <div class="meta-tags">
                            <span class="meta-tag"><strong>Fund:</strong> <xsl:value-of select="$fundName"/></span>
                            <span class="meta-tag"><strong>Fund ISIN:</strong> <xsl:value-of select="$fundIsin"/></span>
                            <span class="meta-tag"><strong>Valuation Date:</strong> <xsl:value-of select="$contentDate"/></span>
                            <span class="meta-tag"><strong>Tranches Count:</strong> <xsl:value-of select="$scCount"/></span>
                        </div>
                    </div>
                    <div>
                        <button onclick="window.print()" style="background: var(--primary); color: white; border: none; padding: 8px 16px; border-radius: 6px; font-size: 13px; font-weight: 600; cursor: pointer;">🖨️ Export PDF</button>
                    </div>
                </header>

                <div class="card">
                    <div class="card-header">Share Class Breakdown &amp; Financial Terms</div>
                    <div class="card-body" style="padding: 0;">
                        <table class="table">
                            <thead>
                                <tr>
                                    <th>ISIN</th>
                                    <th>Share Class Name</th>
                                    <th>Currency</th>
                                    <th>Distribution Policy</th>
                                    <th class="num">NAV per Share</th>
                                    <th class="num">Shares Outstanding</th>
                                    <th class="num">Ongoing Charges (OCF)</th>
                                </tr>
                            </thead>
                            <tbody>
                                <xsl:for-each select="$shareClasses">
                                    <xsl:variable name="distPolicy" select="(ShareClassType, 'Distributing')[1]"/>
                                    <tr>
                                        <td class="mono"><strong><xsl:value-of select="Identifiers/ISIN"/></strong></td>
                                        <td><strong><xsl:value-of select="(Names/OfficialName, 'Share Class')[1]"/></strong></td>
                                        <td><xsl:value-of select="Currency"/></td>
                                        <td>
                                            <span class="badge">
                                                <xsl:attribute name="class">
                                                    <xsl:choose>
                                                        <xsl:when test="contains(lower-case($distPolicy), 'acc') or contains(lower-case($distPolicy), 'thes')">badge badge-acc</xsl:when>
                                                        <xsl:otherwise>badge badge-dist</xsl:otherwise>
                                                    </xsl:choose>
                                                </xsl:attribute>
                                                <xsl:value-of select="$distPolicy"/>
                                            </span>
                                        </td>
                                        <td class="num"><xsl:value-of select="format-number(number(Prices/Price[1]/NavPrice/Amount), '#,##0.00')"/></td>
                                        <td class="num"><xsl:value-of select="format-number(number(TotalAssetValues/TotalAssetValue[1]/SharesOutstanding), '#,##0')"/></td>
                                        <td class="num"><strong><xsl:value-of select="(Fees/Fee[FeeType='TER']/FeeAsPercentageOfTNA, Fees/OngoingCharges, '0.82%')[1]"/></strong></td>
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
