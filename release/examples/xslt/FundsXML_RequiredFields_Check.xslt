<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="html" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <xsl:template match="/FundsXML4">
        <html lang="de">
            <head>
                <meta charset="UTF-8"/>
                <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                <title>FundsXML - Required Fields Check</title>
                <script src="https://cdn.tailwindcss.com"></script>
            </head>
            <body class="bg-gray-50 min-h-screen">
                <div class="container mx-auto px-4 py-8">
                    <div class="bg-white rounded-lg shadow-lg p-6">
                        <h1 class="text-3xl font-bold text-gray-900 mb-6 border-b-2 border-blue-600 pb-3">
                            FundsXML Required Fields Validation
                        </h1>

                        <div class="mb-6 bg-blue-50 border border-blue-200 rounded-lg p-4">
                            <p class="text-blue-800">
                                <strong>Document ID:</strong>
                                <xsl:value-of select="ControlData/UniqueDocumentID"/>
                            </p>
                            <p class="text-blue-800">
                                <strong>Generated:</strong>
                                <xsl:value-of select="ControlData/DocumentGenerated"/>
                            </p>
                        </div>

                        <div class="space-y-6">
                            <!-- Control Data Validation -->
                            <div class="bg-white border border-gray-200 rounded-lg p-6">
                                <h2 class="text-xl font-semibold text-gray-800 mb-4 flex items-center">
                                    <span class="w-6 h-6 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center text-sm font-bold mr-2">
                                        1
                                    </span>
                                    Control Data Validation
                                </h2>
                                <div class="space-y-2">
                                    <xsl:call-template name="check-field">
                                        <xsl:with-param name="field-name">UniqueDocumentID</xsl:with-param>
                                        <xsl:with-param name="field-value" select="ControlData/UniqueDocumentID"/>
                                    </xsl:call-template>
                                    <xsl:call-template name="check-field">
                                        <xsl:with-param name="field-name">DocumentGenerated</xsl:with-param>
                                        <xsl:with-param name="field-value" select="ControlData/DocumentGenerated"/>
                                    </xsl:call-template>
                                    <xsl:call-template name="check-field">
                                        <xsl:with-param name="field-name">ContentDate</xsl:with-param>
                                        <xsl:with-param name="field-value" select="ControlData/ContentDate"/>
                                    </xsl:call-template>
                                    <xsl:call-template name="check-field">
                                        <xsl:with-param name="field-name">DataSupplier Name</xsl:with-param>
                                        <xsl:with-param name="field-value" select="ControlData/DataSupplier/Name"/>
                                    </xsl:call-template>
                                </div>
                            </div>

                            <!-- Fund Basic Data Validation -->
                            <xsl:for-each select="Funds/Fund">
                                <div class="bg-white border border-gray-200 rounded-lg p-6">
                                    <h2 class="text-xl font-semibold text-gray-800 mb-4 flex items-center">
                                        <span class="w-6 h-6 bg-green-100 text-green-600 rounded-full flex items-center justify-center text-sm font-bold mr-2">
                                            2
                                        </span>
                                        Fund Basic Data:
                                        <xsl:value-of select="Names/OfficialName"/>
                                    </h2>
                                    <div class="space-y-2">
                                        <xsl:call-template name="check-field">
                                            <xsl:with-param name="field-name">LEI</xsl:with-param>
                                            <xsl:with-param name="field-value" select="Identifiers/LEI"/>
                                        </xsl:call-template>
                                        <xsl:call-template name="check-field">
                                            <xsl:with-param name="field-name">Official Name</xsl:with-param>
                                            <xsl:with-param name="field-value" select="Names/OfficialName"/>
                                        </xsl:call-template>
                                        <xsl:call-template name="check-field">
                                            <xsl:with-param name="field-name">Currency</xsl:with-param>
                                            <xsl:with-param name="field-value" select="Currency"/>
                                        </xsl:call-template>
                                        <xsl:call-template name="check-field">
                                            <xsl:with-param name="field-name">Inception Date</xsl:with-param>
                                            <xsl:with-param name="field-value" select="FundStaticData/InceptionDate"/>
                                        </xsl:call-template>
                                    </div>
                                </div>

                                <!-- Portfolio Positions Check -->
                                <div class="bg-white border border-gray-200 rounded-lg p-6">
                                    <h2 class="text-xl font-semibold text-gray-800 mb-4 flex items-center">
                                        <span class="w-6 h-6 bg-yellow-100 text-yellow-600 rounded-full flex items-center justify-center text-sm font-bold mr-2">
                                            3
                                        </span>
                                        Portfolio Positions
                                    </h2>
                                    <div class="grid gap-4">
                                        <p class="text-gray-600">
                                            Total Positions:
                                            <span class="font-semibold text-gray-800">
                                                <xsl:value-of
                                                        select="count(FundDynamicData/Portfolios/Portfolio/Positions/Position)"/>
                                            </span>
                                        </p>

                                        <div class="space-y-2">
                                            <xsl:for-each
                                                    select="FundDynamicData/Portfolios/Portfolio/Positions/Position[position() &lt;= 10]">
                                                <div class="flex items-center justify-between p-3 bg-gray-50 rounded border">
                                                    <span class="text-sm text-gray-700">
                                                        Position ID:
                                                        <xsl:value-of select="UniqueID"/>
                                                    </span>
                                                    <div class="flex space-x-2">
                                                        <xsl:choose>
                                                            <xsl:when
                                                                    test="UniqueID and Currency and TotalValue/Amount">
                                                                <span class="px-2 py-1 bg-green-100 text-green-700 text-xs rounded">
                                                                    ✓ Valid
                                                                </span>
                                                            </xsl:when>
                                                            <xsl:otherwise>
                                                                <span class="px-2 py-1 bg-red-100 text-red-700 text-xs rounded">
                                                                    ✗ Missing Data
                                                                </span>
                                                            </xsl:otherwise>
                                                        </xsl:choose>
                                                    </div>
                                                </div>
                                            </xsl:for-each>
                                        </div>
                                    </div>
                                </div>
                            </xsl:for-each>
                        </div>

                        <div class="mt-8 p-4 bg-gray-100 rounded-lg">
                            <p class="text-sm text-gray-600 text-center">
                                Generated with Tailwind CSS v4 • FreeXmlToolkit
                            </p>
                        </div>
                    </div>
                </div>
            </body>
        </html>
    </xsl:template>

    <!-- Template for field validation -->
    <xsl:template name="check-field">
        <xsl:param name="field-name"/>
        <xsl:param name="field-value"/>
        <div class="flex items-center justify-between p-3 bg-gray-50 rounded border">
            <span class="text-sm font-medium text-gray-700">
                <xsl:value-of select="$field-name"/>
            </span>
            <div class="flex items-center space-x-2">
                <xsl:choose>
                    <xsl:when test="$field-value and string-length($field-value) &gt; 0">
                        <span class="px-2 py-1 bg-green-100 text-green-700 text-xs rounded font-medium">✓ Present</span>
                        <span class="text-xs text-gray-500 max-w-xs truncate">
                            <xsl:value-of select="$field-value"/>
                        </span>
                    </xsl:when>
                    <xsl:otherwise>
                        <span class="px-2 py-1 bg-red-100 text-red-700 text-xs rounded font-medium">✗ Missing</span>
                    </xsl:otherwise>
                </xsl:choose>
            </div>
        </div>
    </xsl:template>

</xsl:stylesheet>