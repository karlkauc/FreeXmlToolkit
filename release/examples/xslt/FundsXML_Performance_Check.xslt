<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="html" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <xsl:template match="/FundsXML4">
        <html lang="de">
            <head>
                <meta charset="UTF-8"/>
                <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                <title>FundsXML - Performance &amp; Risk Analysis</title>
                <style>
                    /* Reset und Basis-Styles */
                    * {
                        margin: 0;
                        padding: 0;
                        box-sizing: border-box;
                    }
                    
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
                        background: linear-gradient(to bottom right, #0F172A, #581C87, #0F172A);
                        min-height: 100vh;
                        line-height: 1.5;
                    }
                    
                    /* Container */
                    .container {
                        max-width: 1400px;
                        margin: 0 auto;
                        padding: 2rem 1rem;
                    }
                    
                    /* Hauptkarte */
                    .main-card {
                        background: rgba(255, 255, 255, 0.95);
                        backdrop-filter: blur(10px);
                        border-radius: 1.5rem;
                        box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
                        overflow: hidden;
                        border: 1px solid rgba(255, 255, 255, 0.2);
                    }
                    
                    /* Hero Header */
                    .hero-header {
                        background: linear-gradient(to right, #7C3AED, #8B5CF6, #4C1D95);
                        padding: 2.5rem;
                        position: relative;
                        overflow: hidden;
                    }
                    
                    .hero-header::before {
                        content: '';
                        position: absolute;
                        inset: 0;
                        background: linear-gradient(to right, rgba(124, 58, 237, 0.2), transparent, rgba(76, 29, 149, 0.2));
                        animation: pulse 2s ease-in-out infinite;
                    }
                    
                    @keyframes pulse {
                        0%, 100% { opacity: 0.5; }
                        50% { opacity: 1; }
                    }
                    
                    @keyframes fadeIn {
                        from { opacity: 0; }
                        to { opacity: 1; }
                    }
                    
                    @keyframes slideIn {
                        from { transform: translateY(20px); opacity: 0; }
                        to { transform: translateY(0); opacity: 1; }
                    }
                    
                    .hero-content {
                        position: relative;
                        z-index: 10;
                    }
                    
                    .hero-title {
                        font-size: 3rem;
                        font-weight: bold;
                        color: white;
                        margin-bottom: 1rem;
                        animation: fadeIn 0.5s ease-in-out;
                    }
                    
                    .hero-subtitle {
                        color: #E9D5FF;
                        font-size: 1.25rem;
                        animation: slideIn 0.6s ease-out;
                    }
                    
                    /* Content */
                    .content {
                        padding: 2.5rem;
                    }
                    
                    /* Fund Overview Cards Grid */
                    .fund-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(350px, 1fr));
                        gap: 2rem;
                        margin-bottom: 3rem;
                    }
                    
                    /* Metric Card */
                    .metric-card {
                        background: linear-gradient(to bottom right, white, #F9FAFB);
                        border: 1px solid #E5E7EB;
                        border-radius: 1rem;
                        padding: 2rem;
                        box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1);
                        transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
                    }
                    
                    .metric-card:hover {
                        transform: translateY(-4px);
                        box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1);
                    }
                    
                    .metric-header {
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        margin-bottom: 1.5rem;
                    }
                    
                    .fund-name {
                        font-size: 1.5rem;
                        font-weight: bold;
                        color: #111827;
                    }
                    
                    .metric-icon {
                        width: 4rem;
                        height: 4rem;
                        background: linear-gradient(to bottom right, #3B82F6, #8B5CF6);
                        border-radius: 1rem;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        color: white;
                        font-size: 1.5rem;
                        font-weight: bold;
                    }
                    
                    .metric-list {
                        display: flex;
                        flex-direction: column;
                        gap: 1rem;
                    }
                    
                    .metric-item {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                    }
                    
                    .metric-label {
                        color: #6B7280;
                    }
                    
                    .metric-value {
                        font-size: 1.125rem;
                        font-weight: bold;
                        color: #111827;
                    }
                    
                    .metric-value.blue { color: #2563EB; }
                    
                    .currency-badge {
                        padding: 0.25rem 0.75rem;
                        background: #EDE9FE;
                        color: #7C3AED;
                        border-radius: 9999px;
                        font-size: 0.875rem;
                        font-weight: 500;
                    }
                    
                    .metric-value-small {
                        font-size: 0.875rem;
                        color: #6B7280;
                        margin-left: 0.25rem;
                    }
                    
                    /* Analysis Section */
                    .analysis-section {
                        background: white;
                        border: 1px solid #E5E7EB;
                        border-radius: 1rem;
                        padding: 2rem;
                        box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1);
                        margin-bottom: 3rem;
                    }
                    
                    .section-title {
                        font-size: 1.875rem;
                        font-weight: bold;
                        color: #111827;
                        margin-bottom: 2rem;
                        display: flex;
                        align-items: center;
                    }
                    
                    .section-icon {
                        width: 3rem;
                        height: 3rem;
                        border-radius: 1rem;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        font-size: 1.25rem;
                        font-weight: bold;
                        color: white;
                        margin-right: 1rem;
                    }
                    
                    .section-icon.emerald {
                        background: linear-gradient(to bottom right, #10B981, #14B8A6);
                    }
                    
                    .section-icon.red {
                        background: linear-gradient(to bottom right, #EF4444, #EC4899);
                    }
                    
                    .section-icon.yellow {
                        background: linear-gradient(to bottom right, #F59E0B, #FB923C);
                    }
                    
                    /* Fund Analysis Box */
                    .fund-analysis {
                        margin-bottom: 2rem;
                        padding-bottom: 2rem;
                        border-bottom: 1px solid #E5E7EB;
                    }
                    
                    .fund-analysis:last-child {
                        border-bottom: none;
                        padding-bottom: 0;
                        margin-bottom: 0;
                    }
                    
                    .fund-analysis-title {
                        font-size: 1.25rem;
                        font-weight: 600;
                        color: #1F2937;
                        margin-bottom: 1.5rem;
                    }
                    
                    /* Top Holdings */
                    .holdings-grid {
                        display: grid;
                        gap: 1rem;
                    }
                    
                    .holding-card {
                        background: linear-gradient(to bottom right, #F0FDF4, #DCFCE7);
                        border: 1px solid #BBF7D0;
                        border-radius: 0.75rem;
                        padding: 1.5rem;
                    }
                    
                    .holding-header {
                        display: flex;
                        align-items: flex-start;
                        justify-content: space-between;
                        margin-bottom: 1rem;
                    }
                    
                    .holding-name {
                        font-weight: 600;
                        color: #14532D;
                        margin-bottom: 0.25rem;
                    }
                    
                    .holding-id {
                        font-size: 0.75rem;
                        color: #15803D;
                    }
                    
                    .holding-percentage {
                        font-size: 1.5rem;
                        font-weight: bold;
                        color: #16A34A;
                    }
                    
                    .holding-details {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
                        gap: 0.75rem;
                    }
                    
                    .holding-metric {
                        padding: 0.5rem;
                        background: white;
                        border-radius: 0.5rem;
                        text-align: center;
                    }
                    
                    .holding-metric-label {
                        font-size: 0.75rem;
                        color: #6B7280;
                        margin-bottom: 0.25rem;
                    }
                    
                    .holding-metric-value {
                        font-size: 0.875rem;
                        font-weight: 600;
                        color: #111827;
                    }
                    
                    /* Asset Class */
                    .asset-class-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
                        gap: 1rem;
                    }
                    
                    .asset-class-item {
                        text-align: center;
                        padding: 1rem;
                        background: linear-gradient(to bottom right, #EFF6FF, #DBEAFE);
                        border: 1px solid #BFDBFE;
                        border-radius: 0.75rem;
                    }
                    
                    .asset-class-value {
                        font-size: 1.5rem;
                        font-weight: bold;
                        color: #2563EB;
                        margin-bottom: 0.5rem;
                    }
                    
                    .asset-class-label {
                        font-size: 0.875rem;
                        color: #1E40AF;
                        font-weight: 500;
                    }
                    
                    /* Risk Metrics */
                    .risk-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                        gap: 1.5rem;
                    }
                    
                    .risk-item {
                        background: linear-gradient(to bottom right, #F9FAFB, #F3F4F6);
                        border: 1px solid #E5E7EB;
                        border-radius: 0.75rem;
                        padding: 1.5rem;
                        text-align: center;
                    }
                    
                    .risk-value {
                        font-size: 1.5rem;
                        font-weight: bold;
                        color: #374151;
                        margin-bottom: 0.5rem;
                    }
                    
                    .risk-label {
                        color: #6B7280;
                        font-weight: 500;
                        font-size: 0.875rem;
                    }
                    
                    /* No Data Warning */
                    .no-data-warning {
                        background: #FEF3C7;
                        border: 1px solid #FDE68A;
                        border-radius: 0.75rem;
                        padding: 1.5rem;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        color: #D97706;
                        font-weight: 500;
                    }
                    
                    .warning-icon {
                        font-size: 1.5rem;
                        margin-right: 0.5rem;
                    }
                    
                    /* Currency Exposure */
                    .currency-exposure-box {
                        margin-bottom: 2rem;
                        padding: 1.5rem;
                        background: linear-gradient(to right, #FEF3C7, #FED7AA);
                        border: 1px solid #FDE68A;
                        border-radius: 0.75rem;
                    }
                    
                    .currency-exposure-title {
                        font-size: 1.25rem;
                        font-weight: 600;
                        color: #1F2937;
                        margin-bottom: 1rem;
                    }
                    
                    .currency-exposure-subtitle {
                        font-size: 0.875rem;
                        color: #6B7280;
                        margin-left: 0.5rem;
                    }
                    
                    .currency-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
                        gap: 1rem;
                    }
                    
                    .currency-item {
                        text-align: center;
                        padding: 1rem;
                        background: white;
                        border: 1px solid #FCD34D;
                        border-radius: 0.5rem;
                    }
                    
                    .currency-count {
                        font-size: 1.125rem;
                        font-weight: bold;
                        color: #D97706;
                    }
                    
                    .currency-code {
                        font-size: 0.875rem;
                        color: #F59E0B;
                    }
                    
                    /* Footer */
                    .footer {
                        background: linear-gradient(to right, #F1F5F9, #FAF5FF, #F1F5F9);
                        padding: 1.5rem;
                        text-align: center;
                    }
                    
                    .footer p {
                        font-size: 0.875rem;
                        color: #64748B;
                    }
                    
                    /* Responsive Design */
                    @media (max-width: 768px) {
                        .hero-title {
                            font-size: 2rem;
                        }
                        
                        .fund-grid {
                            grid-template-columns: 1fr;
                        }
                        
                        .holding-details {
                            grid-template-columns: 1fr;
                        }
                        
                        .asset-class-grid,
                        .risk-grid {
                            grid-template-columns: repeat(2, 1fr);
                        }
                        
                        .currency-grid {
                            grid-template-columns: repeat(2, 1fr);
                        }
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="main-card">
                        <!-- Hero Header -->
                        <div class="hero-header">
                            <div class="hero-content">
                                <h1 class="hero-title">Performance Analysis</h1>
                                <p class="hero-subtitle">
                                    Fund performance metrics and risk assessment dashboard
                                </p>
                            </div>
                        </div>

                        <div class="content">
                            <!-- Fund Overview Cards -->
                            <div class="fund-grid">
                                <xsl:for-each select="Funds/Fund">
                                    <div class="metric-card">
                                        <div class="metric-header">
                                            <h2 class="fund-name">
                                                <xsl:value-of select="Names/OfficialName"/>
                                            </h2>
                                            <div class="metric-icon">💼</div>
                                        </div>

                                        <div class="metric-list">
                                            <div class="metric-item">
                                                <span class="metric-label">Total NAV</span>
                                                <span class="metric-value">
                                                    <xsl:value-of
                                                            select="format-number(FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount, '#,##0.00')"/>
                                                    <span class="metric-value-small">
                                                        <xsl:value-of
                                                                select="FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount/@ccy"/>
                                                    </span>
                                                </span>
                                            </div>
                                            <div class="metric-item">
                                                <span class="metric-label">Positions</span>
                                                <span class="metric-value blue">
                                                    <xsl:value-of
                                                            select="count(FundDynamicData/Portfolios/Portfolio/Positions/Position)"/>
                                                </span>
                                            </div>
                                            <div class="metric-item">
                                                <span class="metric-label">Currency</span>
                                                <span class="currency-badge">
                                                    <xsl:value-of select="Currency"/>
                                                </span>
                                            </div>
                                            <div class="metric-item">
                                                <span class="metric-label">Inception</span>
                                                <span class="metric-value">
                                                    <xsl:value-of select="FundStaticData/InceptionDate"/>
                                                </span>
                                            </div>
                                        </div>
                                    </div>
                                </xsl:for-each>
                            </div>

                            <!-- Portfolio Concentration Analysis -->
                            <div class="analysis-section">
                                <h2 class="section-title">
                                    <div class="section-icon emerald">📈</div>
                                    Portfolio Concentration Analysis
                                </h2>

                                <xsl:for-each select="Funds/Fund">
                                    <div class="fund-analysis">
                                        <h3 class="fund-analysis-title">
                                            Top Holdings - <xsl:value-of select="Names/OfficialName"/>
                                        </h3>

                                        <div class="holdings-grid">
                                            <xsl:for-each
                                                    select="FundDynamicData/Portfolios/Portfolio/Positions/Position">
                                                <xsl:sort select="TotalPercentage" data-type="number" order="descending"/>
                                                <xsl:if test="position() &lt;= 5">
                                                    <div class="holding-card">
                                                        <div class="holding-header">
                                                            <div>
                                                                <div class="holding-name">
                                                                    Position #<xsl:value-of select="position()"/>
                                                                </div>
                                                                <div class="holding-id">
                                                                    ID: <xsl:value-of select="UniqueID"/>
                                                                </div>
                                                            </div>
                                                            <div class="holding-percentage">
                                                                <xsl:value-of
                                                                        select="format-number(TotalPercentage, '0.00')"/>%
                                                            </div>
                                                        </div>

                                                        <div class="holding-details">
                                                            <div class="holding-metric">
                                                                <div class="holding-metric-label">Value</div>
                                                                <div class="holding-metric-value">
                                                                    <xsl:value-of
                                                                            select="format-number(TotalValue/Amount, '#,##0')"/>
                                                                </div>
                                                            </div>
                                                            <div class="holding-metric">
                                                                <div class="holding-metric-label">Currency</div>
                                                                <div class="holding-metric-value">
                                                                    <xsl:value-of select="Currency"/>
                                                                </div>
                                                            </div>
                                                            <xsl:if test="AssetClass">
                                                                <div class="holding-metric">
                                                                    <div class="holding-metric-label">Class</div>
                                                                    <div class="holding-metric-value">
                                                                        <xsl:value-of select="AssetClass"/>
                                                                    </div>
                                                                </div>
                                                            </xsl:if>
                                                        </div>
                                                    </div>
                                                </xsl:if>
                                            </xsl:for-each>
                                        </div>
                                    </div>
                                </xsl:for-each>
                            </div>

                            <!-- Asset Class Distribution -->
                            <div class="analysis-section">
                                <h2 class="section-title">
                                    <div class="section-icon emerald">📊</div>
                                    Asset Class Distribution
                                </h2>

                                <xsl:for-each select="Funds/Fund">
                                    <div class="fund-analysis">
                                        <h3 class="fund-analysis-title">
                                            <xsl:value-of select="Names/OfficialName"/>
                                        </h3>

                                        <div class="asset-class-grid">
                                            <xsl:for-each
                                                    select="FundDynamicData/Portfolios/Portfolio/Positions/Position/AssetClass[generate-id() = generate-id(key('asset-classes', .)[1])]">
                                                <xsl:sort select="."/>
                                                <div class="asset-class-item">
                                                    <div class="asset-class-value">
                                                        <xsl:value-of select="count(key('asset-classes', .))"/>
                                                    </div>
                                                    <div class="asset-class-label">
                                                        <xsl:choose>
                                                            <xsl:when test=". = 'Equity'">Equity</xsl:when>
                                                            <xsl:when test=". = 'Bond'">Bonds</xsl:when>
                                                            <xsl:when test=". = 'Cash'">Cash</xsl:when>
                                                            <xsl:when test=". = 'Alternative'">Alternatives</xsl:when>
                                                            <xsl:otherwise>
                                                                <xsl:value-of select="."/>
                                                            </xsl:otherwise>
                                                        </xsl:choose>
                                                    </div>
                                                </div>
                                            </xsl:for-each>
                                        </div>
                                    </div>
                                </xsl:for-each>
                            </div>

                            <!-- Risk Metrics Dashboard -->
                            <div class="analysis-section">
                                <h2 class="section-title">
                                    <div class="section-icon red">⚡</div>
                                    Risk Metrics Dashboard
                                </h2>

                                <xsl:for-each select="Funds/Fund">
                                    <div class="fund-analysis">
                                        <h3 class="fund-analysis-title">
                                            Risk Assessment: <xsl:value-of select="Names/OfficialName"/>
                                        </h3>

                                        <xsl:choose>
                                            <xsl:when test="FundDynamicData/Portfolios/Portfolio/RiskCodes/RiskCode">
                                                <div class="risk-grid">
                                                    <xsl:for-each
                                                            select="FundDynamicData/Portfolios/Portfolio/RiskCodes/RiskCode">
                                                        <div class="risk-item">
                                                            <div class="risk-value">
                                                                <xsl:value-of select="format-number(Value, '0.0000')"/>
                                                            </div>
                                                            <div class="risk-label">
                                                                <xsl:value-of select="ListedCode | UnlistedCode"/>
                                                            </div>
                                                        </div>
                                                    </xsl:for-each>
                                                </div>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <div class="no-data-warning">
                                                    <span class="warning-icon">⚠️</span>
                                                    <span>No risk metrics available for this fund</span>
                                                </div>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </div>
                                </xsl:for-each>
                            </div>

                            <!-- Currency Exposure Analysis -->
                            <div class="analysis-section">
                                <h2 class="section-title">
                                    <div class="section-icon yellow">💱</div>
                                    Currency Exposure Analysis
                                </h2>

                                <xsl:for-each select="Funds/Fund">
                                    <div class="currency-exposure-box">
                                        <h3 class="currency-exposure-title">
                                            <xsl:value-of select="Names/OfficialName"/>
                                            <span class="currency-exposure-subtitle">(Base: <xsl:value-of
                                                    select="Currency"/>)
                                            </span>
                                        </h3>

                                        <div class="currency-grid">
                                            <!-- Count different currencies -->
                                            <xsl:for-each
                                                    select="FundDynamicData/Portfolios/Portfolio/Positions/Position/Currency[generate-id() = generate-id(key('position-currencies', .)[1])]">
                                                <xsl:sort select="."/>
                                                <div class="currency-item">
                                                    <div class="currency-count">
                                                        <xsl:value-of select="count(key('position-currencies', .))"/>
                                                    </div>
                                                    <div class="currency-code">
                                                        <xsl:value-of select="."/> positions
                                                    </div>
                                                </div>
                                            </xsl:for-each>
                                        </div>
                                    </div>
                                </xsl:for-each>
                            </div>
                        </div>

                        <!-- Footer -->
                        <div class="footer">
                            <p>Generated with embedded CSS • Performance &amp; Risk Analysis Dashboard</p>
                        </div>
                    </div>
                </div>
            </body>
        </html>
    </xsl:template>

    <!-- Keys for grouping -->
    <xsl:key name="position-currencies" match="Position/Currency" use="."/>
    <xsl:key name="asset-classes" match="Position/AssetClass" use="."/>

</xsl:stylesheet>