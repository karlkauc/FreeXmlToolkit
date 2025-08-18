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
                <script src="https://cdn.tailwindcss.com"></script>
                <script>
                    tailwind.config = {
                        theme: {
                            extend: {
                                animation: {
                                    'fade-in': 'fadeIn 0.5s ease-in-out',
                                    'slide-in': 'slideIn 0.6s ease-out'
                                }
                            }
                        }
                    }
                </script>
                <style>
                    @keyframes fadeIn {
                    from { opacity: 0; }
                    to { opacity: 1; }
                    }
                    @keyframes slideIn {
                    from { transform: translateY(20px); opacity: 0; }
                    to { transform: translateY(0); opacity: 1; }
                    }
                    .metric-card {
                    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
                    }
                    .metric-card:hover {
                    transform: translateY(-4px);
                    box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1);
                    }
                </style>
            </head>
            <body class="bg-gradient-to-br from-slate-900 via-purple-900 to-slate-900 min-h-screen">
                <div class="container mx-auto px-4 py-8">
                    <div class="bg-white/95 backdrop-blur-sm rounded-3xl shadow-2xl overflow-hidden border border-white/20">
                        <!-- Hero Header -->
                        <div class="bg-gradient-to-r from-violet-600 via-purple-600 to-indigo-600 p-10 relative overflow-hidden">
                            <div class="absolute inset-0">
                                <div class="absolute inset-0 bg-gradient-to-r from-violet-600/20 via-transparent to-indigo-600/20 animate-pulse"></div>
                            </div>
                            <div class="relative z-10">
                                <h1 class="text-5xl font-bold text-white mb-4 animate-fade-in">Performance Analysis</h1>
                                <p class="text-violet-100 text-xl animate-slide-in">
                                    Fund performance metrics and risk assessment dashboard
                                </p>
                            </div>
                        </div>

                        <div class="p-10">
                            <!-- Fund Overview Cards -->
                            <div class="grid grid-cols-1 lg:grid-cols-3 gap-8 mb-12">
                                <xsl:for-each select="Funds/Fund">
                                    <div class="metric-card bg-gradient-to-br from-white to-gray-50 border border-gray-200 rounded-2xl p-8 shadow-lg">
                                        <div class="flex items-center justify-between mb-6">
                                            <h2 class="text-2xl font-bold text-gray-900">
                                                <xsl:value-of select="Names/OfficialName"/>
                                            </h2>
                                            <div class="w-16 h-16 bg-gradient-to-br from-blue-500 to-purple-600 rounded-2xl flex items-center justify-center">
                                                <span class="text-white text-2xl font-bold">💼</span>
                                            </div>
                                        </div>

                                        <div class="space-y-4">
                                            <div class="flex justify-between items-center">
                                                <span class="text-gray-600">Total NAV</span>
                                                <span class="text-xl font-bold text-gray-900">
                                                    <xsl:value-of
                                                            select="format-number(FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount, '#,##0.00')"/>
                                                    <span class="text-sm text-gray-500 ml-1">
                                                        <xsl:value-of
                                                                select="FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount/@ccy"/>
                                                    </span>
                                                </span>
                                            </div>
                                            <div class="flex justify-between items-center">
                                                <span class="text-gray-600">Positions</span>
                                                <span class="text-lg font-semibold text-blue-600">
                                                    <xsl:value-of
                                                            select="count(FundDynamicData/Portfolios/Portfolio/Positions/Position)"/>
                                                </span>
                                            </div>
                                            <div class="flex justify-between items-center">
                                                <span class="text-gray-600">Currency</span>
                                                <span class="px-3 py-1 bg-purple-100 text-purple-800 rounded-full text-sm font-medium">
                                                    <xsl:value-of select="Currency"/>
                                                </span>
                                            </div>
                                            <div class="flex justify-between items-center">
                                                <span class="text-gray-600">Inception</span>
                                                <span class="text-gray-900 font-medium">
                                                    <xsl:value-of select="FundStaticData/InceptionDate"/>
                                                </span>
                                            </div>
                                        </div>
                                    </div>
                                </xsl:for-each>
                            </div>

                            <div class="space-y-12">
                                <!-- Portfolio Concentration Analysis -->
                                <div class="bg-white border border-gray-200 rounded-2xl p-8 shadow-lg">
                                    <h2 class="text-3xl font-bold text-gray-900 mb-8 flex items-center">
                                        <div class="w-12 h-12 bg-gradient-to-br from-emerald-500 to-teal-600 text-white rounded-2xl flex items-center justify-center text-xl font-bold mr-4">
                                            📈
                                        </div>
                                        Portfolio Concentration Analysis
                                    </h2>

                                    <xsl:for-each select="Funds/Fund">
                                        <div class="mb-10 last:mb-0">
                                            <h3 class="text-xl font-semibold text-gray-800 mb-6 flex items-center">
                                                <span class="w-8 h-8 bg-blue-100 text-blue-600 rounded-lg flex items-center justify-center text-sm font-bold mr-3">
                                                    <xsl:value-of select="position()"/>
                                                </span>
                                                <xsl:value-of select="Names/OfficialName"/>
                                            </h3>

                                            <!-- Top 10 Holdings Analysis -->
                                            <div class="bg-gradient-to-r from-blue-50 to-indigo-50 rounded-xl p-6 mb-6">
                                                <h4 class="text-lg font-semibold text-indigo-900 mb-4">Top Holdings
                                                    Distribution
                                                </h4>
                                                <div class="space-y-3">
                                                    <xsl:for-each
                                                            select="FundDynamicData/Portfolios/Portfolio/Positions/Position">
                                                        <xsl:sort select="TotalPercentage" data-type="number"
                                                                  order="descending"/>
                                                        <xsl:if test="position() &lt;= 10">
                                                            <xsl:variable name="percentage" select="TotalPercentage"/>
                                                            <div class="flex items-center space-x-4">
                                                                <div class="w-8 h-8 bg-indigo-100 text-indigo-600 rounded-lg flex items-center justify-center text-xs font-bold">
                                                                    <xsl:value-of select="position()"/>
                                                                </div>
                                                                <div class="flex-1">
                                                                    <div class="flex items-center justify-between mb-1">
                                                                        <span class="text-sm font-medium text-gray-700 truncate">
                                                                            Position ID:
                                                                            <xsl:value-of select="UniqueID"/>
                                                                        </span>
                                                                        <span class="text-sm font-bold text-indigo-600">
                                                                            <xsl:value-of
                                                                                    select="format-number($percentage, '0.00')"/>%
                                                                        </span>
                                                                    </div>
                                                                    <div class="w-full bg-gray-200 rounded-full h-2">
                                                                        <div class="bg-gradient-to-r from-indigo-500 to-purple-600 h-2 rounded-full transition-all duration-700"
                                                                             style="width: {$percentage}%"></div>
                                                                    </div>
                                                                </div>
                                                            </div>
                                                        </xsl:if>
                                                    </xsl:for-each>
                                                </div>
                                            </div>

                                            <!-- Concentration Metrics -->
                                            <xsl:variable name="top5Sum"
                                                          select="sum(FundDynamicData/Portfolios/Portfolio/Positions/Position[position() &lt;= 5]/TotalPercentage)"/>
                                            <xsl:variable name="top10Sum"
                                                          select="sum(FundDynamicData/Portfolios/Portfolio/Positions/Position[position() &lt;= 10]/TotalPercentage)"/>

                                            <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
                                                <!-- Top 5 Concentration -->
                                                <div class="bg-gradient-to-br from-orange-50 to-red-50 border border-orange-200 rounded-xl p-6">
                                                    <div class="text-center">
                                                        <div class="text-3xl font-bold text-orange-600 mb-2">
                                                            <xsl:value-of select="format-number($top5Sum, '0.0')"/>%
                                                        </div>
                                                        <div class="text-orange-800 font-medium">Top 5 Holdings</div>
                                                        <div class="mt-2">
                                                            <xsl:choose>
                                                                <xsl:when test="$top5Sum &gt; 50">
                                                                    <span class="px-2 py-1 bg-red-100 text-red-700 text-xs rounded-full">
                                                                        High Risk
                                                                    </span>
                                                                </xsl:when>
                                                                <xsl:when test="$top5Sum &gt; 30">
                                                                    <span class="px-2 py-1 bg-yellow-100 text-yellow-700 text-xs rounded-full">
                                                                        Medium Risk
                                                                    </span>
                                                                </xsl:when>
                                                                <xsl:otherwise>
                                                                    <span class="px-2 py-1 bg-green-100 text-green-700 text-xs rounded-full">
                                                                        Low Risk
                                                                    </span>
                                                                </xsl:otherwise>
                                                            </xsl:choose>
                                                        </div>
                                                    </div>
                                                </div>

                                                <!-- Top 10 Concentration -->
                                                <div class="bg-gradient-to-br from-blue-50 to-indigo-50 border border-blue-200 rounded-xl p-6">
                                                    <div class="text-center">
                                                        <div class="text-3xl font-bold text-blue-600 mb-2">
                                                            <xsl:value-of select="format-number($top10Sum, '0.0')"/>%
                                                        </div>
                                                        <div class="text-blue-800 font-medium">Top 10 Holdings</div>
                                                        <div class="mt-2">
                                                            <xsl:choose>
                                                                <xsl:when test="$top10Sum &gt; 70">
                                                                    <span class="px-2 py-1 bg-red-100 text-red-700 text-xs rounded-full">
                                                                        High Concentration
                                                                    </span>
                                                                </xsl:when>
                                                                <xsl:when test="$top10Sum &gt; 50">
                                                                    <span class="px-2 py-1 bg-yellow-100 text-yellow-700 text-xs rounded-full">
                                                                        Medium Concentration
                                                                    </span>
                                                                </xsl:when>
                                                                <xsl:otherwise>
                                                                    <span class="px-2 py-1 bg-green-100 text-green-700 text-xs rounded-full">
                                                                        Well Diversified
                                                                    </span>
                                                                </xsl:otherwise>
                                                            </xsl:choose>
                                                        </div>
                                                    </div>
                                                </div>

                                                <!-- Total Positions -->
                                                <div class="bg-gradient-to-br from-purple-50 to-pink-50 border border-purple-200 rounded-xl p-6">
                                                    <div class="text-center">
                                                        <div class="text-3xl font-bold text-purple-600 mb-2">
                                                            <xsl:value-of
                                                                    select="count(FundDynamicData/Portfolios/Portfolio/Positions/Position)"/>
                                                        </div>
                                                        <div class="text-purple-800 font-medium">Total Positions</div>
                                                        <div class="mt-2">
                                                            <xsl:choose>
                                                                <xsl:when
                                                                        test="count(FundDynamicData/Portfolios/Portfolio/Positions/Position) &gt; 100">
                                                                    <span class="px-2 py-1 bg-green-100 text-green-700 text-xs rounded-full">
                                                                        Highly Diversified
                                                                    </span>
                                                                </xsl:when>
                                                                <xsl:when
                                                                        test="count(FundDynamicData/Portfolios/Portfolio/Positions/Position) &gt; 50">
                                                                    <span class="px-2 py-1 bg-blue-100 text-blue-700 text-xs rounded-full">
                                                                        Well Diversified
                                                                    </span>
                                                                </xsl:when>
                                                                <xsl:otherwise>
                                                                    <span class="px-2 py-1 bg-yellow-100 text-yellow-700 text-xs rounded-full">
                                                                        Concentrated
                                                                    </span>
                                                                </xsl:otherwise>
                                                            </xsl:choose>
                                                        </div>
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    </xsl:for-each>
                                </div>

                                <!-- Risk Metrics Dashboard -->
                                <div class="bg-white border border-gray-200 rounded-2xl p-8 shadow-lg">
                                    <h2 class="text-3xl font-bold text-gray-900 mb-8 flex items-center">
                                        <div class="w-12 h-12 bg-gradient-to-br from-red-500 to-pink-600 text-white rounded-2xl flex items-center justify-center text-xl font-bold mr-4">
                                            ⚡
                                        </div>
                                        Risk Metrics Dashboard
                                    </h2>

                                    <xsl:for-each select="Funds/Fund">
                                        <div class="mb-10 last:mb-0">
                                            <h3 class="text-xl font-semibold text-gray-800 mb-6">
                                                Risk Assessment:
                                                <xsl:value-of select="Names/OfficialName"/>
                                            </h3>

                                            <xsl:choose>
                                                <xsl:when
                                                        test="FundDynamicData/Portfolios/Portfolio/RiskCodes/RiskCode">
                                                    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                                                        <xsl:for-each
                                                                select="FundDynamicData/Portfolios/Portfolio/RiskCodes/RiskCode">
                                                            <div class="bg-gradient-to-br from-gray-50 to-gray-100 border border-gray-200 rounded-xl p-6">
                                                                <div class="text-center">
                                                                    <div class="text-2xl font-bold text-gray-700 mb-2">
                                                                        <xsl:value-of
                                                                                select="format-number(Value, '0.0000')"/>
                                                                    </div>
                                                                    <div class="text-gray-600 font-medium text-sm">
                                                                        <xsl:value-of
                                                                                select="ListedCode | UnlistedCode"/>
                                                                    </div>
                                                                </div>
                                                            </div>
                                                        </xsl:for-each>
                                                    </div>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <div class="bg-yellow-50 border border-yellow-200 rounded-xl p-6">
                                                        <div class="flex items-center justify-center text-yellow-700">
                                                            <span class="text-2xl mr-2">⚠️</span>
                                                            <span class="font-medium">No risk metrics available for this
                                                                fund
                                                            </span>
                                                        </div>
                                                    </div>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </div>
                                    </xsl:for-each>
                                </div>

                                <!-- Currency Exposure Analysis -->
                                <div class="bg-white border border-gray-200 rounded-2xl p-8 shadow-lg">
                                    <h2 class="text-3xl font-bold text-gray-900 mb-8 flex items-center">
                                        <div class="w-12 h-12 bg-gradient-to-br from-yellow-500 to-orange-600 text-white rounded-2xl flex items-center justify-center text-xl font-bold mr-4">
                                            💱
                                        </div>
                                        Currency Exposure Analysis
                                    </h2>

                                    <xsl:for-each select="Funds/Fund">
                                        <div class="mb-8 p-6 bg-gradient-to-r from-yellow-50 to-orange-50 border border-yellow-200 rounded-xl">
                                            <h3 class="text-xl font-semibold text-gray-800 mb-4">
                                                <xsl:value-of select="Names/OfficialName"/>
                                                <span class="text-sm text-gray-600 ml-2">(Base: <xsl:value-of
                                                        select="Currency"/>)
                                                </span>
                                            </h3>

                                            <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
                                                <!-- Count different currencies -->
                                                <xsl:for-each
                                                        select="FundDynamicData/Portfolios/Portfolio/Positions/Position/Currency[generate-id() = generate-id(key('position-currencies', .)[1])]">
                                                    <xsl:sort select="."/>
                                                    <div class="text-center p-4 bg-white border border-yellow-300 rounded-lg">
                                                        <div class="text-lg font-bold text-yellow-700">
                                                            <xsl:value-of
                                                                    select="count(key('position-currencies', .))"/>
                                                        </div>
                                                        <div class="text-sm text-yellow-600">
                                                            <xsl:value-of select="."/> positions
                                                        </div>
                                                    </div>
                                                </xsl:for-each>
                                            </div>
                                        </div>
                                    </xsl:for-each>
                                </div>
                            </div>
                        </div>

                        <!-- Footer -->
                        <div class="bg-gradient-to-r from-slate-100 via-purple-50 to-slate-100 p-6">
                            <p class="text-sm text-slate-600 text-center">
                                Generated with Tailwind CSS v4 • Performance &amp; Risk Analysis Dashboard
                            </p>
                        </div>
                    </div>
                </div>
            </body>
        </html>
    </xsl:template>

    <!-- Keys for grouping -->
    <xsl:key name="position-currencies" match="Position/Currency" use="."/>

</xsl:stylesheet>