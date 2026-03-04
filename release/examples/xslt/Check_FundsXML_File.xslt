<?xml version="1.1" encoding="UTF-8"?>
<!--
  ~ FreeXMLToolkit - Universal Toolkit for XML
  ~ Copyright (c) Karl Kauc 2024.
  ~
  ~  Licensed under the Apache License, Version 2.0 (the "License");
  ~  you may not use this file except in compliance with the License.
  ~  You may obtain a copy of the License at
  ~
  ~      http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~  Unless required by applicable law or agreed to in writing, software
  ~  distributed under the License is distributed on an "AS IS" BASIS,
  ~  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~  See the License for the specific language governing permissions and
  ~  limitations under the License.
  ~
  -->
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" exclude-result-prefixes="#all">
    <xsl:output method="html" html-version="5.0" encoding="UTF-8" indent="yes" omit-xml-declaration="yes"/>
    <xsl:preserve-space elements="*"/>

    <!-- Key for Position-Asset cross-references -->
    <xsl:key name="asset-by-id" match="//AssetMasterData/Asset" use="UniqueID"/>
    <!-- Key for positions referencing assets -->
    <xsl:key name="position-by-uid" match="//Portfolio/Positions/Position" use="UniqueID"/>

    <xsl:template match="/">
        <!-- ==================== VALIDATION VARIABLES ==================== -->

        <!-- Category: Structure -->
        <xsl:variable name="err_no_controldata" select="if (not(FundsXML4/ControlData)) then 1 else 0"/>
        <xsl:variable name="err_no_contentdate" select="if (not(FundsXML4/ControlData/ContentDate) or normalize-space(FundsXML4/ControlData/ContentDate) = '') then 1 else 0"/>
        <xsl:variable name="err_no_funds" select="if (count(FundsXML4/Funds/Fund) = 0) then 1 else 0"/>
        <xsl:variable name="warn_no_assets" select="if (count(FundsXML4/AssetMasterData/Asset) = 0) then 1 else 0"/>
        <xsl:variable name="structure_errors" select="$err_no_controldata + $err_no_contentdate + $err_no_funds"/>
        <xsl:variable name="structure_warnings" select="$warn_no_assets"/>

        <!-- Category: Required Fields -->
        <xsl:variable name="funds_missing_name" select="FundsXML4/Funds/Fund[not(Names/OfficialName) or normalize-space(Names/OfficialName) = '']"/>
        <xsl:variable name="funds_missing_currency" select="FundsXML4/Funds/Fund[not(Currency) or normalize-space(Currency) = '']"/>
        <xsl:variable name="funds_missing_shareclass" select="FundsXML4/Funds/Fund[count(SingleFund/ShareClasses/ShareClass) = 0]"/>
        <xsl:variable name="err_no_datasupplier" select="if (not(FundsXML4/ControlData/DataSupplier) or (normalize-space(FundsXML4/ControlData/DataSupplier/Name) = '' and normalize-space(FundsXML4/ControlData/DataSupplier/Short) = '')) then 1 else 0"/>
        <xsl:variable name="required_errors" select="count($funds_missing_name) + count($funds_missing_currency) + $err_no_datasupplier"/>
        <xsl:variable name="required_warnings" select="count($funds_missing_shareclass)"/>

        <!-- Category: Reference Integrity -->
        <xsl:variable name="all_position_uids" select="distinct-values(//Portfolio/Positions/Position/UniqueID)"/>
        <xsl:variable name="all_asset_uids" select="distinct-values(FundsXML4/AssetMasterData/Asset/UniqueID)"/>
        <xsl:variable name="orphan_positions" select="//Portfolio/Positions/Position[not(UniqueID = $all_asset_uids)]"/>
        <xsl:variable name="unused_assets" select="FundsXML4/AssetMasterData/Asset[not(UniqueID = $all_position_uids)]"/>
        <xsl:variable name="reference_errors" select="count($orphan_positions)"/>
        <xsl:variable name="reference_warnings" select="count($unused_assets)"/>

        <!-- Category: Data Types -->
        <xsl:variable name="bad_currency_funds" select="FundsXML4/Funds/Fund[Currency and string-length(normalize-space(Currency)) != 3]"/>
        <xsl:variable name="bad_isin_assets" select="FundsXML4/AssetMasterData/Asset[Identifiers/ISIN and (string-length(normalize-space(Identifiers/ISIN)) != 12 or not(matches(normalize-space(Identifiers/ISIN), '^[A-Z]{2}')))]"/>
        <xsl:variable name="datatype_errors" select="count($bad_currency_funds) + count($bad_isin_assets)"/>
        <xsl:variable name="datatype_warnings" select="0"/>

        <!-- Category: Business Rules -->
        <xsl:variable name="portfolios_with_pct" select="//Portfolio[Positions/Position/TotalPercentage]"/>
        <xsl:variable name="portfolios_pct_error" select="$portfolios_with_pct[number(sum(Positions/Position/TotalPercentage)) &lt; 90 or number(sum(Positions/Position/TotalPercentage)) > 110]"/>
        <xsl:variable name="portfolios_pct_warn" select="$portfolios_with_pct[not(number(sum(Positions/Position/TotalPercentage)) &lt; 90 or number(sum(Positions/Position/TotalPercentage)) > 110) and (number(sum(Positions/Position/TotalPercentage)) &lt; 95 or number(sum(Positions/Position/TotalPercentage)) > 105)]"/>
        <xsl:variable name="assets_missing_isin" select="FundsXML4/AssetMasterData/Asset[AssetType = ('EQ', 'BO', 'SC') and not(Identifiers/ISIN)]"/>
        <xsl:variable name="business_errors" select="count($portfolios_pct_error) + count($assets_missing_isin)"/>
        <xsl:variable name="business_warnings" select="count($portfolios_pct_warn)"/>

        <!-- Category: Completeness -->
        <xsl:variable name="funds_no_tav" select="FundsXML4/Funds/Fund[not(FundDynamicData/TotalAssetValues/TotalAssetValue)]"/>
        <xsl:variable name="funds_no_portfolio" select="FundsXML4/Funds/Fund[not(FundDynamicData/Portfolios/Portfolio/Positions/Position)]"/>
        <xsl:variable name="completeness_errors" select="0"/>
        <xsl:variable name="completeness_warnings" select="count($funds_no_tav) + count($funds_no_portfolio)"/>

        <!-- Totals -->
        <xsl:variable name="total_errors" select="$structure_errors + $required_errors + $reference_errors + $datatype_errors + $business_errors + $completeness_errors"/>
        <xsl:variable name="total_warnings" select="$structure_warnings + $required_warnings + $reference_warnings + $datatype_warnings + $business_warnings + $completeness_warnings"/>

        <!-- Stats -->
        <xsl:variable name="fund_count" select="count(FundsXML4/Funds/Fund)"/>
        <xsl:variable name="position_count" select="count(//Portfolio/Positions/Position)"/>
        <xsl:variable name="asset_count" select="count(FundsXML4/AssetMasterData/Asset)"/>
        <xsl:variable name="shareclass_count" select="count(//SingleFund/ShareClasses/ShareClass)"/>

        <html lang="en">
            <head>
                <title>FundsXML Data Quality Report</title>
                <meta charset="utf-8"/>
                <meta name="viewport" content="width=device-width, initial-scale=1"/>
                <style>
                    /* Reset */
                    *, *::before, *::after { margin: 0; padding: 0; box-sizing: border-box; }

                    html { scroll-behavior: smooth; }

                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
                        background: #f1f5f9;
                        min-height: 100vh;
                        line-height: 1.6;
                        color: #1e293b;
                    }

                    /* Main Container */
                    .report {
                        max-width: 1400px;
                        margin: 0 auto;
                        padding: 1.5rem;
                    }

                    /* Report Header */
                    .report-header {
                        background: white;
                        border-radius: 0.75rem;
                        padding: 1.25rem 1.5rem;
                        margin-bottom: 1rem;
                        box-shadow: 0 1px 3px rgba(0,0,0,0.1);
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        flex-wrap: wrap;
                        gap: 0.75rem;
                    }

                    .report-header h1 {
                        font-size: 1.5rem;
                        font-weight: 700;
                        color: #0f172a;
                    }

                    .report-meta {
                        display: flex;
                        gap: 1.5rem;
                        font-size: 0.875rem;
                        color: #64748b;
                    }

                    .report-meta span { display: flex; align-items: center; gap: 0.375rem; }

                    /* Overall Status Banner */
                    .status-banner {
                        border-radius: 0.75rem;
                        padding: 1rem 1.5rem;
                        margin-bottom: 1rem;
                        font-size: 1.25rem;
                        font-weight: 700;
                        text-align: center;
                        letter-spacing: 0.025em;
                    }

                    .status-pass { background: #f0fdf4; color: #15803d; border: 2px solid #86efac; }
                    .status-warn { background: #fffbeb; color: #a16207; border: 2px solid #fcd34d; }
                    .status-fail { background: #fef2f2; color: #b91c1c; border: 2px solid #fca5a5; }

                    /* Dashboard Grid */
                    .dashboard {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
                        gap: 0.75rem;
                        margin-bottom: 1rem;
                    }

                    .dash-card {
                        background: white;
                        border-radius: 0.75rem;
                        padding: 1rem 1.25rem;
                        box-shadow: 0 1px 3px rgba(0,0,0,0.1);
                        border-left: 4px solid;
                        display: flex;
                        flex-direction: column;
                        gap: 0.25rem;
                    }

                    .dash-card-ok { border-left-color: #22c55e; }
                    .dash-card-warn { border-left-color: #f59e0b; }
                    .dash-card-error { border-left-color: #ef4444; }

                    .dash-card .dash-label {
                        font-size: 0.75rem;
                        font-weight: 600;
                        text-transform: uppercase;
                        letter-spacing: 0.05em;
                        color: #64748b;
                    }

                    .dash-card .dash-value {
                        font-size: 1.125rem;
                        font-weight: 700;
                    }

                    .dash-card-ok .dash-value { color: #16a34a; }
                    .dash-card-warn .dash-value { color: #d97706; }
                    .dash-card-error .dash-value { color: #dc2626; }

                    /* Stats Bar */
                    .stats-bar {
                        background: white;
                        border-radius: 0.75rem;
                        padding: 0.75rem 1.5rem;
                        margin-bottom: 1.5rem;
                        box-shadow: 0 1px 3px rgba(0,0,0,0.1);
                        display: flex;
                        gap: 2rem;
                        flex-wrap: wrap;
                        font-size: 0.875rem;
                        color: #475569;
                    }

                    .stats-bar .stat { display: flex; align-items: center; gap: 0.375rem; }
                    .stats-bar .stat-num { font-weight: 700; color: #0f172a; font-size: 1rem; }

                    /* Error/Warning Detail Sections */
                    .issue-section {
                        border-radius: 0.75rem;
                        margin-bottom: 1rem;
                        overflow: hidden;
                        box-shadow: 0 1px 3px rgba(0,0,0,0.1);
                    }

                    .issue-header {
                        padding: 0.75rem 1.25rem;
                        font-weight: 700;
                        font-size: 0.9375rem;
                    }

                    .issue-section-error .issue-header { background: #fef2f2; color: #b91c1c; border-bottom: 1px solid #fca5a5; }
                    .issue-section-warning .issue-header { background: #fffbeb; color: #92400e; border-bottom: 1px solid #fcd34d; }

                    .issue-table {
                        width: 100%;
                        border-collapse: collapse;
                        font-size: 0.875rem;
                        background: white;
                    }

                    .issue-table th {
                        background: #f8fafc;
                        padding: 0.5rem 1rem;
                        text-align: left;
                        font-weight: 600;
                        color: #475569;
                        border-bottom: 1px solid #e2e8f0;
                    }

                    .issue-table td {
                        padding: 0.5rem 1rem;
                        border-bottom: 1px solid #f1f5f9;
                        vertical-align: top;
                    }

                    .issue-table tr:last-child td { border-bottom: none; }

                    .cat-badge {
                        display: inline-block;
                        padding: 0.125rem 0.5rem;
                        border-radius: 9999px;
                        font-size: 0.75rem;
                        font-weight: 600;
                        white-space: nowrap;
                    }

                    .cat-badge-structure { background: #ede9fe; color: #6d28d9; }
                    .cat-badge-required { background: #dbeafe; color: #1d4ed8; }
                    .cat-badge-reference { background: #fce7f3; color: #be185d; }
                    .cat-badge-datatype { background: #e0e7ff; color: #4338ca; }
                    .cat-badge-business { background: #fef3c7; color: #92400e; }
                    .cat-badge-complete { background: #d1fae5; color: #065f46; }

                    /* Section Cards */
                    .section-card {
                        background: white;
                        border-radius: 0.75rem;
                        margin-bottom: 1rem;
                        box-shadow: 0 1px 3px rgba(0,0,0,0.1);
                        overflow: hidden;
                    }

                    .section-card > details > summary,
                    .section-card > summary {
                        padding: 1rem 1.25rem;
                        cursor: pointer;
                        font-weight: 600;
                        color: #1e293b;
                        background: #f8fafc;
                        border-bottom: 1px solid #e2e8f0;
                        user-select: none;
                        list-style: none;
                        display: flex;
                        align-items: center;
                        gap: 0.5rem;
                    }

                    .section-card > details > summary::before,
                    .section-card > summary::before {
                        content: '\25B6';
                        font-size: 0.625rem;
                        transition: transform 0.2s;
                        color: #94a3b8;
                    }

                    .section-card > details[open] > summary::before,
                    .section-card[open] > summary::before {
                        transform: rotate(90deg);
                    }

                    .section-card > details > summary:hover,
                    .section-card > summary:hover {
                        background: #f1f5f9;
                    }

                    .section-body { padding: 1.25rem; }

                    /* Fund Header */
                    .fund-card {
                        background: white;
                        border-radius: 0.75rem;
                        margin-bottom: 1rem;
                        box-shadow: 0 1px 3px rgba(0,0,0,0.1);
                        overflow: hidden;
                    }

                    .fund-header-bar {
                        padding: 1rem 1.25rem;
                        background: #f8fafc;
                        border-bottom: 1px solid #e2e8f0;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        flex-wrap: wrap;
                        gap: 0.5rem;
                    }

                    .fund-header-bar h2 {
                        font-size: 1.25rem;
                        font-weight: 700;
                        color: #0f172a;
                        margin: 0;
                    }

                    .fund-badges { display: flex; gap: 0.375rem; flex-wrap: wrap; }

                    /* Data Tables */
                    .data-table {
                        width: 100%;
                        border-collapse: collapse;
                        font-size: 0.875rem;
                    }

                    .data-table thead th {
                        background: #1e293b;
                        color: white;
                        padding: 0.625rem 0.75rem;
                        text-align: left;
                        font-weight: 600;
                        white-space: nowrap;
                    }

                    .data-table tbody tr { border-bottom: 1px solid #f1f5f9; }
                    .data-table tbody tr:hover { background: #f8fafc; }

                    .data-table tbody td {
                        padding: 0.5rem 0.75rem;
                        vertical-align: top;
                    }

                    .data-table tbody th {
                        padding: 0.5rem 0.75rem;
                        background: #f8fafc;
                        font-weight: 600;
                        width: 30%;
                        text-align: left;
                        vertical-align: top;
                        color: #475569;
                    }

                    .data-table tfoot td {
                        padding: 0.625rem 0.75rem;
                        font-weight: 700;
                        background: #f8fafc;
                        border-top: 2px solid #e2e8f0;
                    }

                    /* Positions Table */
                    .positions-table thead th {
                        background: #334155;
                    }

                    .positions-table tbody tr:nth-child(even) { background: #f8fafc; }

                    /* Badges */
                    .badge {
                        display: inline-block;
                        padding: 0.125rem 0.5rem;
                        font-size: 0.75rem;
                        font-weight: 600;
                        border-radius: 9999px;
                    }

                    .badge-error { background: #fef2f2; color: #dc2626; border: 1px solid #fca5a5; }
                    .badge-warning { background: #fffbeb; color: #d97706; border: 1px solid #fcd34d; }
                    .badge-info { background: #eff6ff; color: #2563eb; border: 1px solid #bfdbfe; }
                    .badge-success { background: #f0fdf4; color: #16a34a; border: 1px solid #86efac; }
                    .badge-neutral { background: #f1f5f9; color: #475569; border: 1px solid #cbd5e1; }

                    /* Percentage badges */
                    .pct-badge {
                        display: inline-block;
                        padding: 0.125rem 0.5rem;
                        border-radius: 9999px;
                        font-weight: 600;
                        font-size: 0.8125rem;
                        background: #f1f5f9;
                        color: #334155;
                        border: 1px solid #cbd5e1;
                    }

                    .pct-badge-total {
                        background: #1e293b;
                        color: white;
                        padding: 0.125rem 0.625rem;
                        border-radius: 9999px;
                        font-weight: 700;
                    }

                    /* Links */
                    a { color: #2563eb; text-decoration: none; }
                    a:hover { text-decoration: underline; }

                    .position-link {
                        color: #2563eb;
                        font-weight: 600;
                        display: inline-flex;
                        align-items: center;
                        gap: 0.25rem;
                    }

                    .position-link:hover { color: #1d4ed8; }

                    /* Details inside fund cards */
                    .fund-card details {
                        border-top: 1px solid #f1f5f9;
                    }

                    .fund-card details > summary {
                        padding: 0.75rem 1.25rem;
                        cursor: pointer;
                        font-weight: 600;
                        font-size: 0.9375rem;
                        color: #334155;
                        background: #fafbfc;
                        user-select: none;
                        list-style: none;
                        display: flex;
                        align-items: center;
                        gap: 0.5rem;
                    }

                    .fund-card details > summary::before {
                        content: '\25B6';
                        font-size: 0.5625rem;
                        transition: transform 0.2s;
                        color: #94a3b8;
                    }

                    .fund-card details[open] > summary::before {
                        transform: rotate(90deg);
                    }

                    .fund-card details > summary:hover { background: #f1f5f9; }

                    .fund-card details > div { padding: 1rem 1.25rem; }

                    /* Navigation list */
                    .nav-list {
                        list-style: none;
                        margin: 0;
                        padding: 0;
                    }

                    .nav-list li {
                        padding: 0.25rem 0;
                    }

                    .nav-list .nav-sub {
                        list-style: none;
                        margin: 0.25rem 0 0 1.5rem;
                        padding: 0;
                        font-size: 0.8125rem;
                    }

                    /* Asset detail box */
                    .asset-detail-box {
                        margin-top: 0.375rem;
                        padding: 0.375rem 0.5rem;
                        background: #f8fafc;
                        border-radius: 0.25rem;
                        border: 1px solid #e2e8f0;
                        font-size: 0.75rem;
                    }

                    .asset-detail-row {
                        display: flex;
                        justify-content: space-between;
                        padding: 0.0625rem 0;
                    }

                    .asset-detail-label { font-weight: 600; color: #475569; }

                    /* Highlight target */
                    tr:target { animation: highlight 2s ease; background: #fef3c7 !important; }
                    tr[id] { scroll-margin-top: 2rem; }
                    @keyframes highlight {
                        0% { background: #fde047 !important; }
                        100% { background: #fef3c7 !important; }
                    }

                    /* Utilities */
                    .text-right { text-align: right; }
                    .text-center { text-align: center; }
                    .text-xs { font-size: 0.75rem; }
                    .text-sm { font-size: 0.875rem; }
                    .font-bold { font-weight: 700; }
                    .font-mono { font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace; }
                    .text-muted { color: #64748b; }
                    .overflow-x-auto { overflow-x: auto; }
                    .mb-1 { margin-bottom: 1rem; }

                    /* Section heading */
                    .section-heading {
                        font-size: 1.125rem;
                        font-weight: 700;
                        color: #0f172a;
                        margin: 1.5rem 0 0.75rem;
                        padding-bottom: 0.375rem;
                        border-bottom: 2px solid #e2e8f0;
                    }

                    .section-heading:first-of-type { margin-top: 0; }

                    /* Footer */
                    .report-footer {
                        text-align: center;
                        padding: 1.5rem;
                        color: #94a3b8;
                        font-size: 0.8125rem;
                    }

                    /* Print styles */
                    @media print {
                        body { background: white; }
                        .report { padding: 0; }
                        .section-card, .fund-card, .issue-section { box-shadow: none; border: 1px solid #e2e8f0; }
                        details[open] > summary { break-after: avoid; }
                        .data-table { page-break-inside: auto; }
                        .data-table tr { page-break-inside: avoid; }
                    }

                    /* Responsive */
                    @media (max-width: 768px) {
                        .report { padding: 0.75rem; }
                        .report-header { flex-direction: column; align-items: flex-start; }
                        .report-meta { flex-direction: column; gap: 0.25rem; }
                        .dashboard { grid-template-columns: repeat(2, 1fr); }
                        .stats-bar { flex-direction: column; gap: 0.5rem; }
                        .fund-header-bar { flex-direction: column; align-items: flex-start; }
                    }
                </style>
            </head>
            <body>
                <div class="report">

                    <!-- ==================== SECTION 1: HEADER ==================== -->
                    <div class="report-header">
                        <h1>FundsXML Data Quality Report</h1>
                        <div class="report-meta">
                            <span>File: <strong><xsl:value-of select="tokenize(base-uri(.), '/')[last()]"/></strong></span>
                            <span>Generated: <strong><xsl:value-of select="format-dateTime(current-dateTime(), '[Y0001]-[M01]-[D01] [H01]:[m01]')"/></strong></span>
                        </div>
                    </div>

                    <!-- ==================== SECTION 2: STATUS BANNER ==================== -->
                    <div>
                        <xsl:attribute name="class">status-banner <xsl:choose>
                            <xsl:when test="$total_errors > 0">status-fail</xsl:when>
                            <xsl:when test="$total_warnings > 0">status-warn</xsl:when>
                            <xsl:otherwise>status-pass</xsl:otherwise>
                        </xsl:choose></xsl:attribute>
                        <xsl:choose>
                            <xsl:when test="$total_errors > 0">
                                ERRORS FOUND &#8212; <xsl:value-of select="$total_errors"/> Error<xsl:if test="$total_errors != 1">s</xsl:if>
                                <xsl:if test="$total_warnings > 0">, <xsl:value-of select="$total_warnings"/> Warning<xsl:if test="$total_warnings != 1">s</xsl:if></xsl:if>
                            </xsl:when>
                            <xsl:when test="$total_warnings > 0">
                                WARNINGS &#8212; <xsl:value-of select="$total_warnings"/> Warning<xsl:if test="$total_warnings != 1">s</xsl:if>
                            </xsl:when>
                            <xsl:otherwise>
                                PASS &#8212; All Checks OK
                            </xsl:otherwise>
                        </xsl:choose>
                    </div>

                    <!-- ==================== SECTION 3: DASHBOARD CARDS ==================== -->
                    <div class="dashboard">
                        <!-- Structure -->
                        <div>
                            <xsl:attribute name="class">dash-card <xsl:choose>
                                <xsl:when test="$structure_errors > 0">dash-card-error</xsl:when>
                                <xsl:when test="$structure_warnings > 0">dash-card-warn</xsl:when>
                                <xsl:otherwise>dash-card-ok</xsl:otherwise>
                            </xsl:choose></xsl:attribute>
                            <span class="dash-label">Structure</span>
                            <span class="dash-value">
                                <xsl:choose>
                                    <xsl:when test="$structure_errors > 0"><xsl:value-of select="$structure_errors"/> Error<xsl:if test="$structure_errors != 1">s</xsl:if></xsl:when>
                                    <xsl:when test="$structure_warnings > 0"><xsl:value-of select="$structure_warnings"/> Warning<xsl:if test="$structure_warnings != 1">s</xsl:if></xsl:when>
                                    <xsl:otherwise>OK</xsl:otherwise>
                                </xsl:choose>
                            </span>
                        </div>
                        <!-- Required Fields -->
                        <div>
                            <xsl:attribute name="class">dash-card <xsl:choose>
                                <xsl:when test="$required_errors > 0">dash-card-error</xsl:when>
                                <xsl:when test="$required_warnings > 0">dash-card-warn</xsl:when>
                                <xsl:otherwise>dash-card-ok</xsl:otherwise>
                            </xsl:choose></xsl:attribute>
                            <span class="dash-label">Required Fields</span>
                            <span class="dash-value">
                                <xsl:choose>
                                    <xsl:when test="$required_errors > 0"><xsl:value-of select="$required_errors"/> Error<xsl:if test="$required_errors != 1">s</xsl:if></xsl:when>
                                    <xsl:when test="$required_warnings > 0"><xsl:value-of select="$required_warnings"/> Warn</xsl:when>
                                    <xsl:otherwise>OK</xsl:otherwise>
                                </xsl:choose>
                            </span>
                        </div>
                        <!-- Reference Integrity -->
                        <div>
                            <xsl:attribute name="class">dash-card <xsl:choose>
                                <xsl:when test="$reference_errors > 0">dash-card-error</xsl:when>
                                <xsl:when test="$reference_warnings > 0">dash-card-warn</xsl:when>
                                <xsl:otherwise>dash-card-ok</xsl:otherwise>
                            </xsl:choose></xsl:attribute>
                            <span class="dash-label">References</span>
                            <span class="dash-value">
                                <xsl:choose>
                                    <xsl:when test="$reference_errors > 0"><xsl:value-of select="$reference_errors"/> Error<xsl:if test="$reference_errors != 1">s</xsl:if></xsl:when>
                                    <xsl:when test="$reference_warnings > 0"><xsl:value-of select="$reference_warnings"/> Warn</xsl:when>
                                    <xsl:otherwise>OK</xsl:otherwise>
                                </xsl:choose>
                            </span>
                        </div>
                        <!-- Data Types -->
                        <div>
                            <xsl:attribute name="class">dash-card <xsl:choose>
                                <xsl:when test="$datatype_errors > 0">dash-card-error</xsl:when>
                                <xsl:when test="$datatype_warnings > 0">dash-card-warn</xsl:when>
                                <xsl:otherwise>dash-card-ok</xsl:otherwise>
                            </xsl:choose></xsl:attribute>
                            <span class="dash-label">Data Types</span>
                            <span class="dash-value">
                                <xsl:choose>
                                    <xsl:when test="$datatype_errors > 0"><xsl:value-of select="$datatype_errors"/> Error<xsl:if test="$datatype_errors != 1">s</xsl:if></xsl:when>
                                    <xsl:otherwise>OK</xsl:otherwise>
                                </xsl:choose>
                            </span>
                        </div>
                        <!-- Business Rules -->
                        <div>
                            <xsl:attribute name="class">dash-card <xsl:choose>
                                <xsl:when test="$business_errors > 0">dash-card-error</xsl:when>
                                <xsl:when test="$business_warnings > 0">dash-card-warn</xsl:when>
                                <xsl:otherwise>dash-card-ok</xsl:otherwise>
                            </xsl:choose></xsl:attribute>
                            <span class="dash-label">Business Rules</span>
                            <span class="dash-value">
                                <xsl:choose>
                                    <xsl:when test="$business_errors > 0"><xsl:value-of select="$business_errors"/> Error<xsl:if test="$business_errors != 1">s</xsl:if></xsl:when>
                                    <xsl:when test="$business_warnings > 0"><xsl:value-of select="$business_warnings"/> Warn</xsl:when>
                                    <xsl:otherwise>OK</xsl:otherwise>
                                </xsl:choose>
                            </span>
                        </div>
                        <!-- Completeness -->
                        <div>
                            <xsl:attribute name="class">dash-card <xsl:choose>
                                <xsl:when test="$completeness_errors > 0">dash-card-error</xsl:when>
                                <xsl:when test="$completeness_warnings > 0">dash-card-warn</xsl:when>
                                <xsl:otherwise>dash-card-ok</xsl:otherwise>
                            </xsl:choose></xsl:attribute>
                            <span class="dash-label">Completeness</span>
                            <span class="dash-value">
                                <xsl:choose>
                                    <xsl:when test="$completeness_warnings > 0"><xsl:value-of select="$completeness_warnings"/> Warn</xsl:when>
                                    <xsl:otherwise>OK</xsl:otherwise>
                                </xsl:choose>
                            </span>
                        </div>
                    </div>

                    <!-- Stats Bar -->
                    <div class="stats-bar">
                        <div class="stat"><span class="stat-num"><xsl:value-of select="$fund_count"/></span> Fund<xsl:if test="$fund_count != 1">s</xsl:if></div>
                        <div class="stat"><span class="stat-num"><xsl:value-of select="$shareclass_count"/></span> ShareClass<xsl:if test="$shareclass_count != 1">es</xsl:if></div>
                        <div class="stat"><span class="stat-num"><xsl:value-of select="$position_count"/></span> Position<xsl:if test="$position_count != 1">s</xsl:if></div>
                        <div class="stat"><span class="stat-num"><xsl:value-of select="$asset_count"/></span> Asset<xsl:if test="$asset_count != 1">s</xsl:if></div>
                    </div>

                    <!-- ==================== SECTION 4: ERROR DETAILS ==================== -->
                    <xsl:if test="$total_errors > 0">
                        <div class="issue-section issue-section-error">
                            <div class="issue-header">Errors (<xsl:value-of select="$total_errors"/>)</div>
                            <table class="issue-table">
                                <thead>
                                    <tr>
                                        <th style="width:120px">Category</th>
                                        <th>Description</th>
                                        <th style="width:200px">Location</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <!-- Structure errors -->
                                    <xsl:if test="$err_no_controldata = 1">
                                        <tr><td><span class="cat-badge cat-badge-structure">Structure</span></td><td>ControlData section is missing</td><td>/FundsXML4/ControlData</td></tr>
                                    </xsl:if>
                                    <xsl:if test="$err_no_contentdate = 1">
                                        <tr><td><span class="cat-badge cat-badge-structure">Structure</span></td><td>ContentDate is missing or empty</td><td>/FundsXML4/ControlData/ContentDate</td></tr>
                                    </xsl:if>
                                    <xsl:if test="$err_no_funds = 1">
                                        <tr><td><span class="cat-badge cat-badge-structure">Structure</span></td><td>No Fund elements found</td><td>/FundsXML4/Funds</td></tr>
                                    </xsl:if>
                                    <!-- Required field errors -->
                                    <xsl:if test="$err_no_datasupplier = 1">
                                        <tr><td><span class="cat-badge cat-badge-required">Required</span></td><td>DataSupplier is missing or has no Name/Short</td><td>/FundsXML4/ControlData/DataSupplier</td></tr>
                                    </xsl:if>
                                    <xsl:for-each select="$funds_missing_name">
                                        <tr><td><span class="cat-badge cat-badge-required">Required</span></td><td>Fund is missing OfficialName</td><td>Fund #<xsl:value-of select="position()"/></td></tr>
                                    </xsl:for-each>
                                    <xsl:for-each select="$funds_missing_currency">
                                        <tr><td><span class="cat-badge cat-badge-required">Required</span></td><td>Fund is missing Currency</td><td>Fund: <xsl:value-of select="(Names/OfficialName, concat('#', position()))[1]"/></td></tr>
                                    </xsl:for-each>
                                    <!-- Reference integrity errors -->
                                    <xsl:for-each select="$orphan_positions">
                                        <tr><td><span class="cat-badge cat-badge-reference">Reference</span></td><td>Position references non-existent asset</td><td>UniqueID: <xsl:value-of select="UniqueID"/></td></tr>
                                    </xsl:for-each>
                                    <!-- Data type errors -->
                                    <xsl:for-each select="$bad_currency_funds">
                                        <tr><td><span class="cat-badge cat-badge-datatype">DataType</span></td><td>Currency code is not 3 characters: "<xsl:value-of select="Currency"/>"</td><td>Fund: <xsl:value-of select="Names/OfficialName"/></td></tr>
                                    </xsl:for-each>
                                    <xsl:for-each select="$bad_isin_assets">
                                        <tr><td><span class="cat-badge cat-badge-datatype">DataType</span></td><td>Invalid ISIN format: "<xsl:value-of select="Identifiers/ISIN"/>"</td><td>Asset: <xsl:value-of select="UniqueID"/></td></tr>
                                    </xsl:for-each>
                                    <!-- Business rule errors -->
                                    <xsl:for-each select="$portfolios_pct_error">
                                        <tr><td><span class="cat-badge cat-badge-business">Business</span></td><td>Portfolio percentage sum is <xsl:value-of select="format-number(sum(Positions/Position/TotalPercentage), '0.00')"/>% (must be 90-110%)</td><td>Portfolio NavDate: <xsl:value-of select="NavDate"/></td></tr>
                                    </xsl:for-each>
                                    <xsl:for-each select="$assets_missing_isin">
                                        <tr><td><span class="cat-badge cat-badge-business">Business</span></td><td>Asset type <xsl:value-of select="AssetType"/> requires ISIN but none found</td><td>Asset: <xsl:value-of select="UniqueID"/> (<xsl:value-of select="Name"/>)</td></tr>
                                    </xsl:for-each>
                                </tbody>
                            </table>
                        </div>
                    </xsl:if>

                    <!-- ==================== SECTION 5: WARNING DETAILS ==================== -->
                    <xsl:if test="$total_warnings > 0">
                        <div class="issue-section issue-section-warning">
                            <div class="issue-header">Warnings (<xsl:value-of select="$total_warnings"/>)</div>
                            <table class="issue-table">
                                <thead>
                                    <tr>
                                        <th style="width:120px">Category</th>
                                        <th>Description</th>
                                        <th style="width:200px">Location</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <!-- Structure warnings -->
                                    <xsl:if test="$warn_no_assets = 1">
                                        <tr><td><span class="cat-badge cat-badge-structure">Structure</span></td><td>AssetMasterData section is empty or missing</td><td>/FundsXML4/AssetMasterData</td></tr>
                                    </xsl:if>
                                    <!-- Required field warnings -->
                                    <xsl:for-each select="$funds_missing_shareclass">
                                        <tr><td><span class="cat-badge cat-badge-required">Required</span></td><td>Fund has no ShareClasses</td><td>Fund: <xsl:value-of select="Names/OfficialName"/></td></tr>
                                    </xsl:for-each>
                                    <!-- Reference warnings -->
                                    <xsl:for-each select="$unused_assets">
                                        <tr><td><span class="cat-badge cat-badge-reference">Reference</span></td><td>Asset is not referenced by any position</td><td>Asset: <xsl:value-of select="UniqueID"/> (<xsl:value-of select="Name"/>)</td></tr>
                                    </xsl:for-each>
                                    <!-- Business rule warnings -->
                                    <xsl:for-each select="$portfolios_pct_warn">
                                        <tr><td><span class="cat-badge cat-badge-business">Business</span></td><td>Portfolio percentage sum is <xsl:value-of select="format-number(sum(Positions/Position/TotalPercentage), '0.00')"/>% (expected 95-105%)</td><td>Portfolio NavDate: <xsl:value-of select="NavDate"/></td></tr>
                                    </xsl:for-each>
                                    <!-- Completeness warnings -->
                                    <xsl:for-each select="$funds_no_tav">
                                        <tr><td><span class="cat-badge cat-badge-complete">Completeness</span></td><td>Fund has no TotalAssetValue</td><td>Fund: <xsl:value-of select="Names/OfficialName"/></td></tr>
                                    </xsl:for-each>
                                    <xsl:for-each select="$funds_no_portfolio">
                                        <tr><td><span class="cat-badge cat-badge-complete">Completeness</span></td><td>Fund has no portfolio positions</td><td>Fund: <xsl:value-of select="Names/OfficialName"/></td></tr>
                                    </xsl:for-each>
                                </tbody>
                            </table>
                        </div>
                    </xsl:if>

                    <!-- ==================== SECTION 6: CONTROL DATA ==================== -->
                    <xsl:apply-templates select="FundsXML4/ControlData"/>

                    <!-- ==================== SECTION 7: FUND NAVIGATION ==================== -->
                    <xsl:if test="$fund_count > 1">
                        <details class="section-card">
                            <summary>Fund Navigation</summary>
                            <div class="section-body">
                                <ul class="nav-list">
                                    <xsl:for-each select="FundsXML4/Funds/Fund">
                                        <li>
                                            <a href="#{generate-id(.)}"><xsl:value-of select="Names/OfficialName"/></a>
                                            <xsl:text> </xsl:text><span class="badge badge-neutral"><xsl:value-of select="Currency"/></span>
                                            <xsl:if test="SingleFund/ShareClasses/ShareClass">
                                                <ul class="nav-sub">
                                                    <xsl:for-each select="SingleFund/ShareClasses/ShareClass">
                                                        <li>
                                                            <a href="#{generate-id(.)}"><xsl:value-of select="Identifiers/ISIN"/></a>
                                                            <xsl:text> </xsl:text><span class="text-muted text-xs"><xsl:value-of select="Names/OfficialName"/></span>
                                                        </li>
                                                    </xsl:for-each>
                                                </ul>
                                            </xsl:if>
                                        </li>
                                    </xsl:for-each>
                                </ul>
                                <xsl:if test="$asset_count > 0">
                                    <div style="margin-top:0.75rem">
                                        <a href="#AssetMasterData">Asset Master Data (<xsl:value-of select="$asset_count"/> assets)</a>
                                    </div>
                                </xsl:if>
                            </div>
                        </details>
                    </xsl:if>

                    <!-- ==================== SECTION 8: FUND DETAILS ==================== -->
                    <xsl:apply-templates select="FundsXML4/Funds/Fund"/>

                    <!-- ==================== SECTION 9: ASSET MASTER DATA ==================== -->
                    <xsl:apply-templates select="FundsXML4/AssetMasterData"/>

                    <!-- Footer -->
                    <div class="report-footer">
                        Generated with FreeXMLToolkit
                    </div>
                </div>
            </body>
        </html>
    </xsl:template>

    <!-- ==================== CONTROLDATA TEMPLATE ==================== -->
    <xsl:template match="ControlData">
        <details class="section-card">
            <summary>File Overview (ControlData)</summary>
            <div class="section-body">
                <table class="data-table">
                    <tbody>
                        <xsl:for-each select="*[not(self::DataSupplier)]">
                            <tr>
                                <th><xsl:value-of select="name()"/></th>
                                <td><xsl:value-of select="text()"/></td>
                            </tr>
                        </xsl:for-each>
                        <xsl:if test="DataSupplier">
                            <tr>
                                <th>DataSupplier</th>
                                <td>
                                    <xsl:value-of select="(DataSupplier/Name[normalize-space(.) != ''], DataSupplier/Short)[1]"/>
                                    <xsl:if test="DataSupplier/Type">
                                        <xsl:text> </xsl:text><span class="badge badge-info"><xsl:value-of select="DataSupplier/Type"/></span>
                                    </xsl:if>
                                    <xsl:if test="DataSupplier/SystemCountry">
                                        <xsl:text> </xsl:text><span class="badge badge-neutral"><xsl:value-of select="DataSupplier/SystemCountry"/></span>
                                    </xsl:if>
                                </td>
                            </tr>
                            <xsl:if test="DataSupplier/Contact/Email">
                                <tr>
                                    <th>Contact Email</th>
                                    <td><xsl:value-of select="DataSupplier/Contact/Email"/></td>
                                </tr>
                            </xsl:if>
                        </xsl:if>
                    </tbody>
                </table>
            </div>
        </details>
    </xsl:template>

    <!-- ==================== FUND TEMPLATE ==================== -->
    <xsl:template match="Fund">
        <xsl:variable name="fundCurrency" select="Currency"/>
        <div id="{generate-id(.)}" class="fund-card">
            <!-- Always-visible header -->
            <div class="fund-header-bar">
                <h2><xsl:value-of select="Names/OfficialName"/></h2>
                <div class="fund-badges">
                    <span class="badge badge-info"><xsl:value-of select="Currency"/></span>
                    <xsl:if test="SingleFund/ShareClasses/ShareClass">
                        <span class="badge badge-neutral"><xsl:value-of select="count(SingleFund/ShareClasses/ShareClass)"/> ShareClass<xsl:if test="count(SingleFund/ShareClasses/ShareClass) != 1">es</xsl:if></span>
                    </xsl:if>
                    <xsl:if test="count(SingleFund/ShareClasses/ShareClass) = 0">
                        <span class="badge badge-error">No ShareClasses</span>
                    </xsl:if>
                    <xsl:if test="Identifiers/LEI">
                        <span class="badge badge-neutral">LEI: <xsl:value-of select="Identifiers/LEI"/></span>
                    </xsl:if>
                    <xsl:if test="FundStaticData/ListedLegalStructure">
                        <span class="badge badge-success"><xsl:value-of select="FundStaticData/ListedLegalStructure"/></span>
                    </xsl:if>
                </div>
            </div>

            <!-- Identifiers and Names -->
            <details>
                <summary>Identifiers and Names</summary>
                <div>
                    <table class="data-table">
                        <tbody>
                            <xsl:for-each select="Names/*">
                                <tr>
                                    <th><xsl:value-of select="name()"/></th>
                                    <td><xsl:value-of select="text()"/></td>
                                </tr>
                            </xsl:for-each>
                            <xsl:for-each select="Identifiers/*[not(self::OtherID)]">
                                <tr>
                                    <th><xsl:value-of select="name()"/></th>
                                    <td class="font-mono"><xsl:value-of select="."/></td>
                                </tr>
                            </xsl:for-each>
                            <xsl:for-each select="Identifiers/OtherID">
                                <tr>
                                    <th>OtherID
                                        <xsl:for-each select="@*">
                                            <br/><span class="badge badge-info text-xs">@<xsl:value-of select="name()"/>: <xsl:value-of select="."/></span>
                                        </xsl:for-each>
                                    </th>
                                    <td class="font-mono"><xsl:value-of select="."/></td>
                                </tr>
                            </xsl:for-each>
                        </tbody>
                    </table>
                </div>
            </details>

            <!-- FundStaticData -->
            <xsl:if test="FundStaticData">
                <details>
                    <summary>FundStaticData</summary>
                    <div>
                        <table class="data-table">
                            <tbody>
                                <xsl:for-each select="FundStaticData/*">
                                    <tr>
                                        <th><xsl:value-of select="name()"/></th>
                                        <td><xsl:value-of select="."/></td>
                                    </tr>
                                </xsl:for-each>
                            </tbody>
                        </table>
                    </div>
                </details>
            </xsl:if>

            <!-- FundDynamicData -->
            <xsl:if test="FundDynamicData">
                <details>
                    <summary>FundDynamicData</summary>
                    <div>
                        <!-- TotalAssetValues -->
                        <xsl:for-each select="FundDynamicData/TotalAssetValues/TotalAssetValue">
                            <h3 class="section-heading" style="font-size:0.9375rem">TotalAssetValue &#8212; <xsl:value-of select="NavDate"/></h3>
                            <table class="data-table mb-1">
                                <tbody>
                                    <tr>
                                        <th>NavDate</th>
                                        <td><xsl:value-of select="NavDate"/></td>
                                    </tr>
                                    <xsl:if test="TotalAssetNature">
                                        <tr>
                                            <th>TotalAssetNature</th>
                                            <td><xsl:value-of select="TotalAssetNature"/></td>
                                        </tr>
                                    </xsl:if>
                                    <xsl:if test="TotalNetAssetValue">
                                        <tr>
                                            <th>TotalNetAssetValue</th>
                                            <td class="text-right font-bold">
                                                <xsl:value-of select="format-number((TotalNetAssetValue/Amount[@ccy=$fundCurrency], TotalNetAssetValue/Amount)[1], '#,##0.00')"/>
                                                <xsl:text> </xsl:text>
                                                <xsl:value-of select="(TotalNetAssetValue/Amount[@ccy=$fundCurrency], TotalNetAssetValue/Amount)[1]/@ccy"/>
                                            </td>
                                        </tr>
                                    </xsl:if>
                                    <xsl:for-each select="*[not(self::NavDate) and not(self::TotalAssetNature) and not(self::TotalNetAssetValue)]">
                                        <tr>
                                            <th><xsl:value-of select="name()"/></th>
                                            <td>
                                                <xsl:choose>
                                                    <xsl:when test="Amount">
                                                        <xsl:value-of select="format-number((Amount[@ccy=$fundCurrency], Amount)[1], '#,##0.00')"/>
                                                        <xsl:text> </xsl:text><xsl:value-of select="(Amount[@ccy=$fundCurrency], Amount)[1]/@ccy"/>
                                                    </xsl:when>
                                                    <xsl:otherwise><xsl:value-of select="."/></xsl:otherwise>
                                                </xsl:choose>
                                            </td>
                                        </tr>
                                    </xsl:for-each>
                                </tbody>
                            </table>
                        </xsl:for-each>

                        <!-- Portfolios -->
                        <xsl:for-each select="FundDynamicData/Portfolios/Portfolio">
                            <xsl:variable name="portfolioCcy" select="PortfolioCurrency"/>
                            <h3 class="section-heading" style="font-size:0.9375rem">Portfolio &#8212; <xsl:value-of select="NavDate"/> (<xsl:value-of select="count(Positions/Position)"/> Positions)</h3>
                            <div class="overflow-x-auto mb-1">
                                <table class="data-table positions-table">
                                    <thead>
                                        <tr>
                                            <th>#</th>
                                            <th>UniqueID</th>
                                            <th>Type</th>
                                            <th>CCY</th>
                                            <th class="text-right">Total Value</th>
                                            <th class="text-center">%</th>
                                            <th>Details</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        <xsl:for-each select="Positions/Position">
                                            <xsl:sort select="TotalPercentage" data-type="number" order="descending"/>
                                            <tr>
                                                <td class="text-center text-muted"><xsl:value-of select="position()"/></td>
                                                <td>
                                                    <xsl:choose>
                                                        <xsl:when test="key('asset-by-id', UniqueID)">
                                                            <a href="#{UniqueID}" class="position-link" title="Jump to Asset Master Data"><xsl:value-of select="UniqueID"/></a>
                                                        </xsl:when>
                                                        <xsl:otherwise>
                                                            <span class="text-muted"><xsl:value-of select="UniqueID"/></span>
                                                            <xsl:text> </xsl:text><span class="badge badge-warning">orphan</span>
                                                        </xsl:otherwise>
                                                    </xsl:choose>
                                                </td>
                                                <td>
                                                    <span class="badge badge-info">
                                                        <xsl:value-of select="name(Equity | Bond | ShareClass | Warrant | Certificate | Option | Future | FXForward | Swap | Repo | FixedTimeDeposit | CallMoney | Account | Fee | RealEstate | REIT | Loan | Right | Commodity | PrivateEquity | CommercialPaper | Index | Crypto)"/>
                                                    </span>
                                                </td>
                                                <td>
                                                    <xsl:value-of select="Currency"/>
                                                    <xsl:if test="Currency != $portfolioCcy and $portfolioCcy">
                                                        <xsl:text> </xsl:text><span class="badge badge-warning">FX</span>
                                                    </xsl:if>
                                                </td>
                                                <td class="text-right font-bold">
                                                    <xsl:value-of select="format-number((TotalValue/Amount[@ccy=$fundCurrency], TotalValue/Amount)[1], '#,##0.00')"/>
                                                    <span class="text-muted text-xs"><xsl:text> </xsl:text><xsl:value-of select="(TotalValue/Amount[@ccy=$fundCurrency], TotalValue/Amount)[1]/@ccy"/></span>
                                                </td>
                                                <td class="text-center">
                                                    <span class="pct-badge"><xsl:value-of select="format-number(TotalPercentage, '0.00')"/>%</span>
                                                </td>
                                                <td>
                                                    <xsl:apply-templates select="Equity | Bond | ShareClass | Warrant | Certificate | Option | Future | FXForward | Swap | Repo | FixedTimeDeposit | CallMoney | Account | Fee | RealEstate | REIT | Loan | Right | Commodity | PrivateEquity | CommercialPaper | Index | Crypto">
                                                        <xsl:with-param name="inline" select="true()"/>
                                                    </xsl:apply-templates>
                                                </td>
                                            </tr>
                                        </xsl:for-each>
                                    </tbody>
                                    <tfoot>
                                        <tr>
                                            <td colspan="4" class="text-right font-bold">Total:</td>
                                            <td class="text-right font-bold">
                                                <xsl:value-of select="format-number(sum(Positions/Position/TotalValue/Amount[@ccy=$fundCurrency]), '#,##0.00')"/>
                                            </td>
                                            <td class="text-center">
                                                <span class="pct-badge-total"><xsl:value-of select="format-number(sum(Positions/Position/TotalPercentage), '0.00')"/>%</span>
                                            </td>
                                            <td/>
                                        </tr>
                                    </tfoot>
                                </table>
                            </div>
                        </xsl:for-each>
                    </div>
                </details>
            </xsl:if>

            <!-- ShareClasses -->
            <xsl:if test="SingleFund/ShareClasses/ShareClass">
                <details>
                    <summary>ShareClasses (<xsl:value-of select="count(SingleFund/ShareClasses/ShareClass)"/>)</summary>
                    <div>
                        <xsl:for-each select="SingleFund/ShareClasses/ShareClass">
                            <div id="{generate-id(.)}" style="margin-bottom:1rem">
                                <h3 class="section-heading" style="font-size:0.9375rem">
                                    <xsl:value-of select="Identifiers/ISIN"/>
                                    <xsl:text> &#8212; </xsl:text>
                                    <xsl:value-of select="Names/OfficialName"/>
                                </h3>
                                <table class="data-table">
                                    <tbody>
                                        <xsl:for-each select="Names/*">
                                            <tr>
                                                <th>Name: <xsl:value-of select="name()"/></th>
                                                <td><xsl:value-of select="."/></td>
                                            </tr>
                                        </xsl:for-each>
                                        <xsl:for-each select="Identifiers/*">
                                            <tr>
                                                <th>ID: <xsl:value-of select="name()"/></th>
                                                <td class="font-mono font-bold"><xsl:value-of select="."/></td>
                                            </tr>
                                        </xsl:for-each>
                                        <tr>
                                            <th>Currency</th>
                                            <td><xsl:value-of select="Currency"/></td>
                                        </tr>
                                        <xsl:if test="ShareClassType">
                                            <tr>
                                                <th>Type</th>
                                                <td>
                                                    <xsl:value-of select="ShareClassType/Code"/>
                                                    <xsl:if test="ShareClassType/EarningUse">
                                                        <xsl:text> / </xsl:text><xsl:value-of select="ShareClassType/EarningUse"/>
                                                    </xsl:if>
                                                </td>
                                            </tr>
                                        </xsl:if>
                                        <xsl:if test="Prices/Price">
                                            <tr>
                                                <th>NAV Price</th>
                                                <td class="font-bold">
                                                    <xsl:value-of select="Prices/Price[1]/NavPrice"/>
                                                    <xsl:text> </xsl:text><xsl:value-of select="Prices/Price[1]/PriceCurrency"/>
                                                    <xsl:text> (</xsl:text><xsl:value-of select="Prices/Price[1]/NavDate"/><xsl:text>)</xsl:text>
                                                </td>
                                            </tr>
                                        </xsl:if>
                                        <xsl:if test="TotalAssetValues/TotalAssetValue">
                                            <tr>
                                                <th>Net Asset Value</th>
                                                <td class="font-bold">
                                                    <xsl:variable name="tav" select="TotalAssetValues/TotalAssetValue[1]"/>
                                                    <xsl:value-of select="format-number($tav/TotalNetAssetValue/Amount[1], '#,##0.00')"/>
                                                    <xsl:text> </xsl:text><xsl:value-of select="$tav/TotalNetAssetValue/Amount[1]/@ccy"/>
                                                    <xsl:if test="$tav/SharesOutstanding">
                                                        <xsl:text> (</xsl:text><xsl:value-of select="format-number($tav/SharesOutstanding, '#,##0.000')"/> shares<xsl:text>)</xsl:text>
                                                    </xsl:if>
                                                </td>
                                            </tr>
                                        </xsl:if>
                                    </tbody>
                                </table>
                            </div>
                        </xsl:for-each>
                    </div>
                </details>
            </xsl:if>
        </div>
    </xsl:template>

    <!-- ==================== POSITION ASSET TYPE TEMPLATES ==================== -->
    <xsl:template match="Position/Equity | Position/Bond | Position/ShareClass | Position/Warrant | Position/Certificate | Position/Option | Position/Future">
        <xsl:param name="inline" select="false()"/>
        <xsl:choose>
            <xsl:when test="$inline">
                <span class="text-sm">
                    <xsl:if test="ISIN"><span class="font-mono text-xs">ISIN: <xsl:value-of select="ISIN"/></span></xsl:if>
                </span>
            </xsl:when>
            <xsl:otherwise>
                <div class="asset-detail-box">
                    <span class="font-bold"><xsl:value-of select="name(.)"/></span>
                </div>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="Position/FXForward | Position/Swap | Position/Repo | Position/FixedTimeDeposit | Position/CallMoney | Position/Account | Position/Fee | Position/RealEstate | Position/REIT | Position/Loan | Position/Right | Position/Commodity | Position/PrivateEquity | Position/CommercialPaper | Position/Index | Position/Crypto">
        <xsl:param name="inline" select="false()"/>
        <xsl:choose>
            <xsl:when test="$inline">
                <span class="text-sm text-muted"><xsl:value-of select="name(.)"/></span>
            </xsl:when>
            <xsl:otherwise>
                <div class="asset-detail-box">
                    <span class="font-bold"><xsl:value-of select="name(.)"/></span>
                </div>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <!-- ==================== ASSET MASTER DATA TEMPLATE ==================== -->
    <xsl:template match="AssetMasterData">
        <div id="AssetMasterData" class="fund-card">
            <div class="fund-header-bar">
                <h2>Asset Master Data</h2>
                <div class="fund-badges">
                    <span class="badge badge-neutral"><xsl:value-of select="count(Asset)"/> Assets</span>
                </div>
            </div>
            <div style="padding:1rem 1.25rem">
                <div class="overflow-x-auto">
                    <table class="data-table positions-table">
                        <thead>
                            <tr>
                                <th>#</th>
                                <th>UniqueID</th>
                                <th>Identifiers</th>
                                <th>Name</th>
                                <th>CCY</th>
                                <th>Country</th>
                                <th>Type</th>
                                <th>Details</th>
                            </tr>
                        </thead>
                        <tbody>
                            <xsl:for-each select="Asset">
                                <tr id="{UniqueID}">
                                    <td class="text-center text-muted"><xsl:value-of select="position()"/></td>
                                    <td>
                                        <strong class="font-mono"><xsl:value-of select="UniqueID"/></strong>
                                        <xsl:if test="key('position-by-uid', UniqueID)">
                                            <xsl:text> </xsl:text><span class="badge badge-success">in use</span>
                                        </xsl:if>
                                        <xsl:if test="not(key('position-by-uid', UniqueID))">
                                            <xsl:text> </xsl:text><span class="badge badge-warning">unused</span>
                                        </xsl:if>
                                    </td>
                                    <td>
                                        <xsl:for-each select="Identifiers/*[not(self::OtherID)]">
                                            <span class="font-mono text-xs"><xsl:value-of select="name()"/>: <xsl:value-of select="."/></span><br/>
                                        </xsl:for-each>
                                        <xsl:for-each select="Identifiers/OtherID">
                                            <span class="text-xs text-muted">
                                                <xsl:value-of select="concat('OtherID[@', attribute(), ']: ', .)"/>
                                            </span><br/>
                                        </xsl:for-each>
                                        <xsl:if test="AssetType = ('EQ', 'BO', 'SC') and not(Identifiers/ISIN)">
                                            <span class="badge badge-error">Missing ISIN</span>
                                        </xsl:if>
                                    </td>
                                    <td><xsl:value-of select="Name"/></td>
                                    <td><xsl:value-of select="Currency"/></td>
                                    <td><xsl:value-of select="Country"/></td>
                                    <td>
                                        <span class="badge badge-info"><xsl:value-of select="AssetType"/></span>
                                        <xsl:if test="AssetDetails/*">
                                            <br/><span class="text-xs text-muted"><xsl:value-of select="name(AssetDetails/*[1])"/></span>
                                        </xsl:if>
                                    </td>
                                    <td>
                                        <xsl:apply-templates select="AssetDetails/*"/>
                                    </td>
                                </tr>
                            </xsl:for-each>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    </xsl:template>

    <!-- ==================== ASSET DETAIL TEMPLATES ==================== -->
    <xsl:template match="AssetDetails/Equity | AssetDetails/Bond | AssetDetails/ShareClass | AssetDetails/Warrant | AssetDetails/Certificate | AssetDetails/Option | AssetDetails/Future | AssetDetails/FXForward | AssetDetails/Swap | AssetDetails/Account | AssetDetails/Fee" priority="10">
        <div class="asset-detail-box">
            <xsl:for-each select="*[not(*)]">
                <div class="asset-detail-row">
                    <span class="asset-detail-label"><xsl:value-of select="name()"/>:</span>
                    <span><xsl:value-of select="."/></span>
                </div>
            </xsl:for-each>
        </div>
    </xsl:template>

    <xsl:template match="AssetDetails/*" priority="1">
        <div class="asset-detail-box">
            <span class="font-bold"><xsl:value-of select="name(.)"/></span>
        </div>
    </xsl:template>

</xsl:stylesheet>
