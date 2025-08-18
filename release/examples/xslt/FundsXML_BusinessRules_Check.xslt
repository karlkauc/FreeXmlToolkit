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
                <script src="https://cdn.tailwindcss.com"></script>
            </head>
            <body class="bg-gradient-to-br from-gray-50 to-gray-100 min-h-screen">
                <div class="container mx-auto px-4 py-8">
                    <div class="bg-white rounded-2xl shadow-2xl overflow-hidden">
                        <!-- Header -->
                        <div class="bg-gradient-to-r from-indigo-600 to-purple-700 p-8 text-white">
                            <h1 class="text-4xl font-bold mb-2">Business Rules Validation</h1>
                            <p class="text-indigo-100 text-lg">
                                Comprehensive fund data consistency and business logic checks
                            </p>
                        </div>

                        <div class="p-8">
                            <!-- Summary Cards -->
                            <div class="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
                                <div class="bg-blue-50 border border-blue-200 rounded-xl p-6">
                                    <div class="flex items-center">
                                        <div class="w-12 h-12 bg-blue-500 rounded-lg flex items-center justify-center">
                                            <span class="text-white font-bold text-xl">📊</span>
                                        </div>
                                        <div class="ml-4">
                                            <h3 class="font-semibold text-blue-900">Document</h3>
                                            <p class="text-blue-700 text-sm">
                                                <xsl:value-of select="ControlData/UniqueDocumentID"/>
                                            </p>
                                        </div>
                                    </div>
                                </div>
                                <div class="bg-green-50 border border-green-200 rounded-xl p-6">
                                    <div class="flex items-center">
                                        <div class="w-12 h-12 bg-green-500 rounded-lg flex items-center justify-center">
                                            <span class="text-white font-bold text-xl">💰</span>
                                        </div>
                                        <div class="ml-4">
                                            <h3 class="font-semibold text-green-900">Funds</h3>
                                            <p class="text-green-700 text-sm">
                                                <xsl:value-of select="count(Funds/Fund)"/> fund(s)
                                            </p>
                                        </div>
                                    </div>
                                </div>
                                <div class="bg-purple-50 border border-purple-200 rounded-xl p-6">
                                    <div class="flex items-center">
                                        <div class="w-12 h-12 bg-purple-500 rounded-lg flex items-center justify-center">
                                            <span class="text-white font-bold text-xl">📅</span>
                                        </div>
                                        <div class="ml-4">
                                            <h3 class="font-semibold text-purple-900">Date</h3>
                                            <p class="text-purple-700 text-sm">
                                                <xsl:value-of select="ControlData/ContentDate"/>
                                            </p>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <div class="space-y-8">
                                <!-- Portfolio Percentage Sum Check -->
                                <div class="bg-white border border-gray-200 rounded-xl p-6 shadow-sm">
                                    <h2 class="text-2xl font-semibold text-gray-900 mb-6 flex items-center">
                                        <div class="w-10 h-10 bg-orange-500 text-white rounded-xl flex items-center justify-center text-lg font-bold mr-4">
                                            1
                                        </div>
                                        Portfolio Percentage Sum Check
                                    </h2>

                                    <xsl:for-each select="Funds/Fund">
                                        <div class="mb-6 p-4 border border-gray-200 rounded-lg">
                                            <h3 class="text-lg font-semibold text-gray-800 mb-4">
                                                Fund:
                                                <xsl:value-of select="Names/OfficialName"/>
                                            </h3>

                                            <xsl:for-each select="FundDynamicData/Portfolios/Portfolio">
                                                <xsl:variable name="totalPercentage"
                                                              select="sum(Positions/Position/TotalPercentage)"/>
                                                <div class="bg-gray-50 rounded-lg p-4 mb-3">
                                                    <div class="flex items-center justify-between">
                                                        <div>
                                                            <span class="font-medium text-gray-700">Portfolio Date:
                                                            </span>
                                                            <span class="text-gray-900">
                                                                <xsl:value-of select="NavDate"/>
                                                            </span>
                                                        </div>
                                                        <div class="text-right">
                                                            <div class="text-sm text-gray-600">Total Percentage Sum
                                                            </div>
                                                            <div class="text-xl font-bold">
                                                                <xsl:choose>
                                                                    <xsl:when
                                                                            test="$totalPercentage &gt; 99.5 and $totalPercentage &lt; 100.5">
                                                                        <span class="text-green-600"><xsl:value-of
                                                                                select="format-number($totalPercentage, '0.00')"/>%
                                                                        </span>
                                                                    </xsl:when>
                                                                    <xsl:otherwise>
                                                                        <span class="text-red-600"><xsl:value-of
                                                                                select="format-number($totalPercentage, '0.00')"/>%
                                                                        </span>
                                                                    </xsl:otherwise>
                                                                </xsl:choose>
                                                            </div>
                                                        </div>
                                                    </div>
                                                    <div class="mt-3">
                                                        <xsl:choose>
                                                            <xsl:when
                                                                    test="$totalPercentage &gt; 99.5 and $totalPercentage &lt; 100.5">
                                                                <div class="flex items-center text-green-700 bg-green-100 px-3 py-2 rounded">
                                                                    <span class="text-lg mr-2">✓</span>
                                                                    <span class="font-medium">Portfolio sum is within
                                                                        acceptable range (99.5% - 100.5%)
                                                                    </span>
                                                                </div>
                                                            </xsl:when>
                                                            <xsl:otherwise>
                                                                <div class="flex items-center text-red-700 bg-red-100 px-3 py-2 rounded">
                                                                    <span class="text-lg mr-2">⚠</span>
                                                                    <span class="font-medium">Portfolio sum is outside
                                                                        acceptable range (99.5% - 100.5%)
                                                                    </span>
                                                                </div>
                                                            </xsl:otherwise>
                                                        </xsl:choose>
                                                    </div>
                                                </div>
                                            </xsl:for-each>
                                        </div>
                                    </xsl:for-each>
                                </div>

                                <!-- NAV Consistency Check -->
                                <div class="bg-white border border-gray-200 rounded-xl p-6 shadow-sm">
                                    <h2 class="text-2xl font-semibold text-gray-900 mb-6 flex items-center">
                                        <div class="w-10 h-10 bg-blue-500 text-white rounded-xl flex items-center justify-center text-lg font-bold mr-4">
                                            2
                                        </div>
                                        NAV Date Consistency Check
                                    </h2>

                                    <xsl:for-each select="Funds/Fund">
                                        <div class="mb-4 p-4 border border-gray-200 rounded-lg">
                                            <h3 class="text-lg font-semibold text-gray-800 mb-3">
                                                Fund:
                                                <xsl:value-of select="Names/OfficialName"/>
                                            </h3>

                                            <xsl:variable name="fundNavDate"
                                                          select="FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate"/>
                                            <xsl:variable name="portfolioNavDate"
                                                          select="FundDynamicData/Portfolios/Portfolio/NavDate"/>

                                            <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                                                <div class="bg-blue-50 p-3 rounded">
                                                    <div class="text-sm text-blue-600 font-medium">Fund NAV Date</div>
                                                    <div class="text-lg font-semibold text-blue-800">
                                                        <xsl:value-of select="$fundNavDate"/>
                                                    </div>
                                                </div>
                                                <div class="bg-purple-50 p-3 rounded">
                                                    <div class="text-sm text-purple-600 font-medium">Portfolio Date
                                                    </div>
                                                    <div class="text-lg font-semibold text-purple-800">
                                                        <xsl:value-of select="$portfolioNavDate"/>
                                                    </div>
                                                </div>
                                            </div>

                                            <div class="mt-3">
                                                <xsl:choose>
                                                    <xsl:when test="$fundNavDate = $portfolioNavDate">
                                                        <div class="flex items-center text-green-700 bg-green-100 px-3 py-2 rounded">
                                                            <span class="text-lg mr-2">✓</span>
                                                            <span class="font-medium">NAV dates are consistent</span>
                                                        </div>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <div class="flex items-center text-red-700 bg-red-100 px-3 py-2 rounded">
                                                            <span class="text-lg mr-2">⚠</span>
                                                            <span class="font-medium">NAV dates are inconsistent</span>
                                                        </div>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </div>
                                        </div>
                                    </xsl:for-each>
                                </div>

                                <!-- Currency Consistency Check -->
                                <div class="bg-white border border-gray-200 rounded-xl p-6 shadow-sm">
                                    <h2 class="text-2xl font-semibent text-gray-900 mb-6 flex items-center">
                                        <div class="w-10 h-10 bg-green-500 text-white rounded-xl flex items-center justify-center text-lg font-bold mr-4">
                                            3
                                        </div>
                                        Currency Consistency Check
                                    </h2>

                                    <xsl:for-each select="Funds/Fund">
                                        <div class="mb-6 p-4 border border-gray-200 rounded-lg">
                                            <h3 class="text-lg font-semibold text-gray-800 mb-4">
                                                Fund:
                                                <xsl:value-of select="Names/OfficialName"/>
                                                <span class="text-sm text-gray-600 ml-2">(Base: <xsl:value-of
                                                        select="Currency"/>)
                                                </span>
                                            </h3>

                                            <div class="space-y-2">
                                                <xsl:for-each
                                                        select="FundDynamicData/Portfolios/Portfolio/Positions/Position[position() &lt;= 10]">
                                                    <div class="flex items-center justify-between p-3 bg-gray-50 rounded border">
                                                        <div class="flex items-center">
                                                            <span class="text-sm font-medium text-gray-700">Position:
                                                            </span>
                                                            <code class="text-xs bg-gray-200 px-2 py-1 rounded ml-2">
                                                                <xsl:value-of select="UniqueID"/>
                                                            </code>
                                                        </div>
                                                        <div class="flex items-center space-x-2">
                                                            <span class="text-sm text-gray-600">Currency:</span>
                                                            <span class="font-semibold">
                                                                <xsl:value-of select="Currency"/>
                                                            </span>
                                                            <xsl:choose>
                                                                <xsl:when test="Currency = ../../../../../../Currency">
                                                                    <span class="px-2 py-1 bg-green-100 text-green-700 text-xs rounded">
                                                                        ✓ Match
                                                                    </span>
                                                                </xsl:when>
                                                                <xsl:otherwise>
                                                                    <span class="px-2 py-1 bg-blue-100 text-blue-700 text-xs rounded">
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
                                <div class="bg-white border border-gray-200 rounded-xl p-6 shadow-sm">
                                    <h2 class="text-2xl font-semibold text-gray-900 mb-6 flex items-center">
                                        <div class="w-10 h-10 bg-red-500 text-white rounded-xl flex items-center justify-center text-lg font-bold mr-4">
                                            4
                                        </div>
                                        Position Value vs. Percentage Check
                                    </h2>

                                    <xsl:for-each select="Funds/Fund">
                                        <xsl:variable name="totalNav"
                                                      select="FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount"/>

                                        <div class="mb-6 p-4 border border-gray-200 rounded-lg">
                                            <h3 class="text-lg font-semibold text-gray-800 mb-4">
                                                Fund:
                                                <xsl:value-of select="Names/OfficialName"/>
                                                <div class="text-sm text-gray-600 mt-1">
                                                    Total NAV:
                                                    <xsl:value-of select="format-number($totalNav, '#,##0.00')"/>
                                                    <xsl:value-of
                                                            select="FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount/@ccy"/>
                                                </div>
                                            </h3>

                                            <div class="space-y-2">
                                                <xsl:for-each
                                                        select="FundDynamicData/Portfolios/Portfolio/Positions/Position[position() &lt;= 5]">
                                                    <xsl:variable name="positionValue" select="TotalValue/Amount"/>
                                                    <xsl:variable name="positionPercentage" select="TotalPercentage"/>
                                                    <xsl:variable name="calculatedPercentage"
                                                                  select="($positionValue div $totalNav) * 100"/>
                                                    <xsl:variable name="percentageDiff"
                                                                  select="$positionPercentage - $calculatedPercentage"/>

                                                    <div class="flex items-center justify-between p-3 bg-gray-50 rounded border">
                                                        <div>
                                                            <div class="text-sm font-medium text-gray-700">
                                                                Position:
                                                                <xsl:value-of select="UniqueID"/>
                                                            </div>
                                                            <div class="text-xs text-gray-500 mt-1">
                                                                Value:
                                                                <xsl:value-of
                                                                        select="format-number($positionValue, '#,##0.00')"/>
                                                                |
                                                                Stated: <xsl:value-of
                                                                    select="format-number($positionPercentage, '0.00')"/>%
                                                                |
                                                                Calculated: <xsl:value-of
                                                                    select="format-number($calculatedPercentage, '0.00')"/>%
                                                            </div>
                                                        </div>
                                                        <div class="text-right">
                                                            <xsl:choose>
                                                                <xsl:when
                                                                        test="$percentageDiff &gt;= -0.1 and $percentageDiff &lt;= 0.1">
                                                                    <span class="px-2 py-1 bg-green-100 text-green-700 text-xs rounded font-medium">
                                                                        ✓ Consistent
                                                                    </span>
                                                                </xsl:when>
                                                                <xsl:otherwise>
                                                                    <span class="px-2 py-1 bg-red-100 text-red-700 text-xs rounded font-medium">
                                                                        ⚠ Diff: <xsl:value-of
                                                                            select="format-number($percentageDiff, '0.00')"/>%
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
                        </div>

                        <div class="bg-gray-100 p-6">
                            <p class="text-sm text-gray-600 text-center">
                                Generated with Tailwind CSS v4 • Business Rules Validation Report
                            </p>
                        </div>
                    </div>
                </div>
            </body>
        </html>
    </xsl:template>

</xsl:stylesheet>