<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
				xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
				xmlns:fo="http://www.w3.org/1999/XSL/Format">

	<xsl:output method="xml" indent="yes" encoding="UTF-8"/>
	<xsl:strip-space elements="*"/>

	<!-- Definiert einen Schlüssel, um Asset-Stammdaten schnell über ihre ID zu finden. -->
	<xsl:key name="asset-by-id" match="Asset" use="UniqueID"/>

	<!-- Haupt-Template: Definiert das Seitenlayout und startet den Prozess -->
	<xsl:template match="/FundsXML4">
		<fo:root>
			<!-- 1. Layout-Definition für eine A4-Seite mit Rändern -->
			<fo:layout-master-set>
				<fo:simple-page-master master-name="factsheet-page"
									   page-height="29.7cm"
									   page-width="21cm"
									   margin-top="2cm"
									   margin-bottom="2cm"
									   margin-left="2.5cm"
									   margin-right="2.5cm">
					<fo:region-body margin-top="1cm"/>
				</fo:simple-page-master>
			</fo:layout-master-set>

			<!-- 2. Seiten-Sequenz: Hier fließt der Inhalt hinein -->
			<fo:page-sequence master-reference="factsheet-page">
				<fo:flow flow-name="xsl-region-body" font-family="Helvetica, Arial, sans-serif" font-size="10pt">
					<!-- Startet die Verarbeitung beim <Fund>-Element -->
					<xsl:apply-templates select="Funds/Fund"/>
				</fo:flow>
			</fo:page-sequence>
		</fo:root>
	</xsl:template>

	<!-- Template für die Haupt-Fondsinformationen -->
	<xsl:template match="Fund">
		<!-- Titel -->
		<fo:block font-size="22pt" font-weight="bold" text-align="center" color="#004a99" space-after="5mm">
			Factsheet:
			<xsl:value-of select="Names/OfficialName"/>
		</fo:block>
		<!-- Stichtag -->
		<fo:block font-size="10pt" font-style="italic" text-align="center" color="#6c757d" space-after="10mm">
			Daten per Stichtag:
			<xsl:value-of select="FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate"/>
		</fo:block>

		<!-- Abschnitt: Fondsübersicht -->
		<fo:block font-size="16pt" font-weight="bold" color="#004a99" space-before="8mm" space-after="4mm"
				  border-bottom="1pt solid #dee2e6" padding-bottom="2mm">
			Fondsübersicht
		</fo:block>
		<!-- Zwei-Spalten-Tabelle für die Übersicht -->
		<fo:table table-layout="fixed" width="100%" space-after="5mm">
			<fo:table-column column-width="50%"/>
			<fo:table-column column-width="50%"/>
			<fo:table-body>
				<fo:table-row>
					<fo:table-cell padding-right="5mm">
						<fo:block-container border-left="3pt solid #0056b3" padding="5mm" background-color="#f8f9fa">
							<fo:block>
								<strong>Fondsname:</strong>
								<xsl:value-of select="Names/OfficialName"/>
							</fo:block>
							<fo:block>
								<strong>LEI:</strong>
								<xsl:value-of select="Identifiers/LEI"/>
							</fo:block>
							<fo:block>
								<strong>Währung:</strong>
								<xsl:value-of select="Currency"/>
							</fo:block>
							<fo:block>
								<strong>Auflagedatum:</strong>
								<xsl:value-of select="FundStaticData/InceptionDate"/>
							</fo:block>
							<fo:block>
								<strong>Rechtsform:</strong>
								<xsl:value-of select="FundStaticData/ListedLegalStructure"/>
							</fo:block>
						</fo:block-container>
					</fo:table-cell>
					<fo:table-cell padding-left="5mm">
						<fo:block-container border-left="3pt solid #0056b3" padding="5mm" background-color="#f8f9fa">
							<fo:block>
								<strong>Fondsvermögen:</strong>
								<xsl:value-of
										select="format-number(FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount, '#,##0.00')"/>
								<xsl:text> </xsl:text>
								<xsl:value-of
										select="FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount/@ccy"/>
							</fo:block>
							<fo:block>
								<strong>Datenlieferant:</strong>
								<xsl:value-of select="DataSupplier/Name"/>
							</fo:block>
							<fo:block>
								<strong>Kontakt:</strong>
								<xsl:value-of select="DataSupplier/Contact/Email"/>
							</fo:block>
						</fo:block-container>
					</fo:table-cell>
				</fo:table-row>
			</fo:table-body>
		</fo:table>

		<!-- Abschnitt: Wichtige Kennzahlen -->
		<fo:block font-size="16pt" font-weight="bold" color="#004a99" space-before="8mm" space-after="4mm"
				  border-bottom="1pt solid #dee2e6" padding-bottom="2mm">
			Wichtige Kennzahlen
		</fo:block>
		<fo:table table-layout="fixed" width="100%" border-collapse="collapse" border="0.5pt solid #e9ecef"
				  space-before="5mm">
			<fo:table-column column-width="70%"/>
			<fo:table-column column-width="30%"/>
			<fo:table-header background-color="#f1f3f5">
				<fo:table-row>
					<fo:table-cell border="0.5pt solid #e9ecef" padding="2mm">
						<fo:block font-weight="bold">Kennzahl</fo:block>
					</fo:table-cell>
					<fo:table-cell border="0.5pt solid #e9ecef" padding="2mm">
						<fo:block font-weight="bold" text-align="right">Wert</fo:block>
					</fo:table-cell>
				</fo:table-row>
			</fo:table-header>
			<fo:table-body>
				<xsl:for-each select="FundDynamicData/Portfolios/Portfolio/RiskCodes/RiskCode">
					<fo:table-row>
						<fo:table-cell border="0.5pt solid #e9ecef" padding="2mm">
							<fo:block>
								<xsl:value-of select="ListedCode | UnlistedCode"/>
							</fo:block>
						</fo:table-cell>
						<fo:table-cell border="0.5pt solid #e9ecef" padding="2mm">
							<fo:block text-align="right">
								<xsl:value-of select="format-number(Value, '0.0000')"/>
							</fo:block>
						</fo:table-cell>
					</fo:table-row>
				</xsl:for-each>
			</fo:table-body>
		</fo:table>

		<!-- Abschnitt: Anteilsklassen -->
		<fo:block font-size="16pt" font-weight="bold" color="#004a99" space-before="8mm" space-after="4mm"
				  border-bottom="1pt solid #dee2e6" padding-bottom="2mm">
			Anteilsklassen
		</fo:block>
		<fo:table table-layout="fixed" width="100%" border-collapse="collapse" border="0.5pt solid #e9ecef"
				  space-before="5mm">
			<fo:table-column column-width="25%"/>
			<fo:table-column column-width="15%"/>
			<fo:table-column column-width="10%"/>
			<fo:table-column column-width="15%"/>
			<fo:table-column column-width="15%"/>
			<fo:table-column column-width="20%"/>
			<fo:table-header background-color="#f1f3f5">
				<fo:table-row>
					<fo:table-cell border="0.5pt solid #e9ecef" padding="2mm">
						<fo:block font-weight="bold">Name</fo:block>
					</fo:table-cell>
					<fo:table-cell border="0.5pt solid #e9ecef" padding="2mm">
						<fo:block font-weight="bold">ISIN</fo:block>
					</fo:table-cell>
					<fo:table-cell border="0.5pt solid #e9ecef" padding="2mm">
						<fo:block font-weight="bold">Währung</fo:block>
					</fo:table-cell>
					<fo:table-cell border="0.5pt solid #e9ecef" padding="2mm">
						<fo:block font-weight="bold" text-align="right">NAV</fo:block>
					</fo:table-cell>
					<fo:table-cell border="0.5pt solid #e9ecef" padding="2mm">
						<fo:block font-weight="bold" text-align="right">Anteile</fo:block>
					</fo:table-cell>
					<fo:table-cell border="0.5pt solid #e9ecef" padding="2mm">
						<fo:block font-weight="bold" text-align="right">Volumen</fo:block>
					</fo:table-cell>
				</fo:table-row>
			</fo:table-header>
			<fo:table-body>
				<xsl:for-each select="SingleFund/ShareClasses/ShareClass">
					<fo:table-row>
						<fo:table-cell border="0.5pt solid #e9ecef" padding="2mm">
							<fo:block>
								<xsl:value-of select="Names/OfficialName"/>
							</fo:block>
						</fo:table-cell>
						<fo:table-cell border="0.5pt solid #e9ecef" padding="2mm">
							<fo:block>
								<xsl:value-of select="Identifiers/ISIN"/>
							</fo:block>
						</fo:table-cell>
						<fo:table-cell border="0.5pt solid #e9ecef" padding="2mm">
							<fo:block>
								<xsl:value-of select="Currency"/>
							</fo:block>
						</fo:table-cell>
						<fo:table-cell border="0.5pt solid #e9ecef" padding="2mm">
							<fo:block text-align="right">
								<xsl:value-of select="format-number(Prices/Price/NavPrice, '#,##0.00')"/>
							</fo:block>
						</fo:table-cell>
						<fo:table-cell border="0.5pt solid #e9ecef" padding="2mm">
							<fo:block text-align="right">
								<xsl:value-of
										select="format-number(TotalAssetValues/TotalAssetValue/SharesOutstanding, '#,##0.000')"/>
							</fo:block>
						</fo:table-cell>
						<fo:table-cell border="0.5pt solid #e9ecef" padding="2mm">
							<fo:block text-align="right">
								<xsl:value-of
										select="format-number(TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount, '#,##0.00')"/>
								<xsl:text> </xsl:text>
								<xsl:value-of select="TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount/@ccy"/>
							</fo:block>
						</fo:table-cell>
					</fo:table-row>
				</xsl:for-each>
			</fo:table-body>
		</fo:table>

		<!-- Abschnitt: Top 10 Positionen -->
		<fo:block font-size="16pt" font-weight="bold" color="#004a99" space-before="8mm" space-after="4mm"
				  border-bottom="1pt solid #dee2e6" padding-bottom="2mm" keep-with-next.within-page="always">
			Top 10 Positionen
		</fo:block>
		<fo:table table-layout="fixed" width="100%" border-collapse="collapse" border="0.5pt solid #e9ecef"
				  space-before="5mm">
			<fo:table-column column-width="35%"/>
			<fo:table-column column-width="17%"/>
			<fo:table-column column-width="13%"/>
			<fo:table-column column-width="20%"/>
			<fo:table-column column-width="15%"/>
			<fo:table-header background-color="#f1f3f5">
				<fo:table-row>
					<fo:table-cell border="0.5pt solid #e9ecef" padding="2mm">
						<fo:block font-weight="bold">Titel</fo:block>
					</fo:table-cell>
					<fo:table-cell border="0.5pt solid #e9ecef" padding="2mm">
						<fo:block font-weight="bold">ISIN</fo:block>
					</fo:table-cell>
					<fo:table-cell border="0.5pt solid #e9ecef" padding="2mm">
						<fo:block font-weight="bold">Typ</fo:block>
					</fo:table-cell>
					<fo:table-cell border="0.5pt solid #e9ecef" padding="2mm">
						<fo:block font-weight="bold" text-align="right">Wert</fo:block>
					</fo:table-cell>
					<fo:table-cell border="0.5pt solid #e9ecef" padding="2mm">
						<fo:block font-weight="bold" text-align="right">Anteil</fo:block>
					</fo:table-cell>
				</fo:table-row>
			</fo:table-header>
			<fo:table-body>
				<xsl:for-each select="FundDynamicData/Portfolios/Portfolio/Positions/Position">
					<!-- Sortiert nach prozentualem Anteil, absteigend -->
					<xsl:sort select="TotalPercentage" data-type="number" order="descending"/>
					<!-- Limitiert die Ausgabe auf die ersten 10 Positionen -->
					<xsl:if test="position() &lt;= 10">
						<fo:table-row>
							<fo:table-cell border="0.5pt solid #e9ecef" padding="2mm">
								<fo:block>
									<xsl:value-of select="key('asset-by-id', UniqueID)/Name"/>
								</fo:block>
							</fo:table-cell>
							<fo:table-cell border="0.5pt solid #e9ecef" padding="2mm">
								<fo:block>
									<xsl:value-of select="key('asset-by-id', UniqueID)/Identifiers/ISIN"/>
								</fo:block>
							</fo:table-cell>
							<fo:table-cell border="0.5pt solid #e9ecef" padding="2mm">
								<fo:block>
									<xsl:value-of select="key('asset-by-id', UniqueID)/AssetType"/>
								</fo:block>
							</fo:table-cell>
							<fo:table-cell border="0.5pt solid #e9ecef" padding="2mm">
								<fo:block text-align="right">
									<xsl:value-of select="format-number(TotalValue/Amount, '#,##0.00')"/>
									<xsl:text> </xsl:text>
									<xsl:value-of select="TotalValue/Amount/@ccy"/>
								</fo:block>
							</fo:table-cell>
							<fo:table-cell border="0.5pt solid #e9ecef" padding="2mm">
								<fo:block text-align="right">
									<xsl:value-of select="format-number(TotalPercentage, '0.00')"/>%
								</fo:block>
							</fo:table-cell>
						</fo:table-row>
					</xsl:if>
				</xsl:for-each>
			</fo:table-body>
		</fo:table>
	</xsl:template>

	<!-- Hilfs-Template, um das strong-Tag in fo:block umzuwandeln -->
	<xsl:template match="strong">
		<fo:inline font-weight="bold" color="#555" padding-right="5mm">
			<xsl:apply-templates/>
		</fo:inline>
	</xsl:template>

</xsl:stylesheet>