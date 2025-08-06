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

<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xslt="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                expand-text="yes"
                exclude-result-prefixes="#all">

    <xsl:output method="html" version="5.0" encoding="UTF-8" indent="yes" omit-xml-declaration="yes"/>
    <xsl:preserve-space elements="*"/>

    <xsl:variable name="renderXMLContent" select="true()"/>

    <xsl:key name="asset-by-id" match="AssetMasterData/Asset" use="UniqueID"/>

    <xsl:template match="/">
        <html lang="en">
            <head>
                <title>Report</title>
                <meta charset="utf-8"/>
                <meta name="viewport" content="width=device-width, initial-scale=1"/>

                <meta http-equiv="X-UA-Compatible" content="IE=edge"/>
                <meta name="viewport" content="width=device-width, user-scalable=no, initial-scale=1, maximum-scale=1"/>
                <meta name="apple-mobile-web-app-capable" content="yes"/>
                <meta name="mobile-web-app-capable" content="yes"/>
                <meta name="apple-mobile-web-app-status-bar-style" content="black-translucent"/>
                <meta name="theme-color" content="#bce4fa"/>
                <meta name="apple-mobile-web-app-status-bar-style" content="black-translucent"/>

                <link rel="stylesheet" href="freeXmlToolkit.css"/>
                <link rel="stylesheet" href="prism.css"/>
                <link rel="stylesheet" href="prism-unescaped-markup.min.css"/>
            </head>
            <body class="bg-gray-100 p-4 sm:p-6 lg:p-8">
                <main id="content" class="max-w-7xl mx-auto bg-white p-6 rounded-lg shadow-lg">
                    <h1 class="text-3xl font-bold mb-4 border-b pb-2">Analyzing File</h1>
                    <table class="w-full mb-6">
                        <tbody>
                            <tr class="border-b">
                                <th class="py-2 pr-4 text-left font-semibold w-1/4">Report Created</th>
                                <td class="py-2">{current-dateTime()}</td>
                            </tr>
                            <tr class="border-b">
                                <th class="py-2 pr-4 text-left font-semibold">Filename:</th>
                                <td>
                                    <xsl:value-of select="tokenize(base-uri(.), '/')[last()]"
                                                  disable-output-escaping="yes"/>
                                </td>
                            </tr>
                            <tr class="border-b">
                                <th class="py-2 pr-4 text-left font-semibold"># Funds:</th>
                                <td>
                                    <xsl:value-of select="count(FundsXML4/Funds/Fund)"/>
                                </td>
                            </tr>
                            <tr class="border-b">
                                <th class="py-2 pr-4 text-left font-semibold"># ShareClasses:</th>
                                <td>
                                    <xsl:if test="count(//SingleFund/ShareClasses/ShareClass) = 0">
                                        <xsl:attribute name="class">bg-red-100 text-red-700 p-1 rounded</xsl:attribute>
                                    </xsl:if>
                                    <xsl:value-of select="count(//SingleFund/ShareClasses/ShareClass)"/>
                                </td>
                            </tr>
                            <tr>
                                <th class="py-2 pr-4 text-left font-semibold"># Asset Master Data:</th>
                                <td>
                                    <xsl:value-of select="count(FundsXML4/AssetMasterData/Asset)"/>
                                </td>
                            </tr>
                        </tbody>
                    </table>
                    <hr class="my-6"/>
                    <div>
                        <h1 class="text-2xl font-bold text-red-600">ERROR LIST</h1>
                        <ul id="listOfErrors" class="list-disc list-inside mt-2 space-y-1"></ul>
                    </div>
                    <hr class="my-6"/>

                    <div>
                        <h1 class="text-2xl font-bold text-yellow-600">WARNING LIST</h1>
                        <ul id="listOfWarnings" class="list-disc list-inside mt-2 space-y-1"></ul>
                    </div>
                    <hr class="my-6"/>

                    <xsl:apply-templates select="FundsXML4/ControlData"/>
                    <hr class="my-6"/>

                    <h1 class="text-2xl font-bold mb-4">FundList</h1>
                    <ol class="list-decimal list-inside space-y-2">
                        <xsl:for-each select="FundsXML4/Funds/Fund">
                            <li>
                                <a href="#{generate-id(.)}" class="text-blue-600 hover:underline">{Names/OfficialName/text()}</a>
                                <ol class="list-disc list-inside ml-6 mt-1 space-y-1">
                                    <xsl:for-each select="SingleFund/ShareClasses/ShareClass">
                                        <li>
                                            <a href="#{Identifiers/ISIN}" class="text-blue-600 hover:underline">{Identifiers/ISIN}</a>
                                        </li>
                                    </xsl:for-each>
                                </ol>
                            </li>
                        </xsl:for-each>
                    </ol>
                    <hr class="my-6"/>

                    <div class="text-xl font-semibold">
                        <a href="#AssetMasterData" class="flex items-center text-blue-600 hover:underline">
                            <svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" fill="currentColor"
                                 class="mr-2" viewBox="0 0 16 16">
                                <path d="M6.354 5.5H4a3 3 0 0 0 0 6h3a3 3 0 0 0 2.83-4H9c-.086 0-.17.01-.25.031A2 2 0 0 1 7 10.5H4a2 2 0 1 1 0-4h1.535c.218-.376.495-.714.82-1z"/>
                                <path d="M9 5.5a3 3 0 0 0-2.83 4h1.098A2 2 0 0 1 9 6.5h3a2 2 0 1 1 0 4h-1.535a4.02 4.02 0 0 1-.82 1H12a3 3 0 1 0 0-6H9z"/>
                            </svg>
                            Asset Master Data
                        </a>
                    </div>

                    <hr class="my-6"/>

                    <xsl:apply-templates select="FundsXML4/Funds"/>
                    <hr class="my-6"/>
                    <xsl:apply-templates select="FundsXML4/AssetMasterData"/>
                    <hr class="my-6"/>

                    <p class="mt-6">
                        <span class="font-semibold">File Comment:</span>
                        <pre class="bg-gray-100 p-4 rounded mt-2 whitespace-pre-wrap">
                            <xsl:value-of select="comment()"/>
                        </pre>
                    </p>

                    <a href="#content" class="fixed bottom-4 right-4 bg-blue-600 text-white p-3 rounded-full shadow-lg hover:bg-blue-700 transition-colors">
                        <span>Back to top</span>
                    </a>
                </main>
                <script src="replaceNodes.js"/>
                <script src="prism.js"/>
                <script src="prism-unescaped-markup.min.js"/>
            </body>
        </html>
    </xsl:template>

    <xsl:template match="ControlData" expand-text="yes">
        <div class="mb-6">
            <h1 class="text-2xl font-bold mb-4">Control Data</h1>
            <table class="w-full border-collapse border border-gray-300">
                <tbody class="divide-y divide-gray-200">
                    <tr class="odd:bg-gray-50">
                        <th class="p-2 text-left font-semibold w-1/4">UniqueDocumentID:</th>
                        <td class="p-2">{UniqueDocumentID}</td>
                    </tr>
                    <tr class="odd:bg-gray-50">
                        <th class="p-2 text-left font-semibold">DocumentGenerated:</th>
                        <td class="p-2">{DocumentGenerated}</td>
                    </tr>
                    <tr class="odd:bg-gray-50">
                        <th class="p-2 text-left font-semibold">Version:</th>
                        <td class="p-2">
                            <xsl:if test="not(Version)">
                                <span id="WARNING_{generate-id(.)}" data-error-message="Missing FundsXML Version"
                                      class="inline-block px-2 py-1 text-xs font-semibold rounded-full bg-yellow-200 text-yellow-800">Missing FundsXML Version
                                </span>
                            </xsl:if>
                            <xsl:value-of select="Version"/>
                        </td>
                    </tr>
                    <tr class="odd:bg-gray-50">
                        <th class="p-2 text-left font-semibold">ContentDate:</th>
                        <td class="p-2">
                            <xslt:value-of select="ContentDate"/>
                        </td>
                    </tr>
                    <tr class="odd:bg-gray-50">
                        <th class="p-2 text-left font-semibold">DataSupplier:</th>
                        <td class="p-2">
                            <xslt:value-of select="DataSupplier/Short"/> |
                            <xslt:value-of select="DataSupplier/Name"/>
                        </td>
                    </tr>
                    <tr class="odd:bg-gray-50">
                        <th class="p-2 text-left font-semibold">Contact:</th>
                        <td class="p-2">
                            <xsl:choose>
                                <xsl:when test="count(DataSupplier/Contact) = 0">
                                    <span class="inline-block px-2 py-1 text-xs font-semibold rounded-full bg-yellow-200 text-yellow-800">Missing</span>
                                </xsl:when>
                                <xsl:otherwise>
                                    <a class="text-blue-600 hover:underline">
                                        <xsl:attribute name="href">mailto:<xsl:value-of
                                                select="DataSupplier/Contact/Email"/>
                                        </xsl:attribute>
                                        <xsl:choose>
                                            <xsl:when test="DataSupplier/Contact/Name">
                                                <xsl:value-of select="DataSupplier/Contact/Name"/>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <xsl:value-of select="DataSupplier/Contact/Email"/>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </a>
                                </xsl:otherwise>
                            </xsl:choose>
                        </td>
                    </tr>
                    <tr class="odd:bg-gray-50">
                        <th class="p-2 text-left font-semibold">Data Operation:</th>
                        <td class="p-2">
                            <xsl:if test="not(DataOperation)">
                                <span id="ERROR_{generate-id(.)}" data-error-message="Data Operation Missing"
                                      class="inline-block px-2 py-1 text-xs font-semibold rounded-full bg-red-200 text-red-800">Missing
                                </span>
                            </xsl:if>
                            <xsl:value-of select="DataOperation"/>
                        </td>
                    </tr>
                    <tr class="odd:bg-gray-50">
                        <th class="p-2 text-left font-semibold">RelatedDocumentIDs:</th>
                        <td class="p-2">
                            <xsl:for-each select="RelatedDocumentIDs">
                                <xsl:value-of select="RelatedDocumentID"/>
                                <br/>
                            </xsl:for-each>
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>
    </xsl:template>

    <xsl:template match="Funds">
        <xsl:for-each select="Fund">
            <xsl:variable name="fundCCY" select="Currency"/>

            <div id="{generate-id(.)}" class="mb-8 p-4 border rounded-lg shadow-sm">
                <div class="bg-gray-100 p-4 rounded-t-lg">
                    <h1 class="text-2xl font-bold">[#{position()}] Fund Name: {Names/OfficialName/text()}</h1>
                </div>

                <div class="p-4">
                    <h2 class="text-xl font-semibold mb-3">Fund Static Data</h2>
                    <table class="w-full text-sm">
                        <tbody class="divide-y divide-gray-200">
                            <tr class="odd:bg-gray-50">
                                <th class="p-2 text-left font-semibold w-1/4">Identifier</th>
                                <td class="p-2">
                                    <xsl:attribute name="class">
                                        <xsl:value-of select="if (count(Identifiers/*) lt 1) then 'p-2 bg-red-100' else 'p-2' "/>
                                    </xsl:attribute>
                                    <xsl:for-each select="Identifiers">
                                        <div class="flex flex-col space-y-1">
                                            <xsl:if test="LEI">
                                                <span class="font-bold">LEI:
                                                    <span class="font-normal"><xsl:value-of select="LEI"/></span>
                                                </span>
                                            </xsl:if>
                                            <xsl:if test="ISIN">
                                                <span>ISIN:
                                                    <span class="font-normal"><xsl:value-of select="ISIN"/></span>
                                                </span>
                                            </xsl:if>
                                            <xsl:for-each select="OtherID">
                                                <span class="text-xs">Other ID
                                                    <span class="font-normal"><xsl:value-of select="concat('[', attribute(), ']: ', .)" /></span>
                                                </span>
                                            </xsl:for-each>
                                        </div>
                                    </xsl:for-each>
                                </td>
                            </tr>
                            <tr class="odd:bg-gray-50">
                                <th class="p-2 text-left font-semibold">Fund CCY</th>
                                <td class="p-2">
                                    <xsl:value-of select="Currency"/>
                                </td>
                            </tr>
                            <tr class="odd:bg-gray-50">
                                <th class="p-2 text-left font-semibold">InceptionDate:</th>
                                <td class="p-2">
                                    <xsl:choose>
                                        <xsl:when test="not(FundStaticData/InceptionDate)">
                                            <span class="inline-block px-2 py-1 text-xs font-semibold rounded-full bg-yellow-200 text-yellow-800">Missing</span>
                                        </xsl:when>
                                        <xsl:otherwise>
                                            <xsl:value-of select="FundStaticData/InceptionDate"/>
                                        </xsl:otherwise>
                                    </xsl:choose>
                                </td>
                            </tr>
                            <tr class="odd:bg-gray-50">
                                <th class="p-2 text-left font-semibold">Fund Manager</th>
                                <td class="p-2">
                                    <div class="flex flex-col space-y-1">
                                        <div class="flex">
                                            <div class="w-1/4 font-medium">Name:</div>
                                            <div class="w-3/4">
                                                <xsl:choose>
                                                    <xsl:when test="not(FundStaticData/PortfolioManagers/PortfolioManager/Name)">
                                                        <span class="inline-block px-2 py-1 text-xs font-semibold rounded-full bg-yellow-200 text-yellow-800">Missing</span>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        [<xsl:value-of select="FundStaticData/PortfolioManagers/PortfolioManager/Name"/>]
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </div>
                                        </div>
                                        <div class="flex">
                                            <div class="w-1/4 font-medium">Start Date:</div>
                                            <div class="w-3/4">
                                                <xsl:choose>
                                                    <xsl:when test="not(FundStaticData/PortfolioManagers/PortfolioManager/StartDate)">
                                                        <span class="inline-block px-2 py-1 text-xs font-semibold rounded-full bg-yellow-200 text-yellow-800">Missing</span>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        [<xsl:value-of select="FundStaticData/PortfolioManagers/PortfolioManager/StartDate"/>]
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </div>
                                        </div>
                                        <div class="flex">
                                            <div class="w-1/4 font-medium">Role:</div>
                                            <div class="w-3/4">
                                                <xsl:choose>
                                                    <xsl:when test="not(FundStaticData/PortfolioManagers/PortfolioManager/Role)">
                                                        <span class="inline-block px-2 py-1 text-xs font-semibold rounded-full bg-yellow-200 text-yellow-800">Missing</span>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        [<xsl:value-of select="FundStaticData/PortfolioManagers/PortfolioManager/Role"/>]
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </div>
                                        </div>
                                    </div>
                                </td>
                            </tr>
                            <tr class="odd:bg-gray-50">
                                <th class="p-2 text-left font-semibold">Fund Legal Type</th>
                                <td class="p-2">
                                    <xsl:value-of select="FundStaticData/ListedLegalStructure"/>
                                </td>
                            </tr>
                        </tbody>
                    </table>

                    <h2 class="text-xl font-semibold mt-6 mb-3">Fund Dynamic Data</h2>
                    <hr/>

                    <h3 class="text-lg font-semibold mt-4 mb-2">Fund Total Asset Value</h3>
                    <xsl:variable name="sumOfShareClassVolume"
                                  select="sum(SingleFund/ShareClasses/ShareClass/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount[@ccy=$fundCCY])"/>

                    <table class="w-full text-sm">
                        <tbody class="divide-y divide-gray-200">
                            <xsl:for-each select="FundDynamicData/TotalAssetValues/TotalAssetValue">
                                <tr class="odd:bg-gray-50">
                                    <th class="p-2 text-left font-semibold w-1/4">Nav Date</th>
                                    <td class="p-2">
                                        <xsl:value-of select="NavDate"/>
                                    </td>
                                </tr>

                                <tr class="odd:bg-gray-50">
                                    <th class="p-2 text-left font-semibold">Nature</th>
                                    <td class="p-2">
                                        <xsl:value-of select="TotalAssetNature"/>
                                    </td>
                                </tr>
                                <tr class="odd:bg-gray-50">
                                    <th class="p-2 text-left font-semibold">TotalAssetValue (Fund Volume) in Fund CCY</th>
                                    <td class="p-2">
                                        <xsl:value-of select="../../../Currency"/>:
                                        <xsl:value-of
                                                select="format-number(TotalNetAssetValue/Amount[@ccy=$fundCCY], '#,##0.00')"/>
                                    </td>
                                </tr>
                                <tr class="odd:bg-gray-50">
                                    <th class="p-2 text-left font-semibold">Sum of ShareClass Volumes (in Funds CCY)</th>
                                    <td class="p-2">
                                        <xsl:value-of select="../../../Currency"/>:
                                        <xsl:value-of
                                                select="format-number($sumOfShareClassVolume, '#,##0.00')"/>
                                    </td>
                                </tr>
                            </xsl:for-each>
                        </tbody>
                    </table>

                    <h3 class="text-lg font-semibold mt-4 mb-2">Portfolio Data [Fund Level]:</h3>
                    <xsl:if test="not(FundDynamicData/Portfolios)">
                        <span class="font-bold text-yellow-600">NO PORTFOLIO DATA FOUND!</span>
                    </xsl:if>
                    <hr class="my-4"/>

                    <xsl:for-each select="FundDynamicData/Portfolios">
                        <xsl:call-template name="Portfolio">
                            <xsl:with-param name="ccy" select="../../Currency"/>
                            <xsl:with-param name="totalAmount"
                                            select="../TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount[@ccy=$fundCCY]"/>
                        </xsl:call-template>
                    </xsl:for-each>

                    <h2 class="text-xl font-semibold mt-6 mb-3">ShareClasses:</h2>
                    <xsl:for-each select="SingleFund/ShareClasses/ShareClass">
                        <h3 id="{Identifiers/ISIN}" class="text-lg font-bold mt-4 mb-2">
                            <xsl:value-of select="concat('#', position(), ' | ', count(../*))"/>
                        </h3>
                        <xsl:variable name="shareClassCcy" select="Currency"/>
                        <table class="w-full border-collapse border border-gray-300 mb-4">
                            <tbody class="divide-y divide-gray-200">
                                <tr class="odd:bg-gray-50">
                                    <th class="p-2 text-left font-semibold w-1/3">ISIN</th>
                                    <td class="p-2">
                                        <xsl:value-of select="Identifiers/ISIN/text()"/>
                                    </td>
                                </tr>
                                <tr class="odd:bg-gray-50">
                                    <th class="p-2 text-left font-semibold">CCY</th>
                                    <td class="p-2">
                                        <xsl:value-of select="$shareClassCcy"/>
                                    </td>
                                </tr>
                                <tr class="odd:bg-gray-50">
                                    <th class="p-2 text-left font-semibold">ShareClass Volumen Datum</th>
                                    <td class="p-2">
                                        <xsl:value-of select="TotalAssetValues/TotalAssetValue/NavDate/text()"/>
                                    </td>
                                </tr>
                                <tr class="odd:bg-gray-50">
                                    <th class="p-2 text-left font-semibold">Total Net Asset Value</th>
                                    <td class="p-2 text-right">
                                        <xsl:value-of
                                                select="format-number(TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount[@ccy=$shareClassCcy], '#,##0.00')"/>
                                    </td>
                                </tr>
                                <tr class="odd:bg-gray-50">
                                    <th class="p-2 text-left font-semibold">Summe ShareClass Positionen (in ShareClass CCY)</th>
                                    <td class="p-2 text-right">
                                        <xsl:value-of
                                                select="format-number(sum(Portfolios/Portfolio/Positions/Position/TotalValue/Amount[@ccy=$shareClassCcy]), '#,##0.00')"/>
                                    </td>
                                </tr>

                                <xsl:if test="Portfolios">
                                    <xsl:variable name="diff" select="0"/>
                                    <tr class="odd:bg-gray-50">
                                        <xsl:choose>
                                            <xsl:when test="abs($diff) > 1">
                                                <xsl:attribute name="class">bg-red-200</xsl:attribute>
                                                <xsl:attribute name="id">ERROR_{generate-id(.)}</xsl:attribute>
                                                <xsl:attribute name="data-error-message">Difference in Volume
                                                    [{format-number($diff, '#,##0.00')}] not in tolerance for Shareclass
                                                    {Identifiers/ISIN/text()}
                                                </xsl:attribute>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <xsl:attribute name="class">bg-green-200</xsl:attribute>
                                            </xsl:otherwise>
                                        </xsl:choose>

                                        <th class="p-2 text-left font-semibold">DIFF</th>
                                        <td class="p-2 text-right">
                                            <xsl:value-of
                                                    select="format-number($diff, '#,##0.00')"/>
                                        </td>
                                    </tr>
                                </xsl:if>
                            </tbody>
                        </table>

                        <h3 class="text-lg font-semibold mt-4 mb-2">Portfolio Data [ShareClass Level]:</h3>
                        <xsl:if test="not(Portfolios)">
                            <span class="text-lg pr-3">No portfolio data on ShareClass found</span>
                        </xsl:if>

                        <xsl:for-each select="Portfolios">
                            <xsl:call-template name="Portfolio">
                                <xsl:with-param name="ccy" select="$shareClassCcy"/>
                                <xsl:with-param name="totalAmount"
                                                select="../TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount[@ccy=$shareClassCcy]"/>
                            </xsl:call-template>
                        </xsl:for-each>
                    </xsl:for-each>
                </div>
            </div>
        </xsl:for-each>
    </xsl:template>

    <xsl:template name="Portfolio">
        <xsl:param name="ccy"/>
        <xsl:param name="totalAmount"/>

        <xsl:for-each select="Portfolio">
            <div class="flex flex-wrap mb-4">
                <div class="pr-2 mb-2 w-full sm:w-auto">
                    <table class="w-full sm:w-auto border border-gray-300">
                        <tbody class="divide-y divide-gray-200">
                            <tr>
                                <th scope="row" class="p-2 text-left font-semibold">Portfolio Date:</th>
                                <td class="p-2 text-right">{NavDate}</td>
                            </tr>
                            <tr>
                                <th scope="row" class="p-2 text-left font-semibold">Position Count:</th>
                                <td class="p-2 text-right">{count(Positions/Position)}</td>
                            </tr>
                        </tbody>
                    </table>
                </div>
                <div class="pr-2 mb-2 w-full sm:w-auto">
                    <table class="w-full sm:w-auto border border-gray-300">
                        <tbody>
                            <tr>
                                <th class="p-2 text-left font-semibold">Currency Aggregation Total Value</th>
                                <td class="p-2">
                                    <table class="text-sm">
                                        <thead class="border-b">
                                            <tr>
                                                <th class="pr-4 font-semibold">CCY</th>
                                                <th class="text-right font-semibold">%</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            <xsl:for-each-group select="Positions/Position" group-by="Currency">
                                                <tr>
                                                    <td class="pr-4"><xsl:value-of select="current-grouping-key()"/>:
                                                    </td>
                                                    <td class="text-right">
                                                        <xsl:value-of
                                                                select="format-number(sum(current-group()/TotalPercentage), '#,##0.00')"/>
                                                    </td>
                                                </tr>
                                            </xsl:for-each-group>
                                        </tbody>
                                    </table>
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </div>

            <xsl:variable name="myId" select="generate-id(.)"/>
            <details class="border rounded-lg mb-4" open="true">
                <summary class="cursor-pointer p-4 bg-gray-100 rounded-t-lg font-semibold text-lg flex items-center">
                    <svg xmlns="http://www.w3.org/2000/svg" width="25" height="25" fill="currentColor"
                         class="mr-3" viewBox="0 0 16 16">
                        <path d="M.5 0a.5.5 0 0 1 .5.5v15a.5.5 0 0 1-1 0V.5A.5.5 0 0 1 .5 0zM2 1.5a.5.5 0 0 1 .5-.5h4a.5.5 0 0 1 .5.5v1a.5.5 0 0 1-.5.5h-4a.5.5 0 0 1-.5-.5v-1zm2 4a.5.5 0 0 1 .5-.5h7a.5.5 0 0 1 .5.5v1a.5.5 0 0 1-.5.5h-7a.5.5 0 0 1-.5-.5v-1zm2 4a.5.5 0 0 1 .5-.5h6a.5.5 0 0 1 .5.5v1a.5.5 0 0 1-.5.5h-6a.5.5 0 0 1-.5-.5v-1zm2 4a.5.5 0 0 1 .5-.5h7a.5.5 0 0 1 .5.5v1a.5.5 0 0 1-.5.5h-7a.5.5 0 0 1-.5-.5v-1z"/>
                    </svg>
                    Portfolio Data
                </summary>
                <div class="p-4 overflow-x-auto">
                    <table class="w-full border-collapse border border-gray-300 text-sm">
                        <thead class="bg-gray-200 sticky top-0">
                            <tr>
                                <th class="p-2 border">#</th>
                                <th class="p-2 border">UniqueID</th>
                                <th class="p-2 border">Currency</th>
                                <th class="p-2 border">
                                    <xsl:variable name="diff"
                                                  select="sum(Positions/Position/TotalValue/Amount[@ccy=$ccy]) - $totalAmount"/>
                                    <xsl:attribute name="class">
                                        <xsl:value-of
                                                select="if (abs($diff) > 1) then 'p-2 border bg-red-200' else 'p-2 border bg-green-200' "/>
                                    </xsl:attribute>

                                    &#x2211; TotalValue:
                                    <span class="float-right"><xsl:value-of select="$ccy"/>:
                                        <xsl:value-of
                                                select="format-number(sum(Positions/Position/TotalValue/Amount[@ccy=$ccy]), '#,##0.00')"/>
                                        (
                                        <span class="inline-block px-2 py-1 text-xs font-semibold rounded-full bg-blue-200 text-blue-800">
                                            <xsl:value-of select="format-number($diff, '#,##0.00')"/>
                                        </span>
                                        )
                                    </span>
                                </th>
                                <th class="p-2 border">
                                    <xsl:variable name="totalPercent"
                                                  select="sum(Positions/Position/TotalPercentage)"/>
                                    <xsl:choose>
                                        <xsl:when test="($totalPercent gt 101 or $totalPercent lt 99)">
                                            <xsl:attribute name="class">p-2 border bg-red-200</xsl:attribute>
                                            <xsl:attribute name="id">ERROR_{generate-id(.)}</xsl:attribute>
                                            <xsl:attribute name="data-error-message">Total Percentage
                                                {format-number($totalPercent, '#0.00')} not in tolerance
                                            </xsl:attribute>
                                        </xsl:when>
                                        <xsl:otherwise>
                                            <xsl:attribute name="class">p-2 border bg-green-200</xsl:attribute>
                                        </xsl:otherwise>
                                    </xsl:choose>

                                    &#x2211; Total%:
                                    <span class="float-right">
                                        <span class="inline-block px-2 py-1 text-xs font-semibold rounded-full bg-blue-200 text-blue-800">
                                            <xsl:value-of select="format-number($totalPercent, '#0.00')"/>
                                        </span>
                                    </span>
                                </th>
                                <th class="p-2 border">FXRates</th>
                                <th class="p-2 border">Detail</th>
                            </tr>
                        </thead>
                        <tbody class="divide-y divide-gray-200">
                            <xsl:for-each select="Positions/Position">
                                <xsl:variable name="assetCcy" select="Currency"/>
                                <tr class="odd:bg-gray-50 hover:bg-gray-100">
                                    <th class="p-2 border text-center">
                                        <xsl:value-of select="position()"/>
                                    </th>

                                    <td class="p-2 border">
                                        <xsl:variable name="anker" select="UniqueID"/>
                                        <a href="#{$anker}" class="text-blue-600 hover:underline">
                                            <xsl:value-of select="UniqueID"/>
                                        </a>
                                        <br/>
                                        <xsl:variable name="asset" select="key('asset-by-id', UniqueID)"/>
                                        <span class="text-xs break-words">Name:
                                            <xsl:choose>
                                                <xsl:when test="string-length($asset/Name) > 15">
                                                    <abbr title="{$asset/Name}">
                                                        {substring($asset/Name, 1, 15)}...
                                                    </abbr>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    {$asset/Name}
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </span>
                                        <br/>
                                        <xsl:if test="$asset/Identifiers/ISIN">
                                            <p class="text-xs">ISIN:
                                                {$asset/Identifiers/ISIN}
                                            </p>
                                        </xsl:if>
                                    </td>
                                    <td class="p-2 border">
                                        {Currency}
                                    </td>
                                    <td class="p-2 border">
                                        <div class="flex justify-between">
                                            <span class="font-light">(Pos. CCY) <xsl:value-of select="$assetCcy"/>:</span>
                                            <span>
                                                <xsl:choose>
                                                    <xsl:when test="TotalValue/Amount[@ccy=$assetCcy]">
                                                        <xsl:value-of
                                                                select="format-number(TotalValue/Amount[@ccy=$assetCcy], '#,##0.00')"/>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <span class="inline-block px-2 py-1 text-xs font-semibold rounded-full bg-red-200 text-red-800">Missing</span>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </span>
                                        </div>
                                        <div class="flex justify-between">
                                            <span class="font-light">(Port. CCY) <xsl:value-of select="$ccy"/>:</span>
                                            <span>
                                                <xsl:choose>
                                                    <xsl:when test="TotalValue/Amount[@ccy=$ccy]">
                                                        <xsl:value-of
                                                                select="format-number(TotalValue/Amount[@ccy=$ccy], '#,##0.00')"/>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <span class="inline-block px-2 py-1 text-xs font-semibold rounded-full bg-red-200 text-red-800">Missing</span>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </span>
                                        </div>

                                        <xsl:for-each
                                                select="TotalValue/Amount[@ccy != $ccy and @ccy != $assetCcy]">
                                            <div class="flex justify-between">
                                                <span><xsl:value-of select="@ccy"/>:</span>
                                                <span>
                                                    <xsl:value-of select="format-number(., '#,##0.00')"/>
                                                </span>
                                            </div>
                                        </xsl:for-each>
                                        <xsl:choose>
                                            <xsl:when test="not(TotalValue/Amount[@ccy=$ccy])">
                                                <div class="mt-1">
                                                    <span class="inline-block w-full text-center px-2 py-1 text-xs font-semibold rounded-full bg-red-200 text-red-800">Missing TotalValue in Portfolio CCY</span>
                                                </div>
                                            </xsl:when>
                                            <xsl:when test="not(TotalValue/Amount[@ccy=$assetCcy])">
                                                <div class="mt-1">
                                                    <span class="inline-block w-full text-center px-2 py-1 text-xs font-semibold rounded-full bg-red-200 text-red-800">Missing TotalValue in Asset CCY</span>
                                                </div>
                                            </xsl:when>
                                        </xsl:choose>

                                        <xsl:variable name="posNumbers" select="count(TotalValue/Amount[number(.) gt 0])" as="xs:integer"/>
                                        <xsl:variable name="negNumbers" select="count(TotalValue/Amount[number(.) lt 0])" as="xs:integer"/>
                                        <xsl:if test="$negNumbers > 0 and $posNumbers > 0">
                                            <div class="mt-1">
                                                <span id="ERROR_{generate-id(.)}"
                                                      data-error-message="ERROR: Negativ and Positiv Value"
                                                      class="inline-block w-full text-center px-2 py-1 text-xs font-semibold rounded-full bg-red-200 text-red-800">ERROR: NEGATIVE AND POSITIVE AMOUNTS</span>
                                            </div>
                                        </xsl:if>
                                    </td>
                                    <td class="p-2 border text-right">
                                        <xsl:choose>
                                            <xsl:when test="not(TotalPercentage)">
                                                <span class="inline-block px-2 py-1 text-xs font-semibold rounded-full bg-yellow-200 text-yellow-800"
                                                      data-error-message="Missing Total Percentage">Missing
                                                </span>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <xsl:value-of select="format-number(TotalPercentage, '#,##0.000')"/>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </td>
                                    <td class="p-2 border">
                                        <xsl:for-each select="FXRates/FXRate">
                                            <div class="flex justify-between text-xs">
                                                <span>
                                                    (<xsl:value-of select="@mulDiv"/>)
                                                    <xsl:value-of select="@fromCcy"/>/<xsl:value-of select="@toCcy"/>
                                                </span>
                                                <span class="text-right">
                                                    <xsl:value-of select="."/>
                                                </span>
                                            </div>
                                            <xsl:if test="@fromCcy eq @toCcy and number(.) ne 1">
                                                <span id="ERROR_{generate-id(.)}"
                                                      data-error-message="ERROR: FX for same Currency not 1 {@fromCcy}/{@toCcy}: [{.}]"
                                                      class="inline-block w-full text-center mt-1 px-2 py-1 text-xs font-semibold rounded-full bg-red-200 text-red-800">ERROR: FX for same Currency not 1</span>
                                            </xsl:if>
                                        </xsl:for-each>
                                    </td>
                                    <td class="p-2 border">
                                        <div class="space-y-2">
                                            <xsl:apply-templates select="Equity | Bond | ShareClass | Warrant | Certificate | Option | Future | FXForward | Swap | Repo | FixedTimeDeposit | CallMoney | Account | Fee | RealEstate | REIT | Loan | Right | Commodity | PrivateEquity | CommercialPaper | Index | Crypto">
                                                <xsl:with-param name="portfolioCcy" select="$ccy"/>
                                            </xsl:apply-templates>
                                            
                                            <div class="text-xs">
                                                <xsl:if test="$renderXMLContent">
                                                    <details>
                                                        <summary class="cursor-pointer">Original XML</summary>
                                                        <p class="mt-1">
                                                            <script type="text/plain" class="language-xml">
                                                                <xsl:copy-of select="node()" copy-namespaces="false"/>
                                                            </script>
                                                        </p>
                                                    </details>
                                                </xsl:if>
                                            </div>
                                        </div>
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
        <xsl:variable name="positionCCY" select="../Currency"/>

        <div class="p-2 bg-gray-50 rounded">
            <span class="font-bold text-sm">{name(.)}</span>
            <table class="w-full text-xs mt-1">
                <tbody class="divide-y divide-gray-100">
                    <!-- Common fields like Units, Price, etc. can be generalized here if needed -->
                </tbody>
            </table>
        </div>
    </xsl:template>

    <!-- Simplified templates for other asset types -->
    <xsl:template match="Position/FXForward | Position/Swap | Position/Repo | Position/FixedTimeDeposit | Position/CallMoney | Position/Account | Position/Fee | Position/RealEstate | Position/REIT | Position/Loan | Position/Right | Position/Commodity | Position/PrivateEquity | Position/CommercialPaper | Position/Index | Position/Crypto">
        <div class="p-2 bg-gray-50 rounded">
            <span class="font-bold text-sm">{name(.)}</span>
        </div>
    </xsl:template>

    <!-- Template for AssetMasterData -->
    <xsl:template match="AssetMasterData">
        <div class="AssetMasterData" id="AssetMasterData">
            <h1 class="text-2xl font-bold mb-4">Asset Master Data</h1>
            <div class="overflow-x-auto">
                <table class="w-full border-collapse border border-gray-300 text-sm">
                    <thead class="bg-gray-200 sticky top-0">
                        <tr>
                            <th class="p-2 border">#</th>
                            <th class="p-2 border">UniqueID</th>
                            <th class="p-2 border">Identifiers</th>
                            <th class="p-2 border w-1/4">Name</th>
                            <th class="p-2 border">Currency</th>
                            <th class="p-2 border">Country</th>
                            <th class="p-2 border">AssetDetails</th>
                        </tr>
                    </thead>
                    <tbody class="divide-y divide-gray-200">
                        <xsl:for-each select="Asset">
                            <tr class="odd:bg-gray-50 hover:bg-gray-100">
                                <th class="p-2 border text-center">
                                    <xsl:value-of select="position()"/>
                                </th>
                                <xsl:variable name="assetAnker" select="UniqueID"/>
                                <td id="{$assetAnker}" class="p-2 border">
                                    <xsl:value-of select="UniqueID"/>
                                </td>
                                <td class="p-2 border">
                                    <xsl:attribute name="class">
                                        <xsl:value-of select="if (count(Identifiers/*) = 0) then 'p-2 border bg-yellow-100' else 'p-2 border' "/>
                                    </xsl:attribute>

                                    <xsl:for-each select="Identifiers/*[name() != 'OtherID']">
                                        <xsl:value-of select="concat(name(), ': ', .)"/><br/>
                                    </xsl:for-each>
                                    <xsl:for-each select="Identifiers/*[name() = 'OtherID']">
                                        <span class="text-xs">
                                            <xsl:choose>
                                                <xsl:when test="string-length(.) > 16">
                                                    <xsl:value-of select="concat(name(), '[@', attribute(), ']:')"/><br/>
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
                                            <span id="ERROR_{generate-id(.)}"
                                                  data-error-message="Missing ISIN for Instrument Type {AssetType} Asset {../UniqueID}"
                                                  class="inline-block mt-1 px-2 py-1 text-xs font-semibold rounded-full bg-red-200 text-red-800">Missing ISIN for Instrument Type {AssetType}
                                            </span>
                                        </xsl:when>
                                    </xsl:choose>
                                </td>
                                <td class="p-2 border">
                                    <xsl:value-of select="Name"/>
                                </td>
                                <td class="p-2 border">
                                    <xsl:value-of select="Currency"/>
                                </td>
                                <td class="p-2 border">
                                    <xsl:value-of select="Country"/>
                                </td>
                                <td class="p-2 border">
                                    Type: <xsl:value-of select="AssetType"/>
                                    (<span class="font-bold"><xsl:value-of select="name(AssetDetails/*[position()=1])"/></span>)
                                    <br/>
                                    <xsl:apply-templates select="AssetDetails/*"/>

                                    <div class="text-xs mt-2">
                                        <xsl:if test="$renderXMLContent">
                                            <details>
                                                <summary class="cursor-pointer">Original XML</summary>
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
    <xsl:template match="AssetDetails/Equity | AssetDetails/Bond | AssetDetails/ShareClass | AssetDetails/Warrant | AssetDetails/Certificate | AssetDetails/Option | AssetDetails/Future | AssetDetails/FXForward | AssetDetails/Swap | AssetDetails/Account | AssetDetails/Fee">
        <div class="mt-2 p-2 bg-gray-50 rounded text-xs space-y-1">
            <xsl:for-each select="*">
                <div class="flex justify-between">
                    <span class="font-semibold pr-2">{name()}:</span>
                    <span class="text-right">{.}</span>
                </div>
            </xsl:for-each>
        </div>
    </xsl:template>

    <!-- Fallback for other asset details -->
    <xsl:template match="AssetDetails/*">
        <div class="mt-2 p-2 bg-gray-50 rounded text-xs">
            <span class="font-bold">{name(.)}</span>
        </div>
    </xsl:template>

</xsl:stylesheet>
