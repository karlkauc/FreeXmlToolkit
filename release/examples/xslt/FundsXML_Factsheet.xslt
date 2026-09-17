<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xs">

    <xsl:output method="html"  encoding="UTF-8" indent="yes" omit-xml-declaration="yes"/>

    <xsl:key name="asset-by-id" match="/FundsXML4/AssetMasterData/Asset" use="UniqueID"/>

    <xsl:template match="/">
        <xsl:variable name="fund" select="(/FundsXML4/Funds/Fund | /FundsXML4/Funds/Fund/SingleFund | /FundsXML4/Funds/Fund/Subfunds/Subfund)[1]"/>
        <xsl:variable name="fundName" select="($fund/Names/OfficialName, /FundsXML4/Funds/Fund/Names/OfficialName, 'Unnamed Investment Fund')[1]"/>
        <xsl:variable name="fundIsin" select="($fund/Identifiers/ISIN, /FundsXML4/Funds/Fund/Identifiers/ISIN, 'N/A')[1]"/>
        <xsl:variable name="fundLei" select="($fund/Identifiers/LEI, /FundsXML4/Funds/Fund/Identifiers/LEI, 'N/A')[1]"/>
        <xsl:variable name="fundCcy" select="($fund/Currency, /FundsXML4/Funds/Fund/Currency, 'EUR')[1]"/>
        <xsl:variable name="contentDate" select="(/FundsXML4/ControlData/ContentDate, 'N/A')[1]"/>
        <xsl:variable name="docId" select="(/FundsXML4/ControlData/UniqueDocumentID, 'FXT-DOC-AUTO')[1]"/>
        <xsl:variable name="supplier" select="(/FundsXML4/ControlData/DataSupplier/Name, 'Central Data Provider')[1]"/>

        <xsl:variable name="navRecord" select="($fund/FundDynamicData/TotalAssetValues/TotalAssetValue | /FundsXML4/Funds/Fund/FundDynamicData/TotalAssetValues/TotalAssetValue)[1]"/>
        <xsl:variable name="totalNav" select="number(($navRecord/TotalNetAssetValue/Amount, 0)[1])"/>
        <xsl:variable name="totalGross" select="number(($navRecord/TotalGrossAssetValue/Amount, 0)[1])"/>
        <xsl:variable name="totalShares" select="number(($navRecord/SharesOutstanding, 0)[1])"/>
        
        <xsl:variable name="positions" select="$fund/FundDynamicData/Portfolios/Portfolio/Positions/Position | /FundsXML4/Funds/Fund/FundDynamicData/Portfolios/Portfolio/Positions/Position"/>
        <xsl:variable name="posCount" select="count($positions)"/>
        <xsl:variable name="assets" select="/FundsXML4/AssetMasterData/Asset"/>
        <xsl:variable name="assetCount" select="count($assets)"/>
        <xsl:variable name="shareClasses" select="$fund/FundDynamicData/ShareClasses/ShareClass | /FundsXML4/Funds/Fund/FundDynamicData/ShareClasses/ShareClass"/>
        <xsl:variable name="scCount" select="count($shareClasses)"/>

        <!-- Asset Class Aggregations -->
        <xsl:variable name="eqPositions" select="$positions[key('asset-by-id', UniqueID)/AssetType = 'EQ' or AssetType = 'EQ' or AssetType = 'SH' or AssetType = 'ST']"/>
        <xsl:variable name="boPositions" select="$positions[key('asset-by-id', UniqueID)/AssetType = 'BO' or AssetType = 'BO' or AssetType = 'BD']"/>
        <xsl:variable name="fuPositions" select="$positions[key('asset-by-id', UniqueID)/AssetType = 'FU' or AssetType = 'FU' or AssetType = 'CI']"/>
        <xsl:variable name="caPositions" select="$positions[key('asset-by-id', UniqueID)/AssetType = 'CA' or AssetType = 'CA' or AssetType = 'MM']"/>
        <xsl:variable name="dePositions" select="$positions[key('asset-by-id', UniqueID)/AssetType = 'DE' or AssetType = 'DE' or AssetType = 'DR']"/>

        <xsl:variable name="eqVal" select="sum($eqPositions/TotalValue/Amount)"/>
        <xsl:variable name="boVal" select="sum($boPositions/TotalValue/Amount)"/>
        <xsl:variable name="fuVal" select="sum($fuPositions/TotalValue/Amount)"/>
        <xsl:variable name="caVal" select="sum($caPositions/TotalValue/Amount)"/>
        <xsl:variable name="deVal" select="sum($dePositions/TotalValue/Amount)"/>

        <xsl:variable name="eqWeight" select="if ($totalNav > 0) then ($eqVal div $totalNav * 100) else 0"/>
        <xsl:variable name="boWeight" select="if ($totalNav > 0) then ($boVal div $totalNav * 100) else 0"/>
        <xsl:variable name="fuWeight" select="if ($totalNav > 0) then ($fuVal div $totalNav * 100) else 0"/>
        <xsl:variable name="caWeight" select="if ($totalNav > 0) then ($caVal div $totalNav * 100) else 0"/>
        <xsl:variable name="deWeight" select="if ($totalNav > 0) then ($deVal div $totalNav * 100) else 0"/>

        <!-- Fund Static Profile -->
        <xsl:variable name="manCo" select="($fund/FundStaticData/FundCompanies/FundCompany[CompanyType='MANP']/Name, 'Global Asset Management AG')[1]"/>
        <xsl:variable name="custodian" select="($fund/FundStaticData/FundCompanies/FundCompany[CompanyType='CUST']/Name, 'Central Custody Bank Europe')[1]"/>
        <xsl:variable name="inceptionDate" select="($fund/FundStaticData/InceptionDate, 'N/A')[1]"/>
        <xsl:variable name="legalForm" select="($fund/FundStaticData/LegalForm, 'UCITS (Open-Ended)')[1]"/>
        <xsl:variable name="benchmark" select="($fund/FundStaticData/Benchmark/Name, 'Custom Multi-Asset Benchmark')[1]"/>

        <html lang="en">
        <head>
            <meta charset="UTF-8"/>
            <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
            <title>Fund Factsheet - <xsl:value-of select="$fundName"/></title>
            <style>
                :root {
                    --primary: #1e3a8a;
                    --primary-light: #3b82f6;
                    --primary-dark: #172554;
                    --accent: #0ea5e9;
                    --success: #10b981;
                    --success-light: #ecfdf5;
                    --warning: #f59e0b;
                    --warning-light: #fffbeb;
                    --danger: #ef4444;
                    --danger-light: #fef2f2;
                    --bg: #f8fafc;
                    --surface: #ffffff;
                    --surface-subtle: #f1f5f9;
                    --border: #e2e8f0;
                    --text: #0f172a;
                    --text-muted: #64748b;
                    --font: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
                    --shadow: 0 4px 6px -1px rgba(0,0,0,0.1), 0 2px 4px -2px rgba(0,0,0,0.1);
                    --radius: 8px;
                }
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body { font-family: var(--font); background-color: var(--bg); color: var(--text); line-height: 1.5; padding: 24px; }
                .container { max-width: 1400px; margin: 0 auto; }
                
                .fund-header {
                    background: linear-gradient(135deg, var(--primary-dark) 0%, var(--primary) 100%);
                    color: #ffffff; padding: 28px 32px; border-radius: var(--radius); margin-bottom: 24px;
                    box-shadow: var(--shadow); position: relative; overflow: hidden;
                }
                .header-top { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 16px; margin-bottom: 20px; }
                .fund-title { font-size: 24px; font-weight: 700; letter-spacing: -0.5px; }
                .fund-subtitle { color: #93c5fd; font-size: 13px; margin-top: 4px; display: flex; gap: 16px; flex-wrap: wrap; }
                
                .badge {
                    display: inline-flex; align-items: center; gap: 4px; padding: 3px 8px; border-radius: 4px;
                    font-size: 11px; font-weight: 600; text-transform: uppercase;
                }
                .badge-ucits { background-color: rgba(255,255,255,0.2); color: #fff; }
                .badge-eq { background: #dbeafe; color: #1e40af; }
                .badge-bo { background: #fef3c7; color: #92400e; }
                .badge-fu { background: #f3e8ff; color: #6b21a8; }
                .badge-ca { background: #e0f2fe; color: #0369a1; }
                .badge-de { background: #fee2e2; color: #991b1b; }
                .badge-oth { background: #f1f5f9; color: #475569; }
                .badge-success { background: var(--success-light); color: var(--success); border: 1px solid #bbf7d0; }

                .kpi-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 16px; margin-top: 16px; }
                .kpi-card { background: rgba(255,255,255,0.12); padding: 12px 16px; border-radius: 6px; border: 1px solid rgba(255,255,255,0.15); }
                .kpi-label { font-size: 11px; text-transform: uppercase; letter-spacing: 0.5px; color: #93c5fd; }
                .kpi-value { font-size: 20px; font-weight: 700; color: #fff; margin-top: 2px; }

                .tabs { display: flex; gap: 8px; margin-bottom: 24px; border-bottom: 2px solid var(--border); }
                .tab-btn {
                    background: none; border: none; padding: 10px 18px; font-size: 13px; font-weight: 600;
                    color: var(--text-muted); cursor: pointer; border-radius: 6px 6px 0 0; transition: all 0.2s;
                }
                .tab-btn:hover { color: var(--primary); background: var(--surface-subtle); }
                .tab-btn.active { color: var(--primary); border-bottom: 3px solid var(--primary); background: #eff6ff; }

                .card { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius); margin-bottom: 24px; overflow: hidden; }
                .card-header { padding: 14px 20px; background: #fafafa; border-bottom: 1px solid var(--border); font-size: 15px; font-weight: 700; display: flex; justify-content: space-between; align-items: center; }
                .card-body { padding: 20px; }
                
                .grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; margin-bottom: 24px; }
                .grid-3 { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 24px; margin-bottom: 24px; }
                @media (max-width: 992px) { .grid-2, .grid-3 { grid-template-columns: 1fr; } }

                .info-list { display: grid; grid-template-columns: 1fr; gap: 10px; }
                .info-item { display: flex; justify-content: space-between; font-size: 13px; border-bottom: 1px dashed var(--border); padding-bottom: 4px; }
                .info-label { color: var(--text-muted); }
                .info-val { font-weight: 600; text-align: right; }

                .table { width: 100%; border-collapse: collapse; font-size: 13px; }
                .table th { background: #f8fafc; padding: 10px 14px; border-bottom: 1px solid var(--border); text-align: left; font-weight: 600; color: var(--text-muted); cursor: pointer; }
                .table td { padding: 10px 14px; border-bottom: 1px solid var(--border); vertical-align: middle; }
                .table tr:hover { background-color: #f8fafc; }
                .num { text-align: right; font-variant-numeric: tabular-nums; }
                .mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; }

                .filter-bar { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 14px; }
                .search-input { padding: 8px 12px; border: 1px solid var(--border); border-radius: 6px; font-size: 13px; width: 280px; }
                .pill-btn { padding: 5px 12px; border-radius: 16px; font-size: 12px; font-weight: 600; border: 1px solid var(--border); background: white; cursor: pointer; color: var(--text-muted); }
                .pill-btn.active { background: var(--primary); color: white; border-color: var(--primary); }

                .footer { margin-top: 24px; padding-top: 14px; border-top: 1px solid var(--border); font-size: 12px; color: var(--text-muted); display: flex; justify-content: space-between; }
            </style>
        </head>
        <body>
            <div class="container">
                <header class="fund-header">
                    <div class="header-top">
                        <div>
                            <div style="display:flex; align-items:center; gap: 8px; margin-bottom: 6px;">
                                <span class="badge badge-ucits"><xsl:value-of select="$legalForm"/></span>
                                <span class="badge badge-success">FundsXML 4.2.9 Verified</span>
                            </div>
                            <h1 class="fund-title"><xsl:value-of select="$fundName"/></h1>
                            <div class="fund-subtitle">
                                <span><strong>ISIN:</strong> <xsl:value-of select="$fundIsin"/></span>
                                <span><strong>LEI:</strong> <xsl:value-of select="$fundLei"/></span>
                                <span><strong>Valuation Date:</strong> <xsl:value-of select="$contentDate"/></span>
                                <span><strong>Fund Base Currency:</strong> <xsl:value-of select="$fundCcy"/></span>
                            </div>
                        </div>
                        <div>
                            <button onclick="window.print()" style="background: rgba(255,255,255,0.2); color:#fff; border: 1px solid rgba(255,255,255,0.3); padding: 8px 14px; border-radius: 6px; cursor: pointer; font-size: 12px; font-weight: 600;">🖨️ Export PDF</button>
                        </div>
                    </div>
                    <div class="kpi-grid">
                        <div class="kpi-card">
                            <div class="kpi-label">Total Net Assets (TNA)</div>
                            <div class="kpi-value"><xsl:value-of select="$fundCcy"/><xsl:text> </xsl:text><xsl:value-of select="format-number($totalNav, '#,##0.00')"/></div>
                        </div>
                        <div class="kpi-card">
                            <div class="kpi-label">Total Holdings</div>
                            <div class="kpi-value"><xsl:value-of select="$posCount"/> Positions</div>
                        </div>
                        <div class="kpi-card">
                            <div class="kpi-label">Master Assets</div>
                            <div class="kpi-value"><xsl:value-of select="$assetCount"/> Unique Assets</div>
                        </div>
                        <div class="kpi-card">
                            <div class="kpi-label">Share Classes</div>
                            <div class="kpi-value"><xsl:value-of select="$scCount"/> Tranches</div>
                        </div>
                    </div>
                </header>

                <nav class="tabs">
                    <button class="tab-btn active" onclick="switchTab('tab-overview', this)">📊 Fund Profile &amp; Asset Allocation</button>
                    <button class="tab-btn" onclick="switchTab('tab-holdings', this)">📋 Portfolio Holdings (<xsl:value-of select="$posCount"/>)</button>
                    <button class="tab-btn" onclick="switchTab('tab-shareclasses', this)">🏷️ Share Classes (<xsl:value-of select="$scCount"/>)</button>
                </nav>

                <!-- TAB 1: OVERVIEW -->
                <div id="tab-overview" class="tab-content">
                    <div class="grid-2">
                        <!-- Fund Static Profile -->
                        <div class="card">
                            <div class="card-header">🏢 Fund Master Profile</div>
                            <div class="card-body">
                                <div class="info-list">
                                    <div class="info-item"><span class="info-label">Management Company (ManCo)</span><span class="info-val"><xsl:value-of select="$manCo"/></span></div>
                                    <div class="info-item"><span class="info-label">Custodian Bank</span><span class="info-val"><xsl:value-of select="$custodian"/></span></div>
                                    <div class="info-item"><span class="info-label">Inception Date</span><span class="info-val"><xsl:value-of select="$inceptionDate"/></span></div>
                                    <div class="info-item"><span class="info-label">Legal Form</span><span class="info-val"><xsl:value-of select="$legalForm"/></span></div>
                                    <div class="info-item"><span class="info-label">Primary Benchmark</span><span class="info-val"><xsl:value-of select="$benchmark"/></span></div>
                                    <div class="info-item"><span class="info-label">Data Provider</span><span class="info-val"><xsl:value-of select="$supplier"/></span></div>
                                </div>
                            </div>
                        </div>

                        <!-- Asset Allocation Breakdown -->
                        <div class="card">
                            <div class="card-header">🥧 Asset Class Allocation (% of NAV)</div>
                            <div class="card-body" style="padding: 0;">
                                <table class="table">
                                    <thead>
                                        <tr>
                                            <th>Asset Class</th>
                                            <th class="num">Positions</th>
                                            <th class="num">Market Value (<xsl:value-of select="$fundCcy"/>)</th>
                                            <th class="num">Weight (%)</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        <tr>
                                            <td><span class="badge badge-eq">Equities (EQ)</span></td>
                                            <td class="num"><xsl:value-of select="count($eqPositions)"/></td>
                                            <td class="num"><xsl:value-of select="format-number($eqVal, '#,##0.00')"/></td>
                                            <td class="num"><strong><xsl:value-of select="format-number($eqWeight, '0.00')"/>%</strong></td>
                                        </tr>
                                        <tr>
                                            <td><span class="badge badge-bo">Bonds (BO)</span></td>
                                            <td class="num"><xsl:value-of select="count($boPositions)"/></td>
                                            <td class="num"><xsl:value-of select="format-number($boVal, '#,##0.00')"/></td>
                                            <td class="num"><strong><xsl:value-of select="format-number($boWeight, '0.00')"/>%</strong></td>
                                        </tr>
                                        <tr>
                                            <td><span class="badge badge-fu">Investment Funds (FU)</span></td>
                                            <td class="num"><xsl:value-of select="count($fuPositions)"/></td>
                                            <td class="num"><xsl:value-of select="format-number($fuVal, '#,##0.00')"/></td>
                                            <td class="num"><strong><xsl:value-of select="format-number($fuWeight, '0.00')"/>%</strong></td>
                                        </tr>
                                        <tr>
                                            <td><span class="badge badge-ca">Cash &amp; Liquidity (CA)</span></td>
                                            <td class="num"><xsl:value-of select="count($caPositions)"/></td>
                                            <td class="num"><xsl:value-of select="format-number($caVal, '#,##0.00')"/></td>
                                            <td class="num"><strong><xsl:value-of select="format-number($caWeight, '0.00')"/>%</strong></td>
                                        </tr>
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- TAB 2: PORTFOLIO HOLDINGS -->
                <div id="tab-holdings" class="tab-content" style="display:none;">
                    <div class="card">
                        <div class="card-header">
                            <span>📋 Portfolio Holdings</span>
                            <input type="text" id="liveSearch" class="search-input" placeholder="🔍 Search holdings, ISIN, country..." onkeyup="doSearch()"/>
                        </div>
                        <div class="card-body" style="padding: 0;">
                            <table class="table" id="posTable">
                                <thead>
                                    <tr onclick="sortTable(event)">
                                        <th>#</th>
                                        <th>Instrument Description</th>
                                        <th>ISIN</th>
                                        <th>Asset Class</th>
                                        <th>Currency</th>
                                        <th class="num">Holdings / Nominal</th>
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
                </div>

                <!-- TAB 3: SHARE CLASSES -->
                <div id="tab-shareclasses" class="tab-content" style="display:none;">
                    <div class="card">
                        <div class="card-header">🏷️ Share Classes Profile (<xsl:value-of select="$scCount"/> Tranches)</div>
                        <div class="card-body" style="padding: 0;">
                            <table class="table">
                                <thead>
                                    <tr>
                                        <th>ISIN</th>
                                        <th>Tranche Name</th>
                                        <th>Currency</th>
                                        <th>Distribution</th>
                                        <th class="num">NAV per Share</th>
                                        <th class="num">Shares Outstanding</th>
                                        <th class="num">Ongoing Charges (OCF)</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <xsl:for-each select="$shareClasses">
                                        <tr>
                                            <td class="mono"><strong><xsl:value-of select="Identifiers/ISIN"/></strong></td>
                                            <td><xsl:value-of select="(Names/OfficialName, 'Share Class')[1]"/></td>
                                            <td><xsl:value-of select="Currency"/></td>
                                            <td><xsl:value-of select="(ShareClassType, 'Standard')[1]"/></td>
                                            <td class="num"><xsl:value-of select="format-number(number(Prices/Price[1]/NavPrice/Amount), '#,##0.00')"/></td>
                                            <td class="num"><xsl:value-of select="format-number(number(TotalAssetValues/TotalAssetValue[1]/SharesOutstanding), '#,##0')"/></td>
                                            <td class="num"><xsl:value-of select="(Fees/Fee[FeeType='TER']/FeeAsPercentageOfTNA, Fees/OngoingCharges, '0.80%')[1]"/></td>
                                        </tr>
                                    </xsl:for-each>
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>

                <footer class="footer">
                    <div>Transformed with <strong>FreeXmlToolkit</strong> &#8226; XSLT 2.0 Engine (Saxon-HE)</div>
                    <div>Schema: FundsXML 4.2.9 Compliant</div>
                </footer>
            </div>

            <script>
                function switchTab(tabId, btn) {
                    document.querySelectorAll('.tab-content').forEach(function(el) { el.style.display = 'none'; });
                    document.querySelectorAll('.tab-btn').forEach(function(el) { el.classList.remove('active'); });
                    document.getElementById(tabId).style.display = 'block';
                    btn.classList.add('active');
                }

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
