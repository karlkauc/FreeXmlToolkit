<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="html" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <xsl:template match="/FundsXML4">
        <html lang="de">
            <head>
                <meta charset="UTF-8"/>
                <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                <title>FundsXML - XML Structure Validation</title>
                <style>
                    /* Reset und Basis-Styles */
                    * {
                        margin: 0;
                        padding: 0;
                        box-sizing: border-box;
                    }
                    
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
                        background: linear-gradient(to bottom right, #EEF2FF, #FFFFFF, #ECFEFF);
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
                        border-radius: 1.5rem;
                        box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
                        overflow: hidden;
                        border: 1px solid #f3f4f6;
                    }
                    
                    /* Header */
                    .header {
                        background: linear-gradient(to right, #2563EB, #4F46E5, #7C3AED);
                        padding: 2rem;
                        position: relative;
                        overflow: hidden;
                    }
                    
                    .header::before {
                        content: '';
                        position: absolute;
                        top: 0;
                        left: -25%;
                        width: 150%;
                        height: 100%;
                        background: linear-gradient(to right, transparent, rgba(255,255,255,0.1), transparent);
                        transform: skewX(-12deg);
                    }
                    
                    .header-content {
                        position: relative;
                        z-index: 10;
                    }
                    
                    .header h1 {
                        color: white;
                        font-size: 2.25rem;
                        font-weight: bold;
                        margin-bottom: 0.75rem;
                    }
                    
                    .header p {
                        color: #DBEAFE;
                        font-size: 1.125rem;
                    }
                    
                    /* Content */
                    .content {
                        padding: 2rem;
                    }
                    
                    /* Stats Grid */
                    .stats-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
                        gap: 1.5rem;
                        margin-bottom: 2rem;
                    }
                    
                    .stat-card {
                        border-radius: 1rem;
                        padding: 1.5rem;
                        transition: transform 0.2s;
                        border: 1px solid;
                    }
                    
                    .stat-card:hover {
                        transform: scale(1.05);
                    }
                    
                    .stat-card.blue {
                        background: linear-gradient(to bottom right, #EFF6FF, #DBEAFE);
                        border-color: #BFDBFE;
                    }
                    
                    .stat-card.green {
                        background: linear-gradient(to bottom right, #F0FDF4, #DCFCE7);
                        border-color: #BBF7D0;
                    }
                    
                    .stat-card.purple {
                        background: linear-gradient(to bottom right, #FAF5FF, #F3E8FF);
                        border-color: #E9D5FF;
                    }
                    
                    .stat-card.orange {
                        background: linear-gradient(to bottom right, #FFF7ED, #FED7AA);
                        border-color: #FED7AA;
                    }
                    
                    .stat-value {
                        font-size: 1.875rem;
                        font-weight: bold;
                        margin-bottom: 0.5rem;
                    }
                    
                    .stat-card.blue .stat-value { color: #2563EB; }
                    .stat-card.green .stat-value { color: #16A34A; }
                    .stat-card.purple .stat-value { color: #9333EA; }
                    .stat-card.orange .stat-value { color: #EA580C; }
                    
                    .stat-label {
                        font-weight: 500;
                    }
                    
                    .stat-card.blue .stat-label { color: #1E40AF; }
                    .stat-card.green .stat-label { color: #15803D; }
                    .stat-card.purple .stat-label { color: #6B21A8; }
                    .stat-card.orange .stat-label { color: #C2410C; }
                    
                    /* Section Cards */
                    .section-card {
                        background: white;
                        border: 1px solid #E5E7EB;
                        border-radius: 1rem;
                        padding: 2rem;
                        box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1);
                        margin-bottom: 2rem;
                    }
                    
                    .section-title {
                        font-size: 1.875rem;
                        font-weight: bold;
                        color: #111827;
                        margin-bottom: 2rem;
                        display: flex;
                        align-items: center;
                    }
                    
                    .icon-box {
                        width: 3rem;
                        height: 3rem;
                        background: linear-gradient(to bottom right, #6366F1, #7C3AED);
                        color: white;
                        border-radius: 1rem;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        font-size: 1.25rem;
                        font-weight: bold;
                        margin-right: 1rem;
                    }
                    
                    /* Code Block */
                    .code-block {
                        background: #111827;
                        border-radius: 0.75rem;
                        padding: 1.5rem;
                        overflow-x: auto;
                    }
                    
                    .code-text {
                        font-family: 'Courier New', Consolas, monospace;
                        font-size: 0.875rem;
                        line-height: 1.625;
                        color: #10B981;
                    }
                    
                    .code-title {
                        color: #67E8F9;
                        font-weight: bold;
                        margin-bottom: 0.5rem;
                    }
                    
                    .code-element {
                        color: #FDE047;
                    }
                    
                    .code-value {
                        color: white;
                    }
                    
                    .code-comment {
                        color: #9CA3AF;
                        font-size: 0.75rem;
                    }
                    
                    .code-indent-1 { margin-left: 1rem; }
                    .code-indent-2 { margin-left: 1.5rem; }
                    .code-indent-3 { margin-left: 2rem; }
                    
                    /* Tree Styles */
                    .tree-line { 
                        border-left: 2px solid #e5e7eb; 
                    }
                    
                    .tree-branch::before { 
                        content: "├─ "; 
                        color: #9ca3af; 
                    }
                    
                    .tree-last::before { 
                        content: "└─ "; 
                        color: #9ca3af; 
                    }
                    
                    .tree-element { 
                        font-family: 'Courier New', monospace; 
                    }
                    
                    /* Analysis Grid */
                    .analysis-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
                        gap: 1.5rem;
                    }
                    
                    .analysis-box {
                        border-radius: 0.75rem;
                        padding: 1.5rem;
                        border: 1px solid;
                    }
                    
                    .analysis-box.blue {
                        background: #EFF6FF;
                        border-color: #BFDBFE;
                    }
                    
                    .analysis-box.green {
                        background: #F0FDF4;
                        border-color: #BBF7D0;
                    }
                    
                    .analysis-box.purple {
                        background: #FAF5FF;
                        border-color: #E9D5FF;
                    }
                    
                    .analysis-title {
                        font-weight: 600;
                        margin-bottom: 1rem;
                    }
                    
                    .analysis-box.blue .analysis-title { color: #1E40AF; }
                    .analysis-box.green .analysis-title { color: #14532D; }
                    .analysis-box.purple .analysis-title { color: #581C87; }
                    
                    .analysis-content {
                        font-size: 0.875rem;
                    }
                    
                    .analysis-item {
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        margin-bottom: 0.5rem;
                    }
                    
                    .analysis-code {
                        font-size: 0.875rem;
                        padding: 0.25rem 0.5rem;
                        border-radius: 0.25rem;
                    }
                    
                    .analysis-box.blue .analysis-code { 
                        background: #DBEAFE; 
                    }
                    
                    .analysis-box.green .analysis-code { 
                        background: #BBF7D0;
                        padding: 0.5rem;
                        border-radius: 0.25rem;
                        margin-bottom: 0.5rem;
                    }
                    
                    .analysis-box.purple .analysis-code { 
                        background: #E9D5FF; 
                    }
                    
                    .analysis-count {
                        font-size: 0.875rem;
                        font-weight: 500;
                    }
                    
                    .analysis-box.blue .analysis-count { color: #2563EB; }
                    .analysis-box.purple .analysis-count { color: #7C3AED; }
                    
                    /* Footer */
                    .footer {
                        background: linear-gradient(to right, #F3F4F6, #E5E7EB);
                        padding: 1.5rem;
                        text-align: center;
                    }
                    
                    .footer p {
                        font-size: 0.875rem;
                        color: #4B5563;
                    }
                    
                    /* Structure Check Styles */
                    .check-item {
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        padding: 1rem;
                        background: #F9FAFB;
                        border-radius: 0.5rem;
                        border: 1px solid #E5E7EB;
                        margin-bottom: 0.5rem;
                    }
                    
                    .check-info {
                        flex: 1;
                    }
                    
                    .check-name {
                        font-weight: 500;
                        color: #1F2937;
                    }
                    
                    .check-description {
                        font-size: 0.875rem;
                        color: #6B7280;
                    }
                    
                    .check-status {
                        display: flex;
                        align-items: center;
                        padding: 0.25rem 0.75rem;
                        border-radius: 9999px;
                        font-size: 0.875rem;
                        font-weight: 500;
                    }
                    
                    .check-status.pass {
                        background: #DCFCE7;
                        color: #15803D;
                    }
                    
                    .check-status.fail {
                        background: #FEE2E2;
                        color: #DC2626;
                    }
                    
                    .check-icon {
                        font-size: 1.125rem;
                        margin-right: 0.25rem;
                    }
                    
                    /* Progress Bar */
                    .progress-container {
                        margin-bottom: 1rem;
                    }
                    
                    .progress-header {
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        margin-bottom: 0.5rem;
                    }
                    
                    .progress-label {
                        font-size: 0.875rem;
                        font-weight: 500;
                        color: #374151;
                    }
                    
                    .progress-value {
                        font-size: 0.875rem;
                        font-weight: bold;
                        color: #111827;
                    }
                    
                    .progress-bar {
                        width: 100%;
                        height: 0.5rem;
                        background: #E5E7EB;
                        border-radius: 9999px;
                        overflow: hidden;
                    }
                    
                    .progress-fill {
                        height: 100%;
                        border-radius: 9999px;
                        transition: width 0.5s ease;
                    }
                    
                    .progress-fill.blue { background: #3B82F6; }
                    .progress-fill.green { background: #10B981; }
                    .progress-fill.purple { background: #8B5CF6; }
                    .progress-fill.orange { background: #F59E0B; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="main-card">
                        <!-- Animated Header -->
                        <div class="header">
                            <div class="header-content">
                                <h1>XML Structure Analysis</h1>
                                <p>Comprehensive document structure and hierarchy validation</p>
                            </div>
                        </div>

                        <div class="content">
                            <!-- Quick Stats -->
                            <div class="stats-grid">
                                <div class="stat-card blue">
                                    <div class="stat-value">
                                        <xsl:value-of select="count(//*)"/>
                                    </div>
                                    <div class="stat-label">Total Elements</div>
                                </div>
                                <div class="stat-card green">
                                    <div class="stat-value">
                                        <xsl:value-of select="count(Funds/Fund)"/>
                                    </div>
                                    <div class="stat-label">Funds</div>
                                </div>
                                <div class="stat-card purple">
                                    <div class="stat-value">
                                        <xsl:value-of select="count(//Position)"/>
                                    </div>
                                    <div class="stat-label">Positions</div>
                                </div>
                                <div class="stat-card orange">
                                    <div class="stat-value">
                                        <xsl:value-of select="count(//Asset)"/>
                                    </div>
                                    <div class="stat-label">Assets</div>
                                </div>
                            </div>

                            <!-- Document Structure Tree -->
                            <div class="section-card">
                                <h2 class="section-title">
                                    <div class="icon-box">📁</div>
                                    Document Structure Tree
                                </h2>

                                <div class="code-block">
                                    <div class="code-text">
                                        <div class="code-title">FundsXML4</div>
                                        <div class="code-indent-1">
                                            <div class="tree-element">├─
                                                <span class="code-element">ControlData</span>
                                            </div>
                                            <div class="code-indent-2 code-comment">
                                                <div>├─ UniqueDocumentID:
                                                    <span class="code-value">
                                                        <xsl:value-of select="ControlData/UniqueDocumentID"/>
                                                    </span>
                                                </div>
                                                <div>├─ DocumentGenerated:
                                                    <span class="code-value">
                                                        <xsl:value-of select="ControlData/DocumentGenerated"/>
                                                    </span>
                                                </div>
                                                <div>├─ ContentDate:
                                                    <span class="code-value">
                                                        <xsl:value-of select="ControlData/ContentDate"/>
                                                    </span>
                                                </div>
                                                <div>├─ DataSupplier</div>
                                                <div>├─ DataOperation:
                                                    <span class="code-value">
                                                        <xsl:value-of select="ControlData/DataOperation"/>
                                                    </span>
                                                </div>
                                                <div>└─ Language:
                                                    <span class="code-value">
                                                        <xsl:value-of select="ControlData/Language"/>
                                                    </span>
                                                </div>
                                            </div>
                                            <div class="tree-element">├─
                                                <span class="code-element">Funds</span>
                                            </div>
                                            <div class="code-indent-2">
                                                <xsl:for-each select="Funds/Fund">
                                                    <div class="tree-element" style="margin-bottom: 0.5rem;">
                                                        <xsl:choose>
                                                            <xsl:when test="position() = last()">└─</xsl:when>
                                                            <xsl:otherwise>├─</xsl:otherwise>
                                                        </xsl:choose>
                                                        <span style="color: #10B981;">Fund[<xsl:value-of
                                                                select="position()"/>]
                                                        </span>
                                                        <span class="code-comment" style="margin-left: 0.5rem;">(<xsl:value-of
                                                                select="Names/OfficialName"/>)
                                                        </span>
                                                    </div>
                                                </xsl:for-each>
                                            </div>
                                            <div class="tree-element">├─
                                                <span class="code-element">AssetMaster</span>
                                                <span class="code-comment"> (<xsl:value-of select="count(AssetMaster/Asset)"/> assets)</span>
                                            </div>
                                            <div class="tree-element">└─
                                                <span class="code-element">PortfolioMaster</span>
                                                <span class="code-comment"> (<xsl:value-of select="count(PortfolioMaster/Portfolio)"/> portfolios)</span>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <!-- Element Distribution -->
                            <div class="section-card">
                                <h2 class="section-title">
                                    <div class="icon-box">📊</div>
                                    Element Distribution Analysis
                                </h2>

                                <div style="display: grid; gap: 1rem;">
                                    <xsl:call-template name="element-count-bar">
                                        <xsl:with-param name="element-name">Funds</xsl:with-param>
                                        <xsl:with-param name="count" select="count(Funds/Fund)"/>
                                        <xsl:with-param name="max-count" select="count(//*)"/>
                                        <xsl:with-param name="color">blue</xsl:with-param>
                                    </xsl:call-template>

                                    <xsl:call-template name="element-count-bar">
                                        <xsl:with-param name="element-name">Positions</xsl:with-param>
                                        <xsl:with-param name="count" select="count(//Position)"/>
                                        <xsl:with-param name="max-count" select="count(//*)"/>
                                        <xsl:with-param name="color">green</xsl:with-param>
                                    </xsl:call-template>

                                    <xsl:call-template name="element-count-bar">
                                        <xsl:with-param name="element-name">Assets</xsl:with-param>
                                        <xsl:with-param name="count" select="count(//Asset)"/>
                                        <xsl:with-param name="max-count" select="count(//*)"/>
                                        <xsl:with-param name="color">purple</xsl:with-param>
                                    </xsl:call-template>

                                    <xsl:call-template name="element-count-bar">
                                        <xsl:with-param name="element-name">Portfolios</xsl:with-param>
                                        <xsl:with-param name="count" select="count(//Portfolio)"/>
                                        <xsl:with-param name="max-count" select="count(//*)"/>
                                        <xsl:with-param name="color">orange</xsl:with-param>
                                    </xsl:call-template>
                                </div>
                            </div>

                            <!-- Attribute Analysis -->
                            <div class="section-card">
                                <h2 class="section-title">
                                    <div class="icon-box">🔍</div>
                                    Attribute Analysis
                                </h2>

                                <div class="analysis-grid">
                                    <!-- Currency Analysis -->
                                    <div class="analysis-box blue">
                                        <h3 class="analysis-title">Currency Usage</h3>
                                        <div class="analysis-content">
                                            <xsl:for-each select="//@ccy[generate-id() = generate-id(key('currencies', .)[1])]">
                                                <xsl:sort select="."/>
                                                <div class="analysis-item">
                                                    <code class="analysis-code">
                                                        <xsl:value-of select="."/>
                                                    </code>
                                                    <span class="analysis-count">
                                                        <xsl:value-of select="count(key('currencies', .))"/>
                                                    </span>
                                                </div>
                                            </xsl:for-each>
                                        </div>
                                    </div>

                                    <!-- FreeType Attributes -->
                                    <div class="analysis-box green">
                                        <h3 class="analysis-title">FreeType Attributes</h3>
                                        <div class="analysis-content">
                                            <xsl:for-each
                                                    select="//OtherID/@FreeType[generate-id() = generate-id(key('freetypes', .)[1])]">
                                                <div class="analysis-code">
                                                    <xsl:value-of select="."/>
                                                </div>
                                            </xsl:for-each>
                                        </div>
                                    </div>

                                    <!-- mulDiv Attributes -->
                                    <div class="analysis-box purple">
                                        <h3 class="analysis-title">FX Rate Directions</h3>
                                        <div class="analysis-content">
                                            <xsl:for-each
                                                    select="//FXRate/@mulDiv[generate-id() = generate-id(key('muldivs', .)[1])]">
                                                <div class="analysis-item">
                                                    <code class="analysis-code">
                                                        <xsl:value-of select="."/>
                                                    </code>
                                                    <span class="analysis-count">
                                                        <xsl:value-of select="count(key('muldivs', .))"/>
                                                    </span>
                                                </div>
                                            </xsl:for-each>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <!-- Footer -->
                        <div class="footer">
                            <p>Generated with embedded CSS • XML Structure Analysis Report</p>
                        </div>
                    </div>
                </div>
            </body>
        </html>
    </xsl:template>

    <!-- Keys for grouping -->
    <xsl:key name="currencies" match="@ccy" use="."/>
    <xsl:key name="freetypes" match="@FreeType" use="."/>
    <xsl:key name="muldivs" match="@mulDiv" use="."/>

    <!-- Template for element count bars -->
    <xsl:template name="element-count-bar">
        <xsl:param name="element-name"/>
        <xsl:param name="count"/>
        <xsl:param name="max-count"/>
        <xsl:param name="color"/>

        <div class="progress-container">
            <div class="progress-header">
                <span class="progress-label">
                    <xsl:value-of select="$element-name"/>
                </span>
                <span class="progress-value">
                    <xsl:value-of select="$count"/>
                </span>
            </div>
            <div class="progress-bar">
                <div class="progress-fill {$color}" style="width: {($count div $max-count) * 100}%"></div>
            </div>
        </div>
    </xsl:template>

    <!-- Template for structure checks -->
    <xsl:template name="structure-check">
        <xsl:param name="check-name"/>
        <xsl:param name="condition"/>
        <xsl:param name="description"/>

        <div class="check-item">
            <div class="check-info">
                <div class="check-name">
                    <xsl:value-of select="$check-name"/>
                </div>
                <div class="check-description">
                    <xsl:value-of select="$description"/>
                </div>
            </div>
            <div>
                <xsl:choose>
                    <xsl:when test="$condition">
                        <div class="check-status pass">
                            <span class="check-icon">✓</span>
                            <span>Pass</span>
                        </div>
                    </xsl:when>
                    <xsl:otherwise>
                        <div class="check-status fail">
                            <span class="check-icon">✗</span>
                            <span>Fail</span>
                        </div>
                    </xsl:otherwise>
                </xsl:choose>
            </div>
        </div>
    </xsl:template>

</xsl:stylesheet>