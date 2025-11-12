<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="html" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <xsl:template match="/FundsXML4">
        <html lang="de">
            <head>
                <meta charset="UTF-8"/>
                <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                <title>FundsXML - Required Fields Check</title>
                <style>
                    /* Reset und Basis-Styles */
                    * {
                        margin: 0;
                        padding: 0;
                        box-sizing: border-box;
                    }
                    
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
                        background: #F9FAFB;
                        min-height: 100vh;
                        line-height: 1.5;
                    }
                    
                    /* Container */
                    .container {
                        max-width: 1280px;
                        margin: 0 auto;
                        padding: 2rem 1rem;
                    }
                    
                    /* Hauptkarte */
                    .main-card {
                        background: white;
                        border-radius: 0.5rem;
                        box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05);
                        padding: 1.5rem;
                    }
                    
                    /* Titel */
                    .main-title {
                        font-size: 1.875rem;
                        font-weight: bold;
                        color: #111827;
                        margin-bottom: 1.5rem;
                        border-bottom: 2px solid #2563EB;
                        padding-bottom: 0.75rem;
                    }
                    
                    /* Info Box */
                    .info-box {
                        margin-bottom: 1.5rem;
                        background: #EFF6FF;
                        border: 1px solid #BFDBFE;
                        border-radius: 0.5rem;
                        padding: 1rem;
                    }
                    
                    .info-text {
                        color: #1E40AF;
                        margin-bottom: 0.25rem;
                    }
                    
                    .info-text strong {
                        font-weight: 600;
                    }
                    
                    /* Sections Container */
                    .sections-container {
                        display: flex;
                        flex-direction: column;
                        gap: 1.5rem;
                    }
                    
                    /* Validation Section */
                    .validation-section {
                        background: white;
                        border: 1px solid #E5E7EB;
                        border-radius: 0.5rem;
                        padding: 1.5rem;
                    }
                    
                    .section-header {
                        font-size: 1.25rem;
                        font-weight: 600;
                        color: #1F2937;
                        margin-bottom: 1rem;
                        display: flex;
                        align-items: center;
                    }
                    
                    .section-number {
                        width: 1.5rem;
                        height: 1.5rem;
                        border-radius: 9999px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        font-size: 0.875rem;
                        font-weight: bold;
                        margin-right: 0.5rem;
                    }
                    
                    .section-number.blue {
                        background: #DBEAFE;
                        color: #2563EB;
                    }
                    
                    .section-number.green {
                        background: #D1FAE5;
                        color: #059669;
                    }
                    
                    .section-number.yellow {
                        background: #FEF3C7;
                        color: #D97706;
                    }
                    
                    /* Field List */
                    .field-list {
                        display: flex;
                        flex-direction: column;
                        gap: 0.5rem;
                    }
                    
                    /* Field Item */
                    .field-item {
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        padding: 0.75rem;
                        background: #F9FAFB;
                        border-radius: 0.25rem;
                        border: 1px solid #E5E7EB;
                    }
                    
                    .field-name {
                        font-size: 0.875rem;
                        font-weight: 500;
                        color: #374151;
                    }
                    
                    .field-status {
                        display: flex;
                        align-items: center;
                        gap: 0.5rem;
                    }
                    
                    /* Status Badge */
                    .status-badge {
                        padding: 0.25rem 0.5rem;
                        font-size: 0.75rem;
                        border-radius: 0.25rem;
                        font-weight: 500;
                    }
                    
                    .status-present {
                        background: #D1FAE5;
                        color: #065F46;
                    }
                    
                    .status-missing {
                        background: #FEE2E2;
                        color: #991B1B;
                    }
                    
                    /* Field Value */
                    .field-value {
                        font-size: 0.75rem;
                        color: #6B7280;
                        max-width: 20rem;
                        overflow: hidden;
                        text-overflow: ellipsis;
                        white-space: nowrap;
                    }
                    
                    /* Portfolio Section */
                    .portfolio-info {
                        color: #4B5563;
                        margin-bottom: 1rem;
                    }
                    
                    .portfolio-count {
                        font-weight: 600;
                        color: #1F2937;
                    }
                    
                    /* Position Grid */
                    .position-grid {
                        display: flex;
                        flex-direction: column;
                        gap: 0.5rem;
                    }
                    
                    /* Position Item */
                    .position-item {
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        padding: 0.75rem;
                        background: #F9FAFB;
                        border-radius: 0.25rem;
                        border: 1px solid #E5E7EB;
                    }
                    
                    .position-id {
                        font-size: 0.875rem;
                        color: #374151;
                    }
                    
                    .position-status {
                        display: flex;
                        gap: 0.5rem;
                    }
                    
                    /* Summary Statistics */
                    .summary-section {
                        background: white;
                        border: 1px solid #E5E7EB;
                        border-radius: 0.5rem;
                        padding: 1.5rem;
                        margin-top: 1.5rem;
                    }
                    
                    .summary-title {
                        font-size: 1.25rem;
                        font-weight: 600;
                        color: #1F2937;
                        margin-bottom: 1rem;
                        text-align: center;
                    }
                    
                    .stats-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                        gap: 1.5rem;
                        margin-top: 1.5rem;
                    }
                    
                    .stat-card {
                        text-align: center;
                        padding: 1rem;
                        background: #F9FAFB;
                        border-radius: 0.5rem;
                        border: 1px solid #E5E7EB;
                    }
                    
                    .stat-value {
                        font-size: 2rem;
                        font-weight: bold;
                        margin-bottom: 0.5rem;
                    }
                    
                    .stat-value.pass { color: #059669; }
                    .stat-value.fail { color: #DC2626; }
                    .stat-value.warning { color: #F59E0B; }
                    
                    .stat-label {
                        font-size: 0.875rem;
                        color: #6B7280;
                    }
                    
                    /* Progress Bar */
                    .progress-container {
                        margin-top: 1.5rem;
                    }
                    
                    .progress-header {
                        display: flex;
                        justify-content: space-between;
                        margin-bottom: 0.5rem;
                    }
                    
                    .progress-label {
                        font-size: 0.875rem;
                        color: #4B5563;
                    }
                    
                    .progress-percentage {
                        font-size: 0.875rem;
                        font-weight: 600;
                        color: #111827;
                    }
                    
                    .progress-bar {
                        width: 100%;
                        height: 0.75rem;
                        background: #E5E7EB;
                        border-radius: 9999px;
                        overflow: hidden;
                    }
                    
                    .progress-fill {
                        height: 100%;
                        transition: width 0.5s ease;
                    }
                    
                    .progress-fill.good {
                        background: linear-gradient(to right, #10B981, #059669);
                    }
                    
                    .progress-fill.warning {
                        background: linear-gradient(to right, #F59E0B, #D97706);
                    }
                    
                    .progress-fill.error {
                        background: linear-gradient(to right, #EF4444, #DC2626);
                    }
                    
                    /* Footer */
                    .footer {
                        margin-top: 2rem;
                        padding: 1rem;
                        background: #F3F4F6;
                        border-radius: 0.5rem;
                    }
                    
                    .footer p {
                        font-size: 0.875rem;
                        color: #6B7280;
                        text-align: center;
                    }
                    
                    /* Validation Summary Box */
                    .validation-summary {
                        background: linear-gradient(to right, #EFF6FF, #F0F9FF);
                        border: 1px solid #BFDBFE;
                        border-radius: 0.5rem;
                        padding: 1.5rem;
                        margin-bottom: 1.5rem;
                    }
                    
                    .summary-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
                        gap: 1rem;
                        margin-top: 1rem;
                    }
                    
                    .summary-item {
                        text-align: center;
                    }
                    
                    .summary-value {
                        font-size: 1.5rem;
                        font-weight: bold;
                        display: block;
                    }
                    
                    .summary-value.total { color: #3B82F6; }
                    .summary-value.present { color: #10B981; }
                    .summary-value.missing { color: #EF4444; }
                    
                    .summary-label {
                        font-size: 0.75rem;
                        color: #6B7280;
                        text-transform: uppercase;
                    }
                    
                    /* Responsive Design */
                    @media (max-width: 768px) {
                        .stats-grid {
                            grid-template-columns: 1fr;
                        }
                        
                        .summary-grid {
                            grid-template-columns: 1fr;
                        }
                        
                        .field-value {
                            max-width: 10rem;
                        }
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="main-card">
                        <h1 class="main-title">
                            FundsXML Required Fields Validation
                        </h1>

                        <div class="info-box">
                            <p class="info-text">
                                <strong>Document ID:</strong>
                                <xsl:value-of select="ControlData/UniqueDocumentID"/>
                            </p>
                            <p class="info-text">
                                <strong>Generated:</strong>
                                <xsl:value-of select="ControlData/DocumentGenerated"/>
                            </p>
                        </div>

                        <!-- Validation Summary -->
                        <xsl:variable name="totalRequiredFields" select="20"/>
                        <xsl:variable name="controlDataPresent">
                            <xsl:value-of select="count(ControlData/UniqueDocumentID[string-length(.) &gt; 0]) + 
                                                  count(ControlData/DocumentGenerated[string-length(.) &gt; 0]) + 
                                                  count(ControlData/ContentDate[string-length(.) &gt; 0]) + 
                                                  count(ControlData/DataSupplier/Name[string-length(.) &gt; 0])"/>
                        </xsl:variable>
                        
                        <div class="validation-summary">
                            <h2 class="summary-title">Validation Overview</h2>
                            <div class="summary-grid">
                                <div class="summary-item">
                                    <span class="summary-value total">
                                        <xsl:value-of select="$totalRequiredFields"/>
                                    </span>
                                    <span class="summary-label">Total Fields</span>
                                </div>
                                <div class="summary-item">
                                    <span class="summary-value present">
                                        <xsl:value-of select="count(//Fund)"/>
                                    </span>
                                    <span class="summary-label">Funds</span>
                                </div>
                                <div class="summary-item">
                                    <span class="summary-value total">
                                        <xsl:value-of select="count(//Position)"/>
                                    </span>
                                    <span class="summary-label">Positions</span>
                                </div>
                            </div>
                        </div>

                        <div class="sections-container">
                            <!-- Control Data Validation -->
                            <div class="validation-section">
                                <h2 class="section-header">
                                    <span class="section-number blue">1</span>
                                    Control Data Validation
                                </h2>
                                <div class="field-list">
                                    <xsl:call-template name="check-field">
                                        <xsl:with-param name="field-name">UniqueDocumentID</xsl:with-param>
                                        <xsl:with-param name="field-value" select="ControlData/UniqueDocumentID"/>
                                    </xsl:call-template>
                                    <xsl:call-template name="check-field">
                                        <xsl:with-param name="field-name">DocumentGenerated</xsl:with-param>
                                        <xsl:with-param name="field-value" select="ControlData/DocumentGenerated"/>
                                    </xsl:call-template>
                                    <xsl:call-template name="check-field">
                                        <xsl:with-param name="field-name">ContentDate</xsl:with-param>
                                        <xsl:with-param name="field-value" select="ControlData/ContentDate"/>
                                    </xsl:call-template>
                                    <xsl:call-template name="check-field">
                                        <xsl:with-param name="field-name">DataSupplier Name</xsl:with-param>
                                        <xsl:with-param name="field-value" select="ControlData/DataSupplier/Name"/>
                                    </xsl:call-template>
                                    <xsl:call-template name="check-field">
                                        <xsl:with-param name="field-name">DataOperation</xsl:with-param>
                                        <xsl:with-param name="field-value" select="ControlData/DataOperation"/>
                                    </xsl:call-template>
                                    <xsl:call-template name="check-field">
                                        <xsl:with-param name="field-name">Language</xsl:with-param>
                                        <xsl:with-param name="field-value" select="ControlData/Language"/>
                                    </xsl:call-template>
                                </div>
                            </div>

                            <!-- Fund Basic Data Validation -->
                            <xsl:for-each select="Funds/Fund">
                                <div class="validation-section">
                                    <h2 class="section-header">
                                        <span class="section-number green">2</span>
                                        Fund Basic Data: <xsl:value-of select="Names/OfficialName"/>
                                    </h2>
                                    <div class="field-list">
                                        <xsl:call-template name="check-field">
                                            <xsl:with-param name="field-name">LEI</xsl:with-param>
                                            <xsl:with-param name="field-value" select="Identifiers/LEI"/>
                                        </xsl:call-template>
                                        <xsl:call-template name="check-field">
                                            <xsl:with-param name="field-name">ISIN</xsl:with-param>
                                            <xsl:with-param name="field-value" select="Identifiers/ISIN"/>
                                        </xsl:call-template>
                                        <xsl:call-template name="check-field">
                                            <xsl:with-param name="field-name">Official Name</xsl:with-param>
                                            <xsl:with-param name="field-value" select="Names/OfficialName"/>
                                        </xsl:call-template>
                                        <xsl:call-template name="check-field">
                                            <xsl:with-param name="field-name">Currency</xsl:with-param>
                                            <xsl:with-param name="field-value" select="Currency"/>
                                        </xsl:call-template>
                                        <xsl:call-template name="check-field">
                                            <xsl:with-param name="field-name">Inception Date</xsl:with-param>
                                            <xsl:with-param name="field-value" select="FundStaticData/InceptionDate"/>
                                        </xsl:call-template>
                                        <xsl:call-template name="check-field">
                                            <xsl:with-param name="field-name">Domicile</xsl:with-param>
                                            <xsl:with-param name="field-value" select="FundStaticData/Domicile"/>
                                        </xsl:call-template>
                                        <xsl:call-template name="check-field">
                                            <xsl:with-param name="field-name">Legal Form</xsl:with-param>
                                            <xsl:with-param name="field-value" select="FundStaticData/LegalForm"/>
                                        </xsl:call-template>
                                        <xsl:call-template name="check-field">
                                            <xsl:with-param name="field-name">Total NAV</xsl:with-param>
                                            <xsl:with-param name="field-value" select="FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount"/>
                                        </xsl:call-template>
                                        <xsl:call-template name="check-field">
                                            <xsl:with-param name="field-name">NAV Date</xsl:with-param>
                                            <xsl:with-param name="field-value" select="FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate"/>
                                        </xsl:call-template>
                                    </div>
                                </div>

                                <!-- Portfolio Positions Check -->
                                <div class="validation-section">
                                    <h2 class="section-header">
                                        <span class="section-number yellow">3</span>
                                        Portfolio Positions
                                    </h2>
                                    
                                    <p class="portfolio-info">
                                        Total Positions:
                                        <span class="portfolio-count">
                                            <xsl:value-of select="count(FundDynamicData/Portfolios/Portfolio/Positions/Position)"/>
                                        </span>
                                    </p>

                                    <div class="position-grid">
                                        <xsl:for-each select="FundDynamicData/Portfolios/Portfolio/Positions/Position[position() &lt;= 10]">
                                            <div class="position-item">
                                                <span class="position-id">
                                                    Position ID: <xsl:value-of select="UniqueID"/>
                                                </span>
                                                <div class="position-status">
                                                    <xsl:choose>
                                                        <xsl:when test="UniqueID and Currency and TotalValue/Amount">
                                                            <span class="status-badge status-present">✓ Valid</span>
                                                        </xsl:when>
                                                        <xsl:otherwise>
                                                            <span class="status-badge status-missing">✗ Missing Data</span>
                                                        </xsl:otherwise>
                                                    </xsl:choose>
                                                </div>
                                            </div>
                                        </xsl:for-each>
                                        
                                        <xsl:if test="count(FundDynamicData/Portfolios/Portfolio/Positions/Position) &gt; 10">
                                            <p style="font-size: 0.875rem; color: #6B7280; font-style: italic; margin-top: 0.5rem;">
                                                ... and <xsl:value-of select="count(FundDynamicData/Portfolios/Portfolio/Positions/Position) - 10"/> more positions
                                            </p>
                                        </xsl:if>
                                    </div>
                                </div>
                            </xsl:for-each>

                            <!-- Asset Master Validation -->
                            <xsl:if test="AssetMaster">
                                <div class="validation-section">
                                    <h2 class="section-header">
                                        <span class="section-number blue">4</span>
                                        Asset Master Validation
                                    </h2>
                                    <p class="portfolio-info">
                                        Total Assets:
                                        <span class="portfolio-count">
                                            <xsl:value-of select="count(AssetMaster/Asset)"/>
                                        </span>
                                    </p>
                                    
                                    <div class="field-list">
                                        <xsl:for-each select="AssetMaster/Asset[position() &lt;= 5]">
                                            <xsl:call-template name="check-field">
                                                <xsl:with-param name="field-name">Asset <xsl:value-of select="position()"/> - ID</xsl:with-param>
                                                <xsl:with-param name="field-value" select="Identifiers/UniqueID"/>
                                            </xsl:call-template>
                                        </xsl:for-each>
                                    </div>
                                </div>
                            </xsl:if>
                        </div>

                        <div class="footer">
                            <p>Generated with embedded CSS • FreeXmlToolkit</p>
                        </div>
                    </div>
                </div>
            </body>
        </html>
    </xsl:template>

    <!-- Template for field validation -->
    <xsl:template name="check-field">
        <xsl:param name="field-name"/>
        <xsl:param name="field-value"/>
        <div class="field-item">
            <span class="field-name">
                <xsl:value-of select="$field-name"/>
            </span>
            <div class="field-status">
                <xsl:choose>
                    <xsl:when test="$field-value and string-length($field-value) &gt; 0">
                        <span class="status-badge status-present">✓ Present</span>
                        <span class="field-value">
                            <xsl:value-of select="$field-value"/>
                        </span>
                    </xsl:when>
                    <xsl:otherwise>
                        <span class="status-badge status-missing">✗ Missing</span>
                    </xsl:otherwise>
                </xsl:choose>
            </div>
        </div>
    </xsl:template>

</xsl:stylesheet>