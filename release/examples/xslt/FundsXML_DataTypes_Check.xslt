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
                <script src="https://cdn.tailwindcss.com"></script>
            </head>
            <body class="bg-slate-50 min-h-screen">
                <div class="container mx-auto px-4 py-8">
                    <div class="bg-white rounded-xl shadow-xl p-8">
                        <div class="flex items-center justify-between mb-8">
                            <h1 class="text-4xl font-bold text-slate-900">
                                Data Types Validation
                            </h1>
                            <div class="bg-gradient-to-r from-blue-500 to-purple-600 text-white px-4 py-2 rounded-lg text-sm font-semibold">
                                FundsXML 4.2.2
                            </div>
                        </div>

                        <div class="grid grid-cols-1 md:grid-cols-2 gap-6 mb-8">
                            <div class="bg-gradient-to-br from-blue-50 to-blue-100 border border-blue-200 rounded-lg p-6">
                                <h3 class="font-semibold text-blue-900 mb-2">Document Info</h3>
                                <p class="text-blue-800 text-sm">
                                    ID:
                                    <xsl:value-of select="ControlData/UniqueDocumentID"/>
                                </p>
                                <p class="text-blue-800 text-sm">
                                    Date:
                                    <xsl:value-of select="ControlData/ContentDate"/>
                                </p>
                            </div>
                            <div class="bg-gradient-to-br from-green-50 to-green-100 border border-green-200 rounded-lg p-6">
                                <h3 class="font-semibold text-green-900 mb-2">Validation Status</h3>
                                <p class="text-green-800 text-sm">
                                    Funds:
                                    <xsl:value-of select="count(Funds/Fund)"/>
                                </p>
                                <p class="text-green-800 text-sm">
                                    Generated:
                                    <xsl:value-of select="substring(ControlData/DocumentGenerated, 1, 10)"/>
                                </p>
                            </div>
                        </div>

                        <div class="space-y-8">
                            <!-- Date Format Validation -->
                            <div class="bg-white border border-gray-200 rounded-xl p-6 shadow-sm">
                                <h2 class="text-2xl font-semibold text-gray-900 mb-6 flex items-center">
                                    <div class="w-8 h-8 bg-blue-500 text-white rounded-lg flex items-center justify-center text-sm font-bold mr-3">
                                        1
                                    </div>
                                    Date Format Validation
                                </h2>
                                <div class="grid gap-4">
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
                            <div class="bg-white border border-gray-200 rounded-xl p-6 shadow-sm">
                                <h2 class="text-2xl font-semibold text-gray-900 mb-6 flex items-center">
                                    <div class="w-8 h-8 bg-green-500 text-white rounded-lg flex items-center justify-center text-sm font-bold mr-3">
                                        2
                                    </div>
                                    Numeric Values Validation
                                </h2>
                                <div class="space-y-4">
                                    <xsl:for-each select="Funds/Fund">
                                        <div class="bg-gray-50 rounded-lg p-4 border">
                                            <h3 class="font-semibold text-gray-800 mb-3">
                                                Fund:
                                                <xsl:value-of select="Names/OfficialName"/>
                                            </h3>
                                            <div class="grid gap-3">
                                                <xsl:call-template name="validate-numeric">
                                                    <xsl:with-param name="field-name">Total Net Asset Value
                                                    </xsl:with-param>
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
                            <div class="bg-white border border-gray-200 rounded-xl p-6 shadow-sm">
                                <h2 class="text-2xl font-semibold text-gray-900 mb-6 flex items-center">
                                    <div class="w-8 h-8 bg-purple-500 text-white rounded-lg flex items-center justify-center text-sm font-bold mr-3">
                                        3
                                    </div>
                                    Currency Code Validation
                                </h2>
                                <div class="space-y-3">
                                    <xsl:for-each select="Funds/Fund">
                                        <xsl:call-template name="validate-currency">
                                            <xsl:with-param name="field-name">Fund Base Currency</xsl:with-param>
                                            <xsl:with-param name="currency-code" select="Currency"/>
                                        </xsl:call-template>

                                        <xsl:for-each
                                                select="FundDynamicData/Portfolios/Portfolio/Positions/Position[position() &lt;= 3]">
                                            <xsl:call-template name="validate-currency">
                                                <xsl:with-param name="field-name">Position Currency (ID: <xsl:value-of
                                                        select="UniqueID"/>)
                                                </xsl:with-param>
                                                <xsl:with-param name="currency-code" select="Currency"/>
                                            </xsl:call-template>
                                        </xsl:for-each>
                                    </xsl:for-each>
                                </div>
                            </div>

                            <!-- Summary Statistics -->
                            <div class="bg-gradient-to-r from-slate-100 to-slate-200 rounded-xl p-6 border border-slate-300">
                                <h2 class="text-xl font-semibold text-slate-800 mb-4">Validation Summary</h2>
                                <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
                                    <div class="text-center">
                                        <div class="text-2xl font-bold text-blue-600">
                                            <xsl:value-of select="count(Funds/Fund)"/>
                                        </div>
                                        <div class="text-sm text-slate-600">Funds</div>
                                    </div>
                                    <div class="text-center">
                                        <div class="text-2xl font-bold text-green-600">
                                            <xsl:value-of select="count(//Position)"/>
                                        </div>
                                        <div class="text-sm text-slate-600">Positions</div>
                                    </div>
                                    <div class="text-center">
                                        <div class="text-2xl font-bold text-purple-600">
                                            <xsl:value-of select="count(//Amount)"/>
                                        </div>
                                        <div class="text-sm text-slate-600">Amount Fields</div>
                                    </div>
                                    <div class="text-center">
                                        <div class="text-2xl font-bold text-orange-600">
                                            <xsl:value-of select="count(//*[contains(name(), 'Date')])"/>
                                        </div>
                                        <div class="text-sm text-slate-600">Date Fields</div>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div class="mt-8 p-4 bg-slate-100 rounded-lg">
                            <p class="text-sm text-slate-600 text-center">
                                Generated with Tailwind CSS v4 • Data Types Validation Report
                            </p>
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

        <div class="flex items-center justify-between p-4 bg-gray-50 rounded-lg border">
            <div>
                <span class="font-medium text-gray-800">
                    <xsl:value-of select="$field-name"/>
                </span>
                <div class="text-xs text-gray-500">Expected:
                    <xsl:value-of select="$expected-format"/>
                </div>
            </div>
            <div class="flex items-center space-x-3">
                <xsl:choose>
                    <xsl:when test="$date-value and string-length($date-value) &gt; 0">
                        <span class="px-3 py-1 bg-green-100 text-green-700 text-sm rounded-full font-medium">✓ Valid
                        </span>
                        <code class="text-xs text-gray-600 bg-gray-200 px-2 py-1 rounded">
                            <xsl:value-of select="$date-value"/>
                        </code>
                    </xsl:when>
                    <xsl:otherwise>
                        <span class="px-3 py-1 bg-red-100 text-red-700 text-sm rounded-full font-medium">✗ Missing
                        </span>
                    </xsl:otherwise>
                </xsl:choose>
            </div>
        </div>
    </xsl:template>

    <!-- Numeric validation template -->
    <xsl:template name="validate-numeric">
        <xsl:param name="field-name"/>
        <xsl:param name="numeric-value"/>

        <div class="flex items-center justify-between p-3 bg-white rounded border">
            <span class="text-sm font-medium text-gray-700">
                <xsl:value-of select="$field-name"/>
            </span>
            <div class="flex items-center space-x-2">
                <xsl:choose>
                    <xsl:when test="$numeric-value and $numeric-value != '' and $numeric-value = $numeric-value + 0">
                        <span class="px-2 py-1 bg-green-100 text-green-700 text-xs rounded font-medium">✓ Numeric</span>
                        <code class="text-xs text-gray-600 bg-gray-100 px-2 py-1 rounded">
                            <xsl:value-of select="format-number($numeric-value, '#,##0.00')"/>
                        </code>
                    </xsl:when>
                    <xsl:when test="$numeric-value and $numeric-value != ''">
                        <span class="px-2 py-1 bg-yellow-100 text-yellow-700 text-xs rounded font-medium">⚠ Invalid
                        </span>
                        <code class="text-xs text-gray-600 bg-gray-100 px-2 py-1 rounded">
                            <xsl:value-of select="$numeric-value"/>
                        </code>
                    </xsl:when>
                    <xsl:otherwise>
                        <span class="px-2 py-1 bg-red-100 text-red-700 text-xs rounded font-medium">✗ Missing</span>
                    </xsl:otherwise>
                </xsl:choose>
            </div>
        </div>
    </xsl:template>

    <!-- Currency validation template -->
    <xsl:template name="validate-currency">
        <xsl:param name="field-name"/>
        <xsl:param name="currency-code"/>

        <div class="flex items-center justify-between p-3 bg-gray-50 rounded border">
            <span class="text-sm font-medium text-gray-700">
                <xsl:value-of select="$field-name"/>
            </span>
            <div class="flex items-center space-x-2">
                <xsl:choose>
                    <xsl:when test="$currency-code and string-length($currency-code) = 3">
                        <span class="px-2 py-1 bg-green-100 text-green-700 text-xs rounded font-medium">✓ Valid ISO
                        </span>
                        <code class="text-xs text-gray-600 bg-gray-100 px-2 py-1 rounded font-mono">
                            <xsl:value-of select="$currency-code"/>
                        </code>
                    </xsl:when>
                    <xsl:when test="$currency-code">
                        <span class="px-2 py-1 bg-yellow-100 text-yellow-700 text-xs rounded font-medium">⚠ Format
                        </span>
                        <code class="text-xs text-gray-600 bg-gray-100 px-2 py-1 rounded">
                            <xsl:value-of select="$currency-code"/>
                        </code>
                    </xsl:when>
                    <xsl:otherwise>
                        <span class="px-2 py-1 bg-red-100 text-red-700 text-xs rounded font-medium">✗ Missing</span>
                    </xsl:otherwise>
                </xsl:choose>
            </div>
        </div>
    </xsl:template>

</xsl:stylesheet>