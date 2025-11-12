<?xml version="1.0" encoding="UTF-8"?>
<!--
  ~ FreeXMLToolkit - Universal Toolkit for XML
  ~ Copyright (c) Karl Kauc 2025.
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
  ~-->

<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="html" indent="yes" version="5.0"/>

    <xsl:template match="/">
        <html>
            <head>
                <title>FundsXML Consistency Check</title>
                <style>
                    /* Reset und Basis-Styles */
                    * {
                        margin: 0;
                        padding: 0;
                        box-sizing: border-box;
                    }
                    
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
                        background: #0F172A;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        min-height: 100vh;
                        padding: 1rem;
                        line-height: 1.5;
                    }
                    
                    /* Fund Container */
                    .fund-container {
                        display: flex;
                        flex-direction: column;
                        gap: 2rem;
                        width: 100%;
                        max-width: 1200px;
                    }
                    
                    /* Fund Card */
                    .fund-card {
                        max-width: 42rem;
                        width: 100%;
                        background: rgba(255, 255, 255, 0.1);
                        backdrop-filter: blur(12px);
                        border-radius: 0.75rem;
                        box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
                        padding: 2rem;
                        color: white;
                        transition: transform 0.3s ease;
                        margin: 0 auto;
                    }
                    
                    .fund-card:hover {
                        transform: scale(1.05);
                    }
                    
                    /* Title */
                    .fund-title {
                        font-size: 1.875rem;
                        font-weight: bold;
                        margin-bottom: 0.5rem;
                    }
                    
                    .fund-subtitle {
                        color: #CBD5E1;
                        margin-bottom: 1.5rem;
                    }
                    
                    /* Values Grid */
                    .values-grid {
                        display: grid;
                        grid-template-columns: 1fr;
                        gap: 1.5rem;
                        margin-bottom: 1.5rem;
                    }
                    
                    @media (min-width: 768px) {
                        .values-grid {
                            grid-template-columns: 1fr 1fr;
                        }
                    }
                    
                    /* Value Box */
                    .value-box {
                        background: rgba(255, 255, 255, 0.05);
                        padding: 1.5rem;
                        border-radius: 0.5rem;
                    }
                    
                    .value-label {
                        font-size: 0.875rem;
                        font-weight: 500;
                        color: #9CA3AF;
                        margin-bottom: 0.5rem;
                    }
                    
                    .value-amount {
                        font-size: 1.5rem;
                        font-weight: 600;
                    }
                    
                    .currency {
                        font-size: 1rem;
                        font-weight: 500;
                        color: #9CA3AF;
                        margin-left: 0.25rem;
                    }
                    
                    /* Difference Section */
                    .difference-section {
                        margin-top: 1.5rem;
                        padding-top: 1.5rem;
                        border-top: 1px solid rgba(255, 255, 255, 0.1);
                    }
                    
                    .difference-container {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                    }
                    
                    .difference-box {
                        flex: 1;
                    }
                    
                    .difference-label {
                        font-size: 0.875rem;
                        font-weight: 500;
                        color: #9CA3AF;
                    }
                    
                    .difference-value {
                        font-size: 1.5rem;
                        font-weight: 600;
                    }
                    
                    .difference-positive {
                        color: #86EFAC;
                    }
                    
                    .difference-negative {
                        color: #FCA5A5;
                    }
                    
                    /* Status Badge */
                    .status-badge {
                        display: inline-flex;
                        align-items: center;
                        padding: 0.5rem 1rem;
                        font-size: 0.875rem;
                        font-weight: 500;
                        border-radius: 9999px;
                    }
                    
                    .status-consistent {
                        background: rgba(34, 197, 94, 0.2);
                        color: #86EFAC;
                    }
                    
                    .status-mismatch {
                        background: rgba(239, 68, 68, 0.2);
                        color: #FCA5A5;
                    }
                    
                    .status-icon {
                        width: 1rem;
                        height: 1rem;
                        margin-right: 0.5rem;
                        flex-shrink: 0;
                    }
                    
                    /* Extended Info Section */
                    .extended-info {
                        margin-top: 2rem;
                        padding-top: 1.5rem;
                        border-top: 1px solid rgba(255, 255, 255, 0.1);
                    }
                    
                    .info-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                        gap: 1rem;
                        margin-top: 1rem;
                    }
                    
                    .info-item {
                        background: rgba(255, 255, 255, 0.05);
                        padding: 1rem;
                        border-radius: 0.5rem;
                    }
                    
                    .info-item-label {
                        font-size: 0.75rem;
                        text-transform: uppercase;
                        color: #9CA3AF;
                        margin-bottom: 0.25rem;
                    }
                    
                    .info-item-value {
                        font-size: 1rem;
                        font-weight: 600;
                        color: white;
                    }
                    
                    /* Header for multiple funds */
                    .page-header {
                        text-align: center;
                        margin-bottom: 3rem;
                    }
                    
                    .page-title {
                        font-size: 2.5rem;
                        font-weight: bold;
                        color: white;
                        margin-bottom: 0.5rem;
                    }
                    
                    .page-subtitle {
                        color: #9CA3AF;
                        font-size: 1.125rem;
                    }
                    
                    /* Percentage Display */
                    .percentage-display {
                        display: inline-block;
                        margin-left: 1rem;
                        padding: 0.25rem 0.5rem;
                        background: rgba(255, 255, 255, 0.1);
                        border-radius: 0.25rem;
                        font-size: 0.875rem;
                    }
                    
                    /* Animation */
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
                    
                    .fund-card {
                        animation: fadeIn 0.6s ease-out;
                    }
                    
                    /* Responsive adjustments */
                    @media (max-width: 640px) {
                        .fund-card {
                            padding: 1.5rem;
                        }
                        
                        .fund-title {
                            font-size: 1.5rem;
                        }
                        
                        .value-amount,
                        .difference-value {
                            font-size: 1.25rem;
                        }
                        
                        .difference-container {
                            flex-direction: column;
                            gap: 1rem;
                        }
                    }
                </style>
            </head>
            <body>
                <div class="fund-container">
                    <xsl:if test="count(/FundsXML4/Funds/Fund) &gt; 1">
                        <div class="page-header">
                            <h1 class="page-title">FundsXML Consistency Check</h1>
                            <p class="page-subtitle">Validating <xsl:value-of select="count(/FundsXML4/Funds/Fund)"/> funds</p>
                        </div>
                    </xsl:if>
                    <xsl:apply-templates select="/FundsXML4/Funds/Fund"/>
                </div>
            </body>
        </html>
    </xsl:template>

    <xsl:template match="Fund">
        <xsl:variable name="totalNetAssetValue" select="normalize-space(FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount)"/>
        <xsl:variable name="sumOfPositions" select="sum(FundDynamicData/Portfolios/Portfolio/Positions/Position/TotalValue/Amount)"/>
        <xsl:variable name="difference" select="$totalNetAssetValue - $sumOfPositions"/>
        <xsl:variable name="percentageDiff">
            <xsl:choose>
                <xsl:when test="$totalNetAssetValue != 0">
                    <xsl:value-of select="($difference div $totalNetAssetValue) * 100"/>
                </xsl:when>
                <xsl:otherwise>0</xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        
        <div class="fund-card">
            <h1 class="fund-title">
                <xsl:value-of select="Names/OfficialName"/>
            </h1>
            <p class="fund-subtitle">
                Consistency Check for <xsl:value-of select="FundDynamicData/Portfolios/Portfolio/NavDate"/>
            </p>

            <div class="values-grid">
                <!-- Reported NAV -->
                <div class="value-box">
                    <h2 class="value-label">Reported Total Net Asset Value</h2>
                    <p class="value-amount">
                        <xsl:value-of select="format-number(number($totalNetAssetValue), '#,##0.00')"/>
                        <span class="currency"><xsl:value-of select="Currency"/></span>
                    </p>
                </div>

                <!-- Calculated Sum -->
                <div class="value-box">
                    <h2 class="value-label">Calculated Sum of Positions</h2>
                    <p class="value-amount">
                        <xsl:value-of select="format-number($sumOfPositions, '#,##0.00')"/>
                        <span class="currency"><xsl:value-of select="Currency"/></span>
                    </p>
                </div>
            </div>

            <!-- Difference & Status -->
            <div class="difference-section">
                <div class="difference-container">
                    <div class="difference-box">
                        <h2 class="difference-label">Difference</h2>
                        <p>
                            <xsl:attribute name="class">
                                <xsl:choose>
                                    <xsl:when test="round($difference * 100) != 0">
                                        <xsl:text>difference-value difference-negative</xsl:text>
                                    </xsl:when>
                                    <xsl:otherwise>
                                        <xsl:text>difference-value difference-positive</xsl:text>
                                    </xsl:otherwise>
                                </xsl:choose>
                            </xsl:attribute>
                            <xsl:value-of select="format-number($difference, '#,##0.00')"/>
                            <span class="percentage-display">
                                <xsl:value-of select="format-number($percentageDiff, '0.00')"/>%
                            </span>
                        </p>
                    </div>
                    <div>
                        <xsl:choose>
                            <xsl:when test="round($difference * 100) != 0">
                                <span class="status-badge status-mismatch">
                                    <svg class="status-icon" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                                    </svg>
                                    Mismatch
                                </span>
                            </xsl:when>
                            <xsl:otherwise>
                                <span class="status-badge status-consistent">
                                    <svg class="status-icon" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                                    </svg>
                                    Consistent
                                </span>
                            </xsl:otherwise>
                        </xsl:choose>
                    </div>
                </div>
            </div>
            
            <!-- Extended Information -->
            <div class="extended-info">
                <h3 class="value-label">Additional Information</h3>
                <div class="info-grid">
                    <div class="info-item">
                        <div class="info-item-label">Fund Currency</div>
                        <div class="info-item-value"><xsl:value-of select="Currency"/></div>
                    </div>
                    <div class="info-item">
                        <div class="info-item-label">Total Positions</div>
                        <div class="info-item-value">
                            <xsl:value-of select="count(FundDynamicData/Portfolios/Portfolio/Positions/Position)"/>
                        </div>
                    </div>
                    <div class="info-item">
                        <div class="info-item-label">NAV Date</div>
                        <div class="info-item-value">
                            <xsl:value-of select="FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate"/>
                        </div>
                    </div>
                    <xsl:if test="Identifiers/ISIN">
                        <div class="info-item">
                            <div class="info-item-label">ISIN</div>
                            <div class="info-item-value">
                                <xsl:value-of select="Identifiers/ISIN"/>
                            </div>
                        </div>
                    </xsl:if>
                </div>
            </div>
        </div>
    </xsl:template>

</xsl:stylesheet>