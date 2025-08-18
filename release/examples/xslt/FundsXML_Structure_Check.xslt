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
                <script src="https://cdn.tailwindcss.com"></script>
                <style>
                    .tree-line { border-left: 2px solid #e5e7eb; }
                    .tree-branch::before { content: "├─ "; color: #9ca3af; }
                    .tree-last::before { content: "└─ "; color: #9ca3af; }
                    .tree-element { font-family: 'Courier New', monospace; }
                </style>
            </head>
            <body class="bg-gradient-to-br from-indigo-50 via-white to-cyan-50 min-h-screen">
                <div class="container mx-auto px-4 py-8">
                    <div class="bg-white rounded-3xl shadow-2xl overflow-hidden border border-gray-100">
                        <!-- Animated Header -->
                        <div class="bg-gradient-to-r from-blue-600 via-indigo-600 to-purple-600 p-8 relative overflow-hidden">
                            <div class="absolute inset-0 bg-gradient-to-r from-transparent via-white/10 to-transparent transform -skew-x-12"></div>
                            <div class="relative z-10">
                                <h1 class="text-4xl font-bold text-white mb-3">XML Structure Analysis</h1>
                                <p class="text-blue-100 text-lg">
                                    Comprehensive document structure and hierarchy validation
                                </p>
                            </div>
                        </div>

                        <div class="p-8">
                            <!-- Quick Stats -->
                            <div class="grid grid-cols-1 md:grid-cols-4 gap-6 mb-8">
                                <div class="bg-gradient-to-br from-blue-50 to-blue-100 border border-blue-200 rounded-2xl p-6 transform hover:scale-105 transition-transform duration-200">
                                    <div class="text-3xl font-bold text-blue-600 mb-2">
                                        <xsl:value-of select="count(//*)"/>
                                    </div>
                                    <div class="text-blue-800 font-medium">Total Elements</div>
                                </div>
                                <div class="bg-gradient-to-br from-green-50 to-green-100 border border-green-200 rounded-2xl p-6 transform hover:scale-105 transition-transform duration-200">
                                    <div class="text-3xl font-bold text-green-600 mb-2">
                                        <xsl:value-of select="count(Funds/Fund)"/>
                                    </div>
                                    <div class="text-green-800 font-medium">Funds</div>
                                </div>
                                <div class="bg-gradient-to-br from-purple-50 to-purple-100 border border-purple-200 rounded-2xl p-6 transform hover:scale-105 transition-transform duration-200">
                                    <div class="text-3xl font-bold text-purple-600 mb-2">
                                        <xsl:value-of select="count(//Position)"/>
                                    </div>
                                    <div class="text-purple-800 font-medium">Positions</div>
                                </div>
                                <div class="bg-gradient-to-br from-orange-50 to-orange-100 border border-orange-200 rounded-2xl p-6 transform hover:scale-105 transition-transform duration-200">
                                    <div class="text-3xl font-bold text-orange-600 mb-2">
                                        <xsl:value-of select="count(//Asset)"/>
                                    </div>
                                    <div class="text-orange-800 font-medium">Assets</div>
                                </div>
                            </div>

                            <div class="space-y-8">
                                <!-- Document Structure Tree -->
                                <div class="bg-white border border-gray-200 rounded-2xl p-8 shadow-lg">
                                    <h2 class="text-3xl font-bold text-gray-900 mb-8 flex items-center">
                                        <div class="w-12 h-12 bg-gradient-to-br from-indigo-500 to-purple-600 text-white rounded-2xl flex items-center justify-center text-xl font-bold mr-4">
                                            📁
                                        </div>
                                        Document Structure Tree
                                    </h2>

                                    <div class="bg-gray-900 rounded-xl p-6 overflow-x-auto">
                                        <div class="text-green-400 font-mono text-sm leading-relaxed">
                                            <div class="text-cyan-300 font-bold mb-2">FundsXML4</div>
                                            <div class="ml-4 space-y-1">
                                                <div class="tree-element">├─
                                                    <span class="text-yellow-400">ControlData</span>
                                                </div>
                                                <div class="ml-6 text-gray-400 text-xs space-y-1">
                                                    <div>├─ UniqueDocumentID:
                                                        <span class="text-white">
                                                            <xsl:value-of select="ControlData/UniqueDocumentID"/>
                                                        </span>
                                                    </div>
                                                    <div>├─ DocumentGenerated:
                                                        <span class="text-white">
                                                            <xsl:value-of select="ControlData/DocumentGenerated"/>
                                                        </span>
                                                    </div>
                                                    <div>├─ ContentDate:
                                                        <span class="text-white">
                                                            <xsl:value-of select="ControlData/ContentDate"/>
                                                        </span>
                                                    </div>
                                                    <div>├─ DataSupplier</div>
                                                    <div>├─ DataOperation:
                                                        <span class="text-white">
                                                            <xsl:value-of select="ControlData/DataOperation"/>
                                                        </span>
                                                    </div>
                                                    <div>└─ Language:
                                                        <span class="text-white">
                                                            <xsl:value-of select="ControlData/Language"/>
                                                        </span>
                                                    </div>
                                                </div>
                                                <div class="tree-element">├─
                                                    <span class="text-yellow-400">Funds</span>
                                                </div>
                                                <div class="ml-6">
                                                    <xsl:for-each select="Funds/Fund">
                                                        <div class="tree-element mb-2">
                                                            <xsl:choose>
                                                                <xsl:when test="position() = last()">└─</xsl:when>
                                                                <xsl:otherwise>├─</xsl:otherwise>
                                                            </xsl:choose>
                                                            <span class="text-green-400">Fund[<xsl:value-of
                                                                    select="position()"/>]
                                                            </span>
                                                            <span class="text-gray-400 text-xs ml-2">(<xsl:value-of
                                                                    select="Names/OfficialName"/>)
                                                            </span>
                                                        </div>
                                                        <div class="ml-8 text-gray-400 text-xs space-y-1">
                                                            <div>├─ Identifiers (LEI:
                                                                <span class="text-white">
                                                                    <xsl:value-of select="Identifiers/LEI"/>
                                                                </span>
                                                                )
                                                            </div>
                                                            <div>├─ Names</div>
                                                            <div>├─ Currency:
                                                                <span class="text-white">
                                                                    <xsl:value-of select="Currency"/>
                                                                </span>
                                                            </div>
                                                            <div>├─ FundStaticData</div>
                                                            <div>├─ FundDynamicData</div>
                                                            <div>│ ├─ TotalAssetValues</div>
                                                            <div>│ └─ Portfolios (
                                                                <span class="text-cyan-300">
                                                                    <xsl:value-of
                                                                            select="count(FundDynamicData/Portfolios/Portfolio/Positions/Position)"/>
                                                                    positions
                                                                </span>
                                                                )
                                                            </div>
                                                            <xsl:if test="SingleFund">
                                                                <div>└─ SingleFund</div>
                                                            </xsl:if>
                                                        </div>
                                                    </xsl:for-each>
                                                </div>
                                                <xsl:if test="Assets">
                                                    <div class="tree-element">└─ <span class="text-yellow-400">Assets
                                                    </span> (
                                                        <span class="text-cyan-300">
                                                            <xsl:value-of select="count(Assets/Asset)"/> assets
                                                        </span>
                                                        )
                                                    </div>
                                                </xsl:if>
                                            </div>
                                        </div>
                                    </div>
                                </div>

                                <!-- Element Count Analysis -->
                                <div class="bg-white border border-gray-200 rounded-2xl p-8 shadow-lg">
                                    <h2 class="text-3xl font-bold text-gray-900 mb-8 flex items-center">
                                        <div class="w-12 h-12 bg-gradient-to-br from-green-500 to-teal-600 text-white rounded-2xl flex items-center justify-center text-xl font-bold mr-4">
                                            📊
                                        </div>
                                        Element Distribution Analysis
                                    </h2>

                                    <div class="grid grid-cols-1 lg:grid-cols-2 gap-8">
                                        <!-- Most Common Elements -->
                                        <div class="space-y-4">
                                            <h3 class="text-xl font-semibold text-gray-800 mb-4">Most Common Elements
                                            </h3>
                                            <xsl:call-template name="element-count-bar">
                                                <xsl:with-param name="element-name">Amount</xsl:with-param>
                                                <xsl:with-param name="count" select="count(//Amount)"/>
                                                <xsl:with-param name="max-count" select="count(//Amount)"/>
                                                <xsl:with-param name="color">bg-blue-500</xsl:with-param>
                                            </xsl:call-template>
                                            <xsl:call-template name="element-count-bar">
                                                <xsl:with-param name="element-name">Position</xsl:with-param>
                                                <xsl:with-param name="count" select="count(//Position)"/>
                                                <xsl:with-param name="max-count" select="count(//Amount)"/>
                                                <xsl:with-param name="color">bg-green-500</xsl:with-param>
                                            </xsl:call-template>
                                            <xsl:call-template name="element-count-bar">
                                                <xsl:with-param name="element-name">UniqueID</xsl:with-param>
                                                <xsl:with-param name="count" select="count(//UniqueID)"/>
                                                <xsl:with-param name="max-count" select="count(//Amount)"/>
                                                <xsl:with-param name="color">bg-purple-500</xsl:with-param>
                                            </xsl:call-template>
                                            <xsl:call-template name="element-count-bar">
                                                <xsl:with-param name="element-name">Currency</xsl:with-param>
                                                <xsl:with-param name="count" select="count(//Currency)"/>
                                                <xsl:with-param name="max-count" select="count(//Amount)"/>
                                                <xsl:with-param name="color">bg-orange-500</xsl:with-param>
                                            </xsl:call-template>
                                        </div>

                                        <!-- Structure Validation -->
                                        <div class="space-y-4">
                                            <h3 class="text-xl font-semibold text-gray-800 mb-4">Structure Validation
                                            </h3>
                                            <xsl:call-template name="structure-check">
                                                <xsl:with-param name="check-name">Root Element</xsl:with-param>
                                                <xsl:with-param name="condition" select="name(.) = 'FundsXML4'"/>
                                                <xsl:with-param name="description">Document has correct root element
                                                </xsl:with-param>
                                            </xsl:call-template>
                                            <xsl:call-template name="structure-check">
                                                <xsl:with-param name="check-name">Control Data Present</xsl:with-param>
                                                <xsl:with-param name="condition" select="boolean(ControlData)"/>
                                                <xsl:with-param name="description">Control data section exists
                                                </xsl:with-param>
                                            </xsl:call-template>
                                            <xsl:call-template name="structure-check">
                                                <xsl:with-param name="check-name">Funds Section Present</xsl:with-param>
                                                <xsl:with-param name="condition" select="boolean(Funds)"/>
                                                <xsl:with-param name="description">Funds section exists</xsl:with-param>
                                            </xsl:call-template>
                                            <xsl:call-template name="structure-check">
                                                <xsl:with-param name="check-name">Schema Declaration</xsl:with-param>
                                                <xsl:with-param name="condition"
                                                                select="boolean(@*[local-name()='noNamespaceSchemaLocation'])"/>
                                                <xsl:with-param name="description">Schema location is declared
                                                </xsl:with-param>
                                            </xsl:call-template>
                                            <xsl:call-template name="structure-check">
                                                <xsl:with-param name="check-name">Portfolio Structure</xsl:with-param>
                                                <xsl:with-param name="condition"
                                                                select="boolean(Funds/Fund/FundDynamicData/Portfolios/Portfolio)"/>
                                                <xsl:with-param name="description">Portfolio structure is present
                                                </xsl:with-param>
                                            </xsl:call-template>
                                        </div>
                                    </div>
                                </div>

                                <!-- Attribute Analysis -->
                                <div class="bg-white border border-gray-200 rounded-2xl p-8 shadow-lg">
                                    <h2 class="text-3xl font-bold text-gray-900 mb-8 flex items-center">
                                        <div class="w-12 h-12 bg-gradient-to-br from-red-500 to-pink-600 text-white rounded-2xl flex items-center justify-center text-xl font-bold mr-4">
                                            🏷️
                                        </div>
                                        Attribute Analysis
                                    </h2>

                                    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                                        <!-- Currency Attributes -->
                                        <div class="bg-blue-50 border border-blue-200 rounded-xl p-6">
                                            <h3 class="font-semibold text-blue-900 mb-4">Currency Attributes (ccy)</h3>
                                            <div class="space-y-2">
                                                <xsl:for-each
                                                        select="//Amount/@ccy[generate-id() = generate-id(key('currencies', .)[1])]">
                                                    <xsl:sort select="."/>
                                                    <div class="flex items-center justify-between">
                                                        <code class="text-sm bg-blue-100 px-2 py-1 rounded">
                                                            <xsl:value-of select="."/>
                                                        </code>
                                                        <span class="text-sm text-blue-700 font-medium">
                                                            <xsl:value-of select="count(key('currencies', .))"/>
                                                        </span>
                                                    </div>
                                                </xsl:for-each>
                                            </div>
                                        </div>

                                        <!-- FreeType Attributes -->
                                        <div class="bg-green-50 border border-green-200 rounded-xl p-6">
                                            <h3 class="font-semibold text-green-900 mb-4">FreeType Attributes</h3>
                                            <div class="space-y-2 text-sm">
                                                <xsl:for-each
                                                        select="//OtherID/@FreeType[generate-id() = generate-id(key('freetypes', .)[1])]">
                                                    <div class="bg-green-100 p-2 rounded">
                                                        <xsl:value-of select="."/>
                                                    </div>
                                                </xsl:for-each>
                                            </div>
                                        </div>

                                        <!-- mulDiv Attributes -->
                                        <div class="bg-purple-50 border border-purple-200 rounded-xl p-6">
                                            <h3 class="font-semibold text-purple-900 mb-4">FX Rate Directions</h3>
                                            <div class="space-y-2">
                                                <xsl:for-each
                                                        select="//FXRate/@mulDiv[generate-id() = generate-id(key('muldivs', .)[1])]">
                                                    <div class="flex items-center justify-between">
                                                        <code class="text-sm bg-purple-100 px-2 py-1 rounded">
                                                            <xsl:value-of select="."/>
                                                        </code>
                                                        <span class="text-sm text-purple-700 font-medium">
                                                            <xsl:value-of select="count(key('muldivs', .))"/>
                                                        </span>
                                                    </div>
                                                </xsl:for-each>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <!-- Footer -->
                        <div class="bg-gradient-to-r from-gray-100 to-gray-200 p-6">
                            <p class="text-sm text-gray-600 text-center">
                                Generated with Tailwind CSS v4 • XML Structure Analysis Report
                            </p>
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

        <div class="space-y-2">
            <div class="flex items-center justify-between">
                <span class="text-sm font-medium text-gray-700">
                    <xsl:value-of select="$element-name"/>
                </span>
                <span class="text-sm font-bold text-gray-900">
                    <xsl:value-of select="$count"/>
                </span>
            </div>
            <div class="w-full bg-gray-200 rounded-full h-2">
                <div class="{$color} h-2 rounded-full transition-all duration-500"
                     style="width: {($count div $max-count) * 100}%"></div>
            </div>
        </div>
    </xsl:template>

    <!-- Template for structure checks -->
    <xsl:template name="structure-check">
        <xsl:param name="check-name"/>
        <xsl:param name="condition"/>
        <xsl:param name="description"/>

        <div class="flex items-center justify-between p-4 bg-gray-50 rounded-lg border">
            <div>
                <div class="font-medium text-gray-800">
                    <xsl:value-of select="$check-name"/>
                </div>
                <div class="text-sm text-gray-600">
                    <xsl:value-of select="$description"/>
                </div>
            </div>
            <div>
                <xsl:choose>
                    <xsl:when test="$condition">
                        <div class="flex items-center px-3 py-1 bg-green-100 text-green-700 rounded-full">
                            <span class="text-lg mr-1">✓</span>
                            <span class="text-sm font-medium">Pass</span>
                        </div>
                    </xsl:when>
                    <xsl:otherwise>
                        <div class="flex items-center px-3 py-1 bg-red-100 text-red-700 rounded-full">
                            <span class="text-lg mr-1">✗</span>
                            <span class="text-sm font-medium">Fail</span>
                        </div>
                    </xsl:otherwise>
                </xsl:choose>
            </div>
        </div>
    </xsl:template>

</xsl:stylesheet>