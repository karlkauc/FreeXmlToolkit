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

                <!--
                <link rel="stylesheet"
                      href="https://cdnjs.cloudflare.com/ajax/libs/bootstrap/5.2.3/css/bootstrap.min.css"
                      integrity="sha512-SbiR/eusphKoMVVXysTKG/7VseWii+Y3FdHrt0EpKgpToZeemhqHeZeLWLhJutz/2ut2Vw1uQEj2MbRF+TVBUA=="
                      crossorigin="anonymous" referrerpolicy="no-referrer"/>
                <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/prism/9000.0.1/themes/prism.min.css"
                      integrity="sha512-/mZ1FHPkg6EKcxo0fKXF51ak6Cr2ocgDi5ytaTBjsQZIH/RNs6GF6+oId/vPe3eJB836T36nXwVh/WBl/cWT4w=="
                      crossorigin="anonymous" referrerpolicy="no-referrer"/>
                -->

                <link rel="stylesheet" href="freeXmlToolkit.css"/>
                <link rel="stylesheet" href="prism.css"/>
            </head>
            <body class="container-fluid">
                <h1>Analyzing File</h1>
                <table class="table">
                    <tr>
                        <th>Report Created</th>
                        <td>{current-dateTime()}</td>
                    </tr>
                    <tr>
                        <th>Filename:</th>
                        <td>
                            <xsl:value-of select="tokenize(base-uri(.), '/')[last()]" disable-output-escaping="yes"/>
                        </td>
                    </tr>
                    <tr>
                        <th class="w-25"># Funds:</th>
                        <td>
                            <xsl:value-of select="count(FundsXML4/Funds/Fund)"/>
                        </td>
                    </tr>
                    <tr>
                        <th class="w-25"># ShareClasses:</th>
                        <td>
                            <xsl:if test="count(//SingleFund/ShareClasses/ShareClass) = 0">
                                <xsl:attribute name="class">bg-danger bg-gradient</xsl:attribute>
                            </xsl:if>
                            <xsl:value-of select="count(//SingleFund/ShareClasses/ShareClass)"/>
                        </td>
                    </tr>
                    <tr>
                        <th class="w-25"># Asset Master Data:</th>
                        <td>
                            <xsl:value-of select="count(FundsXML4/AssetMasterData/Asset)"/>
                        </td>
                    </tr>
                </table>
                <hr/>
                <div id="list_errors">
                    <h1>ERROR LIST</h1>
                </div>
                <hr/>

                <div id="list_warnings">
                    <h1>WARNING LIST</h1>
                </div>
                <hr/>

                <xsl:apply-templates select="FundsXML4/ControlData"/>
                <hr/>

                <h1>FundList</h1>
                <ol>
                    <xsl:for-each select="FundsXML4/Funds/Fund">
                        <li>
                            <a href="#{generate-id(.)}">{Names/OfficialName/text()}</a>
                            <ol>
                                <xsl:for-each select="SingleFund/ShareClasses/ShareClass">
                                    <li>
                                        <a href="#{Identifiers/ISIN}">{Identifiers/ISIN}</a>
                                    </li>
                                </xsl:for-each>
                            </ol>
                        </li>
                    </xsl:for-each>
                </ol>
                <hr/>

                <div class="ps-1 fs-4">
                    <a href="#AssetMasterData">
                        <svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" fill="currentColor"
                             class="bi bi-link" viewBox="0 0 16 16">
                            <path d="M6.354 5.5H4a3 3 0 0 0 0 6h3a3 3 0 0 0 2.83-4H9c-.086 0-.17.01-.25.031A2 2 0 0 1 7 10.5H4a2 2 0 1 1 0-4h1.535c.218-.376.495-.714.82-1z"/>
                            <path d="M9 5.5a3 3 0 0 0-2.83 4h1.098A2 2 0 0 1 9 6.5h3a2 2 0 1 1 0 4h-1.535a4.02 4.02 0 0 1-.82 1H12a3 3 0 1 0 0-6H9z"/>
                        </svg>
                        Asset Master Data
                    </a>
                </div>

                <hr/>

                <xsl:apply-templates select="FundsXML4/Funds"/>
                <hr/>
                <xsl:apply-templates select="FundsXML4/AssetMasterData"/>
                <hr/>

                <p>
                    File Comment:
                    <pre>
                        <xsl:value-of select="comment()"/>
                    </pre>
                </p>

                <script src="bootstrap.bundle.min.js"></script>
                <script src="prism.js"></script>

                <!--
                <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/9000.0.1/prism.min.js"
                        integrity="sha512-UOoJElONeUNzQbbKQbjldDf9MwOHqxNz49NNJJ1d90yp+X9edsHyJoAs6O4K19CZGaIdjI5ohK+O2y5lBTW6uQ=="
                        crossorigin="anonymous" referrerpolicy="no-referrer"/>
                <script src="https://cdnjs.cloudflare.com/ajax/libs/bootstrap/5.2.3/js/bootstrap.bundle.min.js"
                        integrity="sha512-i9cEfJwUwViEPFKdC1enz4ZRGBj8YQo6QByFTF92YXHi7waCqyexvRD75S5NVTsSiTv7rKWqG9Y5eFxmRsOn0A=="
                        crossorigin="anonymous" referrerpolicy="no-referrer"/>
                    -->

                <script src="replaceNodes.js"/>
                <script src="prism.js"/>
            </body>
        </html>
    </xsl:template>

    <xsl:template match="ControlData">
        <p>
            <h1>Control Data</h1>
            <table class="table table-bordered table-striped table-hover">
                <tr>
                    <th class="w-25">UniqueDocumentID:</th>
                    <td>
                        <xslt:value-of select="UniqueDocumentID"/>
                    </td>
                </tr>
                <tr>
                    <th>DocumentGenerated:</th>
                    <td>
                        <xslt:value-of select="DocumentGenerated"/>
                    </td>
                </tr>
                <tr>
                    <th>Version:</th>
                    <td>
                        <xsl:if test="not(Version)">
                            <span id="WARNING_{generate-id(.)}" data-error-message="Missing FundsXML Version"
                                  class="badge eg_status--orange">Missing FundsXML Version
                            </span>
                        </xsl:if>
                        <xsl:value-of select="Version"/>
                    </td>
                </tr>
                <tr>
                    <th>ContentDate:</th>
                    <td>
                        <xslt:value-of select="ContentDate"/>
                    </td>
                </tr>
                <tr>
                    <th>DataSupplier:</th>
                    <td>
                        <xslt:value-of select="DataSupplier/Short"/> |
                        <xslt:value-of select="DataSupplier/Name"/>
                    </td>
                </tr>
                <tr>
                    <th>Contact:</th>
                    <td>
                        <xsl:choose>
                            <xsl:when test="count(DataSupplier/Contact) = 0">
                                <span class="eg_status eg_status--orange">Missing</span>
                            </xsl:when>
                            <xsl:otherwise>
                                <a>
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
                <tr>
                    <th>Data Operation:</th>
                    <td>
                        <xsl:if test="not(DataOperation)">
                            <span id="ERROR_{generate-id(.)}" data-error-message="Data Operation Missing"
                                  class="badge eg_status--red">Missing
                            </span>
                        </xsl:if>
                        <xsl:value-of select="DataOperation"/>
                    </td>
                </tr>
                <tr>
                    <th>RelatedDocumentIDs:</th>
                    <td>
                        <xsl:for-each select="RelatedDocumentIDs">
                            <xsl:value-of select="RelatedDocumentID"/>
                            <br/>
                        </xsl:for-each>
                    </td>
                </tr>
            </table>
        </p>
    </xsl:template>

    <xsl:template match="Funds">
        <xsl:for-each select="Fund">
            <xsl:variable name="fundCCY" select="Currency"/>

            <p id="{generate-id(.)}">
                <div class="{concat('block block',position() mod 3)}">
                    <h1>[#{position()}] Fund Name: {Names/OfficialName/text()}</h1>

                    <h2>Fund Static Data</h2>
                    <table class="table table-striped">
                        <tr>
                            <th class="w-25">Identifier</th>
                            <td>
                                <xsl:attribute name="class">
                                    <xsl:value-of select="if (count(Identifiers/*) le 1) then 'bg-danger' else '' "/>
                                </xsl:attribute>
                                <xsl:for-each select="Identifiers">
                                    <div class="row">
                                        <xsl:if test="LEI">
                                            <span class="fw-bold">LEI:
                                                <xsl:value-of select="LEI"/>
                                            </span>
                                        </xsl:if>
                                        <xsl:if test="ISIN">
                                            <span>ISIN:
                                                <xsl:value-of select="ISIN"/>
                                            </span>
                                        </xsl:if>
                                        <xsl:for-each select="OtherID">
                                            <span class="fs-small">Other ID
                                                <xsl:value-of select="concat('[', attribute(), ']: ', .)"/>
                                            </span>
                                        </xsl:for-each>
                                    </div>
                                </xsl:for-each>
                            </td>
                        </tr>
                        <tr>
                            <th>Fund CCY</th>
                            <td>
                                <xsl:value-of select="Currency"/>
                            </td>
                        </tr>
                        <tr>
                            <th>InceptionDate:</th>
                            <td>
                                <xsl:choose>
                                    <xsl:when
                                            test="not(FundStaticData/InceptionDate)">
                                        <span class="eg_status eg_status--orange">Missing</span>
                                    </xsl:when>
                                    <xsl:otherwise>
                                        <xsl:value-of
                                                select="FundStaticData/InceptionDate"/>
                                    </xsl:otherwise>
                                </xsl:choose>
                            </td>
                        </tr>
                        <tr>
                            <th>Fund Manager</th>
                            <td>
                                <div class="container" style="padding-left:0; margin-left:0;">
                                    <div class="row">
                                        <div class="col col-2">
                                            Name:
                                        </div>
                                        <div class="col">
                                            <xsl:choose>
                                                <xsl:when
                                                        test="not(FundStaticData/PortfolioManagers/PortfolioManager/Name)">
                                                    <span class="eg_status eg_status--orange">Missing</span>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    [<xsl:value-of
                                                        select="FundStaticData/PortfolioManagers/PortfolioManager/Name"/>]
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </div>
                                    </div>
                                    <div class="row">
                                        <div class="col col-2">
                                            Start Date:
                                        </div>
                                        <div class="col">
                                            <xsl:choose>
                                                <xsl:when
                                                        test="not(FundStaticData/PortfolioManagers/PortfolioManager/StartDate)">
                                                    <span class="eg_status eg_status--orange">Missing</span>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    [<xsl:value-of
                                                        select="FundStaticData/PortfolioManagers/PortfolioManager/StartDate"/>]
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </div>
                                    </div>
                                    <div class="row">
                                        <div class="col col-2">
                                            Role:
                                        </div>
                                        <div class="col">
                                            <xsl:choose>
                                                <xsl:when
                                                        test="not(FundStaticData/PortfolioManagers/PortfolioManager/Role)">
                                                    <span class="eg_status eg_status--orange">Missing</span>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    [<xsl:value-of
                                                        select="FundStaticData/PortfolioManagers/PortfolioManager/Role"/>]
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </div>
                                    </div>
                                </div>
                            </td>
                        </tr>
                        <tr>
                            <th>Fund Legal Type</th>
                            <td>
                                <xsl:value-of select="FundStaticData/ListedLegalStructure"/>
                            </td>
                        </tr>
                    </table>

                    <h2>Fund Dynamic Data</h2>
                    <hr/>

                    <h3>Fund Total Asset Value</h3>
                    <xsl:variable name="sumOfShareClassVolume"
                                  select="sum(SingleFund/ShareClasses/ShareClass/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount[@ccy=$fundCCY])"/>

                    <table class="table table-striped">
                        <xsl:for-each select="FundDynamicData/TotalAssetValues/TotalAssetValue">
                            <tr>
                                <th>Nav Date</th>
                                <td>
                                    <xsl:value-of select="NavDate"/>
                                </td>
                            </tr>

                            <tr>
                                <th>Nature</th>
                                <td>
                                    <xsl:value-of select="TotalAssetNature"/>
                                </td>
                            </tr>
                            <tr>
                                <th>TotalAssetValue (Fund Volume) in Fund CCY</th>
                                <td>
                                    <xsl:value-of select="../../../Currency"/>:
                                    <xsl:value-of
                                            select="format-number(TotalNetAssetValue/Amount[@ccy=$fundCCY], '#,##0.00')"/>
                                </td>
                            </tr>
                            <tr>
                                <th>Sum of ShareClass Volumes (in Funds CCY)</th>
                                <td>
                                    <xsl:value-of select="../../../Currency"/>:
                                    <xsl:value-of
                                            select="format-number($sumOfShareClassVolume, '#,##0.00')"/>

                                </td>
                            </tr>
                        </xsl:for-each>
                    </table>


                    <h3>Portfolio Data [Fund Level]:</h3>
                    <xsl:if test="not(FundDynamicData/Portfolios)">
                        <span class="fw-bold">NO PORTFOLIO DATA FOUND!</span>
                    </xsl:if>
                    <hr/>

                    <xsl:for-each select="FundDynamicData/Portfolios">
                        <xsl:call-template name="Portfolio">
                            <xsl:with-param name="ccy" select="../../Currency"/>
                            <xsl:with-param name="totalAmount"
                                            select="../TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount[@ccy=$fundCCY]"/>
                        </xsl:call-template>
                    </xsl:for-each>

                    <h2>ShareClasses:</h2>
                    <xsl:for-each select="SingleFund/ShareClasses/ShareClass">
                        <h3 id="{Identifiers/ISIN}">
                            <xsl:value-of select="concat('#', position(), '|', count(../*))"/>
                        </h3>
                        <xsl:variable name="shareClassCcy" select="Currency"/>
                        <table class="table table-bordered table-striped table-hover">
                            <tbody>
                                <tr>
                                    <th>ISIN</th>
                                    <td class="w-50">
                                        <xsl:value-of select="Identifiers/ISIN/text()"/>
                                    </td>
                                </tr>
                                <tr>
                                    <th>CCY</th>
                                    <td>
                                        <xsl:value-of select="$shareClassCcy"/>
                                    </td>
                                </tr>
                                <tr>
                                    <th>ShareClass Volumen Datum</th>
                                    <td>
                                        <xsl:value-of select="TotalAssetValues/TotalAssetValue/NavDate/text()"/>
                                    </td>
                                </tr>
                                <tr>
                                    <th>Total Net Asset Value</th>
                                    <td class="text-end">
                                        <xsl:value-of
                                                select="format-number(TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount[@ccy=$shareClassCcy], '#,#00.00')"/>
                                    </td>
                                </tr>
                                <tr>
                                    <th>Summe ShareClass Positionen (in ShareClass CCY)</th>
                                    <td class="text-end">
                                        <xsl:value-of
                                                select="format-number(sum(Portfolios/Portfolio/Positions/Position/TotalValue/Amount[@ccy=$shareClassCcy]), '#,#00.00')"/>
                                    </td>
                                </tr>

                                <xsl:if test="Portfolios">
                                    <!--
                                    <xsl:variable name="s1" select="sum(Portfolios/Portfolio/Positions/Position/TotalValue/Amount[@ccy=$shareClassCcy])" />
                                    <xsl:variable name="diff"
                                                  select="TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount[@ccy=$shareClassCcy] - $s1"/>
                                    -->
                                    <xsl:variable name="diff"
                                                  select="0"/>
                                    <tr>
                                        <xsl:choose>
                                            <xsl:when test="abs($diff) > 1">
                                                <xsl:attribute name="class">bg-danger</xsl:attribute>
                                                <xsl:attribute name="id">ERROR_{generate-id(.)}</xsl:attribute>
                                                <xsl:attribute name="data-error-message">Difference in Volume
                                                    [{format-number($diff, '#,##0.00')}] not in tolerance for Shareclass
                                                    {Identifiers/ISIN/text()}
                                                </xsl:attribute>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <xsl:attribute name="class">bg-success</xsl:attribute>
                                            </xsl:otherwise>
                                        </xsl:choose>

                                        <th>DIFF</th>
                                        <td class="text-end">
                                            <xsl:value-of
                                                    select="format-number($diff, '#,#00.00')"/>
                                        </td>
                                    </tr>
                                </xsl:if>
                            </tbody>
                        </table>

                        <h3>Portfolio Data [ShareClass Level]:</h3>
                        <xsl:if test="not(Portfolios)">
                            <span class="fs-5 pe-3">No portfolio data on shareclass found</span>
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
            </p>
        </xsl:for-each>
    </xsl:template>

    <xsl:template name="Portfolio">
        <xsl:param name="ccy"/>
        <xsl:param name="totalAmount"/>

        <xsl:for-each select="Portfolio">

            <div class="d-flex flex-row mb-3">
                <div class="pe-2">
                    <table class="table table-bordered table-responsive" style="width:auto;">
                        <tr>
                            <th scope="row">Portfolio Date:</th>
                            <td class="text-end">
                                <xsl:value-of select="NavDate"/>
                            </td>
                        </tr>
                        <tr>
                            <th scope="row">Position Count:</th>
                            <td class="text-end">
                                <xsl:value-of select="count(Positions/Position)"/>
                            </td>
                        </tr>
                    </table>

                </div>
                <div class="pe-2">
                    <table class="table table-responsive" style="width:auto;">
                        <tr>
                            <th>Currency Aggregation Total Value</th>
                            <td>
                                <table class="table table-sm table-striped table-responsive">
                                    <thead>
                                        <th>CCY</th>
                                        <th class="text-end">%</th>
                                    </thead>
                                    <tbody>
                                        <xsl:for-each-group select="Positions/Position" group-by="Currency">
                                            <tr>
                                                <td><xsl:value-of select="current-grouping-key()"/>:
                                                </td>
                                                <td class="text-end">
                                                    <xsl:value-of
                                                            select="format-number(sum(current-group()/TotalPercentage), '#,##0.00')"/>
                                                </td>
                                            </tr>
                                        </xsl:for-each-group>
                                    </tbody>
                                </table>
                            </td>
                        </tr>
                    </table>
                </div>
            </div>

            <xsl:variable name="myId" select="generate-id(.)"/>
            <div class="accordion" id="accordeonDiv">
                <div class="accordion-item">
                    <h2 class="accordion-header accordion-bodyNoBorderTop" id="panelsStayOpen-headingOne">
                        <button class="accordion-button" type="button" data-bs-toggle="collapse"
                                data-bs-target="#{$myId}" aria-expanded="true"
                                aria-controls="panelsStayOpen-collapseOne">
                            <svg xmlns="http://www.w3.org/2000/svg" width="25" height="25" fill="currentColor"
                                 class="bi bi-bar-chart-steps" viewBox="0 0 16 16">
                                <path d="M.5 0a.5.5 0 0 1 .5.5v15a.5.5 0 0 1-1 0V.5A.5.5 0 0 1 .5 0zM2 1.5a.5.5 0 0 1 .5-.5h4a.5.5 0 0 1 .5.5v1a.5.5 0 0 1-.5.5h-4a.5.5 0 0 1-.5-.5v-1zm2 4a.5.5 0 0 1 .5-.5h7a.5.5 0 0 1 .5.5v1a.5.5 0 0 1-.5.5h-7a.5.5 0 0 1-.5-.5v-1zm2 4a.5.5 0 0 1 .5-.5h6a.5.5 0 0 1 .5.5v1a.5.5 0 0 1-.5.5h-6a.5.5 0 0 1-.5-.5v-1zm2 4a.5.5 0 0 1 .5-.5h7a.5.5 0 0 1 .5.5v1a.5.5 0 0 1-.5.5h-7a.5.5 0 0 1-.5-.5v-1z"/>
                            </svg>
                            <span class="fs-4 padding-right: 10px;">Portfolio Data</span>
                        </button>
                    </h2>
                    <div id="{$myId}" class="accordion-collapse collapse show"
                         aria-labelledby="panelsStayOpen-headingOne">
                        <div class="accordion-body accordion-bodyNoBorder">
                            <table class="table table-bordered table-striped table-hover">
                                <thead class="sticky-md-top sticky-top-eam">
                                    <th>#</th>
                                    <th>UniqueID</th>
                                    <th>Currency</th>
                                    <th>
                                        <xsl:variable name="diff"
                                                      select="sum(Positions/Position/TotalValue/Amount[@ccy=$ccy]) - $totalAmount"/>
                                        <xsl:attribute name="class">
                                            <xsl:value-of
                                                    select="if (abs($diff) > 1) then 'bg-danger' else 'bg-success' "/>
                                        </xsl:attribute>

                                        &#x2211; TotalValue:
                                        <span style="float:right;"><xsl:value-of select="$ccy"/>:
                                            <xsl:value-of
                                                    select="format-number(sum(Positions/Position/TotalValue/Amount[@ccy=$ccy]), '#,##0.00')"/>
                                            (
                                            <span class="badge eg_status--blue">
                                                <xsl:value-of select="format-number($diff, '#,##0.00')"/>
                                            </span>
                                            )
                                        </span>
                                    </th>
                                    <th>
                                        <xsl:variable name="totalPercent"
                                                      select="sum(Positions/Position/TotalPercentage)"/>
                                        <xsl:choose>
                                            <xsl:when test="($totalPercent gt 101 or $totalPercent lt 99)">
                                                <xsl:attribute name="class">bg-danger</xsl:attribute>
                                                <xsl:attribute name="id">ERROR_{generate-id(.)}</xsl:attribute>
                                                <xsl:attribute name="data-error-message">Total Percentage
                                                    {format-number($totalPercent, '#0.00')} not in tolerance
                                                </xsl:attribute>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <xsl:attribute name="class">bg-success</xsl:attribute>
                                            </xsl:otherwise>
                                        </xsl:choose>

                                        &#x2211; TotalPercentage:
                                        <span style="float:right;">
                                            <span class="eg_status eg_status--blue">
                                                <xsl:value-of select="format-number($totalPercent, '#0.00')"/>
                                            </span>
                                        </span>
                                    </th>
                                    <th>FXRates</th>
                                    <th>Detail</th>
                                </thead>
                                <xsl:for-each select="Positions/Position">
                                    <xsl:variable name="assetCcy" select="Currency"/>
                                    <tr>
                                        <th class="text-center">
                                            <xsl:value-of select="position()"/>
                                        </th>

                                        <td>
                                            <xsl:variable name="anker" select="UniqueID"/>
                                            <xsl:variable name="fontSize"
                                                          select="if (string-length(UniqueID) gt 15) then 'fs-8' else 'fs-5'"/>
                                            <a href="#{$anker}" class="{$fontSize}">
                                                <xsl:value-of select="UniqueID"/>
                                            </a>
                                            <br/>
                                            <xsl:variable name="uniqueId" select="UniqueID"/>
                                            <span class="fs-small text-break">Name:
                                                <xsl:choose>
                                                    <xsl:when
                                                            test="string-length(/FundsXML4/AssetMasterData/Asset[UniqueID=$uniqueId]/Name) gt 15">
                                                        <abbr title="{/FundsXML4/AssetMasterData/Asset[UniqueID=$uniqueId]/Name}">
                                                            {substring(/FundsXML4/AssetMasterData/Asset[UniqueID=$uniqueId]/Name,
                                                            1,
                                                            15)}...
                                                        </abbr>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        {/FundsXML4/AssetMasterData/Asset[UniqueID=$uniqueId]/Name}
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </span>
                                            <br/>
                                            <xsl:if test="/FundsXML4/AssetMasterData/Asset[UniqueID=$uniqueId]/Identifiers/ISIN">
                                                <p class="fs-small">ISIN:
                                                    {/FundsXML4/AssetMasterData/Asset[UniqueID=$uniqueId]/Identifiers/ISIN}
                                                </p>
                                            </xsl:if>
                                        </td>
                                        <td>
                                            {Currency}
                                        </td>
                                        <td>
                                            <div class="row row-cols-2">
                                                <div class="col text-start">(Pos. CCY) <xsl:value-of
                                                        select="$assetCcy"/>:
                                                </div>
                                                <div class="col text-end">
                                                    <xsl:choose>
                                                        <xsl:when test="TotalValue/Amount[@ccy=$assetCcy]">
                                                            <xsl:value-of
                                                                    select="format-number(TotalValue/Amount[@ccy=$assetCcy], '#,##0.00')"/>
                                                        </xsl:when>
                                                        <xsl:otherwise>
                                                            <span class="eg_status eg_status--red">Missing</span>
                                                        </xsl:otherwise>
                                                    </xsl:choose>
                                                </div>
                                            </div>
                                            <div class="row row-cols-2">
                                                <div class="col text-start">(Port. CCY) <xsl:value-of select="$ccy"/>:
                                                </div>
                                                <div class="col text-end">
                                                    <xsl:choose>
                                                        <xsl:when test="TotalValue/Amount[@ccy=$ccy]">
                                                            <xsl:value-of
                                                                    select="format-number(TotalValue/Amount[@ccy=$ccy], '#,##0.00')"/>
                                                        </xsl:when>
                                                        <xsl:otherwise>
                                                            <span class="badge eg_status--red">Missing</span>
                                                        </xsl:otherwise>
                                                    </xsl:choose>
                                                </div>
                                            </div>

                                            <xsl:for-each
                                                    select="TotalValue/Amount[@ccy != $ccy and @ccy != $assetCcy]">
                                                <div class="row row-cols-2">
                                                    <div class="col text-start"><xsl:value-of select="@ccy"/>:
                                                    </div>
                                                    <div class="col text-end">
                                                        <xsl:value-of select="format-number(., '#,##0.00')"/>
                                                    </div>
                                                </div>
                                            </xsl:for-each>
                                            <xsl:choose>
                                                <xsl:when test="not(TotalValue/Amount[@ccy=$ccy])">
                                                    <div class="row">
                                                        <span class="eg_status eg_status--red">Missing TotalValue in
                                                            Portfolio CCY
                                                        </span>
                                                    </div>
                                                </xsl:when>
                                                <xsl:when test="not(TotalValue/Amount[@ccy=$assetCcy])">
                                                    <div class="row">
                                                        <span class="eg_status eg_status--red">Missing TotalValue in
                                                            Asset CCY
                                                        </span>
                                                    </div>
                                                </xsl:when>
                                            </xsl:choose>

                                            <xsl:variable name="posNumbers"
                                                          select="count(TotalValue/Amount[number(.) gt 0])"
                                                          as="xs:integer"/>
                                            <xsl:variable name="negNumbers"
                                                          select="count(TotalValue/Amount[number(.) lt 0])"
                                                          as="xs:integer"/>
                                            <xsl:if test="$negNumbers gt 0 and $posNumbers gt 0">
                                                <div class="row">
                                                    <span id="ERROR_{generate-id(.)}"
                                                          data-error-message="ERROR: Negativ and Positiv Value"
                                                          class="badge eg_status--red">ERROR: NEGATIVE AND POSITIVE
                                                        AMOUNTS
                                                    </span>
                                                </div>
                                            </xsl:if>
                                        </td>
                                        <td class="text-end">
                                            <xsl:choose>
                                                <xsl:when test="not(TotalPercentage)">
                                                    <span class="eg_status eg_status--orange"
                                                          data-error-message="Missing Total Percentage">Missing
                                                    </span>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <xsl:value-of select="format-number(TotalPercentage, '#,##0.000')"/>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </td>
                                        <td>
                                            <xsl:for-each select="FXRates">
                                                <xsl:for-each select="FXRate">
                                                    <div class="row">
                                                        <div class="col">
                                                            (<xsl:value-of select="@mulDiv"/>)
                                                            <xsl:value-of
                                                                    select="@fromCcy"/>/<xsl:value-of select="@toCcy"/>
                                                        </div>
                                                        <div class="col text-end">
                                                            <xsl:value-of select="."/>
                                                        </div>

                                                        <xsl:if test="@fromCcy eq @toCcy and number(.) ne 1">
                                                            <span id="ERROR_{generate-id(.)}"
                                                                  data-error-message="ERROR: FX for same Currency not 1 {@fromCcy}/{@toCcy}: [{.}]"
                                                                  class="badge eg_status--red">ERROR: FX for same
                                                                Currency not 1 {@fromCcy}/{@toCcy}: [{.}]
                                                            </span>
                                                        </xsl:if>
                                                    </div>
                                                </xsl:for-each>
                                            </xsl:for-each>
                                        </td>
                                        <td>
                                            <div class="container" style="padding: 0">
                                                <div class="row">
                                                    <xsl:apply-templates select="Equity">
                                                        <xsl:with-param name="portfolioCcy" select="$ccy"/>
                                                    </xsl:apply-templates>

                                                    <xsl:apply-templates select="Bond">
                                                        <xsl:with-param name="portfolioCcy" select="$ccy"/>
                                                    </xsl:apply-templates>

                                                    <xsl:apply-templates select="ShareClass"/>

                                                    <xsl:apply-templates select="Warrant"/>

                                                    <xsl:apply-templates select="Certificate"/>

                                                    <xsl:apply-templates select="Option"/>

                                                    <xsl:apply-templates select="Future">
                                                        <xsl:with-param name="portfolioCcy" select="$ccy"/>
                                                    </xsl:apply-templates>

                                                    <xsl:apply-templates select="FXForward"/>

                                                    <xsl:apply-templates select="Swap"/>

                                                    <xsl:apply-templates select="Repo"/>

                                                    <xsl:apply-templates select="FixedTimeDeposit"/>

                                                    <xsl:apply-templates select="CallMoney"/>

                                                    <xsl:apply-templates select="Account"/>

                                                    <xsl:apply-templates select="Fee"/>

                                                    <xsl:apply-templates select="RealEstate"/>

                                                    <xsl:apply-templates select="REIT"/>

                                                    <xsl:apply-templates select="Loan"/>

                                                    <xsl:apply-templates select="Right"/>

                                                    <xsl:apply-templates select="Commodity"/>

                                                    <xsl:apply-templates select="PrivateEquity"/>

                                                    <xsl:apply-templates select="CommercialPaper"/>

                                                </div>
                                                <div class="row">
                                                    <div class="fs-small">
                                                        <xsl:if test="$renderXMLContent">
                                                            <details>
                                                                <summary>Original XML</summary>
                                                                <p>
                                                                    <script type="text/plain" class="language-xml">
                                                                            <xsl:copy-of select="node()"/>
                                                                    </script>
                                                                </p>
                                                            </details>
                                                        </xsl:if>
                                                    </div>
                                                </div>
                                            </div>
                                        </td>
                                    </tr>
                                </xsl:for-each>
                            </table>
                        </div>
                    </div>
                </div>
            </div>
        </xsl:for-each>
    </xsl:template>


    <!-- templates for position asset types -->
    <xsl:template match="Position/Equity">
        <xsl:param name="portfolioCcy"/>
        <xsl:variable name="positionCCY" select="../Currency"/>

        <span class="fw-bold">Equity</span>
        <table class="table">

            <!--<xsl:attribute name="class">
                <xsl:value-of
                        select="if (not(Units) or not(Price/Amount[@ccy=$positionCCY])) then 'table bg-danger' else 'table bg-success' "/>
            </xsl:attribute>
            -->
            <tr>
                <th>Units</th>
                <td class="text-end">
                    <xsl:choose>
                        <xsl:when test="Units">
                            <xsl:value-of select="format-number(Units, '#,##0.00')"/>
                        </xsl:when>
                        <xsl:otherwise>
                            <span id="ERROR_{generate-id(.)}"
                                  data-error-message="Missing Equity Unit for Asset {../UniqueID}"
                                  class="badge eg_status--red">Missing
                            </span>
                        </xsl:otherwise>
                    </xsl:choose>
                </td>
            </tr>
            <tr>
                <th>Price:</th>
                <td>
                    <xsl:choose>
                        <xsl:when test="Price/Amount[@ccy=$positionCCY]">
                            <xsl:value-of select="concat($positionCCY, ': ', Price/Amount[@ccy=$positionCCY])"/>
                        </xsl:when>
                        <xsl:otherwise>
                            <span id="ERROR_{generate-id(.)}"
                                  data-error-message="Missing Equity Price in Position CCY for Asset {../UniqueID}"
                                  class="badge eg_status--red">Missing
                            </span>
                        </xsl:otherwise>
                    </xsl:choose>
                </td>
            </tr>
            <tr>
                <th>MarketValue:</th>
                <td>
                    <div class="w-100">
                        <xsl:value-of select="$positionCCY"/>:

                        <xsl:choose>
                            <xsl:when test="MarketValue/Amount[@ccy=$positionCCY]">
                                (Pos. CCY)
                                <xsl:value-of
                                        select="format-number(MarketValue/Amount[@ccy=$positionCCY], '#,##0.00')"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <span id="ERROR_{generate-id(.)}"
                                      data-error-message="Missing Market Value in Position CCY {$positionCCY} for Asset {../UniqueID}"
                                      class="badge eg_status--red">Missing
                                </span>
                            </xsl:otherwise>
                        </xsl:choose>
                    </div>
                    <div class="w-100">
                        <xsl:value-of select="$portfolioCcy"/>:

                        <xsl:choose>
                            <xsl:when test="MarketValue/Amount[@ccy=$portfolioCcy]">
                                (Port. CCY)
                                <xsl:value-of
                                        select="format-number(MarketValue/Amount[@ccy=$portfolioCcy], '#,##0.00')"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <span id="ERROR_{generate-id(.)}"
                                      data-error-message="Missing Market Value in Portolio CCY {$portfolioCcy} for Asset {../UniqueID}"
                                      class="badge eg_status--red">Missing
                                </span>
                            </xsl:otherwise>
                        </xsl:choose>
                    </div>

                    <xsl:for-each select="MarketValue/Amount[@ccy != $portfolioCcy and @ccy != $positionCCY]">
                        <div class="w-100">
                            <xsl:value-of select="concat(@ccy, ': ', format-number(., '#,##0.00'))"/>
                        </div>
                    </xsl:for-each>
                </td>
            </tr>
        </table>
    </xsl:template>

    <xsl:template match="Position/Bond">
        <xsl:param name="portfolioCcy"/>
        <xsl:variable name="positionCCY" select="../Currency"/>

        <span class="fw-bold">{name(.)}</span>

        <div class="container">
            <table class="table">
                <!--
                <xsl:attribute name="class">
                    <xsl:value-of
                            select="if (not(Nominal)
                                    or not(Price/Amount[@ccy=$positionCCY])
                                    or not(MarketValue/Amount[@ccy=$positionCCY])
                                    or not (MarketValue/Amount[@ccy=$portfolioCcy])
                                    ) then 'table bg-danger' else 'table bg-success' "/>
                </xsl:attribute>
                -->

                <tr>
                    <th>Nominal</th>
                    <td class="text-end">
                        <xsl:value-of select="format-number(Nominal, '#,##0.00')"/>
                    </td>
                </tr>
                <tr>
                    <th>MarketValue</th>
                    <td>
                        <div class="row">
                            <div class="col col-8">(Pos. CCY) <xsl:value-of select="$positionCCY"/>:
                            </div>
                            <div class="col text-end">
                                <xsl:value-of
                                        select="format-number(MarketValue/Amount[@ccy=$positionCCY], '#,##0.00')"/>
                            </div>
                        </div>
                        <div class="row">
                            <div class="col col-8">(Port. CCY) <xsl:value-of select="$portfolioCcy"/>:
                            </div>
                            <div class="col text-end">
                                <xsl:value-of
                                        select="format-number(MarketValue/Amount[@ccy=$portfolioCcy], '#,##0.00')"/>
                            </div>
                        </div>

                        <xsl:for-each
                                select="MarketValue/Amount[@ccy != $portfolioCcy and @ccy != $positionCCY]">

                            <div class="row">
                                <div class="col col-8"><xsl:value-of select="@ccy"/>:
                                </div>
                                <div class="col text-end">
                                    <xsl:value-of
                                            select="format-number(., '#,##0.00')"/>
                                </div>
                            </div>

                        </xsl:for-each>
                    </td>
                </tr>
                <tr>
                    <th>Price:</th>
                    <td>
                        <div class="row">
                            <div class="col col-8"><xsl:value-of select="$positionCCY"/>:
                            </div>
                            <div class="col text-end">

                                <xsl:choose>
                                    <xsl:when test="Price/Amount[@ccy=$positionCCY]">
                                        <xsl:value-of
                                                select="concat($positionCCY, ': ', Price/Amount[@ccy=$positionCCY])"/>
                                    </xsl:when>
                                    <xsl:otherwise>
                                        <span id="ERROR_{generate-id(.)}"
                                              data-error-message="Missing Bond Price in Position CCY for Asset {../UniqueID}"
                                              class="badge eg_status--red">Missing
                                        </span>
                                    </xsl:otherwise>
                                </xsl:choose>
                            </div>
                        </div>
                    </td>
                </tr>
            </table>
        </div>
    </xsl:template>

    <xsl:template match="Position/ShareClass">
        <xsl:variable name="positionCCY" select="../Currency"/>
        <span class="fw-bold">ShareClass</span>

        <table class="table">
            <xsl:attribute name="class">
                <xsl:value-of
                        select="if (not(Shares) or not(Price/Amount[@ccy=$positionCCY])) then 'table bg-danger' else 'table bg-success' "/>
            </xsl:attribute>
            <tr>
                <th>Shares</th>
                <td class="text-end">
                    <xsl:value-of select="format-number(Shares, '#,##0.00')"/>
                </td>
            </tr>
            <tr>
                <th>Price:</th>
                <td>
                    <xsl:value-of select="concat($positionCCY, ': ', Price/Amount[@ccy=$positionCCY])"/>
                </td>
            </tr>
        </table>
    </xsl:template>

    <xsl:template match="Position/Warrant">
        <xsl:variable name="positionCCY" select="../Currency"/>
        <table class="table">
            <xsl:attribute name="class">
                <xsl:value-of
                        select="if (not(Units) or not(Price/Amount[@ccy=$positionCCY])) then 'table bg-danger' else 'table bg-success' "/>
            </xsl:attribute>
            Warrant
            <tr>
                <th>Units</th>
                <td class="text-end">
                    <xsl:value-of select="format-number(Units, '#,##0.00')"/>
                </td>
            </tr>
            <tr>
                <th>Price:</th>
                <td>
                    <xsl:value-of select="concat($positionCCY, ': ', Price/Amount[@ccy=$positionCCY])"/>
                </td>
            </tr>
        </table>
    </xsl:template>

    <xsl:template match="Position/Certificate">
        <xsl:variable name="positionCCY" select="../Currency"/>
        <table class="table">
            <xsl:attribute name="class">
                <xsl:value-of
                        select="if (not(Units) or not(Price/Amount[@ccy=$positionCCY])) then 'table bg-danger' else 'table bg-success' "/>
            </xsl:attribute>
            Certificate
            <tr>
                <th>Units</th>
                <td class="text-end">
                    <xsl:value-of select="format-number(Units, '#,##0.00')"/>
                </td>
            </tr>
            <tr>
                <th>Price:</th>
                <td>
                    <xsl:value-of select="concat($positionCCY, ': ', Price/Amount[@ccy=$positionCCY])"/>
                </td>
            </tr>
        </table>
    </xsl:template>

    <xsl:template match="Position/Option">
        <xsl:variable name="positionCCY" select="../Currency"/>
        <table class="table">
            <xsl:attribute name="class">
                <xsl:value-of
                        select="if (not(Contracts) or not(Price/Amount[@ccy=$positionCCY])) then 'table bg-danger' else 'table bg-success' "/>
            </xsl:attribute>
            Options
            <tr>
                <th>Contracts</th>
                <td class="text-end">
                    <xsl:value-of select="format-number(Contracts, '#,##0.00000000')"/>
                </td>
            </tr>
            <tr>
                <th>Price:</th>
                <td>
                    <xsl:value-of select="concat($positionCCY, ': ', Price/Amount[@ccy=$positionCCY])"/>
                </td>
            </tr>
        </table>
    </xsl:template>

    <xsl:template match="Position/Future">
        <xsl:param name="portfolioCcy"/>
        <xsl:variable name="positionCCY" select="../Currency"/>

        <span class="fw-bold">{name(.)}</span>

        <div class="container">
            <table class="table">
                <xsl:attribute name="class">
                    <xsl:value-of
                            select="if (not(Contracts) or not(Price/Amount[@ccy=$positionCCY])) then 'table bg-danger bg-gradient' else 'table border-success border-5 rounded' "/>
                </xsl:attribute>

                <tr>
                    <th>Contracts</th>
                    <td class="text-end">
                        <xsl:value-of select="format-number(Contracts, '#,##0.00000000')"/>
                    </td>
                </tr>
                <tr>
                    <th>Exposure</th>
                    <td>
                        <div class="row">
                            <div class="col col-8">(Pos. CCY) <xsl:value-of select="$positionCCY"/>:
                            </div>
                            <div class="col text-end">
                                <xsl:choose>
                                    <xsl:when test="../Exposures/Exposure/Value/Amount[@ccy=$positionCCY]">
                                        <xsl:value-of
                                                select="format-number(../Exposures/Exposure/Value/Amount[@ccy=$positionCCY], '#,##0.00')"/>
                                    </xsl:when>
                                    <xsl:otherwise>
                                        <span id="ERROR_{generate-id(.)}"
                                              data-error-message="Missing Exposure in Position CCY for Asset {../UniqueID}"
                                              class="badge eg_status--red">Missing
                                        </span>
                                    </xsl:otherwise>
                                </xsl:choose>
                            </div>
                        </div>
                        <div class="row">
                            <div class="col col-8">(Port. CCY) <xsl:value-of select="$portfolioCcy"/>:
                            </div>
                            <div class="col text-end">
                                <xsl:choose>
                                    <xsl:when test="../Exposures/Exposure/Value/Amount[@ccy=$portfolioCcy]">
                                        <xsl:value-of
                                                select="format-number(../Exposures/Exposure/Value/Amount[@ccy=$portfolioCcy], '#,##0.00')"/>
                                    </xsl:when>
                                    <xsl:otherwise>
                                        <span id="ERROR_{generate-id(.)}"
                                              data-error-message="Missing Exposure in Portfolio CCY for Asset {../UniqueID}"
                                              class="eg_status eg_status--red">Missing
                                        </span>
                                    </xsl:otherwise>
                                </xsl:choose>
                            </div>
                        </div>

                        <xsl:for-each
                                select="../Exposures/Exposure/Value/Amount[@ccy != $portfolioCcy and @ccy != $positionCCY]">
                            <div class="w-100">
                                <div class="row">
                                    <div class="col col-8"><xsl:value-of select="@ccy"/>:
                                    </div>
                                    <div class="col text-end">
                                        <xsl:value-of
                                                select="format-number(., '#,##0.00')"/>
                                    </div>
                                </div>
                            </div>
                        </xsl:for-each>
                    </td>
                </tr>
                <tr>
                    <th>Price:</th>
                    <td>
                        <div class="row">
                            <div class="col col-8"><xsl:value-of select="$positionCCY"/>:
                            </div>
                            <div class="col text-end">
                                <xsl:choose>
                                    <xsl:when test="Price/Amount[@ccy=$positionCCY]">
                                        <xsl:value-of
                                                select="concat($positionCCY, ': ', Price/Amount[@ccy=$positionCCY])"/>
                                    </xsl:when>
                                    <xsl:otherwise>
                                        <span id="ERROR_{generate-id(.)}"
                                              data-error-message="Missing Bond Price in Position CCY for Asset {../UniqueID}"
                                              class="badge eg_status--red">Missing
                                        </span>
                                    </xsl:otherwise>
                                </xsl:choose>
                            </div>
                        </div>
                    </td>
                </tr>
            </table>
        </div>
    </xsl:template>

    <xsl:template match="Position/FXForward">
        <span class="fw-bold">FXForward</span>

        <xsl:for-each select="../Exposures/Exposure">
            <div class="row">
                <div class="col">#{position()} Exposure</div>
                <div class="col">
                    <div class="{concat('block ', 'block', position()-1)}" style="padding: 0.4rem;
    margin: 0.4rem;">
                        <div class="row">
                            <div class="col">Type:</div>
                            <div class="col text-end">{Type}</div>
                        </div>
                        <xsl:for-each select="Value/Amount">
                            <div class="row">
                                <div class="col">{@ccy}</div>
                                <div class="col text-end">{format-number(., '#,##0.00')}</div>
                            </div>
                        </xsl:for-each>
                    </div>
                </div>
            </div>
        </xsl:for-each>

        <div class="row">
            <div class="col">Hedge Ratio</div>
            <div class="col text-end">
                <xsl:choose>
                    <xsl:when test="not(HedgeRatio)">
                        <span class="eg_status eg_status--orange">Missing</span>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:value-of select="HedgeRatio"/>
                    </xsl:otherwise>
                </xsl:choose>
            </div>
        </div>
    </xsl:template>

    <xsl:template match="Position/Swap">
        <span class="fw-bold">Swap</span>

        <div class="row">
            <div class="col">Hedge Ratio</div>
            <div class="col text-end">
                <xsl:choose>
                    <xsl:when test="not(HedgeRatio)">
                        <span class="eg_status eg_status--orange">Missing</span>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:value-of select="HedgeRatio"/>
                    </xsl:otherwise>
                </xsl:choose>
            </div>
        </div>
    </xsl:template>

    <xsl:template match="Position/Repo">
        <span class="fw-bold">Repo</span>
    </xsl:template>

    <xsl:template match="Position/FixedTimeDeposit">
        <span class="fw-bold">FixedTimeDeposit</span>
    </xsl:template>

    <xsl:template match="Position/CallMoney">
        <span class="fw-bold">CallMoney</span>
    </xsl:template>

    <xsl:template match="Position/Account">
        <xsl:variable name="positionCCY" select="../Currency"/>
        <div>
            <xsl:attribute name="class">
                <xsl:value-of
                        select="if (not(MarketValue/Amount[@ccy=$positionCCY])) then 'bg-danger' else '' "/>
            </xsl:attribute>
            <span class="fw-bold">Account</span>

            <xsl:for-each select="MarketValue/Amount">
                <div class="row row-cols-2">
                    <div class="col text-start">Market Value <xsl:value-of select="@ccy"/>:
                    </div>
                    <div class="col text-end">
                        <xsl:value-of select="format-number(., '#,##0.00')"/>
                    </div>
                </div>
            </xsl:for-each>
        </div>
    </xsl:template>

    <xsl:template match="Position/Fee">
        <span class="fw-bold">Fee</span>
    </xsl:template>

    <xsl:template match="Position/RealEstate">
        <span class="fw-bold">RealEstate</span>
    </xsl:template>

    <xsl:template match="Position/REIT">
        <span class="fw-bold">REIT</span>
    </xsl:template>

    <xsl:template match="Position/Loan">
        <span class="fw-bold">Loan</span>
    </xsl:template>

    <xsl:template match="Position/Right">
        <span class="fw-bold">Right</span>
    </xsl:template>

    <xsl:template match="Position/Commodity">
        <span class="fw-bold">Commodity</span>
    </xsl:template>

    <xsl:template match="Position/PrivateEquity">
        <span class="fw-bold">PrivateEquity</span>
    </xsl:template>

    <xsl:template match="Position/CommercialPaper">
        <span class="fw-bold">Commercial Paper</span>
    </xsl:template>


    <!-- template for AssetMasterData -->
    <xsl:template match="AssetMasterData">
        <div class="AssetMasterData" id="AssetMasterData">
            <h1>Asset Master Data</h1>
            <div class="table-responsive">
                <table class="table table-sm table-bordered table-striped table-hover">
                    <thead class="sticky-md-top sticky-top-eam">
                        <th>#</th>
                        <th>UniqueID</th>
                        <th>Identifiers</th>
                        <th class="col-md-3">Name</th>
                        <th>Currency</th>
                        <th>Country</th>
                        <th>AssetDetails</th>
                    </thead>
                    <xsl:for-each select="Asset">
                        <tr>
                            <th class="text-center">
                                <xsl:value-of select="position()"/>
                            </th>
                            <xsl:variable name="assetAnker" select="UniqueID"/>
                            <td id="{$assetAnker}">
                                <xsl:value-of select="UniqueID"/>
                            </td>
                            <td>
                                <xsl:attribute name="class">
                                    <xsl:value-of select="if (count(Identifiers/*) = 0) then 'bg-warning' else '' "/>
                                </xsl:attribute>
                                <xsl:for-each select="Identifiers/*[name() != 'OtherID']">
                                    <xsl:value-of select="concat(name(), ': ', .)"/>
                                    <br/>
                                </xsl:for-each>
                                <xsl:for-each select="Identifiers/*[name() = 'OtherID']">
                                    <span class="fs-small">
                                        <xsl:choose>
                                            <xsl:when test="string-length(.) > 16">
                                                <xsl:value-of select="concat(name(), '[@', attribute(), ']:')"/>
                                                <br/>
                                                <xsl:value-of select="."/>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <xsl:value-of select="concat(name(), '[@', attribute(), ']', ': ', .)"/>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </span>
                                    <br/>
                                </xsl:for-each>

                                <!-- check if stock, bond and ShareClass do have an ISIN -->
                                <xsl:choose>
                                    <xsl:when test="AssetType = ('EQ', 'BO', 'SC') and not(Identifiers/ISIN)">
                                        <span id="ERROR_{generate-id(.)}"
                                              data-error-message="Missing ISIN for Instument Type {AssetType} Asset {../UniqueID}"
                                              class="badge eg_status--red">Missing ISIN for Instument Type {AssetType}
                                        </span>
                                    </xsl:when>
                                </xsl:choose>
                            </td>
                            <td>
                                <xsl:value-of select="Name"/>
                            </td>
                            <td>
                                <xsl:value-of select="Currency"/>
                            </td>
                            <td>
                                <xsl:value-of select="Country"/>
                            </td>
                            <td>
                                Type:
                                <xsl:value-of select="AssetType"/>
                                (
                                <span class="fw-bold">
                                    <xsl:value-of select="name(AssetDetails/*[position()=1])"/>
                                </span>
                                )
                                <br/>
                                <xsl:apply-templates select="AssetDetails/*"/>

                                <div class="container" style="padding-left:0">
                                    <div class="row">
                                        <div class="fs-small">
                                            <div class="row align-items-end">
                                                <div class="col">
                                                    <xsl:if test="$renderXMLContent">
                                                        <details>
                                                            <summary>Original XML</summary>
                                                            <p>
                                                                <script type="text/plain" class="language-xml">
                                                                        <xsl:copy-of select="node()"/>
                                                                </script>
                                                            </p>
                                                        </details>
                                                    </xsl:if>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </td>
                        </tr>
                    </xsl:for-each>
                </table>
            </div>
        </div>
    </xsl:template>

    <!-- template for AssetMasterData Detail Type -->
    <xsl:template match="AssetDetails/Equity | AssetDetails/Bond | AssetDetails/ShareClass">
        <div class="container">
            <div class="row">
                <div class="col">Issuer LEI:</div>
                <div class="col">
                    <span class="fw-bold">
                        <xsl:value-of select="Issuer/Identifiers/LEI"/>
                    </span>
                </div>
            </div>
            <div class="row">
                <div class="col">Issuer Name:</div>
                <div class="col">
                    <xsl:value-of select="Issuer/Name"/>
                </div>
            </div>
            <div class="row">
                <div class="col">StockMarket:</div>
                <div class="col">
                    <xsl:value-of select="StockMarket"/>
                </div>
            </div>
            <xsl:if test="name() = 'Bond'">
                <div class="row">
                    <div class="col">Maturity Date</div>
                    <div class="col">
                        <xsl:value-of select="MaturityDate"/>
                    </div>
                </div>
                <div class="row">
                    <div class="col">RedemptionRate</div>
                    <div class="col">
                        <xsl:value-of select="RedemptionRate"/>
                    </div>
                </div>
                <div class="row">
                    <div class="col">InterestRate</div>
                    <div class="col">
                        <xsl:value-of select="InterestRate"/>
                    </div>
                </div>
            </xsl:if>
        </div>
    </xsl:template>

    <xsl:template match="AssetDetails/Warrant">
        Issuer LEI:
        <span class="fw-bold">
            <xsl:value-of select="Issuer/Identifiers/LEI"/>
        </span>
        <br/>
        Issuer Name:
        <xsl:value-of select="Issuer/Name"/>
        <br/>

        Contract Size:
        <xsl:value-of select="ContractSize"/>
        <br/>

        Maturity Date:
        <xsl:value-of select="MaturityDate"/>
        <br/>

        Call/Put Indicator:
        <xsl:value-of select="CallPutIndicator"/>
        <br/>

        Strike Price:
        <xsl:for-each select="StrikePrice/Amount">
            <xsl:value-of select="concat(@ccy, ': ', .)"/>
            <br/>
        </xsl:for-each>
    </xsl:template>

    <xsl:template match="AssetDetails/Certificate">
        Type:
        <xsl:value-of select="Type"/>
        <br/>

        Issuer LEI:
        <span class="fw-bold">
            <xsl:value-of select="Issuer/Identifiers/LEI"/>
        </span>
        <br/>
        Issuer Name:
        <xsl:value-of select="Issuer/Name"/>
        <br/>
    </xsl:template>

    <xsl:template match="AssetDetails/Option">
        Option Type:
        <xsl:value-of select="Type"/>
        <br/>
        Contract Size:
        <xsl:value-of select="ContractSize"/>
        <br/>
        Maturity Date:
        <xsl:value-of select="MaturityDate"/>
        <br/>
        Call/Put Indicator:
        <xsl:value-of select="CallPutIndicator"/>
        <br/>

        Strike Price:
        <xsl:for-each select="StrikePrice/Amount">
            <xsl:value-of select="concat(@ccy, ': ', .)"/>
            <br/>
        </xsl:for-each>
    </xsl:template>

    <xsl:template match="AssetDetails/Future">
        <div class="container">
            <div class="row">
                <div class="col">Future Type:</div>
                <div class="col">
                    <xsl:value-of select="Type"/>
                </div>
            </div>
            <div class="row">
                <div class="col">Contract Size:</div>
                <div class="col">
                    <xsl:value-of select="ContractSize"/>
                </div>
            </div>
            <div class="row">
                <div class="col">Maturity Date:</div>
                <div class="col">
                    <xsl:value-of select="MaturityDate"/>
                </div>
            </div>
        </div>
    </xsl:template>

    <xsl:template match="AssetDetails/FXForward">
        <div class="container">
            <div class="row">
                <div class="col">Buy:</div>
                <div class="col">
                    <xsl:value-of select="concat(CurrencyBuy, ': ', format-number(AmountBuy, '#,##0.00'))"/>
                </div>
            </div>
            <div class="row">
                <div class="col">Sell:</div>
                <div class="col">
                    <xsl:value-of select="concat(CurrencySell, ': ', format-number(AmountSell, '#,##0.00'))"/>
                </div>
            </div>
            <div class="row">
                <div class="col">Maturity Date:</div>
                <div class="col">
                    <xsl:value-of select="MaturityDate"/>
                </div>
            </div>
            <div class="row">
                <div class="col">Counterparty LEI:</div>
                <div class="col">
                    <span class="fw-bold">
                        <xsl:value-of select="Counterparty/Identifiers/LEI"/>
                    </span>
                </div>
            </div>
            <div class="row">
                <div class="col">Counterparty Name:</div>
                <div class="col">
                    <xsl:value-of select="Counterparty/Name"/>
                </div>
            </div>
        </div>
    </xsl:template>

    <xsl:template match="AssetDetails/Swap">
        <div class="container">
            <div class="row">
                <div class="col-4">Type</div>
                <div class="col">{Type}</div>
            </div>
            <div class="row">
                <div class="col-4">MaturityDate</div>
                <div class="col">{MaturityDate}</div>
            </div>
            <div class="row">
                <div class="col-4">Counterparty LEI:</div>
                <div class="col">{Counterparty/Identifiers/LEI}</div>
            </div>
            <div class="row">
                <div class="col-4">Counterparty Name:</div>
                <div class="col">{Counterparty/Name}</div>
            </div>
            <xsl:for-each select="Legs/Leg">
                <div class="row">
                    <div class="col-4">Leg #{position()}: {Type}</div>
                    <div class="col">
                        <div class="{concat('AssetMasterData block block',position()-1)}"
                             style="margin: 0.1rem; padding: .4rem;">
                            <div class="row">
                                <div class="col-3">Currency</div>
                                <div class="col">{Currency}</div>
                            </div>
                            <div class="row">
                                <div class="col-3">Notional</div>
                                <div class="col">{Notional}</div>
                            </div>
                            <div class="row">
                                <div class="col-3">YieldType</div>
                                <div class="col">{YieldType}</div>
                            </div>
                            <div class="row">
                                <div class="col-3">Underlying</div>
                                <div class="col">
                                    <xsl:for-each select="Underlying/*">
                                        <div class="row">
                                            <div class="col">
                                                <xsl:choose>
                                                    <xsl:when test="@attribute()">{name()}[{@attribute()}]: {text()}
                                                    </xsl:when>
                                                    <xsl:otherwise>{name()}: {text()}</xsl:otherwise>
                                                </xsl:choose>
                                            </div>
                                        </div>
                                    </xsl:for-each>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </xsl:for-each>
        </div>

    </xsl:template>

    <xsl:template match="AssetDetails/Repo">

    </xsl:template>

    <xsl:template match="AssetDetails/FixedTimeDeposit">

    </xsl:template>

    <xsl:template match="AssetDetails/CallMoney">

    </xsl:template>

    <xsl:template match="AssetDetails/Account | AssetDetails/Fee">
        <div class="container">
            <div class="row">
                <div class="col">Counterparty LEI:</div>
                <div class="col">
                    <span class="fw-bold">
                        <xsl:value-of select="Counterparty/Identifiers/LEI"/>
                    </span>
                </div>
            </div>
            <div class="row">
                <div class="col">SwiftBIC:</div>
                <div class="col">
                    <xsl:value-of select="Counterparty/Identifiers/SwiftBIC"/>
                </div>
            </div>
            <div class="row">
                <div class="col">Counterparty Name:</div>
                <div class="col">
                    <xsl:value-of select="Counterparty/Name"/>
                </div>
            </div>
        </div>
    </xsl:template>

    <xsl:template match="AssetDetails/RealEstate">

    </xsl:template>

    <xsl:template match="AssetDetails/REIT">

    </xsl:template>

    <xsl:template match="AssetDetails/Loan">
        LOAN
    </xsl:template>

    <xsl:template match="AssetDetails/Right">

    </xsl:template>

    <xsl:template match="AssetDetails/Commodity">

    </xsl:template>

    <xsl:template match="AssetDetails/PrivateEquity">
        PrivateEquity
    </xsl:template>

    <xsl:template match="AssetDetails/CommercialPaper">

    </xsl:template>

</xsl:stylesheet>
