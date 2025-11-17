<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xs">
    
    <xsl:output method="html" indent="yes" encoding="UTF-8"/>
    
    <!-- Haupttemplate -->
    <xsl:template match="/">
        <html>
            <head>
                <title>FundsXML4 Datenqualitätsprüfung</title>
                <style>
                    body { font-family: Arial, sans-serif; margin: 20px; }
                    h1 { color: #333; }
                    h2 { color: #666; margin-top: 30px; }
                    .fund-info { background: #f0f0f0; padding: 15px; margin: 20px 0; border-radius: 5px; }
                    .check-passed { color: green; font-weight: bold; }
                    .check-failed { color: red; font-weight: bold; }
                    .warning { color: orange; font-weight: bold; }
                    table { border-collapse: collapse; width: 100%; margin: 20px 0; }
                    th, td { border: 1px solid #ddd; padding: 10px; text-align: left; }
                    th { background: #4CAF50; color: white; }
                    .number { text-align: right; font-family: monospace; }
                    .summary { background: #e8f4f8; padding: 15px; border-radius: 5px; margin: 20px 0; }
                </style>
            </head>
            <body>
                <h1>FundsXML4 Datenqualitätsprüfung</h1>
                <p>Prüfdatum: <xsl:value-of select="current-dateTime()"/></p>
                <p>Dokumentdatum: <xsl:value-of select="//ContentDate"/></p>
                
                <xsl:for-each select="//Fund">
                    <xsl:call-template name="check-fund"/>
                </xsl:for-each>
            </body>
        </html>
    </xsl:template>
    
    <!-- Template für Fondsprüfung -->
    <xsl:template name="check-fund">
        <div class="fund-info">
            <h2>Fonds: <xsl:value-of select="Names/OfficialName"/></h2>
            <p>LEI: <xsl:value-of select="Identifiers/LEI"/></p>
            <p>NAV-Datum: <xsl:value-of select="FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate"/></p>
        </div>
        
        <!-- Variablen für Berechnungen -->
        <xsl:variable name="fundTotalNAV" select="FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount"/>
        <xsl:variable name="fundCurrency" select="FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount/@ccy"/>
        
        <div class="summary">
            <h3>1. Prüfung: Summe der ShareClass NAVs vs. Fonds NAV</h3>
            
            <table>
                <tr>
                    <th>Beschreibung</th>
                    <th>Wert (EUR)</th>
                </tr>
                <tr>
                    <td>Fonds Total Net Asset Value</td>
                    <td class="number"><xsl:value-of select="format-number($fundTotalNAV, '#,##0.00')"/></td>
                </tr>
                
                <!-- ShareClass Details -->
                <xsl:for-each select="SingleFund/ShareClasses/ShareClass">
                    <tr>
                        <td>ShareClass <xsl:value-of select="Names/OfficialName"/> (ISIN: <xsl:value-of select="Identifiers/ISIN"/>)</td>
                        <td class="number"><xsl:value-of select="format-number(TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount, '#,##0.00')"/></td>
                    </tr>
                </xsl:for-each>
                
                <!-- Summe berechnen -->
                <xsl:variable name="sumShareClassNAV" select="sum(SingleFund/ShareClasses/ShareClass/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount)"/>
                <tr style="font-weight: bold; border-top: 2px solid #333;">
                    <td>Summe aller ShareClass NAVs</td>
                    <td class="number"><xsl:value-of select="format-number($sumShareClassNAV, '#,##0.00')"/></td>
                </tr>
                <tr>
                    <td>Differenz</td>
                    <td class="number">
                        <xsl:variable name="diff1" select="$fundTotalNAV - $sumShareClassNAV"/>
                        <xsl:value-of select="format-number($diff1, '#,##0.00')"/>
                    </td>
                </tr>
                <tr>
                    <td>Status</td>
                    <td>
                        <xsl:variable name="diff1" select="$fundTotalNAV - $sumShareClassNAV"/>
                        <xsl:choose>
                            <xsl:when test="abs($diff1) &lt; 0.01">
                                <span class="check-passed">✓ PRÜFUNG BESTANDEN</span>
                            </xsl:when>
                            <xsl:when test="abs($diff1) &lt; 1">
                                <span class="warning">⚠ RUNDUNGSDIFFERENZ</span>
                            </xsl:when>
                            <xsl:otherwise>
                                <span class="check-failed">✗ PRÜFUNG FEHLGESCHLAGEN</span>
                            </xsl:otherwise>
                        </xsl:choose>
                    </td>
                </tr>
            </table>
        </div>
        
        <div class="summary">
            <h3>2. Prüfung: ShareClass Price × Shares = NAV</h3>
            
            <table>
                <tr>
                    <th>ShareClass</th>
                    <th>Price (EUR)</th>
                    <th>Shares</th>
                    <th>Price × Shares</th>
                    <th>Reported NAV</th>
                    <th>Differenz</th>
                    <th>Status</th>
                </tr>
                
                <xsl:for-each select="SingleFund/ShareClasses/ShareClass">
                    <xsl:variable name="price" select="Prices/Price/NavPrice"/>
                    <xsl:variable name="shares" select="TotalAssetValues/TotalAssetValue/SharesOutstanding"/>
                    <xsl:variable name="reportedNAV" select="TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount"/>
                    <xsl:variable name="calculatedNAV" select="$price * $shares"/>
                    <xsl:variable name="diff" select="$reportedNAV - $calculatedNAV"/>
                    
                    <tr>
                        <td><xsl:value-of select="Identifiers/ISIN"/></td>
                        <td class="number"><xsl:value-of select="format-number($price, '#,##0.00')"/></td>
                        <td class="number"><xsl:value-of select="format-number($shares, '#,##0')"/></td>
                        <td class="number"><xsl:value-of select="format-number($calculatedNAV, '#,##0.00')"/></td>
                        <td class="number"><xsl:value-of select="format-number($reportedNAV, '#,##0.00')"/></td>
                        <td class="number"><xsl:value-of select="format-number($diff, '#,##0.00')"/></td>
                        <td>
                            <xsl:choose>
                                <xsl:when test="abs($diff) &lt; 0.01">
                                    <span class="check-passed">✓ OK</span>
                                </xsl:when>
                                <xsl:when test="abs($diff) &lt; 10">
                                    <span class="warning">⚠ RUNDUNG</span>
                                </xsl:when>
                                <xsl:otherwise>
                                    <span class="check-failed">✗ FEHLER</span>
                                </xsl:otherwise>
                            </xsl:choose>
                        </td>
                    </tr>
                </xsl:for-each>
            </table>
        </div>
        
        <!-- Zusätzliche Informationen -->
        <div class="summary">
            <h3>Portfolio-Übersicht</h3>
            <p>Anzahl Positionen: <xsl:value-of select="count(FundDynamicData/Portfolios/Portfolio/Positions/Position)"/></p>
            <p>Davon Private Equity: <xsl:value-of select="count(FundDynamicData/Portfolios/Portfolio/Positions/Position[PrivateEquity])"/></p>
            <p>Cash-Position: <xsl:value-of select="format-number(sum(FundDynamicData/Portfolios/Portfolio/Positions/Position[Account]/TotalValue/Amount), '#,##0.00')"/> EUR</p>
            <p>Gebühren: <xsl:value-of select="format-number(sum(FundDynamicData/Portfolios/Portfolio/Positions/Position[Fee]/TotalValue/Amount), '#,##0.00')"/> EUR</p>
            <p>Short Term Bridge Loan: <xsl:value-of select="format-number(FundDynamicData/Portfolios/Portfolio/Positions/Position[UniqueID='ID_00016']/TotalValue/Amount, '#,##0.00')"/> EUR</p>
        </div>
    </xsl:template>
    
</xsl:stylesheet>