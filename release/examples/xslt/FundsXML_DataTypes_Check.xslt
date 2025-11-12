<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="html" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <xsl:template match="/FundsXML4">
        <html lang="de">
            <head>
                <meta charset="UTF-8"/>
                <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                <title>FundsXML - Data Types Validation</title>
                <style>
                    /* Reset und Basis-Styles */
                    * {
                        margin: 0;
                        padding: 0;
                        box-sizing: border-box;
                    }
                    
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
                        background: #F8FAFC;
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
                        border-radius: 0.75rem;
                        box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
                        padding: 2rem;
                    }
                    
                    /* Header */
                    .header {
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        margin-bottom: 2rem;
                        flex-wrap: wrap;
                        gap: 1rem;
                    }
                    
                    .main-title {
                        font-size: 2.25rem;
                        font-weight: bold;
                        color: #0F172A;
                    }
                    
                    .version-badge {
                        background: linear-gradient(to right, #3B82F6, #8B5CF6);
                        color: white;
                        padding: 0.5rem 1rem;
                        border-radius: 0.5rem;
                        font-size: 0.875rem;
                        font-weight: 600;
                    }
                    
                    /* Info Grid */
                    .info-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
                        gap: 1.5rem;
                        margin-bottom: 2rem;
                    }
                    
                    .info-card {
                        border-radius: 0.5rem;
                        padding: 1.5rem;
                        border: 1px solid;
                    }
                    
                    .info-card.blue {
                        background: linear-gradient(to bottom right, #EFF6FF, #DBEAFE);
                        border-color: #BFDBFE;
                    }
                    
                    .info-card.green {
                        background: linear-gradient(to bottom right, #F0FDF4, #DCFCE7);
                        border-color: #BBF7D0;
                    }
                    
                    .info-title {
                        font-weight: 600;
                        margin-bottom: 0.5rem;
                    }
                    
                    .info-card.blue .info-title { color: #1E3A8A; }
                    .info-card.green .info-title { color: #14532D; }
                    
                    .info-text {
                        font-size: 0.875rem;
                        margin-bottom: 0.25rem;
                    }
                    
                    .info-card.blue .info-text { color: #1E40AF; }
                    .info-card.green .info-text { color: #15803D; }
                    
                    /* Validation Sections */
                    .validation-section {
                        background: white;
                        border: 1px solid #E5E7EB;
                        border-radius: 0.75rem;
                        padding: 1.5rem;
                        box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.1);
                        margin-bottom: 2rem;
                    }
                    
                    .section-header {
                        font-size: 1.5rem;
                        font-weight: 600;
                        color: #111827;
                        margin-bottom: 1.5rem;
                        display: flex;
                        align-items: center;
                    }
                    
                    .section-number {
                        width: 2rem;
                        height: 2rem;
                        border-radius: 0.5rem;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        font-size: 0.875rem;
                        font-weight: bold;
                        color: white;
                        margin-right: 0.75rem;
                    }
                    
                    .section-number.blue { background: #3B82F6; }
                    .section-number.green { background: #10B981; }
                    .section-number.purple { background: #8B5CF6; }
                    
                    /* Validation Items */
                    .validation-grid {
                        display: grid;
                        gap: 1rem;
                    }
                    
                    .validation-item {
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        padding: 1rem;
                        background: #F9FAFB;
                        border-radius: 0.5rem;
                        border: 1px solid #E5E7EB;
                    }
                    
                    .validation-label {
                        flex: 1;
                    }
                    
                    .field-name {
                        font-weight: 500;
                        color: #1F2937;
                    }
                    
                    .expected-format {
                        font-size: 0.75rem;
                        color: #6B7280;
                        margin-top: 0.125rem;
                    }
                    
                    .validation-result {
                        display: flex;
                        align-items: center;
                        gap: 0.75rem;
                    }
                    
                    /* Status Badges */
                    .status-badge {
                        padding: 0.25rem 0.75rem;
                        font-size: 0.875rem;
                        border-radius: 9999px;
                        font-weight: 500;
                    }
                    
                    .status-valid {
                        background: #DCFCE7;
                        color: #16A34A;
                    }
                    
                    .status-invalid {
                        background: #FEF3C7;
                        color: #D97706;
                    }
                    
                    .status-missing {
                        background: #FEE2E2;
                        color: #DC2626;
                    }
                    
                    /* Code Display */
                    .code-value {
                        font-size: 0.75rem;
                        font-family: 'Courier New', monospace;
                        padding: 0.25rem 0.5rem;
                        border-radius: 0.25rem;
                        background: #E5E7EB;
                        color: #4B5563;
                    }
                    
                    /* Fund Box */
                    .fund-box {
                        background: #F9FAFB;
                        border-radius: 0.5rem;
                        padding: 1rem;
                        border: 1px solid #E5E7EB;
                        margin-bottom: 1rem;
                    }
                    
                    .fund-name {
                        font-weight: 600;
                        color: #1F2937;
                        margin-bottom: 0.75rem;
                    }
                    
                    .fund-validations {
                        display: grid;
                        gap: 0.75rem;
                    }
                    
                    .fund-validation-item {
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        padding: 0.75rem;
                        background: white;
                        border-radius: 0.25rem;
                        border: 1px solid #E5E7EB;
                    }
                    
                    .fund-field-name {
                        font-size: 0.875rem;
                        font-weight: 500;
                        color: #374151;
                    }
                    
                    .fund-validation-result {
                        display: flex;
                        align-items: center;
                        gap: 0.5rem;
                    }
                    
                    .fund-status-badge {
                        padding: 0.25rem 0.5rem;
                        font-size: 0.75rem;
                        border-radius: 0.25rem;
                        font-weight: 500;
                    }
                    
                    .fund-status-valid {
                        background: #DCFCE7;
                        color: #16A34A;
                    }
                    
                    .fund-status-invalid {
                        background: #FEF3C7;
                        color: #D97706;
                    }
                    
                    .fund-status-missing {
                        background: #FEE2E2;
                        color: #DC2626;
                    }
                    
                    .fund-code-value {
                        font-size: 0.75rem;
                        font-family: 'Courier New', monospace;
                        padding: 0.25rem 0.5rem;
                        border-radius: 0.25rem;
                        background: #F3F4F6;
                        color: #4B5563;
                    }
                    
                    /* Summary Stats */
                    .summary-section {
                        background: white;
                        border: 1px solid #E5E7EB;
                        border-radius: 0.75rem;
                        padding: 1.5rem;
                        margin-bottom: 2rem;
                    }
                    
                    .summary-title {
                        font-size: 1.5rem;
                        font-weight: 600;
                        color: #111827;
                        margin-bottom: 1.5rem;
                        display: flex;
                        align-items: center;
                    }
                    
                    .stats-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
                        gap: 1.5rem;
                    }
                    
                    .stat-item {
                        text-align: center;
                    }
                    
                    .stat-value {
                        font-size: 1.5rem;
                        font-weight: bold;
                        margin-bottom: 0.25rem;
                    }
                    
                    .stat-value.blue { color: #3B82F6; }
                    .stat-value.green { color: #10B981; }
                    .stat-value.purple { color: #8B5CF6; }
                    .stat-value.orange { color: #F97316; }
                    
                    .stat-label {
                        font-size: 0.875rem;
                        color: #64748B;
                    }
                    
                    /* Footer */
                    .footer {
                        margin-top: 2rem;
                        padding: 1rem;
                        background: #F1F5F9;
                        border-radius: 0.5rem;
                    }
                    
                    .footer p {
                        font-size: 0.875rem;
                        color: #64748B;
                        text-align: center;
                    }
                    
                    /* Responsive Design */
                    @media (max-width: 768px) {
                        .info-grid {
                            grid-template-columns: 1fr;
                        }
                        
                        .stats-grid {
                            grid-template-columns: repeat(2, 1fr);
                        }
                        
                        .header {
                            flex-direction: column;
                            align-items: stretch;
                            text-align: center;
                        }
                        
                        .validation-item {
                            flex-direction: column;
                            align-items: stretch;
                            gap: 0.5rem;
                        }
                        
                        .validation-result {
                            justify-content: flex-end;
                        }
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="main-card">
                        <div class="header">
                            <h1 class="main-title">Data Types Validation</h1>
                            <div class="version-badge">FundsXML 4.2.2</div>
                        </div>

                        <div class="info-grid">
                            <div class="info-card blue">
                                <h3 class="info-title">Document Info</h3>
                                <p class="info-text">
                                    ID: <xsl:value-of select="ControlData/UniqueDocumentID"/>
                                </p>
                                <p class="info-text">
                                    Date: <xsl:value-of select="ControlData/ContentDate"/>
                                </p>
                            </div>
                            <div class="info-card green">
                                <h3 class="info-title">Validation Status</h3>
                                <p class="info-text">
                                    Funds: <xsl:value-of select="count(Funds/Fund)"/>
                                </p>
                                <p class="info-text">
                                    Generated: <xsl:value-of select="substring(ControlData/DocumentGenerated, 1, 10)"/>
                                </p>
                            </div>
                        </div>

                        <!-- Date Format Validation -->
                        <div class="validation-section">
                            <h2 class="section-header">
                                <div class="section-number blue">1</div>
                                Date Format Validation
                            </h2>
                            <div class="validation-grid">
                                <xsl:call-template name="validate-date">
                                    <xsl:with-param name="field-name">Document Generated</xsl:with-param>
                                    <xsl:with-param name="date-value" select="ControlData/DocumentGenerated"/>
                                    <xsl:with-param name="expected-format">YYYY-MM-DDTHH:MM:SS</xsl:with-param>
                                </xsl:call-template>
                                <xsl:call-template name="validate-date">
                                    <xsl:with-param name="field-name">Content Date</xsl:with-param>
                                    <xsl:with-param name="date-value" select="ControlData/ContentDate"/>
                                    <xsl:with-param name="expected-format">YYYY-MM-DD</xsl:with-param>
                                </xsl:call-template>

                                <xsl:for-each select="Funds/Fund">
                                    <xsl:call-template name="validate-date">
                                        <xsl:with-param name="field-name">Fund Inception Date</xsl:with-param>
                                        <xsl:with-param name="date-value" select="FundStaticData/InceptionDate"/>
                                        <xsl:with-param name="expected-format">YYYY-MM-DD</xsl:with-param>
                                    </xsl:call-template>
                                    <xsl:call-template name="validate-date">
                                        <xsl:with-param name="field-name">NAV Date</xsl:with-param>
                                        <xsl:with-param name="date-value"
                                                        select="FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate"/>
                                        <xsl:with-param name="expected-format">YYYY-MM-DD</xsl:with-param>
                                    </xsl:call-template>
                                </xsl:for-each>
                            </div>
                        </div>

                        <!-- Numeric Validation -->
                        <div class="validation-section">
                            <h2 class="section-header">
                                <div class="section-number green">2</div>
                                Numeric Values Validation
                            </h2>
                            <div>
                                <xsl:for-each select="Funds/Fund">
                                    <div class="fund-box">
                                        <h3 class="fund-name">
                                            Fund: <xsl:value-of select="Names/OfficialName"/>
                                        </h3>
                                        <div class="fund-validations">
                                            <xsl:call-template name="validate-numeric">
                                                <xsl:with-param name="field-name">Total Net Asset Value</xsl:with-param>
                                                <xsl:with-param name="numeric-value"
                                                                select="FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount"/>
                                            </xsl:call-template>

                                            <xsl:for-each
                                                    select="FundDynamicData/Portfolios/Portfolio/Positions/Position[position() &lt;= 5]">
                                                <xsl:call-template name="validate-numeric">
                                                    <xsl:with-param name="field-name">Position Value (ID:
                                                        <xsl:value-of select="UniqueID"/>)
                                                    </xsl:with-param>
                                                    <xsl:with-param name="numeric-value"
                                                                    select="TotalValue/Amount"/>
                                                </xsl:call-template>
                                                <xsl:call-template name="validate-numeric">
                                                    <xsl:with-param name="field-name">Position Percentage (ID:
                                                        <xsl:value-of select="UniqueID"/>)
                                                    </xsl:with-param>
                                                    <xsl:with-param name="numeric-value" select="TotalPercentage"/>
                                                </xsl:call-template>
                                            </xsl:for-each>
                                        </div>
                                    </div>
                                </xsl:for-each>
                            </div>
                        </div>

                        <!-- Currency Code Validation -->
                        <div class="validation-section">
                            <h2 class="section-header">
                                <div class="section-number purple">3</div>
                                Currency Code Validation
                            </h2>
                            <div class="validation-grid">
                                <xsl:for-each select="Funds/Fund">
                                    <xsl:call-template name="validate-currency">
                                        <xsl:with-param name="field-name">Fund Currency</xsl:with-param>
                                        <xsl:with-param name="currency-code" select="Currency"/>
                                    </xsl:call-template>

                                    <xsl:for-each
                                            select="FundDynamicData/Portfolios/Portfolio/Positions/Position[position() &lt;= 3]">
                                        <xsl:call-template name="validate-currency">
                                            <xsl:with-param name="field-name">Position Currency (ID:
                                                <xsl:value-of select="UniqueID"/>)
                                            </xsl:with-param>
                                            <xsl:with-param name="currency-code" select="Currency"/>
                                        </xsl:call-template>
                                    </xsl:for-each>
                                </xsl:for-each>
                            </div>
                        </div>

                        <!-- Summary Statistics -->
                        <div class="summary-section">
                            <h2 class="summary-title">
                                <div class="section-number blue">📊</div>
                                Summary Statistics
                            </h2>
                            <div class="stats-grid">
                                <div class="stat-item">
                                    <div class="stat-value blue">
                                        <xsl:value-of select="count(//Position)"/>
                                    </div>
                                    <div class="stat-label">Positions</div>
                                </div>
                                <div class="stat-item">
                                    <div class="stat-value green">
                                        <xsl:value-of select="count(//Amount)"/>
                                    </div>
                                    <div class="stat-label">Amount Fields</div>
                                </div>
                                <div class="stat-item">
                                    <div class="stat-value purple">
                                        <xsl:value-of select="count(//*[contains(name(), 'Date')])"/>
                                    </div>
                                    <div class="stat-label">Date Fields</div>
                                </div>
                                <div class="stat-item">
                                    <div class="stat-value orange">
                                        <xsl:value-of select="count(//@ccy)"/>
                                    </div>
                                    <div class="stat-label">Currency Attributes</div>
                                </div>
                            </div>
                        </div>

                        <div class="footer">
                            <p>Generated with embedded CSS • Data Types Validation Report</p>
                        </div>
                    </div>
                </div>
            </body>
        </html>
    </xsl:template>

    <!-- Date validation template -->
    <xsl:template name="validate-date">
        <xsl:param name="field-name"/>
        <xsl:param name="date-value"/>
        <xsl:param name="expected-format"/>

        <div class="validation-item">
            <div class="validation-label">
                <div class="field-name">
                    <xsl:value-of select="$field-name"/>
                </div>
                <div class="expected-format">Expected: <xsl:value-of select="$expected-format"/></div>
            </div>
            <div class="validation-result">
                <xsl:choose>
                    <xsl:when test="$date-value and string-length($date-value) &gt; 0">
                        <span class="status-badge status-valid">✓ Valid</span>
                        <code class="code-value">
                            <xsl:value-of select="$date-value"/>
                        </code>
                    </xsl:when>
                    <xsl:otherwise>
                        <span class="status-badge status-missing">✗ Missing</span>
                    </xsl:otherwise>
                </xsl:choose>
            </div>
        </div>
    </xsl:template>

    <!-- Numeric validation template -->
    <xsl:template name="validate-numeric">
        <xsl:param name="field-name"/>
        <xsl:param name="numeric-value"/>

        <div class="fund-validation-item">
            <span class="fund-field-name">
                <xsl:value-of select="$field-name"/>
            </span>
            <div class="fund-validation-result">
                <xsl:choose>
                    <xsl:when test="$numeric-value and $numeric-value != '' and $numeric-value = $numeric-value + 0">
                        <span class="fund-status-badge fund-status-valid">✓ Numeric</span>
                        <code class="fund-code-value">
                            <xsl:value-of select="format-number($numeric-value, '#,##0.00')"/>
                        </code>
                    </xsl:when>
                    <xsl:when test="$numeric-value and $numeric-value != ''">
                        <span class="fund-status-badge fund-status-invalid">⚠ Invalid</span>
                        <code class="fund-code-value">
                            <xsl:value-of select="$numeric-value"/>
                        </code>
                    </xsl:when>
                    <xsl:otherwise>
                        <span class="fund-status-badge fund-status-missing">✗ Missing</span>
                    </xsl:otherwise>
                </xsl:choose>
            </div>
        </div>
    </xsl:template>

    <!-- Currency validation template -->
    <xsl:template name="validate-currency">
        <xsl:param name="field-name"/>
        <xsl:param name="currency-code"/>

        <div class="validation-item">
            <span class="field-name">
                <xsl:value-of select="$field-name"/>
            </span>
            <div class="validation-result">
                <xsl:choose>
                    <xsl:when test="$currency-code and string-length($currency-code) = 3">
                        <span class="status-badge status-valid">✓ Valid ISO</span>
                        <code class="code-value">
                            <xsl:value-of select="$currency-code"/>
                        </code>
                    </xsl:when>
                    <xsl:when test="$currency-code">
                        <span class="status-badge status-invalid">⚠ Format</span>
                        <code class="code-value">
                            <xsl:value-of select="$currency-code"/>
                        </code>
                    </xsl:when>
                    <xsl:otherwise>
                        <span class="status-badge status-missing">✗ Missing</span>
                    </xsl:otherwise>
                </xsl:choose>
            </div>
        </div>
    </xsl:template>

</xsl:stylesheet>