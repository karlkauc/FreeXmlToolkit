<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="html" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <!-- Definiert einen Schlüssel, um Asset-Stammdaten schnell über ihre ID zu finden. -->
    <!-- Dies ist viel effizienter als bei jeder Position den ganzen Baum zu durchsuchen. -->
    <xsl:key name="asset-by-id" match="Asset" use="UniqueID"/>

    <!-- Haupt-Template, das die HTML-Struktur erstellt -->
    <xsl:template match="/FundsXML4">
        <html>
            <head>
                <title>FundsXML Factsheet</title>
                <style>
                    body {
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                    line-height: 1.6;
                    color: #333;
                    background-color: #f8f9fa;
                    margin: 20px;
                    }
                    .container {
                    max-width: 960px;
                    margin: auto;
                    background: #ffffff;
                    padding: 25px;
                    border-radius: 8px;
                    box-shadow: 0 4px 12px rgba(0,0,0,0.08);
                    }
                    h1, h2 {
                    color: #004a99;
                    border-bottom: 2px solid #dee2e6;
                    padding-bottom: 8px;
                    margin-top: 30px;
                    margin-bottom: 15px;
                    }
                    h1 {
                    text-align: center;
                    border-bottom: none;
                    font-size: 2.2em;
                    margin-bottom: 5px;
                    }
                    table {
                    width: 100%;
                    border-collapse: collapse;
                    margin-top: 20px;
                    }
                    th, td {
                    padding: 10px 14px;
                    border: 1px solid #e9ecef;
                    text-align: left;
                    vertical-align: top;
                    }
                    th {
                    background-color: #f1f3f5;
                    font-weight: 600;
                    color: #495057;
                    }
                    tr:nth-child(even) {
                    background-color: #f8f9fa;
                    }
                    .info-grid {
                    display: grid;
                    grid-template-columns: 1fr 1fr;
                    gap: 25px;
                    margin-top: 20px;
                    }
                    .info-block {
                    background: #f8f9fa;
                    padding: 20px;
                    border-left: 4px solid #0056b3;
                    border-radius: 5px;
                    }
                    .info-block p {
                    margin: 8px 0;
                    }
                    .info-block strong {
                    display: inline-block;
                    width: 140px;
                    color: #555;
                    }
                    .date-header {
                    text-align:center;
                    font-style:italic;
                    color: #6c757d;
                    margin-bottom: 25px;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <!-- Startet die Verarbeitung beim <Fund>-Element -->
                    <xsl:apply-templates select="Funds/Fund"/>
                </div>
            </body>
        </html>
    </xsl:template>

    <!-- Template für die Haupt-Fondsinformationen -->
    <xsl:template match="Fund">
        <h1>Factsheet:
            <xsl:value-of select="Names/OfficialName"/>
        </h1>
        <p class="date-header">
            Daten per Stichtag:
            <xsl:value-of select="FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate"/>
        </p>

        <h2>Fondsübersicht</h2>
        <div class="info-grid">
            <div class="info-block">
                <p>
                    <strong>Fondsname:</strong>
                    <xsl:value-of select="Names/OfficialName"/>
                </p>
                <p>
                    <strong>LEI:</strong>
                    <xsl:value-of select="Identifiers/LEI"/>
                </p>
                <p>
                    <strong>Währung:</strong>
                    <xsl:value-of select="Currency"/>
                </p>
                <p>
                    <strong>Auflagedatum:</strong>
                    <xsl:value-of select="FundStaticData/InceptionDate"/>
                </p>
                <p>
                    <strong>Rechtsform:</strong>
                    <xsl:value-of select="FundStaticData/ListedLegalStructure"/>
                </p>
            </div>
            <div class="info-block">
                <p>
                    <strong>Fondsvermögen:</strong>
                    <xsl:value-of
                            select="format-number(FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount, '#,##0.00')"/>
                    <xsl:text> </xsl:text>
                    <xsl:value-of
                            select="FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount/@ccy"/>
                </p>
                <p>
                    <strong>Datenlieferant:</strong>
                    <xsl:value-of select="DataSupplier/Name"/>
                </p>
                <p>
                    <strong>Kontakt:</strong>
                    <xsl:value-of select="DataSupplier/Contact/Email"/>
                </p>
            </div>
        </div>

        <!-- Risikokennzahlen -->
        <h2>Wichtige Kennzahlen</h2>
        <table>
            <tr>
                <th>Kennzahl</th>
                <th>Wert</th>
            </tr>
            <xsl:for-each select="FundDynamicData/Portfolios/Portfolio/RiskCodes/RiskCode">
                <tr>
                    <td>
                        <xsl:value-of select="ListedCode | UnlistedCode"/>
                    </td>
                    <td>
                        <xsl:value-of select="format-number(Value, '0.0000')"/>
                    </td>
                </tr>
            </xsl:for-each>
        </table>

        <!-- Anteilsklassen -->
        <h2>Anteilsklassen</h2>
        <table>
            <tr>
                <th>Name</th>
                <th>ISIN</th>
                <th>Währung</th>
                <th>NAV</th>
                <th>Anteile</th>
                <th>Volumen</th>
            </tr>
            <xsl:for-each select="SingleFund/ShareClasses/ShareClass">
                <tr>
                    <td>
                        <xsl:value-of select="Names/OfficialName"/>
                    </td>
                    <td>
                        <xsl:value-of select="Identifiers/ISIN"/>
                    </td>
                    <td>
                        <xsl:value-of select="Currency"/>
                    </td>
                    <td>
                        <xsl:value-of select="format-number(Prices/Price/NavPrice, '#,##0.00')"/>
                    </td>
                    <td>
                        <xsl:value-of
                                select="format-number(TotalAssetValues/TotalAssetValue/SharesOutstanding, '#,##0.000')"/>
                    </td>
                    <td>
                        <xsl:value-of
                                select="format-number(TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount, '#,##0.00')"/>
                        <xsl:text> </xsl:text>
                        <xsl:value-of select="TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount/@ccy"/>
                    </td>
                </tr>
            </xsl:for-each>
        </table>

        <!-- Top 10 Positionen -->
        <h2>Top 10 Positionen</h2>
        <table>
            <tr>
                <th>Titel</th>
                <th>ISIN</th>
                <th>Typ</th>
                <th>Wert</th>
                <th>Anteil</th>
            </tr>
            <xsl:for-each select="FundDynamicData/Portfolios/Portfolio/Positions/Position">
                <!-- Sortiert nach prozentualem Anteil, absteigend -->
                <xsl:sort select="TotalPercentage" data-type="number" order="descending"/>
                <!-- Limitiert die Ausgabe auf die ersten 10 Positionen -->
                <xsl:if test="position() &lt;= 10">
                    <tr>
                        <!-- Holt den Namen des Assets über den definierten Schlüssel -->
                        <td>
                            <xsl:value-of select="key('asset-by-id', UniqueID)/Name"/>
                        </td>
                        <td>
                            <xsl:value-of select="key('asset-by-id', UniqueID)/Identifiers/ISIN"/>
                        </td>
                        <td>
                            <xsl:value-of select="key('asset-by-id', UniqueID)/AssetType"/>
                        </td>
                        <td>
                            <xsl:value-of select="format-number(TotalValue/Amount, '#,##0.00')"/>
                            <xsl:text> </xsl:text>
                            <xsl:value-of select="TotalValue/Amount/@ccy"/>
                        </td>
                        <td><xsl:value-of select="format-number(TotalPercentage, '0.00')"/>%
                        </td>
                    </tr>
                </xsl:if>
            </xsl:for-each>
        </table>

    </xsl:template>

</xsl:stylesheet>