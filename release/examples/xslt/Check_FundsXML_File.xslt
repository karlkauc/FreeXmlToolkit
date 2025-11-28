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
	<xsl:variable name="renderXMLContent" select="true()"/>
	<xsl:key name="asset-by-id" match="//AssetMasterData/Asset" use="UniqueID"/>
	<xsl:template match="/">
		<html lang="en">
			<head>
				<title>FundsXML Analysis Report</title>
				<meta charset="utf-8"/>
				<meta name="viewport" content="width=device-width, initial-scale=1"/>
				<meta http-equiv="X-UA-Compatible" content="IE=edge"/>
				<meta name="viewport" content="width=device-width, user-scalable=no, initial-scale=1, maximum-scale=1"/>
				<meta name="apple-mobile-web-app-capable" content="yes"/>
				<meta name="mobile-web-app-capable" content="yes"/>
				<meta name="apple-mobile-web-app-status-bar-style" content="black-translucent"/>
				<meta name="theme-color" content="#6366F1"/>
				<meta name="apple-mobile-web-app-status-bar-style" content="black-translucent"/>
				<style>
                    /* Reset and Base Styles */
                    * {
                        margin: 0;
                        padding: 0;
                        box-sizing: border-box;
                    }
                    
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        min-height: 100vh;
                        padding: 2rem 1rem;
                        line-height: 1.6;
                    }
                    
                    /* Main Container */
                    main {
                        max-width: 1400px;
                        margin: 0 auto;
                        background: rgba(255, 255, 255, 0.98);
                        border-radius: 1.5rem;
                        box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
                        padding: 2rem;
                        backdrop-filter: blur(10px);
                    }
                    
                    /* Typography */
                    h1 {
                        font-size: 2.25rem;
                        font-weight: bold;
                        margin-bottom: 1rem;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        -webkit-background-clip: text;
                        -webkit-text-fill-color: transparent;
                        background-clip: text;
                        padding-bottom: 0.5rem;
                        border-bottom: 3px solid;
                        border-image: linear-gradient(135deg, #667eea 0%, #764ba2 100%) 1;
                    }
                    
                    h2 {
                        font-size: 1.5rem;
                        font-weight: 600;
                        margin: 1.5rem 0 1rem;
                        color: #1F2937;
                    }
                    
                    h3 {
                        font-size: 1.25rem;
                        font-weight: 600;
                        margin: 1rem 0 0.5rem;
                        color: #374151;
                    }
                    
                    /* Header Info Table */
                    .info-table {
                        width: 100%;
                        margin-bottom: 2rem;
                        background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
                        border-radius: 1rem;
                        overflow: hidden;
                        box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1);
                    }
                    
                    .info-table tbody tr {
                        border-bottom: 1px solid rgba(255, 255, 255, 0.3);
                    }
                    
                    .info-table tbody tr:last-child {
                        border-bottom: none;
                    }
                    
                    .info-table th {
                        padding: 0.75rem 1rem;
                        text-align: left;
                        font-weight: 600;
                        width: 30%;
                        color: #4C1D95;
                        background: rgba(255, 255, 255, 0.5);
                    }
                    
                    .info-table td {
                        padding: 0.75rem 1rem;
                        color: #1F2937;
                    }
                    
                    /* Error and Warning Sections */
                    .error-section {
                        background: linear-gradient(135deg, #FEE2E2 0%, #FECACA 100%);
                        border-left: 4px solid #DC2626;
                        border-radius: 0.75rem;
                        padding: 1.5rem;
                        margin: 2rem 0;
                    }
                    
                    .error-section h1 {
                        color: #DC2626;
                        font-size: 1.5rem;
                        margin-bottom: 0.5rem;
                        border: none;
                        -webkit-text-fill-color: initial;
                    }
                    
                    .warning-section {
                        background: linear-gradient(135deg, #FEF3C7 0%, #FDE68A 100%);
                        border-left: 4px solid #F59E0B;
                        border-radius: 0.75rem;
                        padding: 1.5rem;
                        margin: 2rem 0;
                    }
                    
                    .warning-section h1 {
                        color: #D97706;
                        font-size: 1.5rem;
                        margin-bottom: 0.5rem;
                        border: none;
                        -webkit-text-fill-color: initial;
                    }
                    
                    /* Lists */
                    ul, ol {
                        margin-left: 1.5rem;
                        margin-top: 0.5rem;
                    }
                    
                    ul li, ol li {
                        margin-bottom: 0.25rem;
                    }
                    
                    /* Links */
                    a {
                        color: #6366F1;
                        text-decoration: none;
                        transition: all 0.2s ease;
                    }
                    
                    a:hover {
                        color: #4F46E5;
                        text-decoration: underline;
                    }
                    
                    /* Horizontal Rule */
                    hr {
                        margin: 2rem 0;
                        border: none;
                        height: 2px;
                        background: linear-gradient(to right, transparent, #E5E7EB, transparent);
                    }
                    
                    /* Tables */
                    table {
                        width: 100%;
                        border-collapse: collapse;
                        margin: 1rem 0;
                    }
                    
                    table.data-table {
                        background: white;
                        border-radius: 0.5rem;
                        overflow: hidden;
                        box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
                    }
                    
                    table thead {
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: white;
                    }
                    
                    table thead th {
                        padding: 0.75rem;
                        text-align: left;
                        font-weight: 600;
                        border-right: 1px solid rgba(255, 255, 255, 0.2);
                    }
                    
                    table thead th:last-child {
                        border-right: none;
                    }
                    
                    table tbody tr {
                        border-bottom: 1px solid #E5E7EB;
                        transition: background-color 0.2s ease;
                    }
                    
                    table tbody tr:hover {
                        background: #F9FAFB;
                    }
                    
                    table tbody tr:nth-child(even) {
                        background: #F9FAFB;
                    }
                    
                    table tbody td, table tbody th {
                        padding: 0.75rem;
                        vertical-align: top;
                    }
                    
                    table tbody th {
                        background: #F3F4F6;
                        font-weight: 600;
                        text-align: center;
                    }
                    
                    /* Details/Summary */
                    details {
                        margin: 1rem 0;
                        background: white;
                        border: 2px solid #E5E7EB;
                        border-radius: 0.75rem;
                        overflow: hidden;
                        transition: all 0.3s ease;
                    }
                    
                    details[open] {
                        box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1);
                    }
                    
                    summary {
                        padding: 1rem;
                        background: linear-gradient(135deg, #EEF2FF 0%, #E0E7FF 100%);
                        cursor: pointer;
                        font-weight: 600;
                        color: #4C1D95;
                        transition: all 0.2s ease;
                        user-select: none;
                    }
                    
                    summary:hover {
                        background: linear-gradient(135deg, #E0E7FF 0%, #C7D2FE 100%);
                    }
                    
                    summary::marker {
                        color: #6366F1;
                    }
                    
                    details > div {
                        padding: 1rem;
                    }
                    
                    /* Fund Cards */
                    .fund-section {
                        background: white;
                        border-radius: 1rem;
                        padding: 1.5rem;
                        margin: 1.5rem 0;
                        box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
                        border: 2px solid transparent;
                        transition: all 0.3s ease;
                    }
                    
                    .fund-section:hover {
                        border-color: #6366F1;
                        box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1);
                    }
                    
                    /* Asset Master Link */
                    .asset-link {
                        display: inline-flex;
                        align-items: center;
                        font-size: 1.25rem;
                        font-weight: 600;
                        color: #6366F1;
                        padding: 0.75rem 1.5rem;
                        background: linear-gradient(135deg, #EEF2FF 0%, #E0E7FF 100%);
                        border-radius: 0.5rem;
                        transition: all 0.3s ease;
                    }
                    
                    .asset-link:hover {
                        transform: translateX(5px);
                        box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
                    }
                    
                    .asset-link svg {
                        margin-right: 0.5rem;
                    }
                    
                    /* Badge Styles */
                    .badge {
                        display: inline-block;
                        padding: 0.25rem 0.75rem;
                        font-size: 0.75rem;
                        font-weight: 600;
                        border-radius: 9999px;
                        margin: 0.25rem;
                    }
                    
                    .badge-error {
                        background: #FEE2E2;
                        color: #DC2626;
                    }
                    
                    .badge-warning {
                        background: #FEF3C7;
                        color: #D97706;
                    }
                    
                    .badge-info {
                        background: #DBEAFE;
                        color: #2563EB;
                    }
                    
                    .badge-success {
                        background: #D1FAE5;
                        color: #059669;
                    }
                    
                    /* Position Cards */
                    .position-card {
                        background: #F9FAFB;
                        border-radius: 0.5rem;
                        padding: 0.5rem;
                        margin: 0.5rem 0;
                        border: 1px solid #E5E7EB;
                    }
                    
                    .position-card .position-type {
                        font-weight: bold;
                        font-size: 0.875rem;
                        color: #6366F1;
                        margin-bottom: 0.25rem;
                    }
                    
                    /* Positions Table Styles */
                    .positions-table {
                        width: 100%;
                        margin-top: 1rem;
                    }
                    
                    .positions-table thead {
                        background: linear-gradient(135deg, #3B82F6 0%, #2563EB 100%);
                        color: white;
                    }
                    
                    .positions-table thead th {
                        padding: 0.75rem;
                        text-align: left;
                        font-weight: 600;
                        white-space: nowrap;
                    }
                    
                    .positions-table tbody tr {
                        border-bottom: 1px solid #E5E7EB;
                        transition: background-color 0.2s ease;
                    }
                    
                    .positions-table tbody tr:hover {
                        background: rgba(99, 102, 241, 0.05);
                    }
                    
                    .position-row-even {
                        background: #F9FAFB;
                    }
                    
                    .position-row-odd {
                        background: white;
                    }
                    
                    .positions-table tfoot tr {
                        background: linear-gradient(135deg, #F3F4F6 0%, #E5E7EB 100%);
                        border-top: 2px solid #6366F1;
                    }
                    
                    .positions-summary td {
                        padding: 0.75rem;
                        font-weight: bold;
                    }
                    
                    .percentage-badge {
                        display: inline-block;
                        padding: 0.25rem 0.75rem;
                        background: linear-gradient(135deg, #EEF2FF 0%, #E0E7FF 100%);
                        border: 1px solid #C7D2FE;
                        border-radius: 9999px;
                        font-weight: 600;
                        color: #4F46E5;
                    }
                    
                    .percentage-badge-total {
                        display: inline-block;
                        padding: 0.25rem 0.75rem;
                        background: linear-gradient(135deg, #6366F1 0%, #4F46E5 100%);
                        color: white;
                        border-radius: 9999px;
                        font-weight: bold;
                    }
                    
                    .asset-type-inline {
                        font-size: 0.875rem;
                        color: #374151;
                    }
                    
                    /* ShareClass Table */
                    .shareclass-table {
                        background: linear-gradient(135deg, #F0F9FF 0%, #E0F2FE 100%);
                        border-radius: 0.5rem;
                        overflow: hidden;
                        margin: 1rem 0;
                    }
                    
                    .shareclass-table thead {
                        background: linear-gradient(135deg, #0EA5E9 0%, #0284C7 100%);
                    }
                    
                    /* Asset Master Data Section */
                    #AssetMasterData {
                        background: linear-gradient(135deg, #FEF3C7 0%, #FDE68A 100%);
                        border-radius: 1rem;
                        padding: 2rem;
                        margin: 2rem 0;
                    }
                    
                    #AssetMasterData h1 {
                        color: #92400E;
                        border-bottom-color: #F59E0B;
                    }
                    
                    /* Overflow handling */
                    .overflow-x-auto {
                        overflow-x: auto;
                    }
                    
                    /* Table header name */
                    .table-header-name {
                        width: 25%;
                    }
                    
                    /* Position table */
                    .position-table {
                        width: 100%;
                        font-size: 0.75rem;
                        margin-top: 0.25rem;
                    }
                    
                    /* ShareClass summary */
                    .shareclass-summary {
                        background: linear-gradient(135deg, #DBEAFE 0%, #BFDBFE 100%);
                    }
                    
                    /* Position summary */
                    .position-summary {
                        background: linear-gradient(135deg, #F0FDF4 0%, #D1FAE5 100%);
                    }
                    
                    /* Text primary color */
                    .text-primary {
                        color: #6366F1;
                    }
                    
                    /* Text alignment and weight */
                    .text-right {
                        text-align: right;
                    }
                    
                    .font-bold {
                        font-weight: 600;
                    }
                    
                    /* Table header narrow */
                    .table-header-narrow {
                        width: 30%;
                        background: #F3F4F6;
                    }
                    
                    /* Small text */
                    .text-xs {
                        font-size: 0.75rem;
                    }
                    
                    .text-sm {
                        font-size: 0.875rem;
                    }
                    
                    /* Footer text */
                    .footer-text {
                        text-align: center;
                        color: #6B7280;
                        margin-top: 2rem;
                    }
                    
                    /* Text utilities */
                    .text-center {
                        text-align: center;
                    }
                    
                    .text-gray {
                        color: #6B7280;
                    }
                    
                    /* Responsive Design */
                    @media (max-width: 768px) {
                        body {
                            padding: 1rem 0.5rem;
                        }
                        
                        main {
                            padding: 1rem;
                            border-radius: 1rem;
                        }
                        
                        h1 {
                            font-size: 1.75rem;
                        }
                        
                        table {
                            font-size: 0.875rem;
                        }
                        
                        table th, table td {
                            padding: 0.5rem;
                        }
                    }
                    
                    /* Code/XML Display */
                    .language-xml {
                        background: #1E293B;
                        color: #E2E8F0;
                        padding: 1rem;
                        border-radius: 0.5rem;
                        font-family: 'Courier New', monospace;
                        font-size: 0.875rem;
                        overflow-x: auto;
                        white-space: pre;
                    }
                    
                    /* Error highlighting */
                    .bg-red-100 {
                        background: #FEE2E2 !important;
                        color: #DC2626 !important;
                        padding: 0.25rem 0.5rem;
                        border-radius: 0.25rem;
                    }
                    
                    .bg-yellow-100 {
                        background: #FEF3C7 !important;
                        padding: 0.25rem 0.5rem;
                        border-radius: 0.25rem;
                    }
                    
                    /* Asset detail box */
                    .asset-detail-box {
                        margin-top: 0.5rem;
                        padding: 0.5rem;
                        background: #F9FAFB;
                        border-radius: 0.25rem;
                        font-size: 0.75rem;
                    }
                    
                    .asset-detail-row {
                        display: flex;
                        justify-content: space-between;
                    }
                    
                    .asset-detail-label {
                        font-weight: 600;
                        padding-right: 0.5rem;
                    }
                    
                    .asset-detail-value {
                        text-align: right;
                    }
                    
                    /* Position Links */
                    .position-link {
                        color: #2563EB;
                        text-decoration: none;
                        font-weight: 600;
                        padding: 0.125rem 0.5rem;
                        border-radius: 0.25rem;
                        transition: all 0.2s ease;
                        display: inline-flex;
                        align-items: center;
                        gap: 0.25rem;
                    }
                    
                    .position-link .link-icon {
                        opacity: 0.6;
                        transition: opacity 0.2s ease;
                    }
                    
                    .position-link:hover {
                        background: #EFF6FF;
                        color: #1E40AF;
                        text-decoration: underline;
                        transform: translateX(2px);
                    }
                    
                    .position-link:hover .link-icon {
                        opacity: 1;
                    }
                    
                    .position-link:active {
                        background: #DBEAFE;
                    }
                    
                    .position-no-link {
                        color: #6B7280;
                        display: inline-flex;
                        align-items: center;
                        gap: 0.25rem;
                    }
                    
                    /* Highlight target in AssetMasterData */
                    tr:target {
                        animation: highlightRow 2s ease;
                        background: #FEF3C7 !important;
                    }
                    
                    @keyframes highlightRow {
                        0% { background: #FDE047; }
                        50% { background: #FEF3C7; }
                        100% { background: #FEF3C7; }
                    }
                    
                    /* Utility Classes */
                    .margin-left {
                        margin-left: 0.25rem;
                    }
                    
                    /* Asset ID Display */
                    .asset-id-display {
                        color: #1F2937;
                        font-size: 0.875rem;
                    }
                    
                    /* Smooth scrolling */
                    html {
                        scroll-behavior: smooth;
                    }
                    
                    /* Scroll margin for better positioning */
                    tr[id] {
                        scroll-margin-top: 2rem;
                    }
                    
                    /* Fund Header - Always Visible */
                    .fund-header {
                        background: linear-gradient(135deg, #f5f7fa 0%, #e3e9f3 100%);
                        border-radius: 1rem;
                        padding: 1.5rem;
                        margin-bottom: 2rem;
                        box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
                    }
                    
                    .fund-header h2 {
                        margin-top: 0;
                        margin-bottom: 1.5rem;
                        color: #4C1D95;
                        font-size: 1.75rem;
                        padding-bottom: 0.75rem;
                        border-bottom: 2px solid rgba(102, 126, 234, 0.3);
                    }
                    
                    /* Section Headers in Tables */
                    .section-header th {
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%) !important;
                        color: white !important;
                        font-weight: 700 !important;
                        text-align: center !important;
                        font-size: 1.1rem;
                        padding: 0.875rem !important;
                        letter-spacing: 0.05em;
                        text-transform: uppercase;
                    }
                    
                    .section-header:hover {
                        background: none !important;
                    }
                    
                    /* Animations */
                    @keyframes fadeIn {
                        from {
                            opacity: 0;
                            transform: translateY(20px);
                        }
                        to {
                            opacity: 1;
                            transform: translateY(0);
                        }
                    }
                    
                    main > * {
                        animation: fadeIn 0.5s ease-out;
                    }
                </style>
			</head>
			<body>
				<main id="content">
					<h1>Analyzing File</h1>
					<table class="info-table">
						<tbody>
							<tr>
								<th>Report Created</th>
								<td>
									<xsl:value-of select="current-dateTime()"/>
								</td>
							</tr>
							<tr>
								<th>Filename:</th>
								<td>
									<xsl:value-of select="tokenize(base-uri(.), '/')[last()]" disable-output-escaping="yes"/>
								</td>
							</tr>
							<tr>
								<th># Funds:</th>
								<td>
									<xsl:value-of select="count(FundsXML4/Funds/Fund)"/>
								</td>
							</tr>
							<tr>
								<th># ShareClasses:</th>
								<td>
									<xsl:if test="count(//SingleFund/ShareClasses/ShareClass) = 0">
										<xsl:attribute name="class">bg-red-100</xsl:attribute>
									</xsl:if>
									<xsl:value-of select="count(//SingleFund/ShareClasses/ShareClass)"/>
								</td>
							</tr>
							<tr>
								<th># Asset Master Data:</th>
								<td>
									<xsl:value-of select="count(FundsXML4/AssetMasterData/Asset)"/>
								</td>
							</tr>
						</tbody>
					</table>
					<hr/>
					<div class="error-section">
						<h1>ERROR LIST</h1>
						<ul id="listOfErrors"/>
					</div>
					<hr/>
					<div class="warning-section">
						<h1>WARNING LIST</h1>
						<ul id="listOfWarnings"/>
					</div>
					<hr/>
					<xsl:apply-templates select="FundsXML4/ControlData"/>
					<hr/>
					<h1>FundList</h1>
					<ol>
						<xsl:for-each select="FundsXML4/Funds/Fund">
							<li>
								<a href="#{generate-id(.)}">
									<xsl:value-of select="Names/OfficialName/text()"/>
								</a>
								<ol>
									<xsl:for-each select="SingleFund/ShareClasses/ShareClass">
										<li>
											<a href="#{Identifiers/ISIN}">
												<xsl:value-of select="Identifiers/ISIN"/>
											</a>
										</li>
									</xsl:for-each>
								</ol>
							</li>
						</xsl:for-each>
					</ol>
					<hr/>
					<div>
						<a href="#AssetMasterData" class="asset-link">
							<svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" fill="currentColor" viewBox="0 0 16 16">
								<path d="M6.354 5.5H4a3 3 0 0 0 0 6h3a3 3 0 0 0 2.83-4H9c-.086 0-.17.01-.25.031A2 2 0 0 1 7 10.5H4a2 2 0 1 1 0-4h1.535c.218-.376.495-.714.82-1z"/>
								<path d="M9 5.5a3 3 0 0 0-2.83 4h1.098A2 2 0 0 1 9 6.5h3a2 2 0 1 1 0 4h-1.535a4.02 4.02 0 0 1-.82 1H12a3 3 0 1 0 0-6H9z"/>
							</svg>
                            Asset Master Data
                        </a>
					</div>
					<hr/>
					<xsl:apply-templates select="FundsXML4/Funds"/>
					<hr/>
					<xsl:apply-templates select="FundsXML4/AssetMasterData"/>
					<hr/>
					<p class="text-center text-gray footer-text">
                        Generated with FreeXMLToolkit • © Karl Kauc 2024
                    </p>
				</main>
			</body>
		</html>
	</xsl:template>
	<!-- Rest of templates remain unchanged but inherit the new styles -->
	<xsl:template match="ControlData">
		<details>
			<summary>ControlData</summary>
			<div>
				<table class="data-table">
					<tbody>
						<xsl:for-each select="*[name() != 'DataSupplier']">
							<tr>
								<th class="table-header-narrow">
									<xsl:value-of select="name()"/>
								</th>
								<td>
									<xsl:value-of select="text()"/>
								</td>
							</tr>
						</xsl:for-each>
						<!-- DataSupplier Section -->
						<xsl:if test="DataSupplier">
							<tr class="section-header">
								<th colspan="2">Data Supplier</th>
							</tr>
							<!-- Name with fallback to Short -->
							<tr>
								<th class="table-header-narrow">Name</th>
								<td>
									<xsl:choose>
										<xsl:when test="DataSupplier/n and DataSupplier/n != ''">
											<xsl:value-of select="DataSupplier/n"/>
										</xsl:when>
										<xsl:otherwise>
											<xsl:value-of select="DataSupplier/Short"/>
										</xsl:otherwise>
									</xsl:choose>
								</td>
							</tr>
							<!-- Other DataSupplier fields except 'n', 'Short' and 'Contact' -->
							<xsl:for-each select="DataSupplier/*[name() != 'n' and name() != 'Short' and name() != 'Contact']">
								<tr>
									<th class="table-header-narrow">
										<xsl:value-of select="name()"/>
									</th>
									<td>
										<xsl:value-of select="."/>
									</td>
								</tr>
							</xsl:for-each>
							<!-- Contact Section -->
							<xsl:if test="DataSupplier/Contact">
								<tr class="section-header">
									<th colspan="2">Contact</th>
								</tr>
								<xsl:for-each select="DataSupplier/Contact/*">
									<tr>
										<th class="table-header-narrow">
											<xsl:value-of select="name()"/>
										</th>
										<td>
											<xsl:value-of select="."/>
										</td>
									</tr>
								</xsl:for-each>
							</xsl:if>
						</xsl:if>
					</tbody>
				</table>
			</div>
		</details>
	</xsl:template>
	<xsl:template match="Funds">
		<xsl:apply-templates select="Fund"/>
	</xsl:template>
	<xsl:template match="Fund">
		<xsl:variable name="fundCurrency" select="Currency"/>
		<div id="{generate-id(.)}" class="fund-section">
			<!-- Always visible header section with basic data, Names and Identifiers -->
			<div class="fund-header">
				<h2>
					<xsl:value-of select="Names/OfficialName"/> (<xsl:value-of select="Currency"/>)
                    <xsl:if test="count(SingleFund/ShareClasses/ShareClass) = 0">
						<span class="badge badge-error">
                            No ShareClasses
                        </span>
					</xsl:if>
				</h2>
				<!-- Combined table with Basic Data, Names and Identifiers -->
				<table class="data-table">
					<tbody>
						<!-- Basic Data Section -->
						<tr class="section-header">
							<th colspan="2">Basic Data</th>
						</tr>
						<tr>
							<th>Currency</th>
							<td>
								<xsl:value-of select="Currency"/>
							</td>
						</tr>
						<tr>
							<th>Inception Date</th>
							<td>
								<xsl:value-of select="FundStaticData/InceptionDate"/>
							</td>
						</tr>
						<tr>
							<th>Domicile</th>
							<td>
								<xsl:value-of select="FundStaticData/Domicile"/>
							</td>
						</tr>
						<tr>
							<th>Fiscal Year End</th>
							<td>
								<xsl:value-of select="FundStaticData/FiscalYearEnd"/>
							</td>
						</tr>
						<!-- Names Section -->
						<tr class="section-header">
							<th colspan="2">Names</th>
						</tr>
						<xsl:for-each select="Names/*">
							<tr>
								<th>
									<xsl:value-of select="name()"/>
								</th>
								<td>
									<xsl:value-of select="text()"/>
								</td>
							</tr>
						</xsl:for-each>
						<!-- Identifiers Section -->
						<tr class="section-header">
							<th colspan="2">Identifiers</th>
						</tr>
						<xsl:for-each select="Identifiers/*[name() != 'OtherID']">
							<tr>
								<th class="table-header-narrow">
									<xsl:value-of select="name()"/>
								</th>
								<td>
									<xsl:value-of select="."/>
								</td>
							</tr>
						</xsl:for-each>
						<xsl:for-each select="Identifiers/*[name() = 'OtherID']">
							<tr>
								<th class="table-header-narrow">
									<xsl:value-of select="name()"/>
									<xsl:for-each select="@*">
										<br/>
										<span class="badge badge-info">@<xsl:value-of select="name()"/>:<xsl:value-of select="."/>
										</span>
									</xsl:for-each>
								</th>
								<td>
									<xsl:value-of select="."/>
								</td>
							</tr>
						</xsl:for-each>
					</tbody>
				</table>
			</div>
			<!-- Collapsible sections below -->
			<hr/>
			<xsl:apply-templates select="FundStaticData"/>
			<hr/>
			<xsl:apply-templates select="FundDynamicData">
				<xsl:with-param name="fundCurrency" select="$fundCurrency"/>
			</xsl:apply-templates>
			<hr/>
			<xsl:apply-templates select="SingleFund"/>
		</div>
	</xsl:template>
	<xsl:template match="Names">
		<details>
			<summary>Names</summary>
			<div>
				<table class="data-table">
					<tbody>
						<xsl:for-each select="*">
							<tr>
								<th>
									<xsl:value-of select="name()"/>
								</th>
								<td>
									<xsl:value-of select="text()"/>
								</td>
							</tr>
						</xsl:for-each>
					</tbody>
				</table>
			</div>
		</details>
	</xsl:template>
	<xsl:template match="Identifiers">
		<details>
			<summary>Identifiers</summary>
			<div>
				<table class="data-table">
					<tbody>
						<xsl:for-each select="*[name() != 'OtherID']">
							<tr>
								<th class="table-header-narrow">
									<xsl:value-of select="name()"/>
								</th>
								<td>
									<xsl:value-of select="."/>
								</td>
							</tr>
						</xsl:for-each>
						<xsl:for-each select="*[name() = 'OtherID']">
							<tr>
								<th class="table-header-narrow">
									<xsl:value-of select="name()"/>
									<xsl:for-each select="@*">
										<br/>
										<span class="badge badge-info">@<xsl:value-of select="name()"/>:<xsl:value-of select="."/>
										</span>
									</xsl:for-each>
								</th>
								<td>
									<xsl:value-of select="."/>
								</td>
							</tr>
						</xsl:for-each>
					</tbody>
				</table>
			</div>
		</details>
	</xsl:template>
	<xsl:template match="FundStaticData">
		<details>
			<summary>FundStaticData</summary>
			<div>
				<table class="data-table">
					<tbody>
						<xsl:for-each select="*">
							<tr>
								<th class="table-header-narrow">
									<xsl:value-of select="name()"/>
								</th>
								<td>
									<xsl:value-of select="."/>
								</td>
							</tr>
						</xsl:for-each>
					</tbody>
				</table>
			</div>
		</details>
	</xsl:template>
	<xsl:template match="FundDynamicData">
		<xsl:param name="fundCurrency"/>
		<details>
			<summary>FundDynamicData</summary>
			<div>
				<xsl:apply-templates select="TotalAssetValues">
					<xsl:with-param name="fundCurrency" select="$fundCurrency"/>
				</xsl:apply-templates>
				<xsl:apply-templates select="Portfolios">
					<xsl:with-param name="fundCurrency" select="$fundCurrency"/>
				</xsl:apply-templates>
			</div>
		</details>
	</xsl:template>
	<xsl:template match="TotalAssetValues">
		<xsl:param name="fundCurrency"/>
		<xsl:for-each select="TotalAssetValue">
			<details>
				<summary>TotalAssetValue <xsl:value-of select="NavDate"/>
				</summary>
				<div>
					<table class="data-table">
						<thead>
							<tr>
								<th>Field</th>
								<th>Value</th>
							</tr>
						</thead>
						<tbody>
							<!-- NavDate -->
							<tr>
								<td>NavDate</td>
								<td>
									<xsl:value-of select="NavDate"/>
								</td>
							</tr>
							<!-- TotalAssetNature -->
							<xsl:if test="TotalAssetNature">
								<tr>
									<td>TotalAssetNature</td>
									<td>
										<xsl:value-of select="TotalAssetNature"/>
									</td>
								</tr>
							</xsl:if>
							<!-- TotalNetAssetValue -->
							<xsl:if test="TotalNetAssetValue">
								<tr>
									<td>TotalNetAssetValue</td>
									<td class="text-right font-bold">
										<xsl:value-of select="format-number((TotalNetAssetValue/Amount[@ccy=$fundCurrency], TotalNetAssetValue/Amount)[1], '#,##0.00')"/>
										<xsl:text> </xsl:text>
										<xsl:value-of select="(TotalNetAssetValue/Amount[@ccy=$fundCurrency], TotalNetAssetValue/Amount)[1]/@ccy"/>
									</td>
								</tr>
							</xsl:if>
							<!-- Other fields dynamically -->
							<xsl:for-each select="*[name() != 'NavDate' and name() != 'TotalAssetNature' and name() != 'TotalNetAssetValue']">
								<tr>
									<td>
										<xsl:value-of select="name()"/>
									</td>
									<td>
										<xsl:choose>
											<xsl:when test="Amount">
												<xsl:value-of select="format-number((Amount[@ccy=$fundCurrency], Amount)[1], '#,##0.00')"/>
												<xsl:text> </xsl:text>
												<xsl:value-of select="(Amount[@ccy=$fundCurrency], Amount)[1]/@ccy"/>
											</xsl:when>
											<xsl:otherwise>
												<xsl:value-of select="."/>
											</xsl:otherwise>
										</xsl:choose>
									</td>
								</tr>
							</xsl:for-each>
						</tbody>
					</table>
				</div>
			</details>
		</xsl:for-each>
	</xsl:template>
	<xsl:template match="Portfolios">
		<xsl:param name="fundCurrency"/>
		<xsl:for-each select="Portfolio">
			<xsl:variable name="portfolioCcy" select="PortfolioCurrency"/>
			<details>
				<summary>Portfolio <xsl:value-of select="NavDate"/> - <xsl:value-of select="count(Positions/Position)"/> Positions</summary>
				<div>
					<div class="overflow-x-auto">
						<table class="data-table positions-table">
							<thead>
								<tr>
									<th>#</th>
									<th>UniqueID</th>
									<th>Asset Type</th>
									<th>Currency</th>
									<th>Total Value</th>
									<th>Percentage</th>
									<th>Asset Details</th>
								</tr>
							</thead>
							<tbody>
								<xsl:for-each select="Positions/Position">
									<xsl:sort select="TotalPercentage" data-type="number" order="descending"/>
									<xsl:variable name="positionCCY" select="Currency"/>
									<tr>
										<xsl:attribute name="class"><xsl:choose><xsl:when test="position() mod 2 = 0">position-row-even</xsl:when><xsl:otherwise>position-row-odd</xsl:otherwise></xsl:choose></xsl:attribute>
										<td class="text-center font-bold">
											<xsl:value-of select="position()"/>
										</td>
										<td>
											<xsl:choose>
												<xsl:when test="key('asset-by-id', UniqueID)">
													<a href="#{UniqueID}" class="position-link" title="Jump to Asset Master Data">
														<xsl:value-of select="UniqueID"/>
														<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" fill="currentColor" class="link-icon" viewBox="0 0 16 16">
															<path d="M4.715 6.542L3.343 7.914a3 3 0 1 0 4.243 4.243l1.828-1.829A3 3 0 0 0 8.586 5.5L8 6.086a1.002 1.002 0 0 0-.154.199 2 2 0 0 1 .861 3.337L6.88 11.45a2 2 0 1 1-2.83-2.83l.793-.792a4.018 4.018 0 0 1-.128-1.287z"/>
															<path d="M6.586 4.672A3 3 0 0 0 7.414 9.5l.775-.776a2 2 0 0 1-.896-3.346L9.12 3.55a2 2 0 1 1 2.83 2.83l-.793.792c.112.42.155.855.128 1.287l1.372-1.372a3 3 0 1 0-4.243-4.243L6.586 4.672z"/>
														</svg>
													</a>
												</xsl:when>
												<xsl:otherwise>
													<span class="position-no-link" title="Not found in Asset Master Data">
														<xsl:value-of select="UniqueID"/>
														<span class="badge badge-warning">⚠</span>
													</span>
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
											<xsl:if test="$positionCCY != $portfolioCcy">
												<span class="badge badge-warning margin-left">
                                                    FX
                                                </span>
											</xsl:if>
										</td>
										<td class="text-right font-bold">
											<xsl:value-of select="format-number((TotalValue/Amount[@ccy=$fundCurrency], TotalValue/Amount)[1], '#,##0.00')"/>
											<span class="text-sm margin-left">
												<xsl:value-of select="(TotalValue/Amount[@ccy=$fundCurrency], TotalValue/Amount)[1]/@ccy"/>
											</span>
										</td>
										<td class="text-center">
											<span class="percentage-badge">
												<xsl:value-of select="format-number(TotalPercentage, '0.00')"/>%
                                            </span>
										</td>
										<td>
											<xsl:apply-templates select="Equity | Bond | ShareClass | Warrant | Certificate | Option | Future | FXForward | Swap | Repo | FixedTimeDeposit | CallMoney | Account | Fee | RealEstate | REIT | Loan | Right | Commodity | PrivateEquity | CommercialPaper | Index | Crypto">
												<xsl:with-param name="portfolioCcy" select="$portfolioCcy"/>
												<xsl:with-param name="inline" select="true()"/>
											</xsl:apply-templates>
										</td>
									</tr>
								</xsl:for-each>
							</tbody>
							<tfoot>
								<tr class="positions-summary">
									<td colspan="4" class="text-right font-bold">Total:</td>
									<td class="text-right font-bold">
										<xsl:value-of select="format-number(sum(Positions/Position/TotalValue/Amount[@ccy=$fundCurrency]), '#,##0.00')"/>
									</td>
									<td class="text-center font-bold">
										<span class="percentage-badge-total">
											<xsl:value-of select="format-number(sum(Positions/Position/TotalPercentage), '0.00')"/>%
                                        </span>
									</td>
									<td/>
								</tr>
							</tfoot>
						</table>
					</div>
				</div>
			</details>
		</xsl:for-each>
	</xsl:template>
	<xsl:template match="SingleFund">
		<details>
			<summary>SingleFund / ShareClasses</summary>
			<div>
				<xsl:apply-templates select="ShareClasses"/>
			</div>
		</details>
	</xsl:template>
	<xsl:template match="ShareClasses">
		<xsl:for-each select="ShareClass">
			<details>
				<summary id="{Identifiers/ISIN}" class="shareclass-summary">
                    ShareClass: <xsl:value-of select="Identifiers/ISIN"/> - <xsl:value-of select="Names/OfficialName"/>
				</summary>
				<div>
					<table class="data-table shareclass-table">
						<tbody>
							<xsl:for-each select="Names/*">
								<tr>
									<th class="table-header-narrow">Name: <xsl:value-of select="name()"/>
									</th>
									<td>
										<xsl:value-of select="."/>
									</td>
								</tr>
							</xsl:for-each>
							<xsl:for-each select="Identifiers/*">
								<tr>
									<th>Identifier: <xsl:value-of select="name()"/>
									</th>
									<td class="font-bold">
										<xsl:value-of select="."/>
									</td>
								</tr>
							</xsl:for-each>
						</tbody>
					</table>
				</div>
			</details>
		</xsl:for-each>
	</xsl:template>
	<!-- Templates for position asset types -->
	<xsl:template match="Position/Equity | Position/Bond | Position/ShareClass | Position/Warrant | Position/Certificate | Position/Option | Position/Future">
		<xsl:param name="portfolioCcy"/>
		<xsl:param name="inline" select="false()"/>
		<xsl:variable name="positionCCY" select="../Currency"/>
		<xsl:choose>
			<xsl:when test="$inline">
				<span class="asset-type-inline">
					<xsl:value-of select="name(.)"/>
					<xsl:if test="ISIN">
						<br/>
						<span class="text-xs">ISIN: <xsl:value-of select="ISIN"/>
						</span>
					</xsl:if>
				</span>
			</xsl:when>
			<xsl:otherwise>
				<div class="position-card">
					<span class="position-type">
						<xsl:value-of select="name(.)"/>
					</span>
					<table class="position-table">
						<tbody>
							<!-- Common fields like Units, Price, etc. can be generalized here if needed -->
						</tbody>
					</table>
				</div>
			</xsl:otherwise>
		</xsl:choose>
	</xsl:template>
	<!-- Simplified templates for other asset types -->
	<xsl:template match="Position/FXForward | Position/Swap | Position/Repo | Position/FixedTimeDeposit | Position/CallMoney | Position/Account | Position/Fee | Position/RealEstate | Position/REIT | Position/Loan | Position/Right | Position/Commodity | Position/PrivateEquity | Position/CommercialPaper | Position/Index | Position/Crypto">
		<xsl:param name="inline" select="false()"/>
		<xsl:choose>
			<xsl:when test="$inline">
				<span class="asset-type-inline">
					<xsl:value-of select="name(.)"/>
				</span>
			</xsl:when>
			<xsl:otherwise>
				<div class="position-card">
					<span class="position-type">
						<xsl:value-of select="name(.)"/>
					</span>
				</div>
			</xsl:otherwise>
		</xsl:choose>
	</xsl:template>
	<!-- Template for AssetMasterData -->
	<xsl:template match="AssetMasterData">
		<div id="AssetMasterData">
			<h1>Asset Master Data</h1>
			<div class="overflow-x-auto">
				<table class="data-table">
					<thead>
						<tr>
							<th>#</th>
							<th>UniqueID</th>
							<th>Identifiers</th>
							<th class="table-header-name">Name</th>
							<th>Currency</th>
							<th>Country</th>
							<th>AssetDetails</th>
						</tr>
					</thead>
					<tbody>
						<xsl:for-each select="Asset">
							<tr id="{UniqueID}">
								<th>
									<xsl:value-of select="position()"/>
								</th>
								<xsl:variable name="assetAnker" select="UniqueID"/>
								<td>
									<strong class="asset-id-display">
										<xsl:value-of select="UniqueID"/>
									</strong>
									<xsl:if test="key('asset-by-id', UniqueID)">
										<span class="badge badge-success" title="Referenced in positions">✓</span>
									</xsl:if>
								</td>
								<td>
									<xsl:attribute name="class"><xsl:value-of select="if (count(Identifiers/*) = 0) then 'bg-yellow-100' else '' "/></xsl:attribute>
									<xsl:for-each select="Identifiers/*[name() != 'OtherID']">
										<xsl:value-of select="concat(name(), ': ', .)"/>
										<br/>
									</xsl:for-each>
									<xsl:for-each select="Identifiers/*[name() = 'OtherID']">
										<span class="text-xs">
											<xsl:choose>
												<xsl:when test="string-length(.) > 16">
													<xsl:value-of select="concat(name(), '[@', attribute(), ']:')"/>
													<br/>
													<xsl:value-of select="."/>
												</xsl:when>
												<xsl:otherwise>
													<xsl:value-of select="concat(name(), '[@', attribute(), ']', ': ', .)"/>
												</xsl:otherwise>
											</xsl:choose>
										</span>
										<br/>
									</xsl:for-each>
									<xsl:choose>
										<xsl:when test="AssetType = ('EQ', 'BO', 'SC', 'WA') and not(Identifiers/ISIN)">
											<span id="ERROR_{generate-id(.)}" data-error-message="Missing ISIN for Instrument Type {AssetType} Asset {../UniqueID}" class="badge badge-error">Missing ISIN for Instrument Type <xsl:value-of select="AssetType"/>
											</span>
										</xsl:when>
									</xsl:choose>
								</td>
								<td>
									<xsl:value-of select="Name"/>
								</td>
								<td>
									<xsl:value-of select="Currency"/>
								</td>
								<td>
									<xsl:value-of select="Country"/>
								</td>
								<td>
                                    Type: <xsl:value-of select="AssetType"/>
                                    (<span class="font-bold">
										<xsl:value-of select="name(AssetDetails/*[position()=1])"/>
									</span>)
                                    <br/>
									<xsl:apply-templates select="AssetDetails/*"/>
									<div class="text-xs">
										<xsl:if test="$renderXMLContent">
											<details>
												<summary>Original XML</summary>
												<script type="text/plain" class="language-xml">
													<xsl:copy-of select="node()" copy-namespaces="false"/>
												</script>
											</details>
										</xsl:if>
									</div>
								</td>
							</tr>
						</xsl:for-each>
					</tbody>
				</table>
			</div>
		</div>
	</xsl:template>
	<!-- Template for AssetMasterData Detail Types -->
	<xsl:template match="AssetDetails/Equity | AssetDetails/Bond | AssetDetails/ShareClass | AssetDetails/Warrant | AssetDetails/Certificate | AssetDetails/Option | AssetDetails/Future | AssetDetails/FXForward | AssetDetails/Swap | AssetDetails/Account | AssetDetails/Fee" priority="10">
		<div class="asset-detail-box">
			<xsl:for-each select="*">
				<div class="asset-detail-row">
					<span class="asset-detail-label">
						<xsl:value-of select="name()"/>:</span>
					<span class="asset-detail-value">
						<xsl:value-of select="."/>
					</span>
				</div>
			</xsl:for-each>
		</div>
	</xsl:template>
	<!-- Fallback for other asset details -->
	<xsl:template match="AssetDetails/*" priority="1">
		<div class="asset-detail-box">
			<span class="font-bold">
				<xsl:value-of select="name(.)"/>
			</span>
		</div>
	</xsl:template>
</xsl:stylesheet>
