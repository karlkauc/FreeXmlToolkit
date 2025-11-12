<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="html" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <xsl:template match="/FundsXML4">
        <html lang="de">
            <head>
                <meta charset="UTF-8"/>
                <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                <title>FundsXML - Business Rules Validation</title>
                <style>
                    /* Reset und Basis-Styles */
                    * {
                        margin: 0;
                        padding: 0;
                        box-sizing: border-box;
                    }
                    
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
                        background: linear-gradient(to bottom right, #F9FAFB, #F3F4F6);
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
                        border-radius: 1rem;
                        box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
                        overflow: hidden;
                    }
                    
                    /* Header */
                    .header {
                        background: linear-gradient(to right, #4F46E5, #7C3AED);
                        padding: 2rem;
                        color: white;
                    }
                    
                    .header h1 {
                        font-size: 2.25rem;
                        font-weight: bold;
                        margin-bottom: 0.5rem;
                    }
                    
                    .header p {
                        color: #E0E7FF;
                        font-size: 1.125rem;
                    }
                    
                    /* Content */
                    .content {
                        padding: 2rem;
                    }
                    
                    /* Summary Cards Grid */
                    .summary-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
                        gap: 1.5rem;
                        margin-bottom: 2rem;
                    }
                    
                    .summary-card {
                        border-radius: 0.75rem;
                        padding: 1.5rem;
                        border: 1px solid;
                    }
                    
                    .summary-card.blue {
                        background: #EFF6FF;
                        border-color: #BFDBFE;
                    }
                    
                    .summary-card.green {
                        background: #F0FDF4;
                        border-color: #BBF7D0;
                    }
                    
                    .summary-card.purple {
                        background: #FAF5FF;
                        border-color: #E9D5FF;
                    }
                    
                    .summary-card-content {
                        display: flex;
                        align-items: center;
                    }
                    
                    .icon-box {
                        width: 3rem;
                        height: 3rem;
                        border-radius: 0.5rem;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        font-size: 1.25rem;
                        font-weight: bold;
                        color: white;
                    }
                    
                    .icon-box.blue { background: #3B82F6; }
                    .icon-box.green { background: #10B981; }
                    .icon-box.purple { background: #8B5CF6; }
                    
                    .card-info {
                        margin-left: 1rem;
                    }
                    
                    .card-title {
                        font-weight: 600;
                    }
                    
                    .summary-card.blue .card-title { color: #1E3A8A; }
                    .summary-card.green .card-title { color: #14532D; }
                    .summary-card.purple .card-title { color: #581C87; }
                    
                    .card-value {
                        font-size: 0.875rem;
                    }
                    
                    .summary-card.blue .card-value { color: #2563EB; }
                    .summary-card.green .card-value { color: #16A34A; }
                    .summary-card.purple .card-value { color: #7C3AED; }
                    
                    /* Validation Sections */
                    .validation-section {
                        background: white;
                        border: 1px solid #E5E7EB;
                        border-radius: 0.75rem;
                        padding: 1.5rem;
                        box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.1);
                        margin-bottom: 2rem;
                    }
                    
                    .section-title {
                        font-size: 1.5rem;
                        font-weight: 600;
                        color: #111827;
                        margin-bottom: 1.5rem;
                        display: flex;
                        align-items: center;
                    }
                    
                    .section-number {
                        width: 2.5rem;
                        height: 2.5rem;
                        border-radius: 0.75rem;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        font-size: 1.125rem;
                        font-weight: bold;
                        color: white;
                        margin-right: 1rem;
                    }
                    
                    .section-number.orange { background: #F97316; }
                    .section-number.green { background: #10B981; }
                    .section-number.blue { background: #3B82F6; }
                    .section-number.red { background: #EF4444; }
                    
                    /* Fund Box */
                    .fund-box {
                        margin-bottom: 1.5rem;
                        padding: 1rem;
                        border: 1px solid #E5E7EB;
                        border-radius: 0.5rem;
                    }
                    
                    .fund-title {
                        font-size: 1.125rem;
                        font-weight: 600;
                        color: #1F2937;
                        margin-bottom: 1rem;
                    }
                    
                    /* Portfolio Box */
                    .portfolio-box {
                        background: #F9FAFB;
                        border-radius: 0.5rem;
                        padding: 1rem;
                        margin-bottom: 0.75rem;
                    }
                    
                    .portfolio-header {
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        flex-wrap: wrap;
                        gap: 1rem;
                    }
                    
                    .portfolio-info {
                        flex: 1;
                    }
                    
                    .label {
                        font-weight: 500;
                        color: #4B5563;
                    }
                    
                    .value {
                        color: #111827;
                    }
                    
                    .portfolio-summary {
                        text-align: right;
                    }
                    
                    .summary-label {
                        font-size: 0.75rem;
                        color: #6B7280;
                    }
                    
                    .summary-value {
                        font-size: 1.25rem;
                        font-weight: bold;
                    }
                    
                    .percentage-ok {
                        color: #16A34A;
                    }
                    
                    .percentage-warning {
                        color: #F59E0B;
                    }
                    
                    .percentage-error {
                        color: #DC2626;
                    }
                    
                    /* Position Details */
                    .position-details {
                        margin-top: 0.75rem;
                        padding-top: 0.75rem;
                        border-top: 1px solid #E5E7EB;
                    }
                    
                    .position-count {
                        font-size: 0.875rem;
                        color: #6B7280;
                        margin-bottom: 0.5rem;
                    }
                    
                    .position-list {
                        display: grid;
                        gap: 0.5rem;
                    }
                    
                    .position-item {
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        padding: 0.75rem;
                        background: white;
                        border-radius: 0.25rem;
                        border: 1px solid #E5E7EB;
                    }
                    
                    .position-info {
                        flex: 1;
                    }
                    
                    .position-id {
                        font-size: 0.875rem;
                        font-weight: 500;
                        color: #4B5563;
                    }
                    
                    .position-metrics {
                        font-size: 0.75rem;
                        color: #6B7280;
                        margin-top: 0.25rem;
                    }
                    
                    /* Status Badge */
                    .status-badge {
                        padding: 0.25rem 0.5rem;
                        font-size: 0.75rem;
                        border-radius: 0.25rem;
                        font-weight: 500;
                        white-space: nowrap;
                    }
                    
                    .status-match {
                        background: #DCFCE7;
                        color: #16A34A;
                    }
                    
                    .status-fx {
                        background: #DBEAFE;
                        color: #2563EB;
                    }
                    
                    .status-consistent {
                        background: #DCFCE7;
                        color: #16A34A;
                    }
                    
                    .status-inconsistent {
                        background: #FEE2E2;
                        color: #DC2626;
                    }
                    
                    /* NAV Display Box */
                    .nav-display {
                        display: flex;
                        gap: 1rem;
                        margin-top: 0.75rem;
                        flex-wrap: wrap;
                    }
                    
                    .nav-item {
                        padding: 0.5rem;
                        background: white;
                        border-radius: 0.375rem;
                        border: 1px solid #E5E7EB;
                        flex: 1;
                        min-width: 120px;
                    }
                    
                    .nav-label {
                        font-size: 0.75rem;
                        color: #6B7280;
                        margin-bottom: 0.25rem;
                    }
                    
                    .nav-value {
                        font-size: 0.875rem;
                        font-weight: 600;
                        color: #111827;
                    }
                    
                    /* Fund NAV Info */
                    .fund-nav-info {
                        font-size: 0.875rem;
                        color: #6B7280;
                        margin-top: 0.25rem;
                    }
                    
                    /* Footer */
                    .footer {
                        background: #F3F4F6;
                        padding: 1.5rem;
                        text-align: center;
                    }
                    
                    .footer p {
                        font-size: 0.875rem;
                        color: #6B7280;
                    }
                    
                    /* Responsive Design */
                    @media (max-width: 768px) {
                        .summary-grid {
                            grid-template-columns: 1fr;
                        }
                        
                        .portfolio-header {
                            flex-direction: column;
                            align-items: stretch;
                        }
                        
                        .portfolio-summary {
                            text-align: left;
                            margin-top: 0.5rem;
                            padding-top: 0.5rem;
                            border-top: 1px solid #E5E7EB;
                        }
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="main-card">
                        <!-- Header -->
                        <div class="header">
                            <h1>Business Rules Validation</h1>
                            <p>Comprehensive fund data consistency and business logic checks</p>
                        </div>

                        <div class="content">
                            <!-- Summary Cards -->
                            <div class="summary-grid">
                                <div class="summary-card blue">
                                    <div class="summary-card-content">
                                        <div class="icon-box blue">
                                            <span>📊</span>
                                        </div>
                                        <div class="card-info">
                                            <h3 class="card-title">Document</h3>
                                            <p class="card-value">
                                                <xsl:value-of select="ControlData/UniqueDocumentID"/>
                                            </p>
                                        </div>
                                    </div>
                                </div>
                                <div class="summary-card green">
                                    <div class="summary-card-content">
                                        <div class="icon-box green">
                                            <span>💰</span>
                                        </div>
                                        <div class="card-info">
                                            <h3 class="card-title">Funds</h3>
                                            <p class="card-value">
                                                <xsl:value-of select="count(Funds/Fund)"/> fund(s)
                                            </p>
                                        </div>
                                    </div>
                                </div>
                                <div class="summary-card purple">
                                    <div class="summary-card-content">
                                        <div class="icon-box purple">
                                            <span>📅</span>
                                        </div>
                                        <div class="card-info">
                                            <h3 class="card-title">Date</h3>
                                            <p class="card-value">
                                                <xsl:value-of select="ControlData/ContentDate"/>
                                            </p>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <!-- Portfolio Percentage Sum Check -->
                            <div class="validation-section">
                                <h2 class="section-title">
                                    <div class="section-number orange">1</div>
                                    Portfolio Percentage Sum Check
                                </h2>

                                <xsl:for-each select="Funds/Fund">
                                    <div class="fund-box">
                                        <h3 class="fund-title">
                                            Fund: <xsl:value-of select="Names/OfficialName"/>
                                        </h3>

                                        <xsl:for-each select="FundDynamicData/Portfolios/Portfolio">
                                            <xsl:variable name="totalPercentage"
                                                          select="sum(Positions/Position/TotalPercentage)"/>
                                            <div class="portfolio-box">
                                                <div class="portfolio-header">
                                                    <div class="portfolio-info">
                                                        <span class="label">Portfolio Date: </span>
                                                        <span class="value">
                                                            <xsl:value-of select="NavDate"/>
                                                        </span>
                                                    </div>
                                                    <div class="portfolio-summary">
                                                        <div class="summary-label">Total Percentage Sum</div>
                                                        <div class="summary-value">
                                                            <xsl:choose>
                                                                <xsl:when
                                                                        test="$totalPercentage &gt; 99.5 and $totalPercentage &lt; 100.5">
                                                                    <span class="percentage-ok">
                                                                        <xsl:value-of select="format-number($totalPercentage, '0.00')"/>%
                                                                    </span>
                                                                </xsl:when>
                                                                <xsl:otherwise>
                                                                    <span class="percentage-error">
                                                                        <xsl:value-of select="format-number($totalPercentage, '0.00')"/>%
                                                                    </span>
                                                                </xsl:otherwise>
                                                            </xsl:choose>
                                                        </div>
                                                    </div>
                                                </div>

                                                <div class="position-details">
                                                    <div class="position-count">
                                                        Positions: <xsl:value-of select="count(Positions/Position)"/>
                                                    </div>
                                                </div>
                                            </div>
                                        </xsl:for-each>
                                    </div>
                                </xsl:for-each>
                            </div>

                            <!-- NAV Date Consistency Check -->
                            <div class="validation-section">
                                <h2 class="section-title">
                                    <div class="section-number green">2</div>
                                    NAV Date Consistency Check
                                </h2>

                                <xsl:for-each select="Funds/Fund">
                                    <div class="fund-box">
                                        <h3 class="fund-title">
                                            Fund: <xsl:value-of select="Names/OfficialName"/>
                                        </h3>

                                        <div class="nav-display">
                                            <div class="nav-item">
                                                <div class="nav-label">Total NAV Date</div>
                                                <div class="nav-value">
                                                    <xsl:value-of select="FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Date"/>
                                                </div>
                                            </div>
                                            <xsl:for-each select="FundDynamicData/Portfolios/Portfolio">
                                                <div class="nav-item">
                                                    <div class="nav-label">Portfolio NAV Date</div>
                                                    <div class="nav-value">
                                                        <xsl:value-of select="NavDate"/>
                                                    </div>
                                                </div>
                                            </xsl:for-each>
                                        </div>
                                    </div>
                                </xsl:for-each>
                            </div>

                            <!-- Currency Consistency Check -->
                            <div class="validation-section">
                                <h2 class="section-title">
                                    <div class="section-number blue">3</div>
                                    Currency Consistency Check
                                </h2>

                                <xsl:for-each select="Funds/Fund">
                                    <div class="fund-box">
                                        <h3 class="fund-title">
                                            Fund: <xsl:value-of select="Names/OfficialName"/>
                                            <span style="margin-left: 0.5rem; color: #6B7280;">
                                                (Currency: <xsl:value-of select="Currency"/>)
                                            </span>
                                        </h3>

                                        <div class="position-list">
                                            <xsl:for-each
                                                    select="FundDynamicData/Portfolios/Portfolio/Positions/Position[position() &lt;= 10]">
                                                <div class="position-item">
                                                    <div class="position-info">
                                                        <div class="position-id">
                                                            Position: <xsl:value-of select="UniqueID"/>
                                                        </div>
                                                        <div class="position-metrics">
                                                            Currency: <xsl:value-of select="Currency"/>
                                                        </div>
                                                    </div>
                                                    <div>
                                                        <xsl:choose>
                                                            <xsl:when test="Currency = ../../../../../../Currency">
                                                                <span class="status-badge status-match">
                                                                    ✓ Match
                                                                </span>
                                                            </xsl:when>
                                                            <xsl:otherwise>
                                                                <span class="status-badge status-fx">
                                                                    FX
                                                                </span>
                                                            </xsl:otherwise>
                                                        </xsl:choose>
                                                    </div>
                                                </div>
                                            </xsl:for-each>
                                        </div>
                                    </div>
                                </xsl:for-each>
                            </div>

                            <!-- Position Value Consistency Check -->
                            <div class="validation-section">
                                <h2 class="section-title">
                                    <div class="section-number red">4</div>
                                    Position Value vs. Percentage Check
                                </h2>

                                <xsl:for-each select="Funds/Fund">
                                    <xsl:variable name="totalNav"
                                                  select="FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount"/>

                                    <div class="fund-box">
                                        <h3 class="fund-title">
                                            Fund: <xsl:value-of select="Names/OfficialName"/>
                                            <div class="fund-nav-info">
                                                Total NAV: 
                                                <xsl:value-of select="format-number($totalNav, '#,##0.00')"/>
                                                <xsl:text> </xsl:text>
                                                <xsl:value-of select="FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount/@ccy"/>
                                            </div>
                                        </h3>

                                        <div class="position-list">
                                            <xsl:for-each
                                                    select="FundDynamicData/Portfolios/Portfolio/Positions/Position[position() &lt;= 5]">
                                                <xsl:variable name="positionValue" select="TotalValue/Amount"/>
                                                <xsl:variable name="positionPercentage" select="TotalPercentage"/>
                                                <xsl:variable name="calculatedPercentage"
                                                              select="($positionValue div $totalNav) * 100"/>
                                                <xsl:variable name="percentageDiff"
                                                              select="$positionPercentage - $calculatedPercentage"/>

                                                <div class="position-item">
                                                    <div class="position-info">
                                                        <div class="position-id">
                                                            Position: <xsl:value-of select="UniqueID"/>
                                                        </div>
                                                        <div class="position-metrics">
                                                            Value: <xsl:value-of select="format-number($positionValue, '#,##0.00')"/>
                                                            | Stated: <xsl:value-of select="format-number($positionPercentage, '0.00')"/>%
                                                            | Calculated: <xsl:value-of select="format-number($calculatedPercentage, '0.00')"/>%
                                                        </div>
                                                    </div>
                                                    <div>
                                                        <xsl:choose>
                                                            <xsl:when test="$percentageDiff &gt;= -0.1 and $percentageDiff &lt;= 0.1">
                                                                <span class="status-badge status-consistent">
                                                                    ✓ Consistent
                                                                </span>
                                                            </xsl:when>
                                                            <xsl:otherwise>
                                                                <span class="status-badge status-inconsistent">
                                                                    ⚠ Diff: <xsl:value-of select="format-number($percentageDiff, '0.00')"/>%
                                                                </span>
                                                            </xsl:otherwise>
                                                        </xsl:choose>
                                                    </div>
                                                </div>
                                            </xsl:for-each>
                                        </div>
                                    </div>
                                </xsl:for-each>
                            </div>
                        </div>

                        <div class="footer">
                            <p>Generated with embedded CSS • Business Rules Validation Report</p>
                        </div>
                    </div>
                </div>
            </body>
        </html>
    </xsl:template>

</xsl:stylesheet>