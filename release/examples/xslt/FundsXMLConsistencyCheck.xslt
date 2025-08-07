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
                <script src="https://cdn.tailwindcss.com/v4.0.0-alpha.13"></script>
            </head>
            <body class="bg-slate-900 flex items-center justify-center min-h-screen p-4">
                <xsl:apply-templates select="/FundsXML4/Funds/Fund"/>
            </body>
        </html>
    </xsl:template>

    <xsl:template match="Fund">
        <xsl:variable name="totalNetAssetValue" select="normalize-space(FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount)"/>
        <xsl:variable name="sumOfPositions" select="sum(FundDynamicData/Portfolios/Portfolio/Positions/Position/TotalValue/Amount)"/>
        <xsl:variable name="difference" select="$totalNetAssetValue - $sumOfPositions"/>

        <div class="max-w-2xl w-full bg-white/10 backdrop-blur-md rounded-xl shadow-2xl p-8 text-white hover:scale-105 transition-transform duration-300">
            <h1 class="text-3xl font-bold mb-2">
                <xsl:value-of select="Names/OfficialName"/>
            </h1>
            <p class="text-gray-300 mb-6">
                Consistency Check for <xsl:value-of select="FundDynamicData/Portfolios/Portfolio/NavDate"/>
            </p>

            <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
                <!-- Reported NAV -->
                <div class="bg-white/5 p-6 rounded-lg">
                    <h2 class="text-sm font-medium text-gray-400 mb-2">Reported Total Net Asset Value</h2>
                    <p class="text-2xl font-semibold">
                        <xsl:value-of select="format-number(number($totalNetAssetValue), '#,##0.00')"/>
                        <span class="text-base font-medium text-gray-400 ml-1"><xsl:value-of select="Currency"/></span>
                    </p>
                </div>

                <!-- Calculated Sum -->
                <div class="bg-white/5 p-6 rounded-lg">
                    <h2 class="text-sm font-medium text-gray-400 mb-2">Calculated Sum of Positions</h2>
                    <p class="text-2xl font-semibold">
                        <xsl:value-of select="format-number($sumOfPositions, '#,##0.00')"/>
                        <span class="text-base font-medium text-gray-400 ml-1"><xsl:value-of select="Currency"/></span>
                    </p>
                </div>
            </div>

            <!-- Difference & Status -->
            <div class="mt-6 pt-6 border-t border-white/10">
                <div class="flex justify-between items-center">
                    <div>
                        <h2 class="text-sm font-medium text-gray-400">Difference</h2>
                        <p class="text-2xl font-semibold">
                            <xsl:attribute name="class">
                                <xsl:choose>
                                    <xsl:when test="round($difference * 100) != 0">
                                        <xsl:text>text-red-400</xsl:text>
                                    </xsl:when>
                                    <xsl:otherwise>
                                        <xsl:text>text-green-400</xsl:text>
                                    </xsl:otherwise>
                                </xsl:choose>
                            </xsl:attribute>
                            <xsl:value-of select="format-number($difference, '#,##0.00')"/>
                        </p>
                    </div>
                    <div>
                        <xsl:choose>
                            <xsl:when test="round($difference * 100) != 0">
                                <span class="inline-flex items-center px-4 py-2 bg-red-500/20 text-red-300 text-sm font-medium rounded-full">
                                    <svg width="16" height="16" class="mr-2 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
                                    Mismatch
                                </span>
                            </xsl:when>
                            <xsl:otherwise>
                                <span class="inline-flex items-center px-4 py-2 bg-green-500/20 text-green-300 text-sm font-medium rounded-full">
                                     <svg width="16" height="16" class="mr-2 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
                                    Consistent
                                </span>
                            </xsl:otherwise>
                        </xsl:choose>
                    </div>
                </div>
            </div>
        </div>
    </xsl:template>

</xsl:stylesheet>